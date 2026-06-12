import { ArmorClass } from "../sim/types";

// Active, signature abilities — one per combat unit, each mechanically distinct.
// Fired from the command card (or hotkey Q) on a cooldown. Effects are applied
// deterministically off sim time so replays and headless AI matches stay stable.
//
// `kind` selects how the ability resolves:
//   "buff"  – a timed self-buff (stat multipliers while abilityActive runs)
//   "rally" – on cast, grants nearby allies a timed attack boost (rallyTimer)
//   "slow"  – on cast, saps the speed of nearby enemies (slowTimer)
//   "heal"  – on cast, instantly restores HP to nearby allies
//   "volley"– on cast, rains an instant area-of-effect strike on the foe
//
// Only the unit types listed here have an ability.

export type AbilityKind = "buff" | "rally" | "slow" | "heal" | "volley" | "cleave";

export interface AbilityDef {
  id: string;
  name: string;
  desc: string;
  kind: AbilityKind;
  cooldown: number; // seconds from activation until usable again
  duration: number; // seconds the caster's own aura/buff shows (buff length, or a cast flash)
  // "buff" stat multipliers (also used by Cavalry Charge):
  speedMult?: number;
  attackMult?: number;
  armorBonus?: number;
  atkIntervalMult?: number; // <1 = faster shots
  bonusVs?: Partial<Record<ArmorClass, number>>;
  // Area abilities (rally / slow / heal / volley):
  radius?: number; // effect radius in world units
  statusDuration?: number; // how long the applied rally/slow lasts
  amount?: number; // heal HP, or volley damage
  /** Aura colour for the render glow. */
  color: string;
}

export const ABILITIES: Record<string, AbilityDef> = {
  // Champion: a heroic sweep that wounds every foe around him and emboldens
  // every ally — the centrepiece of a hero-led push.
  hero: {
    id: "heroic_cleave",
    name: "Heroic Cleave",
    desc: "A sweeping blow: heavy damage to all enemies around the Champion, and a rallying surge for nearby allies.",
    kind: "cleave",
    cooldown: 15,
    duration: 1.6,
    radius: 115,
    amount: 24,
    statusDuration: 5,
    color: "#ffd24a",
  },
  // Villager: not a fighter — a panic button to survive a raid and run for it.
  villager: {
    id: "take_cover",
    name: "Take Cover",
    desc: "Down tools and run! +8 armour and +50% speed for 5s to escape a raid and reach safety.",
    kind: "buff",
    cooldown: 25,
    duration: 5,
    armorBonus: 8,
    speedMult: 1.5,
    color: "#9fd0ff",
  },
  // Man-at-Arms: a frontline leader who lifts the whole battle line.
  militia: {
    id: "war_cry",
    name: "War Cry",
    desc: "Bellow a rallying cry — every nearby ally hits 40% harder for 6s. Time it as the lines meet.",
    kind: "rally",
    cooldown: 26,
    duration: 1.4,
    radius: 150,
    statusDuration: 6,
    color: "#ffcf5a",
  },
  // Spearman: plant against a charge and gut the cavalry that hits you.
  spearman: {
    id: "brace",
    name: "Brace Spears",
    desc: "Set spears against a charge: +4 armour and devastating bonus damage to cavalry for 7s.",
    kind: "buff",
    cooldown: 20,
    duration: 7,
    armorBonus: 4,
    speedMult: 0.45,
    bonusVs: { [ArmorClass.Cavalry]: 18 },
    color: "#ffd27a",
  },
  // Archer: call down a concentrated barrage on the enemy you're firing at.
  archer: {
    id: "arrow_volley",
    name: "Arrow Volley",
    desc: "Loose a massed volley that rains down on your target, striking everything around it at once.",
    kind: "volley",
    cooldown: 19,
    duration: 0.9,
    radius: 84,
    amount: 16,
    color: "#b6ff8a",
  },
  // Skirmisher: scatter caltrops to hobble whoever's chasing you.
  skirmisher: {
    id: "hamstring",
    name: "Caltrops",
    desc: "Scatter caltrops — every enemy nearby is slowed to a crawl for 4s. Kite them to death.",
    kind: "slow",
    cooldown: 18,
    duration: 0.9,
    radius: 120,
    statusDuration: 4,
    color: "#8ad6ff",
  },
  // Knight: the iconic gallop — fast and crushing for a few seconds.
  knight: {
    id: "charge",
    name: "Cavalry Charge",
    desc: "Spur the horses: +65% speed and +50% damage for 5s. Crash into archers and run down stragglers.",
    kind: "buff",
    cooldown: 24,
    duration: 5,
    speedMult: 1.65,
    attackMult: 1.5,
    color: "#ff8a5c",
  },
  // Catapult: load burning pitch for a short, brutal bombardment.
  catapult: {
    id: "incendiary",
    name: "Burning Pitch",
    desc: "Load incendiary shot: heavier rocks, hurled faster, for 8s. Flatten a base in a hurry.",
    kind: "buff",
    cooldown: 28,
    duration: 8,
    attackMult: 1.7,
    atkIntervalMult: 0.6,
    color: "#ff7a2c",
  },
  // Ram: throw the crew into a frenzy against timber and stone.
  ram: {
    id: "battering_frenzy",
    name: "Battering Frenzy",
    desc: "The crew heaves in a fury — double the battering speed against walls and buildings for 6s.",
    kind: "buff",
    cooldown: 22,
    duration: 6,
    attackMult: 1.3,
    atkIntervalMult: 0.45,
    color: "#e0683a",
  },
  // Monk: a pulse of holy light that mends the wounded around him.
  monk: {
    id: "sanctuary",
    name: "Sanctuary",
    desc: "A pulse of holy light instantly heals every ally around the monk for 45 HP.",
    kind: "heal",
    cooldown: 22,
    duration: 1.2,
    radius: 170,
    amount: 45,
    color: "#ffe9a8",
  },
};

// Status strengths shared by the area abilities.
export const SLOW_MULT = 0.45; // Caltrops: fraction of normal speed while slowed
export const RALLY_ATK_MULT = 1.4; // War Cry: attack multiplier while rallied
