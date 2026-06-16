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
document.querySelectorAll(".tabs button").forEach((b) => {
  b.onclick = () => {
    document.querySelectorAll(".tabs button").forEach((x) => x.classList.remove("active"));
    document.querySelectorAll(".tab").forEach((x) => x.classList.remove("active"));
    b.classList.add("active");
    $("#tab-" + b.dataset.tab).classList.add("active");
    if (b.dataset.tab === "decisions") loadDecisions();
    if (b.dataset.tab === "memory") loadMemory();
    if (b.dataset.tab === "dossier") loadDossier();
  };
});

// --- voice (Web Speech API) ------------------------------------------------
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
function listen(targetEl, micBtn) {
  if (!SR) { alert("Voice input needs Chrome/Edge. Type instead — it all works."); return; }
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
function speak(text) {
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
    addBubble("note", { chat: "— talking with you —", argue: "— I'll argue the other side —", asme: "— I'll answer as you —" }[mode]);
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
function renderDue(due) {
  const box = $("#decisions-due");
  if (!box) return;
  if (!due.length) { box.innerHTML = ""; return; }
  box.innerHTML = `<div class="card due"><h2>⏰ Time to grade yourself</h2>${due
    .map(
      (d) => `<div class="decision">
        <b>${esc(d.title)}</b>
        <div class="meta">You predicted: ${esc(d.prediction || "—")} (confidence ${d.confidence}%)</div>
        <textarea id="out-${d.id}" rows="2" placeholder="What actually happened?"></textarea>
        <div class="row"><label>How right were you? <input type="number" id="grade-${d.id}" min="0" max="100" value="50" style="width:64px"/>%</label>
        <button class="primary" onclick="gradeDecision(${d.id})">Save grade</button></div>
      </div>`
    )
    .join("")}</div>`;
}
window.gradeDecision = async (id) => {
  try {
    await api(`/api/decisions/${id}/review`, {
      method: "POST",
      body: JSON.stringify({ outcome: $("#out-" + id).value, self_grade: +$("#grade-" + id).value }),
    });
    refreshStatus();
    loadDecisions();
  } catch (e) { alert(e.message); }
};
async function loadDecisions() {
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
}
async function loadDossier() {
  $("#dossier-text").textContent = await api("/api/dossier");
}
function esc(s) { return (s || "").replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c])); }

// --- boot ------------------------------------------------------------------
refreshStatus();
loadInterview();
