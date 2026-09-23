import { describe, it, expect, vi, beforeEach } from "vitest";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));

import { host, isHostError, describeError, pollIntervalMs } from "./host";

beforeEach(() => invoke.mockReset());

describe("host client", () => {
  it("maps methods to command names and argument keys", async () => {
    invoke.mockResolvedValue(undefined);
    await host.setAgentEnabled("codex", false);
    expect(invoke).toHaveBeenCalledWith("agent_set_enabled", { name: "codex", enabled: false });
    await host.setAgentBin("claude", "/usr/local/bin/claude");
    expect(invoke).toHaveBeenCalledWith("agent_set_bin", { name: "claude", path: "/usr/local/bin/claude" });
    await host.logsTail(500);
    expect(invoke).toHaveBeenCalledWith("logs_tail", { lines: 500 });
    await host.revealPath("logs");
    expect(invoke).toHaveBeenCalledWith("reveal_path", { kind: "logs" });
    await host.agentSettings();
    expect(invoke).toHaveBeenCalledWith("agent_settings");
    await host.state();
    expect(invoke).toHaveBeenCalledWith("host_state");
  });

  it("recognises HostError payloads and describes them", () => {
    const err = { kind: { type: "command_failed", code: 1, stderr: "boom" }, detail: "`agentbuddy stop` exited with Some(1): boom" };
    expect(isHostError(err)).toBe(true);
    expect(describeError(err)).toContain("boom");
    expect(isHostError(new Error("x"))).toBe(false);
    expect(describeError(new Error("x"))).toBe("x");
    expect(describeError("plain")).toBe("plain");
  });

  it("polls fast when visible, slow when hidden, and backs off after 3 failures", () => {
    expect(pollIntervalMs(true, 0)).toBe(2000);
    expect(pollIntervalMs(false, 0)).toBe(30000);
    expect(pollIntervalMs(true, 2)).toBe(2000);
    expect(pollIntervalMs(true, 3)).toBe(30000);
  });
});
