// Deterministic lockstep driver. Every client runs its own World and advances
// it in step: a tick is only simulated once *all* players' commands for that
// tick have arrived. Local actions are scheduled `inputDelay` ticks in the
// future to hide latency. Because the sim is deterministic and all clients apply
// the same commands in the same order, their Worlds stay bit-identical.
//
// This module is transport-agnostic: `onSend` ships a finalized turn to peers,
// and `receiveTurn` feeds peers' turns back in. A WebRTC/data-channel layer
// wires those two together (Phase 2).

import { World } from "../sim/world";
import { Team } from "../sim/types";
import { Command, applyCommand, sortCommands } from "../sim/commands";

export interface Turn {
  tick: number;
  team: Team;
  cmds: Command[];
}

export class Lockstep {
  currentTick = 0;
  readonly teams: Team[];
  private buffer = new Map<number, Map<Team, Command[]>>();
  private pending: Command[] = [];
  nextAuthorTick = 0;
  /** Teams whose player has left: past the last turn they sent, their turns
   *  are treated as empty, so the rest of the lobby keeps simulating in sync
   *  instead of deadlocking. Turns they *did* send still apply — the relay
   *  delivers them to everyone before the drop, so every client (and a caster
   *  watching on a delay) applies exactly the same ones. */
  private dropped = new Set<Team>();
  private dropAfter = new Map<Team, number>();
  /** The highest tick each team's turn has arrived for. */
  private latest = new Map<Team, number>();
  /** Every command applied, with its tick, in the order applied — the match's
   *  replay. Off unless asked for. */
  record: { t: number; c: Command }[] | null = null;

  constructor(
    public world: World,
    public localTeam: Team,
    teams: Team[],
    public inputDelay = 6,
    private onSend?: (turn: Turn) => void,
  ) {
    this.teams = [...teams].sort((a, b) => a - b); // identical order on every client
    // Warm-up window: ticks [0, inputDelay) run with no commands so the sim can
    // start before any scheduled command is due.
    for (let t = 0; t < inputDelay; t++) {
      for (const tm of this.teams) this.submit(t, tm, []);
    }
  }

  private slot(tick: number): Map<Team, Command[]> {
    let m = this.buffer.get(tick);
    if (!m) { m = new Map(); this.buffer.set(tick, m); }
    return m;
  }
  private submit(tick: number, team: Team, cmds: Command[]) {
    this.slot(tick).set(team, cmds);
    if (tick > (this.latest.get(team) ?? -1)) this.latest.set(team, tick);
  }

  /** Is this team's turn needed before `tick` can run? */
  private gates(team: Team, tick: number): boolean {
    return !this.dropped.has(team) || tick <= (this.dropAfter.get(team) ?? -1);
  }

  /**
   * The last tick every still-playing team has sent its turn for — how far the
   * match could be simulated right now. A caster on a broadcast delay stays a
   * fixed number of ticks behind this.
   */
  readyThrough(): number {
    let t = Infinity;
    for (const tm of this.teams) {
      if (this.dropped.has(tm)) continue;
      t = Math.min(t, this.latest.get(tm) ?? -1);
    }
    return t === Infinity ? Math.max(-1, ...this.dropAfter.values()) : t;
  }

  /** Queue a local action; it executes on currentTick + inputDelay. */
  localCommand(cmd: Command) {
    this.pending.push(cmd);
  }

  /** Finalize this client's turn for the next authored tick and ship it. Call
   *  once per simulated tick to keep the local team always inputDelay ahead. */
  authorTurn(): Turn {
    const tick = this.nextAuthorTick + this.inputDelay;
    const cmds = this.pending;
    this.pending = [];
    this.submit(tick, this.localTeam, cmds);
    this.nextAuthorTick++;
    const turn: Turn = { tick, team: this.localTeam, cmds };
    this.onSend?.(turn);
    return turn;
  }

  /** Ingest a peer's finalized turn. */
  receiveTurn(turn: Turn) {
    this.submit(turn.tick, turn.team, turn.cmds);
  }

  /** Mark a team as departed; its turns no longer gate progress. */
  dropTeam(team: Team) {
    if (this.dropped.has(team)) return;
    this.dropped.add(team);
    this.dropAfter.set(team, this.latest.get(team) ?? -1);
  }

  /** True when every still-present player's commands for the current tick are in. */
  canStep(): boolean {
    const m = this.buffer.get(this.currentTick);
    for (const tm of this.teams) {
      if (!this.gates(tm, this.currentTick)) continue;
      if (!m || !m.has(tm)) return false;
    }
    return true;
  }

  /** Advance exactly one tick if ready. Returns false if still waiting on input. */
  step(): boolean {
    if (!this.canStep()) return false;
    const m = this.buffer.get(this.currentTick) ?? new Map<Team, Command[]>();
    const tagged: { cmd: Command; seq: number }[] = [];
    let seq = 0;
    for (const tm of this.teams) {
      for (const c of m.get(tm) ?? []) tagged.push({ cmd: c, seq: seq++ });
    }
    for (const cmd of sortCommands(tagged)) {
      applyCommand(this.world, cmd);
      this.record?.push({ t: this.currentTick, c: cmd });
    }
    this.world.tick();
    this.buffer.delete(this.currentTick);
    this.currentTick++;
    return true;
  }
}
