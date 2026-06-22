// Warband Tactics — the auto-battler screen. Draws a WarbandRun: standings,
// the board (a TFT-style tiled arena where your warband and the enemy's stand
// and then fight with full unit animations), your bench, and the shop/fight
// flow. Presentation only; all rules live in sim/warband.ts and the fight is a
// real, watchable combat sim (sim/autobattle.ts → LiveBattle).

import { ui } from "./ui";
import { isMouseDown } from "./screens";
import { WarbandFx } from "./warband_fx";
import { audio } from "../engine/audio";
import { PAL, withAlpha } from "../render/palette";
import { drawUnit, setTeamColorResolver } from "../render/draw";
import { Kind, Entity } from "../sim/types";
import { TILE } from "../content/balance";
import { UNITS } from "../content/units";
import { WarbandRun, UNIT_TIER, Piece } from "../sim/warband";
import { LiveBattle, GRID_COLS, GRID_ROWS, GRID_CELL } from "../sim/autobattle";
import { traitsOf, Buff } from "../sim/traits";
import { ITEMS, Item, applyItems } from "../sim/items";
import { ABILITIES } from "../content/abilities";

const TIER_COLOR = ["#888888", "#9aa8b4", "#4caf50", "#3a78d8", "#9b5cf0", "#e0a020"];
const STAR_MULT = [1, 1, 1.8, 3.2]; // hp/attack multiplier by star (matches the battle sim)
const shortName = (type: string) => (UNITS[type]?.name ?? type).split(" ")[0];
const stars = (n: number) => "★".repeat(n);
const BATTLE_SPEED = 2; // sim-time multiplier while watching a fight

export class WarbandScreen {
  private selectedItem = -1; // index into the run's item stash, or -1
  private battle: LiveBattle | null = null;
  private battleSig = "";
  private stepAccum = 0;
  private lastTime = 0;
  // Drag/click placement state.
  private heldPiece = -1;        // piece index "picked up" (for placement or selling), or -1
  private heldFromBoard = false; // true if it was lifted off a board cell (so it can be re-placed)
  private wasDown = false;       // pointer-held last frame (for press-edge detection)
  private pressEdge = false;     // pointer went down this frame
  private grabbedThisPress = false;
  private movedSincePress = false;
  private pressX = 0;
  private pressY = 0;
  private sellBox = { x: 0, y: 0, w: 0, h: 0 }; // last frame's sell-box rect
  private fx = new WarbandFx();
  private prevPhase = "shop";
  private itemTip: { id: string; x: number; y: number } | null = null;
  private unitTip: { p: Piece; x: number; y: number } | null = null;
  private liveTip: { e: Entity; x: number; y: number } | null = null; // hovered unit mid-fight

  draw(W: number, H: number, time: number, run: WarbandRun): "exit" | null {
    const ctx = ui.ctx;
    ctx.fillStyle = "#15110b";
    ctx.fillRect(0, 0, W, H);

    const dt = this.lastTime ? Math.min(0.1, time - this.lastTime) : 0;
    this.lastTime = time;

    // Pointer press/drag tracking (for grab-and-drop placement + selling).
    const down = isMouseDown();
    this.pressEdge = down && !this.wasDown;
    if (this.pressEdge) { this.pressX = ui.mx; this.pressY = ui.my; this.movedSincePress = false; this.grabbedThisPress = false; }
    if (down && (Math.abs(ui.mx - this.pressX) > 5 || Math.abs(ui.my - this.pressY) > 5)) this.movedSincePress = true;

    let action: "exit" | null = null;

    // ---- header ----
    const hg = ctx.createLinearGradient(0, 0, 0, 56);
    hg.addColorStop(0, "rgba(26,19,10,0.96)"); hg.addColorStop(1, "rgba(8,6,3,0.94)");
    ctx.fillStyle = hg; ctx.fillRect(0, 0, W, 56);
    ctx.fillStyle = withAlpha(PAL.uiAccent, 0.5); ctx.fillRect(0, 55, W, 1.5);
    ui.text("⚔ Warband Tactics", 20, 35, { size: 22, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });
    const stat = (label: string, val: string, x: number, col = "#e7ddc4", icon?: () => void) => {
      ctx.fillStyle = "rgba(0,0,0,0.3)"; this.roundRect(ctx, x - 10, 8, 74, 40, 6); ctx.fill();
      ui.text(label, x, 22, { size: 10, color: "#9a917b" });
      ui.text(val, icon ? x + 16 : x, 43, { size: 18, bold: true, color: col });
      if (icon) icon();
    };
    stat("ROUND", String(run.round), 280);
    stat("GOLD", String(run.gold), 360, "#ffd24a", () => {
      ctx.save(); ctx.shadowColor = "#ffd24a"; ctx.shadowBlur = 6;
      const cg = ctx.createRadialGradient(364, 35, 1, 366, 37, 7);
      cg.addColorStop(0, "#ffe89a"); cg.addColorStop(1, "#e0a52a");
      ctx.fillStyle = cg; ctx.beginPath(); ctx.arc(366, 37, 6.5, 0, Math.PI * 2); ctx.fill(); ctx.restore();
      ctx.strokeStyle = "#a8771e"; ctx.lineWidth = 1; ctx.beginPath(); ctx.arc(366, 37, 6.5, 0, Math.PI * 2); ctx.stroke();
    });
    stat("LEVEL", String(run.level), 452);
    stat("LIFE", String(run.life), 540, run.life <= 25 ? "#e0564a" : "#7df2a9");
    stat("STREAK", (run.streak > 0 ? "+" : "") + run.streak, 628, run.streak > 0 ? "#7df2a9" : run.streak < 0 ? "#e0a878" : "#9a917b");
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

    // ---- relics ----
    this.itemTip = null;
    if (run.phase === "shop") {
      sy += 14;
      ui.text("Relics", sx, sy, { size: 14, bold: true, color: PAL.uiAccent });
      ui.text("hover for details · click then a unit", sx + 56, sy, { size: 9.5, color: "#6f6a5c" });
      sy += 12;
      if (run.itemStash.length === 0) { ui.text("— none yet —", sx, sy + 12, { size: 11, color: "#6f6a5c" }); sy += 22; }
      const tile = 42, gap = 6, per = 4;
      run.itemStash.forEach((id, idx) => {
        const it = ITEMS[id];
        if (!it) return;
        const tilex = sx + (idx % per) * (tile + gap);
        const tiley = sy + Math.floor(idx / per) * (tile + gap);
        const sel = this.selectedItem === idx;
        const hov = ui.mx >= tilex && ui.mx <= tilex + tile && ui.my >= tiley && ui.my <= tiley + tile;
        const g = ctx.createLinearGradient(0, tiley, 0, tiley + tile);
        g.addColorStop(0, withAlpha(it.color, sel ? 0.5 : hov ? 0.36 : 0.22));
        g.addColorStop(1, "rgba(12,9,5,0.95)");
        if (sel || hov) { ctx.save(); ctx.shadowColor = it.color; ctx.shadowBlur = sel ? 12 : 7; }
        ctx.fillStyle = g; this.roundRect(ctx, tilex, tiley, tile, tile, 7); ctx.fill();
        if (sel || hov) ctx.restore();
        ctx.strokeStyle = withAlpha(it.color, sel ? 1 : 0.8); ctx.lineWidth = sel ? 2.5 : 1.5;
        this.roundRect(ctx, tilex + 1, tiley + 1, tile - 2, tile - 2, 6); ctx.stroke();
        this.itemIcon(ctx, tilex + tile / 2, tiley + tile / 2 - 2, tile * 0.5, it);
        if (hov) this.itemTip = { id, x: tilex + tile + 8, y: tiley };
        if (hov && ui.clicked && !ui.pointerConsumed) { ui.pointerConsumed = true; this.selectedItem = sel ? -1 : idx; }
      });
      if (run.itemStash.length) sy += Math.ceil(run.itemStash.length / per) * (tile + gap);
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
      if (this.battle.fxEvents.length) { this.fx.ingest(this.battle.fxEvents); this.battle.fxEvents.length = 0; }
      if (this.battle.done) run.finishFight(this.battle.result());
    }
    this.fx.update(dt);
    // Phase-transition stingers.
    if (run.phase !== this.prevPhase) {
      if (run.phase === "battle") audio.play("alert");
      else if (run.phase === "result" && run.lastResult) audio.play(run.lastResult.won ? "complete" : "collapse");
      else if (run.phase === "shop") this.fx.clear();
      this.prevPhase = run.phase;
    }

    // ---- board title + enemy banner ----
    ui.text(`Your Warband  ·  ${run.deployedCount()} / ${run.deployCount()} deployed`, boardX, 80, { size: 14, bold: true, color: PAL.uiAccent });
    ui.text(`vs ${run.pendingFoeName()}`, boardX + boardW, 80, { size: 13, bold: true, align: "right", color: "#e0786a" });

    this.drawBoard(boardX, boardY, boardW, boardH, time, dt, run);

    // ---- bench (your pieces) ----
    const benchY = boardBottom + 8;
    ui.text("Bench", boardX, benchY - 2, { size: 12, bold: true, color: "#9a917b" });
    if (this.selectedItem >= 0 && this.selectedItem < run.itemStash.length) {
      ui.text("Relic ready — click a unit to equip it", boardX + 52, benchY - 2, { size: 12, bold: true, color: "#ffd24a" });
    } else if (this.selectedItem >= run.itemStash.length) this.selectedItem = -1;

    // Bench = your reserve (non-deployed) pieces. Drag one onto the board to
    // field it, into the Sell box to sell it, or click with a relic to equip.
    const reserve = run.pieces.map((_, i) => i).filter((i) => !run.pieces[i].deployed);
    const cardW = 78;
    const cardH = benchH - 6;
    reserve.forEach((i, k) => {
      const cx = boardX + k * (cardW + 6);
      if (cx + cardW > W - 16) return; // overflow guard (rare; bench is wide)
      this.pieceCard(cx, benchY + 14, cardW, cardH, run.pieces[i], false, this.heldPiece === i);
      const over = ui.mx >= cx && ui.mx <= cx + cardW && ui.my >= benchY + 14 && ui.my <= benchY + 14 + cardH;
      if (run.phase === "shop" && over && !ui.pointerConsumed) {
        const equipping = this.selectedItem >= 0 && this.selectedItem < run.itemStash.length;
        if (equipping && ui.clicked) { ui.pointerConsumed = true; if (run.equipItem(this.selectedItem, i)) this.selectedItem = -1; }
        else if (!equipping && this.pressEdge && this.heldPiece < 0) {
          this.heldPiece = i; this.heldFromBoard = false; this.grabbedThisPress = true; ui.pointerConsumed = true; audio.play("select");
        }
      }
    });
    if (run.pieces.length === 0) ui.text("Buy units from the shop below…", boardX, benchY + 40, { size: 13, color: "#9a917b" });
    else if (reserve.length === 0) ui.text("(every unit is deployed — buy more or level up for a bigger board)", boardX, benchY + 40, { size: 12, color: "#6f6a5c" });

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
        if (type) this.shopCard(cx, cy, sw, 70, type, run.gold >= run.cost(type), run.poolCount(type), () => { if (run.buy(i)) audio.play("coin"); });
        else { ctx.fillStyle = "rgba(0,0,0,0.25)"; ctx.fillRect(cx, cy, sw, 70); }
      });
      const rxx = bx + 5 * (120 + 8) + 8;
      if (ui.button("Reroll  (2g)", rxx, shopY + 14, 130, 32, { disabled: run.gold < 2, size: 13, tooltip: ["New shop", "Costs 2 gold."] })) { if (run.reroll()) audio.play("ui"); }
      if (ui.button(`Level Up  (4g)`, rxx, shopY + 52, 130, 32, { disabled: run.gold < 4 || run.level >= 9, size: 13, tooltip: ["+4 XP", "Raises board size & shop odds."] })) { if (run.buyXp()) audio.play("levelup"); }
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

    // ---- held unit rides the cursor ----
    if (run.phase === "shop" && this.heldPiece >= 0 && this.heldPiece < run.pieces.length) {
      const p = run.pieces[this.heldPiece];
      const cwd = 92, chd = 28;
      const cxp = Math.min(ui.mx + 14, W - cwd - 6); // keep the label on-screen near edges
      const cyp = Math.min(ui.my + 6, H - chd - 6);
      ctx.fillStyle = "rgba(20,16,9,0.96)"; ctx.fillRect(cxp, cyp, cwd, chd);
      ctx.strokeStyle = "#ffd24a"; ctx.lineWidth = 1.5; ctx.strokeRect(cxp + 0.5, cyp + 0.5, cwd - 1, chd - 1);
      ui.text(`${shortName(p.type)} ${stars(p.star)}`, cxp + cwd / 2, cyp + 18, { align: "center", size: 12, bold: true, color: "#ffe9b0" });
    }
    if (run.phase !== "shop") this.heldPiece = -1; // never carry a held unit out of setup
    this.drawItemTip(W, H);
    this.drawUnitTip(W, H);
    this.drawLiveTip(W, H);
    this.wasDown = down;

    return action;
  }

  private roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  private brazier(ctx: CanvasRenderingContext2D, cx: number, cy: number, time: number) {
    ctx.fillStyle = "#1c150e"; ctx.beginPath(); ctx.ellipse(cx, cy + 2, 8, 4, 0, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#2a2118"; ctx.beginPath(); ctx.ellipse(cx, cy, 7, 3.5, 0, 0, Math.PI * 2); ctx.fill();
    const f = 0.72 + 0.28 * Math.sin(time * 9 + cx * 0.3);
    const f2 = 0.72 + 0.28 * Math.sin(time * 13 + cy * 0.3 + 1);
    ctx.save();
    ctx.shadowColor = "#ff9128"; ctx.shadowBlur = 18 * f; ctx.globalAlpha = 0.92;
    ctx.fillStyle = "#ff7a18"; ctx.beginPath(); ctx.ellipse(cx, cy - 7 * f, 4.4, 9.5 * f, 0, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#ffd862"; ctx.beginPath(); ctx.ellipse(cx, cy - 6 * f2, 2.3, 5.5 * f2, 0, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
  }

  /** The painterly stone arena: platform, team-tinted board, glowing centre line,
   *  vignette, ornate frame and flickering corner braziers. */
  private drawArena(x: number, y: number, w: number, h: number, cellW: number, cellH: number, time: number) {
    const ctx = ui.ctx;
    // Raised stone platform (drop shadow + base).
    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 20; ctx.shadowOffsetY = 7;
    ctx.fillStyle = "#0b0906"; this.roundRect(ctx, x - 6, y - 6, w + 12, h + 12, 12); ctx.fill();
    ctx.restore();

    // Painterly ground gradient.
    const g = ctx.createLinearGradient(0, y, 0, y + h);
    g.addColorStop(0, "#27372c"); g.addColorStop(0.5, "#1d2c23"); g.addColorStop(1, "#14201a");
    ctx.fillStyle = g; ctx.fillRect(x, y, w, h);

    // Checker + team tint, strongest at the centre line.
    for (let c = 0; c < GRID_COLS; c++) {
      const mine = c < GRID_COLS / 2;
      const tintA = Math.max(0.025, 0.11 - Math.abs(c - (GRID_COLS / 2 - 0.5)) * 0.012);
      for (let r = 0; r < GRID_ROWS; r++) {
        const tx = x + c * cellW, ty = y + r * cellH;
        if ((c + r) % 2 === 0) { ctx.fillStyle = "rgba(255,255,255,0.022)"; ctx.fillRect(tx, ty, cellW + 1, cellH + 1); }
        ctx.fillStyle = withAlpha(mine ? "#3f86e0" : "#e0584a", tintA);
        ctx.fillRect(tx, ty, cellW + 1, cellH + 1);
      }
    }
    // Grid lines (dark + faint highlight for an engraved look).
    ctx.strokeStyle = "rgba(0,0,0,0.28)"; ctx.lineWidth = 1; ctx.beginPath();
    for (let c = 1; c < GRID_COLS; c++) { ctx.moveTo(x + c * cellW, y); ctx.lineTo(x + c * cellW, y + h); }
    for (let r = 1; r < GRID_ROWS; r++) { ctx.moveTo(x, y + r * cellH); ctx.lineTo(x + w, y + r * cellH); }
    ctx.stroke();

    // Glowing animated centre divide.
    const mid = x + w / 2, pulse = 0.5 + 0.5 * Math.sin(time * 2.2);
    ctx.save();
    ctx.shadowColor = withAlpha(PAL.uiAccent, 0.85); ctx.shadowBlur = 10 + pulse * 10;
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.4 + pulse * 0.3); ctx.lineWidth = 2.5;
    ctx.beginPath(); ctx.moveTo(mid, y + 3); ctx.lineTo(mid, y + h - 3); ctx.stroke();
    ctx.restore();

    // Vignette.
    const vg = ctx.createRadialGradient(x + w / 2, y + h / 2, Math.min(w, h) * 0.22, x + w / 2, y + h / 2, Math.max(w, h) * 0.62);
    vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, "rgba(0,0,0,0.42)");
    ctx.fillStyle = vg; ctx.fillRect(x, y, w, h);

    // Ornate frame + corner braziers.
    ctx.strokeStyle = "rgba(0,0,0,0.6)"; ctx.lineWidth = 4; ctx.strokeRect(x + 2, y + 2, w - 4, h - 4);
    ctx.strokeStyle = withAlpha("#caa56a", 0.5); ctx.lineWidth = 1.5; ctx.strokeRect(x + 5.5, y + 5.5, w - 11, h - 11);
    for (const [bx2, by2] of [[x + 16, y + 15], [x + w - 16, y + 15], [x + 16, y + h - 15], [x + w - 16, y + h - 15]] as const) {
      this.brazier(ctx, bx2, by2, time);
    }
  }

  /** The tiled arena. In setup it shows your placement (enemy hidden); once the
   *  fight starts it reveals and renders the live, animated battle. */
  private drawBoard(x: number, y: number, w: number, h: number, time: number, dt: number, run: WarbandRun) {
    const ctx = ui.ctx;
    const setup = run.phase === "shop";
    const cellW = w / GRID_COLS;
    const cellH = h / GRID_ROWS;

    this.drawArena(x, y, w, h, cellW, cellH, time);

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
    const us = (cellH * 0.62) / TILE; // unit fits comfortably inside a cell

    // ---- enemy fog (setup) ----
    if (setup) {
      ctx.fillStyle = "rgba(8,5,3,0.66)";
      ctx.fillRect(x + w / 2 + 1.5, y + 1.5, w / 2 - 3, h - 3);
      ui.text("🔒 Enemy warband hidden", x + w * 0.74, y + 30, { align: "center", size: 15, bold: true, color: "#caa" });
      ui.text("revealed when the battle begins", x + w * 0.74, y + 50, { align: "center", size: 12, color: "#8a8278" });
    }

    // ---- placement + sell interaction (setup only) ----
    const deployment = setup ? run.deployment() : [];
    this.unitTip = null;
    let heldEntityId = -1;
    if (setup) {
      const hovC = Math.floor((ui.mx - x) / cellW);
      const hovR = Math.floor((ui.my - y) / cellH);
      const inPlayer = ui.mx >= x && ui.mx < x + w && ui.my >= y && ui.my < y + h && hovC >= 0 && hovC <= 4 && hovR >= 0 && hovR <= 9;
      const equipping = this.selectedItem >= 0 && this.selectedItem < run.itemStash.length;

      // Sell box, bottom-right corner of the board.
      const sbW = 156, sbH = 66;
      this.sellBox = { x: x + w - sbW - 14, y: y + h - sbH - 14, w: sbW, h: sbH };
      const overSell = ui.mx >= this.sellBox.x && ui.mx <= this.sellBox.x + sbW && ui.my >= this.sellBox.y && ui.my <= this.sellBox.y + sbH;
      const heldObj = this.heldPiece >= 0 ? run.pieces[this.heldPiece] : null;
      const hot = overSell && this.heldPiece >= 0;
      ctx.fillStyle = hot ? "rgba(210,70,58,0.55)" : "rgba(70,24,18,0.5)";
      ctx.fillRect(this.sellBox.x, this.sellBox.y, sbW, sbH);
      ctx.strokeStyle = hot ? "#ff7a68" : "#a8463c"; ctx.lineWidth = 2;
      ctx.setLineDash([6, 4]); ctx.strokeRect(this.sellBox.x + 1, this.sellBox.y + 1, sbW - 2, sbH - 2); ctx.setLineDash([]);
      ui.text("🗑 SELL", this.sellBox.x + sbW / 2, this.sellBox.y + 26, { align: "center", size: 15, bold: true, color: "#f0c0b6" });
      ui.text(heldObj ? `drop to sell  +${run.cost(heldObj.type) * heldObj.star}g` : "drag a unit here",
        this.sellBox.x + sbW / 2, this.sellBox.y + 46, { align: "center", size: 11, color: "#e0b0a6" });

      // Grab a board unit on press (when nothing held and not equipping a relic).
      if (this.pressEdge && this.heldPiece < 0 && inPlayer && !equipping && !ui.pointerConsumed) {
        const occ = deployment.find((d) => d.col === hovC && d.row === hovR);
        if (occ) { this.heldPiece = occ.index; this.heldFromBoard = true; this.grabbedThisPress = true; ui.pointerConsumed = true; audio.play("select"); }
      }
      // Equip a selected relic by clicking a board unit.
      if (ui.clicked && inPlayer && equipping && !ui.pointerConsumed) {
        const occ = deployment.find((d) => d.col === hovC && d.row === hovR);
        if (occ) { ui.pointerConsumed = true; if (run.equipItem(this.selectedItem, occ.index)) this.selectedItem = -1; }
      }

      // Highlights: the held unit's cell + the hovered target cell.
      const heldDep = deployment.find((d) => d.index === this.heldPiece);
      if (heldDep) {
        ctx.strokeStyle = "#ffd24a"; ctx.lineWidth = 2.5;
        ctx.strokeRect(x + heldDep.col * cellW + 2, y + heldDep.row * cellH + 2, cellW - 4, cellH - 4);
        // Dim the lifted unit on the board (it rides the cursor instead).
        const players = b.world.entities.filter((e) => e.alive && e.kind === Kind.Unit && e.type !== "villager" && e.team === 0).sort((p, q) => p.id - q.id);
        const k = deployment.findIndex((d) => d.index === this.heldPiece);
        if (k >= 0 && players[k]) heldEntityId = players[k].id;
      }
      if (inPlayer && this.heldPiece >= 0) {
        ctx.fillStyle = withAlpha("#ffd24a", 0.18);
        ctx.fillRect(x + hovC * cellW + 1, y + hovR * cellH + 1, cellW - 2, cellH - 2);
      }
      // Hovering a deployed unit (and not busy) → show its equipped relics.
      if (inPlayer && this.heldPiece < 0 && !equipping) {
        const occ = deployment.find((d) => d.col === hovC && d.row === hovR);
        if (occ) {
          ctx.strokeStyle = withAlpha("#cfe0ff", 0.6); ctx.lineWidth = 1.5;
          ctx.strokeRect(x + hovC * cellW + 2, y + hovR * cellH + 2, cellW - 4, cellH - 4);
          this.unitTip = { p: run.pieces[occ.index], x: x + (hovC + 1) * cellW + 6, y: y + hovR * cellH };
        }
      }

      // Resolve a release: sell / place / field / swap / keep-armed / cancel.
      if (ui.clicked && this.heldPiece >= 0 && !ui.pointerConsumed) {
        if (overSell) { if (run.sell(this.heldPiece)) audio.play("coin"); this.heldPiece = -1; ui.pointerConsumed = true; }
        else if (inPlayer && this.heldFromBoard) {
          ui.pointerConsumed = true;
          const sameCell = heldDep && heldDep.col === hovC && heldDep.row === hovR;
          if (sameCell && !this.movedSincePress && this.grabbedThisPress) { /* just picked up → keep armed */ }
          else if (sameCell && !this.movedSincePress) this.heldPiece = -1; // clicked its own cell again → put down
          else { run.place(this.heldPiece, hovC, hovR); audio.play("command"); this.heldPiece = -1; }
        } else if (inPlayer) {
          // A reserve (bench) unit dropped on the board → field it there.
          ui.pointerConsumed = true;
          if (run.place(this.heldPiece, hovC, hovR)) audio.play("command");
          this.heldPiece = -1;
        } else if (this.grabbedThisPress && !this.movedSincePress) {
          /* just clicked to pick it up off the bench → keep it armed for a click-to-place */
        } else this.heldPiece = -1; // dropped on nothing → cancel
      }

      ui.text(this.heldPiece >= 0 ? "Drop on a cell to place/field · drop on another unit to swap · drop on 🗑 to sell"
        : "Drag a reserve onto the board to field it · click a unit then a cell to move · drag to 🗑 to sell",
        x + w * 0.25, y + h - 12, { align: "center", size: 12, color: this.heldPiece >= 0 ? "#ffd24a" : "#9a917b" });
    }

    // ---- units + combat FX ----
    const ents = b.world.entities
      .filter((e) => e.alive && e.kind === Kind.Unit && e.type !== "villager" && (!setup || e.team === 0))
      .sort((p, q) => p.y - q.y);
    // In setup, draw each unit at the EXACT centre of its assigned arena cell.
    // (The sim snaps spawn positions to its 32-unit nav grid, which doesn't line
    //  up with the 40-unit board cells — so we use the cell, not the entity pos.)
    const cellByEnt = new Map<number, { col: number; row: number }>();
    const itemsByEnt = new Map<number, string[]>();
    if (setup) {
      const players = b.world.entities
        .filter((e) => e.alive && e.kind === Kind.Unit && e.type !== "villager" && e.team === 0)
        .sort((p, q) => p.id - q.id);
      players.forEach((e, k) => {
        const d = deployment[k];
        if (d) { cellByEnt.set(e.id, { col: d.col, row: d.row }); itemsByEnt.set(e.id, run.pieces[d.index].items); }
      });
    }
    setTeamColorResolver(null);
    ctx.save();
    ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip();
    // Screen-shake the contents (not the frame) during heavy combat.
    const sh = this.fx.shake;
    if (sh > 0.1) ctx.translate((Math.random() * 2 - 1) * sh, (Math.random() * 2 - 1) * sh);
    const starGlow = ["#9aa8b4", "#cfe0ff", "#ffd24a"]; // 1★ / 2★ / 3★
    if (!setup) this.liveTip = null; // recomputed each frame from the hovered unit
    let hoverDist = Infinity;
    for (const e of ents) {
      const cell = cellByEnt.get(e.id);
      const sx = cell ? x + (cell.col + 0.5) * cellW : mapX(e.x);
      const sy = cell ? y + (cell.row + 0.5) * cellH : mapY(e.y);
      const lifted = e.id === heldEntityId;
      // Anchor the feet below the cell centre so the unit's BODY sits in the
      // middle of the square (the sprite is feet-anchored and rises upward).
      const footY = sy + cellH * 0.24;
      const star = (e.variantRarity ?? 0) + 1;
      // Soft drop shadow at the feet.
      ctx.globalAlpha = lifted ? 0.12 : 0.34;
      ctx.fillStyle = "#000";
      ctx.beginPath(); ctx.ellipse(sx, footY, cellW * 0.2, cellH * 0.1, 0, 0, Math.PI * 2); ctx.fill();
      ctx.globalAlpha = 1;
      // Star-tier ring at the feet for 2★/3★.
      if (star >= 2) {
        ctx.save();
        ctx.strokeStyle = withAlpha(starGlow[Math.min(2, star - 1)], 0.9);
        ctx.shadowColor = starGlow[Math.min(2, star - 1)]; ctx.shadowBlur = 8; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.ellipse(sx, footY, cellW * 0.22, cellH * 0.11, 0, 0, Math.PI * 2); ctx.stroke();
        ctx.restore();
      }
      ctx.save();
      ctx.globalAlpha = lifted ? 0.3 : 1; // lifted unit rides the cursor
      ctx.translate(sx, footY); // feet here → body centres on the square
      ctx.scale(us, us);
      ctx.translate(-e.x, -e.y);
      try { drawUnit(ctx, e, time, 0); } catch { /* never let one unit kill the frame */ }
      ctx.restore();
      // Star pips above the unit in setup so tiers read at a glance.
      if (setup && star >= 2 && !lifted) {
        for (let s = 0; s < star; s++) {
          ctx.fillStyle = starGlow[Math.min(2, star - 1)];
          ctx.beginPath(); ctx.arc(sx - (star - 1) * 4 + s * 8, sy - cellH * 0.28, 2.4, 0, Math.PI * 2); ctx.fill();
        }
      }
      // Equipped relic icons under the unit so you can see its gear at a glance.
      const eqItems = setup ? itemsByEnt.get(e.id) : undefined;
      if (eqItems && eqItems.length && !lifted) {
        const r = Math.min(7, cellW * 0.1), gap = r * 2.2;
        let ix = sx - (eqItems.length - 1) * gap / 2;
        for (const id of eqItems) {
          const it = ITEMS[id]; if (!it) continue;
          ctx.fillStyle = "rgba(8,6,3,0.92)"; ctx.beginPath(); ctx.arc(ix, footY + cellH * 0.16, r, 0, Math.PI * 2); ctx.fill();
          ctx.strokeStyle = it.color; ctx.lineWidth = 1; ctx.stroke();
          this.itemIcon(ctx, ix, footY + cellH * 0.16, r * 0.72, it);
          ix += gap;
        }
      }
      const frac = Math.max(0, Math.min(1, e.hp / e.maxHp));
      if (!setup) {
        // Always-on health bar during the fight (team-coloured), so you can read
        // every unit's health live.
        const barW = cellW * 0.5, barH = 3.5, byy = sy - cellH * 0.26;
        ctx.fillStyle = "rgba(0,0,0,0.78)"; ctx.fillRect(sx - barW / 2 - 1, byy - 1, barW + 2, barH + 2);
        ctx.fillStyle = "rgba(0,0,0,0.45)"; ctx.fillRect(sx - barW / 2, byy, barW, barH);
        ctx.fillStyle = e.team === 0 ? "#5ad06a" : "#e0564a";
        ctx.fillRect(sx - barW / 2, byy, barW * frac, barH);
        // Track the unit nearest the cursor for a live hover readout.
        const d = Math.hypot(ui.mx - sx, ui.my - sy);
        if (d < hoverDist && d < cellH * 0.8) { hoverDist = d; this.liveTip = { e, x: sx + cellW * 0.4, y: sy - cellH * 0.6 }; }
      }
      // Ability charge bar (battle only) for units with a signature ability.
      if (!setup && ABILITIES[e.type] && e.type !== "villager") {
        const ab = ABILITIES[e.type];
        const charge = e.abilityActive > 0 ? 1 : Math.max(0, Math.min(1, b.chargeOf(e.id)));
        const barW = cellW * 0.5, barH = 2.5, byy = sy - cellH * 0.26 + 5;
        ctx.fillStyle = "rgba(0,0,0,0.7)"; ctx.fillRect(sx - barW / 2 - 1, byy - 1, barW + 2, barH + 2);
        ctx.fillStyle = e.abilityActive > 0 ? "#fff" : withAlpha(ab.color, 0.95);
        ctx.fillRect(sx - barW / 2, byy, barW * charge, barH);
      }
    }
    // Combat FX (sparks + floating damage) on top of the units.
    this.fx.draw(ctx, mapX, mapY, (sxk + syk) / 2);
    ctx.restore();
  }

  /** A signature of the current matchup — rebuild the preview when it changes. */
  private sig(run: WarbandRun): string {
    const board = run.boardUnits().map((u) => `${u.type}${u.star ?? 1}@${u.col},${u.row}:${(u.items ?? []).join(",")}`).join("|");
    return `${run.round}:${run.pendingSeed}:${board}`;
  }

  private pieceCard(x: number, y: number, w: number, h: number, p: Piece, deployed: boolean, held = false) {
    const ctx = ui.ctx;
    const tier = UNIT_TIER[p.type] ?? 1;
    const hover = ui.mx >= x && ui.mx <= x + w && ui.my >= y && ui.my <= y + h;
    ctx.globalAlpha = held ? 0.4 : 1; // a lifted unit dims on its bench slot
    const g = ctx.createLinearGradient(0, y, 0, y + h);
    g.addColorStop(0, withAlpha(TIER_COLOR[tier], hover ? 0.26 : 0.16));
    g.addColorStop(1, "rgba(14,10,6,0.95)");
    ctx.fillStyle = g; this.roundRect(ctx, x, y, w, h, 6); ctx.fill();
    ctx.strokeStyle = withAlpha(TIER_COLOR[tier], hover ? 0.95 : 0.6);
    ctx.lineWidth = 1.5; this.roundRect(ctx, x + 0.75, y + 0.75, w - 1.5, h - 1.5, 6); ctx.stroke();
    ui.text(shortName(p.type), x + w / 2, y + 18, { align: "center", size: 12, bold: true, color: "#e7ddc4" });
    ui.text(stars(p.star), x + w / 2, y + 34, { align: "center", size: 13, color: p.star >= 3 ? "#ffd24a" : p.star === 2 ? "#cfe0ff" : "#9a917b" });
    // Equipped relics as mini icons along the bottom.
    let ix = x + 11;
    for (const id of p.items) {
      const it = ITEMS[id]; if (!it) continue;
      ctx.fillStyle = "rgba(8,6,3,0.9)"; ctx.beginPath(); ctx.arc(ix, y + h - 8, 6, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = it.color; ctx.lineWidth = 1; ctx.stroke();
      this.itemIcon(ctx, ix, y + h - 8, 4.3, it);
      ix += 14;
    }
    ctx.globalAlpha = 1;
    if (hover && this.heldPiece < 0) this.unitTip = { p, x: x + w + 6, y: y - 70 };
  }

  private shopCard(x: number, y: number, w: number, h: number, type: string, affordable: boolean, poolLeft: number, onClick: () => void) {
    const ctx = ui.ctx;
    const tier = UNIT_TIER[type] ?? 1;
    const col = TIER_COLOR[tier];
    const hover = ui.mx >= x && ui.mx <= x + w && ui.my >= y && ui.my <= y + h;
    const oy = hover && affordable ? -3 : 0; // lift on hover
    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.55)"; ctx.shadowBlur = hover && affordable ? 12 : 5; ctx.shadowOffsetY = 4;
    const g = ctx.createLinearGradient(0, y + oy, 0, y + oy + h);
    g.addColorStop(0, withAlpha(col, affordable ? 0.3 : 0.1));
    g.addColorStop(0.45, "rgba(26,20,12,0.96)");
    g.addColorStop(1, "rgba(13,9,5,0.98)");
    ctx.fillStyle = g; this.roundRect(ctx, x, y + oy, w, h, 8); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = withAlpha(col, affordable ? 0.95 : 0.4); ctx.lineWidth = 2;
    this.roundRect(ctx, x + 1, y + oy + 1, w - 2, h - 2, 7); ctx.stroke();
    // Tier accent bar along the top.
    ctx.fillStyle = withAlpha(col, affordable ? 1 : 0.5);
    ctx.fillRect(x + 6, y + oy + 4, w - 12, 2.5);
    ui.text(shortName(type), x + w / 2, y + oy + 26, { align: "center", size: 14, bold: true, color: affordable ? "#f2e8d0" : "#6f6a5c" });
    const tt = traitsOf(type).slice(0, 2);
    let txx = x + 10;
    for (const tr of tt) { ui.text(tr.name, txx, y + oy + 44, { size: 9.5, color: affordable ? tr.color : withAlpha(tr.color, 0.5) }); txx += tr.name.length * 5.6 + 8; }
    ui.text(`Tier ${tier}`, x + 10, y + oy + h - 10, { size: 10, color: withAlpha(col, affordable ? 1 : 0.5) });
    // Copies left in the shared lobby pool.
    ui.text(`${poolLeft} left`, x + w / 2, y + oy + h - 10, { align: "center", size: 9.5, color: poolLeft <= 3 ? "#e0786a" : "#8a8278" });
    // Gold coin with the cost.
    this.coin(ctx, x + w - 18, y + oy + h - 13, tier, affordable);
    if (hover) this.unitTip = { p: { type, star: 1, items: [] }, x: x + w + 6, y: y - 150 };
    if (hover && affordable && ui.clicked && !ui.pointerConsumed) { ui.pointerConsumed = true; onClick(); }
  }

  /** A small gold coin glyph with a number on it. */
  private coin(ctx: CanvasRenderingContext2D, cx: number, cy: number, n: number, bright = true) {
    ctx.save();
    if (bright) { ctx.shadowColor = "#ffd24a"; ctx.shadowBlur = 6; }
    const g = ctx.createRadialGradient(cx - 2, cy - 2, 1, cx, cy, 8);
    g.addColorStop(0, bright ? "#ffe89a" : "#9a8a55"); g.addColorStop(1, bright ? "#e0a52a" : "#6a5a30");
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, 7.5, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = bright ? "#a8771e" : "#544622"; ctx.lineWidth = 1; ctx.beginPath(); ctx.arc(cx, cy, 7.5, 0, Math.PI * 2); ctx.stroke();
    ui.text(String(n), cx, cy + 4, { align: "center", size: 11, bold: true, color: bright ? "#5a3e08" : "#3a3018" });
  }

  /** A distinct procedural glyph per relic, drawn centred at (cx,cy), half-size s. */
  private itemIcon(ctx: CanvasRenderingContext2D, cx: number, cy: number, s: number, it: Item) {
    ctx.save();
    ctx.translate(cx, cy);
    ctx.lineJoin = "round"; ctx.lineCap = "round";
    ctx.fillStyle = it.color; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = Math.max(1, s * 0.12);
    const h = s;
    switch (it.id) {
      case "whetstone": // sword
        ctx.beginPath();
        ctx.moveTo(0, -h); ctx.lineTo(h * 0.22, -h * 0.2); ctx.lineTo(h * 0.22, h * 0.32);
        ctx.lineTo(-h * 0.22, h * 0.32); ctx.lineTo(-h * 0.22, -h * 0.2); ctx.closePath(); ctx.fill(); ctx.stroke();
        ctx.fillStyle = "#caa56a"; ctx.beginPath(); ctx.rect(-h * 0.55, h * 0.3, h * 1.1, h * 0.18); ctx.fill(); ctx.stroke();
        ctx.fillStyle = "#6a4a2a"; ctx.beginPath(); ctx.rect(-h * 0.12, h * 0.48, h * 0.24, h * 0.42); ctx.fill(); ctx.stroke();
        break;
      case "greatmail": // shield
        ctx.beginPath();
        ctx.moveTo(0, -h); ctx.lineTo(h * 0.8, -h * 0.55); ctx.lineTo(h * 0.8, h * 0.2);
        ctx.quadraticCurveTo(h * 0.8, h * 0.92, 0, h);
        ctx.quadraticCurveTo(-h * 0.8, h * 0.92, -h * 0.8, h * 0.2);
        ctx.lineTo(-h * 0.8, -h * 0.55); ctx.closePath(); ctx.fill(); ctx.stroke();
        ctx.strokeStyle = "rgba(0,0,0,0.4)"; ctx.lineWidth = s * 0.16;
        ctx.beginPath(); ctx.moveTo(0, -h * 0.5); ctx.lineTo(0, h * 0.6); ctx.moveTo(-h * 0.5, -h * 0.05); ctx.lineTo(h * 0.5, -h * 0.05); ctx.stroke();
        break;
      case "warhorn": // drinking horn (crescent)
        ctx.beginPath();
        ctx.arc(h * 0.35, h * 0.35, h * 1.0, Math.PI * 1.02, Math.PI * 1.72, false);
        ctx.arc(h * 0.35, h * 0.35, h * 0.5, Math.PI * 1.72, Math.PI * 1.02, true);
        ctx.closePath(); ctx.fill(); ctx.stroke();
        break;
      case "swiftboots": // lightning bolt
        ctx.beginPath();
        ctx.moveTo(h * 0.2, -h); ctx.lineTo(-h * 0.5, h * 0.12); ctx.lineTo(-h * 0.05, h * 0.12);
        ctx.lineTo(-h * 0.25, h); ctx.lineTo(h * 0.55, -h * 0.2); ctx.lineTo(h * 0.05, -h * 0.2);
        ctx.closePath(); ctx.fill(); ctx.stroke();
        break;
      case "giantsbelt": // belt + buckle
        ctx.beginPath(); ctx.rect(-h, -h * 0.32, h * 2, h * 0.64); ctx.fill(); ctx.stroke();
        ctx.fillStyle = "#caa56a"; ctx.beginPath(); ctx.rect(-h * 0.32, -h * 0.46, h * 0.64, h * 0.92); ctx.fill(); ctx.stroke();
        ctx.strokeStyle = "rgba(0,0,0,0.5)"; ctx.lineWidth = s * 0.12; ctx.beginPath(); ctx.rect(-h * 0.15, -h * 0.24, h * 0.3, h * 0.48); ctx.stroke();
        break;
      case "warbanner": // banner on a pole
        ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = s * 0.18;
        ctx.beginPath(); ctx.moveTo(-h * 0.55, -h); ctx.lineTo(-h * 0.55, h); ctx.stroke();
        ctx.fillStyle = it.color; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = Math.max(1, s * 0.1);
        ctx.beginPath();
        ctx.moveTo(-h * 0.55, -h * 0.9); ctx.lineTo(h * 0.78, -h * 0.9); ctx.lineTo(h * 0.46, -h * 0.3);
        ctx.lineTo(h * 0.78, h * 0.3); ctx.lineTo(-h * 0.55, h * 0.3); ctx.closePath(); ctx.fill(); ctx.stroke();
        break;
      case "bloodaxe": // battle axe
        ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = s * 0.18;
        ctx.beginPath(); ctx.moveTo(h * 0.1, h); ctx.lineTo(-h * 0.05, -h); ctx.stroke();
        ctx.fillStyle = it.color; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = Math.max(1, s * 0.1);
        ctx.beginPath();
        ctx.moveTo(-h * 0.05, -h * 0.85); ctx.quadraticCurveTo(h * 0.95, -h * 0.95, h * 0.7, -h * 0.1);
        ctx.quadraticCurveTo(h * 0.5, -h * 0.4, -h * 0.05, -h * 0.35); ctx.closePath(); ctx.fill(); ctx.stroke();
        break;
      case "ironhide": // great helm
        ctx.beginPath();
        ctx.moveTo(-h * 0.6, -h * 0.5); ctx.quadraticCurveTo(0, -h, h * 0.6, -h * 0.5);
        ctx.lineTo(h * 0.6, h * 0.65); ctx.quadraticCurveTo(0, h * 0.95, -h * 0.6, h * 0.65); ctx.closePath();
        ctx.fill(); ctx.stroke();
        ctx.strokeStyle = "rgba(0,0,0,0.45)"; ctx.lineWidth = s * 0.16;
        ctx.beginPath(); ctx.moveTo(0, -h * 0.5); ctx.lineTo(0, h * 0.6); ctx.moveTo(-h * 0.55, 0); ctx.lineTo(h * 0.55, 0); ctx.stroke();
        break;
      case "berserkbrew": // potion flask
        ctx.fillStyle = "#5a3a20"; ctx.beginPath(); ctx.rect(-h * 0.18, -h, h * 0.36, h * 0.45); ctx.fill(); ctx.stroke();
        ctx.fillStyle = it.color;
        ctx.beginPath();
        ctx.moveTo(-h * 0.18, -h * 0.55); ctx.lineTo(h * 0.18, -h * 0.55);
        ctx.quadraticCurveTo(h * 0.85, h * 0.2, h * 0.45, h * 0.8);
        ctx.quadraticCurveTo(0, h * 1.05, -h * 0.45, h * 0.8);
        ctx.quadraticCurveTo(-h * 0.85, h * 0.2, -h * 0.18, -h * 0.55); ctx.closePath(); ctx.fill(); ctx.stroke();
        ctx.fillStyle = "rgba(255,255,255,0.35)"; ctx.beginPath(); ctx.ellipse(-h * 0.18, h * 0.35, h * 0.16, h * 0.28, -0.4, 0, Math.PI * 2); ctx.fill();
        break;
      case "towershield": // tall kite/tower shield
        ctx.beginPath();
        ctx.moveTo(-h * 0.6, -h * 0.85); ctx.lineTo(h * 0.6, -h * 0.85); ctx.lineTo(h * 0.6, h * 0.25);
        ctx.quadraticCurveTo(h * 0.6, h * 0.95, 0, h); ctx.quadraticCurveTo(-h * 0.6, h * 0.95, -h * 0.6, h * 0.25);
        ctx.closePath(); ctx.fill(); ctx.stroke();
        ctx.strokeStyle = "rgba(0,0,0,0.4)"; ctx.lineWidth = s * 0.14;
        ctx.beginPath(); ctx.moveTo(-h * 0.5, -h * 0.45); ctx.lineTo(h * 0.5, -h * 0.45); ctx.moveTo(0, -h * 0.8); ctx.lineTo(0, h * 0.85); ctx.stroke();
        break;
      case "warlordcrest": // crown
        ctx.beginPath();
        ctx.moveTo(-h * 0.8, h * 0.5); ctx.lineTo(-h * 0.8, -h * 0.4); ctx.lineTo(-h * 0.4, h * 0.05);
        ctx.lineTo(0, -h * 0.7); ctx.lineTo(h * 0.4, h * 0.05); ctx.lineTo(h * 0.8, -h * 0.4);
        ctx.lineTo(h * 0.8, h * 0.5); ctx.closePath(); ctx.fill(); ctx.stroke();
        ctx.fillStyle = "rgba(255,255,255,0.5)";
        for (const gx of [-0.8, 0, 0.8]) { ctx.beginPath(); ctx.arc(gx * h, -0.45 * h, h * 0.13, 0, Math.PI * 2); ctx.fill(); }
        break;
      case "windcloak": { // winged emblem
        ctx.beginPath(); ctx.arc(0, 0, h * 0.28, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
        for (const dir of [-1, 1]) {
          ctx.beginPath();
          ctx.moveTo(dir * h * 0.25, -h * 0.1);
          ctx.quadraticCurveTo(dir * h * 1.0, -h * 0.6, dir * h * 0.95, -h * 0.05);
          ctx.quadraticCurveTo(dir * h * 0.9, h * 0.1, dir * h * 0.25, h * 0.2); ctx.closePath(); ctx.fill(); ctx.stroke();
        }
        break;
      }
      default:
        ctx.beginPath(); ctx.arc(0, 0, h * 0.7, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    }
    ctx.restore();
  }

  /** Human-readable benefit lines for a relic's buff. */
  private buffLines(b: Buff): string[] {
    const out: string[] = [];
    if (b.atk) out.push(`+${b.atk} Attack`);
    if (b.atkPct) out.push(`+${b.atkPct}% Attack`);
    if (b.armor) out.push(`+${b.armor} Armour`);
    if (b.hp) out.push(`+${b.hp} Max HP`);
    if (b.hpPct) out.push(`+${b.hpPct}% Max HP`);
    if (b.speedPct) out.push(`+${b.speedPct}% Move Speed`);
    return out;
  }

  /** The relic hover tooltip — name, icon and exact benefits. Drawn last, on top. */
  private drawItemTip(W: number, H: number) {
    if (!this.itemTip) return;
    const ctx = ui.ctx;
    const it = ITEMS[this.itemTip.id];
    if (!it) return;
    const lines = this.buffLines(it.buff);
    const pw = 196, ph = 46 + lines.length * 17 + 16;
    let px = this.itemTip.x, py = this.itemTip.y;
    if (px + pw > W - 4) px = Math.max(4, this.itemTip.x - pw - 56);
    if (py + ph > H - 4) py = H - ph - 4;
    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 16; ctx.shadowOffsetY = 4;
    ctx.fillStyle = "rgba(18,14,9,0.98)"; this.roundRect(ctx, px, py, pw, ph, 9); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = withAlpha(it.color, 0.95); ctx.lineWidth = 1.5; this.roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke();
    this.itemIcon(ctx, px + 20, py + 22, 13, it);
    ui.text(it.name, px + 40, py + 20, { size: 15, bold: true, color: it.color, font: "Georgia, serif" });
    ui.text("Relic", px + 40, py + 34, { size: 10, color: "#9a917b" });
    let ly = py + 58;
    for (const ln of lines) { ui.text("◆ " + ln, px + 14, ly, { size: 12.5, bold: true, color: "#e7ddc4" }); ly += 17; }
    ui.text("Equip onto a unit · up to 3 each", px + 14, ly + 4, { size: 10, color: "#8a8278" });
  }

  /** Hover inspector for a unit: its tier, star, live combat stats, counter
   *  bonuses, signature ability and equipped relics. */
  private drawUnitTip(W: number, H: number) {
    if (!this.unitTip) return;
    const ctx = ui.ctx;
    const p = this.unitTip.p;
    const def = UNITS[p.type];
    if (!def) return;
    const tier = UNIT_TIER[p.type] ?? 1;
    const relics = p.items.map((id) => ITEMS[id]).filter(Boolean) as Item[];
    const ab = ABILITIES[p.type];
    // Effective stats = base × star, then relics (synergies add more in a fight).
    const sm = STAR_MULT[p.star] ?? 1;
    const st = { maxHp: Math.round(def.hp * sm), hp: 0, attack: Math.round(def.attack * sm), armor: def.armor, speed: def.speed };
    st.hp = st.maxHp;
    applyItems(st, p.items);
    const bonuses = Object.entries(def.bonus).map(([cls, amt]) => `+${amt} vs ${cls}`);

    const pw = 244;
    let ph = 50 + 56; // header + stat block (incl. RATE + DPS)
    if (bonuses.length) ph += 18;
    if (ab) ph += 34;
    ph += 8 + (relics.length ? relics.length * 30 : 16);
    let px = this.unitTip.x, py = this.unitTip.y;
    if (px + pw > W - 4) px = Math.max(4, px - pw - 90);
    py = Math.max(4, Math.min(py, H - ph - 4));
    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 16; ctx.shadowOffsetY = 4;
    ctx.fillStyle = "rgba(18,14,9,0.98)"; this.roundRect(ctx, px, py, pw, ph, 9); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = withAlpha(TIER_COLOR[tier], 0.95); ctx.lineWidth = 1.5; this.roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke();
    // Header.
    ui.text(UNITS[p.type]?.name ?? p.type, px + 14, py + 21, { size: 15, bold: true, color: "#f2e8d0", font: "Georgia, serif" });
    ui.text(stars(p.star), px + 14, py + 37, { size: 13, color: p.star >= 3 ? "#ffd24a" : p.star === 2 ? "#cfe0ff" : "#9a917b" });
    ui.text(`Tier ${tier}`, px + pw - 14, py + 21, { align: "right", size: 11, color: TIER_COLOR[tier] });
    let y = py + 50;
    const div = () => { ctx.strokeStyle = "rgba(255,255,255,0.08)"; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(px + 10, y); ctx.lineTo(px + pw - 10, y); ctx.stroke(); };
    div();
    // Stat block. RATE = attacks/sec; DPS ≈ attack × rate (before enemy armour).
    const rate = def.attackInterval > 0 ? 1 / def.attackInterval : 0;
    const dps = Math.round(st.attack * rate);
    const statCell = (label: string, val: string, sx: number, sy: number, col: string) => {
      ui.text(label, sx, sy, { size: 9.5, color: "#8a8278" });
      ui.text(val, sx + 32, sy, { size: 12.5, bold: true, color: col });
    };
    statCell("HP", String(st.maxHp), px + 16, y + 16, "#7df2a9");
    statCell("ATK", String(st.attack), px + 92, y + 16, "#ffce6a");
    statCell("RATE", rate.toFixed(2) + "/s", px + 162, y + 16, "#e7ddc4");
    statCell("ARM", String(st.armor), px + 16, y + 33, "#cfe0ff");
    statCell("RNG", def.range > 0 ? String(def.range) : "melee", px + 92, y + 33, "#e7ddc4");
    statCell("SPD", String(st.speed), px + 162, y + 33, "#e7ddc4");
    ui.text(`DPS ≈ ${dps}`, px + 16, y + 50, { size: 10.5, bold: true, color: "#ffb47a" });
    ui.text(`(${st.attack} dmg every ${def.attackInterval.toFixed(1)}s)`, px + 78, y + 50, { size: 9.5, color: "#8a8278" });
    y += 56;
    if (bonuses.length) {
      div(); ui.text("⚔ " + bonuses.join("  "), px + 14, y + 13, { size: 11, bold: true, color: "#ffb47a" }); y += 18;
    }
    if (ab) {
      div();
      ui.text(ab.name, px + 14, y + 13, { size: 12, bold: true, color: ab.color });
      ui.text("ability — charges in battle", px + pw - 14, y + 13, { align: "right", size: 9, color: "#8a8278" });
      ui.text(this.clip(ab.desc, 42), px + 14, y + 27, { size: 9.5, color: "#cabfa4" });
      y += 34;
    }
    div(); y += 8;
    ui.text(`Relics ${relics.length}/3`, px + 14, y - 0, { size: 10, color: "#9a917b" });
    if (!relics.length) { ui.text("— none equipped —", px + 70, y, { size: 10, color: "#8a8278" }); return; }
    y += 6;
    for (const it of relics) {
      ctx.fillStyle = "rgba(8,6,3,0.9)"; ctx.beginPath(); ctx.arc(px + 22, y + 11, 10, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = it.color; ctx.lineWidth = 1; ctx.stroke();
      this.itemIcon(ctx, px + 22, y + 11, 7, it);
      ui.text(it.name, px + 40, y + 8, { size: 11.5, bold: true, color: it.color });
      ui.text(it.desc, px + 40, y + 21, { size: 10, color: "#cabfa4" });
      y += 30;
    }
  }

  /** Truncate a string to a max length with an ellipsis. */
  private clip(s: string, n: number): string { return s.length > n ? s.slice(0, n - 1) + "…" : s; }

  /** Live readout for a unit hovered during the fight: current HP, attack,
   *  armour and ability charge — its real, in-the-moment combat state. */
  private drawLiveTip(W: number, H: number) {
    if (!this.liveTip || !this.battle) return;
    const e = this.liveTip.e;
    if (!e.alive) { this.liveTip = null; return; }
    const ctx = ui.ctx;
    const name = UNITS[e.type]?.name ?? e.type;
    const ab = ABILITIES[e.type] && e.type !== "villager" ? ABILITIES[e.type] : undefined;
    const charge = e.abilityActive > 0 ? 1 : ab ? Math.max(0, Math.min(1, this.battle.chargeOf(e.id))) : 0;
    const pw = 172, ph = ab ? 92 : 70;
    let px = this.liveTip.x, py = this.liveTip.y;
    if (px + pw > W - 4) px = Math.max(4, px - pw - 28);
    py = Math.max(4, Math.min(py, H - ph - 4));
    const teamCol = e.team === 0 ? "#7fb0e8" : "#e88a7f";
    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 13; ctx.shadowOffsetY = 3;
    ctx.fillStyle = "rgba(18,14,9,0.97)"; this.roundRect(ctx, px, py, pw, ph, 8); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = teamCol; ctx.lineWidth = 1.5; this.roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 8); ctx.stroke();
    ui.text(name, px + 12, py + 17, { size: 13, bold: true, color: "#f2e8d0" });
    ui.text(e.team === 0 ? "yours" : "enemy", px + pw - 12, py + 17, { align: "right", size: 9.5, color: teamCol });
    const frac = Math.max(0, e.hp / e.maxHp);
    const bx = px + 12, by = py + 25, bw = pw - 24, bh = 9;
    ctx.fillStyle = "rgba(0,0,0,0.6)"; ctx.fillRect(bx, by, bw, bh);
    ctx.fillStyle = frac > 0.5 ? "#5ad06a" : frac > 0.25 ? "#ffd24a" : "#e0564a"; ctx.fillRect(bx, by, bw * frac, bh);
    ui.text(`${Math.max(0, Math.round(e.hp))} / ${Math.round(e.maxHp)} HP`, px + pw / 2, by + bh + 10, { align: "center", size: 10, bold: true, color: "#e7ddc4" });
    ui.text(`ATK ${Math.round(e.attack)}`, px + 12, py + 62, { size: 11, bold: true, color: "#ffce6a" });
    ui.text(`ARM ${e.armor}`, px + 92, py + 62, { size: 11, bold: true, color: "#cfe0ff" });
    if (ab) {
      ui.text(ab.name, px + 12, py + 78, { size: 10, color: ab.color });
      ctx.fillStyle = "rgba(0,0,0,0.6)"; ctx.fillRect(px + 12, py + 82, pw - 24, 4);
      ctx.fillStyle = e.abilityActive > 0 ? "#fff" : withAlpha(ab.color, 0.9); ctx.fillRect(px + 12, py + 82, (pw - 24) * charge, 4);
    }
  }
}
