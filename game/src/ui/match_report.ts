// The end-of-match report: your alliance against theirs, in depth.
//
// The old battle report was five numbers in a box, which can tell you that you
// lost but never why. The comparison here is built around one idea: every row
// is a *contest*. Both numbers sit at the ends of a single bar whose split is
// the ratio between them, so who was ahead — and by how much — reads without
// arithmetic. Four tabs, because thirty rows on one screen is a spreadsheet.

import { ui } from "./ui";
import { PAL, withAlpha } from "../render/palette";
import { MatchReport, SideReport } from "../sim/metrics";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";

export type ReportTab = "overview" | "economy" | "military" | "units";
export const REPORT_TABS: { id: ReportTab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "economy", label: "Economy" },
  { id: "military", label: "Military" },
  { id: "units", label: "Armies" },
];

const YOU = "#7fb0e8";
const FOE = "#e0786a";

const num = (n: number) => Math.round(n).toLocaleString("en-GB");
const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;

/**
 * One contested row: label in the middle, values at the ends, and a split bar
 * underneath. `higherIsBetter` only decides the colour of the winning number —
 * for something like "resources left unspent" the bigger number is the worse one.
 */
export function compareRow(
  x: number, y: number, w: number,
  label: string, a: number, b: number,
  opts: { format?: (n: number) => string; higherIsBetter?: boolean; hint?: string } = {},
) {
  const ctx = ui.ctx;
  const fmt = opts.format ?? num;
  const better = opts.higherIsBetter ?? true;
  const aWins = a === b ? null : better ? a > b : a < b;
  ui.text(fmt(a), x, y, { size: 14, bold: true, color: aWins === true ? "#7df2a9" : "#d8cdb4" });
  ui.text(fmt(b), x + w, y, { align: "right", size: 14, bold: true, color: aWins === false ? "#7df2a9" : "#d8cdb4" });
  ui.text(label, x + w / 2, y, { align: "center", size: 12, color: "#9a917b" });
  if (opts.hint) ui.text(opts.hint, x + w / 2, y + 13, { align: "center", size: 9, color: "#6f6a5c" });

  // The split bar. A zero-zero row draws an even split rather than dividing by
  // nothing, which is what "neither of you did any of this" should look like.
  const total = a + b;
  const frac = total > 0 ? a / total : 0.5;
  const by = y + (opts.hint ? 20 : 8);
  ctx.fillStyle = "rgba(0,0,0,0.45)";
  ctx.beginPath(); ctx.roundRect(x, by, w, 5, 2.5); ctx.fill();
  ctx.save();
  ctx.beginPath(); ctx.roundRect(x, by, w, 5, 2.5); ctx.clip();
  ctx.fillStyle = YOU; ctx.fillRect(x, by, w * frac, 5);
  ctx.fillStyle = FOE; ctx.fillRect(x + w * frac, by, w * (1 - frac), 5);
  ctx.restore();
  // A tick at the midpoint, so "just ahead" is distinguishable from "even".
  ctx.fillStyle = "rgba(255,255,255,0.35)";
  ctx.fillRect(x + w / 2 - 0.5, by - 2, 1, 9);
}

/** Section heading inside a report tab. */
function heading(x: number, y: number, w: number, text: string) {
  ui.text(text, x, y, { size: 12, bold: true, color: PAL.uiAccent });
  const ctx = ui.ctx;
  ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.22);
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(x + text.length * 7 + 12, y); ctx.lineTo(x + w, y);
  ctx.stroke();
}

/**
 * A per-type table: every unit either side trained, lost or killed. Sorted by
 * how much of the match each type actually accounted for, so the comp that
 * decided the game is at the top rather than whatever is first alphabetically.
 */
function unitTable(x: number, y: number, w: number, you: SideReport, foe: SideReport, rowsMax: number): number {
  const ctx = ui.ctx;
  const types = new Set<string>([
    ...Object.keys(you.trainedByType), ...Object.keys(foe.trainedByType),
    ...Object.keys(you.lostByType), ...Object.keys(foe.lostByType),
  ]);
  const weight = (t: string) =>
    (you.trainedByType[t] ?? 0) + (foe.trainedByType[t] ?? 0) +
    (you.lostByType[t] ?? 0) + (foe.lostByType[t] ?? 0);
  const rows = [...types].filter((t) => weight(t) > 0).sort((p, q) => weight(q) - weight(p)).slice(0, rowsMax);

  const cols = [0, 0.30, 0.42, 0.54, 0.70, 0.82, 0.94];
  const cx = (i: number) => x + w * cols[i];
  ui.text("UNIT", cx(0), y, { size: 9.5, bold: true, color: "#8f8770" });
  ui.text("built", cx(1), y, { align: "right", size: 9.5, color: YOU });
  ui.text("lost", cx(2), y, { align: "right", size: 9.5, color: YOU });
  ui.text("killed", cx(3), y, { align: "right", size: 9.5, color: YOU });
  ui.text("built", cx(4), y, { align: "right", size: 9.5, color: FOE });
  ui.text("lost", cx(5), y, { align: "right", size: 9.5, color: FOE });
  ui.text("killed", cx(6), y, { align: "right", size: 9.5, color: FOE });
  let ry = y + 16;
  if (!rows.length) {
    ui.text("— no units were trained —", x, ry + 6, { size: 11, color: "#6f6a5c" });
    return ry + 20;
  }
  for (const t of rows) {
    ctx.fillStyle = "rgba(255,255,255,0.03)";
    ctx.fillRect(x - 6, ry - 9, w + 12, 18);
    ui.text(UNITS[t]?.name ?? t, cx(0), ry, { size: 11.5, color: "#e7ddc4" });
    const cell = (v: number, i: number, col: string) =>
      ui.text(v ? String(v) : "·", cx(i), ry, { align: "right", size: 11.5, color: v ? col : "#55504a" });
    cell(you.trainedByType[t] ?? 0, 1, "#cfe0ff");
    cell(you.lostByType[t] ?? 0, 2, "#cfe0ff");
    cell(you.killedByType[t] ?? 0, 3, "#cfe0ff");
    cell(foe.trainedByType[t] ?? 0, 4, "#f0c0b6");
    cell(foe.lostByType[t] ?? 0, 5, "#f0c0b6");
    cell(foe.killedByType[t] ?? 0, 6, "#f0c0b6");
    ry += 19;
  }
  return ry;
}

/** How a side spent its match, as one stacked bar. */
function spendBar(x: number, y: number, w: number, side: SideReport, label: string, colour: string) {
  const ctx = ui.ctx;
  const total = Math.max(1, side.spentOn.units + side.spentOn.buildings + side.spentOn.tech + side.banked);
  const parts: [number, string, string][] = [
    [side.spentOn.units, "#e0786a", "army"],
    [side.spentOn.buildings, "#c8a86a", "buildings"],
    [side.spentOn.tech, "#9b7cf0", "tech"],
    [side.banked, "rgba(255,255,255,0.16)", "unspent"],
  ];
  ui.text(label, x, y, { size: 11.5, bold: true, color: colour });
  ui.text(`${num(side.gathered)} gathered`, x + w, y, { align: "right", size: 10.5, color: "#9a917b" });
  let bx = x;
  const by = y + 8;
  ctx.save();
  ctx.beginPath(); ctx.roundRect(x, by, w, 14, 4); ctx.clip();
  ctx.fillStyle = "rgba(0,0,0,0.4)"; ctx.fillRect(x, by, w, 14);
  for (const [v, col] of parts) {
    const pw = (v / total) * w;
    ctx.fillStyle = col; ctx.fillRect(bx, by, pw, 14);
    bx += pw;
  }
  ctx.restore();
  // Legend under the bar, only naming the slices big enough to see.
  let lx = x;
  for (const [v, col, name] of parts) {
    if (v / total < 0.04) continue;
    ctx.fillStyle = col;
    ctx.fillRect(lx, y + 27, 7, 7);
    ui.text(`${name} ${num(v)}`, lx + 11, y + 31, { size: 9.5, color: "#9a917b" });
    lx += 22 + `${name} ${num(v)}`.length * 5.4;
  }
}

/**
 * Draw one tab's body inside the given rect. Returns nothing — the caller owns
 * the frame, the tabs and the buttons.
 */
export function drawReportTab(
  tab: ReportTab, x: number, y: number, w: number, h: number, r: MatchReport,
) {
  const { you, foe } = r;
  const rowH = 34;
  const colGap = 32;
  const half = (w - colGap) / 2;

  if (tab === "overview") {
    heading(x, y, w, "THE MATCH");
    let ry = y + 26;
    compareRow(x, ry, w, "Score", you.score, foe.score); ry += rowH;
    compareRow(x, ry, w, "Resources gathered", you.gathered, foe.gathered); ry += rowH;
    compareRow(x, ry, w, "Resources spent", you.spent, foe.spent); ry += rowH;
    compareRow(x, ry, w, "Units killed", you.unitsKilled, foe.unitsKilled); ry += rowH;
    compareRow(x, ry, w, "Units lost", you.unitsLost, foe.unitsLost, { higherIsBetter: false }); ry += rowH;
    compareRow(x, ry, w, "Buildings razed", you.buildingsRazed, foe.buildingsRazed); ry += rowH;
    compareRow(x, ry, w, "Damage dealt", you.damageDealt, foe.damageDealt); ry += rowH;
    compareRow(x, ry, w, "Peak army", you.peakArmy, foe.peakArmy); ry += rowH;
    compareRow(x, ry, w, "Peak villagers", you.peakVillagers, foe.peakVillagers); ry += rowH;
    compareRow(x, ry, w, "Technologies", you.upgrades, foe.upgrades); ry += rowH + 10;
    heading(x, ry, w, "WHERE IT WENT");
    ry += 22;
    spendBar(x, ry, half, you, "You", YOU);
    spendBar(x + half + colGap, ry, half, foe, "Opponent", FOE);
    return;
  }

  if (tab === "economy") {
    heading(x, y, w, "GATHERED");
    let ry = y + 26;
    compareRow(x, ry, w, "Food", you.gatheredBy.food, foe.gatheredBy.food); ry += rowH;
    compareRow(x, ry, w, "Wood", you.gatheredBy.wood, foe.gatheredBy.wood); ry += rowH;
    compareRow(x, ry, w, "Gold", you.gatheredBy.gold, foe.gatheredBy.gold); ry += rowH + 8;
    heading(x, ry, w, "SPENT");
    ry += 26;
    compareRow(x, ry, w, "On the army", you.spentOn.units, foe.spentOn.units); ry += rowH;
    compareRow(x, ry, w, "On buildings", you.spentOn.buildings, foe.spentOn.buildings); ry += rowH;
    compareRow(x, ry, w, "On ages & tech", you.spentOn.tech, foe.spentOn.tech); ry += rowH;
    compareRow(x, ry, w, "Left unspent", you.banked, foe.banked, {
      higherIsBetter: false, hint: "resources that sat in the bank doing nothing",
    }); ry += rowH + 12;
    heading(x, ry, w, "THE WORKFORCE");
    ry += 26;
    compareRow(x, ry, w, "Peak villagers", you.peakVillagers, foe.peakVillagers); ry += rowH;
    compareRow(x, ry, w, "Villager idle time", you.idleVillagerTime, foe.idleVillagerTime, {
      higherIsBetter: false, format: (n) => `${Math.round(n)}s`,
      hint: "villager-seconds spent standing around",
    });
    return;
  }

  if (tab === "military") {
    heading(x, y, w, "THE FIGHTING");
    let ry = y + 26;
    compareRow(x, ry, w, "Units killed", you.unitsKilled, foe.unitsKilled); ry += rowH;
    compareRow(x, ry, w, "Units lost", you.unitsLost, foe.unitsLost, { higherIsBetter: false }); ry += rowH;
    const kd = (s: SideReport) => (s.unitsLost === 0 ? s.unitsKilled : s.unitsKilled / s.unitsLost);
    compareRow(x, ry, w, "Kill / loss ratio", kd(you), kd(foe), { format: (n) => n.toFixed(2) }); ry += rowH;
    compareRow(x, ry, w, "Damage dealt", you.damageDealt, foe.damageDealt); ry += rowH;
    compareRow(x, ry, w, "Damage taken", you.damageTaken, foe.damageTaken, { higherIsBetter: false }); ry += rowH + 8;
    heading(x, ry, w, "THE WAR");
    ry += 26;
    compareRow(x, ry, w, "Buildings razed", you.buildingsRazed, foe.buildingsRazed); ry += rowH;
    compareRow(x, ry, w, "Buildings lost", you.buildingsLost, foe.buildingsLost, { higherIsBetter: false }); ry += rowH;
    compareRow(x, ry, w, "Peak army size", you.peakArmy, foe.peakArmy); ry += rowH;
    compareRow(x, ry, w, "Technologies", you.upgrades, foe.upgrades); ry += rowH;
    compareRow(x, ry, w, "Age reached", you.age + 1, foe.age + 1, { format: (n) => ["Dark", "Feudal", "Castle", "Imperial"][Math.min(3, n - 1)] ?? String(n) });
    return;
  }

  // ---- armies ----
  heading(x, y, w, "EVERY UNIT, BOTH SIDES");
  ui.text("YOU", x + w * 0.54, y, { align: "right", size: 10, bold: true, color: YOU });
  ui.text("THEM", x + w * 0.94, y, { align: "right", size: 10, bold: true, color: FOE });
  const after = unitTable(x, y + 26, w, you, foe, Math.max(4, Math.floor((h - 120) / 19)));
  // Buildings get a compact line rather than a second table — what was put up
  // matters, but not enough to spend half the panel on.
  heading(x, after + 14, w, "BUILT");
  const bTypes = [...new Set([...Object.keys(you.builtByType), ...Object.keys(foe.builtByType)])]
    .sort((p, q) => ((you.builtByType[q] ?? 0) + (foe.builtByType[q] ?? 0)) - ((you.builtByType[p] ?? 0) + (foe.builtByType[p] ?? 0)))
    .slice(0, 8);
  let bx = x;
  const byy = after + 40;
  for (const t of bTypes) {
    const label = `${BUILDINGS[t]?.name ?? t}  ${you.builtByType[t] ?? 0}/${foe.builtByType[t] ?? 0}`;
    ui.text(label, bx, byy, { size: 10.5, color: "#cabfa4" });
    bx += label.length * 5.9 + 18;
    if (bx > x + w - 90) break;
  }
}

/** The line under the title: how long, on what, and who was in it. */
export function reportSubtitle(r: MatchReport): string {
  const sides = `${r.you.teams.length}v${r.foe.teams.length}`;
  return `${r.mapName}  ·  ${sides}  ·  ${mmss(r.durationSec)}`;
}
