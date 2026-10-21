// Fixtures for the Godot port's meta parity test: what the TypeScript meta
// modules (progression, chests, boon caches, achievements, history, career,
// playstyle, feedback, rival) return for a few hundred generated inputs, so the
// GDScript build can be checked against them value for value and sentence for
// sentence.
//
//   npx tsx scripts/godot_meta_fixtures.ts
//
// Writes godot/tests/fixtures/meta.json (inputs and outputs together, so the
// Godot side doesn't need this generator). Inputs use whole numbers and short
// decimals so they read back as the same doubles in both JSON parsers.

import fs from "fs";
import path from "path";
import { RNG } from "../src/engine/rng";
import { CHESTS, rollChest } from "../src/meta/chests";
import { rollBoonCache } from "../src/meta/boon_cache";
import { computeRewards, levelFromXp, totalXpForLevel, xpForLevel } from "../src/meta/progression";
import { challengesForWeek, evaluateAwards } from "../src/meta/achievements";
import { summarise, summariseHistory, type MatchRecord } from "../src/meta/history";
import {
  absorb, bestBy, careerMatch, emptyCareer, favourite, favouriteUnitOf, fold, kd, mostEffectiveUnit, topUnit, winRate, worstBy,
  type CareerMatch,
} from "../src/meta/career";
import { ARCHETYPES, analyse, features, matchStyles, openerWord, type PlaystyleProfile } from "../src/meta/playstyle";
import { matchFeedback } from "../src/meta/feedback";
import { adaptationFor, loadRival, memoryOf, settleRival, taunt, type RivalState } from "../src/meta/rival";
import type { MatchReport, PlayerReport, SideReport } from "../src/sim/metrics";
import { FACTION_IDS } from "../src/content/factions";

const OUT = path.resolve("../godot/tests/fixtures");
fs.mkdirSync(OUT, { recursive: true });

const rng = new RNG(20261021);
const int = (a: number, b: number) => rng.int(a, b);
const pick = <T>(xs: readonly T[]) => rng.pick(xs);
const chance = (p: number) => rng.bool(p);
const dec = (a: number, b: number) => Math.round(rng.range(a, b) * 100) / 100;

const MIL = ["militia", "spearman", "archer", "skirmisher", "horseman", "knight", "crossbow", "ram", "catapult", "monk", "horse_archer",
  "legionary", "samurai", "cataphract", "pikeman", "trebuchet", "scout", "javelin", "twohand"];
const MAPS = ["Highlands", "Riverlands", "Open Plains", "Black Forest", "Islands"];
const OATHS = ["plough", "sword", "hearth", "lance", "bow", "shield", "coin", "engine", "crown"];
const BUILDINGS = ["house", "barracks", "palisade", "stone_wall", "gate", "watch_tower", "watchfire", "castle", "farm"];

function units(pool: string[], n: number, lo: number, hi: number): Record<string, number> {
  const out: Record<string, number> = {};
  for (let i = 0; i < n; i++) out[pick(pool)] = int(lo, hi);
  return out;
}

// --------------------------------------------------------------- career --
let at = 1_700_000_000_000;
/** A CareerMatch with a style leaning, sometimes an old record missing the newer fields. */
function careerMatchGen(lean: string): CareerMatch {
  const dur = lean === "rush" ? int(420, 1000) : lean === "late" ? int(1900, 3200) : int(500, 2800);
  const hit = lean === "rush" ? int(180, 430) : lean === "boom" ? int(700, 1300) : chance(0.15) ? -1 : int(200, 1500);
  const old = chance(0.12);
  const ageTimes = [0];
  if (chance(0.9)) { ageTimes.push(int(250, 600)); if (chance(0.7)) { ageTimes.push(int(600, 1400)); if (chance(0.4)) ageTimes.push(int(1200, 2200)); } }
  const trained: Record<string, number> = { villager: int(15, 70), ...units(MIL, int(0, 4), 1, 40) };
  if (lean === "cav") Object.assign(trained, { horseman: int(20, 40), knight: int(5, 20) });
  const m: CareerMatch = {
    at: (at += int(30_000, 900_000)), kind: chance(0.7) ? "skirmish" : "online", ranked: chance(0.2), mode: pick(["conquest", "koth", "regicide"]),
    difficulty: pick(["squire", "knight", "lord", "warlord", ""]), won: chance(lean === "lose" ? 0.2 : 0.62),
    faction: chance(0.95) ? pick(FACTION_IDS) : "", commander: pick(["", "marshal", "steward", "warden"]),
    oaths: chance(0.5) ? [pick(OATHS), ...(chance(0.4) ? [pick(OATHS)] : [])] : [], map: chance(0.95) ? pick(MAPS) : "",
    players: int(2, 8), format: pick(["1 v 1", "2 v 2", "4-player FFA", ""]), allies: chance(0.3) ? [pick(FACTION_IDS)] : [],
    foes: [pick(FACTION_IDS), ...(chance(0.3) ? [pick(FACTION_IDS)] : [])], durationSec: dur, defeatedAt: chance(0.2) ? int(300, dur) : -1,
    age: ageTimes.length - 1, ageTimes, score: int(500, 12000), gathered: lean === "boom" ? int(15000, 30000) : int(3000, 20000),
    gatheredBy: { food: int(1000, 9000), wood: int(1000, 9000), gold: int(0, 5000) }, spent: int(2000, 18000),
    kills: lean === "brawl" ? int(80, 200) : int(0, 120), losses: int(0, 100), razed: lean === "demo" ? int(6, 20) : int(0, 9),
    buildingsLost: int(0, 10), damageDealt: int(0, 40000), damageTaken: int(0, 40000), peakArmy: int(5, 80),
    peakVillagers: lean === "boom" ? int(45, 80) : int(15, 60), idleVillagerTime: int(0, 900), tcIdleShare: dec(0, 0.6),
    upgrades: lean === "tech" ? int(10, 22) : int(0, 14), trained,
    killed: { villager: lean === "raid" ? int(10, 30) : int(0, 12), ...units(MIL, int(0, 3), 1, 30) },
    lost: { villager: int(0, 14), ...units(MIL, int(0, 3), 1, 30) },
    built: lean === "turtle" ? { palisade: int(10, 50), watch_tower: int(2, 8), castle: int(0, 2), gate: int(0, 3) } : units(BUILDINGS, int(0, 4), 1, 12),
  };
  if (!old) {
    Object.assign(m, {
      unitKills: units(MIL, int(0, 4), 0, 50), unitDamage: units(MIL, int(0, 3), 0, 9000), unitRazed: units(MIL, int(0, 2), 0, 6),
      firstHitAt: hit, firstKillAt: hit < 0 ? -1 : hit + int(0, 120), firstRazeAt: chance(0.6) ? int(400, dur) : -1,
      firstAttackAt: chance(0.2) ? int(800, 1600) : hit, firstDefendAt: chance(0.5) ? int(200, 900) : -1,
      damageAttacking: int(0, 20000), damageDefending: int(0, 20000), killsAttacking: int(0, 60), killsDefending: int(0, 60),
      opener: chance(0.7) ? units(MIL, int(1, 3), 50, 900) : {},
      spentOn: { units: int(1000, 9000), buildings: int(500, 7000), tech: int(0, 4000) },
      foe: {
        gathered: int(3000, 20000), razed: int(0, 8), kills: int(0, 100), losses: int(0, 100), peakArmy: int(5, 60), peakVillagers: int(15, 55),
        villagerKills: int(0, 12), upgrades: int(0, 14), defenses: dec(0, 9), age: int(1, 3), ...(chance(0.8) ? { firstAttackAt: chance(0.3) ? -1 : int(200, 1200) } : {}),
      },
    });
  } else if (chance(0.5)) {
    m.firstHitAt = hit;
  }
  return m;
}

const LEANS = ["rush", "late", "boom", "turtle", "demo", "raid", "tech", "brawl", "cav", "lose", "mixed"];

// ------------------------------------------------------- match reports --
function side(n: number): SideReport {
  return {
    teams: Array.from({ length: n }, (_, i) => i), score: int(0, 12000), gathered: int(0, 25000),
    gatheredBy: { food: int(0, 9000), wood: int(0, 9000), gold: int(0, 6000) }, spent: int(0, 20000),
    spentOn: { units: int(0, 9000), buildings: int(0, 7000), tech: int(0, 4000) }, banked: chance(0.3) ? int(1500, 6000) : int(0, 1400),
    unitsKilled: int(0, 120), unitsLost: int(0, 120), buildingsRazed: int(0, 12), buildingsLost: int(0, 10),
    damageDealt: dec(0, 40000), damageTaken: dec(0, 40000), peakArmy: int(0, 80), peakVillagers: int(5, 70), idleVillagerTime: int(0, 6000),
    idleTcTime: int(0, 900), idleProductionTime: int(0, 900), tcSeconds: chance(0.1) ? 0 : int(300, 2400), productionSeconds: int(0, 3000),
    age: int(0, 3), upgrades: int(0, 16), trainedByType: { villager: int(5, 60), ...units(MIL, int(0, 4), 1, 30) },
    lostByType: { villager: int(0, 20), ...units(MIL, int(0, 3), 1, 20) }, killedByType: { villager: int(0, 20), ...units(MIL, int(0, 3), 1, 20) },
    builtByType: units(BUILDINGS, int(0, 5), 1, 20), killsByUnit: units(["villager", ...MIL], int(0, 4), 0, 40),
    damageByUnit: Object.fromEntries(Object.entries(units(MIL, int(0, 3), 0, 9000)).map(([k, v]) => [k, v + 0.25])),
    razedByUnit: units(MIL, int(0, 2), 0, 5),
    firstHitAt: chance(0.2) ? -1 : int(100, 900), firstKillAt: chance(0.2) ? -1 : int(100, 900), firstRazeAt: chance(0.4) ? -1 : int(200, 1500),
    firstAttackAt: chance(0.3) ? -1 : int(120, 1200), firstDefendAt: chance(0.3) ? -1 : int(120, 1200), firstFieldAt: chance(0.3) ? -1 : int(120, 1200),
    damageAttacking: dec(0, 20000), damageDefending: dec(0, 20000), damageField: dec(0, 9000), killsAttacking: int(0, 40), killsDefending: int(0, 40),
    openerByUnit: Object.fromEntries(Object.entries(units(MIL, int(0, 3), 10, 900)).map(([k, v]) => [k, v + 0.5])),
  };
}

function report(): MatchReport {
  const n = int(2, 6);
  const durationSec = dec(120, 3000);
  const r: MatchReport = { you: side(1), foe: side(int(1, 3)), durationSec, mapName: pick(MAPS) };
  if (chance(0.85)) {
    const ffa = chance(0.3);
    const players: PlayerReport[] = [];
    for (let i = 0; i < n; i++) {
      const rel = i === 0 ? "you" : ffa ? "enemy" : i < n / 2 ? "ally" : "enemy";
      const group = ffa ? i : rel === "enemy" ? 1 : 0;
      const p: PlayerReport = {
        ...side(1), team: i, group, relation: rel as PlayerReport["relation"], horde: i === n - 1 && chance(0.15), won: chance(0.5), defeated: chance(0.3),
        defeatedAt: chance(0.3) ? int(200, durationSec) : -1,
      };
      if (chance(0.9)) p.faction = pick(FACTION_IDS);
      if (chance(0.8)) p.oaths = chance(0.5) ? [pick(OATHS)] : [];
      if (chance(0.85)) p.ageTimes = [0, ...(chance(0.85) ? [dec(200, 900)] : []), ...(chance(0.5) ? [dec(800, 1600)] : [])];
      players.push(p);
    }
    r.players = players;
  }
  return r;
}

// ------------------------------------------------------------- fixtures --
const fx: Record<string, unknown> = {};

fx.progression = {
  xpForLevel: Array.from({ length: 60 }, (_, i) => xpForLevel(i + 1)),
  totalXpForLevel: Array.from({ length: 30 }, (_, i) => totalXpForLevel(i + 1)),
  levelFromXp: [0, 99, 100, 186, 187, 5000, 12345, 123456, 999999].map((xp) => ({ xp, out: levelFromXp(xp) })),
  computeRewards: Array.from({ length: 40 }, () => {
    const o = { win: chance(0.5), durationSec: int(60, 4000), unitsKilled: int(0, 150), buildingsRazed: int(0, 25), difficulty: pick(["squire", "knight", "lord", "warlord", "conqueror"]), fairMode: chance(0.3) };
    return { o, out: computeRewards(o) };
  }),
};

fx.chests = [3, 17, 2024].map((seed) => {
  const r = new RNG(seed);
  const owned = new Set<string>();
  const rolls = [];
  for (let i = 0; i < 120; i++) {
    const res = rollChest(CHESTS[i % 3], owned, r);
    owned.add(res.variant.key);
    rolls.push({ key: res.variant.key, rarity: res.rarity, duplicate: res.duplicate, refund: res.refund });
  }
  return { seed, rolls };
});

fx.boonCache = [5, 99].map((seed) => {
  const r = new RNG(seed);
  const owned = new Set<string>();
  const rolls = [];
  for (let i = 0; i < 120; i++) { const res = rollBoonCache(owned, r); owned.add(res.key); rolls.push(res); }
  return { seed, rolls };
});

const records: MatchRecord[] = Array.from({ length: 80 }, () => ({
  at: 1_700_000_000_000 + int(0, 400) * 86_400_000, won: chance(0.6), mapName: pick(MAPS), mode: "conquest",
  difficulty: pick(["squire", "knight", "lord", "duke", "conqueror"]), players: int(2, 8), durationSec: int(200, 3200), score: int(0, 9000),
  foeScore: int(0, 9000), gathered: int(500, 20000), spent: int(400, 20000), unitsKilled: int(0, 160), unitsLost: chance(0.15) ? 0 : int(0, 60),
  buildingsRazed: int(0, 30), buildingsLost: chance(0.2) ? 0 : int(0, 14), peakArmy: int(5, 70), peakVillagers: int(10, 80), age: int(0, 3),
  idleVillagerTime: int(0, 900), idleTcTime: int(0, 300), tcSeconds: chance(0.1) ? 0 : int(30, 2400),
  favouriteUnit: pick(["", ...MIL]),
}));
fx.achievements = {
  weeks: Array.from({ length: 30 }, (_, w) => challengesForWeek(w).map((c) => c.id)),
  awards: records.map((m, i) => {
    const state = { earned: i % 3 === 0 ? ["first_blood", "rush"] : [], challengesDone: i % 4 === 0 ? [`${Math.floor(m.at / 604800000)}:ch_quick`] : [] };
    return { m, state, out: evaluateAwards(m, state) };
  }),
};

fx.history = {
  summaries: [records.slice(0, 1), records.slice(0, 7), records.slice(10, 30), records].map((rows) => ({ rows: rows.length, out: summariseHistory(rows) })),
  summarise: Array.from({ length: 10 }, () => {
    const rep = report();
    const meta = { won: chance(0.5), mode: "conquest", difficulty: "knight", players: int(2, 8), at: int(1, 1e6) };
    const out = summarise(rep, meta) as Partial<MatchRecord>;
    delete out.report;
    return { report: rep, meta, out };
  }),
};

// Careers: lists of matches with a lean, of several sizes.
const careers: CareerMatch[][] = [];
for (const lean of LEANS) for (const n of [4, 9, 16]) {
  careers.push(Array.from({ length: n }, () => careerMatchGen(chance(0.8) ? lean : pick(LEANS))));
}
careers.push([]);

const roundTrip = <T>(v: T): T => JSON.parse(JSON.stringify(v));
fx.career = {
  careers,
  folds: careers.map((ms) => {
    const c = fold(ms);
    return {
      career: roundTrip(c),
      read: {
        winRate: winRate(c.all), kd: kd(c.all), favFaction: favourite(c.byFaction), favMap: favourite(c.byMap), favUnit: favouriteUnitOf(c.all),
        topTrained: topUnit(c, "trained"), topKilled: topUnit(c, "killed", false), topKills: topUnit(c, "kills"), effective: mostEffectiveUnit(c, 5),
        bestFaction: bestBy(c.byFaction, 2), worstFaction: worstBy(c.byFaction, 2), worstFoe: worstBy(c.vsFaction, 1),
      },
    };
  }),
  matches: Array.from({ length: 24 }, () => {
    const rep = report();
    const meta = { at: int(1, 1e9), won: chance(0.5), kind: pick(["skirmish", "online"] as const), ranked: chance(0.3), mode: "conquest", difficulty: "lord", commander: "warden" };
    const cm = careerMatch(rep, meta);
    return { report: rep, meta, out: roundTrip(cm), absorbed: roundTrip(absorb(emptyCareer(), cm)) };
  }),
};

const profileView = (p: PlaystyleProfile) => roundTrip({
  ...p,
  primary: p.primary ? { id: p.primary.archetype.id, score: p.primary.score } : null,
  secondary: p.secondary ? { id: p.secondary.archetype.id, score: p.secondary.score } : null,
  scores: p.scores.map((s) => ({ id: s.archetype.id, score: s.score })),
});
fx.playstyle = {
  profiles: careers.map((ms) => profileView(analyse(ms))),
  styles: careers.flat().map((m) => matchStyles(m)),
  archetypes: careers.filter((ms) => ms.length).slice(0, 12).map((ms) => {
    const f = features(ms);
    return ARCHETYPES.map((a) => ({ id: a.id, score: a.score(f), title: a.title ? a.title(f) : null, describe: a.describe(f), evidence: a.evidence(f) }));
  }),
  openers: Array.from({ length: 30 }, () => { const o = units([...MIL, "villager"], int(0, 4), 0, 900); return { o, out: openerWord(o) }; }),
};

fx.feedback = Array.from({ length: 70 }, () => { const r = report(); return { r, out: matchFeedback(r) }; });

// The rival: a run of matches against it, start to finish.
const NOW = 1_700_000_000_000;
const realNow = Date.now;
Date.now = () => NOW;
const store: Record<string, string> = {};
(globalThis as unknown as { localStorage: unknown }).localStorage = {
  getItem: (k: string) => store[k] ?? null, setItem: (k: string, v: string) => { store[k] = v; }, removeItem: (k: string) => { delete store[k]; },
};
fx.rival = [["legion", 1], ["norse", 2], ["", 3]].map(([faction, seed]) => {
  for (const k of Object.keys(store)) delete store[k];
  const st: RivalState = loadRival(faction as string);
  const start = roundTrip(st);
  const played: CareerMatch[] = [];
  const steps = [];
  const local = new RNG(seed as number);
  for (let i = 0; i < 18; i++) {
    const m = careerMatchGen(pick(LEANS));
    m.won = local.bool(0.55);
    played.push(m);
    const before = memoryOf(st.current, played);
    const plan = { mem: before, adapt: adaptationFor(st.current, before), taunt: taunt(st.current, before) };
    const out = settleRival(st, m, faction as string, NOW + i * 1000);
    steps.push({ m, plan: roundTrip(plan), out, after: roundTrip(st) });
  }
  return { faction, start, steps };
});
Date.now = realNow;

const dest = path.join(OUT, "meta.json");
fs.writeFileSync(dest, JSON.stringify(fx));
console.log(`wrote ${dest} ${(fs.statSync(dest).size / 1024).toFixed(0)} KB`);
