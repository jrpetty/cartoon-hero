// Terrain pre-rendering. The full map is painted once into an offscreen canvas
// (at half resolution to save memory — the soft scale-up reads as painterly),
// then blitted per frame. Also builds the minimap base image.

import { MapData, Terrain } from "../maps/generator";
import { TILE } from "../content/balance";
import { PAL, shade } from "./palette";
import { RNG } from "../engine/rng";

export const TERRAIN_SCALE = 0.5;

export function buildTerrainCache(map: MapData): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  canvas.width = Math.ceil(map.worldW * TERRAIN_SCALE);
  canvas.height = Math.ceil(map.worldH * TERRAIN_SCALE);
  const ctx = canvas.getContext("2d")!;
  const rng = new RNG(map.seed ^ 0xbeef);
  const t = TILE * TERRAIN_SCALE;

  const colorFor = (ter: number): string => {
    switch (ter) {
      case Terrain.GrassDark: return PAL.grassDark;
      case Terrain.Dirt: return PAL.dirt;
      case Terrain.Water: return PAL.water;
      case Terrain.Sand: return PAL.sand;
      default: return PAL.grass;
    }
  };

  // Base fill then per-tile color with slight jitter for a hand-painted feel.
  ctx.fillStyle = PAL.grass;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      const ter = map.terrain[cy * map.cols + cx];
      const base = colorFor(ter);
      const jitter = rng.range(-0.045, 0.045);
      ctx.fillStyle = shade(base, jitter);
      ctx.fillRect(cx * t, cy * t, t + 1, t + 1);
    }
  }

  // Soft edges between terrain types: overdraw blurred blobs on boundaries.
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      const i = cy * map.cols + cx;
      const ter = map.terrain[i];
      const right = cx + 1 < map.cols ? map.terrain[i + 1] : ter;
      const down = cy + 1 < map.rows ? map.terrain[i + map.cols] : ter;
      if (right !== ter || down !== ter) {
        ctx.globalAlpha = 0.35;
        ctx.fillStyle = colorFor(ter);
        ctx.beginPath();
        ctx.arc(cx * t + t, cy * t + t * 0.5, t * 0.7, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalAlpha = 1;
      }
    }
  }

  // Water depth + sparkle band near edges.
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      const i = cy * map.cols + cx;
      if (map.terrain[i] !== Terrain.Water) continue;
      // Inner cells get deeper color.
      const neighbors = [
        cx > 0 ? map.terrain[i - 1] : Terrain.Water,
        cx + 1 < map.cols ? map.terrain[i + 1] : Terrain.Water,
        cy > 0 ? map.terrain[i - map.cols] : Terrain.Water,
        cy + 1 < map.rows ? map.terrain[i + map.cols] : Terrain.Water,
      ];
      const isEdge = neighbors.some((n) => n !== Terrain.Water);
      if (isEdge) {
        ctx.fillStyle = PAL.waterEdge;
        ctx.globalAlpha = 0.5;
        ctx.fillRect(cx * t, cy * t, t, t);
        ctx.globalAlpha = 1;
      } else {
        ctx.fillStyle = PAL.waterDeep;
        ctx.globalAlpha = 0.55;
        ctx.fillRect(cx * t, cy * t, t, t);
        ctx.globalAlpha = 1;
      }
    }
  }

  // Scatter painterly grass tufts and pebbles.
  const detail = Math.floor((map.cols * map.rows) / 18);
  for (let i = 0; i < detail; i++) {
    const cx = rng.int(0, map.cols - 1);
    const cy = rng.int(0, map.rows - 1);
    const ter = map.terrain[cy * map.cols + cx];
    if (ter === Terrain.Water) continue;
    const x = cx * t + rng.range(0, t);
    const y = cy * t + rng.range(0, t);
    if (ter === Terrain.Dirt || ter === Terrain.Sand) {
      ctx.fillStyle = shade(PAL.dirtDark, rng.range(-0.1, 0.1));
      ctx.beginPath();
      ctx.arc(x, y, rng.range(0.8, 1.8), 0, Math.PI * 2);
      ctx.fill();
    } else {
      ctx.strokeStyle = shade(rng.bool() ? PAL.grassShade : PAL.grassDark, rng.range(-0.05, 0.1));
      ctx.lineWidth = 1;
      ctx.beginPath();
      const h = rng.range(2, 4.5);
      ctx.moveTo(x, y);
      ctx.quadraticCurveTo(x + rng.range(-1.5, 1.5), y - h * 0.6, x + rng.range(-2, 2), y - h);
      ctx.stroke();
    }
  }

  return canvas;
}

/** Small terrain image for the minimap background. */
export function buildMinimapBase(map: MapData, size: number): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d")!;
  const sx = size / map.cols;
  const sy = size / map.rows;
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      const ter = map.terrain[cy * map.cols + cx];
      ctx.fillStyle =
        ter === Terrain.Water ? PAL.water :
        ter === Terrain.Dirt ? PAL.dirt :
        ter === Terrain.Sand ? PAL.sand :
        ter === Terrain.GrassDark ? PAL.grassDark : PAL.grass;
      ctx.fillRect(cx * sx, cy * sy, sx + 1, sy + 1);
    }
  }
  return canvas;
}
