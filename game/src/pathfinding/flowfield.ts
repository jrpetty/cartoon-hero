import { NavGrid } from "./grid";

// Flow field for group movement. We run a Dijkstra/BFS from the goal across the
// whole reachable grid to get an integration (distance) field, then derive a
// per-cell direction pointing "downhill" toward the goal. A whole selection
// shares one field, so large armies flow around obstacles cleanly instead of
// each unit computing its own A* path (cheaper and visually better).

const NEI = [
  [1, 0, 1],
  [-1, 0, 1],
  [0, 1, 1],
  [0, -1, 1],
  [1, 1, 1.4142],
  [1, -1, 1.4142],
  [-1, 1, 1.4142],
  [-1, -1, 1.4142],
];

export class FlowField {
  cost: Float32Array; // integration field (distance-to-goal)
  dirX: Float32Array;
  dirY: Float32Array;
  goalX: number;
  goalY: number;

  constructor(private grid: NavGrid, goalWorldX: number, goalWorldY: number) {
    const n = grid.cols * grid.rows;
    this.cost = new Float32Array(n).fill(Infinity);
    this.dirX = new Float32Array(n);
    this.dirY = new Float32Array(n);
    this.goalX = goalWorldX;
    this.goalY = goalWorldY;
    this.build(goalWorldX, goalWorldY);
  }

  private build(gx: number, gy: number) {
    const grid = this.grid;
    let gcx = grid.worldToCellX(gx);
    let gcy = grid.worldToCellY(gy);
    if (grid.isBlocked(gcx, gcy)) {
      const [owx, owy] = grid.nearestOpenWorld(gx, gy);
      gcx = grid.worldToCellX(owx);
      gcy = grid.worldToCellY(owy);
    }
    if (!grid.inBounds(gcx, gcy)) return;

    // Dijkstra with a simple bucket-ish approach via a binary heap.
    const start = grid.idx(gcx, gcy);
    this.cost[start] = 0;
    const heap: { c: number; i: number }[] = [{ c: 0, i: start }];
    const push = (i: number, c: number) => {
      heap.push({ c, i });
      let ci = heap.length - 1;
      while (ci > 0) {
        const p = (ci - 1) >> 1;
        if (heap[p].c <= heap[ci].c) break;
        [heap[p], heap[ci]] = [heap[ci], heap[p]];
        ci = p;
      }
    };
    const pop = () => {
      const top = heap[0];
      const last = heap.pop()!;
      if (heap.length) {
        heap[0] = last;
        let p = 0;
        for (;;) {
          const l = 2 * p + 1;
          const r = 2 * p + 2;
          let s = p;
          if (l < heap.length && heap[l].c < heap[s].c) s = l;
          if (r < heap.length && heap[r].c < heap[s].c) s = r;
          if (s === p) break;
          [heap[p], heap[s]] = [heap[s], heap[p]];
          p = s;
        }
      }
      return top;
    };

    while (heap.length) {
      const { c, i } = pop();
      if (c > this.cost[i]) continue;
      const cx = i % grid.cols;
      const cy = (i / grid.cols) | 0;
      for (const [dx, dy, w] of NEI) {
        const nx = cx + dx;
        const ny = cy + dy;
        if (grid.isBlocked(nx, ny)) continue;
        if (dx !== 0 && dy !== 0) {
          if (grid.isBlocked(cx + dx, cy) && grid.isBlocked(cx, cy + dy)) continue;
        }
        const ni = grid.idx(nx, ny);
        const nc = c + w;
        if (nc < this.cost[ni]) {
          this.cost[ni] = nc;
          push(ni, nc);
        }
      }
    }

    // Derive directions: point toward the lowest-cost neighbor.
    for (let cy = 0; cy < grid.rows; cy++) {
      for (let cx = 0; cx < grid.cols; cx++) {
        const i = grid.idx(cx, cy);
        if (this.cost[i] === Infinity) continue;
        let best = this.cost[i];
        let bx = 0;
        let by = 0;
        for (const [dx, dy] of NEI) {
          const nx = cx + dx;
          const ny = cy + dy;
          if (!grid.inBounds(nx, ny)) continue;
          const nc = this.cost[grid.idx(nx, ny)];
          if (nc < best) {
            best = nc;
            bx = dx;
            by = dy;
          }
        }
        const len = Math.hypot(bx, by) || 1;
        this.dirX[i] = bx / len;
        this.dirY[i] = by / len;
      }
    }
  }

  /** Sample the field at a world position; returns a unit direction (0,0 if at goal/unreachable). */
  sample(wx: number, wy: number): [number, number] {
    const cx = this.grid.worldToCellX(wx);
    const cy = this.grid.worldToCellY(wy);
    if (!this.grid.inBounds(cx, cy)) return [0, 0];
    const i = this.grid.idx(cx, cy);
    return [this.dirX[i], this.dirY[i]];
  }

  reached(wx: number, wy: number): boolean {
    const cx = this.grid.worldToCellX(wx);
    const cy = this.grid.worldToCellY(wy);
    if (!this.grid.inBounds(cx, cy)) return false;
    return this.cost[this.grid.idx(cx, cy)] < 1.5;
  }
}
