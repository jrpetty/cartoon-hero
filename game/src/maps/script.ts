// Random map scripting.
//
// The editor makes one map. A script makes a *family* of them — same rules,
// different seed — which is how competitive map pools actually work: nobody
// ships "Arabia, the map", they ship Arabia the generator and roll it fresh
// each game.
//
// The generator in generator.ts is already exactly this, with the rules
// hard-coded and a preset list standing in for parameters. This exposes the
// same primitives as a small text format, so an author can write down what they
// mean and get a different map every seed.
//
// Design constraints, in order:
//
//   • **Everything is optional.** An empty script is a valid map. Every command
//     has a default, because a format where you must say twelve things before
//     you can say the one thing you care about is a format nobody uses.
//   • **Errors name the line and say what was expected.** A script language
//     with a silent failure mode is worse than no script language: you get a
//     map, it just isn't the one you wrote.
//   • **Deterministic.** Same script and same seed give the same map, always.
//     Otherwise a share code for a script is meaningless.
//   • **It produces a CustomMap.** Not a MapData — a scripted map lands in the
//     editor, where it can be tweaked, validated, saved and shared like any
//     other. A script is a starting point, not a walled garden.

import { RNG } from "../engine/rng";
import { TILE } from "../content/balance";
import { Terrain, BiomeId, BIOMES, TERRAIN_DEFS, TERRAIN_BLOCKS } from "./terrain_kinds";
import { CustomMap, MAX_SEATS, newCustomMap } from "./custom";
import { SymmetryId, SYMMETRIES, symmetryCells } from "./symmetry";
import type { ResourceSpawn } from "./generator";

export interface ScriptError {
  /** 1-based, so it matches what an author sees in the box. */
  line: number;
  text: string;
}

export interface ScriptResult {
  map: CustomMap | null;
  errors: ScriptError[];
  /** Non-fatal remarks: the script ran, but something may not be what you meant. */
  warnings: ScriptError[];
}

/** Terrain by the name an author would write. */
const TERRAIN_BY_WORD: Record<string, Terrain> = (() => {
  const out: Record<string, Terrain> = {};
  // The palette's own display names, so anything shown in the editor's ground
  // list can be typed here verbatim.
  for (const d of TERRAIN_DEFS) out[d.name.toLowerCase()] = d.id;
  // Words an author is more likely to reach for than the palette's own names.
  Object.assign(out, {
    grass: Terrain.Grass, meadow: Terrain.GrassDark, grass_dark: Terrain.GrassDark,
    dirt: Terrain.Dirt, road: Terrain.Dirt, sand: Terrain.Sand, snow: Terrain.Snow,
    hill: Terrain.Hill, hills: Terrain.Hill, highground: Terrain.Hill,
    forest: Terrain.Forest, wood: Terrain.Forest, woods: Terrain.Forest,
    marsh: Terrain.Marsh, bog: Terrain.Marsh, swamp: Terrain.Marsh,
    shallow: Terrain.Shallow, shallows: Terrain.Shallow, ford: Terrain.Shallow,
    water: Terrain.Water, lake: Terrain.Water, sea: Terrain.Water,
    rock: Terrain.Rock, mountain: Terrain.Rock, mountains: Terrain.Rock, cliff: Terrain.Rock,
  });
  return out;
})();

const RESOURCE_BY_WORD: Record<string, ResourceSpawn["type"]> = {
  tree: "tree", trees: "tree", wood: "tree", forest: "tree",
  gold: "gold_mine", gold_mine: "gold_mine", mine: "gold_mine",
  berries: "berries", berry: "berries", food: "berries",
};

const RES_AMOUNT: Record<ResourceSpawn["type"], number> = {
  tree: 160, gold_mine: 1000, berries: 250,
};

/** The commands, for the editor's help panel and for an error that lists them. */
export const SCRIPT_COMMANDS: { name: string; args: string; desc: string }[] = [
  { name: "size", args: "<cells>", desc: "Map width in cells, 40–320. Rounded to the nearest size preset is not required — any number works." },
  { name: "biome", args: "<temperate|arid|alpine|wetland>", desc: "Which palette the map is painted from." },
  { name: "seats", args: "<n>", desc: "How many players the map is for, 1–8." },
  { name: "symmetry", args: "<none|mirrorX|mirrorY|rot180|quad|radial3|radial6|radial8>", desc: "Every placement is repeated through this. rot180 is the classic 1v1 mirror." },
  { name: "base", args: "<terrain>", desc: "Fill the whole map with one ground before anything else." },
  { name: "blob", args: "<terrain> count <n> radius <a>-<b>", desc: "Organic patches — the workhorse. Also spelled patch, lake or range; all the same." },
  { name: "ring", args: "<terrain> radius <0..1> width <n>", desc: "A band at a fraction of the way out from the centre. Moats, crater rims, a central plateau edge." },
  { name: "river", args: "<terrain> width <n> [count <n>]", desc: "A wandering line from one edge to the other." },
  { name: "spawns", args: "ring radius <0..1>", desc: "Seat every player evenly around a circle." },
  { name: "spawn", args: "at <cx>,<cy>", desc: "Seat one player at an exact cell (repeated through symmetry)." },
  { name: "cluster", args: "<resource> count <n> near start radius <a>-<b> nodes <k>", desc: "n clusters of k nodes per seat, at a distance between a and b cells." },
  { name: "scatter", args: "<resource> count <n> [radius <a>-<b>]", desc: "Nodes strewn across the whole map." },
];

// ------------------------------------------------------------------ parsing --

interface Cmd {
  line: number;
  word: string;
  args: string[];
  /** `key value` pairs after the first word, e.g. `count 6`. */
  named: Record<string, string>;
}

/**
 * Split a line into a command and its named arguments.
 *
 * The grammar is deliberately "a word, then some words, then key/value pairs
 * in any order" rather than positional. `blob rock count 8 radius 3-7` and
 * `blob rock radius 3-7 count 8` are the same thing, because remembering an
 * argument order is exactly the kind of friction that stops people writing one.
 */
function tokenise(raw: string, line: number): Cmd | null {
  const stripped = raw.replace(/#.*$/, "").trim(); // # starts a comment
  if (!stripped) return null;
  const parts = stripped.split(/\s+/);
  const word = parts[0].toLowerCase();
  const rest = parts.slice(1);
  const named: Record<string, string> = {};
  const args: string[] = [];
  const KEYS = new Set(["count", "radius", "nodes", "width", "near", "at"]);
  for (let i = 0; i < rest.length; i++) {
    const k = rest[i].toLowerCase();
    if (KEYS.has(k) && i + 1 < rest.length) {
      named[k] = rest[i + 1];
      i++;
    } else {
      args.push(rest[i]);
    }
  }
  return { line, word, args, named };
}

/** `3-7` or `5` → [3,7] / [5,5]. Returns null when it isn't a range at all. */
function parseRange(s: string | undefined): [number, number] | null {
  if (!s) return null;
  const m = /^(\d+(?:\.\d+)?)(?:\s*-\s*(\d+(?:\.\d+)?))?$/.exec(s);
  if (!m) return null;
  const a = Number(m[1]);
  const b = m[2] === undefined ? a : Number(m[2]);
  if (!Number.isFinite(a) || !Number.isFinite(b)) return null;
  return [Math.min(a, b), Math.max(a, b)];
}

// ----------------------------------------------------------------- running --

/**
 * Run a script and hand back an editable map.
 *
 * `seed` is what makes this a family rather than a map: the same script with a
 * different seed is a different battlefield with the same character, which is
 * the entire point of the format.
 */
export function runMapScript(src: string, seed: number, name = "Scripted map"): ScriptResult {
  const errors: ScriptError[] = [];
  const warnings: ScriptError[] = [];
  const cmds: Cmd[] = [];
  src.split(/\r?\n/).forEach((raw, i) => {
    const c = tokenise(raw, i + 1);
    if (c) cmds.push(c);
  });

  // --- header pass: everything that decides the shape of the array ---------
  let size = 128;
  let biome: BiomeId = "temperate";
  let seats = 2;
  let sym: SymmetryId = "rot180";
  for (const c of cmds) {
    if (c.word === "size") {
      const n = Number(c.args[0]);
      if (!Number.isFinite(n) || n < 40 || n > 320) {
        errors.push({ line: c.line, text: `size wants a number from 40 to 320, got "${c.args[0] ?? ""}"` });
      } else size = Math.round(n);
    } else if (c.word === "biome") {
      const b = BIOMES.find((x) => x.id === (c.args[0] ?? "").toLowerCase());
      if (!b) {
        errors.push({ line: c.line, text: `unknown biome "${c.args[0] ?? ""}" — try ${BIOMES.map((x) => x.id).join(", ")}` });
      } else biome = b.id;
    } else if (c.word === "seats") {
      const n = Number(c.args[0]);
      if (!Number.isInteger(n) || n < 1 || n > MAX_SEATS) {
        errors.push({ line: c.line, text: `seats wants a whole number from 1 to ${MAX_SEATS}` });
      } else seats = n;
    } else if (c.word === "symmetry") {
      const id = (c.args[0] ?? "").toLowerCase();
      const found = SYMMETRIES.find((x) => x.id.toLowerCase() === id);
      if (!found) {
        errors.push({ line: c.line, text: `unknown symmetry "${c.args[0] ?? ""}" — try ${SYMMETRIES.map((x) => x.id).join(", ")}` });
      } else sym = found.id;
    }
  }
  if (errors.length) return { map: null, errors, warnings };

  const map = newCustomMap(name, size, biome);
  const cols = map.cols, rows = map.rows;
  const rng = new RNG(seed);
  const idx = (cx: number, cy: number) => cy * cols + cx;
  const inB = (cx: number, cy: number) => cx >= 0 && cy >= 0 && cx < cols && cy < rows;
  const occupied = new Set<number>();

  const paintBlob = (cx: number, cy: number, r: number, t: Terrain) => {
    for (const [ax, ay] of symmetryCells(sym, cx, cy, cols, rows)) {
      for (let dy = -Math.ceil(r); dy <= Math.ceil(r); dy++) {
        for (let dx = -Math.ceil(r); dx <= Math.ceil(r); dx++) {
          const d2 = dx * dx + dy * dy;
          if (d2 > r * r) continue;
          // A soft edge, so a blob reads as a landform rather than a stamped disc.
          if (d2 > (r - 1) * (r - 1) && rng.bool(0.45)) continue;
          const nx = ax + dx, ny = ay + dy;
          if (inB(nx, ny)) map.terrain[idx(nx, ny)] = t;
        }
      }
    }
  };

  const addNode = (cx: number, cy: number, type: ResourceSpawn["type"]) => {
    if (!inB(cx, cy)) return;
    const i = idx(cx, cy);
    // Never two nodes on a cell, and never a node inside a wall — a tree in a
    // lake is a resource no villager can ever reach.
    if (occupied.has(i)) return;
    if (TERRAIN_BLOCKS[map.terrain[i]]) return;
    occupied.add(i);
    map.resources.push({
      type, x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2, amount: RES_AMOUNT[type],
    });
  };

  const terrainArg = (c: Cmd, what: string): Terrain | null => {
    const word = (c.args[0] ?? "").toLowerCase();
    const t = TERRAIN_BY_WORD[word];
    if (t === undefined) {
      errors.push({ line: c.line, text: `${what} wants a terrain, got "${c.args[0] ?? ""}"` });
      return null;
    }
    return t;
  };

  const countArg = (c: Cmd, dflt: number, max = 4000): number => {
    if (c.named.count === undefined) return dflt;
    const n = Number(c.named.count);
    if (!Number.isFinite(n) || n < 0) {
      errors.push({ line: c.line, text: `count wants a number, got "${c.named.count}"` });
      return dflt;
    }
    if (n > max) {
      warnings.push({ line: c.line, text: `count ${n} capped at ${max}` });
      return max;
    }
    return Math.round(n);
  };

  // --- body pass ------------------------------------------------------------
  for (const c of cmds) {
    switch (c.word) {
      case "size": case "biome": case "seats": case "symmetry":
        break; // handled above

      case "base": {
        const t = terrainArg(c, "base");
        if (t !== null) map.terrain.fill(t);
        break;
      }

      // `lake water …` and `range mountain …` read better than `blob` for the
      // two things authors reach for most; they are the same operation.
      case "blob": case "patch": case "lake": case "range": {
        const t = terrainArg(c, c.word);
        if (t === null) break;
        const n = countArg(c, 8, 600);
        const r = parseRange(c.named.radius) ?? [2, 5];
        for (let i = 0; i < n; i++) {
          paintBlob(rng.int(0, cols - 1), rng.int(0, rows - 1), rng.range(r[0], r[1]), t);
        }
        break;
      }

      case "ring": {
        const t = terrainArg(c, "ring");
        if (t === null) break;
        const frac = parseRange(c.named.radius)?.[0] ?? 0.35;
        const width = Math.max(1, Number(c.named.width) || 3);
        const ox = (cols - 1) / 2, oy = (rows - 1) / 2;
        const rad = frac * Math.min(cols, rows) / 2;
        for (let cy = 0; cy < rows; cy++) {
          for (let cx = 0; cx < cols; cx++) {
            const d = Math.hypot(cx - ox, cy - oy);
            if (Math.abs(d - rad) <= width / 2) map.terrain[idx(cx, cy)] = t;
          }
        }
        break;
      }

      case "river": {
        const t = terrainArg(c, "river");
        if (t === null) break;
        const width = Math.max(1, Number(c.named.width) || 3);
        const n = countArg(c, 1, 12);
        for (let k = 0; k < n; k++) {
          // A drunkard's walk from one edge to the other: straight enough to be
          // a river, wobbly enough not to be a canal.
          const vertical = rng.bool(0.5);
          let x = vertical ? rng.int(cols * 0.25, cols * 0.75) : 0;
          let y = vertical ? 0 : rng.int(rows * 0.25, rows * 0.75);
          const steps = vertical ? rows : cols;
          for (let s = 0; s < steps; s++) {
            paintBlob(Math.round(x), Math.round(y), width / 2, t);
            if (vertical) { y++; x += rng.range(-0.8, 0.8); }
            else { x++; y += rng.range(-0.8, 0.8); }
            x = Math.max(1, Math.min(cols - 2, x));
            y = Math.max(1, Math.min(rows - 2, y));
          }
        }
        break;
      }

      case "spawns": {
        // `spawns ring radius 0.36` — the common case, and the one that makes a
        // symmetric map fair without the author placing anything by hand.
        const frac = parseRange(c.named.radius)?.[0] ?? 0.36;
        const ox = (cols - 1) / 2, oy = (rows - 1) / 2;
        const rad = frac * Math.min(cols, rows);
        map.spawns = [];
        for (let i = 0; i < seats; i++) {
          const a = (i / seats) * Math.PI * 2 - Math.PI / 2;
          const cx = Math.round(ox + Math.cos(a) * rad);
          const cy = Math.round(oy + Math.sin(a) * rad);
          map.spawns.push({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 });
        }
        break;
      }

      case "spawn": {
        const at = (c.named.at ?? c.args[0] ?? "").split(",");
        const cx = Number(at[0]), cy = Number(at[1]);
        if (!Number.isFinite(cx) || !Number.isFinite(cy)) {
          errors.push({ line: c.line, text: `spawn wants "at <cx>,<cy>"` });
          break;
        }
        for (const [sx, sy] of symmetryCells(sym, Math.round(cx), Math.round(cy), cols, rows)) {
          if (map.spawns.length >= MAX_SEATS) break;
          map.spawns.push({ x: sx * TILE + TILE / 2, y: sy * TILE + TILE / 2 });
        }
        break;
      }

      case "cluster": {
        const word = (c.args[0] ?? "").toLowerCase();
        const type = RESOURCE_BY_WORD[word];
        if (!type) {
          errors.push({ line: c.line, text: `cluster wants a resource (tree, gold, berries), got "${c.args[0] ?? ""}"` });
          break;
        }
        if (!map.spawns.length) {
          warnings.push({ line: c.line, text: "cluster … near start, but no spawns have been placed yet — put a spawns line above this one" });
          break;
        }
        const n = countArg(c, 2, 200);
        const rr = parseRange(c.named.radius) ?? [12, 28];
        const nodes = Math.max(1, Math.round(Number(c.named.nodes) || 5));
        for (const s of map.spawns) {
          const scx = Math.floor(s.x / TILE), scy = Math.floor(s.y / TILE);
          for (let i = 0; i < n; i++) {
            const a = rng.range(0, Math.PI * 2);
            const d = rng.range(rr[0], rr[1]);
            const bx = Math.round(scx + Math.cos(a) * d);
            const by = Math.round(scy + Math.sin(a) * d);
            for (let k = 0; k < nodes; k++) {
              addNode(bx + rng.int(-1, 1), by + rng.int(-1, 1), type);
            }
          }
        }
        break;
      }

      case "scatter": {
        const word = (c.args[0] ?? "").toLowerCase();
        const type = RESOURCE_BY_WORD[word];
        if (!type) {
          errors.push({ line: c.line, text: `scatter wants a resource (tree, gold, berries), got "${c.args[0] ?? ""}"` });
          break;
        }
        const n = countArg(c, 200, 4000);
        for (let i = 0; i < n; i++) {
          const cx = rng.int(0, cols - 1), cy = rng.int(0, rows - 1);
          for (const [ax, ay] of symmetryCells(sym, cx, cy, cols, rows)) addNode(ax, ay, type);
        }
        break;
      }

      default:
        errors.push({
          line: c.line,
          text: `unknown command "${c.word}" — try ${SCRIPT_COMMANDS.map((x) => x.name).join(", ")}`,
        });
    }
  }

  if (errors.length) return { map: null, errors, warnings };

  map.minPlayers = Math.min(2, Math.max(1, map.spawns.length || 1));
  map.maxPlayers = Math.max(1, map.spawns.length || MAX_SEATS);
  if (!map.spawns.length) {
    // A map with no seats is a valid thing to script — a nomad map — but it is
    // much more often a forgotten `spawns` line, so say so.
    warnings.push({ line: cmds.length, text: "no spawn points were placed; the map will only work as a nomad map" });
    map.nomad = "forced";
    map.minPlayers = 1;
    map.maxPlayers = MAX_SEATS;
  }
  map.desc = `Scripted, seed ${seed}.`;
  return { map, errors, warnings };
}

/**
 * A worked example, shown in the editor.
 *
 * Doubles as documentation: every command that matters appears once, in the
 * order they need to run, with the one ordering constraint the format has
 * (`spawns` before `cluster … near start`) demonstrated rather than explained.
 */
export const EXAMPLE_SCRIPT = `# Highland Passes — a family of maps, not one map.
size 128
biome alpine
seats 4
symmetry quad

base grass
blob meadow count 40 radius 2-5
blob mountain count 10 radius 4-8
blob hill count 22 radius 3-6
blob forest count 26 radius 3-6
lake water count 4 radius 4-8
river shallow width 3

# Seats first: the clusters below are placed relative to them.
spawns ring radius 0.34

cluster berries count 1 near start radius 6-10 nodes 6
cluster gold count 2 near start radius 20-40 nodes 5
cluster tree count 4 near start radius 10-18 nodes 8
scatter tree count 260
`;
