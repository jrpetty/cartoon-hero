// Banner & Blade — application shell. Owns the canvas, the app state machine
// (menu → setup → match → post-match, plus the armory), the fixed-timestep
// sim loop, input routing and the meta-progression loop around each match.

import { World, WorldEvent } from "./sim/world";
import { BuildState, Entity, EntityId, Kind, MAX_TEAMS, OrderKind, Team } from "./sim/types";
import { UNITS } from "./content/units";
import { ABILITIES } from "./content/abilities";
import { BUILDINGS } from "./content/buildings";
import { SIM_DT, TILE } from "./content/balance";
import { dayPhase } from "./content/daynight";
import { generateMap } from "./maps/generator";
import { SkirmishAI } from "./ai/skirmish_ai";
import { DIFFICULTIES } from "./ai/difficulty";
import { Camera } from "./engine/camera";
import { Input } from "./engine/input";
import { Particles } from "./engine/particles";
import { audio } from "./engine/audio";
import { Renderer, CommandMarker, GhostPlacement } from "./render/renderer";
import { PAL } from "./render/palette";
import { HUD, MatchController } from "./ui/hud";
import { ui } from "./ui/ui";
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

type AppState = "menu" | "setup" | "armory" | "match" | "postmatch";

const PLAYER = Team.Player;
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
  ingameMenu = false;
  matchOverTimer = -1;
  playerWon = false;
  matchRewards: MatchRewards | null = null;
  matchWon = false;
  xpBefore = 0;
  levelsGained = 0;
  endStats = { unitsKilled: 0, unitsLost: 0, buildingsRazed: 0, gathered: 0 };
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
    const k = key.length === 1 ? key.toLowerCase() : key;
    if (k === "Escape") {
      if (this.placing) this.placing = null;
      else if (this.attackMoveArmed) this.attackMoveArmed = false;
      else this.ingameMenu = !this.ingameMenu;
      return;
    }
    if (this.ingameMenu) return;
    if (k === "a") this.attackMoveArmed = true;
    if (k === "s") this.world.issueStop(this.playerSelection().map((e) => e.id));
    if (k === "h") this.world.issueHold(this.playerSelection().map((e) => e.id));
    if (k === "q") this.controller.useAbility();
    if (k === "b") {
      this.hud.buildMenuOpen = true;
      this.hud.buildCategory = null;
    }
    if (k === "g") {
      const b = this.playerSelection().find((e) => e.kind === Kind.Building);
      if (b) this.world.ungarrison(b.id);
    }
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

  // --------------------------------------------------------------- selection --

  playerSelection(): Entity[] {
    if (!this.world) return [];
    const out: Entity[] = [];
    for (const id of this.selection) {
      const e = this.world.byId.get(id);
      if (e && e.alive && e.team === PLAYER) out.push(e);
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
    const numPlayers = Math.max(2, Math.min(MAX_TEAMS, config.players ?? 2));
    const map = generateMap(config.presetId, config.seed, numPlayers);
    const world = new World(config.seed);
    // Team 0 is the human; teams 1..n-1 are AI opponents (free-for-all).
    const loadouts = [this.profile.matchLoadout(config.fairMode)];
    const econMults = [1];
    for (let t = 1; t < numPlayers; t++) {
      loadouts.push(this.profile.matchLoadout(true));
      econMults.push(diff.econMult);
    }
    world.init(map, loadouts, econMults);
    this.world = world;
    this.ais = [];
    for (let t = 1; t < numPlayers; t++) {
      this.ais.push(new SkirmishAI(world, t as Team, diff));
    }
    this.renderer.prepare(map);
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
    this.camera.centerOn(map.starts[PLAYER].x, map.starts[PLAYER].y);
    this.accumulator = 0;
    this.state = "match";
    this.hud.addAlert(`${map.name} — vs ${diff.name}. Your villagers await orders!`);
    audio.play("complete");
  }

  // The HUD acts through this controller.
  controller: MatchController = {
    trainUnit: (b, type) => {
      if (this.world?.trainUnit(PLAYER, b.id, type)) audio.play("ui");
    },
    research: (b, techId) => {
      if (this.world?.research(PLAYER, b.id, techId)) audio.play("ui");
    },
    startPlacement: (type) => {
      this.placing = type;
      audio.play("ui");
    },
    ungarrison: (b) => {
      this.world?.ungarrison(b.id);
      audio.play("command");
    },
    toggleGate: (b) => {
      this.world?.toggleGate(b.id);
      audio.play("build");
    },
    trade: (action) => {
      if (this.world?.marketTrade(PLAYER, action)) audio.play("coin");
    },
    stopSelection: () => {
      this.world?.issueStop(this.playerSelection().map((e) => e.id));
      audio.play("command");
    },
    holdSelection: () => {
      this.world?.issueHold(this.playerSelection().map((e) => e.id));
      audio.play("command");
    },
    setAttackMoveMode: () => {
      this.attackMoveArmed = true;
    },
    useAbility: () => {
      const ids = this.playerSelection().filter((e) => e.kind === Kind.Unit).map((e) => e.id);
      const fired = this.world?.useAbility(ids) ?? 0;
      if (fired > 0) audio.play("command");
      else this.hud.addAlert("Ability not ready.");
    },
    minimapNavigate: (wx, wy) => this.camera.centerOn(wx, wy),
    minimapCommand: (wx, wy) => this.issueContextCommand(wx, wy, null),
    openMenu: () => {
      this.ingameMenu = true;
    },
  };

  // -------------------------------------------------------- world interaction --

  /** Snapped, de-duplicated tile centres along a drag, for wall painting/preview. */
  wallLinePoints(wx0: number, wy0: number, wx1: number, wy1: number): { x: number; y: number }[] {
    const steps = Math.max(0, Math.round(Math.hypot(wx1 - wx0, wy1 - wy0) / TILE));
    const out: { x: number; y: number }[] = [];
    const seen = new Set<string>();
    for (let i = 0; i <= steps; i++) {
      const t = steps === 0 ? 0 : i / steps;
      const wx = wx0 + (wx1 - wx0) * t;
      const wy = wy0 + (wy1 - wy0) * t;
      const cx = Math.round(wx / TILE);
      const cy = Math.round(wy / TILE);
      const key = `${cx},${cy}`;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 });
    }
    return out;
  }

  /** Drag-release while a wall is selected: lay a whole run of segments at once. */
  paintWallLine(box: { x0: number; y0: number; x1: number; y1: number }) {
    if (!this.world || !this.placing) return;
    const pts = this.wallLinePoints(
      this.camera.screenToWorldX(box.x0), this.camera.screenToWorldY(box.y0),
      this.camera.screenToWorldX(box.x1), this.camera.screenToWorldY(box.y1),
    );
    const villagers = this.playerSelection().filter((e) => UNITS[e.type]?.canBuild);
    const placed: EntityId[] = [];
    for (const pt of pts) {
      const b = this.world.placeBuilding(PLAYER, this.placing, pt.x, pt.y);
      if (b) placed.push(b.id);
    }
    if (placed.length > 0) {
      placed.forEach((id, i) => {
        const v = villagers[i % Math.max(1, villagers.length)];
        if (v) this.world!.issueBuildRepair([v.id], id, true);
      });
      audio.play("build");
    } else {
      this.hud.addAlert("Cannot build there.");
    }
    if (!this.input.shift) this.placing = null;
  }

  worldClick(sx: number, sy: number) {
    if (!this.world) return;
    const wx = this.camera.screenToWorldX(sx);
    const wy = this.camera.screenToWorldY(sy);

    if (this.placing) {
      const villagers = this.playerSelection().filter((e) => UNITS[e.type]?.canBuild);
      const placed = this.world.placeBuilding(PLAYER, this.placing, wx, wy);
      if (placed) {
        this.world.issueBuildRepair(villagers.map((v) => v.id), placed.id, this.input.shift);
        audio.play("build");
        if (!this.input.shift) this.placing = null;
      } else {
        this.hud.addAlert("Cannot build there.");
      }
      return;
    }

    if (this.attackMoveArmed) {
      const units = this.playerSelection().filter((e) => e.kind === Kind.Unit);
      this.world.issueMove(units.map((e) => e.id), wx, wy, this.input.shift, true);
      this.markers.push({ x: wx, y: wy, age: 0, kind: "attack" });
      this.attackMoveArmed = false;
      audio.play("command");
      return;
    }

    // Plain selection click.
    const e = this.world.entityAt(wx, wy);
    if (e && e.kind !== Kind.Projectile && this.world.visibleTo(PLAYER, e)) {
      if (this.input.shift && e.team === PLAYER) {
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
    const e = this.world.entityAt(wx, wy, PLAYER);
    if (!e || e.kind !== Kind.Unit) return;
    // Select all units of this type currently on screen.
    const ids: EntityId[] = [];
    for (const o of this.world.entitiesOf(PLAYER, Kind.Unit)) {
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
    for (const e of this.world.entitiesOf(PLAYER)) {
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

    // Production buildings: right-click sets rally.
    if (units.length === 0 && buildingsSel.length > 0) {
      for (const b of buildingsSel) {
        if (BUILDINGS[b.type]?.trains.length) {
          this.world.setRally(b.id, wx, wy);
          this.markers.push({ x: wx, y: wy, age: 0, kind: "rally" });
        }
      }
      audio.play("command");
      return;
    }
    if (units.length === 0) return;

    const ids = units.map((e) => e.id);
    if (target && target.alive && this.world.visibleTo(PLAYER, target)) {
      if (target.team !== PLAYER && target.kind !== Kind.Resource) {
        this.world.issueAttack(ids, target.id, shift);
        this.markers.push({ x: wx, y: wy, age: 0, kind: "attack" });
        audio.play("command");
        return;
      }
      if (target.kind === Kind.Resource || (target.team === PLAYER && target.type === "farm" && target.buildState === BuildState.Done)) {
        const gatherers = units.filter((e) => UNITS[e.type]?.canGather);
        if (gatherers.length) {
          this.world.issueGather(gatherers.map((e) => e.id), target.id, shift);
          const rest = units.filter((e) => !UNITS[e.type]?.canGather);
          if (rest.length) this.world.issueMove(rest.map((e) => e.id), wx, wy, shift);
          this.markers.push({ x: wx, y: wy, age: 0, kind: "move" });
          audio.play("command");
          return;
        }
      }
      if (target.team === PLAYER && target.kind === Kind.Building) {
        // Villagers repair/finish construction; combat units garrison.
        const builders = units.filter((e) => UNITS[e.type]?.canBuild);
        const fighters = units.filter((e) => !UNITS[e.type]?.canBuild);
        if (builders.length && (target.buildState !== BuildState.Done || target.hp < target.maxHp)) {
          this.world.issueBuildRepair(builders.map((e) => e.id), target.id, shift);
        }
        const cap = BUILDINGS[target.type]?.garrisonCap ?? 0;
        if (fighters.length && cap > 0) {
          this.world.garrison(fighters.map((e) => e.id), target.id);
        } else if (fighters.length) {
          this.world.issueMove(fighters.map((e) => e.id), wx, wy, shift);
        }
        this.markers.push({ x: wx, y: wy, age: 0, kind: "move" });
        audio.play("command");
        return;
      }
    }
    // Default: move.
    this.world.issueMove(ids, wx, wy, shift);
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
    for (const ev of events) {
      switch (ev.kind) {
        case "sword":
          sfx("sword", "sword");
          this.particles.burst(ev.x, ev.y, 4, "#ffd9a0", 90, { maxLife: 0.25, size: 1.8 });
          break;
        case "bow":
          sfx("bow", "bow");
          break;
        case "arrowHit":
          sfx("arrowHit", "arrowHit");
          this.particles.burst(ev.x, ev.y, 3, "#d9cfb4", 70, { maxLife: 0.2, size: 1.5 });
          if (Math.random() < 0.6) this.renderer.addStuckArrow(ev.x, ev.y, Math.random() * Math.PI * 2);
          break;
        case "hit":
          if (this.showDamageNumbers && ev.data) {
            const friendly = ev.team === PLAYER;
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
          sfx("sword", "death", 0.15);
          this.particles.burst(ev.x, ev.y, 9, PAL.blood, 110, { maxLife: 0.5, size: 2.2, gravity: 140 });
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
          if (ev.team === PLAYER) {
            sfx("complete", "complete", 0.5);
            const name = BUILDINGS[ev.data ?? ""]?.name;
            if (name) this.hud.addAlert(`${name} completed.`);
          }
          break;
        case "underattack":
          if (ev.team === PLAYER) {
            sfx("alert", "alert", 4);
            this.hud.addAlert("⚠ Your forces are under attack!", ev.x, ev.y);
          }
          break;
        case "age": {
          const ageNames = ["Dark Age", "Feudal Age", "Castle Age"];
          const who = ev.team === PLAYER ? "You have" : "The enemy has";
          this.hud.addAlert(`${who} advanced to the ${ageNames[parseInt(ev.data ?? "0", 10)]}!`);
          if (ev.team === PLAYER) sfx("levelup", "age", 1);
          break;
        }
        case "ability": {
          const col = ABILITIES[
            Object.keys(ABILITIES).find((k) => ABILITIES[k].id === ev.data) ?? ""
          ]?.color ?? "#ffe98a";
          this.particles.burst(ev.x, ev.y, 14, col, 130, { maxLife: 0.5, size: 2.4, glow: true, gravity: -40 });
          if (ev.team === PLAYER) sfx("command", "ability", 0.1);
          break;
        }
        case "deposit":
          break;
        case "spawn":
          if (ev.team === PLAYER) sfx("complete", "spawn", 2);
          break;
      }
    }
  }

  // -------------------------------------------------------------------- frame --

  frame() {
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
        } else if (action === "armory") {
          this.state = "armory";
          audio.play("ui");
        }
      } else if (this.state === "setup") {
        const action = this.setup.draw(W, H, this.time, this.profile);
        if (action === "back") this.state = "menu";
        else if (action === "start") this.startMatch({ ...this.setup.config });
      } else if (this.state === "armory") {
        const action = this.armory.draw(W, H, this.time, dt, this.profile);
        if (action === "back") {
          this.profile.save();
          this.state = "menu";
        }
      } else if (this.state === "postmatch" && this.matchRewards) {
        const action = this.postmatch.draw(
          W, H, this.time, dt,
          this.matchWon, this.endStats, this.endDuration,
          this.matchRewards, this.profile, this.xpBefore, this.levelsGained,
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

    requestAnimationFrame(() => this.frame());
  }

  matchFrame(dt: number, W: number, H: number) {
    const world = this.world!;

    // ---- simulate (fixed timestep) ----
    if (!this.ingameMenu && world.winner === null) {
      this.accumulator += dt;
      let steps = 0;
      while (this.accumulator >= SIM_DT && steps < 5) {
        world.tick();
        for (const ai of this.ais) ai.update(SIM_DT);
        this.accumulator -= SIM_DT;
        steps++;
      }
      if (steps === 5) this.accumulator = 0; // drop time if we can't keep up
    }
    this.handleEvents(world.drainEvents());
    this.particles.update(dt);
    audio.updateMusic(dt, !this.ingameMenu);

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
    if (!this.ingameMenu) {
      const camSpeed = 540 / this.camera.zoom;
      const edge = 16;
      let dx = 0;
      let dy = 0;
      if (this.input.isDown("w") || this.input.isDown("ArrowUp")) dy -= 1;
      if (this.input.isDown("s") && !this.input.ctrl) {/* s = stop; no camera */}
      if (this.input.isDown("ArrowDown")) dy += 1;
      if (this.input.isDown("ArrowLeft")) dx -= 1;
      if (this.input.isDown("d") || this.input.isDown("ArrowRight")) dx += 1;
      // edge scroll (only when mouse inside window)
      if (this.input.mx >= 0 && this.input.my >= 0) {
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
      const p = world.player(PLAYER);
      const valid = world.canPlace(PLAYER, this.placing, wx, wy) && world.canAfford(p.resources, def.cost);
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
          valid: world.canPlace(PLAYER, this.placing!, pt.x, pt.y),
        }));
        suppressDragBox = true;
      }
    }

    // ---- render world ----
    const selected = this.playerSelection();
    const rallyFrom = selected.find(
      (e) => e.kind === Kind.Building && BUILDINGS[e.type]?.trains.length && e.rallyX >= 0,
    ) ?? null;
    this.renderer.render(
      world, this.camera, this.particles, dt, this.time, PLAYER,
      this.markers, ghost, suppressDragBox ? { active: false, x0: 0, y0: 0, x1: 0, y1: 0 } : this.input.drag, rallyFrom,
    );

    // ---- HUD (consumes pointer if clicked over panels) ----
    this.hud.draw(W, H, world, this.camera, PLAYER, selected, dt, this.controller, this.attackMoveArmed);
    ui.flushTooltip(W, H);

    // ---- in-game menu overlay ----
    if (this.ingameMenu) {
      const ctx = this.renderer.ctx;
      ctx.fillStyle = "rgba(8, 6, 3, 0.6)";
      ctx.fillRect(0, 0, W, H);
      ui.panel(W / 2 - 150, H / 2 - 110, 300, 220, { light: true });
      ui.text("Paused", W / 2, H / 2 - 80, { align: "center", size: 22, bold: true, color: PAL.uiAccent });
      if (ui.button("Resume", W / 2 - 110, H / 2 - 44, 220, 44, { accent: true, size: 16 })) {
        this.ingameMenu = false;
      }
      if (ui.button("Concede & Quit", W / 2 - 110, H / 2 + 14, 220, 44, { danger: true, size: 15 })) {
        this.finishMatch(false);
      }
    }

    // ---- route unconsumed pointer input to the world ----
    if (!this.ingameMenu) {
      if (this.frameDragEnd && !ui.pointerConsumed) {
        if (this.placing && LINE_BUILDABLE.has(this.placing)) this.paintWallLine(this.frameDragEnd);
        else this.worldDragSelect(this.frameDragEnd);
      }
      if (this.frameDouble && !ui.pointerConsumed) {
        this.worldDoubleClick(this.frameDouble.x, this.frameDouble.y);
      } else if (this.frameClick && !ui.pointerConsumed) {
        this.worldClick(this.frameClick.x, this.frameClick.y);
      }
      if (this.frameRight && !ui.pointerConsumed) {
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
    // The match ends for the human when a winner is decided, or the moment
    // their own realm is wiped out (the AIs may fight on, but the human is out).
    const playerOut = world.player(PLAYER).defeated;
    if ((world.winner !== null || playerOut) && this.matchOverTimer < 0) {
      this.matchOverTimer = 1.8;
      this.playerWon = world.winner === PLAYER && !playerOut;
      this.hud.addAlert(this.playerWon ? "🏆 The last enemy is broken!" : "💀 Your last building has fallen…");
      if (this.playerWon) audio.play("levelup");
      else audio.play("collapse");
    }
    if (this.matchOverTimer > 0) {
      this.matchOverTimer -= dt;
      if (this.matchOverTimer <= 0) this.finishMatch(this.playerWon);
    }
  }

  finishMatch(won: boolean) {
    const world = this.world!;
    const p = world.player(PLAYER);
    this.matchWon = won;
    this.endStats = {
      unitsKilled: p.stats.unitsKilled,
      unitsLost: p.stats.unitsLost,
      buildingsRazed: p.stats.buildingsRazed,
      gathered: p.stats.gathered,
    };
    this.endDuration = world.time;
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
    this.profile.recordResult(won);
    this.profile.save();
    this.postmatch.reset();
    this.world = null;
    this.ais = [];
    this.ingameMenu = false;
    this.state = "postmatch";
  }
}

new App();
