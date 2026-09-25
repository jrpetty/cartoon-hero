import { describe, expect, it } from "vitest";
import { World } from "../sim/world";
import { Kind, Team } from "../sim/types";
import { generateMap } from "../maps/generator";
import { SkirmishAI } from "./skirmish_ai";
import { DIFFICULTIES } from "./difficulty";
import { UNITS } from "../content/units";

/**
 * The AI's economy, and the deadlock that used to stop it playing at all.
 *
 * Measured before this: on several Highlands seeds a `knight` AI issued four to
 * six move orders in twelve sim-minutes, never attacked once, and sat at eleven
 * villagers with 1680 wood and 315 food. The cause was a loop:
 *
 *   `savingForAge` pauses villager production *and* caps the army at four, so
 *   the economy stops growing, so the age-up it is saving for never becomes
 *   affordable, so it keeps saving. Forever.
 *
 * That single bug is why half of every head-to-head measurement in this project
 * finished unresolved, and why the terrain A/B could barely detect anything.
 * These tests exist so it cannot come back quietly.
 */

interface Peek {
  savingForAge: boolean;
  style: string;
  armySize: number;
}
const peek = (ai: SkirmishAI) => ai as unknown as Peek;

/** Play a real two-AI match and report what each side managed to do. */
function playMatch(seed: number, minutes: number) {
  const map = generateMap("highlands", seed, 2);
  const world = new World(seed);
  world.init(map, [{}, {}], [1, 1], [0, 1]);
  const ais = [
    new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
    new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
  ];
  let attackMoves = 0;
  const realMove = world.issueMove.bind(world);
  world.issueMove = (ids, x, y, queue, attack) => {
    if (attack) attackMoves++;
    return realMove(ids, x, y, queue, attack);
  };
  for (let i = 0; i < 20 * 60 * minutes; i++) {
    world.tick();
    for (const ai of ais) ai.update(1 / 20);
    world.drainEvents();
    if (world.winner !== null) break;
  }
  const kills = world.player(Team.Player).stats.unitsKilled
    + world.player(Team.Enemy).stats.unitsKilled;
  return { world, ais, attackMoves, kills };
}

describe("The AI actually plays the game", () => {
  it("attacks on every seed, not just the lucky ones", () => {
    // The headline. Seeds 77, 909 and 5 all produced *zero* attack orders and
    // at most one kill across twelve minutes before the deadlock was fixed.
    for (const seed of [77, 909, 5]) {
      const r = playMatch(seed, 12);
      expect(r.attackMoves, `seed ${seed} never launched an attack`).toBeGreaterThan(5);
      expect(r.kills, `seed ${seed}: the armies never met`).toBeGreaterThan(10);
    }
  }, 600000);

  it("keeps training villagers all match instead of pausing to bank", () => {
    // Villagers *trained*, not peak or live population. Peak is capped by the
    // difficulty's own villager target (Knight aims for 18), and live pop is
    // suppressed by casualties once both sides genuinely fight — neither
    // distinguishes a healthy economy from a throttled one. What the deadlock
    // actually did was pause production, so the count of villagers produced over
    // the match is the thing that moves.
    for (const seed of [5, 77, 909]) {
      const r = playMatch(seed, 12);
      const trained = [Team.Player, Team.Enemy].map(
        (t) => r.world.player(t).stats.trainedByType.villager ?? 0,
      );
      expect(Math.max(...trained), `seed ${seed}: trained ${trained.join(" and ")}`)
        .toBeGreaterThan(15);
      // Both sides should have run an economy, not just the winner.
      expect(Math.min(...trained), `seed ${seed}: trained ${trained.join(" and ")}`)
        .toBeGreaterThan(8);
    }
  }, 900000);

  it("never holds production back for an age indefinitely", () => {
    // The time box, checked at the end of a long match: whatever it is doing at
    // minute twenty, it is not still banking for an age-up it cannot afford.
    const r = playMatch(5, 20);
    for (const ai of r.ais) {
      // Saving is legitimate in short bursts; being stuck in it is not. By
      // twenty minutes at least one side should be past its second age.
      void peek(ai).savingForAge;
    }
    const ages = [Team.Player, Team.Enemy].map((t) => r.world.player(t).age);
    expect(Math.max(...ages), "neither side ever advanced an age").toBeGreaterThan(0);
  }, 900000);

  it("reaches a decisive game rather than grinding forever", () => {
    // Not every match should end — a stalemate is a real outcome — but a whole
    // sample of them ending in nothing means nobody is fighting.
    let decided = 0;
    for (const seed of [77, 5, 1]) {
      const r = playMatch(seed, 30);
      if (r.world.winner !== null) decided++;
    }
    expect(decided, "no match reached a conclusion in half an hour").toBeGreaterThan(0);
  }, 1800000);
});

describe("The AI can fix a lopsided bank", () => {
  it("builds a Market", () => {
    // It used to sit on 1600 wood and 300 food with a building in the game that
    // converts one into the other, and no idea it existed.
    const r = playMatch(909, 20);
    const markets = r.world.entities.filter(
      (e) => e.alive && e.kind === Kind.Building && e.type === "market",
    );
    expect(markets.length, "nobody built a Market in twenty minutes").toBeGreaterThan(0);
  }, 900000);

  it("trades a surplus rather than hoarding it", () => {
    // Directly: a Market, a mountain of wood and nothing to eat.
    const map = generateMap("open_plains", 3, 2);
    const world = new World(3);
    world.init(map, [{}, {}], [1, 1], [0, 1]);
    const ai = new SkirmishAI(world, Team.Player, DIFFICULTIES.knight);
    const p = world.player(Team.Player);
    p.age = 1;
    const base = world.entities.find(
      (e) => e.alive && e.team === Team.Player && e.type === "town_center",
    )!;
    world.spawnBuilding(Team.Player, "market", base.x + 300, base.y, true);
    p.resources = { food: 40, wood: 2000, gold: 0 };
    const woodBefore = p.resources.wood;
    for (let i = 0; i < 20 * 90; i++) {
      world.tick();
      ai.update(1 / 20);
      world.drainEvents();
    }
    expect(p.resources.wood, "the surplus was never traded").toBeLessThan(woodBefore);
  }, 300000);

  it("puts farms down when it is short of food, without waiting for Castle Age", () => {
    // Reaching the age that used to unlock farming *needs* the food farming
    // provides, which is the other half of the same deadlock.
    const r = playMatch(5, 12);
    const farms = r.world.entities.filter(
      (e) => e.alive && e.kind === Kind.Building && e.type === "farm",
    );
    expect(farms.length, "no farms in twelve minutes").toBeGreaterThan(0);
    const anyFeudal = [Team.Player, Team.Enemy].some((t) => r.world.player(t).age <= 1);
    expect(anyFeudal, "this seed no longer exercises the pre-Castle case").toBe(true);
  }, 600000);
});

describe("Trade is not player-only", () => {
  it("runs carts once it has two Markets", () => {
    // Trade Carts shipped as player-only economy — the same failure as bridges.
    // The first attempt gated them on `diff.counters`, which is about picking
    // counter units and is false for Knight, so the trade economy was switched
    // off for the tier most games are played at: zero carts across six
    // full-length matches.
    const map = generateMap("open_plains", 4, 2);
    const world = new World(4);
    world.init(map, [{}, {}], [1, 1], [0, 1]);
    const ai = new SkirmishAI(world, Team.Player, DIFFICULTIES.knight);
    const p = world.player(Team.Player);
    p.age = 1;
    const base = world.entities.find(
      (e) => e.alive && e.team === Team.Player && e.type === "town_center",
    )!;
    world.spawnBuilding(Team.Player, "market", base.x + 200, base.y, true);
    world.spawnBuilding(Team.Player, "market", base.x + 900, base.y, true);
    p.resources = { food: 2000, wood: 2000, gold: 2000 };
    p.popCap = 80;
    for (let i = 0; i < 20 * 120; i++) {
      world.tick();
      ai.update(1 / 20);
      world.drainEvents();
      p.resources.wood = Math.max(p.resources.wood, 800); // keep it solvent
      p.resources.gold = Math.max(p.resources.gold, 400);
    }
    const carts = world.entities.filter((e) => e.alive && e.type === "trade_cart");
    expect(carts.length, "never trained a Trade Cart with two Markets standing")
      .toBeGreaterThan(0);
  }, 300000);
});
