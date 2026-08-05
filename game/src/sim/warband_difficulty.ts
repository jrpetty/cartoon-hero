// Warband Tactics lobby difficulty.
//
// The seven rival warbands already run the *same* economy the player does —
// income, interest, levelling, rerolls, star-ups, relics. Difficulty is
// therefore not a stat multiplier bolted onto their units: it is how hard they
// play the same game. A Squire lobby banks lazily and techs slowly; a
// Conqueror lobby banks well, techs on curve and rolls hard for its carries.
//
// The only player-facing knob is how much a defeat costs, which is what makes
// the low end genuinely forgiving rather than merely slower.

export type WarbandDifficultyId = "squire" | "veteran" | "warlord" | "conqueror";

export interface WarbandDifficulty {
  id: WarbandDifficultyId;
  name: string;
  title: string;
  blurb: string;
  color: string;
  /** Multiplier on a rival's income each round. */
  foeIncome: number;
  /** Multiplier on how fast a rival climbs the level curve. */
  foeLevelPace: number;
  /** Extra rerolls a rival gets each round to dig for star-ups. */
  foeRerolls: number;
  /** A rival earns a relic every this-many rounds (lower is more). */
  foeRelicEvery: number;
  /** Multiplier on the life a defeat costs you. */
  damageTaken: number;
  /** Gold you open the run with, on top of the usual two. */
  startGold: number;
}

export const WARBAND_DIFFICULTIES: WarbandDifficulty[] = [
  {
    id: "squire", name: "Squire", title: "learn the board", color: "#7ad08a",
    blurb: "Rivals bank lazily and tech late. Defeats cost you a third less life, and you open three gold up.",
    foeIncome: 0.82, foeLevelPace: 0.82, foeRerolls: -1, foeRelicEvery: 4, damageTaken: 0.68, startGold: 3,
  },
  {
    id: "veteran", name: "Veteran", title: "the honest fight", color: "#e7ddc4",
    blurb: "Rivals play the same economy you do, at the same pace. Nothing is tilted either way.",
    foeIncome: 1, foeLevelPace: 1, foeRerolls: 0, foeRelicEvery: 3, damageTaken: 1, startGold: 0,
  },
  {
    id: "warlord", name: "Warlord", title: "they draft properly", color: "#e0a23a",
    blurb: "Rivals bank harder, tech on curve and roll for their carries. Defeats bite deeper.",
    foeIncome: 1.12, foeLevelPace: 1.1, foeRerolls: 2, foeRelicEvery: 3, damageTaken: 1.15, startGold: 0,
  },
  {
    id: "conqueror", name: "Conqueror", title: "no room to be wrong", color: "#d8584a",
    blurb: "Rivals run a full economy and dig relentlessly for star-ups. Every defeat costs a third more.",
    foeIncome: 1.26, foeLevelPace: 1.2, foeRerolls: 4, foeRelicEvery: 2, damageTaken: 1.32, startGold: 0,
  },
];

const BY_ID = new Map(WARBAND_DIFFICULTIES.map((d) => [d.id, d]));

/** The default — the one the rest of the game is balanced around. */
export const VETERAN = BY_ID.get("veteran")!;

export function difficultyById(id: string | null | undefined): WarbandDifficulty {
  return (id && BY_ID.get(id as WarbandDifficultyId)) || VETERAN;
}
