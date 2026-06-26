// Shared protocol definitions. Each frame is a JSON object with a `t` (type)
// field. Documented here so client and server stay in lock-step. Swap the
// JSON.stringify/parse pair for a binary codec later without touching callers.

// Server -> Client
export const S = {
  WELCOME: 'welcome',   // { t, id, name, seed, world:{chunkSize,height,seaLevel}, you, blocks }
  CHUNK: 'chunk',       // { t, cx, cz, data:[...blockIds] }  column-major packed
  STATE: 'state',       // { t, players:[...], mobs:[...] }   periodic snapshot
  BLOCK: 'block',       // { t, x, y, z, b }                  authoritative block change
  YOU: 'you',           // { t, stats }                       your stats/skills/inventory/quests
  CHAT: 'chat',         // { t, from, msg, kind }
  LEADERS: 'leaders',   // { t, board:[{name, combat}] }
  TOAST: 'toast',       // { t, msg }                         transient notification
};

// Client -> Server
export const C = {
  JOIN: 'join',         // { t, name }
  MOVE: 'move',         // { t, x, y, z, yaw, pitch }
  BREAK: 'break',       // { t, x, y, z }
  PLACE: 'place',       // { t, x, y, z, b }
  ATTACK: 'attack',     // { t, id }                          mob id
  HOTBAR: 'hotbar',     // { t, slot }
  CHAT: 'chat',         // { t, msg }
  REQCHUNK: 'reqchunk', // { t, cx, cz }
};
