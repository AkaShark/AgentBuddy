import { useCallback, useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import { host, describeError, type PairPayload } from "../lib/host";

export function Pairing({ running }: { running: boolean }) {
  const [payload, setPayload] = useState<PairPayload | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const load = useCallback(async () => {
    try {
      setPayload(await host.pairPayload());
      setError(null);
    } catch (e) {
      setError(describeError(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const rotate = async () => {
    setConfirming(false);
    try {
      await host.rotateToken();
      await load();
    } catch (e) {
      setError(describeError(e));
    }
  };

  return (
    <>
      {!running && <p className="banner">请先启动主机服务，手机扫码后才能连上。</p>}
      {error && <p className="banner">{error}</p>}
      {payload && (
        <div className="row" style={{ alignItems: "flex-start", gap: 24 }}>
          <div className="card" style={{ background: "#000" }}>
            <QRCodeSVG value={payload.raw} size={240} bgColor="#000000" fgColor="#00ff9c" level="M" />
          </div>
          <div className="kv">
            <span className="muted">主机名</span>
            <span>{payload.host_name ?? "—"}</span>
            <span className="muted">节点 id</span>
            <code>{payload.node_id.slice(0, 12)}…</code>
            <span className="muted">token 指纹</span>
            <code>
              {payload.token.slice(0, 6)}…{payload.token.slice(-4)}
            </code>
            <span className="muted">操作</span>
            <span className="row">
              <button onClick={() => void writeText(payload.raw)}>复制 payload</button>
              <button className="danger" onClick={() => setConfirming(true)}>
                轮换 token
              </button>
            </span>
          </div>
        </div>
      )}
      {confirming && (
        <div className="card">
          <p>轮换后所有已配对的手机都需要重新扫码。继续？</p>
          <div className="row">
            <button className="danger" onClick={() => void rotate()}>
              继续轮换
            </button>
            <button onClick={() => setConfirming(false)}>取消</button>
          </div>
        </div>
      )}
      <p className="muted">手机 App → 添加服务器 → 扫码。</p>
    </>
  );
}
