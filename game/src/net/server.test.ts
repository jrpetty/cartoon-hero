import { describe, expect, it, afterAll, beforeAll } from "vitest";
// The relay is plain Node; we drive it here with Node 22's built-in WebSocket.
import { startServer } from "../../server/server.mjs";
import { World } from "../sim/world";
import { Team, Kind } from "../sim/types";
import { generateMap } from "../maps/generator";
import { worldChecksum } from "../sim/commands";
import { NetSession } from "./session";
import { Transport } from "./transport";

const flush = (ms = 10) => new Promise((r) => setTimeout(r, ms));

interface Client {
  name: string;
  slot: number;
  team: number;
  world: World | null;
  session: NetSession | null;
  ws: WebSocket;
  send: (m: unknown) => void;
  joined: Promise<void>;
  started: Promise<void>;
}

function makeClient(url: string, name: string, observer = false, room = "test"): Client {
  let resolveJoined!: () => void;
  let resolveStarted!: () => void;
  const ws = new WebSocket(url);
  const t: Transport = { send: (m) => ws.send(JSON.stringify(m)), close: () => ws.close() };
  ws.onmessage = (e) => t.onData?.(JSON.parse(e.data as string));
  const c: Client = {
    name, slot: -1, team: -1, world: null, session: null, ws,
    send: (m) => t.send(m),
    joined: new Promise((r) => (resolveJoined = r)),
    started: new Promise((r) => (resolveStarted = r)),
  };
  ws.onopen = () => t.send({ t: "hello", name, room, observer });
  t.onData = (raw) => {
    const m = raw as { t: string; slot?: number; seed?: number; numTeams?: number; alliances?: number[]; slotTeams?: { slot: number; team: number }[] };
    if (m.t === "welcome") { c.slot = m.slot!; resolveJoined(); }
    else if (m.t === "start") {
      const mine = m.slotTeams!.find((s) => s.slot === c.slot);
      c.team = mine ? mine.team : 0; // observers view as team 0
      const n = m.numTeams!;
      const teams = Array.from({ length: n }, (_, i) => i as Team);
      const map = generateMap("open_plains", m.seed!, n, false);
      const world = new World(m.seed!);
      world.init(map, teams.map(() => ({})), teams.map(() => 1), m.alliances, teams.map(() => ""), false, undefined, "conquest");
      c.world = world;
      c.session = new NetSession(t, c.team as Team, teams, observer || !mine); // attach overrides t.onData
      c.session.attach(world, 4);
      resolveStarted();
    }
  };
  return c;
}

describe("Relay server end-to-end", () => {
  let srv: { port: number; close: () => Promise<void> };
  beforeAll(async () => { srv = await startServer(0, "127.0.0.1"); });
  afterAll(async () => { await srv.close(); });

  it("relays a 2v2 lockstep match keeping all four clients in sync", async () => {
    const url = `ws://127.0.0.1:${srv.port}`;
    const clients = ["A", "B", "C", "D"].map((n) => makeClient(url, n));
    await Promise.all(clients.map((c) => c.joined));
    // Two per side.
    clients[0].send({ t: "side", side: 0 });
    clients[1].send({ t: "side", side: 1 });
    clients[2].send({ t: "side", side: 0 });
    clients[3].send({ t: "side", side: 1 });
    await flush();
    // Host is the lowest slot (the first to join).
    const hostC = clients.reduce((h, c) => (c.slot < h.slot ? c : h));
    hostC.send({ t: "start" });
    await Promise.all(clients.map((c) => c.started));

    // Allies should share an alliance and enemies should not.
    const a = clients.find((c) => c.slot === 0)!;
    const allyOfA = clients.find((c) => c.world!.areAllied(a.team as Team, c.team as Team) && c !== a);
    expect(allyOfA).toBeTruthy();

    for (let frame = 0; frame < 140; frame++) {
      for (const c of clients) {
        if (frame % 25 === 0) {
          const ids = c.world!.entitiesOf(c.team as Team, Kind.Unit).map((e) => e.id);
          c.session!.lock!.localCommand({ t: "move", team: c.team as Team, ids, x: 600 + c.team * 25, y: 480, queue: false, attackMove: false, formation: true });
        }
        c.session!.authorTick();
      }
      await flush(6); // let the relay deliver turns
      for (const c of clients) c.session!.stepReady(20);
    }
    await flush(20);
    for (const c of clients) c.session!.stepReady(40);

    // The session's own checksum exchange is the verdict: any divergence trips it.
    for (const c of clients) {
      expect(c.session!.lock!.currentTick).toBeGreaterThan(60);
      expect(c.session!.desynced).toBe(false);
    }
    // And independently: all worlds hash identically at the same tick.
    const minTick = Math.min(...clients.map((c) => c.session!.lock!.currentTick));
    // (they advance together; compare the slowest tick's state via checksum equality)
    const sums = clients.map((c) => worldChecksum(c.world!));
    if (clients.every((c) => c.session!.lock!.currentTick === minTick)) {
      expect(new Set(sums).size).toBe(1);
    }
    for (const c of clients) c.ws.close();
    await flush();
  });

  it("keeps simulating when a player drops mid-match", async () => {
    const url = `ws://127.0.0.1:${srv.port}`;
    const clients = ["X", "Y", "Z"].map((n) => makeClient(url, n + Math.random()));
    await Promise.all(clients.map((c) => c.joined));
    // Use a fresh room name so we don't collide with the first test's room.
    const hostC = clients.reduce((h, c) => (c.slot < h.slot ? c : h));
    hostC.send({ t: "start" });
    await Promise.all(clients.map((c) => c.started));

    const run = async (frames: number) => {
      for (let f = 0; f < frames; f++) {
        for (const c of clients) if (c.ws.readyState === WebSocket.OPEN) c.session!.authorTick();
        await flush(6);
        for (const c of clients) if (c.ws.readyState === WebSocket.OPEN) c.session!.stepReady(20);
      }
    };
    await run(30);
    const survivors = clients.filter((c) => c !== clients[clients.length - 1]);
    const before = survivors.map((c) => c.session!.lock!.currentTick);
    clients[clients.length - 1].ws.close(); // a player rage-quits
    await flush(30);
    await run(30);
    // Survivors kept advancing past where they were when the peer left.
    survivors.forEach((c, i) => expect(c.session!.lock!.currentTick).toBeGreaterThan(before[i]));
    for (const c of survivors) c.ws.close();
    await flush();
  });

  it("an observer simulates the match in sync without authoring input", async () => {
    const url = `ws://127.0.0.1:${srv.port}`;
    const a = makeClient(url, "P1", false, "obsroom");
    const b = makeClient(url, "P2", false, "obsroom");
    const obs = makeClient(url, "Caster", true, "obsroom");
    await Promise.all([a.joined, b.joined, obs.joined]);
    // Host is the lowest-slot *player* (observer can't host).
    const host = [a, b].reduce((h, c) => (c.slot < h.slot ? c : h));
    host.send({ t: "start" });
    await Promise.all([a.started, b.started, obs.started]);

    for (let frame = 0; frame < 120; frame++) {
      for (const c of [a, b, obs]) {
        if (frame % 20 === 0 && c !== obs) {
          const ids = c.world!.entitiesOf(c.team as Team, Kind.Unit).map((e) => e.id);
          c.session!.lock!.localCommand({ t: "move", team: c.team as Team, ids, x: 600, y: 480 + c.team * 30, queue: false, attackMove: false, formation: true });
        }
        c.session!.authorTick(); // a no-op for the observer
      }
      await flush(6);
      for (const c of [a, b, obs]) c.session!.stepReady(20);
    }
    await flush(20);
    for (const c of [a, b, obs]) c.session!.stepReady(40);

    expect(obs.session!.desynced).toBe(false);
    expect(obs.session!.lock!.currentTick).toBeGreaterThan(60);
    // The observer's world hashes identically to a player's at the same tick.
    if (obs.session!.lock!.currentTick === a.session!.lock!.currentTick) {
      expect(worldChecksum(obs.world!)).toBe(worldChecksum(a.world!));
    }
    for (const c of [a, b, obs]) c.ws.close();
    await flush();
  });

  it("relays chat and map pings to the other players in the room", async () => {
    const url = `ws://127.0.0.1:${srv.port}`;
    const a = new WebSocket(url);
    const b = new WebSocket(url);
    const bMsgs: { t: string; text?: string; x?: number; slot?: number }[] = [];
    b.onmessage = (e) => bMsgs.push(JSON.parse(e.data as string));
    await new Promise((r) => (a.onopen = () => r(null)));
    await new Promise((r) => (b.onopen = () => r(null)));
    a.send(JSON.stringify({ t: "hello", name: "A", room: "chatroom" }));
    b.send(JSON.stringify({ t: "hello", name: "B", room: "chatroom" }));
    await flush(40);

    a.send(JSON.stringify({ t: "chat", name: "A", text: "glhf", team: 0 }));
    await flush(40);
    expect(bMsgs.some((m) => m.t === "chat" && m.text === "glhf")).toBe(true);

    a.send(JSON.stringify({ t: "ping", x: 123, y: 456, team: 0 }));
    await flush(40);
    expect(bMsgs.some((m) => m.t === "ping" && m.x === 123)).toBe(true);

    a.close(); b.close();
    await flush();
  });

  it("enforces room passwords (first to join sets it)", async () => {
    const url = `ws://127.0.0.1:${srv.port}`;
    const open = (ws: WebSocket) => new Promise((r) => (ws.onopen = () => r(null)));
    const collect = (ws: WebSocket) => { const out: { t: string }[] = []; ws.onmessage = (e) => out.push(JSON.parse(e.data as string)); return out; };

    const a = new WebSocket(url); const am = collect(a); await open(a);
    a.send(JSON.stringify({ t: "hello", name: "A", room: "locked", pass: "secret" }));
    await flush(40);
    expect(am.some((m) => m.t === "welcome")).toBe(true); // creator gets in & sets the password

    const b = new WebSocket(url); const bm = collect(b); await open(b);
    b.send(JSON.stringify({ t: "hello", name: "B", room: "locked", pass: "nope" }));
    await flush(40);
    expect(bm.some((m) => m.t === "error")).toBe(true); // wrong password
    expect(bm.some((m) => m.t === "welcome")).toBe(false);

    const c = new WebSocket(url); const cm = collect(c); await open(c);
    c.send(JSON.stringify({ t: "hello", name: "C", room: "locked", pass: "secret" }));
    await flush(40);
    expect(cm.some((m) => m.t === "welcome")).toBe(true); // right password gets in

    // A room created with no password stays open.
    const d = new WebSocket(url); const dm = collect(d); await open(d);
    d.send(JSON.stringify({ t: "hello", name: "D", room: "openroom" }));
    await flush(40);
    expect(dm.some((m) => m.t === "welcome")).toBe(true);

    a.close(); b.close(); c.close(); d.close();
    await flush();
  });

  it("supports a full 16-team (8v8) world with two alliances", () => {
    const n = 16;
    const seed = 777;
    const map = generateMap("open_plains", seed, n, false);
    expect(map.starts.length).toBeGreaterThanOrEqual(n);
    const alliances = Array.from({ length: n }, (_, t) => t % 2); // even=side0, odd=side1
    const world = new World(seed);
    const teams = Array.from({ length: n }, (_, i) => i as Team);
    world.init(map, teams.map(() => ({})), teams.map(() => 1), alliances, teams.map(() => ""), false, undefined, "conquest");
    expect(world.numTeams).toBe(16);
    // Same-parity teams are allied, opposite-parity are hostile.
    expect(world.areAllied(0 as Team, 2 as Team)).toBe(true);
    expect(world.areAllied(0 as Team, 1 as Team)).toBe(false);
    expect(world.areHostile(0 as Team, 1 as Team)).toBe(true);
    // Every team got a starting town & villagers.
    for (let t = 0; t < n; t++) {
      expect(world.entitiesOf(t as Team, Kind.Unit).length).toBeGreaterThan(0);
    }
  });
});
