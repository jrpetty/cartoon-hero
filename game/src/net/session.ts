// Glues the deterministic Lockstep driver to a PeerLink transport and adds
// periodic checksum exchange so a desync is caught loudly instead of two
// players silently drifting into different games.

import { World } from "../sim/world";
import { Team } from "../sim/types";
import { worldChecksum } from "../sim/commands";
import { Lockstep, Turn } from "./lockstep";
import { PeerLink } from "./webrtc";

type NetMsg =
  | { k: "turn"; turn: Turn }
  | { k: "sum"; tick: number; sum: number };

export class NetSession {
  lock: Lockstep | null = null;
  readonly localTeam: Team;
  readonly peerTeam: Team;
  desynced = false;
  private localSums = new Map<number, number>();
  private readonly sumEvery = 40; // compare state every 2s of sim time

  constructor(public link: PeerLink, public isHost: boolean) {
    this.localTeam = isHost ? Team.Player : Team.Enemy;
    this.peerTeam = isHost ? Team.Enemy : Team.Player;
  }

  /** Build the lockstep driver over a freshly-inited world and wire the wire. */
  attach(world: World, inputDelay = 5) {
    this.lock = new Lockstep(world, this.localTeam, [Team.Player, Team.Enemy], inputDelay,
      (turn) => this.link.send({ k: "turn", turn } as NetMsg));
    this.link.onData = (raw) => {
      const m = raw as NetMsg;
      if (m.k === "turn") this.lock?.receiveTurn(m.turn);
      else if (m.k === "sum") this.compare(m.tick, m.sum);
    };
  }

  /** Author + send the local team's turn for one tick. Driven at the real-time
   *  sim rate, independent of stepping, so a stalled peer still receives our
   *  input (otherwise both sides would deadlock waiting on each other). */
  authorTick() {
    this.lock?.authorTurn();
  }

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
        this.link.send({ k: "sum", tick: t, sum } as NetMsg);
        this.compare(t);
      }
      n++;
    }
    return n;
  }

  private remoteSums = new Map<number, number>();
  private compare(tick: number, remote?: number) {
    if (remote !== undefined) this.remoteSums.set(tick, remote);
    const r = this.remoteSums.get(tick);
    const l = this.localSums.get(tick);
    if (r !== undefined && l !== undefined && r !== l) this.desynced = true;
  }
}
