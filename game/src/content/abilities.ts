import { ArmorClass } from "../sim/types";

// Active, signature abilities. Each is a timed self-buff on a cooldown, fired
// from the command card (or hotkey Q). Effects are applied while `abilityActive`
// is counting down; the whole thing is deterministic off sim time so replays and
// the headless AI matches stay stable. Only unit types listed here have one.

export interface AbilityDef {
  id: string;
  name: string;
  desc: string;
  cooldown: number; // seconds from activation until it can be used again
  duration: number; // seconds the buff stays active
  speedMult?: number; // movement multiplier while active
  attackMult?: number; // damage multiplier while active
  armorBonus?: number; // flat armor added while active
  atkIntervalMult?: number; // <1 = faster shots while active
  bonusVs?: Partial<Record<ArmorClass, number>>; // extra flat damage vs a class
  /** Aura colour for the render glow. */
  color: string;
}

export const ABILITIES: Record<string, AbilityDef> = {
  militia: {
    id: "shield_wall",
    name: "Shield Wall",
    desc: "Lock shields: +6 armour for 8s. Plant your Men-at-Arms to weather arrows and a charge.",
    cooldown: 22,
    duration: 8,
    armorBonus: 6,
    speedMult: 0.6,
    color: "#9fd0ff",
  },
  spearman: {
    id: "brace",
    name: "Brace Spears",
    desc: "Set spears against a charge: +4 armour and devastating bonus damage to cavalry for 7s.",
    cooldown: 20,
    duration: 7,
    armorBonus: 4,
    speedMult: 0.45,
    bonusVs: { [ArmorClass.Cavalry]: 18 },
    color: "#ffd27a",
  },
  knight: {
    id: "charge",
    name: "Cavalry Charge",
    desc: "Spur the horses: +65% speed and +50% damage for 5s. Crash into archers and run down stragglers.",
    cooldown: 24,
    duration: 5,
    speedMult: 1.65,
    attackMult: 1.5,
    color: "#ff8a5c",
  },
  archer: {
    id: "volley",
    name: "Volley",
    desc: "Loose arrows as fast as you can nock them: double fire rate for 6s.",
    cooldown: 20,
    duration: 6,
    atkIntervalMult: 0.5,
    color: "#b6ff8a",
  },
  skirmisher: {
    id: "volley",
    name: "Volley",
    desc: "Rain javelins in a rapid barrage: double throw rate for 6s.",
    cooldown: 20,
    duration: 6,
    atkIntervalMult: 0.5,
    color: "#b6ff8a",
  },
};
