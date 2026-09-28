// Replays: every match, kept so it can be watched again.
//
// The simulation is deterministic, so a match is its starting setup plus the
// orders given, tick by tick. A skirmish needs only the human's orders — the
// AIs make the same decisions again from the same seed. An online match has
// no AIs, so its record is every order the lockstep applied, from every
// player, in the order it applied them. Either way a replay is a few kilobytes
// where a video would be hundreds of megabytes, and it can be watched with the
// full caster view: any player's vision, any speed, the graphs, the feed.

import { SAVE_FORMAT_VERSION, type LoggedCommand } from "./savegame";
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
  /** Opened from a file rather than recorded here. */
  imported?: boolean;
  /** The simulation version it was recorded with (see SAVE_FORMAT_VERSION). */
  sim?: number;
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

// ------------------------------------------------------------ replay files --
//
// A replay is small (the setup plus the orders), so it travels as a plain JSON
// file: download it, send it to a friend or a caster, and open it in the game
// — the caster view plays it exactly as it happened. Anything opened is
// checked field by field before it's trusted: a replay file is someone else's
// data.

export const REPLAY_FILE_FORMAT = "banner-and-blade-replay";
export const REPLAY_FILE_EXT = ".bbreplay";
/** Big enough for hours of 8-player orders; small enough to refuse junk. */
const MAX_COMMANDS = 2_000_000;

/** The file name and text for downloading a replay. */
export function replayFile(r: ReplayRecord): { name: string; text: string } {
  const d = new Date(r.savedAt);
  const stamp = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}_${String(d.getHours()).padStart(2, "0")}${String(d.getMinutes()).padStart(2, "0")}`;
  const map = (r.summary.map || "match").replace(/[^A-Za-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 40) || "match";
  const { imported, ...rest } = r;
  void imported;
  const body = { format: REPLAY_FILE_FORMAT, fileVersion: 1, sim: r.sim ?? SAVE_FORMAT_VERSION, replay: rest };
  return { name: `banner-and-blade_${map}_${stamp}${REPLAY_FILE_EXT}`, text: JSON.stringify(body) };
}

export type ReplayFileResult = { ok: true; replay: ReplayRecord; warning?: string } | { ok: false; error: string };

const isNum = (v: unknown) => typeof v === "number" && Number.isFinite(v);
const isStr = (v: unknown) => typeof v === "string";

/** Read a replay file. Refuses anything that isn't one; warns if it's from another version. */
export function parseReplayFile(text: string): ReplayFileResult {
  let body: unknown;
  try { body = JSON.parse(text); } catch { return { ok: false, error: "That file isn't a Banner & Blade replay (it isn't valid JSON)." }; }
  const b = body as { format?: unknown; sim?: unknown; replay?: Record<string, unknown> };
  if (!b || b.format !== REPLAY_FILE_FORMAT || !b.replay || typeof b.replay !== "object") return { ok: false, error: "That file isn't a Banner & Blade replay." };
  const r = b.replay;
  if (!(r.kind === "skirmish" || r.kind === "watch" || r.kind === "online")) return { ok: false, error: "The replay's match type is unknown." };
  if (!isNum(r.endTick) || (r.endTick as number) < 1 || (r.endTick as number) > 20 * 60 * 60 * 6) return { ok: false, error: "The replay's length is invalid." };
  if (!Array.isArray(r.commands) || r.commands.length > MAX_COMMANDS) return { ok: false, error: "The replay's orders are missing or too many." };
  for (const e of r.commands as unknown[]) {
    const c = e as { t?: unknown; c?: { t?: unknown; team?: unknown } };
    if (!c || !isNum(c.t) || !c.c || !isStr(c.c.t) || !isNum(c.c.team)) return { ok: false, error: "The replay contains an order it can't read." };
  }
  if (r.kind === "online") {
    const n = r.net as Record<string, unknown> | undefined;
    if (!n || !isNum(n.seed) || !isNum(n.numTeams) || !Array.isArray(n.alliances) || !Array.isArray(n.factions) || !n.map || !isStr((n.map as { id?: unknown }).id)) {
      return { ok: false, error: "The online replay's setup is incomplete." };
    }
  } else if (!r.setup || typeof r.setup !== "object" || !isNum((r.setup as { seed?: unknown }).seed)) {
    return { ok: false, error: "The replay's match setup is incomplete." };
  }
  const names = Array.isArray(r.names) ? (r.names as unknown[]).map((x) => String(x ?? "").slice(0, 32)).slice(0, 16) : [];
  const sm = (r.summary ?? {}) as Record<string, unknown>;
  const replay: ReplayRecord = {
    version: REPLAY_VERSION,
    id: replayId(),
    savedAt: isNum(r.savedAt) ? (r.savedAt as number) : Date.now(),
    kind: r.kind as ReplayRecord["kind"],
    setup: r.setup,
    mapCode: isStr(r.mapCode) ? (r.mapCode as string) : undefined,
    net: r.net as NetReplaySetup | undefined,
    names,
    pov: isNum(r.pov) ? (r.pov as number) : -1,
    commands: r.commands as LoggedCommand[],
    endTick: r.endTick as number,
    summary: {
      map: String(sm.map ?? "Battlefield").slice(0, 40),
      players: isNum(sm.players) ? (sm.players as number) : names.length,
      durationSec: isNum(sm.durationSec) ? (sm.durationSec as number) : Math.round((r.endTick as number) / 20),
      result: String(sm.result ?? "").slice(0, 60),
      factions: Array.isArray(sm.factions) ? (sm.factions as unknown[]).map(String).slice(0, 16) : [],
    },
    imported: true,
    sim: isNum(b.sim) ? (b.sim as number) : undefined,
  };
  const warning = replay.sim !== undefined && replay.sim !== SAVE_FORMAT_VERSION
    ? "This replay was recorded with a different version of the game — it may play out differently."
    : undefined;
  return { ok: true, replay, warning };
}
