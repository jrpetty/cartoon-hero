import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as any).document = { createElement: () => createCanvas(1, 1) };
import { newCustomMap, toMapData, MAP_SIZES } from "./custom";
import { Terrain } from "./terrain_kinds";
import { TILE, SIM_DT, SIM_HZ } from "../content/balance";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";
import { buildTerrainCache } from "../render/terrain";

/** A playable map at any size: spawns spaced across the diagonal, with wood. */
function sized(cols: number, players: number) {
  const m = newCustomMap(`Size ${cols}`, cols);
  m.maxPlayers = players; m.minPlayers = players;
  const cell = (cx: number, cy: number) => ({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 });
  const inset = Math.max(6, Math.floor(cols * 0.12));
  for (let i = 0; i < players; i++) {
    const a = (i / players) * Math.PI * 2;
    const cx = Math.round(cols / 2 + Math.cos(a) * (cols / 2 - inset));
    const cy = Math.round(cols / 2 + Math.sin(a) * (cols / 2 - inset));
    m.spawns.push(cell(cx, cy));
    for (let k = 0; k < 8; k++) m.resources.push({ type: "tree", ...cell(cx + 4, cy - 4 + k), amount: 160 });
    for (let k = 0; k < 5; k++) m.resources.push({ type: "berries", ...cell(cx - 4, cy - 2 + k), amount: 250 });
    m.resources.push({ type: "gold_mine", ...cell(cx + 3, cy + 5), amount: 1000 });
  }
  return m;
}

describe("Every map size actually plays", () => {
  it("runs a match on the smallest map there is", () => {
    const md = toMapData(sized(MAP_SIZES[0].cols, 2), 11, 2);
    const world = new World(11);
    world.init(md, [{}, {}], [1, 1], [0, 1]);
    const ais = [
      new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
      new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
    ];
    for (let i = 0; i < SIM_HZ * 60 * 5; i++) {
      world.tick();
      for (const ai of ais) ai.update(SIM_DT);
      world.drainEvents();
      if (world.winner !== null) break;
    }
    for (const t of [Team.Player, Team.Enemy]) {
      expect(world.player(t).stats.gathered, `team ${t} on a duel map`).toBeGreaterThan(200);
    }
  }, 120000);

  it("runs a match on the largest map there is, with a full lobby", () => {
    const cols = MAP_SIZES[MAP_SIZES.length - 1].cols;
    const md = toMapData(sized(cols, 8), 22, 8);
    expect(md.starts.length).toBe(8);
    const world = new World(22);
    world.init(md, Array.from({ length: 8 }, () => ({})), Array.from({ length: 8 }, () => 1));
    const ais = Array.from({ length: 8 }, (_, t) => new SkirmishAI(world, t as Team, DIFFICULTIES.knight));
    for (let i = 0; i < SIM_HZ * 60 * 4; i++) {
      world.tick();
      for (const ai of ais) ai.update(SIM_DT);
      world.drainEvents();
      if (world.winner !== null) break;
    }
    for (let t = 0; t < 8; t++) {
      expect(world.player(t as Team).stats.gathered, `realm ${t} on a colossal map`).toBeGreaterThan(200);
    }
  }, 180000);

  it("bakes a terrain cache the browser would accept at every size", () => {
    for (const size of MAP_SIZES) {
      const m = newCustomMap("x", size.cols);
      m.terrain[0] = Terrain.Water; // give the painter something to shade
      const c = buildTerrainCache(toMapData(m, 1, 1)) as unknown as { width: number; height: number };
      expect(c.width, `${size.label} width`).toBeGreaterThan(0);
      expect(Math.max(c.width, c.height), `${size.label} is ${c.width}px`).toBeLessThanOrEqual(4096);
    }
  }, 120000);
});
