import { describe, expect, it, beforeAll, afterAll } from "vitest";
import { startServer } from "../../server/server.mjs";
import { NetLobby, NetStart, normalizeWsUrl } from "./net_lobby";

// --- A minimal DOM, just enough to run NetLobby headlessly -------------------
class FakeEl {
  tag: string; id = ""; children: FakeEl[] = []; _html = "";
  style: Record<string, string> = { cssText: "", opacity: "" };
  value = ""; placeholder = ""; textContent = ""; disabled = false; readOnly = false; spellcheck = false;
  onclick: (() => void) | null = null;
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

function installDom() {
  const body = new FakeEl("body");
  (globalThis as unknown as { document: unknown }).document = { createElement: (t: string) => new FakeEl(t), body };
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
  };
  return body;
}
const flush = (ms = 25) => new Promise((r) => setTimeout(r, ms));

describe("normalizeWsUrl — forgiving server address entry", () => {
  it("accepts bare host, host:port, and full ws urls", () => {
    expect(normalizeWsUrl("26.13.45.201")).toBe("ws://26.13.45.201:8787");
    expect(normalizeWsUrl("26.13.45.201:9000")).toBe("ws://26.13.45.201:9000");
    expect(normalizeWsUrl("ws://10.0.0.5:8787")).toBe("ws://10.0.0.5:8787");
    expect(normalizeWsUrl("  localhost  ")).toBe("ws://localhost:8787");
    expect(normalizeWsUrl("wss://play.example.com:443")).toBe("wss://play.example.com:443");
    expect(normalizeWsUrl("")).toBe("");
  });
});

describe("Multiplayer menu — full click-through against a live server", () => {
  let srv: { port: number; close: () => Promise<void> };
  let body: FakeEl;
  beforeAll(async () => { body = installDom(); srv = await startServer(0, "127.0.0.1"); });
  afterAll(async () => { await srv.close(); });

  const overlay = () => body.children[body.children.length - 1];
  const button = (label: string) => walk(overlay()).find((e) => e.tag === "button" && e.textContent.includes(label));
  const inputs = () => walk(overlay()).filter((e) => e.tag === "input");
  const panelHtml = () => overlay().querySelector("#mp-panel")!.innerHTML;

  it("Home → Join a Server → connect → host starts → fires NetStart", async () => {
    let started: NetStart | null = null;
    const lobby = new NetLobby();
    lobby.open((s) => { started = s; });

    // Home screen shows the two entry points.
    expect(button("Join a Server")).toBeTruthy();
    expect(button("Quick 1v1")).toBeTruthy();

    // Enter the server screen and fill in the address.
    button("Join a Server")!.onclick!();
    const [url, , room] = inputs();
    url.value = `ws://127.0.0.1:${srv.port}`;
    room.value = "menutest";
    button("Connect")!.onclick!();
    await flush(60); // connect + welcome + lobby render

    // We're alone and therefore the host; Start is present but disabled at 1 player.
    expect(panelHtml()).toContain("Lobby");
    const startBtn1 = button("Start Match");
    expect(startBtn1).toBeTruthy();
    expect(startBtn1!.disabled).toBe(true);

    // A second player joins the same room → Start becomes enabled.
    const other = new WebSocket(`ws://127.0.0.1:${srv.port}`);
    other.onopen = () => other.send(JSON.stringify({ t: "hello", name: "Buddy", room: "menutest" }));
    await flush(60);
    expect(panelHtml()).toContain("2/16");
    const startBtn2 = button("Start Match");
    expect(startBtn2!.disabled).toBe(false);

    // Host starts the match.
    startBtn2!.onclick!();
    await flush(60);

    expect(started).not.toBeNull();
    const s = started! as NetStart;
    expect(s.numTeams).toBe(2);
    expect(s.teams).toEqual([0, 1]);
    expect(s.alliances.length).toBe(2);
    expect(typeof s.seed).toBe("number");
    expect(s.transport).toBeTruthy();          // a live WSLink to hand to NetSession
    expect([0, 1]).toContain(s.localTeam);

    s.transport.close();
    other.close();
    await flush();
  });

  it("Home → Quick 1v1 shows the no-server Host/Join options", () => {
    const lobby = new NetLobby();
    lobby.open(() => { /* */ });
    button("Quick 1v1")!.onclick!(); // navigation only — don't invoke WebRTC headlessly
    expect(button("Host")).toBeTruthy();
    expect(button("Join")).toBeTruthy();
    expect(button("Back")).toBeTruthy();
    lobby.close();
  });
});
