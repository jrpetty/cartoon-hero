// Age progression. Advancing an Age costs resources, requires a key building,
// and unlocks stronger units/buildings plus a flat stat bump (see balance.ts).

export interface AgeDef {
  index: number;
  name: string;
  cost: { food: number; wood: number; gold: number };
  /** Building that must exist before you can advance to this age. */
  requires: string | null;
  advanceTime: number; // seconds to research at the Town Center
}

export const AGES: AgeDef[] = [
  { index: 0, name: "Dark Age", cost: { food: 0, wood: 0, gold: 0 }, requires: null, advanceTime: 0 },
  {
    index: 1,
    name: "Feudal Age",
    cost: { food: 300, wood: 0, gold: 0 },
    requires: "barracks",
    advanceTime: 30,
  },
  {
    index: 2,
    name: "Castle Age",
    cost: { food: 500, wood: 0, gold: 200 },
    requires: "blacksmith",
    advanceTime: 40,
  },
];

export const MAX_AGE = AGES.length - 1;

/** Blacksmith upgrades: simple +attack / +armor researches per category. */
export interface UpgradeDef {
  id: string;
  name: string;
  desc: string;
  cost: { food: number; wood: number; gold: number };
  time: number;
  age: number;
  kind: "attack" | "armor" | "econ";
  /** Which armor classes it benefits. */
  appliesTo: string[];
  amount: number;
  researchedAt: string;
}

export const UPGRADES: Record<string, UpgradeDef> = {
  forging: {
    id: "forging",
    name: "Forging",
    desc: "+2 attack for all melee units.",
    cost: { food: 0, wood: 0, gold: 120 },
    time: 25,
    age: 1,
    kind: "attack",
    appliesTo: ["infantry", "cavalry"],
    amount: 2,
    researchedAt: "blacksmith",
  },
  fletching: {
    id: "fletching",
    name: "Fletching",
    desc: "+1 attack for archers and towers.",
    cost: { food: 0, wood: 0, gold: 100 },
    time: 25,
    age: 1,
    kind: "attack",
    appliesTo: ["archer"],
    amount: 1,
    researchedAt: "blacksmith",
  },
  scale_armor: {
    id: "scale_armor",
    name: "Scale Mail Armor",
    desc: "+1 armor for infantry and cavalry.",
    cost: { food: 0, wood: 0, gold: 100 },
    time: 25,
    age: 1,
    kind: "armor",
    appliesTo: ["infantry", "cavalry"],
    amount: 1,
    researchedAt: "blacksmith",
  },
  padded_archer_armor: {
    id: "padded_archer_armor",
    name: "Padded Archer Armor",
    desc: "+1 armor for ranged units.",
    cost: { food: 0, wood: 0, gold: 100 },
    time: 25,
    age: 1,
    kind: "armor",
    appliesTo: ["archer"],
    amount: 1,
    researchedAt: "blacksmith",
  },
  // --- Castle-age combat tier ---------------------------------------------
  iron_casting: {
    id: "iron_casting",
    name: "Iron Casting",
    desc: "+2 attack for infantry and cavalry. (Castle Age)",
    cost: { food: 0, wood: 0, gold: 200 },
    time: 35,
    age: 2,
    kind: "attack",
    appliesTo: ["infantry", "cavalry"],
    amount: 2,
    researchedAt: "blacksmith",
  },
  chain_mail: {
    id: "chain_mail",
    name: "Chain Mail Armor",
    desc: "+1 armor for infantry and cavalry. (Castle Age)",
    cost: { food: 0, wood: 0, gold: 180 },
    time: 35,
    age: 2,
    kind: "armor",
    appliesTo: ["infantry", "cavalry"],
    amount: 1,
    researchedAt: "blacksmith",
  },
  bodkin_arrow: {
    id: "bodkin_arrow",
    name: "Bodkin Arrow",
    desc: "+2 attack for archers and towers. (Castle Age)",
    cost: { food: 0, wood: 0, gold: 160 },
    time: 35,
    age: 2,
    kind: "attack",
    appliesTo: ["archer"],
    amount: 2,
    researchedAt: "blacksmith",
  },
  // --- Economy upgrades (faster gathering) --------------------------------
  wheelbarrow: {
    id: "wheelbarrow",
    name: "Wheelbarrow",
    desc: "Villagers gather 15% faster. (Feudal Age)",
    cost: { food: 150, wood: 75, gold: 0 },
    time: 30,
    age: 1,
    kind: "econ",
    appliesTo: [],
    amount: 0.15,
    researchedAt: "blacksmith",
  },
  hand_cart: {
    id: "hand_cart",
    name: "Hand Cart",
    desc: "Villagers gather another 15% faster. (Castle Age)",
    cost: { food: 200, wood: 100, gold: 0 },
    time: 35,
    age: 2,
    kind: "econ",
    appliesTo: [],
    amount: 0.15,
    researchedAt: "blacksmith",
  },
};
