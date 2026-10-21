// A browser player, headless: the real src/ui/net_lobby.ts (on a minimal
// fake DOM, as net_lobby.test.ts runs it) and src/net/session.ts, driven
// against a live relay — so a Godot client and a TS client can play one
// lockstep match and compare notes. Used by godot/tests/test_net_crossplay.gd.
//
//   npx tsx scripts/netplay-client.ts <ws url> <room> <name> <ticks> <result.json> [faction]
//
// Joins <room> through the lobby's "Join a Server" form, picks a faction,
// readies up, and waits for the host to start. Then it simulates <ticks>
// ticks under lockstep, issuing the same scripted orders the Godot tests use,
// and writes {ok, team, tick, sums: {tick: checksum}, desynced, factions}.

import { writeFileSync } from "fs";
import { NetLobby, NetStart } from "../src/ui/net_lobby";
import { NetSession } from "../src/net/session";
import { World } from "../src/sim/world";
import { Kind, Team } from "../src/sim/types";
import { Command, worldChecksum } from "../src/sim/commands";
import { generateMap, PRESETS } from "../src/maps/generator";
import { deserialiseMap, toMapData } from "../src/maps/custom";
import { RNG } from "../src/engine/rng";
import { FACTIONS, FACTION_IDS } from "../src/content/factions";

const [url, room, name, ticksArg, outFile, faction = ""] = process.argv.slice(2);
const TICKS = Number(ticksArg) || 300;

// --- A minimal DOM, just enough to run NetLobby headlessly -------------------
class FakeEl {
  tag: string; id = ""; children: FakeEl[] = []; _html = "";
  style: Record<string, string> = { cssText: "", opacity: "" };
  value = ""; placeholder = ""; textContent = ""; disabled = false; readOnly = false; spellcheck = false; title = ""; maxLength = 0;
  onclick: (() => void) | null = null; oninput: (() => void) | null = null; onkeydown: ((e: unknown) => void) | null = null;
  scrollTop = 0;
  constructor(tag: string) { this.tag = tag; }
  set innerHTML(v: string) { this._html = v; this.children = []; }
  get innerHTML(): string { return this._html; }
  appendChild(c: FakeEl) { this.children.push(c); return c; }
  append(...xs: (FakeEl | string)[]) {
    for (const x of xs) {
      if (typeof x === "string") { const t = new FakeEl("#text"); t.textContent = x; this.children.push(t); }
      else this.children.push(x);
    }
  }
  remove() { /* */ }
  select() { /* */ }
  querySelector(sel: string): FakeEl | null { const id = sel.replace("#", ""); return walk(this).find((e) => e.id === id) ?? null; }
}
function walk(el: FakeEl): FakeEl[] {
  const out: FakeEl[] = [el];
  for (const c of el.children) out.push(...walk(c));
  return out;
}
const body = new FakeEl("body");
(globalThis as unknown as { document: unknown }).document = { createElement: (t: string) => new FakeEl(t), body };
const store: Record<string, string> = {};
(globalThis as unknown as { localStorage: unknown }).localStorage = {
  getItem: (k: string) => store[k] ?? null, setItem: (k: string, v: string) => { store[k] = v; }, removeItem: (k: string) => { delete store[k]; },
};
const overlay = () => body.children[body.children.length - 1];
const button = (label: string) => walk(overlay()).find((e) => e.tag === "button" && e.textContent.includes(label));
const inputs = () => walk(overlay()).filter((e) => e.tag === "input");
const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

function fail(msg: string): never {
  writeFileSync(outFile, JSON.stringify({ ok: false, error: msg }));
  process.exit(1);
}

/** The world a NetStart describes: as main.ts startNetMatch builds it (fair, all-Common loadouts). */
function worldFor(start: NetStart): World {
  const { seed, numTeams, alliances, teams } = start;
  const custom = start.map?.code ? deserialiseMap(start.map.code) : null;
  const map = custom
    ? toMapData(custom, seed, numTeams, false)
    : generateMap(PRESETS.some((p) => p.id === start.map?.id) || start.map?.id === "random" ? start.map!.id : "open_plains", seed, numTeams, false, alliances);
  const world = new World(seed);
  const rng = new RNG((seed ^ 0xfac7105) >>> 0);
  const factions = teams.map((_, t) => {
    const roll = FACTION_IDS[rng.int(0, FACTION_IDS.length - 1)];
    const f = start.factions?.[t];
    return f && f in FACTIONS ? f : roll;
  });
  world.init(map, teams.map(() => ({})), teams.map(() => 1), alliances, teams.map(() => ""), false, undefined, "conquest", factions);
  return world;
}

/** The scripted orders of godot/tests/net_test_util.gd ordersFor, line for line. */
function ordersFor(world: World, team: number, tick: number): Command[] {
  if (tick % 15 !== (team * 3) % 15) return [];
  const ids = world.entitiesOf(team as Team, Kind.Unit).map((e) => e.id);
  const out: Command[] = [{
    t: "move", team: team as Team, ids,
    x: 500 + ((tick * 37 + team * 211) % 1400) + tick / 7 + 0.123456789 * (team + 1),
    y: 500 + ((tick * 53) % 1400) + 1 / 3,
    queue: false, attackMove: tick % 2 === 0,
  }];
  if (tick % 60 === 0) {
    const tc = world.entities.find((e) => e.alive && e.team === team && e.type === "town_center");
    if (tc) out.push({ t: "train", team: team as Team, buildingId: tc.id, unit: "villager" });
  }
  return out;
}

/** Every living entity's exact position, all 64 bits (net_test_util.gd exactHash). */
function exactHash(world: World): number {
  let h = 2166136261 >>> 0;
  for (const e of world.entities) {
    if (!e.alive) continue;
    const bytes = new Uint8Array(new Float64Array([e.id, e.x, e.y, e.hp]).buffer);
    for (const b of bytes) h = Math.imul(h ^ b, 16777619) >>> 0;
  }
  return h;
}

async function main() {
  let started: NetStart | null = null;
  const lobby = new NetLobby();
  lobby.open((s) => { started = s; }, { name, factions: FACTION_IDS as unknown as string[], faction });
  button("Join a Server")!.onclick!();
  const [u, nm, rm] = inputs();
  u.value = url; nm.value = name; rm.value = room;
  button("Connect")!.onclick!();
  for (let i = 0; i < 200 && !button("Ready"); i++) await wait(25);
  if (!button("Ready")) fail("never reached the room");
  button("Ready")!.onclick!();
  for (let i = 0; i < 1200 && !started; i++) await wait(25);
  if (!started) fail("the host never started");
  const s = started as NetStart;
  const world = worldFor(s);
  try { s.transport.send({ t: "factions", list: world.players.map((p) => p.faction) }); } catch { /* */ }
  const session = new NetSession(s.transport, s.localTeam, s.teams, s.observer);
  session.attach(world, 5);
  const lock = session.lock!;
  // Real time, as the game runs it: author a turn whenever we're not too far
  // ahead, step whatever has arrived.
  let doneAt = 0;
  const exact: Record<number, number> = {};
  await new Promise<void>((resolve) => {
    const timer = setInterval(() => {
      if (session.authorLead < 12) {
        for (const c of ordersFor(world, s.localTeam, lock.nextAuthorTick)) lock.localCommand(c);
        session.authorTick();
      }
      for (let i = 0; i < 20 && session.stepReady(1); i++) {
        if (lock.currentTick % 40 === 0) exact[lock.currentTick] = exactHash(world);
      }
      if (lock.currentTick >= TICKS && !doneAt) doneAt = Date.now();
      // Keep authoring a while after, so the other side can finish too.
      if (doneAt && Date.now() - doneAt > 2500) { clearInterval(timer); resolve(); }
    }, 3);
  });
  const sums: Record<number, number> = {};
  for (const [t, v] of (session as unknown as { localSums: Map<number, number> }).localSums) sums[t] = v;
  writeFileSync(outFile, JSON.stringify({
    ok: true, team: s.localTeam, tick: lock.currentTick, sums, exact, desynced: session.desynced,
    checksum: worldChecksum(world), factions: world.players.map((p) => p.faction),
  }));
  s.transport.close();
  process.exit(0);
}
// Never outstay the test that started us (a host that went away leaves the
// lockstep waiting forever).
setTimeout(() => fail("timed out"), 150_000);
main().catch((e) => fail(String(e?.stack || e)));
