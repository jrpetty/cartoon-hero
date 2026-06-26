// Client voxel world: stores chunks streamed from the server, builds Three.js
// meshes with per-face culling + vertex-color shading, and provides a voxel DDA
// raycaster for block targeting.

import * as THREE from 'three';

const FACES = [
  // dir,           normal,        corners (CCW),                         shade
  { d: [ 1, 0, 0], n: [ 1, 0, 0], c: [[1,0,0],[1,1,0],[1,1,1],[1,0,1]], s: 0.80 },
  { d: [-1, 0, 0], n: [-1, 0, 0], c: [[0,0,1],[0,1,1],[0,1,0],[0,0,0]], s: 0.80 },
  { d: [ 0, 1, 0], n: [ 0, 1, 0], c: [[0,1,0],[0,1,1],[1,1,1],[1,1,0]], s: 1.00 },
  { d: [ 0,-1, 0], n: [ 0,-1, 0], c: [[0,0,1],[0,0,0],[1,0,0],[1,0,1]], s: 0.55 },
  { d: [ 0, 0, 1], n: [ 0, 0, 1], c: [[1,0,1],[1,1,1],[0,1,1],[0,0,1]], s: 0.68 },
  { d: [ 0, 0,-1], n: [ 0, 0,-1], c: [[0,0,0],[0,1,0],[1,1,0],[1,0,0]], s: 0.68 },
];

export class VoxelWorld {
  constructor(scene, blocks, chunkSize, height) {
    this.scene = scene;
    this.blocks = blocks;
    this.CH = chunkSize;
    this.H = height;
    this.chunks = new Map(); // "cx,cz" -> { data, opaque, trans, dirty }
    this.dirtyQueue = [];
  }

  key(cx, cz) { return `${cx},${cz}`; }

  opaque(id) {
    const b = this.blocks[id];
    return !!(b && b.solid && (b.alpha === undefined || b.alpha >= 1));
  }
  solid(id) { return !!(this.blocks[id] && this.blocks[id].solid); }

  setChunk(cx, cz, data) {
    const k = this.key(cx, cz);
    let c = this.chunks.get(k);
    if (!c) { c = { cx, cz, data, opaque: null, trans: null, dirty: true }; this.chunks.set(k, c); }
    else { c.data = data; c.dirty = true; }
    this.markDirty(cx, cz);
    // neighbors must re-cull against the new chunk's edge blocks
    for (const [dx, dz] of [[1,0],[-1,0],[0,1],[0,-1]]) this.markDirty(cx + dx, cz + dz);
  }

  markDirty(cx, cz) {
    const c = this.chunks.get(this.key(cx, cz));
    if (c && !c._queued) { c.dirty = true; c._queued = true; this.dirtyQueue.push(c); }
  }

  // world block lookup across chunk boundaries; unloaded => treated as air(0)
  getBlock(x, y, z) {
    if (y < 0 || y >= this.H) return 0;
    const cx = Math.floor(x / this.CH), cz = Math.floor(z / this.CH);
    const c = this.chunks.get(this.key(cx, cz));
    if (!c) return 0;
    const lx = x - cx * this.CH, lz = z - cz * this.CH;
    return c.data[(lx * this.CH + lz) * this.H + y] | 0;
  }

  setBlock(x, y, z, b) {
    const cx = Math.floor(x / this.CH), cz = Math.floor(z / this.CH);
    const c = this.chunks.get(this.key(cx, cz));
    if (!c) return;
    const lx = x - cx * this.CH, lz = z - cz * this.CH;
    c.data[(lx * this.CH + lz) * this.H + y] = b;
    this.markDirty(cx, cz);
    if (lx === 0) this.markDirty(cx - 1, cz);
    if (lx === this.CH - 1) this.markDirty(cx + 1, cz);
    if (lz === 0) this.markDirty(cx, cz - 1);
    if (lz === this.CH - 1) this.markDirty(cx, cz + 1);
  }

  faceColor(id, face) {
    const def = this.blocks[id] || {};
    let hex = def.color ?? 0xffffff;
    if (face.n[1] === 1 && def.top !== undefined) hex = def.top;
    else if (face.n[1] === 0 && def.side !== undefined) hex = def.side;
    const shade = def.light ? 1.0 : face.s;       // glowstone ignores shading
    const r = ((hex >> 16) & 255) / 255 * shade;
    const g = ((hex >> 8) & 255) / 255 * shade;
    const b = (hex & 255) / 255 * shade;
    return [r, g, b];
  }

  buildMesh(c) {
    const { cx, cz, data } = c;
    const baseX = cx * this.CH, baseZ = cz * this.CH;
    const op = { pos: [], norm: [], col: [], idx: [] };
    const tr = { pos: [], norm: [], col: [], idx: [] };

    for (let lx = 0; lx < this.CH; lx++) {
      for (let lz = 0; lz < this.CH; lz++) {
        for (let y = 0; y < this.H; y++) {
          const id = data[(lx * this.CH + lz) * this.H + y] | 0;
          if (id === 0) continue;
          const wx = baseX + lx, wz = baseZ + lz;
          const isOpaque = this.opaque(id);
          const target = isOpaque ? op : tr;
          for (const f of FACES) {
            const nb = this.getBlock(wx + f.d[0], y + f.d[1], wz + f.d[2]);
            // cull when neighbor hides this face
            if (isOpaque) { if (this.opaque(nb)) continue; }
            else { if (nb === id || this.opaque(nb)) continue; }
            const col = this.faceColor(id, f);
            const start = target.pos.length / 3;
            for (const corner of f.c) {
              target.pos.push(wx + corner[0], y + corner[1], wz + corner[2]);
              target.norm.push(f.n[0], f.n[1], f.n[2]);
              target.col.push(col[0], col[1], col[2]);
            }
            target.idx.push(start, start + 1, start + 2, start, start + 2, start + 3);
          }
        }
      }
    }

    this.replaceMesh(c, 'opaque', op, false);
    this.replaceMesh(c, 'trans', tr, true);
    c.dirty = false; c._queued = false;
  }

  replaceMesh(c, slot, geo, transparent) {
    if (c[slot]) { this.scene.remove(c[slot]); c[slot].geometry.dispose(); c[slot] = null; }
    if (geo.idx.length === 0) return;
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(geo.pos, 3));
    g.setAttribute('normal', new THREE.Float32BufferAttribute(geo.norm, 3));
    g.setAttribute('color', new THREE.Float32BufferAttribute(geo.col, 3));
    g.setIndex(geo.idx);
    const mat = new THREE.MeshLambertMaterial({
      vertexColors: true,
      transparent,
      opacity: transparent ? 0.85 : 1,
      side: transparent ? THREE.DoubleSide : THREE.FrontSide,
    });
    const mesh = new THREE.Mesh(g, mat);
    mesh.renderOrder = transparent ? 1 : 0;
    c[slot] = mesh;
    this.scene.add(mesh);
  }

  // rebuild a few dirty chunks per frame to avoid stutter
  update(budget = 2) {
    let built = 0;
    while (this.dirtyQueue.length && built < budget) {
      const c = this.dirtyQueue.shift();
      if (!this.chunks.has(this.key(c.cx, c.cz))) { c._queued = false; continue; }
      this.buildMesh(c);
      built++;
    }
  }

  // Amanatides–Woo voxel traversal. Returns {x,y,z, nx,ny,nz} of the first
  // solid block hit, or null. `nx,ny,nz` is the face normal (for placement).
  raycast(origin, dir, maxDist = 6) {
    let x = Math.floor(origin.x), y = Math.floor(origin.y), z = Math.floor(origin.z);
    const stepX = Math.sign(dir.x), stepY = Math.sign(dir.y), stepZ = Math.sign(dir.z);
    const tDeltaX = stepX !== 0 ? Math.abs(1 / dir.x) : Infinity;
    const tDeltaY = stepY !== 0 ? Math.abs(1 / dir.y) : Infinity;
    const tDeltaZ = stepZ !== 0 ? Math.abs(1 / dir.z) : Infinity;
    const distTo = (i, o, s) => s > 0 ? (i + 1 - o) : (o - i);
    let tMaxX = stepX !== 0 ? distTo(x, origin.x, stepX) * tDeltaX : Infinity;
    let tMaxY = stepY !== 0 ? distTo(y, origin.y, stepY) * tDeltaY : Infinity;
    let tMaxZ = stepZ !== 0 ? distTo(z, origin.z, stepZ) * tDeltaZ : Infinity;
    let nx = 0, ny = 0, nz = 0;
    let t = 0;
    while (t <= maxDist) {
      if (this.solid(this.getBlock(x, y, z))) return { x, y, z, nx, ny, nz };
      if (tMaxX < tMaxY && tMaxX < tMaxZ) { x += stepX; t = tMaxX; tMaxX += tDeltaX; nx = -stepX; ny = 0; nz = 0; }
      else if (tMaxY < tMaxZ) { y += stepY; t = tMaxY; tMaxY += tDeltaY; nx = 0; ny = -stepY; nz = 0; }
      else { z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; nx = 0; ny = 0; nz = -stepZ; }
    }
    return null;
  }
}
