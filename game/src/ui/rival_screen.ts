// The Nemesis screen: your rival, what it knows about you, and the button to
// fight it. See meta/rival.ts for how it learns.

import { ui } from "./ui";
import { withAlpha } from "../render/palette";
import { drawUnit, setFactionResolver } from "../render/draw";
import { makeEntity } from "../sim/world";
import { Kind, Team } from "../sim/types";
import { UNITS } from "../content/units";
import { FACTIONS } from "../content/factions";
import { DIFFICULTIES } from "../ai/difficulty";
import { careerLog } from "../meta/career";
import { RANK_DIFFICULTY, RANK_NAMES, RivalState, loadRival, memoryOf, taunt } from "../meta/rival";
import { fitText } from "./career_screen";

const GOLD = "#e8c060", TEXT = "#e7ddc4", DIM = "#a89f88", FAINT = "#6f6a5c", BLOOD = "#c8483e";

export class RivalScreen {
  private st: RivalState | null = null;

  /** Re-read on open, so a match just played shows. */
  open(playerFaction: string) { this.st = loadRival(playerFaction); }

  draw(W: number, H: number, time: number, playerFaction: string): "back" | "fight" | null {
    if (!this.st) this.open(playerFaction);
    const st = this.st!;
    const r = st.current;
    const ctx = ui.ctx;
    const bg = ctx.createRadialGradient(W / 2, H * 0.3, 40, W / 2, H * 0.4, Math.max(W, H));
    bg.addColorStop(0, "#2a130f"); bg.addColorStop(1, "#0c0706");
    ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);

    const outer = Math.min(W - 32, 1100);
    const x0 = Math.round(W / 2 - outer / 2);
    let action: "back" | "fight" | null = null;
    ui.text("NEMESIS", x0, 44, { size: 13, bold: true, color: BLOOD });
    ui.text("A rival who remembers how you play", x0, 66, { size: 13, color: DIM });

    const career = careerLog();
    const mem = memoryOf(r, career);
    const fac = FACTIONS[r.faction as keyof typeof FACTIONS];

    // ---- the rival ----
    const narrow = outer < 760;
    const cardH = narrow ? 300 : 280;
    ui.panel(x0, 84, outer, cardH);
    const px = narrow ? x0 + outer - 90 : x0 + 130, py = 84 + cardH * 0.7;
    ctx.save();
    ctx.fillStyle = withAlpha(BLOOD, 0.14);
    ctx.beginPath(); ctx.arc(px, 84 + cardH / 2, narrow ? 70 : 110, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    ctx.save();
    ctx.beginPath(); ctx.rect(x0 + 2, 86, outer - 4, cardH - 4); ctx.clip();
    this.portrait(px, py, narrow ? 2.6 : 4.2, r.faction, time);
    ctx.restore();
    const tx = narrow ? x0 + 20 : x0 + 280;
    const tw = outer - (tx - x0) - (narrow ? 130 : 24);
    ui.text(`${RANK_NAMES[r.rank].toUpperCase()} · RANK ${r.rank} OF 5`, tx, 118, { size: 11.5, bold: true, color: BLOOD });
    fitText(`${r.name} ${r.epithet}`, tx, 158, tw, { size: 34, bold: true, color: "#ffe2c8", font: "Georgia, serif" });
    ui.text(`${fac?.name ?? r.faction} · plays at ${DIFFICULTIES[RANK_DIFFICULTY[r.rank]]?.name ?? "Knight"} strength`, tx, 184, { size: 13, color: fac?.color ?? DIM });
    // Rank pips.
    for (let i = 0; i < 5; i++) {
      ctx.fillStyle = i < r.rank ? BLOOD : "rgba(255,255,255,0.1)";
      ctx.beginPath(); ctx.moveTo(tx + i * 22 + 8, 196); ctx.lineTo(tx + i * 22 + 16, 204); ctx.lineTo(tx + i * 22 + 8, 212); ctx.lineTo(tx + i * 22, 204); ctx.closePath(); ctx.fill();
    }
    ui.text(`Beat you ${r.beatYou}× · lost to you ${r.lostToYou}× · ${3 - r.lostToYou} more defeat${r.lostToYou === 2 ? "" : "s"} ends them`, tx + 124, 209, { size: 12, color: DIM });
    ui.text(r.origin, tx, 236, { size: 12, color: FAINT });
    // The taunt.
    ctx.fillStyle = "rgba(200,72,62,0.12)";
    ctx.fillRect(tx, 250, tw, narrow ? 80 : 60);
    ctx.fillStyle = BLOOD; ctx.fillRect(tx, 250, 3, narrow ? 80 : 60);
    this.wrap(`“${taunt(r, mem)}”`, tx + 14, 272, tw - 24, 14, "#ffd9c8", "italic 14px Georgia, serif");

    let y = 84 + cardH + 14;
    // ---- what it knows ----
    const colW = narrow ? outer : (outer - 14) / 2;
    const knowH = 190;
    ui.panel(x0, y, colW, knowH);
    ui.text("WHAT IT KNOWS ABOUT YOU", x0 + 16, y + 24, { size: 11, bold: true, color: GOLD });
    const rows: [string, string | null, string][] = [
      ["Your army", mem.armyWord ? `Mostly ${mem.armyWord} — it brings the counter` : null, "Learned after one battle"],
      ["Your timing", mem.timing === "early" ? "You attack early — it walls up first" : mem.timing === "late" ? "You attack late — it will rush you" : mem.timing === "mid" ? "You hit mid-game — it out-builds you" : null, "Learned after two battles"],
      ["How you win", mem.winsBy || null, "Learned after three battles"],
      ["Its grudge", r.grudge ? `Your ${r.grudge === "archer" ? "archers" : r.grudge} scarred it — it brings their answer` : null, "Scar it to find out"],
    ];
    rows.forEach(([k, v, lock], i) => {
      const ry = y + 52 + i * 34;
      ui.text(k, x0 + 16, ry, { size: 12, color: DIM });
      fitText(v ?? `??? · ${lock}`, x0 + 120, ry, colW - 136, { size: 13, bold: !!v, color: v ? TEXT : FAINT });
    });
    // ---- scars and history ----
    const hx = narrow ? x0 : x0 + colW + 14, hy = narrow ? y + knowH + 14 : y;
    ui.panel(hx, hy, colW, knowH);
    ui.text("SCARS & BATTLES", hx + 16, hy + 24, { size: 11, bold: true, color: GOLD });
    let ly = hy + 50;
    if (!r.scars.length && !r.history.length) ui.text("You have never met. Yet.", hx + 16, ly, { size: 12.5, color: FAINT });
    for (const s of r.scars.slice(-2)) { ui.text("×", hx + 16, ly, { size: 15, bold: true, color: BLOOD }); fitText(s, hx + 34, ly, colW - 50, { size: 12.5, color: TEXT }); ly += 22; }
    for (const h of r.history.slice(-4).reverse()) {
      ui.text(h.rivalWon ? "▲" : "▼", hx + 16, ly, { size: 11, color: h.rivalWon ? BLOOD : "#8fd07a" });
      fitText(`${h.note} · ${new Date(h.at).toLocaleDateString()}`, hx + 34, ly, colW - 50, { size: 12.5, color: DIM });
      ly += 22;
    }
    y = (narrow ? hy : y) + knowH + 14;

    // ---- the fallen ----
    if (st.fallen.length && y + 60 < H - 70) {
      ui.text("FALLEN RIVALS", x0, y + 12, { size: 11, bold: true, color: DIM });
      fitText(st.fallen.slice(0, 5).map((f) => `${f.name} ${f.epithet} (${RANK_NAMES[f.rank]})`).join("  ·  "), x0, y + 34, outer, { size: 12.5, color: TEXT });
      y += 52;
    }
    // ---- how it works, while there's room ----
    if (y + 120 < H - 76) {
      ui.panel(x0, y, outer, 104);
      ui.text("HOW A NEMESIS WORKS", x0 + 16, y + 24, { size: 11, bold: true, color: DIM });
      const how = [
        "It learns a layer each time you meet — your army, then your timing, then how you win — and plays the counter.",
        "Beat it and it flees with a scar and a grudge against what did it. Three defeats end it; one of its captains rises to replace it.",
        "Lose and it climbs a rank, plays stronger, and earns a name for how it beat you.",
      ];
      how.forEach((l, i) => fitText(`•  ${l}`, x0 + 16, y + 48 + i * 20, outer - 32, { size: 12.5, color: TEXT }));
    }

    // ---- actions ----
    const by = H - 62;
    if (ui.button("Back", x0, by, 130, 42, { size: 15 })) action = "back";
    if (ui.button(`Fight ${r.name}`, x0 + outer - 240, by, 240, 42, { accent: true, size: 16, tooltip: ["A 1 v 1 against your rival", "Beat them: renown, and they carry a scar. Three defeats ends them.", "Lose: they rise a rank — and learn."] })) action = "fight";
    ui.text(`Win: +${60 * r.rank} renown${r.lostToYou === 2 ? " (+250 if it's the last)" : ""}`, x0 + outer - 250, by + 26, { size: 12, color: GOLD, align: "right" });
    return action;
  }

  private portrait(x: number, y: number, s: number, faction: string, time: number) {
    const def = UNITS.hero ?? UNITS.knight;
    const e = makeEntity();
    Object.assign(e, { kind: Kind.Unit, type: UNITS.hero ? "hero" : "knight", team: Team.Enemy, x, y, radius: def.radius, hp: def.hp, maxHp: def.hp, facing: Math.PI - 0.4, attackInterval: def.attackInterval, animPhase: time * 0.3, seed: 9 });
    setFactionResolver(() => faction);
    const ctx = ui.ctx;
    ctx.save();
    ctx.translate(x, y); ctx.scale(s, s); ctx.translate(-x, -y);
    try { drawUnit(ctx, e, time * 0.3); } catch { /* a portrait is never worth a crash */ }
    ctx.restore();
    setFactionResolver(null);
  }

  private wrap(text: string, x: number, y: number, maxW: number, size: number, color: string, font: string) {
    const ctx = ui.ctx;
    ctx.save();
    ctx.font = font; ctx.fillStyle = color;
    let line = "", yy = y;
    for (const w of text.split(" ")) {
      const t = line ? `${line} ${w}` : w;
      if (ctx.measureText(t).width > maxW && line) { ctx.fillText(line, x, yy); line = w; yy += size + 6; } else line = t;
    }
    if (line) ctx.fillText(line, x, yy);
    ctx.restore();
  }
}
