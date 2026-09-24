// Field validators shared by the v2 host endpoints and the debug endpoint.

export const AGENT_RE = /^[A-Za-z0-9._-]{1,64}$/
export const MAX_ROUTING_ID_BYTES = 128
export const MAX_IOS_TOKEN_LENGTH = 200
export const MAX_ANDROID_TOKEN_LENGTH = 4096

// Control characters (notably \n) would break the newline-delimited canonical
// strings that grants are signed over, so they are never accepted in ids.
const CONTROL_CHAR_RE = /[\u0000-\u001f\u007f]/

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

export function isUnixSeconds(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0
}

// threadId / turnId: non-empty, at most 128 UTF-8 bytes, no control characters.
export function isRoutingId(value: unknown): value is string {
  return (
    typeof value === "string" &&
    value.length > 0 &&
    !CONTROL_CHAR_RE.test(value) &&
    new TextEncoder().encode(value).length <= MAX_ROUTING_ID_BYTES
  )
}

export function hasControlChars(value: string): boolean {
  return CONTROL_CHAR_RE.test(value)
}

// APNs tokens are hex and end up in the APNs request path; FCM tokens are
// printable ASCII without whitespace.
export function pushTokenError(platform: "ios" | "android", token: unknown): string | null {
  if (typeof token !== "string") return "pushToken must be a string"
  if (platform === "ios") {
    return new RegExp(`^[0-9a-fA-F]{1,${MAX_IOS_TOKEN_LENGTH}}$`).test(token)
      ? null
      : `pushToken must be a hex APNs device token of at most ${MAX_IOS_TOKEN_LENGTH} characters`
  }
  return new RegExp(`^[\\x21-\\x7e]{1,${MAX_ANDROID_TOKEN_LENGTH}}$`).test(token)
    ? null
    : `pushToken must be a printable FCM token of at most ${MAX_ANDROID_TOKEN_LENGTH} characters`
}
