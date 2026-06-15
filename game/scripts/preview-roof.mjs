// Compare how much team colour to put on roofs: none / accents / subtle tint /
// full, across two team colours, so we can pick the right amount.
import { createCanvas } from "canvas";
import { writeFileSync } from "node:fs";

const P = { stone: "#9b958a", stoneDark: "#7d776c", stoneLight: "#b5afa3", wood: "#8a6238", woodDark: "#6e4c2a", woodLight: "#a87f4c", roofSlate: "#647183" };
const TEAMS = [{ main: "#3a6fd8", light: "#7da3ec", dark: "#274a90", name: "Azure" }, { main: "#d8403a", light: "#ec837d", dark: "#902a27", name: "Crimson" }];
function grad(ctx, x0, y0, x1, y1, a, b) { const g = ctx.createLinearGradient(x0, y0, x1, y1); g.addColorStop(0, a); g.addColorStop(1, b); return g; }
function rgb(hex) { if (hex[0] === "#") return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)]; const m = hex.match(/[\d.]+/g); return [+m[0], +m[1], +m[2]]; }
function shade(hex, f) { const [r, g, b] = rgb(hex); const a = (c) => Math.max(0, Math.min(255, Math.round(f > 0 ? c + (255 - c) * f : c * (1 + f)))); return `rgb(${a(r)},${a(g)},${a(b)})`; }
function blend(h1, h2, t) { const a = rgb(h1), b = rgb(h2); return `rgb(${Math.round(a[0] + (b[0] - a[0]) * t)},${Math.round(a[1] + (b[1] - a[1]) * t)},${Math.round(a[2] + (b[2] - a[2]) * t)})`; }
function outline(ctx, w = 1.6) { ctx.strokeStyle = "rgba(20,16,10,0.45)"; ctx.lineWidth = w; ctx.stroke(); }
function shadowEll(ctx, x, y, rx, ry) { ctx.fillStyle = "rgba(20,24,12,0.26)"; ctx.beginPath(); ctx.ellipse(x, y, rx, ry, 0, 0, Math.PI * 2); ctx.fill(); }
function stoneWall(ctx, x, y, w, h) { ctx.fillStyle = grad(ctx, x, y, x, y + h, P.stoneLight, P.stoneDark); ctx.beginPath(); ctx.roundRect(x, y, w, h, 4); ctx.fill(); outline(ctx, 1.6); ctx.strokeStyle = "rgba(80,74,66,0.4)"; ctx.lineWidth = 1; for (let yy = y + h * 0.3; yy < y + h; yy += h * 0.28) { ctx.beginPath(); ctx.moveTo(x + 2, yy); ctx.lineTo(x + w - 2, yy); ctx.stroke(); } }
function timberPosts(ctx, x, y, w, h) { ctx.fillStyle = P.woodDark; for (const px of [x, x + w - 4]) ctx.fillRect(px, y, 4, h); }
function door(ctx, cx, baseY, w, h) { ctx.fillStyle = P.woodDark; ctx.beginPath(); ctx.moveTo(cx - w / 2, baseY); ctx.lineTo(cx - w / 2, baseY - h * 0.6); ctx.quadraticCurveTo(cx, baseY - h, cx + w / 2, baseY - h * 0.6); ctx.lineTo(cx + w / 2, baseY); ctx.fill(); }
function win(ctx, cx, cy, s) { ctx.fillStyle = "rgba(20,16,10,0.6)"; ctx.fillRect(cx - s, cy - s, s * 2, s * 2); ctx.fillStyle = "rgba(255,210,120,0.9)"; ctx.fillRect(cx - s + 1, cy - s + 1, s * 2 - 2, s * 2 - 2); }

// mode: "none" | "accent" | "tint" | "full"
function roof(ctx, cx, eaveY, halfW, peakY, tc, mode) {
  let slate = P.roofSlate;
  if (mode === "tint") slate = blend(P.roofSlate, tc.main, 0.4);
  else if (mode === "full") slate = tc.main;
  ctx.fillStyle = grad(ctx, cx, peakY, cx, eaveY, shade(slate, 0.2), shade(slate, -0.2));
  ctx.beginPath(); ctx.moveTo(cx - halfW, eaveY); ctx.lineTo(cx - halfW * 0.45, peakY); ctx.lineTo(cx + halfW * 0.45, peakY); ctx.lineTo(cx + halfW, eaveY); ctx.closePath(); ctx.fill(); outline(ctx, 1.6);
  ctx.strokeStyle = "rgba(255,255,255,0.12)"; ctx.lineWidth = 1;
  for (let t = 0.25; t < 1; t += 0.25) { const y = peakY + (eaveY - peakY) * t; const hw = halfW * 0.45 + (halfW - halfW * 0.45) * t; ctx.beginPath(); ctx.moveTo(cx - hw, y); ctx.lineTo(cx + hw, y); ctx.stroke(); }
  if (mode === "accent") {
    // team ridge cap + gable trim along the eaves + corner pennants
    ctx.strokeStyle = tc.main; ctx.lineWidth = 3.2; ctx.beginPath(); ctx.moveTo(cx - halfW * 0.45, peakY); ctx.lineTo(cx + halfW * 0.45, peakY); ctx.stroke();
    ctx.strokeStyle = tc.light; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(cx - halfW, eaveY); ctx.lineTo(cx - halfW * 0.45, peakY); ctx.moveTo(cx + halfW, eaveY); ctx.lineTo(cx + halfW * 0.45, peakY); ctx.stroke();
    for (const s of [-1, 1]) { ctx.fillStyle = tc.main; ctx.beginPath(); ctx.moveTo(cx + s * halfW * 0.45, peakY); ctx.lineTo(cx + s * halfW * 0.45 + s * 10, peakY + 2); ctx.lineTo(cx + s * halfW * 0.45, peakY + 7); ctx.fill(); }
  } else {
    ctx.strokeStyle = shade(slate, 0.35); ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(cx - halfW * 0.45, peakY); ctx.lineTo(cx + halfW * 0.45, peakY); ctx.stroke();
  }
}
function banner(ctx, x, topY, len, tc) { ctx.strokeStyle = P.woodDark; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(x, topY); ctx.lineTo(x, topY - len); ctx.stroke(); ctx.fillStyle = tc.main; ctx.beginPath(); ctx.moveTo(x, topY - len); ctx.lineTo(x + 15, topY - len + 4); ctx.lineTo(x + 13, topY - len + 10); ctx.lineTo(x, topY - len + 13); ctx.fill(); }

function townCenter(ctx, tc, mode) {
  shadowEll(ctx, 0, 40, 60, 17);
  for (const sx of [-50, 42]) { stoneWall(ctx, sx, -18, 18, 54); roof(ctx, sx + 9, -18, 14, -38, tc, mode); }
  stoneWall(ctx, -36, -4, 72, 42); timberPosts(ctx, -36, -4, 72, 42);
  win(ctx, -16, 14, 4); win(ctx, 16, 14, 4); door(ctx, 0, 36, 18, 24);
  roof(ctx, 0, -4, 46, -42, tc, mode);
  banner(ctx, 0, -42, 20, tc);
}

const modes = [["tint", "Subtle tint (40%)"], ["full", "Full team colour"]];
const cw = 300, ch = 210, W = cw * modes.length + 20, H = ch * TEAMS.length + 50;
const canvas = createCanvas(W, H); const ctx = canvas.getContext("2d");
const bg = ctx.createLinearGradient(0, 0, 0, H); bg.addColorStop(0, "#74b049"); bg.addColorStop(1, "#558b34"); ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
ctx.fillStyle = "#10210a"; ctx.font = "bold 17px sans-serif"; ctx.fillText("Subtle tint vs Full team colour — Town Center, two teams", 14, 28);
ctx.font = "13px sans-serif";
for (let m = 0; m < modes.length; m++) { ctx.fillStyle = "#10210a"; ctx.fillText(modes[m][1], 18 + m * cw + cw * 0.3, 46); }
for (let t = 0; t < TEAMS.length; t++) for (let m = 0; m < modes.length; m++) {
  ctx.save(); ctx.translate(12 + m * cw + cw * 0.5, 60 + t * ch + ch * 0.55); ctx.scale(1.55, 1.55); townCenter(ctx, TEAMS[t], modes[m][0]); ctx.restore();
}
writeFileSync("dist/roof-preview.png", canvas.toBuffer("image/png"));
console.log("wrote dist/roof-preview.png", W + "x" + H);
