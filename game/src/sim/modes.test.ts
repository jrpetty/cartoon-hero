import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind } from "./types";
import { generateMap } from "../maps/generator";
import { SIM_HZ } from "../content/balance";

function run(w: World, seconds: number) {
  for (let i = 0; i < Math.ceil(seconds * SIM_HZ); i++) w.tick();
}

describe("Regicide", () => {
  it("spawns a King per realm; slaying the enemy King wins", () => {
    const map = generateMap("open_plains", 11, 2);
    const w = new World(11);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, false, undefined, "regicide");
    const kings = w.entities.filter((e) => e.alive && e.type === "king");
    expect(kings.length).toBe(2);
    const enemyKing = kings.find((k) => k.team === Team.Enemy)!;
    w.dealDamage(Team.Player, enemyKing, 99999, "test");
    run(w, 2);
    expect(w.player(Team.Enemy).defeated).toBe(true);
    expect(w.winner).toBe(Team.Player);
  });

  it("losing your own King loses the game", () => {
    const map = generateMap("open_plains", 12, 2);
    const w = new World(12);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, false, undefined, "regicide");
    const myKing = w.entities.find((e) => e.alive && e.type === "king" && e.team === Team.Player)!;
    w.dealDamage(Team.Enemy, myKing, 99999, "test");
    run(w, 2);
    expect(w.player(Team.Player).defeated).toBe(true);
    expect(w.winner).toBe(Team.Enemy);
  });
});

describe("Survival", () => {
  it("sets up a baseless horde team and spawns waves at the players", () => {
    const map = generateMap("open_plains", 13, 3);
    const w = new World(13);
    w.init(map, [{}, {}, {}], [1, 1, 1], [0, 0, 1], undefined, false, undefined, "survival");
    expect(w.hordeTeam).toBe(2);
    // Horde has no base/villagers.
    expect(w.countOf(Team.Team3, "town_center")).toBe(0);
    // Players do.
    expect(w.countOf(Team.Player, "town_center")).toBe(1);
    run(w, 50); // past the 45s grace → at least one wave
    expect(w.survivalWave).toBeGreaterThanOrEqual(1);
    expect(w.entitiesOf(Team.Team3, Kind.Unit).length).toBeGreaterThan(0);
  });

  it("clearing all waves wins; being wiped out loses", () => {
    const map = generateMap("open_plains", 14, 2);
    const w = new World(14);
    w.init(map, [{}, {}], [1, 0], [0, 1], undefined, false, undefined, "survival");
    // Win: all waves done and no horde units left.
    w.survivalWon = true;
    run(w, 2);
    expect(w.winner).toBe(Team.Player);

    // Wipe path: a fresh game where the player's base is gone.
    const w2 = new World(15);
    w2.init(generateMap("open_plains", 15, 2), [{}, {}], [1, 0], [0, 1], undefined, false, undefined, "survival");
    for (const e of w2.entitiesOf(Team.Player)) w2.dealDamage(Team.Enemy, e, 99999, "test");
    run(w2, 2);
    expect(w2.player(Team.Player).defeated).toBe(true);
  });
});

describe("King of the Hill", () => {
  it("uncontested holding accrues to the goal and wins", () => {
    const map = generateMap("open_plains", 16, 2);
    const w = new World(16);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, false, undefined, "koth");
    w.kothGoal = 4; // short for the test
    w.spawnUnit(Team.Player, "militia", w.hillX, w.hillY); // stand on the hill
    run(w, 5);
    expect(w.winner).toBe(Team.Player);
  });

  it("an enemy on the hill pauses the timer (no progress while contested)", () => {
    const map = generateMap("open_plains", 17, 2);
    const w = new World(17);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, false, undefined, "koth");
    w.kothGoal = 3;
    const mine = w.spawnUnit(Team.Player, "militia", w.hillX, w.hillY);
    const foe = w.spawnUnit(Team.Enemy, "militia", w.hillX + 10, w.hillY); // contesting
    run(w, 5);
    expect(w.winner).toBe(null); // contested → no one accrues
    // Remove the enemy; now it resumes from where it paused.
    foe.alive = false;
    run(w, 4);
    expect(w.winner).toBe(Team.Player);
  });
});
