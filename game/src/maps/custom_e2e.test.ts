import { describe, expect, it, beforeAll } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as any).document = { createElement: () => createCanvas(1, 1) };
import { newCustomMap, toMapData, validateMap, hasErrors, saveCustomMap, findCustomMap, serialiseMap, deserialiseMap } from "./custom";
import { Terrain } from "./terrain_kinds";
import { TILE } from "../content/balance";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";
import { SIM_DT, SIM_HZ } from "../content/balance";
import { buildTerrainCache } from "../render/terrain";

beforeAll(() => {
  const store: Record<string, string> = {};
  (globalThis as any).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

/** Author a real 1v1 map the way the editor would: symmetric, with geography. */
function authored() {
  const N = 96;
  const m = newCustomMap("The Ford", N, "temperate");
  const set = (cx: number, cy: number, t: Terrain) => {
    if (cx < 0 || cy < 0 || cx >= N || cy >= N) return;
    m.terrain[cy * N + cx] = t;
    m.terrain[(N - 1 - cy) * N + (N - 1 - cx)] = t; // 180° symmetry, as the editor does
  };
  // A river down the middle with one ford.
  for (let cy = 0; cy < N; cy++) {
    if (Math.abs(cy - N / 2) < 6) continue;
    for (let w = -3; w <= 3; w++) set(N / 2 + w, cy, Terrain.Water);
  }
  // A mountain spur off each side, and a hill worth holding by the ford.
  for (let i = 0; i < 14; i++) set(20 + i, 30, Terrain.Rock);
  for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) {
    if (dx * dx + dy * dy <= 16) set(Math.round(N / 2) + dx - 8, Math.round(N / 2) + dy, Terrain.Hill);
  }
  // Woods around each base.
  for (let i = 0; i < 40; i++) {
    const a = (i / 40) * Math.PI * 2;
    set(20 + Math.round(Math.cos(a) * 9), 20 + Math.round(Math.sin(a) * 9), Terrain.Forest);
  }
  const cell = (cx: number, cy: number) => ({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 });
  m.spawns = [cell(20, 20), cell(N - 21, N - 21)];
  m.minPlayers = 2; m.maxPlayers = 2; m.modes = ["conquest"];
  for (const [sx, sy] of [[20, 20], [N - 21, N - 21]] as const) {
    for (let i = 0; i < 8; i++) m.resources.push({ type: "tree", ...cell(sx + 7, sy - 4 + i), amount: 160 });
    for (let i = 0; i < 6; i++) m.resources.push({ type: "berries", ...cell(sx - 6, sy - 3 + i), amount: 250 });
    m.resources.push({ type: "gold_mine", ...cell(sx + 4, sy + 7), amount: 1000 });
    m.resources.push({ type: "gold_mine", ...cell(sx + 5, sy + 8), amount: 1000 });
  }
  return m;
}

describe("A map made in the editor, played end to end", () => {
  it("validates clean", () => {
    const issues = validateMap(authored());
    expect(issues.filter((i) => i.level === "error"), JSON.stringify(issues)).toEqual([]);
  });

  it("saves, reloads by id, and survives a share code", () => {
    const m = authored();
    saveCustomMap(m);
    const back = findCustomMap(m.id)!;
    expect(back).not.toBeNull();
    expect([...back.terrain]).toEqual([...m.terrain]);
    const shared = deserialiseMap(serialiseMap(m))!;
    expect([...shared.terrain]).toEqual([...m.terrain]);
    expect(shared.spawns).toEqual(m.spawns);
  });

  it("renders through the real terrain painter", () => {
    const md = toMapData(authored(), 1, 2);
    const c = buildTerrainCache(md) as any;
    expect(c.width).toBeGreaterThan(0);
  });

  it("runs a real AI match on it — economies grow and the sides meet", () => {
    const md = toMapData(authored(), 4242, 2);
    const world = new World(4242);
    world.init(md, [{}, {}], [1, 1], [0, 1]);
    const ais = [
      new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
      new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
    ];
    for (let i = 0; i < SIM_HZ * 60 * 10; i++) {
      world.tick();
      for (const ai of ais) ai.update(SIM_DT);
      world.drainEvents();
      if (world.winner !== null) break;
    }
    // Both realms found their resources and built from them.
    for (const t of [Team.Player, Team.Enemy]) {
      expect(world.player(t).stats.gathered, `team ${t} gathered nothing`).toBeGreaterThan(600);
    }
    // And they reached each other across the ford, rather than sitting behind
    // a river they could not cross.
    const kills = world.player(Team.Player).stats.unitsKilled + world.player(Team.Enemy).stats.unitsKilled;
    expect(kills, "the two sides never met").toBeGreaterThan(0);
  }, 180000);

  it("plays as a nomad map too, landing everyone somewhere legal", () => {
    const m = authored();
    m.nomad = "optional";
    const md = toMapData(m, 77, 2, true);
    const world = new World(77);
    world.init(md, [{}, {}], [1, 1], [0, 1], undefined, true);
    for (let i = 0; i < SIM_HZ * 60; i++) { world.tick(); world.drainEvents(); }
    // Everyone got villagers on the ground rather than inside a mountain.
    for (const t of [Team.Player, Team.Enemy]) {
      expect(world.entitiesOf(t, 0).length).toBeGreaterThan(0);
    }
  }, 60000);
});
