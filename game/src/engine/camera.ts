import { clamp } from "./math";

/** Converts between world space and screen space, with pan + zoom. */
export class Camera {
  x = 0; // world coord at screen center
  y = 0;
  zoom = 1;
  minZoom = 0.4;
  maxZoom = 1.8;
  viewW = 1280;
  viewH = 720;
  worldW = 4000;
  worldH = 4000;

  setViewport(w: number, h: number) {
    this.viewW = w;
    this.viewH = h;
    this.clampToWorld();
  }

  setWorld(w: number, h: number) {
    this.worldW = w;
    this.worldH = h;
  }

  worldToScreenX(wx: number): number {
    return (wx - this.x) * this.zoom + this.viewW / 2;
  }
  worldToScreenY(wy: number): number {
    return (wy - this.y) * this.zoom + this.viewH / 2;
  }
  screenToWorldX(sx: number): number {
    return (sx - this.viewW / 2) / this.zoom + this.x;
  }
  screenToWorldY(sy: number): number {
    return (sy - this.viewH / 2) / this.zoom + this.y;
  }

  pan(dxWorld: number, dyWorld: number) {
    this.x += dxWorld;
    this.y += dyWorld;
    this.clampToWorld();
  }

  /** Zoom toward a screen anchor point so the world point under the cursor stays put. */
  zoomAt(sx: number, sy: number, factor: number) {
    const wx = this.screenToWorldX(sx);
    const wy = this.screenToWorldY(sy);
    this.zoom = clamp(this.zoom * factor, this.minZoom, this.maxZoom);
    // Re-center so (wx,wy) stays under (sx,sy)
    this.x = wx - (sx - this.viewW / 2) / this.zoom;
    this.y = wy - (sy - this.viewH / 2) / this.zoom;
    this.clampToWorld();
  }

  centerOn(wx: number, wy: number) {
    this.x = wx;
    this.y = wy;
    this.clampToWorld();
  }

  private clampToWorld() {
    // Allow a little overscroll margin so edges feel natural.
    const margin = 200;
    this.x = clamp(this.x, -margin, this.worldW + margin);
    this.y = clamp(this.y, -margin, this.worldH + margin);
  }
}
