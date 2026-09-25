import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind, OrderKind } from "./types";
import { generateMap } from "../maps/generator";
import { AGES } from "../content/tech";

function mk(seed: number, alliances?: number[]): World {
  const w = new World(seed);
  w.init(generateMap("open_plains", seed, 2), [{}, {}], [1, 1], alliances);
  return w;
}

describe("Bugfix: no friendly fire", () => {
  it("can't force-attack an allied unit, but can attack an enemy", () => {
    const allied = mk(1, [0, 0]); // both teams in alliance 0
    const a = allied.spawnUnit(Team.Player, "militia", 800, 800);
    const ally = allied.spawnUnit(Team.Enemy, "militia", 820, 800);
    allied.issueAttack([a.id], ally.id);
    expect(a.order.kind).not.toBe(OrderKind.Attack); // blocked → stays idle

    const war = mk(1, [0, 1]); // opposing alliances
    const b = war.spawnUnit(Team.Player, "militia", 800, 800);
    const foe = war.spawnUnit(Team.Enemy, "militia", 820, 800);
    war.issueAttack([b.id], foe.id);
    expect(b.order.kind).toBe(OrderKind.Attack);
  });
});

describe("Bugfix: age advance can't be double-charged", () => {
  it("a second Town Center cannot queue a concurrent age-up", () => {
    const w = mk(2);
    const p = w.player(Team.Player);
    p.resources.food = 9999; p.resources.wood = 9999; p.resources.gold = 9999;
    // Meet the Feudal requirement (2 of the listed buildings) and add a 2nd TC.
    w.spawnBuilding(Team.Player, "barracks", 700, 900, true);
    w.spawnBuilding(Team.Player, "mill", 760, 900, true);
    const tc1 = w.entitiesOf(Team.Player, Kind.Building).find((e) => e.type === "town_center")!;
    const tc2 = w.spawnBuilding(Team.Player, "town_center", 1100, 1100, true);

    const foodBefore = p.resources.food;
    expect(w.research(Team.Player, tc1.id, "age")).toBe(true);
    expect(w.research(Team.Player, tc2.id, "age")).toBe(false); // already aging
    expect(p.resources.food).toBe(foodBefore - AGES[1].cost.food); // charged once
  });
});

describe("Bugfix: queued age-up uses the real advance time", () => {
  it("itemTime('a:age', age) returns the next age's advance time", () => {
    const w = mk(3);
    expect(w.itemTime("a:age", 0)).toBe(AGES[1].advanceTime); // → Feudal
    expect(w.itemTime("a:age", 1)).toBe(AGES[2].advanceTime); // → Castle (was hardcoded 30)
  });
});

describe("Bugfix: pop-blocked refund matches the discounted cost", () => {
  it("a cost-reduction boon can't mint resources by training while pop-capped", () => {
    const w = mk(4);
    const p = w.player(Team.Player);
    p.boon.unitCostMult = 0.5; // e.g. Quartermaster
    p.age = 2;
    p.resources.food = 2000; p.resources.wood = 2000; p.resources.gold = 2000;
    const stable = w.spawnBuilding(Team.Player, "stable", 800, 800, true);

    const before = { ...p.resources };
    expect(w.trainUnit(Team.Player, stable.id, "horseman")).toBe(true); // pays half cost
    p.popUsed = p.popCap; // now pop-blocked, so completion will refund

    for (let i = 0; i < 3000 && stable.productionQueue.length > 0; i++) w.tick();
    expect(stable.productionQueue.length).toBe(0); // it resolved (refunded)
    // Paid the discounted cost, refunded the same — net zero, no free resources.
    expect(p.resources.food).toBe(before.food);
    expect(p.resources.wood).toBe(before.wood);
    expect(p.resources.gold).toBe(before.gold);
  });
});
