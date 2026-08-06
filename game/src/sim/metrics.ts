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


// ---------------------------------------------------------------------------
// The end-of-match report.
//
// The battle report used to be five numbers. Five numbers cannot tell you *why*
// a match went the way it did — whether you lost because you never built an
// economy, or built one and never spent it, or spent it on the wrong army. This
// snapshot carries the whole ledger for both sides so the screen can show the
// comparison that actually explains the result.

export interface SideReport {
  /** Team numbers folded into this side (your alliance, or theirs). */
  teams: Team[];
  score: number;
  gathered: number;
  gatheredBy: { food: number; wood: number; gold: number };
  spent: number;
  spentOn: { units: number; buildings: number; tech: number };
  /** Gathered but never spent — resources that did nothing for you. */
  banked: number;
  unitsKilled: number;
  unitsLost: number;
  buildingsRazed: number;
  buildingsLost: number;
  damageDealt: number;
  damageTaken: number;
  peakArmy: number;
  peakVillagers: number;
  /** Villager-seconds spent idle. */
  idleVillagerTime: number;
  age: number;
  upgrades: number;
  trainedByType: Record<string, number>;
  lostByType: Record<string, number>;
  killedByType: Record<string, number>;
  builtByType: Record<string, number>;
}

export interface MatchReport {
  you: SideReport;
  foe: SideReport;
  durationSec: number;
  mapName: string;
}

export const emptyMatchReport = (): MatchReport => ({
  you: emptySide(), foe: emptySide(), durationSec: 0, mapName: "",
});

const emptySide = (): SideReport => ({
  teams: [], score: 0, gathered: 0, gatheredBy: { food: 0, wood: 0, gold: 0 },
  spent: 0, spentOn: { units: 0, buildings: 0, tech: 0 }, banked: 0,
  unitsKilled: 0, unitsLost: 0, buildingsRazed: 0, buildingsLost: 0,
  damageDealt: 0, damageTaken: 0, peakArmy: 0, peakVillagers: 0, idleVillagerTime: 0,
  age: 0, upgrades: 0, trainedByType: {}, lostByType: {}, killedByType: {}, builtByType: {},
});

const addInto = (dst: Record<string, number>, src: Record<string, number>) => {
  for (const [k, v] of Object.entries(src)) dst[k] = (dst[k] ?? 0) + v;
};

/** Fold one team's ledger into a side. */
function accumulate(side: SideReport, world: World, team: Team) {
  const p = world.player(team);
  const s = p.stats;
  side.teams.push(team);
  side.score += teamMetrics(world, team).score;
  side.gathered += s.gathered;
  side.gatheredBy.food += s.gatheredBy.food;
  side.gatheredBy.wood += s.gatheredBy.wood;
  side.gatheredBy.gold += s.gatheredBy.gold;
  side.spent += s.resourcesSpent;
  side.spentOn.units += s.spentOn.units;
  side.spentOn.buildings += s.spentOn.buildings;
  side.spentOn.tech += s.spentOn.tech;
  side.banked += Math.round(p.resources.food + p.resources.wood + p.resources.gold);
  side.unitsKilled += s.unitsKilled;
  side.unitsLost += s.unitsLost;
  side.buildingsRazed += s.buildingsRazed;
  side.buildingsLost += s.buildingsLost;
  side.damageDealt += Math.round(s.damageDealt);
  side.damageTaken += Math.round(s.damageTaken);
  side.peakArmy += s.peakArmy;
  side.peakVillagers += s.peakVillagers;
  side.idleVillagerTime += s.idleVillagerTime;
  side.age = Math.max(side.age, p.age);
  side.upgrades += p.upgrades.size;
  addInto(side.trainedByType, s.trainedByType);
  addInto(side.lostByType, s.lostByType);
  addInto(side.killedByType, s.killedByType);
  addInto(side.builtByType, s.builtByType);
}

/**
 * Snapshot the whole match as "your alliance versus theirs". Allies are folded
 * together because that is how the game was actually played and won.
 */
export function matchReport(world: World, me: Team, mapName: string): MatchReport {
  const you = emptySide();
  const foe = emptySide();
  for (let t = 0; t < world.numTeams; t++) {
    const team = t as Team;
    if (world.areAllied(me, team)) accumulate(you, world, team);
    else if (world.areHostile(me, team)) accumulate(foe, world, team);
  }
  return { you, foe, durationSec: world.time, mapName };
}
