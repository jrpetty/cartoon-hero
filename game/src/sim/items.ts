// Warband Tactics relics — equip onto a unit to power it up for the rest of the
// run. Reuses the trait Buff system.
//
// Loot arrives as *components*: small parts that are useful on their own but
// which fuse the moment a unit carries two of them. Every pair of the five
// components makes a distinct full relic (15 recipes), so what you build is a
// real decision rather than a random handout — and a unit can hold three of
// them, so a carry can be assembled deliberately.

import { Buff, applyBuff } from "./traits";

export interface Item {
  id: string;
  name: string;
  short: string;
  desc: string;
  color: string;
  buff: Buff;
  /** Components are the raw loot; two on one unit fuse into a full relic. */
  component?: boolean;
}

/** The five components. Each maps to one stat axis. */
export const COMPONENTS: Record<string, Item> = {
  blade: { id: "blade", name: "Warblade", short: "ATK", desc: "+6 attack", color: "#e0822f", buff: { atk: 6 }, component: true },
  plate: { id: "plate", name: "Ironplate", short: "ARM", desc: "+8 armour", color: "#9aa8b4", buff: { armor: 8 }, component: true },
  hide: { id: "hide", name: "Oxhide", short: "HP", desc: "+140 max HP", color: "#3aa84e", buff: { hp: 140 }, component: true },
  spur: { id: "spur", name: "Rider's Spur", short: "SPD", desc: "+18% speed", color: "#6fd0ff", buff: { speedPct: 18 }, component: true },
  sigil: { id: "sigil", name: "Battle Sigil", short: "PWR", desc: "+12% attack", color: "#cf5fd8", buff: { atkPct: 12 }, component: true },
};
export const COMPONENT_IDS = Object.keys(COMPONENTS);

/** The full relics — each one is the fusion of a specific pair of components. */
export const ITEMS: Record<string, Item> = {
  whetstone: { id: "whetstone", name: "Whetstone", short: "ATK", desc: "+30% attack", color: "#e0822f", buff: { atkPct: 30 } },
  greatmail: { id: "greatmail", name: "Greatmail", short: "ARM", desc: "+12 armour", color: "#9aa8b4", buff: { armor: 12 } },
  warhorn: { id: "warhorn", name: "War Horn", short: "HP", desc: "+30% HP", color: "#3aa84e", buff: { hpPct: 30 } },
  swiftboots: { id: "swiftboots", name: "Swift Boots", short: "SPD", desc: "+30% speed", color: "#6fd0ff", buff: { speedPct: 30 } },
  giantsbelt: { id: "giantsbelt", name: "Giant's Belt", short: "BULK", desc: "+250 max HP", color: "#c9a24a", buff: { hp: 250 } },
  warbanner: { id: "warbanner", name: "War Banner", short: "WAR", desc: "+18% attack & HP", color: "#cf5fd8", buff: { atkPct: 18, hpPct: 18 } },
  bloodaxe: { id: "bloodaxe", name: "Bloodaxe", short: "RAW", desc: "+8 flat attack", color: "#c83a30", buff: { atk: 8 } },
  ironhide: { id: "ironhide", name: "Ironhide", short: "TANK", desc: "+160 HP & +6 armour", color: "#9a6a40", buff: { hp: 160, armor: 6 } },
  berserkbrew: { id: "berserkbrew", name: "Berserker's Brew", short: "RAGE", desc: "+22% attack & +18% speed", color: "#e85a20", buff: { atkPct: 22, speedPct: 18 } },
  towershield: { id: "towershield", name: "Tower Shield", short: "WALL", desc: "+22% HP & +8 armour", color: "#6f8aa6", buff: { hpPct: 22, armor: 8 } },
  warlordcrest: { id: "warlordcrest", name: "Warlord's Crest", short: "LORD", desc: "+14% attack, HP & +12% speed", color: "#e8c040", buff: { atkPct: 14, hpPct: 14, speedPct: 12 } },
  windcloak: { id: "windcloak", name: "Wind Cloak", short: "GALE", desc: "+45% speed & +10% attack", color: "#7fe0d0", buff: { speedPct: 45, atkPct: 10 } },
  // Three relics that exist to complete the recipe grid.
  duelistedge: { id: "duelistedge", name: "Duelist's Edge", short: "DUEL", desc: "+18% attack & +6 armour", color: "#d8a04a", buff: { atkPct: 18, armor: 6 } },
  reaverscleaver: { id: "reaverscleaver", name: "Reaver's Cleaver", short: "REAV", desc: "+20% attack & +120 HP", color: "#b05a3a", buff: { atkPct: 20, hp: 120 } },
  dancersmail: { id: "dancersmail", name: "Dancer's Mail", short: "DANCE", desc: "+8 armour & +25% speed", color: "#8ec0d8", buff: { armor: 8, speedPct: 25 } },
};
export const ITEM_IDS = Object.keys(ITEMS);
export const MAX_ITEMS = 3;

/** Recipe key for a pair of components, order-independent. */
function recipeKey(a: string, b: string): string { return [a, b].sort().join("+"); }

/** Every pair of components → the relic they fuse into. */
export const RECIPES: Record<string, string> = {
  [recipeKey("blade", "blade")]: "bloodaxe",
  [recipeKey("blade", "plate")]: "duelistedge",
  [recipeKey("blade", "hide")]: "reaverscleaver",
  [recipeKey("blade", "spur")]: "berserkbrew",
  [recipeKey("blade", "sigil")]: "whetstone",
  [recipeKey("plate", "plate")]: "greatmail",
  [recipeKey("plate", "hide")]: "ironhide",
  [recipeKey("plate", "spur")]: "dancersmail",
  [recipeKey("plate", "sigil")]: "towershield",
  [recipeKey("hide", "hide")]: "giantsbelt",
  [recipeKey("hide", "spur")]: "windcloak",
  [recipeKey("hide", "sigil")]: "warhorn",
  [recipeKey("spur", "spur")]: "swiftboots",
  [recipeKey("spur", "sigil")]: "warlordcrest",
  [recipeKey("sigil", "sigil")]: "warbanner",
};

/** Look up a component or a full relic by id. */
export function itemDef(id: string): Item | undefined { return ITEMS[id] ?? COMPONENTS[id]; }
export function isComponent(id: string): boolean { return COMPONENTS[id] != null; }

/** The relic two components fuse into (undefined if either isn't a component). */
export function fusionOf(a: string, b: string): string | undefined {
  if (!isComponent(a) || !isComponent(b)) return undefined;
  return RECIPES[recipeKey(a, b)];
}

/**
 * Fuse the first pair of loose components on a unit into their full relic,
 * in place. Returns the relic made, or null if there wasn't a pair.
 */
export function fuseComponents(items: string[]): string | null {
  const idx: number[] = [];
  for (let i = 0; i < items.length && idx.length < 2; i++) if (isComponent(items[i])) idx.push(i);
  if (idx.length < 2) return null;
  const made = fusionOf(items[idx[0]], items[idx[1]]);
  if (!made) return null;
  items.splice(idx[1], 1); // remove the later index first so the earlier stays valid
  items.splice(idx[0], 1);
  items.push(made);
  return made;
}

/** Every relic a component can still become, for the "builds into" tooltip. */
export function buildsInto(componentId: string): string[] {
  if (!isComponent(componentId)) return [];
  const out = new Set<string>();
  for (const other of COMPONENT_IDS) {
    const r = fusionOf(componentId, other);
    if (r) out.add(r);
  }
  return [...out];
}

export function applyItems(u: { maxHp: number; hp: number; attack: number; armor: number; speed: number }, itemIds: string[]) {
  for (const id of itemIds) {
    const it = itemDef(id);
    if (it) applyBuff(u, it.buff);
  }
}
