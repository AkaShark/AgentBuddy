import { useCallback, useEffect, useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { host, describeError, type AgentInfo, type AgentSettings } from "../lib/host";

interface Props {
  agents: AgentInfo[];
  busy: boolean;
  run: (fn: () => Promise<void>) => Promise<void>;
}

/** `available` comes from the daemon (binary found); `enabled` and the
 *  configured binary come from host.toml via agent_settings. */
export function Agents({ agents, busy, run }: Props) {
  const [settings, setSettings] = useState<Record<string, AgentSettings>>({});
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setSettings(await host.agentSettings());
      setError(null);
    } catch (e) {
      setError(describeError(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const change = async (fn: () => Promise<void>) => {
    await run(fn);
    await load();
  };

  const pick = async (a: AgentInfo) => {
    const chosen = await open({ multiple: false, directory: false, title: `选择 ${a.display_name} 可执行文件` });
    if (typeof chosen === "string") {
      await change(() => host.setAgentBin(a.name, chosen));
    }
  };

  return (
    <>
      {error && <p className="banner">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Agent</th>
            <th>可执行文件</th>
            <th>启用</th>
            <th>路径</th>
          </tr>
        </thead>
        <tbody>
          {agents.map((a) => {
            const s = settings[a.name];
            return (
              <tr key={a.name}>
                <td>{a.display_name}</td>
                <td>{a.available ? <span className="badge ok">可用</span> : <span className="badge warn">未找到</span>}</td>
                <td>
                  <input
                    type="checkbox"
                    aria-label={`启用 ${a.display_name}`}
                    disabled={busy}
                    checked={s?.enabled ?? true}
                    onChange={(e) => {
                      const next = e.target.checked;
                      void change(() => host.setAgentEnabled(a.name, next));
                    }}
                  />
                </td>
                <td className="row">
                  <code>{s?.bin ?? a.name}</code>
                  <button disabled={busy} onClick={() => void pick(a)}>
                    选择…
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </>
  );
}
