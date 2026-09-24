// HostChannel: one Durable Object per host (idFromName(hostId)), spec §7.2.
// It owns that host's subscriptions, the host nonce replay cache, the separate
// revoke nonce cache, grant nonces, device revocation points, per-event
// delivery records and delivery retries. The Worker entry has already verified
// the host (or device) signature, the grant and the sealed target, and
// validated the payload before a request reaches this object.
import { ApnsEnvironment, DeliveryState, Platform, sendTurnAlert, TurnKind } from "./alerts"
import { errorResponse, jsonResponse } from "./http"
import { HEX32_RE, randomHex } from "./signing"
import { Env } from "./types"
import { blockedHostIds } from "./validation"

export const MAX_GRANT_LIFETIME_SECONDS = 48 * 3600
export const HOST_NONCE_TTL_MS = 10 * 60_000
export const EVENT_RECORD_TTL_MS = 48 * 3600_000
// Any grant issued before a revocation point has expired 48h later.
export const REVOCATION_TTL_MS = (MAX_GRANT_LIFETIME_SECONDS + 3600) * 1000
export const RATE_WINDOW_MS = 60_000
export const RATE_LIMITS = { subscriptions: 60, events: 120, deletes: 120, revokes: 30 } as const
// Revocation records for devices with no subscription here (anyone can mint a
// device key) are capped per host object; beyond this a revoke gets 429.
export const MAX_UNSEEN_REVOCATIONS = 1000
export const UNSEEN_REVOCATION_RETRY_AFTER_SECONDS = 3600
export const MAX_DELIVERY_ATTEMPTS = 8
export const RETRY_BASE_MS = 30_000
export const RETRY_MAX_MS = 30 * 60_000
export const RETRY_AFTER_MAX_MS = 60 * 60_000
// A send in progress is recorded as a due-later retry first, so a crash
// between the provider call and the result write is retried by the alarm.
export const SEND_LEASE_MS = 60_000
const STORAGE_BATCH = 128
// storage.list() is always paged so a large object never loads at once.
export const LIST_PAGE_SIZE = 1000

// An authorized grant with its target already opened (pushToken is plaintext).
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

      if (!HEX32_RE.test(nonce)) return errorResponse("unauthorized")
      // Device revokes use their own nonce namespace, so nobody can pre-claim
      // a host nonce through the unauthenticated-by-host revoke route.
      if (op === "revoke") return await this.revoke(nonce, (await request.json()) as RevokeInput)

      // Replay cache first: a nonce seen in the last 10 minutes is rejected
      // before any rate-limit or state change.
      if ((await this.state.storage.get(`nonce:${nonce}`)) !== undefined) return errorResponse("unauthorized")

      // Requests that cannot change anything are answered from reads alone:
      // no nonce, rate-limit window or alarm is written (spec §13), so any
      // self-minted host key costs this object nothing.
      let event: EventInput | undefined
      if (op === "event") {
        event = (await request.json()) as EventInput
        if (await this.eventIsNoop(event)) {
          return jsonResponse({ eventId: event.eventId, matched: 0, results: [] }, 202)
        }
      } else if (op === "unsubscribe" && (await this.state.storage.get(`sub:${parts[1]}`)) === undefined) {
        return jsonResponse({ ok: true })
      }

      const bucket: RateBucket = op === "subscribe" ? "subscriptions" : op === "event" ? "events" : "deletes"
      const retryAfter = await this.consumeRate(bucket)
      if (retryAfter !== null) return errorResponse("rate_limited", { retryAfterSeconds: retryAfter })
      await this.rememberNonce(`nonce:${nonce}`)

      switch (op) {
        case "subscribe":
          return await this.subscribe(hostId, (await request.json()) as SubscriptionInput)
        case "unsubscribe":
          return await this.unsubscribe(parts[1])
        case "event":
          return await this.event(event as EventInput)
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

    // §5.1 / §5.3: a grant issued at or before the device's revocation point
    // (same second included) can no longer register.
    const revocation = await this.state.storage.get<{ at: number }>(`revoked:${input.deviceId}`)
    if (revocation && input.issuedAt <= revocation.at) {
      return errorResponse("forbidden", { message: "grant issued before device revocation" })
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

  private async revoke(nonce: string, input: RevokeInput): Promise<Response> {
    if ((await this.state.storage.get(`rnonce:${nonce}`)) !== undefined) return errorResponse("unauthorized")
    const retryAfter = await this.consumeRate("revokes")
    if (retryAfter !== null) return errorResponse("rate_limited", { retryAfterSeconds: retryAfter })

    const key = `revoked:${input.deviceId}`
    const previous = await this.state.storage.get<{ at: number }>(key)
    const subs: Subscription[] = []
    for await (const [, sub] of this.scan<Subscription>("sub:")) {
      if (sub.deviceId === input.deviceId) subs.push(sub)
    }
    if (!previous && subs.length === 0 && (await this.countRevocations()) >= MAX_UNSEEN_REVOCATIONS) {
      return errorResponse("rate_limited", { retryAfterSeconds: UNSEEN_REVOCATION_RETRY_AFTER_SECONDS })
    }
    await this.rememberNonce(`rnonce:${nonce}`)

    for (const sub of subs) await this.removeSubscription(sub, "failed")
    const revoked = subs.length
    const at = Math.max(previous?.at ?? 0, input.timestamp)
    const record = { at, exp: at * 1000 + REVOCATION_TTL_MS }
    await this.state.storage.put(key, record)
    await this.ensureAlarm(record.exp)
    return jsonResponse({ revoked })
  }

  private async countRevocations(): Promise<number> {
    let count = 0
    for await (const _ of this.scan("revoked:")) {
      if (++count >= MAX_UNSEEN_REVOCATIONS) break
    }
    return count
  }

  // Deletes the subscription, its index entry and its pending retries; the
  // pending deliveries are finalized with `pendingState`.
  private async removeSubscription(sub: Subscription, pendingState: DeliveryState): Promise<void> {
    const keys = [`sub:${sub.subscriptionId}`]
    const idx = indexKey(sub)
    if ((await this.state.storage.get<string>(idx)) === sub.subscriptionId) keys.push(idx)
    const now = Date.now()
    const finalized: Record<string, DeliveryRecord> = {}
    for await (const [key, pending] of this.scan<PendingDelivery>("retry:")) {
      if (pending.subscriptionId !== sub.subscriptionId) continue
      keys.push(key)
      finalized[`evt:${pending.eventId}|${sub.subscriptionId}`] = deliveryRecord(pendingState, pending.attempts, null, now)
    }
    await this.deleteKeys(keys)
    await this.putAll(finalized)
  }

  private async removeSubscriptionsWithToken(platform: Platform, pushToken: string): Promise<void> {
    const matching: Subscription[] = []
    for await (const [, sub] of this.scan<Subscription>("sub:")) {
      if (sub.platform === platform && sub.pushToken === pushToken) matching.push(sub)
    }
    for (const sub of matching) await this.removeSubscription(sub, "invalid_token")
  }

  // --- events ------------------------------------------------------------------

  // Nothing to deliver and nothing to report: no subscription for this turn
  // and no earlier delivery of this eventId.
  private async eventIsNoop(input: EventInput): Promise<boolean> {
    const [matches, prior] = await Promise.all([
      this.state.storage.list({ prefix: turnPrefix(input.agent, input.threadId, input.turnId), limit: 1 }),
      this.state.storage.list({ prefix: `evt:${input.eventId}|`, limit: 1 }),
    ])
    return matches.size === 0 && prior.size === 0
  }

  private async event(input: EventInput): Promise<Response> {
    const nowSec = Math.floor(Date.now() / 1000)
    const results: Array<{ subscriptionId: string; state: DeliveryState | "duplicate" }> = []
    const seen = new Set<string>()

    // Earlier deliveries of this eventId (subscriptions are deleted once sent).
    const priorPrefix = `evt:${input.eventId}|`
    for await (const [key, record] of this.scan<DeliveryRecord>(priorPrefix)) {
      const subscriptionId = key.slice(priorPrefix.length)
      seen.add(subscriptionId)
      results.push({ subscriptionId, state: record.state === "sent" ? "duplicate" : record.state })
    }

    const matches: string[] = []
    for await (const [, subscriptionId] of this.scan<string>(turnPrefix(input.agent, input.threadId, input.turnId))) {
      matches.push(subscriptionId)
    }
    for (const subscriptionId of matches) {
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
    const due: Array<[string, PendingDelivery]> = []
    for await (const entry of this.scan<PendingDelivery>("retry:")) {
      if (entry[1].nextAt <= now) due.push(entry)
    }
    const blocked = blockedHostIds(this.env)
    for (const [key, pending] of due) {
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
        if (blocked.has(sub.hostId)) {
          // §5.4: blocking a host also stops its queued retries.
          console.log(`retry ${pending.eventId} ${sub.subscriptionId} stopped: host blocked`)
          await this.removeSubscription(sub, "failed")
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
  // One paged pass over the whole object; an index entry is dropped when its
  // subscription is gone or expired (looked up per page, since "idx:" sorts
  // before "sub:").
  private async cleanup(): Promise<void> {
    const now = Date.now()
    let next: number | null = null
    const consider = (at: number) => {
      if (next === null || at < next) next = at
    }

    for await (const page of this.pages()) {
      const expired: string[] = []
      const indexes: Array<[string, string]> = []
      for (const [key, value] of page) {
        if (key.startsWith("sub:")) {
          const sub = value as Subscription
          if (sub.expiresAt * 1000 <= now) expired.push(key)
          else consider(sub.expiresAt * 1000)
        } else if (key.startsWith("idx:")) {
          indexes.push([key, value as string])
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
      for (let i = 0; i < indexes.length; i += STORAGE_BATCH) {
        const batch = indexes.slice(i, i + STORAGE_BATCH)
        const subs = await this.state.storage.get<Subscription>(batch.map(([, id]) => `sub:${id}`))
        for (const [key, id] of batch) {
          const sub = subs.get(`sub:${id}`)
          if (!sub || sub.expiresAt * 1000 <= now) expired.push(key)
        }
      }
      await this.deleteKeys(expired)
    }

    if (next !== null) await this.ensureAlarm(next)
  }

  // Pages of at most LIST_PAGE_SIZE entries in key order. Keys of a page
  // already yielded may be deleted by the caller; paging resumes after the
  // page's last key either way.
  private async *pages<T = unknown>(prefix?: string): AsyncGenerator<Map<string, T>> {
    let startAfter: string | undefined
    for (;;) {
      const options: DurableObjectListOptions = { limit: LIST_PAGE_SIZE }
      if (prefix !== undefined) options.prefix = prefix
      if (startAfter !== undefined) options.startAfter = startAfter
      const page = await this.state.storage.list<T>(options)
      if (page.size === 0) return
      let last: string | undefined
      for (const key of page.keys()) last = key
      yield page
      if (page.size < LIST_PAGE_SIZE) return
      startAfter = last
    }
  }

  private async *scan<T = unknown>(prefix: string): AsyncGenerator<[string, T]> {
    for await (const page of this.pages<T>(prefix)) yield* page
  }

  // `key` is `nonce:<hex>` (host requests) or `rnonce:<hex>` (device revokes).
  private async rememberNonce(key: string): Promise<void> {
    const exp = Date.now() + HOST_NONCE_TTL_MS
    await this.state.storage.put(key, { exp })
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
