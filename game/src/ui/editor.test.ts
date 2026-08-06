import { describe, expect, it, beforeAll } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { EditorScreen, symmetryCells } from "./editor_screen";
import { Terrain } from "../maps/terrain_kinds";
import { listCustomMaps } from "../maps/custom";


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
    for (const tool of ["terrain", "resource", "spawn", "erase"]) {
      (s as unknown as { tool: string }).tool = tool;
      expect(() => frame({ mx: 100, my: 400 }), tool).not.toThrow();
    }
  });
});
