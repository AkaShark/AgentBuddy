// Sealed push targets (spec §5.5). The phone encrypts its push token to the
// Worker's static X25519 key; the host only stores and forwards the opaque
// string, so it never sees the token.
//
//   sealedTarget = base64url-nopad( 0x01 ‖ kid ‖ epk(32) ‖ nonce(12) ‖ ciphertext+tag(16) )
//   shared = X25519(workerSk, epk)
//   key    = HKDF-SHA256(ikm = shared, salt = epk ‖ workerPk, info = "agentbuddy-push-target-v1", L = 32)
//   AES-256-GCM, AAD = "<hostId>|<deviceId>"
//
// Every failure (encoding, version, unknown kid, bad point, tag, plaintext,
// field mismatch) is reported to the caller as the same `null`.
import { base64UrlEncode } from "./crypto"
import { decodeUtf8 } from "./http"
import { HEX64_RE, hexToBytes } from "./signing"
import { Env } from "./types"
import { isRecord, pushTokenError } from "./validation"

export const SEALED_TARGET_VERSION = 0x01
export const MAX_SEALED_TARGET_LENGTH = 8192
const HKDF_INFO = new TextEncoder().encode("agentbuddy-push-target-v1")
const HEADER_BYTES = 1 + 1 + 32 + 12
const TAG_BYTES = 16
const BASE64URL_RE = /^[A-Za-z0-9_-]+$/
// PKCS#8 wrapper for a raw 32-byte X25519 private key (RFC 8410).
const X25519_PKCS8_PREFIX = hexToBytes("302e020100300506032b656e04220420")
const SEAL_KEY_ENTRY_RE = /^([0-9]{1,3}):([0-9a-fA-F]{64})$/

export interface SealKey {
  privateKey: CryptoKey
  publicKey: Uint8Array
}

export interface ExpectedTarget {
  hostId: string
  deviceId: string
  platform: "ios" | "android"
  apnsEnvironment: "sandbox" | "production" | null
}

// Thrown for a missing or malformed PUSH_TARGET_SEAL_KEY: a server
// misconfiguration (500), not a client error. Never carries key material.
export class SealKeyConfigError extends Error {
  constructor() {
    super("PUSH_TARGET_SEAL_KEY is missing or invalid")
    this.name = "SealKeyConfigError"
  }
}

// The Web Crypto spec names this member `public`; workers-types spells it `$public`.
function x25519(peer: CryptoKey): SubtleCryptoDeriveKeyAlgorithm {
  return { name: "X25519", public: peer } as unknown as SubtleCryptoDeriveKeyAlgorithm
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0))
  let offset = 0
  for (const part of parts) {
    out.set(part, offset)
    offset += part.length
  }
  return out
}

async function loadSealKeys(source: string): Promise<Map<number, SealKey>> {
  const entries = source.split(",").map((entry) => entry.trim()).filter(Boolean)
  if (entries.length === 0) throw new SealKeyConfigError()
  const keys = new Map<number, SealKey>()
  // X25519(sk, 9) is the public key; deriving it keeps the private key non-extractable.
  const basePoint = new Uint8Array(32)
  basePoint[0] = 9
  const base = await crypto.subtle.importKey("raw", basePoint, { name: "X25519" }, false, [])
  for (const entry of entries) {
    const match = SEAL_KEY_ENTRY_RE.exec(entry)
    const kid = match ? Number(match[1]) : NaN
    if (!match || kid > 255 || keys.has(kid)) throw new SealKeyConfigError()
    try {
      const privateKey = await crypto.subtle.importKey(
        "pkcs8",
        concat(X25519_PKCS8_PREFIX, hexToBytes(match[2].toLowerCase())),
        { name: "X25519" },
        false,
        ["deriveBits"]
      )
      const publicKey = new Uint8Array(await crypto.subtle.deriveBits(x25519(base), privateKey, 256))
      keys.set(kid, { privateKey, publicKey })
    } catch {
      throw new SealKeyConfigError()
    }
  }
  return keys
}

let cachedKeys: { source: string; keys: Promise<Map<number, SealKey>> } | null = null

// Parsed once per isolate (and again whenever the secret changes).
export function sealKeys(env: Pick<Env, "PUSH_TARGET_SEAL_KEY">): Promise<Map<number, SealKey>> {
  const source = env.PUSH_TARGET_SEAL_KEY ?? ""
  if (cachedKeys === null || cachedKeys.source !== source) {
    cachedKeys = { source, keys: loadSealKeys(source) }
  }
  return cachedKeys.keys
}

// Strict unpadded base64url: alphabet only, no impossible length, and the
// decoded bytes must re-encode to the identical string (no stray pad bits).
export function decodeBase64Url(text: string): Uint8Array | null {
  if (!BASE64URL_RE.test(text) || text.length % 4 === 1) return null
  let binary: string
  try {
    binary = atob(text.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (text.length % 4)) % 4))
  } catch {
    return null
  }
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return base64UrlEncode(bytes) === text ? bytes : null
}

// Returns the push token when the sealed target opens under one of the
// configured keys and its plaintext matches `expected`; otherwise null.
// Throws SealKeyConfigError when the Worker key itself is not configured.
export async function openSealedTarget(
  env: Pick<Env, "PUSH_TARGET_SEAL_KEY">,
  sealedTarget: string,
  expected: ExpectedTarget
): Promise<string | null> {
  const keys = await sealKeys(env)
  const raw = decodeBase64Url(sealedTarget)
  if (raw === null || raw.length <= HEADER_BYTES + TAG_BYTES || raw[0] !== SEALED_TARGET_VERSION) return null
  const key = keys.get(raw[1])
  if (!key) return null
  const epk = raw.subarray(2, 34)
  const nonce = raw.subarray(34, HEADER_BYTES)
  const ciphertext = raw.subarray(HEADER_BYTES)

  let plaintext: ArrayBuffer
  try {
    const peer = await crypto.subtle.importKey("raw", epk, { name: "X25519" }, false, [])
    // Rejects low-order points (all-zero shared secret).
    const shared = await crypto.subtle.deriveBits(x25519(peer), key.privateKey, 256)
    const ikm = await crypto.subtle.importKey("raw", shared, "HKDF", false, ["deriveBits"])
    const aesKeyBytes = await crypto.subtle.deriveBits(
      { name: "HKDF", hash: "SHA-256", salt: concat(epk, key.publicKey), info: HKDF_INFO },
      ikm,
      256
    )
    const aesKey = await crypto.subtle.importKey("raw", aesKeyBytes, { name: "AES-GCM" }, false, ["decrypt"])
    plaintext = await crypto.subtle.decrypt(
      {
        name: "AES-GCM",
        iv: nonce,
        additionalData: new TextEncoder().encode(`${expected.hostId}|${expected.deviceId}`),
        tagLength: 128,
      },
      aesKey,
      ciphertext
    )
  } catch {
    return null
  }

  const text = decodeUtf8(new Uint8Array(plaintext))
  if (text === null) return null
  let target: unknown
  try {
    target = JSON.parse(text)
  } catch {
    return null
  }
  if (!isRecord(target) || target.v !== 1) return null
  const { platform, token, apnsEnvironment, deviceId, hostId } = target
  if (
    platform !== expected.platform ||
    typeof deviceId !== "string" || !HEX64_RE.test(deviceId) || deviceId !== expected.deviceId ||
    typeof hostId !== "string" || !HEX64_RE.test(hostId) || hostId !== expected.hostId ||
    pushTokenError(expected.platform, token) !== null
  ) {
    return null
  }
  const environment = apnsEnvironment === undefined ? null : apnsEnvironment
  if (environment !== expected.apnsEnvironment) return null
  return token as string
}
