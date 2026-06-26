// Headless smoke test: boots the server on a throwaway port, connects a real
// WebSocket client, drives the join handshake, and asserts the core frames and
// gameplay loops (chunk streaming, block edits, stats) actually work.
// Run with: npm run smoke

import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import WebSocket from 'ws';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = 8099;
let failures = 0;
function ok(cond, msg) {
  console.log(`${cond ? '  ok ' : 'FAIL '}- ${msg}`);
  if (!cond) failures++;
}

const env = { ...process.env, PORT: String(PORT), SEED: '4242' };
const srv = spawn(process.execPath, [path.join(__dirname, 'index.js')], { env, stdio: 'ignore' });

function wait(ms) { return new Promise(r => setTimeout(r, ms)); }

async function run() {
  await wait(700); // let server bind

  const ws = new WebSocket(`ws://localhost:${PORT}`);
  const got = { chunks: 0, welcome: null, you: null, state: null, block: null };

  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('timeout waiting for frames')), 6000);
    ws.on('open', () => ws.send(JSON.stringify({ t: 'join', name: 'Smoke' })));
    ws.on('message', (raw) => {
      const m = JSON.parse(raw);
      if (m.t === 'welcome') got.welcome = m;
      if (m.t === 'chunk') got.chunks++;
      if (m.t === 'you') got.you = m;
      if (m.t === 'state') got.state = m;
      if (m.t === 'block') got.block = m;
      // once we have welcome+chunks+you+a state, try a block break near spawn
      if (got.welcome && got.chunks > 0 && got.you && got.state && !ws._didBreak) {
        ws._didBreak = true;
        const { x, y, z } = got.welcome.you;
        // break the block under spawn (feet - 1)
        ws.send(JSON.stringify({ t: 'break', x: Math.floor(x), y: Math.floor(y) - 1, z: Math.floor(z) }));
        setTimeout(() => { clearTimeout(timeout); resolve(); }, 600);
      }
    });
    ws.on('error', reject);
  });

  ok(got.welcome && typeof got.welcome.seed === 'number', 'received WELCOME with seed');
  ok(got.welcome && got.welcome.blocks && got.welcome.blocks[3], 'WELCOME includes block registry');
  ok(got.chunks >= 9, `streamed chunks on join (got ${got.chunks})`);
  ok(got.you && got.you.stats && got.you.stats.skills.mining, 'received YOU stats with skills');
  ok(got.state && Array.isArray(got.state.players), 'received STATE snapshot');
  ok(got.block && got.block.b === 0, 'block break echoed as BLOCK(air)');

  ws.close();
}

run()
  .catch((e) => { console.error('smoke error:', e.message); failures++; })
  .finally(() => {
    srv.kill('SIGKILL');
    console.log(failures ? `\n${failures} check(s) failed` : '\nAll smoke checks passed');
    process.exit(failures ? 1 : 0);
  });
