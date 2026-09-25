import { describe, expect, it, beforeEach } from "vitest";
import { World } from "./world";
import { Team } from "./types";
import { generateMap } from "../maps/generator";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";
import { applyCommand, worldChecksum, type Command } from "./commands";
import {
  CommandLog, SAVE_FORMAT_VERSION, SAVE_LIMIT, type SaveGame,
  deleteSave, listSaves, replayTo, writeSave,
} from "./savegame";
import { SIM_DT } from "../content/balance";

beforeEach(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { for (const k of Object.keys(store)) delete store[k]; },
    key: () => null,
    length: 0,
  };
});

/** A match, built the same way twice — which is the whole premise of the format. */
function match(seed: number) {
  const map = generateMap("highlands", seed, 2);
  const world = new World(seed);
  world.init(map, [{}, {}], [1, 1], [0, 1]);
  const ais = [new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight)];
  return { world, ais };
}

/** Run the live loop shape: commands due, world tick, then every AI. */
function play(
  world: World, ais: SkirmishAI[], ticks: number,
  log?: CommandLog, orders: { t: number; c: Command }[] = [],
) {
  for (let tick = 0; tick < ticks; tick++) {
    for (const o of orders) {
      if (o.t === tick) {
        log?.record(tick, o.c);
        applyCommand(world, o.c); // the same applier the app dispatches through
      }
    }
    world.tick();
    for (const ai of ais) ai.update(SIM_DT);
    world.drainEvents();
  }
}

describe("The premise the format rests on", () => {
  it("two matches from the same seed are the same match", () => {
    // If this were ever false, a save built from a seed and an order log could
    // not reproduce anything, and the whole approach would have to be a state
    // dump instead.
    const a = match(7);
    const b = match(7);
    play(a.world, a.ais, 1200);
    play(b.world, b.ais, 1200);
    expect(worldChecksum(b.world)).toBe(worldChecksum(a.world));
  });

  it("two matches from different seeds are not", () => {
    const a = match(7);
    const b = match(8);
    play(a.world, a.ais, 600);
    play(b.world, b.ais, 600);
    expect(worldChecksum(b.world)).not.toBe(worldChecksum(a.world));
  });
});

describe("Replaying a match", () => {
  it("reproduces a match played with orders in it, exactly", () => {
    const live = match(11);
    const log = new CommandLog();
    // Some orders that actually change the world: build, train, move.
    const vill = live.world.entities.find(
      (e) => e.alive && e.team === Team.Player && e.type === "villager",
    )!;
    const tc = live.world.entities.find(
      (e) => e.alive && e.team === Team.Player && e.type === "town_center",
    )!;
    const orders: { t: number; c: Command }[] = [
      { t: 5, c: { t: "train", team: Team.Player, buildingId: tc.id, unit: "villager" } },
      { t: 40, c: { t: "move", team: Team.Player, ids: [vill.id], x: tc.x + 300, y: tc.y + 120, queue: false, attackMove: false } },
      { t: 200, c: { t: "place", team: Team.Player, building: "house", x: tc.x + 220, y: tc.y + 160, builders: [vill.id] } },
      { t: 600, c: { t: "stance", team: Team.Player, ids: [vill.id], stance: 1 } },
    ];
    play(live.world, live.ais, 1500, log, orders);
    const expected = worldChecksum(live.world);

    const fresh = match(11);
    const res = replayTo(fresh.world, fresh.ais, log.entries, 1500, expected);
    expect(res.actual, "the replay diverged from the live match").toBe(expected);
    expect(res.faithful).toBe(true);
  });

  it("reports a divergence rather than quietly playing the wrong match", () => {
    // The one failure mode a command log has, and the reason the checksum is
    // stored at all: it has to be caught at load, not an hour later.
    const fresh = match(11);
    const res = replayTo(fresh.world, fresh.ais, [], 200, 12345);
    expect(res.faithful).toBe(false);
    expect(res.expected).toBe(12345);
    expect(res.actual).not.toBe(12345);
  });

  it("lands on the tick it was asked for", () => {
    const fresh = match(3);
    replayTo(fresh.world, fresh.ais, [], 850);
    expect(fresh.world.tickCount).toBe(850);
  });

  it("drains events as it goes, so a resume doesn't fire an hour of alerts at once", () => {
    const fresh = match(3);
    replayTo(fresh.world, fresh.ais, [], 1500);
    expect(fresh.world.drainEvents().length).toBeLessThan(64);
  });

  it("replays an empty log to a clean fast-forward", () => {
    const a = match(5);
    play(a.world, a.ais, 700);
    const b = match(5);
    const res = replayTo(b.world, b.ais, [], 700, worldChecksum(a.world));
    expect(res.faithful).toBe(true);
  });

  it("depends on the log — dropping an order gives a different match", () => {
    // Proves the replay is actually driven by what was recorded rather than
    // reproducing the AI-vs-nobody match regardless. Without this, every replay
    // test above could pass on a log that was silently ignored.
    //
    // The order has to be one that demonstrably lands — an earlier version of
    // this test placed a house on ground the map had a tree on, so the command
    // was a no-op and the two runs matched for the most boring reason there is.
    const live = match(13);
    const log = new CommandLog();
    const tc = live.world.entities.find(
      (e) => e.alive && e.team === Team.Player && e.type === "town_center",
    )!;
    const before = live.world.entities.filter(
      (e) => e.alive && e.team === Team.Player && e.type === "villager",
    ).length;
    play(live.world, live.ais, 900, log, [
      { t: 137, c: { t: "train", team: Team.Player, buildingId: tc.id, unit: "villager" } },
      { t: 300, c: { t: "train", team: Team.Player, buildingId: tc.id, unit: "villager" } },
    ]);
    expect(log.entries[0].t, "the order was stamped with the wrong tick").toBe(137);
    const after = live.world.entities.filter(
      (e) => e.alive && e.team === Team.Player && e.type === "villager",
    ).length;
    expect(after, "the orders never took effect, so this proves nothing").toBeGreaterThan(before);
    const expected = worldChecksum(live.world);

    const withLog = match(13);
    expect(replayTo(withLog.world, withLog.ais, log.entries, 900).actual).toBe(expected);

    const without = match(13);
    expect(replayTo(without.world, without.ais, [], 900).actual,
      "the replay ignored the order log entirely").not.toBe(expected);
  });
});

describe("The command log", () => {
  it("records what it was given, in order", () => {
    const log = new CommandLog();
    log.record(3, { t: "stop", team: Team.Player, ids: [1] });
    log.record(9, { t: "stop", team: Team.Player, ids: [2] });
    expect(log.entries.map((e) => e.t)).toEqual([3, 9]);
  });

  it("clears between matches, so one game's orders never leak into the next", () => {
    const log = new CommandLog();
    log.record(1, { t: "stop", team: Team.Player, ids: [1] });
    log.clear();
    expect(log.entries).toHaveLength(0);
  });

  it("round-trips a loaded log without aliasing the caller's array", () => {
    const log = new CommandLog();
    const src = [{ t: 1, c: { t: "stop", team: Team.Player, ids: [1] } as Command }];
    log.load(src);
    log.record(2, { t: "stop", team: Team.Player, ids: [2] });
    expect(src).toHaveLength(1);
    expect(log.entries).toHaveLength(2);
  });
});

describe("Save storage", () => {
  const save = (label: string, at: number): SaveGame => ({
    version: SAVE_FORMAT_VERSION,
    savedAt: at,
    label,
    setup: { presetId: "highlands", seed: 1 },
    tick: 100,
    commands: [],
    checksum: 42,
    summary: { mapName: "Highlands", mode: "conquest", players: 2, difficulty: "knight", elapsed: 5 },
  });

  it("writes and reads a save", () => {
    expect(writeSave(save("A", 1))).toBe(true);
    expect(listSaves().map((s) => s.label)).toEqual(["A"]);
  });

  it("keeps the newest few and drops the oldest", () => {
    for (let i = 0; i < SAVE_LIMIT + 3; i++) writeSave(save(`S${i}`, 1000 + i));
    const all = listSaves();
    expect(all).toHaveLength(SAVE_LIMIT);
    expect(all[0].label).toBe(`S${SAVE_LIMIT + 2}`);
    expect(all.some((s) => s.label === "S0")).toBe(false);
  });

  it("overwrites a save with the same label rather than keeping both", () => {
    writeSave(save("Camp", 1));
    writeSave(save("Camp", 2));
    expect(listSaves().filter((s) => s.label === "Camp")).toHaveLength(1);
  });

  it("deletes one by label", () => {
    writeSave(save("A", 1));
    writeSave(save("B", 2));
    deleteSave("A");
    expect(listSaves().map((s) => s.label)).toEqual(["B"]);
  });

  it("ignores saves written by an older format", () => {
    // A format change means the replay would produce a different match, and a
    // save that silently loads into the wrong game is the worst outcome there is.
    localStorage.setItem("banner_and_blade_saves_v1",
      JSON.stringify([{ ...save("Old", 1), version: 0 }]));
    expect(listSaves()).toEqual([]);
  });

  it("survives a corrupt store", () => {
    localStorage.setItem("banner_and_blade_saves_v1", "not json at all");
    expect(listSaves()).toEqual([]);
  });
});
