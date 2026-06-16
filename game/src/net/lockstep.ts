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

  /** True when every player's commands for the current tick are present. */
  canStep(): boolean {
    const m = this.buffer.get(this.currentTick);
    if (!m) return false;
    for (const tm of this.teams) if (!m.has(tm)) return false;
    return true;
  }

  /** Advance exactly one tick if ready. Returns false if still waiting on input. */
  step(): boolean {
    if (!this.canStep()) return false;
    const m = this.buffer.get(this.currentTick)!;
    const tagged: { cmd: Command; seq: number }[] = [];
    let seq = 0;
    for (const tm of this.teams) {
      for (const c of m.get(tm) ?? []) tagged.push({ cmd: c, seq: seq++ });
    }
    for (const cmd of sortCommands(tagged)) applyCommand(this.world, cmd);
    this.world.tick();
    this.buffer.delete(this.currentTick);
    this.currentTick++;
    return true;
  }
}
