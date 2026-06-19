// Live multi-team scoreboard overlay (toggled with Tab). Standalone + draws
// through the already-begun `ui` context, so it's testable against a real canvas.

import { ui } from "./ui";
import { teamLabel } from "./spectator";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { teamMetrics } from "../sim/metrics";
import { PAL, teamColor, withAlpha } from "../render/palette";

const AGE_NAMES = ["Dark", "Feudal", "Castle", "Imperial"];

export function drawScoreboard(W: number, H: number, world: World, me: Team) {
  const rows = [];
  for (let t = 0; t < world.numTeams; t++) rows.push(teamMetrics(world, t as Team));
  rows.sort((a, b) => b.score - a.score);

  const ctx = ui.ctx;
  const cols: [string, number][] = [
    ["Realm", 188], ["Score", 70], ["Army", 64], ["Vils", 50], ["Kills", 56], ["Razed", 56], ["Age", 70],
  ];
  const pad = 16;
  const rowH = 26;
  const w = cols.reduce((s, c) => s + c[1], 0) + pad * 2;
  const h = 44 + rows.length * rowH + 14;
  const x = (W - w) / 2;
  const y = 70;

  ctx.fillStyle = "rgba(10, 8, 4, 0.9)";
  ctx.fillRect(x, y, w, h);
  ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.5);
  ctx.strokeRect(x + 0.5, y + 0.5, w, h);
  ui.text("Scoreboard", x + w / 2, y + 24, { align: "center", size: 18, bold: true, color: PAL.uiAccent });

  let cx = x + pad;
  const headY = y + 44;
  for (const [label, cw] of cols) {
    ui.text(label, label === "Realm" ? cx : cx + cw - 8, headY, {
      size: 11, color: "#9a917b", align: label === "Realm" ? "left" : "right",
    });
    cx += cw;
  }

  let ry = headY + 8;
  for (const m of rows) {
    ry += rowH;
    const ally = world.areAllied(me, m.team);
    if (m.team === me) {
      ctx.fillStyle = withAlpha(PAL.uiAccent, 0.12);
      ctx.fillRect(x + 4, ry - rowH + 6, w - 8, rowH);
    }
    const tc = teamColor(m.team).main;
    const dim = m.defeated ? 0.45 : 1;
    cx = x + pad;
    ctx.fillStyle = withAlpha(tc, dim);
    ctx.fillRect(cx, ry - 11, 12, 12);
    const tag = m.team === me ? " (You)" : ally ? " (Ally)" : "";
    const name = teamLabel(m.team) + tag + (m.defeated ? "  ☠" : "");
    ui.text(name, cx + 18, ry, { size: 12, color: m.defeated ? "#9a917b" : "#e7ddc4", bold: m.team === me });
    cx += cols[0][1];
    const vals = [m.score, m.military, m.villagers, m.killed, m.razed];
    for (let i = 0; i < vals.length; i++) {
      ui.text(String(vals[i]), cx + cols[i + 1][1] - 8, ry, { size: 12, align: "right", color: withAlpha("#e7ddc4", dim) });
      cx += cols[i + 1][1];
    }
    ui.text(AGE_NAMES[m.age] ?? "—", cx + cols[6][1] - 8, ry, { size: 12, align: "right", color: withAlpha("#cabfa4", dim) });
  }
  ui.text("Tab to close", x + w / 2, y + h - 6, { align: "center", size: 10, color: "#6f6a5c" });
}
