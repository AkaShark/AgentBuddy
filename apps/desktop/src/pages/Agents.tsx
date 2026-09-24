import { useCallback, useEffect, useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { host, describeError, type AgentInfo, type AgentSettings } from "../lib/host";

interface Props {
  agents: AgentInfo[];
  busy: boolean;
  run: (fn: () => Promise<void>) => Promise<void>;
}

function SettingInput({ label, value, busy, save, numeric = false }: {
  label: string; value: string; busy: boolean; save: (value: string) => Promise<void>; numeric?: boolean;
}) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  return <input aria-label={label} value={draft} disabled={busy}
    type={numeric ? "number" : "text"} min={numeric ? 1 : undefined} max={numeric ? 65535 : undefined}
    onChange={(e) => setDraft(e.target.value)}
    onBlur={(e) => {
      if (draft.trim() && draft !== value && e.currentTarget.checkValidity()) void save(draft.trim());
    }} onKeyDown={(e) => { if (e.key === "Enter") e.currentTarget.blur(); }} />;
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
                  <SettingInput label={`${a.display_name} 路径`} value={s?.bin ?? a.name} busy={busy || !s}
                    save={(path) => change(() => host.setAgentBin(a.name, path))} />
                  <button disabled={busy} onClick={() => void pick(a)}>
                    选择…
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {agents.some((a) => a.name === "codex") && <details>
        <summary>Codex 高级设置</summary>
        <p className="muted">留空保留当前配置；输入后离开输入框即可保存。</p>
        <label>host <SettingInput label="Codex host" value={settings.codex?.host ?? ""} busy={busy || !settings.codex}
          save={(value) => change(() => host.setCodexEndpoint(value, null))} /></label>
        <label>port <SettingInput label="Codex port" value={settings.codex?.port?.toString() ?? ""} busy={busy || !settings.codex} numeric
          save={(value) => change(() => host.setCodexEndpoint(null, Number(value)))} /></label>
      </details>}
    </>
  );
}
