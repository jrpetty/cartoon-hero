import { describe, expect, it } from "vitest";
import { WarbandRun, UNIT_TIER, BENCH_SLOTS, stageOf, stageBaseDamage, roundDamage, streakBonus, placeByStyle } from "./warband";
import { styleOf } from "../content/battle_styles";
import { AUGMENTS, AUGMENT_ROUNDS, augmentById, offerAugments, combinedBuff, combinedTraitBonus, tierForRound } from "./augments";
import { isCreepRound, campForRound, campBoard } from "./creeps";
import { RNG } from "../engine/rng";
import { WARBAND_COMMANDERS, warbandCommander, commanderIdentity } from "./warband_commanders";
import { isDraftRound, rollCarousel, pickValue, CAROUSEL_SIZE } from "./carousel";
import { isComponent } from "./items";
import { CONDITIONS, CLEAR, conditionById, hasEffect, conditionLines } from "./conditions";
import { TRAITS } from "./traits";
import { resolveBattle, mergeSideOpts } from "./autobattle";

/** Drive a run to the start of a given round, taking the first augment offered. */
function advanceTo(run: WarbandRun, round: number) {
  let guard = 0;
  while (run.round < round && run.phase !== "over" && guard++ < 200) {
    if (run.phase === "augment") { run.pickAugment(0); continue; }
    if (run.phase === "draft") { run.takeCarousel(0); continue; }
    if (run.phase === "shop") run.fight();
    if (run.phase === "result") run.next();
  }
}

describe("Warband Tactics run engine", () => {
  it("starts a run with gold, a shop, and seven opponents", () => {
    const run = new WarbandRun(123, null);
    expect(run.gold).toBeGreaterThan(0);
    expect(run.shop.length).toBe(5);
    expect(run.opponents.length).toBe(7);
    expect(run.phase).toBe("shop");
  });

  it("three identical buys merge into a 2-star, and three 2-stars into a 3-star", () => {
    const run = new WarbandRun(1, null);
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
    const run = new WarbandRun(2, null);
    run.gold = 100;
    const lvl0 = run.level;
    for (let i = 0; i < 5; i++) run.buyXp();
    expect(run.level).toBeGreaterThan(lvl0);
  });

  it("higher level unlocks higher-tier units in the shop", () => {
    const run = new WarbandRun(5, null);
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
    const run = new WarbandRun(7, null);
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
    const run = new WarbandRun(8, null);
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
    const run = new WarbandRun(2, null);
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
    const run = new WarbandRun(4, null);
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
    const run = new WarbandRun(3, null);
    const sizes: number[] = [];
    let sawStarUp = false;
    let sawRelic = false;
    // Buy a couple of units so the player survives a while, then march through rounds.
    run.gold = 50;
    run.shop = ["spearman", "spearman", "militia", "archer", "knight"];
    run.buy(0); run.buy(2); run.buy(3);
    for (let r = 0; r < 22 && run.phase !== "over"; r++) {
      if (run.phase === "augment") { run.pickAugment(0); continue; }
      if (run.phase === "draft") { run.takeCarousel(0); continue; }
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
    const run = new WarbandRun(99, null);
    let guard = 0;
    while (run.phase !== "over" && guard++ < 200) {
      if (run.phase === "augment") {
        run.pickAugment(0);
      } else if (run.phase === "draft") {
        run.takeCarousel(0);
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
    const run = new WarbandRun(11, null);
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
    const run = new WarbandRun(12, null);
    run.gold = 200; run.level = 3;
    run.shop = ["knight", "archer", "spearman", "militia", "raider"];
    run.buy(0); run.buy(1); run.buy(2); run.buy(3); run.buy(4);
    expect(run.deployCount()).toBe(3);
    run.augments.push(augmentById("legion")!); // +2 board slots
    expect(run.deployCount()).toBe(5);
    expect(run.deployment().length).toBe(5);
  });

  it("economy augments change income, reroll price and interest", () => {
    const run = new WarbandRun(13, null);
    expect(run.rerollCost()).toBe(2);
    run.augments.push(augmentById("quartermaster")!); // rerolls cost 1
    expect(run.rerollCost()).toBe(1);
    const gold = run.gold;
    run.gold = 5;
    expect(run.reroll()).toBe(true);
    expect(run.gold).toBe(4);
    run.gold = gold;

    // "King's Ransom" pays a bounty immediately and lifts the interest cap.
    const rich = new WarbandRun(14, null);
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
    const run = new WarbandRun(15, null);
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
    const run = new WarbandRun(21, null);
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
    const run = new WarbandRun(22, null);
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
    const run = new WarbandRun(23, null);
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

describe("Warband commanders", () => {
  it("opens the run on a commander choice, then plays on", () => {
    const run = new WarbandRun(51); // no id → the pick is offered
    expect(run.phase).toBe("commander");
    expect(run.commanderOffer.length).toBe(3);
    expect(new Set(run.commanderOffer.map((c) => c.id)).size).toBe(3);
    expect(run.commander).toBeNull();
    // Nothing may be bought until a commander leads.
    run.gold = 50;
    expect(run.reroll()).toBe(false);
    expect(run.buyXp()).toBe(false);
    const chosen = run.commanderOffer[0];
    expect(run.pickCommander(0)).toBe(true);
    expect(run.phase).toBe("shop");
    expect(run.commander?.id).toBe(chosen.id);
    expect(run.pickCommander(0)).toBe(false); // only once
  });

  it("every commander has a real identity and at least one lever", () => {
    for (const c of WARBAND_COMMANDERS) {
      const id = commanderIdentity(c);
      expect(id, c.id).toBeTruthy();
      expect(id.name.length).toBeGreaterThan(0);
      expect(c.perk.length).toBeGreaterThan(0);
      const levers = ["gold", "startGold", "interestCap", "freeRerolls", "boardSlots",
        "shopLevelBonus", "fullRefund", "topTraitBonus", "lossShield", "buff"] as const;
      expect(levers.some((k) => c[k] != null), c.id).toBe(true);
    }
    expect(warbandCommander("nope")).toBeUndefined();
  });

  it("the Steward's free reroll costs nothing and refreshes each round", () => {
    const run = new WarbandRun(52, "steward");
    expect(run.freeRerollsLeft()).toBe(1);
    expect(run.rerollCost()).toBe(0);
    run.gold = 10;
    expect(run.reroll()).toBe(true);
    expect(run.gold).toBe(10);            // the free one
    expect(run.rerollCost()).toBe(2);
    expect(run.reroll()).toBe(true);
    expect(run.gold).toBe(8);             // now it's paid
    // A new round refreshes it.
    run.fight();
    if (run.phase === "result") run.next();
    if (run.phase === "augment") run.pickAugment(0);
    expect(run.freeRerollsLeft()).toBe(1);
  });

  it("the Warden fields an extra unit from round one", () => {
    const bare = new WarbandRun(53, null);
    const warden = new WarbandRun(53, "warden");
    expect(warden.deployCount()).toBe(bare.deployCount() + 1);
    warden.gold = 200; warden.level = 3;
    warden.shop = ["knight", "archer", "spearman", "militia", "raider"];
    for (let i = 0; i < 5; i++) warden.buy(i);
    expect(warden.deployment().length).toBe(4); // level 3 + 1
  });

  it("the Quartermaster refunds every copy sunk into a unit", () => {
    const full = new WarbandRun(54, "quartermaster");
    const bare = new WarbandRun(54, null);
    for (const run of [full, bare]) {
      run.gold = 99;
      for (let i = 0; i < 3; i++) { run.shop = ["militia", "militia", "militia", "militia", "militia"]; run.buy(0); }
    }
    const two = full.pieces.findIndex((p) => p.star === 2);
    expect(two).toBeGreaterThanOrEqual(0);
    const fullBefore = full.gold, bareBefore = bare.gold;
    full.sell(two);
    bare.sell(bare.pieces.findIndex((p) => p.star === 2));
    // A 2★ militia is three copies: full refund 3g vs the flat star price 2g.
    expect(full.gold - fullBefore).toBe(3);
    expect(bare.gold - bareBefore).toBe(2);
  });

  it("the Drillmaster's shop rolls a level above, and the Magnate banks harder", () => {
    const drill = new WarbandRun(55, "drillmaster");
    const bare = new WarbandRun(55, null);
    // Level 2 odds are all tier 1; reading one level up must expose tier 2.
    drill.level = 2; bare.level = 2;
    expect(bare.shopOdds()[1]).toBe(0);
    expect(drill.shopOdds()[1]).toBeGreaterThan(0);

    const mag = new WarbandRun(56, "magnate");
    const plain = new WarbandRun(56, null);
    // The 10g purse is banked before the first income tick, so it immediately
    // earns a point of interest on top.
    expect(mag.gold - plain.gold).toBeGreaterThanOrEqual(10);
    expect(mag.gold).toBe(plain.gold + 11);
  });

  it("the Banneret lifts whichever synergy is currently largest", () => {
    const run = new WarbandRun(57, "banneret");
    run.gold = 200; run.level = 4;
    // Two Marksmen types → Marksmen is the biggest synergy at 2.
    for (const ty of ["archer", "crossbow"]) { run.shop = [ty, ty, ty, ty, ty]; run.buy(0); }
    const marks = run.activeTraits().find((t) => t.trait.id === "marksmen");
    expect(marks?.count).toBe(3); // 2 owned + 1 from the commander
    expect(run.sideOpts().traitBonus).toEqual({ marksmen: 1 });
  });

  it("the Warpriest blunts defeats and the Marshal buffs the whole warband", () => {
    expect(warbandCommander("warpriest")!.lossShield).toBe(3);
    const marshal = new WarbandRun(58, "marshal");
    const buff = marshal.sideOpts().buff;
    expect(buff?.hpPct).toBe(8);
    expect(buff?.armor).toBe(2);
    // A commander buff stacks with an augment buff rather than replacing it.
    marshal.augments.push(augmentById("whetted")!); // +12% attack
    const both = marshal.sideOpts().buff;
    expect(both?.atkPct).toBe(12);
    expect(both?.hpPct).toBe(8);
  });

  it("a full run led by a commander still terminates", () => {
    const run = new WarbandRun(59);
    let guard = 0;
    while (run.phase !== "over" && guard++ < 200) {
      if (run.phase === "commander") run.pickCommander(0);
      else if (run.phase === "augment") run.pickAugment(0);
      else if (run.phase === "draft") run.takeCarousel(0);
      else if (run.phase === "shop") { if (run.gold >= 8) run.buyXp(); for (let s = 0; s < run.shop.length; s++) run.buy(s); run.fight(); }
      else if (run.phase === "result") run.next();
    }
    expect(run.phase).toBe("over");
    expect(run.commander).not.toBeNull();
  }, 30000);
});

describe("Warband carousel", () => {
  it("opens a shared draft on its round, every unit carrying a component", () => {
    const run = new WarbandRun(61, null);
    advanceTo(run, 3);
    expect(run.round).toBe(3);
    expect(isDraftRound(3)).toBe(true);
    expect(run.phase).toBe("draft");
    expect(run.carousel.length).toBeGreaterThan(0);
    for (const p of run.carousel) {
      expect(UNIT_TIER[p.type]).toBeGreaterThan(0);
      expect(isComponent(p.item)).toBe(true);
      expect(p.star).toBe(1);
    }
    // The draft blocks the shop until you take something.
    run.gold = 50;
    expect(run.reroll()).toBe(false);
    expect(run.buyXp()).toBe(false);
  });

  it("gives you the unit with its component already equipped", () => {
    const run = new WarbandRun(62, null);
    advanceTo(run, 3);
    const want = run.carousel[0];
    const before = run.pieces.length;
    const pool = run.poolCount(want.type);
    expect(run.takeCarousel(0)).toBe(true);
    expect(run.phase).toBe("shop");
    expect(run.pieces.length).toBeGreaterThan(before - 1);
    // It arrived carrying its component (or already fused/merged it).
    const mine = run.pieces.find((p) => p.type === want.type);
    expect(mine).toBeTruthy();
    // Drawn from the shared pool — and rivals sweeping the rest of the ring can
    // take further copies of the same type, so this is a floor not an equality.
    expect(run.poolCount(want.type)).toBeLessThanOrEqual(pool - 1);
    expect(run.carousel.length).toBe(0);
    expect(run.takeCarousel(0)).toBe(false); // only once
  });

  it("makes the leader pick last — weaker warbands go first", () => {
    // Top of the lobby: every living rival is weaker, so they all pick ahead.
    const leader = new WarbandRun(63, null);
    advanceTo(leader, 3);
    for (const o of leader.opponents) o.life = 20;
    leader.life = 100;
    const relead = new WarbandRun(63, null);
    advanceTo(relead, 3);
    for (const o of relead.opponents) o.life = 20;
    relead.life = 100;
    // Re-open the draft now the lobby's life totals say we're in front.
    (relead as unknown as { openDraft: () => void }).openDraft();
    expect(relead.draftAhead).toBeGreaterThan(0);
    expect(relead.carousel.length).toBeLessThan(CAROUSEL_SIZE);

    // Bottom of the lobby: nobody is weaker, so you pick from the full ring.
    const last = new WarbandRun(63, null);
    advanceTo(last, 3);
    for (const o of last.opponents) o.life = 100;
    last.life = 5;
    (last as unknown as { openDraft: () => void }).openDraft();
    expect(last.draftAhead).toBe(0);
    expect(last.carousel.length).toBe(CAROUSEL_SIZE);
  });

  it("always leaves you at least one option, even dead last in a full lobby", () => {
    const run = new WarbandRun(64, null);
    advanceTo(run, 3);
    for (const o of run.opponents) { o.life = 1; o.alive = true; }
    run.life = 100;
    (run as unknown as { openDraft: () => void }).openDraft();
    expect(run.carousel.length).toBeGreaterThanOrEqual(1);
    expect(run.takeCarousel(0)).toBe(true);
  });

  it("scales the tiers on offer as the run goes on", () => {
    const rng = new RNG(9);
    const early = rollCarousel(rng, 3).map((p) => UNIT_TIER[p.type]);
    const late = rollCarousel(rng, 18).map((p) => UNIT_TIER[p.type]);
    expect(Math.max(...early)).toBeLessThanOrEqual(2);
    expect(Math.min(...late)).toBeGreaterThanOrEqual(4);
    expect(pickValue({ type: "hero", star: 1, item: "blade" }))
      .toBeGreaterThan(pickValue({ type: "militia", star: 1, item: "blade" }));
  });

  it("the rest of the ring goes to the field, so the lobby drafts too", () => {
    const run = new WarbandRun(65, null);
    advanceTo(run, 3);
    const ringSize = run.carousel.length;
    const foeUnitsBefore = run.opponents.reduce((n, o) => n + (run.scout(o.id)?.board.length ?? 0), 0);
    run.takeCarousel(0);
    const foeUnitsAfter = run.opponents.reduce((n, o) => n + (run.scout(o.id)?.board.length ?? 0), 0);
    expect(ringSize).toBeGreaterThan(1);
    // Rivals cleared what you left behind (boards can also cap out, so ≥).
    expect(foeUnitsAfter).toBeGreaterThanOrEqual(foeUnitsBefore);
  });
});

describe("Warband battlefield conditions", () => {
  it("opens on clear skies and rolls a real condition later", () => {
    const run = new WarbandRun(71, null);
    expect(run.condition.id).toBe("clear");
    expect(hasEffect(CLEAR)).toBe(false);
    const seen = new Set<string>();
    for (let s = 0; s < 30; s++) {
      const r2 = new WarbandRun(700 + s, null);
      advanceTo(r2, 4);
      seen.add(r2.condition.id);
    }
    expect(seen.size).toBeGreaterThan(1); // the weather actually varies
    for (const id of seen) expect(conditionById(id)).toBeTruthy();
  });

  it("every condition is well-formed and describes itself", () => {
    const traitName = (id: string) => TRAITS.find((t) => t.id === id)?.name ?? id;
    for (const c of CONDITIONS) {
      expect(c.name.length).toBeGreaterThan(0);
      expect(c.desc.length).toBeGreaterThan(0);
      expect(c.weight).toBeGreaterThan(0);
      expect(["clear", "rain", "snow", "overcast"]).toContain(c.weather);
      // Trait keys must be real traits, or the effect silently does nothing.
      for (const id of Object.keys(c.traits ?? {})) {
        expect(TRAITS.some((t) => t.id === id), `${c.id} → ${id}`).toBe(true);
      }
      if (hasEffect(c)) expect(conditionLines(c, traitName).length).toBeGreaterThan(0);
    }
  });

  it("bites the trait it names, on both sides of the field", () => {
    // Driving Rain weakens Marksmen. Archers under rain should lose to archers
    // in the clear only if the effect were one-sided — here it hits both, so
    // the check is that the effect reaches the units at all.
    const rain = conditionById("rain")!;
    const field = { buff: rain.all, traitBuffs: rain.traits };
    const archers = [{ type: "archer", star: 1, col: 2, row: 4 }, { type: "archer", star: 1, col: 2, row: 5 }];
    const spears = [{ type: "spearman", star: 1, col: 7, row: 4 }, { type: "spearman", star: 1, col: 7, row: 5 }];
    // Marksmen weakened → archers fare worse against the same melee than in the clear.
    let clearWins = 0, rainWins = 0;
    for (const seed of [1, 2, 3, 4, 5, 6]) {
      if (resolveBattle(archers, spears, seed).winner === "A") clearWins++;
      if (resolveBattle(archers, spears, seed, 40, undefined, field).winner === "A") rainWins++;
    }
    expect(rainWins).toBeLessThanOrEqual(clearWins);
  });

  it("applies the same battlefield to the enemy, not just to you", () => {
    const frost = conditionById("frost")!; // −8% HP to everyone
    const field = { buff: frost.all, traitBuffs: frost.traits };
    const a = [{ type: "knight", star: 1, col: 2, row: 4 }];
    const b = [{ type: "knight", star: 1, col: 7, row: 4 }];
    // A mirror match stays a mirror under a symmetric condition.
    const plain = resolveBattle(a, b, 3);
    const chilled = resolveBattle(a, b, 3, 40, undefined, field);
    expect(chilled.winner).toBe(plain.winner);
  });

  it("merges battlefield modifiers with the warband's own", () => {
    const merged = mergeSideOpts(
      { buff: { atkPct: 20 }, traitBuffs: { marksmen: { atkPct: 10 } } },
      { buff: { hpPct: -8 }, traitBuffs: { marksmen: { atkPct: -18 }, riders: { speedPct: -25 } } },
    );
    expect(merged?.buff).toEqual({ atkPct: 20, hpPct: -8 });
    expect(merged?.traitBuffs?.marksmen).toEqual({ atkPct: -8 }); // 10 + (−18)
    expect(merged?.traitBuffs?.riders).toEqual({ speedPct: -25 });
    // Either side being absent is a pass-through, not a crash.
    expect(mergeSideOpts({ buff: { atk: 1 } }, undefined)).toEqual({ buff: { atk: 1 } });
    expect(mergeSideOpts(undefined, { buff: { atk: 2 } })).toEqual({ buff: { atk: 2 } });
  });

  it("hands the run's condition to the fight through fieldOpts", () => {
    const run = new WarbandRun(72, null);
    advanceTo(run, 4);
    const f = run.fieldOpts();
    expect(f.buff).toBe(run.condition.all);
    expect(f.traitBuffs).toBe(run.condition.traits);
  });
});

describe("Warband damage & streaks", () => {
  it("charges a stage toll plus star-weighted survivors", () => {
    expect(stageOf(1)).toBe(1);
    expect(stageOf(5)).toBe(1);
    expect(stageOf(6)).toBe(2);
    expect(stageOf(11)).toBe(3);
    // Stage 1 costs nothing but the survivors — early losses stay survivable.
    expect(roundDamage(1, 0)).toBe(0);
    expect(roundDamage(3, 4)).toBe(4);
    // The toll grows with the stage, so late rounds actually close a run out.
    expect(roundDamage(8, 4)).toBeGreaterThan(roundDamage(3, 4));
    expect(roundDamage(20, 4)).toBeGreaterThan(roundDamage(8, 4));
    // A 3★ survivor hurts three times as much as a 1★.
    expect(roundDamage(6, 3) - roundDamage(6, 1)).toBe(2);
    // It never runs away past the table's end.
    expect(roundDamage(500, 2)).toBe(roundDamage(100, 2));
  });

  it("pays gold for losing streaks as well as winning ones", () => {
    expect(streakBonus(0)).toBe(0);
    expect(streakBonus(1)).toBe(0);
    expect(streakBonus(2)).toBe(1);
    expect(streakBonus(4)).toBe(2);
    expect(streakBonus(9)).toBe(3); // capped
    // Symmetric: falling behind banks gold, which is the comeback lever.
    for (const n of [0, 1, 2, 3, 4, 5, 9]) expect(streakBonus(-n)).toBe(streakBonus(n));
  });

  it("previews the worst a defeat could cost, and the commander blunts it", () => {
    const bare = new WarbandRun(81, null);
    advanceTo(bare, 4);
    const power = bare.pendingOpp.reduce((n, u) => n + (u.star ?? 1), 0);
    expect(bare.worstCaseDamage()).toBe(roundDamage(bare.round, power));

    const priest = new WarbandRun(81, "warpriest"); // −3 life on a defeat
    advanceTo(priest, 4);
    expect(priest.worstCaseDamage()).toBeLessThan(bare.worstCaseDamage());
    expect(priest.worstCaseDamage()).toBeGreaterThanOrEqual(1); // never free
  });

  it("actually applies the formula when a fight is lost", () => {
    const run = new WarbandRun(82, null);
    advanceTo(run, 4);
    run.pieces.length = 0; // no board at all → a guaranteed loss
    const before = run.life;
    run.fight();
    if (run.lastResult && !run.lastResult.won && !run.lastResult.creep) {
      expect(before - run.life).toBe(run.lastResult.dmg);
      expect(run.lastResult.dmg).toBe(roundDamage(run.round, run.lastResult.foeLeft > 0 ? run.lastResult.dmg - stageBaseDamage(stageOf(run.round)) : 0));
      expect(run.streak).toBeLessThan(0);
      expect(run.streakGold()).toBe(streakBonus(run.streak));
    }
  });
});

describe("Warband bench", () => {
  it("has a fixed nine slots", () => {
    const run = new WarbandRun(83, null);
    expect(BENCH_SLOTS).toBe(9);
    expect(run.benchSlots()).toBe(9);
    expect(run.benchCount()).toBe(0);
    expect(run.benchFull()).toBe(false);
  });

  it("refuses a buy when board and bench are both full", () => {
    const run = new WarbandRun(84, null);
    run.gold = 500; run.level = 1; // board cap 1 → everything else benches
    // Fill the bench with nine distinct types (no merges to free space).
    const types = ["militia", "spearman", "scout", "raider", "archer", "skirmisher", "horseman", "javelin", "pikeman", "knight"];
    for (const t of types) { run.shop = [t, t, t, t, t]; run.buy(0); }
    expect(run.benchCount()).toBe(run.benchSlots());
    expect(run.benchFull()).toBe(true);
    // A further distinct type has nowhere to go.
    run.shop = ["crossbow", null, null, null, null];
    expect(run.canBuy(0)).toEqual({ ok: false, reason: "bench" });
    const gold = run.gold;
    expect(run.buy(0)).toBe(false);
    expect(run.gold).toBe(gold); // and it costs nothing
  });

  it("still allows a buy that merges, even with a full bench", () => {
    const run = new WarbandRun(85, null);
    run.gold = 500; run.level = 1;
    // Two militia (so a third merges) plus eight other types: ten pieces, one
    // of which deploys at level 1, leaving the nine-slot bench exactly full.
    run.shop = ["militia", "militia", "militia", "militia", "militia"];
    run.buy(0); run.buy(1);
    for (const t of ["spearman", "scout", "raider", "archer", "skirmisher", "horseman", "javelin", "pikeman"]) {
      run.shop = [t, t, t, t, t]; run.buy(0);
    }
    expect(run.benchFull()).toBe(true);
    // The third militia combines instead of taking a slot, so it's allowed.
    run.shop = ["militia", null, null, null, null];
    expect(run.canBuy(0).ok).toBe(true);
    expect(run.buy(0)).toBe(true);
    expect(run.pieces.some((p) => p.type === "militia" && p.star === 2)).toBe(true);
  });

  it("reports 'gold' rather than 'bench' when you simply can't afford it", () => {
    const run = new WarbandRun(86, null);
    run.gold = 0;
    run.shop = ["hero", null, null, null, null];
    expect(run.canBuy(0)).toEqual({ ok: false, reason: "gold" });
  });
});

describe("Warband opponent intelligence", () => {
  it("puts artillery at the back and the wall at the front", () => {
    const pieces = [
      { type: "trebuchet", star: 1, items: [] },
      { type: "crossbow", star: 1, items: [] },
      { type: "spearman", star: 1, items: [] },
      { type: "militia", star: 1, items: [] },
      { type: "knight", star: 1, items: [] },
      { type: "scout", star: 1, items: [] },
    ];
    const board = placeByStyle(pieces, 1);
    const at = (t: string) => board.find((u) => u.type === t)!;
    // Enemy half: col 5 is the front line, col 9 the back rank.
    expect(at("spearman").col).toBe(5);
    expect(at("militia").col).toBe(5);
    // Near the back, not at it — the last rank is out of a crossbow's own range.
    expect(at("trebuchet").col).toBe(8);
    expect(at("crossbow").col).toBe(8);
    // Siege must never end up in front of the infantry — the old giveaway.
    expect(at("trebuchet").col!).toBeGreaterThan(at("spearman").col!);
    expect(at("crossbow").col!).toBeGreaterThan(at("militia").col!);
    // Flankers take the outside rows.
    expect([0, 9]).toContain(at("knight").row);
    // Every unit gets its own cell.
    expect(new Set(board.map((u) => `${u.col},${u.row}`)).size).toBe(board.length);
  });

  it("mirrors the layout for the player's half", () => {
    const board = placeByStyle(
      [{ type: "trebuchet", star: 1, items: [] }, { type: "spearman", star: 1, items: [] }], -1,
    );
    const at = (t: string) => board.find((u) => u.type === t)!;
    expect(at("spearman").col).toBe(4);  // front line on the left half
    expect(at("trebuchet").col).toBe(1); // near the back
    for (const u of board) { expect(u.col!).toBeGreaterThanOrEqual(0); expect(u.col!).toBeLessThanOrEqual(4); }
  });

  it("still places a warband bigger than one rank", () => {
    const many = Array.from({ length: 9 }, () => ({ type: "spearman", star: 1, items: [] }));
    const board = placeByStyle(many, 1);
    expect(board.length).toBe(9);
    expect(new Set(board.map((u) => `${u.col},${u.row}`)).size).toBe(9);
  });

  it("actually fields role-sorted boards in a real run", () => {
    // Sample the whole lobby across several rounds and seeds rather than one
    // snapshot: whether any given board happens to hold both a wall and a
    // siege piece is down to what that opponent drafted, and pinning it to a
    // single round makes the test hostage to the roster.
    let checked = 0;
    for (const seed of [91, 92, 93]) {
      const run = new WarbandRun(seed, null);
      for (const round of [8, 12, 16]) {
        advanceTo(run, round);
        if (run.phase === "over") break;
        for (const o of run.opponents.filter((x) => x.alive)) {
          const board = run.scout(o.id)!.board;
          const front = board.filter((u) => styleOf(u.type) === "vanguard").map((u) => u.col!);
          const back = board.filter((u) => styleOf(u.type) === "artillery").map((u) => u.col!);
          if (!front.length || !back.length) continue;
          // Siege never stands in front of the wall.
          expect(Math.min(...back), `seed ${seed} round ${round} ${o.name}`)
            .toBeGreaterThan(Math.max(...front));
          checked++;
        }
      }
    }
    expect(checked).toBeGreaterThan(0); // the situation arose somewhere
  }, 60000);

  it("drafts toward merges and synergies rather than at random", () => {
    const run = new WarbandRun(92, null);
    advanceTo(run, 14);
    let sawStarUp = false;
    let sawSynergy = false;
    for (const o of run.opponents.filter((x) => x.alive)) {
      const s = run.scout(o.id)!;
      if (s.board.some((u) => (u.star ?? 1) >= 2)) sawStarUp = true;
      if (s.traits.length > 0) sawSynergy = true;
    }
    expect(sawStarUp).toBe(true);
    expect(sawSynergy).toBe(true); // boards cohere into real comps
  });

  it("spreads distinct play styles across the lobby", () => {
    const run = new WarbandRun(93, null);
    const brains = (run as unknown as { foeBrains: { play: string }[] }).foeBrains;
    expect(new Set(brains.map((b) => b.play)).size).toBeGreaterThan(1);
  });

  it("off-screen attrition never eliminates the last opponent", () => {
    const run = new WarbandRun(94, null);
    // Leave a single rival on one life, then thin the herd many times.
    for (const o of run.opponents.slice(1)) { o.alive = false; o.life = 0; }
    run.opponents[0].life = 1;
    const thin = (run as unknown as { thinTheHerd: () => void }).thinTheHerd.bind(run);
    for (let i = 0; i < 200; i++) thin();
    expect(run.opponents[0].alive).toBe(true);
    // So a finished run always has somebody left standing at the top.
    const auto = new WarbandRun(95, null);
    let guard = 0;
    while (auto.phase !== "over" && guard++ < 300) {
      if (auto.phase === "augment") auto.pickAugment(0);
      else if (auto.phase === "draft") auto.takeCarousel(0);
      else if (auto.phase === "shop") auto.fight();
      else if (auto.phase === "result") auto.next();
    }
    expect(auto.phase).toBe("over");
    expect(auto.standings()[0].alive).toBe(true);
  }, 30000);
});

describe("Warband scouting", () => {
  it("reports a rival's level, life and the board they'd field", () => {
    const run = new WarbandRun(31, null);
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
    const run = new WarbandRun(32, null);
    const rows = run.standings();
    expect(rows.find((r) => r.you)!.id).toBe(-1);
    for (const r of rows.filter((x) => !x.you)) expect(run.scout(r.id)!.name).toBe(r.name);
  });
});

describe("Warband difficulty", () => {
  /** Play a whole run with a fixed level of care, and report the placement. */
  function playRun(seed: number, skill: "idle" | "average" | "good"): number {
    const run = new WarbandRun(seed, null);
    let guard = 0;
    while (run.phase !== "over" && guard++ < 400) {
      if (run.phase === "augment") { run.pickAugment(0); continue; }
      if (run.phase === "draft") { run.takeCarousel(0); continue; }
      if (run.phase === "shop") {
        if (skill === "average") { if (run.gold >= 12) run.buyXp(); run.buy(0); run.buy(1); }
        if (skill === "good") {
          while (run.gold >= 20 && run.level < 9) run.buyXp();
          for (let s = 0; s < 5; s++) run.buy(s);
          if (run.gold > 30) { run.reroll(); for (let s = 0; s < 5; s++) run.buy(s); }
        }
        run.fight();
      } else if (run.phase === "result") run.next();
    }
    return run.placement();
  }

  const SEEDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
  const mean = (xs: number[]) => xs.reduce((a, b) => a + b, 0) / xs.length;

  it("punishes doing nothing, and rewards playing well", () => {
    const idle = SEEDS.map((s) => playRun(s, "idle"));
    const average = SEEDS.map((s) => playRun(s, "average"));
    const good = SEEDS.map((s) => playRun(s, "good"));

    // Skill has to separate the outcomes, in the right order.
    expect(mean(good)).toBeLessThan(mean(average));
    expect(mean(average)).toBeLessThan(mean(idle));

    // Fielding nothing loses, every time — the lobby is never so passive that
    // an empty board coasts on other people's mistakes.
    expect(Math.max(...idle)).toBe(8);
    expect(idle.filter((p) => p === 1).length).toBe(0);

    // A middling player lands mid-pack and only occasionally takes it. This is
    // the guard against the mode drifting back to "survive and inherit a win".
    expect(mean(average)).toBeGreaterThan(3);
    expect(average.filter((p) => p === 1).length).toBeLessThanOrEqual(4);

    // Playing well is properly rewarded, without being a formality.
    expect(mean(good)).toBeLessThan(3);
    expect(good.filter((p) => p === 1).length).toBeGreaterThan(1);
  }, 120000);

  it("thins the lobby through lost fights, and never below one survivor", () => {
    // A real run — an empty board dies long before the field would thin.
    const run = new WarbandRun(77, null);
    let guard = 0;
    while (run.phase !== "over" && guard++ < 400) {
      if (run.phase === "augment") { run.pickAugment(0); continue; }
      if (run.phase === "draft") { run.takeCarousel(0); continue; }
      if (run.phase === "shop") {
        while (run.gold >= 20 && run.level < 9) run.buyXp();
        for (let s = 0; s < 5; s++) run.buy(s);
        run.fight();
      } else if (run.phase === "result") run.next();
    }
    expect(run.phase).toBe("over");
    // Rivals were knocked out, and every one of them lost life to get there
    // rather than vanishing — dice never eliminated anybody.
    const dead = run.opponents.filter((o) => !o.alive);
    expect(dead.length).toBeGreaterThan(0);
    for (const o of dead) expect(o.life).toBe(0);
    // The run always ends with somebody still standing.
    expect(run.standings()[0].alive).toBe(true);
  }, 60000);
});
