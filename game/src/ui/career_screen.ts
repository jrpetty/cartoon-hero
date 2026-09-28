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
import { ReplayRecord, deleteReplay, listReplays } from "../sim/replay";
import {
  Career, CareerFilter, CareerMatch, Tally, avgSecs, avgWinSecs, bestBy, careerFor, favourite, favouriteUnitOf,
  kd, topUnit, winRate, worstBy,
} from "../meta/career";

type Tab = "overview" | "factions" | "maps" | "units" | "matches" | "replays";
const TABS: [Tab, string][] = [["overview", "Overview"], ["factions", "Factions"], ["maps", "Maps & modes"], ["units", "Units"], ["matches", "Matches"], ["replays", "Replays"]];
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
      if (ui.button(label, x0 + i * 140, ty, 132, 32, { accent: this.tab === id, size: 13.5 })) {
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

  private unitArt(id: string, x: number, y: number, s: number, time: number, faction?: string) {
    const def = UNITS[id];
    if (!def) return;
    const e = makeEntity();
    Object.assign(e, { kind: Kind.Unit, type: id, team: Team.Player, x, y, radius: def.radius, hp: def.hp, maxHp: def.hp, facing: -0.5, attackInterval: def.attackInterval, animPhase: time * 0.3, seed: 5 });
    const f = faction ?? factionForUnit(id)?.id;
    if (f) setFactionResolver(() => f);
    const ctx = ui.ctx;
    ctx.save();
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

    // Favourites.
    y = this.heading(x, y, w, "Your favourites", "most played");
    const favF = favourite(c.byFaction), favM = favourite(c.byMap), favU = topUnit(c, "trained"), favC = favourite(c.byCommander), favO = favourite(c.byOath);
    const favs: [string, string, string, string, (cx: number, cy: number) => void][] = [
      ["Faction", facName(favF), favF ? `${c.byFaction[favF].played} games · ${pct(winRate(c.byFaction[favF]))} won` : "", facColor(favF),
        (cx, cy) => { if (favF) this.unitArt(FACTIONS[favF as keyof typeof FACTIONS].replaces.militia ?? FACTIONS[favF as keyof typeof FACTIONS].extra[0] ?? "militia", cx, cy, 2.2, time, favF); }],
      ["Map", favM || "—", favM ? `${c.byMap[favM].played} games · ${pct(winRate(c.byMap[favM]))} won` : "", TEXT, () => {}],
      ["Unit", unitName(favU), favU ? `${num(c.units[favU].trained)} trained · ${num(c.units[favU].lost)} lost` : "", TEXT,
        (cx, cy) => { if (favU) this.unitArt(favU, cx, cy, 2.2, time); }],
      ["Commander", cmdName(favC), favC ? `${c.byCommander[favC].played} games · ${pct(winRate(c.byCommander[favC]))} won` : "skirmish only", COMMANDERS[favC]?.color ?? TEXT, () => {}],
      ["Oath", favO ? oathName(favO) : "—", favO ? `sworn in ${c.byOath[favO].played} games · ${pct(winRate(c.byOath[favO]))} won` : "reach the Banner Age", TEXT, () => {}],
    ];
    const fcols = w >= 1000 ? 5 : 3, fw = (w - gap * (fcols - 1)) / fcols;
    favs.forEach(([label, value, sub, color, art], i) => {
      const fx = x + (i % fcols) * (fw + gap), fy = y + Math.floor(i / fcols) * 106;
      ui.panel(fx, fy, fw, 96);
      ui.text(label.toUpperCase(), fx + 14, fy + 22, { size: 10.5, bold: true, color: DIM });
      ui.text(value, fx + 14, fy + 50, { size: 17, bold: true, color, font: "Georgia, serif" });
      ui.text(sub, fx + 14, fy + 72, { size: 11, color: FAINT });
      art(fx + fw - 34, fy + 66);
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
    for (const [label, cx, align] of head) ui.text(label.toUpperCase(), cx, y + 22, { size: 10.5, bold: true, color: DIM, align: align ?? "left" });
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
      this.unitArt(f.replaces.militia ?? f.extra[0] ?? "militia", x + 30, ry + 24, 1.4, time, id);
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
    const ids = Object.keys(c.units).filter((u) => UNITS[u]).sort((p, q) => c.units[q].trained - c.units[p].trained || c.units[q].killed - c.units[p].killed);
    const killedMost = topUnit(c, "killed", false), lostMost = topUnit(c, "lost");
    const gap = 12, tw = (w - gap * 2) / 3;
    const tops: [string, string, string, string][] = [
      ["Trained most", topUnit(c, "trained"), "trained", "Your go-to soldier."],
      ["Enemy you kill most", killedMost, "killed", "What you spend most of your time cutting down."],
      ["You lose most", lostMost, "lost", "Where your army bleeds."],
    ];
    tops.forEach(([label, id, field, sub], i) => {
      const tx = x + i * (tw + gap);
      ui.panel(tx, y, tw, 110);
      ui.text(label.toUpperCase(), tx + 14, y + 22, { size: 10.5, bold: true, color: DIM });
      ui.text(unitName(id), tx + 14, y + 52, { size: 19, bold: true, color: "#fff0cc", font: "Georgia, serif" });
      ui.text(id ? `${num(c.units[id][field as "trained"])} ${field}` : "—", tx + 14, y + 74, { size: 12.5, color: GOLD });
      ui.text(sub, tx + 14, y + 94, { size: 11, color: FAINT });
      if (id) this.unitArt(id, tx + tw - 44, y + 78, 2.6, time);
    });
    y += 124;
    y = this.heading(x, y, w, "Every unit", "yours trained and lost · the enemy's of that type you killed");
    const maxT = Math.max(1, ...ids.map((u) => c.units[u].trained)), maxK = Math.max(1, ...ids.map((u) => c.units[u].killed));
    const C = { name: x + 64, tr: x + w * 0.3, lost: x + w * 0.55, kill: x + w * 0.72, ratio: x + w - 16 };
    let ry = this.table(x, y, w, [["Unit", C.name], ["Trained", C.tr], ["Lost", C.lost], ["Killed of this type", C.kill], ["Survival", C.ratio, "right"]], Math.max(1, ids.length));
    if (!ids.length) ui.text("No units yet.", C.name, ry + 21, { size: 13, color: FAINT });
    for (const u of ids) {
      const v = c.units[u];
      this.unitArt(u, x + 32, ry + 26, 1.35, time);
      ui.text(unitName(u), C.name, ry + 21, { size: 13.5, bold: true, color: TEXT });
      ui.bar(C.tr, ry + 14, w * 0.16, 8, v.trained / maxT, GOLD);
      ui.text(num(v.trained), C.tr + w * 0.16 + 8, ry + 21, { size: 12.5, color: TEXT });
      ui.text(num(v.lost), C.lost, ry + 21, { size: 12.5, color: v.lost ? BAD : FAINT });
      ui.bar(C.kill, ry + 14, w * 0.14, 8, v.killed / maxK, GOOD);
      ui.text(num(v.killed), C.kill + w * 0.14 + 8, ry + 21, { size: 12.5, color: TEXT });
      ui.text(v.trained ? pct(Math.max(0, 1 - v.lost / v.trained)) : "—", C.ratio, ry + 21, { align: "right", size: 12.5, color: DIM });
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

  // -------------------------------------------------------------- replays --
  private replays(x: number, y: number, w: number): number {
    const list = listReplays();
    y = this.heading(x, y, w, "Replays", `your last ${list.length} match${list.length === 1 ? "" : "es"} — watch any of them with the full caster view`);
    if (!list.length) {
      ui.panel(x, y, w, 90);
      ui.text("No replays yet — every match you play or watch is recorded here automatically.", x + w / 2, y + 50, { align: "center", size: 13.5, color: DIM });
      return y + 100;
    }
    for (const r of list) {
      ui.panel(x, y, w, 64);
      const d = new Date(r.savedAt);
      ui.text(r.summary.map, x + 16, y + 26, { size: 15, bold: true, color: "#ffe9b0" });
      const kind = r.kind === "online" ? "Online" : r.kind === "watch" ? "AI game you watched" : "Skirmish";
      ui.text(`${kind} · ${r.summary.players} players · ${mmss(r.summary.durationSec)} · ${d.toLocaleDateString()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`, x + 16, y + 46, { size: 12, color: DIM });
      r.summary.factions.forEach((f, i) => this.dot(x + 360 + i * 16, y + 22, facColor(f)));
      ui.text(r.names.filter(Boolean).slice(0, 4).join(" · "), x + 360, y + 46, { size: 11.5, color: FAINT });
      ui.text(r.summary.result, x + w - 250, y + 36, { align: "right", size: 13, bold: true, color: r.summary.result === "Unfinished" ? DIM : GOLD });
      if (ui.button("▶ Watch", x + w - 230, y + 14, 110, 36, { accent: true, size: 14, tooltip: ["Watch this match", "With the caster view: any player's vision, graphs, the feed, and a timeline you can scrub."] })) this.watch = r;
      const armed = this.confirmDelete === r.id;
      if (ui.button(armed ? "Sure?" : "Delete", x + w - 110, y + 14, 94, 36, { size: 13, danger: armed })) {
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
        ui.tooltip([`${m.won ? "Victory" : "Defeat"} on ${m.map}`,
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
