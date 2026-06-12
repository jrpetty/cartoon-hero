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
    if (this.inBounds(cx, cy)) this.blocked[this.idx(cx, cy)] = v ? 1 : 0;
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
