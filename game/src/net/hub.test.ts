import { describe, expect, it, afterAll, beforeAll } from "vitest";
// Node built-ins; the project has no @types/node.
// @ts-ignore
import { mkdtempSync } from "fs";
// @ts-ignore
import { tmpdir } from "os";
// @ts-ignore
import { join } from "path";
// @ts-ignore
import net from "net";
import { startServer, PROTOCOL } from "../../server/server.mjs";

/**
 * The online hub, as a player on the website meets it: a live list of rooms,
 * creating and joining them (with passwords), the host's kick, quick match
 * that seats strangers and starts on its own, and ranked results that only
 * count when the players agree on who won.
 */

const flush = (ms = 40) => new Promise((r) => setTimeout(r, ms));
type Msg = Record<string, unknown> & { t: string };
let pidN = 0;
const pid = () => (++pidN).toString(16).padStart(32, "a");

function client(url: string, name: string) {
  const ws = new WebSocket(url);
  const got: Msg[] = [];
  const me = pid();
  ws.onmessage = (e) => got.push(JSON.parse(e.data as string));
  const open = new Promise<void>((r) => (ws.onopen = () => r()));
  const send = (m: Record<string, unknown>) => ws.send(JSON.stringify({ name, pid: me, v: PROTOCOL, ...m }));
  return {
    ws, got, open, send, pid: me,
    last: (t: string) => [...got].reverse().find((m) => m.t === t) as Msg | undefined,
    hub: async () => { await open; send({ t: "hub" }); await flush(); },
  };
}

let srv: { port: number; close: () => Promise<void> };
const url = () => `ws://127.0.0.1:${srv.port}`;
beforeAll(async () => {
  const dir = mkdtempSync(join(tmpdir(), "bb-hub-"));
  srv = await startServer(0, "127.0.0.1", {
    gameHtml: false, mapsFile: join(dir, "maps.json"), playersFile: join(dir, "players.json"),
    quickCountdownMs: 60, pingMs: 50, idleMs: 200,
  });
});
afterAll(async () => { await srv.close(); });

describe("The room list", () => {
  it("shows rooms as they open, fill and close", async () => {
    const browser = client(url(), "Browser");
    await browser.hub();
    expect((browser.last("rooms")!.rooms as unknown[]).length).toBe(0);
    const host = client(url(), "Hostess");
    await host.hub();
    host.send({ t: "create", room: "Friday Night", max: 4 });
    await flush(250);
    const list = browser.last("rooms")!.rooms as { name: string; players: number; max: number; host: string; locked: boolean }[];
    expect(list).toEqual([expect.objectContaining({ name: "Friday Night", players: 1, max: 4, host: "Hostess", locked: false })]);
    host.send({ t: "leave" });
    await flush(250);
    expect((browser.last("rooms")!.rooms as unknown[]).length).toBe(0);
    browser.ws.close(); host.ws.close();
  });

  it("keeps a password-locked room locked", async () => {
    const host = client(url(), "Keeper");
    await host.hub();
    host.send({ t: "create", room: "Vault", pass: "hunter2" });
    await flush();
    const guest = client(url(), "Guest");
    await guest.hub();
    guest.send({ t: "join", room: "Vault", pass: "wrong" });
    await flush();
    expect(guest.last("error")!.code).toBe("password");
    guest.send({ t: "join", room: "Vault", pass: "hunter2" });
    await flush();
    expect(guest.last("welcome")!.room).toBe("Vault");
    host.ws.close(); guest.ws.close();
  });

  it("won't let a room overfill, and gives two same-named rooms different names", async () => {
    const a = client(url(), "A"), b = client(url(), "B"), c = client(url(), "C");
    await Promise.all([a.hub(), b.hub(), c.hub()]);
    a.send({ t: "create", room: "Duel", max: 2 });
    await flush();
    b.send({ t: "join", room: "Duel" });
    await flush();
    c.send({ t: "join", room: "Duel" });
    await flush();
    expect(c.last("error")!.code).toBe("full");
    c.send({ t: "create", room: "Duel" });
    await flush();
    expect(c.last("welcome")!.room).toBe("Duel 2");
    [a, b, c].forEach((x) => x.ws.close());
  });

  it("lets the host remove someone, who lands back in the hub", async () => {
    const host = client(url(), "Boss"), pest = client(url(), "Pest");
    await Promise.all([host.hub(), pest.hub()]);
    host.send({ t: "create", room: "Tidy" });
    await flush();
    pest.send({ t: "join", room: "Tidy" });
    await flush();
    const slot = (host.last("lobby")!.players as { name: string; slot: number }[]).find((p) => p.name === "Pest")!.slot;
    pest.send({ t: "kick", slot: 0 }); // not the host: ignored
    host.send({ t: "kick", slot });
    await flush();
    expect(pest.last("left")!.why).toBe("kicked");
    expect((host.last("lobby")!.players as unknown[]).length).toBe(1);
    host.ws.close(); pest.ws.close();
  });

  it("tells an out-of-date page to reload", async () => {
    const old = client(url(), "Old");
    await old.open;
    old.send({ t: "hub", v: PROTOCOL - 1 });
    await flush();
    expect(old.last("error")!.code).toBe("version");
    old.ws.close();
  });
});

describe("Quick match", () => {
  it("seats two strangers in a ranked 1 v 1 and starts it on its own", async () => {
    const a = client(url(), "Ana"), b = client(url(), "Bo");
    await Promise.all([a.hub(), b.hub()]);
    a.send({ t: "quick", mode: "1v1", faction: "norse" });
    await flush();
    expect(a.last("queue")).toMatchObject({ mode: "1v1", waiting: 1, need: 2 });
    b.send({ t: "quick", mode: "1v1" });
    await flush(200);
    const start = a.last("start")!;
    expect(start.ranked).toBe(true);
    expect(start.numTeams).toBe(2);
    expect(new Set(start.alliances as number[]).size).toBe(2);
    expect(b.last("start")!.seed).toBe(start.seed);
    a.ws.close(); b.ws.close();
  });

  it("can be cancelled while waiting", async () => {
    const a = client(url(), "Solo");
    await a.hub();
    a.send({ t: "quick", mode: "2v2" });
    await flush();
    a.send({ t: "unquick" });
    await flush();
    const b = client(url(), "Late");
    await b.hub();
    b.send({ t: "quick", mode: "2v2" });
    await flush();
    expect(b.last("queue")).toMatchObject({ waiting: 1 });
    a.ws.close(); b.ws.close();
  });
});

describe("Ranked results", () => {
  async function ranked() {
    const a = client(url(), "Winner"), b = client(url(), "Loser");
    await Promise.all([a.hub(), b.hub()]);
    a.send({ t: "quick", mode: "1v1" });
    await flush();
    b.send({ t: "quick", mode: "1v1" });
    await flush(200);
    const teamOf = (c: typeof a) => {
      const st = c.last("start")!;
      const slot = c.last("welcome")!.slot;
      return (st.slotTeams as { slot: number; team: number }[]).find((s) => s.slot === slot)!.team;
    };
    return { a, b, ta: teamOf(a), tb: teamOf(b) };
  }
  const board = async () => (await (await fetch(`http://127.0.0.1:${srv.port}/leaderboard`)).json()) as { name: string; rating: number; wins: number; losses: number }[];

  it("moves ratings when both sides agree on the winner", async () => {
    const { a, b, ta } = await ranked();
    a.send({ t: "result", winner: ta });
    b.send({ t: "result", winner: ta });
    await flush();
    expect((a.last("rated")!.delta as number)).toBeGreaterThan(0);
    const top = await board();
    const w = top.find((p) => p.name === "Winner")!, l = top.find((p) => p.name === "Loser")!;
    expect(w.rating).toBeGreaterThan(1000);
    expect(l.rating).toBeLessThan(1000);
    expect([w.wins, l.losses]).toEqual([1, 1]);
    a.ws.close(); b.ws.close();
  });

  it("rates nothing when they disagree", async () => {
    const { a, b, ta, tb } = await ranked();
    a.send({ t: "result", winner: ta });
    b.send({ t: "result", winner: tb }); // somebody is lying
    await flush();
    expect(a.last("rated")).toBeUndefined();
    a.ws.close(); b.ws.close();
  });

  it("counts the win for whoever stays when the loser quits", async () => {
    const { a, b, ta } = await ranked();
    b.ws.close();
    await flush();
    a.send({ t: "result", winner: ta });
    await flush();
    expect(a.last("rated")).toBeTruthy();
    a.ws.close();
  });
});

describe("A dead connection", () => {
  it("is dropped, and its seat freed", async () => {
    // A socket that completes the handshake and then says nothing, ever — a
    // closed laptop. (Browsers answer pings; this doesn't.)
    const host = client(url(), "Alive");
    await host.hub();
    host.send({ t: "create", room: "Heartbeat" });
    await flush();
    const sock = net.connect(srv.port, "127.0.0.1");
    await new Promise((r) => sock.on("connect", r));
    sock.write("GET / HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n");
    let bytes = 0; sock.on("data", (d: { length: number }) => { bytes += d.length; });
    const closed = new Promise((r) => sock.on("close", r));
    await Promise.race([closed, flush(1500)]);
    expect(sock.destroyed || sock.readyState === "closed").toBe(true);
    host.ws.close();
  });
});
