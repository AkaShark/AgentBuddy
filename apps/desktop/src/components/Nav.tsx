import type { Page } from "../App";

const items: { id: Page; label: string }[] = [
  { id: "overview", label: "概览" },
  { id: "agents", label: "Agents" },
  { id: "pairing", label: "配对" },
  { id: "logs", label: "日志" },
];

export function Nav({ page, onSelect }: { page: Page; onSelect: (p: Page) => void }) {
  return (
    <nav className="nav">
      {items.map((it) => (
        <button key={it.id} className={it.id === page ? "active" : ""} onClick={() => onSelect(it.id)}>
          {it.label}
        </button>
      ))}
    </nav>
  );
}
