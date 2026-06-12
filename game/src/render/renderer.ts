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
import { PAL, withAlpha } from "./palette";
import { BUILDINGS } from "../content/buildings";
import { TILE } from "../content/balance";
import type { MapData } from "../maps/generator";

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

  constructor(public canvas: HTMLCanvasElement) {
    this.ctx = canvas.getContext("2d")!;
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
