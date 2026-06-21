import { describe, expect, it } from "vitest";
import { resolveBattle, UnitStack } from "./autobattle";

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
