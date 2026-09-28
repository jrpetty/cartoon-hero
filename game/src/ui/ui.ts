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
  private scrollStack: number[] = [];
  /**
   * Rects, in canvas pixels, that must act inside the DOM event rather than a
   * frame later. Rebuilt every frame by whatever is drawn; read by the click
   * handler between frames, so it always reflects what the player was looking
   * at when they clicked. See engine/fullscreen.ts for why this exists.
   */
  gestureZones: { id: string; x: number; y: number; w: number; h: number }[] = [];

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

  /**
   * Draw the next block of UI scrolled up by `dy` and clipped to `clip`.
   *
   * The same bargain as `pushScale`: the context is translated and the pointer
   * is moved to match, so every widget keeps testing `ui.my` against its own
   * layout coordinates and never learns a scroll is in force.
   *
   * A screen that lays its panels out by accumulating `y` downward has no idea
   * how tall it will end up, and on a short window the things at the bottom
   * simply leave the viewport — which is how the Skirmish setup screen ended up
   * with its "To Battle!" button below the fold and unclickable.
   */
  pushScroll(dy: number, clip?: { x: number; y: number; w: number; h: number }) {
    this.ctx.save();
    if (clip) {
      this.ctx.beginPath();
      this.ctx.rect(clip.x, clip.y, clip.w, clip.h);
      this.ctx.clip();
    }
    this.ctx.translate(0, -dy);
    this.scrollStack.push(dy);
    this.my += dy;
  }

  popScroll() {
    const dy = this.scrollStack.pop() ?? 0;
    this.my -= dy;
    this.ctx.restore();
  }

  /**
   * A slim indicator that there is more above or below. Drawn outside the
   * scrolled block, in screen coordinates.
   */
  scrollbar(x: number, y: number, h: number, offset: number, contentH: number) {
    if (contentH <= h) return;
    const { ctx } = this;
    const frac = h / contentH;
    const thumbH = Math.max(24, h * frac);
    const t = y + (h - thumbH) * (offset / Math.max(1, contentH - h));
    ctx.fillStyle = "rgba(255,255,255,0.06)";
    ctx.beginPath(); ctx.roundRect(x, y, 5, h, 2.5); ctx.fill();
    ctx.fillStyle = "rgba(255,233,176,0.45)";
    ctx.beginPath(); ctx.roundRect(x, t, 5, thumbH, 2.5); ctx.fill();
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
    this.scrollStack.length = 0;
    this.gestureZones.length = 0;
  }

  /**
   * Record that this layout rect should act on the raw click. Converted to
   * canvas pixels through the live transform, so it stays exact under the UI
   * scale slider and inside a scrolled panel without either knowing about it.
   */
  registerGestureZone(id: string, x: number, y: number, w: number, h: number) {
    const m = this.ctx.getTransform();
    const x0 = m.a * x + m.c * y + m.e;
    const y0 = m.b * x + m.d * y + m.f;
    const x1 = m.a * (x + w) + m.c * (y + h) + m.e;
    const y1 = m.b * (x + w) + m.d * (y + h) + m.f;
    this.gestureZones.push({
      id, x: Math.min(x0, x1), y: Math.min(y0, y1), w: Math.abs(x1 - x0), h: Math.abs(y1 - y0),
    });
  }

  /** Which gesture zone, if any, a raw canvas-space click landed in. */
  gestureAt(x: number, y: number): string | null {
    // Last drawn wins, matching what is on top.
    for (let i = this.gestureZones.length - 1; i >= 0; i--) {
      const z = this.gestureZones[i];
      if (x >= z.x && x <= z.x + z.w && y >= z.y && y <= z.y + z.h) return z.id;
    }
    return null;
  }

  hit(x: number, y: number, w: number, h: number): boolean {
    return this.mx >= x && this.mx <= x + w && this.my >= y && this.my <= y + h;
  }

  /** Mark a rect as consuming the pointer (e.g. HUD panels over the world). */
  blockPointer(x: number, y: number, w: number, h: number) {
    if (this.hit(x, y, w, h)) this.pointerConsumed = true;
  }

  /**
   * A framed panel. Same parchment-and-gold as ever, with some depth: a soft
   * shadow under it, a gradient that is a shade lighter at the top, a dark
   * outer rim, and the gold line inset from it like a picture frame.
   */
  panel(x: number, y: number, w: number, h: number, opts: { light?: boolean } = {}) {
    const { ctx } = this;
    const r = 9;
    // Drop shadow: an offset dark shape rather than a blur, which is costly.
    ctx.fillStyle = "rgba(0,0,0,0.28)";
    ctx.beginPath(); ctx.roundRect(x + 2, y + 4, w, h, r); ctx.fill();
    const g = ctx.createLinearGradient(0, y, 0, y + h);
    if (opts.light) { g.addColorStop(0, "rgba(64,51,32,0.96)"); g.addColorStop(1, "rgba(40,31,19,0.96)"); }
    else { g.addColorStop(0, "rgba(36,28,17,0.94)"); g.addColorStop(1, "rgba(20,15,9,0.94)"); }
    ctx.fillStyle = g;
    ctx.beginPath(); ctx.roundRect(x, y, w, h, r); ctx.fill();
    ctx.strokeStyle = "rgba(0,0,0,0.55)";
    ctx.lineWidth = 1.5;
    ctx.beginPath(); ctx.roundRect(x + 0.75, y + 0.75, w - 1.5, h - 1.5, r); ctx.stroke();
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.5);
    ctx.lineWidth = 1;
    ctx.beginPath(); ctx.roundRect(x + 3.5, y + 3.5, w - 7, h - 7, r - 3); ctx.stroke();
    // A faint light along the top edge.
    ctx.strokeStyle = "rgba(255,236,190,0.08)";
    ctx.beginPath(); ctx.moveTo(x + r, y + 5); ctx.lineTo(x + w - r, y + 5); ctx.stroke();
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

    // Gold-on-dark as ever, drawn with some depth: a gradient body lighter at
    // the top, a highlight along the upper edge, a gold rim that brightens on
    // hover, and a press that sinks it a pixel.
    const pressed = hover && this.leftHeld;
    const base = opts.danger ? "#5e2622" : opts.accent ? "#6a5024" : "#3c3427";
    const top = opts.disabled ? "#2c271f" : shade(base, hover ? 0.3 : 0.14);
    const bot = opts.disabled ? "#221e18" : shade(base, hover ? 0.02 : -0.12);
    const oy = pressed ? 1 : 0;
    const rad = Math.min(7, h / 2.6);
    ctx.fillStyle = "rgba(0,0,0,0.3)";
    ctx.beginPath(); ctx.roundRect(x, y + 2, w, h, rad); ctx.fill();
    const g = ctx.createLinearGradient(0, y + oy, 0, y + oy + h);
    g.addColorStop(0, pressed ? bot : top);
    g.addColorStop(1, pressed ? top : bot);
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.roundRect(x, y + oy, w, h, rad);
    ctx.fill();
    if (!opts.disabled && !pressed) {
      ctx.strokeStyle = "rgba(255,240,205,0.13)";
      ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(x + rad, y + 1.5); ctx.lineTo(x + w - rad, y + 1.5); ctx.stroke();
    }
    ctx.strokeStyle = opts.disabled
      ? withAlpha("#888070", 0.28)
      : hover
        ? (opts.danger ? "#e0786a" : "#e2b24e")
        : withAlpha(PAL.uiAccent, opts.accent ? 0.85 : 0.5);
    ctx.lineWidth = hover || opts.accent ? 1.6 : 1.1;
    ctx.beginPath();
    ctx.roundRect(x + 0.8, y + oy + 0.8, w - 1.6, h - 1.6, rad - 0.5);
    ctx.stroke();

    // A soft shadow under the label keeps it readable on any fill.
    const lsize = opts.size ?? 14;
    if (!opts.disabled) {
      this.text(label, x + w / 2, y + h / 2 + 2 + oy, { align: "center", size: lsize, color: "rgba(0,0,0,0.45)", bold: true });
    }
    this.text(label, x + w / 2, y + h / 2 + 1 + oy, {
      align: "center",
      size: lsize,
      color: opts.disabled ? "#7a7264" : opts.accent ? "#fff0cc" : PAL.uiParchment,
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

  /** Queue a tooltip for whatever is under the pointer (call when hovering). */
  tooltip(text: string[]) {
    if (text.length) this.hoveredTooltip = { text, x: this.mx, y: this.my };
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
