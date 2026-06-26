// First-person controller: mouse-look, WASD movement, gravity, jumping, and
// axis-separated AABB collision against the voxel world.

import * as THREE from 'three';

const EYE = 1.62;       // camera height above feet
const HALF = 0.3;       // player half-width
const BODY = 1.8;       // player height
const GRAVITY = 26;
const JUMP = 8.4;
const SPEED = 5.2;

export class Player {
  constructor(camera, world) {
    this.cam = camera;
    this.world = world;
    this.pos = new THREE.Vector3(0.5, 50, 0.5); // feet
    this.vel = new THREE.Vector3();
    this.yaw = 0; this.pitch = 0;
    this.onGround = false;
    this.keys = {};
  }

  setSpawn(x, y, z) { this.pos.set(x, y, z); this.vel.set(0, 0, 0); }

  look(dx, dy) {
    this.yaw -= dx * 0.0024;
    this.pitch -= dy * 0.0024;
    const lim = Math.PI / 2 - 0.01;
    this.pitch = Math.max(-lim, Math.min(lim, this.pitch));
  }

  // is any solid voxel overlapping the player AABB at position p?
  collides(p) {
    const minX = Math.floor(p.x - HALF), maxX = Math.floor(p.x + HALF);
    const minY = Math.floor(p.y), maxY = Math.floor(p.y + BODY);
    const minZ = Math.floor(p.z - HALF), maxZ = Math.floor(p.z + HALF);
    for (let x = minX; x <= maxX; x++)
      for (let y = minY; y <= maxY; y++)
        for (let z = minZ; z <= maxZ; z++)
          if (this.world.solid(this.world.getBlock(x, y, z))) return true;
    return false;
  }

  update(dt) {
    // clamp dt so a tab-switch hitch can't fling the player through terrain
    dt = Math.min(dt, 0.05);

    // don't apply physics until the ground beneath us has streamed in, else we
    // fall through not-yet-loaded chunks
    if (!this.world.hasChunkAt(this.pos.x, this.pos.z)) {
      this.syncCamera();
      return;
    }

    // desired horizontal movement in yaw space
    const fwd = (this.keys['KeyW'] ? 1 : 0) - (this.keys['KeyS'] ? 1 : 0);
    const str = (this.keys['KeyD'] ? 1 : 0) - (this.keys['KeyA'] ? 1 : 0);
    const sin = Math.sin(this.yaw), cos = Math.cos(this.yaw);
    let mx = (-sin * fwd + cos * str);
    let mz = (-cos * fwd - sin * str);
    const len = Math.hypot(mx, mz);
    if (len > 0) { mx /= len; mz /= len; }
    this.vel.x = mx * SPEED;
    this.vel.z = mz * SPEED;

    // gravity + jump
    this.vel.y -= GRAVITY * dt;
    if (this.keys['Space'] && this.onGround) { this.vel.y = JUMP; this.onGround = false; }

    // integrate with per-axis collision
    const p = this.pos.clone();

    p.x += this.vel.x * dt;
    if (this.collides(p)) { p.x = this.pos.x; this.vel.x = 0; }

    p.z += this.vel.z * dt;
    if (this.collides(p)) { p.z = this.pos.z; this.vel.z = 0; }

    p.y += this.vel.y * dt;
    if (this.collides(p)) {
      if (this.vel.y < 0) this.onGround = true;
      p.y = this.pos.y;
      this.vel.y = 0;
    } else {
      this.onGround = false;
    }

    this.pos.copy(p);

    // void fallback
    if (this.pos.y < -20) { this.pos.set(0.5, 60, 0.5); this.vel.set(0, 0, 0); }

    this.syncCamera();
  }

  syncCamera() {
    this.cam.position.set(this.pos.x, this.pos.y + EYE, this.pos.z);
    this.cam.rotation.set(0, 0, 0, 'YXZ');
    this.cam.rotateY(this.yaw);
    this.cam.rotateX(this.pitch);
  }

  forward() {
    return new THREE.Vector3(
      -Math.sin(this.yaw) * Math.cos(this.pitch),
      Math.sin(this.pitch),
      -Math.cos(this.yaw) * Math.cos(this.pitch),
    ).normalize();
  }

  eye() { return new THREE.Vector3(this.pos.x, this.pos.y + EYE, this.pos.z); }
}
