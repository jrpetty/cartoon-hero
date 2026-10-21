// Night lighting: darkness with holes in it.
//
// Night used to be one flat navy rectangle over the whole battlefield. Every
// warm thing the building art drew for the dark — lit windows, hearth glow, a
// watchfire's pool of light — was painted *underneath* that rectangle and came
// out as dim as the grass beside it, so the promised "town comes alive at
// night" was a slightly bluer screen. The fix is the standard 2D one: draw the
// darkness into its own layer, cut soft holes where the lights are, tint the
// holes warm, and lay the layer over the scene. Lit ground then shows its true
// colour and everything between the lights stays night.
//
// The layer is a quarter of the screen's resolution. Darkness and light pools
// are all soft falloff with nothing sharp in them, so upscaling costs nothing
// visible and it keeps the pass to one small fill, a handful of sprite blits
// and one full-screen blit — about what the vignette already costs.

import { BuildState, Entity, Kind } from "../sim/types";

/** Buildings that are lived in and light their windows after dark. */
export const LIT_BUILDINGS = new Set([
  "town_center", "house", "mill", "barracks", "archery_range", "stable",
  "blacksmith", "market", "castle", "watch_tower", "siege_workshop",
]);

export interface Light {
  x: number;
  y: number;
  /** World-space radius of the pool of light. */
  r: number;
  /** 0..1: how much of the darkness it clears at its centre. */
  s: number;
}

/**
 * The light an entity gives off at night, or null. Pure, so the choice of what
 * glows is testable without a canvas.
 *
 * `own` is whether the viewer controls it. Your own units carry a faint lantern
 * so an army stays findable in the dark; nobody else's do, because a light that
 * followed enemy units around would make night *easier* to scout, not harder.
 */
export function lightFor(e: Entity, time: number, own: boolean): Light | null {
  if (e.kind === Kind.Building) {
    if (e.buildState !== BuildState.Done) return null;
    const half = e.radius;
    const flick = 0.92 + Math.sin(time * 9 + e.id * 2.3) * 0.05 + Math.sin(time * 13.7 + e.id) * 0.03;
    if (e.type === "watchfire") return { x: e.x, y: e.y - half * 0.3, r: half * 7.5 * flick, s: 0.95 };
    const burning = e.hp < e.maxHp * 0.5;
    if (LIT_BUILDINGS.has(e.type)) {
      const big = e.type === "town_center" || e.type === "castle";
      return {
        x: e.x, y: e.y + half * 0.2,
        r: half * (big ? 3.0 : 2.7) * (burning ? 1.25 : 1),
        s: (burning ? 0.85 : 0.62) * flick,
      };
    }
    // A wall or farm on fire still lights up the night.
    if (burning) return { x: e.x, y: e.y, r: half * 2.4, s: 0.7 * flick };
    return null;
  }
  if (e.kind === Kind.Unit && own) return { x: e.x, y: e.y, r: 34, s: 0.32 };
  return null;
}

export class NightLayer {
  private layer: HTMLCanvasElement | null = null;
  private blob: HTMLCanvasElement | null = null;
  private warm: HTMLCanvasElement | null = null;
  static readonly DOWNSCALE = 4;

  private sprite(rgb: string): HTMLCanvasElement {
    const c = document.createElement("canvas");
    c.width = c.height = 64;
    const g = c.getContext("2d")!;
    const grad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    // A smooth shoulder rather than a linear ramp: a linear falloff has a
    // visible cone tip in the middle of every light.
    grad.addColorStop(0, `rgba(${rgb},1)`);
    grad.addColorStop(0.35, `rgba(${rgb},0.85)`);
    grad.addColorStop(0.7, `rgba(${rgb},0.3)`);
    grad.addColorStop(1, `rgba(${rgb},0)`);
    g.fillStyle = grad;
    g.fillRect(0, 0, 64, 64);
    return c;
  }

  /**
   * Composite the night over the frame. `tint` is the sky colour (rgba, a in
   * 0..1), `night` how far into the night we are (0..1), and each light's
   * position is in world space; `toScreen` maps it with the camera.
   */
  draw(
    ctx: CanvasRenderingContext2D, W: number, H: number,
    tint: [number, number, number, number], night: number,
    lights: readonly Light[], zoom: number,
    toScreen: (x: number, y: number) => [number, number],
  ) {
    const k = NightLayer.DOWNSCALE;
    const lw = Math.max(1, Math.ceil(W / k)), lh = Math.max(1, Math.ceil(H / k));
    if (!this.layer || this.layer.width !== lw || this.layer.height !== lh) {
      this.layer = document.createElement("canvas");
      this.layer.width = lw;
      this.layer.height = lh;
    }
    if (!this.blob) this.blob = this.sprite("255,255,255");
    if (!this.warm) this.warm = this.sprite("255,176,84");
    const l = this.layer.getContext("2d")!;
    l.setTransform(1, 0, 0, 1, 0, 0);
    l.globalAlpha = 1;
    l.globalCompositeOperation = "source-over";
    l.clearRect(0, 0, lw, lh);
    l.fillStyle = `rgba(${tint[0] | 0}, ${tint[1] | 0}, ${tint[2] | 0}, ${tint[3]})`;
    l.fillRect(0, 0, lw, lh);

    if (night > 0.01 && lights.length) {
      const on: number[] = [];
      for (const li of lights) {
        const [sx, sy] = toScreen(li.x, li.y);
        const rad = (li.r * zoom) / k;
        const x = sx / k, y = sy / k;
        if (x + rad < 0 || y + rad < 0 || x - rad > lw || y - rad > lh) continue;
        on.push(x, y, rad, li.s);
      }
      // Cut the holes...
      l.globalCompositeOperation = "destination-out";
      for (let i = 0; i < on.length; i += 4) {
        l.globalAlpha = Math.min(1, on[i + 3] * night);
        l.drawImage(this.blob, on[i] - on[i + 2], on[i + 1] - on[i + 2], on[i + 2] * 2, on[i + 2] * 2);
      }
      // ...and fill them with firelight, a little tighter than the hole so
      // the warm core sits inside the cleared ground.
      l.globalCompositeOperation = "source-over";
      for (let i = 0; i < on.length; i += 4) {
        l.globalAlpha = Math.min(1, on[i + 3] * night * 0.3);
        const r = on[i + 2] * 0.8;
        l.drawImage(this.warm, on[i] - r, on[i + 1] - r, r * 2, r * 2);
      }
      l.globalAlpha = 1;
    }
    const smooth = ctx.imageSmoothingEnabled;
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(this.layer, 0, 0, lw, lh, 0, 0, lw * k, lh * k);
    ctx.imageSmoothingEnabled = smooth;
  }
}
