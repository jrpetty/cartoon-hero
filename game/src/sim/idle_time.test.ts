import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind, BuildState } from "./types";
import { generateMap } from "../maps/generator";
import { SIM_HZ } from "../content/balance";

/** Run a world forward n seconds. */
const run = (w: World, secs: number) => { for (let i = 0; i < SIM_HZ * secs; i++) w.tick(); };

describe("Idle production time", () => {
  const boot = () => {
    const map = generateMap("open_plains", 4242, 2);
    const w = new World(4242);
    w.init(map, [{}, {}], [1, 1], [0, 1]);
    return w;
  };

  it("counts a Town Centre that is sitting there producing nothing", () => {
    const w = boot();
    run(w, 10);
    const s = w.player(Team.Player).stats;
    // A start has exactly one finished Town Centre, and nothing was queued.
    expect(s.tcSeconds).toBeGreaterThan(8);
    expect(s.idleTcTime).toBe(s.tcSeconds);
  });

  it("stops counting the moment something is queued, and resumes after", () => {
    const w = boot();
    run(w, 6);
    const before = w.player(Team.Player).stats.idleTcTime;
    const tc = w.entitiesOf(Team.Player, Kind.Building).find((b) => b.type === "town_center")!;
    w.player(Team.Player).resources.food = 500;
    expect(w.trainUnit(Team.Player, tc.id, "villager")).toBe(true);
    run(w, 5);
    const during = w.player(Team.Player).stats.idleTcTime;
    // The queue was busy for those five seconds, so idle time barely moved.
    expect(during - before).toBeLessThan(3);
    // Let the queue drain, and it starts accruing again.
    run(w, 40);
    expect(w.player(Team.Player).stats.idleTcTime).toBeGreaterThan(during + 5);
  });

  it("never counts a building that is still going up", () => {
    const w = boot();
    const p = w.player(Team.Player);
    p.resources.wood = 900; p.resources.food = 900; p.resources.gold = 900;
    const tc = w.entitiesOf(Team.Player, Kind.Building).find((b) => b.type === "town_center")!;
    // Find somewhere the ground will actually take it — the map now has
    // mountains, lakes and woods that refuse a footprint.
    let b = null;
    for (let r = 120; r < 500 && !b; r += 40) {
      for (let a = 0; a < 12 && !b; a++) {
        const ang = (a / 12) * Math.PI * 2;
        b = w.placeBuilding(Team.Player, "barracks", tc.x + Math.cos(ang) * r, tc.y + Math.sin(ang) * r);
      }
    }
    expect(b).not.toBeNull();
    expect(b!.buildState).not.toBe(BuildState.Done);
    const before = p.stats.productionSeconds;
    run(w, 8);
    // Nobody built it, so it never finished, so it was never idle — a building
    // under construction is not failing to produce, it does not exist yet.
    expect(p.stats.productionSeconds).toBe(before);
    expect(p.stats.idleProductionTime).toBe(0);
  });

  it("keeps the Town Centre and the rest of production on separate ledgers", () => {
    const w = boot();
    run(w, 8);
    const s = w.player(Team.Player).stats;
    expect(s.tcSeconds).toBeGreaterThan(0);
    // A fresh start has no barracks, range or stable.
    expect(s.productionSeconds).toBe(0);
    expect(s.idleProductionTime).toBe(0);
  });

  it("never reports more idle time than the building was even standing", () => {
    const w = boot();
    run(w, 20);
    for (const t of [Team.Player, Team.Enemy]) {
      const s = w.player(t).stats;
      expect(s.idleTcTime).toBeLessThanOrEqual(s.tcSeconds);
      expect(s.idleProductionTime).toBeLessThanOrEqual(s.productionSeconds);
    }
  });
});
