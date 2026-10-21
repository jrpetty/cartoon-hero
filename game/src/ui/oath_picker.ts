// The Oath picker: what advancing an age looks like now.
//
// Advancing used to be one button. It is still one decision, but now it is the
// decision of who the realm becomes, so it gets a proper screen: three cards
// side by side, each saying in plain lines what the Oath does, with its
// signature unit drawn on the card so you know what you are buying before you
// meet it in a fight. The match keeps running behind it — it is a choice, not
// a pause.

import { ui } from "./ui";
import { PAL, shade, withAlpha } from "../render/palette";
import { AGES } from "../content/tech";
import { AFFINITY, OathDef, oathsForAge } from "../content/oaths";
import { COMMANDERS } from "../content/commanders";
import { rarityByIndex } from "../meta/rarity";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { drawUnit } from "../render/draw";
import { makeEntity, World } from "../sim/world";
import { Entity, Kind, Team } from "../sim/types";

/** The question each advance asks — the three Oaths are its answers. */
export const AGE_QUESTION: Record<number, string> = {
  1: "How will your realm grow?",
  2: "What will your army be?",
  3: "What will your realm be remembered for?",
};

const KIND_LABEL = { growth: "Growth", war: "War", guard: "Guard" } as const;

export class OathPicker {
  /** The Town Centre the advance will be researched at, while open. */
  private building: Entity | null = null;
  private portrait: Entity | null = null;

  get isOpen(): boolean {
    return this.building !== null;
  }

  open(building: Entity) {
    this.building = building;
  }

  close() {
    this.building = null;
  }

  /**
   * Draw the picker if open. Calls `swear` with the research id
   * ("age:<oath>") when a card is chosen, and closes itself.
   */
  draw(W: number, H: number, world: World, team: Team, time: number, swear: (building: Entity, techId: string) => void) {
    const b = this.building;
    if (!b) return;
    // Closed underneath us: the Town Centre fell, or the advance is already
    // under way (or done) from somewhere else.
    const p = world.player(team);
    const next = p.age + 1;
    if (!b.alive || next >= AGES.length || world.entities.some((e) =>
      e.alive && e.team === team && e.kind === Kind.Building && e.productionQueue.includes("a:age"))) {
      this.close();
      return;
    }
    const age = AGES[next];
    const oaths = oathsForAge(next);
    const ctx = ui.ctx;

    ctx.fillStyle = "rgba(8, 6, 3, 0.66)";
    ctx.fillRect(0, 0, W, H);

    const cardW = Math.min(330, Math.floor((W - 80) / 3) - 16);
    const cardH = Math.min(430, H - 210);
    const gap = 18;
    const totalW = cardW * 3 + gap * 2;
    const x0 = Math.round((W - totalW) / 2);
    const y0 = Math.round(Math.max(120, (H - cardH) / 2 + 20));

    ui.text(`Advance to the ${age.name}`, W / 2, y0 - 72, {
      align: "center", size: 28, bold: true, color: "#ffe9b0", font: "Georgia, serif",
    });
    ui.text(`Swear an Oath — ${AGE_QUESTION[next] ?? ""}`, W / 2, y0 - 42, { align: "center", size: 15, color: "#d8cdb0" });

    const req = world.ageRequirementProgress(team, next);
    const reqOk = req.have >= req.need;
    const afford = world.canAfford(p.resources, age.cost);
    const costLine = `Costs ${fmtCost(age.cost)} · ${age.advanceTime}s at the Town Centre`;
    const reqLine = reqOk ? "" : `  ·  Needs ${req.need - req.have} more of: ${age.requiresAny.map((id) => BUILDINGS[id]?.name ?? id).join(", ")}`;
    ui.text(costLine + reqLine, W / 2, y0 - 18, {
      align: "center", size: 13, color: !afford || !reqOk ? PAL.uiBad : "#b8ad92",
    });

    const cmdr = COMMANDERS[p.commander];
    oaths.forEach((o, i) => {
      const cx = x0 + i * (cardW + gap);
      const favoured = cmdr?.oath === o.id ? cmdr : undefined;
      const rarity = o.unit ? p.loadout[o.unit] ?? 0 : 0;
      this.card(o, cx, y0, cardW, cardH, time, favoured?.name ?? null, rarity, afford && reqOk, () => {
        swear(b, `age:${o.id}`);
        this.close();
      }, !reqOk ? "Missing a required building" : !afford ? "Not enough resources" : "");
    });

    if (ui.button("Not yet", W / 2 - 80, y0 + cardH + 18, 160, 38, { size: 14 })) this.close();
    ui.text("Esc to close", W / 2, y0 + cardH + 70, { align: "center", size: 11.5, color: "#8f866f" });
  }

  private card(
    o: OathDef, x: number, y: number, w: number, h: number, time: number,
    favouredBy: string | null, rarity: number,
    enabled: boolean, onSwear: () => void, why: string,
  ) {
    const ctx = ui.ctx;
    ui.panel(x, y, w, h, { light: true });
    // Your commander's own Oath: a gold frame and the stronger numbers.
    if (favouredBy) {
      ctx.strokeStyle = "#ffd86a";
      ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.roundRect(x + 1.5, y + 1.5, w - 3, h - 3, 6); ctx.stroke();
      ctx.font = `bold 11px "Trebuchet MS", sans-serif`;
      const label = `★ ${favouredBy}'s Oath · +${Math.round((AFFINITY - 1) * 100)}%`;
      const lw = ctx.measureText(label).width + 16;
      ctx.fillStyle = "#ffd86a";
      ctx.beginPath(); ctx.roundRect(x + w - lw - 12, y + 16, lw, 20, 4); ctx.fill();
      ui.text(label, x + w - lw / 2 - 12, y + 26.5, { align: "center", size: 11, bold: true, color: "#2a1f08" });
    }
    // The Oath's colour across the top, so the three read apart at a glance.
    ctx.fillStyle = o.color;
    ctx.fillRect(x + 3, y + 3, w - 6, 6);
    ui.text(KIND_LABEL[o.kind].toUpperCase(), x + 18, y + 26, { size: 11.5, bold: true, color: shade(o.color, 0.2) });
    ui.text(o.name, x + 18, y + 50, { size: 19, bold: true, color: "#ffe9b0", font: "Georgia, serif" });
    ui.text(`“${o.motto}”`, x + 18, y + 74, { size: 13, color: "#c9bea3", font: "Georgia, serif" });

    // What it does, a line each, wrapped to the card.
    let ly = y + 106;
    ctx.font = `13.5px "Trebuchet MS", sans-serif`;
    for (const line of o.lines(favouredBy ? AFFINITY : 1)) {
      const rows = wrap(ctx, line, w - 52);
      ctx.fillStyle = o.color;
      ctx.beginPath(); ctx.arc(x + 24, ly, 3.2, 0, Math.PI * 2); ctx.fill();
      for (const r of rows) {
        ui.text(r, x + 36, ly, { size: 13.5, color: "#efe6cf" });
        ly += 19;
      }
      ly += 7;
    }

    // The signature unit, drawn on the card — you should know what you are
    // swearing for before you meet it on the field.
    const unitTop = y + h - 150;
    if (o.unit && UNITS[o.unit] && unitTop > ly) {
      const def = UNITS[o.unit];
      ctx.fillStyle = withAlpha(o.color, 0.1);
      ctx.beginPath(); ctx.roundRect(x + 14, unitTop, w - 28, 88, 8); ctx.fill();
      const e = this.portrait ?? (this.portrait = makeEntity());
      Object.assign(e, {
        kind: Kind.Unit, type: def.id, team: Team.Player, radius: def.radius, facing: -0.35,
        hp: def.hp, maxHp: def.hp, animPhase: time, vx: 0, vy: 0, selected: false, veterancy: 0,
        variantRarity: 0, abilityActive: 0, rallyTimer: 0, slowTimer: 0, hitFlash: 0, chargeRun: 0,
        attackCooldown: 0, attackInterval: def.attackInterval,
      });
      const scale = def.radius > 12 ? 2.1 : 2.8;
      ctx.save();
      // Kept to its own box: a lance or a halberd would otherwise reach
      // across the unit's name.
      ctx.beginPath(); ctx.rect(x + 14, unitTop, 96, 88); ctx.clip();
      ctx.translate(x + 62, unitTop + 52);
      ctx.scale(scale, scale);
      e.x = 0; e.y = 0;
      try { drawUnit(ctx, e, time, 0); } catch { /* a portrait must never break the picker */ }
      ctx.restore();
      ui.text(def.name, x + 118, unitTop + 28, { size: 14.5, bold: true, color: "#ffe9b0" });
      const at = BUILDINGS[def.trainedAt]?.name ?? def.trainedAt;
      ui.text(`Trained at the ${at}`, x + 118, unitTop + 48, { size: 12, color: "#b8ad92" });
      // The variant you own, from your chests — the collection feeds the choice.
      const rt = rarityByIndex(rarity);
      ui.text(rarity > 0 ? `Yours: ${rt.name}` : fmtCost(def.cost), x + 118, unitTop + 66, {
        size: 12, bold: rarity > 0, color: rarity > 0 ? rt.color : "#b8ad92",
      });
    } else if (o.signature && unitTop > ly) {
      // No unit: the signature rule gets the same box, with an emblem.
      ctx.fillStyle = withAlpha(o.color, 0.1);
      ctx.beginPath(); ctx.roundRect(x + 14, unitTop, w - 28, 88, 8); ctx.fill();
      emblem(ctx, o.id, x + 62, unitTop + 44, o.color);
      ui.text("SIGNATURE", x + 118, unitTop + 22, { size: 10.5, bold: true, color: shade(o.color, 0.15) });
      ui.text(o.signature.title, x + 118, unitTop + 42, { size: 14.5, bold: true, color: "#ffe9b0" });
      const rows = wrap(ctx, o.signature.text, w - 150);
      rows.slice(0, 2).forEach((r, k) => ui.text(r, x + 118, unitTop + 62 + k * 16, { size: 12, color: "#b8ad92" }));
    }

    const clicked = ui.button(enabled ? "Swear this Oath" : why || "Unavailable", x + 16, y + h - 54, w - 32, 40, {
      accent: enabled, disabled: !enabled, size: 15,
    });
    if (clicked && enabled) onSwear();
  }
}

/** A small drawn emblem for an Oath whose signature is a rule, not a unit. */
function emblem(ctx: CanvasRenderingContext2D, id: string, cx: number, cy: number, color: string) {
  ctx.save();
  ctx.translate(cx, cy);
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  if (id === "plough") {
    // A sheaf: three stalks bound, grain at the tips.
    ctx.strokeStyle = "#c9a24a";
    ctx.lineWidth = 3;
    for (const a of [-0.35, 0, 0.35]) {
      ctx.beginPath(); ctx.moveTo(0, 26); ctx.lineTo(Math.sin(a) * 30, -20); ctx.stroke();
      ctx.fillStyle = color;
      for (let k = 0; k < 4; k++) {
        ctx.beginPath();
        ctx.ellipse(Math.sin(a) * (30 - k * 6), -20 + k * 7, 3.2, 5, a, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    ctx.strokeStyle = "#7a4f22"; ctx.lineWidth = 4;
    ctx.beginPath(); ctx.moveTo(-9, 10); ctx.lineTo(9, 10); ctx.stroke();
  } else if (id === "hearth") {
    // A keep with a mending glow.
    ctx.fillStyle = withAlpha(color, 0.25);
    ctx.beginPath(); ctx.arc(0, 2, 30, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#8a8f98";
    ctx.fillRect(-18, -12, 36, 34);
    for (let k = 0; k < 4; k++) ctx.fillRect(-18 + k * 10, -20, 6, 9);
    ctx.fillStyle = "#3a2a1c";
    ctx.beginPath(); ctx.roundRect(-6, 6, 12, 16, [6, 6, 0, 0]); ctx.fill();
    ctx.strokeStyle = "#e8f0ff"; ctx.lineWidth = 3;
    ctx.beginPath(); ctx.moveTo(18, -26); ctx.lineTo(18, -14); ctx.moveTo(12, -20); ctx.lineTo(24, -20); ctx.stroke();
  } else {
    // A stack of gold coins.
    for (let k = 3; k >= 0; k--) {
      ctx.fillStyle = k % 2 ? "#c9a232" : color;
      ctx.beginPath(); ctx.ellipse(-4 + (k % 2) * 3, 16 - k * 9, 22, 8, 0, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = "#7a5a14"; ctx.lineWidth = 1.2; ctx.stroke();
    }
    ctx.fillStyle = "#7a5a14";
    ctx.font = "bold 12px Georgia, serif";
    ctx.textAlign = "center"; ctx.textBaseline = "middle";
    ctx.fillText("✦", -1, -11);
  }
  ctx.restore();
}

function fmtCost(c: { food: number; wood: number; gold: number }): string {
  const parts: string[] = [];
  if (c.food) parts.push(`${c.food} food`);
  if (c.wood) parts.push(`${c.wood} wood`);
  if (c.gold) parts.push(`${c.gold} gold`);
  return parts.join(", ") || "free";
}

function wrap(ctx: CanvasRenderingContext2D, text: string, maxW: number): string[] {
  const words = text.split(" ");
  const rows: string[] = [];
  let cur = "";
  for (const w of words) {
    const t = cur ? `${cur} ${w}` : w;
    if (ctx.measureText(t).width > maxW && cur) { rows.push(cur); cur = w; } else cur = t;
  }
  if (cur) rows.push(cur);
  return rows;
}
