import { useEffect, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { Nav } from "./components/Nav";
import { ErrorBanner } from "./components/ErrorBanner";
import { Overview } from "./pages/Overview";
import { Pairing } from "./pages/Pairing";
import { Agents } from "./pages/Agents";
import { Logs } from "./pages/Logs";
import { useHostState } from "./lib/host";

export type Page = "overview" | "agents" | "pairing" | "logs";

const pages: Page[] = ["overview", "agents", "pairing", "logs"];

export default function App() {
  const [page, setPage] = useState<Page>("overview");
  const { state, error, busy, run, unreachable } = useHostState();

  useEffect(() => {
    const un = listen<string>("navigate", (e) => {
      if ((pages as string[]).includes(e.payload)) setPage(e.payload as Page);
    });
    return () => {
      void un.then((f) => f());
    };
  }, []);

  return (
    <div className="shell">
      <Nav page={page} onSelect={setPage} />
      <main className="page">
        {unreachable && <span className="badge warn">无法连接控制通道</span>}
        <ErrorBanner error={error ?? state?.status_error ?? null} appVersion={state?.app_version} />
        {state === null ? (
          <p className="muted">正在读取主机状态…</p>
        ) : page === "overview" ? (
          <Overview state={state} busy={busy} run={run} onInstalled={() => setPage("pairing")} />
        ) : page === "pairing" ? (
          <Pairing running={state.running} installed={state.install.kind === "installed"} />
        ) : page === "agents" ? (
          <Agents agents={state.status?.agents ?? []} busy={busy} run={run} />
        ) : (
          <Logs active={page === "logs"} />
        )}
      </main>
    </div>
  );
}
