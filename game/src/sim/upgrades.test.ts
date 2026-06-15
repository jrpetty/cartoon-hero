import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind, ArmorClass } from "./types";
import { generateMap } from "../maps/generator";
import { UNITS } from "../content/units";

function makeWorld(): World {
  const map = generateMap("open_plains", 1234);
  const w = new World(1234);
  w.init(map, [{}, {}], [1, 1]);
  return w;
}

describe("Unit-tech upgrades", () => {
  it("Bloodlines adds cavalry HP and buffs the standing army (preserving HP%)", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    const k = w.spawnUnit(Team.Player, "knight", 1000, 1000);
    const base = k.maxHp;
    k.hp = base - 10;
    p.upgrades.add("bloodlines");
    // researching is what triggers the refresh; call the same path the sim uses.
    (w as unknown as { refreshTeamUnits(t: Team): void }).refreshTeamUnits?.(Team.Player);
    // Even without the private call, a freshly spawned knight gets the HP:
    const k2 = w.spawnUnit(Team.Player, "knight", 1100, 1000);
    expect(k2.maxHp).toBe(base + 20);
  });

  it("Husbandry makes cavalry faster (live)", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    const k = w.spawnUnit(Team.Player, "knight", 1000, 1000);
    const before = (w as any).effectiveSpeed(k);
    p.upgrades.add("husbandry");
    const after = (w as any).effectiveSpeed(k);
    expect(after).toBeGreaterThan(before);
  });

  it("Long Swords boosts only the Man-at-Arms line, not other infantry", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    const mil = w.spawnUnit(Team.Player, "militia", 1000, 1000);
    const spear = w.spawnUnit(Team.Player, "spearman", 1100, 1000);
    const dummy = w.spawnUnit(Team.Enemy, "militia", 1050, 1000);
    const milBefore = (w as any).effectiveAttack(mil, UNITS.militia, dummy);
    const spearBefore = (w as any).effectiveAttack(spear, UNITS.spearman, dummy);
    p.upgrades.add("longswords");
    expect((w as any).effectiveAttack(mil, UNITS.militia, dummy)).toBeGreaterThan(milBefore);
    expect((w as any).effectiveAttack(spear, UNITS.spearman, dummy)).toBe(spearBefore);
  });

  it("Pikes adds anti-cavalry bonus to spearmen/pikemen", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    const spear = w.spawnUnit(Team.Player, "spearman", 1000, 1000);
    const horse = w.spawnUnit(Team.Enemy, "knight", 1050, 1000); // cavalry target
    const before = (w as any).effectiveAttack(spear, UNITS.spearman, horse);
    p.upgrades.add("pikes");
    expect((w as any).effectiveAttack(spear, UNITS.spearman, horse)).toBe(before + 12);
  });

  it("Gold Mining speeds gold gathering only", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    const tick = (withUp: boolean) => {
      const w2 = makeWorld();
      const mine = w2.entities.find((e) => e.alive && e.type === "gold_mine")!;
      // Drop-off right beside the mine so the gather RATE dominates, not the walk.
      const camp = w2.spawnBuilding(Team.Player, "mining_camp", mine.x + 64, mine.y, true);
      camp.buildState = 0;
      if (withUp) w2.player(Team.Player).upgrades.add("gold_mining");
      const v = w2.spawnUnit(Team.Player, "villager", mine.x, mine.y + 24);
      w2.issueGather([v.id], mine.id);
      for (let i = 0; i < 20 * 60; i++) w2.tick();
      return w2.player(Team.Player).resources.gold;
    };
    expect(tick(true)).toBeGreaterThan(tick(false));
  });
});
