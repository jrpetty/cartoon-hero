// Oaths — this game's answer to civilisations.
//
// Civilisations hand every realm an identity before the match starts: you pick
// a people, and you are locked into its bonuses, its unique unit and its blind
// spots whatever the map turns out to be and whoever you meet. Oaths build the
// identity *during* the match instead. Every time a realm advances an age it
// swears one Oath of three, and what it swore shapes it from then on:
//
//   Banner Age — how will the realm grow?        Plough · Sword · Hearth
//   Crown Age  — what will its army be?          Lance · Bow · Shield
//   Empire Age — what will it be remembered for? Coin · Engine · Crown
//
// Three choices of three is twenty-seven realms, arrived at by decisions made
// with the map in view and the enemy scouted — which is the part civilisations
// can't offer. Oaths are public: everyone is told what a realm swears, so the
// identity is something to read and answer, not a surprise at the first fight.
// And they balance one choice against its two siblings rather than a people
// against every other people.
//
// Each Oath is a passive bonus plus a *signature*: a unit only its sworn can
// train, or a rule only they play by.

import type { BoonEffect } from "./boons";

/**
 * Rules a realm's faction and Oaths can change that no boon touches. Neutral
 * by default. The faction's are folded in first, then each Oath's, into the
 * one block the simulation reads.
 */
export interface OathRules {
  /** Faction: Blacksmith research cost (×). */
  techCostMult: number;
  /** Faction: cost (×) by building type. */
  buildingCostMult: Record<string, number>;
  /** Faction: construction speed (×). */
  buildSpeedMult: number;
  /** Faction: soldier and cavalry cost (×), and cost (×) by unit type. */
  soldierCostMult: number;
  unitCostMult: Record<string, number>;
  cavCostMult: number;
  /** Faction: infantry speed (×) and damage to buildings (×). */
  infantrySpeedMult: number;
  infantryBuildingDmgMult: number;
  /** Faction: wood gathering (×). */
  woodGatherMult: number;
  /** Faction: melee attack interval (×, <1 faster). */
  meleeRateMult: number;
  /** Faction: villager max HP (×), and every soldier's (×). */
  villagerHpMult: number;
  soldierHpMult: number;
  /** Faction: soldier training time (×). */
  soldierTrainMult: number;
  /** Faction: extra population per House. */
  housePopBonus: number;
  /** Food a new field holds (×). */
  farmFoodMult: number;
  /** Spent fields replant without the wood. */
  freeReseed: boolean;
  /** HP per second a building mends once it has gone ten seconds unhurt. */
  buildingRegen: number;
  /** Training time (×, <1 faster) by kind of unit. */
  villagerTrainMult: number;
  infantryTrainMult: number;
  cavTrainMult: number;
  siegeTrainMult: number;
  /** Training time of the Oath's own signature unit (commander affinity). */
  signatureTrainMult: number;
  /** Flat armour and max HP (×) for infantry. */
  infantryArmor: number;
  infantryHpMult: number;
  /** Cavalry max HP (×). */
  cavHpMult: number;
  /** Trade Cart pay (×), and extra gold per Market exchange. */
  tradeMult: number;
  tradeRateBonus: number;
  /** Gold gathering speed (×). */
  goldGatherMult: number;
  /** Gold per second paid by each Market, for up to three of them. */
  tithePerMarket: number;
  /** Siege max HP and attack (×). */
  siegeHpMult: number;
  siegeAtkMult: number;
  /** Veterancy earned faster (×). */
  vetMult: number;
  /** The Champion's time to rise again (×, <1 faster). */
  heroRespawnMult: number;
}

export function emptyOathRules(): OathRules {
  return {
    techCostMult: 1, buildingCostMult: {}, buildSpeedMult: 1, soldierCostMult: 1, unitCostMult: {}, cavCostMult: 1,
    infantrySpeedMult: 1, infantryBuildingDmgMult: 1, woodGatherMult: 1, meleeRateMult: 1,
    villagerHpMult: 1, soldierHpMult: 1, soldierTrainMult: 1, housePopBonus: 0,
    farmFoodMult: 1, freeReseed: false, buildingRegen: 0,
    villagerTrainMult: 1, infantryTrainMult: 1, cavTrainMult: 1, siegeTrainMult: 1, signatureTrainMult: 1,
    infantryArmor: 0, infantryHpMult: 1, cavHpMult: 1, tradeMult: 1, tradeRateBonus: 0, goldGatherMult: 1,
    tithePerMarket: 0, siegeHpMult: 1, siegeAtkMult: 1, vetMult: 1, heroRespawnMult: 1,
  };
}

/** How many Markets a Tithe counts — enough to reward trade, not a farm of them. */
export const TITHE_MAX_MARKETS = 3;
/** How long a building must go unhurt before a Hearth-sworn realm mends it. */
export const MEND_AFTER_SEC = 10;
/**
 * Swearing your commander's favoured Oath: every effect it has is this much
 * stronger, and its signature unit trains faster. This is what ties the
 * commander you bring to the realm you become — a Quartermaster *can* swear
 * the Lance, but Mirelle's own Oath is the Bow.
 */
export const AFFINITY = 1.5;
export const AFFINITY_TRAIN_MULT = 0.75;

export type OathKind = "growth" | "war" | "guard";

export interface OathDef {
  id: string;
  /** The age this Oath is sworn on reaching (1 Banner, 2 Crown, 3 Empire). */
  age: 1 | 2 | 3;
  name: string;
  /** One word, for tight labels: "Plough". */
  short: string;
  motto: string;
  kind: OathKind;
  color: string;
  /**
   * What it does, one plain line each, at a given strength (1, or AFFINITY
   * when sworn under its commander) — so the card shows the real numbers.
   */
  lines: (s: number) => string[];
  /** The signature unit, if the Oath has one. */
  unit?: string;
  /** Otherwise, the signature rule — the one thing only its sworn do. */
  signature?: { title: string; text: string };
  apply: (boon: BoonEffect, rules: OathRules, s: number) => void;
}

/** "+50%" for a 0.5 bonus at strength s. */
const pc = (v: number, s: number) => `${Math.round(v * s * 100)}%`;
const up = (m: number, v: number, s: number) => m * (1 + v * s);
const down = (m: number, v: number, s: number) => m * Math.max(0.2, 1 - v * s);

export const OATHS: Record<string, OathDef> = {
  // ---------------------------------------------------------- Banner Age --
  // How will the realm grow? A boom, a rush, or a fortress.
  plough: {
    id: "plough", age: 1, name: "Oath of the Plough", short: "Plough", kind: "growth",
    motto: "Bread before banners.", color: "#d9b44a",
    lines: (s) => [
      `Villagers train ${pc(0.1, s)} faster`,
      `Fields hold ${pc(0.3, s)} more food, and replant for free`,
      `Food gathered ${pc(0.08, s)} faster`,
    ],
    signature: { title: "Endless Fields", text: "Farms replant themselves at no cost" },
    apply: (b, r, s) => {
      r.villagerTrainMult = down(r.villagerTrainMult, 0.1, s);
      r.farmFoodMult = up(r.farmFoodMult, 0.3, s);
      r.freeReseed = true;
      b.foodGatherMult = up(b.foodGatherMult, 0.08, s);
    },
  },
  sword: {
    id: "sword", age: 1, name: "Oath of the Sword", short: "Sword", kind: "war",
    motto: "Strike before they are ready.", color: "#d8574a",
    lines: (s) => [
      `Infantry train ${pc(0.3, s)} faster and march ${pc(0.06, s)} faster`,
      `Infantry hit ${pc(0.15, s)} harder`,
      "Unlocks the Sworn Blade at the Barracks",
    ],
    unit: "sworn_blade",
    apply: (b, r, s) => {
      r.infantryTrainMult = down(r.infantryTrainMult, 0.3, s);
      r.infantrySpeedMult = up(r.infantrySpeedMult, 0.06, s);
      b.atkMultInfantry = up(b.atkMultInfantry, 0.15, s);
    },
  },
  hearth: {
    id: "hearth", age: 1, name: "Oath of the Hearth", short: "Hearth", kind: "guard",
    motto: "What is ours stays ours.", color: "#6aa5d8",
    lines: (s) => [
      `Buildings have ${pc(0.3, s)} more HP and mend when left unharmed`,
      `Villagers build and repair ${pc(0.25, s)} faster`,
      `Towers and Town Centres shoot ${pc(0.3, s)} faster; walls cost ${pc(0.35, s)} less`,
    ],
    signature: { title: "Mending Stones", text: "Buildings heal after 10 seconds unhurt" },
    apply: (b, r, s) => {
      b.buildingHpMult = up(b.buildingHpMult, 0.3, s);
      b.wallHpMult = up(b.wallHpMult, 0.3, s);
      r.buildingRegen += 4 * s;
      r.buildSpeedMult = up(r.buildSpeedMult, 0.25, s);
      b.towerCdMult = down(b.towerCdMult, 0.3, s);
      b.wallCostMult = down(b.wallCostMult, 0.35, s);
    },
  },

  // ----------------------------------------------------------- Crown Age --
  // What will the army be? Horse, bow, or pike.
  lance: {
    id: "lance", age: 2, name: "Oath of the Lance", short: "Lance", kind: "war",
    motto: "The charge decides the day.", color: "#e0a040",
    lines: (s) => [
      `Cavalry have ${pc(0.15, s)} more HP, ride ${pc(0.08, s)} faster`,
      `Cavalry train ${pc(0.2, s)} faster`,
      "Unlocks the Lancer at the Stable — its charge hits three times as hard",
    ],
    unit: "lancer",
    apply: (b, r, s) => {
      r.cavHpMult = up(r.cavHpMult, 0.15, s);
      b.cavSpeedMult = up(b.cavSpeedMult, 0.08, s);
      r.cavTrainMult = down(r.cavTrainMult, 0.2, s);
    },
  },
  bow: {
    id: "bow", age: 2, name: "Oath of the Bow", short: "Bow", kind: "war",
    motto: "Death from the treeline.", color: "#6fbf5a",
    lines: (s) => [
      `Ranged units shoot ${Math.round(24 * s)} further and ${pc(0.1, s)} harder`,
      "Unlocks the Ranger at the Archery Range — stays hidden in woods even while shooting",
    ],
    unit: "ranger",
    apply: (b, _r, s) => {
      b.archerRangeBonus += 24 * s;
      b.atkMultArcher = up(b.atkMultArcher, 0.1, s);
    },
  },
  shield: {
    id: "shield", age: 2, name: "Oath of the Shield", short: "Shield", kind: "guard",
    motto: "Hold the line.", color: "#9aa7b8",
    lines: (s) => [
      `Infantry +${Math.round(2 * s)} armour and ${pc(0.15, s)} more HP`,
      "Unlocks the Halberdier at the Barracks — ruin for cavalry and rams",
    ],
    unit: "halberdier",
    apply: (_b, r, s) => {
      r.infantryArmor += Math.round(2 * s);
      r.infantryHpMult = up(r.infantryHpMult, 0.15, s);
    },
  },

  // ---------------------------------------------------------- Empire Age --
  // What will the realm be remembered for? Wealth, siegecraft, or the crown.
  coin: {
    id: "coin", age: 3, name: "Oath of the Coin", short: "Coin", kind: "growth",
    motto: "Every road leads to our markets.", color: "#f0cf5a",
    lines: (s) => [
      `Trade Carts earn ${pc(0.5, s)} more; the Market pays ${Math.round(12 * s)} more per trade`,
      `Gold mined ${pc(0.2, s)} faster`,
      `Tithe: each Market pays ${Math.round(30 * s)} gold a minute (up to 3)`,
    ],
    signature: { title: "The Tithe", text: "Your Markets pay gold whether or not anyone trades" },
    apply: (_b, r, s) => {
      r.tradeMult = up(r.tradeMult, 0.5, s);
      r.tradeRateBonus += Math.round(12 * s);
      r.goldGatherMult = up(r.goldGatherMult, 0.2, s);
      r.tithePerMarket += 0.5 * s;
    },
  },
  engine: {
    id: "engine", age: 3, name: "Oath of the Engine", short: "Engine", kind: "war",
    motto: "Walls are only a delay.", color: "#b07a4a",
    lines: (s) => [
      `Siege weapons have ${pc(0.3, s)} more HP and hit ${pc(0.25, s)} harder`,
      `Siege weapons build ${pc(0.3, s)} faster`,
      "Unlocks the Great Bombard at the Siege Workshop — outranges towers and castles",
    ],
    unit: "great_bombard",
    apply: (_b, r, s) => {
      r.siegeHpMult = up(r.siegeHpMult, 0.3, s);
      r.siegeAtkMult = up(r.siegeAtkMult, 0.25, s);
      r.siegeTrainMult = down(r.siegeTrainMult, 0.3, s);
    },
  },
  crown: {
    id: "crown", age: 3, name: "Oath of the Crown", short: "Crown", kind: "guard",
    motto: "Long live the realm.", color: "#c890e8",
    lines: (s) => [
      `Every unit has ${pc(0.12, s)} more HP`,
      `Veterancy comes ${pc(0.6, s)} faster; the Champion rises ${pc(0.5, s)} sooner`,
      "Unlocks the Royal Guard at the Castle",
    ],
    unit: "royal_guard",
    apply: (b, r, s) => {
      b.hpMult = up(b.hpMult, 0.12, s);
      r.vetMult = up(r.vetMult, 0.6, s);
      r.heroRespawnMult = down(r.heroRespawnMult, 0.5, s);
    },
  },
};

/** The three Oaths offered on reaching `age`, in a stable order. */
export function oathsForAge(age: number): OathDef[] {
  return Object.values(OATHS).filter((o) => o.age === age);
}

/** The Oath whose signature unit this is, if any. */
export function oathForUnit(unitId: string): OathDef | undefined {
  return Object.values(OATHS).find((o) => o.unit === unitId);
}

/**
 * Fold a realm's sworn Oaths into its boon effect and a fresh rules block.
 * `favoured` is its commander's Oath, sworn at AFFINITY strength.
 */
export function applyOaths(ids: readonly string[], boon: BoonEffect, favoured?: string, faction?: { apply: (b: BoonEffect, r: OathRules) => void }): OathRules {
  const rules = emptyOathRules();
  faction?.apply(boon, rules);
  for (const id of ids) {
    const o = OATHS[id];
    if (!o) continue;
    const strong = id === favoured;
    o.apply(boon, rules, strong ? AFFINITY : 1);
    if (strong && o.unit) rules.signatureTrainMult *= AFFINITY_TRAIN_MULT;
  }
  return rules;
}

/** Oath chips for tight spaces (HUD, scoreboard): the short names, coloured. */
export function oathChips(ids: readonly string[]): { label: string; color: string }[] {
  return ids.map((id) => OATHS[id]).filter(Boolean).map((o) => ({ label: o.short, color: o.color }));
}
