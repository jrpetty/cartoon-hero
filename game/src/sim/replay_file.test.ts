import { describe, expect, it } from "vitest";
import { World } from "./world";
import { generateMap } from "../maps/generator";
import { Kind, Team } from "./types";
import { applyCommand, worldChecksum, Command } from "./commands";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";
import { SIM_DT } from "../content/balance";
import { ReplayRecord, byTick, parseReplayFile, replayFile, REPLAY_FILE_EXT } from "./replay";
import { SAVE_FORMAT_VERSION } from "./savegame";

/**
 * A replay as a file: download it, open it anywhere, and it plays out exactly
 * as it happened. And a file that isn't a replay — or has been tampered with —
 * is refused, not run.
 */

/** Play a short skirmish (human orders + an AI), recording the human's orders. */
function playAndRecord() {
  const w = new World(55);
  w.init(generateMap("open_plains", 55), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
  const ai = new SkirmishAI(w, 1 as Team, DIFFICULTIES.knight);
  const log: { t: number; c: Command }[] = [];
  for (let i = 0; i < 20 * 90; i++) {
    if (i % 100 === 0) {
      const ids = w.entitiesOf(Team.Player, Kind.Unit).map((e) => e.id);
      const c: Command = { t: "move", team: Team.Player, ids, x: 600 + (i % 700), y: 700, queue: false, attackMove: false };
      log.push({ t: w.tickCount, c });
      applyCommand(w, c);
    }
    w.tick();
    ai.update(SIM_DT);
  }
  const rec: ReplayRecord = {
    version: 1, id: "r1", savedAt: Date.now(), kind: "skirmish", setup: { seed: 55, presetId: "open_plains" }, names: ["Aldric", "Azure · Knight"],
    pov: 0, commands: log, endTick: w.tickCount, summary: { map: "Open Plains", players: 2, durationSec: 90, result: "Unfinished", factions: ["kingdom", "kingdom"] }, sim: SAVE_FORMAT_VERSION,
  };
  return { rec, sum: worldChecksum(w) };
}

describe("Replay files", () => {
  it("survive the trip through a file and play out identically", () => {
    const { rec, sum } = playAndRecord();
    const file = replayFile(rec);
    expect(file.name.endsWith(REPLAY_FILE_EXT)).toBe(true);
    expect(file.name).toMatch(/Open-Plains/);
    const back = parseReplayFile(file.text);
    expect(back.ok).toBe(true);
    if (!back.ok) return;
    expect(back.warning).toBeUndefined();
    expect(back.replay.imported).toBe(true);
    expect(back.replay.id).not.toBe(rec.id);
    // Rebuild the match from the file alone.
    const w = new World(55);
    w.init(generateMap("open_plains", 55), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
    const ai = new SkirmishAI(w, 1 as Team, DIFFICULTIES.knight);
    const due = byTick(back.replay.commands);
    while (w.tickCount < back.replay.endTick) {
      for (const c of due.get(w.tickCount) ?? []) applyCommand(w, c);
      w.tick();
      ai.update(SIM_DT);
    }
    expect(worldChecksum(w)).toBe(sum);
  });

  it("stays small", () => {
    const { rec } = playAndRecord();
    expect(replayFile(rec).text.length).toBeLessThan(20_000);
  });

  it("warns when it was recorded with another version", () => {
    const { rec } = playAndRecord();
    const res = parseReplayFile(replayFile({ ...rec, sim: SAVE_FORMAT_VERSION + 1 }).text);
    expect(res.ok && res.warning).toMatch(/different version/);
  });

  it("refuses what isn't a replay", () => {
    expect(parseReplayFile("not json").ok).toBe(false);
    expect(parseReplayFile(JSON.stringify({ hello: "world" })).ok).toBe(false);
    const { rec } = playAndRecord();
    const good = JSON.parse(replayFile(rec).text);
    const tamper = (f: (b: any) => void) => { const b = JSON.parse(JSON.stringify(good)); f(b); return parseReplayFile(JSON.stringify(b)); };
    expect(tamper((b) => { b.replay.kind = "hack"; }).ok).toBe(false);
    expect(tamper((b) => { b.replay.endTick = -5; }).ok).toBe(false);
    expect(tamper((b) => { b.replay.commands = [{ t: "x", c: {} }]; }).ok).toBe(false);
    expect(tamper((b) => { delete b.replay.setup; }).ok).toBe(false);
    expect(tamper((b) => { b.replay.kind = "online"; }).ok, "online replays need their network setup").toBe(false);
    const long = tamper((b) => { b.replay.names = ["x".repeat(500)]; });
    expect(long.ok && long.replay.names[0].length).toBe(32);
  });
});
