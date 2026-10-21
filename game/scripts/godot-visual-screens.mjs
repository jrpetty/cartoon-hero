// The menus and full-screen UI for the Godot port's visual comparison.
//
// Builds a few localStorage fixtures (a fresh player, a veteran with a career,
// a history of reports, a nemesis, replays, a save and custom maps) through the
// real meta modules, then draws every screen in representative states through
// the real src/ui screens on @napi-rs/canvas. Writes spec.json — the fixtures
// and the exact states — for godot/tests/visual/screens_sheet.gd to replay
// through the port, and ts_<state>.png per state.
//
//   node scripts/godot-visual-screens.mjs ../godot/tests/visual/screens [state ...]
import { build } from "esbuild";
import { createCanvas } from "@napi-rs/canvas";
import { writeFileSync, mkdirSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";
import { aliasFonts } from "./godot-visual-fonts.mjs";

aliasFonts();
const outDir = process.argv[2] ?? "../godot/tests/visual/screens";
const only = process.argv.slice(3);
mkdirSync(outDir, { recursive: true });

// ------------------------------------------------------------- environment --
let store = {};
globalThis.localStorage = {
  getItem: (k) => (k in store ? store[k] : null),
  setItem: (k, v) => { store[k] = String(v); },
  removeItem: (k) => { delete store[k]; },
  clear: () => { store = {}; },
  key: (i) => Object.keys(store)[i] ?? null,
  get length() { return Object.keys(store).length; },
};
globalThis.document = { createElement: () => createCanvas(1, 1) };

const tmp = mkdtempSync(join(tmpdir(), "gvscr-"));
const outfile = join(tmp, "screens.cjs");
await build({
  stdin: {
    contents: `
      export * as screens from "./src/ui/screens";
      export { ui } from "./src/ui/ui";
      export { CareerScreen } from "./src/ui/career_screen";
      export { CodexScreen } from "./src/ui/codex";
      export { FactionBook } from "./src/ui/faction_book";
      export { SettingsScreen } from "./src/ui/settings_screen";
      export { RivalScreen } from "./src/ui/rival_screen";
      export { Profile } from "./src/meta/profile";
      export { recordCareer } from "./src/meta/career";
      export { recordMatch, summarise } from "./src/meta/history";
      export { saveRival } from "./src/meta/rival";
      export { saveReplay } from "./src/sim/replay";
      export { customFromGenerated, publishCustomMap, saveCustomMap } from "./src/maps/custom";
      export { emptyMatchReport } from "./src/sim/metrics";
      export { computeRewards } from "./src/meta/progression";
      export { DEFAULT_SETTINGS } from "./src/meta/settings";
      export { CHESTS } from "./src/meta/chests";
      export { VARIANT_BY_KEY, variantKey } from "./src/meta/catalog";
    `,
    resolveDir: process.cwd(), loader: "ts",
  },
  bundle: true, format: "cjs", platform: "node", outfile, logLevel: "error",
});
const require = createRequire(import.meta.url);
/** A fresh copy of every module, so entity ids (a module counter) start at 1. */
function fresh() {
  delete require.cache[require.resolve(outfile)];
  return require(outfile);
}
let M = fresh();

// ---------------------------------------------------------------- fixtures --
let t0 = 1_790_000_000_000; // 2026-09-21
const FOE = { gathered: 12000, razed: 3, kills: 40, losses: 40, peakArmy: 30, peakVillagers: 38, villagerKills: 4, upgrades: 6, defenses: 2, age: 2 };
function cm(over = {}) {
  return {
    at: (t0 += 3_600_000), kind: "skirmish", ranked: false, mode: "conquest", difficulty: "knight", won: true, faction: "legion", commander: "marshal", oaths: [],
    map: "Highlands", players: 2, format: "1 v 1", allies: [], foes: ["norse"], durationSec: 1500, defeatedAt: -1, age: 2, ageTimes: [0, 400, 1000],
    score: 5000, gathered: 12000, gatheredBy: { food: 5000, wood: 5000, gold: 2000 }, spent: 11000, kills: 40, losses: 30, razed: 3, buildingsLost: 2,
    damageDealt: 9000, damageTaken: 7000, peakArmy: 30, peakVillagers: 38, idleVillagerTime: 60, tcIdleShare: 0.1, upgrades: 6,
    trained: { villager: 40, legionary: 18, archer: 10, horseman: 6 }, killed: { villager: 4, militia: 20, archer: 9 }, lost: { legionary: 12, archer: 6 },
    built: { house: 8, barracks: 2, archery_range: 1, palisade: 12, watch_tower: 1 },
    unitKills: { legionary: 22, archer: 12, horseman: 6 }, unitDamage: { legionary: 5200, archer: 2400, horseman: 1400 }, unitRazed: { legionary: 2, horseman: 1 },
    firstHitAt: 520, firstKillAt: 540, firstRazeAt: 900, firstAttackAt: 560, firstDefendAt: 700,
    damageAttacking: 5000, damageDefending: 2000, killsAttacking: 25, killsDefending: 10,
    opener: { legionary: 1800, archer: 400 }, spentOn: { units: 6000, buildings: 3000, tech: 2000 }, foe: { ...FOE }, ...over,
  };
}

const FACS = ["legion", "kingdom", "norse", "shogunate", "khanate", "ascendancy"];
const MAPS = ["Highlands", "Riverlands", "Open Plains", "Twin Rivers"];
const DIFFS = ["squire", "knight", "lord", "knight"];
function careerMatches() {
  const out = [];
  for (let i = 0; i < 42; i++) {
    const fac = i % 5 === 4 ? "norse" : i % 3 === 2 ? "kingdom" : "legion";
    const online = i % 6 === 5;
    const won = i % 4 !== 3 && i % 7 !== 6;
    out.push(cm({
      won, faction: fac, foes: [FACS[(i + 2) % 6]], kind: online ? "online" : "skirmish", ranked: online && i % 12 === 11,
      difficulty: online ? "" : DIFFS[i % 4], commander: online ? "" : i % 2 ? "marshal" : "steward",
      map: MAPS[i % 4], oaths: i % 2 ? ["sword", "lance"] : ["plough"], durationSec: 620 + ((i * 137) % 1900),
      firstHitAt: won ? 380 + (i % 5) * 40 : 760 + (i % 4) * 90, firstAttackAt: won ? 400 + (i % 5) * 40 : 800,
      peakVillagers: won ? 44 + (i % 6) : 31, gathered: won ? 14000 + i * 90 : 9000,
      kills: won ? 48 + (i % 9) : 22, losses: won ? 24 : 44, razed: won ? 6 : 1,
      players: i % 8 === 0 ? 4 : 2, format: i % 8 === 0 ? "2 v 2" : "1 v 1", allies: i % 8 === 0 ? ["kingdom"] : [],
      trained: fac === "norse" ? { villager: 38, berserker: 16, archer: 8, ram: 2 } : fac === "kingdom" ? { villager: 42, militia: 14, knight: 9, archer: 7 } : { villager: 40, legionary: 18, archer: 10, horseman: 6 },
      upgrades: won ? 8 : 4, ageTimes: won ? [0, 380, 900] : [0, 520, 1300],
    }));
  }
  return out;
}

function side(over = {}) {
  return { ...M.emptyMatchReport().you, ...over };
}
function duelReport() {
  const r = M.emptyMatchReport();
  r.mapName = "Highlands";
  r.durationSec = 1242;
  r.you = side({
    teams: [0], score: 4200, gathered: 13200, gatheredBy: { food: 5400, wood: 5200, gold: 2600 },
    spent: 11900, spentOn: { units: 6700, buildings: 3800, tech: 1400 }, banked: 1300,
    unitsKilled: 58, unitsLost: 31, buildingsRazed: 7, buildingsLost: 2,
    damageDealt: 18400, damageTaken: 11100, peakArmy: 34, peakVillagers: 46, idleVillagerTime: 141,
    idleTcTime: 96, tcSeconds: 1242, idleProductionTime: 310, productionSeconds: 2900,
    age: 2, upgrades: 9, firstAttackAt: 410, killsAttacking: 30,
    trainedByType: { villager: 44, legionary: 22, archer: 12, horseman: 6 },
    lostByType: { legionary: 17, archer: 9, villager: 5 },
    killedByType: { militia: 26, archer: 14, villager: 12, knight: 6 },
    builtByType: { house: 9, barracks: 2, archery_range: 1, farm: 8, palisade: 14, watch_tower: 2 },
    killsByUnit: { legionary: 34, archer: 18, horseman: 6 },
  });
  r.foe = side({
    teams: [1], score: 3100, gathered: 9800, gatheredBy: { food: 4300, wood: 3900, gold: 1600 },
    spent: 9100, spentOn: { units: 5300, buildings: 2900, tech: 900 }, banked: 700,
    unitsKilled: 31, unitsLost: 58, buildingsRazed: 2, buildingsLost: 7,
    damageDealt: 11100, damageTaken: 18400, peakArmy: 27, peakVillagers: 37, idleVillagerTime: 320,
    idleTcTime: 280, tcSeconds: 1242, idleProductionTime: 520, productionSeconds: 2400,
    age: 2, upgrades: 5,
    trainedByType: { villager: 38, militia: 30, archer: 16, knight: 6 },
    lostByType: { militia: 26, archer: 14, villager: 12, knight: 6 },
    killedByType: { legionary: 17, archer: 9, villager: 5 },
    builtByType: { house: 7, barracks: 2, stable: 1, farm: 6 },
  });
  r.players = [
    { ...r.you, team: 0, group: 0, relation: "you", horde: false, won: true, defeated: false, defeatedAt: -1, oaths: ["sword", "lance"], faction: "legion", ageTimes: [0, 380, 900] },
    { ...r.foe, team: 1, group: 1, relation: "enemy", horde: false, won: false, defeated: true, defeatedAt: 1242, oaths: ["plough"], faction: "kingdom", ageTimes: [0, 450, 1100] },
  ];
  return r;
}

/** n realms in the given groups, with distinct ledgers; `me` is you. */
function standingsReport(groups, me = 0, mapName = "Open Plains") {
  const r = M.emptyMatchReport();
  r.mapName = mapName;
  r.durationSec = 1810;
  const myGroup = groups[me];
  const winGroup = groups[1];
  r.players = groups.map((g, t) => {
    const k = t + 1;
    const p = side({
      teams: [t], score: 1800 + ((k * 731) % 2400), gathered: 6000 + ((k * 2113) % 9000),
      gatheredBy: { food: 2500 + k * 300, wood: 2400 + ((k * 517) % 2000), gold: 900 + k * 120 },
      spent: 5500 + ((k * 1931) % 8000), spentOn: { units: 3000 + k * 400, buildings: 1800 + ((k * 211) % 900), tech: 500 + k * 90 }, banked: 200 + ((k * 97) % 900),
      unitsKilled: 10 + ((k * 13) % 50), unitsLost: 12 + ((k * 17) % 40), buildingsRazed: (k * 3) % 9, buildingsLost: (k * 5) % 7,
      damageDealt: 4000 + ((k * 1777) % 9000), damageTaken: 4200 + ((k * 1313) % 8000), peakArmy: 14 + ((k * 7) % 25), peakVillagers: 24 + ((k * 5) % 20),
      idleVillagerTime: 40 + ((k * 61) % 300), idleTcTime: 50 + ((k * 43) % 300), tcSeconds: 1810, idleProductionTime: 100, productionSeconds: 2000,
      age: 1 + (k % 3), upgrades: 2 + (k % 7),
      trainedByType: { villager: 30 + k, spearman: 2 + ((k * 3) % 14), archer: 1 + ((k * 5) % 12), knight: k % 4 === 0 ? 0 : (k * 2) % 9, horse_archer: k % 3 === 0 ? 5 : 0, ram: k % 5 === 0 ? 2 : 0 },
      lostByType: { spearman: 1 + (k % 6), archer: k % 5, villager: k % 3 },
      killedByType: { spearman: 2 + (k % 5), archer: 1 + (k % 4) },
      builtByType: { house: 4 + (k % 5), barracks: 1 + (k % 2), farm: 3 + (k % 6), archery_range: k % 2, stable: k % 3 === 0 ? 1 : 0 },
    });
    const won = g === winGroup;
    const defeated = !won && t % 2 === 1;
    return {
      ...p, team: t, group: g, relation: t === me ? "you" : g === myGroup ? "ally" : "enemy", horde: false,
      won, defeated, defeatedAt: defeated ? 600 + t * 170 : -1, oaths: t % 2 ? ["sword"] : ["plough", "crown"].slice(0, 1 + (t % 2)),
      faction: FACS[t % 6], ageTimes: [0, 400],
    };
  });
  const sum = (sideOf) => {
    const s = side({ teams: [] });
    for (const p of r.players.filter(sideOf)) {
      s.teams.push(p.team);
      for (const k of ["score", "gathered", "spent", "banked", "unitsKilled", "unitsLost", "buildingsRazed", "buildingsLost", "damageDealt", "damageTaken", "peakArmy", "peakVillagers", "idleVillagerTime", "upgrades"]) s[k] += p[k];
    }
    return s;
  };
  r.you = sum((p) => p.group === myGroup);
  r.foe = sum((p) => p.group !== myGroup);
  return r;
}

function graphOf(n, realms) {
  const ts = Array.from({ length: n }, (_, i) => i * 60);
  const ramp = (k, b) => Array.from({ length: n }, (_, i) => Math.round(b + i * k + Math.sin(i * 0.7 + k) * k * 1.5));
  if (!realms) {
    return { ts, mine: { score: ramp(140, 50), military: ramp(30, 4), economy: ramp(420, 100) }, foe: { score: ramp(100, 60), military: ramp(24, 6), economy: ramp(320, 120) } };
  }
  return {
    ts, mine: {}, foe: {},
    players: realms.map((t) => ({ team: t, you: t === 0, values: { score: ramp(80 + t * 17, 40), military: ramp(18 + t * 3, 5), economy: ramp(250 + t * 40, 80) } })),
  };
}

function buildFixtures() {
  const fixtures = {};
  // A brand-new player, before the first-launch faction choice.
  store = {};
  M.Profile.load().save();
  fixtures.fresh = { ...store };

  // A veteran: a profile, a career, a history with reports, a nemesis,
  // replays, a save and custom maps.
  store = {};
  const p = new M.Profile();
  p.data.name = "Aldric";
  p.data.commanderReveal = "";
  p.chooseFirstFaction("legion");
  p.data.renown = 2400;
  p.unlockFaction("kingdom");
  p.unlockFaction("norse");
  p.selectFaction("legion");
  p.data.renown = 2385;
  p.data.valor = 650;
  p.data.totalXp = 5200;
  p.data.stats = { wins: 26, losses: 16, played: 42, bestStreak: 6, streak: 2 };
  p.data.commanders = ["steward", "marshal", "warden"];
  p.selectCommander("marshal");
  for (const [u, r] of [["militia", 1], ["militia", 2], ["archer", 1], ["archer", 3], ["knight", 2], ["spearman", 4]]) {
    const key = M.variantKey(u, r);
    if (M.VARIANT_BY_KEY[key]) p.grant(key);
  }
  p.equip("archer", 3);
  p.data.achievements = ["first_blood", "rush"];
  p.save();
  for (const m of careerMatches()) M.recordCareer(m);
  // History: newest last in time, so the first ones recorded are the oldest.
  const duel = duelReport();
  for (let i = 0; i < 9; i++) {
    const rep = i === 8 ? duel : i === 7 ? standingsReport([0, 1, 2, 3]) : i === 6 ? standingsReport([0, 0, 1, 1]) : duelReport();
    if (i < 6) { rep.mapName = MAPS[i % 4]; rep.durationSec = 700 + i * 211; }
    M.recordMatch(M.summarise(rep, { won: i % 3 !== 1, mode: "conquest", difficulty: DIFFS[i % 4], players: rep.players.length, at: t0 + i * 7_200_000 }));
  }
  M.saveRival({
    current: {
      id: 2, name: "Morwen", epithet: "the Patient", faction: "khanate", rank: 3, beatYou: 2, lostToYou: 1,
      scars: ["Routed at Riverlands by your archers"], grudge: "archer", born: t0 - 9e8, origin: "Rose from the ranks of Grimwald's captains",
      history: [
        { at: t0 - 6e8, rivalWon: true, note: "Won at Highlands (14:02)" },
        { at: t0 - 4e8, rivalWon: false, note: "Lost at Riverlands — fled with a scar" },
        { at: t0 - 2e8, rivalWon: true, note: "Won at Open Plains (21:40)" },
      ],
    },
    fallen: [{ id: 1, name: "Grimwald", epithet: "the Upstart", faction: "norse", rank: 2 }],
    seq: 2,
  });
  const rep = (id, savedAt, kind, map, result, factions, names, imported) => ({
    version: 1, id, savedAt, kind, names, pov: 0, commands: [], endTick: 24000,
    summary: { map, players: factions.length, durationSec: 1200 + savedAt % 900, result, factions }, ...(imported ? { imported: true } : {}),
  });
  M.saveReplay(rep("rA", t0 - 5e6, "skirmish", "Highlands", "Victory", ["legion", "kingdom"], ["Aldric", "AI 2"]));
  M.saveReplay(rep("rB", t0 - 3e6, "watch", "Twin Rivers", "Unfinished", ["norse", "khanate", "shogunate", "ascendancy"], ["AI 1", "AI 2", "AI 3", "AI 4"]));
  M.saveReplay(rep("rC", t0 - 1e6, "online", "Riverlands", "Defeat", ["legion", "norse"], ["Aldric", "Sigrun"], true));
  store["banner_and_blade_saves_v1"] = JSON.stringify([{
    version: 2, label: "Highlands — 12:34", savedAt: t0, tick: 15000, commands: [],
    summary: { mapName: "Highlands", mode: "conquest", players: 2, difficulty: "Knight", elapsed: 754 },
  }]);
  const pub = M.customFromGenerated("highlands", 4242, 4, "Crow's Crossing");
  pub.author = "aldric";
  M.publishCustomMap(pub);
  M.saveCustomMap(M.customFromGenerated("open_plains", 77, 2, "Draft Field"));
  fixtures.veteran = { ...store };
  return fixtures;
}

// ------------------------------------------------------------------ states --
const fixtures = buildFixtures();
const awards = [{ id: "a", name: "Siege Breaker", valor: 60, weekly: false }, { id: "w", name: "Raze three buildings", valor: 40, weekly: true }];
const rewardsWin = M.computeRewards({ win: true, durationSec: 1242, unitsKilled: 58, buildingsRazed: 7, difficulty: "knight", fairMode: false });
const settings = { ...M.DEFAULT_SETTINGS, keybinds: { attackMove: "S" }, masterVol: 0.65, uiScale: 1.25 };

const SIZES = [[1600, 900, ""], [1100, 680, "_small"]];
const base = [
  { name: "menu", screen: "menu", fixture: "veteran" },
  { name: "menu_reveal", screen: "menu", fixture: "fresh" },
  { name: "setup_1v1", screen: "setup", fixture: "veteran", config: { seed: 123456 } },
  { name: "setup_2v2", screen: "setup", fixture: "veteran", config: { seed: 98765, players: 4, mode: "koth" }, set: { teamPreset: 2 } },
  { name: "setup_8p_4teams", screen: "setup", fixture: "veteran", config: { seed: 31337, players: 8, aiFactions: ["", "norse", "khanate", "", "shogunate"], aiDifficulties: ["knight", "knight", "warlord", "squire"] }, set: { teamPreset: 4 }, after: { scroll: 1e6 } },
  { name: "setup_custom_teams", screen: "setup", fixture: "veteran", config: { seed: 4, players: 5, teams: [1, 1, 2, 0, 0], presetId: "random" }, set: { teamPreset: "custom" } },
  { name: "armory_chests", screen: "armory", fixture: "veteran", set: { tab: "chests" } },
  { name: "armory_boons", screen: "armory", fixture: "veteran", set: { tab: "boons" } },
  { name: "armory_commanders", screen: "armory", fixture: "veteran", set: { tab: "commanders" } },
  { name: "armory_collection", screen: "armory", fixture: "veteran", set: { tab: "collection" } },
  { name: "armory_reveal", screen: "armory", fixture: "veteran", reveal: { chest: 1, rarity: 3, unit: "knight", duplicate: false } },
  ...["overview", "feedback", "economy", "military", "units"].map((tab) => ({
    name: `post_duel_${tab}`, screen: "postmatch", fixture: "veteran", set: { reportTab: tab, rivalLines: tab === "overview" ? ["Morwen the Patient flees with a scar.", "Two more defeats will end them."] : [] },
    args: { won: true, report: "duel", graph: "duel", awards: tab === "overview" ? "awards" : null, xpBefore: 5200, levelsGained: tab === "overview" ? 1 : 0 },
  })),
  ...["overview", "economy", "military", "units"].map((tab) => ({
    name: `post_ffa_${tab}`, screen: "postmatch", fixture: "veteran", set: { reportTab: tab },
    args: { won: false, report: "ffa4", graph: "ffa4", xpBefore: 5200, levelsGained: 0 },
  })),
  { name: "post_2v2_overview", screen: "postmatch", fixture: "veteran", set: { reportTab: "overview", graphMetric: "economy" }, args: { won: true, report: "team2v2", graph: "team2v2", xpBefore: 5200, levelsGained: 0 } },
  { name: "post_4v4_military", screen: "postmatch", fixture: "veteran", set: { reportTab: "military" }, args: { won: true, report: "team4v4", graph: null, xpBefore: 5200, levelsGained: 0 } },
  ...["overview", "style", "factions", "maps", "units", "matches", "replays"].map((tab) => ({ name: `career_${tab}`, screen: "career", fixture: "veteran", set: { tab } })),
  { name: "career_overview_scrolled", screen: "career", fixture: "veteran", set: { tab: "overview" }, after: { scroll: 760 } },
  { name: "career_style_mid", screen: "career", fixture: "veteran", set: { tab: "style" }, after: { scroll: 820 } },
  { name: "career_style_end", screen: "career", fixture: "veteran", set: { tab: "style" }, after: { scroll: 1e6 } },
  { name: "career_online", screen: "career", fixture: "veteran", set: { tab: "overview", filter: "online" } },
  { name: "career_empty", screen: "career", fixture: "fresh", set: { tab: "overview" } },
  { name: "codex_units", screen: "codex", fixture: "veteran", set: { tab: "units", selUnit: "legionary" } },
  { name: "codex_units_knight", screen: "codex", fixture: "veteran", set: { tab: "units", selUnit: "knight" } },
  { name: "codex_buildings", screen: "codex", fixture: "veteran", set: { tab: "buildings", selBuilding: "castle" } },
  { name: "codex_tech", screen: "codex", fixture: "veteran", set: { tab: "tech" } },
  { name: "codex_units_scrolled", screen: "codex", fixture: "veteran", set: { tab: "units", selUnit: "horse_archer" }, after: { unitScroll: 1e6 } },
  { name: "codex_tech_scrolled", screen: "codex", fixture: "veteran", set: { tab: "tech" }, after: { techScroll: 1e6 } },
  { name: "codex_records", screen: "codex", fixture: "veteran", set: { tab: "records" } },
  { name: "codex_records_report", screen: "codex", fixture: "veteran", set: { tab: "records", reportTab: "economy" }, openReport: 0 },
  { name: "factions_first", screen: "factions", fixture: "fresh", focus: "norse" },
  { name: "factions_book", screen: "factions", fixture: "veteran", focus: "khanate" },
  { name: "factions_owned", screen: "factions", fixture: "veteran", focus: "legion", after: { scroll: 400 } },
  { name: "settings_game", screen: "settings", fixture: "veteran", set: { tab: "game" }, args: { settings } },
  { name: "settings_controls", screen: "settings", fixture: "veteran", set: { tab: "controls" }, args: { settings } },
  { name: "nemesis", screen: "nemesis", fixture: "veteran", args: { playerFaction: "legion" } },
];
const states = [];
for (const s of base) for (const [W, H, suf] of SIZES) states.push({ time: 1.25, dt: 1 / 60, frames: 3, ...s, name: s.name + suf, W, H });
// A hover state or two, so the hover rim and a tooltip are compared too.
states.push({ ...states.find((s) => s.name === "menu"), name: "menu_hover", mouse: { mx: 1012, my: 560 } });
states.push({ ...states.find((s) => s.name === "career_matches"), name: "career_matches_hover", mouse: { mx: 700, my: 205 } });

const reports = { duel: duelReport(), ffa4: standingsReport([0, 1, 2, 3]), team2v2: standingsReport([0, 0, 1, 1]), team4v4: standingsReport([0, 0, 0, 0, 1, 1, 1, 1]) };
const graphs = { duel: graphOf(22, null), ffa4: graphOf(31, [0, 1, 2, 3]), team2v2: graphOf(26, [0, 1, 2, 3]) };
writeFileSync(join(outDir, "spec.json"), JSON.stringify({ fixtures, states, reports, graphs, rewards: rewardsWin, awards }, null, 0));

// ------------------------------------------------------------------ render --
function setFields(obj, fields) {
  for (const [k, v] of Object.entries(fields ?? {})) obj[k] = v;
}

function makeScreen(s, profile) {
  const S = M.screens;
  switch (s.screen) {
    case "menu": return new S.MenuScreen();
    case "setup": {
      const scr = new S.SetupScreen();
      setFields(scr.config, s.config);
      return scr;
    }
    case "armory": {
      const scr = new S.ArmoryScreen();
      if (s.reveal) {
        const chest = M.CHESTS[s.reveal.chest];
        const v = M.VARIANT_BY_KEY[M.variantKey(s.reveal.unit, s.reveal.rarity)];
        scr.opening = chest;
        scr.result = { variant: v, rarity: s.reveal.rarity, duplicate: s.reveal.duplicate, refund: 0 };
        scr.revealed = true;
      }
      return scr;
    }
    case "postmatch": return new S.PostMatchScreen();
    case "career": return new M.CareerScreen();
    case "codex": return new M.CodexScreen();
    case "factions": {
      const b = new M.FactionBook();
      b.focus(s.focus);
      return b;
    }
    case "settings": return new M.SettingsScreen();
    case "nemesis": return new M.RivalScreen();
  }
  throw new Error(`no screen ${s.screen}`);
}

function drawScreen(s, scr, profile, args) {
  const { W, H, time, dt } = s;
  switch (s.screen) {
    case "menu": return scr.draw(W, H, time, profile);
    case "setup": return scr.draw(W, H, time, profile);
    case "armory": return scr.draw(W, H, time, dt, profile);
    case "postmatch": return scr.draw(W, H, time, dt, args.won, reports[args.report], rewardsWin, profile, args.xpBefore, args.levelsGained, args.graph ? graphs[args.graph] : null, args.awards ? awards : []);
    case "career": return scr.draw(W, H, time, profile);
    case "codex": return scr.draw(W, H, time, profile);
    case "factions": return scr.draw(W, H, time, profile, dt);
    case "settings": return scr.draw(W, H, time, args.settings, false);
    case "nemesis": return scr.draw(W, H, time, args.playerFaction);
  }
}

for (const s of states) {
  if (only.length && !only.some((o) => s.name.startsWith(o))) continue;
  M = fresh();
  store = { ...fixtures[s.fixture] };
  const profile = M.Profile.load();
  const scr = makeScreen(s, profile);
  setFields(scr, s.set);
  if (s.openReport !== undefined) {
    // The history row opened back up as its report, as clicking "Report" does.
    const rows = JSON.parse(store["banner_and_blade_history_v1"]).sort((a, b) => b.at - a.at);
    scr.openReport = rows[s.openReport];
  }
  const args = structuredClone(s.args ?? {});
  const canvas = createCanvas(s.W, s.H);
  const ctx = canvas.getContext("2d");
  M.screens.setMouseDown(false);
  for (let f = 0; f < s.frames; f++) {
    const last = f === s.frames - 1;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, s.W, s.H);
    ctx.fillStyle = "#000"; ctx.fillRect(0, 0, s.W, s.H);
    const mouse = last && s.mouse ? s.mouse : { mx: -99, my: -99 };
    M.ui.begin(ctx, { mx: mouse.mx, my: mouse.my, clicked: false, rightClicked: false, alt: false });
    drawScreen(s, scr, profile, args);
    M.ui.flushTooltip(s.W, s.H);
    if (f === 0) setFields(scr, s.after);
  }
  writeFileSync(join(outDir, `ts_${s.name}.png`), canvas.toBuffer("image/png"));
  console.log("ts", s.name);
}
