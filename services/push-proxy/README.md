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
                                   │  Ed25519 signature, ±300 s timestamp,
                                   │  BLOCKED_HOST_IDS, 16 KB limit, validation
                                   ▼
                                 HostChannel DO, one per host (idFromName(hostId))
                                   │  nonce replay cache, per-host rate limits,
                                   │  grant checks, subscriptions, event dedupe,
                                   │  retries + expiry cleanup (alarm)
                                   ▼
                                 src/alerts.ts ──▶ APNs alert / FCM notification
phone ──device-signed revoke──▶ /v2/subscriptions/revoke (per-IP rate limit)
operator ──admin token──▶ /debug/push (disabled by default, one send per call)
old apps ──▶ /register, /:id/deregister → PushRegistration DO (legacy keepalive)
```

The Worker never runs agent work, never polls hosts and never receives
conversation content: events carry only ids and a terminal type.

| File | Role |
|---|---|
| `src/index.ts` | Router; legacy `/register` + `/:id/deregister`; exports the DO classes |
| `src/v2.ts` | v2 routes, host/device signature checks, input validation |
| `src/host-channel.ts` | `HostChannel` Durable Object (spec §7.2) |
| `src/alerts.ts` | APNs/FCM turn alerts and the single-shot debug sender |
| `src/debug.ts` | `POST /debug/push` |
| `src/signing.ts` | Canonical strings, SHA-256/hex helpers, Ed25519 verify |
| `src/http.ts`, `src/validation.ts` | Error/JSON helpers, bounded body reads, field validators |
| `src/apns.ts`, `src/fcm.ts` | Provider credentials (APNs JWT, FCM OAuth) and legacy silent push |
| `src/durable-object.ts` | Legacy `PushRegistration` DO |
| `src/rate-limiter.ts` | Per-key sliding-window `RateLimiter` DO |

## API

All v2 bodies are JSON, at most 16 KB. Errors are always
`{"error":"<code>"}` (some carry an extra human-readable `message`):

| code | HTTP |
|---|---|
| `bad_request` | 400 |
| `unauthorized` | 401 (missing/invalid signature, stale timestamp, replayed nonce, wrong admin token) |
| `forbidden` | 403 (blocked host, grant rejected) |
| `not_found` | 404 |
| `payload_too_large` | 413 |
| `rate_limited` | 429, with `Retry-After` (seconds) |
| `internal` | 500 |

### Host-signed requests

`POST /v2/subscriptions`, `DELETE /v2/subscriptions/{id}` and `POST /v2/events`
carry:

```
X-AgentBuddy-Host: <hostId, 64 lowercase hex>
X-AgentBuddy-Timestamp: <unix seconds>
X-AgentBuddy-Nonce: <32 lowercase hex>
X-AgentBuddy-Signature: <128 lowercase hex>
```

#### `POST /v2/subscriptions`

```json
{
  "deviceId": "8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394",
  "platform": "ios",
  "pushToken": "a1a1…a1",
  "apnsEnvironment": "production",
  "agent": "codex",
  "threadId": "thread-1",
  "turnId": "turn-1",
  "issuedAt": 1790300000,
  "expiresAt": 1790386400,
  "grantNonce": "ffeeddccbbaa99887766554433221100",
  "grantSignature": "c0bb8f66…200e"
}
```

- `201 {"subscriptionId":"sub_<32 hex>","expiresAt":1790386400}` — created.
- `200` with the same shape — the unique key `(hostId, deviceId, agent, threadId, turnId)`
  already exists. A retry with the same grant returns it unchanged; a newer
  valid grant (e.g. rotated push token) keeps the id and adopts the new target
  and expiry.
- Field rules: `platform` `ios|android`; iOS `pushToken` hex ≤ 200 and
  `apnsEnvironment` `sandbox|production` required; Android token printable
  ASCII ≤ 4096, `apnsEnvironment` absent/`null`/`"none"`; `agent`
  `[A-Za-z0-9._-]{1,64}`; `threadId`/`turnId` 1–128 UTF-8 bytes without control
  characters.
- `403 forbidden` when the grant fails any rule in [Signing](#signing).

#### `DELETE /v2/subscriptions/{subscriptionId}`

Empty body (its SHA-256 is that of the empty string). `200 {"ok":true}`,
idempotent. Only the calling host's own subscriptions can be deleted; another
host's id is simply not found in this host's object and nothing happens.

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

### `POST /v2/subscriptions/revoke` (device-signed, no host signature)

```json
{ "hostId": "8a88…6f5c", "deviceId": "8139…b394", "scope": "all",
  "timestamp": 1790300000, "nonce": "0f0e0d0c0b0a09080706050403020100",
  "signature": "8a989a6c…bf03" }
```

`200 {"revoked":3}` — deletes all of that device's subscriptions on that host
and records the revocation point (never moved backwards). Rate limited to 10
requests/minute per client IP. Allowed even for blocked hosts.

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
  401 for a wrong token (no provider call). Global limit 20 requests/minute.
- Logs contain only the request id, platform, mode, provider status and the
  first 8 hex of `sha256(pushToken)`.

### Legacy keepalive

- `POST /register` → `{"id":"<do id>"}`; `POST /:id/deregister` → `{"ok":true}`.
- Controlled by `LEGACY_KEEPALIVE_ENABLED` (default `"true"`). When `"false"`:
  `/register` returns `410 {"error":"legacy_push_retired"}`, `/deregister` still
  returns 200, and each existing `PushRegistration` deletes its storage on its
  next alarm and stops re-arming.

## Signing

Ed25519 (RFC 8032). Keys are iroh node ids (32-byte public key as 64 lowercase
hex); signatures are 64 bytes as 128 lowercase hex. The Worker verifies with
WebCrypto `importKey("raw", pub, {name: "Ed25519"})`, supported by workerd at
the existing `compatibility_date = "2024-12-01"` (checked against the local
workerd), so no compatibility-date bump was needed. Strings are UTF-8, lines
joined with `\n`, no trailing newline.

**Host request** (signed by the host key):

```
agentbuddy-push-host-v1
<METHOD>
<path, no query>
<X-AgentBuddy-Timestamp>
<X-AgentBuddy-Nonce>
<sha256(body) lowercase hex>
```

Rejected when the timestamp is more than 300 s off; nonces are remembered in
the host's object for 10 minutes.

**Device grant** (signed by the device key, `deviceId` is the public key):

```
agentbuddy-push-grant-v1
host=<hostId>
device=<deviceId>
platform=<ios|android>
environment=<sandbox|production|none>
token_sha256=<sha256(pushToken) lowercase hex>
agent=<agent>
thread=<threadId>
turn=<turnId>
issued=<unix seconds>
expires=<unix seconds>
nonce=<32 hex>
```

The Worker builds this string with `host` = the authenticated signing host, so a
grant only works for the host it was issued to, and with the hash of the
submitted token. It then requires: valid signature; `issued` not before the
device's revocation point on this host; `0 < expires − issued ≤ 48 h`; not
expired; `issued` at most 300 s in the future; `grantNonce` never used before
(kept until the grant expires). `environment` is `none` for Android.

**Device revoke**:

```
agentbuddy-push-revoke-v1
host=<hostId>
device=<deviceId>
scope=all
timestamp=<unix seconds>
nonce=<32 hex>
```

Same 300 s window; nonce remembered 10 minutes.

`tests/signing.cjs` checks the spec §12 vectors: canonical strings, hashes,
signatures (Node's deterministic Ed25519 from the spec seeds) and end-to-end
acceptance of the exact vector requests.

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
| FCM | `UNREGISTERED`, `NOT_FOUND`/404, token-related `INVALID_ARGUMENT` | `invalid_token` |
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
- The alarm also removes expired subscriptions, grant nonces, host nonces,
  revocation records (kept 49 h), delivery records and rate-limit windows, and
  stops re-arming once the object is empty. There are no periodic pushes.

## Rate limits

| Scope | Limit |
|---|---|
| Subscription registrations, per host | 60 / minute |
| Events, per host | 120 / minute |
| Subscription deletes, per host | 120 / minute |
| Device revoke, per client IP | 10 / minute |
| Debug push, global | 20 / minute |
| Legacy `/register`, per client IP | 10 / minute |

All return `429 {"error":"rate_limited"}` with `Retry-After`
(legacy `/register` keeps its old `{"error":"rate limited"}` body).

## Secrets and vars

| Name | Kind | Purpose |
|---|---|---|
| `APNS_TEAM_ID`, `APNS_KEY_ID`, `APNS_PRIVATE_KEY` | secret | APNs JWT |
| `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY` | secret | FCM OAuth |
| `DEBUG_PUSH_ADMIN_TOKEN` | secret, optional | debug endpoint admin token |
| `APNS_TOPIC` | var | default `com.akashark.agentbuddy` |
| `DEBUG_PUSH_ENABLED` | var | default `"false"` |
| `LEGACY_KEEPALIVE_ENABLED` | var | default `"true"` |
| `BLOCKED_HOST_IDS` | var | comma-separated blocked host ids, default empty |

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

1. Make sure the secrets above exist (`npx wrangler secret list`).
2. `npx wrangler deploy`. The first deploy with this version applies migration
   `v3` (`new_sqlite_classes = ["HostChannel"]`) and adds the `HOST_CHANNEL`
   binding. Nothing changes for existing users: the legacy path stays on and the
   debug endpoint stays off.
3. Check `GET /v2/health`.

### Debug token

- Enable: `openssl rand -hex 32`, then `npx wrangler secret put DEBUG_PUSH_ADMIN_TOKEN`
  (paste the value), set `DEBUG_PUSH_ENABLED = "true"` in `wrangler.toml` and
  deploy. Keep the token in a private file on the operator's machine only.
- Disable: set `DEBUG_PUSH_ENABLED = "false"` (or remove it) and deploy, or
  `npx wrangler secret delete DEBUG_PUSH_ADMIN_TOKEN`. Either one makes the
  endpoint return 404.
- Rotate: generate a new value with `openssl rand -hex 32`,
  `npx wrangler secret put DEBUG_PUSH_ADMIN_TOKEN`, then update the operator's
  private file. The old token stops working immediately.

### Migration order (spec §9)

1. Worker first: deploy v2 + debug endpoint (off) + `HostChannel` (migration `v3`).
   Legacy path stays on.
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
