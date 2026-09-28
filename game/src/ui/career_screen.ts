// The Career screen: a player's own numbers, offline and online together.
//
// Five tabs over one Career (meta/career.ts): an overview (record, time,
// favourites, best and worst, form, how the game goes for you), then factions,
// maps, units and the matches themselves. A filter at the top narrows it to
// skirmish, online or ranked games; "All time" reads the lifetime totals, which
// are exact however many games have been played.

import { ui } from "./ui";
import { audio } from "../engine/audio";
import { PAL, withAlpha } from "../render/palette";
import { drawUnit, setFactionResolver } from "../render/draw";
import { makeEntity } from "../sim/world";
import { Kind, Team } from "../sim/types";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { FACTIONS, FACTION_IDS, factionForUnit } from "../content/factions";
import { COMMANDERS } from "../content/commanders";
import { OATHS } from "../content/oaths";
import { AGES } from "../content/tech";
import { DIFFICULTIES } from "../ai/difficulty";
import { Profile } from "../meta/profile";
import { REPLAY_FILE_EXT, ReplayRecord, deleteReplay, listReplays, parseReplayFile, replayFile, saveReplay } from "../sim/replay";
import { downloadText, pickTextFile } from "./files";
import { ARCHETYPES, MATCH_STYLES, MIN_GAMES, PlaystyleProfile, analyse, matchStyle, ramp } from "../meta/playstyle";
import {
  Career, CareerFilter, CareerMatch, Tally, avgSecs, avgWinSecs, bestBy, careerFor, favourite, favouriteUnitOf,
  kd, mostEffectiveUnit, topUnit, unitKd, winRate, worstBy,
} from "../meta/career";

/**
 * Draw text so it fits `maxW`: shrink it (down to 75% of its size), and only if
 * that isn't enough, cut it with an ellipsis. Nothing on this screen should run
 * into the next column or out of its card.
 */
export function fitText(text: string, x: number, y: number, maxW: number, o: { size?: number; bold?: boolean; color?: string; align?: CanvasTextAlign; font?: string } = {}) {
  const ctx = ui.ctx;
  let size = o.size ?? 12;
  const min = size * 0.75;
  const family = o.font ?? "'Trebuchet MS', sans-serif";
  const measure = (t: string, sz: number) => { ctx.font = `${o.bold ? "bold " : ""}${sz}px ${family}`; return ctx.measureText(t).width; };
  while (size > min && measure(text, size) > maxW) size -= 0.5;
  let t = text;
  if (measure(t, size) > maxW) {
    while (t.length > 1 && measure(t + "…", size) > maxW) t = t.slice(0, -1);
    t = t.trimEnd() + "…";
  }
  ui.text(t, x, y, { ...o, size });
}

/** Word-wrap a short line into at most two lines (the second ends in … if cut). */
function wrapLines(text: string, x: number, y: number, maxW: number, size: number, color: string) {
  const ctx = ui.ctx;
  ctx.font = `${size}px 'Trebuchet MS', sans-serif`;
  const lines: string[] = [];
  let line = "";
  for (const word of text.split(" ")) {
    const test = line ? `${line} ${word}` : word;
    if (ctx.measureText(test).width > maxW && line) { lines.push(line); line = word; } else line = test;
  }
  if (line) lines.push(line);
  lines.slice(0, 2).forEach((l, i) => ui.text(i === 1 && lines.length > 2 ? `${l}…` : l, x, y + i * (size + 3), { size, color }));
}

type Tab = "overview" | "style" | "factions" | "maps" | "units" | "matches" | "replays";
const TABS: [Tab, string][] = [["overview", "Overview"], ["style", "Playstyle"], ["factions", "Factions"], ["maps", "Maps & modes"], ["units", "Units"], ["matches", "Matches"], ["replays", "Replays"]];
const FILTERS: [CareerFilter, string][] = [["all", "All time"], ["skirmish", "Skirmish"], ["online", "Online"], ["ranked", "Ranked"]];

const GOOD = "#8fd07a", BAD = "#e0786a", DIM = "#a89f88", FAINT = "#6f6a5c", TEXT = "#e9dcc0", GOLD = "#e8c060";

// ---------------------------------------------------------------- format --
export function mmss(sec: number): string {
  if (!sec) return "—";
  const s = Math.round(sec), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
  return h ? `${h}h ${String(m).padStart(2, "0")}m` : `${m}:${String(x).padStart(2, "0")}`;
}
const hours = (sec: number) => (sec >= 3600 ? `${(sec / 3600).toFixed(1)} h` : `${Math.round(sec / 60)} min`);
const pct = (x: number) => `${Math.round(x * 100)}%`;
const num = (n: number) => Math.round(n).toLocaleString("en-GB");
const facName = (id: string) => FACTIONS[id as keyof typeof FACTIONS]?.name ?? (id || "—");
const facColor = (id: string) => FACTIONS[id as keyof typeof FACTIONS]?.color ?? "#8f8770";
const unitName = (id: string) => UNITS[id]?.name ?? (id || "—");
const cmdName = (id: string) => COMMANDERS[id]?.name ?? (id || "—");
const oathName = (id: string) => OATHS[id]?.short ?? (id || "—");
const diffName = (id: string) => DIFFICULTIES[id]?.name ?? (id ? id[0].toUpperCase() + id.slice(1) : "—");
const rateColor = (r: number, n: number) => (!n ? FAINT : r >= 0.55 ? GOOD : r <= 0.45 ? BAD : GOLD);

export class CareerScreen {
  private tab: Tab = "overview";
  private filter: CareerFilter = "all";
  private scroll = 0;
  private contentH = 0;
  private cache: { key: string; at: number; data: ReturnType<typeof careerFor> } | null = null;

  private styleCache: { key: string; at: number; p: PlaystyleProfile } | null = null;
  /** The playstyle read for the current filter (recomputed with the data). */
  private style(): PlaystyleProfile {
    const d = this.data();
    if (!this.styleCache || this.styleCache.key !== this.filter || this.styleCache.at !== this.cache!.at) {
      this.styleCache = { key: this.filter, at: this.cache!.at, p: analyse(d.matches) };
    }
    return this.styleCache.p;
  }

  /** Re-read storage at most twice a second (it's parsed JSON). */
  private data() {
    const now = Date.now();
    if (!this.cache || this.cache.key !== this.filter || now - this.cache.at > 500) {
      this.cache = { key: this.filter, at: now, data: careerFor(this.filter) };
    }
    return this.cache.data;
  }

  /** Open straight on the replay list (coming back from watching one). */
  showReplays() { this.tab = "replays"; this.scroll = 0; }

  private watch: ReplayRecord | null = null;
  private confirmDelete = "";

  draw(W: number, H: number, time: number, profile: Profile): "back" | { watch: ReplayRecord } | null {
    const ctx = ui.ctx;
    const bg = ctx.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, "#221a10");
    bg.addColorStop(1, "#100b06");
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, W, H);

    const outer = Math.min(W - 48, 1280);
    const x0 = Math.round(W / 2 - outer / 2);
    const { career: c, matches, exact } = this.data();

    // Header.
    ui.text("Career", x0, 50, { size: 32, bold: true, color: "#ffe9b0", font: "Georgia, serif" });
    const lvl = profile.levelInfo();
    ui.text(`${profile.data.name} · Level ${lvl.level}${c.since ? ` · playing since ${new Date(c.since).toLocaleDateString()}` : ""}`, x0 + 2, 74, { size: 13, color: "#c9bea3" });
    FILTERS.forEach(([id, label], i) => {
      const bw = 96, bx = x0 + outer - (FILTERS.length - i) * (bw + 6);
      if (ui.button(label, bx, 28, bw, 30, { accent: this.filter === id, size: 12.5, tooltip: [label, id === "all" ? "Every game you've played, ever." : `Only ${label.toLowerCase()} games (from your last matches).`] })) {
        if (this.filter !== id) { this.filter = id; this.scroll = 0; audio.play("ui"); }
      }
    });
    if (!exact) ui.text("from your most recent matches", x0 + outer, 74, { align: "right", size: 11, color: FAINT });

    // Tabs.
    const ty = 90;
    TABS.forEach(([id, label], i) => {
      const tw = Math.min(132, (outer - 8 * (TABS.length - 1)) / TABS.length);
      if (ui.button(label, x0 + i * (tw + 8), ty, tw, 32, { accent: this.tab === id, size: 13.5 })) {
        if (this.tab !== id) { this.tab = id; this.scroll = 0; audio.play("ui"); }
      }
    });

    // Scrolling content.
    const top = ty + 44, FOOT = 64;
    const viewH = H - top - FOOT;
    const maxScroll = Math.max(0, this.contentH - viewH);
    if (ui.wheel && ui.my > top && ui.my < top + viewH) this.scroll = Math.max(0, Math.min(maxScroll, this.scroll + ui.wheel * 0.6));
    this.scroll = Math.min(this.scroll, maxScroll);
    ui.pushScroll(this.scroll, { x: 0, y: top, w: W, h: viewH });
    let end = top;
    if (this.tab === "replays") end = this.replays(x0, top, outer);
    else if (!c.all.played) end = this.empty(x0, top, outer);
    else if (this.tab === "overview") end = this.overview(c, matches, x0, top, outer, time, profile);
    else if (this.tab === "style") end = this.playstyle(this.style(), x0, top, outer, time);
    else if (this.tab === "factions") end = this.factions(c, x0, top, outer, time);
    else if (this.tab === "maps") end = this.maps(c, x0, top, outer);
    else if (this.tab === "units") end = this.units(c, x0, top, outer, time);
    else end = this.matchList(matches, x0, top, outer);
    this.contentH = end - top + 16;
    ui.popScroll();
    ui.scrollbar(x0 + outer + 8, top, viewH, this.scroll, this.contentH);

    // Footer.
    const fy = H - FOOT;
    ctx.fillStyle = "rgba(10,7,3,0.92)";
    ctx.fillRect(0, fy, W, FOOT);
    ctx.fillStyle = withAlpha(PAL.uiAccent, 0.3);
    ctx.fillRect(0, fy, W, 1);
    if (ui.button("⟵  Back", x0, fy + 12, 130, 40, { size: 15 })) return "back";
    if (this.watch) { const w = this.watch; this.watch = null; return { watch: w }; }
    ui.text("Skirmish and online games both count. Kept on this device.", x0 + outer, fy + 37, { align: "right", size: 11.5, color: FAINT });
    return null;
  }

  // ---------------------------------------------------------------- pieces --
  private heading(x: number, y: number, w: number, t: string, sub = "") {
    ui.text(t.toUpperCase(), x, y + 14, { size: 11, bold: true, color: PAL.uiAccent });
    if (sub) ui.text(sub, x + w, y + 14, { align: "right", size: 11, color: FAINT });
    ui.ctx.fillStyle = withAlpha(PAL.uiAccent, 0.22);
    ui.ctx.fillRect(x, y + 22, w, 1);
    return y + 32;
  }

  private tile(x: number, y: number, w: number, h: number, label: string, value: string, sub = "", color = "#fff0cc") {
    ui.panel(x, y, w, h);
    ui.text(label.toUpperCase(), x + 14, y + 22, { size: 10.5, bold: true, color: DIM });
    ui.text(value, x + 14, y + 52, { size: 25, bold: true, color, font: "Georgia, serif" });
    if (sub) ui.text(sub, x + 14, y + 72, { size: 11.5, color: FAINT });
  }

  private rateBar(x: number, y: number, w: number, r: number, n: number) {
    ui.bar(x, y - 5, w, 8, n ? r : 0, rateColor(r, n));
    ui.ctx.fillStyle = "rgba(255,255,255,0.25)";
    ui.ctx.fillRect(x + w / 2, y - 8, 1, 14);
    ui.text(n ? pct(r) : "—", x + w + 8, y + 3, { size: 12.5, bold: true, color: rateColor(r, n) });
  }

  private dot(x: number, y: number, color: string, r = 5) {
    ui.ctx.fillStyle = color;
    ui.ctx.beginPath(); ui.ctx.arc(x, y, r, 0, Math.PI * 2); ui.ctx.fill();
  }

  /**
   * A unit portrait fitted inside a box: scaled so the whole figure (horse,
   * lance and all) fits, standing on the box's bottom edge, and clipped to it
   * so nothing can spill onto the card around it.
   */
  private unitArt(id: string, box: { x: number; y: number; w: number; h: number }, time: number, faction?: string) {
    const def = UNITS[id];
    if (!def) return;
    // A figure reaches ~3 radii up from its feet and ~1.6 either side; a
    // lance or bow a little past that.
    const s = Math.min(3, (box.h * 0.92) / (def.radius * 3.2), (box.w * 0.92) / (def.radius * 3.6));
    const x = box.x + box.w / 2, y = box.y + box.h - def.radius * 0.7 * s - 2;
    const e = makeEntity();
    Object.assign(e, { kind: Kind.Unit, type: id, team: Team.Player, x, y, radius: def.radius, hp: def.hp, maxHp: def.hp, facing: -0.5, attackInterval: def.attackInterval, animPhase: time * 0.3, seed: 5 });
    const f = faction ?? factionForUnit(id)?.id;
    if (f) setFactionResolver(() => f);
    const ctx = ui.ctx;
    ctx.save();
    ctx.beginPath();
    ctx.rect(box.x, box.y, box.w, box.h);
    ctx.clip();
    ctx.translate(x, y); ctx.scale(s, s); ctx.translate(-x, -y);
    try { drawUnit(ctx, e, time * 0.3); } catch { /* a portrait is never worth a crash */ }
    ctx.restore();
    if (f) setFactionResolver(null);
  }

  private empty(x: number, y: number, w: number) {
    ui.panel(x, y + 20, w, 140);
    ui.text("No games yet", x + w / 2, y + 74, { align: "center", size: 22, bold: true, color: "#ffe9b0", font: "Georgia, serif" });
    ui.text(this.filter === "all" ? "Play a Skirmish or an online match — every game you finish lands here." : "No games of this kind yet. Try another filter.",
      x + w / 2, y + 104, { align: "center", size: 13.5, color: DIM });
    return y + 170;
  }

  // -------------------------------------------------------------- overview --
  private overview(c: Career, matches: CareerMatch[], x: number, y: number, w: number, time: number, profile: Profile): number {
    const a = c.all;
    const cols = w >= 1000 ? 6 : 3;
    const gap = 12, tw = (w - gap * (cols - 1)) / cols;
    const tiles: [string, string, string, string?][] = [
      ["Matches", num(a.played), `${a.won} won · ${a.played - a.won} lost`],
      ["Win rate", pct(winRate(a)), `${c.streak > 0 ? `${c.streak} win streak` : c.streak < 0 ? `${-c.streak} loss streak` : "—"} · best ${c.bestStreak}`, rateColor(winRate(a), a.played)],
      ["Time played", hours(a.secs), `longest game ${mmss(a.longest)}`],
      ["Average game", mmss(avgSecs(a)), `across ${a.played} match${a.played === 1 ? "" : "es"}`],
      ["Average win", mmss(avgWinSecs(a)), `fastest ${mmss(a.fastestWin)}`, GOOD],
      ["Kills : losses", kd(a).toFixed(2), `${num(a.kills)} killed · ${num(a.losses)} lost`],
    ];
    tiles.forEach(([l, v, s, col], i) => this.tile(x + (i % cols) * (tw + gap), y + Math.floor(i / cols) * 96, tw, 86, l, v, s, col));
    y += Math.ceil(tiles.length / cols) * 96 + 8;

    // Your style, in one line — the Playstyle tab has the why.
    {
      const sp = this.style();
      ui.panel(x, y, w, 64);
      ui.text("YOUR STYLE", x + 16, y + 22, { size: 10.5, bold: true, color: DIM });
      fitText(sp.title, x + 16, y + 48, w * 0.36, { size: 20, bold: true, color: sp.primary ? GOLD : TEXT, font: "Georgia, serif" });
      fitText(sp.primary ? sp.primary.archetype.short : sp.enough ? "You adapt to the game in front of you." : `Play ${MIN_GAMES - sp.games} more game${MIN_GAMES - sp.games === 1 ? "" : "s"} to find out.`, x + w * 0.4, y + 30, w * 0.42, { size: 13, color: TEXT });
      fitText(sp.traits.slice(0, 3).map((t) => t.name).join(" · "), x + w * 0.4, y + 50, w * 0.42, { size: 11.5, color: DIM });
      if (ui.button("Why? →", x + w - 116, y + 16, 100, 32, { size: 13, accent: true })) { this.tab = "style"; this.scroll = 0; audio.play("ui"); }
      y += 76;
    }

    // Favourites.
    y = this.heading(x, y, w, "Your favourites", "most played");
    const favF = favourite(c.byFaction), favM = favourite(c.byMap), favU = topUnit(c, "trained"), favC = favourite(c.byCommander), favO = favourite(c.byOath);
    const favs: [string, string, string, string, (cx: number, cy: number) => void][] = [
      ["Faction", facName(favF), favF ? `${c.byFaction[favF].played} games · ${pct(winRate(c.byFaction[favF]))} won` : "", facColor(favF),
        (bx, by) => { if (favF) this.unitArt(FACTIONS[favF as keyof typeof FACTIONS].replaces.militia ?? FACTIONS[favF as keyof typeof FACTIONS].extra[0] ?? "militia", { x: bx, y: by, w: 64, h: 80 }, time, favF); }],
      ["Map", favM || "—", favM ? `${c.byMap[favM].played} games · ${pct(winRate(c.byMap[favM]))} won` : "", TEXT, () => {}],
      ["Unit", unitName(favU), favU ? `${num(c.units[favU].trained)} trained · ${num(c.units[favU].lost)} lost` : "", TEXT,
        (bx, by) => { if (favU) this.unitArt(favU, { x: bx, y: by, w: 64, h: 80 }, time); }],
      ["Commander", cmdName(favC), favC ? `${c.byCommander[favC].played} games · ${pct(winRate(c.byCommander[favC]))} won` : "skirmish only", COMMANDERS[favC]?.color ?? TEXT, () => {}],
      ["Oath", favO ? oathName(favO) : "—", favO ? `sworn in ${c.byOath[favO].played} games · ${pct(winRate(c.byOath[favO]))} won` : "reach the Banner Age", TEXT, () => {}],
    ];
    const fcols = w >= 1000 ? 5 : 3, fw = (w - gap * (fcols - 1)) / fcols;
    favs.forEach(([label, value, sub, color, art], i) => {
      const fx = x + (i % fcols) * (fw + gap), fy = y + Math.floor(i / fcols) * 106;
      ui.panel(fx, fy, fw, 96);
      ui.text(label.toUpperCase(), fx + 14, fy + 22, { size: 10.5, bold: true, color: DIM });
      fitText(value, fx + 14, fy + 50, fw - 90, { size: 17, bold: true, color, font: "Georgia, serif" });
      fitText(sub, fx + 14, fy + 72, fw - 90, { size: 11, color: FAINT });
      art(fx + fw - 72, fy + 8);
    });
    y += Math.ceil(favs.length / fcols) * 106 + 8;

    // Strengths and weaknesses.
    y = this.heading(x, y, w, "Where you're strong, where you're not", "3+ games each");
    const pairs: [string, string, Record<string, Tally>, (k: string) => string, boolean][] = [
      ["Best faction", "Weakest faction", c.byFaction, facName, false],
      ["Best map", "Toughest map", c.byMap, (k) => k, false],
      ["Easiest opponent", "Your nemesis", c.vsFaction, facName, true],
    ];
    const pw = (w - gap * 2) / 3;
    pairs.forEach(([good, bad, rec, name], i) => {
      const px = x + i * (pw + gap);
      ui.panel(px, y, pw, 104);
      const b = bestBy(rec), wv = worstBy(rec);
      const line = (label: string, k: string, yy: number, col: string) => {
        ui.text(label, px + 14, yy, { size: 11.5, color: DIM });
        ui.text(k ? name(k) : "not enough games", px + 14, yy + 20, { size: 15, bold: true, color: k ? col : FAINT });
        if (k) ui.text(`${pct(winRate(rec[k]))} of ${rec[k].played}`, px + pw - 14, yy + 20, { align: "right", size: 12.5, color: col });
      };
      line(good, b, y + 24, GOOD);
      line(bad, wv && wv !== b ? wv : "", y + 66, BAD);
    });
    y += 116;

    // Recent form.
    y = this.heading(x, y, w, "Recent form", "last 30 games, newest on the right");
    const recent = [...matches].slice(0, 30).reverse();
    const sq = Math.min(26, (w - 29 * 4) / 30);
    recent.forEach((m, i) => {
      const cx = x + i * (sq + 4);
      ui.ctx.fillStyle = m.won ? "rgba(127,207,122,0.8)" : "rgba(224,120,106,0.75)";
      ui.ctx.beginPath(); ui.ctx.roundRect(cx, y, sq, sq, 4); ui.ctx.fill();
      ui.text(m.won ? "W" : "L", cx + sq / 2, y + sq / 2 + 4, { align: "center", size: 11, bold: true, color: "#1a140c" });
      if (ui.hit(cx, y, sq, sq)) ui.tooltip([`${m.won ? "Won" : "Lost"} — ${m.map}`, `${facName(m.faction)} vs ${m.foes.map(facName).join(", ") || "—"}`, `${mmss(m.durationSec)} · ${m.kind === "online" ? (m.ranked ? "ranked online" : "online") : `skirmish, ${diffName(m.difficulty)}`}`]);
    });
    if (!recent.length) ui.text("—", x, y + 16, { color: FAINT });
    y += sq + 14;
    // Rolling win rate (10 games) as a line.
    if (recent.length >= 4) {
      const ch = 90, cw = w;
      ui.panel(x, y, cw, ch + 24);
      const pts: number[] = recent.map((_, i) => {
        const win = recent.slice(Math.max(0, i - 9), i + 1);
        return win.filter((m) => m.won).length / win.length;
      });
      const px = (i: number) => x + 16 + (i / Math.max(1, pts.length - 1)) * (cw - 32);
      const py = (v: number) => y + 12 + (1 - v) * ch;
      const ctx = ui.ctx;
      ctx.strokeStyle = "rgba(255,255,255,0.12)"; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(x + 16, py(0.5)); ctx.lineTo(x + cw - 16, py(0.5)); ctx.stroke();
      ctx.strokeStyle = GOLD; ctx.lineWidth = 2.5; ctx.beginPath();
      pts.forEach((v, i) => (i ? ctx.lineTo(px(i), py(v)) : ctx.moveTo(px(i), py(v))));
      ctx.stroke();
      ui.text("win rate over your last 10 games, game by game", x + 16, y + ch + 18, { size: 11, color: FAINT });
      ui.text(`now ${pct(pts[pts.length - 1])}`, x + cw - 16, y + ch + 18, { align: "right", size: 11.5, bold: true, color: GOLD });
      y += ch + 36;
    }

    // Game by game: how your matches go.
    y = this.heading(x, y, w, "How your games go", "averages per match");
    const n = Math.max(1, a.played);
    const perMin = a.secs ? (a.gathered / (a.secs / 60)) : 0;
    const rows: [string, string][] = [
      ["Resources gathered", `${num(a.gathered / n)}  (${num(perMin)} a minute)`],
      ["Food · wood · gold", `${num(c.resources.food / n)} · ${num(c.resources.wood / n)} · ${num(c.resources.gold / n)}`],
      ["Enemy buildings razed", `${(c.razed / n).toFixed(1)}  ·  your buildings lost ${(c.buildingsLost / n).toFixed(1)}`],
      ["Damage dealt · taken", `${num(c.damageDealt / n)} · ${num(c.damageTaken / n)}`],
      ["Technologies researched", (c.upgrades / n).toFixed(1)],
      ["Biggest army ever", `${num(c.bestArmy)} soldiers  ·  best score ${num(c.bestScore)}`],
      ["Town Centre idle", `${pct(c.tcIdleShareSum / n)} of the match on average`],
      ["Idle villager time", `${mmss(c.idleVillagerTime / n)} villager-time a game`],
    ];
    const half = Math.ceil(rows.length / 2), cw2 = (w - gap) / 2;
    ui.panel(x, y, w, half * 26 + 16);
    rows.forEach(([k, v], i) => {
      const rx = x + (i < half ? 0 : cw2 + gap) + 16, ry = y + 26 + (i % half) * 26;
      ui.text(k, rx, ry, { size: 12.5, color: DIM });
      ui.text(v, rx + cw2 - 32, ry, { align: "right", size: 12.5, bold: true, color: TEXT });
    });
    y += half * 26 + 28;

    // Ages.
    y = this.heading(x, y, w, "Advancing", "share of games that reached each age, and how long it took");
    const aw = (w - gap * 3) / 4;
    for (let i = 0; i < 4; i++) {
      const ax = x + i * (aw + gap);
      ui.panel(ax, y, aw, 70);
      const reached = i === 0 ? a.played : c.ageReached[i] ?? 0;
      ui.text(AGES[i]?.name ?? `Age ${i}`, ax + 14, y + 22, { size: 13, bold: true, color: TEXT });
      ui.text(pct(reached / n), ax + 14, y + 50, { size: 20, bold: true, color: GOLD, font: "Georgia, serif" });
      ui.text(i === 0 ? "every game" : reached ? `average ${mmss((c.ageTimeSum[i] ?? 0) / reached)}` : "not yet", ax + aw - 14, y + 50, { align: "right", size: 12, color: DIM });
    }
    y += 82;

    // Skirmish vs online, and against each AI.
    y = this.heading(x, y, w, "By kind of game");
    const kinds: [string, string][] = [["skirmish", "Skirmish (vs AI)"], ["online", "Online"], ["ranked", "Ranked online"]];
    const kw = (w - gap * 2) / 3;
    kinds.forEach(([k, label], i) => {
      const t = c.byKind[k];
      const kx = x + i * (kw + gap);
      ui.panel(kx, y, kw, 74);
      ui.text(label, kx + 14, y + 22, { size: 13, bold: true, color: TEXT });
      ui.text(t ? `${t.won}–${t.played - t.won}` : "no games", kx + 14, y + 50, { size: 18, bold: true, color: t ? "#fff0cc" : FAINT, font: "Georgia, serif" });
      if (t) this.rateBar(kx + 110, y + 46, kw - 180, winRate(t), t.played);
    });
    y += 86;
    const diffs = Object.entries(c.byDifficulty).sort((p, q) => (DIFFICULTIES[p[0]] ? Object.keys(DIFFICULTIES).indexOf(p[0]) : 99) - (DIFFICULTIES[q[0]] ? Object.keys(DIFFICULTIES).indexOf(q[0]) : 99));
    if (diffs.length) {
      y = this.heading(x, y, w, "Against each AI");
      ui.panel(x, y, w, diffs.length * 28 + 14);
      diffs.forEach(([d, t], i) => {
        const ry = y + 26 + i * 28;
        ui.text(diffName(d), x + 16, ry, { size: 13, bold: true, color: TEXT });
        ui.text(`${t.won}–${t.played - t.won}`, x + 200, ry, { size: 12.5, color: DIM });
        this.rateBar(x + 280, ry - 2, Math.min(360, w - 560), winRate(t), t.played);
        ui.text(`avg ${mmss(avgSecs(t))} · win ${mmss(avgWinSecs(t))}`, x + w - 16, ry, { align: "right", size: 12, color: DIM });
      });
      y += diffs.length * 28 + 26;
    }
    void profile;
    return y;
  }

  // ------------------------------------------------------------- factions --
  private table(x: number, y: number, w: number, head: [string, number, CanvasTextAlign?][], rows: number) {
    ui.panel(x, y, w, 48 + rows * 34);
    // Each header gets the room up to its neighbour (whichever side it grows
    // towards), so long headers shrink instead of running together.
    const pos = head.map(([, cx]) => cx).sort((a, b) => a - b);
    for (const [label, cx, align] of head) {
      const i = pos.indexOf(cx);
      const room = align === "right" ? cx - (i > 0 ? pos[i - 1] : x) - 10 : (i < pos.length - 1 ? pos[i + 1] : x + w) - cx - 10;
      fitText(label.toUpperCase(), cx, y + 22, Math.max(24, room), { size: 10.5, bold: true, color: DIM, align: align ?? "left" });
    }
    return y + 40;
  }

  private factions(c: Career, x: number, y: number, w: number, time: number): number {
    y = this.heading(x, y, w, "Your record with each faction");
    const C = { name: x + 60, played: x + 290, wl: x + 330, rate: x + 400, avg: x + 700, win: x + 790, fast: x + 880, kd: x + 950, fav: x + 990 };
    let ry = this.table(x, y, w, [["Faction", C.name], ["Games", C.played, "right"], ["W–L", C.wl], ["Win rate", C.rate], ["Avg game", C.avg, "right"], ["Avg win", C.win, "right"], ["Fastest win", C.fast, "right"], ["K : L", C.kd, "right"], ["Favourite unit", C.fav]], FACTION_IDS.length);
    const ids = [...FACTION_IDS].sort((p, q) => (c.byFaction[q]?.played ?? 0) - (c.byFaction[p]?.played ?? 0));
    for (const id of ids) {
      const t = c.byFaction[id];
      const f = FACTIONS[id];
      const col = t ? TEXT : FAINT;
      this.unitArt(f.replaces.militia ?? f.extra[0] ?? "militia", { x: x + 8, y: ry, w: 44, h: 32 }, time, id);
      ui.text(f.name, C.name, ry + 21, { size: 14, bold: true, color: t ? f.color : FAINT });
      ui.text(t ? String(t.played) : "0", C.played, ry + 21, { align: "right", size: 13, color: col });
      ui.text(t ? `${t.won}–${t.played - t.won}` : "—", C.wl, ry + 21, { size: 13, color: col });
      this.rateBar(C.rate, ry + 19, 150, winRate(t), t?.played ?? 0);
      ui.text(t ? mmss(avgSecs(t)) : "—", C.avg, ry + 21, { align: "right", size: 13, color: col });
      ui.text(t ? mmss(avgWinSecs(t)) : "—", C.win, ry + 21, { align: "right", size: 13, color: t?.won ? GOOD : FAINT });
      ui.text(t ? mmss(t.fastestWin) : "—", C.fast, ry + 21, { align: "right", size: 13, color: col });
      ui.text(t ? kd(t).toFixed(2) : "—", C.kd, ry + 21, { align: "right", size: 13, color: col });
      ui.text(t ? unitName(favouriteUnitOf(t)) : "—", C.fav, ry + 21, { size: 13, color: col });
      ry += 34;
    }
    y = ry + 32;

    // Opponents and allies.
    const half = (w - 12) / 2;
    const vsTable = (tx: number, title: string, rec: Record<string, Tally>, note: string) => {
      let yy = this.heading(tx, y, half, title, note);
      const played = FACTION_IDS.filter((id) => rec[id]);
      yy = this.table(tx, yy, half, [["Faction", tx + 16], ["Games", tx + half * 0.5, "right"], ["Your win rate", tx + half * 0.55]], Math.max(1, played.length));
      if (!played.length) ui.text("No games yet.", tx + 16, yy + 21, { size: 13, color: FAINT });
      for (const id of played.sort((p, q) => rec[q].played - rec[p].played)) {
        this.dot(tx + 22, yy + 17, facColor(id));
        ui.text(facName(id), tx + 34, yy + 21, { size: 13, color: TEXT });
        ui.text(String(rec[id].played), tx + half * 0.5, yy + 21, { align: "right", size: 13, color: DIM });
        this.rateBar(tx + half * 0.55, yy + 19, half * 0.3, winRate(rec[id]), rec[id].played);
        yy += 34;
      }
      return yy;
    };
    const e1 = vsTable(x, "Against each enemy faction", c.vsFaction, "matches it was on the other side");
    const e2 = vsTable(x + half + 12, "Beside each ally faction", c.withFaction, "team games");
    return Math.max(e1, e2) + 16;
  }

  // ----------------------------------------------------------------- maps --
  private tallyTable(x: number, y: number, w: number, title: string, rec: Record<string, Tally>, name: (k: string) => string, note = ""): number {
    y = this.heading(x, y, w, title, note);
    const keys = Object.keys(rec).sort((p, q) => rec[q].played - rec[p].played);
    const C = { name: x + 16, played: x + w * 0.36, wl: x + w * 0.4, rate: x + w * 0.48, avg: x + w * 0.76, win: x + w * 0.86, fast: x + w - 16 };
    let ry = this.table(x, y, w, [["", C.name], ["Games", C.played, "right"], ["W–L", C.wl], ["Win rate", C.rate], ["Avg game", C.avg, "right"], ["Avg win", C.win, "right"], ["Fastest", C.fast, "right"]], Math.max(1, keys.length));
    if (!keys.length) ui.text("No games yet.", C.name, ry + 21, { size: 13, color: FAINT });
    for (const k of keys) {
      const t = rec[k];
      ui.text(name(k), C.name, ry + 21, { size: 13.5, bold: true, color: TEXT });
      ui.text(String(t.played), C.played, ry + 21, { align: "right", size: 13, color: DIM });
      ui.text(`${t.won}–${t.played - t.won}`, C.wl, ry + 21, { size: 13, color: DIM });
      this.rateBar(C.rate, ry + 19, w * 0.18, winRate(t), t.played);
      ui.text(mmss(avgSecs(t)), C.avg, ry + 21, { align: "right", size: 13, color: TEXT });
      ui.text(mmss(avgWinSecs(t)), C.win, ry + 21, { align: "right", size: 13, color: t.won ? GOOD : FAINT });
      ui.text(mmss(t.fastestWin), C.fast, ry + 21, { align: "right", size: 13, color: TEXT });
      ry += 34;
    }
    return ry + 20;
  }

  private maps(c: Career, x: number, y: number, w: number): number {
    y = this.tallyTable(x, y, w, "Maps", c.byMap, (k) => k, "most played first");
    y = this.tallyTable(x, y, w, "Formats", c.byFormat, (k) => k);
    const MODES: Record<string, string> = { conquest: "Conquest", survival: "Survival", koth: "King of the Hill", regicide: "Regicide" };
    y = this.tallyTable(x, y, w, "Modes", c.byMode, (k) => MODES[k] ?? k);
    y = this.tallyTable(x, y, w, "Commanders", c.byCommander, cmdName, "skirmish");
    y = this.tallyTable(x, y, w, "Oaths sworn", c.byOath, (k) => OATHS[k]?.name ?? k, "a game counts for every Oath sworn in it");
    return y;
  }

  // ---------------------------------------------------------------- units --
  private units(c: Career, x: number, y: number, w: number, time: number): number {
    const ids = Object.keys(c.units).filter((u) => UNITS[u]).sort((p, q) => c.units[q].trained - c.units[p].trained || (c.units[q].kills ?? 0) - (c.units[p].kills ?? 0));
    const gap = 12, cols = w >= 1100 ? 4 : 2, tw = (w - gap * (cols - 1)) / cols;
    const best = mostEffectiveUnit(c);
    const tops: [string, string, string, string][] = [
      ["Trained most", topUnit(c, "trained"), (() => { const v = c.units[topUnit(c, "trained")]; return v ? `${num(v.trained)} trained` : ""; })(), "Your go-to soldier."],
      ["Deadliest", topUnit(c, "kills"), (() => { const v = c.units[topUnit(c, "kills")]; return v ? `${num(v.kills)} kills · ${num(v.damage)} damage` : ""; })(), "More enemies fall to it than to anything else you field."],
      ["Best trader", best, best ? `${unitKd(c.units[best]).toFixed(2)} kills per loss` : "", "Highest K/D of any unit you've trained 10+ of."],
      ["You lose most", topUnit(c, "lost"), (() => { const v = c.units[topUnit(c, "lost")]; return v ? `${num(v.lost)} lost` : ""; })(), "Where your army bleeds."],
    ];
    tops.forEach(([label, id, value, sub], i) => {
      const tx = x + (i % cols) * (tw + gap), ty = y + Math.floor(i / cols) * 130;
      ui.panel(tx, ty, tw, 120);
      ui.text(label.toUpperCase(), tx + 14, ty + 22, { size: 10.5, bold: true, color: DIM });
      fitText(id ? unitName(id) : "—", tx + 14, ty + 50, tw - 124, { size: 18, bold: true, color: "#fff0cc", font: "Georgia, serif" });
      fitText(value || "not enough games yet", tx + 14, ty + 72, tw - 124, { size: 12, color: value ? GOLD : FAINT });
      wrapLines(sub, tx + 14, ty + 92, tw - 120, 10.5, FAINT);
      if (id) this.unitArt(id, { x: tx + tw - 100, y: ty + 8, w: 92, h: 104 }, time);
    });
    y += Math.ceil(tops.length / cols) * 130 + 4;

    y = this.heading(x, y, w, "Every unit", "K/D = enemies it killed per one of yours lost");
    const maxT = Math.max(1, ...ids.map((u) => c.units[u].trained));
    const C = { name: x + 64, tr: x + w * 0.24, lost: x + w * 0.4, kills: x + w * 0.48, kd: x + w * 0.56, dmg: x + w * 0.65, razed: x + w * 0.73, win: x + w * 0.8, killed: x + w - 16 };
    let ry = this.table(x, y, w, [["Unit", C.name], ["Trained", C.tr], ["Lost", C.lost, "right"], ["Kills", C.kills, "right"], ["K/D", C.kd, "right"], ["Damage", C.dmg, "right"], ["Razed", C.razed, "right"], ["Win % when used", C.win], ["Enemy of type killed", C.killed, "right"]], Math.max(1, ids.length));
    if (!ids.length) ui.text("No units yet.", C.name, ry + 21, { size: 13, color: FAINT });
    for (const u of ids) {
      const v = c.units[u];
      const vill = u === "villager";
      this.unitArt(u, { x: x + 8, y: ry, w: 48, h: 32 }, time);
      fitText(unitName(u), C.name, ry + 21, C.tr - C.name - 10, { size: 13.5, bold: true, color: TEXT });
      ui.bar(C.tr, ry + 14, w * 0.1, 8, v.trained / maxT, GOLD);
      ui.text(num(v.trained), C.tr + w * 0.1 + 8, ry + 21, { size: 12.5, color: TEXT });
      ui.text(num(v.lost), C.lost, ry + 21, { align: "right", size: 12.5, color: v.lost ? BAD : FAINT });
      ui.text(num(v.kills ?? 0), C.kills, ry + 21, { align: "right", size: 12.5, color: v.kills ? GOOD : FAINT });
      const k = unitKd(v);
      const noKd = vill || !(v.kills || v.lost);
      ui.text(noKd ? "—" : k.toFixed(2), C.kd, ry + 21, { align: "right", size: 12.5, bold: true, color: noKd ? FAINT : k >= 1.2 ? GOOD : k < 0.8 ? BAD : GOLD });
      ui.text(v.damage ? num(v.damage) : "—", C.dmg, ry + 21, { align: "right", size: 12.5, color: DIM });
      ui.text(v.razed ? num(v.razed) : "—", C.razed, ry + 21, { align: "right", size: 12.5, color: DIM });
      if (v.games) this.rateBar(C.win, ry + 19, w * 0.1, v.wins / v.games, v.games);
      else ui.text("—", C.win, ry + 21, { size: 12.5, color: FAINT });
      ui.text(num(v.killed), C.killed, ry + 21, { align: "right", size: 12.5, color: DIM });
      ry += 34;
    }
    y = ry + 20;
    const blds = Object.entries(c.buildings).filter(([b]) => BUILDINGS[b]).sort((p, q) => q[1] - p[1]);
    if (blds.length) {
      y = this.heading(x, y, w, "Buildings you raise", "all time");
      const bw = (w - gap * 3) / 4;
      blds.forEach(([b, n], i) => {
        const bx = x + (i % 4) * (bw + gap), by = y + Math.floor(i / 4) * 40;
        ui.panel(bx, by, bw, 34);
        ui.text(BUILDINGS[b].name, bx + 12, by + 22, { size: 12.5, color: TEXT });
        ui.text(num(n), bx + bw - 12, by + 22, { align: "right", size: 12.5, bold: true, color: GOLD });
      });
      y += Math.ceil(blds.length / 4) * 40 + 12;
    }
    return y;
  }

  // ------------------------------------------------------------- playstyle --
  private block(text: string, x: number, y: number, maxW: number, size: number, color: string, bold = false): number {
    const ctx = ui.ctx;
    ctx.font = `${bold ? "bold " : ""}${size}px 'Trebuchet MS', sans-serif`;
    let line = "", yy = y;
    for (const word of text.split(" ")) {
      const test = line ? `${line} ${word}` : word;
      if (ctx.measureText(test).width > maxW && line) { ui.text(line, x, yy, { size, color, bold }); line = word; yy += size + 5; }
      else line = test;
    }
    if (line) { ui.text(line, x, yy, { size, color, bold }); yy += size + 5; }
    return yy - y;
  }

  /** The height `block` would draw, without drawing. */
  private blockHeight(text: string, maxW: number, size: number): number {
    const ctx = ui.ctx;
    ctx.font = `${size}px 'Trebuchet MS', sans-serif`;
    let line = "", rows = 0;
    for (const word of text.split(" ")) {
      const test = line ? `${line} ${word}` : word;
      if (ctx.measureText(test).width > maxW && line) { rows++; line = word; } else line = test;
    }
    if (line) rows++;
    return rows * (size + 5);
  }

  /** A drawn emblem per style (emoji render differently everywhere). */
  private medallion(id: string, cx: number, cy: number, r: number, color: string) {
    const ctx = ui.ctx;
    ctx.save();
    ctx.translate(cx, cy);
    ctx.strokeStyle = color; ctx.fillStyle = color; ctx.lineWidth = Math.max(2, r * 0.09); ctx.lineCap = "round"; ctx.lineJoin = "round";
    ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.globalAlpha = 0.14; ctx.fill(); ctx.globalAlpha = 1; ctx.stroke();
    const u = r / 10;
    ctx.beginPath();
    switch (id) {
      case "rush": // crossed swords
        ctx.moveTo(-6 * u, -6 * u); ctx.lineTo(6 * u, 6 * u); ctx.moveTo(6 * u, -6 * u); ctx.lineTo(-6 * u, 6 * u);
        ctx.moveTo(-6.5 * u, 3 * u); ctx.lineTo(-3 * u, 6.5 * u); ctx.moveTo(6.5 * u, 3 * u); ctx.lineTo(3 * u, 6.5 * u); ctx.stroke(); break;
      case "pressure": // a sword, then an hourglass: hit early, finish late
        ctx.moveTo(-7 * u, 2 * u); ctx.lineTo(0, -5 * u); ctx.moveTo(-6 * u, -2 * u); ctx.lineTo(-3 * u, 1 * u);
        ctx.moveTo(1.5 * u, 0); ctx.lineTo(6.5 * u, 0); ctx.lineTo(1.5 * u, 7 * u); ctx.lineTo(6.5 * u, 7 * u); ctx.closePath(); ctx.stroke(); break;
      case "turtle": // a tower
        ctx.rect(-4 * u, -3 * u, 8 * u, 9 * u); ctx.moveTo(-5 * u, -3 * u); ctx.lineTo(-5 * u, -6 * u); ctx.lineTo(-2.5 * u, -6 * u); ctx.lineTo(-2.5 * u, -4.5 * u);
        ctx.lineTo(0, -4.5 * u); ctx.lineTo(0, -6 * u); ctx.lineTo(2.5 * u, -6 * u); ctx.lineTo(2.5 * u, -4.5 * u); ctx.lineTo(5 * u, -4.5 * u); ctx.lineTo(5 * u, -3 * u); ctx.stroke(); break;
      case "late": // hourglass
        ctx.moveTo(-4 * u, -6 * u); ctx.lineTo(4 * u, -6 * u); ctx.lineTo(-4 * u, 6 * u); ctx.lineTo(4 * u, 6 * u); ctx.closePath(); ctx.stroke(); break;
      case "boom": // a wheat sheaf
        for (const a of [-0.35, 0, 0.35]) { ctx.save(); ctx.rotate(a); ctx.moveTo(0, 6 * u); ctx.lineTo(0, -5 * u); ctx.restore(); }
        ctx.stroke(); ctx.beginPath(); ctx.ellipse(0, -5 * u, 1.6 * u, 2.6 * u, 0, 0, Math.PI * 2); ctx.fill(); break;
      case "demolition": // a flame
        ctx.moveTo(0, 6 * u); ctx.bezierCurveTo(-6 * u, 4 * u, -4 * u, -2 * u, 0, -7 * u); ctx.bezierCurveTo(1 * u, -2 * u, 5 * u, -1 * u, 4 * u, 3 * u); ctx.bezierCurveTo(3.5 * u, 5 * u, 2 * u, 6 * u, 0, 6 * u); ctx.stroke(); break;
      case "raid": // a horseshoe
        ctx.arc(0, -1 * u, 5 * u, Math.PI * 0.85, Math.PI * 2.15); ctx.moveTo(-4.3 * u, 2 * u); ctx.lineTo(-4.3 * u, 6 * u); ctx.moveTo(4.3 * u, 2 * u); ctx.lineTo(4.3 * u, 6 * u); ctx.stroke(); break;
      case "tech": // a scroll
        ctx.rect(-5 * u, -4 * u, 10 * u, 8 * u); ctx.moveTo(-3 * u, -1.5 * u); ctx.lineTo(3 * u, -1.5 * u); ctx.moveTo(-3 * u, 1.5 * u); ctx.lineTo(2 * u, 1.5 * u); ctx.stroke(); break;
      case "brawler": // a shield
        ctx.moveTo(0, -6 * u); ctx.lineTo(5 * u, -4 * u); ctx.lineTo(4 * u, 2 * u); ctx.lineTo(0, 6 * u); ctx.lineTo(-4 * u, 2 * u); ctx.lineTo(-5 * u, -4 * u); ctx.closePath(); ctx.stroke(); break;
      case "all": // scales
        ctx.moveTo(0, -6 * u); ctx.lineTo(0, 6 * u); ctx.moveTo(-6 * u, -3 * u); ctx.lineTo(6 * u, -3 * u); ctx.moveTo(-3 * u, 6 * u); ctx.lineTo(3 * u, 6 * u);
        ctx.moveTo(-6 * u, -3 * u); ctx.lineTo(-7.5 * u, 1.5 * u); ctx.lineTo(-4.5 * u, 1.5 * u); ctx.closePath(); ctx.moveTo(6 * u, -3 * u); ctx.lineTo(4.5 * u, 1.5 * u); ctx.lineTo(7.5 * u, 1.5 * u); ctx.closePath(); ctx.stroke(); break;
      default: // a question
        ctx.arc(0, -2 * u, 3.5 * u, Math.PI, Math.PI * 2.4); ctx.lineTo(0, 3 * u); ctx.moveTo(0, 5.5 * u); ctx.lineTo(0, 5.6 * u); ctx.stroke();
    }
    ctx.restore();
  }

  private playstyle(p: PlaystyleProfile, x: number, y: number, w: number, time: number): number {
    const ctx = ui.ctx;
    const f = p.features;
    const gap = 12;
    // ---- the headline ----
    const heroH = 200;
    ui.panel(x, y, w, heroH);
    const primary = p.primary?.archetype;
    const emX = x + w - 110, emY = y + heroH / 2;
    ctx.fillStyle = "rgba(232,192,96,0.12)";
    ctx.beginPath(); ctx.arc(emX, emY, 70, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = withAlpha(GOLD, 0.6); ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(emX, emY, 70, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * (p.primary?.score ?? 0)); ctx.stroke();
    this.medallion(primary?.id ?? (p.enough ? "all" : "none"), emX, emY, 44, GOLD);
    ui.text(p.primary ? `${Math.round(p.primary.score * 100)}% match` : "", emX, emY + 62, { align: "center", size: 11, color: DIM });
    ui.text("YOUR STYLE", x + 20, y + 26, { size: 11, bold: true, color: DIM });
    fitText(p.title, x + 20, y + 62, w - 260, { size: 32, bold: true, color: p.primary ? GOLD : "#ffe9b0", font: "Georgia, serif" });
    const conf = { none: "not enough games yet", low: "early read", medium: "solid read", high: "confident read" }[p.confidence];
    ui.text(`Based on ${p.games} game${p.games === 1 ? "" : "s"} · ${conf}${this.filter !== "all" ? ` · ${this.filter} only` : ""}`, x + 20, y + 90, { size: 12, color: FAINT });
    this.block(p.description, x + 20, y + 116, w - 260, 13.5, TEXT);
    y += heroH + gap;
    if (!p.enough) return y;

    // ---- keywords: the style in words, built from the numbers ----
    if (p.keywords.length) {
      y = this.heading(x, y, w, "In a few words", "tempo · army · game length · what you stand out at");
      let cx = x, cy = y;
      for (const k of p.keywords) {
        ctx.font = "bold 13px 'Trebuchet MS', sans-serif";
        const cw = ctx.measureText(k).width + 28;
        if (cx + cw > x + w) { cx = x; cy += 36; }
        ctx.fillStyle = "rgba(127,176,232,0.16)";
        ctx.beginPath(); ctx.roundRect(cx, cy, cw, 30, 15); ctx.fill();
        ctx.strokeStyle = "rgba(127,176,232,0.45)"; ctx.lineWidth = 1;
        ctx.beginPath(); ctx.roundRect(cx + 0.5, cy + 0.5, cw - 1, 29, 15); ctx.stroke();
        ui.text(k, cx + 14, cy + 20, { size: 13, bold: true, color: "#cfe2ff" });
        cx += cw + 8;
      }
      y = cy + 44;
    }

    // ---- the verdict: what you do well, what's costing you ----
    if (p.good.length || p.bad.length) {
      y = this.heading(x, y, w, "Your games, honestly", "compared with the opponents you've faced");
      const hw = (w - gap) / 2;
      const measure = (lines: string[]) => lines.reduce((a, l) => a + this.blockHeight(l, hw - 52, 13) + 8, 0);
      const ph = Math.max(80, 40 + Math.max(measure(p.good), measure(p.bad)));
      const side = (sx: number, title: string, lines: string[], color: string, mark: string, empty: string) => {
        ui.panel(sx, y, hw, ph);
        ui.text(title, sx + 16, y + 22, { size: 11, bold: true, color });
        let ly = y + 44;
        if (!lines.length) this.block(empty, sx + 16, ly, hw - 32, 12.5, FAINT);
        for (const l of lines) {
          ui.text(mark, sx + 16, ly + 1, { size: 14, bold: true, color });
          ly += this.block(l, sx + 34, ly, hw - 52, 13, TEXT) + 8;
        }
      };
      side(x, "WHAT YOU DO WELL", p.good, GOOD, "+", "Nothing stands out yet.");
      side(x + hw + gap, "WHAT'S COSTING YOU", p.bad, BAD, "−", "No weak spot shows in your numbers.");
      y += ph + gap;
    }

    // ---- traits ----
    if (p.traits.length) {
      y = this.heading(x, y, w, "Traits", "what else stands out");
      let cx = x, cy = y;
      for (const t of p.traits) {
        ctx.font = "bold 12.5px 'Trebuchet MS', sans-serif";
        const cw = ctx.measureText(t.name).width + 26;
        if (cx + cw > x + w) { cx = x; cy += 34; }
        const hov = ui.hit(cx, cy, cw, 28);
        ctx.fillStyle = hov ? "rgba(232,192,96,0.28)" : "rgba(232,192,96,0.14)";
        ctx.beginPath(); ctx.roundRect(cx, cy, cw, 28, 14); ctx.fill();
        ui.text(t.name, cx + 13, cy + 19, { size: 12.5, bold: true, color: GOLD });
        if (hov) ui.tooltip([t.name, t.detail]);
        cx += cw + 8;
      }
      y = cy + 42;
    }

    // ---- the evidence ----
    if (primary) {
      y = this.heading(x, y, w, `Why you're ${/^[AEIOU]/.test(primary.name) ? "an" : "a"} ${primary.name}`, "the numbers behind it");
      const ev = primary.evidence(f);
      const ew = (w - gap * (ev.length - 1)) / ev.length;
      ev.forEach((e, i) => {
        const ex = x + i * (ew + gap);
        ui.panel(ex, y, ew, 78);
        fitText(e.label, ex + 14, y + 24, ew - 28, { size: 12, color: DIM });
        ui.text(e.value, ex + 14, y + 52, { size: 22, bold: true, color: "#fff0cc", font: "Georgia, serif" });
        ui.bar(ex + ew * 0.45, y + 44, ew * 0.55 - 14, 8, e.weight, e.weight >= 0.7 ? GOOD : e.weight >= 0.35 ? GOLD : FAINT);
      });
      y += 90;
      const cw = (w - gap * 2) / 3;
      const col = (i: number, title: string, lines: string[], color: string) => {
        const cx = x + i * (cw + gap);
        let yy = y + 26;
        ui.text(title, cx + 14, yy, { size: 11, bold: true, color });
        yy += 20;
        for (const l of lines) yy += this.block(`• ${l}`, cx + 14, yy, cw - 28, 12.5, TEXT) + 2;
        return yy - y;
      };
      const measureH = Math.max(90, 50 + Math.max(primary.strengths.length, primary.risks.length) * 36);
      for (let i = 0; i < 3; i++) ui.panel(x + i * (cw + gap), y, cw, measureH);
      col(0, "STRENGTHS", primary.strengths, GOOD);
      col(1, "WATCH OUT FOR", primary.risks, BAD);
      col(2, "TO GET BETTER AT IT", [primary.tip], GOLD);
      y += measureH + gap;
    }

    // ---- how you win ----
    y = this.heading(x, y, w, "How you win", `${f.wins} wins · average ${mmss(f.avgWinSec)}`);
    const half = (w - gap) / 2;
    ui.panel(x, y, half, 170);
    const buckets: [string, number][] = [["< 15 min", f.winsBefore15], ["15–30", f.wins15to30], ["30–40", f.wins30to40], ["40+ min", f.winsAfter40]];
    const bw = (half - 60) / 4;
    buckets.forEach(([label, v], i) => {
      const bx = x + 30 + i * bw, bh = 100 * v;
      ctx.fillStyle = i === 0 ? "#e0786a" : i === 3 ? "#5b8fe0" : GOLD;
      ctx.fillRect(bx + 8, y + 130 - bh, bw - 16, bh);
      ui.text(pct(v), bx + bw / 2, y + 124 - bh, { align: "center", size: 12, bold: true, color: TEXT });
      ui.text(label, bx + bw / 2, y + 150, { align: "center", size: 11, color: DIM });
    });
    ui.text("WHEN YOUR WINS COME", x + 16, y + 20, { size: 10.5, bold: true, color: DIM });
    // What your wins have that your losses don't.
    ui.panel(x + half + gap, y, half, 170);
    ui.text("WHAT YOUR WINS HAVE THAT YOUR LOSSES DON'T", x + half + gap + 16, y + 20, { size: 10.5, bold: true, color: DIM });
    let ky = y + 44;
    if (!p.winKeys.length) this.block("Needs at least three wins and three losses to compare.", x + half + gap + 16, ky, half - 32, 12.5, FAINT);
    for (const k of p.winKeys) {
      ui.text(k.change > 0 ? "▲" : "▼", x + half + gap + 16, ky, { size: 12, color: k.change > 0 ? GOOD : "#7fb0e8" });
      ky += this.block(k.sentence, x + half + gap + 34, ky, half - 52, 12.5, TEXT) + 4;
    }
    y += 182;
    const facts: [string, string][] = [
      ["Median first attack", mmss(f.medianFirstHit)], ["Attack before 7:00", pct(f.rushRate)],
      ["Losses inside 15 min", pct(f.earlyLossShare)], ["Win rate in 30+ min games", f.lateShare ? pct(f.lateWinRate) : "—"],
    ];
    const fw2 = (w - gap * 3) / 4;
    facts.forEach(([k, v], i) => {
      const fx = x + i * (fw2 + gap);
      ui.panel(fx, y, fw2, 56);
      fitText(k, fx + 14, y + 22, fw2 - 28, { size: 11.5, color: DIM });
      ui.text(v, fx + 14, y + 44, { size: 16, bold: true, color: "#fff0cc" });
    });
    y += 68;

    // ---- your army ----
    y = this.heading(x, y, w, "Your army", "what you train, and what you spend on");
    ui.panel(x, y, w, 118);
    const classes: [keyof typeof f.classShare, string, string][] = [["infantry", "Infantry", "#c8a060"], ["archer", "Archers", "#8fd07a"], ["cavalry", "Cavalry", "#7fb0e8"], ["siege", "Siege", "#e0786a"], ["support", "Support", "#c8b8e8"]];
    let sx = x + 16;
    const barW = w - 32;
    for (const [k, , color] of classes) { const v = f.classShare[k]; if (!v) continue; ctx.fillStyle = color; ctx.fillRect(sx, y + 18, barW * v, 18); sx += barW * v; }
    let lx = x + 16;
    for (const [k, label, color] of classes) {
      const v = f.classShare[k]; if (!v) continue;
      ctx.fillStyle = color; ctx.fillRect(lx, y + 46, 10, 10);
      const t = `${label} ${pct(v)}`;
      ui.text(t, lx + 14, y + 55, { size: 12, color: TEXT });
      ctx.font = "12px 'Trebuchet MS', sans-serif";
      lx += ctx.measureText(t).width + 30;
    }
    fitText(`Top unit: ${f.topUnit ? unitName(f.topUnit) : "—"} (${pct(f.topUnitShare)} of your soldiers)  ·  variety: ${f.diversity.toFixed(1)} unit types' worth`, x + 16, y + 80, w - 32, { size: 12.5, color: DIM });
    fitText(`Spending: ${pct(f.spendUnits)} army · ${pct(f.spendBuildings)} buildings · ${pct(f.spendTech)} technology`, x + 16, y + 102, w - 32, { size: 12.5, color: DIM });
    y += 130;

    // ---- you vs them ----
    y = this.heading(x, y, w, "You vs your opponents", "per game, against the average enemy you faced");
    const rows: [string, number, string][] = [
      ["Income", f.gatherRatio, `${Math.round(f.gatherPerMin)} a minute`],
      ["Peak villagers", f.villagerRatio, `${Math.round(f.peakVillagers)}`],
      ["Buildings razed", f.razedRatio, f.razedPerGame.toFixed(1)],
      ["Villagers killed", f.raidRatio, f.raidPerGame.toFixed(1)],
      ["Defence built", f.defenseRatio, f.defensesPerGame.toFixed(1)],
      ["Research", f.upgradeRatio, f.upgradesPerGame.toFixed(1)],
      ["Kills per loss", f.foeKd > 0 ? f.kd / f.foeKd : 1, `${f.kd.toFixed(2)} (them ${f.foeKd.toFixed(2)})`],
    ];
    ui.panel(x, y, w, rows.length * 30 + 20);
    rows.forEach(([label, r, you], i) => {
      const ry = y + 26 + i * 30;
      fitText(label, x + 16, ry, 150, { size: 13, color: TEXT, bold: true });
      const mid = x + w * 0.45, span = w * 0.22;
      ctx.fillStyle = "rgba(255,255,255,0.08)"; ctx.fillRect(mid - span, ry - 10, span * 2, 10);
      ctx.fillStyle = "rgba(255,255,255,0.35)"; ctx.fillRect(mid, ry - 13, 1, 16);
      const lg = Math.max(-1, Math.min(1, Math.log2(Math.max(0.01, r)) / 1.5));
      ctx.fillStyle = lg >= 0 ? GOOD : BAD;
      ctx.fillRect(lg >= 0 ? mid : mid + lg * span, ry - 10, Math.abs(lg) * span, 10);
      ui.text(`${r.toFixed(2)}×`, mid + span + 12, ry, { size: 12.5, bold: true, color: r >= 1.1 ? GOOD : r <= 0.9 ? BAD : GOLD });
      fitText(`you: ${you}`, x + w * 0.76, ry, w * 0.24 - 16, { size: 12, color: DIM });
    });
    y += rows.length * 30 + 32;

    // ---- game by game ----
    y = this.heading(x, y, w, "Game by game", "each match's own style — hover for the rule");
    ui.panel(x, y, w, p.matchMix.length * 30 + 20);
    p.matchMix.forEach((mm, i) => {
      const ry = y + 26 + i * 30;
      const st = MATCH_STYLES[mm.style];
      ui.text(st.name, x + 16, ry, { size: 13, bold: true, color: st.color });
      ui.bar(x + 140, ry - 9, w * 0.4, 10, mm.share, st.color);
      ui.text(`${pct(mm.share)} of games`, x + 150 + w * 0.4, ry, { size: 12, color: TEXT });
      ui.text(`won ${mm.wins} of ${mm.games} (${pct(mm.wins / Math.max(1, mm.games))})`, x + w - 16, ry, { align: "right", size: 12, color: mm.wins / Math.max(1, mm.games) >= 0.55 ? GOOD : mm.wins / Math.max(1, mm.games) <= 0.45 ? BAD : DIM });
      if (ui.hit(x, ry - 16, w, 26)) ui.tooltip([st.name, st.rule]);
    });
    y += p.matchMix.length * 30 + 32;
    if (p.byFaction.length) {
      y = this.heading(x, y, w, "By faction", "how you most often play each one");
      ui.panel(x, y, w, p.byFaction.length * 30 + 20);
      p.byFaction.forEach((b, i) => {
        const ry = y + 26 + i * 30;
        this.dot(x + 22, ry - 4, facColor(b.faction));
        fitText(facName(b.faction), x + 34, ry, 200, { size: 13, bold: true, color: TEXT });
        const st = MATCH_STYLES[b.style];
        ui.text(`${st.name} in ${pct(b.share)} of ${b.games} games`, x + 260, ry, { size: 12.5, color: st.color });
        ui.text(`${pct(b.winRate)} won`, x + w - 16, ry, { align: "right", size: 12.5, color: b.winRate >= 0.55 ? GOOD : b.winRate <= 0.45 ? BAD : DIM });
      });
      y += p.byFaction.length * 30 + 32;
    }

    // ---- every style ----
    y = this.heading(x, y, w, "Every style", "how closely you match each one (35% or more counts)");
    for (const sc of p.scores) {
      const a = sc.archetype;
      const rowH = 64;
      ui.panel(x, y, w, rowH);
      this.medallion(a.id, x + 30, y + rowH / 2, 18, sc === p.primary ? GOLD : DIM);
      fitText(a.name, x + 60, y + 26, 190, { size: 15, bold: true, color: sc === p.primary ? GOLD : TEXT });
      ui.bar(x + 60, y + 38, 170, 7, sc.score, sc.score >= 0.35 ? GOLD : FAINT);
      ui.text(`${Math.round(sc.score * 100)}%`, x + 238, y + 45, { size: 11.5, bold: true, color: sc.score >= 0.35 ? GOLD : FAINT });
      fitText(a.short, x + 290, y + 26, w - 306, { size: 13, color: TEXT });
      fitText(a.criteria, x + 290, y + 46, w - 306, { size: 11.5, color: DIM });
      y += rowH + 6;
    }
    void time; void ramp;
    return y + 6;
  }

  // -------------------------------------------------------------- replays --
  private notice = "";
  private noticeBad = false;
  setNotice(msg: string, bad: boolean) { this.notice = msg; this.noticeBad = bad; }

  /** Open a replay file from the player's computer; it's kept, then played. */
  openReplayFile() {
    void pickTextFile(`${REPLAY_FILE_EXT},.json,application/json`).then((f) => {
      if (!f) return;
      const res = parseReplayFile(f.text);
      if (!res.ok) { this.notice = res.error; this.noticeBad = true; return; }
      saveReplay(res.replay);
      this.notice = res.warning ?? `Opened ${f.name}.`;
      this.noticeBad = !!res.warning;
      this.watch = res.replay;
    });
  }

  private replays(x: number, y: number, w: number): number {
    const list = listReplays();
    y = this.heading(x, y, w, "Replays", `your last ${list.length} match${list.length === 1 ? "" : "es"} — watch any of them with the full caster view`);
    // Opening one from a file (or dropping it onto the game).
    ui.panel(x, y, w, 58);
    if (ui.button("📂  Open a replay file…", x + 14, y + 11, 220, 36, { accent: true, size: 14, tooltip: ["Open a replay file", `A ${REPLAY_FILE_EXT} file downloaded from this game — yours, a friend's, a tournament's.`, "You can also drag the file onto the game."] })) this.openReplayFile();
    fitText(this.notice || `Download any replay below to keep it or share it — anyone with the game can open it and watch it with the caster view. Or drag a ${REPLAY_FILE_EXT} file onto the game.`,
      x + 250, y + 34, w - 266, { size: 12.5, color: this.notice ? (this.noticeBad ? BAD : GOOD) : DIM });
    y += 70;
    if (!list.length) {
      ui.panel(x, y, w, 90);
      ui.text("No replays yet — every match you play or watch is recorded here automatically.", x + w / 2, y + 50, { align: "center", size: 13.5, color: DIM });
      return y + 100;
    }
    for (const r of list) {
      ui.panel(x, y, w, 64);
      const d = new Date(r.savedAt);
      fitText(r.summary.map, x + 16, y + 26, 300, { size: 15, bold: true, color: "#ffe9b0" });
      if (r.imported) ui.text("FROM FILE", x + 16 + Math.min(300, (ui.ctx.measureText(r.summary.map).width || 0) + 10), y + 25, { size: 9.5, bold: true, color: "#7fb0e8" });
      const kind = r.kind === "online" ? "Online" : r.kind === "watch" ? "AI game watched" : "Skirmish";
      fitText(`${kind} · ${r.summary.players} players · ${mmss(r.summary.durationSec)} · ${d.toLocaleDateString()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`, x + 16, y + 46, 330, { size: 12, color: DIM });
      r.summary.factions.forEach((f, i) => this.dot(x + 360 + i * 16, y + 22, facColor(f)));
      fitText(r.names.filter(Boolean).slice(0, 4).join(" · "), x + 360, y + 46, w - 360 - 470, { size: 11.5, color: FAINT });
      fitText(r.summary.result, x + w - 350, y + 36, 110, { align: "right", size: 13, bold: true, color: r.summary.result === "Unfinished" ? DIM : GOLD });
      if (ui.button("▶ Watch", x + w - 330, y + 14, 100, 36, { accent: true, size: 14, tooltip: ["Watch this match", "With the caster view: any player's vision, graphs, the feed, and a timeline you can scrub."] })) this.watch = r;
      if (ui.button("⬇ Download", x + w - 222, y + 14, 106, 36, { size: 13, tooltip: ["Download the replay", `Saves a ${REPLAY_FILE_EXT} file — a few kilobytes. Anyone with the game can open it and watch.`] })) {
        const file = replayFile(r);
        this.notice = downloadText(file.name, file.text) ? `Downloaded ${file.name}` : "Your browser blocked the download.";
        this.noticeBad = false;
      }
      const armed = this.confirmDelete === r.id;
      if (ui.button(armed ? "Sure?" : "Delete", x + w - 108, y + 14, 94, 36, { size: 13, danger: armed })) {
        if (armed) { deleteReplay(r.id); this.confirmDelete = ""; } else this.confirmDelete = r.id;
      }
      y += 72;
    }
    return y;
  }

  // -------------------------------------------------------------- matches --
  private matchList(ms: CareerMatch[], x: number, y: number, w: number): number {
    y = this.heading(x, y, w, "Your matches", `${ms.length} kept · newest first`);
    const list = ms.slice(0, 150);
    const C = { when: x + 16, kind: x + 110, map: x + 240, you: x + 400, vs: x + 560, res: x + w * 0.74, len: x + w * 0.82, kl: x + w * 0.9, age: x + w - 16 };
    let ry = this.table(x, y, w, [["When", C.when], ["Game", C.kind], ["Map", C.map], ["You", C.you], ["Against", C.vs], ["Result", C.res], ["Length", C.len, "right"], ["K : L", C.kl, "right"], ["Age", C.age, "right"]], Math.max(1, list.length));
    for (const m of list) {
      const d = new Date(m.at);
      ui.text(`${d.getDate()}/${d.getMonth() + 1} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`, C.when, ry + 21, { size: 12, color: DIM });
      ui.text(m.kind === "online" ? (m.ranked ? "Ranked" : "Online") : diffName(m.difficulty), C.kind, ry + 21, { size: 12.5, bold: true, color: m.kind === "online" ? "#7fb0e8" : TEXT });
      ui.text(m.format, C.kind + 70, ry + 21, { size: 11, color: FAINT });
      ui.text(m.map, C.map, ry + 21, { size: 12.5, color: TEXT });
      this.dot(C.you + 6, ry + 17, facColor(m.faction));
      ui.text(facName(m.faction).replace(/^The /, ""), C.you + 16, ry + 21, { size: 12.5, color: TEXT });
      if (m.foes.length === 1) {
        this.dot(C.vs + 6, ry + 17, facColor(m.foes[0]));
        ui.text(facName(m.foes[0]).replace(/^The /, ""), C.vs + 16, ry + 21, { size: 12.5, color: DIM });
      } else m.foes.slice(0, 7).forEach((f, i) => this.dot(C.vs + 6 + i * 14, ry + 17, facColor(f)));
      if (m.allies.length) ui.text(`+${m.allies.length} ${m.allies.length === 1 ? "ally" : "allies"}`, C.vs + 120, ry + 21, { size: 11, color: FAINT });
      if (!m.foes.length) ui.text("—", C.vs, ry + 21, { size: 12, color: FAINT });
      ui.text(m.won ? "Victory" : "Defeat", C.res, ry + 21, { size: 13, bold: true, color: m.won ? GOOD : BAD });
      ui.text(mmss(m.durationSec), C.len, ry + 21, { align: "right", size: 12.5, color: TEXT });
      ui.text(`${m.kills} : ${m.losses}`, C.kl, ry + 21, { align: "right", size: 12.5, color: DIM });
      ui.text(AGES[m.age]?.short ?? String(m.age), C.age, ry + 21, { align: "right", size: 12.5, color: DIM });
      if (ui.hit(x, ry, w, 34)) {
        const fav = Object.entries(m.trained).filter(([u]) => u !== "villager").sort((p, q) => q[1] - p[1])[0];
        ui.tooltip([`${m.won ? "Victory" : "Defeat"} on ${m.map} — ${MATCH_STYLES[matchStyle(m)].name}`,
          `${facName(m.faction)}${m.commander ? ` led by ${cmdName(m.commander)}` : ""}${m.oaths.length ? ` · Oaths: ${m.oaths.map(oathName).join(", ")}` : ""}`,
          `Against: ${m.foes.map(facName).join(", ") || "—"}${m.allies.length ? ` · with ${m.allies.map(facName).join(", ")}` : ""}`,
          `Gathered ${num(m.gathered)} · peak army ${m.peakArmy} · razed ${m.razed}`,
          fav ? `Most trained: ${unitName(fav[0])} ×${fav[1]}` : "No soldiers trained"]);
      }
      ry += 34;
    }
    if (!list.length) ui.text("No matches yet.", x + 16, ry + 21, { size: 13, color: FAINT });
    return ry + 20;
  }
}
