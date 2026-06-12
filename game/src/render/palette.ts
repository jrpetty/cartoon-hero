// The painterly fantasy palette. Every color in the game routes through here
// so the whole scene stays tonally coherent.

export const PAL = {
  // Terrain
  grass: "#6da944",
  grassDark: "#5c9639",
  grassShade: "#4f8a31",
  dirt: "#a98a5c",
  dirtDark: "#94774d",
  sand: "#d8c188",
  waterDeep: "#2e6e8f",
  water: "#3d85a8",
  waterEdge: "#7db9cc",

  // Nature
  trunk: "#6d4a2f",
  foliage1: "#2f6b2a",
  foliage2: "#3d7d33",
  foliage3: "#4f9140",
  berryBush: "#3a703a",
  berry: "#c43b4e",
  goldRock: "#8a8378",
  goldVein: "#e8b13c",

  // Structures
  wood: "#8a6238",
  woodDark: "#6e4c2a",
  woodLight: "#a87f4c",
  stone: "#9b958a",
  stoneDark: "#7d776c",
  stoneLight: "#b5afa3",
  thatch: "#c2a45c",
  thatchDark: "#a3873f",
  roofSlate: "#647183",

  // Units
  skin: "#e8b88a",
  leather: "#8a6844",
  steel: "#c7cdd4",
  steelDark: "#8f99a6",

  // Teams
  teams: [
    { main: "#3a6fd8", light: "#7da3ec", dark: "#274a90", name: "Azure" },
    { main: "#d8403a", light: "#ec837d", dark: "#902a27", name: "Crimson" },
    { main: "#3aa84e", light: "#79d08a", dark: "#246e34", name: "Verdant" },
    { main: "#d8a83a", light: "#eccd7d", dark: "#90702a", name: "Amber" },
  ],

  // UI
  uiParchment: "#e9dcc0",
  uiParchmentDark: "#d4c3a0",
  uiInk: "#3a2f22",
  uiAccent: "#b8862d",
  uiPanel: "rgba(26, 20, 12, 0.92)",
  uiPanelLight: "rgba(48, 38, 24, 0.95)",
  uiGood: "#5bd66b",
  uiBad: "#e0564a",

  // FX
  blood: "#a8352f",
  dust: "#c9b48d",
  smoke: "#5a554d",
  fire: "#f2933a",
  fireBright: "#ffd166",
  heal: "#7df2a9",
};

export function teamColor(team: number) {
  return PAL.teams[team] ?? PAL.teams[0];
}

/** Quick alpha helper for hex colors. */
export function withAlpha(hex: string, a: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${a})`;
}

/** Darken/lighten a hex color by a factor (-1..1). */
export function shade(hex: string, f: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  const adj = (c: number) => {
    const v = f > 0 ? c + (255 - c) * f : c * (1 + f);
    return Math.max(0, Math.min(255, Math.round(v)));
  };
  return `rgb(${adj(r)},${adj(g)},${adj(b)})`;
}
