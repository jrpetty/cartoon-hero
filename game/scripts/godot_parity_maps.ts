// Fixtures for the Godot port's map-tooling parity tests: the script language,
// share codes, validation, nomad landing sites, the Random roll and symmetry —
// what the TypeScript build produces, so the GDScript build can be checked
// against it value for value.
//
//   npx tsx scripts/godot_parity_maps.ts
//
// Writes godot/tests/fixtures/maps_tools.json. (Generated maps themselves are
// in maps.json, from godot_parity.ts.)

import fs from "fs";
import path from "path";
import { runMapScript, EXAMPLE_SCRIPT } from "../src/maps/script";
import {
  customFromGenerated, serialiseMap, deserialiseMap, toMapData, validateMap, rollRandomMap, newCustomMap, CustomMap,
} from "../src/maps/custom";
import { symmetryCells, SymmetryId } from "../src/maps/symmetry";
import { PRESETS } from "../src/maps/generator";
import { TILE } from "../src/content/balance";
import { Terrain } from "../src/maps/terrain_kinds";

const OUT = path.resolve("../godot/tests/fixtures");
fs.mkdirSync(OUT, { recursive: true });

function fnv(bytes: ArrayLike<number>): number {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < bytes.length; i++) { h ^= bytes[i] & 0xff; h = Math.imul(h, 16777619) >>> 0; }
  return h >>> 0;
}

/** The deterministic part of a CustomMap (id and timestamps are not). */
function mapJson(m: CustomMap, withTerrain: boolean) {
  return {
    name: m.name, desc: m.desc, version: m.version, biome: m.biome, cols: m.cols, rows: m.rows,
    terrainHash: fnv(m.terrain), ...(withTerrain ? { terrain: Array.from(m.terrain) } : {}),
    // [type, x, y, amount] per node, in order (compact: there are thousands).
    resources: m.resources.map((r) => [r.type, r.x, r.y, r.amount]), spawns: m.spawns, modes: m.modes, minPlayers: m.minPlayers,
    maxPlayers: m.maxPlayers, nomad: m.nomad, startResources: m.startResources,
  };
}

// ---- the script language ----
// Every command, every symmetry, fractional river bounds (size 130 → 32.5),
// errors, warnings, and the worked example over several seeds.
const SCRIPTS: [string, string, number][] = [
  ["example_1", EXAMPLE_SCRIPT, 1],
  ["example_5", EXAMPLE_SCRIPT, 5],
  ["example_33", EXAMPLE_SCRIPT, 33],
  ["example_99", EXAMPLE_SCRIPT, 99],
  ["empty", "", 1],
  ["radial3", "size 100\nseats 3\nsymmetry radial3\nbase grass\nblob rock count 12 radius 2-4\nspawns ring radius 0.3\ncluster gold count 2 near start radius 10-20 nodes 4\nscatter tree count 150", 7],
  ["radial6", "size 90\nsymmetry radial6\nbase snow\nblob hill count 9 radius 3.5-6.5\nscatter berries count 80", 8],
  ["radial8", "size 120\nseats 8\nsymmetry radial8\nbiome wetland\nblob marsh count 10 radius 2-6\nspawns ring radius 0.38\ncluster tree count 3 near start radius 8-14 nodes 6", 9],
  ["mirrors", "size 72\nsymmetry mirrorX\nbase dirt\npatch meadow count 20 radius 1-3\nlake water count 3 radius 3-5\nspawn at 10,20\nspawn at 15,50\nscatter gold count 40", 10],
  ["mirrorY_quad", "size 64\nsymmetry mirrorY\nblob forest count 10\nsymmetry quad\nscatter tree count 120", 11],
  ["river_frac", "size 130\nbase grass\nriver shallow width 4 count 3\nring water radius 0.5 width 2.5\nspawns ring radius 0.3\nscatter tree count 300", 12],
  ["none_sym", "size 50\nsymmetry none\nrange mountain count 5 radius 2-3\nspawn at 5,5\nspawn at 44,44\nspawn at 5,44", 13],
  ["late_spawns", "size 96\nseats 2\nbase grass\nscatter tree count 900\nspawns ring radius 0.3", 5],
  ["errors", "size 128\nblob nonsense count 3\nfrobnicate\ncount\nscatter rubies\nspawn at x,y\nblob rock count -2", 1],
  ["header_errors", "size 5\nbiome tropical\nseats 0\nsymmetry sideways", 1],
  ["capped", "size 40\nscatter tree count 999999\nblob rock count 1e3 radius 1", 2],
  ["no_spawn_cluster", "base grass\ncluster gold count 2 near start", 1],
  ["comments", "  # a comment\r\nsize 64    # trailing\r\n\r\nBLOB Rock Radius 3-5 COUNT 6\n", 4],
];
const scripts = SCRIPTS.map(([name, src, seed]) => {
  const r = runMapScript(src, seed);
  return {
    name, src, seed, errors: r.errors, warnings: r.warnings,
    map: r.map ? mapJson(r.map, name === "example_1" || name === "radial3" || name === "river_frac") : null,
  };
});

// ---- share codes ----
// A code made from a generated map, from a scripted one, and from a hand-made
// one with a non-Latin-1 name and an author, plus decoding odd payloads.
const crossing = customFromGenerated("open_plains", 7, 2, "Crossing");
const scripted = runMapScript(EXAMPLE_SCRIPT, 5).map!;
const odd = newCustomMap("Ardennes — Frühling ⚔ \"quoted\" \\ back\tslash", 24, "alpine");
odd.author = "Ragnhild";
odd.modes = ["koth", "regicide"];
odd.nomad = "optional";
odd.terrain[3] = Terrain.Rock;
odd.resources.push({ type: "berries", x: 5 * TILE + 16, y: 6 * TILE + 16, amount: 250 });
odd.spawns.push({ x: 2 * TILE + 16, y: 3 * TILE + 16 });
const codes = [
  { name: "crossing", m: crossing, terrain: false },
  { name: "scripted", m: scripted, terrain: false },
  { name: "odd", m: odd, terrain: true },
].map(({ name, m, terrain }) => {
  const code = serialiseMap(m);
  // What reading the code back gives (resources land on cell centres, the name
  // is trimmed to 40 characters) — the Godot build must read it the same way.
  const back = deserialiseMap(code)!;
  return { name, code, map: mapJson(m, terrain), decoded: { ...mapJson(back, terrain), author: back.author ?? null } };
});
const b64 = (o: unknown) => "BBMAP1:" + btoa(unescape(encodeURIComponent(JSON.stringify(o))));
const forged = [
  "", "hello", "BBMAP1:not-base64!!", "BBMAP1:" + btoa("{}"), btoa("{}"), "BBMAP1:" + btoa("null"),
  b64({ c: 20, r: 20, t: "99x400" }),
  b64({ c: "20", r: 20.0, t: "2x100.0x300", n: 42, d: ["a", "b"], v: "3", b: "arid", nm: "forced", mn: "3", mx: 99, sr: { food: "50", wood: 0, gold: -5 }, au: 7 }),
  b64({ c: 16, r: 16, t: "1x256", rs: [["t", 1, 2, 0], ["x", 1, 1, 1], ["g", "3", 4.7, "500"], "b12", ["b", null, 2]], sp: [[1, 1], ["2", "3"]], md: ["koth", "nope", 5] }),
  b64({ c: 16, r: 16, t: "1x255" }),
  b64({ c: 16, r: 16, t: "300x10.1x246" }),
  b64({ c: 15, r: 16, t: "1x240" }),
  b64({ c: 16, r: 16, t: "1x256", rs: [null] }),
  "  BBMAP2:" + btoa(JSON.stringify({ c: 16, r: 16, t: "0x256", n: "Spaces" })) + "  ",
];
const decoded = forged.map((code) => {
  const m = deserialiseMap(code);
  return { code, map: m ? { ...mapJson(m, true), published: m.published, author: m.author ?? null } : null };
});

// ---- validation ----
const playable = () => {
  const m = newCustomMap("Test Field", 48);
  const cell = (cx: number, cy: number) => ({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 });
  m.maxPlayers = 2;
  m.spawns = [cell(10, 10), cell(37, 37)];
  for (const [sx, sy] of [[10, 10], [37, 37]] as const) {
    for (let i = 0; i < 6; i++) {
      m.resources.push({ type: "tree", ...cell(sx + 5, sy + i - 2), amount: 160 });
      m.resources.push({ type: "berries", ...cell(sx - 4, sy + i - 2), amount: 250 });
    }
    m.resources.push({ type: "gold_mine", ...cell(sx + 3, sy + 5), amount: 1000 });
  }
  return m;
};
const v1 = playable(); v1.maxPlayers = 4; v1.modes = ["survival", "regicide"]; v1.nomad = "forced";
const v2 = playable(); v2.name = "  "; v2.minPlayers = 3; v2.maxPlayers = 2; v2.spawns.push({ x: -40, y: 10 });
const v3 = playable(); v3.modes = ["koth"]; v3.terrain.fill(Terrain.Rock, 0, 48 * 30);
const validations = [
  ...PRESETS.map((p) => ({ name: `generated_${p.id}`, issues: validateMap(customFromGenerated(p.id, 31, 4)) })),
  ...[1, 2, 3, 4, 5].map((s) => ({ name: `example_${s}`, issues: validateMap(runMapScript(EXAMPLE_SCRIPT, s).map!) })),
  { name: "v1", issues: validateMap(v1) },
  { name: "v2", issues: validateMap(v2) },
  { name: "v3", issues: validateMap(v3) },
  { name: "blank", issues: validateMap(newCustomMap("Blank", 64)) },
];

// ---- to MapData: nomad landing sites and the fallback seating ----
const nomadMap = playable(); nomadMap.nomad = "forced";
for (let cy = 8; cy < nomadMap.rows - 8; cy++) {
  for (let cx = 8; cx < nomadMap.cols - 8; cx++) {
    if ((cx * 7 + cy * 13) % 11 === 0) nomadMap.terrain[cy * nomadMap.cols + cx] = Terrain.Rock;
  }
}
const short = playable(); // two spawns, asked for five players
const toMap = [
  ...[1, 2, 3, 4, 5].map((seed) => ({ name: `nomad_${seed}`, starts: toMapData(nomadMap, seed, 4, true).starts })),
  ...[1, 2, 4, 8].map((n) => ({ name: `nomad_n${n}`, starts: toMapData(nomadMap, 3, n, true).starts })),
  { name: "crossing_nomad", starts: toMapData({ ...crossing, nomad: "optional" }, 4242, 2, true).starts },
  { name: "short", starts: toMapData(short, 9, 5).starts },
  { name: "scripted_8", starts: toMapData({ ...scripted, nomad: "forced" }, 77, 8).starts },
];

// ---- the Random roll ----
const pool = [{ id: "custom_a" }, { id: "custom_b" }, { id: "custom_c" }];
const rolls = Array.from({ length: 60 }, (_, s) => rollRandomMap(s * 7919 + 3, pool, PRESETS.length));

// ---- symmetry ----
const syms: SymmetryId[] = ["none", "mirrorX", "mirrorY", "rot180", "quad", "radial3", "radial6", "radial8"];
const symmetry: { sym: string; cx: number; cy: number; cols: number; rows: number; cells: [number, number][] }[] = [];
for (const sym of syms) {
  for (const [cx, cy, cols, rows] of [[3, 4, 40, 40], [20, 7, 41, 41], [0, 0, 64, 64], [33, 50, 64, 64], [12, 30, 128, 96], [70, 70, 128, 128]]) {
    symmetry.push({ sym, cx, cy, cols, rows, cells: symmetryCells(sym, cx, cy, cols, rows) });
  }
}

const out = { scripts, codes, decoded, validations, toMap, rolls, symmetry };
fs.writeFileSync(path.join(OUT, "maps_tools.json"), JSON.stringify(out));
console.log(`  maps_tools.json ${(fs.statSync(path.join(OUT, "maps_tools.json")).size / 1024).toFixed(0)} KB`);

// ---- Math.sin / Math.cos, bit for bit (for JSMath) ----
// As hex bit patterns: Godot's text-to-float isn't correctly rounded for every
// 17-digit literal, and this is exactly the precision being tested.
const dv = new DataView(new ArrayBuffer(8));
const hex = (x: number) => { dv.setFloat64(0, x); return dv.getBigUint64(0).toString(16).padStart(16, "0"); };
let st = 20261021 >>> 0;
const rnd = () => { st = (st + 0x6d2b79f5) | 0; let t = Math.imul(st ^ (st >>> 15), 1 | st); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const xs: number[] = [0, -0, 1e-300, -1e-9, 2 ** -28, 0.3, 0.78125, Math.PI / 4, Math.PI / 2, Math.PI, 3 * Math.PI / 4, 2 * Math.PI, 1e5, -823549.0, 823550.5];
for (let k = 1; k < 40; k++) { xs.push(k * Math.PI / 2, -k * Math.PI / 2, k * Math.PI / 2 + 1e-12); }
for (let i = 0; i < 1500; i++) xs.push((rnd() - 0.5) * [2, 8, 40, 400, 2e5][i % 5]);
const trig = xs.map((x) => [hex(x), hex(Math.sin(x)), hex(Math.cos(x))]);
fs.writeFileSync(path.join(OUT, "js_math.json"), JSON.stringify({ trig }));
console.log(`  js_math.json ${(fs.statSync(path.join(OUT, "js_math.json")).size / 1024).toFixed(0)} KB`);
