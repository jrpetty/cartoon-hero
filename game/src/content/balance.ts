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

/**
 * What a Trade Cart earns per trip: `TRADE_GOLD_PER_TILE * tiles ^ TRADE_GOLD_EXPONENT`.
 *
 * The exponent is the whole point, and the first version got it wrong. Paying
 * *linearly* by distance sounds right and does nothing: a trip pays twice as
 * much and takes twice as long, so gold per second is identical at every
 * distance and a cart shuttling between two Markets in one courtyard earns
 * exactly what a cart crossing the map earns — measured at 312 gold for a short
 * route against 231 for a long one, i.e. worse.
 *
 * Above 1 the income rises with distance instead: at these values an 8-tile hop
 * makes about 0.7 gold/sec and a 50-tile line about 1.2, so a long route is
 * worth roughly twice a short one *and* spends most of its life outside your
 * walls where it can be cut. That is the decision trade is supposed to be.
 */
export const TRADE_GOLD_PER_TILE = 0.16;
export const TRADE_GOLD_EXPONENT = 1.3;

/**
 * How much of a Market price's displacement survives each second.
 *
 * 0.994 halves it in about two minutes — long enough that dumping a thousand
 * wood in one go is meaningfully worse than spreading it out, short enough that
 * a mistake early doesn't shadow the rest of the game.
 */
export const MARKET_RECOVERY = 0.994;
