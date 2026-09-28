import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = { createElement: () => createCanvas(1, 1) };
import { World } from "./world";
import { Entity, Kind, Team } from "./types";
import { generateMap } from "../maps/generator";
import { FACTIONS, FACTION_IDS, FactionId, rosterFor } from "../content/factions";
import { UNITS } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { UPGRADES } from "../content/tech";
import { RARITY_HP_MULT } from "../content/balance";
import { CATALOG } from "../meta/catalog";
import { drawBuilding, setFactionResolver } from "../render/draw";
import { makeEntity } from "./world";
import { teamColor } from "../render/palette";

/**
 * Factions: who a realm is. Each has its own look, bonuses with a price, and
 * units of its own — and the meta systems (unboxed rarities, commanders,
 * boons) have to keep working on top of all of it.
 */

const P = Team.Player;

function realm(faction: FactionId, opts: { loadout?: Record<string, number>; commander?: string; boons?: { id: string; rarity: number; age: number }[] } = {}): World {
  const w = new World(99);
  w.init(generateMap("open_plains", 99), [opts.loadout ?? {}, {}], [1, 1], undefined,
    [opts.commander ?? "", ""], false, opts.boons ? [opts.boons, []] : undefined, "conquest", [faction, "kingdom"]);
  w.player(P).resources = { food: 99999, wood: 99999, gold: 99999 };
  return w;
}

function build(w: World, type: string): Entity {
  const start = w.map.starts[P];
  for (let r = 140; r <= 620; r += 36) {
    for (let a = 0; a < 16; a++) {
      const ang = (a / 16) * Math.PI * 2;
      const b = w.placeBuilding(P, type, start.x + Math.cos(ang) * r, start.y + Math.sin(ang) * r);
      if (b) { b.buildState = 0; b.buildProgress = 1; b.hp = b.maxHp; return b; }
    }
  }
  throw new Error("no spot for " + type);
}

describe("Every faction's units are real and trainable", () => {
  it("names units that exist, on a building that trains them", () => {
    for (const f of Object.values(FACTIONS)) {
      for (const [base, own] of Object.entries(f.replaces)) {
        expect(UNITS[base], `${f.id} replaces unknown ${base}`).toBeDefined();
        expect(UNITS[own]?.role, `${own} should inherit ${base}'s rarity`).toBe(base);
      }
      for (const u of [...Object.values(f.replaces), ...f.extra]) {
        expect(BUILDINGS[UNITS[u].trainedAt].trains, `${u} is on no roster`).toContain(u);
      }
    }
  });

  it("trains its own units instead of the ones they replace, and nobody else's", () => {
    const legion = realm("legion");
    const barracks = build(legion, "barracks");
    expect(legion.trainUnit(P, barracks.id, "militia"), "the Legion trained a Man-at-Arms").toBe(false);
    expect(legion.trainUnit(P, barracks.id, "legionary")).toBe(true);
    const kingdom = realm("kingdom");
    const kb = build(kingdom, "barracks");
    expect(kingdom.trainUnit(P, kb.id, "legionary"), "the Kingdom trained a Legionary").toBe(false);
    expect(kingdom.trainUnit(P, kb.id, "militia")).toBe(true);
    expect(rosterFor("legion", BUILDINGS.barracks.trains)).not.toContain("militia");
    expect(rosterFor("legion", BUILDINGS.barracks.trains)).toContain("legionary");
  });
});

describe("Bonuses and their price", () => {
  it("are in force from the first tick", () => {
    const k = realm("khanate");
    const tc = k.entities.find((e) => e.team === P && e.type === "town_center")!;
    expect(tc.maxHp).toBe(Math.round(BUILDINGS.town_center.hp * 0.8));
    const s = realm("shogunate");
    const vil = s.entities.find((e) => e.team === P && e.type === "villager")!;
    expect(vil.maxHp).toBe(Math.round(UNITS.villager.hp * 1.25));
  });

  it("change what things cost", () => {
    const kingdom = realm("kingdom");
    const base = UPGRADES.forging.cost.gold;
    expect(kingdom.techCostFor(P, "forging").gold).toBe(Math.round(base * 0.75));
    const norse = realm("norse");
    expect(norse.techCostFor(P, "forging").gold).toBe(Math.round(base * 1.1));
    const legion = realm("legion");
    expect(legion.unitCostFor(P, "knight").food).toBe(Math.round(UNITS.knight.cost.food * 1.15));
    const asc = realm("ascendancy");
    expect(asc.unitCostFor(P, "spearman").food).toBe(Math.round(UNITS.spearman.cost.food * 1.15));
    expect(asc.unitCostFor(P, "villager").food, "villagers aren't soldiers").toBe(UNITS.villager.cost.food);
    const khan = realm("khanate");
    expect(khan.buildingCostFor(P, "stable").wood).toBe(Math.round(BUILDINGS.stable.cost.wood * 0.75));
  });

  it("gives the Khanate's yurts room for more", () => {
    const khan = realm("khanate");
    const before = khan.player(P).popCap;
    const house = khan.placeBuilding(P, "house", khan.map.starts[P].x + 200, khan.map.starts[P].y + 40)
      ?? khan.placeBuilding(P, "house", khan.map.starts[P].x - 200, khan.map.starts[P].y - 40)!;
    const builder = khan.entities.find((e) => e.team === P && e.type === "villager")!;
    khan.issueBuildRepair([builder.id], house.id);
    for (let i = 0; i < 20 * 60 && khan.player(P).popCap === before; i++) khan.tick();
    expect(khan.player(P).popCap - before).toBe(BUILDINGS.house.popProvided + 3);
  });
});

describe("The meta systems still apply", () => {
  it("carries unboxed rarity across factions by role", () => {
    // A Very Rare Man-at-Arms makes the Legion's Legionary Very Rare too.
    const w = realm("legion", { loadout: { militia: 3 } });
    const leg = w.spawnUnit(P, "legionary", 700, 700);
    expect(leg.variantRarity).toBe(3);
    expect(leg.maxHp).toBe(Math.round(UNITS.legionary.hp * RARITY_HP_MULT[3]));
    // A rarer variant of the unit itself wins.
    const w2 = realm("legion", { loadout: { militia: 1, legionary: 4 } });
    expect(w2.spawnUnit(P, "legionary", 700, 700).variantRarity).toBe(4);
  });

  it("applies commander bonuses to faction units", () => {
    const w = realm("legion", { commander: "marshal" }); // +8% HP, +1 armour
    const leg = w.spawnUnit(P, "legionary", 700, 700);
    expect(leg.armor).toBe(UNITS.legionary.armor + 1);
    expect(leg.maxHp).toBe(Math.round(UNITS.legionary.hp * 1.08));
  });

  it("stacks boons with faction bonuses", () => {
    const w = realm("legion", { boons: [{ id: "whetstones", rarity: 0, age: 0 }] }); // +8% infantry attack
    const leg = w.spawnUnit(P, "legionary", 700, 700);
    expect(leg.attack).toBe(Math.round(UNITS.legionary.attack * 1.08));
  });

  it("puts every faction and Oath unit in the War Chests", () => {
    const units = new Set(CATALOG.map((v) => v.unitId));
    for (const f of Object.values(FACTIONS)) for (const u of [...Object.values(f.replaces), ...f.extra]) expect(units.has(u), u).toBe(true);
    for (const u of ["sworn_blade", "lancer", "ranger", "halberdier", "great_bombard", "royal_guard"]) expect(units.has(u), u).toBe(true);
  });
});

describe("Each faction looks like itself", () => {
  // One Town Centre per faction, same team colour, drawn to a small canvas.
  function render(f: FactionId) {
    const c = createCanvas(160, 160);
    const ctx = c.getContext("2d") as unknown as CanvasRenderingContext2D;
    ctx.fillStyle = "#6da944"; ctx.fillRect(0, 0, 160, 160);
    const e = makeEntity();
    Object.assign(e, { kind: Kind.Building, type: "town_center", team: 0, x: 80, y: 100, radius: 48, hp: 1, maxHp: 1, buildState: 0, buildProgress: 1 });
    setFactionResolver(() => f);
    drawBuilding(ctx, e, 0, 0 as Team);
    setFactionResolver(null);
    return ctx.getImageData(0, 0, 160, 160).data;
  }

  it("draws six different Town Centres", () => {
    const shots = FACTION_IDS.map(render);
    for (let i = 0; i < shots.length; i++) {
      for (let j = i + 1; j < shots.length; j++) {
        let diff = 0;
        for (let k = 0; k < shots[i].length; k += 4) if (Math.abs(shots[i][k] - shots[j][k]) + Math.abs(shots[i][k + 1] - shots[j][k + 1]) > 40) diff++;
        expect(diff, `${FACTION_IDS[i]} and ${FACTION_IDS[j]} look the same`).toBeGreaterThan(1500);
      }
    }
  });

  it("keeps the team colour big enough to read", () => {
    const main = teamColor(0).main;
    const [tr, tg, tb] = [1, 3, 5].map((i) => parseInt(main.slice(i, i + 2), 16));
    for (const f of FACTION_IDS) {
      const d = render(f);
      let team = 0;
      for (let k = 0; k < d.length; k += 4) if (Math.abs(d[k] - tr) + Math.abs(d[k + 1] - tg) + Math.abs(d[k + 2] - tb) < 70) team++;
      expect(team, `${f}'s Town Centre barely shows whose it is`).toBeGreaterThan(250);
    }
  });
});
