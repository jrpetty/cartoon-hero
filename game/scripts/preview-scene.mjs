// Compose a battle "diorama" from the real draw routines (units + buildings +
// projectiles on grass) to judge how the game reads in context. Not the live
// renderer, but the same per-entity art the game uses.
import { build } from "esbuild";
import { createCanvas } from "canvas";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const outdir = mkdtempSync(join(tmpdir(), "scene-"));
const outfile = join(outdir, "draw.cjs");
await build({
  entryPoints: ["src/render/draw.ts"],
  bundle: true, format: "cjs", platform: "node", outfile,
  external: ["canvas"], logLevel: "error",
});
const draw = await import(pathToFileURL(outfile).href);

const base = (over) => ({
  id: (Math.random() * 1e6) | 0, kind: 0, team: 0, type: "militia", alive: true,
  x: 0, y: 0, radius: 16, facing: 0, vx: 0, vy: 0, speed: 60,
  attack: 8, range: 0, attackCooldown: 0, attackInterval: 1, armor: 0, pierceArmor: 0,
  armorClass: "infantry", visionRange: 0, animPhase: Math.random() * 6,
  hp: 100, maxHp: 100, carry: 0, carryKind: null, gatherCooldown: 0, amount: 0,
  buildState: 0, buildProgress: 1, popProvided: 0, rallyX: 0, rallyY: 0,
  productionQueue: [], productionTime: 0, garrison: [], gateOpen: false, gateForce: 0, farmWorker: -1,
  variantRarity: 0, veterancy: 0, heroLevel: 0,
  abilityActive: 0, rallyTimer: 0, slowTimer: 0, hitFlash: 0,
  projTargetId: -1, projSourceId: -1, projDamage: 0, projSpeed: 0, projSourceTeam: 0,
  projArmorClassBonusFrom: "", projElapsed: 0, projDuration: 1, projFromX: 0, projFromY: 0,
  order: { kind: 0, tx: 0, ty: 0, target: -1 }, ...over,
});

const W = 920, H = 540;
const canvas = createCanvas(W, H);
const ctx = canvas.getContext("2d");
// terrain: grass with a faint dirt path + some texture mottling
const g = ctx.createLinearGradient(0, 0, 0, H);
g.addColorStop(0, "#74b049"); g.addColorStop(1, "#558b34");
ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
for (let i = 0; i < 600; i++) {
  ctx.fillStyle = `rgba(${30 + Math.random() * 30},${90 + Math.random() * 40},${30},0.10)`;
  const x = Math.random() * W, y = Math.random() * H, r = 6 + Math.random() * 22;
  ctx.beginPath(); ctx.ellipse(x, y, r, r * 0.5, 0, 0, Math.PI * 2); ctx.fill();
}

const scene = [];
// buildings (back line)
scene.push(base({ kind: 1, type: "town_center", team: 0, x: 110, y: 150, radius: 36 }));
scene.push(base({ kind: 1, type: "barracks", team: 0, x: 230, y: 95, radius: 28 }));
scene.push(base({ kind: 1, type: "house", team: 0, x: 90, y: 280, radius: 20 }));
scene.push(base({ kind: 1, type: "town_center", team: 1, x: 810, y: 400, radius: 36 }));
scene.push(base({ kind: 1, type: "barracks", team: 1, x: 700, y: 460, radius: 28 }));
scene.push(base({ kind: 1, type: "stable", team: 1, x: 830, y: 250, radius: 28 }));
// resources
scene.push(base({ kind: 2, type: "tree", team: 9, x: 60, y: 430, radius: 16, amount: 100 }));
scene.push(base({ kind: 2, type: "tree", team: 9, x: 100, y: 470, radius: 16, amount: 100 }));
scene.push(base({ kind: 2, type: "gold", team: 9, x: 870, y: 120, radius: 16, amount: 100 }));

// Blue army advancing from the left (facing right ~0), Red from the right (~PI).
const blue = ["knight", "militia", "twohand", "pikeman", "archer", "crossbow", "hero", "militia", "spearman"];
const red = ["spearman", "militia", "raider", "handcannon", "skirmisher", "king", "pikeman", "horseman", "militia"];
let bi = 0;
for (let row = 0; row < 3; row++) for (let cmn = 0; cmn < 3; cmn++) {
  const t = blue[bi++];
  scene.push(base({ type: t, team: 0, x: 330 + cmn * 46, y: 230 + row * 50, facing: 0.1 + (Math.random() - 0.5) * 0.4, vx: 30, attackCooldown: Math.random() < 0.4 ? 0.95 : 0, kind: t === "knight" || t === "raider" || t === "horseman" ? 0 : 0 }));
}
let ri = 0;
for (let row = 0; row < 3; row++) for (let cmn = 0; cmn < 3; cmn++) {
  const t = red[ri++];
  scene.push(base({ type: t, team: 1, x: 560 + cmn * 46, y: 250 + row * 50, facing: Math.PI - 0.1 + (Math.random() - 0.5) * 0.4, vx: -30, attackCooldown: Math.random() < 0.4 ? 0.95 : 0 }));
}
// a few arrows in flight between the lines
for (let i = 0; i < 5; i++) {
  scene.push(base({ kind: 3, type: "arrow", x: 470 + Math.random() * 80, y: 240 + Math.random() * 120, projElapsed: 0.5, projDuration: 1, projFromX: 400, projFromY: 240, projTargetId: -1 }));
}

// draw back-to-front by y
scene.sort((a, b) => (a.kind === 1 ? a.y - 20 : a.y) - (b.kind === 1 ? b.y - 20 : b.y));
const time = 1.0;
for (const e of scene) {
  try {
    if (e.kind === 1) draw.drawBuilding(ctx, e, time, 0);
    else if (e.kind === 2) draw.drawResource(ctx, e, time);
    else if (e.kind === 3) draw.drawProjectile(ctx, e);
    else draw.drawUnit(ctx, e, time);
  } catch (err) { console.log("skip", e.type, String(err).slice(0, 60)); }
}

writeFileSync("dist/battle-scene.png", canvas.toBuffer("image/png"));
console.log("wrote dist/battle-scene.png", W + "x" + H);
