// The fullscreen control, drawn wherever the game offers one.
//
// Drawing it does not toggle anything — the click handler does, inside the
// gesture (engine/fullscreen.ts). This only draws the button, labels it for the
// current state, and registers where it is. Where fullscreen is unavailable it
// is drawn disabled with the reason, instead of being a button that silently
// fails.

import { ui } from "./ui";
import { fullscreenSupported, isFullscreen } from "../engine/fullscreen";

export const FULLSCREEN_ZONE = "fullscreen";

export function fullscreenButton(
  x: number, y: number, w: number, h: number,
  opts: { compact?: boolean; size?: number; hotkey?: string } = {},
) {
  const supported = fullscreenSupported();
  const on = isFullscreen();
  const label = opts.compact ? (on ? "🗗" : "⛶") : on ? "🗗  Exit fullscreen" : "⛶  Fullscreen";
  const key = opts.hotkey ? ` (${opts.hotkey})` : "";
  ui.button(label, x, y, w, h, {
    size: opts.size ?? 14,
    disabled: !supported,
    tooltip: supported
      ? [on ? `Exit fullscreen${key}` : `Fullscreen${key}`, "Esc also leaves fullscreen."]
      : [
        "Fullscreen isn't available here",
        "This page is embedded somewhere that doesn't allow it, or the device only allows fullscreen for video. Open the game file directly in a browser tab.",
      ],
  });
  if (supported) ui.registerGestureZone(FULLSCREEN_ZONE, x, y, w, h);
}
