// In-match chat log + input line, drawn bottom-left over the world. Standalone so
// it can be rendered against a real canvas in tests. The App owns the data and
// the typing state; this just paints them.

import { ui } from "./ui";
import { teamLabel } from "./spectator";
import { Team } from "../sim/types";
import { PAL, teamColor, withAlpha } from "../render/palette";

export interface ChatLine { name: string; text: string; team: Team; t: number; }

const LINE_LIFE = 12; // seconds a chat line lingers
const MAX_SHOWN = 6;

export function drawChat(W: number, H: number, lines: ChatLine[], draft: string | null, now: number, baseY: number) {
  const ctx = ui.ctx;
  const x = 12;
  const lineH = 18;
  // Most-recent-first from the bottom up; only the last few, still-fresh lines.
  const fresh = lines.filter((l) => now - l.t < LINE_LIFE || draft !== null).slice(-MAX_SHOWN);
  let y = baseY - (draft !== null ? lineH : 0);

  if (draft !== null) {
    ctx.fillStyle = "rgba(8,6,3,0.8)";
    ctx.fillRect(x - 4, y - 13, 420, 19);
    ui.text("Say:", x, y, { size: 13, color: PAL.uiAccent, bold: true });
    ui.text(draft + "▍", x + 38, y, { size: 13, color: "#f3e9d2" });
  }
  for (let i = fresh.length - 1; i >= 0; i--) {
    y -= lineH;
    const l = fresh[i];
    const fade = draft !== null ? 1 : Math.max(0.15, 1 - (now - l.t) / LINE_LIFE);
    const tag = teamColor(l.team).main;
    ctx.fillStyle = withAlpha("#000000", 0.32 * fade);
    ctx.fillRect(x - 4, y - 13, Math.min(560, 12 + (l.name.length + l.text.length) * 7.2), 18);
    ui.text(`${l.name}:`, x, y, { size: 12, color: withAlpha(tag, fade), bold: true });
    ui.text(l.text, x + 8 + l.name.length * 7.4 + 6, y, { size: 12, color: withAlpha("#e7ddc4", fade) });
  }
}

/** Label for a ping/chat author by team, e.g. "Crimson". */
export function authorLabel(team: Team): string {
  return teamLabel(team);
}
