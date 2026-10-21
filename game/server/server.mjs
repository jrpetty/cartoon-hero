// Banner & Blade — the game's website and its multiplayer server, in one
// zero-dependency Node file.
//
// Runs on plain Node (>=18), no `npm install` needed. It does three jobs:
//
//   1. Serves the game itself at `/` (the single-file build), so a player just
//      opens the site — the page then talks to this same server over a
//      same-origin WebSocket, with nothing to type.
//   2. Runs the online hub: a live list of rooms, creating and joining them,
//      quick-match queues that make a room when enough people are waiting,
//      and a lobby per room — teams, factions, battlefield, chat, kick.
//   3. Relays each match. Games run in deterministic lockstep, so the server
//      never simulates anything: it forwards each client's turns and
//      checksums, which keeps it tiny and cheap even for 8v8. Ranked quick
//      matches report their winner here; when the players agree, ratings
//      move and the leaderboard updates.
//
//   node server/server.mjs [port]        (default 8787, binds 0.0.0.0)

import http from "http";
import crypto from "crypto";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import zlib from "zlib";

const MAX_PLAYERS = 16; // 8v8
const MAX_TEAMS = 8;
const FACTIONS = ["kingdom", "legion", "norse", "shogunate", "khanate", "ascendancy"];
/** A map code is a few KB; anything this big is not a map. */
const MAX_MAP_CODE = 256 * 1024;
const MAX_POOL = 500;
/** Bump when the wire protocol changes incompatibly; old pages are told to reload. */
export const PROTOCOL = 2;
/** Room names people can see; also how many rooms a server holds at most. */
const MAX_ROOMS = 400;
/** Heartbeat: ping every socket this often, drop any silent for IDLE_MS. */
const PING_MS = 15000;
const IDLE_MS = 45000;
/** Per-socket message budget (token bucket). Lockstep sends ~20 turns a second. */
const RATE_BURST = 240;
const RATE_PER_SEC = 120;
/** Quick-match queues: how many players make a room, and how they are split. */
const QUEUES = {
  "1v1": { size: 2, layout: 2, label: "Quick 1 v 1" },
  "2v2": { size: 4, layout: 2, label: "Quick 2 v 2" },
  "ffa4": { size: 4, layout: 0, label: "Quick 4-player free-for-all" },
};
/** Built-in battlefields a quick match rolls from (ids the client knows). */
const QUICK_MAPS = [
  { id: "open_plains", name: "Open Plains" }, { id: "black_forest", name: "Black Forest" },
  { id: "riverlands", name: "Riverlands" }, { id: "highlands", name: "Highlands" },
  { id: "crossroads", name: "Crossroads" }, { id: "gauntlet", name: "Gauntlet" },
];
const QUICK_COUNTDOWN_MS = 4000;
const START_RATING = 1000;
const ELO_K = 32;
const WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

// --- RFC6455 framing (text only; ping/pong/close handled) -------------------
function frameParser(onText, onClose, onPing) {
  let buf = Buffer.alloc(0);
  let fragOp = 0;
  let frags = [];
  return (chunk) => {
    buf = Buffer.concat([buf, chunk]);
    for (;;) {
      if (buf.length < 2) return;
      const b0 = buf[0], b1 = buf[1];
      const fin = (b0 & 0x80) !== 0;
      const opcode = b0 & 0x0f;
      const masked = (b1 & 0x80) !== 0;
      let len = b1 & 0x7f;
      let off = 2;
      if (len === 126) { if (buf.length < off + 2) return; len = buf.readUInt16BE(off); off += 2; }
      else if (len === 127) { if (buf.length < off + 8) return; len = Number(buf.readBigUInt64BE(off)); off += 8; }
      let mask;
      if (masked) { if (buf.length < off + 4) return; mask = buf.subarray(off, off + 4); off += 4; }
      if (len > 1024 * 1024) { onClose(); return; } // nothing we speak is this big
      if (buf.length < off + len) return;
      let payload = buf.subarray(off, off + len);
      if (masked) {
        const out = Buffer.allocUnsafe(len);
        for (let i = 0; i < len; i++) out[i] = payload[i] ^ mask[i & 3];
        payload = out;
      }
      buf = buf.subarray(off + len);
      if (opcode === 0x8) { onClose(); return; }
      if (opcode === 0x9) { onPing(payload); continue; }
      if (opcode === 0xA) continue; // pong
      if (opcode === 0x0) frags.push(payload);
      else { fragOp = opcode; frags = [payload]; }
      if (fin) {
        const full = Buffer.concat(frags);
        frags = [];
        if (fragOp === 0x1) onText(full.toString("utf8"));
      }
    }
  };
}

function encode(str, opcode = 0x1) {
  const data = Buffer.from(str, "utf8");
  const len = data.length;
  let header;
  if (len < 126) header = Buffer.from([0x80 | opcode, len]);
  else if (len < 65536) { header = Buffer.alloc(4); header[0] = 0x80 | opcode; header[1] = 126; header.writeUInt16BE(len, 2); }
  else { header = Buffer.alloc(10); header[0] = 0x80 | opcode; header[1] = 127; header.writeBigUInt64BE(BigInt(len), 2); }
  return Buffer.concat([header, data]);
}


// --- Helpers ----------------------------------------------------------------

const clip = (v, n) => String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, n);
const int = (v, lo, hi, d) => { const n = Math.floor(Number(v)); return Number.isFinite(n) ? Math.max(lo, Math.min(hi, n)) : d; };
const here = path.dirname(fileURLToPath(import.meta.url));

/**
 * A JSON file of records keyed by id, loaded once and written back a moment
 * after each change. If the disk is read-only it simply lives in memory.
 */
function jsonStore(file) {
  const map = new Map();
  try {
    const arr = JSON.parse(fs.readFileSync(file, "utf8"));
    if (Array.isArray(arr)) for (const v of arr) if (v && typeof v.id === "string") map.set(v.id, v);
  } catch { /* nothing saved yet */ }
  let timer = null;
  // Write to a temporary file and rename it over the real one, so a crash or
  // a deploy mid-write can never leave half a file behind.
  const flush = () => {
    timer = null;
    try {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      const tmp = `${file}.${process.pid}.tmp`;
      fs.writeFileSync(tmp, JSON.stringify([...map.values()]));
      fs.renameSync(tmp, file);
    } catch (e) { warnOnce(`can't write ${file} (${e.code || e.message}) — keeping it in memory`); }
  };
  return {
    map,
    save() { if (!timer) timer = setTimeout(flush, 1000); },
    flush() { if (timer) { clearTimeout(timer); flush(); } },
  };
}

/**
 * An append-only log of JSON lines (the match history). Everything is also
 * kept in memory — the newest `cap` records — for the admin dashboard.
 */
function jsonLog(file, cap = 100000) {
  const items = [];
  try {
    for (const line of fs.readFileSync(file, "utf8").split("\n")) {
      if (!line.trim()) continue;
      try { items.push(JSON.parse(line)); } catch { /* a torn last line */ }
    }
  } catch { /* nothing yet */ }
  if (items.length > cap) items.splice(0, items.length - cap);
  let pending = "";
  let timer = null;
  const flush = () => {
    timer = null;
    if (!pending) return;
    const chunk = pending;
    pending = "";
    try { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.appendFileSync(file, chunk); }
    catch (e) { warnOnce(`can't write ${file} (${e.code || e.message}) — keeping it in memory`); }
  };
  return {
    items,
    add(rec) {
      items.push(rec);
      if (items.length > cap) items.shift();
      pending += JSON.stringify(rec) + "\n";
      if (!timer) timer = setTimeout(flush, 1000);
    },
    flush() { if (timer) clearTimeout(timer); flush(); },
  };
}

const warned = new Set();
function warnOnce(msg) { if (warned.has(msg)) return; warned.add(msg); if (!process.env.VITEST) console.warn(msg); }

// --- One server instance ----------------------------------------------------
//
// Everything a running server knows lives in here, so tests can start several
// side by side without them sharing rooms.

function createHub(opts) {
  const maps = jsonStore(opts.mapsFile || process.env.MAPS_FILE || path.join(here, "community-maps.json"));
  const players = jsonStore(opts.playersFile || process.env.PLAYERS_FILE || path.join(here, "players.json"));
  /** @type {Map<string, any>} */
  const rooms = new Map();
  /** Every live connection. */
  const conns = new Set();
  const queues = Object.fromEntries(Object.keys(QUEUES).map((k) => [k, []]));
  let quickCount = 0;
  // The match history sits beside the other data unless told otherwise.
  const matchesPath = opts.matchesFile || process.env.MATCHES_FILE || path.join(opts.mapsFile ? path.dirname(opts.mapsFile) : here, "matches.jsonl");
  const matchLog = jsonLog(matchesPath);
  let matchSeq = matchLog.items.length ? Math.max(0, ...matchLog.items.slice(-200).map((m) => m.n || 0)) : 0;

  // Live counters for the admin dashboard: since this process started.
  const counters = {
    bootAt: Date.now(), connections: 0, peakOnline: 0, peakAt: 0, messages: 0, bytesIn: 0, relayed: 0,
    chats: 0, rateLimited: 0, heartbeatDrops: 0, errors: 0, desyncs: 0, roomsCreated: 0, quickMatched: 0,
    kicks: 0, published: 0, lastError: "",
  };
  // One sample a minute, a month of them: who was online, what was running.
  // Kept on disk beside the match log, so the chart survives a deploy.
  const sampleLog = jsonLog(opts.samplesFile || process.env.SAMPLES_FILE || path.join(path.dirname(matchesPath), "samples.jsonl"), 30 * 24 * 60);
  const samples = sampleLog.items;
  let lastSample = { messages: 0, bytesIn: 0, relayed: 0 };
  function sample() {
    const inGames = [...rooms.values()].filter((r) => r.started).reduce((n, r) => n + playersOf(r).length, 0);
    sampleLog.add({
      t: Date.now(), online: conns.size, games: [...rooms.values()].filter((r) => r.started).length, inGames,
      rooms: rooms.size, queued: Object.values(queues).reduce((n, q) => n + q.length, 0),
      msgs: counters.messages - lastSample.messages, bytes: counters.bytesIn - lastSample.bytesIn, relayed: counters.relayed - lastSample.relayed,
    });
    lastSample = { messages: counters.messages, bytesIn: counters.bytesIn, relayed: counters.relayed };
  }
  // Event-loop lag: how late a 500 ms timer fires. The number that says the
  // machine is too small before players notice.
  let lag = 0, lagMax = 0, lagAt = Date.now();
  const lagTimer = setInterval(() => {
    const now = Date.now();
    lag = Math.max(0, now - lagAt - 500);
    lagMax = Math.max(lagMax * 0.98, lag);
    lagAt = now;
  }, 500);
  lagTimer.unref?.();
  const sampler = setInterval(sample, opts.sampleMs ?? 60000);
  sampler.unref?.();
  // A first point straight away, so a fresh deploy's chart isn't empty.
  const firstSample = setTimeout(sample, Math.min(5000, opts.sampleMs ?? 5000));
  firstSample.unref?.();

  // ---- community maps ----
  function poolMeta(m) { const { code, ...meta } = m; void code; return meta; }
  function publishMap(raw) {
    const code = String(raw?.code ?? "");
    if (!code.startsWith("BBMAP") || code.length > MAX_MAP_CODE) return null;
    const id = "cm_" + crypto.createHash("sha1").update(code).digest("hex").slice(0, 12);
    const prev = maps.map.get(id);
    const entry = {
      id,
      name: clip(raw.name, 40) || "Untitled map",
      author: clip(raw.author, 24) || "Unknown",
      desc: clip(raw.desc, 160),
      minPlayers: int(raw.minPlayers, 1, MAX_PLAYERS, 2),
      maxPlayers: int(raw.maxPlayers, 1, MAX_PLAYERS, 8),
      cols: int(raw.cols, 16, 400, 128),
      published: prev?.published ?? Date.now(),
      code,
    };
    maps.map.set(id, entry);
    // Oldest out first once the pool is full.
    while (maps.map.size > MAX_POOL) maps.map.delete([...maps.map.values()].sort((a, b) => a.published - b.published)[0].id);
    maps.save(); maps.flush(); // rare, and worth not losing
    return entry;
  }
  const poolList = () => [...maps.map.values()].sort((a, b) => b.published - a.published).map(poolMeta);

  // ---- players and ratings ----
  // A player is a random id their browser keeps, plus a name. There are no
  // passwords: the id is the account. Ratings only move in ranked (quick
  // match) games, and only when the players' own reports of the winner agree.
  function playerRec(pid, name) {
    if (!pid) return null;
    let p = players.map.get(pid);
    if (!p) { p = { id: pid, name: name || "Player", rating: START_RATING, wins: 0, losses: 0, games: 0, seen: Date.now(), first: Date.now(), played: 0 }; players.map.set(pid, p); }
    if (name) p.name = name;
    p.seen = Date.now();
    players.save();
    return p;
  }
  const publicPlayer = (p) => ({ name: p.name, rating: Math.round(p.rating), wins: p.wins, losses: p.losses, games: p.games });
  function leaderboard(n = 50) {
    return [...players.map.values()].filter((p) => p.games > 0).sort((a, b) => b.rating - a.rating).slice(0, n).map(publicPlayer);
  }

  // ---- rooms ----
  function newRoom(name, o = {}) {
    const r = {
      name, clients: [], started: false, slotTeams: new Map(), pass: o.pass || "", layout: o.layout ?? 2,
      map: o.map || { id: "open_plains", name: "Open Plains" }, max: o.max || MAX_PLAYERS, ranked: !!o.ranked,
      quick: o.quick || "", created: Date.now(), teamPids: [], teamAlliance: [], reports: new Map(), resolved: false,
    };
    rooms.set(name, r);
    return r;
  }
  const host = (r) => r.clients.filter((c) => !c.observer).reduce((h, c) => (h === null || c.slot < h.slot ? c : h), null);
  const playersOf = (r) => r.clients.filter((c) => !c.observer).sort((a, b) => a.slot - b.slot);

  /** Seats in join order, in blocks: 8 players in 2 teams is 1–4 v 5–8. 0 = free-for-all. */
  function applyLayout(r) {
    const ps = playersOf(r);
    if (r.layout === 0) { for (const c of ps) c.team = 0; return; }
    const k = Math.max(1, Math.min(r.layout, ps.length));
    const base = Math.floor(ps.length / k), extra = ps.length % k;
    let i = 0;
    for (let t = 0; t < k; t++) {
      const size = base + (t < extra ? 1 : 0);
      for (let j = 0; j < size; j++) ps[i++].team = t + 1;
    }
  }
  function openTeam(r) {
    if (r.layout === 0) return 0;
    const counts = Array.from({ length: r.layout }, (_, t) => playersOf(r).filter((c) => c.team === t + 1).length);
    let best = 0;
    counts.forEach((n, t) => { if (n < counts[best]) best = t; });
    return best + 1;
  }
  function sidesOf(r) {
    const seen = new Set();
    for (const c of playersOf(r)) seen.add(c.team > 0 ? `t${c.team}` : `s${c.slot}`);
    return seen.size;
  }
  function formatOf(r) {
    const ps = playersOf(r);
    if (ps.length < 2) return "";
    const groups = new Map();
    for (const c of ps) { const k = c.team > 0 ? `t${c.team}` : `s${c.slot}`; groups.set(k, (groups.get(k) || 0) + 1); }
    const sizes = [...groups.values()].sort((a, b) => b - a);
    return sizes.every((n) => n === 1) ? (ps.length === 2 ? "1 v 1" : `${ps.length}-player FFA`) : sizes.join(" v ");
  }
  function lobbyState(r) {
    const h = host(r);
    return {
      t: "lobby",
      room: r.name,
      host: h ? h.slot : -1,
      started: r.started,
      layout: r.layout,
      max: r.max,
      ranked: r.ranked,
      quick: r.quick,
      locked: !!r.pass,
      map: { id: r.map.id, name: r.map.name, author: r.map.author || "", minPlayers: r.map.minPlayers, maxPlayers: r.map.maxPlayers },
      players: r.clients.map((c) => ({
        slot: c.slot, name: c.name, team: c.team, side: c.team > 0 ? (c.team - 1) % 2 : c.slot % 2,
        faction: c.faction, ready: c.ready, observer: c.observer, rating: c.conn.rec ? Math.round(c.conn.rec.rating) : undefined,
      })),
    };
  }
  function roomSummary(r) {
    const h = host(r);
    return {
      name: r.name, host: h?.name ?? "", players: playersOf(r).length, observers: r.clients.length - playersOf(r).length,
      max: r.max, map: r.map.name, format: formatOf(r), locked: !!r.pass, started: r.started, ranked: r.ranked, quick: !!r.quick,
    };
  }
  const roomList = () => [...rooms.values()].filter((r) => !r.quick || r.started).sort((a, b) => a.created - b.created).map(roomSummary);

  function broadcast(r, obj, except) {
    const frame = encode(JSON.stringify(obj));
    for (const c of r.clients) if (c.socket !== except) { try { c.socket.write(frame); } catch { /* */ } }
  }

  // The hub's room list, pushed to everyone browsing it — at most a few times
  // a second however busy the server is.
  let hubTimer = null;
  function hubChanged() {
    if (hubTimer) return;
    hubTimer = setTimeout(() => {
      hubTimer = null;
      const msg = encode(JSON.stringify({ t: "rooms", rooms: roomList(), online: conns.size }));
      for (const c of conns) if (c.inHub && !c.room) { try { c.socket.write(msg); } catch { /* */ } }
    }, 150);
  }

  function startMatch(r) {
    const ps = playersOf(r);
    if (r.started || ps.length < 2 || sidesOf(r) < 2) return false;
    r.started = true;
    if (r.countdown) { clearTimeout(r.countdown); r.countdown = null; }
    // Allies get consecutive team indices (and so adjacent map starts): sort by
    // team — solo players last — then by join order.
    const key = (c) => (c.team > 0 ? c.team : MAX_TEAMS + 1 + c.slot);
    const ordered = ps.sort((a, b) => key(a) - key(b) || a.slot - b.slot);
    const alliances = [], factions = [], slotTeams = [];
    let solo = MAX_TEAMS;
    ordered.forEach((c, team) => {
      alliances[team] = c.team > 0 ? c.team - 1 : solo++;
      factions[team] = c.faction || "";
      slotTeams.push({ slot: c.slot, team });
      r.slotTeams.set(c.slot, team);
      r.teamPids[team] = c.conn.pid || "";
    });
    r.teamAlliance = alliances;
    r.reports = new Map();
    r.times = [];
    r.resolved = false;
    r.match = {
      id: crypto.randomBytes(6).toString("hex"), n: ++matchSeq, room: r.name,
      kind: r.quick ? `quick-${r.quick}` : "custom", ranked: r.ranked,
      map: { id: r.map.id, name: r.map.name, custom: !!r.map.code, author: r.map.author || "" },
      players: ordered.length, format: formatOf(r),
      teams: ordered.map((c, team) => ({ alliance: alliances[team], faction: c.faction || "", chose: !!c.faction, name: c.name, pid: c.conn.pid || "" })),
      observers: r.clients.length - ordered.length,
      startedAt: Date.now(), endedAt: 0, realSec: 0, gameSec: 0, winner: null, outcome: "", quitters: 0, desync: false,
    };
    for (const c of ordered) { const rec = c.conn.rec; if (rec) { rec.played = (rec.played || 0) + 1; } }
    players.save();
    const seed = (Math.random() * 1e9) | 0;
    broadcast(r, { t: "start", seed, numTeams: ordered.length, alliances, slotTeams, factions, names: ordered.map((c) => c.name), map: r.map, ranked: r.ranked });
    log(`[room ${r.name}] started: ${ordered.length} players on ${r.map.name}${r.ranked ? " (ranked)" : ""}`);
    hubChanged();
    return true;
  }

  // ---- results & ratings ----
  // Every client simulates the same match, so every client knows who won.
  // Each reports it; once everyone still connected has, the most-reported
  // alliance wins. Disagreement (someone lying) means no result at all.
  function report(r, conn, m) {
    if (!r.started || r.resolved || !conn.client || conn.client.observer) return;
    const alliance = r.teamAlliance[int(m.winner, 0, 63, -1)];
    if (alliance === undefined) return;
    r.reports.set(conn.client.slot, alliance);
    const t = Number(m.time);
    if (Number.isFinite(t) && t > 0 && t < 24 * 3600) r.times.push(t);
    noteFactions(r, m.factions);
    maybeResolve(r);
  }
  /** The factions the match actually used ("Random" resolved), as a client saw them. */
  function noteFactions(r, list) {
    if (!r.match || r.match.factionsKnown || !Array.isArray(list) || list.length !== r.match.teams.length) return;
    if (!list.every((f) => FACTIONS.includes(f))) return;
    list.forEach((f, i) => { r.match.teams[i].faction = f; });
    r.match.factionsKnown = true;
  }
  /** Close the match's record: won, disputed or abandoned. */
  function recordMatch(r, outcome, winner) {
    const m = r.match;
    if (!m || m.endedAt) return;
    m.endedAt = Date.now();
    m.realSec = Math.round((m.endedAt - m.startedAt) / 1000);
    m.gameSec = r.times.length ? Math.round(Math.max(...r.times)) : m.realSec;
    m.outcome = outcome;
    m.winner = winner ?? null;
    const rec = { ...m, teams: m.teams.map((t) => ({ ...t, pid: t.pid ? crypto.createHash("sha256").update(t.pid).digest("hex").slice(0, 12) : "" })) };
    matchLog.add(rec);
  }
  function maybeResolve(r, final = false) {
    if (r.resolved || !r.started) return;
    const live = playersOf(r).map((c) => c.slot);
    // Wait for everyone still here — unless nobody is.
    if (!final && (!r.reports.size || live.some((s) => !r.reports.has(s)))) return;
    r.resolved = true;
    if (!r.reports.size) { recordMatch(r, "abandoned"); return; }
    const tally = new Map();
    for (const a of r.reports.values()) tally.set(a, (tally.get(a) || 0) + 1);
    const top = [...tally.entries()].sort((a, b) => b[1] - a[1]);
    if (top.length > 1 && top[0][1] === top[1][1]) { recordMatch(r, "disputed"); log(`[room ${r.name}] result disputed — not rated`); return; }
    const winner = top[0][0];
    recordMatch(r, "won", winner);
    if (!r.ranked) return;
    const win = [], lose = [];
    r.teamAlliance.forEach((a, team) => {
      const p = players.map.get(r.teamPids[team]);
      if (p) (a === winner ? win : lose).push(p);
    });
    if (!win.length || !lose.length) return;
    const avg = (ps) => ps.reduce((s, p) => s + p.rating, 0) / ps.length;
    const expected = 1 / (1 + 10 ** ((avg(lose) - avg(win)) / 400));
    const delta = ELO_K * (1 - expected);
    for (const p of win) { p.rating += delta; p.wins++; p.games++; }
    for (const p of lose) { p.rating -= delta; p.losses++; p.games++; }
    players.save();
    broadcast(r, { t: "rated", delta: Math.round(delta), winner });
    log(`[room ${r.name}] rated: ±${Math.round(delta)}`);
  }

  // ---- joining and leaving ----
  function uniqueName(r, name) {
    const taken = new Set(r.clients.map((c) => c.name));
    if (!taken.has(name)) return name;
    for (let i = 2; ; i++) if (!taken.has(`${name} (${i})`)) return `${name} (${i})`;
  }
  function joinRoom(conn, roomName, o) {
    const name = clip(roomName, 32) || "main";
    const pass = clip(o.pass, 64);
    let r = rooms.get(name);
    if (r && (r.pass || "") !== pass) return conn.send({ t: "error", code: "password", msg: "Wrong room password." });
    if (r && r.started) return conn.send({ t: "error", code: "started", msg: "That match is already in progress." });
    if (r && r.quick && !o.quick) return conn.send({ t: "error", msg: "That's a quick-match room." });
    if (r && playersOf(r).length >= r.max && !o.observer) return conn.send({ t: "error", code: "full", msg: `Room is full (${r.max} players).` });
    if (r && r.clients.length >= MAX_PLAYERS + 8) return conn.send({ t: "error", code: "full", msg: "Room is full." });
    if (!r) {
      if (rooms.size >= MAX_ROOMS) return conn.send({ t: "error", msg: "The server is full — try again shortly." });
      r = newRoom(name, { pass, max: int(o.max, 2, MAX_PLAYERS, MAX_PLAYERS), layout: o.layout, map: o.map, ranked: o.ranked, quick: o.quick });
      if (o.quick) counters.quickMatched++; else counters.roomsCreated++;
    }
    if (conn.room) leaveRoom(conn);
    unqueue(conn);
    const used = new Set(r.clients.map((c) => c.slot));
    let slot = 0; while (used.has(slot)) slot++;
    const me = {
      socket: conn.socket, conn, slot, name: uniqueName(r, clip(o.name, 24) || conn.name || `Player ${slot + 1}`),
      team: 0, faction: FACTIONS.includes(o.faction) ? o.faction : "", ready: false, observer: !!o.observer, send: conn.send,
    };
    if (!me.observer) me.team = openTeam(r);
    r.clients.push(me);
    conn.room = r;
    conn.client = me;
    conn.send({ t: "welcome", slot, room: r.name, max: r.max, observer: me.observer, protocol: PROTOCOL });
    broadcast(r, lobbyState(r));
    hubChanged();
    return r;
  }
  function leaveRoom(conn, why) {
    const r = conn.room, me = conn.client;
    conn.room = null; conn.client = null;
    if (!r || !me) return;
    r.clients = r.clients.filter((c) => c !== me);
    if (r.started) {
      const team = r.slotTeams.get(me.slot);
      if (team !== undefined) broadcast(r, { t: "drop", team }); // keep the sim alive
      if (!me.observer && !r.resolved && r.match) r.match.quitters++;
      maybeResolve(r, playersOf(r).length === 0);
    } else {
      // A quick-match room someone walks out of before it starts is off.
      if (r.quick && r.countdown) {
        clearTimeout(r.countdown); r.countdown = null;
        broadcast(r, { t: "cancelled", msg: "Someone left before the match began — back to the queue." });
      }
      broadcast(r, lobbyState(r));
    }
    if (r.clients.length === 0) {
      if (r.countdown) clearTimeout(r.countdown);
      if (r.started && !r.resolved) maybeResolve(r, true);
      rooms.delete(r.name);
    }
    if (why) conn.send({ t: "left", why });
    hubChanged();
  }

  // ---- quick match ----
  function unqueue(conn) {
    if (!conn.queue) return;
    const q = queues[conn.queue];
    const i = q.indexOf(conn);
    if (i >= 0) q.splice(i, 1);
    conn.queue = null;
    queueStatus();
  }
  function queueStatus() {
    for (const [mode, q] of Object.entries(queues)) {
      for (const c of q) c.send({ t: "queue", mode, waiting: q.length, need: QUEUES[mode].size });
    }
    hubChanged();
  }
  function enqueue(conn, mode, o) {
    if (!QUEUES[mode]) return conn.send({ t: "error", msg: "Unknown quick-match mode." });
    if (conn.room) leaveRoom(conn);
    unqueue(conn);
    conn.queue = mode;
    conn.queueFaction = FACTIONS.includes(o.faction) ? o.faction : "";
    queues[mode].push(conn);
    const spec = QUEUES[mode];
    // Enough waiting: make the room, seat them, count down, start.
    while (queues[mode].length >= spec.size) {
      const group = queues[mode].splice(0, spec.size);
      const map = QUICK_MAPS[Math.floor(Math.random() * QUICK_MAPS.length)];
      const name = `${spec.label} #${++quickCount}`;
      for (const c of group) {
        c.queue = null;
        joinRoom(c, name, { name: c.name, faction: c.queueFaction, quick: mode, layout: spec.layout, map, ranked: true, max: spec.size });
      }
      const r = rooms.get(name);
      if (!r) continue;
      r.layout = spec.layout;
      applyLayout(r);
      broadcast(r, lobbyState(r));
      broadcast(r, { t: "countdown", ms: QUICK_COUNTDOWN_MS });
      r.countdown = setTimeout(() => { r.countdown = null; if (!startMatch(r)) broadcast(r, { t: "cancelled", msg: "Not enough players left to start." }); }, opts.quickCountdownMs ?? QUICK_COUNTDOWN_MS);
    }
    queueStatus();
  }

  // ---- a connection ----
  function handleConn(socket) {
    const conn = {
      socket, pid: "", name: "", rec: null, room: null, client: null, inHub: false, queue: null, queueFaction: "",
      tokens: RATE_BURST, lastRefill: Date.now(), lastSeen: Date.now(), strikes: 0, chatAt: [],
      send: (obj) => { try { socket.write(encode(JSON.stringify(obj))); } catch { /* */ } },
    };
    conns.add(conn);
    counters.connections++;
    if (conns.size > counters.peakOnline) { counters.peakOnline = conns.size; counters.peakAt = Date.now(); }
    const send = conn.send;
    const r = () => conn.room;
    const me = () => conn.client;
    const isHost = () => r() && host(r()) === me();

    const identify = (m) => {
      if (typeof m.pid === "string" && /^[a-f0-9]{16,64}$/.test(m.pid)) conn.pid = m.pid;
      const nm = clip(m.name, 24);
      if (nm) conn.name = nm;
      if (conn.pid) conn.rec = playerRec(conn.pid, conn.name);
      if (m.v !== undefined && Number(m.v) !== PROTOCOL) {
        send({ t: "error", code: "version", msg: "The game has been updated — reload the page to play online." });
        return false;
      }
      return true;
    };

    const onText = (text) => {
      conn.lastSeen = Date.now();
      // Rate limit: a bucket that refills steadily. A client that keeps
      // overrunning it is not a game client, and is disconnected.
      const now = Date.now();
      conn.tokens = Math.min(RATE_BURST, conn.tokens + ((now - conn.lastRefill) / 1000) * RATE_PER_SEC);
      conn.lastRefill = now;
      counters.messages++;
      counters.bytesIn += text.length;
      if (conn.tokens < 1) { counters.rateLimited++; if (++conn.strikes > RATE_BURST) onClose(); return; }
      conn.tokens -= 1;
      let m;
      try { m = JSON.parse(text); } catch { return; }
      if (!m || typeof m.t !== "string") return;
      switch (m.t) {
        // The old direct way in: straight into a named room.
        case "hello": {
          if (!identify(m)) return;
          joinRoom(conn, m.room || "main", m);
          break;
        }
        // The hub: browse rooms, queue, create, join.
        case "hub": {
          if (!identify(m)) return;
          if (conn.room) leaveRoom(conn);
          conn.inHub = true;
          send({ t: "rooms", rooms: roomList(), online: conns.size, protocol: PROTOCOL, me: conn.rec ? publicPlayer(conn.rec) : null });
          break;
        }
        case "rename": {
          identify({ name: m.name });
          if (conn.client && !r().started) { conn.client.name = uniqueName(r(), conn.name); broadcast(r(), lobbyState(r())); }
          break;
        }
        case "create": {
          if (!identify(m)) return;
          const base = clip(m.room, 32) || `${conn.name || "Player"}'s room`;
          let name = base;
          for (let i = 2; rooms.has(name); i++) name = `${base} ${i}`;
          joinRoom(conn, name, { ...m, max: m.max });
          break;
        }
        case "join": {
          if (!identify(m)) return;
          if (!rooms.has(clip(m.room, 32))) { send({ t: "error", code: "gone", msg: "That room has closed." }); break; }
          joinRoom(conn, m.room, m);
          break;
        }
        case "leave": {
          if (conn.room) leaveRoom(conn, "left");
          conn.inHub = true;
          send({ t: "rooms", rooms: roomList(), online: conns.size, me: conn.rec ? publicPlayer(conn.rec) : null });
          break;
        }
        case "quick": {
          if (!identify(m)) return;
          conn.inHub = true;
          enqueue(conn, String(m.mode), m);
          break;
        }
        case "unquick": unqueue(conn); send({ t: "queue", mode: "", waiting: 0, need: 0 }); break;
        // Older clients pick a side, A or B: that is team 1 or 2.
        case "side": if (me() && !r().started && !me().observer && !r().quick) { me().team = m.side ? 2 : 1; broadcast(r(), lobbyState(r())); } break;
        // Join a team (0 = on your own). The host may move anyone by slot.
        case "team": {
          if (!me() || r().started || r().quick) break;
          const target = m.slot === undefined || m.slot === me().slot ? me() : isHost() ? r().clients.find((c) => c.slot === m.slot) : null;
          if (!target || target.observer) break;
          target.team = int(m.team, 0, MAX_TEAMS, 0);
          broadcast(r(), lobbyState(r()));
          break;
        }
        // Host: lay every seat out in blocks — 0 free-for-all, 2..8 teams.
        case "layout": {
          if (!me() || r().started || !isHost() || r().quick) break;
          const k = int(m.teams, 0, MAX_TEAMS, 2);
          r().layout = k === 1 ? 2 : k;
          applyLayout(r());
          broadcast(r(), lobbyState(r()));
          hubChanged();
          break;
        }
        case "faction": if (me() && !r().started) { me().faction = FACTIONS.includes(m.faction) ? m.faction : ""; broadcast(r(), lobbyState(r())); } break;
        // Host: the battlefield — a built-in preset by id, or a map carried as code.
        case "map": {
          if (!me() || r().started || !isHost() || !m.map || r().quick) break;
          const code = m.map.code ? String(m.map.code) : "";
          if (code && (!code.startsWith("BBMAP") || code.length > MAX_MAP_CODE)) break;
          r().map = {
            id: clip(m.map.id, 64) || "open_plains", name: clip(m.map.name, 40) || "Battlefield", author: clip(m.map.author, 24),
            ...(code ? { code, minPlayers: int(m.map.minPlayers, 1, MAX_PLAYERS, 1), maxPlayers: int(m.map.maxPlayers, 1, MAX_PLAYERS, MAX_PLAYERS) } : {}),
          };
          broadcast(r(), lobbyState(r()));
          hubChanged();
          break;
        }
        case "kick": {
          if (!me() || r().started || !isHost()) break;
          const target = r().clients.find((c) => c.slot === m.slot && c !== me());
          if (target) { leaveRoom(target.conn, "kicked"); target.conn.inHub = true; counters.kicks++; }
          break;
        }
        case "ready": if (me() && !r().started) { me().ready = !!m.ready; broadcast(r(), lobbyState(r())); } break;
        case "start": if (me() && isHost() && !r().quick) startMatch(r()); break;
        case "turn":
        case "sum": if (r() && r().started) { broadcast(r(), m, socket); counters.relayed += r().clients.length - 1; } break; // relay to others
        case "chat": {
          if (!r() || !me()) break;
          // At most five lines in five seconds, each a sentence or two.
          conn.chatAt = conn.chatAt.filter((t) => now - t < 5000);
          if (conn.chatAt.length >= 5) break;
          conn.chatAt.push(now);
          counters.chats++;
          broadcast(r(), { t: "chat", slot: me().slot, name: me().name, team: m.team, text: clip(m.text, 240) }, socket);
          break;
        }
        case "ping": if (r() && me()) broadcast(r(), { t: "ping", x: Number(m.x) || 0, y: Number(m.y) || 0, team: m.team, slot: me().slot }, socket); break;
        case "result": if (r()) report(r(), conn, m); break;
        case "factions": if (r() && r().started && me() && !me().observer) noteFactions(r(), m.list); break;
        case "desync": if (r() && r().match && !r().match.desync) { r().match.desync = true; counters.desyncs++; } break;
        // The community map pool: publish, list, fetch one.
        case "publish": {
          const entry = publishMap(m.map);
          if (entry) counters.published++;
          send(entry ? { t: "published", map: poolMeta(entry) } : { t: "error", msg: "That doesn't look like a map code." });
          if (entry) send({ t: "maps", maps: poolList() });
          break;
        }
        case "maps": send({ t: "maps", maps: poolList() }); break;
        case "getmap": {
          const e = maps.map.get(String(m.id));
          send(e ? { t: "mapcode", id: e.id, code: e.code, name: e.name, author: e.author } : { t: "error", msg: "That map is no longer on this server." });
          break;
        }
        case "leaderboard": send({ t: "leaderboard", top: leaderboard(int(m.n, 1, 100, 50)), me: conn.rec ? publicPlayer(conn.rec) : null }); break;
        default: break;
      }
    };

    let closed = false;
    const onClose = () => {
      if (closed) return;
      closed = true;
      try { socket.destroy(); } catch { /* */ }
      unqueue(conn);
      leaveRoom(conn);
      conns.delete(conn);
      hubChanged();
    };
    conn.close = onClose;

    const parse = frameParser(onText, onClose, (payload) => { conn.lastSeen = Date.now(); try { socket.write(encode(payload.toString("binary"), 0xA)); } catch { /* */ } });
    socket.on("data", (c) => {
      conn.lastSeen = Date.now();
      try { parse(c); }
      catch (e) { counters.errors++; counters.lastError = String(e?.message || e); onClose(); }
    });
    socket.on("close", onClose);
    socket.on("error", onClose);
  }

  // Heartbeat: browsers answer pings on their own, so a socket that has been
  // silent for IDLE_MS is gone (a closed laptop, a dropped Wi-Fi) and its seat
  // is freed rather than held forever.
  const beat = setInterval(() => {
    const now = Date.now();
    const ping = encode("", 0x9);
    for (const c of [...conns]) {
      try {
        if (now - c.lastSeen > (opts.idleMs ?? IDLE_MS)) { counters.heartbeatDrops++; c.close(); continue; }
        c.socket.write(ping);
      } catch (e) { counters.errors++; counters.lastError = String(e?.message || e); }
    }
  }, opts.pingMs ?? PING_MS);
  beat.unref?.();

  // ---- analytics for the admin dashboard ----
  const median = (xs) => { if (!xs.length) return 0; const a = [...xs].sort((x, y) => x - y); const m = a.length >> 1; return a.length % 2 ? a[m] : (a[m - 1] + a[m]) / 2; };
  const mean = (xs) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0);
  const pct = (a, b) => (b ? Math.round((a / b) * 1000) / 10 : 0);
  const LENGTH_BUCKETS = [[0, 5], [5, 10], [10, 15], [15, 20], [20, 30], [30, 45], [45, 60], [60, Infinity]];

  /** At most `n` points for a chart: bucket and keep each bucket's peak. */
  function thin(xs, n) {
    if (xs.length <= n) return xs;
    const step = xs.length / n, out = [];
    for (let i = 0; i < n; i++) {
      const b = xs.slice(Math.floor(i * step), Math.floor((i + 1) * step));
      out.push(b.reduce((m, x) => (x.online > m.online ? x : m), b[0]));
    }
    return out;
  }

  function analytics(rangeMs) {
    const now = Date.now();
    const from = rangeMs ? now - rangeMs : 0;
    const all = matchLog.items.filter((m) => m.startedAt >= from);
    const done = all.filter((m) => m.outcome === "won");
    const minutes = (m) => m.gameSec / 60;

    // Overview.
    const lengths = done.map(minutes);
    const overview = {
      matches: all.length, completed: done.length,
      abandoned: all.filter((m) => m.outcome === "abandoned").length,
      disputed: all.filter((m) => m.outcome === "disputed").length,
      ranked: all.filter((m) => m.ranked).length, quick: all.filter((m) => m.kind.startsWith("quick")).length,
      custom: all.filter((m) => m.kind === "custom").length,
      playerSlots: all.reduce((n, m) => n + m.players, 0),
      avgPlayers: Math.round(mean(all.map((m) => m.players)) * 10) / 10,
      avgMinutes: Math.round(mean(lengths) * 10) / 10, medianMinutes: Math.round(median(lengths) * 10) / 10,
      longestMinutes: Math.round(Math.max(0, ...lengths) * 10) / 10,
      quitRate: pct(all.filter((m) => m.quitters > 0).length, all.length),
      desyncRate: pct(all.filter((m) => m.desync).length, all.length),
      completionRate: pct(done.length, all.length),
    };

    // Factions: how often picked, how often they win, how long their games run.
    const fac = Object.fromEntries(FACTIONS.map((f) => [f, { faction: f, picks: 0, chosen: 0, wins: 0, games: 0, minutes: [], winMinutes: [], duelWins: 0, duels: 0 }]));
    let slotsKnown = 0;
    for (const m of all) for (const t of m.teams) if (fac[t.faction]) { fac[t.faction].picks++; slotsKnown++; if (t.chose) fac[t.faction].chosen++; }
    for (const m of done) {
      for (const t of m.teams) {
        const f = fac[t.faction];
        if (!f) continue;
        f.games++;
        f.minutes.push(minutes(m));
        if (t.alliance === m.winner) { f.wins++; f.winMinutes.push(minutes(m)); }
      }
    }
    // 1 v 1 head-to-head: the cleanest read of balance.
    const matchup = Object.fromEntries(FACTIONS.map((a) => [a, Object.fromEntries(FACTIONS.map((b) => [b, { wins: 0, games: 0 }]))]));
    for (const m of done) {
      if (m.players !== 2 || m.teams.length !== 2) continue;
      const [x, y] = m.teams;
      if (!fac[x.faction] || !fac[y.faction] || x.alliance === y.alliance) continue;
      fac[x.faction].duels++; fac[y.faction].duels++;
      matchup[x.faction][y.faction].games++; matchup[y.faction][x.faction].games++;
      if (m.winner === x.alliance) { matchup[x.faction][y.faction].wins++; fac[x.faction].duelWins++; }
      else if (m.winner === y.alliance) { matchup[y.faction][x.faction].wins++; fac[y.faction].duelWins++; }
    }
    const factions = Object.values(fac).map((f) => ({
      faction: f.faction, picks: f.picks, pickRate: pct(f.picks, slotsKnown), chosenRate: pct(f.chosen, f.picks),
      games: f.games, wins: f.wins, winRate: pct(f.wins, f.games),
      duels: f.duels, duelWinRate: pct(f.duelWins, f.duels),
      avgMinutes: Math.round(mean(f.minutes) * 10) / 10, avgWinMinutes: Math.round(mean(f.winMinutes) * 10) / 10,
    })).sort((a, b) => b.picks - a.picks);

    // Maps.
    const mapAgg = new Map();
    for (const m of all) {
      const k = m.map.id;
      const a = mapAgg.get(k) || { id: k, name: m.map.name, custom: m.map.custom, author: m.map.author, plays: 0, completed: 0, minutes: [], players: [] };
      a.plays++; a.players.push(m.players);
      if (m.outcome === "won") { a.completed++; a.minutes.push(minutes(m)); }
      mapAgg.set(k, a);
    }
    const mapsOut = [...mapAgg.values()].map((a) => ({
      id: a.id, name: a.name, custom: a.custom, author: a.author, plays: a.plays, share: pct(a.plays, all.length),
      completed: a.completed, avgMinutes: Math.round(mean(a.minutes) * 10) / 10, avgPlayers: Math.round(mean(a.players) * 10) / 10,
    })).sort((a, b) => b.plays - a.plays);

    // Game length: a histogram overall and by kind of match.
    const hist = (ls) => LENGTH_BUCKETS.map(([lo, hi]) => ({ label: hi === Infinity ? `${lo}+` : `${lo}–${hi}`, count: ls.filter((x) => x >= lo && x < hi).length }));
    const kinds = [...new Set(all.map((m) => m.kind))];
    const lengthsOut = {
      histogram: hist(lengths),
      byKind: kinds.map((k) => { const ls = done.filter((m) => m.kind === k).map(minutes); return { kind: k, games: all.filter((m) => m.kind === k).length, avgMinutes: Math.round(mean(ls) * 10) / 10, medianMinutes: Math.round(median(ls) * 10) / 10 }; })
        .sort((a, b) => b.games - a.games),
      byPlayers: [...new Set(done.map((m) => m.players))].sort((a, b) => a - b).map((n) => { const ls = done.filter((m) => m.players === n).map(minutes); return { players: n, games: ls.length, avgMinutes: Math.round(mean(ls) * 10) / 10 }; }),
    };

    // Formats (1 v 1, 2 v 2, 4-player FFA…).
    const fmt = new Map();
    for (const m of all) fmt.set(m.format || "?", (fmt.get(m.format || "?") || 0) + 1);
    const formats = [...fmt.entries()].map(([format, count]) => ({ format, count, share: pct(count, all.length) })).sort((a, b) => b.count - a.count);

    // Activity: matches per hour (48 h) and per day (30 d), and when people play.
    const HOUR = 3600e3, DAY = 24 * HOUR;
    const perHour = Array.from({ length: 48 }, (_, i) => ({ t: Math.floor(now / HOUR) * HOUR - (47 - i) * HOUR, matches: 0, players: 0 }));
    const perDay = Array.from({ length: 30 }, (_, i) => ({ t: Math.floor(now / DAY) * DAY - (29 - i) * DAY, matches: 0, players: 0, unique: new Set() }));
    const hourOfDay = Array.from({ length: 24 }, (_, h) => ({ hour: h, matches: 0 }));
    for (const m of matchLog.items) {
      const h = Math.floor(m.startedAt / HOUR) * HOUR, d = Math.floor(m.startedAt / DAY) * DAY;
      const ph = perHour.find((x) => x.t === h); if (ph) { ph.matches++; ph.players += m.players; }
      const pd = perDay.find((x) => x.t === d); if (pd) { pd.matches++; pd.players += m.players; for (const t of m.teams) if (t.pid) pd.unique.add(t.pid); }
      if (m.startedAt >= from) hourOfDay[new Date(m.startedAt).getUTCHours()].matches++;
    }

    // Players.
    const ps = [...players.map.values()];
    const ratings = ps.filter((p) => p.games > 0).map((p) => p.rating);
    const rb = [[0, 800], [800, 900], [900, 1000], [1000, 1100], [1100, 1200], [1200, 1300], [1300, 1400], [1400, Infinity]];
    const playersOut = {
      total: ps.length,
      active24h: ps.filter((p) => now - (p.seen || 0) < DAY).length,
      active7d: ps.filter((p) => now - (p.seen || 0) < 7 * DAY).length,
      active30d: ps.filter((p) => now - (p.seen || 0) < 30 * DAY).length,
      new24h: ps.filter((p) => now - (p.first || p.seen || 0) < DAY).length,
      new7d: ps.filter((p) => now - (p.first || p.seen || 0) < 7 * DAY).length,
      returning7d: ps.filter((p) => now - (p.seen || 0) < 7 * DAY && now - (p.first || 0) > 7 * DAY).length,
      rated: ratings.length,
      avgRating: Math.round(mean(ratings)),
      ratingHistogram: rb.map(([lo, hi]) => ({ label: hi === Infinity ? `${lo}+` : `${lo}–${hi}`, count: ratings.filter((r) => r >= lo && r < hi).length })),
      avgMatchesPerPlayer: Math.round(mean(ps.map((p) => p.played || 0)) * 10) / 10,
      top: leaderboard(25),
      mostActive: [...ps].sort((a, b) => (b.played || 0) - (a.played || 0)).slice(0, 10).map((p) => ({ name: p.name, played: p.played || 0, rating: Math.round(p.rating), lastSeen: p.seen })),
    };

    return {
      generatedAt: now, range: rangeMs || 0, overview, factions, matchup, maps: mapsOut, lengths: lengthsOut, formats,
      activity: {
        perHour, perDay: perDay.map(({ unique, ...d }) => ({ ...d, unique: unique.size })), hourOfDay,
        samples: thin(samples.filter((x) => x.t >= now - (rangeMs || 30 * DAY)), 720),
      },
      players: playersOut,
      community: { maps: maps.map.size, newest: poolList().slice(0, 10) },
    };
  }

  /** Right now: who is here, what is running, and how the machine is coping. */
  function live() {
    const mem = process.memoryUsage();
    const now = Date.now();
    const roomsOut = [...rooms.values()].map((r) => ({
      name: r.name, players: playersOf(r).length, observers: r.clients.length - playersOf(r).length, max: r.max,
      map: r.map.name, format: formatOf(r), started: r.started, ranked: r.ranked, quick: r.quick || "", locked: !!r.pass,
      host: host(r)?.name ?? "", age: Math.round((now - r.created) / 1000),
      elapsed: r.started && r.match ? Math.round((now - r.match.startedAt) / 1000) : 0,
      factions: r.match ? r.match.teams.map((t) => t.faction) : playersOf(r).map((c) => c.faction),
      names: playersOf(r).map((c) => c.name),
    })).sort((a, b) => Number(b.started) - Number(a.started) || b.players - a.players);
    const inGames = roomsOut.filter((r) => r.started).reduce((n, r) => n + r.players, 0);
    const inLobbies = roomsOut.filter((r) => !r.started).reduce((n, r) => n + r.players + r.observers, 0);
    const watching = roomsOut.filter((r) => r.started).reduce((n, r) => n + r.observers, 0);
    const queued = Object.fromEntries(Object.entries(queues).map(([k, q]) => [k, q.length]));
    const last = samples[samples.length - 1];
    return {
      now, online: conns.size, inGames, inLobbies, watching,
      browsing: [...conns].filter((c) => !c.room && !c.queue).length,
      queued, games: roomsOut.filter((r) => r.started).length, lobbies: roomsOut.filter((r) => !r.started).length,
      rooms: roomsOut,
      server: {
        uptime: Math.round(process.uptime()), bootAt: counters.bootAt, node: process.version, pid: process.pid,
        rssMB: Math.round(mem.rss / 1048576), heapMB: Math.round(mem.heapUsed / 1048576),
        lagMs: Math.round(lag), lagMaxMs: Math.round(lagMax), protocol: PROTOCOL,
        msgsPerMin: last?.msgs ?? 0, kbPerMin: Math.round((last?.bytes ?? 0) / 1024),
        storage: {
          matchesFile: matchesPath,
          matchesInMemory: matchLog.items.length,
          players: players.map.size, communityMaps: maps.map.size,
        },
      },
      counters: { ...counters, sinceBootSec: Math.round((now - counters.bootAt) / 1000) },
    };
  }

  const recentMatches = (n = 100) => matchLog.items.slice(-n).sort((a, b) => b.startedAt - a.startedAt).map((m) => ({ ...m, teams: m.teams.map(({ pid, ...t }) => t) }));

  function matchesCsv() {
    const head = ["n", "id", "startedAt", "endedAt", "kind", "ranked", "map", "customMap", "players", "format", "outcome", "winnerAlliance", "gameMinutes", "realMinutes", "quitters", "desync", "factions", "winningFactions"];
    const q = (v) => { const s2 = String(v ?? ""); return /[",\n]/.test(s2) ? `"${s2.replace(/"/g, '""')}"` : s2; };
    const lines = [head.join(",")];
    for (const m of matchLog.items) {
      lines.push([m.n, m.id, new Date(m.startedAt).toISOString(), m.endedAt ? new Date(m.endedAt).toISOString() : "", m.kind, m.ranked, m.map.name, m.map.custom,
        m.players, m.format, m.outcome, m.winner ?? "", (m.gameSec / 60).toFixed(1), (m.realSec / 60).toFixed(1), m.quitters, m.desync,
        m.teams.map((t) => t.faction || "?").join(" "), m.teams.filter((t) => t.alliance === m.winner).map((t) => t.faction || "?").join(" ")].map(q).join(","));
    }
    return lines.join("\n") + "\n";
  }

  /** Admin: a message to everyone connected, in the hub and in matches. */
  function announce(text) {
    const msg = clip(text, 300);
    if (!msg) return 0;
    const frame = encode(JSON.stringify({ t: "announce", text: msg }));
    for (const c of conns) { try { c.socket.write(frame); } catch { /* */ } }
    return conns.size;
  }
  /** Admin: close a room — everyone in it goes back to the hub. */
  function closeRoom(name) {
    const r = rooms.get(name);
    if (!r) return false;
    for (const c of [...r.clients]) { leaveRoom(c.conn, "closed"); c.conn.inHub = true; }
    rooms.delete(name);
    hubChanged();
    return true;
  }

  function stats() {
    let inRooms = 0; for (const r of rooms.values()) inRooms += r.clients.length;
    let games = 0; for (const r of rooms.values()) if (r.started) games++;
    return { rooms: rooms.size, games, players: inRooms, online: conns.size };
  }
  function shutdown() {
    clearInterval(beat); clearInterval(sampler); clearInterval(lagTimer); clearTimeout(firstSample);
    // Matches still running are recorded as cut off by the restart.
    for (const r of rooms.values()) if (r.started && !r.resolved) { r.resolved = true; recordMatch(r, "abandoned"); }
    maps.flush(); players.flush(); matchLog.flush(); sampleLog.flush();
    for (const c of [...conns]) c.close();
  }
  return { handleConn, stats, roomList, poolList, leaderboard, shutdown, analytics, live, recentMatches, matchesCsv, announce, closeRoom, sample };
}

function log(msg) { if (process.env.NODE_ENV !== "test" && !process.env.VITEST) console.log(msg); }

// --- The website ------------------------------------------------------------

function findGameHtml(explicit) {
  if (explicit === false) return null; // relay only
  const candidates = [explicit, process.env.GAME_HTML, path.join(here, "public", "index.html"), path.join(here, "..", "dist", "banner-and-blade.html")];
  for (const f of candidates) { if (f && fs.existsSync(f)) return f; }
  return null;
}

export function startServer(port = 8787, bindHost = "0.0.0.0", opts = {}) {
  const hub = createHub(opts);
  const gameFile = findGameHtml(opts.gameHtml);
  let gameCache = null;
  const sockets = new Set();
  const json = (res, code, body) => {
    res.writeHead(code, { "content-type": "application/json", "access-control-allow-origin": "*", "cache-control": "no-store" });
    res.end(JSON.stringify(body));
  };
  // ---- the admin dashboard ----
  // Off unless ADMIN_TOKEN is set (on Fly: `fly secrets set ADMIN_TOKEN=…`).
  // Every API call carries it as a Bearer token; wrong guesses are limited per
  // address so it can't be brute-forced.
  const adminToken = opts.adminToken ?? process.env.ADMIN_TOKEN ?? "";
  const adminHash = adminToken ? crypto.createHash("sha256").update(adminToken).digest() : null;
  const failures = new Map(); // ip -> { n, since }
  const clientIp = (req) => String(req.headers["fly-client-ip"] || String(req.headers["x-forwarded-for"] || "").split(",")[0] || req.socket.remoteAddress || "").trim();
  function authorised(req) {
    if (!adminHash) return "disabled";
    const ip = clientIp(req);
    const f = failures.get(ip);
    if (f && Date.now() - f.since < 10 * 60e3 && f.n >= 10) return "locked";
    const got = String(req.headers.authorization || "").replace(/^Bearer\s+/i, "");
    const ok = crypto.timingSafeEqual(crypto.createHash("sha256").update(got).digest(), adminHash);
    if (ok) { failures.delete(ip); return "ok"; }
    const cur = f && Date.now() - f.since < 10 * 60e3 ? f : { n: 0, since: Date.now() };
    cur.n++;
    failures.set(ip, cur);
    return "denied";
  }
  const readBody = (req) => new Promise((resolve) => {
    let body = "";
    req.on("data", (c) => { body += c; if (body.length > 10000) req.destroy(); });
    req.on("end", () => { try { resolve(JSON.parse(body || "{}")); } catch { resolve({}); } });
    req.on("error", () => resolve({}));
  });
  const RANGES = { "24h": 24 * 3600e3, "7d": 7 * 24 * 3600e3, "30d": 30 * 24 * 3600e3, "all": 0 };
  const adminPage = path.join(here, "admin.html");

  async function adminApi(req, res, url) {
    const auth = authorised(req);
    if (auth === "disabled") return json(res, 503, { error: "The admin dashboard is off. Set ADMIN_TOKEN (on Fly: fly secrets set ADMIN_TOKEN=<a long random string>) and restart." });
    if (auth === "locked") return json(res, 429, { error: "Too many wrong tokens from this address — wait ten minutes." });
    if (auth !== "ok") return json(res, 401, { error: "Wrong admin token." });
    const q = new URL(req.url || "/", "http://x").searchParams;
    if (url === "/admin/api/live") return json(res, 200, hub.live());
    if (url === "/admin/api/stats") return json(res, 200, hub.analytics(RANGES[q.get("range") || "7d"] ?? RANGES["7d"]));
    if (url === "/admin/api/matches") return json(res, 200, hub.recentMatches(Math.max(1, Math.min(1000, Number(q.get("n")) || 100))));
    if (url === "/admin/api/matches.csv") {
      res.writeHead(200, { "content-type": "text/csv; charset=utf-8", "content-disposition": "attachment; filename=\"banner-and-blade-matches.csv\"", "cache-control": "no-store" });
      res.end(hub.matchesCsv());
      return;
    }
    if (req.method === "POST" && url === "/admin/api/announce") {
      const body = await readBody(req);
      return json(res, 200, { sent: hub.announce(body.text) });
    }
    if (req.method === "POST" && url === "/admin/api/close-room") {
      const body = await readBody(req);
      return json(res, 200, { closed: hub.closeRoom(String(body.name || "")) });
    }
    return json(res, 404, { error: "Unknown admin call." });
  }

  const server = http.createServer((req, res) => {
    const url = (req.url || "/").split("?")[0];
    if (url === "/admin" || url === "/admin/") {
      try {
        res.writeHead(200, {
          "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff",
          "x-frame-options": "DENY", "referrer-policy": "no-referrer",
        });
        res.end(fs.readFileSync(adminPage));
      } catch { res.writeHead(500, { "content-type": "text/plain" }); res.end("admin.html is missing next to server.mjs\n"); }
      return;
    }
    if (url.startsWith("/admin/api/")) {
      adminApi(req, res, url).catch(() => { if (!res.headersSent) json(res, 500, { error: "Admin call failed." }); });
      return;
    }
    if (url === "/healthz") return json(res, 200, { ok: true, ...hub.stats(), protocol: PROTOCOL, game: !!gameFile, uptime: process.uptime() });
    if (url === "/maps") return json(res, 200, hub.poolList());
    if (url === "/rooms") return json(res, 200, hub.roomList());
    if (url === "/leaderboard") return json(res, 200, hub.leaderboard(100));
    if ((url === "/" || url === "/index.html" || url === "/play") && gameFile) {
      try {
        // Read once, re-read when the file changes (a redeploy of the build).
        const mtime = fs.statSync(gameFile).mtimeMs;
        if (!gameCache || gameCache.mtime !== mtime) {
          const body = fs.readFileSync(gameFile);
          gameCache = {
            mtime, body, gz: zlib.gzipSync(body, { level: 9 }),
            etag: `"${crypto.createHash("sha1").update(body).digest("hex").slice(0, 16)}"`,
          };
        }
        // Revalidated every visit (no-cache), so a deploy is picked up at once,
        // but an unchanged page is a 304 and never re-sent.
        const headers = {
          "content-type": "text/html; charset=utf-8", "cache-control": "no-cache", etag: gameCache.etag,
          "x-content-type-options": "nosniff", "referrer-policy": "no-referrer", vary: "accept-encoding",
        };
        if (req.headers["if-none-match"] === gameCache.etag) { res.writeHead(304, headers); res.end(); return; }
        const gzip = /\bgzip\b/.test(String(req.headers["accept-encoding"] || ""));
        res.writeHead(200, gzip ? { ...headers, "content-encoding": "gzip" } : headers);
        res.end(gzip ? gameCache.gz : gameCache.body);
      } catch {
        res.writeHead(500, { "content-type": "text/plain" });
        res.end("The game build could not be read.\n");
      }
      return;
    }
    if (url === "/") {
      const s = hub.stats();
      res.writeHead(200, { "content-type": "text/plain" });
      res.end(`Banner & Blade relay up. Rooms: ${s.rooms}, players: ${s.players}\n(No game build found — run \`npm run build:single\` or set GAME_HTML to serve the game here.)\n`);
      return;
    }
    res.writeHead(404, { "content-type": "text/plain" });
    res.end("Not found\n");
  });
  server.on("upgrade", (req, socket) => {
    const key = req.headers["sec-websocket-key"];
    if (!key) { socket.destroy(); return; }
    const accept = crypto.createHash("sha1").update(key + WS_GUID).digest("base64");
    socket.write(
      "HTTP/1.1 101 Switching Protocols\r\n" +
      "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
      `Sec-WebSocket-Accept: ${accept}\r\n\r\n`,
    );
    socket.setNoDelay?.(true);
    sockets.add(socket);
    socket.on("close", () => sockets.delete(socket));
    hub.handleConn(socket);
  });
  return new Promise((resolve) => {
    server.listen(port, bindHost, () => {
      const addr = server.address();
      log(`Banner & Blade listening on http://${bindHost}:${addr.port}  (game ${gameFile ? "served from " + gameFile : "not built"}; up to ${MAX_PLAYERS} players/room)`);
      resolve({
        server, port: addr.port,
        close: () => new Promise((r) => { hub.shutdown(); for (const s of sockets) s.destroy(); server.close(() => r()); }),
      });
    });
  });
}

// Run directly: `node server/server.mjs [port]`
//
// PORT from the environment takes precedence over the argument, because that is
// how every managed host tells a process where to listen — a server that only
// reads argv binds 8787, the platform routes to whatever it assigned, and the
// deploy looks healthy while being unreachable.
/**
 * Started as root with RUN_AS=uid:gid (the container does this): take
 * ownership of the data files — a freshly mounted Fly volume belongs to root —
 * then give up root for good before listening to anyone.
 */
function dropPrivileges() {
  const spec = process.env.RUN_AS;
  if (!spec || typeof process.getuid !== "function" || process.getuid() !== 0) return;
  const [uid, gid] = spec.split(":").map(Number);
  if (!Number.isInteger(uid) || !Number.isInteger(gid)) return;
  const dirs = new Set([process.env.MAPS_FILE, process.env.PLAYERS_FILE, process.env.MATCHES_FILE].filter(Boolean).map((f) => path.dirname(f)));
  for (const d of dirs) {
    try {
      fs.mkdirSync(d, { recursive: true });
      fs.chownSync(d, uid, gid);
      for (const f of fs.readdirSync(d)) { try { fs.chownSync(path.join(d, f), uid, gid); } catch { /* */ } }
    } catch (e) { console.warn(`couldn't prepare ${d}: ${e.message}`); }
  }
  process.setgid(gid);
  process.setuid(uid);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  dropPrivileges();
  const port = Number(process.env.PORT) || Number(process.argv[2]) || 8787;
  const host = process.env.HOST || "0.0.0.0";
  startServer(port, host).then(({ server, close }) => {
    // Containers are stopped with SIGTERM. Without this the platform waits out
    // its grace period and SIGKILLs, which turns every deploy into a hang.
    let closing = false;
    const bye = (sig) => {
      if (closing) return;
      closing = true;
      console.log(`\n${sig} — closing relay.`);
      close().then(() => process.exit(0));
      // Sockets mid-match would otherwise hold the process open indefinitely.
      setTimeout(() => process.exit(0), 5000).unref();
    };
    process.on("SIGTERM", () => bye("SIGTERM"));
    process.on("SIGINT", () => bye("SIGINT"));
    void server;
  });
}
