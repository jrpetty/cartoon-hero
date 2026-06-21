// Warband Tactics — the auto-battler screen. Draws a WarbandRun: standings,
// your board, the shop, and the fight/result flow. Pure presentation; all rules
// live in sim/warband.ts.

import { ui } from "./ui";
import { PAL, withAlpha } from "../render/palette";
import { UNITS } from "../content/units";
import { WarbandRun, UNIT_TIER, Piece } from "../sim/warband";

const TIER_COLOR = ["#888888", "#9aa8b4", "#4caf50", "#3a78d8", "#9b5cf0", "#e0a020"];
const shortName = (type: string) => (UNITS[type]?.name ?? type).split(" ")[0];
const stars = (n: number) => "★".repeat(n);

export class WarbandScreen {
  draw(W: number, H: number, time: number, run: WarbandRun): "exit" | null {
    const ctx = ui.ctx;
    ctx.fillStyle = "#15110b";
    ctx.fillRect(0, 0, W, H);

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

    // ---- your board ----
    const bx = 232;
    const bw = W - bx - 16;
    ui.text(`Your Warband  (deploying top ${run.level})`, bx, 80, { size: 14, bold: true, color: PAL.uiAccent });
    const cap = run.level;
    const order = run.pieces.map((_, i) => i).sort((a, b) =>
      run.pieces[b].star - run.pieces[a].star || (UNIT_TIER[run.pieces[b].type] ?? 0) - (UNIT_TIER[run.pieces[a].type] ?? 0));
    const deployedSet = new Set(order.slice(0, cap));
    const cardW = 86;
    const cardH = 56;
    const perRow = Math.max(1, Math.floor(bw / (cardW + 8)));
    run.pieces.forEach((p, i) => {
      const col = i % perRow;
      const row = Math.floor(i / perRow);
      const cx = bx + col * (cardW + 8);
      const cy = 96 + row * (cardH + 8);
      this.pieceCard(cx, cy, cardW, cardH, p, deployedSet.has(i), () => { if (run.phase === "shop") run.sell(i); });
    });
    if (run.pieces.length === 0) ui.text("Buy units from the shop below…", bx, 120, { size: 13, color: "#9a917b" });

    // ---- shop / actions (bottom) ----
    const shopY = H - 132;
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
      // Buttons column on the right.
      const rxx = bx + 5 * (120 + 8) + 8;
      if (ui.button("Reroll  (2g)", rxx, shopY + 14, 130, 32, { disabled: run.gold < 2, size: 13, tooltip: ["New shop", "Costs 2 gold."] })) run.reroll();
      if (ui.button(`Level Up  (4g)`, rxx, shopY + 52, 130, 32, { disabled: run.gold < 4 || run.level >= 9, size: 13, tooltip: ["+4 XP", "Raises board size & shop odds."] })) run.buyXp();
      if (ui.button("⚔ FIGHT", rxx, shopY + 92, 130, 36, { accent: true, size: 16, tooltip: ["Send your warband to battle the next opponent."] })) run.fight();
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

  private pieceCard(x: number, y: number, w: number, h: number, p: Piece, deployed: boolean, onClick: () => void) {
    const ctx = ui.ctx;
    const tier = UNIT_TIER[p.type] ?? 1;
    const hover = ui.mx >= x && ui.mx <= x + w && ui.my >= y && ui.my <= y + h;
    ctx.fillStyle = deployed ? "rgba(40,34,20,0.95)" : "rgba(18,14,9,0.85)";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = withAlpha(TIER_COLOR[tier], deployed ? 1 : 0.5);
    ctx.lineWidth = deployed ? 2 : 1;
    ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
    ui.text(shortName(p.type), x + w / 2, y + 22, { align: "center", size: 12, bold: true, color: "#e7ddc4" });
    ui.text(stars(p.star), x + w / 2, y + 40, { align: "center", size: 13, color: p.star >= 3 ? "#ffd24a" : p.star === 2 ? "#cfe0ff" : "#9a917b" });
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
    ui.text(shortName(type), x + w / 2, y + 26, { align: "center", size: 14, bold: true, color: affordable ? "#e7ddc4" : "#6f6a5c" });
    ui.text(`Tier ${tier}`, x + 8, y + h - 10, { size: 10, color: TIER_COLOR[tier] });
    ui.text(`${tier}g`, x + w - 8, y + h - 10, { align: "right", size: 13, bold: true, color: affordable ? "#ffd24a" : "#7a6a3a" });
    if (hover && affordable && ui.clicked && !ui.pointerConsumed) { ui.pointerConsumed = true; onClick(); }
  }
}
