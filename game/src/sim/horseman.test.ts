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
  it("exists as a Feudal-age melee cavalry trained at the stable", () => {
    const h = UNITS.horseman;
    expect(h).toBeTruthy();
    expect(h.age).toBe(1); // Feudal — available with the Stable
    expect(h.ranged).toBe(false); // melee cavalry
    expect(h.range).toBe(0);
    expect(h.attack).toBe(9);
    expect(h.trainedAt).toBe("stable");
    expect(BUILDINGS.stable.trains).toContain("horseman");
    expect(h.bonus.siege).toBeGreaterThan(0); // anti-siege
    expect(h.bonus.archer).toBeGreaterThan(0); // runs down archers
    // Stable now unlocks in Feudal; the Knight stays Castle.
    expect(BUILDINGS.stable.age).toBe(1);
    expect(UNITS.knight.age).toBe(2);
  });

  it("takes full melee damage, with light barding vs arrows", () => {
    const w = makeWorld();
    const arrowTarget = w.spawnUnit(Team.Player, "horseman", 1000, 1000);
    const meleeTarget = w.spawnUnit(Team.Player, "horseman", 1100, 1000);
    expect(arrowTarget.armor).toBe(1); // light melee armor
    expect(arrowTarget.pierceArmor).toBe(1);
    const hp0 = arrowTarget.maxHp;
    // 10 raw from a ranged source → 1 pierce armor → 9 dmg.
    w.dealDamage(Team.Enemy, arrowTarget, 10, "archer", -1, true);
    expect(arrowTarget.hp).toBe(hp0 - 9);
    // 10 raw from melee → 1 melee armor → 9 dmg.
    w.dealDamage(Team.Enemy, meleeTarget, 10, "militia", -1, false);
    expect(meleeTarget.hp).toBe(hp0 - 9);
  });

  it("charges into melee and chews siege (bonus damage)", () => {
    const w = makeWorld();
    const horse = w.spawnUnit(Team.Player, "horseman", 1000, 1000);
    const ram = w.spawnUnit(Team.Enemy, "ram", 1040, 1000);
    w.issueAttack([horse.id], ram.id);
    const ramHp0 = ram.hp;
    for (let i = 0; i < 20 * 6; i++) w.tick();
    expect(ram.hp).toBeLessThan(ramHp0); // closed the gap and is hacking it down
    expect(UNITS.horseman.bonus.siege).toBe(10);
  });
});
