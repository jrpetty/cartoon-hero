import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind } from "./types";
import { generateMap } from "../maps/generator";
import { BUILDINGS } from "../content/buildings";

function worldWithBoon(boon?: { id: string; rarity: number }): World {
  const map = generateMap("open_plains", 4242);
  const w = new World(4242);
  // Team 0 gets the boon active from the start (age 0); team 1 is the baseline.
  const plan = boon ? [{ ...boon, age: 0 }] : [];
  w.init(map, [{}, {}], [1, 1], undefined, undefined, false, [plan, []]);
  return w;
}

describe("Boons", () => {
  it("Whetstones raises infantry attack vs baseline", () => {
    const base = worldWithBoon();
    const buffed = worldWithBoon({ id: "whetstones", rarity: 2 });
    const b = base.spawnUnit(Team.Player, "militia", 1000, 1000);
    const a = buffed.spawnUnit(Team.Player, "militia", 1000, 1000);
    expect(a.attack).toBeGreaterThan(b.attack);
  });

  it("Iron Discipline raises unit HP; Pavise raises armor", () => {
    const base = worldWithBoon();
    const hp = worldWithBoon({ id: "iron_discipline", rarity: 3 });
    const ar = worldWithBoon({ id: "pavise", rarity: 4 });
    const b = base.spawnUnit(Team.Player, "spearman", 1000, 1000);
    expect(hp.spawnUnit(Team.Player, "spearman", 1000, 1000).maxHp).toBeGreaterThan(b.maxHp);
    expect(ar.spawnUnit(Team.Player, "spearman", 1000, 1000).armor).toBeGreaterThan(b.armor);
  });

  it("a higher-rarity boon is stronger than a lower one (tiers scale)", () => {
    const lo = worldWithBoon({ id: "whetstones", rarity: 0 });
    const hi = worldWithBoon({ id: "whetstones", rarity: 5 });
    const l = lo.spawnUnit(Team.Player, "militia", 1000, 1000);
    const h = hi.spawnUnit(Team.Player, "militia", 1000, 1000);
    expect(h.attack).toBeGreaterThan(l.attack);
  });

  it("Master Masons makes buildings tougher", () => {
    const base = worldWithBoon();
    const masons = worldWithBoon({ id: "master_masons", rarity: 3 });
    const b = base.spawnBuilding(Team.Player, "barracks", 1200, 1200, true);
    const m = masons.spawnBuilding(Team.Player, "barracks", 1200, 1200, true);
    expect(m.maxHp).toBeGreaterThan(b.maxHp);
  });

  it("Bountiful Fields speeds food gathering", () => {
    const base = worldWithBoon();
    const fields = worldWithBoon({ id: "bountiful_fields", rarity: 4 });
    const tick = (w: World) => {
      const berry = w.entities.find((e) => e.alive && e.type === "berries")!;
      // A mill right next to the berries so deposits are instant and the gather
      // RATE (not the walk) is what's measured.
      const mill = w.spawnBuilding(Team.Player, "mill", berry.x + 32 * 3, berry.y, true);
      mill.buildState = 0;
      const v = w.spawnUnit(Team.Player, "villager", berry.x, berry.y + 24);
      w.issueGather([v.id], berry.id);
      for (let i = 0; i < 20 * 30; i++) w.tick();
      return w.player(Team.Player).resources.food;
    };
    expect(tick(fields)).toBeGreaterThan(tick(base));
  });

  it("an age-gated boon is inactive until that age, then buffs the existing army", () => {
    const map = generateMap("open_plains", 4242);
    const w = new World(4242);
    // Iron Discipline assigned to Age II (unlock age 1), not active at the start.
    w.init(map, [{}, {}], [1, 1], undefined, undefined, false, [[{ id: "iron_discipline", rarity: 3, age: 1 }], []]);
    const u = w.spawnUnit(Team.Player, "spearman", 1000, 1000);
    const baseHp = u.maxHp;
    // Take a little damage so we can check the buff scales HP without a free heal.
    u.hp = baseHp - 10;
    // Advance the realm to Age II → the boon activates and the existing unit grows.
    w.player(Team.Player).age = 1;
    w.recomputeBoons(Team.Player);
    expect(u.maxHp).toBeGreaterThan(baseHp);
    expect(u.hp).toBeLessThan(u.maxHp); // still wounded — no free heal
  });

  it("boon-free players are unaffected (AI/tests unchanged)", () => {
    const w = worldWithBoon();
    const u = w.spawnUnit(Team.Player, "knight", 1000, 1000);
    const e = w.spawnUnit(Team.Enemy, "knight", 1100, 1000);
    expect(u.attack).toBe(e.attack);
    expect(u.maxHp).toBe(e.maxHp);
  });
});
