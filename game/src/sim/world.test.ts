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
    // Find an open spot near the base (the home berry patch may sit on some).
    let house = null;
    for (const [dx, dy] of [[150, 150], [-150, 150], [150, -150], [-150, -150], [210, 0], [0, -210], [-210, 0]]) {
      house = w.placeBuilding(Team.Player, "house", start.x + dx, start.y + dy);
      if (house) break;
    }
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

  it("villagers gather a berry that sits next to a mill", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    const TILE = 32;
    const berry = w.spawnResource("berries", 1600, 1600, 150);
    // A mill a few tiles away (a valid, non-overlapping drop-off).
    const mill = w.placeBuilding(Team.Player, "mill", berry.x + TILE * 3, berry.y);
    expect(mill).not.toBeNull();
    mill!.buildState = 0;
    run(w, 0.1); // settle so the spatial index sees the new nodes
    // Right-clicking on the berry resolves to the berry (not some other thing).
    expect(w.resourceAt(berry.x + 2, berry.y + 2, Team.Player)?.id).toBe(berry.id);
    // A villager beside the berry can work it and bank food.
    const vill = w.spawnUnit(Team.Player, "villager", berry.x, berry.y + TILE * 2);
    const food0 = p.resources.food;
    w.issueGather([vill.id], berry.id);
    run(w, 30);
    expect(p.resources.food).toBeGreaterThan(food0);
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

  it("units gain veterancy from kills (stronger HP/attack)", () => {
    const w = makeWorld();
    const vet = w.spawnUnit(Team.Player, "militia", 1600, 1600);
    const baseHp = vet.maxHp;
    const baseAtk = vet.attack;
    for (let n = 0; n < 6; n++) {
      const foe = w.spawnUnit(Team.Enemy, "villager", 1612, 1600);
      foe.maxHp = foe.hp = 5;
      w.issueAttack([vet.id], foe.id);
      for (let i = 0; i < SIM_HZ * 4 && foe.alive; i++) w.tick();
    }
    expect(vet.vetKills).toBe(6);
    expect(vet.veterancy).toBeGreaterThanOrEqual(2);
    expect(vet.maxHp).toBeGreaterThan(baseHp);
    expect(vet.attack).toBeGreaterThan(baseAtk);
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

  it("Heroic Cleave hits all foes around the hero and rallies allies", () => {
    const w = makeWorld();
    const hero = w.spawnUnit(Team.Player, "hero", 1000, 1000);
    const foeA = w.spawnUnit(Team.Enemy, "militia", 1040, 1000);
    const foeB = w.spawnUnit(Team.Enemy, "militia", 1000, 1050);
    const ally = w.spawnUnit(Team.Player, "spearman", 1050, 1010);
    run(w, 0.2);
    expect(w.useAbility([hero.id])).toBe(1);
    expect(foeA.hp).toBeLessThan(foeA.maxHp);
    expect(foeB.hp).toBeLessThan(foeB.maxHp);
    expect(ally.rallyTimer).toBeGreaterThan(0);
  });
});

describe("Hero units", () => {
  it("trains one Champion per player and blocks a second", () => {
    const w = makeWorld();
    const tc = w.entitiesOf(Team.Player, Kind.Building).find((e) => e.type === "town_center")!;
    w.player(Team.Player).resources.food = 999;
    w.player(Team.Player).resources.gold = 999;
    expect(w.trainUnit(Team.Player, tc.id, "hero")).toBe(true);
    expect(w.trainUnit(Team.Player, tc.id, "hero")).toBe(false); // already pending
    run(w, UNITS.hero.buildTime + 2);
    expect(w.countOf(Team.Player, "hero")).toBe(1);
    expect(w.player(Team.Player).heroState).toBe("alive");
    expect(w.trainUnit(Team.Player, tc.id, "hero")).toBe(false); // already fielded
  });

  it("levels up from nearby kills, gaining HP and attack", () => {
    const w = makeWorld();
    const hero = w.spawnUnit(Team.Player, "hero", 1000, 1000);
    const baseHp = hero.maxHp;
    const baseAtk = hero.attack;
    run(w, 0.2);
    // Three enemies die next to the hero → level 1.
    for (let i = 0; i < 3; i++) {
      const foe = w.spawnUnit(Team.Enemy, "militia", 1000 + i * 8, 1010);
      w.dealDamage(Team.Player, foe, 9999, "hero");
    }
    expect(hero.heroLevel).toBe(1);
    expect(hero.maxHp).toBeGreaterThan(baseHp);
    expect(hero.attack).toBeGreaterThan(baseAtk);
  });

  it("respawns at the Town Center after it falls", () => {
    const w = makeWorld();
    const tc = w.entitiesOf(Team.Player, Kind.Building).find((e) => e.type === "town_center")!;
    w.player(Team.Player).resources.food = 999;
    w.player(Team.Player).resources.gold = 999;
    w.trainUnit(Team.Player, tc.id, "hero");
    run(w, UNITS.hero.buildTime + 2);
    const hero = w.entitiesOf(Team.Player, Kind.Unit).find((e) => e.type === "hero")!;
    w.dealDamage(Team.Enemy, hero, 99999, "militia");
    expect(w.player(Team.Player).heroState).toBe("respawning");
    expect(w.countOf(Team.Player, "hero")).toBe(0);
    run(w, 47); // respawn timer is 45s
    expect(w.player(Team.Player).heroState).toBe("alive");
    expect(w.countOf(Team.Player, "hero")).toBe(1);
  });
});
