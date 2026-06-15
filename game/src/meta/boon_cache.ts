// Warband Cache: bought with Valor, rolls an UPGRADED tier of a boon. Everyone
// owns every boon at base (Common) rarity already, so the cache only ever grants
// the advanced rarities (Uncommon and up) — a stronger version of the same boon.
// Duplicates refund a little Valor so the grind never fully stalls. Kept separate
// from the unit-variant chests so the boon economy can be tuned independently.

import { RNG } from "../engine/rng";
import { RARITY_WEIGHTS, rarityByIndex } from "./rarity";
import { BOON_IDS, BOONS_BY_ID } from "../content/boons";

export const BOON_CACHE_COST = 200; // Valor

export function boonKey(boonId: string, rarity: number): string {
  return `${boonId}:${rarity}`;
}

export function parseBoonKey(key: string): { boonId: string; rarity: number } {
  const i = key.lastIndexOf(":");
  return { boonId: key.slice(0, i), rarity: parseInt(key.slice(i + 1), 10) || 0 };
}

export interface BoonRoll {
  boonId: string;
  rarity: number;
  key: string;
  name: string;
  duplicate: boolean;
  refund: number; // Valor refunded if duplicate
}

const DUP_REFUND = [25, 50, 100, 220, 500, 1200];

/** Roll a Warband Cache. Pure given the RNG, so it's deterministic and testable.
 *  Rolls an advanced rarity (1..N) only — base Common is owned by everyone. */
export function rollBoonCache(owned: Set<string>, rng: RNG): BoonRoll {
  // Drop the Common slot and re-weight across the advanced tiers.
  const rarity = rng.weightedIndex(RARITY_WEIGHTS.slice(1)) + 1;
  const boonId = rng.pick(BOON_IDS);
  const key = boonKey(boonId, rarity);
  const duplicate = owned.has(key);
  return {
    boonId,
    rarity,
    key,
    name: BOONS_BY_ID[boonId]?.name ?? boonId,
    duplicate,
    refund: duplicate ? DUP_REFUND[rarity] ?? 25 : 0,
  };
}
