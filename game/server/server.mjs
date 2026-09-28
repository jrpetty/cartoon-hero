// Banner & Blade — zero-dependency lockstep relay server.
//
// Runs on plain Node (>=18), no `npm install` needed. Host it anywhere your
// players can reach — e.g. a box on your VPN — and everyone connects to
// ws://<that-host>:<port>. The server is a thin relay: it groups players into
// rooms, assigns teams/alliances and a shared seed when the host starts, and
// forwards each client's lockstep turns/checksums to the others. It never
// simulates the game itself, so it stays tiny and cheap even for 8v8.
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

// --- Community map pool -----------------------------------------------------
//
// Players publish the maps they have made to the server they play on, and
// everyone who connects can see them, pick one for a match, or keep a copy.
// The pool is a JSON file beside the server (or MAPS_FILE), so it outlives a
// restart; if the disk is read-only it simply lives in memory.

let mapsFile = process.env.MAPS_FILE || path.join(path.dirname(fileURLToPath(import.meta.url)), "community-maps.json");
/** @type {Map<string, {id:string,name:string,author:string,desc:string,minPlayers:number,maxPlayers:number,cols:number,published:number,code:string}>} */
let pool = new Map();
let poolLoaded = false;
function loadPool() {
  if (poolLoaded) return;
  poolLoaded = true;
  try {
    const arr = JSON.parse(fs.readFileSync(mapsFile, "utf8"));
    if (Array.isArray(arr)) for (const m of arr) if (m && typeof m.id === "string" && typeof m.code === "string") pool.set(m.id, m);
  } catch { /* no pool yet */ }
}
function savePool() {
  try { fs.writeFileSync(mapsFile, JSON.stringify([...pool.values()])); } catch { /* memory only */ }
}
const clip = (v, n) => String(v ?? "").slice(0, n);
const int = (v, lo, hi, d) => { const n = Math.floor(Number(v)); return Number.isFinite(n) ? Math.max(lo, Math.min(hi, n)) : d; };
function poolMeta(m) {
  const { code, ...meta } = m;
  void code;
  return meta;
}
/** Add a map to the pool. Same code, same id — publishing twice is harmless. */
function publishMap(raw) {
  loadPool();
  const code = String(raw?.code ?? "");
  if (!code.startsWith("BBMAP") || code.length > MAX_MAP_CODE) return null;
  const id = "cm_" + crypto.createHash("sha1").update(code).digest("hex").slice(0, 12);
  const prev = pool.get(id);
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
  pool.set(id, entry);
  // Oldest out first once the pool is full.
  while (pool.size > MAX_POOL) pool.delete([...pool.values()].sort((a, b) => a.published - b.published)[0].id);
  savePool();
  return entry;
}
function poolList() {
  loadPool();
  return [...pool.values()].sort((a, b) => b.published - a.published).map(poolMeta);
}

// --- Rooms ------------------------------------------------------------------
/** @typedef {{socket:any, slot:number, name:string, team:number, faction:string, ready:boolean, observer:boolean, send:(o:any)=>void}} Client */
const rooms = new Map(); // name -> { clients, started, slotTeams, pass, layout, map }

function room(name) {
  let r = rooms.get(name);
  if (!r) {
    r = { clients: [], started: false, slotTeams: new Map(), pass: "", layout: 2, map: { id: "open_plains", name: "Open Plains" } };
    rooms.set(name, r);
  }
  return r;
}
function host(r) {
  // Observers can never be host; the lowest-slot player is.
  return r.clients.filter((c) => !c.observer).reduce((h, c) => (h === null || c.slot < h.slot ? c : h), null);
}
const playersOf = (r) => r.clients.filter((c) => !c.observer).sort((a, b) => a.slot - b.slot);

/**
 * The room's layout, applied to everyone: seats in join order, in contiguous
 * blocks — with 8 players and 2 teams, the first four are team 1 and the rest
 * team 2. 0 is free-for-all (everyone on their own).
 */
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
/** Where someone joining a room goes: the smallest of the layout's teams. */
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
function lobbyState(r) {
  const h = host(r);
  return {
    t: "lobby",
    host: h ? h.slot : -1,
    started: r.started,
    layout: r.layout,
    map: { id: r.map.id, name: r.map.name, author: r.map.author || "", minPlayers: r.map.minPlayers, maxPlayers: r.map.maxPlayers },
    players: r.clients.map((c) => ({
      slot: c.slot, name: c.name, team: c.team, side: c.team > 0 ? (c.team - 1) % 2 : c.slot % 2,
      faction: c.faction, ready: c.ready, observer: c.observer,
    })),
  };
}
function broadcast(r, obj, except) {
  const frame = encode(JSON.stringify(obj));
  for (const c of r.clients) if (c.socket !== except) { try { c.socket.write(frame); } catch { /* */ } }
}

function startMatch(r) {
  // Only actual players get teams; observers just watch.
  const players = playersOf(r);
  if (r.started || players.length < 2 || sidesOf(r) < 2) return;
  r.started = true;
  // Allies get consecutive team indices (and so adjacent map starts): sort by
  // team — solo players last — then by join order.
  const key = (c) => (c.team > 0 ? c.team : MAX_TEAMS + 1 + c.slot);
  const ordered = players.sort((a, b) => key(a) - key(b) || a.slot - b.slot);
  const numTeams = ordered.length;
  const alliances = [];
  const factions = [];
  const slotTeams = [];
  let solo = MAX_TEAMS;
  ordered.forEach((c, team) => {
    alliances[team] = c.team > 0 ? c.team - 1 : solo++;
    factions[team] = c.faction || "";
    slotTeams.push({ slot: c.slot, team });
    r.slotTeams.set(c.slot, team);
  });
  const seed = (Math.random() * 1e9) | 0;
  broadcast(r, { t: "start", seed, numTeams, alliances, slotTeams, factions, map: r.map });
  console.log(`[room ${roomName(r)}] match started: ${numTeams} players on ${r.map.name}, alliances ${alliances.join(",")}`);
}
function roomName(r) {
  for (const [n, rr] of rooms) if (rr === r) return n;
  return "?";
}

function handleConn(socket) {
  let r = null;
  /** @type {Client} */
  let me = null;

  const send = (obj) => { try { socket.write(encode(JSON.stringify(obj))); } catch { /* */ } };

  const onText = (text) => {
    let m;
    try { m = JSON.parse(text); } catch { return; }
    switch (m.t) {
      case "hello": {
        const name = (m.room || "main").toString().slice(0, 32);
        const pass = (m.pass || "").toString().slice(0, 64);
        const existing = rooms.get(name);
        // The first to enter a room sets its password; everyone else must match.
        if (existing && (existing.pass || "") !== pass) { send({ t: "error", msg: "Wrong room password." }); return; }
        r = room(name);
        if (!existing) r.pass = pass;
        if (r.started) { send({ t: "error", msg: "That match is already in progress." }); return; }
        if (r.clients.length >= MAX_PLAYERS) { send({ t: "error", msg: "Room is full (16 players)." }); return; }
        const used = new Set(r.clients.map((c) => c.slot));
        let slot = 0; while (used.has(slot)) slot++;
        me = { socket, slot, name: (m.name || `Player ${slot + 1}`).toString().slice(0, 24), team: 0, faction: FACTIONS.includes(m.faction) ? m.faction : "", ready: false, observer: !!m.observer, send };
        if (!me.observer) me.team = openTeam(r);
        r.clients.push(me);
        send({ t: "welcome", slot, room: name, max: MAX_PLAYERS, observer: me.observer });
        broadcast(r, lobbyState(r));
        break;
      }
      // Older clients pick a side, A or B: that is team 1 or 2.
      case "side": if (me && r && !r.started && !me.observer) { me.team = m.side ? 2 : 1; broadcast(r, lobbyState(r)); } break;
      // Join a team (0 = on your own). The host may move anyone by slot.
      case "team": {
        if (!me || !r || r.started) break;
        const target = m.slot === undefined || m.slot === me.slot ? me : host(r) === me ? r.clients.find((c) => c.slot === m.slot) : null;
        if (!target || target.observer) break;
        target.team = int(m.team, 0, MAX_TEAMS, 0);
        broadcast(r, lobbyState(r));
        break;
      }
      // Host: lay every seat out in blocks — 0 free-for-all, 2..8 teams.
      case "layout": {
        if (!me || !r || r.started || host(r) !== me) break;
        const k = int(m.teams, 0, MAX_TEAMS, 2);
        r.layout = k === 1 ? 2 : k;
        applyLayout(r);
        broadcast(r, lobbyState(r));
        break;
      }
      case "faction": if (me && r && !r.started) { me.faction = FACTIONS.includes(m.faction) ? m.faction : ""; broadcast(r, lobbyState(r)); } break;
      // Host: the battlefield — a built-in preset by id, or a map carried as code.
      case "map": {
        if (!me || !r || r.started || host(r) !== me || !m.map) break;
        const code = m.map.code ? String(m.map.code) : "";
        if (code && (!code.startsWith("BBMAP") || code.length > MAX_MAP_CODE)) break;
        r.map = {
          id: clip(m.map.id, 64) || "open_plains", name: clip(m.map.name, 40) || "Battlefield", author: clip(m.map.author, 24),
          ...(code ? { code, minPlayers: int(m.map.minPlayers, 1, MAX_PLAYERS, 1), maxPlayers: int(m.map.maxPlayers, 1, MAX_PLAYERS, MAX_PLAYERS) } : {}),
        };
        broadcast(r, lobbyState(r));
        break;
      }
      // The community pool: publish, list, fetch one.
      case "publish": {
        const entry = publishMap(m.map);
        send(entry ? { t: "published", map: poolMeta(entry) } : { t: "error", msg: "That doesn't look like a map code." });
        if (entry) send({ t: "maps", maps: poolList() });
        break;
      }
      case "maps": send({ t: "maps", maps: poolList() }); break;
      case "getmap": {
        loadPool();
        const e = pool.get(String(m.id));
        send(e ? { t: "mapcode", id: e.id, code: e.code, name: e.name, author: e.author } : { t: "error", msg: "That map is no longer on this server." });
        break;
      }
      case "ready": if (me && r && !r.started) { me.ready = !!m.ready; broadcast(r, lobbyState(r)); } break;
      case "start": if (me && r && host(r) === me) startMatch(r); break;
      case "turn":
      case "sum": if (r && r.started) broadcast(r, m, socket); break; // relay to others
      case "chat":
      case "ping": if (r && me) broadcast(r, { ...m, slot: me.slot }, socket); break; // relay (lobby + match)
      default: break;
    }
  };

  const onClose = () => {
    try { socket.destroy(); } catch { /* */ }
    if (!r || !me) return;
    r.clients = r.clients.filter((c) => c !== me);
    if (r.started) {
      const team = r.slotTeams.get(me.slot);
      if (team !== undefined) broadcast(r, { t: "drop", team }); // keep the sim alive
    } else {
      broadcast(r, lobbyState(r));
    }
    if (r.clients.length === 0) rooms.delete(roomName(r));
    me = null; r = null;
  };

  const parse = frameParser(onText, onClose, (pong) => { try { socket.write(encode(pong.toString("binary"), 0xA)); } catch { /* */ } });
  socket.on("data", (c) => { try { parse(c); } catch { onClose(); } });
  socket.on("close", onClose);
  socket.on("error", onClose);
}

export function startServer(port = 8787, host = "0.0.0.0", opts = {}) {
  if (opts.mapsFile) mapsFile = opts.mapsFile;
  pool = new Map(); poolLoaded = false;
  const server = http.createServer((req, res) => {
    let n = 0; for (const r of rooms.values()) n += r.clients.length;
    if (req.url === "/maps") {
      res.writeHead(200, { "content-type": "application/json", "access-control-allow-origin": "*" });
      res.end(JSON.stringify(poolList()));
      return;
    }
    // Managed hosts poll a health endpoint and restart anything that doesn't
    // answer it. JSON so a monitor can read it; the plain root stays a
    // human-readable "is it up?" check from a browser.
    if (req.url === "/healthz") {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: true, rooms: rooms.size, players: n, uptime: process.uptime() }));
      return;
    }
    res.writeHead(200, { "content-type": "text/plain" });
    res.end(`Banner & Blade relay up. Rooms: ${rooms.size}, players: ${n}\n`);
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
    handleConn(socket);
  });
  return new Promise((resolve) => {
    server.listen(port, host, () => {
      const addr = server.address();
      console.log(`Banner & Blade relay listening on ws://${host}:${addr.port}  (up to ${MAX_PLAYERS} players/room)`);
      resolve({ server, port: addr.port, close: () => new Promise((r) => server.close(r)) });
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
