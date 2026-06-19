// Per-team performance metrics for the in-match scoreboard and post-match graphs.
// Pure read-only derivation from world state — never mutates the sim, so it's
// safe to call every frame and from either client in a net game.

import { World } from "./world";
import { Kind, Team } from "./types";
import { UNITS } from "../content/units";

export interface TeamMetrics {
  team: Team;
  score: number; // single composite "how well is this realm doing"
  military: number; // current standing-army strength
  economy: number; // total resources gathered over the match
  stockpile: number; // food + wood + gold on hand right now
  villagers: number;
  army: number; // count of combat units
  killed: number;
  lost: number;
  razed: number;
  lostBuildings: number;
  age: number;
  defeated: boolean;
}

/** Current combat strength of a team's standing army (attack + survivability). */
function militaryStrength(world: World, team: Team): { military: number; villagers: number; army: number } {
  let military = 0;
  let villagers = 0;
  let army = 0;
  for (const e of world.entities) {
    if (!e.alive || e.team !== team || e.kind !== Kind.Unit) continue;
    if (e.type === "villager") { villagers++; continue; }
    army++;
    const u = UNITS[e.type];
    military += (u?.attack ?? 4) + e.hp * 0.1 + (u?.pop ?? 1) * 4;
  }
  return { military: Math.round(military), villagers, army };
}

export function teamMetrics(world: World, team: Team): TeamMetrics {
  const p = world.player(team);
  const { military, villagers, army } = militaryStrength(world, team);
  const s = p.stats;
  const stockpile = Math.round(p.resources.food + p.resources.wood + p.resources.gold);
  // Composite score: economy worked, fights won, territory taken, tech & age
  // climbed, plus the army currently fielded. Tuned so good play trends up.
  const score = Math.round(
    s.gathered * 0.1 +
    s.unitsKilled * 8 +
    s.buildingsRazed * 20 +
    p.age * 120 +
    p.upgrades.size * 25 +
    military +
    villagers * 5,
  );
  return {
    team,
    score,
    military,
    economy: s.gathered,
    stockpile,
    villagers,
    army,
    killed: s.unitsKilled,
    lost: s.unitsLost,
    razed: s.buildingsRazed,
    lostBuildings: s.buildingsLost,
    age: p.age,
    defeated: p.defeated,
  };
}

/** Snapshot all player teams at one moment (for the time-series history). */
export function snapshotMetrics(world: World): TeamMetrics[] {
  const out: TeamMetrics[] = [];
  for (let t = 0; t < world.numTeams; t++) out.push(teamMetrics(world, t as Team));
  return out;
}
