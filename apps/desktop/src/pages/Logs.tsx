import { useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { host } from "../lib/host";

const MAX_LINES = 2000;

export function Logs({ active }: { active: boolean }) {
  const [lines, setLines] = useState<string[]>([]);
  const [filter, setFilter] = useState("");
  const [follow, setFollow] = useState(false);
  const box = useRef<HTMLPreElement>(null);

  useEffect(() => {
    if (!active) return;
    host
      .logsTail(500)
      .then(setLines)
      .catch(() => setLines(["(无法读取日志)"]));
  }, [active]);

  useEffect(() => {
    if (!active || !follow) return;
    let un: (() => void) | undefined;
    let cancelled = false;
    void listen<{ line: string }>("log-line", (e) => {
      setLines((prev) => [...prev, e.payload.line].slice(-MAX_LINES));
    }).then((f) => {
      if (cancelled) f();
      else un = f;
    });
    void host.logsFollowStart();
    return () => {
      cancelled = true;
      un?.();
      void host.logsFollowStop();
    };
  }, [active, follow]);

  useEffect(() => {
    if (follow && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [lines, follow]);

  const shown = filter ? lines.filter((l) => l.includes(filter)) : lines;

  return (
    <>
      <div className="row">
        <input placeholder="过滤" value={filter} onChange={(e) => setFilter(e.target.value)} />
        <label className="row">
          <input type="checkbox" aria-label="跟随" checked={follow} onChange={(e) => setFollow(e.target.checked)} />
          跟随
        </label>
        <button onClick={() => void host.revealPath("logs").catch(() => {})}>打开日志目录</button>
      </div>
      <pre className="log" ref={box}>
        {shown.join("\n")}
      </pre>
    </>
  );
}
