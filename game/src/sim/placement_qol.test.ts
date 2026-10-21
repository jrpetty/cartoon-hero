import { describe, expect, it } from "vitest";
import { World } from "./world";
import { generateMap } from "../maps/generator";
import { BuildState, Kind, Team } from "./types";
import { applyCommand } from "./commands";
import { BUILDINGS } from "../content/buildings";
import { UNITS } from "../content/units";
import { AGES } from "../content/tech";
import { TILE } from "../content/balance";

/**
 * Placing buildings and managing a queue, from the player's side: the game
 * says exactly why a spot won't take a building (and never shows a spot as
 * fine that the sim then refuses), and a queued item can be taken back.
 */

const P = Team.Player;
function fresh(seed = 12) {
  const w = new World(seed);
  w.init(generateMap("open_plains", seed), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
  return w;
}
const tcOf = (w: World) => w.entities.find((e) => e.alive && e.team === P && e.type === "town_center")!;

describe("Why a building won't go there", () => {
  it("names the reason: age, a missing building, money, ground, units, a farm", () => {
    const w = fresh();
    const tc = tcOf(w);
    const p = w.player(P);
    const castle = Object.values(BUILDINGS).find((b) => b.age >= 2)!;
    expect(w.placementProblem(P, castle.id, tc.x + 300, tc.y)).toMatch(/^Needs the /);
    const withReq = Object.values(BUILDINGS).find((b) => b.requires && b.age <= 1);
    if (withReq) { p.age = withReq.age; expect(w.placementProblem(P, withReq.id, tc.x + 300, tc.y)).toMatch(/^Needs a /); p.age = 0; }
    p.resources = { food: 0, wood: 3, gold: 0 };
    expect(w.placementProblem(P, "house", tc.x + 300, tc.y)).toMatch(/^Need \d+ more wood$/);
    p.resources = { food: 9999, wood: 9999, gold: 9999 };
    expect(w.placementProblem(P, "house", tc.x, tc.y), "on top of the Town Center").toMatch(/^Blocked/);
    w.tick(); // units join the spatial index on the first tick
    const vil = w.entitiesOf(P, Kind.Unit)[0];
    expect(w.placementProblem(P, "house", vil.x, vil.y)).toMatch(/Units are standing there|Blocked/);
    let farm = null;
    for (let r = 180; !farm && r < 700; r += 40) for (let a = 0; !farm && a < 12; a++) farm = w.placeBuilding(P, "farm", tc.x + Math.cos(a / 2) * r, tc.y + Math.sin(a / 2) * r);
    farm = farm!;
    expect(farm).toBeTruthy();
    expect(w.placementProblem(P, "house", farm.x, farm.y)).toBe("Overlaps a farm");
  });

  it("agrees with the sim at every spot: no green ghost the sim refuses", () => {
    const w = fresh(33);
    const p = w.player(P);
    p.resources = { food: 5000, wood: 5000, gold: 5000 };
    const tc = tcOf(w);
    let agreed = 0;
    for (let i = 0; i < 400; i++) {
      const x = tc.x + ((i * 97) % 900) - 450, y = tc.y + ((i * 61) % 900) - 450;
      const type = ["house", "barracks", "lumber_camp", "farm", "palisade"][i % 5];
      const problem = w.placementProblem(P, type, x, y);
      const placed = w.placeBuilding(P, type, x, y);
      expect(!!placed, `${type} at ${x},${y}: said "${problem}"`).toBe(problem === null);
      agreed++;
    }
    expect(agreed).toBe(400);
  });
});

describe("Taking something out of the queue", () => {
  it("refunds it, and the next item starts fresh", () => {
    const w = fresh();
    const tc = tcOf(w);
    const p = w.player(P);
    p.resources = { food: 1000, wood: 1000, gold: 1000 };
    for (let i = 0; i < 3; i++) w.trainUnit(P, tc.id, "villager");
    const before = { ...p.resources };
    for (let i = 0; i < 20; i++) w.tick(); // part-way through the first
    applyCommand(w, { t: "cancel", team: P, buildingId: tc.id, index: 0 });
    expect(tc.productionQueue).toHaveLength(2);
    expect(p.resources.food).toBe(before.food + UNITS.villager.cost.food);
    expect(tc.productionTime, "the next one starts from the beginning").toBeGreaterThan(UNITS.villager.buildTime * 0.9);
    applyCommand(w, { t: "cancel", team: P, buildingId: tc.id, index: 1 });
    expect(tc.productionQueue).toHaveLength(1);
  });

  it("won't cancel someone else's", () => {
    const w = fresh();
    const tc = tcOf(w);
    w.player(P).resources.food = 500;
    w.trainUnit(P, tc.id, "villager");
    applyCommand(w, { t: "cancel", team: 1 as Team, buildingId: tc.id, index: 0 });
    expect(tc.productionQueue).toHaveLength(1);
  });

  it("can call off an age advance, and its Oath", () => {
    const w = fresh();
    const tc = tcOf(w);
    const p = w.player(P);
    p.resources = { food: 5000, wood: 5000, gold: 5000 };
    const next = AGES[1];
    for (const id of next.requiresAny.slice(0, next.requiresCount)) {
      let b = null;
      for (let r = 200; !b && r < 700; r += 40) for (let a = 0; !b && a < 12; a++) b = w.placeBuilding(P, id, tc.x + Math.cos(a / 2) * r, tc.y + Math.sin(a / 2) * r);
      if (b) { b.buildState = BuildState.Done; b.buildProgress = 1; b.hp = b.maxHp; }
    }
    const ok = w.research(P, tc.id, "age:sword");
    expect(ok).toBe(true);
    const spent = { ...p.resources };
    w.cancelProduction(P, tc.id, tc.productionQueue.indexOf("a:age"));
    expect(p.pendingOath).toBeNull();
    expect(p.resources.food).toBe(spent.food + next.cost.food);
  });
});

describe("The population cap", () => {
  it("tells the player when a finished unit had no room", () => {
    const w = fresh();
    const tc = tcOf(w);
    const p = w.player(P);
    p.resources.food = 1000;
    w.trainUnit(P, tc.id, "villager");
    p.popCap = p.popUsed; // full up while it trains
    let saw = false;
    for (let i = 0; i < 20 * 40 && !saw; i++) {
      w.tick();
      saw = w.drainEvents().some((e) => e.kind === "popcap" && e.team === P);
    }
    expect(saw).toBe(true);
    void TILE;
  });
});
