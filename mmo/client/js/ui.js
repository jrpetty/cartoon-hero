// HUD rendering: vitals, skills, hotbar, quest tracker, chat, leaderboard,
// radar minimap, and toasts. Pure DOM/canvas; fed by server frames from main.js.

import { ITEM_TO_BLOCK } from '/shared/blocks.js';

export class UI {
  constructor(blocks) {
    this.blocks = blocks;
    this.el = {
      healthFill: document.getElementById('healthFill'),
      healthText: document.getElementById('healthText'),
      charLevel: document.getElementById('charLevel'),
      charName: document.getElementById('charName'),
      skills: document.getElementById('skills'),
      hotbar: document.getElementById('hotbar'),
      questName: document.getElementById('questName'),
      questDesc: document.getElementById('questDesc'),
      questFill: document.getElementById('questFill'),
      questText: document.getElementById('questText'),
      chatLog: document.getElementById('chatLog'),
      chatInput: document.getElementById('chatInput'),
      online: document.getElementById('onlineCount'),
      leaderList: document.getElementById('leaderList'),
      toast: document.getElementById('toast'),
      minimap: document.getElementById('minimap'),
    };
    this.mini = this.el.minimap.getContext('2d');
    this.toastTimer = null;
    this.lastStats = null;
  }

  hex(id) {
    const c = (this.blocks[id] && this.blocks[id].color) || 0x888888;
    return '#' + c.toString(16).padStart(6, '0');
  }

  itemNameForBlock(id) {
    for (const [name, b] of Object.entries(ITEM_TO_BLOCK)) if (b === id) return name;
    return (this.blocks[id] && this.blocks[id].drop) || 'item';
  }

  setName(name) { this.el.charName.textContent = name; }

  updateStats(s) {
    this.lastStats = s;
    // health
    const hpPct = Math.max(0, (s.hp / s.maxHp) * 100);
    this.el.healthFill.style.width = hpPct + '%';
    this.el.healthText.textContent = `${s.hp} / ${s.maxHp}`;
    this.el.charLevel.textContent = s.level;

    // skills
    this.el.skills.innerHTML = '';
    for (const [name, sk] of Object.entries(s.skills)) {
      const need = Math.round(50 * Math.pow(sk.level + 1, 1.5));
      const cur = Math.round(50 * Math.pow(sk.level, 1.5));
      const pct = sk.level >= 50 ? 100 : Math.max(0, Math.min(100, ((sk.xp - cur) / (need - cur)) * 100));
      const div = document.createElement('div');
      div.className = 'skill';
      div.innerHTML =
        `<div class="row"><span class="name">${name}</span><span class="lvl">${sk.level}</span></div>` +
        `<div class="xpbar"><div style="width:${pct}%"></div></div>`;
      this.el.skills.appendChild(div);
    }

    // hotbar
    this.el.hotbar.innerHTML = '';
    s.hotbar.forEach((blockId, i) => {
      const item = this.itemNameForBlock(blockId);
      const count = s.inventory[item] || 0;
      const slot = document.createElement('div');
      slot.className = 'slot' + (i === s.hotbarSlot ? ' active' : '');
      slot.innerHTML =
        `<span class="key">${i + 1}</span>` +
        `<div class="swatch" style="background:${this.hex(blockId)}"></div>` +
        `<span class="count">${count || ''}</span>`;
      this.el.hotbar.appendChild(slot);
    });

    // quest
    if (s.quest) {
      this.el.questName.textContent = s.quest.title;
      this.el.questDesc.textContent = s.quest.desc;
      const pct = (s.quest.progress / s.quest.target) * 100;
      this.el.questFill.style.width = pct + '%';
      this.el.questText.textContent = `${s.quest.progress} / ${s.quest.target}`;
    } else {
      this.el.questName.textContent = 'All quests complete!';
      this.el.questDesc.textContent = `${s.questsDone}/${s.questsTotal} done. More coming soon.`;
      this.el.questFill.style.width = '100%';
      this.el.questText.textContent = '✓';
    }
  }

  selectedBlock() {
    if (!this.lastStats) return null;
    return this.lastStats.hotbar[this.lastStats.hotbarSlot];
  }

  addChat({ from, msg, kind }) {
    const line = document.createElement('div');
    line.className = 'line' + (kind === 'system' ? ' system' : '');
    if (kind === 'system') line.textContent = msg;
    else line.innerHTML = `<span class="who">${from}:</span> ${this.escape(msg)}`;
    this.el.chatLog.appendChild(line);
    while (this.el.chatLog.children.length > 40) this.el.chatLog.removeChild(this.el.chatLog.firstChild);
    this.el.chatLog.scrollTop = this.el.chatLog.scrollHeight;
  }

  escape(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

  setOnline(n) { this.el.online.textContent = n; }

  setLeaders(board) {
    this.el.leaderList.innerHTML = '';
    board.forEach((p, i) => {
      const li = document.createElement('li');
      li.innerHTML = `<span>${i + 1}. ${this.escape(p.name)}</span><span>${p.combat}</span>`;
      this.el.leaderList.appendChild(li);
    });
  }

  toast(msg) {
    this.el.toast.textContent = msg;
    this.el.toast.classList.add('show');
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.el.toast.classList.remove('show'), 2200);
  }

  // radar: self at center, other players green, mobs red, oriented to yaw
  drawMinimap(self, yaw, players, mobs) {
    const ctx = this.mini, R = 70, scale = 4;
    ctx.clearRect(0, 0, 140, 140);
    ctx.fillStyle = 'rgba(10,14,20,0.6)';
    ctx.beginPath(); ctx.arc(R, R, R, 0, Math.PI * 2); ctx.fill();
    // ring
    ctx.strokeStyle = 'rgba(255,255,255,0.15)';
    ctx.beginPath(); ctx.arc(R, R, R - 1, 0, Math.PI * 2); ctx.stroke();

    const plot = (wx, wz, color, size) => {
      let dx = wx - self.x, dz = wz - self.z;
      // rotate so player's facing is up
      const s = Math.sin(yaw), c = Math.cos(yaw);
      const rx = dx * c - dz * s;
      const rz = dx * s + dz * c;
      const px = R + rx * scale, py = R + rz * scale;
      if (Math.hypot(px - R, py - R) > R - 3) return;
      ctx.fillStyle = color;
      ctx.beginPath(); ctx.arc(px, py, size, 0, Math.PI * 2); ctx.fill();
    };

    for (const m of mobs) plot(m.x, m.z, '#e9573f', 2.5);
    for (const p of players) plot(p.x, p.z, '#7cb342', 3);

    // self
    ctx.fillStyle = '#ffce54';
    ctx.beginPath(); ctx.moveTo(R, R - 6); ctx.lineTo(R - 4, R + 4); ctx.lineTo(R + 4, R + 4); ctx.closePath(); ctx.fill();
    // N marker
    ctx.fillStyle = '#fff'; ctx.font = '10px sans-serif'; ctx.textAlign = 'center';
    ctx.fillText('N', R, 12);
  }
}
