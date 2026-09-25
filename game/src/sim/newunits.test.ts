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

  it("Scout: cheap Dark-age outrider with big vision from the Town Center", () => {
    const s = UNITS.scout;
    expect(s.age).toBe(0);
    expect(s.trainedAt).toBe("town_center");
    expect(BUILDINGS.town_center.trains).toContain("scout");
    expect(s.visionRange).toBeGreaterThan(UNITS.knight.visionRange);
    expect(s.attack).toBeLessThan(UNITS.militia.attack); // not a real soldier
  });

  it("Two-Handed Swordsman: Castle heavy infantry above the Man-at-Arms", () => {
    const t = UNITS.twohand;
    expect(t.age).toBe(2);
    expect(t.trainedAt).toBe("barracks");
    expect(BUILDINGS.barracks.trains).toContain("twohand");
    expect(t.attack).toBeGreaterThan(UNITS.militia.attack);
    expect(t.hp).toBeGreaterThan(UNITS.militia.hp);
  });

  it("Pikeman: Castle anti-cavalry stronger than the Spearman", () => {
    const p = UNITS.pikeman;
    expect(p.age).toBe(2);
    expect(p.trainedAt).toBe("barracks");
    expect(BUILDINGS.barracks.trains).toContain("pikeman");
    expect(p.bonus[ArmorClass.Cavalry]).toBeGreaterThan(UNITS.spearman.bonus[ArmorClass.Cavalry] ?? 0);
  });
});
