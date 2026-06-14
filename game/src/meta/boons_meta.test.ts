import { describe, expect, it } from "vitest";
import { Profile } from "./profile";
import { rollBoonCache, boonKey, BOON_CACHE_COST } from "./boon_cache";
import { RNG } from "../engine/rng";

describe("Boon meta", () => {
  it("starts with Valor, no boons, and one slot at level 1", () => {
    const p = new Profile();
    expect(p.data.valor).toBeGreaterThan(0);
    expect(p.data.boons.length).toBe(0);
    expect(p.boonSlots()).toBe(1);
    expect(p.unlockedBoonCategories()).toEqual(["offensive"]);
  });

  it("unlocks more slots as the account levels up", () => {
    const p = new Profile();
    p.addXp(1_000_000); // jump to a high level
    expect(p.boonSlots()).toBe(3);
    expect(p.unlockedBoonCategories()).toEqual(["offensive", "defensive", "supportive"]);
  });

  it("owns, ranks and equips boons (best rarity wins)", () => {
    const p = new Profile();
    p.addXp(1_000_000); // unlock all slots
    p.grantBoon(boonKey("whetstones", 1));
    p.grantBoon(boonKey("whetstones", 3));
    expect(p.ownsBoon(boonKey("whetstones", 3))).toBe(true);
    expect(p.bestBoonRarity("whetstones")).toBe(3);
    p.equipBoon("whetstones", true);
    const load = p.equippedBoonLoadout();
    expect(load).toContainEqual({ id: "whetstones", rarity: 3 });
  });

  it("won't equip a boon whose category slot is locked", () => {
    const p = new Profile(); // level 1 → only offensive unlocked
    p.grantBoon(boonKey("iron_discipline", 2)); // defensive
    p.equipBoon("iron_discipline", true);
    expect(p.equippedBoonLoadout().some((b) => b.id === "iron_discipline")).toBe(false);
  });

  it("Warband Cache rolls a valid boon; duplicates refund Valor", () => {
    const owned = new Set<string>();
    const rng = new RNG(7);
    const roll = rollBoonCache(owned, rng);
    expect(roll.key).toBe(boonKey(roll.boonId, roll.rarity));
    expect(roll.duplicate).toBe(false);
    owned.add(roll.key);
    // Re-roll the same seed → same draw → now a duplicate with a refund.
    const dup = rollBoonCache(owned, new RNG(7));
    expect(dup.duplicate).toBe(true);
    expect(dup.refund).toBeGreaterThan(0);
  });

  it("spends and earns Valor correctly", () => {
    const p = new Profile();
    p.data.valor = BOON_CACHE_COST;
    expect(p.spendValor(BOON_CACHE_COST)).toBe(true);
    expect(p.data.valor).toBe(0);
    expect(p.spendValor(1)).toBe(false); // can't overspend
    p.addValor(50);
    expect(p.data.valor).toBe(50);
  });
});
