// Factions — who your realm is, chosen before the match.
//
// Six peoples from different eras. Each has a look of its own (its buildings
// and soldiers are drawn in its style — the team colour stays, so you can
// always tell sides apart), a small set of passive bonuses with a price
// attached, and one or two units of its own. Most of the army is shared, and
// every faction swears the same Oaths as it advances: factions should *play*
// differently and still feel like one game.
//
// Balance is by shape, not by size. Each faction is strong at a different
// point in the match — the Norse hit hardest early, the Kingdom and the
// Ascendancy come into their own late — and each pays for its strength
// somewhere. Nothing is flatly better; an early faction that lets the game run
// long is weaker, not beaten, and a late one has to survive to get there.
// The numbers here are measured by AI round-robin (src/ai/factions_ai.test.ts),
// not guessed.

import type { BoonEffect } from "./boons";
import type { OathRules } from "./oaths";

export type FactionId = "kingdom" | "legion" | "norse" | "shogunate" | "khanate" | "ascendancy";

/** How a faction is drawn. Every field is read by the procedural renderer. */
export interface FactionLook {
  /** Building walls: base stone/masonry tone, and plaster/panel tone. */
  stone: string;
  plaster: string;
  frame: string;
  /** Roof form for halls and houses, and cap form for towers. */
  roof: "hip" | "tile" | "shingle" | "pagoda" | "yurt" | "dome";
  /** Roof material under the team colour (a band or tint rides on top). */
  roofMaterial: string | null;
  /** Standard carried by buildings. */
  banner: "pennant" | "vexillum" | "raven" | "nobori" | "tug" | "holo";
  /** Soldiers. */
  helm: "kingdom" | "galea" | "nasal" | "kabuto" | "steppe" | "visor";
  armour: string;
  shield: "round" | "scutum" | "painted" | "none" | "buckler" | "energy";
  /** An accent that isn't the team colour — gold, bronze, lacquer, glow. */
  accent: string;
  /** Windows and lamps: warm firelight, or a cold glow. */
  light: string;
}

export interface FactionDef {
  id: FactionId;
  name: string;
  /** "Feudal kingdom", "Classical legion" — era and kind, in a few words. */
  era: string;
  tagline: string;
  /** When it is strongest: shown on the picker so the plan is clear. */
  curve: "early" | "mid" | "late" | "steady";
  color: string;
  /** Plain lines: strengths first, then the price. */
  strengths: string[];
  weaknesses: string[];
  /** Base unit → the faction's own unit that replaces it in the roster. */
  replaces: Record<string, string>;
  /** Units only this faction has, on top of the shared roster. */
  extra: string[];
  look: FactionLook;
  apply: (boon: BoonEffect, rules: OathRules) => void;
}

export const FACTIONS: Record<FactionId, FactionDef> = {
  kingdom: {
    id: "kingdom", name: "The Kingdom", era: "High-medieval realm", curve: "late", color: "#5b8fe0",
    tagline: "Castles, knights and the longbow — slow to start, hard to stop.",
    strengths: ["Blacksmith research costs 25% less", "Knights cost 10% less", "Castles and Watch Towers cost 20% less", "Longbowmen at the Archery Range (Crown Age)"],
    weaknesses: ["No early-game bonus"],
    replaces: {},
    extra: ["yeoman"],
    look: {
      stone: "#a8a49a", plaster: "#d8c8a8", frame: "#6e4c2a", roof: "hip", roofMaterial: null,
      banner: "pennant", helm: "kingdom", armour: "#c7cdd4", shield: "round", accent: "#e0c060", light: "#ffd98a",
    },
    apply: (_b, r) => {
      r.techCostMult *= 0.75;
      r.unitCostMult.knight = (r.unitCostMult.knight ?? 1) * 0.9;
      r.buildingCostMult.castle = (r.buildingCostMult.castle ?? 1) * 0.8;
      r.buildingCostMult.watch_tower = (r.buildingCostMult.watch_tower ?? 1) * 0.8;
    },
  },
  legion: {
    id: "legion", name: "The Legion", era: "Classical empire", curve: "mid", color: "#d8574a",
    tagline: "Engineers and heavy infantry — the shield wall that builds a road behind it.",
    strengths: ["Buildings go up 10% faster", "Legionaries replace the Man-at-Arms — they shrug off arrows", "Shieldbearers at the Barracks"],
    weaknesses: ["Cavalry cost 15% more"],
    replaces: { militia: "legionary" },
    extra: ["shieldbearer"],
    look: {
      stone: "#e2d6bc", plaster: "#efe5cf", frame: "#9a7a52", roof: "tile", roofMaterial: "#c2643a",
      banner: "vexillum", helm: "galea", armour: "#c89a52", shield: "scutum", accent: "#e8c060", light: "#ffd98a",
    },
    apply: (_b, r) => {
      r.buildSpeedMult *= 1.1;
      r.cavCostMult *= 1.15;
    },
  },
  norse: {
    id: "norse", name: "The Jarls", era: "Northern raiders", curve: "early", color: "#7fb0c8",
    tagline: "Fast feet and axes — burn them out before they are ready.",
    strengths: ["Infantry move 12% faster", "Infantry deal 25% more damage to buildings", "Wood chopped 12% faster", "Berserkers at the Barracks from the Banner Age"],
    weaknesses: ["Castles cost 25% more", "Blacksmith research costs 10% more"],
    replaces: {},
    extra: ["berserker"],
    look: {
      stone: "#7d7466", plaster: "#8a6a48", frame: "#3e2c1c", roof: "shingle", roofMaterial: "#5a4a32",
      banner: "raven", helm: "nasal", armour: "#8f96a0", shield: "painted", accent: "#c8a060", light: "#ffc070",
    },
    apply: (_b, r) => {
      r.infantrySpeedMult *= 1.12;
      r.infantryBuildingDmgMult *= 1.25;
      r.woodGatherMult *= 1.12;
      r.buildingCostMult.castle = (r.buildingCostMult.castle ?? 1) * 1.25;
      r.techCostMult *= 1.1;
    },
  },
  shogunate: {
    id: "shogunate", name: "The Shogunate", era: "Island warlords", curve: "late", color: "#e0786a",
    tagline: "Fewer soldiers, better ones — every blade is a master's.",
    strengths: ["Melee soldiers attack 12% faster", "Every soldier has 8% more HP", "Villagers have 25% more HP", "Samurai replace the Two-Handed Swordsman"],
    weaknesses: ["Soldiers train 8% slower"],
    replaces: { twohand: "samurai" },
    extra: [],
    look: {
      stone: "#4a4642", plaster: "#efe8da", frame: "#2a2420", roof: "pagoda", roofMaterial: "#3e4046",
      banner: "nobori", helm: "kabuto", armour: "#3a2a2a", shield: "none", accent: "#c8403a", light: "#ffe0a0",
    },
    apply: (b, r) => {
      r.meleeRateMult *= 0.88;
      r.villagerHpMult *= 1.25;
      r.soldierHpMult *= 1.08;
      r.soldierTrainMult *= 1.08;
      void b;
    },
  },
  khanate: {
    id: "khanate", name: "The Khanate", era: "Steppe horse lords", curve: "early", color: "#d8a83a",
    tagline: "The whole realm rides — strike anywhere, never stand still.",
    strengths: ["Cavalry ride 12% faster", "Stables and Archery Ranges cost 25% less", "Yurts house 13 instead of 10", "Horse Archers from the Banner Age; Cataphracts at the Stable"],
    weaknesses: ["Buildings have 20% less HP", "Walls and gates cost 25% more"],
    replaces: {},
    extra: ["horse_archer", "cataphract"],
    look: {
      stone: "#b8a888", plaster: "#eadfc4", frame: "#7a5a36", roof: "yurt", roofMaterial: "#e6dcc6",
      banner: "tug", helm: "steppe", armour: "#8a6a44", shield: "buckler", accent: "#d8b050", light: "#ffcf80",
    },
    apply: (b, r) => {
      b.cavSpeedMult *= 1.12;
      r.buildingCostMult.stable = (r.buildingCostMult.stable ?? 1) * 0.75;
      r.buildingCostMult.archery_range = (r.buildingCostMult.archery_range ?? 1) * 0.75;
      r.housePopBonus += 3;
      b.buildingHpMult *= 0.8;
      b.wallCostMult *= 1.25;
    },
  },
  ascendancy: {
    id: "ascendancy", name: "The Ascendancy", era: "Off-world expedition", curve: "late", color: "#3ad8c8",
    tagline: "Stranded star-farers with better tools and fewer hands.",
    strengths: ["Villagers carry 40% more", "Buildings repair themselves when unharmed", "Pulse Troopers replace the Crossbowman; Skimmers replace the Horseman"],
    weaknesses: ["Soldiers cost 15% more"],
    replaces: { crossbow: "pulse_trooper", horseman: "skimmer" },
    extra: [],
    look: {
      stone: "#c8ccd4", plaster: "#e8ecf0", frame: "#5a6270", roof: "dome", roofMaterial: "#dfe4ea",
      banner: "holo", helm: "visor", armour: "#dfe4ea", shield: "energy", accent: "#3ad8e8", light: "#8ae8ff",
    },
    apply: (b, r) => {
      b.villCarryMult *= 1.4;
      r.buildingRegen += 2;
      r.soldierCostMult *= 1.15;
    },
  },
};

export const FACTION_IDS = Object.keys(FACTIONS) as FactionId[];
export const DEFAULT_FACTION: FactionId = "kingdom";

export function factionOf(id: string | undefined): FactionDef {
  return FACTIONS[(id as FactionId)] ?? FACTIONS[DEFAULT_FACTION];
}

/** Every unit that belongs to one faction, and which. */
export function factionForUnit(unitId: string): FactionDef | undefined {
  return Object.values(FACTIONS).find((f) => f.extra.includes(unitId) || Object.values(f.replaces).includes(unitId));
}

/** The roster a faction trains at a building: shared units, with its own swapped in. */
export function rosterFor(faction: string | undefined, trains: readonly string[]): string[] {
  const f = factionOf(faction);
  const out: string[] = [];
  for (const id of trains) {
    const own = factionForUnit(id);
    if (own && own.id !== f.id) continue; // another people's unit
    if (f.replaces[id]) continue; // replaced by ours, which is on the roster itself
    out.push(id);
  }
  return out;
}
