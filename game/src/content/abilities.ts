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
//   "guard" – on cast, shields nearby allies with timed armour (guardTimer)
//
// Only the unit types listed here have an ability.

export type AbilityKind = "buff" | "rally" | "slow" | "heal" | "volley" | "cleave" | "guard";

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
    cooldown: 22,
    duration: 1.6,
    radius: 115,
    amount: 11,
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
  // Two-Handed Swordsman: a wide arc that catches everything in front of him.
  twohand: {
    id: "wide_arc", name: "Wide Arc",
    desc: "A two-handed sweep that carves through every enemy pressed around him.",
    kind: "cleave", cooldown: 20, duration: 1.3, radius: 92, amount: 10, statusDuration: 3,
    color: "#e8d08a",
  },
  // Shieldbearer: the reason to field one — he makes the whole line harder to kill.
  shieldbearer: {
    id: "shield_wall", name: "Shield Wall",
    desc: "Shields up along the line: every nearby ally gains armour for 8s. He is why the line holds.",
    kind: "guard", cooldown: 24, duration: 1.5, radius: 160, statusDuration: 8,
    color: "#9fc4e8",
  },
  // Berserker: burns hotter the moment he is let loose.
  berserker: {
    id: "blood_frenzy", name: "Blood Frenzy",
    desc: "Works himself into a fury: strikes 60% faster and 30% harder for 6s. No guard at all.",
    kind: "buff", cooldown: 21, duration: 6, attackMult: 1.3, atkIntervalMult: 0.62,
    color: "#e04a3a",
  },
  // Pikeman: a hedge of points that stops a charge dead rather than trading with it.
  pikeman: {
    id: "pike_hedge", name: "Pike Hedge",
    desc: "Level the pikes — everything nearby is bogged down for 5s, and cavalry is gutted on the points.",
    kind: "slow", cooldown: 20, duration: 1.2, radius: 130, statusDuration: 5,
    color: "#9ad02f",
  },
  // Crossbowman: plant the pavise and shoot from behind it.
  crossbow: {
    id: "pavise", name: "Pavise",
    desc: "Plant the shield and wind the windlass: +6 armour and 40% faster bolts for 7s, rooted in place.",
    kind: "buff", cooldown: 23, duration: 7, armorBonus: 6, atkIntervalMult: 0.6, speedMult: 0.5,
    color: "#a8b4c4",
  },
  // Longbowman: the massed sky-darkening volley, at its own long range.
  longbow: {
    id: "arrow_storm", name: "Arrow Storm",
    desc: "The sky goes dark: a wide, punishing fall of arrows across the enemy formation.",
    kind: "volley", cooldown: 22, duration: 1.1, radius: 120, amount: 12,
    color: "#c8e88a",
  },
  // Hand Cannoneer: one devastating close blast.
  handcannon: {
    id: "scattershot", name: "Scattershot",
    desc: "Cram the barrel with scrap and fire: a brutal cone of shot that shreds packed infantry.",
    kind: "volley", cooldown: 20, duration: 1.0, radius: 76, amount: 17,
    color: "#ffa24a",
  },
  // Javelin: hurl every javelin at once at whatever is riding you down.
  javelin: {
    id: "volley_of_spears", name: "Volley of Spears",
    desc: "Loose the whole bundle at once — heavy damage, and cavalry take the worst of it.",
    kind: "volley", cooldown: 18, duration: 0.9, radius: 88, amount: 11,
    color: "#9ad0a0",
  },
  // Horseman: ride down the shooters, which is the whole point of him.
  horseman: {
    id: "run_down", name: "Run Them Down",
    desc: "Spur into the shooters: +55% speed and savage bonus damage to archers for 5s.",
    kind: "buff", cooldown: 21, duration: 5, speedMult: 1.55,
    bonusVs: { [ArmorClass.Archer]: 14 },
    color: "#ffb06a",
  },
  // Raider: a torch through the economy.
  raider: {
    id: "put_to_torch", name: "Put to the Torch",
    desc: "Burn as you pass: +50% speed and heavy bonus damage to villagers and buildings for 6s.",
    kind: "buff", cooldown: 22, duration: 6, speedMult: 1.5,
    bonusVs: { [ArmorClass.Villager]: 16, [ArmorClass.Building]: 12 },
    color: "#e07a3a",
  },
  // Scout: seeing trouble early and living through it.
  scout: {
    id: "outrider", name: "Outrider",
    desc: "Break into a gallop: +80% speed and +4 armour for 5s. For getting out, not getting stuck in.",
    kind: "buff", cooldown: 18, duration: 5, speedMult: 1.8, armorBonus: 4,
    color: "#a8e0c8",
  },
  // Cataphract: armoured wedge that shrugs off the line it breaks.
  cataphract: {
    id: "iron_wedge", name: "Iron Wedge",
    desc: "Lower the lances and drive through: +8 armour and +45% damage for 6s.",
    kind: "buff", cooldown: 25, duration: 6, armorBonus: 5, attackMult: 1.3,
    color: "#c8b48a",
  },
  // Battlemage: the reason to guard him at all.
  battlemage: {
    id: "firestorm", name: "Firestorm",
    desc: "Calls fire down across a wide stretch of the field. Devastating on a packed formation.",
    kind: "volley", cooldown: 24, duration: 1.4, radius: 140, amount: 22,
    color: "#ff8a2c",
  },
  // Bombard: one enormous shot.
  bombard: {
    id: "point_blank", name: "Point Blank",
    desc: "Depress the barrel and touch it off: an enormous blast that levels whatever is in front of it.",
    kind: "volley", cooldown: 26, duration: 1.5, radius: 118, amount: 32,
    color: "#ffb84a",
  },
  // Trebuchet: wind the counterweight to its limit.
  trebuchet: {
    id: "counterweight", name: "Full Counterweight",
    desc: "Wind it to the stops: far heavier stones, thrown faster, for 9s.",
    kind: "buff", cooldown: 30, duration: 9, attackMult: 1.8, atkIntervalMult: 0.65,
    color: "#d8a06a",
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
export const GUARD_ARMOR = 5; // Shield Wall: flat armour while guarded
