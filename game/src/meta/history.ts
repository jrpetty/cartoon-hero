// Match history: the last twenty games you played, kept.
//
// The deep end-of-match report already exists and is thrown away the moment you
// press Continue. That is a shame twice over — you can't compare a game against
// the one before it, and you can't see whether the thing you have been working
// on is actually working. A record over time says more than any single screen.
//
// What is stored is a *summary*, not the full report: twenty complete
// MatchReports with their per-unit-type maps would be a few hundred kilobytes of
// localStorage, and nothing on the history screen needs that resolution. The
// fields here are the ones a trend is made of.

import type { MatchReport } from "../sim/metrics";

const STORAGE_KEY = "banner_and_blade_history_v1";

/** How many games we keep. Twenty is roughly an evening, and bounds the store. */
export const HISTORY_LIMIT = 20;

export interface MatchRecord {
  /** Epoch millis. The only clock the history screen has. */
  at: number;
  won: boolean;
  mapName: string;
  mode: string;
  difficulty: string;
  players: number;
  durationSec: number;
  /** Final score, yours and theirs. */
  score: number;
  foeScore: number;
  gathered: number;
  spent: number;
  unitsKilled: number;
  unitsLost: number;
  buildingsRazed: number;
  buildingsLost: number;
  peakArmy: number;
  peakVillagers: number;
  age: number;
  /** Villager-seconds idle, and how bad that was as a share of the match. */
  idleVillagerTime: number;
  idleTcTime: number;
  tcSeconds: number;
  /** The unit you trained most of, which is the shortest honest read of a style. */
  favouriteUnit: string;
  /**
   * The whole end-of-match report, kept for the most recent few games only.
   *
   * The summary above is what a *trend* needs; this is what re-reading one game
   * needs, and the two are different sizes. A full report carries four per-type
   * maps per side, so twenty of them is a few hundred kilobytes of localStorage
   * for something nobody scrolls back to. Three is enough to answer "what went
   * wrong in that one" while it is still the question you are asking.
   */
  report?: MatchReport;
}

/** How many of the stored games keep their full report. */
export const FULL_REPORT_KEEP = 3;

/** Boil a full report down to what a trend actually needs. */
export function summarise(
  report: MatchReport,
  meta: { won: boolean; mode: string; difficulty: string; players: number; at: number },
): MatchRecord {
  const you = report.you;
  let favouriteUnit = "";
  let most = 0;
  for (const [type, n] of Object.entries(you.trainedByType)) {
    // Villagers are trained most in almost every game, and "you built villagers"
    // is not a read on anyone's style.
    if (type === "villager") continue;
    if (n > most) { most = n; favouriteUnit = type; }
  }
  return {
    report,
    at: meta.at,
    won: meta.won,
    mapName: report.mapName,
    mode: meta.mode,
    difficulty: meta.difficulty,
    players: meta.players,
    durationSec: report.durationSec,
    score: you.score,
    foeScore: report.foe.score,
    gathered: you.gathered,
    spent: you.spent,
    unitsKilled: you.unitsKilled,
    unitsLost: you.unitsLost,
    buildingsRazed: you.buildingsRazed,
    buildingsLost: you.buildingsLost,
    peakArmy: you.peakArmy,
    peakVillagers: you.peakVillagers,
    age: you.age,
    idleVillagerTime: you.idleVillagerTime,
    idleTcTime: you.idleTcTime,
    tcSeconds: you.tcSeconds,
    favouriteUnit,
  };
}

function store(): Storage | null {
  try {
    return typeof localStorage === "undefined" ? null : localStorage;
  } catch {
    return null; // a browser with storage disabled — history is a nicety, not a need
  }
}

/** Newest first. */
export function listHistory(): MatchRecord[] {
  const s = store();
  if (!s) return [];
  try {
    const raw = s.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    // Anything malformed is dropped rather than crashing the menu it appears on.
    return parsed
      .filter((r): r is MatchRecord => !!r && typeof r.at === "number" && typeof r.won === "boolean")
      .sort((a, b) => b.at - a.at)
      .slice(0, HISTORY_LIMIT);
  } catch {
    return [];
  }
}

export function recordMatch(rec: MatchRecord): void {
  const s = store();
  if (!s) return;
  const all = [rec, ...listHistory()].slice(0, HISTORY_LIMIT);
  // Only the newest few carry their full report; older rows are thinned to the
  // summary as they age out of the window rather than all at once.
  for (let i = FULL_REPORT_KEEP; i < all.length; i++) {
    if (all[i].report) all[i] = { ...all[i], report: undefined };
  }
  try {
    s.setItem(STORAGE_KEY, JSON.stringify(all));
  } catch {
    // Storage is full. A history entry is a nicety, but losing the *report* is
    // what blew the budget, so retry without them before giving up entirely.
    try {
      s.setItem(STORAGE_KEY, JSON.stringify(all.map((r) => ({ ...r, report: undefined }))));
    } catch {
      /* nothing left to try */
    }
  }
}

export function clearHistory(): void {
  store()?.removeItem(STORAGE_KEY);
}

export interface HistorySummary {
  played: number;
  won: number;
  winRate: number;
  avgDuration: number;
  totalKills: number;
  totalLost: number;
  /** Kill:loss over every recorded game — the one number that trends usefully. */
  killRatio: number;
  /** Average share of the match a Town Centre sat with nothing queued. */
  avgTcIdleShare: number;
  bestStreak: number;
}

/**
 * The numbers across the whole record. Averages over what is stored rather than
 * over all time — the profile already keeps lifetime wins and losses, and this
 * is deliberately "how have the last twenty gone", which is the question you
 * actually ask after a session.
 */
export function summariseHistory(rows: MatchRecord[]): HistorySummary {
  if (!rows.length) {
    return {
      played: 0, won: 0, winRate: 0, avgDuration: 0, totalKills: 0,
      totalLost: 0, killRatio: 0, avgTcIdleShare: 0, bestStreak: 0,
    };
  }
  const won = rows.filter((r) => r.won).length;
  const totalKills = rows.reduce((a, r) => a + r.unitsKilled, 0);
  const totalLost = rows.reduce((a, r) => a + r.unitsLost, 0);
  const idleShares = rows
    .filter((r) => r.tcSeconds > 0)
    .map((r) => r.idleTcTime / r.tcSeconds);
  // Oldest to newest, so a streak means what it sounds like.
  let best = 0, run = 0;
  for (const r of [...rows].sort((a, b) => a.at - b.at)) {
    run = r.won ? run + 1 : 0;
    if (run > best) best = run;
  }
  return {
    played: rows.length,
    won,
    winRate: won / rows.length,
    avgDuration: rows.reduce((a, r) => a + r.durationSec, 0) / rows.length,
    totalKills,
    totalLost,
    killRatio: totalLost > 0 ? totalKills / totalLost : totalKills,
    avgTcIdleShare: idleShares.length
      ? idleShares.reduce((a, b) => a + b, 0) / idleShares.length
      : 0,
    bestStreak: best,
  };
}
