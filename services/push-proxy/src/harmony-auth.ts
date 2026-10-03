import { base64UrlEncode, pemToArrayBuffer } from "./crypto"
import { Env } from "./types"

// Huawei Push Kit v3 consumes this PS256 JWT directly as the bearer token.
// https://developer.huawei.com/consumer/cn/doc/doccenter-capabilities/push-jwt-token
const AUDIENCE = "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
interface ServiceAccount {
  project_id: string
  key_id: string
  sub_account: string
  private_key: string
}

let cached: { source: string; token: string; expires: number; projectId: string } | undefined

export function clearHarmonyJWTCache(): void { cached = undefined }

export async function harmonyAuthorization(env: Env): Promise<{ token: string; projectId: string }> {
  const source = env.HARMONY_SERVICE_ACCOUNT
  if (!source) throw new Error("harmony_credentials_missing")
  const now = Math.floor(Date.now() / 1000)
  if (cached?.source === source && now < cached.expires) {
    return { token: cached.token, projectId: cached.projectId }
  }
  // Do not leak the JSON, private key, or parser errors into delivery logs.
  let account: ServiceAccount
  try {
    account = JSON.parse(source) as ServiceAccount
    if (!account || typeof account.project_id !== "string" || !/^[0-9]+$/.test(account.project_id) ||
      ![account.key_id, account.sub_account, account.private_key].every(v => typeof v === "string" && v.trim().length > 0)) {
      throw new Error()
    }
  } catch { throw new Error("harmony_credentials_invalid") }
  const encode = (value: unknown) => base64UrlEncode(new TextEncoder().encode(JSON.stringify(value)))
  const input = `${encode({ alg: "PS256", typ: "JWT", kid: account.key_id })}.${encode({
    aud: AUDIENCE, iss: account.sub_account, iat: now, exp: now + 3600,
  })}`
  let signature: ArrayBuffer
  try {
    const key = await crypto.subtle.importKey("pkcs8", pemToArrayBuffer(account.private_key),
      { name: "RSA-PSS", hash: "SHA-256" }, false, ["sign"])
    signature = await crypto.subtle.sign({ name: "RSA-PSS", saltLength: 32 }, key, new TextEncoder().encode(input))
  } catch { throw new Error("harmony_credentials_invalid") }
  const token = `${input}.${base64UrlEncode(signature)}`
  cached = { source, token, expires: now + 3300, projectId: account.project_id }
  return { token, projectId: account.project_id }
}
