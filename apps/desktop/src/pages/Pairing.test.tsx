import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
const writeText = vi.fn(async (_text: string) => {});
vi.mock("@tauri-apps/plugin-clipboard-manager", () => ({ writeText: (t: string) => writeText(t) }));

import { Pairing } from "./Pairing";

const RAW = '{"v":1,"node_id":"node123456789abc","token":"tokABCDEF1234","host_name":"studio"}';

beforeEach(() => {
  invoke.mockReset();
  invoke.mockImplementation(async (cmd: string) => {
    if (cmd === "pair_payload") {
      return { raw: RAW, node_id: "node123456789abc", token: "tokABCDEF1234", host_name: "studio", relay: null };
    }
    return undefined;
  });
});

describe("Pairing", () => {
  it("shows the daemon fingerprint instead of fragments of the pairing token", async () => {
    const { rerender } = render(<Pairing running={true} installed={true} tokenShort="fingerprint-123" />);
    await screen.findByText("studio");
    expect(screen.getByText("fingerprint-123")).toBeInTheDocument();
    expect(screen.queryByText("tokABC…1234")).toBeNull();
    rerender(<Pairing running={true} installed={true} tokenShort="fingerprint-456" />);
    expect(screen.getByText("fingerprint-456")).toBeInTheDocument();
    expect(screen.queryByText("fingerprint-123")).toBeNull();
  });
  it("renders a QR code from the raw payload and shows host details", async () => {
    const { container } = render(<Pairing running={true} installed={true} />);
    await waitFor(() => expect(container.querySelector("svg")).not.toBeNull());
    expect(screen.getByText("studio")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "复制 payload" }));
    expect(writeText).toHaveBeenCalledWith(RAW);
  });

  it("asks before rotating the token and reloads the payload afterwards", async () => {
    render(<Pairing running={true} installed={true} />);
    await waitFor(() => screen.getByRole("button", { name: "轮换 token" }));
    fireEvent.click(screen.getByRole("button", { name: "轮换 token" }));
    expect(invoke).not.toHaveBeenCalledWith("rotate_token");
    fireEvent.click(screen.getByRole("button", { name: "继续轮换" }));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("rotate_token"));
    await waitFor(() => expect(invoke.mock.calls.filter((c) => c[0] === "pair_payload").length).toBe(2));
  });

  it("tells the user to start the service first when not running", () => {
    render(<Pairing running={false} installed={true} />);
    expect(screen.getByText(/先启动主机服务/)).toBeInTheDocument();
  });

  it("does not call pair before the service is installed", () => {
    render(<Pairing running={false} installed={false} />);
    expect(screen.getByText(/先安装后台服务/)).toBeInTheDocument();
    expect(invoke).not.toHaveBeenCalledWith("pair_payload");
    expect(screen.queryByRole("button", { name: "复制 payload" })).toBeNull();
  });
});
