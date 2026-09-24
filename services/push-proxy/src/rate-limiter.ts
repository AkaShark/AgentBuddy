const MAX_REQUESTS = 10
const WINDOW_MS = 60_000
const TIMESTAMPS_KEY = "timestamps"

// Sliding-window limiter keyed per client IP. The window lives in DO storage
// (not instance memory) so an evicted/restarted instance keeps counting.
// `?limit=N` on the check URL overrides the default of 10 requests per minute;
// a 429 carries Retry-After (seconds until the oldest request leaves the window).
export class RateLimiter implements DurableObject {
  private state: DurableObjectState

  constructor(state: DurableObjectState) {
    this.state = state
  }

  async fetch(request: Request): Promise<Response> {
    const requested = Number(new URL(request.url).searchParams.get("limit"))
    const maxRequests = Number.isSafeInteger(requested) && requested > 0 ? requested : MAX_REQUESTS
    const now = Date.now()
    const stored = (await this.state.storage.get<number[]>(TIMESTAMPS_KEY)) ?? []
    const timestamps = stored.filter((t) => now - t < WINDOW_MS)
    if (timestamps.length >= maxRequests) {
      const retryAfter = Math.max(1, Math.ceil((timestamps[0] + WINDOW_MS - now) / 1000))
      return new Response("rate limited", { status: 429, headers: { "retry-after": String(retryAfter) } })
    }
    timestamps.push(now)
    await this.state.storage.put(TIMESTAMPS_KEY, timestamps)
    // Clean up once the window has passed so idle IPs don't keep storage forever.
    await this.state.storage.setAlarm(now + WINDOW_MS)
    return new Response("ok")
  }

  async alarm(): Promise<void> {
    const now = Date.now()
    const stored = (await this.state.storage.get<number[]>(TIMESTAMPS_KEY)) ?? []
    const timestamps = stored.filter((t) => now - t < WINDOW_MS)
    if (timestamps.length === 0) {
      await this.state.storage.deleteAll()
      return
    }
    await this.state.storage.put(TIMESTAMPS_KEY, timestamps)
    await this.state.storage.setAlarm(now + WINDOW_MS)
  }
}

// Returns null when allowed, otherwise the Retry-After seconds.
export async function checkRateLimit(
  namespace: DurableObjectNamespace,
  name: string,
  limit: number
): Promise<number | null> {
  const limiter = namespace.get(namespace.idFromName(name))
  const resp = await limiter.fetch(new Request(`https://rl/check?limit=${limit}`))
  if (resp.status !== 429) return null
  const retryAfter = Number(resp.headers.get("retry-after"))
  return Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : 60
}
