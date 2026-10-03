import type { AppSessionSummary, AppServerSnapshot, MobilePreferences, ThreadKey } from '@agentbuddy/core';

export class HomeTaskItem {
  key: ThreadKey = { serverId: '', threadId: '' };
  title: string = '';
  preview: string = '';
  host: string = '';
  runtime: string = '';
  active: boolean = false;
  pinned: boolean = false;
  hidden: boolean = false;
  placeholder: boolean = false;
}

export function taskKey(key: ThreadKey): string { return JSON.stringify([key.serverId, key.threadId]); }

// Render-only home shaping, matching Android HomeTaskList: pins in saved order,
// otherwise ten recent tasks; hidden tasks stay out of the normal home list.
export function homeTaskItems(sessions: AppSessionSummary[], servers: AppServerSnapshot[],
  preferences: MobilePreferences, scope: number, query: string, projectPath: string = ''): HomeTaskItem[] {
  const pinned = new Set(preferences.pinnedThreads.map(key => taskKey(key)));
  const hidden = new Set(preferences.hiddenThreads.map(key => taskKey(key)));
  const serverId = preferences.homeSelection.selectedServerId;
  const search = query.trim().toLowerCase();
  const candidates = sessions.filter(session =>
    (!serverId || session.key.serverId === serverId) && (!projectPath || session.cwd === projectPath) &&
    (scope === 2 ? hidden.has(taskKey(session.key)) : !hidden.has(taskKey(session.key))) &&
    (!search || (session.title + '\n' + session.preview + '\n' + (session.lastResponsePreview ?? '')).toLowerCase().includes(search)));
  let selected = candidates;
  if (scope === 0 && !search && pinned.size === 0) selected = candidates.slice(0, 10);
  const result = selected.map(session => {
    const item = new HomeTaskItem(); item.key = session.key; item.title = session.title || '新任务';
    item.preview = session.lastResponsePreview || session.preview; item.host = session.serverDisplayName;
    item.runtime = session.agentRuntimeKind; item.active = session.hasActiveTurn;
    item.pinned = pinned.has(taskKey(session.key)); item.hidden = hidden.has(taskKey(session.key));
    return item;
  });
  if (scope !== 0 || search || pinned.size === 0) return result;
  const byKey = new Map(result.map(item => [taskKey(item.key), item]));
  const allKeys = new Set(sessions.map(session => taskKey(session.key)));
  const pins: HomeTaskItem[] = [];
  for (const key of preferences.pinnedThreads) {
    if (hidden.has(taskKey(key)) || (serverId && key.serverId !== serverId)) continue;
    const existing = byKey.get(taskKey(key));
    if (existing) { pins.push(existing); continue; }
    // A loaded item outside the project filter is not a missing task.
    if (projectPath || allKeys.has(taskKey(key))) continue;
    const server = servers.find(value => value.serverId === key.serverId);
    if (!server) continue;
    const item = new HomeTaskItem(); item.key = key; item.title = '等待加载任务';
    item.preview = '点按重新读取，或在更多操作中取消固定。'; item.host = server.displayName;
    item.pinned = true; item.placeholder = true; pins.push(item);
  }
  return pins;
}

export function taskRenderKey(item: HomeTaskItem): string {
  return taskKey(item.key) + ':' + JSON.stringify([item.title, item.preview, item.host,
    item.runtime, item.active, item.pinned, item.hidden, item.placeholder]);
}
