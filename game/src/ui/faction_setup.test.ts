import { describe, expect, it, beforeAll } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = { createElement: () => createCanvas(1, 1) };
import { ui } from "./ui";
import { SetupScreen } from "./screens";
import { Profile } from "../meta/profile";
import { FACTIONS } from "../content/factions";

/**
 * Picking a faction on the Skirmish setup screen: six cards, the choice sticks
 * to the profile, and each faction brings back the commander last led with it.
 */

beforeAll(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

const W = 1600, H = 900;

/** Draw a frame (optionally clicking), returning each faction card's rect. */
function frame(s: SetupScreen, profile: Profile, click?: { x: number; y: number }) {
  const canvas = createCanvas(W, H);
  ui.begin(canvas.getContext("2d") as unknown as CanvasRenderingContext2D, {
    mx: click?.x ?? -1, my: click?.y ?? -1, clicked: !!click, rightClicked: false, alt: false,
  });
  const cards: Record<string, { x: number; y: number; w: number; h: number }> = {};
  const orig = ui.button.bind(ui);
  ui.button = ((label: string, x: number, y: number, w: number, h: number, o?: Parameters<typeof ui.button>[5]) => {
    const name = o?.tooltip?.[0];
    const f = Object.values(FACTIONS).find((d) => d.name === name);
    if (f && label === "") cards[f.id] = { x, y: y + ui.ctx.getTransform().f, w, h };
    return orig(label, x, y, w, h, o);
  }) as typeof ui.button;
  try { s.draw(W, H, 1, profile); } finally { ui.button = orig; }
  return cards;
}

describe("Choosing a faction", () => {
  it("offers all six and remembers the choice", () => {
    const s = new SetupScreen();
    const profile = new Profile();
    profile.chooseFirstFaction("kingdom");
    profile.data.renown = 5000;
    profile.unlockFaction("khanate");
    frame(s, profile);
    // Scroll to the bottom so the faction panel is in view (it clamps).
    (s as unknown as { scroll: number }).scroll = 1e6;
    frame(s, profile);
    const cards = frame(s, profile);
    expect(Object.keys(cards).sort()).toEqual(Object.keys(FACTIONS).sort());
    const k = cards.khanate;
    expect(k.y + k.h / 2, "the faction cards are off screen even scrolled down").toBeLessThan(H - 80);
    frame(s, profile, { x: k.x + k.w / 2, y: k.y + k.h / 2 });
    expect(s.config.faction).toBe("khanate");
    expect(profile.data.faction).toBe("khanate");
  });

  it("won't let you pick a faction you haven't unlocked", () => {
    const s = new SetupScreen();
    const profile = new Profile();
    profile.chooseFirstFaction("legion");
    frame(s, profile);
    (s as unknown as { scroll: number }).scroll = 1e6;
    frame(s, profile);
    const cards = frame(s, profile);
    const k = cards.khanate;
    frame(s, profile, { x: k.x + k.w / 2, y: k.y + k.h / 2 });
    expect(s.config.faction).toBe("legion");
    expect(s.bookFocus, "a locked card opens the Factions book on it").toBe("khanate");
  });

  it("brings each faction's commander back with it", () => {
    const profile = new Profile();
    profile.chooseFirstFaction("norse");
    profile.data.renown = 5000;
    profile.unlockFaction("legion");
    profile.data.commanders = ["steward", "marshal"];
    profile.selectFaction("legion");
    profile.selectCommander("marshal");
    profile.pairCommander("legion", "marshal");
    profile.selectFaction("norse");
    profile.selectCommander("steward");
    profile.pairCommander("norse", "steward");
    profile.selectFaction("legion");
    expect(profile.data.commander).toBe("marshal");
    profile.selectFaction("norse");
    expect(profile.data.commander).toBe("steward");
  });
});
