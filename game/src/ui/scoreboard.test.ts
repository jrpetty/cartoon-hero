import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { drawScoreboard } from "./scoreboard";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { generateMap } from "../maps/generator";

describe("Scoreboard renders", () => {
  it("draws a multi-team (2v2) scoreboard without throwing", () => {
    const W = 1280;
    const H = 760;
    const ctx = createCanvas(W, H).getContext("2d") as unknown as CanvasRenderingContext2D;
    const w = new World(7);
    w.init(generateMap("open_plains", 7, 4), [{}, {}, {}, {}], [1, 1, 1, 1], [0, 0, 1, 1]);
    w.spawnUnit(Team.Player, "knight", 800, 800);
    w.player(Team.Enemy).stats.unitsKilled = 5;
    w.player(2 as Team).defeated = true; // a fallen realm renders dimmed

    ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
    expect(() => drawScoreboard(W, H, w, Team.Player)).not.toThrow();
  });
});
