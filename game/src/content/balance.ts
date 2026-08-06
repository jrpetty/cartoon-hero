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
// Farms tick slightly slower than foraging — they're raid-safe and sit inside
// your walls, so they pay a small speed tax. Kept mild so farming feels
// productive.
export const FARM_TICK_MULT = 1.1;
/**
 * Food in one field before the soil is spent.
 *
 * Farms used to be literally infinite (`amount = 999999`), which made the
 * 60-wood cost a one-off toll on unlimited food and left the late economy with
 * nothing to spend wood on. A finite field turns each one into a recurring
 * wood-for-food trade, which is the decision farming is supposed to be. At the
 * standing rate this is a little over four minutes of work for one villager —
 * long enough that re-seeding is background noise rather than a job, which is
 * why auto-reseed exists and defaults on.
 */
export const FARM_FOOD = 350;

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
// Higher rarities are also a touch quicker, and their signature ability is
// stronger: a shorter cooldown and a more potent effect (longer/bigger).
export const RARITY_SPEED_MULT = [1.0, 1.0, 1.02, 1.04, 1.06, 1.09];
export const RARITY_ABILITY_CD_MULT = [1.0, 0.96, 0.92, 0.86, 0.8, 0.72];
export const RARITY_ABILITY_POWER_MULT = [1.0, 1.05, 1.1, 1.16, 1.24, 1.35];

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
