// More parity fixtures for the Godot port's simulation: scenarios that reach
// the parts of world.ts the basic godot_parity.ts runs never touch — combat
// and projectiles, flow-field formations, abilities, farms and re-seeding,
// ages and Oaths, factions/commanders/boons, banners, the game modes, trade,
// bridges and gates, a nomad start.
//
//   npx tsx scripts/godot_world_parity.ts
//
// Writes godot/tests/fixtures/world_<name>.json. Each scenario's script runs
// here against the live TS world and its decisions are recorded as concrete
// ops (Commands, plus a few harness ops: _spawn, _building, _res, _set,
// _player, _world, _terrain, _damage), so the Godot test only replays them.
// Recorded per tick: worldChecksum, and `xhash` — a hash over far more state
// (every entity field, each realm's ledger, fog, the events drained) — plus a
// full snapshot every `snapEvery` ticks to say what differs.

import fs from "fs";
import path from "path";
import { World, WorldEvent } from "../src/sim/world";
import { Entity, Kind, Team } from "../src/sim/types";
import { generateMap } from "../src/maps/generator";
import { applyCommand, worldChecksum, Command } from "../src/sim/commands";
import { TERRAIN_BLOCKS } from "../src/maps/terrain_kinds";

const OUT = path.resolve("../godot/tests/fixtures");
fs.mkdirSync(OUT, { recursive: true });

type Op =
  | Command
  | { t: "_spawn"; team: number; type: string; x: number; y: number }
  | { t: "_building"; team: number; type: string; x: number; y: number; done: boolean }
  | { t: "_res"; team: number; food: number; wood: number; gold: number }
  | { t: "_set"; id: number; field: string; value: number }
  | { t: "_player"; team: number; field: string; value: number }
  | { t: "_world"; field: string; value: number }
  | { t: "_terrain"; cells: [number, number, number][] }
  | { t: "_damage"; team: number; target: number; amount: number; source: string };

function applyOp(w: World, op: Op) {
  switch (op.t) {
    case "_spawn": w.spawnUnit(op.team as Team, op.type, op.x, op.y); break;
    case "_building": w.spawnBuilding(op.team as Team, op.type, op.x, op.y, op.done); break;
    case "_res": w.player(op.team as Team).resources = { food: op.food, wood: op.wood, gold: op.gold }; break;
    case "_set": { const e = w.byId.get(op.id); if (e) (e as any)[op.field] = op.value; break; }
    case "_player": (w.player(op.team as Team) as any)[op.field] = op.value; break;
    case "_world": (w as any)[op.field] = op.value; break;
    case "_terrain":
      for (const [c, r, v] of op.cells) {
        w.terrain[r * w.terrainCols + c] = v;
        w.grid.setBlocked(c, r, TERRAIN_BLOCKS[v] === 1);
      }
      break;
    case "_damage": { const e = w.byId.get(op.target); if (e) w.dealDamage(op.team as Team, e, op.amount, op.source); break; }
    default: applyCommand(w, op as Command);
  }
}

// ---- the extended hash -------------------------------------------------
function mix(acc: number, n: number): number {
  acc = (acc ^ (n | 0)) >>> 0;
  return Math.imul(acc, 16777619) >>> 0;
}
class H {
  h = 2166136261 >>> 0;
  i(v: number) { this.h = mix(this.h, v); }
  n(v: number) { this.h = mix(this.h, Math.round(v * 1000)); }
  b(v: boolean) { this.i(v ? 1 : 0); }
  s(v: string) { this.i(v.length); for (let k = 0; k < v.length; k++) this.i(v.charCodeAt(k)); }
  v(v: unknown) {
    if (v === null || v === undefined) this.i(-7);
    else if (typeof v === "number") this.n(v);
    else if (typeof v === "string") this.s(v);
    else if (typeof v === "boolean") this.b(v);
    else if (Array.isArray(v)) { this.i(v.length); for (const x of v) this.v(x); }
    else if (v instanceof Set) { this.i(v.size); for (const x of v) this.v(x); }
    else if (typeof v === "object") {
      const ks = Object.keys(v as object);
      this.i(ks.length);
      for (const k of ks) { this.s(k); this.v((v as Record<string, unknown>)[k]); }
    }
  }
}

function entityRecord(e: Entity): Record<string, unknown> {
  if (e.kind === Kind.Resource) return { id: e.id, alive: e.alive, amount: e.amount };
  return {
    id: e.id, alive: e.alive, type: e.type, team: e.team, kind: e.kind,
    x: e.x, y: e.y, hp: e.hp, maxHp: e.maxHp, vx: e.vx, vy: e.vy, speed: e.speed, attack: e.attack, range: e.range,
    armor: e.armor, pierceArmor: e.pierceArmor, visionRange: e.visionRange, attackInterval: e.attackInterval, radius: e.radius,
    order: e.order.kind, target: e.order.target, tx: e.order.tx, ty: e.order.ty,
    queue: (e.order.queue ?? []).map((q) => [q.kind, q.target, q.tx, q.ty]),
    stance: e.stance, path: e.path ? e.path.length : -1, pathIndex: e.pathIndex, repathCooldown: e.repathCooldown,
    carry: e.carry, carryKind: e.carryKind, gatherCooldown: e.gatherCooldown, amount: e.amount,
    buildState: e.buildState, buildProgress: e.buildProgress, popProvided: e.popProvided, rallyX: e.rallyX, rallyY: e.rallyY,
    productionQueue: [...e.productionQueue], productionTime: e.productionTime, garrison: [...e.garrison],
    gateOpen: e.gateOpen, gateForce: e.gateForce, farmWorker: e.farmWorker,
    projTargetId: e.projTargetId, projSourceId: e.projSourceId, projDamage: e.projDamage, projElapsed: e.projElapsed, projDuration: e.projDuration,
    attackCooldown: e.attackCooldown, abilityCooldown: e.abilityCooldown, abilityActive: e.abilityActive,
    slowTimer: e.slowTimer, rallyTimer: e.rallyTimer, guardTimer: e.guardTimer, tradeHomeId: e.tradeHomeId,
    spottedBy: e.spottedBy, heroLevel: e.heroLevel, heroKills: e.heroKills, veterancy: e.veterancy, vetKills: e.vetKills,
    chargeRun: e.chargeRun, lastDamageTime: e.lastDamageTime, lastAttackerId: e.lastAttackerId, variantRarity: e.variantRarity,
    facing: e.facing, animPhase: e.animPhase, hitFlash: e.hitFlash,
  };
}
// Which entity fields are ints (hashed raw) rather than numbers (hashed x1000).
const INT_FIELDS = new Set(["id", "team", "kind", "order", "target", "stance", "path", "pathIndex", "buildState", "gateForce", "farmWorker",
  "projTargetId", "projSourceId", "tradeHomeId", "spottedBy", "heroLevel", "heroKills", "veterancy", "vetKills", "lastAttackerId", "variantRarity"]);

function playerRecord(w: World, t: number): Record<string, unknown> {
  const p = w.player(t as Team);
  return {
    food: p.resources.food, wood: p.resources.wood, gold: p.resources.gold, popUsed: p.popUsed, popCap: p.popCap, age: p.age,
    upgrades: [...p.upgrades], oaths: [...p.oaths], pendingOath: p.pendingOath, heroState: p.heroState,
    heroRespawnTimer: p.heroRespawnTimer, heroLevel: p.heroLevel, powerCooldown: p.powerCooldown,
    marketWood: p.marketPressure.wood, marketFood: p.marketPressure.food, defeated: p.defeated, defeatedAt: p.defeatedAt,
    ageTimes: Array.from({ length: p.ageTimes.length }, (_, i) => p.ageTimes[i] ?? null), autoReseed: p.autoReseed, stats: p.stats,
  };
}

function worldRecord(w: World): Record<string, unknown> {
  return {
    tick: w.tickCount, time: w.time, winner: w.winner ?? -1, count: w.entities.length, kothHold: [...w.kothHold],
    survivalWave: w.survivalWave, survivalTimer: (w as any).survivalTimer, survivalWon: w.survivalWon,
    banners: w.banners.map((b) => [b.team, b.x, b.y, b.expires]),
  };
}

function fogHash(f: Uint8Array): number {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < f.length; i++) h = mix(h, f[i]);
  return h;
}

function xhash(w: World, events: WorldEvent[]): number {
  const h = new H();
  const wr = worldRecord(w);
  h.i(wr.tick as number); h.n(wr.time as number); h.i(wr.winner as number); h.i(wr.count as number);
  h.v(wr.kothHold); h.i(wr.survivalWave as number); h.n(wr.survivalTimer as number); h.b(wr.survivalWon as boolean); h.v(wr.banners);
  for (let t = 0; t < w.numTeams; t++) {
    const pr = playerRecord(w, t);
    for (const k of Object.keys(pr)) { h.s(k); h.v(pr[k]); }
    if (w.tickCount % 5 === 0) h.i(fogHash(w.fog[t]));
  }
  for (const e of w.entities) {
    const r = entityRecord(e);
    for (const k of Object.keys(r)) {
      const v = r[k];
      if (INT_FIELDS.has(k)) h.i(v as number); else h.v(v);
    }
  }
  for (const ev of events) { h.s(ev.kind); h.n(ev.x); h.n(ev.y); h.i(ev.team); h.v(ev.data ?? null); }
  return h.h;
}

function snapshot(w: World, events: WorldEvent[]) {
  const players = Array.from({ length: w.numTeams }, (_, t) => ({ ...playerRecord(w, t), fog: fogHash(w.fog[t]) }));
  return {
    world: worldRecord(w), players,
    entities: w.entities.map(entityRecord),
    events: events.map((ev) => [ev.kind, ev.x, ev.y, ev.team, ev.data ?? null]),
  };
}

// ---- scenario helpers --------------------------------------------------
type Rec = (op: Op) => void;
const alive = (w: World, team: number, type?: string) =>
  w.entities.filter((e) => e.alive && e.team === team && e.kind === Kind.Unit && (type === undefined || e.type === type));
const army = (w: World, team: number) => alive(w, team).filter((e) => e.type !== "villager");
const ids = (es: Entity[]) => es.map((e) => e.id);
const building = (w: World, team: number, type: string, done = true) =>
  w.entities.find((e) => e.alive && e.team === team && e.kind === Kind.Building && e.type === type && (!done || e.buildState === 0));
function nearestRes(w: World, type: string, x: number, y: number): Entity | undefined {
  return w.entities.filter((e) => e.alive && e.kind === Kind.Resource && e.type === type)
    .sort((a, b) => Math.hypot(a.x - x, a.y - y) - Math.hypot(b.x - x, b.y - y))[0];
}
/** A spot near (x, y) where `type` can go: placementProblem for an order, canPlace for a harness spawn. */
function spot(w: World, team: number, type: string, x: number, y: number, harness = false, minR = 0): { x: number; y: number } {
  for (let r = minR; r < 900; r += 32) {
    for (let a = 0; a < 24; a++) {
      const px = Math.round(x + Math.cos((a / 24) * Math.PI * 2) * r);
      const py = Math.round(y + Math.sin((a / 24) * Math.PI * 2) * r);
      const ok = harness ? w.canPlace(team as Team, type, px, py) : w.placementProblem(team as Team, type, px, py, true) === null;
      if (ok) return { x: px, y: py };
      if (r === 0) break;
    }
  }
  throw new Error(`no spot for ${type}`);
}
function grid(rec: Rec, team: number, list: [string, number][], x: number, y: number, per = 6, gap = 26) {
  let k = 0;
  for (const [type, n] of list) for (let j = 0; j < n; j++, k++) rec({ t: "_spawn", team, type, x: x + (k % per) * gap, y: y + Math.floor(k / per) * gap });
}
const centre = (es: Entity[]) => ({ x: Math.round(es.reduce((s, e) => s + e.x, 0) / Math.max(1, es.length)), y: Math.round(es.reduce((s, e) => s + e.y, 0) / Math.max(1, es.length)) });

interface Scenario {
  name: string; preset: string; seed: number; players: number; ticks: number; snapEvery: number;
  init?: { alliances?: number[]; commanders?: string[]; nomad?: boolean; boons?: { id: string; rarity: number; age: number }[][]; mode?: string; factions?: string[] };
  /** Decides this tick's ops from the live world; `rec` records and applies one at once. */
  script: (w: World, tick: number, rec: Rec) => void;
}

function run(s: Scenario) {
  const map = generateMap(s.preset, s.seed, s.players);
  const w = new World(s.seed);
  const i0 = s.init ?? {};
  w.init(map, Array.from({ length: s.players }, () => ({})), Array.from({ length: s.players }, () => 1),
    i0.alliances, i0.commanders, i0.nomad ?? false, i0.boons, (i0.mode ?? "conquest") as any, i0.factions);
  const ops: [number, Op][] = [];
  const sums: number[] = [worldChecksum(w)];
  const xh: number[] = [xhash(w, [])];
  const snaps = [snapshot(w, [])];
  for (let i = 0; i < s.ticks; i++) {
    s.script(w, i, (op) => { ops.push([i, op]); applyOp(w, op); });
    w.tick();
    const ev = w.drainEvents();
    sums.push(worldChecksum(w));
    xh.push(xhash(w, ev));
    if ((i + 1) % s.snapEvery === 0) snaps.push(snapshot(w, ev));
    if (w.winner !== null) break;
  }
  const { script, ...meta } = s;
  void script;
  const file = path.join(OUT, `world_${s.name}.json`);
  fs.writeFileSync(file, JSON.stringify({ ...meta, ops, checksums: sums, xhash: xh, snapshots: snaps }));
  console.log(`  world_${s.name}.json ${(fs.statSync(file).size / 1024).toFixed(0)} KB, ${sums.length - 1} ticks, winner ${w.winner}`);
}

const ARMY: [string, number][] = [["militia", 5], ["spearman", 4], ["archer", 5], ["knight", 3], ["skirmisher", 3], ["catapult", 2], ["monk", 2],
  ["crossbow", 2], ["horseman", 2], ["twohand", 2], ["scout", 1], ["hero", 1], ["lancer", 1], ["ram", 1], ["javelin", 2]];

// ---- battle: two armies, formations, flow fields, every kind of fight ----
run({
  name: "battle", preset: "open_plains", seed: 7, players: 2, ticks: 2000, snapEvery: 200,
  script: (w, i, rec) => {
    if (i === 0) { grid(rec, 0, ARMY, 1350, 1450); grid(rec, 1, ARMY, 1900, 1650); }
    if (i === 1) {
      rec({ t: "move", team: 0, ids: ids(army(w, 0)), x: 1900, y: 1700, queue: false, attackMove: true, formation: true });
      rec({ t: "move", team: 1, ids: ids(army(w, 1)), x: 1350, y: 1500, queue: false, attackMove: true });
    }
    if (i === 2) {
      rec({ t: "stance", team: 0, ids: ids(army(w, 0).filter((e) => ["skirmisher", "archer", "javelin"].includes(e.type))), stance: 4 });
      rec({ t: "stance", team: 1, ids: ids(alive(w, 1, "knight")), stance: 0 });
      rec({ t: "stance", team: 1, ids: ids(alive(w, 1, "catapult")), stance: 2 });
      rec({ t: "stance", team: 0, ids: ids(alive(w, 0, "monk")), stance: 3 });
    }
    if (i === 40 || i === 240 || i === 480) for (const t of [0, 1]) rec({ t: "ability", team: t, ids: ids(army(w, t)) });
    if (i === 150) rec({ t: "hold", team: 1, ids: ids(alive(w, 1, "crossbow")) });
    if (i === 400) {
      const foe = army(w, 1)[0];
      if (foe) rec({ t: "attack", team: 0, ids: ids(alive(w, 0, "knight")), target: foe.id, queue: false });
    }
    if (i === 700) {
      const s1 = w.map.starts[1];
      rec({ t: "move", team: 0, ids: ids(army(w, 0)), x: s1.x, y: s1.y, queue: false, attackMove: true, formation: true });
      rec({ t: "move", team: 1, ids: ids(army(w, 1)), x: s1.x, y: s1.y - 150, queue: false, attackMove: false });
    }
    if (i === 1000) {
      const tc = building(w, 1, "town_center");
      if (tc) rec({ t: "attack", team: 0, ids: ids(alive(w, 0, "ram").concat(alive(w, 0, "catapult"))), target: tc.id, queue: false });
    }
    if (i === 1300) {
      const m = army(w, 0)[0];
      if (m) rec({ t: "delete", team: 0, ids: [m.id] });
    }
    if (i === 1400) rec({ t: "stop", team: 1, ids: ids(alive(w, 1)) });
  },
});

// ---- economy: a real build order, farms, ages and Oaths, techs, the market ----
{
  const st: Record<string, boolean> = {};
  const once = (k: string) => (st[k] ? false : (st[k] = true));
  run({
    name: "economy", preset: "open_plains", seed: 7, players: 2, ticks: 4000, snapEvery: 250,
    script: (w, i, rec) => {
      const p0 = w.player(0 as Team), p1 = w.player(1 as Team);
      const tc0 = building(w, 0, "town_center")!, tc1 = building(w, 1, "town_center")!;
      const v0 = alive(w, 0, "villager"), v1 = alive(w, 1, "villager");
      if (i === 0) {
        rec({ t: "_res", team: 0, food: 3000, wood: 3000, gold: 2000 });
        rec({ t: "_res", team: 1, food: 2000, wood: 2000, gold: 1000 });
        const tree = nearestRes(w, "tree", tc0.x, tc0.y)!, berries = nearestRes(w, "berries", tc0.x, tc0.y)!;
        rec({ t: "gather", team: 0, ids: [v0[0].id], node: tree.id, queue: false });
        rec({ t: "gather", team: 0, ids: [v0[1].id], node: berries.id, queue: false });
        // The third villager raises a lumber camp, then (shift-queued) a house.
        const lc = spot(w, 0, "lumber_camp", tree.x, tree.y, false, 64);
        rec({ t: "place", team: 0, building: "lumber_camp", x: lc.x, y: lc.y, builders: [v0[2].id] });
        const h = spot(w, 0, "house", tc0.x - 160, tc0.y + 80);
        rec({ t: "place", team: 0, building: "house", x: h.x, y: h.y, builders: [] });
        const house = w.entities[w.entities.length - 1];
        rec({ t: "build", team: 0, ids: [v0[2].id], building: house.id, queue: true });
        for (let k = 0; k < 5; k++) rec({ t: "train", team: 0, buildingId: tc0.id, unit: "villager" });
        const b1 = nearestRes(w, "berries", tc1.x, tc1.y)!;
        const mill = spot(w, 1, "mill", b1.x, b1.y, false, 64);
        rec({ t: "place", team: 1, building: "mill", x: mill.x, y: mill.y, builders: ids(v1) });
        for (let k = 0; k < 3; k++) rec({ t: "train", team: 1, buildingId: tc1.id, unit: "villager" });
        rec({ t: "rally", team: 1, buildingId: tc1.id, x: tc1.x + 120, y: tc1.y });
      }
      // Each new villager gets a job as it comes out.
      if (i === 300 && v0[3]) { const b = spot(w, 0, "barracks", tc0.x + 200, tc0.y + 60); rec({ t: "place", team: 0, building: "barracks", x: b.x, y: b.y, builders: [v0[3].id] }); }
      if (i === 550 && v0[4]) {
        const gold = nearestRes(w, "gold_mine", tc0.x, tc0.y)!;
        const mc = spot(w, 0, "mining_camp", gold.x, gold.y, false, 64);
        rec({ t: "place", team: 0, building: "mining_camp", x: mc.x, y: mc.y, builders: [v0[4].id] });
        rec({ t: "rally", team: 0, buildingId: tc0.id, x: gold.x, y: gold.y });
      }
      if (i === 800 && v0[5]) {
        const placed: number[] = [];
        for (let k = 0; k < 3; k++) {
          const f = spot(w, 0, "farm", tc0.x + 40 + k * 80, tc0.y + 170);
          rec({ t: "place", team: 0, building: "farm", x: f.x, y: f.y, builders: k === 0 ? [v0[5].id] : [] });
          placed.push(w.entities[w.entities.length - 1].id);
        }
        rec({ t: "build", team: 0, ids: [v0[5].id], building: placed[1], queue: true });
        if (v0[6]) rec({ t: "build", team: 0, ids: [v0[6].id], building: placed[2], queue: false });
      }
      if (i === 300) { const t1 = nearestRes(w, "tree", tc1.x, tc1.y)!; const lc = spot(w, 1, "lumber_camp", t1.x, t1.y, false, 64); rec({ t: "place", team: 1, building: "lumber_camp", x: lc.x, y: lc.y, builders: [v1[0].id] }); }
      if (p0.age === 0 && w.ageRequirementMet(0 as Team, 1) && once("age1")) rec({ t: "research", team: 0, buildingId: tc0.id, tech: "age:plough" });
      if (p1.age === 0 && w.ageRequirementMet(1 as Team, 1) && once("age1b")) rec({ t: "research", team: 1, buildingId: tc1.id, tech: "age" });
      if (p0.age >= 1 && once("bs")) {
        const [a, b] = [v0[0], v0[1]];
        const bs = spot(w, 0, "blacksmith", tc0.x - 200, tc0.y - 100); rec({ t: "place", team: 0, building: "blacksmith", x: bs.x, y: bs.y, builders: [a.id] });
        const ar = spot(w, 0, "archery_range", tc0.x + 260, tc0.y - 140); rec({ t: "place", team: 0, building: "archery_range", x: ar.x, y: ar.y, builders: [b.id] });
        const mk = spot(w, 0, "market", tc0.x - 120, tc0.y - 260); rec({ t: "place", team: 0, building: "market", x: mk.x, y: mk.y, builders: [] });
        rec({ t: "build", team: 0, ids: [a.id], building: w.entities[w.entities.length - 1].id, queue: true });
      }
      const bs = building(w, 0, "blacksmith");
      if (bs && once("techs")) for (const tech of ["wheelbarrow", "forging", "scale_armor", "bloodlines"]) rec({ t: "research", team: 0, buildingId: bs.id, tech });
      const mk = building(w, 0, "market");
      if (mk && once("trade")) for (const a of ["sell_wood", "sell_wood", "buy_food", "sell_food", "buy_wood"] as const) rec({ t: "trade", team: 0, action: a });
      if (i === 1300) {
        // Two villagers hide in the Town Centre: one beside it goes straight in,
        // one across the field walks over first.
        const near = v0.filter((v) => Math.hypot(v.x - tc0.x, v.y - tc0.y) <= tc0.radius + 60);
        const far = v0.filter((v) => Math.hypot(v.x - tc0.x, v.y - tc0.y) > tc0.radius + 60);
        rec({ t: "garrison", team: 0, ids: ids(near.slice(0, 1).concat(far.slice(0, 1))), buildingId: tc0.id });
      }
      if (i === 1400) rec({ t: "ungarrison", team: 0, buildingId: tc0.id });
      if (i === 1590) for (let k = 0; k < 3; k++) rec({ t: "train", team: 0, buildingId: tc0.id, unit: "villager" });
      if (i === 1600) rec({ t: "cancel", team: 0, buildingId: tc0.id, index: 1 });
      const farms = w.entities.filter((e) => e.alive && e.team === 0 && e.type === "farm" && e.buildState === 0);
      if (i === 1800 && farms[0]) rec({ t: "_set", id: farms[0].id, field: "amount", value: 5 });
      if (i === 2300) { rec({ t: "autoreseed", team: 0, on: false }); if (farms[1]) rec({ t: "_set", id: farms[1].id, field: "amount", value: 3 }); }
      if (p0.age === 1 && w.ageRequirementMet(0 as Team, 2) && once("age2")) rec({ t: "research", team: 0, buildingId: tc0.id, tech: "age:bow" });
      const ar = building(w, 0, "archery_range");
      if (ar && p0.age >= 2 && once("rangers")) {
        for (const u of ["ranger", "ranger", "archer", "longbow"]) rec({ t: "train", team: 0, buildingId: ar.id, unit: u });
        rec({ t: "rally", team: 0, buildingId: ar.id, x: ar.x, y: ar.y + 160 });
      }
      const br = building(w, 0, "barracks");
      if (br && once("mil")) for (const u of ["militia", "militia", "spearman", "spearman", "legionary"]) rec({ t: "train", team: 0, buildingId: br.id, unit: u });
      if (i === 2800) rec({ t: "move", team: 0, ids: ids(army(w, 0)), x: tc1.x, y: tc1.y, queue: false, attackMove: true });
      if (i === 3000) { const v = alive(w, 1, "villager")[0]; if (v) rec({ t: "stance", team: 1, ids: [v.id], stance: 0 }); }
    },
  });
}

// ---- alliance: 2v2 with factions, commanders, boons, banners, forest cover ----
run({
  name: "alliance", preset: "riverlands", seed: 23, players: 4, ticks: 1800, snapEvery: 200,
  init: {
    alliances: [0, 1, 0, 1], factions: ["khanate", "ascendancy", "norse", "shogunate"], commanders: ["banneret", "architect", "warpriest", "marshal"],
    boons: [
      [{ id: "whetstones", rarity: 3, age: 0 }, { id: "far_sight", rarity: 1, age: 0 }, { id: "iron_discipline", rarity: 2, age: 1 }],
      [{ id: "quartermaster", rarity: 5, age: 0 }, { id: "reaver", rarity: 2, age: 0 }],
      [{ id: "bulwark", rarity: 2, age: 0 }, { id: "vigilant_watch", rarity: 4, age: 0 }, { id: "destrier", rarity: 1, age: 0 }],
      [{ id: "field_medic", rarity: 3, age: 0 }, { id: "pavise", rarity: 5, age: 0 }, { id: "bodkin_points", rarity: 2, age: 0 }],
    ],
  },
  script: (w, i, rec) => {
    if (i === 0) {
      grid(rec, 0, [["horse_archer", 4], ["cataphract", 2], ["knight", 2], ["archer", 3]], 1450, 1600);
      grid(rec, 2, [["berserker", 4], ["militia", 3], ["spearman", 2], ["catapult", 1]], 1450, 1760);
      grid(rec, 1, [["skimmer", 3], ["pulse_trooper", 3], ["horseman", 2], ["monk", 1]], 1950, 1600);
      grid(rec, 3, [["samurai", 3], ["spearman", 3], ["archer", 2], ["hero", 1]], 1950, 1760);
      const wt = spot(w, 1, "watch_tower", 1850, 1650, true);
      rec({ t: "_building", team: 1, type: "watch_tower", x: wt.x, y: wt.y, done: true });
      const s3 = w.map.starts[3];
      const br = spot(w, 3, "barracks", s3.x + 160, s3.y + 40, true);
      rec({ t: "_building", team: 3, type: "barracks", x: br.x, y: br.y, done: true });
      // Archers waiting in the nearest woods.
      const forest: [number, number][] = [];
      for (let r = 0; r < w.terrainRows; r++) for (let c = 0; c < w.terrainCols; c++) if (w.terrain[r * w.terrainCols + c] === 5 && !w.grid.isBlocked(c, r)) forest.push([c, r]);
      forest.sort((a, b) => Math.hypot(a[0] * 32 - 1700, a[1] * 32 - 1700) - Math.hypot(b[0] * 32 - 1700, b[1] * 32 - 1700));
      for (let k = 0; k < 3 && k < forest.length; k++) rec({ t: "_spawn", team: 0, type: "longbow", x: forest[k][0] * 32 + 16, y: forest[k][1] * 32 + 16 });
      for (let k = 3; k < 5 && k < forest.length; k++) rec({ t: "_spawn", team: 1, type: "ranger", x: forest[k][0] * 32 + 16, y: forest[k][1] * 32 + 16 });
    }
    if (i === 1) {
      rec({ t: "move", team: 0, ids: ids(army(w, 0)), x: 1950, y: 1650, queue: false, attackMove: true, formation: true });
      rec({ t: "move", team: 2, ids: ids(army(w, 2)), x: 1950, y: 1800, queue: false, attackMove: true });
      rec({ t: "move", team: 1, ids: ids(army(w, 1)), x: 1450, y: 1650, queue: false, attackMove: true, formation: true });
      rec({ t: "move", team: 3, ids: ids(army(w, 3)), x: 1450, y: 1800, queue: false, attackMove: true, formation: true });
    }
    if (i === 20) { const c = centre(army(w, 0)); rec({ t: "banner", team: 0, x: c.x, y: c.y }); const c2 = centre(army(w, 2)); rec({ t: "banner", team: 2, x: c2.x, y: c2.y }); }
    if (i === 100 || i === 500) for (const t of [0, 1, 2, 3]) rec({ t: "ability", team: t, ids: ids(army(w, t)) });
    if (i === 300) { const br = building(w, 3, "barracks"); if (br) for (const u of ["spearman", "samurai", "militia", "twohand"]) rec({ t: "train", team: 3, buildingId: br.id, unit: u }); }
    if (i === 600) { const tc = building(w, 1, "town_center"); if (tc) rec({ t: "research", team: 1, buildingId: tc.id, tech: "age" }); }
    if (i === 900) { const c = centre(army(w, 0)); rec({ t: "banner", team: 0, x: c.x, y: c.y }); }
    if (i === 1000) for (const t of [0, 1, 2, 3]) { const a = army(w, t); if (a.length) { const s = w.map.starts[(t + 1) % 4]; rec({ t: "move", team: t, ids: ids(a), x: s.x, y: s.y, queue: false, attackMove: true }); } }
  },
});

// ---- survival: waves from the edges against a defended base ----
run({
  name: "survival", preset: "open_plains", seed: 7, players: 2, ticks: 2600, snapEvery: 200, init: { mode: "survival" },
  script: (w, i, rec) => {
    const s0 = w.map.starts[0];
    if (i === 0) {
      for (const [dx, dy] of [[140, 0], [-140, 40]]) { const p = spot(w, 0, "watch_tower", s0.x + dx, s0.y + dy, true); rec({ t: "_building", team: 0, type: "watch_tower", x: p.x, y: p.y, done: true }); }
      grid(rec, 0, [["spearman", 4], ["archer", 3], ["knight", 2]], s0.x - 60, s0.y + 120);
      const tree = nearestRes(w, "tree", s0.x, s0.y)!;
      rec({ t: "gather", team: 0, ids: ids(alive(w, 0, "villager")), node: tree.id, queue: false });
    }
    if (i === 1000) rec({ t: "ability", team: 0, ids: ids(army(w, 0)) });
  },
});

// ---- regicide: ride down the King ----
run({
  name: "regicide", preset: "open_plains", seed: 7, players: 2, ticks: 1500, snapEvery: 100, init: { mode: "regicide" },
  script: (w, i, rec) => {
    const king1 = w.entities.find((e) => e.alive && e.team === 1 && e.type === "king");
    if (i === 0 && king1) {
      grid(rec, 0, [["knight", 6]], king1.x - 300, king1.y + 100);
      grid(rec, 1, [["militia", 3]], king1.x + 30, king1.y + 30, 3);
    }
    if (i === 1 && king1) rec({ t: "attack", team: 0, ids: ids(alive(w, 0, "knight")), target: king1.id, queue: false });
  },
});

// ---- koth: take and hold the hill, then contest it ----
run({
  name: "koth", preset: "crossroads", seed: 99, players: 3, ticks: 1500, snapEvery: 150, init: { mode: "koth" },
  script: (w, i, rec) => {
    if (i === 0) {
      rec({ t: "_world", field: "kothGoal", value: 30 });
      grid(rec, 0, [["militia", 5]], w.hillX - 50, w.hillY - 20, 5);
      grid(rec, 1, [["spearman", 4]], w.hillX + 500, w.hillY, 4);
      grid(rec, 2, [["villager", 2]], w.hillX, w.hillY + 30, 2);
    }
    if (i === 200) rec({ t: "move", team: 1, ids: ids(army(w, 1)), x: w.hillX, y: w.hillY, queue: false, attackMove: true });
  },
});

// ---- misc: bridge, walls and a gate, repair by rally, trade carts ----
{
  const st: Record<string, boolean> = {};
  const once = (k: string) => (st[k] ? false : (st[k] = true));
  run({
    name: "misc", preset: "open_plains", seed: 7, players: 2, ticks: 2400, snapEvery: 200,
    script: (w, i, rec) => {
      const tc0 = building(w, 0, "town_center")!;
      const v0 = alive(w, 0, "villager");
      if (i === 0) {
        rec({ t: "_res", team: 0, food: 2000, wood: 2000, gold: 2000 });
        // A ford beside the base: shallows over open ground.
        const cells: [number, number, number][] = [];
        for (let r = 12; r < 28; r++) for (let c = 44; c < 46; c++) if (!w.grid.isBlocked(c, r)) cells.push([c, r, 10]);
        rec({ t: "_terrain", cells });
        const br = spot(w, 0, "bridge", 45 * 32, 20 * 32);
        rec({ t: "place", team: 0, building: "bridge", x: br.x, y: br.y, builders: [v0[0].id] });
        rec({ t: "_player", team: 0, field: "age", value: 1 });
        const m1 = spot(w, 0, "market", tc0.x - 200, tc0.y + 200, true);
        rec({ t: "_building", team: 0, type: "market", x: m1.x, y: m1.y, done: true });
        const m2 = spot(w, 0, "market", 2300, 1300, true);
        rec({ t: "_building", team: 0, type: "market", x: m2.x, y: m2.y, done: true });
        const h = spot(w, 0, "house", tc0.x + 150, tc0.y - 120, true);
        rec({ t: "_building", team: 0, type: "house", x: h.x, y: h.y, done: true });
      }
      if (i === 1) {
        const mk = building(w, 0, "market")!;
        rec({ t: "train", team: 0, buildingId: mk.id, unit: "trade_cart" });
        rec({ t: "train", team: 0, buildingId: mk.id, unit: "trade_cart" });
        for (let k = 0; k < 3; k++) {
          const p = spot(w, 0, "palisade", tc0.x - 120 + k * 32, tc0.y - 200);
          rec({ t: "place", team: 0, building: "palisade", x: p.x, y: p.y, builders: [v0[1].id] });
        }
        const g = spot(w, 0, "gate", tc0.x - 24, tc0.y - 200);
        rec({ t: "place", team: 0, building: "gate", x: g.x, y: g.y, builders: [v0[1].id] });
        const sw = spot(w, 0, "stone_wall", tc0.x + 40, tc0.y - 200);
        rec({ t: "place", team: 0, building: "stone_wall", x: sw.x, y: sw.y, builders: [v0[2].id] });
      }
      if (i === 50) {
        const h = building(w, 0, "house")!;
        rec({ t: "_damage", team: 1, target: h.id, amount: 200, source: "militia" });
        rec({ t: "rally", team: 0, buildingId: tc0.id, x: h.x, y: h.y });
        rec({ t: "train", team: 0, buildingId: tc0.id, unit: "villager" });
      }
      const gate = building(w, 0, "gate");
      if (gate && once("gate1")) rec({ t: "gate", team: 0, buildingId: gate.id });
      if (gate && i === 900) rec({ t: "gate", team: 0, buildingId: gate.id });
      if (i === 400) rec({ t: "trade_route", team: 0, ids: ids(alive(w, 0, "trade_cart")) });
      if (i === 1200) {
        const cart = alive(w, 0, "trade_cart")[0];
        if (cart) grid(rec, 1, [["knight", 2]], cart.x + 60, cart.y, 2);
      }
      if (i === 1500) { const b = building(w, 0, "bridge"); if (b) rec({ t: "delete", team: 0, ids: [b.id] }); }
    },
  });
}

// ---- nomad: no Town Centre — raise one, then grow ----
{
  const st: Record<string, boolean> = {};
  const once = (k: string) => (st[k] ? false : (st[k] = true));
  run({
    name: "nomad", preset: "black_forest", seed: 11, players: 2, ticks: 1600, snapEvery: 200, init: { nomad: true },
    script: (w, i, rec) => {
      if (i === 0) {
        const v0 = alive(w, 0, "villager");
        const c = centre(v0);
        const p = spot(w, 0, "town_center", c.x, c.y);
        rec({ t: "place", team: 0, building: "town_center", x: p.x, y: p.y, builders: ids(v0) });
        const v1 = alive(w, 1, "villager");
        const tree = nearestRes(w, "tree", v1[0].x, v1[0].y)!;
        rec({ t: "gather", team: 1, ids: ids(v1), node: tree.id, queue: false });
      }
      const tc = building(w, 0, "town_center");
      if (tc && once("tc")) {
        rec({ t: "train", team: 0, buildingId: tc.id, unit: "villager" });
        rec({ t: "train", team: 0, buildingId: tc.id, unit: "scout" });
        const berries = nearestRes(w, "berries", tc.x, tc.y);
        if (berries) rec({ t: "rally", team: 0, buildingId: tc.id, x: berries.x, y: berries.y });
      }
    },
  });
}
