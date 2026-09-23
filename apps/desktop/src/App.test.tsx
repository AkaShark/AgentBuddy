import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/api/window", () => ({ getCurrentWindow: () => ({ isVisible: async () => false }) }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async () => () => {}) }));
vi.mock("@tauri-apps/plugin-clipboard-manager", () => ({ writeText: vi.fn(async () => {}) }));

import App from "./App";

describe("App", () => {
  it("shows a status --json failure instead of a silent stopped state", async () => {
    invoke.mockImplementation(async (cmd: string) =>
      cmd === "host_state"
        ? {
            install: { kind: "installed" },
            running: false,
            status: null,
            status_error: { kind: { type: "command_failed", code: 1, stderr: "invalid host.toml" }, detail: "`agentbuddy status --json` exited with Some(1): invalid host.toml" },
            install_blocked: null,
            app_version: "0.1.0",
            sidecar_path: "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy",
          }
        : undefined,
    );
    render(<App />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("invalid host.toml"));
  });
});
