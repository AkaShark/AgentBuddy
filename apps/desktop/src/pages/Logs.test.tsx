import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

const invoke = vi.fn();
const listeners: Array<(e: { payload: { line: string } }) => void> = [];
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/api/event", () => ({
  listen: vi.fn(async (_name: string, cb: (e: { payload: { line: string } }) => void) => {
    listeners.push(cb);
    return () => {};
  }),
}));

import { Logs } from "./Logs";

describe("Logs", () => {
  it("loads the tail, filters lines and appends streamed lines when following", async () => {
    invoke.mockImplementation(async (cmd: string) => (cmd === "logs_tail" ? ["INFO started", "WARN slow"] : undefined));
    render(<Logs active={true} />);
    await waitFor(() => expect(screen.getByText(/INFO started/)).toBeInTheDocument());
    fireEvent.change(screen.getByPlaceholderText("过滤"), { target: { value: "WARN" } });
    expect(screen.queryByText(/INFO started/)).toBeNull();
    fireEvent.click(screen.getByLabelText("跟随"));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("logs_follow_start"));
    await waitFor(() => expect(listeners.length).toBeGreaterThan(0));
    listeners.forEach((cb) => cb({ payload: { line: "WARN streamed" } }));
    await waitFor(() => expect(screen.getByText(/WARN streamed/)).toBeInTheDocument());
  });
});
