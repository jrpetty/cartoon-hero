// Battlefield conditions — the weather and ground a round is fought on.
//
// This is the auto-battler leaning on the fact that it sits on a real RTS: a
// round is not fought in a vacuum, it's fought in driving rain that slackens
// bowstrings, or deep mud that bogs down a cavalry charge. Conditions apply to
// *both* warbands equally, so they're a puzzle rather than a coin flip — the
// question is whether your comp is the one that copes, and whether it's worth
// swapping a bench unit in for this round.
//
// Effects are expressed per trait, so they read as "rain hurts Marksmen"
// rather than as an opaque stat tweak.

import { Buff } from "./traits";
import { RNG } from "../engine/rng";

/** Matches the renderer's WeatherType, so the screen can dress the arena. */
export type ConditionWeather = "clear" | "rain" | "snow" | "overcast";

export interface Condition {
  id: string;
  name: string;
  /** One line, written as what a commander would notice. */
  desc: string;
  weather: ConditionWeather;
  color: string;
  /** Applied to every unit on both sides. */
  all?: Buff;
  /** Applied to units belonging to a trait, on both sides. */
  traits?: Record<string, Buff>;
  /** Relative chance of being rolled. */
  weight: number;
}

export const CONDITIONS: Condition[] = [
  {
    id: "clear", name: "Clear Skies", desc: "A still, bright field. Nothing favours anyone.",
    weather: "clear", color: "#cfe0ff", weight: 30,
  },
  {
    id: "rain", name: "Driving Rain", desc: "Slack bowstrings — Marksmen shoot 18% weaker.",
    weather: "rain", color: "#6fa8d0", weight: 14,
    traits: { marksmen: { atkPct: -18 } },
  },
  {
    id: "mud", name: "Deep Mud", desc: "The field is a bog. Everyone slows; Riders lose their charge.",
    weather: "overcast", color: "#9a7a4a", weight: 12,
    all: { speedPct: -10 },
    traits: { riders: { speedPct: -25 } },
  },
  {
    id: "frost", name: "Bitter Frost", desc: "Numb hands and brittle frames — 8% less HP, siege 15% weaker.",
    weather: "snow", color: "#bcd8ea", weight: 11,
    all: { hpPct: -8 },
    traits: { engines: { atkPct: -15 } },
  },
  {
    id: "gale", name: "Howling Gale", desc: "A wind at your backs: everyone 12% faster, Marksmen 10% weaker.",
    weather: "rain", color: "#7fe0d0", weight: 11,
    all: { speedPct: 12 },
    traits: { marksmen: { atkPct: -10 } },
  },
  {
    id: "dusk", name: "Dusk Raid", desc: "Failing light. Marksmen shoot 22% weaker; Footmen hold 10% harder.",
    weather: "overcast", color: "#8a7ab0", weight: 10,
    traits: { marksmen: { atkPct: -22 }, footmen: { hpPct: 10 } },
  },
  {
    id: "firm", name: "Firm Ground", desc: "Hard, level turf — Riders run 18% faster and hit 8% harder.",
    weather: "clear", color: "#e0a23a", weight: 6,
    traits: { riders: { speedPct: 18, atkPct: 8 } },
  },
  {
    id: "stillair", name: "Still Air", desc: "Not a breath of wind. Siege and Marksmen find their range.",
    weather: "clear", color: "#e8c14b", weight: 6,
    traits: { engines: { atkPct: 25 }, marksmen: { atkPct: 10 } },
  },
];

const BY_ID = new Map(CONDITIONS.map((c) => [c.id, c]));
export function conditionById(id: string): Condition | undefined { return BY_ID.get(id); }

/** The opener is always clear — no reason to punish a first board. */
export const CLEAR = CONDITIONS[0];

/** Roll a condition by weight. Round 1 is always Clear Skies. */
export function rollCondition(rng: RNG, round: number): Condition {
  if (round <= 1) return CLEAR;
  let total = 0;
  for (const c of CONDITIONS) total += c.weight;
  let pick = rng.range(0, total);
  for (const c of CONDITIONS) {
    pick -= c.weight;
    if (pick < 0) return c;
  }
  return CLEAR;
}

/** True when a condition actually changes anything (Clear Skies doesn't). */
export function hasEffect(c: Condition): boolean {
  return !!(c.all && Object.keys(c.all).length) || !!(c.traits && Object.keys(c.traits).length);
}

/** Human-readable effect lines, for the tooltip. */
export function conditionLines(c: Condition, traitName: (id: string) => string): string[] {
  const out: string[] = [];
  const fmt = (b: Buff): string => {
    const parts: string[] = [];
    if (b.atkPct) parts.push(`${b.atkPct > 0 ? "+" : ""}${b.atkPct}% attack`);
    if (b.atk) parts.push(`${b.atk > 0 ? "+" : ""}${b.atk} attack`);
    if (b.hpPct) parts.push(`${b.hpPct > 0 ? "+" : ""}${b.hpPct}% HP`);
    if (b.hp) parts.push(`${b.hp > 0 ? "+" : ""}${b.hp} HP`);
    if (b.armor) parts.push(`${b.armor > 0 ? "+" : ""}${b.armor} armour`);
    if (b.speedPct) parts.push(`${b.speedPct > 0 ? "+" : ""}${b.speedPct}% speed`);
    return parts.join(", ");
  };
  if (c.all) out.push(`Everyone: ${fmt(c.all)}`);
  for (const [id, b] of Object.entries(c.traits ?? {})) out.push(`${traitName(id)}: ${fmt(b)}`);
  return out;
}
