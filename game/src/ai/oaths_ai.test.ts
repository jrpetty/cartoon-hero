import { describe, expect, it } from "vitest";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { generateMap } from "../maps/generator";
import { SkirmishAI } from "./skirmish_ai";
import { DIFFICULTIES } from "./difficulty";
import { SIM_DT, SIM_HZ } from "../content/balance";
import { OATHS } from "../content/oaths";
import { UNITS } from "../content/units";

/**
 * The AI and the late game.
 *
 * Measured before any of this: across nine AI-vs-AI duels, not one AI reached
 * the third age in thirty minutes — most sat in the second with a thousand
 * gold banked and no food. Three things were wrong, and none was about Oaths:
 * villagers walked to the nearest berry bush on the whole map before trying a
 * farm; several were sent to the same farm, which only one can work; and a
 * Town Centre hemmed in by its own buildings could leave its nearest open cell
 * sealed off, so food-carriers heading home stood still for the rest of the
 * match. With those fixed, the same nine seeds reach the Empire Age.
 */

function play(preset: string, seed: number, minutes: number, onMinute?: (w: World, m: number) => void) {
  const w = new World(seed);
  w.init(generateMap(preset, seed, 2), [{}, {}], [1, 1]);
  const ais = [0, 1].map((t) => new SkirmishAI(w, t as Team, DIFFICULTIES.lord));
  for (let i = 1; i <= SIM_HZ * 60 * minutes; i++) {
    w.tick();
    for (const a of ais) a.update(SIM_DT);
    w.drainEvents();
    if (i % (SIM_HZ * 60) === 0) onMinute?.(w, i / (SIM_HZ * 60));
    if (w.winner !== null) break;
  }
  return w;
}

describe("The AI in the later ages", () => {
  it("never lets food income stall while it is still standing", () => {
    // Highlands seed 6: before the fixes, one side gathered no food at all from
    // minute seven to minute nineteen.
    const food: number[][] = [[], []];
    const w = play("highlands", 6, 16, (w) => {
      for (const t of [0, 1]) food[t].push(w.player(t as Team).stats.gatheredBy.food);
    });
    for (const t of [0, 1]) {
      if (w.player(t as Team).defeated) continue;
      for (let m = 6; m + 3 < food[t].length; m++) {
        const got = food[t][m + 3] - food[t][m];
        expect(got, `team ${t} gathered ${got} food between minutes ${m + 1} and ${m + 4}`).toBeGreaterThan(250);
      }
    }
  }, 120000);

  it("swears a real Oath at every advance, and reaches the Empire Age", () => {
    const w = play("riverlands", 8, 24);
    const ages = [0, 1].map((t) => w.player(t as Team).age);
    expect(Math.max(...ages), "neither AI reached the Empire Age").toBe(3);
    for (const t of [0, 1]) {
      const p = w.player(t as Team);
      expect(p.oaths, `team ${t} advanced without swearing`).toHaveLength(p.age);
      p.oaths.forEach((o, i) => expect(OATHS[o]?.age, `${o} sworn at the wrong age`).toBe(i + 1));
      // A martial Oath's unit actually gets fielded.
      for (const o of p.oaths) {
        const unit = OATHS[o].unit;
        if (!unit || UNITS[unit].age > 2) continue;
        expect(p.stats.trainedByType[unit] ?? 0, `swore ${o} and never trained a ${unit}`).toBeGreaterThan(0);
      }
    }
  }, 180000);
});
