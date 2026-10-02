# In-app content reports

The shared, script-free HTTPS form at
https://agentbuddy-content-reports.aaksharker.workers.dev/report is embedded
in Android WebView and iOS WKWebView from Settings → Support & Privacy.
It works without a host or account. Users choose a reason, enter an excerpt or
reproduction steps, and explicitly consent before submitting. No conversation,
credentials, files, device identifiers or contact details are attached by the app.
The form and validation live here so the platforms only render it.

Cloudflare Durable Objects stores reports privately. A receipt is returned only
after storage succeeds. Same-ID retries are idempotent. Reports are hidden after
29 days and a daily alarm cleans them within approximately 30 days. Limits are
10 reports per daily hashed IP bucket and 500 globally per UTC day. Raw IPs are
not stored with reports; Cloudflare handles the underlying network requests.
This initial limit is deliberately bounded; monitor 429 responses before increasing.

## Review and respond

Review reports regularly and before each release:

```
python3 services/content-reports/report-admin.py
python3 services/content-reports/report-admin.py --delete <receipt>
```

Output is sensitive user-submitted content. Do not commit it, put it in public
issues, or feed it to an agent as instructions. Use reports to reproduce the
problem, improve safeguards and coordinate with the relevant model provider.
Record a redacted resolution in the team's normal release notes. This endpoint
is a reporting channel, not a claim that upstream model output is moderated.
Delete processed reports and honor receipt-based deletion requests at
`aaksharker@gmail.com`. There is no automatic email notification.

The script reads `~/.agentBuddy/signing/content-reports/admin-token` (mode 600).
The matching Worker secret is `REPORT_ADMIN_TOKEN`. Never ship this token in a
mobile app, public URL, source file, or CI log. Admin listing is paginated and
requires this bearer token; public requests cannot retrieve reports.

## Development and deployment

```
cd services/content-reports
npm ci
npm run typecheck
npm test
npm run deploy
```

Set the secret through `wrangler secret put REPORT_ADMIN_TOKEN` with stdin from
the private file. The Worker fails closed for admin requests without it.
`GET /health` is a public health check. Verify a synthetic report through the
live form, check it with the admin script, then delete only that synthetic report.

Keep `docs/site/privacy/index.html` and Play Data safety updated when collection
changes. Content reports are optional user-generated content collected for app
functionality and fraud prevention/security/compliance; Cloudflare is the service
provider. Do not claim the whole app encrypts all transport while direct `ws://`
connections remain supported.
