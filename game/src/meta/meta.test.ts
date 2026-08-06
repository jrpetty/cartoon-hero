import { describe, expect, it } from "vitest";
import { RNG } from "../engine/rng";
import { CHESTS, rollChest } from "./chests";
import { RARITIES } from "./rarity";
import { computeRewards, levelFromXp, totalXpForLevel, xpForLevel } from "./progression";
import { Profile } from "./profile";
import { COLLECTIBLE_UNIT_IDS, variantKey } from "./catalog";

describe("Chest rolls", () => {
  it("respects rarity weighting over many samples", () => {
    const rng = new RNG(2024);
    const chest = CHESTS[0]; // unbiased Footman's chest
    const counts = new Array(RARITIES.length).fill(0);
    const owned = new Set<string>();
    for (let i = 0; i < 20000; i++) {
      const r = rollChest(chest, owned, rng);
      counts[r.rarity]++;
    }
    // Commons dominate, each tier rarer than the one before.
    for (let i = 1; i < counts.length; i++) {
      expect(counts[i]).toBeLessThan(counts[i - 1]);
    }
    expect(counts[0] / 20000).toBeGreaterThan(0.5);
    expect(counts[5] / 20000).toBeLessThan(0.01);
  });

  it("premium chest skews rarer than the basic chest", () => {
    const rngA = new RNG(7);
    const rngB = new RNG(7);
    const owned = new Set<string>();
    let basicHigh = 0;
    let royalHigh = 0;
    for (let i = 0; i < 8000; i++) {
      if (rollChest(CHESTS[0], owned, rngA).rarity >= 3) basicHigh++;
      if (rollChest(CHESTS[2], owned, rngB).rarity >= 3) royalHigh++;
    }
    expect(royalHigh).toBeGreaterThan(basicHigh * 2);
  });

  it("flags duplicates and pays a refund", () => {
    const rng = new RNG(5);
    const owned = new Set<string>();
    // Own everything: every roll must be a duplicate with a positive refund.
    for (const id of COLLECTIBLE_UNIT_IDS) {
      for (const r of RARITIES) owned.add(variantKey(id, r.index));
    }
    const res = rollChest(CHESTS[1], owned, rng);
    expect(res.duplicate).toBe(true);
    expect(res.refund).toBeGreaterThan(0);
  });
});

describe("Progression", () => {
  it("level curve is monotonically increasing", () => {
    for (let l = 1; l < 50; l++) {
      expect(xpForLevel(l + 1)).toBeGreaterThan(xpForLevel(l));
    }
  });

  it("levelFromXp inverts totalXpForLevel", () => {
    for (const l of [1, 2, 5, 10, 25]) {
      const { level, into } = levelFromXp(totalXpForLevel(l));
      expect(level).toBe(l);
      expect(into).toBe(0);
    }
  });

  it("wins on harder difficulties reward more", () => {
    const base = { durationSec: 600, unitsKilled: 30, buildingsRazed: 5, fairMode: false };
    const easy = computeRewards({ ...base, win: true, difficulty: "squire" });
    const hard = computeRewards({ ...base, win: true, difficulty: "warlord" });
    expect(hard.xp).toBeGreaterThan(easy.xp);
    expect(hard.renown).toBeGreaterThan(easy.renown);
  });

  it("fair mode pays a premium", () => {
    const base = { durationSec: 600, unitsKilled: 30, buildingsRazed: 5, difficulty: "knight", win: true };
    const normal = computeRewards({ ...base, fairMode: false });
    const fair = computeRewards({ ...base, fairMode: true });
    expect(fair.renown).toBeGreaterThan(normal.renown);
  });
});

describe("Profile", () => {
  it("starts with all commons owned and equipped", () => {
    const p = new Profile();
    for (const id of COLLECTIBLE_UNIT_IDS) {
      expect(p.owns(variantKey(id, 0))).toBe(true);
      expect(p.data.loadout[id]).toBe(0);
    }
  });

  it("grants, equips and resolves best rarity", () => {
    const p = new Profile();
    expect(p.grant(variantKey("knight", 3))).toBe(true);
    expect(p.grant(variantKey("knight", 3))).toBe(false); // duplicate
    expect(p.bestOwnedRarity("knight")).toBe(3);
    p.equip("knight", 3);
    expect(p.data.loadout.knight).toBe(3);
    // Can't equip what you don't own.
    p.equip("knight", 5);
    expect(p.data.loadout.knight).toBe(3);
  });

  it("fair-mode loadout flattens everything to common", () => {
    const p = new Profile();
    p.grant(variantKey("archer", 4));
    p.equip("archer", 4);
    expect(p.matchLoadout(false).archer).toBe(4);
    expect(p.matchLoadout(true).archer).toBe(0);
  });

  it("XP grants levels and level-up renown", () => {
    const p = new Profile();
    const renownBefore = p.data.renown;
    const res = p.addXp(xpForLevel(1) + 10);
    expect(res.levelsGained).toBe(1);
    expect(p.level).toBe(2);
    expect(p.data.renown).toBeGreaterThan(renownBefore);
  });

  it("renown spend respects balance", () => {
    const p = new Profile();
    p.data.renown = 100;
    expect(p.spendRenown(150)).toBe(false);
    expect(p.spendRenown(80)).toBe(true);
    expect(p.data.renown).toBe(20);
  });
});
