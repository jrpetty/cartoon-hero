import { describe, expect, it, beforeEach } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = { createElement: () => createCanvas(1, 1) };
import {
  customFromGenerated, deserialiseMap, findCustomMap, listCustomMaps, mapPool, newCustomMap,
  publishCustomMap, rollRandomMap, saveCustomMap, serialiseMap, unpublishCustomMap,
} from "./custom";
import { PRESETS } from "./generator";
import { ui } from "../ui/ui";
import { SetupScreen } from "../ui/screens";
import { Profile } from "../meta/profile";

/**
 * Making a map and offering it to the game are two steps: a map is a draft
 * until its author publishes it, and publishing puts it in the pool — the
 * Skirmish list, the Random roll and online lobbies.
 */

let store: Record<string, string> = {};
beforeEach(() => {
  store = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

const ready = (name = "Crossing") => {
  const m = customFromGenerated("open_plains", 7, 2, name);
  m.maxPlayers = 2;
  return m;
};

describe("Publishing a map", () => {
  it("starts as a draft, outside the pool", () => {
    const m = ready();
    saveCustomMap(m);
    expect(findCustomMap(m.id)!.published).toBeFalsy();
    expect(mapPool("conquest", 2)).toEqual([]);
  });

  it("puts it in the pool, and saving again keeps it there", () => {
    const m = ready();
    saveCustomMap(m);
    expect(publishCustomMap(m, "Jarl Petty")).toEqual([]);
    expect(mapPool("conquest", 2).map((x) => x.id)).toEqual([m.id]);
    const again = findCustomMap(m.id)!;
    again.name = "Crossing II";
    saveCustomMap(again);
    expect(findCustomMap(m.id)!.published).toBeTruthy();
    expect(findCustomMap(m.id)!.author).toBe("Jarl Petty");
  });

  it("refuses a map with errors", () => {
    const m = newCustomMap("Empty", 64); // no spawns, no resources
    const why = publishCustomMap(m);
    expect(why.length).toBeGreaterThan(0);
    expect(mapPool("conquest", 2)).toEqual([]);
  });

  it("only offers a map to matches it allows", () => {
    const m = ready();
    publishCustomMap(m);
    expect(mapPool("conquest", 2)).toHaveLength(1);
    expect(mapPool("conquest", 4), "a 2-seat map offered to four").toHaveLength(0);
  });

  it("can be taken back out", () => {
    const m = ready();
    publishCustomMap(m);
    unpublishCustomMap(m);
    expect(mapPool("conquest", 2)).toEqual([]);
    expect(listCustomMaps()).toHaveLength(1);
  });

  it("keeps maps saved before publishing existed in the pool", () => {
    const m = ready();
    store.bb_custom_maps = JSON.stringify([`${m.id}|${serialiseMap(m)}`]);
    expect(mapPool("conquest", 2).map((x) => x.id)).toEqual([m.id]);
  });

  it("imports someone else's map as a draft, with their name on it", () => {
    const m = ready();
    m.author = "Ragnhild";
    m.published = Date.now();
    const got = deserialiseMap(serialiseMap(m))!;
    expect(got.published).toBeFalsy();
    expect(got.author).toBe("Ragnhild");
  });
});

describe("The Random battlefield", () => {
  it("rolls only presets while the pool is empty", () => {
    for (let s = 0; s < 50; s++) expect(rollRandomMap(s, [], PRESETS.length)).toBe("random");
  });

  it("lands on published maps too, the same way for the same seed", () => {
    const pool = [{ id: "custom_a" }, { id: "custom_b" }];
    const got = new Set<string>();
    for (let s = 0; s < 400; s++) {
      const r = rollRandomMap(s, pool, PRESETS.length);
      expect(rollRandomMap(s, pool, PRESETS.length)).toBe(r);
      got.add(r);
    }
    expect([...got].sort()).toEqual(["custom_a", "custom_b", "random"]);
  });
});

describe("The Skirmish list", () => {
  it("shows published maps and not drafts", () => {
    const draft = ready("Draft Field");
    saveCustomMap(draft);
    const pub = ready("Published Field");
    publishCustomMap(pub);
    const names: string[] = [];
    const canvas = createCanvas(1600, 900);
    ui.begin(canvas.getContext("2d") as unknown as CanvasRenderingContext2D, { mx: -1, my: -1, clicked: false, rightClicked: false, alt: false });
    const orig = ui.button.bind(ui);
    ui.button = ((label: string, x: number, y: number, w: number, h: number, o?: Parameters<typeof ui.button>[5]) => {
      if (o?.tooltip?.[0]) names.push(o.tooltip[0]);
      return orig(label, x, y, w, h, o);
    }) as typeof ui.button;
    try { new SetupScreen().draw(1600, 900, 1, new Profile()); } finally { ui.button = orig; }
    expect(names).toContain("Published Field");
    expect(names).not.toContain("Draft Field");
  });
});
