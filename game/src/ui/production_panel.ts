// Production overview (toggle with V): every production building at a glance —
// what it's training, how deep the queue is and current progress. Returns the id
// of a clicked row so the caller can select + jump to that building.

import { ui } from "./ui";
import { World } from "../sim/world";
import { Entity, EntityId, Kind, Team } from "../sim/types";
import { BUILDINGS } from "../content/buildings";
import { UNITS } from "../content/units";
import { UPGRADES } from "../content/tech";
import { PAL, withAlpha } from "../render/palette";

function itemLabel(item: string): string {
  if (item.startsWith("u:")) return UNITS[item.slice(2)]?.name ?? item.slice(2);
  if (item.startsWith("t:")) return UPGRADES[item.slice(2)]?.name ?? item.slice(2);
  if (item === "a:age") return "Advance Age";
  return item;
}

export function drawProductionPanel(W: number, H: number, world: World, me: Team): EntityId | null {
  const p = world.player(me);
  const prod: Entity[] = [];
  for (const e of world.entities) {
    if (!e.alive || e.team !== me || e.kind !== Kind.Building) continue;
    if ((BUILDINGS[e.type]?.trains?.length ?? 0) > 0 || e.productionQueue.length > 0) prod.push(e);
  }
  // Active producers first, then grouped by type for a stable order.
  prod.sort((a, b) =>
    (b.productionQueue.length > 0 ? 1 : 0) - (a.productionQueue.length > 0 ? 1 : 0) ||
    a.type.localeCompare(b.type) || a.id - b.id);

  const w = 250;
  const rowH = 30;
  const headH = 30;
  const x = 12;
  const maxRows = Math.max(1, Math.floor((H * 0.52) / rowH));
  const rows = prod.slice(0, maxRows);
  const h = headH + Math.max(1, rows.length) * rowH + 8;
  const y = Math.max(70, H / 2 - h / 2);
  const ctx = ui.ctx;

  ctx.fillStyle = "rgba(10,8,4,0.88)";
  ctx.fillRect(x, y, w, h);
  ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.5);
  ctx.strokeRect(x + 0.5, y + 0.5, w, h);
  ui.text("Production", x + 12, y + 20, { size: 14, bold: true, color: PAL.uiAccent });
  const active = prod.filter((e) => e.productionQueue.length > 0).length;
  ui.text(`${active} active`, x + w - 12, y + 20, { size: 11, align: "right", color: "#9a917b" });

  if (rows.length === 0) {
    ui.text("No production buildings.", x + 12, y + headH + 18, { size: 12, color: "#9a917b" });
    return null;
  }

  let clicked: EntityId | null = null;
  let ry = y + headH;
  for (const e of rows) {
    const hover = ui.mx >= x && ui.mx <= x + w && ui.my >= ry && ui.my <= ry + rowH;
    if (hover) {
      ctx.fillStyle = withAlpha(PAL.uiAccent, 0.12);
      ctx.fillRect(x + 2, ry, w - 4, rowH);
      if (ui.clicked && !ui.pointerConsumed) { ui.pointerConsumed = true; clicked = e.id; }
    }
    ui.text(BUILDINGS[e.type]?.name ?? e.type, x + 12, ry + 13, { size: 12, color: "#e7ddc4" });
    const item = e.productionQueue[0];
    if (item) {
      const total = world.itemTime(item, p.age) * (item.startsWith("u:") ? p.boon.trainSpeedMult : 1);
      const prog = total > 0 ? Math.max(0, Math.min(1, 1 - e.productionTime / total)) : 0;
      const qn = e.productionQueue.length;
      ui.text(itemLabel(item) + (qn > 1 ? ` ×${qn}` : ""), x + 12, ry + 26, { size: 10, color: "#cabfa4" });
      const bw = 70;
      const bx = x + w - bw - 12;
      ctx.fillStyle = "rgba(0,0,0,0.35)"; ctx.fillRect(bx, ry + 17, bw, 7);
      ctx.fillStyle = PAL.uiAccent; ctx.fillRect(bx, ry + 17, bw * prog, 7);
    } else {
      ui.text("idle", x + 12, ry + 26, { size: 10, color: "#6f6a5c" });
    }
    ry += rowH;
  }
  ui.text("V to close", x + w / 2, y + h - 5, { align: "center", size: 9, color: "#6f6a5c" });
  return clicked;
}
