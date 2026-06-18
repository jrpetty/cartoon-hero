// Consolidated settings: audio, gameplay/accessibility toggles, and a read-only
// controls reference. Edits the Settings object in place; the App applies it live
// each frame and persists on exit. Reachable from the main menu and the pause
// menu.

import { ui } from "./ui";
import { PAL } from "../render/palette";
import { Settings } from "../meta/settings";

const SCROLL_MIN = 240;
const SCROLL_MAX = 960;

const CONTROLS: [string, string][] = [
  ["Left-click / drag", "Select units"],
  ["Right-click", "Move · attack · gather · build"],
  ["Shift", "Queue order · add to selection"],
  ["A", "Attack-move"],
  ["S / H", "Stop · Hold position"],
  ["Q", "Use ability"],
  ["B / G", "Build menu · Garrison"],
  ["C", "Commander power"],
  [". / ,", "Idle villager · Select army"],
  ["0–9", "Control groups (Ctrl+# sets)"],
  ["WASD / Arrows / edge", "Pan camera"],
  ["Mouse wheel", "Zoom"],
  ["P / Space", "Pause"],
  ["+ / −", "Game speed"],
  ["Esc", "Menu"],
];

export class SettingsScreen {
  draw(W: number, H: number, time: number, s: Settings, mouseDown: boolean): "back" | null {
    const ctx = ui.ctx;
    // Backdrop.
    ctx.fillStyle = "#171208";
    ctx.fillRect(0, 0, W, H);
    ui.text("Settings", W / 2, 64, { align: "center", size: 34, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });

    const colW = 380;
    const gap = 40;
    const totalW = colW * 2 + gap;
    const x0 = Math.round(W / 2 - totalW / 2);
    const xR = x0 + colW + gap;
    const top = 120;
    const drag = ui.clicked || mouseDown;

    // ----- left column: Audio + Gameplay -----
    ui.panel(x0, top, colW, 470, { light: true });
    let y = top + 24;
    const lx = x0 + 22;
    const sx = x0 + 150;
    const sw = colW - 172;

    ui.text("Audio", lx, y, { size: 15, bold: true, color: PAL.uiAccent });
    y += 30;
    const vol = (label: string, val: number): number => {
      ui.text(label, lx, y + 4, { size: 13 });
      ui.text(`${Math.round(val * 100)}%`, x0 + colW - 22, y + 4, { size: 12, align: "right", color: "#bdb49a" });
      const nv = ui.slider(sx, y, sw - 36, val, drag);
      y += 30;
      return nv;
    };
    s.masterVol = vol("Master", s.masterVol);
    s.sfxVol = vol("Effects", s.sfxVol);
    s.musicVol = vol("Music", s.musicVol);
    s.muted = this.toggle(lx, y, colW - 44, "Mute all sound", s.muted); y += 40;

    y += 10;
    ui.text("Gameplay & Accessibility", lx, y, { size: 15, bold: true, color: PAL.uiAccent });
    y += 30;
    s.damageNumbers = this.toggle(lx, y, colW - 44, "Floating damage numbers", s.damageNumbers); y += 40;
    s.edgeScroll = this.toggle(lx, y, colW - 44, "Edge-scroll camera", s.edgeScroll); y += 40;
    // Camera scroll speed.
    {
      const norm = (s.scrollSpeed - SCROLL_MIN) / (SCROLL_MAX - SCROLL_MIN);
      ui.text("Scroll speed", lx, y + 4, { size: 13 });
      ui.text(`${Math.round(s.scrollSpeed)}`, x0 + colW - 22, y + 4, { size: 12, align: "right", color: "#bdb49a" });
      const nv = ui.slider(sx, y, sw - 36, Math.max(0, Math.min(1, norm)), drag);
      s.scrollSpeed = Math.round(SCROLL_MIN + nv * (SCROLL_MAX - SCROLL_MIN));
      y += 34;
    }
    s.reduceEffects = this.toggle(lx, y, colW - 44, "Reduce effects (faster 8v8)", s.reduceEffects); y += 40;
    s.colorblind = this.toggle(lx, y, colW - 44, "Colorblind team colors", s.colorblind); y += 40;

    // ----- right column: Controls reference -----
    ui.panel(xR, top, colW, 470, { light: true });
    ui.text("Controls", xR + 22, top + 24, { size: 15, bold: true, color: PAL.uiAccent });
    let cy = top + 54;
    for (const [key, act] of CONTROLS) {
      ui.text(key, xR + 22, cy, { size: 12.5, color: "#ffe9b0" });
      ui.text(act, xR + 168, cy, { size: 12.5, color: "#cabfa4" });
      cy += 27;
    }

    // ----- back -----
    let action: "back" | null = null;
    if (ui.button("⟵ Back", x0, top + 490, 140, 44, { size: 15 })) action = "back";
    ui.text("Settings save automatically.", x0 + 160, top + 512, { size: 12, color: "#9a917b" });
    return action;
  }

  /** A labelled On/Off switch. Returns the (possibly toggled) value. */
  private toggle(x: number, y: number, w: number, label: string, value: boolean): boolean {
    ui.text(label, x, y + 14, { size: 13 });
    const clicked = ui.button(value ? "On" : "Off", x + w - 72, y, 72, 28, { accent: value, size: 13 });
    return clicked ? !value : value;
  }
}
