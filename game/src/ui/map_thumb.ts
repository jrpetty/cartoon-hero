// A map at a glance.
//
// The editor's library and the lobby's battlefield picker both want the same
// thing — a few dozen pixels that say what a map *is* before you commit to it —
// so the swatch table and the drawing live here rather than being duplicated
// and drifting apart.
//
// Deliberately not the real terrain renderer: that bakes a texture the size of
// the map and is far too expensive for a list of cards redrawn every frame.
// This samples on a stride and fills flat rectangles.

import { Terrain } from "../maps/terrain_kinds";
import type { CustomMap } from "../maps/custom";
import { TILE } from "../content/balance";
import { PAL } from "../render/palette";

/** Flat colour per terrain — the editor palette, shared so the two agree. */
export const MAP_SWATCH: Record<number, string> = {
  [Terrain.Grass]: "#6f9c4a", [Terrain.GrassDark]: "#557f3c", [Terrain.Dirt]: "#8a6f47",
  [Terrain.Sand]: "#cbb383", [Terrain.Snow]: "#dfe7f0", [Terrain.Hill]: "#7f9c5b",
  [Terrain.Forest]: "#2f4429", [Terrain.Marsh]: "#4d5a3c", [Terrain.Shallow]: "#4a7f96",
  [Terrain.Water]: "#2f6a86", [Terrain.Rock]: "#6a6f76",
};

export interface ThumbOpts {
  /** Draw a numbered dot per spawn point. Off in tight rows where it'd be mush. */
  spawns?: boolean;
  /** Draw resource nodes. Off by default — on a small thumbnail it reads as noise. */
  resources?: boolean;
}

/**
 * Draw `m` into a `size`-square box at (x,y).
 *
 * Spawn dots are the reason this is worth more than a coloured blob: where the
 * seats are, and how far apart, is most of what tells you whether a map is a
 * knife fight or a long game — and it is exactly what a name and a cell count
 * cannot say.
 */
export function drawMapThumbnail(
  ctx: CanvasRenderingContext2D, x: number, y: number, size: number,
  m: CustomMap, opts: ThumbOpts = {},
) {
  const step = Math.max(1, Math.floor(m.cols / size));
  const px = size / Math.ceil(m.cols / step);
  for (let cy = 0, ry = 0; cy < m.rows; cy += step, ry++) {
    for (let cx = 0, rx = 0; cx < m.cols; cx += step, rx++) {
      ctx.fillStyle = MAP_SWATCH[m.terrain[cy * m.cols + cx]] ?? "#6f9c4a";
      ctx.fillRect(x + rx * px, y + ry * px, Math.ceil(px), Math.ceil(px));
    }
  }
  const scale = size / (m.cols * TILE);
  if (opts.resources) {
    for (const r of m.resources) {
      ctx.fillStyle = r.type === "gold_mine" ? "#e0b83a" : r.type === "berries" ? "#c8425a" : "#2f6b32";
      ctx.fillRect(x + r.x * scale - 0.5, y + r.y * scale - 0.5, 1.5, 1.5);
    }
  }
  if (opts.spawns) {
    m.spawns.forEach((s, i) => {
      const sx = x + s.x * scale, sy = y + s.y * scale;
      const col = PAL.teams[i % PAL.teams.length].main;
      ctx.fillStyle = col;
      ctx.beginPath(); ctx.arc(sx, sy, Math.max(2, size * 0.045), 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = "rgba(0,0,0,0.65)"; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.arc(sx, sy, Math.max(2, size * 0.045), 0, Math.PI * 2); ctx.stroke();
    });
  }
  ctx.strokeStyle = "rgba(255,255,255,0.14)";
  ctx.lineWidth = 1;
  ctx.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
}
