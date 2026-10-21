import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = {
  createElement: () => createCanvas(1, 1),
};
import { Renderer } from "./renderer";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { generateMap } from "../maps/generator";
import { Camera } from "../engine/camera";
import { Particles } from "../engine/particles";

/**
 * The fog frontier.
 *
 * Fog is one value per nav cell and only ever takes three of them, so
 * upscaling it straight to the screen ramps between cell *centres* and leaves
 * a visible staircase along every diagonal frontier — which is most of them.
 * Blurring the mask first turns that edge into the soft falloff a scouted
 * horizon should have, and is the single largest thing wrong with how this
 * game looked.
 */

function frame(W = 900, H = 520) {
  const map = generateMap("open_plains", 6, 2);
  const world = new World(6);
  world.init(map, [{}, {}], [1, 1], [0, 1]);
  // One tick so vision is computed; the rest of the map stays unexplored.
  world.tick();
  world.drainEvents();
  const canvas = createCanvas(W, H) as unknown as Shot;
  const r = new Renderer(canvas as unknown as HTMLCanvasElement);
  r.prepare(map);
  const cam = new Camera();
  cam.setWorld(map.worldW, map.worldH);
  cam.setViewport(W, H);
  cam.zoom = 0.6;
  cam.centerOn(map.starts[0].x, map.starts[0].y);
  r.render(world, cam, new Particles(), 1 / 60, 1, Team.Player, [], null,
    { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null, -1, 1);
  return { canvas, W, H };
}

interface Shot { width: number; height: number; getContext(t: "2d"): CanvasRenderingContext2D }

function scanline(canvas: Shot, y: number): number[] {
  const d = canvas.getContext("2d").getImageData(0, y, canvas.width, 1).data;
  const out: number[] = [];
  for (let x = 0; x < canvas.width; x++) {
    const o = x * 4;
    out.push((d[o] + d[o + 1] + d[o + 2]) / 3);
  }
  return out;
}

describe("The fog frontier is soft", () => {
  // The frontier's softness is asserted in fogblur.test.ts, against the mask
  // itself. Measuring it here looked reasonable and was worthless: terrain
  // noise and the vignette dominate any scanline, so the test passed
  // identically with the blur switched off.
  it("still hides the unexplored map completely", () => {
    // Softening the edge must not leak the map through it.
    const { canvas } = frame();
    const corner = canvas.getContext("2d").getImageData(0, 0, 24, 24).data;
    let sum = 0;
    for (let i = 0; i < corner.length; i += 4) sum += (corner[i] + corner[i + 1] + corner[i + 2]) / 3;
    expect(sum / (corner.length / 4), "unexplored ground is not dark").toBeLessThan(26);
  });

  it("leaves the ground around your own base plainly lit", () => {
    const { canvas, W, H } = frame();
    const d = canvas.getContext("2d")
      .getImageData(Math.floor(W / 2) - 8, Math.floor(H / 2) - 8, 16, 16).data;
    let sum = 0;
    for (let i = 0; i < d.length; i += 4) sum += (d[i] + d[i + 1] + d[i + 2]) / 3;
    expect(sum / (d.length / 4), "your own base is in the dark").toBeGreaterThan(40);
  });
});
