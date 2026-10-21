// Parity fixtures for the Godot port of Warband Tactics (sim/warband.ts and
// everything it pulls in: autobattle, traits, items, augments, carousel,
// creeps, commanders, difficulty, conditions, terrain).
//
//   npx tsx scripts/godot_warband_parity.ts
//
// Writes godot/tests/fixtures/warband_<name>.json. Each scenario plays a whole
// run from a seed with a scripted player: shop/bench/board decisions (buy,
// sell, reroll, level, lock, place, field, equip), augment and carousel picks,
// commander and difficulty choices, a typed-in ground seed, creep and boss
// rounds — and fights each round either headlessly (`run.fight()`, which goes
// through resolveBattle) or the way the screen does (`beginFight()` + a
// LiveBattle stepped in uneven batches + `finishFight`). The player's
// decisions are made here, against the live TS run, and recorded as concrete
// ops, so the Godot test only replays them.
//
// Recorded: after every op its return value and a hash of the run's state; at
// every round boundary a full snapshot (including every derived read the
// screen makes: standings, synergies, scouting reports, odds, the worst-case
// damage…); for every battle its BattleResult and the worldChecksum of the
// battle's world (the live ones every 20 ticks too).

import fs from "fs";
import path from "path";
import { WarbandRun } from "../src/sim/warband";
import { LiveBattle, BattleResult } from "../src/sim/autobattle";
import { World } from "../src/sim/world";
import { worldChecksum } from "../src/sim/commands";
import { pickValue } from "../src/sim/carousel";
import { UNIT_TIER } from "../src/sim/unit_tiers";
import { isBlocked } from "../src/sim/terrain";
import { difficultyById } from "../src/sim/warband_difficulty";

const OUT = path.resolve("../godot/tests/fixtures");
fs.mkdirSync(OUT, { recursive: true });

// The world each battle is fought in: resolveBattle builds its own and never
// hands it out, so World.init is wrapped to catch it. (The Godot side keeps a
// reference in Autobattle.lastWorld for the same purpose.)
let lastWorld: World | null = null;
const origInit = World.prototype.init;
World.prototype.init = function (this: World, ...args: Parameters<World["init"]>) {
  lastWorld = this; // eslint-disable-line @typescript-eslint/no-this-alias
  return origInit.apply(this, args);
};

// ---- hashing (mirrored in test_warband_parity.gd) -------------------------
function mix(acc: number, n: number): number {
  acc = (acc ^ (n | 0)) >>> 0;
  return Math.imul(acc, 16777619) >>> 0;
}
class H {
  h = 2166136261 >>> 0;
  v(x: unknown): void {
    if (x === null || x === undefined) this.h = mix(this.h, -7);
    else if (typeof x === "number") this.h = mix(this.h, Math.round(x * 1000));
    else if (typeof x === "boolean") this.h = mix(this.h, x ? 1 : 0);
    else if (typeof x === "string") { this.h = mix(this.h, x.length); for (let k = 0; k < x.length; k++) this.h = mix(this.h, x.charCodeAt(k)); }
    else if (Array.isArray(x)) { this.h = mix(this.h, x.length); for (const y of x) this.v(y); }
    else { const ks = Object.keys(x as object); this.h = mix(this.h, ks.length); for (const k of ks) this.v((x as Record<string, unknown>)[k]); }
  }
}
const hash = (x: unknown) => { const h = new H(); h.v(x); return h.h; };

// ---- state records ----------------------------------------------------------
type Any = any; // eslint-disable-line @typescript-eslint/no-explicit-any
const pieceRec = (p: Any) => [p.type, p.star, [...p.items], p.col ?? -1, p.row ?? -1, p.deployed ? 1 : 0];
const arenaRec = (u: Any) => [u.type, u.star ?? 1, [...(u.items ?? [])], u.col ?? -1, u.row ?? -1];
const buffRec = (b: Any) => (b ? Object.entries(b) : []);

/** The run's own state (no derived reads) — hashed after every op. */
function core(run: WarbandRun): Record<string, unknown> {
  const R = run as Any;
  return {
    round: run.round, phase: run.phase, gold: run.gold, level: run.level, xp: run.xp, streak: run.streak, life: run.life,
    outcome: run.outcome ?? "",
    pieces: run.pieces.map(pieceRec),
    itemStash: [...run.itemStash],
    shop: run.shop.map((s) => s ?? ""),
    opponents: run.opponents.map((o) => [o.life, o.alive ? 1 : 0]),
    augments: run.augments.map((a) => a.id),
    augmentOffer: run.augmentOffer.map((a) => a.id),
    commander: run.commander?.id ?? "",
    commanderOffer: run.commanderOffer.map((c) => c.id),
    freeRerolls: R.freeRerolls,
    pendingCamp: run.pendingCamp?.name ?? "",
    condition: run.condition.id,
    terrain: run.terrain.map((f) => [f.kind, f.col, f.row]),
    terrainSeed: run.terrainSeed,
    shopLocked: run.shopLocked ? 1 : 0,
    carousel: run.carousel.map((c) => [c.type, c.star, c.item]),
    draftAhead: run.draftAhead,
    history: run.history.map((h) => [h.round, h.stage, h.foe, h.won ? 1 : 0, h.creep ? 1 : 0, h.dmg, h.youLeft, h.foeLeft, h.life]),
    tally: Object.values(run.tally),
    lastResult: run.lastResult ? [run.lastResult.won ? 1 : 0, run.lastResult.foe, run.lastResult.youLeft, run.lastResult.foeLeft,
      run.lastResult.dmg, run.lastResult.relics, run.lastResult.gold, run.lastResult.creep ? 1 : 0] : [],
    lastMerge: run.lastMerge ? [run.lastMerge.type, run.lastMerge.star] : [],
    lastFusion: run.lastFusion ? [run.lastFusion.item, run.lastFusion.type] : [],
    difficulty: run.difficulty.id,
    boss: [R.bossPending ? 1 : 0, run.bossRound ? 1 : 0, run.bossCleared ? 1 : 0],
    pendingSeed: run.pendingSeed,
    pendingOpp: run.pendingOpp.map(arenaRec),
    pendingFoe: R.pendingFoe?.id ?? -1,
    foes: R.foeBrains.map((b: Any) => [b.gold, b.level, b.xp, b.pieces.map((p: Any) => [p.type, p.star, [...p.items]]), b.rng.state >>> 0, [...b.favored], b.play]),
    pool: Object.entries(R.pool),
    rng: R.rng.state >>> 0,
  };
}

/** Everything the screen reads off a run, on top of its state. */
function derived(run: WarbandRun): Record<string, unknown> {
  const side = run.sideOpts();
  const field = run.fieldOpts();
  const xp = run.xpProgress();
  return {
    deployCount: run.deployCount(), rerollCost: run.rerollCost(), freeRerollsLeft: run.freeRerollsLeft(),
    shopOdds: [...run.shopOdds()], xpProgress: [xp.xp, xp.need, xp.max ? 1 : 0], stageLabel: run.stageLabel(),
    terrainCode: run.terrainCode(), benchCount: run.benchCount(), benchFull: run.benchFull() ? 1 : 0,
    deployedCount: run.deployedCount(), worstCase: run.worstCaseDamage(), streakGold: run.streakGold(),
    pendingFoeName: run.pendingFoeName(), isCreepRound: run.isCreepRound() ? 1 : 0, isDraftRound: run.isDraftRound() ? 1 : 0,
    placement: run.placement(),
    canBuy: run.shop.map((_, i) => { const c = run.canBuy(i); return [c.ok ? 1 : 0, c.reason ?? ""]; }),
    poolCounts: run.shop.map((t) => (t ? run.poolCount(t) : -1)),
    sideBuff: buffRec(side.buff), sideTraitBonus: buffRec(side.traitBonus),
    fieldBuff: buffRec(field.buff), fieldTraitBuffs: Object.entries(field.traitBuffs ?? {}).map(([k, b]) => [k, buffRec(b)]),
    boardUnits: run.boardUnits().map(arenaRec),
    deployment: run.deployment().map((d) => [d.index, d.type, d.star, d.col, d.row]),
    weakest: (() => { const w = run.weakestDeployed(); return w ? pieceRec(w) : []; })(),
    boardStacks: run.boardStacks().map((s) => [s.type, s.count, s.star ?? 1]),
    activeTraits: run.activeTraits().map((a) => [a.trait.id, a.count, a.tierIndex]),
    traitProgress: run.traitProgress().map((t) => [t.trait.id, t.count, t.tier?.label ?? "", t.next ?? -1]),
    standings: run.standings().map((s) => [s.name, s.life, s.alive ? 1 : 0, s.you ? 1 : 0, s.id]),
    scout: run.opponents.map((o) => {
      const s = run.scout(o.id)!;
      return [s.name, s.level, s.life, s.alive ? 1 : 0, s.board.map(arenaRec), s.traits.map((a) => [a.trait.id, a.count, a.tierIndex])];
    }),
  };
}

// ---- ops ----------------------------------------------------------------------
type Op =
  | { op: "buy" | "sell" | "pickAugment" | "takeCarousel" | "pickCommander"; i: number }
  | { op: "reroll" | "buyXp" | "lock" | "fight" | "next" }
  | { op: "place"; i: number; col: number; row: number }
  | { op: "equip"; s: number; i: number }
  | { op: "pickDifficulty" | "setDifficulty"; id: string }
  | { op: "terrain"; code: string }
  | { op: "live"; batches: number[] };

function apply(run: WarbandRun, op: Op): unknown {
  switch (op.op) {
    case "buy": return run.buy(op.i);
    case "sell": return run.sell(op.i);
    case "pickAugment": return run.pickAugment(op.i);
    case "takeCarousel": return run.takeCarousel(op.i);
    case "pickCommander": return run.pickCommander(op.i);
    case "reroll": return run.reroll();
    case "buyXp": return run.buyXp();
    case "lock": return run.toggleShopLock();
    case "next": run.next(); return null;
    case "place": return run.place(op.i, op.col, op.row);
    case "equip": return run.equipItem(op.s, op.i);
    case "pickDifficulty": return run.pickDifficulty(op.id);
    case "setDifficulty": { (run as Any).difficulty = difficultyById(op.id); return null; }
    case "terrain": return run.applyTerrainCode(op.code);
    case "fight": run.fight(); return null;
    case "live": return null; // handled by liveFight (it needs the battle in hand)
  }
}

/** The headless fight's world and verdict, caught on the way through. */
let lastBattle: BattleResult | null = null;
const origApply = (WarbandRun.prototype as Any).applyOutcome;
(WarbandRun.prototype as Any).applyOutcome = function (res: BattleResult) { lastBattle = { ...res }; return origApply.call(this, res); };
const worldRec = (w: World | null) => (w ? { checksum: worldChecksum(w), tick: w.tickCount, alive: w.entities.filter((e) => e.alive).length } : null);

// ---- the scripted players ----------------------------------------------------
type Strategy = "idle" | "greedy" | "drafter" | "bossplay";
interface Scenario {
  name: string; seed: number; commander?: string | null; difficulty?: string; pickCommander?: number;
  setDifficulty?: string; strategy: Strategy; battle: "fight" | "live"; maxRounds?: number;
}

const tier = (t: string) => UNIT_TIER[t] ?? 1;

function playScenario(sc: Scenario) {
  const run = sc.commander === undefined ? new WarbandRun(sc.seed) : new WarbandRun(sc.seed, sc.commander);
  const steps: unknown[] = [];
  const snaps: unknown[] = [];
  const battles: unknown[] = [];
  const snap = (label: string) => {
    const s = { label, core: core(run), derived: derived(run) };
    snaps.push(s);
    steps.push({ snap: snaps.length - 1 });
  };
  const doOp = (op: Op) => {
    lastWorld = null; lastBattle = null;
    const ret = apply(run, op);
    if (op.op === "fight") {
      battles.push({ kind: "fight", round: run.round, result: lastBattle, world: worldRec(lastWorld) });
      steps.push({ ...op, battle: battles.length - 1, ret: null, h: hash(core(run)) });
      return;
    }
    steps.push({ ...op, ret: ret ?? null, h: hash(core(run)) });
  };
  snap("start");
  let guard = 0;
  let terrainDone = false;
  while (run.phase !== "over" && guard++ < 400 && run.round <= (sc.maxRounds ?? 99)) {
    if (run.phase === "difficulty") { doOp({ op: "pickDifficulty", id: sc.difficulty ?? "veteran" }); continue; }
    if (run.phase === "commander") { doOp({ op: "pickCommander", i: sc.pickCommander ?? 0 }); continue; }
    if (run.phase === "augment") {
      const i = sc.strategy === "drafter" ? run.round % run.augmentOffer.length : 0;
      doOp({ op: "pickAugment", i });
      continue;
    }
    if (run.phase === "draft") {
      let i = 0;
      if (sc.strategy === "drafter") {
        let best = -1;
        run.carousel.forEach((p, k) => { if (pickValue(p) > best) { best = pickValue(p); i = k; } });
      }
      doOp({ op: "takeCarousel", i });
      continue;
    }
    if (run.phase === "shop") {
      if (sc.setDifficulty && run.round === 1 && !steps.some((s: Any) => s.op === "setDifficulty")) {
        doOp({ op: "setDifficulty", id: sc.setDifficulty });
      }
      shopRound(run, sc, doOp, () => { if (terrainDone) return false; terrainDone = true; return true; });
      if (sc.battle === "fight") doOp({ op: "fight" });
      else liveFight(run, battles, steps);
      snap(`after fight r${run.round}`);
      continue;
    }
    if (run.phase === "result") { doOp({ op: "next" }); snap(`round ${run.round}`); continue; }
    break;
  }
  snap("end");
  return { name: sc.name, seed: sc.seed, commander: sc.commander === undefined ? "<ask>" : sc.commander, steps, snaps, battles,
    final: { round: run.round, outcome: run.outcome, placement: run.placement(), bossRound: run.bossRound, bossCleared: run.bossCleared } };
}

/** One round of shopping, by strategy. Every decision goes through doOp. */
function shopRound(run: WarbandRun, sc: Scenario, doOp: (op: Op) => void, terrainOnce: () => boolean) {
  const s = sc.strategy;
  if (s === "idle") return;
  if (s === "greedy" || s === "bossplay") {
    if (s === "greedy" && run.gold >= 8) doOp({ op: "buyXp" });
    for (let k = 0; k < 5; k++) doOp({ op: "buy", i: k });
    if (s === "bossplay" && run.gold >= 8) doOp({ op: "buyXp" });
    return;
  }
  // ---- the drafter: a player who actually works the shop ----
  // A typed-in ground seed, once the ground has features.
  if (run.round >= 7 && run.terrain.length && terrainOnce()) {
    doOp({ op: "terrain", code: "ZZZZZZ" });
    doOp({ op: "terrain", code: "hello!" }); // refused
    doOp({ op: "terrain", code: (run.terrainSeed + 12345).toString(36).toUpperCase().slice(-6) });
  }
  // Equip relics onto the strongest unit with room.
  for (let guard = 0; guard < 6 && run.itemStash.length; guard++) {
    const dep = run.deployment();
    const target = dep.find((d) => run.pieces[d.index].items.length < 3);
    if (!target) break;
    doOp({ op: "equip", s: run.itemStash.length - 1, i: target.index });
  }
  // Level when rich.
  while (run.gold >= 24 && run.level < 9) doOp({ op: "buyXp" });
  // Buy what pairs with what we own, or anything when the board is thin.
  const owned = () => new Set(run.pieces.map((p) => p.type));
  for (let k = 0; k < 5; k++) {
    const t = run.shop[k];
    if (!t) continue;
    if (owned().has(t) || run.pieces.length < run.deployCount() + 2 || tier(t) >= 3) doOp({ op: "buy", i: k });
  }
  // Dig for copies when there's a surplus.
  if (run.gold > 18) {
    doOp({ op: "reroll" });
    for (let k = 0; k < 5; k++) { const t = run.shop[k]; if (t && owned().has(t)) doOp({ op: "buy", i: k }); }
  }
  // Sell chaff off a crowded bench: the weakest reserve.
  if (run.benchCount() >= 6) {
    let worst = -1;
    run.pieces.forEach((p, i) => {
      if (p.deployed) return;
      const w = worst < 0 ? null : run.pieces[worst];
      if (!w || p.star < w.star || (p.star === w.star && tier(p.type) < tier(w.type))) worst = i;
    });
    if (worst >= 0) doOp({ op: "sell", i: worst });
  }
  // Rearrange: move the weakest deployed unit, swap two, and field a reserve.
  const dep = run.deployment();
  if (dep.length >= 1) {
    const col = run.round % 5, row = (run.round * 3) % 10;
    doOp({ op: "place", i: dep[dep.length - 1].index, col, row });
  }
  if (dep.length >= 2) doOp({ op: "place", i: dep[0].index, col: dep[1].col, row: dep[1].row });
  const reserve = run.pieces.findIndex((p) => !p.deployed);
  if (reserve >= 0 && run.round % 2 === 0) {
    let col = 4, row = run.round % 10;
    if (isBlocked(run.terrain, col, row)) { col = 3; row = (row + 1) % 10; }
    doOp({ op: "place", i: reserve, col, row });
  }
  doOp({ op: "place", i: 0, col: 7, row: 0 }); // refused: the enemy half
  // Hold a shop now and then.
  if (run.round % 4 === 0) doOp({ op: "lock" });
}

/** The fight the way the screen runs it: a LiveBattle stepped in batches. */
function liveFight(run: WarbandRun, battles: unknown[], steps: unknown[]) {
  const began = run.beginFight();
  if (!began) { steps.push({ op: "beginFight", ret: false, h: hash(core(run)) }); return; }
  const lb = new LiveBattle(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30, run.sideOpts(), run.fieldOpts());
  lb.begin();
  const pattern = [2, 1, 3, 2, 5];
  const batches: number[] = [];
  const sums: number[] = [];
  let events = 0;
  const kinds = new H();
  let k = 0;
  let next = 20;
  for (let guard = 0; !lb.done && guard < 5000; guard++) {
    const n = pattern[k++ % pattern.length];
    batches.push(n);
    lb.step(n);
    for (const ev of lb.fxEvents) { events++; kinds.v(ev.kind); kinds.v(ev.team); kinds.v(ev.x); kinds.v(ev.y); }
    lb.fxEvents.length = 0;
    while (lb.ticks >= next) { sums.push(worldChecksum(lb.world)); next += 20; }
  }
  const res = lb.result();
  battles.push({
    kind: "live", round: run.round, batches, sums, events, kinds: kinds.h, result: res,
    world: worldRec(lb.world),
  });
  run.finishFight(res);
  steps.push({ op: "live", battle: battles.length - 1, ret: null, h: hash(core(run)) });
}

// ---- scenarios -----------------------------------------------------------------
function findBossSeed(): number {
  for (let s = 1; s <= 44; s++) {
    const r = playScenario({ name: "probe", seed: s * 37, commander: null, strategy: "bossplay", battle: "fight" });
    if (r.final.bossRound) return s * 37;
  }
  return 37;
}

const SCENARIOS: Scenario[] = [
  { name: "greedy", seed: 11, commander: null, strategy: "greedy", battle: "fight" },
  { name: "idle", seed: 7, commander: null, strategy: "idle", battle: "fight" },
  { name: "squire_live", seed: 23, difficulty: "squire", pickCommander: 1, strategy: "drafter", battle: "live" },
  { name: "conqueror", seed: 37, difficulty: "conqueror", pickCommander: 2, strategy: "drafter", battle: "fight" },
  { name: "warlord_qm_live", seed: 51, commander: "quartermaster", setDifficulty: "warlord", strategy: "drafter", battle: "live", maxRounds: 14 },
  { name: "banneret", seed: 64, commander: "banneret", strategy: "drafter", battle: "fight" },
  { name: "steward_live", seed: 99, commander: "steward", strategy: "greedy", battle: "live", maxRounds: 12 },
  { name: "magnate", seed: 123, commander: "magnate", strategy: "drafter", battle: "fight" },
  { name: "warden_drill", seed: 202, commander: "warden", setDifficulty: "conqueror", strategy: "greedy", battle: "fight" },
  { name: "drillmaster", seed: 303, commander: "drillmaster", strategy: "drafter", battle: "fight", maxRounds: 16 },
  { name: "warpriest", seed: 404, commander: "warpriest", setDifficulty: "squire", strategy: "drafter", battle: "fight" },
  { name: "marshal_live", seed: 505, commander: "marshal", strategy: "drafter", battle: "live", maxRounds: 10 },
];

const only = process.argv.slice(2);
const bossSeed = findBossSeed();
SCENARIOS.push({ name: "boss", seed: bossSeed, commander: null, strategy: "bossplay", battle: "fight" });
for (const sc of SCENARIOS) {
  if (only.length && !only.includes(sc.name)) continue;
  const t0 = Date.now();
  const fx = playScenario(sc);
  const file = path.join(OUT, `warband_${sc.name}.json`);
  fs.writeFileSync(file, JSON.stringify(fx));
  console.log(`${sc.name}: seed ${sc.seed}, ${fx.steps.length} steps, ${fx.battles.length} battles, round ${fx.final.round} ` +
    `${fx.final.outcome} #${fx.final.placement}${fx.final.bossRound ? (fx.final.bossCleared ? " (crowned)" : " (boss)") : ""} ` +
    `— ${(fs.statSync(file).size / 1024).toFixed(0)} KB, ${Date.now() - t0} ms`);
}
