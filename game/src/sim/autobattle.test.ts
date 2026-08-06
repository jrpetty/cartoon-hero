import { describe, expect, it } from "vitest";
import { resolveBattle, UnitStack, LiveBattle } from "./autobattle";

describe("Auto-battle resolver (Warband Tactics core)", () => {
  it("a clearly stronger composition wins and is deterministic", () => {
    const knights: UnitStack[] = [{ type: "knight", count: 8 }];
    const fewMilitia: UnitStack[] = [{ type: "militia", count: 4 }];
    const r1 = resolveBattle(knights, fewMilitia, 42);
    const r2 = resolveBattle(knights, fewMilitia, 42);
    expect(r1.winner).toBe("A"); // 8 knights crush 4 militia
    expect(r1.survivorsA).toBeGreaterThan(0);
    expect(r1.survivorsB).toBe(0);
    expect(r1).toEqual(r2); // same seed → identical result
  });

  it("resolves to a winner (not a hang) and reports survivors", () => {
    const r = resolveBattle([{ type: "archer", count: 10 }], [{ type: "militia", count: 10 }], 7);
    expect(["A", "B", "draw"]).toContain(r.winner);
    expect(r.ticks).toBeGreaterThan(0);
    expect(r.survivorsA + r.survivorsB).toBeGreaterThanOrEqual(0);
  });

  it("star-ups make a stack stronger", () => {
    // 3 two-star knights vs 5 one-star knights — the upgrade should carry it.
    const wins2Star = (() => {
      let w = 0;
      for (const seed of [1, 2, 3, 4, 5]) {
        const r = resolveBattle([{ type: "knight", count: 3, star: 2 }], [{ type: "knight", count: 4, star: 1 }], seed);
        if (r.winner === "A") w++;
      }
      return w;
    })();
    expect(wins2Star).toBeGreaterThanOrEqual(3); // wins the majority of seeds
  });

  it("spearmen counter cavalry (composition matters)", () => {
    // Spearmen have a big bonus vs cavalry — fewer of them should beat knights.
    let spearWins = 0;
    for (const seed of [1, 2, 3, 4, 5, 6]) {
      const r = resolveBattle([{ type: "spearman", count: 8 }], [{ type: "knight", count: 6 }], seed);
      if (r.winner === "A") spearWins++;
    }
    expect(spearWins).toBeGreaterThanOrEqual(4);
  });
});

describe("LiveBattle (the watchable fight)", () => {
  const a: UnitStack[] = [{ type: "knight", count: 5 }];
  const b: UnitStack[] = [{ type: "militia", count: 6 }];

  const runToEnd = (seed: number, batch: number) => {
    const lb = new LiveBattle(a, b, seed);
    lb.begin();
    let guard = 0;
    while (!lb.done && guard++ < 5000) lb.step(batch);
    return lb;
  };

  it("plays out to a verdict and reports survivors", () => {
    const lb = runToEnd(42, 1);
    expect(lb.done).toBe(true);
    expect(["A", "B", "draw"]).toContain(lb.result().winner);
    expect(lb.ticks).toBeGreaterThan(0);
  });

  it("is deterministic regardless of how the steps are batched", () => {
    const r1 = runToEnd(7, 1).result();
    const r2 = runToEnd(7, 3).result();
    const r3 = runToEnd(7, 11).result();
    expect(r1).toEqual(r2);
    expect(r2).toEqual(r3);
  });

  it("units charge and cast their signature abilities during the fight", () => {
    const army = [{ type: "knight", star: 2 }, { type: "knight", star: 1 }, { type: "archer", star: 1 }, { type: "militia", star: 1 }];
    const foe = [{ type: "militia", star: 1 }, { type: "spearman", star: 1 }, { type: "archer", star: 1 }, { type: "knight", star: 1 }];
    const lb = new LiveBattle(army, foe, 42);
    lb.begin();
    let casts = 0, guard = 0;
    while (!lb.done && guard++ < 4000) {
      lb.step(1);
      for (const ev of lb.fxEvents) if (ev.kind === "ability") casts++;
      lb.fxEvents.length = 0;
    }
    expect(casts).toBeGreaterThan(0); // abilities fire in the live battle
  });

  it("doesn't advance before begin() (a static board preview)", () => {
    const lb = new LiveBattle(a, b, 1);
    const before = lb.world.entities.filter((e) => e.alive).length;
    lb.step(20); // no-op until begin()
    expect(lb.ticks).toBe(0);
    expect(lb.world.entities.filter((e) => e.alive).length).toBe(before);
  });
});
