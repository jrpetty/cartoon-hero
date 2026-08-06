// Achievements and weekly challenges.
//
// Two different jobs that share one mechanism:
//
//   • **Achievements** are permanent. They are a list of things the game can do
//     that you might not have thought to try — win without losing a villager,
//     take the Iron Crown on Conqueror — and each pays Valor once, ever.
//   • **Challenges** rotate weekly. Same shape, but drawn from a pool by the
//     week number, so there is a reason to come back and a reason to play
//     something other than your favourite opening.
//
// Both are evaluated against the same MatchRecord the history screen stores, so
// a condition is a plain function over one game's summary and nothing has to
// reach into the sim. That is the whole design constraint: if a condition can't
// be read off a finished match, it doesn't belong here.

import type { MatchRecord } from "./history";

export interface Achievement {
  id: string;
  name: string;
  desc: string;
  /** Valor paid the first time it is earned. */
  valor: number;
  /** True when this match earned it. */
  test: (m: MatchRecord) => boolean;
}

/**
 * Permanent achievements.
 *
 * Every one of these is a *win* condition as well as its own condition. Paying
 * out for a flawless loss would be strange, and it also keeps the list honest:
 * these are things to do while winning, not instead of it.
 */
export const ACHIEVEMENTS: Achievement[] = [
  {
    id: "first_blood",
    name: "First Blood",
    desc: "Win a match.",
    valor: 40,
    test: (m) => m.won,
  },
  {
    id: "flawless_eco",
    name: "Not One Villager",
    desc: "Win without losing a single villager or building.",
    valor: 220,
    test: (m) => m.won && m.unitsLost === 0 && m.buildingsLost === 0,
  },
  {
    id: "iron_crown_conqueror",
    name: "Iron Crown, Conqueror",
    desc: "Win on Conqueror difficulty.",
    valor: 300,
    test: (m) => m.won && m.difficulty === "conqueror",
  },
  {
    id: "rush",
    name: "Before the Bell",
    desc: "Win in under eight minutes.",
    valor: 160,
    test: (m) => m.won && m.durationSec < 8 * 60,
  },
  {
    id: "long_haul",
    name: "The Long Haul",
    desc: "Win a match that ran past forty minutes.",
    valor: 160,
    test: (m) => m.won && m.durationSec > 40 * 60,
  },
  {
    id: "imperial",
    name: "Imperial Ambition",
    desc: "Win having reached the final age.",
    valor: 120,
    test: (m) => m.won && m.age >= 3,
  },
  {
    id: "butcher",
    name: "Butcher's Bill",
    desc: "Win a match with 100 kills.",
    valor: 140,
    test: (m) => m.won && m.unitsKilled >= 100,
  },
  {
    id: "ten_to_one",
    name: "Ten to One",
    desc: "Win with ten kills for every unit you lost.",
    valor: 200,
    test: (m) => m.won && m.unitsLost > 0 && m.unitsKilled >= m.unitsLost * 10,
  },
  {
    id: "busy_hands",
    name: "Busy Hands",
    desc: "Win with your Town Centre idle less than a tenth of the match.",
    valor: 180,
    test: (m) => m.won && m.tcSeconds > 60 && m.idleTcTime / m.tcSeconds < 0.1,
  },
  {
    id: "full_house",
    name: "Full House",
    desc: "Win a match against seven opponents.",
    valor: 260,
    test: (m) => m.won && m.players >= 8,
  },
  {
    id: "razed",
    name: "Nothing Left Standing",
    desc: "Win having razed twenty enemy buildings.",
    valor: 150,
    test: (m) => m.won && m.buildingsRazed >= 20,
  },
  {
    id: "boomer",
    name: "Boom Town",
    desc: "Win with sixty villagers at your peak.",
    valor: 170,
    test: (m) => m.won && m.peakVillagers >= 60,
  },
];

export const ACHIEVEMENTS_BY_ID: Record<string, Achievement> =
  Object.fromEntries(ACHIEVEMENTS.map((a) => [a.id, a]));

// ------------------------------------------------------------- challenges --

export interface Challenge extends Achievement {
  /** Challenges can be earned again in a later week; achievements cannot. */
  weekly: true;
}

const CH = (id: string, name: string, desc: string, valor: number, test: (m: MatchRecord) => boolean): Challenge =>
  ({ id, name, desc, valor, test, weekly: true });

/**
 * The pool three of these are drawn from each week.
 *
 * Written to pull in different directions on purpose — one rewards rushing, one
 * rewards booming, one rewards a map you would not otherwise pick — so a week's
 * set is usually not all satisfiable by the same game.
 */
export const CHALLENGE_POOL: Challenge[] = [
  CH("ch_cavalry", "Hooves", "Win with cavalry as your most-trained unit.",
    120, (m) => m.won && ["knight", "horseman", "raider", "scout"].includes(m.favouriteUnit)),
  CH("ch_archers", "Arrow Storm", "Win with archers as your most-trained unit.",
    120, (m) => m.won && ["archer", "crossbow", "skirmisher", "javelin", "handcannon"].includes(m.favouriteUnit)),
  CH("ch_infantry", "Shield Wall", "Win with infantry as your most-trained unit.",
    120, (m) => m.won && ["militia", "spearman", "twohand", "pikeman"].includes(m.favouriteUnit)),
  CH("ch_frugal", "Nothing Wasted", "Win having spent nine tenths of everything you gathered.",
    140, (m) => m.won && m.gathered > 1000 && m.spent / m.gathered >= 0.9),
  CH("ch_quick", "Quick Work", "Win in under twelve minutes.",
    130, (m) => m.won && m.durationSec < 12 * 60),
  CH("ch_survivor", "Held the Line", "Win a match in which you lost at least ten buildings.",
    150, (m) => m.won && m.buildingsLost >= 10),
  CH("ch_army", "Host of Thousands", "Win with a peak army of forty.",
    140, (m) => m.won && m.peakArmy >= 40),
  CH("ch_hard", "Uphill", "Win on Duke difficulty or harder.",
    160, (m) => m.won && ["duke", "conqueror"].includes(m.difficulty)),
  CH("ch_crowd", "Crowded Field", "Win a match with at least four players in it.",
    130, (m) => m.won && m.players >= 4),
  CH("ch_efficient", "Clean Sheet", "Win losing fewer than five units.",
    170, (m) => m.won && m.unitsLost < 5),
];

/** How many run at once. Three is enough to choose between and few enough to read. */
export const CHALLENGES_PER_WEEK = 3;

/**
 * Which week we are in, counted from the epoch in whole weeks.
 *
 * Taken from a timestamp passed in rather than read from the clock, so the
 * rotation is testable and so a caller that already knows "now" doesn't
 * disagree with this by a millisecond.
 */
export function weekIndex(atMillis: number): number {
  return Math.floor(atMillis / (7 * 24 * 60 * 60 * 1000));
}

/**
 * This week's three challenges.
 *
 * A week takes a consecutive window of the pool and the window advances by its
 * own width, so consecutive weeks never share an entry and the whole pool is
 * visited before anything comes round again. A rotation that felt random would
 * keep re-offering the same two, which is worse than one you can see the shape
 * of. (This originally strode by three *within* the week as well as between
 * them, which made week 0 and week 1 overlap on two of their three.)
 */
export function challengesForWeek(week: number): Challenge[] {
  const n = CHALLENGE_POOL.length;
  const take = Math.min(CHALLENGES_PER_WEEK, n);
  const base = week * take;
  const out: Challenge[] = [];
  for (let i = 0; i < take; i++) out.push(CHALLENGE_POOL[(base + i) % n]);
  return out;
}

// ------------------------------------------------------------- evaluation --

export interface EarnedAward {
  id: string;
  name: string;
  valor: number;
  weekly: boolean;
}

export interface AwardState {
  /** Achievement ids already paid out, ever. */
  earned: string[];
  /** `week:challengeId` for challenges already paid out that week. */
  challengesDone: string[];
}

/**
 * What this match just earned, given what has already been paid for.
 *
 * Returns the awards rather than applying them: the caller owns the profile and
 * the Valor, and a pure function here is what lets the whole table be tested
 * without a profile at all.
 */
export function evaluateAwards(m: MatchRecord, state: AwardState): EarnedAward[] {
  const done = new Set(state.earned);
  const weekDone = new Set(state.challengesDone);
  const week = weekIndex(m.at);
  const out: EarnedAward[] = [];
  for (const a of ACHIEVEMENTS) {
    if (done.has(a.id)) continue;
    if (a.test(m)) out.push({ id: a.id, name: a.name, valor: a.valor, weekly: false });
  }
  for (const c of challengesForWeek(week)) {
    const key = `${week}:${c.id}`;
    if (weekDone.has(key)) continue;
    if (c.test(m)) out.push({ id: key, name: c.name, valor: c.valor, weekly: true });
  }
  return out;
}
