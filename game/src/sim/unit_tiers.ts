// Shop tiers for Warband Tactics, kept in their own module so both the run
// engine and the things it pulls in (the carousel, the screen) can read them
// without importing each other in a cycle.

import { UNITS } from "../content/units";

/** Shop tier (1 cheap/weak … 5 rare/strong); buying costs the tier in gold. */
export const UNIT_TIER: Record<string, number> = {
  militia: 1, spearman: 1, scout: 1, raider: 1,
  archer: 2, skirmisher: 2, horseman: 2, javelin: 2, pikeman: 2, shieldbearer: 2,
  knight: 3, crossbow: 3, twohand: 3, berserker: 3, longbow: 3,
  handcannon: 4, monk: 4, catapult: 4, cataphract: 4, battlemage: 4,
  ram: 5, trebuchet: 5, hero: 5, bombard: 5,
};

/** Unit types available at each tier (index 1..5), skipping anything undefined. */
export const TIER_UNITS: string[][] = [[], [], [], [], [], []];
for (const [type, tier] of Object.entries(UNIT_TIER)) if (UNITS[type]) TIER_UNITS[tier].push(type);
