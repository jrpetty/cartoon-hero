// The ground itself, and what it does to whoever stands on it.
//
// Before this there were five cosmetic ground types and one rule: water is a
// wall. Everything else was decoration. A map was therefore an open field with
// some scenery, and there was nothing to manoeuvre around — no reason to take
// the long way, no ground worth holding, no line you could not simply walk
// through.
//
// Now the ground has four jobs:
//   • **Mountains** and **lakes** are hard barriers. They shape where armies can
//     go at all, which is what makes a map have geography rather than texture.
//   • **Forests** can be pushed through, slowly. They are the *soft* barrier:
//     passable, so nothing is ever truly walled off, but slow enough that going
//     around is usually right — and slow enough that being caught in one is a
//     real cost.
//   • **Hills** are passable and worth holding: standing on one sees further and
//     shoots further. High ground is the thing worth fighting over.
//   • **Marsh** and **shallows** are pure friction — slow ground with nothing to
//     recommend it, so crossing at the wrong place is punished.

export const enum Terrain {
  Grass = 0,
  GrassDark = 1,
  Dirt = 2,
  Water = 3,   // lake / river — impassable
  Sand = 4,
  Forest = 5,  // passable, slow — the soft barrier
  Rock = 6,    // mountain — impassable
  Hill = 7,    // passable, high ground: sees and shoots further
  Marsh = 8,   // passable, slow
  Snow = 9,
  Shallow = 10, // fordable water: slow but crossable
}

export const TERRAIN_COUNT = 11;

export interface TerrainDef {
  id: Terrain;
  name: string;
  /** Nothing can walk here, and nothing can be built here. */
  blocks: boolean;
  /** Movement multiplier for anything crossing it. 1 = normal ground. */
  speed: number;
  /** Multiplier on attack range and vision while standing here. */
  sight: number;
  /** Buildings may be placed on it. */
  buildable: boolean;
  /** Short line for the editor's palette. */
  desc: string;
}

/**
 * Indexed by Terrain, so a lookup is an array read on the movement path.
 * Everything the sim asks about the ground comes from here.
 */
export const TERRAIN_DEFS: TerrainDef[] = [
  { id: Terrain.Grass, name: "Grass", blocks: false, speed: 1, sight: 1, buildable: true, desc: "Open ground." },
  { id: Terrain.GrassDark, name: "Meadow", blocks: false, speed: 1, sight: 1, buildable: true, desc: "Open ground, darker grass." },
  { id: Terrain.Dirt, name: "Dirt", blocks: false, speed: 1, sight: 1, buildable: true, desc: "Bare earth — a road or a worn patch." },
  { id: Terrain.Water, name: "Lake", blocks: true, speed: 0, sight: 1, buildable: false, desc: "Deep water. Impassable — armies go around." },
  { id: Terrain.Sand, name: "Sand", blocks: false, speed: 0.92, sight: 1, buildable: true, desc: "Loose sand; very slightly slower." },
  { id: Terrain.Forest, name: "Forest", blocks: false, speed: 0.55, sight: 0.75, buildable: false, desc: "Passable but slow, and you can't see far in. Nothing builds here." },
  { id: Terrain.Rock, name: "Mountain", blocks: true, speed: 0, sight: 1, buildable: false, desc: "Sheer rock. Impassable — the hardest barrier there is." },
  { id: Terrain.Hill, name: "Hill", blocks: false, speed: 0.85, sight: 1.2, buildable: true, desc: "High ground: slower to climb, but sees and shoots 20% further." },
  { id: Terrain.Marsh, name: "Marsh", blocks: false, speed: 0.6, sight: 0.9, buildable: false, desc: "Boggy. Slow going and nothing to show for it." },
  { id: Terrain.Snow, name: "Snow", blocks: false, speed: 0.88, sight: 1, buildable: true, desc: "Snowfield — a little heavy underfoot." },
  { id: Terrain.Shallow, name: "Shallows", blocks: false, speed: 0.5, sight: 1, buildable: false, desc: "Fordable water. Crossable, and a terrible place to be caught." },
];

/** Flat lookup tables, built once — these are read on the movement hot path. */
export const TERRAIN_BLOCKS: Uint8Array = (() => {
  const a = new Uint8Array(TERRAIN_COUNT);
  for (const d of TERRAIN_DEFS) a[d.id] = d.blocks ? 1 : 0;
  return a;
})();
export const TERRAIN_SPEED: Float32Array = (() => {
  const a = new Float32Array(TERRAIN_COUNT).fill(1);
  for (const d of TERRAIN_DEFS) a[d.id] = d.blocks ? 0 : d.speed;
  return a;
})();
export const TERRAIN_SIGHT: Float32Array = (() => {
  const a = new Float32Array(TERRAIN_COUNT).fill(1);
  for (const d of TERRAIN_DEFS) a[d.id] = d.sight;
  return a;
})();
export const TERRAIN_BUILDABLE: Uint8Array = (() => {
  const a = new Uint8Array(TERRAIN_COUNT);
  for (const d of TERRAIN_DEFS) a[d.id] = d.buildable ? 1 : 0;
  return a;
})();

export const terrainDef = (t: number): TerrainDef => TERRAIN_DEFS[t] ?? TERRAIN_DEFS[0];

// ---------------------------------------------------------------- biomes --

export type BiomeId = "temperate" | "arid" | "alpine" | "wetland";

export interface Biome {
  id: BiomeId;
  name: string;
  desc: string;
  /** The two ground types the open field is painted from. */
  ground: [Terrain, Terrain];
  /** What a patch of rough ground is here. */
  rough: Terrain;
  /** What sits at the edge of water. */
  shore: Terrain;
  /** Tint applied to the whole map, so a biome reads at a glance. */
  tint: string;
}

export const BIOMES: Biome[] = [
  {
    id: "temperate", name: "Temperate", desc: "Green country: grass, mixed woods, the odd lake.",
    ground: [Terrain.Grass, Terrain.GrassDark], rough: Terrain.Dirt, shore: Terrain.Sand, tint: "#7da65a",
  },
  {
    id: "arid", name: "Arid", desc: "Dust and rock. Sparse woods, mountains everywhere.",
    ground: [Terrain.Sand, Terrain.Dirt], rough: Terrain.Rock, shore: Terrain.Sand, tint: "#c8a463",
  },
  {
    id: "alpine", name: "Alpine", desc: "Snow and stone, cut by high ridges and cold lakes.",
    ground: [Terrain.Snow, Terrain.Grass], rough: Terrain.Rock, shore: Terrain.Shallow, tint: "#cfdae8",
  },
  {
    id: "wetland", name: "Wetland", desc: "Low, wet ground — marsh, shallows and thick forest.",
    ground: [Terrain.GrassDark, Terrain.Grass], rough: Terrain.Marsh, shore: Terrain.Shallow, tint: "#5f8a63",
  },
];

export const biomeById = (id: string): Biome => BIOMES.find((b) => b.id === id) ?? BIOMES[0];
