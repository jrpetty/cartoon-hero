import { describe, expect, it, beforeAll } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = { createElement: () => createCanvas(1, 1) };
import { ui } from "./ui";
import { CodexScreen } from "./codex";
import { Profile } from "../meta/profile";
import { UNITS } from "../content/units";

/**
 * The Codex has to show everything the game now has — four ages, nine Oaths,
 * six factions and thirty-nine units — without breaking or running off the
 * screen. Every tab, every unit, at a small and a large window.
 */

beforeAll(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

function frame(c: CodexScreen, W: number, H: number, wheel = 0) {
  const canvas = createCanvas(W, H);
  ui.begin(canvas.getContext("2d") as unknown as CanvasRenderingContext2D,
    { mx: 200, my: H / 2, clicked: false, rightClicked: false, alt: false, wheel } as Parameters<typeof ui.begin>[1]);
  c.draw(W, H, 1, new Profile());
}

describe("The Codex", () => {
  it("draws every tab and every unit without throwing", () => {
    for (const [W, H] of [[1280, 760], [1600, 900]] as const) {
      const c = new CodexScreen();
      for (const tab of ["units", "buildings", "tech", "records"]) {
        (c as unknown as { tab: string }).tab = tab;
        expect(() => frame(c, W, H), `${tab} at ${W}x${H}`).not.toThrow();
      }
      (c as unknown as { tab: string }).tab = "units";
      for (const id of Object.keys(UNITS)) {
        (c as unknown as { selUnit: string }).selUnit = id;
        expect(() => frame(c, W, H), id).not.toThrow();
      }
    }
  });

  it("scrolls the unit list to its end", () => {
    const c = new CodexScreen();
    (c as unknown as { tab: string }).tab = "units";
    for (let i = 0; i < 20; i++) frame(c, 1280, 760, 400);
    expect((c as unknown as { unitScroll: number }).unitScroll).toBeGreaterThan(0);
  });
});
