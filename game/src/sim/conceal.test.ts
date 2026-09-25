import { describe, expect, it } from "vitest";
import { World } from "./world";
import { OrderKind, Team } from "./types";
import { generateMap } from "../maps/generator";
import { TILE, CONCEAL_RANGE } from "../content/balance";
import { Terrain } from "../maps/terrain_kinds";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";

/**
 * Woodland you can hide in.
 *
 * Forest already cost speed and sight, but hid nobody — so the terrain this
 * project describes as the *soft* barrier was purely a tax and never an
 * opportunity. A treeline is now somewhere to wait, which also gives scouting
 * a job beyond lifting fog.
 */

/** A world that is all grass except a block of woodland around the middle. */
function woods(seed = 21) {
  const map = generateMap("open_plains", seed, 2);
  const w = new World(seed);
  w.init(map, [{}, {}], [1, 1], [0, 1]);
  w.terrain = new Uint8Array(w.terrain.length);
  const mid = Math.floor(w.terrainCols / 2);
  for (let dy = -6; dy <= 6; dy++) {
    for (let dx = -6; dx <= 6; dx++) {
      w.terrain[(mid + dy) * w.terrainCols + (mid + dx)] = Terrain.Forest;
      w.grid.setBlocked(mid + dx, mid + dy, false);
    }
  }
  return { w, wx: mid * TILE + TILE / 2, wy: mid * TILE + TILE / 2 };
}

/** Concealment is recomputed with vision, every five ticks. */
const settle = (w: World) => { for (let i = 0; i < 6; i++) { w.tick(); w.drainEvents(); } };

describe("Hiding in the woods", () => {
  it("hides a unit standing in forest from a distant enemy", () => {
    const { w, wx, wy } = woods();
    const hider = w.spawnUnit(Team.Player, "archer", wx, wy);
    // Far enough away to have no eyes in the trees.
    w.spawnUnit(Team.Enemy, "knight", wx + 900, wy);
    settle(w);
    expect(w.visibleTo(Team.Enemy, hider), "the ambush was visible from 900 away").toBe(false);
  });

  it("still shows it to its own side", () => {
    const { w, wx, wy } = woods();
    const hider = w.spawnUnit(Team.Player, "archer", wx, wy);
    settle(w);
    expect(w.visibleTo(Team.Player, hider)).toBe(true);
  });

  it("gives it away once something comes close enough", () => {
    const { w, wx, wy } = woods();
    const hider = w.spawnUnit(Team.Player, "archer", wx, wy);
    const scout = w.spawnUnit(Team.Enemy, "scout", wx + CONCEAL_RANGE * 0.5, wy);
    settle(w);
    expect(w.visibleTo(Team.Enemy, hider), "walked into the wood and saw nothing").toBe(true);
    void scout;
  });

  it("leaves a unit in the open plainly visible", () => {
    const { w, wx, wy } = woods();
    // Well outside the wood, on bare grass.
    const inTheOpen = w.spawnUnit(Team.Player, "archer", wx + 600, wy);
    w.spawnUnit(Team.Enemy, "knight", wx + 700, wy);
    settle(w);
    expect(w.visibleTo(Team.Enemy, inTheOpen)).toBe(true);
  });

  it("does not hide buildings — a Town Centre in a wood is still a Town Centre", () => {
    const { w, wx, wy } = woods();
    const b = w.spawnBuilding(Team.Player, "house", wx, wy, true);
    w.spawnUnit(Team.Enemy, "knight", wx + 900, wy);
    settle(w);
    // Buildings follow the ordinary fog rules, so scout it first.
    const spy = w.spawnUnit(Team.Enemy, "scout", wx + 80, wy);
    settle(w);
    expect(w.visibleTo(Team.Enemy, b)).toBe(true);
    void spy;
  });

  it("gives an ambusher away the moment it attacks", () => {
    // An ambush that stays invisible while it kills you is not an ambush.
    const { w, wx, wy } = woods();
    const hider = w.spawnUnit(Team.Player, "archer", wx, wy);
    const victim = w.spawnUnit(Team.Enemy, "villager", wx + 60, wy);
    w.issueAttack([hider.id], victim.id);
    for (let i = 0; i < 20 * 6; i++) { w.tick(); w.drainEvents(); }
    expect(hider.attackCooldown, "never actually attacked").toBeGreaterThan(0);
    expect(w.visibleTo(Team.Enemy, hider), "shot from cover and stayed hidden").toBe(true);
  });
});

describe("Concealment is not decoration", () => {
  it("stops a long-ranged unit picking a target it cannot see", () => {
    // Without this a longbowman would shoot a hidden unit from across the field
    // and the whole feature would exist only on the minimap.
    const { w, wx, wy } = woods();
    const hider = w.spawnUnit(Team.Player, "spearman", wx, wy);
    const archer = w.spawnUnit(Team.Enemy, "archer", wx + 190, wy);
    for (let i = 0; i < 20 * 4; i++) { w.tick(); w.drainEvents(); }
    expect(w.visibleTo(Team.Enemy, hider), "close enough to spot").toBe(false);
    expect(archer.order.target, "acquired a target it could not see").not.toBe(hider.id);
  });

  it("lets the same unit be attacked once it steps into the open", () => {
    const { w, wx, wy } = woods();
    const open = w.spawnUnit(Team.Player, "spearman", wx + 600, wy);
    const archer = w.spawnUnit(Team.Enemy, "archer", wx + 600 + 120, wy);
    for (let i = 0; i < 20 * 4; i++) { w.tick(); w.drainEvents(); }
    expect(archer.order.kind, "ignored a unit standing in the open").toBe(OrderKind.Attack);
    expect(archer.order.target).toBe(open.id);
  });

  it("is recomputed as units move, not fixed at spawn", () => {
    const { w, wx, wy } = woods();
    const hider = w.spawnUnit(Team.Player, "archer", wx, wy);
    w.spawnUnit(Team.Enemy, "knight", wx + 900, wy);
    settle(w);
    expect(w.visibleTo(Team.Enemy, hider)).toBe(false);
    // Walk it out of the trees.
    w.issueMove([hider.id], wx + 700, wy);
    for (let i = 0; i < 20 * 30; i++) { w.tick(); w.drainEvents(); }
    expect(w.terrainAt(hider.x, hider.y), "never left the wood").not.toBe(Terrain.Forest);
    // spottedBy rather than visibleTo: out in the open it is no longer
    // *concealed*, but whether the enemy can actually see it is then the
    // ordinary fog question, and conflating the two tests neither.
    expect(hider.spottedBy, "still concealed out in the open").toBe(~0);
  });
});

describe("Both sides play by it", () => {
  it("a whole match on wooded ground still resolves into a fight", () => {
    // The failure mode worth guarding: if concealment made armies unable to
    // find each other, matches would quietly stop happening.
    const map = generateMap("black_forest", 5, 2);
    const w = new World(5);
    w.init(map, [{}, {}], [1, 1], [0, 1]);
    const ais = [
      new SkirmishAI(w, Team.Player, DIFFICULTIES.knight),
      new SkirmishAI(w, Team.Enemy, DIFFICULTIES.knight),
    ];
    for (let i = 0; i < 20 * 60 * 14; i++) {
      w.tick();
      for (const a of ais) a.update(1 / 20);
      w.drainEvents();
      if (w.winner !== null) break;
    }
    const kills = w.player(Team.Player).stats.unitsKilled + w.player(Team.Enemy).stats.unitsKilled;
    expect(kills, "nobody could find anybody on a forest map").toBeGreaterThan(0);
  }, 300000);

  it("marks units in the open as seen by everyone, which is the cheap default", () => {
    const { w, wx, wy } = woods();
    const u = w.spawnUnit(Team.Player, "knight", wx + 800, wy);
    settle(w);
    expect(u.spottedBy).toBe(~0);
  });
});
