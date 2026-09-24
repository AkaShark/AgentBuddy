// HostChannel: one Durable Object per host (idFromName(hostId)), spec §7.2.
// It owns that host's subscriptions, the host nonce replay cache, grant nonces,
// device revocation points, per-event delivery records and delivery retries.
// The Worker entry has already verified the host (or device) signature and
// validated the payload before a request reaches this object.
import { ApnsEnvironment, DeliveryState, Platform, sendTurnAlert, TurnKind } from "./alerts"
import { errorResponse, jsonResponse } from "./http"
import { grantSigningString, HEX32_RE, randomHex, sha256Hex, SIGNATURE_WINDOW_SECONDS, verifyEd25519 } from "./signing"
import { Env } from "./types"

export const MAX_GRANT_LIFETIME_SECONDS = 48 * 3600
export const HOST_NONCE_TTL_MS = 10 * 60_000
export const EVENT_RECORD_TTL_MS = 48 * 3600_000
// Any grant issued before a revocation point has expired 48h later.
export const REVOCATION_TTL_MS = (MAX_GRANT_LIFETIME_SECONDS + 3600) * 1000
export const RATE_WINDOW_MS = 60_000
export const RATE_LIMITS = { subscriptions: 60, events: 120, deletes: 120 } as const
export const MAX_DELIVERY_ATTEMPTS = 8
export const RETRY_BASE_MS = 30_000
export const RETRY_MAX_MS = 30 * 60_000
export const RETRY_AFTER_MAX_MS = 60 * 60_000
// A send in progress is recorded as a due-later retry first, so a crash
// between the provider call and the result write is retried by the alarm.
export const SEND_LEASE_MS = 60_000
const STORAGE_BATCH = 128

export interface SubscriptionInput {
  deviceId: string
  platform: Platform
  pushToken: string
  apnsEnvironment: ApnsEnvironment | null
  agent: string
  threadId: string
  turnId: string
  issuedAt: number
  expiresAt: number
  grantNonce: string
  grantSignature: string
}

export interface EventInput {
  eventId: string
  agent: string
  threadId: string
  turnId: string
  type: TurnKind
  reason: "interrupted" | "error" | null
  occurredAt: number
}

export interface RevokeInput {
  deviceId: string
  timestamp: number
}

interface Subscription {
  subscriptionId: string
  hostId: string
  deviceId: string
  platform: Platform
  pushToken: string
  apnsEnvironment: ApnsEnvironment | null
  agent: string
  threadId: string
  turnId: string
  issuedAt: number // unix seconds
  expiresAt: number // unix seconds
  grantNonce: string
  createdAt: number // ms
}

interface DeliveryRecord {
  state: DeliveryState
  attempts: number
  providerStatus: number | null
  updatedAt: number // ms
  exp: number // ms
}

interface PendingDelivery {
  eventId: string
  subscriptionId: string
  type: TurnKind
  reason: "interrupted" | "error" | null
  occurredAt: number
  attempts: number
  nextAt: number // ms
}

type RateBucket = keyof typeof RATE_LIMITS

function turnPrefix(agent: string, threadId: string, turnId: string): string {
  // thread/turn ids are free-form strings; encoding keeps the key unambiguous.
  return `idx:${agent}|${encodeURIComponent(threadId)}|${encodeURIComponent(turnId)}|`
}

function indexKey(sub: { agent: string; threadId: string; turnId: string; deviceId: string }): string {
  return `${turnPrefix(sub.agent, sub.threadId, sub.turnId)}${sub.deviceId}`
}

function deliveryRecord(state: DeliveryState, attempts: number, providerStatus: number | null, now: number): DeliveryRecord {
  return { state, attempts, providerStatus, updatedAt: now, exp: now + EVENT_RECORD_TTL_MS }
}

export class HostChannel implements DurableObject {
  private state: DurableObjectState
  private env: Env

  constructor(state: DurableObjectState, env: Env) {
    this.state = state
    this.env = env
  }

  async fetch(request: Request): Promise<Response> {
    try {
      const url = new URL(request.url)
      const parts = url.pathname.split("/").filter(Boolean)
      const hostId = request.headers.get("x-agentbuddy-host") ?? ""
      const nonce = request.headers.get("x-agentbuddy-nonce") ?? ""

      let op: "subscribe" | "unsubscribe" | "event" | "revoke"
      if (request.method === "POST" && parts.length === 1 && parts[0] === "subscriptions") op = "subscribe"
      else if (request.method === "DELETE" && parts.length === 2 && parts[0] === "subscriptions") op = "unsubscribe"
      else if (request.method === "POST" && parts.length === 1 && parts[0] === "events") op = "event"
      else if (request.method === "POST" && parts.length === 1 && parts[0] === "revoke") op = "revoke"
      else return errorResponse("not_found")

      // Replay cache first: a nonce seen in the last 10 minutes is rejected
      // before any rate-limit or state change.
      if (!HEX32_RE.test(nonce) || (await this.state.storage.get(`nonce:${nonce}`)) !== undefined) {
        return errorResponse("unauthorized")
      }
      if (op !== "revoke") {
        const bucket: RateBucket = op === "subscribe" ? "subscriptions" : op === "event" ? "events" : "deletes"
        const retryAfter = await this.consumeRate(bucket)
        if (retryAfter !== null) return errorResponse("rate_limited", { retryAfterSeconds: retryAfter })
      }
      await this.rememberNonce(nonce)

      switch (op) {
        case "subscribe":
          return await this.subscribe(hostId, (await request.json()) as SubscriptionInput)
        case "unsubscribe":
          return await this.unsubscribe(parts[1])
        case "event":
          return await this.event((await request.json()) as EventInput)
        case "revoke":
          return await this.revoke((await request.json()) as RevokeInput)
      }
    } catch (err) {
      console.error(`HostChannel error: ${err instanceof Error ? err.message : String(err)}`)
      return errorResponse("internal")
    }
  }

  async alarm(): Promise<void> {
    await this.processDueRetries()
    await this.cleanup()
  }

  // --- subscriptions ---------------------------------------------------------

  private async subscribe(hostId: string, input: SubscriptionInput): Promise<Response> {
    const nowMs = Date.now()
    const nowSec = Math.floor(nowMs / 1000)

    // §5.1 grant rules. `host` is bound by putting the authenticated signer's
    // hostId into the canonical string; the token by hashing the submitted one.
    if (input.expiresAt <= input.issuedAt || input.expiresAt - input.issuedAt > MAX_GRANT_LIFETIME_SECONDS) {
      return errorResponse("forbidden", { message: "grant lifetime must be positive and at most 48h" })
    }
    if (input.expiresAt <= nowSec) return errorResponse("forbidden", { message: "grant expired" })
    if (input.issuedAt > nowSec + SIGNATURE_WINDOW_SECONDS) {
      return errorResponse("forbidden", { message: "grant issued in the future" })
    }
    const revocation = await this.state.storage.get<{ at: number }>(`revoked:${input.deviceId}`)
    if (revocation && input.issuedAt < revocation.at) {
      return errorResponse("forbidden", { message: "grant issued before device revocation" })
    }
    const signed = grantSigningString({
      host: hostId,
      device: input.deviceId,
      platform: input.platform,
      environment: input.platform === "ios" ? input.apnsEnvironment ?? "production" : "none",
      tokenSha256: await sha256Hex(input.pushToken),
      agent: input.agent,
      thread: input.threadId,
      turn: input.turnId,
      issued: input.issuedAt,
      expires: input.expiresAt,
      nonce: input.grantNonce,
    })
    if (!(await verifyEd25519(input.deviceId, input.grantSignature, signed))) {
      return errorResponse("forbidden", { message: "grant signature invalid" })
    }

    const idx = indexKey(input)
    const existingId = await this.state.storage.get<string>(idx)
    let existing = existingId ? await this.state.storage.get<Subscription>(`sub:${existingId}`) : undefined
    if (existing && existing.expiresAt <= nowSec) {
      await this.removeSubscription(existing, "failed")
      existing = undefined
    }

    // Same unique key + same grant: an idempotent retry from the host outbox.
    if (existing && existing.grantNonce === input.grantNonce) {
      return jsonResponse({ subscriptionId: existing.subscriptionId, expiresAt: existing.expiresAt }, 200)
    }
    const grantKey = `grant:${input.grantNonce}`
    if ((await this.state.storage.get(grantKey)) !== undefined) {
      return errorResponse("forbidden", { message: "grant nonce already used" })
    }
    const grantRecord = { exp: input.expiresAt * 1000 }

    if (existing) {
      // Same unique key with a newer grant (e.g. a rotated push token): keep the
      // subscription id and adopt the newer target and expiry.
      let current = existing
      const puts: Record<string, unknown> = { [grantKey]: grantRecord }
      if (input.issuedAt >= existing.issuedAt) {
        current = {
          ...existing,
          platform: input.platform,
          pushToken: input.pushToken,
          apnsEnvironment: input.apnsEnvironment,
          issuedAt: input.issuedAt,
          expiresAt: input.expiresAt,
          grantNonce: input.grantNonce,
        }
        puts[`sub:${current.subscriptionId}`] = current
      }
      await this.state.storage.put(puts)
      await this.ensureAlarm(Math.min(current.expiresAt, input.expiresAt) * 1000)
      return jsonResponse({ subscriptionId: current.subscriptionId, expiresAt: current.expiresAt }, 200)
    }

    const sub: Subscription = {
      subscriptionId: `sub_${randomHex(16)}`,
      hostId,
      deviceId: input.deviceId,
      platform: input.platform,
      pushToken: input.pushToken,
      apnsEnvironment: input.apnsEnvironment,
      agent: input.agent,
      threadId: input.threadId,
      turnId: input.turnId,
      issuedAt: input.issuedAt,
      expiresAt: input.expiresAt,
      grantNonce: input.grantNonce,
      createdAt: nowMs,
    }
    await this.state.storage.put({ [`sub:${sub.subscriptionId}`]: sub, [idx]: sub.subscriptionId, [grantKey]: grantRecord })
    await this.ensureAlarm(sub.expiresAt * 1000)
    return jsonResponse({ subscriptionId: sub.subscriptionId, expiresAt: sub.expiresAt }, 201)
  }

  // Idempotent; ids that are not in this host's object are simply not found.
  private async unsubscribe(subscriptionId: string): Promise<Response> {
    const sub = await this.state.storage.get<Subscription>(`sub:${subscriptionId}`)
    if (sub) await this.removeSubscription(sub, "failed")
    return jsonResponse({ ok: true })
  }

  private async revoke(input: RevokeInput): Promise<Response> {
    const subs = await this.state.storage.list<Subscription>({ prefix: "sub:" })
    let revoked = 0
    for (const sub of subs.values()) {
      if (sub.deviceId !== input.deviceId) continue
      await this.removeSubscription(sub, "failed")
      revoked++
    }
    const key = `revoked:${input.deviceId}`
    const previous = await this.state.storage.get<{ at: number }>(key)
    const at = Math.max(previous?.at ?? 0, input.timestamp)
    const record = { at, exp: at * 1000 + REVOCATION_TTL_MS }
    await this.state.storage.put(key, record)
    await this.ensureAlarm(record.exp)
    return jsonResponse({ revoked })
  }

  // Deletes the subscription, its index entry and its pending retries; the
  // pending deliveries are finalized with `pendingState`.
  private async removeSubscription(sub: Subscription, pendingState: DeliveryState): Promise<void> {
    const keys = [`sub:${sub.subscriptionId}`]
    const idx = indexKey(sub)
    if ((await this.state.storage.get<string>(idx)) === sub.subscriptionId) keys.push(idx)
    const now = Date.now()
    const finalized: Record<string, DeliveryRecord> = {}
    const retries = await this.state.storage.list<PendingDelivery>({ prefix: "retry:" })
    for (const [key, pending] of retries) {
      if (pending.subscriptionId !== sub.subscriptionId) continue
      keys.push(key)
      finalized[`evt:${pending.eventId}|${sub.subscriptionId}`] = deliveryRecord(pendingState, pending.attempts, null, now)
    }
    await this.deleteKeys(keys)
    await this.putAll(finalized)
  }

  private async removeSubscriptionsWithToken(platform: Platform, pushToken: string): Promise<void> {
    const subs = await this.state.storage.list<Subscription>({ prefix: "sub:" })
    for (const sub of subs.values()) {
      if (sub.platform === platform && sub.pushToken === pushToken) {
        await this.removeSubscription(sub, "invalid_token")
      }
    }
  }

  // --- events ------------------------------------------------------------------

  private async event(input: EventInput): Promise<Response> {
    const nowSec = Math.floor(Date.now() / 1000)
    const results: Array<{ subscriptionId: string; state: DeliveryState | "duplicate" }> = []
    const seen = new Set<string>()

    // Earlier deliveries of this eventId (subscriptions are deleted once sent).
    const priorPrefix = `evt:${input.eventId}|`
    const prior = await this.state.storage.list<DeliveryRecord>({ prefix: priorPrefix })
    for (const [key, record] of prior) {
      const subscriptionId = key.slice(priorPrefix.length)
      seen.add(subscriptionId)
      results.push({ subscriptionId, state: record.state === "sent" ? "duplicate" : record.state })
    }

    const matches = await this.state.storage.list<string>({
      prefix: turnPrefix(input.agent, input.threadId, input.turnId),
    })
    for (const subscriptionId of matches.values()) {
      if (seen.has(subscriptionId)) continue
      const sub = await this.state.storage.get<Subscription>(`sub:${subscriptionId}`)
      if (!sub || sub.expiresAt <= nowSec) continue
      const pending: PendingDelivery = {
        eventId: input.eventId,
        subscriptionId,
        type: input.type,
        reason: input.reason,
        occurredAt: input.occurredAt,
        attempts: 0,
        nextAt: 0,
      }
      results.push({ subscriptionId, state: await this.deliver(sub, pending) })
    }

    return jsonResponse({ eventId: input.eventId, matched: results.length, results }, 202)
  }

  private async deliver(sub: Subscription, pending: PendingDelivery): Promise<DeliveryState> {
    const key = `${pending.eventId}|${sub.subscriptionId}`
    const attempt = pending.attempts + 1
    const startedAt = Date.now()
    const lease: PendingDelivery = { ...pending, attempts: attempt, nextAt: startedAt + SEND_LEASE_MS }
    await this.state.storage.put({
      [`evt:${key}`]: deliveryRecord("retrying", attempt, null, startedAt),
      [`retry:${key}`]: lease,
    })
    await this.ensureAlarm(lease.nextAt)

    const outcome = await sendTurnAlert(this.env, sub, {
      hostId: sub.hostId,
      threadId: sub.threadId,
      turnId: sub.turnId,
      eventId: pending.eventId,
      kind: pending.type,
    })
    const now = Date.now()
    let state = outcome.state
    if (state === "retrying" && attempt >= MAX_DELIVERY_ATTEMPTS) state = "failed"
    console.log(
      `deliver ${pending.eventId} ${sub.subscriptionId} ${sub.platform} attempt=${attempt} -> ${state}` +
        ` (status=${outcome.providerStatus ?? "none"} reason=${outcome.reason ?? "none"})`
    )

    if (state === "retrying") {
      const backoff = Math.min(RETRY_MAX_MS, RETRY_BASE_MS * 2 ** (attempt - 1))
      const hinted = outcome.retryAfterSeconds === null ? 0 : Math.min(RETRY_AFTER_MAX_MS, outcome.retryAfterSeconds * 1000)
      const nextAt = now + Math.max(backoff, hinted)
      await this.state.storage.put({
        [`evt:${key}`]: deliveryRecord("retrying", attempt, outcome.providerStatus, now),
        [`retry:${key}`]: { ...lease, nextAt },
      })
      await this.ensureAlarm(nextAt)
      return state
    }

    await this.state.storage.put(`evt:${key}`, deliveryRecord(state, attempt, outcome.providerStatus, now))
    await this.state.storage.delete(`retry:${key}`)
    if (state === "invalid_token") {
      await this.removeSubscriptionsWithToken(sub.platform, sub.pushToken)
    } else {
      // Sent (or permanently failed): the turn's single terminal event is done.
      await this.removeSubscription(sub, "failed")
    }
    return state
  }

  private async processDueRetries(): Promise<void> {
    const now = Date.now()
    const retries = await this.state.storage.list<PendingDelivery>({ prefix: "retry:" })
    for (const [key, pending] of retries) {
      if (pending.nextAt > now) continue
      try {
        const sub = await this.state.storage.get<Subscription>(`sub:${pending.subscriptionId}`)
        if (!sub || sub.expiresAt * 1000 <= now) {
          await this.state.storage.put(
            `evt:${pending.eventId}|${pending.subscriptionId}`,
            deliveryRecord("failed", pending.attempts, null, now)
          )
          await this.state.storage.delete(key)
          continue
        }
        await this.deliver(sub, pending)
      } catch (err) {
        // The retry record (or its lease) stays, so the next alarm retries it.
        console.error(`retry ${key} error: ${err instanceof Error ? err.message : String(err)}`)
      }
    }
  }

  // --- housekeeping ------------------------------------------------------------

  // Drops expired records and re-arms the alarm for the next expiry or retry.
  private async cleanup(): Promise<void> {
    const now = Date.now()
    const entries = await this.state.storage.list<unknown>()
    const expired: string[] = []
    const liveSubs = new Set<string>()
    let next: number | null = null
    const consider = (at: number) => {
      if (next === null || at < next) next = at
    }

    for (const [key, value] of entries) {
      if (!key.startsWith("sub:")) continue
      const sub = value as Subscription
      if (sub.expiresAt * 1000 <= now) {
        expired.push(key)
      } else {
        liveSubs.add(sub.subscriptionId)
        consider(sub.expiresAt * 1000)
      }
    }
    for (const [key, value] of entries) {
      if (key.startsWith("sub:")) continue
      if (key.startsWith("idx:")) {
        if (!liveSubs.has(value as string)) expired.push(key)
      } else if (key.startsWith("retry:")) {
        consider((value as PendingDelivery).nextAt)
      } else if (key.startsWith("rl:")) {
        const stamps = (value as number[]).filter((t) => now - t < RATE_WINDOW_MS)
        if (stamps.length === 0) expired.push(key)
        else consider(stamps[stamps.length - 1] + RATE_WINDOW_MS)
      } else {
        const exp = (value as { exp?: unknown } | null)?.exp
        if (typeof exp !== "number") continue
        if (exp <= now) expired.push(key)
        else consider(exp)
      }
    }

    await this.deleteKeys(expired)
    if (next !== null) await this.ensureAlarm(next)
  }

  private async rememberNonce(nonce: string): Promise<void> {
    const exp = Date.now() + HOST_NONCE_TTL_MS
    await this.state.storage.put(`nonce:${nonce}`, { exp })
    await this.ensureAlarm(exp)
  }

  // Sliding one-minute window per bucket; returns Retry-After seconds when full.
  private async consumeRate(bucket: RateBucket): Promise<number | null> {
    const key = `rl:${bucket}`
    const now = Date.now()
    const stamps = ((await this.state.storage.get<number[]>(key)) ?? []).filter((t) => now - t < RATE_WINDOW_MS)
    if (stamps.length >= RATE_LIMITS[bucket]) {
      return Math.max(1, Math.ceil((stamps[0] + RATE_WINDOW_MS - now) / 1000))
    }
    stamps.push(now)
    await this.state.storage.put(key, stamps)
    await this.ensureAlarm(now + RATE_WINDOW_MS)
    return null
  }

  private async ensureAlarm(at: number): Promise<void> {
    const current = await this.state.storage.getAlarm()
    if (current === null || at < current) await this.state.storage.setAlarm(at)
  }

  private async deleteKeys(keys: string[]): Promise<void> {
    for (let i = 0; i < keys.length; i += STORAGE_BATCH) {
      await this.state.storage.delete(keys.slice(i, i + STORAGE_BATCH))
    }
  }

  private async putAll(entries: Record<string, unknown>): Promise<void> {
    const keys = Object.keys(entries)
    for (let i = 0; i < keys.length; i += STORAGE_BATCH) {
      const batch: Record<string, unknown> = {}
      for (const key of keys.slice(i, i + STORAGE_BATCH)) batch[key] = entries[key]
      await this.state.storage.put(batch)
    }
  }
}
