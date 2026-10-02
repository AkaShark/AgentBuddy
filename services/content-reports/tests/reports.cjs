const {test,after} = require('node:test');
const assert = require('node:assert/strict');
const {execFileSync} = require('node:child_process');
const {mkdtempSync,rmSync} = require('node:fs');
const {tmpdir} = require('node:os');
const path = require('node:path');
const {randomUUID,webcrypto} = require('node:crypto');
globalThis.crypto ??= webcrypto;
const output = mkdtempSync(path.join(tmpdir(),'agentbuddy-reports-test-'));
execFileSync(process.execPath,[require.resolve('typescript/bin/tsc'),'--noEmit','false','--module','commonjs','--moduleResolution','node','--outDir',output],{cwd:path.join(__dirname,'..')});
const {default:worker,ContentReports,parseReport,readLimited} = require(path.join(output,'index.js'));
const {escapeHTML} = require(path.join(output,'page.js'));
after(()=>rmSync(output,{recursive:true,force:true}));
function harness() {
 const data=new Map();let alarm=null,queue=Promise.resolve();
 const storage={get:async k=>structuredClone(data.get(k)),put:async o=>{for(const [k,v] of Object.entries(o))data.set(k,structuredClone(v));},delete:async keys=>{for(const k of Array.isArray(keys)?keys:[keys])data.delete(k);},list:async ({prefix='',limit=1000,startAfter}={})=>new Map([...data].sort(([a],[b])=>a.localeCompare(b)).filter(([k])=>k.startsWith(prefix)&&(!startAfter||k>startAfter)).slice(0,limit)),getAlarm:async()=>alarm,setAlarm:async v=>{alarm=v;}};
 const store=new ContentReports({storage,blockConcurrencyWhile:fn=>{const next=queue.then(fn);queue=next.catch(()=>{});return next;}});
 const env={REPORT_ADMIN_TOKEN:'test-only-token-012345678901234567890123456789',REPORTS:{idFromName:()=>'',get:()=>store}};
 return {data,store,env};
}
const body=(options={})=>new URLSearchParams({id:randomUUID(),reason:'unsafe',content:'Example unsafe output',consent:'yes',...options}).toString();
const submit=(env,b=body(),headers={})=>worker.fetch(new Request('https://reports.test/report',{method:'POST',headers:{Origin:'https://reports.test','Content-Type':'application/x-www-form-urlencoded','CF-Connecting-IP':'192.0.2.1',...headers},body:b}),env);
const admin=(env,suffix='',method='GET',auth=env.REPORT_ADMIN_TOKEN)=>worker.fetch(new Request('https://reports.test/admin/reports'+suffix,{method,headers:{Authorization:`Bearer ${auth}`}}),env);

test('no account, no script, explicit consent, private form response',async()=>{
 const {env}=harness();const r=await worker.fetch(new Request('https://reports.test/report'),env);const html=await r.text();
 assert.equal(r.status,200);assert.match(html,/name="consent"/);assert.doesNotMatch(html,/<script/);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.match(r.headers.get('Content-Security-Policy'),/form-action 'self'/);
});
test('reject missing consent, empty text, invalid reason or identifier',()=>{
 for(const override of [{consent:''},{content:' '},{content:'x'.repeat(6001)},{reason:'bad'},{id:'../secret'}])assert.equal(parseReport(body(override)),null);
});
test('enforce body cap even without Content-Length',async()=>{
 await assert.rejects(readLimited(new Request('https://reports.test',{method:'POST',body:'x'.repeat(65537)})),/too_large/);
});
test('reject cross-origin requests and unsupported body types',async()=>{
 const {env}=harness();assert.equal((await submit(env,body(),{Origin:'https://evil.test'})).status,403);
 assert.equal((await submit(env,body(),{'Content-Type':'application/json'})).status,415);
});
test('store only reviewed fields, return receipt, admin requires token',async()=>{
 const {env,data}=harness();const id=randomUUID();const r=await submit(env,body({id,content:'<script>alert(1)</script>',email:'private@example.test'}));
 assert.equal(r.status,201);assert.match(await r.text(),new RegExp(id));
 assert.deepEqual(Object.keys(data.get('report:'+id)).sort(),['content','createdAt','id','reason']);
 assert.equal((await admin(env,'','GET','bad')).status,401);
 assert.equal((await admin({...env,REPORT_ADMIN_TOKEN:undefined})).status,401);
 const listed=await (await admin(env)).json();assert.equal(listed.reports.length,1);
 assert.equal((await admin(env,'/'+id,'DELETE')).status,200);assert.equal(data.has('report:'+id),false);
});
test('retry does not duplicate or consume rate allowance',async()=>{
 const {env,data}=harness();const b=body();await submit(env,b);await submit(env,b);
 assert.equal([...data.keys()].filter(k=>k.startsWith('report:')).length,1);assert.equal(data.get('limits').total,1);
});
test('limit abuse even under concurrent submissions',async()=>{
 const {env,data}=harness();const results=await Promise.all(Array.from({length:12},()=>submit(env)));
 assert.equal(results.filter(r=>r.status===201).length,10);assert.equal(results.filter(r=>r.status===429).length,2);assert.equal(data.get('limits').total,10);
});
test('content is not publicly listed; expired content is hidden and purged',async()=>{
 const {env,data,store}=harness();const id=randomUUID();data.set('report:'+id,{id,reason:'other',content:'old',createdAt:Date.now()-31*86400000});
 assert.equal((await worker.fetch(new Request('https://reports.test/reports'),env)).status,404);
 assert.equal((await (await admin(env)).json()).reports.length,0);
 await store.alarm();assert.equal(data.has('report:'+id),false);
});
test('HTML escaping and storage failure never show a successful receipt',async()=>{
 assert.equal(escapeHTML('<img onerror="x">'), '&lt;img onerror=&quot;x&quot;&gt;');
 const {env}=harness();env.REPORTS.get=()=>({fetch:async()=>{throw Error('storage failed')}});
 const r=await submit(env);assert.equal(r.status,503);assert.doesNotMatch(await r.text(),/Report received/);
});
