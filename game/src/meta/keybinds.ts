// Rebindable hotkeys.
//
// The defaults follow Age of Empires conventions wherever the game has an
// equivalent, because that is the muscle memory most RTS players arrive with:
//
//   .  /  ,        next / previous idle villager
//   H              select the Town Centre and go there
//   Space          jump to the last thing that happened to you
//   Ctrl+1..9      assign a control group   (1..9 selects, twice re-centres)
//   Shift+1..9     add the selection to a group
//   Ctrl+A         select every soldier you own
//   F3             pause
//   Del            delete the selection
//
// Everything here is data: an action has an id, a label, a group and a default
// chord. Bindings live in Settings, so a rebind survives a reload, and any
// action can be cleared or moved without touching the code that fires it.

/** Every rebindable action. Adding one only needs an entry in ACTIONS below. */
export type ActionId =
  // Unit commands
  | "attackMove" | "stop" | "hold" | "ability" | "garrison" | "cycleStance"
  | "buildMenu" | "commanderPower" | "deleteUnit"
  // Selection
  | "idleVillagerNext" | "idleVillagerPrev" | "selectArmy" | "selectTownCentre"
  // Camera
  | "cameraLastEvent" | "cameraTownCentre"
  | "panUp" | "panDown" | "panLeft" | "panRight"
  // Interface
  | "scoreboard" | "productionPanel" | "perfOverlay" | "chat" | "menu"
  // Game
  | "pause" | "speedUp" | "speedDown";

export type KeybindGroup = "Units" | "Selection" | "Camera" | "Interface" | "Game";

export interface ActionDef {
  id: ActionId;
  label: string;
  group: KeybindGroup;
  /** Default chord, in the canonical form produced by `chordOf`. */
  def: string;
  /** A second default that also fires it and can't be rebound (e.g. arrow keys). */
  alt?: string;
  /** Shown under the label in the rebinding list. */
  hint?: string;
}

export const ACTIONS: ActionDef[] = [
  // ---- unit commands ----
  { id: "attackMove", label: "Attack-move", group: "Units", def: "A", hint: "Advance, engaging anything on the way" },
  { id: "stop", label: "Stop", group: "Units", def: "S" },
  { id: "hold", label: "Hold position", group: "Units", def: "Shift+H", hint: "H is the Town Centre, as in Age of Empires" },
  { id: "ability", label: "Use ability", group: "Units", def: "Q" },
  { id: "garrison", label: "Garrison / eject", group: "Units", def: "G" },
  { id: "cycleStance", label: "Cycle stance", group: "Units", def: "Y", hint: "Aggressive · Defensive · Stand Ground · Passive · Skirmish" },
  { id: "buildMenu", label: "Build menu", group: "Units", def: "B" },
  { id: "commanderPower", label: "Commander power", group: "Units", def: "C" },
  { id: "deleteUnit", label: "Delete selected", group: "Units", def: "Delete" },
  // ---- selection ----
  { id: "idleVillagerNext", label: "Next idle villager", group: "Selection", def: "." },
  { id: "idleVillagerPrev", label: "Previous idle villager", group: "Selection", def: "," },
  { id: "selectArmy", label: "Select all soldiers", group: "Selection", def: "Ctrl+A" },
  { id: "selectTownCentre", label: "Select Town Centre", group: "Selection", def: "H", hint: "Selects it and takes the camera there" },
  // ---- camera ----
  { id: "cameraLastEvent", label: "Go to last event", group: "Camera", def: "Space", hint: "The last attack, completion or alert" },
  { id: "cameraTownCentre", label: "Go to Town Centre", group: "Camera", def: "Home", hint: "Camera only — nothing is selected" },
  // Panning is *held*, not tapped, so it must not share a key with a command:
  // WASD used to pan and issue orders at the same time, which is why only two
  // of the four directions ever worked. Arrows by default; W/D are free if you
  // want them, and A/S become free the moment you move Attack-move and Stop.
  { id: "panUp", label: "Pan up", group: "Camera", def: "ArrowUp" },
  { id: "panDown", label: "Pan down", group: "Camera", def: "ArrowDown" },
  { id: "panLeft", label: "Pan left", group: "Camera", def: "ArrowLeft" },
  { id: "panRight", label: "Pan right", group: "Camera", def: "ArrowRight" },
  // ---- interface ----
  { id: "scoreboard", label: "Scoreboard", group: "Interface", def: "Tab" },
  { id: "productionPanel", label: "Production overview", group: "Interface", def: "V" },
  { id: "perfOverlay", label: "Performance overlay", group: "Interface", def: "F10", hint: "Frame time, tick time, entity count" },
  { id: "chat", label: "Chat (multiplayer)", group: "Interface", def: "Enter" },
  { id: "menu", label: "Menu / cancel", group: "Interface", def: "Escape" },
  // ---- game ----
  { id: "pause", label: "Pause", group: "Game", def: "F3", alt: "P" },
  { id: "speedUp", label: "Speed up", group: "Game", def: "+" },
  { id: "speedDown", label: "Slow down", group: "Game", def: "-" },
];

export const ACTION_BY_ID = new Map(ACTIONS.map((a) => [a.id, a]));
export const KEYBIND_GROUPS: KeybindGroup[] = ["Units", "Selection", "Camera", "Interface", "Game"];

/** A binding map. Missing entries fall back to the action's default. */
export type Keybinds = Partial<Record<ActionId, string>>;

/**
 * Canonical name for a key. Single characters upper-case, so "a" and "A" are
 * the same binding; everything else keeps its DOM name ("Tab", "F3", "Home").
 * The two shifted keys people actually use for speed are folded onto their
 * unshifted twins, so "+" works whether or not you reach it with Shift.
 */
export function keyName(key: string): string {
  if (key === " ") return "Space";
  if (key === "=") return "+";
  if (key === "_") return "-";
  if (key.length === 1) return key.toUpperCase();
  return key;
}

/** Canonical chord: modifiers in a fixed order, then the key. */
export function chordOf(key: string, mods: { ctrl?: boolean; shift?: boolean; alt?: boolean } = {}): string {
  const name = keyName(key);
  // A modifier pressed on its own is not a chord.
  if (name === "Control" || name === "Shift" || name === "Alt" || name === "Meta") return "";
  // A printable symbol already *is* the shifted key: the browser reports "+"
  // for Shift+=, so counting Shift again would produce "Shift++" and never
  // match the "+" the player bound. Letters and digits keep their modifier,
  // because Shift+H and H are genuinely different chords.
  const symbol = name.length === 1 && !/[A-Z0-9]/.test(name);
  let out = "";
  if (mods.ctrl) out += "Ctrl+";
  if (mods.shift && !symbol) out += "Shift+";
  if (mods.alt) out += "Alt+";
  return out + name;
}

/** Human-readable chord for the settings list and the controls reference. */
export function chordLabel(chord: string): string {
  if (!chord) return "—";
  return chord
    .replace("Delete", "Del")
    .replace("ArrowUp", "↑").replace("ArrowDown", "↓")
    .replace("ArrowLeft", "←").replace("ArrowRight", "→");
}

/** The chord currently bound to an action. */
export function chordFor(binds: Keybinds, id: ActionId): string {
  const bound = binds[id];
  if (bound !== undefined) return bound;
  return ACTION_BY_ID.get(id)?.def ?? "";
}

/**
 * Which action a chord fires, or null. Built fresh only when the bindings
 * change — resolving is on the keyboard path and runs for every keypress.
 */
export class KeybindResolver {
  private map = new Map<string, ActionId>();
  private signature = "";

  /** Rebuild if the bindings changed since last time, then resolve. */
  resolve(binds: Keybinds, chord: string): ActionId | null {
    const sig = JSON.stringify(binds);
    if (sig !== this.signature) { this.rebuild(binds); this.signature = sig; }
    return this.map.get(chord) ?? null;
  }

  private rebuild(binds: Keybinds) {
    this.map.clear();
    for (const a of ACTIONS) {
      const chord = chordFor(binds, a.id);
      // First writer wins, so a conflicting rebind can never shadow silently:
      // conflicts are surfaced in the settings screen instead.
      if (chord && !this.map.has(chord)) this.map.set(chord, a.id);
      if (a.alt && !this.map.has(a.alt)) this.map.set(a.alt, a.id);
    }
  }
}

/**
 * Actions bound to the same chord. Two commands on one key is legal — the pan
 * keys deliberately share W/A/S/D with Stop and Attack-move, because panning is
 * held rather than tapped — so this only reports clashes the player made.
 */
export function conflictsOf(binds: Keybinds): Map<string, ActionId[]> {
  const byChord = new Map<string, ActionId[]>();
  for (const a of ACTIONS) {
    if (a.group === "Camera" && a.id.startsWith("pan")) continue; // held, not tapped
    const chord = chordFor(binds, a.id);
    if (!chord) continue;
    const list = byChord.get(chord) ?? [];
    list.push(a.id);
    byChord.set(chord, list);
  }
  for (const [chord, list] of [...byChord]) if (list.length < 2) byChord.delete(chord);
  return byChord;
}

/** Chords the game must keep for itself, whatever the player types. */
const RESERVED = new Set(["F5", "F11", "F12", "Ctrl+R", "Ctrl+W", "Ctrl+T", "Ctrl+N"]);
export function isReserved(chord: string): boolean { return RESERVED.has(chord); }
