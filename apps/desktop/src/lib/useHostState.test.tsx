import { renderHook, act, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/api/window", () => ({ getCurrentWindow: () => ({ isVisible: async () => false }) }));

import { host, useHostState } from "./host";

const state = {
  install: { kind: "installed" },
  running: true,
  status: null,
  app_version: "0.1.0",
  sidecar_path: "/x/agentbuddy",
};

describe("useHostState", () => {
  it("keeps a failed action's error visible after the follow-up refresh", async () => {
    const installError = { kind: { type: "command_failed", code: 1, stderr: "bootstrap failed" }, detail: "install failed: bootstrap failed" };
    invoke.mockImplementation(async (cmd: string) => {
      if (cmd === "host_state") return state;
      if (cmd === "host_install") throw installError;
      return undefined;
    });
    const { result } = renderHook(() => useHostState());
    await waitFor(() => expect(result.current.state).not.toBeNull());
    await act(async () => {
      await result.current.run(host.install);
    });
    expect(result.current.error?.detail).toBe("install failed: bootstrap failed");
    expect(result.current.busy).toBe(false);
  });

  it("flags the control channel unreachable after three failed polls", async () => {
    invoke.mockRejectedValue({ kind: { type: "sidecar_missing" }, detail: "agentbuddy not found" });
    const { result } = renderHook(() => useHostState());
    for (let i = 0; i < 3; i++) {
      await act(async () => {
        await result.current.refresh();
      });
    }
    expect(result.current.unreachable).toBe(true);
    expect(result.current.error?.detail).toBe("agentbuddy not found");
  });
});
