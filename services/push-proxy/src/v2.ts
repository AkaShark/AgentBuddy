// v2 routes (spec §7.1). Host requests are authenticated here — signature,
// timestamp window, BLOCKED_HOST_IDS — and validated before they reach the
// host's HostChannel object, which then enforces nonce replay and rate limits.
import { EventInput, RevokeInput, SubscriptionInput } from "./host-channel"
import { decodeUtf8, errorResponse, jsonResponse, MAX_V2_BODY_BYTES, readBodyLimited } from "./http"
import { checkRateLimit } from "./rate-limiter"
import {
  HEX128_RE,
  HEX32_RE,
  HEX64_RE,
  hostRequestSigningString,
  revokeSigningString,
  sha256Hex,
  verifyEd25519,
  withinSignatureWindow,
} from "./signing"
import { Env } from "./types"
import { AGENT_RE, isRecord, isRoutingId, isUnixSeconds, pushTokenError } from "./validation"

export const REVOKE_RATE_LIMIT_PER_MINUTE = 10
const EVENT_ID_RE = /^evt_[0-9a-f]{32}$/
const SUBSCRIPTION_ID_RE = /^sub_[0-9a-f]{32}$/
const TIMESTAMP_RE = /^[0-9]{1,12}$/

export function blockedHostIds(env: Env): Set<string> {
  return new Set(
    (env.BLOCKED_HOST_IDS ?? "")
      .split(",")
      .map((id) => id.trim().toLowerCase())
      .filter(Boolean)
  )
}

function parseSubscription(raw: unknown): SubscriptionInput | string {
  if (!isRecord(raw)) return "body must be a JSON object"
  const { deviceId, platform, pushToken, apnsEnvironment, agent, threadId, turnId } = raw
  if (typeof deviceId !== "string" || !HEX64_RE.test(deviceId)) return "deviceId must be 64 lowercase hex"
  if (platform !== "ios" && platform !== "android") return 'platform must be "ios" or "android"'
  const tokenError = pushTokenError(platform, pushToken)
  if (tokenError) return tokenError
  let environment: "sandbox" | "production" | null = null
  if (platform === "ios") {
    if (apnsEnvironment !== "sandbox" && apnsEnvironment !== "production") {
      return 'apnsEnvironment must be "sandbox" or "production" for ios'
    }
    environment = apnsEnvironment
  } else if (apnsEnvironment !== undefined && apnsEnvironment !== null && apnsEnvironment !== "none") {
    return "apnsEnvironment is only valid for ios"
  }
  if (typeof agent !== "string" || !AGENT_RE.test(agent)) return "agent is invalid"
  if (!isRoutingId(threadId)) return "threadId must be 1-128 bytes without control characters"
  if (!isRoutingId(turnId)) return "turnId must be 1-128 bytes without control characters"
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
    pushToken: pushToken as string,
    apnsEnvironment: environment,
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
  if (!isRoutingId(threadId)) return "threadId must be 1-128 bytes without control characters"
  if (!isRoutingId(turnId)) return "turnId must be 1-128 bytes without control characters"
  if (type !== "completed" && type !== "failed") return 'type must be "completed" or "failed"'
  if (reason !== undefined && reason !== null && reason !== "interrupted" && reason !== "error") {
    return 'reason must be null, "interrupted" or "error"'
  }
  if (!isUnixSeconds(occurredAt)) return "occurredAt must be unix seconds"
  return { eventId, agent, threadId, turnId, type, reason: reason ?? null, occurredAt }
}

async function authenticateHost(
  request: Request,
  env: Env,
  path: string,
  body: Uint8Array
): Promise<{ hostId: string; nonce: string } | Response> {
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
  const signed = hostRequestSigningString(request.method, path, timestamp, nonce, await sha256Hex(body))
  if (!(await verifyEd25519(hostId, signature, signed))) return errorResponse("unauthorized")
  if (blockedHostIds(env).has(hostId)) return errorResponse("forbidden")
  return { hostId, nonce }
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

async function hostSigned<T>(
  request: Request,
  env: Env,
  path: string,
  channelPath: string,
  parse?: (raw: unknown) => T | string
): Promise<Response> {
  const body = await readBodyLimited(request, MAX_V2_BODY_BYTES)
  if (body === null) return errorResponse("payload_too_large")
  const auth = await authenticateHost(request, env, path, body)
  if (auth instanceof Response) return auth
  let payload: T | undefined
  if (parse) {
    const parsed = parseJsonBody(body)
    if (parsed instanceof Response) return parsed
    const result = parse(parsed.raw)
    if (typeof result === "string") return errorResponse("bad_request", { message: result })
    payload = result
  }
  return forwardToChannel(env, auth.hostId, auth.nonce, request.method, channelPath, payload)
}

async function handleRevoke(request: Request, env: Env): Promise<Response> {
  const ip = request.headers.get("cf-connecting-ip") ?? "unknown"
  const retryAfter = await checkRateLimit(env.RATE_LIMITER, `revoke:${ip}`, REVOKE_RATE_LIMIT_PER_MINUTE)
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
  const signed = revokeSigningString({ host: hostId, device: deviceId, scope, timestamp, nonce })
  if (!(await verifyEd25519(deviceId, signature, signed))) return errorResponse("unauthorized")

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
        return await hostSigned(request, env, url.pathname, "/subscriptions", parseSubscription)
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
