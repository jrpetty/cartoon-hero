// The Nemesis: one named rival warlord who remembers you.
//
// Borrowed from action RPGs (Shadow of Mordor's Nemesis system) rather than
// from other strategy games: an opponent with a name, a rank and a memory.
// Every time you fight it, it learns — first what your army is made of, then
// when you attack and how you win — and it comes back playing the counter.
// Beat it and it carries a scar (and a grudge against whatever did it); beat it
// three times and it falls, and someone from its ranks rises to take its place.
// Lose to it and it climbs a rank and earns a name for how it beat you.
//
// Everything it knows is read from your own career record (meta/playstyle), so
// the rival is the playstyle analysis turned into an opponent.

import { FACTION_IDS } from "../content/factions";
import type { AIStyle, RivalAdaptation, SeenComposition } from "../ai/skirmish_ai";
import type { CareerMatch } from "./career";
import { analyse, attackAt, openerWord, unitClass, type UnitClass } from "./playstyle";

const KEY = "bb_rival_v1";

export interface RivalMemory {
  /** What it has learned, in the order it learns: army, timing, how you win. */
  army: SeenComposition | null;
  armyWord: string;
  timing: "early" | "late" | "mid" | null;
  winsBy: string;
}

export interface Rival {
  id: number;
  name: string;
  epithet: string;
  faction: string;
  /** 1–5. Climbs when it beats you. */
  rank: number;
  beatYou: number;
  lostToYou: number;
  scars: string[];
  /** The unit class that scarred it most recently — it brings the counter. */
  grudge: UnitClass | "";
  born: number;
  /** Where it came from ("Rose from Grimwald's ranks"). */
  origin: string;
  history: { at: number; rivalWon: boolean; note: string }[];
}

export interface Fallen { name: string; epithet: string; rank: number; beatYou: number; lostToYou: number; at: number }

export interface RivalState { current: Rival; fallen: Fallen[]; seq: number }

const NAMES = ["Grimwald", "Sigrun", "Vasca", "Tarkhan", "Oro", "Belisar", "Morwen", "Kaede", "Ulfar", "Temur", "Aurelia", "Drago",
  "Hakon", "Isolde", "Ravik", "Sabine", "Toghrul", "Yorick", "Casimir", "Brannoc", "Akane", "Varus", "Ingrid", "Malach"];
const FIRST_EPITHETS = ["the Upstart", "the Unproven", "the Hungry", "the Watcher", "the Ambitious", "the Young"];

/** The difficulty a rival of this rank plays at. */
export const RANK_DIFFICULTY = ["knight", "knight", "lord", "lord", "warlord", "conqueror"];
export const RANK_NAMES = ["", "Captain", "Warchief", "Warlord", "Overlord", "Tyrant"];

function store(): Storage | null {
  try { return typeof localStorage === "undefined" ? null : localStorage; } catch { return null; }
}

/** A small deterministic hash, so a new rival's name and faction come from its number. */
const pick = <T>(xs: readonly T[], n: number) => xs[((n * 2654435761) >>> 0) % xs.length];

function newRival(seq: number, avoidFaction: string, origin: string, rank = 1, now = Date.now()): Rival {
  const facs = FACTION_IDS.filter((f) => f !== avoidFaction);
  return {
    id: seq, name: pick(NAMES, seq + 7), epithet: pick(FIRST_EPITHETS, seq + 3), faction: pick(facs, seq + 11),
    rank, beatYou: 0, lostToYou: 0, scars: [], grudge: "", born: now, origin, history: [],
  };
}

export function loadRival(playerFaction = ""): RivalState {
  const s = store();
  try {
    const raw = s?.getItem(KEY);
    if (raw) { const st = JSON.parse(raw) as RivalState; if (st?.current?.name) return st; }
  } catch { /* fall through to a fresh rival */ }
  const st: RivalState = { current: newRival(1, playerFaction, "Has heard of you, and wants your name for its own"), fallen: [], seq: 1 };
  saveRival(st);
  return st;
}

export function saveRival(st: RivalState) {
  try { store()?.setItem(KEY, JSON.stringify(st)); } catch { /* storage full or blocked — the rival just forgets */ }
}

// ------------------------------------------------------------------ memory --
const CLASS_WORD: Record<UnitClass, string> = { infantry: "infantry", archer: "archers", cavalry: "cavalry", siege: "siege", support: "monks" };

/**
 * What the rival knows about you. It learns a layer per meeting: the first
 * time it has only heard stories; after one battle it knows your army; after
 * two, your timing; after three, how you win. Its rank lets it remember more.
 */
export function memoryOf(r: Rival, career: CareerMatch[]): RivalMemory {
  const met = r.history.length + Math.max(0, r.rank - 2);
  const recent = career.slice(-15);
  const mem: RivalMemory = { army: null, armyWord: "", timing: null, winsBy: "" };
  if (met < 1 || !recent.length) return mem;
  const comp: SeenComposition = { infantry: 0, archer: 0, cavalry: 0, siege: 0 };
  let total = 0;
  for (const m of recent) for (const [u, n] of Object.entries(m.trained)) {
    const c = unitClass(u);
    if (c && c !== "support") { comp[c] += n; total += n; }
  }
  if (total > 0) {
    // Scaled to "an army of 12 of these", which is what the AI's counter
    // blend reads its scouting in.
    for (const k of Object.keys(comp) as (keyof SeenComposition)[]) comp[k] = Math.round((comp[k] / total) * 12 * 10) / 10;
    mem.army = comp;
    const top = (Object.entries(comp) as [UnitClass, number][]).sort((a, b) => b[1] - a[1])[0];
    mem.armyWord = top[1] >= 5 ? CLASS_WORD[top[0]] : "a mixed army";
  }
  if (met >= 2) {
    const early = recent.filter((m) => { const a = attackAt(m); return a >= 0 && a <= 420; }).length / recent.length;
    const late = recent.filter((m) => { const a = attackAt(m); return a < 0 || a >= 780; }).length / recent.length;
    mem.timing = early >= 0.4 ? "early" : late >= 0.5 ? "late" : "mid";
  }
  if (met >= 3) {
    const p = analyse(career);
    mem.winsBy = p.primary ? p.title : p.enough ? "adapting" : "";
    if (!mem.winsBy) {
      const op = openerWord(Object.assign({}, ...recent.map((m) => m.opener ?? {})));
      mem.winsBy = op.word ? `${op.word} attacks` : "";
    }
  }
  return mem;
}

/** How it will play you, from what it knows. Stored in the match config so a replay rebuilds it. */
export function adaptationFor(r: Rival, mem: RivalMemory): RivalAdaptation {
  const a: RivalAdaptation = {};
  if (mem.army) a.expect = { ...mem.army };
  // A grudge: whatever scarred it, it now brings the answer to.
  if (r.grudge && r.grudge !== "support") {
    a.expect = a.expect ?? {};
    a.expect[r.grudge] = (a.expect[r.grudge] ?? 0) + 5;
  }
  if (mem.timing === "early") { a.style = "turtle"; a.walls = true; }
  else if (mem.timing === "late") a.style = "rush";
  else if (mem.timing === "mid") a.style = "boom" as AIStyle;
  return a;
}

/** What it says before the battle — the proof that it remembers. */
export function taunt(r: Rival, mem: RivalMemory): string {
  if (!r.history.length) return `So you're the one they talk about. I am ${r.name} ${r.epithet}, and I'll learn everything about you.`;
  const last = r.history[r.history.length - 1];
  if (r.grudge && last && !last.rivalWon) return `Your ${CLASS_WORD[r.grudge as UnitClass] ?? r.grudge} gave me this scar. I've brought their answer.`;
  if (mem.timing === "early") return `You always come before seven minutes. This time my walls will be waiting${mem.armyWord ? ` for your ${mem.armyWord}` : ""}.`;
  if (mem.timing === "late") return "You like to take your time and build. I won't give you the time.";
  if (last?.rivalWon) return `I beat you once. They call me ${r.epithet} for it now.`;
  if (mem.armyWord) return `I know your ${mem.armyWord} now. Every one of them.`;
  return "Again, then.";
}

// ----------------------------------------------------------------- outcome --
export interface RivalOutcome {
  lines: string[];
  /** Extra renown for the player. */
  renown: number;
  slain: boolean;
  promoted: boolean;
}

/** The epithet a rival earns for how it beat you. */
function epithetFor(m: CareerMatch): string {
  if (m.durationSec < 900) return "the Swift";
  if (m.buildingsLost >= 6) return "the Burner";
  if (m.durationSec >= 2100) return "the Patient";
  if ((m.lost.villager ?? 0) >= 10) return "the Butcher";
  return "the Unbroken";
}

/**
 * Settle a match against the rival. `m` is your side of it (a CareerMatch),
 * so the rival reads exactly what the career reads.
 */
export function settleRival(st: RivalState, m: CareerMatch, playerFaction = "", now = Date.now()): RivalOutcome {
  const r = st.current;
  const out: RivalOutcome = { lines: [], renown: 0, slain: false, promoted: false };
  if (!m.won) {
    r.beatYou++;
    const was = r.rank;
    r.rank = Math.min(5, r.rank + 1);
    out.promoted = r.rank > was;
    r.epithet = epithetFor(m);
    r.history.push({ at: now, rivalWon: true, note: `Beat you in ${Math.round(m.durationSec / 60)} min` });
    out.lines.push(`${r.name} beat you — now ${r.name} ${r.epithet}${out.promoted ? `, risen to ${RANK_NAMES[r.rank]}` : ""}.`);
    out.lines.push("It will remember how you played.");
  } else {
    r.lostToYou++;
    out.renown = 60 * r.rank;
    // Scarred by the unit class that did the most killing.
    const byClass: Partial<Record<UnitClass, number>> = {};
    for (const [u, k] of Object.entries(m.unitKills ?? m.trained)) { const c = unitClass(u); if (c) byClass[c] = (byClass[c] ?? 0) + k; }
    const top = (Object.entries(byClass) as [UnitClass, number][]).sort((a, b) => b[1] - a[1])[0];
    if (top) { r.grudge = top[0]; r.scars.push(`Scarred by your ${CLASS_WORD[top[0]]}`); }
    r.history.push({ at: now, rivalWon: false, note: `Lost to you in ${Math.round(m.durationSec / 60)} min` });
    if (r.lostToYou >= 3) {
      out.slain = true;
      out.renown += 250;
      st.fallen.unshift({ name: r.name, epithet: r.epithet, rank: r.rank, beatYou: r.beatYou, lostToYou: r.lostToYou, at: now });
      st.fallen = st.fallen.slice(0, 12);
      st.seq++;
      const heir = newRival(st.seq, playerFaction, `Rose from ${r.name}'s ranks after you ended ${r.name}`, Math.min(3, 1 + Math.floor(st.fallen.length / 2)), now);
      // The heir watched every battle: it keeps its master's grudge.
      heir.grudge = r.grudge;
      st.current = heir;
      out.lines.push(`${r.name} ${r.epithet} has fallen for good. +${out.renown} renown.`);
      out.lines.push(`${heir.name} ${heir.epithet} rises from the ranks to take their place — and remembers.`);
    } else {
      out.lines.push(`You drove off ${r.name}${top ? `, scarred by your ${CLASS_WORD[top[0]]}` : ""}. +${out.renown} renown.`);
      out.lines.push(`${r.name} escaped, and will be back (${3 - r.lostToYou} more defeat${r.lostToYou === 2 ? "" : "s"} to end them).`);
    }
  }
  r.history = r.history.slice(-20);
  saveRival(st);
  return out;
}
