// Render the whole roster (idle / moving / attacking) to a PNG contact sheet so
// we can eyeball the procedural art. Bundles draw.ts with esbuild + node-canvas.
import { build } from "esbuild";
import { createCanvas } from "canvas";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const outdir = mkdtempSync(join(tmpdir(), "preview-"));
const outfile = join(outdir, "draw.cjs");
await build({
  entryPoints: ["src/render/draw.ts"],
  bundle: true, format: "cjs", platform: "node", outfile,
  external: ["canvas"], logLevel: "error",
});
const draw = await import(pathToFileURL(outfile).href);

function ent(type, team, facing, opts = {}) {
  return {
    id: 7, kind: 0, team, type, alive: true, x: 0, y: 0, radius: 16, facing,
    vx: opts.moving ? 40 : 0, vy: 0, speed: 60,
    attack: 8, range: 0, attackCooldown: opts.atk ?? 0, attackInterval: 1, armor: 0, pierceArmor: 0,
    armorClass: "infantry", visionRange: 0, animPhase: opts.phase ?? 1.4,
    hp: 100, maxHp: 100, carry: 0, carryKind: null, gatherCooldown: 0, amount: 0,
    variantRarity: 0, veterancy: 0, heroLevel: 0,
    abilityActive: 0, rallyTimer: 0, slowTimer: 0, hitFlash: 0,
    order: { kind: 0, tx: 0, ty: 0, target: -1 },
  };
}

const units = [
  "villager", "militia", "twohand", "spearman", "pikeman",
  "archer", "skirmisher", "javelin", "crossbow", "handcannon",
  "knight", "horseman", "raider", "scout",
  "catapult", "trebuchet", "ram", "hero", "king", "monk",
];

const cellW = 150, cellH = 96, cols = 4;
const rows = Math.ceil(units.length / cols);
const W = cols * cellW + 30, H = rows * cellH + 60;
const canvas = createCanvas(W, H);
const ctx = canvas.getContext("2d");
const bg = ctx.createLinearGradient(0, 0, 0, H);
bg.addColorStop(0, "#6da944"); bg.addColorStop(1, "#588f37");
ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
ctx.fillStyle = "#10210a"; ctx.font = "bold 18px sans-serif";
ctx.fillText("Banner & Blade — full roster (idle · moving · attacking)", 16, 30);
ctx.font = "12px sans-serif";

function drawAt(type, cx, cy, facing, opts) {
  const e = ent(type, 1, facing, opts);
  e.x = cx; e.y = cy;
  draw.drawUnit(ctx, e, 0.7);
}

for (let i = 0; i < units.length; i++) {
  const type = units[i];
  const col = i % cols, row = (i / cols) | 0;
  const x0 = 18 + col * cellW, y0 = 48 + row * cellH;
  ctx.fillStyle = "#10210a";
  ctx.fillText(type, x0 + 4, y0 + 12);
  const baseY = y0 + cellH * 0.66;
  drawAt(type, x0 + 28, baseY, 0, { phase: 0.3 });            // idle
  drawAt(type, x0 + 70, baseY, 0, { moving: true, phase: 1.2 }); // moving
  drawAt(type, x0 + 112, baseY, 2.6, { atk: 0.96, phase: 0.8 }); // attacking
}

writeFileSync("dist/unit-preview.png", canvas.toBuffer("image/png"));
console.log("wrote dist/unit-preview.png", W + "x" + H);
