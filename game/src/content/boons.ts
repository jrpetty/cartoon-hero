// Boons — the case-unlocked customization layer. Each boon comes in rarity
// tiers (Common → One-of-a-Kind); a higher tier is a STRONGER version of the
// same effect (not a different, strictly-better boon). Players equip one boon
// per category. Effects are folded into per-team bonus fields at match start.
//
// Tuning: the headline percentage buffs scale ~8% (Common) → ~24% (Mythic),
// interpolated by BOON_RARITY_SCALE. Flat effects (armor, range, regen) get
// their own gentle curves.

export type BoonCategory = "offensive" | "defensive" | "supportive";

// Multiplier applied to a boon's base magnitude by rarity index (0..5).
// 0.08 * this lands on 8% / 11.2% / 14.4% / 17.6% / 20.8% / 24%.
export const BOON_RARITY_SCALE = [1.0, 1.4, 1.8, 2.2, 2.6, 3.0];

/** All per-team modifiers a boon loadout can contribute (neutral defaults). */
export interface BoonEffect {
  atkMultInfantry: number;
  atkMultArcher: number;
  atkMultCavalry: number;
  archerRangeBonus: number; // px
  armySpeedMult: number; // all units
  cavSpeedMult: number; // cavalry only (charge feel)
  raiderMult: number; // bonus damage vs villagers & buildings
  hpMult: number; // all units
  armorBonus: number; // flat, all units
  buildingHpMult: number;
  buildCostMult: number; // <1 cheaper (non-wall buildings)
  towerCdMult: number; // <1 faster defensive fire
  towerRangeBonus: number; // px
  wallHpMult: number;
  wallCostMult: number; // <1 cheaper walls/gates
  foodGatherMult: number;
  regenRate: number; // hp/sec out of combat
  unitCostMult: number; // <1 cheaper units
  trainSpeedMult: number; // <1 faster training
  villCarryMult: number;
  villSpeedMult: number;
  visionMult: number;
}

export function emptyBoonEffect(): BoonEffect {
  return {
    atkMultInfantry: 1, atkMultArcher: 1, atkMultCavalry: 1, archerRangeBonus: 0,
    armySpeedMult: 1, cavSpeedMult: 1, raiderMult: 1, hpMult: 1, armorBonus: 0,
    buildingHpMult: 1, buildCostMult: 1, towerCdMult: 1, towerRangeBonus: 0,
    wallHpMult: 1, wallCostMult: 1, foodGatherMult: 1, regenRate: 0,
    unitCostMult: 1, trainSpeedMult: 1, villCarryMult: 1, villSpeedMult: 1, visionMult: 1,
  };
}

const pct = (base: number, scale: number) => Math.round(base * scale * 100); // for tooltips

export interface BoonDef {
  id: string;
  name: string;
  category: BoonCategory;
  blurb: string; // short identity line
  /** Human-readable magnitude at a given rarity (for tooltips). */
  detail: (rarity: number) => string;
  /** Fold this boon's effect (at the given rarity) into the aggregate. */
  apply: (eff: BoonEffect, rarity: number) => void;
}

const S = (rarity: number) => BOON_RARITY_SCALE[rarity] ?? 1;
// Flat per-rarity curves for non-percentage effects.
const ARMOR_STEPS = [1, 1, 2, 2, 3, 3];

export const BOONS: BoonDef[] = [
  // ----------------------------------------------------------- offensive --
  {
    id: "whetstones", name: "Whetstones", category: "offensive",
    blurb: "Infantry hit harder.",
    detail: (r) => `+${pct(0.08, S(r))}% infantry attack`,
    apply: (e, r) => { e.atkMultInfantry *= 1 + 0.08 * S(r); },
  },
  {
    id: "bodkin_points", name: "Bodkin Points", category: "offensive",
    blurb: "Archers gain attack and a little range.",
    detail: (r) => `+${pct(0.08, S(r))}% archer attack, +${Math.round(6 * S(r))} range`,
    apply: (e, r) => { e.atkMultArcher *= 1 + 0.08 * S(r); e.archerRangeBonus += 6 * S(r); },
  },
  {
    id: "destrier", name: "Destrier", category: "offensive",
    blurb: "Cavalry hit harder and ride faster.",
    detail: (r) => `+${pct(0.08, S(r))}% cavalry attack, +${pct(0.04, S(r))}% cavalry speed`,
    apply: (e, r) => { e.atkMultCavalry *= 1 + 0.08 * S(r); e.cavSpeedMult *= 1 + 0.04 * S(r); },
  },
  {
    id: "warpath", name: "Warpath", category: "offensive",
    blurb: "The whole army marches faster — relentless pushes and raids.",
    detail: (r) => `+${pct(0.05, S(r))}% army move speed`,
    apply: (e, r) => { e.armySpeedMult *= 1 + 0.05 * S(r); },
  },
  {
    id: "reaver", name: "Reaver", category: "offensive",
    blurb: "Extra damage to villagers and buildings — eco denial.",
    detail: (r) => `+${pct(0.1, S(r))}% damage to villagers & buildings`,
    apply: (e, r) => { e.raiderMult *= 1 + 0.1 * S(r); },
  },

  // ----------------------------------------------------------- defensive --
  {
    id: "iron_discipline", name: "Iron Discipline", category: "defensive",
    blurb: "Every unit is tougher.",
    detail: (r) => `+${pct(0.08, S(r))}% unit HP`,
    apply: (e, r) => { e.hpMult *= 1 + 0.08 * S(r); },
  },
  {
    id: "pavise", name: "Pavise", category: "defensive",
    blurb: "Units gain armor — superb against archers.",
    detail: (r) => `+${ARMOR_STEPS[r] ?? 1} armor to all units`,
    apply: (e, r) => { e.armorBonus += ARMOR_STEPS[r] ?? 1; },
  },
  {
    id: "master_masons", name: "Master Masons", category: "defensive",
    blurb: "Buildings are tougher and a bit cheaper.",
    detail: (r) => `+${pct(0.1, S(r))}% building HP, -${pct(0.05, S(r))}% build cost`,
    apply: (e, r) => { e.buildingHpMult *= 1 + 0.1 * S(r); e.buildCostMult *= 1 - 0.05 * S(r); },
  },
  {
    id: "vigilant_watch", name: "Vigilant Watch", category: "defensive",
    blurb: "Towers, keeps and Town Centers fire faster and farther.",
    detail: (r) => `+${pct(0.06, S(r))}% defensive fire rate, +${Math.round(8 * S(r))} range`,
    apply: (e, r) => { e.towerCdMult *= 1 - 0.06 * S(r); e.towerRangeBonus += 8 * S(r); },
  },
  {
    id: "bulwark", name: "Bulwark", category: "defensive",
    blurb: "Walls and gates are far tougher and cheaper — true turtle.",
    detail: (r) => `+${pct(0.15, S(r))}% wall/gate HP, -${pct(0.08, S(r))}% cost`,
    apply: (e, r) => { e.wallHpMult *= 1 + 0.15 * S(r); e.wallCostMult *= 1 - 0.08 * S(r); },
  },

  // ---------------------------------------------------------- supportive --
  {
    id: "bountiful_fields", name: "Bountiful Fields", category: "supportive",
    blurb: "Farms and foraging yield more food.",
    detail: (r) => `+${pct(0.08, S(r))}% food gathering`,
    apply: (e, r) => { e.foodGatherMult *= 1 + 0.08 * S(r); },
  },
  {
    id: "field_medic", name: "Field Medic", category: "supportive",
    blurb: "Units slowly heal out of combat — army sustain between fights.",
    detail: (r) => `${(0.6 * S(r)).toFixed(1)} HP/sec out of combat`,
    apply: (e, r) => { e.regenRate += 0.6 * S(r); },
  },
  {
    id: "quartermaster", name: "Quartermaster", category: "supportive",
    blurb: "Units cost less and train faster — out-mass everyone.",
    detail: (r) => `-${pct(0.05, S(r))}% unit cost & train time`,
    apply: (e, r) => { e.unitCostMult *= 1 - 0.05 * S(r); e.trainSpeedMult *= 1 - 0.05 * S(r); },
  },
  {
    id: "wayfarers", name: "Wayfarers", category: "supportive",
    blurb: "Villagers carry more and move faster — smoother eco.",
    detail: (r) => `+${pct(0.1, S(r))}% villager carry, +${pct(0.04, S(r))}% villager speed`,
    apply: (e, r) => { e.villCarryMult *= 1 + 0.1 * S(r); e.villSpeedMult *= 1 + 0.04 * S(r); },
  },
  {
    id: "far_sight", name: "Far Sight", category: "supportive",
    blurb: "Greater vision range — map control and scouting.",
    detail: (r) => `+${pct(0.1, S(r))}% vision range`,
    apply: (e, r) => { e.visionMult *= 1 + 0.1 * S(r); },
  },
];

export const BOONS_BY_ID: Record<string, BoonDef> = Object.fromEntries(BOONS.map((b) => [b.id, b]));
export const BOON_IDS = BOONS.map((b) => b.id);
export const BOON_CATEGORIES: BoonCategory[] = ["offensive", "defensive", "supportive"];

/** Aggregate a set of equipped boons (id + rolled rarity) into one effect. */
export function aggregateBoons(equipped: { id: string; rarity: number }[]): BoonEffect {
  const eff = emptyBoonEffect();
  for (const { id, rarity } of equipped) {
    const def = BOONS_BY_ID[id];
    if (def) def.apply(eff, rarity);
  }
  return eff;
}
