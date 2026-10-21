import { describe, expect, it, afterAll, beforeAll } from "vitest";
// Node built-ins; the project has no @types/node.
// @ts-ignore
import { mkdtempSync, readFileSync, existsSync } from "fs";
// @ts-ignore
import { tmpdir } from "os";
// @ts-ignore
import { join } from "path";
import { startServer, PROTOCOL } from "../../server/server.mjs";

/**
 * The operator's view: every match the server sees is recorded — mode, map,
 * factions (Random resolved), sides, winner, length, quitters — and the admin
 * dashboard turns that into pick rates, win rates, match-ups, map popularity,
 * game lengths and live activity, behind a token.
 */

const TOKEN = "correct-horse-battery-staple";
const flush = (ms = 50) => new Promise((r) => setTimeout(r, ms));
type Msg = Record<string, unknown> & { t: string };
let pidN = 0;

function client(url: string, name: string) {
  const ws = new WebSocket(url);
  const got: Msg[] = [];
  const pid = (++pidN + 1000).toString(16).padStart(32, "b");
  ws.onmessage = (e) => got.push(JSON.parse(e.data as string));
  const open = new Promise<void>((r) => (ws.onopen = () => r()));
  const send = (m: Record<string, unknown>) => ws.send(JSON.stringify({ name, pid, v: PROTOCOL, ...m }));
  return { ws, got, open, send, last: (t: string) => [...got].reverse().find((m) => m.t === t) as Msg | undefined };
}

let srv: { port: number; close: () => Promise<void> };
let dir: string;
const base = () => `http://127.0.0.1:${srv.port}`;
const url = () => `ws://127.0.0.1:${srv.port}`;
const admin = (path: string, init: RequestInit = {}, token = TOKEN) =>
  fetch(base() + path, { ...init, headers: { authorization: `Bearer ${token}`, "content-type": "application/json", ...(init.headers || {}) } });

async function start() {
  srv = await startServer(0, "127.0.0.1", {
    gameHtml: false, adminToken: TOKEN, quickCountdownMs: 30, sampleMs: 40,
    mapsFile: join(dir, "maps.json"), playersFile: join(dir, "players.json"), matchesFile: join(dir, "matches.jsonl"),
  });
}
beforeAll(async () => { dir = mkdtempSync(join(tmpdir(), "bb-admin-")); await start(); });
afterAll(async () => { await srv.close(); });

/** Play a quick 1v1 to the end: `a` with faction fa wins in `minutes`. */
async function duel(fa: string, fb: string, minutes: number, aWins = true) {
  const a = client(url(), `P${pidN}a`), b = client(url(), `P${pidN}b`);
  await Promise.all([a.open, b.open]);
  a.send({ t: "quick", mode: "1v1" });
  await flush(20);
  b.send({ t: "quick", mode: "1v1" });
  await flush(120);
  const start = a.last("start")!;
  const st = start.slotTeams as { slot: number; team: number }[];
  const ta = st.find((s) => s.slot === a.last("welcome")!.slot)!.team;
  const tb = st.find((s) => s.slot === b.last("welcome")!.slot)!.team;
  const list: string[] = [];
  list[ta] = fa; list[tb] = fb;
  a.send({ t: "factions", list });
  const winner = aWins ? ta : tb;
  a.send({ t: "result", winner, time: minutes * 60, factions: list });
  b.send({ t: "result", winner, time: minutes * 60, factions: list });
  await flush(60);
  a.ws.close(); b.ws.close();
  await flush(30);
}

describe("The admin dashboard", () => {
  it("is off without a token and keeps strangers out with one", async () => {
    const off = await startServer(0, "127.0.0.1", { gameHtml: false, adminToken: "", mapsFile: join(dir, "m2.json") });
    expect((await fetch(`http://127.0.0.1:${off.port}/admin/api/live`)).status).toBe(503);
    await off.close();
    expect((await admin("/admin/api/live", {}, "wrong")).status).toBe(401);
    expect((await admin("/admin/api/live")).status).toBe(200);
    const page = await fetch(base() + "/admin");
    expect(page.headers.get("content-type")).toContain("text/html");
    expect(await page.text()).toContain("Banner &amp; Blade");
  });

  it("locks out an address that keeps guessing", async () => {
    const other = await startServer(0, "127.0.0.1", { gameHtml: false, adminToken: TOKEN, mapsFile: join(dir, "m3.json") });
    const hit = (t: string) => fetch(`http://127.0.0.1:${other.port}/admin/api/live`, { headers: { authorization: `Bearer ${t}` } });
    for (let i = 0; i < 10; i++) await hit("guess" + i);
    expect((await hit(TOKEN)).status, "even the right token, once locked").toBe(429);
    await other.close();
  });

  it("records every match and turns it into faction, map and length stats", async () => {
    await duel("legion", "norse", 12);
    await duel("legion", "khanate", 20);
    await duel("norse", "legion", 8);      // Jarls beat Legion
    await duel("kingdom", "shogunate", 30, false); // Shogunate wins
    const S = await (await admin("/admin/api/stats?range=all")).json();
    expect(S.overview.matches).toBe(4);
    expect(S.overview.completed).toBe(4);
    expect(S.overview.ranked).toBe(4);
    const legion = S.factions.find((f: { faction: string }) => f.faction === "legion");
    expect(legion.picks).toBe(3);
    expect(legion.wins).toBe(2);
    expect(legion.winRate).toBeCloseTo(66.7, 1);
    expect(S.factions[0].faction, "most-picked first").toBe("legion");
    expect(S.matchup.norse.legion).toEqual({ wins: 1, games: 2 }); // they met twice, one win each
    expect(S.matchup.legion.norse).toEqual({ wins: 1, games: 2 });
    expect(S.matchup.shogunate.kingdom).toEqual({ wins: 1, games: 1 });
    expect(S.overview.avgMinutes).toBeCloseTo(17.5, 1);
    expect(S.overview.medianMinutes).toBeCloseTo(16, 1);
    const hist = Object.fromEntries(S.lengths.histogram.map((b: { label: string; count: number }) => [b.label, b.count]));
    expect(hist["5–10"]).toBe(1);
    expect(hist["30–45"]).toBe(1);
    expect(S.maps.reduce((n: number, m: { plays: number }) => n + m.plays, 0)).toBe(4);
    expect(S.formats[0]).toMatchObject({ format: "1 v 1", count: 4 });
    expect(S.players.rated).toBe(8);
    expect(S.activity.perDay.at(-1).matches).toBe(4);
  });

  it("records a match nobody finished as abandoned", async () => {
    const a = client(url(), "Leaver1"), b = client(url(), "Leaver2");
    await Promise.all([a.open, b.open]);
    a.send({ t: "create", room: "Doomed" });
    await flush(30);
    b.send({ t: "join", room: "Doomed" });
    await flush(30);
    a.send({ t: "start" });
    await flush(40);
    a.ws.close(); b.ws.close();
    await flush(80);
    const ms = await (await admin("/admin/api/matches?n=5")).json();
    expect(ms[0]).toMatchObject({ room: "Doomed", outcome: "abandoned", kind: "custom", quitters: 2 });
    expect(JSON.stringify(ms), "player ids never leave the server").not.toMatch(/b{20}/);
  });

  it("shows what's happening right now", async () => {
    const a = client(url(), "Watcher"), b = client(url(), "Host");
    await Promise.all([a.open, b.open]);
    a.send({ t: "hub" });
    b.send({ t: "create", room: "Live Room" });
    await flush(80);
    const L = await (await admin("/admin/api/live")).json();
    expect(L.online).toBeGreaterThanOrEqual(2);
    expect(L.rooms.map((r: { name: string }) => r.name)).toContain("Live Room");
    expect(L.server.rssMB).toBeGreaterThan(0);
    expect(L.counters.connections).toBeGreaterThan(2);
    // Announce reaches everyone; closing a room sends them to the hub.
    const sent = await (await admin("/admin/api/announce", { method: "POST", body: JSON.stringify({ text: "Restart in 5 minutes" }) })).json();
    expect(sent.sent).toBeGreaterThanOrEqual(2);
    await admin("/admin/api/close-room", { method: "POST", body: JSON.stringify({ name: "Live Room" }) });
    await flush(60);
    expect(a.last("announce")!.text).toBe("Restart in 5 minutes");
    expect(b.last("left")!.why).toBe("closed");
    const S = await (await admin("/admin/api/stats?range=24h")).json();
    expect(S.activity.samples.length, "per-minute samples of who was online").toBeGreaterThan(0);
    a.ws.close(); b.ws.close();
  });

  it("exports the history as CSV, and keeps it across a restart", async () => {
    const csv = await (await admin("/admin/api/matches.csv")).text();
    const lines = csv.trim().split("\n");
    expect(lines[0]).toContain("winningFactions");
    expect(lines.length).toBe(1 + 5);
    await srv.close();
    await flush(50);
    expect(existsSync(join(dir, "matches.jsonl"))).toBe(true);
    expect(readFileSync(join(dir, "matches.jsonl"), "utf8").trim().split("\n").length).toBe(5);
    await start();
    const S = await (await admin("/admin/api/stats?range=all")).json();
    expect(S.overview.matches).toBe(5);
    expect(S.players.top.length).toBe(8);
  });
});
