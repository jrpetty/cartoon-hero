// Warband Tactics augments — the run-defining choices, in the spirit of TFT's
// Hextech Augments. Three times a run you're offered three of these and keep
// one, and that pick bends the whole run: an economy engine, a stat spike
// across your whole warband, an extra board slot, or a banner that fakes two
// extra units of a synergy.
//
// Pure data + a picker. The run engine (sim/warband.ts) reads the fields it
// cares about; the screen just draws them.

import { Buff } from "./traits";
import { RNG } from "../engine/rng";

export type AugmentTier = "silver" | "gold" | "prismatic";

export const TIER_COLOR: Record<AugmentTier, string> = {
  silver: "#b9c4cf",
  gold: "#e8c040",
  prismatic: "#7fe0d0",
};

export interface Augment {
  id: string;
  name: string;
  desc: string;
  tier: AugmentTier;
  /** Extra gold every round. */
  gold?: number;
  /** Raises the interest cap (default 5 gold). */
  interestCap?: number;
  /** Rerolls cost this instead of 2. */
  rerollCost?: number;
  /** Free XP at the start of every round. */
  xp?: number;
  /** Extra board slots on top of your level. */
  boardSlots?: number;
  /** Applied to every unit in your warband, every fight. */
  buff?: Buff;
  /** Counts as extra distinct types toward a synergy (a banner/emblem). */
  traitBonus?: { trait: string; count: number };
  /** Earn a relic every N rounds instead of the usual 3. */
  relicEvery?: number;
  /** One-off life restored when taken. */
  life?: number;
  /** One-off gold granted when taken. */
  bounty?: number;
  /** One-off relics granted when taken. */
  relics?: number;
}

export const AUGMENTS: Augment[] = [
  // ---- silver: small, early, always useful -------------------------------
  { id: "warchest", name: "War Chest", tier: "silver", gold: 2, desc: "+2 gold every round." },
  { id: "whetted", name: "Whetted Blades", tier: "silver", buff: { atkPct: 12 }, desc: "Your warband deals +12% attack." },
  { id: "hardened", name: "Hardened Ranks", tier: "silver", buff: { hpPct: 12 }, desc: "Your warband has +12% HP." },
  { id: "quartermaster", name: "Quartermaster", tier: "silver", rerollCost: 1, desc: "Rerolls cost 1 gold instead of 2." },
  { id: "apprentice", name: "Apprentice Drill", tier: "silver", xp: 2, desc: "+2 free XP every round." },
  { id: "scavengers", name: "Scavengers", tier: "silver", relics: 1, gold: 1, desc: "Gain a relic now, then +1 gold every round." },
  { id: "shieldwall", name: "Shield Wall", tier: "silver", buff: { armor: 6 }, desc: "Your warband gains +6 armour." },
  { id: "forcedmarch", name: "Forced March", tier: "silver", buff: { speedPct: 18 }, desc: "Your warband moves 18% faster." },

  // ---- gold: real power spikes -------------------------------------------
  { id: "ironbank", name: "Iron Bank", tier: "gold", interestCap: 8, gold: 1, desc: "Interest caps at 8 gold (not 5), +1 gold a round." },
  { id: "warforged", name: "Warforged Banner", tier: "gold", boardSlots: 1, desc: "+1 board slot — field one more unit." },
  { id: "relichoard", name: "Relic Hoard", tier: "gold", relicEvery: 2, desc: "Earn a relic every 2 rounds instead of 3." },
  { id: "blademaster", name: "Blademaster's Oath", tier: "gold", buff: { atkPct: 26 }, desc: "Your warband deals +26% attack." },
  { id: "bulwark", name: "Bulwark", tier: "gold", buff: { hpPct: 18, armor: 8 }, desc: "+18% HP and +8 armour to your warband." },
  { id: "warcollege", name: "War College", tier: "gold", xp: 4, desc: "+4 free XP every round — level up fast." },
  { id: "veterans", name: "Veterans' Pay", tier: "gold", gold: 4, desc: "+4 gold every round." },

  // ---- prismatic: run-defining -------------------------------------------
  { id: "legion", name: "The Legion", tier: "prismatic", boardSlots: 2, desc: "+2 board slots — field two more units." },
  { id: "dragonsblood", name: "Dragon's Blood", tier: "prismatic", buff: { hpPct: 30, atkPct: 22 }, desc: "+30% HP and +22% attack to your warband." },
  { id: "kingsransom", name: "King's Ransom", tier: "prismatic", gold: 5, interestCap: 8, bounty: 12, desc: "12 gold now, then +5 a round and interest to 8." },
  { id: "relicforge", name: "Relic Forge", tier: "prismatic", relicEvery: 1, desc: "Earn a relic every single round." },
  { id: "secondwind", name: "Second Wind", tier: "prismatic", life: 30, buff: { hpPct: 12 }, desc: "Restore 30 life and gain +12% HP." },
  // Banners fake two extra unit types for a synergy — the emblem fantasy.
  { id: "banner_footmen", name: "Banner of Footmen", tier: "prismatic", traitBonus: { trait: "footmen", count: 2 }, desc: "Your Footmen synergy counts +2." },
  { id: "banner_marksmen", name: "Banner of Marksmen", tier: "prismatic", traitBonus: { trait: "marksmen", count: 2 }, desc: "Your Marksmen synergy counts +2." },
  { id: "banner_riders", name: "Banner of Riders", tier: "prismatic", traitBonus: { trait: "riders", count: 2 }, desc: "Your Riders synergy counts +2." },
  { id: "banner_elite", name: "Banner of the Elite", tier: "prismatic", traitBonus: { trait: "elite", count: 2 }, desc: "Your Elite synergy counts +2." },
];

const BY_ID = new Map(AUGMENTS.map((a) => [a.id, a]));
export function augmentById(id: string): Augment | undefined { return BY_ID.get(id); }

/** The rounds an augment is offered on, and the tier offered each time. */
export const AUGMENT_ROUNDS = [2, 5, 9];
const ROUND_TIER: AugmentTier[] = ["silver", "gold", "prismatic"];

/** Which tier is offered on a given round (null if it isn't an augment round). */
export function tierForRound(round: number): AugmentTier | null {
  const i = AUGMENT_ROUNDS.indexOf(round);
  return i < 0 ? null : ROUND_TIER[i];
}

/**
 * Roll `count` distinct augment choices of a tier, skipping any already owned.
 * Falls back to other tiers if a tier somehow runs dry, so the offer is never
 * short.
 */
export function offerAugments(rng: RNG, tier: AugmentTier, owned: string[], count = 3): Augment[] {
  const have = new Set(owned);
  const pool = AUGMENTS.filter((a) => a.tier === tier && !have.has(a.id));
  const spare = AUGMENTS.filter((a) => a.tier !== tier && !have.has(a.id));
  const out: Augment[] = [];
  const draw = (from: Augment[]) => {
    while (out.length < count && from.length) {
      const i = rng.int(0, from.length - 1);
      out.push(from.splice(i, 1)[0]);
    }
  };
  draw(pool);
  draw(spare);
  return out;
}

/** Merge every owned augment's stat buff into one buff for the battle. */
export function combinedBuff(owned: Augment[]): Buff | undefined {
  const out: Buff = {};
  let any = false;
  for (const a of owned) {
    if (!a.buff) continue;
    any = true;
    for (const k of ["armor", "atk", "atkPct", "hp", "hpPct", "speedPct"] as const) {
      if (a.buff[k]) out[k] = (out[k] ?? 0) + a.buff[k]!;
    }
  }
  return any ? out : undefined;
}

/** Merge every owned banner into a trait-id → bonus-count map. */
export function combinedTraitBonus(owned: Augment[]): Record<string, number> | undefined {
  const out: Record<string, number> = {};
  let any = false;
  for (const a of owned) {
    if (!a.traitBonus) continue;
    any = true;
    out[a.traitBonus.trait] = (out[a.traitBonus.trait] ?? 0) + a.traitBonus.count;
  }
  return any ? out : undefined;
}
