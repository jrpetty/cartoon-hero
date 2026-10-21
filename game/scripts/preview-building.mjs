// PROPOSAL: upgraded building art in the same shaded painterly style as the new
// units. Left = current (real drawBuilding); right = proposed (self-contained
// here, not yet wired into the game). For a quick yes/no before the full rollout.
import { build } from "esbuild";
import { createCanvas } from "canvas";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const outdir = mkdtempSync(join(tmpdir(), "bld-"));
const outfile = join(outdir, "draw.cjs");
await build({ entryPoints: ["src/render/draw.ts"], bundle: true, format: "cjs", platform: "node", outfile, external: ["canvas"], logLevel: "error" });
const draw = await import(pathToFileURL(outfile).href);

// palette (mirrors src/render/palette.ts)
const P = { stone: "#9b958a", stoneDark: "#7d776c", stoneLight: "#b5afa3", wood: "#8a6238", woodDark: "#6e4c2a", woodLight: "#a87f4c", thatch: "#c2a45c", thatchDark: "#a3873f", roofSlate: "#647183" };
const TEAM = { main: "#3a6fd8", light: "#7da3ec", dark: "#274a90" };
function grad(ctx, x0, y0, x1, y1, a, b) { const g = ctx.createLinearGradient(x0, y0, x1, y1); g.addColorStop(0, a); g.addColorStop(1, b); return g; }
function shade(hex, f) { let r, g, b; if (hex[0] === "#") { r = parseInt(hex.slice(1, 3), 16); g = parseInt(hex.slice(3, 5), 16); b = parseInt(hex.slice(5, 7), 16); } else { const m = hex.match(/[\d.]+/g); r = +m[0]; g = +m[1]; b = +m[2]; } const a = (c) => Math.max(0, Math.min(255, Math.round(f > 0 ? c + (255 - c) * f : c * (1 + f)))); return `rgb(${a(r)},${a(g)},${a(b)})`; }
function outline(ctx, w = 1.6) { ctx.strokeStyle = "rgba(20,16,10,0.45)"; ctx.lineWidth = w; ctx.stroke(); }
function shadowEll(ctx, x, y, rx, ry) { ctx.fillStyle = "rgba(20,24,12,0.26)"; ctx.beginPath(); ctx.ellipse(x, y, rx, ry, 0, 0, Math.PI * 2); ctx.fill(); }

// shared parts -------------------------------------------------------------
function stoneWall(ctx, x, y, w, h) {
  ctx.fillStyle = grad(ctx, x, y, x, y + h, P.stoneLight, P.stoneDark);
  ctx.beginPath(); ctx.roundRect(x, y, w, h, 4); ctx.fill(); outline(ctx, 1.6);
  // stone courses
  ctx.strokeStyle = "rgba(80,74,66,0.4)"; ctx.lineWidth = 1;
  for (let yy = y + h * 0.3; yy < y + h; yy += h * 0.28) { ctx.beginPath(); ctx.moveTo(x + 2, yy); ctx.lineTo(x + w - 2, yy); ctx.stroke(); }
}
function timberPosts(ctx, x, y, w, h) {
  ctx.fillStyle = P.woodDark;
  for (const px of [x, x + w - 4]) ctx.fillRect(px, y, 4, h);
}
function door(ctx, cx, baseY, w, h) {
  ctx.fillStyle = P.woodDark;
  ctx.beginPath(); ctx.moveTo(cx - w / 2, baseY); ctx.lineTo(cx - w / 2, baseY - h * 0.6); ctx.quadraticCurveTo(cx, baseY - h, cx + w / 2, baseY - h * 0.6); ctx.lineTo(cx + w / 2, baseY); ctx.fill();
  ctx.strokeStyle = P.woodLight; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(cx, baseY); ctx.lineTo(cx, baseY - h * 0.7); ctx.stroke();
}
function window(ctx, cx, cy, s) {
  ctx.fillStyle = "rgba(20,16,10,0.6)"; ctx.fillRect(cx - s, cy - s, s * 2, s * 2);
  ctx.fillStyle = "rgba(255,210,120,0.9)"; ctx.fillRect(cx - s + 1, cy - s + 1, s * 2 - 2, s * 2 - 2);
}
function banner(ctx, x, topY, len, tc) {
  ctx.strokeStyle = P.woodDark; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(x, topY); ctx.lineTo(x, topY - len); ctx.stroke();
  ctx.fillStyle = tc.main; ctx.beginPath(); ctx.moveTo(x, topY - len); ctx.lineTo(x + 16, topY - len + 4); ctx.lineTo(x + 14, topY - len + 11); ctx.lineTo(x, topY - len + 14); ctx.fill();
  ctx.fillStyle = tc.light; ctx.beginPath(); ctx.arc(x + 6, topY - len + 7, 2, 0, Math.PI * 2); ctx.fill();
}
function tiledRoof(ctx, cx, eaveY, halfW, peakY, slate) {
  // hipped roof as a shaded trapezoid + ridge
  ctx.fillStyle = grad(ctx, cx, peakY, cx, eaveY, shade(slate, 0.2), shade(slate, -0.2));
  ctx.beginPath();
  ctx.moveTo(cx - halfW, eaveY); ctx.lineTo(cx - halfW * 0.45, peakY); ctx.lineTo(cx + halfW * 0.45, peakY); ctx.lineTo(cx + halfW, eaveY); ctx.closePath();
  ctx.fill(); outline(ctx, 1.6);
  // tile rows
  ctx.strokeStyle = "rgba(255,255,255,0.12)"; ctx.lineWidth = 1;
  for (let t = 0.25; t < 1; t += 0.25) { const y = peakY + (eaveY - peakY) * t; const hw = halfW * 0.45 + (halfW - halfW * 0.45) * t; ctx.beginPath(); ctx.moveTo(cx - hw, y); ctx.lineTo(cx + hw, y); ctx.stroke(); }
  // ridge highlight
  ctx.strokeStyle = shade(slate, 0.35); ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(cx - halfW * 0.45, peakY); ctx.lineTo(cx + halfW * 0.45, peakY); ctx.stroke();
}

// proposed buildings -------------------------------------------------------
function propTownCenter(ctx, tc) {
  shadowEll(ctx, 0, 40, 64, 18);
  // two flanking stone towers
  for (const sx of [-52, 44]) { stoneWall(ctx, sx, -20, 18, 56); tiledRoof(ctx, sx + 9, -20, 14, -40, P.roofSlate); }
  // main hall
  stoneWall(ctx, -38, -6, 76, 44); timberPosts(ctx, -38, -6, 76, 44);
  window(ctx, -18, 14, 4); window(ctx, 18, 14, 4); door(ctx, 0, 38, 18, 26);
  tiledRoof(ctx, 0, -6, 48, -44, P.roofSlate);
  banner(ctx, 0, -44, 22, tc);
}
function propHouse(ctx, tc) {
  shadowEll(ctx, 0, 30, 40, 12);
  stoneWall(ctx, -28, 0, 56, 32); timberPosts(ctx, -28, 0, 56, 32);
  window(ctx, -12, 14, 3.5); door(ctx, 12, 32, 12, 20);
  // thatch roof
  tiledRoof(ctx, 0, 0, 36, -30, P.thatch);
  // chimney + smoke
  ctx.fillStyle = P.stoneDark; ctx.fillRect(16, -26, 7, 14);
  ctx.fillStyle = "rgba(220,220,220,0.5)"; for (let i = 0; i < 3; i++) { ctx.beginPath(); ctx.arc(19 + i * 2, -32 - i * 9, 4 + i * 1.5, 0, Math.PI * 2); ctx.fill(); }
}
function propBarracks(ctx, tc) {
  shadowEll(ctx, 0, 34, 52, 15);
  stoneWall(ctx, -44, -4, 88, 42); timberPosts(ctx, -44, -4, 88, 42);
  window(ctx, -24, 12, 4); window(ctx, 24, 12, 4); door(ctx, 0, 38, 20, 24);
  tiledRoof(ctx, 0, -4, 52, -34, shade(P.roofSlate, -0.1));
  // weapon rack out front
  ctx.strokeStyle = P.woodDark; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(-40, 40); ctx.lineTo(-28, 40); ctx.stroke();
  ctx.strokeStyle = "#c7cdd4"; ctx.lineWidth = 1.5; for (const sx of [-38, -34, -30]) { ctx.beginPath(); ctx.moveTo(sx, 40); ctx.lineTo(sx + 2, 26); ctx.stroke(); }
  banner(ctx, 36, -34, 18, tc);
}

// compose comparison sheet -------------------------------------------------
const items = [
  ["town_center", 36, propTownCenter],
  ["house", 20, propHouse],
  ["barracks", 28, propBarracks],
];
const cellW = 220, cellH = 200, W = cellW * 2 + 30, H = cellH * items.length + 50;
const canvas = createCanvas(W, H);
const ctx = canvas.getContext("2d");
const bg = ctx.createLinearGradient(0, 0, 0, H); bg.addColorStop(0, "#74b049"); bg.addColorStop(1, "#558b34");
ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
ctx.fillStyle = "#10210a"; ctx.font = "bold 17px sans-serif";
ctx.fillText("Buildings — current (left) vs proposed (right)", 16, 30);

const bld = (type, radius, x, y) => { const e = { id: 1, kind: 1, team: 0, type, alive: true, x, y, radius, facing: 0, buildState: 0, buildProgress: 1, hp: 1000, maxHp: 1000, popProvided: 0, rallyX: x, rallyY: y, productionQueue: [], productionTime: 0, garrison: [], gateOpen: false, gateForce: 0, farmWorker: -1, amount: 0, animPhase: 0, vx: 0, vy: 0, variantRarity: 0, veterancy: 0, heroLevel: 0, abilityActive: 0, rallyTimer: 0, slowTimer: 0, hitFlash: 0, order: { kind: 0, tx: 0, ty: 0, target: -1 } }; return e; };

for (let i = 0; i < items.length; i++) {
  const [type, radius, prop] = items[i];
  const cy = 60 + i * cellH + cellH * 0.5;
  ctx.fillStyle = "#10210a"; ctx.font = "13px sans-serif"; ctx.fillText(type, 18, 60 + i * cellH + 16);
  try { draw.drawBuilding(ctx, bld(type, radius, cellW * 0.5, cy), 1.0, 0); } catch (e) { console.log("cur skip", type, String(e).slice(0, 50)); }
  ctx.save(); ctx.translate(cellW * 1.5, cy); ctx.scale(1.25, 1.25); prop(ctx, TEAM); ctx.restore();
}
writeFileSync("dist/building-preview.png", canvas.toBuffer("image/png"));
console.log("wrote dist/building-preview.png", W + "x" + H);
