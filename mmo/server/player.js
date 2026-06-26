// Player state + the skill/XP progression economy (SkyBlock-style: every action
// grants XP in a discipline; levels gate content and scale stats).

import { PLACEABLE } from '../shared/blocks.js';

export const SKILLS = ['mining', 'foraging', 'combat', 'building'];
export const MAX_LEVEL = 50;

// Classic exponential curve. Total XP to *reach* level n from level 1.
export function xpForLevel(n) {
  return Math.round(50 * Math.pow(n, 1.5));
}

export function levelFromXp(xp) {
  let lvl = 1;
  while (lvl < MAX_LEVEL && xp >= xpForLevel(lvl + 1)) lvl++;
  return lvl;
}

let nextId = 1;

export function createPlayer(name) {
  const skills = {};
  for (const s of SKILLS) skills[s] = { xp: 0, level: 1 };
  return {
    id: nextId++,
    name: (name || 'Adventurer').slice(0, 16),
    x: 0, y: 40, z: 0, yaw: 0, pitch: 0,
    hp: 20, maxHp: 20,
    skills,
    inventory: {},            // itemName -> count
    hotbar: PLACEABLE.slice(0, 9),
    hotbarSlot: 0,
    questIndex: 0,
    questProgress: 0,
    sentChunks: new Set(),    // "cx,cz" chunks already streamed/scheduled
    chunkQueue: [],           // "cx,cz" chunks waiting to be sent (paced)
    lastSeen: Date.now(),
    dirty: true,              // stats changed -> resend to client
  };
}

// Award XP to a skill; recompute level & derived stats. Returns level-ups.
export function grantXp(player, skill, amount) {
  const s = player.skills[skill];
  if (!s) return 0;
  const before = s.level;
  s.xp += amount;
  s.level = levelFromXp(s.xp);
  if (skill === 'combat') {
    player.maxHp = 20 + (s.level - 1) * 2;   // combat scales survivability
    if (player.hp > player.maxHp) player.hp = player.maxHp;
  }
  player.dirty = true;
  return s.level - before;
}

// Headline character level = average of skill levels (rounded), min 1.
export function characterLevel(player) {
  const sum = SKILLS.reduce((a, s) => a + player.skills[s].level, 0);
  return Math.max(1, Math.round(sum / SKILLS.length));
}

export function giveItem(player, item, count = 1) {
  if (!item) return;
  player.inventory[item] = (player.inventory[item] || 0) + count;
  player.dirty = true;
}

export function takeItem(player, item, count = 1) {
  if ((player.inventory[item] || 0) < count) return false;
  player.inventory[item] -= count;
  if (player.inventory[item] <= 0) delete player.inventory[item];
  player.dirty = true;
  return true;
}

// Compact public view of a player for the STATE snapshot (what others see).
export function publicView(p) {
  return { id: p.id, name: p.name, x: p.x, y: p.y, z: p.z, yaw: p.yaw, hp: p.hp, maxHp: p.maxHp };
}
