// Drawn icons for the main menu's tiles.
//
// Emoji in a label render differently on every platform (and as boxes on
// some), and they never matched the game's painted look. These are the same
// kind of shapes the game draws its world with: flat fills, a darker outline,
// one highlight. Each is drawn centred on (x, y) at roughly `s` pixels across.

import { shade, withAlpha } from "../render/palette";

type Ctx = CanvasRenderingContext2D;

const INK = "#1e160c";
const GOLD = "#e8c060";
const STEEL = "#cfd6de";

function stroke(ctx: Ctx, w: number) {
  ctx.strokeStyle = INK;
  ctx.lineWidth = w;
  ctx.lineJoin = "round";
  ctx.lineCap = "round";
  ctx.stroke();
}

function sword(ctx: Ctx, angle: number, u: number) {
  ctx.save();
  ctx.rotate(angle);
  // blade
  ctx.beginPath();
  ctx.moveTo(-u * 0.07, u * 0.18);
  ctx.lineTo(-u * 0.07, -u * 0.62);
  ctx.lineTo(0, -u * 0.76);
  ctx.lineTo(u * 0.07, -u * 0.62);
  ctx.lineTo(u * 0.07, u * 0.18);
  ctx.closePath();
  ctx.fillStyle = STEEL;
  ctx.fill();
  stroke(ctx, u * 0.035);
  ctx.fillStyle = withAlpha("#ffffff", 0.55);
  ctx.fillRect(-u * 0.02, -u * 0.6, u * 0.03, u * 0.74);
  // guard, grip, pommel
  ctx.beginPath();
  ctx.roundRect(-u * 0.24, u * 0.16, u * 0.48, u * 0.09, u * 0.04);
  ctx.fillStyle = GOLD;
  ctx.fill();
  stroke(ctx, u * 0.03);
  ctx.beginPath();
  ctx.rect(-u * 0.045, u * 0.25, u * 0.09, u * 0.26);
  ctx.fillStyle = "#6a3e22";
  ctx.fill();
  stroke(ctx, u * 0.03);
  ctx.beginPath();
  ctx.arc(0, u * 0.56, u * 0.07, 0, Math.PI * 2);
  ctx.fillStyle = GOLD;
  ctx.fill();
  stroke(ctx, u * 0.03);
  ctx.restore();
}

/** Crossed swords over a round shield: Skirmish. */
export function iconSkirmish(ctx: Ctx, x: number, y: number, s: number, accent: string) {
  const u = s / 1.6;
  ctx.save();
  ctx.translate(x, y);
  ctx.beginPath();
  ctx.arc(0, 0, u * 0.5, 0, Math.PI * 2);
  ctx.fillStyle = accent;
  ctx.fill();
  stroke(ctx, u * 0.04);
  ctx.beginPath();
  ctx.arc(0, 0, u * 0.36, 0, Math.PI * 2);
  ctx.fillStyle = shade(accent, -0.18);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(0, 0, u * 0.1, 0, Math.PI * 2);
  ctx.fillStyle = GOLD;
  ctx.fill();
  stroke(ctx, u * 0.03);
  sword(ctx, -0.72, u);
  sword(ctx, 0.72, u);
  ctx.restore();
}

function flag(ctx: Ctx, x: number, y: number, u: number, col: string, flip: number, wave: number) {
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(flip, 1);
  ctx.beginPath();
  ctx.rect(-u * 0.03, -u * 0.5, u * 0.06, u * 1.0);
  ctx.fillStyle = "#6a4a2a";
  ctx.fill();
  stroke(ctx, u * 0.025);
  ctx.beginPath();
  ctx.moveTo(u * 0.03, -u * 0.46);
  ctx.quadraticCurveTo(u * 0.22, -u * 0.5 + wave, u * 0.44, -u * 0.44);
  ctx.lineTo(u * 0.36, -u * 0.28);
  ctx.lineTo(u * 0.44, -u * 0.1);
  ctx.quadraticCurveTo(u * 0.22, -u * 0.16 + wave, u * 0.03, -u * 0.12);
  ctx.closePath();
  ctx.fillStyle = col;
  ctx.fill();
  stroke(ctx, u * 0.03);
  ctx.beginPath();
  ctx.arc(0, -u * 0.52, u * 0.04, 0, Math.PI * 2);
  ctx.fillStyle = GOLD;
  ctx.fill();
  ctx.restore();
}

/** Two banners leaning together, allies of different colours: Multiplayer. */
export function iconMultiplayer(ctx: Ctx, x: number, y: number, s: number, time: number) {
  const u = s;
  const w = Math.sin(time * 2.4) * u * 0.03;
  flag(ctx, x - u * 0.12, y + u * 0.08, u, "#5b8fe0", -1, w);
  flag(ctx, x + u * 0.12, y + u * 0.08, u, "#d8574a", 1, -w);
  // the handshake between them: a small gold ring
  ctx.beginPath();
  ctx.arc(x, y + u * 0.3, u * 0.1, 0, Math.PI * 2);
  ctx.strokeStyle = GOLD;
  ctx.lineWidth = u * 0.05;
  ctx.stroke();
}

/** A hex tile with three pips: Warband Tactics. */
export function iconWarband(ctx: Ctx, x: number, y: number, s: number) {
  const r = s * 0.42;
  ctx.save();
  ctx.translate(x, y);
  ctx.beginPath();
  for (let i = 0; i < 6; i++) {
    const a = Math.PI / 6 + (i * Math.PI) / 3;
    ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  ctx.closePath();
  ctx.fillStyle = "#8a5a9e";
  ctx.fill();
  stroke(ctx, s * 0.035);
  ctx.beginPath();
  for (let i = 0; i < 6; i++) {
    const a = Math.PI / 6 + (i * Math.PI) / 3;
    ctx.lineTo(Math.cos(a) * r * 0.72, Math.sin(a) * r * 0.72);
  }
  ctx.closePath();
  ctx.fillStyle = "#6a3e7e";
  ctx.fill();
  for (const [px, py] of [[-0.16, -0.14], [0.16, -0.14], [0, 0.16]]) {
    ctx.beginPath();
    ctx.arc(px * s, py * s, s * 0.07, 0, Math.PI * 2);
    ctx.fillStyle = GOLD;
    ctx.fill();
    stroke(ctx, s * 0.02);
  }
  ctx.restore();
}

/** A banded chest: the Armory. */
export function iconArmory(ctx: Ctx, x: number, y: number, s: number) {
  const u = s / 100;
  ctx.save();
  ctx.translate(x, y + 6 * u);
  ctx.beginPath();
  ctx.roundRect(-40 * u, -14 * u, 80 * u, 40 * u, 5 * u);
  ctx.fillStyle = "#8a5a2e";
  ctx.fill();
  stroke(ctx, 3 * u);
  ctx.beginPath();
  ctx.roundRect(-40 * u, -36 * u, 80 * u, 24 * u, [14 * u, 14 * u, 0, 0]);
  ctx.fillStyle = "#a06a36";
  ctx.fill();
  stroke(ctx, 3 * u);
  ctx.fillStyle = GOLD;
  ctx.fillRect(-12 * u, -36 * u, 7 * u, 62 * u);
  ctx.fillRect(5 * u, -36 * u, 7 * u, 62 * u);
  ctx.beginPath();
  ctx.roundRect(-7 * u, -6 * u, 14 * u, 16 * u, 3 * u);
  ctx.fill();
  stroke(ctx, 2 * u);
  // a glint of what's inside
  ctx.fillStyle = withAlpha("#ffe9b0", 0.8);
  for (const [gx, gy] of [[-26, -44], [22, -48], [0, -52]]) {
    ctx.beginPath();
    ctx.moveTo(gx * u, (gy - 5) * u);
    ctx.lineTo((gx + 2) * u, gy * u);
    ctx.lineTo(gx * u, (gy + 5) * u);
    ctx.lineTo((gx - 2) * u, gy * u);
    ctx.closePath();
    ctx.fill();
  }
  ctx.restore();
}

/** A folded map with a route marked on it: the Map Editor. */
export function iconMap(ctx: Ctx, x: number, y: number, s: number) {
  const u = s / 100;
  ctx.save();
  ctx.translate(x, y);
  const xs = [-42, -14, 14, 42];
  for (let i = 0; i < 3; i++) {
    ctx.beginPath();
    const up = i % 2 === 0;
    ctx.moveTo(xs[i] * u, (up ? -30 : -24) * u);
    ctx.lineTo(xs[i + 1] * u, (up ? -24 : -30) * u);
    ctx.lineTo(xs[i + 1] * u, (up ? 30 : 36) * u);
    ctx.lineTo(xs[i] * u, (up ? 36 : 30) * u);
    ctx.closePath();
    ctx.fillStyle = i === 1 ? "#d8c49a" : "#e8d8b0";
    ctx.fill();
    stroke(ctx, 2.5 * u);
  }
  // land, water and a route
  ctx.fillStyle = "#7aa860";
  ctx.beginPath(); ctx.ellipse(-26 * u, -4 * u, 10 * u, 14 * u, 0.3, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#6a9ac8";
  ctx.beginPath(); ctx.ellipse(24 * u, 12 * u, 11 * u, 8 * u, -0.2, 0, Math.PI * 2); ctx.fill();
  ctx.setLineDash([4 * u, 4 * u]);
  ctx.strokeStyle = "#b8483e";
  ctx.lineWidth = 3 * u;
  ctx.beginPath();
  ctx.moveTo(-28 * u, 20 * u);
  ctx.quadraticCurveTo(0, -20 * u, 26 * u, -14 * u);
  ctx.stroke();
  ctx.setLineDash([]);
  ctx.fillStyle = "#b8483e";
  ctx.font = `bold ${16 * u}px Georgia, serif`;
  ctx.textAlign = "center";
  ctx.fillText("✕", 27 * u, -8 * u);
  ctx.restore();
}

/** An open book: the Codex. */
export function iconCodex(ctx: Ctx, x: number, y: number, s: number) {
  const u = s / 100;
  ctx.save();
  ctx.translate(x, y);
  for (const side of [-1, 1]) {
    ctx.beginPath();
    ctx.moveTo(0, -24 * u);
    ctx.quadraticCurveTo(side * 22 * u, -34 * u, side * 44 * u, -28 * u);
    ctx.lineTo(side * 44 * u, 26 * u);
    ctx.quadraticCurveTo(side * 22 * u, 20 * u, 0, 30 * u);
    ctx.closePath();
    ctx.fillStyle = "#efe2c4";
    ctx.fill();
    stroke(ctx, 2.5 * u);
    ctx.strokeStyle = withAlpha(INK, 0.35);
    ctx.lineWidth = 2 * u;
    for (let i = 0; i < 4; i++) {
      ctx.beginPath();
      ctx.moveTo(side * 8 * u, (-14 + i * 10) * u);
      ctx.lineTo(side * 36 * u, (-16 + i * 10) * u);
      ctx.stroke();
    }
  }
  ctx.fillStyle = "#b8483e";
  ctx.fillRect(-3 * u, -26 * u, 6 * u, 62 * u);
  ctx.restore();
}

/** A gear: Settings. */
export function iconSettings(ctx: Ctx, x: number, y: number, s: number) {
  const u = s / 100;
  ctx.save();
  ctx.translate(x, y);
  ctx.beginPath();
  const teeth = 8;
  for (let i = 0; i < teeth * 2; i++) {
    const a = (i / (teeth * 2)) * Math.PI * 2;
    const r = (i % 2 === 0 ? 38 : 30) * u;
    ctx.lineTo(Math.cos(a - 0.12) * r, Math.sin(a - 0.12) * r);
    ctx.lineTo(Math.cos(a + 0.12) * r, Math.sin(a + 0.12) * r);
  }
  ctx.closePath();
  ctx.fillStyle = "#9aa4b0";
  ctx.fill();
  stroke(ctx, 2.5 * u);
  ctx.beginPath();
  ctx.arc(0, 0, 12 * u, 0, Math.PI * 2);
  ctx.fillStyle = "#3a3226";
  ctx.fill();
  stroke(ctx, 2 * u);
  ctx.restore();
}

/** A curved arrow over an hourglass: Resume. */
export function iconResume(ctx: Ctx, x: number, y: number, s: number) {
  const u = s / 100;
  ctx.save();
  ctx.translate(x, y);
  ctx.beginPath();
  ctx.moveTo(-18 * u, -30 * u); ctx.lineTo(18 * u, -30 * u); ctx.lineTo(0, 0);
  ctx.lineTo(18 * u, 30 * u); ctx.lineTo(-18 * u, 30 * u); ctx.lineTo(0, 0);
  ctx.closePath();
  ctx.fillStyle = withAlpha("#cfe6f0", 0.85);
  ctx.fill();
  stroke(ctx, 3 * u);
  ctx.fillStyle = GOLD;
  ctx.beginPath(); ctx.moveTo(-10 * u, 26 * u); ctx.lineTo(10 * u, 26 * u); ctx.lineTo(0, 12 * u); ctx.closePath(); ctx.fill();
  ctx.fillStyle = "#6a4a2a";
  ctx.fillRect(-24 * u, -36 * u, 48 * u, 7 * u);
  ctx.fillRect(-24 * u, 29 * u, 48 * u, 7 * u);
  ctx.restore();
}

/** Three standards in different colours, crossed: the Factions book. */
export function iconFactions(ctx: Ctx, x: number, y: number, s: number) {
  const u = s / 100;
  ctx.save();
  ctx.translate(x, y + 6 * u);
  const cols = ["#5b8fe0", "#d8a83a", "#d8574a"];
  [-0.35, 0, 0.35].forEach((a, i) => {
    ctx.save();
    ctx.rotate(a);
    ctx.beginPath();
    ctx.rect(-2.5 * u, -46 * u, 5 * u, 80 * u);
    ctx.fillStyle = "#6a4a2a";
    ctx.fill();
    stroke(ctx, 2 * u);
    ctx.beginPath();
    ctx.moveTo(2.5 * u, -44 * u);
    ctx.lineTo(26 * u, -40 * u);
    ctx.lineTo(18 * u, -30 * u);
    ctx.lineTo(26 * u, -20 * u);
    ctx.lineTo(2.5 * u, -18 * u);
    ctx.closePath();
    ctx.fillStyle = cols[i];
    ctx.fill();
    stroke(ctx, 2 * u);
    ctx.restore();
  });
  ctx.beginPath();
  ctx.arc(0, 30 * u, 7 * u, 0, Math.PI * 2);
  ctx.fillStyle = GOLD;
  ctx.fill();
  stroke(ctx, 2 * u);
  ctx.restore();
}

/** A laurel-edged scroll with a rising bar chart on it: the Career screen. */
export function iconCareer(ctx: Ctx, x: number, y: number, s: number) {
  const u = s / 100;
  ctx.save();
  ctx.translate(x, y);
  ctx.beginPath();
  ctx.roundRect(-34 * u, -34 * u, 68 * u, 64 * u, 6 * u);
  ctx.fillStyle = "#efe2c4";
  ctx.fill();
  stroke(ctx, 2.5 * u);
  const bars = [14, 24, 20, 36];
  const cols = ["#b8a888", "#b8a888", "#b8a888", GOLD];
  bars.forEach((h, i) => {
    ctx.beginPath();
    ctx.rect((-24 + i * 13) * u, (22 - h) * u, 9 * u, h * u);
    ctx.fillStyle = cols[i];
    ctx.fill();
    stroke(ctx, 1.6 * u);
  });
  // A little laurel at the corner: two leaves.
  ctx.fillStyle = "#7aa860";
  for (const a of [-0.6, 0.6]) {
    ctx.save();
    ctx.translate(26 * u, -26 * u);
    ctx.rotate(a);
    ctx.beginPath();
    ctx.ellipse(0, -8 * u, 4 * u, 9 * u, 0, 0, Math.PI * 2);
    ctx.fill();
    stroke(ctx, 1.5 * u);
    ctx.restore();
  }
  ctx.restore();
}

/** Nemesis: a horned war-helm with a red eye-slit — the rival who remembers. */
export function iconNemesis(ctx: Ctx, x: number, y: number, s: number) {
  const r = s * 0.34;
  ctx.save();
  ctx.translate(x, y + s * 0.04);
  // Horns.
  for (const d of [-1, 1]) {
    ctx.beginPath();
    ctx.moveTo(d * r * 0.7, -r * 0.35);
    ctx.quadraticCurveTo(d * r * 1.55, -r * 0.55, d * r * 1.35, -r * 1.35);
    ctx.quadraticCurveTo(d * r * 1.15, -r * 0.8, d * r * 0.55, -r * 0.7);
    ctx.closePath();
    ctx.fillStyle = "#e7dcc0";
    ctx.fill();
    stroke(ctx, s * 0.03);
  }
  // The helm.
  ctx.beginPath();
  ctx.moveTo(-r, r * 0.9);
  ctx.lineTo(-r, -r * 0.2);
  ctx.quadraticCurveTo(-r, -r * 1.05, 0, -r * 1.05);
  ctx.quadraticCurveTo(r, -r * 1.05, r, -r * 0.2);
  ctx.lineTo(r, r * 0.9);
  ctx.lineTo(r * 0.25, r * 1.1);
  ctx.lineTo(-r * 0.25, r * 1.1);
  ctx.closePath();
  ctx.fillStyle = "#6e6a70";
  ctx.fill();
  stroke(ctx, s * 0.035);
  // Eye slit, glowing.
  ctx.fillStyle = "#1a0d0b";
  ctx.fillRect(-r * 0.72, -r * 0.12, r * 1.44, r * 0.26);
  ctx.fillStyle = "#ff5a3c";
  ctx.shadowColor = "#ff5a3c";
  ctx.shadowBlur = s * 0.12;
  ctx.fillRect(-r * 0.5, -r * 0.05, r * 0.3, r * 0.12);
  ctx.fillRect(r * 0.2, -r * 0.05, r * 0.3, r * 0.12);
  ctx.shadowBlur = 0;
  // Nose guard.
  ctx.fillStyle = "#8a868c";
  ctx.fillRect(-r * 0.08, -r * 0.12, r * 0.16, r * 0.8);
  ctx.restore();
}
