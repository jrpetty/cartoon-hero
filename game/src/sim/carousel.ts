// Warband Tactics carousel — the shared draft round, and the lobby's great
// equaliser. Every few rounds a ring of units rides out, each already carrying
// a component, and everyone still standing takes one. Pick order runs from the
// weakest warband to the strongest: if you're winning you pick last, from
// whatever the field left behind.

import { UNITS } from "../content/units";
import { UNIT_TIER, TIER_UNITS } from "./unit_tiers";
import { COMPONENT_IDS } from "./items";
import { RNG } from "../engine/rng";

export interface CarouselPick {
  type: string;
  star: number;
  /** The component this unit rides out carrying. */
  item: string;
}

/** Draft rounds: one every fifth round, offset from camps and augment picks. */
export function isDraftRound(round: number): boolean { return round % 5 === 3; }

/** The tiers on offer, which climb as the run goes on. */
function bandForRound(round: number): number[] {
  const step = Math.floor(round / 5); // 0 at round 3, 1 at round 8, 2 at 13…
  if (step <= 0) return [1, 2];
  if (step === 1) return [2, 3];
  if (step === 2) return [3, 4];
  return [4, 5];
}

/** How many options ride out — enough that even the last picker has a choice. */
export const CAROUSEL_SIZE = 6;

/** Roll the ring of units, each carrying a component. */
export function rollCarousel(rng: RNG, round: number, size = CAROUSEL_SIZE): CarouselPick[] {
  const band = bandForRound(round);
  const out: CarouselPick[] = [];
  for (let i = 0; i < size; i++) {
    const tier = band[rng.int(0, band.length - 1)];
    const pool = TIER_UNITS[tier].length ? TIER_UNITS[tier] : TIER_UNITS[1];
    if (!pool.length) continue;
    out.push({
      type: pool[rng.int(0, pool.length - 1)],
      star: 1,
      item: COMPONENT_IDS[rng.int(0, COMPONENT_IDS.length - 1)],
    });
  }
  return out;
}

/**
 * How strong a carousel option looks, for the AI's greedy pick (and for
 * ordering what's left). Higher tier is better; ties break on the component.
 */
export function pickValue(p: CarouselPick): number {
  return (UNIT_TIER[p.type] ?? 1) * 10 + (UNITS[p.type]?.attack ?? 0) / 10;
}
