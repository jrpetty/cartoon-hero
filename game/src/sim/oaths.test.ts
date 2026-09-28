import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Entity, Kind, Team } from "./types";
import { generateMap } from "../maps/generator";
import { AGES, MAX_AGE } from "../content/tech";
import { OATHS, oathsForAge } from "../content/oaths";
import { UNITS, CHARGE_RUN } from "../content/units";
import { BUILDINGS } from "../content/buildings";
import { FARM_FOOD, SIM_HZ } from "../content/balance";
import { Terrain } from "../maps/terrain_kinds";
import { applyCommand, worldChecksum } from "./commands";

/**
 * Four ages, and an Oath sworn at every advance past the Hearth — this game's
 * answer to civilisations. These pin down the rules; the picker and the AI
 * are tested where they live.
 */

const P = Team.Player;

function makeWorld(seed = 1234): World {
  const w = new World(seed);
  w.init(generateMap("open_plains", seed), [{}, {}], [1, 1]);
  w.player(P).resources = { food: 99999, wood: 99999, gold: 99999 };
  return w;
}

function build(w: World, type: string, team: Team = P): Entity {
  const start = w.map.starts[team];
  for (let r = 140; r <= 620; r += 36) {
    for (let a = 0; a < 16; a++) {
      const ang = (a / 16) * Math.PI * 2;
      const b = w.placeBuilding(team, type, start.x + Math.cos(ang) * r, start.y + Math.sin(ang) * r);
      if (b) { b.buildState = 0; b.buildProgress = 1; b.hp = b.maxHp; return b; }
    }
  }
  throw new Error("no spot for " + type);
}

const tcOf = (w: World, team: Team = P) =>
  w.entitiesOf(team, Kind.Building).find((e) => e.type === "town_center")!;

function run(w: World, seconds: number) {
  for (let i = 0; i < seconds * SIM_HZ; i++) w.tick();
}

/** Advance `team` one age, swearing `oath`, and let the research finish. */
function advance(w: World, oath: string | null, team: Team = P) {
  const p = w.player(team);
  const next = p.age + 1;
  for (const t of AGES[next].requiresAny.slice(0, AGES[next].requiresCount)) if (!w.hasBuilding(team, t)) build(w, t, team);
  p.resources = { food: 99999, wood: 99999, gold: 99999 };
  expect(w.research(team, tcOf(w, team).id, oath ? `age:${oath}` : "age"), `could not start ${AGES[next].name}`).toBe(true);
  run(w, AGES[next].advanceTime + 1);
  expect(p.age).toBe(next);
}

describe("Four ages", () => {
  it("runs Hearth, Banner, Crown, Empire", () => {
    expect(AGES.map((a) => a.name)).toEqual(["Hearth Age", "Banner Age", "Crown Age", "Empire Age"]);
    expect(MAX_AGE).toBe(3);
  });

  it("puts gunpowder and the great engines in the Empire Age", () => {
    for (const id of ["handcannon", "trebuchet"]) expect(UNITS[id].age, id).toBe(3);
  });

  it("can actually be reached — a Castle or a Siege Workshop qualifies", () => {
    const w = makeWorld();
    advance(w, "plough");
    advance(w, "shield");
    expect(w.ageRequirementMet(P, 3)).toBe(false);
    build(w, "siege_workshop");
    expect(w.ageRequirementMet(P, 3)).toBe(true);
    advance(w, "engine");
    expect(w.player(P).age).toBe(3);
  });
});

describe("Swearing an Oath", () => {
  it("offers three Oaths at each advance, and only those", () => {
    for (const age of [1, 2, 3]) expect(oathsForAge(age)).toHaveLength(3);
    expect(oathsForAge(0)).toHaveLength(0);
    const w = makeWorld();
    build(w, "barracks"); build(w, "mill");
    // An Oath of a later age can't be sworn now.
    expect(w.research(P, tcOf(w).id, "age:lance")).toBe(false);
    expect(w.research(P, tcOf(w).id, "age:nonsense")).toBe(false);
  });

  it("swears the Oath when the advance completes, and tells everyone", () => {
    const w = makeWorld();
    build(w, "barracks"); build(w, "mill");
    w.research(P, tcOf(w).id, "age:sword");
    expect(w.player(P).oaths, "sworn before the advance finished").toEqual([]);
    const seen: string[] = [];
    for (let i = 0; i < (AGES[1].advanceTime + 1) * SIM_HZ; i++) {
      w.tick();
      for (const ev of w.drainEvents()) if (ev.kind === "oath") seen.push(`${ev.team}:${ev.data}`);
    }
    expect(w.player(P).oaths).toEqual(["sword"]);
    expect(seen).toEqual([`${P}:sword`]);
  });

  it("replays identically from the command log", () => {
    const play = () => {
      const w = makeWorld(77);
      build(w, "barracks"); build(w, "mill");
      applyCommand(w, { t: "research", team: P, buildingId: tcOf(w).id, tech: "age:hearth" });
      run(w, 40);
      return w;
    };
    const a = play(), b = play();
    expect(a.player(P).oaths).toEqual(["hearth"]);
    expect(worldChecksum(a)).toBe(worldChecksum(b));
  });
});

describe("Signature units belong to their sworn", () => {
  const pairs = Object.values(OATHS).filter((o) => o.unit).map((o) => [o.id, o.unit!] as const);

  it("every signature unit exists, names its Oath, and is on a building's roster", () => {
    for (const [oath, unit] of pairs) {
      expect(UNITS[unit]?.oath, unit).toBe(oath);
      expect(BUILDINGS[UNITS[unit].trainedAt].trains, unit).toContain(unit);
      expect(UNITS[unit].age, unit).toBe(OATHS[oath].age);
    }
  });

  it("can't be trained without the Oath, and can with it", () => {
    const w = makeWorld();
    advance(w, "plough"); // not the Sword
    const barracks = build(w, "barracks");
    expect(w.trainUnit(P, barracks.id, "sworn_blade")).toBe(false);
    const w2 = makeWorld();
    advance(w2, "sword");
    const b2 = build(w2, "barracks");
    expect(w2.trainUnit(P, b2.id, "sworn_blade")).toBe(true);
  });
});

describe("What each Oath does", () => {
  it("Plough: fuller fields that replant for free", () => {
    const w = makeWorld();
    advance(w, "plough");
    const farm = build(w, "farm");
    expect(farm.amount).toBe(Math.round(FARM_FOOD * 1.3));
    expect(w.player(P).oath.villagerTrainMult).toBeCloseTo(0.9);
    expect(w.player(P).oath.freeReseed).toBe(true);
  });

  it("Hearth: a tougher town, and buildings that mend", () => {
    const w = makeWorld();
    const house = build(w, "house");
    const before = house.maxHp;
    advance(w, "hearth");
    expect(house.maxHp, "existing buildings didn't toughen").toBe(Math.round(BUILDINGS.house.hp * 1.3));
    expect(house.maxHp).toBeGreaterThan(before);
    house.hp = house.maxHp / 2;
    house.lastDamageTime = w.time;
    run(w, 5);
    expect(house.hp, "mended while still under attack").toBe(house.maxHp / 2);
    run(w, 8);
    expect(house.hp).toBeGreaterThan(house.maxHp / 2);
  });

  it("is stronger when it is your commander's favoured Oath", () => {
    const hp = (commander: string) => {
      const w = makeWorld();
      w.player(P).commander = commander;
      const house = build(w, "house");
      advance(w, "hearth");
      return house.maxHp / BUILDINGS.house.hp;
    };
    expect(hp("steward")).toBeCloseTo(1.3, 2); // favours the Plough
    expect(hp("architect")).toBeCloseTo(1.45, 2); // favours the Hearth: +50% of the effect
  });

  it("Shield: infantry already in the field get the armour", () => {
    const w = makeWorld();
    advance(w, "plough");
    const man = w.spawnUnit(P, "militia", 600, 600);
    const armour = man.armor;
    advance(w, "shield");
    expect(man.armor).toBe(armour + 2);
  });

  it("Lance: the blow at the end of a run hits three times as hard", () => {
    const hit = (runUp: boolean) => {
      const w = makeWorld();
      advance(w, "plough"); advance(w, "lance");
      const lancer = w.spawnUnit(P, "lancer", 800, 800);
      const dummy = w.spawnUnit(Team.Enemy, "twohand", 800 + (runUp ? CHARGE_RUN + 120 : 20), 800);
      // A target that stands still, so the run-up is the Lancer's own.
      dummy.maxHp = dummy.hp = 100000;
      dummy.speed = 0;
      w.issueAttack([lancer.id], dummy.id);
      const before = dummy.hp;
      for (let i = 0; i < 20 * 12 && dummy.hp === before; i++) w.tick();
      return before - dummy.hp;
    };
    const charged = hit(true), standing = hit(false);
    expect(standing).toBeGreaterThan(0);
    expect(charged / standing).toBeGreaterThan(2.5);
  });

  it("Bow: a Ranger keeps its cover in the woods while it shoots, an Archer doesn't", () => {
    // Read the concealment mask itself: fog would hide a distant shooter
    // either way, and that isn't what the Oath is about.
    const hiddenWhileShooting = (type: string) => {
      const w = makeWorld();
      const cx = 30, cy = 30;
      for (let y = cy - 3; y <= cy + 3; y++) for (let x = cx - 3; x <= cx + 3; x++) w.terrain[y * w.terrainCols + x] = Terrain.Forest;
      const s = w.spawnUnit(P, type, (cx + 0.5) * 32, (cy + 0.5) * 32);
      w.spawnUnit(Team.Enemy, "militia", (cx + 8) * 32, (cy + 0.5) * 32); // outside CONCEAL_RANGE
      for (let i = 0; i < 5; i++) { s.attackCooldown = 1; w.tick(); } // shooting throughout
      return (s.spottedBy & (1 << Team.Enemy)) === 0;
    };
    expect(hiddenWhileShooting("ranger")).toBe(true);
    expect(hiddenWhileShooting("archer")).toBe(false);
  });

  it("Coin: each Market pays a tithe, up to three", () => {
    const w = makeWorld();
    advance(w, "plough"); advance(w, "shield");
    build(w, "siege_workshop");
    advance(w, "coin");
    for (let i = 0; i < 5; i++) build(w, "market");
    const p = w.player(P);
    const g0 = p.resources.gold;
    run(w, 10);
    // 3 Markets × 0.5 gold/s × 10s — the other two don't count.
    expect(p.resources.gold - g0).toBeCloseTo(15, 0);
  });

  it("Engine: siege is tougher; Crown: every unit is", () => {
    const w = makeWorld();
    advance(w, "plough"); advance(w, "shield");
    build(w, "siege_workshop");
    advance(w, "engine");
    expect(w.spawnUnit(P, "ram", 700, 700).maxHp).toBe(Math.round(UNITS.ram.hp * 1.3));
    const w2 = makeWorld();
    advance(w2, "plough"); advance(w2, "shield");
    build(w2, "castle");
    advance(w2, "crown");
    expect(w2.spawnUnit(P, "knight", 700, 700).maxHp).toBe(Math.round(UNITS.knight.hp * 1.12));
    expect(w2.player(P).oath.vetMult).toBeCloseTo(1.6);
  });
});
