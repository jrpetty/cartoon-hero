// Rarity tiers for the CS-style unit collection. Higher tiers roll less often
// and grant better stat multipliers (see balance.ts RARITY_* arrays) plus a
// more ornate procedural look (trim color + glow used by the renderer).

export interface RarityTier {
  index: number;
  id: string;
  name: string;
  color: string; // primary trim/glow color
  glow: string; // softer glow color
  weight: number; // relative drop weight
}

export const RARITIES: RarityTier[] = [
  { index: 0, id: "common", name: "Common", color: "#b7b7b7", glow: "#8d8d8d", weight: 600 },
  { index: 1, id: "uncommon", name: "Uncommon", color: "#5bd66b", glow: "#2f9e3f", weight: 250 },
  { index: 2, id: "rare", name: "Rare", color: "#4aa3ff", glow: "#1e6fd6", weight: 100 },
  { index: 3, id: "epic", name: "Very Rare", color: "#b15bff", glow: "#7d2fd6", weight: 35 },
  { index: 4, id: "legendary", name: "Extremely Rare", color: "#ffcf3a", glow: "#d69a1e", weight: 13 },
  { index: 5, id: "mythic", name: "One-of-a-Kind", color: "#ff5a4a", glow: "#ff2d6e", weight: 2 },
];

export const RARITY_WEIGHTS = RARITIES.map((r) => r.weight);

export function rarityByIndex(i: number): RarityTier {
  return RARITIES[Math.max(0, Math.min(RARITIES.length - 1, i))];
}
