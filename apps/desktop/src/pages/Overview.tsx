import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import { host, type HostState } from "../lib/host";
import { StatusBadge } from "../components/StatusBadge";

export function formatUptime(secs: number): string {
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  if (h > 0) return `${h} 小时 ${m} 分`;
  if (m > 0) return `${m} 分`;
  return `${secs} 秒`;
}

interface Props {
  state: HostState;
  busy: boolean;
  run: (fn: () => Promise<void>) => Promise<void>;
  /** injectable for tests; defaults to host.install */
  installAction?: () => Promise<void>;
  /** called only after a successful install (spec §4: jump to pairing) */
  onInstalled?: () => void;
}

export function Overview({ state, busy, run, installAction = host.install, onInstalled }: Props) {
  const install = () =>
    run(async () => {
      await installAction();
      onInstalled?.();
    });

  if (state.install.kind === "not_installed") {
    return (
      <section className="card">
        <h2>把这台 Mac 变成 AgentBuddy 主机</h2>
        <p className="muted">
          安装后台服务后，守护进程会随登录自动启动，退出本 App 也不受影响。手机 App 通过「配对」页的二维码连接这台 Mac。
        </p>
        {state.install_blocked && <p className="banner">{state.install_blocked}</p>}
        <button className="primary" disabled={busy || !!state.install_blocked} onClick={() => void install()}>
          安装后台服务
        </button>
      </section>
    );
  }

  const s = state.status;
  return (
    <>
      {state.install.kind === "path_mismatch" && (
        <div className="banner">
          <pre>服务指向旧版本或旧位置：{state.install.plist_exe}</pre>
          <button
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await host.install();
                await host.restart();
              })
            }
          >
            修复
          </button>
        </div>
      )}
      <div className="row">
        <StatusBadge state={state} />
        {state.running ? (
          <button className="danger" disabled={busy} onClick={() => void run(host.stop)}>
            停止
          </button>
        ) : (
          <button className="primary" disabled={busy} onClick={() => void run(host.start)}>
            启动
          </button>
        )}
        <button disabled={busy} onClick={() => void run(host.restart)}>
          重启
        </button>
        <button disabled={busy || !state.running} onClick={() => void run(host.reload)}>
          重载配置
        </button>
      </div>
      <div className="kv">
        <span className="muted">节点 id</span>
        <span className="row">
          <code>{s?.node_id ?? "—"}</code>
          {s && <button onClick={() => void writeText(s.node_id)}>复制</button>}
        </span>
        <span className="muted">relay</span>
        <code>{s?.relay ?? "默认"}</code>
        <span className="muted">运行时长</span>
        <span>{state.running && s ? formatUptime(s.uptime_secs) : "—"}</span>
        <span className="muted">守护进程版本</span>
        <code>{s?.version ?? "—"}</code>
        <span className="muted">App 版本</span>
        <code>{state.app_version}</code>
        <span className="muted">守护进程</span>
        <code>{state.sidecar_path}</code>
        <span className="muted">配置</span>
        <span className="row">
          <code>{s?.config_path ?? "—"}</code>
          <button onClick={() => void run(() => host.revealPath("config"))}>在 Finder 中显示</button>
        </span>
        <span className="muted">日志</span>
        <span className="row">
          <button onClick={() => void run(() => host.revealPath("logs"))}>在 Finder 中显示</button>
        </span>
      </div>
    </>
  );
}
