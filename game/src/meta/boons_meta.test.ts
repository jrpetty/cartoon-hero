import { describe, expect, it } from "vitest";
import { Profile } from "./profile";
import { rollBoonCache, boonKey, BOON_CACHE_COST, parseBoonKey } from "./boon_cache";
import { BOON_IDS } from "../content/boons";
import { RNG } from "../engine/rng";

describe("Boon meta (battle plan)", () => {
  it("starts with Valor, every boon at base rarity, and a default age order", () => {
    const p = new Profile();
    expect(p.data.valor).toBeGreaterThan(0);
    // Everyone owns every boon at Common (rarity 0) by default.
    expect(p.data.boons.length).toBe(BOON_IDS.length);
    for (const id of BOON_IDS) expect(p.bestBoonRarity(id)).toBe(0);
    expect(p.data.boonOrder.length).toBe(3);
    // Default: one of each category, distinct ages.
    expect(new Set(p.data.boonOrder).size).toBe(3);
  });

  it("equips any boon by default (all owned at base) and the cache only grants advanced tiers", () => {
    const p = new Profile();
    // No grant needed — base is owned, so it equips straight away.
    p.equipBoon("whetstones", true);
    expect(p.equippedBoonPlan().some((b) => b.id === "whetstones" && b.rarity === 0)).toBe(true);
    // Caches never roll Common.
    for (let s = 0; s < 30; s++) {
      expect(parseBoonKey(rollBoonCache(new Set(), new RNG(s)).key).rarity).toBeGreaterThanOrEqual(1);
    }
  });

  it("equips one boon per category and assigns each an unlock age", () => {
    const p = new Profile();
    p.grantBoon(boonKey("whetstones", 3));      // offensive
    p.grantBoon(boonKey("iron_discipline", 1)); // defensive
    p.grantBoon(boonKey("bountiful_fields", 2)); // supportive
    p.equipBoon("whetstones", true);
    p.equipBoon("iron_discipline", true);
    p.equipBoon("bountiful_fields", true);
    // Put the eco boon first (start), attack at age II, defense at age III.
    p.setBoonAge("supportive", 0);
    p.setBoonAge("offensive", 1);
    p.setBoonAge("defensive", 2);
    const plan = p.equippedBoonPlan();
    expect(plan).toContainEqual({ id: "bountiful_fields", rarity: 2, age: 0 });
    expect(plan).toContainEqual({ id: "whetstones", rarity: 3, age: 1 });
    expect(plan).toContainEqual({ id: "iron_discipline", rarity: 1, age: 2 });
  });

  it("setBoonAge keeps the order a permutation (swaps, never duplicates)", () => {
    const p = new Profile();
    p.setBoonAge("supportive", 0); // whatever was at age 0 moves to supportive's old slot
    expect(new Set(p.data.boonOrder).size).toBe(3); // still one per age
    expect(p.boonAgeFor("supportive")).toBe(0);
  });

  it("uses the best owned rarity of an equipped boon", () => {
    const p = new Profile();
    p.grantBoon(boonKey("whetstones", 1));
    p.grantBoon(boonKey("whetstones", 4));
    p.equipBoon("whetstones", true);
    expect(p.bestBoonRarity("whetstones")).toBe(4);
    expect(p.equippedBoonPlan().some((b) => b.id === "whetstones" && b.rarity === 4)).toBe(true);
  });

  it("Warband Cache rolls a valid boon; duplicates refund Valor", () => {
    const owned = new Set<string>();
    const roll = rollBoonCache(owned, new RNG(7));
    expect(roll.key).toBe(boonKey(roll.boonId, roll.rarity));
    expect(roll.duplicate).toBe(false);
    owned.add(roll.key);
    const dup = rollBoonCache(owned, new RNG(7));
    expect(dup.duplicate).toBe(true);
    expect(dup.refund).toBeGreaterThan(0);
  });

  it("spends and earns Valor correctly", () => {
    const p = new Profile();
    p.data.valor = BOON_CACHE_COST;
    expect(p.spendValor(BOON_CACHE_COST)).toBe(true);
    expect(p.data.valor).toBe(0);
    expect(p.spendValor(1)).toBe(false);
    p.addValor(50);
    expect(p.data.valor).toBe(50);
  });
});
