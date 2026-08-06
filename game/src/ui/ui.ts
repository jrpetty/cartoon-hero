// Tiny immediate-mode canvas UI: panels, buttons, bars, tooltips — all drawn
// in the parchment-and-ink fantasy style so HUD and menus feel like one piece.

import { PAL, shade, withAlpha } from "../render/palette";

export interface UIFrameInput {
  mx: number;
  my: number;
  clicked: boolean; // left release this frame
  rightClicked: boolean;
  alt?: boolean; // Alt held this frame (used for minimap pings)
  // Held state and the wheel, for screens that drag and zoom (the map editor).
  leftHeld?: boolean;
  rightHeld?: boolean;
  ctrlHeld?: boolean;
  shiftHeld?: boolean;
  wheel?: number; // scroll delta this frame, 0 when still
}

export class UI {
  ctx!: CanvasRenderingContext2D;
  mx = 0;
  my = 0;
  clicked = false;
  rightClicked = false;
  alt = false;
  leftHeld = false;
  rightHeld = false;
  ctrlHeld = false;
  shiftHeld = false;
  wheel = 0;
  /** Set true by any widget that consumed the pointer this frame. */
  pointerConsumed = false;
  hoveredTooltip: { text: string[]; x: number; y: number } | null = null;
  /** Interface scale currently in force. 1 = canvas pixels. */
  scale = 1;
  private scaleStack: number[] = [];

  /**
   * Draw the next block of UI at `s`× size. Widgets lay out in a *smaller*
   * space (pass W/s, H/s) and the transform blows them up, so a scaled HUD is
   * genuinely larger rather than a stretched bitmap. Pointer coordinates are
   * divided to match, which is what keeps hit-testing honest — every widget
   * tests `ui.mx` against its own layout coordinates and neither knows nor
   * cares that a transform is in force.
   */
  pushScale(s: number) {
    this.ctx.save();
    this.ctx.scale(s, s);
    this.scaleStack.push(this.scale);
    this.mx /= s;
    this.my /= s;
    this.scale = s;
  }

  popScale() {
    const prev = this.scaleStack.pop() ?? 1;
    this.mx *= this.scale;
    this.my *= this.scale;
    this.scale = prev;
    this.ctx.restore();
  }

  begin(ctx: CanvasRenderingContext2D, input: UIFrameInput) {
    this.ctx = ctx;
    this.mx = input.mx;
    this.my = input.my;
    this.clicked = input.clicked;
    this.rightClicked = input.rightClicked;
    this.alt = !!input.alt;
    this.leftHeld = !!input.leftHeld;
    this.rightHeld = !!input.rightHeld;
    this.ctrlHeld = !!input.ctrlHeld;
    this.shiftHeld = !!input.shiftHeld;
    this.wheel = input.wheel ?? 0;
    this.pointerConsumed = false;
    this.hoveredTooltip = null;
    this.scale = 1;
    this.scaleStack.length = 0;
  }

  hit(x: number, y: number, w: number, h: number): boolean {
    return this.mx >= x && this.mx <= x + w && this.my >= y && this.my <= y + h;
  }

  /** Mark a rect as consuming the pointer (e.g. HUD panels over the world). */
  blockPointer(x: number, y: number, w: number, h: number) {
    if (this.hit(x, y, w, h)) this.pointerConsumed = true;
  }

  panel(x: number, y: number, w: number, h: number, opts: { light?: boolean } = {}) {
    const { ctx } = this;
    ctx.fillStyle = opts.light ? PAL.uiPanelLight : PAL.uiPanel;
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, 6);
    ctx.fill();
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.55);
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.roundRect(x + 1, y + 1, w - 2, h - 2, 5);
    ctx.stroke();
    this.blockPointer(x, y, w, h);
  }

  text(
    s: string,
    x: number,
    y: number,
    opts: { size?: number; color?: string; align?: CanvasTextAlign; bold?: boolean; font?: string } = {},
  ) {
    const { ctx } = this;
    ctx.font = `${opts.bold ? "bold " : ""}${opts.size ?? 14}px ${opts.font ?? '"Trebuchet MS", sans-serif'}`;
    ctx.fillStyle = opts.color ?? PAL.uiParchment;
    ctx.textAlign = opts.align ?? "left";
    ctx.textBaseline = "middle";
    ctx.fillText(s, x, y);
    ctx.textAlign = "left";
  }

  button(
    label: string,
    x: number,
    y: number,
    w: number,
    h: number,
    opts: {
      disabled?: boolean;
      accent?: boolean;
      danger?: boolean;
      size?: number;
      tooltip?: string[];
      badge?: string;
    } = {},
  ): boolean {
    const { ctx } = this;
    const over = this.hit(x, y, w, h);
    const hover = over && !opts.disabled;
    if (over) this.pointerConsumed = true;

    const base = opts.danger ? "#5a2320" : opts.accent ? "#5a4520" : "#3a3226";
    const fill = opts.disabled ? "#2a251d" : hover ? shade(base, 0.18) : base;
    ctx.fillStyle = fill;
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, 5);
    ctx.fill();
    ctx.strokeStyle = opts.disabled
      ? withAlpha("#888070", 0.3)
      : hover
        ? PAL.uiAccent
        : withAlpha(PAL.uiAccent, 0.5);
    ctx.lineWidth = hover ? 2 : 1.2;
    ctx.beginPath();
    ctx.roundRect(x + 1, y + 1, w - 2, h - 2, 4);
    ctx.stroke();

    this.text(label, x + w / 2, y + h / 2 + 1, {
      align: "center",
      size: opts.size ?? 14,
      color: opts.disabled ? "#7a7264" : PAL.uiParchment,
      bold: true,
    });

    if (opts.badge) {
      ctx.fillStyle = PAL.uiAccent;
      ctx.beginPath();
      ctx.roundRect(x + w - 22, y - 6, 24, 16, 8);
      ctx.fill();
      this.text(opts.badge, x + w - 10, y + 2, { align: "center", size: 11, color: "#1c150c", bold: true });
    }

    // Show the tooltip even when disabled — so you can read an item's cost and
    // requirements before you can afford or unlock it.
    if (over && opts.tooltip && opts.tooltip.length) {
      this.hoveredTooltip = { text: opts.tooltip, x: this.mx, y };
    }
    return hover && this.clicked && !opts.disabled;
  }

  bar(
    x: number,
    y: number,
    w: number,
    h: number,
    frac: number,
    color: string,
    bg = "rgba(0,0,0,0.55)",
  ) {
    const { ctx } = this;
    ctx.fillStyle = bg;
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, h / 2);
    ctx.fill();
    if (frac > 0.01) {
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.roundRect(x + 1, y + 1, Math.max(h - 2, (w - 2) * Math.min(1, frac)), h - 2, (h - 2) / 2);
      ctx.fill();
    }
  }

  /** Draw the queued tooltip last so it sits above everything. */
  flushTooltip(canvasW: number, canvasH: number) {
    if (!this.hoveredTooltip) return;
    const { ctx } = this;
    const lines = this.hoveredTooltip.text;
    ctx.font = "13px 'Trebuchet MS', sans-serif";
    let maxW = 0;
    for (const l of lines) maxW = Math.max(maxW, ctx.measureText(l).width);
    const w = maxW + 20;
    const h = lines.length * 18 + 14;
    let x = Math.min(this.hoveredTooltip.x + 14, canvasW - w - 8);
    let y = this.hoveredTooltip.y - h - 10;
    if (y < 8) y = this.hoveredTooltip.y + 26;
    ctx.fillStyle = "rgba(16, 12, 6, 0.96)";
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, 5);
    ctx.fill();
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.7);
    ctx.lineWidth = 1;
    ctx.stroke();
    for (let i = 0; i < lines.length; i++) {
      this.text(lines[i], x + 10, y + 16 + i * 18, {
        size: 13,
        color: i === 0 ? PAL.uiAccent : PAL.uiParchment,
        bold: i === 0,
      });
    }
  }

  /** A slider; returns the (possibly updated) value while dragging. */
  slider(x: number, y: number, w: number, value: number, dragging: boolean): number {
    const { ctx } = this;
    this.bar(x, y - 3, w, 6, value, PAL.uiAccent);
    const hx = x + value * w;
    ctx.fillStyle = PAL.uiParchment;
    ctx.beginPath();
    ctx.arc(hx, y, 7, 0, Math.PI * 2);
    ctx.fill();
    if (this.hit(x - 8, y - 12, w + 16, 24)) {
      this.pointerConsumed = true;
      if (dragging) {
        return Math.max(0, Math.min(1, (this.mx - x) / w));
      }
    }
    return value;
  }
}

export const ui = new UI();
