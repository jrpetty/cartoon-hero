// Commander XP/level curve and per-match reward calculation (XP + Renown).
// Renown is intentionally a slow grind — distinct from in-game gold.

export interface MatchOutcome {
  win: boolean;
  durationSec: number;
  unitsKilled: number;
  buildingsRazed: number;
  difficulty: string; // "squire" | "knight" | "lord" | "warlord"
  fairMode: boolean; // ranked/all-common match
}

export interface MatchRewards {
  xp: number;
  renown: number;
  breakdown: { label: string; xp: number; renown: number }[];
}

const DIFF_MULT: Record<string, number> = {
  squire: 0.7,
  knight: 1.0,
  lord: 1.35,
  warlord: 1.8,
};

/** XP required to go from `level` to `level+1`. Gentle quadratic curve. */
export function xpForLevel(level: number): number {
  return Math.floor(100 + level * 75 + level * level * 12);
}

/** Total XP at the start of a level (cumulative). */
export function totalXpForLevel(level: number): number {
  let t = 0;
  for (let l = 1; l < level; l++) t += xpForLevel(l);
  return t;
}

/** Given total xp, return {level, into, need}. Levels start at 1. */
export function levelFromXp(totalXp: number): { level: number; into: number; need: number } {
  let level = 1;
  let remaining = totalXp;
  while (remaining >= xpForLevel(level)) {
    remaining -= xpForLevel(level);
    level++;
  }
  return { level, into: remaining, need: xpForLevel(level) };
}

export function computeRewards(o: MatchOutcome): MatchRewards {
  const mult = (DIFF_MULT[o.difficulty] ?? 1) * (o.fairMode ? 1.25 : 1);
  const breakdown: MatchRewards["breakdown"] = [];
  const add = (label: string, xp: number, renown: number) => {
    breakdown.push({ label, xp: Math.round(xp), renown: Math.round(renown) });
  };

  add("Match played", 40, 12);
  if (o.win) add("Victory", 120 * mult, 45 * mult);
  else add("Defeat (still learning)", 25, 8);

  const mins = o.durationSec / 60;
  add("Time on field", Math.min(mins, 30) * 6, Math.min(mins, 30) * 1.5);
  add("Enemies slain", o.unitsKilled * 1.5, o.unitsKilled * 0.4);
  add("Buildings razed", o.buildingsRazed * 8 * mult, o.buildingsRazed * 2 * mult);

  let xp = 0;
  let renown = 0;
  for (const b of breakdown) {
    xp += b.xp;
    renown += b.renown;
  }
  return { xp: Math.round(xp), renown: Math.round(renown), breakdown };
}

/** Reward granted on each level-up. */
export function levelUpRenown(level: number): number {
  return 50 + level * 10;
}
