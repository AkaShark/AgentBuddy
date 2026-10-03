import type { DeliveryOutcome } from "./alerts"
import { clearHarmonyJWTCache, harmonyAuthorization } from "./harmony-auth"
import { parseRetryAfter } from "./http"
import { Env } from "./types"

// HarmonyOS Push Kit v3: notification and response definitions are documented at
// https://developer.huawei.com/consumer/cn/doc/doccenter-references/api/push-scenariozed-api-request-param
// https://developer.huawei.com/consumer/cn/doc/doccenter-references/api/push-scenariozed-api-response
export interface HarmonyAlert {
  title: string
  body: string
  routing: Record<string, string>
  collapseKey: string
}

export function harmonyPayload(env: Env, token: string, alert: HarmonyAlert): unknown {
  return {
    payload: { notification: {
      category: "WORK", title: alert.title, body: alert.body,
      foregroundShow: false,
      // The same terminal event cannot alert twice, including provider retries.
      appMessageId: alert.collapseKey,
      notifyId: Number.parseInt(alert.collapseKey.slice(2, 10), 16) & 0x7fffffff,
      clickAction: { actionType: 0, data: alert.routing },
    } },
    target: { token: [token] },
    pushOptions: { ttl: 86400, ...(env.HARMONY_TEST_MESSAGE === "true" ? { testMessage: true } : {}) },
  }
}

export async function sendHarmonyAlert(env: Env, token: string, alert: HarmonyAlert, retryAuth = true): Promise<DeliveryOutcome> {
  let auth: { token: string; projectId: string }
  try { auth = await harmonyAuthorization(env) }
  catch { return { state: "retrying", providerStatus: null, reason: "credentials_error", retryAfterSeconds: null } }
  let response: Response
  try {
    response = await fetch(`https://push-api.cloud.huawei.com/v3/${auth.projectId}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${auth.token}`, "content-type": "application/json", "push-type": "0" },
      body: JSON.stringify(harmonyPayload(env, token, alert)),
      signal: AbortSignal.timeout(10_000),
    })
  } catch { return { state: "retrying", providerStatus: null, reason: "network_error", retryAfterSeconds: null } }
  const body = await response.json().catch(() => null) as { code?: unknown; msg?: unknown } | null
  const code = typeof body?.code === "string" && /^\d{8}$/.test(body.code) ? body.code : null
  const base = { providerStatus: response.status, reason: code ?? `http_${response.status}`,
    retryAfterSeconds: parseRetryAfter(response.headers.get("retry-after"), Date.now()) }
  if (response.status === 401 || code === "80200005" || code === "80200001") {
    clearHarmonyJWTCache()
    if (retryAuth) return sendHarmonyAlert(env, token, alert, false)
  }
  if (response.ok && code === "80000000") return { ...base, state: "sent", reason: null }
  // 80300007 also covers wrong project / missing rights. Only a token-specific
  // format rejection proves this registration is unusable; never prune peers
  // for a provider configuration error. Do not log msg (it can contain tokens).
  if ((code === "80300007" || code === "80100000") && typeof body?.msg === "string") {
    try {
      const detail = JSON.parse(body.msg) as { illegalTokens?: { tokenFormatError?: unknown } }
      const invalid = detail?.illegalTokens?.tokenFormatError
      if (Array.isArray(invalid) && invalid.includes(token)) return { ...base, state: "invalid_token" }
    } catch { /* Invalid provider diagnostic; retain the registration. */ }
  }
  const retry = response.status === 429 || response.status >= 500 || code === "81000001" ||
    code === "80300029" || (response.ok && code === null)
  return { ...base, state: retry ? "retrying" : "failed" }
}
