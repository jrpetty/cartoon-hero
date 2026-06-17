// Multiplayer lobby — a DOM overlay (canvas UI can't host editable text).
// Two ways in:
//   • Server (2–16 players, up to 8v8): connect to a relay you host (e.g. on a
//     VPN), pick a side, ready up, host starts. Scales far better than a mesh.
//   • Quick 1v1 (no server): serverless WebRTC with copy/paste codes.
// Nothing here runs until opened, so headless boot is unaffected.

import { Team } from "../sim/types";
import { Transport } from "../net/transport";
import { PeerLink } from "../net/webrtc";
import { WSLink } from "../net/ws";

export interface NetStart {
  transport: Transport;
  localTeam: Team;
  teams: Team[];
  alliances: number[];
  seed: number;
  numTeams: number;
}

interface LobbyPlayer { slot: number; name: string; side: number; ready: boolean; }

const LS_URL = "bb_server_url";
const LS_NAME = "bb_player_name";

export class NetLobby {
  private el: HTMLDivElement | null = null;
  private link: PeerLink | null = null;
  private ws: WSLink | null = null;
  private slot = -1;
  private hostSlot = -1;
  private players: LobbyPlayer[] = [];
  private onStart?: (s: NetStart) => void;

  open(onStart: (s: NetStart) => void) {
    this.onStart = onStart;
    this.build();
    this.showHome();
  }
  close() { this.el?.remove(); this.el = null; }

  // --- DOM helpers ---------------------------------------------------------
  private build() {
    const el = document.createElement("div");
    el.style.cssText = ["position:fixed", "inset:0", "display:flex", "align-items:center", "justify-content:center",
      "background:rgba(8,6,3,0.82)", "z-index:50", "font-family:system-ui,sans-serif", "color:#e8ddc4"].join(";");
    const panel = document.createElement("div");
    panel.id = "mp-panel";
    panel.style.cssText = ["width:600px", "max-width:94vw", "max-height:90vh", "overflow:auto", "background:#221b12",
      "border:2px solid #c9a24a", "border-radius:12px", "padding:24px", "box-shadow:0 12px 40px rgba(0,0,0,0.6)"].join(";");
    el.appendChild(panel);
    document.body.appendChild(el);
    this.el = el;
  }
  private panel() { return this.el!.querySelector("#mp-panel") as HTMLDivElement; }
  private set(html: string) { this.panel().innerHTML = html; }
  private btn(label: string, primary = true) {
    const b = document.createElement("button");
    b.textContent = label;
    b.style.cssText = ["padding:10px 16px", "margin:6px 8px 6px 0", "border-radius:8px", "cursor:pointer",
      "border:1px solid #c9a24a", "font-size:15px", "font-weight:600",
      primary ? "background:#c9a24a;color:#221b12" : "background:#2e2618;color:#e8ddc4"].join(";");
    return b;
  }
  private field(value: string, placeholder = "") {
    const i = document.createElement("input");
    i.value = value; i.placeholder = placeholder; i.spellcheck = false;
    i.style.cssText = ["width:100%", "margin:6px 0", "box-sizing:border-box", "background:#15110a", "color:#e8ddc4",
      "border:1px solid #5a4a2a", "border-radius:6px", "padding:9px", "font-size:14px"].join(";");
    return i;
  }
  private area(value: string, readonly: boolean) {
    const t = document.createElement("textarea");
    t.value = value; t.readOnly = readonly; t.spellcheck = false;
    t.style.cssText = ["width:100%", "height:80px", "margin:8px 0", "box-sizing:border-box", "resize:none",
      "background:#15110a", "color:#cabfa4", "border:1px solid #5a4a2a", "border-radius:6px", "padding:8px",
      "font-family:monospace", "font-size:11px"].join(";");
    return t;
  }
  private title(t: string) { return `<div style="font-size:22px;font-weight:700;color:#ffe9b0;margin-bottom:4px">${t}</div>`; }
  private note(t: string) { return `<div style="color:#bdb49a;font-size:13px;margin-bottom:12px">${t}</div>`; }
  private ls(key: string, fallback: string) { try { return localStorage.getItem(key) || fallback; } catch { return fallback; } }
  private save(key: string, v: string) { try { localStorage.setItem(key, v); } catch { /* */ } }

  // --- Home ----------------------------------------------------------------
  private showHome() {
    this.set(this.title("Multiplayer") + this.note("Play with friends — up to 8 vs 8."));
    const p = this.panel();
    const server = this.btn("🖧  Join a Server  (2–16 players)");
    const p2p = this.btn("⚡  Quick 1v1  (no server)", false);
    const cancel = this.btn("Cancel", false);
    server.onclick = () => this.showServerConnect();
    p2p.onclick = () => this.showP2PHome();
    cancel.onclick = () => this.cancel();
    p.append(server, document.createElement("br"), p2p, document.createElement("br"), cancel);
  }

  // --- Server flow ---------------------------------------------------------
  private showServerConnect() {
    this.set(this.title("Join a Server") +
      this.note("Enter the address of the relay (run <code>node server/server.mjs</code> on a box your players can reach — e.g. its VPN address). Everyone uses the same room name."));
    const p = this.panel();
    const url = this.field(this.ls(LS_URL, "ws://localhost:8787"), "ws://10.0.0.5:8787");
    const name = this.field(this.ls(LS_NAME, "Player"), "Your name");
    const room = this.field("main", "Room name");
    const connect = this.btn("Connect");
    const back = this.btn("Back", false);
    const status = document.createElement("div");
    status.style.cssText = "color:#e0a05a;font-size:13px;margin-top:8px";
    back.onclick = () => this.showHome();
    connect.onclick = () => {
      this.save(LS_URL, url.value); this.save(LS_NAME, name.value);
      status.textContent = "Connecting…";
      this.connectServer(url.value.trim(), name.value.trim() || "Player", room.value.trim() || "main", (msg) => (status.textContent = msg));
    };
    p.append("Server address", url, "Display name", name, "Room", room, connect, back, status);
  }

  private connectServer(url: string, name: string, room: string, status: (m: string) => void) {
    const ws = new WSLink(url);
    this.ws = ws;
    ws.onOpen = () => ws.send({ t: "hello", name, room });
    ws.onClose = () => status("Disconnected. Check the address and that the server is running.");
    ws.onData = (raw) => {
      const m = raw as { t: string; slot?: number; host?: number; players?: LobbyPlayer[]; msg?: string;
        seed?: number; numTeams?: number; alliances?: number[]; slotTeams?: { slot: number; team: number }[] };
      if (m.t === "welcome") { this.slot = m.slot!; }
      else if (m.t === "lobby") { this.hostSlot = m.host!; this.players = m.players!; this.renderRoom(); }
      else if (m.t === "error") { status(m.msg || "Server refused the connection."); }
      else if (m.t === "start") {
        const localTeam = m.slotTeams!.find((s) => s.slot === this.slot)!.team as Team;
        const teams = Array.from({ length: m.numTeams! }, (_, i) => i as Team);
        this.finish({ transport: ws, localTeam, teams, alliances: m.alliances!, seed: m.seed!, numTeams: m.numTeams! });
      }
    };
  }

  private renderRoom() {
    const isHost = this.slot === this.hostSlot;
    const sideName = (s: number) => (s === 0 ? "Side A" : "Side B");
    const rows = this.players.map((pl) => {
      const meTag = pl.slot === this.slot ? " (you)" : "";
      const hostTag = pl.slot === this.hostSlot ? " ⭐" : "";
      const col = pl.side === 0 ? "#7da3ec" : "#ec837d";
      return `<div style="display:flex;justify-content:space-between;padding:4px 0;border-bottom:1px solid #3a2f1c">
        <span>${pl.ready ? "✅" : "⬜"} ${pl.name}${meTag}${hostTag}</span>
        <span style="color:${col}">${sideName(pl.side)}</span></div>`;
    }).join("");
    const counts = [0, 1].map((s) => this.players.filter((p) => p.side === s).length);
    this.set(this.title(`Lobby — ${this.players.length}/16`) +
      this.note(`Sides: <b style="color:#7da3ec">A ${counts[0]}</b> vs <b style="color:#ec837d">B ${counts[1]}</b>. ${isHost ? "You're the host — start when everyone's in." : "Waiting for the host to start…"}`) +
      `<div style="margin:8px 0">${rows}</div>`);
    const p = this.panel();
    const me = this.players.find((pl) => pl.slot === this.slot);
    const toA = this.btn("Side A", me?.side !== 0); toA.onclick = () => this.ws!.send({ t: "side", side: 0 });
    const toB = this.btn("Side B", me?.side !== 1); toB.onclick = () => this.ws!.send({ t: "side", side: 1 });
    const ready = this.btn(me?.ready ? "Unready" : "Ready", !me?.ready);
    ready.onclick = () => this.ws!.send({ t: "ready", ready: !me?.ready });
    p.append(toA, toB, ready);
    if (isHost) {
      const start = this.btn("⚔ Start Match");
      start.disabled = this.players.length < 2;
      if (start.disabled) start.style.opacity = "0.5";
      start.onclick = () => this.ws!.send({ t: "start" });
      p.append(start);
    }
    const leave = this.btn("Leave", false);
    leave.onclick = () => this.cancel();
    p.append(leave);
  }

  // --- P2P (serverless 1v1) flow ------------------------------------------
  private showP2PHome() {
    this.set(this.title("Quick 1v1 — no server") + this.note("One of you hosts and shares a code; the other joins and sends a code back. Use any chat to swap them."));
    const p = this.panel();
    const host = this.btn("Host"); host.onclick = () => this.p2pHost();
    const join = this.btn("Join", false); join.onclick = () => this.p2pJoin();
    const back = this.btn("Back", false); back.onclick = () => this.showHome();
    p.append(host, join, back);
  }
  private async p2pHost() {
    this.set(this.title("Hosting 1v1…") + this.note("Generating your invite code…"));
    const link = new PeerLink(); this.link = link;
    const seed = (Math.random() * 1e9) | 0;
    const code = await link.host();
    link.onOpen = () => { link.send({ t: "p2pstart", seed }); this.finishP2P(link, true, seed); };
    this.set(this.title("Hosting 1v1") + this.note("1) Send this invite code to your opponent."));
    const p = this.panel();
    const out = this.area(code, true);
    const copy = this.btn("Copy invite code"); copy.onclick = () => { out.select(); navigator.clipboard?.writeText(code); copy.textContent = "Copied!"; };
    const ans = this.area("", false);
    const connect = this.btn("Connect"); const status = document.createElement("div");
    status.style.cssText = "color:#9fd08a;font-size:13px;margin-top:8px";
    connect.onclick = async () => { try { status.textContent = "Connecting…"; await link.accept(ans.value); } catch { status.textContent = "That reply code didn't work."; } };
    const cancel = this.btn("Cancel", false); cancel.onclick = () => this.cancel();
    p.append(out, copy, "2) Paste their reply code, then Connect.", ans, connect, cancel, status);
  }
  private async p2pJoin() {
    this.set(this.title("Join 1v1") + this.note("Paste the host's invite code, then send them your reply."));
    const p = this.panel();
    const inp = this.area("", false);
    const gen = this.btn("Generate reply code");
    const status = document.createElement("div"); status.style.cssText = "color:#9fd08a;font-size:13px;margin-top:8px";
    const cancel = this.btn("Cancel", false); cancel.onclick = () => this.cancel();
    gen.onclick = async () => {
      const link = new PeerLink(); this.link = link;
      link.onData = (m) => { const msg = m as { t?: string; seed?: number }; if (msg.t === "p2pstart" && typeof msg.seed === "number") this.finishP2P(link, false, msg.seed); };
      try { const reply = await link.join(inp.value); this.renderP2PReply(reply); }
      catch { status.textContent = "That invite code didn't work."; }
    };
    p.append(inp, gen, cancel, status);
  }
  private renderP2PReply(reply: string) {
    this.set(this.title("Join 1v1") + this.note("Send this reply code back to the host — the match starts automatically once they connect."));
    const p = this.panel();
    const out = this.area(reply, true);
    const copy = this.btn("Copy reply code"); copy.onclick = () => { out.select(); navigator.clipboard?.writeText(reply); copy.textContent = "Copied!"; };
    const cancel = this.btn("Cancel", false); cancel.onclick = () => this.cancel();
    p.append(out, copy, "Waiting for the host to connect…", cancel);
  }
  private finishP2P(link: PeerLink, isHost: boolean, seed: number) {
    this.finish({ transport: link, localTeam: (isHost ? Team.Player : Team.Enemy), teams: [Team.Player, Team.Enemy], alliances: [0, 1], seed, numTeams: 2 });
  }

  // --- exit ----------------------------------------------------------------
  private cancel() {
    try { this.link?.close(); } catch { /* */ }
    try { this.ws?.close(); } catch { /* */ }
    this.link = null; this.ws = null;
    this.close();
  }
  private finish(s: NetStart) {
    const cb = this.onStart;
    this.close();
    cb?.(s);
  }
}
