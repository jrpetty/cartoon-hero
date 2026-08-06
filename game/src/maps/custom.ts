// Custom maps: the format the editor writes, and everything the game needs to
// read one back.
//
// A custom map is deliberately *not* a MapData. MapData is what the sim
// consumes for one particular match — it already knows how many players there
// are, where they start and which cells are walls. A custom map is the authored
// thing that outlives any single match: the ground, the resources, the spawn
// points, and the rules about who may play on it. `toMapData` is where the two
// meet, and it is the only place that has to know about player counts, nomad
// or team seating.

import { RNG } from "../engine/rng";
import { TILE, START_RESOURCES } from "../content/balance";
import { GameMode } from "../sim/types";
import { NavGrid } from "../pathfinding/grid";
import { MapData, ResourceSpawn } from "./generator";
import { Terrain, BiomeId, biomeById, TERRAIN_BLOCKS, TERRAIN_BUILDABLE } from "./terrain_kinds";

export const MAP_FORMAT_VERSION = 1;

/** How a map treats nomad play. */
export type NomadRule = "off" | "optional" | "forced";

export const NOMAD_RULES: { id: NomadRule; label: string; desc: string }[] = [
  { id: "off", label: "Never", desc: "This map is played from its authored spawn points." },
  { id: "optional", label: "Allowed", desc: "Spawn points are used normally; a nomad game ignores them and drops everyone at random." },
  { id: "forced", label: "Always nomad", desc: "There are no spawn points. Everyone lands somewhere random, every game." },
];

export interface CustomMap {
  id: string;
  name: string;
  version: number;
  biome: BiomeId;
  cols: number;
  rows: number;
  /** One Terrain per cell, row-major. */
  terrain: Uint8Array;
  resources: ResourceSpawn[];
  /** Authored spawn points in world coordinates, in seat order. */
  spawns: { x: number; y: number }[];
  /** Which game modes this map is for. Empty means "any". */
  modes: GameMode[];
  minPlayers: number;
  maxPlayers: number;
  nomad: NomadRule;
  startResources: { food: number; wood: number; gold: number };
  /** Epoch millis of the last save, for sorting the library. */
  updated: number;
}

/**
 * The size ladder, from a duel arena to something you could get lost in.
 *
 * `cols` is cells; a cell is TILE (32) world units, and a Town Centre is three
 * cells across. So a Duel map is 40 cells — about thirteen Town Centres wide,
 * which is close quarters — and Colossal is 320, eight times that in area and
 * genuinely a long march from corner to corner.
 *
 * Nothing here implies a player count. A creator can put eight seats on a Duel
 * map if they want a knife fight, or two on a Colossal one if they want a long
 * game of scouting and expansion. The blurb says what a size is *for*; it does
 * not decide anything.
 */
export const MAP_SIZES: { label: string; cols: number; desc: string }[] = [
  { label: "Duel", cols: 40, desc: "A closed arena. Bases within sight of each other — first fight decides it." },
  { label: "Skirmish", cols: 56, desc: "Very small. Room for a wall and a woodline, not much more." },
  { label: "Tiny", cols: 72, desc: "Cramped. Expansions are contested from the first minute." },
  { label: "Small", cols: 96, desc: "Tight but complete — the classic aggressive 1v1 size." },
  { label: "Medium", cols: 128, desc: "The standard field. Comfortable for two to four." },
  { label: "Large", cols: 160, desc: "Room to manoeuvre, and a real map to scout." },
  { label: "Huge", cols: 200, desc: "Long games. Expansions matter more than the opening." },
  { label: "Massive", cols: 256, desc: "A campaign map. Armies take a while to arrive anywhere." },
  { label: "Colossal", cols: 320, desc: "Enormous. A corner-to-corner march is most of a minute at full speed." },
];

/** The engine seats at most this many realms — colours, names and AI slots. */
export const MAX_SEATS = 8;

/** A blank map, painted with its biome's primary ground. */
export function newCustomMap(name: string, cols: number, biome: BiomeId = "temperate"): CustomMap {
  const b = biomeById(biome);
  const terrain = new Uint8Array(cols * cols);
  terrain.fill(b.ground[0]);
  return {
    id: `custom_${Date.now().toString(36)}_${Math.floor(Math.random() * 1e6).toString(36)}`,
    name,
    version: MAP_FORMAT_VERSION,
    biome,
    cols,
    rows: cols,
    terrain,
    resources: [],
    spawns: [],
    modes: [],
    // Wide open by default: the creator narrows it if they mean to, rather than
    // discovering later that the editor quietly decided for them.
    minPlayers: 1,
    maxPlayers: MAX_SEATS,
    nomad: "off",
    startResources: { ...START_RESOURCES },
    updated: Date.now(),
  };
}

// ------------------------------------------------------------ serialising --
//
// The whole game ships as one HTML file, so the only realistic way to move a
// map between two people is a string they can paste. Terrain is run-length
// encoded first — a map is mostly large areas of one ground, so RLE takes a
// 25,000-cell field down to a few hundred runs — and the result is base64'd so
// it survives a chat window.

function rleEncode(a: Uint8Array): string {
  const parts: string[] = [];
  let i = 0;
  while (i < a.length) {
    const v = a[i];
    let n = 1;
    while (i + n < a.length && a[i + n] === v && n < 65535) n++;
    parts.push(n === 1 ? String(v) : `${v}x${n}`);
    i += n;
  }
  return parts.join(".");
}

function rleDecode(s: string, expect: number): Uint8Array | null {
  const out = new Uint8Array(expect);
  let at = 0;
  for (const part of s.split(".")) {
    if (!part) continue;
    const xi = part.indexOf("x");
    const v = Number(xi < 0 ? part : part.slice(0, xi));
    const n = xi < 0 ? 1 : Number(part.slice(xi + 1));
    if (!Number.isFinite(v) || !Number.isFinite(n) || n < 1) return null;
    if (at + n > expect) return null;
    out.fill(v, at, at + n);
    at += n;
  }
  return at === expect ? out : null;
}

// btoa/atob exist in every browser and in Node 16+, which covers the game and
// its headless tests alike; the byte dance around them is what lets a map name
// contain a character outside Latin-1 without corrupting the string.
const b64encode = (s: string): string => btoa(unescape(encodeURIComponent(s)));
const b64decode = (s: string): string => decodeURIComponent(escape(atob(s)));

/** A map as one pasteable string. */
export function serialiseMap(m: CustomMap): string {
  const payload = {
    v: m.version, n: m.name, b: m.biome, c: m.cols, r: m.rows,
    t: rleEncode(m.terrain),
    // Resources and spawns go to cell coordinates: they are always placed on a
    // cell, and integers survive a round trip where floats invite drift. It has
    // to be floor, not round — a cell centre is cx*TILE + TILE/2, so rounding
    // sends every coordinate one cell down and to the right.
    rs: m.resources.map((x) => [x.type[0], Math.floor(x.x / TILE), Math.floor(x.y / TILE), x.amount]),
    sp: m.spawns.map((s) => [Math.floor(s.x / TILE), Math.floor(s.y / TILE)]),
    md: m.modes, mn: m.minPlayers, mx: m.maxPlayers, nm: m.nomad,
    sr: m.startResources,
  };
  return `BBMAP1:${b64encode(JSON.stringify(payload))}`;
}

const RES_BY_LETTER: Record<string, ResourceSpawn["type"]> = { t: "tree", g: "gold_mine", b: "berries" };

/** Parse a pasted map string. Returns null for anything it doesn't trust. */
export function deserialiseMap(code: string): CustomMap | null {
  try {
    const trimmed = code.trim();
    const at = trimmed.indexOf(":");
    if (at < 0 || !trimmed.slice(0, at).startsWith("BBMAP")) return null;
    const p = JSON.parse(b64decode(trimmed.slice(at + 1)));
    const cols = Number(p.c), rows = Number(p.r);
    if (!Number.isInteger(cols) || !Number.isInteger(rows) || cols < 16 || cols > 400 || rows < 16 || rows > 400) return null;
    const terrain = rleDecode(String(p.t), cols * rows);
    if (!terrain) return null;
    // Anything outside the terrain table would index off the end of the lookup
    // arrays on the movement path, so clamp rather than trust the string.
    for (let i = 0; i < terrain.length; i++) if (terrain[i] > 10) terrain[i] = 0;
    const cell = (v: unknown) => Math.floor(Number(v)) * TILE + TILE / 2;
    return {
      id: `custom_${Date.now().toString(36)}_${Math.floor(Math.random() * 1e6).toString(36)}`,
      name: String(p.n ?? "Imported map").slice(0, 40),
      version: Number(p.v) || MAP_FORMAT_VERSION,
      biome: (["temperate", "arid", "alpine", "wetland"].includes(p.b) ? p.b : "temperate") as BiomeId,
      cols, rows, terrain,
      resources: (Array.isArray(p.rs) ? p.rs : []).flatMap((r: unknown[]) => {
        const type = RES_BY_LETTER[String(r[0])];
        if (!type) return [];
        return [{ type, x: cell(r[1]), y: cell(r[2]), amount: Math.max(1, Number(r[3]) || 100) }];
      }),
      spawns: (Array.isArray(p.sp) ? p.sp : []).map((s: unknown[]) => ({ x: cell(s[0]), y: cell(s[1]) })),
      modes: (Array.isArray(p.md) ? p.md : []).filter((m: string) =>
        ["conquest", "survival", "koth", "regicide"].includes(m)) as GameMode[],
      minPlayers: Math.max(1, Math.min(MAX_SEATS, Number(p.mn) || 2)),
      maxPlayers: Math.max(1, Math.min(MAX_SEATS, Number(p.mx) || MAX_SEATS)),
      nomad: (["off", "optional", "forced"].includes(p.nm) ? p.nm : "off") as NomadRule,
      startResources: {
        food: Math.max(0, Number(p.sr?.food) || START_RESOURCES.food),
        wood: Math.max(0, Number(p.sr?.wood) || START_RESOURCES.wood),
        gold: Math.max(0, Number(p.sr?.gold) || START_RESOURCES.gold),
      },
      updated: Date.now(),
    };
  } catch {
    return null;
  }
}

// -------------------------------------------------------------- validation --

export interface MapIssue { level: "error" | "warning"; text: string }

/** How far from a spawn each resource has to be to count as "at your base". */
const NEAR_BASE = 14; // cells

/**
 * Everything wrong with a map, split into what stops it being playable and
 * what merely makes it a bad map. Errors block playing; warnings do not,
 * because an author is allowed to make something deliberately strange.
 */
export function validateMap(m: CustomMap): MapIssue[] {
  const issues: MapIssue[] = [];
  const err = (text: string) => issues.push({ level: "error", text });
  const warn = (text: string) => issues.push({ level: "warning", text });

  if (!m.name.trim()) err("The map has no name.");
  if (m.maxPlayers < m.minPlayers) err("Maximum players is below the minimum.");

  const idx = (cx: number, cy: number) => cy * m.cols + cx;
  const inB = (cx: number, cy: number) => cx >= 0 && cy >= 0 && cx < m.cols && cy < m.rows;

  // ---- spawns ----
  const needSpawns = m.nomad === "forced" ? 0 : m.maxPlayers;
  if (m.nomad === "forced" && m.spawns.length) {
    warn("This map is always nomad, so its spawn points are ignored.");
  }
  if (m.spawns.length < needSpawns) {
    err(`${m.spawns.length} spawn point${m.spawns.length === 1 ? "" : "s"} for up to ${m.maxPlayers} players. Place ${needSpawns - m.spawns.length} more, or lower the maximum to ${m.spawns.length}.`);
  }
  const cellsOf = (s: { x: number; y: number }) => [Math.floor(s.x / TILE), Math.floor(s.y / TILE)] as [number, number];
  m.spawns.forEach((s, i) => {
    const [cx, cy] = cellsOf(s);
    if (!inB(cx, cy)) { err(`Spawn ${i + 1} is off the map.`); return; }
    // A Town Centre is 3 tiles; leave a ring around it so the first villagers
    // are not born inside a mountain.
    for (let dy = -2; dy <= 2; dy++) {
      for (let dx = -2; dx <= 2; dx++) {
        if (!inB(cx + dx, cy + dy) || !TERRAIN_BUILDABLE[m.terrain[idx(cx + dx, cy + dy)]]) {
          err(`Spawn ${i + 1} has no room for a Town Centre — clear the ground around it.`);
          return;
        }
      }
    }
  });

  // ---- reachability ----
  // Component labels make this exact and instant: if two spawns are in
  // different pockets, no route between them exists at all.
  if (m.spawns.length > 1) {
    const grid = new NavGrid(m.cols * TILE, m.rows * TILE);
    for (let cy = 0; cy < m.rows; cy++) {
      for (let cx = 0; cx < m.cols; cx++) {
        if (TERRAIN_BLOCKS[m.terrain[idx(cx, cy)]]) grid.setBlocked(cx, cy, true);
      }
    }
    const [ax, ay] = cellsOf(m.spawns[0]);
    for (let i = 1; i < m.spawns.length; i++) {
      const [bx, by] = cellsOf(m.spawns[i]);
      if (grid.provablyUnreachable(ax, ay, bx, by)) {
        err(`Spawn ${i + 1} is walled off from spawn 1 — no army could ever reach it.`);
      }
    }
    // King of the Hill is fought over the centre, so the centre has to be
    // somewhere everybody can actually get to.
    if (m.modes.includes("koth")) {
      const mc = Math.floor(m.cols / 2), mr = Math.floor(m.rows / 2);
      if (TERRAIN_BLOCKS[m.terrain[idx(mc, mr)]]) err("King of the Hill: the centre of the map is impassable.");
      else if (grid.provablyUnreachable(ax, ay, mc, mr)) err("King of the Hill: the centre is unreachable from spawn 1.");
    }
  }

  // ---- resources ----
  const counts = { tree: 0, gold_mine: 0, berries: 0 };
  for (const r of m.resources) counts[r.type]++;
  if (!counts.tree) err("There is no wood anywhere on the map.");
  if (!counts.gold_mine) warn("There is no gold on the map — nothing past the Feudal Age can be trained.");
  if (!counts.berries) warn("There is no food on the map besides farms.");

  if (m.nomad !== "forced") {
    m.spawns.forEach((s, i) => {
      const near = { tree: 0, gold_mine: 0, berries: 0 };
      for (const r of m.resources) {
        const d = Math.hypot(r.x - s.x, r.y - s.y) / TILE;
        if (d <= NEAR_BASE) near[r.type]++;
      }
      if (!near.tree) warn(`Spawn ${i + 1} has no wood within reach of its base.`);
      if (!near.berries && !near.gold_mine) warn(`Spawn ${i + 1} starts with no food or gold nearby.`);
    });
    // Fairness: seats should be roughly as well supplied as each other.
    if (m.spawns.length > 1) {
      const scores = m.spawns.map((s) =>
        m.resources.reduce((n, r) => n + (Math.hypot(r.x - s.x, r.y - s.y) / TILE <= NEAR_BASE ? r.amount : 0), 0));
      const lo = Math.min(...scores), hi = Math.max(...scores);
      if (hi > 0 && lo < hi * 0.6) {
        warn("Some starts are much richer than others — the seats are not equal.");
      }
    }
  }

  // ---- mode-specific ----
  if (m.modes.includes("survival")) {
    // Waves arrive from the edges, so the edges must not be sealed.
    let openEdge = 0, edge = 0;
    for (let cx = 0; cx < m.cols; cx++) {
      for (const cy of [0, m.rows - 1]) { edge++; if (!TERRAIN_BLOCKS[m.terrain[idx(cx, cy)]]) openEdge++; }
    }
    for (let cy = 0; cy < m.rows; cy++) {
      for (const cx of [0, m.cols - 1]) { edge++; if (!TERRAIN_BLOCKS[m.terrain[idx(cx, cy)]]) openEdge++; }
    }
    if (openEdge < edge * 0.25) {
      err("Survival: the map edges are walled in, so waves would have nowhere to spawn.");
    }
    if (m.maxPlayers > 4) warn("Survival is co-op against the horde — more than four defenders is unusual.");
  }
  if (m.modes.includes("regicide") && m.nomad === "forced") {
    warn("Regicide on a nomad map: Kings land wherever their villagers do.");
  }

  const blocked = m.terrain.reduce((n, t) => n + (TERRAIN_BLOCKS[t] ? 1 : 0), 0);
  if (blocked > m.terrain.length * 0.55) {
    warn("More than half the map is impassable — there may not be room to play.");
  }
  return issues;
}

export const hasErrors = (issues: MapIssue[]) => issues.some((i) => i.level === "error");

/** Whether a map allows a given match setup, for the skirmish map list. */
export function mapSupports(m: CustomMap, mode: GameMode, players: number): boolean {
  if (m.modes.length && !m.modes.includes(mode)) return false;
  return players >= m.minPlayers && players <= m.maxPlayers;
}

// ------------------------------------------------------------- to MapData --

/**
 * Turn an authored map into the MapData one match consumes.
 *
 * Nomad is resolved here and nowhere else: a forced-nomad map has no spawn
 * points at all, an optional-nomad map ignores them only when the match asks
 * for nomad, and everything else seats players at the points the author placed.
 * Random landing sites are drawn from the match seed, so a nomad game on a
 * custom map is as replayable as any other.
 */
export function toMapData(m: CustomMap, seed: number, players: number, nomad = false): MapData {
  const terrain = new Uint8Array(m.terrain); // the match must never edit the author's copy
  const blockedCells: [number, number][] = [];
  for (let cy = 0; cy < m.rows; cy++) {
    for (let cx = 0; cx < m.cols; cx++) {
      if (TERRAIN_BLOCKS[terrain[cy * m.cols + cx]]) blockedCells.push([cx, cy]);
    }
  }
  const useNomad = m.nomad === "forced" || (nomad && m.nomad !== "off");
  let starts: { x: number; y: number }[];
  if (useNomad) {
    starts = randomStarts(m, seed, players);
  } else {
    starts = m.spawns.slice(0, players).map((s) => ({ ...s }));
    // Fewer authored spawns than players should have been caught in validation;
    // if it happens anyway, put the extras somewhere legal rather than crash.
    while (starts.length < players) starts.push(...randomStarts(m, seed + starts.length, 1));
  }
  return {
    name: m.name,
    worldW: m.cols * TILE,
    worldH: m.rows * TILE,
    cols: m.cols,
    rows: m.rows,
    terrain,
    blockedCells,
    resources: m.resources.map((r) => ({ ...r })),
    starts,
    startResources: { ...m.startResources },
    seed,
    biome: m.biome,
    custom: true,
    modes: m.modes,
    nomad: useNomad,
  };
}

/** Well-spaced landing sites on open, buildable ground. */
function randomStarts(m: CustomMap, seed: number, players: number): { x: number; y: number }[] {
  const rng = new RNG(seed);
  const margin = Math.max(4, Math.floor(m.cols * 0.1));
  const minSep = m.cols * (players >= 4 ? 0.26 : 0.36);
  const roomFor = (cx: number, cy: number) => {
    for (let dy = -2; dy <= 2; dy++) {
      for (let dx = -2; dx <= 2; dx++) {
        const nx = cx + dx, ny = cy + dy;
        if (nx < 0 || ny < 0 || nx >= m.cols || ny >= m.rows) return false;
        if (!TERRAIN_BUILDABLE[m.terrain[ny * m.cols + nx]]) return false;
      }
    }
    return true;
  };
  const pts: { x: number; y: number }[] = [];
  const cells: [number, number][] = [];
  let guard = 0;
  while (pts.length < players && guard++ < 8000) {
    const cx = rng.int(margin, m.cols - 1 - margin);
    const cy = rng.int(margin, m.rows - 1 - margin);
    if (!roomFor(cx, cy)) continue;
    if (!cells.every(([px, py]) => (px - cx) ** 2 + (py - cy) ** 2 > minSep * minSep)) continue;
    cells.push([cx, cy]);
    pts.push({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 });
  }
  // A crowded or hostile map may not have room for the spacing we wanted;
  // relax it rather than hand back fewer starts than there are players.
  guard = 0;
  while (pts.length < players && guard++ < 8000) {
    const cx = rng.int(margin, m.cols - 1 - margin);
    const cy = rng.int(margin, m.rows - 1 - margin);
    if (!roomFor(cx, cy)) continue;
    pts.push({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 });
  }
  while (pts.length < players) {
    pts.push({ x: (m.cols / 2) * TILE, y: (m.rows / 2) * TILE });
  }
  return pts;
}

// ----------------------------------------------------------------- storage --

const STORE_KEY = "bb_custom_maps";

/** Every saved map, newest first. Bad entries are skipped, never thrown. */
export function listCustomMaps(): CustomMap[] {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw) as string[];
    const out: CustomMap[] = [];
    for (const entry of arr) {
      const m = deserialiseMap(entry.slice(entry.indexOf("|") + 1));
      if (!m) continue;
      m.id = entry.slice(0, entry.indexOf("|")) || m.id;
      out.push(m);
    }
    return out.sort((a, b) => b.updated - a.updated);
  } catch {
    return [];
  }
}

export function saveCustomMap(m: CustomMap): void {
  try {
    m.updated = Date.now();
    const all = listCustomMaps().filter((x) => x.id !== m.id);
    all.unshift(m);
    localStorage.setItem(STORE_KEY, JSON.stringify(all.slice(0, 40).map((x) => `${x.id}|${serialiseMap(x)}`)));
  } catch { /* storage full or unavailable — the export string still works */ }
}

export function deleteCustomMap(id: string): void {
  try {
    const all = listCustomMaps().filter((x) => x.id !== id);
    localStorage.setItem(STORE_KEY, JSON.stringify(all.map((x) => `${x.id}|${serialiseMap(x)}`)));
  } catch { /* */ }
}

export function findCustomMap(id: string): CustomMap | null {
  return listCustomMaps().find((m) => m.id === id) ?? null;
}
