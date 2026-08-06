// Procedural entity art. Everything is drawn in world space (the renderer
// applies the camera transform). Style: soft top-down-three-quarter "toy
// soldier" look — chunky silhouettes, team-colored cloth, warm shadows.

import { BuildState, Entity, Kind, Team } from "../sim/types";
import { BUILDINGS } from "../content/buildings";
import { UNITS } from "../content/units";
import { PAL, shade, teamColor, withAlpha } from "./palette";
import { rarityByIndex } from "../meta/rarity";
import { TILE } from "../content/balance";
import { isNight } from "../content/daynight";
import { ABILITIES } from "../content/abilities";
import { spriteFor } from "./sprites";

type Ctx = CanvasRenderingContext2D;

/** Draw a baked sprite centered at the local origin, sized to the radius. */
function blitSprite(ctx: Ctx, sp: { img: CanvasImageSource; scale: number; anchorY: number }, radius: number) {
  const w = radius * 2.6 * sp.scale;
  const iw = (sp.img as HTMLImageElement).width || w;
  const ih = (sp.img as HTMLImageElement).height || w;
  const h = w * (ih / iw);
  // anchorY 1 = feet on the origin, 0.5 = centered.
  ctx.drawImage(sp.img, -w / 2, -h * sp.anchorY, w, h);
}

// The renderer pushes the live day-cycle phase here each frame so world-space
// art (watchfire glow, etc.) can react to night without threading it through
// every draw signature. Derived from deterministic sim time upstream.
let gDayPhase = 0;
export function setDrawDayPhase(p: number) {
  gDayPhase = p;
}

// Optional viewer-relative colour resolver (diplomacy colouring in team games).
// When set, entities are coloured by their relation to the viewer instead of by
// raw team. Null = use the global per-team colours (1v1 / FFA).
type TeamCol = ReturnType<typeof teamColor>;
let gColorResolver: ((team: number) => TeamCol) | null = null;
export function setTeamColorResolver(fn: ((team: number) => TeamCol) | null) {
  gColorResolver = fn;
}
function tcol(team: number): TeamCol {
  return gColorResolver ? gColorResolver(team) : teamColor(team);
}

// Buildings whose windows light up at night.
const WINDOW_GLOW = new Set([
  "town_center", "house", "mill", "barracks", "archery_range", "stable",
  "blacksmith", "market", "castle", "watch_tower",
]);

function shadow(ctx: Ctx, x: number, y: number, rx: number, ry: number, alpha = 0.25) {
  ctx.fillStyle = `rgba(20, 24, 12, ${alpha})`;
  ctx.beginPath();
  ctx.ellipse(x, y, rx, ry, 0, 0, Math.PI * 2);
  ctx.fill();
}

// A bold team-coloured ground disc + ring under every unit — the clearest "who
// owns this" cue, readable at a glance even in a big mixed-team brawl.
function teamRing(ctx: Ctx, x: number, y: number, r: number, tc: TeamCol) {
  const rx = r * 1.15;
  const ry = r * 0.52;
  // Soft filled disc.
  ctx.fillStyle = withAlpha(tc.main, 0.42);
  ctx.beginPath();
  ctx.ellipse(x, y, rx, ry, 0, 0, Math.PI * 2);
  ctx.fill();
  // Dark contrast rim so the colour reads on any terrain, then a bright ring.
  ctx.lineWidth = 3;
  ctx.strokeStyle = "rgba(8, 8, 6, 0.55)";
  ctx.beginPath();
  ctx.ellipse(x, y, rx + 0.5, ry + 0.5, 0, 0, Math.PI * 2);
  ctx.stroke();
  ctx.lineWidth = 2;
  ctx.strokeStyle = tc.light;
  ctx.beginPath();
  ctx.ellipse(x, y, rx, ry, 0, 0, Math.PI * 2);
  ctx.stroke();
}

// ---------------------------------------------------------------- resources --

export function drawResource(ctx: Ctx, e: Entity, time: number) {
  const frac = Math.max(0.25, Math.min(1, e.amount / 125));
  const seed = (e.id * 2654435761) >>> 0;
  const v = (n: number) => ((seed >> (n * 3)) % 100) / 100; // cheap per-entity variation

  if (e.type === "tree") {
    const sway = Math.sin(time * 0.8 + e.id) * 0.8;
    shadow(ctx, e.x + 3, e.y + 5, 11 * frac, 5 * frac);
    // trunk with a little rounded shading
    ctx.fillStyle = grad(ctx, e.x - 2, e.y, e.x + 2, e.y, shade(PAL.trunk, 0.12), shade(PAL.trunk, -0.18));
    ctx.fillRect(e.x - 2, e.y - 4, 4, 9);
    // dark canopy underlayer for a crisp silhouette
    ctx.fillStyle = shade(PAL.foliage1, -0.22);
    ctx.beginPath();
    ctx.arc(e.x + sway * 0.4, e.y - 6, 10 * frac + v(2) * 2, 0, Math.PI * 2);
    ctx.fill();
    // layered canopy
    const layers: [number, number, string][] = [
      [9 * frac, 4, PAL.foliage1],
      [7 * frac, 0, PAL.foliage2],
      [5 * frac, -3, PAL.foliage3],
    ];
    for (const [r, oy, col] of layers) {
      ctx.fillStyle = shade(col, v(1) * 0.12 - 0.06);
      ctx.beginPath();
      ctx.arc(e.x + sway * 0.4, e.y - 8 + oy, r + v(2) * 2, 0, Math.PI * 2);
      ctx.fill();
    }
    // sun-side highlight
    ctx.fillStyle = withAlpha("#dff0c0", 0.3);
    ctx.beginPath();
    ctx.arc(e.x - 3 + sway * 0.4, e.y - 12, 3.5 * frac, 0, Math.PI * 2);
    ctx.fill();
  } else if (e.type === "gold_mine") {
    shadow(ctx, e.x + 2, e.y + 6, 13 * frac + 2, 5);
    // rock pile
    ctx.fillStyle = grad(ctx, e.x, e.y - 9 * frac, e.x, e.y + 7, shade(PAL.goldRock, 0.12), shade(PAL.goldRock, -0.1));
    ctx.beginPath();
    ctx.moveTo(e.x - 13 * frac, e.y + 7);
    ctx.lineTo(e.x - 6, e.y - 9 * frac);
    ctx.lineTo(e.x + 3, e.y - 5 * frac - 4);
    ctx.lineTo(e.x + 13 * frac, e.y + 7);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = shade(PAL.goldRock, -0.18);
    ctx.beginPath();
    ctx.moveTo(e.x, e.y + 7);
    ctx.lineTo(e.x + 3, e.y - 5 * frac - 4);
    ctx.lineTo(e.x + 13 * frac, e.y + 7);
    ctx.closePath();
    ctx.fill();
    // gold veins
    ctx.fillStyle = PAL.goldVein;
    for (let i = 0; i < 4; i++) {
      const gx = e.x - 8 + v(i) * 16;
      const gy = e.y + 4 - v(i + 3) * 9 * frac;
      ctx.beginPath();
      ctx.arc(gx, gy, 1.6 + v(i + 1), 0, Math.PI * 2);
      ctx.fill();
    }
  } else if (e.type === "berries") {
    shadow(ctx, e.x + 1, e.y + 4, 9 * frac, 4);
    ctx.fillStyle = shade(PAL.berryBush, v(1) * 0.1 - 0.05);
    ctx.beginPath();
    ctx.arc(e.x, e.y - 2, 8 * frac, 0, Math.PI * 2);
    ctx.arc(e.x - 5 * frac, e.y + 1, 5 * frac, 0, Math.PI * 2);
    ctx.arc(e.x + 5 * frac, e.y + 1, 5 * frac, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = PAL.berry;
    for (let i = 0; i < 5; i++) {
      ctx.beginPath();
      ctx.arc(e.x - 5 + v(i) * 10, e.y - 4 + v(i + 2) * 7, 1.4, 0, Math.PI * 2);
      ctx.fill();
    }
  }
}

// ---------------------------------------------------------------- buildings --

export function drawBuilding(ctx: Ctx, e: Entity, time: number, selectedTeamView: Team) {
  const def = BUILDINGS[e.type];
  const half = e.radius;
  const tc = tcol(e.team);

  // Foundation / construction states.
  if (e.buildState !== BuildState.Done) {
    // dirt pad
    ctx.fillStyle = PAL.dirt;
    ctx.fillRect(e.x - half, e.y - half, half * 2, half * 2);
    ctx.strokeStyle = PAL.woodDark;
    ctx.lineWidth = 2;
    ctx.strokeRect(e.x - half + 2, e.y - half + 2, half * 2 - 4, half * 2 - 4);
    // corner posts
    ctx.fillStyle = PAL.wood;
    for (const [px, py] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
      ctx.fillRect(e.x + px * (half - 5) - 2, e.y + py * (half - 5) - 5, 4, 8);
    }
    if (e.buildState === BuildState.UnderConstruction) {
      // scaffold rises with progress
      const h = e.buildProgress * half * 1.2;
      ctx.fillStyle = withAlpha(PAL.woodLight, 0.8);
      ctx.fillRect(e.x - half * 0.7, e.y - h * 0.6, half * 1.4, h * 0.6);
      ctx.strokeStyle = PAL.woodDark;
      for (let i = 0; i < 3; i++) {
        const sx = e.x - half * 0.7 + (half * 1.4 * i) / 2;
        ctx.beginPath();
        ctx.moveTo(sx, e.y);
        ctx.lineTo(sx, e.y - h * 0.6);
        ctx.stroke();
      }
      // progress arc
      ctx.strokeStyle = PAL.uiGood;
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(e.x, e.y, half + 6, -Math.PI / 2, -Math.PI / 2 + e.buildProgress * Math.PI * 2);
      ctx.stroke();
    }
    return;
  }

  shadow(ctx, e.x + half * 0.12, e.y + half * 0.55, half * 1.05, half * 0.45, 0.3);

  const bsprite = spriteFor(e.type);
  if (bsprite) {
    ctx.save();
    ctx.translate(e.x, e.y);
    blitSprite(ctx, bsprite, half);
    ctx.restore();
  } else
  switch (e.type) {
    case "town_center": drawTownCenter(ctx, e, half, tc, time); break;
    case "house": drawHouse(ctx, e, half, tc); break;
    case "mill": drawMill(ctx, e, half, tc, time); break;
    case "lumber_camp": drawLumberCamp(ctx, e, half); break;
    case "mining_camp": drawMiningCamp(ctx, e, half); break;
    case "farm": drawFarm(ctx, e, half); break;
    case "bridge": drawBridge(ctx, e, half); break;
    case "barracks": drawBarracks(ctx, e, half, tc); break;
    case "archery_range": drawArcheryRange(ctx, e, half, tc); break;
    case "stable": drawStable(ctx, e, half, tc); break;
    case "blacksmith": drawBlacksmith(ctx, e, half, tc, time); break;
    case "market": drawMarket(ctx, e, half, tc); break;
    case "palisade": drawPalisade(ctx, e, half); break;
    case "stone_wall": drawStoneWall(ctx, e, half); break;
    case "gate": drawGate(ctx, e, half, tc); break;
    case "watchfire": drawWatchfire(ctx, e, half, time); break;
    case "watch_tower": drawTower(ctx, e, half, tc); break;
    case "castle": drawCastle(ctx, e, half, tc, time); break;
    case "siege_workshop": drawSiegeWorkshop(ctx, e, half); break;
    default: {
      ctx.fillStyle = PAL.stone;
      ctx.fillRect(e.x - half, e.y - half, half * 2, half * 2);
    }
  }

  // After dark, lived-in buildings show warm lit windows and a soft hearth
  // glow — sells the night and makes towns feel alive.
  if (isNight(gDayPhase) && WINDOW_GLOW.has(e.type)) {
    const flick = 0.8 + Math.sin(time * 6 + e.id * 1.7) * 0.12;
    const g = ctx.createRadialGradient(e.x, e.y, 1, e.x, e.y, half * 1.7);
    g.addColorStop(0, withAlpha("#ffbe5a", 0.1 * flick));
    g.addColorStop(1, withAlpha("#ffbe5a", 0));
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(e.x, e.y, half * 1.7, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = withAlpha("#ffd98a", 0.9 * flick);
    const wn = e.radius > 40 ? 3 : 2;
    for (let i = 0; i < wn; i++) {
      const wx = e.x + ((i + 0.5) / wn - 0.5) * half * 1.1;
      ctx.fillRect(wx - 1.5, e.y + half * 0.18, 3, 4);
    }
  }

  // Damage smoke handled by game-side particles; here add scorched look.
  const dmg = 1 - e.hp / e.maxHp;
  if (dmg > 0.4) {
    ctx.fillStyle = withAlpha("#1a140c", Math.min(0.45, (dmg - 0.4) * 0.9));
    ctx.fillRect(e.x - half, e.y - half, half * 2, half * 2);
  }
  if (e.hitFlash > 0) {
    ctx.fillStyle = withAlpha("#ffffff", e.hitFlash * 0.35);
    ctx.fillRect(e.x - half, e.y - half, half * 2, half * 2);
  }
}

function roofGable(ctx: Ctx, x: number, y: number, w: number, h: number, color: string) {
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(x - w / 2 - 2, y + h / 2);
  ctx.lineTo(x, y - h / 2);
  ctx.lineTo(x + w / 2 + 2, y + h / 2);
  ctx.closePath();
  ctx.fill();
  ctx.fillStyle = withAlpha("#ffffff", 0.12);
  ctx.beginPath();
  ctx.moveTo(x - w / 2 - 2, y + h / 2);
  ctx.lineTo(x, y - h / 2);
  ctx.lineTo(x + w * 0.1, y - h / 2 + 2);
  ctx.lineTo(x - w * 0.35, y + h / 2);
  ctx.closePath();
  ctx.fill();
}

function banner(ctx: Ctx, x: number, y: number, tc: { main: string; dark: string }, time: number, id: number) {
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(x, y);
  ctx.lineTo(x, y - 16);
  ctx.stroke();
  const wave = Math.sin(time * 3 + id) * 2;
  ctx.fillStyle = tc.main;
  ctx.beginPath();
  ctx.moveTo(x, y - 16);
  ctx.quadraticCurveTo(x + 6, y - 15 + wave * 0.4, x + 11, y - 13 + wave);
  ctx.lineTo(x + 9, y - 9 + wave * 0.6);
  ctx.quadraticCurveTo(x + 5, y - 10, x, y - 10);
  ctx.closePath();
  ctx.fill();
}

// ---- shared shaded-building parts (world space) ----------------------------
// A stone wall block: vertical gradient, mortar courses, soft outline.
function stoneBlock(ctx: Ctx, x: number, y: number, w: number, h: number, tone = PAL.stone) {
  ctx.fillStyle = grad(ctx, x, y, x, y + h, shade(tone, 0.18), shade(tone, -0.18));
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, 3);
  ctx.fill();
  softOutline(ctx, 1.4);
  ctx.strokeStyle = "rgba(70,64,56,0.32)";
  ctx.lineWidth = 1;
  for (let yy = y + h * 0.34; yy < y + h - 1; yy += h * 0.32) {
    ctx.beginPath();
    ctx.moveTo(x + 2, yy);
    ctx.lineTo(x + w - 2, yy);
    ctx.stroke();
  }
}

// A timber-framed plaster wall (warm), with corner posts.
function woodBlock(ctx: Ctx, x: number, y: number, w: number, h: number) {
  ctx.fillStyle = grad(ctx, x, y, x, y + h, "#d8c8a8", "#b49a72");
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, 3);
  ctx.fill();
  softOutline(ctx, 1.4);
  ctx.fillStyle = PAL.woodDark; // corner posts + lintel
  ctx.fillRect(x, y, 4, h);
  ctx.fillRect(x + w - 4, y, 4, h);
  ctx.fillRect(x, y, w, 3.5);
}

function cornerPosts(ctx: Ctx, x: number, y: number, w: number, h: number) {
  ctx.fillStyle = PAL.woodDark;
  ctx.fillRect(x, y, 4, h);
  ctx.fillRect(x + w - 4, y, 4, h);
}

// A hipped tiled roof in FULL team colour, shaded with tile rows + ridge.
function hipRoof(ctx: Ctx, cx: number, eaveY: number, halfW: number, peakY: number, tc: any) {
  const slate = tc.main;
  ctx.fillStyle = grad(ctx, cx, peakY, cx, eaveY, shade(slate, 0.24), shade(slate, -0.2));
  ctx.beginPath();
  ctx.moveTo(cx - halfW, eaveY);
  ctx.lineTo(cx - halfW * 0.42, peakY);
  ctx.lineTo(cx + halfW * 0.42, peakY);
  ctx.lineTo(cx + halfW, eaveY);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1.6);
  ctx.strokeStyle = "rgba(255,255,255,0.12)"; // tile courses
  ctx.lineWidth = 1;
  for (let r = 0.25; r < 1; r += 0.25) {
    const y = peakY + (eaveY - peakY) * r;
    const hw = halfW * 0.42 + (halfW - halfW * 0.42) * r;
    ctx.beginPath();
    ctx.moveTo(cx - hw, y);
    ctx.lineTo(cx + hw, y);
    ctx.stroke();
  }
  ctx.strokeStyle = shade(slate, 0.42); // ridge highlight
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(cx - halfW * 0.42, peakY);
  ctx.lineTo(cx + halfW * 0.42, peakY);
  ctx.stroke();
}

// An arched timber door.
function archDoor(ctx: Ctx, cx: number, baseY: number, w: number, h: number) {
  ctx.fillStyle = grad(ctx, cx, baseY - h, cx, baseY, PAL.wood, PAL.woodDark);
  ctx.beginPath();
  ctx.moveTo(cx - w / 2, baseY);
  ctx.lineTo(cx - w / 2, baseY - h * 0.55);
  ctx.quadraticCurveTo(cx, baseY - h, cx + w / 2, baseY - h * 0.55);
  ctx.lineTo(cx + w / 2, baseY);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1.2);
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(cx, baseY);
  ctx.lineTo(cx, baseY - h * 0.65);
  ctx.stroke();
}

// A small window with a warm-lit pane.
function litWindow(ctx: Ctx, cx: number, cy: number, s: number) {
  ctx.fillStyle = "rgba(20,16,10,0.65)";
  ctx.fillRect(cx - s, cy - s, s * 2, s * 2);
  ctx.fillStyle = "#ffd98a";
  ctx.fillRect(cx - s + 1, cy - s + 1, s * 2 - 2, s * 2 - 2);
  ctx.strokeStyle = "rgba(20,16,10,0.5)";
  ctx.lineWidth = 0.8;
  ctx.beginPath();
  ctx.moveTo(cx, cy - s + 1);
  ctx.lineTo(cx, cy + s - 1);
  ctx.stroke();
}

// A conical team-colour roof for round towers.
function coneRoof(ctx: Ctx, cx: number, baseY: number, halfW: number, peakY: number, tc: any) {
  const c = tc.main;
  ctx.fillStyle = grad(ctx, cx, peakY, cx, baseY, shade(c, 0.24), shade(c, -0.2));
  ctx.beginPath();
  ctx.moveTo(cx - halfW, baseY);
  ctx.lineTo(cx, peakY);
  ctx.lineTo(cx + halfW, baseY);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1.4);
  ctx.strokeStyle = shade(c, 0.42);
  ctx.lineWidth = 1.4;
  ctx.beginPath();
  ctx.moveTo(cx, peakY);
  ctx.lineTo(cx - halfW * 0.45, baseY);
  ctx.stroke();
}

function drawTownCenter(ctx: Ctx, e: Entity, half: number, tc: any, time: number) {
  // two flanking stone towers with team-colour caps
  for (const sx of [-1, 1]) {
    const tx = e.x + sx * half * 0.78;
    stoneBlock(ctx, tx - half * 0.24, e.y - half * 0.3, half * 0.48, half * 1.15);
    hipRoof(ctx, tx, e.y - half * 0.3, half * 0.36, e.y - half * 0.78, tc);
  }
  // main hall
  stoneBlock(ctx, e.x - half * 0.62, e.y - half * 0.1, half * 1.24, half * 0.95);
  cornerPosts(ctx, e.x - half * 0.62, e.y - half * 0.1, half * 1.24, half * 0.95);
  litWindow(ctx, e.x - half * 0.32, e.y + half * 0.28, half * 0.1);
  litWindow(ctx, e.x + half * 0.32, e.y + half * 0.28, half * 0.1);
  archDoor(ctx, e.x, e.y + half * 0.85, half * 0.38, half * 0.5);
  hipRoof(ctx, e.x, e.y - half * 0.1, half * 0.82, e.y - half * 0.78, tc);
  banner(ctx, e.x + half * 0.7, e.y - half * 0.5, tc, time, e.id);
}

function drawHouse(ctx: Ctx, e: Entity, half: number, tc: any) {
  woodBlock(ctx, e.x - half * 0.72, e.y - half * 0.1, half * 1.44, half * 0.9);
  litWindow(ctx, e.x - half * 0.28, e.y + half * 0.28, half * 0.1);
  archDoor(ctx, e.x + half * 0.28, e.y + half * 0.8, half * 0.3, half * 0.42);
  hipRoof(ctx, e.x, e.y - half * 0.1, half * 0.78, e.y - half * 0.72, tc);
  // chimney + curling smoke
  ctx.fillStyle = PAL.stoneDark;
  ctx.fillRect(e.x + half * 0.42, e.y - half * 0.7, half * 0.16, half * 0.4);
  ctx.fillStyle = "rgba(220,220,220,0.45)";
  for (let i = 0; i < 3; i++) {
    ctx.beginPath();
    ctx.arc(e.x + half * 0.5 + i * 2, e.y - half * 0.85 - i * half * 0.22, half * 0.1 + i * 1.5, 0, Math.PI * 2);
    ctx.fill();
  }
}

function drawMill(ctx: Ctx, e: Entity, half: number, tc: any, time: number) {
  woodBlock(ctx, e.x - half * 0.5, e.y - half * 0.2, half * 1.0, half * 1.0);
  litWindow(ctx, e.x, e.y + half * 0.35, half * 0.1);
  hipRoof(ctx, e.x, e.y - half * 0.2, half * 0.66, e.y - half * 0.62, tc);
  // rotating sail blades
  const a = time * 0.9;
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = 3;
  for (let i = 0; i < 4; i++) {
    const ang = a + (i * Math.PI) / 2;
    ctx.beginPath();
    ctx.moveTo(e.x, e.y - half * 0.55);
    ctx.lineTo(e.x + Math.cos(ang) * half * 0.85, e.y - half * 0.55 + Math.sin(ang) * half * 0.85);
    ctx.stroke();
  }
  ctx.fillStyle = PAL.woodDark;
  ctx.beginPath();
  ctx.arc(e.x, e.y - half * 0.55, 3, 0, Math.PI * 2);
  ctx.fill();
}

function drawLumberCamp(ctx: Ctx, e: Entity, half: number) {
  // timber lean-to (a utility shed — no team roof)
  woodBlock(ctx, e.x - half * 0.9, e.y - half * 0.25, half * 0.95, half * 1.0);
  ctx.fillStyle = grad(ctx, e.x, e.y - half * 0.55, e.x, e.y - half * 0.15, shade(PAL.thatch, 0.1), PAL.thatchDark);
  ctx.beginPath();
  ctx.moveTo(e.x - half * 0.95, e.y - half * 0.15);
  ctx.lineTo(e.x - half * 0.6, e.y - half * 0.55);
  ctx.lineTo(e.x + half * 0.1, e.y - half * 0.55);
  ctx.lineTo(e.x + half * 0.05, e.y - half * 0.15);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1.4);
  // log pile
  ctx.fillStyle = PAL.trunk;
  for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3 - i; j++) {
      ctx.beginPath();
      ctx.arc(e.x + half * 0.4 + j * 7 - 7 + i * 3.5, e.y + half * 0.4 - i * 6, 3.6, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  ctx.fillStyle = PAL.woodLight;
  for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3 - i; j++) {
      ctx.beginPath();
      ctx.arc(e.x + half * 0.4 + j * 7 - 7 + i * 3.5, e.y + half * 0.4 - i * 6, 1.7, 0, Math.PI * 2);
      ctx.fill();
    }
  }
}

function drawMiningCamp(ctx: Ctx, e: Entity, half: number) {
  stoneBlock(ctx, e.x - half * 0.9, e.y - half * 0.2, half * 1.8, half * 1.0, PAL.stoneDark);
  // mine entrance arch
  ctx.fillStyle = "#241c12";
  ctx.beginPath();
  ctx.arc(e.x, e.y + half * 0.35, half * 0.4, Math.PI, 0);
  ctx.fill();
  ctx.strokeStyle = PAL.wood;
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.arc(e.x, e.y + half * 0.35, half * 0.42, Math.PI, 0);
  ctx.stroke();
  // cart of ore
  ctx.fillStyle = PAL.woodDark;
  ctx.fillRect(e.x + half * 0.3, e.y - half * 0.1, half * 0.5, half * 0.32);
  ctx.fillStyle = PAL.goldVein;
  ctx.beginPath();
  ctx.arc(e.x + half * 0.45, e.y - half * 0.1, 3, 0, Math.PI * 2);
  ctx.arc(e.x + half * 0.6, e.y - half * 0.13, 2.5, 0, Math.PI * 2);
  ctx.fill();
}

/**
 * Timber decking over the shallows. Read flat and low on purpose — a bridge is
 * something troops walk *over*, and anything with height to it would read as an
 * obstacle in the middle of the water, which is the opposite of what it is.
 */
function drawBridge(ctx: Ctx, e: Entity, half: number) {
  const x = e.x - half, y = e.y - half, s = half * 2;
  // The deck.
  ctx.fillStyle = PAL.woodDark;
  ctx.fillRect(x + 1, y + 1, s - 2, s - 2);
  // Planks across the span.
  ctx.strokeStyle = "#9c7b4a";
  ctx.lineWidth = 1.6;
  for (let i = 1; i < 7; i++) {
    const px = x + (s * i) / 7;
    ctx.beginPath();
    ctx.moveTo(px, y + 3);
    ctx.lineTo(px, y + s - 3);
    ctx.stroke();
  }
  // Handrails down both long edges, so the crossing direction is legible.
  ctx.strokeStyle = "#6b4f2c";
  ctx.lineWidth = 2.4;
  ctx.beginPath();
  ctx.moveTo(x + 1, y + 2.5); ctx.lineTo(x + s - 1, y + 2.5);
  ctx.moveTo(x + 1, y + s - 2.5); ctx.lineTo(x + s - 1, y + s - 2.5);
  ctx.stroke();
}

function drawFarm(ctx: Ctx, e: Entity, half: number) {
  ctx.fillStyle = PAL.dirtDark;
  ctx.fillRect(e.x - half + 2, e.y - half + 2, half * 2 - 4, half * 2 - 4);
  // crop rows
  ctx.strokeStyle = "#c8b446";
  ctx.lineWidth = 2.5;
  for (let i = 0; i < 5; i++) {
    const ry = e.y - half + 6 + ((half * 2 - 12) * i) / 4;
    ctx.beginPath();
    ctx.moveTo(e.x - half + 5, ry);
    ctx.lineTo(e.x + half - 5, ry);
    ctx.stroke();
  }
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 2;
  ctx.strokeRect(e.x - half + 2, e.y - half + 2, half * 2 - 4, half * 2 - 4);
}

function drawBarracks(ctx: Ctx, e: Entity, half: number, tc: any) {
  stoneBlock(ctx, e.x - half * 0.78, e.y - half * 0.15, half * 1.56, half * 1.0);
  cornerPosts(ctx, e.x - half * 0.78, e.y - half * 0.15, half * 1.56, half * 1.0);
  hipRoof(ctx, e.x, e.y - half * 0.15, half * 0.95, e.y - half * 0.82, tc);
  // crossed-sword shield emblem on the wall
  ctx.fillStyle = shade(tc.main, -0.1);
  ctx.beginPath();
  ctx.moveTo(e.x, e.y - half * 0.02);
  ctx.lineTo(e.x + half * 0.2, e.y + half * 0.14);
  ctx.lineTo(e.x + half * 0.15, e.y + half * 0.42);
  ctx.lineTo(e.x, e.y + half * 0.54);
  ctx.lineTo(e.x - half * 0.15, e.y + half * 0.42);
  ctx.lineTo(e.x - half * 0.2, e.y + half * 0.14);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1.2);
  ctx.strokeStyle = PAL.steel;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(e.x - half * 0.11, e.y + half * 0.12);
  ctx.lineTo(e.x + half * 0.11, e.y + half * 0.42);
  ctx.moveTo(e.x + half * 0.11, e.y + half * 0.12);
  ctx.lineTo(e.x - half * 0.11, e.y + half * 0.42);
  ctx.stroke();
}

function drawArcheryRange(ctx: Ctx, e: Entity, half: number, tc: any) {
  woodBlock(ctx, e.x - half * 0.9, e.y - half * 0.15, half * 1.1, half * 0.95);
  hipRoof(ctx, e.x - half * 0.35, e.y - half * 0.15, half * 0.66, e.y - half * 0.68, tc);
  // target butt
  const tx = e.x + half * 0.55;
  const ty = e.y + half * 0.3;
  for (const [r, col] of [[half * 0.34, "#e6ddc4"], [half * 0.24, "#cc4444"], [half * 0.13, "#e6ddc4"], [half * 0.05, "#cc4444"]] as [number, string][]) {
    ctx.fillStyle = col;
    ctx.beginPath();
    ctx.arc(tx, ty, r, 0, Math.PI * 2);
    ctx.fill();
  }
  // stuck arrow
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(tx + 2, ty - 2);
  ctx.lineTo(tx + 9, ty - 9);
  ctx.stroke();
}

function drawStable(ctx: Ctx, e: Entity, half: number, tc: any) {
  woodBlock(ctx, e.x - half * 0.85, e.y - half * 0.1, half * 1.7, half * 0.95);
  hipRoof(ctx, e.x, e.y - half * 0.1, half * 1.0, e.y - half * 0.7, tc);
  // stall openings
  ctx.fillStyle = PAL.woodDark;
  for (let i = -1; i <= 1; i++) {
    ctx.beginPath();
    ctx.arc(e.x + i * half * 0.5, e.y + half * 0.5, half * 0.18, Math.PI, 0);
    ctx.fill();
    ctx.fillRect(e.x + i * half * 0.5 - half * 0.18, e.y + half * 0.5, half * 0.36, half * 0.28);
  }
  // horseshoe emblem
  ctx.strokeStyle = tc.light;
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.arc(e.x, e.y + half * 0.12, half * 0.15, Math.PI * 0.15, Math.PI * 0.85, true);
  ctx.stroke();
}

function drawBlacksmith(ctx: Ctx, e: Entity, half: number, tc: any, time: number) {
  stoneBlock(ctx, e.x - half * 0.78, e.y - half * 0.1, half * 1.56, half * 0.95, PAL.stoneDark);
  hipRoof(ctx, e.x, e.y - half * 0.1, half * 0.95, e.y - half * 0.72, tc);
  // glowing forge window
  const glow = 0.6 + Math.sin(time * 5 + e.id) * 0.25;
  ctx.fillStyle = withAlpha(PAL.fire, glow);
  ctx.fillRect(e.x - half * 0.22, e.y + half * 0.16, half * 0.44, half * 0.34);
  ctx.fillStyle = withAlpha(PAL.fireBright, glow * 0.7);
  ctx.fillRect(e.x - half * 0.13, e.y + half * 0.22, half * 0.26, half * 0.2);
  // chimney
  ctx.fillStyle = PAL.stoneDark;
  ctx.fillRect(e.x + half * 0.5, e.y - half * 0.6, half * 0.2, half * 0.5);
  // anvil silhouette
  ctx.fillStyle = "#3c4148";
  ctx.fillRect(e.x - half * 0.66, e.y + half * 0.42, half * 0.3, half * 0.12);
  ctx.fillRect(e.x - half * 0.58, e.y + half * 0.32, half * 0.15, half * 0.12);
}

function drawMarket(ctx: Ctx, e: Entity, half: number, tc: any) {
  woodBlock(ctx, e.x - half * 0.9, e.y - half * 0.05, half * 1.8, half * 0.85);
  // striped awning (team colour)
  const stripes = 6;
  for (let i = 0; i < stripes; i++) {
    ctx.fillStyle = i % 2 === 0 ? tc.main : PAL.uiParchment;
    const w = (half * 2 - 4) / stripes;
    ctx.beginPath();
    ctx.moveTo(e.x - half + 2 + i * w, e.y - half * 0.5);
    ctx.lineTo(e.x - half + 2 + (i + 1) * w, e.y - half * 0.5);
    ctx.lineTo(e.x - half + 2 + (i + 1) * w, e.y - half * 0.1);
    ctx.arc(e.x - half + 2 + (i + 0.5) * w, e.y - half * 0.1, w / 2, 0, Math.PI);
    ctx.closePath();
    ctx.fill();
  }
  // goods: crates and a gold sack
  ctx.fillStyle = PAL.woodDark;
  ctx.fillRect(e.x - half * 0.55, e.y + half * 0.35, half * 0.34, half * 0.3);
  ctx.fillStyle = PAL.uiParchmentDark;
  ctx.beginPath();
  ctx.arc(e.x + half * 0.4, e.y + half * 0.5, half * 0.18, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = PAL.goldVein;
  ctx.beginPath();
  ctx.arc(e.x + half * 0.4, e.y + half * 0.42, half * 0.07, 0, Math.PI * 2);
  ctx.fill();
}

function drawPalisade(ctx: Ctx, e: Entity, half: number) {
  // A run of sharpened logs lashed together.
  ctx.fillStyle = PAL.woodDark;
  ctx.fillRect(e.x - half, e.y - 2, half * 2, 5);
  const stakes = 4;
  for (let i = 0; i < stakes; i++) {
    const sx = e.x - half + 3 + (i * (half * 2 - 6)) / (stakes - 1);
    ctx.fillStyle = shade(PAL.wood, i % 2 ? -0.08 : 0.05);
    ctx.beginPath();
    ctx.moveTo(sx - 3, e.y + half * 0.5);
    ctx.lineTo(sx - 3, e.y - half * 0.5);
    ctx.lineTo(sx, e.y - half * 0.9);
    ctx.lineTo(sx + 3, e.y - half * 0.5);
    ctx.lineTo(sx + 3, e.y + half * 0.5);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = withAlpha("#ffffff", 0.1);
    ctx.fillRect(sx - 3, e.y - half * 0.5, 1.5, half);
  }
}

function drawStoneWall(ctx: Ctx, e: Entity, half: number) {
  const g = ctx.createLinearGradient(e.x, e.y - half, e.x, e.y + half);
  g.addColorStop(0, PAL.stoneLight);
  g.addColorStop(1, PAL.stoneDark);
  ctx.fillStyle = g;
  ctx.fillRect(e.x - half, e.y - half * 0.7, half * 2, half * 1.5);
  // crenellations
  ctx.fillStyle = PAL.stoneDark;
  for (let i = 0; i < 3; i++) {
    ctx.fillRect(e.x - half + i * (half * 0.72), e.y - half * 0.95, half * 0.5, half * 0.3);
  }
  // mortar lines
  ctx.strokeStyle = withAlpha(PAL.stoneDark, 0.6);
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(e.x - half, e.y);
  ctx.lineTo(e.x + half, e.y);
  ctx.moveTo(e.x, e.y - half * 0.7);
  ctx.lineTo(e.x, e.y + half * 0.8);
  ctx.stroke();
}

function drawGate(ctx: Ctx, e: Entity, half: number, tc: any) {
  // stone posts
  ctx.fillStyle = PAL.stoneDark;
  ctx.fillRect(e.x - half, e.y - half, half * 0.5, half * 2);
  ctx.fillRect(e.x + half * 0.5, e.y - half, half * 0.5, half * 2);
  ctx.fillStyle = PAL.stone;
  ctx.fillRect(e.x - half + 1, e.y - half, half * 0.4, half * 0.4);
  ctx.fillRect(e.x + half * 0.55, e.y - half, half * 0.4, half * 0.4);
  // doors
  if (e.gateOpen) {
    ctx.fillStyle = PAL.woodDark;
    ctx.save();
    ctx.translate(e.x - half * 0.5, e.y);
    ctx.rotate(-0.9);
    ctx.fillRect(-half * 0.1, -half * 0.55, half * 0.5, half * 0.5);
    ctx.restore();
    ctx.save();
    ctx.translate(e.x + half * 0.5, e.y);
    ctx.rotate(0.9);
    ctx.fillRect(-half * 0.4, -half * 0.55, half * 0.5, half * 0.5);
    ctx.restore();
  } else {
    ctx.fillStyle = PAL.wood;
    ctx.fillRect(e.x - half * 0.5, e.y - half * 0.7, half, half * 1.4);
    ctx.strokeStyle = PAL.woodDark;
    ctx.lineWidth = 1.5;
    for (let i = -1; i <= 1; i++) {
      ctx.beginPath();
      ctx.moveTo(e.x + i * half * 0.3, e.y - half * 0.7);
      ctx.lineTo(e.x + i * half * 0.3, e.y + half * 0.7);
      ctx.stroke();
    }
    ctx.beginPath();
    ctx.moveTo(e.x - half * 0.5, e.y);
    ctx.lineTo(e.x + half * 0.5, e.y);
    ctx.stroke();
  }
  // team pennant on the post
  ctx.fillStyle = tc.main;
  ctx.fillRect(e.x - half, e.y - half - 1, half * 2, 2.5);
}

function drawWatchfire(ctx: Ctx, e: Entity, half: number, time: number) {
  const flick = 0.78 + Math.sin(time * 11 + e.x) * 0.12 + Math.sin(time * 17 + e.y) * 0.1;
  const night = isNight(gDayPhase);
  // Warm ground glow — bigger and brighter once the sun is down.
  const glowR = half * (night ? 4.6 : 2.4) * flick;
  const g = ctx.createRadialGradient(e.x, e.y - half * 0.3, 1, e.x, e.y - half * 0.3, glowR);
  g.addColorStop(0, withAlpha("#ffd27a", night ? 0.5 : 0.28));
  g.addColorStop(0.5, withAlpha("#ff9a3c", night ? 0.22 : 0.1));
  g.addColorStop(1, withAlpha("#ff9a3c", 0));
  ctx.fillStyle = g;
  ctx.beginPath();
  ctx.arc(e.x, e.y - half * 0.3, glowR, 0, Math.PI * 2);
  ctx.fill();
  // Three legs of a timber brazier stand.
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 2;
  for (const dx of [-half * 0.55, 0, half * 0.55]) {
    ctx.beginPath();
    ctx.moveTo(e.x + dx, e.y - half * 0.2);
    ctx.lineTo(e.x + dx * 0.35, e.y + half * 0.7);
    ctx.stroke();
  }
  // Iron bowl.
  ctx.fillStyle = "#3a3026";
  ctx.beginPath();
  ctx.ellipse(e.x, e.y - half * 0.2, half * 0.7, half * 0.32, 0, 0, Math.PI * 2);
  ctx.fill();
  // Flames — stacked teardrops, hot core to cool tip, dancing with the flicker.
  const fh = half * (1.5 + flick * 0.5);
  for (const [col, sc, off] of [["#ff7a1c", 1, 0], ["#ffb43c", 0.66, 0.18], ["#ffe98a", 0.34, 0.34]] as const) {
    ctx.fillStyle = col;
    const sway = Math.sin(time * 9 + off * 10) * half * 0.18;
    ctx.beginPath();
    ctx.moveTo(e.x + sway, e.y - half * 0.35 - fh * sc);
    ctx.quadraticCurveTo(e.x - half * 0.6 * sc, e.y - half * 0.35 - fh * sc * 0.35, e.x, e.y - half * 0.3);
    ctx.quadraticCurveTo(e.x + half * 0.6 * sc, e.y - half * 0.35 - fh * sc * 0.35, e.x + sway, e.y - half * 0.35 - fh * sc);
    ctx.fill();
  }
}

function drawTower(ctx: Ctx, e: Entity, half: number, tc: any) {
  // tall cylinder reads via horizontal gradient (rounded shading)
  const g = ctx.createLinearGradient(e.x - half, e.y, e.x + half, e.y);
  g.addColorStop(0, PAL.stoneLight);
  g.addColorStop(0.5, PAL.stone);
  g.addColorStop(1, PAL.stoneDark);
  ctx.fillStyle = g;
  ctx.beginPath();
  ctx.roundRect(e.x - half * 0.75, e.y - half * 1.05, half * 1.5, half * 1.85, 4);
  ctx.fill();
  softOutline(ctx, 1.4);
  // mortar courses
  ctx.strokeStyle = "rgba(70,64,56,0.3)";
  ctx.lineWidth = 1;
  for (let yy = e.y - half * 0.7; yy < e.y + half * 0.7; yy += half * 0.4) {
    ctx.beginPath();
    ctx.moveTo(e.x - half * 0.72, yy);
    ctx.lineTo(e.x + half * 0.72, yy);
    ctx.stroke();
  }
  // arrow slit
  ctx.fillStyle = "#241c12";
  ctx.fillRect(e.x - 1.5, e.y - half * 0.4, 3, half * 0.5);
  // pointed team-colour roof + finial flag
  coneRoof(ctx, e.x, e.y - half * 1.0, half * 0.92, e.y - half * 1.8, tc);
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(e.x, e.y - half * 1.8);
  ctx.lineTo(e.x, e.y - half * 2.05);
  ctx.stroke();
  ctx.fillStyle = tc.light;
  ctx.beginPath();
  ctx.moveTo(e.x, e.y - half * 2.05);
  ctx.lineTo(e.x + half * 0.4, e.y - half * 1.98);
  ctx.lineTo(e.x, e.y - half * 1.9);
  ctx.fill();
}

function drawCastle(ctx: Ctx, e: Entity, half: number, tc: any, time: number) {
  // four corner towers, each capped with a team-colour cone
  for (const [px, py] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
    const cx = e.x + px * half * 0.72;
    const cy = e.y + py * half * 0.72;
    ctx.fillStyle = grad(ctx, cx - half * 0.32, cy, cx + half * 0.32, cy, PAL.stoneLight, PAL.stoneDark);
    ctx.beginPath();
    ctx.arc(cx, cy, half * 0.32, 0, Math.PI * 2);
    ctx.fill();
    softOutline(ctx, 1.2);
    coneRoof(ctx, cx, cy - half * 0.22, half * 0.36, cy - half * 0.78, tc);
  }
  // keep
  stoneBlock(ctx, e.x - half * 0.62, e.y - half * 0.55, half * 1.24, half * 1.1);
  // crenellated top edge
  ctx.fillStyle = PAL.stoneDark;
  for (let i = 0; i < 5; i++) {
    ctx.fillRect(e.x - half * 0.62 + i * half * 0.28, e.y - half * 0.66, half * 0.16, half * 0.16);
  }
  // gate
  ctx.fillStyle = "#241c12";
  ctx.beginPath();
  ctx.arc(e.x, e.y + half * 0.45, half * 0.2, Math.PI, 0);
  ctx.fill();
  ctx.fillRect(e.x - half * 0.2, e.y + half * 0.45, half * 0.4, half * 0.2);
  ctx.strokeStyle = PAL.steelDark;
  ctx.lineWidth = 1.5;
  for (let i = -1; i <= 1; i++) {
    ctx.beginPath();
    ctx.moveTo(e.x + i * half * 0.1, e.y + half * 0.28);
    ctx.lineTo(e.x + i * half * 0.1, e.y + half * 0.65);
    ctx.stroke();
  }
  banner(ctx, e.x, e.y - half * 0.7, tc, time, e.id);
}

function drawSiegeWorkshop(ctx: Ctx, e: Entity, half: number) {
  // open timber frame shed
  ctx.fillStyle = PAL.dirt;
  ctx.fillRect(e.x - half + 4, e.y - half * 0.2, half * 2 - 8, half * 1.1);
  roofGable(ctx, e.x, e.y - half * 0.55, half * 2.05, half * 0.8, PAL.woodDark);
  ctx.fillStyle = PAL.wood;
  for (const px of [-1, 1]) {
    ctx.fillRect(e.x + px * (half - 8) - 3, e.y - half * 0.2, 6, half * 1.1);
  }
  // wheel + beam (catapult parts)
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.arc(e.x - half * 0.3, e.y + half * 0.4, half * 0.22, 0, Math.PI * 2);
  ctx.stroke();
  for (let i = 0; i < 4; i++) {
    const a = (i * Math.PI) / 2 + 0.4;
    ctx.beginPath();
    ctx.moveTo(e.x - half * 0.3, e.y + half * 0.4);
    ctx.lineTo(e.x - half * 0.3 + Math.cos(a) * half * 0.22, e.y + half * 0.4 + Math.sin(a) * half * 0.22);
    ctx.stroke();
  }
  ctx.fillStyle = PAL.woodLight;
  ctx.fillRect(e.x + half * 0.05, e.y + half * 0.3, half * 0.7, 5);
}

// -------------------------------------------------------------------- units --

export function drawUnit(ctx: Ctx, e: Entity, time: number, lod = 0) {
  const tc = tcol(e.team);
  // Level-of-detail blob: a single filled body (+ a flag dot for the champion).
  // Used when zoomed out / in big crowds, where the full art is sub-pixel.
  if (lod > 0) {
    ctx.fillStyle = withAlpha("#0a0805", 0.28);
    ctx.beginPath();
    ctx.ellipse(e.x, e.y + e.radius * 0.55, e.radius * 0.9, e.radius * 0.4, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = tc.main;
    ctx.beginPath();
    ctx.ellipse(e.x, e.y, e.radius * 0.92, e.radius * 0.72, 0, 0, Math.PI * 2);
    ctx.fill();
    if (UNITS[e.type]?.hero) { ctx.fillStyle = "#ffd24a"; ctx.beginPath(); ctx.arc(e.x, e.y - e.radius, 2.2, 0, Math.PI * 2); ctx.fill(); }
    return;
  }
  const moving = Math.hypot(e.vx, e.vy) > 4;
  const bob = moving ? Math.sin(e.animPhase * 9) * 1.4 : Math.sin(e.animPhase * 2.4) * 0.4;
  // Attack lunge: strongest right after an attack starts cooling down.
  const atkFrac = e.attackInterval > 0 ? e.attackCooldown / e.attackInterval : 0;
  const lunge = atkFrac > 0.82 ? (atkFrac - 0.82) / 0.18 : 0;

  // Rarity aura under high-tier player units.
  if (e.variantRarity >= 2) {
    const r = rarityByIndex(e.variantRarity);
    const pulse = 0.5 + Math.sin(time * 3 + e.id) * 0.18;
    ctx.fillStyle = withAlpha(r.glow.startsWith("#") ? r.glow : "#888888", 0.16 * pulse * e.variantRarity);
    ctx.beginPath();
    ctx.ellipse(e.x, e.y + e.radius * 0.5, e.radius * 1.7, e.radius * 0.8, 0, 0, Math.PI * 2);
    ctx.fill();
  }

  // Status aura: the unit's own active ability, or a rally/slow applied to it.
  // A pulsing coloured ring beneath the feet, colour by status (priority order).
  let auraColor: string | null = null;
  if (e.abilityActive > 0 && ABILITIES[e.type]) auraColor = ABILITIES[e.type].color;
  else if (e.rallyTimer > 0) auraColor = "#ffcf5a"; // War Cry rally
  else if (e.slowTimer > 0) auraColor = "#8ad6ff"; // Caltrops slow
  if (auraColor) {
    const pulse = 0.62 + Math.sin(time * 9 + e.id) * 0.22;
    ctx.strokeStyle = withAlpha(auraColor, 0.85 * pulse);
    ctx.lineWidth = 2.2;
    ctx.beginPath();
    ctx.ellipse(e.x, e.y + e.radius * 0.5, e.radius * 1.5, e.radius * 0.7, 0, 0, Math.PI * 2);
    ctx.stroke();
    ctx.fillStyle = withAlpha(auraColor, 0.14 * pulse);
    ctx.fill();
  }

  // Heroes wear a permanent golden aura plus level pips above their head.
  if (UNITS[e.type]?.hero) {
    const pulse = 0.6 + Math.sin(time * 3 + e.id) * 0.2;
    ctx.fillStyle = withAlpha("#ffd24a", 0.16 * pulse);
    ctx.beginPath();
    ctx.ellipse(e.x, e.y + e.radius * 0.5, e.radius * 1.9, e.radius * 0.95, 0, 0, Math.PI * 2);
    ctx.fill();
    for (let i = 0; i < e.heroLevel; i++) {
      ctx.fillStyle = "#ffe07a";
      ctx.beginPath();
      ctx.arc(e.x - (e.heroLevel - 1) * 3 + i * 6, e.y - e.radius * 2.1, 2, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  // Veterancy chevrons above seasoned units (gold, one per rank).
  //
  // Drawn twice: a dark stroke underneath, then the gold on top. A 1.4px gold
  // line on its own vanished against pale ground and any bright particle, which
  // meant a rank that was quietly adding HP and attack was invisible exactly
  // when it mattered — in the middle of a fight.
  if (e.veterancy > 0 && !UNITS[e.type]?.hero) {
    const cy = e.y - e.radius * 1.7;
    const col = e.veterancy >= 3 ? "#ffe07a" : e.veterancy >= 2 ? "#ffd24a" : "#e8c98a";
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    for (const [stroke, width] of [["rgba(12,10,6,0.75)", 3.2], [col, 1.6]] as const) {
      ctx.strokeStyle = stroke;
      ctx.lineWidth = width;
      for (let i = 0; i < e.veterancy; i++) {
        const cx = e.x - (e.veterancy - 1) * 3.5 + i * 7;
        ctx.beginPath();
        ctx.moveTo(cx - 2.6, cy + 1.6);
        ctx.lineTo(cx, cy - 1.6);
        ctx.lineTo(cx + 2.6, cy + 1.6);
        ctx.stroke();
      }
    }
    ctx.lineCap = "butt";
    ctx.lineJoin = "miter";
  }

  shadow(ctx, e.x + 1, e.y + e.radius * 0.55, e.radius * 0.95, e.radius * 0.42);
  teamRing(ctx, e.x, e.y + e.radius * 0.55, e.radius, tc);

  ctx.save();
  ctx.translate(e.x + Math.cos(e.facing) * lunge * 4, e.y - bob + Math.sin(e.facing) * lunge * 4);

  const usprite = spriteFor(e.type);
  if (usprite) { blitSprite(ctx, usprite, e.radius); ctx.restore(); return drawUnitOverlays(ctx, e, bob); }

  switch (e.type) {
    case "villager": drawVillager(ctx, e, tc, moving); break;
    case "militia": drawMilitia(ctx, e, tc, lunge); break;
    case "shieldbearer": drawShieldbearer(ctx, e, tc, lunge); break;
    case "berserker": drawBerserker(ctx, e, tc, lunge, time); break;
    case "longbow": drawLongbow(ctx, e, tc, atkFrac); break;
    case "cataphract": drawCataphract(ctx, e, tc, moving, time, lunge); break;
    case "battlemage": drawBattlemage(ctx, e, tc, atkFrac, time); break;
    case "bombard": drawBombard(ctx, e, tc, atkFrac); break;
    case "twohand": drawTwohand(ctx, e, tc, lunge); break;
    case "spearman": drawSpearman(ctx, e, tc, lunge); break;
    case "pikeman": drawPikeman(ctx, e, tc, lunge); break;
    case "scout": drawScout(ctx, e, tc, moving); break;
    case "archer": drawArcher(ctx, e, tc, atkFrac); break;
    case "skirmisher": drawSkirmisher(ctx, e, tc, atkFrac); break;
    case "crossbow": drawCrossbow(ctx, e, tc, atkFrac); break;
    case "knight": drawKnight(ctx, e, tc, moving, time, lunge); break;
    case "horseman": drawHorseman(ctx, e, tc, moving); break;
    case "raider": drawRaider(ctx, e, tc, moving, lunge); break;
    case "javelin": drawJavelin(ctx, e, tc, atkFrac); break;
    case "handcannon": drawHandcannon(ctx, e, tc, atkFrac); break;
    case "catapult": drawCatapult(ctx, e, tc, atkFrac); break;
    case "trebuchet": drawTrebuchet(ctx, e, tc, atkFrac); break;
    case "ram": drawRam(ctx, e, tc, atkFrac); break;
    case "hero": drawHero(ctx, e, tc, lunge, time); break;
    case "king": drawKing(ctx, e, tc, lunge, time); break;
    case "monk": drawMonk(ctx, e, tc, time); break;
    default: {
      ctx.fillStyle = tc.main;
      ctx.beginPath();
      ctx.arc(0, 0, e.radius, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  ctx.restore();
  drawUnitOverlays(ctx, e, bob);
}

// Rarity trim ring + hit flash, drawn in world space after the body (procedural
// or baked sprite). Shared so the sprite fast-path keeps these cues.
function drawUnitOverlays(ctx: Ctx, e: Entity, bob: number) {
  if (e.variantRarity >= 1) {
    const r = rarityByIndex(e.variantRarity);
    ctx.strokeStyle = withAlpha(r.color, 0.85);
    ctx.lineWidth = e.variantRarity >= 4 ? 2 : 1.2;
    ctx.beginPath();
    ctx.arc(e.x, e.y - bob, e.radius + 2.5, 0, Math.PI * 2);
    ctx.stroke();
  }

  if (e.hitFlash > 0) {
    ctx.fillStyle = withAlpha("#ffffff", e.hitFlash * 0.5);
    ctx.beginPath();
    ctx.arc(e.x, e.y - bob, e.radius + 1, 0, Math.PI * 2);
    ctx.fill();
  }
}

// ============================================================================
// Articulated figure rig. Units are drawn as little upright "toy soldiers" with
// shaded, layered parts and a parametric animation: a continuous walk cycle
// (striding legs + arm/torso bob) and an attack that sweeps the weapon through
// an arc. Everything is oriented at runtime by `facing`, so a single rig covers
// all 360° — no baked sprite angles. Drawing order per unit: legs → off-hand →
// torso → head → weapon, so the figure layers correctly.
// ============================================================================

function grad(ctx: Ctx, x0: number, y0: number, x1: number, y1: number, c0: string, c1: string) {
  const g = ctx.createLinearGradient(x0, y0, x1, y1);
  g.addColorStop(0, c0);
  g.addColorStop(1, c1);
  return g;
}

/** Stroke the path just filled with a soft dark outline for a crisp read. */
function softOutline(ctx: Ctx, w = 1.6) {
  ctx.strokeStyle = "rgba(20,16,10,0.42)";
  ctx.lineWidth = w;
  ctx.stroke();
}

/** A shaded round-capped limb between two points. */
function capsule(ctx: Ctx, x0: number, y0: number, x1: number, y1: number, w: number, c0: string, c1: string) {
  ctx.strokeStyle = grad(ctx, x0, y0, x1, y1, c0, c1);
  ctx.lineCap = "round";
  ctx.lineWidth = w;
  ctx.beginPath();
  ctx.moveTo(x0, y0);
  ctx.lineTo(x1, y1);
  ctx.stroke();
}

/** Walk phase: legs/arms swing strongly when moving, sway gently when idle. */
function gait(e: Entity): number {
  const moving = Math.hypot(e.vx, e.vy) > 4;
  return moving ? Math.sin(e.animPhase * 9) : Math.sin(e.animPhase * 2.2) * 0.2;
}

/** Two striding legs + boots, drawn behind the torso. */
function drawLegs(ctx: Ctx, r: number, step: number, col: string) {
  for (const side of [1, -1]) {
    const sw = step * side;
    const hipX = side * r * 0.24;
    const footX = hipX + sw * r * 0.4;
    const lift = Math.max(0, sw) * r * 0.16;
    capsule(ctx, hipX, r * 0.32, footX, r * 0.8 - lift, r * 0.24, shade(col, 0.05), shade(col, -0.3));
    ctx.fillStyle = shade(PAL.leather, -0.1);
    ctx.beginPath();
    ctx.ellipse(footX + r * 0.05, r * 0.8 - lift, r * 0.16, r * 0.09, 0, 0, Math.PI * 2);
    ctx.fill();
  }
}

interface BodyOpts {
  pauldrons?: boolean; // steel shoulder plates
  chestPlate?: boolean; // steel breastplate over the surcoat
  sash?: string; // diagonal team sash colour
  legCol?: string; // override leg colour
}

/** Striding legs + a layered, shaded torso (surcoat + belt + rim light). */
function body(ctx: Ctx, e: Entity, cloth: string, clothDark: string, opts: BodyOpts = {}) {
  const r = e.radius;
  drawLegs(ctx, r, gait(e), opts.legCol ?? clothDark);
  // surcoat
  ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.45, shade(cloth, 0.16), clothDark);
  ctx.beginPath();
  ctx.moveTo(-r * 0.5, r * 0.42);
  ctx.quadraticCurveTo(-r * 0.6, -r * 0.5, 0, -r * 0.58);
  ctx.quadraticCurveTo(r * 0.6, -r * 0.5, r * 0.5, r * 0.42);
  ctx.quadraticCurveTo(0, r * 0.6, -r * 0.5, r * 0.42);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1.8);
  if (opts.chestPlate) {
    ctx.fillStyle = grad(ctx, 0, -r * 0.4, 0, r * 0.3, PAL.steel, PAL.steelDark);
    ctx.beginPath();
    ctx.arc(0, -r * 0.02, r * 0.42, 0, Math.PI * 2);
    ctx.fill();
    softOutline(ctx, 1.2);
  }
  if (opts.sash) {
    ctx.strokeStyle = opts.sash;
    ctx.lineWidth = 2.6;
    ctx.beginPath();
    ctx.moveTo(-r * 0.45, -r * 0.35);
    ctx.lineTo(r * 0.42, r * 0.46);
    ctx.stroke();
  }
  // belt
  ctx.fillStyle = shade(PAL.leather, -0.05);
  ctx.fillRect(-r * 0.46, r * 0.24, r * 0.92, r * 0.11);
  // top-left rim light
  ctx.strokeStyle = "rgba(255,255,255,0.32)";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(-r * 0.48, r * 0.18);
  ctx.quadraticCurveTo(-r * 0.56, -r * 0.44, -r * 0.04, -r * 0.55);
  ctx.stroke();
  // steel pauldrons
  if (opts.pauldrons) {
    for (const sx of [-1, 1]) {
      ctx.fillStyle = grad(ctx, sx * r * 0.5, -r * 0.5, sx * r * 0.5, -r * 0.2, PAL.steel, PAL.steelDark);
      ctx.beginPath();
      ctx.ellipse(sx * r * 0.46, -r * 0.32, r * 0.2, r * 0.15, sx * 0.4, 0, Math.PI * 2);
      ctx.fill();
      softOutline(ctx, 1.2);
    }
  }
}

type HelmKind = "open" | "full" | "cap" | "hood" | "bare";
interface HeadOpts {
  helm?: HelmKind;
  tone?: string; // helmet base colour (defaults to steel)
  plume?: string; // plume / crest colour
  hair?: string; // visible hair colour for bare heads
}

/** Skin head + a shaded helmet variant, optional plume. */
function head(ctx: Ctx, r: number, opts: HeadOpts = {}) {
  const hy = -r * 0.78;
  const helm = opts.helm ?? "open";
  const tone = opts.tone ?? PAL.steel;
  ctx.fillStyle = PAL.skin;
  ctx.beginPath();
  ctx.arc(0, hy, r * 0.3, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.1);
  if (opts.hair && (helm === "bare")) {
    ctx.fillStyle = opts.hair;
    ctx.beginPath();
    ctx.arc(0, hy - r * 0.04, r * 0.3, Math.PI * 1.02, Math.PI * 1.98);
    ctx.fill();
  }
  if (helm === "cap" || helm === "open" || helm === "full") {
    ctx.fillStyle = grad(ctx, 0, hy - r * 0.3, 0, hy + r * 0.1, shade(tone, 0.16), shade(tone, -0.16));
    ctx.beginPath();
    ctx.arc(0, hy - r * 0.04, r * 0.31, Math.PI * 0.95, Math.PI * 2.05);
    ctx.fill();
    softOutline(ctx, 1);
    if (helm === "full") {
      ctx.fillStyle = shade(tone, -0.05);
      ctx.beginPath();
      ctx.arc(0, hy, r * 0.31, Math.PI * 1.04, Math.PI * 1.96);
      ctx.fill();
      ctx.strokeStyle = PAL.steelDark;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(0, hy - r * 0.1);
      ctx.lineTo(0, hy + r * 0.16);
      ctx.stroke();
    } else if (helm === "open") {
      ctx.strokeStyle = PAL.steelDark; // nasal bar
      ctx.lineWidth = 1.3;
      ctx.beginPath();
      ctx.moveTo(0, hy - r * 0.06);
      ctx.lineTo(0, hy + r * 0.12);
      ctx.stroke();
    }
  } else if (helm === "hood") {
    ctx.fillStyle = grad(ctx, 0, hy - r * 0.3, 0, hy + r * 0.2, shade(tone, 0.1), shade(tone, -0.2));
    ctx.beginPath();
    ctx.arc(0, hy - r * 0.02, r * 0.36, Math.PI * 0.78, Math.PI * 2.22);
    ctx.fill();
    softOutline(ctx, 1);
  }
  if (opts.plume) {
    ctx.fillStyle = opts.plume;
    ctx.beginPath();
    ctx.moveTo(-r * 0.02, hy - r * 0.32);
    ctx.quadraticCurveTo(r * 0.2, hy - r * 0.72, r * 0.05, hy - r * 0.34);
    ctx.quadraticCurveTo(-r * 0.04, hy - r * 0.4, -r * 0.02, hy - r * 0.32);
    ctx.fill();
  }
}

function weaponAngleParts(facing: number): [number, number] {
  return [Math.cos(facing), Math.sin(facing)];
}

/** A melee weapon swing factor (1 = mid-strike, eases to 0 during recovery),
 *  derived from the attack cooldown which is full right after a blow lands. */
function strikeT(e: Entity): number {
  const f = e.attackInterval > 0 ? e.attackCooldown / e.attackInterval : 0;
  return f > 0.55 ? (f - 0.55) / 0.45 : 0;
}

/** Facing rotated through a swing arc as a strike plays out — gives weapons a
 *  real sweep instead of a static poke, while staying oriented to `facing`. */
function swungDir(e: Entity, arc: number): [number, number, number] {
  const s = strikeT(e);
  const ang = e.facing + (s - 0.5) * arc;
  return [Math.cos(ang), Math.sin(ang), s];
}

/** Draw a shaded blade from the body out along (dx,dy). */
function blade(ctx: Ctx, r: number, dx: number, dy: number, len: number, hiltCol: string, len0 = 0.3) {
  const x0 = dx * r * len0;
  const y0 = dy * r * len0;
  // crossguard
  ctx.strokeStyle = hiltCol;
  ctx.lineWidth = r * 0.1;
  ctx.beginPath();
  ctx.moveTo(x0 - dy * r * 0.22, y0 + dx * r * 0.22);
  ctx.lineTo(x0 + dy * r * 0.22, y0 - dx * r * 0.22);
  ctx.stroke();
  // blade with a bright edge glint
  ctx.strokeStyle = grad(ctx, x0, y0, dx * r * len, dy * r * len, "#f2f4f8", PAL.steelDark);
  ctx.lineWidth = r * 0.13;
  ctx.lineCap = "round";
  ctx.beginPath();
  ctx.moveTo(x0, y0);
  ctx.lineTo(dx * r * len, dy * r * len);
  ctx.stroke();
  ctx.strokeStyle = "rgba(255,255,255,0.85)";
  ctx.lineWidth = 1.3;
  ctx.beginPath();
  ctx.moveTo(x0 + dx * r * 0.2, y0 + dy * r * 0.2);
  ctx.lineTo(dx * r * len, dy * r * len);
  ctx.stroke();
}

/** A round shield on the off-hand (opposite the weapon), facing-aware. */
function roundShield(ctx: Ctx, r: number, fx: number, fy: number, tc: any) {
  const sx = -fy * r * 0.7;
  const sy = fx * r * 0.7;
  ctx.fillStyle = grad(ctx, sx, sy - r * 0.4, sx, sy + r * 0.4, PAL.steel, PAL.steelDark);
  ctx.beginPath();
  ctx.ellipse(sx, sy, r * 0.38, r * 0.46, 0, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.4);
  ctx.fillStyle = tc.main;
  ctx.beginPath();
  ctx.arc(sx, sy, r * 0.15, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = "rgba(255,255,255,0.45)";
  ctx.lineWidth = 1.2;
  ctx.beginPath();
  ctx.arc(sx, sy, r * 0.27, 0, Math.PI * 2);
  ctx.stroke();
}

function drawVillager(ctx: Ctx, e: Entity, tc: any, moving: boolean) {
  const r = e.radius;
  body(ctx, e, PAL.leather, shade(PAL.leather, -0.25), { sash: tc.main });
  head(ctx, r, { helm: "bare", hair: "#5a4326" });
  // tool: hatchet, worked up and down as it gathers (or shouldered when idle)
  const [fx, fy] = weaponAngleParts(e.facing);
  const chop = strikeT(e);
  const len = 1.0 + chop * 0.4;
  ctx.strokeStyle = grad(ctx, fx * r * 0.4, fy * r * 0.4, fx * r * len, fy * r * len, PAL.woodLight, PAL.woodDark);
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.12;
  ctx.beginPath();
  ctx.moveTo(fx * r * 0.4, fy * r * 0.4);
  ctx.lineTo(fx * r * len, fy * r * len);
  ctx.stroke();
  ctx.fillStyle = PAL.steel;
  ctx.beginPath();
  ctx.moveTo(fx * r * len, fy * r * len);
  ctx.lineTo(fx * r * len - fy * r * 0.22, fy * r * len + fx * r * 0.22);
  ctx.lineTo(fx * r * (len + 0.18), fy * r * (len + 0.18));
  ctx.closePath();
  ctx.fill();
  // carry bundle
  if (e.carry > 2 && e.carryKind) {
    const col = e.carryKind === "wood" ? PAL.trunk : e.carryKind === "gold" ? PAL.goldVein : PAL.berry;
    ctx.fillStyle = col;
    ctx.beginPath();
    ctx.arc(-fx * r * 0.8, -fy * r * 0.8 - r * 0.4, r * 0.4, 0, Math.PI * 2);
    ctx.fill();
    softOutline(ctx, 1.2);
  }
}

function drawMilitia(ctx: Ctx, e: Entity, tc: any, lunge: number) {
  const r = e.radius;
  const [fx, fy] = weaponAngleParts(e.facing);
  roundShield(ctx, r, fx, fy, tc); // off-hand (behind torso)
  body(ctx, e, tc.main, tc.dark, { pauldrons: true });
  head(ctx, r, { helm: "open", tone: PAL.steel });
  // one-handed sword, sweeping through its strike
  const [dx, dy] = swungDir(e, 1.0);
  blade(ctx, r, dx, dy, 1.5 + lunge * 0.4, shade(tc.dark, -0.1));
}

function drawSpearman(ctx: Ctx, e: Entity, tc: any, lunge: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark);
  head(ctx, r, { helm: "cap", tone: PAL.leather });
  const [fx, fy] = weaponAngleParts(e.facing);
  // spear thrusts forward on the strike rather than swinging
  const reach = 2.2 + lunge * 0.8 + strikeT(e) * 0.5;
  ctx.strokeStyle = grad(ctx, -fx * r * 0.8, -fy * r * 0.8, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark);
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.1;
  ctx.beginPath();
  ctx.moveTo(-fx * r * 0.8, -fy * r * 0.8);
  ctx.lineTo(fx * r * reach, fy * r * reach);
  ctx.stroke();
  ctx.fillStyle = PAL.steel;
  const tipX = fx * r * reach;
  const tipY = fy * r * reach;
  ctx.beginPath();
  ctx.moveTo(tipX + fx * 4, tipY + fy * 4);
  ctx.lineTo(tipX - fy * 2.4, tipY + fx * 2.4);
  ctx.lineTo(tipX + fy * 2.4, tipY - fx * 2.4);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1);
}

/** A wall of a man: full helm, heavy pauldrons and a tower shield up front. */
function drawShieldbearer(ctx: Ctx, e: Entity, tc: any, lunge: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark, { pauldrons: true, chestPlate: true });
  head(ctx, r, { helm: "full", tone: PAL.steel });
  const [fx, fy] = weaponAngleParts(e.facing);
  // Short stabbing sword, kept close.
  ctx.strokeStyle = PAL.steel;
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.16;
  ctx.beginPath();
  ctx.moveTo(-fy * r * 0.5, fx * r * 0.5);
  ctx.lineTo(-fy * r * 0.5 + fx * r * (1.1 + lunge * 0.5), fx * r * 0.5 + fy * r * (1.1 + lunge * 0.5));
  ctx.stroke();
  // The shield itself — big, slightly forward, team-coloured with a boss.
  const sx = fx * r * 0.85 + fy * r * 0.45;
  const sy = fy * r * 0.85 - fx * r * 0.45;
  ctx.fillStyle = grad(ctx, sx, sy - r * 0.7, sx, sy + r * 0.7, shade(tc.light, 0.1), tc.dark);
  ctx.beginPath();
  ctx.ellipse(sx, sy, r * 0.5, r * 0.82, e.facing, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = PAL.steelDark;
  ctx.lineWidth = r * 0.1;
  ctx.stroke();
  ctx.fillStyle = PAL.steel;
  ctx.beginPath();
  ctx.arc(sx, sy, r * 0.17, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1);
}

/** Bare-headed, wild-haired, an axe in each hand. No armour anywhere. */
function drawBerserker(ctx: Ctx, e: Entity, tc: any, lunge: number, time: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark, { sash: tc.light });
  head(ctx, r, { helm: "bare", hair: "#b8763a" });
  const [fx, fy] = weaponAngleParts(e.facing);
  const swing = lunge * 0.9 + Math.sin(time * 3 + e.id) * 0.06;
  for (const side of [-1, 1]) {
    const hx = -fy * r * 0.6 * side;
    const hy = fx * r * 0.6 * side;
    const tx = hx + fx * r * (1.25 + swing);
    const ty = hy + fy * r * (1.25 + swing);
    ctx.strokeStyle = PAL.woodDark;
    ctx.lineCap = "round";
    ctx.lineWidth = r * 0.11;
    ctx.beginPath();
    ctx.moveTo(hx, hy);
    ctx.lineTo(tx, ty);
    ctx.stroke();
    // Axe head, angled off the haft.
    ctx.fillStyle = grad(ctx, tx, ty - r * 0.3, tx, ty + r * 0.3, PAL.steel, PAL.steelDark);
    ctx.beginPath();
    ctx.moveTo(tx, ty);
    ctx.lineTo(tx + fx * r * 0.34 - fy * r * 0.36 * side, ty + fy * r * 0.34 + fx * r * 0.36 * side);
    ctx.lineTo(tx - fx * r * 0.12 - fy * r * 0.42 * side, ty - fy * r * 0.12 + fx * r * 0.42 * side);
    ctx.closePath();
    ctx.fill();
  }
  softOutline(ctx, 1);
}

/** A very tall bow, drawn past the ear — the silhouette is the whole point. */
function drawLongbow(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark, { sash: tc.light });
  head(ctx, r, { helm: "cap", tone: "#4a5c3a" });
  const [fx, fy] = weaponAngleParts(e.facing);
  const draw = Math.max(0, 1 - atkFrac); // full draw just before loosing
  const bx = fx * r * 0.55 - fy * r * 0.2;
  const by = fy * r * 0.55 + fx * r * 0.2;
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = r * 0.11;
  ctx.lineCap = "round";
  ctx.beginPath();
  ctx.ellipse(bx, by, r * 0.34, r * 1.5, e.facing, Math.PI * 0.42, Math.PI * 1.58);
  ctx.stroke();
  // String, pulled back as the shot builds.
  ctx.strokeStyle = "rgba(240,236,220,0.85)";
  ctx.lineWidth = Math.max(0.6, r * 0.045);
  const nockX = bx - fx * r * 0.75 * draw;
  const nockY = by - fy * r * 0.75 * draw;
  ctx.beginPath();
  ctx.moveTo(bx - fy * r * 1.42, by + fx * r * 1.42);
  ctx.lineTo(nockX, nockY);
  ctx.lineTo(bx + fy * r * 1.42, by - fx * r * 1.42);
  ctx.stroke();
  if (draw > 0.25) { // the arrow on the string
    ctx.strokeStyle = PAL.woodDark;
    ctx.lineWidth = r * 0.07;
    ctx.beginPath();
    ctx.moveTo(nockX, nockY);
    ctx.lineTo(nockX + fx * r * 1.5, nockY + fy * r * 1.5);
    ctx.stroke();
  }
  softOutline(ctx, 1);
}

/** Knight silhouette, but the horse is armoured too — heavier and squarer. */
function drawCataphract(ctx: Ctx, e: Entity, tc: any, moving: boolean, time: number, lunge: number) {
  const r = e.radius;
  const gallop = moving ? Math.sin(e.animPhase * 10) * 1.1 : 0;
  // Barded horse: a blockier body than the Knight's, in steel over team colour.
  ctx.fillStyle = grad(ctx, 0, r * 0.1, 0, r * 0.95, PAL.steel, PAL.steelDark);
  ctx.beginPath();
  ctx.ellipse(0, r * 0.5 + gallop * 0.1, r * 1.05, r * 0.62, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = shade(tc.dark, -0.05); // caparison skirt
  ctx.beginPath();
  ctx.ellipse(0, r * 0.78, r * 0.98, r * 0.34, 0, 0, Math.PI * 2);
  ctx.fill();
  const [fx, fy] = weaponAngleParts(e.facing);
  // Armoured head of the horse, thrust forward.
  ctx.fillStyle = PAL.steel;
  ctx.beginPath();
  ctx.ellipse(fx * r * 0.95, fy * r * 0.95 + r * 0.3, r * 0.34, r * 0.26, e.facing, 0, Math.PI * 2);
  ctx.fill();
  // Rider.
  body(ctx, e, tc.main, tc.dark, { pauldrons: true, chestPlate: true });
  head(ctx, r, { helm: "full", tone: PAL.steel, plume: tc.light });
  // Couched lance.
  const reach = 2.4 + lunge * 0.7;
  ctx.strokeStyle = grad(ctx, 0, 0, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark);
  ctx.lineWidth = r * 0.12;
  ctx.lineCap = "round";
  ctx.beginPath();
  ctx.moveTo(-fx * r * 0.7, -fy * r * 0.7);
  ctx.lineTo(fx * r * reach, fy * r * reach);
  ctx.stroke();
  ctx.fillStyle = PAL.steel;
  ctx.beginPath();
  ctx.moveTo(fx * r * (reach + 0.3), fy * r * (reach + 0.3));
  ctx.lineTo(fx * r * reach - fy * r * 0.22, fy * r * reach + fx * r * 0.22);
  ctx.lineTo(fx * r * reach + fy * r * 0.22, fy * r * reach - fx * r * 0.22);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1);
}

/** Robed, hooded, with a staff whose head kindles as the cast builds. */
function drawBattlemage(ctx: Ctx, e: Entity, tc: any, atkFrac: number, time: number) {
  const r = e.radius;
  body(ctx, e, shade(tc.dark, -0.12), shade(tc.dark, -0.24), { legCol: shade(tc.dark, -0.3) });
  head(ctx, r, { helm: "hood", tone: shade(tc.dark, -0.18) });
  const [fx, fy] = weaponAngleParts(e.facing);
  const charge = Math.max(0, 1 - atkFrac); // brightest just before the cast
  // Staff.
  const topX = -fy * r * 0.62 + fx * r * 0.2;
  const topY = fx * r * 0.62 + fy * r * 0.2;
  ctx.strokeStyle = grad(ctx, topX, topY - r * 1.4, topX, topY + r * 0.6, PAL.woodLight, PAL.woodDark);
  ctx.lineWidth = r * 0.1;
  ctx.lineCap = "round";
  ctx.beginPath();
  ctx.moveTo(topX, topY + r * 0.6);
  ctx.lineTo(topX, topY - r * 1.25);
  ctx.stroke();
  // The ember at its head, breathing and flaring on the cast.
  const glow = 0.35 + charge * 0.65 + Math.sin(time * 4 + e.id) * 0.08;
  const gy = topY - r * 1.35;
  ctx.save();
  ctx.shadowColor = "#ff9a3a";
  ctx.shadowBlur = r * (1.4 + charge * 2.6);
  ctx.fillStyle = grad(ctx, topX, gy - r * 0.3, topX, gy + r * 0.3, "#ffe7a8", "#e8631a");
  ctx.beginPath();
  ctx.arc(topX, gy, r * (0.2 + glow * 0.18), 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
  softOutline(ctx, 1);
}

/** A short fat barrel on a two-wheeled carriage, muzzle flaring as it fires. */
function drawBombard(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  const [fx, fy] = weaponAngleParts(e.facing);
  const recoil = atkFrac > 0.85 ? (atkFrac - 0.85) / 0.15 : 0;
  // Carriage bed + wheels.
  ctx.fillStyle = grad(ctx, 0, -r * 0.3, 0, r * 0.5, PAL.woodLight, PAL.woodDark);
  ctx.beginPath();
  ctx.ellipse(0, r * 0.28, r * 0.9, r * 0.42, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = PAL.woodDark;
  for (const side of [-1, 1]) {
    ctx.beginPath();
    ctx.ellipse(-fy * r * 0.62 * side, fx * r * 0.62 * side + r * 0.34, r * 0.34, r * 0.34, 0, 0, Math.PI * 2);
    ctx.fill();
  }
  // The barrel, kicked back on the shot.
  const bx = -fx * r * 0.5 * recoil;
  const by = -fy * r * 0.5 * recoil - r * 0.15;
  ctx.fillStyle = grad(ctx, bx, by - r * 0.35, bx, by + r * 0.35, "#5a5a62", "#2c2c33");
  ctx.beginPath();
  ctx.ellipse(bx + fx * r * 0.42, by + fy * r * 0.42, r * 0.86, r * 0.36, e.facing, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#6e6e78"; // reinforcing band
  ctx.beginPath();
  ctx.ellipse(bx + fx * r * 0.1, by + fy * r * 0.1, r * 0.28, r * 0.4, e.facing, 0, Math.PI * 2);
  ctx.fill();
  if (recoil > 0.1) { // muzzle flash
    ctx.save();
    ctx.shadowColor = "#ffb03a";
    ctx.shadowBlur = r * 3;
    ctx.fillStyle = "#ffd88a";
    ctx.beginPath();
    ctx.arc(bx + fx * r * 1.45, by + fy * r * 1.45, r * 0.42 * recoil, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }
  softOutline(ctx, 1);
}

function drawArcher(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark, { sash: tc.light });
  head(ctx, r, { helm: "hood", tone: shade(tc.dark, -0.05) });
  const [fx, fy] = weaponAngleParts(e.facing);
  // bow: arc perpendicular to facing; draws back as attack readies
  const draw = atkFrac > 0.7 ? (atkFrac - 0.7) / 0.3 : 0;
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 1.8;
  ctx.beginPath();
  ctx.arc(fx * r * 0.7, fy * r * 0.7, r * 0.85, e.facing - 1.25, e.facing + 1.25);
  ctx.stroke();
  // string
  ctx.strokeStyle = "#ddd6c2";
  ctx.lineWidth = 0.8;
  const ax = fx * r * 0.7 + Math.cos(e.facing - 1.25) * r * 0.85;
  const ay = fy * r * 0.7 + Math.sin(e.facing - 1.25) * r * 0.85;
  const bx = fx * r * 0.7 + Math.cos(e.facing + 1.25) * r * 0.85;
  const by = fy * r * 0.7 + Math.sin(e.facing + 1.25) * r * 0.85;
  const pullX = fx * r * (0.7 - draw * 0.8);
  const pullY = fy * r * (0.7 - draw * 0.8);
  ctx.beginPath();
  ctx.moveTo(ax, ay);
  ctx.lineTo(pullX, pullY);
  ctx.lineTo(bx, by);
  ctx.stroke();
  // quiver
  ctx.fillStyle = PAL.leather;
  ctx.fillRect(-fy * r * 0.7 - 2, fx * r * 0.7 - 2, 4, 6);
}

function drawSkirmisher(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  body(ctx, e, PAL.leather, shade(PAL.leather, -0.25), { sash: tc.main });
  head(ctx, r, { helm: "bare", hair: "#3a2c1c" });
  const [fx, fy] = weaponAngleParts(e.facing);
  // javelin raised overhead when about to throw
  const raise = atkFrac > 0.7 ? (atkFrac - 0.7) / 0.3 : 0;
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = 1.6;
  ctx.beginPath();
  ctx.moveTo(-fx * r * (0.6 + raise * 0.5), -fy * r * (0.6 + raise * 0.5) - raise * 3);
  ctx.lineTo(fx * r * 1.7, fy * r * 1.7 - raise * 3);
  ctx.stroke();
}

interface HorseOpts {
  coat: string;
  scaleX?: number;
  scaleY?: number;
  gallopHz?: number;
  caparison?: string; // team cloth over the horse
  capAlpha?: number;
}
/** A shaded horse oriented along facing, with galloping legs, mane and tail. */
function horse(ctx: Ctx, e: Entity, moving: boolean, opts: HorseOpts) {
  const r = e.radius;
  const sx = opts.scaleX ?? 1.2;
  const sy = opts.scaleY ?? 0.56;
  const gallop = moving ? Math.sin(e.animPhase * (opts.gallopHz ?? 11)) * 1.3 : 0;
  ctx.save();
  ctx.rotate(e.facing);
  // legs (behind the body)
  ctx.strokeStyle = shade(opts.coat, -0.32);
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.13;
  for (const lx of [-r * sx * 0.55, r * sx * 0.5]) {
    for (const d of [1, -1]) {
      ctx.beginPath();
      ctx.moveTo(lx, r * 0.28);
      ctx.lineTo(lx + gallop * 1.6 * d, r * 0.84);
      ctx.stroke();
    }
  }
  // tail
  ctx.strokeStyle = shade(opts.coat, -0.36);
  ctx.lineWidth = r * 0.12;
  ctx.beginPath();
  ctx.moveTo(-r * sx * 0.92, -r * 0.05);
  ctx.quadraticCurveTo(-r * sx * 1.2, r * 0.2 - gallop, -r * sx * 1.05, r * 0.5);
  ctx.stroke();
  // barrel
  ctx.fillStyle = grad(ctx, 0, -r * sy, 0, r * sy, shade(opts.coat, 0.14), shade(opts.coat, -0.18));
  ctx.beginPath();
  ctx.ellipse(0, 0, r * sx, r * sy, 0, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.4);
  // neck + head
  ctx.fillStyle = shade(opts.coat, 0.04);
  ctx.beginPath();
  ctx.ellipse(r * sx * 0.92, -r * 0.12, r * 0.38, r * 0.22, -0.4, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.1);
  // mane
  ctx.strokeStyle = shade(opts.coat, -0.4);
  ctx.lineWidth = r * 0.1;
  ctx.beginPath();
  ctx.moveTo(r * sx * 0.5, -r * 0.3);
  ctx.lineTo(r * sx * 0.95, -r * 0.22);
  ctx.stroke();
  // caparison
  if (opts.caparison) {
    ctx.fillStyle = withAlpha(opts.caparison, opts.capAlpha ?? 0.85);
    ctx.beginPath();
    ctx.ellipse(-r * 0.12, 0, r * sx * 0.62, r * sy * 0.95, 0, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.restore();
}

interface RiderOpts { coat: string; coatDark: string; helm?: HelmKind; tone?: string; plume?: string; }
/** A mounted rider's torso + head, sitting just back of the saddle. */
function rider(ctx: Ctx, r: number, opts: RiderOpts) {
  ctx.fillStyle = grad(ctx, 0, -r * 0.72, 0, -r * 0.1, shade(opts.coat, 0.15), opts.coatDark);
  ctx.beginPath();
  ctx.ellipse(-r * 0.08, -r * 0.4, r * 0.34, r * 0.44, 0, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.3);
  ctx.fillStyle = PAL.skin;
  ctx.beginPath();
  ctx.arc(-r * 0.08, -r * 0.68, r * 0.22, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1);
  if (opts.helm && opts.helm !== "bare") {
    ctx.fillStyle = grad(ctx, 0, -r * 0.92, 0, -r * 0.5, shade(opts.tone ?? PAL.steel, 0.15), shade(opts.tone ?? PAL.steel, -0.15));
    ctx.beginPath();
    ctx.arc(-r * 0.08, -r * 0.7, r * 0.22, Math.PI * 0.95, Math.PI * 2.05);
    ctx.fill();
    softOutline(ctx, 1);
  }
  if (opts.plume) {
    ctx.fillStyle = opts.plume;
    ctx.beginPath();
    ctx.moveTo(-r * 0.08, -r * 0.92);
    ctx.quadraticCurveTo(r * 0.08, -r * 1.24, -r * 0.14, -r * 0.9);
    ctx.fill();
  }
}

function drawKnight(ctx: Ctx, e: Entity, tc: any, moving: boolean, time: number, lunge: number) {
  const r = e.radius;
  const [fx, fy] = weaponAngleParts(e.facing);
  horse(ctx, e, moving, { coat: "#5e4632", scaleX: 1.25, scaleY: 0.62, caparison: tc.main, capAlpha: 0.88 });
  rider(ctx, r, { coat: tc.main, coatDark: tc.dark, helm: "full", tone: PAL.steel, plume: tc.light });
  // couched lance from the shoulder, dipping forward on the charge
  const sx = -r * 0.08 + fx * r * 0.2;
  const sy = -r * 0.4 + fy * r * 0.2;
  const reach = 2.1 + lunge;
  ctx.strokeStyle = grad(ctx, sx, sy, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark);
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.12;
  ctx.beginPath();
  ctx.moveTo(sx - fx * r * 0.5, sy - fy * r * 0.5);
  ctx.lineTo(fx * r * reach, fy * r * reach);
  ctx.stroke();
  ctx.fillStyle = PAL.steel; // lance head
  ctx.beginPath();
  ctx.moveTo(fx * r * (reach + 0.2), fy * r * (reach + 0.2));
  ctx.lineTo(fx * r * reach - fy * r * 0.18, fy * r * reach + fx * r * 0.18);
  ctx.lineTo(fx * r * reach + fy * r * 0.18, fy * r * reach - fx * r * 0.18);
  ctx.closePath();
  ctx.fill();
}

function drawHorseman(ctx: Ctx, e: Entity, tc: any, moving: boolean) {
  const r = e.radius;
  const [fx, fy] = weaponAngleParts(e.facing);
  const atkFrac = e.attackInterval > 0 ? e.attackCooldown / e.attackInterval : 0;
  horse(ctx, e, moving, { coat: "#6b5238", scaleX: 1.18, scaleY: 0.54, gallopHz: 12, caparison: tc.main, capAlpha: 0.6 });
  rider(ctx, r, { coat: tc.main, coatDark: tc.dark, helm: "cap", tone: PAL.leather });
  // recurve bow aimed forward; string draws back as a shot readies
  const bx = -r * 0.08 + fx * r * 0.5;
  const by = -r * 0.42 + fy * r * 0.5;
  const draw = atkFrac > 0.7 ? (atkFrac - 0.7) / 0.3 : 0;
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = r * 0.08;
  ctx.beginPath();
  ctx.arc(bx, by, r * 0.55, e.facing - 1.15, e.facing + 1.15);
  ctx.stroke();
  ctx.strokeStyle = "#ddd6c2";
  ctx.lineWidth = 0.8;
  const ax = bx + Math.cos(e.facing - 1.15) * r * 0.55;
  const ay = by + Math.sin(e.facing - 1.15) * r * 0.55;
  const cx2 = bx + Math.cos(e.facing + 1.15) * r * 0.55;
  const cy2 = by + Math.sin(e.facing + 1.15) * r * 0.55;
  ctx.beginPath();
  ctx.moveTo(ax, ay);
  ctx.lineTo(bx - fx * r * draw * 0.7, by - fy * r * draw * 0.7);
  ctx.lineTo(cx2, cy2);
  ctx.stroke();
}

function drawRaider(ctx: Ctx, e: Entity, tc: any, moving: boolean, lunge: number) {
  const r = e.radius;
  horse(ctx, e, moving, { coat: "#574033", scaleX: 1.2, scaleY: 0.5, gallopHz: 13 });
  rider(ctx, r, { coat: tc.main, coatDark: tc.dark, helm: "cap", tone: PAL.steelDark });
  // curved sabre sweeping through its strike
  const [dx, dy, s] = swungDir(e, 1.3);
  const ox = -r * 0.08;
  const oy = -r * 0.4;
  ctx.strokeStyle = grad(ctx, ox, oy, ox + dx * r * 1.4, oy + dy * r * 1.4, "#f2f4f8", PAL.steelDark);
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.12;
  ctx.beginPath();
  ctx.arc(ox + dx * r * (0.7 + lunge * 0.3), oy + dy * r * (0.7 + lunge * 0.3), r * 0.6, e.facing - 0.7 + s * 0.4, e.facing + 0.7 + s * 0.4);
  ctx.stroke();
}

function drawCrossbow(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark);
  head(ctx, r, { helm: "open", tone: PAL.steelDark }); // kettle helm
  const [fx, fy] = weaponAngleParts(e.facing);
  const px = -fy;
  const py = fx;
  // horizontal stock pointed forward
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 2.4;
  ctx.beginPath();
  ctx.moveTo(fx * r * 0.2, fy * r * 0.2);
  ctx.lineTo(fx * r * 1.5, fy * r * 1.5);
  ctx.stroke();
  // steel prod across the front
  const tipX = fx * r * 1.5;
  const tipY = fy * r * 1.5;
  ctx.strokeStyle = PAL.steel;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(tipX + px * r * 0.7, tipY + py * r * 0.7);
  ctx.lineTo(tipX - px * r * 0.7, tipY - py * r * 0.7);
  ctx.stroke();
  // loaded bolt glints just before a shot
  if (atkFrac > 0.6) {
    ctx.strokeStyle = "#e8e2cf";
    ctx.lineWidth = 1.4;
    ctx.beginPath();
    ctx.moveTo(fx * r * 0.4, fy * r * 0.4);
    ctx.lineTo(tipX, tipY);
    ctx.stroke();
  }
}

// A shaded, spoked siege wheel.
function siegeWheel(ctx: Ctx, wx: number, wy: number, wr: number) {
  ctx.fillStyle = grad(ctx, wx - wr, wy, wx + wr, wy, "#4a3826", "#241910");
  ctx.beginPath();
  ctx.arc(wx, wy, wr, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.2);
  ctx.strokeStyle = "#1c130a";
  ctx.lineWidth = 1;
  for (let i = 0; i < 4; i++) {
    const a = (i * Math.PI) / 4;
    ctx.beginPath();
    ctx.moveTo(wx - Math.cos(a) * wr, wy - Math.sin(a) * wr);
    ctx.lineTo(wx + Math.cos(a) * wr, wy + Math.sin(a) * wr);
    ctx.stroke();
  }
  ctx.fillStyle = "#5a4632";
  ctx.beginPath();
  ctx.arc(wx, wy, wr * 0.26, 0, Math.PI * 2);
  ctx.fill();
}

function drawTrebuchet(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  ctx.save();
  ctx.rotate(e.facing);
  siegeWheel(ctx, -r * 0.6, r * 0.55, r * 0.28);
  siegeWheel(ctx, r * 0.6, r * 0.55, r * 0.28);
  // heavy timber base
  ctx.fillStyle = grad(ctx, 0, -r * 0.6, 0, r * 0.6, shade(PAL.wood, 0.1), PAL.woodDark);
  ctx.beginPath();
  ctx.roundRect(-r * 0.85, -r * 0.55, r * 1.7, r * 1.1, 3);
  ctx.fill();
  softOutline(ctx, 1.4);
  // A-frame uprights
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = r * 0.12;
  ctx.lineCap = "round";
  for (const s of [-1, 1]) {
    ctx.beginPath();
    ctx.moveTo(s * r * 0.5, r * 0.4);
    ctx.lineTo(0, -r * 1.1);
    ctx.stroke();
  }
  // throwing beam (counterweight short end, sling long end)
  const beam = atkFrac > 0.85 ? 1.1 : atkFrac > 0 ? -0.7 + (1 - atkFrac) * 1.8 : 1.1;
  const px = Math.cos(beam);
  const py = Math.sin(beam);
  ctx.strokeStyle = grad(ctx, px * r * 1.7, -r * 1.1 - py * r * 1.7, -px * r * 0.7, -r * 1.1 + py * r * 0.7, PAL.woodLight, PAL.woodDark);
  ctx.lineWidth = r * 0.14;
  ctx.beginPath();
  ctx.moveTo(px * r * 1.7, -r * 1.1 - py * r * 1.7);
  ctx.lineTo(-px * r * 0.7, -r * 1.1 + py * r * 0.7);
  ctx.stroke();
  ctx.fillStyle = "#2c2c30"; // counterweight
  ctx.beginPath();
  ctx.arc(-px * r * 0.7, -r * 1.1 + py * r * 0.7, r * 0.22, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.2);
  ctx.fillStyle = PAL.leather; // sling pouch
  ctx.beginPath();
  ctx.arc(px * r * 1.7, -r * 1.1 - py * r * 1.7, r * 0.2, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = tc.main;
  ctx.fillRect(-r * 0.85, -r * 0.05, r * 0.32, r * 0.18);
  ctx.restore();
}

function drawCatapult(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  ctx.save();
  ctx.rotate(e.facing);
  for (const [wx, wy] of [[-r * 0.7, -r * 0.62], [-r * 0.7, r * 0.62], [r * 0.7, -r * 0.62], [r * 0.7, r * 0.62]]) {
    siegeWheel(ctx, wx, wy, r * 0.26);
  }
  // frame
  ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.55, shade(PAL.wood, 0.1), PAL.woodDark);
  ctx.beginPath();
  ctx.roundRect(-r * 0.9, -r * 0.5, r * 1.8, r, 3);
  ctx.fill();
  softOutline(ctx, 1.4);
  // throwing arm: cocked back to reload, snaps forward on fire
  const armAngle = atkFrac > 0.85 ? -0.4 : atkFrac > 0 ? 0.9 - (0.85 - atkFrac) : -0.4;
  ctx.strokeStyle = grad(ctx, -r * 0.4, 0, -r * 0.4 + Math.cos(armAngle) * r * 1.2, -Math.sin(armAngle) * r * 1.2, PAL.woodLight, PAL.woodDark);
  ctx.lineWidth = r * 0.14;
  ctx.lineCap = "round";
  ctx.beginPath();
  ctx.moveTo(-r * 0.4, 0);
  ctx.lineTo(-r * 0.4 + Math.cos(armAngle) * r * 1.2, -Math.sin(armAngle) * r * 1.2);
  ctx.stroke();
  ctx.fillStyle = PAL.leather; // cup
  ctx.beginPath();
  ctx.arc(-r * 0.4 + Math.cos(armAngle) * r * 1.2, -Math.sin(armAngle) * r * 1.2, r * 0.2, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.1);
  ctx.fillStyle = tc.main;
  ctx.fillRect(r * 0.7, -r * 0.08, r * 0.4, r * 0.18);
  ctx.restore();
}

function drawRam(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  ctx.save();
  ctx.rotate(e.facing);
  siegeWheel(ctx, -r * 0.5, r * 0.7, r * 0.22);
  siegeWheel(ctx, r * 0.5, r * 0.7, r * 0.22);
  // swinging log with an iron head (drawn under the roof)
  const swing = atkFrac > 0.8 ? (atkFrac - 0.8) / 0.2 : 0;
  ctx.strokeStyle = grad(ctx, -r * 0.6, 0, r * 1.3, 0, shade(PAL.trunk, 0.12), shade(PAL.trunk, -0.2));
  ctx.lineWidth = r * 0.26;
  ctx.lineCap = "round";
  ctx.beginPath();
  ctx.moveTo(-r * 0.5, 0);
  ctx.lineTo(r * (1.0 + swing * 0.5), 0);
  ctx.stroke();
  ctx.fillStyle = grad(ctx, r * (1.0 + swing * 0.5), -r * 0.2, r * (1.2 + swing * 0.5), r * 0.2, PAL.steel, PAL.steelDark);
  ctx.beginPath();
  ctx.moveTo(r * (1.0 + swing * 0.5), -r * 0.2);
  ctx.lineTo(r * (1.35 + swing * 0.5), 0);
  ctx.lineTo(r * (1.0 + swing * 0.5), r * 0.2);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1.2);
  // protective gabled roof
  ctx.fillStyle = grad(ctx, 0, -r * 0.75, 0, r * 0.75, shade(PAL.wood, 0.08), PAL.woodDark);
  ctx.beginPath();
  ctx.moveTo(-r, r * 0.55);
  ctx.lineTo(-r, -r * 0.5);
  ctx.lineTo(-r * 0.6, -r * 0.78);
  ctx.lineTo(r * 0.6, -r * 0.78);
  ctx.lineTo(r, -r * 0.5);
  ctx.lineTo(r, r * 0.55);
  ctx.lineTo(r * 0.6, r * 0.75);
  ctx.lineTo(-r * 0.6, r * 0.75);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1.6);
  ctx.strokeStyle = "rgba(20,16,10,0.3)"; // planks
  ctx.lineWidth = 1;
  for (let i = -2; i <= 2; i++) {
    ctx.beginPath();
    ctx.moveTo(i * r * 0.35, -r * 0.6);
    ctx.lineTo(i * r * 0.35, r * 0.55);
    ctx.stroke();
  }
  ctx.fillStyle = tc.main; // team ridge
  ctx.fillRect(-r * 0.6, -r * 0.78, r * 1.2, 3);
  ctx.restore();
}

function drawHero(ctx: Ctx, e: Entity, tc: any, lunge: number, time: number) {
  const r = e.radius;
  const [fx, fy] = weaponAngleParts(e.facing);
  // flowing team cape behind
  ctx.fillStyle = shade(tc.dark, -0.08);
  ctx.beginPath();
  ctx.moveTo(-r * 0.5, -r * 0.2);
  ctx.quadraticCurveTo(-r * 1.1, r * 0.6 + Math.sin(time * 4) * 1.5, -r * 0.2, r * 1.0);
  ctx.quadraticCurveTo(r * 0.2, r * 0.5, r * 0.5, -r * 0.2);
  ctx.fill();
  // kite shield on the off-hand (behind the torso)
  const shx = -fy * r * 0.82;
  const shy = fx * r * 0.82;
  ctx.fillStyle = grad(ctx, shx, shy - r * 0.5, shx, shy + r * 0.5, tc.main, tc.dark);
  ctx.beginPath();
  ctx.ellipse(shx, shy, r * 0.4, r * 0.58, e.facing, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.4);
  ctx.fillStyle = "#ffd24a";
  ctx.beginPath();
  ctx.arc(shx, shy, r * 0.14, 0, Math.PI * 2);
  ctx.fill();
  body(ctx, e, tc.main, tc.dark, { pauldrons: true, chestPlate: true });
  head(ctx, r, { helm: "full", tone: PAL.steel, plume: tc.light });
  // gleaming greatsword sweeping through its strike
  const [dx, dy] = swungDir(e, 1.1);
  blade(ctx, r, dx, dy, 1.9 + lunge * 0.4, "#ffd24a");
}

function drawMonk(ctx: Ctx, e: Entity, tc: any, time: number) {
  const r = e.radius;
  body(ctx, e, "#cfc4a8", "#a89a78");
  // team stole down the front of the robe
  ctx.fillStyle = tc.main;
  ctx.fillRect(-r * 0.08, -r * 0.5, r * 0.16, r * 0.95);
  head(ctx, r, { helm: "hood", tone: "#a89a78" });
  // staff with a glowing tip
  const [fx, fy] = weaponAngleParts(e.facing);
  ctx.strokeStyle = grad(ctx, fx * r * 0.5, fy * r * 0.5 + r * 0.5, fx * r * 0.9, fy * r * 0.9 - r * 1.1, PAL.woodLight, PAL.woodDark);
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.09;
  ctx.beginPath();
  ctx.moveTo(fx * r * 0.5, fy * r * 0.5 + r * 0.5);
  ctx.lineTo(fx * r * 0.9, fy * r * 0.9 - r * 1.1);
  ctx.stroke();
  const glow = 0.6 + Math.sin(time * 4 + e.id) * 0.3;
  ctx.fillStyle = withAlpha(PAL.heal, glow);
  ctx.beginPath();
  ctx.arc(fx * r * 0.9, fy * r * 0.9 - r * 1.2, r * 0.18, 0, Math.PI * 2);
  ctx.fill();
}

// Two-Handed Swordsman: heavy plate, full helm, a huge greatsword gripped in
// both gauntlets — no shield (that's what tells it apart from the Militia).
function drawTwohand(ctx: Ctx, e: Entity, tc: any, lunge: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark, { pauldrons: true, chestPlate: true });
  head(ctx, r, { helm: "full", tone: PAL.steel });
  const [dx, dy, s] = swungDir(e, 1.4); // wide two-handed sweep
  // huge greatsword, both gauntlets on the grip
  ctx.fillStyle = PAL.steelDark;
  for (const d of [0.05, -0.3]) {
    ctx.beginPath();
    ctx.arc(dx * r * d, dy * r * d, r * 0.15, 0, Math.PI * 2);
    ctx.fill();
  }
  blade(ctx, r, dx, dy, 2.05 + lunge + s * 0.2, shade(tc.dark, -0.1), -0.5);
}

// Pikeman: a far longer haft than the Spearman, a small team pennant flying at
// the grip, and a slim leaf-blade — the reach is the read.
function drawPikeman(ctx: Ctx, e: Entity, tc: any, lunge: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark);
  head(ctx, r, { helm: "open", tone: PAL.steelDark });
  const [fx, fy] = weaponAngleParts(e.facing);
  const reach = 3.0 + lunge * 0.7 + strikeT(e) * 0.4; // pike (Spearman is ~2.2)
  ctx.strokeStyle = grad(ctx, -fx * r, -fy * r, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark);
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.09;
  ctx.beginPath();
  ctx.moveTo(-fx * r * 1.0, -fy * r * 1.0);
  ctx.lineTo(fx * r * reach, fy * r * reach);
  ctx.stroke();
  // pennant near the grip
  const bx = fx * r * 0.55;
  const by = fy * r * 0.55;
  ctx.fillStyle = tc.main;
  ctx.beginPath();
  ctx.moveTo(bx, by);
  ctx.lineTo(bx + fx * r * 0.55 - fy * r * 0.42, by + fy * r * 0.55 + fx * r * 0.42);
  ctx.lineTo(bx + fx * r * 0.55, by + fy * r * 0.55);
  ctx.closePath();
  ctx.fill();
  // long leaf head
  const tipX = fx * r * reach;
  const tipY = fy * r * reach;
  ctx.fillStyle = PAL.steel;
  ctx.beginPath();
  ctx.moveTo(tipX + fx * r * 0.5, tipY + fy * r * 0.5);
  ctx.lineTo(tipX - fy * r * 0.18, tipY + fx * r * 0.18);
  ctx.lineTo(tipX + fy * r * 0.18, tipY - fx * r * 0.18);
  ctx.closePath();
  ctx.fill();
}

// Scout: a light, pale, fast pony and an unarmed rider carrying a tall pennant
// mast — reads as a recon unit, not the sabre-swinging Raider.
function drawScout(ctx: Ctx, e: Entity, tc: any, moving: boolean) {
  const r = e.radius;
  horse(ctx, e, moving, { coat: "#9a8260", scaleX: 1.15, scaleY: 0.46, gallopHz: 15 });
  rider(ctx, r, { coat: tc.main, coatDark: tc.dark, helm: "bare" });
  // tall scouting pennant (fixed mast, not a weapon)
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = r * 0.06;
  ctx.beginPath();
  ctx.moveTo(r * 0.15, -r * 0.3);
  ctx.lineTo(r * 0.15, -r * 1.5);
  ctx.stroke();
  ctx.fillStyle = tc.light;
  ctx.beginPath();
  ctx.moveTo(r * 0.15, -r * 1.5);
  ctx.lineTo(r * 0.78, -r * 1.32);
  ctx.lineTo(r * 0.15, -r * 1.12);
  ctx.closePath();
  ctx.fill();
  softOutline(ctx, 1);
}

// Javelin Thrower: a soldier in team colours with a back-fan of spare javelins,
// winding up an overhand throw — distinct from the leather-clad Skirmisher.
function drawJavelin(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  // bundle of spare javelins fanned across the back (behind the body)
  const [fx, fy] = weaponAngleParts(e.facing);
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.07;
  for (const o of [-0.28, 0, 0.28]) {
    const ang = e.facing + Math.PI + o;
    ctx.beginPath();
    ctx.moveTo(Math.cos(ang) * r * 0.2, Math.sin(ang) * r * 0.2);
    ctx.lineTo(Math.cos(ang) * r * 1.2, Math.sin(ang) * r * 1.2);
    ctx.stroke();
  }
  body(ctx, e, tc.main, tc.dark, { sash: tc.light });
  head(ctx, r, { helm: "cap", tone: PAL.leather });
  // the throwing javelin, cocked overhead then hurled forward
  const raise = atkFrac > 0.7 ? (atkFrac - 0.7) / 0.3 : 0;
  const back = 0.7 + raise * 0.7;
  ctx.strokeStyle = grad(ctx, -fx * r * back, -fy * r * back, fx * r * 1.9, fy * r * 1.9, PAL.woodLight, PAL.woodDark);
  ctx.lineWidth = r * 0.08;
  ctx.beginPath();
  ctx.moveTo(-fx * r * back, -fy * r * back - raise * 4);
  ctx.lineTo(fx * r * 1.9, fy * r * 1.9 - raise * 4);
  ctx.stroke();
  ctx.fillStyle = PAL.steel;
  ctx.beginPath();
  ctx.arc(fx * r * 1.9, fy * r * 1.9 - raise * 4, r * 0.12, 0, Math.PI * 2);
  ctx.fill();
}

// Hand Cannoneer: a long iron barrel on a wooden stock, with a muzzle flash and
// drifting smoke when it fires — gunpowder, not the Crossbow's steel prod.
function drawHandcannon(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  body(ctx, e, tc.main, tc.dark);
  head(ctx, r, { helm: "open", tone: PAL.steelDark });
  const [fx, fy] = weaponAngleParts(e.facing);
  // wooden stock
  ctx.strokeStyle = grad(ctx, -fx * r * 0.6, -fy * r * 0.6, fx * r * 0.4, fy * r * 0.4, PAL.woodLight, PAL.woodDark);
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.12;
  ctx.beginPath();
  ctx.moveTo(-fx * r * 0.6, -fy * r * 0.6);
  ctx.lineTo(fx * r * 0.4, fy * r * 0.4);
  ctx.stroke();
  // iron barrel
  ctx.strokeStyle = grad(ctx, 0, 0, fx * r * 1.7, fy * r * 1.7, "#54585e", "#2a2d31");
  ctx.lineWidth = r * 0.1;
  ctx.beginPath();
  ctx.moveTo(-fx * r * 0.3, -fy * r * 0.3);
  ctx.lineTo(fx * r * 1.7, fy * r * 1.7);
  ctx.stroke();
  const mx = fx * r * 1.7;
  const my = fy * r * 1.7;
  if (atkFrac > 0.82) {
    const f = (atkFrac - 0.82) / 0.18;
    ctx.fillStyle = withAlpha("#fff0b0", f);
    ctx.beginPath();
    ctx.arc(mx, my, r * 0.5 * f + 1.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = withAlpha("#ffae3a", f * 0.8);
    ctx.beginPath();
    ctx.arc(mx + fx * 4, my + fy * 4, r * 0.32 * f, 0, Math.PI * 2);
    ctx.fill();
  } else if (atkFrac > 0.4) {
    ctx.fillStyle = withAlpha("#cfcabd", 0.35 * (atkFrac - 0.4));
    ctx.beginPath();
    ctx.arc(mx + fx * 5, my + fy * 5, r * 0.4, 0, Math.PI * 2);
    ctx.fill();
  }
}

// King: a robed, bearded monarch under a big jewelled crown, bearing an orbed
// sceptre rather than a war blade — clearly royalty, not the Champion hero.
function drawKing(ctx: Ctx, e: Entity, tc: any, lunge: number, time: number) {
  const r = e.radius;
  // long royal robe behind
  ctx.fillStyle = grad(ctx, 0, -r * 0.2, 0, r * 1.2, shade(tc.main, -0.05), shade(tc.dark, -0.1));
  ctx.beginPath();
  ctx.moveTo(-r * 0.6, -r * 0.1);
  ctx.quadraticCurveTo(-r * 1.2, r * 0.9 + Math.sin(time * 3) * 1.5, 0, r * 1.2);
  ctx.quadraticCurveTo(r * 1.2, r * 0.9 - Math.sin(time * 3) * 1.5, r * 0.6, -r * 0.1);
  ctx.fill();
  body(ctx, e, tc.main, tc.dark);
  // ermine collar + gold medallion
  ctx.fillStyle = "#f0ece0";
  ctx.beginPath();
  ctx.arc(0, r * 0.2, r * 0.5, 0.2, Math.PI - 0.2);
  ctx.fill();
  ctx.fillStyle = "#ffd24a";
  ctx.beginPath();
  ctx.arc(0, 0, r * 0.2, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1.2);
  head(ctx, r, { helm: "bare", hair: "#d8d2c4" });
  // grey royal beard
  ctx.fillStyle = "#e2ddd0";
  ctx.beginPath();
  ctx.arc(0, -r * 0.66, r * 0.22, 0.15, Math.PI - 0.15);
  ctx.fill();
  // big jewelled crown
  ctx.fillStyle = grad(ctx, 0, -r * 1.1, 0, -r * 0.78, "#ffe488", "#e0a830");
  ctx.fillRect(-r * 0.5, -r * 1.06, r * 1.0, r * 0.18);
  for (let i = -2; i <= 2; i++) {
    ctx.beginPath();
    ctx.moveTo(i * r * 0.24 - r * 0.1, -r * 1.06);
    ctx.lineTo(i * r * 0.24, -r * 1.36);
    ctx.lineTo(i * r * 0.24 + r * 0.1, -r * 1.06);
    ctx.fill();
  }
  ctx.fillStyle = "#d8403a"; // jewels
  for (let i = -1; i <= 1; i++) {
    ctx.beginPath();
    ctx.arc(i * r * 0.3, -r * 0.97, r * 0.06, 0, Math.PI * 2);
    ctx.fill();
  }
  // orbed golden sceptre
  const [fx, fy] = weaponAngleParts(e.facing);
  ctx.strokeStyle = grad(ctx, fx * r * 0.3, fy * r * 0.3, fx * r * 1.2, fy * r * 1.2, "#ffe488", "#c8901e");
  ctx.lineCap = "round";
  ctx.lineWidth = r * 0.1;
  ctx.beginPath();
  ctx.moveTo(fx * r * 0.3, fy * r * 0.3);
  ctx.lineTo(fx * r * (1.1 + lunge * 0.4), fy * r * (1.1 + lunge * 0.4));
  ctx.stroke();
  ctx.fillStyle = "#ffe07a";
  ctx.beginPath();
  ctx.arc(fx * r * (1.2 + lunge * 0.4), fy * r * (1.2 + lunge * 0.4), r * 0.16, 0, Math.PI * 2);
  ctx.fill();
  softOutline(ctx, 1);
}

// ----------------------------------------------------------------- projectiles --

export function drawProjectile(ctx: Ctx, e: Entity) {
  const t = Math.min(1, e.projElapsed / e.projDuration);
  // Ballistic arc height (visual only).
  const arc = Math.sin(t * Math.PI) * (e.type === "rock" ? 38 : 14);
  const x = e.x;
  const y = e.y - arc;
  if (e.type === "rock") {
    shadow(ctx, e.x, e.y + 3, 5, 2.4, 0.3);
    ctx.fillStyle = "#6e675c";
    ctx.beginPath();
    ctx.arc(x, y, 4.6, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#867f72";
    ctx.beginPath();
    ctx.arc(x - 1.2, y - 1.2, 2.2, 0, Math.PI * 2);
    ctx.fill();
  } else {
    shadow(ctx, e.x, e.y + 2, 3, 1.2, 0.22);
    ctx.strokeStyle = "#d9cfb4";
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    ctx.moveTo(x - Math.cos(e.facing) * 6, y - Math.sin(e.facing) * 6);
    ctx.lineTo(x + Math.cos(e.facing) * 6, y + Math.sin(e.facing) * 6);
    ctx.stroke();
    ctx.fillStyle = PAL.steel;
    ctx.beginPath();
    ctx.arc(x + Math.cos(e.facing) * 6, y + Math.sin(e.facing) * 6, 1.6, 0, Math.PI * 2);
    ctx.fill();
  }
}

// -------------------------------------------------------------- decorations --

export function drawSelectionRing(ctx: Ctx, e: Entity, isPlayer: boolean) {
  const col = isPlayer ? "#7df27d" : "#f2e87d";
  if (e.kind === Kind.Building) {
    const half = e.radius + 4;
    ctx.strokeStyle = withAlpha(col, 0.9);
    ctx.lineWidth = 2;
    const c = 9;
    for (const [px, py] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
      ctx.beginPath();
      ctx.moveTo(e.x + px * half - px * c, e.y + py * half);
      ctx.lineTo(e.x + px * half, e.y + py * half);
      ctx.lineTo(e.x + px * half, e.y + py * half - py * c);
      ctx.stroke();
    }
  } else {
    ctx.strokeStyle = withAlpha(col, 0.9);
    ctx.lineWidth = 1.8;
    ctx.beginPath();
    ctx.ellipse(e.x, e.y + e.radius * 0.45, e.radius + 3.5, (e.radius + 3.5) * 0.5, 0, 0, Math.PI * 2);
    ctx.stroke();
  }
}

export function drawHealthBar(ctx: Ctx, e: Entity) {
  const frac = Math.max(0, e.hp / e.maxHp);
  const w = e.kind === Kind.Building ? Math.max(30, e.radius * 1.4) : 20;
  const x = e.x - w / 2;
  const y = e.y - e.radius - (e.kind === Kind.Building ? e.radius * 0.6 + 8 : 14);
  ctx.fillStyle = "rgba(10, 10, 8, 0.7)";
  ctx.fillRect(x - 1, y - 1, w + 2, 5);
  ctx.fillStyle = frac > 0.6 ? PAL.uiGood : frac > 0.3 ? "#e8c33a" : PAL.uiBad;
  ctx.fillRect(x, y, w * frac, 3);
}
