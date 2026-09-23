import type { HostState } from "../lib/host";

export function statusText(s: HostState): { text: string; ok: boolean } {
  if (s.install.kind === "not_installed") return { text: "未安装", ok: false };
  if (s.install.kind === "path_mismatch") return { text: "需要修复", ok: false };
  return s.running ? { text: "运行中", ok: true } : { text: "已停止", ok: false };
}

export function StatusBadge({ state }: { state: HostState }) {
  const { text, ok } = statusText(state);
  return <span className={`badge ${ok ? "ok" : "warn"}`}>{text}</span>;
}
