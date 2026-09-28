import { describe, expect, it, beforeEach } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = { createElement: () => createCanvas(1, 1) };
import {
  CareerMatch, LOG_LIMIT, absorb, avgWinSecs, bestBy, careerFor, careerLog, careerMatch, careerTotals, emptyCareer,
  favourite, favouriteUnitOf, fold, kd, recordCareer, topUnit, winRate, worstBy,
} from "./career";
import { World } from "../sim/world";
import { generateMap } from "../maps/generator";
import { matchReport } from "../sim/metrics";
import { Team } from "../sim/types";
import { ui } from "../ui/ui";
import { CareerScreen } from "../ui/career_screen";
import { Profile } from "./profile";

/**
 * The player's own stats: every match, skirmish or online, summed into a
 * career — win rate and average win time per faction, favourite faction, map,
 * unit, commander and Oath, record against each enemy faction, and the rest.
 */

let store: Record<string, string>;
beforeEach(() => {
  store = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

let t0 = 1_700_000_000_000;
function m(over: Partial<CareerMatch> = {}): CareerMatch {
  return {
    at: (t0 += 60_000), kind: "skirmish", ranked: false, mode: "conquest", difficulty: "knight", won: true,
    faction: "legion", commander: "marshal", oaths: [], map: "Highlands", players: 2, format: "1 v 1", allies: [], foes: ["norse"],
    durationSec: 900, defeatedAt: -1, age: 2, ageTimes: [0, 300, 600], score: 5000, gathered: 8000,
    gatheredBy: { food: 4000, wood: 3000, gold: 1000 }, spent: 7000, kills: 40, losses: 20, razed: 5, buildingsLost: 1,
    damageDealt: 9000, damageTaken: 5000, peakArmy: 30, peakVillagers: 40, idleVillagerTime: 60, tcIdleShare: 0.1, upgrades: 6,
    trained: { villager: 40, legionary: 20, archer: 5 }, killed: { militia: 30, archer: 10 }, lost: { legionary: 15, archer: 5 },
    built: { house: 8, barracks: 2 }, ...over,
  };
}

describe("A career, from matches", () => {
  it("knows your record and average win time per faction", () => {
    const c = fold([
      m({ faction: "legion", won: true, durationSec: 600 }),
      m({ faction: "legion", won: true, durationSec: 1200 }),
      m({ faction: "legion", won: false, durationSec: 1500 }),
      m({ faction: "norse", won: true, durationSec: 480, foes: ["kingdom"] }),
    ]);
    expect(c.all.played).toBe(4);
    expect(winRate(c.byFaction.legion)).toBeCloseTo(2 / 3);
    expect(avgWinSecs(c.byFaction.legion)).toBe(900);
    expect(c.byFaction.legion.fastestWin).toBe(600);
    expect(c.byFaction.legion.longest).toBe(1500);
    expect(avgWinSecs(c.byFaction.norse)).toBe(480);
    expect(c.all.fastestWin).toBe(480);
  });

  it("finds your favourite faction, map, unit, commander and Oath", () => {
    const c = fold([
      m({ faction: "khanate", map: "Riverlands", commander: "steward", oaths: ["plough"], trained: { horse_archer: 30, villager: 90 } }),
      m({ faction: "khanate", map: "Riverlands", commander: "steward", oaths: ["plough", "bow"], trained: { horse_archer: 25 } }),
      m({ faction: "legion", map: "Highlands", trained: { legionary: 12 } }),
    ]);
    expect(favourite(c.byFaction)).toBe("khanate");
    expect(favourite(c.byMap)).toBe("Riverlands");
    expect(favourite(c.byCommander)).toBe("steward");
    expect(favourite(c.byOath)).toBe("plough");
    expect(topUnit(c, "trained"), "villagers aren't a favourite").toBe("horse_archer");
    expect(favouriteUnitOf(c.byFaction.legion)).toBe("legionary");
  });

  it("tracks your record against each enemy faction — best, worst, nemesis", () => {
    const games = [
      ...Array.from({ length: 4 }, () => m({ foes: ["norse"], won: false })),
      ...Array.from({ length: 4 }, () => m({ foes: ["kingdom"], won: true })),
      m({ foes: ["khanate", "khanate"], won: true }),
    ];
    const c = fold(games);
    expect(c.vsFaction.norse.played).toBe(4);
    expect(winRate(c.vsFaction.norse)).toBe(0);
    expect(c.vsFaction.khanate.played, "one match, however many of them").toBe(1);
    expect(worstBy(c.vsFaction)).toBe("norse");
    expect(bestBy(c.vsFaction)).toBe("kingdom");
  });

  it("counts streaks in the order games were played", () => {
    const c = fold([m({ won: true }), m({ won: true }), m({ won: true }), m({ won: false }), m({ won: false }), m({ won: true })]);
    expect(c.bestStreak).toBe(3);
    expect(c.worstStreak).toBe(2);
    expect(c.streak).toBe(1);
  });

  it("averages how long each age takes, over the games that reached it", () => {
    const c = fold([m({ ageTimes: [0, 300] }), m({ ageTimes: [0, 400, 800] }), m({ ageTimes: [0] })]);
    expect(c.ageReached[1]).toBe(2);
    expect(c.ageTimeSum[1] / c.ageReached[1]).toBe(350);
    expect(c.ageReached[2]).toBe(1);
  });

  it("keeps units trained, lost and killed", () => {
    const c = fold([m(), m()]);
    expect(c.units.legionary).toEqual({ trained: 40, killed: 0, lost: 30 });
    expect(c.units.militia.killed).toBe(60);
    expect(topUnit(c, "killed", false)).toBe("militia");
    expect(kd(c.all)).toBe(2);
  });
});

describe("Offline and online together", () => {
  it("records both, and filters to either", () => {
    recordCareer(m({ kind: "skirmish", won: true }));
    recordCareer(m({ kind: "online", won: false, difficulty: "" }));
    recordCareer(m({ kind: "online", ranked: true, won: true, difficulty: "" }));
    expect(careerFor("all").career.all.played).toBe(3);
    expect(careerFor("skirmish").career.all.played).toBe(1);
    expect(careerFor("online").career.all.played).toBe(2);
    expect(careerFor("ranked").career.all.won).toBe(1);
    expect(careerTotals().byKind.ranked.played).toBe(1);
  });

  it("keeps lifetime totals exact past the log's limit", () => {
    for (let i = 0; i < LOG_LIMIT + 25; i++) recordCareer(m({ won: i % 2 === 0 }));
    expect(careerLog().length).toBe(LOG_LIMIT);
    expect(careerTotals().all.played).toBe(LOG_LIMIT + 25);
  });

  it("survives full storage by keeping fewer recent matches", () => {
    (globalThis as unknown as { localStorage: unknown }).localStorage = {
      getItem: (k: string) => store[k] ?? null,
      setItem: (k: string, v: string) => { if (v.length > 20_000) throw new Error("QuotaExceeded"); store[k] = v; },
      removeItem: (k: string) => { delete store[k]; },
    };
    for (let i = 0; i < 60; i++) recordCareer(m());
    expect(careerTotals().all.played).toBe(60);
    expect(careerLog().length).toBeGreaterThan(0);
  });

  it("reads a real match: your faction, the enemy's, your units and ages", () => {
    const w = new World(5);
    w.init(generateMap("open_plains", 5), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest", ["shogunate", "khanate"]);
    for (let i = 0; i < 20 * 20; i++) w.tick();
    const report = matchReport(w, Team.Player, "Open Plains");
    const cm = careerMatch(report, { at: 1, won: true, kind: "online", ranked: true, mode: "conquest", difficulty: "knight", commander: "marshal" });
    expect(cm.faction).toBe("shogunate");
    expect(cm.foes).toEqual(["khanate"]);
    expect(cm.format).toBe("1 v 1");
    expect(cm.map).toBe("Open Plains");
    expect(cm.difficulty, "no AI difficulty online").toBe("");
    expect(cm.commander, "no commanders online").toBe("");
    expect(cm.ageTimes[0]).toBe(0);
    expect(cm.durationSec).toBe(20);
    const c = absorb(emptyCareer(), cm);
    expect(c.byKind.online.played).toBe(1);
    expect(c.byKind.ranked.played).toBe(1);
  });
});

describe("The Career screen", () => {
  function frame(s: CareerScreen, p: Profile, W = 1600, H = 900) {
    const canvas = createCanvas(W, H);
    ui.begin(canvas.getContext("2d") as unknown as CanvasRenderingContext2D, { mx: 400, my: 300, clicked: false, rightClicked: false, alt: false });
    const r = s.draw(W, H, 1, p);
    ui.flushTooltip(W, H);
    return r;
  }

  it("draws every tab and filter, empty or full, at any size", () => {
    const p = new Profile();
    const s = new CareerScreen();
    const internals = s as unknown as { tab: string; filter: string };
    const tabs = ["overview", "factions", "maps", "units", "matches"];
    for (const t of tabs) { internals.tab = t; expect(() => frame(s, p)).not.toThrow(); }
    const factions = ["kingdom", "legion", "norse", "shogunate", "khanate", "ascendancy"];
    for (let i = 0; i < 40; i++) {
      recordCareer(m({
        won: i % 3 !== 0, faction: factions[i % 6], foes: [factions[(i + 2) % 6]], kind: i % 4 === 0 ? "online" : "skirmish",
        ranked: i % 8 === 0, map: ["Highlands", "Riverlands", "Open Plains"][i % 3], oaths: i % 2 ? ["sword", "lance"] : ["plough"],
        difficulty: ["squire", "knight", "duke"][i % 3], durationSec: 400 + i * 30,
      }));
    }
    for (const f of ["all", "skirmish", "online", "ranked"]) {
      internals.filter = f;
      for (const t of tabs) {
        internals.tab = t;
        for (const [W, H] of [[1600, 900], [1024, 640]] as const) expect(() => frame(s, p, W, H), `${f}/${t} ${W}`).not.toThrow();
      }
    }
  });
});
