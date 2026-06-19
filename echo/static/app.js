// Echo frontend — talks to the FastAPI brain, handles voice in/out.
const $ = (sel) => document.querySelector(sel);
const api = async (path, opts = {}) => {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...opts,
  });
  if (!res.ok) {
    const detail = await res.json().catch(() => ({}));
    throw new Error(detail.detail || res.statusText);
  }
  return res.headers.get("content-type")?.includes("application/json")
    ? res.json()
    : res.text();
};

// --- tabs ------------------------------------------------------------------
function activateTab(name) {
  document.querySelectorAll(".tabs button").forEach((x) => x.classList.toggle("active", x.dataset.tab === name));
  document.querySelectorAll(".tab").forEach((x) => x.classList.remove("active"));
  $("#tab-" + name).classList.add("active");
  if (name === "today") loadToday();
  if (name === "interview") loadInterview();
  if (name === "decisions") loadDecisions();
  if (name === "memory") loadMemory();
  if (name === "dossier") loadDossier();
}
document.querySelectorAll(".tabs button").forEach((b) => {
  b.onclick = () => activateTab(b.dataset.tab);
});
// Buttons anywhere can jump to a tab via data-goto (used by the Today surface).
document.addEventListener("click", (e) => {
  const el = e.target.closest("[data-goto]");
  if (el) activateTab(el.dataset.goto);
});

// --- voice (premium ElevenLabs/Whisper with browser fallback) --------------
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
let voiceCfg = { premium_tts: false, premium_stt: false };
(async () => {
  try { voiceCfg = await api("/api/voice/config"); } catch (e) {}
})();

function listen(targetEl, micBtn) {
  if (voiceCfg.premium_stt && window.MediaRecorder) return recordPremium(targetEl, micBtn);
  if (!SR) { alert("Voice input needs Chrome/Edge (or set OPENAI_API_KEY for Whisper). Type instead — it all works."); return; }
  const rec = new SR();
  rec.lang = "en-US";
  rec.interimResults = true;
  rec.continuous = false;
  let base = targetEl.value ? targetEl.value + " " : "";
  micBtn.classList.add("listening");
  rec.onresult = (e) => {
    let txt = "";
    for (const r of e.results) txt += r[0].transcript;
    targetEl.value = base + txt;
  };
  rec.onend = () => micBtn.classList.remove("listening");
  rec.onerror = () => micBtn.classList.remove("listening");
  rec.start();
}

// Premium STT: record a clip, send to Whisper, append the transcript.
async function recordPremium(targetEl, micBtn) {
  if (micBtn.classList.contains("listening") && micBtn._recorder) {
    micBtn._recorder.stop();
    return;
  }
  let stream;
  try { stream = await navigator.mediaDevices.getUserMedia({ audio: true }); }
  catch (e) { alert("Couldn't access the mic."); return; }
  const rec = new MediaRecorder(stream);
  const chunks = [];
  micBtn._recorder = rec;
  micBtn.classList.add("listening");
  micBtn.title = "Click to stop";
  rec.ondataavailable = (e) => chunks.push(e.data);
  rec.onstop = async () => {
    stream.getTracks().forEach((t) => t.stop());
    micBtn.classList.remove("listening");
    micBtn._recorder = null;
    micBtn.title = "Speak";
    const blob = new Blob(chunks, { type: "audio/webm" });
    const fd = new FormData();
    fd.append("audio", blob, "speech.webm");
    try {
      const res = await fetch("/api/stt", { method: "POST", body: fd });
      const data = await res.json();
      if (data.text) targetEl.value = (targetEl.value ? targetEl.value + " " : "") + data.text;
    } catch (e) { alert("Transcription failed."); }
  };
  rec.start();
}

let currentAudio = null;
async function speak(text) {
  if (voiceCfg.premium_tts) {
    try {
      const res = await fetch("/api/tts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text }),
      });
      if (res.status === 200) {
        const blob = await res.blob();
        if (currentAudio) currentAudio.pause();
        currentAudio = new Audio(URL.createObjectURL(blob));
        currentAudio.play();
        return;
      }
    } catch (e) { /* fall through to browser voice */ }
  }
  if (!window.speechSynthesis) return;
  const u = new SpeechSynthesisUtterance(text);
  u.rate = 1.02; u.pitch = 1.0;
  window.speechSynthesis.speak(u);
}

// --- status ----------------------------------------------------------------
async function refreshStatus() {
  try {
    const s = await api("/api/state");
    const bits = [
      `${s.memories} memories`,
      `${s.decisions} decisions`,
      s.has_dossier ? "dossier ✓" : "no dossier yet",
    ];
    if (!s.has_key) bits.unshift("⚠ no API key");
    $("#status").textContent = bits.join("  ·  ");
    if (s.auth_enabled) $("#logout-form").classList.remove("hidden");
    if (s.decisions_due?.length) renderDue(s.decisions_due);
  } catch (e) { $("#status").textContent = "offline"; }
}

// --- interview -------------------------------------------------------------
let currentQuestion = null;
async function loadInterview() {
  const data = await api("/api/interview/next");
  const pct = Math.round((data.answered / data.total) * 100);
  $("#interview-bar").style.width = pct + "%";
  if (data.done) {
    $("#interview-progress").textContent = `All ${data.total} answered.`;
    $("#interview-question").classList.add("hidden");
    $("#interview-answer").classList.add("hidden");
    $("#interview-submit").classList.add("hidden");
    $("#interview-mic").classList.add("hidden");
    $("#interview-done").classList.remove("hidden");
    return;
  }
  currentQuestion = data.question;
  $("#interview-progress").textContent = `Question ${data.answered + 1} of ${data.total}`;
  $("#interview-question").textContent = data.question.text;
  $("#interview-answer").value = "";
  $("#interview-answer").focus();
}
$("#interview-submit").onclick = async () => {
  const answer = $("#interview-answer").value.trim();
  if (!answer || !currentQuestion) return;
  $("#interview-submit").disabled = true;
  try {
    await api("/api/interview/answer", {
      method: "POST",
      body: JSON.stringify({ question_id: currentQuestion.id, answer }),
    });
    await loadInterview();
    refreshStatus();
  } catch (e) { alert(e.message); }
  $("#interview-submit").disabled = false;
};
$("#interview-mic").onclick = () => listen($("#interview-answer"), $("#interview-mic"));
$("#synthesize").onclick = async () => {
  $("#synthesize").disabled = true;
  $("#synthesize").textContent = "Thinking deeply about you…";
  try {
    await api("/api/interview/synthesize", { method: "POST" });
    alert("Dossier built. Check the 📜 Dossier tab.");
    refreshStatus();
  } catch (e) { alert(e.message); }
  $("#synthesize").disabled = false;
  $("#synthesize").textContent = "Build my dossier ✨";
};

// --- chat ------------------------------------------------------------------
let mode = "chat";
document.querySelectorAll(".mode-switch button").forEach((b) => {
  b.onclick = () => {
    document.querySelectorAll(".mode-switch button").forEach((x) => x.classList.remove("active"));
    b.classList.add("active");
    mode = b.dataset.mode;
    addBubble("note", {
      chat: "— talking with you —",
      argue: "— I'll argue the other side —",
      asme: "— I'll answer as you —",
      decide: "— tell me the choice; I'll make the call as you —",
    }[mode]);
  };
});
function addBubble(role, text) {
  const div = document.createElement("div");
  div.className = "bubble " + role;
  div.textContent = text;
  $("#chat-log").appendChild(div);
  $("#chat-log").scrollTop = $("#chat-log").scrollHeight;
  return div;
}
async function sendChat() {
  const text = $("#chat-text").value.trim();
  if (!text) return;
  $("#chat-text").value = "";
  addBubble("user", text);
  const thinking = addBubble("echo", "…");
  try {
    const data = await api("/api/chat", {
      method: "POST",
      body: JSON.stringify({ mode, message: text }),
    });
    thinking.textContent = data.reply;
    if ($("#speak-replies").checked) speak(data.reply);
    if (data.learned?.length) addBubble("note", "🧠 learned: " + data.learned.map((m) => m.content).join(" · "));
    if (data.contradictions?.length)
      data.contradictions.forEach((c) => addBubble("note", `🔀 you used to think "${c.old}" — now "${c.new}"`));
    refreshStatus();
  } catch (e) { thinking.textContent = "⚠ " + e.message; }
}
$("#chat-send").onclick = sendChat;
$("#chat-text").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendChat(); }
});
$("#chat-mic").onclick = () => listen($("#chat-text"), $("#chat-mic"));

// --- decisions -------------------------------------------------------------
$("#d-confidence").oninput = (e) => ($("#d-conf-label").textContent = e.target.value);
$("#d-draft").onclick = async () => {
  const title = $("#d-title").value.trim();
  if (!title) { alert("Give the decision a title first."); return; }
  $("#d-draft").disabled = true;
  $("#d-draft").textContent = "Drafting as you…";
  try {
    const d = await api("/api/decisions/draft", {
      method: "POST",
      body: JSON.stringify({ title, context: $("#d-context").value }),
    });
    $("#d-reasoning").value = d.reasoning || "";
    $("#d-prediction").value = d.prediction || "";
    if (d.confidence != null) { $("#d-confidence").value = d.confidence; $("#d-conf-label").textContent = d.confidence; }
  } catch (e) { alert(e.message); }
  $("#d-draft").disabled = false;
  $("#d-draft").textContent = "🧭 Draft this for me";
};
$("#d-save").onclick = async () => {
  const title = $("#d-title").value.trim();
  if (!title) { alert("Give the decision a title."); return; }
  $("#d-save").disabled = true;
  try {
    const data = await api("/api/decisions", {
      method: "POST",
      body: JSON.stringify({
        title,
        context: $("#d-context").value,
        reasoning: $("#d-reasoning").value,
        prediction: $("#d-prediction").value,
        confidence: +$("#d-confidence").value,
        review_in_days: +$("#d-days").value,
      }),
    });
    ["d-title", "d-context", "d-reasoning", "d-prediction"].forEach((id) => ($("#" + id).value = ""));
    if (data.advice) {
      $("#d-advice").textContent = "Echo's take: " + data.advice;
      $("#d-advice").classList.remove("hidden");
    }
    loadDecisions();
    refreshStatus();
  } catch (e) { alert(e.message); }
  $("#d-save").disabled = false;
};
// Inline grading cards for due decisions. `prefix` keeps the input ids unique
// when the same due list is shown on both the Today and Decisions tabs.
function dueItemsHTML(due, prefix = "") {
  return due
    .map(
      (d) => `<div class="decision">
        <b>${esc(d.title)}</b>
        <div class="meta">You predicted: ${esc(d.prediction || "—")} (confidence ${d.confidence}%)</div>
        <textarea id="${prefix}out-${d.id}" rows="2" placeholder="What actually happened?"></textarea>
        <div class="row"><label>How right were you? <input type="number" id="${prefix}grade-${d.id}" min="0" max="100" value="50" style="width:64px"/>%</label>
        <button class="primary" onclick="gradeDecision(${d.id}, '${prefix}')">Save grade</button></div>
      </div>`
    )
    .join("");
}
function renderDue(due) {
  const box = $("#decisions-due");
  if (!box) return;
  box.innerHTML = due.length
    ? `<div class="card due"><h2>⏰ Time to grade yourself</h2>${dueItemsHTML(due)}</div>`
    : "";
}
window.gradeDecision = async (id, prefix = "") => {
  try {
    const r = await api(`/api/decisions/${id}/review`, {
      method: "POST",
      body: JSON.stringify({ outcome: $(`#${prefix}out-${id}`).value, self_grade: +$(`#${prefix}grade-${id}`).value }),
    });
    if (r.calibration && r.calibration !== "graded")
      alert(`Logged. On this one you were ${r.calibration}. Echo will remember that.`);
    refreshStatus();
    loadDecisions();
    loadToday();
  } catch (e) { alert(e.message); }
};
async function loadCalibration() {
  const body = $("#calibration-body");
  let r;
  try { r = await api("/api/calibration"); }
  catch (e) { body.textContent = "—"; return; }
  if (!r.graded) {
    body.innerHTML = `Grade a few decisions and your scorecard shows up here — <b>this is the whole point</b>. It tells you whether your confidence actually tracks reality.`;
    return;
  }
  const sign = r.bias > 0 ? "+" : "";
  const labelClass = r.label === "well-calibrated" ? "good" : "warn";
  const insight = {
    overconfident: `You lean <b>overconfident</b> — your gut runs about ${Math.abs(r.bias)} points hotter than reality.`,
    underconfident: `You lean <b>underconfident</b> — you're about ${Math.abs(r.bias)} points better than you give yourself credit for.`,
    "well-calibrated": `You're <b>well-calibrated</b> — your confidence and reality line up. That's rare.`,
  }[r.label];
  const rows = r.buckets.map((b) => {
    const off = b.actual - b.stated;
    const tag = Math.abs(off) <= 10 ? "on the money" : off < 0 ? `${-off} pts too sure` : `${off} pts too modest`;
    return `<tr><td>said&nbsp;${b.label}</td><td>→ <b>${b.actual}%</b> right</td><td class="meta">${b.count} call${b.count > 1 ? "s" : ""} · ${tag}</td></tr>`;
  }).join("");
  body.innerHTML = `
    <div class="calib-head"><span class="badge ${labelClass}">${r.label}</span>
      <span class="meta">${r.graded} graded · avg confidence ${r.avg_confidence}% vs actual ${r.avg_accuracy}% (${sign}${r.bias})</span></div>
    <p class="calib-insight">${insight} Your typical miss is <b>${r.avg_gap} pts</b>.</p>
    <table class="calib"><tbody>${rows}</tbody></table>
    <div class="meta calib-bw">Best call: ${esc(r.best.title)} (${r.best.grade}% right) · Worst: ${esc(r.worst.title)} (${r.worst.grade}% right)</div>`;
}
async function loadDecisions() {
  loadCalibration();
  const { decisions, due } = await api("/api/decisions");
  renderDue(due);
  $("#decisions-list").innerHTML = decisions.length
    ? decisions.map((d) => `<div class="decision">
        <b>${esc(d.title)}</b> <span class="meta">conf ${d.confidence}%${d.self_grade != null ? ` · <span class="grade">graded ${d.self_grade}%</span>` : ""}</span>
        <div class="meta">${esc(d.prediction || "")}</div>
        ${d.outcome ? `<div class="meta">Outcome: ${esc(d.outcome)}</div>` : ""}
      </div>`).join("")
    : '<p class="muted">No decisions logged yet.</p>';
}

// --- memory & dossier ------------------------------------------------------
async function loadMemory() {
  const { memory } = await api("/api/memory");
  $("#memory-list").innerHTML = memory.length
    ? memory.map((m) => `<div class="mem"><span class="kind">${esc(m.kind)}</span><span>${esc(m.content)}</span></div>`).join("")
    : '<p class="muted">Nothing yet. Do the interview or just talk.</p>';
  loadContradictions();
}
async function loadContradictions() {
  const { contradictions } = await api("/api/contradictions");
  const card = $("#contradictions-card");
  if (!contradictions.length) { card.classList.add("hidden"); return; }
  card.classList.remove("hidden");
  $("#contradictions-list").innerHTML = contradictions.map((c) => `
    <div class="contradiction">
      <div class="was">used to think: ${esc(c.old_view)}</div>
      <div class="now">now: ${esc(c.new_view)}</div>
      ${c.note ? `<div class="meta">${esc(c.note)}</div>` : ""}
      <div class="row">
        <button class="ghost" onclick="confirmContradiction(${c.id})">✓ I changed my mind</button>
        <button class="ghost" onclick="dismissContradiction(${c.id})">dismiss</button>
      </div>
    </div>`).join("");
}
window.dismissContradiction = async (id) => {
  await api(`/api/contradictions/${id}/dismiss`, { method: "POST" });
  loadContradictions();
  refreshStatus();
};
window.confirmContradiction = async (id) => {
  const r = await api(`/api/contradictions/${id}/confirm`, { method: "POST" });
  loadContradictions();
  refreshStatus();
  if (r.dossier_updated) { loadDossier(); }
};
async function loadDossier() {
  $("#dossier-text").textContent = await api("/api/dossier");
}
$("#dossier-refresh").onclick = async () => {
  const btn = $("#dossier-refresh");
  btn.disabled = true;
  btn.textContent = "Re-syncing from everything…";
  try {
    const r = await api("/api/dossier/refresh", { method: "POST" });
    $("#dossier-text").textContent = r.dossier;
    refreshStatus();
  } catch (e) { alert(e.message); }
  btn.disabled = false;
  btn.textContent = "♻️ Re-sync from everything I've learned";
};
function esc(s) { return (s || "").replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c])); }

// --- today (home surface) --------------------------------------------------
function greeting() {
  const h = new Date().getHours();
  return h < 5 ? "Still up" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}
async function loadToday() {
  let t;
  try { t = await api("/api/today"); }
  catch (e) { $("#today-body").innerHTML = `<div class="card"><p class="muted">⚠ ${esc(e.message)}</p></div>`; return; }

  const parts = [];

  // The single best next step, given how far along you are.
  parts.push(`<div class="card today-hero">
    <h2>${greeting()}.</h2>
    <p class="muted">${esc(t.next.detail)}</p>
    <button class="primary" data-goto="${t.next.tab}">${esc(t.next.label)} →</button>
  </div>`);

  // What needs you now: due decisions, gradeable inline.
  if (t.due.length) {
    parts.push(`<div class="card due"><h2>⏰ Time to grade yourself</h2>${dueItemsHTML(t.due, "t")}</div>`);
  }

  // Your latest calibration read.
  const c = t.calibration;
  if (c && c.graded) {
    const cls = c.label === "well-calibrated" ? "good" : "warn";
    const sign = c.bias > 0 ? "+" : "";
    parts.push(`<div class="card">
      <h2>Your calibration</h2>
      <div class="calib-head"><span class="badge ${cls}">${c.label}</span>
        <span class="meta">${c.graded} graded · ${sign}${c.bias} vs reality · typical miss ${c.avg_gap} pts</span></div>
      <button class="ghost" data-goto="decisions">See the full scorecard →</button>
    </div>`);
  }

  // Unresolved shifts in your views.
  if (t.contradictions) {
    parts.push(`<div class="card">
      <p>🔀 <b>${t.contradictions}</b> unresolved shift${t.contradictions !== 1 ? "s" : ""} in your views.</p>
      <button class="ghost" data-goto="memory">Review them →</button>
    </div>`);
  }

  parts.push(`<p class="muted small today-stats">${t.counts.memories} memories · ${t.counts.decisions} decisions · ${t.counts.messages} messages${t.has_dossier ? " · dossier ✓" : ""}</p>`);

  $("#today-body").innerHTML = parts.join("");
}

// --- boot ------------------------------------------------------------------
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js").catch(() => {});
}
refreshStatus();
loadToday();
