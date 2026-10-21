import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind } from "./types";
import { generateMap } from "../maps/generator";
import { SIM_HZ } from "../content/balance";

function run(w: World, seconds: number) {
  for (let i = 0; i < Math.ceil(seconds * SIM_HZ); i++) w.tick();
}

describe("Nomad + game modes", () => {
  it("Nomad Regicide: no Town Center, scattered villagers, a King — kill theirs to win", () => {
    const map = generateMap("open_plains", 31, 2, true);
    const w = new World(31);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, true, undefined, "regicide");
    // Nomad start: no TC, but villagers and a King for each realm.
    expect(w.countOf(Team.Player, "town_center")).toBe(0);
    expect(w.entitiesOf(Team.Player, Kind.Unit).filter((e) => e.type === "villager").length).toBeGreaterThan(0);
    expect(w.entities.filter((e) => e.alive && e.type === "king").length).toBe(2);
    const enemyKing = w.entities.find((e) => e.alive && e.type === "king" && e.team === Team.Enemy)!;
    w.dealDamage(Team.Player, enemyKing, 99999, "test");
    run(w, 2);
    expect(w.winner).toBe(Team.Player);
  });

  it("Nomad Regicide: buildings don't decide it (razing a base is not a loss)", () => {
    const map = generateMap("open_plains", 32, 2, true);
    const w = new World(32);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, true, undefined, "regicide");
    // Wipe a realm's villagers/buildings but leave its King — it must NOT lose.
    for (const e of w.entitiesOf(Team.Enemy)) {
      if (e.type !== "king") w.dealDamage(Team.Player, e, 99999, "test");
    }
    run(w, 2);
    expect(w.player(Team.Enemy).defeated).toBe(false);
    expect(w.winner).toBe(null);
  });

  it("Nomad Survival: baseless horde, scattered defenders, waves seek the villagers", () => {
    const map = generateMap("open_plains", 33, 3, true);
    const w = new World(33);
    w.init(map, [{}, {}, {}], [1, 1, 1], [0, 0, 1], undefined, true, undefined, "survival");
    expect(w.hordeTeam).toBe(2);
    expect(w.countOf(Team.Player, "town_center")).toBe(0);
    expect(w.entitiesOf(Team.Player, Kind.Unit).length).toBeGreaterThan(0);
    run(w, 50);
    expect(w.survivalWave).toBeGreaterThanOrEqual(1);
    expect(w.entitiesOf(Team.Team3, Kind.Unit).length).toBeGreaterThan(0); // a wave is on the field
  });

  it("Nomad King of the Hill: holding still wins from a nomad start", () => {
    const map = generateMap("open_plains", 34, 2, true);
    const w = new World(34);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, true, undefined, "koth");
    w.kothGoal = 4;
    w.spawnUnit(Team.Player, "militia", w.hillX, w.hillY);
    run(w, 5);
    expect(w.winner).toBe(Team.Player);
  });
});
