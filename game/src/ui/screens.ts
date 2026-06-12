// Out-of-match screens: main menu, skirmish setup, armory (chests, collection,
// loadout), chest-opening reveal, and the post-match report.

import { Profile } from "../meta/profile";
import { CHESTS, ChestDef, rollChest, RollResult } from "../meta/chests";
import { RARITIES, rarityByIndex } from "../meta/rarity";
import { CATALOG, COLLECTIBLE_UNIT_IDS, variantKey, VARIANT_BY_KEY } from "../meta/catalog";
import { MatchRewards, levelFromXp } from "../meta/progression";
import { UNITS } from "../content/units";
import { PRESETS } from "../maps/generator";
import { DIFFICULTIES, DIFFICULTY_IDS } from "../ai/difficulty";
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
  draw(W: number, H: number, time: number, profile: Profile): "skirmish" | "armory" | null {
    drawMenuBackground(W, H, time);
    const ctx = ui.ctx;

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

    let action: "skirmish" | "armory" | null = null;
    const bw = 280;
    const bx = W / 2 - bw / 2;
    let by = H * 0.36 + 92;
    if (ui.button("⚔  Skirmish", bx, by, bw, 52, { accent: true, size: 19 })) action = "skirmish";
    by += 64;
    if (ui.button(`🗝  Armory   (${profile.data.renown} ✦)`, bx, by, bw, 52, { size: 17 })) action = "armory";
    by += 64;

    // Volume sliders.
    ui.panel(bx, by, bw, 96, { light: true });
    ui.text("Master", bx + 16, by + 20, { size: 12 });
    audio.masterVol = ui.slider(bx + 90, by + 20, bw - 110, audio.masterVol, ui.clicked || isMouseDown());
    ui.text("Effects", bx + 16, by + 48, { size: 12 });
    audio.sfxVol = ui.slider(bx + 90, by + 48, bw - 110, audio.sfxVol, ui.clicked || isMouseDown());
    ui.text("Music", bx + 16, by + 76, { size: 12 });
    audio.musicVol = ui.slider(bx + 90, by + 76, bw - 110, audio.musicVol, ui.clicked || isMouseDown());
    audio.applyVolumes();

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
function isMouseDown() {
  return mouseDown;
}

// ------------------------------------------------------------------ setup --

export class SetupScreen {
  config: SkirmishConfig = {
    presetId: "open_plains",
    seed: randomSeed(),
    difficulty: "knight",
    fairMode: false,
  };

  draw(W: number, H: number, time: number, profile: Profile): "start" | "back" | null {
    drawMenuBackground(W, H, time);
    ui.text("Skirmish Setup", W / 2, 64, {
      align: "center", size: 34, bold: true, color: "#ffe9b0", font: "Georgia, serif",
    });

    const colW = Math.min(880, W - 80);
    const x0 = W / 2 - colW / 2;
    let y = 110;

    // Map presets.
    ui.panel(x0, y, colW, 150);
    ui.text("Battlefield", x0 + 16, y + 22, { size: 16, bold: true, color: PAL.uiAccent });
    const cardW = (colW - 48 - 24) / 3;
    for (let i = 0; i < PRESETS.length; i++) {
      const p = PRESETS[i];
      const cx = x0 + 16 + i * (cardW + 12);
      const sel = this.config.presetId === p.id;
      if (ui.button("", cx, y + 38, cardW, 96, { accent: sel })) {
        this.config.presetId = p.id;
        audio.play("ui");
      }
      ui.text(p.name, cx + 12, y + 58, { size: 15, bold: true, color: sel ? "#ffe9b0" : PAL.uiParchment });
      wrapText(p.desc, cx + 12, y + 80, cardW - 24, 13, "#bdb49a");
    }
    y += 162;

    // Seed + difficulty row.
    ui.panel(x0, y, colW, 86);
    ui.text("Seed", x0 + 16, y + 22, { size: 16, bold: true, color: PAL.uiAccent });
    ui.text(String(this.config.seed), x0 + 16, y + 50, { size: 14, color: "#bdb49a" });
    if (ui.button("🎲 New Seed", x0 + 16, y + 60, 110, 20, { size: 11 })) {
      this.config.seed = randomSeed();
      audio.play("ui");
    }

    ui.text("Enemy Commander", x0 + 210, y + 22, { size: 16, bold: true, color: PAL.uiAccent });
    const dw = (colW - 226 - 16 - 36) / 4;
    for (let i = 0; i < DIFFICULTY_IDS.length; i++) {
      const d = DIFFICULTIES[DIFFICULTY_IDS[i]];
      const sel = this.config.difficulty === d.id;
      if (
        ui.button(d.name, x0 + 210 + i * (dw + 12), y + 38, dw, 34, {
          accent: sel,
          tooltip: [d.name, d.desc],
        })
      ) {
        this.config.difficulty = d.id;
        audio.play("ui");
      }
    }
    y += 98;

    // Fair mode.
    ui.panel(x0, y, colW, 64);
    const fm = this.config.fairMode;
    if (ui.button(fm ? "✓" : " ", x0 + 16, y + 16, 32, 32, { accent: fm })) {
      this.config.fairMode = !fm;
      audio.play("ui");
    }
    ui.text("Ranked match — all-Common loadout (pure skill, +25% rewards)", x0 + 60, y + 26, { size: 14, bold: true });
    ui.text(
      fm ? "Your unlocked variants are benched for this battle." : "Your equipped variants will take the field.",
      x0 + 60, y + 46,
      { size: 12, color: "#bdb49a" },
    );
    y += 80;

    let action: "start" | "back" | null = null;
    if (ui.button("⟵ Back", x0, y, 130, 44, { size: 15 })) action = "back";
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

export class ArmoryScreen {
  tab: "chests" | "collection" = "chests";
  // Chest-opening overlay state.
  private opening: ChestDef | null = null;
  private result: RollResult | null = null;
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
    ui.text(`✦ ${profile.data.renown} Renown`, W / 2, 92, {
      align: "center", size: 17, bold: true, color: PAL.uiAccent,
    });

    let action: "back" | null = null;
    if (!this.opening) {
      // Tabs.
      const tabW = 160;
      if (ui.button("War Chests", W / 2 - tabW - 6, 112, tabW, 36, { accent: this.tab === "chests" })) {
        this.tab = "chests";
        audio.play("ui");
      }
      if (ui.button("Collection", W / 2 + 6, 112, tabW, 36, { accent: this.tab === "collection" })) {
        this.tab = "collection";
        audio.play("ui");
      }

      if (this.tab === "chests") this.drawChests(W, H, profile);
      else this.drawCollection(W, H, profile);

      if (ui.button("⟵ Back", 24, H - 68, 130, 44, { size: 15 })) action = "back";
    } else {
      this.drawOpening(W, H, dt, profile);
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

export class PostMatchScreen {
  private xpAnim = 0;

  reset() {
    this.xpAnim = 0;
  }

  draw(
    W: number,
    H: number,
    time: number,
    dt: number,
    won: boolean,
    stats: { unitsKilled: number; unitsLost: number; buildingsRazed: number; gathered: number },
    durationSec: number,
    rewards: MatchRewards,
    profile: Profile,
    xpBefore: number,
    levelsGained: number,
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
    const lines: [string, string][] = [
      ["Duration", `${mins}:${secs.toString().padStart(2, "0")}`],
      ["Enemies slain", String(stats.unitsKilled)],
      ["Units lost", String(stats.unitsLost)],
      ["Buildings razed", String(stats.buildingsRazed)],
      ["Resources gathered", String(stats.gathered)],
    ];
    let ly = y0 + 60;
    for (const [k, v] of lines) {
      ui.text(k, x0 + 20, ly, { size: 14, color: "#bdb49a" });
      ui.text(v, x0 + colW - 20, ly, { size: 14, bold: true, align: "right" });
      ly += 32;
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
    ui.text(`+${rewards.xp} XP   +${rewards.renown} ✦`, x1 + colW - 20, ly, {
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

    if (ui.button("Continue", W / 2 - 110, H - 90, 220, 48, { accent: true, size: 18 })) {
      return "continue";
    }
    return null;
  }
}
