// Terrain pre-rendering. The full map is painted once into an offscreen canvas
// (at half resolution to save memory — the soft scale-up reads as painterly),
// then blitted per frame. Also builds the minimap base image.
//
// Style notes: tile colors are blended with smooth value-noise (no per-tile
// checkerboard), biome boundaries are painted as organic blobs, water gets a
// sandy shoreline and depth shading, and the ground is dressed with clustered
// tufts, flowers and pebbles plus large soft light patches.

import { MapData, Terrain } from "../maps/generator";
import { TILE } from "../content/balance";
import { PAL, shade, withAlpha } from "./palette";
import { RNG } from "../engine/rng";
import { blurMask } from "./fogblur";

export const TERRAIN_SCALE = 0.5;

/**
 * The scale the cache for *this* map is baked at.
 *
 * Half-scale is right for a normal map, but the cache is one canvas spanning
 * the whole world, and a 320-cell custom map is 10,240 world units across —
 * 5,120 pixels at half scale, which is past Safari's 4,096-pixel canvas limit
 * and would hand back a blank texture rather than an error. So large maps are
 * baked coarser. Nothing is lost: at the zoom you view a map that size from,
 * the extra resolution was sub-pixel anyway.
 */
const MAX_CACHE_PX = 3072;
export function terrainCacheScale(map: { worldW: number; worldH: number }): number {
  const longest = Math.max(map.worldW, map.worldH);
  return Math.min(TERRAIN_SCALE, MAX_CACHE_PX / Math.max(1, longest));
}

/** Smooth 2D value noise on a coarse lattice, bilinear + smoothstep. */
function makeNoise(rng: RNG, cols: number, rows: number, step: number) {
  const gw = Math.ceil(cols / step) + 2;
  const gh = Math.ceil(rows / step) + 2;
  const g = new Float32Array(gw * gh);
  for (let i = 0; i < g.length; i++) g[i] = rng.range(0, 1);
  const smooth = (t: number) => t * t * (3 - 2 * t);
  return (cx: number, cy: number): number => {
    const fx = cx / step;
    const fy = cy / step;
    const x0 = Math.floor(fx);
    const y0 = Math.floor(fy);
    const tx = smooth(fx - x0);
    const ty = smooth(fy - y0);
    const a = g[y0 * gw + x0];
    const b = g[y0 * gw + x0 + 1];
    const c = g[(y0 + 1) * gw + x0];
    const d = g[(y0 + 1) * gw + x0 + 1];
    return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
  };
}

export function buildTerrainCache(map: MapData): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  const scale = terrainCacheScale(map);
  canvas.width = Math.ceil(map.worldW * scale);
  canvas.height = Math.ceil(map.worldH * scale);
  const ctx = canvas.getContext("2d")!;
  const rng = new RNG(map.seed ^ 0xbeef);
  const t = TILE * scale;
  const at = (cx: number, cy: number): number =>
    cx >= 0 && cy >= 0 && cx < map.cols && cy < map.rows ? map.terrain[cy * map.cols + cx] : Terrain.Grass;
  const isWater = (cx: number, cy: number) => at(cx, cy) === Terrain.Water;

  // Two octaves of smooth noise drive all ground shading — variation flows
  // ACROSS tiles instead of changing at every tile edge (no checkerboard).
  const n1 = makeNoise(rng, map.cols, map.rows, 7);
  const n2 = makeNoise(rng, map.cols, map.rows, 2.3);
  const groundShade = (cx: number, cy: number) => (n1(cx, cy) * 0.72 + n2(cx, cy) * 0.28 - 0.5);

  // --- 1. Grass base, noise-shaded ----------------------------------------
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      ctx.fillStyle = shade(PAL.grass, groundShade(cx, cy) * 0.13);
      ctx.fillRect(cx * t, cy * t, t + 1, t + 1);
    }
  }

  // --- 2. Biome layers painted as organic blobs ---------------------------
  // Interior cells fill solid; boundary cells grow 2-3 round lobes so edges
  // wander naturally instead of stair-stepping.
  const paintBiome = (ter: number, color: (cx: number, cy: number) => string) => {
    for (let cy = 0; cy < map.rows; cy++) {
      for (let cx = 0; cx < map.cols; cx++) {
        if (at(cx, cy) !== ter) continue;
        const interior =
          at(cx - 1, cy) === ter && at(cx + 1, cy) === ter &&
          at(cx, cy - 1) === ter && at(cx, cy + 1) === ter;
        ctx.fillStyle = color(cx, cy);
        if (interior) {
          ctx.fillRect(cx * t - 1, cy * t - 1, t + 2, t + 2);
        } else {
          const mx = cx * t + t / 2;
          const my = cy * t + t / 2;
          ctx.beginPath();
          ctx.arc(mx, my, t * 0.66, 0, Math.PI * 2);
          ctx.arc(mx + rng.range(-t * 0.45, t * 0.45), my + rng.range(-t * 0.45, t * 0.45), t * 0.5, 0, Math.PI * 2);
          ctx.arc(mx + rng.range(-t * 0.45, t * 0.45), my + rng.range(-t * 0.45, t * 0.45), t * 0.42, 0, Math.PI * 2);
          ctx.fill();
        }
      }
    }
  };
  paintBiome(Terrain.GrassDark, (cx, cy) => shade(PAL.grassDark, groundShade(cx, cy) * 0.12));
  paintBiome(Terrain.Dirt, (cx, cy) => shade(PAL.dirt, groundShade(cx, cy) * 0.14));
  paintBiome(Terrain.Sand, (cx, cy) => shade(PAL.sand, groundShade(cx, cy) * 0.1));
  paintBiome(Terrain.Snow, (cx, cy) => shade("#dfe7f0", groundShade(cx, cy) * 0.08));
  paintBiome(Terrain.Marsh, (cx, cy) => shade("#4d5a3c", groundShade(cx, cy) * 0.16));
  paintBiome(Terrain.Shallow, (cx, cy) => shade("#4a7f96", groundShade(cx, cy) * 0.1));
  // Woodland floor: darker, mottled ground under the trees, so a wood reads as
  // a place rather than a scatter of trunks on a lawn.
  paintBiome(Terrain.Forest, (cx, cy) => shade("#2f4429", groundShade(cx, cy) * 0.2));
  // High ground gets a lit crown and a shaded skirt, which is the cheapest way
  // to say "this is raised" on a flat top-down map.
  paintBiome(Terrain.Hill, (cx, cy) => shade("#6d8a4c", groundShade(cx, cy) * 0.12));
  // Relief and water are painted per pixel from smooth fields — see
  // paintReliefAndWater below. It used to be a circle per hill cell and a
  // square per water cell, which is exactly the tile grid those were meant to
  // hide: hills read as a quilt of discs and every river as a staircase.
  const wetAt = paintReliefAndWater(ctx, canvas, map, t);

  // Mountains: a shaded mass with a lit north face, drawn per cell so a ridge
  // reads as one range instead of a row of identical lumps.
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      if (at(cx, cy) !== Terrain.Rock) continue;
      // Not every cell gets a peak, and no two peaks are the same size or sit
      // on the same spot in their cell. One identical cone per cell on a
      // regular grid read as wallpaper, not a mountain range — and the
      // hillshading underneath now carries the mass, so the peaks only have
      // to punctuate it.
      const interiorRock = at(cx - 1, cy) === Terrain.Rock && at(cx + 1, cy) === Terrain.Rock
        && at(cx, cy - 1) === Terrain.Rock && at(cx, cy + 1) === Terrain.Rock;
      if (interiorRock && rng.bool(0.35)) continue;
      const k = rng.range(0.72, 1.18);
      const mx = cx * t + t / 2 + rng.range(-t * 0.28, t * 0.28);
      const my = cy * t + t / 2 + rng.range(-t * 0.22, t * 0.22);
      ctx.save();
      ctx.translate(mx, my);
      ctx.scale(k, k);
      ctx.translate(-mx, -my);
      ctx.fillStyle = "rgba(10,10,12,0.5)";
      ctx.beginPath(); ctx.ellipse(mx, my + t * 0.3, t * 0.72, t * 0.42, 0, 0, Math.PI * 2); ctx.fill();
      const g = ctx.createLinearGradient(mx, my - t * 0.7, mx, my + t * 0.6);
      g.addColorStop(0, shade("#9aa0a8", rng.range(-0.05, 0.08)));
      g.addColorStop(1, "#3c4046");
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.moveTo(mx - t * 0.72, my + t * 0.5);
      ctx.lineTo(mx - t * 0.4, my - t * 0.45 + rng.range(-t * 0.12, t * 0.12));
      ctx.lineTo(mx + t * 0.05, my - t * 0.78 + rng.range(-t * 0.14, t * 0.14));
      ctx.lineTo(mx + t * 0.45, my - t * 0.3 + rng.range(-t * 0.12, t * 0.12));
      ctx.lineTo(mx + t * 0.72, my + t * 0.5);
      ctx.closePath(); ctx.fill();
      // Snowline / lit edge.
      ctx.fillStyle = "rgba(232,238,246,0.5)";
      ctx.beginPath();
      ctx.moveTo(mx + t * 0.05, my - t * 0.78);
      ctx.lineTo(mx - t * 0.16, my - t * 0.42);
      ctx.lineTo(mx + t * 0.24, my - t * 0.4);
      ctx.closePath(); ctx.fill();
      ctx.restore();
    }
  }

  // --- 3/4. Shore and water are painted per pixel above. Only the static
  // wave strokes remain here; the live glints animate on top at runtime.
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      if (!isWater(cx, cy)) continue;
      const deep = isWater(cx - 1, cy) && isWater(cx + 1, cy) && isWater(cx, cy - 1) && isWater(cx, cy + 1);
      if (!deep || !rng.bool(0.2)) continue;
      ctx.strokeStyle = withAlpha("#dff2ff", 0.2);
      ctx.lineWidth = 1;
      const wx = cx * t + rng.range(t * 0.2, t * 0.8);
      const wy = cy * t + rng.range(t * 0.2, t * 0.8);
      ctx.beginPath();
      ctx.arc(wx, wy, rng.range(2, 4), Math.PI * 0.15, Math.PI * 0.85);
      ctx.stroke();
    }
  }

  // --- 5. Large soft light/shadow patches break up the flatness -----------
  const patches = 16;
  for (let i = 0; i < patches; i++) {
    const px = rng.range(0, canvas.width);
    const py = rng.range(0, canvas.height);
    const pr = rng.range(t * 8, t * 18);
    const light = rng.bool(0.5);
    const g = ctx.createRadialGradient(px, py, 0, px, py, pr);
    g.addColorStop(0, withAlpha(light ? "#ffffff" : "#1c3a14", light ? 0.05 : 0.07));
    g.addColorStop(1, withAlpha("#ffffff", 0));
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(px, py, pr, 0, Math.PI * 2);
    ctx.fill();
  }

  // --- 6. Ground dressing: tuft clusters, flowers, pebbles ----------------
  const clusters = Math.floor((map.cols * map.rows) / 26);
  for (let i = 0; i < clusters; i++) {
    const cx = rng.int(0, map.cols - 1);
    const cy = rng.int(0, map.rows - 1);
    const ter = at(cx, cy);
    if (ter === Terrain.Water) continue;
    const baseX = cx * t + rng.range(0, t);
    const baseY = cy * t + rng.range(0, t);
    if (ter === Terrain.Dirt || ter === Terrain.Sand) {
      // Pebble cluster.
      const n = rng.int(2, 5);
      for (let k = 0; k < n; k++) {
        const px = baseX + rng.range(-t * 0.8, t * 0.8);
        const py = baseY + rng.range(-t * 0.8, t * 0.8);
        if (wetAt(px, py)) continue;
        ctx.fillStyle = shade(rng.bool(0.3) ? PAL.stone : PAL.dirtDark, rng.range(-0.12, 0.12));
        ctx.beginPath();
        ctx.ellipse(px, py, rng.range(0.9, 2.1), rng.range(0.7, 1.5), rng.range(0, 3), 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = withAlpha("#ffffff", 0.18);
        ctx.beginPath();
        ctx.arc(px - 0.4, py - 0.5, 0.5, 0, Math.PI * 2);
        ctx.fill();
      }
    } else if (rng.bool(0.3)) {
      // Flower cluster — tiny colored blooms with bright centres.
      const bloom = ["#f4f0ff", "#ffd9e8", "#f7e07a", "#e8907a"][rng.int(0, 3)];
      const n = rng.int(2, 5);
      for (let k = 0; k < n; k++) {
        const px = baseX + rng.range(-t * 0.7, t * 0.7);
        const py = baseY + rng.range(-t * 0.7, t * 0.7);
        if (wetAt(px, py)) continue;
        ctx.fillStyle = bloom;
        for (let p2 = 0; p2 < 4; p2++) {
          const a = (p2 / 4) * Math.PI * 2 + rng.range(0, 0.6);
          ctx.beginPath();
          ctx.arc(px + Math.cos(a) * 1.1, py + Math.sin(a) * 1.1, 0.9, 0, Math.PI * 2);
          ctx.fill();
        }
        ctx.fillStyle = "#f2c14b";
        ctx.beginPath();
        ctx.arc(px, py, 0.7, 0, Math.PI * 2);
        ctx.fill();
      }
    } else {
      // Grass tuft cluster — small bent blades, varied greens.
      const n = rng.int(3, 7);
      for (let k = 0; k < n; k++) {
        const px = baseX + rng.range(-t * 0.9, t * 0.9);
        const py = baseY + rng.range(-t * 0.9, t * 0.9);
        // Clusters scatter up to a cell from where they were seeded, which
        // used to plant grass in the shallows of every river.
        if (wetAt(px, py)) continue;
        ctx.strokeStyle = shade(rng.bool() ? PAL.grassShade : PAL.grassDark, rng.range(-0.06, 0.14));
        ctx.lineWidth = 1;
        const h = rng.range(2.2, 5);
        const lean = rng.range(-2, 2);
        ctx.beginPath();
        ctx.moveTo(px, py);
        ctx.quadraticCurveTo(px + lean * 0.4, py - h * 0.6, px + lean, py - h);
        ctx.stroke();
      }
    }
  }

  return canvas;
}

/** Small terrain image for the minimap background. */
export function buildMinimapBase(map: MapData, size: number): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d")!;
  const rng = new RNG(map.seed ^ 0xfeed);
  const noise = makeNoise(rng, map.cols, map.rows, 6);
  const sx = size / map.cols;
  const sy = size / map.rows;
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      const ter = map.terrain[cy * map.cols + cx];
      const base =
        ter === Terrain.Water ? PAL.water :
        ter === Terrain.Dirt ? PAL.dirt :
        ter === Terrain.Sand ? PAL.sand :
        ter === Terrain.Rock ? "#6a6f76" :
        ter === Terrain.Hill ? "#7f9c5b" :
        ter === Terrain.Forest ? "#2f4429" :
        ter === Terrain.Marsh ? "#4d5a3c" :
        ter === Terrain.Snow ? "#dfe7f0" :
        ter === Terrain.Shallow ? "#4a7f96" :
        ter === Terrain.GrassDark ? PAL.grassDark : PAL.grass;
      ctx.fillStyle = shade(base, (noise(cx, cy) - 0.5) * 0.12);
      ctx.fillRect(cx * sx, cy * sy, sx + 1, sy + 1);
    }
  }
  return canvas;
}

// ------------------------------------------------------ relief and water --

const hexRgb = (h: string): [number, number, number] => [
  parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16),
];

const smoothstep = (a: number, b: number, x: number) => {
  const t = Math.max(0, Math.min(1, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
};

/**
 * Bilinear sample of a per-cell field at cell-space (u, v), where a cell's
 * centre sits at (cx + 0.5, cy + 0.5). Clamped at the map edge.
 */
function sampleField(f: Float32Array, cols: number, rows: number, u: number, v: number): number {
  const x = Math.max(0, Math.min(cols - 1.001, u - 0.5));
  const y = Math.max(0, Math.min(rows - 1.001, v - 0.5));
  const x0 = x | 0, y0 = y | 0;
  const fx = x - x0, fy = y - y0;
  const i = y0 * cols + x0;
  const a = f[i], b = f[i + 1], c = f[i + cols], d = f[i + cols + 1];
  return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
}

/**
 * Water, shoreline and hillshading, painted per pixel from smooth fields.
 *
 * Runs once when the map loads, so it can afford to be thorough — nothing here
 * costs anything per frame. It replaces a square per water cell and a circle
 * per hill cell, which between them drew the tile grid the rest of the painter
 * works hard to hide: every river a staircase of two-tone blue blocks, every
 * hill a quilt of discs.
 *
 * **The outline of the water has to agree with the sim.** Water is impassable,
 * so a picture that shows land where the pathfinder sees water is a lie with
 * consequences. A plain blur would round the coast beautifully and also erase
 * any channel one cell wide or any single-cell pond. The outline therefore comes
 * from bilinear interpolation of the *raw* cells — marching-squares style, which
 * keeps every water cell and a one-cell channel exactly one cell wide, but turns
 * a staircase into a straight diagonal — nudged by a light blur for roundness.
 * The heavier blur is used only for things that cannot change topology: how
 * deep the water looks and how the ground is lit.
 *
 * **Hills are lit, not tinted.** High ground becomes a height field, blurred so
 * it rolls, and each pixel is shaded by how its slope faces a light from the
 * north-west. That is the standard cartographic hillshade, and it is what makes
 * a ridge read as a ridge instead of a patch of darker grass.
 */
function paintReliefAndWater(
  ctx: CanvasRenderingContext2D, canvas: HTMLCanvasElement, map: MapData, t: number,
): (px: number, py: number) => boolean {
  const cols = map.cols, rows = map.rows, n = cols * rows;
  const raw = new Float32Array(n);   // 1 = water
  const elev = new Float32Array(n);  // height: hills, then mountains higher
  const shallow = new Uint8Array(n);
  for (let i = 0; i < n; i++) {
    const ter = map.terrain[i];
    if (ter === Terrain.Water) raw[i] = 1;
    if (ter === Terrain.Shallow) shallow[i] = 1;
    elev[i] = ter === Terrain.Hill ? 1 : ter === Terrain.Rock ? 1.7 : 0;
  }
  const scratch = new Float32Array(n);
  // The shoreline: a smooth field, pinned so every cell centre keeps the side
  // of 0.5 the sim puts it on. Bilinear interpolation passes exactly through
  // the cell centres, so the pinned field can round a coast as much as the blur
  // likes without ever drying a water cell or flooding a land one — a lone
  // pond stays a pond, a one-cell channel stays open, a one-cell island stays
  // an island. Pinning to 0.6 / 0.4 rather than 0.5 keeps them visible.
  const shore = raw.slice();
  blurMask(shore, scratch, cols, rows, 1); blurMask(shore, scratch, cols, rows, 1);
  // How far sand reaches inland comes from the same blur taken one step
  // further and left unpinned, so a beach rounds off instead of following the
  // waterline into every corner — a sandbar's grassy middle used to come out
  // as a star.
  const beach = shore.slice(); blurMask(beach, scratch, cols, rows, 1);
  for (let i = 0; i < n; i++) shore[i] = raw[i] ? Math.max(shore[i], 0.6) : Math.min(shore[i], 0.4);
  const depth = raw.slice(); blurMask(depth, scratch, cols, rows, 2); blurMask(depth, scratch, cols, rows, 2);
  const height = elev.slice(); blurMask(height, scratch, cols, rows, 1); blurMask(height, scratch, cols, rows, 1);

  // Light from the north-west and a little above: the convention every map
  // reader expects, and the one that makes hills read as raised, not sunken.
  //
  // The shade is worked out once per *cell*, from central differences of the
  // height field, and only then interpolated across the pixels. Taking the
  // slope per pixel from a bilinear height field looks equivalent and isn't:
  // bilinear height has a slope that jumps at every cell boundary, and the
  // first version of this painted each hill as a stack of flat facets — the
  // tile grid again, just in shading instead of colour.
  const Lx = -0.55, Ly = -0.68, Lz = 0.48;
  const Ln = Math.hypot(Lx, Ly, Lz);
  const lx = Lx / Ln, ly = Ly / Ln, lz = Lz / Ln;
  const RELIEF = 1.6; // how steep a hill's flank reads
  const lit = new Float32Array(n);
  for (let cy = 0; cy < rows; cy++) {
    for (let cx = 0; cx < cols; cx++) {
      const i = cy * cols + cx;
      const l = height[cy * cols + Math.max(0, cx - 1)], rr = height[cy * cols + Math.min(cols - 1, cx + 1)];
      const u = height[Math.max(0, cy - 1) * cols + cx], dd = height[Math.min(rows - 1, cy + 1) * cols + cx];
      const nx = -(rr - l) * 0.5 * RELIEF, ny = -(dd - u) * 0.5 * RELIEF;
      lit[i] = (nx * lx + ny * ly + lz) / Math.hypot(nx, ny, 1) - lz;
    }
  }

  // Cells worth touching at all: near water, or anywhere the light changes. On
  // a typical map that is a small fraction of the canvas, and skipping the rest
  // is most of what keeps this fast on the largest maps. A pixel's value is
  // interpolated from its own cell and the ring around it, so a cell counts if
  // anything in that ring does — gating on the cell alone left a hard edge
  // wherever a hill's shadow reached one cell further than its height did.
  const RELIEF_NEAR = 1, SHORE_NEAR = 2;
  const busy = new Uint8Array(n);
  for (let cy = 0; cy < rows; cy++) {
    for (let cx = 0; cx < cols; cx++) {
      let m = 0;
      for (let dy = -1; dy <= 1; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          const nx = cx + dx, ny = cy + dy;
          if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
          const j = ny * cols + nx;
          if (height[j] > 0.001 || lit[j] !== 0) m |= RELIEF_NEAR;
          if (shore[j] > 0.001 || beach[j] > 0.001) m |= SHORE_NEAR;
        }
      }
      busy[cy * cols + cx] = m;
    }
  }

  // The pass never reads the canvas back. Everything it does to a pixel —
  // shade, sunlight, sand, water — is expressed as layers of paint laid *over*
  // whatever is there, accumulated per pixel into one overlay that is then
  // drawn on in a single blit. That matters twice over: reading back a canvas
  // this size was half the cost of the whole bake, and in a browser a readback
  // can quietly move the canvas off the GPU, which would make every frame's
  // terrain blit slower for the rest of the match.
  const W = canvas.width, H = canvas.height;
  const overlay = document.createElement("canvas");
  overlay.width = W;
  overlay.height = H;
  const octx = overlay.getContext("2d")!;
  const img = octx.createImageData(W, H);
  const d = img.data;
  const [er, eg, eb] = hexRgb(PAL.waterEdge);
  const [wr, wg, wb] = hexRgb(PAL.water);
  const [dr, dg, db] = hexRgb(PAL.waterDeep);
  const [sr, sg, sb] = hexRgb(PAL.sand);

  // Every field is sampled at the same point, so the bilinear weights are
  // worked out once per pixel and shared — and since a pixel's column fixes its
  // horizontal weight and its row the vertical one, those come from two small
  // tables.
  const X0 = new Int32Array(W), FX = new Float32Array(W);
  for (let px = 0; px < W; px++) {
    const x = Math.max(0, Math.min(cols - 1.001, (px + 0.5) / t - 0.5));
    X0[px] = x | 0; FX[px] = x - (x | 0);
  }
  const YI = new Int32Array(H), FY = new Float32Array(H);
  for (let py = 0; py < H; py++) {
    const y = Math.max(0, Math.min(rows - 1.001, (py + 0.5) / t - 0.5));
    YI[py] = (y | 0) * cols; FY[py] = y - (y | 0);
  }

  for (let cy = 0; cy < rows; cy++) {
    for (let cx = 0; cx < cols; cx++) {
      const m = busy[cy * cols + cx];
      if (!m) continue;
      const relief = (m & RELIEF_NEAR) !== 0, near = (m & SHORE_NEAR) !== 0;
      const px0 = Math.floor(cx * t), px1 = Math.min(W, Math.floor((cx + 1) * t));
      const py0 = Math.floor(cy * t), py1 = Math.min(H, Math.floor((cy + 1) * t));
      const ownShallow = shallow[cy * cols + cx] === 1;
      for (let py = py0; py < py1; py++) {
        const fy = FY[py], row = YI[py];
        for (let px = px0; px < px1; px++) {
          const i = row + X0[px], fx = FX[px];
          const w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
          const i01 = i + cols;
          const o = (py * W + px) * 4;

          // --- water and shore ---
          const c = near ? shore[i] * w00 + shore[i + 1] * w10 + shore[i01] * w01 + shore[i01 + 1] * w11 : 0;
          if (c >= 0.5) {
            const dp = depth[i] * w00 + depth[i + 1] * w10 + depth[i01] * w01 + depth[i01 + 1] * w11;
            const deep = smoothstep(0.45, 0.95, dp);
            // edge -> body -> deep, so a lake has a lit rim and a dark heart.
            let wr2: number, wg2: number, wb2: number;
            if (deep < 0.5) {
              const k = deep / 0.5;
              wr2 = er + (wr - er) * k; wg2 = eg + (wg - eg) * k; wb2 = eb + (wb - eb) * k;
            } else {
              const k = (deep - 0.5) / 0.5;
              wr2 = wr + (dr - wr) * k; wg2 = wg + (dg - wg) * k; wb2 = wb + (db - wb) * k;
            }
            // A thin line of foam where water meets land.
            const foam = 1 - smoothstep(0.5, 0.58, c);
            d[o] = wr2 + (232 - wr2) * foam * 0.55;
            d[o + 1] = wg2 + (244 - wg2) * foam * 0.55;
            d[o + 2] = wb2 + (246 - wb2) * foam * 0.55;
            d[o + 3] = 255;
            continue;
          }

          // Paint laid over the ground, accumulated: the result is
          // ground * keep + (ar, ag, ab).
          let keep = 1, ar = 0, ag = 0, ab = 0;

          // --- hillshade ---
          if (relief) {
            const s = lit[i] * w00 + lit[i + 1] * w10 + lit[i01] * w01 + lit[i01 + 1] * w11;
            if (s > 0.001) {
              // A sunlit flank mixes toward warm light rather than being
              // scaled up: multiplying grass by 1.3 makes it neon, not bright.
              const a = Math.min(0.4, s * 0.8);
              ar = ar * (1 - a) + 250 * a; ag = ag * (1 - a) + 236 * a; ab = ab * (1 - a) + 188 * a; keep *= 1 - a;
            } else if (s < -0.001) {
              // Shade darkens and cools, as it does outdoors — a pure
              // brightness change reads as dirt, not slope.
              const a = Math.min(0.8, -s * 1.1);
              ar = ar * (1 - a) + 16 * a; ag = ag * (1 - a) + 26 * a; ab = ab * (1 - a) + 44 * a; keep *= 1 - a;
            }
            const hc = height[i] * w00 + height[i + 1] * w10 + height[i01] * w01 + height[i01 + 1] * w11;
            if (hc > 0.002) {
              // Tops a touch drier and lighter, so the crest of a range stands out.
              const a = Math.min(1, hc) * 0.05;
              ar = ar * (1 - a) + 235 * a; ag = ag * (1 - a) + 230 * a; ab = ab * (1 - a) + 190 * a; keep *= 1 - a;
            }
          }

          // Sand, strongest right at the waterline, fading inland over about a
          // cell, and darker where it's wet in the last stretch.
          if (near && !ownShallow) {
            const bc = beach[i] * w00 + beach[i + 1] * w10 + beach[i01] * w01 + beach[i01 + 1] * w11;
            const a = Math.max(smoothstep(0.3, 0.5, c), smoothstep(0.1, 0.4, bc)) * 0.88;
            if (a > 0) {
              ar = ar * (1 - a) + sr * a; ag = ag * (1 - a) + sg * a; ab = ab * (1 - a) + sb * a; keep *= 1 - a;
              const wet = smoothstep(0.4, 0.5, c) * 0.18;
              if (wet > 0) { ar *= 1 - wet; ag *= 1 - wet; ab *= 1 - wet; keep *= 1 - wet; }
            }
          }

          const alpha = 1 - keep;
          if (alpha <= 0.002) continue;
          d[o] = ar / alpha;
          d[o + 1] = ag / alpha;
          d[o + 2] = ab / alpha;
          d[o + 3] = alpha * 255;
        }
      }
    }
  }
  octx.putImageData(img, 0, 0);
  ctx.drawImage(overlay, 0, 0);
  // Where the painted water is, for anything dressed on afterwards. A little
  // inside the waterline, so a tuft can stand on the wet sand but not in the
  // river.
  return (px, py) => sampleField(shore, cols, rows, px / t, py / t) >= 0.46;
}

// --------------------------------------------------------- ground detail --

/** Cheap deterministic hash per cell, so detail never shimmers between frames. */
function cellHash(cx: number, cy: number, n: number): number {
  let v = (cx * 374761393 + cy * 668265263 + n * 2246822519) | 0;
  v = Math.imul(v ^ (v >>> 13), 1274126177);
  return ((v ^ (v >>> 16)) >>> 0) / 4294967296;
}

/**
 * Crisp ground detail, drawn live in world space over the blitted cache.
 *
 * The terrain cache is baked at half resolution, which is right for the zoom
 * you fight at but turns to soft green mush the moment you lean in — at 1.6×
 * the cache is magnified over three times and every tuft and pebble baked into
 * it is a smear. Rather than bake a bigger texture (the cache already has to
 * stay under Safari's canvas limit on the largest maps), the close-up detail is
 * drawn fresh each frame for the handful of cells actually on screen.
 *
 * It fades in with zoom so nothing pops, and is skipped entirely when zoomed
 * out — where it would be sub-pixel noise costing thousands of draws a frame.
 */
export function drawGroundDetail(
  ctx: CanvasRenderingContext2D,
  map: MapData,
  vx0: number, vy0: number, vx1: number, vy1: number,
  zoom: number,
  time: number,
) {
  // Detail exists to rescue the close-up view: the cache is baked at half
  // resolution, so it only turns to mush once you lean in past about 0.85.
  // Below that the marks would be sub-pixel noise costing real time — and the
  // cost is *worse* zoomed out, because a wider view holds more cells, which is
  // exactly backwards from where the detail is wanted.
  const strength = Math.min(1, (zoom - 0.85) / 0.45);
  if (strength <= 0.02) return;

  const c0x = Math.max(0, Math.floor(vx0 / TILE));
  const c0y = Math.max(0, Math.floor(vy0 / TILE));
  const c1x = Math.min(map.cols - 1, Math.ceil(vx1 / TILE));
  const c1y = Math.min(map.rows - 1, Math.ceil(vy1 / TILE));
  if (c1x < c0x || c1y < c0y) return;
  if ((c1x - c0x + 1) * (c1y - c0y + 1) > 3000) return;

  ctx.save();
  ctx.globalAlpha = strength;
  ctx.lineCap = "round";
  ctx.lineWidth = 1.3;

  const GRASS_TONES = [PAL.grassShade, PAL.grassDark, "#84bd57"];

  for (let cy = c0y; cy <= c1y; cy++) {
    for (let cx = c0x; cx <= c1x; cx++) {
      const t = map.terrain[cy * map.cols + cx];
      const ox = cx * TILE;
      const oy = cy * TILE;

      switch (t) {
        case Terrain.Hill: {
          // The relief itself is baked into the cache as hillshading; this
          // only adds the dry upland grass on top. (Lit and shadowed bands per
          // cell used to live here too, and drew the tile grid right back in.)
          // Dry upland tufts — straight, short, and fewer than on meadow.
          ctx.strokeStyle = "rgba(147,173,99,0.6)";
          ctx.beginPath();
          for (let i = 0; i < 3; i++) {
            const px = ox + cellHash(cx, cy, i) * TILE;
            const py = oy + cellHash(cx, cy, i + 7) * TILE;
            ctx.moveTo(px, py);
            ctx.lineTo(px + 1, py - 3.5);
          }
          ctx.stroke();
          break;
        }
        case Terrain.Grass:
        case Terrain.GrassDark: {
          // One path per cell, three tones interleaved: enough variation to
          // read as meadow without a stroke call per blade.
          const sway = Math.sin(time * 0.7 + cx * 0.4 + cy * 0.3) * 1.1;
          for (let tone = 0; tone < 3; tone++) {
            ctx.strokeStyle = withAlpha(GRASS_TONES[tone], 0.72);
            ctx.beginPath();
            for (let i = tone; i < 4; i += 3) {
              const px = ox + cellHash(cx, cy, i) * TILE;
              const py = oy + cellHash(cx, cy, i + 7) * TILE;
              const hgt = 3 + cellHash(cx, cy, i + 13) * 3.5;
              ctx.moveTo(px, py);
              ctx.lineTo(px + sway, py - hgt);
            }
            ctx.stroke();
          }
          break;
        }
        case Terrain.Forest: {
          // Crowns are *lighter* than the forest floor the cache paints. Dark
          // on dark was why the first attempt at this was invisible even though
          // it was drawing: a wood from above is lit tops over shadowed gaps,
          // not a uniform dark disc.
          for (let i = 0; i < 3; i++) {
            const px = ox + 7 + cellHash(cx, cy, i) * (TILE - 14);
            const py = oy + 7 + cellHash(cx, cy, i + 3) * (TILE - 14);
            const r = 6.5 + cellHash(cx, cy, i + 11) * 3;
            ctx.fillStyle = "rgba(22,36,15,0.72)";
            ctx.beginPath(); ctx.arc(px + 1, py + 2.5, r, 0, Math.PI * 2); ctx.fill();
            ctx.fillStyle = withAlpha(shade(PAL.foliage3, cellHash(cx, cy, i + 5) * 0.2 - 0.04), 0.96);
            ctx.beginPath(); ctx.arc(px, py - 1, r * 0.86, 0, Math.PI * 2); ctx.fill();
            ctx.fillStyle = "rgba(143,199,106,0.4)";
            ctx.beginPath(); ctx.arc(px - r * 0.32, py - r * 0.45, r * 0.3, 0, Math.PI * 2); ctx.fill();
          }
          break;
        }
        case Terrain.Rock: {
          ctx.fillStyle = "rgba(181,175,163,0.5)";
          ctx.beginPath();
          for (let i = 0; i < 3; i++) {
            const px = ox + 4 + cellHash(cx, cy, i) * (TILE - 8);
            const py = oy + 4 + cellHash(cx, cy, i + 7) * (TILE - 8);
            const r = 2 + cellHash(cx, cy, i + 13) * 3;
            ctx.moveTo(px - r, py + r * 0.6);
            ctx.lineTo(px - r * 0.3, py - r);
            ctx.lineTo(px + r, py + r * 0.2);
            ctx.closePath();
          }
          ctx.fill();
          break;
        }
        case Terrain.Dirt:
        case Terrain.Sand:
        case Terrain.Snow: {
          ctx.fillStyle = t === Terrain.Snow ? "rgba(255,255,255,0.45)"
            : t === Terrain.Sand ? "rgba(181,175,163,0.4)" : "rgba(148,119,77,0.45)";
          ctx.beginPath();
          for (let i = 0; i < 2; i++) {
            const px = ox + cellHash(cx, cy, i) * TILE;
            const py = oy + cellHash(cx, cy, i + 7) * TILE;
            const r = 0.9 + cellHash(cx, cy, i + 13) * 1.3;
            ctx.moveTo(px + r, py);
            ctx.arc(px, py, r, 0, Math.PI * 2);
          }
          ctx.fill();
          break;
        }
        case Terrain.Marsh: {
          const sway = Math.sin(time * 1.1 + cx * 0.5) * 2;
          ctx.strokeStyle = "rgba(109,122,74,0.55)";
          ctx.lineWidth = 1;
          ctx.beginPath();
          for (let i = 0; i < 3; i++) {
            const px = ox + cellHash(cx, cy, i) * TILE;
            const py = oy + cellHash(cx, cy, i + 7) * TILE;
            const hgt = 5 + cellHash(cx, cy, i + 13) * 4;
            ctx.moveTo(px, py);
            ctx.lineTo(px + sway, py - hgt);
          }
          ctx.stroke();
          ctx.lineWidth = 1.3;
          break;
        }
        default:
          break; // water and shallows have their own glints
      }
    }
  }
  ctx.restore();
}
