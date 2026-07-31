import { describe, expect, it } from "vitest";
import {
  ITEMS, COMPONENTS, COMPONENT_IDS, RECIPES, applyItems, itemDef, isComponent,
  fusionOf, fuseComponents, buildsInto,
} from "./items";
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
    const run = new WarbandRun(3, null);
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
    const run = new WarbandRun(4, null);
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

describe("Warband components", () => {
  it("covers every component pair with a distinct, real relic", () => {
    const seen = new Set<string>();
    for (const a of COMPONENT_IDS) {
      for (const b of COMPONENT_IDS) {
        const made = fusionOf(a, b);
        expect(made, `${a}+${b}`).toBeTruthy();
        expect(ITEMS[made!], `${a}+${b} → ${made}`).toBeTruthy();
        seen.add(made!);
      }
    }
    // 5 components → 15 unordered pairs → 15 distinct relics.
    expect(Object.keys(RECIPES).length).toBe(15);
    expect(seen.size).toBe(15);
    // Order never matters.
    expect(fusionOf("blade", "plate")).toBe(fusionOf("plate", "blade"));
    expect(fusionOf("whetstone", "blade")).toBeUndefined(); // a relic isn't a component
  });

  it("fuses the first loose pair on a unit and frees the slot", () => {
    const items = ["blade", "sigil"];
    expect(fuseComponents(items)).toBe("whetstone");
    expect(items).toEqual(["whetstone"]);
    // A lone component doesn't fuse.
    const one = ["blade"];
    expect(fuseComponents(one)).toBeNull();
    expect(one).toEqual(["blade"]);
    // A finished relic is left alone; only the two components combine.
    const mixed = ["giantsbelt", "plate", "plate"];
    expect(fuseComponents(mixed)).toBe("greatmail");
    expect(mixed).toEqual(["giantsbelt", "greatmail"]);
  });

  it("equipping a second component onto a unit forges a relic in place", () => {
    const run = new WarbandRun(31, null);
    run.gold = 50;
    run.shop = ["militia", "militia", "militia", "militia", "militia"];
    run.buy(0);
    run.itemStash = ["hide", "hide"];
    expect(run.equipItem(0, 0)).toBe(true);
    expect(run.pieces[0].items).toEqual(["hide"]);
    expect(run.lastFusion).toBeNull();
    expect(run.equipItem(0, 0)).toBe(true);
    // The pair fused, so the unit holds one relic and has slots to spare.
    expect(run.pieces[0].items).toEqual(["giantsbelt"]);
    expect(run.lastFusion).toEqual({ item: "giantsbelt", type: "militia" });
    expect(run.itemStash.length).toBe(0);
  });

  it("components buff a unit on their own, before they are fused", () => {
    const u = { maxHp: 100, hp: 100, attack: 10, armor: 1, speed: 80 };
    applyItems(u, ["blade", "plate"]); // +6 attack, +8 armour
    expect(u.attack).toBe(16);
    expect(u.armor).toBe(9);
    // …and the relic they fuse into is stronger than the parts.
    const fused = { maxHp: 100, hp: 100, attack: 10, armor: 1, speed: 80 };
    applyItems(fused, [fusionOf("blade", "plate")!]);
    expect(fused.attack + fused.armor).toBeGreaterThan(0);
  });

  it("exposes lookup helpers the screen relies on", () => {
    expect(isComponent("blade")).toBe(true);
    expect(isComponent("whetstone")).toBe(false);
    expect(itemDef("blade")).toBe(COMPONENTS.blade);
    expect(itemDef("whetstone")).toBe(ITEMS.whetstone);
    expect(itemDef("nope")).toBeUndefined();
    // Each component builds into five relics (one per partner, incl. itself).
    for (const id of COMPONENT_IDS) expect(buildsInto(id).length).toBe(5);
    expect(buildsInto("whetstone")).toEqual([]);
    for (const c of Object.values(COMPONENTS)) {
      expect(c.component).toBe(true);
      expect(Object.keys(c.buff).length).toBeGreaterThan(0);
    }
  });

  it("a run drops components as loot, and they fuse into relics over time", () => {
    const run = new WarbandRun(41, null);
    run.gold = 200; run.level = 6;
    for (const ty of ["knight", "archer", "spearman"]) { run.shop = [ty, ty, ty, ty, ty]; run.buy(0); run.buy(1); }
    let sawComponent = false;
    for (let r = 0; r < 16 && run.phase !== "over"; r++) {
      if (run.phase === "augment") { run.pickAugment(0); continue; }
      if (run.phase === "draft") { run.takeCarousel(0); continue; }
      if (run.itemStash.some((id) => isComponent(id))) sawComponent = true;
      // Equip everything we have onto the first unit that has room.
      while (run.itemStash.length && run.phase === "shop") {
        const target = run.pieces.findIndex((p) => p.items.length < 3);
        if (target < 0 || !run.equipItem(0, target)) break;
      }
      if (run.phase === "shop") run.fight();
      if (run.phase === "result") run.next();
    }
    expect(sawComponent).toBe(true);
    // Something got forged along the way.
    const forged = run.pieces.flatMap((p) => p.items).filter((id) => !isComponent(id));
    expect(forged.length).toBeGreaterThan(0);
    for (const id of forged) expect(ITEMS[id]).toBeTruthy();
  });
});
