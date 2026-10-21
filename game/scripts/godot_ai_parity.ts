// Fixtures for the Godot port's skirmish-AI parity tests: what the TypeScript
// AI does, tick by tick, in matches that reach the parts of it the two basic
// fixtures in godot_parity.ts (sim_ai2 / sim_ai4) never touch — eight seats,
// alliances and pooled intel, Regicide, Survival, nomad starts, factions,
// commanders' banners, a Nemesis rival's adaptation, a terrain-blind brain and
// a ford worth bridging.
//
//   npx tsx scripts/godot_ai_parity.ts [name ...]
//
// Writes godot/tests/fixtures/ai_<name>.json: the setup, the TS worldChecksum
// after every tick, an AI digest after every tick (each brain's RNG state and
// a few decisions — it diverges the moment the port makes a different choice,
// often long before the world shows it), and snapshots of the world and of
// every brain's state now and then, which say *what* went wrong.

import fs from "fs";
import path from "path";
import { World } from "../src/sim/world";
import { GameMode, Team } from "../src/sim/types";
import { generateMap } from "../src/maps/generator";
import { Terrain } from "../src/maps/terrain_kinds";
import { SkirmishAI, RivalAdaptation } from "../src/ai/skirmish_ai";
import { DIFFICULTIES } from "../src/ai/difficulty";
import { SIM_DT, SIM_HZ } from "../src/content/balance";
import { worldChecksum } from "../src/sim/commands";

const OUT = path.resolve("../godot/tests/fixtures");
fs.mkdirSync(OUT, { recursive: true });

export interface AIScenario {
  name: string;
  preset: string;
  seed: number;
  players: number;
  minutes: number;
  /** Difficulty id per seat; null = no brain (a Survival horde). */
  diffs: (string | null)[];
  /** Economy multipliers from each seat's difficulty, as main.ts does (else 1). */
  econFromDiff?: boolean;
  alliances?: number[];
  commanders?: string[];
  factions?: string[];
  nomad?: boolean;
  mode?: GameMode;
  /** Rival adaptations by seat. */
  adapt?: Record<number, RivalAdaptation>;
  /** Seats whose brain does not read the ground. */
  blind?: number[];
  /** Paint a band of shallows down the middle of the map (terrain_sense.test.ts `acrossAFord`). */
  ford?: number;
  snapEvery: number;
}

export const SCENARIOS: AIScenario[] = [
  // The perf.ts match: eight Knights on Highlands for twelve minutes.
  { name: "highlands8", preset: "highlands", seed: 80808, players: 8, minutes: 12, diffs: Array(8).fill("knight"), snapEvery: 2400 },
  // 2v2 with mixed tiers, factions and commanders: alliances, pooled intel,
  // focus enemy, relief columns, callouts, banners.
  {
    name: "teams", preset: "riverlands", seed: 23, players: 4, minutes: 10,
    diffs: ["warlord", "conqueror", "lord", "warlord"], econFromDiff: true, alliances: [0, 1, 0, 1],
    commanders: ["banneret", "warpriest", "warden", "marshal"], factions: ["kingdom", "legion", "khanate", "norse"],
    snapEvery: 1200,
  },
  // Regicide: bodyguards, King hunts.
  {
    name: "regicide", preset: "open_plains", seed: 7, players: 2, minutes: 8, mode: "regicide",
    diffs: ["conqueror", "lord"], econFromDiff: true, factions: ["shogunate", "ascendancy"],
    commanders: ["warpriest", "banneret"], snapEvery: 1200,
  },
  // Nomad: no Town Centre — scout a site and found one.
  { name: "nomad", preset: "open_plains", seed: 3, players: 2, minutes: 6, nomad: true, diffs: ["lord", "knight"], alliances: [0, 1], snapEvery: 1200 },
  // A Nemesis rival (turtle, expecting cavalry, walls up) against a blind Squire.
  {
    name: "rival", preset: "black_forest", seed: 11, players: 2, minutes: 8, diffs: ["squire", "knight"], econFromDiff: true,
    adapt: { 1: { style: "turtle", expect: { cavalry: 6, archer: 2 }, walls: true } }, blind: [0], snapEvery: 1200,
  },
  // Survival: three allied brains against the horde.
  {
    name: "survival", preset: "open_plains", seed: 7, players: 4, minutes: 8, mode: "survival",
    diffs: ["lord", "knight", "warlord", null], alliances: [0, 0, 0, 1], snapEvery: 1200,
  },
  // A ford across the middle: bridges, and raiders burning them.
  { name: "ford", preset: "open_plains", seed: 4, players: 2, minutes: 14, diffs: ["knight", "lord"], alliances: [0, 1], ford: 3, snapEvery: 2400 },
];

function fnvMix(h: number, n: number): number {
  for (let i = 0; i < 4; i++) {
    h = (h ^ ((n >>> (i * 8)) & 0xff)) >>> 0;
    h = Math.imul(h, 16777619) >>> 0;
  }
  return h;
}

type AnyAI = Record<string, any>;

/** A digest of every brain's hidden state: diverges on the first different decision. */
export function aiChecksum(world: World, ais: SkirmishAI[]): number {
  let h = 2166136261 >>> 0;
  h = fnvMix(h, (world.rng as unknown as AnyAI).state >>> 0);
  for (const ai of ais as unknown as AnyAI[]) {
    h = fnvMix(h, ai.rng.state >>> 0);
    h = fnvMix(h, (ai.attacking ? 1 : 0) | (ai.savingForAge ? 2 : 0) | (ai.wallsPlanned ? 4 : 0));
    h = fnvMix(h, ai.scoutId);
    h = fnvMix(h, ai.lastFocusId);
    h = fnvMix(h, ai.seenWalls);
    h = fnvMix(h, Math.round(ai.lastWaveTime * 1000));
  }
  return h >>> 0;
}

export function aiState(ai: SkirmishAI) {
  const a = ai as unknown as AnyAI;
  return {
    team: a.team, style: a.style, timer: a.timer, gameTime: a.gameTime, rng: a.rng.state >>> 0,
    seen: { ...a.seen }, seenWalls: a.seenWalls, attacking: a.attacking, savingForAge: a.savingForAge,
    saveStartedAt: a.saveStartedAt, saveGaveUpAt: a.saveGaveUpAt, ageReadySince: a.ageReadySince,
    lastWaveTime: a.lastWaveTime, lastHarassTime: a.lastHarassTime, lastBridgeTime: a.lastBridgeTime,
    lastTradeTime: a.lastTradeTime, lastScoutTime: a.lastScoutTime, lastRetreatTime: a.lastRetreatTime,
    lastAllyHelpTime: a.lastAllyHelpTime, lastPressTime: a.lastPressTime, lastCalloutTime: a.lastCalloutTime,
    scoutId: a.scoutId, lastFocusId: a.lastFocusId, wallsPlanned: a.wallsPlanned,
    everBuilt: [...a.everBuilt], oathChoice: { ...a.oathChoice },
  };
}

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

/** Build the scenario's world and brains exactly as the Godot test does. */
export function setup(s: AIScenario) {
  const map = generateMap(s.preset, s.seed, s.players, !!s.nomad, s.alliances);
  const w = new World(s.seed);
  const econ = s.diffs.map((d) => (s.econFromDiff && d ? DIFFICULTIES[d].econMult : 1));
  w.init(map, Array.from({ length: s.players }, () => ({})), econ, s.alliances, s.commanders, !!s.nomad, undefined, s.mode ?? "conquest", s.factions);
  if (s.ford) {
    const midC = Math.floor(w.terrainCols / 2);
    for (let cy = 0; cy < w.terrainRows; cy++) {
      for (let dx = -s.ford; dx <= s.ford; dx++) {
        w.terrain[cy * w.terrainCols + midC + dx] = Terrain.Shallow;
        w.grid.setBlocked(midC + dx, cy, false);
      }
    }
  }
  const ais: SkirmishAI[] = [];
  s.diffs.forEach((d, t) => {
    if (!d) return;
    const ai = new SkirmishAI(w, t as Team, DIFFICULTIES[d]);
    const a = s.adapt?.[t];
    if (a) ai.adapt(a);
    if (s.blind?.includes(t)) ai.readsGround = false;
    ais.push(ai);
  });
  return { w, ais };
}

function run(s: AIScenario) {
  const { w, ais } = setup(s);
  const sums: number[] = [worldChecksum(w)];
  const aiSums: number[] = [aiChecksum(w, ais)];
  const snaps = [{ ...snapshot(w), ais: ais.map(aiState) }];
  const ticks = SIM_HZ * 60 * s.minutes;
  const callouts: string[] = [];
  for (let i = 0; i < ticks; i++) {
    w.tick();
    for (const ai of ais) ai.update(SIM_DT);
    for (const ev of w.drainEvents()) if (ev.kind === "callout") callouts.push(String(ev.data));
    sums.push(worldChecksum(w));
    aiSums.push(aiChecksum(w, ais));
    if ((i + 1) % s.snapEvery === 0 || w.winner !== null) snaps.push({ ...snapshot(w), ais: ais.map(aiState) });
    if (w.winner !== null) break;
  }
  const file = `ai_${s.name}.json`;
  fs.writeFileSync(path.join(OUT, file), JSON.stringify({ ...s, ticks, checksums: sums, aiSums, snapshots: snaps, callouts }));
  const count = (type: string) => w.entities.filter((e) => e.type === type).length;
  console.log(`  ${file} ${(fs.statSync(path.join(OUT, file)).size / 1024).toFixed(0)} KB — ${sums.length - 1} ticks, winner ${w.winner}, `
    + `ages ${Array.from({ length: w.numTeams }, (_, t) => w.player(t as Team).age).join("/")}, `
    + `kills ${Array.from({ length: w.numTeams }, (_, t) => w.player(t as Team).stats.unitsKilled).join("/")}, `
    + `bridges ${count("bridge")}, walls ${count("stone_wall")}, markets ${count("market")}, carts ${count("trade_cart")}, `
    + `callouts ${callouts.length}, styles ${ais.map((a) => (a as unknown as AnyAI).style).join("/")}`);
}

const only = process.argv.slice(2);
for (const s of SCENARIOS) if (!only.length || only.includes(s.name)) run(s);
