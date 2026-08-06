import { describe, expect, it, beforeAll } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { SetupScreen } from "./screens";
import { Profile } from "../meta/profile";

/**
 * The Skirmish setup screen has to stay usable at any window height.
 *
 * It lays its panels out by accumulating `y` downward, so how tall it ends up
 * depends on how many battlefield cards exist. Seven presets plus Random already
 * measured 914px of content against 834px of room on a 1010px window, and a
 * single custom map adds a whole row — which put "To Battle!" below the bottom
 * of the screen with no way to reach it and no way to start a game.
 */

beforeAll(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { for (const k of Object.keys(store)) delete store[k]; },
    key: () => null,
    length: 0,
  };
});

const FOOTER = 76;

function harness(W: number, H: number) {
  const canvas = createCanvas(W, H);
  const ctx = canvas.getContext("2d") as unknown as CanvasRenderingContext2D;
  const s = new SetupScreen();
  const profile = new Profile();
  const frame = (o: Partial<{ mx: number; my: number; clicked: boolean; wheel: number }> = {}) => {
    ui.begin(ctx, {
      mx: o.mx ?? -99, my: o.my ?? -99, clicked: !!o.clicked, rightClicked: false, alt: false,
      leftHeld: false, rightHeld: false, ctrlHeld: false, shiftHeld: false, wheel: o.wheel ?? 0,
    });
    return s.draw(W, H, 1, profile);
  };
  const colW = Math.min(880, W - 80);
  const x0 = W / 2 - colW / 2;
  return { s, frame, colW, x0, footerY: H - FOOTER + 16 };
}

describe("The setup screen's actions are always reachable", () => {
  for (const [W, H] of [[1920, 1010], [1920, 700], [1280, 620], [1024, 560]] as const) {
    it(`starts, spectates and goes back at ${W}x${H}`, () => {
      const h = harness(W, H);
      h.frame(); // lay out once so the scroll clamp has a content height
      const mid = h.footerY + 22;
      expect(h.frame({ mx: h.x0 + h.colW - 110, my: mid, clicked: true }), "To Battle!").toBe("start");
      expect(h.frame({ mx: h.x0 + h.colW - 295, my: mid, clicked: true }), "Watch").toBe("spectate");
      expect(h.frame({ mx: h.x0 + 65, my: mid, clicked: true }), "Back").toBe("back");
    });
  }

  it("is genuinely taller than the window, so the fix is load-bearing", () => {
    // If this ever stops overflowing the pinned bar is untested by the cases
    // above, and the screen could silently go back to relying on there being
    // room.
    const h = harness(1920, 1010);
    h.frame();
    const inner = h.s as unknown as { contentH: number };
    expect(inner.contentH, "the setup content now fits, so nothing above is proving anything")
      .toBeGreaterThan(1010 - 100 - FOOTER);
  });
});

describe("Scrolling the setup panels", () => {
  it("moves the content and takes hit-testing with it", () => {
    // The half of the fix that is easy to get wrong: the context is translated,
    // so the pointer has to move by the same amount or every widget below the
    // fold responds to a click somewhere else entirely.
    const h = harness(1920, 700);
    h.frame();
    const inner = h.s as unknown as { scroll: number; config: { presetId: string } };
    const cardX = h.x0 + 16 + 60;

    inner.scroll = 0;
    h.frame({ mx: cardX, my: 200, clicked: true });
    const atTop = inner.config.presetId;

    // Scroll down two card rows and click the same point on screen. The pointer
    // has to be over the content area — the wheel is deliberately ignored
    // elsewhere, so a frame with no pointer position scrolls nothing.
    h.frame({ mx: cardX, my: 300, wheel: 400 });
    expect(inner.scroll, "the wheel did not scroll anything").toBeGreaterThan(0);
    h.frame({ mx: cardX, my: 200, clicked: true });
    expect(inner.config.presetId, "the same screen point selected the same card after scrolling")
      .not.toBe(atTop);
  });

  it("never scrolls past the end, or above the start", () => {
    const h = harness(1920, 700);
    h.frame();
    const inner = h.s as unknown as { scroll: number; contentH: number };
    for (let i = 0; i < 20; i++) h.frame({ mx: 900, my: 300, wheel: 400 });
    const viewH = 700 - 100 - FOOTER;
    expect(inner.scroll).toBeLessThanOrEqual(Math.max(0, inner.contentH - viewH) + 1);
    for (let i = 0; i < 40; i++) h.frame({ mx: 900, my: 300, wheel: -400 });
    expect(inner.scroll).toBe(0);
  });

  it("ignores the wheel when the pointer is over the pinned bar", () => {
    // Otherwise the content slides about while you are aiming at "To Battle!".
    const h = harness(1920, 700);
    h.frame();
    const inner = h.s as unknown as { scroll: number };
    h.frame({ mx: 900, my: 300, wheel: 400 });
    const before = inner.scroll;
    h.frame({ mx: 900, my: h.footerY + 22, wheel: 400 });
    expect(inner.scroll).toBe(before);
  });

  it("renders at every size without throwing", () => {
    for (const [W, H] of [[640, 480], [1920, 1080], [2560, 1440], [900, 400]] as const) {
      const h = harness(W, H);
      expect(() => { h.frame(); h.frame({ mx: 500, my: 300 }); }, `${W}x${H}`).not.toThrow();
    }
  });
});
