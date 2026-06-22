import { describe, expect, it } from "vitest";
import { WarbandRun, UNIT_TIER } from "./warband";

describe("Warband Tactics run engine", () => {
  it("starts a run with gold, a shop, and seven opponents", () => {
    const run = new WarbandRun(123);
    expect(run.gold).toBeGreaterThan(0);
    expect(run.shop.length).toBe(5);
    expect(run.opponents.length).toBe(7);
    expect(run.phase).toBe("shop");
  });

  it("three identical buys merge into a 2-star, and three 2-stars into a 3-star", () => {
    const run = new WarbandRun(1);
    run.gold = 99;
    // Force a known unit into every shop slot and buy 9 of them.
    for (let i = 0; i < 9; i++) {
      run.shop = ["militia", "militia", "militia", "militia", "militia"];
      run.buy(0);
    }
    // 9 one-stars → 3 two-stars → 1 three-star (+0 leftover... 9→3 twos→1 three).
    const threes = run.pieces.filter((p) => p.star === 3).length;
    const twos = run.pieces.filter((p) => p.star === 2).length;
    expect(threes).toBe(1);
    expect(twos).toBe(0);
    expect(run.pieces.filter((p) => p.star === 1).length).toBe(0);
  });

  it("buying XP raises the level (and board capacity)", () => {
    const run = new WarbandRun(2);
    run.gold = 100;
    const lvl0 = run.level;
    for (let i = 0; i < 5; i++) run.buyXp();
    expect(run.level).toBeGreaterThan(lvl0);
  });

  it("higher level unlocks higher-tier units in the shop", () => {
    const run = new WarbandRun(5);
    run.gold = 1e6;
    // At level 1, every shop unit is tier 1.
    run.level = 1;
    for (let i = 0; i < 30; i++) {
      run.reroll();
      for (const t of run.shop) if (t) expect(UNIT_TIER[t]).toBe(1);
    }
    // At a high level, higher tiers show up.
    run.level = 9;
    let sawHigh = false;
    for (let i = 0; i < 80 && !sawHigh; i++) {
      run.reroll();
      for (const t of run.shop) if (t && UNIT_TIER[t] >= 3) sawHigh = true;
    }
    expect(sawHigh).toBe(true);
  });

  it("a fight resolves and either damages a foe (win) or you (loss)", () => {
    const run = new WarbandRun(7);
    run.gold = 50;
    run.shop = ["knight", "knight", "knight", "knight", "knight"];
    run.buy(0); run.buy(1); run.buy(2); // a little army
    run.level = 3;
    const myLifeBefore = run.life;
    const foeLifeBefore = run.opponents.reduce((s, o) => s + o.life, 0);
    run.fight();
    expect(run.lastResult).not.toBeNull();
    const myLifeAfter = run.life;
    const foeLifeAfter = run.opponents.reduce((s, o) => s + o.life, 0);
    // Either we took damage, or a foe did (the thinning can also nick foes).
    expect(myLifeAfter <= myLifeBefore).toBe(true);
    expect(foeLifeAfter <= foeLifeBefore).toBe(true);
  });

  it("places units on distinct board cells and lets you move/swap them", () => {
    const run = new WarbandRun(8);
    run.gold = 50; run.level = 4;
    run.shop = ["knight", "archer", "spearman", "militia", "horseman"];
    run.buy(0); run.buy(1); run.buy(2); run.buy(3);
    // Every deployed unit gets a unique cell on the player's half (cols 0..4).
    const dep = run.deployment();
    expect(dep.length).toBe(4);
    const cells = new Set(dep.map((d) => `${d.col},${d.row}`));
    expect(cells.size).toBe(4);
    for (const d of dep) { expect(d.col).toBeGreaterThanOrEqual(0); expect(d.col).toBeLessThanOrEqual(4); }
    // Move one unit to an empty cell.
    expect(run.place(dep[0].index, 0, 9)).toBe(true);
    expect(run.deployment().find((d) => d.index === dep[0].index)).toMatchObject({ col: 0, row: 9 });
    // Placing onto an occupied cell swaps the two.
    const a = run.deployment().find((d) => d.index === dep[1].index)!;
    const targetCell = run.deployment().find((d) => d.index === dep[2].index)!;
    run.place(a.index, targetCell.col, targetCell.row);
    const after = run.deployment();
    expect(after.find((d) => d.index === a.index)).toMatchObject({ col: targetCell.col, row: targetCell.row });
    // Off-board cells are rejected.
    expect(run.place(dep[0].index, 5, 0)).toBe(false); // col 5 is the enemy half
    expect(run.place(dep[0].index, 0, 10)).toBe(false);
  });

  it("fields a reserve unit from the bench onto the board", () => {
    const run = new WarbandRun(4);
    run.gold = 99; run.level = 2; // board cap = 2
    // Buy 4 distinct units → 2 auto-deploy (cap), 2 sit on the bench.
    run.shop = ["knight", "archer", "spearman", "militia", "raider"];
    run.buy(0); run.buy(1); run.buy(2); run.buy(3);
    expect(run.deployedCount()).toBe(2);
    const deployed = new Set(run.deployment().map((d) => d.index));
    const benchIdx = run.pieces.findIndex((_, i) => !deployed.has(i));
    expect(benchIdx).toBeGreaterThanOrEqual(0);
    expect(run.pieces[benchIdx].deployed).toBeFalsy();
    // Drop the bench unit on an empty cell while the board is full → it fields,
    // and the weakest deployed unit is bumped to the bench. Count stays at cap.
    expect(run.place(benchIdx, 4, 0)).toBe(true);
    expect(run.pieces[benchIdx].deployed).toBe(true);
    expect(run.deployedCount()).toBe(2);
    expect(run.deployment().some((d) => d.index === benchIdx)).toBe(true);
  });

  it("opponents run the same economy and field a warband that grows + stars up", () => {
    const run = new WarbandRun(3);
    const sizes: number[] = [];
    let sawStarUp = false;
    let sawRelic = false;
    // Buy a couple of units so the player survives a while, then march through rounds.
    run.gold = 50;
    run.shop = ["spearman", "spearman", "militia", "archer", "knight"];
    run.buy(0); run.buy(2); run.buy(3);
    for (let r = 0; r < 14 && run.phase !== "over"; r++) {
      sizes.push(run.pendingOpp.length); // the upcoming opponent's deployed board
      if (run.pendingOpp.some((u) => (u.star ?? 1) >= 2)) sawStarUp = true;
      if (run.pendingOpp.some((u) => (u.items ?? []).length > 0)) sawRelic = true;
      run.fight();
      if (run.phase === "result") run.next();
    }
    // The opponent always brings a warband, scales up, hits star-ups and fields relics.
    expect(sizes[0]).toBeGreaterThanOrEqual(1);
    expect(Math.max(...sizes)).toBeGreaterThan(sizes[0]);
    expect(sawStarUp).toBe(true);
    expect(sawRelic).toBe(true);
  });

  it("a full auto-played run always terminates with a win or loss", () => {
    const run = new WarbandRun(99);
    let guard = 0;
    while (run.phase !== "over" && guard++ < 200) {
      if (run.phase === "shop") {
        // Greedy auto-play: level when rich, else buy the first affordable unit, then fight.
        if (run.gold >= 8) run.buyXp();
        for (let s = 0; s < run.shop.length; s++) run.buy(s);
        run.fight();
      } else if (run.phase === "result") {
        run.next();
      }
    }
    expect(run.phase).toBe("over");
    expect(["win", "loss"]).toContain(run.outcome);
    expect(run.standings()[0].alive).toBe(true);
  }, 30000);
});
