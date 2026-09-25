// Screen-space weather & atmosphere overlay (rain / snow / overcast). Purely
// cosmetic — never touches the sim — so it's safe to drive with Math.random and
// to skip entirely when the setting is off. The *type* is chosen from the map
// seed so it's stable for a match (and identical on both clients in a net game).

export type WeatherType = "clear" | "rain" | "snow" | "overcast";

interface Drop { x: number; y: number; v: number; len: number; phase: number; }

function pickWeather(seed: number, mapName: string): WeatherType {
  const r = ((seed % 1000) / 1000 + 1) % 1; // 0..1, stable
  const n = (mapName || "").toLowerCase();
  if (n.includes("highland")) return r < 0.6 ? "snow" : "overcast";
  if (n.includes("river") || n.includes("island")) return r < 0.55 ? "rain" : "clear";
  if (n.includes("forest")) return r < 0.5 ? "overcast" : "rain";
  // General maps: mostly clear, with the occasional weather day.
  if (r < 0.55) return "clear";
  if (r < 0.74) return "rain";
  if (r < 0.88) return "overcast";
  return "snow";
}

const TINT: Record<WeatherType, string | null> = {
  clear: null,
  rain: "rgba(40, 55, 80, 0.16)",
  snow: "rgba(200, 215, 235, 0.12)",
  overcast: "rgba(70, 72, 80, 0.18)",
};

export class Weather {
  type: WeatherType = "clear";
  private drops: Drop[] = [];
  private t = 0;
  private w = 0;
  private h = 0;

  /** Choose the weather for a match from its map (deterministic & replayable). */
  configure(seed: number, mapName: string) {
    this.type = pickWeather(seed, mapName);
    this.drops = [];
    this.w = 0;
    this.h = 0;
  }

  private targetCount(density: number): number {
    if (this.type === "rain") return Math.round(240 * density);
    if (this.type === "snow") return Math.round(170 * density);
    return 0;
  }

  private reseed(W: number, H: number, density: number) {
    this.w = W; this.h = H;
    const n = this.targetCount(density);
    this.drops = [];
    for (let i = 0; i < n; i++) {
      this.drops.push(this.type === "rain"
        ? { x: Math.random() * W, y: Math.random() * H, v: 900 + Math.random() * 500, len: 10 + Math.random() * 10, phase: 0 }
        : { x: Math.random() * W, y: Math.random() * H, v: 45 + Math.random() * 65, len: 1.4 + Math.random() * 1.8, phase: Math.random() * Math.PI * 2 });
    }
  }

  /** Update + draw in one pass, in screen space. Call after the world render and
   *  before the HUD. `density` (0..1) thins particles for the reduce-effects setting. */
  render(ctx: CanvasRenderingContext2D, W: number, H: number, dt: number, density = 1) {
    if (this.type === "clear") return;
    this.t += dt;
    const tint = TINT[this.type];

    ctx.save();
    if (tint) { ctx.fillStyle = tint; ctx.fillRect(0, 0, W, H); }

    if (this.type === "overcast") { ctx.restore(); return; }
    if (this.drops.length !== this.targetCount(density) || this.w !== W || this.h !== H) this.reseed(W, H, density);

    if (this.type === "rain") {
      const wind = 140;
      ctx.strokeStyle = "rgba(190, 210, 235, 0.32)";
      ctx.lineWidth = 1.1;
      ctx.beginPath();
      for (const d of this.drops) {
        d.y += d.v * dt;
        d.x += wind * dt;
        if (d.y > H) { d.y = -d.len; d.x = Math.random() * W; }
        else if (d.x > W) d.x -= W;
        const dxl = (wind / d.v) * d.len;
        ctx.moveTo(d.x, d.y);
        ctx.lineTo(d.x - dxl, d.y - d.len);
      }
      ctx.stroke();
    } else { // snow
      ctx.fillStyle = "rgba(255, 255, 255, 0.85)";
      for (const d of this.drops) {
        d.y += d.v * dt;
        const sway = Math.sin(this.t * 1.5 + d.phase) * 14;
        if (d.y > H) { d.y = -4; d.x = Math.random() * W; }
        ctx.beginPath();
        ctx.arc(d.x + sway, d.y, d.len, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    ctx.restore();
  }
}
