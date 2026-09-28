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
import { PRESETS } from "../maps/generator";
import { CustomMap, deserialiseMap, listCustomMaps, publishCustomMap, saveCustomMap, serialiseMap } from "../maps/custom";
import { FACTIONS, FACTION_IDS } from "../content/factions";
import { MAX_TEAMS, TEAM_COLORS, formatLabel } from "./teams";

export interface NetStart {
  transport: Transport;
  localTeam: Team;
  teams: Team[];
  alliances: number[];
  seed: number;
  numTeams: number;
  observer?: boolean; // joined to watch, not play
  /** Each realm's faction, in team order ("" = let the seed choose). */
  factions?: string[];
  /** The battlefield: a preset id, or a published map carried as its code. */
  map?: { id: string; name: string; code?: string };
}

interface LobbyPlayer { slot: number; name: string; side: number; team?: number; faction?: string; ready: boolean; observer?: boolean; }
interface LobbyMap { id: string; name: string; author?: string; minPlayers?: number; maxPlayers?: number }
/** A map in the server's community pool (its code is fetched on demand). */
interface PoolMap { id: string; name: string; author: string; desc: string; minPlayers: number; maxPlayers: number; cols: number; published: number }

/** Escape text for innerHTML: player and map names come from other people. */
const esc = (s: string) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!));

/** Forgiving normalization of the server address a player types. Accepts a bare
 *  IP/host, adds ws://, and supplies the default port — so "26.13.45.201",
 *  "26.13.45.201:8787" and "ws://26.13.45.201:8787" all work. */
export function normalizeWsUrl(raw: string): string {
  let s = (raw || "").trim();
  if (!s) return "";
  if (!/^wss?:\/\//i.test(s)) s = "ws://" + s;
  // Add the default port if none was given (after the host, before any path).
  s = s.replace(/^(wss?:\/\/[^/:]+)(\/|$)/i, `$1:8787$2`);
  return s;
}

const LS_URL = "bb_server_url";
const LS_NAME = "bb_player_name";

export class NetLobby {
  private el: HTMLDivElement | null = null;
  private link: PeerLink | null = null;
  private ws: WSLink | null = null;
  private slot = -1;
  private hostSlot = -1;
  private players: LobbyPlayer[] = [];
  private layout = 2;
  private map: LobbyMap = { id: "open_plains", name: "Open Plains" };
  private community: PoolMap[] = [];
  private picking = false;
  private faction = "";
  private statusLine = "";
  private onStart?: (s: NetStart) => void;

  open(onStart: (s: NetStart) => void) {
    this.onStart = onStart;
    if (this.el) return; // already open — never stack a second overlay
    this.build();
    this.showHome();
  }
  close() { this.el?.remove(); this.el = null; }

  // --- DOM helpers ---------------------------------------------------------
  private build() {
    const el = document.createElement("div");
    el.style.cssText = ["position:fixed", "inset:0", "display:flex", "align-items:center", "justify-content:center",
      "background:radial-gradient(ellipse at 50% 30%, rgba(40,30,16,0.86), rgba(6,4,2,0.94))", "z-index:50",
      "font-family:system-ui,sans-serif", "color:#e8ddc4"].join(";");
    const panel = document.createElement("div");
    panel.id = "mp-panel";
    panel.style.cssText = ["width:760px", "max-width:94vw", "max-height:90vh", "overflow:auto",
      "background:linear-gradient(180deg,#2c2316,#1c160d)", "border:1px solid #6a5428", "outline:1px solid rgba(201,162,74,0.35)",
      "outline-offset:-6px", "border-radius:12px", "padding:26px 28px", "box-shadow:0 18px 60px rgba(0,0,0,0.7), inset 0 1px 0 rgba(255,230,170,0.12)"].join(";");
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
      "border:1px solid " + (primary ? "#e0bc62" : "#5a4a2a"), "font-size:15px", "font-weight:600",
      "box-shadow:0 2px 6px rgba(0,0,0,0.4), inset 0 1px 0 rgba(255,240,200,0.18)",
      primary ? "background:linear-gradient(180deg,#e0bc62,#b08a36);color:#1e170c" : "background:linear-gradient(180deg,#3a3020,#2a2216);color:#e8ddc4"].join(";");
    return b;
  }
  /** A small chip button, for teams, factions and layouts. */
  private chip(label: string, on: boolean, color = "#c9a24a") {
    const b = this.btn(label, false);
    b.style.cssText = ["padding:5px 10px", "margin:3px 6px 3px 0", "border-radius:14px", "cursor:pointer", "font-size:12.5px", "font-weight:600",
      `border:1px solid ${on ? color : "#4a3c22"}`, on ? `background:${color};color:#15110a` : "background:#241d12;color:#d8cdb4"].join(";");
    return b;
  }
  private div(css: string, html = "") {
    const d = document.createElement("div");
    d.style.cssText = css;
    if (html) d.innerHTML = html;
    return d;
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
  private title(t: string) { return `<div style="font-family:Georgia,serif;font-size:26px;font-weight:700;color:#ffe9b0;margin-bottom:4px;text-shadow:0 2px 6px rgba(0,0,0,0.6)">${t}</div>`; }
  private heading(t: string) { return this.div("font-size:12px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#c9a24a;margin:14px 0 6px;border-bottom:1px solid #4a3c22;padding-bottom:4px", esc(t)); }
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
    const pass = this.field("", "Room password (optional)");
    const connect = this.btn("Connect");
    const back = this.btn("Back", false);
    const status = document.createElement("div");
    status.style.cssText = "color:#e0a05a;font-size:13px;margin-top:8px";
    back.onclick = () => this.showHome();
    const go = (observer: boolean) => {
      const wsUrl = normalizeWsUrl(url.value);
      if (!wsUrl) { status.textContent = "Enter the server address (e.g. ws://26.13.45.201:8787)."; return; }
      this.save(LS_URL, url.value); this.save(LS_NAME, name.value);
      status.textContent = `Connecting${observer ? " as observer" : ""} to ${wsUrl}…`;
      try {
        this.connectServer(wsUrl, name.value.trim() || (observer ? "Observer" : "Player"), room.value.trim() || "main", pass.value, observer, (msg) => (status.textContent = msg));
      } catch {
        status.textContent = "Couldn't open that address — check it and try again.";
      }
    };
    connect.onclick = () => go(false);
    const observe = this.btn("Observe", false);
    observe.onclick = () => go(true);
    p.append("Server address", url, "Display name", name, "Room", room, "Password (first to join sets it)", pass, connect, observe, back, status);
  }

  private observing = false;
  private room = "main";

  private connectServer(url: string, name: string, room: string, pass: string, observer: boolean, status: (m: string) => void) {
    const ws = new WSLink(url);
    this.ws = ws;
    this.observing = observer;
    this.room = room;
    ws.onOpen = () => ws.send({ t: "hello", name, room, pass, observer, faction: this.faction });
    ws.onClose = () => status("Disconnected. Check the address and that the server is running.");
    ws.onData = (raw) => {
      const m = raw as { t: string; slot?: number; host?: number; players?: LobbyPlayer[]; msg?: string; layout?: number;
        seed?: number; numTeams?: number; alliances?: number[]; slotTeams?: { slot: number; team: number }[];
        map?: LobbyMap & { code?: string }; maps?: PoolMap[]; factions?: string[]; id?: string; code?: string; name?: string; author?: string };
      if (m.t === "welcome") { this.slot = m.slot!; ws.send({ t: "maps" }); }
      else if (m.t === "lobby") {
        this.hostSlot = m.host!; this.players = m.players!;
        if (m.layout !== undefined) this.layout = m.layout;
        if (m.map) this.map = m.map;
        this.render();
      }
      else if (m.t === "maps") { this.community = m.maps ?? []; if (this.picking) this.render(); }
      else if (m.t === "published") { this.statusLine = `Published "${m.map?.name}" to this server.`; this.render(); }
      else if (m.t === "mapcode") { this.onMapCode(m.id!, m.code!); }
      else if (m.t === "error") { status(m.msg || "Server refused the connection."); this.statusLine = m.msg || ""; if (this.players.length) this.render(); }
      else if (m.t === "start") {
        const teams = Array.from({ length: m.numTeams! }, (_, i) => i as Team);
        const mine = m.slotTeams!.find((s) => s.slot === this.slot);
        if (!mine) {
          // Not assigned a team → we joined to observe.
          this.finish({ transport: ws, localTeam: Team.Player, teams, alliances: m.alliances!, seed: m.seed!, numTeams: m.numTeams!, observer: true, factions: m.factions, map: m.map });
        } else {
          this.finish({ transport: ws, localTeam: mine.team as Team, teams, alliances: m.alliances!, seed: m.seed!, numTeams: m.numTeams!, factions: m.factions, map: m.map });
        }
      }
    };
  }

  private render() { if (this.picking) this.renderMapPicker(); else this.renderRoom(); }

  private renderRoom() {
    const isHost = this.slot === this.hostSlot;
    const players = this.players.filter((p) => !p.observer);
    const obsCount = this.players.length - players.length;
    const teamOf = (p: LobbyPlayer) => p.team ?? p.side + 1;
    const format = players.length >= 2 ? formatLabel(players.map(teamOf)) : "waiting for players";
    const sides = new Set(players.map((p) => (teamOf(p) > 0 ? `t${teamOf(p)}` : `s${p.slot}`))).size;
    this.set(this.title(`Lobby — ${players.length} player${players.length === 1 ? "" : "s"}${obsCount ? ` · ${obsCount} 👁` : ""}`) +
      this.note(`Room <b>${esc(this.room)}</b> · <b style="color:#e2c889">${esc(format)}</b> · ${this.observing ? "You're observing — the match starts when the host begins." : isHost ? "You're the host ⭐ — set the teams and the battlefield, then start." : "Waiting for the host to start…"}`));
    const p = this.panel();
    const me = this.players.find((pl) => pl.slot === this.slot);

    // Battlefield.
    p.append(this.heading("Battlefield"));
    const mapRow = this.div("display:flex;align-items:center;justify-content:space-between;background:#15110a;border:1px solid #4a3c22;border-radius:8px;padding:10px 14px");
    mapRow.append(this.div("", `<div style="font-size:16px;font-weight:700;color:#ffe9b0">${esc(this.map.name)}</div>` +
      `<div style="font-size:12px;color:#a89f88">${this.map.author ? `by ${esc(this.map.author)}` : "built-in battlefield"}${this.map.maxPlayers ? ` · ${this.map.minPlayers}–${this.map.maxPlayers} players` : ""}</div>`));
    if (isHost) {
      const change = this.btn("Change…", false);
      change.onclick = () => { this.picking = true; this.ws!.send({ t: "maps" }); this.render(); };
      mapRow.append(change);
    }
    p.append(mapRow);

    // Teams: a card per team, in order, with its players.
    p.append(this.heading("Teams"));
    if (isHost) {
      const lay = this.div("margin-bottom:6px");
      lay.append("Layout: ");
      for (const [k, label] of [[0, "Free-for-all"], [2, "2 teams"], [3, "3 teams"], [4, "4 teams"]] as const) {
        const c = this.chip(label, this.layout === k);
        c.onclick = () => this.ws!.send({ t: "layout", teams: k });
        lay.append(c);
      }
      p.append(lay);
    }
    const used = [...new Set(players.map(teamOf))].filter((t) => t > 0).sort((a, b) => a - b);
    const shown = this.layout > 0 ? [...new Set([...Array.from({ length: this.layout }, (_, i) => i + 1), ...used])].sort((a, b) => a - b) : used;
    if (players.some((pl) => teamOf(pl) === 0) || this.layout === 0) shown.push(0);
    const grid = this.div("display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:10px");
    for (const t of shown) {
      const col = TEAM_COLORS[t] ?? TEAM_COLORS[0];
      const members = players.filter((pl) => teamOf(pl) === t);
      const card = this.div(`background:#1a140c;border:1px solid #4a3c22;border-top:3px solid ${col};border-radius:8px;padding:8px 10px`);
      card.append(this.div("display:flex;justify-content:space-between;align-items:center",
        `<b style="color:${col}">${t === 0 ? "On their own" : `Team ${t}`}</b><span style="font-size:11px;color:#8f8770">${members.length}</span>`));
      for (const pl of members) {
        const f = pl.faction ? FACTIONS[pl.faction as keyof typeof FACTIONS] : undefined;
        const row = this.div("display:flex;align-items:center;justify-content:space-between;padding:4px 0;border-bottom:1px solid #2e2618;font-size:13.5px",
          `<span>${pl.ready ? "✅" : "⬜"} ${esc(pl.name)}${pl.slot === this.slot ? " <i style='color:#a89f88'>(you)</i>" : ""}${pl.slot === this.hostSlot ? " ⭐" : ""}` +
          `<br><span style="font-size:11px;color:${f?.color ?? "#8f8770"}">${f ? esc(f.name) : "Random faction"}</span></span>`);
        // The host can move anyone: to the next team, round to "on their own".
        if (isHost) {
          const mv = this.chip("Move ▸", false);
          const maxT = Math.max(2, this.layout, ...used);
          mv.onclick = () => this.ws!.send({ t: "team", slot: pl.slot, team: t >= Math.min(MAX_TEAMS, maxT + (maxT < MAX_TEAMS ? 1 : 0)) ? 0 : t + 1 });
          row.append(mv);
        }
        card.append(row);
      }
      if (!this.observing && me && teamOf(me) !== t) {
        const join = this.chip(t === 0 ? "Go alone" : `Join team ${t}`, false, col);
        join.onclick = () => this.ws!.send({ t: "team", team: t });
        card.append(join);
      }
      grid.append(card);
    }
    if (!this.observing && me && shown.length < MAX_TEAMS + 1) {
      const next = Math.min(MAX_TEAMS, Math.max(0, ...shown) + 1);
      if (!shown.includes(next)) {
        const add = this.div("border:1px dashed #4a3c22;border-radius:8px;padding:8px 10px;display:flex;align-items:center;justify-content:center");
        const nb = this.chip(`＋ New team ${next}`, false);
        nb.onclick = () => this.ws!.send({ t: "team", team: next });
        add.append(nb);
        grid.append(add);
      }
    }
    p.append(grid);

    // Your realm.
    if (!this.observing) {
      p.append(this.heading("Your faction"));
      const fr = this.div("");
      const mine = me?.faction ?? this.faction;
      const rnd = this.chip("Random", !mine);
      rnd.onclick = () => { this.faction = ""; this.ws!.send({ t: "faction", faction: "" }); };
      fr.append(rnd);
      for (const id of FACTION_IDS) {
        const c = this.chip(FACTIONS[id].name.replace(/^The /, ""), mine === id, FACTIONS[id].color);
        c.onclick = () => { this.faction = id; this.ws!.send({ t: "faction", faction: id }); };
        fr.append(c);
      }
      p.append(fr);
    }

    // Actions.
    const bar = this.div("margin-top:16px;padding-top:10px;border-top:1px solid #4a3c22");
    if (!this.observing) {
      const ready = this.btn(me?.ready ? "Unready" : "Ready", !me?.ready);
      ready.onclick = () => this.ws!.send({ t: "ready", ready: !me?.ready });
      bar.append(ready);
    }
    if (isHost) {
      const start = this.btn("⚔ Start Match");
      start.disabled = players.length < 2 || sides < 2;
      if (start.disabled) start.style.opacity = "0.5";
      start.onclick = () => this.ws!.send({ t: "start" });
      bar.append(start);
    }
    const leave = this.btn("Leave", false);
    leave.onclick = () => this.cancel();
    bar.append(leave);
    if (isHost && players.length >= 2 && sides < 2) bar.append(this.div("color:#e0786a;font-size:13px;margin-top:6px", "Everyone is on one team — split into at least two sides."));
    if (this.statusLine) bar.append(this.div("color:#9fd08a;font-size:13px;margin-top:6px", esc(this.statusLine)));
    p.append(bar);
  }

  /**
   * The host's battlefield picker: built-in battlefields, the maps you have
   * published, and the community pool on this server. Your published maps can
   * be published to the server from here, and a community map can be kept.
   */
  private renderMapPicker() {
    const n = this.players.filter((pl) => !pl.observer).length;
    this.set(this.title("Choose the battlefield") + this.note(`${n} player${n === 1 ? "" : "s"} in the room. Maps that can't seat everyone are marked.`));
    const p = this.panel();
    const fits = (min?: number, max?: number) => (min === undefined || n >= min) && (max === undefined || n <= max);
    const row = (name: string, sub: string, ok: boolean, actions: HTMLButtonElement[]) => {
      const r = this.div("display:flex;align-items:center;justify-content:space-between;padding:6px 10px;margin:4px 0;background:#15110a;border:1px solid #3a2f1c;border-radius:6px" + (ok ? "" : ";opacity:0.55"),
        `<span><b style="color:#f2e8d0">${esc(name)}</b><br><span style="font-size:11.5px;color:#a89f88">${sub}</span></span>`);
      const box = this.div("white-space:nowrap");
      box.append(...actions);
      r.append(box);
      p.append(r);
    };
    const use = (m: LobbyMap & { code?: string }) => {
      const b = this.chip(this.map.id === m.id ? "✓ Chosen" : "Use", this.map.id === m.id);
      b.onclick = () => { this.ws!.send({ t: "map", map: m }); this.picking = false; this.render(); };
      return b;
    };

    p.append(this.heading("Built-in battlefields"));
    row("Random", "A different built-in battlefield every match, from the seed.", true, [use({ id: "random", name: "Random battlefield" })]);
    for (const pr of PRESETS) row(pr.name, esc(pr.desc), true, [use({ id: pr.id, name: pr.name })]);

    const mine = listCustomMaps().filter((m) => m.published);
    p.append(this.heading(`Your published maps (${mine.length})`));
    if (!mine.length) p.append(this.div("font-size:12.5px;color:#8f8770", "Publish a map from the Map Editor and it appears here."));
    for (const m of mine) {
      const code = serialiseMap(m);
      const onServer = this.community.some((c) => c.name === m.name && c.author === (m.author || this.ls(LS_NAME, "Player")));
      const pub = this.chip(onServer ? "On server" : "Publish to server", false);
      pub.disabled = onServer;
      pub.onclick = () => this.ws!.send({ t: "publish", map: this.publishable(m, code) });
      row(m.name, `${m.cols}×${m.rows} · ${m.minPlayers}–${m.maxPlayers} players${m.author ? ` · by ${esc(m.author)}` : ""}`, fits(m.minPlayers, m.maxPlayers),
        [use({ id: m.id, name: m.name, author: m.author, minPlayers: m.minPlayers, maxPlayers: m.maxPlayers, code }), pub]);
    }

    p.append(this.heading(`Community maps on this server (${this.community.length})`));
    if (!this.community.length) p.append(this.div("font-size:12.5px;color:#8f8770", "Nobody has published a map here yet — be the first."));
    for (const c of this.community) {
      const pick = this.chip(this.map.id === c.id ? "✓ Chosen" : "Use", this.map.id === c.id);
      pick.onclick = () => { this.pendingUse = c.id; this.ws!.send({ t: "getmap", id: c.id }); };
      const keep = this.chip("Keep a copy", false);
      keep.onclick = () => { this.pendingKeep = c.id; this.ws!.send({ t: "getmap", id: c.id }); };
      row(c.name, `by ${esc(c.author)} · ${c.cols}×${c.cols} · ${c.minPlayers}–${c.maxPlayers} players${c.desc ? ` · ${esc(c.desc)}` : ""}`, fits(c.minPlayers, c.maxPlayers), [pick, keep]);
    }

    const back = this.btn("Back to the lobby", false);
    back.onclick = () => { this.picking = false; this.render(); };
    p.append(this.div("margin-top:12px"), back);
    if (this.statusLine) p.append(this.div("color:#9fd08a;font-size:13px;margin-top:6px", esc(this.statusLine)));
  }

  private pendingUse = "";
  private pendingKeep = "";

  /** A community map's code arrived: use it for the match, or keep a copy. */
  private onMapCode(id: string, code: string) {
    const m = deserialiseMap(code);
    const meta = this.community.find((c) => c.id === id);
    if (!m) { this.statusLine = "That map didn't load."; this.render(); return; }
    if (this.pendingUse === id) {
      this.ws?.send({ t: "map", map: { id, name: m.name, author: m.author ?? meta?.author ?? "", minPlayers: m.minPlayers, maxPlayers: m.maxPlayers, code } });
      this.picking = false;
    }
    if (this.pendingKeep === id) {
      // A kept community map goes straight into your own pool: it was already
      // published by its author.
      if (!m.author && meta?.author) m.author = meta.author;
      publishCustomMap(m);
      if (!m.published) saveCustomMap(m);
      this.statusLine = `Saved "${m.name}" to your maps.`;
    }
    this.pendingUse = this.pendingKeep = "";
    this.render();
  }

  private publishable(m: CustomMap, code: string) {
    return { code, name: m.name, author: m.author || this.ls(LS_NAME, "Player"), desc: m.desc, minPlayers: m.minPlayers, maxPlayers: m.maxPlayers, cols: m.cols };
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
