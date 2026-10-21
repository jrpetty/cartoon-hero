// Warband Tactics board terrain — the arena stops being a flat 10×10.
//
// Every round rolls its own small set of features from its own seed, so the
// ground is a fresh problem each time: a boulder that takes a cell out of the
// board entirely, a rise that lets whoever holds it shoot further, a mire that
// bogs down whatever stands in it.
//
// Two rules make it fair rather than random punishment. Features are **mirrored
// across the centre line**, so both warbands face exactly the same ground — a
// boulder in your third rank has a twin in theirs. And the seed is shown in the
// UI, so a board you liked (or lost to) can be found again.

import { RNG } from "../engine/rng";

export type TerrainKind = "boulder" | "rise" | "mire";

export interface TerrainDef {
  kind: TerrainKind;
  name: string;
  desc: string;
  color: string;
  /** Nothing can be placed here at all. */
  blocks?: boolean;
  /** Percent modifiers for whatever stands on the cell. */
  rangePct?: number;
  speedPct?: number;
}

export const TERRAIN: Record<TerrainKind, TerrainDef> = {
  boulder: {
    kind: "boulder", name: "Boulder", color: "#8a8478",
    desc: "Solid rock. Nothing can stand here — it simply takes the cell off the board.",
    blocks: true,
  },
  rise: {
    kind: "rise", name: "Rise", color: "#9ac26a",
    desc: "High ground. Whoever holds it shoots 20% further.",
    rangePct: 20,
  },
  mire: {
    kind: "mire", name: "Mire", color: "#6a7a4a",
    desc: "Boggy ground. Anything standing in it moves 30% slower.",
    speedPct: -30,
  },
};

export interface TerrainFeature { kind: TerrainKind; col: number; row: number; }

/** The player owns cols 0-4; a feature there is mirrored to 9-col. */
export const MIRROR_COL = (col: number) => 9 - col;

/**
 * Roll a round's terrain. Features are generated on the left half and mirrored,
 * so the two halves are identical ground. Round 1 is deliberately clear — the
 * opener shouldn't also be a puzzle.
 */
export function rollTerrain(seed: number, round: number): TerrainFeature[] {
  if (round <= 1) return [];
  const rng = new RNG(seed);
  const count = rng.int(2, 4); // per half, so 4-8 on the board
  const kinds: TerrainKind[] = ["boulder", "rise", "mire"];
  const used = new Set<string>();
  const out: TerrainFeature[] = [];
  for (let i = 0; i < count; i++) {
    // Keep the front rank (col 4) clear so a wall always has somewhere to stand.
    const col = rng.int(0, 3);
    const row = rng.int(0, 9);
    const key = `${col},${row}`;
    if (used.has(key)) continue;
    used.add(key);
    const kind = kinds[rng.int(0, kinds.length - 1)];
    out.push({ kind, col, row });
    out.push({ kind, col: MIRROR_COL(col), row });
  }
  return out;
}

/** The feature on a cell, if any. */
export function terrainAt(features: TerrainFeature[], col: number, row: number): TerrainDef | null {
  const f = features.find((t) => t.col === col && t.row === row);
  return f ? TERRAIN[f.kind] : null;
}

/** True when a cell can't hold a unit. */
export function isBlocked(features: TerrainFeature[], col: number, row: number): boolean {
  return terrainAt(features, col, row)?.blocks === true;
}

/**
 * A short, human-readable code for a round's terrain seed, so a board can be
 * noted down and found again.
 */
export function terrainCode(seed: number): string {
  return (seed >>> 0).toString(36).toUpperCase().slice(-6).padStart(6, "0");
}

/** Parse a code back into a seed (returns null if it isn't one). */
export function parseTerrainCode(code: string): number | null {
  const clean = code.trim().toUpperCase();
  if (!/^[0-9A-Z]{1,6}$/.test(clean)) return null;
  const n = parseInt(clean, 36);
  return Number.isFinite(n) ? n : null;
}
