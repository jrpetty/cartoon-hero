// Career: every match you play, offline or online, summed up for good.
//
// The match history (history.ts) keeps the last twenty games for trends. This
// is the other half — the numbers a player looks up about themselves: how often
// each faction wins for them, how fast, on which maps, which unit they lean on,
// which enemy faction gives them trouble.
//
// Two stores, both in this browser:
//   • a *log* of the last LOG_LIMIT matches, each a compact CareerMatch — what
//     the filtered views (skirmish only, online only, ranked only) and the
//     recent-matches list are built from;
//   • lifetime *totals*, updated match by match and never trimmed, so "all
//     time" stays exact however long someone plays.
// Both are the same shape of numbers (a Career), so every screen reads one
// thing: `fold` builds it from a list, `absorb` adds one match to it.

import type { MatchReport, PlayerReport } from "../sim/metrics";

const LOG_KEY = "bb_career_log_v1";
const TOTALS_KEY = "bb_career_totals_v1";
export const LOG_LIMIT = 400;

export type MatchKind = "skirmish" | "online";

/** One match, from your side of it. */
export interface CareerMatch {
  at: number;
  kind: MatchKind;
  ranked: boolean;
  mode: string;
  /** AI difficulty for skirmish; "" online. */
  difficulty: string;
  won: boolean;
  faction: string;
  commander: string;
  oaths: string[];
  map: string;
  players: number;
  /** "1 v 1", "2 v 2", "4-player FFA"… */
  format: string;
  allies: string[];
  foes: string[];
  durationSec: number;
  /** When you were knocked out, or -1. */
  defeatedAt: number;
  age: number;
  /** Seconds each age was reached (index 0 = start). */
  ageTimes: number[];
  score: number;
  gathered: number;
  gatheredBy: { food: number; wood: number; gold: number };
  spent: number;
  kills: number;
  losses: number;
  razed: number;
  buildingsLost: number;
  damageDealt: number;
  damageTaken: number;
  peakArmy: number;
  peakVillagers: number;
  idleVillagerTime: number;
  /** Share of the match your Town Centres sat idle (0–1). */
  tcIdleShare: number;
  upgrades: number;
  trained: Record<string, number>;
  killed: Record<string, number>;
  lost: Record<string, number>;
  built: Record<string, number>;
  /** By your unit type: enemies it killed, damage it dealt, buildings it razed. Absent on older records. */
  unitKills?: Record<string, number>;
  unitDamage?: Record<string, number>;
  unitRazed?: Record<string, number>;
  /** Seconds to your side's first hit on an enemy, first kill, first razed building (-1 never). Absent on older records. */
  firstHitAt?: number;
  firstKillAt?: number;
  firstRazeAt?: number;
  /** What you spent it on. */
  spentOn?: { units: number; buildings: number; tech: number };
  /**
   * The average enemy realm in this match — what "more than usual" is measured
   * against: the people you actually played, not a made-up norm.
   */
  foe?: { gathered: number; razed: number; kills: number; losses: number; peakArmy: number; peakVillagers: number; villagerKills: number; upgrades: number; defenses: number; age: number };
}

/**
 * How much defence a realm built, in "tower equivalents": a wall run is many
 * cheap segments, so segments count a quarter each; a castle counts four.
 */
export const DEFENSE_WEIGHT: Record<string, number> = { palisade: 0.25, stone_wall: 0.25, gate: 1, watch_tower: 2, watchfire: 1, castle: 4 };
export const defensesBuilt = (built: Record<string, number>) =>
  Math.round(Object.entries(DEFENSE_WEIGHT).reduce((n, [b, w]) => n + (built[b] ?? 0) * w, 0) * 10) / 10;

/** One unit type across a career. */
export interface UnitTally {
  trained: number;
  /** Your units of this type that died. */
  lost: number;
  /** Enemy units of this type you killed (whatever did it). */
  killed: number;
  /** Enemy units this type killed. */
  kills: number;
  damage: number;
  razed: number;
  /** Matches you trained at least one. */
  games: number;
  wins: number;
}

/** Played / won and the numbers that go with them, for any slice. */
export interface Tally {
  played: number;
  won: number;
  secs: number;
  winSecs: number;
  /** Fastest win in seconds (0 = no win yet). */
  fastestWin: number;
  longest: number;
  kills: number;
  losses: number;
  gathered: number;
  /** Units trained with this slice, for "favourite unit with the Legion". */
  units: Record<string, number>;
}

export interface Career {
  since: number;
  last: number;
  all: Tally;
  byKind: Record<string, Tally>;
  byFaction: Record<string, Tally>;
  byMap: Record<string, Tally>;
  byMode: Record<string, Tally>;
  byDifficulty: Record<string, Tally>;
  byCommander: Record<string, Tally>;
  byOath: Record<string, Tally>;
  byFormat: Record<string, Tally>;
  /** Your record against each enemy faction (per match it appeared in). */
  vsFaction: Record<string, Tally>;
  /** With each ally faction beside you. */
  withFaction: Record<string, Tally>;
  units: Record<string, UnitTally>;
  buildings: Record<string, number>;
  resources: { food: number; wood: number; gold: number };
  damageDealt: number;
  damageTaken: number;
  razed: number;
  buildingsLost: number;
  upgrades: number;
  bestArmy: number;
  bestScore: number;
  /** How many matches reached each age, and the summed time it took. */
  ageReached: number[];
  ageTimeSum: number[];
  idleVillagerTime: number;
  tcIdleShareSum: number;
  streak: number;
  bestStreak: number;
  worstStreak: number;
  losingRun: number;
}

const tally = (): Tally => ({ played: 0, won: 0, secs: 0, winSecs: 0, fastestWin: 0, longest: 0, kills: 0, losses: 0, gathered: 0, units: {} });

export function emptyCareer(): Career {
  return {
    since: 0, last: 0, all: tally(), byKind: {}, byFaction: {}, byMap: {}, byMode: {}, byDifficulty: {}, byCommander: {},
    byOath: {}, byFormat: {}, vsFaction: {}, withFaction: {}, units: {}, buildings: {}, resources: { food: 0, wood: 0, gold: 0 },
    damageDealt: 0, damageTaken: 0, razed: 0, buildingsLost: 0, upgrades: 0, bestArmy: 0, bestScore: 0,
    ageReached: [0, 0, 0, 0], ageTimeSum: [0, 0, 0, 0], idleVillagerTime: 0, tcIdleShareSum: 0,
    streak: 0, bestStreak: 0, worstStreak: 0, losingRun: 0,
  };
}

function addTo(t: Tally, m: CareerMatch) {
  t.played++;
  t.secs += m.durationSec;
  t.kills += m.kills;
  t.losses += m.losses;
  t.gathered += m.gathered;
  t.longest = Math.max(t.longest, m.durationSec);
  if (m.won) {
    t.won++;
    t.winSecs += m.durationSec;
    t.fastestWin = t.fastestWin ? Math.min(t.fastestWin, m.durationSec) : m.durationSec;
  }
  for (const [u, n] of Object.entries(m.trained)) if (u !== "villager") t.units[u] = (t.units[u] ?? 0) + n;
}

const slot = (rec: Record<string, Tally>, key: string) => (rec[key] ??= tally());

/** Add one match to a career. */
export function absorb(c: Career, m: CareerMatch): Career {
  c.since = c.since ? Math.min(c.since, m.at) : m.at;
  c.last = Math.max(c.last, m.at);
  addTo(c.all, m);
  addTo(slot(c.byKind, m.kind), m);
  if (m.ranked) addTo(slot(c.byKind, "ranked"), m);
  if (m.faction) addTo(slot(c.byFaction, m.faction), m);
  if (m.map) addTo(slot(c.byMap, m.map), m);
  if (m.mode) addTo(slot(c.byMode, m.mode), m);
  if (m.difficulty) addTo(slot(c.byDifficulty, m.difficulty), m);
  if (m.commander) addTo(slot(c.byCommander, m.commander), m);
  if (m.format) addTo(slot(c.byFormat, m.format), m);
  for (const o of new Set(m.oaths)) addTo(slot(c.byOath, o), m);
  for (const f of new Set(m.foes)) addTo(slot(c.vsFaction, f), m);
  for (const f of new Set(m.allies)) addTo(slot(c.withFaction, f), m);
  const unit = (u: string) => {
    const t = (c.units[u] ??= { trained: 0, killed: 0, lost: 0, kills: 0, damage: 0, razed: 0, games: 0, wins: 0 });
    // Older saved totals predate the per-unit fields.
    t.kills ??= 0; t.damage ??= 0; t.razed ??= 0; t.games ??= 0; t.wins ??= 0;
    return t;
  };
  for (const [u, n] of Object.entries(m.trained)) {
    const t = unit(u);
    t.trained += n;
    if (n > 0) { t.games++; if (m.won) t.wins++; }
  }
  for (const [u, n] of Object.entries(m.killed)) unit(u).killed += n;
  for (const [u, n] of Object.entries(m.lost)) unit(u).lost += n;
  for (const [u, n] of Object.entries(m.unitKills ?? {})) unit(u).kills += n;
  for (const [u, n] of Object.entries(m.unitDamage ?? {})) unit(u).damage += n;
  for (const [u, n] of Object.entries(m.unitRazed ?? {})) unit(u).razed += n;
  for (const [b, n] of Object.entries(m.built)) c.buildings[b] = (c.buildings[b] ?? 0) + n;
  c.resources.food += m.gatheredBy.food;
  c.resources.wood += m.gatheredBy.wood;
  c.resources.gold += m.gatheredBy.gold;
  c.damageDealt += m.damageDealt;
  c.damageTaken += m.damageTaken;
  c.razed += m.razed;
  c.buildingsLost += m.buildingsLost;
  c.upgrades += m.upgrades;
  c.bestArmy = Math.max(c.bestArmy, m.peakArmy);
  c.bestScore = Math.max(c.bestScore, m.score);
  for (let a = 1; a < m.ageTimes.length && a < c.ageReached.length; a++) {
    if (m.ageTimes[a] === undefined || m.ageTimes[a] === null) continue;
    c.ageReached[a]++;
    c.ageTimeSum[a] += m.ageTimes[a];
  }
  c.ageReached[0]++;
  c.idleVillagerTime += m.idleVillagerTime;
  c.tcIdleShareSum += m.tcIdleShare;
  // Streaks run in the order matches are absorbed (oldest first).
  if (m.won) { c.streak = c.streak > 0 ? c.streak + 1 : 1; c.losingRun = 0; }
  else { c.streak = c.streak < 0 ? c.streak - 1 : -1; c.losingRun++; }
  c.bestStreak = Math.max(c.bestStreak, c.streak);
  c.worstStreak = Math.max(c.worstStreak, -Math.min(0, c.streak));
  return c;
}

/** A career from a list of matches (any order). */
export function fold(ms: CareerMatch[]): Career {
  const c = emptyCareer();
  for (const m of [...ms].sort((a, b) => a.at - b.at)) absorb(c, m);
  return c;
}

// ------------------------------------------------------------ reading it --

export const winRate = (t: Tally | undefined) => (t && t.played ? t.won / t.played : 0);
export const avgSecs = (t: Tally | undefined) => (t && t.played ? t.secs / t.played : 0);
export const avgWinSecs = (t: Tally | undefined) => (t && t.won ? t.winSecs / t.won : 0);
export const kd = (t: Tally | undefined) => (t ? (t.losses ? t.kills / t.losses : t.kills) : 0);

/** The key played most (ties: more wins, then alphabetical), or "". */
export function favourite(rec: Record<string, Tally>): string {
  let best = "", bp = -1, bw = -1;
  for (const [k, t] of Object.entries(rec)) {
    if (t.played > bp || (t.played === bp && t.won > bw)) { best = k; bp = t.played; bw = t.won; }
  }
  return best;
}

/** The unit trained most in a tally (villagers excluded), or "". */
export function favouriteUnitOf(t: Tally | undefined): string {
  let best = "", n = 0;
  for (const [u, k] of Object.entries(t?.units ?? {})) if (k > n) { best = u; n = k; }
  return best;
}

/** The unit with the most of `field` across the career. */
export function topUnit(c: Career, field: "trained" | "killed" | "lost" | "kills" | "damage" | "razed", skipVillager = true): string {
  let best = "", n = 0;
  for (const [u, v] of Object.entries(c.units)) {
    if (skipVillager && u === "villager") continue;
    if ((v[field] ?? 0) > n) { best = u; n = v[field] ?? 0; }
  }
  return best;
}

/** Kills per unit lost for a unit type (kills if it never died). */
export const unitKd = (v: UnitTally | undefined) => (!v ? 0 : v.lost ? (v.kills ?? 0) / v.lost : v.kills ?? 0);

/**
 * The unit that trades best: highest K/D among the ones trained enough to
 * count (villagers excluded — they're not meant to fight).
 */
export function mostEffectiveUnit(c: Career, minTrained = 10): string {
  let best = "", r = -1;
  for (const [u, v] of Object.entries(c.units)) {
    if (u === "villager" || v.trained < minTrained || !(v.kills ?? 0)) continue;
    const k = unitKd(v);
    if (k > r) { best = u; r = k; }
  }
  return best;
}

/** Best win rate among slices with at least `min` games (a "best faction"/"best map"). */
export function bestBy(rec: Record<string, Tally>, min = 3): string {
  let best = "", r = -1;
  for (const [k, t] of Object.entries(rec)) if (t.played >= min && winRate(t) > r) { best = k; r = winRate(t); }
  return best;
}
export function worstBy(rec: Record<string, Tally>, min = 3): string {
  let worst = "", r = 2;
  for (const [k, t] of Object.entries(rec)) if (t.played >= min && winRate(t) < r) { worst = k; r = winRate(t); }
  return worst;
}

// ---------------------------------------------------------- making one --

function formatOf(players: PlayerReport[]): string {
  const groups = new Map<number, number>();
  for (const p of players) if (!p.horde) groups.set(p.group, (groups.get(p.group) ?? 0) + 1);
  const sizes = [...groups.values()].sort((a, b) => b - a);
  if (!sizes.length) return "";
  if (sizes.every((n) => n === 1)) return sizes.length === 2 ? "1 v 1" : `${sizes.length}-player FFA`;
  return sizes.join(" v ");
}

/** The average enemy realm, from their per-realm reports. */
function foeAverage(foes: PlayerReport[]): CareerMatch["foe"] {
  if (!foes.length) return undefined;
  const avg = (f: (p: PlayerReport) => number) => Math.round((foes.reduce((a, p) => a + f(p), 0) / foes.length) * 10) / 10;
  return {
    gathered: avg((p) => p.gathered), razed: avg((p) => p.buildingsRazed), kills: avg((p) => p.unitsKilled), losses: avg((p) => p.unitsLost),
    peakArmy: avg((p) => p.peakArmy), peakVillagers: avg((p) => p.peakVillagers), villagerKills: avg((p) => p.killedByType.villager ?? 0),
    upgrades: avg((p) => p.upgrades), defenses: avg((p) => defensesBuilt(p.builtByType)), age: avg((p) => p.age),
  };
}

/** Your side of a finished match, from its end-of-match report. */
export function careerMatch(report: MatchReport, meta: {
  at: number; won: boolean; kind: MatchKind; ranked?: boolean; mode: string; difficulty: string; commander: string;
}): CareerMatch {
  const players = report.players ?? [];
  const me = players.find((p) => p.relation === "you");
  const side = me ?? report.you;
  const trained = { ...side.trainedByType };
  return {
    at: meta.at,
    kind: meta.kind,
    ranked: !!meta.ranked,
    mode: meta.mode,
    difficulty: meta.kind === "online" ? "" : meta.difficulty,
    won: meta.won,
    faction: me?.faction ?? "",
    commander: meta.kind === "online" ? "" : meta.commander,
    oaths: [...(me?.oaths ?? [])],
    map: report.mapName,
    players: players.filter((p) => !p.horde).length || 2,
    format: formatOf(players),
    allies: players.filter((p) => p.relation === "ally" && p.faction).map((p) => p.faction!),
    foes: players.filter((p) => p.relation === "enemy" && !p.horde && p.faction).map((p) => p.faction!),
    durationSec: Math.round(report.durationSec),
    defeatedAt: me?.defeatedAt ?? -1,
    age: side.age,
    ageTimes: (me?.ageTimes ?? [0]).map((t) => Math.round(t)),
    score: Math.round(side.score),
    gathered: Math.round(side.gathered),
    gatheredBy: { food: Math.round(side.gatheredBy.food), wood: Math.round(side.gatheredBy.wood), gold: Math.round(side.gatheredBy.gold) },
    spent: Math.round(side.spent),
    kills: side.unitsKilled,
    losses: side.unitsLost,
    razed: side.buildingsRazed,
    buildingsLost: side.buildingsLost,
    damageDealt: Math.round(side.damageDealt),
    damageTaken: Math.round(side.damageTaken),
    peakArmy: side.peakArmy,
    peakVillagers: side.peakVillagers,
    idleVillagerTime: Math.round(side.idleVillagerTime),
    tcIdleShare: side.tcSeconds > 0 ? Math.min(1, side.idleTcTime / side.tcSeconds) : 0,
    upgrades: side.upgrades,
    trained,
    killed: { ...side.killedByType },
    lost: { ...side.lostByType },
    built: { ...side.builtByType },
    unitKills: { ...(side.killsByUnit ?? {}) },
    unitDamage: Object.fromEntries(Object.entries(side.damageByUnit ?? {}).map(([k, v]) => [k, Math.round(v)])),
    unitRazed: { ...(side.razedByUnit ?? {}) },
    firstHitAt: Math.round(side.firstHitAt ?? -1),
    firstKillAt: Math.round(side.firstKillAt ?? -1),
    firstRazeAt: Math.round(side.firstRazeAt ?? -1),
    spentOn: { units: Math.round(side.spentOn.units), buildings: Math.round(side.spentOn.buildings), tech: Math.round(side.spentOn.tech) },
    foe: foeAverage(players.filter((p) => p.relation === "enemy" && !p.horde)),
  };
}

// ---------------------------------------------------------------- storage --

function store(): Storage | null {
  try { return typeof localStorage === "undefined" ? null : localStorage; } catch { return null; }
}

/** The recent matches, newest first. */
export function careerLog(): CareerMatch[] {
  try {
    const raw = store()?.getItem(LOG_KEY);
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr) ? arr.filter((m) => m && typeof m.at === "number").sort((a, b) => b.at - a.at) : [];
  } catch { return []; }
}

/** Lifetime totals (exact, never trimmed). */
export function careerTotals(): Career {
  try {
    const raw = store()?.getItem(TOTALS_KEY);
    if (!raw) return fold(careerLog());
    return { ...emptyCareer(), ...JSON.parse(raw) };
  } catch { return fold(careerLog()); }
}

/** Keep a finished match: on the log, and in the lifetime totals. */
export function recordCareer(m: CareerMatch): void {
  const s = store();
  if (!s) return;
  const totals = absorb(careerTotals(), m);
  const log = [m, ...careerLog()].slice(0, LOG_LIMIT);
  try { s.setItem(TOTALS_KEY, JSON.stringify(totals)); } catch { /* storage full — totals are small; nothing else to drop */ }
  for (let keep = log.length; keep > 0; keep = Math.floor(keep * 0.7)) {
    try { s.setItem(LOG_KEY, JSON.stringify(log.slice(0, keep))); return; } catch { /* full: keep fewer */ }
  }
}

export function clearCareer(): void {
  store()?.removeItem(LOG_KEY);
  store()?.removeItem(TOTALS_KEY);
}

export type CareerFilter = "all" | "skirmish" | "online" | "ranked";

/** The career for a filter: lifetime totals for "all", else folded from the log. */
export function careerFor(filter: CareerFilter): { career: Career; matches: CareerMatch[]; exact: boolean } {
  const log = careerLog();
  if (filter === "all") return { career: careerTotals(), matches: log, exact: true };
  const ms = log.filter((m) => (filter === "ranked" ? m.ranked : m.kind === filter));
  return { career: fold(ms), matches: ms, exact: log.length < LOG_LIMIT };
}
