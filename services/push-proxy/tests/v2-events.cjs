// Event dispatch: exact APNs/FCM requests (spec §7.3/§7.4), dedupe, retries,
// invalid-token cleanup and expiry cleanup (§7.2).
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const h = require("./support/harness");

const worker = h.load("index").default;
const { MAX_DELIVERY_ATTEMPTS } = h.load("host-channel");
const { HOST_A, DEVICE_A, DEVICE_B } = h;

beforeEach(() => {
  h.clock.set(h.VECTOR_TIME * 1000);
  h.fetchMock.reset(() => new Response(null, { status: 200 }));
});

async function subscribe(env, fields = {}, device = DEVICE_A) {
  const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", h.makeGrant(device, HOST_A.id, fields)), env);
  assert.ok(response.status === 201 || response.status === 200);
  return (await response.json()).subscriptionId;
}

async function sendEvent(env, fields = {}) {
  const event = h.makeEvent(fields);
  const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event), env);
  assert.equal(response.status, 202);
  return { event, body: await response.json() };
}

const collapseKey = (threadId = "thread-1", turnId = "turn-1") => `t-${h.sha256(`${HOST_A.id}|${threadId}|${turnId}`).slice(0, 32)}`;

function apnsError(status, reason, headers = {}) {
  return () => new Response(JSON.stringify({ reason }), { status, headers });
}

test("completed on iOS sends the exact APNs alert request", async () => {
  const { env, channel } = h.makeEnv();
  const subscriptionId = await subscribe(env);
  const logs = h.captureLogs();
  let result;
  try {
    result = await sendEvent(env);
  } finally {
    logs.restore();
  }
  assert.deepEqual(result.body, { eventId: result.event.eventId, matched: 1, results: [{ subscriptionId, state: "sent" }] });

  assert.equal(h.fetchMock.calls.length, 1);
  const call = h.fetchMock.calls[0];
  assert.equal(call.url, `https://api.push.apple.com/3/device/${h.IOS_TOKEN}`);
  assert.equal(call.method, "POST");
  const { authorization, ...headers } = call.headers;
  assert.match(authorization, /^bearer [\w-]+\.[\w-]+\.[\w-]+$/);
  assert.deepEqual(headers, {
    "apns-push-type": "alert",
    "apns-priority": "10",
    "apns-expiration": String(h.clock.sec + 86400),
    "apns-collapse-id": collapseKey(),
    "apns-topic": "com.akashark.agentbuddy",
  });
  assert.equal(collapseKey().length, 34);
  assert.deepEqual(call.body, {
    aps: {
      alert: { title: "任务已完成", body: "点击查看结果" },
      sound: "default",
      "thread-id": "thread-1",
      category: "agentbuddy.task.complete",
    },
    "agentbuddy.notification.serverId": `alleycat:${HOST_A.id}`,
    "agentbuddy.notification.threadId": "thread-1",
    "agentbuddy.notification.turnId": "turn-1",
    "agentbuddy.notification.kind": "completed",
    "agentbuddy.notification.eventId": result.event.eventId,
  });
  assert.deepEqual(Object.keys(call.body), [
    "aps",
    "agentbuddy.notification.serverId",
    "agentbuddy.notification.threadId",
    "agentbuddy.notification.turnId",
    "agentbuddy.notification.kind",
    "agentbuddy.notification.eventId",
  ]);

  // Sent: the subscription is deleted, the delivery record kept for dedupe.
  const ctx = channel(HOST_A.id);
  assert.deepEqual(ctx.keys("sub:"), []);
  assert.deepEqual(ctx.keys("idx:"), []);
  assert.deepEqual(ctx.keys("retry:"), []);
  assert.equal(ctx.map.get(`evt:${result.event.eventId}|${subscriptionId}`).state, "sent");
  assert.ok(logs.lines.every((line) => !line.includes(h.IOS_TOKEN)), "logs never contain the push token");
});

test("failed on iOS sandbox uses the sandbox host, failure text and APNS_TOPIC", async () => {
  const { env } = h.makeEnv({ APNS_TOPIC: "com.example.custom" });
  await subscribe(env, { apnsEnvironment: "sandbox" });
  const { event } = await sendEvent(env, { type: "failed", reason: "interrupted" });
  const call = h.fetchMock.calls[0];
  assert.equal(call.url, `https://api.sandbox.push.apple.com/3/device/${h.IOS_TOKEN}`);
  assert.equal(call.headers["apns-topic"], "com.example.custom");
  assert.deepEqual(call.body.aps.alert, { title: "任务未完成", body: "任务失败或已中断，点击查看详情" });
  assert.equal(call.body["agentbuddy.notification.kind"], "failed");
  assert.equal(call.body["agentbuddy.notification.eventId"], event.eventId);
});

test("completed and failed on Android send the exact FCM message", async () => {
  const { env } = h.makeEnv();
  await subscribe(env, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: undefined });
  await subscribe(env, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: undefined, turnId: "turn-2" });
  const completed = await sendEvent(env);
  const failed = await sendEvent(env, { turnId: "turn-2", type: "failed", reason: "error" });
  assert.equal(completed.body.results[0].state, "sent");
  assert.equal(failed.body.results[0].state, "sent");

  const sends = h.fetchMock.providerCalls();
  assert.equal(sends.length, 2);
  assert.equal(h.fetchMock.calls.filter((c) => c.url === "https://oauth2.googleapis.com/token").length, 1, "OAuth token cached");
  const [first, second] = sends;
  assert.equal(first.url, "https://fcm.googleapis.com/v1/projects/agentbuddy-test/messages:send");
  assert.match(first.headers.authorization, /^Bearer oauth-/);
  assert.equal(first.headers["content-type"], "application/json");
  assert.deepEqual(first.body, {
    message: {
      token: h.ANDROID_TOKEN,
      data: {
        "agentbuddy.notification.serverId": `alleycat:${HOST_A.id}`,
        "agentbuddy.notification.threadId": "thread-1",
        "agentbuddy.notification.turnId": "turn-1",
        "agentbuddy.notification.kind": "completed",
        "agentbuddy.notification.eventId": completed.event.eventId,
      },
      android: {
        priority: "HIGH",
        ttl: "86400s",
        collapse_key: collapseKey(),
        notification: { channel_id: "turn_complete", tag: collapseKey(), title: "任务已完成", body: "点击查看结果" },
      },
    },
  });
  assert.deepEqual(second.body.message.android.notification, {
    channel_id: "turn_complete", tag: collapseKey("thread-1", "turn-2"), title: "任务未完成", body: "任务失败或已中断，点击查看详情",
  });
  assert.equal(second.body.message.data["agentbuddy.notification.kind"], "failed");
});

test("FCM data keys are identical to the APNs custom payload keys", async () => {
  const { env } = h.makeEnv();
  await subscribe(env);
  await subscribe(env, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: undefined }, DEVICE_B);
  const { body } = await sendEvent(env);
  assert.equal(body.matched, 2);
  const apns = h.fetchMock.providerCalls().find((c) => c.url.includes("push.apple.com")).body;
  const fcm = h.fetchMock.providerCalls().find((c) => c.url.includes("fcm.googleapis.com")).body.message.data;
  const { aps, ...custom } = apns;
  assert.deepEqual(fcm, custom);
});

test("a duplicate event report is not re-sent", async () => {
  const { env } = h.makeEnv();
  const subscriptionId = await subscribe(env);
  const event = h.makeEvent();
  const first = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event), env);
  assert.deepEqual((await first.json()).results, [{ subscriptionId, state: "sent" }]);
  const second = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event), env);
  assert.equal(second.status, 202);
  assert.deepEqual(await second.json(), { eventId: event.eventId, matched: 1, results: [{ subscriptionId, state: "duplicate" }] });
  assert.equal(h.fetchMock.providerCalls().length, 1);
});

test("an in-flight delivery is not sent twice by a concurrent duplicate report", async () => {
  const { env } = h.makeEnv();
  const subscriptionId = await subscribe(env);
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  h.fetchMock.reset(async () => { await gate; return new Response(null, { status: 200 }); });
  const event = h.makeEvent();
  const firstPromise = worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event), env);
  await new Promise((resolve) => setTimeout(resolve, 20));
  const second = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event), env);
  assert.deepEqual((await second.json()).results, [{ subscriptionId, state: "retrying" }]);
  release();
  assert.deepEqual((await (await firstPromise).json()).results, [{ subscriptionId, state: "sent" }]);
  assert.equal(h.fetchMock.providerCalls().length, 1);
});

test("a retryable failure is retried by the alarm honoring Retry-After, then succeeds", async () => {
  const { env, channel } = h.makeEnv();
  const subscriptionId = await subscribe(env);
  h.fetchMock.reset(apnsError(503, "ServiceUnavailable", { "retry-after": "120" }));
  const { event, body } = await sendEvent(env);
  assert.deepEqual(body.results, [{ subscriptionId, state: "retrying" }]);

  const ctx = channel(HOST_A.id);
  const key = `${event.eventId}|${subscriptionId}`;
  const pending = ctx.map.get(`retry:${key}`);
  assert.equal(pending.attempts, 1);
  assert.equal(pending.nextAt, h.clock.now + 120_000, "Retry-After beats the 30s base backoff");
  assert.equal(ctx.map.get(`evt:${key}`).state, "retrying");
  assert.ok(ctx.alarm !== null && ctx.alarm <= pending.nextAt);
  assert.ok(ctx.map.has(`sub:${subscriptionId}`), "subscription kept while retrying");

  // A duplicate report while retrying reports retrying and does not send.
  const dup = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event), env);
  assert.deepEqual((await dup.json()).results, [{ subscriptionId, state: "retrying" }]);
  assert.equal(h.fetchMock.providerCalls().length, 1);

  // Alarm before the retry is due: nothing is sent.
  h.clock.advance(60_000);
  await h.runAlarm(ctx);
  assert.equal(h.fetchMock.providerCalls().length, 1);
  assert.equal(ctx.alarm, pending.nextAt);

  h.fetchMock.reset(() => new Response(null, { status: 200 }));
  h.clock.set(pending.nextAt);
  await h.runAlarm(ctx);
  assert.equal(h.fetchMock.providerCalls().length, 1);
  assert.equal(h.fetchMock.calls[0].body["agentbuddy.notification.eventId"], event.eventId);
  assert.equal(ctx.map.get(`evt:${key}`).state, "sent");
  assert.equal(ctx.map.get(`evt:${key}`).attempts, 2);
  assert.deepEqual(ctx.keys("retry:"), []);
  assert.deepEqual(ctx.keys("sub:"), []);

  const after = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event), env);
  assert.deepEqual((await after.json()).results, [{ subscriptionId, state: "duplicate" }]);
});

test("retries back off exponentially and give up after the maximum attempts", async () => {
  const { env, channel } = h.makeEnv();
  const subscriptionId = await subscribe(env);
  const ctx = channel(HOST_A.id);
  let status = 500;
  h.fetchMock.reset(() => new Response(JSON.stringify({ reason: "InternalServerError" }), { status }));
  const { event } = await sendEvent(env);
  const key = `retry:${event.eventId}|${subscriptionId}`;
  const delays = [];
  for (let attempt = 1; attempt < MAX_DELIVERY_ATTEMPTS; attempt++) {
    const pending = ctx.map.get(key);
    assert.equal(pending.attempts, attempt);
    delays.push(pending.nextAt - h.clock.now);
    h.clock.set(pending.nextAt);
    await h.runAlarm(ctx);
  }
  assert.deepEqual(delays, [30_000, 60_000, 120_000, 240_000, 480_000, 960_000, 1_800_000]);
  assert.equal(h.fetchMock.providerCalls().length, MAX_DELIVERY_ATTEMPTS);
  assert.equal(ctx.map.has(key), false);
  const record = ctx.map.get(`evt:${event.eventId}|${subscriptionId}`);
  assert.equal(record.state, "failed");
  assert.equal(record.attempts, MAX_DELIVERY_ATTEMPTS);
  assert.deepEqual(ctx.keys("sub:"), []);
});

test("network errors and 429 are retrying", async () => {
  const { env } = h.makeEnv();
  await subscribe(env);
  await subscribe(env, { turnId: "turn-2" });
  h.fetchMock.reset(() => { throw new TypeError("fetch failed"); });
  assert.equal((await sendEvent(env)).body.results[0].state, "retrying");
  h.fetchMock.reset(apnsError(429, "TooManyRequests"));
  assert.equal((await sendEvent(env, { turnId: "turn-2" })).body.results[0].state, "retrying");
});

test("APNs 403 ExpiredProviderToken refreshes the JWT and retries once", async () => {
  const { env } = h.makeEnv();
  await subscribe(env);
  h.fetchMock.reset((_call, n) => (n === 1 ? apnsError(403, "ExpiredProviderToken")() : new Response(null, { status: 200 })));
  const { body } = await sendEvent(env);
  assert.equal(body.results[0].state, "sent");
  const [first, second] = h.fetchMock.providerCalls();
  assert.ok(first && second);
  assert.notEqual(first.headers.authorization, second.headers.authorization, "a new provider token was minted");

  // Persistent InvalidProviderToken: one retry only, then failed.
  await subscribe(env, { turnId: "turn-2" });
  h.fetchMock.reset(apnsError(403, "InvalidProviderToken"));
  const again = await sendEvent(env, { turnId: "turn-2" });
  assert.equal(again.body.results[0].state, "failed");
  assert.equal(h.fetchMock.providerCalls().length, 2);
});

test("APNs invalid token responses delete every subscription using that token", async () => {
  for (const respond of [apnsError(410, "Unregistered"), apnsError(400, "BadDeviceToken"), apnsError(400, "DeviceTokenNotForTopic"), apnsError(400, "Unregistered")]) {
    const { env, channel } = h.makeEnv();
    const sub1 = await subscribe(env);
    const sub2 = await subscribe(env, { turnId: "turn-2" });
    const keep = await subscribe(env, { pushToken: h.IOS_TOKEN_2, turnId: "turn-3" }, DEVICE_B);
    h.fetchMock.reset(respond);
    const { event, body } = await sendEvent(env);
    assert.deepEqual(body.results, [{ subscriptionId: sub1, state: "invalid_token" }]);
    const ctx = channel(HOST_A.id);
    assert.deepEqual(ctx.keys("sub:"), [`sub:${keep}`]);
    assert.equal(ctx.map.has(`sub:${sub2}`), false);
    assert.equal(ctx.map.get(`evt:${event.eventId}|${sub1}`).state, "invalid_token");
    assert.equal(ctx.keys("idx:").length, 1);
  }
});

test("other APNs 4xx responses are permanent failures", async () => {
  const { env, channel } = h.makeEnv();
  await subscribe(env);
  h.fetchMock.reset(apnsError(400, "TopicDisallowed"));
  const { body } = await sendEvent(env);
  assert.equal(body.results[0].state, "failed");
  assert.equal(h.fetchMock.providerCalls().length, 1);
  assert.deepEqual(channel(HOST_A.id).keys("retry:"), []);
});

test("FCM error classification: token errors, 401 refresh, retryable and permanent", async () => {
  const android = { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: undefined };
  const fcmError = (status, error, headers = {}) => () => new Response(JSON.stringify({ error }), { status, headers });
  const cases = [
    ["unregistered", fcmError(404, { status: "NOT_FOUND", details: [{ "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "UNREGISTERED" }] }), "invalid_token"],
    ["not found", fcmError(404, { status: "NOT_FOUND", message: "Requested entity was not found." }), "invalid_token"],
    ["invalid token argument", fcmError(400, { status: "INVALID_ARGUMENT", message: "The registration token is not a valid FCM registration token", details: [{ errorCode: "INVALID_ARGUMENT" }] }), "invalid_token"],
    ["token field violation", fcmError(400, { status: "INVALID_ARGUMENT", details: [{ "@type": "type.googleapis.com/google.rpc.BadRequest", fieldViolations: [{ field: "message.token" }] }] }), "invalid_token"],
    ["other invalid argument", fcmError(400, { status: "INVALID_ARGUMENT", message: "Invalid value at 'message.android.ttl'", details: [{ fieldViolations: [{ field: "message.android.ttl" }] }] }), "failed"],
    ["quota", fcmError(429, { status: "RESOURCE_EXHAUSTED" }, { "retry-after": "300" }), "retrying"],
    ["unavailable", fcmError(503, { status: "UNAVAILABLE" }), "retrying"],
    ["sender mismatch", fcmError(403, { status: "PERMISSION_DENIED", details: [{ errorCode: "SENDER_ID_MISMATCH" }] }), "failed"],
  ];
  for (const [name, respond, expected] of cases) {
    const { env, channel } = h.makeEnv();
    const subscriptionId = await subscribe(env, android);
    h.fetchMock.reset(respond);
    const { event, body } = await sendEvent(env);
    assert.equal(body.results[0].state, expected, name);
    const ctx = channel(HOST_A.id);
    if (expected === "invalid_token" || expected === "failed") assert.deepEqual(ctx.keys("sub:"), [], name);
    if (name === "quota") {
      assert.equal(ctx.map.get(`retry:${event.eventId}|${subscriptionId}`).nextAt, h.clock.now + 300_000, "Retry-After honored");
    }
  }

  // 401: clear the cached OAuth token, fetch a new one and retry once.
  const { env } = h.makeEnv();
  await subscribe(env, android);
  let sends = 0;
  h.fetchMock.reset((call) => (++sends === 1 ? new Response("{}", { status: 401 }) : new Response("{}", { status: 200 })));
  const { body } = await sendEvent(env);
  assert.equal(body.results[0].state, "sent");
  const sendCalls = h.fetchMock.providerCalls();
  assert.equal(sendCalls.length, 2);
  assert.notEqual(sendCalls[0].headers.authorization, sendCalls[1].headers.authorization);
  assert.equal(h.fetchMock.calls.filter((c) => c.url === "https://oauth2.googleapis.com/token").length, 1, "one fresh token after the 401 (the first came from the cache)");
});

test("events only match this host's subscriptions for the same agent/thread/turn", async () => {
  const { env } = h.makeEnv();
  const target = await subscribe(env);
  await subscribe(env, { turnId: "turn-2" });
  await subscribe(env, { agent: "claude" });
  await subscribe(env, { threadId: "thread-1|turn-1" }, DEVICE_B);
  const { body } = await sendEvent(env);
  assert.deepEqual(body.results, [{ subscriptionId: target, state: "sent" }]);
  assert.equal(h.fetchMock.providerCalls().length, 1);
});

test("expired subscriptions are not notified and are cleaned up with their records", async () => {
  const { env, channel } = h.makeEnv();
  const now = h.clock.sec;
  const shortLived = h.makeGrant(DEVICE_A, HOST_A.id, { expiresAt: now + 600 });
  await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", shortLived), env);
  const longLived = await subscribe(env, { turnId: "turn-2", expiresAt: now + 7200 });
  const ctx = channel(HOST_A.id);

  h.clock.advance(601_000);
  const { body } = await sendEvent(env);
  assert.equal(body.matched, 0);
  assert.equal(h.fetchMock.providerCalls().length, 0);

  await h.runAlarm(ctx);
  assert.deepEqual(ctx.keys("sub:"), [`sub:${longLived}`]);
  assert.equal(ctx.keys("idx:").length, 1);
  assert.equal(ctx.map.has(`grant:${shortLived.grantNonce}`), false);
  assert.equal(ctx.keys("nonce:").length, 1, "only the recent event request nonce remains");
  assert.ok(ctx.alarm !== null);

  // Everything expires eventually and the alarm stops re-arming.
  h.clock.advance(3 * 86400_000);
  await h.runAlarm(ctx);
  assert.equal(ctx.map.size, 0, [...ctx.map.keys()].join(","));
  assert.equal(ctx.alarm, null);
});

test("delivery records expire after 48 hours; pending retries for vanished subscriptions fail", async () => {
  const { env, channel } = h.makeEnv();
  const subscriptionId = await subscribe(env);
  h.fetchMock.reset(apnsError(500, "InternalServerError"));
  const { event } = await sendEvent(env);
  const ctx = channel(HOST_A.id);
  const del = await worker.fetch(h.hostRequest(HOST_A, "DELETE", `/v2/subscriptions/${subscriptionId}`), env);
  assert.equal(del.status, 200);
  assert.deepEqual(ctx.keys("retry:"), [], "unsubscribe drops pending retries");
  assert.equal(ctx.map.get(`evt:${event.eventId}|${subscriptionId}`).state, "failed");

  h.clock.advance(48 * 3600_000 + 1);
  await h.runAlarm(ctx);
  assert.equal(ctx.map.has(`evt:${event.eventId}|${subscriptionId}`), false);
});

test("a crash between the provider call and the result write is recovered by the alarm", async () => {
  const { env, channel } = h.makeEnv();
  const subscriptionId = await subscribe(env);
  const ctx = channel(HOST_A.id);
  // Fail the first storage write after the provider call, like an evicted object.
  h.fetchMock.reset(() => {
    const put = ctx.storage.put;
    ctx.storage.put = async () => { ctx.storage.put = put; throw new Error("storage unavailable"); };
    return new Response(null, { status: 503 });
  });
  const event = h.makeEvent();
  const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event), env);
  assert.equal(response.status, 500);
  assert.equal((await response.json()).error, "internal");
  const lease = ctx.map.get(`retry:${event.eventId}|${subscriptionId}`);
  assert.equal(lease.attempts, 1);
  assert.ok(ctx.alarm !== null && ctx.alarm <= lease.nextAt);

  h.fetchMock.reset(() => new Response(null, { status: 200 }));
  h.clock.set(lease.nextAt);
  await h.runAlarm(ctx);
  assert.equal(ctx.map.get(`evt:${event.eventId}|${subscriptionId}`).state, "sent");
  assert.equal(h.fetchMock.providerCalls().length, 1);
});
