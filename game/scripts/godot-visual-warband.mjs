// The Warband Tactics screen in each of its phases, for the Godot port's
// visual comparison: rendered through the real src/ui/warband_screen.ts on
// @napi-rs/canvas, with a spec of exactly how each shot was reached (the run's
// seed, the ops played on it, and every frame's pointer and clock) that
// godot/tests/visual/warband/warband_shot.gd replays through the port.
//
//   node scripts/godot-visual-warband.mjs ../godot/tests/visual/warband/out
//
// Writes ts_<shot>.png and shots.json into that folder.
import { build } from "esbuild";
import { createCanvas } from "@napi-rs/canvas";
import { writeFileSync, mkdtempSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { aliasFonts } from "./godot-visual-fonts.mjs";

aliasFonts();
const outDir = process.argv[2] ?? "../godot/tests/visual/warband/out";
mkdirSync(outDir, { recursive: true });
const tmp = mkdtempSync(join(tmpdir(), "gvw-"));
const outfile = join(tmp, "warband.cjs");
await build({
  stdin: {
    contents: `export { ui } from "./src/ui/ui";
      export { setMouseDown } from "./src/ui/screens";
      export { WarbandScreen } from "./src/ui/warband_screen";
      export { WarbandRun } from "./src/sim/warband";
      export { LiveBattle } from "./src/sim/autobattle";
      export { difficultyById } from "./src/sim/warband_difficulty";`,
    resolveDir: process.cwd(), loader: "ts",
  },
  bundle: true, format: "cjs", platform: "node", outfile, logLevel: "error",
});
const M = await import(pathToFileURL(outfile).href);
const { ui, setMouseDown, WarbandScreen, WarbandRun, LiveBattle, difficultyById } = M;

// Screen shake and sparks use Math.random; seed it so a shot is repeatable.
let rs = 12345;
Math.random = () => { rs = (rs * 1103515245 + 12345) & 0x7fffffff; return rs / 0x80000000; };

const W = 1600, H = 900;

/** Ops, as in the parity fixtures (the Godot side shares the applier). */
function apply(run, op) {
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
    case "setDifficulty": run.difficulty = difficultyById(op.id); return null;
    case "fight": run.fight(); return null;
    case "gold": run.gold = op.n; return null;
    case "level": run.level = op.n; return null;
    case "shop": run.shop = [...op.types]; return null; // copies: buy() nulls the slot
    case "stash": run.itemStash = [...op.items]; return null;
  }
  throw new Error("op " + op.op);
}

/** Play a run forward with a plain policy until `until(run)`, recording ops. */
function advance(run, ops, until, policy = "greedy") {
  const doOp = (op) => { ops.push(op); apply(run, op); };
  let guard = 0;
  while (!until(run) && run.phase !== "over" && guard++ < 300) {
    if (run.phase === "difficulty") { doOp({ op: "pickDifficulty", id: "veteran" }); continue; }
    if (run.phase === "commander") { doOp({ op: "pickCommander", i: 0 }); continue; }
    if (run.phase === "augment") { doOp({ op: "pickAugment", i: run.round % run.augmentOffer.length }); continue; }
    if (run.phase === "draft") { doOp({ op: "takeCarousel", i: 0 }); continue; }
    if (run.phase === "shop") {
      if (policy === "greedy") {
        if (run.gold >= 10 && run.level < 7) doOp({ op: "buyXp" });
        for (let k = 0; k < 5; k++) if (run.shop[k] && run.canBuy(k).ok) doOp({ op: "buy", i: k });
        while (run.itemStash.length) {
          const d = run.deployment()[0];
          if (!d || run.pieces[d.index].items.length >= 3) break;
          doOp({ op: "equip", s: 0, i: d.index });
        }
      }
      doOp({ op: "fight" });
      continue;
    }
    if (run.phase === "result") { doOp({ op: "next" }); continue; }
    break;
  }
}

const frames = (n, mx = -100, my = -100, extra = {}) => Array.from({ length: n }, () => ({ mx, my, down: false, clicked: false, ...extra }));

// ---- the shots ---------------------------------------------------------------
const SHOTS = [];
function shot(name, seed, commander, setup, frameList, opts = {}) {
  const run = commander === "<ask>" ? new WarbandRun(seed) : new WarbandRun(seed, commander);
  const ops = [];
  setup(run, ops);
  SHOTS.push({ name, seed, commander, ops, frames: frameList, battle: opts.battle ?? false, run });
}

// 1. The opening choice: how hard the lobby plays.
shot("difficulty", 17, "<ask>", () => {}, frames(40));
// 2. Who leads.
shot("commander", 17, "<ask>", (run, ops) => { ops.push({ op: "pickDifficulty", id: "warlord" }); apply(run, ops[0]); }, frames(40));
// 3. The shop: a board, a bench, relics, terrain, a hovered shop card.
shot("shop", 21, "marshal", (run, ops) => {
  advance(run, ops, (r) => r.round >= 7 && r.phase === "shop");
  const extra = [
    { op: "gold", n: 60 }, { op: "level", n: 6 },
    { op: "shop", types: ["knight", "archer", "spearman", "catapult", "hero"] },
    { op: "buy", i: 0 }, { op: "buy", i: 1 }, { op: "buy", i: 2 },
    { op: "shop", types: ["militia", "scout", "pikeman", "longbow", "monk"] },
    { op: "stash", items: ["blade", "hide", "whetstone", "sigil", "plate"] },
    { op: "gold", n: 27 },
  ];
  for (const op of extra) { ops.push(op); apply(run, op); }
}, frames(12));
// 4. The fight, mid-battle.
shot("battle", 21, "marshal", (run, ops) => {
  advance(run, ops, (r) => r.round >= 7 && r.phase === "shop");
}, frames(96), { battle: true });
// 5. The carousel.
shot("carousel", 31, null, (run, ops) => { advance(run, ops, (r) => r.phase === "draft"); }, frames(40));
// 6. An augment pick (the prismatic one, with its rays).
shot("augment", 33, null, (run, ops) => {
  advance(run, ops, (r) => r.phase === "augment" && r.round >= 9);
}, frames(40));
// 7. A round's result, flourish and all.
shot("result", 21, "marshal", (run, ops) => {
  advance(run, ops, (r) => r.round >= 7 && r.phase === "shop");
  ops.push({ op: "fight" }); apply(run, ops[ops.length - 1]);
}, frames(14));
// 8. The run summary.
shot("summary", 37, null, (run, ops) => { advance(run, ops, () => false); }, frames(6));
// 9. Scouting a rival (click their row in the standings).
shot("scout", 21, "marshal", (run, ops) => {
  advance(run, ops, (r) => r.round >= 4 && r.phase === "shop");
}, [...frames(2), { mx: 100, my: 98 + 1 * 29 + 13, down: false, clicked: true }, ...frames(3, 100, 300)]);

// ---- render -------------------------------------------------------------------
const spec = [];
for (const s of SHOTS) {
  const canvas = createCanvas(W, H);
  const ctx = canvas.getContext("2d");
  const screen = new WarbandScreen();
  const run = s.run;
  let t = 0;
  if (s.battle) {
    // Into the arena the way the FIGHT button does it.
    run.beginFight();
    screen.battle = new LiveBattle(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30, run.sideOpts(), run.fieldOpts());
    screen.battle.begin();
  }
  for (const f of s.frames) {
    t += 1 / 30;
    setMouseDown(f.down);
    ui.begin(ctx, { mx: f.mx, my: f.my, clicked: f.clicked, rightClicked: false, alt: false, leftHeld: f.down });
    ctx.save();
    screen.draw(W, H, t, run);
    ctx.restore();
  }
  writeFileSync(join(outDir, `ts_${s.name}.png`), canvas.toBuffer("image/png"));
  spec.push({ name: s.name, seed: s.seed, commander: s.commander, ops: s.ops, frames: s.frames, battle: s.battle, W, H, phase: run.phase, round: run.round });
  console.log(`${s.name}: round ${run.round} ${run.phase}, ${s.ops.length} ops, ${s.frames.length} frames`);
}
writeFileSync(join(outDir, "shots.json"), JSON.stringify(spec));
