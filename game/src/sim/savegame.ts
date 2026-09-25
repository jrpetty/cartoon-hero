// Save and resume, built on the command layer rather than on a state dump.
//
// A saved game here is **the seed, the setup, and every order you gave** — not a
// snapshot of the world. The sim is a deterministic fixed-tick machine driven by
// a seeded RNG, and the AI is deterministic too, so replaying the setup and the
// order log reproduces the match exactly, tick for tick. That is the same
// property lockstep multiplayer needs, and the same one that makes replays
// nearly free once this exists.
//
// Why not serialise the world? It would load instantly, which is the one real
// advantage, and it would cost a per-field serialiser for every entity, player
// and grid — plus a new bug every time anyone adds a field and forgets to save
// it. That class of bug is silent and corrupts saves retroactively. A command
// log has one failure mode instead: if the replay ever diverges, the checksum
// says so at load time, loudly, before you have played an hour on top of it.
//
// The cost is load time. Twelve minutes of a two-player match is ~14,000 ticks,
// which is a few seconds of headless simulation. Worth it for a format that
// cannot silently rot.

import type { Command } from "./commands";
import { applyCommand, worldChecksum } from "./commands";
import type { World } from "./world";
import { SIM_DT } from "../content/balance";

export const SAVE_FORMAT_VERSION = 1;
const STORAGE_KEY = "banner_and_blade_saves_v1";
/** How many saves we keep. Enough for a few games in flight, bounded for storage. */
export const SAVE_LIMIT = 6;

/** One order, and the tick it was applied on. */
export interface LoggedCommand {
  t: number;
  c: Command;
}

/**
 * Everything needed to rebuild a match.
 *
 * `setup` is deliberately opaque here: the sim doesn't own the notion of a match
 * config, the app does, and threading its type through would make this module
 * depend on the UI. It is round-tripped verbatim.
 */
export interface SaveGame {
  version: number;
  savedAt: number;
  label: string;
  setup: unknown;
  /**
   * A custom map's share code. Custom maps live in a separate localStorage
   * library that can be edited or deleted between saving and loading, so the
   * map travels *with* the save rather than being looked up by id.
   */
  mapCode?: string;
  /** The tick the save was taken at — how far the resume must fast-forward. */
  tick: number;
  commands: LoggedCommand[];
  /** Sim state hash at `tick`. A resume that diverged is caught, not played. */
  checksum: number;
  /** For the load screen, so a save can be told apart without replaying it. */
  summary: {
    mapName: string;
    mode: string;
    players: number;
    difficulty: string;
    /** Match clock in seconds. */
    elapsed: number;
  };
}

/**
 * Records the orders a match has been given.
 *
 * Only the *player's* commands go in. The AI drives the world directly and
 * deterministically off its own seeded RNG, so its decisions are reproduced by
 * re-running it rather than by recording them — which is also why the log stays
 * small: a long match is a few hundred entries, not a few hundred thousand.
 */
export class CommandLog {
  entries: LoggedCommand[] = [];

  clear() {
    this.entries.length = 0;
  }

  record(tick: number, cmd: Command) {
    this.entries.push({ t: tick, c: cmd });
  }

  /** Restore a log from a save, so play can continue and be saved again. */
  load(entries: LoggedCommand[]) {
    this.entries = entries.map((e) => ({ t: e.t, c: e.c }));
  }
}

export interface ReplayResult {
  /** True when the rebuilt world hashes to what the save recorded. */
  faithful: boolean;
  expected: number;
  actual: number;
  ticks: number;
}

/**
 * Fast-forward a freshly-built world to the saved tick.
 *
 * The order inside the loop has to match the live match loop exactly — world
 * tick, then every AI in roster order — because "deterministic" only means
 * anything relative to a fixed order of operations. Getting this wrong is the
 * one way a command-log save can diverge, so it is the only thing this function
 * does.
 */
export function replayTo(
  world: World,
  ais: { update(dt: number): void }[],
  log: LoggedCommand[],
  targetTick: number,
  expectedChecksum?: number,
): ReplayResult {
  // Bucket by tick so each step is a lookup rather than a scan of the whole log.
  const byTick = new Map<number, Command[]>();
  for (const e of log) {
    const at = byTick.get(e.t);
    if (at) at.push(e.c);
    else byTick.set(e.t, [e.c]);
  }
  for (let tick = 0; tick < targetTick; tick++) {
    const due = byTick.get(tick);
    if (due) for (const c of due) applyCommand(world, c);
    world.tick();
    for (const ai of ais) ai.update(SIM_DT);
    // Events would otherwise pile up to the cap and the first live frame would
    // fire an hour of alerts and sounds at once.
    world.drainEvents();
  }
  const actual = worldChecksum(world);
  return {
    faithful: expectedChecksum === undefined || actual === expectedChecksum,
    expected: expectedChecksum ?? actual,
    actual,
    ticks: targetTick,
  };
}

// ------------------------------------------------------------------ storage --

function store(): Storage | null {
  try {
    return typeof localStorage === "undefined" ? null : localStorage;
  } catch {
    return null;
  }
}

/** Newest first. */
export function listSaves(): SaveGame[] {
  const s = store();
  if (!s) return [];
  try {
    const raw = s.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((x): x is SaveGame =>
        !!x && x.version === SAVE_FORMAT_VERSION && Array.isArray(x.commands) && typeof x.tick === "number")
      .sort((a, b) => b.savedAt - a.savedAt)
      .slice(0, SAVE_LIMIT);
  } catch {
    return [];
  }
}

/** Write a save, evicting the oldest once the shelf is full. */
export function writeSave(save: SaveGame): boolean {
  const s = store();
  if (!s) return false;
  const all = [save, ...listSaves().filter((x) => x.label !== save.label)].slice(0, SAVE_LIMIT);
  try {
    s.setItem(STORAGE_KEY, JSON.stringify(all));
    return true;
  } catch {
    // Storage full. Worth reporting, unlike a dropped history entry — someone
    // asked for this one.
    return false;
  }
}

export function deleteSave(label: string): void {
  const s = store();
  if (!s) return;
  try {
    s.setItem(STORAGE_KEY, JSON.stringify(listSaves().filter((x) => x.label !== label)));
  } catch { /* nothing useful to do */ }
}
