// The map editor.
//
// The shape of it: a library you arrive at, a canvas you paint on, a properties
// rail that decides what the map *is*, and a validation list that tells you the
// truth about it while you work rather than when you try to play it.
//
// Three things drive most of the design:
//
//   • **Painting has to be cheap.** The map is redrawn every frame at whatever
//     zoom you are at, so the canvas draws cells, not sprites, and only the
//     cells actually on screen.
//   • **Symmetry is the whole job on a competitive map.** Painting one corner
//     of a four-player map by hand and hoping is not authoring. Every stroke is
//     mirrored through the chosen symmetry, so the map is fair by construction.
//   • **You should never be able to save something unplayable by accident.**
//     Validation runs on every change and says what is wrong in words, and
//     Test Map is disabled while any of it is an error.

import { ui } from "./ui";
import { PAL, withAlpha } from "../render/palette";
import { audio } from "../engine/audio";
import { GameMode } from "../sim/types";
import { TILE } from "../content/balance";
import {
  BIOMES, BiomeId, Terrain, TERRAIN_DEFS, biomeById, terrainDef,
} from "../maps/terrain_kinds";
import {
  CustomMap, MAP_SIZES, MAX_SEATS, MapIssue, NOMAD_RULES, NomadRule, customFromGenerated,
  deleteCustomMap, deserialiseMap, hasErrors, listCustomMaps, newCustomMap, saveCustomMap,
  serialiseMap, validateMap,
} from "../maps/custom";
import { PRESETS } from "../maps/generator";
import { SYMMETRIES, SymmetryId, symmetryCells } from "../maps/symmetry";

export { SYMMETRIES, symmetryCells };
export type { SymmetryId };
import { drawMapThumbnail } from "./map_thumb";
import { EXAMPLE_SCRIPT, SCRIPT_COMMANDS, runMapScript } from "../maps/script";

// ------------------------------------------------------------------- tools --

type Tool = "terrain" | "path" | "scatter" | "region" | "resource" | "spawn" | "erase";
type ResourceKind = "tree" | "gold_mine" | "berries";

const PALETTE: Terrain[] = [
  Terrain.Grass, Terrain.GrassDark, Terrain.Dirt, Terrain.Sand, Terrain.Snow,
  Terrain.Hill, Terrain.Forest, Terrain.Marsh, Terrain.Shallow, Terrain.Water, Terrain.Rock,
];

const SWATCH: Record<number, string> = {
  [Terrain.Grass]: "#6f9c4a", [Terrain.GrassDark]: "#557f3c", [Terrain.Dirt]: "#8a6f47",
  [Terrain.Sand]: "#cbb383", [Terrain.Snow]: "#dfe7f0", [Terrain.Hill]: "#7f9c5b",
  [Terrain.Forest]: "#2f4429", [Terrain.Marsh]: "#4d5a3c", [Terrain.Shallow]: "#4a7f96",
  [Terrain.Water]: "#2f6a86", [Terrain.Rock]: "#6a6f76",
};

const RES_COLOUR: Record<ResourceKind, string> = { tree: "#2f6b32", gold_mine: "#e0b83a", berries: "#c8425a" };
const RES_LABEL: Record<ResourceKind, string> = { tree: "Trees", gold_mine: "Gold", berries: "Berries" };
const RES_AMOUNT: Record<ResourceKind, number> = { tree: 160, gold_mine: 1000, berries: 250 };

const MODES: { id: GameMode; label: string }[] = [
  { id: "conquest", label: "Conquest" },
  { id: "survival", label: "Survival" },
  { id: "koth", label: "King of the Hill" },
  { id: "regicide", label: "Regicide" },
];

const RAIL = 214;      // the tool rail on the left
const PROPS = 300;     // the properties rail on the right
const UNDO_DEPTH = 40;

export type EditorAction = { kind: "back" } | { kind: "test"; map: CustomMap } | null;

interface Snapshot { terrain: Uint8Array; resources: CustomMap["resources"]; spawns: CustomMap["spawns"] }

export class EditorScreen {
  /** null = the library; otherwise the map being edited. */
  private map: CustomMap | null = null;
  private tool: Tool = "terrain";
  private paint: Terrain = Terrain.Grass;
  private resource: ResourceKind = "tree";
  private brush = 3;
  /**
   * Where a drag started, in cells. Shared by the path and region tools: both
   * are "press here, release there" gestures rather than continuous painting,
   * so both need the anchor and neither wants the other's.
   */
  private dragFrom: { cx: number; cy: number } | null = null;
  /** How thickly the scatter brush sprinkles, as a chance per candidate cell. */
  private scatterDensity = 0.25;
  /** A lifted rectangle of map, waiting to be stamped down somewhere. */
  private clip: {
    w: number; h: number; terrain: Uint8Array;
    resources: { type: ResourceKind; dx: number; dy: number; amount: number }[];
  } | null = null;
  private clipFlipX = false;
  private clipFlipY = false;
  /** Deterministic sprinkle: an author's scatter should not reshuffle per frame. */
  private scatterSeed = 1;
  private sym: SymmetryId = "rot180";
  private camX = 0;
  private camY = 0;
  private zoom = 4; // screen pixels per cell
  private undo: Snapshot[] = [];
  private redo: Snapshot[] = [];
  private strokeOpen = false;
  private issues: MapIssue[] = [];
  private dirty = true;
  private status = "";
  private statusT = 0;
  private lastPan: { x: number; y: number } | null = null;
  /** Which text field is being typed into, if any. */
  private editing: "name" | "desc" | "import" | "script" | null = null;
  private importBuf = "";
  private newSize = 128;
  private newBiome: BiomeId = "temperate";
  /** The "roll one and edit it" controls in the library. */
  private rollPreset = "highlands";
  private rollSeed = 1 + Math.floor(Math.random() * 99999);
  private rollPlayers = 2;
  /** The random-map script, its seed, and whatever the last run had to say. */
  private scriptBuf = EXAMPLE_SCRIPT;
  private scriptSeed = 1 + Math.floor(Math.random() * 99999);
  private scriptMsgs: { line: number; text: string; bad: boolean }[] = [];
  private showScript = false;
  private confirmDelete = "";
  /** Set when a map is opened: the first draw frames the whole thing. */
  private needsFit = true;

  /** Keys, forwarded by the app. True when the editor consumed one. */
  handleKey(key: string): boolean {
    if (this.editing) {
      if (key === "Escape") { this.editing = null; return true; }
      if (key === "Enter") {
        // A script is the one field where Enter means "new line" rather than
        // "done" — a one-line script language would be a poor one.
        if (this.editing === "script") { this.scriptBuf += "\n"; return true; }
        this.editing = null;
        return true;
      }
      if (key === "Backspace") {
        if (this.editing === "name" && this.map) this.map.name = this.map.name.slice(0, -1);
        else if (this.editing === "desc" && this.map) this.map.desc = this.map.desc.slice(0, -1);
        else if (this.editing === "script") this.scriptBuf = this.scriptBuf.slice(0, -1);
        else this.importBuf = this.importBuf.slice(0, -1);
        return true;
      }
      if (key.length === 1) {
        if (this.editing === "name" && this.map) this.map.name = (this.map.name + key).slice(0, 40);
        else if (this.editing === "desc" && this.map) this.map.desc = (this.map.desc + key).slice(0, 160);
        else if (this.editing === "script") this.scriptBuf += key;
        else this.importBuf += key;
      }
      return true;
    }
    if (!this.map) return false;
    // Undo/redo are the two shortcuts an editor cannot be without.
    if ((key === "z" || key === "Z") && ui.ctrlHeld) { this.undoStep(); return true; }
    if ((key === "y" || key === "Y") && ui.ctrlHeld) { this.redoStep(); return true; }
    const n = Number(key);
    if (Number.isInteger(n) && n >= 1 && n <= 9 && n <= PALETTE.length) {
      this.tool = "terrain"; this.paint = PALETTE[n - 1]; return true;
    }
    if (key === "[") { this.brush = Math.max(1, this.brush - 1); return true; }
    if (key === "]") { this.brush = Math.min(24, this.brush + 1); return true; }
    return false;
  }

  private say(msg: string) { this.status = msg; this.statusT = 3; }

  // ------------------------------------------------------------ undo/redo --

  private snapshot(): Snapshot {
    const m = this.map!;
    return {
      terrain: new Uint8Array(m.terrain),
      resources: m.resources.map((r) => ({ ...r })),
      spawns: m.spawns.map((s) => ({ ...s })),
    };
  }

  private beginStroke() {
    if (this.strokeOpen || !this.map) return;
    this.strokeOpen = true;
    this.undo.push(this.snapshot());
    if (this.undo.length > UNDO_DEPTH) this.undo.shift();
    this.redo.length = 0;
  }

  private applySnapshot(s: Snapshot) {
    const m = this.map!;
    m.terrain = new Uint8Array(s.terrain);
    m.resources = s.resources.map((r) => ({ ...r }));
    m.spawns = s.spawns.map((p) => ({ ...p }));
    this.dirty = true;
  }

  private undoStep() {
    if (!this.map || !this.undo.length) { this.say("Nothing to undo"); return; }
    this.redo.push(this.snapshot());
    this.applySnapshot(this.undo.pop()!);
    this.say("Undone");
  }

  private redoStep() {
    if (!this.map || !this.redo.length) { this.say("Nothing to redo"); return; }
    this.undo.push(this.snapshot());
    this.applySnapshot(this.redo.pop()!);
    this.say("Redone");
  }

  // --------------------------------------------------------------- editing --

  private paintAt(cx: number, cy: number) {
    const m = this.map!;
    const r = this.brush - 1;
    for (const [sx, sy] of symmetryCells(this.sym, cx, cy, m.cols, m.rows)) {
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (dx * dx + dy * dy > r * r + r * 0.6) continue; // round brush
          const nx = sx + dx, ny = sy + dy;
          if (nx < 0 || ny < 0 || nx >= m.cols || ny >= m.rows) continue;
          m.terrain[ny * m.cols + nx] = this.paint;
        }
      }
    }
    this.dirty = true;
  }

  /**
   * Stroke a straight run of ground from one cell to another.
   *
   * Rivers, ridges and roads are lines, and a round brush dragged by hand makes
   * a wobbly sausage rather than a river. Walking the line and stamping the
   * brush at every step gives an even-width run with no gaps, however steep the
   * diagonal — the same reason the wall tool in the match uses a line walk
   * rather than sampling along the distance.
   */
  private strokePath(x0: number, y0: number, x1: number, y1: number) {
    const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    if (steps === 0) { this.paintAt(x0, y0); return; }
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      this.paintAt(Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t));
    }
  }

  /**
   * A cheap deterministic hash of a cell, for the scatter brush.
   *
   * It has to be a function of the *cell*, not a running random: an author
   * dragging back over ground they already covered should not keep thickening
   * it, and the same drag twice should look the same both times.
   */
  private scatterRoll(cx: number, cy: number): number {
    let h = (cx * 374761393 + cy * 668265263 + this.scatterSeed * 2246822519) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
  }

  /**
   * Sprinkle resource nodes across the brush at the chosen density, rather than
   * one node per click. A woodline is fifty trees; placing those by hand is the
   * single most tedious thing in the editor.
   */
  private scatterAt(cx: number, cy: number) {
    const m = this.map!;
    const r = this.brush - 1;
    const occupied = new Set(
      m.resources.map((res) => `${Math.floor(res.x / TILE)},${Math.floor(res.y / TILE)}`),
    );
    for (const [ax, ay] of symmetryCells(this.sym, cx, cy, m.cols, m.rows)) {
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (dx * dx + dy * dy > r * r + r * 0.6) continue;
          const nx = ax + dx, ny = ay + dy;
          if (nx < 0 || ny < 0 || nx >= m.cols || ny >= m.rows) continue;
          if (this.scatterRoll(nx, ny) > this.scatterDensity) continue;
          const key = `${nx},${ny}`;
          if (occupied.has(key)) continue;
          occupied.add(key);
          m.resources.push({
            type: this.resource,
            x: nx * TILE + TILE / 2,
            y: ny * TILE + TILE / 2,
            amount: RES_AMOUNT[this.resource],
          });
        }
      }
    }
    this.dirty = true;
  }

  /** Lift a rectangle of ground and everything standing on it. */
  private copyRegion(x0: number, y0: number, x1: number, y1: number) {
    const m = this.map!;
    const ax = Math.max(0, Math.min(x0, x1)), ay = Math.max(0, Math.min(y0, y1));
    const bx = Math.min(m.cols - 1, Math.max(x0, x1)), by = Math.min(m.rows - 1, Math.max(y0, y1));
    const w = bx - ax + 1, h = by - ay + 1;
    if (w < 1 || h < 1) return;
    const terrain = new Uint8Array(w * h);
    for (let cy = 0; cy < h; cy++) {
      for (let cx = 0; cx < w; cx++) terrain[cy * w + cx] = m.terrain[(ay + cy) * m.cols + (ax + cx)];
    }
    const resources = m.resources
      .map((r) => ({
        type: r.type as ResourceKind,
        dx: Math.floor(r.x / TILE) - ax,
        dy: Math.floor(r.y / TILE) - ay,
        amount: r.amount,
      }))
      .filter((r) => r.dx >= 0 && r.dy >= 0 && r.dx < w && r.dy < h);
    this.clip = { w, h, terrain, resources };
    this.say(`Copied ${w}×${h} — click to stamp it`);
  }

  /**
   * Stamp the lifted region with its top-left at the cursor.
   *
   * Deliberately *not* mirrored through the symmetry setting. Symmetry exists so
   * a stroke lands in every quadrant at once; a stamp is a considered placement
   * of something already built, and having it also fire into three other corners
   * is almost never what an author means. Flip X/Y are offered instead, which is
   * what you actually want when facing a base at the opposite seat.
   */
  private pasteRegion(cx: number, cy: number) {
    const m = this.map!;
    const c = this.clip;
    if (!c) return;
    for (let dy = 0; dy < c.h; dy++) {
      for (let dx = 0; dx < c.w; dx++) {
        const sx = this.clipFlipX ? c.w - 1 - dx : dx;
        const sy = this.clipFlipY ? c.h - 1 - dy : dy;
        const nx = cx + dx, ny = cy + dy;
        if (nx < 0 || ny < 0 || nx >= m.cols || ny >= m.rows) continue;
        m.terrain[ny * m.cols + nx] = c.terrain[sy * c.w + sx];
      }
    }
    // Anything already standing inside the stamped area goes, or the paste
    // would layer new trees on top of old ones.
    const ax = cx, ay = cy;
    m.resources = m.resources.filter((r) => {
      const rx = Math.floor(r.x / TILE) - ax, ry = Math.floor(r.y / TILE) - ay;
      return rx < 0 || ry < 0 || rx >= c.w || ry >= c.h;
    });
    for (const r of c.resources) {
      const dx = this.clipFlipX ? c.w - 1 - r.dx : r.dx;
      const dy = this.clipFlipY ? c.h - 1 - r.dy : r.dy;
      const nx = cx + dx, ny = cy + dy;
      if (nx < 0 || ny < 0 || nx >= m.cols || ny >= m.rows) continue;
      m.resources.push({ type: r.type, x: nx * TILE + TILE / 2, y: ny * TILE + TILE / 2, amount: r.amount });
    }
    this.dirty = true;
    this.say("Stamped");
  }

  private placeResource(cx: number, cy: number) {
    const m = this.map!;
    const step = Math.max(1, Math.round(this.brush / 2));
    for (const [sx, sy] of symmetryCells(this.sym, cx, cy, m.cols, m.rows)) {
      // Never stack two nodes on one cell — the sim would spawn them on top of
      // each other and a villager could only ever reach one.
      if (m.resources.some((res) => Math.floor(res.x / TILE) === sx && Math.floor(res.y / TILE) === sy)) continue;
      if (sx < 0 || sy < 0 || sx >= m.cols || sy >= m.rows) continue;
      m.resources.push({
        type: this.resource,
        x: sx * TILE + TILE / 2,
        y: sy * TILE + TILE / 2,
        amount: RES_AMOUNT[this.resource],
      });
    }
    void step;
    this.dirty = true;
  }

  private placeSpawn(cx: number, cy: number) {
    const m = this.map!;
    if (m.nomad === "forced") { this.say("This map is always nomad — spawn points are disabled"); return; }
    const cells = symmetryCells(this.sym, cx, cy, m.cols, m.rows);
    for (const [sx, sy] of cells) {
      if (m.spawns.length >= 8) break;
      const wx = sx * TILE + TILE / 2, wy = sy * TILE + TILE / 2;
      if (m.spawns.some((s) => Math.hypot(s.x - wx, s.y - wy) < TILE * 4)) continue;
      m.spawns.push({ x: wx, y: wy });
    }
    this.dirty = true;
  }

  private eraseAt(cx: number, cy: number) {
    const m = this.map!;
    const rad = this.brush * TILE;
    for (const [sx, sy] of symmetryCells(this.sym, cx, cy, m.cols, m.rows)) {
      const wx = sx * TILE + TILE / 2, wy = sy * TILE + TILE / 2;
      m.resources = m.resources.filter((r) => Math.hypot(r.x - wx, r.y - wy) > rad);
      m.spawns = m.spawns.filter((s) => Math.hypot(s.x - wx, s.y - wy) > rad);
    }
    this.dirty = true;
  }

  /** Flatten every cell back to the biome's base ground, keeping resources. */
  private levelAll() {
    const m = this.map!;
    this.undo.push(this.snapshot());
    m.terrain.fill(biomeById(m.biome).ground[0]);
    this.dirty = true;
    this.say("Land levelled");
  }

  // ---------------------------------------------------------------- drawing --

  draw(W: number, H: number, time: number, mouseDown: boolean): EditorAction {
    if (this.statusT > 0) this.statusT -= 1 / 60;
    if (!this.map) return this.drawLibrary(W, H);
    return this.drawEditor(W, H, time, mouseDown);
  }

  // ---- library ----

  private drawLibrary(W: number, H: number): EditorAction {
    const ctx = ui.ctx;
    ctx.fillStyle = "#171208"; ctx.fillRect(0, 0, W, H);
    ui.text("Map Editor", W / 2, 58, { align: "center", size: 34, bold: true, color: PAL.uiAccent, font: "Georgia, serif" });
    ui.text("Build a battlefield. Paint the ground, seat the players, decide what it is for.",
      W / 2, 86, { align: "center", size: 13, color: "#9a917b" });

    const colW = Math.min(980, W - 80);
    const x0 = Math.round(W / 2 - colW / 2);
    let y = 122;

    // ---- new map ----
    const perRow = 5;
    const sizeRows = Math.ceil(MAP_SIZES.length / perRow);
    const panelH = 92 + sizeRows * 30 + 42;
    ui.panel(x0, y, colW, panelH, { light: true });
    ui.text("New map", x0 + 20, y + 26, { size: 16, bold: true, color: PAL.uiAccent });
    const chosen = MAP_SIZES.find((s) => s.cols === this.newSize);
    if (chosen) {
      ui.text(`${chosen.label} — ${chosen.cols} × ${chosen.cols} cells`, x0 + 110, y + 26,
        { size: 12, bold: true, color: "#e7ddc4" });
      ui.text(chosen.desc, x0 + 110, y + 42, { size: 11, color: "#9a917b" });
    }
    ui.text("Size", x0 + 20, y + 68, { size: 12, color: "#9a917b" });
    const sw = Math.floor((colW - 220) / perRow) - 6;
    MAP_SIZES.forEach((s, i) => {
      const bx2 = x0 + 66 + (i % perRow) * (sw + 6);
      const by2 = y + 56 + Math.floor(i / perRow) * 30;
      if (ui.button(s.label, bx2, by2, sw, 26, {
        accent: this.newSize === s.cols, size: 11,
        tooltip: [s.label, `${s.cols} × ${s.cols} cells`, s.desc],
      })) this.newSize = s.cols;
    });
    const by3 = y + 56 + sizeRows * 30 + 6;
    ui.text("Biome", x0 + 20, by3 + 13, { size: 12, color: "#9a917b" });
    BIOMES.forEach((b, i) => {
      if (ui.button(b.name, x0 + 66 + i * 110, by3, 104, 26,
        { accent: this.newBiome === b.id, size: 11.5, tooltip: [b.name, b.desc] })) this.newBiome = b.id;
    });
    if (ui.button("Create", x0 + colW - 140, y + 56, 120, 44, { accent: true, size: 15 })) {
      this.open(newCustomMap("New Map", this.newSize, this.newBiome));
    }
    y += panelH + 16;

    // ---- roll from the generator ----
    ui.panel(x0, y, colW, 78, { light: true });
    ui.text("Start from a generated map", x0 + 20, y + 24, { size: 14, bold: true, color: PAL.uiAccent });
    ui.text("Rolls a real map with its spawn points already placed, then hands it to you to edit.",
      x0 + 20, y + 40, { size: 11, color: "#9a917b" });
    {
      const pw = Math.min(96, Math.floor((colW - 300) / PRESETS.length) - 4);
      PRESETS.forEach((pp, i) => {
        if (ui.button(pp.name.split(" ")[0], x0 + 20 + i * (pw + 4), y + 48, pw, 22, {
          accent: this.rollPreset === pp.id, size: 10.5, tooltip: [pp.name, pp.desc],
        })) this.rollPreset = pp.id;
      });
      const rx = x0 + colW - 260;
      ui.text(`seed ${this.rollSeed}`, rx, y + 30, { size: 11, color: "#cabfa4", font: "ui-monospace, monospace" });
      if (ui.button("New seed", rx, y + 40, 84, 24, { size: 11 })) {
        this.rollSeed = 1 + Math.floor(Math.random() * 99999);
      }
      ui.text(`${this.rollPlayers} seats`, rx + 96, y + 30, { size: 11, color: "#cabfa4" });
      if (ui.button("−", rx + 96, y + 40, 26, 24, { size: 13, disabled: this.rollPlayers <= 2 })) this.rollPlayers--;
      if (ui.button("+", rx + 126, y + 40, 26, 24, { size: 13, disabled: this.rollPlayers >= MAX_SEATS })) this.rollPlayers++;
      if (ui.button("Roll & edit", rx + 160, y + 40, 96, 24, { accent: true, size: 12 })) {
        this.open(customFromGenerated(this.rollPreset, this.rollSeed, this.rollPlayers));
        this.say("Rolled — edit it however you like");
      }
    }
    y += 94;

    // ---- import ----
    ui.panel(x0, y, colW, 78, { light: true });
    ui.text("Paste a map code", x0 + 20, y + 26, { size: 14, bold: true, color: PAL.uiAccent });
    const fw = colW - 200;
    const fhov = ui.hit(x0 + 20, y + 38, fw, 26);
    ctx.fillStyle = this.editing === "import" ? "rgba(60,50,20,0.6)" : fhov ? "rgba(255,255,255,0.08)" : "rgba(0,0,0,0.32)";
    ctx.beginPath(); ctx.roundRect(x0 + 20, y + 38, fw, 26, 4); ctx.fill();
    ctx.strokeStyle = this.editing === "import" ? "#ffd24a" : "rgba(255,255,255,0.1)";
    ctx.lineWidth = 1; ctx.stroke();
    const shown = this.importBuf.length > 60 ? `…${this.importBuf.slice(-58)}` : this.importBuf;
    ui.text(shown || (this.editing === "import" ? "paste with Ctrl+V, then Enter" : "click, then paste a BBMAP code"),
      x0 + 28, y + 52, { size: 11, color: this.importBuf ? "#e7ddc4" : "#6f6a5c", font: "ui-monospace, monospace" });
    if (fhov && ui.clicked) { this.editing = this.editing === "import" ? null : "import"; }
    if (ui.button("Import", x0 + colW - 160, y + 36, 140, 30, { size: 13, disabled: !this.importBuf.trim() })) {
      const m = deserialiseMap(this.importBuf);
      if (m) { saveCustomMap(m); this.importBuf = ""; this.editing = null; this.say(`Imported "${m.name}"`); }
      else this.say("That isn't a map code");
    }
    y += 94;

    // ---- random map script ----
    // Folded away by default. It is the most powerful thing on this screen and
    // also the one most people will never touch, and an editor that greets you
    // with a code box has told you it is for programmers.
    if (this.showScript) {
      const shH = Math.min(360, H - y - 120);
      ui.panel(x0, y, colW, shH, { light: true });
      ui.text("Random map script", x0 + 20, y + 24, { size: 14, bold: true, color: PAL.uiAccent });
      ui.text("One script, a different map every seed — which is how map pools actually work.",
        x0 + 150, y + 24, { size: 11, color: "#9a917b" });
      if (ui.button("Hide", x0 + colW - 90, y + 12, 70, 24, { size: 12 })) this.showScript = false;

      const boxW = colW - 320;
      const boxH = shH - 96;
      const bhov = ui.hit(x0 + 20, y + 38, boxW, boxH);
      ctx.fillStyle = this.editing === "script" ? "rgba(40,34,14,0.85)" : "rgba(0,0,0,0.42)";
      ctx.beginPath(); ctx.roundRect(x0 + 20, y + 38, boxW, boxH, 4); ctx.fill();
      ctx.strokeStyle = this.editing === "script" ? "#ffd24a" : "rgba(255,255,255,0.1)";
      ctx.lineWidth = 1; ctx.stroke();
      // Line numbers, because every error the runner reports names one.
      const lines = this.scriptBuf.split("\n");
      const lh = 12;
      const visible = Math.floor((boxH - 12) / lh);
      const bad = new Set(this.scriptMsgs.filter((m2) => m2.bad).map((m2) => m2.line));
      lines.slice(0, visible).forEach((ln, i) => {
        ui.text(String(i + 1).padStart(3), x0 + 28, y + 52 + i * lh,
          { size: 9.5, color: bad.has(i + 1) ? "#e0786a" : "#5a5548", font: "ui-monospace, monospace" });
        ui.text(ln.slice(0, 78), x0 + 56, y + 52 + i * lh,
          { size: 10, color: bad.has(i + 1) ? "#ffb0a4" : "#cabfa4", font: "ui-monospace, monospace" });
      });
      if (lines.length > visible) {
        ui.text(`… ${lines.length - visible} more lines`, x0 + 56, y + 52 + visible * lh,
          { size: 9.5, color: "#6f6a5c" });
      }
      if (bhov && ui.clicked) { this.editing = this.editing === "script" ? null : "script"; ui.pointerConsumed = true; }

      // The command reference sits beside the box rather than in a manual —
      // this is a language nobody has seen before and there is nowhere else to
      // look it up.
      const rx = x0 + boxW + 36;
      ui.text("COMMANDS", rx, y + 50, { size: 9.5, bold: true, color: "#8f8770" });
      SCRIPT_COMMANDS.slice(0, Math.floor((boxH - 30) / 22)).forEach((cmd, i) => {
        ui.text(cmd.name, rx, y + 66 + i * 22, { size: 10.5, bold: true, color: "#e7ddc4", font: "ui-monospace, monospace" });
        ui.text(cmd.args.slice(0, 34), rx + 62, y + 66 + i * 22, { size: 9.5, color: "#8f8770", font: "ui-monospace, monospace" });
      });

      const by4 = y + shH - 46;
      ui.text(`seed ${this.scriptSeed}`, x0 + 20, by4 + 18, { size: 11, color: "#cabfa4", font: "ui-monospace, monospace" });
      if (ui.button("New seed", x0 + 110, by4, 84, 28, { size: 11 })) {
        this.scriptSeed = 1 + Math.floor(Math.random() * 99999);
      }
      if (ui.button("Reset to example", x0 + 202, by4, 130, 28, { size: 11 })) {
        this.scriptBuf = EXAMPLE_SCRIPT;
        this.scriptMsgs = [];
      }
      // Whatever the last run said, good or bad.
      const firstBad = this.scriptMsgs.find((m2) => m2.bad);
      if (this.scriptMsgs.length) {
        ui.text(firstBad ? `line ${firstBad.line}: ${firstBad.text}` : this.scriptMsgs[0].text,
          x0 + 344, by4 + 18, { size: 11, color: firstBad ? "#e0786a" : "#c3b98f" });
      }
      if (ui.button("Generate & edit", x0 + colW - 170, by4, 150, 28, { accent: true, size: 13 })) {
        const res = runMapScript(this.scriptBuf, this.scriptSeed, "Scripted map");
        this.scriptMsgs = [
          ...res.errors.map((e) => ({ ...e, bad: true })),
          ...res.warnings.map((e) => ({ ...e, bad: false })),
        ];
        if (res.map) {
          this.open(res.map);
          this.say(res.warnings.length ? "Generated, with notes" : "Generated");
        } else {
          this.say(`${res.errors.length} error${res.errors.length === 1 ? "" : "s"} in the script`);
        }
      }
      y += shH + 16;
    } else {
      ui.panel(x0, y, colW, 52, { light: true });
      ui.text("Random map script", x0 + 20, y + 24, { size: 13, bold: true, color: PAL.uiAccent });
      ui.text("Write rules instead of a map, and roll a different battlefield every seed.",
        x0 + 168, y + 24, { size: 11, color: "#9a917b" });
      if (ui.button("Open", x0 + colW - 110, y + 12, 90, 28, { size: 12 })) this.showScript = true;
      y += 68;
    }

    // ---- library ----
    const maps = listCustomMaps();
    ui.text(`Your maps  (${maps.length})`, x0, y, { size: 15, bold: true, color: PAL.uiAccent });
    y += 14;
    const rowH = 54;
    const listH = H - y - 90;
    ui.panel(x0, y, colW, Math.max(70, listH), { light: true });
    if (!maps.length) {
      ui.text("Nothing saved yet — make one above.", x0 + 20, y + 40, { size: 13, color: "#6f6a5c" });
    }
    let ry = y + 12;
    for (const m of maps) {
      if (ry + rowH > y + listH) break;
      const hov = ui.hit(x0 + 10, ry, colW - 20, rowH - 6);
      ctx.fillStyle = hov ? "rgba(255,255,255,0.06)" : "rgba(0,0,0,0.18)";
      ctx.beginPath(); ctx.roundRect(x0 + 10, ry, colW - 20, rowH - 6, 5); ctx.fill();
      this.thumbnail(ctx, x0 + 18, ry + 5, 38, m);
      ui.text(m.name, x0 + 66, ry + 20, { size: 14, bold: true, color: "#f2e8d0" });
      const modeText = m.modes.length ? m.modes.map((x) => MODES.find((y2) => y2.id === x)?.label ?? x).join(", ") : "any mode";
      const sizeLabel = MAP_SIZES.find((z) => z.cols === m.cols)?.label ?? `${m.cols}×${m.rows}`;
      ui.text(`${sizeLabel} (${m.cols}×${m.rows}) · ${biomeById(m.biome).name} · ${modeText} · ${m.minPlayers}–${m.maxPlayers} players${m.nomad === "forced" ? " · nomad" : ""}`,
        x0 + 66, ry + 38, { size: 11, color: "#9a917b" });
      if (ui.button("Edit", x0 + colW - 240, ry + 12, 68, 26, { size: 12 })) this.open(m);
      if (ui.button("Copy code", x0 + colW - 166, ry + 12, 84, 26, { size: 12,
        tooltip: ["Export", "Puts the map's share code in the box above so you can copy it."] })) {
        this.importBuf = serialiseMap(m);
        this.say("Code placed in the box above — select and copy it");
      }
      const armed = this.confirmDelete === m.id;
      if (ui.button(armed ? "Sure?" : "Delete", x0 + colW - 76, ry + 12, 62, 26, { size: 12, danger: armed })) {
        if (armed) { deleteCustomMap(m.id); this.confirmDelete = ""; this.say("Deleted"); }
        else this.confirmDelete = m.id;
      }
      ry += rowH;
    }

    if (ui.button("‹  Back", x0, H - 66, 140, 44, { size: 15 })) return { kind: "back" };
    if (this.statusT > 0) ui.text(this.status, x0 + 160, H - 44, { size: 12.5, color: "#ffd24a" });
    return null;
  }

  private open(m: CustomMap) {
    this.map = m;
    this.undo.length = 0;
    this.redo.length = 0;
    this.dirty = true;
    this.confirmDelete = "";
    this.editing = null;
    // Frame the whole map to begin with — you cannot edit what you cannot see.
    // The canvas size is only known once we draw, so the fit happens there.
    this.needsFit = true;
    this.camX = m.cols / 2;
    this.camY = m.rows / 2;
  }

  /** A tiny picture of a map, for the library rows. */
  private thumbnail(ctx: CanvasRenderingContext2D, x: number, y: number, size: number, m: CustomMap) {
    drawMapThumbnail(ctx, x, y, size, m, { spawns: true });
  }

  // ---- editor ----

  private drawEditor(W: number, H: number, time: number, mouseDown: boolean): EditorAction {
    const m = this.map!;
    const ctx = ui.ctx;
    ctx.fillStyle = "#12100a"; ctx.fillRect(0, 0, W, H);

    if (this.dirty) { this.issues = validateMap(m); this.dirty = false; }

    const canvasX = RAIL, canvasY = 44;
    const canvasW = W - RAIL - PROPS, canvasH = H - canvasY - 52;

    this.drawCanvas(canvasX, canvasY, canvasW, canvasH, m, mouseDown, time);
    this.drawToolRail(0, canvasY, RAIL, H - canvasY - 52, m);
    const action = this.drawProperties(W - PROPS, canvasY, PROPS, H - canvasY - 52, m, time);
    if (action) return action;

    // ---- top bar ----
    ctx.fillStyle = "rgba(24,18,10,0.96)"; ctx.fillRect(0, 0, W, canvasY);
    ctx.fillStyle = withAlpha(PAL.uiAccent, 0.4); ctx.fillRect(0, canvasY - 1.5, W, 1.5);
    ui.text("Editing", 16, 27, { size: 12, color: "#9a917b" });
    // The name is editable in place — a map you cannot name is a map you cannot find.
    const nameW = 260;
    const nhov = ui.hit(70, 10, nameW, 24);
    ctx.fillStyle = this.editing === "name" ? "rgba(60,50,20,0.7)" : nhov ? "rgba(255,255,255,0.07)" : "transparent";
    ctx.beginPath(); ctx.roundRect(70, 10, nameW, 24, 4); ctx.fill();
    ui.text(m.name + (this.editing === "name" && Math.floor(time * 2) % 2 ? "_" : ""), 78, 27,
      { size: 15, bold: true, color: "#f2e8d0" });
    if (nhov && ui.clicked) { this.editing = this.editing === "name" ? null : "name"; ui.pointerConsumed = true; }

    if (ui.button("Undo", 350, 8, 62, 28, { size: 12, disabled: !this.undo.length, tooltip: ["Undo", "Ctrl+Z"] })) this.undoStep();
    if (ui.button("Redo", 416, 8, 62, 28, { size: 12, disabled: !this.redo.length, tooltip: ["Redo", "Ctrl+Y"] })) this.redoStep();
    if (ui.button("Fit", 486, 8, 48, 28, { size: 12, tooltip: ["Fit to view", "Frame the whole map again."] })) this.needsFit = true;
    if (ui.button("Level land", 538, 8, 96, 28, { size: 12,
      tooltip: ["Level the land", "Flattens every cell back to this biome's base ground.", "Resources and spawns are kept."] })) this.levelAll();

    const errs = hasErrors(this.issues);
    if (ui.button("Save", W - 300, 8, 78, 28, { size: 12.5, accent: true })) {
      saveCustomMap(m); this.say(`Saved "${m.name}"`);
    }
    if (ui.button("Test map", W - 216, 8, 92, 28, {
      size: 12.5, disabled: errs,
      tooltip: errs ? ["Cannot test", "Fix the errors listed on the right first."] : ["Test", "Save and play a match on this map right now."],
    })) {
      saveCustomMap(m);
      return { kind: "test", map: m };
    }
    if (ui.button("Close", W - 118, 8, 78, 28, { size: 12.5 })) {
      saveCustomMap(m);
      this.map = null;
      this.say("Saved");
    }
    if (this.statusT > 0) {
      ui.text(this.status, W / 2, H - 26, { align: "center", size: 13, bold: true, color: "#ffd24a" });
    }
    return null;
  }

  // ---- canvas ----

  private drawCanvas(x: number, y: number, w: number, h: number, m: CustomMap, mouseDown: boolean, time: number) {
    const ctx = ui.ctx;
    ctx.save();
    ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip();
    ctx.fillStyle = "#0b0906"; ctx.fillRect(x, y, w, h);

    if (this.needsFit) {
      this.needsFit = false;
      this.zoom = Math.max(1.5, Math.min(24, Math.min(w / m.cols, h / m.rows) * 0.92));
      this.camX = m.cols / 2;
      this.camY = m.rows / 2;
    }
    const z = this.zoom;
    const toScreenX = (cx: number) => x + w / 2 + (cx - this.camX) * z;
    const toScreenY = (cy: number) => y + h / 2 + (cy - this.camY) * z;
    const toCellX = (sx: number) => (sx - x - w / 2) / z + this.camX;
    const toCellY = (sy: number) => (sy - y - h / 2) / z + this.camY;

    // Only the cells actually on screen — a 200×200 map is 40,000 of them and
    // the editor redraws every frame.
    const c0 = Math.max(0, Math.floor(toCellX(x)));
    const c1 = Math.min(m.cols - 1, Math.ceil(toCellX(x + w)));
    const r0 = Math.max(0, Math.floor(toCellY(y)));
    const r1 = Math.min(m.rows - 1, Math.ceil(toCellY(y + h)));
    for (let cy = r0; cy <= r1; cy++) {
      for (let cx = c0; cx <= c1; cx++) {
        ctx.fillStyle = SWATCH[m.terrain[cy * m.cols + cx]] ?? "#6f9c4a";
        ctx.fillRect(toScreenX(cx), toScreenY(cy), z + 1, z + 1);
      }
    }
    // Resources.
    for (const r of m.resources) {
      const sx = toScreenX(r.x / TILE), sy = toScreenY(r.y / TILE);
      if (sx < x - 8 || sx > x + w + 8 || sy < y - 8 || sy > y + h + 8) continue;
      ctx.fillStyle = RES_COLOUR[r.type];
      ctx.beginPath();
      ctx.arc(sx + z / 2, sy + z / 2, Math.max(1.6, z * 0.42), 0, Math.PI * 2);
      ctx.fill();
    }
    // Spawn points, numbered by seat.
    m.spawns.forEach((s, i) => {
      const sx = toScreenX(s.x / TILE) + z / 2, sy = toScreenY(s.y / TILE) + z / 2;
      const r = Math.max(7, z * 2);
      ctx.fillStyle = withAlpha(PAL.teams[i % PAL.teams.length].main, 0.4);
      ctx.beginPath(); ctx.arc(sx, sy, r, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = PAL.teams[i % PAL.teams.length].main; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.arc(sx, sy, r, 0, Math.PI * 2); ctx.stroke();
      ui.text(String(i + 1), sx, sy + 4, { align: "center", size: 12, bold: true, color: "#fff" });
    });
    // Map border.
    ctx.strokeStyle = withAlpha(PAL.uiAccent, 0.5); ctx.lineWidth = 1.5;
    ctx.strokeRect(toScreenX(0), toScreenY(0), m.cols * z, m.rows * z);

    // ---- interaction ----
    const over = ui.hit(x, y, w, h) && !ui.pointerConsumed;
    const hx = Math.floor(toCellX(ui.mx));
    const hy = Math.floor(toCellY(ui.my));
    if (over) {
      // Symmetry preview: show every cell this stroke would touch.
      const cells = symmetryCells(this.sym, hx, hy, m.cols, m.rows);
      const rr = this.tool === "terrain" ? this.brush : 1;
      for (const [sx, sy] of cells) {
        ctx.strokeStyle = withAlpha("#ffd24a", sx === hx && sy === hy ? 0.95 : 0.5);
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(toScreenX(sx) + z / 2, toScreenY(sy) + z / 2, Math.max(3, rr * z), 0, Math.PI * 2);
        ctx.stroke();
      }
      if (mouseDown && ui.rightHeld) {
        // Right-drag pans, which is the only way to work on a big map.
        if (this.lastPan) {
          this.camX -= (ui.mx - this.lastPan.x) / z;
          this.camY -= (ui.my - this.lastPan.y) / z;
        }
        this.lastPan = { x: ui.mx, y: ui.my };
      } else if (mouseDown) {
        this.beginStroke();
        if (hx >= 0 && hy >= 0 && hx < m.cols && hy < m.rows) {
          if (this.tool === "terrain") this.paintAt(hx, hy);
          else if (this.tool === "scatter") this.scatterAt(hx, hy);
          else if (this.tool === "resource") this.placeResource(hx, hy);
          else if (this.tool === "spawn") this.placeSpawn(hx, hy);
          else if (this.tool === "path" || this.tool === "region") {
            // Both are press-here-release-there gestures. Remember where the
            // press landed; the work happens on release, below.
            if (!this.dragFrom) this.dragFrom = { cx: hx, cy: hy };
          } else this.eraseAt(hx, hy);
        }
      }
      if (ui.wheel) {
        const before = [toCellX(ui.mx), toCellY(ui.my)] as const;
        this.zoom = Math.max(1.5, Math.min(24, this.zoom * (ui.wheel < 0 ? 1.15 : 1 / 1.15)));
        // Keep the cell under the cursor under the cursor.
        this.camX += before[0] - toCellX(ui.mx);
        this.camY += before[1] - toCellY(ui.my);
      }
      ui.text(`${hx}, ${hy}`, x + w - 10, y + h - 10, { align: "right", size: 11, color: "#8a8278", font: "ui-monospace, monospace" });
    }
    // Preview for the two gesture tools, drawn while the button is still down so
    // an author can see the run or the rectangle before committing to it.
    if (this.dragFrom && mouseDown) {
      const a = this.dragFrom;
      ctx.setLineDash([4, 3]);
      ctx.strokeStyle = "#ffd24a";
      ctx.lineWidth = 1.5;
      if (this.tool === "path") {
        ctx.beginPath();
        ctx.moveTo(toScreenX(a.cx) + z / 2, toScreenY(a.cy) + z / 2);
        ctx.lineTo(toScreenX(hx) + z / 2, toScreenY(hy) + z / 2);
        ctx.stroke();
      } else {
        const sx = toScreenX(Math.min(a.cx, hx)), sy = toScreenY(Math.min(a.cy, hy));
        ctx.strokeRect(sx, sy, (Math.abs(hx - a.cx) + 1) * z, (Math.abs(hy - a.cy) + 1) * z);
      }
      ctx.setLineDash([]);
    }
    // A copied region follows the cursor, so a stamp lands where you expect.
    if (over && this.tool === "region" && this.clip && !this.dragFrom) {
      ctx.strokeStyle = withAlpha("#7fd0ff", 0.9);
      ctx.lineWidth = 1.5;
      ctx.strokeRect(toScreenX(hx), toScreenY(hy), this.clip.w * z, this.clip.h * z);
    }

    if (!mouseDown) {
      // Release: this is where the gesture tools actually do their work, so the
      // author sees the whole shape before it is committed.
      if (this.dragFrom) {
        const a = this.dragFrom;
        this.dragFrom = null;
        const inside = hx >= 0 && hy >= 0 && hx < m.cols && hy < m.rows;
        if (this.tool === "path" && inside) {
          this.strokePath(a.cx, a.cy, hx, hy);
        } else if (this.tool === "region") {
          // A drag lifts a region; a click (start and end on one cell) stamps
          // the one already held. One tool, because copy and paste are two
          // halves of the same act and splitting them across two buttons means
          // more trips to the rail than to the map.
          if (a.cx === hx && a.cy === hy && this.clip) this.pasteRegion(hx, hy);
          else this.copyRegion(a.cx, a.cy, hx, hy);
        }
      }
      this.strokeOpen = false;
      this.lastPan = null;
    }
    // Clamp so the map can never be lost off-screen entirely.
    this.camX = Math.max(-w / z / 2, Math.min(m.cols + w / z / 2, this.camX));
    this.camY = Math.max(-h / z / 2, Math.min(m.rows + h / z / 2, this.camY));

    ui.text("right-drag to pan · wheel to zoom · 1–9 pick ground · [ ] brush size · Ctrl+Z undo",
      x + 10, y + h - 10, { size: 10.5, color: "#6f6a5c" });
    ctx.restore();
    void time;
  }

  // ---- tool rail ----

  private drawToolRail(x: number, y: number, w: number, h: number, m: CustomMap) {
    const ctx = ui.ctx;
    ctx.fillStyle = "rgba(20,16,9,0.96)"; ctx.fillRect(x, y, w, h);
    ctx.fillStyle = withAlpha(PAL.uiAccent, 0.25); ctx.fillRect(x + w - 1, y, 1, h);
    ui.blockPointer(x, y, w, h);
    let ry = y + 14;

    const tools: [Tool, string, string][] = [
      ["terrain", "Ground", "Paint terrain with the selected brush."],
      ["path", "Path", "Drag a straight run — rivers, ridges and roads are lines, and a round brush makes a wobble."],
      ["scatter", "Scatter", "Drag to sprinkle resources at a density, instead of placing each node by hand."],
      ["region", "Region", "Drag to copy a rectangle, then click to stamp it. Build one base properly and repeat it at every seat."],
      ["resource", "Resources", "Place trees, gold and berries one at a time."],
      ["spawn", "Spawns", "Seat a player. Numbered in the order you place them."],
      ["erase", "Erase", "Remove resources and spawn points under the brush."],
    ];
    for (let i = 0; i < tools.length; i++) {
      const [id, label, desc] = tools[i];
      const bx = x + 10 + (i % 2) * 98;
      const by = ry + Math.floor(i / 2) * 32;
      const disabled = id === "spawn" && m.nomad === "forced";
      if (ui.button(label, bx, by, 92, 28, {
        accent: this.tool === id, size: 12, disabled,
        tooltip: disabled ? ["Spawns disabled", "This map is set to always nomad."] : [label, desc],
      })) this.tool = id;
    }
    ry += Math.ceil(tools.length / 2) * 32 + 10;

    if (this.tool === "terrain" || this.tool === "path") {
      ui.text("GROUND", x + 12, ry, { size: 10, bold: true, color: "#8f8770" });
      ry += 10;
      PALETTE.forEach((t, i) => {
        const bx = x + 10 + (i % 2) * 98;
        const by = ry + Math.floor(i / 2) * 30;
        const def = terrainDef(t);
        const sel = this.paint === t;
        ctx.fillStyle = sel ? withAlpha("#ffd24a", 0.22) : ui.hit(bx, by, 92, 26) ? "rgba(255,255,255,0.07)" : "rgba(0,0,0,0.25)";
        ctx.beginPath(); ctx.roundRect(bx, by, 92, 26, 4); ctx.fill();
        if (sel) { ctx.strokeStyle = "#ffd24a"; ctx.lineWidth = 1.4; ctx.stroke(); }
        ctx.fillStyle = SWATCH[t] ?? "#6f9c4a";
        ctx.beginPath(); ctx.roundRect(bx + 5, by + 5, 16, 16, 3); ctx.fill();
        ui.text(def.name, bx + 27, by + 14, { size: 10.5, color: sel ? "#ffe9b0" : "#cabfa4" });
        if (ui.hit(bx, by, 92, 26)) {
          ui.pointerConsumed = true;
          ui.hoveredTooltip = { text: [def.name, def.desc, `${i + 1}`], x: ui.mx, y: by };
          if (ui.clicked) this.paint = t;
        }
      });
      ry += Math.ceil(PALETTE.length / 2) * 30 + 10;
      if (this.tool === "path") {
        ui.text("Drag from one point to another.", x + 12, ry, { size: 10.5, color: "#9a917b" });
        ui.text("Brush size sets how wide the run is.", x + 12, ry + 14, { size: 10.5, color: "#9a917b" });
        ry += 34;
      }
    } else if (this.tool === "scatter") {
      ui.text("SCATTER", x + 12, ry, { size: 10, bold: true, color: "#8f8770" });
      ry += 12;
      for (const k of ["tree", "gold_mine", "berries"] as ResourceKind[]) {
        if (ui.button(RES_LABEL[k], x + 10, ry, 190, 28, { accent: this.resource === k, size: 12 })) this.resource = k;
        ctx.fillStyle = RES_COLOUR[k];
        ctx.beginPath(); ctx.arc(x + 24, ry + 14, 5, 0, Math.PI * 2); ctx.fill();
        ry += 32;
      }
      ry += 6;
      ui.text(`DENSITY  ${Math.round(this.scatterDensity * 100)}%`, x + 12, ry, { size: 10, bold: true, color: "#8f8770" });
      const dv = ui.slider(x + 12, ry + 20, w - 34, (this.scatterDensity - 0.05) / 0.75, ui.clicked || ui.leftHeld);
      this.scatterDensity = Math.max(0.05, Math.min(0.8, 0.05 + dv * 0.75));
      ry += 40;
      // Re-rolling matters: the sprinkle is a function of the cell, so without
      // this an author who dislikes a clump can only move somewhere else.
      if (ui.button("Re-roll pattern", x + 10, ry, 190, 26, { size: 12,
        tooltip: ["Shuffle the sprinkle", "The pattern is fixed per cell so dragging back over ground doesn't thicken it. This picks a different one."] })) {
        this.scatterSeed = (this.scatterSeed * 1664525 + 1013904223) >>> 0;
        this.say("New scatter pattern");
      }
      ry += 36;
    } else if (this.tool === "region") {
      ui.text("REGION", x + 12, ry, { size: 10, bold: true, color: "#8f8770" });
      ry += 14;
      if (this.clip) {
        ui.text(`Holding ${this.clip.w} × ${this.clip.h} cells`, x + 12, ry, { size: 11, color: "#e7ddc4" });
        ry += 16;
        ui.text("Click the map to stamp it.", x + 12, ry, { size: 10.5, color: "#9a917b" });
        ry += 20;
        if (ui.button(this.clipFlipX ? "Flip ↔ on" : "Flip ↔ off", x + 10, ry, 92, 26,
          { size: 11, accent: this.clipFlipX })) this.clipFlipX = !this.clipFlipX;
        if (ui.button(this.clipFlipY ? "Flip ↕ on" : "Flip ↕ off", x + 108, ry, 92, 26,
          { size: 11, accent: this.clipFlipY })) this.clipFlipY = !this.clipFlipY;
        ry += 32;
        if (ui.button("Drop it", x + 10, ry, 190, 26, { size: 12 })) { this.clip = null; this.say("Region dropped"); }
        ry += 36;
      } else {
        ui.text("Drag a rectangle to copy the", x + 12, ry, { size: 10.5, color: "#9a917b" });
        ui.text("ground and everything on it.", x + 12, ry + 14, { size: 10.5, color: "#9a917b" });
        ry += 40;
      }
    } else if (this.tool === "resource") {
      ui.text("RESOURCE", x + 12, ry, { size: 10, bold: true, color: "#8f8770" });
      ry += 12;
      for (const k of ["tree", "gold_mine", "berries"] as ResourceKind[]) {
        if (ui.button(RES_LABEL[k], x + 10, ry, 190, 28, { accent: this.resource === k, size: 12 })) this.resource = k;
        ctx.fillStyle = RES_COLOUR[k];
        ctx.beginPath(); ctx.arc(x + 24, ry + 14, 5, 0, Math.PI * 2); ctx.fill();
        ry += 32;
      }
      ry += 6;
    } else if (this.tool === "spawn") {
      ui.text(`SPAWNS  ${m.spawns.length} / 8`, x + 12, ry, { size: 10, bold: true, color: "#8f8770" });
      ry += 14;
      ui.text("Click the map to seat a player.", x + 12, ry, { size: 10.5, color: "#9a917b" });
      ry += 14;
      ui.text("Symmetry seats them all at once.", x + 12, ry, { size: 10.5, color: "#9a917b" });
      ry += 20;
      if (ui.button("Clear all spawns", x + 10, ry, 190, 26, { size: 12, disabled: !m.spawns.length })) {
        this.undo.push(this.snapshot());
        m.spawns = [];
        this.dirty = true;
      }
      ry += 36;
    } else {
      ui.text("Drag over resources or spawn", x + 12, ry, { size: 10.5, color: "#9a917b" });
      ui.text("points to remove them.", x + 12, ry + 14, { size: 10.5, color: "#9a917b" });
      ry += 40;
    }

    // Brush size applies to ground and erase.
    if (this.tool === "terrain" || this.tool === "path" || this.tool === "scatter"
      || this.tool === "erase" || this.tool === "resource") {
      ui.text(`BRUSH  ${this.brush}`, x + 12, ry, { size: 10, bold: true, color: "#8f8770" });
      const nv = ui.slider(x + 12, ry + 20, w - 34, (this.brush - 1) / 23, ui.clicked || ui.leftHeld);
      this.brush = Math.max(1, Math.round(1 + nv * 23));
      ry += 40;
    }

    ui.text("SYMMETRY", x + 12, ry, { size: 10, bold: true, color: "#8f8770" });
    ry += 10;
    SYMMETRIES.forEach((s, i) => {
      const bx = x + 10 + (i % 2) * 98;
      const by = ry + Math.floor(i / 2) * 28;
      if (ui.button(s.label, bx, by, 92, 24, { accent: this.sym === s.id, size: 10.5, tooltip: [s.label, s.desc] })) {
        this.sym = s.id;
      }
    });
  }

  // ---- properties + validation ----

  private drawProperties(x: number, y: number, w: number, h: number, m: CustomMap, time: number): EditorAction {
    const ctx = ui.ctx;
    ctx.fillStyle = "rgba(20,16,9,0.96)"; ctx.fillRect(x, y, w, h);
    ctx.fillStyle = withAlpha(PAL.uiAccent, 0.25); ctx.fillRect(x, y, 1, h);
    ui.blockPointer(x, y, w, h);
    let ry = y + 18;
    const lx = x + 14;

    ui.text("MAP", lx, ry, { size: 10, bold: true, color: "#8f8770" });
    ry += 14;
    {
      const sz = MAP_SIZES.find((z) => z.cols === m.cols);
      ui.text(`${sz ? sz.label + " · " : ""}${m.cols} × ${m.rows} cells`, lx, ry, { size: 11.5, color: "#cabfa4" });
      ry += 20;
    }

    // What the map is *for*. A line here is the difference between a map people
    // scroll past in the lobby and one they pick.
    ui.text("DESCRIPTION", lx, ry, { size: 10, bold: true, color: "#8f8770" });
    ry += 8;
    {
      const dh = 46;
      const dhov = ui.hit(lx, ry, w - 28, dh);
      ctx.fillStyle = this.editing === "desc" ? "rgba(60,50,20,0.7)"
        : dhov ? "rgba(255,255,255,0.07)" : "rgba(0,0,0,0.28)";
      ctx.beginPath(); ctx.roundRect(lx, ry, w - 28, dh, 4); ctx.fill();
      ctx.strokeStyle = this.editing === "desc" ? "#ffd24a" : "rgba(255,255,255,0.1)";
      ctx.lineWidth = 1; ctx.stroke();
      const shown = m.desc || (this.editing === "desc" ? "" : "click to describe this map");
      // Wrapped by hand at a character count rather than measured: the rail is a
      // fixed width and the font is fixed, so this is exact enough and costs
      // nothing per frame.
      const lines: string[] = [];
      for (const word of shown.split(" ")) {
        if (!lines.length || (lines[lines.length - 1] + " " + word).length > 34) lines.push(word);
        else lines[lines.length - 1] += " " + word;
      }
      lines.slice(0, 3).forEach((ln, i) => {
        ui.text(ln + (this.editing === "desc" && i === Math.min(lines.length, 3) - 1
          && Math.floor(time * 2) % 2 ? "_" : ""), lx + 7, ry + 15 + i * 13,
          { size: 10.5, color: m.desc ? "#e7ddc4" : "#6f6a5c" });
      });
      if (dhov && ui.clicked) { this.editing = this.editing === "desc" ? null : "desc"; ui.pointerConsumed = true; }
      ry += dh + 14;
    }

    // Biome — changing it repaints only the untouched base ground, so the work
    // you have already done survives a change of mind about the setting.
    ui.text("BIOME", lx, ry, { size: 10, bold: true, color: "#8f8770" });
    ry += 10;
    BIOMES.forEach((b, i) => {
      const bx = lx + (i % 2) * 138;
      const by = ry + Math.floor(i / 2) * 28;
      if (ui.button(b.name, bx, by, 132, 24, { accent: m.biome === b.id, size: 11, tooltip: [b.name, b.desc] })) {
        m.biome = b.id;
        this.dirty = true;
      }
    });
    ry += 64;

    ui.text("GAME MODES", lx, ry, { size: 10, bold: true, color: "#8f8770" });
    ui.text(m.modes.length ? "" : "any", x + w - 14, ry, { align: "right", size: 10, color: "#6f6a5c" });
    ry += 10;
    MODES.forEach((md, i) => {
      const bx = lx + (i % 2) * 138;
      const by = ry + Math.floor(i / 2) * 28;
      const on = m.modes.includes(md.id);
      if (ui.button(md.label, bx, by, 132, 24, { accent: on, size: 10.5,
        tooltip: [md.label, on ? "Click to remove" : "Click to allow this mode"] })) {
        m.modes = on ? m.modes.filter((z) => z !== md.id) : [...m.modes, md.id];
        this.dirty = true;
      }
    });
    ry += 62;
    ui.text("None selected means any mode.", lx, ry, { size: 10, color: "#6f6a5c" });
    ry += 18;

    ui.text("PLAYERS", lx, ry, { size: 10, bold: true, color: "#8f8770" });
    ry += 12;
    // Nothing here is derived from the map's size — a Duel map may seat eight
    // if that is the fight you want, and a Colossal one may seat two.
    const stepper = (label: string, value: number, set: (v: number) => void) => {
      ui.text(label, lx, ry + 13, { size: 11.5, color: "#cabfa4" });
      if (ui.button("−", lx + 96, ry, 26, 24, { size: 13, disabled: value <= 1 })) { set(value - 1); this.dirty = true; }
      ui.text(String(value), lx + 136, ry + 13, { align: "center", size: 13, bold: true, color: "#f2e8d0" });
      if (ui.button("+", lx + 150, ry, 26, 24, { size: 13, disabled: value >= MAX_SEATS })) { set(value + 1); this.dirty = true; }
      ry += 30;
    };
    stepper("Minimum", m.minPlayers, (v) => { m.minPlayers = v; if (m.maxPlayers < v) m.maxPlayers = v; });
    stepper("Maximum", m.maxPlayers, (v) => { m.maxPlayers = v; if (m.minPlayers > v) m.minPlayers = v; });
    if (ui.button("Match spawns", lx, ry, 110, 24, {
      size: 11, disabled: !m.spawns.length || m.nomad === "forced",
      tooltip: ["Match the seats to the map", `Set the maximum to the ${m.spawns.length} spawn point${m.spawns.length === 1 ? "" : "s"} you have placed.`],
    })) {
      m.maxPlayers = Math.max(1, m.spawns.length);
      if (m.minPlayers > m.maxPlayers) m.minPlayers = m.maxPlayers;
      this.dirty = true;
    }
    if (ui.button("Any", lx + 118, ry, 58, 24, { size: 11,
      tooltip: ["Open it up", `One to ${MAX_SEATS} players, any mode.`] })) {
      m.minPlayers = 1; m.maxPlayers = MAX_SEATS; m.modes = []; this.dirty = true;
    }
    ry += 30;
    ui.text(`The engine seats ${MAX_SEATS} realms at most.`, lx, ry, { size: 10, color: "#6f6a5c" });
    ry += 22;

    ui.text("NOMAD", lx, ry, { size: 10, bold: true, color: "#8f8770" });
    ry += 10;
    NOMAD_RULES.forEach((n: { id: NomadRule; label: string; desc: string }, i) => {
      const bx = lx + (i % 3) * 92;
      if (ui.button(n.label, bx, ry, 86, 24, { accent: m.nomad === n.id, size: 10.5, tooltip: [n.label, n.desc] })) {
        m.nomad = n.id;
        // Turning nomad on for good takes the spawn tool away with it, so the
        // author is never painting seats that will be ignored.
        if (n.id === "forced" && this.tool === "spawn") this.tool = "terrain";
        this.dirty = true;
      }
    });
    ry += 32;
    if (m.nomad === "forced") {
      ui.text("Spawn points are disabled on this map.", lx, ry, { size: 10, color: "#e0a878" });
      ry += 16;
    }
    ry += 6;

    // ---- validation ----
    const errs = this.issues.filter((i) => i.level === "error");
    const warns = this.issues.filter((i) => i.level === "warning");
    ctx.strokeStyle = "rgba(255,255,255,0.08)"; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(lx, ry); ctx.lineTo(x + w - 14, ry); ctx.stroke();
    ry += 16;
    if (!this.issues.length) {
      ui.text("✔ Ready to play", lx, ry, { size: 12.5, bold: true, color: "#7df2a9" });
      ry += 20;
    } else {
      ui.text(`${errs.length} error${errs.length === 1 ? "" : "s"} · ${warns.length} warning${warns.length === 1 ? "" : "s"}`,
        lx, ry, { size: 11.5, bold: true, color: errs.length ? "#e0564a" : "#e0a878" });
      ry += 16;
    }
    for (const issue of [...errs, ...warns]) {
      if (ry > y + h - 20) { ui.text("…", lx, ry, { size: 11, color: "#6f6a5c" }); break; }
      const col = issue.level === "error" ? "#e0564a" : "#e0a878";
      ctx.fillStyle = col;
      ctx.beginPath(); ctx.arc(lx + 3, ry - 3, 2.6, 0, Math.PI * 2); ctx.fill();
      for (const line of wrapText(issue.text, 36)) {
        ui.text(line, lx + 12, ry, { size: 10.5, color: issue.level === "error" ? "#f0b0a6" : "#d8c39a" });
        ry += 13;
      }
      ry += 5;
    }
    return null;
  }
}

/** Greedy wrap to a character budget — the rail is narrow and text must fit. */
function wrapText(text: string, per: number): string[] {
  const words = text.split(" ");
  const out: string[] = [];
  let line = "";
  for (const w of words) {
    if (line && (line + " " + w).length > per) { out.push(line); line = w; }
    else line = line ? `${line} ${w}` : w;
  }
  if (line) out.push(line);
  return out;
}
