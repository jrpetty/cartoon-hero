// Voxelia MMO server: static file host + authoritative WebSocket game server +
// 20 tps simulation. Single port; open http://localhost:PORT.

import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { WebSocketServer } from 'ws';

import { World, CHUNK, HEIGHT, SEA } from './world.js';
import { MobManager } from './mobs.js';
import {
  createPlayer, grantXp, characterLevel, giveItem, takeItem, publicView, SKILLS,
} from './player.js';
import { BLOCKS, AIR, isSolid, ITEM_TO_BLOCK } from '../shared/blocks.js';
import { advanceQuest, currentQuest, QUESTS } from './quests.js';
import { S, C } from './protocol.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(__dirname, '..');
const PORT = process.env.PORT || 8080;
const VIEW = 4;            // chunk view radius streamed to clients
const TICK_MS = 50;        // 20 tps
const REACH = 6;           // max block/attack interaction distance

const world = new World(Number(process.env.SEED) || 1337);
const mobs = new MobManager(world, 30);
const clients = new Map(); // ws -> player

// ---------------------------------------------------------------- static host
const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
  '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

const server = http.createServer((req, res) => {
  let urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
  if (urlPath === '/') urlPath = '/client/index.html';
  // allow /shared/* and /client/*; default everything else under /client
  if (!urlPath.startsWith('/client/') && !urlPath.startsWith('/shared/')) {
    urlPath = '/client' + urlPath;
  }
  const filePath = path.normalize(path.join(ROOT, urlPath));
  if (!filePath.startsWith(ROOT)) { res.writeHead(403); return res.end('forbidden'); }
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); return res.end('not found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(filePath)] || 'application/octet-stream' });
    res.end(data);
  });
});

// ---------------------------------------------------------------- websockets
const wss = new WebSocketServer({ server });

function send(ws, obj) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj));
}
function broadcast(obj, except = null) {
  const msg = JSON.stringify(obj);
  for (const ws of clients.keys()) if (ws !== except && ws.readyState === ws.OPEN) ws.send(msg);
}

function youPayload(p) {
  return {
    t: S.YOU,
    stats: {
      hp: p.hp, maxHp: p.maxHp,
      level: characterLevel(p),
      skills: p.skills,
      inventory: p.inventory,
      hotbar: p.hotbar,
      hotbarSlot: p.hotbarSlot,
      quest: currentQuest(p),
      questsDone: p.questIndex,
      questsTotal: QUESTS.length,
    },
  };
}

function streamChunks(ws, p) {
  const pcx = Math.floor(p.x / CHUNK), pcz = Math.floor(p.z / CHUNK);
  for (let dx = -VIEW; dx <= VIEW; dx++) {
    for (let dz = -VIEW; dz <= VIEW; dz++) {
      const cx = pcx + dx, cz = pcz + dz;
      const key = `${cx},${cz}`;
      if (p.sentChunks.has(key)) continue;
      p.sentChunks.add(key);
      send(ws, { t: S.CHUNK, cx, cz, data: world.chunkData(cx, cz) });
    }
  }
}

function withinReach(p, x, y, z) {
  const dx = x + 0.5 - p.x, dy = y + 0.5 - (p.y), dz = z + 0.5 - p.z;
  return dx * dx + dy * dy + dz * dz <= (REACH + 2) * (REACH + 2);
}

wss.on('connection', (ws) => {
  let player = null;

  ws.on('message', (raw) => {
    let m;
    try { m = JSON.parse(raw); } catch { return; }

    // first meaningful message must be JOIN
    if (!player && m.t !== C.JOIN) return;

    switch (m.t) {
      case C.JOIN: {
        if (player) break;
        player = createPlayer(m.name);
        // spawn standing on the surface near origin
        player.x = 0.5; player.z = 0.5; player.y = world.surfaceY(0, 0);
        clients.set(ws, player);
        send(ws, {
          t: S.WELCOME, id: player.id, name: player.name, seed: world.seed,
          world: { chunkSize: CHUNK, height: HEIGHT, seaLevel: SEA },
          you: { x: player.x, y: player.y, z: player.z },
          blocks: BLOCKS,
        });
        streamChunks(ws, player);
        send(ws, youPayload(player));
        broadcast({ t: S.CHAT, from: 'Server', kind: 'system', msg: `${player.name} joined the world` });
        // seed some mobs near spawn
        for (let i = 0; i < 6; i++) mobs.spawnNear(player.x, player.z);
        break;
      }

      case C.MOVE: {
        // trust-but-clamp: accept client position (MVP), basic sanity bounds
        if (Number.isFinite(m.x) && Number.isFinite(m.y) && Number.isFinite(m.z)) {
          player.x = m.x; player.y = m.y; player.z = m.z;
          player.yaw = m.yaw || 0; player.pitch = m.pitch || 0;
          player.lastSeen = Date.now();
          streamChunks(ws, player);
        }
        break;
      }

      case C.BREAK: {
        const { x, y, z } = m;
        if (!withinReach(player, x, y, z)) break;
        const id = world.get(x, y, z);
        const def = BLOCKS[id];
        if (id === AIR || !def || !def.solid) break;
        world.setBlock(x, y, z, AIR);
        broadcast({ t: S.BLOCK, x, y, z, b: AIR });
        // progression
        if (def.skill) grantXp(player, def.skill, 6);
        if (def.drop) giveItem(player, def.drop, 1);
        // quest hooks: stone/ore -> mine, log/leaves -> forage
        if (def.skill === 'mining') finishQuest(ws, player, advanceQuest(player, 'mine'));
        if (def.skill === 'foraging') finishQuest(ws, player, advanceQuest(player, 'forage'));
        send(ws, youPayload(player));
        break;
      }

      case C.PLACE: {
        const { x, y, z } = m;
        if (!withinReach(player, x, y, z)) break;
        if (world.get(x, y, z) !== AIR) break;
        // don't place inside the player
        if (Math.floor(player.x) === x && z === Math.floor(player.z) &&
            (y === Math.floor(player.y) || y === Math.floor(player.y) - 1)) break;
        const item = player.hotbar[player.hotbarSlot];
        const blockId = ITEM_TO_BLOCK[itemNameFor(item)] ?? item;
        const need = itemNameFor(item);
        if (!takeItem(player, need, 1)) { send(ws, { t: S.TOAST, msg: `Out of ${need}` }); break; }
        world.setBlock(x, y, z, blockId);
        broadcast({ t: S.BLOCK, x, y, z, b: blockId });
        grantXp(player, 'building', 3);
        finishQuest(ws, player, advanceQuest(player, 'place'));
        send(ws, youPayload(player));
        break;
      }

      case C.ATTACK: {
        const res = mobs.damage(m.id, 5);
        if (!res) break;
        if (res.dead) {
          grantXp(player, 'combat', 25);
          giveItem(player, 'slimeball', 1);
          finishQuest(ws, player, advanceQuest(player, 'kill'));
          send(ws, { t: S.TOAST, msg: '+25 Combat XP' });
          send(ws, youPayload(player));
          // respawn one elsewhere to keep population up
          mobs.spawnNear(player.x, player.z);
        }
        break;
      }

      case C.HOTBAR: {
        if (m.slot >= 0 && m.slot < player.hotbar.length) {
          player.hotbarSlot = m.slot;
        }
        break;
      }

      case C.CHAT: {
        const text = String(m.msg || '').slice(0, 200).trim();
        if (text) broadcast({ t: S.CHAT, from: player.name, kind: 'player', msg: text });
        break;
      }

      case C.REQCHUNK: {
        send(ws, { t: S.CHUNK, cx: m.cx, cz: m.cz, data: world.chunkData(m.cx, m.cz) });
        player.sentChunks.add(`${m.cx},${m.cz}`);
        break;
      }
    }
  });

  ws.on('close', () => {
    if (player) {
      clients.delete(ws);
      broadcast({ t: S.CHAT, from: 'Server', kind: 'system', msg: `${player.name} left the world` });
    }
  });
});

function itemNameFor(blockId) {
  // hotbar stores block ids; map to the inventory item name that places it
  const entry = Object.entries(ITEM_TO_BLOCK).find(([, id]) => id === blockId);
  return entry ? entry[0] : (BLOCKS[blockId]?.drop || BLOCKS[blockId]?.name || 'cobblestone');
}

function finishQuest(ws, player, completed) {
  if (!completed) return;
  const { reward, title } = completed;
  if (reward.skill && reward.xp) grantXp(player, reward.skill, reward.xp);
  for (const [item, n] of Object.entries(reward.items || {})) giveItem(player, item, n);
  send(ws, { t: S.TOAST, msg: `Quest complete: ${title}` });
  broadcast({ t: S.CHAT, from: 'Server', kind: 'system', msg: `${player.name} completed "${title}"` });
}

// ---------------------------------------------------------------- game loop
let mobAttackCooldown = new Map(); // playerId -> ticks

setInterval(() => {
  const playerList = [...clients.values()];
  mobs.tick(playerList.map(p => ({ x: p.x, y: p.y, z: p.z })));

  // mob melee: if a mob is adjacent to a player, chip its HP on a cooldown
  for (const p of playerList) {
    const cd = mobAttackCooldown.get(p.id) || 0;
    if (cd > 0) { mobAttackCooldown.set(p.id, cd - 1); continue; }
    for (const mob of mobs.mobs.values()) {
      const dx = mob.x - p.x, dz = mob.z - p.z, dy = mob.y - p.y;
      if (dx * dx + dz * dz < 1.6 && Math.abs(dy) < 2) {
        p.hp = Math.max(0, p.hp - 2);
        p.dirty = true;
        mobAttackCooldown.set(p.id, 10); // ~0.5s
        if (p.hp <= 0) respawn(p);
        break;
      }
    }
  }

  // periodic HP regen
  for (const p of playerList) {
    if (p.hp > 0 && p.hp < p.maxHp && Math.random() < 0.05) { p.hp++; p.dirty = true; }
  }

  // broadcast world snapshot
  broadcast({
    t: S.STATE,
    players: playerList.map(publicView),
    mobs: mobs.publicView(),
  });

  // push individual stat updates to anyone whose stats changed
  for (const [ws, p] of clients) {
    if (p.dirty) { send(ws, youPayload(p)); p.dirty = false; }
  }
}, TICK_MS);

function respawn(p) {
  p.x = 0.5; p.z = 0.5; p.y = world.surfaceY(0, 0);
  p.hp = p.maxHp;
  p.dirty = true;
  const ws = [...clients.entries()].find(([, pl]) => pl === p)?.[0];
  if (ws) send(ws, { t: S.TOAST, msg: 'You died — respawned at spawn' });
}

// leaderboard + autosave
setInterval(() => {
  const board = [...clients.values()]
    .map(p => ({ name: p.name, combat: p.skills.combat.level }))
    .sort((a, b) => b.combat - a.combat)
    .slice(0, 10);
  broadcast({ t: S.LEADERS, board });
}, 4000);

setInterval(() => world.save(), 15000);

// keep mob population near active players
setInterval(() => {
  const players = [...clients.values()];
  if (!players.length) return;
  if (mobs.mobs.size < mobs.cap) {
    const p = players[Math.floor(Math.random() * players.length)];
    mobs.spawnNear(p.x, p.z);
  }
}, 2000);

function shutdown() {
  console.log('\n[server] saving and exiting...');
  world.save();
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

server.listen(PORT, () => {
  console.log(`\n  Voxelia MMO running:  http://localhost:${PORT}`);
  console.log(`  Seed ${world.seed} • ${SKILLS.length} skills • ${QUESTS.length} quests`);
  console.log(`  Open a second tab for multiplayer.\n`);
});
