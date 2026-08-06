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

export const TERRAIN_SCALE = 0.5;

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
  canvas.width = Math.ceil(map.worldW * TERRAIN_SCALE);
  canvas.height = Math.ceil(map.worldH * TERRAIN_SCALE);
  const ctx = canvas.getContext("2d")!;
  const rng = new RNG(map.seed ^ 0xbeef);
  const t = TILE * TERRAIN_SCALE;
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
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      if (at(cx, cy) !== Terrain.Hill) continue;
      const mx = cx * t + t / 2, my = cy * t + t / 2;
      // A brighter cap where the hill is highest (away from its edge).
      const edge = at(cx - 1, cy) !== Terrain.Hill || at(cx + 1, cy) !== Terrain.Hill
        || at(cx, cy - 1) !== Terrain.Hill || at(cx, cy + 1) !== Terrain.Hill;
      ctx.globalAlpha = edge ? 0.5 : 0.32;
      ctx.fillStyle = edge ? "rgba(20,28,14,0.55)" : "#8fae66";
      ctx.beginPath();
      ctx.arc(mx, my, t * (edge ? 0.62 : 0.5), 0, Math.PI * 2);
      ctx.fill();
      ctx.globalAlpha = 1;
    }
  }
  // Mountains: a shaded mass with a lit north face, drawn per cell so a ridge
  // reads as one range instead of a row of identical lumps.
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      if (at(cx, cy) !== Terrain.Rock) continue;
      const mx = cx * t + t / 2, my = cy * t + t / 2;
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
    }
  }

  // --- 3. Shoreline: warm sand rim on land cells that touch water ---------
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      if (isWater(cx, cy)) continue;
      if (!(isWater(cx - 1, cy) || isWater(cx + 1, cy) || isWater(cx, cy - 1) || isWater(cx, cy + 1))) continue;
      ctx.globalAlpha = 0.75;
      ctx.fillStyle = shade(PAL.sand, rng.range(-0.06, 0.06));
      const mx = cx * t + t / 2;
      const my = cy * t + t / 2;
      ctx.beginPath();
      ctx.arc(mx, my, t * 0.58, 0, Math.PI * 2);
      ctx.arc(mx + rng.range(-t * 0.4, t * 0.4), my + rng.range(-t * 0.4, t * 0.4), t * 0.4, 0, Math.PI * 2);
      ctx.fill();
      ctx.globalAlpha = 1;
    }
  }

  // --- 4. Water with depth bands + cached wave strokes --------------------
  paintBiome(Terrain.Water, () => PAL.water);
  for (let cy = 0; cy < map.rows; cy++) {
    for (let cx = 0; cx < map.cols; cx++) {
      if (!isWater(cx, cy)) continue;
      // Distance-to-shore approximation: ring-1 = shallows, ring-2+ = deep.
      let nearLand = false;
      let nearLand2 = false;
      for (let dy = -1; dy <= 1 && !nearLand; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          if (!isWater(cx + dx, cy + dy)) { nearLand = true; break; }
        }
      }
      if (!nearLand) {
        outer: for (let dy = -2; dy <= 2; dy++) {
          for (let dx = -2; dx <= 2; dx++) {
            if (!isWater(cx + dx, cy + dy)) { nearLand2 = true; break outer; }
          }
        }
      }
      if (nearLand) {
        ctx.globalAlpha = 0.55;
        ctx.fillStyle = PAL.waterEdge;
      } else if (nearLand2) {
        ctx.globalAlpha = 0.3;
        ctx.fillStyle = PAL.waterDeep;
      } else {
        ctx.globalAlpha = 0.65;
        ctx.fillStyle = PAL.waterDeep;
      }
      ctx.fillRect(cx * t, cy * t, t + 1, t + 1);
      ctx.globalAlpha = 1;
      // Occasional static wave stroke (the live glints animate on top).
      if (rng.bool(0.16)) {
        ctx.strokeStyle = withAlpha("#dff2ff", 0.22);
        ctx.lineWidth = 1;
        const wx = cx * t + rng.range(t * 0.2, t * 0.8);
        const wy = cy * t + rng.range(t * 0.2, t * 0.8);
        ctx.beginPath();
        ctx.arc(wx, wy, rng.range(2, 4), Math.PI * 0.15, Math.PI * 0.85);
        ctx.stroke();
      }
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
