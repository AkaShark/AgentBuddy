import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import type { HostError } from "../lib/host";

export function ErrorBanner({ error, appVersion }: { error: HostError | null; appVersion?: string }) {
  if (!error) return null;
  const diag = JSON.stringify({ app_version: appVersion, ...error }, null, 2);
  return (
    <div className="banner" role="alert">
      <pre>{error.detail}</pre>
      <button onClick={() => void writeText(diag)}>复制诊断信息</button>
    </div>
  );
}
