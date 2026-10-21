import { describe, expect, it, beforeEach } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = { createElement: () => createCanvas(1, 1) };
import { FACTION_PRICE, Profile } from "./profile";
import { FACTIONS, FACTION_IDS } from "../content/factions";
import { ui } from "../ui/ui";
import { FactionBook } from "../ui/faction_book";
import { MenuScreen } from "../ui/screens";

/**
 * Factions are owned. A new player picks one free; every other costs
 * FACTION_PRICE renown — the War Chest currency — and the Factions book is
 * where they read up and buy.
 */

beforeEach(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

describe("Owning factions", () => {
  it("starts with none, and one free pick", () => {
    const p = new Profile();
    expect(p.needsFirstFaction).toBe(true);
    expect(p.chooseFirstFaction("khanate")).toBe(true);
    expect(p.ownedFactions()).toEqual(["khanate"]);
    expect(p.chooseFirstFaction("legion"), "a second free pick").toBe(false);
    expect(p.playableFaction()).toBe("khanate");
  });

  it("costs 1500 renown for each after that", () => {
    expect(FACTION_PRICE).toBe(1500);
    const p = new Profile();
    p.chooseFirstFaction("kingdom");
    p.data.renown = 1499;
    expect(p.unlockFaction("legion")).toBe(false);
    expect(p.data.renown).toBe(1499);
    p.data.renown = 1600;
    expect(p.unlockFaction("legion")).toBe(true);
    expect(p.data.renown).toBe(100);
    expect(p.unlockFaction("legion"), "buying twice").toBe(false);
    expect(p.data.renown).toBe(100);
  });

  it("won't select a faction you don't own", () => {
    const p = new Profile();
    p.chooseFirstFaction("norse");
    p.selectFaction("ascendancy");
    expect(p.data.faction).toBe("norse");
  });

  it("survives a reload", () => {
    const p = new Profile();
    p.chooseFirstFaction("shogunate");
    p.data.renown = 2000;
    p.unlockFaction("legion");
    const again = Profile.load();
    expect(again.ownedFactions().sort()).toEqual(["legion", "shogunate"]);
  });

  it("has a full entry in the book for every faction", () => {
    for (const id of FACTION_IDS) {
      const g = FACTIONS[id].guide;
      expect(g.playstyle.length, id).toBeGreaterThan(120);
      expect(g.suits.length, id).toBeGreaterThanOrEqual(3);
      expect(g.tips.length, id).toBeGreaterThanOrEqual(3);
      expect(g.power.every((v) => v >= 1 && v <= 5), id).toBe(true);
    }
  });
});

function frame(fn: () => unknown, click?: { x: number; y: number }, W = 1600, H = 900) {
  const canvas = createCanvas(W, H);
  ui.begin(canvas.getContext("2d") as unknown as CanvasRenderingContext2D, {
    mx: click?.x ?? -1, my: click?.y ?? -1, clicked: !!click, rightClicked: false, alt: false,
  });
  return fn();
}

describe("The first launch", () => {
  it("sends a new player to choose a faction before anything else", () => {
    const p = new Profile();
    p.data.commanderReveal = "";
    expect(frame(() => new MenuScreen().draw(1600, 900, 0, p))).toBe("factions");
    p.chooseFirstFaction("legion");
    expect(frame(() => new MenuScreen().draw(1600, 900, 0, p))).toBeNull();
  });

  it("gives the chosen faction free from the book, and only then lets you leave", () => {
    const p = new Profile();
    const book = new FactionBook();
    book.focus("ascendancy");
    // The take button sits at the right of the action bar.
    const W = 1600, H = 900;
    const outer = Math.min(W - 48, 1320), x0 = Math.round(W / 2 - outer / 2);
    const take = { x: x0 + outer - 150, y: H - 72 + 14 + 22 };
    const back = { x: x0 + 65, y: take.y };
    expect(frame(() => book.draw(W, H, 0, p), back), "left without choosing").toBeNull();
    expect(frame(() => book.draw(W, H, 0, p), take)).toBe("back");
    expect(p.ownedFactions()).toEqual(["ascendancy"]);
    expect(p.data.renown, "the first one is free").toBe(new Profile().data.renown);
  });

  it("sells the rest from the book", () => {
    const p = new Profile();
    p.chooseFirstFaction("kingdom");
    p.data.renown = 1500;
    const book = new FactionBook();
    book.focus("norse");
    const W = 1600, H = 900;
    const outer = Math.min(W - 48, 1320), x0 = Math.round(W / 2 - outer / 2);
    frame(() => book.draw(W, H, 0, p), { x: x0 + outer - 150, y: H - 72 + 36 });
    expect(p.ownsFaction("norse")).toBe(true);
    expect(p.data.renown).toBe(0);
  });

  it("draws every faction's page without throwing, at any size", () => {
    const p = new Profile();
    p.chooseFirstFaction("kingdom");
    const book = new FactionBook();
    for (const id of FACTION_IDS) {
      book.focus(id);
      for (const [W, H] of [[1600, 900], [900, 600]] as const) expect(() => frame(() => book.draw(W, H, 1, p), undefined, W, H)).not.toThrow();
    }
  });
});
