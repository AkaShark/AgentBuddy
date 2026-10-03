#!/usr/bin/env python3
"""Synthetic app-server for physical-device shared history regression.

No real tasks, credentials or filesystem actions. Every socket has private history.
Run with --bind <Mac LAN IP> for iPhone; Android can use adb reverse on loopback.
Requires Python websockets. Keep logs and downloaded dependencies in AgentBuddy's
external work directory. Clients must explicitly opt in to this fixture.
"""
import argparse
import asyncio
import copy
import json
from websockets import serve


def turn(index):
    return {'id': f't{index}', 'status': 'completed', 'itemsView': 'full', 'error': None,
            'items': [
                {'id': f'u{index}', 'type': 'userMessage', 'content': [{'type': 'text', 'text': f'prompt-{index}', 'textElements': []}]},
                {'id': f's{index}', 'type': 'sleep', 'durationMs': 2000},
                {'id': f'a{index}', 'type': 'agentMessage', 'text': f'answer-{index}'}]}


def thread(name, turns):
    return {'id': name, 'sessionId': name, 'preview': 'Device regression fixture',
            'ephemeral': False, 'modelProvider': 'openai', 'createdAt': 1, 'updatedAt': 2,
            'status': {'type': 'idle'}, 'cwd': '/tmp/agentbuddy-history-fixture',
            'cliVersion': '0.160.0', 'source': 'cli', 'turns': turns, 'name': name}


def resumed(name, turns):
    return {'thread': thread(name, turns), 'model': 'fixture', 'modelProvider': 'openai',
            'cwd': '/tmp/agentbuddy-history-fixture', 'approvalPolicy': 'never',
            'approvalsReviewer': 'user', 'sandbox': {'type': 'readOnly'}, 'reasoningEffort': None}


async def connection(socket, *_):
    histories = {name: [turn(i) for i in range(1, 4)] for name in ['history', 'history-error']}
    mutations = []
    async for raw in socket:
        message = json.loads(raw)
        if 'id' not in message:
            continue
        method, params = message.get('method'), message.get('params') or {}
        name = params.get('threadId', 'history')
        error = None
        if method == 'initialize':
            result = {'userAgent': 'AgentBuddy QA fixture'}
        elif method == 'thread/read':
            result = {'thread': thread(name, copy.deepcopy(histories[name]) if params.get('includeTurns') else [])}
        elif method == 'thread/resume':
            result = resumed(name, [] if params.get('excludeTurns') else copy.deepcopy(histories[name]))
        elif method == 'thread/turns/list':
            assert params.get('itemsView') == 'full', 'device must request full history'
            remaining = list(reversed(histories[name]))
            offset = int(params.get('cursor') or 0)
            limit = params.get('limit') or 20
            page = remaining[offset:offset+limit]
            next_offset = offset+len(page)
            result = {'data': copy.deepcopy(page), 'nextCursor': str(next_offset) if next_offset < len(remaining) else None,
                      'backwardsCursor': None}
        elif method == 'thread/fork':
            fork = name + '-fork'
            histories[fork] = copy.deepcopy(histories[name])
            result = resumed(fork, copy.deepcopy(histories[fork]))
        elif method == 'thread/revert':
            mutations.append((method, name, params['beforeTurnId']))
            if name == 'history-error':
                error = {'code': -32000, 'message': 'Injected failure: do not retry with rollback'}
            else:
                index = next(i for i, value in enumerate(histories[name]) if value['id'] == params['beforeTurnId'])
                histories[name] = histories[name][:index]
            result = {'thread': thread(name, []), 'turnsBackwardsCursor': '0' if histories[name] else None,
                      'itemsBackwardsCursor': None}
        elif method == 'thread/rollback':
            raise AssertionError('modern mutation failures must not fall back to rollback')
        elif method in ['thread/list', 'model/list']:
            result = {'data': [], 'nextCursor': None}
        elif method == 'account/read':
            result = {'account': None, 'requiresOpenaiAuth': False}
        elif method == 'account/rateLimits/read':
            result = {'rateLimits': {'primary': None, 'secondary': None, 'credits': None, 'planType': None}}
        elif method in ['thread/archive', 'thread/unsubscribe']:
            result = {}
        else:
            result = {}
            error = {'code': -32601, 'message': f'Fixture does not implement {method}'}
        print(json.dumps({'method': method, 'thread': name, 'cursor': params.get('cursor'),
                          'error': bool(error), 'mutations': mutations if method.startswith('thread/re') else None}), flush=True)
        await socket.send(json.dumps({'jsonrpc': '2.0', 'id': message['id'], **({'error': error} if error else {'result': result})}))


async def main(args):
    async with serve(connection, args.bind, args.port):
        print(f'Fixture listening on {args.bind}:{args.port}', flush=True)
        await asyncio.Future()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=18765)
    asyncio.run(main(parser.parse_args()))
