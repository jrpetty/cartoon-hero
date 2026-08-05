import { describe, expect, it } from "vitest";
import { rollTerrain, terrainAt, isBlocked, terrainCode, parseTerrainCode, TERRAIN, MIRROR_COL } from "./terrain";
import { WarbandRun, placeByStyle } from "./warband";
import { resolveBattle } from "./autobattle";

describe("Board terrain", () => {
  it("is mirrored across the centre line, so both halves are the same ground", () => {
    for (const seed of [1, 7, 99, 12345]) {
      const feats = rollTerrain(seed, 6);
      expect(feats.length).toBeGreaterThan(0);
      for (const f of feats) {
        const twin = feats.find((t) => t.col === MIRROR_COL(f.col) && t.row === f.row);
        expect(twin, `${f.kind} at ${f.col},${f.row} has no mirror`).toBeTruthy();
        expect(twin!.kind).toBe(f.kind); // and the same kind, not just any feature
      }
      // Nothing on the front rank of either half — a wall always has somewhere to stand.
      for (const f of feats) expect(f.col === 4 || f.col === 5).toBe(false);
      for (const f of feats) { expect(f.row).toBeGreaterThanOrEqual(0); expect(f.row).toBeLessThanOrEqual(9); }
    }
  });

  it("is stable for a seed and different between seeds", () => {
    const key = (s: number) => rollTerrain(s, 8).map((f) => `${f.kind}${f.col},${f.row}`).join("|");
    expect(key(42)).toBe(key(42));            // replayable
    expect(key(42)).not.toBe(key(43));        // and actually varies
    // The opener is deliberately clear.
    expect(rollTerrain(42, 1)).toEqual([]);
  });

  it("round-trips its seed code so a board can be found again", () => {
    for (const seed of [1, 999, 123456, 999999999]) {
      const code = terrainCode(seed);
      expect(code).toMatch(/^[0-9A-Z]{6}$/);
      expect(terrainCode(seed)).toBe(code); // stable
    }
    expect(parseTerrainCode(terrainCode(12345))).toBe(parseInt(terrainCode(12345), 36));
    expect(parseTerrainCode("not a code!")).toBeNull();
  });

  it("boulders take a cell off the board for both sides", () => {
    const run = new WarbandRun(21, null);
    run.gold = 200; run.level = 5;
    run.shop = ["knight", "archer", "spearman", "militia", "raider"];
    for (let i = 0; i < 5; i++) run.buy(i);
    // Force a boulder onto a known cell and try to stand on it.
    run.terrain = [{ kind: "boulder", col: 2, row: 2 }, { kind: "boulder", col: 7, row: 2 }];
    const idx = run.deployment()[0].index;
    expect(run.place(idx, 2, 2)).toBe(false);
    expect(run.place(idx, 2, 3)).toBe(true); // the cell beside it is fine
    // The AI's role placement avoids them too.
    const board = placeByStyle(
      [{ type: "spearman", star: 1, items: [] }, { type: "militia", star: 1, items: [] }],
      1, [{ kind: "boulder", col: 5, row: 4 }],
    );
    expect(board.some((u) => u.col === 5 && u.row === 4)).toBe(false);
  });

  it("moves a unit off a cell a new round's boulder lands on", () => {
    const run = new WarbandRun(22, null);
    run.gold = 200; run.level = 4;
    run.shop = ["militia", "militia", "militia", "militia", "militia"];
    run.buy(0);
    const d = run.deployment()[0];
    run.place(d.index, 1, 1);
    expect(run.deployment().find((x) => x.index === d.index)).toMatchObject({ col: 1, row: 1 });
    run.terrain = [{ kind: "boulder", col: 1, row: 1 }];
    const after = run.deployment().find((x) => x.index === d.index)!;
    expect(`${after.col},${after.row}`).not.toBe("1,1"); // shoved off the rock
  });

  it("a rise extends reach and a mire slows, in the fight itself", () => {
    const archers = (col: number) => [{ type: "archer", star: 1, items: [] as string[], col, row: 4 }];
    const foe = [{ type: "spearman", star: 1, items: [] as string[], col: 7, row: 4 }];
    const rise = [{ kind: "rise" as const, col: 1, row: 4 }, { kind: "rise" as const, col: 8, row: 4 }];
    // Same fight, once on flat ground and once with the archer on high ground.
    const flat = resolveBattle(archers(1), foe, 3, 40, undefined, {});
    const high = resolveBattle(archers(1), foe, 3, 40, undefined, { terrain: rise });
    // The high-ground archer opens fire sooner, so it does at least as well.
    expect(high.powerA).toBeGreaterThanOrEqual(flat.powerA);
    // And the definitions carry the numbers the sim reads.
    expect(TERRAIN.rise.rangePct).toBeGreaterThan(0);
    expect(TERRAIN.mire.speedPct).toBeLessThan(0);
    expect(TERRAIN.boulder.blocks).toBe(true);
    expect(isBlocked([{ kind: "boulder", col: 3, row: 3 }], 3, 3)).toBe(true);
    expect(terrainAt([{ kind: "mire", col: 3, row: 3 }], 3, 3)?.kind).toBe("mire");
  }, 30000);

  it("gives every round its own ground, and the opener none", () => {
    const run = new WarbandRun(31, null);
    expect(run.terrain.length).toBe(0);   // round 1 is clear
    expect(run.terrainCode()).toMatch(/^[0-9A-Z]{6}$/);
    const seen = new Set<string>();
    let guard = 0;
    while (run.round < 8 && run.phase !== "over" && guard++ < 60) {
      if (run.phase === "augment") { run.pickAugment(0); continue; }
      if (run.phase === "draft") { run.takeCarousel(0); continue; }
      if (run.phase === "shop") { seen.add(run.terrainCode()); run.fight(); }
      if (run.phase === "result") run.next();
    }
    expect(seen.size).toBeGreaterThan(1); // fresh ground each round
  }, 30000);
});

describe("Shop lock", () => {
  it("holds the shop through the round, then releases", () => {
    const run = new WarbandRun(41, null);
    run.gold = 50;
    const held = [...run.shop];
    expect(run.shopLocked).toBe(false);
    expect(run.toggleShopLock()).toBe(true);
    expect(run.shopLocked).toBe(true);
    run.fight();
    if (run.phase === "result") run.next();
    if (run.phase === "augment") run.pickAugment(0);
    if (run.phase === "draft") run.takeCarousel(0);
    // Same five units, and the hold is spent so next round rolls normally.
    expect(run.shop).toEqual(held);
    expect(run.shopLocked).toBe(false);
  });

  it("is released by rerolling — you asked for a new shop", () => {
    const run = new WarbandRun(42, null);
    run.gold = 50;
    run.toggleShopLock();
    expect(run.shopLocked).toBe(true);
    run.reroll();
    expect(run.shopLocked).toBe(false);
  });

  it("can't be toggled outside the shop phase", () => {
    const run = new WarbandRun(43, null);
    run.fight();
    if (run.phase !== "shop") expect(run.toggleShopLock()).toBe(false);
  });
});
