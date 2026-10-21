// Contact sheets of the procedural art for the Godot port's visual comparison.
//
// Renders every unit (several factions, teams and poses), every building (every
// faction), resources, projectiles, construction stages and the decorations,
// through the real render/draw.ts on @napi-rs/canvas, and writes alongside
// each PNG a JSON spec of exactly what was drawn where. The Godot sheet-maker
// (godot/tests/visual/art_sheet.gd) replays the spec through the port, so the
// two images can be compared pixel for pixel.
//
//   node scripts/godot-visual-sheet.mjs ../godot/tests/visual/out
import { build } from "esbuild";
import { createCanvas } from "@napi-rs/canvas";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { aliasFonts } from "./godot-visual-fonts.mjs";

aliasFonts();
const outDir = process.argv[2] ?? "../godot/tests/visual/out";
const tmp = mkdtempSync(join(tmpdir(), "gvs-"));
const outfile = join(tmp, "draw.cjs");
await build({
  stdin: {
    contents: `export * from "./src/render/draw"; export { UNITS } from "./src/content/units"; export { FACTION_IDS } from "./src/content/factions";`,
    resolveDir: process.cwd(), loader: "ts",
  },
  bundle: true, format: "cjs", platform: "node", outfile, logLevel: "error",
});
const draw = await import(pathToFileURL(outfile).href);
const { UNITS } = draw;

// Every Entity field the art reads, with the sim's defaults (world.ts makeEntity).
function entity(over) {
  return {
    id: 7, kind: 0, team: 0, type: "villager", alive: true, x: 0, y: 0, radius: 8, facing: 0,
    hp: 100, maxHp: 100, vx: 0, vy: 0, speed: 60, attack: 8, range: 0,
    attackCooldown: 0, attackInterval: 1, armor: 0, pierceArmor: 0, armorClass: "infantry",
    visionRange: 100, carry: 0, carryKind: null, gatherCooldown: 0, amount: 0,
    buildState: 0, buildProgress: 1, popProvided: 0, rallyX: -1, rallyY: -1,
    productionQueue: [], productionTime: 0, garrison: [], gateOpen: false, gateForce: 0, farmWorker: -1,
    projElapsed: 0, projDuration: 1, abilityCooldown: 0, abilityActive: 0, slowTimer: 0, rallyTimer: 0,
    heroLevel: 0, veterancy: 0, animPhase: 0, hitFlash: 0, chargeRun: 0, variantRarity: 0,
    order: { kind: 0, tx: 0, ty: 0, target: -1 },
    ...over,
  };
}

// Which entity fields go into the spec (what the Godot side copies across).
const FIELDS = [
  "id", "kind", "team", "type", "x", "y", "radius", "facing", "hp", "maxHp", "vx", "vy",
  "attackCooldown", "attackInterval", "carry", "carryKind", "amount", "buildState", "buildProgress",
  "gateOpen", "projElapsed", "projDuration", "abilityActive", "slowTimer", "rallyTimer", "heroLevel",
  "veterancy", "animPhase", "hitFlash", "chargeRun", "variantRarity",
];
const pick = (e) => Object.fromEntries(FIELDS.map((k) => [k, e[k]]));

/**
 * A sheet: a canvas, a background, and a list of ops. Each op is replayed by
 * both sides: { op, ent?, time, lod, faction, scale, tx, ty, text?, ... }.
 */
function sheet(name, W, H, bg) {
  const canvas = createCanvas(W, H);
  const ctx = canvas.getContext("2d");
  const ops = [];
  const s = {
    name, W, H, bg, ops, ctx,
    run(o) {
      ops.push(o.ent ? { ...o, ent: pick(o.ent) } : o);
      replay(ctx, o);
    },
    save() {
      writeFileSync(join(outDir, `ts_${name}.png`), canvas.toBuffer("image/png"));
      return { name, W, H, bg, ops };
    },
  };
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);
  return s;
}

function replay(ctx, o) {
  draw.setFactionResolver(() => o.faction ?? "kingdom");
  draw.setTeamColorResolver(null);
  draw.setDrawDayPhase(o.dayPhase ?? 0);
  ctx.save();
  ctx.translate(o.tx ?? 0, o.ty ?? 0);
  ctx.scale(o.scale ?? 1, o.scale ?? 1);
  const e = o.ent;
  switch (o.op) {
    case "unit": draw.drawUnit(ctx, e, o.time ?? 0.7, o.lod ?? 0); break;
    case "building": draw.drawBuilding(ctx, e, o.time ?? 1, 0); break;
    case "resource": draw.drawResource(ctx, e, o.time ?? 1); break;
    case "projectile": draw.drawProjectile(ctx, e); break;
    case "selection": draw.drawSelectionRing(ctx, e, !!o.isPlayer); break;
    case "health": draw.drawHealthBar(ctx, e); break;
    case "text":
      ctx.fillStyle = o.color ?? "#10210a";
      ctx.font = o.font ?? "bold 11px 'Trebuchet MS', sans-serif";
      ctx.textAlign = "left";
      ctx.textBaseline = "alphabetic";
      ctx.fillText(o.text, 0, 0);
      break;
  }
  ctx.restore();
}

const FACTIONS = ["kingdom", "legion", "norse", "shogunate", "khanate", "ascendancy"];
const POSES = [
  { name: "idle", facing: 0.4, opts: { animPhase: 0.3 } },
  { name: "walk", facing: -0.6, opts: { vx: 40, animPhase: 1.25 } },
  { name: "attack", facing: 2.6, opts: { attackCooldown: 0.96, animPhase: 0.8 } },
  { name: "swing", facing: 1.2, opts: { attackCooldown: 0.66, animPhase: 0.5 } },
];
const unitIds = Object.keys(UNITS);
const specs = [];

// ---- 1. Every unit, the Kingdom, two teams, four poses (2x) ---------------
{
  const cellW = 74, cellH = 82, left = 96, top = 30, scale = 2;
  const cols = POSES.length * 2;
  const s = sheet("units_kingdom", left + cols * cellW + 10, top + unitIds.length * cellH + 10, "#6da944");
  s.run({ op: "text", text: "Kingdom — every unit · teams Azure / Crimson · idle, walk, attack, swing (2x)", tx: 8, ty: 18 });
  unitIds.forEach((type, row) => {
    const y0 = top + row * cellH;
    s.run({ op: "text", text: type, tx: 6, ty: y0 + cellH * 0.55 });
    for (let t = 0; t < 2; t++) {
      POSES.forEach((p, pi) => {
        const col = t * POSES.length + pi;
        const ent = entity({ id: 7 + row * 13 + col, team: t, type, radius: UNITS[type].radius, facing: p.facing, ...p.opts });
        s.run({ op: "unit", ent, time: 0.7, faction: "kingdom", scale, tx: left + col * cellW + cellW / 2, ty: y0 + cellH * 0.62 });
      });
    }
  });
  specs.push(s.save());
}

// ---- 2. Every unit in each other faction: idle + attack (2x) --------------
{
  const cellW = 70, cellH = 82, left = 96, top = 30, scale = 2;
  const fs = FACTIONS.slice(1);
  const cols = fs.length * 2;
  const s = sheet("units_factions", left + cols * cellW + 10, top + unitIds.length * cellH + 10, "#6da944");
  s.run({ op: "text", text: "Legion · Jarls · Shogunate · Khanate · Ascendancy — idle / attack (2x)", tx: 8, ty: 18 });
  unitIds.forEach((type, row) => {
    const y0 = top + row * cellH;
    s.run({ op: "text", text: type, tx: 6, ty: y0 + cellH * 0.55 });
    fs.forEach((f, fi) => {
      [POSES[0], POSES[2]].forEach((p, pi) => {
        const col = fi * 2 + pi;
        const ent = entity({ id: 11 + row * 7 + col, team: fi % 2 ? 1 : 0, type, radius: UNITS[type].radius, facing: p.facing, ...p.opts });
        s.run({ op: "unit", ent, time: 1.3, faction: f, scale, tx: left + col * cellW + cellW / 2, ty: y0 + cellH * 0.62 });
      });
    });
  });
  specs.push(s.save());
}

// ---- 3. Every building in every faction ------------------------------------
const BUILDINGS = [["town_center", 36], ["house", 22], ["mill", 26], ["lumber_camp", 22], ["mining_camp", 22], ["farm", 26],
  ["bridge", 16], ["barracks", 28], ["archery_range", 28], ["stable", 28], ["blacksmith", 28], ["market", 28], ["palisade", 18],
  ["stone_wall", 20], ["gate", 22], ["watch_tower", 22], ["castle", 38], ["siege_workshop", 28], ["watchfire", 16]];
{
  const cellW = 118, cellH = 132, left = 100, top = 30;
  const s = sheet("buildings", left + FACTIONS.length * cellW + 10, top + BUILDINGS.length * cellH + 10, "#74b049");
  s.run({ op: "text", text: "Every building · Kingdom, Legion, Jarls, Shogunate, Khanate, Ascendancy", tx: 8, ty: 18 });
  BUILDINGS.forEach(([type, radius], row) => {
    const y0 = top + row * cellH;
    s.run({ op: "text", text: type, tx: 6, ty: y0 + cellH * 0.55 });
    FACTIONS.forEach((f, col) => {
      const ent = entity({ id: 5 + row * 6 + col, kind: 1, team: col % 2, type, radius, hp: 1000, maxHp: 1000, gateOpen: col >= 3 && type === "gate" });
      s.run({ op: "building", ent, time: 1.0, faction: f, tx: left + col * cellW + cellW / 2, ty: y0 + cellH * 0.66 });
    });
  });
  specs.push(s.save());
}

// ---- 4. Resources, projectiles, construction, decorations ------------------
{
  const s = sheet("misc", 900, 560, "#6da944");
  let x = 40;
  s.run({ op: "text", text: "Resources · projectiles · building sites · decorations · night · LOD (2x where small)", tx: 8, ty: 18 });
  for (const [type, amount] of [["tree", 125], ["tree", 40], ["gold_mine", 800], ["gold_mine", 60], ["berries", 125], ["berries", 30]]) {
    for (const id of [3, 1234567]) {
      s.run({ op: "resource", ent: entity({ id, kind: 2, type, amount, radius: 12 }), time: 2.0, scale: 2, tx: x, ty: 80 });
      x += 66;
    }
  }
  // Projectiles mid-flight.
  x = 40;
  for (const [type, t] of [["arrow", 0.3], ["arrow", 0.5], ["rock", 0.5], ["rock", 0.8]]) {
    s.run({ op: "projectile", ent: entity({ kind: 3, type, facing: -0.4, projElapsed: t, projDuration: 1 }), scale: 2, tx: x, ty: 190 });
    x += 60;
  }
  // Building sites at a few stages.
  x = 330;
  for (const [type, radius, state, prog, f] of [["house", 22, 2, 0, "kingdom"], ["barracks", 28, 1, 0.35, "kingdom"], ["watch_tower", 22, 1, 0.7, "norse"], ["farm", 26, 1, 0.5, "kingdom"], ["castle", 38, 1, 0.55, "legion"]]) {
    s.run({ op: "building", ent: entity({ id: 9, kind: 1, type, radius, buildState: state, buildProgress: prog, hp: 300, maxHp: 1000 }), time: 1, faction: f, tx: x, ty: 200 });
    x += type === "castle" ? 120 : 96;
  }
  // Decorations: selection rings, health bars, hit flash, rarity, auras, veterancy, heroes.
  x = 50;
  const deco = [
    { ent: { type: "militia", radius: 9, veterancy: 3 }, sel: true },
    { ent: { type: "archer", radius: 8, variantRarity: 3, hitFlash: 0.6 }, hp: 40 },
    { ent: { type: "knight", radius: 11, abilityActive: 1 } },
    { ent: { type: "spearman", radius: 9, rallyTimer: 2, veterancy: 1 } },
    { ent: { type: "crossbow", radius: 8, slowTimer: 2, variantRarity: 5 } },
    { ent: { type: "hero", radius: 12, heroLevel: 3 }, sel: true },
    { ent: { type: "villager", radius: 8, carry: 10, carryKind: "wood", vx: 30, animPhase: 0.9 } },
    { ent: { type: "villager", radius: 8, carry: 10, carryKind: "gold", facing: 2.2 } },
    { ent: { type: "lancer", radius: 13, chargeRun: 999, vx: 50, animPhase: 0.4 } },
  ];
  for (const d of deco) {
    const ent = entity({ id: 21, team: 0, facing: 0.3, animPhase: 0.3, ...d.ent, hp: d.hp ?? 100 });
    if (d.sel) s.run({ op: "selection", ent, isPlayer: true, scale: 2, tx: x, ty: 330 });
    s.run({ op: "unit", ent, time: 2.2, faction: "kingdom", scale: 2, tx: x, ty: 330 });
    s.run({ op: "health", ent, scale: 2, tx: x, ty: 330 });
    x += 92;
  }
  // Night: lit windows and a big watchfire glow; LOD blobs; selected building.
  x = 60;
  for (const [type, radius] of [["house", 22], ["town_center", 36], ["watchfire", 16]]) {
    const ent = entity({ id: 4, kind: 1, type, radius, hp: 350, maxHp: 1000, hitFlash: type === "house" ? 0.5 : 0 });
    s.run({ op: "building", ent, time: 3.3, faction: "kingdom", dayPhase: 0.7, tx: x, ty: 480 });
    s.run({ op: "selection", ent, isPlayer: false, tx: x, ty: 480 });
    s.run({ op: "health", ent, tx: x, ty: 480 });
    x += 130;
  }
  for (const type of ["militia", "knight", "hero", "trebuchet"]) {
    s.run({ op: "unit", ent: entity({ type, radius: UNITS[type].radius, team: 1 }), lod: 1, scale: 2, tx: x, ty: 480 });
    x += 60;
  }
  specs.push(s.save());
}

writeFileSync(join(outDir, "sheet_spec.json"), JSON.stringify(specs));
console.log("wrote", specs.map((s) => `ts_${s.name}.png (${s.W}x${s.H}, ${s.ops.length} ops)`).join(", "));
