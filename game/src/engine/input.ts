// Mouse + keyboard input tracking. The game polls this each frame and also
// subscribes to discrete events (click, drag end, wheel) via callbacks.

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
  keys = new Set<string>();
  wheelDelta = 0;

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

  private lastClickTime = 0;
  private el: HTMLElement;

  constructor(el: HTMLElement) {
    this.el = el;
    el.addEventListener("mousemove", this.handleMove);
    el.addEventListener("mousedown", this.handleDown);
    window.addEventListener("mouseup", this.handleUp);
    el.addEventListener("wheel", this.handleWheel, { passive: false });
    el.addEventListener("contextmenu", (e) => e.preventDefault());
    window.addEventListener("keydown", this.handleKeyDown);
    window.addEventListener("keyup", this.handleKeyUp);
    window.addEventListener("blur", () => {
      this.keys.clear();
      this.leftDown = this.rightDown = this.middleDown = false;
      this.drag.active = false;
    });
  }

  private rel(e: MouseEvent): [number, number] {
    const r = this.el.getBoundingClientRect();
    return [e.clientX - r.left, e.clientY - r.top];
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
    const k = e.key.length === 1 ? e.key.toLowerCase() : e.key;
    this.keys.add(k);
    this.onKeyDown?.(e.key);
  };

  private handleKeyUp = (e: KeyboardEvent) => {
    this.shift = e.shiftKey;
    this.ctrl = e.ctrlKey || e.metaKey;
    const k = e.key.length === 1 ? e.key.toLowerCase() : e.key;
    this.keys.delete(k);
  };

  isDown(key: string): boolean {
    return this.keys.has(key);
  }
}
