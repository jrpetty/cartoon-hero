// Headless performance harness for the main game mode.
//
//   npx tsx scripts/perf.ts [players] [minutes]
//
// Plays a full AI-vs-AI match with no rendering and reports what a tick costs.
// The percentiles are the number that matters: a sim whose *average* tick is
// comfortable can still stutter badly if one tick in a hundred blows through
// the frame budget, and that is what a busy eight-player match feels like.

import { World } from "../src/sim/world";
import { Team } from "../src/sim/types";
import { generateMap } from "../src/maps/generator";
import { SkirmishAI } from "../src/ai/skirmish_ai";
import { DIFFICULTIES } from "../src/ai/difficulty";
import { SIM_DT, SIM_HZ } from "../src/content/balance";

const N = Number(process.argv[2] ?? 8);
const MINUTES = Number(process.argv[3] ?? 12);
const seed = 80808;

const map = generateMap("highlands", seed, N);
const world = new World(seed);
world.init(map, Array.from({ length: N }, () => ({})), Array.from({ length: N }, () => 1));
const ais = Array.from({ length: N }, (_, t) => new SkirmishAI(world, t as Team, DIFFICULTIES.knight));

const ticks = SIM_HZ * 60 * MINUTES;
const samples: number[] = [];
let tickMs = 0, aiMs = 0, done = ticks;
const wall = performance.now();
for (let i = 0; i < ticks; i++) {
  let a = performance.now();
  world.tick();
  const one = performance.now() - a;
  tickMs += one; samples.push(one);
  a = performance.now();
  for (const ai of ais) ai.update(SIM_DT);
  aiMs += performance.now() - a;
  world.drainEvents();
  if (world.winner !== null) { done = i + 1; break; }
}
const total = performance.now() - wall;
const alive = world.entities.filter((e) => e.alive).length;
samples.sort((x, y) => x - y);
const q = (p: number) => samples[Math.min(samples.length - 1, Math.floor(samples.length * p))];
const budget = 1000 / SIM_HZ;

console.log(`${N} players, ${(done / SIM_HZ / 60).toFixed(1)} min simulated, ${alive} entities at the end`);
console.log(`wall ${(total / 1000).toFixed(1)}s  |  world.tick ${(tickMs / done).toFixed(3)}ms avg  |  ai ${(aiMs / done).toFixed(3)}ms avg`);
console.log(`tick  median ${q(0.5).toFixed(2)}  p95 ${q(0.95).toFixed(2)}  p99 ${q(0.99).toFixed(2)}  p99.9 ${q(0.999).toFixed(2)}  worst ${samples[samples.length - 1].toFixed(2)}  (budget ${budget.toFixed(0)}ms)`);
const over = samples.filter((s) => s > budget).length;
console.log(`ticks over budget: ${over} (${((over / done) * 100).toFixed(2)}%)`);
