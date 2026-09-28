// Banner & Blade — application shell. Owns the canvas, the app state machine
// (menu → setup → match → post-match, plus the armory), the fixed-timestep
// sim loop, input routing and the meta-progression loop around each match.

import { World, WorldEvent } from "./sim/world";
import { BuildState, Entity, EntityId, Kind, MAX_TEAMS, OrderKind, Stance, Team } from "./sim/types";
import { UNITS } from "./content/units";
import { ABILITIES } from "./content/abilities";
import { BUILDINGS } from "./content/buildings";
import { COMMANDERS, COMMANDER_IDS } from "./content/commanders";
import { SIM_DT, SIM_HZ, TILE } from "./content/balance";
import { RNG } from "./engine/rng";
import {
  CommandLog, SAVE_FORMAT_VERSION, SaveGame, deleteSave, listSaves, replayTo, writeSave,
} from "./sim/savegame";

/** Seconds as m:ss — a match clock reads as a duration, not a number. */
const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
import { dayPhase } from "./content/daynight";
import { PRESETS, generateMap } from "./maps/generator";
import { randomSeed } from "./engine/rng";
import { SkirmishAI } from "./ai/skirmish_ai";
import { DIFFICULTIES } from "./ai/difficulty";
import { Camera } from "./engine/camera";
import { wallLinePoints as computeWallLine, blockPoints } from "./engine/wallline";
import { toggleFullscreen } from "./engine/fullscreen";
import { FULLSCREEN_ZONE, fullscreenButton } from "./ui/fullscreen_button";
import { WarbandRun } from "./sim/warband";
import { WarbandScreen } from "./ui/warband_screen";
import { Input } from "./engine/input";
import { Particles } from "./engine/particles";
import { audio } from "./engine/audio";
import { Renderer, CommandMarker, GhostPlacement } from "./render/renderer";
import { loadSprites } from "./render/sprites";
import { PAL, withAlpha } from "./render/palette";
import { HUD, MatchController, MINIMAP_SIZE } from "./ui/hud";
import { drawSpectatorPanels, teamLabel } from "./ui/spectator";
import { ui } from "./ui/ui";
import { AGES } from "./content/tech";
import { OATHS } from "./content/oaths";
import { DEFAULT_FACTION, FACTIONS, FACTION_IDS } from "./content/factions";
import { alliancesFor, resizeTeams } from "./ui/teams";
import { CodexScreen } from "./ui/codex";
import { FactionBook } from "./ui/faction_book";
import { CareerScreen } from "./ui/career_screen";
import { RivalScreen } from "./ui/rival_screen";
import { RANK_DIFFICULTY, adaptationFor, loadRival, memoryOf, settleRival, taunt } from "./meta/rival";
import { Caster } from "./ui/caster";
import { NetReplaySetup, ReplayRecord, byTick, parseReplayFile, replayFile, replayId, saveReplay } from "./sim/replay";
import { downloadText } from "./ui/files";
import {
  ArmoryScreen,
  MenuScreen,
  PostMatchScreen,
  SetupScreen,
  setMouseDown,
  SkirmishConfig,
  type GraphSeries,
} from "./ui/screens";
import { Profile } from "./meta/profile";
import { computeRewards, MatchRewards } from "./meta/progression";
import { Command, applyCommand, worldChecksum } from "./sim/commands";
import { NetSession } from "./net/session";
import { NetLobby, NetStart } from "./ui/net_lobby";
import { Settings, loadSettings, saveSettings } from "./meta/settings";
import { SettingsScreen } from "./ui/settings_screen";
import { setColorblindTeams } from "./render/palette";
import { TeamMetrics, snapshotMetrics, matchReport, MatchReport, emptyMatchReport } from "./sim/metrics";
import { recordMatch, summarise } from "./meta/history";
import { careerLog, careerMatch, recordCareer } from "./meta/career";
import { EarnedAward, evaluateAwards } from "./meta/achievements";
import { drawScoreboard } from "./ui/scoreboard";
import { drawProductionPanel } from "./ui/production_panel";
import { Weather } from "./render/weather";
import { drawChat, ChatLine } from "./ui/chat";
import { KeybindResolver, chordFor, chordLabel, chordOf } from "./meta/keybinds";
import { EditorScreen } from "./ui/editor_screen";
import {
  CustomMap, deserialiseMap, findCustomMap, saveCustomMap, serialiseMap, toMapData, mapPool, rollRandomMap,
} from "./maps/custom";

/** Small drawn cursors (SVG), each with a fallback. */
const svgCursor = (svg: string, x: number, y: number, fallback: string) =>
  `url("data:image/svg+xml;utf8,${encodeURIComponent(svg)}") ${x} ${y}, ${fallback}`;
const CURSORS = {
  attack: svgCursor(`<svg xmlns='http://www.w3.org/2000/svg' width='28' height='28'><g stroke='#1a0d08' stroke-width='3' stroke-linecap='round'><path d='M4 4 L20 20 M20 14 L14 20'/></g><g stroke='#ff6a55' stroke-width='2' stroke-linecap='round'><path d='M4 4 L20 20 M20 14 L14 20'/></g><circle cx='4' cy='4' r='2' fill='#ffd2c8'/></svg>`, 3, 3, "crosshair"),
  gather: svgCursor(`<svg xmlns='http://www.w3.org/2000/svg' width='28' height='28'><path d='M5 23 L17 11' stroke='#2a1a0c' stroke-width='5' stroke-linecap='round'/><path d='M5 23 L17 11' stroke='#b88a52' stroke-width='3' stroke-linecap='round'/><path d='M10 6 Q18 4 24 12' fill='none' stroke='#2a1a0c' stroke-width='5' stroke-linecap='round'/><path d='M10 6 Q18 4 24 12' fill='none' stroke='#d8dde2' stroke-width='3' stroke-linecap='round'/></svg>`, 22, 10, "pointer"),
  build: svgCursor(`<svg xmlns='http://www.w3.org/2000/svg' width='28' height='28'><path d='M6 22 L16 12' stroke='#2a1a0c' stroke-width='5' stroke-linecap='round'/><path d='M6 22 L16 12' stroke='#b88a52' stroke-width='3' stroke-linecap='round'/><rect x='13' y='3' width='12' height='8' rx='1.5' transform='rotate(45 19 7)' fill='#9aa4b0' stroke='#2a1a0c' stroke-width='2'/></svg>`, 20, 5, "pointer"),
};

type AppState = "menu" | "setup" | "armory" | "match" | "postmatch" | "codex" | "settings" | "warband" | "editor" | "factions" | "career" | "nemesis";

// Buildings you can drag-paint into a continuous run.
// Dragged out as one gap-free run. A wall is never one segment, which is why
// these also stay armed between runs (see paintWallLine).
const LINE_BUILDABLE = new Set(["palisade", "stone_wall"]);
// Dragged out as a spaced grid with walking room between. Only the buildings
// you genuinely want several of at once — a drag over anything else places one,
// because nine Town Centres in a grid is a misclick, not a feature.
const BLOCK_BUILDABLE = new Set(["house", "farm"]);

class App {
  canvas: HTMLCanvasElement;
  renderer: Renderer;
  input: Input;
  camera = new Camera();
  particles = new Particles(2400);
  hud = new HUD();
  profile = Profile.load();

  state: AppState = "menu";
  menu = new MenuScreen();
  setup = new SetupScreen();
  armory = new ArmoryScreen();
  postmatch = new PostMatchScreen();
  editorScreen = new EditorScreen();
  codexScreen = new CodexScreen();
  factionBook = new FactionBook();
  careerScreen = new CareerScreen();
  rivalScreen = new RivalScreen();
  /** The caster view, used whenever this client is watching rather than playing. */
  caster = new Caster();
  /** Display names per team for the caster (online names, or realm + AI level). */
  private playerNames: string[] = [];
  /** A replay being played back: orders by tick, where it ends, and what it was. */
  private replay: { rec: ReplayRecord; byTick: Map<number, Command[]>; endTick: number; ended: boolean } | null = null;
  /** How an online match was set up, kept for its replay. */
  private netSetup: NetReplaySetup | null = null;
  /** Broadcast delay for an online caster, in ticks. */
  private casterDelayTicks = 0;
  /** Where the Factions book returns to. */
  private factionsReturn: AppState = "menu";
  settingsScreen = new SettingsScreen();
  warbandScreen = new WarbandScreen();
  warband: WarbandRun | null = null;
  settings: Settings = loadSettings();
  private settingsReturn: AppState = "menu"; // where Back from settings goes

  // Match state
  world: World | null = null;
  ais: SkirmishAI[] = [];
  config: SkirmishConfig | null = null;
  selection: EntityId[] = [];
  controlGroups: EntityId[][] = Array.from({ length: 10 }, () => []);
  lastGroupTap = { idx: -1, time: 0 };
  private keybinds = new KeybindResolver();
  /** Rolling frame/tick costs behind the performance overlay and auto-LOD. */
  private perf = {
    frameMs: 16.7, worstFrameMs: 0, tickMs: 0, worstTickMs: 0,
    ticksThisFrame: 0, ticksLastFrame: 1, slipT: 0, autoLod: false, worstReset: 0,
  };
  /** Where the last thing that happened to you happened (AoE's Space key). */
  private lastEvent: { x: number; y: number } | null = null;
  markers: CommandMarker[] = [];
  placing: string | null = null;
  attackMoveArmed = false;
  powerArmed = false; // commander power placement mode
  ingameMenu = false;
  spectating = false; // watch mode: all teams are AI, no player commands
  showScoreboard = false; // Tab — live multi-team scoreboard overlay
  showProduction = false; // V — production overview panel
  private weather = new Weather();
  // Multiplayer chat (Enter to open) + log.
  private chatOpen = false;
  private chatDraft = "";
  private chatLog: ChatLine[] = [];
  /** Time-series of per-team metrics, sampled through the match, for the graphs. */
  private matchHistory: { t: number; m: TeamMetrics[] }[] = [];
  private nextSampleT = 0;
  /** Aggregated (your alliance vs enemies) series, built at match end. */
  endGraph: GraphSeries = null;
  /** The team this client controls/views. 0 for single-player & the net host;
   *  the net joiner sets it to their team. (Was the old PLAYER constant.) */
  me: Team = Team.Player;
  net: NetSession | null = null; // active lockstep session in multiplayer
  private netAccumulator = 0;
  private netDesyncAlerted = false;
  /** This online match is ranked: report the winner to the server. */
  private netRanked = false;
  private resultSent = false;
  private netFactions: string[] = [];
  /** The hub an online match came from; the lobby reopens there after it. */
  private netHub = "";
  private lobby = new NetLobby();
  paused = false;
  gameSpeed = 1; // 0.5 / 1 / 2 / 3
  private idleVillIndex = 0;
  matchOverTimer = -1;
  playerWon = false;
  combatHeat = 0; // 0..1 battle intensity, drives combat music
  matchRewards: MatchRewards | null = null;
  matchWon = false;
  xpBefore = 0;
  levelsGained = 0;
  /** The whole match, both sides, for the end-of-match report. */
  endReport: MatchReport = emptyMatchReport();
  /** Achievements and challenges this match earned, for the post-match screen. */
  matchAwards: EarnedAward[] = [];
  /** The map this match is being fought on, for the end-of-match report. */
  private mapName = "";
  /** Wheel delta this frame, for out-of-match screens that zoom. */
  private frameWheel = 0;
  /** True when this match was launched from the editor, so we go back there. */
  private editorReturn = false;
  endDuration = 0;

  // Frame-level input flags consumed by UI/world each frame.
  frameClick: { x: number; y: number } | null = null;
  frameDouble: { x: number; y: number } | null = null;
  frameRight: { x: number; y: number } | null = null;
  frameDragEnd: { x0: number; y0: number; x1: number; y1: number } | null = null;

  sfxCooldown = new Map<string, number>();
  time = 0;
  showDamageNumbers = true;
  private fps = 60; // smoothed frames-per-second for the optional overlay
  private smokeTimer = 0;
  private accumulator = 0;
  /** Every order this match has been given — half of what a save is. */
  private cmdLog = new CommandLog();
  /** Set while a resume is fast-forwarding, so the match loop stays paused. */
  private loadingSave = false;
  private lastFrame = performance.now();

  constructor() {
    const root = document.getElementById("app")!;
    this.canvas = document.createElement("canvas");
    root.appendChild(this.canvas);
    this.renderer = new Renderer(this.canvas);
    this.input = new Input(this.canvas);
    this.resize();
    window.addEventListener("resize", () => this.resize());
    window.addEventListener("orientationchange", () => setTimeout(() => this.resize(), 80));
    window.visualViewport?.addEventListener("resize", () => this.resize()); // mobile toolbar show/hide
    // Entering or leaving fullscreen changes the viewport; most browsers also
    // fire resize, but not reliably on every platform, so listen to the source.
    document.addEventListener("fullscreenchange", () => this.resize());
    // Drop a replay file anywhere on the game to watch it.
    window.addEventListener("dragover", (e) => { if (e.dataTransfer?.types?.includes("Files")) e.preventDefault(); });
    window.addEventListener("drop", (e) => {
      const f = e.dataTransfer?.files?.[0];
      if (!f) return;
      e.preventDefault();
      if (this.state === "match" && !this.replay) { this.hud.addAlert("Finish or leave this match to open a replay."); return; }
      void f.text().then((text) => this.openReplayText(f.name, text));
    });
    document.addEventListener("webkitfullscreenchange", () => this.resize());
    this.wireInput();
    this.applySettings();
    // Load any Meshy-baked sprites (no-op if none generated yet); procedural art
    // fills in until each image is ready, so we don't block startup on it.
    loadSprites().then((n) => { if (n) console.log(`[sprites] loaded ${n} baked models`); });
    requestAnimationFrame(() => this.frame());
  }

  resize() {
    const vw = Math.max(1, window.innerWidth);
    const vh = Math.max(1, window.innerHeight);
    const aspect = vw / vh;
    const DESIGN = 760; // the UI's design space on its short axis
    let cw = vw, ch = vh;
    // On small / mobile screens the fixed-layout UI needs more room than the
    // device gives it, so render at a virtual resolution (short side = DESIGN)
    // and CSS-scale the canvas down to fit. Input is mapped back accordingly.
    if (Math.min(vw, vh) < DESIGN) {
      if (aspect >= 1) { ch = DESIGN; cw = Math.round(DESIGN * aspect); }
      else { cw = DESIGN; ch = Math.round(DESIGN / aspect); }
      this.canvas.style.width = vw + "px";
      this.canvas.style.height = vh + "px";
    } else {
      this.canvas.style.width = "";
      this.canvas.style.height = "";
    }
    this.canvas.width = cw;
    this.canvas.height = ch;
    this.camera.setViewport(cw, ch);
  }

  // ----------------------------------------------------------- input wiring --

  wireInput() {
    this.input.onLeftClick = (x, y) => {
      audio.resume();
      // Fullscreen has to be requested *here*, inside the click — by the time
      // the next frame interprets frameClick the gesture is over and Safari
      // refuses. The button drew its rect last frame; if the click is on it,
      // act now. The frame still sees the click land on the button, which is
      // what stops it falling through to the world underneath.
      if (ui.gestureAt(x, y) === FULLSCREEN_ZONE) toggleFullscreen();
      this.frameClick = { x, y };
    };
    this.input.onLeftDouble = (x, y) => {
      this.frameDouble = { x, y };
      this.frameClick = { x, y }; // double also counts as a click for UI
    };
    this.input.onRightClick = (x, y) => {
      this.frameRight = { x, y };
    };
    this.input.onDragEnd = (box) => {
      this.frameDragEnd = { x0: box.x0, y0: box.y0, x1: box.x1, y1: box.y1 };
      // Outside a match, screens are tap/drag UIs (no box-select), so a drag's
      // release should also count as a click at the end point — that's what makes
      // touch (and mouse) drag-and-drop in Warband Tactics resolve on release.
      if (this.state !== "match") this.frameClick = { x: box.x1, y: box.y1 };
    };
    this.input.onWheel = (x, y, delta) => {
      if (this.state === "match") {
        this.camera.zoomAt(x, y, delta > 0 ? 0.88 : 1.14);
      } else {
        // Screens that zoom (the map editor) read this off the frame input.
        this.frameWheel = delta;
      }
    };
    // Two-finger gestures: pan + pinch-zoom the match camera.
    this.input.onPan = (dx, dy) => {
      if (this.state === "match") this.camera.pan(-dx / this.camera.zoom, -dy / this.camera.zoom);
    };
    this.input.onPinch = (cx, cy, factor) => {
      if (this.state === "match") this.camera.zoomAt(cx, cy, factor);
    };
    this.input.onKeyDown = (key) => this.handleKey(key);
  }

  handleKey(key: string) {
    // Fullscreen works on every screen, so it is checked before any screen
    // gets the key — except a rebind in progress, which owns every chord.
    // This runs synchronously inside the keydown event, which is what lets
    // the browser honour the request.
    if (!(this.state === "settings" && this.settingsScreen.isListening())) {
      const fsChord = chordOf(key, { ctrl: this.input.ctrl, shift: this.input.shift, alt: this.input.alt });
      if (fsChord && this.keybinds.resolve(this.settings.keybinds, fsChord) === "fullscreen") {
        toggleFullscreen();
        return;
      }
    }
    // Warband Tactics has one typed field (the ground seed); give it first
    // refusal on every key while that screen is up.
    if (this.state === "warband") { this.warbandScreen.handleKey(key); return; }
    if (this.state === "editor" && this.editorScreen.handleKey(key)) return;
    // The settings screen swallows everything while it is listening for a
    // rebind, so a player can bind Escape or Tab without triggering them.
    if (this.state === "settings" && this.settingsScreen.captureKey(key, this.input)) return;
    if (this.state !== "match" || !this.world) return;
    // Chat capture takes precedence over every game hotkey while typing.
    if (this.chatOpen) { this.handleChatKey(key); return; }
    // The caster's keys come first while watching (digits pick a player's vision).
    if (this.spectating && !this.ingameMenu && this.caster.key(key, this.world)) return;

    const chord = chordOf(key, { ctrl: this.input.ctrl, shift: this.input.shift, alt: this.input.alt });
    if (!chord) return; // a bare modifier
    const action = this.keybinds.resolve(this.settings.keybinds, chord);

    // Control groups are positional rather than bound, exactly as they are in
    // Age of Empires: Ctrl sets, Shift adds, a bare digit selects, and tapping
    // the same digit twice takes the camera to the group.
    const digit = /^(?:Ctrl\+|Shift\+|Alt\+)*([0-9])$/.exec(chord);
    if (digit && !this.ingameMenu && !this.spectating) { this.controlGroupKey(parseInt(digit[1], 10)); return; }

    if (action === "menu") {
      if (this.hud.oathPicker.isOpen) { this.hud.oathPicker.close(); return; }
      if (this.spectating) this.exitToMenu();
      else if (this.placing) this.placing = null;
      else if (this.hud.buildMenuOpen) { this.hud.buildMenuOpen = false; this.hud.buildCategory = null; }
      else if (this.powerArmed) this.powerArmed = false;
      else if (this.attackMoveArmed) this.attackMoveArmed = false;
      else this.ingameMenu = !this.ingameMenu;
      return;
    }
    if (action === "chat" && this.net) { this.chatOpen = true; return; }
    if (this.ingameMenu) return;

    // These work in every mode, spectating included.
    switch (action) {
      case "pause": this.togglePause(); return;
      case "speedUp": this.cycleSpeed(1); return;
      case "speedDown": this.cycleSpeed(-1); return;
      case "scoreboard": this.showScoreboard = !this.showScoreboard; return;
      case "productionPanel": this.showProduction = !this.showProduction; return;
      case "perfOverlay":
        this.settings.perfOverlay = !this.settings.perfOverlay;
        this.hud.addAlert(this.settings.perfOverlay ? "Performance overlay on" : "Performance overlay off");
        saveSettings(this.settings);
        return;
      case "cameraLastEvent": this.goToLastEvent(); return;
    }
    if (this.spectating) return; // spectators only watch — no army commands

    switch (action) {
      case "attackMove": this.attackMoveArmed = true; break;
      case "stop": this.dispatch({ t: "stop", team: this.me, ids: this.playerSelection().map((e) => e.id) }); break;
      case "hold": this.dispatch({ t: "hold", team: this.me, ids: this.playerSelection().map((e) => e.id) }); break;
      case "ability": this.controller.useAbility(); break;
      case "buildMenu":
        if (!this.playerSelection().some((e) => UNITS[e.type]?.canBuild)) { this.hud.addAlert("Select a villager to build."); break; }
        this.hud.buildMenuOpen = true; this.hud.buildCategory = null;
        break;
      case "garrison": this.garrisonSelected(); break;
      case "cycleStance": this.cycleStance(); break;
      case "commanderPower": this.armCommanderPower(); break;
      case "deleteUnit": this.deleteSelection(); break;
      case "idleVillagerNext": this.selectIdleVillager(1); break;
      case "idleVillagerPrev": this.selectIdleVillager(-1); break;
      case "selectArmy": this.selectAllArmy(); break;
      case "selectTownCentre": this.selectTownCentre(true); break;
      case "cameraTownCentre": this.selectTownCentre(false); break;
    }
  }

  /** Ctrl sets, Shift adds, a bare digit selects; twice re-centres. */
  private controlGroupKey(idx: number) {
    if (!this.world) return;
    const live = (ids: EntityId[]) => ids.filter((id) => this.world!.byId.get(id)?.alive);
    if (this.input.ctrl || this.input.alt) {
      this.controlGroups[idx] = this.playerSelection().map((e) => e.id);
      this.hud.addAlert(`Group ${idx} set (${this.controlGroups[idx].length})`);
      return;
    }
    if (this.input.shift) {
      const merged = new Set([...live(this.controlGroups[idx]), ...this.playerSelection().map((e) => e.id)]);
      this.controlGroups[idx] = [...merged];
      this.hud.addAlert(`Group ${idx} now ${this.controlGroups[idx].length}`);
      return;
    }
    const ids = live(this.controlGroups[idx]);
    if (!ids.length) return;
    const now = performance.now();
    this.select(ids);
    if (this.lastGroupTap.idx === idx && now - this.lastGroupTap.time < 350) {
      const first = this.world.byId.get(ids[0])!;
      this.camera.centerOn(first.x, first.y);
    }
    this.lastGroupTap = { idx, time: now };
  }

  /** Disband the selection — your own units and buildings only. */
  deleteSelection() {
    const ids = this.playerSelection().map((e) => e.id);
    if (!ids.length) return;
    this.dispatch({ t: "delete", team: this.me, ids });
    this.hud.addAlert(`Disbanded ${ids.length}`);
    audio.play("command");
  }

  /** H — the Town Centre, selected and centred (AoE's most-worn key). */
  selectTownCentre(alsoSelect: boolean) {
    if (!this.world) return;
    const tc = this.world.entitiesOf(this.me, Kind.Building).find((b) => b.type === "town_center");
    if (!tc) { this.hud.addAlert("No Town Centre"); return; }
    if (alsoSelect) this.select([tc.id]);
    this.camera.centerOn(tc.x, tc.y);
  }

  /** Space — jump to whatever last happened to you. */
  goToLastEvent() {
    if (!this.lastEvent) { this.hud.addAlert("Nothing has happened yet"); return; }
    this.camera.centerOn(this.lastEvent.x, this.lastEvent.y);
  }

  /**
   * Garrison (G): tuck the selected units into a building, or eject if only a
   * building is selected. Targets the selected building when it can hold troops,
   * otherwise the nearest friendly building with free space. Any unit can
   * garrison — villagers included — so they can take cover from a raid.
   */
  /** Y — cycle the selected units' combat stance. */
  cycleStance() {
    const units = this.playerSelection().filter((e) => e.kind === Kind.Unit);
    if (!units.length) return;
    const next = (((units[0].stance as number) + 1) % 5) as Stance;
    this.dispatch({ t: "stance", team: this.me, ids: units.map((e) => e.id), stance: next });
    this.hud.addAlert(`Stance: ${["⚔ Aggressive", "🛡 Defensive", "⚑ Stand Ground", "✋ Passive", "🏹 Skirmish"][next]}`);
    audio.play("command");
  }

  garrisonSelected() {
    if (!this.world) return;
    const sel = this.playerSelection();
    const units = sel.filter((e) => e.kind === Kind.Unit && e.team === this.me);
    const selBuilding = sel.find((e) => e.kind === Kind.Building && e.team === this.me);
    const cap = (e: Entity) => BUILDINGS[e.type]?.garrisonCap ?? 0;

    // Just a building selected (no troops to load) → eject its garrison.
    if (selBuilding && units.length === 0) {
      if (cap(selBuilding) > 0 && selBuilding.garrison.length > 0) {
        this.world.ungarrison(selBuilding.id);
        this.hud.addAlert("Garrison ejected.");
        audio.play("command");
      }
      return;
    }
    if (units.length === 0) return;

    // Prefer the selected building; else the nearest friendly one with room.
    let target: Entity | null = selBuilding && cap(selBuilding) > 0 ? selBuilding : null;
    if (!target) {
      let cx = 0;
      let cy = 0;
      for (const u of units) { cx += u.x; cy += u.y; }
      cx /= units.length;
      cy /= units.length;
      let bestD = Infinity;
      for (const e of this.world.entities) {
        if (!e.alive || e.kind !== Kind.Building || e.team !== this.me) continue;
        if (e.buildState !== BuildState.Done || cap(e) <= 0 || e.garrison.length >= cap(e)) continue;
        const d = Math.hypot(e.x - cx, e.y - cy);
        if (d < bestD) { bestD = d; target = e; }
      }
    }
    if (!target) { this.hud.addAlert("No garrison-capable building nearby."); return; }
    this.dispatch({ t: "garrison", team: this.me, ids: units.map((u) => u.id), buildingId: target.id });
    this.hud.addAlert(`Garrisoning into ${BUILDINGS[target.type]?.name ?? "building"}…`);
    audio.play("command");
  }

  // --------------------------------------------------------------- selection --

  playerSelection(): Entity[] {
    if (!this.world) return [];
    const out: Entity[] = [];
    for (const id of this.selection) {
      const e = this.world.byId.get(id);
      if (e && e.alive && e.team === this.me) out.push(e);
    }
    return out;
  }

  /** Everything currently selected, any team — so you can inspect (e.g. click
   *  an enemy unit to read its health/stats). Commands still use playerSelection. */
  selectedEntities(): Entity[] {
    if (!this.world) return [];
    const out: Entity[] = [];
    for (const id of this.selection) {
      const e = this.world.byId.get(id);
      if (e && e.alive) out.push(e);
    }
    return out;
  }

  select(ids: EntityId[]) {
    if (!this.world) return;
    for (const id of this.selection) {
      const e = this.world.byId.get(id);
      if (e) e.selected = false;
    }
    this.selection = ids;
    for (const id of ids) {
      const e = this.world.byId.get(id);
      if (e) e.selected = true;
    }
    if (ids.length) audio.play("select");
  }

  // ------------------------------------------------------------- match setup --

  /**
   * A 1 v 1 against your Nemesis, on a random battlefield, at the strength of
   * its rank, playing what it has learned about you. Everything it needs goes
   * into the config, so a save or a replay rebuilds the same rival.
   */
  startRivalMatch() {
    const mine = this.profile.playableFaction() ?? DEFAULT_FACTION;
    const r = loadRival(mine).current;
    const mem = memoryOf(r, careerLog());
    const seed = (Math.random() * 0x7fffffff) | 0;
    const maps = PRESETS.filter((p) => !["islands", "survival_arena"].includes(p.id));
    const diff = RANK_DIFFICULTY[r.rank] ?? "knight";
    this.startMatch({
      presetId: maps[seed % maps.length].id, seed, difficulty: diff, aiDifficulties: ["", diff], fairMode: false,
      players: 2, teams: [0, 0], commander: "", faction: mine, aiFactions: ["", r.faction], nomad: false, mode: "conquest",
      rival: { name: r.name, epithet: r.epithet, rank: r.rank, adaptation: adaptationFor(r, mem) },
    });
    // It speaks first — the proof that it remembers.
    this.addChatLine(`${r.name} ${r.epithet}`, taunt(r, mem), 1 as Team);
  }

  startMatch(config: SkirmishConfig) {
    this.config = config;
    // Each bot can run a different personality; fall back to the default.
    const diffFor = (t: number) => DIFFICULTIES[config.aiDifficulties?.[t] ?? config.difficulty] ?? DIFFICULTIES[config.difficulty];
    const diff = DIFFICULTIES[config.difficulty];
    const mode = config.mode ?? "conquest";
    // Survival is co-op: the chosen player count is your side (all allied), and
    // one extra "horde" team is appended for the waves.
    const side = Math.max(2, Math.min(mode === "survival" ? MAX_TEAMS - 1 : MAX_TEAMS, config.players ?? 2));
    const numPlayers = mode === "survival" ? side + 1 : side;
    const hordeTeam = mode === "survival" ? side : -1;
    // Alliances: survival = all players vs the horde; even-teams = two sides; else FFA.
    let alliances: number[] | undefined;
    if (mode === "survival") alliances = Array.from({ length: numPlayers }, (_, t) => (t === hordeTeam ? 1 : 0));
    // Teams as chosen on the roster, seat by seat (ui/teams.ts).
    else if (config.teams?.some((t) => t > 0)) alliances = alliancesFor(resizeTeams(config.teams, numPlayers));
    const map = this.resolveMap(config.presetId, config.seed, numPlayers, config.nomad, alliances);
    const world = new World(config.seed);
    // Team 0 is the human; the rest are AI (allies or opponents), plus the horde.
    // The human's unit rarities and boons are part of the match: written into
    // the config, so a save or a replay rebuilds exactly what was played even
    // after the collection changes.
    const replaying = this.loadingSave && !!config.humanLoadout;
    const loadouts = [replaying ? { ...config.humanLoadout! } : this.profile.matchLoadout(config.fairMode)];
    const econMults = [1];
    const commanders = [config.commander || this.profile.data.commander];
    const boonLoadouts: { id: string; rarity: number; age: number }[][] = [replaying ? [...(config.humanBoons ?? [])] : config.fairMode ? [] : this.profile.equippedBoonPlan()];
    config.humanLoadout = { ...loadouts[0] };
    config.humanBoons = [...boonLoadouts[0]];
    config.commander = commanders[0];
    const setupRng = new RNG(config.seed ^ 0x5eed);
    for (let t = 1; t < numPlayers; t++) {
      loadouts.push(this.profile.matchLoadout(true));
      econMults.push(t === hordeTeam ? diff.econMult : diffFor(t).econMult);
      // Seeded, not Math.random(). A match has to be reproducible from its
      // config alone — that is what makes a save a seed plus a command log
      // rather than a dump of every entity — and an AI that drew a different
      // commander on reload would fight a different battle.
      commanders.push(COMMANDER_IDS[setupRng.int(0, COMMANDER_IDS.length - 1)]);
      boonLoadouts.push([]);
    }
    // Resolved once and written back, so a save (which stores this config)
    // rebuilds exactly these factions even if the profile's pick changes.
    const factions = this.factionsFor(config, numPlayers, true);
    config.faction = factions[0];
    config.aiFactions = factions.map((f, t) => (t === 0 ? "" : f));
    world.init(map, loadouts, econMults, alliances, commanders, config.nomad, boonLoadouts, mode, factions);
    this.world = world;
    this.ais = [];
    for (let t = 1; t < numPlayers; t++) {
      if (t === hordeTeam) continue; // the horde has no brain — the sim spawns its waves
      const ai = new SkirmishAI(world, t as Team, diffFor(t));
      // Your Nemesis plays what it has learned about you.
      if (config.rival && t === 1) ai.adapt(config.rival.adaptation);
      this.ais.push(ai);
    }
    this.playerNames = Array.from({ length: numPlayers }, (_, t) =>
      t === 0 ? this.profile.data.name : t === hordeTeam ? "The Horde"
        : config.rival && t === 1 ? `${config.rival.name} ${config.rival.epithet}`
        : `${PAL.teams[t % PAL.teams.length].name} · ${diffFor(t).name}`);
    this.replay = null;
    this.renderer.prepare(map);
    this.mapName = map.name;
    this.weather.configure(map.seed, map.name);
    this.renderer.clearFx();
    this.hud.prepare(map);
    this.particles.clear();
    this.selection = [];
    this.controlGroups = Array.from({ length: 10 }, () => []);
    this.markers = [];
    this.placing = null;
    this.attackMoveArmed = false;
    this.ingameMenu = false;
    this.matchOverTimer = -1;
    this.matchRewards = null;
    this.camera.setWorld(map.worldW, map.worldH);
    this.camera.zoom = 1;
    this.camera.centerOn(map.starts[this.me].x, map.starts[this.me].y);
    this.accumulator = 0;
    this.spectating = false;
    this.cmdLog.clear();
    this.resetMatchTelemetry();
    this.endNet();
    this.me = Team.Player;
    this.state = "match";
    this.hud.addAlert(config.rival ? `${map.name} — your Nemesis, ${config.rival.name} ${config.rival.epithet}, awaits.` : `${map.name} — vs ${diff.name}. Your villagers await orders!`);
    audio.play("complete");
  }

  /**
   * Write the match out: its setup, every order given, and a hash of where the
   * sim actually is. Custom maps travel as a share code rather than an id,
   * since the map library can be edited or emptied between save and load.
   */
  saveMatch(label?: string): boolean {
    const world = this.world;
    if (!world || !this.config) return false;
    if (this.net) {
      // Resuming one side of a lockstep match would desync everyone else.
      this.hud.addAlert("Multiplayer matches can't be saved.");
      return false;
    }
    const custom = this.config.presetId.startsWith("custom_")
      ? findCustomMap(this.config.presetId)
      : null;
    const save: SaveGame = {
      version: SAVE_FORMAT_VERSION,
      savedAt: Date.now(),
      label: label ?? `${this.mapName || "Skirmish"} — ${mmss(world.time)}`,
      setup: { ...this.config },
      mapCode: custom ? serialiseMap(custom) : undefined,
      tick: world.tickCount,
      commands: this.cmdLog.entries,
      checksum: worldChecksum(world),
      summary: {
        mapName: this.mapName || "Skirmish",
        mode: this.config.mode ?? "conquest",
        players: this.config.players ?? 2,
        difficulty: this.config.difficulty ?? "knight",
        elapsed: world.time,
      },
    };
    const ok = writeSave(save);
    this.hud.addAlert(ok ? `Saved — ${save.label}` : "Couldn't save (storage full?)");
    audio.play(ok ? "complete" : "ui");
    return ok;
  }

  /**
   * Rebuild a saved match by replaying it.
   *
   * The setup is re-run through the normal startMatch path — same seed, same
   * world, same AI — and then the order log is fast-forwarded through it. If
   * the result doesn't hash to what was saved, say so *now*: a divergence is
   * only recoverable while the player still knows the game isn't the one they
   * left, and playing an hour on top of a wrong resume is the bad outcome.
   */
  loadMatch(save: SaveGame): boolean {
    // A custom map may have been edited or deleted since; the save carries its
    // own copy, so restore that first and point the config at it.
    const setup = { ...(save.setup as SkirmishConfig) };
    if (save.mapCode) {
      const m = deserialiseMap(save.mapCode);
      if (!m) {
        this.hud.addAlert("That save's map couldn't be read.");
        return false;
      }
      m.id = setup.presetId; // keep the id the save was taken against
      m.published = undefined; // keep whether the library had it published
      saveCustomMap(m);
    }
    this.loadingSave = true;
    this.startMatch(setup);
    const world = this.world;
    if (!world) { this.loadingSave = false; return false; }
    const res = replayTo(world, this.ais, save.commands, save.tick, save.checksum);
    this.cmdLog.load(save.commands);
    this.loadingSave = false;
    this.accumulator = 0;
    this.camera.centerOn(world.map.starts[this.me].x, world.map.starts[this.me].y);
    if (!res.faithful) {
      this.hud.addAlert("This save was made by a different version — the match may differ.");
    } else {
      this.hud.addAlert(`Resumed at ${mmss(world.time)}.`);
    }
    return true;
  }

  /** Tear down any active net session/link (called when leaving a net match). */
  private endNet() {
    if (this.net) {
      try { this.net.transport.close(); } catch { /* */ }
      this.net = null;
    }
    this.netDesyncAlerted = false;
  }

  /** Start a networked match (server-relayed 2–16 players, or serverless 1v1)
   *  once the lobby has connected. Every client builds the identical world from
   *  the shared seed (fair all-Common, no commanders/boons, given alliances) and
   *  drives it under lockstep. */
  startNetMatch(start: NetStart) {
    const { transport, localTeam, teams, alliances, seed, numTeams } = start;
    // The host's battlefield: a published map travels as its code, so every
    // client builds it identically even if nobody else has it saved.
    const custom = start.map?.code ? deserialiseMap(start.map.code) : null;
    const map = custom
      ? toMapData(custom, seed, numTeams, false)
      : generateMap(PRESETS.some((p) => p.id === start.map?.id) || start.map?.id === "random" ? start.map!.id : "open_plains", seed, numTeams, false, alliances);
    const world = new World(seed);
    const loadouts = teams.map(() => this.profile.matchLoadout(true));
    const econMults = teams.map(() => 1);
    const commanders = teams.map(() => "");
    // Each realm's chosen faction; anyone who didn't choose gets one from the seed.
    const rng = new RNG((seed ^ 0xfac7105) >>> 0);
    const factions = teams.map((_, t) => {
      const roll = FACTION_IDS[rng.int(0, FACTION_IDS.length - 1)];
      const f = start.factions?.[t];
      return f && f in FACTIONS ? f : roll;
    });
    world.init(map, loadouts, econMults, alliances, commanders, false, undefined, "conquest", factions);
    // Tell the server which factions were really played ("Random" resolved), for its stats.
    this.netFactions = factions;
    try { transport.send({ t: "factions", list: factions }); } catch { /* stats only */ }
    if (start.observer) world.revealAll = true; // casters see the whole board
    this.world = world;
    this.ais = [];
    this.net = new NetSession(transport, localTeam, teams, start.observer);
    this.me = localTeam;
    this.spectating = !!start.observer; // no commands, full vision, caster HUD
    this.net.attach(world, 5);
    // Every order the lockstep applies is kept: that is the match's replay.
    if (this.net.lock) this.net.lock.record = [];
    this.netSetup = { seed, numTeams, alliances: [...alliances], factions: [...factions], map: { id: start.map?.id ?? "open_plains", name: map.name, ...(start.map?.code ? { code: start.map.code } : {}) } };
    this.playerNames = teams.map((_, t) => start.names?.[t] || PAL.teams[t % PAL.teams.length].name);
    this.casterDelayTicks = start.observer ? Math.round((start.delaySec ?? 0) * SIM_HZ) : 0;
    this.caster.reset();
    this.caster.delaySec = start.observer ? start.delaySec ?? 0 : 0;
    this.replay = null;
    this.net.onChat = (m) => { if (m.text) this.addChatLine(m.name || teamLabel((m.team ?? 0) as Team), m.text, (m.team ?? 0) as Team); };
    this.net.onPing = (m) => this.remotePing(m.x ?? 0, m.y ?? 0, (m.team ?? 0) as Team);
    this.net.onAnnounce = (text) => this.hud.addAlert(`📣 ${text}`);
    this.net.onRated = (m) => {
      const won = m.winner !== undefined && world.alliances[this.me] === m.winner;
      this.hud.addAlert(`⚖ Ranked result recorded — rating ${won ? "+" : "−"}${m.delta ?? 0}`);
    };
    this.netRanked = !!start.ranked;
    this.netHub = start.online ?? "";
    this.resultSent = false;
    transport.onClose = () => this.hud.addAlert("⚠ Connection lost.");
    this.renderer.prepare(map);
    this.mapName = map.name;
    this.weather.configure(map.seed, map.name);
    this.renderer.clearFx();
    this.hud.prepare(map);
    this.particles.clear();
    this.selection = [];
    this.controlGroups = Array.from({ length: 10 }, () => []);
    this.markers = [];
    this.placing = null;
    this.attackMoveArmed = false;
    this.powerArmed = false;
    this.paused = false;
    this.gameSpeed = 1;
    this.ingameMenu = false;
    this.matchOverTimer = -1;
    this.matchRewards = null;
    this.netAccumulator = 0;
    this.netDesyncAlerted = false;
    this.resetMatchTelemetry();
    this.camera.setWorld(map.worldW, map.worldH);
    this.camera.zoom = 1;
    this.camera.centerOn(start.observer ? map.worldW / 2 : map.starts[this.me].x, start.observer ? map.worldH / 2 : map.starts[this.me].y);
    this.state = "match";
    this.hud.addAlert(start.observer ? "👁 Observing — the match is underway." : "🔗 Connected — good luck!");
    audio.play("complete");
  }

  /**
   * Watch mode: every team is AI-controlled and the whole map is revealed. We
   * reuse the setup config (map, players, difficulty, alliances) but give team 0
   * a brain too and award no rewards — it's purely for watching.
   */
  startSpectate(config: SkirmishConfig) {
    this.config = config;
    const diffFor = (t: number) => DIFFICULTIES[config.aiDifficulties?.[t] ?? config.difficulty] ?? DIFFICULTIES[config.difficulty];
    const mode = config.mode ?? "conquest";
    // Mirror startMatch's mode setup so Watch mode honours KotH / Regicide /
    // Survival (previously spectate always fell back to Conquest).
    const side = Math.max(2, Math.min(mode === "survival" ? MAX_TEAMS - 1 : MAX_TEAMS, config.players ?? 2));
    const numPlayers = mode === "survival" ? side + 1 : side;
    const hordeTeam = mode === "survival" ? side : -1;
    let alliances: number[] | undefined;
    if (mode === "survival") alliances = Array.from({ length: numPlayers }, (_, t) => (t === hordeTeam ? 1 : 0));
    // Teams as chosen on the roster, seat by seat (ui/teams.ts).
    else if (config.teams?.some((t) => t > 0)) alliances = alliancesFor(resizeTeams(config.teams, numPlayers));
    const map = this.resolveMap(config.presetId, config.seed, numPlayers, config.nomad, alliances);
    const world = new World(config.seed);
    const loadouts: Record<string, number>[] = [];
    const econMults: number[] = [];
    const commanders: string[] = [];
    const spectateRng = new RNG(config.seed ^ 0x5eed);
    for (let t = 0; t < numPlayers; t++) {
      loadouts.push(this.profile.matchLoadout(true)); // fair, all-Common loadouts
      econMults.push(t === hordeTeam ? 1 : diffFor(t).econMult);
      // Seeded, so a watched game can be replayed exactly.
      commanders.push(COMMANDER_IDS[spectateRng.int(0, COMMANDER_IDS.length - 1)]);
    }
    const factions = this.factionsFor(config, commanders.length, false);
    config.aiFactions = factions;
    world.init(map, loadouts, econMults, alliances, commanders, config.nomad, undefined, mode, factions);
    world.revealAll = true; // spectators see the entire battlefield
    this.world = world;
    this.ais = [];
    for (let t = 0; t < numPlayers; t++) {
      if (t === hordeTeam) continue; // the horde is sim-driven, no brain
      this.ais.push(new SkirmishAI(world, t as Team, diffFor(t)));
    }
    this.renderer.prepare(map);
    this.mapName = map.name;
    this.weather.configure(map.seed, map.name);
    this.renderer.clearFx();
    this.hud.prepare(map);
    this.particles.clear();
    this.selection = [];
    this.controlGroups = Array.from({ length: 10 }, () => []);
    this.markers = [];
    this.placing = null;
    this.attackMoveArmed = false;
    this.ingameMenu = false;
    this.matchOverTimer = -1;
    this.matchRewards = null;
    this.camera.setWorld(map.worldW, map.worldH);
    this.camera.zoom = 0.85;
    this.camera.centerOn(map.worldW / 2, map.worldH / 2);
    this.accumulator = 0;
    this.spectating = true;
    this.resetMatchTelemetry();
    this.endNet();
    this.me = Team.Player;
    this.state = "match";
    this.playerNames = Array.from({ length: numPlayers }, (_, t) => t === hordeTeam ? "The Horde" : `${PAL.teams[t % PAL.teams.length].name} · ${diffFor(t).name}`);
    this.caster.reset();
    this.hud.addAlert(`👁 Casting — ${map.name}, ${numPlayers} AI realms. Press ? for caster keys.`);
    audio.play("complete");
  }

  // The HUD acts through this controller.
  controller: MatchController = {
    trainUnit: (b, type) => {
      // Queue the unit in every selected production building of this type (so
      // double-click-select all your stables, then mass-produce in one click).
      const sameType = this.playerSelection().filter((e) => e.kind === Kind.Building && e.type === b.type);
      const targets = sameType.length ? sameType : [b];
      for (const bld of targets) this.dispatch({ t: "train", team: this.me, buildingId: bld.id, unit: type });
      audio.play("ui");
    },
    research: (b, techId) => {
      this.dispatch({ t: "research", team: this.me, buildingId: b.id, tech: techId });
      audio.play("ui");
    },
    startPlacement: (type) => {
      this.placing = type;
      audio.play("ui");
    },
    ungarrison: (b) => {
      this.dispatch({ t: "ungarrison", team: this.me, buildingId: b.id });
      audio.play("command");
    },
    toggleGate: (b) => {
      this.dispatch({ t: "gate", team: this.me, buildingId: b.id });
      audio.play("build");
    },
    setAutoReseed: (on) => {
      this.dispatch({ t: "autoreseed", team: this.me, on });
      audio.play("ui");
    },
    trade: (action) => {
      this.dispatch({ t: "trade", team: this.me, action });
      audio.play("coin");
    },
    stopSelection: () => {
      this.dispatch({ t: "stop", team: this.me, ids: this.playerSelection().map((e) => e.id) });
      audio.play("command");
    },
    holdSelection: () => {
      this.dispatch({ t: "hold", team: this.me, ids: this.playerSelection().map((e) => e.id) });
      audio.play("command");
    },
    setStance: (stance) => {
      const ids = this.playerSelection().filter((e) => e.kind === Kind.Unit).map((e) => e.id);
      if (!ids.length) return;
      this.dispatch({ t: "stance", team: this.me, ids, stance });
      audio.play("command");
    },
    setAttackMoveMode: () => {
      this.attackMoveArmed = true;
    },
    garrisonSelection: () => this.garrisonSelected(),
    useAbility: () => {
      const ids = this.playerSelection().filter((e) => e.kind === Kind.Unit).map((e) => e.id);
      if (ids.length) this.dispatch({ t: "ability", team: this.me, ids });
      audio.play("command");
    },
    minimapNavigate: (wx, wy) => this.camera.centerOn(wx, wy),
    minimapCommand: (wx, wy) => this.issueContextCommand(wx, wy, null),
    minimapPing: (wx, wy) => this.dropPing(wx, wy),
    cancelProduction: (b, index) => {
      this.dispatch({ t: "cancel", team: this.me, buildingId: b.id, index });
      audio.play("ui");
    },
    jumpTo: (x, y) => { this.camera.centerOn(x, y); audio.play("ui"); },
    narrowSelection: (type, remove) => {
      const keep = this.selectedEntities().filter((e) => (remove ? e.type !== type : e.type === type));
      if (keep.length) this.select(keep.map((e) => e.id));
      audio.play("select");
    },
    openMenu: () => {
      this.ingameMenu = true;
    },
  };

  // -------------------------------------------------------- world interaction --

  /** Snapped, de-duplicated tile centres along a drag, for wall painting/preview. */
  wallLinePoints(wx0: number, wy0: number, wx1: number, wy1: number): { x: number; y: number }[] {
    return computeWallLine(wx0, wy0, wx1, wy1, TILE);
  }

  /**
   * Where a drag would put buildings, whichever kind is being placed: a gap-free
   * run for walls, a spaced grid for everything else. One function so the ghost
   * preview and the actual placement can never disagree about the answer.
   */
  dragPlacements(type: string, wx0: number, wy0: number, wx1: number, wy1: number) {
    if (LINE_BUILDABLE.has(type)) return computeWallLine(wx0, wy0, wx1, wy1, TILE);
    if (BLOCK_BUILDABLE.has(type)) return blockPoints(wx0, wy0, wx1, wy1, TILE, BUILDINGS[type]?.tiles ?? 2);
    // Everything else: a drag is just a click that wandered. Place one, where
    // the button came up.
    return [{ x: wx1, y: wy1 }];
  }

  /** The fullscreen hotkey as the player has it bound, for tooltips. */
  fullscreenKeyLabel(): string {
    return chordLabel(chordFor(this.settings.keybinds, "fullscreen"));
  }

  /** Funnel every player action through here: queued for lockstep in a net game,
   *  applied immediately in single-player. Keeps both paths in one place. */
  dispatch(cmd: Command) {
    if (this.net?.lock) this.net.lock.localCommand(cmd);
    else if (this.world) {
      // Log before applying, stamped with the tick it will land on. A save is
      // the seed plus this log, so anything that reaches the world and isn't
      // here is a divergence waiting to happen on reload.
      this.cmdLog.record(this.world.tickCount, cmd);
      applyCommand(this.world, cmd);
    }
  }

  /**
   * Drag-release while a building is selected: lay the whole run at once — a
   * line of wall, or a block of houses/farms.
   */
  paintWallLine(box: { x0: number; y0: number; x1: number; y1: number }) {
    if (!this.world || !this.placing) return;
    const pts = this.dragPlacements(
      this.placing,
      this.camera.screenToWorldX(box.x0), this.camera.screenToWorldY(box.y0),
      this.camera.screenToWorldX(box.x1), this.camera.screenToWorldY(box.y1),
    );
    const villagers = this.playerSelection().filter((e) => UNITS[e.type]?.canBuild);
    const world = this.world;
    const cost = world.buildingCostFor(this.me, this.placing);
    let budget = { ...world.player(this.me).resources };
    let placed = 0, lastProblem = "";
    pts.forEach((pt) => {
      const problem = world.placementProblem(this.me, this.placing!, pt.x, pt.y, true)
        ?? (world.canAfford(budget, cost) ? null : "Out of resources");
      if (problem) { lastProblem = problem; return; }
      const v = villagers[placed % Math.max(1, villagers.length)];
      this.dispatch({ t: "place", team: this.me, building: this.placing!, x: pt.x, y: pt.y, builders: v ? [v.id] : [] });
      budget = { food: budget.food - cost.food, wood: budget.wood - cost.wood, gold: budget.gold - cost.gold };
      placed++;
    });
    if (placed) {
      audio.play("build");
      if (placed < pts.length) this.hud.addAlert(`Placed ${placed} of ${pts.length} — ${lastProblem.toLowerCase()}.`);
    } else {
      this.hud.addAlert(`Can't build there — ${lastProblem.toLowerCase() || "nothing fits"}.`);
      audio.play("tick");
    }
    // Walls stay armed. Laying a wall means laying several runs — around a
    // corner, along a ridge, across a gap — and having to reopen Build →
    // Defense → Palisade between every one of them was the whole reason this
    // felt like work. Right-click or Escape puts the cursor down.
    if (!LINE_BUILDABLE.has(this.placing!) && !this.input.shift) this.placing = null;
  }

  /** A clickable row of control-group chips above the minimap (number + live count). */
  drawControlGroups(W: number, H: number) {
    if (!this.world) return;
    const ctx = this.renderer.ctx;
    const chipW = 34;
    const chipH = 22;
    const y = H - MINIMAP_SIZE - 10 - chipH - 6;
    let x = 12;
    const selSet = new Set(this.selection);
    for (let g = 1; g <= 9; g++) {
      const live = this.controlGroups[g].filter((id) => this.world!.byId.get(id)?.alive);
      if (live.length === 0) continue;
      const hover = ui.mx >= x && ui.mx <= x + chipW && ui.my >= y && ui.my <= y + chipH;
      // A chip is "active" if its members are exactly the current selection.
      const active = live.length === selSet.size && live.every((id) => selSet.has(id));
      ctx.fillStyle = active ? withAlpha(PAL.uiAccent, 0.85) : hover ? "rgba(40,32,20,0.95)" : "rgba(20,16,10,0.85)";
      ctx.strokeStyle = withAlpha(PAL.uiAccent, active ? 1 : 0.4);
      ctx.lineWidth = 1;
      ctx.fillRect(x, y, chipW, chipH);
      ctx.strokeRect(x, y, chipW, chipH);
      ctx.fillStyle = active ? "#1a1208" : PAL.uiAccent;
      ctx.font = "bold 12px sans-serif";
      ctx.textAlign = "left";
      ctx.fillText(String(g), x + 5, y + 15);
      ctx.fillStyle = active ? "#1a1208" : "#e7ddc4";
      ctx.font = "11px sans-serif";
      ctx.textAlign = "right";
      ctx.fillText(String(live.length), x + chipW - 5, y + 15);
      if (hover && this.frameClick && !ui.pointerConsumed) {
        ui.pointerConsumed = true;
        this.select(live);
      }
      x += chipW + 4;
    }
    ctx.textAlign = "left";
  }

  /** Push the current settings into the live engine systems. Called at boot and
   *  every frame the settings screen is open (for instant preview). */
  /** Interface scale, clamped. 1 = the canvas's own pixels. */
  private uiScale(): number {
    return Math.max(0.8, Math.min(1.5, this.settings.uiScale || 1));
  }

  applySettings() {
    const s = this.settings;
    audio.masterVol = s.masterVol;
    audio.sfxVol = s.sfxVol;
    audio.musicVol = s.musicVol;
    audio.muted = s.muted;
    audio.applyVolumes();
    setColorblindTeams(s.colorblind);
    this.particles.density = s.reduceEffects ? 0.4 : 1;
    this.renderer.aggressiveLod = s.reduceEffects || this.perf.autoLod;
    this.showDamageNumbers = s.damageNumbers;
  }

  private resetMatchTelemetry() {
    this.matchHistory = [];
    this.nextSampleT = 0;
    this.endGraph = null;
    this.showScoreboard = false;
    this.showProduction = false;
    this.chatOpen = false;
    this.chatDraft = "";
    this.chatLog = [];
  }

  /** Sample every team's metrics at a fixed game-time cadence for the graphs. */
  private sampleHistory(world: World) {
    if (world.time < this.nextSampleT) return;
    this.nextSampleT = world.time + 4; // every 4s of sim time
    this.matchHistory.push({ t: world.time, m: snapshotMetrics(world) });
  }

  private openSettings(returnTo: AppState) {
    this.settingsReturn = returnTo;
    this.state = "settings";
    audio.play("ui");
  }

  // ----------------------------------------------------------- QoL controls --
  togglePause() {
    this.paused = !this.paused;
    this.hud.addAlert(this.paused ? "⏸ Paused" : "▶ Resumed");
  }

  setSpeed(v: number) {
    this.gameSpeed = v;
    this.paused = false;
    if (!this.spectating) this.hud.addAlert(`Speed ${v}×`); // the caster bar shows it
  }

  cycleSpeed(dir: number) {
    // Replays and AI games can run far faster than a game you're playing.
    const speeds = this.spectating && !this.net ? [0.5, 1, 2, 4, 8, 16] : [0.5, 1, 2, 3];
    if (this.paused) { this.paused = false; return; }
    const i = Math.max(0, Math.min(speeds.length - 1, speeds.indexOf(this.gameSpeed) + dir));
    this.setSpeed(speeds[i]);
  }

  /**
   * Cycle the camera through idle villagers, selecting each in turn. `dir` is
   * +1 for "." and −1 for "," — the same pair Age of Empires uses, and the
   * reason a backwards step exists at all is that overshooting the one you
   * wanted is the whole reason people press it twice.
   */
  selectIdleVillager(dir: 1 | -1 = 1) {
    if (!this.world) return;
    const idle = this.world
      .entitiesOf(this.me, Kind.Unit)
      .filter((e) => e.type === "villager" && e.order.kind === OrderKind.Idle);
    if (idle.length === 0) { this.hud.addAlert("No idle villagers"); return; }
    const n = idle.length;
    this.idleVillIndex = ((this.idleVillIndex % n) + n) % n;
    const v = idle[this.idleVillIndex];
    this.idleVillIndex = (this.idleVillIndex + dir + n) % n;
    this.select([v.id]);
    this.camera.centerOn(v.x, v.y);
    this.hud.addAlert(`Idle villager (${n} idle)`);
  }

  /** Select every military unit you own (everything that isn't a villager). */
  selectAllArmy() {
    if (!this.world) return;
    const army = this.world
      .entitiesOf(this.me, Kind.Unit)
      .filter((e) => !UNITS[e.type]?.canGather);
    if (army.length === 0) { this.hud.addAlert("No army units"); return; }
    this.select(army.map((e) => e.id));
  }

  /** Pause/speed chips in the top bar + idle-villager & army buttons by the minimap. */
  drawQoLBar(W: number, H: number) {
    if (!this.world) return;
    const ctx = this.renderer.ctx;
    const chip = (label: string, x: number, y: number, w: number, h: number, active: boolean, onClick: () => void, danger = false) => {
      const hover = ui.mx >= x && ui.mx <= x + w && ui.my >= y && ui.my <= y + h;
      ctx.fillStyle = active ? withAlpha(PAL.uiAccent, 0.9) : hover ? "rgba(54,42,24,0.96)" : "rgba(18,14,9,0.72)";
      ctx.fillRect(x, y, w, h);
      ctx.strokeStyle = withAlpha(PAL.uiAccent, active ? 1 : 0.35);
      ctx.lineWidth = 1;
      ctx.strokeRect(x, y, w, h);
      ctx.fillStyle = active ? "#1a1208" : danger ? "#f0a878" : PAL.uiParchment;
      ctx.font = "bold 12px sans-serif";
      ctx.textAlign = "center";
      ctx.fillText(label, x + w / 2, y + h / 2 + 4);
      if (hover && this.frameClick && !ui.pointerConsumed) { ui.pointerConsumed = true; onClick(); }
      // A right-click or a drag that ends on a chip is for the chip, not the map under it.
      if (hover) ui.pointerConsumed = true;
    };

    // Pause + speed, right of the day/night clock.
    let x = W / 2 + 150;
    chip(this.paused ? "▶" : "❚❚", x, 4, 26, 26, false, () => this.togglePause(), this.paused);
    x += 29;
    for (const s of [0.5, 1, 2, 3]) {
      chip(s === 0.5 ? "½×" : s + "×", x, 4, 28, 26, this.gameSpeed === s && !this.paused, () => this.setSpeed(s));
      x += 31;
    }
    ctx.textAlign = "left";

    // Idle-villager + army, above the minimap.
    const idleCount = this.world
      .entitiesOf(this.me, Kind.Unit)
      .filter((e) => e.type === "villager" && e.order.kind === OrderKind.Idle).length;
    const by = H - MINIMAP_SIZE - 10 - 22 - 6 - 26 - 4;
    chip(`Idle ${idleCount}`, 12, by, 70, 24, idleCount > 0, () => this.selectIdleVillager());
    chip("Army", 86, by, 46, 24, false, () => this.selectAllArmy());
    chip("⚒", 136, by, 30, 24, this.showProduction, () => { this.showProduction = !this.showProduction; });

    // Commander power button (only if your commander has one).
    const p = this.world.player(this.me);
    const power = COMMANDERS[p.commander]?.power;
    if (power) {
      const ready = p.powerCooldown <= 0;
      const label = this.powerArmed ? "Place ⚑" : ready ? `⚑ ${power.name.split(" ")[0]}` : `${Math.ceil(p.powerCooldown)}s`;
      chip(label, 170, by, 96, 24, this.powerArmed || ready, () => this.armCommanderPower());
    }
    ctx.textAlign = "left";
  }

  armCommanderPower() {
    if (!this.world) return;
    if (this.world.powerReady(this.me)) {
      this.powerArmed = true;
      this.placing = null;
      this.attackMoveArmed = false;
    } else {
      this.hud.addAlert("Commander power not ready.");
    }
  }

  dropPing(wx: number, wy: number) {
    this.hud.addPing(wx, wy);
    this.markers.push({ x: wx, y: wy, age: 0, kind: "rally" }); // quick in-world flare
    audio.play("ui");
    this.net?.transport.send({ t: "ping", x: wx, y: wy, team: this.me }); // share with the lobby
  }

  /** Show an incoming network ping from another player. */
  private remotePing(x: number, y: number, team: Team) {
    this.hud.addPing(x, y);
    this.markers.push({ x, y, age: 0, kind: "rally" });
    this.hud.addAlert(`📍 ${teamLabel(team)} pinged the map`);
    audio.play("ui");
  }

  private myName(): string {
    try { return localStorage.getItem("bb_player_name") || "You"; } catch { return "You"; }
  }

  private handleChatKey(key: string) {
    if (key === "Enter") {
      const text = this.chatDraft.trim();
      if (text && this.net) { this.net.transport.send({ t: "chat", name: this.myName(), text, team: this.me }); this.addChatLine(this.myName(), text, this.me); }
      this.chatOpen = false; this.chatDraft = "";
    } else if (key === "Escape") {
      this.chatOpen = false; this.chatDraft = "";
    } else if (key === "Backspace") {
      this.chatDraft = this.chatDraft.slice(0, -1);
    } else if (key.length === 1 && this.chatDraft.length < 120) {
      this.chatDraft += key;
    }
  }

  addChatLine(name: string, text: string, team: Team) {
    this.chatLog.push({ name: name.slice(0, 24), text: text.slice(0, 120), team, t: this.time });
    if (this.chatLog.length > 30) this.chatLog.shift();
  }

  worldClick(sx: number, sy: number) {
    if (!this.world) return;
    const wx = this.camera.screenToWorldX(sx);
    const wy = this.camera.screenToWorldY(sy);

    // Planting the commander banner.
    if (this.powerArmed) {
      if (this.world.powerReady(this.me)) {
        this.dispatch({ t: "banner", team: this.me, x: wx, y: wy });
        const power = COMMANDERS[this.world.player(this.me).commander]?.power;
        this.markers.push({ x: wx, y: wy, age: 0, kind: "rally" });
        this.hud.addAlert(`⚑ ${power?.name ?? "Banner"} planted!`);
        audio.play("command");
      }
      this.powerArmed = false;
      return;
    }

    // Alt+click drops a ping (look-here signal) instead of selecting.
    if (this.input.alt) {
      this.dropPing(wx, wy);
      return;
    }

    if (this.placing) {
      const villagers = this.playerSelection().filter((e) => UNITS[e.type]?.canBuild);
      // The same checks the sim will make, so a click never looks like it
      // worked when it didn't — and when it can't, say why.
      const problem = this.world.placementProblem(this.me, this.placing, wx, wy);
      if (!problem) {
        this.dispatch({ t: "place", team: this.me, building: this.placing, x: wx, y: wy, builders: villagers.map((v) => v.id) });
        audio.play("build");
        this.flashBuilders(villagers, wx, wy);
        if (!villagers.length) this.hud.addAlert("Placed — no villager selected, so it waits for a builder.", wx, wy);
        // Walls stay armed either way (click or drag); other buildings with Shift.
        if (!this.input.shift && !LINE_BUILDABLE.has(this.placing)) this.placing = null;
      } else {
        this.hud.addAlert(`Can't build there — ${problem.charAt(0).toLowerCase()}${problem.slice(1)}.`, wx, wy);
        audio.play("tick");
      }
      return;
    }

    if (this.attackMoveArmed) {
      const units = this.playerSelection().filter((e) => e.kind === Kind.Unit);
      this.dispatch({ t: "move", team: this.me, ids: units.map((e) => e.id), x: wx, y: wy, queue: this.input.shift, attackMove: true, formation: true });
      this.markers.push({ x: wx, y: wy, age: 0, kind: "attack" });
      this.attackMoveArmed = false;
      audio.play("command");
      return;
    }

    // Plain selection click.
    const e = this.world.entityAt(wx, wy);
    if (e && e.kind !== Kind.Projectile && this.world.visibleTo(this.me, e)) {
      if (this.input.shift && e.team === this.me) {
        const cur = new Set(this.selection);
        if (cur.has(e.id)) cur.delete(e.id);
        else cur.add(e.id);
        this.select([...cur]);
      } else {
        this.select([e.id]);
      }
    } else if (!this.input.shift) {
      this.select([]);
    }
  }

  worldDoubleClick(sx: number, sy: number) {
    if (!this.world) return;
    const wx = this.camera.screenToWorldX(sx);
    const wy = this.camera.screenToWorldY(sy);
    const e = this.world.entityAt(wx, wy, this.me);
    if (!e || (e.kind !== Kind.Unit && e.kind !== Kind.Building)) return;
    // Select every entity of this kind+type currently on screen — units (an army
    // of one type) or buildings (e.g. all your stables, to mass-train at once).
    const ids: EntityId[] = [];
    for (const o of this.world.entitiesOf(this.me, e.kind)) {
      if (o.type !== e.type) continue;
      const ox = this.camera.worldToScreenX(o.x);
      const oy = this.camera.worldToScreenY(o.y);
      if (ox >= 0 && oy >= 0 && ox <= this.canvas.width && oy <= this.canvas.height) ids.push(o.id);
    }
    this.select(ids);
  }

  worldDragSelect(box: { x0: number; y0: number; x1: number; y1: number }) {
    if (!this.world) return;
    const wx0 = Math.min(this.camera.screenToWorldX(box.x0), this.camera.screenToWorldX(box.x1));
    const wx1 = Math.max(this.camera.screenToWorldX(box.x0), this.camera.screenToWorldX(box.x1));
    const wy0 = Math.min(this.camera.screenToWorldY(box.y0), this.camera.screenToWorldY(box.y1));
    const wy1 = Math.max(this.camera.screenToWorldY(box.y0), this.camera.screenToWorldY(box.y1));
    const units: EntityId[] = [];
    const buildings: EntityId[] = [];
    for (const e of this.world.entitiesOf(this.me)) {
      if (e.x < wx0 || e.x > wx1 || e.y < wy0 || e.y > wy1) continue;
      if (e.kind === Kind.Unit) units.push(e.id);
      else if (e.kind === Kind.Building) buildings.push(e.id);
    }
    let picked = units.length ? units : buildings.slice(0, 1);
    if (this.input.shift) picked = [...new Set([...this.selection, ...picked])];
    this.select(picked);
  }

  issueContextCommand(wx: number, wy: number, screen: { x: number; y: number } | null) {
    if (!this.world) return;
    const sel = this.playerSelection();
    if (sel.length === 0) return;
    const units = sel.filter((e) => e.kind === Kind.Unit);
    const buildingsSel = sel.filter((e) => e.kind === Kind.Building);
    const target = this.world.entityAt(wx, wy);
    const shift = this.input.shift;

    const mv = (mids: EntityId[], formation: boolean) =>
      this.dispatch({ t: "move", team: this.me, ids: mids, x: wx, y: wy, queue: shift, attackMove: false, formation });

    // Production buildings: right-click sets rally.
    if (units.length === 0 && buildingsSel.length > 0) {
      for (const b of buildingsSel) {
        if (BUILDINGS[b.type]?.trains.length) {
          this.dispatch({ t: "rally", team: this.me, buildingId: b.id, x: wx, y: wy });
          this.markers.push({ x: wx, y: wy, age: 0, kind: "rally" });
        }
      }
      audio.play("command");
      return;
    }
    if (units.length === 0) return;

    const ids = units.map((e) => e.id);

    // Gatherers prefer a nearby resource even if a building (e.g. a mill built
    // right next to berries) sits closer to the click point.
    const gatherers0 = units.filter((e) => UNITS[e.type]?.canGather);
    if (gatherers0.length > 0) {
      const node = this.world.resourceAt(wx, wy, this.me);
      if (node && this.world.visibleTo(this.me, node) && !(target && target.team !== this.me && target.kind !== Kind.Resource)) {
        this.dispatch({ t: "gather", team: this.me, ids: gatherers0.map((e) => e.id), node: node.id, queue: shift });
        const rest = units.filter((e) => !UNITS[e.type]?.canGather);
        if (rest.length) mv(rest.map((e) => e.id), false);
        this.markers.push({ x: wx, y: wy, age: 0, kind: "move" });
        audio.play("command");
        return;
      }
    }

    if (target && target.alive && this.world.visibleTo(this.me, target)) {
      if (this.world.areHostile(this.me, target.team) && target.kind !== Kind.Resource) {
        this.dispatch({ t: "attack", team: this.me, ids, target: target.id, queue: shift });
        this.markers.push({ x: wx, y: wy, age: 0, kind: "attack" });
        audio.play("command");
        return;
      }
      if (target.kind === Kind.Resource || (target.team === this.me && target.type === "farm" && target.buildState === BuildState.Done)) {
        const gatherers = units.filter((e) => UNITS[e.type]?.canGather);
        if (gatherers.length) {
          this.dispatch({ t: "gather", team: this.me, ids: gatherers.map((e) => e.id), node: target.id, queue: shift });
          const rest = units.filter((e) => !UNITS[e.type]?.canGather);
          if (rest.length) mv(rest.map((e) => e.id), false);
          this.markers.push({ x: wx, y: wy, age: 0, kind: "move" });
          audio.play("command");
          return;
        }
      }
      if (target.team === this.me && target.kind === Kind.Building) {
        // Villagers repair/finish construction; combat units garrison.
        const builders = units.filter((e) => UNITS[e.type]?.canBuild);
        const fighters = units.filter((e) => !UNITS[e.type]?.canBuild);
        if (builders.length && (target.buildState !== BuildState.Done || target.hp < target.maxHp)) {
          this.dispatch({ t: "build", team: this.me, ids: builders.map((e) => e.id), building: target.id, queue: shift });
        }
        const cap = BUILDINGS[target.type]?.garrisonCap ?? 0;
        if (fighters.length && cap > 0) {
          this.dispatch({ t: "garrison", team: this.me, ids: fighters.map((e) => e.id), buildingId: target.id });
        } else if (fighters.length) {
          mv(fighters.map((e) => e.id), false);
        }
        this.markers.push({ x: wx, y: wy, age: 0, kind: "move" });
        audio.play("command");
        return;
      }
    }
    // Default: move.
    mv(ids, true);
    this.markers.push({ x: wx, y: wy, age: 0, kind: "move" });
    audio.play("command");
  }

  // ------------------------------------------------------------- world events --

  handleEvents(events: WorldEvent[]) {
    const sfx = (name: Parameters<typeof audio.play>[0], key: string, cd = 0.08) => {
      const last = this.sfxCooldown.get(key) ?? -99;
      if (this.time - last > cd) {
        this.sfxCooldown.set(key, this.time);
        audio.play(name);
      }
    };
    const heat: Record<string, number> = { sword: 0.06, bow: 0.03, siege: 0.12, death: 0.1, collapse: 0.15 };
    for (const ev of events) {
      if (heat[ev.kind]) this.combatHeat = Math.min(1, this.combatHeat + heat[ev.kind]);
      switch (ev.kind) {
        case "sword":
          sfx("sword", "sword");
          // dust puff + a couple of bright metallic glints for a punchy clash
          this.particles.burst(ev.x, ev.y, 5, "#ffd9a0", 95, { maxLife: 0.22, size: 1.8 });
          this.particles.burst(ev.x, ev.y, 3, "#fff6e0", 150, { maxLife: 0.16, size: 1.4, glow: true });
          break;
        case "bow":
          sfx("bow", "bow");
          break;
        case "arrowHit":
          sfx("arrowHit", "arrowHit");
          this.particles.burst(ev.x, ev.y, 3, "#d9cfb4", 70, { maxLife: 0.2, size: 1.5 });
          this.particles.burst(ev.x, ev.y, 2, "#fff6e0", 130, { maxLife: 0.14, size: 1.2, glow: true });
          if (Math.random() < 0.6) this.renderer.addStuckArrow(ev.x, ev.y, Math.random() * Math.PI * 2);
          break;
        case "hit":
          if (this.showDamageNumbers && ev.data) {
            const friendly = ev.team === this.me;
            this.renderer.addFloater(ev.x, ev.y, ev.data, friendly ? "#fff0c0" : "#ff9a8a", 13);
          }
          break;
        case "siege":
          sfx("siege", "siege", 0.2);
          this.particles.burst(ev.x, ev.y, 22, PAL.dust, 170, { maxLife: 0.7, size: 3.4, gravity: 60 });
          this.particles.burst(ev.x, ev.y, 10, PAL.fire, 220, { maxLife: 0.35, size: 2.6, glow: true });
          this.renderer.addShake(4);
          break;
        case "death":
          sfx("death", "death", 0.14);
          this.particles.burst(ev.x, ev.y, 9, PAL.blood, 110, { maxLife: 0.5, size: 2.2, gravity: 140 });
          this.particles.burst(ev.x, ev.y, 5, "#ffffff", 80, { maxLife: 0.18, size: 2.4, glow: true });
          this.renderer.addCorpse(ev.x, ev.y, ev.team, false);
          break;
        case "collapse":
          sfx("collapse", "collapse", 0.3);
          this.particles.burst(ev.x, ev.y, 36, PAL.dust, 200, { maxLife: 1.1, size: 4.2, gravity: 50 });
          this.particles.burst(ev.x, ev.y, 16, PAL.smoke, 90, { maxLife: 1.6, size: 5, gravity: -30 });
          this.particles.burst(ev.x, ev.y, 12, PAL.fire, 140, { maxLife: 0.6, size: 3, glow: true });
          this.renderer.addScorch(ev.x, ev.y, 30);
          this.renderer.addShake(7);
          break;
        case "build":
          sfx("build", "build", 0.4);
          break;
        case "complete":
          if (ev.team === this.me && !this.spectating) {
            sfx("complete", "complete", 0.5);
            const name = BUILDINGS[ev.data ?? ""]?.name;
            if (name) this.hud.addAlert(`${name} completed.`, ev.x, ev.y);
            this.lastEvent = { x: ev.x, y: ev.y };
          }
          break;
        case "popcap":
          if (ev.team === this.me && !this.spectating && this.time - this.popcapAlertAt > 6) {
            this.popcapAlertAt = this.time;
            this.hud.addAlert(`Population limit — a ${UNITS[ev.data ?? ""]?.name ?? "unit"} couldn't join (refunded). Build a House.`, ev.x, ev.y);
            sfx("alert", "alert", 3);
          }
          break;
        case "underattack":
          if (ev.team === this.me && !this.spectating) {
            sfx("alert", "alert", 4);
            this.hud.addAlert("⚠ Your forces are under attack!", ev.x, ev.y);
            // Space jumps here, which is what makes an "under attack" warning
            // actionable rather than a message you then have to go hunting for.
            this.lastEvent = { x: ev.x, y: ev.y };
          }
          break;
        case "callout":
          // Surface an allied AI's voice line (not our own) so team games feel
          // like a coordinated front rather than silent co-op.
          if (!this.spectating && ev.team !== this.me && this.world?.areAllied(this.me, ev.team) && ev.data) {
            this.hud.addAlert(`🗣 Ally: ${ev.data}`, ev.x, ev.y);
          }
          break;
        case "oath": {
          // Oaths are public: everyone hears what a realm swears, so its
          // identity is something to answer rather than a surprise.
          const o = OATHS[ev.data ?? ""];
          if (!o) break;
          if (ev.team === this.me) {
            const u = o.unit ? UNITS[o.unit] : undefined;
            this.hud.addAlert(`You swore the ${o.name}${u ? ` — ${u.name}s can now be trained` : ""}`);
          } else {
            // No map ping: the event sits on their Town Centre, and the
            // announcement mustn't double as a free scout of it.
            this.hud.addAlert(`${this.teamLabel(ev.team)} swore the ${o.name}`);
          }
          break;
        }
        case "age": {
          const who = ev.team === this.me ? "You have" : "The enemy has";
          this.hud.addAlert(`${who} advanced to the ${AGES[parseInt(ev.data ?? "0", 10)]?.name ?? "next age"}!`);
          if (ev.team === this.me) sfx("levelup", "age", 1);
          break;
        }
        case "ability": {
          const col = ABILITIES[
            Object.keys(ABILITIES).find((k) => ABILITIES[k].id === ev.data) ?? ""
          ]?.color ?? "#ffe98a";
          this.particles.burst(ev.x, ev.y, 14, col, 130, { maxLife: 0.5, size: 2.4, glow: true, gravity: -40 });
          if (ev.team === this.me) sfx("command", "ability", 0.1);
          break;
        }
        case "deposit":
          break;
        case "spawn":
          if (ev.team === this.me) sfx("complete", "spawn", 2);
          break;
      }
    }
  }

  // -------------------------------------------------------------------- frame --

  frame() {
    // A throw anywhere in a frame must never kill the loop — always reschedule.
    try {
      this.frameBody();
    } catch (err) {
      console.error("frame error (recovered):", err);
    }
    requestAnimationFrame(() => this.frame());
  }

  frameBody() {
    const now = performance.now();
    let dt = (now - this.lastFrame) / 1000;
    this.lastFrame = now;
    dt = Math.min(dt, 0.1);
    this.time += dt;
    // Smoothed FPS (EMA) for the optional on-screen counter.
    if (dt > 0) this.fps += (1 / dt - this.fps) * 0.1;
    this.trackPerf(dt);

    const W = this.canvas.width;
    const H = this.canvas.height;
    const ctx = this.renderer.ctx;

    setMouseDown(this.input.leftDown);
    this.input.longPressRight = this.state === "match"; // long-press → right-click only in a match
    ui.begin(ctx, {
      mx: this.input.mx,
      my: this.input.my,
      clicked: !!this.frameClick,
      rightClicked: !!this.frameRight,
      alt: this.input.alt,
      leftHeld: this.input.leftDown,
      rightHeld: this.input.rightDown,
      ctrlHeld: this.input.ctrl,
      shiftHeld: this.input.shift,
      wheel: this.frameWheel,
    });

    if (this.state === "match" && this.world) {
      this.matchFrame(dt, W, H);
    } else {
      // Out-of-match screens are pure interface, so the whole block draws
      // through the interface scale.
      audio.updateMusic(dt, true);
      const uis = this.uiScale();
      const W = this.canvas.width / uis, H = this.canvas.height / uis;
      ui.pushScale(uis);
      if (this.state === "menu") {
        const action = this.menu.draw(W, H, this.time, this.profile);
        fullscreenButton(W - 186, 16, 170, 36, { size: 14, hotkey: this.fullscreenKeyLabel() });
        if (action === "skirmish") {
          this.state = "setup";
          audio.play("ui");
        } else if (action === "resume" && this.menu.pickedSave) {
          const save = this.menu.pickedSave;
          this.menu.pickedSave = null;
          audio.play("ui");
          this.loadMatch(save);
        } else if (action === "multiplayer") {
          audio.play("ui");
          this.lobby.open((start) => this.startNetMatch(start), this.lobbyOptions());
        } else if (action === "warband") {
          this.warband = new WarbandRun();
          this.state = "warband";
          audio.play("ui");
        } else if (action === "armory") {
          this.state = "armory";
          audio.play("ui");
        } else if (action === "codex") {
          this.state = "codex";
          audio.play("ui");
        } else if (action === "career") {
          this.state = "career";
          audio.play("ui");
        } else if (action === "nemesis") {
          this.rivalScreen.open(this.profile.playableFaction() ?? "");
          this.state = "nemesis";
          audio.play("ui");
        } else if (action === "factions") {
          this.openFactions("menu", this.profile.playableFaction());
        } else if (action === "editor") {
          this.state = "editor";
          this.editorScreen.author = this.profile.data.name;
          audio.play("ui");
        } else if (action === "settings") {
          this.openSettings("menu");
        }
      } else if (this.state === "warband" && this.warband) {
        if (this.warbandScreen.draw(W, H, this.time, this.warband) === "exit") {
          this.warband = null;
          this.state = "menu";
          audio.play("ui");
        }
      } else if (this.state === "settings") {
        const a = this.settingsScreen.draw(W, H, this.time, this.settings, this.input.leftDown);
        this.applySettings(); // live preview every frame
        if (a === "back") {
          saveSettings(this.settings);
          this.state = this.settingsReturn;
          audio.play("ui");
        }
      } else if (this.state === "editor") {
        const a = this.editorScreen.draw(W, H, this.time, this.input.leftDown);
        if (a?.kind === "back") { this.state = "menu"; audio.play("ui"); }
        else if (a?.kind === "test") this.testCustomMap(a.map);
      } else if (this.state === "nemesis") {
        const a = this.rivalScreen.draw(W, H, this.time, this.profile.playableFaction() ?? "");
        if (a === "back") { this.state = "menu"; audio.play("ui"); }
        else if (a === "fight") { audio.play("ui"); this.startRivalMatch(); }
      } else if (this.state === "career") {
        const a = this.careerScreen.draw(W, H, this.time, this.profile);
        if (a === "back") {
          this.state = "menu";
          audio.play("ui");
        } else if (a && "watch" in a) {
          audio.play("ui");
          this.startReplay(a.watch);
        }
      } else if (this.state === "factions") {
        if (this.factionBook.draw(W, H, this.time, this.profile, dt) === "back") {
          this.state = this.factionsReturn;
          if (this.state === "setup") this.setup.config.faction = this.profile.playableFaction() ?? DEFAULT_FACTION;
          audio.play("ui");
        }
      } else if (this.state === "codex") {
        if (this.codexScreen.draw(W, H, this.time, this.profile) === "back") {
          this.state = "menu";
          audio.play("ui");
        }
      } else if (this.state === "setup") {
        const action = this.setup.draw(W, H, this.time, this.profile);
        if (action === "back") this.state = "menu";
        else if (action === "factions") this.openFactions("setup", this.setup.bookFocus);
        else if (action === "start") this.startMatch({ ...this.setup.config });
        else if (action === "spectate") this.startSpectate({ ...this.setup.config });
      } else if (this.state === "armory") {
        const action = this.armory.draw(W, H, this.time, dt, this.profile);
        if (action === "back") {
          this.profile.save();
          this.state = "menu";
        }
      } else if (this.state === "postmatch" && this.matchRewards) {
        const action = this.postmatch.draw(
          W, H, this.time, dt,
          this.matchWon, this.endReport, this.matchRewards,
          this.profile, this.xpBefore, this.levelsGained, this.endGraph, this.matchAwards,
        );
        if (action === "continue") {
          // A match launched from the editor goes back to the editor, so
          // "test, tweak, test again" is a loop rather than a round trip
          // through the main menu every time.
          this.state = this.editorReturn ? "editor" : "menu";
          this.editorReturn = false;
          audio.play("ui");
          // An online match goes back to the server's hub, not just the menu.
          if (this.netHub) {
            const hub = this.netHub;
            this.netHub = "";
            this.lobby.reopenHub((start) => this.startNetMatch(start), hub, this.lobbyOptions());
          }
        }
      }
      ui.flushTooltip(W, H);
      ui.popScale();
    }

    // Optional FPS overlay, drawn on top of everything in every state.
    {
      // The diagnostics scale too — they are the readouts a player squinting at
      // a chugging match most needs to be able to read.
      const uis = this.uiScale();
      ui.pushScale(uis);
      if (this.settings.showFps) this.drawFps(W / uis);
      if (this.settings.perfOverlay) this.drawPerfOverlay(W / uis, H / uis);
      ui.popScale();
    }
    // On a phone held in portrait, nudge to landscape — the UI is landscape-first.
    if (this.input.usingTouch && H > W * 1.05) this.drawRotatePrompt(W, H);

    // Clear frame input flags.
    this.frameClick = null;
    this.frameDouble = null;
    this.frameRight = null;
    this.frameDragEnd = null;
    this.frameWheel = 0;
  }

  /**
   * Frame/tick bookkeeping, plus adaptive detail.
   *
   * The rule is deliberately slow on the way in and slower on the way out: a
   * single stuttery frame is normal (a building finishing, a map reveal), and
   * detail that flickers on and off is worse than either setting. So the
   * budget has to be blown for most of a second before detail drops, and the
   * frame rate has to be comfortably back for two before it returns.
   */
  private trackPerf(dt: number) {
    const ms = dt * 1000;
    this.perf.frameMs += (ms - this.perf.frameMs) * 0.1;
    this.perf.ticksLastFrame = this.perf.ticksThisFrame;
    this.perf.ticksThisFrame = 0;
    if (ms > this.perf.worstFrameMs) this.perf.worstFrameMs = ms;
    // The "worst" readouts are a rolling three-second window, so the overlay
    // shows what is happening now rather than the worst thing that ever did.
    this.perf.worstReset += dt;
    if (this.perf.worstReset > 3) {
      this.perf.worstReset = 0;
      this.perf.worstFrameMs = ms;
      this.perf.worstTickMs = this.perf.tickMs;
    }
    if (!this.settings.autoLod || this.settings.reduceEffects) {
      if (this.perf.autoLod) { this.perf.autoLod = false; this.applySettings(); }
      this.perf.slipT = 0;
      return;
    }
    const SLIPPING = 1000 / 45; // below ~45fps counts as slipping
    const RECOVERED = 1000 / 55;
    if (!this.perf.autoLod && this.perf.frameMs > SLIPPING) {
      this.perf.slipT += dt;
      if (this.perf.slipT > 0.8) {
        this.perf.autoLod = true;
        this.perf.slipT = 0;
        this.applySettings();
        this.hud.addAlert("Detail reduced to keep up");
      }
    } else if (this.perf.autoLod && this.perf.frameMs < RECOVERED) {
      this.perf.slipT += dt;
      if (this.perf.slipT > 2) {
        this.perf.autoLod = false;
        this.perf.slipT = 0;
        this.applySettings();
      }
    } else this.perf.slipT = 0;
  }

  /**
   * The performance overlay. Its job is to answer "why is this chugging?"
   * without a devtools profile: whether the cost is the simulation or the
   * drawing, how much of the map is alive, and whether detail has already been
   * dropped to cope.
   */
  private drawPerfOverlay(W: number, H: number) {
    const ctx = this.renderer.ctx;
    const p = this.perf;
    const fps = Math.max(0, Math.round(this.fps));
    const ents = this.world ? this.world.entities.length : 0;
    const budget = 1000 / 60;
    // Frame cost minus what the simulation took is, near enough, the drawing.
    const simShare = Math.min(p.frameMs, p.tickMs * Math.max(1, p.ticksLastFrame));
    const rows: [string, string, string][] = [
      ["fps", String(fps), fps >= 55 ? "#7df2a9" : fps >= 30 ? "#ffd24a" : "#e0564a"],
      ["frame", `${p.frameMs.toFixed(1)} ms  (worst ${p.worstFrameMs.toFixed(0)})`,
        p.frameMs <= budget ? "#7df2a9" : p.frameMs <= budget * 2 ? "#ffd24a" : "#e0564a"],
      ["sim", `${p.tickMs.toFixed(2)} ms/tick  (worst ${p.worstTickMs.toFixed(1)})`,
        p.tickMs <= 8 ? "#7df2a9" : p.tickMs <= 20 ? "#ffd24a" : "#e0564a"],
      ["draw", `${Math.max(0, p.frameMs - simShare).toFixed(1)} ms`, "#cfe0ff"],
      ["entities", String(ents), ents < 900 ? "#cabfa4" : ents < 1600 ? "#ffd24a" : "#e0564a"],
      ["detail", this.settings.reduceEffects ? "reduced (setting)" : p.autoLod ? "reduced (auto)" : "full",
        p.autoLod || this.settings.reduceEffects ? "#ffd24a" : "#cabfa4"],
    ];
    const w = 220, rowH = 17, h = 12 + rows.length * rowH + 6;
    const x = W - w - 12, y = 12;
    ctx.save();
    ctx.fillStyle = "rgba(8,6,3,0.82)";
    ctx.beginPath(); ctx.roundRect(x, y, w, h, 6); ctx.fill();
    ctx.strokeStyle = "rgba(255,255,255,0.14)"; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.roundRect(x + 0.5, y + 0.5, w - 1, h - 1, 6); ctx.stroke();
    ctx.font = "11px ui-monospace, SFMono-Regular, monospace";
    ctx.textBaseline = "middle";
    rows.forEach(([label, value, col], i) => {
      const ry = y + 14 + i * rowH;
      ctx.textAlign = "left";
      ctx.fillStyle = "#8a8278"; ctx.fillText(label, x + 10, ry);
      ctx.textAlign = "right";
      ctx.fillStyle = col; ctx.fillText(value, x + w - 10, ry);
    });
    ctx.restore();
  }

  /** Small live FPS readout, top-centre, colour-coded by smoothness. */
  private drawFps(W: number) {
    const ctx = this.renderer.ctx;
    const fps = Math.max(0, Math.round(this.fps));
    const col = fps >= 55 ? "#7df2a9" : fps >= 30 ? "#ffd24a" : "#e0564a";
    const txt = `${fps} FPS`;
    ctx.save();
    ctx.font = "bold 13px ui-monospace, SFMono-Regular, monospace";
    const tw = ctx.measureText(txt).width;
    const w = tw + 16, h = 20, x = W / 2 - w / 2, y = 4;
    ctx.fillStyle = "rgba(8,6,3,0.72)";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = "rgba(255,255,255,0.12)"; ctx.lineWidth = 1; ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
    ctx.fillStyle = col; ctx.textBaseline = "middle"; ctx.textAlign = "center";
    ctx.fillText(txt, W / 2, y + h / 2 + 1);
    ctx.restore();
  }

  /** Full-screen "rotate to landscape" overlay for portrait phones. */
  private drawRotatePrompt(W: number, H: number) {
    const ctx = this.renderer.ctx;
    ctx.save();
    ctx.fillStyle = "rgba(8,6,3,0.94)"; ctx.fillRect(0, 0, W, H);
    const cx = W / 2, cy = H / 2 - 30;
    const t = this.time * 1.6;
    const wob = Math.sin(t) * 0.35 - 0.4; // gentle rotate wobble
    ctx.translate(cx, cy); ctx.rotate(wob);
    const pw = 78, ph = 140;
    ctx.fillStyle = "#1c150e"; ctx.strokeStyle = "#caa56a"; ctx.lineWidth = 4;
    ctx.beginPath();
    (ctx as any).roundRect ? (ctx as any).roundRect(-pw / 2, -ph / 2, pw, ph, 12) : ctx.rect(-pw / 2, -ph / 2, pw, ph);
    ctx.fill(); ctx.stroke();
    ctx.fillStyle = "#3a78d8"; ctx.fillRect(-pw / 2 + 8, -ph / 2 + 16, pw - 16, ph - 32);
    ctx.restore();
    ctx.fillStyle = "#ffe9b0";
    ctx.font = "bold 30px Georgia, serif"; ctx.textAlign = "center"; ctx.textBaseline = "middle";
    ctx.fillText("↻ Rotate your device", cx, cy + 96);
    ctx.fillStyle = "#d8cdb4"; ctx.font = "16px system-ui, sans-serif";
    ctx.fillText("Banner & Blade plays in landscape.", cx, cy + 126);
  }

  matchFrame(dt: number, W: number, H: number) {
    const world = this.world!;

    // ---- simulate ----
    if (this.net) {
      // Lockstep: author local input at the fixed sim rate (no game-speed, and
      // never pausing — a local pause would just stall the peer), capped so we
      // don't run far ahead of a lagging opponent; step as far as the received
      // remote input allows. AIs don't run (both teams are human).
      if (world.winner === null) {
        if (this.net.observer) {
          // Observers don't author input — just simulate as relayed turns
          // arrive, staying the broadcast delay behind the live match.
          this.net.stepReady(20, this.casterDelayTicks);
        } else {
          this.netAccumulator += dt;
          const ahead = this.net.lock!.inputDelay + 12;
          let guard = 0;
          while (this.netAccumulator >= SIM_DT && this.net.authorLead < ahead && guard++ < 30) {
            this.net.authorTick();
            this.netAccumulator -= SIM_DT;
          }
          this.net.stepReady(10);
        }
        if (this.net.desynced && !this.netDesyncAlerted) {
          this.netDesyncAlerted = true;
          this.hud.addAlert("⚠ Connection desynced — the match is out of sync.");
          try { this.net.transport.send({ t: "desync", tick: world.tick }); } catch { /* stats only */ }
        }
      }
    } else if (this.replaySeek !== null) {
      this.advanceSeek();
    } else if (!this.ingameMenu && !this.paused && (world.winner === null || this.replay)) {
      this.accumulator += dt * this.gameSpeed;
      let steps = 0;
      const maxSteps = 5 + Math.ceil(this.gameSpeed) * 2; // allow catch-up at high speed
      while (this.accumulator >= SIM_DT && steps < maxSteps) {
        if (this.replay && this.replayStep(world)) { this.accumulator = 0; break; }
        const t0 = performance.now();
        world.tick();
        for (const ai of this.ais) ai.update(SIM_DT);
        const cost = performance.now() - t0;
        this.perf.tickMs += (cost - this.perf.tickMs) * 0.12;
        if (cost > this.perf.worstTickMs) this.perf.worstTickMs = cost;
        this.perf.ticksThisFrame++;
        this.accumulator -= SIM_DT;
        steps++;
      }
      if (steps === maxSteps) this.accumulator = 0; // drop time if we can't keep up
    }
    const events = world.drainEvents();
    if (this.spectating) this.caster.onEvents(world, events, this.playerNames);
    this.handleEvents(events);
    this.sampleHistory(world);
    if (this.spectating) {
      // Vision: everything, or exactly one player's fog of war.
      const v = this.caster.vision;
      world.revealAll = v < 0 || v >= world.numTeams;
      this.me = (v >= 0 && v < world.numTeams ? v : 0) as Team;
      if (!this.ingameMenu) this.caster.direct(world, this.camera, dt);
    }
    this.particles.update(dt);
    // Combat heat fades over a few seconds; it drives the music's intensity.
    this.combatHeat *= Math.pow(0.5, dt / 3);
    audio.updateMusic(dt, !this.ingameMenu, this.ingameMenu ? 0 : this.combatHeat);

    // Day/night cycle (deterministic off sim time; drives sky tint + vision).
    this.renderer.dayPhase = dayPhase(world.time);

    // Damaged buildings smoke and burn.
    this.smokeTimer -= dt;
    if (this.smokeTimer <= 0) {
      this.smokeTimer = 0.09;
      for (const e of world.entities) {
        if (!e.alive || e.kind !== Kind.Building || e.buildState !== BuildState.Done) continue;
        const frac = e.hp / e.maxHp;
        if (frac >= 0.5) continue;
        const sx = e.x + (Math.random() - 0.5) * e.radius;
        const sy = e.y - e.radius * 0.3 + (Math.random() - 0.5) * e.radius * 0.4;
        this.particles.spawn({ x: sx, y: sy, vx: (Math.random() - 0.5) * 8, vy: -22 - Math.random() * 14, color: PAL.smoke, maxLife: 1.4 + Math.random(), size: 3 + Math.random() * 2.5, gravity: -8, drag: 0.96 });
        if (frac < 0.28 && Math.random() < 0.5) {
          this.particles.spawn({ x: sx, y: sy, vx: (Math.random() - 0.5) * 10, vy: -30 - Math.random() * 14, color: Math.random() < 0.5 ? PAL.fire : PAL.fireBright, maxLife: 0.45, size: 2.4 + Math.random() * 2, gravity: -30, glow: true });
        }
      }
    }

    // ---- camera movement ----
    if (!this.ingameMenu && !this.chatOpen) {
      const camSpeed = this.settings.scrollSpeed / this.camera.zoom;
      const edge = 16;
      let dx = 0;
      let dy = 0;
      // Panning is held rather than tapped, so it reads the bound key's state
      // directly. `isDown` stores single characters lower-cased.
      const held = (id: "panUp" | "panDown" | "panLeft" | "panRight") => {
        const chord = chordFor(this.settings.keybinds, id);
        if (!chord || chord.includes("+")) return false; // a modifier chord can't be held sensibly
        return this.input.isDown(chord.length === 1 ? chord.toLowerCase() : chord);
      };
      if (held("panUp")) dy -= 1;
      if (held("panDown")) dy += 1;
      if (held("panLeft")) dx -= 1;
      if (held("panRight")) dx += 1;
      // edge scroll (only with a real mouse, when enabled and inside the window)
      if (this.settings.edgeScroll && !this.input.usingTouch && this.input.mx >= 0 && this.input.my >= 0) {
        if (this.input.mx < edge) dx -= 1;
        if (this.input.mx > W - edge) dx += 1;
        if (this.input.my < edge) dy -= 1;
        if (this.input.my > H - edge) dy += 1;
      }
      if (dx || dy) {
        const l = Math.hypot(dx, dy) || 1;
        this.camera.pan((dx / l) * camSpeed * dt, (dy / l) * camSpeed * dt);
      }
      // Hold the middle button and drag to grab the map.
      if (this.input.middleDown && this.middleLast) {
        this.camera.pan(-(this.input.mx - this.middleLast.x) / this.camera.zoom, -(this.input.my - this.middleLast.y) / this.camera.zoom);
      }
      this.middleLast = this.input.middleDown ? { x: this.input.mx, y: this.input.my } : null;
    }

    // ---- markers age ----
    for (const m of this.markers) m.age += dt;
    this.markers = this.markers.filter((m) => m.age < 0.7);
    for (const l of this.builderLines) l.age += dt;
    this.builderLines = this.builderLines.filter((l) => l.age < 1.6);
    this.renderer.builderLines = this.builderLines;

    // ---- ghost placement validity ----
    let ghost: GhostPlacement | null = null;
    let suppressDragBox = false;
    this.placementInfo = null;
    // Over the HUD, the cursor is for the HUD: no ghost, no panel.
    if (this.placing && !this.hud.overHud(this.canvas.width / this.uiScale(), this.canvas.height / this.uiScale(), this.input.mx / this.uiScale(), this.input.my / this.uiScale())) {
      const wx = this.camera.screenToWorldX(this.input.mx);
      const wy = this.camera.screenToWorldY(this.input.my);
      const def = BUILDINGS[this.placing];
      const p = world.player(this.me);
      // The sim's own checks and its real (discounted) price — the preview and
      // the placement can't disagree.
      const cost = world.buildingCostFor(this.me, this.placing);
      const problem = world.placementProblem(this.me, this.placing, wx, wy);
      ghost = { type: this.placing, x: wx, y: wy, valid: !problem, team: this.me };
      const builders = this.playerSelection().filter((e) => UNITS[e.type]?.canBuild);
      ghost.builders = builders.slice(0, 12).map((b) => ({ x: b.x, y: b.y }));
      // Drop-off buildings: which resources would this one serve?
      let coverage = "";
      const kinds = def.dropoffKinds.filter(() => this.placing !== "town_center");
      if (kinds.length) {
        const R = 8 * TILE;
        const nodeKind: Record<string, string> = { tree: "wood", gold_mine: "gold", berries: "food", farm: "food" };
        const nodes = world.entities.filter((e) => e.alive && (e.kind === Kind.Resource || e.type === "farm") && kinds.includes(nodeKind[e.type] ?? "")
          && Math.hypot(e.x - wx, e.y - wy) < R && (e.kind !== Kind.Resource || world.fogAt(this.me, e.x, e.y) !== 0));
        ghost.coverage = { radius: R, nodes: nodes.map((n) => ({ x: n.x, y: n.y, r: n.radius })) };
        const label: Record<string, [string, string]> = { tree: ["tree", "trees"], gold_mine: ["gold mine", "gold mines"], berries: ["berry bush", "berry bushes"], farm: ["farm", "farms"] };
        const counts = new Map<string, number>();
        for (const n of nodes) counts.set(n.type, (counts.get(n.type) ?? 0) + 1);
        coverage = counts.size ? [...counts.entries()].map(([t, n]) => `${n} ${label[t]?.[n === 1 ? 0 : 1] ?? t}`).join(", ") + " in reach" : `No ${kinds.join("/")} within reach`;
      }
      this.placementInfo = { type: this.placing, cost, problem, builders: builders.length, coverage, short: !world.canAfford(p.resources, cost) };
      // Drag-painting: preview the whole snapped run instead of a selection box.
      if (this.input.drag.active) {
        const d = this.input.drag;
        const pts = this.dragPlacements(
          this.placing,
          this.camera.screenToWorldX(d.x0), this.camera.screenToWorldY(d.y0),
          this.camera.screenToWorldX(d.x1), this.camera.screenToWorldY(d.y1),
        );
        // Mark them invalid once the treasury runs out rather than only once the
        // ground does, so a twelve-house drag shows you where the wood stops.
        let budget = { ...p.resources };
        ghost.line = pts.map((pt) => {
          const ok = !world.placementProblem(this.me, this.placing!, pt.x, pt.y, true) && world.canAfford(budget, cost);
          if (ok) budget = { food: budget.food - cost.food, wood: budget.wood - cost.wood, gold: budget.gold - cost.gold };
          return { x: pt.x, y: pt.y, valid: ok };
        });
        // What you are about to spend, before you spend it. Counting only the
        // placements that will actually land, since the greyed-out tail costs
        // nothing and reporting it would be a lie.
        const n = ghost.line.filter((pt) => pt.valid).length;
        const parts: string[] = [];
        if (cost.wood) parts.push(`${cost.wood * n} wood`);
        if (cost.gold) parts.push(`${cost.gold * n} gold`);
        if (cost.food) parts.push(`${cost.food * n} food`);
        const unit = n === 1 ? "" : "s";
        this.placementInfo.drag = n > 0
          ? `${n} ${LINE_BUILDABLE.has(this.placing) ? `segment${unit}` : `building${unit}`}${parts.length ? `  ·  ${parts.join(", ")}` : ""}${n < pts.length ? `  (${pts.length - n} won't fit)` : ""}`
          : "Nothing fits there";
        suppressDragBox = true;
      }
    }

    // ---- render world ----
    const selected = this.playerSelection();
    const rallyFrom = selected.filter(
      (e) => e.kind === Kind.Building && BUILDINGS[e.type]?.trains.length && e.rallyX >= 0,
    );
    // Pop a health bar above whatever the cursor is over (no click needed).
    let hoveredId = -1;
    if (!this.placing && this.input.mx >= 0 && this.input.my >= 0) {
      const hw = this.camera.screenToWorldX(this.input.mx);
      const hh = this.camera.screenToWorldY(this.input.my);
      const he = world.entityAt(hw, hh);
      if (he && he.kind !== Kind.Projectile && world.visibleTo(this.me, he)) hoveredId = he.id;
    }
    // Interpolation factor: how far we are into the next sim tick (0..1).
    const acc = this.net ? this.netAccumulator : this.accumulator;
    const alpha = (!this.net && (this.ingameMenu || this.paused)) ? 1 : Math.min(1, acc / SIM_DT);
    this.renderer.render(
      world, this.camera, this.particles, dt, this.time, this.me,
      this.markers, ghost, suppressDragBox || this.hud.minimapDragging ? { active: false, x0: 0, y0: 0, x1: 0, y1: 0 } : this.input.drag, rallyFrom,
      hoveredId, alpha,
    );
    this.renderHovered = hoveredId;

    // ---- weather overlay (cosmetic, screen-space, over world & under HUD) ----
    if (this.settings.weather) {
      this.weather.render(this.renderer.ctx, W, H, dt, this.settings.reduceEffects ? 0.45 : 1);
    }

    // ---- HUD (consumes pointer if clicked over panels) ----
    // Pass the full selection (any team) so the info panel can show a clicked
    // enemy/neutral unit's health; the command card filters to own units.
    // Everything from here down is interface, so it draws through the scale.
    // Widgets lay out against UW/UH and the transform sizes them up; pointer
    // coordinates are divided to match, so hit-testing needs no special case.
    const uis = this.uiScale();
    const UW = W / uis, UH = H / uis;
    ui.pushScale(uis);
    // The Oath picker is modal: while it is up, nothing under it takes a click.
    const oathModal = this.hud.oathPicker.isOpen;
    const frameClicked = ui.clicked;
    if (oathModal) ui.clicked = false;
    if (!(this.spectating && this.caster.clean)) this.hud.draw(UW, UH, world, this.camera, this.me, this.selectedEntities(), dt, this.controller, this.attackMoveArmed, this.spectating, this.placing);
    // Beside the HUD's Menu button. Drawn here rather than in the HUD because
    // it needs the player's current binding for its tooltip, and the HUD
    // deliberately knows nothing about settings.
    if (!this.spectating) fullscreenButton(UW - 108, 5, 30, 24, { compact: true, size: 13, hotkey: this.fullscreenKeyLabel() });
    if (this.spectating) this.drawCaster(UW, UH, world);
    if (this.placing && !this.spectating && !this.ingameMenu) this.drawPlacementPanel(UW, UH);
    if (world.mode !== "conquest") this.drawModeStatus(UW, UH, world);
    this.drawControlGroups(UW, UH);
    if (!this.spectating) this.drawQoLBar(UW, UH); // the caster view has its own controls
    if (this.showProduction && !this.spectating) {
      const jump = drawProductionPanel(UW, UH, world, this.me);
      if (jump != null) {
        const b = world.byId.get(jump);
        if (b) { this.select([jump]); this.camera.centerOn(b.x, b.y); }
      }
    }
    if (this.showScoreboard) drawScoreboard(UW, UH, world, this.me);
    if (this.net || this.chatLog.length) drawChat(UW, UH, this.chatLog, this.net && this.chatOpen ? this.chatDraft : null, this.time, UH - MINIMAP_SIZE - 70);
    if (this.hud.oathPicker.isOpen && !this.spectating) {
      // Only a click from a frame where it was already open counts — the click
      // that opened it must not also choose an Oath.
      ui.clicked = oathModal ? frameClicked : false;
      this.hud.oathPicker.draw(UW, UH, world, this.me, this.time, (b, techId) => this.controller.research(b, techId));
    }
    ui.flushTooltip(UW, UH);

    // ---- in-game menu overlay ----
    if (this.ingameMenu) {
      const ctx = this.renderer.ctx;
      ctx.fillStyle = "rgba(8, 6, 3, 0.6)";
      ctx.fillRect(0, 0, UW, UH);
      ui.panel(UW / 2 - 150, UH / 2 - 130, 300, 374, { light: true });
      ui.text("Paused", UW / 2, UH / 2 - 100, { align: "center", size: 22, bold: true, color: PAL.uiAccent });
      if (ui.button("Resume", UW / 2 - 110, UH / 2 - 64, 220, 44, { accent: true, size: 16 })) {
        this.ingameMenu = false;
      }
      if (ui.button("⚙  Settings", UW / 2 - 110, UH / 2 - 12, 220, 44, { size: 15 })) {
        this.openSettings("match"); // returns to the (still-paused) match
      }
      if (ui.button("💾  Save Game", UW / 2 - 110, UH / 2 + 40, 220, 44, {
        size: 15,
        disabled: !!this.net,
        tooltip: this.net
          ? ["Not in multiplayer", "Resuming one side of a lockstep match would desync the others."]
          : ["Save this match", "Stores the setup and every order you gave — reloading replays them."],
      })) {
        this.saveMatch();
      }
      fullscreenButton(UW / 2 - 110, UH / 2 + 92, 220, 44, { size: 15, hotkey: this.fullscreenKeyLabel() });
      if (ui.button("Concede & Quit", UW / 2 - 110, UH / 2 + 144, 220, 44, { danger: true, size: 15 })) {
        this.finishMatch(false);
      }
    }
    ui.popScale();

    this.updateCursor(world, this.renderHovered);
    this.hud.shiftHeld = this.input.shift;

    // ---- route unconsumed pointer input to the world ----
    // Spectators can still left-click/drag to select-and-inspect units, but
    // issue no commands.
    if (!this.ingameMenu && !oathModal && !this.hud.oathPicker.isOpen) {
      if (this.frameDragEnd && !ui.pointerConsumed && !this.hud.minimapDragEnded) {
        if (!this.spectating && this.placing) this.paintWallLine(this.frameDragEnd);
        else this.worldDragSelect(this.frameDragEnd);
      }
      if (this.frameDouble && !ui.pointerConsumed) {
        this.worldDoubleClick(this.frameDouble.x, this.frameDouble.y);
      } else if (this.frameClick && !ui.pointerConsumed) {
        this.worldClick(this.frameClick.x, this.frameClick.y);
      }
      if (!this.spectating && this.frameRight && !ui.pointerConsumed) {
        if (this.placing) {
          this.placing = null;
        } else {
          const wx = this.camera.screenToWorldX(this.frameRight.x);
          const wy = this.camera.screenToWorldY(this.frameRight.y);
          this.issueContextCommand(wx, wy, this.frameRight);
        }
      }
    }

    // ---- victory / defeat ----
    if (this.spectating) {
      // No stake in the fight — just announce the victor and bow out to the
      // menu. A replay stays open: the caster may want to scrub back.
      if (world.winner !== null && this.matchOverTimer < 0 && !this.replay) {
        this.matchOverTimer = 8.0;
        this.hud.addAlert(`🏆 ${this.teamLabel(world.winner)} wins the battle!`);
        audio.play("levelup");
      }
      if (this.matchOverTimer > 0) {
        this.matchOverTimer -= dt;
        if (this.matchOverTimer <= 0) this.exitToMenu();
      }
    } else {
      // The match ends for the human when a winner is decided, or the moment
      // their own realm is wiped out (the AIs may fight on, but the human is out).
      const playerOut = world.player(this.me).defeated;
      // Every client simulates the same match, so each reports the same
      // winner; the server rates it when the reports agree.
      if (this.net && world.winner !== null && !this.resultSent) {
        this.resultSent = true;
        try { this.net.transport.send({ t: "result", winner: world.winner, time: Math.round(world.time), factions: this.netFactions }); } catch { /* the match still ends */ }
      }
      if ((world.winner !== null || playerOut) && this.matchOverTimer < 0) {
        this.matchOverTimer = 1.8;
        // Win if your alliance is the last standing (and you're still in it).
        this.playerWon = world.winner !== null && world.areAllied(world.winner, this.me) && !playerOut;
        this.hud.addAlert(this.playerWon ? "🏆 The last enemy is broken!" : "💀 Your last building has fallen…");
        if (this.playerWon) audio.play("levelup");
        else audio.play("collapse");
      }
      if (this.matchOverTimer > 0) {
        this.matchOverTimer -= dt;
        if (this.matchOverTimer <= 0) this.finishMatch(this.playerWon);
      }
    }
  }

  /** A readable name for a team, used in spectator callouts. */
  private teamLabel(t: Team): string {
    return teamLabel(t);
  }

  /** Top-centre objective readout for the non-Conquest game modes. */
  private drawModeStatus(W: number, H: number, world: World) {
    const fmt = (s: number) => `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, "0")}`;
    let line = "";
    if (world.mode === "survival") {
      const wave = world.survivalWave;
      const total = world.survivalWavesTotal;
      const hordeLeft = world.hordeTeam >= 0 ? world.entitiesOf(world.hordeTeam as Team, Kind.Unit).length : 0;
      line = world.survivalWon
        ? `Final wave! Clear ${hordeLeft} remaining to win`
        : `🌊 Wave ${wave} / ${total}` + (hordeLeft > 0 ? `   ·   ${hordeLeft} enemies left` : "   ·   brace for the next wave");
    } else if (world.mode === "koth") {
      // Show the player's alliance hold vs the best enemy hold.
      const prog = world.kothProgress();
      let mine = 0;
      let foe = 0;
      for (const { team, hold } of prog) {
        if (world.areAllied(this.me, team)) mine = Math.max(mine, hold);
        else foe = Math.max(foe, hold);
      }
      line = `👑 Hold the Hill — You ${fmt(mine)} / ${fmt(world.kothGoal)}   ·   Enemy ${fmt(foe)}`;
    } else if (world.mode === "regicide") {
      const myKing = world.entities.some((e) => e.alive && e.type === "king" && world.areAllied(this.me, e.team));
      const foeKings = world.entities.filter((e) => e.alive && e.type === "king" && !world.areAllied(this.me, e.team)).length;
      line = `♚ Regicide — Your King: ${myKing ? "alive" : "FALLEN"}   ·   Enemy Kings: ${foeKings}`;
    }
    if (!line) return;
    const w = 460;
    ui.panel(W / 2 - w / 2, 38, w, 28, { light: true });
    ui.text(line, W / 2, 56, { align: "center", size: 14, bold: true, color: PAL.uiAccent });
  }

  /**
   * Rich live spectator HUD: one detailed card per realm (army, villagers, pop,
   * resources, gathered, K/L, bases + a military-strength bar). Drawing lives in
   * ui/spectator; here we layer click-to-follow onto the returned card rects.
   */
  private drawSpectatorHud(W: number, H: number, world: World) {
    const rects = drawSpectatorPanels(W, H, world, this.gameSpeed, this.paused);
    for (const r of rects) {
      if (!ui.hit(r.x, r.y, r.w, r.h)) continue;
      ui.pointerConsumed = true;
      if (ui.clicked) {
        const fx = r.focus ? r.focus.x : world.map.starts[r.team]?.x ?? world.worldW / 2;
        const fy = r.focus ? r.focus.y : world.map.starts[r.team]?.y ?? world.worldH / 2;
        this.camera.centerOn(fx, fy);
        audio.play("ui");
      }
    }
  }

  // ------------------------------------------------------------ caster --
  private drawCaster(W: number, H: number, world: World) {
    const req = this.caster.draw(W, H, {
      world,
      names: this.playerNames,
      history: this.matchHistory,
      speed: this.gameSpeed,
      paused: this.paused,
      source: this.replay ? "replay" : this.net && this.casterDelayTicks > 0 ? "delay" : "live",
      delaySec: this.net ? this.net.behindSeconds(SIM_HZ) : 0,
      replay: this.replay ? { tick: world.tickCount, endTick: this.replay.endTick } : undefined,
      offline: !this.net,
    });
    if (req.exit) { this.exitToMenu(); return; }
    if (req.download && this.replay) {
      const f = replayFile(this.replay.rec);
      this.hud.addAlert(downloadText(f.name, f.text) ? `⬇ Saved ${f.name}` : "Your browser blocked the download.");
    }
    if (req.focus) { this.camera.centerOn(req.focus.x, req.focus.y); this.caster.manual(world); audio.play("ui"); }
    if (req.speed !== undefined && !this.net) this.setSpeed(req.speed);
    if (req.togglePause && !this.net) { this.paused = !this.paused; if (this.replay?.ended && !this.paused) this.paused = true; }
    if (req.seekTo !== undefined && this.replay) this.seekReplay(req.seekTo);
    if (this.replaySeek !== null) {
      const pct = this.replay ? Math.round((world.tickCount / Math.max(1, this.replaySeek)) * 100) : 0;
      ui.panel(W / 2 - 150, H / 2 - 30, 300, 60);
      ui.text(`Jumping to ${Math.floor(this.replaySeek / SIM_HZ / 60)}:${String(Math.floor((this.replaySeek / SIM_HZ) % 60)).padStart(2, "0")}… ${pct}%`, W / 2, H / 2 + 5, { align: "center", size: 15, bold: true, color: "#ffe9b0" });
    }
  }

  /** A replay's orders for this tick, applied before it runs. True once it has ended. */
  private replayStep(world: World): boolean {
    const rp = this.replay!;
    if (this.replaySeek !== null) return true;
    if (world.tickCount >= rp.endTick) {
      if (!rp.ended) {
        rp.ended = true;
        this.paused = true;
        this.hud.addAlert("⏹ End of the recording — scrub the timeline to watch again, or Esc to leave.");
      }
      return true;
    }
    const due = rp.byTick.get(world.tickCount);
    if (due) for (const c of due) applyCommand(world, c);
    return false;
  }

  /** What the cursor panel says while placing a building. */
  private placementInfo: { type: string; cost: { food: number; wood: number; gold: number }; problem: string | null; builders: number; coverage: string; short: boolean; drag?: string } | null = null;

  /** A brief line from each assigned builder to the site, so you see who's going. */
  private flashBuilders(builders: Entity[], x: number, y: number) {
    for (const b of builders.slice(0, 12)) this.builderLines.push({ x0: b.x, y0: b.y, x1: x, y1: y, age: 0 });
  }
  builderLines: { x0: number; y0: number; x1: number; y1: number; age: number }[] = [];
  private middleLast: { x: number; y: number } | null = null;
  private renderHovered = -1;
  private cursorNow = "";
  private popcapAlertAt = -99;

  /**
   * The pointer says what a right-click would do: attack over an enemy,
   * gather over a resource, build/repair over your own unfinished or damaged
   * building, place while placing.
   */
  private updateCursor(world: World, hoveredId: number) {
    let c = "default";
    if (this.state === "match" && !this.spectating && !this.ingameMenu && !ui.pointerConsumed) {
      const sel = this.playerSelection();
      const he = hoveredId >= 0 ? world.byId.get(hoveredId) : undefined;
      const units = sel.filter((e) => e.kind === Kind.Unit);
      const vills = units.filter((e) => UNITS[e.type]?.canBuild);
      if (this.placing) c = "crosshair";
      else if (this.attackMoveArmed) c = CURSORS.attack;
      else if (he && units.length) {
        if (he.team !== this.me && he.kind !== Kind.Resource && world.areHostile(this.me, he.team)) c = CURSORS.attack;
        else if (vills.length && (he.kind === Kind.Resource || he.type === "farm")) c = CURSORS.gather;
        else if (vills.length && he.team === this.me && he.kind === Kind.Building && (he.buildState !== BuildState.Done || he.hp < he.maxHp)) c = CURSORS.build;
        else if (he.team === this.me && he.kind === Kind.Building && (BUILDINGS[he.type]?.garrisonCap ?? 0) > 0) c = "pointer";
      }
    }
    if (c !== this.cursorNow) {
      this.cursorNow = c;
      try { this.canvas.style.cursor = c; } catch { /* headless */ }
    }
  }

  /** The panel beside the cursor while placing: name, real cost, why not, what it serves. */
  private drawPlacementPanel(W: number, H: number) {
    const info = this.placementInfo;
    if (!info || !this.world) return;
    const def = BUILDINGS[info.type];
    const res = this.world.player(this.me).resources;
    const rows: { text: string; color: string; bold?: boolean; size?: number }[] = [];
    const costParts = (["food", "wood", "gold"] as const).filter((k) => info.cost[k]).map((k) => `${info.cost[k]} ${k}`);
    rows.push({ text: `${def?.name ?? info.type}${costParts.length ? `  ·  ${costParts.join(", ")}` : ""}`, color: info.short ? PAL.uiBad : "#ffe9b0", bold: true, size: 13.5 });
    if (info.drag) rows.push({ text: info.drag, color: "#e2d6ba" });
    else if (info.problem) rows.push({ text: info.problem, color: PAL.uiBad, bold: true });
    else if (info.short) {
      const need = (["food", "wood", "gold"] as const).filter((k) => res[k] < info.cost[k]).map((k) => `${Math.ceil(info.cost[k] - res[k])} more ${k}`);
      rows.push({ text: `Need ${need.join(" and ")}`, color: PAL.uiBad, bold: true });
    }
    if (info.coverage) rows.push({ text: info.coverage, color: info.coverage.startsWith("No ") ? "#e0a070" : "#9fd08a" });
    if (!info.builders) rows.push({ text: "No villager selected — it will wait for a builder", color: "#e0a070" });
    else rows.push({ text: `${info.builders} villager${info.builders === 1 ? "" : "s"} will build it`, color: "#a89f88" });
    rows.push({ text: LINE_BUILDABLE.has(info.type) ? "Drag to lay a line · right-click to stop" : BLOCK_BUILDABLE.has(info.type) ? "Drag for a block · Shift: keep placing · right-click: cancel" : "Shift: keep placing · right-click: cancel", color: "#8f8770", size: 11 });
    const ctx = this.renderer.ctx;
    ctx.font = "bold 13px 'Trebuchet MS', sans-serif";
    const w = Math.max(...rows.map((r) => { ctx.font = `${r.bold ? "bold " : ""}${r.size ?? 12}px 'Trebuchet MS', sans-serif`; return ctx.measureText(r.text).width; })) + 20;
    const h = rows.length * 18 + 10;
    let x = this.input.mx + 22, y = this.input.my + 18;
    if (x + w > W - 8) x = this.input.mx - w - 16;
    if (y + h > H - 8) y = this.input.my - h - 12;
    ctx.fillStyle = "rgba(12,9,5,0.86)";
    ctx.beginPath(); ctx.roundRect(x, y, w, h, 6); ctx.fill();
    ctx.strokeStyle = info.problem ? withAlpha(PAL.uiBad, 0.6) : withAlpha(PAL.uiAccent, 0.4);
    ctx.lineWidth = 1;
    ctx.stroke();
    rows.forEach((r, i) => ui.text(r.text, x + 10, y + 20 + i * 18, { size: r.size ?? 12, bold: r.bold, color: r.color }));
  }

  /** Where a replay is jumping to, run a slice at a time so the screen never freezes. */
  private replaySeek: number | null = null;

  private seekReplay(tick: number) {
    const rp = this.replay;
    if (!rp || !this.world) return;
    const target = Math.max(0, Math.min(tick, rp.endTick));
    // The sim only runs forwards: going back means starting again.
    if (target < this.world.tickCount) this.startReplay(rp.rec, true);
    this.replaySeek = target;
    this.paused = false;
  }

  /** Run part of a pending seek; called once per frame. */
  private advanceSeek() {
    const world = this.world;
    const rp = this.replay;
    if (!world || !rp || this.replaySeek === null) return;
    const t0 = performance.now();
    while (world.tickCount < this.replaySeek && performance.now() - t0 < 28) {
      const due = rp.byTick.get(world.tickCount);
      if (due) for (const c of due) applyCommand(world, c);
      world.tick();
      for (const ai of this.ais) ai.update(SIM_DT);
      if (world.tickCount % (SIM_HZ * 4) === 0) this.sampleHistory(world);
    }
    world.drainEvents(); // a jump's worth of events would be noise
    if (world.tickCount >= this.replaySeek) {
      this.replaySeek = null;
      this.accumulator = 0;
      rp.ended = false;
    }
  }

  /** A replay file's text (dropped, or opened): keep it and play it. */
  openReplayText(name: string, text: string) {
    const res = parseReplayFile(text);
    if (!res.ok) {
      this.state = "career";
      this.careerScreen.showReplays();
      this.careerScreen.setNotice(`${name}: ${res.error}`, true);
      return;
    }
    saveReplay(res.replay);
    this.careerScreen.setNotice(res.warning ?? `Opened ${name}.`, !!res.warning);
    this.startReplay(res.replay);
    if (res.warning) this.hud.addAlert(`⚠ ${res.warning}`);
  }

  /** Watch a recorded match with the caster view. */
  startReplay(rec: ReplayRecord, quiet = false) {
    const setup = rec.setup as SkirmishConfig | undefined;
    if (rec.mapCode && setup?.presetId?.startsWith("custom_")) {
      const m = deserialiseMap(rec.mapCode);
      if (m) { m.id = setup.presetId; m.published = undefined; saveCustomMap(m); }
    }
    this.loadingSave = true;
    try {
      if (rec.kind === "online" && rec.net) this.buildReplayNetWorld(rec.net);
      else if (rec.kind === "watch" && setup) this.startSpectate({ ...setup });
      else if (setup) this.startMatch({ ...setup });
    } finally {
      this.loadingSave = false;
    }
    const world = this.world;
    if (!world) return;
    this.spectating = true;
    world.revealAll = true;
    this.replay = { rec, byTick: byTick(rec.commands), endTick: rec.endTick, ended: false };
    this.replaySeek = null;
    this.playerNames = [...rec.names];
    if (!quiet) {
      this.caster.reset();
      this.gameSpeed = 1;
      this.camera.zoom = 0.85;
      this.camera.centerOn(world.worldW / 2, world.worldH / 2);
      this.hud.alerts = [];
      this.hud.addAlert(`▶ Replay — ${rec.summary.map}, ${rec.summary.players} players. Press ? for caster keys.`);
    }
    this.paused = false;
  }

  /** Rebuild an online match's opening exactly as startNetMatch did, without a network. */
  private buildReplayNetWorld(net: NetReplaySetup) {
    const custom = net.map.code ? deserialiseMap(net.map.code) : null;
    const map = custom
      ? toMapData(custom, net.seed, net.numTeams, false)
      : generateMap(PRESETS.some((p) => p.id === net.map.id) || net.map.id === "random" ? net.map.id : "open_plains", net.seed, net.numTeams, false, net.alliances);
    const world = new World(net.seed);
    const teams = Array.from({ length: net.numTeams }, (_, i) => i);
    world.init(map, teams.map(() => this.profile.matchLoadout(true)), teams.map(() => 1), net.alliances, teams.map(() => ""), false, undefined, "conquest", net.factions);
    this.endNet();
    this.world = world;
    this.ais = [];
    this.renderer.prepare(map);
    this.mapName = map.name;
    this.weather.configure(map.seed, map.name);
    this.renderer.clearFx();
    this.hud.prepare(map);
    this.particles.clear();
    this.selection = [];
    this.markers = [];
    this.placing = null;
    this.ingameMenu = false;
    this.matchOverTimer = -1;
    this.camera.setWorld(map.worldW, map.worldH);
    this.accumulator = 0;
    this.resetMatchTelemetry();
    this.me = Team.Player;
    this.state = "match";
  }

  /** Keep this match as a replay (called as it ends). */
  private recordReplay(world: World, kind: ReplayRecord["kind"]) {
    if (this.replay || world.tickCount < SIM_HZ * 10) return; // a replay of a replay, or a false start
    const players = Array.from({ length: world.numTeams }, (_, t) => t).filter((t) => t !== world.hordeTeam);
    const winner = world.winner !== null && world.winner !== Team.Neutral ? world.winner : null;
    const setup = this.config ? { ...this.config } : undefined;
    let mapCode: string | undefined;
    if (setup?.presetId?.startsWith("custom_")) { const m = findCustomMap(setup.presetId); if (m) mapCode = serialiseMap(m); }
    const commands = kind === "online" ? [...(this.net?.lock?.record ?? [])].map(({ t, c }) => ({ t, c })) : kind === "watch" ? [] : this.cmdLog.entries.slice();
    saveReplay({
      version: 1, id: replayId(), savedAt: Date.now(), kind, sim: SAVE_FORMAT_VERSION,
      setup: kind === "online" ? undefined : setup, mapCode, net: kind === "online" ? this.netSetup ?? undefined : undefined,
      names: [...this.playerNames], pov: this.spectating ? -1 : this.me, commands, endTick: world.tickCount,
      summary: {
        map: this.mapName || "Battlefield", players: players.length, durationSec: Math.round(world.time),
        result: winner === null ? "Unfinished" : `${this.playerNames[winner] || PAL.teams[winner].name} won`,
        factions: players.map((t) => world.player(t as Team).faction),
      },
    });
  }

  /** Tear down the current match and return to the main menu (spectator exit). */
  private exitToMenu() {
    if (this.world && this.spectating && !this.replay) this.recordReplay(this.world, this.net ? "online" : "watch");
    const wasReplay = !!this.replay;
    this.replay = null;
    this.replaySeek = null;
    if (this.world) this.world.revealAll = false;
    this.world = null;
    this.ais = [];
    this.spectating = false;
    this.ingameMenu = false;
    this.matchOverTimer = -1;
    this.endNet();
    this.me = Team.Player;
    this.state = wasReplay ? "career" : "menu";
    if (wasReplay) this.careerScreen.showReplays();
    // Watching an online match: back to that server's hub.
    if (this.netHub) {
      const hub = this.netHub;
      this.netHub = "";
      this.lobby.reopenHub((start) => this.startNetMatch(start), hub, this.lobbyOptions());
    }
  }

  /**
   * The single place a match decides what it is being played on. A preset id is
   * generated; a custom map id is looked up and converted. Everything else —
   * skirmish, spectate, testing from the editor — goes through this, so a
   * custom map is never a second-class citizen with its own code path.
   */
  private resolveMap(presetId: string, seed: number, players: number, nomad: boolean, alliances?: number[]) {
    // Random draws from the whole pool, published maps included. The result
    // is written back into the config so a save records the field it was on.
    if (presetId === "random" && this.config) {
      presetId = rollRandomMap(seed, mapPool(this.config.mode ?? "conquest", this.config.mode === "survival" ? players - 1 : players), PRESETS.length);
      this.config.presetId = presetId;
    }
    if (presetId.startsWith("custom_")) {
      const m = findCustomMap(presetId);
      if (m) return toMapData(m, seed, players, nomad);
      this.hud.addAlert("That custom map is missing — falling back to a generated one.");
    }
    return generateMap(presetId, seed, players, nomad, alliances);
  }

  /** What the lobby needs to know about this player. */
  private lobbyOptions() {
    return { name: this.profile.data.name, factions: this.profile.ownedFactions(), faction: this.profile.playableFaction() };
  }

  /** Open the Factions book on a faction, remembering where to come back to. */
  private openFactions(from: AppState, focus?: string) {
    this.factionsReturn = from;
    this.factionBook.focus(focus);
    this.state = "factions";
    audio.play("ui");
  }

  /** Play the map currently open in the editor, and come back to it after. */
  testCustomMap(m: CustomMap) {
    saveCustomMap(m); // so resolveMap can find it by id
    this.setup.config = {
      ...this.setup.config,
      presetId: m.id,
      players: Math.max(m.minPlayers, Math.min(m.maxPlayers, 2)),
      mode: m.modes.length ? m.modes[0] : "conquest",
      nomad: m.nomad === "forced",
      seed: randomSeed(),
    };
    this.editorReturn = true;
    this.startMatch({ ...this.setup.config });
  }

  /**
   * Each realm's faction: yours as picked, each bot's as set on the setup
   * screen or else drawn from the match seed. Its own random stream, so the
   * picks don't disturb anything else the seed decides — and so a save, which
   * is the config plus the orders, rebuilds the same match.
   */
  private factionsFor(config: SkirmishConfig, n: number, humanFirst: boolean): string[] {
    const rng = new RNG((config.seed ^ 0xfac7105) >>> 0);
    const valid = (id: string | undefined) => !!id && id in FACTIONS;
    const out: string[] = [];
    for (let t = 0; t < n; t++) {
      const roll = FACTION_IDS[rng.int(0, FACTION_IDS.length - 1)];
      // Yours has to be one you own (a save replays as it was played).
      if (t === 0 && humanFirst) out.push(valid(config.faction) && (this.loadingSave || this.profile.ownsFaction(config.faction)) ? config.faction : (this.profile.playableFaction() ?? DEFAULT_FACTION));
      else out.push(valid(config.aiFactions?.[t]) ? config.aiFactions[t] : roll);
    }
    return out;
  }

  finishMatch(won: boolean) {
    const world = this.world!;
    const p = world.player(this.me);
    this.matchWon = won;
    this.endReport = matchReport(world, this.me, this.mapName || "Skirmish");
    this.endDuration = world.time;
    // Aggregate the time-series into your-alliance vs enemies for the graphs.
    if (this.matchHistory.length >= 2) {
      const keys = ["score", "military", "economy"] as const;
      const mine: Record<string, number[]> = {};
      const foeS: Record<string, number[]> = {};
      for (const k of keys) {
        mine[k] = this.matchHistory.map((s) => s.m.reduce((a, tm) => a + (world.areAllied(this.me, tm.team) ? tm[k] : 0), 0));
        foeS[k] = this.matchHistory.map((s) => s.m.reduce((a, tm) => a + (world.areHostile(this.me, tm.team) ? tm[k] : 0), 0));
      }
      // And a line per realm, so a free-for-all isn't drawn as you against
      // the sum of everyone else.
      const players = Array.from({ length: world.numTeams }, (_, t) => {
        const values: Record<string, number[]> = {};
        for (const k of keys) values[k] = this.matchHistory.map((s) => s.m[t]?.[k] ?? 0);
        return { team: t, you: t === this.me, horde: t === world.hordeTeam, values };
      });
      this.endGraph = { ts: this.matchHistory.map((s) => s.t), mine, foe: foeS, players };
    } else {
      this.endGraph = null;
    }
    this.xpBefore = this.profile.data.totalXp;
    const rewards = computeRewards({
      win: won,
      durationSec: world.time,
      unitsKilled: p.stats.unitsKilled,
      buildingsRazed: p.stats.buildingsRazed,
      difficulty: this.config?.difficulty ?? "knight",
      fairMode: this.config?.fairMode ?? false,
    });
    this.matchRewards = rewards;
    const lvl = this.profile.addXp(rewards.xp);
    this.levelsGained = lvl.levelsGained;
    this.profile.addRenown(rewards.renown);
    this.profile.addValor(rewards.valor);
    this.profile.recordResult(won);
    // Keep the game. The deep report is thrown away at Continue, so a summary
    // goes to the history store before it is; awards are then evaluated off
    // that same record, which keeps a condition to something that can actually
    // be read from a finished match.
    const record = summarise(this.endReport, {
      won,
      mode: this.config?.mode ?? "conquest",
      difficulty: this.config?.difficulty ?? "knight",
      players: this.config?.players ?? 2,
      at: Date.now(),
    });
    recordMatch(record);
    // The career: every match, skirmish or online, for the player's own stats.
    const online = !!this.net;
    this.recordReplay(world, online ? "online" : "skirmish");
    recordCareer(careerMatch(this.endReport, {
      at: Date.now(),
      won,
      kind: online ? "online" : "skirmish",
      ranked: online && this.netRanked,
      mode: online ? "conquest" : this.config?.mode ?? "conquest",
      difficulty: this.config?.difficulty ?? "knight",
      commander: this.config?.commander || this.profile.data.commander,
    }));
    // A Nemesis match: the rival learns, rises, scars or falls.
    this.postmatch.rivalLines = [];
    if (this.config?.rival && !online && !this.replay) {
      const st = loadRival(this.profile.playableFaction() ?? "");
      const out = settleRival(st, careerMatch(this.endReport, { at: Date.now(), won, kind: "skirmish", mode: "conquest", difficulty: this.config.difficulty, commander: "" }), this.profile.playableFaction() ?? "");
      if (out.renown) this.profile.addRenown(out.renown);
      this.postmatch.rivalLines = out.lines;
    }
    this.matchAwards = this.profile.claimAwards(evaluateAwards(record, this.profile.awardState()));
    this.profile.save();
    this.postmatch.reset();
    this.world = null;
    this.ais = [];
    this.ingameMenu = false;
    this.endNet(); // close any net link and clear the session
    this.me = Team.Player; // back to the default perspective for the next match
    this.state = "postmatch";
  }
}

new App();
