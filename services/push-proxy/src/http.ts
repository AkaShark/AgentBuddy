// Shared response and body helpers for the v2 and debug endpoints.

export type ErrorCode =
  | "bad_request"
  | "payload_too_large"
  | "unauthorized"
  | "forbidden"
  | "not_found"
  | "rate_limited"
  | "internal"

const ERROR_STATUS: Record<ErrorCode, number> = {
  bad_request: 400,
  payload_too_large: 413,
  unauthorized: 401,
  forbidden: 403,
  not_found: 404,
  rate_limited: 429,
  internal: 500,
}

export const MAX_V2_BODY_BYTES = 16 * 1024

export function jsonResponse(data: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json", ...headers },
  })
}

// `{"error":"<code>"}`, optionally with a short non-sensitive `message`.
export function errorResponse(
  code: ErrorCode,
  options: { retryAfterSeconds?: number; message?: string } = {}
): Response {
  const headers: Record<string, string> = {}
  if (code === "rate_limited") headers["retry-after"] = String(Math.max(1, options.retryAfterSeconds ?? 60))
  const body: Record<string, string> = { error: code }
  if (options.message) body.message = options.message
  return jsonResponse(body, ERROR_STATUS[code], headers)
}

// Reads at most `maxBytes`. Returns null when the declared Content-Length or
// the streamed body exceeds the limit, without buffering the remainder.
export async function readBodyLimited(request: Request, maxBytes: number): Promise<Uint8Array | null> {
  const declared = request.headers.get("content-length")
  if (declared !== null) {
    const length = Number(declared)
    if (Number.isFinite(length) && length > maxBytes) return null
  }
  if (request.body === null) return new Uint8Array(0)

  const reader = request.body.getReader()
  const chunks: Uint8Array[] = []
  let total = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    total += value.byteLength
    if (total > maxBytes) {
      await reader.cancel().catch(() => {})
      return null
    }
    chunks.push(value)
  }
  const out = new Uint8Array(total)
  let offset = 0
  for (const chunk of chunks) {
    out.set(chunk, offset)
    offset += chunk.byteLength
  }
  return out
}

export function decodeUtf8(bytes: Uint8Array): string | null {
  try {
    return new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes)
  } catch {
    return null
  }
}

// Parses an HTTP Retry-After value (delta seconds or HTTP-date) into seconds.
export function parseRetryAfter(value: string | null, nowMs: number): number | null {
  if (value === null) return null
  const trimmed = value.trim()
  if (/^\d+$/.test(trimmed)) return Number(trimmed)
  const date = Date.parse(trimmed)
  if (Number.isNaN(date)) return null
  return Math.max(0, Math.ceil((date - nowMs) / 1000))
}
