#!/usr/bin/env python3
"""Private report review/deletion. Token stays outside the checkout and command line."""
import argparse
import json
from pathlib import Path
import urllib.request
import uuid

BASE = 'https://agentbuddy-content-reports.aaksharker.workers.dev'
parser = argparse.ArgumentParser()
parser.add_argument('--token-file', type=Path, default=Path.home()/'.agentBuddy/signing/content-reports/admin-token')
parser.add_argument('--delete', metavar='RECEIPT', help='Delete one reviewed report by receipt')
args = parser.parse_args()
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise RuntimeError('Refusing to redirect report credentials')
opener = urllib.request.build_opener(NoRedirect)
token = args.token_file.read_text().strip()
def request(path, method='GET'):
    req = urllib.request.Request(BASE+path, method=method, headers={'Authorization':'Bearer '+token, 'User-Agent':'AgentBuddy-Report-Admin/1.0'})
    with opener.open(req, timeout=30) as response:
        return json.load(response)
if args.delete:
    receipt = str(uuid.UUID(args.delete))
    print(json.dumps(request('/admin/reports/'+receipt, 'DELETE')))
else:
    cursor = None
    while True:
        result = request('/admin/reports'+('?cursor='+cursor if cursor else ''))
        for report in result['reports']:
            print(json.dumps(report, ensure_ascii=False))
        cursor = result.get('nextCursor')
        if not cursor:
            break
