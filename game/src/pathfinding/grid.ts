import { TILE } from "../content/balance";

// Navigation grid. Each cell is passable or blocked (by buildings, resource
// nodes, water/cliffs). Units path on this grid; buildings stamp footprints.

export class NavGrid {
  cols: number;
  rows: number;
  blocked: Uint8Array; // 1 = impassable terrain/building
  // A separate "static cost" could go here; we keep it simple.

  constructor(public worldW: number, public worldH: number) {
    this.cols = Math.ceil(worldW / TILE);
    this.rows = Math.ceil(worldH / TILE);
    this.blocked = new Uint8Array(this.cols * this.rows);
  }

  idx(cx: number, cy: number): number {
    return cy * this.cols + cx;
  }

  inBounds(cx: number, cy: number): boolean {
    return cx >= 0 && cy >= 0 && cx < this.cols && cy < this.rows;
  }

  worldToCellX(wx: number): number {
    return Math.floor(wx / TILE);
  }
  worldToCellY(wy: number): number {
    return Math.floor(wy / TILE);
  }
  cellCenterX(cx: number): number {
    return cx * TILE + TILE / 2;
  }
  cellCenterY(cy: number): number {
    return cy * TILE + TILE / 2;
  }

  isBlocked(cx: number, cy: number): boolean {
    if (!this.inBounds(cx, cy)) return true;
    return this.blocked[this.idx(cx, cy)] === 1;
  }

  isBlockedWorld(wx: number, wy: number): boolean {
    return this.isBlocked(this.worldToCellX(wx), this.worldToCellY(wy));
  }

  setBlocked(cx: number, cy: number, v: boolean) {
    if (!this.inBounds(cx, cy)) return;
    const i = this.idx(cx, cy);
    const next = v ? 1 : 0;
    if (this.blocked[i] === next) return;
    this.blocked[i] = next;
    this.regionsDirty = true;
  }

  // ---- connected components ------------------------------------------------
  // Which cells can reach which. Two cells with different labels have no route
  // between them at all, and A* can say so in constant time instead of
  // exhausting its node budget to find out. That mattered: on an eight-player
  // map nearly two in five path requests were to a target walled off behind
  // water or a building line, and each one scanned six thousand cells before
  // giving up — which was, by a wide margin, the most expensive thing the game
  // did. Labels are rebuilt lazily, and only after the grid actually changes.

  /** Component label per cell; -1 for blocked cells. Do not read directly. */
  private region: Int32Array | null = null;
  private regionsDirty = true;

  /** The component a cell belongs to, or -1 if it is blocked or out of bounds. */
  regionAt(cx: number, cy: number): number {
    if (!this.inBounds(cx, cy)) return -1;
    if (this.regionsDirty || !this.region) this.rebuildRegions();
    return this.region![this.idx(cx, cy)];
  }

  /**
   * True when the two cells provably have no route between them. Deliberately
   * *not* the inverse of "connected": if either end sits on a blocked cell the
   * answer is "don't know", because a unit shoved onto a footprint can still
   * walk out through its open neighbours, and that case has to keep taking the
   * full search exactly as it did before.
   */
  provablyUnreachable(ax: number, ay: number, bx: number, by: number): boolean {
    const ra = this.regionAt(ax, ay);
    if (ra < 0) return false;
    const rb = this.regionAt(bx, by);
    if (rb < 0) return false;
    return ra !== rb;
  }

  /** Flood-fill every open cell into a component. O(cells), run on demand. */
  private rebuildRegions() {
    const n = this.cols * this.rows;
    if (!this.region || this.region.length !== n) this.region = new Int32Array(n);
    const region = this.region;
    region.fill(-1);
    // One shared queue for the whole pass; each cell is enqueued once.
    const queue = new Int32Array(n);
    let label = 0;
    for (let seed = 0; seed < n; seed++) {
      if (this.blocked[seed] === 1 || region[seed] !== -1) continue;
      let head = 0, tail = 0;
      queue[tail++] = seed;
      region[seed] = label;
      while (head < tail) {
        const cur = queue[head++];
        const cx = cur % this.cols;
        const cy = (cur / this.cols) | 0;
        // Four-way only: A* may move diagonally, but never *between* two
        // blocked orthogonals, so a diagonal-only link is not a real route.
        if (cx > 0) { const i = cur - 1; if (this.blocked[i] === 0 && region[i] === -1) { region[i] = label; queue[tail++] = i; } }
        if (cx < this.cols - 1) { const i = cur + 1; if (this.blocked[i] === 0 && region[i] === -1) { region[i] = label; queue[tail++] = i; } }
        if (cy > 0) { const i = cur - this.cols; if (this.blocked[i] === 0 && region[i] === -1) { region[i] = label; queue[tail++] = i; } }
        if (cy < this.rows - 1) { const i = cur + this.cols; if (this.blocked[i] === 0 && region[i] === -1) { region[i] = label; queue[tail++] = i; } }
      }
      label++;
    }
    this.regionsDirty = false;
  }

  /** Stamp a tiles x tiles footprint centered (roughly) at a world position. */
  stampFootprint(wx: number, wy: number, tiles: number, v: boolean) {
    const c0x = this.worldToCellX(wx - (tiles * TILE) / 2);
    const c0y = this.worldToCellY(wy - (tiles * TILE) / 2);
    for (let y = 0; y < tiles; y++) {
      for (let x = 0; x < tiles; x++) {
        this.setBlocked(c0x + x, c0y + y, v);
      }
    }
  }

  /** Is a tiles x tiles footprint fully clear (for placement validation)? */
  footprintClear(wx: number, wy: number, tiles: number): boolean {
    const c0x = this.worldToCellX(wx - (tiles * TILE) / 2);
    const c0y = this.worldToCellY(wy - (tiles * TILE) / 2);
    for (let y = 0; y < tiles; y++) {
      for (let x = 0; x < tiles; x++) {
        if (this.isBlocked(c0x + x, c0y + y)) return false;
      }
    }
    return true;
  }

  /** Find the nearest passable cell to a world point (BFS spiral). */
  nearestOpenWorld(wx: number, wy: number, maxRadius = 12): [number, number] {
    let cx = this.worldToCellX(wx);
    let cy = this.worldToCellY(wy);
    cx = Math.max(0, Math.min(this.cols - 1, cx));
    cy = Math.max(0, Math.min(this.rows - 1, cy));
    if (!this.isBlocked(cx, cy)) return [this.cellCenterX(cx), this.cellCenterY(cy)];
    for (let r = 1; r <= maxRadius; r++) {
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (Math.abs(dx) !== r && Math.abs(dy) !== r) continue;
          if (!this.isBlocked(cx + dx, cy + dy)) {
            return [this.cellCenterX(cx + dx), this.cellCenterY(cy + dy)];
          }
        }
      }
    }
    return [wx, wy];
  }
}
