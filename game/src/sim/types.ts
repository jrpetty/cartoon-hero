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
  // Neutral sits above any player slot so player teams can occupy 0..N-1.
  Neutral = 9,
}

/** Highest number of player teams supported in one match. */
export const MAX_TEAMS = 4;

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

  // Projectile
  projTargetId: EntityId;
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

  // Visual helpers (render only; safe to derive)
  animPhase: number;
  hitFlash: number;
  lastDamageTime: number;
  selected: boolean;

  // Variant/meta (player units may carry stat multipliers + a rarity tint)
  variantRarity: number; // 0 = common
  tier: number; // tech/age upgrade tier applied
}
