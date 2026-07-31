import { describe, expect, it } from "vitest";
import { WarbandRun, UNIT_TIER } from "./warband";
import { AUGMENTS, AUGMENT_ROUNDS, augmentById, offerAugments, combinedBuff, combinedTraitBonus, tierForRound } from "./augments";
import { isCreepRound, campForRound, campBoard } from "./creeps";
import { RNG } from "../engine/rng";

/** Drive a run to the start of a given round, taking the first augment offered. */
function advanceTo(run: WarbandRun, round: number) {
  let guard = 0;
  while (run.round < round && run.phase !== "over" && guard++ < 200) {
    if (run.phase === "augment") { run.pickAugment(0); continue; }
    if (run.phase === "shop") run.fight();
    if (run.phase === "result") run.next();
  }
}

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

  it("shares a finite unit pool — buying depletes it, selling refunds copies", () => {
    const run = new WarbandRun(2);
    run.gold = 99;
    const before = run.poolCount("militia");
    run.shop = ["militia", "militia", "militia", "militia", "militia"];
    run.buy(0); run.buy(1);
    expect(run.poolCount("militia")).toBe(before - 2); // two copies taken
    // Sell a 1-star → one copy returns to the pool.
    const idx = run.pieces.findIndex((p) => p.type === "militia" && p.star === 1);
    run.sell(idx);
    expect(run.poolCount("militia")).toBe(before - 1);
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
    for (let r = 0; r < 22 && run.phase !== "over"; r++) {
      if (run.phase === "augment") { run.pickAugment(0); continue; }
      // Only sample real player rounds — PvE camp boards are scripted, not drafted.
      if (!run.isCreepRound()) {
        sizes.push(run.pendingOpp.length); // the upcoming opponent's deployed board
        if (run.pendingOpp.some((u) => (u.star ?? 1) >= 2)) sawStarUp = true;
        if (run.pendingOpp.some((u) => (u.items ?? []).length > 0)) sawRelic = true;
      }
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
      if (run.phase === "augment") {
        run.pickAugment(0);
      } else if (run.phase === "shop") {
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

describe("Warband augments", () => {
  it("offers three distinct augments on an augment round and pauses for the pick", () => {
    const run = new WarbandRun(11);
    advanceTo(run, AUGMENT_ROUNDS[0]);
    expect(run.round).toBe(AUGMENT_ROUNDS[0]);
    expect(run.phase).toBe("augment");
    expect(run.augmentOffer.length).toBe(3);
    expect(new Set(run.augmentOffer.map((a) => a.id)).size).toBe(3);
    // Nothing may be bought while the pick is pending.
    run.gold = 50;
    expect(run.reroll()).toBe(false);
    expect(run.buyXp()).toBe(false);
    // Taking one resumes the shop and banks it for the run.
    const chosen = run.augmentOffer[0];
    expect(run.pickAugment(0)).toBe(true);
    expect(run.phase).toBe("shop");
    expect(run.augments.map((a) => a.id)).toEqual([chosen.id]);
    expect(run.augmentOffer.length).toBe(0);
  });

  it("never offers an augment you already own", () => {
    const rng = new RNG(4);
    const owned = AUGMENTS.filter((a) => a.tier === "silver").slice(0, 2).map((a) => a.id);
    for (let i = 0; i < 40; i++) {
      const offer = offerAugments(rng, "silver", owned);
      expect(offer.length).toBe(3);
      for (const a of offer) expect(owned).not.toContain(a.id);
    }
  });

  it("board-slot augments field more units than your level alone", () => {
    const run = new WarbandRun(12);
    run.gold = 200; run.level = 3;
    run.shop = ["knight", "archer", "spearman", "militia", "raider"];
    run.buy(0); run.buy(1); run.buy(2); run.buy(3); run.buy(4);
    expect(run.deployCount()).toBe(3);
    run.augments.push(augmentById("legion")!); // +2 board slots
    expect(run.deployCount()).toBe(5);
    expect(run.deployment().length).toBe(5);
  });

  it("economy augments change income, reroll price and interest", () => {
    const run = new WarbandRun(13);
    expect(run.rerollCost()).toBe(2);
    run.augments.push(augmentById("quartermaster")!); // rerolls cost 1
    expect(run.rerollCost()).toBe(1);
    const gold = run.gold;
    run.gold = 5;
    expect(run.reroll()).toBe(true);
    expect(run.gold).toBe(4);
    run.gold = gold;

    // "King's Ransom" pays a bounty immediately and lifts the interest cap.
    const rich = new WarbandRun(14);
    advanceTo(rich, AUGMENT_ROUNDS[0]);
    rich.augmentOffer = [augmentById("kingsransom")!];
    const before = rich.gold;
    rich.pickAugment(0);
    expect(rich.gold).toBe(before + 12);
    // 100 gold banked → interest 8 (capped at 8, not the usual 5) + 5 base + 5 augment.
    rich.gold = 100;
    const at = rich.round;
    rich.fight();
    if (rich.phase === "result") rich.next();
    if (rich.round > at) expect(rich.gold).toBeGreaterThanOrEqual(100 + 5 + 8);
  });

  it("banner augments add virtual synergy counts, and buffs stack into one", () => {
    const run = new WarbandRun(15);
    run.gold = 200; run.level = 2;
    run.shop = ["militia", "militia", "militia", "militia", "militia"];
    run.buy(0);
    // One Footman type alone is below the 2-unit breakpoint.
    expect(run.activeTraits().find((t) => t.trait.id === "footmen")).toBeUndefined();
    run.augments.push(augmentById("banner_footmen")!); // counts +2
    const foot = run.activeTraits().find((t) => t.trait.id === "footmen");
    expect(foot).toBeDefined();
    expect(foot!.count).toBe(3);

    // Two stat augments merge into a single combined buff for the fight.
    const buff = combinedBuff([augmentById("whetted")!, augmentById("blademaster")!]);
    expect(buff?.atkPct).toBe(12 + 26);
    expect(combinedTraitBonus([augmentById("banner_riders")!])).toEqual({ riders: 2 });
    expect(run.sideOpts().traitBonus).toEqual({ footmen: 2 });
  });

  it("schedules augments at fixed rounds with escalating tiers", () => {
    expect(tierForRound(AUGMENT_ROUNDS[0])).toBe("silver");
    expect(tierForRound(AUGMENT_ROUNDS[1])).toBe("gold");
    expect(tierForRound(AUGMENT_ROUNDS[2])).toBe("prismatic");
    expect(tierForRound(3)).toBeNull();
  });
});

describe("Warband monster camps", () => {
  it("opens the run against a camp instead of a player", () => {
    const run = new WarbandRun(21);
    expect(run.round).toBe(1);
    expect(run.isCreepRound()).toBe(true);
    expect(run.pendingCamp).not.toBeNull();
    expect(run.pendingFoeName()).toBe(campForRound(1).name);
    expect(run.pendingOpp.length).toBeGreaterThan(0);
    // The camp stands on the enemy half.
    for (const u of run.pendingOpp) expect(u.col!).toBeGreaterThanOrEqual(5);
  });

  it("camp rounds land every fifth round and cost no player life to lose", () => {
    expect(isCreepRound(1)).toBe(true);
    expect(isCreepRound(6)).toBe(true);
    expect(isCreepRound(2)).toBe(false);
    // Losing the opener with an empty board only takes the camp's small bite.
    const run = new WarbandRun(22);
    const camp = run.pendingCamp!;
    const lifeBefore = run.life;
    run.fight();
    expect(run.lastResult?.creep).toBe(true);
    if (!run.lastResult!.won) {
      expect(lifeBefore - run.life).toBe(camp.bite);
      expect(camp.bite).toBeLessThan(10);
    }
    // No opponent was eliminated by a camp round on our behalf.
    expect(run.opponents.length).toBe(7);
  });

  it("beating a camp drops relics and gold", () => {
    const run = new WarbandRun(23);
    run.gold = 200; run.level = 9;
    // A crushing board so the opening camp definitely dies.
    run.shop = ["hero", "hero", "hero", "hero", "hero"];
    for (let i = 0; i < 5; i++) { run.shop = ["hero", "hero", "hero", "hero", "hero"]; run.buy(0); }
    const camp = run.pendingCamp!;
    const relicsBefore = run.itemStash.length;
    const goldBefore = run.gold;
    run.fight();
    expect(run.lastResult?.creep).toBe(true);
    expect(run.lastResult?.won).toBe(true);
    expect(run.itemStash.length).toBe(relicsBefore + camp.relics);
    expect(run.gold).toBe(goldBefore + camp.gold);
    expect(run.lastResult?.relics).toBe(camp.relics);
  });

  it("scales camps past the scripted list instead of running out", () => {
    const late = campForRound(101);
    expect(late.units.length).toBeGreaterThan(0);
    expect(campBoard(late).length).toBeGreaterThan(0);
    expect(late.gold).toBeGreaterThan(campForRound(1).gold);
  });
});

describe("Warband scouting", () => {
  it("reports a rival's level, life and the board they'd field", () => {
    const run = new WarbandRun(31);
    advanceTo(run, 4); // let the lobby build boards
    const s = run.scout(0);
    expect(s).not.toBeNull();
    expect(s!.name).toBe(run.opponents[0].name);
    expect(s!.life).toBe(run.opponents[0].life);
    expect(s!.level).toBeGreaterThanOrEqual(1);
    expect(s!.board.length).toBeGreaterThan(0);
    // Scouted boards sit on the enemy half and carry readable synergies.
    for (const u of s!.board) expect(u.col!).toBeGreaterThanOrEqual(5);
    expect(Array.isArray(s!.traits)).toBe(true);
    expect(run.scout(99)).toBeNull();
  });

  it("standings carry the opponent id the scout panel needs", () => {
    const run = new WarbandRun(32);
    const rows = run.standings();
    expect(rows.find((r) => r.you)!.id).toBe(-1);
    for (const r of rows.filter((x) => !x.you)) expect(run.scout(r.id)!.name).toBe(r.name);
  });
});
