// The deterministic simulation world. Fixed-tick (SIM_HZ). All gameplay rules
// live here: movement, combat, economy, production, construction, vision.
// Both the human player and the AI act through the same public command API,
// so neither side can do anything the other can't.

import {
  ArmorClass,
  BuildState,
  Entity,
  EntityId,
  Kind,
  Order,
  OrderKind,
  ResourceBag,
  ResourceKind,
  Team,
} from "./types";
import { UNITS, UnitDef } from "../content/units";
import { BUILDINGS, BuildingDef } from "../content/buildings";
import { AGES, MAX_AGE, UPGRADES } from "../content/tech";
import { dayPhase, visionMult } from "../content/daynight";
import { ABILITIES, SLOW_MULT, RALLY_ATK_MULT } from "../content/abilities";
import {
  AGE_ARMOR_BONUS,
  AGE_ATTACK_BONUS,
  GATHER_RATE,
  GATHER_TICK,
  FARM_TICK_MULT,
  POP_CAP_HARD,
  PROJECTILE_SPEED,
  RARITY_ABILITY_CD_MULT,
  RARITY_ABILITY_POWER_MULT,
  RARITY_ARMOR_BONUS,
  RARITY_ATK_MULT,
  RARITY_HP_MULT,
  RARITY_SPEED_MULT,
  REPAIR_RATE,
  SIM_DT,
  TILE,
  VILLAGER_CARRY_CAP,
} from "../content/balance";
import { NavGrid } from "../pathfinding/grid";
import { findPath } from "../pathfinding/astar";
import { FlowField } from "../pathfinding/flowfield";
import { applySeparation, resolveOverlaps } from "../pathfinding/steering";
import { SpatialGrid } from "../engine/spatialgrid";
import { RNG } from "../engine/rng";
import { dist, dist2 } from "../engine/math";
import type { MapData } from "../maps/generator";
import { COMMANDERS, CommanderPower } from "../content/commanders";
import { BoonEffect, emptyBoonEffect, aggregateBoons } from "../content/boons";

export interface PlayerState {
  team: Team;
  resources: ResourceBag;
  age: number;
  upgrades: Set<string>;
  popUsed: number;
  popCap: number;
  /** unitId -> rarity index, resolved from the meta loadout (AI uses all 0). */
  loadout: Record<string, number>;
  /** Economy handicap/bonus (AI difficulty; player = 1). */
  econMult: number;
  defeated: boolean;
  /** Hero (Champion) lifecycle: none = never trained, alive, or respawning. */
  heroState: "none" | "alive" | "respawning";
  heroRespawnTimer: number; // seconds until the Champion rises again
  heroLevel: number; // saved level, carried across a respawn
  // Commander (leader) — passive bonuses + an optional banner power.
  commander: string;
  buildMult: number; // construction speed (×)
  gatherMultC: number; // commander gather speed (×)
  unitHpMult: number; // combat unit max HP (×)
  unitArmorBonus: number; // combat unit armor (flat)
  vetMult: number; // veterancy gained faster (×)
  powerCooldown: number; // seconds until the commander power is ready
  /** Battle plan: each boon unlocks at its assigned age (0/1/2) and stacks. */
  boonPlan: { id: string; rarity: number; age: number }[];
  boon: BoonEffect; // boons currently active (unlocked at or below the current age)
  stats: {
    unitsKilled: number;
    unitsLost: number;
    buildingsRazed: number;
    buildingsLost: number;
    gathered: number;
  };
}

/** A planted commander banner buffing nearby friendly units. */
export interface Banner {
  team: Team;
  x: number;
  y: number;
  expires: number;
  power: CommanderPower;
}

/** Kills needed to reach hero levels 1..5. */
export const HERO_THRESHOLDS = [4, 10, 17, 26, 37];
/** Kills needed to reach veterancy ranks 1..3 (Veteran / Elite / Legendary). */
export const VET_THRESHOLDS = [2, 5, 9];
const HERO_RESPAWN_SEC = 45;

export interface WorldEvent {
  kind:
    | "sword" | "bow" | "arrowHit" | "siege" | "death" | "collapse"
    | "build" | "complete" | "underattack" | "age" | "deposit" | "spawn" | "hit" | "ability" | "callout";
  x: number;
  y: number;
  team: Team;
  data?: string;
}

export const FOG_UNSEEN = 0;
export const FOG_EXPLORED = 1;
export const FOG_VISIBLE = 2;

const RES_TYPE_KIND: Record<string, ResourceKind> = {
  tree: ResourceKind.Wood,
  gold_mine: ResourceKind.Gold,
  berries: ResourceKind.Food,
};

/** Buildings units can walk over (don't block the nav grid). */
const BUILDING_WALKABLE = new Set<string>(["farm"]);

/** Wall-class buildings (Bulwark boon, cheaper/tougher fortifications). */
const WALL_TYPES = new Set<string>(["palisade", "stone_wall", "gate"]);

let nextId = 1;

export function makeEntity(): Entity {
  return {
    id: nextId++,
    kind: Kind.Unit,
    team: Team.Neutral,
    type: "",
    alive: true,
    x: 0, y: 0, radius: 8, facing: 0,
    hp: 1, maxHp: 1,
    vx: 0, vy: 0, speed: 0,
    attack: 0, range: 0, attackCooldown: 0, attackInterval: 1,
    armor: 0, pierceArmor: 0, armorClass: ArmorClass.Infantry, visionRange: 100,
    order: { kind: OrderKind.Idle, tx: 0, ty: 0, target: -1 },
    path: null, pathIndex: 0, repathCooldown: 0,
    carry: 0, carryKind: null, gatherCooldown: 0,
    amount: 0,
    buildState: BuildState.Done, buildProgress: 1, popProvided: 0,
    rallyX: -1, rallyY: -1, productionQueue: [], productionTime: 0,
    garrison: [], gateOpen: false, gateForce: 0, farmWorker: -1,
    projTargetId: -1, projDamage: 0, projSpeed: 0, projSourceTeam: Team.Neutral,
    projArmorClassBonusFrom: "", projElapsed: 0, projDuration: 0, projFromX: 0, projFromY: 0,
    abilityCooldown: 0, abilityActive: 0, slowTimer: 0, rallyTimer: 0, heroLevel: 0, heroKills: 0,
    veterancy: 0, vetKills: 0, projSourceId: -1,
    animPhase: 0, hitFlash: 0, lastDamageTime: -999, lastAttackerId: -1, selected: false,
    variantRarity: 0, tier: 0,
  };
}

export class World {
  entities: Entity[] = [];
  byId = new Map<EntityId, Entity>();
  grid!: NavGrid;
  spatial = new SpatialGrid(96);
  players: PlayerState[] = [];
  rng: RNG;
  tickCount = 0;
  time = 0;
  worldW = 0;
  worldH = 0;
  events: WorldEvent[] = [];
  /** Per-team fog grids (tile resolution), indexed [team][cell]. */
  fog: Uint8Array[] = [];
  fogCols = 0;
  fogRows = 0;
  /** Spectator mode: reveal the entire map to every viewer (no fog). */
  revealAll = false;
  numTeams = 2; // number of player teams in this match (2..MAX_TEAMS)
  /** Alliance id per team; teams sharing an id are allies. Default: all solo. */
  alliances: number[] = [];
  winner: Team | null = null;
  map!: MapData;
  private lastAlertTime: number[] = [];

  constructor(seed: number) {
    this.rng = new RNG(seed);
    nextId = 1;
  }

  // ---------------------------------------------------------------- setup --

  banners: Banner[] = [];

  nomad = false;

  init(map: MapData, loadouts: Record<string, number>[], econMults: number[], alliances?: number[], commanders?: string[], nomad = false, boonLoadouts?: { id: string; rarity: number; age: number }[][]) {
    this.nomad = nomad;
    this.map = map;
    this.worldW = map.worldW;
    this.worldH = map.worldH;
    this.grid = new NavGrid(map.worldW, map.worldH);
    this.fogCols = this.grid.cols;
    this.fogRows = this.grid.rows;

    // Terrain blocking (water / cliffs) from map data.
    for (const [cx, cy] of map.blockedCells) this.grid.setBlocked(cx, cy, true);

    // A match has as many player teams as it has loadouts (2 for 1v1, up to 4
    // for a free-for-all). Everything below is sized off numTeams.
    this.numTeams = Math.min(loadouts.length, map.starts.length);
    // Alliances default to everyone-for-themselves (1v1 / FFA). A 2v2 passes
    // e.g. [0,1,0,1] so teams 0&2 ally against 1&3.
    this.alliances = alliances && alliances.length >= this.numTeams
      ? alliances.slice(0, this.numTeams)
      : Array.from({ length: this.numTeams }, (_, t) => t);
    this.lastAlertTime = new Array(this.numTeams).fill(-99);
    for (let t = 0; t < this.numTeams; t++) {
      const cmdId = commanders?.[t] ?? "";
      const cmd = COMMANDERS[cmdId];
      const b = cmd?.bonus ?? {};
      const res = { ...map.startResources };
      if (b.startResources) {
        res.food += b.startResources.food ?? 0;
        res.wood += b.startResources.wood ?? 0;
        res.gold += b.startResources.gold ?? 0;
      }
      // Nomad: no Town Center to start, so stake them enough wood to raise one.
      if (nomad) {
        res.wood += 280;
        res.food += 50;
      }
      this.players.push({
        team: t as Team,
        resources: res,
        age: 0,
        upgrades: new Set(),
        popUsed: 0,
        popCap: b.popBonus ?? 0,
        loadout: loadouts[t] ?? {},
        econMult: econMults[t] ?? 1,
        defeated: false,
        heroState: "none",
        heroRespawnTimer: 0,
        heroLevel: 0,
        commander: cmdId,
        buildMult: b.buildMult ?? 1,
        gatherMultC: b.gatherMult ?? 1,
        unitHpMult: b.unitHpMult ?? 1,
        unitArmorBonus: b.unitArmorBonus ?? 0,
        vetMult: b.vetMult ?? 1,
        powerCooldown: 0,
        boonPlan: boonLoadouts?.[t] ?? [],
        boon: aggregateBoons((boonLoadouts?.[t] ?? []).filter((bn) => bn.age <= 0)),
        stats: { unitsKilled: 0, unitsLost: 0, buildingsRazed: 0, buildingsLost: 0, gathered: 0 },
      });
      this.fog.push(new Uint8Array(this.fogCols * this.fogRows));
    }

    // Resources.
    for (const r of map.resources) {
      this.spawnResource(r.type, r.x, r.y, r.amount);
    }

    // Starting bases.
    for (let t = 0; t < this.numTeams; t++) {
      const start = map.starts[t];
      const extra = COMMANDERS[commanders?.[t] ?? ""]?.bonus.startVillagers ?? 0;
      if (nomad) {
        // No Town Center — villagers dotted across a wide area around the spawn.
        const count = 4 + extra; // a touch more than the standard three
        for (let i = 0; i < count; i++) {
          const a = this.rng.range(0, Math.PI * 2);
          const r = this.rng.range(TILE * 1.5, TILE * 6); // wide scatter
          this.spawnUnit(t as Team, "villager", start.x + Math.cos(a) * r, start.y + Math.sin(a) * r);
        }
      } else {
        this.spawnBuilding(t as Team, "town_center", start.x, start.y, true);
        const offsets: number[][] = [[-60, 50], [0, 64], [60, 50]];
        for (let i = 0; i < extra; i++) offsets.push([-90 + i * 36, 84]);
        for (const [dx, dy] of offsets) {
          this.spawnUnit(t as Team, "villager", start.x + dx, start.y + dy);
        }
      }
    }
    this.recomputeVision();
  }

  player(team: Team): PlayerState {
    return this.players[team];
  }

  /** Same team, or teams sharing an alliance. Neutral allies no one. */
  areAllied(a: Team, b: Team): boolean {
    if (a === b) return true;
    if (a === Team.Neutral || b === Team.Neutral) return false;
    if (a >= this.numTeams || b >= this.numTeams) return false;
    return this.alliances[a] === this.alliances[b];
  }

  /** Opposing player teams (never Neutral, never allies). */
  areHostile(a: Team, b: Team): boolean {
    if (a === Team.Neutral || b === Team.Neutral) return false;
    return !this.areAllied(a, b);
  }

  /** True when at least two teams share an alliance (i.e. a 2v2 team game). */
  hasTeamAlliances(): boolean {
    for (let a = 0; a < this.numTeams; a++) {
      for (let b = a + 1; b < this.numTeams; b++) {
        if (this.alliances[a] === this.alliances[b]) return true;
      }
    }
    return false;
  }

  /** Relation of `team` to the viewer, for diplomacy colouring. */
  relationTo(viewer: Team, team: Team): "self" | "ally" | "enemy" {
    if (team === viewer) return "self";
    return this.areAllied(viewer, team) ? "ally" : "enemy";
  }

  /** Stable 0-based index of `team` among the viewer's enemies (for colours). */
  enemyIndexOf(viewer: Team, team: Team): number {
    let idx = 0;
    for (let t = 0; t < this.numTeams; t++) {
      if (t === team) break;
      if (this.areHostile(viewer, t as Team)) idx++;
    }
    return idx;
  }

  // ------------------------------------------------------------- spawning --

  /** Effective stats for a unit owned by `team`, applying rarity + age/tech. */
  private applyUnitStats(e: Entity, def: UnitDef, team: Team) {
    const p = this.players[team];
    const rarity = p?.loadout[def.id] ?? 0;
    e.variantRarity = rarity;
    const bn = p?.boon ?? emptyBoonEffect();
    // Commander bonuses apply to soldiers (not villagers).
    const soldier = !def.canGather;
    const hpMult = (soldier ? (p?.unitHpMult ?? 1) : 1) * bn.hpMult; // boon HP applies to all
    const armorPlus = (soldier ? (p?.unitArmorBonus ?? 0) : 0) + bn.armorBonus;
    // Attack boon by armour class.
    let atkBoon = 1;
    if (def.armorClass === ArmorClass.Infantry) atkBoon = bn.atkMultInfantry;
    else if (def.armorClass === ArmorClass.Archer) atkBoon = bn.atkMultArcher;
    else if (def.armorClass === ArmorClass.Cavalry) atkBoon = bn.atkMultCavalry;
    // Speed boon: army-wide, plus cavalry/villager extras.
    let spdBoon = soldier ? bn.armySpeedMult : bn.villSpeedMult;
    if (def.armorClass === ArmorClass.Cavalry) spdBoon *= bn.cavSpeedMult;
    // Flat HP from researched "hp" upgrades (e.g. Bloodlines for cavalry).
    let hpFlat = 0;
    if (p) {
      for (const upId of p.upgrades) {
        const up = UPGRADES[upId];
        if (up?.kind === "hp" && (up.appliesTo.includes(def.armorClass) || (up.appliesToUnits?.includes(def.id) ?? false))) hpFlat += up.amount;
      }
    }
    e.maxHp = Math.round(def.hp * (RARITY_HP_MULT[rarity] ?? 1) * hpMult) + hpFlat;
    e.hp = e.maxHp;
    e.attack = Math.round(def.attack * (RARITY_ATK_MULT[rarity] ?? 1) * atkBoon);
    e.armor = def.armor + (RARITY_ARMOR_BONUS[rarity] ?? 0) + armorPlus;
    // Pierce armor mirrors melee armor unless the unit defines a different value
    // (e.g. the Horseman: 0 melee / 2 pierce). The delta rides on top of bonuses.
    e.pierceArmor = e.armor + (def.pierceArmor !== undefined ? def.pierceArmor - def.armor : 0);
    e.range = def.range + (def.armorClass === ArmorClass.Archer ? bn.archerRangeBonus : 0);
    e.attackInterval = def.attackInterval;
    e.speed = def.speed * (RARITY_SPEED_MULT[rarity] ?? 1) * spdBoon;
    e.visionRange = def.visionRange * bn.visionMult;
    e.radius = def.radius;
    e.armorClass = def.armorClass;
  }

  /** Recompute a team's active boons for its current age, then refresh its army
   *  so a newly-unlocked boon buffs the troops you already have (veterancy and
   *  hero levels are preserved). Called on age-up. */
  recomputeBoons(team: Team) {
    const p = this.players[team];
    if (!p) return;
    p.boon = aggregateBoons(p.boonPlan.filter((b) => b.age <= p.age));
    this.refreshTeamUnits(team);
  }

  /** Re-derive every unit's stats for a team (after a boon/HP-upgrade unlock),
   *  preserving current HP fraction, veterancy and hero level. */
  private refreshTeamUnits(team: Team) {
    for (const e of this.entities) {
      if (!e.alive || e.kind !== Kind.Unit || e.team !== team) continue;
      const def = UNITS[e.type];
      if (!def) continue;
      const frac = e.maxHp > 0 ? e.hp / e.maxHp : 1;
      this.applyUnitStats(e, def, team); // base + rarity + current boon + hp upgrades
      // Re-stack veterancy (compounds, matches creditVeterancy) and hero level.
      for (let i = 0; i < e.veterancy; i++) e.maxHp += Math.round(e.maxHp * 0.12);
      e.attack += e.veterancy;
      e.armor += Math.max(0, e.veterancy - 1);
      if (def.hero && e.heroLevel > 0) {
        e.maxHp += e.heroLevel * 35;
        e.attack += e.heroLevel * 3;
        e.armor += Math.floor(e.heroLevel / 2);
      }
      e.hp = Math.max(1, Math.round(e.maxHp * frac));
    }
  }

  spawnUnit(team: Team, type: string, x: number, y: number): Entity {
    const def = UNITS[type];
    const e = makeEntity();
    e.kind = Kind.Unit;
    e.team = team;
    e.type = type;
    [e.x, e.y] = this.grid ? this.grid.nearestOpenWorld(x, y) : [x, y];
    this.applyUnitStats(e, def, team);
    e.facing = this.rng.range(0, Math.PI * 2);
    this.entities.push(e);
    this.byId.set(e.id, e);
    const p = this.players[team];
    if (p) p.popUsed += def.pop;
    return e;
  }

  spawnBuilding(team: Team, type: string, x: number, y: number, completed: boolean): Entity {
    const def = BUILDINGS[type];
    const e = makeEntity();
    e.kind = Kind.Building;
    e.team = team;
    e.type = type;
    // Snap to tile grid so footprints align.
    const tiles = def.tiles;
    e.x = Math.round(x / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    e.y = Math.round(y / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    e.radius = (tiles * TILE) / 2;
    const bn = this.players[team]?.boon ?? emptyBoonEffect();
    const hpMult = WALL_TYPES.has(type) ? bn.wallHpMult : bn.buildingHpMult;
    const maxHp = Math.round(def.hp * hpMult);
    e.maxHp = maxHp;
    e.armorClass = ArmorClass.Building;
    e.armor = 1;
    e.pierceArmor = 1;
    e.visionRange = def.sight;
    e.attack = def.attack;
    // Vigilant Watch: defensive buildings fire faster and farther.
    e.range = def.range + (def.attack > 0 ? bn.towerRangeBonus : 0);
    e.attackInterval = def.attack > 0 ? def.attackInterval * bn.towerCdMult : def.attackInterval;
    if (completed) {
      e.hp = maxHp;
      e.buildState = BuildState.Done;
      e.buildProgress = 1;
      this.onBuildingCompleted(e, def, true);
    } else {
      e.hp = Math.max(1, maxHp * 0.08);
      e.buildState = BuildState.Foundation;
      e.buildProgress = 0;
    }
    // Farms are crop plots — units freely walk across them, so they never block
    // the nav grid (placement overlap is still guarded in placeBuilding).
    if (!BUILDING_WALKABLE.has(type)) this.grid.stampFootprint(e.x, e.y, tiles, true);
    this.entities.push(e);
    this.byId.set(e.id, e);
    return e;
  }

  spawnResource(type: string, x: number, y: number, amount: number): Entity {
    const e = makeEntity();
    e.kind = Kind.Resource;
    e.team = Team.Neutral;
    e.type = type;
    e.x = Math.floor(x / TILE) * TILE + TILE / 2;
    e.y = Math.floor(y / TILE) * TILE + TILE / 2;
    e.radius = TILE / 2;
    e.amount = amount;
    e.hp = e.maxHp = 1;
    this.grid.stampFootprint(e.x, e.y, 1, true);
    this.entities.push(e);
    this.byId.set(e.id, e);
    return e;
  }

  private onBuildingCompleted(e: Entity, def: BuildingDef, silent = false) {
    const p = this.players[e.team];
    if (p) {
      p.popCap = Math.min(POP_CAP_HARD, p.popCap + def.popProvided);
    }
    e.popProvided = def.popProvided;
    // Farms act as a renewable food node once finished.
    if (def.id === "farm") e.amount = 999999;
    if (!silent) this.emit("complete", e.x, e.y, e.team, def.id);
  }

  // --------------------------------------------------------------- events --

  private emit(kind: WorldEvent["kind"], x: number, y: number, team: Team, data?: string) {
    if (this.events.length < 512) this.events.push({ kind, x, y, team, data });
  }

  drainEvents(): WorldEvent[] {
    const out = this.events;
    this.events = [];
    return out;
  }

  /** AI voice line — surfaced by the UI for allies of the viewing player. */
  callout(team: Team, x: number, y: number, text: string) {
    this.emit("callout", x, y, team, text);
  }

  // ------------------------------------------------------------- commands --

  private resolveOrderQueue(e: Entity, order: Order, queue: boolean) {
    if (queue && e.order.kind !== OrderKind.Idle) {
      if (!e.order.queue) e.order.queue = [];
      e.order.queue.push(order);
    } else {
      order.queue = queue && e.order.queue ? e.order.queue : order.queue;
      e.order = order;
      e.path = null;
      e.pathIndex = 0;
    }
  }

  issueMove(ids: EntityId[], tx: number, ty: number, queue = false, attackMove = false) {
    const movers = ids
      .map((id) => this.byId.get(id))
      .filter((e): e is Entity => !!e && e.alive && e.kind === Kind.Unit);
    if (movers.length === 0) return;
    const kind = attackMove ? OrderKind.AttackMove : OrderKind.Move;
    // Groups share one flow field; singles use A*.
    let flow: FlowField | null = null;
    if (movers.length > 3) flow = new FlowField(this.grid, tx, ty);
    for (const e of movers) {
      const order: Order = { kind, tx, ty, target: -1 };
      (order as any).flow = flow;
      this.resolveOrderQueue(e, order, queue);
    }
  }

  /**
   * Like issueMove, but spreads the group into a box formation facing the
   * direction of travel so units arrive side-by-side instead of stacking on
   * one point. Used for player commands; the AI keeps the cheaper issueMove.
   */
  issueFormationMove(ids: EntityId[], tx: number, ty: number, queue = false, attackMove = false) {
    const movers = ids
      .map((id) => this.byId.get(id))
      .filter((e): e is Entity => !!e && e.alive && e.kind === Kind.Unit);
    if (movers.length <= 1) return this.issueMove(ids, tx, ty, queue, attackMove);
    const kind = attackMove ? OrderKind.AttackMove : OrderKind.Move;
    const n = movers.length;

    // Forward = from the group's centre toward the target.
    let ax = 0;
    let ay = 0;
    for (const e of movers) { ax += e.x; ay += e.y; }
    ax /= n; ay /= n;
    let ang = Math.atan2(ty - ay, tx - ax);
    if (!Number.isFinite(ang)) ang = 0;
    const ux = Math.cos(ang);
    const uy = Math.sin(ang);
    const px = -uy; // rightward across the front
    const py = ux;
    const cols = Math.max(1, Math.round(Math.sqrt(n)));
    const spacing = 24;

    // Slot positions: a grid centred on the target, front rank at the target.
    const slots: { x: number; y: number }[] = [];
    for (let i = 0; i < n; i++) {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const lx = (col - (cols - 1) / 2) * spacing;
      const ly = row * spacing;
      const [sx, sy] = this.grid.nearestOpenWorld(tx + px * lx - ux * ly, ty + py * lx - uy * ly);
      slots.push({ x: sx, y: sy });
    }

    const flow = n > 3 ? new FlowField(this.grid, tx, ty) : null;
    const used = new Array(n).fill(false);
    for (const e of movers) {
      let best = -1;
      let bestD = Infinity;
      for (let s = 0; s < n; s++) {
        if (used[s]) continue;
        const d = dist2(e.x, e.y, slots[s].x, slots[s].y);
        if (d < bestD) { bestD = d; best = s; }
      }
      used[best] = true;
      const order: Order = { kind, tx: slots[best].x, ty: slots[best].y, target: -1 };
      (order as any).flow = flow;
      (order as any).slot = true;
      this.resolveOrderQueue(e, order, queue);
    }
  }

  issueAttack(ids: EntityId[], targetId: EntityId, queue = false) {
    const target = this.byId.get(targetId);
    if (!target || !target.alive) return;
    for (const id of ids) {
      const e = this.byId.get(id);
      if (!e || !e.alive || e.kind !== Kind.Unit) continue;
      this.resolveOrderQueue(e, { kind: OrderKind.Attack, tx: target.x, ty: target.y, target: targetId }, queue);
    }
  }

  issueGather(ids: EntityId[], nodeId: EntityId, queue = false) {
    const node = this.byId.get(nodeId);
    if (!node || !node.alive) return;
    for (const id of ids) {
      const e = this.byId.get(id);
      if (!e || !e.alive || !UNITS[e.type]?.canGather) continue;
      this.resolveOrderQueue(e, { kind: OrderKind.Gather, tx: node.x, ty: node.y, target: nodeId }, queue);
    }
  }

  issueBuildRepair(ids: EntityId[], buildingId: EntityId, queue = false) {
    const b = this.byId.get(buildingId);
    if (!b || !b.alive || b.kind !== Kind.Building) return;
    const kind = b.buildState === BuildState.Done ? OrderKind.Repair : OrderKind.Build;
    for (const id of ids) {
      const e = this.byId.get(id);
      if (!e || !e.alive || !UNITS[e.type]?.canBuild) continue;
      this.resolveOrderQueue(e, { kind, tx: b.x, ty: b.y, target: buildingId }, queue);
    }
  }

  issueStop(ids: EntityId[]) {
    for (const id of ids) {
      const e = this.byId.get(id);
      if (!e || !e.alive) continue;
      e.order = { kind: OrderKind.Idle, tx: 0, ty: 0, target: -1 };
      e.path = null;
    }
  }

  issueHold(ids: EntityId[]) {
    for (const id of ids) {
      const e = this.byId.get(id);
      if (!e || !e.alive) continue;
      e.order = { kind: OrderKind.Hold, tx: e.x, ty: e.y, target: -1 };
      e.path = null;
    }
  }

  /**
   * Trigger each selected unit's signature ability if it has one and it's off
   * cooldown. Resolves the ability by kind (self-buff, ally rally, enemy slow,
   * burst heal, or an area volley). Returns how many fired (for UI feedback).
   */
  useAbility(ids: EntityId[]): number {
    let fired = 0;
    for (const id of ids) {
      const e = this.byId.get(id);
      if (!e || !e.alive || e.kind !== Kind.Unit) continue;
      const ab = ABILITIES[e.type];
      if (!ab || e.abilityCooldown > 0) continue;
      // Higher-rarity units wield a stronger, faster-recharging ability.
      const pm = RARITY_ABILITY_POWER_MULT[e.variantRarity] ?? 1;
      const cm = RARITY_ABILITY_CD_MULT[e.variantRarity] ?? 1;
      e.abilityActive = ab.duration * pm;
      e.abilityCooldown = ab.cooldown * cm;

      switch (ab.kind) {
        case "rally": {
          // Lift the attack of every nearby ally (and yourself).
          const r = (ab.radius ?? 150) * pm;
          for (const n of this.spatial.query(e.x, e.y, r) as Entity[]) {
            if (n.alive && n.kind === Kind.Unit && this.areAllied(e.team, n.team) && dist(e.x, e.y, n.x, n.y) <= r) {
              n.rallyTimer = Math.max(n.rallyTimer, (ab.statusDuration ?? 6) * pm);
            }
          }
          break;
        }
        case "slow": {
          // Hobble nearby enemies.
          const r = (ab.radius ?? 120) * pm;
          for (const n of this.spatial.query(e.x, e.y, r) as Entity[]) {
            if (n.alive && n.kind === Kind.Unit && this.areHostile(e.team, n.team) &&
                dist(e.x, e.y, n.x, n.y) <= r) {
              n.slowTimer = Math.max(n.slowTimer, (ab.statusDuration ?? 4) * pm);
            }
          }
          break;
        }
        case "heal": {
          // Instantly mend wounded allies around the caster.
          const r = (ab.radius ?? 160) * pm;
          const amt = (ab.amount ?? 40) * pm;
          for (const n of this.spatial.query(e.x, e.y, r) as Entity[]) {
            if (n.alive && n.kind === Kind.Unit && this.areAllied(e.team, n.team) && n.hp < n.maxHp &&
                dist(e.x, e.y, n.x, n.y) <= r) {
              n.hp = Math.min(n.maxHp, n.hp + amt);
            }
          }
          break;
        }
        case "volley": {
          // Rain a barrage on the unit's target (or the nearest foe in sight).
          const r = (ab.radius ?? 84) * pm;
          const amt = (ab.amount ?? 16) * pm;
          let ix = e.x;
          let iy = e.y;
          const tgt = this.byId.get(e.order.target);
          if (tgt && tgt.alive && this.areHostile(e.team, tgt.team)) {
            ix = tgt.x;
            iy = tgt.y;
          } else {
            const foe = this.findEnemyInRange(e, e.visionRange);
            if (foe) {
              ix = foe.x;
              iy = foe.y;
            }
          }
          for (const n of this.spatial.query(ix, iy, r) as Entity[]) {
            if (n.alive && this.areHostile(e.team, n.team) && dist(ix, iy, n.x, n.y) <= r) {
              this.dealDamage(e.team, n, amt, e.type, e.id);
            }
          }
          this.emit("ability", ix, iy, e.team, ab.id);
          break;
        }
        case "cleave": {
          // A heroic sweep: damage every foe around the hero, rally every ally.
          const r = (ab.radius ?? 115) * pm;
          for (const n of this.spatial.query(e.x, e.y, r) as Entity[]) {
            if (!n.alive || n.kind !== Kind.Unit || dist(e.x, e.y, n.x, n.y) > r) continue;
            if (this.areHostile(e.team, n.team)) this.dealDamage(e.team, n, (ab.amount ?? 24) * pm, e.type, e.id);
            else if (this.areAllied(e.team, n.team)) {
              n.rallyTimer = Math.max(n.rallyTimer, (ab.statusDuration ?? 5) * pm);
            }
          }
          break;
        }
      }

      this.emit("ability", e.x, e.y - e.radius, e.team, ab.id);
      fired++;
    }
    return fired;
  }

  /** Apply a hero's level bonuses to a freshly-spawned Champion. */
  private applyHeroLevel(e: Entity, level: number) {
    e.heroLevel = level;
    e.heroKills = level > 0 ? HERO_THRESHOLDS[level - 1] : 0;
    e.maxHp += level * 35;
    e.attack += level * 3;
    e.armor += Math.floor(level / 2); // +1 at lvl 2-3, +2 at lvl 4-5
    e.hp = e.maxHp; // respawn at full (not a mid-fight heal)
  }

  /**
   * Award a kill to the unit that landed it, ranking it up the veterancy ladder
   * (Veteran → Elite → Legendary). Villagers, heroes (own leveling) and
   * buildings don't earn veterancy.
   */
  private creditVeterancy(killerId: EntityId) {
    if (killerId < 0) return;
    const k = this.byId.get(killerId);
    if (!k || !k.alive || k.kind !== Kind.Unit) return;
    const def = UNITS[k.type];
    if (!def || def.canGather || def.hero || def.attack <= 0) return;
    k.vetKills++;
    const vetMult = this.players[k.team]?.vetMult ?? 1;
    const need = (rank: number) => Math.max(1, Math.round(VET_THRESHOLDS[rank] / vetMult));
    while (k.veterancy < VET_THRESHOLDS.length && k.vetKills >= need(k.veterancy)) {
      k.veterancy++;
      // Bigger max HP (but NOT a heal — staying alive is the unit's job), plus
      // a sharper edge in attack and, from Elite, armour.
      k.maxHp += Math.round(k.maxHp * 0.12);
      k.attack += 1;
      if (k.veterancy >= 2) k.armor += 1;
      if (k.team === Team.Player) this.emit("ability", k.x, k.y - k.radius, k.team, "veteran");
    }
  }

  /** Credit the nearest friendly Champion for a kill near (x,y) and level it up. */
  private creditHeroKill(byTeam: Team, x: number, y: number) {
    let hero: Entity | null = null;
    let best = 170 * 170;
    for (const h of this.spatial.query(x, y, 180) as Entity[]) {
      if (!h.alive || h.team !== byTeam || !UNITS[h.type]?.hero) continue;
      const d = dist2(x, y, h.x, h.y);
      if (d < best) {
        best = d;
        hero = h;
      }
    }
    if (!hero) return;
    hero.heroKills++;
    const p = this.players[byTeam];
    while (hero.heroLevel < HERO_THRESHOLDS.length && hero.heroKills >= HERO_THRESHOLDS[hero.heroLevel]) {
      hero.heroLevel++;
      // Tougher over time, but no mid-fight heal (a kill shouldn't top you up),
      // and a gentler curve so a few kills don't make him unstoppable.
      hero.maxHp += 35;
      hero.attack += 3;
      if (hero.heroLevel % 2 === 0) hero.armor += 1; // +1 at levels 2 & 4 (max +2)
      if (p) p.heroLevel = hero.heroLevel;
      this.emit("ability", hero.x, hero.y - hero.radius, byTeam, "hero_levelup");
    }
  }

  // Commander power -----------------------------------------------------------

  /** True if `team`'s commander has a power ready to plant. */
  powerReady(team: Team): boolean {
    const p = this.players[team];
    if (!p || p.powerCooldown > 0) return false;
    return !!COMMANDERS[p.commander]?.power;
  }

  /** Plant the commander's banner at (wx,wy). Returns true if planted. */
  placeBanner(team: Team, wx: number, wy: number): boolean {
    const p = this.players[team];
    const power = COMMANDERS[p?.commander ?? ""]?.power;
    if (!p || !power || p.powerCooldown > 0) return false;
    this.banners.push({ team, x: wx, y: wy, expires: this.time + power.duration, power });
    p.powerCooldown = power.cooldown;
    this.emit("ability", wx, wy, team, power.id);
    return true;
  }

  /** The active friendly banner covering (x,y), if any. */
  private bannerAt(team: Team, x: number, y: number): Banner | null {
    for (const b of this.banners) {
      if (!this.areAllied(team, b.team)) continue;
      const r = b.power.radius;
      const dx = x - b.x;
      const dy = y - b.y;
      if (dx * dx + dy * dy <= r * r) return b;
    }
    return null;
  }

  /** Per-tick: expire banners, heal soldiers standing under one, cool powers. */
  private tickBanners() {
    for (let t = 0; t < this.numTeams; t++) {
      const p = this.players[t];
      if (p && p.powerCooldown > 0) p.powerCooldown = Math.max(0, p.powerCooldown - SIM_DT);
    }
    if (this.banners.length === 0) return;
    this.banners = this.banners.filter((b) => b.expires > this.time);
    for (const b of this.banners) {
      if (b.power.militaryRegen <= 0) continue;
      const r = b.power.radius;
      for (const e of this.spatial.query(b.x, b.y, r) as Entity[]) {
        if (!e.alive || e.kind !== Kind.Unit || e.hp >= e.maxHp) continue;
        if (!this.areAllied(b.team, e.team)) continue;
        if (UNITS[e.type]?.canGather) continue; // soldiers only
        if (dist2(b.x, b.y, e.x, e.y) > r * r) continue;
        e.hp = Math.min(e.maxHp, e.hp + b.power.militaryRegen * SIM_DT);
      }
    }
  }

  /** Validate placement + pay cost + spawn foundation. Returns entity or null. */
  placeBuilding(team: Team, type: string, wx: number, wy: number): Entity | null {
    const def = BUILDINGS[type];
    const p = this.players[team];
    if (!def || !p) return null;
    if (p.age < def.age) return null;
    if (def.requires && !this.hasBuilding(team, def.requires)) return null;
    // Master Masons (buildings) / Bulwark (walls) make construction cheaper.
    const cost = this.scaledCost(def.cost, WALL_TYPES.has(type) ? p.boon.wallCostMult : p.boon.buildCostMult);
    if (!this.canAfford(p.resources, cost)) return null;
    const tiles = def.tiles;
    const sx = Math.round(wx / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    const sy = Math.round(wy / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    if (!this.grid.footprintClear(sx, sy, tiles)) return null;
    const overlaps = (ent: Entity) =>
      Math.abs(ent.x - sx) < (tiles * TILE) / 2 + ent.radius &&
      Math.abs(ent.y - sy) < (tiles * TILE) / 2 + ent.radius;
    // No building on top of units.
    const near = this.spatial.query(sx, sy, tiles * TILE * 0.75) as Entity[];
    for (const n of near) {
      if (n.alive && (n as Entity).kind === Kind.Unit && overlaps(n as Entity)) return null;
    }
    // No building on top of a walkable building (farm) — those don't stamp the
    // grid, so footprintClear can't catch them. Scanned directly since the
    // spatial index may not include a farm placed this same tick.
    for (const ent of this.entities) {
      if (ent.alive && ent.kind === Kind.Building && BUILDING_WALKABLE.has(ent.type) && overlaps(ent)) {
        return null;
      }
    }
    this.pay(p.resources, cost);
    const e = this.spawnBuilding(team, type, sx, sy, false);
    this.emit("build", e.x, e.y, team, type);
    return e;
  }

  canPlace(team: Team, type: string, wx: number, wy: number): boolean {
    const def = BUILDINGS[type];
    if (!def) return false;
    const tiles = def.tiles;
    const sx = Math.round(wx / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    const sy = Math.round(wy / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    return this.grid.footprintClear(sx, sy, tiles);
  }

  /** How many distinct qualifying building types `team` has, vs how many an age needs. */
  ageRequirementProgress(team: Team, ageIndex: number): { have: number; need: number } {
    const age = AGES[ageIndex];
    if (!age) return { have: 0, need: 0 };
    let have = 0;
    for (const id of age.requiresAny) if (this.hasBuilding(team, id)) have++;
    return { have: Math.min(have, age.requiresCount), need: age.requiresCount };
  }

  ageRequirementMet(team: Team, ageIndex: number): boolean {
    const { have, need } = this.ageRequirementProgress(team, ageIndex);
    return have >= need;
  }

  hasBuilding(team: Team, type: string): boolean {
    for (const e of this.entities) {
      if (e.alive && e.team === team && e.kind === Kind.Building && e.type === type && e.buildState === BuildState.Done) {
        return true;
      }
    }
    return false;
  }

  trainUnit(team: Team, buildingId: EntityId, unitType: string): boolean {
    const b = this.byId.get(buildingId);
    const p = this.players[team];
    const def = UNITS[unitType];
    if (!b || !b.alive || !p || !def || b.team !== team) return false;
    if (b.buildState !== BuildState.Done) return false;
    if (!BUILDINGS[b.type].trains.includes(unitType)) return false;
    if (p.age < def.age) return false;
    // One Champion per realm: only trainable when none is fielded or pending.
    if (def.hero && (p.heroState !== "none" || b.productionQueue.includes("u:hero"))) return false;
    if (b.productionQueue.length >= 8) return false;
    if (p.popUsed + def.pop > p.popCap) return false;
    const cost = this.scaledCost(def.cost, p.boon.unitCostMult); // Quartermaster
    if (!this.canAfford(p.resources, cost)) return false;
    this.pay(p.resources, cost);
    b.productionQueue.push(`u:${unitType}`);
    if (b.productionQueue.length === 1) b.productionTime = def.buildTime * p.boon.trainSpeedMult;
    return true;
  }

  /** Champion availability for the command card. */
  heroStatus(team: Team): { trainable: boolean; label: string } {
    const p = this.players[team];
    if (!p || p.heroState === "none") return { trainable: true, label: "" };
    if (p.heroState === "respawning") {
      return { trainable: false, label: `Rises in ${Math.ceil(p.heroRespawnTimer)}s` };
    }
    return { trainable: false, label: "In the field" };
  }

  research(team: Team, buildingId: EntityId, techId: string): boolean {
    const b = this.byId.get(buildingId);
    const p = this.players[team];
    if (!b || !b.alive || !p || b.team !== team || b.buildState !== BuildState.Done) return false;
    if (techId === "age") {
      const next = p.age + 1;
      if (next > MAX_AGE || b.type !== "town_center") return false;
      const age = AGES[next];
      if (!this.ageRequirementMet(team, next)) return false;
      if (!this.canAfford(p.resources, age.cost)) return false;
      if (b.productionQueue.some((q) => q === "a:age")) return false;
      this.pay(p.resources, age.cost);
      b.productionQueue.push("a:age");
      if (b.productionQueue.length === 1) b.productionTime = age.advanceTime;
      return true;
    }
    const up = UPGRADES[techId];
    if (!up || p.upgrades.has(techId) || p.age < up.age) return false;
    if (b.type !== up.researchedAt) return false;
    if (b.productionQueue.includes(`t:${techId}`)) return false;
    if (!this.canAfford(p.resources, up.cost)) return false;
    this.pay(p.resources, up.cost);
    b.productionQueue.push(`t:${techId}`);
    if (b.productionQueue.length === 1) b.productionTime = up.time;
    return true;
  }

  setRally(buildingId: EntityId, x: number, y: number) {
    const b = this.byId.get(buildingId);
    if (b && b.alive && b.kind === Kind.Building) {
      b.rallyX = x;
      b.rallyY = y;
    }
  }

  garrison(ids: EntityId[], buildingId: EntityId) {
    const b = this.byId.get(buildingId);
    if (!b || !b.alive) return;
    const cap = BUILDINGS[b.type]?.garrisonCap ?? 0;
    for (const id of ids) {
      if (b.garrison.length >= cap) break;
      const e = this.byId.get(id);
      if (!e || !e.alive || e.kind !== Kind.Unit || e.team !== b.team) continue;
      if (dist(e.x, e.y, b.x, b.y) > b.radius + 60) {
        // walk there first; garrison via order target re-issue when close
        this.resolveOrderQueue(e, { kind: OrderKind.Move, tx: b.x, ty: b.y, target: buildingId }, false);
        continue;
      }
      e.alive = false; // hidden while garrisoned (not dead — kept in garrison list)
      b.garrison.push(e.id);
    }
  }

  ungarrison(buildingId: EntityId) {
    const b = this.byId.get(buildingId);
    if (!b) return;
    for (const id of b.garrison) {
      const e = this.byId.get(id);
      if (!e) continue;
      const [ox, oy] = this.grid.nearestOpenWorld(
        b.x + this.rng.range(-b.radius - 20, b.radius + 20),
        b.y + b.radius + 24,
      );
      e.alive = true;
      e.x = ox;
      e.y = oy;
      e.order = { kind: OrderKind.Idle, tx: 0, ty: 0, target: -1 };
    }
    b.garrison.length = 0;
  }

  /** Market trading at fixed, slightly lossy rates. */
  marketTrade(team: Team, action: "sell_wood" | "sell_food" | "buy_wood" | "buy_food"): boolean {
    const p = this.players[team];
    if (!p || !this.hasBuilding(team, "market")) return false;
    const r = p.resources;
    switch (action) {
      case "sell_wood":
        if (r.wood < 100) return false;
        r.wood -= 100; r.gold += 75; return true;
      case "sell_food":
        if (r.food < 100) return false;
        r.food -= 100; r.gold += 75; return true;
      case "buy_wood":
        if (r.gold < 100) return false;
        r.gold -= 100; r.wood += 75; return true;
      case "buy_food":
        if (r.gold < 100) return false;
        r.gold -= 100; r.food += 75; return true;
    }
  }

  canAfford(bag: ResourceBag, cost: { food: number; wood: number; gold: number }): boolean {
    return bag.food >= cost.food && bag.wood >= cost.wood && bag.gold >= cost.gold;
  }

  private pay(bag: ResourceBag, cost: { food: number; wood: number; gold: number }) {
    bag.food -= cost.food;
    bag.wood -= cost.wood;
    bag.gold -= cost.gold;
  }

  /** Cost scaled by a multiplier (boons: cheaper units/buildings), rounded. */
  private scaledCost(cost: { food: number; wood: number; gold: number }, mult: number) {
    if (mult === 1) return cost;
    return { food: Math.round(cost.food * mult), wood: Math.round(cost.wood * mult), gold: Math.round(cost.gold * mult) };
  }

  // ----------------------------------------------------------------- tick --

  tick() {
    this.tickCount++;
    this.time += SIM_DT;

    // Rebuild spatial hash.
    this.spatial.clear();
    for (const e of this.entities) {
      if (e.alive) this.spatial.insert(e);
    }

    for (const e of this.entities) {
      if (!e.alive) continue;
      e.attackCooldown = Math.max(0, e.attackCooldown - SIM_DT);
      e.abilityCooldown = Math.max(0, e.abilityCooldown - SIM_DT);
      e.abilityActive = Math.max(0, e.abilityActive - SIM_DT);
      e.slowTimer = Math.max(0, e.slowTimer - SIM_DT);
      e.rallyTimer = Math.max(0, e.rallyTimer - SIM_DT);
      e.hitFlash = Math.max(0, e.hitFlash - SIM_DT * 3);
      switch (e.kind) {
        case Kind.Unit: this.tickUnit(e); break;
        case Kind.Building: this.tickBuilding(e); break;
        case Kind.Projectile: this.tickProjectile(e); break;
        case Kind.Resource: break;
      }
    }

    resolveOverlaps(this.entities, this.spatial, SIM_DT);

    // Keep units inside the world and off blocked cells.
    for (const e of this.entities) {
      if (!e.alive || e.kind !== Kind.Unit) continue;
      e.x = Math.max(8, Math.min(this.worldW - 8, e.x));
      e.y = Math.max(8, Math.min(this.worldH - 8, e.y));
      if (this.grid.isBlockedWorld(e.x, e.y)) {
        const [ox, oy] = this.grid.nearestOpenWorld(e.x, e.y, 4);
        e.x = ox;
        e.y = oy;
      }
    }

    if (this.tickCount % 5 === 0) this.recomputeVision();
    if (this.tickCount % 20 === 0) this.checkVictory();
    if (this.tickCount % 600 === 0) this.compact();
    this.tickHeroRespawns();
    this.tickBanners();
  }

  /** Bring fallen Champions back at their Town Center once the timer elapses. */
  private tickHeroRespawns() {
    for (let t = 0; t < this.numTeams; t++) {
      const p = this.players[t];
      if (!p || p.heroState !== "respawning") continue;
      p.heroRespawnTimer -= SIM_DT;
      if (p.heroRespawnTimer > 0) continue;
      const tc = this.entities.find(
        (e) => e.alive && e.team === t && e.kind === Kind.Building &&
          e.type === "town_center" && e.buildState === BuildState.Done,
      );
      if (!tc) {
        p.heroRespawnTimer = 0; // wait for a Town Center to exist
        continue;
      }
      const [sx, sy] = this.grid.nearestOpenWorld(tc.x, tc.y + tc.radius + 20);
      const u = this.spawnUnit(t as Team, "hero", sx, sy);
      this.applyHeroLevel(u, p.heroLevel);
      p.heroState = "alive";
      this.emit("spawn", sx, sy, t as Team, "hero");
    }
  }

  // Units ------------------------------------------------------------------

  private tickUnit(e: Entity) {
    const def = UNITS[e.type];
    e.animPhase += SIM_DT * (1 + (Math.hypot(e.vx, e.vy) > 4 ? 2 : 0));

    // Field Medic boon: units mend out of combat (5s since last hit).
    const rgn = this.players[e.team]?.boon.regenRate ?? 0;
    if (rgn > 0 && e.hp < e.maxHp && this.time - e.lastDamageTime > 5) {
      e.hp = Math.min(e.maxHp, e.hp + rgn * SIM_DT);
    }

    switch (e.order.kind) {
      case OrderKind.Idle:
      case OrderKind.Hold:
        this.autoAcquire(e, def, e.order.kind === OrderKind.Hold);
        if (def.healer) this.healNearby(e);
        e.vx = e.vy = 0;
        break;
      case OrderKind.Move:
        this.stepMove(e, def, false);
        break;
      case OrderKind.AttackMove:
        // Engage anything in sight — and always answer whatever's shooting us
        // (so the march isn't kited by ranged units out past our scan).
        if (this.tickCount % 3 === e.id % 3) {
          const enemy = this.retaliationTarget(e) ??
            this.findEnemyInRange(e, Math.max(e.visionRange, e.range + 60));
          if (enemy) {
            const am = e.order;
            e.order = { kind: OrderKind.Attack, tx: enemy.x, ty: enemy.y, target: enemy.id, queue: [{ ...am }] };
            e.path = null;
            break;
          }
        }
        this.stepMove(e, def, false);
        break;
      case OrderKind.Attack:
        this.stepAttack(e, def);
        break;
      case OrderKind.Gather:
        this.stepGather(e, def);
        break;
      case OrderKind.Return:
        this.stepReturn(e, def);
        break;
      case OrderKind.Build:
      case OrderKind.Repair:
        this.stepBuildRepair(e, def);
        break;
    }
  }

  private finishOrder(e: Entity) {
    const q = e.order.queue;
    if (q && q.length > 0) {
      const next = q.shift()!;
      next.queue = q;
      e.order = next;
    } else {
      e.order = { kind: OrderKind.Idle, tx: 0, ty: 0, target: -1 };
    }
    e.path = null;
    e.pathIndex = 0;
    e.vx = e.vy = 0;
  }

  /** Move toward order.tx/ty using flow field (groups) or A* path (singles). */
  private stepMove(e: Entity, def: UnitDef, ignoreArrival: boolean): boolean {
    const flow: FlowField | undefined = (e.order as any).flow;
    const dx = e.order.tx - e.x;
    const dy = e.order.ty - e.y;
    const d = Math.hypot(dx, dy);

    // Arrival check: flow-field groups stop when near goal or crowded by
    // arrived allies; singles when close. Formation ("slot") moves settle on
    // their own slot, only honouring the shared goal once they're close to it.
    const isSlot = !!(e.order as any).slot;
    const arriveDist = flow ? (isSlot ? 15 : 26) : 10;
    const reachedGoal = !!flow && flow.reached(e.x, e.y);
    if (d < arriveDist || (reachedGoal && (!isSlot || d < 55))) {
      // If this was a "walk to building then garrison" move, complete it.
      if (e.order.target !== -1) {
        const b = this.byId.get(e.order.target);
        if (b && b.alive && b.kind === Kind.Building && b.team === e.team) {
          const cap = BUILDINGS[b.type]?.garrisonCap ?? 0;
          if (b.garrison.length < cap && dist(e.x, e.y, b.x, b.y) < b.radius + 70) {
            e.alive = false;
            b.garrison.push(e.id);
            return true;
          }
        }
      }
      this.finishOrder(e);
      return true;
    }

    let desX = 0;
    let desY = 0;
    if (flow) {
      const [fx, fy] = flow.sample(e.x, e.y);
      if (fx === 0 && fy === 0) {
        desX = dx / (d || 1);
        desY = dy / (d || 1);
      } else {
        desX = fx;
        desY = fy;
      }
    } else {
      // A* path following.
      e.repathCooldown -= SIM_DT;
      if (!e.path && e.repathCooldown <= 0) {
        e.path = findPath(this.grid, e.x, e.y, e.order.tx, e.order.ty);
        e.pathIndex = 0;
        e.repathCooldown = 0.8;
        if (!e.path) {
          this.finishOrder(e);
          return true;
        }
      }
      if (e.path) {
        // advance waypoint
        while (
          e.pathIndex < e.path.length &&
          dist(e.x, e.y, e.path[e.pathIndex], e.path[e.pathIndex + 1]) < 14
        ) {
          e.pathIndex += 2;
        }
        if (e.pathIndex >= e.path.length) {
          this.finishOrder(e);
          return true;
        }
        const wx = e.path[e.pathIndex];
        const wy = e.path[e.pathIndex + 1];
        const wd = dist(e.x, e.y, wx, wy) || 1;
        desX = (wx - e.x) / wd;
        desY = (wy - e.y) / wd;
      }
    }

    const moveSpeed = this.effectiveSpeed(e);
    const neighbors = this.spatial.query(e.x, e.y, 40) as Entity[];
    let [sx, sy] = applySeparation(e, neighbors, desX * moveSpeed, desY * moveSpeed);
    const sl = Math.hypot(sx, sy) || 1;
    const spd = Math.min(moveSpeed, sl);
    e.vx = (sx / sl) * spd;
    e.vy = (sy / sl) * spd;

    const nx = e.x + e.vx * SIM_DT;
    const ny = e.y + e.vy * SIM_DT;
    // Don't walk into blocked cells (slide along).
    if (!this.grid.isBlockedWorld(nx, ny)) {
      e.x = nx;
      e.y = ny;
    } else if (!this.grid.isBlockedWorld(nx, e.y)) {
      e.x = nx;
    } else if (!this.grid.isBlockedWorld(e.x, ny)) {
      e.y = ny;
    } else if (!flow) {
      e.path = null; // wedged against a building corner — replan immediately
      e.repathCooldown = 0;
    }
    if (Math.hypot(e.vx, e.vy) > 1) e.facing = Math.atan2(e.vy, e.vx);
    return false;
  }

  private stepAttack(e: Entity, def: UnitDef) {
    const target = this.byId.get(e.order.target);
    if (!target || !target.alive || target.hp <= 0) {
      this.finishOrder(e);
      return;
    }
    e.order.tx = target.x;
    e.order.ty = target.y;
    const contact = e.radius + target.radius + (def.ranged ? e.range : 6);
    const d = dist(e.x, e.y, target.x, target.y);
    if (d <= contact) {
      e.vx = e.vy = 0;
      e.facing = Math.atan2(target.y - e.y, target.x - e.x);
      if (e.attackCooldown <= 0 && def.attack > 0) {
        e.attackCooldown = this.effectiveInterval(e);
        if (def.ranged) {
          this.fireProjectile(e, target, def);
          this.emit(def.id === "catapult" ? "siege" : "bow", e.x, e.y, e.team);
        } else {
          this.dealDamage(e.team, target, this.effectiveAttack(e, def, target), def.id, e.id);
          this.emit("sword", target.x, target.y, e.team);
        }
      }
    } else {
      this.stepMove(e, def, true);
      // stepMove may finish the order on arrival; re-target next tick.
      if (e.order.kind === OrderKind.Idle) {
        e.order = { kind: OrderKind.Attack, tx: target.x, ty: target.y, target: target.id };
      }
    }
  }

  /** Is villager `v` currently the worker bound to `farm` (gathering it or returning from it)? */
  private worksFarm(v: Entity, farm: Entity): boolean {
    if (v.order.target === farm.id) return true;
    if (v.order.kind === OrderKind.Return && (v.order.queue ?? []).some((o) => o.target === farm.id)) return true;
    return false;
  }

  /** A villager bumped off a taken farm: send it to the nearest free farm, else any resource, else idle. */
  private reassignFarmer(e: Entity, fromFarm: Entity) {
    let bestFarm: Entity | null = null;
    let bestFd = Infinity;
    for (const f of this.entities) {
      if (!f.alive || f.kind !== Kind.Building || f.type !== "farm" || f.team !== e.team) continue;
      if (f.id === fromFarm.id || f.buildState !== BuildState.Done) continue;
      const owner = this.byId.get(f.farmWorker);
      if (owner && owner.alive && owner.id !== e.id && this.worksFarm(owner, f)) continue;
      const d = dist2(e.x, e.y, f.x, f.y);
      if (d < bestFd) { bestFd = d; bestFarm = f; }
    }
    if (bestFarm) {
      bestFarm.farmWorker = e.id;
      e.order = { kind: OrderKind.Gather, tx: bestFarm.x, ty: bestFarm.y, target: bestFarm.id };
      e.path = null;
      return;
    }
    // No free farm — fall back to the nearest natural resource.
    let bestR: Entity | null = null;
    let bestRd = Infinity;
    for (const r of this.entities) {
      if (!r.alive || r.kind !== Kind.Resource || r.amount <= 0) continue;
      const d = dist2(e.x, e.y, r.x, r.y);
      if (d < bestRd) { bestRd = d; bestR = r; }
    }
    if (bestR) {
      e.order = { kind: OrderKind.Gather, tx: bestR.x, ty: bestR.y, target: bestR.id };
      e.path = null;
    } else {
      this.finishOrder(e);
    }
  }

  private stepGather(e: Entity, def: UnitDef) {
    const node = this.byId.get(e.order.target);
    if (!node || !node.alive || (node.kind === Kind.Resource && node.amount <= 0)) {
      // Find another node of the same kind nearby, else go idle.
      const nextNode = node ? this.findNearbyResource(node.x, node.y, node.type, 200) : null;
      if (nextNode) {
        e.order = { kind: OrderKind.Gather, tx: nextNode.x, ty: nextNode.y, target: nextNode.id, queue: e.order.queue };
      } else if (e.carry > 0) {
        this.startReturn(e);
      } else {
        this.finishOrder(e);
      }
      return;
    }
    const isFarm = node.kind === Kind.Building && node.type === "farm";
    if (isFarm && node.buildState !== BuildState.Done) {
      this.finishOrder(e);
      return;
    }
    // One farmer per farm: if another villager already works this farm, this one
    // is bumped to the nearest free farm (or any other resource).
    if (isFarm) {
      const owner = this.byId.get(node.farmWorker);
      const taken = owner && owner.alive && owner.id !== e.id && this.worksFarm(owner, node);
      if (taken) {
        this.reassignFarmer(e, node);
        return;
      }
      node.farmWorker = e.id; // claim it
    }
    const kind = isFarm ? ResourceKind.Food : RES_TYPE_KIND[node.type];
    if (!kind) {
      this.finishOrder(e);
      return;
    }
    const d = dist(e.x, e.y, node.x, node.y);
    // Farms are walkable, so the farmer stands *on* the plot (near its centre) —
    // it reads unmistakably as "this villager is farming". Other nodes block the
    // grid, so reach a tile out to work them from any adjacent open cell.
    // For farms the work point is the plot centre; the reach sits just past the
    // mover's arrival distance so the farmer settles on the plot and harvests
    // (too tight and it parks at the edge without ever gathering).
    const reach = isFarm
      ? TILE * 0.6
      : e.radius + node.radius + TILE * 0.7;
    if (d > reach) {
      e.order.tx = node.x;
      e.order.ty = node.y;
      this.stepMove(e, def, true);
      if (e.order.kind === OrderKind.Idle) {
        e.order = { kind: OrderKind.Gather, tx: node.x, ty: node.y, target: node.id };
      }
      return;
    }
    e.vx = e.vy = 0;
    e.facing = Math.atan2(node.y - e.y, node.x - e.x);
    e.carryKind = kind;
    e.gatherCooldown -= SIM_DT;
    if (e.gatherCooldown <= 0) {
      const gp = this.players[e.team];
      let econ = gp?.econMult ?? 1;
      if (gp) {
        if (gp.upgrades.has("wheelbarrow")) econ *= 1 + UPGRADES.wheelbarrow.amount;
        if (gp.upgrades.has("hand_cart")) econ *= 1 + UPGRADES.hand_cart.amount;
        // Resource-specific eco upgrades (Horse Collar / Bow Saw / Gold Mining).
        for (const upId of gp.upgrades) {
          const up = UPGRADES[upId];
          if (up?.kind === "econ" && up.resource === kind) econ *= 1 + up.amount;
        }
        econ *= gp.gatherMultC; // commander passive
        if (kind === ResourceKind.Food) econ *= gp.boon.foodGatherMult; // Bountiful Fields
      }
      // Rally banner boosts gathering for villagers standing in its radius.
      const banner = this.bannerAt(e.team, e.x, e.y);
      if (banner) econ *= banner.power.villagerGatherMult;
      e.gatherCooldown = (GATHER_TICK / econ) * (isFarm ? FARM_TICK_MULT : 1);
      const cap = VILLAGER_CARRY_CAP * (gp?.boon.villCarryMult ?? 1); // Wayfarers
      const take = Math.min(GATHER_RATE * GATHER_TICK * 10, node.amount); // chunked
      const got = Math.min(take, cap - e.carry, 1.2);
      e.carry += got;
      if (!isFarm) {
        node.amount -= got;
        if (node.amount <= 0) {
          node.alive = false;
          this.grid.stampFootprint(node.x, node.y, 1, false);
        }
      }
      if (e.carry >= cap) this.startReturn(e);
    }
  }

  private startReturn(e: Entity) {
    const drop = this.findNearestDropoff(e.team, e.x, e.y, e.carryKind!);
    if (!drop) {
      this.finishOrder(e);
      return;
    }
    // Remember the gather order so we resume after depositing.
    const back: Order = { ...e.order, queue: undefined };
    e.order = { kind: OrderKind.Return, tx: drop.x, ty: drop.y, target: drop.id, queue: [back, ...(e.order.queue ?? [])] };
    e.path = null;
  }

  private stepReturn(e: Entity, def: UnitDef) {
    const drop = this.byId.get(e.order.target);
    if (!drop || !drop.alive) {
      const nd = this.findNearestDropoff(e.team, e.x, e.y, e.carryKind ?? ResourceKind.Food);
      if (!nd) {
        this.finishOrder(e);
        return;
      }
      e.order.target = nd.id;
      e.order.tx = nd.x;
      e.order.ty = nd.y;
      return;
    }
    const d = dist(e.x, e.y, drop.x, drop.y);
    if (d <= e.radius + drop.radius + 10) {
      const p = this.players[e.team];
      if (p && e.carryKind) {
        p.resources[e.carryKind] += Math.round(e.carry);
        p.stats.gathered += Math.round(e.carry);
        this.emit("deposit", drop.x, drop.y, e.team, e.carryKind);
      }
      e.carry = 0;
      this.finishOrder(e); // resumes the queued Gather order
    } else {
      this.stepMove(e, def, true);
      if (e.order.kind === OrderKind.Idle) {
        // re-issue (stepMove finished early)
        const nd = this.byId.get(drop.id);
        if (nd) e.order = { kind: OrderKind.Return, tx: nd.x, ty: nd.y, target: nd.id };
      }
    }
  }

  private stepBuildRepair(e: Entity, def: UnitDef) {
    const b = this.byId.get(e.order.target);
    if (!b || !b.alive || b.kind !== Kind.Building) {
      this.finishOrder(e);
      return;
    }
    const bdef = BUILDINGS[b.type];
    if (e.order.kind === OrderKind.Repair && b.hp >= b.maxHp) {
      this.finishOrder(e);
      return;
    }
    const d = dist(e.x, e.y, b.x, b.y);
    if (d > e.radius + b.radius + 12) {
      this.stepMove(e, def, true);
      if (e.order.kind === OrderKind.Idle) {
        e.order = { kind: b.buildState === BuildState.Done ? OrderKind.Repair : OrderKind.Build, tx: b.x, ty: b.y, target: b.id };
      }
      return;
    }
    e.vx = e.vy = 0;
    e.facing = Math.atan2(b.y - e.y, b.x - e.x);
    e.animPhase += SIM_DT * 2;
    if (b.buildState !== BuildState.Done) {
      const pp = this.players[e.team];
      const mult = (pp?.econMult ?? 1) * (pp?.buildMult ?? 1); // commander build speed
      b.buildProgress += (SIM_DT / bdef.buildTime) * mult;
      b.hp = Math.min(b.maxHp, b.hp + (b.maxHp * 0.92 * SIM_DT * mult) / bdef.buildTime);
      b.buildState = BuildState.UnderConstruction;
      if (this.tickCount % 10 === 0) this.emit("build", b.x, b.y, e.team);
      if (b.buildProgress >= 1) {
        b.buildState = BuildState.Done;
        b.buildProgress = 1;
        b.hp = b.maxHp;
        this.onBuildingCompleted(b, bdef);
        this.finishOrder(e);
      }
    } else {
      b.hp = Math.min(b.maxHp, b.hp + REPAIR_RATE * SIM_DT);
      if (b.hp >= b.maxHp) this.finishOrder(e);
    }
  }

  /**
   * Whatever just hit us — so units fight back instead of being kited or
   * marching obliviously while taking fire. Leashed so they don't chase forever.
   */
  private retaliationTarget(e: Entity): Entity | null {
    if (e.lastAttackerId < 0 || this.time - e.lastDamageTime > 3) return null;
    const a = this.byId.get(e.lastAttackerId);
    if (!a || !a.alive || a.kind !== Kind.Unit || !this.areHostile(e.team, a.team)) return null;
    const leash = e.visionRange * 1.6;
    if (dist2(e.x, e.y, a.x, a.y) > leash * leash) return null;
    return a;
  }

  private autoAcquire(e: Entity, def: UnitDef, hold: boolean) {
    if (def.attack <= 0) return;
    if (this.tickCount % 5 !== e.id % 5) return;
    // Idle units always answer an attacker; hold units only scan their post.
    let enemy = hold ? null : this.retaliationTarget(e);
    if (!enemy) {
      const scanRange = hold ? Math.max(e.range + 20, 60) : e.visionRange * 0.85;
      enemy = this.findEnemyInRange(e, scanRange);
    }
    if (enemy) {
      const home: Order | null = hold ? null : { kind: OrderKind.Move, tx: e.x, ty: e.y, target: -1 };
      e.order = {
        kind: OrderKind.Attack,
        tx: enemy.x,
        ty: enemy.y,
        target: enemy.id,
        queue: home ? [home] : undefined,
      };
    }
  }

  private healNearby(e: Entity) {
    if (this.tickCount % 10 !== e.id % 10) return;
    const near = this.spatial.query(e.x, e.y, 90) as Entity[];
    let best: Entity | null = null;
    let bestFrac = 1;
    for (const n of near) {
      if (!n.alive || n.team !== e.team || n.kind !== Kind.Unit || n === e) continue;
      const frac = n.hp / n.maxHp;
      if (frac < bestFrac) {
        bestFrac = frac;
        best = n;
      }
    }
    if (best && bestFrac < 1) {
      best.hp = Math.min(best.maxHp, best.hp + 4);
    }
  }

  private findEnemyInRange(e: Entity, range: number): Entity | null {
    const near = this.spatial.query(e.x, e.y, range) as Entity[];
    let best: Entity | null = null;
    let bestD = Infinity;
    for (const n of near) {
      if (!n.alive || n.hp <= 0) continue;
      if (!this.areHostile(e.team, n.team)) continue;
      if (n.kind !== Kind.Unit && n.kind !== Kind.Building) continue;
      // Prefer units over buildings slightly.
      const d = dist2(e.x, e.y, n.x, n.y) * (n.kind === Kind.Building ? 1.8 : 1);
      if (d < bestD) {
        bestD = d;
        best = n;
      }
    }
    return best;
  }

  private findNearbyResource(x: number, y: number, type: string, radius: number): Entity | null {
    let best: Entity | null = null;
    let bestD = Infinity;
    for (const e of this.entities) {
      if (!e.alive) continue;
      const isFarmFood = type === "farm" && e.type === "farm" && e.kind === Kind.Building;
      if (e.type !== type && !isFarmFood) continue;
      if (e.kind === Kind.Resource && e.amount <= 0) continue;
      const d = dist2(x, y, e.x, e.y);
      if (d < radius * radius && d < bestD) {
        bestD = d;
        best = e;
      }
    }
    return best;
  }

  findNearestDropoff(team: Team, x: number, y: number, kind: ResourceKind): Entity | null {
    let best: Entity | null = null;
    let bestD = Infinity;
    for (const e of this.entities) {
      if (!e.alive || e.team !== team || e.kind !== Kind.Building || e.buildState !== BuildState.Done) continue;
      const def = BUILDINGS[e.type];
      if (!def.isDropoff || !def.dropoffKinds.includes(kind)) continue;
      const d = dist2(x, y, e.x, e.y);
      if (d < bestD) {
        bestD = d;
        best = e;
      }
    }
    return best;
  }

  // Buildings ----------------------------------------------------------------

  private tickBuilding(e: Entity) {
    if (e.buildState !== BuildState.Done) return;
    const def = BUILDINGS[e.type];

    // Gates: open for the owner when safe, bar shut when the enemy is near.
    if (e.type === "gate" && this.tickCount % 5 === e.id % 5) {
      this.updateGate(e);
    }

    // Production queue.
    if (e.productionQueue.length > 0) {
      const p = this.players[e.team];
      const speed = p?.econMult ?? 1;
      e.productionTime -= SIM_DT * speed;
      if (e.productionTime <= 0) {
        const item = e.productionQueue.shift()!;
        this.completeProduction(e, item);
        if (e.productionQueue.length > 0) {
          const next = e.productionQueue[0];
          const trainMult = next.startsWith("u:") ? (p?.boon.trainSpeedMult ?? 1) : 1; // Quartermaster
          e.productionTime = this.itemTime(next) * trainMult;
        }
      }
    }

    // Defensive fire (towers, TC, castle).
    if (def.attack > 0 && e.attackCooldown <= 0) {
      const enemy = this.findEnemyInRange(e, def.range);
      if (enemy) {
        e.attackCooldown = def.attackInterval;
        const garrisonBonus = e.garrison.length * 2;
        this.fireProjectileRaw(e, enemy, def.attack + garrisonBonus, "tower");
        this.emit("bow", e.x, e.y, e.team);
      }
    }
  }

  private updateGate(e: Entity) {
    let open: boolean;
    if (e.gateForce === 1) {
      open = true;
    } else if (e.gateForce === 2) {
      open = false;
    } else {
      const near = this.spatial.query(e.x, e.y, 96) as Entity[];
      let friendly = false;
      let enemy = false;
      for (const n of near) {
        if (!n.alive || n.kind !== Kind.Unit) continue;
        if (n.team === e.team && dist(n.x, n.y, e.x, e.y) < 52) friendly = true;
        else if (this.areHostile(e.team, n.team)) enemy = true;
      }
      open = friendly && !enemy;
    }
    if (open !== e.gateOpen) {
      e.gateOpen = open;
      // Open => clear the cell so units can pass; shut => block it again.
      this.grid.stampFootprint(e.x, e.y, 1, !open);
    }
  }

  /** Cycle a gate between forced-open and forced-shut (player override). */
  toggleGate(id: EntityId) {
    const e = this.byId.get(id);
    if (!e || !e.alive || e.type !== "gate") return;
    e.gateForce = e.gateOpen ? 2 : 1;
    this.updateGate(e);
  }

  itemTime(item: string): number {
    if (item.startsWith("u:")) return UNITS[item.slice(2)]?.buildTime ?? 10;
    if (item.startsWith("t:")) return UPGRADES[item.slice(2)]?.time ?? 20;
    if (item === "a:age") {
      return 30; // refined per-age below when queued first
    }
    return 10;
  }

  private completeProduction(b: Entity, item: string) {
    const p = this.players[b.team];
    if (!p) return;
    if (item.startsWith("u:")) {
      const type = item.slice(2);
      const def = UNITS[type];
      if (p.popUsed + def.pop > p.popCap) {
        // No room: refund and bail.
        p.resources.food += def.cost.food;
        p.resources.wood += def.cost.wood;
        p.resources.gold += def.cost.gold;
        return;
      }
      const [sx, sy] = this.grid.nearestOpenWorld(b.x, b.y + b.radius + 20);
      const u = this.spawnUnit(b.team, type, sx, sy);
      if (def.hero) {
        p.heroState = "alive";
        this.applyHeroLevel(u, p.heroLevel);
      }
      this.emit("spawn", sx, sy, b.team, type);
      if (b.rallyX >= 0) {
        const rallyTarget = this.entityAt(b.rallyX, b.rallyY);
        if (rallyTarget && rallyTarget.kind === Kind.Resource && UNITS[type].canGather) {
          this.issueGather([u.id], rallyTarget.id);
        } else {
          this.issueMove([u.id], b.rallyX, b.rallyY);
        }
      }
    } else if (item.startsWith("t:")) {
      const upId = item.slice(2);
      p.upgrades.add(upId);
      // HP upgrades are baked at spawn — refresh the standing army so it benefits.
      if (UPGRADES[upId]?.kind === "hp") this.refreshTeamUnits(b.team);
      this.emit("complete", b.x, b.y, b.team, item);
    } else if (item === "a:age") {
      p.age = Math.min(MAX_AGE, p.age + 1);
      this.recomputeBoons(b.team); // unlock this age's boon and buff the army
      this.emit("age", b.x, b.y, b.team, String(p.age));
    }
  }

  // Projectiles --------------------------------------------------------------

  private fireProjectile(from: Entity, target: Entity, def: UnitDef) {
    const dmg = this.effectiveAttack(from, def, target);
    this.fireProjectileRaw(from, target, dmg, def.id, def.aoeRadius ?? 0);
  }

  private fireProjectileRaw(from: Entity, target: Entity, dmg: number, sourceType: string, aoe = 0) {
    const e = makeEntity();
    e.kind = Kind.Projectile;
    e.team = from.team;
    e.type = sourceType === "catapult" ? "rock" : "arrow";
    e.x = from.x;
    e.y = from.y;
    e.projFromX = from.x;
    e.projFromY = from.y;
    e.projTargetId = target.id;
    e.projDamage = dmg;
    e.projSourceTeam = from.team;
    e.projSourceId = from.kind === Kind.Unit ? from.id : -1;
    e.projArmorClassBonusFrom = sourceType;
    e.radius = e.type === "rock" ? 5 : 2;
    const speed = e.type === "rock" ? PROJECTILE_SPEED * 0.55 : PROJECTILE_SPEED;
    e.projSpeed = speed;
    e.projDuration = Math.max(0.12, dist(from.x, from.y, target.x, target.y) / speed);
    e.projElapsed = 0;
    e.amount = aoe; // reuse: aoe radius
    this.entities.push(e);
    this.byId.set(e.id, e);
  }

  private tickProjectile(e: Entity) {
    e.projElapsed += SIM_DT;
    const target = this.byId.get(e.projTargetId);
    const t = Math.min(1, e.projElapsed / e.projDuration);
    // Track target's current position (slight homing keeps hits readable).
    const tx = target && target.alive ? target.x : e.order.tx || e.x;
    const ty = target && target.alive ? target.y : e.order.ty || e.y;
    e.order.tx = tx;
    e.order.ty = ty;
    e.x = e.projFromX + (tx - e.projFromX) * t;
    e.y = e.projFromY + (ty - e.projFromY) * t;
    e.facing = Math.atan2(ty - e.projFromY, tx - e.projFromX);
    if (t >= 1) {
      e.alive = false;
      const aoe = e.amount;
      if (aoe > 0) {
        const near = this.spatial.query(e.x, e.y, aoe) as Entity[];
        for (const n of near) {
          if (!n.alive || !this.areHostile(e.projSourceTeam, n.team)) continue;
          if (n.kind !== Kind.Unit && n.kind !== Kind.Building) continue;
          this.dealDamage(e.projSourceTeam, n, e.projDamage, e.projArmorClassBonusFrom, e.projSourceId, true);
        }
        this.emit("siege", e.x, e.y, e.projSourceTeam);
      } else if (target && target.alive) {
        this.dealDamage(e.projSourceTeam, target, e.projDamage, e.projArmorClassBonusFrom, e.projSourceId, true);
        this.emit("arrowHit", e.x, e.y, e.projSourceTeam);
      }
    }
  }

  // Damage -------------------------------------------------------------------

  /** Attack value including tech/age bonuses and RPS bonus vs the target. */
  /** Movement speed including ability buffs (Charge / Brace) and Caltrops slow. */
  private effectiveSpeed(e: Entity): number {
    let s = e.speed;
    const p = this.players[e.team];
    if (p) {
      for (const upId of p.upgrades) {
        const up = UPGRADES[upId];
        if (up?.kind === "speed" && (up.appliesTo.includes(e.armorClass) || (up.appliesToUnits?.includes(e.type) ?? false))) s += up.amount;
      }
    }
    if (e.abilityActive > 0) {
      const ab = ABILITIES[e.type];
      if (ab?.speedMult) s *= ab.speedMult;
    }
    if (e.slowTimer > 0) s *= SLOW_MULT;
    return s;
  }

  /** Seconds between shots, shortened by Volley while active. */
  private effectiveInterval(e: Entity): number {
    if (e.abilityActive > 0) {
      const ab = ABILITIES[e.type];
      if (ab?.atkIntervalMult) return e.attackInterval * ab.atkIntervalMult;
    }
    return e.attackInterval;
  }

  private effectiveAttack(e: Entity, def: UnitDef, target: Entity): number {
    const p = this.players[e.team];
    let atk = e.attack;
    let bonus = def.bonus[target.armorClass] ?? 0;
    if (p) {
      atk += AGE_ATTACK_BONUS[p.age] ?? 0;
      for (const upId of p.upgrades) {
        const up = UPGRADES[upId];
        if (!up) continue;
        const hits = up.appliesTo.includes(def.armorClass) || (up.appliesToUnits?.includes(def.id) ?? false);
        if (!hits) continue;
        if (up.kind === "attack") atk += up.amount;
        else if (up.kind === "bonus" && up.bonusVs === target.armorClass) bonus += up.amount;
      }
    }
    // Active ability buffs (Charge damage, Brace's anti-cavalry bite, …).
    const ab = e.abilityActive > 0 ? ABILITIES[e.type] : undefined;
    if (ab) {
      if (ab.attackMult) atk *= ab.attackMult;
      if (ab.bonusVs) bonus += ab.bonusVs[target.armorClass] ?? 0;
    }
    // War Cry rally from a nearby ally.
    if (e.rallyTimer > 0) atk *= RALLY_ATK_MULT;
    return atk + bonus;
  }

  private effectiveArmor(target: Entity, ranged = false): number {
    const p = this.players[target.team];
    // Ranged hits bite pierce armor; melee bites melee armor (same for most units).
    let armor = ranged ? target.pierceArmor : target.armor;
    if (p && target.kind === Kind.Unit) {
      armor += AGE_ARMOR_BONUS[p.age] ?? 0;
      const def = UNITS[target.type];
      for (const upId of p.upgrades) {
        const up = UPGRADES[upId];
        if (up && up.kind === "armor" && def && (up.appliesTo.includes(def.armorClass) || (up.appliesToUnits?.includes(target.type) ?? false))) armor += up.amount;
      }
    }
    // Shield Wall / Brace add armour while active.
    if (target.abilityActive > 0) {
      const ab = ABILITIES[target.type];
      if (ab?.armorBonus) armor += ab.armorBonus;
    }
    // Rally banner toughens soldiers standing under it.
    if (target.kind === Kind.Unit && !UNITS[target.type]?.canGather) {
      const banner = this.bannerAt(target.team, target.x, target.y);
      if (banner) armor += banner.power.militaryArmor;
    }
    return armor;
  }

  dealDamage(fromTeam: Team, target: Entity, rawDamage: number, sourceType: string, fromId: EntityId = -1, ranged = false) {
    if (!target.alive) return;
    // Reaver boon: extra damage to villagers and buildings (eco denial / sieging).
    const attacker = this.players[fromTeam];
    let raw = rawDamage;
    if (attacker && attacker.boon.raiderMult > 1 && (target.kind === Kind.Building || target.type === "villager")) {
      raw *= attacker.boon.raiderMult;
    }
    const dmg = Math.max(1, raw - this.effectiveArmor(target, ranged));
    target.hp -= dmg;
    target.hitFlash = 1;
    target.lastDamageTime = this.time;
    if (fromId >= 0) target.lastAttackerId = fromId;

    // Floating damage numbers only for fights the human player is part of —
    // keeps the event stream (and the screen) from drowning in big melees.
    if (fromTeam === Team.Player || target.team === Team.Player) {
      this.emit("hit", target.x, target.y - target.radius, fromTeam, String(Math.round(dmg)));
    }

    // Under-attack alert (rate limited).
    const p = this.players[target.team];
    if (p && this.time - this.lastAlertTime[target.team] > 12) {
      this.lastAlertTime[target.team] = this.time;
      this.emit("underattack", target.x, target.y, target.team);
    }

    // Villagers fight back if idle and attacked in melee… keep simple: no.

    if (target.hp <= 0) {
      this.creditVeterancy(fromId);
      this.kill(target, fromTeam);
    }
  }

  private kill(e: Entity, byTeam: Team) {
    e.alive = false;
    e.hp = 0;
    const killer = this.players[byTeam];
    const owner = this.players[e.team];
    if (e.kind === Kind.Unit) {
      const def = UNITS[e.type];
      if (owner) {
        owner.popUsed = Math.max(0, owner.popUsed - (def?.pop ?? 1));
        owner.stats.unitsLost++;
      }
      if (killer) killer.stats.unitsKilled++;
      this.creditHeroKill(byTeam, e.x, e.y);
      // The Champion falls — but rises again at the Town Center after a while.
      if (def?.hero && owner) {
        owner.heroState = "respawning";
        owner.heroRespawnTimer = HERO_RESPAWN_SEC;
        owner.heroLevel = e.heroLevel;
      }
      this.emit("death", e.x, e.y, e.team, e.type);
    } else if (e.kind === Kind.Building) {
      const def = BUILDINGS[e.type];
      // Walkable buildings never stamped the grid; don't clear cells they may
      // share with adjacent obstacles.
      if (!BUILDING_WALKABLE.has(e.type)) this.grid.stampFootprint(e.x, e.y, def.tiles, false);
      if (owner) {
        owner.popCap = Math.max(0, owner.popCap - e.popProvided);
        owner.stats.buildingsLost++;
      }
      if (killer) killer.stats.buildingsRazed++;
      // Garrisoned units die with the building.
      for (const id of e.garrison) {
        const g = this.byId.get(id);
        if (g) {
          g.alive = false;
          if (owner) owner.popUsed = Math.max(0, owner.popUsed - (UNITS[g.type]?.pop ?? 1));
        }
      }
      this.emit("collapse", e.x, e.y, e.team, e.type);
    }
  }

  // Vision / fog ---------------------------------------------------------------

  private recomputeVision() {
    for (let t = 0; t < this.numTeams; t++) {
      const f = this.fog[t];
      // visible -> explored
      for (let i = 0; i < f.length; i++) {
        if (f[i] === FOG_VISIBLE) f[i] = FOG_EXPLORED;
      }
    }
    // Night shrinks sight by up to 40%. Buildings (lit by braziers and watch-
    // fires) keep most of theirs, so your base never goes fully blind.
    const phase = dayPhase(this.time);
    const nightMult = visionMult(phase);
    const buildingMult = Math.max(0.82, nightMult);
    for (const e of this.entities) {
      if (!e.alive || e.team === Team.Neutral) continue;
      if (e.kind !== Kind.Unit && e.kind !== Kind.Building) continue;
      const f = this.fog[e.team];
      const cx = this.grid.worldToCellX(e.x);
      const cy = this.grid.worldToCellY(e.y);
      // Watchfires burn through the dark — full sight regardless of the hour.
      const mult = e.type === "watchfire" ? 1 : e.kind === Kind.Building ? buildingMult : nightMult;
      const r = Math.max(2, Math.ceil((e.visionRange * mult) / TILE));
      const r2 = r * r;
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (dx * dx + dy * dy > r2) continue;
          const nx = cx + dx;
          const ny = cy + dy;
          if (nx < 0 || ny < 0 || nx >= this.fogCols || ny >= this.fogRows) continue;
          f[ny * this.fogCols + nx] = FOG_VISIBLE;
        }
      }
    }

    // Shared sight: allies see what each other sees (take the brighter state).
    for (let a = 0; a < this.numTeams; a++) {
      for (let b = a + 1; b < this.numTeams; b++) {
        if (this.alliances[a] !== this.alliances[b]) continue;
        const fa = this.fog[a];
        const fb = this.fog[b];
        for (let i = 0; i < fa.length; i++) {
          const m = fa[i] > fb[i] ? fa[i] : fb[i];
          fa[i] = m;
          fb[i] = m;
        }
      }
    }
  }

  fogAt(team: Team, wx: number, wy: number): number {
    if (this.revealAll) return FOG_VISIBLE; // spectator sees the whole field
    const cx = this.grid.worldToCellX(wx);
    const cy = this.grid.worldToCellY(wy);
    if (cx < 0 || cy < 0 || cx >= this.fogCols || cy >= this.fogRows) return FOG_UNSEEN;
    return this.fog[team][cy * this.fogCols + cx];
  }

  /** Is this entity visible to `team` right now? */
  visibleTo(team: Team, e: Entity): boolean {
    if (this.revealAll || e.team === team) return true;
    const f = this.fogAt(team, e.x, e.y);
    if (f === FOG_VISIBLE) return true;
    // Explored buildings remain visible as "last known" — renderer handles ghosting.
    if (e.kind === Kind.Building && f === FOG_EXPLORED) return true;
    return false;
  }

  // Victory ---------------------------------------------------------------------

  private checkVictory() {
    if (this.winner !== null) return;
    // A team is only out when it has NO buildings AND no villager to rebuild
    // one — so a nomad start (villagers, no Town Center) isn't an instant loss,
    // and a razed base with surviving villagers can still make a comeback.
    const buildings = new Array(this.numTeams).fill(0);
    const villagers = new Array(this.numTeams).fill(0);
    for (const e of this.entities) {
      if (!e.alive || e.team >= this.numTeams) continue;
      if (e.kind === Kind.Building) buildings[e.team]++;
      else if (e.kind === Kind.Unit && UNITS[e.type]?.canBuild) villagers[e.team]++;
    }
    const alive: Team[] = [];
    const aliveAlliances = new Set<number>();
    for (let t = 0; t < this.numTeams; t++) {
      if (buildings[t] === 0 && villagers[t] === 0) this.players[t].defeated = true;
      else if (!this.players[t].defeated) {
        alive.push(t as Team);
        aliveAlliances.add(this.alliances[t]);
      }
    }
    // Last alliance standing wins; winner is a surviving member of it.
    if (aliveAlliances.size <= 1) this.winner = alive[0] ?? Team.Neutral;
  }

  // Queries for UI/AI -------------------------------------------------------------

  /**
   * Nearest gatherable node at a point — a resource, or one of `team`'s farms.
   * Uses a slightly generous pick radius so a berry/tree tucked against a
   * drop-off building is still selectable for gathering instead of the building.
   */
  resourceAt(wx: number, wy: number, team?: Team): Entity | null {
    const near = this.spatial.query(wx, wy, 44) as Entity[];
    let best: Entity | null = null;
    let bestD = Infinity;
    for (const e of near) {
      if (!e.alive) continue;
      const isFarm = e.kind === Kind.Building && e.type === "farm" &&
        e.buildState === BuildState.Done && (team === undefined || e.team === team);
      if (e.kind === Kind.Resource) {
        if (e.amount <= 0) continue;
      } else if (!isFarm) {
        continue;
      }
      const r = Math.max(e.radius + 10, 16);
      const d = dist2(wx, wy, e.x, e.y);
      if (d < r * r && d < bestD) {
        bestD = d;
        best = e;
      }
    }
    return best;
  }

  entityAt(wx: number, wy: number, team?: Team): Entity | null {
    const near = this.spatial.query(wx, wy, 48) as Entity[];
    let best: Entity | null = null;
    let bestD = Infinity;
    for (const e of near) {
      if (!e.alive) continue;
      if (team !== undefined && e.team !== team) continue;
      const r = e.kind === Kind.Building ? e.radius : Math.max(e.radius + 6, 12);
      const d = dist2(wx, wy, e.x, e.y);
      if (d < r * r && d < bestD) {
        bestD = d;
        best = e;
      }
    }
    return best;
  }

  entitiesOf(team: Team, kind?: Kind): Entity[] {
    return this.entities.filter(
      (e) => e.alive && e.team === team && (kind === undefined || e.kind === kind),
    );
  }

  countOf(team: Team, type: string): number {
    let n = 0;
    for (const e of this.entities) {
      if (e.alive && e.team === team && e.type === type) n++;
    }
    return n;
  }

  /** Periodically drop long-dead entities to keep iteration fast. */
  private compact() {
    const keep: Entity[] = [];
    for (const e of this.entities) {
      // Garrisoned units are alive=false but must be kept.
      const garrisoned = !e.alive && e.kind === Kind.Unit &&
        this.entities.some((b) => b.alive && b.garrison.includes(e.id));
      if (e.alive || garrisoned) keep.push(e);
      else this.byId.delete(e.id);
    }
    this.entities = keep;
  }
}
