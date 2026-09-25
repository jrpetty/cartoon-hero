// The collectible catalog: every trainable unit has a variant per rarity tier.
// A variant is identified by `${unitId}:${rarity}`. Common variants are owned by
// default; higher rarities are unlocked from chests and grant stat multipliers.

import { UNITS } from "../content/units";
import { RARITIES } from "./rarity";

export interface VariantDef {
  key: string; // `${unitId}:${rarity}`
  unitId: string;
  unitName: string;
  rarity: number;
  name: string; // flavorful display name
}

// Flavor prefixes by rarity to make unlocks feel special.
const PREFIX: string[][] = [
  ["Levy", "Common", "Standard"],
  ["Seasoned", "Veteran", "Drilled"],
  ["Elite", "Royal", "Hardened"],
  ["Champion", "Vanguard", "Warbound"],
  ["Legendary", "Mythril", "Storied"],
  ["", "", ""], // mythic uses a unique named title
];

// Unique titles for the One-of-a-Kind mythic per unit.
const MYTHIC_TITLE: Record<string, string> = {
  villager: "Sigrun, the Tireless",
  militia: "Brakka Ironjaw",
  spearman: "The Thornwall",
  archer: "Wyl of the Long Sight",
  skirmisher: "Fenn the Needler",
  knight: "Sir Aldric Dawnbringer",
  catapult: "Old Thunder",
  ram: "The Gatebreaker",
  monk: "Brother Lumen",
};

function variantName(unitId: string, unitName: string, rarity: number, salt: number): string {
  if (rarity >= 5) return MYTHIC_TITLE[unitId] ?? `${unitName} Prime`;
  const pool = PREFIX[rarity];
  if (rarity === 0) return unitName;
  return `${pool[salt % pool.length]} ${unitName}`;
}

export const CATALOG: VariantDef[] = [];
export const VARIANT_BY_KEY: Record<string, VariantDef> = {};

let salt = 0;
for (const unitId of Object.keys(UNITS)) {
  const u = UNITS[unitId];
  for (const r of RARITIES) {
    const key = `${unitId}:${r.index}`;
    const v: VariantDef = {
      key,
      unitId,
      unitName: u.name,
      rarity: r.index,
      name: variantName(unitId, u.name, r.index, salt++),
    };
    CATALOG.push(v);
    VARIANT_BY_KEY[key] = v;
  }
}

export function variantKey(unitId: string, rarity: number): string {
  return `${unitId}:${rarity}`;
}

/** All unit ids that can appear in the collection / loadout. */
export const COLLECTIBLE_UNIT_IDS = Object.keys(UNITS);
