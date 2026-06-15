// PROPOSAL PROTOTYPE — a higher-fidelity, parametrically-animated soldier,
// rendered to a smooth GIF so we can judge the look + motion before rolling the
// style out to every unit. Self-contained (not yet wired into the game).
import { createCanvas } from "canvas";
import GIFEncoder from "gif-encoder-2";
import { writeFileSync } from "node:fs";

const W = 240, H = 260;
const TEAM = { main: "#3a6fd8", light: "#7da3ec", dark: "#274a90" };
const SKIN = "#e8b88a", STEEL = "#c7cdd4", STEELD = "#8f99a6", LEATHER = "#7a5a38";

function lerp(a, b, t) { return a + (b - a) * t; }
function grad(ctx, x0, y0, x1, y1, c0, c1) {
  const g = ctx.createLinearGradient(x0, y0, x1, y1);
  g.addColorStop(0, c0); g.addColorStop(1, c1); return g;
}
// soft outline used on every part for a crisp painterly read
function outline(ctx, w = 2) { ctx.strokeStyle = "rgba(20,16,10,0.45)"; ctx.lineWidth = w; ctx.stroke(); }

function drawSoldier(ctx, time) {
  const r = 30;
  // ---- animation rig -------------------------------------------------------
  const T = 2.2;                       // full loop
  const lt = (time % T) / T;           // 0..1
  const attacking = lt > 0.46 && lt < 0.82;
  const atk = attacking ? (lt - 0.46) / 0.36 : 0;
  const walking = !attacking;
  const step = walking ? Math.sin(time * 7) : 0;        // leg/arm swing
  const bob = walking ? Math.abs(Math.sin(time * 7)) * 2.2 : 0.6 + Math.sin(time * 3) * 0.4;
  // weapon-arm swing angle (radians): gentle bob, or wind-up→strike→recover
  let swing;
  if (!attacking) swing = -0.35 + Math.sin(time * 7 + 1) * 0.12;
  else if (atk < 0.34) swing = lerp(-0.35, -1.35, atk / 0.34);       // anticipation
  else if (atk < 0.52) swing = lerp(-1.35, 1.25, (atk - 0.34) / 0.18); // fast strike
  else swing = lerp(1.25, -0.35, (atk - 0.52) / 0.48);                // recover

  ctx.save();
  ctx.translate(0, -bob);

  // ---- ground shadow (squashes as the body lifts) --------------------------
  ctx.save();
  ctx.translate(0, bob); // shadow stays on the ground
  ctx.fillStyle = "rgba(20,30,10,0.28)";
  ctx.beginPath();
  ctx.ellipse(2, r * 0.95, r * (0.92 - bob * 0.02), r * 0.34, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();

  // ---- legs (behind torso) -------------------------------------------------
  for (const side of [1, -1]) {
    const sw = step * side;
    const hipX = side * r * 0.26;
    const footX = hipX + sw * r * 0.42;
    const lift = Math.max(0, sw) * r * 0.18;
    ctx.strokeStyle = grad(ctx, hipX, r * 0.3, footX, r * 0.85, TEAM.dark, "#1b3464");
    ctx.lineCap = "round";
    ctx.lineWidth = r * 0.26;
    ctx.beginPath();
    ctx.moveTo(hipX, r * 0.35);
    ctx.lineTo(footX, r * 0.82 - lift);
    ctx.stroke();
    // boot
    ctx.fillStyle = LEATHER;
    ctx.beginPath();
    ctx.ellipse(footX + r * 0.06, r * 0.82 - lift, r * 0.16, r * 0.1, 0, 0, Math.PI * 2);
    ctx.fill();
  }

  // ---- far arm + round shield (off-hand) -----------------------------------
  ctx.fillStyle = grad(ctx, -r * 0.7, -r * 0.4, -r * 0.7, r * 0.4, STEEL, STEELD);
  ctx.beginPath();
  ctx.ellipse(-r * 0.62, r * 0.04, r * 0.42, r * 0.5, -0.12, 0, Math.PI * 2);
  ctx.fill(); outline(ctx, 1.6);
  ctx.fillStyle = TEAM.main; // team boss
  ctx.beginPath(); ctx.arc(-r * 0.62, r * 0.04, r * 0.16, 0, Math.PI * 2); ctx.fill();
  ctx.strokeStyle = "rgba(255,255,255,0.5)";
  ctx.lineWidth = 1.4;
  ctx.beginPath(); ctx.arc(-r * 0.62, r * 0.04, r * 0.3, 0, Math.PI * 2); ctx.stroke();

  // ---- torso (layered: surcoat over mail, with shading + rim light) --------
  ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.45, TEAM.light, TEAM.dark);
  ctx.beginPath();
  ctx.moveTo(-r * 0.5, r * 0.42);
  ctx.quadraticCurveTo(-r * 0.6, -r * 0.5, 0, -r * 0.58);
  ctx.quadraticCurveTo(r * 0.6, -r * 0.5, r * 0.5, r * 0.42);
  ctx.quadraticCurveTo(0, r * 0.6, -r * 0.5, r * 0.42);
  ctx.closePath();
  ctx.fill(); outline(ctx, 2);
  // belt
  ctx.fillStyle = LEATHER;
  ctx.fillRect(-r * 0.46, r * 0.26, r * 0.92, r * 0.12);
  ctx.fillStyle = "#d8b24a";
  ctx.fillRect(-r * 0.08, r * 0.25, r * 0.16, r * 0.14);
  // rim light along the top-left
  ctx.strokeStyle = "rgba(255,255,255,0.35)";
  ctx.lineWidth = 1.6;
  ctx.beginPath();
  ctx.moveTo(-r * 0.5, r * 0.2);
  ctx.quadraticCurveTo(-r * 0.58, -r * 0.45, -r * 0.04, -r * 0.56);
  ctx.stroke();
  // steel pauldrons
  for (const sx of [-1, 1]) {
    ctx.fillStyle = grad(ctx, sx * r * 0.5, -r * 0.5, sx * r * 0.5, -r * 0.2, STEEL, STEELD);
    ctx.beginPath();
    ctx.ellipse(sx * r * 0.46, -r * 0.34, r * 0.22, r * 0.16, sx * 0.4, 0, Math.PI * 2);
    ctx.fill(); outline(ctx, 1.4);
  }

  // ---- head: skin + steel helm + team plume --------------------------------
  const hy = -r * 0.78;
  ctx.fillStyle = SKIN;
  ctx.beginPath(); ctx.arc(0, hy, r * 0.3, 0, Math.PI * 2); ctx.fill(); outline(ctx, 1.4);
  ctx.fillStyle = grad(ctx, 0, hy - r * 0.3, 0, hy + r * 0.1, STEEL, STEELD);
  ctx.beginPath(); ctx.arc(0, hy - r * 0.04, r * 0.3, Math.PI * 0.96, Math.PI * 2.04); ctx.fill();
  ctx.beginPath(); ctx.arc(0, hy, r * 0.31, Math.PI * 1.05, Math.PI * 1.95); ctx.fill(); // brow guard
  outline(ctx, 1.2);
  // nasal bar
  ctx.strokeStyle = STEELD; ctx.lineWidth = 1.6;
  ctx.beginPath(); ctx.moveTo(0, hy - r * 0.1); ctx.lineTo(0, hy + r * 0.18); ctx.stroke();
  // plume
  ctx.fillStyle = TEAM.main;
  ctx.beginPath();
  ctx.moveTo(0, hy - r * 0.34);
  ctx.quadraticCurveTo(r * 0.18, hy - r * 0.7, r * 0.04, hy - r * 0.34);
  ctx.fill();

  // ---- weapon arm + sword (swings on attack) -------------------------------
  const shX = r * 0.42, shY = -r * 0.18; // shoulder pivot
  ctx.save();
  ctx.translate(shX, shY);
  ctx.rotate(swing);
  // upper arm
  ctx.strokeStyle = grad(ctx, 0, 0, r * 0.5, 0, TEAM.light, TEAM.main);
  ctx.lineCap = "round"; ctx.lineWidth = r * 0.2;
  ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(r * 0.5, r * 0.02); ctx.stroke();
  // gauntlet
  ctx.fillStyle = STEELD;
  ctx.beginPath(); ctx.arc(r * 0.52, r * 0.02, r * 0.12, 0, Math.PI * 2); ctx.fill();
  // sword: crossguard, then a metallic blade with a bright edge highlight
  ctx.fillStyle = "#caa64a";
  ctx.fillRect(r * 0.46, -r * 0.14, r * 0.08, r * 0.3); // crossguard
  ctx.strokeStyle = grad(ctx, r * 0.5, 0, r * 1.6, 0, "#f2f4f8", STEELD);
  ctx.lineWidth = r * 0.12;
  ctx.beginPath(); ctx.moveTo(r * 0.54, r * 0.0); ctx.lineTo(r * 1.62, -r * 0.02); ctx.stroke();
  ctx.strokeStyle = "rgba(255,255,255,0.9)"; // edge glint
  ctx.lineWidth = 1.4;
  ctx.beginPath(); ctx.moveTo(r * 0.6, -r * 0.04); ctx.lineTo(r * 1.6, -r * 0.05); ctx.stroke();
  ctx.restore();

  ctx.restore();
}

// ---- render the GIF --------------------------------------------------------
const fps = 30, secs = 2.2;
const frames = Math.round(fps * secs);
const enc = new GIFEncoder(W, H, "neuquant", true);
enc.setDelay(Math.round(1000 / fps));
enc.setRepeat(0);
enc.setQuality(8);
enc.start();
const canvas = createCanvas(W, H);
const ctx = canvas.getContext("2d");
for (let f = 0; f < frames; f++) {
  const time = (f / fps);
  // grass backdrop
  const bg = ctx.createLinearGradient(0, 0, 0, H);
  bg.addColorStop(0, "#6da944"); bg.addColorStop(1, "#5c9639");
  ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
  ctx.save();
  ctx.translate(W / 2, H / 2 + 30);
  ctx.scale(2.4, 2.4);
  drawSoldier(ctx, time);
  ctx.restore();
  enc.addFrame(ctx);
}
enc.finish();
writeFileSync("dist/example-anim.gif", enc.out.getData());
console.log("wrote dist/example-anim.gif", W + "x" + H, frames + " frames");
