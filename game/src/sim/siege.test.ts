import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team } from "./types";
import { generateMap } from "../maps/generator";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { SIM_HZ } from "../content/balance";

/**
 * Walls, siege, and whether one is an answer to the other.
 *
 * Measured before any of this existed: eleven Militia (880 gold) broke a stone
 * wall in 23.1s while four Battering Rams (800 gold) took 24.0s. Siege was not
 * merely weak against walls, it was *worse per gold than the cheapest infantry
 * in the game* — so a wall was never a decision, because whatever army you
 * already had went through it at the same rate.
 *
 * The fix is armour on wall-class buildings rather than more damage on siege.
 * The damage floor is 20% of raw, so armour can slow a sword by at most five
 * times and barely touches a ram's 56 — which is exactly the differential that
 * makes bringing siege a choice instead of a habit.
 */

/** Gold-equivalent, so "per gold" comparisons mean something. */
const worth = (c: { food: number; wood: number; gold: number }) => c.food + c.wood + c.gold;

/** Seconds for `budget` gold of `type` to break one segment of `wallType`. */
function breachTime(type: string, wallType: string, budget = 900): number {
  const map = generateMap("open_plains", 5, 2);
  const w = new World(5);
  w.init(map, [{}, {}], [1, 1], [0, 1]);
  const cx = w.worldW / 2, cy = w.worldH / 2;
  const wall = w.spawnBuilding(Team.Enemy, wallType, cx, cy, true);
  const def = UNITS[type];
  const n = Math.max(1, Math.floor(budget / worth(def.cost)));
  const ids: number[] = [];
  for (let i = 0; i < n; i++) {
    ids.push(w.spawnUnit(Team.Player, type, cx - 200, cy - 60 + i * 22).id);
  }
  w.issueAttack(ids, wall.id);
  for (let t = 0; t < SIM_HZ * 400; t++) {
    w.tick();
    w.drainEvents();
    if (!wall.alive) return t / SIM_HZ;
  }
  return Infinity;
}

describe("Siege against stone", () => {
  it("breaks a stone wall far faster than the same gold of infantry", () => {
    // The headline. Three times is the margin that makes a Siege Workshop worth
    // the 200 wood and the detour; anything under about two and you may as well
    // send the army you already have.
    const ram = breachTime("ram", "stone_wall");
    const militia = breachTime("militia", "stone_wall");
    expect(ram).toBeLessThan(Infinity);
    expect(militia / ram, `ram ${ram.toFixed(1)}s vs militia ${militia.toFixed(1)}s`)
      .toBeGreaterThan(2.2);
  }, 300000);

  it("puts the trebuchet at the top of the wall-breaking order", () => {
    // The most expensive, longest-ranged, slowest thing in the workshop should
    // be the best answer to a fortress, or there is no reason to ever build one.
    const treb = breachTime("trebuchet", "stone_wall");
    const cata = breachTime("catapult", "stone_wall");
    expect(treb).toBeLessThan(cata);
  }, 300000);

  it("leaves cavalry and archers hopeless against stone, which is the point", () => {
    // A wall you can knock down with the cavalry you happened to have is not a
    // wall. Both should be several times worse than a ram.
    const ram = breachTime("ram", "stone_wall");
    for (const type of ["knight", "archer"]) {
      const t = breachTime(type, "stone_wall");
      expect(t / ram, `${type} took ${t.toFixed(1)}s against ram ${ram.toFixed(1)}s`)
        .toBeGreaterThan(3);
    }
  }, 300000);

  it("still lets an army through eventually, so a wall is not an auto-win", () => {
    // Infantry has to be able to get in without a workshop, or walling stops
    // being a decision and becomes a hard counter to every army without siege.
    const militia = breachTime("militia", "stone_wall");
    expect(militia, "infantry cannot break a stone wall at all").toBeLessThan(150);
  }, 300000);
});

describe("Palisades are a speed bump, not a fortress", () => {
  it("comes down to infantry in seconds", () => {
    // Five gold of timber. If this needed siege the early game would be broken.
    expect(breachTime("militia", "palisade")).toBeLessThan(20);
  }, 300000);

  it("is far quicker to break than stone, per gold spent attacking it", () => {
    expect(breachTime("militia", "palisade")).toBeLessThan(breachTime("militia", "stone_wall") / 3);
  }, 300000);
});

describe("The armour that makes it work", () => {
  it("gives wall-class buildings real armour and leaves everything else at one", () => {
    // The mechanism, pinned. If these drift back toward 1 the whole differential
    // above collapses and nothing else in this file would explain why.
    expect(BUILDINGS.stone_wall.armor).toBeGreaterThan(15);
    expect(BUILDINGS.palisade.armor).toBeGreaterThan(3);
    // A gate is a door: tough, but the obvious place to hit a wall line.
    expect(BUILDINGS.gate.armor!).toBeLessThan(BUILDINGS.stone_wall.armor!);
    for (const id of ["house", "barracks", "town_center", "farm"]) {
      expect(BUILDINGS[id].armor ?? 1, `${id} quietly gained wall armour`).toBe(1);
    }
  });

  it("reaches the world, not just the definition", () => {
    const map = generateMap("open_plains", 5, 2);
    const w = new World(5);
    w.init(map, [{}, {}], [1, 1], [0, 1]);
    const wall = w.spawnBuilding(Team.Player, "stone_wall", w.worldW / 2, w.worldH / 2, true);
    const house = w.spawnBuilding(Team.Player, "house", w.worldW / 2 + 200, w.worldH / 2, true);
    expect(wall.armor).toBe(BUILDINGS.stone_wall.armor);
    expect(house.armor).toBe(1);
  });

  it("keeps a gate easier to batter than the wall beside it", () => {
    expect(breachTime("ram", "gate")).toBeLessThan(breachTime("ram", "stone_wall"));
  }, 300000);
});
