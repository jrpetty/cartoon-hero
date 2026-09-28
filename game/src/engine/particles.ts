// Pooled particle system for combat FX, dust, smoke and the meta case-opening
// flourish. Particles live in world space and are drawn by the renderer.

export interface Particle {
  active: boolean;
  x: number;
  y: number;
  vx: number;
  vy: number;
  life: number;
  maxLife: number;
  size: number;
  color: string;
  drag: number;
  gravity: number;
  fade: boolean;
  shrink: boolean;
  glow: boolean;
}

export class Particles {
  pool: Particle[] = [];
  private cursor = 0;
  /** 0..1 spawn fraction — lowered by the "reduce effects" setting. */
  density = 1;

  constructor(max = 2000) {
    for (let i = 0; i < max; i++) {
      this.pool.push({
        active: false,
        x: 0,
        y: 0,
        vx: 0,
        vy: 0,
        life: 0,
        maxLife: 1,
        size: 2,
        color: "#fff",
        drag: 0.92,
        gravity: 0,
        fade: true,
        shrink: false,
        glow: false,
      });
    }
  }

  private acquire(): Particle {
    // Round-robin over the pool; oldest gets reused if exhausted.
    for (let i = 0; i < this.pool.length; i++) {
      const idx = (this.cursor + i) % this.pool.length;
      if (!this.pool[idx].active) {
        this.cursor = (idx + 1) % this.pool.length;
        return this.pool[idx];
      }
    }
    const p = this.pool[this.cursor];
    this.cursor = (this.cursor + 1) % this.pool.length;
    return p;
  }

  spawn(opts: Partial<Particle> & { x: number; y: number }) {
    // Cosmetic only (never sim), so a probabilistic skip is safe and keeps the
    // thinning uniform across every effect.
    if (this.density < 1 && Math.random() > this.density) return;
    const p = this.acquire();
    p.active = true;
    p.x = opts.x;
    p.y = opts.y;
    p.vx = opts.vx ?? 0;
    p.vy = opts.vy ?? 0;
    p.maxLife = opts.maxLife ?? 0.6;
    p.life = p.maxLife;
    p.size = opts.size ?? 3;
    p.color = opts.color ?? "#ffffff";
    p.drag = opts.drag ?? 0.9;
    p.gravity = opts.gravity ?? 0;
    p.fade = opts.fade ?? true;
    p.shrink = opts.shrink ?? false;
    p.glow = opts.glow ?? false;
  }

  burst(x: number, y: number, count: number, color: string, speed: number, opts: Partial<Particle> = {}) {
    for (let i = 0; i < count; i++) {
      const a = Math.random() * Math.PI * 2;
      const s = speed * (0.4 + Math.random() * 0.6);
      this.spawn({
        x,
        y,
        vx: Math.cos(a) * s,
        vy: Math.sin(a) * s,
        color,
        ...opts,
      });
    }
  }

  update(dt: number) {
    for (const p of this.pool) {
      if (!p.active) continue;
      p.life -= dt;
      if (p.life <= 0) {
        p.active = false;
        continue;
      }
      p.vx *= Math.pow(p.drag, dt * 60);
      p.vy *= Math.pow(p.drag, dt * 60);
      p.vy += p.gravity * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
    }
  }

  clear() {
    for (const p of this.pool) p.active = false;
  }
}
