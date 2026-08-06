import { NavGrid } from "./grid";

// A* on the nav grid for single-unit pathing. Returns a list of world-space
// waypoints (flattened [x0,y0,x1,y1,...]) or null if unreachable. Includes a
// simple line-of-sight smoothing pass so paths don't hug every cell corner.

// Neighbour offsets, flattened. The order matters: it decides the order equal-
// cost nodes enter the heap, which decides which of several equally short paths
// a unit takes. Keep it as it is unless you mean to change every path.
const DX = [1, -1, 0, 0, 1, 1, -1, -1];
const DY = [0, 0, 1, -1, 1, -1, 1, -1];
const DC = [1, 1, 1, 1, 1.4142, 1.4142, 1.4142, 1.4142];

/**
 * Scratch state, reused across every search.
 *
 * A* used to allocate three arrays the size of the whole nav grid on each call
 * and fill two of them — about 190KB of allocation and 21,000 writes per path
 * request, whether the path turned out to be five cells long or five hundred.
 * On an eight-player match that was the single largest cost in the game, and
 * most of the garbage collector's work as well.
 *
 * Instead the buffers live here and are stamped with a generation counter: an
 * entry counts as "set this search" only when its stamp matches, so starting a
 * search is O(1) instead of O(cells). The buffers only ever grow, so alternating
 * between maps of different sizes doesn't churn them.
 */
let gScore = new Float32Array(0);
let gStamp = new Uint32Array(0);
let cameFrom = new Int32Array(0);
let closedStamp = new Uint32Array(0);
let heapF = new Float64Array(0);
let heapI = new Int32Array(0);
let heapLen = 0;
let generation = 0;

function ensureCapacity(n: number) {
  if (gScore.length >= n) return;
  gScore = new Float32Array(n);
  gStamp = new Uint32Array(n);
  cameFrom = new Int32Array(n);
  closedStamp = new Uint32Array(n);
  generation = 0; // fresh (zeroed) stamps, so restart the counter
}

/**
 * The open list is *not* bounded by the number of cells: a cell is pushed again
 * every time a cheaper route to it is found, and the stale copies are discarded
 * on pop. Sizing the heap to the grid and trusting it looked right and silently
 * dropped pushes — typed arrays ignore out-of-range writes — which quietly
 * produced worse paths. So it grows on demand instead.
 */
function growHeap() {
  const cap = Math.max(1024, heapF.length * 2);
  const nf = new Float64Array(cap); nf.set(heapF); heapF = nf;
  const ni = new Int32Array(cap); ni.set(heapI); heapI = ni;
}

/**
 * Binary min-heap over two parallel typed arrays. The comparisons and the order
 * of swaps are exactly those of the object-array heap this replaces, so the pop
 * order — and therefore every path the game produces — is unchanged.
 */
function heapPush(i: number, f: number) {
  if (heapLen >= heapF.length) growHeap();
  let c = heapLen++;
  heapF[c] = f;
  heapI[c] = i;
  while (c > 0) {
    const p = (c - 1) >> 1;
    if (heapF[p] <= heapF[c]) break;
    const tf = heapF[p], ti = heapI[p];
    heapF[p] = heapF[c]; heapI[p] = heapI[c];
    heapF[c] = tf; heapI[c] = ti;
    c = p;
  }
}

function heapPop(): number {
  const top = heapI[0];
  const lastF = heapF[--heapLen], lastI = heapI[heapLen];
  if (heapLen > 0) {
    heapF[0] = lastF; heapI[0] = lastI;
    let p = 0;
    for (;;) {
      const l = 2 * p + 1;
      const r = 2 * p + 2;
      let s = p;
      if (l < heapLen && heapF[l] < heapF[s]) s = l;
      if (r < heapLen && heapF[r] < heapF[s]) s = r;
      if (s === p) break;
      const tf = heapF[p], ti = heapI[p];
      heapF[p] = heapF[s]; heapI[p] = heapI[s];
      heapF[s] = tf; heapI[s] = ti;
      p = s;
    }
  }
  return top;
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
  // Nothing to search for if the two ends are in different pockets of the map.
  // The search would have expanded its whole node budget and returned null; on
  // an eight-player match that was two in five requests and almost all of the
  // pathfinder's cost.
  if (grid.provablyUnreachable(scx, scy, tcx, tcy)) return null;

  const cols = grid.cols, rows = grid.rows;
  const blocked = grid.blocked;
  const n = cols * rows;
  ensureCapacity(n);
  // Stamps live in a Uint32Array, so the counter has to stay inside it. Four
  // billion searches away, but a wrapped stamp would silently stop matching.
  if (generation >= 0xfffffffe) { gStamp.fill(0); closedStamp.fill(0); generation = 0; }
  const gen = ++generation;
  heapLen = 0;

  const start = scy * cols + scx;
  gScore[start] = 0;
  gStamp[start] = gen;
  cameFrom[start] = -1; // the chain terminator; nothing else needs clearing
  {
    const dx = Math.abs(scx - tcx), dy = Math.abs(scy - tcy);
    heapPush(start, (dx + dy) + (1.4142 - 2) * Math.min(dx, dy));
  }
  const goal = tcy * cols + tcx;
  let expanded = 0;

  while (heapLen > 0) {
    const cur = heapPop();
    if (cur === goal) return reconstruct(grid, cameFrom, cur, tx, ty);
    if (closedStamp[cur] === gen) continue;
    closedStamp[cur] = gen;
    if (++expanded > maxNodes) break;
    const ccx = cur % cols;
    const ccy = (cur / cols) | 0;
    const gCur = gScore[cur];
    for (let k = 0; k < 8; k++) {
      const nx = ccx + DX[k];
      const ny = ccy + DY[k];
      if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
      const ni = ny * cols + nx;
      if (blocked[ni] === 1) continue;
      // Prevent diagonal corner cutting through two blocked orthogonals.
      if (k >= 4) {
        const sideX = nx < 0 || nx >= cols || ccy < 0 || ccy >= rows || blocked[ccy * cols + nx] === 1;
        const sideY = ccx < 0 || ccx >= cols || ny < 0 || ny >= rows || blocked[ny * cols + ccx] === 1;
        if (sideX && sideY) continue;
      }
      if (closedStamp[ni] === gen) continue;
      const ng = gCur + DC[k];
      if (gStamp[ni] !== gen || ng < gScore[ni]) {
        gScore[ni] = ng;
        gStamp[ni] = gen;
        cameFrom[ni] = cur;
        const dx = Math.abs(nx - tcx), dy = Math.abs(ny - tcy);
        // Group the heuristic exactly as the old `h()` did. Floating-point
        // addition is not associative, so `ng + (a + b)` and `(ng + a) + b`
        // disagree in the last bit — which is enough to break ties differently
        // and send units down a different (equally short) path.
        const hn = (dx + dy) + (1.4142 - 2) * Math.min(dx, dy);
        heapPush(ni, ng + hn);
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
