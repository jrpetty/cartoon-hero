// Warband Tactics commanders — the run's opening decision, and the part of the
// mode that is most ours rather than borrowed. You lead a warband under one of
// the realm's commanders, the same people who lead your armies in skirmish, and
// their perk is *structural*: it changes a rule of the run rather than handing
// you a stat. Free rerolls, better shop odds, full-value sales, an extra board
// slot from round one.
//
// Identity (name, title, colour) is reused from content/commanders.ts so the
// roster stays consistent across modes; only the perk is mode-specific.

import { COMMANDERS, CommanderDef } from "../content/commanders";
import { Buff } from "./traits";
import { RNG } from "../engine/rng";

export interface WarbandCommander {
  /** Matches an id in content/commanders.ts, for name/title/colour. */
  id: string;
  /** The warband-facing perk line. */
  perk: string;
  // ---- structural levers, all optional ----
  /** Extra gold every round. */
  gold?: number;
  /** One-off gold when the run begins. */
  startGold?: number;
  /** Raises the interest cap (default 5). */
  interestCap?: number;
  /** Free rerolls granted at the start of every round. */
  freeRerolls?: number;
  /** Extra board slots on top of your level. */
  boardSlots?: number;
  /** Shop odds are read as if you were this many levels higher. */
  shopLevelBonus?: number;
  /** Selling refunds every copy sunk into a unit, not a flat star price. */
  fullRefund?: boolean;
  /** Your single largest synergy counts this much higher. */
  topTraitBonus?: number;
  /** Life lost on a defeat is reduced by this much (never below 1). */
  lossShield?: number;
  /** A buff applied to every unit in your warband. */
  buff?: Buff;
}

export const WARBAND_COMMANDERS: WarbandCommander[] = [
  {
    id: "steward", perk: "Your first reroll each round is free.",
    freeRerolls: 1,
  },
  {
    id: "quartermaster", perk: "Sell units for every copy you sank into them, and take +1 gold a round.",
    fullRefund: true, gold: 1,
  },
  {
    id: "marshal", perk: "Your warband is drilled hard: +8% HP and +2 armour.",
    buff: { hpPct: 8, armor: 2 },
  },
  {
    id: "magnate", perk: "Open with 10 gold, and your interest caps at 7 instead of 5.",
    startGold: 10, interestCap: 7,
  },
  {
    id: "warden", perk: "Field one extra unit from the very first round.",
    boardSlots: 1,
  },
  {
    id: "drillmaster", perk: "Your shop rolls at one level above your own.",
    shopLevelBonus: 1,
  },
  {
    id: "banneret", perk: "Your largest synergy always counts one higher.",
    topTraitBonus: 1,
  },
  {
    id: "warpriest", perk: "Defeats cost 3 less life — your wounded are carried home.",
    lossShield: 3,
  },
];

const BY_ID = new Map(WARBAND_COMMANDERS.map((c) => [c.id, c]));
export function warbandCommander(id: string): WarbandCommander | undefined { return BY_ID.get(id); }

/** The shared roster entry (name, title, colour) behind a warband commander. */
export function commanderIdentity(c: WarbandCommander): CommanderDef { return COMMANDERS[c.id]; }

/** Roll a set of distinct commanders to choose between at the start of a run. */
export function offerCommanders(rng: RNG, count = 3): WarbandCommander[] {
  const pool = [...WARBAND_COMMANDERS];
  const out: WarbandCommander[] = [];
  while (out.length < count && pool.length) out.push(pool.splice(rng.int(0, pool.length - 1), 1)[0]);
  return out;
}
