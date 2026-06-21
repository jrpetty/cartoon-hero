// Warband Tactics — the auto-battler screen. Draws a WarbandRun: standings,
// the board (a TFT-style tiled arena where your warband and the enemy's stand
// and then fight with full unit animations), your bench, and the shop/fight
// flow. Presentation only; all rules live in sim/warband.ts and the fight is a
// real, watchable combat sim (sim/autobattle.ts → LiveBattle).

import { ui } from "./ui";
import { PAL, withAlpha } from "../render/palette";
import { drawUnit, setTeamColorResolver } from "../render/draw";
import { Kind } from "../sim/types";
import { TILE } from "../content/balance";
import { UNITS } from "../content/units";
import { WarbandRun, UNIT_TIER, Piece } from "../sim/warband";
import { LiveBattle, GRID_COLS, GRID_ROWS, GRID_CELL } from "../sim/autobattle";
import { traitsOf } from "../sim/traits";
import { ITEMS } from "../sim/items";

const TIER_COLOR = ["#888888", "#9aa8b4", "#4caf50", "#3a78d8", "#9b5cf0", "#e0a020"];
const shortName = (type: string) => (UNITS[type]?.name ?? type).split(" ")[0];
const stars = (n: number) => "★".repeat(n);
const BATTLE_SPEED = 2; // sim-time multiplier while watching a fight

export class WarbandScreen {
  private selectedItem = -1; // index into the run's item stash, or -1
  private battle: LiveBattle | null = null;
  private battleSig = "";
  private stepAccum = 0;
  private lastTime = 0;
  private heldPiece = -1; // piece index "picked up" for placement, or -1

  draw(W: number, H: number, time: number, run: WarbandRun): "exit" | null {
    const ctx = ui.ctx;
    ctx.fillStyle = "#15110b";
    ctx.fillRect(0, 0, W, H);

    const dt = this.lastTime ? Math.min(0.1, time - this.lastTime) : 0;
    this.lastTime = time;

    let action: "exit" | null = null;

    // ---- header ----
    ctx.fillStyle = "rgba(8,6,3,0.9)";
    ctx.fillRect(0, 0, W, 56);
    ui.text("⚔ Warband Tactics", 20, 34, { size: 22, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });
    const stat = (label: string, val: string, x: number, col = "#e7ddc4") => {
      ui.text(label, x, 22, { size: 11, color: "#9a917b" });
      ui.text(val, x, 42, { size: 18, bold: true, color: col });
    };
    stat("ROUND", String(run.round), 280);
    stat("GOLD", String(run.gold), 360, "#ffd24a");
    stat("LEVEL", String(run.level), 440);
    stat("LIFE", String(run.life), 520, run.life <= 25 ? "#e0564a" : "#7df2a9");
    stat("STREAK", (run.streak > 0 ? "+" : "") + run.streak, 600, run.streak > 0 ? "#7df2a9" : run.streak < 0 ? "#e0a878" : "#9a917b");
    if (ui.button("Quit Run", W - 120, 12, 100, 32, { danger: true, size: 13 })) action = "exit";

    // ---- standings sidebar ----
    const sx = 12;
    let sy = 80;
    ui.text("Standings", sx, sy, { size: 14, bold: true, color: PAL.uiAccent });
    sy += 18;
    for (const s of run.standings()) {
      const h = 26;
      ctx.fillStyle = s.you ? withAlpha(PAL.uiAccent, 0.16) : "rgba(0,0,0,0.25)";
      ctx.fillRect(sx, sy, 200, h);
      ui.text((s.alive ? "" : "☠ ") + s.name, sx + 8, sy + 17, {
        size: 12, bold: s.you, color: s.alive ? (s.you ? "#ffe9b0" : "#e7ddc4") : "#6f6a5c",
      });
      ui.bar(sx + 110, sy + 9, 82, 9, Math.max(0, s.life) / 100, s.alive ? "#7df2a9" : "#5a554d");
      sy += h + 3;
    }

    // ---- synergies ----
    sy += 12;
    ui.text("Synergies", sx, sy, { size: 14, bold: true, color: PAL.uiAccent });
    sy += 16;
    const traits = run.activeTraits();
    if (!traits.length) ui.text("— none active —", sx, sy + 6, { size: 11, color: "#6f6a5c" });
    for (const at of traits) {
      const th = 30;
      ctx.fillStyle = withAlpha(at.trait.color, 0.14);
      ctx.fillRect(sx, sy, 200, th);
      ctx.fillStyle = at.trait.color;
      ctx.fillRect(sx, sy, 3, th);
      ui.text(`${at.trait.name}`, sx + 10, sy + 13, { size: 12, bold: true, color: at.trait.color });
      ui.text(`×${at.count}`, sx + 192, sy + 13, { size: 12, align: "right", color: "#e7ddc4" });
      ui.text(at.tier?.label ?? "", sx + 10, sy + 26, { size: 10, color: "#cabfa4" });
      sy += th + 3;
    }

    // ---- board geometry ----
    const bx = 232;
    const boardX = bx;
    const boardY = 92;
    const boardW = W - boardX - 16;
    const benchH = 66;
    const shopTop = H - 132;
    const boardBottom = shopTop - benchH - 16;
    const boardH = boardBottom - boardY;

    // Keep a battle world around for the current matchup: during shop it's a
    // static preview (both warbands standing on the board); FIGHT begins it.
    if (run.phase === "shop") {
      const sig = this.sig(run);
      if (sig !== this.battleSig || !this.battle) {
        this.battle = new LiveBattle(run.boardUnits(), run.pendingOpp, run.pendingSeed);
        this.battleSig = sig;
      }
    }
    // Advance the live fight in real (scaled) time, then bank the result.
    if (run.phase === "battle" && this.battle) {
      if (!this.battle.started) this.battle.begin(); // safety: always marching once fighting
      this.stepAccum += dt * 20 * BATTLE_SPEED; // 20 = SIM_HZ
      const n = Math.floor(this.stepAccum);
      if (n > 0) { this.stepAccum -= n; this.battle.step(n); }
      if (this.battle.done) run.finishFight(this.battle.result());
    }

    // ---- board title + enemy banner ----
    ui.text(`Your Warband  ·  deploying top ${run.deployCount()}`, boardX, 80, { size: 14, bold: true, color: PAL.uiAccent });
    ui.text(`vs ${run.pendingFoeName()}`, boardX + boardW, 80, { size: 13, bold: true, align: "right", color: "#e0786a" });

    this.drawBoard(boardX, boardY, boardW, boardH, time, dt, run);

    // ---- bench (your pieces) + relic tray ----
    const benchY = boardBottom + 8;
    ui.text("Bench", boardX, benchY - 2, { size: 12, bold: true, color: "#9a917b" });
    if (run.phase === "shop") {
      let tx = boardX + 60;
      run.itemStash.forEach((id, idx) => {
        const it = ITEMS[id];
        if (!it) return;
        const cw = 56;
        const cyy = benchY - 18;
        const sel = this.selectedItem === idx;
        const hov = ui.mx >= tx && ui.mx <= tx + cw && ui.my >= cyy && ui.my <= cyy + 16;
        ctx.fillStyle = sel ? withAlpha(it.color, 0.5) : hov ? withAlpha(it.color, 0.3) : withAlpha(it.color, 0.16);
        ctx.fillRect(tx, cyy, cw, 16);
        ctx.strokeStyle = it.color; ctx.lineWidth = sel ? 2 : 1; ctx.strokeRect(tx + 0.5, cyy + 0.5, cw - 1, 15);
        ui.text(it.short, tx + cw / 2, cyy + 12, { align: "center", size: 10, bold: true, color: it.color });
        if (hov && ui.clicked && !ui.pointerConsumed) { ui.pointerConsumed = true; this.selectedItem = sel ? -1 : idx; }
        tx += cw + 5;
      });
      if (this.selectedItem >= 0 && this.selectedItem < run.itemStash.length) {
        ui.text("→ click a unit to equip", tx + 6, benchY - 6, { size: 11, bold: true, color: "#ffd24a" });
      } else this.selectedItem = -1;
    }

    const order = run.pieces.map((_, i) => i).sort((a, b) =>
      run.pieces[b].star - run.pieces[a].star || (UNIT_TIER[run.pieces[b].type] ?? 0) - (UNIT_TIER[run.pieces[a].type] ?? 0));
    const deployedSet = new Set(order.slice(0, run.deployCount()));
    const cardW = 78;
    const cardH = benchH - 6;
    run.pieces.forEach((p, i) => {
      const cx = boardX + i * (cardW + 6);
      if (cx + cardW > W - 16) return; // overflow guard (rare; bench is wide)
      this.pieceCard(cx, benchY + 14, cardW, cardH, p, deployedSet.has(i), () => {
        if (run.phase !== "shop") return;
        if (this.selectedItem >= 0 && this.selectedItem < run.itemStash.length) {
          if (run.equipItem(this.selectedItem, i)) this.selectedItem = -1;
        } else run.sell(i);
      });
    });
    if (run.pieces.length === 0) ui.text("Buy units from the shop below…", boardX, benchY + 40, { size: 13, color: "#9a917b" });

    // ---- shop / actions (bottom) ----
    const shopY = shopTop;
    ctx.fillStyle = "rgba(8,6,3,0.85)";
    ctx.fillRect(0, shopY - 8, W, H - shopY + 8);

    if (run.phase === "shop") {
      ui.text("Shop", bx, shopY + 6, { size: 13, bold: true, color: PAL.uiAccent });
      const sw = 120;
      run.shop.forEach((type, i) => {
        const cx = bx + i * (sw + 8);
        const cy = shopY + 14;
        if (type) this.shopCard(cx, cy, sw, 70, type, run.gold >= run.cost(type), () => run.buy(i));
        else { ctx.fillStyle = "rgba(0,0,0,0.25)"; ctx.fillRect(cx, cy, sw, 70); }
      });
      const rxx = bx + 5 * (120 + 8) + 8;
      if (ui.button("Reroll  (2g)", rxx, shopY + 14, 130, 32, { disabled: run.gold < 2, size: 13, tooltip: ["New shop", "Costs 2 gold."] })) run.reroll();
      if (ui.button(`Level Up  (4g)`, rxx, shopY + 52, 130, 32, { disabled: run.gold < 4 || run.level >= 9, size: 13, tooltip: ["+4 XP", "Raises board size & shop odds."] })) run.buyXp();
      if (ui.button("⚔ FIGHT", rxx, shopY + 92, 130, 36, { accent: true, size: 16, tooltip: ["Send your warband into the arena."] })) {
        if (run.beginFight()) {
          this.battle = new LiveBattle(run.boardUnits(), run.pendingOpp, run.pendingSeed);
          this.battle.begin();
          this.stepAccum = 0;
          this.heldPiece = -1;
        }
      }
    } else if (run.phase === "battle") {
      ui.text("⚔ Battle in progress…", W / 2, shopY + 40, { align: "center", size: 22, bold: true, color: "#ffd24a", font: "Georgia, serif" });
      ui.text(`vs ${run.pendingFoeName()}`, W / 2, shopY + 66, { align: "center", size: 14, color: "#d8cdb4" });
    } else if (run.phase === "result" && run.lastResult) {
      const r = run.lastResult;
      ui.text(r.won ? `Victory vs ${r.foe}!` : `Defeat vs ${r.foe}`, W / 2, shopY + 30, {
        align: "center", size: 24, bold: true, color: r.won ? "#7df2a9" : "#e0564a", font: "Georgia, serif",
      });
      ui.text(
        r.won ? `${r.youLeft} of your warband survived.` : `You lost ${r.dmg} life (${r.foeLeft} enemies left standing).`,
        W / 2, shopY + 58, { align: "center", size: 14, color: "#d8cdb4" },
      );
      if (ui.button("Continue ▶", W / 2 - 80, shopY + 80, 160, 40, { accent: true, size: 16 })) run.next();
    }

    // ---- run over ----
    if (run.phase === "over") {
      ctx.fillStyle = "rgba(6,4,2,0.78)";
      ctx.fillRect(0, 0, W, H);
      const won = run.outcome === "win";
      ui.text(won ? "WARBAND TRIUMPHANT" : "WARBAND BROKEN", W / 2, H / 2 - 60, {
        align: "center", size: 44, bold: true, color: won ? "#ffe9b0" : "#c87a72", font: "Georgia, serif",
      });
      ui.text(won ? "Last warband standing — the arena is yours." : `You placed #${run.placement()} of 8.`,
        W / 2, H / 2 - 12, { align: "center", size: 17, color: "#d8cdb4" });
      if (ui.button("Back to Menu", W / 2 - 110, H / 2 + 30, 220, 48, { accent: true, size: 18 })) action = "exit";
    }

    return action;
  }

  /** The tiled arena. In setup it shows your placement (enemy hidden); once the
   *  fight starts it reveals and renders the live, animated battle. */
  private drawBoard(x: number, y: number, w: number, h: number, time: number, dt: number, run: WarbandRun) {
    const ctx = ui.ctx;
    const setup = run.phase === "shop";
    const cellW = w / GRID_COLS;
    const cellH = h / GRID_ROWS;

    ctx.fillStyle = "#0c0a06";
    ctx.fillRect(x - 2, y - 2, w + 4, h + 4);
    // 10×10 placement grid: your half (left 5 cols) blue, enemy half (right) red.
    for (let c = 0; c < GRID_COLS; c++) {
      for (let r = 0; r < GRID_ROWS; r++) {
        const tx = x + c * cellW, ty = y + r * cellH;
        const dark = (c + r) % 2 === 0;
        const mine = c < GRID_COLS / 2;
        ctx.fillStyle = dark ? "#1a261f" : "#212e24";
        ctx.fillRect(tx, ty, cellW + 1, cellH + 1);
        ctx.fillStyle = withAlpha(mine ? "#3a78d8" : "#d8564a", dark ? 0.07 : 0.035);
        ctx.fillRect(tx, ty, cellW + 1, cellH + 1);
      }
    }
    ctx.strokeStyle = "rgba(0,0,0,0.22)"; ctx.lineWidth = 1;
    ctx.beginPath();
    for (let c = 1; c < GRID_COLS; c++) { ctx.moveTo(x + c * cellW, y); ctx.lineTo(x + c * cellW, y + h); }
    for (let r = 1; r < GRID_ROWS; r++) { ctx.moveTo(x, y + r * cellH); ctx.lineTo(x + w, y + r * cellH); }
    ctx.stroke();
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.32); ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(x + w / 2, y); ctx.lineTo(x + w / 2, y + h); ctx.stroke();
    ctx.strokeStyle = "rgba(0,0,0,0.5)"; ctx.lineWidth = 3;
    ctx.strokeRect(x + 1.5, y + 1.5, w - 3, h - 3);

    if (!this.battle) return;
    const b = this.battle;
    // World ↔ screen mapping: the 10×10 world grid maps onto the board rect, so
    // a unit on cell (c,r) sits exactly in that on-screen cell.
    const worldLeft = b.cx - (GRID_COLS / 2) * GRID_CELL;
    const worldTop = b.cy - (GRID_ROWS / 2) * GRID_CELL;
    const sxk = w / (GRID_COLS * GRID_CELL);
    const syk = h / (GRID_ROWS * GRID_CELL);
    const mapX = (wx: number) => x + (wx - worldLeft) * sxk;
    const mapY = (wy: number) => y + (wy - worldTop) * syk;
    const us = (cellH * 0.82) / TILE; // unit drawn ~80% of a cell tall

    // ---- placement interaction (setup only) ----
    const deployment = setup ? run.deployment() : [];
    if (setup) {
      const hovC = Math.floor((ui.mx - x) / cellW);
      const hovR = Math.floor((ui.my - y) / cellH);
      const inPlayer = ui.mx >= x && ui.mx < x + w && ui.my >= y && ui.my < y + h && hovC >= 0 && hovC <= 4 && hovR >= 0 && hovR <= 9;
      // Highlight the held unit's cell, then the hovered cell.
      const held = deployment.find((d) => d.index === this.heldPiece);
      if (held) {
        ctx.strokeStyle = "#ffd24a"; ctx.lineWidth = 2.5;
        ctx.strokeRect(x + held.col * cellW + 2, y + held.row * cellH + 2, cellW - 4, cellH - 4);
      }
      if (inPlayer) {
        ctx.fillStyle = withAlpha(this.heldPiece >= 0 ? "#ffd24a" : "#cfe0ff", 0.16);
        ctx.fillRect(x + hovC * cellW + 1, y + hovR * cellH + 1, cellW - 2, cellH - 2);
      }
      if (ui.clicked && !ui.pointerConsumed) {
        if (inPlayer) {
          ui.pointerConsumed = true;
          const occ = deployment.find((d) => d.col === hovC && d.row === hovR);
          if (this.heldPiece >= 0) { run.place(this.heldPiece, hovC, hovR); this.heldPiece = -1; }
          else if (occ) this.heldPiece = occ.index;
        } else this.heldPiece = -1;
      }
    }

    // ---- enemy fog (setup) ----
    if (setup) {
      ctx.fillStyle = "rgba(8,5,3,0.66)";
      ctx.fillRect(x + w / 2 + 1.5, y + 1.5, w / 2 - 3, h - 3);
      ui.text("🔒 Enemy warband hidden", x + w * 0.75, y + h / 2 - 8, { align: "center", size: 15, bold: true, color: "#caa" });
      ui.text("revealed when the battle begins", x + w * 0.75, y + h / 2 + 12, { align: "center", size: 12, color: "#8a8278" });
      ui.text(this.heldPiece >= 0 ? "Click a cell to place the unit" : "Click a unit, then a cell, to position your warband",
        x + w * 0.25, y + h - 12, { align: "center", size: 12, color: this.heldPiece >= 0 ? "#ffd24a" : "#9a917b" });
    }

    // ---- units ----
    const ents = b.world.entities
      .filter((e) => e.alive && e.kind === Kind.Unit && e.type !== "villager" && (!setup || e.team === 0))
      .sort((p, q) => p.y - q.y);
    setTeamColorResolver(null);
    ctx.save();
    ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip();
    for (const e of ents) {
      const sx = mapX(e.x), sy = mapY(e.y);
      ctx.save();
      ctx.translate(sx, sy + cellH * 0.20); // feet a touch below cell centre
      ctx.scale(us, us);
      ctx.translate(-e.x, -e.y);
      try { drawUnit(ctx, e, time, 0); } catch { /* never let one unit kill the frame */ }
      ctx.restore();
      const frac = Math.max(0, Math.min(1, e.hp / e.maxHp));
      if (!setup && frac < 1) {
        const barW = cellW * 0.5, barH = 3, byy = sy - cellH * 0.32;
        ctx.fillStyle = "rgba(0,0,0,0.75)"; ctx.fillRect(sx - barW / 2, byy, barW, barH);
        ctx.fillStyle = e.team === 0 ? "#5ad06a" : "#e0564a";
        ctx.fillRect(sx - barW / 2, byy, barW * frac, barH);
      }
    }
    ctx.restore();
  }

  /** A signature of the current matchup — rebuild the preview when it changes. */
  private sig(run: WarbandRun): string {
    const board = run.boardUnits().map((u) => `${u.type}${u.star ?? 1}@${u.col},${u.row}:${(u.items ?? []).join(",")}`).join("|");
    return `${run.round}:${run.pendingSeed}:${board}`;
  }

  private pieceCard(x: number, y: number, w: number, h: number, p: Piece, deployed: boolean, onClick: () => void) {
    const ctx = ui.ctx;
    const tier = UNIT_TIER[p.type] ?? 1;
    const hover = ui.mx >= x && ui.mx <= x + w && ui.my >= y && ui.my <= y + h;
    ctx.fillStyle = deployed ? "rgba(40,34,20,0.95)" : "rgba(18,14,9,0.85)";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = withAlpha(TIER_COLOR[tier], deployed ? 1 : 0.5);
    ctx.lineWidth = deployed ? 2 : 1;
    ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
    ui.text(shortName(p.type), x + w / 2, y + 18, { align: "center", size: 12, bold: true, color: "#e7ddc4" });
    ui.text(stars(p.star), x + w / 2, y + 34, { align: "center", size: 13, color: p.star >= 3 ? "#ffd24a" : p.star === 2 ? "#cfe0ff" : "#9a917b" });
    let ix = x + 5;
    for (const id of p.items) {
      ctx.fillStyle = ITEMS[id]?.color ?? "#fff";
      ctx.fillRect(ix, y + h - 9, 8, 6);
      ix += 10;
    }
    if (hover && ui.clicked && !ui.pointerConsumed) { ui.pointerConsumed = true; onClick(); }
  }

  private shopCard(x: number, y: number, w: number, h: number, type: string, affordable: boolean, onClick: () => void) {
    const ctx = ui.ctx;
    const tier = UNIT_TIER[type] ?? 1;
    const hover = ui.mx >= x && ui.mx <= x + w && ui.my >= y && ui.my <= y + h;
    ctx.fillStyle = affordable ? (hover ? "rgba(54,42,24,0.96)" : "rgba(28,22,13,0.92)") : "rgba(16,12,8,0.85)";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = withAlpha(TIER_COLOR[tier], 0.9);
    ctx.lineWidth = 2;
    ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
    ui.text(shortName(type), x + w / 2, y + 22, { align: "center", size: 14, bold: true, color: affordable ? "#e7ddc4" : "#6f6a5c" });
    const tt = traitsOf(type).slice(0, 2);
    let txx = x + 8;
    for (const tr of tt) {
      ui.text(tr.name, txx, y + 40, { size: 9.5, color: tr.color });
      txx += tr.name.length * 5.6 + 8;
    }
    ui.text(`Tier ${tier}`, x + 8, y + h - 10, { size: 10, color: TIER_COLOR[tier] });
    ui.text(`${tier}g`, x + w - 8, y + h - 10, { align: "right", size: 13, bold: true, color: affordable ? "#ffd24a" : "#7a6a3a" });
    if (hover && affordable && ui.clicked && !ui.pointerConsumed) { ui.pointerConsumed = true; onClick(); }
  }
}
