import { describe, expect, it } from "vitest";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { ArmorClass } from "./types";

describe("New units", () => {
  it("Javelin Thrower: Feudal archery unit, more damage / less range than the archer", () => {
    const j = UNITS.javelin;
    expect(j.age).toBe(1);
    expect(j.trainedAt).toBe("archery_range");
    expect(BUILDINGS.archery_range.trains).toContain("javelin");
    expect(j.attack).toBeGreaterThan(UNITS.archer.attack);
    expect(j.range).toBeLessThan(UNITS.archer.range);
    expect(j.bonus[ArmorClass.Cavalry]).toBeGreaterThan(0);
  });

  it("Hand Cannoneer: Castle gunpowder — long range, slow, high damage", () => {
    const h = UNITS.handcannon;
    expect(h.age).toBe(2);
    expect(h.trainedAt).toBe("archery_range");
    expect(BUILDINGS.archery_range.trains).toContain("handcannon");
    expect(h.range).toBeGreaterThan(UNITS.archer.range); // longer range
    expect(h.attack).toBeGreaterThan(UNITS.crossbow.attack); // hits hard
    expect(h.attackInterval).toBeGreaterThan(UNITS.archer.attackInterval); // slow reload
  });

  it("Raider: fast, fragile Feudal cavalry that butchers villagers", () => {
    const r = UNITS.raider;
    expect(r.age).toBe(1);
    expect(r.trainedAt).toBe("stable");
    expect(BUILDINGS.stable.trains).toContain("raider");
    expect(r.speed).toBeGreaterThan(UNITS.knight.speed); // faster than a knight
    expect(r.hp).toBeLessThan(UNITS.knight.hp); // low-ish health
    expect(r.bonus[ArmorClass.Villager]).toBeGreaterThanOrEqual(10); // eco raider
  });
});
