import { describe, expect, it } from "vitest";
import { wallLinePoints, blockPoints } from "./wallline";

const TILE = 32;
const cell = (p: { x: number; y: number }) => [Math.floor(p.x / TILE), Math.floor(p.y / TILE)] as const;

describe("wallLinePoints", () => {
  it("fills a straight horizontal run end-to-end with no gaps", () => {
    const pts = wallLinePoints(0, 0, 10 * TILE, 0, TILE);
    expect(pts.length).toBe(11); // 0..10 inclusive
    const xs = pts.map((p) => cell(p)[0]);
    expect(xs).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]); // contiguous, reaches the end
  });

  it("reaches the exact endpoint cell on a diagonal (the old bug stopped short)", () => {
    const pts = wallLinePoints(0, 0, 8 * TILE, 5 * TILE, TILE);
    expect(cell(pts[0])).toEqual([0, 0]);
    expect(cell(pts[pts.length - 1])).toEqual([8, 5]); // full length to where you dragged
  });

  it("leaves no gaps — every consecutive cell is adjacent", () => {
    for (const [ex, ey] of [[8, 5], [3, 11], [-7, 4], [12, -2], [0, 9]] as const) {
      const pts = wallLinePoints(0, 0, ex * TILE, ey * TILE, TILE);
      for (let i = 1; i < pts.length; i++) {
        const [ax, ay] = cell(pts[i - 1]);
        const [bx, by] = cell(pts[i]);
        const step = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        expect(step).toBe(1); // never jumps over a cell
      }
      expect(cell(pts[pts.length - 1])).toEqual([ex, ey]); // and always reaches the end
    }
  });

  it("a single click yields one segment", () => {
    // Started from mid-cell on purpose. The original version started exactly on
    // a cell boundary and moved 2px *up*, which genuinely crosses into the cell
    // above — the old rounding hid that by pulling both ends to the nearest
    // boundary, which is the same thing that made a dragged run land a cell off
    // the line the player dragged.
    const pts = wallLinePoints(5.5 * TILE, 5.5 * TILE, 5.5 * TILE + 3, 5.5 * TILE - 2, TILE);
    expect(pts.length).toBe(1);
    expect(cell(pts[0])).toEqual([5, 5]);
  });

  it("counts a drag that crosses a cell boundary as two", () => {
    // The honest reading of a two-pixel move that happens to straddle a line.
    const pts = wallLinePoints(5 * TILE, 5 * TILE, 5 * TILE, 5 * TILE - 2, TILE);
    expect(pts.map(cell)).toEqual([[5, 5], [5, 4]]);
  });

  it("caps absurdly long drags", () => {
    const pts = wallLinePoints(0, 0, 100000, 0, TILE, 64);
    expect(pts.length).toBeLessThanOrEqual(65);
  });
});

describe("Dragging out a block of buildings", () => {

  it("spaces them a tile apart so the block isn't an accidental wall", () => {
    // 2-tile footprints at a 3-tile pitch: 96 world units between centres, of
    // which 64 is building and 32 is walking room.
    const pts = blockPoints(0, 0, TILE * 9, 0, TILE, 2);
    expect(pts.length).toBeGreaterThan(1);
    for (let i = 1; i < pts.length; i++) {
      expect(pts[i].x - pts[i - 1].x).toBe(3 * TILE);
    }
  });

  it("fills the rectangle in reading order", () => {
    const pts = blockPoints(0, 0, TILE * 6, TILE * 6, TILE, 2);
    expect(pts.length).toBe(9); // 3x3
    // Left to right, then top to bottom — builders are handed out along this
    // list, so the order decides whether they work a stretch or criss-cross.
    for (let i = 1; i < pts.length; i++) {
      const a = pts[i - 1], b = pts[i];
      expect(b.y > a.y || (b.y === a.y && b.x > a.x)).toBe(true);
    }
  });

  it("always yields at least one placement, however small the drag", () => {
    const pts = blockPoints(100, 100, 101, 101, TILE, 2);
    expect(pts).toHaveLength(1);
  });

  it("snaps odd and even footprints the way placeBuilding does", () => {
    // Even footprints land on tile corners, odd ones on tile centres. If these
    // disagreed, the ghost preview would sit half a tile off what gets built.
    const even = blockPoints(50, 50, 50, 50, TILE, 2)[0];
    expect(even.x % TILE).toBe(0);
    const odd = blockPoints(50, 50, 50, 50, TILE, 3)[0];
    expect(Math.abs(odd.x % TILE)).toBe(TILE / 2);
  });

  it("caps a runaway drag rather than queueing hundreds of foundations", () => {
    const pts = blockPoints(0, 0, TILE * 400, TILE * 400, TILE, 2, 64);
    expect(pts).toHaveLength(64);
  });
});
