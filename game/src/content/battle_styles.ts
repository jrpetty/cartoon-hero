// Battle styles — *how* a unit opens a fight, as opposed to how hard it hits.
//
// In the arena a unit doesn't just walk forward. A Vanguard plants itself on
// the line and holds. Line troops push in. Flankers sweep along the edge and
// come in from the side. Artillery never advances at all — it stands and
// shoots. And Infiltrators simply aren't there when the horns blow: they open
// the fight behind the enemy, on top of whatever the enemy was protecting.
//
// This is what makes placement a real decision. Artillery parked at the back is
// safe from the front line and helpless against an Infiltrator; a Vanguard on
// the wrong flank can't answer a sweep. Data only — sim/autobattle.ts turns
// these into opening orders.

export type BattleStyle = "vanguard" | "line" | "flanker" | "artillery" | "infiltrator";

export interface BattleStyleDef {
  id: BattleStyle;
  name: string;
  /** One line, in the language of a commander watching it happen. */
  desc: string;
  color: string;
}

export const BATTLE_STYLES: Record<BattleStyle, BattleStyleDef> = {
  vanguard: {
    id: "vanguard", name: "Vanguard", color: "#c9a24a",
    desc: "Plants on the centre line and holds it. Your wall — nothing gets past without going through.",
  },
  line: {
    id: "line", name: "Line", color: "#9aa8b4",
    desc: "Marches straight across and pushes into the enemy formation.",
  },
  flanker: {
    id: "flanker", name: "Flanker", color: "#e0822f",
    desc: "Sweeps along the edge of the field and comes in from the side, past the enemy front.",
  },
  artillery: {
    id: "artillery", name: "Artillery", color: "#d8403a",
    desc: "Never advances. Stands where you put it and shoots — so where you put it is the whole decision.",
  },
  infiltrator: {
    id: "infiltrator", name: "Infiltrator", color: "#cf5fd8",
    desc: "Opens the fight behind the enemy line, straight onto their archers and engines.",
  },
};

/**
 * Style per unit type. Light, fast units infiltrate; heavy foot holds the line;
 * cavalry sweeps; siege and heavy shot stand still.
 */
export const UNIT_STYLE: Record<string, BattleStyle> = {
  // The wall.
  militia: "vanguard",
  shieldbearer: "vanguard",
  spearman: "vanguard",
  pikeman: "vanguard",
  twohand: "vanguard",
  ram: "vanguard",
  // Straight in.
  archer: "line",
  javelin: "line",
  monk: "line",
  hero: "line",
  berserker: "line",
  // Around the side.
  horseman: "flanker",
  knight: "flanker",
  skirmisher: "flanker",
  cataphract: "flanker",
  // Stand and shoot.
  crossbow: "artillery",
  handcannon: "artillery",
  catapult: "artillery",
  trebuchet: "artillery",
  longbow: "artillery",
  battlemage: "artillery",
  bombard: "artillery",
  // Behind their line.
  scout: "infiltrator",
  raider: "infiltrator",
};

export function styleOf(type: string): BattleStyle { return UNIT_STYLE[type] ?? "line"; }
export function styleDef(type: string): BattleStyleDef { return BATTLE_STYLES[styleOf(type)]; }
