// Mouse + keyboard + touch input tracking. The game polls this each frame and
// also subscribes to discrete events (click, drag end, wheel, pan, pinch) via
// callbacks. Touch maps one finger to the left mouse (tap = click, drag = box /
// UI drag), two fingers to camera pan + pinch-zoom, and a long-press to a
// right-click (contextual command) while in a match.

export interface DragBox {
  active: boolean;
  x0: number;
  y0: number;
  x1: number;
  y1: number;
}

export class Input {
  mx = 0; // screen mouse position
  my = 0;
  leftDown = false;
  rightDown = false;
  middleDown = false;
  shift = false;
  ctrl = false;
  alt = false;
  keys = new Set<string>();
  wheelDelta = 0;
  usingTouch = false; // true once the player has used touch (suppresses edge-scroll)
  longPressRight = false; // set by the game: enable long-press → right-click (match only)

  drag: DragBox = { active: false, x0: 0, y0: 0, x1: 0, y1: 0 };
  private dragStartX = 0;
  private dragStartY = 0;
  private leftPressTime = 0;
  private readonly DRAG_THRESHOLD = 6;

  // Callbacks set by the game layer.
  onLeftClick: ((sx: number, sy: number) => void) | null = null;
  onLeftDouble: ((sx: number, sy: number) => void) | null = null;
  onDragEnd: ((box: DragBox) => void) | null = null;
  onRightClick: ((sx: number, sy: number) => void) | null = null;
  onWheel: ((sx: number, sy: number, delta: number) => void) | null = null;
  onKeyDown: ((key: string) => void) | null = null;
  onPan: ((dxScreen: number, dyScreen: number) => void) | null = null; // two-finger drag
  onPinch: ((cx: number, cy: number, factor: number) => void) | null = null; // pinch zoom

  // Touch state.
  private touches = new Map<number, { x: number; y: number }>();
  private pinching = false;
  private lastPinchDist = 0;
  private lastPinchMid: [number, number] = [0, 0];
  private longPressTimer: ReturnType<typeof setTimeout> | null = null;
  private longPressed = false;

  private lastClickTime = 0;
  private el: HTMLElement;

  constructor(el: HTMLElement) {
    this.el = el;
    el.addEventListener("mousemove", this.handleMove);
    el.addEventListener("mousedown", this.handleDown);
    window.addEventListener("mouseup", this.handleUp);
    el.addEventListener("wheel", this.handleWheel, { passive: false });
    el.addEventListener("contextmenu", (e) => e.preventDefault());
    el.addEventListener("touchstart", this.handleTouchStart, { passive: false });
    el.addEventListener("touchmove", this.handleTouchMove, { passive: false });
    window.addEventListener("touchend", this.handleTouchEnd, { passive: false });
    window.addEventListener("touchcancel", this.handleTouchEnd, { passive: false });
    window.addEventListener("keydown", this.handleKeyDown);
    window.addEventListener("keyup", this.handleKeyUp);
    window.addEventListener("blur", () => {
      this.keys.clear();
      this.leftDown = this.rightDown = this.middleDown = false;
      this.drag.active = false;
      this.touches.clear();
      this.pinching = false;
      this.clearLongPress();
    });
  }

  /** Map a client point to the canvas's internal (virtual) coordinate space —
   *  the canvas may be CSS-scaled to fit a small/mobile screen. */
  private toCanvas(clientX: number, clientY: number): [number, number] {
    const r = this.el.getBoundingClientRect();
    const cw = (this.el as HTMLCanvasElement).width || r.width || 1;
    const chh = (this.el as HTMLCanvasElement).height || r.height || 1;
    const sx = r.width ? cw / r.width : 1;
    const sy = r.height ? chh / r.height : 1;
    return [(clientX - r.left) * sx, (clientY - r.top) * sy];
  }

  private rel(e: MouseEvent): [number, number] {
    return this.toCanvas(e.clientX, e.clientY);
  }

  private handleMove = (e: MouseEvent) => {
    const [x, y] = this.rel(e);
    this.mx = x;
    this.my = y;
    if (this.leftDown) {
      const d = Math.hypot(x - this.dragStartX, y - this.dragStartY);
      if (d > this.DRAG_THRESHOLD) this.drag.active = true;
      if (this.drag.active) {
        this.drag.x1 = x;
        this.drag.y1 = y;
      }
    }
  };

  private handleDown = (e: MouseEvent) => {
    const [x, y] = this.rel(e);
    this.mx = x;
    this.my = y;
    this.shift = e.shiftKey;
    this.ctrl = e.ctrlKey || e.metaKey;
    if (e.button === 0) {
      this.leftDown = true;
      this.dragStartX = x;
      this.dragStartY = y;
      this.drag = { active: false, x0: x, y0: y, x1: x, y1: y };
      this.leftPressTime = performance.now();
    } else if (e.button === 2) {
      this.rightDown = true;
    } else if (e.button === 1) {
      this.middleDown = true;
      e.preventDefault();
    }
  };

  private handleUp = (e: MouseEvent) => {
    if (e.button === 0) {
      this.leftDown = false;
      if (this.drag.active) {
        this.onDragEnd?.({ ...this.drag });
      } else {
        const now = performance.now();
        if (now - this.lastClickTime < 280) {
          this.onLeftDouble?.(this.mx, this.my);
        } else {
          this.onLeftClick?.(this.mx, this.my);
        }
        this.lastClickTime = now;
      }
      this.drag.active = false;
    } else if (e.button === 2) {
      this.rightDown = false;
      this.onRightClick?.(this.mx, this.my);
    } else if (e.button === 1) {
      this.middleDown = false;
    }
  };

  private handleWheel = (e: WheelEvent) => {
    e.preventDefault();
    this.onWheel?.(this.mx, this.my, e.deltaY);
  };

  private handleKeyDown = (e: KeyboardEvent) => {
    this.shift = e.shiftKey;
    this.ctrl = e.ctrlKey || e.metaKey;
    this.alt = e.altKey;
    // Browsers reserve Ctrl+digit for tab switching; Alt+digit is our
    // control-group fallback, so keep the browser out of it.
    if (e.altKey && /^[0-9]$/.test(e.key)) e.preventDefault();
    if (e.key === "Tab") e.preventDefault(); // Tab = scoreboard, not focus-cycle
    const k = e.key.length === 1 ? e.key.toLowerCase() : e.key;
    this.keys.add(k);
    this.onKeyDown?.(e.key);
  };

  private handleKeyUp = (e: KeyboardEvent) => {
    this.shift = e.shiftKey;
    this.ctrl = e.ctrlKey || e.metaKey;
    this.alt = e.altKey;
    const k = e.key.length === 1 ? e.key.toLowerCase() : e.key;
    this.keys.delete(k);
  };

  // ----------------------------------------------------------------- touch --

  private relT(t: Touch): [number, number] {
    return this.toCanvas(t.clientX, t.clientY);
  }

  private clearLongPress() {
    if (this.longPressTimer != null) { clearTimeout(this.longPressTimer); this.longPressTimer = null; }
  }

  private armLongPress(x: number, y: number) {
    this.clearLongPress();
    if (!this.longPressRight) return;
    this.longPressTimer = setTimeout(() => {
      this.longPressTimer = null;
      if (this.touches.size === 1 && !this.drag.active && !this.pinching) {
        this.longPressed = true;
        this.onRightClick?.(x, y);
      }
    }, 450);
  }

  private handleTouchStart = (e: TouchEvent) => {
    e.preventDefault();
    this.usingTouch = true;
    for (const t of Array.from(e.changedTouches)) { const [x, y] = this.relT(t); this.touches.set(t.identifier, { x, y }); }
    if (this.touches.size === 1) {
      const c = [...this.touches.values()][0];
      this.mx = c.x; this.my = c.y;
      this.leftDown = true;
      this.dragStartX = c.x; this.dragStartY = c.y;
      this.drag = { active: false, x0: c.x, y0: c.y, x1: c.x, y1: c.y };
      this.leftPressTime = performance.now();
      this.longPressed = false;
      this.armLongPress(c.x, c.y);
    } else if (this.touches.size >= 2) {
      // Two fingers → pinch/pan; abandon any single-finger gesture.
      this.leftDown = false; this.drag.active = false; this.clearLongPress();
      const pts = [...this.touches.values()].slice(0, 2);
      this.pinching = true;
      this.lastPinchDist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
      this.lastPinchMid = [(pts[0].x + pts[1].x) / 2, (pts[0].y + pts[1].y) / 2];
    }
  };

  private handleTouchMove = (e: TouchEvent) => {
    e.preventDefault();
    for (const t of Array.from(e.changedTouches)) { const c = this.touches.get(t.identifier); if (c) { const [x, y] = this.relT(t); c.x = x; c.y = y; } }
    if (this.touches.size === 1 && this.leftDown && !this.pinching) {
      const c = [...this.touches.values()][0];
      this.mx = c.x; this.my = c.y;
      const d = Math.hypot(c.x - this.dragStartX, c.y - this.dragStartY);
      if (d > this.DRAG_THRESHOLD) { this.drag.active = true; this.clearLongPress(); }
      if (this.drag.active) { this.drag.x1 = c.x; this.drag.y1 = c.y; }
    } else if (this.touches.size >= 2) {
      const pts = [...this.touches.values()].slice(0, 2);
      const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
      const mid: [number, number] = [(pts[0].x + pts[1].x) / 2, (pts[0].y + pts[1].y) / 2];
      this.mx = mid[0]; this.my = mid[1];
      if (this.lastPinchDist > 0) {
        const factor = dist / this.lastPinchDist;
        if (Math.abs(factor - 1) > 0.002) this.onPinch?.(mid[0], mid[1], factor);
        this.onPan?.(mid[0] - this.lastPinchMid[0], mid[1] - this.lastPinchMid[1]);
      }
      this.lastPinchDist = dist; this.lastPinchMid = mid;
    }
  };

  private handleTouchEnd = (e: TouchEvent) => {
    e.preventDefault();
    for (const t of Array.from(e.changedTouches)) this.touches.delete(t.identifier);
    if (this.touches.size === 0) {
      this.clearLongPress();
      if (this.pinching) {
        this.pinching = false; this.lastPinchDist = 0;
      } else if (this.leftDown) {
        if (this.drag.active) {
          this.onDragEnd?.({ ...this.drag });
        } else if (!this.longPressed) {
          const now = performance.now();
          if (now - this.lastClickTime < 280) this.onLeftDouble?.(this.mx, this.my);
          else this.onLeftClick?.(this.mx, this.my);
          this.lastClickTime = now;
        }
      }
      this.leftDown = false; this.drag.active = false; this.longPressed = false;
    } else if (this.pinching) {
      // A finger lifted mid-pinch — stay neutral until all fingers are up.
      this.leftDown = false; this.lastPinchDist = 0;
    }
  };

  isDown(key: string): boolean {
    return this.keys.has(key);
  }
}
