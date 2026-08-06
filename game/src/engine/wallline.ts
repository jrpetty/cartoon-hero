// Cells a drag-painted wall should occupy, from the start cursor cell to the end
// cursor cell. Uses an integer grid line-walk (Bresenham) so the run is gap-free
// and reaches the exact endpoint — the old "sample every TILE along the diagonal
// distance" approach under-sampled angled drags, leaving holes and stopping short.

export interface WallCell { x: number; y: number; }

export function wallLinePoints(
  wx0: number, wy0: number, wx1: number, wy1: number, tile: number, maxCells = 256,
): WallCell[] {
  let x0 = Math.round(wx0 / tile);
  let y0 = Math.round(wy0 / tile);
  const x1 = Math.round(wx1 / tile);
  const y1 = Math.round(wy1 / tile);
  const dx = Math.abs(x1 - x0);
  const dy = Math.abs(y1 - y0);
  const sx = x0 < x1 ? 1 : -1;
  const sy = y0 < y1 ? 1 : -1;
  let err = dx - dy;
  const out: WallCell[] = [];
  for (let guard = 0; guard <= maxCells; guard++) {
    out.push({ x: x0 * tile + tile / 2, y: y0 * tile + tile / 2 });
    if (x0 === x1 && y0 === y1) break;
    const e2 = 2 * err;
    if (e2 > -dy) { err -= dy; x0 += sx; }
    if (e2 < dx) { err += dx; y0 += sy; }
  }
  return out;
}

/**
 * Snapped centres filling the dragged rectangle with a block of `tiles`-wide
 * buildings, in reading order.
 *
 * A wall wants to be a gap-free line; a block of houses wants the opposite. Each
 * building is spaced one tile clear of its neighbour so villagers can still walk
 * between them and so the whole block doesn't become an accidental wall — laying
 * twelve houses that seal your own base in is not what anyone means by "drag a
 * block of houses".
 *
 * Reading order matters: builders are handed out round-robin along this list, so
 * a predictable left-to-right, top-to-bottom sequence keeps each villager
 * working a contiguous stretch instead of criss-crossing the site.
 */
export function blockPoints(
  wx0: number, wy0: number, wx1: number, wy1: number,
  tile: number, tiles: number, maxCells = 64,
): WallCell[] {
  const pitch = (tiles + 1) * tile; // footprint plus a one-tile walking gap
  // Odd footprints sit on a tile centre, even ones on a tile corner — the same
  // rule placeBuilding snaps to, so the preview matches what actually gets built.
  const half = tiles % 2 === 1 ? tile / 2 : 0;
  const snap = (v: number) => Math.round((v - half) / tile) * tile + half;
  const ax = snap(Math.min(wx0, wx1)), ay = snap(Math.min(wy0, wy1));
  const bx = snap(Math.max(wx0, wx1)), by = snap(Math.max(wy0, wy1));
  const cols = Math.max(1, Math.floor((bx - ax) / pitch) + 1);
  const rows = Math.max(1, Math.floor((by - ay) / pitch) + 1);
  const out: WallCell[] = [];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      if (out.length >= maxCells) return out;
      out.push({ x: ax + c * pitch, y: ay + r * pitch });
    }
  }
  return out;
}
