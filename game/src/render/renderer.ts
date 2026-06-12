// Frame rendering: terrain blit, y-sorted entities (fog-filtered), particles,
// fog-of-war overlay, command markers, placement ghost and drag box.

import { World, FOG_UNSEEN, FOG_VISIBLE } from "../sim/world";
import { BuildState, Entity, Kind, Team } from "../sim/types";
import { Camera } from "../engine/camera";
import { Particles } from "../engine/particles";
import { buildTerrainCache, TERRAIN_SCALE } from "./terrain";
import {
  drawBuilding,
  drawHealthBar,
  drawProjectile,
  drawResource,
  drawSelectionRing,
  drawUnit,
} from "./draw";
import { PAL, teamColor, withAlpha } from "./palette";
import { BUILDINGS } from "../content/buildings";
import { TILE } from "../content/balance";
import { lerp } from "../engine/math";
import type { MapData } from "../maps/generator";

// Battlefield decals: corpses, stuck arrows and scorch marks linger after the
// action and fade. Presentation-only (kept out of the deterministic sim).
interface Decal {
  kind: "corpse" | "arrow" | "scorch";
  x: number;
  y: number;
  angle: number;
  team: number;
  size: number;
  age: number;
  life: number;
}

interface Floater {
  x: number;
  y: number;
  vy: number;
  text: string;
  color: string;
  age: number;
  life: number;
  size: number;
}

/** Day/night sky tint by cycle phase (0..1). Returns rgba overlay components. */
function skyTint(phase: number): [number, number, number, number] {
  const keys: [number, [number, number, number, number]][] = [
    [0.0, [255, 168, 96, 0.16]], // dawn
    [0.22, [255, 255, 255, 0.0]], // bright day
    [0.46, [255, 120, 60, 0.2]], // dusk
    [0.6, [22, 32, 74, 0.5]], // night
    [0.9, [18, 28, 68, 0.46]], // deep night
    [1.0, [255, 168, 96, 0.16]], // back to dawn
  ];
  for (let i = 0; i < keys.length - 1; i++) {
    const [p0, c0] = keys[i];
    const [p1, c1] = keys[i + 1];
    if (phase >= p0 && phase <= p1) {
      const t = (phase - p0) / (p1 - p0 || 1);
      return [lerp(c0[0], c1[0], t), lerp(c0[1], c1[1], t), lerp(c0[2], c1[2], t), lerp(c0[3], c1[3], t)];
    }
  }
  return [0, 0, 0, 0];
}

export interface CommandMarker {
  x: number;
  y: number;
  age: number; // seconds since created
  kind: "move" | "attack" | "rally";
}

export interface GhostPlacement {
  type: string;
  x: number;
  y: number;
  valid: boolean;
}

export class Renderer {
  ctx: CanvasRenderingContext2D;
  private terrainCache: HTMLCanvasElement | null = null;
  private fogCanvas: HTMLCanvasElement | null = null;
  private fogCtx: CanvasRenderingContext2D | null = null;
  private fogDirtyTimer = 0;
  shakeX = 0;
  shakeY = 0;
  private shakeAmp = 0;
  decals: Decal[] = [];
  floaters: Floater[] = [];
  dayPhase = 0.18; // 0..1 around the day/night cycle

  constructor(public canvas: HTMLCanvasElement) {
    this.ctx = canvas.getContext("2d")!;
  }

  /** True when it is visually night — gameplay layers can read this later. */
  get isNight(): boolean {
    return this.dayPhase > 0.55 && this.dayPhase < 0.95;
  }

  addCorpse(x: number, y: number, team: number, big = false) {
    this.decals.push({ kind: "corpse", x, y, angle: Math.random() * Math.PI * 2, team, size: big ? 16 : 9, age: 0, life: big ? 14 : 9 });
    if (this.decals.length > 240) this.decals.shift();
  }
  addStuckArrow(x: number, y: number, angle: number) {
    this.decals.push({ kind: "arrow", x, y, angle, team: 0, size: 6, age: 0, life: 6 });
    if (this.decals.length > 240) this.decals.shift();
  }
  addScorch(x: number, y: number, size: number) {
    this.decals.push({ kind: "scorch", x, y, angle: 0, team: 0, size, age: 0, life: 22 });
    if (this.decals.length > 240) this.decals.shift();
  }
  addFloater(x: number, y: number, text: string, color: string, size = 13) {
    this.floaters.push({ x: x + (Math.random() - 0.5) * 10, y, vy: -26, text, color, age: 0, life: 0.9, size });
    if (this.floaters.length > 80) this.floaters.shift();
  }
  clearFx() {
    this.decals.length = 0;
    this.floaters.length = 0;
  }

  private drawDecals(ctx: CanvasRenderingContext2D, dt: number, vx0: number, vy0: number, vx1: number, vy1: number) {
    for (let i = this.decals.length - 1; i >= 0; i--) {
      const d = this.decals[i];
      d.age += dt;
      if (d.age >= d.life) {
        this.decals.splice(i, 1);
        continue;
      }
      if (d.x < vx0 || d.x > vx1 || d.y < vy0 || d.y > vy1) continue;
      const fade = Math.min(1, (d.life - d.age) / 2.5);
      ctx.globalAlpha = fade;
      if (d.kind === "corpse") {
        ctx.fillStyle = "rgba(26, 18, 12, 0.5)";
        ctx.beginPath();
        ctx.ellipse(d.x, d.y + 2, d.size, d.size * 0.45, 0, 0, Math.PI * 2);
        ctx.fill();
        const tc = teamColor(d.team);
        ctx.fillStyle = withAlpha(tc.dark, 0.8);
        ctx.beginPath();
        ctx.ellipse(d.x, d.y, d.size * 0.7, d.size * 0.42, d.angle, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = withAlpha(PAL.blood, 0.35);
        ctx.beginPath();
        ctx.ellipse(d.x + Math.cos(d.angle) * d.size * 0.5, d.y + Math.sin(d.angle) * d.size * 0.3, d.size * 0.9, d.size * 0.4, d.angle, 0, Math.PI * 2);
        ctx.fill();
      } else if (d.kind === "arrow") {
        ctx.strokeStyle = "rgba(90, 70, 45, 0.9)";
        ctx.lineWidth = 1.4;
        ctx.beginPath();
        ctx.moveTo(d.x, d.y);
        ctx.lineTo(d.x + Math.cos(d.angle) * d.size, d.y + Math.sin(d.angle) * d.size - 5);
        ctx.stroke();
      } else {
        ctx.fillStyle = "rgba(18, 14, 10, 0.5)";
        ctx.beginPath();
        ctx.ellipse(d.x, d.y, d.size, d.size * 0.6, 0, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    ctx.globalAlpha = 1;
  }

  private drawFloaters(ctx: CanvasRenderingContext2D, dt: number) {
    ctx.textAlign = "center";
    for (let i = this.floaters.length - 1; i >= 0; i--) {
      const f = this.floaters[i];
      f.age += dt;
      if (f.age >= f.life) {
        this.floaters.splice(i, 1);
        continue;
      }
      f.y += f.vy * dt;
      const t = f.age / f.life;
      ctx.globalAlpha = 1 - t * t;
      ctx.font = `bold ${f.size}px "Trebuchet MS", sans-serif`;
      ctx.fillStyle = "rgba(0,0,0,0.6)";
      ctx.fillText(f.text, f.x + 1, f.y + 1);
      ctx.fillStyle = f.color;
      ctx.fillText(f.text, f.x, f.y);
    }
    ctx.globalAlpha = 1;
    ctx.textAlign = "left";
  }

  prepare(map: MapData) {
    this.terrainCache = buildTerrainCache(map);
    this.fogCanvas = document.createElement("canvas");
    this.fogCanvas.width = map.cols;
    this.fogCanvas.height = map.rows;
    this.fogCtx = this.fogCanvas.getContext("2d")!;
  }

  addShake(amp: number) {
    this.shakeAmp = Math.min(10, this.shakeAmp + amp);
  }

  render(
    world: World,
    cam: Camera,
    particles: Particles,
    dt: number,
    time: number,
    viewTeam: Team,
    markers: CommandMarker[],
    ghost: GhostPlacement | null,
    dragBox: { active: boolean; x0: number; y0: number; x1: number; y1: number },
    rallyFrom: Entity | null,
  ) {
    const { ctx, canvas } = this;
    const W = canvas.width;
    const H = canvas.height;

    // Screen shake decay.
    this.shakeAmp *= Math.pow(0.0015, dt);
    if (this.shakeAmp < 0.05) this.shakeAmp = 0;
    this.shakeX = (Math.random() - 0.5) * this.shakeAmp * 2;
    this.shakeY = (Math.random() - 0.5) * this.shakeAmp * 2;

    ctx.fillStyle = "#0d0b08";
    ctx.fillRect(0, 0, W, H);

    ctx.save();
    ctx.translate(W / 2 + this.shakeX, H / 2 + this.shakeY);
    ctx.scale(cam.zoom, cam.zoom);
    ctx.translate(-cam.x, -cam.y);

    // Visible world rect for culling.
    const pad = 80;
    const vx0 = cam.x - W / 2 / cam.zoom - pad;
    const vy0 = cam.y - H / 2 / cam.zoom - pad;
    const vx1 = cam.x + W / 2 / cam.zoom + pad;
    const vy1 = cam.y + H / 2 / cam.zoom + pad;

    // Terrain.
    if (this.terrainCache) {
      ctx.imageSmoothingEnabled = true;
      ctx.drawImage(
        this.terrainCache,
        0, 0, this.terrainCache.width, this.terrainCache.height,
        0, 0, this.terrainCache.width / TERRAIN_SCALE, this.terrainCache.height / TERRAIN_SCALE,
      );
    }

    // Ground decals (corpses, arrows, scorch) sit on the terrain, under units.
    this.drawDecals(ctx, dt, vx0, vy0, vx1, vy1);

    // Collect and sort drawable entities by y (painter's algorithm).
    const drawables: Entity[] = [];
    for (const e of world.entities) {
      if (!e.alive) continue;
      if (e.x < vx0 || e.x > vx1 || e.y < vy0 || e.y > vy1) continue;
      if (!world.visibleTo(viewTeam, e)) continue;
      drawables.push(e);
    }
    drawables.sort((a, b) => a.y - b.y || a.id - b.id);

    // Selection rings under everything else.
    for (const e of drawables) {
      if (e.selected) drawSelectionRing(ctx, e, e.team === viewTeam);
    }

    // Rally line from a selected production building.
    if (rallyFrom && rallyFrom.rallyX >= 0) {
      ctx.strokeStyle = withAlpha("#7df27d", 0.5);
      ctx.lineWidth = 1.5;
      ctx.setLineDash([6, 6]);
      ctx.beginPath();
      ctx.moveTo(rallyFrom.x, rallyFrom.y);
      ctx.lineTo(rallyFrom.rallyX, rallyFrom.rallyY);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = withAlpha("#7df27d", 0.8);
      ctx.beginPath();
      ctx.arc(rallyFrom.rallyX, rallyFrom.rallyY, 5, 0, Math.PI * 2);
      ctx.fill();
    }

    for (const e of drawables) {
      const ghosted =
        e.team !== viewTeam &&
        e.kind === Kind.Building &&
        world.fogAt(viewTeam, e.x, e.y) !== FOG_VISIBLE;
      if (ghosted) ctx.globalAlpha = 0.45;
      switch (e.kind) {
        case Kind.Resource: drawResource(ctx, e, time); break;
        case Kind.Building: drawBuilding(ctx, e, time, viewTeam); break;
        case Kind.Unit: drawUnit(ctx, e, time); break;
        case Kind.Projectile: drawProjectile(ctx, e); break;
      }
      if (ghosted) ctx.globalAlpha = 1;
    }

    // Health bars for damaged or selected entities.
    for (const e of drawables) {
      if (e.kind !== Kind.Unit && e.kind !== Kind.Building) continue;
      if (e.selected || (e.hp < e.maxHp && time - e.lastDamageTime < 6)) {
        if (e.kind === Kind.Building && e.buildState !== BuildState.Done) continue;
        drawHealthBar(ctx, e);
      }
    }

    // Particles.
    for (const p of particles.pool) {
      if (!p.active) continue;
      if (p.x < vx0 || p.x > vx1 || p.y < vy0 || p.y > vy1) continue;
      const lifeFrac = p.life / p.maxLife;
      ctx.globalAlpha = p.fade ? lifeFrac : 1;
      const size = p.shrink ? p.size * lifeFrac : p.size;
      ctx.fillStyle = p.color;
      if (p.glow) {
        ctx.shadowColor = p.color;
        ctx.shadowBlur = 8;
      }
      ctx.beginPath();
      ctx.arc(p.x, p.y, Math.max(0.4, size), 0, Math.PI * 2);
      ctx.fill();
      if (p.glow) ctx.shadowBlur = 0;
    }
    ctx.globalAlpha = 1;

    // Floating combat numbers above the fray.
    this.drawFloaters(ctx, dt);

    // Command markers (shrinking rings).
    for (const m of markers) {
      const t = m.age / 0.7;
      if (t > 1) continue;
      const r = 16 * (1 - t) + 4;
      ctx.strokeStyle = withAlpha(m.kind === "attack" ? "#f25d4a" : "#7df27d", 1 - t);
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(m.x, m.y, r, 0, Math.PI * 2);
      ctx.stroke();
    }

    // Building placement ghost.
    if (ghost) {
      const def = BUILDINGS[ghost.type];
      if (def) {
        const tiles = def.tiles;
        const sx = Math.round(ghost.x / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
        const sy = Math.round(ghost.y / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
        const half = (tiles * TILE) / 2;
        ctx.fillStyle = ghost.valid ? withAlpha("#7df27d", 0.3) : withAlpha("#f25d4a", 0.35);
        ctx.fillRect(sx - half, sy - half, half * 2, half * 2);
        ctx.strokeStyle = ghost.valid ? "#7df27d" : "#f25d4a";
        ctx.lineWidth = 2;
        ctx.strokeRect(sx - half, sy - half, half * 2, half * 2);
      }
    }

    // Fog of war overlay.
    this.fogDirtyTimer -= dt;
    if (this.fogDirtyTimer <= 0 && this.fogCtx && this.fogCanvas) {
      this.fogDirtyTimer = 0.12;
      const img = this.fogCtx.createImageData(world.fogCols, world.fogRows);
      const fog = world.fog[viewTeam];
      for (let i = 0; i < fog.length; i++) {
        const o = i * 4;
        img.data[o] = 8;
        img.data[o + 1] = 7;
        img.data[o + 2] = 4;
        img.data[o + 3] = fog[i] === FOG_UNSEEN ? 255 : fog[i] === FOG_VISIBLE ? 0 : 110;
      }
      this.fogCtx.putImageData(img, 0, 0);
    }
    if (this.fogCanvas) {
      ctx.imageSmoothingEnabled = true;
      ctx.drawImage(this.fogCanvas, 0, 0, world.fogCols, world.fogRows, 0, 0, world.worldW, world.worldH);
    }

    ctx.restore();

    // Day/night sky tint over the whole battlefield (screen space, under HUD).
    const [tr, tg, tb, ta] = skyTint(this.dayPhase);
    if (ta > 0.003) {
      ctx.fillStyle = `rgba(${tr | 0}, ${tg | 0}, ${tb | 0}, ${ta})`;
      ctx.fillRect(0, 0, W, H);
    }

    // Drag selection box (screen space).
    if (dragBox.active) {
      ctx.strokeStyle = withAlpha("#7df27d", 0.9);
      ctx.fillStyle = withAlpha("#7df27d", 0.08);
      ctx.lineWidth = 1.5;
      const x = Math.min(dragBox.x0, dragBox.x1);
      const y = Math.min(dragBox.y0, dragBox.y1);
      const w = Math.abs(dragBox.x1 - dragBox.x0);
      const h = Math.abs(dragBox.y1 - dragBox.y0);
      ctx.fillRect(x, y, w, h);
      ctx.strokeRect(x, y, w, h);
    }
  }
}
