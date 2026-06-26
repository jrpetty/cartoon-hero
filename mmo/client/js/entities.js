// Renders other players and mobs from server STATE snapshots, smoothly
// interpolating toward the latest authoritative positions.

import * as THREE from 'three';

function nameSprite(text) {
  const cv = document.createElement('canvas');
  cv.width = 256; cv.height = 64;
  const ctx = cv.getContext('2d');
  ctx.font = 'bold 30px Segoe UI, sans-serif';
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.fillStyle = 'rgba(0,0,0,0.55)';
  const w = ctx.measureText(text).width + 24;
  ctx.fillRect(128 - w / 2, 14, w, 36);
  ctx.fillStyle = '#fff';
  ctx.fillText(text, 128, 33);
  const tex = new THREE.CanvasTexture(cv);
  const spr = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, depthTest: false }));
  spr.scale.set(2.4, 0.6, 1);
  spr.position.y = 2.3;
  return spr;
}

export class Entities {
  constructor(scene) {
    this.scene = scene;
    this.players = new Map(); // id -> { group, target }
    this.mobs = new Map();    // id -> { mesh, target, hpbar }
    this.selfId = null;
  }

  makePlayer(p) {
    const group = new THREE.Group();
    const body = new THREE.Mesh(
      new THREE.BoxGeometry(0.6, 1.7, 0.6),
      new THREE.MeshLambertMaterial({ color: 0x4f86c6 }),
    );
    body.position.y = 0.85;
    const head = new THREE.Mesh(
      new THREE.BoxGeometry(0.5, 0.5, 0.5),
      new THREE.MeshLambertMaterial({ color: 0xe0ac69 }),
    );
    head.position.y = 1.95;
    group.add(body, head, nameSprite(p.name));
    this.scene.add(group);
    return { group, target: new THREE.Vector3(p.x, p.y, p.z), yaw: p.yaw || 0 };
  }

  makeMob(m) {
    const mesh = new THREE.Mesh(
      new THREE.BoxGeometry(0.9, 0.9, 0.9),
      new THREE.MeshLambertMaterial({ color: 0x66bb6a, transparent: true, opacity: 0.85 }),
    );
    const eyes = new THREE.Mesh(
      new THREE.BoxGeometry(0.92, 0.18, 0.92),
      new THREE.MeshBasicMaterial({ color: 0x113311 }),
    );
    eyes.position.y = 0.12;
    mesh.add(eyes);
    this.scene.add(mesh);
    return { mesh, target: new THREE.Vector3(m.x, m.y, m.z), id: m.id };
  }

  syncPlayers(list) {
    const seen = new Set();
    for (const p of list) {
      if (p.id === this.selfId) continue;
      seen.add(p.id);
      let e = this.players.get(p.id);
      if (!e) { e = this.makePlayer(p); this.players.set(p.id, e); }
      e.target.set(p.x, p.y, p.z);
      e.yaw = p.yaw || 0;
    }
    for (const [id, e] of this.players) {
      if (!seen.has(id)) { this.scene.remove(e.group); this.players.delete(id); }
    }
  }

  syncMobs(list) {
    const seen = new Set();
    for (const m of list) {
      seen.add(m.id);
      let e = this.mobs.get(m.id);
      if (!e) { e = this.makeMob(m); this.mobs.set(m.id, e); }
      e.target.set(m.x, m.y + 0.45, m.z);
    }
    for (const [id, e] of this.mobs) {
      if (!seen.has(id)) { this.scene.remove(e.mesh); this.mobs.delete(id); }
    }
  }

  // smooth toward targets; called every frame
  update(dt, time) {
    const k = Math.min(1, dt * 10);
    for (const e of this.players.values()) {
      e.group.position.lerp(e.target, k);
      e.group.rotation.y = e.yaw;
    }
    for (const e of this.mobs.values()) {
      e.mesh.position.lerp(e.target, k);
      e.mesh.position.y = e.target.y + Math.sin(time * 4 + e.id) * 0.08; // bob
    }
  }

  // mob meshes for raycast targeting
  mobMeshes() { return [...this.mobs.values()].map(e => e.mesh); }
  mobIdForMesh(mesh) {
    for (const e of this.mobs.values()) if (e.mesh === mesh) return e.id;
    return null;
  }
}
