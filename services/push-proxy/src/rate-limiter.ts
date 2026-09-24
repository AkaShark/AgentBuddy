const MAX_REQUESTS = 10
const WINDOW_MS = 60_000
const TIMESTAMPS_KEY = "timestamps"

// Sliding-window limiter keyed per client IP. The window lives in DO storage
// (not instance memory) so an evicted/restarted instance keeps counting.
export class RateLimiter implements DurableObject {
  private state: DurableObjectState

  constructor(state: DurableObjectState) {
    this.state = state
  }

  async fetch(_request: Request): Promise<Response> {
    const now = Date.now()
    const stored = (await this.state.storage.get<number[]>(TIMESTAMPS_KEY)) ?? []
    const timestamps = stored.filter((t) => now - t < WINDOW_MS)
    if (timestamps.length >= MAX_REQUESTS) {
      return new Response("rate limited", { status: 429 })
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
