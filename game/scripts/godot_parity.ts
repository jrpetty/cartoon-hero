// Fixtures for the Godot port's parity tests: what the TypeScript build does,
// tick by tick, so the GDScript build can be checked against it.
//
//   npx tsx scripts/godot_parity.ts
//
// Writes godot/tests/fixtures/*.json. The Godot tests replay the same setups
// and compare: generated maps field by field, and the simulation by
// `worldChecksum` every tick (plus full snapshots now and then, which say
// *what* diverged when a checksum stops matching).

import fs from "fs";
import path from "path";
import { World } from "../src/sim/world";
import { Team, Kind } from "../src/sim/types";
import { generateMap, MapData } from "../src/maps/generator";
import { SkirmishAI } from "../src/ai/skirmish_ai";
import { DIFFICULTIES } from "../src/ai/difficulty";
import { SIM_DT } from "../src/content/balance";
import { applyCommand, worldChecksum, Command } from "../src/sim/commands";

const OUT = path.resolve("../godot/tests/fixtures");
fs.mkdirSync(OUT, { recursive: true });
const write = (name: string, v: unknown) => {
  fs.writeFileSync(path.join(OUT, name), JSON.stringify(v));
  console.log(`  ${name} ${(fs.statSync(path.join(OUT, name)).size / 1024).toFixed(0)} KB`);
};

function fnv(bytes: ArrayLike<number>): number {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < bytes.length; i++) { h ^= bytes[i] & 0xff; h = Math.imul(h, 16777619) >>> 0; }
  return h >>> 0;
}

function mapJson(m: MapData) {
  return { ...m, terrain: Array.from(m.terrain), terrainHash: fnv(m.terrain) };
}

// ---- maps ----
const MAP_CASES: [string, number, number][] = [
  ["open_plains", 7, 2], ["black_forest", 11, 2], ["riverlands", 23, 4], ["highlands", 80808, 8],
  ["islands", 5, 2], ["crossroads", 99, 3], ["gauntlet", 4242, 2],
];
write("maps.json", MAP_CASES.map(([id, seed, n]) => ({ id, seed, n, map: mapJson(generateMap(id, seed, n)) })));

// ---- simulation ----
function snapshot(w: World) {
  return {
    tick: w.tickCount,
    entities: w.entities.filter((e) => e.alive).map((e) => ({
      id: e.id, type: e.type, team: e.team, kind: e.kind, x: e.x, y: e.y, hp: e.hp,
      order: e.order.kind, target: e.order.target, carry: e.carry, q: e.productionQueue.length,
    })),
    players: Array.from({ length: w.numTeams }, (_, t) => {
      const p = w.player(t as Team);
      return { food: p.resources.food, wood: p.resources.wood, gold: p.resources.gold, pop: p.popUsed, age: p.age };
    }),
  };
}

interface Scenario { name: string; preset: string; seed: number; players: number; ticks: number; ai: string | null; commands?: [number, Command][]; snapEvery: number }

function run(s: Scenario) {
  const map = generateMap(s.preset, s.seed, s.players);
  const w = new World(s.seed);
  w.init(map, Array.from({ length: s.players }, () => ({})), Array.from({ length: s.players }, () => 1));
  const ais = s.ai ? Array.from({ length: s.players }, (_, t) => new SkirmishAI(w, t as Team, DIFFICULTIES[s.ai!])) : [];
  const sums: number[] = [worldChecksum(w)];
  const snaps = [snapshot(w)];
  const cmds = [...(s.commands ?? [])];
  for (let i = 0; i < s.ticks; i++) {
    while (cmds.length && cmds[0][0] === i) applyCommand(w, cmds.shift()![1]);
    w.tick();
    for (const ai of ais) ai.update(SIM_DT);
    w.drainEvents();
    sums.push(worldChecksum(w));
    if ((i + 1) % s.snapEvery === 0) snaps.push(snapshot(w));
    if (w.winner !== null) break;
  }
  write(`sim_${s.name}.json`, { ...s, checksums: sums, snapshots: snaps });
}

// Commands need real ids: build the same world once to find them.
function idsFor(preset: string, seed: number, players: number) {
  const w = new World(seed);
  w.init(generateMap(preset, seed, players), Array.from({ length: players }, () => ({})), Array.from({ length: players }, () => 1));
  const mine = (type: string) => w.entities.filter((e) => e.alive && e.team === Team.Player && e.type === type);
  const tc = mine("town_center")[0];
  const vills = mine("villager").map((e) => e.id);
  const near = (type: string) => w.entities.filter((e) => e.alive && e.kind === Kind.Resource && e.type === type)
    .sort((a, b) => Math.hypot(a.x - tc.x, a.y - tc.y) - Math.hypot(b.x - tc.x, b.y - tc.y))[0];
  return { tc, vills, tree: near("tree"), berries: near("berries"), gold: near("gold_mine") };
}

const ids = idsFor("open_plains", 7, 2);
run({ name: "idle", preset: "open_plains", seed: 7, players: 2, ticks: 200, ai: null, snapEvery: 50 });
run({
  name: "commands", preset: "open_plains", seed: 7, players: 2, ticks: 1800, ai: null, snapEvery: 100,
  commands: [
    [0, { t: "gather", team: Team.Player, ids: ids.vills.slice(0, 1), node: ids.tree?.id ?? -1, queue: false }],
    [0, { t: "gather", team: Team.Player, ids: ids.vills.slice(1, 2), node: ids.berries?.id ?? -1, queue: false }],
    [5, { t: "train", team: Team.Player, buildingId: ids.tc.id, unit: "villager" }],
    [6, { t: "train", team: Team.Player, buildingId: ids.tc.id, unit: "villager" }],
    [40, { t: "place", team: Team.Player, building: "house", x: ids.tc.x + 160, y: ids.tc.y + 40, builders: ids.vills.slice(2, 3) }],
    [300, { t: "move", team: Team.Player, ids: ids.vills.slice(0, 3), x: ids.tc.x - 200, y: ids.tc.y, queue: false, attackMove: false }],
    [600, { t: "gather", team: Team.Player, ids: ids.vills.slice(0, 3), node: ids.gold?.id ?? -1, queue: false }],
  ],
});
run({ name: "ai2", preset: "open_plains", seed: 7, players: 2, ticks: 20 * 60 * 6, ai: "knight", snapEvery: 200 });
run({ name: "ai4", preset: "highlands", seed: 80808, players: 4, ticks: 20 * 60 * 4, ai: "lord", snapEvery: 400 });
