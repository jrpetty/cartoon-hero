// Glues the deterministic Lockstep driver to a Transport (WebRTC peer or
// server-relayed WebSocket) and adds periodic checksum exchange so a desync is
// caught loudly instead of players silently drifting into different games.
// Works for any number of teams (2 up to MAX_TEAMS, e.g. 8v8).

import { World } from "../sim/world";
import { Team } from "../sim/types";
import { worldChecksum } from "../sim/commands";
import { Lockstep } from "./lockstep";
import { Transport } from "./transport";

export class NetSession {
  lock: Lockstep | null = null;
  desynced = false;
  private localSums = new Map<number, number>();
  private remoteSums = new Map<number, Map<Team, number>>();
  private readonly sumEvery = 40; // compare state every ~2s of sim time

  constructor(public transport: Transport, public localTeam: Team, public teams: Team[]) {}

  /** Build the lockstep driver over a freshly-inited world and wire the wire. */
  attach(world: World, inputDelay = 5) {
    this.lock = new Lockstep(world, this.localTeam, this.teams, inputDelay,
      (turn) => this.transport.send({ t: "turn", turn }));
    this.transport.onData = (raw) => {
      const m = raw as { t?: string; turn?: never; tick?: number; sum?: number; team?: number };
      if (m.t === "turn" && m.turn) this.lock?.receiveTurn(m.turn);
      else if (m.t === "sum" && typeof m.tick === "number" && typeof m.sum === "number" && typeof m.team === "number")
        this.compare(m.tick, m.team as Team, m.sum);
      else if (m.t === "drop" && typeof m.team === "number") this.lock?.dropTeam(m.team as Team);
    };
  }

  /** A team's player left mid-match — keep simulating without them. */
  dropTeam(team: Team) { this.lock?.dropTeam(team); }

  /** Author + send the local team's turn for one tick. Driven at the real-time
   *  sim rate, independent of stepping, so a stalled peer still receives our
   *  input (otherwise everyone would deadlock waiting on each other). */
  authorTick() { this.lock?.authorTurn(); }

  /** How far the local author cursor is ahead of the simulated tick — used to
   *  cap how far we run ahead of a lagging peer. */
  get authorLead(): number {
    return this.lock ? this.lock.nextAuthorTick + this.lock.inputDelay - this.lock.currentTick : 0;
  }

  /** Step the sim as far as the received remote input allows (bounded), emitting
   *  a checksum periodically for desync detection. Returns ticks simulated. */
  stepReady(maxSteps: number): number {
    const lock = this.lock;
    if (!lock) return 0;
    let n = 0;
    while (n < maxSteps && lock.step()) {
      const t = lock.currentTick;
      if (t % this.sumEvery === 0) {
        const sum = worldChecksum(lock.world);
        this.localSums.set(t, sum);
        this.transport.send({ t: "sum", tick: t, sum, team: this.localTeam });
        this.compare(t);
      }
      n++;
    }
    return n;
  }

  // Remote checksums keyed by tick AND author team: with 3+ players several peers
  // report a sum for the same tick, so a single shared slot would let a matching
  // peer mask another peer's divergence.
  private compare(tick: number, team?: Team, remote?: number) {
    if (team !== undefined && remote !== undefined) {
      let m = this.remoteSums.get(tick);
      if (!m) { m = new Map(); this.remoteSums.set(tick, m); }
      m.set(team, remote);
    }
    const l = this.localSums.get(tick);
    const m = this.remoteSums.get(tick);
    if (l === undefined || !m) return;
    for (const v of m.values()) if (v !== l) { this.desynced = true; return; }
  }
}
