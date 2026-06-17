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
    { main: "#8a5cf0", light: "#b69bf6", dark: "#5a37a8", name: "Violet" },
    { main: "#2fb3c9", light: "#74dcec", dark: "#1c7785", name: "Teal" },
    { main: "#e0822f", light: "#f2b272", dark: "#96521c", name: "Coral" },
    { main: "#f06a9a", light: "#f7a3c0", dark: "#a83f68", name: "Rose" },
    // Second eight, for matches up to 8v8.
    { main: "#9ad02f", light: "#c3e879", dark: "#658a1c", name: "Lime" },
    { main: "#7a6048", light: "#a98c6c", dark: "#4e3c2c", name: "Umber" },
    { main: "#cf5fd8", light: "#e29bec", dark: "#883a90", name: "Magenta" },
    { main: "#3ad8b0", light: "#7decce", dark: "#249077", name: "Jade" },
    { main: "#5c78f0", light: "#9bacf6", dark: "#374aa8", name: "Indigo" },
    { main: "#d8d23a", light: "#ece97d", dark: "#90892a", name: "Gold" },
    { main: "#d83a6f", light: "#ec7da3", dark: "#90274a", name: "Ruby" },
    { main: "#9aa8b4", light: "#c6d0d8", dark: "#646e78", name: "Steel" },
  ],

  // Diplomacy colours — used in team games to recolour units relative to the
  // viewer: you (blue), allies (teal), enemies (warm reds).
  diplomacy: {
    self: { main: "#3a6fd8", light: "#7da3ec", dark: "#274a90", name: "You" },
    ally: { main: "#2fb39a", light: "#74e3cd", dark: "#1c7c6a", name: "Ally" },
    enemies: [
      { main: "#d8403a", light: "#ec837d", dark: "#902a27", name: "Enemy" },
      { main: "#e0822f", light: "#f2b272", dark: "#96521c", name: "Enemy" },
      { main: "#c64fb0", light: "#e58fd4", dark: "#85317a", name: "Enemy" },
      { main: "#7cbf3a", light: "#aee072", dark: "#527f22", name: "Enemy" },
      { main: "#8a5cf0", light: "#b69bf6", dark: "#5a37a8", name: "Enemy" },
      { main: "#e6c12f", light: "#f4dd7e", dark: "#9a7f17", name: "Enemy" },
      { main: "#f06a9a", light: "#f7a3c0", dark: "#a83f68", name: "Enemy" },
    ],
  },

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

/** Parse "#rrggbb" or "rgb(r,g,b)" into [r,g,b]. */
function parseRGB(col: string): [number, number, number] {
  if (col[0] === "#") {
    return [parseInt(col.slice(1, 3), 16), parseInt(col.slice(3, 5), 16), parseInt(col.slice(5, 7), 16)];
  }
  const m = col.match(/(\d+(?:\.\d+)?)/g);
  return m ? [+m[0], +m[1], +m[2]] : [128, 128, 128];
}

/** Darken/lighten a hex or rgb() color by a factor (-1..1). */
export function shade(hex: string, f: number): string {
  const [r, g, b] = parseRGB(hex);
  const adj = (c: number) => {
    const v = f > 0 ? c + (255 - c) * f : c * (1 + f);
    return Math.max(0, Math.min(255, Math.round(v)));
  };
  return `rgb(${adj(r)},${adj(g)},${adj(b)})`;
}
