import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = { createElement: () => createCanvas(1, 1) };
import { ui } from "./ui";
import { CASTER_PANELS, Caster, sidesOf, snapshotRealms } from "./caster";
import { World } from "../sim/world";
import { generateMap } from "../maps/generator";
import { Team } from "../sim/types";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";
import { SIM_DT } from "../content/balance";
import { snapshotMetrics } from "../sim/metrics";

/**
 * The caster view: a scoreboard of every realm, a director that finds the
 * fight, a feed of what happened, vision switching, and panels that never
 * break however the match looks.
 */

function aiMatch(n = 4, seconds = 240) {
  const w = new World(21);
  const teams = Array.from({ length: n }, (_, i) => i);
  w.init(generateMap("open_plains", 21, n), teams.map(() => ({})), teams.map(() => 1), [0, 0, 1, 1].slice(0, n), teams.map(() => ""), false, undefined, "conquest",
    ["kingdom", "legion", "norse", "khanate"].slice(0, n));
  const ais = teams.map((t) => new SkirmishAI(w, t as Team, DIFFICULTIES.knight));
  const history: { t: number; m: ReturnType<typeof snapshotMetrics> }[] = [];
  const caster = new Caster();
  for (let i = 0; i < seconds * 20; i++) {
    w.tick();
    for (const ai of ais) ai.update(SIM_DT);
    caster.onEvents(w, w.drainEvents(), []);
    if (i % 80 === 0) history.push({ t: w.time, m: snapshotMetrics(w) });
  }
  return { w, caster, history };
}

function frame(fn: () => void, click?: { x: number; y: number }, W = 1600, H = 900) {
  const canvas = createCanvas(W, H);
  ui.begin(canvas.getContext("2d") as unknown as CanvasRenderingContext2D, { mx: click?.x ?? -1, my: click?.y ?? -1, clicked: !!click, rightClicked: false, alt: false });
  fn();
  ui.flushTooltip(W, H);
}

describe("The caster's numbers", () => {
  it("totals every realm and groups them into sides", () => {
    const { w } = aiMatch(4, 90);
    const r = snapshotRealms(w);
    expect(r).toHaveLength(4);
    for (const x of r) {
      expect(x.villagers).toBeGreaterThan(3);
      expect(x.buildings).toBeGreaterThan(0);
    }
    expect(r.some((x) => x.production.length > 0), "something is always in production early on").toBe(true);
    expect(sidesOf(w)).toEqual([[0, 1], [2, 3]]);
  });
});

describe("The director", () => {
  it("cuts to a fight, follows it, and backs off when the caster takes over", () => {
    const w = new World(3);
    w.init(generateMap("open_plains", 3), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
    const c = new Caster();
    const cam = { x: 100, y: 100, zoom: 1 };
    // A burst of deaths far from the camera.
    const deaths = Array.from({ length: 10 }, (_, i) => ({ kind: "death" as const, x: 1800 + i * 5, y: 1600, team: (i % 2) as Team, data: "militia" }));
    c.onEvents(w, deaths, []);
    for (let i = 0; i < 90; i++) c.direct(w, cam, 1 / 30);
    expect(Math.hypot(cam.x - 1820, cam.y - 1600), "the camera went to the fight").toBeLessThan(40);
    expect(c.feed.some((f) => f.text.startsWith("Battle") || f.text.startsWith("First blood")), "and the feed said so").toBe(true);
    // The caster grabs the camera: the director waits.
    cam.x = 300; cam.y = 300;
    c.direct(w, cam, 1 / 30);
    for (let i = 0; i < 30; i++) c.direct(w, cam, 1 / 30);
    expect(cam.x).toBe(300);
  });

  it("tours the bases when nothing is happening", () => {
    const w = new World(4);
    w.init(generateMap("open_plains", 4), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
    const c = new Caster();
    const cam = { x: w.worldW / 2, y: w.worldH / 2, zoom: 1 };
    for (let i = 0; i < 120; i++) c.direct(w, cam, 1 / 30);
    const near = [0, 1].some((t) => Math.hypot(cam.x - w.map.starts[t].x, cam.y - w.map.starts[t].y) < 200);
    expect(near).toBe(true);
  });
});

describe("The feed", () => {
  it("reports ages, Oaths, fallen Town Centres and eliminations", () => {
    const w = new World(5);
    w.init(generateMap("open_plains", 5), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
    const c = new Caster();
    c.onEvents(w, [
      { kind: "age", x: 0, y: 0, team: 0 as Team, data: "1" },
      { kind: "oath", x: 0, y: 0, team: 0 as Team, data: "sword" },
      { kind: "collapse", x: 0, y: 0, team: 1 as Team, data: "town_center" },
    ], ["Aldric", "Brenna"]);
    w.player(1 as Team).defeated = true;
    c.onEvents(w, [], ["Aldric", "Brenna"]);
    const lines = c.feed.map((f) => f.text);
    expect(lines).toContain("Aldric reaches the Banner Age");
    expect(lines).toContain("Aldric swears the Oath of the Sword");
    expect(lines).toContain("Brenna's Town Center has fallen");
    expect(lines).toContain("Brenna has been eliminated");
  });
});

describe("Caster keys", () => {
  it("switch vision, panels, the director, clean feed and graphs", () => {
    const w = new World(6);
    w.init(generateMap("open_plains", 6), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
    const c = new Caster();
    expect(c.key("2", w)).toBe(true);
    expect(c.vision).toBe(1);
    c.key("2", w);
    expect(c.vision, "again: back to everything").toBe(-1);
    c.key("7", w);
    expect(c.vision, "no seventh player here").toBe(-1);
    c.key("w", w); expect(c.panel).toBe("army");
    c.key("Tab", w); expect(c.panel).toBe("economy");
    const auto = c.auto; c.key("a", w); expect(c.auto).toBe(!auto);
    c.key("h", w); expect(c.clean).toBe(true);
    c.key("g", w); expect(c.graphs).toBe(true);
    expect(c.key("x", w)).toBe(false);
  });
});

describe("Drawing the caster view", () => {
  it("draws every panel, clean feed, graphs, help and a replay timeline at any size", () => {
    const { w, caster, history } = aiMatch(4, 200);
    const names = ["Aldric", "Brenna", "Cato", "Dagny"];
    for (const [id] of CASTER_PANELS) {
      caster.panel = id;
      for (const [W, H] of [[1600, 900], [1280, 720], [1024, 600]] as const) {
        expect(() => frame(() => caster.draw(W, H, { world: w, names, history, speed: 1, paused: false, source: "live", delaySec: 0 }), undefined, W, H), `${id} ${W}`).not.toThrow();
      }
    }
    caster.graphs = true; caster.help = true;
    expect(() => frame(() => caster.draw(1600, 900, { world: w, names, history, speed: 4, paused: true, source: "replay", delaySec: 0, replay: { tick: 2000, endTick: 4000 } }))).not.toThrow();
    caster.clean = true;
    expect(() => frame(() => caster.draw(1600, 900, { world: w, names, history, speed: 1, paused: false, source: "delay", delaySec: 60 }))).not.toThrow();
  });

  it("asks to jump when the replay timeline is clicked", () => {
    const { w, caster, history } = aiMatch(2, 30);
    caster.auto = false;
    const ctx = { world: w, names: [], history, speed: 1, paused: false, source: "replay" as const, delaySec: 0, replay: { tick: 0, endTick: 20 * 600 } };
    // The timeline's bar spans from after the speed buttons to the time readout.
    const W = 1600, H = 900;
    const y = H - 176 - 40;
    let req = {};
    const x0 = 196 + 30, bx = x0 + 36 + 6 * 38 + 10, bw = W - 20 - bx - 70;
    frame(() => { req = caster.draw(W, H, ctx); }, { x: bx + bw / 2, y: y + 12 });
    expect((req as { seekTo?: number }).seekTo).toBeCloseTo(20 * 300, -2);
  });
});
