// Caster mode: everything someone commentating or streaming a match needs.
//
// Modelled on the observer tools of the big competitive RTS games:
//   • a player bar across the top — each realm's colour, name, faction, age,
//     population, resources and army value — with tug-of-war bars under it
//     comparing the sides' armies, economies and kills;
//   • a bottom panel with five views: overview, army composition, economy,
//     production (what every building is making, with progress) and tech
//     (ages, Oaths, researched upgrades);
//   • vision switching: the whole map, or exactly what one player can see;
//   • an auto-director camera that finds the biggest fight and cuts to it,
//     tours the bases when it's quiet, and backs off the moment the caster
//     takes the camera;
//   • an event feed (ages, Oaths, battles, fallen Town Centres and castles,
//     eliminations, first blood) — click a line to go there — and fight
//     markers on the minimap;
//   • live graphs of army value and score per player;
//   • a clean feed for streaming: just the game and a slim bar;
//   • for replays, a timeline to scrub and speeds up to 16×; online, a
//     broadcast delay so a caster's stream can't be used to cheat.
//
// Drawing is immediate-mode like the rest of the UI. The App owns the world,
// camera and replay; it hands them in and reads back what the caster asked for.

import { ui } from "./ui";
import { PAL, withAlpha } from "../render/palette";
import { World, WorldEvent } from "../sim/world";
import { Entity, Kind, OrderKind, Team } from "../sim/types";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { AGES, UPGRADES, ageShort } from "../content/tech";
import { OATHS } from "../content/oaths";
import { factionOf } from "../content/factions";
import { SIM_HZ } from "../content/balance";
import type { TeamMetrics } from "../sim/metrics";
import { MINIMAP_SIZE } from "./hud";

export type CasterPanel = "overview" | "army" | "economy" | "production" | "tech";
export const CASTER_PANELS: [CasterPanel, string][] = [
  ["overview", "Overview"], ["army", "Army"], ["economy", "Economy"], ["production", "Production"], ["tech", "Tech & Oaths"],
];

/** What the caster asked the App to do this frame. */
export interface CasterRequest {
  seekTo?: number; // replay: go to this tick
  speed?: number;
  togglePause?: boolean;
  focus?: { x: number; y: number };
  exit?: boolean;
}

export interface CasterContext {
  world: World;
  names: string[];
  /** Per-team metric samples over time (for graphs). */
  history: { t: number; m: TeamMetrics[] }[];
  speed: number;
  paused: boolean;
  /** "live", "replay" or "delay" (online, behind the match). */
  source: "live" | "replay" | "delay";
  delaySec: number;
  replay?: { tick: number; endTick: number };
  /** Not online: the caster controls pause and speed. */
  offline?: boolean;
}

interface FeedLine { at: number; time: number; text: string; color: string; x: number; y: number; weight: number }
interface Heat { x: number; y: number; w: number; t: number; team: Team }

const teamCol = (t: number) => PAL.teams[t % PAL.teams.length].main;
const teamLight = (t: number) => PAL.teams[t % PAL.teams.length].light;
const clock = (sec: number) => `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, "0")}`;
const num = (n: number) => (n >= 10000 ? `${(n / 1000).toFixed(1)}k` : Math.round(n).toLocaleString("en-GB"));
const MAJOR_BUILDINGS = new Set(["town_center", "castle", "wonder", "siege_workshop"]);

/** Per-realm numbers for one frame. */
export interface RealmSnapshot {
  team: number;
  army: number;
  armyValue: number;
  villagers: number;
  idleVillagers: number;
  buildings: number;
  units: Map<string, number>;
  production: { item: string; label: string; progress: number; building: string }[];
  defeated: boolean;
}

/** Walk the world once and total up every realm. */
export function snapshotRealms(world: World): RealmSnapshot[] {
  const out: RealmSnapshot[] = Array.from({ length: world.numTeams }, (_, t) => ({
    team: t, army: 0, armyValue: 0, villagers: 0, idleVillagers: 0, buildings: 0, units: new Map(), production: [],
    defeated: world.player(t as Team).defeated,
  }));
  for (const e of world.entities) {
    if (!e.alive || e.team < 0 || e.team >= world.numTeams) continue;
    const r = out[e.team];
    if (e.kind === Kind.Building) {
      r.buildings++;
      if (e.productionQueue.length) {
        const item = e.productionQueue[0];
        const [k, id] = item.split(":");
        let total = 1, label = id;
        if (k === "u") { total = UNITS[id]?.buildTime ?? 1; label = UNITS[id]?.name ?? id; }
        else if (k === "t") { total = UPGRADES[id]?.time ?? 1; label = UPGRADES[id]?.name ?? id; }
        else if (k === "a") { const next = AGES[world.player(e.team).age + 1]; total = next?.advanceTime ?? 1; label = next ? `→ ${next.short}` : "Age"; }
        r.production.push({ item, label, progress: Math.max(0, Math.min(1, 1 - e.productionTime / Math.max(0.1, total))), building: BUILDINGS[e.type]?.name ?? e.type });
      }
    } else if (e.kind === Kind.Unit) {
      if (e.type === "villager") {
        r.villagers++;
        if (e.order.kind === OrderKind.Idle) r.idleVillagers++;
      } else if (UNITS[e.type]) {
        r.army++;
        const c = UNITS[e.type].cost;
        r.armyValue += (c.food ?? 0) + (c.wood ?? 0) + (c.gold ?? 0);
      }
      r.units.set(e.type, (r.units.get(e.type) ?? 0) + 1);
    }
  }
  // Ages and research first, then units, longest-running first.
  for (const r of out) r.production.sort((a, b) => (a.item[0] === "u" ? 1 : 0) - (b.item[0] === "u" ? 1 : 0) || b.progress - a.progress);
  return out;
}

/**
 * The sides for tug-of-war bars: realms grouped by alliance, in order of first
 * appearance. A free-for-all is one side per realm.
 */
export function sidesOf(world: World): number[][] {
  const bySide = new Map<number, number[]>();
  for (let t = 0; t < world.numTeams; t++) {
    if (t === world.hordeTeam) continue;
    const a = world.alliances[t] ?? t;
    const s = bySide.get(a) ?? [];
    s.push(t);
    bySide.set(a, s);
  }
  return [...bySide.values()];
}

export class Caster {
  panel: CasterPanel = "overview";
  /** -1 = see everything; otherwise the team whose vision is shown. */
  vision = -1;
  auto = true;
  clean = false;
  graphs = false;
  help = false;
  /** Broadcast delay for online casting, in seconds. */
  delaySec = 0;
  feed: FeedLine[] = [];
  private heat: Heat[] = [];
  private announced: { x: number; y: number; t: number }[] = [];
  private defeated = new Set<number>();
  private firstBlood = false;
  private lastCut = -99;
  private manualUntil = -1;
  private lastSet: { x: number; y: number } | null = null;
  private tourIndex = 0;
  private target: { x: number; y: number; reason: string } | null = null;
  private realms: RealmSnapshot[] = [];
  private realmsAt = -1;

  reset() {
    this.feed = []; this.heat = []; this.announced = []; this.defeated.clear(); this.firstBlood = false;
    this.lastCut = -99; this.manualUntil = -1; this.lastSet = null; this.tourIndex = 0; this.target = null; this.realmsAt = -1;
    this.vision = -1;
  }

  // ------------------------------------------------------------- events --
  /** Read the sim's events: they feed the director's heat map and the feed. */
  onEvents(world: World, events: WorldEvent[], names: string[]) {
    const now = world.time;
    const who = (t: number) => names[t] || PAL.teams[t % PAL.teams.length].name;
    for (const ev of events) {
      if (ev.kind === "death") {
        const def = UNITS[ev.data ?? ""];
        this.heat.push({ x: ev.x, y: ev.y, w: def && ev.data !== "villager" ? 1 : 0.7, t: now, team: ev.team });
        if (!this.firstBlood && ev.data && ev.data !== "sheep" && ev.team >= 0 && ev.team < world.numTeams && ev.team !== world.hordeTeam) {
          this.firstBlood = true;
          this.push(world, `First blood — ${who(ev.team)} loses a ${UNITS[ev.data]?.name ?? ev.data}`, teamLight(ev.team), ev.x, ev.y, 1);
        }
      } else if (ev.kind === "collapse") {
        this.heat.push({ x: ev.x, y: ev.y, w: 3, t: now, team: ev.team });
        if (MAJOR_BUILDINGS.has(ev.data ?? "") && ev.team < world.numTeams) {
          this.push(world, `${who(ev.team)}'s ${BUILDINGS[ev.data!]?.name ?? ev.data} has fallen`, teamLight(ev.team), ev.x, ev.y, 3);
        }
      } else if (ev.kind === "underattack") {
        this.heat.push({ x: ev.x, y: ev.y, w: 0.25, t: now, team: ev.team });
      } else if (ev.kind === "age" && ev.team < world.numTeams) {
        const age = AGES[Number(ev.data)];
        this.push(world, `${who(ev.team)} reaches the ${age?.name ?? "next age"}`, teamLight(ev.team), ev.x, ev.y, 2);
      } else if (ev.kind === "oath" && ev.team < world.numTeams) {
        this.push(world, `${who(ev.team)} swears the ${OATHS[ev.data ?? ""]?.name ?? "an Oath"}`, teamLight(ev.team), ev.x, ev.y, 2);
      } else if (ev.kind === "charge") {
        this.heat.push({ x: ev.x, y: ev.y, w: 0.6, t: now, team: ev.team });
      }
    }
    for (let t = 0; t < world.numTeams; t++) {
      if (t === world.hordeTeam || this.defeated.has(t) || !world.player(t as Team).defeated) continue;
      this.defeated.add(t);
      const st = world.map.starts[t];
      this.push(world, `${who(t)} has been eliminated`, teamLight(t), st?.x ?? world.worldW / 2, st?.y ?? world.worldH / 2, 4);
    }
    if (world.winner !== null && world.winner !== Team.Neutral && !this.feed.some((f) => f.text.includes("wins the match"))) {
      const win = sidesOf(world).find((s) => s.includes(world.winner!)) ?? [world.winner];
      this.push(world, `${win.map(who).join(" & ")} wins the match!`, "#ffe9b0", world.worldW / 2, world.worldH / 2, 5);
    }
  }

  private push(world: World, text: string, color: string, x: number, y: number, weight: number) {
    this.feed.push({ at: performance.now(), time: world.time, text, color, x, y, weight });
    if (this.feed.length > 60) this.feed.shift();
  }

  // ------------------------------------------------------------ director --
  /** The hottest spot on the map right now, if any fight is on. */
  hotspot(world: World): { x: number; y: number; score: number; teams: Map<number, number> } | null {
    const now = world.time;
    this.heat = this.heat.filter((h) => now - h.t < 14);
    if (!this.heat.length) return null;
    const CELL = 360;
    const cells = new Map<string, { x: number; y: number; w: number; teams: Map<number, number> }>();
    for (const h of this.heat) {
      const decay = Math.pow(0.5, (now - h.t) / 5);
      const k = `${Math.floor(h.x / CELL)},${Math.floor(h.y / CELL)}`;
      const c = cells.get(k) ?? { x: 0, y: 0, w: 0, teams: new Map() };
      c.x += h.x * h.w * decay; c.y += h.y * h.w * decay; c.w += h.w * decay;
      c.teams.set(h.team, (c.teams.get(h.team) ?? 0) + 1);
      cells.set(k, c);
    }
    let best: { x: number; y: number; score: number; teams: Map<number, number> } | null = null;
    for (const c of cells.values()) {
      if (!best || c.w > best.score) best = { x: c.x / c.w, y: c.y / c.w, score: c.w, teams: c.teams };
    }
    return best;
  }

  /** Tell the director the caster moved the camera: it waits before taking over again. */
  manual(world: World, seconds = 10) { this.manualUntil = world.time + seconds; }

  /**
   * Move the camera if the director is on. Returns true while it is steering.
   * Big fights are announced in the feed once per place.
   */
  direct(world: World, camera: { x: number; y: number; zoom: number }, dt: number): boolean {
    const now = world.time;
    // Spot the caster taking the camera (keys, edge-pan, drag, minimap).
    if (this.lastSet && Math.hypot(camera.x - this.lastSet.x, camera.y - this.lastSet.y) > 3) this.manual(world);
    const hot = this.hotspot(world);
    if (hot && hot.score >= 6 && !this.announced.some((a) => now - a.t < 25 && Math.hypot(a.x - hot.x, a.y - hot.y) < 700)) {
      this.announced.push({ x: hot.x, y: hot.y, t: now });
      const sides = [...hot.teams.entries()].filter(([t]) => t >= 0 && t < world.numTeams).sort((a, b) => b[1] - a[1]).slice(0, 2);
      const label = sides.length === 2 ? `Battle — ${PAL.teams[sides[0][0] % PAL.teams.length].name} vs ${PAL.teams[sides[1][0] % PAL.teams.length].name}` : "A fight breaks out";
      this.push(world, label, "#f0c070", hot.x, hot.y, 2);
    }
    if (!this.auto || now < this.manualUntil) { this.lastSet = null; return false; }
    // Choose where to look: a real fight beats everything; otherwise tour the bases.
    const far = (x: number, y: number) => Math.hypot(camera.x - x, camera.y - y) > 420;
    if (hot && hot.score >= 2.5) {
      if (!this.target || this.target.reason !== "fight" || (now - this.lastCut > 5 && far(hot.x, hot.y)) || !far(this.target.x, this.target.y)) {
        if (!this.target || this.target.reason !== "fight" || now - this.lastCut > 5) {
          this.target = { x: hot.x, y: hot.y, reason: "fight" };
          this.lastCut = now;
        } else {
          // Stay on the fight but follow it as it moves.
          this.target.x += (hot.x - this.target.x) * Math.min(1, dt * 0.8);
          this.target.y += (hot.y - this.target.y) * Math.min(1, dt * 0.8);
        }
      }
    } else if (!this.target || now - this.lastCut > 12) {
      const alive = [...Array(world.numTeams).keys()].filter((t) => t !== world.hordeTeam && !world.player(t as Team).defeated);
      if (alive.length) {
        const t = alive[this.tourIndex++ % alive.length];
        const tc = world.entities.find((e) => e.alive && e.team === t && e.type === "town_center");
        const st = tc ?? world.map.starts[t];
        if (st) { this.target = { x: st.x, y: st.y, reason: "tour" }; this.lastCut = now; }
      }
    }
    if (!this.target) return false;
    const k = Math.min(1, dt * (this.target.reason === "fight" ? 2.6 : 1.4));
    camera.x += (this.target.x - camera.x) * k;
    camera.y += (this.target.y - camera.y) * k;
    this.lastSet = { x: camera.x, y: camera.y };
    return true;
  }

  // ---------------------------------------------------------------- keys --
  /** Caster hotkeys. Returns true if the key was the caster's. */
  key(key: string, world: World): boolean {
    const k = key.length === 1 ? key.toLowerCase() : key;
    if (/^[1-8]$/.test(k)) { const t = Number(k) - 1; if (t < world.numTeams && t !== world.hordeTeam) this.vision = this.vision === t ? -1 : t; return true; }
    if (k === "0" || k === "v") { this.vision = -1; return true; }
    if (k === "a") { this.auto = !this.auto; if (this.auto) this.manualUntil = -1; return true; }
    if (k === "h") { this.clean = !this.clean; return true; }
    if (k === "g") { this.graphs = !this.graphs; return true; }
    if (k === "?" || k === "/" || k === "F1") { this.help = !this.help; return true; }
    if (k === "Tab") { const i = CASTER_PANELS.findIndex(([p]) => p === this.panel); this.panel = CASTER_PANELS[(i + 1) % CASTER_PANELS.length][0]; return true; }
    const map: Record<string, CasterPanel> = { q: "overview", w: "army", e: "economy", r: "production", t: "tech" };
    if (map[k]) { this.panel = map[k]; return true; }
    return false;
  }

  // ---------------------------------------------------------------- draw --
  draw(W: number, H: number, c: CasterContext): CasterRequest {
    const req: CasterRequest = {};
    const world = c.world;
    // A snapshot a few times a second is plenty for a scoreboard.
    if (this.realmsAt < 0 || Math.abs(world.time - this.realmsAt) > 0.25) { this.realms = snapshotRealms(world); this.realmsAt = world.time; }
    const realms = this.realms;
    const teams = realms.map((r) => r.team).filter((t) => t !== world.hordeTeam);
    const name = (t: number) => c.names[t] || PAL.teams[t % PAL.teams.length].name;

    const barH = this.clean ? 40 : 66;
    this.topBar(W, barH, c, teams, name, req);
    if (!this.clean) this.tugOfWar(W, barH, c, realms);
    this.feedDraw(W, H, c, req);
    if (this.clean) {
      ui.text("H — show caster view", W - 12, H - 10, { align: "right", size: 11, color: withAlpha("#ffffff", 0.35) });
      return req;
    }
    this.minimapFights(H, world);
    const panelTop = H - 176;
    if (c.replay) this.timeline(W, panelTop - 40, c, req);
    this.bottomPanel(W, H, panelTop, c, realms, teams, name);
    this.statusChips(W, barH + 44, c, req);
    if (this.graphs) this.graphPanel(W, barH + 76, c, teams, name);
    if (this.help) this.helpPanel(W, H);
    return req;
  }

  private topBar(W: number, h: number, c: CasterContext, teams: number[], name: (t: number) => string, req: CasterRequest) {
    const ctx = ui.ctx;
    const world = c.world;
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, "rgba(12,9,5,0.94)");
    g.addColorStop(1, "rgba(22,16,9,0.9)");
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, h);
    ctx.fillStyle = withAlpha(PAL.uiAccent, 0.45);
    ctx.fillRect(0, h - 1, W, 1);
    // Centre block: clock and source.
    const cw = 150;
    const cx = W / 2 - cw / 2;
    ui.text(clock(world.time), W / 2, this.clean ? 27 : 30, { align: "center", size: this.clean ? 20 : 24, bold: true, color: "#fff0cc", font: "Georgia, serif" });
    if (!this.clean) {
      const src = c.source === "replay" ? "REPLAY" : c.source === "delay" ? `LIVE · ${Math.round(c.delaySec)}s DELAY` : "LIVE";
      const col = c.source === "replay" ? "#7fb0e8" : "#e0786a";
      ui.text(`${c.paused ? "❚❚ " : ""}${src}${c.speed !== 1 && c.source !== "delay" ? ` · ${c.speed}×` : ""}`, W / 2, 52, { align: "center", size: 11, bold: true, color: col });
    }
    // Realm cards either side of the clock: first half left, second half right.
    const half = Math.ceil(teams.length / 2);
    const left = teams.slice(0, half), right = teams.slice(half);
    const drawSide = (list: number[], x0: number, x1: number) => {
      if (!list.length) return;
      const w = (x1 - x0) / list.length;
      list.forEach((t, i) => this.realmCard(x0 + i * w, 0, w, h, t, name(t), c, req));
    };
    drawSide(left, 0, cx);
    drawSide(right, cx + cw, W);
  }

  private realmCard(x: number, y: number, w: number, h: number, t: number, nm: string, c: CasterContext, req: CasterRequest) {
    const ctx = ui.ctx;
    const world = c.world;
    const p = world.player(t as Team);
    const r = this.realms[t];
    const f = factionOf(p.faction);
    const out = p.defeated;
    const hov = ui.hit(x, y, w, h);
    ctx.fillStyle = hov ? "rgba(255,255,255,0.05)" : "transparent";
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = teamCol(t);
    ctx.fillRect(x + 2, y + 6, 4, h - 12);
    if (this.vision === t) {
      ctx.strokeStyle = teamLight(t); ctx.lineWidth = 2;
      ctx.strokeRect(x + 1, y + 1, w - 2, h - 2);
    }
    const tx = x + 12;
    const nameSize = w < 170 ? 12 : 14;
    ui.text(nm.length > 18 ? nm.slice(0, 17) + "…" : nm, tx, y + 18, { size: nameSize, bold: true, color: out ? "#6f6a5c" : teamLight(t) });
    if (this.clean) {
      ui.text(`${ageShort(p.age)} · ${r?.army ?? 0} army`, x + w - 8, y + 18, { align: "right", size: 11, color: "#c9bea3" });
      ui.text(f.name.replace(/^The /, ""), tx, y + 33, { size: 10.5, color: f.color });
    } else {
      ui.text(`${f.name.replace(/^The /, "")} · ${ageShort(p.age)}${p.oaths?.length ? ` · ${p.oaths.map((o) => OATHS[o]?.short ?? o).join("/")}` : ""}`, tx, y + 33, { size: 10.5, color: f.color });
      if (out) ui.text("ELIMINATED", tx, y + 53, { size: 11, bold: true, color: "#e0786a" });
      else {
        const res = p.resources;
        const line = w >= 250
          ? `🍖${num(res.food)}  🪵${num(res.wood)}  🪙${num(res.gold)}   👥${p.popUsed}/${p.popCap}`
          : `👥${p.popUsed}/${p.popCap} · ⚔${r?.army ?? 0}`;
        ui.text(line, tx, y + 53, { size: 11, color: "#e2d6ba" });
      }
      if (w >= 200 && !out) ui.text(`⚔ ${num(r?.armyValue ?? 0)}`, x + w - 8, y + 18, { align: "right", size: 11.5, bold: true, color: "#f0c070" });
    }
    if (hov) {
      ui.pointerConsumed = true;
      ui.tooltip([nm, `${f.name} · ${AGES[p.age]?.name ?? ""}`, "Click: look at this realm · 1–8: its vision"]);
      if (ui.clicked) {
        const tc = world.entities.find((e: Entity) => e.alive && e.team === t && e.type === "town_center");
        const st = tc ?? world.map.starts[t];
        if (st) req.focus = { x: st.x, y: st.y };
      }
    }
  }

  private tugOfWar(W: number, top: number, c: CasterContext, realms: RealmSnapshot[]) {
    const world = c.world;
    const sides = sidesOf(world);
    if (sides.length < 2) return;
    const metrics: [string, (t: number) => number][] = [
      ["ARMY", (t) => realms[t]?.armyValue ?? 0],
      ["ECONOMY", (t) => world.player(t as Team).stats.gathered],
      ["KILLS", (t) => world.player(t as Team).stats.unitsKilled],
    ];
    const bw = Math.min(260, (W - 80) / 3), gap = 14;
    const x0 = W / 2 - (bw * 3 + gap * 2) / 2;
    const ctx = ui.ctx;
    ctx.fillStyle = "rgba(10,7,4,0.75)";
    ctx.beginPath(); ctx.roundRect(x0 - 10, top + 4, bw * 3 + gap * 2 + 20, 34, 6); ctx.fill();
    metrics.forEach(([label, fn], i) => {
      const bx = x0 + i * (bw + gap);
      const vals = sides.map((s) => s.reduce((a, t) => a + fn(t), 0));
      const total = vals.reduce((a, b) => a + b, 0);
      ui.text(label, bx + bw / 2, top + 17, { align: "center", size: 9.5, bold: true, color: "#a89f88" });
      let x = bx;
      sides.forEach((s, k) => {
        const w = total > 0 ? (vals[k] / total) * bw : bw / sides.length;
        ctx.fillStyle = teamCol(s[0]);
        ctx.fillRect(x, top + 22, Math.max(0, w - 1), 9);
        x += w;
      });
      ui.text(num(vals[0]), bx, top + 17, { size: 10, bold: true, color: teamLight(sides[0][0]) });
      ui.text(num(vals[vals.length - 1]), bx + bw, top + 17, { align: "right", size: 10, bold: true, color: teamLight(sides[sides.length - 1][0]) });
    });
  }

  private statusChips(W: number, y: number, c: CasterContext, req: CasterRequest) {
    const chips: [string, boolean, () => void, string][] = [
      [this.auto ? "🎥 Auto camera: ON" : "🎥 Auto camera: off", this.auto, () => { this.auto = !this.auto; }, "A — the director follows the biggest fight, and tours the bases when it's quiet. Move the camera yourself and it waits."],
      [this.vision < 0 ? "👁 Vision: everything" : `👁 Vision: ${c.names[this.vision] || PAL.teams[this.vision].name}`, this.vision >= 0, () => { this.vision = -1; }, "1–8 — see exactly what one player sees (fog of war included). 0 or V — everything."],
      ["📈 Graphs", this.graphs, () => { this.graphs = !this.graphs; }, "G — army value and score over time."],
      ["▭ Clean feed", false, () => { this.clean = true; }, "H — hide the caster panels for streaming."],
      ["? Keys", this.help, () => { this.help = !this.help; }, "All caster hotkeys."],
    ];
    let x = 12;
    for (const [label, on, fn, tip] of chips) {
      const w = Math.max(80, label.length * 6.6 + 18);
      if (ui.button(label, x, y, w, 24, { size: 11, accent: on, tooltip: [label.replace(/^[^ ]+ /, ""), tip] })) fn();
      x += w + 6;
    }
    // Speed for a live AI game (a replay has its own on the timeline).
    if (c.offline && !c.replay) {
      x += 8;
      if (ui.button(c.paused ? "▶" : "❚❚", x, y, 28, 24, { size: 11, accent: c.paused, tooltip: ["Pause", "Space"] })) req.togglePause = true;
      x += 32;
      for (const s of [0.5, 1, 2, 4, 8, 16]) {
        if (ui.button(`${s}×`, x, y, 36, 24, { size: 10.5, accent: c.speed === s && !c.paused, tooltip: ["Speed", "+ / −"] })) req.speed = s;
        x += 40;
      }
    }
    if (ui.button("Leave", x + 8, y, 60, 24, { size: 11, danger: true, tooltip: ["Stop watching", "Esc. The match is kept as a replay."] })) req.exit = true;
  }

  private feedDraw(W: number, H: number, c: CasterContext, req: CasterRequest) {
    const now = performance.now();
    const shown = this.feed.filter((f) => now - f.at < 14000).slice(-7);
    const x = W - 12, w = 340;
    let y = this.clean ? 56 : 120;
    for (const f of shown) {
      const age = (now - f.at) / 14000;
      const a = age > 0.8 ? (1 - age) / 0.2 : 1;
      const ctx = ui.ctx;
      ctx.fillStyle = `rgba(10,7,4,${0.72 * a})`;
      ctx.beginPath(); ctx.roundRect(x - w, y - 15, w, 22, 5); ctx.fill();
      ctx.fillStyle = withAlpha(f.color, a);
      ctx.fillRect(x - w, y - 15, 3, 22);
      ui.text(clock(f.time), x - w + 10, y, { size: 10.5, color: withAlpha("#a89f88", a) });
      ui.text(f.text.length > 44 ? f.text.slice(0, 43) + "…" : f.text, x - w + 50, y, { size: 12, bold: f.weight >= 3, color: withAlpha(f.color, a) });
      if (ui.hit(x - w, y - 15, w, 22)) {
        ui.pointerConsumed = true;
        if (ui.clicked) req.focus = { x: f.x, y: f.y };
      }
      y += 26;
    }
    void H; void c;
  }

  private minimapFights(H: number, world: World) {
    const hot = this.heat;
    if (!hot.length) return;
    const ctx = ui.ctx;
    const mmX = 10, mmY = H - MINIMAP_SIZE - 10;
    const sx = MINIMAP_SIZE / world.worldW, sy = MINIMAP_SIZE / world.worldH;
    const pulse = 0.5 + 0.5 * Math.sin(performance.now() / 180);
    const seen: { x: number; y: number }[] = [];
    for (const h of hot) {
      if (world.time - h.t > 8 || h.w < 0.5) continue;
      if (seen.some((s) => Math.hypot(s.x - h.x, s.y - h.y) < 300)) continue;
      seen.push({ x: h.x, y: h.y });
      ctx.strokeStyle = `rgba(255,120,80,${0.5 + 0.4 * pulse})`;
      ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.arc(mmX + h.x * sx, mmY + h.y * sy, 4 + pulse * 3, 0, Math.PI * 2); ctx.stroke();
    }
  }

  private timeline(W: number, y: number, c: CasterContext, req: CasterRequest) {
    const rp = c.replay!;
    const x0 = MINIMAP_SIZE + 30, x1 = W - 20, w = x1 - x0;
    ui.panel(x0 - 6, y - 4, w + 12, 34);
    if (ui.button(c.paused ? "▶" : "❚❚", x0, y, 30, 26, { size: 12, tooltip: ["Play / pause", "Space"] })) req.togglePause = true;
    const speeds = [0.5, 1, 2, 4, 8, 16];
    speeds.forEach((s, i) => {
      if (ui.button(`${s}×`, x0 + 36 + i * 38, y, 34, 26, { size: 11, accent: c.speed === s })) req.speed = s;
    });
    const bx = x0 + 36 + speeds.length * 38 + 10, bw = x1 - bx - 70;
    const frac = rp.endTick ? rp.tick / rp.endTick : 0;
    ui.bar(bx, y + 9, bw, 8, frac, "#7fb0e8");
    ui.text(`${clock(rp.tick / SIM_HZ)} / ${clock(rp.endTick / SIM_HZ)}`, x1, y + 17, { align: "right", size: 11.5, color: "#c9bea3" });
    if (ui.hit(bx, y, bw, 26)) {
      ui.pointerConsumed = true;
      const at = Math.max(0, Math.min(1, (ui.mx - bx) / bw));
      ui.tooltip([`Jump to ${clock((at * rp.endTick) / SIM_HZ)}`, "Going back re-runs the match from the start, so it takes a moment."]);
      if (ui.clicked) req.seekTo = Math.round(at * rp.endTick);
    }
  }

  private bottomPanel(W: number, H: number, top: number, c: CasterContext, realms: RealmSnapshot[], teams: number[], name: (t: number) => string) {
    const world = c.world;
    const x0 = MINIMAP_SIZE + 30, x1 = W - 12, w = x1 - x0;
    ui.panel(x0 - 6, top - 6, w + 12, H - top);
    CASTER_PANELS.forEach(([id, label], i) => {
      if (ui.button(label, x0 + i * 104, top, 98, 24, { size: 11.5, accent: this.panel === id, tooltip: [label, `Q W E R T, or Tab`] })) this.panel = id;
    });
    const rowTop = top + 34;
    const rowH = Math.min(26, (H - rowTop - 10) / Math.max(1, teams.length));
    const ctx = ui.ctx;
    teams.forEach((t, i) => {
      const y = rowTop + i * rowH;
      const p = world.player(t as Team);
      const r = realms[t];
      ctx.fillStyle = i % 2 ? "rgba(255,255,255,0.025)" : "transparent";
      ctx.fillRect(x0, y, w, rowH);
      ctx.fillStyle = teamCol(t);
      ctx.fillRect(x0, y + 3, 3, rowH - 6);
      const nm = name(t);
      ui.text(nm.length > 14 ? nm.slice(0, 13) + "…" : nm, x0 + 10, y + rowH / 2 + 4, { size: 12, bold: true, color: p.defeated ? "#6f6a5c" : teamLight(t) });
      const cx = x0 + 130;
      const col = "#e2d6ba", dim = "#a89f88";
      const cells = (vals: [string, string][], cw: number) => vals.forEach(([k, v], j) => {
        ui.text(k, cx + j * cw, y + rowH / 2 + 4, { size: 10.5, color: dim });
        ui.text(v, cx + j * cw + cw - 12, y + rowH / 2 + 4, { align: "right", size: 12, bold: true, color: col });
      });
      if (this.panel === "overview") {
        const s = p.stats;
        cells([["Age", ageShort(p.age)], ["Pop", `${p.popUsed}/${p.popCap}`], ["Villagers", String(r.villagers)], ["Army", String(r.army)],
          ["Army value", num(r.armyValue)], ["Kills", String(s.unitsKilled)], ["Lost", String(s.unitsLost)], ["Buildings", String(r.buildings)]], Math.min(120, (w - 140) / 8));
      } else if (this.panel === "economy") {
        const res = p.resources;
        const hist = c.history;
        let rate = 0;
        if (hist.length >= 2) {
          const a = hist[Math.max(0, hist.length - 16)], b = hist[hist.length - 1];
          const dt = b.t - a.t;
          rate = dt > 0 ? ((b.m[t]?.economy ?? 0) - (a.m[t]?.economy ?? 0)) / (dt / 60) : 0;
        }
        cells([["Food", num(res.food)], ["Wood", num(res.wood)], ["Gold", num(res.gold)], ["Gathered", num(p.stats.gathered)],
          ["Per min", num(rate)], ["Villagers", String(r.villagers)], ["Idle", String(r.idleVillagers)], ["Spent", num(p.stats.resourcesSpent)]], Math.min(120, (w - 140) / 8));
      } else if (this.panel === "army") {
        const list = [...r.units.entries()].filter(([u]) => u !== "villager").sort((a, b) => b[1] - a[1]).slice(0, 8);
        let ux = cx;
        if (!list.length) ui.text("no army", ux, y + rowH / 2 + 4, { size: 11.5, color: dim });
        for (const [u, n] of list) {
          const label = `${UNITS[u]?.name ?? u} ×${n}`;
          ui.text(label, ux, y + rowH / 2 + 4, { size: 11.5, color: col });
          ctx.font = "11.5px sans-serif";
          ux += ctx.measureText(label).width + 16;
          if (ux > x1 - 60) break;
        }
      } else if (this.panel === "production") {
        let px = cx;
        const pw = 118;
        if (!r.production.length) ui.text("nothing in production", px, y + rowH / 2 + 4, { size: 11.5, color: dim });
        for (const it of r.production.slice(0, Math.floor((w - 140) / (pw + 8)))) {
          ui.bar(px, y + rowH - 8, pw, 4, it.progress, it.item[0] === "a" ? "#f0c070" : it.item[0] === "t" ? "#7fb0e8" : teamLight(t));
          ui.text(it.label.length > 16 ? it.label.slice(0, 15) + "…" : it.label, px, y + rowH / 2 + 1, { size: 11, color: it.item[0] === "a" ? "#f0c070" : col });
          px += pw + 8;
        }
      } else if (this.panel === "tech") {
        const ups = [...p.upgrades];
        const oaths = (p.oaths ?? []).map((o) => OATHS[o]?.name.replace(/^Oath of the /, "") ?? o);
        ui.text(`${AGES[p.age]?.name ?? ""}`, cx, y + rowH / 2 + 4, { size: 12, bold: true, color: "#f0c070" });
        ui.text(oaths.length ? `Oaths: ${oaths.join(", ")}` : "No Oath yet", cx + 130, y + rowH / 2 + 4, { size: 11.5, color: col });
        const recent = ups.slice(-4).map((u) => UPGRADES[u]?.name ?? u).join(", ");
        ui.text(`${ups.length} researched${recent ? ` — latest: ${recent}` : ""}`, cx + 380, y + rowH / 2 + 4, { size: 11.5, color: dim });
      }
    });
  }

  private graphPanel(W: number, top: number, c: CasterContext, teams: number[], name: (t: number) => string) {
    const hist = c.history;
    const w = Math.min(560, W * 0.4), h = 220, x = 12, y = top;
    ui.panel(x, y, w, h);
    ui.text("ARMY VALUE", x + 14, y + 20, { size: 10.5, bold: true, color: PAL.uiAccent });
    ui.text("SCORE", x + w / 2 + 6, y + 20, { size: 10.5, bold: true, color: PAL.uiAccent });
    if (hist.length < 2) { ui.text("Collecting…", x + w / 2, y + h / 2, { align: "center", size: 12, color: "#a89f88" }); return; }
    const ctx = ui.ctx;
    const plot = (px: number, pw: number, key: "military" | "score") => {
      const max = Math.max(1, ...hist.flatMap((s) => teams.map((t) => s.m[t]?.[key] ?? 0)));
      const t0 = hist[0].t, t1 = hist[hist.length - 1].t || 1;
      for (const t of teams) {
        ctx.strokeStyle = teamCol(t); ctx.lineWidth = 2; ctx.beginPath();
        hist.forEach((s, i) => {
          const X = px + ((s.t - t0) / Math.max(1, t1 - t0)) * pw;
          const Y = y + h - 22 - ((s.m[t]?.[key] ?? 0) / max) * (h - 56);
          if (i) ctx.lineTo(X, Y); else ctx.moveTo(X, Y);
        });
        ctx.stroke();
      }
    };
    plot(x + 14, w / 2 - 24, "military");
    plot(x + w / 2 + 6, w / 2 - 20, "score");
    teams.slice(0, 8).forEach((t, i) => ui.text(name(t).slice(0, 10), x + 14 + i * ((w - 28) / Math.min(8, teams.length)), y + h - 6, { size: 10, bold: true, color: teamLight(t) }));
  }

  private helpPanel(W: number, H: number) {
    const w = 460, h = 330, x = W / 2 - w / 2, y = H / 2 - h / 2;
    ui.panel(x, y, w, h);
    ui.text("Caster keys", x + w / 2, y + 32, { align: "center", size: 20, bold: true, color: "#ffe9b0", font: "Georgia, serif" });
    const rows: [string, string][] = [
      ["1 – 8", "See one player's vision (again: back to all)"], ["0 / V", "See everything"], ["A", "Auto camera on / off"],
      ["Q W E R T / Tab", "Overview · Army · Economy · Production · Tech"], ["G", "Graphs"], ["H", "Clean feed (for streaming)"],
      ["Space", "Pause (replays and AI games)"], ["+ / −", "Speed"], ["Click a player", "Look at their base"],
      ["Click the feed", "Go to where it happened"], ["Esc", "Leave"],
    ];
    rows.forEach(([k, v], i) => {
      ui.text(k, x + 30, y + 70 + i * 22, { size: 12.5, bold: true, color: "#f0c070" });
      ui.text(v, x + 170, y + 70 + i * 22, { size: 12.5, color: "#e2d6ba" });
    });
  }
}
