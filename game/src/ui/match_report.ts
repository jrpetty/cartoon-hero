// The end-of-match report.
//
// A duel is drawn as a set of *contests*: both numbers at the ends of a single
// bar whose split is the ratio between them, so who was ahead — and by how
// much — reads without arithmetic.
//
// Anything bigger is drawn as *standings*: one row per realm, grouped by team
// in a team game and ranked in a free-for-all. The report used to fold every
// match into "you against them", which is right for a duel and wrong for
// everything else: in a four-way free-for-all it added three opponents who
// were fighting *each other* into one "Opponent" and called it 1v3, and in a
// 2v2 it hid whether you or your ally carried the game.
//
// Four tabs either way, because thirty rows on one screen is a spreadsheet.

import { ui } from "./ui";
import { PAL, teamColor, withAlpha } from "../render/palette";
import { MatchReport, PlayerReport, SideReport } from "../sim/metrics";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";

export type ReportTab = "overview" | "economy" | "military" | "units";
export const REPORT_TABS: { id: ReportTab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "economy", label: "Economy" },
  { id: "military", label: "Military" },
  { id: "units", label: "Armies" },
];

// Type colours. Everything the eye has to *read* is at least 11.5px and well
// clear of the panel in contrast; the first version put column headers at
// 9.5px in a grey barely lighter than the background.
const INK = "#f1e8d2";
const LABEL = "#cfc4a8";
const MUTED = "#aaa08a";
const FAINT = "#6f6a5c";
const BEST = "#ffd86a";
const LOST = "#e39a8e";

const YOU = "#7fb0e8";
const FOE = "#e0786a";
// The duel's two colours. Set from the realms' own colours when the report
// knows them, so the bars match what you saw on the battlefield.
let youCol = YOU;
let foeCol = FOE;

const num = (n: number) => Math.round(n).toLocaleString("en-GB");
/** Building-seconds read better as minutes once they get big. */
const secs = (n: number) => (n >= 120 ? `${Math.floor(n / 60)}m ${Math.round(n % 60)}s` : `${Math.round(n)}s`);
/** Idle time as a share of the time the thing existed at all. */
const idleShare = (idle: number, standing: number) =>
  standing > 0 ? `${Math.round((idle / standing) * 100)}%` : "—";
const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
const AGES = ["Dark", "Feudal", "Castle", "Imperial"];
const ordinal = (n: number) => {
  const t = n % 100;
  if (t >= 11 && t <= 13) return `${n}th`;
  return `${n}${["th", "st", "nd", "rd"][n % 10] ?? "th"}`;
};

const FOOD = "#e59a5c", WOOD = "#b98a55", GOLD = "#f0cf5a";
const ARMY = "#e0786a", BUILT = "#c8a86a", TECH = "#9b7cf0", BANKED = "rgba(255,255,255,0.18)";

// ------------------------------------------------------------ who played --

/** A realm's name, as the scoreboard and the battlefield colours give it. */
export function playerName(p: PlayerReport): string {
  return p.horde ? "The Horde" : teamColor(p.team).name;
}

/** Colour a realm is drawn in: its own, lightened a touch for text on dark. */
function nameColour(p: PlayerReport): string {
  return p.horde ? "#c9c2b0" : teamColor(p.team).light;
}
function barColour(p: PlayerReport): string {
  return p.horde ? "#8d8779" : teamColor(p.team).main;
}

/**
 * True when the report should be drawn as standings rather than a duel: more
 * than two realms, or anyone fighting alongside you.
 */
export function isStandings(r: MatchReport): boolean {
  const ps = r.players;
  return !!ps && (ps.length > 2 || ps.some((p) => p.relation === "ally"));
}

export interface Group {
  id: number;
  members: PlayerReport[];
  won: boolean;
}

/**
 * Realms in the order the report lists them. Winners first; then whoever was
 * still standing, by score; then the fallen, the last to fall first — which in
 * a free-for-all is the finishing order.
 */
export function standings(players: PlayerReport[]): { groups: Group[]; ffa: boolean } {
  const byId = new Map<number, PlayerReport[]>();
  for (const p of players) {
    const g = byId.get(p.group) ?? [];
    g.push(p);
    byId.set(p.group, g);
  }
  const groups: Group[] = [...byId.entries()].map(([id, members]) => ({
    id,
    members: members.slice().sort((a, b) => b.score - a.score || a.team - b.team),
    won: members.some((m) => m.won),
  }));
  const standing = (g: Group) => g.members.some((m) => !m.defeated);
  const fell = (g: Group) => Math.max(...g.members.map((m) => m.defeatedAt));
  const total = (g: Group) => g.members.reduce((a, m) => a + m.score, 0);
  groups.sort((a, b) => {
    if (a.won !== b.won) return a.won ? -1 : 1;
    if (standing(a) !== standing(b)) return standing(a) ? -1 : 1;
    if (!standing(a) && fell(a) !== fell(b)) return fell(b) - fell(a);
    return total(b) - total(a) || a.id - b.id;
  });
  return { groups, ffa: groups.every((g) => g.members.length === 1) };
}

// ---------------------------------------------------------------- duels --

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
  ui.text(fmt(a), x, y, { size: 16, bold: true, color: aWins === true ? BEST : INK });
  ui.text(fmt(b), x + w, y, { align: "right", size: 16, bold: true, color: aWins === false ? BEST : INK });
  ui.text(label, x + w / 2, y, { align: "center", size: 13.5, color: LABEL });
  if (opts.hint) ui.text(opts.hint, x + w / 2, y + 15, { align: "center", size: 11, color: MUTED });

  // The split bar. A zero-zero row draws an even split rather than dividing by
  // nothing, which is what "neither of you did any of this" should look like.
  const total = a + b;
  const frac = total > 0 ? a / total : 0.5;
  const by = y + (opts.hint ? 23 : 11);
  ctx.fillStyle = "rgba(0,0,0,0.45)";
  ctx.beginPath(); ctx.roundRect(x, by, w, 7, 3.5); ctx.fill();
  ctx.save();
  ctx.beginPath(); ctx.roundRect(x, by, w, 7, 3.5); ctx.clip();
  ctx.fillStyle = youCol; ctx.fillRect(x, by, w * frac, 7);
  ctx.fillStyle = foeCol; ctx.fillRect(x + w * frac, by, w * (1 - frac), 7);
  ctx.restore();
  // A tick at the midpoint, so "just ahead" is distinguishable from "even".
  ctx.fillStyle = "rgba(255,255,255,0.45)";
  ctx.fillRect(x + w / 2 - 0.5, by - 2, 1, 11);
}

/** Section heading inside a report tab. */
function heading(x: number, y: number, w: number, text: string, lineEnd = x + w) {
  const ctx = ui.ctx;
  ui.text(text, x, y, { size: 13, bold: true, color: PAL.uiAccent });
  ctx.font = `bold 13px "Trebuchet MS", sans-serif`;
  const tw = ctx.measureText(text).width;
  if (lineEnd <= x + tw + 24) return;
  ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.25);
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(x + tw + 12, y); ctx.lineTo(lineEnd, y);
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

  const cols = [0, 0.32, 0.44, 0.56, 0.72, 0.84, 0.96];
  const cx = (i: number) => x + w * cols[i];
  ui.text("UNIT", cx(0), y, { size: 11.5, bold: true, color: LABEL });
  ["built", "lost", "killed"].forEach((h, i) => {
    ui.text(h, cx(1 + i), y, { align: "right", size: 11.5, bold: true, color: youCol });
    ui.text(h, cx(4 + i), y, { align: "right", size: 11.5, bold: true, color: foeCol });
  });
  let ry = y + 20;
  if (!rows.length) {
    ui.text("— no units were trained —", x, ry + 6, { size: 12.5, color: MUTED });
    return ry + 22;
  }
  rows.forEach((t, i) => {
    if (i % 2 === 0) {
      ctx.fillStyle = "rgba(255,255,255,0.045)";
      ctx.fillRect(x - 6, ry - 11, w + 12, 22);
    }
    ui.text(UNITS[t]?.name ?? t, cx(0), ry, { size: 13, color: INK });
    const cell = (v: number, j: number) =>
      ui.text(v ? String(v) : "·", cx(j), ry, { align: "right", size: 13, bold: !!v, color: v ? INK : FAINT });
    cell(you.trainedByType[t] ?? 0, 1);
    cell(you.lostByType[t] ?? 0, 2);
    cell(you.killedByType[t] ?? 0, 3);
    cell(foe.trainedByType[t] ?? 0, 4);
    cell(foe.lostByType[t] ?? 0, 5);
    cell(foe.killedByType[t] ?? 0, 6);
    ry += 22;
  });
  return ry;
}

/** How a side spent its match, as one stacked bar. */
function spendBar(x: number, y: number, w: number, side: SideReport, label: string, colour: string) {
  const ctx = ui.ctx;
  const total = Math.max(1, side.spentOn.units + side.spentOn.buildings + side.spentOn.tech + side.banked);
  const parts: [number, string, string][] = [
    [side.spentOn.units, ARMY, "army"],
    [side.spentOn.buildings, BUILT, "buildings"],
    [side.spentOn.tech, TECH, "tech"],
    [side.banked, BANKED, "unspent"],
  ];
  ui.text(label, x, y, { size: 13, bold: true, color: colour });
  ui.text(`${num(side.gathered)} gathered`, x + w, y, { align: "right", size: 12, color: MUTED });
  let bx = x;
  const by = y + 10;
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
  ctx.font = `11.5px "Trebuchet MS", sans-serif`;
  for (const [v, col, name] of parts) {
    if (v / total < 0.04) continue;
    const s = `${name} ${num(v)}`;
    ctx.fillStyle = col;
    ctx.fillRect(lx, y + 31, 8, 8);
    ui.text(s, lx + 12, y + 35, { size: 11.5, color: MUTED });
    ctx.font = `11.5px "Trebuchet MS", sans-serif`;
    lx += 26 + ctx.measureText(s).width;
  }
}

function drawDuelTab(tab: ReportTab, x: number, y: number, w: number, h: number, r: MatchReport) {
  const { you, foe } = r;
  const duel = r.players?.length === 2 ? r.players : null;
  const youP = duel?.find((p) => p.relation === "you");
  const foeP = duel?.find((p) => p.relation !== "you");
  youCol = youP ? barColour(youP) : YOU;
  foeCol = foeP ? barColour(foeP) : FOE;
  const youName = youP ? `${playerName(youP)} (you)` : "You";
  const foeName = foeP ? playerName(foeP) : "Opponent";
  // Rows breathe when there is room and close up when there isn't: ten of
  // them plus the spending bars have to fit a 680px-tall window too.
  const rowH = Math.max(32, Math.min(42, Math.floor((h - 110) / 11)));
  const colGap = 32;
  const half = (w - colGap) / 2;

  if (tab === "overview") {
    heading(x, y, w, "THE MATCH");
    let ry = y + 30;
    compareRow(x, ry, w, "Score", you.score, foe.score); ry += rowH;
    compareRow(x, ry, w, "Resources gathered", you.gathered, foe.gathered); ry += rowH;
    compareRow(x, ry, w, "Resources spent", you.spent, foe.spent); ry += rowH;
    compareRow(x, ry, w, "Units killed", you.unitsKilled, foe.unitsKilled); ry += rowH;
    compareRow(x, ry, w, "Units lost", you.unitsLost, foe.unitsLost, { higherIsBetter: false }); ry += rowH;
    compareRow(x, ry, w, "Buildings razed", you.buildingsRazed, foe.buildingsRazed); ry += rowH;
    compareRow(x, ry, w, "Damage dealt", you.damageDealt, foe.damageDealt); ry += rowH;
    compareRow(x, ry, w, "Peak army", you.peakArmy, foe.peakArmy); ry += rowH;
    compareRow(x, ry, w, "Peak villagers", you.peakVillagers, foe.peakVillagers); ry += rowH;
    compareRow(x, ry, w, "Technologies", you.upgrades, foe.upgrades); ry += rowH + 6;
    heading(x, ry, w, "WHERE IT WENT");
    ry += 24;
    spendBar(x, ry, half, you, youName, youCol);
    spendBar(x + half + colGap, ry, half, foe, foeName, foeCol);
    return;
  }

  if (tab === "economy") {
    heading(x, y, w, "GATHERED");
    let ry = y + 30;
    compareRow(x, ry, w, "Food", you.gatheredBy.food, foe.gatheredBy.food); ry += rowH;
    compareRow(x, ry, w, "Wood", you.gatheredBy.wood, foe.gatheredBy.wood); ry += rowH;
    compareRow(x, ry, w, "Gold", you.gatheredBy.gold, foe.gatheredBy.gold); ry += rowH + 6;
    heading(x, ry, w, "SPENT");
    ry += 30;
    compareRow(x, ry, w, "On the army", you.spentOn.units, foe.spentOn.units); ry += rowH;
    compareRow(x, ry, w, "On buildings", you.spentOn.buildings, foe.spentOn.buildings); ry += rowH;
    compareRow(x, ry, w, "On ages & tech", you.spentOn.tech, foe.spentOn.tech); ry += rowH;
    compareRow(x, ry, w, "Left unspent", you.banked, foe.banked, {
      higherIsBetter: false, hint: "resources that sat in the bank doing nothing",
    }); ry += rowH + 14;
    heading(x, ry, w, "WHAT STOOD STILL");
    ry += 30;
    compareRow(x, ry, w, "Villager idle time", you.idleVillagerTime, foe.idleVillagerTime, {
      higherIsBetter: false, format: secs, hint: "villager-seconds spent standing around",
    }); ry += rowH + 12;
    // Idle production, as a share of the time those buildings were standing.
    // A raw total is unreadable: sixty seconds idle means one thing with a
    // single Town Centre and something much worse with three.
    compareRow(x, ry, w, "Town Centre idle", you.idleTcTime, foe.idleTcTime, {
      higherIsBetter: false, format: secs,
      hint: `${idleShare(you.idleTcTime, you.tcSeconds)} of the time it stood · ${idleShare(foe.idleTcTime, foe.tcSeconds)} for them`,
    }); ry += rowH + 12;
    compareRow(x, ry, w, "Production idle", you.idleProductionTime, foe.idleProductionTime, {
      higherIsBetter: false, format: secs,
      hint: `barracks, ranges, stables and the rest — ${idleShare(you.idleProductionTime, you.productionSeconds)} vs ${idleShare(foe.idleProductionTime, foe.productionSeconds)}`,
    });
    return;
  }

  if (tab === "military") {
    heading(x, y, w, "THE FIGHTING");
    let ry = y + 30;
    compareRow(x, ry, w, "Units killed", you.unitsKilled, foe.unitsKilled); ry += rowH;
    compareRow(x, ry, w, "Units lost", you.unitsLost, foe.unitsLost, { higherIsBetter: false }); ry += rowH;
    compareRow(x, ry, w, "Kill / loss ratio", kd(you), kd(foe), { format: (n) => n.toFixed(2) }); ry += rowH;
    compareRow(x, ry, w, "Damage dealt", you.damageDealt, foe.damageDealt); ry += rowH;
    compareRow(x, ry, w, "Damage taken", you.damageTaken, foe.damageTaken, { higherIsBetter: false }); ry += rowH + 6;
    heading(x, ry, w, "THE WAR");
    ry += 30;
    compareRow(x, ry, w, "Buildings razed", you.buildingsRazed, foe.buildingsRazed); ry += rowH;
    compareRow(x, ry, w, "Buildings lost", you.buildingsLost, foe.buildingsLost, { higherIsBetter: false }); ry += rowH;
    compareRow(x, ry, w, "Peak army size", you.peakArmy, foe.peakArmy); ry += rowH;
    compareRow(x, ry, w, "Technologies", you.upgrades, foe.upgrades); ry += rowH;
    compareRow(x, ry, w, "Age reached", you.age + 1, foe.age + 1, { format: (n) => AGES[Math.min(3, n - 1)] ?? String(n) });
    return;
  }

  // ---- armies ----
  heading(x, y, w, "EVERY UNIT, BOTH SIDES");
  ui.text(youName.toUpperCase(), x + w * 0.56, y + 18, { align: "right", size: 12, bold: true, color: youCol });
  ui.text(foeName.toUpperCase(), x + w * 0.96, y + 18, { align: "right", size: 12, bold: true, color: foeCol });
  const after = unitTable(x, y + 40, w, you, foe, Math.max(4, Math.floor((h - 130) / 22)));
  heading(x, after + 16, w, "BUILT");
  builtLine(x, after + 42, w, you.builtByType, foe.builtByType);
}

/** Buildings put up, as one compact line: "House 6/5" is yours / theirs. */
function builtLine(x: number, y: number, w: number, a: Record<string, number>, b: Record<string, number>) {
  const ctx = ui.ctx;
  const types = [...new Set([...Object.keys(a), ...Object.keys(b)])]
    .sort((p, q) => ((a[q] ?? 0) + (b[q] ?? 0)) - ((a[p] ?? 0) + (b[p] ?? 0)));
  let bx = x;
  ctx.font = `12px "Trebuchet MS", sans-serif`;
  for (const t of types) {
    const label = `${BUILDINGS[t]?.name ?? t}  ${a[t] ?? 0}/${b[t] ?? 0}`;
    const tw = ctx.measureText(label).width;
    if (bx + tw > x + w) break;
    ui.text(label, bx, y, { size: 12, color: LABEL });
    ctx.font = `12px "Trebuchet MS", sans-serif`;
    bx += tw + 22;
  }
}

const kd = (s: SideReport) => (s.unitsLost === 0 ? s.unitsKilled : s.unitsKilled / s.unitsLost);

// ------------------------------------------------------------ standings --

interface Col {
  label: string;
  value: (p: PlayerReport) => number;
  format?: (n: number, p: PlayerReport) => string;
  /** Default true. False marks the *lowest* as the best in the column. */
  higherIsBetter?: boolean;
  /** Stacked breakdown drawn as the bar, instead of a single share. */
  stack?: (p: PlayerReport) => [number, string][];
  /** Dropped first when the table is narrow: higher goes first. */
  drop: number;
  minW?: number;
  /** Leave the column out of team totals — a sum of ages or ratios is noise. */
  noTotal?: boolean;
  /** No bar: for things like age, where a share of the best says nothing. */
  noBar?: boolean;
}

/**
 * The standings table: a row per realm, a column per measure, and under every
 * number a bar showing it against the best in that column, in the realm's own
 * colour. The best in each column is picked out in gold, so the answer to "who
 * was best at what" is on the screen without reading a single number.
 */
function standingsTable(
  x: number, y: number, w: number, h: number, players: PlayerReport[], cols: Col[],
  opts: { footnote?: string } = {},
): number {
  const ctx = ui.ctx;
  const { groups, ffa } = standings(players);
  const team = !ffa;

  // Name block, then the columns that fit.
  const placeW = ffa ? 38 : 0;
  const nameW = Math.max(150, Math.min(230, Math.round(w * 0.25))) + placeW;
  let shown = cols.slice();
  const minW = (c: Col) => c.minW ?? 78;
  while (shown.length > 2 && shown.reduce((a, c) => a + minW(c), 0) > w - nameW) {
    const worst = shown.reduce((a, c) => (c.drop > a.drop ? c : a));
    shown = shown.filter((c) => c !== worst);
  }
  const spare = Math.max(0, w - nameW - shown.reduce((a, c) => a + minW(c), 0));
  const widths = shown.map((c) => minW(c) + spare / shown.length);

  // Rows size to the room: roomy for four realms, tight for sixteen. An 8v8
  // has to fit a 760px-tall window, so past ten realms they may close right up.
  const n = players.length;
  const many = n > 10;
  const headerH = 28;
  const groupH = team ? (many ? 24 : 30) : 0;
  const footH = opts.footnote ? 24 : 0;
  const avail = h - headerH - footH - groups.length * groupH;
  const withTotals = Math.floor(avail / Math.max(1, n + groups.length * 0.8));
  const totals = team && withTotals >= 34;
  const rowH = Math.max(many ? 22 : 28, Math.min(54, totals ? withTotals : Math.floor(avail / Math.max(1, n))));
  const compact = rowH < 40;

  // Column headers.
  let cx = x + nameW;
  shown.forEach((c, i) => {
    ui.text(c.label.toUpperCase(), cx + widths[i] - 10, y, { align: "right", size: 11.5, bold: true, color: LABEL });
    cx += widths[i];
  });
  ui.text(ffa ? "REALM" : "TEAM / REALM", x + 12, y, { size: 11.5, bold: true, color: LABEL });

  // The best in every column, across everyone — "best at what" is the point.
  const best = shown.map((c) => {
    const vals = players.filter((p) => !p.horde).map(c.value);
    if (!vals.length) return NaN;
    const b = (c.higherIsBetter ?? true) ? Math.max(...vals) : Math.min(...vals);
    // Nothing to celebrate when everyone is on zero (or all are equal).
    return vals.every((v) => v === b) ? NaN : b;
  });
  const max = shown.map((c) => Math.max(1e-9, ...players.map(c.value)));

  let ry = y + headerH - 6;
  let place = 0;
  groups.forEach((g, gi) => {
    if (team) {
      // A band naming the side: which team, how it ended, what it scored.
      const gy = ry + 6;
      ctx.fillStyle = withAlpha(PAL.uiAccent, 0.1);
      ctx.fillRect(x - 6, gy, w + 12, groupH - 6);
      const mine = g.members.some((m) => m.relation === "you");
      const label = g.members.every((m) => m.horde) ? "THE HORDE" : `TEAM ${gi + 1}${mine ? " — YOUR SIDE" : ""}`;
      ui.text(label, x + 12, gy + (groupH - 6) / 2, { size: 12.5, bold: true, color: mine ? "#ffe9b0" : LABEL });
      const status = g.won ? "Victors" : g.members.every((m) => m.defeated) ? "Defeated" : "";
      if (status) {
        ctx.font = `bold 12.5px "Trebuchet MS", sans-serif`;
        const lw = ctx.measureText(label).width;
        ui.text(`·  ${status}`, x + 20 + lw, gy + (groupH - 6) / 2, {
          size: 12.5, bold: true, color: g.won ? BEST : LOST,
        });
      }
      ry += groupH;
    }
    for (const p of g.members) {
      place++;
      const top = ry;
      const mid = top + rowH / 2;
      const me = p.relation === "you";
      const dim = p.defeated && !p.won;
      // Row ground; yours outlined so you can find yourself in eight rows.
      ctx.fillStyle = me ? withAlpha(PAL.uiAccent, 0.16) : place % 2 ? "rgba(255,255,255,0.05)" : "rgba(255,255,255,0.025)";
      ctx.fillRect(x - 6, top + 1, w + 12, rowH - 2);
      if (me) {
        ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.65);
        ctx.lineWidth = 1.5;
        ctx.strokeRect(x - 6 + 0.75, top + 1.75, w + 12 - 1.5, rowH - 3.5);
      }
      ctx.fillStyle = barColour(p);
      ctx.fillRect(x - 6, top + 1, 5, rowH - 2);

      let nx = x + 10;
      if (ffa) {
        ui.text(ordinal(place), nx, mid, { size: compact ? 13 : 15, bold: true, color: place === 1 ? BEST : LABEL });
        nx += placeW;
      }
      // Name, a tag saying whose it is, and how it ended.
      const nameSize = compact ? 14 : 15.5;
      const nameY = compact ? mid : mid - 8;
      ui.text(playerName(p), nx, nameY, { size: nameSize, bold: true, color: nameColour(p) });
      ctx.font = `bold ${nameSize}px "Trebuchet MS", sans-serif`;
      let tx = nx + ctx.measureText(playerName(p)).width + 8;
      tx += relationTag(tx, nameY, p);
      const status = p.won ? "Victor" : p.defeated ? (p.defeatedAt >= 0 ? `Out at ${mmss(p.defeatedAt)}` : "Defeated") : "Still standing";
      const statusCol = p.won ? BEST : p.defeated ? LOST : MUTED;
      if (compact) {
        if (tx < x + nameW - 40) ui.text(status, tx, nameY, { size: 11, color: statusCol });
      } else {
        ui.text(status, nx, mid + 10, { size: 12, color: statusCol });
      }

      // The numbers.
      let cx2 = x + nameW;
      shown.forEach((c, i) => {
        const cw = widths[i];
        const v = c.value(p);
        const isBest = !p.horde && !Number.isNaN(best[i]) && v === best[i];
        const text = c.format ? c.format(v, p) : num(v);
        ui.text(text, cx2 + cw - 10, compact ? mid - 3 : mid - 5, {
          align: "right", size: compact ? 13.5 : 15, bold: true,
          color: isBest ? BEST : dim ? withAlpha(INK, 0.6) : INK,
        });
        if (c.noBar) { cx2 += cw; return; }
        // The bar: this realm's share of the column's best, in its colour.
        const bx = cx2 + 12, bw = cw - 22, by = compact ? mid + 7 : mid + 9, bh = compact ? 3 : 4;
        ctx.fillStyle = "rgba(255,255,255,0.07)";
        ctx.fillRect(bx, by, bw, bh);
        if (c.stack) {
          const parts = c.stack(p);
          const tot = parts.reduce((a, [pv]) => a + pv, 0);
          let sx = bx;
          const full = bw * Math.min(1, tot / max[i]);
          for (const [pv, col] of parts) {
            const pw = tot > 0 ? (pv / tot) * full : 0;
            ctx.fillStyle = col; ctx.fillRect(sx, by, pw, bh);
            sx += pw;
          }
        } else {
          ctx.fillStyle = withAlpha(barColour(p), dim ? 0.5 : 0.95);
          ctx.fillRect(bx, by, bw * Math.max(0, Math.min(1, v / max[i])), bh);
        }
        cx2 += cw;
      });
      ry += rowH;
    }

    // Team totals, where there is room: what each side did as a side.
    if (totals) {
      const mid = ry + 12;
      ui.text("Team total", x + 12 + placeW, mid, { size: 12, bold: true, color: MUTED });
      let cx3 = x + nameW;
      shown.forEach((c, i) => {
        if (!c.noTotal) {
          const v = g.members.reduce((a, m) => a + c.value(m), 0);
          ui.text(num(v), cx3 + widths[i] - 10, mid, { align: "right", size: 12.5, bold: true, color: MUTED });
        }
        cx3 += widths[i];
      });
      ry += 26;
    }
  });

  if (opts.footnote) {
    ui.text(opts.footnote, x, ry + 16, { size: 11.5, color: MUTED });
    ry += footH;
  }
  return ry + 6;
}

/** How wide `key` will draw the same items. */
function keyWidth(items: [string, string][]): number {
  const ctx = ui.ctx;
  ctx.font = `12px "Trebuchet MS", sans-serif`;
  return items.reduce((a, [l]) => a + 14 + ctx.measureText(l).width + 16, 0) - 16;
}

/** The little "YOU" / "ALLY" badge after a name. Returns the width it took. */
function relationTag(x: number, y: number, p: PlayerReport): number {
  const ctx = ui.ctx;
  const me = p.relation === "you";
  const tag = me ? "YOU" : p.relation === "ally" ? "ALLY" : "";
  if (!tag) return 0;
  ctx.font = `bold 10.5px "Trebuchet MS", sans-serif`;
  const tw = ctx.measureText(tag).width + 10;
  ctx.fillStyle = me ? withAlpha(PAL.uiAccent, 0.85) : withAlpha("#2fb39a", 0.75);
  ctx.beginPath(); ctx.roundRect(x, y - 8, tw, 16, 3); ctx.fill();
  ui.text(tag, x + tw / 2, y + 0.5, { align: "center", size: 10.5, bold: true, color: "#1a1408" });
  return tw + 8;
}

/** A row of colour keys: "■ Food  ■ Wood  ■ Gold". */
function key(x: number, y: number, items: [string, string][], title?: string): number {
  const ctx = ui.ctx;
  let kx = x;
  if (title) {
    ui.text(title, kx, y, { size: 12, bold: true, color: LABEL });
    ctx.font = `bold 12px "Trebuchet MS", sans-serif`;
    kx += ctx.measureText(title).width + 10;
  }
  for (const [label, col] of items) {
    ctx.fillStyle = col;
    ctx.fillRect(kx, y - 5, 10, 10);
    ui.text(label, kx + 14, y, { size: 12, color: LABEL });
    ctx.font = `12px "Trebuchet MS", sans-serif`;
    kx += 14 + ctx.measureText(label).width + 16;
  }
  return kx;
}

const BEST_NOTE = "Gold marks the best in each column — for losses, damage taken and idle time, that is the lowest.";

function drawStandingsTab(tab: ReportTab, x: number, y: number, w: number, h: number, ps: PlayerReport[]) {
  const ctx = ui.ctx;
  ctx.save();
  ctx.beginPath(); ctx.rect(x - 12, y - 16, w + 24, h + 24); ctx.clip();
  try {
    if (tab === "overview") {
      heading(x, y, w, "STANDINGS");
      const tableH = Math.min(h - 40, 60 + ps.length * 54 + 90);
      const after = standingsTable(x, y + 30, w, tableH, ps, [
        { label: "Score", value: (p) => p.score, drop: 0 },
        { label: "Killed", value: (p) => p.unitsKilled, drop: 1 },
        { label: "Lost", value: (p) => p.unitsLost, higherIsBetter: false, drop: 3 },
        { label: "Gathered", value: (p) => p.gathered, drop: 2, minW: 88 },
        { label: "Razed", value: (p) => p.buildingsRazed, drop: 5 },
        { label: "Peak army", value: (p) => p.peakArmy, drop: 6, minW: 86 },
        { label: "Age", value: (p) => p.age, format: (n) => AGES[n] ?? "—", drop: 4, noTotal: true, noBar: true },
      ], { footnote: BEST_NOTE });
      // Where each realm's resources went, if there is room for it: a share,
      // not a size, so a small economy that spent everything on its army reads
      // as clearly as a big one.
      const lineH = 26;
      const need = 34 + ps.length * lineH;
      if (y + h - after >= need) {
        const items: [string, string][] = [["army", ARMY], ["buildings", BUILT], ["tech", TECH], ["unspent", BANKED]];
        const kw = keyWidth(items);
        heading(x, after + 14, w, "WHERE IT WENT", x + w - kw - 16);
        key(x + w - kw, after + 14, items);
        let ly = after + 42;
        const { groups } = standings(ps);
        const labelW = Math.max(150, Math.min(230, Math.round(w * 0.25)));
        for (const g of groups) {
          for (const p of g.members) {
            ui.text(playerName(p), x, ly, { size: 13, bold: true, color: nameColour(p) });
            const parts: [number, string][] = [
              [p.spentOn.units, ARMY], [p.spentOn.buildings, BUILT], [p.spentOn.tech, TECH], [p.banked, BANKED],
            ];
            const tot = Math.max(1, parts.reduce((a, [v]) => a + v, 0));
            const bx = x + labelW, bw = w - labelW - 130;
            ctx.save();
            ctx.beginPath(); ctx.roundRect(bx, ly - 7, bw, 14, 4); ctx.clip();
            ctx.fillStyle = "rgba(0,0,0,0.4)"; ctx.fillRect(bx, ly - 7, bw, 14);
            let sx = bx;
            for (const [v, col] of parts) { const pw = (v / tot) * bw; ctx.fillStyle = col; ctx.fillRect(sx, ly - 7, pw, 14); sx += pw; }
            ctx.restore();
            ui.text(`${num(p.gathered)} gathered`, x + w, ly, { align: "right", size: 12, color: MUTED });
            ly += lineH;
          }
        }
      }
      return;
    }

    if (tab === "economy") {
      heading(x, y, w, "ECONOMY");
      let kx = key(x, y + 28, [["food", FOOD], ["wood", WOOD], ["gold", GOLD]], "Gathered:");
      key(kx + 14, y + 28, [["army", ARMY], ["buildings", BUILT], ["tech", TECH]], "Spent:");
      standingsTable(x, y + 58, w, h - 58, ps, [
        {
          label: "Gathered", value: (p) => p.gathered, drop: 0, minW: 118,
          stack: (p) => [[p.gatheredBy.food, FOOD], [p.gatheredBy.wood, WOOD], [p.gatheredBy.gold, GOLD]],
        },
        {
          label: "Spent", value: (p) => p.spent, drop: 1, minW: 118,
          stack: (p) => [[p.spentOn.units, ARMY], [p.spentOn.buildings, BUILT], [p.spentOn.tech, TECH]],
        },
        { label: "Unspent", value: (p) => p.banked, higherIsBetter: false, drop: 4 },
        { label: "Peak vils", value: (p) => p.peakVillagers, drop: 2 },
        { label: "Vils idle", value: (p) => p.idleVillagerTime, higherIsBetter: false, format: (n) => secs(n), drop: 3, minW: 86, noTotal: true },
        {
          label: "TC idle", value: (p) => (p.tcSeconds > 0 ? p.idleTcTime / p.tcSeconds : 0), higherIsBetter: false,
          format: (_n, p) => idleShare(p.idleTcTime, p.tcSeconds), drop: 5, noTotal: true,
        },
      ], { footnote: BEST_NOTE });
      return;
    }

    if (tab === "military") {
      heading(x, y, w, "THE FIGHTING");
      standingsTable(x, y + 30, w, h - 30, ps, [
        { label: "Killed", value: (p) => p.unitsKilled, drop: 0 },
        { label: "Lost", value: (p) => p.unitsLost, higherIsBetter: false, drop: 1 },
        { label: "K / L", value: kd, format: (n) => n.toFixed(2), drop: 2, noTotal: true, noBar: true },
        { label: "Dmg dealt", value: (p) => p.damageDealt, drop: 3, minW: 88 },
        { label: "Dmg taken", value: (p) => p.damageTaken, higherIsBetter: false, drop: 6, minW: 88 },
        { label: "Razed", value: (p) => p.buildingsRazed, drop: 4 },
        { label: "Bldgs lost", value: (p) => p.buildingsLost, higherIsBetter: false, drop: 7, minW: 84 },
        { label: "Techs", value: (p) => p.upgrades, drop: 5 },
      ], { footnote: BEST_NOTE });
      return;
    }

    armiesTab(x, y, w, h, ps);
  } finally {
    ctx.restore();
  }
}

/**
 * What each realm fielded, as chips — "12 Man-at-Arms · 9 Archer" — biggest
 * first, then what it built. A unit-by-player grid would be eight columns of
 * mostly dots; this reads as each realm's style at a glance.
 */
function armiesTab(x: number, y: number, w: number, h: number, ps: PlayerReport[]) {
  const ctx = ui.ctx;
  heading(x, y, w, "WHAT EACH REALM FIELDED");
  const { groups } = standings(ps);
  const nameW = Math.max(150, Math.min(210, Math.round(w * 0.22)));
  // Sixteen realms in an 8v8 have to fit a 760px window too, so rows close
  // right up when there are many — one line of chips, nothing under the name.
  const rowH = Math.max(ps.length > 10 ? 25 : 40, Math.min(66, Math.floor((h - 40) / Math.max(1, ps.length))));
  const twoLines = rowH >= 52;
  const summary = rowH >= 40;
  let ry = y + 26;
  let i = 0;
  for (const g of groups) {
    for (const p of g.members) {
      i++;
      const me = p.relation === "you";
      ctx.fillStyle = me ? withAlpha(PAL.uiAccent, 0.16) : i % 2 ? "rgba(255,255,255,0.05)" : "rgba(255,255,255,0.025)";
      ctx.fillRect(x - 6, ry + 1, w + 12, rowH - 2);
      ctx.fillStyle = barColour(p);
      ctx.fillRect(x - 6, ry + 1, 5, rowH - 2);
      const l1 = twoLines ? ry + rowH / 2 - 9 : ry + rowH / 2;
      ui.text(playerName(p), x + 10, l1, { size: 14.5, bold: true, color: nameColour(p) });
      ctx.font = `bold 14.5px "Trebuchet MS", sans-serif`;
      relationTag(x + 18 + ctx.measureText(playerName(p)).width, l1, p);
      if (summary) {
        const trained = Object.values(p.trainedByType).reduce((a, v) => a + v, 0);
        ui.text(`${num(trained)} trained · ${num(p.unitsLost)} lost`, x + 10, twoLines ? l1 + 19 : l1 + 15, {
          size: 11.5, color: MUTED,
        });
      }

      // Unit chips, biggest first. Villagers last and muted — every realm
      // trains them and they say nothing about a style.
      const units = Object.entries(p.trainedByType).filter(([, v]) => v > 0)
        .sort((a, b) => (a[0] === "villager" ? 1 : 0) - (b[0] === "villager" ? 1 : 0) || b[1] - a[1]);
      let cx = x + nameW;
      const right = x + w;
      ctx.font = `bold 12.5px "Trebuchet MS", sans-serif`;
      let shownN = 0;
      for (const [t, v] of units) {
        const label = `${v} ${UNITS[t]?.name ?? t}`;
        const cw = ctx.measureText(label).width + 16;
        const left = units.length - shownN;
        if (cx + cw > right - (left > 1 ? 70 : 0)) {
          ui.text(`+${left} more`, cx + 2, l1, { size: 12, color: MUTED });
          break;
        }
        const vil = t === "villager";
        ctx.fillStyle = vil ? "rgba(255,255,255,0.06)" : withAlpha(barColour(p), 0.22);
        ctx.beginPath(); ctx.roundRect(cx, l1 - 10, cw, 20, 4); ctx.fill();
        ctx.strokeStyle = vil ? "rgba(255,255,255,0.12)" : withAlpha(barColour(p), 0.55);
        ctx.lineWidth = 1;
        ctx.stroke();
        ui.text(label, cx + 8, l1 + 0.5, { size: 12.5, bold: true, color: vil ? MUTED : INK });
        ctx.font = `bold 12.5px "Trebuchet MS", sans-serif`;
        cx += cw + 6;
        shownN++;
      }
      if (!units.length) ui.text("— no units trained —", cx, l1, { size: 12, color: MUTED });

      if (twoLines) {
        const built = Object.entries(p.builtByType).filter(([, v]) => v > 0).sort((a, b) => b[1] - a[1]);
        const line = built.map(([t, v]) => `${v} ${BUILDINGS[t]?.name ?? t}`).join("  ·  ");
        if (line) {
          ctx.font = `12px "Trebuchet MS", sans-serif`;
          let s = `Built: ${line}`;
          const maxW = w - nameW;
          while (s.length > 12 && ctx.measureText(s).width > maxW) s = s.slice(0, s.lastIndexOf("  ·  ")) + "  …";
          ui.text(s, x + nameW, l1 + 20, { size: 12, color: LABEL });
        }
      }
      ry += rowH;
    }
  }
}

// ------------------------------------------------------------------ entry --

/**
 * Draw one tab's body inside the given rect. Returns nothing — the caller owns
 * the frame, the tabs and the buttons.
 */
export function drawReportTab(
  tab: ReportTab, x: number, y: number, w: number, h: number, r: MatchReport,
) {
  if (isStandings(r)) drawStandingsTab(tab, x, y, w, h, r.players!);
  else drawDuelTab(tab, x, y, w, h, r);
}

/**
 * The key beside the tabs. A duel needs one — two colours stand for two
 * realms on every row. Standings name each realm on its own row, so it says
 * nothing there. `right` is the right edge to align against.
 */
export function drawReportKey(right: number, y: number, r: MatchReport) {
  if (isStandings(r)) return;
  const ctx = ui.ctx;
  const ps = r.players;
  const youP = ps?.find((p) => p.relation === "you");
  const foeP = ps?.find((p) => p.relation !== "you");
  const items: [string, string][] = [
    [youP ? `${playerName(youP)} (you)` : "You", youP ? barColour(youP) : YOU],
    [foeP ? playerName(foeP) : "Opponent", foeP ? barColour(foeP) : FOE],
  ];
  ctx.font = `12.5px "Trebuchet MS", sans-serif`;
  const width = items.reduce((a, [l]) => a + 16 + ctx.measureText(l).width + 16, 0);
  let kx = right - width;
  for (const [label, col] of items) {
    ctx.fillStyle = col;
    ctx.fillRect(kx, y - 6, 12, 12);
    ui.text(label, kx + 17, y, { size: 12.5, color: LABEL });
    ctx.font = `12.5px "Trebuchet MS", sans-serif`;
    kx += 16 + ctx.measureText(label).width + 16;
  }
}

/** "2v2", "1v1", "4-player free-for-all" — how the match was set up. */
export function matchFormat(r: MatchReport): string {
  const ps = r.players;
  if (!ps) return `${r.you.teams.length}v${r.foe.teams.length}`;
  if (ps.some((p) => p.horde)) {
    const defenders = ps.filter((p) => !p.horde).length;
    return defenders > 1 ? `Survival · ${defenders} defenders` : "Survival";
  }
  const sizes = new Map<number, number>();
  for (const p of ps) sizes.set(p.group, (sizes.get(p.group) ?? 0) + 1);
  if (sizes.size > 2 && [...sizes.values()].every((s) => s === 1)) return `${ps.length}-player free-for-all`;
  // Your side first, the way you'd say it.
  const mine = ps.find((p) => p.relation === "you")?.group;
  const order = [...sizes.entries()].sort((a, b) => (a[0] === mine ? -1 : b[0] === mine ? 1 : b[1] - a[1]));
  return order.map(([, s]) => s).join("v");
}

/** The line under the title: how long, on what, and who was in it. */
export function reportSubtitle(r: MatchReport): string {
  return `${r.mapName}  ·  ${matchFormat(r)}  ·  ${mmss(r.durationSec)}`;
}
