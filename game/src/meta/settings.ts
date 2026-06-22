// Player-wide options, persisted to localStorage independently of the match
// profile. Loaded once at boot and applied through App.applySettings().

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
};

const KEY = "bb_settings";

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) };
  } catch { /* no storage / bad json -> defaults */ }
  return { ...DEFAULT_SETTINGS };
}

export function saveSettings(s: Settings) {
  try { localStorage.setItem(KEY, JSON.stringify(s)); } catch { /* */ }
}
