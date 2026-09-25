import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind } from "./types";
import { generateMap } from "../maps/generator";

// The renderer tweens between (prevX,prevY) and (x,y). That only looks right if
// the sim snapshots prevX/prevY to each entity's position at the START of every
// tick — verify that invariant so interpolation stays smooth and correct.
describe("Tick position snapshot (render interpolation)", () => {
  it("prevX/prevY hold last tick's position while an entity moves", () => {
    const map = generateMap("open_plains", 5, 2);
    const w = new World(5);
    w.init(map, [{}, {}], [1, 1]);
    const v = w.entitiesOf(Team.Player, Kind.Unit).find((e) => e.type === "villager")!;
    w.issueMove([v.id], v.x + 320, v.y);
    w.tick(); // start walking
    const before = v.x;
    w.tick();
    expect(v.prevX).toBe(before); // snapshot = position at the start of this tick
    expect(v.x).not.toBe(before); // and it actually advanced
  });

  it("a freshly spawned unit has prev == current (no fly-in from origin)", () => {
    const map = generateMap("open_plains", 6, 2);
    const w = new World(6);
    w.init(map, [{}, {}], [1, 1]);
    const u = w.spawnUnit(Team.Player, "militia", map.worldW / 2, map.worldH / 2);
    expect(u.prevX).toBe(u.x);
    expect(u.prevY).toBe(u.y);
  });
});
