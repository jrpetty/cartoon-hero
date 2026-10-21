// Fullscreen, and the one rule that decides how it has to be wired.
//
// Browsers only honour requestFullscreen() while a user gesture is in flight —
// synchronously inside the click or keydown handler (Safari is the strictest;
// Chrome allows a short grace period, but nothing should rely on it). This UI is
// immediate-mode: a click is recorded by the DOM handler and only *interpreted*
// when the next frame draws the buttons, by which time the gesture is over and
// Safari rejects the request. So the toggle is never called from the frame
// loop. Buttons register where they are (see ui.registerGestureZone) and the
// click handler checks those rects and calls toggleFullscreen() itself.

type Doc = Document & {
  webkitFullscreenElement?: Element | null;
  webkitFullscreenEnabled?: boolean;
  webkitExitFullscreen?: () => Promise<void> | void;
};
type El = HTMLElement & { webkitRequestFullscreen?: () => Promise<void> | void };

function doc(): Doc | null {
  return typeof document === "undefined" ? null : (document as Doc);
}

/**
 * Whether this page is allowed to go fullscreen at all.
 *
 * False on iPhone Safari (element fullscreen exists only for video there) and
 * inside an iframe that was not given `allow="fullscreen"` — which includes some
 * hosted previews. Callers show the control disabled with a reason rather than
 * offering a button that silently does nothing.
 */
export function fullscreenSupported(): boolean {
  const d = doc();
  if (!d) return false;
  return !!(d.fullscreenEnabled || d.webkitFullscreenEnabled);
}

export function isFullscreen(): boolean {
  const d = doc();
  return !!d && !!(d.fullscreenElement || d.webkitFullscreenElement);
}

/**
 * Enter or leave fullscreen. Must be called from inside a user-gesture handler.
 *
 * Never throws: a refused request (no gesture, blocked by the embedding page,
 * the user said no) leaves the game exactly as it was, which is the only sane
 * outcome for a convenience toggle.
 */
export function toggleFullscreen(target?: HTMLElement): void {
  const d = doc();
  if (!d || !fullscreenSupported()) return;
  try {
    if (isFullscreen()) {
      const exit = d.exitFullscreen?.bind(d) ?? d.webkitExitFullscreen?.bind(d);
      const p = exit?.();
      if (p && typeof (p as Promise<void>).catch === "function") (p as Promise<void>).catch(() => {});
      return;
    }
    const el = (target ?? d.documentElement) as El;
    const req = el.requestFullscreen?.bind(el) ?? el.webkitRequestFullscreen?.bind(el);
    const p = req?.();
    const after = () => lockLandscape();
    if (p && typeof (p as Promise<void>).then === "function") {
      (p as Promise<void>).then(after, () => {});
    } else {
      after();
    }
  } catch {
    /* refused — nothing to undo */
  }
}

/**
 * On a phone, fullscreen is also the moment to hold the screen sideways: an RTS
 * in portrait is a letterbox with a minimap. Only ever *attempted* — most
 * browsers refuse orientation locks outside fullscreen, some refuse them always,
 * and desktop has no orientation to lock.
 */
function lockLandscape() {
  try {
    const touch = typeof navigator !== "undefined" && (navigator.maxTouchPoints ?? 0) > 0;
    if (!touch) return;
    const o = (screen as Screen & { orientation?: { lock?: (o: string) => Promise<void> } }).orientation;
    o?.lock?.("landscape")?.catch(() => {});
  } catch {
    /* not supported here */
  }
}
