// Player-wide options, persisted to localStorage independently of the match
// profile. Loaded once at boot and applied through App.applySettings().

import { Keybinds } from "./keybinds";

export interface Settings {
  masterVol: number;
  sfxVol: number;
  musicVol: number;
  muted: boolean;
  damageNumbers: boolean;
  edgeScroll: boolean;
  scrollSpeed: number; // base camera pan speed (px/s at zoom 1)
  reduceEffects: boolean; // thin out particles on weaker machines / big 8v8s
  colorblind: boolean; // high-contrast team palette
  weather: boolean; // atmospheric rain/snow/overcast overlay
  showFps: boolean; // live FPS counter overlay
  perfOverlay: boolean; // frame/tick/entity readout, for diagnosing a chugging match
  autoLod: boolean; // drop detail on its own when frames start slipping
  uiScale: number; // 0.8..1.5 — interface only; the world keeps its apparent size
  keybinds: Keybinds; // overrides; anything missing uses the action's default
}

export const DEFAULT_SETTINGS: Settings = {
  masterVol: 0.8,
  sfxVol: 0.7,
  musicVol: 0.35,
  muted: false,
  damageNumbers: true,
  edgeScroll: true,
  scrollSpeed: 540,
  reduceEffects: false,
  colorblind: false,
  weather: true,
  showFps: false,
  perfOverlay: false,
  autoLod: true,
  uiScale: 1,
  keybinds: {},
};

const KEY = "bb_settings";

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const saved = JSON.parse(raw) as Partial<Settings>;
      // Merge rather than replace: a settings file written before an option
      // existed must still get that option's default, and a corrupt keybind
      // map must not take the whole hotkey system down with it.
      return {
        ...DEFAULT_SETTINGS,
        ...saved,
        keybinds: (saved.keybinds && typeof saved.keybinds === "object") ? saved.keybinds : {},
        uiScale: Math.max(0.8, Math.min(1.5, Number(saved.uiScale) || 1)),
      };
    }
  } catch { /* no storage / bad json -> defaults */ }
  return { ...DEFAULT_SETTINGS };
}

export function saveSettings(s: Settings) {
  try { localStorage.setItem(KEY, JSON.stringify(s)); } catch { /* */ }
}
