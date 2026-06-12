// Procedural entity art. Everything is drawn in world space (the renderer
// applies the camera transform). Style: soft top-down-three-quarter "toy
// soldier" look — chunky silhouettes, team-colored cloth, warm shadows.

import { BuildState, Entity, Kind, Team } from "../sim/types";
import { BUILDINGS } from "../content/buildings";
import { PAL, shade, teamColor, withAlpha } from "./palette";
import { rarityByIndex } from "../meta/rarity";
import { TILE } from "../content/balance";
import { isNight } from "../content/daynight";
import { ABILITIES } from "../content/abilities";

type Ctx = CanvasRenderingContext2D;

// The renderer pushes the live day-cycle phase here each frame so world-space
// art (watchfire glow, etc.) can react to night without threading it through
// every draw signature. Derived from deterministic sim time upstream.
let gDayPhase = 0;
export function setDrawDayPhase(p: number) {
  gDayPhase = p;
}

function shadow(ctx: Ctx, x: number, y: number, rx: number, ry: number, alpha = 0.25) {
  ctx.fillStyle = `rgba(20, 24, 12, ${alpha})`;
  ctx.beginPath();
  ctx.ellipse(x, y, rx, ry, 0, 0, Math.PI * 2);
  ctx.fill();
}

// ---------------------------------------------------------------- resources --

export function drawResource(ctx: Ctx, e: Entity, time: number) {
  const frac = Math.max(0.25, Math.min(1, e.amount / 125));
  const seed = (e.id * 2654435761) >>> 0;
  const v = (n: number) => ((seed >> (n * 3)) % 100) / 100; // cheap per-entity variation

  if (e.type === "tree") {
    const sway = Math.sin(time * 0.8 + e.id) * 0.8;
    shadow(ctx, e.x + 3, e.y + 5, 11 * frac, 5 * frac);
    // trunk
    ctx.fillStyle = PAL.trunk;
    ctx.fillRect(e.x - 2, e.y - 4, 4, 9);
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
    // highlight
    ctx.fillStyle = withAlpha("#dff0c0", 0.25);
    ctx.beginPath();
    ctx.arc(e.x - 3 + sway * 0.4, e.y - 12, 3.5 * frac, 0, Math.PI * 2);
    ctx.fill();
  } else if (e.type === "gold_mine") {
    shadow(ctx, e.x + 2, e.y + 6, 13 * frac + 2, 5);
    // rock pile
    ctx.fillStyle = PAL.goldRock;
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
  const tc = teamColor(e.team);

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

  switch (e.type) {
    case "town_center": drawTownCenter(ctx, e, half, tc, time); break;
    case "house": drawHouse(ctx, e, half, tc); break;
    case "mill": drawMill(ctx, e, half, tc, time); break;
    case "lumber_camp": drawLumberCamp(ctx, e, half); break;
    case "mining_camp": drawMiningCamp(ctx, e, half); break;
    case "farm": drawFarm(ctx, e, half); break;
    case "barracks": drawBarracks(ctx, e, half, tc); break;
    case "archery_range": drawArcheryRange(ctx, e, half, tc); break;
    case "stable": drawStable(ctx, e, half, tc); break;
    case "blacksmith": drawBlacksmith(ctx, e, half, time); break;
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

function drawTownCenter(ctx: Ctx, e: Entity, half: number, tc: any, time: number) {
  // stone base
  ctx.fillStyle = PAL.stone;
  ctx.fillRect(e.x - half + 3, e.y - half * 0.2, half * 2 - 6, half * 1.1);
  ctx.fillStyle = PAL.stoneDark;
  ctx.fillRect(e.x - half + 3, e.y + half * 0.62, half * 2 - 6, half * 0.28);
  // timber upper
  ctx.fillStyle = PAL.wood;
  ctx.fillRect(e.x - half + 8, e.y - half * 0.62, half * 2 - 16, half * 0.5);
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 2;
  for (let i = 0; i < 4; i++) {
    const lx = e.x - half + 10 + ((half * 2 - 20) * i) / 3;
    ctx.beginPath();
    ctx.moveTo(lx, e.y - half * 0.62);
    ctx.lineTo(lx, e.y - half * 0.12);
    ctx.stroke();
  }
  // grand gabled roof
  roofGable(ctx, e.x, e.y - half * 0.55, half * 2.04, half * 0.9, tc.dark);
  roofGable(ctx, e.x, e.y - half * 0.62, half * 1.5, half * 0.62, tc.main);
  // door
  ctx.fillStyle = PAL.woodDark;
  ctx.beginPath();
  ctx.arc(e.x, e.y + half * 0.55, half * 0.22, Math.PI, 0);
  ctx.fill();
  ctx.fillRect(e.x - half * 0.22, e.y + half * 0.55, half * 0.44, half * 0.34);
  banner(ctx, e.x + half * 0.7, e.y - half * 0.55, tc, time, e.id);
}

function drawHouse(ctx: Ctx, e: Entity, half: number, tc: any) {
  ctx.fillStyle = PAL.wood;
  ctx.fillRect(e.x - half + 4, e.y - half * 0.25, half * 2 - 8, half * 1.05);
  roofGable(ctx, e.x, e.y - half * 0.4, half * 1.9, half * 0.85, PAL.thatch);
  ctx.fillStyle = PAL.thatchDark;
  ctx.fillRect(e.x - half * 0.95, e.y - half * 0.02, half * 1.9, 3);
  ctx.fillStyle = PAL.woodDark;
  ctx.fillRect(e.x - half * 0.16, e.y + half * 0.3, half * 0.32, half * 0.5);
  // team trim on door frame
  ctx.fillStyle = tc.main;
  ctx.fillRect(e.x - half * 0.2, e.y + half * 0.26, half * 0.4, 2.5);
}

function drawMill(ctx: Ctx, e: Entity, half: number, tc: any, time: number) {
  ctx.fillStyle = PAL.stone;
  ctx.fillRect(e.x - half * 0.6, e.y - half * 0.3, half * 1.2, half * 1.15);
  roofGable(ctx, e.x, e.y - half * 0.5, half * 1.3, half * 0.7, tc.main);
  // rotating blades
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
  // lean-to
  ctx.fillStyle = PAL.wood;
  ctx.fillRect(e.x - half + 4, e.y - half * 0.4, half, half * 1.2);
  roofGable(ctx, e.x - half * 0.5 + 4, e.y - half * 0.5, half * 1.1, half * 0.5, PAL.thatchDark);
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
  ctx.fillStyle = PAL.stoneDark;
  ctx.fillRect(e.x - half + 4, e.y - half * 0.3, half * 2 - 8, half * 1.05);
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
  ctx.fillStyle = PAL.stone;
  ctx.fillRect(e.x - half + 3, e.y - half * 0.35, half * 2 - 6, half * 1.2);
  roofGable(ctx, e.x, e.y - half * 0.52, half * 2, half * 0.78, PAL.roofSlate);
  // crossed-sword shield emblem
  ctx.fillStyle = tc.main;
  ctx.beginPath();
  ctx.moveTo(e.x, e.y - half * 0.05);
  ctx.lineTo(e.x + half * 0.22, e.y + half * 0.12);
  ctx.lineTo(e.x + half * 0.16, e.y + half * 0.42);
  ctx.lineTo(e.x, e.y + half * 0.55);
  ctx.lineTo(e.x - half * 0.16, e.y + half * 0.42);
  ctx.lineTo(e.x - half * 0.22, e.y + half * 0.12);
  ctx.closePath();
  ctx.fill();
  ctx.strokeStyle = PAL.steel;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(e.x - half * 0.12, e.y + half * 0.1);
  ctx.lineTo(e.x + half * 0.12, e.y + half * 0.42);
  ctx.moveTo(e.x + half * 0.12, e.y + half * 0.1);
  ctx.lineTo(e.x - half * 0.12, e.y + half * 0.42);
  ctx.stroke();
}

function drawArcheryRange(ctx: Ctx, e: Entity, half: number, tc: any) {
  ctx.fillStyle = PAL.wood;
  ctx.fillRect(e.x - half + 3, e.y - half * 0.3, half * 1.1, half * 1.1);
  roofGable(ctx, e.x - half * 0.45 + 3, e.y - half * 0.45, half * 1.2, half * 0.6, tc.main);
  // target butt
  const tx = e.x + half * 0.55;
  const ty = e.y + half * 0.25;
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
  ctx.fillStyle = PAL.wood;
  ctx.fillRect(e.x - half + 3, e.y - half * 0.3, half * 2 - 6, half * 1.1);
  roofGable(ctx, e.x, e.y - half * 0.5, half * 2, half * 0.7, PAL.thatch);
  // stall openings
  ctx.fillStyle = PAL.woodDark;
  for (let i = -1; i <= 1; i++) {
    ctx.beginPath();
    ctx.arc(e.x + i * half * 0.55, e.y + half * 0.45, half * 0.2, Math.PI, 0);
    ctx.fill();
    ctx.fillRect(e.x + i * half * 0.55 - half * 0.2, e.y + half * 0.45, half * 0.4, half * 0.3);
  }
  // horseshoe emblem
  ctx.strokeStyle = tc.light;
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.arc(e.x, e.y - half * 0.05, half * 0.16, Math.PI * 0.15, Math.PI * 0.85, true);
  ctx.stroke();
}

function drawBlacksmith(ctx: Ctx, e: Entity, half: number, time: number) {
  ctx.fillStyle = PAL.stoneDark;
  ctx.fillRect(e.x - half + 3, e.y - half * 0.3, half * 2 - 6, half * 1.1);
  roofGable(ctx, e.x, e.y - half * 0.48, half * 2, half * 0.66, PAL.roofSlate);
  // glowing forge window
  const glow = 0.6 + Math.sin(time * 5 + e.id) * 0.25;
  ctx.fillStyle = withAlpha(PAL.fire, glow);
  ctx.fillRect(e.x - half * 0.25, e.y + half * 0.1, half * 0.5, half * 0.4);
  ctx.fillStyle = withAlpha(PAL.fireBright, glow * 0.7);
  ctx.fillRect(e.x - half * 0.15, e.y + half * 0.18, half * 0.3, half * 0.24);
  // chimney
  ctx.fillStyle = PAL.stone;
  ctx.fillRect(e.x + half * 0.45, e.y - half * 0.85, half * 0.22, half * 0.45);
  // anvil silhouette
  ctx.fillStyle = "#3c4148";
  ctx.fillRect(e.x - half * 0.7, e.y + half * 0.35, half * 0.32, half * 0.12);
  ctx.fillRect(e.x - half * 0.62, e.y + half * 0.24, half * 0.16, half * 0.12);
}

function drawMarket(ctx: Ctx, e: Entity, half: number, tc: any) {
  ctx.fillStyle = PAL.wood;
  ctx.fillRect(e.x - half + 4, e.y - half * 0.15, half * 2 - 8, half * 0.95);
  // striped awning
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
  // tall cylinder reads via vertical gradient
  const g = ctx.createLinearGradient(e.x - half, e.y, e.x + half, e.y);
  g.addColorStop(0, PAL.stoneLight);
  g.addColorStop(0.5, PAL.stone);
  g.addColorStop(1, PAL.stoneDark);
  ctx.fillStyle = g;
  ctx.fillRect(e.x - half * 0.75, e.y - half * 1.3, half * 1.5, half * 2.1);
  // crenellations
  ctx.fillStyle = PAL.stoneDark;
  for (let i = 0; i < 4; i++) {
    ctx.fillRect(e.x - half * 0.75 + i * half * 0.42, e.y - half * 1.5, half * 0.26, half * 0.24);
  }
  // arrow slit
  ctx.fillStyle = "#241c12";
  ctx.fillRect(e.x - 1.5, e.y - half * 0.6, 3, half * 0.5);
  ctx.fillStyle = tc.main;
  ctx.fillRect(e.x - half * 0.75, e.y - half * 1.26, half * 1.5, 3);
}

function drawCastle(ctx: Ctx, e: Entity, half: number, tc: any, time: number) {
  // corner towers
  for (const [px, py] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
    const cx = e.x + px * half * 0.72;
    const cy = e.y + py * half * 0.72;
    ctx.fillStyle = PAL.stoneDark;
    ctx.beginPath();
    ctx.arc(cx, cy, half * 0.32, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = PAL.stone;
    ctx.beginPath();
    ctx.arc(cx - 1.5, cy - 1.5, half * 0.26, 0, Math.PI * 2);
    ctx.fill();
  }
  // keep
  const g = ctx.createLinearGradient(e.x, e.y - half, e.x, e.y + half);
  g.addColorStop(0, PAL.stoneLight);
  g.addColorStop(1, PAL.stoneDark);
  ctx.fillStyle = g;
  ctx.fillRect(e.x - half * 0.62, e.y - half * 0.62, half * 1.24, half * 1.24);
  // crenellated top edge
  ctx.fillStyle = PAL.stoneDark;
  for (let i = 0; i < 5; i++) {
    ctx.fillRect(e.x - half * 0.62 + i * half * 0.28, e.y - half * 0.72, half * 0.16, half * 0.16);
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

export function drawUnit(ctx: Ctx, e: Entity, time: number) {
  const tc = teamColor(e.team);
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

  // Active-ability aura: a pulsing coloured ring beneath the unit.
  if (e.abilityActive > 0) {
    const ab = ABILITIES[e.type];
    if (ab) {
      const pulse = 0.62 + Math.sin(time * 9 + e.id) * 0.22;
      ctx.strokeStyle = withAlpha(ab.color, 0.85 * pulse);
      ctx.lineWidth = 2.2;
      ctx.beginPath();
      ctx.ellipse(e.x, e.y + e.radius * 0.5, e.radius * 1.5, e.radius * 0.7, 0, 0, Math.PI * 2);
      ctx.stroke();
      ctx.fillStyle = withAlpha(ab.color, 0.14 * pulse);
      ctx.fill();
    }
  }

  shadow(ctx, e.x + 1, e.y + e.radius * 0.55, e.radius * 0.95, e.radius * 0.42);

  ctx.save();
  ctx.translate(e.x + Math.cos(e.facing) * lunge * 4, e.y - bob + Math.sin(e.facing) * lunge * 4);

  switch (e.type) {
    case "villager": drawVillager(ctx, e, tc, moving); break;
    case "militia": drawMilitia(ctx, e, tc, lunge); break;
    case "spearman": drawSpearman(ctx, e, tc, lunge); break;
    case "archer": drawArcher(ctx, e, tc, atkFrac); break;
    case "skirmisher": drawSkirmisher(ctx, e, tc, atkFrac); break;
    case "knight": drawKnight(ctx, e, tc, moving, time, lunge); break;
    case "catapult": drawCatapult(ctx, e, tc, atkFrac); break;
    case "ram": drawRam(ctx, e, tc, atkFrac); break;
    case "monk": drawMonk(ctx, e, tc, time); break;
    default: {
      ctx.fillStyle = tc.main;
      ctx.beginPath();
      ctx.arc(0, 0, e.radius, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  ctx.restore();

  // Rarity trim ring.
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

function body(ctx: Ctx, r: number, cloth: string, clothDark: string) {
  // torso
  ctx.fillStyle = clothDark;
  ctx.beginPath();
  ctx.arc(0, 0, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = cloth;
  ctx.beginPath();
  ctx.arc(-r * 0.18, -r * 0.18, r * 0.82, 0, Math.PI * 2);
  ctx.fill();
}

function head(ctx: Ctx, r: number, helmetColor?: string) {
  ctx.fillStyle = PAL.skin;
  ctx.beginPath();
  ctx.arc(0, -r * 0.35, r * 0.52, 0, Math.PI * 2);
  ctx.fill();
  if (helmetColor) {
    ctx.fillStyle = helmetColor;
    ctx.beginPath();
    ctx.arc(0, -r * 0.42, r * 0.5, Math.PI * 0.95, Math.PI * 2.05);
    ctx.fill();
  }
}

function weaponAngleParts(facing: number): [number, number] {
  return [Math.cos(facing), Math.sin(facing)];
}

function drawVillager(ctx: Ctx, e: Entity, tc: any, moving: boolean) {
  const r = e.radius;
  body(ctx, r, PAL.leather, shade(PAL.leather, -0.25));
  // team sash
  ctx.strokeStyle = tc.main;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(-r * 0.6, -r * 0.3);
  ctx.lineTo(r * 0.5, r * 0.5);
  ctx.stroke();
  head(ctx, r);
  // tool: hatchet over shoulder
  const [fx, fy] = weaponAngleParts(e.facing);
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(fx * r * 0.4, fy * r * 0.4);
  ctx.lineTo(fx * r * 1.3, fy * r * 1.3);
  ctx.stroke();
  ctx.fillStyle = PAL.steelDark;
  ctx.fillRect(fx * r * 1.3 - 2.5, fy * r * 1.3 - 2.5, 5, 5);
  // carry bundle
  if (e.carry > 2 && e.carryKind) {
    const col = e.carryKind === "wood" ? PAL.trunk : e.carryKind === "gold" ? PAL.goldVein : PAL.berry;
    ctx.fillStyle = col;
    ctx.beginPath();
    ctx.arc(-fx * r * 0.8, -fy * r * 0.8 - r * 0.4, r * 0.4, 0, Math.PI * 2);
    ctx.fill();
  }
}

function drawMilitia(ctx: Ctx, e: Entity, tc: any, lunge: number) {
  const r = e.radius;
  body(ctx, r, tc.main, tc.dark);
  head(ctx, r, PAL.steelDark);
  const [fx, fy] = weaponAngleParts(e.facing);
  // round shield on off-hand side
  ctx.fillStyle = tc.dark;
  ctx.beginPath();
  ctx.arc(-fy * r * 0.75, fx * r * 0.75, r * 0.5, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = PAL.steel;
  ctx.beginPath();
  ctx.arc(-fy * r * 0.75, fx * r * 0.75, r * 0.18, 0, Math.PI * 2);
  ctx.fill();
  // sword
  ctx.strokeStyle = PAL.steel;
  ctx.lineWidth = 2.2;
  ctx.beginPath();
  ctx.moveTo(fx * r * 0.3, fy * r * 0.3);
  ctx.lineTo(fx * r * (1.5 + lunge), fy * r * (1.5 + lunge));
  ctx.stroke();
}

function drawSpearman(ctx: Ctx, e: Entity, tc: any, lunge: number) {
  const r = e.radius;
  body(ctx, r, tc.main, tc.dark);
  head(ctx, r, PAL.leather);
  const [fx, fy] = weaponAngleParts(e.facing);
  // long spear
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = 1.8;
  ctx.beginPath();
  ctx.moveTo(-fx * r * 0.8, -fy * r * 0.8);
  ctx.lineTo(fx * r * (2.2 + lunge * 0.8), fy * r * (2.2 + lunge * 0.8));
  ctx.stroke();
  ctx.fillStyle = PAL.steel;
  const tipX = fx * r * (2.2 + lunge * 0.8);
  const tipY = fy * r * (2.2 + lunge * 0.8);
  ctx.beginPath();
  ctx.moveTo(tipX + fx * 4, tipY + fy * 4);
  ctx.lineTo(tipX - fy * 2.2, tipY + fx * 2.2);
  ctx.lineTo(tipX + fy * 2.2, tipY - fx * 2.2);
  ctx.closePath();
  ctx.fill();
}

function drawArcher(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  body(ctx, r, tc.main, tc.dark);
  head(ctx, r, shade(tc.dark, -0.15));
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
  body(ctx, r, PAL.leather, shade(PAL.leather, -0.25));
  // team band
  ctx.strokeStyle = tc.main;
  ctx.lineWidth = 2.4;
  ctx.beginPath();
  ctx.arc(0, 0, r * 0.85, -0.6, 0.9);
  ctx.stroke();
  head(ctx, r);
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

function drawKnight(ctx: Ctx, e: Entity, tc: any, moving: boolean, time: number, lunge: number) {
  const r = e.radius;
  const [fx, fy] = weaponAngleParts(e.facing);
  // horse: ellipse along facing
  ctx.save();
  ctx.rotate(e.facing);
  const gallop = moving ? Math.sin(e.animPhase * 11) * 1.2 : 0;
  ctx.fillStyle = "#5e4632";
  ctx.beginPath();
  ctx.ellipse(0, 0, r * 1.25, r * 0.62, 0, 0, Math.PI * 2);
  ctx.fill();
  // head/neck
  ctx.beginPath();
  ctx.ellipse(r * 1.15, -r * 0.1, r * 0.42, r * 0.26, -0.35, 0, Math.PI * 2);
  ctx.fill();
  // legs flicker when galloping
  if (moving) {
    ctx.strokeStyle = "#4a3626";
    ctx.lineWidth = 2;
    for (const lx of [-r * 0.7, r * 0.6]) {
      ctx.beginPath();
      ctx.moveTo(lx, r * 0.4);
      ctx.lineTo(lx + gallop * 2, r * 0.85);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(lx + r * 0.25, r * 0.4);
      ctx.lineTo(lx + r * 0.25 - gallop * 2, r * 0.85);
      ctx.stroke();
    }
  }
  // caparison (team cloth over horse)
  ctx.fillStyle = withAlpha(tc.main, 0.85);
  ctx.beginPath();
  ctx.ellipse(-r * 0.15, 0, r * 0.7, r * 0.55, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
  // rider
  ctx.fillStyle = tc.dark;
  ctx.beginPath();
  ctx.arc(-fx * r * 0.15, -fy * r * 0.15 - r * 0.35, r * 0.5, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = PAL.steel;
  ctx.beginPath();
  ctx.arc(-fx * r * 0.15, -fy * r * 0.15 - r * 0.6, r * 0.3, 0, Math.PI * 2);
  ctx.fill();
  // plume
  ctx.fillStyle = tc.light;
  ctx.beginPath();
  ctx.arc(-fx * r * 0.15, -fy * r * 0.15 - r * 0.85, r * 0.13, 0, Math.PI * 2);
  ctx.fill();
  // lance
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(-fx * r * 0.5, -fy * r * 0.5 - r * 0.3);
  ctx.lineTo(fx * r * (2 + lunge), fy * r * (2 + lunge) - r * 0.2);
  ctx.stroke();
}

function drawCatapult(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  ctx.save();
  ctx.rotate(e.facing);
  // frame
  ctx.fillStyle = PAL.woodDark;
  ctx.fillRect(-r, -r * 0.6, r * 2, r * 1.2);
  ctx.fillStyle = PAL.wood;
  ctx.fillRect(-r * 0.85, -r * 0.45, r * 1.7, r * 0.9);
  // wheels
  ctx.fillStyle = "#3a2c1c";
  for (const [wx, wy] of [[-r * 0.7, -r * 0.62], [-r * 0.7, r * 0.62], [r * 0.7, -r * 0.62], [r * 0.7, r * 0.62]]) {
    ctx.beginPath();
    ctx.arc(wx, wy, r * 0.26, 0, Math.PI * 2);
    ctx.fill();
  }
  // throwing arm: cocked back as it reloads, snaps forward on fire
  const armAngle = atkFrac > 0.85 ? -0.4 : atkFrac > 0 ? 0.9 - (0.85 - atkFrac) : -0.4;
  ctx.strokeStyle = PAL.woodLight;
  ctx.lineWidth = 3.2;
  ctx.beginPath();
  ctx.moveTo(-r * 0.4, 0);
  ctx.lineTo(-r * 0.4 + Math.cos(armAngle) * r * 1.2, -Math.sin(armAngle) * r * 1.2);
  ctx.stroke();
  // cup
  ctx.fillStyle = PAL.leather;
  ctx.beginPath();
  ctx.arc(-r * 0.4 + Math.cos(armAngle) * r * 1.2, -Math.sin(armAngle) * r * 1.2, r * 0.2, 0, Math.PI * 2);
  ctx.fill();
  // team pennant
  ctx.fillStyle = tc.main;
  ctx.fillRect(r * 0.75, -r * 0.1, r * 0.45, r * 0.2);
  ctx.restore();
}

function drawRam(ctx: Ctx, e: Entity, tc: any, atkFrac: number) {
  const r = e.radius;
  ctx.save();
  ctx.rotate(e.facing);
  // protective roof
  ctx.fillStyle = PAL.woodDark;
  ctx.beginPath();
  ctx.moveTo(-r, r * 0.55);
  ctx.lineTo(-r, -r * 0.55);
  ctx.lineTo(-r * 0.6, -r * 0.75);
  ctx.lineTo(r * 0.6, -r * 0.75);
  ctx.lineTo(r, -r * 0.55);
  ctx.lineTo(r, r * 0.55);
  ctx.lineTo(r * 0.6, r * 0.75);
  ctx.lineTo(-r * 0.6, r * 0.75);
  ctx.closePath();
  ctx.fill();
  ctx.fillStyle = PAL.wood;
  ctx.fillRect(-r * 0.85, -r * 0.5, r * 1.7, r);
  // plank lines
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 1;
  for (let i = -2; i <= 2; i++) {
    ctx.beginPath();
    ctx.moveTo(i * r * 0.35, -r * 0.5);
    ctx.lineTo(i * r * 0.35, r * 0.5);
    ctx.stroke();
  }
  // swinging log tip: pokes out the front on attack
  const swing = atkFrac > 0.8 ? (atkFrac - 0.8) / 0.2 : 0;
  ctx.fillStyle = PAL.trunk;
  ctx.fillRect(r * (0.6 + swing * 0.5), -r * 0.14, r * 0.7, r * 0.28);
  ctx.fillStyle = PAL.steelDark;
  ctx.fillRect(r * (1.2 + swing * 0.5), -r * 0.17, r * 0.16, r * 0.34);
  // team stripe
  ctx.fillStyle = tc.main;
  ctx.fillRect(-r * 0.85, -r * 0.55, r * 1.7, 3);
  ctx.restore();
}

function drawMonk(ctx: Ctx, e: Entity, tc: any, time: number) {
  const r = e.radius;
  // robe
  body(ctx, r, "#cfc4a8", "#a89a78");
  ctx.fillStyle = tc.main;
  ctx.fillRect(-1.5, -r, 3, r * 1.8);
  head(ctx, r, "#a89a78");
  // staff with glowing tip
  const [fx, fy] = weaponAngleParts(e.facing);
  ctx.strokeStyle = PAL.woodDark;
  ctx.lineWidth = 1.8;
  ctx.beginPath();
  ctx.moveTo(fx * r * 0.5, fy * r * 0.5 + r * 0.5);
  ctx.lineTo(fx * r * 0.9, fy * r * 0.9 - r * 1.1);
  ctx.stroke();
  const glow = 0.6 + Math.sin(time * 4 + e.id) * 0.3;
  ctx.fillStyle = withAlpha(PAL.heal, glow);
  ctx.beginPath();
  ctx.arc(fx * r * 0.9, fy * r * 0.9 - r * 1.2, 3, 0, Math.PI * 2);
  ctx.fill();
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
