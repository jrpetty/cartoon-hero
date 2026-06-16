// Networked-play foundation: a serializable representation of every player
// action, a deterministic applier, and a state checksum for desync detection.
//
// In lockstep multiplayer each client runs the same deterministic sim and only
// exchanges these Commands (not state). A command is scheduled to run on an
// agreed future tick and applied identically on every client before that tick
// is simulated — so all clients stay in perfect sync from the same seed.

import { World } from "./world";
import { EntityId, Team } from "./types";

export type Command =
  | { t: "move"; team: Team; ids: EntityId[]; x: number; y: number; queue: boolean; attackMove: boolean }
  | { t: "attack"; team: Team; ids: EntityId[]; target: EntityId; queue: boolean }
  | { t: "gather"; team: Team; ids: EntityId[]; node: EntityId; queue: boolean }
  | { t: "build"; team: Team; ids: EntityId[]; building: EntityId; queue: boolean }
  | { t: "stop"; team: Team; ids: EntityId[] }
  | { t: "hold"; team: Team; ids: EntityId[] }
  | { t: "ability"; team: Team; ids: EntityId[] }
  | { t: "place"; team: Team; building: string; x: number; y: number }
  | { t: "train"; team: Team; buildingId: EntityId; unit: string }
  | { t: "rally"; team: Team; buildingId: EntityId; x: number; y: number }
  | { t: "garrison"; team: Team; ids: EntityId[]; buildingId: EntityId }
  | { t: "banner"; team: Team; x: number; y: number };

/** Keep only ids the issuing team actually owns — a client can never command
 *  another player's units, and it keeps application order-independent. */
function owned(world: World, team: Team, ids: EntityId[]): EntityId[] {
  const out: EntityId[] = [];
  for (const id of ids) {
    const e = world.byId.get(id);
    if (e && e.alive && e.team === team) out.push(id);
  }
  return out;
}

function ownsBuilding(world: World, team: Team, id: EntityId): boolean {
  const e = world.byId.get(id);
  return !!e && e.alive && e.team === team;
}

/** Apply one command to the world. Pure w.r.t. determinism: same world + same
 *  command => same result on every client. Ownership is enforced here. */
export function applyCommand(world: World, c: Command): void {
  switch (c.t) {
    case "move": world.issueMove(owned(world, c.team, c.ids), c.x, c.y, c.queue, c.attackMove); break;
    case "attack": world.issueAttack(owned(world, c.team, c.ids), c.target, c.queue); break;
    case "gather": world.issueGather(owned(world, c.team, c.ids), c.node, c.queue); break;
    case "build": world.issueBuildRepair(owned(world, c.team, c.ids), c.building, c.queue); break;
    case "stop": world.issueStop(owned(world, c.team, c.ids)); break;
    case "hold": world.issueHold(owned(world, c.team, c.ids)); break;
    case "ability": world.useAbility(owned(world, c.team, c.ids)); break;
    case "place": world.placeBuilding(c.team, c.building, c.x, c.y); break;
    case "train": if (ownsBuilding(world, c.team, c.buildingId)) world.trainUnit(c.team, c.buildingId, c.unit); break;
    case "rally": if (ownsBuilding(world, c.team, c.buildingId)) world.setRally(c.buildingId, c.x, c.y); break;
    case "garrison": world.garrison(owned(world, c.team, c.ids), c.buildingId); break;
    case "banner": world.placeBanner(c.team, c.x, c.y); break;
  }
}

/** Deterministic order so a set of commands applies identically everywhere:
 *  by team, then by stable arrival index within the team. */
export function sortCommands(cmds: { cmd: Command; seq: number }[]): Command[] {
  return [...cmds]
    .sort((a, b) => a.cmd.team - b.cmd.team || a.seq - b.seq)
    .map((x) => x.cmd);
}

// --- State checksum --------------------------------------------------------
// A cheap FNV-1a-style rolling hash over the salient sim state. Floats are
// quantized to 0.01 so identical same-engine sims hash equal while real
// divergence (units in different places) still trips it.
function mix(acc: number, n: number): number {
  acc = (acc ^ (n | 0)) >>> 0;
  return Math.imul(acc, 16777619) >>> 0;
}

export function worldChecksum(world: World): number {
  let h = 2166136261 >>> 0;
  h = mix(h, world.tickCount);
  // Entities in id order (entities array is append-only, ids monotonic).
  for (const e of world.entities) {
    if (!e.alive) continue;
    h = mix(h, e.id);
    h = mix(h, e.team);
    h = mix(h, Math.round(e.x * 100));
    h = mix(h, Math.round(e.y * 100));
    h = mix(h, Math.round(e.hp * 100));
    h = mix(h, e.order.kind);
    h = mix(h, e.order.target);
  }
  // Per-player economy/pop/age.
  for (let t = 0; t < world.numTeams; t++) {
    const p = world.player(t as Team);
    if (!p) continue;
    h = mix(h, Math.round(p.resources.food));
    h = mix(h, Math.round(p.resources.wood));
    h = mix(h, Math.round(p.resources.gold));
    h = mix(h, p.popUsed);
    h = mix(h, p.age);
  }
  return h >>> 0;
}
