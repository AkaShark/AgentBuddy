import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn(async () => undefined) }));
const writeText = vi.fn(async (_text: string) => {});
vi.mock("@tauri-apps/plugin-clipboard-manager", () => ({ writeText: (t: string) => writeText(t) }));

import { Overview } from "./Overview";
import type { HostState } from "../lib/host";

const base: HostState = {
  install: { kind: "installed" },
  running: true,
  status: {
    pid: 4242, node_id: "abcdef1234567890", token_short: "ab12", relay: "https://relay.example",
    config_path: "/Users/me/Library/Application Support/com.akashark.agentbuddycli/host.toml",
    uptime_secs: 3725, agents: [], version: "0.1.0",
  },
  status_error: null,
  install_blocked: null,
  app_version: "0.1.0",
  sidecar_path: "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy",
};

describe("Overview", () => {
  it("shows running state, node id, formatted uptime and the daemon version", () => {
    render(<Overview state={base} busy={false} run={async (f) => f()} />);
    expect(screen.getByText("运行中")).toBeInTheDocument();
    expect(screen.getByText("abcdef1234567890")).toBeInTheDocument();
    expect(screen.getByText("1 小时 2 分")).toBeInTheDocument();
    expect(screen.getByText("守护进程版本")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "停止" })).toBeInTheDocument();
  });

  it("copies the node id through the clipboard plugin", () => {
    render(<Overview state={base} busy={false} run={async (f) => f()} />);
    fireEvent.click(screen.getByRole("button", { name: "复制" }));
    expect(writeText).toHaveBeenCalledWith("abcdef1234567890");
  });

  it("shows the onboarding card when not installed and installs on click", async () => {
    const run = vi.fn(async (f: () => Promise<void>) => f());
    const install = vi.fn(async () => {});
    render(<Overview state={{ ...base, install: { kind: "not_installed" }, running: false, status: null }} busy={false} run={run} installAction={install} />);
    fireEvent.click(screen.getByRole("button", { name: "安装后台服务" }));
    expect(run).toHaveBeenCalled();
    expect(install).toHaveBeenCalled();
  });

  it("jumps to pairing only after a successful install", async () => {
    const onInstalled = vi.fn();
    const notInstalled = { ...base, install: { kind: "not_installed" as const }, running: false, status: null };
    const ok = vi.fn(async () => {});
    const { unmount } = render(<Overview state={notInstalled} busy={false} run={async (f) => f()} installAction={ok} onInstalled={onInstalled} />);
    fireEvent.click(screen.getByRole("button", { name: "安装后台服务" }));
    await waitFor(() => expect(onInstalled).toHaveBeenCalledTimes(1));
    unmount();
    const failing = vi.fn(async () => { throw { kind: { type: "command_failed", code: 1, stderr: "x" }, detail: "boom" }; });
    const swallow = async (f: () => Promise<void>) => { try { await f(); } catch { /* run() keeps the error */ } };
    render(<Overview state={notInstalled} busy={false} run={swallow} installAction={failing} onInstalled={onInstalled} />);
    fireEvent.click(screen.getByRole("button", { name: "安装后台服务" }));
    await waitFor(() => expect(failing).toHaveBeenCalled());
    expect(onInstalled).toHaveBeenCalledTimes(1);
  });

  it("explains and blocks installing from a dmg or translocated copy", () => {
    const blocked = { ...base, install: { kind: "not_installed" as const }, running: false, status: null, install_blocked: "请先把 AgentBuddy 拖进「应用程序」文件夹" };
    render(<Overview state={blocked} busy={false} run={async (f) => f()} />);
    expect(screen.getByText(/拖进「应用程序」/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "安装后台服务" })).toBeDisabled();
  });

  it("offers repair on path mismatch", () => {
    render(<Overview state={{ ...base, install: { kind: "path_mismatch", plist_exe: "/old/agentbuddy" } }} busy={false} run={async (f) => f()} />);
    expect(screen.getByText(/服务指向旧版本或旧位置/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "修复" })).toBeInTheDocument();
  });
});
