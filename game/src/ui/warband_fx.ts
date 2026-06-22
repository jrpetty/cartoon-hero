// Warband Tactics combat FX: hit sparks, floating damage numbers, death bursts
// and screen-shake — driven by the live battle's world events, plus throttled
// synth SFX. All effects live in arena world-space and are mapped to the board
// when drawn, so they track the units exactly.

import { WorldEvent } from "../sim/world";
import { Team } from "../sim/types";
import { audio } from "../engine/audio";
import { ABILITIES } from "../content/abilities";

interface Spark { x: number; y: number; vx: number; vy: number; life: number; max: number; size: number; color: string; grav: number; glow: boolean; }
interface DmgText { x: number; y: number; vy: number; life: number; max: number; text: string; color: string; size: number; }

export class WarbandFx {
  private sparks: Spark[] = [];
  private dmg: DmgText[] = [];
  shake = 0;
  private lastSound: Record<string, number> = {};
  private clock = 0;

  private sound(name: Parameters<typeof audio.play>[0], gap = 0.06) {
    const last = this.lastSound[name] ?? -1;
    if (this.clock - last < gap) return;
    this.lastSound[name] = this.clock;
    audio.play(name);
  }

  /** Spawn FX + play SFX for a batch of combat events. */
  ingest(events: WorldEvent[]) {
    for (const ev of events) {
      switch (ev.kind) {
        case "hit": {
          const n = Number(ev.data);
          if (n > 0) this.dmg.push({ x: ev.x + (Math.random() * 8 - 4), y: ev.y - 6, vy: -34, life: 0.85, max: 0.85, text: String(n), color: ev.team === Team.Player ? "#ffe9b0" : "#ffb4a8", size: 13 + Math.min(10, n / 6) });
          this.burst(ev.x, ev.y, 5, "#ffe6a0", 60, 0.5, 2);
          this.sound("sword", 0.05);
          break;
        }
        case "sword": this.burst(ev.x, ev.y, 4, "#fff0c0", 55, 0.45, 2); this.sound("sword", 0.05); break;
        case "bow": this.sound("bow", 0.06); break;
        case "arrowHit": this.burst(ev.x, ev.y, 4, "#d8e4f0", 70, 0.4, 1.6); this.sound("arrowHit", 0.05); break;
        case "siege": this.burst(ev.x, ev.y, 14, "#ffb060", 130, 0.7, 3, 60); this.shake = Math.min(10, this.shake + 6); this.sound("siege", 0.12); break;
        case "death": {
          const col = ev.team === Team.Player ? "#7fb0e8" : "#e88a7f";
          this.burst(ev.x, ev.y - 6, 16, col, 95, 0.7, 2.6, 40);
          this.burst(ev.x, ev.y - 6, 8, "#3a2f22", 60, 0.8, 3, 80);
          this.shake = Math.min(9, this.shake + 2.4);
          this.sound("death", 0.08);
          break;
        }
        case "ability": {
          // A bright signature-ability cast: a ring of sparks in the ability colour.
          const col = Object.values(ABILITIES).find((a) => a.id === ev.data)?.color ?? "#ffe9a8";
          this.burst(ev.x, ev.y - 8, 18, col, 120, 0.8, 3, 30);
          this.shake = Math.min(7, this.shake + 1.5);
          this.sound("complete", 0.12);
          break;
        }
      }
    }
  }

  private burst(x: number, y: number, count: number, color: string, speed: number, life: number, size: number, grav = 0) {
    for (let i = 0; i < count; i++) {
      const a = Math.random() * Math.PI * 2;
      const s = speed * (0.35 + Math.random() * 0.65);
      this.sparks.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s - 10, life: life * (0.6 + Math.random() * 0.5), max: life, size: size * (0.6 + Math.random() * 0.7), color, grav, glow: true });
    }
  }

  update(dt: number) {
    this.clock += dt;
    for (const p of this.sparks) { p.x += p.vx * dt; p.y += p.vy * dt; p.vy += p.grav * dt; p.vx *= 0.9; p.vy *= 0.9; p.life -= dt; }
    this.sparks = this.sparks.filter((p) => p.life > 0);
    for (const d of this.dmg) { d.y += d.vy * dt; d.vy += 26 * dt; d.life -= dt; }
    this.dmg = this.dmg.filter((d) => d.life > 0);
    this.shake *= Math.max(0, 1 - dt * 7);
    if (this.shake < 0.15) this.shake = 0;
  }

  clear() { this.sparks.length = 0; this.dmg.length = 0; this.shake = 0; }

  /** Draw sparks + damage numbers, mapping arena world-space to screen. */
  draw(ctx: CanvasRenderingContext2D, mapX: (x: number) => number, mapY: (y: number) => number, scale: number) {
    ctx.save();
    for (const p of this.sparks) {
      const a = Math.max(0, Math.min(1, p.life / p.max));
      const sx = mapX(p.x), sy = mapY(p.y), r = Math.max(0.6, p.size * scale * (0.4 + a * 0.6));
      ctx.globalAlpha = a;
      if (p.glow) { ctx.shadowColor = p.color; ctx.shadowBlur = 8; }
      ctx.fillStyle = p.color;
      ctx.beginPath(); ctx.arc(sx, sy, r, 0, Math.PI * 2); ctx.fill();
    }
    ctx.shadowBlur = 0;
    ctx.textAlign = "center";
    for (const d of this.dmg) {
      const a = Math.max(0, Math.min(1, d.life / d.max));
      ctx.globalAlpha = a;
      ctx.font = `bold ${Math.round(d.size)}px Georgia, serif`;
      ctx.fillStyle = "rgba(0,0,0,0.55)";
      ctx.fillText(d.text, mapX(d.x) + 1, mapY(d.y) + 1);
      ctx.fillStyle = d.color;
      ctx.fillText(d.text, mapX(d.x), mapY(d.y));
    }
    ctx.restore();
  }
}
