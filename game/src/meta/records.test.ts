import { describe, expect, it, beforeEach } from "vitest";
import {
  HISTORY_LIMIT, clearHistory, listHistory, recordMatch, summarise, summariseHistory,
  type MatchRecord,
} from "./history";
import {
  ACHIEVEMENTS, ACHIEVEMENTS_BY_ID, CHALLENGES_PER_WEEK, CHALLENGE_POOL,
  challengesForWeek, evaluateAwards, weekIndex,
} from "./achievements";
import { emptyMatchReport } from "../sim/metrics";

beforeEach(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { for (const k of Object.keys(store)) delete store[k]; },
    key: () => null,
    length: 0,
  };
});

const rec = (over: Partial<MatchRecord> = {}): MatchRecord => ({
  at: 1_700_000_000_000,
  won: true,
  mapName: "Highlands",
  mode: "conquest",
  difficulty: "knight",
  players: 2,
  durationSec: 900,
  score: 1000,
  foeScore: 800,
  gathered: 5000,
  spent: 4000,
  unitsKilled: 30,
  unitsLost: 20,
  buildingsRazed: 4,
  buildingsLost: 2,
  peakArmy: 20,
  peakVillagers: 30,
  age: 2,
  idleVillagerTime: 40,
  idleTcTime: 60,
  tcSeconds: 600,
  favouriteUnit: "knight",
  ...over,
});

describe("Keeping a match", () => {
  it("stores a game and reads it back", () => {
    clearHistory();
    recordMatch(rec({ mapName: "Riverlands" }));
    const rows = listHistory();
    expect(rows).toHaveLength(1);
    expect(rows[0].mapName).toBe("Riverlands");
  });

  it("keeps the newest twenty and drops the rest", () => {
    clearHistory();
    for (let i = 0; i < HISTORY_LIMIT + 8; i++) {
      recordMatch(rec({ at: 1_700_000_000_000 + i * 1000, mapName: `M${i}` }));
    }
    const rows = listHistory();
    expect(rows).toHaveLength(HISTORY_LIMIT);
    // Newest first, and the oldest eight are gone.
    expect(rows[0].mapName).toBe(`M${HISTORY_LIMIT + 7}`);
    expect(rows.some((r) => r.mapName === "M0")).toBe(false);
  });

  it("survives a corrupt store rather than taking the menu down with it", () => {
    localStorage.setItem("banner_and_blade_history_v1", "{not json");
    expect(listHistory()).toEqual([]);
    localStorage.setItem("banner_and_blade_history_v1", '[{"junk":true},null,3]');
    expect(listHistory()).toEqual([]);
  });

  it("boils a full report down to what a trend needs", () => {
    const report = emptyMatchReport();
    report.mapName = "Gauntlet";
    report.durationSec = 1234;
    report.you.unitsKilled = 41;
    report.you.trainedByType = { villager: 60, knight: 12, archer: 5 };
    const r = summarise(report, {
      won: true, mode: "koth", difficulty: "duke", players: 4, at: 12345,
    });
    expect(r.mapName).toBe("Gauntlet");
    expect(r.unitsKilled).toBe(41);
    expect(r.difficulty).toBe("duke");
    // Villagers are trained most in nearly every game, and "you built
    // villagers" says nothing about how anyone plays.
    expect(r.favouriteUnit).toBe("knight");
  });

  it("reports no favourite when nothing but villagers was trained", () => {
    const report = emptyMatchReport();
    report.you.trainedByType = { villager: 40 };
    const r = summarise(report, { won: false, mode: "conquest", difficulty: "squire", players: 2, at: 1 });
    expect(r.favouriteUnit).toBe("");
  });
});

describe("Reading the record", () => {
  it("says nothing rather than dividing by zero on an empty history", () => {
    const s = summariseHistory([]);
    expect(s.played).toBe(0);
    expect(s.winRate).toBe(0);
    expect(s.killRatio).toBe(0);
  });

  it("counts the best streak in the order the games were played", () => {
    // Stored newest-first, so a summary that trusted the array order would
    // read every streak backwards.
    const rows = [
      rec({ at: 5, won: false }),
      rec({ at: 4, won: true }),
      rec({ at: 3, won: true }),
      rec({ at: 2, won: true }),
      rec({ at: 1, won: false }),
    ];
    expect(summariseHistory(rows).bestStreak).toBe(3);
  });

  it("gives kills-per-loss, and does not divide by zero on a flawless run", () => {
    expect(summariseHistory([rec({ unitsKilled: 40, unitsLost: 10 })]).killRatio).toBe(4);
    expect(summariseHistory([rec({ unitsKilled: 7, unitsLost: 0 })]).killRatio).toBe(7);
  });

  it("ignores games where nothing stood when averaging idle time", () => {
    const s = summariseHistory([
      rec({ idleTcTime: 300, tcSeconds: 600 }),
      rec({ idleTcTime: 0, tcSeconds: 0 }), // a match that ended before the TC did anything
    ]);
    expect(s.avgTcIdleShare).toBeCloseTo(0.5, 5);
  });
});

describe("Achievements", () => {
  it("pays for a win, once and only once", () => {
    const first = evaluateAwards(rec(), { earned: [], challengesDone: [] });
    expect(first.some((a) => a.id === "first_blood")).toBe(true);
    const again = evaluateAwards(rec(), { earned: ["first_blood"], challengesDone: [] });
    expect(again.some((a) => a.id === "first_blood")).toBe(false);
  });

  it("never pays out for losing", () => {
    // Every achievement is a thing to do *while winning*. A flawless loss
    // earning "Not One Villager" would be a strange kind of consolation.
    const lost = rec({
      won: false, unitsLost: 0, buildingsLost: 0, durationSec: 60,
      difficulty: "conqueror", age: 3, unitsKilled: 500, players: 8,
      peakVillagers: 99, buildingsRazed: 99, idleTcTime: 0,
    });
    expect(evaluateAwards(lost, { earned: [], challengesDone: [] })).toEqual([]);
  });

  it("recognises a flawless win", () => {
    const flawless = rec({ unitsLost: 0, buildingsLost: 0 });
    const ids = evaluateAwards(flawless, { earned: [], challengesDone: [] }).map((a) => a.id);
    expect(ids).toContain("flawless_eco");
  });

  it("does not award ten-to-one when nothing was lost at all", () => {
    // That case is already Not One Villager; a ratio against zero is not a ratio.
    expect(ACHIEVEMENTS_BY_ID.ten_to_one.test(rec({ unitsKilled: 50, unitsLost: 0 }))).toBe(false);
    expect(ACHIEVEMENTS_BY_ID.ten_to_one.test(rec({ unitsKilled: 50, unitsLost: 5 }))).toBe(true);
  });

  it("has no duplicate ids, since an id is what marks one as paid", () => {
    const ids = ACHIEVEMENTS.map((a) => a.id);
    expect(new Set(ids).size).toBe(ids.length);
    const cids = CHALLENGE_POOL.map((c) => c.id);
    expect(new Set(cids).size).toBe(cids.length);
  });

  it("pays something for every achievement, so none is a booby prize", () => {
    for (const a of [...ACHIEVEMENTS, ...CHALLENGE_POOL]) {
      expect(a.valor, a.id).toBeGreaterThan(0);
      expect(a.name.length, a.id).toBeGreaterThan(0);
      expect(a.desc.length, a.id).toBeGreaterThan(0);
    }
  });
});

describe("Weekly challenges", () => {
  it("offers three at a time", () => {
    expect(challengesForWeek(0)).toHaveLength(CHALLENGES_PER_WEEK);
  });

  it("shares nothing between consecutive weeks", () => {
    // A rotation that felt random would keep re-offering the same two, which is
    // worse than a rotation you can see the shape of.
    for (let w = 0; w < 40; w++) {
      const a = new Set(challengesForWeek(w).map((c) => c.id));
      for (const c of challengesForWeek(w + 1)) {
        expect(a.has(c.id), `week ${w} repeats ${c.id}`).toBe(false);
      }
    }
  });

  it("offers three distinct challenges within one week", () => {
    for (let w = 0; w < 40; w++) {
      const ids = challengesForWeek(w).map((c) => c.id);
      expect(new Set(ids).size, `week ${w}`).toBe(ids.length);
    }
  });

  it("visits the whole pool rather than favouring a few", () => {
    const seen = new Set<string>();
    for (let w = 0; w < 40; w++) for (const c of challengesForWeek(w)) seen.add(c.id);
    expect(seen.size).toBe(CHALLENGE_POOL.length);
  });

  it("can be earned again in a later week", () => {
    // The difference between a challenge and an achievement: the key carries
    // the week, so last week's tick doesn't cancel this week's.
    const week = weekIndex(rec().at);
    const target = challengesForWeek(week)[0];
    const m = rec({ favouriteUnit: "knight", durationSec: 300, unitsLost: 1, players: 8, spent: 4900, buildingsLost: 12, peakArmy: 90, difficulty: "conqueror" });
    const done = [`${week - 1}:${target.id}`];
    const got = evaluateAwards(m, { earned: [], challengesDone: done });
    // Whatever this week's set contains, an entry earned last week must not
    // block it.
    expect(got.filter((a) => a.weekly).length).toBeGreaterThan(0);
  });

  it("keys a week off the timestamp, not the wall clock", () => {
    expect(weekIndex(0)).toBe(0);
    expect(weekIndex(7 * 24 * 60 * 60 * 1000)).toBe(1);
    expect(weekIndex(7 * 24 * 60 * 60 * 1000 - 1)).toBe(0);
  });
});
