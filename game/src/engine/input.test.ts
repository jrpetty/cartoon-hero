import { describe, it, expect, beforeAll, afterAll, vi } from "vitest";
import { Input } from "./input";

// The Input constructor touches window/el; provide minimal stubs (like boot-check),
// and a *monotonic* performance.now so click/double-click timing is deterministic
// even if another test file leaked fake timers. Restore everything afterwards.
let hadWindow = false;
let realPerf: unknown;
beforeAll(() => {
  hadWindow = "window" in globalThis;
  if (!hadWindow) (globalThis as any).window = { addEventListener() {}, removeEventListener() {} };
  realPerf = (globalThis as any).performance;
  let clock = 1000;
  (globalThis as any).performance = { now: () => (clock += 1000) };
});
afterAll(() => {
  if (!hadWindow) delete (globalThis as any).window;
  (globalThis as any).performance = realPerf;
});

const fakeEl = () => ({ addEventListener() {}, removeEventListener() {}, getBoundingClientRect: () => ({ left: 0, top: 0 }) });
const t = (id: number, x: number, y: number) => ({ identifier: id, clientX: x, clientY: y });
const te = (changed: any[]) => ({ preventDefault() {}, changedTouches: changed });

describe("touch input", () => {
  it("a tap maps to a left click", () => {
    const i = new Input(fakeEl() as any);
    let click: number[] | null = null;
    i.onLeftClick = (x, y) => { click = [x, y]; };
    (i as any).handleTouchStart(te([t(1, 100, 120)]));
    expect(i.leftDown).toBe(true); expect(i.mx).toBe(100); expect(i.usingTouch).toBe(true);
    (i as any).handleTouchEnd(te([t(1, 100, 120)]));
    expect(click).toEqual([100, 120]); expect(i.leftDown).toBe(false);
  });

  it("a one-finger drag maps to a drag-box release (no click)", () => {
    const i = new Input(fakeEl() as any);
    let click = false, drag: any = null;
    i.onLeftClick = () => { click = true; };
    i.onDragEnd = (b) => { drag = b; };
    (i as any).handleTouchStart(te([t(1, 50, 50)]));
    (i as any).handleTouchMove(te([t(1, 160, 60)]));
    expect(i.drag.active).toBe(true);
    (i as any).handleTouchEnd(te([t(1, 160, 60)]));
    expect(drag.x0).toBe(50); expect(drag.x1).toBe(160);
    expect(click).toBe(false);
  });

  it("two fingers pinch (zoom) and pan", () => {
    const i = new Input(fakeEl() as any);
    let pinch: number | null = null, panned = false, clicked = false;
    i.onPinch = (_cx, _cy, f) => { pinch = f; };
    i.onPan = () => { panned = true; };
    i.onLeftClick = () => { clicked = true; };
    (i as any).handleTouchStart(te([t(1, 100, 100)]));
    (i as any).handleTouchStart(te([t(2, 200, 100)])); // now pinching, base dist 100
    (i as any).handleTouchMove(te([t(1, 80, 100), t(2, 220, 100)])); // dist 140 → factor 1.4
    expect(pinch).toBeCloseTo(1.4, 1);
    expect(panned).toBe(true);
    (i as any).handleTouchEnd(te([t(1, 80, 100), t(2, 220, 100)]));
    expect(clicked).toBe(false); // a pinch never fires a click
  });

  it("long-press fires a right click only when enabled (match)", () => {
    vi.useFakeTimers();
    try {
      const i = new Input(fakeEl() as any);
      let right: number[] | null = null;
      i.onRightClick = (x, y) => { right = [x, y]; };
      i.longPressRight = true;
      (i as any).handleTouchStart(te([t(1, 70, 80)]));
      vi.advanceTimersByTime(500);
      expect(right).toEqual([70, 80]);
    } finally {
      vi.useRealTimers();
    }
  });
});
