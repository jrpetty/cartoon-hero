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
  const flush = () => { timer = null; try { fs.writeFileSync(file, JSON.stringify([...map.values()])); } catch { /* memory only */ } };
  return {
    map,
    save() { if (!timer) timer = setTimeout(flush, 250); },
    flush() { if (timer) { clearTimeout(timer); flush(); } },
  };
}

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
    if (!p) { p = { id: pid, name: name || "Player", rating: START_RATING, wins: 0, losses: 0, games: 0, seen: Date.now() }; players.map.set(pid, p); }
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
    const seed = (Math.random() * 1e9) | 0;
    broadcast(r, { t: "start", seed, numTeams: ordered.length, alliances, slotTeams, factions, map: r.map, ranked: r.ranked });
    log(`[room ${r.name}] started: ${ordered.length} players on ${r.map.name}${r.ranked ? " (ranked)" : ""}`);
    hubChanged();
    return true;
  }

  // ---- results & ratings ----
  // Every client simulates the same match, so every client knows who won.
  // Each reports it; once everyone still connected has, the most-reported
  // alliance wins. Disagreement (someone lying) means no result at all.
  function report(r, conn, winnerTeam) {
    if (!r.started || !r.ranked || r.resolved || !conn.client || conn.client.observer) return;
    const alliance = r.teamAlliance[int(winnerTeam, 0, 63, -1)];
    if (alliance === undefined) return;
    r.reports.set(conn.client.slot, alliance);
    maybeResolve(r);
  }
  function maybeResolve(r) {
    if (r.resolved || !r.ranked || !r.started) return;
    const live = playersOf(r).map((c) => c.slot);
    if (!r.reports.size || live.some((s) => !r.reports.has(s))) return;
    const tally = new Map();
    for (const a of r.reports.values()) tally.set(a, (tally.get(a) || 0) + 1);
    const ranked = [...tally.entries()].sort((a, b) => b[1] - a[1]);
    r.resolved = true;
    if (ranked.length > 1 && ranked[0][1] === ranked[1][1]) { log(`[room ${r.name}] result disputed — not rated`); return; }
    const winner = ranked[0][0];
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
      maybeResolve(r);
    } else {
      // A quick-match room someone walks out of before it starts is off.
      if (r.quick && r.countdown) {
        clearTimeout(r.countdown); r.countdown = null;
        broadcast(r, { t: "cancelled", msg: "Someone left before the match began — back to the queue." });
      }
      broadcast(r, lobbyState(r));
    }
    if (r.clients.length === 0) { if (r.countdown) clearTimeout(r.countdown); rooms.delete(r.name); }
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
      if (conn.tokens < 1) { if (++conn.strikes > RATE_BURST) onClose(); return; }
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
          if (target) { leaveRoom(target.conn, "kicked"); target.conn.inHub = true; }
          break;
        }
        case "ready": if (me() && !r().started) { me().ready = !!m.ready; broadcast(r(), lobbyState(r())); } break;
        case "start": if (me() && isHost() && !r().quick) startMatch(r()); break;
        case "turn":
        case "sum": if (r() && r().started) broadcast(r(), m, socket); break; // relay to others
        case "chat": {
          if (!r() || !me()) break;
          // At most five lines in five seconds, each a sentence or two.
          conn.chatAt = conn.chatAt.filter((t) => now - t < 5000);
          if (conn.chatAt.length >= 5) break;
          conn.chatAt.push(now);
          broadcast(r(), { t: "chat", slot: me().slot, name: me().name, team: m.team, text: clip(m.text, 240) }, socket);
          break;
        }
        case "ping": if (r() && me()) broadcast(r(), { t: "ping", x: Number(m.x) || 0, y: Number(m.y) || 0, team: m.team, slot: me().slot }, socket); break;
        case "result": if (r()) report(r(), conn, m.winner); break;
        // The community map pool: publish, list, fetch one.
        case "publish": {
          const entry = publishMap(m.map);
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
    socket.on("data", (c) => { conn.lastSeen = Date.now(); try { parse(c); } catch { onClose(); } });
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
        if (now - c.lastSeen > (opts.idleMs ?? IDLE_MS)) { c.close(); continue; }
        c.socket.write(ping);
      } catch (e) { console.error("heartbeat:", e); }
    }
  }, opts.pingMs ?? PING_MS);
  beat.unref?.();

  function stats() {
    let inRooms = 0; for (const r of rooms.values()) inRooms += r.clients.length;
    let games = 0; for (const r of rooms.values()) if (r.started) games++;
    return { rooms: rooms.size, games, players: inRooms, online: conns.size };
  }
  function shutdown() {
    clearInterval(beat);
    maps.flush(); players.flush();
    for (const c of [...conns]) c.close();
  }
  return { handleConn, stats, roomList, poolList, leaderboard, shutdown };
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
  const server = http.createServer((req, res) => {
    const url = (req.url || "/").split("?")[0];
    if (url === "/healthz") return json(res, 200, { ok: true, ...hub.stats(), protocol: PROTOCOL, game: !!gameFile, uptime: process.uptime() });
    if (url === "/maps") return json(res, 200, hub.poolList());
    if (url === "/rooms") return json(res, 200, hub.roomList());
    if (url === "/leaderboard") return json(res, 200, hub.leaderboard(100));
    if ((url === "/" || url === "/index.html" || url === "/play") && gameFile) {
      try {
        // Read once, re-read when the file changes (a redeploy of the build).
        const mtime = fs.statSync(gameFile).mtimeMs;
        if (!gameCache || gameCache.mtime !== mtime) gameCache = { mtime, body: fs.readFileSync(gameFile) };
        res.writeHead(200, {
          "content-type": "text/html; charset=utf-8",
          "cache-control": "no-cache",
          "x-content-type-options": "nosniff",
          "referrer-policy": "no-referrer",
        });
        res.end(gameCache.body);
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
if (import.meta.url === `file://${process.argv[1]}`) {
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
