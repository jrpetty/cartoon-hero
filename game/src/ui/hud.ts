// In-match HUD: top resource bar, minimap, selection panel, command card,
// alerts. Talks back to the match controller through the MatchController
// interface — the HUD never mutates the world directly.

import { World, FOG_UNSEEN } from "../sim/world";
import { BuildState, Entity, Kind, Team } from "../sim/types";
import { Camera } from "../engine/camera";
import { UNITS } from "../content/units";
import { ABILITIES } from "../content/abilities";
import { BUILDINGS } from "../content/buildings";
import { AGES, MAX_AGE, UPGRADES } from "../content/tech";
import { PAL, teamColor, withAlpha } from "../render/palette";
import { dayLabel, dayPhase, isNight } from "../content/daynight";
import { rarityByIndex } from "../meta/rarity";
import { ui } from "./ui";
import { buildMinimapBase } from "../render/terrain";
import type { MapData } from "../maps/generator";

export interface Alert {
  text: string;
  time: number; // seconds remaining
  x?: number;
  y?: number; // world position for minimap ping
}

export interface MatchController {
  trainUnit(building: Entity, type: string): void;
  research(building: Entity, techId: string): void;
  startPlacement(type: string): void;
  ungarrison(building: Entity): void;
  toggleGate(building: Entity): void;
  trade(action: "sell_wood" | "sell_food" | "buy_wood" | "buy_food"): void;
  stopSelection(): void;
  holdSelection(): void;
  setAttackMoveMode(): void;
  garrisonSelection(): void;
  useAbility(): void;
  minimapNavigate(wx: number, wy: number): void;
  minimapCommand(wx: number, wy: number): void;
  minimapPing(wx: number, wy: number): void;
  openMenu(): void;
}

interface BuildCategory {
  id: string;
  label: string;
  hint: string;
  buildings: string[];
}

const BUILD_CATEGORIES: BuildCategory[] = [
  {
    id: "economy",
    label: "Economy",
    hint: "Houses, drop-off camps, farms, market and expansion Town Centers.",
    buildings: ["house", "mill", "lumber_camp", "mining_camp", "farm", "market", "town_center"],
  },
  {
    id: "military",
    label: "Military",
    hint: "Production and upgrade buildings for your army.",
    buildings: ["barracks", "archery_range", "stable", "siege_workshop", "blacksmith"],
  },
  {
    id: "defense",
    label: "Defense",
    hint: "Walls, gates, towers, watchfires and the mighty Castle.",
    buildings: ["palisade", "stone_wall", "gate", "watchfire", "watch_tower", "castle"],
  },
];

export const MINIMAP_SIZE = 196;
const CARD_W = 4;
const CARD_H = 3;
const BTN = 56;
const GAP = 6;
const PING_LIFE = 2.4; // seconds a minimap ping lingers

export class HUD {
  private minimapBase: HTMLCanvasElement | null = null;
  alerts: Alert[] = [];
  pings: { x: number; y: number; age: number }[] = [];
  buildMenuOpen = false;
  buildCategory: string | null = null;

  prepare(map: MapData) {
    this.minimapBase = buildMinimapBase(map, MINIMAP_SIZE);
    this.alerts = [];
    this.pings = [];
    this.buildMenuOpen = false;
    this.buildCategory = null;
  }

  addAlert(text: string, x?: number, y?: number) {
    this.alerts.unshift({ text, time: 5, x, y });
    if (this.alerts.length > 4) this.alerts.pop();
  }

  addPing(x: number, y: number) {
    this.pings.push({ x, y, age: 0 });
    if (this.pings.length > 8) this.pings.shift();
  }

  draw(
    W: number,
    H: number,
    world: World,
    cam: Camera,
    team: Team,
    selection: Entity[],
    dt: number,
    ctrl: MatchController,
    attackMoveArmed: boolean,
    spectating = false,
  ) {
    const ctx = ui.ctx;
    const p = world.player(team);

    // ---------------------------------------------------------- top bar --
    ui.panel(0, 0, W, 34);
    if (!spectating) {
      const res = p.resources;
      const items: [string, string, number][] = [
        ["🍖", "#e89a5a", Math.floor(res.food)],
        ["🪵", "#b08a52", Math.floor(res.wood)],
        ["🪙", PAL.goldVein, Math.floor(res.gold)],
      ];
      let x = 14;
      for (const [icon, color, val] of items) {
        ui.text(icon, x, 17, { size: 15 });
        ui.text(String(val), x + 22, 17, { size: 14, color, bold: true });
        x += 92;
      }
      const popBlocked = p.popUsed >= p.popCap;
      ui.text(`Pop ${p.popUsed}/${p.popCap}`, x, 17, {
        size: 14,
        color: popBlocked ? PAL.uiBad : PAL.uiParchment,
        bold: popBlocked,
      });
      x += 110;
      ui.text(AGES[p.age].name, x, 17, { size: 14, color: PAL.uiAccent, bold: true });
    }

    const mins = Math.floor(world.time / 60);
    const secs = Math.floor(world.time % 60);
    ui.text(`${mins}:${secs.toString().padStart(2, "0")}`, W / 2, 17, { align: "center", size: 14 });

    // Day/night dial + label.
    const phase = dayPhase(world.time);
    const night = isNight(phase);
    const dialX = W / 2 + 52;
    if (night) {
      ctx.fillStyle = "#cdd6ea";
      ctx.beginPath();
      ctx.arc(dialX, 17, 7, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = PAL.uiPanel;
      ctx.beginPath();
      ctx.arc(dialX + 3, 15, 6, 0, Math.PI * 2);
      ctx.fill();
    } else {
      const warm = phase < 0.08 || phase >= 0.4;
      ctx.fillStyle = warm ? "#ffcf6a" : "#ffe9b0";
      ctx.beginPath();
      ctx.arc(dialX, 17, 6, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = withAlpha("#ffd98a", 0.7);
      ctx.lineWidth = 1.5;
      for (let i = 0; i < 8; i++) {
        const a = (i / 8) * Math.PI * 2;
        ctx.beginPath();
        ctx.moveTo(dialX + Math.cos(a) * 8, 17 + Math.sin(a) * 8);
        ctx.lineTo(dialX + Math.cos(a) * 10.5, 17 + Math.sin(a) * 10.5);
        ctx.stroke();
      }
    }
    ui.text(dayLabel(phase), dialX + 16, 17, {
      size: 12,
      color: night ? "#9fb0d6" : "#e8c98a",
      bold: night,
    });

    if (ui.button("Menu", W - 74, 5, 64, 24, { size: 13 })) ctrl.openMenu();

    if (attackMoveArmed) {
      ui.text("⚔ ATTACK-MOVE: click a target location", W / 2, 52, {
        align: "center", size: 15, color: "#f2a05d", bold: true,
      });
    }

    // ------------------------------------------------------------ alerts --
    let ay = 76;
    for (const a of this.alerts) {
      a.time -= dt;
      if (a.time <= 0) continue;
      const alpha = Math.min(1, a.time);
      ctx.globalAlpha = alpha;
      ui.text(a.text, W / 2, ay, { align: "center", size: 15, color: "#f2c45d", bold: true });
      ctx.globalAlpha = 1;
      ay += 22;
    }
    this.alerts = this.alerts.filter((a) => a.time > 0);
    for (const ping of this.pings) ping.age += dt;
    this.pings = this.pings.filter((ping) => ping.age < PING_LIFE);

    // ----------------------------------------------------------- minimap --
    const mmX = 10;
    const mmY = H - MINIMAP_SIZE - 10;
    ui.panel(mmX - 4, mmY - 4, MINIMAP_SIZE + 8, MINIMAP_SIZE + 8);
    if (this.minimapBase) ctx.drawImage(this.minimapBase, mmX, mmY);

    const sx = MINIMAP_SIZE / world.worldW;
    const sy = MINIMAP_SIZE / world.worldH;

    // Fog shading on minimap.
    const fog = world.fog[team];
    const cellW = MINIMAP_SIZE / world.fogCols;
    const cellH = MINIMAP_SIZE / world.fogRows;
    ctx.fillStyle = "rgba(8,7,4,0.85)";
    for (let cy = 0; cy < world.fogRows; cy += 2) {
      for (let cx = 0; cx < world.fogCols; cx += 2) {
        if (fog[cy * world.fogCols + cx] === FOG_UNSEEN) {
          ctx.fillRect(mmX + cx * cellW, mmY + cy * cellH, cellW * 2, cellH * 2);
        }
      }
    }

    // In team games, colour the map by relation to you, not raw team.
    const diplo = world.hasTeamAlliances();
    const diploMain = (t: Team): string => {
      const rel = world.relationTo(team, t);
      if (rel === "self") return PAL.diplomacy.self.main;
      if (rel === "ally") return PAL.diplomacy.ally.main;
      return PAL.diplomacy.enemies[world.enemyIndexOf(team, t) % PAL.diplomacy.enemies.length].main;
    };

    // Entities as dots.
    for (const e of world.entities) {
      if (!e.alive) continue;
      if (e.kind === Kind.Projectile) continue;
      if (!world.visibleTo(team, e)) continue;
      let color: string;
      if (e.kind === Kind.Resource) {
        color = e.type === "tree" ? "#2f5b28" : e.type === "gold_mine" ? PAL.goldVein : "#b13a4a";
      } else {
        color = diplo ? diploMain(e.team) : teamColor(e.team).main;
      }
      const s = e.kind === Kind.Building ? 3 : 1.6;
      ctx.fillStyle = color;
      ctx.fillRect(mmX + e.x * sx - s / 2, mmY + e.y * sy - s / 2, s, s);
    }

    // Alert pings.
    for (const a of this.alerts) {
      if (a.x === undefined || a.y === undefined) continue;
      const pulse = (a.time * 3) % 1;
      ctx.strokeStyle = withAlpha("#f25d4a", 1 - pulse);
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(mmX + a.x * sx, mmY + a.y! * sy, 4 + pulse * 8, 0, Math.PI * 2);
      ctx.stroke();
    }

    // Viewport rectangle.
    const vw = (cam.viewW / cam.zoom) * sx;
    const vh = (cam.viewH / cam.zoom) * sy;
    ctx.strokeStyle = "rgba(243, 233, 210, 0.9)";
    ctx.lineWidth = 1;
    ctx.strokeRect(mmX + cam.x * sx - vw / 2, mmY + cam.y * sy - vh / 2, vw, vh);

    // Diplomacy legend (team games only): You / Ally / Enemy swatches.
    if (diplo) {
      const legend: [string, string][] = [
        ["You", PAL.diplomacy.self.main],
        ["Ally", PAL.diplomacy.ally.main],
        ["Enemy", PAL.diplomacy.enemies[0].main],
      ];
      const lw = 58;
      const lh = 12 + legend.length * 14;
      const lx = mmX + 4;
      const ly = mmY + 4;
      ctx.fillStyle = "rgba(12,9,5,0.72)";
      ctx.fillRect(lx, ly, lw, lh);
      ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.3);
      ctx.lineWidth = 1;
      ctx.strokeRect(lx, ly, lw, lh);
      for (let i = 0; i < legend.length; i++) {
        const [label, col] = legend[i];
        const ry = ly + 8 + i * 14;
        ctx.fillStyle = col;
        ctx.fillRect(lx + 7, ry + 2, 9, 9);
        ui.text(label, lx + 22, ry + 7, { size: 11, color: "#e7ddc4" });
      }
    }

    // Player pings: expanding cyan rings, both on the minimap and (briefly) in
    // the world. Friendly signals — "look here", "rally here".
    for (const ping of this.pings) {
      const t = ping.age / PING_LIFE;
      const pulse = (ping.age * 2.2) % 1;
      ctx.strokeStyle = withAlpha("#79e0ff", (1 - t) * (1 - pulse));
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(mmX + ping.x * sx, mmY + ping.y * sy, 3 + pulse * 9, 0, Math.PI * 2);
      ctx.stroke();
    }

    // Minimap interaction.
    if (ui.hit(mmX, mmY, MINIMAP_SIZE, MINIMAP_SIZE)) {
      ui.pointerConsumed = true;
      const wx = (ui.mx - mmX) / sx;
      const wy = (ui.my - mmY) / sy;
      if (ui.clicked) {
        if (ui.alt) ctrl.minimapPing(wx, wy);
        else ctrl.minimapNavigate(wx, wy);
      }
      if (ui.rightClicked) ctrl.minimapCommand(wx, wy);
    }

    // ----------------------------------------------------- command card --
    const cardW = CARD_W * (BTN + GAP) + GAP + 8;
    const cardH = CARD_H * (BTN + GAP) + GAP + 8;
    const cardX = W - cardW - 10;
    const cardY = H - cardH - 10;
    if (!spectating) {
      ui.panel(cardX, cardY, cardW, cardH);
      this.drawCommandCard(cardX + 8, cardY + 8, world, team, selection, ctrl);
    }

    // -------------------------------------------------- selection panel --
    const selX = mmX + MINIMAP_SIZE + 18;
    const selW = cardX - selX - 12;
    if (!spectating && selection.length > 0 && selW > 200) {
      const selH = 104;
      const selY = H - selH - 10;
      ui.panel(selX, selY, selW, selH);
      this.drawSelectionPanel(selX + 10, selY + 8, selW - 20, world, selection, team);
    }
  }

  private cost(c: { food: number; wood: number; gold: number }): string {
    const parts: string[] = [];
    if (c.food) parts.push(`${c.food}f`);
    if (c.wood) parts.push(`${c.wood}w`);
    if (c.gold) parts.push(`${c.gold}g`);
    return parts.join(" ") || "free";
  }

  private drawCommandCard(
    x0: number,
    y0: number,
    world: World,
    team: Team,
    selection: Entity[],
    ctrl: MatchController,
  ) {
    const p = world.player(team);
    const own = selection.filter((e) => e.team === team);
    const btnAt = (i: number): [number, number] => [
      x0 + (i % CARD_W) * (BTN + GAP),
      y0 + Math.floor(i / CARD_W) * (BTN + GAP),
    ];
    let slot = 0;
    const place = (
      label: string,
      onClick: () => void,
      opts: Parameters<UIType["button"]>[5] = {},
    ) => {
      const [bx, by] = btnAt(slot++);
      if (ui.button(label, bx, by, BTN, BTN, { ...opts, size: opts.size ?? 11 })) onClick();
    };
    type UIType = typeof ui;

    const villagers = own.filter((e) => e.kind === Kind.Unit && UNITS[e.type]?.canBuild);
    const combatUnits = own.filter((e) => e.kind === Kind.Unit);
    const building = own.find((e) => e.kind === Kind.Building);

    if (villagers.length > 0 && (this.buildMenuOpen || own.every((e) => e.type === "villager"))) {
      if (this.buildMenuOpen) {
        if (!this.buildCategory) {
          // Pick a build category.
          for (const cat of BUILD_CATEGORIES) {
            place(cat.label, () => (this.buildCategory = cat.id), {
              accent: true,
              size: 13,
              tooltip: [cat.label, cat.hint],
            });
          }
          place("Close", () => (this.buildMenuOpen = false), { danger: true });
          return;
        }
        const cat = BUILD_CATEGORIES.find((c) => c.id === this.buildCategory)!;
        for (const id of cat.buildings) {
          const def = BUILDINGS[id];
          if (!def) continue;
          const lockedAge = p.age < def.age;
          const lockedReq = def.requires && !world.hasBuilding(team, def.requires);
          const cantAfford = !world.canAfford(p.resources, def.cost);
          place(
            def.name.split(" ")[0],
            () => {
              ctrl.startPlacement(id);
              this.buildMenuOpen = false;
              this.buildCategory = null;
            },
            {
              disabled: lockedAge || !!lockedReq || cantAfford,
              tooltip: [
                def.name,
                this.cost(def.cost),
                ...(lockedAge ? [`Requires ${AGES[def.age].name}`] : []),
                ...(lockedReq ? [`Requires ${BUILDINGS[def.requires!].name}`] : []),
                def.desc,
              ],
            },
          );
        }
        place("Back", () => (this.buildCategory = null), { danger: true });
        return;
      }
      place("Build", () => {
        this.buildMenuOpen = true;
        this.buildCategory = null;
      }, { accent: true, tooltip: ["Open the construction menu (B)"] });
    }

    if (combatUnits.length > 0) {
      place("Stop", () => ctrl.stopSelection(), { tooltip: ["Stop (S)"] });
      place("Hold", () => ctrl.holdSelection(), { tooltip: ["Hold position (H)", "Units won't chase."] });
      place("Atk Move", () => ctrl.setAttackMoveMode(), {
        accent: true,
        tooltip: ["Attack-move (A)", "March and engage anything hostile on the way."],
      });
      place("Garrison", () => ctrl.garrisonSelection(), {
        tooltip: ["Garrison (G)", "Tuck units into the nearest building for cover + extra arrows."],
      });
      // Signature ability for whichever ability-carrying type is selected.
      const abilityUnit = combatUnits.find((e) => ABILITIES[e.type]);
      if (abilityUnit) {
        const ab = ABILITIES[abilityUnit.type];
        const ready = combatUnits.filter((e) => ABILITIES[e.type]?.id === ab.id && e.abilityCooldown <= 0);
        const minCd = Math.min(
          ...combatUnits
            .filter((e) => ABILITIES[e.type]?.id === ab.id)
            .map((e) => e.abilityCooldown),
        );
        place(ab.name.split(" ")[0], () => ctrl.useAbility(), {
          accent: ready.length > 0,
          disabled: ready.length === 0,
          badge: ready.length === 0 && minCd > 0 ? String(Math.ceil(minCd)) : "",
          tooltip: [
            `${ab.name} (Q)`,
            ab.desc,
            ready.length > 0
              ? `${ready.length} ready`
              : `On cooldown — ${Math.ceil(minCd)}s`,
          ],
        });
      }
      return;
    }

    if (building && building.buildState === BuildState.Done) {
      const def = BUILDINGS[building.type];
      for (const unitId of def.trains) {
        const u = UNITS[unitId];
        const lockedAge = p.age < u.age;
        const heroState = u.hero ? world.heroStatus(team) : null;
        const heroLocked = !!heroState && !heroState.trainable;
        place(
          u.name.split(" ")[0].slice(0, 7),
          () => ctrl.trainUnit(building, unitId),
          {
            accent: !!u.hero && !heroLocked,
            disabled: lockedAge || heroLocked || !world.canAfford(p.resources, u.cost) || p.popUsed + u.pop > p.popCap,
            tooltip: [
              u.name + (u.hero ? " ★" : ""),
              this.cost(u.cost) + `  (${u.pop} pop)`,
              ...(lockedAge ? [`Requires ${AGES[u.age].name}`] : []),
              ...(heroState && heroState.label ? [heroState.label] : []),
              u.desc,
            ],
            badge: String(building.productionQueue.filter((q) => q === `u:${unitId}`).length || ""),
          },
        );
      }
      if (building.type === "town_center" && p.age < MAX_AGE) {
        const next = AGES[p.age + 1];
        const reqOk = !next.requires || world.hasBuilding(team, next.requires);
        place(
          `${next.name.split(" ")[0]} Age`,
          () => ctrl.research(building, "age"),
          {
            accent: true,
            disabled: !reqOk || !world.canAfford(p.resources, next.cost) ||
              building.productionQueue.includes("a:age"),
            tooltip: [
              `Advance to ${next.name}`,
              this.cost(next.cost),
              ...(next.requires && !reqOk ? [`Requires ${BUILDINGS[next.requires].name}`] : []),
              "Unlocks new units, buildings and +attack/+armor for your army.",
            ],
          },
        );
      }
      if (building.type === "blacksmith") {
        for (const upId of Object.keys(UPGRADES)) {
          const up = UPGRADES[upId];
          if (p.upgrades.has(upId)) continue;
          place(up.name.split(" ")[0].slice(0, 7), () => ctrl.research(building, upId), {
            disabled: p.age < up.age || !world.canAfford(p.resources, up.cost),
            tooltip: [up.name, this.cost(up.cost), up.desc],
          });
        }
      }
      if (building.type === "market") {
        place("Sell 🪵", () => ctrl.trade("sell_wood"), { tooltip: ["Sell 100 wood → 75 gold"] });
        place("Sell 🍖", () => ctrl.trade("sell_food"), { tooltip: ["Sell 100 food → 75 gold"] });
        place("Buy 🪵", () => ctrl.trade("buy_wood"), { tooltip: ["Buy 75 wood ← 100 gold"] });
        place("Buy 🍖", () => ctrl.trade("buy_food"), { tooltip: ["Buy 75 food ← 100 gold"] });
      }
      if (building.type === "gate") {
        place(building.gateOpen ? "Close Gate" : "Open Gate", () => ctrl.toggleGate(building), {
          accent: true,
          tooltip: [
            building.gateOpen ? "Bar the gate shut" : "Swing the gate open",
            "Otherwise it opens for your troops and shuts when foes draw near.",
          ],
        });
      }
      if (building.garrison.length > 0) {
        place(`Eject ${building.garrison.length}`, () => ctrl.ungarrison(building), {
          tooltip: ["Ungarrison all units (G)"],
        });
      }
    }
  }

  private drawSelectionPanel(
    x: number,
    y: number,
    w: number,
    world: World,
    selection: Entity[],
    team: Team,
  ) {
    const ctx = ui.ctx;
    if (selection.length === 1) {
      const e = selection[0];
      const def = e.kind === Kind.Unit ? UNITS[e.type] : null;
      const bdef = e.kind === Kind.Building ? BUILDINGS[e.type] : null;
      const name = def?.name ?? bdef?.name ?? e.type;
      const rarity = e.kind === Kind.Unit && e.team === team ? rarityByIndex(e.variantRarity) : null;
      ui.text(name, x, y + 10, { size: 16, bold: true, color: PAL.uiAccent });
      if (rarity && rarity.index > 0) {
        ui.text(rarity.name, x + ctx.measureText(name).width + 26, y + 10, { size: 12, color: rarity.color, bold: true });
      }
      ui.bar(x, y + 26, Math.min(w, 220), 8, e.hp / e.maxHp, e.hp / e.maxHp > 0.4 ? PAL.uiGood : PAL.uiBad);
      ui.text(`${Math.ceil(e.hp)} / ${e.maxHp}`, x + Math.min(w, 220) + 10, y + 30, { size: 12 });
      let line = "";
      if (def) {
        line = `⚔ ${e.attack}   🛡 ${e.armor}   ${def.ranged ? `🎯 ${e.range}` : "melee"}   👟 ${Math.round(e.speed)}`;
      } else if (bdef) {
        if (e.buildState !== BuildState.Done) {
          line = `Under construction — ${Math.round(e.buildProgress * 100)}%`;
        } else if (bdef.attack > 0) {
          line = `⚔ ${bdef.attack}   🎯 ${bdef.range}   Garrison ${e.garrison.length}/${bdef.garrisonCap}`;
        } else if (e.garrison.length > 0) {
          line = `Garrison ${e.garrison.length}/${bdef.garrisonCap}`;
        }
      }
      if (line) ui.text(line, x, y + 50, { size: 13 });

      // Production queue.
      if (e.kind === Kind.Building && e.productionQueue.length > 0) {
        const item = e.productionQueue[0];
        const total = world.itemTime(item);
        const label = item.startsWith("u:")
          ? UNITS[item.slice(2)].name
          : item === "a:age"
            ? "Advancing Age"
            : UPGRADES[item.slice(2)]?.name ?? item;
        ui.text(`${label}  (+${e.productionQueue.length - 1} queued)`, x, y + 70, { size: 12, color: "#bdb49a" });
        ui.bar(x, y + 82, Math.min(w, 220), 6, 1 - e.productionTime / total, PAL.uiAccent);
      }
    } else {
      // Multi-select: count chips by type.
      const counts = new Map<string, number>();
      for (const e of selection) counts.set(e.type, (counts.get(e.type) ?? 0) + 1);
      ui.text(`${selection.length} selected`, x, y + 10, { size: 15, bold: true, color: PAL.uiAccent });
      let cx = x;
      let cy = y + 34;
      for (const [type, n] of counts) {
        const name = UNITS[type]?.name ?? BUILDINGS[type]?.name ?? type;
        const label = `${name} ×${n}`;
        ctx.font = "13px 'Trebuchet MS', sans-serif";
        const cw = ctx.measureText(label).width + 18;
        if (cx + cw > x + w) {
          cx = x;
          cy += 26;
        }
        ctx.fillStyle = "rgba(255,255,255,0.07)";
        ctx.beginPath();
        ctx.roundRect(cx, cy - 11, cw, 22, 11);
        ctx.fill();
        ui.text(label, cx + 9, cy, { size: 13 });
        cx += cw + 8;
      }
    }
  }
}
