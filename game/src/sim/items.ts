// Warband Tactics relics — equip onto a unit to power it up for the rest of the
// run. Reuses the trait Buff system. You earn one every few rounds; stack up to
// three on a single carry to snowball it.

import { Buff, applyBuff } from "./traits";

export interface Item { id: string; name: string; short: string; desc: string; color: string; buff: Buff; }

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
};
export const ITEM_IDS = Object.keys(ITEMS);
export const MAX_ITEMS = 3;

export function applyItems(u: { maxHp: number; hp: number; attack: number; armor: number; speed: number }, itemIds: string[]) {
  for (const id of itemIds) {
    const it = ITEMS[id];
    if (it) applyBuff(u, it.buff);
  }
}
