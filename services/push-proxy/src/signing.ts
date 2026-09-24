// Canonical strings and Ed25519 verification for the v2 push protocol.
// The exact byte layout is a cross-implementation contract with the host
// (alleycat) and the mobile client; see
// docs/superpowers/specs/2026-09-24-host-push-notifications-design.md §5 and §12.
import { Env } from "./types"

export const SIGNATURE_WINDOW_SECONDS = 300

export const HEX32_RE = /^[0-9a-f]{32}$/
export const HEX64_RE = /^[0-9a-f]{64}$/
export const HEX128_RE = /^[0-9a-f]{128}$/

export function bytesToHex(data: ArrayBuffer | Uint8Array): string {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data)
  let out = ""
  for (const b of bytes) out += b.toString(16).padStart(2, "0")
  return out
}

// Callers validate the input first (even-length hex).
export function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2)
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16)
  return out
}

export function randomHex(byteLength: number): string {
  return bytesToHex(crypto.getRandomValues(new Uint8Array(byteLength)))
}

export async function sha256Hex(data: string | ArrayBuffer | Uint8Array): Promise<string> {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data
  return bytesToHex(await crypto.subtle.digest("SHA-256", bytes))
}

export function withinSignatureWindow(timestampSeconds: number, nowSeconds: number): boolean {
  return Math.abs(nowSeconds - timestampSeconds) <= SIGNATURE_WINDOW_SECONDS
}

// §5: every v2 string is bound to the Worker origin (`scheme://host[:port]`),
// so a signature captured at one deployment cannot be replayed at another.
// PUSH_AUDIENCE overrides it when the public URL differs from request.url;
// an unparsable override is used verbatim and simply matches no signer.
export function workerAudience(request: Request, env: Pick<Env, "PUSH_AUDIENCE">): string {
  const override = env.PUSH_AUDIENCE?.trim()
  if (!override) return new URL(request.url).origin
  try {
    return new URL(override).origin
  } catch {
    return override
  }
}

// §5.2 — every host → Worker request.
export function hostRequestSigningString(
  aud: string,
  method: string,
  path: string,
  timestamp: string | number,
  nonce: string,
  bodySha256: string
): string {
  return ["agentbuddy-push-host-v2", aud, method, path, String(timestamp), nonce, bodySha256].join("\n")
}

export interface GrantFields {
  aud: string
  host: string
  device: string
  platform: "ios" | "android"
  environment: "sandbox" | "production" | "none"
  targetSha256: string
  agent: string
  thread: string
  turn: string
  issued: number
  expires: number
  nonce: string
}

// §5.1 — device grant authorizing one subscription; target_sha256 is the
// SHA-256 of the sealedTarget ASCII string (§5.5).
export function grantSigningString(g: GrantFields): string {
  return [
    "agentbuddy-push-grant-v2",
    `aud=${g.aud}`,
    `host=${g.host}`,
    `device=${g.device}`,
    `platform=${g.platform}`,
    `environment=${g.environment}`,
    `target_sha256=${g.targetSha256}`,
    `agent=${g.agent}`,
    `thread=${g.thread}`,
    `turn=${g.turn}`,
    `issued=${g.issued}`,
    `expires=${g.expires}`,
    `nonce=${g.nonce}`,
  ].join("\n")
}

export interface RevokeFields {
  aud: string
  host: string
  device: string
  scope: "all"
  timestamp: number
  nonce: string
}

// §5.3 — device-signed revocation of all its subscriptions on one host.
export function revokeSigningString(r: RevokeFields): string {
  return [
    "agentbuddy-push-revoke-v2",
    `aud=${r.aud}`,
    `host=${r.host}`,
    `device=${r.device}`,
    `scope=${r.scope}`,
    `timestamp=${r.timestamp}`,
    `nonce=${r.nonce}`,
  ].join("\n")
}

// RFC 8032 Ed25519 via WebCrypto. The public key is the 32-byte iroh node id
// (64 lowercase hex), the signature 64 bytes (128 lowercase hex).
export async function verifyEd25519(
  publicKeyHex: string,
  signatureHex: string,
  message: string
): Promise<boolean> {
  if (!HEX64_RE.test(publicKeyHex) || !HEX128_RE.test(signatureHex)) return false
  try {
    const key = await crypto.subtle.importKey(
      "raw",
      hexToBytes(publicKeyHex),
      { name: "Ed25519" },
      false,
      ["verify"]
    )
    return await crypto.subtle.verify(
      "Ed25519",
      key,
      hexToBytes(signatureHex),
      new TextEncoder().encode(message)
    )
  } catch {
    // Not a valid curve point, or the runtime rejected the key.
    return false
  }
}
