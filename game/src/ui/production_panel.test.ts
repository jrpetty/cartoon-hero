import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { drawProductionPanel } from "./production_panel";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { generateMap } from "../maps/generator";

function ctx2d() {
  return createCanvas(1280, 760).getContext("2d") as unknown as CanvasRenderingContext2D;
}
const begin = (ctx: CanvasRenderingContext2D, mx: number, my: number, clicked: boolean) =>
  ui.begin(ctx, { mx, my, clicked, rightClicked: false, alt: false });

describe("Production panel", () => {
  it("renders production buildings and reports clicked rows", () => {
    const w = new World(5);
    w.init(generateMap("open_plains", 5, 2), [{}, {}], [1, 1]);
    const p = w.player(Team.Player);
    p.age = 2;
    p.resources.food = 9999; p.resources.wood = 9999; p.resources.gold = 9999;
    const barracks = w.spawnBuilding(Team.Player, "barracks", 800, 800, true);
    const stable = w.spawnBuilding(Team.Player, "stable", 880, 800, true);
    w.trainUnit(Team.Player, barracks.id, "militia"); // an active producer

    const ctx = ctx2d();
    begin(ctx, 0, 0, false);
    expect(() => drawProductionPanel(1280, 760, w, Team.Player)).not.toThrow();
    expect(drawProductionPanel(1280, 760, w, Team.Player)).toBeNull(); // no click → no jump

    // Scan a click down the panel; both production buildings should be hittable.
    const seen = new Set<number>();
    for (let my = 300; my < 480; my += 2) {
      begin(ctx, 24, my, true);
      const r = drawProductionPanel(1280, 760, w, Team.Player);
      if (r != null) seen.add(r);
    }
    expect(seen.has(barracks.id)).toBe(true);
    expect(seen.has(stable.id)).toBe(true);
  });

  it("is safe with no production buildings", () => {
    const w = new World(6);
    w.init(generateMap("open_plains", 6, 2), [{}, {}], [1, 1]);
    // Remove the starting Town Center so there's nothing that trains.
    for (const e of w.entitiesOf(Team.Player)) if (e.type === "town_center") w.dealDamage(Team.Enemy, e, 1e9, "test");
    const ctx = ctx2d();
    begin(ctx, 0, 0, false);
    expect(() => drawProductionPanel(1280, 760, w, Team.Player)).not.toThrow();
  });
});
