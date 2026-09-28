import { describe, expect, it } from "vitest";
import { World } from "../sim/world";
import { generateMap } from "../maps/generator";
import { Kind, Team } from "../sim/types";
import { Command, applyCommand, worldChecksum } from "../sim/commands";
import { Lockstep, Turn } from "./lockstep";
import { byTick } from "../sim/replay";

/**
 * Casting and replays rest on one promise: the same orders on the same opening
 * give the same match. Three checks of it —
 *   • a caster watching on a broadcast delay stays in sync with the players,
 *     even when one of them disconnects mid-match;
 *   • the lockstep's own record of applied orders rebuilds the match exactly
 *     (that record is an online match's replay);
 *   • a dropped player's orders that did arrive still count, for everyone.
 */

const FACTIONS = ["legion", "khanate", "norse"];
function freshWorld(n = 3) {
  const w = new World(77);
  const teams = Array.from({ length: n }, (_, i) => i);
  w.init(generateMap("open_plains", 77, n), teams.map(() => ({})), teams.map(() => 1), teams, teams.map(() => ""), false, undefined, "conquest", FACTIONS.slice(0, n));
  return w;
}

/** Deterministic busywork: each team keeps sending its units somewhere and training. */
function ordersFor(world: World, team: number, tick: number): Command[] {
  if (tick % 15 !== team * 3) return [];
  const ids = world.entitiesOf(team as Team, Kind.Unit).map((e) => e.id);
  const tc = world.entities.find((e) => e.alive && e.team === team && e.type === "town_center");
  const out: Command[] = [{ t: "move", team: team as Team, ids, x: 500 + ((tick * 37 + team * 211) % 1400), y: 500 + ((tick * 53) % 1400), queue: false, attackMove: tick % 2 === 0 }];
  if (tc && tick % 60 === 0) out.push({ t: "train", team: team as Team, buildingId: tc.id, unit: "villager" });
  return out;
}

describe("A caster on a delay", () => {
  it("stays in sync with the players, through a disconnect", () => {
    const N = 3, TICKS = 600, DELAY = 120, DROP_AT = 300;
    const players: { lock: Lockstep; world: World }[] = [];
    const casterWorld = freshWorld(N);
    const outbox: Turn[] = [];
    for (let t = 0; t < N; t++) {
      const world = freshWorld(N);
      players.push({ world, lock: new Lockstep(world, t as Team, [0, 1, 2] as Team[], 4, (turn) => outbox.push(turn)) });
    }
    const caster = new Lockstep(casterWorld, 0 as Team, [0, 1, 2] as Team[], 4);
    caster.record = [];
    let dropped = false;
    for (let step = 0; step < TICKS; step++) {
      // Each live player authors its turn, then everyone receives everything.
      for (let t = 0; t < N; t++) {
        if (dropped && t === 2) continue;
        const p = players[t];
        for (const c of ordersFor(p.world, t, p.lock.nextAuthorTick)) p.lock.localCommand(c);
        p.lock.authorTurn();
      }
      for (const turn of outbox.splice(0)) {
        for (let t = 0; t < N; t++) if (t !== turn.team) players[t].lock.receiveTurn(turn);
        caster.receiveTurn(turn);
      }
      if (step === DROP_AT && !dropped) {
        dropped = true;
        for (let t = 0; t < 2; t++) players[t].lock.dropTeam(2 as Team);
        caster.dropTeam(2 as Team); // arrives while the caster is still DELAY ticks behind
      }
      for (let t = 0; t < N; t++) if (!(dropped && t === 2)) while (players[t].lock.step()) { /* catch up */ }
      while (caster.currentTick <= caster.readyThrough() - DELAY && caster.step()) { /* stay behind */ }
    }
    expect(players[0].lock.currentTick - caster.currentTick, "the caster really was behind").toBeGreaterThanOrEqual(DELAY - 5);
    // Let the caster catch up to the players, then compare.
    while (caster.step()) { /* finish */ }
    expect(caster.currentTick).toBe(players[0].lock.currentTick);
    expect(worldChecksum(casterWorld)).toBe(worldChecksum(players[0].world));
    expect(worldChecksum(players[1].world)).toBe(worldChecksum(players[0].world));

    // And the caster's record is the match: replay it onto a fresh world.
    const replayWorld = freshWorld(N);
    const due = byTick(caster.record!);
    while (replayWorld.tickCount < caster.currentTick) {
      for (const c of due.get(replayWorld.tickCount) ?? []) applyCommand(replayWorld, c);
      replayWorld.tick();
    }
    expect(worldChecksum(replayWorld), "a replay rebuilds the match exactly").toBe(worldChecksum(players[0].world));
  });
});
