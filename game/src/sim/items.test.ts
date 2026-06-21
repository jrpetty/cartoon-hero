import { describe, expect, it } from "vitest";
import { ITEMS, applyItems } from "./items";
import { resolveBattle } from "./autobattle";
import { WarbandRun } from "./warband";

describe("Warband relics", () => {
  it("applyItems stacks flat and percent buffs", () => {
    const u = { maxHp: 100, hp: 100, attack: 10, armor: 1, speed: 80 };
    applyItems(u, ["whetstone", "greatmail"]); // +30% attack, +12 armour
    expect(u.attack).toBe(13);
    expect(u.armor).toBe(13);
  });

  it("a fully-itemized carry sweeps an identical un-itemized unit", () => {
    const buffed = [{ type: "knight", star: 1, items: ["giantsbelt", "warbanner", "whetstone"] }];
    const plain = [{ type: "knight", star: 1 }];
    let wins = 0;
    for (const seed of [1, 2, 3, 4, 5]) if (resolveBattle(buffed, plain, seed).winner === "A") wins++;
    expect(wins).toBe(5);
  });

  it("equipping moves a relic onto a unit, capped at 3", () => {
    const run = new WarbandRun(3);
    run.gold = 50;
    run.shop = ["militia", "militia", "militia", "militia", "militia"];
    run.buy(0);
    run.itemStash = ["whetstone", "greatmail", "warhorn", "giantsbelt"];
    expect(run.equipItem(0, 0)).toBe(true);
    expect(run.equipItem(0, 0)).toBe(true);
    expect(run.equipItem(0, 0)).toBe(true);
    expect(run.equipItem(0, 0)).toBe(false); // 4th rejected
    expect(run.pieces[0].items.length).toBe(3);
    expect(run.itemStash.length).toBe(1);
  });

  it("merging a star-up inherits the components' relics", () => {
    const run = new WarbandRun(4);
    run.gold = 99;
    run.shop = ["archer", "archer", "archer", "archer", "archer"]; run.buy(0);
    run.itemStash = ["whetstone"]; run.equipItem(0, 0); // archer #1 ← Whetstone
    run.shop = ["archer", "archer", "archer", "archer", "archer"]; run.buy(0);
    run.itemStash = ["greatmail"]; run.equipItem(0, run.pieces.length - 1); // archer #2 ← Greatmail
    run.shop = ["archer", "archer", "archer", "archer", "archer"]; run.buy(0); // #3 → merge to 2-star
    const two = run.pieces.find((p) => p.star === 2);
    expect(two).toBeTruthy();
    expect(two!.items.length).toBeGreaterThanOrEqual(2); // carried Whetstone + Greatmail
  });

  it("ITEMS all have a buff and a label", () => {
    for (const it of Object.values(ITEMS)) {
      expect(it.short.length).toBeGreaterThan(0);
      expect(Object.keys(it.buff).length).toBeGreaterThan(0);
    }
  });
});
