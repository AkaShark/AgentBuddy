import common from '@ohos.app.ability.common';
import fs from '@ohos.file.fs';
import { AppClient, AppSshHostKeyMismatch, AppSshHostKeyMismatchKind, AppSshSessionResult,
  AgentAvailabilityStatus, ClientError, ClientErrorSshHostKeyMismatchPayload,
  RemoteAgentAvailability, SavedServerRecord, ServerBridge, SshBridge, SshBridgeTransport,
  SshCredentialRecord, TerminalSshTrustStore, setSshTrustStore } from '@agentbuddy/core';
import { SshSecurity } from './SshSecurity';
import { errorMessage } from './ErrorPresentation';

export class SshConnectionState {
  busy: boolean = false;
  label: string = '';
  error: string = '';
  agents: RemoteAgentAvailability[] = [];
  mismatch?: AppSshHostKeyMismatch;
  sessionReady: boolean = false;
  connected: boolean = false;
}

class SshAttempt {
  host: string = '';
  port: number = 22;
  name: string = '';
  credential?: SshCredentialRecord;
  remember: boolean = false;
}

export class SshConnection {
  private state = new SshConnectionState();
  private ssh = new SshBridge();
  private trust: TerminalSshTrustStore;
  private attempt?: SshAttempt;
  private session?: AppSshSessionResult;
  private generation: number = 0;
  private observer?: (state: SshConnectionState) => void;
  private sessions = new Map<string, string>();

  constructor(private context: common.Context, readonly security: SshSecurity, private bridge: ServerBridge,
    private client: AppClient, private connected: (host: SavedServerRecord) => Promise<void>) {
    this.trust = new TerminalSshTrustStore(security); setSshTrustStore(this.trust);
  }

  observe(observer: (state: SshConnectionState) => void): void { this.observer = observer; this.emit(); }
  private emit(): void { this.observer?.(Object.assign(new SshConnectionState(), this.state)); }
  clearError(): void { this.state.error = ''; this.emit(); }
  label(kind: string): string { return this.client.agentMetadata(kind)?.displayName ?? kind; }
  supportsBridge(agent: RemoteAgentAvailability): boolean {
    return agent.kind !== 'codex' && agent.status === AgentAvailabilityStatus.Available &&
      !!this.client.agentMetadata(agent.kind)?.capabilities?.supportsSshBridge;
  }

  async cancel(): Promise<void> {
    this.generation++; this.observer = undefined;
    const session = this.session; this.session = undefined; this.attempt = undefined;
    this.state = new SshConnectionState();
    if (session) await this.ssh.sshClose(session.sessionId).catch(() => {});
  }

  async disconnect(id: string): Promise<void> {
    const session = this.sessions.get(id); this.sessions.delete(id);
    if (session) await this.ssh.sshClose(session).catch(() => {});
  }

  async connect(host: string, port: number, name: string, credential: SshCredentialRecord, remember: boolean): Promise<void> {
    if (this.state.busy) return;
    this.attempt = { host: host.trim(), port, name: name.trim() || host.trim(), credential, remember };
    await this.openSession();
  }

  private async openSession(): Promise<void> {
    const attempt = this.attempt; const credential = attempt?.credential;
    if (!attempt || !credential) return;
    const generation = ++this.generation;
    this.state = new SshConnectionState(); this.state.busy = true; this.state.label = '正在验证 SSH 连接…'; this.emit();
    try {
      const old = this.session; this.session = undefined;
      if (old) await this.ssh.sshClose(old.sessionId);
      const session = await this.ssh.sshOpenSession(attempt.host, attempt.port, credential.username,
        credential.password, credential.privateKeyPem, credential.passphrase, credential.unlockMacosKeychain, false);
      if (generation !== this.generation) { await this.ssh.sshClose(session.sessionId); return; }
      this.session = session;
      this.state.label = '正在检查主机上的智能体…'; this.emit();
      const agents = await this.ssh.sshProbeRemoteAgents(session.sessionId);
      if (generation !== this.generation) return;
      this.state.agents = agents; this.state.sessionReady = true; this.state.label = '选择要连接的智能体';
    } catch (error) { if (generation === this.generation) this.failure(error); }
    finally { if (generation === this.generation) { this.state.busy = false; this.emit(); } }
  }

  async trustAndRetry(): Promise<void> {
    const mismatch = this.state.mismatch;
    if (!mismatch || this.state.busy) return;
    try {
      // Write the platform backend directly; calling a synchronous Rust pin API
      // here would re-enter the ArkTS callback on its own UI thread.
      if (mismatch.kind === AppSshHostKeyMismatchKind.TrustStoreUnavailable) this.security.remove(mismatch.host, mismatch.port);
      else this.security.write(mismatch.host, mismatch.port, mismatch.fingerprint);
      await this.openSession();
    } catch (error) { this.failure(error); this.emit(); }
  }

  async choose(kind: string): Promise<void> {
    const attempt = this.attempt; const session = this.session; const credential = attempt?.credential;
    if (!attempt || !session || !credential || this.state.busy) return;
    const generation = this.generation;
    this.state.busy = true; this.state.error = ''; this.state.label = '正在启动远程任务服务…'; this.emit();
    let id = `ssh:${session.normalizedHost}:${attempt.port}`;
    try {
      if (kind === 'codex') {
        await this.ssh.sshClose(session.sessionId); this.session = undefined;
        await this.bridge.connectRemoteOverSsh(id, attempt.name, session.normalizedHost, attempt.port,
          credential.username, credential.password, credential.privateKeyPem, credential.passphrase,
          credential.unlockMacosKeychain, false, undefined);
      } else {
        id = `ssh-bridge:${session.normalizedHost}:${attempt.port}`;
        const root = this.context.filesDir + '/ssh-bridges/' + session.normalizedHost.replace(/[^A-Za-z0-9._-]/g, '_') + '-' + attempt.port;
        fs.mkdirSync(root, true);
        const result = await this.ssh.sshConnectBridgeSession(session.sessionId, id, attempt.name,
          session.normalizedHost, root, [kind], SshBridgeTransport.Ephemeral);
        id = result.serverId; this.sessions.set(id, session.sessionId); this.session = undefined;
      }
      if (generation !== this.generation) { this.bridge.disconnectServer(id); await this.disconnect(id); return; }
      this.security.saveCredential(session.normalizedHost, attempt.port, attempt.remember ? credential : undefined);
      await this.connected({ id, name: attempt.name, hostname: session.normalizedHost, port: 0, codexPorts: [],
        sshPort: attempt.port, source: 'ssh', hasCodexServer: true, rememberedByUser: true, preferredConnectionMode: 'ssh',
        alleycatAgentName: kind === 'codex' ? undefined : kind, alleycatAgentWire: kind === 'codex' ? undefined : 'ssh-bridge' });
      if (generation !== this.generation) return;
      this.state.connected = true; this.attempt = undefined;
    } catch (error) {
      this.bridge.disconnectServer(id); await this.disconnect(id);
      await this.ssh.sshClose(session.sessionId).catch(() => {});
      if (generation === this.generation) {
        this.session = undefined; this.state.sessionReady = false; this.state.agents = [];
        this.failure(error);
      }
    }
    finally { if (generation === this.generation) { this.state.busy = false; this.emit(); } }
  }

  private failure(error: unknown): void {
    if (error instanceof ClientError && error.payload instanceof ClientErrorSshHostKeyMismatchPayload) {
      this.state.mismatch = error.payload.mismatch;
      this.state.error = '';
    } else { this.state.error = errorMessage(error, 'SSH 连接失败，请重试。'); }
  }
}
