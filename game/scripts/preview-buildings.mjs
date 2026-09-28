// Render every building type via the REAL drawBuilding (two teams) to verify the
// shaded-building rollout with full team-colour roofs.
import { build } from "esbuild";
import { createCanvas } from "canvas";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const outdir = mkdtempSync(join(tmpdir(), "blds-"));
const outfile = join(outdir, "draw.cjs");
await build({ entryPoints: ["src/render/draw.ts"], bundle: true, format: "cjs", platform: "node", outfile, external: ["canvas"], logLevel: "error" });
const draw = await import(pathToFileURL(outfile).href);

const bld = (type, team, x, y, radius) => ({ id: 5, kind: 1, team, type, alive: true, x, y, radius, facing: 0, buildState: 0, buildProgress: 1, hp: 1000, maxHp: 1000, popProvided: 0, rallyX: x, rallyY: y, productionQueue: [], productionTime: 0, garrison: [], gateOpen: false, gateForce: 0, farmWorker: -1, amount: 0, animPhase: 0, vx: 0, vy: 0, variantRarity: 0, veterancy: 0, heroLevel: 0, abilityActive: 0, rallyTimer: 0, slowTimer: 0, hitFlash: 0, order: { kind: 0, tx: 0, ty: 0, target: -1 } });

const list = [["town_center", 36], ["house", 22], ["mill", 26], ["lumber_camp", 22], ["mining_camp", 22], ["farm", 26], ["barracks", 28], ["archery_range", 28], ["stable", 28], ["blacksmith", 28], ["market", 28], ["palisade", 18], ["stone_wall", 20], ["gate", 22], ["watch_tower", 22], ["castle", 38], ["siege_workshop", 28], ["watchfire", 16]];

const cols = 4, cellW = 200, cellH = 150;
const rows = Math.ceil(list.length / cols);
const W = cols * cellW + 20, H = rows * cellH + 50;
const canvas = createCanvas(W, H); const ctx = canvas.getContext("2d");
const bg = ctx.createLinearGradient(0, 0, 0, H); bg.addColorStop(0, "#74b049"); bg.addColorStop(1, "#558b34"); ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
ctx.fillStyle = "#10210a"; ctx.font = "bold 17px sans-serif"; ctx.fillText("All buildings — full team-colour roofs (Azure left / Crimson right of each cell)", 14, 28);
ctx.font = "12px sans-serif";

for (let i = 0; i < list.length; i++) {
  const [type, radius] = list[i];
  const col = i % cols, row = (i / cols) | 0;
  const x0 = 12 + col * cellW, y0 = 44 + row * cellH;
  ctx.fillStyle = "#10210a"; ctx.fillText(type, x0 + 6, y0 + 14);
  const cy = y0 + cellH * 0.62;
  try { draw.drawBuilding(ctx, bld(type, 0, x0 + cellW * 0.3, cy, radius), 1.0, 0); } catch (e) { console.log("L", type, String(e).slice(0, 60)); }
  try { draw.drawBuilding(ctx, bld(type, 1, x0 + cellW * 0.72, cy, radius), 1.0, 0); } catch (e) { console.log("R", type, String(e).slice(0, 60)); }
}
writeFileSync("dist/buildings-all.png", canvas.toBuffer("image/png"));
console.log("wrote dist/buildings-all.png", W + "x" + H);
