import { describe, expect, it, beforeEach, afterEach } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { fullscreenSupported, isFullscreen, toggleFullscreen } from "./fullscreen";
import { ui } from "../ui/ui";
import { FULLSCREEN_ZONE, fullscreenButton } from "../ui/fullscreen_button";
import { ACTIONS, chordFor, conflictsOf, type Keybinds } from "../meta/keybinds";

/**
 * A stand-in document. The real rules this has to respect: requests may
 * resolve or reject, some browsers only expose webkit-prefixed methods, and
 * some pages cannot go fullscreen at all.
 */
function fakeDocument(opts: {
  enabled?: boolean; webkitOnly?: boolean; reject?: boolean; throws?: boolean;
} = {}) {
  const calls: string[] = [];
  const state = { el: null as unknown };
  const settle = () => (opts.reject ? Promise.reject(new Error("denied")) : Promise.resolve());
  const root: Record<string, unknown> = {};
  if (opts.webkitOnly) {
    root.webkitRequestFullscreen = () => { calls.push("webkitRequest"); if (opts.throws) throw new Error("x"); state.el = root; };
  } else {
    root.requestFullscreen = () => {
      calls.push("request");
      if (opts.throws) throw new Error("x");
      if (!opts.reject) state.el = root;
      return settle();
    };
  }
  const d: Record<string, unknown> = { documentElement: root };
  Object.defineProperty(d, opts.webkitOnly ? "webkitFullscreenElement" : "fullscreenElement", {
    get: () => state.el,
  });
  if (opts.webkitOnly) {
    d.webkitFullscreenEnabled = opts.enabled ?? true;
    d.webkitExitFullscreen = () => { calls.push("webkitExit"); state.el = null; };
  } else {
    d.fullscreenEnabled = opts.enabled ?? true;
    d.exitFullscreen = () => { calls.push("exit"); state.el = null; return Promise.resolve(); };
  }
  return { d, calls, state };
}

let saved: unknown;
beforeEach(() => { saved = (globalThis as { document?: unknown }).document; });
afterEach(() => { (globalThis as { document?: unknown }).document = saved; });
const install = (d: unknown) => { (globalThis as { document?: unknown }).document = d; };

describe("Toggling fullscreen", () => {
  it("enters, and then leaves", () => {
    const f = fakeDocument();
    install(f.d);
    expect(isFullscreen()).toBe(false);
    toggleFullscreen();
    expect(f.calls).toEqual(["request"]);
    expect(isFullscreen()).toBe(true);
    toggleFullscreen();
    expect(f.calls).toEqual(["request", "exit"]);
    expect(isFullscreen()).toBe(false);
  });

  it("falls back to the webkit-prefixed API", () => {
    // Older Safari only ships the prefixed names.
    const f = fakeDocument({ webkitOnly: true });
    install(f.d);
    expect(fullscreenSupported()).toBe(true);
    toggleFullscreen();
    toggleFullscreen();
    expect(f.calls).toEqual(["webkitRequest", "webkitExit"]);
  });

  it("does nothing at all where fullscreen is not allowed", () => {
    // iPhone Safari, or an iframe without allow="fullscreen".
    const f = fakeDocument({ enabled: false });
    install(f.d);
    expect(fullscreenSupported()).toBe(false);
    toggleFullscreen();
    expect(f.calls).toEqual([]);
  });

  it("never throws when the browser refuses", async () => {
    // A convenience toggle must not be able to take the game down.
    const rejects = fakeDocument({ reject: true });
    install(rejects.d);
    expect(() => toggleFullscreen()).not.toThrow();
    await Promise.resolve();
    expect(isFullscreen()).toBe(false);

    const throws = fakeDocument({ throws: true });
    install(throws.d);
    expect(() => toggleFullscreen()).not.toThrow();
  });

  it("is harmless with no document at all, as in a headless test", () => {
    install(undefined);
    expect(fullscreenSupported()).toBe(false);
    expect(isFullscreen()).toBe(false);
    expect(() => toggleFullscreen()).not.toThrow();
  });
});

describe("Gesture zones", () => {
  const begin = () => {
    const canvas = createCanvas(800, 600);
    const ctx = canvas.getContext("2d") as unknown as CanvasRenderingContext2D;
    ui.begin(ctx, {
      mx: -99, my: -99, clicked: false, rightClicked: false, alt: false,
      leftHeld: false, rightHeld: false, ctrlHeld: false, shiftHeld: false, wheel: 0,
    });
    return ctx;
  };

  it("maps a button to the canvas pixels a click will arrive in", () => {
    begin();
    ui.registerGestureZone("x", 100, 50, 40, 20);
    expect(ui.gestureAt(120, 60)).toBe("x");
    expect(ui.gestureAt(99, 60)).toBeNull();
    expect(ui.gestureAt(141, 60)).toBeNull();
  });

  it("stays exact under the interface-scale slider", () => {
    // Registered in layout coordinates, clicked in canvas pixels: a 1.5x UI
    // puts a button laid out at (100,100) at (150,150) on screen.
    begin();
    ui.pushScale(1.5);
    ui.registerGestureZone("x", 100, 100, 20, 20);
    ui.popScale();
    expect(ui.gestureAt(160, 160)).toBe("x");
    expect(ui.gestureAt(110, 110)).toBeNull();
  });

  it("stays exact inside a scrolled panel", () => {
    begin();
    ui.pushScroll(200);
    ui.registerGestureZone("x", 10, 300, 50, 20);
    ui.popScroll();
    expect(ui.gestureAt(20, 110)).toBe("x");
    expect(ui.gestureAt(20, 310)).toBeNull();
  });

  it("is cleared every frame, so a button that stops being drawn stops working", () => {
    begin();
    ui.registerGestureZone("x", 0, 0, 50, 50);
    expect(ui.gestureAt(10, 10)).toBe("x");
    begin();
    expect(ui.gestureAt(10, 10)).toBeNull();
  });
});

describe("The fullscreen button", () => {
  const frame = () => {
    const canvas = createCanvas(800, 600);
    const ctx = canvas.getContext("2d") as unknown as CanvasRenderingContext2D;
    ui.begin(ctx, {
      mx: -99, my: -99, clicked: false, rightClicked: false, alt: false,
      leftHeld: false, rightHeld: false, ctrlHeld: false, shiftHeld: false, wheel: 0,
    });
  };

  it("registers where it is when fullscreen is possible", () => {
    install(fakeDocument().d);
    frame();
    fullscreenButton(600, 20, 160, 36);
    expect(ui.gestureAt(680, 38)).toBe(FULLSCREEN_ZONE);
  });

  it("registers nothing when fullscreen is impossible, so a click does nothing", () => {
    install(fakeDocument({ enabled: false }).d);
    frame();
    fullscreenButton(600, 20, 160, 36);
    expect(ui.gestureAt(680, 38)).toBeNull();
  });
});

describe("The hotkey", () => {
  it("defaults to Alt+Enter, which does not collide with chat on Enter", () => {
    const binds: Keybinds = {}; // no overrides = the defaults
    expect(chordFor(binds, "fullscreen")).toBe("Alt+Enter");
    expect(chordFor(binds, "chat")).toBe("Enter");
    const clash = conflictsOf(binds).get("Alt+Enter");
    expect(clash === undefined || clash.length <= 1).toBe(true);
    expect(ACTIONS.some((a) => a.id === "fullscreen")).toBe(true);
  });
});
