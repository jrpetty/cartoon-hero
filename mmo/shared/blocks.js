// Shared block registry — imported by BOTH the Node server and the browser
// client (served statically at /shared/blocks.js). Pure ESM, no platform APIs,
// so the two sides can never disagree on block ids or properties.

export const AIR = 0;

// id -> definition. `color` is the base face color (client rendering).
// `top`/`side` override per-face when present. `solid` gates collision &
// raycasting. `skill` + `drop` drive progression when a block is broken.
export const BLOCKS = {
  0:  { name: 'air',        solid: false },
  1:  { name: 'grass',      solid: true,  color: 0x6aa84f, top: 0x7cb342, side: 0x8d6e3a, skill: 'mining',   drop: 'dirt' },
  2:  { name: 'dirt',       solid: true,  color: 0x8d6e3a, skill: 'mining',   drop: 'dirt' },
  3:  { name: 'stone',      solid: true,  color: 0x8a8a8a, skill: 'mining',   drop: 'cobblestone' },
  4:  { name: 'sand',       solid: true,  color: 0xe6d9a3, skill: 'mining',   drop: 'sand' },
  5:  { name: 'water',      solid: false, color: 0x3b6fd6, alpha: 0.6 },
  6:  { name: 'log',        solid: true,  color: 0x6b4f2a, top: 0x9c7b4a, skill: 'foraging', drop: 'log' },
  7:  { name: 'leaves',     solid: true,  color: 0x4f8f3a, alpha: 0.92, skill: 'foraging', drop: 'leaves' },
  8:  { name: 'planks',     solid: true,  color: 0xb5894e, skill: 'mining',   drop: 'planks' },
  9:  { name: 'cobblestone',solid: true,  color: 0x6f6f6f, skill: 'mining',   drop: 'cobblestone' },
  10: { name: 'glass',      solid: true,  color: 0xa8d8ff, alpha: 0.4, skill: 'mining', drop: 'glass' },
  11: { name: 'coal_ore',   solid: true,  color: 0x3a3a3a, skill: 'mining',   drop: 'coal' },
  12: { name: 'iron_ore',   solid: true,  color: 0xd8b48c, skill: 'mining',   drop: 'iron' },
  13: { name: 'glowstone',  solid: true,  color: 0xffd86b, light: true,       skill: 'mining', drop: 'glowstone' },
};

export function isSolid(id) {
  const b = BLOCKS[id];
  return !!(b && b.solid);
}

// Blocks a player can put in the hotbar / receive as drops, in display order.
export const PLACEABLE = [3, 2, 4, 8, 9, 6, 7, 10, 13];

// Map a dropped-item name back to a placeable block id (for inventory -> world).
export const ITEM_TO_BLOCK = {
  dirt: 2, cobblestone: 9, sand: 4, planks: 8, log: 6,
  leaves: 7, glass: 10, glowstone: 13, stone: 3,
};
