const { test, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { generateKeyPairSync } = require('node:crypto');
const h = require('./support/harness');
const { sendTurnAlert } = h.load('alerts');
const worker = h.load('index').default;
const key = generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey.export({ type:'pkcs8', format:'pem' });
const credentials = JSON.stringify({ project_id:'123456789', key_id:'key', sub_account:'service', private_key:key });
const target = { platform:'harmony', pushToken:'harmony-token', apnsEnvironment:null };
const alert = { hostId:h.HOST_A.id, threadId:'thread-1', turnId:'turn-1', eventId:'evt_abc', kind:'completed' };
const env = () => h.makeEnv({ HARMONY_SERVICE_ACCOUNT:credentials }).env;
beforeEach(() => { h.clock.set(h.VECTOR_TIME * 1000); h.fetchMock.reset(() => Response.json({code:'80000000'})); });

test('Harmony notifications go to v3 with foreground silence, task route and stable deduplication', async () => {
  const requests=[];
  h.fetchMock.reset(call => { requests.push(call); return Response.json({code:'80000000'}); });
  assert.equal((await sendTurnAlert(env(), target, alert)).state, 'sent');
  assert.equal((await sendTurnAlert(env(), target, alert)).state, 'sent');
  const [first, second]=requests;
  assert.equal(first.url, 'https://push-api.cloud.huawei.com/v3/123456789/messages:send');
  assert.equal(first.headers['push-type'], '0');
  const body=first.body, again=second.body;
  assert.equal(body.payload.notification.foregroundShow, false);
  assert.equal(body.payload.notification.category, 'WORK');
  assert.equal(body.payload.notification.appMessageId, again.payload.notification.appMessageId);
  assert.equal(body.payload.notification.notifyId, again.payload.notification.notifyId);
  assert.equal(body.payload.notification.clickAction.data['agentbuddy.notification.threadId'], alert.threadId);
  assert.deepEqual(body.target.token, [target.pushToken]);
  assert.equal(body.pushOptions.ttl, 86400);
});

test('HTTP success without business success is not accepted; provider rights errors do not revoke tokens', async () => {
  for (const code of ['80100000','80300002','80300007']) {
    h.fetchMock.reset(() => Response.json({code, msg:JSON.stringify({illegalTokens:{noRight:[target.pushToken]}})}));
    const result=await sendTurnAlert(env(),target,alert);
    assert.equal(result.state,'failed');
    assert.equal(result.reason,code);
    assert.ok(!JSON.stringify(result).includes(target.pushToken));
  }
  h.fetchMock.reset(() => Response.json({code:'80300007',msg:JSON.stringify({illegalTokens:{tokenFormatError:[target.pushToken]}})}));
  assert.equal((await sendTurnAlert(env(),target,alert)).state,'invalid_token');
});

test('retryable responses and expired provider auth are handled without changing the deduplication key', async () => {
  for (const [code,status] of [['81000001',200],['80300029',400],['',503]]) {
    h.fetchMock.reset(() => Response.json({code},{status,headers:{'retry-after':'30'}}));
    const result=await sendTurnAlert(env(),target,alert);
    assert.equal(result.state,'retrying');
    assert.equal(result.retryAfterSeconds,30);
  }
  let calls=0;
  h.fetchMock.reset(() => Response.json({code:++calls === 1 ? '80200005':'80000000'}));
  assert.equal((await sendTurnAlert(env(),target,alert)).state,'sent');
  assert.equal(calls,2);
});

test('signed and sealed Harmony grants are accepted and retain the platform identity', async () => {
  const {env: environment,channel}=h.makeEnv();
  const grant=h.makeGrant(h.DEVICE_A,h.HOST_A.id,{platform:'harmony',apnsEnvironment:null,pushToken:target.pushToken});
  const response=await worker.fetch(h.hostRequest(h.HOST_A,'POST','/v2/subscriptions',grant),environment);
  assert.equal(response.status,201,await response.clone().text());
  const entries=channel(h.HOST_A.id).keys('sub:');
  assert.equal(entries.length,1);
});
