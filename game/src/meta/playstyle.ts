// Playstyle: how a player wins, read from what they actually did.
//
// Two levels, both built only from numbers the game already records:
//
//  • Each match gets a style — Rush, Raid, Demolition, Turtle, Boom, Fast Age,
//    Late Game or Standard — from that game alone: when you first attacked,
//    what you built, what you broke, how big your economy grew, measured
//    against the average enemy in that same game.
//
//  • Across a career, eight strategy archetypes are scored 0–1 from the
//    player's whole record, the strongest becomes their style (with a name
//    that says how: "Man-at-Arms Rush", "Siege Grinder"), and a set of traits
//    adds the rest (Cavalry Commander, Faction Specialist, Efficient Trader…).
//    Every score comes with the evidence behind it, so a player can see *why*
//    they're a Boomer, and what the Rusher threshold would need.
//
// "More than usual" always means more than the opponents you actually faced
// (each match stores the average enemy's numbers), not a guessed norm.

import { UNITS } from "../content/units";
import { FACTIONS } from "../content/factions";
import { OATHS } from "../content/oaths";
import { ArmorClass } from "../sim/types";
import type { CareerMatch } from "./career";
import { defensesBuilt } from "./career";

// ------------------------------------------------------------------ helpers --
const mean = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0);
const median = (xs: number[]) => { if (!xs.length) return 0; const a = [...xs].sort((p, q) => p - q); const m = a.length >> 1; return a.length % 2 ? a[m] : (a[m - 1] + a[m]) / 2; };
/** 0 at or below `lo`, 1 at or above `hi`, linear between. */
export const ramp = (x: number, lo: number, hi: number) => Math.max(0, Math.min(1, (x - lo) / (hi - lo)));
const ratio = (a: number, b: number) => (b > 0 ? a / b : a > 0 ? 2 : 1);
const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, "0")}`;
const pct = (x: number) => `${Math.round(x * 100)}%`;
const unitName = (id: string) => UNITS[id]?.name ?? id;
const facName = (id: string) => FACTIONS[id as keyof typeof FACTIONS]?.name ?? id;

export type UnitClass = "infantry" | "archer" | "cavalry" | "siege" | "support";
/** A military unit's class (villagers and unknowns → null). */
export function unitClass(id: string): UnitClass | null {
  const d = UNITS[id];
  if (!d || id === "villager" || d.armorClass === ArmorClass.Villager) return null;
  if (d.healer) return "support";
  switch (d.armorClass) {
    case ArmorClass.Infantry: return "infantry";
    case ArmorClass.Archer: return "archer";
    case ArmorClass.Cavalry: return "cavalry";
    case ArmorClass.Siege: return "siege";
    default: return null;
  }
}

/** Military units trained, by type (villagers left out). */
function military(m: CareerMatch): Record<string, number> {
  const out: Record<string, number> = {};
  for (const [u, n] of Object.entries(m.trained)) if (unitClass(u)) out[u] = n;
  return out;
}
/**
 * When you first attacked: the first blow on the enemy's own ground. Records
 * from before attack and defence were told apart only have "first hit on an
 * enemy anywhere", which also counts beating off their attack at home.
 */
export const attackAt = (m: CareerMatch) => m.firstAttackAt ?? m.firstHitAt ?? -1;
const CLASS_WORD: Record<UnitClass, string> = { infantry: "Infantry", archer: "Archer", cavalry: "Cavalry", siege: "Siege", support: "Monk" };
/**
 * What your early attacks are made of, as one word: the unit if one dominates
 * (Man-at-Arms), else the class (Archer, Cavalry), else "Mixed". Read from the
 * damage each unit type did away from home in the first ten minutes, so it is
 * what actually fought, not what sat at home.
 */
export function openerWord(opener: Record<string, number>): { word: string; unit: string; share: number } {
  const mil = Object.entries(opener).filter(([u]) => unitClass(u));
  const total = mil.reduce((a, [, v]) => a + v, 0);
  if (total <= 0) return { word: "", unit: "", share: 0 };
  mil.sort((a, b) => b[1] - a[1]);
  const [unit, top] = mil[0];
  if (top / total >= 0.5) return { word: unitName(unit), unit, share: top / total };
  const cls: Partial<Record<UnitClass, number>> = {};
  for (const [u, v] of mil) { const c = unitClass(u)!; cls[c] = (cls[c] ?? 0) + v; }
  const [c, cv] = (Object.entries(cls) as [UnitClass, number][]).sort((a, b) => b[1] - a[1])[0];
  if (cv / total >= 0.55) return { word: CLASS_WORD[c], unit: "", share: cv / total };
  return { word: "Mixed", unit: "", share: top / total };
}
const sum = (r: Record<string, number>) => Object.values(r).reduce((a, b) => a + b, 0);
const villagerKills = (m: CareerMatch) => m.killed.villager ?? 0;
const gatherPerMin = (m: CareerMatch) => (m.durationSec > 0 ? m.gathered / (m.durationSec / 60) : 0);
const foeGatherPerMin = (m: CareerMatch) => (m.foe && m.durationSec > 0 ? m.foe.gathered / (m.durationSec / 60) : 0);

// ------------------------------------------------------- one match's style --
export type MatchStyle = "rush" | "raid" | "demolition" | "turtle" | "boom" | "fastage" | "lategame" | "standard";

export const MATCH_STYLES: Record<MatchStyle, { name: string; color: string; rule: string }> = {
  rush: { name: "Rush", color: "#e0786a", rule: "First attack on an enemy before 7:00." },
  raid: { name: "Raid", color: "#d8a83a", rule: "Killed 8+ enemy villagers, and half again as many as the enemy killed of yours." },
  demolition: { name: "Demolition", color: "#c8643a", rule: "Razed 5+ buildings, and half again as many as the average enemy." },
  turtle: { name: "Turtle", color: "#7fb0c8", rule: "Built 6+ tower-equivalents of defence (walls count ¼ per segment), half again the enemy's, and didn't attack before 10:00." },
  boom: { name: "Boom", color: "#8fd07a", rule: "Peaked at 35+ villagers and 20% more than the enemy, with no attack before 10:00." },
  fastage: { name: "Fast Age", color: "#9a8ae0", rule: "Reached the Crown Age by 14:00, or researched half again as much as the enemy (6+ techs)." },
  lategame: { name: "Late Game", color: "#5b8fe0", rule: "The match ran 35 minutes or more." },
  standard: { name: "Standard", color: "#a89f88", rule: "None of the above stood out." },
};

/** Every style this match fits, strongest signal first. */
export function matchStyles(m: CareerMatch): MatchStyle[] {
  const out: MatchStyle[] = [];
  const foe = m.foe;
  const hit = attackAt(m);
  if (hit >= 0 && hit <= 420) out.push("rush");
  const vk = villagerKills(m);
  if (vk >= 8 && vk >= (foe?.villagerKills ?? 0) * 1.5) out.push("raid");
  if (m.razed >= 5 && m.razed >= (foe?.razed ?? 0) * 1.5) out.push("demolition");
  const def = defensesBuilt(m.built);
  const quietStart = hit < 0 || hit >= 600;
  if (def >= 6 && def >= (foe?.defenses ?? 0) * 1.5 && quietStart) out.push("turtle");
  if (m.peakVillagers >= 35 && m.peakVillagers >= (foe?.peakVillagers ?? 0) * 1.2 && quietStart) out.push("boom");
  const crown = m.ageTimes[2];
  if ((crown !== undefined && crown !== null && crown > 0 && crown <= 840) || (m.upgrades >= 6 && m.upgrades >= (foe?.upgrades ?? 0) * 1.5)) out.push("fastage");
  if (m.durationSec >= 2100) out.push("lategame");
  return out.length ? out : ["standard"];
}
export const matchStyle = (m: CareerMatch): MatchStyle => matchStyles(m)[0];

// --------------------------------------------------------- career features --
export interface StyleFeatures {
  games: number;
  wins: number;
  winRate: number;
  /** Share of wins by length. */
  winsBefore15: number;
  wins15to30: number;
  wins30to40: number;
  winsAfter40: number;
  avgWinSec: number;
  /** Games with a first attack before 7:00, of games that recorded it. */
  rushRate: number;
  medianFirstHit: number;
  /** What your early attacks are made of ("Man-at-Arms", "Archer", "Mixed"). */
  rushUnit: string;
  rushUnitShare: number;
  openerWord: string;
  /** Wins that went past 30 minutes, as a share of wins. */
  lateWins: number;
  /** Average share of the match your Town Center sat idle. */
  tcIdle: number;
  /** Of games where the enemy attacked before 8:00, the share you won. */
  heldRushRate: number;
  rushedGames: number;
  classShare: Record<UnitClass, number>;
  topUnit: string;
  topUnitShare: number;
  /** Effective number of unit types you field (1 = one unit only). */
  diversity: number;
  razedPerGame: number;
  razedRatio: number;
  raidPerGame: number;
  raidRatio: number;
  defensesPerGame: number;
  defenseRatio: number;
  peakVillagers: number;
  villagerRatio: number;
  gatherPerMin: number;
  gatherRatio: number;
  upgradesPerGame: number;
  upgradeRatio: number;
  crownAvgSec: number;
  crownRate: number;
  empireRate: number;
  kd: number;
  foeKd: number;
  combatPerMin: number;
  combatRatio: number;
  lateShare: number;
  lateWinRate: number;
  earlyLossShare: number;
  spendUnits: number;
  spendBuildings: number;
  spendTech: number;
  topFaction: string;
  topFactionShare: number;
  factionsPlayed: number;
  topOath: string;
  topOathShare: number;
  topMap: string;
  topMapShare: number;
  topMapWinRate: number;
}

export function features(ms: CareerMatch[]): StyleFeatures {
  const n = ms.length;
  const wins = ms.filter((m) => m.won);
  const losses = ms.filter((m) => !m.won);
  const winSecs = wins.map((m) => m.durationSec);
  const share = (xs: CareerMatch[], f: (m: CareerMatch) => boolean) => (xs.length ? xs.filter(f).length / xs.length : 0);
  const withHit = ms.filter((m) => m.firstAttackAt !== undefined || m.firstHitAt !== undefined);
  // What the early attacks were made of: the damage each unit did away from
  // home in the first ten minutes of games where you attacked early. Older
  // records without that fall back to what was trained in fast wins.
  const opener: Record<string, number> = {};
  for (const m of ms) {
    const a = attackAt(m);
    if (a < 0 || a > 600) continue;
    if (m.opener) { const t = sum(m.opener) || 1; for (const [u, d] of Object.entries(m.opener)) opener[u] = (opener[u] ?? 0) + d / t; }
    else if (m.won && m.durationSec <= 900) { const mil = military(m), t = sum(mil) || 1; for (const [u, k] of Object.entries(mil)) opener[u] = (opener[u] ?? 0) + k / t; }
  }
  const op = openerWord(opener);
  const rushUnit = op.unit;
  const rushed = ms.filter((m) => m.foe?.firstAttackAt !== undefined && m.foe.firstAttackAt >= 0 && m.foe.firstAttackAt <= 480);
  // Army make-up.
  const allMil: Record<string, number> = {};
  for (const m of ms) for (const [u, k] of Object.entries(military(m))) allMil[u] = (allMil[u] ?? 0) + k;
  const milTotal = Math.max(1, sum(allMil));
  const classShare: Record<UnitClass, number> = { infantry: 0, archer: 0, cavalry: 0, siege: 0, support: 0 };
  for (const [u, k] of Object.entries(allMil)) { const c = unitClass(u); if (c) classShare[c] += k / milTotal; }
  const topUnitEntry = Object.entries(allMil).sort((a, b) => b[1] - a[1])[0];
  const entropy = -Object.values(allMil).reduce((a, k) => { const p = k / milTotal; return a + (p > 0 ? p * Math.log(p) : 0); }, 0);
  // Against the opponents faced.
  const withFoe = ms.filter((m) => m.foe);
  const foeMean = (f: (m: CareerMatch) => number) => mean(withFoe.map(f));
  const youMean = (f: (m: CareerMatch) => number) => mean(withFoe.map(f));
  const crowns = ms.map((m) => m.ageTimes[2]).filter((t): t is number => typeof t === "number" && t > 0);
  const kills = ms.reduce((a, m) => a + m.kills, 0), lost = ms.reduce((a, m) => a + m.losses, 0);
  const foeKills = withFoe.reduce((a, m) => a + m.foe!.kills, 0), foeLost = withFoe.reduce((a, m) => a + m.foe!.losses, 0);
  const minutes = ms.reduce((a, m) => a + m.durationSec / 60, 0);
  const late = ms.filter((m) => m.durationSec >= 1800);
  const spent = ms.reduce((a, m) => { const s = m.spentOn; if (s) { a.u += s.units; a.b += s.buildings; a.t += s.tech; } return a; }, { u: 0, b: 0, t: 0 });
  const spentAll = Math.max(1, spent.u + spent.b + spent.t);
  const count = (key: (m: CareerMatch) => string | string[]) => {
    const c = new Map<string, number>();
    for (const m of ms) for (const k of ([] as string[]).concat(key(m))) if (k) c.set(k, (c.get(k) ?? 0) + 1);
    return [...c.entries()].sort((a, b) => b[1] - a[1]);
  };
  const facs = count((m) => m.faction);
  const oaths = count((m) => m.oaths);
  const sworn = ms.filter((m) => m.oaths.length).length;
  const maps = count((m) => m.map);
  const topMap = maps[0]?.[0] ?? "";
  const onTopMap = ms.filter((m) => m.map === topMap);
  return {
    games: n,
    wins: wins.length,
    winRate: n ? wins.length / n : 0,
    winsBefore15: share(wins, (m) => m.durationSec < 900),
    wins15to30: share(wins, (m) => m.durationSec >= 900 && m.durationSec < 1800),
    wins30to40: share(wins, (m) => m.durationSec >= 1800 && m.durationSec < 2400),
    winsAfter40: share(wins, (m) => m.durationSec >= 2400),
    avgWinSec: mean(winSecs),
    rushRate: share(withHit, (m) => attackAt(m) >= 0 && attackAt(m) <= 420),
    medianFirstHit: median(withHit.map((m) => (attackAt(m) >= 0 ? attackAt(m) : m.durationSec))),
    rushUnit,
    rushUnitShare: op.share,
    openerWord: op.word,
    lateWins: share(wins, (m) => m.durationSec >= 1800),
    tcIdle: mean(ms.map((m) => m.tcIdleShare)),
    heldRushRate: share(rushed, (m) => m.won),
    rushedGames: rushed.length,
    classShare,
    topUnit: topUnitEntry?.[0] ?? "",
    topUnitShare: topUnitEntry ? topUnitEntry[1] / milTotal : 0,
    diversity: Object.keys(allMil).length ? Math.exp(entropy) : 0,
    razedPerGame: mean(ms.map((m) => m.razed)),
    razedRatio: ratio(youMean((m) => m.razed), foeMean((m) => m.foe!.razed)),
    raidPerGame: mean(ms.map(villagerKills)),
    raidRatio: ratio(youMean(villagerKills), foeMean((m) => m.foe!.villagerKills)),
    defensesPerGame: mean(ms.map((m) => defensesBuilt(m.built))),
    defenseRatio: ratio(youMean((m) => defensesBuilt(m.built)), foeMean((m) => m.foe!.defenses)),
    peakVillagers: mean(ms.map((m) => m.peakVillagers)),
    villagerRatio: ratio(youMean((m) => m.peakVillagers), foeMean((m) => m.foe!.peakVillagers)),
    gatherPerMin: mean(ms.map(gatherPerMin)),
    gatherRatio: ratio(youMean(gatherPerMin), foeMean(foeGatherPerMin)),
    upgradesPerGame: mean(ms.map((m) => m.upgrades)),
    upgradeRatio: ratio(youMean((m) => m.upgrades), foeMean((m) => m.foe!.upgrades)),
    crownAvgSec: mean(crowns),
    crownRate: share(ms, (m) => typeof m.ageTimes[2] === "number" && m.ageTimes[2] > 0),
    empireRate: share(ms, (m) => m.age >= 3),
    kd: lost ? kills / lost : kills,
    foeKd: foeLost ? foeKills / foeLost : foeKills,
    combatPerMin: minutes ? (kills + lost) / minutes : 0,
    combatRatio: ratio(youMean((m) => (m.kills + m.losses) / Math.max(1, m.durationSec / 60)), foeMean((m) => (m.foe!.kills + m.foe!.losses) / Math.max(1, m.durationSec / 60))),
    lateShare: share(ms, (m) => m.durationSec >= 2100),
    lateWinRate: share(late, (m) => m.won),
    earlyLossShare: share(losses, (m) => m.durationSec < 900),
    spendUnits: spent.u / spentAll,
    spendBuildings: spent.b / spentAll,
    spendTech: spent.t / spentAll,
    topFaction: facs[0]?.[0] ?? "",
    topFactionShare: n ? (facs[0]?.[1] ?? 0) / n : 0,
    factionsPlayed: facs.filter(([, c]) => c >= 2).length,
    topOath: oaths[0]?.[0] ?? "",
    topOathShare: sworn ? (oaths[0]?.[1] ?? 0) / sworn : 0,
    topMap,
    topMapShare: n ? onTopMap.length / n : 0,
    topMapWinRate: onTopMap.length ? onTopMap.filter((m) => m.won).length / onTopMap.length : 0,
  };
}

// -------------------------------------------------------------- archetypes --
export interface Evidence { label: string; value: string; /** How far toward the style's threshold (0–1). */ weight: number }

export interface Archetype {
  id: string;
  name: string;
  emblem: string;
  /** One line: what the style is. */
  short: string;
  /** The rule, in plain words, for the "every style" list. */
  criteria: string;
  score: (f: StyleFeatures) => number;
  /** A specific name for this player (e.g. "Man-at-Arms Rush"). */
  title?: (f: StyleFeatures) => string;
  /** How this player plays it, written from their numbers. */
  describe: (f: StyleFeatures) => string;
  evidence: (f: StyleFeatures) => Evidence[];
  strengths: string[];
  risks: string[];
  tip: string;
}

const ev = (label: string, value: string, weight: number): Evidence => ({ label, value, weight: Math.max(0, Math.min(1, weight)) });

export const ARCHETYPES: Archetype[] = [
  {
    id: "rush", name: "Rusher", emblem: "⚔",
    short: "Hits first and ends it early.",
    criteria: "Attacks before 7:00 in most games, and wins a large share of games inside 15 minutes.",
    score: (f) => 0.5 * ramp(f.rushRate, 0.25, 0.65) + 0.5 * ramp(f.winsBefore15, 0.25, 0.65),
    title: (f) => (f.openerWord ? `${f.openerWord} Rush` : "Rusher"),
    describe: (f) => `You go straight for the throat. Your first attack on their base lands before 7:00 in ${pct(f.rushRate)} of your games (median ${mmss(f.medianFirstHit)}), and ${pct(f.winsBefore15)} of your wins are over inside 15 minutes.${f.openerWord ? ` You open with ${f.openerWord === "Mixed" ? "a mixed force" : f.rushUnit ? `the ${f.openerWord} — ${pct(f.rushUnitShare)} of your early damage` : `${f.openerWord.toLowerCase()} units — ${pct(f.rushUnitShare)} of your early damage`}.` : ""} You win by denying your opponent the time to build anything worth defending.`,
    evidence: (f) => [
      ev("Games you attack before 7:00", pct(f.rushRate), ramp(f.rushRate, 0.25, 0.65)),
      ev("Wins inside 15 minutes", pct(f.winsBefore15), ramp(f.winsBefore15, 0.25, 0.65)),
      ev("Median first attack", mmss(f.medianFirstHit), 1 - ramp(f.medianFirstHit, 300, 720)),
    ],
    strengths: ["Punishes greedy economies", "Short games — more of them per evening"],
    risks: ["If the first push is held, you're behind in economy", "Walls and towers blunt it"],
    tip: "Scout before you commit: a walled opponent wants a different opener (rams, or a boom of your own).",
  },
  {
    id: "pressure", name: "Early Pressure, Late Finish", emblem: "⏳",
    short: "Hits early to slow them down, then wins the long game.",
    criteria: "Attacks before 7:00 in most games, but the games — and the wins — run past 30 minutes.",
    score: (f) => 0.45 * ramp(f.rushRate, 0.25, 0.65) + 0.55 * ramp(Math.max(f.lateShare, f.lateWins), 0.25, 0.6),
    title: (f) => (f.openerWord ? `${f.openerWord} Harass, Late Closer` : "Early Pressure, Late Finisher"),
    describe: (f) => `You attack early but you don't need the rush to end it. Your first attack on their base lands before 7:00 in ${pct(f.rushRate)} of your games${f.openerWord ? `, usually with ${f.openerWord === "Mixed" ? "a mixed force" : f.openerWord.toLowerCase() + (f.rushUnit ? "s" : " units")}` : ""}, yet ${pct(f.lateWins)} of your wins come after 30 minutes. The early hits cost them villagers and time; you cash that in with the stronger late army.`,
    evidence: (f) => [
      ev("Games you attack before 7:00", pct(f.rushRate), ramp(f.rushRate, 0.25, 0.65)),
      ev("Wins after 30 minutes", pct(f.lateWins), ramp(f.lateWins, 0.25, 0.6)),
      ev("Games past 35 minutes", pct(f.lateShare), ramp(f.lateShare, 0.25, 0.6)),
    ],
    strengths: ["Keeps the enemy off balance all game", "Doesn't fold if the first attack is held"],
    risks: ["Early losses that don't trade", "Over-investing in the harass and falling behind in eco"],
    tip: "Harass with as little as does the job — the late game is where you win, so every unit you don't lose early is one more at 30:00.",
  },
  {
    id: "turtle", name: "Turtle", emblem: "🏰",
    short: "Walls up, holds, and wins the long game.",
    criteria: "Builds half again the defences of their opponents (towers, walls, castles) and wins late — after 30–40 minutes.",
    score: (f) => 0.5 * ramp(f.winsAfter40 + 0.5 * f.wins30to40, 0.2, 0.55) + 0.5 * (f.defensesPerGame >= 3 ? ramp(f.defenseRatio, 1.2, 2.5) : 0),
    title: (f) => (f.winsAfter40 >= 0.4 ? "Turtle — plays for the late game" : "Turtle"),
    describe: (f) => `You make yourself hard to kill. You build ${f.defensesPerGame.toFixed(1)} tower-equivalents of defence a game — ${f.defenseRatio.toFixed(1)}× your opponents — and your wins come late: ${pct(f.winsAfter40)} after 40 minutes, ${pct(f.wins30to40)} between 30 and 40. You let the other side break itself on your walls, then win with the stronger late army.`,
    evidence: (f) => [
      ev("Defence built vs opponents", `${f.defenseRatio.toFixed(1)}×`, ramp(f.defenseRatio, 1.2, 2.5)),
      ev("Defence per game", f.defensesPerGame.toFixed(1), ramp(f.defensesPerGame, 3, 10)),
      ev("Wins after 40 minutes", pct(f.winsAfter40), ramp(f.winsAfter40, 0.2, 0.55)),
    ],
    strengths: ["Survives rushes", "Strong when the game goes long"],
    risks: ["Gives away map control and resources", "A booming opponent can out-scale you"],
    tip: "Walls buy time — spend it booming. A turtle that doesn't also out-gather loses slowly instead of quickly.",
  },
  {
    id: "late", name: "Late-Game Specialist", emblem: "⌛",
    short: "Wants the game to go long, and wins it when it does.",
    criteria: "A large share of games run past 35 minutes, reaches the Empire Age often, and wins most long games.",
    score: (f) => 0.35 * ramp(f.lateShare, 0.25, 0.6) + 0.35 * ramp(f.empireRate, 0.15, 0.5) + 0.3 * ramp(f.lateWinRate, 0.5, 0.75),
    describe: (f) => `Your games run long — ${pct(f.lateShare)} go past 35 minutes — and you reach the Empire Age in ${pct(f.empireRate)} of them. In games that last, you win ${pct(f.lateWinRate)}. You play the full tech tree and win with the army nobody can field in the Banner Age.`,
    evidence: (f) => [
      ev("Games past 35 minutes", pct(f.lateShare), ramp(f.lateShare, 0.25, 0.6)),
      ev("Reach the Empire Age", pct(f.empireRate), ramp(f.empireRate, 0.15, 0.5)),
      ev("Win rate in 30+ minute games", pct(f.lateWinRate), ramp(f.lateWinRate, 0.5, 0.75)),
    ],
    strengths: ["Full upgrades, best units", "Composed when behind early"],
    risks: ["Vulnerable before the power spike", "Long games are long"],
    tip: "Know your danger window: the minutes before your Crown/Empire spike. Defend it and you're favourite.",
  },
  {
    id: "boom", name: "Boomer", emblem: "🌾",
    short: "Out-gathers everyone, then spends it.",
    criteria: "More villagers and a faster income than the opponents faced, with a late first attack.",
    score: (f) => (0.5 * ramp(f.villagerRatio, 1.05, 1.35) + 0.5 * ramp(f.gatherRatio, 1.05, 1.4)) * (0.4 + 0.6 * ramp(f.medianFirstHit, 360, 720)),
    describe: (f) => `You win the economy first. You peak at ${Math.round(f.peakVillagers)} villagers — ${f.villagerRatio.toFixed(2)}× your opponents — and gather ${Math.round(f.gatherPerMin)} resources a minute, ${f.gatherRatio.toFixed(2)}× theirs. Your first attack comes around ${mmss(f.medianFirstHit)}: by then you can afford to lose fights and keep coming.`,
    evidence: (f) => [
      ev("Peak villagers vs opponents", `${f.villagerRatio.toFixed(2)}×`, ramp(f.villagerRatio, 1.05, 1.35)),
      ev("Income vs opponents", `${f.gatherRatio.toFixed(2)}×`, ramp(f.gatherRatio, 1.05, 1.4)),
      ev("Median first attack", mmss(f.medianFirstHit), ramp(f.medianFirstHit, 360, 720)),
    ],
    strengths: ["Snowballs past the mid-game", "Can replace any army"],
    risks: ["Rushes and raids on your villagers", "Idle resources win nothing"],
    tip: "A boom's value is what you spend it on — if your bank keeps climbing, add production buildings.",
  },
  {
    id: "demolition", name: "Demolisher", emblem: "🔥",
    short: "Tears the enemy's town down.",
    criteria: "Razes well above the opponents' rate, and several buildings a game.",
    score: (f) => 0.6 * ramp(f.razedRatio, 1.2, 2.2) + 0.4 * ramp(f.razedPerGame, 3, 10),
    title: (f) => (f.lateShare >= 0.35 || f.winsAfter40 + f.wins30to40 >= 0.45 ? "Siege Grinder — longevity & demolition" : f.winsBefore15 >= 0.4 ? "Burner — raze and run" : "Demolisher"),
    describe: (f) => `You win by what you break. You raze ${f.razedPerGame.toFixed(1)} buildings a game — ${f.razedRatio.toFixed(1)}× the opponents you've faced${f.classShare.siege >= 0.1 ? `, with siege making up ${pct(f.classShare.siege)} of your army` : ""}. You don't need to win every fight: once the production buildings and the Town Center are gone, the fights stop mattering.`,
    evidence: (f) => [
      ev("Buildings razed vs opponents", `${f.razedRatio.toFixed(1)}×`, ramp(f.razedRatio, 1.2, 2.2)),
      ev("Buildings razed per game", f.razedPerGame.toFixed(1), ramp(f.razedPerGame, 3, 10)),
      ev("Siege in your army", pct(f.classShare.siege), ramp(f.classShare.siege, 0.05, 0.2)),
    ],
    strengths: ["Ends games decisively", "Makes the enemy fight where you choose"],
    risks: ["Siege dies to cavalry without escort", "Slow to reach"],
    tip: "Escort your siege: a few spearmen behind the rams stop the cavalry sally that kills most sieges.",
  },
  {
    id: "raid", name: "Raider", emblem: "🐎",
    short: "Hunts villagers, never stands still.",
    criteria: "Kills far more enemy villagers than opponents kill of theirs, with a fast (cavalry/archer) army.",
    score: (f) => 0.6 * ramp(f.raidRatio, 1.2, 2.5) + 0.4 * ramp(f.classShare.cavalry + f.classShare.archer * 0.5, 0.25, 0.6),
    describe: (f) => `You fight the economy, not the army. You kill ${f.raidPerGame.toFixed(1)} enemy villagers a game — ${f.raidRatio.toFixed(1)}× what your opponents manage against you — with an army that's ${pct(f.classShare.cavalry)} cavalry and ${pct(f.classShare.archer)} archers. Every villager you catch is income they never get back.`,
    evidence: (f) => [
      ev("Villager kills vs opponents", `${f.raidRatio.toFixed(1)}×`, ramp(f.raidRatio, 1.2, 2.5)),
      ev("Villagers killed per game", f.raidPerGame.toFixed(1), ramp(f.raidPerGame, 4, 15)),
      ev("Cavalry in your army", pct(f.classShare.cavalry), ramp(f.classShare.cavalry, 0.2, 0.5)),
    ],
    strengths: ["Cripples economies without a big fight", "Forces the enemy to defend everywhere"],
    risks: ["Spearmen and walls", "Raiding while your own army is caught out"],
    tip: "Hit two places at once — a single raid gets answered, two get one of them through.",
  },
  {
    id: "tech", name: "Tech Master", emblem: "📜",
    short: "Ages up fast and out-researches everyone.",
    criteria: "Reaches the Crown Age early and researches half again what opponents do.",
    score: (f) => 0.5 * ramp(f.upgradeRatio, 1.1, 1.6) + 0.5 * (f.crownRate > 0.3 ? 1 - ramp(f.crownAvgSec, 780, 1200) : 0),
    title: (f) => (f.crownAvgSec > 0 && f.crownAvgSec <= 840 ? "Fast Crown" : "Tech Master"),
    describe: (f) => `You win with quality. You research ${f.upgradesPerGame.toFixed(1)} technologies a game — ${f.upgradeRatio.toFixed(2)}× your opponents — and reach the Crown Age ${f.crownAvgSec ? `at ${mmss(f.crownAvgSec)} on average` : "often"}. Every soldier you field is better than the one it meets.`,
    evidence: (f) => [
      ev("Research vs opponents", `${f.upgradeRatio.toFixed(2)}×`, ramp(f.upgradeRatio, 1.1, 1.6)),
      ev("Average time to the Crown Age", f.crownAvgSec ? mmss(f.crownAvgSec) : "—", f.crownAvgSec ? 1 - ramp(f.crownAvgSec, 780, 1200) : 0),
      ev("Spent on technology", pct(f.spendTech), ramp(f.spendTech, 0.12, 0.3)),
    ],
    strengths: ["Upgrades win even fights", "Unlocks the best units first"],
    risks: ["A small army while you tech", "Early aggression"],
    tip: "The age-up is a timing: hide it behind a few defenders, then hit with the new units the moment they land.",
  },
  {
    id: "brawler", name: "Brawler", emblem: "🛡",
    short: "Fights constantly and trades efficiently.",
    criteria: "Far more combat per minute than opponents, trading at a good ratio.",
    score: (f) => 0.5 * ramp(f.combatRatio, 1.1, 1.7) + 0.5 * ramp(f.kd, 1.0, 1.8),
    title: (f) => (f.kd >= 1.8 ? "Efficient Brawler" : "Brawler"),
    describe: (f) => `You want the fight, and you win it. There are ${f.combatPerMin.toFixed(1)} kills-plus-losses a minute in your games — ${f.combatRatio.toFixed(2)}× your opponents — and you trade at ${f.kd.toFixed(2)} kills per loss (they manage ${f.foeKd.toFixed(2)}). You grind armies down rather than going around them.`,
    evidence: (f) => [
      ev("Combat per minute vs opponents", `${f.combatRatio.toFixed(2)}×`, ramp(f.combatRatio, 1.1, 1.7)),
      ev("Kills per loss", f.kd.toFixed(2), ramp(f.kd, 1.0, 1.8)),
      ev("Opponents' kills per loss", f.foeKd.toFixed(2), 1 - ramp(f.foeKd, 0.6, 1.2)),
    ],
    strengths: ["Wins the army battle", "Hard to raid — you're always there"],
    risks: ["Costly if the trades turn", "Can neglect the economy"],
    tip: "Fight near your towers and castle: the same trade is much better inside your own arrows.",
  },
];

// ------------------------------------------------------------------ traits --
export interface Trait { id: string; name: string; detail: string }

export function traitsOf(f: StyleFeatures): Trait[] {
  const t: Trait[] = [];
  const c = f.classShare;
  if (c.cavalry >= 0.45) t.push({ id: "cav", name: "Cavalry Commander", detail: `${pct(c.cavalry)} of your army is mounted.` });
  if (c.archer >= 0.45) t.push({ id: "archer", name: "Archer General", detail: `${pct(c.archer)} of your army shoots.` });
  if (c.infantry >= 0.55) t.push({ id: "inf", name: "Shield Wall", detail: `${pct(c.infantry)} of your army is infantry.` });
  if (c.siege >= 0.15) t.push({ id: "siege", name: "Siege Engineer", detail: `${pct(c.siege)} of your army is siege.` });
  if (f.diversity >= 4) t.push({ id: "combined", name: "Combined Arms", detail: `You field ${f.diversity.toFixed(1)} unit types' worth of variety.` });
  else if (f.topUnit && f.topUnitShare >= 0.6) t.push({ id: "onetrick", name: `One-Trick: ${unitName(f.topUnit)}`, detail: `${pct(f.topUnitShare)} of everything you train is the ${unitName(f.topUnit)}.` });
  if (f.games >= 5 && f.topFactionShare >= 0.7) t.push({ id: "specialist", name: `${facName(f.topFaction).replace(/^The /, "")} Specialist`, detail: `${pct(f.topFactionShare)} of your games are as ${facName(f.topFaction)}.` });
  else if (f.factionsPlayed >= 4 && f.topFactionShare <= 0.4) t.push({ id: "chameleon", name: "Chameleon", detail: `You play ${f.factionsPlayed} factions regularly and none dominates.` });
  if (f.topOath && f.topOathShare >= 0.75) t.push({ id: "oath", name: `Oathbound: ${OATHS[f.topOath]?.short ?? f.topOath}`, detail: `You swear the ${OATHS[f.topOath]?.name ?? f.topOath} in ${pct(f.topOathShare)} of games you swear at all.` });
  if (f.games >= 6 && f.topMapShare >= 0.4 && f.topMapWinRate >= f.winRate + 0.1) t.push({ id: "map", name: `At home on ${f.topMap}`, detail: `${pct(f.topMapWinRate)} wins there, against ${pct(f.winRate)} overall.` });
  if (f.kd >= 1.8) t.push({ id: "trader", name: "Efficient Trader", detail: `${f.kd.toFixed(2)} kills for every unit lost.` });
  if (f.lateShare >= 0.2 && f.lateWinRate >= 0.65) t.push({ id: "closer", name: "Closer", detail: `You win ${pct(f.lateWinRate)} of games that go past 35 minutes.` });
  if (f.earlyLossShare >= 0.5 && f.games - f.wins >= 3) t.push({ id: "vulnerable", name: "Rush-vulnerable", detail: `${pct(f.earlyLossShare)} of your losses come inside 15 minutes.` });
  if (f.spendBuildings >= 0.45) t.push({ id: "builder", name: "Master Builder", detail: `${pct(f.spendBuildings)} of your spending goes on buildings.` });
  return t;
}

// ---------------------------------------------------------------- the read --
export interface StyleScore { archetype: Archetype; score: number }
export interface WinKey { label: string; win: string; loss: string; change: number; sentence: string }

export interface PlaystyleProfile {
  enough: boolean;
  games: number;
  confidence: "none" | "low" | "medium" | "high";
  primary: StyleScore | null;
  /** The primary's specific name ("Man-at-Arms Rush", "Siege Grinder"). */
  title: string;
  description: string;
  secondary: StyleScore | null;
  scores: StyleScore[];
  traits: Trait[];
  features: StyleFeatures;
  /** How your matches go, game by game. */
  matchMix: { style: MatchStyle; share: number; wins: number; games: number }[];
  /** What's different about the games you win. */
  winKeys: WinKey[];
  /** Short words that sum up how you play, built from your numbers. */
  keywords: string[];
  /** What you do well, and what's costing you games. */
  good: string[];
  bad: string[];
  /** Per faction: the style you most often play it with. */
  byFaction: { faction: string; games: number; style: MatchStyle; share: number; winRate: number }[];
}

export const MIN_GAMES = 5;

export function analyse(ms: CareerMatch[]): PlaystyleProfile {
  const f = features(ms);
  const scores = ARCHETYPES.map((a) => ({ archetype: a, score: f.games ? a.score(f) : 0 })).sort((a, b) => b.score - a.score);
  const enough = f.games >= MIN_GAMES;
  const top = scores[0];
  const primary = enough && top && top.score >= 0.35 ? top : null;
  const second = primary && scores[1] && scores[1].score >= 0.35 ? scores[1] : null;
  let title = primary ? primary.archetype.title?.(f) ?? primary.archetype.name : enough ? "All-Rounder" : "Still finding out";
  // Two strong styles together have their own names.
  if (primary && second) {
    const pair = [primary.archetype.id, second.archetype.id].sort().join("+");
    const combo: Record<string, string> = {
      "raid+rush": "Raiding Rush", "boom+turtle": "Fortress Boom", "demolition+late": "Siege Grinder — longevity & demolition",
      "demolition+turtle": "Siege Grinder — longevity & demolition", "brawler+rush": "Relentless Aggressor", "boom+tech": "Economic Powerhouse",
      "late+turtle": "Turtle — plays for the late game", "boom+late": "Late-Game Boomer", "raid+tech": "Tech Raider",
    };
    if (combo[pair]) title = combo[pair];
  }
  const description = primary
    ? primary.archetype.describe(f) + (second ? ` There's a strong ${second.archetype.name.toLowerCase()} streak too: ${second.archetype.short.charAt(0).toLowerCase()}${second.archetype.short.slice(1)}` : "")
    : enough
      ? `No single approach dominates your ${f.games} games — you adapt: rushing, booming and defending as the game asks. Your wins average ${mmss(f.avgWinSec)}.`
      : `Play ${MIN_GAMES - f.games} more game${MIN_GAMES - f.games === 1 ? "" : "s"} and your style will show up here.`;

  // Match-by-match mix.
  const mix = new Map<MatchStyle, { games: number; wins: number }>();
  for (const m of ms) { const s = matchStyle(m); const x = mix.get(s) ?? { games: 0, wins: 0 }; x.games++; if (m.won) x.wins++; mix.set(s, x); }
  const matchMix = [...mix.entries()].map(([style, x]) => ({ style, share: x.games / Math.max(1, ms.length), wins: x.wins, games: x.games })).sort((a, b) => b.games - a.games);

  // What your wins have that your losses don't.
  const wins = ms.filter((m) => m.won), losses = ms.filter((m) => !m.won);
  const winKeys: WinKey[] = [];
  if (wins.length >= 3 && losses.length >= 3) {
    const cand: { label: string; f: (m: CareerMatch) => number | null; fmt: (x: number) => string; higher: string; lower: string }[] = [
      { label: "Peak villagers", f: (m) => m.peakVillagers, fmt: (x) => Math.round(x).toString(), higher: "more villagers at your peak", lower: "fewer villagers at your peak" },
      { label: "Income per minute", f: gatherPerMin, fmt: (x) => Math.round(x).toString(), higher: "more income a minute", lower: "less income a minute" },
      { label: "Peak army", f: (m) => m.peakArmy, fmt: (x) => Math.round(x).toString(), higher: "a bigger army at its peak", lower: "a smaller army at its peak" },
      { label: "Technologies", f: (m) => m.upgrades, fmt: (x) => x.toFixed(1), higher: "more research", lower: "less research" },
      { label: "First attack", f: (m) => (attackAt(m) >= 0 ? attackAt(m) : null), fmt: mmss, higher: "a later first attack", lower: "an earlier first attack" },
      { label: "Crown Age at", f: (m) => (typeof m.ageTimes[2] === "number" && m.ageTimes[2] > 0 ? m.ageTimes[2] : null), fmt: mmss, higher: "a later Crown Age", lower: "an earlier Crown Age" },
      { label: "Buildings razed", f: (m) => m.razed, fmt: (x) => x.toFixed(1), higher: "more buildings razed", lower: "fewer buildings razed" },
      { label: "Defence built", f: (m) => defensesBuilt(m.built), fmt: (x) => x.toFixed(1), higher: "more defences built", lower: "fewer defences built" },
      { label: "Town Center idle", f: (m) => m.tcIdleShare, fmt: pct, higher: "your Town Center idle more", lower: "your Town Center idle less" },
    ];
    for (const c of cand) {
      const w = wins.map(c.f).filter((x): x is number => x !== null), l = losses.map(c.f).filter((x): x is number => x !== null);
      if (w.length < 3 || l.length < 3) continue;
      const wm = mean(w), lm = mean(l);
      const change = lm !== 0 ? (wm - lm) / Math.abs(lm) : wm > 0 ? 1 : 0;
      if (Math.abs(change) < 0.1) continue;
      winKeys.push({ label: c.label, win: c.fmt(wm), loss: c.fmt(lm), change,
        sentence: `In your wins you have ${c.label === "Town Center idle" ? "" : `${Math.round(Math.abs(change) * 100)}% `}${change > 0 ? c.higher : c.lower} (${c.fmt(wm)} vs ${c.fmt(lm)}).` });
    }
    winKeys.sort((a, b) => Math.abs(b.change) - Math.abs(a.change));
  }

  // Style by faction.
  const byFaction: PlaystyleProfile["byFaction"] = [];
  const facs = [...new Set(ms.map((m) => m.faction).filter(Boolean))];
  for (const fac of facs) {
    const games = ms.filter((m) => m.faction === fac);
    if (games.length < 3) continue;
    const c = new Map<MatchStyle, number>();
    for (const m of games) c.set(matchStyle(m), (c.get(matchStyle(m)) ?? 0) + 1);
    const [style, k] = [...c.entries()].sort((a, b) => b[1] - a[1])[0];
    byFaction.push({ faction: fac, games: games.length, style, share: k / games.length, winRate: games.filter((m) => m.won).length / games.length });
  }
  byFaction.sort((a, b) => b.games - a.games);

  return {
    enough, games: f.games,
    confidence: f.games < MIN_GAMES ? "none" : f.games < 10 ? "low" : f.games < 25 ? "medium" : "high",
    primary, title, description, secondary: second, scores, traits: enough ? traitsOf(f) : [], features: f,
    matchMix, winKeys: winKeys.slice(0, 4), byFaction,
    keywords: enough ? keywordsOf(f) : [], ...(enough ? careerFeedback(f) : { good: [], bad: [] }),
  };
}

// ---------------------------------------------------------------- keywords --
/**
 * The words the style is built from. Tempo (when you attack), make-up (what
 * with), length (when your games end) and the things you stand out at — so
 * "Early attacker · Archer · Long games" is possible, not only the stock titles.
 */
export function keywordsOf(f: StyleFeatures): string[] {
  const k: string[] = [];
  if (f.rushRate >= 0.5) k.push("Early attacker");
  else if (f.medianFirstHit >= 900) k.push("Slow starter");
  else k.push("Mid-game attacker");
  if (f.openerWord && f.rushRate >= 0.25) k.push(`${f.openerWord} opener`);
  const c = f.classShare;
  const top = (Object.entries(c) as [UnitClass, number][]).sort((a, b) => b[1] - a[1])[0];
  if (top && top[1] >= 0.45) k.push(`${CLASS_WORD[top[0]]} army`);
  else if (f.diversity >= 3.5) k.push("Combined arms");
  if (f.winsBefore15 >= 0.5) k.push("Short games");
  else if (f.lateWins >= 0.5 || f.lateShare >= 0.4) k.push("Long games");
  if (f.razedRatio >= 1.4 && f.razedPerGame >= 3) k.push("Demolition");
  if (f.raidRatio >= 1.4 && f.raidPerGame >= 4) k.push("Villager hunter");
  if (f.defenseRatio >= 1.4 && f.defensesPerGame >= 3) k.push("Fortifier");
  if (f.gatherRatio >= 1.15) k.push("Strong economy");
  if (f.upgradeRatio >= 1.3) k.push("Tech-heavy");
  if (f.kd >= 1.5) k.push("Efficient fighter");
  return k;
}

// ---------------------------------------------------------------- feedback --
/** What the record says you do well, and what's losing you games. */
export function careerFeedback(f: StyleFeatures): { good: string[]; bad: string[] } {
  const good: string[] = [], bad: string[] = [];
  if (f.gatherRatio >= 1.1) good.push(`Your economy out-gathers your opponents by ${pct(f.gatherRatio - 1)}.`);
  else if (f.gatherRatio <= 0.9) bad.push(`You gather ${pct(1 - f.gatherRatio)} less than your opponents — more villagers, fewer idle ones.`);
  if (f.tcIdle <= 0.12) good.push(`Your Town Center is almost never idle (${pct(f.tcIdle)}).`);
  else if (f.tcIdle >= 0.3) bad.push(`Your Town Center sits idle ${pct(f.tcIdle)} of the time — keep villagers queued.`);
  if (f.kd >= 1.3) good.push(`You trade well: ${f.kd.toFixed(2)} kills per loss.`);
  else if (f.kd <= 0.8 && f.games >= 5) bad.push(`You lose more units than you kill (${f.kd.toFixed(2)} per loss) — fight near your towers or with the counter unit.`);
  if (f.rushedGames >= 3) {
    if (f.heldRushRate >= 0.6) good.push(`You hold early attacks: ${pct(f.heldRushRate)} wins when rushed.`);
    else if (f.heldRushRate <= 0.35) bad.push(`Early attacks beat you — ${pct(f.heldRushRate)} wins in ${f.rushedGames} games where you were rushed.`);
  }
  if (f.earlyLossShare >= 0.5 && f.games - f.wins >= 3) bad.push(`${pct(f.earlyLossShare)} of your losses are over inside 15 minutes.`);
  if (f.lateShare >= 0.2 && f.lateWinRate >= 0.6) good.push(`You close long games: ${pct(f.lateWinRate)} wins past 30 minutes.`);
  else if (f.lateShare >= 0.2 && f.lateWinRate <= 0.35) bad.push(`Long games slip away — ${pct(f.lateWinRate)} wins past 30 minutes.`);
  if (f.upgradeRatio >= 1.25) good.push(`You out-research your opponents (${f.upgradeRatio.toFixed(1)}×).`);
  else if (f.upgradeRatio <= 0.75) bad.push(`You research less than your opponents (${f.upgradeRatio.toFixed(1)}×) — blacksmith upgrades win even fights.`);
  if (f.crownRate >= 0.3 && f.crownAvgSec > 0 && f.crownAvgSec >= 1260) bad.push(`You reach the Crown Age late (${mmss(f.crownAvgSec)} on average).`);
  return { good: good.slice(0, 4), bad: bad.slice(0, 4) };
}
