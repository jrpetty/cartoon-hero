// Age progression. Advancing an Age costs resources, requires a key building,
// and unlocks stronger units/buildings plus a flat stat bump (see balance.ts).
//
// Four ages: Hearth → Banner → Crown → Empire. The names are the realm's own
// story — a hearth, a banner raised, a crown taken, an empire proclaimed —
// rather than a borrowed history lesson. Every advance past the Hearth also
// swears an Oath (content/oaths.ts), which is where a realm's identity comes
// from in place of a pre-picked civilisation.

export interface AgeDef {
  index: number;
  name: string;
  /** One word: "Hearth", "Banner", "Crown", "Empire". */
  short: string;
  cost: { food: number; wood: number; gold: number };
  /** Advance once you've built at least `requiresCount` distinct types from
   *  this set — so openings diverge (military, boom, or defensive paths all
   *  reach the next age). Empty/0 for the Hearth Age. */
  requiresAny: string[];
  requiresCount: number;
  advanceTime: number; // seconds to research at the Town Center
}

export const AGES: AgeDef[] = [
  { index: 0, name: "Hearth Age", short: "Hearth", cost: { food: 0, wood: 0, gold: 0 }, requiresAny: [], requiresCount: 0, advanceTime: 0 },
  {
    index: 1,
    name: "Banner Age",
    short: "Banner",
    cost: { food: 300, wood: 0, gold: 0 },
    requiresAny: ["barracks", "mill", "lumber_camp", "mining_camp"],
    requiresCount: 2,
    advanceTime: 30,
  },
  {
    index: 2,
    name: "Crown Age",
    short: "Crown",
    cost: { food: 500, wood: 0, gold: 200 },
    requiresAny: ["barracks", "archery_range", "stable", "blacksmith", "market", "watch_tower"],
    requiresCount: 2,
    advanceTime: 40,
  },
  {
    // The late game's destination. Gunpowder and the great siege engines live
    // here, with the last tier of smithing and economy, so reaching it is a
    // decision about the endgame rather than a stat bump. Either Crown
    // building qualifies: a Castle for the defensive road, a Siege Workshop for
    // the aggressive one.
    index: 3,
    name: "Empire Age",
    short: "Empire",
    cost: { food: 900, wood: 0, gold: 600 },
    requiresAny: ["castle", "siege_workshop"],
    requiresCount: 1,
    advanceTime: 50,
  },
];

/** "Hearth", "Banner", "Crown", "Empire" — for tables and tight labels. */
export const AGE_SHORT = AGES.map((a) => a.short);
export const ageShort = (i: number): string => AGES[i]?.short ?? "—";

export const MAX_AGE = AGES.length - 1;

/** Blacksmith upgrades: simple +attack / +armor researches per category. */
export interface UpgradeDef {
  id: string;
  name: string;
  desc: string;
  cost: { food: number; wood: number; gold: number };
  time: number;
  age: number;
  /** "vision", "build" and "trade" are team-wide and ignore appliesTo. */
  kind: "attack" | "armor" | "hp" | "speed" | "bonus" | "econ" | "vision" | "build" | "trade";
  /** Which armor classes it benefits. */
  appliesTo: string[];
  /** Or specific unit ids (per-unit "elite" upgrades). */
  appliesToUnits?: string[];
  /** For kind "bonus": the armor class the extra bonus damage applies vs. */
  bonusVs?: string;
  /** For kind "econ": which resource it speeds (omit = handled elsewhere). */
  resource?: string;
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
  // --- Crown-age combat tier ---------------------------------------------
  iron_casting: {
    id: "iron_casting",
    name: "Iron Casting",
    desc: "+2 attack for infantry and cavalry. (Crown Age)",
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
    desc: "+1 armor for infantry and cavalry. (Crown Age)",
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
    desc: "+2 attack for archers and towers. (Crown Age)",
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
    desc: "Villagers gather 15% faster. (Banner Age)",
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
    desc: "Villagers gather another 15% faster. (Crown Age)",
    cost: { food: 200, wood: 100, gold: 0 },
    time: 35,
    age: 2,
    kind: "econ",
    appliesTo: [],
    amount: 0.15,
    researchedAt: "blacksmith",
  },

  // --- Cavalry (unit-type) ---
  husbandry: {
    id: "husbandry", name: "Husbandry", desc: "Cavalry move noticeably faster. (Banner Age)",
    cost: { food: 150, wood: 0, gold: 0 }, time: 30, age: 1,
    kind: "speed", appliesTo: ["cavalry"], amount: 14, researchedAt: "blacksmith",
  },
  bloodlines: {
    id: "bloodlines", name: "Bloodlines", desc: "Cavalry gain +20 HP. (Crown Age)",
    cost: { food: 0, wood: 0, gold: 200 }, time: 35, age: 2,
    kind: "hp", appliesTo: ["cavalry"], amount: 20, researchedAt: "blacksmith",
  },
  // --- Per-unit "elite" upgrades ---
  longswords: {
    id: "longswords", name: "Long Swords", desc: "Man-at-Arms & Two-Handed Swordsmen +3 attack. (Crown Age)",
    cost: { food: 0, wood: 0, gold: 160 }, time: 35, age: 2,
    kind: "attack", appliesTo: [], appliesToUnits: ["militia", "twohand"], amount: 3, researchedAt: "blacksmith",
  },
  pikes: {
    id: "pikes", name: "Pikes", desc: "Spearmen & Pikemen +12 bonus damage vs cavalry. (Crown Age)",
    cost: { food: 0, wood: 0, gold: 150 }, time: 35, age: 2,
    kind: "bonus", appliesTo: [], appliesToUnits: ["spearman", "pikeman"], bonusVs: "cavalry", amount: 12, researchedAt: "blacksmith",
  },
  // --- Economy (researched at the gathering buildings) ---
  horse_collar: {
    id: "horse_collar", name: "Horse Collar", desc: "Farms & foraging yield 18% more food. (Banner Age)",
    cost: { food: 0, wood: 75, gold: 0 }, time: 25, age: 1,
    kind: "econ", appliesTo: [], resource: "food", amount: 0.18, researchedAt: "mill",
  },
  bow_saw: {
    id: "bow_saw", name: "Bow Saw", desc: "Villagers chop wood 18% faster. (Banner Age)",
    cost: { food: 75, wood: 0, gold: 0 }, time: 25, age: 1,
    kind: "econ", appliesTo: [], resource: "wood", amount: 0.18, researchedAt: "lumber_camp",
  },
  gold_mining: {
    id: "gold_mining", name: "Gold Mining", desc: "Villagers mine gold 18% faster. (Banner Age)",
    cost: { food: 100, wood: 0, gold: 0 }, time: 25, age: 1,
    kind: "econ", appliesTo: [], resource: "gold", amount: 0.18, researchedAt: "mining_camp",
  },

  // --- Town Centre: the survivability / quality-of-life line ----------------
  loom: {
    id: "loom", name: "Loom", desc: "Villagers gain +15 HP — raids stop being free. (Hearth Age)",
    cost: { food: 0, wood: 0, gold: 50 }, time: 20, age: 0,
    kind: "hp", appliesTo: [], appliesToUnits: ["villager"], amount: 15, researchedAt: "town_center",
  },
  town_watch: {
    id: "town_watch", name: "Town Watch", desc: "Everything you own sees 25% further. (Banner Age)",
    cost: { food: 75, wood: 0, gold: 0 }, time: 25, age: 1,
    kind: "vision", appliesTo: [], amount: 0.25, researchedAt: "town_center",
  },
  treadmill_crane: {
    id: "treadmill_crane", name: "Treadmill Crane", desc: "Villagers build and repair 20% faster. (Crown Age)",
    cost: { food: 0, wood: 200, gold: 0 }, time: 30, age: 2,
    kind: "build", appliesTo: [], amount: 0.2, researchedAt: "town_center",
  },

  // --- Market: make trading worth the building -----------------------------
  caravan: {
    id: "caravan", name: "Caravan", desc: "Market trades return 85 instead of 75. (Crown Age)",
    cost: { food: 0, wood: 150, gold: 0 }, time: 30, age: 2,
    kind: "trade", appliesTo: [], amount: 10, researchedAt: "market",
  },

  // --- Empire Age: the last tier of smithing and economy ---------------------
  blast_furnace: {
    id: "blast_furnace", name: "Blast Furnace", desc: "+2 attack for infantry and cavalry. (Empire Age)",
    cost: { food: 150, wood: 0, gold: 250 }, time: 45, age: 3,
    kind: "attack", appliesTo: ["infantry", "cavalry"], amount: 2, researchedAt: "blacksmith",
  },
  plate_mail: {
    id: "plate_mail", name: "Plate Mail Armor", desc: "+2 armor for infantry and cavalry. (Empire Age)",
    cost: { food: 150, wood: 0, gold: 250 }, time: 45, age: 3,
    kind: "armor", appliesTo: ["infantry", "cavalry"], amount: 2, researchedAt: "blacksmith",
  },
  bracer: {
    id: "bracer", name: "Bracer", desc: "+2 attack for archers and towers. (Empire Age)",
    cost: { food: 150, wood: 0, gold: 220 }, time: 45, age: 3,
    kind: "attack", appliesTo: ["archer"], amount: 2, researchedAt: "blacksmith",
  },
  ring_archer_armor: {
    id: "ring_archer_armor", name: "Ring Archer Armor", desc: "+2 armor for ranged units. (Empire Age)",
    cost: { food: 150, wood: 0, gold: 200 }, time: 45, age: 3,
    kind: "armor", appliesTo: ["archer"], amount: 2, researchedAt: "blacksmith",
  },
  crop_rotation: {
    id: "crop_rotation", name: "Crop Rotation", desc: "Farms & foraging yield 15% more food. (Empire Age)",
    cost: { food: 0, wood: 250, gold: 100 }, time: 40, age: 3,
    kind: "econ", appliesTo: [], resource: "food", amount: 0.15, researchedAt: "mill",
  },
  two_man_saw: {
    id: "two_man_saw", name: "Two-Man Saw", desc: "Villagers chop wood 15% faster. (Empire Age)",
    cost: { food: 250, wood: 0, gold: 100 }, time: 40, age: 3,
    kind: "econ", appliesTo: [], resource: "wood", amount: 0.15, researchedAt: "lumber_camp",
  },
  shaft_mining: {
    id: "shaft_mining", name: "Shaft Mining", desc: "Villagers mine gold 15% faster. (Empire Age)",
    cost: { food: 250, wood: 100, gold: 0 }, time: 40, age: 3,
    kind: "econ", appliesTo: [], resource: "gold", amount: 0.15, researchedAt: "mining_camp",
  },

  // --- Elite lines for the mounted units ------------------------------------
  cavalier: {
    id: "cavalier", name: "Cavalier", desc: "Knights gain +25 HP. (Crown Age)",
    cost: { food: 300, wood: 0, gold: 150 }, time: 40, age: 2,
    kind: "hp", appliesTo: [], appliesToUnits: ["knight"], amount: 25, researchedAt: "stable",
  },
  heavy_cav_archer: {
    id: "heavy_cav_archer", name: "Heavy Cavalry Archer", desc: "Horsemen gain +3 attack. (Crown Age)",
    cost: { food: 200, wood: 0, gold: 150 }, time: 40, age: 2,
    kind: "attack", appliesTo: [], appliesToUnits: ["horseman"], amount: 3, researchedAt: "archery_range",
  },
};
