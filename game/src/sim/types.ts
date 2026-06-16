// Core simulation types: entity kinds, ids, orders and the entity record.
// We use a lightweight entity-record model (not strict ECS) backed by a flat
// array in `world.ts` — cache-friendly enough for our unit counts and far
// simpler to wire than a full component store.

export type EntityId = number;

export const enum Team {
  Player = 0,
  Enemy = 1, // also the primary rival in 1v1
  Team3 = 2,
  Team4 = 3,
  Team5 = 4,
  Team6 = 5,
  Team7 = 6,
  Team8 = 7,
  // Neutral sits above any player slot so player teams can occupy 0..N-1.
  Neutral = 9,
}

/** Highest number of player teams supported in one match. */
export const MAX_TEAMS = 8;

/**
 * Victory rulesets.
 *  - conquest: eliminate every enemy (default).
 *  - survival: a player alliance holds out against escalating waves.
 *  - koth:     hold the central hill for a cumulative time to win.
 *  - regicide: protect your King and slay the enemy's.
 */
export type GameMode = "conquest" | "survival" | "koth" | "regicide";

export const enum Kind {
  Unit = 0,
  Building = 1,
  Resource = 2,
  Projectile = 3,
}

/** Armor classes used for the rock-paper-scissors bonus-damage table. */
export const enum ArmorClass {
  Infantry = "infantry",
  Archer = "archer",
  Cavalry = "cavalry",
  Siege = "siege",
  Building = "building",
  Villager = "villager",
}

export const enum ResourceKind {
  Food = "food",
  Wood = "wood",
  Gold = "gold",
}

export interface ResourceBag {
  food: number;
  wood: number;
  gold: number;
}

export const enum OrderKind {
  Idle = 0,
  Move = 1,
  AttackMove = 2,
  Attack = 3,
  Gather = 4,
  Return = 5,
  Build = 6,
  Repair = 7,
  Hold = 8,
}

export interface Order {
  kind: OrderKind;
  tx: number; // target world position x
  ty: number; // target world position y
  target: EntityId; // target entity for attack/gather/build/repair (-1 none)
  /** Queued follow-up orders (shift-queue). */
  queue?: Order[];
}

export const enum BuildState {
  Done = 0,
  UnderConstruction = 1,
  Foundation = 2,
}

/** A single live thing in the world. Not every field applies to every kind. */
export interface Entity {
  id: EntityId;
  kind: Kind;
  team: Team;
  type: string; // def id, e.g. "villager", "knight", "town_center", "tree"

  alive: boolean;
  x: number;
  y: number;
  /** Position at the start of the current sim tick. The renderer interpolates
   *  between (prevX,prevY) and (x,y) for smooth 60fps motion over the 20Hz sim. */
  prevX: number;
  prevY: number;
  radius: number;
  facing: number; // radians

  hp: number;
  maxHp: number;

  // Movement
  vx: number;
  vy: number;
  speed: number; // max move speed (world units / sec)

  // Combat
  attack: number;
  range: number;
  attackCooldown: number; // seconds remaining until next shot
  attackInterval: number; // seconds between shots
  armor: number;
  pierceArmor: number; // armor vs ranged/projectile hits (melee uses `armor`)
  armorClass: ArmorClass;
  visionRange: number;

  // Orders
  order: Order;
  path: number[] | null; // flattened [x0,y0,x1,y1,...] waypoints in world space
  pathIndex: number;
  repathCooldown: number;

  // Worker / gather
  carry: number;
  carryKind: ResourceKind | null;
  gatherCooldown: number;

  // Resource node
  amount: number; // remaining resources for ResourceKind nodes

  // Building
  buildState: BuildState;
  buildProgress: number; // 0..1
  popProvided: number;
  rallyX: number;
  rallyY: number;
  productionQueue: string[];
  productionTime: number; // seconds remaining on current item
  garrison: EntityId[];
  gateOpen: boolean; // gates only: is the passage currently open
  gateForce: number; // 0 = auto, 1 = forced open, 2 = forced shut
  farmWorker: EntityId; // farms only: the one villager allowed to work it (-1 = free)

  // Projectile
  projTargetId: EntityId;
  projSourceId: EntityId; // unit that fired (for kill credit / veterancy)
  projDamage: number;
  projSpeed: number;
  projSourceTeam: Team;
  projArmorClassBonusFrom: string; // unit type that fired (for bonus lookup)
  projElapsed: number;
  projDuration: number;
  projFromX: number;
  projFromY: number;

  // Active ability (signature self-buff)
  abilityCooldown: number; // seconds until usable again (0 = ready)
  abilityActive: number; // seconds of buff remaining (0 = inactive)
  slowTimer: number; // seconds of enemy-applied slow remaining (Caltrops)
  rallyTimer: number; // seconds of ally-applied attack boost remaining (War Cry)
  heroLevel: number; // hero units only: 0..5, raises stats
  heroKills: number; // hero units only: kills credited toward the next level
  veterancy: number; // combat units: 0..3 rank earned from kills
  vetKills: number; // kills credited toward the next veterancy rank

  // Visual helpers (render only; safe to derive)
  animPhase: number;
  hitFlash: number;
  lastDamageTime: number;
  lastAttackerId: EntityId; // who last hit us (for retaliation / anti-kite)
  selected: boolean;

  // Variant/meta (player units may carry stat multipliers + a rarity tint)
  variantRarity: number; // 0 = common
  tier: number; // tech/age upgrade tier applied
}
