import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team } from "./types";
import { generateMap } from "../maps/generator";
import { teamMetrics, snapshotMetrics } from "./metrics";

function mk(seed: number, players = 2): World {
  const w = new World(seed);
  w.init(generateMap("open_plains", seed, players), Array(players).fill({}), Array(players).fill(1));
  return w;
}

describe("teamMetrics", () => {
  it("counts villagers, army strength, and a composite score", () => {
    const w = mk(1);
    const m0 = teamMetrics(w, Team.Player);
    expect(m0.villagers).toBeGreaterThan(0);
    expect(m0.army).toBe(0);
    expect(m0.military).toBe(0);

    w.spawnUnit(Team.Player, "knight", 800, 800);
    const m1 = teamMetrics(w, Team.Player);
    expect(m1.army).toBe(1);
    expect(m1.military).toBeGreaterThan(0);
    expect(m1.score).toBeGreaterThan(m0.score); // fielding an army raises score
  });

  it("reflects kills, gathering and age in the score", () => {
    const w = mk(2);
    const before = teamMetrics(w, Team.Player).score;
    const p = w.player(Team.Player);
    p.stats.unitsKilled += 12;
    p.stats.gathered += 800;
    p.age = 2;
    const after = teamMetrics(w, Team.Player);
    expect(after.killed).toBe(12);
    expect(after.age).toBe(2);
    expect(after.score).toBeGreaterThan(before);
  });

  it("snapshots every team (works at 8v8 scale)", () => {
    const w = mk(3, 8);
    const snap = snapshotMetrics(w);
    expect(snap.length).toBe(8);
    expect(snap.map((s) => s.team)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
  });
});
