// Uniform spatial hash for fast neighbor queries (targeting, separation,
// selection). Rebuilt each tick — cheap at our entity counts and avoids
// incremental bookkeeping bugs.

export interface SpatialItem {
  x: number;
  y: number;
  alive: boolean;
}

export class SpatialGrid {
  private cells: Map<number, SpatialItem[]> = new Map();
  constructor(private cellSize = 96) {}

  private key(cx: number, cy: number): number {
    return (cx & 0xffff) | ((cy & 0xffff) << 16);
  }

  clear() {
    this.cells.clear();
  }

  insert(item: SpatialItem) {
    const cx = Math.floor(item.x / this.cellSize);
    const cy = Math.floor(item.y / this.cellSize);
    const k = this.key(cx, cy);
    let arr = this.cells.get(k);
    if (!arr) {
      arr = [];
      this.cells.set(k, arr);
    }
    arr.push(item);
  }

  /** All items within `radius` of (x, y). Conservative (cell-rect) then exact filter. */
  query(x: number, y: number, radius: number): SpatialItem[] {
    const out: SpatialItem[] = [];
    const c0x = Math.floor((x - radius) / this.cellSize);
    const c0y = Math.floor((y - radius) / this.cellSize);
    const c1x = Math.floor((x + radius) / this.cellSize);
    const c1y = Math.floor((y + radius) / this.cellSize);
    const r2 = radius * radius;
    for (let cy = c0y; cy <= c1y; cy++) {
      for (let cx = c0x; cx <= c1x; cx++) {
        const arr = this.cells.get(this.key(cx, cy));
        if (!arr) continue;
        for (const it of arr) {
          if (!it.alive) continue;
          const dx = it.x - x;
          const dy = it.y - y;
          if (dx * dx + dy * dy <= r2) out.push(it);
        }
      }
    }
    return out;
  }
}
