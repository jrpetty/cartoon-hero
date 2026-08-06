// Frame rendering: terrain blit, y-sorted entities (fog-filtered), particles,
// fog-of-war overlay, command markers, placement ghost and drag box.

import { World, FOG_UNSEEN, FOG_VISIBLE } from "../sim/world";
import { BuildState, Entity, Kind, Team } from "../sim/types";
import { Camera } from "../engine/camera";
import { Particles } from "../engine/particles";
import { buildTerrainCache, terrainCacheScale } from "./terrain";
import {
  drawBuilding,
  drawHealthBar,
  drawProjectile,
  drawResource,
  drawSelectionRing,
  drawUnit,
  setDrawDayPhase,
  setTeamColorResolver,
} from "./draw";
import { PAL, teamColor, withAlpha } from "./palette";
import { BUILDINGS } from "../content/buildings";
import { snapBuilding } from "../engine/gridsnap";
import { TILE } from "../content/balance";
import { skyTint } from "../content/daynight";
import { Terrain } from "../maps/generator";
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
  /** When drag-painting a wall, the snapped tile centres of the whole run. */
  line?: { x: number; y: number; valid: boolean }[];
  /**
   * "14 segments · 70 wood", drawn at the cursor while dragging. You should not
   * have to build a wall to find out how long it is or what it costs.
   */
  label?: string;
}

export class Renderer {
  ctx: CanvasRenderingContext2D;
  private terrainCache: HTMLCanvasElement | null = null;
  /** The scale this map's cache was baked at — large maps are baked coarser. */
  private terrainScale = 0.5;
  private fogCanvas: HTMLCanvasElement | null = null;
  private fogCtx: CanvasRenderingContext2D | null = null;
  private fogDirtyTimer = 0;
  shakeX = 0;
  shakeY = 0;
  private shakeAmp = 0;
  decals: Decal[] = [];
  floaters: Floater[] = [];
  dayPhase = 0.18; // 0..1 around the day/night cycle
  aggressiveLod = false; // "Reduce effects" → simplify units sooner
  private waterCells: number[] = []; // [x0,y0,x1,y1,...] water cell centres
  private vignette: HTMLCanvasElement | null = null;

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

  // Render resilience: a single bad draw must never blank the whole frame.
  // Run `fn`; on throw, restore the transform/state and log the cause once.
  private warned = new Set<string>();
  private guard(ctx: CanvasRenderingContext2D, key: string, tf: DOMMatrix | null, fn: () => void) {
    try {
      fn();
    } catch (err) {
      if (tf) ctx.setTransform(tf);
      ctx.globalAlpha = 1;
      ctx.shadowBlur = 0;
      if (!this.warned.has(key)) {
        this.warned.add(key);
        console.error(`render guard [${key}]:`, err);
      }
    }
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
    this.terrainScale = terrainCacheScale(map);
    this.fogCanvas = document.createElement("canvas");
    this.fogCanvas.width = map.cols;
    this.fogCanvas.height = map.rows;
    this.fogCtx = this.fogCanvas.getContext("2d")!;
    // Water cell centres (world coords) for the animated glint pass.
    this.waterCells = [];
    for (let cy = 0; cy < map.rows; cy++) {
      for (let cx = 0; cx < map.cols; cx++) {
        if (map.terrain[cy * map.cols + cx] === Terrain.Water) {
          this.waterCells.push(cx * TILE + TILE / 2, cy * TILE + TILE / 2);
        }
      }
    }
    this.vignette = null; // rebuilt on first frame for the current canvas size
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
    hoveredId = -1,
    alpha = 1, // 0..1 interpolation between the last two sim ticks
  ) {
    const { ctx, canvas } = this;
    const W = canvas.width;
    const H = canvas.height;
    setDrawDayPhase(this.dayPhase);
    // Spectators see absolute per-realm colours (every side distinct); players
    // see units coloured by relation to themselves (you/ally/enemy).
    if (world.revealAll) {
      setTeamColorResolver(null); // default = absolute PAL.teams colour per team
    } else if (world.hasTeamAlliances()) {
      setTeamColorResolver((team) => {
        if (team >= world.numTeams) return teamColor(team);
        const rel = world.relationTo(viewTeam, team as Team);
        return rel === "self" ? PAL.diplomacy.self
          : rel === "ally" ? PAL.diplomacy.ally
          : PAL.diplomacy.enemies[world.enemyIndexOf(viewTeam, team as Team) % PAL.diplomacy.enemies.length];
      });
    } else {
      setTeamColorResolver(null);
    }

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

    // Snapshot of the world-space transform — used to recover from any draw
    // that throws so it can't corrupt the rest of the frame.
    const worldTf = ctx.getTransform();

    // Terrain — blit only the slice under the camera, not the whole map. (The
    // cache spans the world at TERRAIN_SCALE; drawing all of it every frame was
    // a heavy per-frame native allocation that leaked memory over a match.)
    if (this.terrainCache) {
      ctx.imageSmoothingEnabled = true;
      const cx0 = Math.max(0, vx0);
      const cy0 = Math.max(0, vy0);
      const cx1 = Math.min(world.worldW, vx1);
      const cy1 = Math.min(world.worldH, vy1);
      if (cx1 > cx0 && cy1 > cy0) {
        ctx.drawImage(
          this.terrainCache,
          cx0 * this.terrainScale, cy0 * this.terrainScale,
          (cx1 - cx0) * this.terrainScale, (cy1 - cy0) * this.terrainScale,
          cx0, cy0, cx1 - cx0, cy1 - cy0,
        );
      }
    }

    // Ground decals (corpses, arrows, scorch) sit on the terrain, under units.
    this.guard(ctx, "decals", worldTf, () => this.drawDecals(ctx, dt, vx0, vy0, vx1, vy1));

    // Living water: drifting glints on visible water cells. Each cell twinkles
    // on its own phase; cheap because it's culled to the viewport.
    this.guard(ctx, "water", worldTf, () => {
      const wc = this.waterCells;
      for (let i = 0; i < wc.length; i += 2) {
        const x = wc[i];
        const y = wc[i + 1];
        if (x < vx0 || x > vx1 || y < vy0 || y > vy1) continue;
        const phase = x * 0.137 + y * 0.211;
        const s = Math.sin(time * 1.7 + phase);
        if (s <= 0.55) continue;
        const a = (s - 0.55) * 0.8;
        const jx = Math.sin(phase * 3.1) * TILE * 0.3 + Math.sin(time * 0.6 + phase) * 3;
        const jy = Math.cos(phase * 2.3) * TILE * 0.3;
        ctx.strokeStyle = withAlpha("#eaf7ff", a);
        ctx.lineWidth = 1.2;
        ctx.beginPath();
        ctx.arc(x + jx, y + jy, 2.2 + s * 1.6, Math.PI * 0.1, Math.PI * 0.9);
        ctx.stroke();
      }
    });

    // Commander banners — radius aura on the ground + a waving standard.
    this.guard(ctx, "banners", worldTf, () => {
      for (const b of world.banners) {
        if (b.x < vx0 - b.power.radius || b.x > vx1 + b.power.radius) continue;
        const col = b.power.color;
        const pulse = 0.5 + Math.sin(time * 2 + b.x) * 0.2;
        // radius ring
        ctx.strokeStyle = withAlpha(col, 0.4 * pulse);
        ctx.lineWidth = 2;
        ctx.setLineDash([8, 8]);
        ctx.beginPath();
        ctx.arc(b.x, b.y, b.power.radius, 0, Math.PI * 2);
        ctx.stroke();
        ctx.setLineDash([]);
        // soft fill
        const g = ctx.createRadialGradient(b.x, b.y, 1, b.x, b.y, b.power.radius);
        g.addColorStop(0, withAlpha(col, 0.07));
        g.addColorStop(1, withAlpha(col, 0));
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.arc(b.x, b.y, b.power.radius, 0, Math.PI * 2);
        ctx.fill();
        // pole + waving flag
        ctx.strokeStyle = "#5a4632";
        ctx.lineWidth = 2.5;
        ctx.beginPath();
        ctx.moveTo(b.x, b.y);
        ctx.lineTo(b.x, b.y - 26);
        ctx.stroke();
        ctx.fillStyle = col;
        const w = 5 + Math.sin(time * 6 + b.y) * 1.5;
        ctx.beginPath();
        ctx.moveTo(b.x, b.y - 26);
        ctx.lineTo(b.x + 16, b.y - 23 + w * 0.4);
        ctx.lineTo(b.x, b.y - 16);
        ctx.fill();
        ctx.fillStyle = "#ffe07a";
        ctx.beginPath();
        ctx.arc(b.x, b.y - 27, 2, 0, Math.PI * 2);
        ctx.fill();
      }
    });

    // King of the Hill: the central control zone.
    if (world.mode === "koth") {
      ctx.save();
      ctx.strokeStyle = withAlpha("#ffe07a", 0.85);
      ctx.lineWidth = 3;
      ctx.setLineDash([10, 8]);
      ctx.beginPath();
      ctx.arc(world.hillX, world.hillY, world.hillR, 0, Math.PI * 2);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = withAlpha("#ffe07a", 0.07);
      ctx.fill();
      ctx.restore();
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
    // Level-of-detail: when zoomed out or there's a big crowd on screen, draw
    // units as cheap blobs (skip auras/chevrons/weapons) — the detail is sub-pixel
    // anyway and this is what keeps a high-pop 8v8 smooth. "Reduce effects" makes
    // it kick in sooner for weaker machines.
    const zoomCut = this.aggressiveLod ? 0.82 : 0.62;
    const crowdCut = this.aggressiveLod ? 160 : 340;
    const lod = cam.zoom < zoomCut || drawables.length > crowdCut ? 1 : 0;

    // Tween each entity toward this frame's fraction between sim ticks so motion
    // is smooth at 60fps over the 20Hz sim. Originals are restored in `finally`
    // so the simulation never sees the interpolated values.
    const interpOX: number[] = [];
    const interpOY: number[] = [];
    if (alpha < 1) {
      for (let i = 0; i < drawables.length; i++) {
        const e = drawables[i];
        interpOX.push(e.x);
        interpOY.push(e.y);
        e.x = e.prevX + (e.x - e.prevX) * alpha;
        e.y = e.prevY + (e.y - e.prevY) * alpha;
      }
    }
    try {

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

    // Per-entity isolation so one bad draw never wipes the rest of the frame —
    // but inline (no closure/string alloc, which was the real cost we removed).
    for (const e of drawables) {
      const ghosted =
        e.team !== viewTeam &&
        e.kind === Kind.Building &&
        world.fogAt(viewTeam, e.x, e.y) !== FOG_VISIBLE;
      if (ghosted) ctx.globalAlpha = 0.45;
      try {
        switch (e.kind) {
          case Kind.Resource: drawResource(ctx, e, time); break;
          case Kind.Building: drawBuilding(ctx, e, time, viewTeam); break;
          case Kind.Unit: drawUnit(ctx, e, time, lod); break;
          case Kind.Projectile: drawProjectile(ctx, e); break;
        }
      } catch (err) {
        if (worldTf) ctx.setTransform(worldTf);
        ctx.globalAlpha = 1; ctx.shadowBlur = 0;
        if (!this.warned.has("entity")) { this.warned.add("entity"); console.error("render guard [entity]:", err); }
      }
      if (ghosted) ctx.globalAlpha = 1;
    }

    // Health bars for hovered, selected or recently-damaged entities. Zoomed
    // out (lod), only the selected get one — the rest are too small to read.
    for (const e of drawables) {
      if (e.kind !== Kind.Unit && e.kind !== Kind.Building) continue;
      const show = e.selected || e.id === hoveredId || (lod === 0 && e.hp < e.maxHp && time - e.lastDamageTime < 6);
      if (!show) continue;
      if (e.kind === Kind.Building && e.buildState !== BuildState.Done) continue;
      try {
        drawHealthBar(ctx, e);
      } catch (err) {
        if (worldTf) ctx.setTransform(worldTf);
        ctx.globalAlpha = 1; ctx.shadowBlur = 0;
        if (!this.warned.has("healthbar")) { this.warned.add("healthbar"); console.error("render guard [healthbar]:", err); }
      }
    }

    } finally {
      // Restore true sim positions regardless of any draw error above.
      if (alpha < 1) {
        for (let i = 0; i < drawables.length; i++) {
          drawables[i].x = interpOX[i];
          drawables[i].y = interpOY[i];
        }
      }
    }

    // Particles.
    for (const p of particles.pool) {
      if (!p.active) continue;
      if (p.x < vx0 || p.x > vx1 || p.y < vy0 || p.y > vy1) continue;
      const lifeFrac = p.maxLife > 0 ? p.life / p.maxLife : 0;
      ctx.globalAlpha = p.fade ? Math.max(0, Math.min(1, lifeFrac)) : 1;
      const rawSize = p.shrink ? p.size * lifeFrac : p.size;
      const size = Number.isFinite(rawSize) ? Math.max(0.4, rawSize) : 0.4;
      ctx.fillStyle = p.color;
      if (p.glow) {
        ctx.shadowColor = p.color;
        ctx.shadowBlur = 8;
      }
      ctx.beginPath();
      ctx.arc(p.x, p.y, size, 0, Math.PI * 2);
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
        const half = (tiles * TILE) / 2;
        const cell = (x: number, y: number, valid: boolean) => {
          const sx = snapBuilding(x, tiles);
          const sy = snapBuilding(y, tiles);
          ctx.fillStyle = valid ? withAlpha("#7df27d", 0.3) : withAlpha("#f25d4a", 0.35);
          ctx.fillRect(sx - half, sy - half, half * 2, half * 2);
          ctx.strokeStyle = valid ? "#7df27d" : "#f25d4a";
          ctx.lineWidth = 2;
          ctx.strokeRect(sx - half, sy - half, half * 2, half * 2);
        };
        if (ghost.line && ghost.line.length > 0) {
          for (const pt of ghost.line) cell(pt.x, pt.y, pt.valid);
        } else {
          cell(ghost.x, ghost.y, ghost.valid);
        }
        if (ghost.label) {
          // Drawn in world space with the rest of the ghost, so it sits by the
          // cursor at any zoom. Dark plate behind it — a wall run crosses grass,
          // stone and water, and white text on grass is unreadable.
          const lx = ghost.x + 18;
          const ly = ghost.y - 14;
          ctx.font = "600 13px 'Trebuchet MS', sans-serif";
          const tw = ctx.measureText(ghost.label).width;
          ctx.fillStyle = "rgba(10,8,4,0.78)";
          ctx.beginPath();
          ctx.roundRect(lx - 6, ly - 12, tw + 12, 19, 4);
          ctx.fill();
          ctx.fillStyle = "#ffe9b0";
          ctx.textAlign = "left";
          ctx.textBaseline = "alphabetic";
          ctx.fillText(ghost.label, lx, ly + 2);
        }
      }
    }

    // Fog of war overlay (skipped entirely for spectators — they see all).
    if (!world.revealAll) this.guard(ctx, "fog", worldTf, () => {
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
        // Same viewport culling as terrain — only upscale the visible fog slice.
        const fox = world.fogCols / world.worldW;
        const foy = world.fogRows / world.worldH;
        const cx0 = Math.max(0, vx0);
        const cy0 = Math.max(0, vy0);
        const cx1 = Math.min(world.worldW, vx1);
        const cy1 = Math.min(world.worldH, vy1);
        if (cx1 > cx0 && cy1 > cy0) {
          ctx.drawImage(
            this.fogCanvas,
            cx0 * fox, cy0 * foy, (cx1 - cx0) * fox, (cy1 - cy0) * foy,
            cx0, cy0, cx1 - cx0, cy1 - cy0,
          );
        }
      }
    });

    ctx.restore();

    // Day/night sky tint over the whole battlefield (screen space, under HUD).
    const [tr, tg, tb, ta] = skyTint(this.dayPhase);
    if (ta > 0.003) {
      ctx.fillStyle = `rgba(${tr | 0}, ${tg | 0}, ${tb | 0}, ${ta})`;
      ctx.fillRect(0, 0, W, H);
    }

    // Cinematic vignette — cached per canvas size, a single blit per frame.
    if (!this.vignette || this.vignette.width !== W || this.vignette.height !== H) {
      this.vignette = document.createElement("canvas");
      this.vignette.width = W;
      this.vignette.height = H;
      const vctx = this.vignette.getContext("2d")!;
      const g = vctx.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.42, W / 2, H / 2, Math.hypot(W, H) * 0.62);
      g.addColorStop(0, "rgba(8,6,3,0)");
      g.addColorStop(1, "rgba(8,6,3,0.30)");
      vctx.fillStyle = g;
      vctx.fillRect(0, 0, W, H);
    }
    ctx.drawImage(this.vignette, 0, 0);

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
