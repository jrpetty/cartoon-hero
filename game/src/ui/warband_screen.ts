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
import { Kind, Entity, Team, ArmorClass } from "../sim/types";
import { makeEntity } from "../sim/world";
import { TILE } from "../content/balance";
import { UNITS } from "../content/units";
import { WarbandRun, UNIT_TIER, Piece, BENCH_SLOTS } from "../sim/warband";
import { LiveBattle, GRID_COLS, GRID_ROWS, GRID_CELL } from "../sim/autobattle";
import { traitsOf, Buff } from "../sim/traits";
import { Item, applyItems, itemDef, isComponent, buildsInto } from "../sim/items";
import { ABILITIES } from "../content/abilities";
import { Augment, TIER_COLOR as AUG_COLOR } from "../sim/augments";
import { WarbandCommander, commanderIdentity } from "../sim/warband_commanders";
import { CarouselPick } from "../sim/carousel";
import { Condition, hasEffect, conditionLines } from "../sim/conditions";
import { TRAITS } from "../sim/traits";
import { styleDef, BattleStyleDef } from "../content/battle_styles";

const TIER_COLOR = ["#888888", "#9aa8b4", "#4caf50", "#3a78d8", "#9b5cf0", "#e0a020"];
const STAR_MULT = [1, 1, 1.8, 3.2]; // hp/attack multiplier by star (matches the battle sim)
const shortName = (type: string) => (UNITS[type]?.name ?? type).split(" ")[0];
const stars = (n: number) => "★".repeat(n);
const BATTLE_SPEED = 2; // sim-time multiplier while watching a fight
const INTRO_LEN = 1.4;  // seconds the VS clash banner holds before the charge
const easeOut = (t: number) => 1 - Math.pow(1 - Math.max(0, Math.min(1, t)), 3);

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
  private augTip: { a: Augment; x: number; y: number } | null = null; // hovered owned augment
  private cmdTip: { c: WarbandCommander; x: number; y: number } | null = null; // hovered commander plate
  private condTip: { c: Condition; x: number; y: number } | null = null; // hovered battlefield chip
  /** One reusable entity per unit type, so cards can draw the real sprite. */
  private portraits = new Map<string, Entity>();
  private introT = 0;   // VS clash banner countdown at the start of a fight
  private resultT = -1; // victory/defeat flourish clock
  private scoutId = -1;   // opponent whose warband is being scouted, or -1
  private scoutRect = { x: 0, y: 0, w: 0, h: 0 }; // last frame's panel, to block clicks through it
  private augPick = -1;   // augment card under the cursor on the picker
  private augT = 0;       // augment picker intro clock (card deal-in)
  // A star-up celebration: set when the run reports a merge, fades on its own.
  private starUp: { type: string; star: number; t: number } | null = null;
  // A relic-forged flourish when two components fuse on a unit.
  private fusion: { item: string; type: string; t: number } | null = null;

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
    // TFT-style stage-round ("2-3") with the raw round number under it.
    stat("STAGE", run.stageLabel(), 280);
    ui.text(`round ${run.round}`, 280, 53, { size: 8.5, color: "#6f6a5c" });
    stat("GOLD", String(run.gold), 360, "#ffd24a", () => {
      ctx.save(); ctx.shadowColor = "#ffd24a"; ctx.shadowBlur = 6;
      const cg = ctx.createRadialGradient(364, 35, 1, 366, 37, 7);
      cg.addColorStop(0, "#ffe89a"); cg.addColorStop(1, "#e0a52a");
      ctx.fillStyle = cg; ctx.beginPath(); ctx.arc(366, 37, 6.5, 0, Math.PI * 2); ctx.fill(); ctx.restore();
      ctx.strokeStyle = "#a8771e"; ctx.lineWidth = 1; ctx.beginPath(); ctx.arc(366, 37, 6.5, 0, Math.PI * 2); ctx.stroke();
    });
    stat("LEVEL", String(run.level), 452);
    // XP progress toward the next level, right under the number.
    {
      const xp = run.xpProgress();
      const bx2 = 443, by2 = 46, bw2 = 68;
      ctx.fillStyle = "rgba(0,0,0,0.55)"; ctx.fillRect(bx2, by2, bw2, 4);
      if (xp.max) {
        ctx.fillStyle = "#ffd24a"; ctx.fillRect(bx2, by2, bw2, 4);
        ui.text("MAX", bx2 + bw2, 22, { align: "right", size: 9, color: "#ffd24a" });
      } else {
        const cur = Math.max(0, xp.xp); // a hand-set level can sit below its own gate
        ctx.fillStyle = "#7fb0e8"; ctx.fillRect(bx2, by2, bw2 * Math.max(0, Math.min(1, cur / xp.need)), 4);
        ui.text(`${cur}/${xp.need}`, bx2 + bw2, 22, { align: "right", size: 9, color: "#8a8278" });
      }
    }
    stat("LIFE", String(run.life), 540, run.life <= 25 ? "#e0564a" : "#7df2a9");
    stat("STREAK", (run.streak > 0 ? "+" : "") + run.streak, 628, run.streak > 0 ? "#7df2a9" : run.streak < 0 ? "#e0a878" : "#9a917b");
    // While a modal pick is up nothing behind it may be clicked.
    const picking = run.phase === "augment" || run.phase === "commander" || run.phase === "draft";
    if (!picking && ui.button("Quit Run", W - 120, 12, 100, 32, { danger: true, size: 13 })) action = "exit";

    // ---- standings sidebar ----
    const sx = 12;
    let sy = 80;
    ui.text("Standings", sx, sy, { size: 14, bold: true, color: PAL.uiAccent });
    ui.text("click to scout", sx + 74, sy, { size: 9.5, color: "#6f6a5c" });
    sy += 18;
    for (const s of run.standings()) {
      const h = 26;
      const hov = !s.you && s.alive && ui.mx >= sx && ui.mx <= sx + 200 && ui.my >= sy && ui.my <= sy + h;
      const open = s.id === this.scoutId;
      ctx.fillStyle = s.you ? withAlpha(PAL.uiAccent, 0.16) : open ? withAlpha("#7fb0e8", 0.24) : hov ? "rgba(255,255,255,0.09)" : "rgba(0,0,0,0.25)";
      ctx.fillRect(sx, sy, 200, h);
      if (open || hov) { ctx.strokeStyle = withAlpha("#7fb0e8", open ? 0.9 : 0.45); ctx.lineWidth = 1; ctx.strokeRect(sx + 0.5, sy + 0.5, 199, h - 1); }
      // The warband you face this round gets a marker so you can read the matchup.
      const next = !s.you && !run.isCreepRound() && s.name === run.pendingFoeName() && s.alive;
      ui.text((s.alive ? "" : "☠ ") + s.name, sx + 8, sy + 17, {
        size: 12, bold: s.you, color: s.alive ? (s.you ? "#ffe9b0" : "#e7ddc4") : "#6f6a5c",
      });
      if (next) ui.text("⚔", sx + 96, sy + 17, { size: 12, color: "#e0786a" });
      ui.bar(sx + 110, sy + 9, 82, 9, Math.max(0, s.life) / 100, s.alive ? "#7df2a9" : "#5a554d");
      ui.text(String(Math.max(0, s.life)), sx + 152, sy + 17, { align: "center", size: 9, bold: true, color: "rgba(6,10,6,0.85)" });
      if (hov && !picking && ui.clicked && !ui.pointerConsumed) { ui.pointerConsumed = true; this.scoutId = open ? -1 : s.id; audio.play("ui"); }
      sy += h + 3;
    }

    // ---- the commander leading this run ----
    this.cmdTip = null;
    if (run.commander) {
      const id = commanderIdentity(run.commander);
      const col = id?.color ?? "#caa56a";
      sy += 12;
      const ch = 40;
      const cg = ctx.createLinearGradient(sx, 0, sx + 200, 0);
      cg.addColorStop(0, withAlpha(col, 0.26)); cg.addColorStop(1, "rgba(12,9,5,0.6)");
      ctx.fillStyle = cg; this.roundRect(ctx, sx, sy, 200, ch, 6); ctx.fill();
      ctx.strokeStyle = withAlpha(col, 0.6); ctx.lineWidth = 1;
      this.roundRect(ctx, sx + 0.5, sy + 0.5, 199, ch - 1, 6); ctx.stroke();
      this.crest(ctx, sx + 22, sy + ch / 2, 14, run.commander, time);
      ui.text(id?.name ?? run.commander.id, sx + 44, sy + 18, { size: 13, bold: true, color: "#f2e8d0", font: "Georgia, serif" });
      ui.text(this.clip(id?.title ?? "", 22), sx + 44, sy + 32, { size: 10, color: col });
      const hovC = ui.mx >= sx && ui.mx <= sx + 200 && ui.my >= sy && ui.my <= sy + ch;
      if (hovC) this.cmdTip = { c: run.commander, x: sx + 208, y: sy - 6 };
      sy += ch;
    }

    // ---- augments you've taken this run ----
    this.augTip = null;
    if (run.augments.length) {
      sy += 12;
      ui.text("Augments", sx, sy, { size: 14, bold: true, color: PAL.uiAccent });
      sy += 8;
      run.augments.forEach((a, i) => {
        const ay = sy + i * 30, aw = 200, ah = 26;
        const col = AUG_COLOR[a.tier];
        const hov = ui.mx >= sx && ui.mx <= sx + aw && ui.my >= ay && ui.my <= ay + ah;
        const g2 = ctx.createLinearGradient(sx, 0, sx + aw, 0);
        g2.addColorStop(0, withAlpha(col, hov ? 0.34 : 0.2)); g2.addColorStop(1, "rgba(12,9,5,0.6)");
        ctx.fillStyle = g2; this.roundRect(ctx, sx, ay, aw, ah, 5); ctx.fill();
        ctx.strokeStyle = withAlpha(col, hov ? 0.95 : 0.55); ctx.lineWidth = 1;
        this.roundRect(ctx, sx + 0.5, ay + 0.5, aw - 1, ah - 1, 5); ctx.stroke();
        this.augSigil(ctx, sx + 15, ay + 13, 8, a, time);
        ui.text(this.clip(a.name, 22), sx + 30, ay + 17, { size: 11.5, bold: true, color: col });
        if (hov) this.augTip = { a, x: sx + aw + 8, y: ay - 10 };
      });
      sy += run.augments.length * 30;
    }

    // ---- synergies ----
    sy += 12;
    ui.text("Synergies", sx, sy, { size: 14, bold: true, color: PAL.uiAccent });
    sy += 16;
    const traits = run.activeTraits();
    if (!traits.length) { ui.text("— none active —", sx, sy + 6, { size: 11, color: "#6f6a5c" }); sy += 16; }
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
        const it = itemDef(id);
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
        // Components get a dashed edge — a part, not a finished relic.
        if (it.component) ctx.setLineDash([4, 3]);
        this.roundRect(ctx, tilex + 1, tiley + 1, tile - 2, tile - 2, 6); ctx.stroke();
        ctx.setLineDash([]);
        this.itemIcon(ctx, tilex + tile / 2, tiley + tile / 2 - 2, tile * 0.42, it);
        if (it.component) ui.text(it.short, tilex + tile / 2, tiley + tile - 5, { align: "center", size: 8, bold: true, color: withAlpha(it.color, 0.9) });
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
    if (run.phase === "shop" || run.phase === "augment" || run.phase === "draft") {
      const sig = this.sig(run);
      if (sig !== this.battleSig || !this.battle) {
        this.battle = new LiveBattle(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30, run.sideOpts(), run.fieldOpts());
        this.battleSig = sig;
      }
    }
    // A merge happened → celebrate the star-up over the board.
    if (run.lastMerge) {
      this.starUp = { type: run.lastMerge.type, star: run.lastMerge.star, t: 0 };
      run.lastMerge = null;
      audio.play("levelup");
    }
    if (this.starUp) { this.starUp.t += dt; if (this.starUp.t > 1.6) this.starUp = null; }
    if (run.lastFusion) {
      this.fusion = { item: run.lastFusion.item, type: run.lastFusion.type, t: 0 };
      run.lastFusion = null;
      audio.play("complete");
    }
    if (this.fusion) { this.fusion.t += dt; if (this.fusion.t > 1.7) this.fusion = null; }
    if (picking) this.augT += dt; else this.augT = 0;
    // Advance the live fight in real (scaled) time, then bank the result.
    if (run.phase === "battle" && this.introT > 0) {
      this.introT -= dt;   // the clash banner holds the fight for a beat
      this.stepAccum = 0;
    } else if (run.phase === "battle" && this.battle) {
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
      if (run.phase === "battle") {
        audio.play("alert");
        this.introT = INTRO_LEN;
        this.starUp = null; this.fusion = null; // shop flourishes don't follow you in
      } else if (run.phase === "result" && run.lastResult) {
        audio.play(run.lastResult.won ? "complete" : "collapse");
        this.resultT = 0;
        this.starUp = null; this.fusion = null;
      } else if (run.phase === "shop") { this.fx.clear(); this.resultT = -1; }
      this.prevPhase = run.phase;
    }
    if (this.resultT >= 0) this.resultT += dt;

    // ---- board title + enemy banner ----
    ui.text(`Your Warband  ·  ${run.deployedCount()} / ${run.deployCount()} deployed`, boardX, 80, { size: 14, bold: true, color: PAL.uiAccent });
    // ---- the round's battlefield condition ----
    this.condTip = null;
    {
      const cond = run.condition;
      const live = hasEffect(cond);
      const label = cond.name;
      const cw2 = 22 + label.length * 6.4;
      const cx2 = boardX + 232, cy2 = 66;
      const hovC = ui.mx >= cx2 && ui.mx <= cx2 + cw2 && ui.my >= cy2 && ui.my <= cy2 + 20;
      ctx.fillStyle = withAlpha(cond.color, hovC ? 0.34 : live ? 0.2 : 0.12);
      this.roundRect(ctx, cx2, cy2, cw2, 20, 5); ctx.fill();
      ctx.strokeStyle = withAlpha(cond.color, hovC ? 0.95 : 0.5); ctx.lineWidth = 1;
      this.roundRect(ctx, cx2 + 0.5, cy2 + 0.5, cw2 - 1, 19, 5); ctx.stroke();
      this.weatherGlyph(ctx, cx2 + 12, cy2 + 10, 6, cond, time);
      ui.text(label, cx2 + 22, cy2 + 14, { size: 11, bold: true, color: cond.color });
      if (hovC) this.condTip = { c: cond, x: cx2, y: cy2 + 26 };
    }
    if (run.isCreepRound()) {
      // A PvE camp round reads differently: loot on the line, not your life.
      const camp = run.pendingCamp!;
      ui.text(`🐺 ${camp.name}`, boardX + boardW, 80, { size: 13, bold: true, align: "right", color: "#c8a86a" });
      ui.text(`win → ${camp.relics} relic${camp.relics > 1 ? "s" : ""} + ${camp.gold}g`, boardX + boardW, 66, { size: 10.5, align: "right", color: "#9a917b" });
    } else {
      ui.text(`vs ${run.pendingFoeName()}`, boardX + boardW, 80, { size: 13, bold: true, align: "right", color: "#e0786a" });
    }

    // The scout panel floats over the left of the board — swallow the pointer
    // there so a click on it never lands on a board cell underneath.
    if (this.scoutId >= 0) {
      const r = this.scoutRect;
      if (ui.mx >= r.x && ui.mx <= r.x + r.w && ui.my >= r.y && ui.my <= r.y + r.h) ui.pointerConsumed = true;
    }

    this.drawBoard(boardX, boardY, boardW, boardH, time, dt, run);

    // ---- bench (your pieces) ----
    const benchY = boardBottom + 8;
    const benchUsed = run.benchCount();
    ui.text("Bench", boardX, benchY - 2, { size: 12, bold: true, color: "#9a917b" });
    ui.text(`${benchUsed} / ${run.benchSlots()}`, boardX + 44, benchY - 2, {
      size: 11, bold: true, color: run.benchFull() ? "#e0786a" : "#6f6a5c",
    });
    if (this.selectedItem >= 0 && this.selectedItem < run.itemStash.length) {
      ui.text("Relic ready — click a unit to equip it", boardX + 52, benchY - 2, { size: 12, bold: true, color: "#ffd24a" });
    } else if (this.selectedItem >= run.itemStash.length) this.selectedItem = -1;

    // Bench = your reserve (non-deployed) pieces. Drag one onto the board to
    // field it, into the Sell box to sell it, or click with a relic to equip.
    const reserve = run.pieces.map((_, i) => i).filter((i) => !run.pieces[i].deployed);
    const cardW = 78;
    const cardH = benchH - 14; // clear of the shop panel below
    // A fixed rank of slots, so the bench reads as a real capacity you can fill.
    for (let k = 0; k < BENCH_SLOTS; k++) {
      const cx = boardX + k * (cardW + 6);
      if (cx + cardW > W - 16) break;
      const i = reserve[k];
      if (i == null) {
        // An empty slot: a dashed frame, so "how much room is left" is visible.
        ctx.fillStyle = "rgba(0,0,0,0.22)";
        this.roundRect(ctx, cx, benchY + 14, cardW, cardH, 6); ctx.fill();
        ctx.strokeStyle = "rgba(255,255,255,0.09)"; ctx.lineWidth = 1;
        ctx.setLineDash([4, 4]);
        this.roundRect(ctx, cx + 0.5, benchY + 14.5, cardW - 1, cardH - 1, 6); ctx.stroke();
        ctx.setLineDash([]);
        continue;
      }
      this.pieceCard(cx, benchY + 14, cardW, cardH, run.pieces[i], false, time, this.heldPiece === i);
      const over = ui.mx >= cx && ui.mx <= cx + cardW && ui.my >= benchY + 14 && ui.my <= benchY + 14 + cardH;
      if (run.phase === "shop" && over && !ui.pointerConsumed) {
        const equipping = this.selectedItem >= 0 && this.selectedItem < run.itemStash.length;
        if (equipping && ui.clicked) { ui.pointerConsumed = true; if (run.equipItem(this.selectedItem, i)) this.selectedItem = -1; }
        else if (!equipping && this.pressEdge && this.heldPiece < 0) {
          this.heldPiece = i; this.heldFromBoard = false; this.grabbedThisPress = true; ui.pointerConsumed = true; audio.play("select");
        }
      }
    }
    if (run.pieces.length === 0) {
      ui.text("Buy units from the shop below…", boardX + BENCH_SLOTS * (cardW + 6) + 10, benchY + 40, { size: 12, color: "#9a917b" });
    }

    // ---- shop / actions (bottom) ----
    const shopY = shopTop;
    ctx.fillStyle = "rgba(8,6,3,0.85)";
    ctx.fillRect(0, shopY - 8, W, H - shopY + 8);

    if (run.phase === "shop") {
      ui.text("Shop", bx, shopY + 6, { size: 13, bold: true, color: PAL.uiAccent });
      const sw = 120, sh = 108; // tall enough for the unit to actually be seen
      run.shop.forEach((type, i) => {
        const cx = bx + i * (sw + 8);
        const cy = shopY + 12;
        if (type) {
          const can = run.canBuy(i);
          this.shopCard(cx, cy, sw, sh, type, can.ok, run.poolCount(type), can.reason, time, () => { if (run.buy(i)) audio.play("coin"); });
        }
        else {
          // A bought-out slot still holds its place in the row.
          ctx.fillStyle = "rgba(0,0,0,0.25)"; this.roundRect(ctx, cx, cy, sw, sh, 8); ctx.fill();
          ctx.strokeStyle = "rgba(255,255,255,0.05)"; ctx.lineWidth = 1;
          ctx.setLineDash([4, 4]); this.roundRect(ctx, cx + 0.5, cy + 0.5, sw - 1, sh - 1, 8); ctx.stroke(); ctx.setLineDash([]);
        }
      });
      const rxx = bx + 5 * (120 + 8) + 8;
      const rc = run.rerollCost();
      const freeR = run.freeRerollsLeft();
      if (ui.button(freeR > 0 ? `Reroll  (FREE ×${freeR})` : `Reroll  (${rc}g)`, rxx, shopY + 14, 130, 32, {
        disabled: run.gold < rc, size: 13, accent: freeR > 0,
        tooltip: ["New shop", freeR > 0 ? "Your commander covers this one." : `Costs ${rc} gold.`],
      })) { if (run.reroll()) audio.play("ui"); }
      // Level-up shows the shop odds it would unlock — the real reason to tech.
      const odds = run.shopOdds();
      if (ui.button(`Level Up  (4g)`, rxx, shopY + 52, 130, 32, {
        disabled: run.gold < 4 || run.level >= 9, size: 13,
        tooltip: ["+4 XP · bigger board", `Shop odds now: ${odds.map((o, i) => `T${i + 1} ${o}%`).filter((_, i) => odds[i] > 0).join("  ")}`],
      })) { if (run.buyXp()) audio.play("levelup"); }
      // What this fight is actually worth risking, before you commit to it.
      const worst = run.worstCaseDamage();
      const sg = run.streakGold();
      ui.text(`Defeat costs up to ${worst} life`, rxx + 138, shopY + 26, {
        size: 11, bold: true, color: worst >= run.life ? "#e0564a" : "#e0a878",
      });
      ui.text(sg > 0 ? `Streak pays +${sg}g next round` : "No streak bonus yet", rxx + 138, shopY + 44, {
        size: 11, color: sg > 0 ? "#7df2a9" : "#6f6a5c",
      });
      if (ui.button("⚔ FIGHT", rxx, shopY + 92, 130, 36, { accent: true, size: 16, tooltip: ["Send your warband into the arena."] })) {
        if (run.beginFight()) {
          this.battle = new LiveBattle(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30, run.sideOpts(), run.fieldOpts());
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
      const title = r.creep
        ? (r.won ? `${r.foe} cleared!` : `${r.foe} drove you off`)
        : (r.won ? `Victory vs ${r.foe}!` : `Defeat vs ${r.foe}`);
      ui.text(title, W / 2, shopY + 30, {
        align: "center", size: 24, bold: true, color: r.won ? "#7df2a9" : "#e0564a", font: "Georgia, serif",
      });
      if (r.creep && r.won) {
        // Camp loot is the whole point of the round — spell it out.
        ui.text(`Loot:  ${r.relics} relic${r.relics === 1 ? "" : "s"}  ·  ${r.gold} gold`,
          W / 2, shopY + 58, { align: "center", size: 15, bold: true, color: "#ffd24a" });
      } else {
        ui.text(
          r.won ? `${r.youLeft} of your warband survived.`
            : r.creep ? `The camp holds. You lost ${r.dmg} life.`
              : `You lost ${r.dmg} life (${r.foeLeft} enemies left standing).`,
          W / 2, shopY + 58, { align: "center", size: 14, color: "#d8cdb4" },
        );
      }
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

    // ---- overlays, back to front ----
    if (run.phase === "battle" && this.introT > 0) this.drawIntro(boardX, boardY, boardW, boardH, run);
    if (run.phase === "result" && run.lastResult) this.drawResultFlourish(W, H, run, time);
    this.drawStarUp(boardX, boardY, boardW, boardH);
    this.drawFusion(boardX, boardY, boardW, boardH);
    if (this.scoutId >= 0 && run.phase !== "over") this.drawScoutPanel(W, H, run, time);
    if (picking) {
      this.unitTip = null; this.itemTip = null; this.augTip = null; this.cmdTip = null; this.condTip = null;
      if (run.phase === "commander") this.drawCommanderPicker(W, H, run, time);
      else if (run.phase === "draft") this.drawDraftPicker(W, H, run, time);
      else this.drawAugmentPicker(W, H, run, time);
    }
    this.drawItemTip(W, H);
    this.drawUnitTip(W, H);
    this.drawLiveTip(W, H);
    this.drawAugTip(W, H);
    this.drawCmdTip(W, H);
    this.drawCondTip(W, H);
    this.wasDown = down;

    return action;
  }

  /** Greedy word-wrap to a character budget per line. */
  private wrap(text: string, per: number): string[] {
    const words = text.split(" ");
    const lines: string[] = [];
    let line = "";
    for (const w of words) {
      if (line && (line + " " + w).length > per) { lines.push(line); line = w; }
      else line = line ? line + " " + w : w;
    }
    if (line) lines.push(line);
    return lines;
  }

  /**
   * A distinct procedural sigil per augment, picked from what the augment
   * actually does — coins for economy, a blade for attack, a shield for bulk,
   * a banner for board slots and synergies, a gem for relics.
   */
  private augSigil(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number, a: Augment, time: number) {
    const col = AUG_COLOR[a.tier];
    ctx.save();
    ctx.translate(cx, cy);
    ctx.lineJoin = "round"; ctx.lineCap = "round";
    ctx.fillStyle = col; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = Math.max(1, r * 0.14);
    const h = r;
    if (a.boardSlots) { // board slots: a rank of cells with a fresh one lighting up
      const cw = h * 0.52, gap2 = h * 0.14;
      for (let i = 0; i < 3; i++) {
        const cxx = -cw - gap2 + i * (cw + gap2);
        const fresh = i === 2;
        ctx.fillStyle = fresh ? col : withAlpha(col, 0.28);
        ctx.strokeStyle = withAlpha(col, 0.9); ctx.lineWidth = Math.max(1, r * 0.1);
        ctx.beginPath(); ctx.rect(cxx - cw / 2, -cw / 2, cw, cw); ctx.fill(); ctx.stroke();
        if (fresh) { // a plus on the new slot
          ctx.strokeStyle = "rgba(20,15,8,0.9)"; ctx.lineWidth = Math.max(1.2, r * 0.14);
          ctx.beginPath();
          ctx.moveTo(cxx, -cw * 0.28); ctx.lineTo(cxx, cw * 0.28);
          ctx.moveTo(cxx - cw * 0.28, 0); ctx.lineTo(cxx + cw * 0.28, 0); ctx.stroke();
        }
      }
    } else if (a.traitBonus) { // banner on a pole
      ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = r * 0.2;
      ctx.beginPath(); ctx.moveTo(-h * 0.5, -h); ctx.lineTo(-h * 0.5, h); ctx.stroke();
      ctx.fillStyle = col; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = Math.max(1, r * 0.11);
      ctx.beginPath();
      ctx.moveTo(-h * 0.5, -h * 0.85); ctx.lineTo(h * 0.85, -h * 0.85); ctx.lineTo(h * 0.5, -h * 0.2);
      ctx.lineTo(h * 0.85, h * 0.45); ctx.lineTo(-h * 0.5, h * 0.45); ctx.closePath(); ctx.fill(); ctx.stroke();
    } else if (a.relicEvery || a.relics) { // gem
      ctx.beginPath();
      ctx.moveTo(0, -h); ctx.lineTo(h * 0.85, -h * 0.2); ctx.lineTo(0, h); ctx.lineTo(-h * 0.85, -h * 0.2);
      ctx.closePath(); ctx.fill(); ctx.stroke();
      ctx.fillStyle = "rgba(255,255,255,0.45)";
      ctx.beginPath(); ctx.moveTo(0, -h); ctx.lineTo(h * 0.4, -h * 0.3); ctx.lineTo(0, 0); ctx.closePath(); ctx.fill();
    } else if (a.gold || a.bounty || a.interestCap || a.rerollCost != null) { // coin stack
      for (let i = 2; i >= 0; i--) {
        const yy = h * 0.45 - i * h * 0.42;
        const g = ctx.createRadialGradient(-r * 0.2, yy - r * 0.2, 1, 0, yy, r * 0.9);
        g.addColorStop(0, "#ffe89a"); g.addColorStop(1, col);
        ctx.fillStyle = g;
        ctx.beginPath(); ctx.ellipse(0, yy, h * 0.85, h * 0.36, 0, 0, Math.PI * 2); ctx.fill();
        ctx.strokeStyle = "rgba(0,0,0,0.5)"; ctx.lineWidth = Math.max(1, r * 0.1); ctx.stroke();
      }
    } else if (a.xp) { // chevrons rising
      ctx.strokeStyle = col; ctx.lineWidth = Math.max(1.5, r * 0.24); ctx.fillStyle = "transparent";
      for (let i = 0; i < 3; i++) {
        const yy = h * 0.6 - i * h * 0.55;
        ctx.beginPath(); ctx.moveTo(-h * 0.7, yy); ctx.lineTo(0, yy - h * 0.45); ctx.lineTo(h * 0.7, yy); ctx.stroke();
      }
    } else if (a.life) { // heart
      ctx.beginPath();
      ctx.moveTo(0, h * 0.85);
      ctx.bezierCurveTo(-h * 1.3, -h * 0.1, -h * 0.55, -h * 1.05, 0, -h * 0.35);
      ctx.bezierCurveTo(h * 0.55, -h * 1.05, h * 1.3, -h * 0.1, 0, h * 0.85);
      ctx.closePath(); ctx.fill(); ctx.stroke();
    } else if (a.buff?.armor || a.buff?.hpPct || a.buff?.hp) { // shield
      ctx.beginPath();
      ctx.moveTo(0, -h); ctx.lineTo(h * 0.82, -h * 0.55); ctx.lineTo(h * 0.82, h * 0.2);
      ctx.quadraticCurveTo(h * 0.82, h * 0.92, 0, h);
      ctx.quadraticCurveTo(-h * 0.82, h * 0.92, -h * 0.82, h * 0.2);
      ctx.lineTo(-h * 0.82, -h * 0.55); ctx.closePath(); ctx.fill(); ctx.stroke();
    } else { // blade
      ctx.beginPath();
      ctx.moveTo(0, -h); ctx.lineTo(h * 0.24, -h * 0.15); ctx.lineTo(h * 0.24, h * 0.35);
      ctx.lineTo(-h * 0.24, h * 0.35); ctx.lineTo(-h * 0.24, -h * 0.15); ctx.closePath(); ctx.fill(); ctx.stroke();
      ctx.fillStyle = "#caa56a";
      ctx.beginPath(); ctx.rect(-h * 0.6, h * 0.33, h * 1.2, h * 0.2); ctx.fill(); ctx.stroke();
    }
    // Prismatic augments get a slow sparkle.
    if (a.tier === "prismatic") {
      const tw = 0.5 + 0.5 * Math.sin(time * 3 + cx * 0.1);
      ctx.globalAlpha = 0.5 + tw * 0.5;
      ctx.fillStyle = "#fff";
      ctx.beginPath(); ctx.arc(h * 0.75, -h * 0.75, Math.max(0.8, r * 0.13), 0, Math.PI * 2); ctx.fill();
    }
    ctx.restore();
  }

  /**
   * The augment pick — a full-screen modal with three dealt-in cards. This is
   * the run's fork in the road, so it gets the weight: dimmed board behind,
   * tier-coloured cards, a sigil, and the exact effect spelled out.
   */
  private drawAugmentPicker(W: number, H: number, run: WarbandRun, time: number) {
    const ctx = ui.ctx;
    const offer = run.augmentOffer;
    if (!offer.length) return;
    const tier = offer[0].tier;
    const tcol = AUG_COLOR[tier];

    // Dim everything behind, with a warm pool of light behind the cards.
    ctx.fillStyle = "rgba(5,4,2,0.82)"; ctx.fillRect(0, 0, W, H);
    const glow = ctx.createRadialGradient(W / 2, H / 2, 40, W / 2, H / 2, Math.max(W, H) * 0.55);
    glow.addColorStop(0, withAlpha(tcol, 0.16)); glow.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H);

    // Slow rotating rays for the prismatic pick — it should feel like an event.
    if (tier === "prismatic") {
      ctx.save();
      ctx.translate(W / 2, H / 2); ctx.rotate(time * 0.12); ctx.globalAlpha = 0.085;
      for (let i = 0; i < 12; i++) {
        ctx.rotate(Math.PI / 6);
        ctx.fillStyle = tcol;
        ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(Math.max(W, H) * 0.7, -26); ctx.lineTo(Math.max(W, H) * 0.7, 26); ctx.closePath(); ctx.fill();
      }
      ctx.restore();
    }

    const gap = 24;
    const cardW = Math.min(258, (W - 100 - gap * 2) / 3);
    const cardH = Math.min(324, H * 0.5);
    const totalW = cardW * offer.length + gap * (offer.length - 1);
    const x0 = (W - totalW) / 2;
    const cy = H / 2 - cardH / 2 + 24;

    const titleT = Math.min(1, this.augT * 3);
    ctx.globalAlpha = titleT;
    ui.text("CHOOSE AN AUGMENT", W / 2, cy - 62, { align: "center", size: 30, bold: true, color: "#f2e8d0", font: "Georgia, serif" });
    ui.text(`${tier.toUpperCase()} · shapes the rest of your run`, W / 2, cy - 38, { align: "center", size: 13, bold: true, color: tcol });
    ctx.globalAlpha = 1;

    this.augPick = -1;
    offer.forEach((a, i) => {
      // Staggered deal-in: slide up + fade, one card after another.
      const t = Math.max(0, Math.min(1, (this.augT - i * 0.11) * 3.2));
      const ease = 1 - Math.pow(1 - t, 3);
      if (t <= 0) return;
      const col = AUG_COLOR[a.tier];
      const x = x0 + i * (cardW + gap);
      const hov = ui.mx >= x && ui.mx <= x + cardW && ui.my >= cy && ui.my <= cy + cardH && t >= 1;
      if (hov) this.augPick = i;
      const lift = (hov ? -8 : 0) + (1 - ease) * 46;
      const y = cy + lift;

      ctx.save();
      ctx.globalAlpha = ease;
      // Card body.
      ctx.save();
      ctx.shadowColor = hov ? withAlpha(col, 0.75) : "rgba(0,0,0,0.65)";
      ctx.shadowBlur = hov ? 30 : 16; ctx.shadowOffsetY = 6;
      const g = ctx.createLinearGradient(0, y, 0, y + cardH);
      g.addColorStop(0, withAlpha(col, hov ? 0.4 : 0.24));
      g.addColorStop(0.42, "rgba(24,18,11,0.98)");
      g.addColorStop(1, "rgba(12,9,5,0.99)");
      ctx.fillStyle = g; this.roundRect(ctx, x, y, cardW, cardH, 13); ctx.fill();
      ctx.restore();
      ctx.strokeStyle = withAlpha(col, hov ? 1 : 0.7); ctx.lineWidth = hov ? 2.6 : 1.6;
      this.roundRect(ctx, x + 1.5, y + 1.5, cardW - 3, cardH - 3, 12); ctx.stroke();

      // Tier chip.
      const chipW = 74;
      ctx.fillStyle = withAlpha(col, 0.22);
      this.roundRect(ctx, x + cardW / 2 - chipW / 2, y + 16, chipW, 17, 8); ctx.fill();
      ui.text(a.tier.toUpperCase(), x + cardW / 2, y + 29, { align: "center", size: 9.5, bold: true, color: col });

      // Sigil in a ring.
      const sy2 = y + 92;
      ctx.save();
      ctx.shadowColor = withAlpha(col, 0.8); ctx.shadowBlur = hov ? 20 : 10;
      ctx.strokeStyle = withAlpha(col, 0.5); ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.arc(x + cardW / 2, sy2, 36, 0, Math.PI * 2); ctx.stroke();
      ctx.restore();
      ctx.fillStyle = "rgba(0,0,0,0.35)";
      ctx.beginPath(); ctx.arc(x + cardW / 2, sy2, 34, 0, Math.PI * 2); ctx.fill();
      this.augSigil(ctx, x + cardW / 2, sy2, 21, a, time);

      // Name + description.
      const nm = a.name.length > 20 ? 15 : 18;
      ui.text(a.name, x + cardW / 2, y + 158, { align: "center", size: nm, bold: true, color: "#f7efdc", font: "Georgia, serif" });
      ctx.strokeStyle = withAlpha(col, 0.35); ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(x + 34, y + 172); ctx.lineTo(x + cardW - 34, y + 172); ctx.stroke();
      let ly = y + 196;
      for (const ln of this.wrap(a.desc, 30)) {
        ui.text(ln, x + cardW / 2, ly, { align: "center", size: 12.5, color: "#d8cdb4" });
        ly += 18;
      }

      // Take button.
      const bw = cardW - 44, bh = 38, byy = y + cardH - bh - 18;
      const bhov = ui.mx >= x + 22 && ui.mx <= x + 22 + bw && ui.my >= byy && ui.my <= byy + bh && t >= 1;
      ctx.fillStyle = bhov ? withAlpha(col, 0.85) : withAlpha(col, 0.24);
      this.roundRect(ctx, x + 22, byy, bw, bh, 8); ctx.fill();
      ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.5;
      this.roundRect(ctx, x + 22.75, byy + 0.75, bw - 1.5, bh - 1.5, 8); ctx.stroke();
      ui.text("TAKE", x + cardW / 2, byy + 25, { align: "center", size: 15, bold: true, color: bhov ? "#1a1207" : col });
      ctx.restore();

      if ((hov || bhov) && ui.clicked && !ui.pointerConsumed) {
        ui.pointerConsumed = true;
        if (run.pickAugment(i)) { audio.play("levelup"); this.battleSig = ""; }
      }
    });
    // Swallow any other click while the modal is up.
    if (ui.clicked) ui.pointerConsumed = true;
  }

  /**
   * A commander's heraldic crest: a shield bearing a charge picked from what
   * their perk actually does, over their own colour.
   */
  private crest(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number, c: WarbandCommander, time: number) {
    const id = commanderIdentity(c);
    const col = id?.color ?? "#caa56a";
    ctx.save();
    ctx.translate(cx, cy);
    ctx.lineJoin = "round"; ctx.lineCap = "round";
    // Shield.
    const h = r;
    const g = ctx.createLinearGradient(0, -h, 0, h);
    g.addColorStop(0, withAlpha(col, 0.95)); g.addColorStop(1, withAlpha(col, 0.45));
    ctx.fillStyle = g; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = Math.max(1.4, r * 0.09);
    ctx.beginPath();
    ctx.moveTo(-h * 0.78, -h * 0.85); ctx.lineTo(h * 0.78, -h * 0.85); ctx.lineTo(h * 0.78, h * 0.15);
    ctx.quadraticCurveTo(h * 0.78, h * 0.9, 0, h);
    ctx.quadraticCurveTo(-h * 0.78, h * 0.9, -h * 0.78, h * 0.15);
    ctx.closePath(); ctx.fill(); ctx.stroke();
    // Charge — the perk, as a symbol.
    ctx.fillStyle = "rgba(16,11,6,0.86)"; ctx.strokeStyle = "rgba(16,11,6,0.86)";
    ctx.lineWidth = Math.max(1.4, r * 0.13);
    const s = h * 0.46;
    if (c.freeRerolls) { // cycling arrows
      ctx.beginPath(); ctx.arc(0, 0, s, 0.6, Math.PI * 1.6); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(-s * 0.1, -s * 1.15); ctx.lineTo(s * 0.45, -s * 0.75); ctx.lineTo(-s * 0.25, -s * 0.4); ctx.closePath(); ctx.fill();
    } else if (c.fullRefund || c.gold || c.startGold) { // coin
      ctx.beginPath(); ctx.arc(0, 0, s * 0.9, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = withAlpha(col, 0.95);
      ctx.beginPath(); ctx.arc(0, 0, s * 0.42, 0, Math.PI * 2); ctx.fill();
    } else if (c.boardSlots) { // a rank of cells, one new
      const cw = s * 0.62;
      for (let i = 0; i < 3; i++) {
        ctx.beginPath(); ctx.rect(-cw * 1.6 + i * cw * 1.15, -cw / 2, cw, cw);
        if (i === 2) ctx.fill(); else ctx.stroke();
      }
    } else if (c.shopLevelBonus) { // upward chevrons
      for (let i = 0; i < 2; i++) {
        const yy = s * 0.5 - i * s * 0.75;
        ctx.beginPath(); ctx.moveTo(-s * 0.8, yy); ctx.lineTo(0, yy - s * 0.6); ctx.lineTo(s * 0.8, yy); ctx.stroke();
      }
    } else if (c.topTraitBonus) { // a banner
      ctx.beginPath(); ctx.moveTo(-s * 0.5, -s); ctx.lineTo(-s * 0.5, s); ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(-s * 0.5, -s * 0.9); ctx.lineTo(s * 0.85, -s * 0.9); ctx.lineTo(s * 0.5, -s * 0.35);
      ctx.lineTo(s * 0.85, s * 0.2); ctx.lineTo(-s * 0.5, s * 0.2); ctx.closePath(); ctx.fill();
    } else if (c.lossShield) { // a cross
      ctx.beginPath();
      ctx.moveTo(0, -s); ctx.lineTo(0, s); ctx.moveTo(-s * 0.7, -s * 0.25); ctx.lineTo(s * 0.7, -s * 0.25);
      ctx.stroke();
    } else { // crossed swords — the drilled warband
      for (const dir of [-1, 1]) {
        ctx.beginPath(); ctx.moveTo(dir * -s * 0.8, s * 0.8); ctx.lineTo(dir * s * 0.8, -s * 0.9); ctx.stroke();
      }
    }
    ctx.restore();
    // A slow sheen across the shield.
    const sheen = 0.5 + 0.5 * Math.sin(time * 1.6 + cx * 0.05);
    ctx.save();
    ctx.globalAlpha = 0.1 + sheen * 0.12;
    ctx.fillStyle = "#fff";
    ctx.beginPath(); ctx.ellipse(cx - r * 0.3, cy - r * 0.45, r * 0.28, r * 0.5, -0.5, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
  }

  /**
   * The opening choice of the run: which commander leads your warband. Same
   * card language as the augment pick, but heraldic — these are the realm's
   * captains, and the perk is a rule of the run rather than a stat.
   */
  private drawCommanderPicker(W: number, H: number, run: WarbandRun, time: number) {
    const ctx = ui.ctx;
    const offer = run.commanderOffer;
    if (!offer.length) return;

    ctx.fillStyle = "rgba(5,4,2,0.86)"; ctx.fillRect(0, 0, W, H);
    const glow = ctx.createRadialGradient(W / 2, H / 2, 40, W / 2, H / 2, Math.max(W, H) * 0.55);
    glow.addColorStop(0, withAlpha("#caa56a", 0.15)); glow.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H);

    const gap = 24;
    const cardW = Math.min(266, (W - 100 - gap * 2) / 3);
    const cardH = Math.min(348, H * 0.56);
    const totalW = cardW * offer.length + gap * (offer.length - 1);
    const x0 = (W - totalW) / 2;
    const cy = H / 2 - cardH / 2 + 22;

    ctx.globalAlpha = Math.min(1, this.augT * 3);
    ui.text("WHO LEADS YOUR WARBAND?", W / 2, cy - 60, { align: "center", size: 30, bold: true, color: "#f2e8d0", font: "Georgia, serif" });
    ui.text("Their command changes a rule of the whole run.", W / 2, cy - 36, { align: "center", size: 13, bold: true, color: "#caa56a" });
    ctx.globalAlpha = 1;

    offer.forEach((c, i) => {
      const t = Math.max(0, Math.min(1, (this.augT - i * 0.11) * 3.2));
      const ease = 1 - Math.pow(1 - t, 3);
      if (t <= 0) return;
      const id = commanderIdentity(c);
      const col = id?.color ?? "#caa56a";
      const x = x0 + i * (cardW + gap);
      const hov = ui.mx >= x && ui.mx <= x + cardW && ui.my >= cy && ui.my <= cy + cardH && t >= 1;
      const y = cy + (hov ? -8 : 0) + (1 - ease) * 46;

      ctx.save();
      ctx.globalAlpha = ease;
      ctx.save();
      ctx.shadowColor = hov ? withAlpha(col, 0.75) : "rgba(0,0,0,0.65)";
      ctx.shadowBlur = hov ? 30 : 16; ctx.shadowOffsetY = 6;
      const g = ctx.createLinearGradient(0, y, 0, y + cardH);
      g.addColorStop(0, withAlpha(col, hov ? 0.38 : 0.22));
      g.addColorStop(0.45, "rgba(24,18,11,0.98)");
      g.addColorStop(1, "rgba(12,9,5,0.99)");
      ctx.fillStyle = g; this.roundRect(ctx, x, y, cardW, cardH, 13); ctx.fill();
      ctx.restore();
      ctx.strokeStyle = withAlpha(col, hov ? 1 : 0.7); ctx.lineWidth = hov ? 2.6 : 1.6;
      this.roundRect(ctx, x + 1.5, y + 1.5, cardW - 3, cardH - 3, 12); ctx.stroke();

      this.crest(ctx, x + cardW / 2, y + 74, 40, c, time);

      ui.text(id?.name ?? c.id, x + cardW / 2, y + 148, { align: "center", size: 20, bold: true, color: "#f7efdc", font: "Georgia, serif" });
      ui.text(id?.title ?? "", x + cardW / 2, y + 168, { align: "center", size: 12, bold: true, color: col });
      ctx.strokeStyle = withAlpha(col, 0.35); ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(x + 34, y + 182); ctx.lineTo(x + cardW - 34, y + 182); ctx.stroke();
      let ly = y + 206;
      for (const ln of this.wrap(c.perk, 30)) {
        ui.text(ln, x + cardW / 2, ly, { align: "center", size: 12.5, color: "#d8cdb4" });
        ly += 18;
      }

      const bw = cardW - 44, bh = 38, byy = y + cardH - bh - 18;
      const bhov = ui.mx >= x + 22 && ui.mx <= x + 22 + bw && ui.my >= byy && ui.my <= byy + bh && t >= 1;
      ctx.fillStyle = bhov ? withAlpha(col, 0.85) : withAlpha(col, 0.24);
      this.roundRect(ctx, x + 22, byy, bw, bh, 8); ctx.fill();
      ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.5;
      this.roundRect(ctx, x + 22.75, byy + 0.75, bw - 1.5, bh - 1.5, 8); ctx.stroke();
      ui.text("LEAD", x + cardW / 2, byy + 25, { align: "center", size: 15, bold: true, color: bhov ? "#1a1207" : col });
      ctx.restore();

      if ((hov || bhov) && ui.clicked && !ui.pointerConsumed) {
        ui.pointerConsumed = true;
        if (run.pickCommander(i)) { audio.play("levelup"); this.battleSig = ""; }
      }
    });
    if (ui.clicked) ui.pointerConsumed = true;
  }

  /**
   * The carousel: the shared draft. Units ride out already carrying a
   * component, and the warbands below you in the standings have already taken
   * theirs — so the ring you see is what the field left behind.
   */
  private drawDraftPicker(W: number, H: number, run: WarbandRun, time: number) {
    const ctx = ui.ctx;
    const ring = run.carousel;
    if (!ring.length) return;

    ctx.fillStyle = "rgba(5,4,2,0.85)"; ctx.fillRect(0, 0, W, H);
    const glow = ctx.createRadialGradient(W / 2, H / 2, 40, W / 2, H / 2, Math.max(W, H) * 0.55);
    glow.addColorStop(0, withAlpha("#7fb0e8", 0.14)); glow.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H);

    const gap = 14;
    const cardW = Math.min(150, (W - 120 - gap * (ring.length - 1)) / ring.length);
    const cardH = 236;
    const totalW = cardW * ring.length + gap * (ring.length - 1);
    const x0 = (W - totalW) / 2;
    const cy = H / 2 - cardH / 2 + 16;

    ctx.globalAlpha = Math.min(1, this.augT * 3);
    ui.text("THE CAROUSEL", W / 2, cy - 62, { align: "center", size: 30, bold: true, color: "#f2e8d0", font: "Georgia, serif" });
    ui.text(
      run.draftAhead > 0
        ? `${run.draftAhead} warband${run.draftAhead === 1 ? "" : "s"} below you picked first — take what's left.`
        : "You're last in the standings, so you pick first. Take the best of it.",
      W / 2, cy - 38, { align: "center", size: 13, bold: true, color: run.draftAhead > 0 ? "#e0a878" : "#7df2a9" },
    );
    ctx.globalAlpha = 1;

    ring.forEach((p, i) => {
      const t = Math.max(0, Math.min(1, (this.augT - i * 0.07) * 3.4));
      const ease = 1 - Math.pow(1 - t, 3);
      if (t <= 0) return;
      const tier = UNIT_TIER[p.type] ?? 1;
      const col = TIER_COLOR[tier];
      const it = itemDef(p.item);
      const x = x0 + i * (cardW + gap);
      const hov = ui.mx >= x && ui.mx <= x + cardW && ui.my >= cy && ui.my <= cy + cardH && t >= 1;
      // The ring drifts, so it reads as a carousel rather than a row of cards.
      const drift = Math.sin(time * 0.9 + i * 0.7) * 4;
      const y = cy + (hov ? -10 : 0) + drift + (1 - ease) * 40;

      ctx.save();
      ctx.globalAlpha = ease;
      ctx.save();
      ctx.shadowColor = hov ? withAlpha(col, 0.8) : "rgba(0,0,0,0.65)";
      ctx.shadowBlur = hov ? 26 : 14; ctx.shadowOffsetY = 5;
      const g = ctx.createLinearGradient(0, y, 0, y + cardH);
      g.addColorStop(0, withAlpha(col, hov ? 0.42 : 0.24));
      g.addColorStop(0.5, "rgba(24,18,11,0.98)");
      g.addColorStop(1, "rgba(12,9,5,0.99)");
      ctx.fillStyle = g; this.roundRect(ctx, x, y, cardW, cardH, 11); ctx.fill();
      ctx.restore();
      ctx.strokeStyle = withAlpha(col, hov ? 1 : 0.7); ctx.lineWidth = hov ? 2.4 : 1.5;
      this.roundRect(ctx, x + 1.25, y + 1.25, cardW - 2.5, cardH - 2.5, 10); ctx.stroke();

      // The component it carries, on a plinth at the top.
      if (it) {
        ctx.save();
        ctx.shadowColor = withAlpha(it.color, 0.9); ctx.shadowBlur = hov ? 18 : 9;
        ctx.fillStyle = "rgba(8,6,3,0.9)";
        ctx.beginPath(); ctx.arc(x + cardW / 2, y + 44, 25, 0, Math.PI * 2); ctx.fill();
        ctx.strokeStyle = withAlpha(it.color, 0.95); ctx.lineWidth = 1.5;
        ctx.setLineDash([4, 3]); ctx.stroke(); ctx.setLineDash([]);
        ctx.restore();
        this.itemIcon(ctx, x + cardW / 2, y + 44, 14, it);
        ui.text(it.name, x + cardW / 2, y + 84, { align: "center", size: 10.5, bold: true, color: it.color });
        ui.text(it.desc, x + cardW / 2, y + 97, { align: "center", size: 9, color: "#8a8278" });
      }

      ctx.strokeStyle = withAlpha(col, 0.3); ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(x + 18, y + 108); ctx.lineTo(x + cardW - 18, y + 108); ctx.stroke();

      const nm = shortName(p.type);
      ui.text(nm, x + cardW / 2, y + 132, { align: "center", size: nm.length > 9 ? 12 : 14.5, bold: true, color: "#f7efdc", font: "Georgia, serif" });
      ui.text(`Tier ${tier}`, x + cardW / 2, y + 150, { align: "center", size: 10, color: col });
      // Its synergies, so you can draft toward a comp.
      let ty = y + 170;
      for (const tr of traitsOf(p.type).slice(0, 2)) {
        ui.text(`◆ ${tr.name}`, x + cardW / 2, ty, { align: "center", size: 9.5, color: tr.color });
        ty += 13;
      }

      const bw = cardW - 26, bh = 30, byy = y + cardH - bh - 14;
      const bhov = ui.mx >= x + 13 && ui.mx <= x + 13 + bw && ui.my >= byy && ui.my <= byy + bh && t >= 1;
      ctx.fillStyle = bhov ? withAlpha(col, 0.85) : withAlpha(col, 0.22);
      this.roundRect(ctx, x + 13, byy, bw, bh, 7); ctx.fill();
      ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.4;
      this.roundRect(ctx, x + 13.7, byy + 0.7, bw - 1.4, bh - 1.4, 7); ctx.stroke();
      ui.text("TAKE", x + cardW / 2, byy + 20, { align: "center", size: 13, bold: true, color: bhov ? "#1a1207" : col });
      ctx.restore();

      if ((hov || bhov) && ui.clicked && !ui.pointerConsumed) {
        ui.pointerConsumed = true;
        if (run.takeCarousel(i)) { audio.play("coin"); this.battleSig = ""; }
      }
    });
    if (ui.clicked) ui.pointerConsumed = true;
  }

  /**
   * A unit's battle-style mark: an arrow-and-line glyph saying how it opens the
   * fight. Drawn small on the board so a comp's shape reads at a glance.
   */
  private styleGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number, def: BattleStyleDef) {
    ctx.save();
    ctx.translate(cx, cy);
    ctx.lineJoin = "round"; ctx.lineCap = "round";
    ctx.strokeStyle = def.color; ctx.fillStyle = def.color; ctx.lineWidth = Math.max(1, r * 0.3);
    switch (def.id) {
      case "vanguard": // a wall
        ctx.beginPath(); ctx.moveTo(0, -r); ctx.lineTo(0, r); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(-r * 0.7, -r * 0.45); ctx.lineTo(-r * 0.1, 0); ctx.lineTo(-r * 0.7, r * 0.45); ctx.stroke();
        break;
      case "line": // straight arrow
        ctx.beginPath(); ctx.moveTo(-r, 0); ctx.lineTo(r * 0.55, 0); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(r * 0.15, -r * 0.5); ctx.lineTo(r, 0); ctx.lineTo(r * 0.15, r * 0.5); ctx.closePath(); ctx.fill();
        break;
      case "flanker": // a hooking arrow
        ctx.beginPath();
        ctx.moveTo(-r, r * 0.5);
        ctx.quadraticCurveTo(-r * 0.1, r * 0.6, r * 0.3, -r * 0.4);
        ctx.stroke();
        ctx.beginPath(); ctx.moveTo(r * 0.65, -r * 0.35); ctx.lineTo(r * 0.2, -r); ctx.lineTo(r * 0.75, -r * 0.85); ctx.closePath(); ctx.fill();
        break;
      case "artillery": // an anchored mark with an arc over it
        ctx.beginPath(); ctx.arc(0, r * 0.25, r * 0.35, 0, Math.PI * 2); ctx.fill();
        ctx.beginPath(); ctx.arc(0, r * 0.25, r * 0.95, Math.PI * 1.15, Math.PI * 1.85); ctx.stroke();
        break;
      case "infiltrator": // a dashed jump over the line
        ctx.setLineDash([r * 0.42, r * 0.34]);
        ctx.beginPath(); ctx.arc(0, r * 0.6, r, Math.PI * 1.12, Math.PI * 1.88); ctx.stroke();
        ctx.setLineDash([]);
        ctx.beginPath(); ctx.moveTo(r * 0.45, -r * 0.5); ctx.lineTo(r * 1.05, -r * 0.05); ctx.lineTo(r * 0.4, r * 0.1); ctx.closePath(); ctx.fill();
        break;
    }
    ctx.restore();
  }

  /** A tiny weather mark for the condition chip: sun, rain, snow or cloud. */
  private weatherGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number, c: Condition, time: number) {
    ctx.save();
    ctx.translate(cx, cy);
    ctx.fillStyle = c.color; ctx.strokeStyle = c.color; ctx.lineWidth = 1.2;
    if (c.weather === "clear") {
      ctx.beginPath(); ctx.arc(0, 0, r * 0.5, 0, Math.PI * 2); ctx.fill();
      for (let i = 0; i < 8; i++) {
        const a = (i / 8) * Math.PI * 2 + time * 0.25;
        ctx.beginPath();
        ctx.moveTo(Math.cos(a) * r * 0.7, Math.sin(a) * r * 0.7);
        ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r);
        ctx.stroke();
      }
    } else {
      // A cloud for everything else, with rain or snow beneath.
      ctx.beginPath();
      ctx.arc(-r * 0.35, -r * 0.1, r * 0.45, 0, Math.PI * 2);
      ctx.arc(r * 0.2, -r * 0.2, r * 0.55, 0, Math.PI * 2);
      ctx.arc(r * 0.55, r * 0.05, r * 0.35, 0, Math.PI * 2);
      ctx.fill();
      if (c.weather === "rain") {
        for (const dx of [-0.4, 0.2, 0.7]) {
          ctx.beginPath(); ctx.moveTo(dx * r, r * 0.5); ctx.lineTo(dx * r - r * 0.15, r * 1.05); ctx.stroke();
        }
      } else if (c.weather === "snow") {
        for (const dx of [-0.4, 0.25, 0.75]) {
          ctx.beginPath(); ctx.arc(dx * r, r * 0.8, r * 0.16, 0, Math.PI * 2); ctx.fill();
        }
      }
    }
    ctx.restore();
  }

  /** The battlefield tooltip: what the weather does, to whom, on both sides. */
  private drawCondTip(W: number, H: number) {
    if (!this.condTip) return;
    const ctx = ui.ctx;
    const c = this.condTip.c;
    const traitName = (id: string) => TRAITS.find((t) => t.id === id)?.name ?? id;
    const effects = conditionLines(c, traitName);
    const desc = this.wrap(c.desc, 38);
    const pw = 264;
    const ph = 44 + desc.length * 15 + (effects.length ? 12 + effects.length * 16 : 0) + 20;
    let px = this.condTip.x, py = this.condTip.y;
    if (px + pw > W - 4) px = Math.max(4, W - pw - 4);
    py = Math.max(4, Math.min(py, H - ph - 4));
    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 14; ctx.shadowOffsetY = 4;
    ctx.fillStyle = "rgba(18,14,9,0.98)"; this.roundRect(ctx, px, py, pw, ph, 9); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = withAlpha(c.color, 0.95); ctx.lineWidth = 1.5;
    this.roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke();
    this.weatherGlyph(ctx, px + 20, py + 22, 9, c, 0);
    ui.text(c.name, px + 38, py + 26, { size: 14.5, bold: true, color: c.color, font: "Georgia, serif" });
    let ly = py + 46;
    for (const ln of desc) { ui.text(ln, px + 14, ly, { size: 11, color: "#cabfa4" }); ly += 15; }
    if (effects.length) {
      ctx.strokeStyle = "rgba(255,255,255,0.09)"; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(px + 12, ly); ctx.lineTo(px + pw - 12, ly); ctx.stroke();
      ly += 16;
      for (const ln of effects) { ui.text("◆ " + ln, px + 14, ly, { size: 11.5, bold: true, color: "#e7ddc4" }); ly += 16; }
    }
    ui.text("Applies to both warbands.", px + 14, ly + 6, { size: 9.5, color: "#8a8278" });
  }

  /** Tooltip for the run's commander plate — who they are and what they change. */
  private drawCmdTip(W: number, H: number) {
    if (!this.cmdTip) return;
    const ctx = ui.ctx;
    const c = this.cmdTip.c;
    const id = commanderIdentity(c);
    const col = id?.color ?? "#caa56a";
    const lines = this.wrap(c.perk, 34);
    const pw = 236, ph = 62 + lines.length * 16 + 10;
    let px = this.cmdTip.x, py = this.cmdTip.y;
    if (px + pw > W - 4) px = Math.max(4, this.cmdTip.x - pw - 216);
    py = Math.max(4, Math.min(py, H - ph - 4));
    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 14; ctx.shadowOffsetY = 4;
    ctx.fillStyle = "rgba(18,14,9,0.98)"; this.roundRect(ctx, px, py, pw, ph, 9); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.5;
    this.roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke();
    this.crest(ctx, px + 26, py + 30, 18, c, 0);
    ui.text(id?.name ?? c.id, px + 52, py + 24, { size: 15, bold: true, color: "#f2e8d0", font: "Georgia, serif" });
    ui.text(id?.title ?? "", px + 52, py + 40, { size: 10.5, color: col });
    ctx.strokeStyle = "rgba(255,255,255,0.09)"; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(px + 12, py + 52); ctx.lineTo(px + pw - 12, py + 52); ctx.stroke();
    let ly = py + 70;
    for (const ln of lines) { ui.text(ln, px + 14, ly, { size: 11.5, color: "#d8cdb4" }); ly += 16; }
  }

  /** Tooltip for an owned augment in the sidebar. */
  private drawAugTip(W: number, H: number) {
    if (!this.augTip) return;
    const ctx = ui.ctx;
    const a = this.augTip.a;
    const col = AUG_COLOR[a.tier];
    const lines = this.wrap(a.desc, 32);
    const pw = 220, ph = 46 + lines.length * 16 + 10;
    let px = this.augTip.x, py = this.augTip.y;
    if (px + pw > W - 4) px = Math.max(4, this.augTip.x - pw - 220);
    py = Math.max(4, Math.min(py, H - ph - 4));
    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 14; ctx.shadowOffsetY = 4;
    ctx.fillStyle = "rgba(18,14,9,0.98)"; this.roundRect(ctx, px, py, pw, ph, 9); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.5;
    this.roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke();
    this.augSigil(ctx, px + 22, py + 24, 12, a, 0);
    ui.text(a.name, px + 44, py + 22, { size: 14, bold: true, color: col, font: "Georgia, serif" });
    ui.text(`${a.tier} augment`, px + 44, py + 36, { size: 9.5, color: "#8a8278" });
    let ly = py + 58;
    for (const ln of lines) { ui.text(ln, px + 14, ly, { size: 11.5, color: "#d8cdb4" }); ly += 16; }
  }

  /**
   * The scout panel: what a rival would field right now, their level and their
   * synergies — so you can read the lobby and adapt before spending gold.
   */
  private drawScoutPanel(W: number, H: number, run: WarbandRun, time: number) {
    const s = run.scout(this.scoutId);
    if (!s) { this.scoutId = -1; return; }
    const ctx = ui.ctx;
    // Group their board by type+star so it reads as a comp, not a pile.
    const groups = new Map<string, { type: string; star: number; n: number }>();
    for (const u of s.board) {
      const k = `${u.type}:${u.star ?? 1}`;
      const g = groups.get(k);
      if (g) g.n++; else groups.set(k, { type: u.type, star: u.star ?? 1, n: 1 });
    }
    const rows = [...groups.values()].sort((a, b) => b.star - a.star || (UNIT_TIER[b.type] ?? 0) - (UNIT_TIER[a.type] ?? 0));
    const pw = 300;
    const ph = Math.min(H - 100, 78 + Math.max(1, rows.length) * 26 + (s.traits.length ? s.traits.length * 18 + 24 : 0));
    const px = 224, py = 88;
    this.scoutRect = { x: px, y: py, w: pw, h: ph };

    ctx.save();
    ctx.shadowColor = "rgba(0,0,0,0.7)"; ctx.shadowBlur = 24; ctx.shadowOffsetY = 8;
    const g = ctx.createLinearGradient(0, py, 0, py + ph);
    g.addColorStop(0, "rgba(26,22,14,0.99)"); g.addColorStop(1, "rgba(12,9,5,0.99)");
    ctx.fillStyle = g; this.roundRect(ctx, px, py, pw, ph, 11); ctx.fill();
    ctx.restore();
    ctx.strokeStyle = withAlpha("#7fb0e8", 0.85); ctx.lineWidth = 1.8;
    this.roundRect(ctx, px + 1, py + 1, pw - 2, ph - 2, 10); ctx.stroke();

    ui.text(`🔭 ${s.name}`, px + 16, py + 26, { size: 17, bold: true, color: "#cfe0ff", font: "Georgia, serif" });
    ui.text(s.alive ? `Level ${s.level}` : "eliminated", px + pw - 16, py + 26, { align: "right", size: 12, bold: true, color: s.alive ? "#e7ddc4" : "#6f6a5c" });
    // Life bar.
    ctx.fillStyle = "rgba(0,0,0,0.6)"; ctx.fillRect(px + 16, py + 36, pw - 32, 9);
    ctx.fillStyle = s.alive ? "#7df2a9" : "#5a554d"; ctx.fillRect(px + 16, py + 36, (pw - 32) * Math.max(0, s.life) / 100, 9);
    ui.text(`${Math.max(0, s.life)} life`, px + pw - 16, py + 58, { align: "right", size: 10.5, color: "#9a917b" });
    ui.text(`${s.board.length} deployed`, px + 16, py + 58, { size: 10.5, color: "#9a917b" });

    // Their comp.
    ctx.strokeStyle = "rgba(255,255,255,0.08)"; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(px + 12, py + 68); ctx.lineTo(px + pw - 12, py + 68); ctx.stroke();
    let y = py + 84;
    if (!rows.length) ui.text("— no warband fielded yet —", px + 16, y, { size: 11.5, color: "#6f6a5c" });
    for (const r of rows) {
      if (y + 10 > py + ph) break;
      const tier = UNIT_TIER[r.type] ?? 1;
      ctx.fillStyle = withAlpha(TIER_COLOR[tier], 0.16);
      this.roundRect(ctx, px + 14, y - 13, pw - 28, 22, 5); ctx.fill();
      ctx.fillStyle = TIER_COLOR[tier]; ctx.fillRect(px + 14, y - 13, 3, 22);
      ui.text(UNITS[r.type]?.name ?? r.type, px + 24, y + 3, { size: 12, bold: true, color: "#e7ddc4" });
      ui.text(stars(r.star), px + pw - 54, y + 3, { align: "right", size: 11, color: r.star >= 3 ? "#ffd24a" : r.star === 2 ? "#cfe0ff" : "#9a917b" });
      if (r.n > 1) ui.text(`×${r.n}`, px + pw - 24, y + 3, { align: "right", size: 11, bold: true, color: "#9a917b" });
      y += 26;
    }
    // Their synergies.
    if (s.traits.length && y + 24 < py + ph) {
      ctx.strokeStyle = "rgba(255,255,255,0.08)"; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(px + 12, y - 4); ctx.lineTo(px + pw - 12, y - 4); ctx.stroke();
      y += 14;
      for (const at of s.traits) {
        if (y + 6 > py + ph) break;
        ui.text(`◆ ${at.trait.name} ×${at.count}`, px + 18, y, { size: 11, bold: true, color: at.trait.color });
        ui.text(at.tier?.label ?? "", px + pw - 18, y, { align: "right", size: 9.5, color: "#8a8278" });
        y += 18;
      }
    }
    // Close.
    const cbx = px + pw - 30, cby = py + 8;
    const chov = ui.mx >= cbx && ui.mx <= cbx + 22 && ui.my >= cby && ui.my <= cby + 22;
    ui.text("✕", cbx + 11, cby + 16, { align: "center", size: 15, bold: true, color: chov ? "#ff9a8a" : "#8a8278" });
    if (ui.clicked && !ui.pointerConsumed) {
      const inside = ui.mx >= px && ui.mx <= px + pw && ui.my >= py && ui.my <= py + ph;
      if (chov || !inside) { ui.pointerConsumed = inside; this.scoutId = -1; }
    }
  }

  /**
   * The clash: two plates slam in from opposite edges with a VS between them,
   * holding the fight for a beat so a round starts as an event rather than
   * units quietly beginning to walk.
   */
  private drawIntro(bx: number, by: number, bw: number, bh: number, run: WarbandRun) {
    const ctx = ui.ctx;
    const t = 1 - this.introT / INTRO_LEN; // 0 → 1 across the banner
    const slide = easeOut(Math.min(1, t * 2.2));
    const fade = t > 0.82 ? 1 - (t - 0.82) / 0.18 : 1; // clear out at the end
    const cy = by + bh * 0.42;
    const plateH = 56;

    ctx.save();
    ctx.globalAlpha = fade;
    ctx.beginPath(); ctx.rect(bx, by, bw, bh); ctx.clip();
    // A dark band behind the banner.
    ctx.fillStyle = "rgba(6,4,2,0.72)";
    ctx.fillRect(bx, cy - plateH / 2 - 10, bw, plateH + 20);

    const drawPlate = (label: string, sub: string, colour: string, fromLeft: boolean) => {
      const w = bw * 0.42;
      const rest = fromLeft ? bx + bw * 0.5 - w - 26 : bx + bw * 0.5 + 26;
      const off = (1 - slide) * (bw * 0.55) * (fromLeft ? -1 : 1);
      const x = rest + off;
      const g = ctx.createLinearGradient(x, 0, x + w, 0);
      if (fromLeft) { g.addColorStop(0, withAlpha(colour, 0.06)); g.addColorStop(1, withAlpha(colour, 0.42)); }
      else { g.addColorStop(0, withAlpha(colour, 0.42)); g.addColorStop(1, withAlpha(colour, 0.06)); }
      ctx.fillStyle = g;
      ctx.fillRect(x, cy - plateH / 2, w, plateH);
      ctx.fillStyle = colour;
      ctx.fillRect(fromLeft ? x + w - 2.5 : x, cy - plateH / 2, 2.5, plateH);
      ui.text(label, fromLeft ? x + w - 16 : x + 16, cy - 2, {
        align: fromLeft ? "right" : "left", size: 22, bold: true, color: "#f7efdc", font: "Georgia, serif",
      });
      ui.text(sub, fromLeft ? x + w - 16 : x + 16, cy + 18, {
        align: fromLeft ? "right" : "left", size: 11.5, color: withAlpha(colour, 0.95),
      });
    };
    drawPlate("YOUR WARBAND", `${run.deployedCount()} deployed · ${run.activeTraits().length} synergies`, "#7fb0e8", true);
    drawPlate(run.pendingFoeName(), run.isCreepRound() ? "monster camp" : "rival warband", "#e0786a", false);

    // The VS mark, punching in on its own beat.
    const pop = 0.5 + easeOut(Math.min(1, Math.max(0, t - 0.16) * 3.4)) * 0.5;
    ctx.save();
    ctx.translate(bx + bw / 2, cy);
    ctx.scale(pop, pop);
    ctx.shadowColor = "#ffd24a"; ctx.shadowBlur = 24;
    ui.text("VS", 0, 12, { align: "center", size: 40, bold: true, color: "#ffd24a", font: "Georgia, serif" });
    ctx.restore();
    ctx.restore();
  }

  /**
   * The result flourish: rays and a scale-popped banner. Winning a round should
   * feel like something, not read as a line of text in the shop bar.
   */
  private drawResultFlourish(W: number, H: number, run: WarbandRun, time: number) {
    const r = run.lastResult;
    if (!r || this.resultT < 0) return;
    const ctx = ui.ctx;
    const t = this.resultT;
    const fade = t > 1.5 ? Math.max(0, 1 - (t - 1.5) / 0.6) : 1;
    if (fade <= 0) return;
    const cx = W / 2, cy = H * 0.34;
    const col = r.won ? "#7df2a9" : "#e0564a";

    ctx.save();
    ctx.globalAlpha = fade;
    // Radiant rays behind a win; a dull pall behind a loss.
    if (r.won) {
      ctx.save();
      ctx.translate(cx, cy); ctx.rotate(time * 0.22); ctx.globalAlpha = fade * 0.1;
      for (let i = 0; i < 14; i++) {
        ctx.rotate((Math.PI * 2) / 14);
        ctx.fillStyle = "#ffe9b0";
        ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(Math.max(W, H) * 0.5, -22); ctx.lineTo(Math.max(W, H) * 0.5, 22); ctx.closePath(); ctx.fill();
      }
      ctx.restore();
    }
    const glow = ctx.createRadialGradient(cx, cy, 10, cx, cy, 300);
    glow.addColorStop(0, withAlpha(col, 0.2)); glow.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H);

    // Scale-pop banner.
    const pop = 1 + 0.5 * Math.exp(-t * 5) * Math.cos(t * 13);
    ctx.save();
    ctx.translate(cx, cy); ctx.scale(pop, pop); ctx.translate(-cx, -cy);
    ctx.save();
    ctx.shadowColor = col; ctx.shadowBlur = 26;
    ui.text(r.won ? "VICTORY" : "DEFEAT", cx, cy, {
      align: "center", size: 58, bold: true, color: r.won ? "#f7efdc" : "#e8bfb6", font: "Georgia, serif",
    });
    ctx.restore();
    ui.text(
      r.creep && r.won ? `${r.foe} cleared — ${r.relics} relic${r.relics === 1 ? "" : "s"} + ${r.gold}g`
        : r.won ? `${r.foe} broken · ${r.youLeft} still standing`
          : `${r.foe} holds · −${r.dmg} life`,
      cx, cy + 34, { align: "center", size: 16, bold: true, color: col, font: "Georgia, serif" },
    );
    ctx.restore();
    ctx.restore();
  }

  /** A forge flourish when two components fuse into a full relic. */
  private drawFusion(bx: number, by: number, bw: number, bh: number) {
    if (!this.fusion) return;
    const it = itemDef(this.fusion.item);
    if (!it) return;
    const ctx = ui.ctx;
    const t = this.fusion.t / 1.7;
    const a = Math.max(0, 1 - Math.pow(t, 2));
    const cx = bx + bw / 2, cy = by + bh * 0.62;
    ctx.save();
    ctx.globalAlpha = a;
    // Sparks flying outward from the forge point.
    for (let i = 0; i < 14; i++) {
      const ang = (i / 14) * Math.PI * 2 + t * 1.2;
      const d = 18 + t * 96;
      ctx.fillStyle = withAlpha(it.color, 0.85);
      ctx.beginPath(); ctx.arc(cx + Math.cos(ang) * d, cy + Math.sin(ang) * d * 0.55, Math.max(0.6, 3 * (1 - t)), 0, Math.PI * 2); ctx.fill();
    }
    const pop = 1 + 0.35 * Math.exp(-t * 6) * Math.cos(t * 15);
    ctx.translate(cx, cy); ctx.scale(pop, pop); ctx.translate(-cx, -cy);
    ctx.save();
    ctx.shadowColor = it.color; ctx.shadowBlur = 20;
    ctx.fillStyle = "rgba(10,7,4,0.85)";
    ctx.beginPath(); ctx.arc(cx, cy - 16, 24, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = it.color; ctx.lineWidth = 2; ctx.stroke();
    this.itemIcon(ctx, cx, cy - 16, 14, it);
    ui.text("RELIC FORGED", cx, cy + 24, { align: "center", size: 13, bold: true, color: "#f7efdc", font: "Georgia, serif" });
    ui.text(it.name, cx, cy + 42, { align: "center", size: 16, bold: true, color: it.color, font: "Georgia, serif" });
    ctx.restore();
    ctx.restore();
  }

  /** A star-up flourish over the board when three pieces merge. */
  private drawStarUp(bx: number, by: number, bw: number, bh: number) {
    if (!this.starUp) return;
    const ctx = ui.ctx;
    const t = this.starUp.t / 1.6;
    const a = Math.max(0, 1 - t);
    const cx = bx + bw / 2, cy = by + bh * 0.32;
    const col = this.starUp.star >= 3 ? "#ffd24a" : "#cfe0ff";
    ctx.save();
    ctx.globalAlpha = a;
    // Expanding rings.
    for (let i = 0; i < 3; i++) {
      const rt = Math.max(0, t - i * 0.12);
      const r = 20 + rt * 190;
      ctx.strokeStyle = withAlpha(col, 0.5 * (1 - rt));
      ctx.lineWidth = 3 * (1 - rt);
      ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.stroke();
    }
    // Scale-pop text.
    const pop = 1 + 0.4 * Math.exp(-t * 6) * Math.cos(t * 16);
    ctx.translate(cx, cy); ctx.scale(pop, pop); ctx.translate(-cx, -cy);
    ctx.save();
    ctx.shadowColor = col; ctx.shadowBlur = 22;
    ui.text(stars(this.starUp.star), cx, cy - 8, { align: "center", size: 34, bold: true, color: col });
    ui.text(`${shortName(this.starUp.type).toUpperCase()} UPGRADED`, cx, cy + 22, {
      align: "center", size: 17, bold: true, color: "#f7efdc", font: "Georgia, serif",
    });
    ctx.restore();
    ctx.restore();
  }

  /**
   * Draw a unit's actual animated sprite as a card portrait. This is the same
   * `drawUnit` the arena uses, on a throwaway entity built from the unit
   * definition — so a card shows the thing you're buying, idling and breathing,
   * rather than a name and a number.
   */
  private portrait(ctx: CanvasRenderingContext2D, cx: number, cy: number, px: number, type: string, star: number, time: number) {
    const def = UNITS[type];
    if (!def) return;
    let e = this.portraits.get(type);
    if (!e) {
      e = makeEntity();
      e.type = type;
      e.team = Team.Player;
      e.x = 0; e.y = 0; e.prevX = 0; e.prevY = 0;
      this.portraits.set(type, e);
    }
    // Refresh the fields the renderer reads (star tier changes the trim).
    e.radius = def.radius;
    e.maxHp = def.hp; e.hp = def.hp;
    e.attack = def.attack; e.range = def.range;
    e.attackInterval = def.attackInterval;
    e.armor = def.armor; e.armorClass = def.armorClass ?? ArmorClass.Infantry;
    e.speed = def.speed;
    e.variantRarity = Math.max(0, Math.min(2, star - 1));
    e.animPhase = time * 0.6 + type.length; // a gentle idle sway, offset per type
    e.facing = -0.35;                       // three-quarter view, facing the reader
    e.hitFlash = 0;
    const scale = px / TILE;
    ctx.save();
    ctx.translate(cx, cy);
    ctx.scale(scale, scale);
    ctx.translate(-e.x, -e.y);
    try { drawUnit(ctx, e, time, 0); } catch { /* a bad sprite must never kill the frame */ }
    ctx.restore();
  }

  /** A small stone plinth for a portrait to stand on. */
  private plinth(ctx: CanvasRenderingContext2D, cx: number, cy: number, rx: number, color: string) {
    ctx.save();
    ctx.fillStyle = "rgba(0,0,0,0.4)";
    ctx.beginPath(); ctx.ellipse(cx, cy, rx, rx * 0.34, 0, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = withAlpha(color, 0.55); ctx.lineWidth = 1.2;
    ctx.beginPath(); ctx.ellipse(cx, cy, rx, rx * 0.34, 0, 0, Math.PI * 2); ctx.stroke();
    ctx.restore();
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
  private drawArena(x: number, y: number, w: number, h: number, cellW: number, cellH: number, time: number, cond: Condition) {
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

    // ---- the round's weather, inside the arena ----
    if (cond.weather !== "clear") {
      ctx.save();
      ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip();
      // A colour wash for the sky.
      const wash = cond.weather === "rain" ? "rgba(40,55,80,0.20)"
        : cond.weather === "snow" ? "rgba(200,215,235,0.13)"
          : "rgba(48,46,52,0.34)";
      ctx.fillStyle = wash; ctx.fillRect(x, y, w, h);
      // A breath of the condition's own colour, so mud reads brown and frost blue.
      ctx.fillStyle = withAlpha(cond.color, 0.09); ctx.fillRect(x, y, w, h);
      if (cond.weather === "rain") {
        // Slanted streaks, marching on a fixed cycle so it stays smooth.
        ctx.strokeStyle = "rgba(180,205,235,0.34)"; ctx.lineWidth = 1;
        ctx.beginPath();
        for (let i = 0; i < 90; i++) {
          const sx0 = ((i * 137.5) % w);
          const speed = 420 + (i % 5) * 90;
          const sy0 = ((i * 61.7 + time * speed) % (h + 40)) - 20;
          const len = 12 + (i % 4) * 4;
          ctx.moveTo(x + sx0, y + sy0);
          ctx.lineTo(x + sx0 - len * 0.32, y + sy0 + len);
        }
        ctx.stroke();
      } else if (cond.weather === "snow") {
        ctx.fillStyle = "rgba(235,244,255,0.62)";
        for (let i = 0; i < 70; i++) {
          const drift = Math.sin(time * 0.8 + i) * 14;
          const sx0 = ((i * 149.3) % w) + drift;
          const sy0 = ((i * 83.1 + time * 52) % (h + 20)) - 10;
          const r = 1 + (i % 3) * 0.7;
          ctx.beginPath(); ctx.arc(x + sx0, y + sy0, r, 0, Math.PI * 2); ctx.fill();
        }
      } else { // overcast — heavy cloud shadows drifting across the field
        for (let i = 0; i < 5; i++) {
          const cy2 = y + h * (0.12 + i * 0.19);
          const cx2 = x + ((i * 337 + time * 20) % (w + 420)) - 210;
          const g2 = ctx.createRadialGradient(cx2, cy2, 20, cx2, cy2, 210);
          g2.addColorStop(0, "rgba(0,0,0,0.30)"); g2.addColorStop(1, "rgba(0,0,0,0)");
          ctx.fillStyle = g2; ctx.fillRect(x, y, w, h);
        }
      }
      ctx.restore();
    }

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
    // "Setup" is how the board *reads* (your side only, no health bars); it also
    // covers the augment pause so the board doesn't flash back to the last fight.
    // Placement itself stays shop-only.
    const setup = run.phase === "shop" || run.phase === "augment" || run.phase === "draft";
    const interactive = run.phase === "shop";
    // Monster camps are never hidden — you get to plan against what you can see.
    const reveal = run.isCreepRound();
    const cellW = w / GRID_COLS;
    const cellH = h / GRID_ROWS;

    this.drawArena(x, y, w, h, cellW, cellH, time, run.condition);

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

    // ---- enemy fog (setup) — camps stand in the open instead ----
    if (setup && !reveal) {
      ctx.fillStyle = "rgba(8,5,3,0.66)";
      ctx.fillRect(x + w / 2 + 1.5, y + 1.5, w / 2 - 3, h - 3);
      ui.text("🔒 Enemy warband hidden", x + w * 0.74, y + 30, { align: "center", size: 15, bold: true, color: "#caa" });
      ui.text("scout a rival on the left to see theirs", x + w * 0.74, y + 50, { align: "center", size: 12, color: "#8a8278" });
    } else if (setup && reveal) {
      const camp = run.pendingCamp!;
      ui.text(camp.name, x + w * 0.74, y + 26, { align: "center", size: 16, bold: true, color: "#c8a86a", font: "Georgia, serif" });
      ui.text(camp.blurb, x + w * 0.74, y + 44, { align: "center", size: 11.5, color: "#8a8278" });
    }

    // ---- placement + sell interaction (setup only) ----
    const deployment = setup ? run.deployment() : [];
    this.unitTip = null;
    let heldEntityId = -1;
    if (interactive) {
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
      .filter((e) => e.alive && e.kind === Kind.Unit && e.type !== "villager" && (!setup || reveal || e.team === 0))
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
      // A revealed camp gets the same exact-cell treatment on the enemy half.
      if (reveal) {
        b.world.entities
          .filter((e) => e.alive && e.kind === Kind.Unit && e.type !== "villager" && e.team !== 0)
          .sort((p, q) => p.id - q.id)
          .forEach((e, k) => {
            const u = run.pendingOpp[k];
            if (u?.col != null && u.row != null) cellByEnt.set(e.id, { col: u.col, row: u.row });
          });
      }
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
      // Battle-style mark at the unit's shoulder in setup, so a comp's shape
      // (who holds, who sweeps, who jumps their line) reads at a glance.
      if (setup && !lifted && e.team === 0) {
        this.styleGlyph(ctx, sx + cellW * 0.28, sy - cellH * 0.12, Math.min(6.5, cellW * 0.1), styleDef(e.type));
      }
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
          const it = itemDef(id); if (!it) continue;
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

  private pieceCard(x: number, y: number, w: number, h: number, p: Piece, deployed: boolean, time: number, held = false) {
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
    // The unit stands on the card; its name sits on a plate along the bottom.
    ctx.save();
    ctx.beginPath(); ctx.rect(x + 2, y + 2, w - 4, h - 18); ctx.clip();
    this.plinth(ctx, x + w / 2, y + h - 24, 16, TIER_COLOR[tier]);
    this.portrait(ctx, x + w / 2, y + h - 24, 33, p.type, p.star, time);
    ctx.restore();
    if (p.star >= 2) {
      ui.text(stars(p.star), x + w / 2, y + 13, { align: "center", size: 11, color: p.star >= 3 ? "#ffd24a" : "#cfe0ff" });
    }
    ctx.fillStyle = "rgba(10,7,4,0.82)";
    this.roundRect(ctx, x + 2, y + h - 17, w - 4, 15, 4); ctx.fill();
    ui.text(shortName(p.type), x + w / 2, y + h - 6, { align: "center", size: 10.5, bold: true, color: "#e7ddc4" });
    // Equipped relics as mini icons along the bottom.
    let ix = x + 11;
    for (const id of p.items) {
      const it = itemDef(id); if (!it) continue;
      ctx.fillStyle = "rgba(8,6,3,0.9)"; ctx.beginPath(); ctx.arc(ix, y + 13, 6, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = it.color; ctx.lineWidth = 1; ctx.stroke();
      this.itemIcon(ctx, ix, y + 13, 4.3, it);
      ix += 14;
    }
    ctx.globalAlpha = 1;
    if (hover && this.heldPiece < 0) this.unitTip = { p, x: x + w + 6, y: y - 70 };
  }

  private shopCard(
    x: number, y: number, w: number, h: number, type: string, affordable: boolean,
    poolLeft: number, blocked: "gold" | "bench" | undefined, time: number, onClick: () => void,
  ) {
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
    // The unit itself, standing on a lit plinth — the card's centrepiece.
    const feet = y + oy + 58;
    ctx.save();
    ctx.globalAlpha = affordable ? 1 : 0.4;
    // A soft pool of tier light behind the figure.
    const halo = ctx.createRadialGradient(x + w / 2, feet - 14, 2, x + w / 2, feet - 14, 34);
    halo.addColorStop(0, withAlpha(col, 0.3)); halo.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = halo; ctx.fillRect(x + 2, y + oy + 8, w - 4, 62);
    this.plinth(ctx, x + w / 2, feet, 21, col);
    ctx.save();
    ctx.beginPath(); ctx.rect(x + 3, y + oy + 8, w - 6, 54); ctx.clip();
    this.portrait(ctx, x + w / 2, feet, 46, type, 1, time);
    ctx.restore();
    ctx.globalAlpha = 1;
    ctx.restore();
    const nm = shortName(type);
    ui.text(nm, x + w / 2, y + oy + 78, {
      align: "center", size: nm.length > 10 ? 11 : 13.5, bold: true,
      color: affordable ? "#f2e8d0" : "#6f6a5c", font: "Georgia, serif",
    });
    // Synergies, centred under the name.
    const tt = traitsOf(type).slice(0, 2);
    const label = tt.map((t2) => t2.name).join(" · ");
    if (label) {
      ui.text(label, x + w / 2, y + oy + 92, {
        align: "center", size: 9, color: affordable ? withAlpha(tt[0].color, 0.95) : withAlpha(tt[0].color, 0.45),
      });
    }
    ui.text(`T${tier}`, x + 9, y + oy + h - 8, { size: 9.5, bold: true, color: withAlpha(col, affordable ? 1 : 0.5) });
    // How it opens a fight, as a corner mark — the name lives in the tooltip.
    const sd2 = styleDef(type);
    ctx.globalAlpha = affordable ? 1 : 0.45;
    this.styleGlyph(ctx, x + w - 15, y + oy + 15, 6, sd2);
    ctx.globalAlpha = 1;
    // Copies left in the shared lobby pool — or why this one can't be bought.
    if (blocked === "bench") {
      ui.text("bench full", x + w / 2, y + oy + h - 8, { align: "center", size: 9.5, bold: true, color: "#e0786a" });
    } else {
      ui.text(`${poolLeft} left`, x + w / 2, y + oy + h - 8, { align: "center", size: 9.5, color: poolLeft <= 3 ? "#e0786a" : "#8a8278" });
    }
    // Gold coin with the cost.
    this.coin(ctx, x + w - 16, y + oy + h - 12, tier, affordable);
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
      // ---- components: deliberately simpler, "raw part" shapes ----
      case "blade": // a short blade
        ctx.beginPath();
        ctx.moveTo(0, -h); ctx.lineTo(h * 0.26, -h * 0.35); ctx.lineTo(h * 0.26, h * 0.3);
        ctx.lineTo(-h * 0.26, h * 0.3); ctx.lineTo(-h * 0.26, -h * 0.35); ctx.closePath(); ctx.fill(); ctx.stroke();
        ctx.fillStyle = "#6a4a2a"; ctx.beginPath(); ctx.rect(-h * 0.4, h * 0.28, h * 0.8, h * 0.22); ctx.fill(); ctx.stroke();
        break;
      case "plate": // a riveted armour plate
        ctx.beginPath();
        ctx.moveTo(-h * 0.7, -h * 0.72); ctx.lineTo(h * 0.7, -h * 0.72);
        ctx.lineTo(h * 0.82, h * 0.5); ctx.quadraticCurveTo(0, h * 0.95, -h * 0.82, h * 0.5);
        ctx.closePath(); ctx.fill(); ctx.stroke();
        ctx.fillStyle = "rgba(0,0,0,0.4)";
        for (const rx of [-0.45, 0.45]) { ctx.beginPath(); ctx.arc(rx * h, -h * 0.45, h * 0.12, 0, Math.PI * 2); ctx.fill(); }
        break;
      case "hide": // a stretched pelt
        ctx.beginPath();
        ctx.moveTo(0, -h);
        ctx.quadraticCurveTo(h * 0.95, -h * 0.6, h * 0.6, 0);
        ctx.quadraticCurveTo(h * 0.95, h * 0.7, 0, h);
        ctx.quadraticCurveTo(-h * 0.95, h * 0.7, -h * 0.6, 0);
        ctx.quadraticCurveTo(-h * 0.95, -h * 0.6, 0, -h);
        ctx.closePath(); ctx.fill(); ctx.stroke();
        break;
      case "spur": // a rowel spur
        ctx.beginPath(); ctx.arc(0, 0, h * 0.42, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
        for (let i = 0; i < 6; i++) {
          const a2 = (i / 6) * Math.PI * 2;
          ctx.beginPath();
          ctx.moveTo(Math.cos(a2) * h * 0.42, Math.sin(a2) * h * 0.42);
          ctx.lineTo(Math.cos(a2) * h, Math.sin(a2) * h);
          ctx.lineTo(Math.cos(a2 + 0.4) * h * 0.42, Math.sin(a2 + 0.4) * h * 0.42);
          ctx.closePath(); ctx.fill(); ctx.stroke();
        }
        break;
      case "sigil": { // a rune diamond
        ctx.beginPath();
        ctx.moveTo(0, -h); ctx.lineTo(h * 0.72, 0); ctx.lineTo(0, h); ctx.lineTo(-h * 0.72, 0);
        ctx.closePath(); ctx.fill(); ctx.stroke();
        ctx.strokeStyle = "rgba(0,0,0,0.45)"; ctx.lineWidth = s * 0.14;
        ctx.beginPath(); ctx.moveTo(0, -h * 0.5); ctx.lineTo(0, h * 0.5); ctx.moveTo(-h * 0.34, 0); ctx.lineTo(h * 0.34, 0); ctx.stroke();
        break;
      }
      // ---- the three grid-completing relics ----
      case "duelistedge": // slim rapier over a buckler
        ctx.beginPath(); ctx.arc(-h * 0.35, h * 0.3, h * 0.45, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
        ctx.fillStyle = "#e8e0cc";
        ctx.beginPath();
        ctx.moveTo(h * 0.15, -h); ctx.lineTo(h * 0.34, -h * 0.75); ctx.lineTo(h * 0.34, h * 0.55);
        ctx.lineTo(h * 0.1, h * 0.55); ctx.closePath(); ctx.fill(); ctx.stroke();
        break;
      case "reaverscleaver": // heavy cleaver
        ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = s * 0.2;
        ctx.beginPath(); ctx.moveTo(-h * 0.3, h); ctx.lineTo(h * 0.05, -h * 0.3); ctx.stroke();
        ctx.fillStyle = it.color; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = Math.max(1, s * 0.1);
        ctx.beginPath();
        ctx.moveTo(-h * 0.15, -h * 0.25); ctx.lineTo(h * 0.35, -h);
        ctx.quadraticCurveTo(h * 1.0, -h * 0.3, h * 0.5, h * 0.15);
        ctx.closePath(); ctx.fill(); ctx.stroke();
        break;
      case "dancersmail": // winged mail
        ctx.beginPath();
        ctx.moveTo(-h * 0.45, -h * 0.6); ctx.lineTo(h * 0.45, -h * 0.6); ctx.lineTo(h * 0.45, h * 0.35);
        ctx.quadraticCurveTo(0, h * 0.9, -h * 0.45, h * 0.35); ctx.closePath(); ctx.fill(); ctx.stroke();
        for (const dir of [-1, 1]) {
          ctx.beginPath();
          ctx.moveTo(dir * h * 0.45, -h * 0.45);
          ctx.quadraticCurveTo(dir * h * 1.05, -h * 0.55, dir * h * 0.95, h * 0.05);
          ctx.quadraticCurveTo(dir * h * 0.75, -h * 0.1, dir * h * 0.45, h * 0.05);
          ctx.closePath(); ctx.fill(); ctx.stroke();
        }
        break;
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
    const it = itemDef(this.itemTip.id);
    if (!it) return;
    const lines = this.buffLines(it.buff);
    // A component also lists every relic it can still become.
    const recipes = it.component ? buildsInto(it.id).map((r) => itemDef(r)!).filter(Boolean) : [];
    const pw = it.component ? 250 : 196;
    const ph = 58 + lines.length * 17 + (recipes.length ? 26 + Math.ceil(recipes.length / 2) * 15 + 14 : 20);
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
    ui.text(it.component ? "Component" : "Relic", px + 40, py + 34, { size: 10, color: "#9a917b" });
    let ly = py + 58;
    for (const ln of lines) { ui.text("◆ " + ln, px + 14, ly, { size: 12.5, bold: true, color: "#e7ddc4" }); ly += 17; }
    if (recipes.length) {
      ctx.strokeStyle = "rgba(255,255,255,0.09)"; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(px + 12, ly - 2); ctx.lineTo(px + pw - 12, ly - 2); ctx.stroke();
      ui.text("Builds into", px + 14, ly + 13, { size: 10, bold: true, color: "#9a917b" });
      ly += 26;
      recipes.forEach((r, i) => {
        const cx2 = px + 14 + (i % 2) * ((pw - 28) / 2);
        const cy2 = ly + Math.floor(i / 2) * 15;
        this.itemIcon(ctx, cx2 + 5, cy2 - 3, 5, r);
        ui.text(this.clip(r.name, 16), cx2 + 14, cy2, { size: 9.5, color: withAlpha(r.color, 0.95) });
      });
      ly += Math.ceil(recipes.length / 2) * 15;
      ui.text("Pair two components on one unit to forge it", px + 14, ly + 4, { size: 9.5, color: "#8a8278" });
    } else ui.text("Equip onto a unit · up to 3 each", px + 14, ly + 4, { size: 10, color: "#8a8278" });
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
    const relics = p.items.map((id) => itemDef(id)).filter(Boolean) as Item[];
    const ab = ABILITIES[p.type];
    // Effective stats = base × star, then relics (synergies add more in a fight).
    const sm = STAR_MULT[p.star] ?? 1;
    const st = { maxHp: Math.round(def.hp * sm), hp: 0, attack: Math.round(def.attack * sm), armor: def.armor, speed: def.speed };
    st.hp = st.maxHp;
    applyItems(st, p.items);
    const bonuses = Object.entries(def.bonus).map(([cls, amt]) => `+${amt} vs ${cls}`);

    const pw = 244;
    let ph = 50 + 56; // header + stat block (incl. RATE + DPS)
    ph += 52; // battle-style block
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
    const sd = styleDef(p.type);
    this.styleGlyph(ctx, px + pw - 24, py + 38, 6.5, sd);
    ui.text(sd.name, px + pw - 36, py + 41, { align: "right", size: 10.5, bold: true, color: sd.color });
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
    // How it enters a fight — as important as its stats for where you place it.
    div();
    this.styleGlyph(ctx, px + 20, y + 15, 6.5, sd);
    ui.text(sd.name, px + 32, y + 18, { size: 11.5, bold: true, color: sd.color });
    this.wrap(sd.desc, 52).slice(0, 2).forEach((ln, i) => {
      ui.text(ln, px + 14, y + 31 + i * 11, { size: 9, color: "#cabfa4" });
    });
    y += 52;
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
