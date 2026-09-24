// POST /debug/push (spec §7.5): admin-token protected, disabled by default,
// one provider send per request, no registrations or alarms.
import { DebugPushRequest, sendDebugPush } from "./alerts"
import { decodeUtf8, errorResponse, jsonResponse, MAX_V2_BODY_BYTES, readBodyLimited } from "./http"
import { checkRateLimit, clientIpBucket } from "./rate-limiter"
import { HEX64_RE, randomHex, sha256Hex } from "./signing"
import { Env } from "./types"
import { hasControlChars, isRecord, isRoutingId, pushTokenError } from "./validation"

export const DEBUG_RATE_LIMIT_PER_MINUTE = 20
// Per client IP bucket, before the admin token is checked (spec §13).
export const DEBUG_IP_RATE_LIMIT_PER_MINUTE = 30
export const MIN_DEBUG_ADMIN_TOKEN_LENGTH = 32
const DEBUG_RATE_LIMIT_KEY = "debug-push:global"
const MAX_TITLE_CHARS = 64
const MAX_BODY_CHARS = 200
const DEFAULT_TITLE = "调试通知"
const ALLOWED_FIELDS = new Set([
  "platform",
  "pushToken",
  "apnsEnvironment",
  "mode",
  "title",
  "body",
  "hostId",
  "threadId",
  "turnId",
])

export function debugPushEnabled(env: Env): boolean {
  return (
    env.DEBUG_PUSH_ENABLED === "true" &&
    typeof env.DEBUG_PUSH_ADMIN_TOKEN === "string" &&
    env.DEBUG_PUSH_ADMIN_TOKEN.length >= MIN_DEBUG_ADMIN_TOKEN_LENGTH
  )
}

// Compares SHA-256 digests so both inputs have equal length for timingSafeEqual.
async function isAuthorized(request: Request, adminToken: string): Promise<boolean> {
  const match = /^Bearer\s+(\S+)$/i.exec(request.headers.get("authorization") ?? "")
  const encoder = new TextEncoder()
  const [provided, expected] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(match ? match[1] : "")),
    crypto.subtle.digest("SHA-256", encoder.encode(adminToken)),
  ])
  return crypto.subtle.timingSafeEqual(provided, expected) && match !== null
}

function isText(value: unknown, maxChars: number): value is string {
  return (
    typeof value === "string" &&
    value.trim().length > 0 &&
    [...value].length <= maxChars &&
    !hasControlChars(value)
  )
}

function parseDebugRequest(raw: unknown): DebugPushRequest | string {
  if (!isRecord(raw)) return "body must be a JSON object"
  if (Object.keys(raw).some((key) => !ALLOWED_FIELDS.has(key))) return "body contains an unsupported field"
  const { platform, pushToken, apnsEnvironment, mode, title, body, hostId, threadId, turnId } = raw
  if (platform !== "ios" && platform !== "android") return 'platform must be "ios" or "android"'
  const tokenError = pushTokenError(platform, pushToken)
  if (tokenError) return tokenError
  let environment: "sandbox" | "production" | null = null
  if (platform === "ios") {
    if (apnsEnvironment !== "sandbox" && apnsEnvironment !== "production") {
      return 'apnsEnvironment must be "sandbox" or "production" for ios'
    }
    environment = apnsEnvironment
  } else if (apnsEnvironment !== undefined && apnsEnvironment !== null) {
    return "apnsEnvironment is only valid for ios"
  }
  if (mode !== "alert" && mode !== "background") return 'mode must be "alert" or "background"'
  if (title !== undefined && title !== null && !isText(title, MAX_TITLE_CHARS)) {
    return `title must be 1-${MAX_TITLE_CHARS} characters`
  }
  if (mode === "alert" ? !isText(body, MAX_BODY_CHARS) : body !== undefined && body !== null && !isText(body, MAX_BODY_CHARS)) {
    return `body must be 1-${MAX_BODY_CHARS} characters (required for alert)`
  }
  if (hostId !== undefined && hostId !== null && (typeof hostId !== "string" || !HEX64_RE.test(hostId))) {
    return "hostId must be 64 lowercase hex"
  }
  if (threadId !== undefined && threadId !== null && !isRoutingId(threadId)) return "threadId is invalid"
  if (turnId !== undefined && turnId !== null && !isRoutingId(turnId)) return "turnId is invalid"

  const routing: Record<string, string> = {}
  if (typeof hostId === "string") routing["agentbuddy.notification.serverId"] = `alleycat:${hostId}`
  if (typeof threadId === "string") routing["agentbuddy.notification.threadId"] = threadId
  if (typeof turnId === "string") routing["agentbuddy.notification.turnId"] = turnId

  return {
    platform,
    pushToken: pushToken as string,
    apnsEnvironment: environment,
    mode,
    title: typeof title === "string" ? title : DEFAULT_TITLE,
    body: typeof body === "string" ? body : "",
    routing,
  }
}

export async function handleDebugPush(request: Request, env: Env): Promise<Response> {
  if (!debugPushEnabled(env) || request.method !== "POST") return errorResponse("not_found")
  try {
    const ipRetryAfter = await checkRateLimit(
      env.RATE_LIMITER,
      `debug-ip:${clientIpBucket(request)}`,
      DEBUG_IP_RATE_LIMIT_PER_MINUTE
    )
    if (ipRetryAfter !== null) return errorResponse("rate_limited", { retryAfterSeconds: ipRetryAfter })
    if (!(await isAuthorized(request, env.DEBUG_PUSH_ADMIN_TOKEN as string))) {
      return errorResponse("unauthorized")
    }
    // The global budget is only spent by authorized requests.
    const retryAfter = await checkRateLimit(env.RATE_LIMITER, DEBUG_RATE_LIMIT_KEY, DEBUG_RATE_LIMIT_PER_MINUTE)
    if (retryAfter !== null) return errorResponse("rate_limited", { retryAfterSeconds: retryAfter })

    const bytes = await readBodyLimited(request, MAX_V2_BODY_BYTES)
    if (bytes === null) return errorResponse("payload_too_large")
    const text = decodeUtf8(bytes)
    if (text === null) return errorResponse("bad_request", { message: "body must be UTF-8" })
    let raw: unknown
    try {
      raw = JSON.parse(text)
    } catch {
      return errorResponse("bad_request", { message: "invalid JSON body" })
    }
    const parsed = parseDebugRequest(raw)
    if (typeof parsed === "string") return errorResponse("bad_request", { message: parsed })

    const requestId = `dbg_${randomHex(16)}`
    const result = await sendDebugPush(env, parsed)
    // Never log the token, the Authorization header, the text or any key.
    const tokenHash = (await sha256Hex(parsed.pushToken)).slice(0, 8)
    console.log(
      `debug push ${requestId} platform=${parsed.platform} mode=${parsed.mode}` +
        ` providerStatus=${result.providerStatus ?? "none"} token_sha256=${tokenHash}`
    )
    return jsonResponse({ requestId, ...result })
  } catch (err) {
    console.error(`debug push error: ${err instanceof Error ? err.name : "unknown"}`)
    return errorResponse("internal")
  }
}
