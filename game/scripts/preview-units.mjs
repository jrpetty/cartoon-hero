// Render a lineup of unit sprites to a PNG so we can eyeball the procedural art.
// Bundles draw.ts with esbuild, then draws each unit with node-canvas.
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
  bundle: true,
  format: "cjs",
  platform: "node",
  outfile,
  external: ["canvas"],
  logLevel: "error",
});
const draw = await import(pathToFileURL(outfile).href);

// Minimal entity factory with every field drawUnit touches.
function ent(type, team, facing = 0, opts = {}) {
  return {
    id: Math.floor(Math.random() * 1e6), kind: 0, team, type, alive: true,
    x: 0, y: 0, radius: 15, facing,
    vx: opts.moving ? 30 : 0, vy: 0, speed: 60,
    attack: 8, range: 0, attackCooldown: opts.atk ?? 0, attackInterval: 1, armor: 0, pierceArmor: 0,
    armorClass: "infantry", visionRange: 0,
    animPhase: opts.phase ?? 1.2,
    hp: 100, maxHp: 100, carry: 0, carryKind: null, gatherCooldown: 0, amount: 0,
    variantRarity: 0, veterancy: 0, heroLevel: 0,
    abilityActive: 0, rallyTimer: 0, slowTimer: 0, hitFlash: 0,
    order: { kind: 0, tx: 0, ty: 0, target: -1 },
  };
}

// Pairs: [label, type, oldTwinLabel, oldTwinType]
const rows = [
  ["Two-Handed Sword", "twohand", "Militia (was)", "militia"],
  ["Pikeman", "pikeman", "Spearman (was)", "spearman"],
  ["Scout", "scout", "Raider (was)", "raider"],
  ["Javelin Thrower", "javelin", "Skirmisher (was)", "skirmisher"],
  ["Hand Cannoneer", "handcannon", "Crossbow (was)", "crossbow"],
  ["King", "king", "Champion (was)", "hero"],
];

const cell = 120;
const cols = 4; // new + old, each shown moving + attacking-ish via two facings
const W = cell * cols + 40;
const H = cell * rows.length + 60;
const canvas = createCanvas(W, H);
const ctx = canvas.getContext("2d");
ctx.fillStyle = "#6da944"; // grass
ctx.fillRect(0, 0, W, H);
ctx.font = "bold 16px sans-serif";
ctx.fillStyle = "#1c2a12";
ctx.fillText("New procedural sprites (left two) vs the sprite they used to share (right two)", 16, 28);

function drawAt(type, cx, cy, facing, opts) {
  const e = ent(type, 1, facing, opts); // team 1 = Crimson, vivid
  e.x = cx; e.y = cy;
  ctx.save();
  draw.drawUnit(ctx, e, 0.6);
  ctx.restore();
}

ctx.font = "12px sans-serif";
for (let r = 0; r < rows.length; r++) {
  const [label, type, oldLabel, oldType] = rows[r];
  const y = 70 + r * cell + cell * 0.4;
  // new: facing right (moving) + facing down-left (attacking)
  drawAt(type, 50, y, 0, { moving: true });
  drawAt(type, 50 + cell, y, 2.4, { atk: 0.95 });
  // old twin
  drawAt(oldType, 50 + cell * 2, y, 0, { moving: true });
  drawAt(oldType, 50 + cell * 3, y, 2.4, { atk: 0.95 });
  ctx.fillStyle = "#10210a";
  ctx.fillText(label, 20, 70 + r * cell + 16);
  ctx.fillStyle = "#3a2a10";
  ctx.fillText(oldLabel, 20 + cell * 2, 70 + r * cell + 16);
}

writeFileSync("dist/unit-preview.png", canvas.toBuffer("image/png"));
console.log("wrote dist/unit-preview.png", W + "x" + H);
