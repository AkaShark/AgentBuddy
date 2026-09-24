// v2 routes (spec §7.1). Host requests are authenticated here — per-IP rate
// limit, signature over the v2 string with `aud`, timestamp window,
// BLOCKED_HOST_IDS — and validated before they reach the host's HostChannel
// object, which then enforces nonce replay, revocation and per-host limits.
// Subscription grants and sealed targets (§5.1, §5.5) are checked here too, so
// the object only ever receives an authorized, decrypted target.
import { EventInput, MAX_GRANT_LIFETIME_SECONDS, RevokeInput, SubscriptionInput } from "./host-channel"
import { decodeUtf8, errorResponse, jsonResponse, MAX_V2_BODY_BYTES, readBodyLimited } from "./http"
import { checkRateLimit, clientIpBucket } from "./rate-limiter"
import { MAX_SEALED_TARGET_LENGTH, openSealedTarget } from "./sealed-target"
import {
  grantSigningString,
  HEX128_RE,
  HEX32_RE,
  HEX64_RE,
  hostRequestSigningString,
  revokeSigningString,
  sha256Hex,
  SIGNATURE_WINDOW_SECONDS,
  verifyEd25519,
  withinSignatureWindow,
  workerAudience,
} from "./signing"
import { Env } from "./types"
import { AGENT_RE, blockedHostIds, isRecord, isRoutingId, isUnixSeconds } from "./validation"

export const REVOKE_RATE_LIMIT_PER_MINUTE = 10
// Per client IP bucket, checked before the body is read or any signature is
// verified. One host at its full per-host budget (60 + 120 + 120) fits.
export const HOST_IP_RATE_LIMIT_PER_MINUTE = 300
const EVENT_ID_RE = /^evt_[0-9a-f]{32}$/
const SUBSCRIPTION_ID_RE = /^sub_[0-9a-f]{32}$/
const TIMESTAMP_RE = /^[0-9]{1,12}$/

interface SubscriptionRequest {
  deviceId: string
  platform: "ios" | "android"
  apnsEnvironment: "sandbox" | "production" | null
  sealedTarget: string
  agent: string
  threadId: string
  turnId: string
  issuedAt: number
  expiresAt: number
  grantNonce: string
  grantSignature: string
}

function parseSubscription(raw: unknown): SubscriptionRequest | string {
  if (!isRecord(raw)) return "body must be a JSON object"
  const { deviceId, platform, apnsEnvironment, sealedTarget, agent, threadId, turnId } = raw
  if (typeof deviceId !== "string" || !HEX64_RE.test(deviceId)) return "deviceId must be 64 lowercase hex"
  if (platform !== "ios" && platform !== "android") return 'platform must be "ios" or "android"'
  let environment: "sandbox" | "production" | null = null
  if (platform === "ios") {
    if (apnsEnvironment !== "sandbox" && apnsEnvironment !== "production") {
      return 'apnsEnvironment must be "sandbox" or "production" for ios'
    }
    environment = apnsEnvironment
  } else if (apnsEnvironment !== undefined && apnsEnvironment !== null && apnsEnvironment !== "none") {
    return "apnsEnvironment is only valid for ios"
  }
  // Only the type and size are checked here; the encoding is part of opening
  // it, where every failure is the same 403.
  if (typeof sealedTarget !== "string" || sealedTarget.length === 0 || sealedTarget.length > MAX_SEALED_TARGET_LENGTH) {
    return `sealedTarget must be a string of at most ${MAX_SEALED_TARGET_LENGTH} characters`
  }
  if (typeof agent !== "string" || !AGENT_RE.test(agent)) return "agent is invalid"
  if (!isRoutingId(threadId)) return "threadId must be 1-128 well-formed bytes without control characters"
  if (!isRoutingId(turnId)) return "turnId must be 1-128 well-formed bytes without control characters"
  if (!isUnixSeconds(raw.issuedAt) || !isUnixSeconds(raw.expiresAt)) {
    return "issuedAt and expiresAt must be unix seconds"
  }
  if (typeof raw.grantNonce !== "string" || !HEX32_RE.test(raw.grantNonce)) return "grantNonce must be 32 lowercase hex"
  if (typeof raw.grantSignature !== "string" || !HEX128_RE.test(raw.grantSignature)) {
    return "grantSignature must be 128 lowercase hex"
  }
  return {
    deviceId,
    platform,
    apnsEnvironment: environment,
    sealedTarget,
    agent,
    threadId,
    turnId,
    issuedAt: raw.issuedAt,
    expiresAt: raw.expiresAt,
    grantNonce: raw.grantNonce,
    grantSignature: raw.grantSignature,
  }
}

function parseEvent(raw: unknown): EventInput | string {
  if (!isRecord(raw)) return "body must be a JSON object"
  const { eventId, agent, threadId, turnId, type, reason, occurredAt } = raw
  if (typeof eventId !== "string" || !EVENT_ID_RE.test(eventId)) return "eventId must be evt_ + 32 lowercase hex"
  if (typeof agent !== "string" || !AGENT_RE.test(agent)) return "agent is invalid"
  if (!isRoutingId(threadId)) return "threadId must be 1-128 well-formed bytes without control characters"
  if (!isRoutingId(turnId)) return "turnId must be 1-128 well-formed bytes without control characters"
  if (type !== "completed" && type !== "failed") return 'type must be "completed" or "failed"'
  if (reason !== undefined && reason !== null && reason !== "interrupted" && reason !== "error") {
    return 'reason must be null, "interrupted" or "error"'
  }
  if (!isUnixSeconds(occurredAt)) return "occurredAt must be unix seconds"
  return { eventId, agent, threadId, turnId, type, reason: reason ?? null, occurredAt }
}

// §5.1 grant rules that need no per-host state, then the sealed target
// (§5.5). Revocation and grant-nonce reuse are checked by the HostChannel.
async function authorizeSubscription(
  env: Env,
  aud: string,
  hostId: string,
  req: SubscriptionRequest
): Promise<SubscriptionInput | Response> {
  const nowSec = Math.floor(Date.now() / 1000)
  if (req.expiresAt <= req.issuedAt || req.expiresAt - req.issuedAt > MAX_GRANT_LIFETIME_SECONDS) {
    return errorResponse("forbidden", { message: "grant lifetime must be positive and at most 48h" })
  }
  if (req.expiresAt <= nowSec) return errorResponse("forbidden", { message: "grant expired" })
  if (req.issuedAt > nowSec + SIGNATURE_WINDOW_SECONDS) {
    return errorResponse("forbidden", { message: "grant issued in the future" })
  }
  // `host` is the authenticated signer and `aud` this Worker, so a grant only
  // works for the host and deployment it was issued to.
  const signed = grantSigningString({
    aud,
    host: hostId,
    device: req.deviceId,
    platform: req.platform,
    environment: req.platform === "ios" ? req.apnsEnvironment ?? "production" : "none",
    targetSha256: await sha256Hex(req.sealedTarget),
    agent: req.agent,
    thread: req.threadId,
    turn: req.turnId,
    issued: req.issuedAt,
    expires: req.expiresAt,
    nonce: req.grantNonce,
  })
  if (!(await verifyEd25519(req.deviceId, req.grantSignature, signed))) {
    return errorResponse("forbidden", { message: "grant signature invalid" })
  }
  // The AAD binds hostId|deviceId, so a target sealed for another device or
  // host does not open; which check failed is never revealed.
  const pushToken = await openSealedTarget(env, req.sealedTarget, {
    hostId,
    deviceId: req.deviceId,
    platform: req.platform,
    apnsEnvironment: req.apnsEnvironment,
  })
  if (pushToken === null) return errorResponse("forbidden")
  return {
    deviceId: req.deviceId,
    platform: req.platform,
    pushToken,
    apnsEnvironment: req.apnsEnvironment,
    agent: req.agent,
    threadId: req.threadId,
    turnId: req.turnId,
    issuedAt: req.issuedAt,
    expiresAt: req.expiresAt,
    grantNonce: req.grantNonce,
  }
}

// Header shape and timestamp window (no I/O), then the per-IP limit, then the
// body and the signature — so unauthenticated floods never cost a body read,
// a signature check or a HostChannel object.
async function authenticateHost(
  request: Request,
  env: Env,
  path: string
): Promise<{ hostId: string; nonce: string; aud: string; body: Uint8Array } | Response> {
  const hostId = request.headers.get("x-agentbuddy-host")
  const timestamp = request.headers.get("x-agentbuddy-timestamp")
  const nonce = request.headers.get("x-agentbuddy-nonce")
  const signature = request.headers.get("x-agentbuddy-signature")
  if (
    hostId === null || !HEX64_RE.test(hostId) ||
    timestamp === null || !TIMESTAMP_RE.test(timestamp) ||
    nonce === null || !HEX32_RE.test(nonce) ||
    signature === null || !HEX128_RE.test(signature)
  ) {
    return errorResponse("unauthorized")
  }
  if (!withinSignatureWindow(Number(timestamp), Math.floor(Date.now() / 1000))) {
    return errorResponse("unauthorized")
  }
  const retryAfter = await checkRateLimit(
    env.RATE_LIMITER,
    `host-ip:${clientIpBucket(request)}`,
    HOST_IP_RATE_LIMIT_PER_MINUTE
  )
  if (retryAfter !== null) return errorResponse("rate_limited", { retryAfterSeconds: retryAfter })

  const body = await readBodyLimited(request, MAX_V2_BODY_BYTES)
  if (body === null) return errorResponse("payload_too_large")
  const aud = workerAudience(request, env)
  const signed = hostRequestSigningString(aud, request.method, path, timestamp, nonce, await sha256Hex(body))
  if (!(await verifyEd25519(hostId, signature, signed))) return errorResponse("unauthorized")
  if (blockedHostIds(env).has(hostId)) return errorResponse("forbidden")
  return { hostId, nonce, aud, body }
}

async function forwardToChannel(
  env: Env,
  hostId: string,
  nonce: string,
  method: string,
  path: string,
  payload?: unknown
): Promise<Response> {
  const stub = env.HOST_CHANNEL.get(env.HOST_CHANNEL.idFromName(hostId))
  const resp = await stub.fetch(
    new Request(`https://host-channel${path}`, {
      method,
      headers: { "content-type": "application/json", "x-agentbuddy-host": hostId, "x-agentbuddy-nonce": nonce },
      body: payload === undefined ? undefined : JSON.stringify(payload),
    })
  )
  return new Response(resp.body, { status: resp.status, headers: resp.headers })
}

function parseJsonBody(bytes: Uint8Array): { raw: unknown } | Response {
  const text = decodeUtf8(bytes)
  if (text === null) return errorResponse("bad_request", { message: "body must be UTF-8" })
  try {
    return { raw: JSON.parse(text) }
  } catch {
    return errorResponse("bad_request", { message: "invalid JSON body" })
  }
}

type Authorized = { hostId: string; aud: string }

async function hostSigned<T>(
  request: Request,
  env: Env,
  path: string,
  channelPath: string,
  parse?: (raw: unknown) => T | string,
  authorize?: (auth: Authorized, parsed: T) => Promise<unknown | Response>
): Promise<Response> {
  const auth = await authenticateHost(request, env, path)
  if (auth instanceof Response) return auth
  let payload: unknown
  if (parse) {
    const parsed = parseJsonBody(auth.body)
    if (parsed instanceof Response) return parsed
    const result = parse(parsed.raw)
    if (typeof result === "string") return errorResponse("bad_request", { message: result })
    payload = authorize ? await authorize(auth, result) : result
    if (payload instanceof Response) return payload
  }
  return forwardToChannel(env, auth.hostId, auth.nonce, request.method, channelPath, payload)
}

async function handleRevoke(request: Request, env: Env): Promise<Response> {
  const retryAfter = await checkRateLimit(
    env.RATE_LIMITER,
    `revoke-ip:${clientIpBucket(request)}`,
    REVOKE_RATE_LIMIT_PER_MINUTE
  )
  if (retryAfter !== null) return errorResponse("rate_limited", { retryAfterSeconds: retryAfter })

  const bytes = await readBodyLimited(request, MAX_V2_BODY_BYTES)
  if (bytes === null) return errorResponse("payload_too_large")
  const parsed = parseJsonBody(bytes)
  if (parsed instanceof Response) return parsed
  const raw = parsed.raw
  if (!isRecord(raw)) return errorResponse("bad_request", { message: "body must be a JSON object" })
  const { hostId, deviceId, scope, timestamp, nonce, signature } = raw
  if (
    typeof hostId !== "string" || !HEX64_RE.test(hostId) ||
    typeof deviceId !== "string" || !HEX64_RE.test(deviceId) ||
    scope !== "all" ||
    !isUnixSeconds(timestamp) ||
    typeof nonce !== "string" || !HEX32_RE.test(nonce) ||
    typeof signature !== "string" || !HEX128_RE.test(signature)
  ) {
    return errorResponse("bad_request", { message: "invalid revoke request" })
  }
  if (!withinSignatureWindow(timestamp, Math.floor(Date.now() / 1000))) return errorResponse("unauthorized")
  const signed = revokeSigningString({ aud: workerAudience(request, env), host: hostId, device: deviceId, scope, timestamp, nonce })
  if (!(await verifyEd25519(deviceId, signature, signed))) return errorResponse("unauthorized")

  // The HostChannel keeps revoke nonces apart from host nonces and applies
  // the per-host revoke limit and the cap on never-seen devices.
  const input: RevokeInput = { deviceId, timestamp }
  return forwardToChannel(env, hostId, nonce, "POST", "/revoke", input)
}

export async function handleV2(request: Request, env: Env, url: URL, parts: string[]): Promise<Response> {
  try {
    const route = parts.slice(1)
    const method = request.method

    if (method === "GET" && route.length === 1 && route[0] === "health") {
      return jsonResponse({ ok: true, features: ["subscriptions.v2", "events.v2"] })
    }
    if (route[0] === "subscriptions") {
      if (method === "POST" && route.length === 1) {
        return await hostSigned(request, env, url.pathname, "/subscriptions", parseSubscription, (auth, req) =>
          authorizeSubscription(env, auth.aud, auth.hostId, req)
        )
      }
      if (method === "POST" && route.length === 2 && route[1] === "revoke") {
        return await handleRevoke(request, env)
      }
      if (method === "DELETE" && route.length === 2) {
        if (!SUBSCRIPTION_ID_RE.test(route[1])) return errorResponse("not_found")
        return await hostSigned(request, env, url.pathname, `/subscriptions/${route[1]}`)
      }
    }
    if (method === "POST" && route.length === 1 && route[0] === "events") {
      return await hostSigned(request, env, url.pathname, "/events", parseEvent)
    }
    return errorResponse("not_found")
  } catch (err) {
    console.error(`v2 error: ${err instanceof Error ? err.message : String(err)}`)
    return errorResponse("internal")
  }
}
