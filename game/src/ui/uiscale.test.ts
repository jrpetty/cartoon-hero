import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { SettingsScreen } from "./settings_screen";
import { DEFAULT_SETTINGS, Settings } from "../meta/settings";
import { chordFor } from "../meta/keybinds";

const ctxOf = (w = 1280, h = 760) =>
  createCanvas(w, h).getContext("2d") as unknown as CanvasRenderingContext2D;

describe("Interface scale", () => {
  const frame = (ctx: CanvasRenderingContext2D, mx: number, my: number) =>
    ui.begin(ctx, { mx, my, clicked: true, rightClicked: false, alt: false });

  it("hit-tests a scaled widget at the place it is actually drawn", () => {
    // A button laid out at (100,100) in a 1.5× block appears at (150,150) on
    // screen. Both must agree, or every click lands in the wrong place — which
    // is the one bug an interface scale can introduce.
    const ctx = ctxOf();
    frame(ctx, 150, 150);
    ui.pushScale(1.5);
    expect(ui.hit(100, 100, 40, 40)).toBe(true);
    expect(ui.hit(160, 160, 40, 40)).toBe(false); // where it would be unscaled
    ui.popScale();
    // Outside the block, the pointer is back in canvas pixels.
    expect(ui.mx).toBe(150);
    expect(ui.my).toBe(150);
  });

  it("restores the pointer exactly, so a scale can't drift over a frame", () => {
    const ctx = ctxOf();
    frame(ctx, 640, 400);
    for (const s of [0.8, 1, 1.25, 1.5]) {
      ui.pushScale(s);
      ui.popScale();
    }
    expect(ui.mx).toBeCloseTo(640, 6);
    expect(ui.my).toBeCloseTo(400, 6);
  });

  it("is reset by begin, so an unbalanced push can't leak into the next frame", () => {
    const ctx = ctxOf();
    frame(ctx, 100, 100);
    ui.pushScale(1.5);
    frame(ctx, 100, 100); // a new frame, mid-scale
    expect(ui.scale).toBe(1);
    expect(ui.mx).toBe(100);
  });

  it("draws the same layout at every scale — only bigger", () => {
    // The screens take W/H and lay out from them, so a scaled screen must be
    // handed the reduced space. Both of these must render without throwing and
    // put the Back button in the same *layout* position.
    const scr = new SettingsScreen();
    const st: Settings = { ...DEFAULT_SETTINGS, keybinds: {} };
    for (const scale of [0.8, 1, 1.5]) {
      const ctx = ctxOf(1600, 900);
      frame(ctx, -99, -99);
      ui.pushScale(scale);
      expect(() => scr.draw(1600 / scale, 900 / scale, 1, st, false)).not.toThrow();
      ui.popScale();
    }
  });
});

describe("Rebinding", () => {
  const mods = { ctrl: false, shift: false, alt: false };

  const openControls = (scr: SettingsScreen, st: Settings, ctx: CanvasRenderingContext2D) => {
    const W = 1600, H = 900;
    const step = (mx: number, my: number, clicked: boolean) => {
      ui.begin(ctx, { mx, my, clicked, rightClicked: false, alt: false });
      scr.draw(W, H, 1, st, false);
    };
    step(-99, -99, false);
    step(W / 2 + 80, 100, true); // the Controls tab
    step(-99, -99, false);
    return step;
  };

  /**
   * Centre of the first rebind button in the left column, derived from the
   * screen's own layout constants rather than eyeballed, so a spacing change
   * moves the test with it instead of silently missing.
   */
  const firstBindButton = (): [number, number] => {
    const W = 1600, colW = 400, gap = 40, top = 132;
    const x0 = Math.round(W / 2 - (colW * 2 + gap) / 2);
    const bw = 116;
    return [x0 + colW - bw - 22 + bw / 2, top + 24 + 30 + 12];
  };

  it("ignores keys until a binding is actually listening", () => {
    const scr = new SettingsScreen();
    expect(scr.captureKey("Z", mods)).toBe(false);
  });

  it("binds the next key pressed, and only that one", () => {
    const scr = new SettingsScreen();
    const st: Settings = { ...DEFAULT_SETTINGS, keybinds: {} };
    const ctx = ctxOf(1600, 900);
    const step = openControls(scr, st, ctx);
    // Attack-move is the first row of the first group in the left column.
    step(...firstBindButton(), true);
    expect(scr.captureKey("Z", mods)).toBe(true); // swallowed while listening
    step(-99, -99, false);                        // the draw applies it
    expect(chordFor(st.keybinds, "attackMove")).toBe("Z");
    expect(scr.captureKey("Y", mods)).toBe(false); // no longer listening
    expect(chordFor(st.keybinds, "stop")).toBe("S");
  });

  it("lets Escape and Tab be bound rather than acting on the screen", () => {
    // The whole point of routing keys through the screen first: a player who
    // wants Tab for something else must be able to say so.
    const scr = new SettingsScreen();
    const st: Settings = { ...DEFAULT_SETTINGS, keybinds: {} };
    const ctx = ctxOf(1600, 900);
    const step = openControls(scr, st, ctx);
    step(...firstBindButton(), true);
    expect(scr.captureKey("Tab", mods)).toBe(true);
    step(-99, -99, false);
    expect(chordFor(st.keybinds, "attackMove")).toBe("Tab");
  });

  it("cancels on Escape without changing anything", () => {
    const scr = new SettingsScreen();
    const st: Settings = { ...DEFAULT_SETTINGS, keybinds: {} };
    const ctx = ctxOf(1600, 900);
    const step = openControls(scr, st, ctx);
    step(...firstBindButton(), true);
    expect(scr.captureKey("Escape", mods)).toBe(true);
    step(-99, -99, false);
    expect(chordFor(st.keybinds, "attackMove")).toBe("A");
    expect(scr.captureKey("Z", mods)).toBe(false); // listening really did stop
  });

  it("refuses a chord the browser has spoken for, and keeps listening", () => {
    const scr = new SettingsScreen();
    const st: Settings = { ...DEFAULT_SETTINGS, keybinds: {} };
    const ctx = ctxOf(1600, 900);
    const step = openControls(scr, st, ctx);
    step(...firstBindButton(), true);
    expect(scr.captureKey("F5", mods)).toBe(true);
    step(-99, -99, false);
    expect(chordFor(st.keybinds, "attackMove")).toBe("A"); // unchanged
    expect(scr.captureKey("Z", mods)).toBe(true);          // still waiting
    step(-99, -99, false);
    expect(chordFor(st.keybinds, "attackMove")).toBe("Z");
  });
});
