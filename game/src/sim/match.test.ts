// Headless smoke match: two skirmish AIs fight on a generated map. This
// exercises the entire stack — economy, construction, pathing, production,
// combat, fog, victory — with no rendering. The strongest regression net we
// have for "the game actually plays".

import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Kind, Team } from "./types";
import { generateMap } from "../maps/generator";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";
import { SIM_DT, SIM_HZ } from "../content/balance";

describe("AI vs AI smoke match", () => {
  it("two Knight AIs develop economies and armies and fight", () => {
    const seed = 20260612;
    const map = generateMap("open_plains", seed);
    const world = new World(seed);
    world.init(map, [{}, {}], [1, 1]);
    const ais = [
      new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
      new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
    ];

    const MINUTES = 14;
    const ticks = SIM_HZ * 60 * MINUTES;
    for (let i = 0; i < ticks; i++) {
      world.tick();
      for (const ai of ais) ai.update(SIM_DT);
      world.drainEvents(); // keep the queue from hitting its cap
      if (world.winner !== null) break;
    }

    for (const team of [Team.Player, Team.Enemy]) {
      const p = world.player(team);
      const lost = p.defeated;
      // Economy ran: significant resources gathered.
      expect(p.stats.gathered).toBeGreaterThan(800);
      if (!lost) {
        // Villagers were trained beyond the starting three.
        const vills = world.countOf(team, "villager");
        expect(vills + p.stats.unitsLost).toBeGreaterThan(5);
        // Build order progressed into military production.
        const buildings = world.entitiesOf(team, Kind.Building);
        expect(buildings.length).toBeGreaterThan(2);
      }
    }

    // The war happened: somebody killed something.
    const totalKills =
      world.player(Team.Player).stats.unitsKilled + world.player(Team.Enemy).stats.unitsKilled;
    expect(totalKills).toBeGreaterThan(3);

    // At least one side should have advanced an age in 14 minutes.
    const maxAge = Math.max(world.player(Team.Player).age, world.player(Team.Enemy).age);
    expect(maxAge).toBeGreaterThanOrEqual(1);
  }, 120000);

  it("a Warlord AI crushes a Squire AI", () => {
    const seed = 5150;
    const map = generateMap("open_plains", seed);
    const world = new World(seed);
    world.init(map, [{}, {}], [DIFFICULTIES.squire.econMult, DIFFICULTIES.warlord.econMult]);
    const ais = [
      new SkirmishAI(world, Team.Player, DIFFICULTIES.squire),
      new SkirmishAI(world, Team.Enemy, DIFFICULTIES.warlord),
    ];

    const ticks = SIM_HZ * 60 * 25;
    for (let i = 0; i < ticks; i++) {
      world.tick();
      for (const ai of ais) ai.update(SIM_DT);
      world.drainEvents();
      if (world.winner !== null) break;
    }
    // The Warlord should win — or at minimum be far ahead on kills if the
    // 25-minute cap hits first.
    if (world.winner !== null) {
      expect(world.winner).toBe(Team.Enemy);
    } else {
      const squire = world.player(Team.Player);
      const warlord = world.player(Team.Enemy);
      expect(warlord.stats.unitsKilled).toBeGreaterThan(squire.stats.unitsKilled);
    }
  }, 180000);
});
