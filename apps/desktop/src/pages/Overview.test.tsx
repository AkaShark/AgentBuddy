import { render, screen, fireEvent } from "@testing-library/react";
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

  it("offers repair on path mismatch", () => {
    render(<Overview state={{ ...base, install: { kind: "path_mismatch", plist_exe: "/old/agentbuddy" } }} busy={false} run={async (f) => f()} />);
    expect(screen.getByText(/服务指向旧版本或旧位置/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "修复" })).toBeInTheDocument();
  });
});
