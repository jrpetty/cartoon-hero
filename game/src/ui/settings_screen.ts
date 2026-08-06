// Consolidated settings, in two tabs: Game (audio, accessibility, performance)
// and Controls (every hotkey, rebindable). Edits the Settings object in place;
// the App applies it live each frame and persists on exit. Reachable from the
// main menu and the pause menu.

import { ui } from "./ui";
import { PAL, withAlpha } from "../render/palette";
import { Settings } from "../meta/settings";
import {
  ACTIONS, ActionId, KEYBIND_GROUPS, chordFor, chordLabel, chordOf,
  conflictsOf, isReserved,
} from "../meta/keybinds";

const SCROLL_MIN = 240;
const SCROLL_MAX = 960;
const UI_MIN = 0.8;
const UI_MAX = 1.5;

// Row pitches. Panel heights are derived from these rather than hard-coded, so
// adding a setting or a control line can't silently overflow its frame.
const ROW = 40;       // toggle row
const SLIDER = 30;    // slider row
const HEAD = 30;      // section heading
const BIND_ROW = 30;  // one rebindable action
const PAD = 16;       // breathing room at the panel's bottom edge

/** Things the keyboard can do that aren't rebindable actions. */
const FIXED_CONTROLS: [string, string][] = [
  ["Left-click / drag", "Select"],
  ["Right-click", "Move · attack · gather · build"],
  ["Shift", "Queue order · add to selection"],
  ["1 – 9", "Select control group (twice: go there)"],
  ["Ctrl + 1 – 9", "Assign control group"],
  ["Shift + 1 – 9", "Add selection to group"],
  ["Alt-click minimap", "Ping"],
  ["Mouse wheel", "Zoom"],
  ["Screen edge", "Pan camera"],
];

export class SettingsScreen {
  private tab: "game" | "controls" = "game";
  /** The action currently listening for a new chord, if any. */
  private listening: ActionId | null = null;

  /**
   * Called by the app before its own hotkey handling. While an action is
   * listening, every key belongs to the rebind — otherwise binding Escape or
   * Tab would close the screen instead of binding them. Returns true when the
   * key was swallowed.
   */
  captureKey(key: string, mods: { ctrl: boolean; shift: boolean; alt: boolean }): boolean {
    if (!this.listening) return false;
    if (key === "Escape") { this.listening = null; return true; } // cancel
    const chord = chordOf(key, mods);
    if (!chord) return true; // a bare modifier: keep listening
    if (isReserved(chord)) return true; // the browser needs this one
    this.pending = chord;
    return true;
  }

  /** Set by captureKey, consumed on the next draw (which owns the Settings). */
  private pending: string | null = null;

  draw(W: number, H: number, time: number, s: Settings, mouseDown: boolean): "back" | null {
    const ctx = ui.ctx;
    ctx.fillStyle = "#171208";
    ctx.fillRect(0, 0, W, H);
    ui.text("Settings", W / 2, 56, { align: "center", size: 34, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });

    // Apply a rebind captured since the last frame.
    if (this.pending && this.listening) {
      if (this.pending === "Backspace") s.keybinds[this.listening] = ""; // unbind
      else s.keybinds[this.listening] = this.pending;
      this.listening = null;
      this.pending = null;
    }

    const colW = 400;
    const gap = 40;
    const totalW = colW * 2 + gap;
    const x0 = Math.round(W / 2 - totalW / 2);
    const top = 132;
    const drag = ui.clicked || mouseDown;

    // ----- tabs -----
    const tabW = 150;
    const tx = W / 2 - tabW - 4;
    if (ui.button("Game", tx, 84, tabW, 32, { accent: this.tab === "game", size: 14 })) { this.tab = "game"; this.listening = null; }
    if (ui.button("Controls", tx + tabW + 8, 84, tabW, 32, { accent: this.tab === "controls", size: 14 })) this.tab = "controls";

    const panelH = this.tab === "game"
      ? this.drawGameTab(x0, colW, gap, top, s, drag)
      : this.drawControlsTab(x0, colW, gap, top, s, H);

    // ----- back -----
    let action: "back" | null = null;
    const footY = top + panelH + 20;
    if (ui.button("‹  Back", x0, footY, 140, 44, { size: 15 })) { this.listening = null; action = "back"; }
    ui.text("Settings save automatically.", x0 + 160, footY + 22, { size: 12, color: "#9a917b" });
    return action;
  }

  // ------------------------------------------------------------- game tab --

  private drawGameTab(x0: number, colW: number, gap: number, top: number, s: Settings, drag: boolean): number {
    const xR = x0 + colW + gap;
    const leftH = 24 + HEAD + 3 * SLIDER + ROW + 10 + HEAD + 2 * ROW + 34 + 3 * ROW + PAD;
    const rightH = 24 + HEAD + 34 + 4 * ROW + 10 + HEAD + 2 * ROW + 34 + PAD;
    const panelH = Math.max(leftH, rightH);

    // ----- left: audio + gameplay -----
    ui.panel(x0, top, colW, panelH, { light: true });
    let y = top + 24;
    const lx = x0 + 22;
    const sx = x0 + 150;
    const sw = colW - 172;

    ui.text("Audio", lx, y, { size: 15, bold: true, color: PAL.uiAccent });
    y += HEAD;
    const vol = (label: string, val: number): number => {
      ui.text(label, lx, y + 4, { size: 13 });
      ui.text(`${Math.round(val * 100)}%`, x0 + colW - 22, y + 4, { size: 12, align: "right", color: "#bdb49a" });
      const nv = ui.slider(sx, y, sw - 36, val, drag);
      y += SLIDER;
      return nv;
    };
    s.masterVol = vol("Master", s.masterVol);
    s.sfxVol = vol("Effects", s.sfxVol);
    s.musicVol = vol("Music", s.musicVol);
    s.muted = this.toggle(lx, y, colW - 44, "Mute all sound", s.muted); y += ROW;

    y += 10;
    ui.text("Gameplay", lx, y, { size: 15, bold: true, color: PAL.uiAccent });
    y += HEAD;
    s.damageNumbers = this.toggle(lx, y, colW - 44, "Floating damage numbers", s.damageNumbers); y += ROW;
    s.edgeScroll = this.toggle(lx, y, colW - 44, "Edge-scroll camera", s.edgeScroll); y += ROW;
    {
      const norm = (s.scrollSpeed - SCROLL_MIN) / (SCROLL_MAX - SCROLL_MIN);
      ui.text("Scroll speed", lx, y + 4, { size: 13 });
      ui.text(`${Math.round(s.scrollSpeed)}`, x0 + colW - 22, y + 4, { size: 12, align: "right", color: "#bdb49a" });
      const nv = ui.slider(sx, y, sw - 36, Math.max(0, Math.min(1, norm)), drag);
      s.scrollSpeed = Math.round(SCROLL_MIN + nv * (SCROLL_MAX - SCROLL_MIN));
      y += 34;
    }
    s.weather = this.toggle(lx, y, colW - 44, "Weather effects", s.weather); y += ROW;
    s.colorblind = this.toggle(lx, y, colW - 44, "Colorblind team colours", s.colorblind); y += ROW;

    // ----- right: interface + performance -----
    ui.panel(xR, top, colW, panelH, { light: true });
    let ry = top + 24;
    const rx = xR + 22;
    const rsx = xR + 150;

    ui.text("Interface", rx, ry, { size: 15, bold: true, color: PAL.uiAccent });
    ry += HEAD;
    {
      // Scales the HUD and menus only — the world keeps its apparent size, so
      // turning this up doesn't cost you any view of the battlefield.
      const norm = (s.uiScale - UI_MIN) / (UI_MAX - UI_MIN);
      ui.text("Interface scale", rx, ry + 4, { size: 13 });
      ui.text(`${Math.round(s.uiScale * 100)}%`, xR + colW - 22, ry + 4, { size: 12, align: "right", color: "#bdb49a" });
      const nv = ui.slider(rsx, ry, colW - 208, Math.max(0, Math.min(1, norm)), drag);
      // Quarter steps: a slider that lands on 103% is a slider nobody trusts.
      s.uiScale = Math.round((UI_MIN + nv * (UI_MAX - UI_MIN)) * 20) / 20;
      ry += 34;
    }
    ui.text("HUD and menus only — the map keeps its size.", rx, ry - 6, { size: 11, color: "#8a8278" });
    ry += 14;

    y = ry; // keep the toggle helper's local naming consistent
    s.showFps = this.toggle(rx, ry, colW - 44, "FPS counter", s.showFps); ry += ROW;
    s.perfOverlay = this.toggle(rx, ry, colW - 44, "Performance overlay", s.perfOverlay); ry += ROW;
    ui.text("Frame time, sim tick, entity count.", rx, ry - 12, { size: 11, color: "#8a8278" });
    ry += 4;

    ui.text("Performance", rx, ry + 8, { size: 15, bold: true, color: PAL.uiAccent });
    ry += HEAD + 8;
    s.reduceEffects = this.toggle(rx, ry, colW - 44, "Reduce effects", s.reduceEffects); ry += ROW;
    s.autoLod = this.toggle(rx, ry, colW - 44, "Adaptive detail", s.autoLod); ry += ROW;
    ui.text("Simplifies units on its own when frames slip,", rx, ry - 12, { size: 11, color: "#8a8278" });
    ui.text("and restores them when they recover.", rx, ry + 1, { size: 11, color: "#8a8278" });

    return panelH;
  }

  // --------------------------------------------------------- controls tab --

  private drawControlsTab(x0: number, colW: number, gap: number, top: number, s: Settings, H: number): number {
    const xR = x0 + colW + gap;
    const conflicts = conflictsOf(s.keybinds);
    const rowsOf = (g: string) => ACTIONS.filter((a) => a.group === g).length;
    const groupH = (g: string) => HEAD + rowsOf(g) * BIND_ROW + 8;
    const EXTRAS_H = HEAD + FIXED_CONTROLS.length * 22 + 8;
    // Balance the two columns by drawn height, counting the mouse reference as
    // a block of its own so it can't push a column off the bottom of its panel.
    const left: string[] = [], right: string[] = [];
    let lH = 0, rH = EXTRAS_H; // the extras always live in the right column
    for (const g of KEYBIND_GROUPS) {
      if (lH <= rH) { left.push(g); lH += groupH(g); }
      else { right.push(g); rH += groupH(g); }
    }
    const panelH = Math.max(lH, rH) + 24 + PAD;

    const drawGroups = (px: number, groups: string[]): number => {
      ui.panel(px, top, colW, panelH, { light: true });
      let y = top + 24;
      for (const g of groups) {
        ui.text(g, px + 22, y, { size: 15, bold: true, color: PAL.uiAccent });
        y += HEAD;
        for (const a of ACTIONS.filter((x) => x.group === g)) {
          const chord = chordFor(s.keybinds, a.id);
          const clash = chord !== "" && conflicts.has(chord);
          const listening = this.listening === a.id;
          ui.text(a.label, px + 22, y + 13, { size: 12.5, color: a.hint ? "#e7ddc4" : "#e7ddc4" });
          const bw = 116, bx = px + colW - bw - 22;
          const label = listening ? "press a key…" : chordLabel(chord);
          if (ui.button(label, bx, y, bw, 24, {
            accent: listening,
            danger: clash && !listening,
            size: 12,
            tooltip: listening
              ? ["Listening", "Esc cancels · Backspace clears the binding"]
              : clash
                ? ["Conflict", `${chordLabel(chord)} is bound to more than one action`, ...(a.hint ? [a.hint] : [])]
                : a.hint ? [a.label, a.hint] : undefined,
          })) {
            this.listening = listening ? null : a.id;
          }
          y += BIND_ROW;
        }
        y += 8;
      }
      return y;
    };

    drawGroups(x0, left);
    let ry = drawGroups(xR, right);

    // Fixed controls live under the shorter column.
    ui.text("Mouse & groups", xR + 22, ry, { size: 15, bold: true, color: PAL.uiAccent });
    ry += HEAD - 8;
    for (const [key, act] of FIXED_CONTROLS) {
      ui.text(key, xR + 22, ry, { size: 11.5, color: "#ffe9b0" });
      ui.text(act, xR + 172, ry, { size: 11.5, color: "#cabfa4" });
      ry += 22;
    }

    // Conflicts are legal but almost never intended, so say so plainly.
    if (conflicts.size) {
      const names = [...conflicts.keys()].map(chordLabel).join(", ");
      ui.text(`Bound twice: ${names} — the first action listed wins.`, x0 + 160, top + panelH + 46, { size: 12, bold: true, color: "#e0a878" });
    }
    if (ui.button("Reset to defaults", xR + colW - 180, top + panelH + 20, 180, 44, { size: 14 })) {
      for (const k of Object.keys(s.keybinds)) delete s.keybinds[k as ActionId];
      this.listening = null;
    }
    ui.text("Click a key to rebind · Esc cancels · Backspace clears",
      xR + colW - 196, top + panelH + 42, { align: "right", size: 11.5, color: "#8a8278" });
    return panelH;
  }

  /** A labelled On/Off switch. Returns the (possibly toggled) value. */
  private toggle(x: number, y: number, w: number, label: string, value: boolean): boolean {
    ui.text(label, x, y + 14, { size: 13 });
    const clicked = ui.button(value ? "On" : "Off", x + w - 72, y, 72, 28, { accent: value, size: 13 });
    return clicked ? !value : value;
  }
}
