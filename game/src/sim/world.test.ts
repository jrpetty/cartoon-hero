import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Kind, OrderKind, Team } from "./types";
import { generateMap } from "../maps/generator";
import { SIM_HZ, START_RESOURCES } from "../content/balance";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";

function makeWorld(seed = 1234): World {
  const map = generateMap("open_plains", seed);
  const w = new World(seed);
  w.init(map, [{}, {}], [1, 1]);
  return w;
}

function run(w: World, seconds: number) {
  const ticks = Math.ceil(seconds * SIM_HZ);
  for (let i = 0; i < ticks; i++) w.tick();
}

describe("World setup", () => {
  it("spawns both bases with villagers and a town center", () => {
    const w = makeWorld();
    for (const team of [Team.Player, Team.Enemy]) {
      expect(w.countOf(team, "town_center")).toBe(1);
      expect(w.countOf(team, "villager")).toBe(3);
      const p = w.player(team);
      expect(p.popUsed).toBe(3);
      expect(p.popCap).toBe(10); // town center provides 10
      expect(p.resources).toEqual(START_RESOURCES);
    }
  });

  it("is deterministic: same seed, same state hash after 30s", () => {
    const hash = (w: World) => {
      let h = 0;
      for (const e of w.entities) {
        if (!e.alive) continue;
        h = (h * 31 + ((e.x * 7919) | 0) + ((e.y * 104729) | 0) + e.id) | 0;
      }
      return h;
    };
    const a = makeWorld(777);
    const b = makeWorld(777);
    run(a, 30);
    run(b, 30);
    expect(hash(a)).toBe(hash(b));
  });
});

describe("Economy", () => {
  it("villagers gather wood and deposit it", () => {
    const w = makeWorld();
    const start = w.player(Team.Player).resources.wood;
    // Send all villagers to the nearest tree.
    const tree = w.entities.find((e) => e.alive && e.type === "tree")!;
    const vills = w.entitiesOf(Team.Player, Kind.Unit).filter((e) => e.type === "villager");
    w.issueGather(vills.map((v) => v.id), tree.id);
    run(w, 120);
    expect(w.player(Team.Player).resources.wood).toBeGreaterThan(start);
    expect(w.player(Team.Player).stats.gathered).toBeGreaterThan(0);
  });

  it("trains a villager at the town center (cost + pop accounting)", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    const tc = w.entitiesOf(Team.Player, Kind.Building).find((e) => e.type === "town_center")!;
    const foodBefore = p.resources.food;
    expect(w.trainUnit(Team.Player, tc.id, "villager")).toBe(true);
    expect(p.resources.food).toBe(foodBefore - UNITS.villager.cost.food);
    run(w, UNITS.villager.buildTime + 2);
    expect(w.countOf(Team.Player, "villager")).toBe(4);
    expect(p.popUsed).toBe(4);
  });

  it("blocks training when population-capped", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    p.popUsed = p.popCap;
    const tc = w.entitiesOf(Team.Player, Kind.Building).find((e) => e.type === "town_center")!;
    expect(w.trainUnit(Team.Player, tc.id, "villager")).toBe(false);
  });
});

describe("Construction & placement", () => {
  it("places a house, builds it with a villager, and pop cap rises", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    const start = w.map.starts[Team.Player];
    const before = p.popCap;
    const woodBefore = p.resources.wood;
    const house = w.placeBuilding(Team.Player, "house", start.x + 150, start.y + 150);
    expect(house).not.toBeNull();
    expect(p.resources.wood).toBe(woodBefore - BUILDINGS.house.cost.wood);
    const vills = w.entitiesOf(Team.Player, Kind.Unit).filter((e) => e.type === "villager");
    w.issueBuildRepair(vills.map((v) => v.id), house!.id);
    run(w, 40);
    expect(p.popCap).toBe(before + BUILDINGS.house.popProvided);
  });

  it("rejects placement on top of an existing building", () => {
    const w = makeWorld();
    const tc = w.entitiesOf(Team.Player, Kind.Building)[0];
    const blocked = w.placeBuilding(Team.Player, "house", tc.x, tc.y);
    expect(blocked).toBeNull();
  });
});

describe("Combat", () => {
  it("applies armor and never deals less than 1 damage", () => {
    const w = makeWorld();
    const u = w.spawnUnit(Team.Enemy, "militia", 600, 600);
    const hp = u.hp;
    w.dealDamage(Team.Player, u, 5, "militia"); // 5 raw vs 1 armor => 4
    expect(u.hp).toBe(hp - 4);
    w.dealDamage(Team.Player, u, 1, "militia"); // floor at 1
    expect(u.hp).toBe(hp - 5);
  });

  it("the counter triangle holds in 1v1 duels", () => {
    // Duel arena far from both bases.
    const w = makeWorld();
    const duel = (a: string, b: string): string => {
      const ua = w.spawnUnit(Team.Player, a, 1600, 1600);
      const ub = w.spawnUnit(Team.Enemy, b, 1650, 1600);
      w.issueAttack([ua.id], ub.id);
      w.issueAttack([ub.id], ua.id);
      for (let i = 0; i < SIM_HZ * 60; i++) {
        w.tick();
        if (!ua.alive || !ub.alive) break;
      }
      const winner = ua.alive ? a : b;
      // Clean up survivors so duels don't interfere.
      ua.alive = false;
      ub.alive = false;
      return winner;
    };
    // Spears gut cavalry; knights run down archers; in a toe-to-toe melee the
    // man-at-arms beats the archer (archers win at range, in numbers).
    expect(duel("spearman", "knight")).toBe("spearman");
    expect(duel("knight", "archer")).toBe("knight");
    expect(duel("militia", "archer")).toBe("militia");
    expect(duel("skirmisher", "archer")).toBe("skirmisher");
  });

  it("a unit ordered to attack a building destroys it", () => {
    const w = makeWorld();
    const house = w.placeBuilding(Team.Enemy, "house", 1500, 1500)!;
    house.buildState = 0; // BuildState.Done
    house.hp = 60;
    const ram = w.spawnUnit(Team.Player, "ram", 1400, 1500);
    w.issueAttack([ram.id], house.id);
    run(w, 30);
    expect(house.alive).toBe(false);
    expect(w.player(Team.Player).stats.buildingsRazed).toBe(1);
  });
});

describe("Fog of war", () => {
  it("enemy base starts unexplored and own base is visible", () => {
    const w = makeWorld();
    const own = w.map.starts[Team.Player];
    const foe = w.map.starts[Team.Enemy];
    expect(w.fogAt(Team.Player, own.x, own.y)).toBe(2); // visible
    expect(w.fogAt(Team.Player, foe.x, foe.y)).toBe(0); // unseen
  });

  it("a scout reveals terrain and it stays explored after leaving", () => {
    const w = makeWorld();
    const mid = { x: w.worldW / 2, y: w.worldH / 2 };
    const scout = w.spawnUnit(Team.Player, "knight", mid.x, mid.y);
    run(w, 1);
    expect(w.fogAt(Team.Player, mid.x, mid.y)).toBe(2);
    scout.x = 200;
    scout.y = 200;
    run(w, 1);
    expect(w.fogAt(Team.Player, mid.x, mid.y)).toBe(1); // explored
  });
});

describe("Victory", () => {
  it("declares a winner when one side loses all buildings", () => {
    const w = makeWorld();
    for (const e of w.entitiesOf(Team.Enemy, Kind.Building)) {
      w.dealDamage(Team.Player, e, 999999, "test");
    }
    run(w, 2);
    expect(w.winner).toBe(Team.Player);
    expect(w.player(Team.Enemy).defeated).toBe(true);
  });
});

describe("Unit abilities", () => {
  it("Sanctuary heals wounded allies in radius", () => {
    const w = makeWorld();
    const monk = w.spawnUnit(Team.Player, "monk", 1000, 1000);
    const ally = w.spawnUnit(Team.Player, "militia", 1040, 1000);
    w.dealDamage(Team.Enemy, ally, 40, "militia");
    const hurt = ally.hp;
    expect(hurt).toBeLessThan(ally.maxHp);
    run(w, 0.2); // settle so the spatial index sees the units
    expect(w.useAbility([monk.id])).toBe(1);
    expect(ally.hp).toBeGreaterThan(hurt);
  });

  it("Caltrops slows nearby enemies but not allies", () => {
    const w = makeWorld();
    const skirm = w.spawnUnit(Team.Player, "skirmisher", 1000, 1000);
    const foe = w.spawnUnit(Team.Enemy, "knight", 1050, 1000);
    const friend = w.spawnUnit(Team.Player, "militia", 1050, 1010);
    run(w, 0.2);
    w.useAbility([skirm.id]);
    expect(foe.slowTimer).toBeGreaterThan(0);
    expect(friend.slowTimer).toBe(0);
  });

  it("War Cry rallies nearby allies", () => {
    const w = makeWorld();
    const leader = w.spawnUnit(Team.Player, "militia", 1000, 1000);
    const ally = w.spawnUnit(Team.Player, "spearman", 1060, 1000);
    run(w, 0.2);
    w.useAbility([leader.id]);
    expect(leader.rallyTimer).toBeGreaterThan(0);
    expect(ally.rallyTimer).toBeGreaterThan(0);
  });

  it("Arrow Volley strikes everything around the target", () => {
    const w = makeWorld();
    const archer = w.spawnUnit(Team.Player, "archer", 1000, 1000);
    const a = w.spawnUnit(Team.Enemy, "militia", 1000, 1100);
    const b = w.spawnUnit(Team.Enemy, "militia", 1030, 1110);
    run(w, 0.2);
    w.issueAttack([archer.id], a.id);
    w.useAbility([archer.id]);
    expect(a.hp).toBeLessThan(a.maxHp);
    expect(b.hp).toBeLessThan(b.maxHp);
  });

  it("an ability can't be used again until off cooldown", () => {
    const w = makeWorld();
    const knight = w.spawnUnit(Team.Player, "knight", 1000, 1000);
    expect(w.useAbility([knight.id])).toBe(1);
    expect(w.useAbility([knight.id])).toBe(0); // still cooling down
    expect(knight.abilityActive).toBeGreaterThan(0);
  });
});
