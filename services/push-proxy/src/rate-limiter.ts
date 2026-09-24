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

const IPV4_RE = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/

// Rate-limit bucket for a client address (spec §13): an IPv4 address as is,
// an IPv6 address by its /64 prefix (one subscriber usually owns a whole /64),
// IPv4-mapped IPv6 as the IPv4 address. Anything unparsable shares "invalid".
export function ipBucket(ip: string): string {
  const value = ip.trim().toLowerCase()
  if (value === "") return "unknown"
  if (value.length > 64) return "invalid"
  const v4 = IPV4_RE.exec(value)
  if (v4) return v4.slice(1).some((part) => Number(part) > 255) ? "invalid" : v4.slice(1).map(Number).join(".")
  if (!value.includes(":")) return "invalid"

  let address = value.split("%")[0]
  const tail = /(\d{1,3}(?:\.\d{1,3}){3})$/.exec(address)
  if (tail) {
    const octets = tail[1].split(".").map(Number)
    if (octets.some((octet) => octet > 255)) return "invalid"
    address = address.slice(0, -tail[1].length) +
      `${((octets[0] << 8) | octets[1]).toString(16)}:${((octets[2] << 8) | octets[3]).toString(16)}`
  }
  const halves = address.split("::")
  if (halves.length > 2) return "invalid"
  const head = halves[0] ? halves[0].split(":") : []
  const rest = halves.length === 2 && halves[1] ? halves[1].split(":") : []
  const missing = 8 - head.length - rest.length
  if (halves.length === 1 ? missing !== 0 : missing < 1) return "invalid"
  const groups = [...head, ...new Array<string>(halves.length === 2 ? missing : 0).fill("0"), ...rest]
  if (groups.some((group) => !/^[0-9a-f]{1,4}$/.test(group))) return "invalid"
  const words = groups.map((group) => parseInt(group, 16))
  if (words.slice(0, 5).every((word) => word === 0) && words[5] === 0xffff) {
    return [words[6] >> 8, words[6] & 0xff, words[7] >> 8, words[7] & 0xff].join(".")
  }
  return `${words.slice(0, 4).map((word) => word.toString(16)).join(":")}::/64`
}

export function clientIpBucket(request: Request): string {
  return ipBucket(request.headers.get("cf-connecting-ip") ?? "")
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
