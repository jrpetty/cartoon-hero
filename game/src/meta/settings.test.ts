import { describe, expect, it, beforeEach } from "vitest";
import { loadSettings, saveSettings, DEFAULT_SETTINGS } from "./settings";
import { teamColor, setColorblindTeams } from "../render/palette";
import { Particles } from "../engine/particles";

describe("Settings persistence", () => {
  beforeEach(() => {
    const store: Record<string, string> = {};
    (globalThis as unknown as { localStorage: unknown }).localStorage = {
      getItem: (k: string) => store[k] ?? null,
      setItem: (k: string, v: string) => { store[k] = v; },
    };
  });

  it("returns defaults when nothing is stored", () => {
    expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
  });

  it("round-trips saved settings", () => {
    const s = { ...DEFAULT_SETTINGS, musicVol: 0.1, colorblind: true, scrollSpeed: 700 };
    saveSettings(s);
    expect(loadSettings()).toEqual(s);
  });

  it("merges partial/older stored settings over current defaults", () => {
    localStorage.setItem("bb_settings", JSON.stringify({ muted: true })); // missing newer keys
    const loaded = loadSettings();
    expect(loaded.muted).toBe(true);
    expect(loaded.masterVol).toBe(DEFAULT_SETTINGS.masterVol); // filled from defaults
    expect(loaded.colorblind).toBe(DEFAULT_SETTINGS.colorblind);
  });
});

describe("Colorblind palette", () => {
  it("swaps team colors when enabled and restores when off", () => {
    const normal = teamColor(0).main;
    setColorblindTeams(true);
    const cb = teamColor(0).main;
    expect(cb).not.toBe(normal);
    expect(teamColor(15)).toBeTruthy(); // all 16 teams have a color
    setColorblindTeams(false);
    expect(teamColor(0).main).toBe(normal);
  });
});

describe("Reduce-effects particle thinning", () => {
  it("density 0 spawns nothing; density 1 spawns", () => {
    const p = new Particles(200);
    const active = () => p.pool.filter((q) => q.active).length;
    p.density = 0;
    for (let i = 0; i < 50; i++) p.spawn({ x: 0, y: 0 });
    expect(active()).toBe(0);
    p.density = 1;
    for (let i = 0; i < 50; i++) p.spawn({ x: 0, y: 0 });
    expect(active()).toBeGreaterThan(0);
  });
});
