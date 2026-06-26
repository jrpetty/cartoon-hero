// Thin WebSocket wrapper. Emits parsed server frames to registered handlers and
// exposes typed send helpers mirroring server/protocol.js.

export const C = {
  JOIN: 'join', MOVE: 'move', BREAK: 'break', PLACE: 'place',
  ATTACK: 'attack', HOTBAR: 'hotbar', CHAT: 'chat', REQCHUNK: 'reqchunk',
};

export class Net {
  constructor() {
    this.handlers = {};
    this.ws = null;
    this.ready = false;
    this.outbox = [];   // messages queued before the socket is open
  }

  connect() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    this.ws = new WebSocket(`${proto}://${location.host}`);
    this.ws.onopen = () => {
      this.ready = true;
      for (const m of this.outbox) this.ws.send(m);
      this.outbox.length = 0;
      this.emit('open');
    };
    this.ws.onclose = () => { this.ready = false; this.emit('close'); };
    this.ws.onerror = () => this.emit('error');
    this.ws.onmessage = (e) => {
      let m; try { m = JSON.parse(e.data); } catch { return; }
      this.emit(m.t, m);
    };
  }

  on(type, fn) { (this.handlers[type] ||= []).push(fn); return this; }
  emit(type, payload) { (this.handlers[type] || []).forEach(fn => fn(payload)); }

  // Buffer until open so an early join() (sent before the socket connects)
  // is never silently dropped. Frequent frames (move) are skipped while
  // disconnected since a newer one will follow.
  send(obj) {
    const data = JSON.stringify(obj);
    if (this.ready) this.ws.send(data);
    else if (obj.t !== C.MOVE) this.outbox.push(data);
  }

  join(name) { this.send({ t: C.JOIN, name }); }
  move(x, y, z, yaw, pitch) { this.send({ t: C.MOVE, x, y, z, yaw, pitch }); }
  break(x, y, z) { this.send({ t: C.BREAK, x, y, z }); }
  place(x, y, z, b) { this.send({ t: C.PLACE, x, y, z, b }); }
  attack(id) { this.send({ t: C.ATTACK, id }); }
  hotbar(slot) { this.send({ t: C.HOTBAR, slot }); }
  chat(msg) { this.send({ t: C.CHAT, msg }); }
}
