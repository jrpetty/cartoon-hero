// The Codex — in-game tech tree & encyclopedia. Three tabs: Units, Buildings,
// and Ages & Tech. Unit/building art is drawn live with the real draw
// functions on display-only entities, so the codex always matches the game.

import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { ABILITIES } from "../content/abilities";
import { AGES, UPGRADES } from "../content/tech";
import { TILE } from "../content/balance";
import { BuildState, Entity, Kind, Team } from "../sim/types";
import { makeEntity } from "../sim/world";
import { drawUnit, drawBuilding } from "../render/draw";
import { PAL, shade, withAlpha } from "../render/palette";
import { ui } from "./ui";
import { drawMenuBackground } from "./screens";

import type { Profile } from "../meta/profile";
import { MatchRecord, listHistory, summariseHistory } from "../meta/history";
import { REPORT_TABS, ReportTab, drawReportTab } from "./match_report";
import { ACHIEVEMENTS, challengesForWeek, weekIndex } from "../meta/achievements";

type Tab = "units" | "buildings" | "tech" | "records";

/** Seconds as m:ss — match lengths read as durations, not as numbers. */
const mmssOf = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;

const CLASS_LABEL: Record<string, string> = {
  infantry: "Infantry",
  archer: "Ranged",
  cavalry: "Cavalry",
  siege: "Siege",
  villager: "Villager",
  building: "Building",
};

function fmtCost(c: { food: number; wood: number; gold: number }): string {
  const parts: string[] = [];
  if (c.food) parts.push(`🍖${c.food}`);
  if (c.wood) parts.push(`🪵${c.wood}`);
  if (c.gold) parts.push(`🪙${c.gold}`);
  return parts.length ? parts.join("  ") : "Free";
}

/** Tiny word-wrapper (screens.ts keeps its own private). */
function wrap(text: string, x: number, y: number, maxW: number, size: number, color: string): number {
  const ctx = ui.ctx;
  ctx.font = `${size}px "Trebuchet MS", sans-serif`;
  ctx.fillStyle = color;
  ctx.textAlign = "left";
  const words = text.split(" ");
  let line = "";
  let yy = y;
  for (const w of words) {
    const probe = line ? line + " " + w : w;
    if (ctx.measureText(probe).width > maxW && line) {
      ctx.fillText(line, x, yy);
      yy += size + 4;
      line = w;
    } else {
      line = probe;
    }
  }
  if (line) ctx.fillText(line, x, yy);
  return yy + size + 4;
}

export class CodexScreen {
  private tab: Tab = "units";
  private selUnit = "militia";
  private selBuilding = "town_center";
  /** Display-only entities for live art, keyed by def id. */
  private models = new Map<string, Entity>();

  private model(id: string, kind: Kind): Entity {
    let e = this.models.get(id);
    if (e) return e;
    e = makeEntity();
    e.kind = kind;
    e.team = Team.Player;
    e.type = id;
    if (kind === Kind.Unit) {
      const def = UNITS[id];
      e.radius = def.radius;
      e.attackInterval = def.attackInterval;
      e.maxHp = e.hp = def.hp;
      e.facing = -Math.PI / 6;
      if (def.hero) e.heroLevel = 3; // show the pips
    } else {
      const def = BUILDINGS[id];
      e.radius = (def.tiles * TILE) / 2;
      e.maxHp = e.hp = def.hp;
      e.buildState = BuildState.Done;
    }
    this.models.set(id, e);
    return e;
  }

  /** Pedestal + live art, scaled to fit the box. */
  private drawModel(id: string, kind: Kind, cx: number, cy: number, box: number, time: number) {
    const ctx = ui.ctx;
    const e = this.model(id, kind);
    e.x = cx;
    e.y = cy;
    e.animPhase = time;
    // grass pedestal
    const g = ctx.createRadialGradient(cx, cy + box * 0.18, 4, cx, cy + box * 0.18, box * 0.52);
    g.addColorStop(0, shade(PAL.grass, 0.06));
    g.addColorStop(1, shade(PAL.grassDark, -0.12));
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.ellipse(cx, cy + box * 0.18, box * 0.5, box * 0.26, 0, 0, Math.PI * 2);
    ctx.fill();
    const scale = kind === Kind.Unit
      ? Math.max(2.2, Math.min(3.2, box / (e.radius * 6)))
      : Math.max(1.1, Math.min(3, (box * 0.42) / e.radius));
    ctx.save();
    ctx.translate(cx, cy);
    ctx.scale(scale, scale);
    ctx.translate(-cx, -cy);
    if (kind === Kind.Unit) drawUnit(ctx, e, time);
    else drawBuilding(ctx, e, time, Team.Player);
    ctx.restore();
  }

  private statRow(label: string, value: string, x: number, y: number, w: number) {
    ui.text(label, x, y, { size: 13, color: "#bdb49a" });
    ui.text(value, x + w, y, { size: 13, bold: true, align: "right" });
  }

  private chips(items: string[], x: number, y: number, maxW: number, color: string): number {
    const ctx = ui.ctx;
    let cx = x;
    let cy = y;
    ctx.font = "bold 12px sans-serif";
    for (const item of items) {
      const w = ctx.measureText(item).width + 16;
      if (cx + w > x + maxW) {
        cx = x;
        cy += 24;
      }
      ctx.fillStyle = withAlpha(color, 0.16);
      ctx.fillRect(cx, cy - 13, w, 19);
      ctx.strokeStyle = withAlpha(color, 0.55);
      ctx.lineWidth = 1;
      ctx.strokeRect(cx, cy - 13, w, 19);
      ctx.fillStyle = color;
      ctx.textAlign = "left";
      ctx.fillText(item, cx + 8, cy + 1);
      cx += w + 6;
    }
    return cy + 28;
  }

  draw(W: number, H: number, time: number, profile: Profile): "back" | null {
    drawMenuBackground(W, H, time);
    const ctx = ui.ctx;
    ctx.fillStyle = "rgba(10, 8, 4, 0.45)";
    ctx.fillRect(0, 0, W, H);

    ui.text("Codex — Tech Tree & Encyclopedia", W / 2, 46, {
      align: "center", size: 30, bold: true, color: "#ffe9b0", font: "Georgia, serif",
    });

    // Tabs.
    const tabs: [Tab, string][] = [
      ["units", "Units"], ["buildings", "Buildings"], ["tech", "Ages & Tech"], ["records", "Records"],
    ];
    const tw = 150;
    let tx = W / 2 - (tabs.length * (tw + 10) - 10) / 2;
    for (const [id, label] of tabs) {
      if (ui.button(label, tx, 70, tw, 36, { accent: this.tab === id, size: 15 })) this.tab = id;
      tx += tw + 10;
    }

    const top = 124;
    if (this.tab === "units") this.drawUnits(W, H, top, time);
    else if (this.tab === "buildings") this.drawBuildings(W, H, top, time);
    else if (this.tab === "records") this.drawRecords(W, H, top, profile);
    else this.drawTech(W, H, top);

    let action: "back" | null = null;
    if (ui.button("⟵ Back", 24, H - 68, 130, 44, { size: 15 })) action = "back";
    return action;
  }

  // ---------------------------------------------------------------- records --
  //
  // What the last twenty games looked like, and what is still unearned. Lives
  // in the Codex rather than on its own menu entry because it is the same kind
  // of thing as the rest of it — a reference you go and look at, not a mode you
  // enter.

  /** The history row currently opened back up as its full report, if any. */
  private openReport: MatchRecord | null = null;
  private reportTab: ReportTab = "overview";

  private drawRecords(W: number, H: number, top: number, profile: Profile) {
    const ctx = ui.ctx;
    // A report opened from the list takes over the tab. The summary row can
    // never say what actually went wrong in a game; the report can, and it was
    // being thrown away at Continue.
    if (this.openReport?.report) {
      const r = this.openReport;
      const colW = Math.min(1180, W - 60);
      const x0 = Math.round(W / 2 - colW / 2);
      ui.panel(x0, top, colW, H - top - 84, { light: true });
      const when = new Date(r.at);
      ui.text(`${r.won ? "Victory" : "Defeat"} — ${r.mapName}`, x0 + 20, top + 28,
        { size: 17, bold: true, color: r.won ? "#7df2a9" : "#e0786a" });
      ui.text(
        `${when.toLocaleDateString()} · ${r.mode} · ${r.players} players · ${r.difficulty} · ${mmssOf(r.durationSec)}`,
        x0 + 20, top + 48, { size: 11.5, color: "#9a917b" },
      );
      let tx = x0 + 20;
      for (const t of REPORT_TABS) {
        if (ui.button(t.label, tx, top + 62, 106, 26, { accent: this.reportTab === t.id, size: 12 })) {
          this.reportTab = t.id;
        }
        tx += 112;
      }
      if (ui.button("‹ Back to records", x0 + colW - 170, top + 62, 150, 26, { size: 12 })) {
        this.openReport = null;
      }
      ctx.fillStyle = "#7fb0e8"; ctx.fillRect(x0 + colW - 320, top + 30, 10, 10);
      ui.text("You", x0 + colW - 306, top + 36, { size: 11, color: "#cabfa4" });
      ctx.fillStyle = "#e0786a"; ctx.fillRect(x0 + colW - 250, top + 30, 10, 10);
      ui.text("Opponent", x0 + colW - 236, top + 36, { size: 11, color: "#cabfa4" });
      drawReportTab(this.reportTab, x0 + 26, top + 108, colW - 52, H - top - 210, r.report!);
      return;
    }
    const rows = listHistory();
    const sum = summariseHistory(rows);
    const colW = Math.min(1180, W - 60);
    const x0 = Math.round(W / 2 - colW / 2);
    const leftW = Math.round(colW * 0.54);
    const rightX = x0 + leftW + 20;
    const rightW = colW - leftW - 20;
    const panelH = H - top - 84;

    // ---- the last twenty ----
    ui.panel(x0, top, leftW, panelH, { light: true });
    ui.text("Last twenty matches", x0 + 18, top + 26, { size: 16, bold: true, color: PAL.uiAccent });
    if (!rows.length) {
      ui.text("Nothing recorded yet. Play a skirmish.", x0 + 18, top + 56, { size: 13, color: "#6f6a5c" });
    } else {
      // The summary line first: the whole point of keeping a record is the
      // trend, and a trend belongs above the rows it came from.
      ui.text(
        `${sum.won}W ${sum.played - sum.won}L  ·  ${Math.round(sum.winRate * 100)}%  ·  ${sum.killRatio.toFixed(2)} kills per loss  ·  best streak ${sum.bestStreak}`,
        x0 + 18, top + 46, { size: 11.5, color: "#cabfa4" },
      );
      ui.text(
        `Town Centre idle ${Math.round(sum.avgTcIdleShare * 100)}% of an average match  ·  typical length ${mmssOf(sum.avgDuration)}`,
        x0 + 18, top + 62, { size: 11.5, color: "#8f8770" },
      );
      let ry = top + 84;
      const rowH = 30;
      for (const r of rows) {
        if (ry + rowH > top + panelH - 8) break;
        ctx.fillStyle = r.won ? "rgba(90,150,90,0.14)" : "rgba(150,80,70,0.13)";
        ctx.beginPath(); ctx.roundRect(x0 + 12, ry, leftW - 24, rowH - 4, 4); ctx.fill();
        ctx.fillStyle = r.won ? "#7df2a9" : "#e0786a";
        ctx.fillRect(x0 + 12, ry, 3, rowH - 4);
        ui.text(r.won ? "WIN" : "LOSS", x0 + 24, ry + 17,
          { size: 11, bold: true, color: r.won ? "#7df2a9" : "#e0786a" });
        ui.text(r.mapName.slice(0, 22), x0 + 66, ry + 17, { size: 11.5, color: "#e7ddc4" });
        ui.text(`${r.difficulty} · ${r.players}p`, x0 + 220, ry + 17, { size: 10.5, color: "#8f8770" });
        ui.text(mmssOf(r.durationSec), x0 + 320, ry + 17, { size: 10.5, color: "#8f8770" });
        ui.text(`${r.unitsKilled}/${r.unitsLost}`, x0 + leftW - 92, ry + 17,
          { size: 10.5, align: "right", color: "#cabfa4" });
        // Only the newest few kept their full report — the rest are summaries,
        // so say so rather than offering a button that does nothing.
        if (r.report) {
          if (ui.button("Report", x0 + leftW - 84, ry + 2, 62, 22, { size: 10.5 })) {
            this.openReport = r;
            this.reportTab = "overview";
          }
        } else {
          ui.text("—", x0 + leftW - 53, ry + 17, { size: 11, align: "center", color: "#5a5548" });
        }
        ry += rowH;
      }
    }

    // ---- achievements and this week's challenges ----
    ui.panel(rightX, top, rightW, panelH, { light: true });
    const doneSet = new Set(profile.data.achievements);
    const weeklyDone = new Set(profile.data.challengesDone);
    const week = weekIndex(Date.now());
    ui.text("This week", rightX + 18, top + 26, { size: 16, bold: true, color: PAL.uiAccent });
    let ay = top + 52;
    for (const c of challengesForWeek(week)) {
      const got = weeklyDone.has(`${week}:${c.id}`);
      ui.text(got ? "◈" : "◇", rightX + 18, ay, { size: 13, bold: true, color: got ? "#7fd0ff" : "#5a5548" });
      ui.text(c.name, rightX + 38, ay, { size: 12.5, bold: true, color: got ? "#7fd0ff" : "#e7ddc4" });
      ui.text(`+${c.valor} ⚔`, rightX + rightW - 18, ay, {
        size: 11.5, align: "right", bold: true, color: got ? "#5a5548" : "#ffd24a",
      });
      ui.text(c.desc, rightX + 38, ay + 14, { size: 10.5, color: "#8f8770" });
      ay += 36;
    }

    ay += 10;
    ui.text(`Achievements  ${doneSet.size} / ${ACHIEVEMENTS.length}`, rightX + 18, ay,
      { size: 16, bold: true, color: PAL.uiAccent });
    ay += 24;
    for (const a of ACHIEVEMENTS) {
      if (ay + 30 > top + panelH - 8) break;
      const got = doneSet.has(a.id);
      ui.text(got ? "★" : "☆", rightX + 18, ay + 12,
        { size: 13, bold: true, color: got ? "#ffd24a" : "#5a5548" });
      ui.text(a.name, rightX + 38, ay + 12, { size: 12, bold: true, color: got ? "#ffe9b0" : "#9a917b" });
      ui.text(`+${a.valor} ⚔`, rightX + rightW - 18, ay + 12, {
        size: 11, align: "right", color: got ? "#5a5548" : "#ffd24a",
      });
      ui.text(a.desc, rightX + 38, ay + 25, { size: 10, color: got ? "#6f6a5c" : "#8f8770" });
      ay += 32;
    }
  }

  // ------------------------------------------------------------------ units --
  private drawUnits(W: number, H: number, top: number, time: number) {
    const ids = Object.keys(UNITS);
    const listW = 330;
    const x0 = Math.max(20, W / 2 - 470);
    ui.panel(x0, top, listW, H - top - 84);
    let ly = top + 12;
    for (const id of ids) {
      const def = UNITS[id];
      const sel = this.selUnit === id;
      if (ui.button("", x0 + 10, ly, listW - 20, 34, { accent: sel })) this.selUnit = id;
      ui.text(def.name, x0 + 22, ly + 17, { size: 14, bold: true, color: sel ? "#ffe9b0" : PAL.uiParchment });
      ui.text(
        `${CLASS_LABEL[def.armorClass] ?? def.armorClass} · ${AGES[def.age].name.split(" ")[0]}`,
        x0 + listW - 22, ly + 17, { size: 11, align: "right", color: "#bdb49a" },
      );
      ly += 38;
    }

    // Detail panel.
    const dx = x0 + listW + 16;
    const dw = Math.min(580, W - dx - 20);
    ui.panel(dx, top, dw, H - top - 84, { light: true });
    const def = UNITS[this.selUnit];
    if (!def) return;
    this.drawModel(this.selUnit, Kind.Unit, dx + 92, top + 86, 130, time);

    ui.text(def.name, dx + 180, top + 38, { size: 22, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });
    ui.text(
      `${CLASS_LABEL[def.armorClass] ?? def.armorClass} — ${AGES[def.age].name}  ·  ${fmtCost(def.cost)}  ·  ${def.pop} pop`,
      dx + 180, top + 60, { size: 13, color: "#d8cdb4" },
    );
    let yy = wrap(def.desc, dx + 180, top + 84, dw - 200, 13, "#bdb49a");

    // Stat grid (two columns).
    const colW = (dw - 60) / 2;
    const sx1 = dx + 24;
    const sx2 = dx + 40 + colW;
    let sy = top + 170;
    const dps = def.attack > 0 ? (def.attack / def.attackInterval).toFixed(1) : "—";
    this.statRow("Hit points", String(def.hp), sx1, sy, colW);
    this.statRow("Speed", String(def.speed), sx2, sy, colW);
    sy += 24;
    this.statRow("Attack", def.attack ? `${def.attack} (${dps}/s)` : "—", sx1, sy, colW);
    this.statRow("Armor", String(def.armor), sx2, sy, colW);
    sy += 24;
    this.statRow("Range", def.ranged ? String(def.range) : "Melee", sx1, sy, colW);
    this.statRow("Vision", String(def.visionRange), sx2, sy, colW);
    sy += 24;
    this.statRow("Train time", `${def.buildTime}s`, sx1, sy, colW);
    this.statRow("Trained at", BUILDINGS[def.trainedAt]?.name ?? def.trainedAt, sx2, sy, colW);
    sy += 34;

    // Counter web.
    const strong = Object.entries(def.bonus).map(
      ([cls, v]) => `+${v} vs ${CLASS_LABEL[cls] ?? cls}`,
    );
    if (strong.length) {
      ui.text("Bonus damage", sx1, sy, { size: 13, bold: true, color: PAL.uiGood });
      sy = this.chips(strong, sx1 + 110, sy, dw - 150, PAL.uiGood);
    }
    const counteredBy = Object.keys(UNITS).filter(
      (k) => k !== this.selUnit && (UNITS[k].bonus[def.armorClass] ?? 0) >= 4,
    ).map((k) => UNITS[k].name);
    if (counteredBy.length) {
      ui.text("Countered by", sx1, sy, { size: 13, bold: true, color: PAL.uiBad });
      sy = this.chips(counteredBy, sx1 + 110, sy, dw - 150, PAL.uiBad);
    }

    // Ability.
    const ab = ABILITIES[this.selUnit];
    if (ab) {
      sy += 4;
      ui.text(`★ ${ab.name}  (Q · ${ab.cooldown}s cooldown)`, sx1, sy, { size: 14, bold: true, color: "#ffd24a" });
      sy = wrap(ab.desc, sx1, sy + 22, dw - 60, 13, "#d8cdb4");
    }
  }

  // -------------------------------------------------------------- buildings --
  private drawBuildings(W: number, H: number, top: number, time: number) {
    const ids = Object.keys(BUILDINGS);
    const listW = 330;
    const x0 = Math.max(20, W / 2 - 470);
    const rowH = 30;
    ui.panel(x0, top, listW, H - top - 84);
    let ly = top + 10;
    for (const id of ids) {
      const def = BUILDINGS[id];
      const sel = this.selBuilding === id;
      if (ui.button("", x0 + 10, ly, listW - 20, rowH - 4, { accent: sel })) this.selBuilding = id;
      ui.text(def.name, x0 + 22, ly + 13, { size: 13, bold: true, color: sel ? "#ffe9b0" : PAL.uiParchment });
      ui.text(AGES[def.age].name.split(" ")[0], x0 + listW - 22, ly + 13, { size: 11, align: "right", color: "#bdb49a" });
      ly += rowH;
    }

    const dx = x0 + listW + 16;
    const dw = Math.min(580, W - dx - 20);
    ui.panel(dx, top, dw, H - top - 84, { light: true });
    const def = BUILDINGS[this.selBuilding];
    if (!def) return;
    this.drawModel(this.selBuilding, Kind.Building, dx + 100, top + 96, 168, time);

    ui.text(def.name, dx + 200, top + 38, { size: 22, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });
    ui.text(
      `${AGES[def.age].name}  ·  ${fmtCost(def.cost)}  ·  ${def.buildTime}s build`,
      dx + 200, top + 60, { size: 13, color: "#d8cdb4" },
    );
    let yy = wrap(def.desc, dx + 200, top + 84, dw - 220, 13, "#bdb49a");

    const colW = (dw - 60) / 2;
    const sx1 = dx + 24;
    const sx2 = dx + 40 + colW;
    let sy = top + 210;
    this.statRow("Hit points", String(def.hp), sx1, sy, colW);
    this.statRow("Footprint", `${def.tiles}×${def.tiles} tiles`, sx2, sy, colW);
    sy += 24;
    if (def.attack > 0) {
      this.statRow("Attack", `${def.attack} @ ${def.range} range`, sx1, sy, colW);
      this.statRow("Fire rate", `${def.attackInterval}s`, sx2, sy, colW);
      sy += 24;
    }
    if (def.popProvided) {
      this.statRow("Population", `+${def.popProvided}`, sx1, sy, colW);
      sy += 24;
    }
    if (def.garrisonCap) {
      this.statRow("Garrison", `${def.garrisonCap} units`, sx1, sy, colW);
      sy += 24;
    }
    if (def.isDropoff) {
      this.statRow("Drop-off", def.dropoffKinds.join(", "), sx1, sy, colW);
      sy += 24;
    }
    if (def.requires) {
      this.statRow("Requires", BUILDINGS[def.requires].name, sx1, sy, colW);
      sy += 24;
    }
    sy += 10;
    if (def.trains.length) {
      ui.text("Trains", sx1, sy, { size: 13, bold: true, color: PAL.uiAccent });
      sy = this.chips(def.trains.map((t) => UNITS[t]?.name ?? t), sx1 + 70, sy, dw - 110, PAL.uiAccent);
    }
    // Who can break it down fastest.
    const breakers = Object.keys(UNITS)
      .filter((k) => (UNITS[k].bonus["building"] ?? 0) >= 20)
      .map((k) => UNITS[k].name);
    if (breakers.length) {
      ui.text("Felled by", sx1, sy, { size: 13, bold: true, color: PAL.uiBad });
      this.chips(breakers, sx1 + 70, sy, dw - 110, PAL.uiBad);
    }
  }

  // ------------------------------------------------------------------- tech --
  private drawTech(W: number, H: number, top: number) {
    const x0 = Math.max(20, W / 2 - 470);
    const fullW = Math.min(940, W - x0 - 20);

    // Ages timeline.
    const aw = (fullW - 24 * 2) / 3;
    for (let i = 0; i < AGES.length; i++) {
      const age = AGES[i];
      const ax = x0 + i * (aw + 24);
      ui.panel(ax, top, aw, 196, { light: true });
      ui.text(age.name, ax + 16, top + 26, { size: 17, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });
      ui.text(
        i === 0 ? "Where every realm begins." : `${fmtCost(age.cost)} · ${age.advanceTime}s at the Town Center`,
        ax + 16, top + 48, { size: 12, color: "#bdb49a" },
      );
      if (age.requiresCount > 0) {
        const names = age.requiresAny.map((id) => BUILDINGS[id]?.name ?? id).join(", ");
        ui.text(`Requires: any ${age.requiresCount} of — ${names}`, ax + 16, top + 66, { size: 12, color: "#d8cdb4" });
      }
      const unlockU = Object.values(UNITS).filter((u) => u.age === i).map((u) => u.name);
      const unlockB = Object.values(BUILDINGS).filter((b) => b.age === i).map((b) => b.name);
      let uy = top + 92;
      ui.text("Unlocks", ax + 16, uy, { size: 12, bold: true, color: PAL.uiGood });
      uy = wrap([...unlockU, ...unlockB].join(", ") || "—", ax + 16, uy + 18, aw - 32, 11, "#d8cdb4");
      // chevron to the next age (drawn, so it renders everywhere)
      if (i < AGES.length - 1) {
        const cx = ax + aw + 12;
        const cy = top + 98;
        ui.ctx.strokeStyle = PAL.uiAccent;
        ui.ctx.lineWidth = 3;
        ui.ctx.beginPath();
        ui.ctx.moveTo(cx - 4, cy - 7);
        ui.ctx.lineTo(cx + 4, cy);
        ui.ctx.lineTo(cx - 4, cy + 7);
        ui.ctx.stroke();
      }
    }

    // Upgrades, grouped by age.
    const uy0 = top + 216;
    ui.panel(x0, uy0, fullW, H - uy0 - 84);
    ui.text("Blacksmith Research", x0 + 16, uy0 + 24, { size: 16, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });
    const ups = Object.values(UPGRADES);
    const cols = 3;
    const uw = (fullW - 32 - (cols - 1) * 14) / cols;
    ups.forEach((up, i) => {
      const ux = x0 + 16 + (i % cols) * (uw + 14);
      const uyy = uy0 + 44 + Math.floor(i / cols) * 78;
      ui.panel(ux, uyy, uw, 70, { light: true });
      ui.text(up.name, ux + 12, uyy + 18, { size: 13, bold: true });
      ui.text(
        `${AGES[up.age].name.split(" ")[0]} · ${fmtCost(up.cost)} · ${up.time}s`,
        ux + 12, uyy + 36, { size: 11, color: "#bdb49a" },
      );
      wrap(up.desc, ux + 12, uyy + 54, uw - 24, 11, "#d8cdb4");
    });
  }
}
