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
  /** Armor vs ranged hits. Defaults to `armor` when omitted (no melee/pierce
   *  split); set it to model units that resist arrows but not blades. */
  pierceArmor?: number;
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
  hero?: boolean; // unique, levels up in-match, respawns; one per player
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
    hp: 60,
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
    bonus: { [ArmorClass.Cavalry]: 25, [ArmorClass.Siege]: 5 },
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

  horseman: U({
    id: "horseman",
    name: "Horseman",
    desc: "Mounted skirmisher. Pelts foes from horseback and runs down siege; its light barding turns arrows but offers nothing against blades. (Feudal Age)",
    armorClass: ArmorClass.Cavalry,
    hp: 65,
    attack: 8,
    range: 140,
    attackInterval: 1.6,
    armor: 0, // no melee armor — blades cut it down
    pierceArmor: 2, // light barding turns arrows
    speed: 108,
    visionRange: 170,
    radius: 11,
    cost: { food: 70, wood: 0, gold: 40 },
    buildTime: 20,
    pop: 1,
    trainedAt: "stable",
    age: 1,
    ranged: true,
    bonus: { [ArmorClass.Siege]: 14 },
  }),

  crossbow: U({
    id: "crossbow",
    name: "Crossbowman",
    desc: "Heavy ranged punch. Outranges and outshoots the Archer; the backbone of a Castle-age line. (Castle Age)",
    armorClass: ArmorClass.Archer,
    hp: 45,
    attack: 9,
    range: 170,
    attackInterval: 1.5,
    armor: 1,
    speed: 78,
    visionRange: 180,
    radius: 8,
    cost: { food: 0, wood: 25, gold: 55 },
    buildTime: 18,
    pop: 1,
    trainedAt: "archery_range",
    age: 2,
    ranged: true,
    bonus: { [ArmorClass.Infantry]: 4 },
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

  hero: U({
    id: "hero",
    name: "Champion",
    desc: "Your unique hero. Towering in a fight, grows stronger with every kill, and rises again at your Town Center if he falls.",
    armorClass: ArmorClass.Infantry,
    hp: 320,
    attack: 16,
    range: 0,
    attackInterval: 1.0,
    armor: 3,
    speed: 96,
    visionRange: 190,
    radius: 12,
    cost: { food: 150, wood: 0, gold: 120 },
    buildTime: 35,
    pop: 3,
    trainedAt: "town_center",
    age: 0,
    ranged: false,
    hero: true,
    bonus: { [ArmorClass.Archer]: 5, [ArmorClass.Siege]: 6, [ArmorClass.Cavalry]: 6, [ArmorClass.Building]: 4 },
  }),

  trebuchet: U({
    id: "trebuchet",
    name: "Trebuchet",
    desc: "Siege artillery with colossal range. Levels walls and towers from beyond their reach — but glacially slow and helpless up close. (Castle Age)",
    armorClass: ArmorClass.Siege,
    hp: 120,
    attack: 55,
    range: 340,
    attackInterval: 4.6,
    armor: 2,
    speed: 34,
    visionRange: 220,
    radius: 13,
    cost: { food: 0, wood: 200, gold: 200 },
    buildTime: 40,
    pop: 3,
    trainedAt: "siege_workshop",
    age: 2,
    ranged: true,
    aoeRadius: 30,
    bonus: { [ArmorClass.Building]: 110, [ArmorClass.Siege]: 20 },
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
