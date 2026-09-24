import { base64UrlEncode, pemToArrayBuffer } from "./crypto"
import { ContentState, Env } from "./types"

let cachedAccessToken: { token: string; expires: number } | null = null

async function getFCMAccessToken(env: Env): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  if (cachedAccessToken && now < cachedAccessToken.expires) return cachedAccessToken.token

  const header = base64UrlEncode(
    new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" }))
  )
  const claims = base64UrlEncode(
    new TextEncoder().encode(
      JSON.stringify({
        iss: env.FCM_CLIENT_EMAIL,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      })
    )
  )
  const signingInput = `${header}.${claims}`

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(env.FCM_PRIVATE_KEY),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  )
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput)
  )
  const jwt = `${signingInput}.${base64UrlEncode(signature)}`

  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  })
  const data = (await resp.json().catch(() => ({}))) as { access_token?: unknown }
  if (!resp.ok || typeof data.access_token !== "string" || data.access_token.length === 0) {
    // Never cache a failed exchange; the next push retries the token request.
    cachedAccessToken = null
    throw new Error(`FCM OAuth token request failed (${resp.status})`)
  }

  cachedAccessToken = { token: data.access_token, expires: now + 55 * 60 }
  return data.access_token
}

export async function sendFCMPush(
  env: Env,
  pushToken: string,
  contentState: ContentState
): Promise<{ ok: boolean; unregistered: boolean }> {
  let accessToken: string
  try {
    accessToken = await getFCMAccessToken(env)
  } catch (err) {
    // Report a soft failure so the alarm loop reschedules instead of throwing.
    console.log(`FCM token error: ${err instanceof Error ? err.message : String(err)}`)
    return { ok: false, unregistered: false }
  }

  const resp = await fetch(
    `https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${accessToken}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: pushToken,
          data: {
            type: "turn_keepalive",
            phase: contentState.phase ?? "thinking",
            elapsedSeconds: String(contentState.elapsedSeconds ?? 0),
            toolCallCount: String(contentState.toolCallCount ?? 0),
            activeThreadCount: String(contentState.activeThreadCount ?? 1),
            ...(contentState.serverId ? { serverId: contentState.serverId } : {}),
            ...(contentState.threadId ? { threadId: contentState.threadId } : {}),
          },
        },
      }),
    }
  )

  if (!resp.ok) {
    // A 401 means the cached access token was rejected; fetch a fresh one next time.
    if (resp.status === 401) cachedAccessToken = null
    const body = (await resp.json().catch(() => ({}))) as {
      error?: { details?: Array<{ errorCode?: string }> }
    }
    const unregistered = body.error?.details?.some((d) => d.errorCode === "UNREGISTERED") ?? false
    return { ok: false, unregistered }
  }

  return { ok: true, unregistered: false }
}
