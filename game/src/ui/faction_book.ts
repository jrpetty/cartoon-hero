// The Factions book: an encyclopedia of the six peoples, and where they are
// unlocked.
//
// Two ways in. From the main menu (or a locked card on the Skirmish screen) it
// is a place to read about each faction and buy one for FACTION_PRICE renown.
// On a player's very first launch it is the choice itself: one faction is
// theirs free, and they cannot leave until they have picked it — so the page
// has to tell them enough to pick well, not just list numbers.

import { ui } from "./ui";
import { audio } from "../engine/audio";
import { PAL, shade, withAlpha } from "../render/palette";
import { drawBuilding, drawUnit, setFactionResolver } from "../render/draw";
import { makeEntity } from "../sim/world";
import { BuildState, Kind, Team } from "../sim/types";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { TILE } from "../content/balance";
import { FACTIONS, FACTION_IDS, FactionDef, FactionId } from "../content/factions";
import { AGES } from "../content/tech";
import { FACTION_PRICE, Profile } from "../meta/profile";

export type FactionBookAction = "back" | null;

const DIFFICULTY = ["", "Forgiving", "Needs a plan", "Punishing"];
const CURVE = { early: "Strongest early", mid: "Strongest mid-game", late: "Strongest late", steady: "Steady throughout" };

/** Word-wrap `text` into `maxW`, drawing it; returns the height used. */
function wrap(text: string, x: number, y: number, maxW: number, size: number, color: string, bold = false): number {
  const ctx = ui.ctx;
  ctx.font = `${bold ? "bold " : ""}${size}px 'Trebuchet MS', sans-serif`;
  let line = "";
  let yy = y;
  for (const w of text.split(" ")) {
    const test = line ? line + " " + w : w;
    if (ctx.measureText(test).width > maxW && line) {
      ui.text(line, x, yy, { size, color, bold });
      line = w;
      yy += size + 5;
    } else line = test;
  }
  if (line) ui.text(line, x, yy, { size, color, bold });
  return yy - y + size + 5;
}

export class FactionBook {
  selected: FactionId = FACTION_IDS[0];
  private scroll = 0;
  private contentH = 0;
  private flash = "";
  private flashT = 0;

  /** Open on a particular faction (e.g. a locked card that was clicked). */
  focus(id: string | undefined) {
    if (id && id in FACTIONS) this.selected = id as FactionId;
    this.scroll = 0;
  }

  draw(W: number, H: number, time: number, profile: Profile, dt = 1 / 60): FactionBookAction {
    const ctx = ui.ctx;
    const first = profile.needsFirstFaction;
    this.flashT = Math.max(0, this.flashT - dt);

    // Backdrop: dark parchment with a warm light from above.
    const bg = ctx.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, "#241b10");
    bg.addColorStop(1, "#110c06");
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, W, H);
    const glow = ctx.createRadialGradient(W / 2, -H * 0.2, 10, W / 2, -H * 0.2, H * 1.1);
    glow.addColorStop(0, "rgba(255,200,120,0.14)");
    glow.addColorStop(1, "rgba(255,200,120,0)");
    ctx.fillStyle = glow;
    ctx.fillRect(0, 0, W, H);

    const outer = Math.min(W - 48, 1320);
    const x0 = Math.round(W / 2 - outer / 2);
    const narrow = W < 1000;
    const listW = narrow ? outer : 300;

    // Header.
    ui.text(first ? "Choose your first faction" : "The Factions", x0, 50, { size: 32, bold: true, color: "#ffe9b0", font: "Georgia, serif" });
    ui.text(first
      ? `One is yours, free. The others cost ${FACTION_PRICE} ✦ renown each — earned in battle, the same renown that opens War Chests.`
      : `Six peoples from six eras. Read how each plays, then unlock the ones that suit you for ${FACTION_PRICE} ✦ each.`,
    x0 + 2, 76, { size: 13, color: "#c9bea3" });
    ui.text(`${profile.data.renown} ✦`, x0 + outer, 50, { align: "right", size: 22, bold: true, color: "#e8c060" });
    ui.text(`renown · ${profile.ownedFactions().length} of ${FACTION_IDS.length} owned`, x0 + outer, 70, { align: "right", size: 11.5, color: "#a89f88" });

    const top = 96;
    const FOOT = 72;

    // ---- the six, as a list (or a row of chips when narrow) ----
    if (narrow) {
      const cw = (outer - 5 * 6) / 6;
      FACTION_IDS.forEach((id, i) => {
        const f = FACTIONS[id];
        if (ui.button(f.name.replace(/^The /, ""), x0 + i * (cw + 6), top, cw, 34, { accent: this.selected === id, size: 11.5 })) this.pick(id);
      });
    } else {
      FACTION_IDS.forEach((id, i) => this.listCard(FACTIONS[id], x0, top + i * 86, listW, 78, profile, first, time));
    }

    // ---- the selected faction's page, scrollable ----
    const px = narrow ? x0 : x0 + listW + 20;
    const pw = narrow ? outer : outer - listW - 20;
    const py = narrow ? top + 44 : top;
    const viewH = H - py - FOOT;
    const maxScroll = Math.max(0, this.contentH - viewH);
    if (ui.wheel && ui.mx > px && ui.my > py && ui.my < py + viewH) this.scroll = Math.max(0, Math.min(maxScroll, this.scroll + ui.wheel * 0.6));
    this.scroll = Math.min(this.scroll, maxScroll);
    ui.pushScroll(this.scroll, { x: px - 4, y: py, w: pw + 8, h: viewH });
    const end = this.page(FACTIONS[this.selected], px, py, pw, profile, time);
    this.contentH = end - py + 12;
    ui.popScroll();
    ui.scrollbar(px + pw + 6, py, viewH, this.scroll, this.contentH);

    // ---- action bar ----
    const fy = H - FOOT;
    ctx.fillStyle = "rgba(10,7,3,0.92)";
    ctx.fillRect(0, fy, W, FOOT);
    ctx.fillStyle = withAlpha(PAL.uiAccent, 0.3);
    ctx.fillRect(0, fy, W, 1);
    const by = fy + 14;
    let action: FactionBookAction = null;
    if (!first && ui.button("⟵  Back", x0, by, 130, 44, { size: 15 })) action = "back";
    const f = FACTIONS[this.selected];
    const owned = profile.ownsFaction(f.id);
    const bw = 300, bx = x0 + outer - bw;
    if (first) {
      if (ui.button(`Take ${f.name} — free`, bx, by, bw, 44, { accent: true, size: 16 })) {
        profile.chooseFirstFaction(f.id);
        audio.play("levelup");
        action = "back";
      }
      ui.text("You can unlock the others later with renown.", bx - 16, by + 27, { align: "right", size: 12, color: "#a89f88" });
    } else if (owned) {
      const playing = profile.playableFaction() === f.id;
      if (ui.button(playing ? `✓ Playing as ${f.name}` : `Play as ${f.name}`, bx, by, bw, 44, { accent: !playing, size: 15, disabled: playing })) {
        profile.selectFaction(f.id);
        this.say(`You'll play as ${f.name}.`);
        audio.play("ui");
      }
    } else {
      const short = FACTION_PRICE - profile.data.renown;
      if (ui.button(`🔓  Unlock for ${FACTION_PRICE} ✦`, bx, by, bw, 44, { accent: short <= 0, size: 16, disabled: short > 0,
        tooltip: short > 0 ? ["Not enough renown yet", `You need ${short} more ✦. Every battle earns renown — wins and harder AIs earn more.`] : [`Unlock ${f.name}`, `Spend ${FACTION_PRICE} ✦. It's yours for good — Skirmish and online.`] })) {
        if (profile.unlockFaction(f.id)) {
          profile.selectFaction(f.id);
          this.say(`${f.name} unlocked!`);
          audio.play("levelup");
        }
      }
      if (short > 0) ui.text(`${short} ✦ more to unlock`, bx - 16, by + 27, { align: "right", size: 12.5, color: "#e0a070" });
    }
    if (this.flashT > 0) ui.text(this.flash, W / 2, by + 27, { align: "center", size: 14, bold: true, color: "#9fd08a" });
    return action;
  }

  private say(msg: string) { this.flash = msg; this.flashT = 3; }

  private pick(id: FactionId) {
    if (this.selected !== id) { this.selected = id; this.scroll = 0; audio.play("ui"); }
  }

  private listCard(f: FactionDef, x: number, y: number, w: number, h: number, profile: Profile, first: boolean, time: number) {
    const ctx = ui.ctx;
    const sel = this.selected === f.id;
    if (ui.button("", x, y, w, h, { accent: sel, tooltip: [f.name, f.era, f.tagline] })) this.pick(f.id);
    ctx.fillStyle = f.color;
    ctx.fillRect(x + 1, y + 8, 4, h - 16);
    this.soldier(f, x + 36, y + h * 0.62, 1.7, time);
    ui.text(f.name, x + 70, y + 26, { size: 16, bold: true, color: sel ? "#fff0cc" : f.color, font: "Georgia, serif" });
    ui.text(f.era, x + 70, y + 44, { size: 11.5, color: "#c9bea3" });
    ui.text(CURVE[f.curve], x + 70, y + 62, { size: 11, color: "#8f8770" });
    const owned = profile.ownsFaction(f.id);
    const tag = first ? "FREE" : owned ? "OWNED ✓" : `🔒 ${FACTION_PRICE} ✦`;
    ui.text(tag, x + w - 12, y + 24, { align: "right", size: 10.5, bold: true, color: first ? "#9fd08a" : owned ? "#9fd08a" : "#e8c060" });
    if (!owned && !first) {
      ctx.fillStyle = "rgba(0,0,0,0.18)";
      ctx.fillRect(x + 6, y + 2, w - 8, h - 4);
    }
  }

  /** One faction's page. Returns the y it ends at. */
  private page(f: FactionDef, x: number, y: number, w: number, profile: Profile, time: number): number {
    const ctx = ui.ctx;
    const g = f.guide;

    // Hero panel: name and story on the left, a diorama of its town and army on the right.
    const heroH = 190;
    ui.panel(x, y, w, heroH);
    const dioW = Math.min(360, w * 0.42);
    const dx = x + w - dioW - 10;
    const dg = ctx.createLinearGradient(0, y + 10, 0, y + heroH - 10);
    dg.addColorStop(0, shade(PAL.grass, 0.08));
    dg.addColorStop(1, shade(PAL.grassDark, -0.1));
    ctx.fillStyle = dg;
    ctx.beginPath();
    ctx.roundRect(dx, y + 10, dioW, heroH - 20, 8);
    ctx.fill();
    ctx.save();
    ctx.beginPath();
    ctx.roundRect(dx, y + 10, dioW, heroH - 20, 8);
    ctx.clip();
    this.townCentre(f, dx + dioW * 0.34, y + heroH * 0.62, time);
    this.soldier(f, dx + dioW * 0.72, y + heroH * 0.74, 2.4, time);
    const own = [...Object.values(f.replaces), ...f.extra];
    if (own[0]) this.unit(f, own[0], dx + dioW * 0.88, y + heroH * 0.66, 2.1, time);
    ctx.restore();
    const tw = w - dioW - 44;
    ui.text(f.name, x + 22, y + 44, { size: 32, bold: true, color: f.color, font: "Georgia, serif" });
    ui.text(f.era, x + 24, y + 68, { size: 14, color: "#d8cdb4" });
    let ty = y + 96;
    ty += wrap(`“${f.tagline}”`, x + 24, ty, tw, 14, "#e9dcc0");
    // Difficulty and power curve.
    const my = y + heroH - 44;
    ui.text("DIFFICULTY", x + 24, my, { size: 10, bold: true, color: "#a89f88" });
    for (let i = 0; i < 3; i++) {
      ctx.fillStyle = i < g.difficulty ? "#e8c060" : "rgba(255,255,255,0.12)";
      ctx.beginPath(); ctx.arc(x + 30 + i * 16, my + 16, 5.5, 0, Math.PI * 2); ctx.fill();
    }
    ui.text(DIFFICULTY[g.difficulty], x + 80, my + 20, { size: 12.5, bold: true, color: "#e9dcc0" });
    const cx = x + 200;
    ui.text("POWER THROUGH THE MATCH", cx, my, { size: 10, bold: true, color: "#a89f88" });
    ["Early", "Mid", "Late"].forEach((lab, i) => {
      const bx = cx + i * 62;
      for (let k = 0; k < 5; k++) {
        ctx.fillStyle = k < g.power[i] ? f.color : "rgba(255,255,255,0.1)";
        ctx.fillRect(bx + k * 9, my + 10, 7, 12);
      }
      ui.text(lab, bx, my + 36, { size: 10.5, color: "#bdb49a" });
    });
    y += heroH + 14;

    // How it plays.
    const section = (title: string) => {
      ui.text(title.toUpperCase(), x + 4, y + 14, { size: 11, bold: true, color: PAL.uiAccent });
      ctx.fillStyle = withAlpha(PAL.uiAccent, 0.25);
      ctx.fillRect(x + 4, y + 22, w - 8, 1);
      y += 34;
    };
    section("How it plays");
    y += wrap(g.playstyle, x + 4, y, w - 8, 14, "#e2d6ba") + 8;

    // Strengths and price side by side; then who it suits and how to win.
    const colW = (w - 20) / 2;
    const list = (items: string[], lx: number, ly: number, mark: string, color: string) => {
      let yy = ly;
      for (const it of items) {
        ui.text(mark, lx, yy, { size: 13, bold: true, color });
        yy += wrap(it, lx + 20, yy, colW - 26, 13, "#e2d6ba") + 2;
      }
      return yy;
    };
    section("Bonuses and their price");
    const a = list(f.strengths, x + 4, y, "✓", "#8fd07a");
    const b = list(f.weaknesses, x + 4 + colW + 20, y, "✗", "#e0786a");
    y = Math.max(a, b) + 8;
    section("Pick it if you…");
    const c = list(g.suits.slice(0, Math.ceil(g.suits.length / 2)), x + 4, y, "•", f.color);
    const d = list(g.suits.slice(Math.ceil(g.suits.length / 2)), x + 4 + colW + 20, y, "•", f.color);
    y = Math.max(c, d) + 8;

    // Its own units, drawn, with their numbers.
    section("Its own soldiers");
    const units = [...Object.entries(f.replaces).map(([base, u]) => ({ id: u, note: `Replaces the ${UNITS[base]?.name ?? base}` })),
      ...f.extra.map((u) => ({ id: u, note: `Extra, at the ${BUILDINGS[UNITS[u].trainedAt]?.name ?? "Barracks"}` }))];
    const uw = Math.min(300, (w - 12 * (units.length - 1)) / Math.max(1, units.length));
    units.forEach((u, i) => {
      const def = UNITS[u.id];
      if (!def) return;
      const ux = x + i * (uw + 12);
      ui.panel(ux, y, uw, 150);
      this.unit(f, u.id, ux + 44, y + 92, 2.6, time);
      ui.text(def.name, ux + 88, y + 26, { size: 15, bold: true, color: "#ffe9b0" });
      ui.text(u.note, ux + 88, y + 44, { size: 11, color: "#a89f88" });
      ui.text(`${AGES[def.age ?? 0]?.name ?? ""}`, ux + 88, y + 60, { size: 11, color: "#a89f88" });
      const cost = Object.entries(def.cost).filter(([, v]) => v).map(([k, v]) => `${v}${k[0]}`).join(" ");
      const stat = (label: string, v: string, sy: number) => {
        ui.text(label, ux + 88, sy, { size: 12, color: "#bdb49a" });
        ui.text(v, ux + uw - 12, sy, { align: "right", size: 12, bold: true, color: "#e9dcc0" });
      };
      stat("HP", String(def.hp), y + 82);
      stat("Attack", String(def.attack), y + 99);
      stat("Range", def.ranged ? String(def.range) : "melee", y + 116);
      stat("Cost", cost, y + 133);
    });
    y += 150 + 12;

    section("How to win with it");
    for (const t of g.tips) {
      ui.text("›", x + 4, y, { size: 14, bold: true, color: "#e8c060" });
      y += wrap(t, x + 22, y, w - 30, 13.5, "#e2d6ba") + 3;
    }
    y += 6;
    section("What gives it trouble");
    y += wrap(g.struggles, x + 4, y, w - 8, 13.5, "#e0b8a8") + 6;
    void profile;
    return y;
  }

  private withFaction(f: FactionDef, fn: () => void) {
    setFactionResolver(() => f.id);
    try { fn(); } catch { /* a picture is never worth a crash */ } finally { setFactionResolver(null); }
  }

  private scaled(x: number, y: number, s: number, fn: () => void) {
    const ctx = ui.ctx;
    ctx.save();
    ctx.translate(x, y);
    ctx.scale(s, s);
    ctx.translate(-x, -y);
    fn();
    ctx.restore();
  }

  private unit(f: FactionDef, id: string, x: number, y: number, s: number, time: number) {
    const def = UNITS[id];
    if (!def) return;
    const e = makeEntity();
    Object.assign(e, { kind: Kind.Unit, type: id, team: Team.Player, x, y, radius: def.radius, hp: def.hp, maxHp: def.hp,
      facing: -Math.PI / 6, attackInterval: def.attackInterval, animPhase: time, seed: 11 });
    this.withFaction(f, () => this.scaled(x, y, s, () => drawUnit(ui.ctx, e, time)));
  }

  /** Its line infantryman: the faction's own if it replaces the Man-at-Arms. */
  private soldier(f: FactionDef, x: number, y: number, s: number, time: number) {
    const id = f.replaces.militia ?? (f.extra.find((u) => UNITS[u]?.trainedAt === "barracks") ?? "militia");
    this.unit(f, id, x, y, s, time);
  }

  private townCentre(f: FactionDef, x: number, y: number, time: number) {
    const def = BUILDINGS.town_center;
    const e = makeEntity();
    Object.assign(e, { kind: Kind.Building, type: "town_center", team: Team.Player, x, y, radius: (def.tiles * TILE) / 2,
      hp: def.hp, maxHp: def.hp, buildState: BuildState.Done, buildProgress: 1 });
    this.withFaction(f, () => this.scaled(x, y, 1.15, () => drawBuilding(ui.ctx, e, time, Team.Player)));
  }
}
