export const policyURL = 'https://akashark.github.io/AgentBuddy/privacy/';
export const reasons = ['unsafe', 'child-safety', 'harassment', 'deception', 'other'] as const;
export function escapeHTML(value: string): string {
  return value.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
}
export function page(content: string, status = 200): Response {
  return new Response(`<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>内容举报 · AgentBuddy</title>
<style>:root{color-scheme:light dark;font:17px/1.6 system-ui,sans-serif}body{max-width:640px;margin:auto;padding:24px;color:#182e24;background:#f5f7f2}h1{font-size:24px}label{display:block;margin:20px 0 8px}textarea,select,button{box-sizing:border-box;font:inherit;width:100%;padding:12px;border-radius:12px;border:1px solid #718678}textarea{min-height:160px}button{background:#286642;color:white;margin-top:24px;min-height:48px}input{width:22px;height:22px;vertical-align:middle}a{color:#286642}small{display:block}.note{color:#53685b}@media(prefers-color-scheme:dark){body{color:#e5ece5;background:#151819}a{color:#a8d9bd}.note{color:#b0c0b5}}</style>
<main>${content}</main></html>`, {status, headers: {
    'Content-Type':'text/html; charset=utf-8', 'Cache-Control':'no-store',
    'Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
    'Referrer-Policy':'same-origin', 'X-Content-Type-Options':'nosniff',
    'Permissions-Policy':'camera=(), microphone=(), geolocation=()',
    'Strict-Transport-Security':'max-age=31536000',
  }});
}
export function form(): Response {
  return page(`<p class="note">搭子 AgentBuddy · 内容安全 / Content safety</p>
<h1>举报 AI 内容 / Report AI content</h1>
<p>可举报不当、危险或侵害他人权益的 AI 输出。无需账号。此表单不会自动读取对话或文件。</p>
<p>You can report harmful or inappropriate AI output without an account. No conversations or files are attached automatically.</p>
<form method="post" action="/report">
<input type="hidden" name="id" value="${crypto.randomUUID()}">
<label for="reason">原因 / Reason</label><select id="reason" name="reason" required>
<option value="">请选择 / Select</option><option value="unsafe">危险或色情内容 / Unsafe or sexual content</option>
<option value="child-safety">儿童安全 / Child safety</option><option value="harassment">仇恨或骚扰 / Hate or harassment</option>
<option value="deception">欺诈或误导 / Fraud or deception</option><option value="other">其他 / Other</option></select>
<label for="content">内容或问题描述 / Content or description</label>
<textarea id="content" name="content" maxlength="6000" required placeholder="请粘贴需要举报的片段，或描述问题及复现步骤。 / Paste an excerpt or describe the issue and reproduction steps."></textarea>
<p class="note">请移除密码、密钥及无关私人信息。涉及儿童的违法图像请仅描述问题，不要上传或复制。 / Remove secrets and unrelated personal data. Describe illegal child imagery; do not copy or upload it.</p>
<label><input type="checkbox" name="consent" value="yes" required> 我确认将本表单内容发送给搭子开发者处理。 / I agree to send this form to the AgentBuddy developer.</label>
<small>由 Cloudflare 提供传输和存储，举报通常在 30 天内清理。网络信息用于防滥用；不用于广告。 / Cloudflare processes and stores reports, normally removed within 30 days. Network information is used to prevent abuse, not for ads.</small>
<p><a href="${policyURL}">隐私政策与删除请求 / Privacy and deletion</a></p>
<button type="submit">提交举报 / Submit report</button></form>`);
}
export function failure(message: string, status: number): Response {
  return page(`<h1>未提交 / Not submitted</h1><p>${escapeHTML(message)}</p><p>请返回上一页重试。 / Go back and try again.</p><a href="/report">重新填写 / New report</a>`, status);
}
