// Out-of-match screens: main menu, skirmish setup, armory (chests, collection,
// loadout), chest-opening reveal, and the post-match report.

import { FACTION_PRICE, Profile } from "../meta/profile";
import { CHESTS, ChestDef, rollChest, RollResult } from "../meta/chests";
import { RARITIES, rarityByIndex } from "../meta/rarity";
import { CATALOG, COLLECTIBLE_UNIT_IDS, variantKey, VARIANT_BY_KEY } from "../meta/catalog";
import { MatchRewards, levelFromXp } from "../meta/progression";
import type { EarnedAward } from "../meta/achievements";
import { SaveGame, listSaves } from "../sim/savegame";
import { UNITS } from "../content/units";
import { PRESETS } from "../maps/generator";
import { GameMode } from "../sim/types";
import { DIFFICULTIES, DIFFICULTY_IDS } from "../ai/difficulty";
import { COMMANDERS, COMMANDER_IDS, commanderPerks } from "../content/commanders";
import { BOONS, BOONS_BY_ID, BOON_CATEGORIES, BoonCategory, BOON_IDS } from "../content/boons";
import { rollBoonCache, boonKey, BOON_CACHE_COST, BoonRoll } from "../meta/boon_cache";
import { PAL, shade, teamColor, withAlpha } from "../render/palette";
import { ui } from "./ui";
import { RNG, randomSeed } from "../engine/rng";
import { audio } from "../engine/audio";
import { Particles } from "../engine/particles";
import { MatchReport } from "../sim/metrics";
import { CustomMap, listCustomMaps, mapPool } from "../maps/custom";
import { drawMapThumbnail } from "./map_thumb";
import { iconArmory, iconFactions, iconCodex, iconMap, iconMultiplayer, iconResume, iconSettings, iconSkirmish, iconWarband } from "./menu_icons";
import { REPORT_TABS, ReportTab, drawReportKey, drawReportTab, reportSubtitle } from "./match_report";
import { TEAM_COLORS, blockTeams, coopTeams, formatLabel, freeForAll, resizeTeams, teamsValid } from "./teams";
import { FACTIONS, FACTION_IDS, DEFAULT_FACTION, factionOf } from "../content/factions";
import { drawBuilding, drawUnit, setFactionResolver } from "../render/draw";
import { makeEntity } from "../sim/world";
import { BUILDINGS } from "../content/buildings";
import { Kind, Team } from "../sim/types";

export interface SkirmishConfig {
  presetId: string;
  seed: number;
  difficulty: string; // default AI personality, applied to any unset bot slot
  aiDifficulties: string[]; // per-bot personality by team index (1..players-1)
  fairMode: boolean;
  players: number; // 2 = 1v1, 4 = FFA or 2v2
  /**
   * Each seat's team, seat 0 being you: 0 fights alone, seats sharing 1..8
   * are allies (ui/teams.ts). Replaces the old "allied" switch, which split
   * seats by parity.
   */
  teams: number[];
  commander: string; // selected commander id
  /** Your faction, and each bot's by team index ("" = a seeded random pick). */
  faction: string;
  aiFactions: string[];
  nomad: boolean; // no starting Town Center; villagers scattered on the map
  mode: GameMode; // conquest / survival / koth / regicide
}

// ------------------------------------------------------------- background --

/** Painterly menu backdrop: dusk sky, hills, castle silhouette. */
export function drawMenuBackground(W: number, H: number, time: number) {
  const ctx = ui.ctx;
  const sky = ctx.createLinearGradient(0, 0, 0, H);
  sky.addColorStop(0, "#2c2440");
  sky.addColorStop(0.55, "#7a4a58");
  sky.addColorStop(0.8, "#c8784a");
  sky.addColorStop(1, "#e8a05a");
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, W, H);

  // The first stars, fading out toward the glow.
  for (let i = 0; i < 70; i++) {
    const sx = ((i * 7919) % 1000) / 1000 * W;
    const sy = ((i * 104729) % 1000) / 1000 * H * 0.45;
    const tw = 0.35 + 0.35 * Math.sin(time * 1.3 + i * 2.1);
    ctx.fillStyle = withAlpha("#fff4dc", tw * (1 - sy / (H * 0.45)));
    ctx.fillRect(sx, sy, i % 5 === 0 ? 2 : 1.2, i % 5 === 0 ? 2 : 1.2);
  }
  // Long thin clouds catching the last light, drifting.
  for (let i = 0; i < 4; i++) {
    const cyy = H * (0.3 + i * 0.07);
    const cxx = ((i * 0.37 + time * 0.004 * (i + 1)) % 1.4 - 0.2) * W;
    const cg = ctx.createLinearGradient(cxx - 220, 0, cxx + 220, 0);
    cg.addColorStop(0, "rgba(255,190,150,0)");
    cg.addColorStop(0.5, `rgba(255,190,150,${0.16 - i * 0.02})`);
    cg.addColorStop(1, "rgba(255,190,150,0)");
    ctx.fillStyle = cg;
    ctx.beginPath();
    ctx.ellipse(cxx, cyy, 220 + i * 40, 7 + i * 2, 0, 0, Math.PI * 2);
    ctx.fill();
  }

  // sun
  ctx.fillStyle = withAlpha("#ffd9a0", 0.9);
  ctx.beginPath();
  ctx.arc(W * 0.72, H * 0.62, 46, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = withAlpha("#ffd9a0", 0.25);
  ctx.beginPath();
  ctx.arc(W * 0.72, H * 0.62, 78, 0, Math.PI * 2);
  ctx.fill();

  // distant hills
  ctx.fillStyle = "#4a3a52";
  ctx.beginPath();
  ctx.moveTo(0, H * 0.78);
  for (let x = 0; x <= W; x += 40) {
    ctx.lineTo(x, H * 0.78 - Math.sin(x * 0.004 + 2) * 36 - Math.sin(x * 0.013) * 14);
  }
  ctx.lineTo(W, H);
  ctx.lineTo(0, H);
  ctx.fill();

  // nearer hills with a ragged treeline
  ctx.fillStyle = "#3a2e40";
  ctx.beginPath();
  ctx.moveTo(0, H * 0.84);
  for (let x = 0; x <= W; x += 14) {
    const hy = H * 0.84 - Math.sin(x * 0.006 + 0.7) * 22 - Math.sin(x * 0.021) * 6;
    ctx.lineTo(x, hy - ((x * 37) % 11 > 6 ? 8 + ((x * 13) % 7) : 0));
  }
  ctx.lineTo(W, H);
  ctx.lineTo(0, H);
  ctx.fill();

  // castle silhouette
  ctx.fillStyle = "#241c2c";
  const cx = W * 0.2;
  const base = H * 0.82;
  ctx.fillRect(cx - 90, base - 110, 180, 110);
  for (const tx of [-90, 90]) {
    ctx.fillRect(cx + tx - 22, base - 170, 44, 170);
    for (let i = 0; i < 3; i++) ctx.fillRect(cx + tx - 22 + i * 17, base - 184, 10, 16);
  }
  ctx.fillRect(cx - 14, base - 220, 28, 220);
  for (let i = 0; i < 2; i++) ctx.fillRect(cx - 14 + i * 19, base - 234, 9, 16);
  // lit windows
  ctx.fillStyle = withAlpha("#ffc870", 0.75 + 0.2 * Math.sin(time * 3.1));
  for (const [wx, wy] of [[-60, -80], [-30, -80], [30, -80], [60, -80], [-90, -130], [90, -130], [0, -170]]) {
    ctx.fillRect(cx + wx - 3, base + wy, 6, 10);
  }
  // banner waving from the keep
  const wave = Math.sin(time * 2.2) * 5;
  ctx.fillStyle = "#b8483e";
  ctx.beginPath();
  ctx.moveTo(cx + 14, base - 230);
  ctx.quadraticCurveTo(cx + 40, base - 226 + wave * 0.4, cx + 58, base - 220 + wave);
  ctx.lineTo(cx + 52, base - 208 + wave * 0.7);
  ctx.quadraticCurveTo(cx + 34, base - 212, cx + 14, base - 212);
  ctx.closePath();
  ctx.fill();

  // foreground field
  ctx.fillStyle = "#2c3420";
  ctx.beginPath();
  ctx.moveTo(0, H * 0.92);
  for (let x = 0; x <= W; x += 30) {
    ctx.lineTo(x, H * 0.92 - Math.sin(x * 0.01 + 9) * 10);
  }
  ctx.lineTo(W, H);
  ctx.lineTo(0, H);
  ctx.fill();

  // A soft vignette pulls the eye to the middle.
  const vg = ctx.createRadialGradient(W / 2, H * 0.45, Math.min(W, H) * 0.3, W / 2, H * 0.45, Math.max(W, H) * 0.75);
  vg.addColorStop(0, "rgba(0,0,0,0)");
  vg.addColorStop(1, "rgba(10,6,14,0.45)");
  ctx.fillStyle = vg;
  ctx.fillRect(0, 0, W, H);
}

// ------------------------------------------------------------------- menu --

export class MenuScreen {
  /** A save the player picked from the continue strip, consumed by the app. */
  pickedSave: SaveGame | null = null;

  draw(W: number, H: number, time: number, profile: Profile): "skirmish" | "multiplayer" | "warband" | "armory" | "codex" | "settings" | "editor" | "resume" | "factions" | null {
    drawMenuBackground(W, H, time);
    const ctx = ui.ctx;

    // First-launch (or freshly-recruited) commander reveal overlay.
    const reveal = COMMANDERS[profile.data.commanderReveal];
    if (reveal) {
      ctx.fillStyle = "rgba(8,6,3,0.78)";
      ctx.fillRect(0, 0, W, H);
      ui.text("⚑ A Commander Joins Your Banner ⚑", W / 2, H * 0.26, {
        align: "center", size: 26, bold: true, color: "#ffe9b0", font: "Georgia, serif",
      });
      const pw = 460;
      const px = W / 2 - pw / 2;
      const py = H * 0.32;
      ui.panel(px, py, pw, 200, { light: true });
      ui.text(reveal.name, W / 2, py + 40, { align: "center", size: 30, bold: true, color: reveal.color, font: "Georgia, serif" });
      ui.text(reveal.title, W / 2, py + 66, { align: "center", size: 16, color: "#d8cdb4" });
      wrapText(reveal.desc, px + 30, py + 96, pw - 60, 14, "#bdb49a");
      let ry = py + 150;
      for (const perk of commanderPerks(reveal)) {
        ui.text("• " + perk, W / 2, ry, { align: "center", size: 13, color: PAL.uiAccent });
        ry += 18;
      }
      if (ui.button("Claim", W / 2 - 90, py + 216, 180, 46, { accent: true, size: 18 })) {
        profile.clearCommanderReveal();
        audio.play("levelup");
      }
      return null;
    }

    type MenuAction = "skirmish" | "multiplayer" | "warband" | "armory" | "codex" | "settings" | "editor" | "resume" | "factions";
    let action: MenuAction | null = null;
    // First launch, once the commander is claimed: choose the free faction
    // before anything else. The book won't let you leave without one.
    if (profile.needsFirstFaction) return "factions";

    // ---- title ----
    const narrow = W < 980;
    const titleY = Math.max(66, Math.min(H * 0.14, 124));
    const tsize = narrow ? 46 : 64;
    ctx.save();
    ctx.textAlign = "center";
    ctx.font = `bold ${tsize}px Georgia, 'Times New Roman', serif`;
    ctx.fillStyle = "rgba(0,0,0,0.5)";
    ctx.fillText("Banner & Blade", W / 2 + 3, titleY + 3);
    const grad = ctx.createLinearGradient(0, titleY - tsize * 0.65, 0, titleY + 10);
    grad.addColorStop(0, "#fff2c8");
    grad.addColorStop(0.55, "#ffd98a");
    grad.addColorStop(1, "#c8923a");
    ctx.fillStyle = grad;
    ctx.fillText("Banner & Blade", W / 2, titleY);
    // A thin gold rule with a diamond, under the title.
    const rw = Math.min(360, W * 0.3);
    const ry = titleY + 18;
    const rule = ctx.createLinearGradient(W / 2 - rw, 0, W / 2 + rw, 0);
    rule.addColorStop(0, "rgba(232,192,96,0)"); rule.addColorStop(0.5, "rgba(232,192,96,0.9)"); rule.addColorStop(1, "rgba(232,192,96,0)");
    ctx.fillStyle = rule;
    ctx.fillRect(W / 2 - rw, ry, rw * 2, 1.5);
    ctx.beginPath(); ctx.moveTo(W / 2, ry - 5); ctx.lineTo(W / 2 + 5, ry + 0.75); ctx.lineTo(W / 2, ry + 6.5); ctx.lineTo(W / 2 - 5, ry + 0.75); ctx.closePath();
    ctx.fillStyle = "#e8c060"; ctx.fill();
    ctx.font = "italic 16px Georgia, serif";
    ctx.fillStyle = withAlpha("#f3e9d2", 0.85);
    ctx.fillText("Raise your banner. Sharpen your blade. Take the field.", W / 2, ry + 26);
    ctx.restore();

    // ---- layout: your realm on the left, where to go on the right ----
    const outer = Math.min(W - (narrow ? 32 : 64), 1120);
    const x0 = Math.round(W / 2 - outer / 2);
    // Sit the block a little below the title when there is room to spare.
    const blockH = 380;
    const top = Math.round(ry + 46 + Math.max(0, (H - (ry + 46) - blockH - 90) * 0.3));
    const gap = 14;
    const leftW = narrow ? 0 : 320;
    const rx = narrow ? x0 : x0 + leftW + 20;
    const rw2 = narrow ? outer : outer - leftW - 20;
    const saves = listSaves();
    const info = profile.levelInfo();
    const stats = profile.data.stats;

    if (!narrow) {
      let ly = top;
      // Profile.
      ui.panel(x0, ly, leftW, 112);
      ui.text(profile.data.name, x0 + 18, ly + 30, { size: 20, bold: true, color: "#ffe9b0", font: "Georgia, serif" });
      ui.text(`Level ${info.level}`, x0 + leftW - 18, ly + 29, { align: "right", size: 13, bold: true, color: PAL.uiAccent });
      ui.bar(x0 + 18, ly + 44, leftW - 36, 8, info.into / info.need, PAL.uiAccent);
      ui.text(`${info.into} / ${info.need} XP`, x0 + 18, ly + 68, { size: 11.5, color: "#bdb49a" });
      ui.text(`${profile.data.renown} ✦ renown`, x0 + leftW - 18, ly + 68, { align: "right", size: 11.5, bold: true, color: "#e8c060" });
      ui.text(`${stats.played} battles · ${stats.wins} victories · best streak ${stats.bestStreak}`, x0 + 18, ly + 94, { size: 11.5, color: "#a89f88" });
      ly += 112 + gap;

      // Your realm: the faction and commander you will take into Skirmish.
      const fac = factionOf(profile.playableFaction());
      const cmdr = COMMANDERS[profile.data.commander];
      if (ui.button("", x0, ly, leftW, 124, { tooltip: ["Your realm", `${profile.ownedFactions().length} of ${FACTION_IDS.length} factions owned`, "Open the Factions book — read about each, and unlock more."] })) action = "factions";
      ctx.fillStyle = fac.color;
      ctx.fillRect(x0 + 1, ly + 10, 4, 104);
      ui.text("YOUR REALM", x0 + 18, ly + 22, { size: 10.5, bold: true, color: "#a89f88" });
      ui.text(fac.name, x0 + 18, ly + 48, { size: 21, bold: true, color: fac.color, font: "Georgia, serif" });
      ui.text(fac.era, x0 + 18, ly + 67, { size: 12, color: "#d8cdb4" });
      ui.text(cmdr ? `Led by ${cmdr.name}, ${cmdr.title}` : "No commander", x0 + 18, ly + 90, { size: 12, color: cmdr?.color ?? "#a89f88" });
      ui.text(`${profile.ownedFactions().length}/${FACTION_IDS.length} owned · `, x0 + 18, ly + 110, { size: 11, color: "#8f8770" });
      ui.text(`Strongest ${fac.curve === "early" ? "early" : fac.curve === "mid" ? "mid-game" : fac.curve === "late" ? "late" : "throughout"}`, x0 + 92, ly + 110, { size: 11, color: "#8f8770" });
      this.drawRealmSoldier(x0 + leftW - 46, ly + 96, time, profile.playableFaction());
      ly += 124 + gap;

      // Continue: the latest save, and the others as chips.
      if (saves.length) {
        const latest = saves[0];
        const mm = Math.floor(latest.summary.elapsed / 60);
        const ss = String(Math.floor(latest.summary.elapsed % 60)).padStart(2, "0");
        if (ui.button("", x0, ly, leftW, 70, {
          tooltip: ["Continue your last save", `${latest.summary.mode} · ${latest.summary.players} players · ${latest.summary.difficulty}`,
            "Loading replays the match from its opening order, so it takes a moment."],
        })) { this.pickedSave = latest; action = "resume"; }
        iconResume(ctx, x0 + 34, ly + 35, 44);
        ui.text("Continue", x0 + 64, ly + 28, { size: 16, bold: true, color: "#ffe9b0" });
        ui.text(`${latest.summary.mapName} · ${mm}:${ss}`, x0 + 64, ly + 48, { size: 12, color: "#bdb49a" });
        ly += 70 + 6;
        if (saves.length > 1) {
          const n = Math.min(3, saves.length - 1);
          const cw = (leftW - (n - 1) * 4) / n;
          saves.slice(1, 1 + n).forEach((sv, i) => {
            const sm = Math.floor(sv.summary.elapsed / 60);
            if (ui.button(`${sv.summary.mapName.slice(0, 10)} · ${sm}m`, x0 + i * (cw + 4), ly, cw, 26, {
              size: 10.5, tooltip: [sv.label, `${sv.summary.mode} · ${sv.summary.players} players`],
            })) { this.pickedSave = sv; action = "resume"; }
          });
        }
      }
    }

    // ---- tiles ----
    let ty = top;
    if (narrow) {
      // One compact line for the profile, then the tiles.
      ui.panel(x0, ty, outer, 46);
      ui.text(`${profile.data.name} · Level ${info.level}`, x0 + 14, ty + 28, { size: 14, bold: true, color: "#ffe9b0" });
      ui.bar(x0 + outer * 0.5, ty + 20, outer * 0.5 - 14, 7, info.into / info.need, PAL.uiAccent);
      ty += 46 + gap;
      if (saves.length) {
        const latest = saves[0];
        if (ui.button(`Continue — ${latest.summary.mapName}`, x0, ty, outer, 38, { size: 14 })) { this.pickedSave = latest; action = "resume"; }
        ty += 38 + gap;
      }
    }
    const pool = listCustomMaps().filter((m) => m.published).length;
    const bigH = narrow ? 104 : 150;
    if (this.tile(rx, ty, rw2, bigH, "Skirmish", "Battle the AI — two to eight realms, any teams, any battlefield.",
      pool ? `${PRESETS.length} battlefields + ${pool} published map${pool === 1 ? "" : "s"}` : `${PRESETS.length} battlefields · ${FACTION_IDS.length} factions · 4 ages`,
      (cx, cy, sz) => iconSkirmish(ctx, cx, cy, sz, "#b8483e"), { accent: true, big: true })) action = "skirmish";
    ty += bigH + gap;
    const midH = narrow ? 88 : 108;
    const hw = (rw2 - gap) / 2;
    if (this.tile(rx, ty, hw, midH, "Multiplayer", "Online, up to 8 v 8 — or a quick 1 v 1.", "",
      (cx, cy, sz) => iconMultiplayer(ctx, cx, cy, sz, time))) action = "multiplayer";
    if (this.tile(rx + hw + gap, ty, hw, midH, "Warband Tactics", "Draft, merge and outlast.", "",
      (cx, cy, sz) => iconWarband(ctx, cx, cy, sz))) action = "warband";
    ty += midH + gap;
    const smH = narrow ? 80 : 96;
    const qw = (rw2 - gap * 4) / 5;
    const small: [MenuAction, string, string, (cx: number, cy: number, sz: number) => void][] = [
      ["factions", "Factions", `${profile.ownedFactions().length}/${FACTION_IDS.length} owned · read about each, unlock more`, (cx, cy, sz) => iconFactions(ctx, cx, cy, sz)],
      ["armory", "Armory", `${profile.data.renown} ✦ · War Chests & boons`, (cx, cy, sz) => iconArmory(ctx, cx, cy, sz)],
      ["editor", "Map Editor", "Make a map, publish it", (cx, cy, sz) => iconMap(ctx, cx, cy, sz)],
      ["codex", "Codex", "Units, ages, factions", (cx, cy, sz) => iconCodex(ctx, cx, cy, sz)],
      ["settings", "Settings", "Sound, display, keys", (cx, cy, sz) => iconSettings(ctx, cx, cy, sz)],
    ];
    small.forEach(([id, label, sub, icon], i) => {
      const x = rx + i * (qw + gap);
      if (ui.button("", x, ty, qw, smH, { tooltip: [label, sub] })) action = id;
      icon(x + qw / 2, ty + smH * 0.38, Math.min(smH * 0.5, 50));
      ui.text(label, x + qw / 2, ty + smH - 16, { align: "center", size: narrow ? 12.5 : 14, bold: true, color: PAL.uiParchment });
    });

    ui.text("Banner & Blade", W - 16, H - 14, { align: "right", size: 10.5, color: withAlpha("#f3e9d2", 0.35) });
    return action;
  }

  /** A menu tile: a drawn icon on the left, a title and a line or two. */
  private tile(x: number, y: number, w: number, h: number, title: string, sub: string, foot: string,
    icon: (cx: number, cy: number, size: number) => void, o: { accent?: boolean; big?: boolean } = {}): boolean {
    const clicked = ui.button("", x, y, w, h, { accent: o.accent, tooltip: [title, sub] });
    const isz = Math.min(h * (o.big ? 0.62 : 0.58), o.big ? 96 : 60);
    const pad = o.big ? 26 : 16;
    icon(x + pad + isz / 2, y + h / 2, isz);
    const tx = x + pad * 2 + isz;
    const tsize = o.big ? (h > 120 ? 30 : 24) : 17;
    const lines = foot ? 3 : 2;
    const block = tsize + 8 + (lines - 1) * 18;
    let cy = y + h / 2 - block / 2 + tsize * 0.8;
    ui.text(title, tx, cy, { size: tsize, bold: true, color: o.accent ? "#fff0cc" : "#ffe9b0", font: "Georgia, serif" });
    cy += Math.round(tsize * 0.35) + 16;
    wrapText(sub, tx, cy, w - (tx - x) - 14, o.big ? 13.5 : 12, "#d8cdb4");
    if (foot) ui.text(foot, tx, cy + 22, { size: 11.5, bold: true, color: "#e8c060" });
    return clicked;
  }

  /** The soldier of your faction standing beside the realm card. */
  private drawRealmSoldier(x: number, y: number, time: number, faction: string | undefined) {
    const e = makeEntity();
    const f = factionOf(faction);
    const soldier = f.replaces.militia ?? (f.extra[0] && UNITS[f.extra[0]]?.trainedAt === "barracks" ? f.extra[0] : "militia");
    Object.assign(e, { kind: Kind.Unit, type: soldier, team: 0, x, y, radius: 10, hp: 1, maxHp: 1, facing: Math.PI, seed: 7 });
    setFactionResolver(() => f.id);
    const ctx = ui.ctx;
    ctx.save();
    ctx.translate(x, y);
    ctx.scale(2.2, 2.2);
    ctx.translate(-x, -y);
    try { drawUnit(ctx, e, time, 0 as Team); } catch { /* a preview is never worth a crash */ }
    ctx.restore();
    setFactionResolver(null);
  }
}

// Track mouse-down state for sliders (set from main each frame).
let mouseDown = false;
export function setMouseDown(d: boolean) {
  mouseDown = d;
}
export function isMouseDown() {
  return mouseDown;
}

// ------------------------------------------------------------------ setup --

export class SetupScreen {
  config: SkirmishConfig = {
    presetId: "open_plains",
    seed: randomSeed(),
    difficulty: "knight",
    aiDifficulties: [],
    fairMode: false,
    players: 2,
    teams: [0, 0],
    commander: "",
    faction: "",
    aiFactions: [],
    nomad: false,
    mode: "conquest",
  };

  /** How far the setup panels are scrolled, and how tall they were last frame. */
  private scroll = 0;
  private contentH = 0;
  /**
   * Which team preset the roster follows, so adding or removing a seat keeps
   * the shape ("2 teams" stays two teams). Editing a seat by hand makes it
   * "custom", and resizing then leaves the teams alone.
   */
  private teamPreset: "ffa" | 2 | 3 | 4 | "coop" | "custom" = "ffa";
  private previews: Record<string, { tc: ReturnType<typeof makeEntity>; unit: ReturnType<typeof makeEntity> }> = {};

  /**
   * The Skirmish setup, in two organised columns: the battle on the left
   * (where, how, and the options), the people in it on the right (a roster
   * with a row per seat — colour, faction, difficulty, team — and your own
   * realm under it). One scroll region, and an action bar pinned at the
   * bottom so "To Battle!" is always in reach.
   */
  draw(W: number, H: number, time: number, profile: Profile): "start" | "spectate" | "back" | "factions" | null {
    drawMenuBackground(W, H, time);
    const ctx = ui.ctx;
    ctx.fillStyle = "rgba(10, 8, 4, 0.35)";
    ctx.fillRect(0, 0, W, H);
    this.syncTeams();

    const FOOTER = 76;
    const top = 104;
    const viewH = Math.max(120, H - top - FOOTER);
    const wide = W >= 1180;
    const outer = Math.min(W - 64, 1440);
    const x0 = Math.round(W / 2 - outer / 2);
    const gap = 20;
    const leftW = wide ? Math.round(outer * 0.42) : outer;
    const rightW = wide ? outer - leftW - gap : outer;
    const rx = wide ? x0 + leftW + gap : x0;

    // Header: title on the left, the match at a glance on the right.
    ui.text("Skirmish", x0, 56, { size: 36, bold: true, color: "#ffe9b0", font: "Georgia, serif" });
    ui.text("Set the field, choose your sides, take the field.", x0 + 2, 84, { size: 13, color: "#c9bea3" });
    const preset = PRESETS.find((pp) => pp.id === this.config.presetId);
    const mapName = preset?.name ?? (this.config.presetId === "random" ? "Random battlefield" : listCustomMaps().find((m) => m.id === this.config.presetId)?.name ?? "");
    ui.text(`${mapName}  ·  ${formatLabel(this.config.teams)}  ·  ${this.modeName()}`, x0 + outer, 60, {
      align: "right", size: 15, bold: true, color: "#e2c889",
    });

    const maxScroll = Math.max(0, this.contentH - viewH);
    if (ui.wheel && ui.my > top && ui.my < top + viewH) {
      this.scroll = Math.max(0, Math.min(maxScroll, this.scroll + ui.wheel * 0.6));
    }
    this.scroll = Math.min(this.scroll, maxScroll);
    ui.pushScroll(this.scroll, { x: 0, y: top, w: W, h: viewH });
    const y0 = top + 6;

    const leftEnd = this.drawBattleColumn(x0, y0, leftW);
    let rightTop = wide ? y0 : leftEnd + 16;
    rightTop = this.drawRoster(rx, rightTop, rightW, profile);
    const rightEnd = this.drawFactionPanel(rx, rightTop + 16, rightW, profile);
    this.contentH = Math.max(leftEnd, rightEnd) - top + 16;
    ui.popScroll();
    ui.scrollbar(x0 + outer + 8, top, viewH, this.scroll, this.contentH);

    // ---- pinned action bar ----
    const fy = H - FOOTER;
    const fg = ctx.createLinearGradient(0, fy, 0, H);
    fg.addColorStop(0, "rgba(14, 10, 6, 0.9)");
    fg.addColorStop(1, "rgba(8, 6, 3, 0.96)");
    ctx.fillStyle = fg;
    ctx.fillRect(0, fy, W, FOOTER);
    ctx.fillStyle = withAlpha(PAL.uiAccent, 0.3);
    ctx.fillRect(0, fy, W, 1);
    const by2 = fy + 16;
    let action: "start" | "spectate" | "back" | "factions" | null = this.openBook ? "factions" : null;
    this.openBook = false;
    if (ui.button("⟵  Back", x0, by2, 130, 44, { size: 15 })) action = "back";
    const valid = teamsValid(this.config.teams);
    if (!valid) ui.text("Everyone is on one team — split them into at least two sides.", W / 2, by2 + 22, { align: "center", size: 13, color: PAL.uiBad });
    else if (this.contentH > viewH) ui.text("scroll for more", x0 + 146, by2 + 22, { size: 11, color: "#8f8770" });
    if (ui.button("👁  Watch", x0 + outer - 360, by2, 130, 44, { size: 15, disabled: !valid, tooltip: ["Spectate an AI vs AI battle", "Every seat is played by the AI — sit back and watch."] })) action = "spectate";
    if (ui.button("⚔  To Battle!", x0 + outer - 220, by2, 220, 44, { accent: true, size: 18, disabled: !valid })) action = "start";
    return action;
  }

  private modeName(): string {
    return { conquest: "Conquest", survival: "Survival", koth: "King of the Hill", regicide: "Regicide" }[this.config.mode] ?? "Conquest";
  }

  /** Keep the per-seat arrays in step with the player count and mode. */
  private syncTeams() {
    const n = this.config.players;
    if (!this.config.teams || this.config.teams.length !== n) {
      this.config.teams = this.teamPreset === "custom" ? resizeTeams(this.config.teams ?? [], n) : this.presetTeams(n);
    }
    // Survival is everyone together against the waves.
    if (this.config.mode === "survival") this.config.teams = Array.from({ length: n }, () => 1);
  }

  private presetTeams(n: number): number[] {
    const p = this.teamPreset;
    if (p === "ffa" || p === "custom") return freeForAll(n);
    if (p === "coop") return coopTeams(n, Math.max(0, Math.floor(n / 2) - 1));
    return blockTeams(n, p);
  }

  /** A section heading with a gold rule, used down both columns. */
  private heading(x: number, y: number, w: number, text: string, sub?: string) {
    const ctx = ui.ctx;
    ui.text(text, x, y, { size: 17, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });
    ctx.font = `bold 17px Georgia, serif`;
    const tw = ctx.measureText(text).width;
    if (sub) {
      ui.text(sub, x + tw + 12, y + 1, { size: 12, color: "#9b927c" });
      ctx.font = `12px 'Trebuchet MS', sans-serif`;
    }
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.22);
    ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(x, y + 14); ctx.lineTo(x + w, y + 14); ctx.stroke();
  }

  // ------------------------------------------------------------ the battle --

  private drawBattleColumn(x: number, y: number, w: number): number {
    const ctx = ui.ctx;
    // Battlefield: a two-across grid of map cards. Published maps sit alongside
    // the presets, filtered to the ones that allow this match; drafts stay in
    // the editor until their author publishes them.
    const custom = mapPool(this.config.mode, this.config.players);
    const drafts = listCustomMaps().filter((m) => !m.published).length;
    const cards = [
      ...PRESETS.map((pp) => ({ id: pp.id, name: pp.name, desc: pp.desc, map: null as CustomMap | null })),
      { id: "random", name: "Random", desc: custom.length ? `Any battlefield here, published maps included — rolled from the seed.` : "A different preset every match, rolled from the seed.", map: null as CustomMap | null },
      ...custom.map((m) => ({
        id: m.id, name: m.name,
        desc: m.desc?.trim() || `${m.cols}×${m.rows} · ${m.minPlayers}–${m.maxPlayers} players${m.nomad === "forced" ? " · always nomad" : ""}`,
        map: m,
      })),
    ];
    if (this.config.presetId.startsWith("custom_") && !custom.some((m) => m.id === this.config.presetId)) {
      this.config.presetId = "open_plains";
    }
    const perRow = w >= 420 ? 2 : 1;
    const cardH = 70;
    const cgap = 10;
    const rows = Math.ceil(cards.length / perRow);
    const panelH = 52 + rows * (cardH + cgap) + (drafts ? 22 : 0);
    ui.panel(x, y, w, panelH);
    this.heading(x + 18, y + 24, w - 36, "Battlefield");
    if (drafts) {
      ui.text(`${drafts} draft map${drafts === 1 ? "" : "s"} in the Map Editor — publish ${drafts === 1 ? "it" : "them"} to play here.`,
        x + 18, y + panelH - 16, { size: 11.5, color: "#a89f88" });
    }
    const cardW = (w - 36 - (perRow - 1) * cgap) / perRow;
    cards.forEach((c, i) => {
      const cx = x + 18 + (i % perRow) * (cardW + cgap);
      const cy = y + 46 + Math.floor(i / perRow) * (cardH + cgap);
      const sel = this.config.presetId === c.id;
      if (ui.button("", cx, cy, cardW, cardH, { accent: sel, tooltip: [c.name, c.desc] })) { this.config.presetId = c.id; audio.play("ui"); }
      const thumb = c.map ? cardH - 16 : 0;
      if (c.map) drawMapThumbnail(ctx, cx + 8, cy + 8, thumb, c.map, { spawns: true, resources: true });
      const tx = cx + 12 + (thumb ? thumb + 6 : 0);
      ui.text(c.name, tx, cy + 18, { size: 14.5, bold: true, color: sel ? "#ffe9b0" : PAL.uiParchment });
      if (c.map) ui.text(c.map.author ? `BY ${c.map.author.slice(0, 12).toUpperCase()}` : "PUBLISHED", cx + cardW - 10, cy + 18, { align: "right", size: 9.5, bold: true, color: "#7fb0e8" });
      wrapText(c.desc, tx, cy + 38, cardW - (tx - cx) - 10, 11.5, "#b8ad92");
    });
    y += panelH + 16;

    // Mode.
    ui.panel(x, y, w, 96);
    this.heading(x + 18, y + 24, w - 36, "Mode");
    const modes: [GameMode, string, string][] = [
      ["conquest", "Conquest", "Destroy every enemy. The classic skirmish."],
      ["survival", "Survival", "Co-op: every seat together against escalating waves."],
      ["koth", "King of the Hill", "Hold the centre for 5 cumulative minutes to win."],
      ["regicide", "Regicide", "Each side has a King — slay theirs, protect yours."],
    ];
    const mw = (w - 36 - 3 * 8) / 4;
    modes.forEach(([id, label, hint], i) => {
      if (ui.button(mw < 110 && label.length > 10 ? "KotH" : label, x + 18 + i * (mw + 8), y + 46, mw, 34, {
        accent: this.config.mode === id, size: 13, tooltip: [label, hint],
      })) { this.config.mode = id; audio.play("ui"); }
    });
    y += 96 + 16;

    // Options: seed, AI default, ranked, nomad.
    const optH = 214;
    ui.panel(x, y, w, optH);
    this.heading(x + 18, y + 24, w - 36, "Options");
    ui.text("Seed", x + 18, y + 58, { size: 13, bold: true, color: "#d8cdb4" });
    ui.text(String(this.config.seed), x + 110, y + 58, { size: 13.5, color: "#e9dcc0" });
    if (ui.button("New seed", x + w - 18 - 110, y + 45, 110, 26, { size: 12 })) { this.config.seed = randomSeed(); audio.play("ui"); }
    ui.text("Every AI", x + 18, y + 96, { size: 13, bold: true, color: "#d8cdb4" });
    const dw = (w - 36 - 92 - (DIFFICULTY_IDS.length - 1) * 6) / DIFFICULTY_IDS.length;
    DIFFICULTY_IDS.forEach((id, i) => {
      const d = DIFFICULTIES[id];
      if (ui.button(d.name, x + 110 + i * (dw + 6), y + 82, dw, 28, { accent: this.config.difficulty === id, size: 12, tooltip: [d.name, d.desc, "Sets every AI seat; change one seat in the roster."] })) {
        this.config.difficulty = id;
        this.config.aiDifficulties = [];
        audio.play("ui");
      }
    });
    const toggle = (ty: number, on: boolean, title: string, sub: string, flip: () => void) => {
      if (ui.button(on ? "✓" : "", x + 18, ty, 28, 28, { accent: on, size: 15 })) { flip(); audio.play("ui"); }
      ui.text(title, x + 58, ty + 9, { size: 13.5, bold: true, color: PAL.uiParchment });
      ui.text(sub, x + 58, ty + 25, { size: 11.5, color: "#a89f88" });
    };
    toggle(y + 124, this.config.fairMode, "Ranked", "All units Common, +25% rewards — your unboxed variants sit out.", () => (this.config.fairMode = !this.config.fairMode));
    toggle(y + 166, this.config.nomad, "Nomad start", "No Town Centre — settle wherever you land.", () => (this.config.nomad = !this.config.nomad));
    return y + optH;
  }

  // ------------------------------------------------------------- the roster --

  /**
   * One row per seat: its colour, who plays it, their faction, their skill,
   * and their team — every team picked here rather than implied. Presets fill
   * the teams in organised blocks; any seat can then be moved.
   */
  private drawRoster(x: number, y: number, w: number, profile: Profile): number {
    const ctx = ui.ctx;
    const n = this.config.players;
    const survival = this.config.mode === "survival";
    const rowH = 46;
    const panelH = 124 + n * (rowH + 6) + 8;
    ui.panel(x, y, w, panelH);
    this.heading(x + 18, y + 24, w - 36, "Players", formatLabel(this.config.teams));

    // Seat count.
    const cx = x + w - 18;
    if (ui.button("+", cx - 30, y + 10, 30, 28, { size: 16, disabled: n >= 8, tooltip: ["Add a seat"] })) { this.config.players = n + 1; audio.play("ui"); }
    ui.text(`${n}`, cx - 48, y + 24, { align: "center", size: 16, bold: true, color: "#ffe9b0" });
    if (ui.button("–", cx - 96, y + 10, 30, 28, { size: 16, disabled: n <= 2, tooltip: ["Remove a seat"] })) { this.config.players = n - 1; audio.play("ui"); }

    // Team presets.
    const presets: [typeof this.teamPreset, string, string][] = [
      ["ffa", "Free-for-all", "Every seat for itself."],
      [2, "2 teams", "Seats split into two blocks: 1–4 against 5–8, and so on."],
      [3, "3 teams", "Three blocks, as even as the seats allow."],
      [4, "4 teams", "Four blocks — two-a-side four ways with 8 seats."],
      ["coop", "Co-op vs AI", "You and the first half of the seats against the rest."],
    ];
    const pw = (w - 36 - (presets.length - 1) * 6) / presets.length;
    presets.forEach(([id, label, hint], i) => {
      const can = !survival && (typeof id !== "number" || id <= n) && !(id === "coop" && n < 3);
      if (ui.button(label, x + 18 + i * (pw + 6), y + 46, pw, 28, { accent: this.teamPreset === id, size: 12, disabled: !can, tooltip: [label, hint] })) {
        this.teamPreset = id;
        this.config.teams = this.presetTeams(n);
        audio.play("ui");
      }
    });

    // Column heads.
    const cols = { seat: x + 18, name: x + 50, faction: x + w * 0.36, skill: x + w * 0.53, team: x + w * 0.68 };
    const hy = y + 96;
    for (const [label, cx2] of [["PLAYER", cols.name], ["FACTION", cols.faction], ["SKILL", cols.skill], ["TEAM", cols.team]] as const) {
      ui.text(label, cx2, hy, { size: 10.5, bold: true, color: "#9b927c" });
    }

    const teamColors = TEAM_COLORS;
    for (let t = 0; t < n; t++) {
      const ry = y + 110 + t * (rowH + 6);
      const me = t === 0;
      // Row ground; yours picked out, and a stripe in its team's colour so the
      // sides read at a glance down the list.
      ctx.fillStyle = me ? withAlpha(PAL.uiAccent, 0.14) : t % 2 ? "rgba(255,255,255,0.035)" : "rgba(255,255,255,0.015)";
      ctx.beginPath(); ctx.roundRect(x + 12, ry, w - 24, rowH, 6); ctx.fill();
      if (this.config.teams[t] > 0) {
        ctx.fillStyle = teamColors[this.config.teams[t]];
        ctx.beginPath(); ctx.roundRect(x + 12, ry, 4, rowH, [6, 0, 0, 6]); ctx.fill();
      }
      // Seat colour.
      const col = teamColor(t);
      ctx.fillStyle = col.main;
      ctx.beginPath(); ctx.roundRect(cols.seat, ry + 11, 22, 24, 5); ctx.fill();
      ui.text(String(t + 1), cols.seat + 11, ry + 23.5, { align: "center", size: 12, bold: true, color: "#fff" });
      // Who.
      if (me) {
        const cmdr = COMMANDERS[this.config.commander || profile.data.commander];
        ui.text("You", cols.name, ry + 16, { size: 14, bold: true, color: "#ffe9b0" });
        ui.text(cmdr ? `${cmdr.name}, ${cmdr.title}` : "Your realm", cols.name, ry + 33, { size: 11, color: "#a89f88" });
      } else {
        ui.text(`AI ${t + 1}`, cols.name, ry + 16, { size: 14, bold: true, color: PAL.uiParchment });
        ui.text(col.name, cols.name, ry + 33, { size: 11, color: withAlpha(col.light, 0.9) });
      }
      // Faction: yours is chosen in the panel below; a bot's cycles here.
      const fw = cols.skill - cols.faction - 10;
      if (me) {
        const f = factionOf(this.config.faction || profile.data.faction);
        ui.text(f.name.replace(/^The /, ""), cols.faction + 6, ry + rowH / 2, { size: 13, bold: true, color: f.color });
      } else {
        const cur = this.config.aiFactions[t] ?? "";
        const f = cur ? FACTIONS[cur as keyof typeof FACTIONS] : null;
        if (ui.button(f ? f.name.replace(/^The /, "") : "Random", cols.faction, ry + 9, fw, 28, {
          size: 12, accent: !!f, tooltip: [`Seat ${t + 1}'s faction`, "Click to cycle. Random is drawn from the match seed."],
        })) {
          const order = ["", ...FACTION_IDS];
          while (this.config.aiFactions.length <= t) this.config.aiFactions.push("");
          this.config.aiFactions[t] = order[(order.indexOf(cur) + 1) % order.length];
          audio.play("ui");
        }
      }
      // Skill: a bot's personality.
      const sw = cols.team - cols.skill - 10;
      if (!me) {
        const did = this.config.aiDifficulties[t] ?? this.config.difficulty;
        const d = DIFFICULTIES[did];
        if (ui.button(d.name, cols.skill, ry + 9, sw, 28, { size: 12, accent: did !== this.config.difficulty, tooltip: [`Seat ${t + 1}: ${d.name}`, d.desc, "Click to cycle."] })) {
          const next = DIFFICULTY_IDS[(DIFFICULTY_IDS.indexOf(did) + 1) % DIFFICULTY_IDS.length];
          while (this.config.aiDifficulties.length <= t) this.config.aiDifficulties.push(this.config.difficulty);
          this.config.aiDifficulties[t] = next;
          audio.play("ui");
        }
      } else {
        ui.text("—", cols.skill + sw / 2, ry + rowH / 2, { align: "center", size: 13, color: "#6f6a5c" });
      }
      // Team: "–" alone, or 1..k with allies. Four to start with, and one more
      // than the highest in use — so up to as many teams as there are seats,
      // without nine buttons on every row.
      const used = Math.max(0, ...this.config.teams);
      const tmax = Math.min(n, Math.max(4, used + 1), 8);
      const tw = Math.max(20, Math.min(30, (x + w - 20 - cols.team) / (tmax + 1) - 3));
      for (let k = 0; k <= tmax; k++) {
        const bx = cols.team + k * (tw + 3);
        const on = this.config.teams[t] === k;
        const tcol = teamColors[k];
        if (ui.button(k === 0 ? "–" : String(k), bx, ry + 10, tw, 26, {
          size: 12, accent: on, disabled: survival,
          tooltip: k === 0 ? ["Alone", "This seat fights for itself."] : [`Team ${k}`, "Seats on the same team are allies — shared vision, no friendly fire."],
        })) {
          this.config.teams[t] = k;
          this.teamPreset = "custom";
          audio.play("ui");
        }
        if (on && k > 0) {
          ctx.fillStyle = tcol;
          ctx.fillRect(bx + 3, ry + 32, tw - 6, 3);
        }
      }
    }
    if (survival) {
      ui.text("Survival: every seat stands together against the waves.", x + 18, y + panelH - 10, { size: 11.5, color: "#a89f88" });
    }
    return y + panelH;
  }

  /**
   * Your realm: six faction cards, each with the faction's own Town Centre
   * and signature soldier drawn live in its style, then what it is good at,
   * what it pays for it, and your commander.
   */
  /** The faction a locked card asked about, for the Factions book to open on. */
  bookFocus = "";
  private openBook = false;

  private drawFactionPanel(x0: number, y: number, colW: number, profile: Profile): number {
    // Only a faction you own can be yours.
    if (!this.config.faction || !profile.ownsFaction(this.config.faction)) this.config.faction = profile.playableFaction() ?? DEFAULT_FACTION;
    if (!profile.ownsCommander(this.config.commander)) {
      this.config.commander = profile.data.commander || profile.data.commanders[0] || "";
    }
    const f = factionOf(this.config.faction);
    const cardGap = 8;
    const cardW = Math.floor((colW - 36 - cardGap * (FACTION_IDS.length - 1)) / FACTION_IDS.length);
    const cardH = 124;
    const panelH = 58 + cardH + 176;
    ui.panel(x0, y, colW, panelH);
    this.heading(x0 + 18, y + 24, colW - 36, "Your realm", "faction and commander");
    const ctx = ui.ctx;
    const CURVE = { early: "Strong early", mid: "Peaks mid-game", late: "Strong late", steady: "Steady" } as const;
    FACTION_IDS.forEach((id, i) => {
      const d = FACTIONS[id];
      const cx = x0 + 18 + i * (cardW + cardGap);
      const cy = y + 46;
      const sel = this.config.faction === id;
      const locked = !profile.ownsFaction(id);
      if (ui.button("", cx, cy, cardW, cardH, { accent: sel, tooltip: locked
        ? [d.name, `Locked — ${FACTION_PRICE} ✦ renown to unlock`, "Click to read about it in the Factions book."]
        : [d.name, d.era, d.tagline] })) {
        if (locked) { this.bookFocus = id; this.openBook = true; }
        else {
          this.config.faction = id;
          profile.selectFaction(id);
          this.config.commander = profile.data.commander;
        }
        audio.play("ui");
      }
      if (sel) {
        ctx.strokeStyle = d.color; ctx.lineWidth = 2.5;
        ctx.beginPath(); ctx.roundRect(cx + 1, cy + 1, cardW - 2, cardH - 2, 6); ctx.stroke();
      }
      const pv = this.previews[id] ?? (this.previews[id] = { tc: makeEntity(), unit: makeEntity() });
      const tcDef = BUILDINGS.town_center;
      Object.assign(pv.tc, { kind: Kind.Building, type: "town_center", team: Team.Player, x: 0, y: 0, radius: (tcDef.tiles * 32) / 2, hp: tcDef.hp, maxHp: tcDef.hp, buildState: 0, buildProgress: 1 });
      const soldier = Object.values(d.replaces)[0] ?? d.extra[0] ?? "militia";
      const ud = UNITS[soldier];
      Object.assign(pv.unit, { kind: Kind.Unit, type: soldier, team: Team.Player, x: 0, y: 0, radius: ud.radius, facing: -0.5, hp: ud.hp, maxHp: ud.hp, animPhase: 0.3, attackInterval: ud.attackInterval });
      setFactionResolver(() => id);
      ctx.save();
      ctx.beginPath(); ctx.rect(cx + 3, cy + 3, cardW - 6, 80); ctx.clip();
      ctx.translate(cx + cardW * 0.44, cy + 58);
      const sc = Math.min(0.72, cardW / 150);
      ctx.scale(sc, sc);
      try { drawBuilding(ctx, pv.tc, 0, Team.Player); } catch { /* a preview never breaks the menu */ }
      ctx.restore();
      ctx.save();
      ctx.translate(cx + cardW * 0.8, cy + 72);
      ctx.scale(1.6, 1.6);
      try { drawUnit(ctx, pv.unit, 0, 0); } catch { /* ditto */ }
      ctx.restore();
      setFactionResolver(null);
      if (locked) {
        // Dimmed behind a lock, with the price.
        ctx.fillStyle = "rgba(12,9,5,0.62)";
        ctx.beginPath(); ctx.roundRect(cx + 2, cy + 2, cardW - 4, cardH - 4, 6); ctx.fill();
        const lx = cx + cardW / 2, ly = cy + 40;
        ctx.strokeStyle = "#e8c060"; ctx.lineWidth = 3;
        ctx.beginPath(); ctx.arc(lx, ly - 6, 8, Math.PI, 0); ctx.stroke();
        ctx.fillStyle = "#e8c060";
        ctx.beginPath(); ctx.roundRect(lx - 12, ly - 6, 24, 18, 3); ctx.fill();
        ui.text(`${FACTION_PRICE} ✦`, lx, ly + 34, { align: "center", size: 12, bold: true, color: "#e8c060" });
      }
      ui.text(d.name.replace(/^The /, ""), cx + cardW / 2, cy + 98, { align: "center", size: 13, bold: true, color: locked ? "#8f8770" : sel ? "#ffe9b0" : PAL.uiParchment });
      ui.text(locked ? "Locked" : CURVE[d.curve], cx + cardW / 2, cy + 114, { align: "center", size: 10.5, color: locked ? "#8f8770" : sel ? d.color : "#9b927c" });
    });

    let dy = y + 46 + cardH + 22;
    ui.text(`${f.name} — ${f.era}`, x0 + 18, dy, { size: 15, bold: true, color: f.color });
    ui.text(`“${f.tagline}”`, x0 + 18, dy + 20, { size: 12.5, color: "#d8cdb4", font: "Georgia, serif" });
    dy += 44;
    const half = (colW - 48) / 2;
    f.strengths.forEach((line, i) => ui.text(`+  ${line}`, x0 + 18, dy + i * 18, { size: 12.5, color: "#9fe0a0" }));
    f.weaknesses.forEach((line, i) => ui.text(`–  ${line}`, x0 + 32 + half, dy + i * 18, { size: 12.5, color: "#e8a898" }));

    // Commander, cycling through the ones you own; remembered per faction.
    const cy2 = y + panelH - 42;
    const owned = COMMANDER_IDS.filter((id) => profile.ownsCommander(id));
    const cur = COMMANDERS[this.config.commander];
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.18);
    ctx.beginPath(); ctx.moveTo(x0 + 18, cy2 - 12); ctx.lineTo(x0 + colW - 18, cy2 - 12); ctx.stroke();
    ui.text("Commander", x0 + 18, cy2 + 14, { size: 13, bold: true, color: PAL.uiAccent });
    const cycle = (dir: number) => {
      const i = owned.indexOf(this.config.commander);
      const next = owned[(i + dir + owned.length) % owned.length];
      this.config.commander = next;
      profile.selectCommander(next);
      profile.pairCommander(this.config.faction || DEFAULT_FACTION, next);
      audio.play("ui");
    };
    if (owned.length > 1) {
      if (ui.button("‹", x0 + 104, cy2, 28, 28, {})) cycle(-1);
      if (ui.button("›", x0 + colW - 46, cy2, 28, 28, {})) cycle(1);
    }
    if (cur) {
      ui.text(`${cur.name} — ${cur.title}`, x0 + 142, cy2 + 8, { size: 14, bold: true, color: cur.color });
      const perks = commanderPerks(cur).join("  •  ");
      const oath = cur.oath ? `  •  Favours the Oath of the ${cur.oath[0].toUpperCase()}${cur.oath.slice(1)}` : "";
      ui.text(perks + oath, x0 + 142, cy2 + 26, { size: 11.5, color: "#c9bea3" });
    }
    return y + panelH;
  }
}

function wrapText(text: string, x: number, y: number, maxW: number, size: number, color: string) {
  const ctx = ui.ctx;
  ctx.font = `${size}px 'Trebuchet MS', sans-serif`;
  const words = text.split(" ");
  let line = "";
  let yy = y;
  for (const w of words) {
    const test = line ? line + " " + w : w;
    if (ctx.measureText(test).width > maxW && line) {
      ui.text(line, x, yy, { size, color });
      line = w;
      yy += size + 4;
    } else {
      line = test;
    }
  }
  if (line) ui.text(line, x, yy, { size, color });
}

// ----------------------------------------------------------------- armory --

interface SpinTicket {
  rarity: number;
  unitName: string;
}

const COMMANDER_RECRUIT_COST = 500;

export class ArmoryScreen {
  tab: "chests" | "boons" | "collection" | "commanders" = "chests";
  // Chest-opening overlay state.
  private opening: ChestDef | null = null;
  private result: RollResult | null = null;
  // Boon-cache opening overlay (same lottery spinner, boon payload).
  private boonOpening = false;
  private boonResult: BoonRoll | null = null;
  private tickets: SpinTicket[] = [];
  private spinT = 0; // 0..1 animation progress
  private spinDur = 4.2;
  private revealed = false;
  private claimed = false;
  private lastTickIndex = -1;
  private particles = new Particles(300);

  draw(W: number, H: number, time: number, dt: number, profile: Profile): "back" | null {
    drawMenuBackground(W, H, time);
    ui.text("The Armory", W / 2, 56, {
      align: "center", size: 34, bold: true, color: "#ffe9b0", font: "Georgia, serif",
    });
    ui.text(`✦ ${profile.data.renown} Renown      ⚔ ${profile.data.valor} Valor`, W / 2, 92, {
      align: "center", size: 17, bold: true, color: PAL.uiAccent,
    });

    let action: "back" | null = null;
    if (!this.opening && !this.boonOpening) {
      // Tabs.
      const tabW = 138;
      const tabs: [typeof this.tab, string][] = [["chests", "War Chests"], ["boons", "Boons"], ["commanders", "Commanders"], ["collection", "Collection"]];
      let tx = W / 2 - (tabs.length * (tabW + 8) - 8) / 2;
      for (const [id, label] of tabs) {
        if (ui.button(label, tx, 112, tabW, 36, { accent: this.tab === id })) {
          this.tab = id;
          audio.play("ui");
        }
        tx += tabW + 8;
      }

      if (this.tab === "chests") this.drawChests(W, H, profile);
      else if (this.tab === "boons") this.drawBoons(W, H, profile);
      else if (this.tab === "commanders") this.drawCommanders(W, H, profile);
      else this.drawCollection(W, H, profile);

      if (ui.button("⟵ Back", 24, H - 68, 130, 44, { size: 15 })) action = "back";
    } else if (this.opening) {
      this.drawOpening(W, H, dt, profile);
    } else {
      this.drawBoonOpening(W, H, dt, profile);
    }
    return action;
  }

  private drawChests(W: number, H: number, profile: Profile) {
    const n = CHESTS.length;
    const cw = 250;
    const gap = 28;
    const x0 = W / 2 - (n * cw + (n - 1) * gap) / 2;
    const y0 = 180;
    for (let i = 0; i < n; i++) {
      const chest = CHESTS[i];
      const x = x0 + i * (cw + gap);
      ui.panel(x, y0, cw, 320, { light: true });
      // chest art
      drawChestArt(x + cw / 2, y0 + 86, 1 + i * 0.18, i);
      ui.text(chest.name, x + cw / 2, y0 + 170, { align: "center", size: 17, bold: true, color: PAL.uiAccent });
      wrapText(chest.desc, x + 18, y0 + 196, cw - 36, 12, "#bdb49a");
      // odds readout
      let oy = y0 + 244;
      const weights = RARITIES.map((r, ri) => r.weight * (chest.rarityBias[ri] ?? 1));
      const total = weights.reduce((a, b) => a + b, 0);
      const interesting = [2, 3, 4, 5];
      let ox = x + 18;
      for (const ri of interesting) {
        const pct = ((weights[ri] / total) * 100);
        const r = RARITIES[ri];
        ui.text(`${pct >= 10 ? pct.toFixed(0) : pct.toFixed(1)}%`, ox, oy, { size: 11, color: r.color, bold: true });
        ox += 54;
      }
      const afford = profile.data.renown >= chest.cost;
      if (
        ui.button(`Open — ${chest.cost} ✦`, x + 24, y0 + 266, cw - 48, 38, {
          accent: afford,
          disabled: !afford,
          size: 15,
        })
      ) {
        this.startOpening(chest, profile);
      }
    }
  }

  private startOpening(chest: ChestDef, profile: Profile) {
    if (!profile.spendRenown(chest.cost)) return;
    profile.data.openedChests++;
    const rng = new RNG(randomSeed());
    this.result = rollChest(chest, profile.ownedSetSnapshot(), rng);
    this.opening = chest;
    this.spinT = 0;
    this.revealed = false;
    this.claimed = false;
    this.lastTickIndex = -1;
    // Build the ticker strip: 60 tickets, winner placed at index 52.
    this.tickets = [];
    const weights = RARITIES.map((r, ri) => r.weight * (chest.rarityBias[ri] ?? 1));
    for (let i = 0; i < 60; i++) {
      const rarity = rng.weightedIndex(weights);
      const unit = rng.pick(COLLECTIBLE_UNIT_IDS);
      this.tickets.push({ rarity, unitName: UNITS[unit].name });
    }
    this.tickets[52] = { rarity: this.result.rarity, unitName: this.result.variant.unitName };
    audio.play("ui");
  }

  private drawOpening(W: number, H: number, dt: number, profile: Profile) {
    const ctx = ui.ctx;
    const result = this.result!;
    ctx.fillStyle = "rgba(8, 6, 3, 0.78)";
    ctx.fillRect(0, 0, W, H);

    if (!this.revealed) {
      this.spinT = Math.min(1, this.spinT + dt / this.spinDur);
      const ease = 1 - Math.pow(1 - this.spinT, 3.2); // strong deceleration
      const ticketW = 132;
      // Land ticket 52 dead-center.
      const finalOffset = 52 * ticketW;
      const startOffset = finalOffset - ticketW * 34;
      const offset = startOffset + (finalOffset - startOffset) * ease;

      // tick sound as tickets pass the needle
      const idx = Math.floor(offset / ticketW);
      if (idx !== this.lastTickIndex) {
        this.lastTickIndex = idx;
        audio.play("tick");
      }

      const cy = H / 2;
      ui.text("Opening " + this.opening!.name + "…", W / 2, cy - 130, {
        align: "center", size: 20, bold: true, color: PAL.uiAccent,
      });
      // ticket strip
      ctx.save();
      ctx.beginPath();
      ctx.rect(W / 2 - 420, cy - 70, 840, 140);
      ctx.clip();
      for (let i = 0; i < this.tickets.length; i++) {
        const t = this.tickets[i];
        const x = W / 2 + (i * ticketW - offset);
        if (x < W / 2 - 500 || x > W / 2 + 500) continue;
        const r = rarityByIndex(t.rarity);
        ctx.fillStyle = shade("#2a2218", 0.05);
        ctx.beginPath();
        ctx.roundRect(x - ticketW / 2 + 5, cy - 60, ticketW - 10, 120, 7);
        ctx.fill();
        ctx.strokeStyle = r.color;
        ctx.lineWidth = 2.5;
        ctx.stroke();
        ctx.fillStyle = withAlpha(r.color, 0.18);
        ctx.fill();
        ui.text(t.unitName, x, cy - 14, { align: "center", size: 14, bold: true });
        ui.text(r.name, x, cy + 16, { align: "center", size: 11, color: r.color, bold: true });
      }
      ctx.restore();
      // needle
      ctx.strokeStyle = "#ffe9b0";
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.moveTo(W / 2, cy - 78);
      ctx.lineTo(W / 2, cy + 78);
      ctx.stroke();

      if (this.spinT >= 1) {
        this.revealed = true;
        audio.play("reveal");
        if (result.rarity >= 3) audio.play("levelup");
        const r = rarityByIndex(result.rarity);
        this.particles.burst(W / 2, H / 2, 26 + result.rarity * 22, r.color, 260, {
          maxLife: 1.2, size: 3.4, gravity: 160, glow: result.rarity >= 4,
        });
      }
      // click to skip
      if (ui.clicked) this.spinT = 1;
    } else {
      // Reveal card.
      const r = rarityByIndex(result.rarity);
      const cw = 360;
      const chH = 300;
      const x = W / 2 - cw / 2;
      const y = H / 2 - chH / 2 - 20;
      ctx.save();
      ctx.shadowColor = r.color;
      ctx.shadowBlur = result.rarity >= 3 ? 42 : 18;
      ctx.fillStyle = "#241d12";
      ctx.beginPath();
      ctx.roundRect(x, y, cw, chH, 12);
      ctx.fill();
      ctx.restore();
      ctx.strokeStyle = r.color;
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.roundRect(x, y, cw, chH, 12);
      ctx.stroke();

      ui.text(r.name.toUpperCase(), W / 2, y + 40, { align: "center", size: 16, bold: true, color: r.color });
      ui.text(result.variant.name, W / 2, y + 84, { align: "center", size: 24, bold: true, color: "#ffe9b0" });
      ui.text(result.variant.unitName + " variant", W / 2, y + 116, { align: "center", size: 14, color: "#bdb49a" });

      // stat preview
      const hpMult = [100, 106, 113, 122, 134, 150][result.rarity];
      const atkMult = [100, 105, 111, 119, 130, 145][result.rarity];
      ui.text(`❤ ${hpMult}%    ⚔ ${atkMult}%`, W / 2, y + 156, { align: "center", size: 16, bold: true });

      if (result.duplicate) {
        ui.text(`Duplicate — refunded ${result.refund} ✦`, W / 2, y + 196, {
          align: "center", size: 14, color: PAL.uiAccent,
        });
      }

      this.particles.update(dt);
      for (const p of this.particles.pool) {
        if (!p.active) continue;
        ctx.globalAlpha = p.life / p.maxLife;
        ctx.fillStyle = p.color;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size * (p.life / p.maxLife), 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;

      if (!this.claimed && ui.button("Claim", W / 2 - 80, y + chH - 64, 160, 42, { accent: true, size: 17 })) {
        this.claimed = true;
        if (result.duplicate) {
          profile.addRenown(result.refund);
          audio.play("coin");
        } else {
          profile.grant(result.variant.key);
          audio.play("complete");
        }
        profile.save();
        this.opening = null;
        this.result = null;
      }
    }
  }

  private startBoonOpening(profile: Profile) {
    if (!profile.spendValor(BOON_CACHE_COST)) return;
    const rng = new RNG(randomSeed());
    this.boonResult = rollBoonCache(profile.boonSetSnapshot(), rng);
    this.boonOpening = true;
    this.spinT = 0;
    this.revealed = false;
    this.claimed = false;
    this.lastTickIndex = -1;
    // Ticker strip: 60 boon tickets at advanced rarities, winner at index 52.
    this.tickets = [];
    const advW = RARITIES.slice(1).map((r) => r.weight); // caches never roll Common
    for (let i = 0; i < 60; i++) {
      const rarity = rng.weightedIndex(advW) + 1;
      const id = rng.pick(BOON_IDS);
      this.tickets.push({ rarity, unitName: BOONS_BY_ID[id].name });
    }
    this.tickets[52] = { rarity: this.boonResult.rarity, unitName: this.boonResult.name };
    audio.play("ui");
  }

  private drawBoonOpening(W: number, H: number, dt: number, profile: Profile) {
    const ctx = ui.ctx;
    const res = this.boonResult!;
    ctx.fillStyle = "rgba(8, 6, 3, 0.78)";
    ctx.fillRect(0, 0, W, H);

    if (!this.revealed) {
      this.spinT = Math.min(1, this.spinT + dt / this.spinDur);
      const ease = 1 - Math.pow(1 - this.spinT, 3.2);
      const ticketW = 132;
      const finalOffset = 52 * ticketW;
      const startOffset = finalOffset - ticketW * 34;
      const offset = startOffset + (finalOffset - startOffset) * ease;

      const idx = Math.floor(offset / ticketW);
      if (idx !== this.lastTickIndex) {
        this.lastTickIndex = idx;
        audio.play("tick");
      }

      const cy = H / 2;
      ui.text("Opening Warband Cache…", W / 2, cy - 130, { align: "center", size: 20, bold: true, color: PAL.uiAccent });
      ctx.save();
      ctx.beginPath();
      ctx.rect(W / 2 - 420, cy - 70, 840, 140);
      ctx.clip();
      for (let i = 0; i < this.tickets.length; i++) {
        const t = this.tickets[i];
        const x = W / 2 + (i * ticketW - offset);
        if (x < W / 2 - 500 || x > W / 2 + 500) continue;
        const r = rarityByIndex(t.rarity);
        ctx.fillStyle = shade("#2a2218", 0.05);
        ctx.beginPath();
        ctx.roundRect(x - ticketW / 2 + 5, cy - 60, ticketW - 10, 120, 7);
        ctx.fill();
        ctx.strokeStyle = r.color;
        ctx.lineWidth = 2.5;
        ctx.stroke();
        ctx.fillStyle = withAlpha(r.color, 0.18);
        ctx.fill();
        ui.text(t.unitName, x, cy - 14, { align: "center", size: 13, bold: true });
        ui.text(r.name, x, cy + 16, { align: "center", size: 11, color: r.color, bold: true });
      }
      ctx.restore();
      ctx.strokeStyle = "#ffe9b0";
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.moveTo(W / 2, cy - 78);
      ctx.lineTo(W / 2, cy + 78);
      ctx.stroke();

      if (this.spinT >= 1) {
        this.revealed = true;
        audio.play("reveal");
        if (res.rarity >= 3) audio.play("levelup");
        const r = rarityByIndex(res.rarity);
        this.particles.burst(W / 2, H / 2, 26 + res.rarity * 22, r.color, 260, {
          maxLife: 1.2, size: 3.4, gravity: 160, glow: res.rarity >= 4,
        });
      }
      if (ui.clicked) this.spinT = 1;
    } else {
      const r = rarityByIndex(res.rarity);
      const def = BOONS_BY_ID[res.boonId];
      const cw = 380;
      const chH = 300;
      const x = W / 2 - cw / 2;
      const y = H / 2 - chH / 2 - 20;
      ctx.save();
      ctx.shadowColor = r.color;
      ctx.shadowBlur = res.rarity >= 3 ? 42 : 18;
      ctx.fillStyle = "#241d12";
      ctx.beginPath();
      ctx.roundRect(x, y, cw, chH, 12);
      ctx.fill();
      ctx.restore();
      ctx.strokeStyle = r.color;
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.roundRect(x, y, cw, chH, 12);
      ctx.stroke();

      ui.text(r.name.toUpperCase(), W / 2, y + 40, { align: "center", size: 16, bold: true, color: r.color });
      ui.text(res.name, W / 2, y + 84, { align: "center", size: 24, bold: true, color: "#ffe9b0" });
      ui.text(`${def.category[0].toUpperCase() + def.category.slice(1)} boon`, W / 2, y + 114, { align: "center", size: 14, color: "#bdb49a" });
      wrapText(def.detail(res.rarity), x + 28, y + 150, cw - 56, 14, "#d8cdb4");

      if (res.duplicate) {
        ui.text(`Duplicate — refunded ${res.refund} ⚔`, W / 2, y + 224, { align: "center", size: 14, color: PAL.uiAccent });
      } else {
        ui.text("Upgraded! Equip it in your Battle Plan.", W / 2, y + 224, { align: "center", size: 13, color: "#9fd08a" });
      }

      this.particles.update(dt);
      for (const p of this.particles.pool) {
        if (!p.active) continue;
        ctx.globalAlpha = p.life / p.maxLife;
        ctx.fillStyle = p.color;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size * (p.life / p.maxLife), 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;

      if (!this.claimed && ui.button("Claim", W / 2 - 80, y + chH - 64, 160, 42, { accent: true, size: 17 })) {
        this.claimed = true;
        if (res.duplicate) {
          profile.addValor(res.refund);
          audio.play("coin");
        } else {
          profile.grantBoon(res.key);
          audio.play("complete");
        }
        profile.save();
        this.boonOpening = false;
        this.boonResult = null;
      }
    }
  }

  private drawBoons(W: number, H: number, profile: Profile) {
    const colW = Math.min(960, W - 80);
    const x0 = W / 2 - colW / 2;
    let y = 162;

    // Warband Cache — buy a boon roll with Valor.
    ui.panel(x0, y, colW, 70);
    ui.text("Warband Cache", x0 + 16, y + 26, { size: 17, bold: true, color: PAL.uiAccent });
    ui.text("You own every boon at Common. Open caches to roll higher rarities — stronger versions of the same boon.", x0 + 16, y + 48, { size: 12, color: "#bdb49a" });
    const canAfford = profile.data.valor >= BOON_CACHE_COST;
    if (ui.button(`Open — ${BOON_CACHE_COST} ⚔`, x0 + colW - 200, y + 18, 184, 36, { accent: canAfford, disabled: !canAfford, tooltip: canAfford ? undefined : ["Not enough Valor", "Earn Valor by fighting — kills, razings and wins."] })) {
      this.startBoonOpening(profile);
    }
    y += 84;

    ui.text("Battle Plan — equip one Offensive, Defensive & Supportive boon, then set which age each unlocks (they stack as you advance).", x0 + 16, y, { size: 13, color: "#d8cdb4" });
    y += 22;

    const AGE_LABEL = ["Hearth Age (start)", "Banner Age", "Crown Age"];
    const cw = (colW - 24) / 3;
    BOON_CATEGORIES.forEach((cat, ci) => {
      const cx = x0 + ci * (cw + 12);
      const title = cat[0].toUpperCase() + cat.slice(1);
      ui.text(title, cx + 6, y + 14, { size: 14, bold: true, color: PAL.uiAccent });

      // Age picker: which age this category's boon unlocks at (I / II / III).
      const curAge = profile.boonAgeFor(cat);
      const bw = (cw - 12) / 3;
      for (let a = 0; a < 3; a++) {
        if (ui.button(["I", "II", "III"][a], cx + a * (bw + 4), y + 24, bw, 22, {
          accent: curAge === a, size: 12, tooltip: [AGE_LABEL[a], "When this boon activates in a match."],
        })) {
          profile.setBoonAge(cat, a);
          audio.play("ui");
        }
      }

      const equipped = profile.data.equippedBoons[cat];
      let by = y + 54;
      for (const def of BOONS.filter((b) => b.category === cat)) {
        const best = profile.bestBoonRarity(def.id);
        const owns = best >= 0;
        const isEq = equipped === def.id;
        const ch = 50;
        const r = owns ? rarityByIndex(best) : null;
        if (ui.button("", cx, by, cw, ch, { accent: isEq, disabled: !owns })) {
          profile.equipBoon(def.id, !isEq);
          audio.play("ui");
        }
        ui.text(def.name + (isEq ? "  ✓" : ""), cx + 10, by + 16, {
          size: 13, bold: true, color: owns ? (r ? r.color : PAL.uiParchment) : "#6f685a",
        });
        const sub = owns ? def.detail(best) : "Locked — find it in a Warband Cache";
        wrapText(sub, cx + 10, by + 32, cw - 20, 11, owns ? "#cabfa4" : "#6f685a");
        by += ch + 7;
      }
    });
  }

  private drawCommanders(W: number, H: number, profile: Profile) {
    const owned = COMMANDER_IDS.filter((id) => profile.ownsCommander(id)).length;
    ui.text(`${owned}/${COMMANDER_IDS.length} commanders unlocked`, W / 2, 170, {
      align: "center", size: 15, color: "#d8cdb4",
    });
    const canRecruit = owned < COMMANDER_IDS.length && profile.data.renown >= COMMANDER_RECRUIT_COST;
    if (ui.button(`Recruit a Commander — ${COMMANDER_RECRUIT_COST} ✦`, W / 2 - 180, 186, 360, 40, {
      accent: canRecruit, disabled: !canRecruit, size: 15,
    })) {
      const got = profile.recruitCommander(COMMANDER_RECRUIT_COST);
      if (got) {
        profile.selectCommander(got);
        profile.clearCommanderReveal(); // shown inline here, not on the menu
        audio.play("levelup");
      }
    }

    const cols = 3;
    const cw = 320;
    const gap = 18;
    const x0 = W / 2 - (cols * cw + (cols - 1) * gap) / 2;
    const y0 = 244;
    COMMANDER_IDS.forEach((id, i) => {
      const def = COMMANDERS[id];
      const has = profile.ownsCommander(id);
      const selected = profile.data.commander === id;
      const x = x0 + (i % cols) * (cw + gap);
      const y = y0 + Math.floor(i / cols) * 150;
      ui.panel(x, y, cw, 138, { light: has });
      if (!has) { ui.ctx.fillStyle = "rgba(8,6,3,0.45)"; ui.ctx.fillRect(x, y, cw, 138); }
      ui.text(has ? `${def.name} — ${def.title}` : `??? — ${def.title}`, x + 16, y + 26, {
        size: 15, bold: true, color: has ? def.color : "#7a7263",
      });
      if (has) {
        wrapText(def.desc, x + 16, y + 48, cw - 32, 12, "#bdb49a");
        let py = y + 96;
        for (const perk of commanderPerks(def)) {
          ui.text("• " + perk, x + 16, py, { size: 11, color: PAL.uiAccent });
          py += 16;
        }
        if (selected) ui.text("✓ Leading", x + cw - 90, y + 26, { size: 12, bold: true, color: PAL.uiGood });
        else if (ui.button("Lead", x + cw - 78, y + 104, 62, 24, { size: 12 })) {
          profile.selectCommander(id);
          audio.play("ui");
        }
      } else {
        ui.text("Locked — recruit to reveal", x + 16, y + 60, { size: 12, color: "#9b927c" });
      }
    });
  }

  private drawCollection(W: number, H: number, profile: Profile) {
    const rows = COLLECTIBLE_UNIT_IDS.length;
    const rowH = 52;
    const colW = Math.min(900, W - 60);
    const x0 = W / 2 - colW / 2;
    let y = 172;

    if (ui.button("Equip Best Everywhere", x0 + colW - 210, y - 8, 210, 30, { accent: true, size: 13 })) {
      profile.equipBestAll();
      audio.play("complete");
    }
    y += 32;

    const cellW = 92;
    const gridX = x0 + 200;
    // rarity headers
    for (let ri = 0; ri < RARITIES.length; ri++) {
      const r = RARITIES[ri];
      ui.text(r.name.replace("Extremely Rare", "Ex. Rare").replace("One-of-a-Kind", "Unique"), gridX + ri * cellW + cellW / 2, y, {
        align: "center", size: 10.5, color: r.color, bold: true,
      });
    }
    y += 16;

    for (const unitId of COLLECTIBLE_UNIT_IDS) {
      const u = UNITS[unitId];
      ui.panel(x0, y, colW, rowH - 6, { light: true });
      ui.text(u.name, x0 + 14, y + (rowH - 6) / 2, { size: 14, bold: true });
      const equipped = profile.data.loadout[unitId] ?? 0;
      for (let ri = 0; ri < RARITIES.length; ri++) {
        const r = RARITIES[ri];
        const owned = profile.owns(variantKey(unitId, ri));
        const isEq = equipped === ri;
        const bx = gridX + ri * cellW + 6;
        const by = y + 7;
        const bw = cellW - 12;
        const bh = rowH - 20;
        const v = VARIANT_BY_KEY[variantKey(unitId, ri)];
        if (
          ui.button(owned ? (isEq ? "★" : "✓") : "🔒", bx, by, bw, bh, {
            disabled: !owned,
            accent: isEq,
            size: 13,
            tooltip: owned
              ? [v.name, `${r.name}`, isEq ? "Equipped" : "Click to equip"]
              : [v.name, `${r.name}`, "Locked — found in War Chests"],
          }) && owned && !isEq
        ) {
          profile.equip(unitId, ri);
          audio.play("ui");
        }
        // rarity underline
        ui.ctx.fillStyle = owned ? r.color : withAlpha(r.color, 0.25);
        ui.ctx.fillRect(bx + 4, by + bh - 3, bw - 8, 2.5);
      }
      y += rowH;
      if (y > H - 80) break; // viewport guard (all rosters currently fit)
    }
  }
}

function drawChestArt(cx: number, cy: number, scale: number, tier: number) {
  const ctx = ui.ctx;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(scale, scale);
  const bodyCol = tier === 0 ? "#7a5a36" : tier === 1 ? "#6a6f7e" : "#8a6a2e";
  const trimCol = tier === 0 ? "#4a3620" : tier === 1 ? "#b8c2d4" : "#ffd166";
  // body
  ctx.fillStyle = bodyCol;
  ctx.beginPath();
  ctx.roundRect(-46, -18, 92, 48, 6);
  ctx.fill();
  // lid
  ctx.fillStyle = shade(bodyCol, 0.15);
  ctx.beginPath();
  ctx.roundRect(-46, -40, 92, 28, [14, 14, 0, 0]);
  ctx.fill();
  // bands
  ctx.fillStyle = trimCol;
  ctx.fillRect(-46, -16, 92, 5);
  ctx.fillRect(-14, -40, 8, 70);
  ctx.fillRect(6, -40, 8, 70);
  // lock
  ctx.fillStyle = trimCol;
  ctx.beginPath();
  ctx.roundRect(-8, -8, 16, 18, 3);
  ctx.fill();
  ctx.fillStyle = "#241c10";
  ctx.beginPath();
  ctx.arc(0, -1, 3.4, 0, Math.PI * 2);
  ctx.fill();
  if (tier === 2) {
    // royal glow
    ctx.strokeStyle = withAlpha("#ffd166", 0.7);
    ctx.lineWidth = 2;
    for (let i = 0; i < 4; i++) {
      const a = -Math.PI / 2 + (i - 1.5) * 0.5;
      ctx.beginPath();
      ctx.moveTo(Math.cos(a) * 50, -30 + Math.sin(a) * 22);
      ctx.lineTo(Math.cos(a) * 64, -30 + Math.sin(a) * 34);
      ctx.stroke();
    }
  }
  ctx.restore();
}

// -------------------------------------------------------------- post-match --

/**
 * Time series for the progression chart. `players` carries a line per realm;
 * `mine` / `foe` (alliance totals) are the fallback when it is absent.
 */
const num0 = (n: number) => Math.round(n).toLocaleString("en-GB");

export type GraphSeries = {
  ts: number[];
  mine: Record<string, number[]>;
  foe: Record<string, number[]>;
  players?: { team: number; you: boolean; horde?: boolean; values: Record<string, number[]> }[];
} | null;

export class PostMatchScreen {
  private xpAnim = 0;
  private graphMetric: "score" | "military" | "economy" = "score";
  private reportTab: ReportTab = "overview";

  reset() {
    this.xpAnim = 0;
    this.reportTab = "overview";
  }

  /**
   * A time-series chart: a line per realm in its own colour, yours drawn
   * heaviest and last so it is never buried. It used to draw exactly two lines,
   * "your alliance" and "everyone else", which in a free-for-all summed three
   * rivals into one line that meant nothing.
   */
  private drawChart(x: number, y: number, w: number, h: number, ts: number[], lines: { vals: number[]; color: string; you: boolean }[]) {
    const ctx = ui.ctx;
    const n = ts.length;
    const max = Math.max(1, ...lines.flatMap((l) => l.vals));
    // Frame + gridlines.
    ctx.strokeStyle = withAlpha("#ffffff", 0.08);
    ctx.lineWidth = 1;
    for (let g = 0; g <= 4; g++) {
      const gy = y + (h * g) / 4;
      ctx.beginPath(); ctx.moveTo(x, gy); ctx.lineTo(x + w, gy); ctx.stroke();
    }
    const plot = (vals: number[], color: string, width: number) => {
      ctx.strokeStyle = color;
      ctx.lineWidth = width;
      ctx.lineJoin = "round";
      ctx.beginPath();
      for (let i = 0; i < n; i++) {
        const px = x + (n <= 1 ? 0 : (i / (n - 1)) * w);
        const py = y + h - ((vals[i] ?? 0) / max) * h;
        if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
      }
      ctx.stroke();
    };
    for (const l of lines) if (!l.you) plot(l.vals, withAlpha(l.color, 0.9), 2);
    for (const l of lines) if (l.you) { plot(l.vals, "rgba(0,0,0,0.55)", 5.5); plot(l.vals, l.color, 3.2); }
    ctx.lineWidth = 1;
    // Y-axis max + time axis labels.
    ui.text(num0(max), x + 4, y + 12, { size: 11.5, color: "#b3a98f" });
    const endMin = Math.floor((ts[n - 1] ?? 0) / 60);
    const endSec = Math.floor((ts[n - 1] ?? 0) % 60);
    ui.text("0:00", x, y + h + 13, { size: 11.5, color: "#b3a98f" });
    ui.text(`${endMin}:${endSec.toString().padStart(2, "0")}`, x + w, y + h + 13, { size: 11.5, align: "right", color: "#b3a98f" });
  }

  draw(
    W: number,
    H: number,
    time: number,
    dt: number,
    won: boolean,
    report: MatchReport,
    rewards: MatchRewards,
    profile: Profile,
    xpBefore: number,
    levelsGained: number,
    graph: GraphSeries = null,
    awards: EarnedAward[] = [],
  ): "continue" | null {
    drawMenuBackground(W, H, time);
    const ctx = ui.ctx;
    ctx.fillStyle = "rgba(10, 8, 4, 0.62)";
    ctx.fillRect(0, 0, W, H);

    ui.text(won ? "VICTORY" : "DEFEAT", W / 2, 62, {
      align: "center", size: 46, bold: true,
      color: won ? "#ffe9b0" : "#c87a72", font: "Georgia, serif",
    });
    ui.text(reportSubtitle(report), W / 2, 92, { align: "center", size: 13, color: "#9a917b" });

    // Two columns: the report gets the room, the spoils get the corner.
    const pad = Math.max(24, (W - 1360) / 2);
    const right = 380;
    const gap = 24;
    const x0 = pad;
    const leftW = Math.max(420, W - pad * 2 - right - gap);
    const x1 = x0 + leftW + gap;
    const top = 116;
    const bottom = H - 84;
    const panelH = bottom - top;

    // ---- the report ----
    ui.panel(x0, top, leftW, panelH, { light: true });
    let tx = x0 + 18;
    for (const t of REPORT_TABS) {
      if (ui.button(t.label, tx, top + 14, 108, 28, { accent: this.reportTab === t.id, size: 12.5 })) {
        this.reportTab = t.id;
        audio.play("ui");
      }
      tx += 114;
    }
    // Whose colour is whose, once, rather than on every row.
    drawReportKey(x0 + leftW - 20, top + 28, report);
    drawReportTab(this.reportTab, x0 + 24, top + 66, leftW - 48, panelH - 82, report);

    // ---- spoils ----
    const spoilsH = 42 + Math.min(5, rewards.breakdown.length) * 26 + 62;
    ui.panel(x1, top, right, spoilsH, { light: true });
    ui.text("Spoils of War", x1 + 18, top + 26, { size: 16, bold: true, color: PAL.uiAccent });
    let ly = top + 54;
    for (const b of rewards.breakdown.slice(0, 5)) {
      ui.text(b.label, x1 + 18, ly, { size: 12.5, color: "#bdb49a" });
      ui.text(`+${b.xp} XP  +${b.renown} ✦`, x1 + right - 18, ly, { size: 12.5, bold: true, align: "right" });
      ly += 26;
    }
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.4);
    ctx.beginPath(); ctx.moveTo(x1 + 18, ly - 4); ctx.lineTo(x1 + right - 18, ly - 4); ctx.stroke();
    ly += 18;
    ui.text("Total", x1 + 18, ly, { size: 14, bold: true });
    ui.text(`+${rewards.xp} XP  +${rewards.renown} ✦  +${rewards.valor} ⚔`, x1 + right - 18, ly, {
      size: 13.5, bold: true, align: "right", color: PAL.uiAccent,
    });

    // ---- level ----
    this.xpAnim = Math.min(1, this.xpAnim + dt / 1.6);
    const animXp = xpBefore + rewards.xp * this.xpAnim;
    const info = levelFromXp(Math.floor(animXp));
    const lvlY = top + spoilsH + gap;
    ui.panel(x1, lvlY, right, 62, { light: true });
    ui.text(`Level ${info.level}`, x1 + 18, lvlY + 22, { size: 14, bold: true, color: PAL.uiAccent });
    if (levelsGained > 0 && this.xpAnim >= 1) {
      ui.text(`LEVEL UP! +${levelsGained}`, x1 + right - 18, lvlY + 22, {
        size: 13, bold: true, align: "right", color: "#ffe9b0",
      });
    }
    ui.bar(x1 + 18, lvlY + 38, right - 36, 10, info.into / info.need, PAL.uiAccent);

    // ---- what this match unlocked ----
    // Above the graph rather than below it: an achievement is news, and news
    // that has scrolled off the bottom of a panel is not news.
    let awardsH = 0;
    if (awards.length) {
      awardsH = 40 + awards.length * 24 + 8;
      const ay = lvlY + 62 + gap;
      ui.panel(x1, ay, right, awardsH, { light: true });
      ui.text("Earned", x1 + 18, ay + 24, { size: 15, bold: true, color: PAL.uiAccent });
      awards.forEach((a, i) => {
        const ry = ay + 48 + i * 24;
        ui.text(a.weekly ? "◈" : "★", x1 + 18, ry, {
          size: 13, bold: true, color: a.weekly ? "#7fd0ff" : "#ffd24a",
        });
        ui.text(a.name, x1 + 36, ry, { size: 12.5, color: "#e7ddc4" });
        ui.text(`+${a.valor} ⚔`, x1 + right - 18, ry, {
          size: 12.5, bold: true, align: "right", color: "#ffd24a",
        });
      });
    }

    // ---- progression graph ----
    if (graph && graph.ts.length >= 2) {
      const gy = lvlY + 62 + gap + (awardsH ? awardsH + gap : 0);
      const gh = bottom - gy;
      if (gh > 120) {
        ui.panel(x1, gy, right, gh, { light: true });
        ui.text("Progression", x1 + 18, gy + 22, { size: 14, bold: true, color: PAL.uiAccent });
        const tabs: ["score" | "military" | "economy", string][] = [["score", "Score"], ["military", "Army"], ["economy", "Eco"]];
        let gx = x1 + 130;
        for (const [id, label] of tabs) {
          if (ui.button(label, gx, gy + 8, 74, 26, { accent: this.graphMetric === id, size: 11.5 })) {
            this.graphMetric = id;
            audio.play("ui");
          }
          gx += 80;
        }
        const m = this.graphMetric;
        const lines = graph.players?.length
          ? graph.players.map((p) => ({
              vals: p.values[m] ?? [],
              color: p.horde ? "#8d8779" : teamColor(p.team).main,
              you: p.you,
              name: p.horde ? "Horde" : teamColor(p.team).name,
            }))
          : [
              { vals: graph.foe[m], color: "#e0786a", you: false, name: "Opponents" },
              { vals: graph.mine[m], color: "#7fb0e8", you: true, name: "You" },
            ];
        // Legend under the chart, wrapping: up to sixteen realms have to fit.
        ctx.font = `12px "Trebuchet MS", sans-serif`;
        const chipW = (l: { name: string; you: boolean }) => 18 + ctx.measureText(l.name + (l.you ? " (you)" : "")).width + 12;
        const legendRows: typeof lines[] = [[]];
        let rowW = 0;
        const legendW = right - 36;
        for (const l of lines) {
          const cw = chipW(l);
          if (rowW + cw > legendW && legendRows[legendRows.length - 1].length) { legendRows.push([]); rowW = 0; }
          legendRows[legendRows.length - 1].push(l);
          rowW += cw;
        }
        const legendH = legendRows.length * 18 + 4;
        this.drawChart(x1 + 34, gy + 48, right - 58, gh - 84 - legendH, graph.ts, lines);
        let ly2 = gy + gh - legendH - 4;
        for (const row of legendRows) {
          let lx = x1 + 18;
          for (const l of row) {
            ctx.fillStyle = l.color;
            ctx.fillRect(lx, ly2 - 5, 12, l.you ? 4 : 3);
            const label = l.name + (l.you ? " (you)" : "");
            ui.text(label, lx + 17, ly2, { size: 12, bold: l.you, color: l.you ? "#ffe9b0" : "#cfc4a8" });
            ctx.font = `12px "Trebuchet MS", sans-serif`;
            lx += chipW(l);
          }
          ly2 += 18;
        }
      }
    }

    if (ui.button("Continue", W / 2 - 110, H - 62, 220, 46, { accent: true, size: 17 })) {
      return "continue";
    }
    return null;
  }
}
