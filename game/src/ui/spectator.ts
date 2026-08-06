// Spectator overlay: a detailed live card per realm (army, villagers, pop,
// resources, gathered, K/L, bases, and a military-strength bar). Drawing is
// pure so it can be screenshot-tested; the App layers click-to-follow on top of
// the card rects this returns.

import { ui } from "./ui";
import { PAL } from "../render/palette";
import { World } from "../sim/world";
import { Entity, Kind, Team } from "../sim/types";
import { UNITS } from "../content/units";
import { COMMANDERS } from "../content/commanders";

const AGE_NAMES = ["Dark", "Feudal", "Castle"];

/** Absolute per-realm colour (matches the in-world spectator colouring). */
function teamColor(t: number): string {
  return PAL.teams[t % PAL.teams.length].main;
}

export function teamLabel(t: Team): string {
  return PAL.teams[t]?.name ?? `Realm ${t + 1}`;
}

export interface SpectatorCardRect {
  team: number;
  x: number;
  y: number;
  w: number;
  h: number;
  /** A building to focus the camera on when this card is clicked (or null). */
  focus: Entity | null;
}

/**
 * Draws the spectator overlay and returns the card rectangles so the caller can
 * hit-test them for click-to-follow.
 */
export function drawSpectatorPanels(
  W: number,
  H: number,
  world: World,
  gameSpeed: number,
  paused: boolean,
): SpectatorCardRect[] {
  const ctx = ui.ctx;

  // Aggregate live counts + a rough military value per realm.
  type Agg = { army: number; vills: number; bld: number; milValue: number; firstBld: Entity | null };
  const a: Agg[] = Array.from({ length: world.numTeams }, () => ({
    army: 0, vills: 0, bld: 0, milValue: 0, firstBld: null,
  }));
  for (const e of world.entities) {
    if (!e.alive || e.team >= world.numTeams) continue;
    const g = a[e.team];
    if (e.kind === Kind.Building) { g.bld++; if (!g.firstBld) g.firstBld = e; }
    else if (e.kind === Kind.Unit) {
      if (e.type === "villager") g.vills++;
      else {
        g.army++;
        const c = UNITS[e.type]?.cost;
        if (c) g.milValue += (c.food ?? 0) + (c.wood ?? 0) + (c.gold ?? 0);
      }
    }
  }
  const maxMil = Math.max(1, ...a.map((g) => g.milValue));

  // Top-bar spectator label (the player resource block is hidden in watch mode).
  ui.text("👁 SPECTATING", 14, 17, { size: 14, bold: true, color: PAL.uiAccent });
  ui.text(paused ? "❚❚ Paused" : `▶ ${gameSpeed}× speed`, 150, 17, {
    size: 13, color: paused ? PAL.uiBad : "#d8cdaf",
  });
  ui.text("Space pause · +/- speed · click a realm to follow · Esc to leave", 300, 17, {
    size: 12, color: "#9c9379",
  });

  // Up to 4 realms stack in one right-hand column; 5–8 split into two columns.
  const cardW = 250;
  const cardH = 116;
  const gap = 8;
  const cols = world.numTeams > 4 ? 2 : 1;
  const perCol = Math.ceil(world.numTeams / cols);
  const rects: SpectatorCardRect[] = [];

  for (let t = 0; t < world.numTeams; t++) {
    const colIdx = Math.floor(t / perCol); // 0 = rightmost column
    const rowIdx = t % perCol;
    const cx = W - cardW - 12 - colIdx * (cardW + 12);
    const cy = 44 + rowIdx * (cardH + gap);
    const p = world.player(t as Team);
    const g = a[t];
    const dead = p.defeated;
    const col = teamColor(t);
    const dim = dead ? "#7a7060" : "#d8cdaf";

    ui.panel(cx, cy, cardW, cardH, { light: true });

    // Header: colour swatch + name + age.
    ctx.fillStyle = dead ? "#555049" : col;
    ctx.fillRect(cx + 12, cy + 10, 12, 12);
    ui.text(teamLabel(t as Team) + (dead ? "  ☠" : ""), cx + 32, cy + 17, {
      size: 14, bold: true, color: dead ? "#8a8070" : col,
    });
    ui.text(dead ? "DEFEATED" : AGE_NAMES[p.age] ?? "—", cx + cardW - 12, cy + 17, {
      align: "right", size: 12, bold: true, color: dead ? PAL.uiBad : PAL.uiAccent,
    });
    const cmdr = COMMANDERS[p.commander]?.name;
    if (cmdr) ui.text(cmdr, cx + 32, cy + 33, { size: 11, color: "#9c9379" });

    // Population row.
    ui.text(`⚔ ${g.army}`, cx + 14, cy + 52, { size: 13, color: dim, bold: true });
    ui.text(`👤 ${g.vills}`, cx + 90, cy + 52, { size: 13, color: dim });
    ui.text(`Pop ${p.popUsed}/${p.popCap}`, cx + 160, cy + 52, { size: 12, color: dim });

    // Resource stockpile.
    ui.text(`🍖${Math.floor(p.resources.food)}`, cx + 14, cy + 70, { size: 12, color: "#e89a5a" });
    ui.text(`🪵${Math.floor(p.resources.wood)}`, cx + 100, cy + 70, { size: 12, color: "#b08a52" });
    ui.text(`🪙${Math.floor(p.resources.gold)}`, cx + 186, cy + 70, { size: 12, color: PAL.goldVein });

    // Economy + combat record.
    ui.text(`⛏ ${Math.floor(p.stats.gathered)} gathered`, cx + 14, cy + 87, { size: 11, color: "#9c9379" });
    ui.text(`K ${p.stats.unitsKilled} / L ${p.stats.unitsLost}   🏠${g.bld}`, cx + cardW - 14, cy + 87, {
      align: "right", size: 11, color: "#9c9379",
    });

    // Military-strength bar (relative to the strongest realm).
    const barW = cardW - 28;
    const frac = g.milValue / maxMil;
    ctx.fillStyle = "rgba(0,0,0,0.35)";
    ctx.fillRect(cx + 14, cy + 98, barW, 8);
    ctx.fillStyle = dead ? "#555049" : col;
    ctx.fillRect(cx + 14, cy + 98, barW * frac, 8);

    rects.push({ team: t, x: cx, y: cy, w: cardW, h: cardH, focus: g.firstBld });
  }
  return rects;
}
