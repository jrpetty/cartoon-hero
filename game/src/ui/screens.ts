// Out-of-match screens: main menu, skirmish setup, armory (chests, collection,
// loadout), chest-opening reveal, and the post-match report.

import { Profile } from "../meta/profile";
import { CHESTS, ChestDef, rollChest, RollResult } from "../meta/chests";
import { RARITIES, rarityByIndex } from "../meta/rarity";
import { CATALOG, COLLECTIBLE_UNIT_IDS, variantKey, VARIANT_BY_KEY } from "../meta/catalog";
import { MatchRewards, levelFromXp } from "../meta/progression";
import { UNITS } from "../content/units";
import { PRESETS } from "../maps/generator";
import { GameMode } from "../sim/types";
import { DIFFICULTIES, DIFFICULTY_IDS } from "../ai/difficulty";
import { COMMANDERS, COMMANDER_IDS, commanderPerks } from "../content/commanders";
import { BOONS, BOONS_BY_ID, BOON_CATEGORIES, BoonCategory, BOON_IDS } from "../content/boons";
import { rollBoonCache, boonKey, BOON_CACHE_COST, BoonRoll } from "../meta/boon_cache";
import { PAL, shade, withAlpha } from "../render/palette";
import { ui } from "./ui";
import { RNG, randomSeed } from "../engine/rng";
import { audio } from "../engine/audio";
import { Particles } from "../engine/particles";

export interface SkirmishConfig {
  presetId: string;
  seed: number;
  difficulty: string;
  fairMode: boolean;
  players: number; // 2 = 1v1, 4 = FFA or 2v2
  allied: boolean; // true = 2v2 teams (you + ally vs two foes)
  commander: string; // selected commander id
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
}

// ------------------------------------------------------------------- menu --

export class MenuScreen {
  draw(W: number, H: number, time: number, profile: Profile): "skirmish" | "multiplayer" | "warband" | "armory" | "codex" | "settings" | null {
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

    // Title with drop shadow.
    ctx.save();
    ctx.textAlign = "center";
    ctx.font = "bold 64px Georgia, 'Times New Roman', serif";
    ctx.fillStyle = "rgba(0,0,0,0.5)";
    ctx.fillText("Banner & Blade", W / 2 + 3, H * 0.24 + 3);
    const grad = ctx.createLinearGradient(0, H * 0.24 - 40, 0, H * 0.24 + 20);
    grad.addColorStop(0, "#ffe9b0");
    grad.addColorStop(1, "#c8923a");
    ctx.fillStyle = grad;
    ctx.fillText("Banner & Blade", W / 2, H * 0.24);
    ctx.font = "italic 18px Georgia, serif";
    ctx.fillStyle = withAlpha("#f3e9d2", 0.85);
    ctx.fillText("Raise your banner. Sharpen your blade. Take the field.", W / 2, H * 0.24 + 42);
    ctx.restore();

    // Profile chip.
    const info = profile.levelInfo();
    ui.panel(W / 2 - 180, H * 0.36, 360, 64, { light: true });
    ui.text(`${profile.data.name} — Level ${info.level}`, W / 2, H * 0.36 + 18, {
      align: "center", size: 15, bold: true, color: PAL.uiAccent,
    });
    ui.bar(W / 2 - 150, H * 0.36 + 36, 300, 9, info.into / info.need, PAL.uiAccent);
    ui.text(`${info.into}/${info.need} XP`, W / 2, H * 0.36 + 54, { align: "center", size: 11, color: "#bdb49a" });

    let action: "skirmish" | "multiplayer" | "warband" | "armory" | "codex" | "settings" | null = null;
    const bw = 280;
    const bx = W / 2 - bw / 2;
    let by = H * 0.36 + 92;
    if (ui.button("⚔  Skirmish", bx, by, bw, 52, { accent: true, size: 19 })) action = "skirmish";
    by += 60;
    if (ui.button("🔗  Multiplayer", bx, by, bw, 46, { size: 16, tooltip: ["Play online — up to 8 vs 8", "Join a hosted server, or quick 1v1 with no server."] })) action = "multiplayer";
    by += 56;
    if (ui.button("🎲  Warband Tactics", bx, by, bw, 46, { size: 16, tooltip: ["Auto-battler", "Draft a warband, merge star-ups, fight for the last spot standing."] })) action = "warband";
    by += 56;
    if (ui.button(`🗝  Armory   (${profile.data.renown} ✦)`, bx, by, bw, 48, { size: 16 })) action = "armory";
    by += 56;
    if (ui.button("📖  Codex", bx, by, bw / 2 - 6, 44, { size: 14 })) action = "codex";
    if (ui.button("⚙  Settings", bx + bw / 2 + 6, by, bw / 2 - 6, 44, { size: 14 })) action = "settings";
    by += 54;

    const stats = profile.data.stats;
    ui.text(
      `Battles ${stats.played}   Victories ${stats.wins}   Best streak ${stats.bestStreak}`,
      W / 2, H - 24,
      { align: "center", size: 13, color: withAlpha("#f3e9d2", 0.7) },
    );
    return action;
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
    fairMode: false,
    players: 2,
    allied: false,
    commander: "",
    nomad: false,
    mode: "conquest",
  };

  draw(W: number, H: number, time: number, profile: Profile): "start" | "spectate" | "back" | null {
    drawMenuBackground(W, H, time);
    ui.text("Skirmish Setup", W / 2, 64, {
      align: "center", size: 34, bold: true, color: "#ffe9b0", font: "Georgia, serif",
    });

    const colW = Math.min(880, W - 80);
    const x0 = W / 2 - colW / 2;
    let y = 110;

    // Map presets — laid out in a grid that wraps every 3 cards. A "Random"
    // card (rolled fresh from the seed each match) is appended after the set.
    const cards = [
      ...PRESETS.map((p) => ({ id: p.id, name: p.name, desc: p.desc })),
      { id: "random", name: "🎲 Random", desc: "A surprise battlefield — a different preset every match." },
    ];
    const perRow = 3;
    const cardH = 96;
    const rowGap = 12;
    const rows = Math.ceil(cards.length / perRow);
    const panelH = 38 + rows * (cardH + rowGap);
    ui.panel(x0, y, colW, panelH);
    ui.text("Battlefield", x0 + 16, y + 22, { size: 16, bold: true, color: PAL.uiAccent });
    const cardW = (colW - 32 - (perRow - 1) * 12) / perRow;
    for (let i = 0; i < cards.length; i++) {
      const p = cards[i];
      const cx = x0 + 16 + (i % perRow) * (cardW + 12);
      const cy = y + 38 + Math.floor(i / perRow) * (cardH + rowGap);
      const sel = this.config.presetId === p.id;
      if (ui.button("", cx, cy, cardW, cardH, { accent: sel })) {
        this.config.presetId = p.id;
        audio.play("ui");
      }
      ui.text(p.name, cx + 12, cy + 20, { size: 15, bold: true, color: sel ? "#ffe9b0" : PAL.uiParchment });
      wrapText(p.desc, cx + 12, cy + 42, cardW - 24, 13, "#bdb49a");
    }
    y += panelH + 12;

    // Seed + difficulty row.
    ui.panel(x0, y, colW, 86);
    ui.text("Seed", x0 + 16, y + 22, { size: 16, bold: true, color: PAL.uiAccent });
    ui.text(String(this.config.seed), x0 + 16, y + 50, { size: 14, color: "#bdb49a" });
    if (ui.button("🎲 New Seed", x0 + 16, y + 60, 110, 20, { size: 11 })) {
      this.config.seed = randomSeed();
      audio.play("ui");
    }

    ui.text("Enemy Commander", x0 + 210, y + 22, { size: 16, bold: true, color: PAL.uiAccent });
    const gapD = 10;
    const dw = (colW - 226 - 16 - 36 - gapD * (DIFFICULTY_IDS.length - 1)) / DIFFICULTY_IDS.length;
    for (let i = 0; i < DIFFICULTY_IDS.length; i++) {
      const d = DIFFICULTIES[DIFFICULTY_IDS[i]];
      const sel = this.config.difficulty === d.id;
      if (
        ui.button(d.name, x0 + 210 + i * (dw + gapD), y + 38, dw, 34, {
          accent: sel,
          tooltip: [d.name, d.desc],
        })
      ) {
        this.config.difficulty = d.id;
        audio.play("ui");
      }
    }
    y += 98;

    // Players (2–8) + team format.
    ui.panel(x0, y, colW, 96);
    ui.text("Players", x0 + 16, y + 26, { size: 16, bold: true, color: PAL.uiAccent });
    const counts = [2, 3, 4, 5, 6, 7, 8];
    const bw = 40;
    for (let i = 0; i < counts.length; i++) {
      const c = counts[i];
      const sel = this.config.players === c;
      if (ui.button(String(c), x0 + 120 + i * (bw + 8), y + 12, bw, 30, { accent: sel })) {
        this.config.players = c;
        if (c < 4) this.config.allied = false; // teams need at least 4
        audio.play("ui");
      }
    }
    ui.text("Format", x0 + 16, y + 66, { size: 16, bold: true, color: PAL.uiAccent });
    const teamsOK = this.config.players >= 4;
    const teamHalf = this.config.players / 2;
    const teamLabel = `Even Teams (${Math.ceil(teamHalf)}v${Math.floor(teamHalf)})`;
    if (ui.button("Free-for-All", x0 + 120, y + 54, 160, 30, {
      accent: !this.config.allied,
      tooltip: ["Free-for-All", "Every realm for itself — last one standing wins."],
    })) { this.config.allied = false; audio.play("ui"); }
    if (ui.button(teamsOK ? teamLabel : "Even Teams (4+)", x0 + 290, y + 54, 200, 30, {
      accent: this.config.allied,
      disabled: !teamsOK,
      tooltip: ["Even Teams", "Split into two allied sides with shared vision. Needs 4+ players."],
    })) { this.config.allied = true; audio.play("ui"); }
    y += 112;

    // Game mode.
    ui.panel(x0, y, colW, 64);
    ui.text("Mode", x0 + 16, y + 24, { size: 16, bold: true, color: PAL.uiAccent });
    const modes: [GameMode, string, string][] = [
      ["conquest", "Conquest", "Destroy every enemy. The classic skirmish."],
      ["survival", "Survival", "Co-op: you + AI allies hold out against escalating waves."],
      ["koth", "King of the Hill", "Hold the centre for 5 cumulative minutes to win."],
      ["regicide", "Regicide", "Each side has a King — slay theirs, protect yours."],
    ];
    const mwid = (colW - 150 - 16 - 36) / 4;
    for (let i = 0; i < modes.length; i++) {
      const [id, label, hint] = modes[i];
      if (ui.button(label.length > 11 ? "KotH" : label, x0 + 150 + i * (mwid + 12), y + 16, mwid, 32, {
        accent: this.config.mode === id, size: 12, tooltip: [label, hint],
      })) { this.config.mode = id; audio.play("ui"); }
    }
    y += 80;

    // Commander selector (cycle through the ones you own).
    if (!profile.ownsCommander(this.config.commander)) {
      this.config.commander = profile.data.commander || profile.data.commanders[0] || "";
    }
    ui.panel(x0, y, colW, 86);
    ui.text("Commander", x0 + 16, y + 24, { size: 16, bold: true, color: PAL.uiAccent });
    const owned = COMMANDER_IDS.filter((id) => profile.ownsCommander(id));
    const cur = COMMANDERS[this.config.commander];
    const cycle = (dir: number) => {
      const i = owned.indexOf(this.config.commander);
      const next = owned[(i + dir + owned.length) % owned.length];
      this.config.commander = next;
      profile.selectCommander(next);
      audio.play("ui");
    };
    if (owned.length > 1) {
      if (ui.button("‹", x0 + 150, y + 14, 28, 28, {})) cycle(-1);
      if (ui.button("›", x0 + colW - 44, y + 14, 28, 28, {})) cycle(1);
    }
    if (cur) {
      ui.text(`${cur.name} — ${cur.title}`, x0 + 190, y + 26, { size: 15, bold: true, color: cur.color });
      ui.text(commanderPerks(cur).join("   •   "), x0 + 190, y + 48, { size: 12, color: "#d8cdb4" });
      ui.text(`${owned.length}/${COMMANDER_IDS.length} unlocked — recruit more in the Armory`, x0 + 190, y + 68, { size: 11, color: "#9b927c" });
    }
    y += 102;

    // Fair mode + Nomad — two toggles sharing a row.
    ui.panel(x0, y, colW, 64);
    const half = colW / 2;
    const fm = this.config.fairMode;
    if (ui.button(fm ? "✓" : " ", x0 + 16, y + 16, 32, 32, { accent: fm })) {
      this.config.fairMode = !fm;
      audio.play("ui");
    }
    ui.text("Ranked (all-Common, +25% rewards)", x0 + 56, y + 26, { size: 13, bold: true });
    ui.text(fm ? "Variants benched." : "Equipped variants take the field.", x0 + 56, y + 46, { size: 11, color: "#bdb49a" });

    const nm = this.config.nomad;
    if (ui.button(nm ? "✓" : " ", x0 + half + 16, y + 16, 32, 32, { accent: nm })) {
      this.config.nomad = !nm;
      audio.play("ui");
    }
    ui.text("Nomad start", x0 + half + 56, y + 26, { size: 13, bold: true });
    ui.text("No Town Center — settle where you land.", x0 + half + 56, y + 46, { size: 11, color: "#bdb49a" });
    y += 80;

    let action: "start" | "spectate" | "back" | null = null;
    if (ui.button("⟵ Back", x0, y, 130, 44, { size: 15 })) action = "back";
    if (ui.button("👁 Watch", x0 + colW - 360, y, 130, 44, { size: 15, tooltip: ["Spectate an AI vs AI battle", "All sides are AI — sit back and watch."] })) action = "spectate";
    if (ui.button("⚔  To Battle!", x0 + colW - 220, y, 220, 44, { accent: true, size: 18 })) action = "start";
    return action;
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

    const AGE_LABEL = ["Age I (start)", "Age II", "Age III"];
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

type GraphSeries = { ts: number[]; mine: Record<string, number[]>; foe: Record<string, number[]> } | null;

export class PostMatchScreen {
  private xpAnim = 0;
  private graphMetric: "score" | "military" | "economy" = "score";

  reset() {
    this.xpAnim = 0;
  }

  /** A two-line time-series chart (your alliance vs enemies). */
  private drawChart(x: number, y: number, w: number, h: number, ts: number[], mine: number[], foe: number[]) {
    const ctx = ui.ctx;
    const n = ts.length;
    const max = Math.max(1, ...mine, ...foe);
    // Frame + gridlines.
    ctx.strokeStyle = withAlpha("#ffffff", 0.08);
    ctx.lineWidth = 1;
    for (let g = 0; g <= 4; g++) {
      const gy = y + (h * g) / 4;
      ctx.beginPath(); ctx.moveTo(x, gy); ctx.lineTo(x + w, gy); ctx.stroke();
    }
    const plot = (vals: number[], color: string) => {
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.beginPath();
      for (let i = 0; i < n; i++) {
        const px = x + (n <= 1 ? 0 : (i / (n - 1)) * w);
        const py = y + h - (vals[i] / max) * h;
        if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
      }
      ctx.stroke();
    };
    plot(foe, PAL.teams[1].main);
    plot(mine, PAL.teams[0].main);
    ctx.lineWidth = 1;
    // Y-axis max + time axis labels.
    ui.text(String(Math.round(max)), x + 4, y + 12, { size: 10, color: "#9a917b" });
    const endMin = Math.floor((ts[n - 1] ?? 0) / 60);
    const endSec = Math.floor((ts[n - 1] ?? 0) % 60);
    ui.text(`${endMin}:${endSec.toString().padStart(2, "0")}`, x + w - 4, y + h - 4, { size: 10, align: "right", color: "#9a917b" });
  }

  draw(
    W: number,
    H: number,
    time: number,
    dt: number,
    won: boolean,
    stats: { unitsKilled: number; unitsLost: number; buildingsRazed: number; buildingsLost: number; gathered: number },
    foe: { unitsKilled: number; gathered: number; buildingsRazed: number },
    durationSec: number,
    rewards: MatchRewards,
    profile: Profile,
    xpBefore: number,
    levelsGained: number,
    graph: GraphSeries = null,
  ): "continue" | null {
    drawMenuBackground(W, H, time);
    const ctx = ui.ctx;
    ctx.fillStyle = "rgba(10, 8, 4, 0.55)";
    ctx.fillRect(0, 0, W, H);

    ui.text(won ? "VICTORY" : "DEFEAT", W / 2, 96, {
      align: "center", size: 56, bold: true,
      color: won ? "#ffe9b0" : "#c87a72", font: "Georgia, serif",
    });
    ui.text(
      won ? "The field is yours, Commander." : "Your banner falls… but banners rise again.",
      W / 2, 140, { align: "center", size: 16, color: "#d8cdb4" },
    );

    const colW = 380;
    const gap = 40;
    const x0 = W / 2 - colW - gap / 2;
    const x1 = W / 2 + gap / 2;
    const y0 = 184;

    // Battle report.
    ui.panel(x0, y0, colW, 240, { light: true });
    ui.text("Battle Report", x0 + 20, y0 + 26, { size: 17, bold: true, color: PAL.uiAccent });
    const mins = Math.floor(durationSec / 60);
    const secs = Math.floor(durationSec % 60);
    const kd = stats.unitsLost > 0 ? (stats.unitsKilled / stats.unitsLost).toFixed(2) : "—";
    const lines: [string, string][] = [
      ["Duration", `${mins}:${secs.toString().padStart(2, "0")}`],
      ["Units lost", String(stats.unitsLost)],
      ["Kill / loss ratio", kd],
      ["Buildings razed", String(stats.buildingsRazed)],
      ["Buildings lost", String(stats.buildingsLost)],
    ];
    let ly = y0 + 54;
    for (const [k, v] of lines) {
      ui.text(k, x0 + 20, ly, { size: 14, color: "#bdb49a" });
      ui.text(v, x0 + colW - 20, ly, { size: 14, bold: true, align: "right" });
      ly += 23;
    }

    // You-vs-enemy comparison bars (you in azure, foe in crimson).
    ly += 4;
    const cmp: [string, number, number][] = [
      ["Slain", stats.unitsKilled, foe.unitsKilled],
      ["Razed", stats.buildingsRazed, foe.buildingsRazed],
      ["Gathered", stats.gathered, foe.gathered],
    ];
    for (const [label, mine, theirs] of cmp) {
      const max = Math.max(mine, theirs, 1);
      const bw = colW - 40;
      ctx.fillStyle = withAlpha("#000000", 0.25);
      ctx.fillRect(x0 + 20, ly, bw, 14);
      ctx.fillStyle = PAL.teams[0].main;
      ctx.fillRect(x0 + 20, ly, bw * (mine / max) * 0.5, 6);
      ctx.fillStyle = PAL.teams[1].main;
      ctx.fillRect(x0 + 20, ly + 8, bw * (theirs / max) * 0.5, 6);
      ui.text(`${label}: ${mine} vs ${theirs}`, x0 + 24, ly + 11, { size: 11, color: "#e7ddc4" });
      ly += 19;
    }

    // Rewards.
    ui.panel(x1, y0, colW, 240, { light: true });
    ui.text("Spoils of War", x1 + 20, y0 + 26, { size: 17, bold: true, color: PAL.uiAccent });
    ly = y0 + 60;
    for (const b of rewards.breakdown.slice(0, 5)) {
      ui.text(b.label, x1 + 20, ly, { size: 13, color: "#bdb49a" });
      ui.text(`+${b.xp} XP  +${b.renown} ✦`, x1 + colW - 20, ly, { size: 13, bold: true, align: "right" });
      ly += 28;
    }
    ui.ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.4);
    ui.ctx.beginPath();
    ui.ctx.moveTo(x1 + 20, ly);
    ui.ctx.lineTo(x1 + colW - 20, ly);
    ui.ctx.stroke();
    ly += 22;
    ui.text("Total", x1 + 20, ly, { size: 15, bold: true });
    ui.text(`+${rewards.xp} XP   +${rewards.renown} ✦   +${rewards.valor} ⚔`, x1 + colW - 20, ly, {
      size: 15, bold: true, align: "right", color: PAL.uiAccent,
    });

    // Animated XP bar.
    this.xpAnim = Math.min(1, this.xpAnim + dt / 1.6);
    const animXp = xpBefore + rewards.xp * this.xpAnim;
    const info = levelFromXp(Math.floor(animXp));
    const barW = colW * 2 + gap;
    ui.panel(x0, y0 + 256, barW, 64, { light: true });
    ui.text(`Level ${info.level}`, x0 + 20, y0 + 256 + 22, { size: 15, bold: true, color: PAL.uiAccent });
    if (levelsGained > 0 && this.xpAnim >= 1) {
      ui.text(`LEVEL UP! +${levelsGained}`, x0 + barW - 20, y0 + 256 + 22, {
        size: 15, bold: true, align: "right", color: "#ffe9b0",
      });
    }
    ui.bar(x0 + 20, y0 + 256 + 38, barW - 40, 10, info.into / info.need, PAL.uiAccent);

    // Progression graph: your alliance vs the enemy, over the match.
    if (graph && graph.ts.length >= 2) {
      const gy = y0 + 256 + 76;
      const gh = Math.min(178, H - 100 - gy);
      ui.panel(x0, gy, barW, gh, { light: true });
      ui.text("Progression", x0 + 20, gy + 22, { size: 15, bold: true, color: PAL.uiAccent });
      // Metric tabs.
      const tabs: ["score" | "military" | "economy", string][] = [["score", "Score"], ["military", "Army"], ["economy", "Economy"]];
      let tx = x0 + 150;
      for (const [id, label] of tabs) {
        if (ui.button(label, tx, gy + 8, 92, 26, { accent: this.graphMetric === id, size: 12 })) {
          this.graphMetric = id;
          audio.play("ui");
        }
        tx += 100;
      }
      // Legend.
      const ctx = ui.ctx;
      ctx.fillStyle = PAL.teams[0].main; ctx.fillRect(x0 + barW - 220, gy + 16, 12, 12);
      ui.text("You", x0 + barW - 204, gy + 26, { size: 12, color: "#e7ddc4" });
      ctx.fillStyle = PAL.teams[1].main; ctx.fillRect(x0 + barW - 150, gy + 16, 12, 12);
      ui.text("Enemies", x0 + barW - 134, gy + 26, { size: 12, color: "#e7ddc4" });
      this.drawChart(x0 + 40, gy + 44, barW - 80, gh - 60,
        graph.ts, graph.mine[this.graphMetric], graph.foe[this.graphMetric]);
    }

    if (ui.button("Continue", W / 2 - 110, H - 64, 220, 48, { accent: true, size: 18 })) {
      return "continue";
    }
    return null;
  }
}
