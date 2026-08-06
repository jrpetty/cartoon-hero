// Commanders — a meta-progression layer the human player (and the AI) leads a
// match with. Each grants passive realm bonuses; some also have an active
// banner power planted on the battlefield. Owned/unlocked via the profile,
// chosen before a match. Deliberately modest — flavour and edge, not dominance.

import { ArmorClass } from "../sim/types";

export interface CommanderPower {
  id: string;
  name: string;
  desc: string;
  cooldown: number; // seconds between plants
  duration: number; // seconds the banner stands
  radius: number; // world units of effect
  villagerGatherMult: number; // gather speed for villagers in range
  militaryRegen: number; // hp/sec healed for military in range
  militaryArmor: number; // bonus armor for military in range
  color: string;
}

export interface CommanderBonus {
  startVillagers?: number;
  startResources?: { food?: number; wood?: number; gold?: number };
  gatherMult?: number; // passive gather speed (×)
  buildMult?: number; // construction/repair speed (×)
  unitHpMult?: number; // all combat units' max HP (×)
  unitArmorBonus?: number; // all combat units' armor (flat)
  popBonus?: number; // extra starting population headroom
  vetMult?: number; // veterancy gained faster (×) — fewer kills per rank
}

export interface CommanderDef {
  id: string;
  name: string;
  title: string;
  desc: string;
  color: string;
  bonus: CommanderBonus;
  power?: CommanderPower;
}

export const COMMANDERS: Record<string, CommanderDef> = {
  steward: {
    id: "steward",
    name: "Aldric",
    title: "The Steward",
    desc: "A master of the early game. Begins each match with two extra villagers already at work.",
    color: "#7ad08a",
    bonus: { startVillagers: 2 },
  },
  quartermaster: {
    id: "quartermaster",
    name: "Mirelle",
    title: "The Quartermaster",
    desc: "Nothing is wasted under her ledger. Every villager gathers 15% faster, all game.",
    color: "#e8c14b",
    bonus: { gatherMult: 1.15 },
  },
  architect: {
    id: "architect",
    name: "Doryan",
    title: "The Architect",
    desc: "Raises walls and halls in a blink — your villagers build and repair twice as fast.",
    color: "#9fb4d8",
    bonus: { buildMult: 2 },
  },
  marshal: {
    id: "marshal",
    name: "Sir Garron",
    title: "The Marshal",
    desc: "Drills a hardier host. Every soldier you field has +8% health and +1 armor.",
    color: "#d8584a",
    bonus: { unitHpMult: 1.08, unitArmorBonus: 1 },
  },
  magnate: {
    id: "magnate",
    name: "Cassia",
    title: "The Magnate",
    desc: "Old coin opens every door. Start with a tidy stockpile of food, wood and gold.",
    color: "#e0a23a",
    bonus: { startResources: { food: 150, wood: 150, gold: 100 } },
  },
  drillmaster: {
    id: "drillmaster",
    name: "Vasco",
    title: "The Drillmaster",
    desc: "Forges veterans from recruits — your units earn veterancy 40% faster from kills.",
    color: "#c89a5a",
    bonus: { vetMult: 1.4 },
  },
  warden: {
    id: "warden",
    name: "Brenna",
    title: "The Warden",
    desc: "Plans for a siege from the first dawn. Begin with +10 population headroom and +1 unit armor.",
    color: "#6fae9a",
    bonus: { popBonus: 10, unitArmorBonus: 1 },
  },
  banneret: {
    id: "banneret",
    name: "Lady Yorath",
    title: "The Banneret",
    desc: "Plants a rallying standard: villagers near it gather 15% faster and soldiers are bolstered and mended.",
    color: "#ffd24a",
    bonus: { startResources: { food: 50 } },
    power: {
      id: "rally_banner",
      name: "Rally Banner",
      desc: "Plant a banner: friendly villagers nearby gather +15% and soldiers gain +2 armor and steady healing for 20s.",
      cooldown: 85,
      duration: 20,
      radius: 300,
      villagerGatherMult: 1.15,
      militaryRegen: 3,
      militaryArmor: 2,
      color: "#ffd24a",
    },
  },
  warpriest: {
    id: "warpriest",
    name: "Father Edmun",
    title: "The Warpriest",
    desc: "Raises a holy standard that knits wounds fast — strong battlefield healing where it stands.",
    color: "#f0e2a8",
    bonus: { unitHpMult: 1.05 },
    power: {
      id: "holy_standard",
      name: "Holy Standard",
      desc: "Plant a sacred standard: nearby soldiers gain +1 armor and heal rapidly for 18s.",
      cooldown: 75,
      duration: 18,
      radius: 280,
      villagerGatherMult: 1,
      militaryRegen: 7,
      militaryArmor: 1,
      color: "#f0e2a8",
    },
  },
};

export const COMMANDER_IDS = Object.keys(COMMANDERS);

/** A one-line summary of a commander's perks, for menus. */
export function commanderPerks(def: CommanderDef): string[] {
  const b = def.bonus;
  const out: string[] = [];
  if (b.startVillagers) out.push(`+${b.startVillagers} starting villagers`);
  if (b.startResources) {
    const r = b.startResources;
    const parts = [r.food && `${r.food} food`, r.wood && `${r.wood} wood`, r.gold && `${r.gold} gold`].filter(Boolean);
    out.push(`Start +${parts.join(", ")}`);
  }
  if (b.gatherMult) out.push(`+${Math.round((b.gatherMult - 1) * 100)}% gather rate`);
  if (b.buildMult) out.push(`${b.buildMult}× build & repair speed`);
  if (b.unitHpMult) out.push(`+${Math.round((b.unitHpMult - 1) * 100)}% unit health`);
  if (b.unitArmorBonus) out.push(`+${b.unitArmorBonus} unit armor`);
  if (b.popBonus) out.push(`+${b.popBonus} starting population`);
  if (b.vetMult) out.push(`+${Math.round((b.vetMult - 1) * 100)}% veterancy gain`);
  if (def.power) out.push(`Power: ${def.power.name}`);
  return out;
}
