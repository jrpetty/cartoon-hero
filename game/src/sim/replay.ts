// Replays: every match, kept so it can be watched again.
//
// The simulation is deterministic, so a match is its starting setup plus the
// orders given, tick by tick. A skirmish needs only the human's orders — the
// AIs make the same decisions again from the same seed. An online match has
// no AIs, so its record is every order the lockstep applied, from every
// player, in the order it applied them. Either way a replay is a few kilobytes
// where a video would be hundreds of megabytes, and it can be watched with the
// full caster view: any player's vision, any speed, the graphs, the feed.

import type { LoggedCommand } from "./savegame";
import type { Command } from "./commands";

export const REPLAY_VERSION = 1;
const KEY = "bb_replays_v1";
/** How many replays are kept; the oldest go first. */
export const REPLAY_LIMIT = 12;

/** How an online match was set up — everything startNetMatch needs, minus the network. */
export interface NetReplaySetup {
  seed: number;
  numTeams: number;
  alliances: number[];
  factions: string[];
  map: { id: string; name: string; code?: string };
}

export interface ReplayRecord {
  version: number;
  id: string;
  savedAt: number;
  kind: "skirmish" | "watch" | "online";
  /** The skirmish setup (skirmish / watch). */
  setup?: unknown;
  /** A custom map's share code, so the replay survives the map being edited. */
  mapCode?: string;
  net?: NetReplaySetup;
  /** Display names per team, in team order. */
  names: string[];
  /** The team the recording player played as (-1 when they watched). */
  pov: number;
  commands: LoggedCommand[];
  /** Where the recording ends. */
  endTick: number;
  summary: { map: string; players: number; durationSec: number; result: string; factions: string[] };
}

function store(): Storage | null {
  try { return typeof localStorage === "undefined" ? null : localStorage; } catch { return null; }
}

export function listReplays(): ReplayRecord[] {
  try {
    const raw = store()?.getItem(KEY);
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr) ? arr.filter((r) => r && r.version === REPLAY_VERSION).sort((a, b) => b.savedAt - a.savedAt) : [];
  } catch { return []; }
}

/** Keep a replay. When storage is full the oldest are dropped until it fits. */
export function saveReplay(r: ReplayRecord): boolean {
  const s = store();
  if (!s) return false;
  let all = [r, ...listReplays().filter((x) => x.id !== r.id)].slice(0, REPLAY_LIMIT);
  while (all.length) {
    try { s.setItem(KEY, JSON.stringify(all)); return true; } catch { all = all.slice(0, all.length - 1); }
  }
  return false;
}

export function deleteReplay(id: string): void {
  const s = store();
  if (!s) return;
  try { s.setItem(KEY, JSON.stringify(listReplays().filter((r) => r.id !== id))); } catch { /* */ }
}

/** Orders bucketed by tick, for playback. */
export function byTick(commands: LoggedCommand[]): Map<number, Command[]> {
  const m = new Map<number, Command[]>();
  for (const e of commands) {
    const at = m.get(e.t);
    if (at) at.push(e.c);
    else m.set(e.t, [e.c]);
  }
  return m;
}

export const replayId = () => `r${Date.now().toString(36)}${Math.floor(Math.random() * 1e6).toString(36)}`;
