# agentbuddy-push-proxy

Cloudflare Worker that turns **host-reported turn completions** into visible
APNs / FCM notifications, plus the legacy 30-second silent keepalive path that
it replaces. The design contract shared with the host (alleycat) and the mobile
apps is `docs/superpowers/specs/2026-09-24-host-push-notifications-design.md`;
canonical strings, header names, paths, JSON fields and payload keys below are
copied from it and must not drift.

## Architecture

```
host (alleycat) ──signed HTTP──▶ Worker entry (src/index.ts → src/v2.ts)
                                   │  per-IP limit (IPv6 /64) before any body read,
                                   │  Ed25519 v2 signature with aud, ±300 s,
                                   │  BLOCKED_HOST_IDS, 16 KB limit, validation,
                                   │  device grant + sealed target (src/sealed-target.ts)
                                   ▼
                                 HostChannel DO, one per host (idFromName(hostId))
                                   │  nonce replay cache, per-host rate limits,
                                   │  revocation points, subscriptions, event dedupe,
                                   │  retries + paged expiry cleanup (alarm)
                                   ▼
                                 src/alerts.ts ──▶ APNs alert / FCM notification
phone ──device-signed revoke──▶ /v2/subscriptions/revoke (per-IP + per-host limits)
operator ──admin token──▶ /debug/push (disabled by default, per-IP limit before auth)
old apps ──▶ /register, /:id/deregister → PushRegistration DO (legacy keepalive)
```

The push token never passes through the host in clear: the phone seals it to
the Worker's X25519 key ([Sealed target](#sealed-target)), the host stores and
forwards the opaque string, and only the Worker opens it.

The Worker never runs agent work, never polls hosts and never receives
conversation content: events carry only ids and a terminal type.

| File | Role |
|---|---|
| `src/index.ts` | Router; legacy `/register` + `/:id/deregister`; exports the DO classes |
| `src/v2.ts` | v2 routes, host/device signature checks, grant checks, input validation |
| `src/sealed-target.ts` | `PUSH_TARGET_SEAL_KEY` parsing and sealed-target opening (X25519 + HKDF + AES-GCM) |
| `src/host-channel.ts` | `HostChannel` Durable Object (spec §7.2) |
| `src/alerts.ts` | APNs/FCM turn alerts and the single-shot debug sender |
| `src/debug.ts` | `POST /debug/push` |
| `src/signing.ts` | Canonical strings, SHA-256/hex helpers, Ed25519 verify |
| `src/http.ts`, `src/validation.ts` | Error/JSON helpers, bounded body reads, field validators |
| `src/apns.ts`, `src/fcm.ts` | Provider credentials (APNs JWT, FCM OAuth) and legacy silent push |
| `src/durable-object.ts` | Legacy `PushRegistration` DO |
| `src/rate-limiter.ts` | Per-key sliding-window `RateLimiter` DO; client IP buckets (IPv6 /64) |

## API

All v2 bodies are JSON, at most 16 KB. Errors are always
`{"error":"<code>"}` (some carry an extra human-readable `message`):

| code | HTTP |
|---|---|
| `bad_request` | 400 |
| `unauthorized` | 401 (missing/invalid signature, wrong `aud`, stale timestamp, replayed nonce, wrong admin token) |
| `forbidden` | 403 (blocked host, grant rejected, sealed target rejected) |
| `not_found` | 404 |
| `payload_too_large` | 413 |
| `rate_limited` | 429, with `Retry-After` (seconds) |
| `internal` | 500 (also: `PUSH_TARGET_SEAL_KEY` missing or malformed) |

### Host-signed requests

`POST /v2/subscriptions`, `DELETE /v2/subscriptions/{id}` and `POST /v2/events`
carry:

```
X-AgentBuddy-Host: <hostId, 64 lowercase hex>
X-AgentBuddy-Timestamp: <unix seconds>
X-AgentBuddy-Nonce: <32 lowercase hex>
X-AgentBuddy-Signature: <128 lowercase hex>
```

Order of checks: header shape and timestamp window, then the per-IP limit
(before the body is read or the signature verified), then the bounded body
read, signature, `BLOCKED_HOST_IDS`, JSON validation.

#### `POST /v2/subscriptions`

```json
{
  "deviceId": "8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394",
  "platform": "ios",
  "apnsEnvironment": "production",
  "sealedTarget": "AQGsAbIgnoY1T7hT…Ta4EtrUQcvhO-SD6OJtfJpKJt7AB1O9_W…SG3g",
  "agent": "codex",
  "threadId": "thread-1",
  "turnId": "turn-1",
  "issuedAt": 1790300000,
  "expiresAt": 1790386400,
  "grantNonce": "ffeeddccbbaa99887766554433221100",
  "grantSignature": "40206a5b…bf05"
}
```

- `201 {"subscriptionId":"sub_<32 hex>","expiresAt":1790386400}` — created.
- `200` with the same shape — the unique key `(hostId, deviceId, agent, threadId, turnId)`
  already exists. A retry with the same grant returns it unchanged; a newer
  valid grant (e.g. rotated push token) keeps the id and adopts the new target
  and expiry.
- Field rules: `platform` `ios|android`; iOS `apnsEnvironment`
  `sandbox|production` required, Android absent/`null`/`"none"`; `sealedTarget`
  a string of at most 8192 characters; `agent` `[A-Za-z0-9._-]{1,64}`;
  `threadId`/`turnId` 1–128 UTF-8 bytes, well-formed (no lone UTF-16
  surrogates), no control characters. There is no clear-text `pushToken`.
- Token rules apply to the token inside the sealed target: iOS hex ≤ 200,
  Android printable ASCII ≤ 4096.
- `403 forbidden` when the grant fails any rule in [Signing](#signing) or the
  sealed target fails any rule in [Sealed target](#sealed-target). Sealed-target
  failures are always the bare `{"error":"forbidden"}`: the response never says
  which check failed.
- `500 internal` when `PUSH_TARGET_SEAL_KEY` is missing or malformed (the host
  outbox retries 5xx, so registrations resume once the secret is fixed).

#### `DELETE /v2/subscriptions/{subscriptionId}`

Empty body (its SHA-256 is that of the empty string). `200 {"ok":true}`,
idempotent. Only the calling host's own subscriptions can be deleted; another
host's id is simply not found in this host's object and nothing happens. An
unknown id is answered without writing anything (no nonce, no rate window).

#### `POST /v2/events`

```json
{ "eventId": "evt_0123456789abcdef0123456789abcdef", "agent": "codex",
  "threadId": "thread-1", "turnId": "turn-1",
  "type": "completed", "reason": null, "occurredAt": 1790300123 }
```

`type` is `completed|failed`; `reason` is `null`, `"interrupted"` or `"error"`.

```json
202 {"eventId":"evt_…","matched":2,
     "results":[{"subscriptionId":"sub_…","state":"sent"},
                {"subscriptionId":"sub_…","state":"retrying"}]}
```

| state | meaning |
|---|---|
| `sent` | provider accepted the push |
| `retrying` | the Worker retries on its own (alarm) |
| `duplicate` | this `eventId` was already sent to that subscription |
| `invalid_token` | provider rejected the token; every subscription with that token was deleted |
| `failed` | permanent failure |

Only this host's subscriptions with the same `agent`/`threadId`/`turnId` match.
Earlier deliveries of the same `eventId` are listed with their recorded state.
When nothing matches and the `eventId` has no earlier delivery (for example a
host with no subscriptions at all), the Worker still verifies the signature and
then answers `202 {"eventId":"evt_…","matched":0,"results":[]}` without
writing any state — no nonce, no rate-limit window, no alarm.

### `POST /v2/subscriptions/revoke` (device-signed, no host signature)

```json
{ "hostId": "8a88…6f5c", "deviceId": "8139…b394", "scope": "all",
  "timestamp": 1790300000, "nonce": "0f0e0d0c0b0a09080706050403020100",
  "signature": "8ebac8fa…4f0e" }
```

`200 {"revoked":3}` — deletes all of that device's subscriptions on that host
and records the revocation point (never moved backwards); grants issued at or
before it (same second included) are refused afterwards. Allowed even for
blocked hosts. Limits (all `429 rate_limited`):

- 10 requests/minute per client IP bucket (IPv4 address, IPv6 /64), checked
  before the body is read;
- 30 requests/minute per target `hostId`;
- at most 1000 live revocation records per host for devices that have no
  subscription there and no earlier record (anyone can mint a device key);
  past that a revoke for such a device gets `429` with `Retry-After: 3600`.

Revoke nonces are stored as `rnonce:<nonce>`, separate from host request nonces
(`nonce:<nonce>`), so a revoke can never pre-claim a host's nonce.

### `GET /v2/health`

`200 {"ok":true,"features":["subscriptions.v2","events.v2"]}`

### `POST /debug/push`

Disabled (`404 {"error":"not_found"}`, no provider call) unless the var
`DEBUG_PUSH_ENABLED` is exactly `"true"` **and** the secret
`DEBUG_PUSH_ADMIN_TOKEN` has at least 32 characters.

```
Authorization: Bearer <DEBUG_PUSH_ADMIN_TOKEN>
```

```json
{ "platform": "ios", "pushToken": "<hex>", "apnsEnvironment": "sandbox",
  "mode": "alert", "title": "测试", "body": "点击测试路由",
  "hostId": "<64 hex>", "threadId": "thread-1", "turnId": "turn-1" }
```

- `platform` `ios|android`; `pushToken` iOS hex ≤ 200, Android ≤ 4096;
  `apnsEnvironment` required for iOS only; `mode` `alert|background`; `title`
  ≤ 64 characters (optional); `body` ≤ 200 characters (required for `alert`);
  optional `hostId`/`threadId`/`turnId` fill the same
  `agentbuddy.notification.serverId|threadId|turnId` routing keys as real
  alerts. Any other field (URL, topic, credentials, raw payload) is rejected.
- `background`: iOS `content-available` push (`apns-push-type: background`,
  priority 5); Android data-only high-priority message with
  `type: "debug_background"`.
- Response (always 200 once the send was attempted):
  `{"requestId":"dbg_…","provider":"apns|fcm","accepted":true,"providerStatus":200,"error":null}`.
  `accepted` means the provider accepted it, not that the phone displayed it.
- Exactly one provider send per request; no retries, registrations or alarms.
  401 for a wrong token (no provider call). 30 requests/minute per client IP
  bucket, checked **before** the admin token; then a global limit of 20
  requests/minute that only authorized requests spend.
- Logs contain only the request id, platform, mode, provider status and the
  first 8 hex of `sha256(pushToken)`.

### Legacy keepalive

- `POST /register` → `{"id":"<do id>"}`; `POST /:id/deregister` → `{"ok":true}`.
- Controlled by `LEGACY_KEEPALIVE_ENABLED` (default `"true"`). When `"false"`:
  `/register` returns `410 {"error":"legacy_push_retired"}`, `/deregister` still
  returns 200, and each existing `PushRegistration` deletes its storage on its
  next alarm and stops re-arming.

## Signing

Protocol v2 (spec §5, §12). Ed25519 (RFC 8032). Keys are iroh node ids (32-byte
public key as 64 lowercase hex); signatures are 64 bytes as 128 lowercase hex.
The Worker verifies with WebCrypto `importKey("raw", pub, {name: "Ed25519"})`,
supported by workerd at the existing `compatibility_date = "2024-12-01"`.
Strings are UTF-8, lines joined with `\n`, no trailing newline.

`aud` is the Worker origin (`scheme://host[:port]`, no trailing slash), e.g.
`https://agentbuddy-push-proxy.aaksharker.workers.dev`. Signers use the origin
they actually call; the Worker compares with `new URL(request.url).origin`, so a
signature captured at one deployment cannot be replayed at another. When the
public URL differs from what the Worker sees (a proxy or route in front of it),
set the var `PUSH_AUDIENCE` to the public origin.

**Host request** (signed by the host key):

```
agentbuddy-push-host-v2
<aud>
<METHOD>
<path, no query>
<X-AgentBuddy-Timestamp>
<X-AgentBuddy-Nonce>
<sha256(body) lowercase hex>
```

Rejected when the timestamp is more than 300 s off; nonces are remembered in
the host's object for 10 minutes. Every retry needs a fresh timestamp, nonce
and signature.

**Device grant** (signed by the device key, `deviceId` is the public key):

```
agentbuddy-push-grant-v2
aud=<aud>
host=<hostId>
device=<deviceId>
platform=<ios|android>
environment=<sandbox|production|none>
target_sha256=<sha256(sealedTarget ASCII string) lowercase hex>
agent=<agent>
thread=<threadId>
turn=<turnId>
issued=<unix seconds>
expires=<unix seconds>
nonce=<32 hex>
```

The Worker builds this string with `aud` = its own origin, `host` = the
authenticated signing host and the hash of the submitted `sealedTarget`, so a
grant only works for the Worker and host it was issued to and for exactly that
sealed target. It then requires: `0 < expires − issued ≤ 48 h`; not expired;
`issued` at most 300 s in the future; valid signature; the sealed target opens
and matches (below); `issued` strictly after the device's revocation point on
this host; `grantNonce` never used before (kept until the grant expires).
`environment` is `none` for Android.

**Device revoke**:

```
agentbuddy-push-revoke-v2
aud=<aud>
host=<hostId>
device=<deviceId>
scope=all
timestamp=<unix seconds>
nonce=<32 hex>
```

Same 300 s window; nonce remembered 10 minutes in its own namespace.

`tests/signing.cjs` checks the spec §12 v2 vectors: canonical strings, hashes,
signatures (Node's deterministic Ed25519 from the spec seeds), the sealed-target
vector (decrypted by the Worker code with the test key) and end-to-end
acceptance of the exact vector requests at `https://push.example.test`.

## Sealed target

Spec §5.5. The Worker holds a static X25519 private key (secret
`PUSH_TARGET_SEAL_KEY`) with a one-byte key id; the matching public key is built
into the mobile shared Rust client, where the host cannot replace it.

```
sealedTarget = base64url-no-padding( 0x01 ‖ kid ‖ epk(32) ‖ nonce(12) ‖ ciphertext ‖ tag(16) )

shared    = X25519(esk, workerPk)                  (phone; Worker: X25519(workerSk, epk))
key       = HKDF-SHA256(ikm = shared, salt = epk ‖ workerPk,
                        info = "agentbuddy-push-target-v1", L = 32)
AAD       = "<hostId>|<deviceId>"                  (UTF-8)
cipher    = AES-256-GCM, random 12-byte nonce, 16-byte tag
plaintext = {"v":1,"platform":"ios","token":"<push token>","apnsEnvironment":"production",
             "deviceId":"<deviceId>","hostId":"<hostId>"}      (Android: "apnsEnvironment":null)
```

The Worker accepts a target only if: it is strict unpadded base64url (alphabet,
length and canonical trailing bits); the version byte is `0x01`; `kid` is one of
the configured keys; X25519 succeeds (low-order points are rejected); the GCM tag
verifies with AAD `<authenticated hostId>|<deviceId>`; the plaintext is UTF-8
JSON with `v == 1`; its `hostId`, `deviceId`, `platform` and `apnsEnvironment`
equal the request's; its `token` passes the platform token rules. Any failure is
the same bare `403 {"error":"forbidden"}`. The decrypted token is stored in the
subscription and is never logged; the sealed string itself is not stored.

All of it is plain WebCrypto — `importKey("pkcs8"|"raw", …, {name: "X25519"})`
+ `deriveBits`, HKDF, AES-GCM — available in workerd at the existing
`compatibility_date = "2024-12-01"` (checked by running the bundled Worker in
the local workerd through Miniflare against the §12 vector). The Worker derives
each key's public half as `X25519(sk, 9)`, so the private key stays
non-extractable.

Because the AAD binds host and device, a host that got hold of a sealed target
cannot register it under a self-minted device key or for another host, and it
cannot swap the target without invalidating the device's grant signature.

## Security rules (spec §13)

- **Ids**: `agent` matches `[A-Za-z0-9._-]{1,64}`; `threadId`/`turnId` are
  non-empty, ≤ 128 UTF-8 bytes, without control characters or lone UTF-16
  surrogates — checked before any canonical string is built.
- **Nonces**: hosts and phones generate them with a CSPRNG.
- **Tokens are secrets**: the host never sees a clear token; the Worker never
  logs one (debug logs only the first 8 hex of `sha256(token)`).
- **Rate limits**: host-signed routes per IP bucket before verification; revoke
  per IP bucket and per target host; debug per IP bucket before auth; revocation
  records for never-seen devices capped per host object; cleanup pages through
  storage (`list({limit: 1000})`).
- **No subscriptions, no state**: events that match nothing are answered with
  `matched: 0` without writing anything.
- **FCM token invalidation** only for `UNREGISTERED`, or `INVALID_ARGUMENT`
  whose `google.rpc.BadRequest` details name the field `message.token`; a bare
  404 is a permanent failure, not proof the token is dead.
- **Blocking a host** (`BLOCKED_HOST_IDS`) also stops its queued retries.
- **HTTP clients** that call the Worker directly (phone revoke, debug script)
  do not follow redirects and require HTTPS (loopback excepted).

## Notifications

APNs (`api.push.apple.com` or `api.sandbox.push.apple.com` per subscription):

```
apns-push-type: alert
apns-priority: 10
apns-expiration: <now + 86400>
apns-collapse-id: t-<first 32 hex of sha256(hostId|threadId|turnId)>
apns-topic: <APNS_TOPIC, default com.akashark.agentbuddy>
```

```json
{
  "aps": { "alert": { "title": "任务已完成", "body": "点击查看结果" },
           "sound": "default", "thread-id": "<threadId>", "category": "agentbuddy.task.complete" },
  "agentbuddy.notification.serverId": "alleycat:<hostId>",
  "agentbuddy.notification.threadId": "<threadId>",
  "agentbuddy.notification.turnId": "<turnId>",
  "agentbuddy.notification.kind": "completed",
  "agentbuddy.notification.eventId": "evt_…"
}
```

Failed turns use 「任务未完成」/「任务失败或已中断，点击查看详情」 and `kind: "failed"`.

FCM (`messages:send`):

```json
{ "message": {
    "token": "<token>",
    "data": { "agentbuddy.notification.serverId": "alleycat:<hostId>", "…": "same keys as APNs" },
    "android": { "priority": "HIGH", "ttl": "86400s", "collapse_key": "t-…",
                 "notification": { "channel_id": "turn_complete", "tag": "t-…",
                                   "title": "任务已完成", "body": "点击查看结果" } } } }
```

Provider responses:

| Provider | Response | State |
|---|---|---|
| APNs | 200 | `sent` |
| APNs | 410; 400 `BadDeviceToken` / `DeviceTokenNotForTopic` / `Unregistered` | `invalid_token` |
| APNs | 403 `ExpiredProviderToken` / `InvalidProviderToken` | JWT cache cleared, resent once, then classified |
| APNs | 429, 5xx, network error, credential error | `retrying` |
| FCM | 2xx | `sent` |
| FCM | `UNREGISTERED`; `INVALID_ARGUMENT` with a `message.token` field violation | `invalid_token` |
| FCM | 401 | OAuth cache cleared, resent once, then classified |
| FCM | 429, 5xx, network error, OAuth failure | `retrying` (honors `Retry-After`) |
| both | anything else | `failed` |

## Delivery semantics

At-least-once to the providers, with dedupe — **not exactly-once**:

- Each `eventId + subscriptionId` has a delivery record (`evt:` key, kept 48 h).
  Once it is `sent`, later reports of the same event return `duplicate`
  without sending.
- Before each provider call the object writes the record as `retrying` plus a
  retry entry due in 60 s (a lease). A concurrent duplicate report sees it and
  does not send. If the object dies after the provider accepted but before the
  result is stored, the alarm resends once the lease expires — the device then
  merges the duplicate through `apns-collapse-id` / the Android notification tag.
- Retries run from the alarm: 30 s doubling to a 30 min cap, or the provider's
  `Retry-After` when larger (capped at 1 h), at most 8 attempts, then `failed`.
- After `sent` or `failed` the subscription is deleted (a turn has one terminal
  event). `invalid_token` deletes every subscription in that host's object that
  uses the token. Unsubscribe/revoke drops pending retries.
- When a host is in `BLOCKED_HOST_IDS`, its due retries are not sent: each is
  finalized as `failed` and the subscription removed.
- The alarm also removes expired subscriptions, grant nonces, host and revoke
  nonces, revocation records (kept 49 h), delivery records and rate-limit
  windows, and stops re-arming once the object is empty. It pages through
  storage 1000 keys at a time. There are no periodic pushes.

## Rate limits

| Scope | Limit |
|---|---|
| Host-signed routes, per client IP bucket (before body read / verification) | 300 / minute |
| Subscription registrations, per host | 60 / minute |
| Events that match a subscription or earlier delivery, per host | 120 / minute |
| Subscription deletes of existing subscriptions, per host | 120 / minute |
| Device revoke, per client IP bucket | 10 / minute |
| Device revoke, per target host | 30 / minute |
| Revocation records for never-seen devices, per host | 1000 live records |
| Debug push, per client IP bucket (before auth) | 30 / minute |
| Debug push, global (authorized requests) | 20 / minute |
| Legacy `/register`, per client IP | 10 / minute |

A client IP bucket is the IPv4 address, or the /64 prefix of an IPv6 address
(IPv4-mapped IPv6 counts as IPv4), from `CF-Connecting-IP`. All return
`429 {"error":"rate_limited"}` with `Retry-After` (legacy `/register` keeps its
old `{"error":"rate limited"}` body).

The per-IP limits use the existing `RateLimiter` Durable Object (one object per
bucket name, sliding one-minute window in storage). Cloudflare's Rate Limiting
binding was not used: it is per-location and eventually consistent, and its
availability/pricing on this account's plan could not be confirmed without
talking to Cloudflare. The cost is one extra Durable Object request per
host-signed request.

## Secrets and vars

| Name | Kind | Purpose |
|---|---|---|
| `APNS_TEAM_ID`, `APNS_KEY_ID`, `APNS_PRIVATE_KEY` | secret | APNs JWT |
| `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY` | secret | FCM OAuth |
| `PUSH_TARGET_SEAL_KEY` | secret, **required** for `POST /v2/subscriptions` | X25519 private key(s) that open sealed targets: `<kid>:<64 hex>`, comma-separated during rotation |
| `DEBUG_PUSH_ADMIN_TOKEN` | secret, optional | debug endpoint admin token |
| `APNS_TOPIC` | var | default `com.akashark.agentbuddy` |
| `DEBUG_PUSH_ENABLED` | var | default `"false"`; never commit `"true"` |
| `LEGACY_KEEPALIVE_ENABLED` | var | default `"true"` |
| `BLOCKED_HOST_IDS` | var | comma-separated blocked host ids, default empty |
| `PUSH_AUDIENCE` | var, optional | signed `aud` override (default: request origin) |

Vars live in `wrangler.toml` `[vars]`; secrets are set with
`npx wrangler secret put <NAME>` and are never committed.

## Development

```sh
npm ci
npx tsc --noEmit   # typecheck
npm test           # node --test tests/*.cjs (compiles src/ with tsc into a temp dir)
```

Tests use fake Durable Object storage, a controllable clock and a mocked
`fetch`; nothing talks to Cloudflare, Apple or Google.

## Deploy

1. Make sure the secrets above exist (`npx wrangler secret list`), including
   `PUSH_TARGET_SEAL_KEY` (see below) — without it every
   `POST /v2/subscriptions` returns 500.
2. `npx wrangler deploy`. The first deploy with this version applies migration
   `v3` (`new_sqlite_classes = ["HostChannel"]`) and adds the `HOST_CHANNEL`
   binding. Nothing changes for existing users: the legacy path stays on and the
   debug endpoint stays off.
3. Check `GET /v2/health`.

### Sealed-target key (`PUSH_TARGET_SEAL_KEY`)

The secret value is `<kid>:<64 hex private key>`; `kid` is a decimal byte
(0–255) that the phone writes into every sealed target. The public key and kid
go into the mobile shared Rust client. Never use the spec §12 test key
(`03…03`) in production.

Generate a key into a private file (Node):

```sh
umask 077
node -e '
const c = require("crypto");
const { privateKey, publicKey } = c.generateKeyPairSync("x25519");
const sk = privateKey.export({ format: "der", type: "pkcs8" }).subarray(16).toString("hex");
const pk = publicKey.export({ format: "der", type: "spki" }).subarray(12).toString("hex");
process.stdout.write("1:" + sk);
console.error("kid 1 public key (for the mobile client): " + pk);
' > ~/.agentbuddy/push-target-seal-key.private
```

or with OpenSSL 1.1.1+ / 3.x (macOS's `/usr/bin/openssl` is LibreSSL without
X25519 `genpkey`; use e.g. `$(brew --prefix openssl@3)/bin/openssl`):

```sh
umask 077
openssl genpkey -algorithm X25519 -out seal.pem
printf '1:%s' "$(openssl pkey -in seal.pem -outform DER | tail -c 32 | xxd -p -c 64)" \
  > ~/.agentbuddy/push-target-seal-key.private
openssl pkey -in seal.pem -pubout -outform DER | tail -c 32 | xxd -p -c 64   # public key
rm seal.pem
```

Install it without the value ever appearing on a command line or in shell
history:

```sh
npx wrangler secret put PUSH_TARGET_SEAL_KEY < ~/.agentbuddy/push-target-seal-key.private
```

Rotation: generate a new key with the next kid (e.g. `2`), set the secret to
both keys, new first — `2:<new hex>,1:<old hex>` — and ship app builds with the
new public key and kid. Once no client or host outbox can still hold a kid-1
target (host outbox items live at most 24 h; allow for app adoption), set the
secret to `2:<new hex>` alone. A missing or malformed secret makes
subscriptions fail with 500 and logs `PUSH_TARGET_SEAL_KEY is missing or
invalid` (never the value).

### Debug token

`DEBUG_PUSH_ENABLED` stays `"false"` in `wrangler.toml`; never commit `"true"`.

- Enable: `openssl rand -hex 32` into a private file, then
  `npx wrangler secret put DEBUG_PUSH_ADMIN_TOKEN < <that file>`, then turn the
  endpoint on for one deployment with either
  - `npx wrangler deploy --var DEBUG_PUSH_ENABLED:true`, or
  - the Cloudflare dashboard: Worker → Settings → Variables and Secrets → set
    `DEBUG_PUSH_ENABLED` to `true` → Deploy.

  Either way, the next plain `npx wrangler deploy` resets it to the committed
  `"false"`.
- Disable: `npx wrangler deploy` (restores `"false"`), set it back to `false` in
  the dashboard, or `npx wrangler secret delete DEBUG_PUSH_ADMIN_TOKEN`. Any one
  makes the endpoint return 404.
- Rotate: generate a new value with `openssl rand -hex 32`,
  `npx wrangler secret put DEBUG_PUSH_ADMIN_TOKEN`, then update the operator's
  private file. The old token stops working immediately.

### Migration order (spec §9)

1. Worker first: set `PUSH_TARGET_SEAL_KEY`, deploy v2 + debug endpoint (off) +
   `HostChannel` (migration `v3`). Legacy path stays on.
2. Hosts: ship the alleycat build that advertises `push.v1`.
3. Apps: release the app versions that use v2 only.
4. After verification, retire the legacy path: set
   `LEGACY_KEEPALIVE_ENABLED = "false"` and deploy. Old registrations clean
   themselves up on their next alarm (legacy TTL is at most 6 hours).
5. Once no legacy registrations remain, add migration `v4` with
   `deleted_classes = ["PushRegistration"]` (and drop the binding and class).

### Rollback

- `npx wrangler rollback` to the previous Worker version, or
- set `LEGACY_KEEPALIVE_ENABLED = "true"` again and deploy.

Cloudflare may refuse a rollback to a version from before a Durable Object
migration (here `v3`). In that case, redeploy the older code with the
`HostChannel` class and binding kept in place rather than deleting them.

Old apps keep working until step 4. Nothing here enables paid plans, creates
releases or submits store reviews.
