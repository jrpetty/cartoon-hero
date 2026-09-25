import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind } from "./types";
import { generateMap } from "../maps/generator";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";
import { SIM_DT, SIM_HZ } from "../content/balance";

function dist(ax: number, ay: number, bx: number, by: number) {
  return Math.hypot(ax - bx, ay - by);
}

describe("Regicide AI behaviour", () => {
  it("seizes an exposed enemy King instead of ignoring him", () => {
    const seed = 4242;
    const map = generateMap("open_plains", seed, 2);
    const w = new World(seed);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, false, undefined, "regicide");
    const ai = new SkirmishAI(w, Team.Enemy, DIFFICULTIES.lord);

    // Strip the player down to a lone, undefended King — and let him wander to
    // the edge of the enemy's territory where their army can reach him.
    for (const e of w.entitiesOf(Team.Player)) {
      if (e.type !== "king") w.dealDamage(Team.Enemy, e, 99999, "test");
    }
    const pKing = w.entities.find((e) => e.alive && e.type === "king" && e.team === Team.Player)!;
    const eStart = w.map.starts[Team.Enemy];
    pKing.x = eStart.x + 220;
    pKing.y = eStart.y;
    // Give the enemy a real standing army on hand.
    for (let i = 0; i < 24; i++) w.spawnUnit(Team.Enemy, "militia", eStart.x + (i % 8) * 12, eStart.y + Math.floor(i / 8) * 12);

    for (let i = 0; i < SIM_HZ * 200; i++) {
      w.tick();
      ai.update(SIM_DT);
      w.drainEvents();
      if (w.winner !== null) break;
    }
    // The exposed King got hunted down — that's the win condition.
    expect(w.winner).toBe(Team.Enemy);
  }, 60000);

  it("bodyguards its own King — recalls the army when a force closes in", () => {
    const seed = 909;
    const map = generateMap("open_plains", seed, 2);
    const w = new World(seed);
    w.init(map, [{}, {}], [1, 1], undefined, undefined, false, undefined, "regicide");
    const ai = new SkirmishAI(w, Team.Enemy, DIFFICULTIES.lord);

    const king = w.entities.find((e) => e.alive && e.type === "king" && e.team === Team.Enemy)!;
    // Park the AI's army well away from the King.
    const far = { x: king.x + 700, y: king.y + 200 };
    for (let i = 0; i < 8; i++) w.spawnUnit(Team.Enemy, "militia", far.x + i * 12, far.y);
    // Drop a menacing (passive) raiding party right on top of the King.
    for (let i = 0; i < 10; i++) w.spawnUnit(Team.Player, "militia", king.x + 40 + i * 6, king.y + 30);

    for (let i = 0; i < SIM_HZ * 30; i++) {
      w.tick();
      ai.update(SIM_DT);
      w.drainEvents();
    }
    // The defenders broke off and rushed back to guard the King.
    const guards = w
      .entitiesOf(Team.Enemy, Kind.Unit)
      .filter((u) => u.type === "militia" && dist(u.x, u.y, king.x, king.y) < 380);
    expect(guards.length).toBeGreaterThan(0);
  }, 60000);
});
