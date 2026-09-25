// Warband Tactics synergies. Each unit belongs to one or more traits; deploying
// enough *distinct* unit types of a trait activates an escalating buff applied to
// the units carrying it. Overlapping traits (e.g. a Spearman is both a Footman
// and a Pike) make drafting a real puzzle. Pure data + helpers, shared by the
// battle resolver and the screen.

export interface Buff {
  armor?: number;
  atk?: number; // flat
  atkPct?: number; // percent
  hp?: number; // flat
  hpPct?: number; // percent
  speedPct?: number;
}

export interface TraitTier { at: number; label: string; buff: Buff; }
export interface Trait { id: string; name: string; color: string; types: string[]; tiers: TraitTier[]; }

export const TRAITS: Trait[] = [
  {
    id: "footmen", name: "Footmen", color: "#c9a24a",
    types: ["militia", "spearman", "twohand", "pikeman", "shieldbearer", "berserker"],
    tiers: [
      { at: 2, label: "+2 armour", buff: { armor: 2 } },
      { at: 4, label: "+5 armour, +12% HP", buff: { armor: 5, hpPct: 12 } },
    ],
  },
  {
    id: "marksmen", name: "Marksmen", color: "#6fd0ff",
    types: ["archer", "skirmisher", "crossbow", "handcannon", "javelin", "longbow"],
    tiers: [
      { at: 2, label: "+18% attack", buff: { atkPct: 18 } },
      { at: 4, label: "+45% attack", buff: { atkPct: 45 } },
    ],
  },
  {
    id: "riders", name: "Riders", color: "#e0822f",
    types: ["knight", "horseman", "raider", "scout", "cataphract"],
    tiers: [
      { at: 2, label: "+12% HP", buff: { hpPct: 12 } },
      { at: 3, label: "+30% HP, +15% speed", buff: { hpPct: 30, speedPct: 15 } },
    ],
  },
  {
    id: "engines", name: "War Engines", color: "#d8403a",
    types: ["catapult", "ram", "trebuchet", "bombard"],
    tiers: [
      { at: 2, label: "+35% attack", buff: { atkPct: 35 } },
    ],
  },
  {
    id: "pikes", name: "Pikes", color: "#9ad02f",
    types: ["spearman", "pikeman", "javelin"],
    tiers: [
      { at: 2, label: "+25% attack", buff: { atkPct: 25 } },
    ],
  },
  {
    id: "elite", name: "Elite", color: "#cf5fd8",
    types: ["hero", "twohand", "knight", "handcannon", "cataphract", "berserker"],
    tiers: [
      { at: 2, label: "+15% attack & HP", buff: { atkPct: 15, hpPct: 15 } },
    ],
  },
  {
    id: "mystics", name: "Mystics", color: "#9a7ae0",
    types: ["monk", "battlemage"],
    tiers: [
      { at: 2, label: "+20% HP, +3 armour", buff: { hpPct: 20, armor: 3 } },
    ],
  },
  {
    id: "gunpowder", name: "Gunpowder", color: "#e07a3a",
    types: ["handcannon", "bombard"],
    tiers: [
      { at: 2, label: "+30% attack", buff: { atkPct: 30 } },
    ],
  },
];

/** type → traits it belongs to. */
const TYPE_TRAITS = new Map<string, Trait[]>();
for (const t of TRAITS) for (const type of t.types) {
  const arr = TYPE_TRAITS.get(type) ?? [];
  arr.push(t);
  TYPE_TRAITS.set(type, arr);
}
export function traitsOf(type: string): Trait[] { return TYPE_TRAITS.get(type) ?? []; }

export interface ActiveTrait { trait: Trait; count: number; tier: TraitTier | null; tierIndex: number; }

/**
 * Given the distinct deployed unit types, which traits are active and at what
 * tier. `bonus` adds virtual counts per trait id — that's how a warband banner
 * (augment) makes a synergy read as two extra units.
 */
export function activeTraits(distinctTypes: string[], bonus?: Record<string, number>): ActiveTrait[] {
  const set = new Set(distinctTypes);
  const out: ActiveTrait[] = [];
  for (const trait of TRAITS) {
    const count = trait.types.reduce((n, t) => n + (set.has(t) ? 1 : 0), 0) + (bonus?.[trait.id] ?? 0);
    if (count < trait.tiers[0].at) continue;
    let tier: TraitTier | null = null;
    let tierIndex = -1;
    for (let i = 0; i < trait.tiers.length; i++) {
      if (count >= trait.tiers[i].at) { tier = trait.tiers[i]; tierIndex = i; }
    }
    out.push({ trait, count, tier, tierIndex });
  }
  return out;
}

/** Mutably apply a buff to a spawned unit (after star scaling). */
export function applyBuff(u: { maxHp: number; hp: number; attack: number; armor: number; speed: number }, b: Buff) {
  if (b.armor) u.armor += b.armor;
  if (b.atk) u.attack += b.atk;
  if (b.atkPct) u.attack = Math.round(u.attack * (1 + b.atkPct / 100));
  if (b.hp) { u.maxHp += b.hp; u.hp += b.hp; }
  if (b.hpPct) { u.maxHp = Math.round(u.maxHp * (1 + b.hpPct / 100)); u.hp = u.maxHp; }
  if (b.speedPct) u.speed = Math.round(u.speed * (1 + b.speedPct / 100));
}
