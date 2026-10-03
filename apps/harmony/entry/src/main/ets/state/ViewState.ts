import {
  AppAlleycatAgentInfo, AppSessionSummary, AppServerSnapshot, AppThreadSnapshot,
  HydratedConversationItem, PendingApproval, PendingUserInputRequest, SavedServerRecord,
  HydratedPlanStepStatus, MobilePreferences
} from '@agentbuddy/core';
import util from '@ohos.util';
import { presentationRevision } from './MessageIdentity';

export class MessageBlock {
  text: string = '';
  code: boolean = false;
  image: string = '';
  language: string = '';
}

export class MessageView {
  id: string = '';
  renderRevision: number = 0;
  role: string = '';
  title: string = '';
  text: string = '';
  images: string[] = [];
  remoteImagePath: string = '';
  code: boolean = false;
  blocks: MessageBlock[] = [];
}

// ArkUI ForEach retains a node while its key is unchanged. Rust snapshots replace
// records rather than mutating @Observed ArkUI objects, so revise only changed
// presentation nodes. This is UI invalidation, not runtime-state reconciliation.
export function messageViews(items: HydratedConversationItem[], previous: MessageView[]): MessageView[] {
  const old = new Map(previous.map(item => [item.id, item]));
  return items.map(item => {
    const next = messageView(item);
    const last = old.get(item.id);
    next.renderRevision = presentationRevision(last, next);
    return next;
  }).filter(item => item.text.length > 0 || item.images.length > 0);
}

export class ProjectView {
  id: string = '';
  serverId: string = '';
  path: string = '';
  count: number = 0;
}

export class ScreenState {
  notificationStatus: string = '通知未开启';
  ready: boolean = false;
  busy: boolean = false;
  error: string = '';
  hosts: SavedServerRecord[] = [];
  servers: AppServerSnapshot[] = [];
  tasks: AppSessionSummary[] = [];
  projects: ProjectView[] = [];
  agents: AppAlleycatAgentInfo[] = [];
  thread?: AppThreadSnapshot;
  messages: MessageView[] = [];
  approvals: PendingApproval[] = [];
  questions: PendingUserInputRequest[] = [];
  homePreferences: MobilePreferences = { pinnedThreads: [], hiddenThreads: [], homeSelection: {} };
  homeScope: number = 0;
  homeQuery: string = '';
}

// Render-only projection of Rust's typed, hydrated items. No wire payload parsing.
export function messageView(item: HydratedConversationItem): MessageView {
  const result = new MessageView();
  result.id = item.id;
  const content = item.content;
  switch (content.tag) {
    case 'User': result.role = 'user'; result.text = content.value0.text;
      result.images = content.value0.imageDataUris; break;
    case 'Assistant': result.role = 'assistant'; result.title = content.value0.agentNickname ?? '搭子';
      result.text = content.value0.text; break;
    case 'Reasoning': result.title = '思考';
      result.text = [...content.value0.summary, ...content.value0.content].join('\n\n'); break;
    case 'CommandExecution': result.title = '执行命令'; result.code = true;
      result.text = content.value0.command + '\n' + (content.value0.output ?? ''); break;
    case 'ProposedPlan': result.title = '计划'; result.text = content.value0.content; break;
    case 'TodoList': result.title = '进度';
      result.text = content.value0.steps.map(step =>
        (step.status === HydratedPlanStepStatus.Completed ? '✓ ' : '○ ') + step.step).join('\n'); break;
    case 'TurnDiff': result.title = '变更'; result.code = true; result.text = content.value0.diff; break;
    case 'FileChange': result.title = '文件变更';
      result.code = true;
      result.text = content.value0.changes.map(change => change.path + '\n' + change.diff).join('\n\n'); break;
    case 'McpToolCall': result.title = content.value0.tool;
      result.text = content.value0.errorMessage ?? content.value0.contentSummary ?? content.value0.argumentsJson ?? ''; break;
    case 'DynamicToolCall': result.title = content.value0.display?.title ?? content.value0.tool;
      result.text = content.value0.display?.summary ?? content.value0.contentSummary ?? ''; break;
    case 'WebSearch': result.title = '搜索'; result.text = content.value0.query; break;
    case 'Note': result.title = content.value0.title; result.text = content.value0.body; break;
    case 'Error': result.title = content.value0.title; result.text = content.value0.message; break;
    case 'CodeReview': result.title = '代码审查';
      result.text = [content.value0.overallExplanation ?? '', ...content.value0.findings.map(finding =>
        finding.title + '\n' + finding.body)].join('\n\n'); break;
    case 'MultiAgentAction': result.title = '协作任务'; result.text = content.value0.prompt ?? ''; break;
    case 'UserInputResponse': result.title = '已回答';
      result.text = content.value0.questions.map(question => `${question.question}\n${question.answer}`).join('\n\n'); break;
    case 'ImageGeneration': result.title = '生成图片'; result.text = content.value0.revisedPrompt ?? '';
      if (content.value0.imagePng) result.images.push('data:image/png;base64,' + new util.Base64Helper().encodeToStringSync(content.value0.imagePng));
      break;
    case 'ImageView': result.title = '查看图片'; result.text = content.value0.path;
      result.remoteImagePath = content.value0.path; break;
    case 'Divider': result.title = '上下文'; result.text = content.value0.tag; break;
    default: result.title = '任务事件'; break;
  }
  // Markdown needs the whole document to retain list/table structure. Only
  // plain user/tool content needs a separate block; avoid parsing it twice.
  if (result.text && (result.code || result.role === 'user')) {
    const block = new MessageBlock(); block.text = result.text; block.code = result.code;
    result.blocks.push(block);
  }
  return result;
}

export function projectViews(tasks: AppSessionSummary[]): ProjectView[] {
  const projects = new Map<string, ProjectView>();
  for (const task of tasks) {
    if (!task.cwd) continue;
    const id = `${task.key.serverId}:${task.cwd}`;
    let project = projects.get(id);
    if (!project) {
      project = new ProjectView();
      project.id = id; project.serverId = task.key.serverId; project.path = task.cwd;
      projects.set(id, project);
    }
    project.count++;
  }
  return [...projects.values()];
}
