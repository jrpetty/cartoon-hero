import { describe, expect, it } from "vitest";
import { World } from "../sim/world";
import { Team, Kind, OrderKind } from "../sim/types";
import { generateMap } from "../maps/generator";
import { SkirmishAI, armyTargetPriority } from "./skirmish_ai";
import { DIFFICULTIES, DIFFICULTY_IDS } from "./difficulty";
import { SIM_DT, SIM_HZ } from "../content/balance";

describe("Difficulty tiers", () => {
  it("has five tiers, all fully specified, Conqueror the hardest", () => {
    expect(DIFFICULTY_IDS).toEqual(["squire", "knight", "lord", "warlord", "conqueror"]);
    for (const id of DIFFICULTY_IDS) {
      const d = DIFFICULTIES[id];
      expect(d).toBeTruthy();
      expect(typeof d.focusFire).toBe("boolean");
    }
    const c = DIFFICULTIES.conqueror;
    const w = DIFFICULTIES.warlord;
    // Hardest by the metrics that matter: economy, reaction speed, no mistakes.
    expect(c.econMult).toBeGreaterThanOrEqual(w.econMult);
    expect(c.reactionSec).toBeLessThanOrEqual(w.reactionSec);
    expect(c.executionError).toBe(0);
    expect(c.focusFire).toBe(true);
    // Only the sharper tiers micro their army.
    expect(DIFFICULTIES.squire.focusFire).toBe(false);
    expect(DIFFICULTIES.knight.focusFire).toBe(false);
    expect(DIFFICULTIES.lord.focusFire).toBe(true);
  });
});

describe("Focus-fire target priority", () => {
  it("kills siege > champion > monk > ranged > cavalry > infantry", () => {
    const siege = armyTargetPriority("catapult");
    const hero = armyTargetPriority("hero");
    const monk = armyTargetPriority("monk");
    const ranged = armyTargetPriority("archer");
    const cav = armyTargetPriority("knight");
    const inf = armyTargetPriority("militia");
    expect(siege).toBeGreaterThan(hero);
    expect(hero).toBeGreaterThan(monk);
    expect(monk).toBeGreaterThan(ranged);
    expect(ranged).toBeGreaterThan(inf);
    expect(siege).toBeGreaterThan(cav);
    // Unknown type falls back to a low value.
    expect(armyTargetPriority("nonsense")).toBeLessThan(siege);
  });
});

describe("Conqueror AI plays a real game", () => {
  it("mounts an offensive and runs the focus-fire path without errors", () => {
    const seed = 1337;
    const map = generateMap("open_plains", seed, 2);
    const w = new World(seed);
    w.init(map, [{}, {}], [1, 1]);
    const hard = new SkirmishAI(w, Team.Enemy, DIFFICULTIES.conqueror);
    // Stage a ready army so the wave (and its focus-fire micro) triggers promptly
    // rather than waiting out a full economy ramp.
    const es = map.starts[Team.Enemy];
    for (let i = 0; i < 20; i++) {
      w.spawnUnit(Team.Enemy, "militia", es.x + (i % 5) * 16 - 32, es.y + Math.floor(i / 5) * 16);
    }

    let enemyAttacked = false;
    for (let i = 0; i < SIM_HZ * 150; i++) {
      w.tick();
      hard.update(SIM_DT);
      w.drainEvents();
      if (!enemyAttacked &&
        w.entitiesOf(Team.Enemy, Kind.Unit).some((e) => e.type !== "villager" &&
          (e.order.kind === OrderKind.Attack || e.order.kind === OrderKind.AttackMove))) {
        enemyAttacked = true;
      }
      if (w.winner !== null) break;
    }

    expect(enemyAttacked).toBe(true); // launched a wave (focus-fire path exercised)
    expect(w.player(Team.Enemy).defeated).toBe(false);
  }, 60000);
});
