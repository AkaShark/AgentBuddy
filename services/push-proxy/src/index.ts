import { handleDebugPush } from "./debug"
import { isLegacyKeepaliveEnabled } from "./durable-object"
import { ContentState, Env, RegisterRequest } from "./types"
import { handleV2 } from "./v2"

export { PushRegistration } from "./durable-object"
export { RateLimiter } from "./rate-limiter"
export { HostChannel } from "./host-channel"

const DEFAULT_INTERVAL_SECONDS = 30
const MIN_INTERVAL_SECONDS = 10
const MAX_INTERVAL_SECONDS = 3600
const DEFAULT_TTL_SECONDS = 7200
const MIN_TTL_SECONDS = 60
const MAX_TTL_SECONDS = 6 * 3600
const MAX_PUSH_TOKEN_LENGTH = 1024
const MAX_REGISTER_BODY_LENGTH = 16 * 1024

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json" },
  })
}

// Absent → fallback; non-numeric → null (invalid); otherwise clamp to [min, max].
function clampSeconds(value: unknown, fallback: number, min: number, max: number): number | null {
  if (value === undefined || value === null) return fallback
  if (typeof value !== "number" || !Number.isFinite(value)) return null
  return Math.min(max, Math.max(min, Math.floor(value)))
}

// Validates the shape actual clients send today (see PushProxyClient.swift /
// AppLifecycleController.kt on Android): phase is a string, elapsedSeconds /
// toolCallCount / activeThreadCount are numbers, serverId / threadId are
// strings. All fields are optional (iOS currently omits contentState
// entirely on register).
function parseContentState(raw: unknown): ContentState | string | undefined {
  if (raw === undefined || raw === null) return undefined
  if (typeof raw !== "object" || Array.isArray(raw)) {
    return "contentState must be an object"
  }
  const cs = raw as Record<string, unknown>

  if (cs.phase !== undefined && typeof cs.phase !== "string") {
    return "contentState.phase must be a string"
  }
  for (const key of ["elapsedSeconds", "toolCallCount", "activeThreadCount"] as const) {
    const value = cs[key]
    if (value !== undefined && (typeof value !== "number" || !Number.isFinite(value))) {
      return `contentState.${key} must be a number`
    }
  }
  for (const key of ["serverId", "threadId"] as const) {
    const value = cs[key]
    if (value !== undefined && typeof value !== "string") {
      return `contentState.${key} must be a string`
    }
  }

  return {
    phase: cs.phase as string | undefined,
    elapsedSeconds: cs.elapsedSeconds as number | undefined,
    toolCallCount: cs.toolCallCount as number | undefined,
    activeThreadCount: cs.activeThreadCount as number | undefined,
    serverId: cs.serverId as string | undefined,
    threadId: cs.threadId as string | undefined,
  }
}

function parseRegisterRequest(raw: unknown): RegisterRequest | string {
  if (typeof raw !== "object" || raw === null || Array.isArray(raw)) {
    return "body must be a JSON object"
  }
  const body = raw as Record<string, unknown>

  const platform = body.platform
  if (platform !== "ios" && platform !== "android") {
    return 'platform must be "ios" or "android"'
  }

  const pushToken = body.pushToken
  if (
    typeof pushToken !== "string" ||
    pushToken.trim().length === 0 ||
    pushToken.length > MAX_PUSH_TOKEN_LENGTH
  ) {
    return `pushToken must be a non-empty string of at most ${MAX_PUSH_TOKEN_LENGTH} characters`
  }
  // APNs device tokens are hex and get interpolated into the APNs request path.
  if (platform === "ios" && !/^[0-9a-fA-F]+$/.test(pushToken)) {
    return "pushToken must be a hex APNs device token"
  }

  const apnsEnvironment = body.apnsEnvironment ?? "production"
  if (apnsEnvironment !== "production" && apnsEnvironment !== "sandbox") {
    return 'apnsEnvironment must be "production" or "sandbox"'
  }

  const intervalSeconds = clampSeconds(
    body.intervalSeconds,
    DEFAULT_INTERVAL_SECONDS,
    MIN_INTERVAL_SECONDS,
    MAX_INTERVAL_SECONDS
  )
  if (intervalSeconds === null) return "intervalSeconds must be a number"

  const ttlSeconds = clampSeconds(body.ttlSeconds, DEFAULT_TTL_SECONDS, MIN_TTL_SECONDS, MAX_TTL_SECONDS)
  if (ttlSeconds === null) return "ttlSeconds must be a number"

  const contentState = parseContentState(body.contentState)
  if (typeof contentState === "string") {
    return contentState
  }

  return {
    platform,
    pushToken,
    apnsEnvironment,
    intervalSeconds,
    ttlSeconds,
    contentState,
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)
    const parts = url.pathname.split("/").filter(Boolean)

    if (parts[0] === "v2") return handleV2(request, env, url, parts)
    if (parts.length === 2 && parts[0] === "debug" && parts[1] === "push") {
      return handleDebugPush(request, env)
    }

    if (request.method === "POST" && parts.length === 1 && parts[0] === "register") {
      // Legacy keepalive retired: answer once with 410 so old clients stop,
      // without touching the rate limiter or creating a registration.
      if (!isLegacyKeepaliveEnabled(env)) return json({ error: "legacy_push_retired" }, 410)
      const ip = request.headers.get("cf-connecting-ip") ?? "unknown"
      const limitId = env.RATE_LIMITER.idFromName(ip)
      const limiter = env.RATE_LIMITER.get(limitId)
      const limitResp = await limiter.fetch(new Request("https://rl/check"))
      if (limitResp.status === 429) {
        return json({ error: "rate limited" }, 429)
      }

      // Reject an oversized body before reading it whenever Content-Length is
      // present (avoids buffering large request bodies just to discard them).
      // When absent or the request is chunked, fall back to checking the
      // decoded text length below.
      const contentLengthHeader = request.headers.get("content-length")
      if (contentLengthHeader !== null) {
        const contentLength = Number(contentLengthHeader)
        if (Number.isFinite(contentLength) && contentLength > MAX_REGISTER_BODY_LENGTH) {
          return json({ error: "request body too large" }, 413)
        }
      }

      const text = await request.text()
      if (text.length > MAX_REGISTER_BODY_LENGTH) {
        return json({ error: "request body too large" }, 413)
      }
      let raw: unknown
      try {
        raw = JSON.parse(text)
      } catch {
        return json({ error: "invalid JSON body" }, 400)
      }
      const body = parseRegisterRequest(raw)
      if (typeof body === "string") {
        return json({ error: body }, 400)
      }

      const id = env.PUSH_REGISTRATION.newUniqueId()
      const stub = env.PUSH_REGISTRATION.get(id)
      await stub.fetch(new Request("https://do/", { method: "PUT", body: JSON.stringify(body) }))

      return json({ id: id.toString() })
    }

    if (parts.length === 2 && request.method === "POST") {
      const [doId, action] = parts
      if (!["deregister"].includes(action)) return json({ error: "not found" }, 404)
      let id: DurableObjectId
      try {
        id = env.PUSH_REGISTRATION.idFromString(doId)
      } catch {
        return json({ error: "invalid registration id" }, 400)
      }
      const stub = env.PUSH_REGISTRATION.get(id)
      const doReq = new Request(`https://do/${action}`, {
        method: "POST",
        body: request.body,
        headers: request.headers,
      })
      const resp = await stub.fetch(doReq)
      return new Response(resp.body, { status: resp.status, headers: resp.headers })
    }

    return json({ error: "not found" }, 404)
  },
}
