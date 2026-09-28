import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = {
  createElement: () => createCanvas(1, 1),
};
import { NightLayer, lightFor } from "./nightlight";
import { drawBuilding } from "./draw";
import { makeEntity } from "../sim/world";
import { BuildState, Entity, Kind, Team } from "../sim/types";
import { nightAmount, skyTint } from "../content/daynight";

/**
 * Night used to be one flat navy rectangle over everything, drawn on top of the
 * lit windows and watchfires it was supposed to show off. Now the darkness is
 * a layer with holes cut where the lights are.
 */

interface Shot { width: number; height: number; getContext(t: "2d"): CanvasRenderingContext2D }

function building(type: string, over: Partial<Entity> = {}): Entity {
  const e = makeEntity();
  Object.assign(e, {
    kind: Kind.Building, type, team: Team.Player, x: 100, y: 100, radius: 32,
    hp: 100, maxHp: 100, buildState: BuildState.Done, buildProgress: 1, alive: true,
  }, over);
  return e;
}

function unit(over: Partial<Entity> = {}): Entity {
  const e = makeEntity();
  Object.assign(e, { kind: Kind.Unit, type: "spearman", team: Team.Player, x: 50, y: 50, radius: 8, hp: 10, maxHp: 10, alive: true }, over);
  return e;
}

describe("What gives off light at night", () => {
  it("lights lived-in buildings, and a watchfire most of all", () => {
    const house = lightFor(building("house"), 0, false);
    const fire = lightFor(building("watchfire", { radius: 16 }), 0, false);
    expect(house).not.toBeNull();
    expect(fire!.r, "a watchfire should throw the widest pool of light").toBeGreaterThan(house!.r);
  });

  it("keeps building sites and plain walls dark", () => {
    expect(lightFor(building("house", { buildState: BuildState.Foundation, buildProgress: 0 }), 0, false)).toBeNull();
    expect(lightFor(building("house", { buildState: BuildState.UnderConstruction, buildProgress: 0.6 }), 0, false)).toBeNull();
    expect(lightFor(building("palisade", { radius: 16 }), 0, false)).toBeNull();
  });

  it("lights anything that is burning", () => {
    expect(lightFor(building("palisade", { radius: 16, hp: 20 }), 0, false), "a burning wall stayed dark").not.toBeNull();
  });

  it("gives your own units a lantern and nobody else's", () => {
    expect(lightFor(unit(), 0, true)).not.toBeNull();
    expect(lightFor(unit({ team: Team.Enemy }), 0, false), "a light on enemy units makes night easier to scout").toBeNull();
  });

  it("only cuts through the dark at night, not at dusk", () => {
    expect(nightAmount(0.2)).toBe(0);
    expect(nightAmount(0.46)).toBe(0);
    expect(nightAmount(0.75)).toBe(1);
  });
});

describe("The night layer", () => {
  const W = 400, H = 240;
  function frame(lights: { x: number; y: number; r: number; s: number }[], night: number) {
    const canvas = createCanvas(W, H) as unknown as Shot;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#6da944";
    ctx.fillRect(0, 0, W, H);
    new NightLayer().draw(ctx, W, H, skyTint(0.75), night, lights, 1, (x, y) => [x, y]);
    const d = ctx.getImageData(0, 0, W, H).data;
    return (x: number, y: number) => {
      const o = (y * W + x) * 4;
      return d[o] * 0.3 + d[o + 1] * 0.59 + d[o + 2] * 0.11;
    };
  }

  it("is lighter at a light than away from it", () => {
    const lum = frame([{ x: 100, y: 120, r: 60, s: 0.8 }], 1);
    expect(lum(100, 120) - lum(320, 120), "the light made no difference").toBeGreaterThan(30);
  });

  it("leaves the dark alone away from the lights — it is still night", () => {
    const lit = frame([{ x: 100, y: 120, r: 60, s: 0.8 }], 1);
    const dark = frame([], 1);
    expect(Math.abs(lit(320, 120) - dark(320, 120))).toBeLessThan(2);
    expect(dark(200, 120), "the night isn't dark").toBeLessThan(110);
  });

  it("does nothing before nightfall", () => {
    const withLight = frame([{ x: 100, y: 120, r: 60, s: 0.8 }], 0);
    const without = frame([], 0);
    expect(Math.abs(withLight(100, 120) - without(100, 120))).toBeLessThan(2);
  });
});

describe("Building sites", () => {
  // How much of the finished building's own art shows at full strength: the
  // team-blue roof is the most saturated thing in it.
  function roof(state: BuildState, progress: number): number {
    const canvas = createCanvas(200, 200) as unknown as Shot;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#6da944";
    ctx.fillRect(0, 0, 200, 200);
    drawBuilding(ctx, building("house", { buildState: state, buildProgress: progress }), 0, Team.Player);
    const d = ctx.getImageData(0, 0, 200, 200).data;
    let n = 0;
    for (let i = 0; i < d.length; i += 4) if (d[i + 2] > d[i] + 80 && d[i + 2] > d[i + 1] + 40) n++;
    return n;
  }

  it("show what they will become, and rise as the work goes on", () => {
    const planned = roof(BuildState.Foundation, 0);
    const half = roof(BuildState.UnderConstruction, 0.5);
    const nearly = roof(BuildState.UnderConstruction, 0.95);
    const done = roof(BuildState.Done, 1);
    expect(planned, "a foundation shows the finished building at full strength").toBeLessThan(done * 0.2);
    expect(nearly, "the building didn't rise as it was built").toBeGreaterThan(half);
    expect(nearly).toBeGreaterThan(done * 0.6);
  });
});
