// Voxelia client entry point. Sets up the Three.js scene, wires the network,
// runs the render/update loop, and routes input to the player & server.

import * as THREE from 'three';
import { Net } from './network.js';
import { VoxelWorld } from './world.js';
import { Player } from './player.js';
import { Entities } from './entities.js';
import { UI } from './ui.js';

const net = new Net();
let world, player, entities, ui, blocks;
let camera, scene, renderer, raycaster;
let started = false;
let lastMoveSent = 0;
let prevTime = performance.now();
const clock = { time: 0 };
let highlight; // wireframe box over targeted block

// ----------------------------------------------------------- scene bootstrap
function initScene() {
  scene = new THREE.Scene();
  scene.background = new THREE.Color(0x9fd3ff);
  scene.fog = new THREE.Fog(0x9fd3ff, 40, 90);

  camera = new THREE.PerspectiveCamera(75, innerWidth / innerHeight, 0.1, 1000);

  renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(innerWidth, innerHeight);
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.domElement.id = 'game';
  document.body.appendChild(renderer.domElement);

  scene.add(new THREE.HemisphereLight(0xbfe3ff, 0x4a5a3a, 0.9));
  const sun = new THREE.DirectionalLight(0xfff2d0, 0.8);
  sun.position.set(40, 80, 20);
  scene.add(sun);

  // block highlight wireframe
  highlight = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(1.002, 1.002, 1.002)),
    new THREE.LineBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.4 }),
  );
  highlight.visible = false;
  scene.add(highlight);

  raycaster = new THREE.Raycaster();
  raycaster.far = 6;

  addEventListener('resize', () => {
    camera.aspect = innerWidth / innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(innerWidth, innerHeight);
  });
}

// ----------------------------------------------------------- networking
net.on('open', () => setConn('connected', 'ok'));
net.on('close', () => setConn('disconnected — reload to retry', 'err'));
net.on('error', () => setConn('connection error', 'err'));

net.on('welcome', (m) => {
  blocks = m.blocks;
  world = new VoxelWorld(scene, blocks, m.world.chunkSize, m.world.height);
  player = new Player(camera, world);
  player.setSpawn(m.you.x, m.you.y, m.you.z);
  entities = new Entities(scene);
  entities.selfId = m.id;
  ui = new UI(blocks);
  ui.setName(m.name);
});

net.on('chunk', (m) => { if (world) world.setChunk(m.cx, m.cz, m.data); });
net.on('block', (m) => { if (world) world.setBlock(m.x, m.y, m.z, m.b); });
net.on('you', (m) => { if (ui) ui.updateStats(m.stats); });
net.on('chat', (m) => { if (ui) ui.addChat(m); });
net.on('leaders', (m) => { if (ui) ui.setLeaders(m.board); });
net.on('toast', (m) => { if (ui) ui.toast(m.msg); });
net.on('state', (m) => {
  if (!entities) return;
  entities.syncPlayers(m.players);
  entities.syncMobs(m.mobs);
  ui.setOnline(m.players.length);
  lastState = m;
});
let lastState = { players: [], mobs: [] };

function setConn(text, cls) {
  const el = document.getElementById('connStatus');
  if (el) { el.textContent = text; el.className = 'conn ' + (cls || ''); }
}

// ----------------------------------------------------------- input
function setupInput() {
  const canvas = renderer.domElement;

  canvas.addEventListener('click', () => {
    if (!chatOpen()) canvas.requestPointerLock();
  });

  addEventListener('mousemove', (e) => {
    if (document.pointerLockElement === canvas) player?.look(e.movementX, e.movementY);
  });

  // break / attack (left) and place (right)
  canvas.addEventListener('mousedown', (e) => {
    if (document.pointerLockElement !== canvas || !player) return;
    if (e.button === 0) leftClick();
    else if (e.button === 2) rightClick();
  });
  canvas.addEventListener('contextmenu', (e) => e.preventDefault());

  addEventListener('keydown', (e) => {
    if (chatOpen()) {
      if (e.code === 'Enter') sendChat();
      else if (e.code === 'Escape') closeChat();
      return;
    }
    if (e.code === 'Enter') { openChat(); e.preventDefault(); return; }
    if (player) player.keys[e.code] = true;
    if (e.code >= 'Digit1' && e.code <= 'Digit9') {
      const slot = Number(e.code.slice(5)) - 1;
      net.hotbar(slot);
      if (ui?.lastStats) { ui.lastStats.hotbarSlot = slot; ui.updateStats(ui.lastStats); }
    }
  });
  addEventListener('keyup', (e) => { if (player) player.keys[e.code] = false; });

  // scroll hotbar
  addEventListener('wheel', (e) => {
    if (!ui?.lastStats || chatOpen()) return;
    const n = ui.lastStats.hotbar.length;
    let slot = (ui.lastStats.hotbarSlot + (e.deltaY > 0 ? 1 : -1) + n) % n;
    net.hotbar(slot);
    ui.lastStats.hotbarSlot = slot; ui.updateStats(ui.lastStats);
  }, { passive: true });
}

// targeting: returns { block, mobId } based on current view
function target() {
  const eye = player.eye(), dir = player.forward();
  const block = world.raycast(eye, dir, 6);
  // mob raycast
  raycaster.set(eye, dir);
  const hits = raycaster.intersectObjects(entities.mobMeshes(), true);
  let mobId = null, mobDist = Infinity;
  if (hits.length) {
    let obj = hits[0].object;
    while (obj && entities.mobIdForMesh(obj) === null) obj = obj.parent;
    if (obj) { mobId = entities.mobIdForMesh(obj); mobDist = hits[0].distance; }
  }
  const blockDist = block ? eye.distanceTo(new THREE.Vector3(block.x + 0.5, block.y + 0.5, block.z + 0.5)) : Infinity;
  return { block, blockDist, mobId, mobDist };
}

function leftClick() {
  const t = target();
  if (t.mobId !== null && t.mobDist <= t.blockDist) { net.attack(t.mobId); return; }
  if (t.block) net.break(t.block.x, t.block.y, t.block.z);
}

function rightClick() {
  const t = target();
  if (!t.block) return;
  const x = t.block.x + t.block.nx, y = t.block.y + t.block.ny, z = t.block.z + t.block.nz;
  const b = ui.selectedBlock();
  if (b != null) net.place(x, y, z, b);
}

// ----------------------------------------------------------- chat helpers
function chatOpen() { return ui && !ui.el.chatInput.classList.contains('hidden'); }
function openChat() {
  if (!ui) return;
  document.exitPointerLock();
  ui.el.chatInput.classList.remove('hidden');
  ui.el.chatInput.focus();
  if (player) player.keys = {};
}
function closeChat() {
  ui.el.chatInput.value = '';
  ui.el.chatInput.classList.add('hidden');
}
function sendChat() {
  const v = ui.el.chatInput.value.trim();
  if (v) net.chat(v);
  closeChat();
}

// ----------------------------------------------------------- main loop
function loop() {
  requestAnimationFrame(loop);
  const now = performance.now();
  const dt = (now - prevTime) / 1000;
  prevTime = now;
  clock.time += dt;

  if (player && world) {
    player.update(dt);
    world.update(3);
    entities.update(dt, clock.time);

    // block highlight
    const t = target();
    if (t.block && (t.mobId === null || t.blockDist < t.mobDist)) {
      highlight.visible = true;
      highlight.position.set(t.block.x + 0.5, t.block.y + 0.5, t.block.z + 0.5);
    } else highlight.visible = false;

    // throttle movement updates to ~20/s
    if (now - lastMoveSent > 50) {
      net.move(player.pos.x, player.pos.y, player.pos.z, player.yaw, player.pitch);
      lastMoveSent = now;
    }

    // minimap
    ui.drawMinimap(player.pos, player.yaw, lastState.players.filter(p => p.id !== entities.selfId), lastState.mobs);

    renderer.render(scene, camera);
  }
}

// ----------------------------------------------------------- start
function start() {
  if (started) return;
  const name = document.getElementById('nameInput').value.trim() || 'Adventurer';
  net.join(name);
  document.getElementById('login').classList.add('hidden');
  document.getElementById('hud').classList.remove('hidden');
  renderer.domElement.requestPointerLock();
  started = true;
}

initScene();
setupInput();
net.connect();
loop();

document.getElementById('playBtn').addEventListener('click', start);
document.getElementById('nameInput').addEventListener('keydown', (e) => { if (e.code === 'Enter') start(); });
