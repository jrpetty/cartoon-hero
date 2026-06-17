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

const MAX_PLAYERS = 16; // 8v8
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

// --- Rooms ------------------------------------------------------------------
/** @typedef {{socket:any, slot:number, name:string, side:number, ready:boolean, send:(o:any)=>void}} Client */
const rooms = new Map(); // name -> { clients: Client[], started: bool, slotTeams: Map<slot,team> }

function room(name) {
  let r = rooms.get(name);
  if (!r) { r = { clients: [], started: false, slotTeams: new Map() }; rooms.set(name, r); }
  return r;
}
function host(r) {
  return r.clients.reduce((h, c) => (h === null || c.slot < h.slot ? c : h), null);
}
function lobbyState(r) {
  const h = host(r);
  return {
    t: "lobby",
    host: h ? h.slot : -1,
    started: r.started,
    players: r.clients.map((c) => ({ slot: c.slot, name: c.name, side: c.side, ready: c.ready })),
  };
}
function broadcast(r, obj, except) {
  const frame = encode(JSON.stringify(obj));
  for (const c of r.clients) if (c.socket !== except) { try { c.socket.write(frame); } catch { /* */ } }
}

function startMatch(r) {
  if (r.started || r.clients.length < 2) return;
  r.started = true;
  // Allies get consecutive team indices (and so adjacent map starts): sort by
  // side, then by join order.
  const ordered = [...r.clients].sort((a, b) => a.side - b.side || a.slot - b.slot);
  const numTeams = ordered.length;
  const alliances = [];
  const slotTeams = [];
  ordered.forEach((c, team) => {
    alliances[team] = c.side;
    slotTeams.push({ slot: c.slot, team });
    r.slotTeams.set(c.slot, team);
  });
  const seed = (Math.random() * 1e9) | 0;
  broadcast(r, { t: "start", seed, numTeams, alliances, slotTeams });
  console.log(`[room ${roomName(r)}] match started: ${numTeams} players, sides ${alliances.join("")}`);
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
        r = room(name);
        if (r.started) { send({ t: "error", msg: "That match is already in progress." }); return; }
        if (r.clients.length >= MAX_PLAYERS) { send({ t: "error", msg: "Room is full (16 players)." }); return; }
        const used = new Set(r.clients.map((c) => c.slot));
        let slot = 0; while (used.has(slot)) slot++;
        me = { socket, slot, name: (m.name || `Player ${slot + 1}`).toString().slice(0, 24), side: slot % 2, ready: false, send };
        r.clients.push(me);
        send({ t: "welcome", slot, room: name, max: MAX_PLAYERS });
        broadcast(r, lobbyState(r));
        break;
      }
      case "side": if (me && r && !r.started) { me.side = m.side ? 1 : 0; broadcast(r, lobbyState(r)); } break;
      case "ready": if (me && r && !r.started) { me.ready = !!m.ready; broadcast(r, lobbyState(r)); } break;
      case "start": if (me && r && host(r) === me) startMatch(r); break;
      case "turn":
      case "sum": if (r && r.started) broadcast(r, m, socket); break; // relay to others
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

export function startServer(port = 8787, host = "0.0.0.0") {
  const server = http.createServer((req, res) => {
    // A plain GET is handy for a quick "is it up?" check from a browser.
    res.writeHead(200, { "content-type": "text/plain" });
    let n = 0; for (const r of rooms.values()) n += r.clients.length;
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
if (import.meta.url === `file://${process.argv[1]}`) {
  startServer(Number(process.argv[2]) || 8787);
}
