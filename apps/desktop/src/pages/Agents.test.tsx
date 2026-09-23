import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/plugin-dialog", () => ({ open: vi.fn(async () => "/opt/homebrew/bin/claude") }));

import { Agents } from "./Agents";
import type { AgentInfo } from "../lib/host";

const agents: AgentInfo[] = [
  { name: "codex", display_name: "Codex", wire: "jsonl", available: true },
  { name: "claude", display_name: "Claude Code", wire: "jsonl", available: false },
];

beforeEach(() => {
  invoke.mockReset();
  invoke.mockImplementation(async (cmd: string) =>
    cmd === "agent_settings"
      ? { codex: { enabled: true, bin: "codex" }, claude: { enabled: false, bin: "claude" } }
      : undefined,
  );
});

describe("Agents", () => {
  it("shows availability and the enabled flag from host.toml, and toggles it", async () => {
    render(<Agents agents={agents} busy={false} run={async (f) => f()} />);
    expect(screen.getByText("Codex")).toBeInTheDocument();
    expect(screen.getAllByText("未找到")).toHaveLength(1);
    const claude = await screen.findByLabelText("启用 Claude Code");
    await waitFor(() => expect(claude).not.toBeChecked());
    expect(screen.getByLabelText("启用 Codex")).toBeChecked();
    fireEvent.click(claude);
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("agent_set_enabled", { name: "claude", enabled: true }));
  });

  it("lets the user pick a binary with the file dialog", async () => {
    render(<Agents agents={agents} busy={false} run={async (f) => f()} />);
    await screen.findByLabelText("启用 Claude Code");
    fireEvent.click(screen.getAllByRole("button", { name: "选择…" })[1]);
    await waitFor(() =>
      expect(invoke).toHaveBeenCalledWith("agent_set_bin", { name: "claude", path: "/opt/homebrew/bin/claude" }),
    );
  });
});
