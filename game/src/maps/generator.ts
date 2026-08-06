// Seeded, symmetric skirmish map generation. Maps are 180°-rotationally
// symmetric so both players get identical starts: main base resources, a safe
// woodline, gold, berries, a natural expansion, and contested center gold.

import { RNG } from "../engine/rng";
import { TILE, START_RESOURCES } from "../content/balance";
import { Terrain, BiomeId, biomeById, TERRAIN_BLOCKS } from "./terrain_kinds";

export { Terrain } from "./terrain_kinds";

export interface ResourceSpawn {
  type: "tree" | "gold_mine" | "berries";
  x: number;
  y: number;
  amount: number;
}

export interface MapData {
  name: string;
  worldW: number;
  worldH: number;
  cols: number;
  rows: number;
  terrain: Uint8Array; // Terrain enum per cell, for rendering
  blockedCells: [number, number][]; // water/cliff cells (impassable)
  resources: ResourceSpawn[];
  starts: { x: number; y: number }[];
  startResources: { food: number; wood: number; gold: number };
  seed: number;
  /** Which palette and terrain mix this map is painted from. */
  biome: BiomeId;
  /** Set when the map came from the editor rather than the generator. */
  custom?: boolean;
  /** Game modes this map was authored for; empty means "any". */
  modes?: string[];
  /** True when starts are random (nomad) rather than authored. */
  nomad?: boolean;
}

export interface MapPreset {
  id: string;
  name: string;
  desc: string;
  size: number; // world units square
  forestry: number; // 0..1 density of extra forest
  water: number; // 0..1 amount of lakes/river
  chokes?: number; // 0..1 strength of forest barriers that carve lanes
  /** 0..1 — how much of the map is mountain ridge (a hard barrier). */
  mountains?: number;
  /** 0..1 — how much high ground there is to fight over. */
  hills?: number;
  /** 0..1 — how much of the "forest" is walkable woodland rather than trees. */
  woodland?: number;
  biome?: BiomeId;
}

export const PRESETS: MapPreset[] = [
  {
    id: "open_plains",
    name: "Open Plains",
    desc: "Wide open grassland. Armies clash early and often — cavalry country.",
    size: 3200,
    forestry: 0.25,
    water: 0.08,
    // Deliberately no hills or woodland. Warband Tactics borrows this preset as
    // its arena substrate, and the geography passes draw from the same RNG
    // stream as the resource scatter — so adding them here would silently move
    // every tree in the auto-battler's arena and re-tune a season of measured
    // balance work. "Wide open grassland" wants a bare field anyway.
  },
  {
    id: "black_forest",
    name: "Black Forest",
    desc: "Dense woods carve natural walls. Turtle up, then break through.",
    size: 3400,
    forestry: 0.85,
    water: 0.04,
    woodland: 0.55,
    hills: 0.15,
  },
  {
    id: "riverlands",
    name: "Riverlands",
    desc: "A winding river splits the field. Control the crossings.",
    size: 3400,
    forestry: 0.35,
    water: 0.55,
    hills: 0.3,
    woodland: 0.35,
  },
  {
    id: "highlands",
    name: "Highlands",
    desc: "Rolling country dotted with ponds and copses. A balanced all-rounder.",
    size: 3300,
    forestry: 0.5,
    water: 0.22,
    mountains: 0.45,
    hills: 0.7,
    woodland: 0.4,
  },
  {
    id: "islands",
    name: "Islands",
    desc: "Lakes carve the land into linked holds. Fight over the land bridges.",
    size: 3500,
    forestry: 0.3,
    water: 0.78,
    hills: 0.25,
    woodland: 0.3,
  },
  {
    id: "crossroads",
    name: "Crossroads",
    desc: "A small, near-bare arena. No room to hide — the better army wins, fast.",
    size: 2800,
    forestry: 0.16,
    water: 0.0,
    hills: 0.2,
  },
  {
    id: "gauntlet",
    name: "Gauntlet",
    desc: "Forest barriers funnel every attack through narrow lanes. Hold the gap.",
    size: 3400,
    forestry: 0.3,
    water: 0.05,
    mountains: 0.6,
    hills: 0.2,
    chokes: 0.85,
  },
];

// Richer nodes than before — more food/wood/gold banked in every patch.
const AMOUNT = { tree: 160, gold_mine: 1000, berries: 250 };

export function generateMap(
  presetId: string,
  seed: number,
  players = 2,
  nomad = false,
  alliances?: number[],
): MapData {
  const rng = new RNG(seed);
  // "random" rolls a real preset from the seed (so the map is still replayable).
  const preset =
    presetId === "random"
      ? PRESETS[rng.int(0, PRESETS.length - 1)]
      : PRESETS.find((p) => p.id === presetId) ?? PRESETS[0];
  // Grow the field with the player count so 8 realms aren't cramped (keeps the
  // per-player area roughly constant beyond 4 players).
  const sizeScale = players > 4 ? Math.sqrt(players / 4) : 1;
  const W = Math.round((preset.size * sizeScale) / TILE) * TILE;
  const H = W;
  const cols = Math.ceil(W / TILE);
  const rows = Math.ceil(H / TILE);
  const terrain = new Uint8Array(cols * rows);
  const blockedSet = new Set<number>();
  const resources: ResourceSpawn[] = [];
  const resCells = new Set<number>();

  const idx = (cx: number, cy: number) => cy * cols + cx;
  const inB = (cx: number, cy: number) => cx >= 1 && cy >= 1 && cx < cols - 1 && cy < rows - 1;
  // 180° rotational mirror.
  const mirror = (cx: number, cy: number): [number, number] => [cols - 1 - cx, rows - 1 - cy];

  // --- Base grass with patches of texture ---------------------------------
  for (let cy = 0; cy < rows; cy++) {
    for (let cx = 0; cx < cols; cx++) {
      terrain[idx(cx, cy)] = Terrain.Grass;
    }
  }
  const patches = Math.floor((cols * rows) / 240);
  for (let i = 0; i < patches; i++) {
    const cx = rng.int(0, cols - 1);
    const cy = rng.int(0, rows - 1);
    const r = rng.int(2, 5);
    const t = rng.bool(0.6) ? Terrain.GrassDark : Terrain.Dirt;
    for (let dy = -r; dy <= r; dy++) {
      for (let dx = -r; dx <= r; dx++) {
        if (dx * dx + dy * dy <= r * r && inB(cx + dx, cy + dy) && rng.bool(0.8)) {
          terrain[idx(cx + dx, cy + dy)] = t;
          const [mx, my] = mirror(cx + dx, cy + dy);
          terrain[idx(mx, my)] = t;
        }
      }
    }
  }

  // --- Start positions ------------------------------------------------------
  const margin = Math.floor(cols * 0.18);
  let startCells: [number, number][];
  let primaries: [number, number][];
  // In nomad maps resources surround the actual (random) spawns, not mirrors.
  let mirrorRes = true;

  if (nomad) {
    // Spawn anywhere on the map — random points, well spaced, on inland ground.
    const nMargin = Math.floor(cols * 0.14);
    const minSep = cols * (players >= 4 ? 0.3 : 0.42);
    const pts: [number, number][] = [];
    let guard = 0;
    while (pts.length < players && guard++ < 4000) {
      const cx = rng.int(nMargin, cols - 1 - nMargin);
      const cy = rng.int(nMargin, rows - 1 - nMargin);
      if (pts.every(([px, py]) => (px - cx) ** 2 + (py - cy) ** 2 > minSep * minSep)) pts.push([cx, cy]);
    }
    while (pts.length < players) pts.push([rng.int(nMargin, cols - 1 - nMargin), rng.int(nMargin, rows - 1 - nMargin)]);
    startCells = pts;
    primaries = pts; // each spawn gets its own (un-mirrored) resource ring
    mirrorRes = false;
  } else {
    // Evenly space every start on a ring around the centre — inherently fair
    // (each equidistant from the middle and its neighbours). Teammates are
    // seated next to each other so an alliance holds one arc of the map.
    const ringR = Math.min(cols, rows) * 0.5 - margin;
    const ccx = cols / 2;
    const ccy = rows / 2;
    const a0 = rng.range(0, Math.PI * 2);
    // Slot k (clockwise) → a team, ordered so same-alliance teams are adjacent.
    const order = Array.from({ length: players }, (_, t) => t).sort(
      (a, b) => (alliances?.[a] ?? a) - (alliances?.[b] ?? b) || a - b,
    );
    startCells = new Array(players);
    for (let k = 0; k < players; k++) {
      const ang = a0 + (k / players) * Math.PI * 2;
      const cx = Math.round(ccx + Math.cos(ang) * ringR);
      const cy = Math.round(ccy + Math.sin(ang) * ringR);
      startCells[order[k]] = [
        Math.max(margin, Math.min(cols - 1 - margin, cx)),
        Math.max(margin, Math.min(rows - 1 - margin, cy)),
      ];
    }
    // Every realm gets its own identical resource ring → a perfectly even start.
    primaries = startCells;
    mirrorRes = false;
  }
  const startCellA = startCells[0];
  const starts = startCells.map(([cx, cy]) => ({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 }));
  const startSafeR = 11; // cells kept clear of water/forest around each start

  const nearStart = (cx: number, cy: number, r = startSafeR) => {
    for (const [scx, scy] of startCells) {
      const dx = cx - scx;
      const dy = cy - scy;
      if (dx * dx + dy * dy < r * r) return true;
    }
    return false;
  };

  // --- Water: lakes or a central river -------------------------------------
  const addWater = (cx: number, cy: number) => {
    if (!inB(cx, cy) || nearStart(cx, cy)) return;
    terrain[idx(cx, cy)] = Terrain.Water;
    blockedSet.add(idx(cx, cy));
    const [mx, my] = mirror(cx, cy);
    if (!nearStart(mx, my)) {
      terrain[idx(mx, my)] = Terrain.Water;
      blockedSet.add(idx(mx, my));
    }
  };

  if (preset.id === "islands") {
    // Many lakes break the land into holds; corridors (carved below) keep every
    // base reachable so the map is always winnable without ships.
    const lakeCount = 24;
    for (let i = 0; i < lakeCount; i++) {
      const cx = rng.int(5, cols - 6);
      const cy = rng.int(5, rows - 6);
      const r = rng.int(3, 6);
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (dx * dx + dy * dy <= r * r) addWater(cx + dx, cy + dy);
        }
      }
    }
  } else if (preset.water > 0.4) {
    // Winding river through the middle with 2 crossings.
    const crossings: number[] = [
      Math.floor(rows * 0.3) + rng.int(-3, 3),
      Math.floor(rows * 0.62) + rng.int(-3, 3),
    ];
    let riverX = cols / 2 + rng.range(-6, 6);
    for (let cy = 0; cy < rows; cy++) {
      riverX += rng.range(-1.4, 1.4);
      riverX = Math.max(cols * 0.3, Math.min(cols * 0.7, riverX));
      const isCrossing = crossings.some((c) => Math.abs(cy - c) < 3);
      if (isCrossing) {
        // sandy ford
        for (let w = -2; w <= 2; w++) {
          const cx = Math.floor(riverX) + w;
          if (inB(cx, cy)) terrain[idx(cx, cy)] = Terrain.Sand;
        }
        continue;
      }
      const width = 2 + Math.floor(rng.range(0, 1.6));
      for (let w = -width; w <= width; w++) addWater(Math.floor(riverX) + w, cy);
    }
  } else {
    const lakes = Math.floor(preset.water * 14);
    for (let i = 0; i < lakes; i++) {
      const cx = rng.int(6, cols - 7);
      const cy = rng.int(6, rows - 7);
      const r = rng.int(2, 4);
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (dx * dx + dy * dy <= r * r) addWater(cx + dx, cy + dy);
        }
      }
    }
  }

  // --- Mountains: ridges you have to go around -----------------------------
  // Ridges are drawn as walks rather than blobs, because a blob is scenery and
  // a ridge is geography: it has a direction, a length, and two sides. They are
  // always mirrored, always kept off the starts, and always broken by at least
  // one pass, so a ridge shapes the approach without sealing anything off.
  const addRock = (cx: number, cy: number) => {
    if (!inB(cx, cy) || nearStart(cx, cy)) return;
    const i = idx(cx, cy);
    if (terrain[i] === Terrain.Water) return; // a lake already owns this cell
    terrain[i] = Terrain.Rock;
    blockedSet.add(i);
    resCells.add(i);
    const [mx, my] = mirror(cx, cy);
    if (!nearStart(mx, my) && terrain[idx(mx, my)] !== Terrain.Water) {
      terrain[idx(mx, my)] = Terrain.Rock;
      blockedSet.add(idx(mx, my));
      resCells.add(idx(mx, my));
    }
  };
  if (preset.mountains && preset.mountains > 0) {
    const ridges = 1 + Math.round(preset.mountains * 3);
    for (let r = 0; r < ridges; r++) {
      // Start somewhere off-centre and walk, drifting, for a good distance.
      let cx = rng.range(cols * 0.15, cols * 0.85);
      let cy = rng.range(rows * 0.15, rows * 0.85);
      let ang = rng.range(0, Math.PI * 2);
      const len = Math.round(rng.range(cols * 0.22, cols * 0.45));
      // One pass per ridge, at a random point along it, so there is always a way
      // through for whoever is willing to fight for it.
      const passAt = rng.int(Math.floor(len * 0.25), Math.floor(len * 0.75));
      const passWidth = 3;
      for (let stepI = 0; stepI < len; stepI++) {
        ang += rng.range(-0.22, 0.22);
        cx += Math.cos(ang);
        cy += Math.sin(ang);
        if (Math.abs(stepI - passAt) < passWidth) continue; // the gap
        const half = 1 + (rng.bool(0.35) ? 1 : 0);
        for (let dy = -half; dy <= half; dy++) {
          for (let dx = -half; dx <= half; dx++) {
            if (dx * dx + dy * dy > half * half + 0.5) continue;
            addRock(Math.round(cx) + dx, Math.round(cy) + dy);
          }
        }
      }
    }
  }

  // --- Hills: high ground worth taking --------------------------------------
  // Passable, slower to climb, and worth 20% more range and sight to whoever
  // holds them — so they are the thing to fight over rather than around.
  if (preset.hills && preset.hills > 0) {
    const count = Math.round(preset.hills * 10);
    for (let i = 0; i < count; i++) {
      const hx = rng.int(4, cols - 5);
      const hy = rng.int(4, rows - 5);
      const r = rng.int(2, 5);
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          const d2 = dx * dx + dy * dy;
          if (d2 > r * r) continue;
          // A soft edge, so a hill reads as a slope rather than a stamped disc.
          if (d2 > (r - 1) * (r - 1) && rng.bool(0.45)) continue;
          for (const [tx, ty] of [[hx + dx, hy + dy], mirror(hx + dx, hy + dy)] as [number, number][]) {
            if (!inB(tx, ty) || nearStart(tx, ty, 7)) continue;
            const ti = idx(tx, ty);
            if (blockedSet.has(ti)) continue; // never overwrite a barrier
            terrain[ti] = Terrain.Hill;
          }
        }
      }
    }
  }

  // --- Land bridges: guarantee every base connects to the centre -----------
  // On water-heavy maps lakes/rivers could otherwise wall a base off entirely
  // (we have no ships). Carving a passable lane from each start to the centre —
  // all lanes meeting there — keeps the whole map mutually reachable. Lane cells
  // are reserved so no tree/gold spawns later and re-blocks them.
  if (preset.id === "islands" || preset.water > 0.4) {
    const midC = Math.floor(cols / 2);
    const midR = Math.floor(rows / 2);
    const carveLand = (x0c: number, y0c: number, x1c: number, y1c: number, halfWidth: number) => {
      const steps = Math.ceil(Math.hypot(x1c - x0c, y1c - y0c));
      for (let s = 0; s <= steps; s++) {
        const t = s / steps;
        const cx = Math.round(x0c + (x1c - x0c) * t);
        const cy = Math.round(y0c + (y1c - y0c) * t);
        for (let w = -halfWidth; w <= halfWidth; w++) {
          for (let h = -halfWidth; h <= halfWidth; h++) {
            const nx = cx + w;
            const ny = cy + h;
            if (!inB(nx, ny)) continue;
            const i = idx(nx, ny);
            if (terrain[i] === Terrain.Water) {
              terrain[i] = Terrain.Sand; // a ford/causeway across the water
              blockedSet.delete(i);
            }
            resCells.add(i); // keep the lane clear of resource nodes
          }
        }
      }
    };
    for (const [scx, scy] of startCells) carveLand(scx, scy, midC, midR, 1);
  }

  // --- Resource helpers -----------------------------------------------------
  const placeRes = (type: ResourceSpawn["type"], cx: number, cy: number, mirrored = mirrorRes) => {
    if (!inB(cx, cy)) return;
    const i = idx(cx, cy);
    if (blockedSet.has(i) || resCells.has(i) || terrain[i] === Terrain.Water) return;
    resCells.add(i);
    resources.push({ type, x: cx * TILE + 4, y: cy * TILE + 4, amount: AMOUNT[type] });
    if (mirrored) {
      const [mx, my] = mirror(cx, cy);
      const mi = idx(mx, my);
      if (!blockedSet.has(mi) && !resCells.has(mi) && terrain[mi] !== Terrain.Water) {
        resCells.add(mi);
        resources.push({ type, x: mx * TILE + 4, y: my * TILE + 4, amount: AMOUNT[type] });
      }
    }
  };

  const cluster = (
    type: ResourceSpawn["type"],
    cx: number,
    cy: number,
    count: number,
    spread: number,
  ) => {
    let placed = 0;
    let guard = 0;
    while (placed < count && guard++ < count * 12) {
      const dx = rng.int(-spread, spread);
      const dy = rng.int(-spread, spread);
      const tx = cx + dx;
      const ty = cy + dy;
      const i = inB(tx, ty) ? idx(tx, ty) : -1;
      if (i >= 0 && !blockedSet.has(i) && !resCells.has(i) && terrain[i] !== Terrain.Water) {
        placeRes(type, tx, ty);
        placed++;
      }
    }
  };

  // --- Per-start resources (mirrored on standard maps) ----------------------
  for (const [acx, acy] of primaries) {
    // Main woodline: a fat arc of trees just outside the base.
    const woodAngle = rng.range(0, Math.PI * 2);
    const wx = acx + Math.round(Math.cos(woodAngle) * 9);
    const wy = acy + Math.round(Math.sin(woodAngle) * 9);
    cluster("tree", wx, wy, 20, 3);
    // A second woodline on the far side so wood never runs dry early.
    cluster("tree", acx - Math.round(Math.cos(woodAngle) * 9), acy - Math.round(Math.sin(woodAngle) * 9), 12, 3);
    // Berries close to the TC — a generous patch so early food isn't starved.
    const berryAngle = woodAngle + rng.range(1.5, 2.5);
    cluster("berries", acx + Math.round(Math.cos(berryAngle) * 6), acy + Math.round(Math.sin(berryAngle) * 6), 11, 2);
    // Gold a bit farther out.
    const goldAngle = woodAngle - rng.range(1.5, 2.5);
    cluster("gold_mine", acx + Math.round(Math.cos(goldAngle) * 8), acy + Math.round(Math.sin(goldAngle) * 8), 6, 1);
    // Natural expansion: berries + gold toward the middle.
    const ecx = Math.round(acx + (cols / 2 - acx) * 0.45);
    const ecy = Math.round(acy + (rows / 2 - acy) * 0.45);
    cluster("gold_mine", ecx, ecy, 5, 2);
    cluster("berries", ecx + rng.int(-4, 4), ecy + rng.int(-4, 4), 8, 2);
    cluster("tree", ecx + rng.int(-6, 6), ecy + rng.int(-6, 6), 12, 3);
  }

  // --- Contested center gold -------------------------------------------------
  cluster("gold_mine", Math.floor(cols / 2), Math.floor(rows / 2), 6, 3);

  // --- Scattered forest ------------------------------------------------------
  const forests = Math.floor(preset.forestry * 34);
  for (let i = 0; i < forests; i++) {
    const cx = rng.int(4, cols - 5);
    const cy = rng.int(4, rows - 5);
    if (nearStart(cx, cy, 13)) continue;
    cluster("tree", cx, cy, rng.int(6, 14), rng.int(2, 3));
  }

  // --- Walkable woodland under and around the tree lines --------------------
  // Tree *nodes* are harvestable obstacles; woodland is the ground they stand
  // on. It is passable but slow and short-sighted, which is what makes a wood a
  // place you can push through when you must and would rather go around — and
  // what makes an ambush in one work. Painting it around the existing forest
  // means the map's woods look like woods instead of a scatter of stumps.
  if (preset.woodland && preset.woodland > 0) {
    const treeCells = new Set<number>();
    for (const r of resources) {
      if (r.type !== "tree") continue;
      treeCells.add(idx(Math.floor(r.x / TILE), Math.floor(r.y / TILE)));
    }
    const reach = 1 + Math.round(preset.woodland * 2);
    for (const ti of treeCells) {
      const tx = ti % cols, ty = (ti / cols) | 0;
      for (let dy = -reach; dy <= reach; dy++) {
        for (let dx = -reach; dx <= reach; dx++) {
          if (dx * dx + dy * dy > reach * reach) continue;
          const nx = tx + dx, ny = ty + dy;
          if (!inB(nx, ny) || nearStart(nx, ny, 9)) continue;
          const i = idx(nx, ny);
          if (blockedSet.has(i)) continue;          // never soften a barrier
          if (terrain[i] === Terrain.Hill) continue; // nor bury high ground
          // Thin at the edges so a wood has a ragged border, not a stamped one.
          if (dx * dx + dy * dy > (reach - 1) * (reach - 1) && rng.bool(0.5)) continue;
          terrain[i] = Terrain.Forest;
        }
      }
    }
  }

  // --- Extra resources strewn across the whole map (richer maps) ------------
  const extraGold = 4 + Math.floor((cols * rows) / 2600);
  for (let i = 0; i < extraGold; i++) {
    const cx = rng.int(5, cols - 6);
    const cy = rng.int(5, rows - 6);
    if (nearStart(cx, cy, 9)) continue;
    cluster("gold_mine", cx, cy, rng.int(2, 4), 2);
  }
  const extraBerries = 4 + Math.floor((cols * rows) / 3000);
  for (let i = 0; i < extraBerries; i++) {
    const cx = rng.int(5, cols - 6);
    const cy = rng.int(5, rows - 6);
    if (nearStart(cx, cy, 9)) continue;
    cluster("berries", cx, cy, rng.int(3, 6), 2);
  }

  // --- Chokepoints: forest barriers with a central lane ---------------------
  if (preset.chokes && preset.chokes > 0) {
    const [acx, acy] = startCellA;
    const midC = cols / 2;
    const midR = rows / 2;
    const dx = midC - acx;
    const dy = midR - acy;
    const dl = Math.hypot(dx, dy) || 1;
    const ux = dx / dl;
    const uy = dy / dl;
    const px = -uy; // perpendicular to the base→center axis
    const py = ux;
    // Barrier sits two-thirds of the way out; mirror() gives the foe's twin.
    const bx = acx + ux * dl * 0.6;
    const by = acy + uy * dl * 0.6;
    const span = Math.floor(cols * 0.2);
    const gap = 3 + Math.floor(preset.chokes * 2); // open lane width (cells)
    for (let s = -span; s <= span; s++) {
      if (Math.abs(s) <= gap) continue; // leave the lane clear
      const cx = Math.round(bx + px * s);
      const cy = Math.round(by + py * s);
      if (!inB(cx, cy) || nearStart(cx, cy, 11)) continue;
      cluster("tree", cx, cy, 2, 1);
    }
  }

  const blockedCells: [number, number][] = [];
  for (const i of blockedSet) {
    blockedCells.push([i % cols, Math.floor(i / cols)]);
  }

  return {
    name: preset.name,
    worldW: W,
    worldH: H,
    cols,
    rows,
    terrain,
    blockedCells,
    resources,
    starts,
    startResources: { ...START_RESOURCES },
    seed,
    biome: preset.biome ?? "temperate",
    nomad,
  };
}
