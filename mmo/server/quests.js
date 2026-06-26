// Data-driven starter quest chain. Quests are pure data; progress lives on the
// player. Add content by appending objects here — no engine changes needed.
//
// type:   what action advances the quest
//   'forage' -> chopping logs/leaves
//   'mine'   -> breaking stone/ore
//   'kill'   -> defeating mobs
//   'place'  -> placing blocks
// reward: { skill, xp, items? }  applied on completion

export const QUESTS = [
  {
    id: 'first-cut',
    title: 'First Cut',
    desc: 'Chop 5 logs from the trees around spawn.',
    type: 'forage',
    target: 5,
    reward: { skill: 'foraging', xp: 60, items: { planks: 8 } },
  },
  {
    id: 'pick-of-the-litter',
    title: 'Pick of the Litter',
    desc: 'Mine 10 stone blocks.',
    type: 'mine',
    target: 10,
    reward: { skill: 'mining', xp: 90, items: { cobblestone: 16 } },
  },
  {
    id: 'pest-control',
    title: 'Pest Control',
    desc: 'Defeat 3 slimes.',
    type: 'kill',
    target: 3,
    reward: { skill: 'combat', xp: 120, items: {} },
  },
  {
    id: 'homestead',
    title: 'Homestead',
    desc: 'Place 20 blocks to start building.',
    type: 'place',
    target: 20,
    reward: { skill: 'building', xp: 100, items: { glass: 8 } },
  },
];

// Advance the active quest for a player when an action of `type` happens.
// Returns a completion result if the quest just finished, else null.
export function advanceQuest(player, type, amount = 1) {
  const q = QUESTS[player.questIndex];
  if (!q) return null; // chain complete
  if (q.type !== type) return null;
  player.questProgress += amount;
  if (player.questProgress >= q.target) {
    const completed = q;
    player.questIndex += 1;
    player.questProgress = 0;
    return completed;
  }
  return null;
}

export function currentQuest(player) {
  const q = QUESTS[player.questIndex];
  if (!q) return null;
  return { ...q, progress: player.questProgress };
}
