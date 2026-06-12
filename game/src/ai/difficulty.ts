// AI difficulty tiers. Lower tiers are handicapped in behavior (no scouting,
// no counters, slow reactions); only Warlord gets a real economic bonus.

export interface DifficultyDef {
  id: string;
  name: string;
  desc: string;
  econMult: number; // gather/build/production speed multiplier
  villagerTarget: number;
  scouts: boolean; // sends an early scout and reacts to intel
  counters: boolean; // picks counter-units vs what it has seen
  expands: boolean; // builds a second Town Center
  harasses: boolean; // cavalry raids on the player's economy
  attackArmySize: number; // army pop needed before first push
  attackEverySec: number; // pacing between waves
  reactionSec: number; // decision loop interval
  maxAge: number;
  buildsCastle: boolean;
}

export const DIFFICULTIES: Record<string, DifficultyDef> = {
  squire: {
    id: "squire",
    name: "Squire",
    desc: "A gentle introduction. Slow to act, never scouts, fields simple armies.",
    econMult: 0.7,
    villagerTarget: 11,
    scouts: false,
    counters: false,
    expands: false,
    harasses: false,
    attackArmySize: 8,
    attackEverySec: 150,
    reactionSec: 2.4,
    maxAge: 1,
    buildsCastle: false,
  },
  knight: {
    id: "knight",
    name: "Knight",
    desc: "A real opponent. Follows a solid build, defends itself, attacks on a timer.",
    econMult: 1.0,
    villagerTarget: 18,
    scouts: true,
    counters: false,
    expands: false,
    harasses: false,
    attackArmySize: 14,
    attackEverySec: 110,
    reactionSec: 1.2,
    maxAge: 2,
    buildsCastle: false,
  },
  lord: {
    id: "lord",
    name: "Lord",
    desc: "Scouts you, counters your army, expands, and pressures from two sides.",
    econMult: 1.15,
    villagerTarget: 26,
    scouts: true,
    counters: true,
    expands: true,
    harasses: true,
    attackArmySize: 18,
    attackEverySec: 85,
    reactionSec: 0.8,
    maxAge: 2,
    buildsCastle: true,
  },
  warlord: {
    id: "warlord",
    name: "Warlord",
    desc: "Relentless. Everything the Lord does, faster, with a stronger economy.",
    econMult: 1.35,
    villagerTarget: 32,
    scouts: true,
    counters: true,
    expands: true,
    harasses: true,
    attackArmySize: 16,
    attackEverySec: 60,
    reactionSec: 0.5,
    maxAge: 2,
    buildsCastle: true,
  },
};

export const DIFFICULTY_IDS = ["squire", "knight", "lord", "warlord"];
