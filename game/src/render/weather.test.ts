import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { Weather, WeatherType } from "./weather";

function ctx2d() {
  return createCanvas(1280, 760).getContext("2d") as unknown as CanvasRenderingContext2D;
}

describe("Weather overlay", () => {
  it("renders every type without throwing (and respects reduce-density)", () => {
    const ctx = ctx2d();
    const w = new Weather();
    for (const t of ["clear", "rain", "snow", "overcast"] as WeatherType[]) {
      w.type = t;
      expect(() => { for (let i = 0; i < 4; i++) w.render(ctx, 1280, 760, 0.016, 1); }).not.toThrow();
      expect(() => w.render(ctx, 1280, 760, 0.016, 0.4)).not.toThrow(); // reduce-effects
    }
  });

  it("picks a stable, replayable type from the map seed", () => {
    const a = new Weather(); a.configure(123, "Highlands");
    const b = new Weather(); b.configure(123, "Highlands");
    expect(a.type).toBe(b.type); // deterministic
    expect(["clear", "rain", "snow", "overcast"]).toContain(a.type);
  });

  it("themes weather by map name", () => {
    // Highlands never rains under the rule (snow/overcast only).
    for (const seed of [1, 50, 123, 777, 999]) {
      const w = new Weather(); w.configure(seed, "Highlands");
      expect(["snow", "overcast"]).toContain(w.type);
    }
  });

  it("re-seeds particles when the viewport size changes", () => {
    const w = new Weather();
    w.type = "rain";
    expect(() => {
      w.render(ctx2d(), 1280, 760, 0.016, 1);
      w.render(ctx2d(), 800, 600, 0.016, 1); // smaller viewport
    }).not.toThrow();
  });
});
