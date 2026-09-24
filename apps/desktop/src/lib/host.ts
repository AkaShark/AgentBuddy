import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { useCallback, useEffect, useRef, useState } from "react";

export type InstallState =
  | { kind: "not_installed" }
  | { kind: "installed" }
  | { kind: "path_mismatch"; plist_exe: string };

export interface AgentInfo {
  name: string;
  display_name: string;
  wire: unknown;
  available: boolean;
  presentation?: unknown;
  capabilities?: unknown;
}

export interface StatusInfo {
  pid: number;
  node_id: string;
  token_short: string;
  relay: string | null;
  config_path: string;
  uptime_secs: number;
  agents: AgentInfo[];
  /** Daemon version; absent in older daemons. */
  version?: string | null;
}

export interface HostState {
  install: InstallState;
  running: boolean;
  status: StatusInfo | null;
  /** `status --json` itself failed (e.g. invalid host.toml). */
  status_error: HostError | null;
  /** Why installing from this bundle location is refused (dmg / translocated). */
  install_blocked: string | null;
  app_version: string;
  sidecar_path: string;
}

export interface PairPayload {
  raw: string;
  node_id: string;
  token: string;
  host_name: string | null;
  relay: string | null;
}

/** Per-agent settings read from host.toml. */
export interface AgentSettings {
  enabled: boolean;
  bin: string | null;
  host?: string | null;
  port?: number | null;
}

export type HostErrorKind =
  | { type: "sidecar_missing" }
  | { type: "command_failed"; code: number | null; stderr: string }
  | { type: "parse_failed" }
  | { type: "permission_denied" }
  | { type: "config_invalid" }
  | { type: "install_location" }
  | { type: "not_installed" };

export interface HostError {
  kind: HostErrorKind;
  detail: string;
}

export type PathKind = "config" | "logs";

export const host = {
  state: () => invoke<HostState>("host_state"),
  install: () => invoke<void>("host_install"),
  uninstall: () => invoke<void>("host_uninstall"),
  start: () => invoke<void>("host_start"),
  stop: () => invoke<void>("host_stop"),
  restart: () => invoke<void>("host_restart"),
  reload: () => invoke<void>("host_reload"),
  upgrade: () => invoke<void>("host_upgrade"),
  pairPayload: () => invoke<PairPayload>("pair_payload"),
  rotateToken: () => invoke<void>("rotate_token"),
  agentSettings: () => invoke<Record<string, AgentSettings>>("agent_settings"),
  setAgentEnabled: (name: string, enabled: boolean) =>
    invoke<void>("agent_set_enabled", { name, enabled }),
  setAgentBin: (name: string, path: string) => invoke<void>("agent_set_bin", { name, path }),
  setCodexEndpoint: (host: string | null, port: number | null) => invoke<void>("codex_set_endpoint", { host, port }),
  logsTail: (lines: number) => invoke<string[]>("logs_tail", { lines }),
  logsFollowStart: () => invoke<void>("logs_follow_start"),
  logsFollowStop: () => invoke<void>("logs_follow_stop"),
  revealPath: (kind: PathKind) => invoke<void>("reveal_path", { kind }),
};

export function isHostError(e: unknown): e is HostError {
  return (
    typeof e === "object" &&
    e !== null &&
    "detail" in e &&
    "kind" in e &&
    typeof (e as HostError).detail === "string"
  );
}

export function describeError(e: unknown): string {
  if (isHostError(e)) return e.detail;
  if (e instanceof Error) return e.message;
  return String(e);
}

export function pollIntervalMs(visible: boolean, consecutiveFailures = 0, running = true): number {
  if (consecutiveFailures >= 3) return 30000;
  return visible ? (running ? 2000 : 5000) : 30000;
}

function asHostError(e: unknown): HostError {
  return isHostError(e) ? e : { kind: { type: "parse_failed" }, detail: describeError(e) };
}

async function windowVisible(): Promise<boolean> {
  try {
    return await getCurrentWindow().isVisible();
  } catch {
    return true;
  }
}

export function useHostState() {
  const [state, setState] = useState<HostState | null>(null);
  const [error, setError] = useState<HostError | null>(null);
  const [busy, setBusy] = useState(false);
  const [unreachable, setUnreachable] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const failures = useRef(0);
  const running = useRef(true);

  const refresh = useCallback(async () => {
    try {
      const next = await host.state();
      running.current = next.running;
      setState(next);
      setError(null);
      failures.current = 0;
      setUnreachable(false);
    } catch (e) {
      failures.current += 1;
      if (failures.current >= 3) setUnreachable(true);
      setError(asHostError(e));
    }
  }, []);

  const run = useCallback(
    async (fn: () => Promise<void>) => {
      setBusy(true);
      let actionError: HostError | null = null;
      try {
        await fn();
      } catch (e) {
        actionError = asHostError(e);
      } finally {
        setBusy(false);
        await refresh();
        // A successful refresh clears `error`; the action's own failure must stay visible.
        if (actionError) setError(actionError);
      }
    },
    [refresh],
  );

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      await refresh();
      if (cancelled) return;
      const visible = await windowVisible();
      timer.current = setTimeout(tick, pollIntervalMs(visible, failures.current, running.current));
    };
    void tick();
    return () => {
      cancelled = true;
      if (timer.current) clearTimeout(timer.current);
    };
  }, [refresh]);

  return { state, error, refresh, busy, run, unreachable };
}
