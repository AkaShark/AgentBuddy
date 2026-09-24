import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import { ErrorBanner } from "./ErrorBanner";
vi.mock("@tauri-apps/plugin-clipboard-manager", () => ({ writeText: vi.fn(async () => {}) }));

describe("ErrorBanner", () => {
  it("includes both versions in copied diagnostics and guides failed installs", async () => {
    render(<ErrorBanner error={{ kind: { type: "command_failed", code: 1, stderr: "bootstrap failed" }, detail: "`agentbuddy install` exited with Some(1): bootstrap failed" }} appVersion="0.2.0" daemonVersion="0.1.0" />);
    expect(screen.getByRole("alert")).toHaveTextContent("系统设置 → 通用 → 登录项");
    fireEvent.click(screen.getByText("复制诊断信息"));
    await waitFor(() => expect(writeText).toHaveBeenCalled());
    expect(JSON.parse(vi.mocked(writeText).mock.calls.at(-1)![0])).toMatchObject({ app_version: "0.2.0", daemon_version: "0.1.0" });
  });
});
