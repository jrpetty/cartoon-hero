// perf.ts, with the AI measured as carefully as the world: the same headless
// AI-vs-AI match (Highlands seed 80808, Knights), reporting average, median,
// p95, p99 and worst for world.tick() *and* for the AIs' update() per tick,
// so the Godot port's benchmark (godot/tests/test_ai_perf.gd) has numbers to
// set itself against.
//
//   npx tsx scripts/ai_perf.ts [players] [minutes]

import { World } from "../src/sim/world";
import { Team } from "../src/sim/types";
import { generateMap } from "../src/maps/generator";
import { SkirmishAI } from "../src/ai/skirmish_ai";
import { DIFFICULTIES } from "../src/ai/difficulty";
import { SIM_DT, SIM_HZ } from "../src/content/balance";
import { worldChecksum } from "../src/sim/commands";

const N = Number(process.argv[2] ?? 8);
const MINUTES = Number(process.argv[3] ?? 12);
const seed = 80808;

const map = generateMap("highlands", seed, N);
const world = new World(seed);
world.init(map, Array.from({ length: N }, () => ({})), Array.from({ length: N }, () => 1));
const ais = Array.from({ length: N }, (_, t) => new SkirmishAI(world, t as Team, DIFFICULTIES.knight));

const ticks = SIM_HZ * 60 * MINUTES;
const tickS: number[] = [];
const aiS: number[] = [];
let done = ticks;
const wall = performance.now();
for (let i = 0; i < ticks; i++) {
  let a = performance.now();
  world.tick();
  tickS.push(performance.now() - a);
  a = performance.now();
  for (const ai of ais) ai.update(SIM_DT);
  aiS.push(performance.now() - a);
  world.drainEvents();
  if (world.winner !== null) { done = i + 1; break; }
}
const total = performance.now() - wall;

function stats(name: string, s: number[]) {
  const sum = s.reduce((x, y) => x + y, 0);
  const o = [...s].sort((x, y) => x - y);
  const q = (p: number) => o[Math.min(o.length - 1, Math.floor(o.length * p))];
  console.log(`${name.padEnd(12)} avg ${(sum / s.length).toFixed(3)}  median ${q(0.5).toFixed(3)}  p95 ${q(0.95).toFixed(3)}  p99 ${q(0.99).toFixed(3)}  worst ${o[o.length - 1].toFixed(2)}  (ms)`);
}
console.log(`${N} players, ${(done / SIM_HZ / 60).toFixed(1)} min simulated, ${world.entities.filter((e) => e.alive).length} entities at the end, checksum ${worldChecksum(world)}, wall ${(total / 1000).toFixed(1)}s`);
stats("world.tick", tickS);
stats("AI update", aiS);
stats("both", tickS.map((t, i) => t + aiS[i]));
