import { describe, expect, it } from "vitest";
import { World } from "../sim/world";
import { Team, Kind } from "../sim/types";
import { generateMap } from "../maps/generator";
import { Command, applyCommand, worldChecksum } from "../sim/commands";
import { Lockstep, Turn } from "./lockstep";

function freshWorld(seed: number): World {
  const map = generateMap("open_plains", seed, 2);
  const w = new World(seed);
  w.init(map, [{}, {}], [1, 1]);
  return w;
}

describe("Lockstep determinism", () => {
  it("two clients fed the same command stream stay bit-identical", () => {
    const SEED = 4242;
    // A perfect ordered "network": turns A sends go to B, and vice versa.
    const aToB: Turn[] = [];
    const bToA: Turn[] = [];
    const a = new Lockstep(freshWorld(SEED), Team.Player, [Team.Player, Team.Enemy], 4, (t) => aToB.push(t));
    const b = new Lockstep(freshWorld(SEED), Team.Enemy, [Team.Player, Team.Enemy], 4, (t) => bToA.push(t));

    // Each client commands its own villagers around with a deterministic script.
    const villA = a.world.entitiesOf(Team.Player, Kind.Unit).filter((e) => e.type === "villager").map((e) => e.id);
    const villB = b.world.entitiesOf(Team.Enemy, Kind.Unit).filter((e) => e.type === "villager").map((e) => e.id);

    for (let frame = 0; frame < 120; frame++) {
      if (frame % 20 === 0) {
        const tx = 600 + (frame % 5) * 90;
        a.localCommand({ t: "move", team: Team.Player, ids: villA, x: tx, y: 500 + frame, queue: false, attackMove: false });
        b.localCommand({ t: "move", team: Team.Enemy, ids: villB, x: tx, y: 600 + frame, queue: false, attackMove: false });
      }
      a.authorTurn();
      b.authorTurn();
      while (bToA.length) a.receiveTurn(bToA.shift()!); // A consumes B's turns
      while (aToB.length) b.receiveTurn(aToB.shift()!); // B consumes A's turns
      while (a.step()) { /* advance as far as input allows */ }
      while (b.step()) { /* advance as far as input allows */ }
    }

    expect(a.currentTick).toBeGreaterThan(100);
    expect(a.currentTick).toBe(b.currentTick);
    expect(worldChecksum(a.world)).toBe(worldChecksum(b.world));
  });

  it("a tick cannot advance until every player's turn has arrived", () => {
    const a = new Lockstep(freshWorld(7), Team.Player, [Team.Player, Team.Enemy], 3);
    // Warm-up ticks (0..2) are pre-seeded, so exactly inputDelay steps are free.
    let free = 0;
    while (a.step()) free++;
    expect(free).toBe(3);
    // Now stalled: Team.Enemy never submitted tick 3.
    a.authorTurn(); // only authors Team.Player's tick 3
    expect(a.canStep()).toBe(false);
    a.receiveTurn({ tick: 3, team: Team.Enemy, cmds: [] });
    expect(a.step()).toBe(true);
  });

  it("applyCommand ignores commands for units you don't own", () => {
    const w = freshWorld(11);
    const enemyVill = w.entitiesOf(Team.Enemy, Kind.Unit).find((e) => e.type === "villager")!;
    const before = { x: enemyVill.x, y: enemyVill.y };
    // Player tries to move an ENEMY villager — must be a no-op.
    const cmd: Command = { t: "move", team: Team.Player, ids: [enemyVill.id], x: 999, y: 999, queue: false, attackMove: false };
    applyCommand(w, cmd);
    expect(enemyVill.order.kind).toBe(0); // still Idle
    expect(enemyVill.x).toBe(before.x);
    expect(enemyVill.y).toBe(before.y);
  });
});
