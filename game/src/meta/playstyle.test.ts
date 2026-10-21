import { describe, expect, it } from "vitest";
import type { CareerMatch } from "./career";
import { analyse, matchStyles, features, ARCHETYPES, MIN_GAMES } from "./playstyle";

/**
 * Playstyle: players who play a certain way are recognised as playing it —
 * a Man-at-Arms rusher, a turtle, a boomer, a demolisher, a raider, a tech
 * master, a brawler — from the numbers alone, and the reasons are shown.
 */

let t = 1_700_000_000_000;
const FOE = { gathered: 12000, razed: 3, kills: 40, losses: 40, peakArmy: 30, peakVillagers: 38, villagerKills: 4, upgrades: 6, defenses: 2, age: 2 };
function m(over: Partial<CareerMatch> = {}): CareerMatch {
  return {
    at: (t += 60_000), kind: "skirmish", ranked: false, mode: "conquest", difficulty: "knight", won: true, faction: "legion", commander: "", oaths: [],
    map: "Highlands", players: 2, format: "1 v 1", allies: [], foes: ["norse"], durationSec: 1500, defeatedAt: -1, age: 2, ageTimes: [0, 400, 1000],
    score: 5000, gathered: 12000, gatheredBy: { food: 5000, wood: 5000, gold: 2000 }, spent: 11000, kills: 40, losses: 40, razed: 3, buildingsLost: 3,
    damageDealt: 9000, damageTaken: 9000, peakArmy: 30, peakVillagers: 38, idleVillagerTime: 60, tcIdleShare: 0.1, upgrades: 6,
    trained: { villager: 40, spearman: 10, archer: 10, horseman: 10, militia: 10 }, killed: { villager: 4 }, lost: {}, built: { house: 8 },
    firstHitAt: 600, firstKillAt: 620, firstRazeAt: 900, spentOn: { units: 6000, buildings: 3000, tech: 2000 }, foe: { ...FOE }, ...over,
  };
}
const many = (n: number, over: Partial<CareerMatch>, lossEvery = 4) => Array.from({ length: n }, (_, i) => m({ ...over, won: i % lossEvery !== lossEvery - 1 }));

describe("Recognising a style", () => {
  it("a Man-at-Arms rusher", () => {
    const p = analyse(many(12, { firstHitAt: 290, durationSec: 700, trained: { villager: 22, militia: 26, archer: 3 } }));
    expect(p.primary?.archetype.id).toBe("rush");
    expect(p.title).toBe("Man-at-Arms Rush");
    expect(p.description).toMatch(/before 7:00/);
  });

  it("a turtle who plays for the late game", () => {
    const p = analyse(many(12, { firstHitAt: 1300, durationSec: 2700, built: { palisade: 40, watch_tower: 5, castle: 1, gate: 2 }, age: 3 }));
    expect(p.primary?.archetype.id).toMatch(/turtle|late/);
    expect(p.title).toMatch(/Turtle/);
  });

  it("a boomer", () => {
    const p = analyse(many(12, { firstHitAt: 900, peakVillagers: 62, gathered: 21000, durationSec: 1600 }));
    expect(p.primary?.archetype.id).toBe("boom");
  });

  it("a demolisher who wins long — longevity & demolition", () => {
    const p = analyse(many(12, { razed: 13, durationSec: 2500, firstHitAt: 800, trained: { villager: 40, ram: 8, knight: 15 } }));
    expect(p.scores.find((s) => s.archetype.id === "demolition")!.score).toBeGreaterThan(0.6);
    expect(p.title).toMatch(/Siege Grinder/);
  });

  it("a raider", () => {
    const p = analyse(many(12, { killed: { villager: 22 }, trained: { villager: 30, horseman: 25, horse_archer: 10 }, firstHitAt: 520 }));
    expect(p.primary?.archetype.id).toBe("raid");
  });

  it("a fast-aging tech master", () => {
    const p = analyse(many(12, { upgrades: 15, ageTimes: [0, 330, 690], firstHitAt: 900 }));
    expect(p.primary?.archetype.id).toBe("tech");
    expect(p.title).toBe("Fast Crown");
  });

  it("an efficient brawler", () => {
    const p = analyse(many(12, { kills: 150, losses: 70, durationSec: 1500, firstHitAt: 500 }));
    expect(p.primary?.archetype.id).toBe("brawler");
  });

  it("nobody in particular — an all-rounder", () => {
    const p = analyse(many(12, {}));
    expect(p.primary).toBeNull();
    expect(p.title).toBe("All-Rounder");
  });

  it("waits for enough games", () => {
    const p = analyse(many(MIN_GAMES - 1, { firstHitAt: 200, durationSec: 600 }));
    expect(p.enough).toBe(false);
    expect(p.confidence).toBe("none");
  });
});

describe("One match's style", () => {
  it("follows the written rules", () => {
    expect(matchStyles(m({ firstHitAt: 300 }))).toContain("rush");
    expect(matchStyles(m({ killed: { villager: 12 } }))).toContain("raid");
    expect(matchStyles(m({ razed: 9 }))).toContain("demolition");
    expect(matchStyles(m({ built: { watch_tower: 3, palisade: 12 }, firstHitAt: 800 }))).toContain("turtle");
    expect(matchStyles(m({ peakVillagers: 50, firstHitAt: 800 }))).toContain("boom");
    expect(matchStyles(m({ ageTimes: [0, 300, 800] }))).toContain("fastage");
    expect(matchStyles(m({ durationSec: 2400 }))).toContain("lategame");
    expect(matchStyles(m())).toEqual(["standard"]);
  });
});

describe("Traits and the reasons behind them", () => {
  it("names the army, the faction and the trades", () => {
    const p = analyse(many(12, { faction: "khanate", trained: { villager: 30, horseman: 30, horse_archer: 20, cataphract: 10 }, kills: 120, losses: 50 }));
    const names = p.traits.map((x) => x.name);
    expect(names).toContain("Cavalry Commander");
    expect(names).toContain("Khanate Specialist");
    expect(names).toContain("Efficient Trader");
  });

  it("finds what's different about the games you win", () => {
    const games = [...Array.from({ length: 6 }, () => m({ won: true, peakVillagers: 55 })), ...Array.from({ length: 6 }, () => m({ won: false, peakVillagers: 30 }))];
    const p = analyse(games);
    expect(p.winKeys[0].label).toBe("Peak villagers");
    expect(p.winKeys[0].sentence).toMatch(/more villagers/);
  });

  it("gives every archetype evidence it can show", () => {
    const f = features(many(10, {}));
    for (const a of ARCHETYPES) {
      const e = a.evidence(f);
      expect(e.length, a.id).toBeGreaterThanOrEqual(2);
      for (const x of e) expect(x.weight).toBeGreaterThanOrEqual(0);
      expect(a.describe(f).length).toBeGreaterThan(60);
    }
  });

  it("copes with old records that lack the newer fields", () => {
    const old = many(8, { firstHitAt: undefined, foe: undefined, spentOn: undefined });
    expect(() => analyse(old)).not.toThrow();
  });
});

describe("Style built from keywords", () => {
  it("a rusher who opens with archers is an Archer Rush, not Man-at-Arms", () => {
    const p = analyse(many(12, { firstAttackAt: 300, firstHitAt: 300, durationSec: 700, opener: { archer: 900, militia: 80 }, trained: { villager: 22, militia: 20, archer: 12 } }));
    expect(p.primary?.archetype.id).toBe("rush");
    expect(p.title).toBe("Archer Rush");
    expect(p.keywords).toContain("Early attacker");
    expect(p.keywords).toContain("Archer opener");
  });

  it("a mixed opener says so", () => {
    const p = analyse(many(12, { firstAttackAt: 300, durationSec: 700, opener: { archer: 300, militia: 300, horseman: 300 } }));
    expect(p.title).toBe("Mixed Rush");
  });

  it("attacks early but wins long: early pressure, late finish", () => {
    const p = analyse(many(12, { firstAttackAt: 330, durationSec: 2600, opener: { horseman: 700 }, age: 3 }));
    expect(p.primary?.archetype.id).toBe("pressure");
    expect(p.title).toMatch(/Late Closer|Late Finisher/);
    expect(p.keywords).toEqual(expect.arrayContaining(["Early attacker", "Long games"]));
  });

  it("defending at home early is not a rush", () => {
    // Old fields say "hit something at 4:00"; the new ones say it was at home.
    const p = analyse(many(12, { firstHitAt: 240, firstAttackAt: 1100, firstDefendAt: 240, durationSec: 1700 }));
    expect(p.features.rushRate).toBe(0);
    expect(matchStyles(m({ firstHitAt: 240, firstAttackAt: -1 }))).not.toContain("rush");
  });

  it("gives good and bad feedback from the record", () => {
    const p = analyse(many(12, { tcIdleShare: 0.4, gathered: 15000, kills: 20, losses: 50 }));
    expect(p.good.join(" ")).toMatch(/out-gathers/);
    expect(p.bad.join(" ")).toMatch(/Town Center/);
    expect(p.bad.join(" ")).toMatch(/lose more units/);
  });
});
