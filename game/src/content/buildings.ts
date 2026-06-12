import { ArmorClass } from "../sim/types";

export interface BuildingDef {
  id: string;
  name: string;
  desc: string;
  hp: number;
  cost: { food: number; wood: number; gold: number };
  buildTime: number; // seconds of villager construction
  tiles: number; // footprint is tiles x tiles
  sight: number;
  popProvided: number;
  trains: string[]; // unit def ids
  age: number; // min age to place
  builtBy: string; // which builder constructs it (villager)
  isDropoff: boolean;
  dropoffKinds: string[]; // resource kinds it accepts ("food","wood","gold")
  // Defensive buildings
  attack: number;
  range: number;
  attackInterval: number;
  armorClass: ArmorClass;
  /** Building that must already exist to unlock placement (tech gating). */
  requires?: string;
  /** Garrison capacity (0 = none). Garrisoned units add arrows. */
  garrisonCap?: number;
}

const B = (d: Partial<BuildingDef> & { id: string; name: string }): BuildingDef => ({
  desc: "",
  hp: 600,
  cost: { food: 0, wood: 0, gold: 0 },
  buildTime: 25,
  tiles: 2,
  sight: 180,
  popProvided: 0,
  trains: [],
  age: 0,
  builtBy: "villager",
  isDropoff: false,
  dropoffKinds: [],
  attack: 0,
  range: 0,
  attackInterval: 2,
  armorClass: ArmorClass.Building,
  garrisonCap: 0,
  ...d,
});

export const BUILDINGS: Record<string, BuildingDef> = {
  town_center: B({
    id: "town_center",
    name: "Town Center",
    desc: "Heart of your realm. Trains villagers, drops off all resources, garrisons your people.",
    hp: 2400,
    cost: { food: 0, wood: 350, gold: 0 },
    buildTime: 60,
    tiles: 3,
    sight: 260,
    popProvided: 10,
    trains: ["villager"],
    isDropoff: true,
    dropoffKinds: ["food", "wood", "gold"],
    attack: 8,
    range: 180,
    attackInterval: 1.6,
    garrisonCap: 12,
  }),
  house: B({
    id: "house",
    name: "House",
    desc: "Raises your population limit so you can field a larger force.",
    hp: 480,
    cost: { food: 0, wood: 30, gold: 0 },
    buildTime: 18,
    tiles: 2,
    popProvided: 5,
  }),
  mill: B({
    id: "mill",
    name: "Mill",
    desc: "Food drop-off. Build it next to berries, farms or hunt to save villager walking time.",
    hp: 500,
    cost: { food: 0, wood: 80, gold: 0 },
    buildTime: 22,
    tiles: 2,
    isDropoff: true,
    dropoffKinds: ["food"],
  }),
  lumber_camp: B({
    id: "lumber_camp",
    name: "Lumber Camp",
    desc: "Wood drop-off. Plant it in the trees to keep your woodline efficient.",
    hp: 480,
    cost: { food: 0, wood: 80, gold: 0 },
    buildTime: 22,
    tiles: 2,
    isDropoff: true,
    dropoffKinds: ["wood"],
  }),
  mining_camp: B({
    id: "mining_camp",
    name: "Mining Camp",
    desc: "Gold drop-off. Build beside a gold vein to mine efficiently.",
    hp: 480,
    cost: { food: 0, wood: 80, gold: 0 },
    buildTime: 22,
    tiles: 2,
    isDropoff: true,
    dropoffKinds: ["gold"],
  }),
  farm: B({
    id: "farm",
    name: "Farm",
    desc: "A renewable source of food. Villagers work the field around it.",
    hp: 240,
    cost: { food: 0, wood: 60, gold: 0 },
    buildTime: 15,
    tiles: 2,
  }),
  barracks: B({
    id: "barracks",
    name: "Barracks",
    desc: "Trains infantry — Men-at-Arms and anti-cavalry Spearmen.",
    hp: 1000,
    cost: { food: 0, wood: 160, gold: 0 },
    buildTime: 35,
    tiles: 3,
    trains: ["militia", "spearman"],
  }),
  archery_range: B({
    id: "archery_range",
    name: "Archery Range",
    desc: "Trains ranged units — Archers and anti-archer Skirmishers. (Feudal Age)",
    hp: 1000,
    cost: { food: 0, wood: 175, gold: 0 },
    buildTime: 35,
    tiles: 3,
    age: 1,
    requires: "barracks",
    trains: ["archer", "skirmisher"],
  }),
  blacksmith: B({
    id: "blacksmith",
    name: "Blacksmith",
    desc: "Improves your military with attack and armor upgrades. (Feudal Age)",
    hp: 1000,
    cost: { food: 0, wood: 150, gold: 0 },
    buildTime: 35,
    tiles: 2,
    age: 1,
  }),
  market: B({
    id: "market",
    name: "Market",
    desc: "Trade resources for gold. Smooths out shortages in long games. (Feudal Age)",
    hp: 900,
    cost: { food: 0, wood: 175, gold: 0 },
    buildTime: 35,
    tiles: 3,
    age: 1,
  }),
  stable: B({
    id: "stable",
    name: "Stable",
    desc: "Trains Knights — fast, hard-hitting cavalry. (Castle Age)",
    hp: 1000,
    cost: { food: 0, wood: 175, gold: 0 },
    buildTime: 40,
    tiles: 3,
    age: 2,
    requires: "barracks",
    trains: ["knight"],
  }),
  siege_workshop: B({
    id: "siege_workshop",
    name: "Siege Workshop",
    desc: "Builds Catapults and Battering Rams to crack fortifications. (Castle Age)",
    hp: 1000,
    cost: { food: 0, wood: 200, gold: 0 },
    buildTime: 40,
    tiles: 3,
    age: 2,
    trains: ["catapult", "ram"],
  }),
  watch_tower: B({
    id: "watch_tower",
    name: "Watch Tower",
    desc: "Ranged defensive tower. Garrison units to add arrows. (Feudal Age)",
    hp: 1020,
    cost: { food: 0, wood: 50, gold: 25 },
    buildTime: 28,
    tiles: 1,
    age: 1,
    sight: 220,
    attack: 10,
    range: 200,
    attackInterval: 1.0,
    garrisonCap: 5,
  }),
  palisade: B({
    id: "palisade",
    name: "Palisade",
    desc: "Cheap wooden wall. Throws up fast to seal a gap or screen your villagers.",
    hp: 250,
    cost: { food: 0, wood: 5, gold: 0 },
    buildTime: 6,
    tiles: 1,
    sight: 80,
  }),
  stone_wall: B({
    id: "stone_wall",
    name: "Stone Wall",
    desc: "Tough stone rampart. The backbone of a fortress. (Feudal Age)",
    hp: 1800,
    cost: { food: 0, wood: 12, gold: 4 },
    buildTime: 10,
    tiles: 1,
    age: 1,
    sight: 90,
  }),
  gate: B({
    id: "gate",
    name: "Gate",
    desc: "Opens for your troops, shuts against the enemy. Sally out, then bar the door. (Feudal Age)",
    hp: 1600,
    cost: { food: 0, wood: 30, gold: 0 },
    buildTime: 14,
    tiles: 1,
    age: 1,
    sight: 140,
  }),
  castle: B({
    id: "castle",
    name: "Castle",
    desc: "Towering fortress. Heavy arrow fire, huge garrison, trains Monks. (Castle Age)",
    hp: 3600,
    cost: { food: 0, wood: 200, gold: 400 },
    buildTime: 70,
    tiles: 3,
    age: 2,
    sight: 280,
    attack: 22,
    range: 240,
    attackInterval: 0.8,
    garrisonCap: 20,
    trains: ["monk"],
  }),
};

export const BUILDING_IDS = Object.keys(BUILDINGS);
