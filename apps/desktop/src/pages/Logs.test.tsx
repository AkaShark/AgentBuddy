import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const invoke = vi.fn();
const listeners: Record<string, Array<(e: { payload: unknown }) => void>> = {};
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/api/event", () => ({
  listen: vi.fn(async (name: string, cb: (e: { payload: unknown }) => void) => {
    (listeners[name] ??= []).push(cb);
    return () => {
      listeners[name] = (listeners[name] ?? []).filter((f) => f !== cb);
    };
  }),
}));

import { Logs } from "./Logs";

const emit = (name: string, payload: unknown) => (listeners[name] ?? []).forEach((cb) => cb({ payload }));

beforeEach(() => {
  invoke.mockReset();
  invoke.mockImplementation(async (cmd: string) => (cmd === "logs_tail" ? ["INFO started", "WARN slow"] : undefined));
  for (const k of Object.keys(listeners)) delete listeners[k];
});

describe("Logs", () => {
  it("loads the tail, filters lines and appends streamed lines when following", async () => {
    render(<Logs active={true} />);
    await waitFor(() => expect(screen.getByText(/INFO started/)).toBeInTheDocument());
    fireEvent.change(screen.getByPlaceholderText("过滤"), { target: { value: "WARN" } });
    expect(screen.queryByText(/INFO started/)).toBeNull();
    fireEvent.click(screen.getByLabelText("跟随"));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("logs_follow_start"));
    await waitFor(() => expect(listeners["log-line"]?.length).toBeGreaterThan(0));
    emit("log-line", { line: "WARN streamed" });
    await waitFor(() => expect(screen.getByText(/WARN streamed/)).toBeInTheDocument());
  });

  it("unchecks follow when the backend stops the stream because the window was hidden", async () => {
    render(<Logs active={true} />);
    const follow = screen.getByLabelText("跟随");
    fireEvent.click(follow);
    await waitFor(() => expect(follow).toBeChecked());
    await waitFor(() => expect(listeners["follow-stopped"]?.length).toBeGreaterThan(0));
    emit("follow-stopped", null);
    await waitFor(() => expect(follow).not.toBeChecked());
  });
});
