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
  /** For the Factions book: how it plays, who it suits, and how to win with it. */
  guide: FactionGuide;
}

export interface FactionGuide {
  /** 1 forgiving, 2 needs a plan, 3 punishing if misplayed. */
  difficulty: 1 | 2 | 3;
  /** Relative strength early / mid / late, 1–5. */
  power: [number, number, number];
  /** A paragraph on how the faction actually plays. */
  playstyle: string;
  /** "Pick this if you…" lines. */
  suits: string[];
  /** Concrete advice. */
  tips: string[];
  /** What gives it trouble. */
  struggles: string;
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
    guide: {
      difficulty: 1, power: [2, 3, 5],
      playstyle: "The most forgiving realm to learn on. Nothing about the Kingdom is flashy in the first ten minutes — it builds, walls up and researches — but cheap Blacksmith upgrades mean every soldier it fields in the Crown and Empire ages hits harder and lasts longer than anyone else's, and cheap castles let it hold ground it takes. Longbowmen shoot farther than any crossbow or Pulse Trooper.",
      suits: ["You want to learn the game without being punished for it", "You like walls, castles and a strong late army", "You prefer winning big fights over raiding"],
      tips: ["Get to the Crown Age — your bonuses are worth most there", "Spend the Blacksmith discount: every upgrade, every age", "A castle at a choke point is cheap for you and very expensive for them", "Longbowmen behind knights or spearmen win most open fights"],
      struggles: "Early aggression — the Jarls and the Khanate hit before your bonuses matter. Wall and hold the first ten minutes.",
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
    guide: {
      difficulty: 2, power: [3, 5, 3],
      playstyle: "An engineer's army. The Legion builds faster than anyone, so its economy and its forward bases come online early, and its Legionaries replace the Man-at-Arms with a heavier soldier that shrugs off arrows. It is at its best in the middle of the match, pushing a wall of shields forward and building towers and barracks right behind it.",
      suits: ["You like a solid infantry line that just walks forward", "You like building forward — towers, barracks, walls near the enemy", "You want a strong mid-game rather than an all-in rush"],
      tips: ["Legionaries beat archer-heavy armies: take fights into their arrows", "Build a forward barracks — you put it up faster than they can react", "Shieldbearers screen your line while it advances", "Avoid mass cavalry: it costs you 15% more"],
      struggles: "Fast cavalry that refuses to fight your line and raids behind it, and very late armies with full upgrades.",
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
    guide: {
      difficulty: 2, power: [5, 3, 2],
      playstyle: "Raiders. The Jarls' infantry run faster and tear buildings down a quarter faster than anyone else's, their woodcutters are quicker, and Berserkers arrive as soon as the Banner Age. The whole faction is built to hit early and keep hitting — burn houses, kill villagers, make the enemy fight on your schedule. Let the game run long and your smithing and castles cost more than theirs.",
      suits: ["You like to attack first and never stop", "You enjoy raiding, burning and hit-and-run", "You would rather end the game early than out-tech anyone"],
      tips: ["Take Berserkers the moment the Banner Age lands and go", "Target houses and the Town Centre's farms — your infantry burns them fast", "Keep raiding in two places at once; fast feet make that possible", "If the enemy walls up, switch to rams rather than waiting"],
      struggles: "Walls and towers, and any opponent who survives to the Crown Age with an economy intact.",
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
    guide: {
      difficulty: 3, power: [2, 4, 4],
      playstyle: "Fewer soldiers, better ones. Every Shogunate soldier has more HP and every melee soldier strikes faster, the villagers are a quarter tougher, and Samurai replace the Two-Handed Swordsman as the finest duellists in the game. The price is time: soldiers train slower, so every loss hurts and the army has to be used well rather than thrown away.",
      suits: ["You like controlling small, elite armies carefully", "You win by good engagements rather than bigger numbers", "You want villagers that survive early raids"],
      tips: ["Never trade evenly — pick fights where your quality decides it", "Pull units back when they are hurt; replacing them is slow", "Samurai win most infantry duels: send them at the enemy's foot soldiers", "Your villagers survive raids that would kill others' — keep them working longer"],
      struggles: "Being out-numbered by cheap mass armies, and long wars of attrition where train time decides it.",
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
    guide: {
      difficulty: 3, power: [5, 4, 2],
      playstyle: "The whole realm rides. Khanate cavalry are the fastest in the game, Stables and Archery Ranges are cheap, and Horse Archers arrive as early as the Banner Age with Cataphracts later. It wins by movement — raiding where the enemy isn't, and refusing fights it doesn't want. Its buildings are flimsy and walls expensive, so it has to attack rather than hold.",
      suits: ["You like fast units and hit-and-run", "You are happy to micro-manage a moving army", "You would rather be everywhere than defend one place"],
      tips: ["Horse Archers kite infantry forever — never let them catch you", "Raid villagers, not buildings", "Yurts house 13: fewer houses means more for the army", "Don't turtle — your buildings are 20% weaker; defend by counter-attacking"],
      struggles: "Spearmen, pikemen and walls, and anyone who forces a stand-up fight at a choke point.",
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
    guide: {
      difficulty: 2, power: [2, 3, 5],
      playstyle: "Stranded star-farers with better tools and fewer hands. Villagers carry 40% more, so the economy grows with fewer workers, and buildings repair themselves when left alone. Pulse Troopers replace the Crossbowman and Skimmers replace the Horseman — both better than what they stand in for. Everything military costs 15% more, so the Ascendancy plays a patient, efficient game and wins late.",
      suits: ["You like a strong economy and a patient build-up", "You want high-tech units that out-class what they face", "You like the game going long"],
      tips: ["Fewer villagers carrying more — spend the saving on getting to the next age", "Once a raid is beaten off, leave the damage — your buildings mend themselves", "Pulse Troopers out-class the Crossbowmen they replace — make them your ranged core", "Skimmers are your scouts and raiders early"],
      struggles: "Early rushes — soldiers cost more and there are fewer of them at the start.",
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
