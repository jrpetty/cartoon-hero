import { ArmorClass } from "../sim/types";

export interface UnitDef {
  id: string;
  name: string;
  desc: string;
  armorClass: ArmorClass;
  hp: number;
  attack: number;
  range: number; // 0 = melee (uses contact range)
  attackInterval: number; // seconds
  armor: number;
  speed: number; // world units / sec
  visionRange: number;
  radius: number;
  cost: { food: number; wood: number; gold: number };
  buildTime: number; // seconds to train
  pop: number;
  trainedAt: string; // building def id
  age: number; // minimum age index required (0 = Dark)
  ranged: boolean;
  /** Extra damage vs an armor class — the rock-paper-scissors triangle. */
  bonus: Partial<Record<ArmorClass, number>>;
  /** Special role flags. */
  canGather?: boolean;
  canBuild?: boolean;
  canRepair?: boolean;
  healer?: boolean;
  aoeRadius?: number; // siege splash
}

const U = (d: UnitDef) => d;

export const UNITS: Record<string, UnitDef> = {
  villager: U({
    id: "villager",
    name: "Villager",
    desc: "Gathers food, wood and gold; builds structures. Defenseless in a real fight.",
    armorClass: ArmorClass.Villager,
    hp: 40,
    attack: 3,
    range: 0,
    attackInterval: 1.2,
    armor: 0,
    speed: 70,
    visionRange: 120,
    radius: 8,
    cost: { food: 50, wood: 0, gold: 0 },
    buildTime: 12,
    pop: 1,
    trainedAt: "town_center",
    age: 0,
    ranged: false,
    bonus: {},
    canGather: true,
    canBuild: true,
    canRepair: true,
  }),

  militia: U({
    id: "militia",
    name: "Man-at-Arms",
    desc: "Cheap, sturdy frontline infantry. Closes on archers; melts to massed cavalry.",
    armorClass: ArmorClass.Infantry,
    hp: 70,
    attack: 8,
    range: 0,
    attackInterval: 1.1,
    armor: 1,
    speed: 78,
    visionRange: 120,
    radius: 9,
    cost: { food: 60, wood: 0, gold: 20 },
    buildTime: 14,
    pop: 1,
    trainedAt: "barracks",
    age: 0,
    ranged: false,
    bonus: { [ArmorClass.Archer]: 4, [ArmorClass.Building]: 2 },
  }),

  spearman: U({
    id: "spearman",
    name: "Spearman",
    desc: "Anti-cavalry specialist. Brutal vs knights, weak against everything else.",
    armorClass: ArmorClass.Infantry,
    hp: 55,
    attack: 5,
    range: 0,
    attackInterval: 1.1,
    armor: 0,
    speed: 80,
    visionRange: 120,
    radius: 9,
    cost: { food: 35, wood: 25, gold: 0 },
    buildTime: 13,
    pop: 1,
    trainedAt: "barracks",
    age: 0,
    ranged: false,
    bonus: { [ArmorClass.Cavalry]: 16, [ArmorClass.Siege]: 4 },
  }),

  archer: U({
    id: "archer",
    name: "Archer",
    desc: "Ranged damage that shreds infantry. Fragile — keep it back, fear the cavalry.",
    armorClass: ArmorClass.Archer,
    hp: 38,
    attack: 6,
    range: 150,
    attackInterval: 1.4,
    armor: 0,
    speed: 80,
    visionRange: 170,
    radius: 8,
    cost: { food: 0, wood: 25, gold: 45 },
    buildTime: 16,
    pop: 1,
    trainedAt: "archery_range",
    age: 1,
    ranged: true,
    bonus: { [ArmorClass.Infantry]: 3 },
  }),

  skirmisher: U({
    id: "skirmisher",
    name: "Skirmisher",
    desc: "Cheap anti-archer ranged unit. Counters bowmen; poor against everything melee.",
    armorClass: ArmorClass.Archer,
    hp: 42,
    attack: 4,
    range: 130,
    attackInterval: 1.5,
    armor: 1,
    speed: 80,
    visionRange: 160,
    radius: 8,
    cost: { food: 35, wood: 35, gold: 0 },
    buildTime: 16,
    pop: 1,
    trainedAt: "archery_range",
    age: 1,
    ranged: true,
    bonus: { [ArmorClass.Archer]: 6 },
  }),

  knight: U({
    id: "knight",
    name: "Knight",
    desc: "Fast, heavy cavalry. Runs down archers, siege and villagers. Hates spears.",
    armorClass: ArmorClass.Cavalry,
    hp: 130,
    attack: 12,
    range: 0,
    attackInterval: 1.2,
    armor: 2,
    speed: 115,
    visionRange: 130,
    radius: 11,
    cost: { food: 70, wood: 0, gold: 75 },
    buildTime: 20,
    pop: 1,
    trainedAt: "stable",
    age: 2,
    ranged: false,
    bonus: { [ArmorClass.Archer]: 6, [ArmorClass.Siege]: 8, [ArmorClass.Villager]: 6 },
  }),

  catapult: U({
    id: "catapult",
    name: "Catapult",
    desc: "Long-range siege. Devastates buildings and tight formations; helpless up close.",
    armorClass: ArmorClass.Siege,
    hp: 90,
    attack: 24,
    range: 200,
    attackInterval: 3.2,
    armor: 1,
    speed: 45,
    visionRange: 180,
    radius: 12,
    cost: { food: 0, wood: 160, gold: 80 },
    buildTime: 30,
    pop: 2,
    trainedAt: "siege_workshop",
    age: 2,
    ranged: true,
    aoeRadius: 36,
    bonus: { [ArmorClass.Building]: 40, [ArmorClass.Infantry]: 8, [ArmorClass.Archer]: 8 },
  }),

  ram: U({
    id: "ram",
    name: "Battering Ram",
    desc: "Armored siege engine built to break walls and buildings. Slow and clumsy vs troops.",
    armorClass: ArmorClass.Siege,
    hp: 200,
    attack: 6,
    range: 0,
    attackInterval: 2.5,
    armor: 4,
    speed: 50,
    visionRange: 120,
    radius: 13,
    cost: { food: 0, wood: 160, gold: 40 },
    buildTime: 28,
    pop: 2,
    trainedAt: "siege_workshop",
    age: 2,
    ranged: false,
    bonus: { [ArmorClass.Building]: 50 },
  }),

  monk: U({
    id: "monk",
    name: "Monk",
    desc: "Castle-age support. Heals nearby allies over time. No frontline value.",
    armorClass: ArmorClass.Infantry,
    hp: 45,
    attack: 0,
    range: 0,
    attackInterval: 1,
    armor: 0,
    speed: 70,
    visionRange: 150,
    radius: 8,
    cost: { food: 0, wood: 0, gold: 100 },
    buildTime: 24,
    pop: 1,
    trainedAt: "castle",
    age: 2,
    ranged: false,
    healer: true,
    bonus: {},
  }),
};

export const UNIT_IDS = Object.keys(UNITS);
