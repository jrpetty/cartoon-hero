// Central tunables. Keep magic numbers here so balance passes touch one file.

export const TILE = 32; // world units per nav-grid tile

export const SIM_HZ = 20; // fixed simulation ticks per second
export const SIM_DT = 1 / SIM_HZ;

export const POP_CAP_HARD = 200;
export const POP_PER_HOUSE = 5;
export const POP_PER_TOWNCENTER = 10;

export const VILLAGER_CARRY_CAP = 10;
export const GATHER_RATE = 0.45; // resource units per gather tick action
export const GATHER_TICK = 0.6; // seconds per gather action

export const START_RESOURCES = { food: 200, wood: 200, gold: 100 };

// Per-age advance cost and what it unlocks is defined in tech.ts; this is the
// global feel of how strongly upgrades scale unit stats.
export const AGE_ATTACK_BONUS = [0, 1, 2, 3]; // flat attack added per age index
export const AGE_ARMOR_BONUS = [0, 0, 1, 2];

// Rarity stat multipliers (index by rarity 0..5). Applied to player units whose
// equipped variant has that rarity. PvE power fantasy — see plan "Fairness".
export const RARITY_HP_MULT = [1.0, 1.06, 1.13, 1.22, 1.34, 1.5];
export const RARITY_ATK_MULT = [1.0, 1.05, 1.11, 1.19, 1.3, 1.45];
export const RARITY_ARMOR_BONUS = [0, 0, 1, 1, 2, 3];

// AI eco multipliers per difficulty (gather speed / build speed handicap).
// Only the top tier gets a real bonus; lower tiers are handicapped.
export const AI_ECON_MULT = {
  squire: 0.7,
  knight: 1.0,
  lord: 1.15,
  warlord: 1.35,
};

export const PROJECTILE_SPEED = 360; // world units / sec for arrows etc.

export const REPAIR_RATE = 30; // hp/sec while a villager repairs
export const BUILD_PLACEMENT_PAD = 2; // tiles clearance helper
