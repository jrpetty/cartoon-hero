// Banner & Blade — application shell. Owns the canvas, the app state machine
// (menu → setup → match → post-match, plus the armory), the fixed-timestep
// sim loop, input routing and the meta-progression loop around each match.

import { World, WorldEvent } from "./sim/world";
import { BuildState, Entity, EntityId, Kind, MAX_TEAMS, OrderKind, Stance, Team } from "./sim/types";
import { UNITS } from "./content/units";
import { ABILITIES } from "./content/abilities";
import { BUILDINGS } from "./content/buildings";
import { COMMANDERS, COMMANDER_IDS } from "./content/commanders";
import { SIM_DT, TILE } from "./content/balance";
import { dayPhase } from "./content/daynight";
import { generateMap } from "./maps/generator";
import { SkirmishAI } from "./ai/skirmish_ai";
import { DIFFICULTIES } from "./ai/difficulty";
import { Camera } from "./engine/camera";
import { wallLinePoints as computeWallLine } from "./engine/wallline";
import { Input } from "./engine/input";
import { Particles } from "./engine/particles";
import { audio } from "./engine/audio";
import { Renderer, CommandMarker, GhostPlacement } from "./render/renderer";
import { loadSprites } from "./render/sprites";
import { PAL, withAlpha } from "./render/palette";
import { HUD, MatchController, MINIMAP_SIZE } from "./ui/hud";
import { drawSpectatorPanels, teamLabel } from "./ui/spectator";
import { ui } from "./ui/ui";
import { CodexScreen } from "./ui/codex";
import {
  ArmoryScreen,
  MenuScreen,
  PostMatchScreen,
  SetupScreen,
  setMouseDown,
  SkirmishConfig,
} from "./ui/screens";
import { Profile } from "./meta/profile";
import { computeRewards, MatchRewards } from "./meta/progression";
import { Command, applyCommand } from "./sim/commands";
import { NetSession } from "./net/session";
import { NetLobby, NetStart } from "./ui/net_lobby";
import { Settings, loadSettings, saveSettings } from "./meta/settings";
import { SettingsScreen } from "./ui/settings_screen";
import { setColorblindTeams } from "./render/palette";
import { TeamMetrics, snapshotMetrics } from "./sim/metrics";
import { drawScoreboard } from "./ui/scoreboard";
import { drawProductionPanel } from "./ui/production_panel";
import { Weather } from "./render/weather";
import { drawChat, ChatLine } from "./ui/chat";

type AppState = "menu" | "setup" | "armory" | "match" | "postmatch" | "codex" | "settings";

// Buildings you can drag-paint into a continuous run.
const LINE_BUILDABLE = new Set(["palisade", "stone_wall"]);

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
  codexScreen = new CodexScreen();
  settingsScreen = new SettingsScreen();
  settings: Settings = loadSettings();
  private settingsReturn: AppState = "menu"; // where Back from settings goes

  // Match state
  world: World | null = null;
  ais: SkirmishAI[] = [];
  config: SkirmishConfig | null = null;
  selection: EntityId[] = [];
  controlGroups: EntityId[][] = Array.from({ length: 10 }, () => []);
  lastGroupTap = { idx: -1, time: 0 };
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
  endGraph: { ts: number[]; mine: Record<string, number[]>; foe: Record<string, number[]> } | null = null;
  /** The team this client controls/views. 0 for single-player & the net host;
   *  the net joiner sets it to their team. (Was the old PLAYER constant.) */
  me: Team = Team.Player;
  net: NetSession | null = null; // active lockstep session in multiplayer
  private netAccumulator = 0;
  private netDesyncAlerted = false;
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
  endStats = { unitsKilled: 0, unitsLost: 0, buildingsRazed: 0, buildingsLost: 0, gathered: 0 };
  endFoeStats = { unitsKilled: 0, gathered: 0, buildingsRazed: 0 };
  endDuration = 0;

  // Frame-level input flags consumed by UI/world each frame.
  frameClick: { x: number; y: number } | null = null;
  frameDouble: { x: number; y: number } | null = null;
  frameRight: { x: number; y: number } | null = null;
  frameDragEnd: { x0: number; y0: number; x1: number; y1: number } | null = null;

  sfxCooldown = new Map<string, number>();
  time = 0;
  showDamageNumbers = true;
  private smokeTimer = 0;
  private accumulator = 0;
  private lastFrame = performance.now();

  constructor() {
    const root = document.getElementById("app")!;
    this.canvas = document.createElement("canvas");
    root.appendChild(this.canvas);
    this.renderer = new Renderer(this.canvas);
    this.input = new Input(this.canvas);
    this.resize();
    window.addEventListener("resize", () => this.resize());
    this.wireInput();
    this.applySettings();
    // Load any Meshy-baked sprites (no-op if none generated yet); procedural art
    // fills in until each image is ready, so we don't block startup on it.
    loadSprites().then((n) => { if (n) console.log(`[sprites] loaded ${n} baked models`); });
    requestAnimationFrame(() => this.frame());
  }

  resize() {
    this.canvas.width = window.innerWidth;
    this.canvas.height = window.innerHeight;
    this.camera.setViewport(this.canvas.width, this.canvas.height);
  }

  // ----------------------------------------------------------- input wiring --

  wireInput() {
    this.input.onLeftClick = (x, y) => {
      audio.resume();
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
    };
    this.input.onWheel = (x, y, delta) => {
      if (this.state === "match") {
        this.camera.zoomAt(x, y, delta > 0 ? 0.88 : 1.14);
      }
    };
    this.input.onKeyDown = (key) => this.handleKey(key);
  }

  handleKey(key: string) {
    if (this.state !== "match" || !this.world) return;
    // Chat capture takes precedence over every game hotkey while typing.
    if (this.chatOpen) { this.handleChatKey(key); return; }
    if (key === "Enter" && this.net) { this.chatOpen = true; return; } // open chat (MP only)
    const k = key.length === 1 ? key.toLowerCase() : key;
    if (k === "Escape") {
      if (this.spectating) this.exitToMenu();
      else if (this.placing) this.placing = null;
      else if (this.powerArmed) this.powerArmed = false;
      else if (this.attackMoveArmed) this.attackMoveArmed = false;
      else this.ingameMenu = !this.ingameMenu;
      return;
    }
    if (this.ingameMenu) return;
    // Pause / game-speed work in every mode (including spectating).
    if (k === "p" || k === " ") this.togglePause();
    if (k === "+" || k === "=") this.cycleSpeed(1);
    if (k === "-" || k === "_") this.cycleSpeed(-1);
    if (k === "Tab") this.showScoreboard = !this.showScoreboard; // live scoreboard
    if (k === "v") this.showProduction = !this.showProduction; // production overview
    // Spectators only watch — no army commands.
    if (this.spectating) return;
    if (k === "a") this.attackMoveArmed = true;
    if (k === "s") this.dispatch({ t: "stop", team: this.me, ids: this.playerSelection().map((e) => e.id) });
    if (k === "h") this.dispatch({ t: "hold", team: this.me, ids: this.playerSelection().map((e) => e.id) });
    if (k === "q") this.controller.useAbility();
    if (k === "b") {
      this.hud.buildMenuOpen = true;
      this.hud.buildCategory = null;
    }
    if (k === "g") this.garrisonSelected();
    if (k === "y") this.cycleStance();
    if (k === ".") this.selectIdleVillager();
    if (k === ",") this.selectAllArmy();
    if (k === "c") this.armCommanderPower();
    // Control groups.
    if (/^[0-9]$/.test(k)) {
      const idx = parseInt(k, 10);
      if (this.input.ctrl || this.input.alt) {
        this.controlGroups[idx] = this.playerSelection().map((e) => e.id);
        this.hud.addAlert(`Group ${idx} set (${this.controlGroups[idx].length})`);
      } else {
        const ids = this.controlGroups[idx].filter((id) => {
          const e = this.world!.byId.get(id);
          return e && e.alive;
        });
        if (ids.length) {
          const now = performance.now();
          this.select(ids);
          if (this.lastGroupTap.idx === idx && now - this.lastGroupTap.time < 350) {
            const first = this.world!.byId.get(ids[0])!;
            this.camera.centerOn(first.x, first.y);
          }
          this.lastGroupTap = { idx, time: now };
        }
      }
    }
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
    const next = (((units[0].stance as number) + 1) % 4) as Stance;
    this.dispatch({ t: "stance", team: this.me, ids: units.map((e) => e.id), stance: next });
    this.hud.addAlert(`Stance: ${["⚔ Aggressive", "🛡 Defensive", "⚑ Stand Ground", "✋ Passive"][next]}`);
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

  startMatch(config: SkirmishConfig) {
    this.config = config;
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
    else if (config.allied) alliances = Array.from({ length: numPlayers }, (_, t) => t % 2);
    const map = generateMap(config.presetId, config.seed, numPlayers, config.nomad, alliances);
    const world = new World(config.seed);
    // Team 0 is the human; the rest are AI (allies or opponents), plus the horde.
    const loadouts = [this.profile.matchLoadout(config.fairMode)];
    const econMults = [1];
    const commanders = [config.commander || this.profile.data.commander];
    const boonLoadouts: { id: string; rarity: number; age: number }[][] = [config.fairMode ? [] : this.profile.equippedBoonPlan()];
    for (let t = 1; t < numPlayers; t++) {
      loadouts.push(this.profile.matchLoadout(true));
      econMults.push(diff.econMult);
      commanders.push(COMMANDER_IDS[Math.floor(Math.random() * COMMANDER_IDS.length)]);
      boonLoadouts.push([]);
    }
    world.init(map, loadouts, econMults, alliances, commanders, config.nomad, boonLoadouts, mode);
    this.world = world;
    this.ais = [];
    for (let t = 1; t < numPlayers; t++) {
      if (t === hordeTeam) continue; // the horde has no brain — the sim spawns its waves
      this.ais.push(new SkirmishAI(world, t as Team, diff));
    }
    this.renderer.prepare(map);
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
    this.resetMatchTelemetry();
    this.endNet();
    this.me = Team.Player;
    this.state = "match";
    this.hud.addAlert(`${map.name} — vs ${diff.name}. Your villagers await orders!`);
    audio.play("complete");
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
    const map = generateMap("open_plains", seed, numTeams, false);
    const world = new World(seed);
    const loadouts = teams.map(() => this.profile.matchLoadout(true));
    const econMults = teams.map(() => 1);
    const commanders = teams.map(() => "");
    world.init(map, loadouts, econMults, alliances, commanders, false, undefined, "conquest");
    if (start.observer) world.revealAll = true; // casters see the whole board
    this.world = world;
    this.ais = [];
    this.net = new NetSession(transport, localTeam, teams, start.observer);
    this.me = localTeam;
    this.spectating = !!start.observer; // no commands, full vision, caster HUD
    this.net.attach(world, 5);
    this.net.onChat = (m) => { if (m.text) this.addChatLine(m.name || teamLabel((m.team ?? 0) as Team), m.text, (m.team ?? 0) as Team); };
    this.net.onPing = (m) => this.remotePing(m.x ?? 0, m.y ?? 0, (m.team ?? 0) as Team);
    transport.onClose = () => this.hud.addAlert("⚠ Connection lost.");
    this.renderer.prepare(map);
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
    const diff = DIFFICULTIES[config.difficulty];
    const mode = config.mode ?? "conquest";
    // Mirror startMatch's mode setup so Watch mode honours KotH / Regicide /
    // Survival (previously spectate always fell back to Conquest).
    const side = Math.max(2, Math.min(mode === "survival" ? MAX_TEAMS - 1 : MAX_TEAMS, config.players ?? 2));
    const numPlayers = mode === "survival" ? side + 1 : side;
    const hordeTeam = mode === "survival" ? side : -1;
    let alliances: number[] | undefined;
    if (mode === "survival") alliances = Array.from({ length: numPlayers }, (_, t) => (t === hordeTeam ? 1 : 0));
    else if (config.allied) alliances = Array.from({ length: numPlayers }, (_, t) => t % 2);
    const map = generateMap(config.presetId, config.seed, numPlayers, config.nomad, alliances);
    const world = new World(config.seed);
    const loadouts: Record<string, number>[] = [];
    const econMults: number[] = [];
    const commanders: string[] = [];
    for (let t = 0; t < numPlayers; t++) {
      loadouts.push(this.profile.matchLoadout(true)); // fair, all-Common loadouts
      econMults.push(diff.econMult);
      commanders.push(COMMANDER_IDS[Math.floor(Math.random() * COMMANDER_IDS.length)]);
    }
    world.init(map, loadouts, econMults, alliances, commanders, config.nomad, undefined, mode);
    world.revealAll = true; // spectators see the entire battlefield
    this.world = world;
    this.ais = [];
    for (let t = 0; t < numPlayers; t++) {
      if (t === hordeTeam) continue; // the horde is sim-driven, no brain
      this.ais.push(new SkirmishAI(world, t as Team, diff));
    }
    this.renderer.prepare(map);
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
    this.hud.addAlert(`👁 Spectating — ${map.name}, ${numPlayers} AI ${diff.name}s. Sit back.`);
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
    openMenu: () => {
      this.ingameMenu = true;
    },
  };

  // -------------------------------------------------------- world interaction --

  /** Snapped, de-duplicated tile centres along a drag, for wall painting/preview. */
  wallLinePoints(wx0: number, wy0: number, wx1: number, wy1: number): { x: number; y: number }[] {
    return computeWallLine(wx0, wy0, wx1, wy1, TILE);
  }

  /** Funnel every player action through here: queued for lockstep in a net game,
   *  applied immediately in single-player. Keeps both paths in one place. */
  dispatch(cmd: Command) {
    if (this.net?.lock) this.net.lock.localCommand(cmd);
    else if (this.world) applyCommand(this.world, cmd);
  }

  /** Drag-release while a wall is selected: lay a whole run of segments at once. */
  paintWallLine(box: { x0: number; y0: number; x1: number; y1: number }) {
    if (!this.world || !this.placing) return;
    const pts = this.wallLinePoints(
      this.camera.screenToWorldX(box.x0), this.camera.screenToWorldY(box.y0),
      this.camera.screenToWorldX(box.x1), this.camera.screenToWorldY(box.y1),
    );
    const villagers = this.playerSelection().filter((e) => UNITS[e.type]?.canBuild);
    let any = false;
    pts.forEach((pt, i) => {
      if (!this.world!.canPlace(this.me, this.placing!, pt.x, pt.y)) return;
      const v = villagers[i % Math.max(1, villagers.length)];
      this.dispatch({ t: "place", team: this.me, building: this.placing!, x: pt.x, y: pt.y, builders: v ? [v.id] : [] });
      any = true;
    });
    if (any) audio.play("build");
    else this.hud.addAlert("Cannot build there.");
    if (!this.input.shift) this.placing = null;
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
  applySettings() {
    const s = this.settings;
    audio.masterVol = s.masterVol;
    audio.sfxVol = s.sfxVol;
    audio.musicVol = s.musicVol;
    audio.muted = s.muted;
    audio.applyVolumes();
    setColorblindTeams(s.colorblind);
    this.particles.density = s.reduceEffects ? 0.4 : 1;
    this.renderer.aggressiveLod = s.reduceEffects;
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
    this.hud.addAlert(`Speed ${v}×`);
  }

  cycleSpeed(dir: number) {
    const speeds = [0.5, 1, 2, 3];
    if (this.paused) { this.paused = false; return; }
    const i = Math.max(0, Math.min(speeds.length - 1, speeds.indexOf(this.gameSpeed) + dir));
    this.setSpeed(speeds[i]);
  }

  /** Cycle the camera through idle villagers, selecting each in turn. */
  selectIdleVillager() {
    if (!this.world) return;
    const idle = this.world
      .entitiesOf(this.me, Kind.Unit)
      .filter((e) => e.type === "villager" && e.order.kind === OrderKind.Idle);
    if (idle.length === 0) { this.hud.addAlert("No idle villagers"); return; }
    this.idleVillIndex = this.idleVillIndex % idle.length;
    const v = idle[this.idleVillIndex];
    this.idleVillIndex++;
    this.select([v.id]);
    this.camera.centerOn(v.x, v.y);
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
      if (this.world.canPlace(this.me, this.placing, wx, wy)) {
        this.dispatch({ t: "place", team: this.me, building: this.placing, x: wx, y: wy, builders: villagers.map((v) => v.id) });
        audio.play("build");
        if (!this.input.shift) this.placing = null;
      } else {
        this.hud.addAlert("Cannot build there.");
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
          if (ev.team === this.me) {
            sfx("complete", "complete", 0.5);
            const name = BUILDINGS[ev.data ?? ""]?.name;
            if (name) this.hud.addAlert(`${name} completed.`);
          }
          break;
        case "underattack":
          if (ev.team === this.me) {
            sfx("alert", "alert", 4);
            this.hud.addAlert("⚠ Your forces are under attack!", ev.x, ev.y);
          }
          break;
        case "callout":
          // Surface an allied AI's voice line (not our own) so team games feel
          // like a coordinated front rather than silent co-op.
          if (ev.team !== this.me && this.world?.areAllied(this.me, ev.team) && ev.data) {
            this.hud.addAlert(`🗣 Ally: ${ev.data}`, ev.x, ev.y);
          }
          break;
        case "age": {
          const ageNames = ["Dark Age", "Feudal Age", "Castle Age"];
          const who = ev.team === this.me ? "You have" : "The enemy has";
          this.hud.addAlert(`${who} advanced to the ${ageNames[parseInt(ev.data ?? "0", 10)]}!`);
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

    const W = this.canvas.width;
    const H = this.canvas.height;
    const ctx = this.renderer.ctx;

    setMouseDown(this.input.leftDown);
    ui.begin(ctx, {
      mx: this.input.mx,
      my: this.input.my,
      clicked: !!this.frameClick,
      rightClicked: !!this.frameRight,
      alt: this.input.alt,
    });

    if (this.state === "match" && this.world) {
      this.matchFrame(dt, W, H);
    } else {
      // Out-of-match screens.
      audio.updateMusic(dt, true);
      if (this.state === "menu") {
        const action = this.menu.draw(W, H, this.time, this.profile);
        if (action === "skirmish") {
          this.state = "setup";
          audio.play("ui");
        } else if (action === "multiplayer") {
          audio.play("ui");
          this.lobby.open((start) => this.startNetMatch(start));
        } else if (action === "armory") {
          this.state = "armory";
          audio.play("ui");
        } else if (action === "codex") {
          this.state = "codex";
          audio.play("ui");
        } else if (action === "settings") {
          this.openSettings("menu");
        }
      } else if (this.state === "settings") {
        const a = this.settingsScreen.draw(W, H, this.time, this.settings, this.input.leftDown);
        this.applySettings(); // live preview every frame
        if (a === "back") {
          saveSettings(this.settings);
          this.state = this.settingsReturn;
          audio.play("ui");
        }
      } else if (this.state === "codex") {
        if (this.codexScreen.draw(W, H, this.time) === "back") {
          this.state = "menu";
          audio.play("ui");
        }
      } else if (this.state === "setup") {
        const action = this.setup.draw(W, H, this.time, this.profile);
        if (action === "back") this.state = "menu";
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
          this.matchWon, this.endStats, this.endFoeStats, this.endDuration,
          this.matchRewards, this.profile, this.xpBefore, this.levelsGained,
          this.endGraph,
        );
        if (action === "continue") {
          this.state = "menu";
          audio.play("ui");
        }
      }
      ui.flushTooltip(W, H);
    }

    // Clear frame input flags.
    this.frameClick = null;
    this.frameDouble = null;
    this.frameRight = null;
    this.frameDragEnd = null;
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
          // Observers don't author input — just simulate as relayed turns arrive.
          this.net.stepReady(20);
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
        }
      }
    } else if (!this.ingameMenu && !this.paused && world.winner === null) {
      this.accumulator += dt * this.gameSpeed;
      let steps = 0;
      const maxSteps = 5 + Math.ceil(this.gameSpeed) * 2; // allow catch-up at high speed
      while (this.accumulator >= SIM_DT && steps < maxSteps) {
        world.tick();
        for (const ai of this.ais) ai.update(SIM_DT);
        this.accumulator -= SIM_DT;
        steps++;
      }
      if (steps === maxSteps) this.accumulator = 0; // drop time if we can't keep up
    }
    this.handleEvents(world.drainEvents());
    this.sampleHistory(world);
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
      if (this.input.isDown("w") || this.input.isDown("ArrowUp")) dy -= 1;
      if (this.input.isDown("s") && !this.input.ctrl) {/* s = stop; no camera */}
      if (this.input.isDown("ArrowDown")) dy += 1;
      if (this.input.isDown("ArrowLeft")) dx -= 1;
      if (this.input.isDown("d") || this.input.isDown("ArrowRight")) dx += 1;
      // edge scroll (only when enabled and the mouse is inside the window)
      if (this.settings.edgeScroll && this.input.mx >= 0 && this.input.my >= 0) {
        if (this.input.mx < edge) dx -= 1;
        if (this.input.mx > W - edge) dx += 1;
        if (this.input.my < edge) dy -= 1;
        if (this.input.my > H - edge) dy += 1;
      }
      if (dx || dy) {
        const l = Math.hypot(dx, dy) || 1;
        this.camera.pan((dx / l) * camSpeed * dt, (dy / l) * camSpeed * dt);
      }
    }

    // ---- markers age ----
    for (const m of this.markers) m.age += dt;
    this.markers = this.markers.filter((m) => m.age < 0.7);

    // ---- ghost placement validity ----
    let ghost: GhostPlacement | null = null;
    let suppressDragBox = false;
    if (this.placing) {
      const wx = this.camera.screenToWorldX(this.input.mx);
      const wy = this.camera.screenToWorldY(this.input.my);
      const def = BUILDINGS[this.placing];
      const p = world.player(this.me);
      const valid = world.canPlace(this.me, this.placing, wx, wy) && world.canAfford(p.resources, def.cost);
      ghost = { type: this.placing, x: wx, y: wy, valid };
      // Drag-painting a wall: preview the whole snapped run instead of a box.
      if (LINE_BUILDABLE.has(this.placing) && this.input.drag.active) {
        const d = this.input.drag;
        const pts = this.wallLinePoints(
          this.camera.screenToWorldX(d.x0), this.camera.screenToWorldY(d.y0),
          this.camera.screenToWorldX(d.x1), this.camera.screenToWorldY(d.y1),
        );
        ghost.line = pts.map((pt) => ({
          x: pt.x, y: pt.y,
          valid: world.canPlace(this.me, this.placing!, pt.x, pt.y),
        }));
        suppressDragBox = true;
      }
    }

    // ---- render world ----
    const selected = this.playerSelection();
    const rallyFrom = selected.find(
      (e) => e.kind === Kind.Building && BUILDINGS[e.type]?.trains.length && e.rallyX >= 0,
    ) ?? null;
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
      this.markers, ghost, suppressDragBox ? { active: false, x0: 0, y0: 0, x1: 0, y1: 0 } : this.input.drag, rallyFrom,
      hoveredId, alpha,
    );

    // ---- weather overlay (cosmetic, screen-space, over world & under HUD) ----
    if (this.settings.weather) {
      this.weather.render(this.renderer.ctx, W, H, dt, this.settings.reduceEffects ? 0.45 : 1);
    }

    // ---- HUD (consumes pointer if clicked over panels) ----
    // Pass the full selection (any team) so the info panel can show a clicked
    // enemy/neutral unit's health; the command card filters to own units.
    this.hud.draw(W, H, world, this.camera, this.me, this.selectedEntities(), dt, this.controller, this.attackMoveArmed, this.spectating);
    if (this.spectating) this.drawSpectatorHud(W, H, world);
    if (world.mode !== "conquest") this.drawModeStatus(W, H, world);
    this.drawControlGroups(W, H);
    this.drawQoLBar(W, H);
    if (this.showProduction && !this.spectating) {
      const jump = drawProductionPanel(W, H, world, this.me);
      if (jump != null) {
        const b = world.byId.get(jump);
        if (b) { this.select([jump]); this.camera.centerOn(b.x, b.y); }
      }
    }
    if (this.showScoreboard) drawScoreboard(W, H, world, this.me);
    if (this.net) drawChat(W, H, this.chatLog, this.chatOpen ? this.chatDraft : null, this.time, H - MINIMAP_SIZE - 70);
    ui.flushTooltip(W, H);

    // ---- in-game menu overlay ----
    if (this.ingameMenu) {
      const ctx = this.renderer.ctx;
      ctx.fillStyle = "rgba(8, 6, 3, 0.6)";
      ctx.fillRect(0, 0, W, H);
      ui.panel(W / 2 - 150, H / 2 - 130, 300, 270, { light: true });
      ui.text("Paused", W / 2, H / 2 - 100, { align: "center", size: 22, bold: true, color: PAL.uiAccent });
      if (ui.button("Resume", W / 2 - 110, H / 2 - 64, 220, 44, { accent: true, size: 16 })) {
        this.ingameMenu = false;
      }
      if (ui.button("⚙  Settings", W / 2 - 110, H / 2 - 12, 220, 44, { size: 15 })) {
        this.openSettings("match"); // returns to the (still-paused) match
      }
      if (ui.button("Concede & Quit", W / 2 - 110, H / 2 + 40, 220, 44, { danger: true, size: 15 })) {
        this.finishMatch(false);
      }
    }

    // ---- route unconsumed pointer input to the world ----
    // Spectators can still left-click/drag to select-and-inspect units, but
    // issue no commands.
    if (!this.ingameMenu) {
      if (this.frameDragEnd && !ui.pointerConsumed) {
        if (!this.spectating && this.placing && LINE_BUILDABLE.has(this.placing)) this.paintWallLine(this.frameDragEnd);
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
      // No stake in the fight — just announce the victor and bow out to the menu.
      if (world.winner !== null && this.matchOverTimer < 0) {
        this.matchOverTimer = 3.0;
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

  /** Tear down the current match and return to the main menu (spectator exit). */
  private exitToMenu() {
    if (this.world) this.world.revealAll = false;
    this.world = null;
    this.ais = [];
    this.spectating = false;
    this.ingameMenu = false;
    this.matchOverTimer = -1;
    this.endNet();
    this.me = Team.Player;
    this.state = "menu";
  }

  finishMatch(won: boolean) {
    const world = this.world!;
    const p = world.player(this.me);
    this.matchWon = won;
    this.endStats = {
      unitsKilled: p.stats.unitsKilled,
      unitsLost: p.stats.unitsLost,
      buildingsRazed: p.stats.buildingsRazed,
      buildingsLost: p.stats.buildingsLost,
      gathered: p.stats.gathered,
    };
    // Aggregate the opposing alliance for a side-by-side comparison.
    const foe = { unitsKilled: 0, gathered: 0, buildingsRazed: 0 };
    for (let t = 0; t < world.numTeams; t++) {
      if (!world.areHostile(this.me, t as Team)) continue;
      const s = world.player(t as Team).stats;
      foe.unitsKilled += s.unitsKilled;
      foe.gathered += s.gathered;
      foe.buildingsRazed += s.buildingsRazed;
    }
    this.endFoeStats = foe;
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
      this.endGraph = { ts: this.matchHistory.map((s) => s.t), mine, foe: foeS };
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
