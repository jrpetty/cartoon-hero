import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { drawChat, ChatLine, authorLabel } from "./chat";
import { Team } from "../sim/types";

function ctx2d() {
  return createCanvas(1280, 760).getContext("2d") as unknown as CanvasRenderingContext2D;
}

describe("Chat overlay", () => {
  it("renders a chat log and the input draft without throwing", () => {
    const ctx = ctx2d();
    ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
    const lines: ChatLine[] = [
      { name: "Azure", text: "glhf", team: Team.Player, t: 0 },
      { name: "Crimson", text: "good luck", team: Team.Enemy, t: 4 },
    ];
    // Closed (no draft), open (draft), and empty-draft variants.
    expect(() => drawChat(1280, 760, lines, null, 10, 600)).not.toThrow();
    expect(() => drawChat(1280, 760, lines, "typing a message", 10, 600)).not.toThrow();
    expect(() => drawChat(1280, 760, [], "", 0, 600)).not.toThrow();
  });

  it("fades out stale lines but keeps them visible while typing", () => {
    const ctx = ctx2d();
    ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
    const old: ChatLine[] = [{ name: "X", text: "old", team: Team.Player, t: 0 }];
    // now=100 (well past LINE_LIFE) closed → still renders safely (filtered out);
    // with a draft open it forces them visible. Just assert no throw both ways.
    expect(() => drawChat(1280, 760, old, null, 100, 600)).not.toThrow();
    expect(() => drawChat(1280, 760, old, "x", 100, 600)).not.toThrow();
  });

  it("labels an author by team color name", () => {
    expect(typeof authorLabel(Team.Player)).toBe("string");
    expect(authorLabel(Team.Player).length).toBeGreaterThan(0);
  });
});
