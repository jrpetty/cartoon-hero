// Authoritative voxel world: deterministic procedural generation + a sparse
// overlay of player edits that is persisted to disk. The base terrain is never
// stored — it is regenerated from the seed on demand — so only edits cost space.

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { fbm } from './noise.js';
import { AIR, isSolid } from '../shared/blocks.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export const CHUNK = 16;     // blocks per chunk side
export const HEIGHT = 64;    // world height
export const SEA = 24;       // sea level

const B = { GRASS:1, DIRT:2, STONE:3, SAND:4, WATER:5, LOG:6, LEAVES:7, COAL:11, IRON:12 };

export class World {
  constructor(seed = 1337, saveFile = path.join(__dirname, 'world-save.json')) {
    this.seed = seed;
    this.saveFile = saveFile;
    this.edits = new Map();        // "x,y,z" -> blockId  (player modifications)
    this.chunkCache = new Map();   // "cx,cz" -> Int8Array (generated base, no edits)
    this.load();
  }

  // ---- persistence ----
  load() {
    try {
      const raw = JSON.parse(fs.readFileSync(this.saveFile, 'utf8'));
      this.seed = raw.seed ?? this.seed;
      for (const [k, v] of Object.entries(raw.edits || {})) this.edits.set(k, v);
      console.log(`[world] loaded ${this.edits.size} edits (seed ${this.seed})`);
    } catch {
      console.log(`[world] new world, seed ${this.seed}`);
    }
  }

  save() {
    const obj = { seed: this.seed, edits: Object.fromEntries(this.edits) };
    fs.writeFileSync(this.saveFile, JSON.stringify(obj));
  }

  // ---- generation ----
  // Surface height at world (x, z). Blends a base plain with mountains; biomes
  // are chosen by a slow noise field.
  heightAt(x, z) {
    const cont = fbm(x, z, this.seed, 4, 0.010, 0.5);          // continents
    const mtn = Math.pow(fbm(x, z, this.seed + 99, 4, 0.020, 0.5), 2);
    let h = SEA - 4 + cont * 18 + mtn * 22;
    return Math.max(2, Math.min(HEIGHT - 8, Math.floor(h)));
  }

  biomeAt(x, z) {
    const t = fbm(x, z, this.seed + 555, 2, 0.006, 0.5);
    if (t < 0.42) return 'desert';
    if (t > 0.62) return 'mountains';
    return 'plains';
  }

  // Base block (no edits) at world coords.
  generated(x, y, z) {
    if (y < 0 || y >= HEIGHT) return AIR;
    const h = this.heightAt(x, z);
    const biome = this.biomeAt(x, z);
    if (y > h) {
      return y <= SEA ? B.WATER : AIR;
    }
    if (y === h) {
      if (biome === 'desert') return B.SAND;
      if (h <= SEA) return B.SAND;            // beach
      if (biome === 'mountains' && h > SEA + 14) return B.STONE;
      return B.GRASS;
    }
    if (y > h - 4) return biome === 'desert' ? B.SAND : B.DIRT;
    // deep stone with sparse ores (deterministic from coords)
    const ore = fbm(x * 3.1, z * 3.1 + y * 7, this.seed + 7, 2, 0.07, 0.5);
    if (y < SEA - 6 && ore > 0.80) return B.IRON;
    if (ore > 0.78) return B.COAL;
    return B.STONE;
  }

  // Trees are generated per-chunk so trunks/leaves are stable and not split.
  // Returns a Map of "x,y,z"->block for decorations in this chunk.
  decorations(cx, cz) {
    const deco = new Map();
    const baseX = cx * CHUNK, baseZ = cz * CHUNK;
    for (let lx = 0; lx < CHUNK; lx++) {
      for (let lz = 0; lz < CHUNK; lz++) {
        const x = baseX + lx, z = baseZ + lz;
        const biome = this.biomeAt(x, z);
        if (biome === 'desert') continue;
        const h = this.heightAt(x, z);
        if (h <= SEA) continue;
        // deterministic tree placement, ~3% of land columns, spaced out
        const r = fbm(x * 12.9, z * 78.2, this.seed + 4242, 1, 1.0, 0.5);
        const density = biome === 'mountains' ? 0.985 : 0.965;
        if (r < density) continue;
        const trunk = 4 + Math.floor(fbm(x, z, this.seed + 1, 1, 1.0, 0.5) * 2);
        for (let i = 1; i <= trunk; i++) deco.set(`${x},${h + i},${z}`, B.LOG);
        const top = h + trunk;
        for (let dx = -2; dx <= 2; dx++)
          for (let dz = -2; dz <= 2; dz++)
            for (let dy = 0; dy <= 2; dy++) {
              if (Math.abs(dx) === 2 && Math.abs(dz) === 2 && dy !== 1) continue;
              const key = `${x + dx},${top - 1 + dy},${z + dz}`;
              if (!deco.has(key)) deco.set(key, B.LEAVES);
            }
        deco.set(`${x},${top + 1},${z}`, B.LEAVES);
      }
    }
    return deco;
  }

  // ---- block access ----
  get(x, y, z) {
    const key = `${x},${y},${z}`;
    if (this.edits.has(key)) return this.edits.get(key);
    const base = this.baseAt(x, y, z);
    return base;
  }

  // base = generated terrain + deterministic decorations (cached per chunk)
  baseAt(x, y, z) {
    const cx = Math.floor(x / CHUNK), cz = Math.floor(z / CHUNK);
    const chunk = this.getBaseChunk(cx, cz);
    const lx = ((x % CHUNK) + CHUNK) % CHUNK;
    const lz = ((z % CHUNK) + CHUNK) % CHUNK;
    if (y < 0 || y >= HEIGHT) return AIR;
    return chunk[(lx * CHUNK + lz) * HEIGHT + y];
  }

  getBaseChunk(cx, cz) {
    const key = `${cx},${cz}`;
    let chunk = this.chunkCache.get(key);
    if (chunk) return chunk;
    chunk = new Int8Array(CHUNK * CHUNK * HEIGHT);
    const baseX = cx * CHUNK, baseZ = cz * CHUNK;
    for (let lx = 0; lx < CHUNK; lx++)
      for (let lz = 0; lz < CHUNK; lz++)
        for (let y = 0; y < HEIGHT; y++)
          chunk[(lx * CHUNK + lz) * HEIGHT + y] = this.generated(baseX + lx, y, baseZ + lz);
    // bake decorations (trees) into the base chunk
    const deco = this.decorations(cx, cz);
    for (const [k, b] of deco) {
      const [x, y, z] = k.split(',').map(Number);
      if (y < 0 || y >= HEIGHT) continue;
      const lx = ((x % CHUNK) + CHUNK) % CHUNK;
      const lz = ((z % CHUNK) + CHUNK) % CHUNK;
      // only place into this chunk's columns (deco can spill into neighbors;
      // those are handled when the neighbor chunk is generated)
      if (x >= baseX && x < baseX + CHUNK && z >= baseZ && z < baseZ + CHUNK)
        chunk[(lx * CHUNK + lz) * HEIGHT + y] = b;
    }
    // neighbor tree spill-in: leaves/trunks from adjacent chunks
    for (const [dcx, dcz] of [[-1,0],[1,0],[0,-1],[0,1],[-1,-1],[1,1],[-1,1],[1,-1]]) {
      const nd = this.decorations(cx + dcx, cz + dcz);
      for (const [k, b] of nd) {
        const [x, y, z] = k.split(',').map(Number);
        if (y < 0 || y >= HEIGHT) continue;
        if (x >= baseX && x < baseX + CHUNK && z >= baseZ && z < baseZ + CHUNK) {
          const lx = ((x % CHUNK) + CHUNK) % CHUNK;
          const lz = ((z % CHUNK) + CHUNK) % CHUNK;
          const idx = (lx * CHUNK + lz) * HEIGHT + y;
          if (chunk[idx] === AIR) chunk[idx] = b;
        }
      }
    }
    this.chunkCache.set(key, chunk);
    if (this.chunkCache.size > 512) this.chunkCache.delete(this.chunkCache.keys().next().value);
    return chunk;
  }

  // Full block column-major array for a chunk WITH edits applied — what we ship
  // to the client.
  chunkData(cx, cz) {
    const base = this.getBaseChunk(cx, cz);
    const out = new Array(base.length);
    const baseX = cx * CHUNK, baseZ = cz * CHUNK;
    for (let i = 0; i < base.length; i++) out[i] = base[i];
    // apply edits that fall in this chunk
    for (const [k, b] of this.edits) {
      const [x, y, z] = k.split(',').map(Number);
      if (x >= baseX && x < baseX + CHUNK && z >= baseZ && z < baseZ + CHUNK && y >= 0 && y < HEIGHT) {
        const lx = x - baseX, lz = z - baseZ;
        out[(lx * CHUNK + lz) * HEIGHT + y] = b;
      }
    }
    return out;
  }

  setBlock(x, y, z, b) {
    if (y < 0 || y >= HEIGHT) return false;
    const key = `${x},${y},${z}`;
    // if setting back to the generated base, drop the edit to keep the map small
    if (this.baseAt(x, y, z) === b) this.edits.delete(key);
    else this.edits.set(key, b);
    return true;
  }

  // Find a safe spawn Y (top solid block + 1) at a column.
  surfaceY(x, z) {
    for (let y = HEIGHT - 1; y >= 0; y--) {
      if (isSolid(this.get(x, y, z))) return y + 1;
    }
    return SEA + 1;
  }
}
