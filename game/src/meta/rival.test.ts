import { beforeEach, describe, expect, it } from "vitest";
import type { CareerMatch } from "./career";
import { adaptationFor, loadRival, memoryOf, settleRival, taunt } from "./rival";
import { World } from "../sim/world";
import { generateMap } from "../maps/generator";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";
import { Team } from "../sim/types";

/**
 * The Nemesis: a named rival who learns how you play a layer at a time, climbs
 * when it beats you, carries a scar when you beat it, and falls on the third
 * defeat — with an heir who keeps the grudge.
 */

const store: Record<string, string> = {};
beforeEach(() => {
  for (const k of Object.keys(store)) delete store[k];
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null, setItem: (k: string, v: string) => { store[k] = v; }, removeItem: (k: string) => { delete store[k]; },
  };
});

let t = 1_700_000_000_000;
function m(over: Partial<CareerMatch> = {}): CareerMatch {
  return {
    at: (t += 60_000), kind: "skirmish", ranked: false, mode: "conquest", difficulty: "knight", won: true, faction: "legion", commander: "", oaths: [],
    map: "Highlands", players: 2, format: "1 v 1", allies: [], foes: ["norse"], durationSec: 1500, defeatedAt: -1, age: 2, ageTimes: [0, 400, 1000],
    score: 5000, gathered: 12000, gatheredBy: { food: 5000, wood: 5000, gold: 2000 }, spent: 11000, kills: 40, losses: 40, razed: 3, buildingsLost: 3,
    damageDealt: 9000, damageTaken: 9000, peakArmy: 30, peakVillagers: 38, idleVillagerTime: 60, tcIdleShare: 0.1, upgrades: 6,
    trained: { villager: 40, archer: 30, militia: 4 }, killed: {}, lost: {}, built: {}, unitKills: { archer: 35 },
    firstHitAt: 300, firstAttackAt: 300, opener: { archer: 900 }, ...over,
  };
}

describe("Your Nemesis", () => {
  it("starts as a stranger who knows nothing", () => {
    const st = loadRival("legion");
    expect(st.current.name).toBeTruthy();
    expect(st.current.rank).toBe(1);
    expect(st.current.faction).not.toBe("legion");
    const mem = memoryOf(st.current, [m(), m()]);
    expect(mem.army).toBeNull();
    expect(taunt(st.current, mem)).toMatch(/learn everything/);
    expect(loadRival().current.name, "it persists").toBe(st.current.name);
  });

  it("beats you, rises a rank, earns a name, and learns your army", () => {
    const st = loadRival();
    const out = settleRival(st, m({ won: false, durationSec: 700 }));
    expect(out.promoted).toBe(true);
    expect(st.current.rank).toBe(2);
    expect(st.current.epithet).toBe("the Swift");
    const mem = memoryOf(st.current, [m(), m(), m()]);
    expect(mem.armyWord).toBe("archers");
    expect(adaptationFor(st.current, mem).expect?.archer).toBeGreaterThan(8);
  });

  it("learns your timing next: an early attacker finds it walled up", () => {
    const st = loadRival();
    settleRival(st, m({ won: true }));
    settleRival(st, m({ won: true }));
    const mem = memoryOf(st.current, Array.from({ length: 6 }, () => m()));
    expect(mem.timing).toBe("early");
    const a = adaptationFor(st.current, mem);
    expect(a.style).toBe("turtle");
    expect(a.walls).toBe(true);
    expect(taunt(st.current, mem)).toMatch(/archers gave me this scar/);
  });

  it("a slow builder gets rushed", () => {
    const st = loadRival();
    settleRival(st, m({ won: false }));
    settleRival(st, m({ won: false }));
    const late = Array.from({ length: 6 }, () => m({ firstAttackAt: 1100, firstHitAt: 1100 }));
    expect(adaptationFor(st.current, memoryOf(st.current, late)).style).toBe("rush");
  });

  it("carries a scar when beaten, and falls on the third defeat — its heir keeps the grudge", () => {
    const st = loadRival();
    const first = st.current.name;
    const a = settleRival(st, m({ won: true }));
    expect(a.renown).toBe(60);
    expect(st.current.scars[0]).toMatch(/archers/);
    expect(st.current.grudge).toBe("archer");
    settleRival(st, m({ won: true }));
    const last = settleRival(st, m({ won: true }));
    expect(last.slain).toBe(true);
    expect(last.renown).toBe(60 + 250);
    expect(st.fallen[0].name).toBe(first);
    expect(st.current.name).not.toBe(first);
    expect(st.current.grudge).toBe("archer");
    expect(st.current.origin).toMatch(first);
    expect(loadRival().fallen.length).toBe(1);
  });
});

describe("The rival's brain", () => {
  it("brings the counter to the army it remembers, before seeing a soldier", () => {
    const w = new World(5);
    w.init(generateMap("open_plains", 5), [{}, {}], [1, 1]);
    const plain = new SkirmishAI(w, Team.Enemy, DIFFICULTIES.knight);
    const rival = new SkirmishAI(w, Team.Enemy, DIFFICULTIES.knight);
    rival.adapt({ expect: { cavalry: 12 } });
    const a = (plain as unknown as { composition(): Record<string, number> }).composition();
    const b = (rival as unknown as { composition(): Record<string, number> }).composition();
    const share = (c: Record<string, number>, k: string) => (c[k] ?? 0) / Object.values(c).reduce((x, y) => x + y, 0);
    // Anti-cavalry goes up, the militia that loses to horse goes down.
    expect(share(b, "spearman") + share(b, "pikeman")).toBeGreaterThan(share(a, "spearman") + 0.2);
    expect(share(b, "militia")).toBeLessThan(share(a, "militia"));
  });

  it("plays a real match adapted without stalling", () => {
    const w = new World(6);
    w.init(generateMap("open_plains", 6), [{}, {}], [1, 1]);
    const ais = [new SkirmishAI(w, Team.Player, DIFFICULTIES.knight), new SkirmishAI(w, Team.Enemy, DIFFICULTIES.knight)];
    ais[1].adapt({ style: "turtle", walls: true, expect: { archer: 10 } });
    for (let i = 0; i < 20 * 240; i++) { w.tick(); for (const ai of ais) ai.update(1 / 20); w.drainEvents(); }
    expect(w.player(Team.Enemy).stats.gathered).toBeGreaterThan(800);
  });
});
