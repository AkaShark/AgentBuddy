import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import type { HostError } from "../lib/host";

export function ErrorBanner({ error, appVersion, daemonVersion }: { error: HostError | null; appVersion?: string; daemonVersion?: string | null }) {
  if (!error) return null;
  const diag = JSON.stringify({ app_version: appVersion, daemon_version: daemonVersion ?? "unknown", ...error }, null, 2);
  return (
    <div className="banner" role="alert">
      <pre>{error.detail}</pre>
      {error.detail.includes("`agentbuddy install`") && <p>请前往「系统设置 → 通用 → 登录项」检查 AgentBuddy 后台服务是否获准运行。</p>}
      <button onClick={() => void writeText(diag)}>复制诊断信息</button>
    </div>
  );
}
