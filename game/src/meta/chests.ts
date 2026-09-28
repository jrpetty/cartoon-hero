// War Chests: buy with Renown, open to roll a unit variant by weighted rarity.
// Duplicates convert to a small Renown refund so progress never fully stalls.

import { RNG } from "../engine/rng";
import { RARITIES, RARITY_WEIGHTS, rarityByIndex } from "./rarity";
import { CATALOG, VariantDef, variantKey } from "./catalog";
import { COLLECTIBLE_UNIT_IDS } from "./catalog";

export interface ChestDef {
  id: string;
  name: string;
  desc: string;
  cost: number; // Renown
  /** Multiplies the base rarity weights to bias toward rarer drops. */
  rarityBias: number[]; // length === RARITIES.length
}

// Bias arrays multiply base weights. A "bigger" chest skews rarer.
export const CHESTS: ChestDef[] = [
  {
    id: "footmans",
    name: "Footman's Chest",
    desc: "A humble crate. Mostly common gear, a slim chance at something better.",
    cost: 150,
    rarityBias: [1, 1, 1, 1, 1, 1],
  },
  {
    id: "knights",
    name: "Knight's Chest",
    desc: "Better odds at Rare and above. The reliable mid-tier pull.",
    cost: 400,
    rarityBias: [0.5, 1.2, 1.8, 2.2, 2.5, 2.5],
  },
  {
    id: "royal",
    name: "Royal Reliquary",
    desc: "Premium chest. Strongly weighted toward Epic, Legendary and beyond.",
    cost: 1000,
    rarityBias: [0.15, 0.6, 1.5, 3.2, 4.5, 6.0],
  },
];

export const CHEST_BY_ID: Record<string, ChestDef> = Object.fromEntries(
  CHESTS.map((c) => [c.id, c]),
);

export interface RollResult {
  variant: VariantDef;
  rarity: number;
  duplicate: boolean;
  refund: number; // Renown refunded if duplicate
}

/** Refund value of a duplicate, scaling with rarity. */
function dupRefund(rarity: number): number {
  return [30, 60, 120, 260, 600, 1500][rarity] ?? 30;
}

/**
 * Roll a chest. Picks a rarity by biased weights, then a random unit of that
 * rarity. Pure given the RNG, so it is deterministic and testable.
 */
export function rollChest(chest: ChestDef, owned: Set<string>, rng: RNG): RollResult {
  const weights = RARITY_WEIGHTS.map((w, i) => w * (chest.rarityBias[i] ?? 1));
  const rarity = rng.weightedIndex(weights);
  const unitId = rng.pick(COLLECTIBLE_UNIT_IDS);
  const key = variantKey(unitId, rarity);
  const variant = CATALOG.find((v) => v.key === key)!;
  const duplicate = owned.has(key);
  return {
    variant,
    rarity,
    duplicate,
    refund: duplicate ? dupRefund(rarity) : 0,
  };
}

export { RARITIES, rarityByIndex };
