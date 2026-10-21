import { describe, expect, it } from "vitest";
import { World } from "./world";
import { generateMap } from "../maps/generator";
import { BuildState, Entity, Kind, OrderKind, Team } from "./types";

/**
 * Farming, the way a player does it: build a farm and the builder works it,
 * send several villagers at one farm and they spread over the free ones, a
 * spent farm is replanted and worked again, and nobody wanders off across the
 * map because their farm was taken.
 */

const P = Team.Player;
function setup(seed = 8) {
  const w = new World(seed);
  w.init(generateMap("open_plains", seed), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
  w.player(P).resources = { food: 5000, wood: 5000, gold: 5000 };
  return w;
}
const tc = (w: World) => w.entities.find((e) => e.alive && e.team === P && e.type === "town_center")!;
function farmNear(w: World, i: number, done = true): Entity {
  const c = tc(w);
  for (let r = 140 + i * 10; r < 700; r += 36) for (let a = 0; a < 16; a++) {
    const f = w.placeBuilding(P, "farm", c.x + Math.cos(a / 2.5 + i) * r, c.y + Math.sin(a / 2.5 + i) * r);
    if (f) { if (done) { f.buildState = BuildState.Done; f.buildProgress = 1; f.hp = f.maxHp; } return f; }
  }
  throw new Error("no farm spot");
}
const villagers = (w: World) => w.entitiesOf(P, Kind.Unit).filter((e) => e.type === "villager");
const run = (w: World, sec: number) => { for (let i = 0; i < sec * 20; i++) w.tick(); };
const farming = (v: Entity, f: Entity) => v.order.target === f.id || (v.order.kind === OrderKind.Return && (v.order.queue ?? []).some((o) => o.target === f.id));

describe("Farming", () => {
  it("a villager sent to a farm keeps farming it", () => {
    const w = setup();
    const f = farmNear(w, 0);
    const v = villagers(w)[0];
    const food0 = w.player(P).resources.food;
    w.issueGather([v.id], f.id);
    run(w, 90);
    expect(farming(v, f)).toBe(true);
    expect(w.player(P).resources.food).toBeGreaterThan(food0 + 20);
  });

  it("several villagers on one farm spread over the free farms", () => {
    const w = setup();
    const farms = [farmNear(w, 0), farmNear(w, 1), farmNear(w, 2)];
    const vs = villagers(w).slice(0, 3);
    w.issueGather(vs.map((v) => v.id), farms[0].id);
    run(w, 30);
    const worked = new Set(vs.map((v) => farms.findIndex((f) => farming(v, f))));
    expect(worked.has(-1), "someone isn't on a farm").toBe(false);
    expect(worked.size, "two share a farm").toBe(3);
  });

  it("a builder farms the farm it just built", () => {
    const w = setup();
    const f = farmNear(w, 0, false);
    const v = villagers(w)[0];
    w.issueBuildRepair([v.id], f.id);
    run(w, 60);
    expect(f.buildState).toBe(BuildState.Done);
    expect(farming(v, f), `after building: order ${v.order.kind}`).toBe(true);
  });

  it("two builders: one farms it, the other takes a free farm", () => {
    const w = setup();
    const f = farmNear(w, 0, false);
    const spare = farmNear(w, 1);
    const [a, b] = villagers(w);
    w.issueBuildRepair([a.id, b.id], f.id);
    run(w, 60);
    const onF = [a, b].filter((v) => farming(v, f)).length;
    const onSpare = [a, b].filter((v) => farming(v, spare)).length;
    expect([onF, onSpare]).toEqual([1, 1]);
  });

  it("the villager who was sent to build it keeps it, even if a helper finishes it", () => {
    const w = setup();
    const f = farmNear(w, 0, false);
    const spare = farmNear(w, 1);
    const [helper, builder] = villagers(w);
    w.issueBuildRepair([builder.id], f.id);
    run(w, 3);
    w.issueBuildRepair([helper.id], f.id);
    run(w, 60);
    expect(farming(builder, f), "the builder lost its farm").toBe(true);
    expect(farming(helper, spare), "the helper should take the free farm").toBe(true);
  });

  it("a villager sent to farm an unfinished farm helps build it rather than standing idle", () => {
    const w = setup();
    const f = farmNear(w, 0, false);
    const [v] = villagers(w);
    w.issueGather([v.id], f.id);
    run(w, 60);
    expect(f.buildState).toBe(BuildState.Done);
    expect(farming(v, f), `order ${v.order.kind}`).toBe(true);
  });

  it("a farm queued after another job is farmed once it's built", () => {
    const w = setup();
    const f = farmNear(w, 0, false);
    const g = farmNear(w, 1, false);
    const [v] = villagers(w);
    w.issueBuildRepair([v.id], g.id);
    w.issueBuildRepair([v.id], f.id, true);
    run(w, 120);
    expect(farming(v, f), "farms the last farm it built").toBe(true);
  });

  it("a builder taken off the job gives up the farm", () => {
    const w = setup();
    const f = farmNear(w, 0, false);
    const [a, b] = villagers(w);
    w.issueBuildRepair([a.id], f.id);
    run(w, 3);
    w.issueMove([a.id], a.x - 200, a.y);
    w.issueBuildRepair([b.id], f.id);
    run(w, 60);
    expect(farming(b, f)).toBe(true);
    expect(farming(a, f)).toBe(false);
  });

  it("the farmer is never bumped by others sent to its farm", () => {
    const w = setup();
    const f = farmNear(w, 0, false);
    const vs = villagers(w);
    w.issueBuildRepair([vs[0].id], f.id);
    run(w, 60);
    w.issueGather([vs[1].id, vs[2].id], f.id);
    run(w, 60);
    expect(farming(vs[0], f)).toBe(true);
    expect(vs.slice(1, 3).some((v) => farming(v, f))).toBe(false);
  });

  it("a villager bumped from a taken farm doesn't wander across the map", () => {
    const w = setup();
    const f = farmNear(w, 0);
    const [a, b] = villagers(w);
    w.issueGather([a.id], f.id);
    run(w, 20);
    w.issueGather([b.id], f.id);
    run(w, 5);
    const target = w.byId.get(b.order.target);
    if (target) expect(Math.hypot(target.x - f.x, target.y - f.y), `went to a ${target.type}`).toBeLessThan(420);
  });

  it("a spent farm is replanted and farmed again", () => {
    const w = setup();
    const f = farmNear(w, 0);
    const v = villagers(w)[0];
    w.player(P).autoReseed = true;
    w.issueGather([v.id], f.id);
    run(w, 10);
    f.amount = 3; // almost spent
    run(w, 80);
    const now = w.entities.find((e) => e.alive && e.type === "farm" && Math.hypot(e.x - f.x, e.y - f.y) < 5)!;
    expect(now, "no farm on the old ground").toBeTruthy();
    expect(now.buildState).toBe(BuildState.Done);
    expect(farming(v, now), `after replanting: order ${v.order.kind}`).toBe(true);
  });

  it("moving a farmer to another farm frees the first for someone else", () => {
    const w = setup();
    const [f1, f2] = [farmNear(w, 0), farmNear(w, 1)];
    const [a, b] = villagers(w);
    w.issueGather([a.id], f1.id);
    run(w, 20);
    w.issueGather([a.id], f2.id);
    w.issueGather([b.id], f1.id);
    run(w, 30);
    expect(farming(a, f2)).toBe(true);
    expect(farming(b, f1)).toBe(true);
  });
});

describe("Clicking a farm", () => {
  it("anywhere on the field counts, corners included — even with a farmer standing on it", () => {
    const w = setup();
    const f = farmNear(w, 0);
    for (let i = 0; i < 5; i++) w.tick();
    const h = f.radius - 2;
    for (const [dx, dy] of [[0, 0], [h, h], [-h, h], [h, -h], [-h, -h]]) {
      expect(w.resourceAt(f.x + dx, f.y + dy, P)?.id, `at ${dx},${dy}`).toBe(f.id);
    }
  });

  it("knows which farms have nobody on them", () => {
    const w = setup();
    const f = farmNear(w, 0);
    expect(w.farmWorked(f)).toBe(false);
    w.issueGather([villagers(w)[0].id], f.id);
    w.tick();
    expect(w.farmWorked(f)).toBe(true);
  });
});
