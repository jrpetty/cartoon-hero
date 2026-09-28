import { describe, expect, it } from "vitest";
import { RNG } from "./rng";

describe("RNG", () => {
  it("is deterministic for the same seed", () => {
    const a = new RNG(12345);
    const b = new RNG(12345);
    for (let i = 0; i < 100; i++) {
      expect(a.next()).toBe(b.next());
    }
  });

  it("differs across seeds", () => {
    const a = new RNG(1);
    const b = new RNG(2);
    const seqA = Array.from({ length: 10 }, () => a.next());
    const seqB = Array.from({ length: 10 }, () => b.next());
    expect(seqA).not.toEqual(seqB);
  });

  it("int() stays within bounds", () => {
    const r = new RNG(99);
    for (let i = 0; i < 1000; i++) {
      const v = r.int(3, 7);
      expect(v).toBeGreaterThanOrEqual(3);
      expect(v).toBeLessThanOrEqual(7);
    }
  });

  it("weightedIndex respects weights statistically", () => {
    const r = new RNG(7);
    const counts = [0, 0, 0];
    for (let i = 0; i < 30000; i++) counts[r.weightedIndex([80, 15, 5])]++;
    expect(counts[0]).toBeGreaterThan(counts[1]);
    expect(counts[1]).toBeGreaterThan(counts[2]);
    // ~80/15/5 within tolerance
    expect(counts[0] / 30000).toBeGreaterThan(0.75);
    expect(counts[2] / 30000).toBeLessThan(0.08);
  });

  it("fork() produces independent deterministic streams", () => {
    const a = new RNG(42).fork(1);
    const b = new RNG(42).fork(1);
    const c = new RNG(42).fork(2);
    expect(a.next()).toBe(b.next());
    expect(a.next()).not.toBe(c.next());
  });
});
