// The skirmish AI brain. Acts only through the same public World command API
// the human player uses. Behavior scales with difficulty: scouting, counter
// composition, expansion, harassment and attack pacing (see difficulty.ts).

import { World } from "../sim/world";
import { BuildState, Entity, Kind, OrderKind, ResourceKind, Team } from "../sim/types";
import { UNITS } from "../content/units";
import { ABILITIES } from "../content/abilities";
import { BUILDINGS } from "../content/buildings";
import { UPGRADES, AGES } from "../content/tech";
import { TILE } from "../content/balance";
import { DifficultyDef } from "./difficulty";
import { RNG } from "../engine/rng";
import { dist } from "../engine/math";
import { FOG_VISIBLE } from "../sim/world";

interface SeenComposition {
  infantry: number;
  archer: number;
  cavalry: number;
  siege: number;
}

type AIStyle = "rush" | "boom" | "turtle" | "balanced";

export class SkirmishAI {
  private timer = 0;
  private rng: RNG;
  private seen: SeenComposition = { infantry: 0, archer: 0, cavalry: 0, siege: 0 };
  private lastWaveTime = 0;
  private lastHarassTime = 0;
  private lastScoutTime = -999;
  private scoutId = -1;
  private wallsPlanned = false;
  private attacking = false;
  private gameTime = 0;
  private seenWalls = 0;
  private lastRetreatTime = -999;
  private savingForAge = false;
  /** Personality — biases army timing, eco focus and turtling. */
  private style: AIStyle = "balanced";

  constructor(
    private world: World,
    private team: Team,
    private diff: DifficultyDef,
  ) {
    this.rng = world.rng.fork(100 + team);
    // Squire stays vanilla; mid tiers get the full personality spread; the top
    // tiers stay aggressive (no turtling) so they keep the pressure on.
    const pools: Record<string, AIStyle[]> = {
      squire: ["balanced"],
      knight: ["rush", "boom", "turtle", "balanced", "balanced"],
      lord: ["rush", "balanced", "balanced", "boom"],
      warlord: ["rush", "rush", "balanced", "balanced", "boom"],
    };
    const styles = pools[diff.id] ?? ["balanced"];
    this.style = styles[this.rng.int(0, styles.length - 1)];
    // First wave shouldn't come absurdly early on slow tiers.
    this.lastWaveTime = -this.cadence * 0.4;
  }

  // Personality-scaled tunables ------------------------------------------------
  private get armySize(): number {
    const m = { rush: 0.62, boom: 1.35, turtle: 1.5, balanced: 1 }[this.style];
    return Math.max(6, Math.round(this.diff.attackArmySize * m));
  }
  private get cadence(): number {
    const m = { rush: 0.58, boom: 1.18, turtle: 1.3, balanced: 1 }[this.style];
    return this.diff.attackEverySec * m;
  }
  private get villagerGoal(): number {
    const m = { rush: 0.82, boom: 1.25, turtle: 1.05, balanced: 1 }[this.style];
    return Math.round(this.diff.villagerTarget * m);
  }
  private get turtles(): boolean {
    return this.style === "turtle";
  }

  /** Rough combat strength of own (mine=true) or hostile units near a point. */
  private strengthNear(x: number, y: number, r: number, mine: boolean): number {
    let s = 0;
    const r2 = r * r;
    for (const e of this.world.entities) {
      if (!e.alive || e.kind !== Kind.Unit) continue;
      if (mine ? e.team !== this.team : !this.isHostile(e.team)) continue;
      if (e.type === "villager") continue;
      const dx = e.x - x;
      const dy = e.y - y;
      if (dx * dx + dy * dy > r2) continue;
      const def = UNITS[e.type];
      s += (def?.attack ?? 4) + e.hp * 0.08;
    }
    return s;
  }

  update(dt: number) {
    this.gameTime += dt;
    this.timer -= dt;
    if (this.timer > 0) return;
    this.timer = this.diff.reactionSec;
    if (this.world.winner !== null) return;
    const p = this.world.player(this.team);
    if (!p || p.defeated) return;

    this.updateIntel();
    this.economy();
    this.military();
  }

  // ------------------------------------------------------------- helpers --

  private myUnits(type?: string): Entity[] {
    return this.world.entities.filter(
      (e) => e.alive && e.team === this.team && e.kind === Kind.Unit && (!type || e.type === type),
    );
  }

  private myBuildings(type?: string, doneOnly = true): Entity[] {
    return this.world.entities.filter(
      (e) =>
        e.alive &&
        e.team === this.team &&
        e.kind === Kind.Building &&
        (!type || e.type === type) &&
        (!doneOnly || e.buildState === BuildState.Done),
    );
  }

  /** A foe is any hostile team (respects 2v2 alliances). */
  private isHostile(t: Team): boolean {
    return this.world.areHostile(this.team, t);
  }

  /** The rival we're focusing: the nearest non-defeated enemy by base distance. */
  private get enemyTeam(): Team {
    const myBase = this.base();
    let best: Team = ((this.team + 1) % this.world.numTeams) as Team;
    let bestD = Infinity;
    for (let t = 0; t < this.world.numTeams; t++) {
      if (!this.isHostile(t as Team)) continue;
      const p = this.world.player(t as Team);
      if (!p || p.defeated) continue;
      const s = this.world.map.starts[t];
      const d = myBase && s ? dist(myBase.x, myBase.y, s.x, s.y) : 0;
      if (d < bestD) {
        bestD = d;
        best = t as Team;
      }
    }
    return best;
  }

  private base(): Entity | null {
    const tcs = this.myBuildings("town_center");
    return tcs[0] ?? this.myBuildings()[0] ?? null;
  }

  /** Cost of the next age the AI is ready to research, or null if not ready. */
  private ageUpCost(p: ReturnType<World["player"]>): { food: number; wood: number; gold: number } | null {
    const vills = this.myUnits("villager").length;
    if (p.age === 0 && this.diff.maxAge >= 1 && this.myBuildings("barracks").length > 0 &&
        vills >= Math.floor(this.villagerGoal * 0.55)) {
      return AGES[1].cost;
    }
    if (p.age === 1 && this.diff.maxAge >= 2 && this.myBuildings("blacksmith").length > 0 &&
        vills >= Math.floor(this.villagerGoal * 0.8)) {
      return AGES[2].cost;
    }
    return null;
  }

  /** Try to place a building somewhere in a ring around (cx, cy). */
  private placeNear(type: string, cx: number, cy: number, minR = 2, maxR = 9): Entity | null {
    for (let attempt = 0; attempt < 24; attempt++) {
      const r = this.rng.range(minR, maxR) * TILE;
      const a = this.rng.range(0, Math.PI * 2);
      const x = cx + Math.cos(a) * r;
      const y = cy + Math.sin(a) * r;
      const placed = this.world.placeBuilding(this.team, type, x, y);
      if (placed) return placed;
    }
    return null;
  }

  /** Send the nearest idle (or gathering) villager to construct `b`. */
  private assignBuilder(b: Entity, count = 1) {
    const villagers = this.myUnits("villager")
      .filter((v) => v.order.kind !== OrderKind.Build)
      .sort((u, v) => dist(u.x, u.y, b.x, b.y) - dist(v.x, v.y, b.x, b.y));
    const picked = villagers.slice(0, count);
    if (picked.length) this.world.issueBuildRepair(picked.map((v) => v.id), b.id);
  }

  private nearestResource(type: string, x: number, y: number): Entity | null {
    let best: Entity | null = null;
    let bestD = Infinity;
    for (const e of this.world.entities) {
      if (!e.alive) continue;
      const isFarm = type === "farm" && e.kind === Kind.Building && e.type === "farm" &&
        e.team === this.team && e.buildState === BuildState.Done;
      if (!isFarm && (e.kind !== Kind.Resource || e.type !== type || e.amount <= 0)) continue;
      const d = dist(x, y, e.x, e.y);
      if (d < bestD) {
        bestD = d;
        best = e;
      }
    }
    return best;
  }

  // --------------------------------------------------------------- intel --

  private updateIntel() {
    // Decay memory, then add what's currently visible to our fog.
    const decay = 0.92;
    this.seen.infantry *= decay;
    this.seen.archer *= decay;
    this.seen.cavalry *= decay;
    this.seen.siege *= decay;
    const now: SeenComposition = { infantry: 0, archer: 0, cavalry: 0, siege: 0 };
    for (const e of this.world.entities) {
      if (!e.alive || !this.isHostile(e.team) || e.kind !== Kind.Unit) continue;
      if (this.world.fogAt(this.team, e.x, e.y) !== FOG_VISIBLE) continue;
      const def = UNITS[e.type];
      if (!def) continue;
      if (def.armorClass === "cavalry") now.cavalry++;
      else if (def.armorClass === "archer") now.archer++;
      else if (def.armorClass === "siege") now.siege++;
      else if (e.type !== "villager") now.infantry++;
    }
    this.seen.infantry = Math.max(this.seen.infantry, now.infantry);
    this.seen.archer = Math.max(this.seen.archer, now.archer);
    this.seen.cavalry = Math.max(this.seen.cavalry, now.cavalry);
    this.seen.siege = Math.max(this.seen.siege, now.siege);

    // Count the enemy's fortifications we've laid eyes on — drives siege.
    let walls = 0;
    for (const e of this.world.entities) {
      if (!e.alive || !this.isHostile(e.team) || e.kind !== Kind.Building) continue;
      if (this.world.fogAt(this.team, e.x, e.y) === 0) continue;
      if (e.type === "stone_wall" || e.type === "gate" || e.type === "palisade" || e.type === "castle") walls++;
    }
    this.seenWalls = walls;
  }

  // ------------------------------------------------------------- economy --

  private economy() {
    const p = this.world.player(this.team);
    const base = this.base();
    if (!base) return;

    // If we're ready to advance an age but can't afford it, stop spending food
    // (villagers + army) so the cost can actually be banked — otherwise the AI
    // dribbles every scrap into units and never reaches the next age.
    const gate = this.ageUpCost(p);
    this.savingForAge = gate !== null && !this.world.canAfford(p.resources, gate);

    // 1. Keep villager production rolling (paused while saving for an age).
    const villagers = this.myUnits("villager");
    for (const tc of this.myBuildings("town_center")) {
      if (!this.savingForAge && villagers.length < this.villagerGoal && tc.productionQueue.length < 2) {
        this.world.trainUnit(this.team, tc.id, "villager");
      }
    }

    // 2. Houses ahead of pop block.
    const housesBuilding = this.myBuildings("house", false).filter(
      (h) => h.buildState !== BuildState.Done,
    ).length;
    if (p.popCap - p.popUsed < 6 && housesBuilding === 0 && p.popCap < 200) {
      const h = this.placeNear("house", base.x, base.y, 3, 8);
      if (h) this.assignBuilder(h);
    }

    // 3. Gather balance.
    this.balanceGatherers(villagers);

    // 4. Drop-off camps near distant clusters.
    this.maybeBuildCamps(base);

    // 5. Farms once berries thin out.
    this.maybeBuildFarms(base, villagers.length);

    // 6. Military/tech buildings + age advances per the build order.
    this.buildOrder(p, base, villagers.length);

    // 7. Blacksmith upgrades when comfortable.
    if (p.age >= 1 && p.resources.gold > 280) {
      const smith = this.myBuildings("blacksmith")[0];
      if (smith && smith.productionQueue.length === 0) {
        for (const id of Object.keys(UPGRADES)) {
          if (!p.upgrades.has(id)) {
            if (this.world.research(this.team, smith.id, id)) break;
          }
        }
      }
    }

    // 8. Expansion town center.
    if (this.diff.expands && p.age >= 2 && this.myBuildings("town_center").length < 2 &&
        p.resources.wood > 500) {
      const start = this.world.map.starts[this.team];
      const c = { x: this.world.worldW / 2, y: this.world.worldH / 2 };
      const ex = start.x + (c.x - start.x) * 0.45;
      const ey = start.y + (c.y - start.y) * 0.45;
      const tc = this.placeNear("town_center", ex, ey, 0, 5);
      if (tc) this.assignBuilder(tc, 3);
    }

    // 8b. Fortify the front with a wall + gate once established.
    this.buildDefenses(base);

    // 9. Castle for late-game defense/elite production.
    if (this.diff.buildsCastle && p.age >= 2 && this.myBuildings("castle").length === 0 &&
        this.world.canAfford(p.resources, BUILDINGS.castle.cost)) {
      const start = this.world.map.starts[this.team];
      const c = { x: this.world.worldW / 2, y: this.world.worldH / 2 };
      const fx = start.x + (c.x - start.x) * 0.3;
      const fy = start.y + (c.y - start.y) * 0.3;
      const castle = this.placeNear("castle", fx, fy, 0, 6);
      if (castle) this.assignBuilder(castle, 3);
    }
  }

  /**
   * Lay a defensive wall line with a central gate across the approach from the
   * base toward the map centre, backed by a watch tower. Built once, when the
   * economy can spare a few builders. Kept short so it screens the front
   * without sealing the AI's own resource lines.
   */
  private buildDefenses(base: Entity) {
    if ((!this.diff.buildsWalls && !this.turtles) || this.wallsPlanned) return;
    const p = this.world.player(this.team);
    if (p.age < 1 || p.resources.wood < 160) return;
    if (this.myUnits("villager").length < 16) return;

    const cx = this.world.worldW / 2;
    const cy = this.world.worldH / 2;
    const dx = cx - base.x;
    const dy = cy - base.y;
    const dl = Math.hypot(dx, dy) || 1;
    const ux = dx / dl;
    const uy = dy / dl;
    const px = -uy;
    const py = ux;
    const front = 4.5 * TILE;
    const fx = base.x + ux * front;
    const fy = base.y + uy * front;

    const builders: number[] = [];
    const span = 3; // segments either side of the gate
    for (let i = -span; i <= span; i++) {
      const wx = fx + px * i * TILE;
      const wy = fy + py * i * TILE;
      const type = i === 0 ? "gate" : "stone_wall";
      const b = this.world.placeBuilding(this.team, type, wx, wy);
      if (b) builders.push(b.id);
    }
    const tower = this.world.placeBuilding(this.team, "watch_tower", fx - ux * TILE, fy - uy * TILE);
    if (tower) builders.push(tower.id);

    // Send a handful of villagers to raise it all (queued), then resume eco.
    if (builders.length > 0) {
      const vills = this.myUnits("villager")
        .filter((v) => v.order.kind !== OrderKind.Build)
        .sort((a, b) => dist(a.x, a.y, fx, fy) - dist(b.x, b.y, fx, fy))
        .slice(0, 4);
      builders.forEach((bid, i) => {
        const v = vills[i % vills.length];
        if (v) this.world.issueBuildRepair([v.id], bid, true);
      });
    }
    this.wallsPlanned = true;
  }

  private gatherTargets(age: number): Record<ResourceKind, number> {
    if (age === 0) return { food: 0.5, wood: 0.4, gold: 0.1 } as Record<ResourceKind, number>;
    if (age === 1) return { food: 0.42, wood: 0.33, gold: 0.25 } as Record<ResourceKind, number>;
    return { food: 0.36, wood: 0.29, gold: 0.35 } as Record<ResourceKind, number>;
  }

  private villagerTask(v: Entity): ResourceKind | "busy" | "idle" {
    if (v.order.kind === OrderKind.Gather || v.order.kind === OrderKind.Return) {
      if (v.carryKind) return v.carryKind;
      const node = this.world.byId.get(v.order.target);
      if (node) {
        if (node.type === "tree") return ResourceKind.Wood;
        if (node.type === "gold_mine") return ResourceKind.Gold;
        return ResourceKind.Food;
      }
      return ResourceKind.Food;
    }
    if (v.order.kind === OrderKind.Build || v.order.kind === OrderKind.Repair) return "busy";
    if (v.order.kind === OrderKind.Idle) return "idle";
    return "busy";
  }

  private balanceGatherers(villagers: Entity[]) {
    const p = this.world.player(this.team);
    const base = this.base();
    if (!base) return;
    const counts: Record<string, number> = { food: 0, wood: 0, gold: 0 };
    const idle: Entity[] = [];
    let working = 0;
    for (const v of villagers) {
      const t = this.villagerTask(v);
      if (t === "idle") idle.push(v);
      else if (t !== "busy") {
        counts[t]++;
        working++;
      }
    }
    const targets = this.gatherTargets(p.age);
    const wantKind = (): ResourceKind => {
      const total = working + 1;
      const deficits: [ResourceKind, number][] = (
        [ResourceKind.Food, ResourceKind.Wood, ResourceKind.Gold] as ResourceKind[]
      ).map((k) => [k, targets[k] - counts[k] / total]);
      deficits.sort((a, b) => b[1] - a[1]);
      return deficits[0][0];
    };

    const NODE_FOR: Record<ResourceKind, string[]> = {
      [ResourceKind.Food]: ["berries", "farm"],
      [ResourceKind.Wood]: ["tree"],
      [ResourceKind.Gold]: ["gold_mine"],
    };

    // Put every idle villager to work; nudge one worker per cycle for balance.
    const toAssign = idle.slice(0, 6);
    if (toAssign.length === 0 && villagers.length > 6) {
      // Rebalance: find the kind with the largest surplus and move one worker.
      const total = Math.max(1, working);
      let surplusKind: ResourceKind | null = null;
      let surplus = 0.08;
      for (const k of [ResourceKind.Food, ResourceKind.Wood, ResourceKind.Gold]) {
        const s = counts[k] / total - targets[k];
        if (s > surplus) {
          surplus = s;
          surplusKind = k;
        }
      }
      if (surplusKind) {
        const v = villagers.find((u) => this.villagerTask(u) === surplusKind);
        if (v) toAssign.push(v);
      }
    }

    for (const v of toAssign) {
      const kind = wantKind();
      let node: Entity | null = null;
      for (const nt of NODE_FOR[kind]) {
        node = this.nearestResource(nt, v.x, v.y);
        if (node) break;
      }
      if (node) {
        this.world.issueGather([v.id], node.id);
        counts[kind]++;
        working++;
      }
    }
  }

  private maybeBuildCamps(base: Entity) {
    const p = this.world.player(this.team);
    if (p.resources.wood < 110) return;
    const camps: [string, string, ResourceKind][] = [
      ["tree", "lumber_camp", ResourceKind.Wood],
      ["gold_mine", "mining_camp", ResourceKind.Gold],
    ];
    for (const [resType, campType, kind] of camps) {
      const node = this.nearestResource(resType, base.x, base.y);
      if (!node) continue;
      const drop = this.world.findNearestDropoff(this.team, node.x, node.y, kind);
      const dropDist = drop ? dist(drop.x, drop.y, node.x, node.y) : Infinity;
      if (dropDist > TILE * 7) {
        const camp = this.placeNear(campType, node.x, node.y, 1.5, 3.5);
        if (camp) {
          this.assignBuilder(camp);
          return; // one camp per cycle
        }
      }
    }
  }

  private maybeBuildFarms(base: Entity, villagerCount: number) {
    const p = this.world.player(this.team);
    const berriesLeft = this.world.entities.some(
      (e) => e.alive && e.kind === Kind.Resource && e.type === "berries" && e.amount > 0 &&
        dist(e.x, e.y, base.x, base.y) < TILE * 16,
    );
    const farmTarget = berriesLeft ? 2 : Math.max(3, Math.floor(villagerCount / 4));
    const farms = this.myBuildings("farm", false).length;
    if (farms < farmTarget && p.resources.wood > 120) {
      const mill = this.myBuildings("mill")[0];
      const cx = mill ? mill.x : base.x;
      const cy = mill ? mill.y : base.y;
      const farm = this.placeNear("farm", cx, cy, 2, 6);
      if (farm) this.assignBuilder(farm);
    }
    // A mill makes farm eco compact.
    if (!berriesLeft && this.myBuildings("mill", false).length === 0 && p.resources.wood > 200) {
      const mill = this.placeNear("mill", base.x, base.y, 3, 6);
      if (mill) this.assignBuilder(mill);
    }
  }

  private buildOrder(p: ReturnType<World["player"]>, base: Entity, villagerCount: number) {
    const have = (t: string) => this.myBuildings(t, false).length > 0;
    const haveDone = (t: string) => this.myBuildings(t).length > 0;

    // Dark Age: barracks once the eco can carry it.
    if (!have("barracks") && villagerCount >= 6 && p.resources.wood >= 170) {
      const b = this.placeNear("barracks", base.x, base.y, 4, 9);
      if (b) this.assignBuilder(b);
    }

    // Advance to Feudal.
    if (p.age === 0 && this.diff.maxAge >= 1 && haveDone("barracks") &&
        villagerCount >= Math.floor(this.villagerGoal * 0.55)) {
      const tc = this.myBuildings("town_center")[0];
      if (tc) this.world.research(this.team, tc.id, "age");
    }

    if (p.age >= 1) {
      if (!have("archery_range") && p.resources.wood >= 185) {
        const b = this.placeNear("archery_range", base.x, base.y, 4, 9);
        if (b) this.assignBuilder(b);
      }
      if (!have("blacksmith") && p.resources.wood >= 160) {
        const b = this.placeNear("blacksmith", base.x, base.y, 3, 8);
        if (b) this.assignBuilder(b);
      }
      // A defensive tower at the front.
      if (this.myBuildings("watch_tower", false).length < (this.diff.id === "squire" ? 0 : 2) &&
          p.resources.wood > 220) {
        const c = { x: this.world.worldW / 2, y: this.world.worldH / 2 };
        const fx = base.x + (c.x - base.x) * 0.22;
        const fy = base.y + (c.y - base.y) * 0.22;
        const t = this.placeNear("watch_tower", fx, fy, 0, 4);
        if (t) this.assignBuilder(t);
      }
    }

    // Advance to Castle.
    if (p.age === 1 && this.diff.maxAge >= 2 && haveDone("blacksmith") &&
        villagerCount >= Math.floor(this.villagerGoal * 0.8)) {
      const tc = this.myBuildings("town_center")[0];
      if (tc) this.world.research(this.team, tc.id, "age");
    }

    if (p.age >= 2) {
      if (!have("stable") && p.resources.wood >= 185) {
        const b = this.placeNear("stable", base.x, base.y, 4, 9);
        if (b) this.assignBuilder(b);
      }
      if (!have("siege_workshop") && p.resources.wood >= 210) {
        const b = this.placeNear("siege_workshop", base.x, base.y, 4, 9);
        if (b) this.assignBuilder(b);
      }
    }
  }

  // ------------------------------------------------------------ military --

  private armyUnits(): Entity[] {
    return this.myUnits().filter((e) => e.type !== "villager" && e.type !== "monk");
  }

  private military() {
    this.trainArmy();
    this.heroStep();
    this.scoutStep();

    const defended = this.defendStep();
    if (!defended) {
      this.attackStep();
      this.harassStep();
    }
    this.abilityStep();
  }

  /** Field the one Champion once the economy can spare the cost. */
  private heroStep() {
    if (!this.diff.usesAbilities || this.savingForAge) return;
    const p = this.world.player(this.team);
    if (p.heroState !== "none") return;
    if (this.myUnits("villager").length < 12) return;
    if (p.resources.gold < 120 || p.resources.food < 150) return;
    const tc = this.myBuildings("town_center")[0];
    if (tc) this.world.trainUnit(this.team, tc.id, "hero");
  }

  /** Fire signature abilities on a knot of army units that are mid-fight. */
  private abilityStep() {
    if (!this.diff.usesAbilities) return;
    const fighters = this.armyUnits().filter(
      (e) => ABILITIES[e.type] && e.abilityCooldown <= 0 && e.order.kind === OrderKind.Attack,
    );
    // Fire in a real melee (2+), or whenever the Champion is in the thick of it.
    const hero = fighters.find((f) => UNITS[f.type]?.hero);
    if (fighters.length >= 2 || hero) this.world.useAbility(fighters.map((f) => f.id));
  }

  /** Composition weights, optionally countering what we've seen. */
  private composition(): Record<string, number> {
    const p = this.world.player(this.team);
    const base: Record<string, number> = {};
    if (p.age === 0) {
      base.militia = 0.7;
      base.spearman = 0.3;
    } else if (p.age === 1) {
      base.militia = 0.4;
      base.archer = 0.35;
      base.spearman = 0.15;
      base.skirmisher = 0.1;
    } else {
      base.knight = 0.3;
      base.crossbow = 0.26;
      base.archer = 0.04;
      base.militia = 0.18;
      base.catapult = 0.08;
      base.ram = 0.05;
      base.spearman = 0.06;
      base.trebuchet = 0.03;
      // The enemy is walling up — bring the wall-breakers.
      if (this.seenWalls >= 4 && this.myBuildings("siege_workshop").length > 0) {
        base.ram += 0.14;
        base.trebuchet += 0.1;
        base.catapult += 0.08;
      }
    }
    if (!this.diff.counters) return base;

    const s = this.seen;
    const total = s.infantry + s.archer + s.cavalry + s.siege;
    if (total < 3) return base;
    // Blend the base comp with counters to the observed army.
    const counter: Record<string, number> = {};
    const add = (k: string, v: number) => (counter[k] = (counter[k] ?? 0) + v);
    add("archer", (s.infantry / total) * 0.8);
    add("skirmisher", (s.archer / total) * 0.6);
    add("knight", (s.archer / total) * 0.4 + (s.siege / total) * 0.7);
    add("spearman", (s.cavalry / total) * 0.9);
    add("militia", (s.archer / total) * 0.2 + (s.siege / total) * 0.3);
    const out: Record<string, number> = {};
    for (const k of new Set([...Object.keys(base), ...Object.keys(counter)])) {
      out[k] = (base[k] ?? 0) * 0.45 + (counter[k] ?? 0) * 0.55;
    }
    return out;
  }

  private trainArmy() {
    // Hold army production too while banking for an age — get to the next tier
    // first; a Dark-Age army just feeds a Feudal one. (Keep a token defence.)
    if (this.savingForAge && this.armyUnits().length >= 4) return;
    const p = this.world.player(this.team);
    const comp = this.composition();
    const army = this.armyUnits();
    const want = Math.max(this.armySize * 1.4, army.length + 4);

    const countByType: Record<string, number> = {};
    for (const u of army) countByType[u.type] = (countByType[u.type] ?? 0) + 1;

    // Greedy: train the unit type with the biggest deficit that we can afford.
    const deficits = Object.entries(comp)
      .map(([type, w]) => [type, w * want - (countByType[type] ?? 0)] as [string, number])
      .filter(([type, d]) => d > 0.5 && UNITS[type] && p.age >= UNITS[type].age)
      .sort((a, b) => b[1] - a[1]);

    for (const [type] of deficits) {
      const def = UNITS[type];
      const producers = this.myBuildings(def.trainedAt).filter((b) => b.productionQueue.length < 2);
      for (const b of producers) {
        if (!this.world.trainUnit(this.team, b.id, type)) break;
      }
    }
  }

  private scoutStep() {
    if (!this.diff.scouts) return;
    if (this.gameTime - this.lastScoutTime < 120) return;
    const scout = this.world.byId.get(this.scoutId);
    if (scout && scout.alive && scout.order.kind !== OrderKind.Idle) return;
    // Use the cheapest spare military unit; fall back to a villager early on.
    const army = this.armyUnits();
    const unit = army.find((u) => u.order.kind === OrderKind.Idle) ??
      (this.gameTime < 180 ? this.myUnits("villager")[0] : undefined);
    if (!unit) return;
    this.scoutId = unit.id;
    this.lastScoutTime = this.gameTime;
    const enemyStart = this.world.map.starts[this.enemyTeam];
    const home = this.world.map.starts[this.team];
    // Arc to the enemy base edge, then home.
    this.world.issueMove([unit.id], enemyStart.x + this.rng.range(-200, 200), enemyStart.y + this.rng.range(-200, 200));
    this.world.issueMove([unit.id], home.x, home.y, true);
  }

  /** Returns true if we are busy defending. */
  private defendStep(): boolean {
    const buildings = this.myBuildings(undefined, false);
    let threatX = 0;
    let threatY = 0;
    let threats = 0;
    for (const e of this.world.entities) {
      if (!e.alive || !this.isHostile(e.team) || e.kind !== Kind.Unit) continue;
      if (e.type === "villager") continue;
      for (const b of buildings) {
        if (dist(e.x, e.y, b.x, b.y) < 320) {
          threatX += e.x;
          threatY += e.y;
          threats++;
          break;
        }
      }
    }
    if (threats < 2) return false;
    threatX /= threats;
    threatY /= threats;
    // Villagers caught in the raid take cover and scatter.
    const panicked = this.myUnits("villager")
      .filter((v) => v.abilityCooldown <= 0 && dist(v.x, v.y, threatX, threatY) < 360)
      .map((v) => v.id);
    if (panicked.length) this.world.useAbility(panicked);
    this.attacking = false;
    const defenders = this.armyUnits().filter((u) => u.order.kind !== OrderKind.Attack);
    if (defenders.length) {
      this.world.issueMove(defenders.map((u) => u.id), threatX, threatY, false, true);
    }
    return true;
  }

  private attackStep() {
    const army = this.armyUnits();
    const armyPop = army.reduce((s, u) => s + (UNITS[u.type]?.pop ?? 1), 0);
    if (this.attacking) {
      // Wave over when army is spent.
      if (armyPop < this.armySize * 0.35) { this.attacking = false; return; }
      // Smart retreat: only if our committed force (plus nearby reinforcements)
      // is badly outmatched — not just because the defender has the home edge.
      // Rate-limited so the army doesn't oscillate at the wall.
      const fighting = army.filter((u) => u.order.kind === OrderKind.Attack || u.order.kind === OrderKind.AttackMove);
      if (fighting.length >= 5 && this.gameTime - this.lastRetreatTime > 14) {
        let fx = 0;
        let fy = 0;
        for (const u of fighting) { fx += u.x; fy += u.y; }
        fx /= fighting.length;
        fy /= fighting.length;
        const mine = this.strengthNear(fx, fy, 700, true); // our force + reinforcements
        const foe = this.strengthNear(fx, fy, 440, false);
        // Only bail from a genuinely overwhelming force — never from a small
        // garrison we should be steamrolling.
        if (foe > 110 && mine < foe * 0.38) {
          const home = this.world.map.starts[this.team];
          this.world.issueMove(army.map((u) => u.id), home.x, home.y, false, false);
          this.attacking = false;
          this.lastRetreatTime = this.gameTime;
          this.lastWaveTime = this.gameTime - this.cadence * 0.55; // regroup, then come again
          return;
        }
      }
      return;
    }
    if (armyPop < this.armySize) return;
    if (this.gameTime - this.lastWaveTime < this.cadence) return;

    this.lastWaveTime = this.gameTime;
    this.attacking = true;

    // Primary target: nearest known enemy building, else their start position.
    const enemyBuildings = this.world.entities.filter(
      (e) => e.alive && e.team === this.enemyTeam && e.kind === Kind.Building &&
        this.world.fogAt(this.team, e.x, e.y) !== 0,
    );
    const start = this.world.map.starts[this.enemyTeam];
    let tx = start.x;
    let ty = start.y;
    const base = this.base();
    if (enemyBuildings.length && base) {
      enemyBuildings.sort((a, b) => dist(a.x, a.y, base.x, base.y) - dist(b.x, b.y, base.x, base.y));
      tx = enemyBuildings[0].x;
      ty = enemyBuildings[0].y;
    }

    // Siege engines peel off to smash the nearest fortification on the approach
    // so the melee isn't left chipping a stone wall with swords.
    const siege = army.filter((u) => UNITS[u.type]?.armorClass === "siege");
    let melee = army;
    if (siege.length > 0) {
      const forts = this.world.entities.filter(
        (e) => e.alive && e.kind === Kind.Building && this.isHostile(e.team) &&
          this.world.fogAt(this.team, e.x, e.y) !== 0 &&
          ["stone_wall", "gate", "palisade", "watch_tower", "castle"].includes(e.type),
      );
      if (forts.length > 0) {
        forts.sort((a, b) => dist(a.x, a.y, tx, ty) - dist(b.x, b.y, tx, ty));
        this.world.issueAttack(siege.map((u) => u.id), forts[0].id);
        melee = army.filter((u) => UNITS[u.type]?.armorClass !== "siege");
      }
    }

    const ids = melee.map((u) => u.id);
    if (this.diff.counters && melee.length >= 10) {
      // Two-prong: main force + flank.
      const main = ids.slice(0, Math.floor(ids.length * 0.7));
      const flank = ids.slice(Math.floor(ids.length * 0.7));
      this.world.issueMove(main, tx, ty, false, true);
      const side = this.rng.bool() ? 1 : -1;
      this.world.issueMove(flank, tx + side * 420, ty - side * 420, false, true);
      this.world.issueMove(flank, tx, ty, true, true);
    } else if (ids.length > 0) {
      this.world.issueMove(ids, tx, ty, false, true);
    }
  }

  /** Raid: send fast units to butcher the enemy's villagers / eco. */
  private harassStep() {
    if (!this.diff.harasses && this.style !== "rush") return;
    const cool = this.style === "rush" ? 55 : 85;
    if (this.gameTime - this.lastHarassTime < cool) return;
    let raiders = this.myUnits("knight").filter((u) => u.order.kind === OrderKind.Idle);
    if (raiders.length < 3) {
      raiders = raiders.concat(this.myUnits("militia").filter((u) => u.order.kind === OrderKind.Idle));
    }
    if (raiders.length < 3) return;
    this.lastHarassTime = this.gameTime;
    const ids = raiders.slice(0, 4).map((u) => u.id);
    // Prefer a visible enemy villager; otherwise sweep their base eco.
    const vills = this.world.entities.filter(
      (e) => e.alive && e.kind === Kind.Unit && this.isHostile(e.team) && e.type === "villager" &&
        this.world.fogAt(this.team, e.x, e.y) === FOG_VISIBLE,
    );
    const base = this.base();
    if (vills.length > 0 && base) {
      vills.sort((a, b) => dist(a.x, a.y, base.x, base.y) - dist(b.x, b.y, base.x, base.y));
      this.world.issueAttack(ids, vills[0].id);
      return;
    }
    const start = this.world.map.starts[this.enemyTeam];
    this.world.issueMove(ids, start.x + this.rng.range(-300, 300), start.y + this.rng.range(-300, 300), false, true);
  }
}
