import { form, page, failure, reasons, escapeHTML } from './page';

interface Env { REPORTS: DurableObjectNamespace; REPORT_ADMIN_TOKEN?: string }
interface Report { id: string; reason: string; content: string; createdAt: number }
const DAY = 86400000;
const MAX_BODY = 65536; // URL-encoded multilingual text expands up to 9x.
const RETENTION = 29 * DAY; // Daily cleanup removes reports within 30 days.
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export async function readLimited(request: Request): Promise<string> {
  const reader = request.body?.getReader();
  if (!reader) return '';
  const chunks: Uint8Array[] = []; let size = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY) { await reader.cancel(); throw new Error('too_large'); }
      chunks.push(value);
    }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    return new TextDecoder('utf-8', {fatal:true,ignoreBOM:false}).decode(bytes);
  } finally { reader.releaseLock(); }
}
export function parseReport(body: string): Omit<Report, 'createdAt'> | null {
  const data = new URLSearchParams(body);
  const id = data.get('id') ?? '', reason = data.get('reason') ?? '';
  const content = (data.get('content') ?? '').trim();
  if (!uuid.test(id) || !reasons.some(r => r === reason) || !content || content.length > 6000 || data.get('consent') !== 'yes') return null;
  return {id, reason, content};
}
async function digest(value: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return Array.from(new Uint8Array(hash), b => b.toString(16).padStart(2,'0')).join('');
}
async function authorized(request: Request, env: Env): Promise<boolean> {
  if (!env.REPORT_ADMIN_TOKEN || env.REPORT_ADMIN_TOKEN.length < 32) return false;
  const actual = await digest(request.headers.get('Authorization') ?? '');
  const expected = await digest(`Bearer ${env.REPORT_ADMIN_TOKEN}`);
  let difference = 0;
  for (let i=0;i<actual.length;i++) difference |= actual.charCodeAt(i) ^ expected.charCodeAt(i);
  return difference === 0;
}
const json = (value: unknown, status = 200) => Response.json(value, {status, headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') return json({ok:true});
    if (url.pathname === '/report' && request.method === 'GET') return form();
    const store = env.REPORTS.get(env.REPORTS.idFromName('reports'));
    if (url.pathname.startsWith('/admin/')) {
      if (!await authorized(request, env)) return json({error:'unauthorized'},401);
      return store.fetch(request);
    }
    if (url.pathname !== '/report' || request.method !== 'POST') return json({error:'not_found'},404);
    if (request.headers.get('Origin') !== url.origin) return failure('Invalid origin / 请从举报页面提交。',403);
    if (!request.headers.get('Content-Type')?.startsWith('application/x-www-form-urlencoded')) return failure('Unsupported format',415);
    let report: Omit<Report,'createdAt'> | null;
    try { report = parseReport(await readLimited(request)); }
    catch { return failure('内容过长或格式错误 / Content too large or invalid.',413); }
    if (!report) return failure('请填写原因、内容并确认提交 / Complete the reason, content and consent.',400);
    // Daily non-reversible bucket; raw IP is never stored with a report.
    const bucket = await digest(`${Math.floor(Date.now()/DAY)}:${request.headers.get('CF-Connecting-IP') ?? 'unknown'}`);
    try {
      const result = await store.fetch(new Request('https://store/submit', {method:'POST',body:JSON.stringify({report,bucket})}));
      if (!result.ok) return failure('提交过于频繁，请稍后重试 / Too many reports. Please try later.',result.status);
      return page(`<h1>举报已收到 / Report received</h1><p>搭子开发者会审阅举报并据此改进内容安全。 / The developer will review this report to improve content safety.</p><p>编号 / Receipt: <code>${escapeHTML(report.id)}</code></p><p>如需删除，请通过隐私政策中的联系方式提供此编号。 / Keep this receipt for a deletion request.</p><a href="/report">返回 / Back</a>`,201);
    } catch { return failure('服务暂不可用，尚未确认提交成功 / Service unavailable; submission has not been confirmed. Please retry.',503); }
  },
};

export class ContentReports {
  constructor(private state: DurableObjectState) {}
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === '/submit' && request.method === 'POST') {
      const {report,bucket} = await request.json<{report:Omit<Report,'createdAt'>;bucket:string}>();
      return this.state.blockConcurrencyWhile(async () => {
        const storage=this.state.storage, now=Date.now(), day=Math.floor(now/DAY);
        const prior=await storage.get<Report>(`report:${report.id}`);
        if(prior) return prior.reason===report.reason && prior.content===report.content ? json({id:report.id}) : json({error:'conflict'},409);
        const counts=await storage.get<{day:number;total:number;ips:Record<string,number>}>('limits');
        const limits=counts?.day===day ? counts : {day,total:0,ips:{}};
        if(limits.total>=500 || (limits.ips[bucket]??0)>=10) return json({error:'rate_limited'},429);
        limits.total++;limits.ips[bucket]=(limits.ips[bucket]??0)+1;
        await storage.put({[`report:${report.id}`]:{...report,createdAt:now},limits});
        if(!await storage.getAlarm()) await storage.setAlarm(now+DAY);
        return json({id:report.id},201);
      });
    }
    if(url.pathname==='/admin/reports' && request.method==='GET') {
      const cursor=url.searchParams.get('cursor');
      if(cursor && !uuid.test(cursor)) return json({error:'invalid_cursor'},400);
      const reports=await this.state.storage.list<Report>({prefix:'report:',limit:100,...(cursor?{startAfter:`report:${cursor}`}:{})});
      const values=[...reports.values()].filter(r=>r.createdAt>Date.now()-RETENTION);
      const last=[...reports.keys()].at(-1)?.slice(7);
      return json({reports:values,nextCursor:reports.size===100?last:null});
    }
    if(url.pathname.startsWith('/admin/reports/') && request.method==='DELETE') {
      const id=url.pathname.split('/').at(-1)!;
      if(!uuid.test(id)) return json({error:'invalid_id'},400);
      await this.state.storage.delete(`report:${id}`);return json({deleted:true});
    }
    return json({error:'not_found'},404);
  }
  async alarm(): Promise<void> {
    let startAfter: string|undefined;
    const cutoff=Date.now()-RETENTION;
    do {
      const rows=await this.state.storage.list<Report>({prefix:'report:',limit:100,startAfter});
      const expired=[...rows].filter(([,r])=>r.createdAt<=cutoff).map(([key])=>key);
      if(expired.length) await this.state.storage.delete(expired);
      startAfter=rows.size===100?[...rows.keys()].at(-1):undefined;
    } while(startAfter);
    const limits=await this.state.storage.get<{day:number}>('limits');
    if(limits && limits.day<Math.floor(Date.now()/DAY)) await this.state.storage.delete('limits');
    if((await this.state.storage.list({prefix:'report:',limit:1})).size) await this.state.storage.setAlarm(Date.now()+DAY);
  }
}
