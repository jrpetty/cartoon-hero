// Fixtures for the Godot port's map-editor parity tests: the scatter brush's
// per-cell hash, what a scatter dab, a path stroke and a symmetric brush do to
// a map, and a region copy/stamp — the TypeScript editor's answers, so the
// GDScript editor can be checked against them value for value.
//
//   npx tsx scripts/godot_parity_editor.ts
//
// Writes godot/tests/fixtures/editor.json.

import fs from "fs";
import path from "path";
import { EditorScreen } from "../src/ui/editor_screen";
import { newCustomMap, CustomMap } from "../src/maps/custom";
import { Terrain } from "../src/maps/terrain_kinds";

const OUT = path.resolve("../godot/tests/fixtures");
fs.mkdirSync(OUT, { recursive: true });

function fnv(bytes: ArrayLike<number>): number {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < bytes.length; i++) { h ^= bytes[i] & 0xff; h = Math.imul(h, 16777619) >>> 0; }
  return h >>> 0;
}

type Inner = {
  map: CustomMap | null; sym: string; paint: number; brush: number; resource: string; scatterDensity: number; scatterSeed: number;
  clipFlipX: boolean; clipFlipY: boolean;
  scatterRoll(cx: number, cy: number): number; scatterAt(cx: number, cy: number): void; paintAt(cx: number, cy: number): void;
  strokePath(x0: number, y0: number, x1: number, y1: number): void; copyRegion(x0: number, y0: number, x1: number, y1: number): void;
  pasteRegion(cx: number, cy: number): void; placeResource(cx: number, cy: number): void; placeSpawn(cx: number, cy: number): void;
  eraseAt(cx: number, cy: number): void;
};
const make = (sym: string, cols = 64) => {
  const s = new EditorScreen() as unknown as Inner;
  s.map = newCustomMap("T", cols, "temperate");
  s.sym = sym;
  return s;
};
const out = (s: Inner) => ({
  terrainHash: fnv(s.map!.terrain),
  resources: s.map!.resources.map((r) => [r.type, r.x, r.y, r.amount]),
  spawns: s.map!.spawns.map((p) => [p.x, p.y]),
});

// The hash itself, over seeds that exercise the double-precision rounding of
// the big product (seed × 2246822519 is past 2^53 for a large seed).
const rolls: { seed: number; cx: number; cy: number; v: number }[] = [];
const roller = new EditorScreen() as unknown as Inner;
for (const seed of [1, 2, 999, 1013904223, 1664525 * 7 + 3, 2 ** 31 - 1, 2 ** 31 + 5, 4294967295]) {
  for (const [cx, cy] of [[0, 0], [1, 0], [0, 1], [30, 30], [63, 63], [127, 5], [199, 199], [17, 101]]) {
    roller.scatterSeed = seed;
    rolls.push({ seed, cx, cy, v: roller.scatterRoll(cx, cy) });
  }
}

// What the tools do to a map, under every symmetry.
const cases: Record<string, unknown> = {};
for (const sym of ["none", "mirrorX", "mirrorY", "rot180", "quad", "radial3", "radial6", "radial8"]) {
  const a = make(sym);
  a.paint = Terrain.Rock; a.brush = 4; a.paintAt(20, 13);
  a.paint = Terrain.Water; a.brush = 2; a.strokePath(3, 9, 41, 30);
  a.resource = "tree"; a.brush = 7; a.scatterDensity = 0.35; a.scatterSeed = 12345; a.scatterAt(18, 40);
  a.resource = "gold_mine"; a.brush = 1; a.placeResource(10, 50);
  a.placeSpawn(12, 12);
  a.brush = 2; a.eraseAt(17, 40);
  cases[sym] = out(a);
}
// A region lifted and stamped back, flipped both ways.
const r = make("none");
r.paint = Terrain.Rock; r.brush = 1;
r.strokePath(5, 5, 9, 5); r.strokePath(5, 5, 5, 8);
r.resource = "berries"; r.brush = 3; r.scatterDensity = 0.6; r.scatterAt(7, 6);
r.copyRegion(9, 8, 4, 4);
r.clipFlipX = true; r.pasteRegion(40, 30);
r.clipFlipY = true; r.pasteRegion(60, 60);
cases.region = out(r);

fs.writeFileSync(path.join(OUT, "editor.json"), JSON.stringify({ rolls, cases }));
console.log(`wrote ${path.join(OUT, "editor.json")}: ${rolls.length} rolls, ${Object.keys(cases).length} tool cases`);
