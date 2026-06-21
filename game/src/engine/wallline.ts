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
