import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind } from "./types";
import { generateMap } from "../maps/generator";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";

function makeWorld(): World {
  const map = generateMap("open_plains", 1234);
  const w = new World(1234);
  w.init(map, [{}, {}], [1, 1]);
  return w;
}

describe("Horseman", () => {
  it("exists as a Feudal-age ranged cavalry trained at the stable", () => {
    const h = UNITS.horseman;
    expect(h).toBeTruthy();
    expect(h.age).toBe(1); // Feudal — available with the Stable
    expect(h.ranged).toBe(true);
    expect(h.attack).toBe(8);
    expect(h.trainedAt).toBe("stable");
    expect(BUILDINGS.stable.trains).toContain("horseman");
    expect(h.bonus.siege).toBeGreaterThan(0); // bonus damage vs siege
    // Stable now unlocks in Feudal; the Knight stays Castle.
    expect(BUILDINGS.stable.age).toBe(1);
    expect(UNITS.knight.age).toBe(2);
  });

  it("spawns with 2 pierce armor and 0 melee armor", () => {
    const w = makeWorld();
    const h = w.spawnUnit(Team.Player, "horseman", 1000, 1000);
    expect(h.armor).toBe(0); // no melee armor
    expect(h.pierceArmor).toBe(2); // light barding turns arrows
  });

  it("shrugs off arrows but takes full melee damage", () => {
    const w = makeWorld();
    const arrowTarget = w.spawnUnit(Team.Player, "horseman", 1000, 1000);
    const meleeTarget = w.spawnUnit(Team.Player, "horseman", 1100, 1000);
    const hp0 = arrowTarget.maxHp;
    // 10 raw from a ranged source → 2 pierce armor → 8 dmg.
    w.dealDamage(Team.Enemy, arrowTarget, 10, "archer", -1, true);
    expect(arrowTarget.hp).toBe(hp0 - 8);
    // 10 raw from melee → 0 melee armor → full 10 dmg.
    w.dealDamage(Team.Enemy, meleeTarget, 10, "militia", -1, false);
    expect(meleeTarget.hp).toBe(hp0 - 10);
  });

  it("deals bonus damage to siege", () => {
    const w = makeWorld();
    const horse = w.spawnUnit(Team.Player, "horseman", 1000, 1000);
    const ram = w.spawnUnit(Team.Enemy, "ram", 1080, 1000);
    const infantry = w.spawnUnit(Team.Enemy, "militia", 1080, 1100);
    w.issueAttack([horse.id], ram.id);
    // Fire a few volleys and confirm the siege engine is being chewed faster
    // than a same-HP-ish comparison would be — just assert it takes real damage.
    const ramHp0 = ram.hp;
    for (let i = 0; i < 20 * 6; i++) w.tick();
    expect(ram.hp).toBeLessThan(ramHp0);
    expect(UNITS.horseman.bonus.siege).toBe(14);
  });
});
