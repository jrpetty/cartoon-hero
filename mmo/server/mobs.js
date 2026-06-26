// Server-authoritative mobs. Simple slimes that wander and drift toward nearby
// players. The server owns HP, movement, death, loot and respawn so combat is
// cheat-resistant.

let nextMobId = 1;

export class MobManager {
  constructor(world, cap = 24) {
    this.world = world;
    this.mobs = new Map();   // id -> mob
    this.cap = cap;
  }

  spawnNear(x, z) {
    if (this.mobs.size >= this.cap) return null;
    // pick a spot a few blocks from (x, z)
    const ang = (nextMobId * 2.399963); // golden-angle scatter, deterministic
    const dist = 6 + (nextMobId % 6);
    const mx = Math.round(x + Math.cos(ang) * dist);
    const mz = Math.round(z + Math.sin(ang) * dist);
    const my = this.world.surfaceY(mx, mz);
    if (my > 62) return null;
    const mob = {
      id: nextMobId++, type: 'slime',
      x: mx + 0.5, y: my, z: mz + 0.5,
      hp: 14, maxHp: 14, speed: 0.045 + Math.random() * 0.02,
      wanderX: 0, wanderZ: 0, retarget: 0,
    };
    this.mobs.set(mob.id, mob);
    return mob;
  }

  // One simulation step. `players` is an array of {x,y,z}. Returns nothing;
  // mutates mob positions.
  tick(players) {
    for (const mob of this.mobs.values()) {
      // find nearest player within aggro range
      let nearest = null, best = 12 * 12;
      for (const p of players) {
        const dx = p.x - mob.x, dz = p.z - mob.z;
        const d2 = dx * dx + dz * dz;
        if (d2 < best) { best = d2; nearest = p; }
      }
      let tx, tz;
      if (nearest) {
        tx = nearest.x - mob.x; tz = nearest.z - mob.z;
      } else {
        if (mob.retarget <= 0) {
          mob.wanderX = Math.cos(mob.id + this.now()) ;
          mob.wanderZ = Math.sin(mob.id * 1.7 + this.now());
          mob.retarget = 40;
        }
        mob.retarget--;
        tx = mob.wanderX; tz = mob.wanderZ;
      }
      const len = Math.hypot(tx, tz) || 1;
      const nx = mob.x + (tx / len) * mob.speed;
      const nz = mob.z + (tz / len) * mob.speed;
      // keep mobs on the surface (no pathfinding through terrain)
      const sy = this.world.surfaceY(Math.floor(nx), Math.floor(nz));
      if (Math.abs(sy - mob.y) <= 1.5) {
        mob.x = nx; mob.z = nz; mob.y = sy;
      } else {
        mob.retarget = 0; // blocked: pick a new wander next tick
      }
    }
  }

  now() {
    // coarse time for wander variation without Math.random in the hot path
    return Math.floor(Date.now() / 1000) % 1000;
  }

  damage(id, amount) {
    const mob = this.mobs.get(id);
    if (!mob) return null;
    mob.hp -= amount;
    if (mob.hp <= 0) {
      this.mobs.delete(id);
      return { dead: true, mob };
    }
    return { dead: false, mob };
  }

  publicView() {
    return [...this.mobs.values()].map(m => ({
      id: m.id, type: m.type, x: m.x, y: m.y, z: m.z, hp: m.hp, maxHp: m.maxHp,
    }));
  }
}
