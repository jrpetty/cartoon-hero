import { describe, expect, it, beforeAll } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { EditorScreen, symmetryCells } from "./editor_screen";
import { Terrain } from "../maps/terrain_kinds";
import {
  customFromGenerated, deserialiseMap, hasErrors, listCustomMaps, newCustomMap, serialiseMap, validateMap,
} from "../maps/custom";


beforeAll(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

describe("Symmetry", () => {
  it("paints one cell when free", () => {
    expect(symmetryCells("none", 3, 4, 20, 20)).toEqual([[3, 4]]);
  });

  it("mirrors across each axis, and both at once", () => {
    expect(symmetryCells("mirrorX", 3, 4, 20, 20)).toEqual([[3, 4], [16, 4]]);
    expect(symmetryCells("mirrorY", 3, 4, 20, 20)).toEqual([[3, 4], [3, 15]]);
    expect(symmetryCells("rot180", 3, 4, 20, 20)).toEqual([[3, 4], [16, 15]]);
    expect(symmetryCells("quad", 3, 4, 20, 20)).toEqual([[3, 4], [16, 4], [3, 15], [16, 15]]);
  });

  it("puts one cell in each slice for radial symmetry", () => {
    // Inside the circle that fits the square: rotating any of these stays on
    // the map. A corner would not, which is geometry rather than a bug.
    for (const [sym, n] of [["radial3", 3], ["radial6", 6], ["radial8", 8]] as const) {
      const cells = symmetryCells(sym, 20, 8, 41, 41);
      expect(cells.length, sym).toBe(n);
      // Every copy is the same distance from the centre — that is what makes
      // the seats equal rather than merely numerous.
      const o = 20;
      const r0 = Math.hypot(20 - o, 8 - o);
      // Cells are integers, so a rotated copy lands within about a cell of the
      // exact radius. Anything further would mean the seats are not equal.
      for (const [cx, cy] of cells) {
        expect(Math.abs(Math.hypot(cx - o, cy - o) - r0), `${sym} ${cx},${cy}`).toBeLessThan(1.2);
      }
    }
  });

  it("drops the copies that would rotate off a square map, rather than clamping", () => {
    // Clamping would pile several seats onto one edge cell, which is worse
    // than simply having fewer of them.
    const cells = symmetryCells("radial8", 1, 1, 41, 41);
    for (const [cx, cy] of cells) {
      expect(cx >= 0 && cx < 41 && cy >= 0 && cy < 41).toBe(true);
    }
    expect(cells.length).toBeLessThan(8);
  });

  it("never returns a duplicate or a cell off the map", () => {
    for (const sym of ["mirrorX", "mirrorY", "rot180", "quad", "radial3", "radial6", "radial8"] as const) {
      for (const [cx, cy] of [[0, 0], [19, 19], [10, 10], [0, 19]] as const) {
        const cells = symmetryCells(sym, cx, cy, 20, 20);
        const seen = new Set<string>();
        for (const [x, y] of cells) {
          expect(x >= 0 && y >= 0 && x < 20 && y < 20, `${sym} ${x},${y}`).toBe(true);
          expect(seen.has(`${x},${y}`)).toBe(false);
          seen.add(`${x},${y}`);
        }
      }
    }
  });

  it("keeps the centre cell to itself under rotation", () => {
    // Painting the exact centre must not produce eight copies of one cell.
    expect(symmetryCells("radial8", 10, 10, 21, 21)).toEqual([[10, 10]]);
  });
});

describe("Editor", () => {
  const W = 1600, H = 900;
  const harness = () => {
    const canvas = createCanvas(W, H);
    const ctx = canvas.getContext("2d") as unknown as CanvasRenderingContext2D;
    const s = new EditorScreen();
    let t = 0;
    const frame = (o: Partial<{ mx: number; my: number; clicked: boolean; down: boolean; right: boolean; wheel: number }> = {}) => {
      t += 1 / 60;
      ui.begin(ctx, {
        mx: o.mx ?? -99, my: o.my ?? -99, clicked: !!o.clicked, rightClicked: false, alt: false,
        leftHeld: !!o.down, rightHeld: !!o.right, ctrlHeld: false, shiftHeld: false, wheel: o.wheel ?? 0,
      });
      return s.draw(W, H, t, !!o.down);
    };
    return { s, frame, canvas };
  };

  const mapOf = (s: EditorScreen) => (s as unknown as { map: unknown }).map as {
    terrain: Uint8Array; cols: number; rows: number; spawns: unknown[]; resources: unknown[]; name: string;
  } | null;

  it("opens the library, creates a map, and paints with symmetry", () => {
    const { s, frame } = harness();
    expect(() => frame()).not.toThrow();
    expect(mapOf(s)).toBeNull();

    // Click "Create" — it sits at the right of the New Map panel.
    const colW = Math.min(980, W - 80);
    const x0 = Math.round(W / 2 - colW / 2);
    frame({ mx: x0 + colW - 80, my: 122 + 82, clicked: true });
    frame();
    const m = mapOf(s)!;
    expect(m).not.toBeNull();
    expect(m.cols).toBe(128);

    // Paint in the middle of the canvas with the default 180° symmetry.
    const before = [...m.terrain];
    (s as unknown as { paint: number }).paint = Terrain.Rock;
    const cx = 214 + (W - 214 - 300) / 2 - 120;
    const cy = 44 + (H - 96) / 2 - 60;
    frame({ mx: cx, my: cy, down: true });
    frame({ mx: cx, my: cy, down: true });
    const after = [...mapOf(s)!.terrain];
    const changed = after.filter((v, i) => v !== before[i]).length;
    expect(changed, "nothing was painted").toBeGreaterThan(0);
    // 180° symmetry means the stroke landed in two places, not one.
    const rockCells: number[] = [];
    after.forEach((v, i) => { if (v === Terrain.Rock) rockCells.push(i); });
    const cols = m.cols, rows = m.rows;
    for (const i of rockCells) {
      const px = i % cols, py = (i / cols) | 0;
      const mirror = (rows - 1 - py) * cols + (cols - 1 - px);
      expect(after[mirror], "the mirrored half was not painted").toBe(Terrain.Rock);
    }
  });

  it("undoes a stroke and redoes it", () => {
    const { s, frame } = harness();
    const colW = Math.min(980, W - 80);
    const x0 = Math.round(W / 2 - colW / 2);
    frame({ mx: x0 + colW - 80, my: 122 + 82, clicked: true });
    frame();
    (s as unknown as { paint: number }).paint = Terrain.Water;
    const cx = 214 + (W - 214 - 300) / 2, cy = 44 + (H - 96) / 2;
    frame({ mx: cx, my: cy, down: true });
    frame({ mx: -99, my: -99 }); // release, closing the stroke
    const painted = [...mapOf(s)!.terrain].filter((v) => v === Terrain.Water).length;
    expect(painted).toBeGreaterThan(0);
    (s as unknown as { undoStep(): void }).undoStep();
    expect([...mapOf(s)!.terrain].filter((v) => v === Terrain.Water).length).toBe(0);
    (s as unknown as { redoStep(): void }).redoStep();
    expect([...mapOf(s)!.terrain].filter((v) => v === Terrain.Water).length).toBe(painted);
  });

  it("takes the spawn tool away on an always-nomad map", () => {
    const { s, frame } = harness();
    const colW = Math.min(980, W - 80);
    const x0 = Math.round(W / 2 - colW / 2);
    frame({ mx: x0 + colW - 80, my: 122 + 82, clicked: true });
    frame();
    const priv = s as unknown as { tool: string; map: { nomad: string; spawns: unknown[] } };
    priv.tool = "spawn";
    priv.map.nomad = "forced";
    frame(); // the rail notices and moves the tool off spawns
    (s as unknown as { placeSpawn(cx: number, cy: number): void }).placeSpawn(10, 10);
    expect(priv.map.spawns.length, "a spawn was placed on an always-nomad map").toBe(0);
  });

  it("types a name a character at a time", () => {
    const { s, frame } = harness();
    const colW = Math.min(980, W - 80);
    const x0 = Math.round(W / 2 - colW / 2);
    frame({ mx: x0 + colW - 80, my: 122 + 82, clicked: true });
    frame();
    (s as unknown as { editing: string }).editing = "name";
    for (const ch of "Ford") expect(s.handleKey(ch)).toBe(true);
    expect(mapOf(s)!.name).toBe("New MapFord");
    expect(s.handleKey("Backspace")).toBe(true);
    expect(mapOf(s)!.name).toBe("New MapFor");
    expect(s.handleKey("Enter")).toBe(true);
    expect(s.handleKey("x")).toBe(false); // no longer typing
  });

  it("saves on close, so a map is never lost by leaving", () => {
    const { s, frame } = harness();
    const colW = Math.min(980, W - 80);
    const x0 = Math.round(W / 2 - colW / 2);
    frame({ mx: x0 + colW - 80, my: 122 + 82, clicked: true });
    frame();
    const id = (mapOf(s) as unknown as { id: string }).id;
    frame({ mx: W - 118 + 39, my: 22, clicked: true }); // Close
    expect(mapOf(s)).toBeNull();
    expect(listCustomMaps().some((m) => m.id === id)).toBe(true);
  });

  it("renders every tool without throwing", () => {
    const { s, frame } = harness();
    const colW = Math.min(980, W - 80);
    const x0 = Math.round(W / 2 - colW / 2);
    frame({ mx: x0 + colW - 80, my: 122 + 82, clicked: true });
    for (const tool of ["terrain", "path", "scatter", "region", "resource", "spawn", "erase"]) {
      (s as unknown as { tool: string }).tool = tool;
      expect(() => frame({ mx: 100, my: 400 }), tool).not.toThrow();
    }
  });
});

// ---------------------------------------------------------- the new tools --
//
// These drive the tool methods directly rather than through synthetic drags.
// The click plumbing is already covered by the tests above; what is worth
// pinning here is what each tool does to the map, which is the part an author
// would notice going wrong.

interface ToolInternals {
  map: unknown;
  tool: string;
  paint: number;
  brush: number;
  sym: string;
  resource: string;
  scatterDensity: number;
  scatterSeed: number;
  clip: { w: number; h: number } | null;
  clipFlipX: boolean;
  clipFlipY: boolean;
  strokePath(x0: number, y0: number, x1: number, y1: number): void;
  scatterAt(cx: number, cy: number): void;
  copyRegion(x0: number, y0: number, x1: number, y1: number): void;
  pasteRegion(cx: number, cy: number): void;
}

function editing() {
  const s = new EditorScreen();
  const inner = s as unknown as ToolInternals;
  inner.map = newCustomMap("T", 64, "temperate");
  inner.sym = "none";
  return { s, inner, map: inner.map as { cols: number; rows: number; terrain: Uint8Array; resources: unknown[] } };
}

describe("The path tool", () => {
  it("lays a gap-free run between two points", () => {
    // The reason it exists: a river dragged with a round brush wobbles and can
    // leave holes on a steep diagonal, and a river with a hole in it is a ford
    // nobody meant to author.
    const { inner, map } = editing();
    inner.paint = Terrain.Water;
    inner.brush = 1;
    inner.strokePath(4, 4, 40, 22);
    let painted = 0;
    for (let cy = 0; cy < map.rows; cy++) {
      for (let cx = 0; cx < map.cols; cx++) if (map.terrain[cy * map.cols + cx] === Terrain.Water) painted++;
    }
    expect(painted).toBeGreaterThan(30);
    // Walk the line and check every step landed.
    for (let i = 0; i <= 36; i++) {
      const t = i / 36;
      const cx = Math.round(4 + 36 * t), cy = Math.round(4 + 18 * t);
      expect(map.terrain[cy * map.cols + cx], `hole at ${cx},${cy}`).toBe(Terrain.Water);
    }
  });

  it("handles a zero-length drag as a single dab", () => {
    const { inner, map } = editing();
    inner.paint = Terrain.Rock;
    inner.brush = 1;
    expect(() => inner.strokePath(10, 10, 10, 10)).not.toThrow();
    expect(map.terrain[10 * map.cols + 10]).toBe(Terrain.Rock);
  });

  it("gets its width from the brush, so a road and a river differ", () => {
    const wide = editing();
    wide.inner.paint = Terrain.Dirt;
    wide.inner.brush = 4;
    wide.inner.strokePath(10, 30, 50, 30);
    const thin = editing();
    thin.inner.paint = Terrain.Dirt;
    thin.inner.brush = 1;
    thin.inner.strokePath(10, 30, 50, 30);
    const count = (m: { terrain: Uint8Array }) =>
      [...m.terrain].filter((t) => t === Terrain.Dirt).length;
    expect(count(wide.map)).toBeGreaterThan(count(thin.map) * 2);
  });
});

describe("The scatter brush", () => {
  it("sprinkles many nodes from one dab instead of placing them one at a time", () => {
    const { inner, map } = editing();
    inner.resource = "tree";
    inner.brush = 8;
    inner.scatterDensity = 0.4;
    inner.scatterAt(30, 30);
    expect(map.resources.length).toBeGreaterThan(10);
  });

  it("thickens with density", () => {
    const sparse = editing();
    sparse.inner.resource = "tree"; sparse.inner.brush = 8; sparse.inner.scatterDensity = 0.1;
    sparse.inner.scatterAt(30, 30);
    const dense = editing();
    dense.inner.resource = "tree"; dense.inner.brush = 8; dense.inner.scatterDensity = 0.7;
    dense.inner.scatterAt(30, 30);
    expect(dense.map.resources.length).toBeGreaterThan(sparse.map.resources.length);
  });

  it("does not keep thickening when you drag back over the same ground", () => {
    // The pattern is a function of the cell, not a running random, so a second
    // pass over ground you already covered is a no-op rather than a pile-up.
    const { inner, map } = editing();
    inner.resource = "tree"; inner.brush = 6; inner.scatterDensity = 0.4;
    inner.scatterAt(30, 30);
    const once = map.resources.length;
    inner.scatterAt(30, 30);
    inner.scatterAt(30, 30);
    expect(map.resources.length).toBe(once);
  });

  it("never stacks two nodes on one cell", () => {
    const { inner, map } = editing();
    inner.resource = "tree"; inner.brush = 10; inner.scatterDensity = 0.8;
    inner.scatterAt(30, 30);
    inner.resource = "gold_mine";
    inner.scatterAt(30, 30);
    const cells = (map.resources as { x: number; y: number }[])
      .map((r) => `${Math.floor(r.x / 32)},${Math.floor(r.y / 32)}`);
    expect(new Set(cells).size).toBe(cells.length);
  });

  it("re-rolls to a different pattern on demand", () => {
    const a = editing();
    a.inner.resource = "tree"; a.inner.brush = 8; a.inner.scatterDensity = 0.4;
    a.inner.scatterAt(30, 30);
    const b = editing();
    b.inner.resource = "tree"; b.inner.brush = 8; b.inner.scatterDensity = 0.4;
    b.inner.scatterSeed = 999;
    b.inner.scatterAt(30, 30);
    const key = (m: { resources: unknown[] }) =>
      (m.resources as { x: number; y: number }[]).map((r) => `${r.x},${r.y}`).sort().join("|");
    expect(key(b.map)).not.toBe(key(a.map));
  });
});

describe("Copying a region", () => {
  it("lifts the ground and what stands on it, then stamps it elsewhere", () => {
    const { inner, map } = editing();
    // Author a small distinctive patch.
    inner.paint = Terrain.Rock;
    inner.brush = 1;
    for (let cy = 5; cy <= 9; cy++) for (let cx = 5; cx <= 9; cx++) inner.strokePath(cx, cy, cx, cy);
    inner.resource = "gold_mine";
    inner.brush = 2; inner.scatterDensity = 0.8;
    inner.scatterAt(7, 7);
    const nodesBefore = map.resources.length;

    inner.copyRegion(5, 5, 9, 9);
    expect(inner.clip).not.toBeNull();
    expect(inner.clip!.w).toBe(5);
    expect(inner.clip!.h).toBe(5);

    inner.pasteRegion(40, 40);
    for (let cy = 40; cy <= 44; cy++) {
      for (let cx = 40; cx <= 44; cx++) {
        expect(map.terrain[cy * map.cols + cx], `${cx},${cy}`).toBe(Terrain.Rock);
      }
    }
    expect(map.resources.length, "the stamp brought nothing with it")
      .toBeGreaterThan(nodesBefore);
  });

  it("mirrors a stamp, which is what you want facing the opposite seat", () => {
    const { inner, map } = editing();
    inner.paint = Terrain.Rock;
    inner.brush = 1;
    // An L, so a flip is detectable.
    inner.strokePath(5, 5, 9, 5);
    inner.strokePath(5, 5, 5, 7);
    inner.copyRegion(5, 5, 9, 7);
    inner.clipFlipX = true;
    inner.pasteRegion(40, 40);
    // The upright of the L was on the left; flipped, it is on the right.
    expect(map.terrain[42 * map.cols + 44]).toBe(Terrain.Rock);
    expect(map.terrain[42 * map.cols + 40]).not.toBe(Terrain.Rock);
  });

  it("clears what was already there rather than layering on top", () => {
    const { inner, map } = editing();
    // Somewhere to stamp onto that already has nodes.
    inner.resource = "tree"; inner.brush = 4; inner.scatterDensity = 0.8;
    inner.scatterAt(42, 42);
    const before = map.resources.length;
    expect(before).toBeGreaterThan(0);
    // An empty patch of plain ground.
    inner.copyRegion(2, 2, 8, 8);
    inner.pasteRegion(39, 39);
    const inside = (map.resources as { x: number; y: number }[]).filter((r) => {
      const cx = Math.floor(r.x / 32), cy = Math.floor(r.y / 32);
      return cx >= 39 && cx <= 45 && cy >= 39 && cy <= 45;
    });
    expect(inside, "old nodes survived under the stamp").toHaveLength(0);
  });

  it("clips a stamp at the map edge instead of wrapping or throwing", () => {
    const { inner, map } = editing();
    inner.paint = Terrain.Rock;
    inner.brush = 1;
    for (let cx = 5; cx <= 9; cx++) inner.strokePath(cx, 5, cx, 5);
    inner.copyRegion(5, 5, 9, 5);
    expect(() => inner.pasteRegion(map.cols - 2, map.rows - 2)).not.toThrow();
    // Nothing wrapped around to the far side.
    expect(map.terrain[(map.rows - 2) * map.cols + 0]).not.toBe(Terrain.Rock);
  });
});

describe("A map's description", () => {
  it("survives a round trip through a share code", () => {
    const m = newCustomMap("Described", 48, "temperate");
    m.desc = "A narrow pass with one gold in the middle.";
    const back = deserialiseMap(serialiseMap(m));
    expect(back?.desc).toBe(m.desc);
  });

  it("defaults to empty rather than undefined, so the lobby can trust it", () => {
    expect(newCustomMap("X", 40).desc).toBe("");
    const older = deserialiseMap(serialiseMap({ ...newCustomMap("Y", 40), desc: undefined as unknown as string }));
    expect(older?.desc).toBe("");
  });
});

describe("Starting from a generated map", () => {
  it("arrives painted, stocked and already seated", () => {
    // The whole point: a blank field is a lot of painting before there is
    // anything to react to.
    const m = customFromGenerated("highlands", 42, 4);
    expect(m.cols).toBeGreaterThan(0);
    expect(m.spawns, "no seats came with it").toHaveLength(4);
    expect(m.resources.length, "no resources came with it").toBeGreaterThan(10);
    const distinct = new Set([...m.terrain]);
    expect(distinct.size, "the ground is all one thing").toBeGreaterThan(2);
  });

  it("is a real editable map — same seed gives the same map", () => {
    const a = customFromGenerated("highlands", 7, 2);
    const b = customFromGenerated("highlands", 7, 2);
    expect([...b.terrain]).toEqual([...a.terrain]);
    expect(b.spawns).toEqual(a.spawns);
  });

  it("validates clean enough to play straight away", () => {
    const m = customFromGenerated("highlands", 5, 2);
    expect(hasErrors(validateMap(m)), validateMap(m).map((i) => i.text).join("; ")).toBe(false);
  });

  it("round-trips through a share code like any other map", () => {
    const m = customFromGenerated("riverlands", 9, 2);
    const back = deserialiseMap(serialiseMap(m));
    expect(back).not.toBeNull();
    expect([...back!.terrain]).toEqual([...m.terrain]);
    expect(back!.spawns.length).toBe(m.spawns.length);
  });
});
