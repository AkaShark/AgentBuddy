// Visible turn-completion alerts (spec §7.3 APNs, §7.4 FCM) and the single-shot
// debug sender (§7.5). The legacy silent keepalive push stays in apns.ts/fcm.ts.
import { apnsHost, clearAPNsJWTCache, generateAPNsJWT } from "./apns"
import { clearFCMAccessTokenCache, getFCMAccessToken } from "./fcm"
import { parseRetryAfter } from "./http"
import { sha256Hex } from "./signing"
import { Env } from "./types"

export const DEFAULT_APNS_TOPIC = "com.akashark.agentbuddy"
export const ALERT_TTL_SECONDS = 86400
export const APNS_TURN_CATEGORY = "agentbuddy.task.complete"
export const ANDROID_TURN_CHANNEL_ID = "turn_complete"
const PROVIDER_TIMEOUT_MS = 10_000

const APNS_INVALID_TOKEN_REASONS = new Set(["BadDeviceToken", "DeviceTokenNotForTopic", "Unregistered"])
const APNS_PROVIDER_TOKEN_REASONS = new Set(["ExpiredProviderToken", "InvalidProviderToken"])

export type Platform = "ios" | "android"
export type ApnsEnvironment = "sandbox" | "production"
export type TurnKind = "completed" | "failed"

export interface PushTarget {
  platform: Platform
  pushToken: string
  apnsEnvironment: ApnsEnvironment | null
}

export interface TurnAlert {
  hostId: string
  threadId: string
  turnId: string
  eventId: string
  kind: TurnKind
}

export type DeliveryState = "sent" | "retrying" | "invalid_token" | "failed"

export interface DeliveryOutcome {
  state: DeliveryState
  providerStatus: number | null
  reason: string | null
  retryAfterSeconds: number | null
}

type ProviderResult =
  | { kind: "response"; status: number; reason: string | null; tokenError: boolean; retryAfterSeconds: number | null }
  | { kind: "credentials_error" }
  | { kind: "network_error" }

export function apnsTopic(env: Env): string {
  const topic = env.APNS_TOPIC?.trim()
  return topic ? topic : DEFAULT_APNS_TOPIC
}

// "t-" + first 32 hex of sha256(hostId|threadId|turnId): APNs collapse-id and
// the Android notification tag / collapse_key, so duplicates merge on device.
export async function turnCollapseKey(hostId: string, threadId: string, turnId: string): Promise<string> {
  return `t-${(await sha256Hex(`${hostId}|${threadId}|${turnId}`)).slice(0, 32)}`
}

export function turnAlertText(kind: TurnKind): { title: string; body: string } {
  return kind === "completed"
    ? { title: "任务已完成", body: "点击查看结果" }
    : { title: "任务未完成", body: "任务失败或已中断，点击查看详情" }
}

// Identical keys for APNs custom payload keys and FCM data.
export function turnRoutingData(alert: TurnAlert): Record<string, string> {
  return {
    "agentbuddy.notification.serverId": `alleycat:${alert.hostId}`,
    "agentbuddy.notification.threadId": alert.threadId,
    "agentbuddy.notification.turnId": alert.turnId,
    "agentbuddy.notification.kind": alert.kind,
    "agentbuddy.notification.eventId": alert.eventId,
  }
}

async function apnsSend(
  env: Env,
  pushToken: string,
  environment: ApnsEnvironment,
  headers: Record<string, string>,
  payload: unknown
): Promise<ProviderResult> {
  let jwt: string
  try {
    jwt = await generateAPNsJWT(env)
  } catch {
    return { kind: "credentials_error" }
  }
  let resp: Response
  try {
    resp = await fetch(`${apnsHost(environment)}/3/device/${pushToken}`, {
      method: "POST",
      headers: { authorization: `bearer ${jwt}`, ...headers },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MS),
    })
  } catch {
    return { kind: "network_error" }
  }
  let reason: string | null = null
  if (resp.status !== 200) {
    const body = (await resp.json().catch(() => ({}))) as { reason?: unknown }
    reason = typeof body.reason === "string" ? body.reason : null
  } else {
    // Release the connection; the success body is not needed.
    await resp.body?.cancel().catch(() => {})
  }
  return {
    kind: "response",
    status: resp.status,
    reason,
    tokenError: resp.status === 410 || (resp.status === 400 && reason !== null && APNS_INVALID_TOKEN_REASONS.has(reason)),
    retryAfterSeconds: parseRetryAfter(resp.headers.get("retry-after"), Date.now()),
  }
}

interface FcmErrorBody {
  error?: {
    status?: unknown
    message?: unknown
    details?: Array<{ errorCode?: unknown; fieldViolations?: Array<{ field?: unknown }> }>
  }
}

async function fcmSend(env: Env, message: unknown): Promise<ProviderResult> {
  let accessToken: string
  try {
    accessToken = await getFCMAccessToken(env)
  } catch {
    return { kind: "credentials_error" }
  }
  let resp: Response
  try {
    resp = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
      body: JSON.stringify({ message }),
      signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MS),
    })
  } catch {
    return { kind: "network_error" }
  }
  const retryAfterSeconds = parseRetryAfter(resp.headers.get("retry-after"), Date.now())
  if (resp.ok) {
    await resp.body?.cancel().catch(() => {})
    return { kind: "response", status: resp.status, reason: null, tokenError: false, retryAfterSeconds }
  }

  const body = (await resp.json().catch(() => ({}))) as FcmErrorBody
  const details = Array.isArray(body.error?.details) ? body.error.details : []
  const errorCode = details.map((d) => d?.errorCode).find((c): c is string => typeof c === "string") ?? null
  const status = typeof body.error?.status === "string" ? body.error.status : null
  // google.rpc.BadRequest detail naming the token field.
  const tokenField = details.some((d) =>
    Array.isArray(d?.fieldViolations) && d.fieldViolations.some((v) => v?.field === "message.token")
  )
  const invalidArgument = errorCode === "INVALID_ARGUMENT" || status === "INVALID_ARGUMENT"
  // Spec §13: only UNREGISTERED, or INVALID_ARGUMENT that names message.token,
  // means the token is dead. A bare 404 / NOT_FOUND (e.g. a wrong project) is
  // not proof, so it is a permanent failure that keeps the subscription's peers.
  const tokenError = errorCode === "UNREGISTERED" || (invalidArgument && tokenField)
  return { kind: "response", status: resp.status, reason: errorCode ?? status, tokenError, retryAfterSeconds }
}

function classify(result: ProviderResult): DeliveryOutcome {
  if (result.kind !== "response") {
    return { state: "retrying", providerStatus: null, reason: result.kind, retryAfterSeconds: null }
  }
  const base = { providerStatus: result.status, reason: result.reason, retryAfterSeconds: result.retryAfterSeconds }
  if (result.status >= 200 && result.status < 300) return { state: "sent", ...base }
  if (result.tokenError) return { state: "invalid_token", ...base }
  if (result.status === 429 || result.status >= 500) return { state: "retrying", ...base }
  return { state: "failed", ...base }
}

function isApnsProviderTokenError(result: ProviderResult): boolean {
  return result.kind === "response" && result.status === 403 && result.reason !== null &&
    APNS_PROVIDER_TOKEN_REASONS.has(result.reason)
}

function isFcmAuthError(result: ProviderResult): boolean {
  return result.kind === "response" && result.status === 401
}

export async function sendTurnAlert(env: Env, target: PushTarget, alert: TurnAlert): Promise<DeliveryOutcome> {
  const collapseKey = await turnCollapseKey(alert.hostId, alert.threadId, alert.turnId)
  const { title, body } = turnAlertText(alert.kind)
  const data = turnRoutingData(alert)

  if (target.platform === "ios") {
    const environment = target.apnsEnvironment ?? "production"
    const headers = {
      "apns-push-type": "alert",
      "apns-priority": "10",
      "apns-expiration": String(Math.floor(Date.now() / 1000) + ALERT_TTL_SECONDS),
      "apns-collapse-id": collapseKey,
      "apns-topic": apnsTopic(env),
    }
    const payload = {
      aps: {
        alert: { title, body },
        sound: "default",
        "thread-id": alert.threadId,
        category: APNS_TURN_CATEGORY,
      },
      ...data,
    }
    let result = await apnsSend(env, target.pushToken, environment, headers, payload)
    if (isApnsProviderTokenError(result)) {
      clearAPNsJWTCache()
      result = await apnsSend(env, target.pushToken, environment, headers, payload)
    }
    return classify(result)
  }

  const message = {
    token: target.pushToken,
    data,
    android: {
      priority: "HIGH",
      ttl: `${ALERT_TTL_SECONDS}s`,
      collapse_key: collapseKey,
      notification: { channel_id: ANDROID_TURN_CHANNEL_ID, tag: collapseKey, title, body },
    },
  }
  let result = await fcmSend(env, message)
  if (isFcmAuthError(result)) {
    clearFCMAccessTokenCache()
    result = await fcmSend(env, message)
  }
  return classify(result)
}

export interface DebugPushRequest {
  platform: Platform
  pushToken: string
  apnsEnvironment: ApnsEnvironment | null
  mode: "alert" | "background"
  title: string
  body: string
  routing: Record<string, string>
}

export interface DebugPushResult {
  provider: "apns" | "fcm"
  accepted: boolean
  providerStatus: number | null
  error: string | null
}

function debugResult(provider: "apns" | "fcm", result: ProviderResult): DebugPushResult {
  if (result.kind !== "response") {
    return { provider, accepted: false, providerStatus: null, error: result.kind }
  }
  const accepted = result.status >= 200 && result.status < 300
  const reason = (result.reason ?? `http_${result.status}`).replace(/[^A-Za-z0-9_.-]/g, "").slice(0, 64)
  return { provider, accepted, providerStatus: result.status, error: accepted ? null : reason }
}

// Exactly one provider send per call: no retry, no stored state. A rejected
// provider credential is still dropped from the cache for the next call.
export async function sendDebugPush(env: Env, req: DebugPushRequest): Promise<DebugPushResult> {
  if (req.platform === "ios") {
    const environment = req.apnsEnvironment ?? "production"
    const alert = req.mode === "alert"
    const headers: Record<string, string> = alert
      ? {
          "apns-push-type": "alert",
          "apns-priority": "10",
          "apns-expiration": String(Math.floor(Date.now() / 1000) + ALERT_TTL_SECONDS),
          "apns-topic": apnsTopic(env),
        }
      : { "apns-push-type": "background", "apns-priority": "5", "apns-topic": apnsTopic(env) }
    const aps = alert
      ? { alert: { title: req.title, body: req.body }, sound: "default" }
      : { "content-available": 1 }
    const result = await apnsSend(env, req.pushToken, environment, headers, { aps, ...req.routing })
    if (isApnsProviderTokenError(result)) clearAPNsJWTCache()
    return debugResult("apns", result)
  }

  const message =
    req.mode === "alert"
      ? {
          token: req.pushToken,
          data: req.routing,
          android: {
            priority: "HIGH",
            ttl: `${ALERT_TTL_SECONDS}s`,
            notification: { channel_id: ANDROID_TURN_CHANNEL_ID, title: req.title, body: req.body },
          },
        }
      : {
          token: req.pushToken,
          data: { type: "debug_background", ...req.routing },
          android: { priority: "HIGH" },
        }
  const result = await fcmSend(env, message)
  if (isFcmAuthError(result)) clearFCMAccessTokenCache()
  return debugResult("fcm", result)
}
