import { NavGrid } from "./grid";

// A* on the nav grid for single-unit pathing. Returns a list of world-space
// waypoints (flattened [x0,y0,x1,y1,...]) or null if unreachable. Includes a
// simple line-of-sight smoothing pass so paths don't hug every cell corner.

const DIRS = [
  [1, 0, 1],
  [-1, 0, 1],
  [0, 1, 1],
  [0, -1, 1],
  [1, 1, 1.4142],
  [1, -1, 1.4142],
  [-1, 1, 1.4142],
  [-1, -1, 1.4142],
];

class MinHeap {
  private items: { f: number; i: number }[] = [];
  get size() {
    return this.items.length;
  }
  push(i: number, f: number) {
    const a = this.items;
    a.push({ f, i });
    let c = a.length - 1;
    while (c > 0) {
      const p = (c - 1) >> 1;
      if (a[p].f <= a[c].f) break;
      [a[p], a[c]] = [a[c], a[p]];
      c = p;
    }
  }
  pop(): number {
    const a = this.items;
    const top = a[0].i;
    const last = a.pop()!;
    if (a.length) {
      a[0] = last;
      let p = 0;
      for (;;) {
        const l = 2 * p + 1;
        const r = 2 * p + 2;
        let s = p;
        if (l < a.length && a[l].f < a[s].f) s = l;
        if (r < a.length && a[r].f < a[s].f) s = r;
        if (s === p) break;
        [a[p], a[s]] = [a[s], a[p]];
        p = s;
      }
    }
    return top;
  }
}

export function findPath(
  grid: NavGrid,
  sx: number,
  sy: number,
  tx: number,
  ty: number,
  maxNodes = 6000,
): number[] | null {
  let scx = grid.worldToCellX(sx);
  let scy = grid.worldToCellY(sy);
  let tcx = grid.worldToCellX(tx);
  let tcy = grid.worldToCellY(ty);

  if (!grid.inBounds(scx, scy)) return null;
  // If we're sitting on a blocked cell (shoved onto a footprint, spawned tight),
  // step out to the nearest open cell so the search has somewhere to begin.
  if (grid.isBlocked(scx, scy)) {
    const [owx, owy] = grid.nearestOpenWorld(sx, sy);
    scx = grid.worldToCellX(owx);
    scy = grid.worldToCellY(owy);
  }
  // If the goal cell is blocked, retarget to the nearest open cell.
  if (grid.isBlocked(tcx, tcy)) {
    const [owx, owy] = grid.nearestOpenWorld(tx, ty);
    tcx = grid.worldToCellX(owx);
    tcy = grid.worldToCellY(owy);
  }
  if (scx === tcx && scy === tcy) return [tx, ty];

  const n = grid.cols * grid.rows;
  const g = new Float32Array(n).fill(Infinity);
  const came = new Int32Array(n).fill(-1);
  const closed = new Uint8Array(n);
  const open = new MinHeap();

  const h = (cx: number, cy: number) => {
    const dx = Math.abs(cx - tcx);
    const dy = Math.abs(cy - tcy);
    return (dx + dy) + (1.4142 - 2) * Math.min(dx, dy);
  };

  const start = grid.idx(scx, scy);
  g[start] = 0;
  open.push(start, h(scx, scy));
  const goal = grid.idx(tcx, tcy);
  let expanded = 0;

  while (open.size > 0) {
    const cur = open.pop();
    if (cur === goal) return reconstruct(grid, came, cur, tx, ty);
    if (closed[cur]) continue;
    closed[cur] = 1;
    if (++expanded > maxNodes) break;
    const ccx = cur % grid.cols;
    const ccy = (cur / grid.cols) | 0;
    for (const [dx, dy, cost] of DIRS) {
      const nx = ccx + dx;
      const ny = ccy + dy;
      if (grid.isBlocked(nx, ny)) continue;
      // Prevent diagonal corner cutting through two blocked orthogonals.
      if (dx !== 0 && dy !== 0) {
        if (grid.isBlocked(ccx + dx, ccy) && grid.isBlocked(ccx, ccy + dy)) continue;
      }
      const ni = grid.idx(nx, ny);
      if (closed[ni]) continue;
      const ng = g[cur] + cost;
      if (ng < g[ni]) {
        g[ni] = ng;
        came[ni] = cur;
        open.push(ni, ng + h(nx, ny));
      }
    }
  }
  return null;
}

function reconstruct(grid: NavGrid, came: Int32Array, end: number, tx: number, ty: number): number[] {
  const cells: number[] = [];
  let c = end;
  while (c !== -1) {
    cells.push(c);
    c = came[c];
  }
  cells.reverse();
  // Convert to world coords, then smooth via line-of-sight.
  const pts: number[] = [];
  for (const ci of cells) {
    const cx = ci % grid.cols;
    const cy = (ci / grid.cols) | 0;
    pts.push(grid.cellCenterX(cx), grid.cellCenterY(cy));
  }
  // Replace last point with the actual requested target for precision.
  if (pts.length >= 2) {
    pts[pts.length - 2] = tx;
    pts[pts.length - 1] = ty;
  }
  return smooth(grid, pts);
}

/** Drop intermediate waypoints that have clear line of sight. */
function smooth(grid: NavGrid, pts: number[]): number[] {
  if (pts.length <= 4) return pts;
  const out: number[] = [pts[0], pts[1]];
  let anchorX = pts[0];
  let anchorY = pts[1];
  for (let i = 2; i < pts.length - 2; i += 2) {
    const nextX = pts[i + 2];
    const nextY = pts[i + 3];
    if (!lineClear(grid, anchorX, anchorY, nextX, nextY)) {
      out.push(pts[i], pts[i + 1]);
      anchorX = pts[i];
      anchorY = pts[i + 1];
    }
  }
  out.push(pts[pts.length - 2], pts[pts.length - 1]);
  return out;
}

/**
 * Clear line of sight between two world points, keeping a perpendicular
 * `clearance` band free so smoothed paths don't shave building corners (which
 * leaves units snagging on edges). A wider band = units route with more berth.
 */
export function lineClear(
  grid: NavGrid,
  x0: number, y0: number,
  x1: number, y1: number,
  clearance = 11,
): boolean {
  const dx = x1 - x0;
  const dy = y1 - y0;
  const len = Math.hypot(dx, dy) || 1;
  const steps = Math.ceil(len / 12);
  // Unit perpendicular, scaled to the clearance we want on either side.
  const px = (-dy / len) * clearance;
  const py = (dx / len) * clearance;
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const cx = x0 + dx * t;
    const cy = y0 + dy * t;
    if (grid.isBlockedWorld(cx, cy)) return false;
    if (grid.isBlockedWorld(cx + px, cy + py)) return false;
    if (grid.isBlockedWorld(cx - px, cy - py)) return false;
  }
  return true;
}
