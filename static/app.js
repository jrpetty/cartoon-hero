const api = {
  async season() {
    const r = await fetch("/season");
    if (!r.ok) throw new Error("Failed to load season");
    return r.json();
  },
  async players() {
    const r = await fetch("/players");
    if (!r.ok) throw new Error("Failed to load players");
    return r.json();
  },
  async matches() {
    const r = await fetch("/matches?limit=20");
    if (!r.ok) throw new Error("Failed to load matches");
    return r.json();
  },
  async addPlayer(payload) {
    const r = await fetch("/players", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!r.ok) throw new Error("Failed to add player");
    return r.json();
  },
  async recordMatch(result) {
    const r = await fetch("/matches", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ result }),
    });
    if (!r.ok) throw new Error("Failed to record match");
    return r.json();
  },
};

function toast(msg, isError = false) {
  const el = document.getElementById("toast");
  el.textContent = msg;
  el.classList.toggle("error", isError);
  el.classList.add("show");
  clearTimeout(toast._t);
  toast._t = setTimeout(() => el.classList.remove("show"), 2600);
}

function renderSeason(s) {
  document.getElementById("stat-games").textContent = s.games_played;
  document.getElementById("stat-points").textContent = s.points;
  const ppg = s.games_played ? (s.points / s.games_played).toFixed(2) : "0.00";
  document.getElementById("stat-ppg").textContent = ppg;
  document.getElementById("stat-budget").textContent = "$" + s.budget.toLocaleString();
}

// matches arrive most-recent-first from the API
function renderForm(matches) {
  const counts = { W: 0, D: 0, L: 0 };
  matches.forEach((m) => {
    if (counts[m.result] !== undefined) counts[m.result] += 1;
  });
  document.getElementById("stat-record").textContent =
    `${counts.W}-${counts.D}-${counts.L}`;
  document.getElementById("form-summary").textContent =
    `last ${matches.length} match${matches.length === 1 ? "" : "es"}`;

  const strip = document.getElementById("form-strip");
  if (!matches.length) {
    strip.innerHTML = '<span class="empty">No matches yet</span>';
    return;
  }
  const labels = { W: "W", D: "D", L: "L" };
  const titles = { W: "Win", D: "Draw", L: "Loss" };
  // show oldest -> newest so the strip reads left to right
  strip.innerHTML = [...matches]
    .reverse()
    .map(
      (m) =>
        `<span class="form-chip ${m.result.toLowerCase()}" title="${titles[m.result] || m.result}">${labels[m.result] || "?"}</span>`
    )
    .join("");
}

function renderPlayers(players) {
  const body = document.getElementById("players-body");
  document.getElementById("squad-count").textContent =
    `${players.length} player${players.length === 1 ? "" : "s"}`;

  if (!players.length) {
    body.innerHTML = '<tr><td colspan="6" class="empty">No players yet — add one →</td></tr>';
    return;
  }

  const sorted = [...players].sort((a, b) => b.rating - a.rating);
  body.innerHTML = sorted
    .map((p) => {
      const homegrown = p.homegrown
        ? '<span class="badge yes">Yes</span>'
        : '<span class="badge no">No</span>';
      const injury =
        p.injury_games_remaining > 0
          ? `<span class="badge injured">${p.injury_games_remaining}</span>`
          : "—";
      return `<tr>
        <td>${escapeHtml(p.name)}</td>
        <td>${p.rating}</td>
        <td>${escapeHtml(p.nationality)}</td>
        <td>${homegrown}</td>
        <td>${p.matches_played}</td>
        <td>${injury}</td>
      </tr>`;
    })
    .join("");
}

function escapeHtml(str) {
  return String(str).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])
  );
}

async function refresh() {
  try {
    const [season, players, matches] = await Promise.all([
      api.season(),
      api.players(),
      api.matches(),
    ]);
    renderSeason(season);
    renderPlayers(players);
    renderForm(matches);
  } catch (e) {
    toast(e.message, true);
  }
}

document.getElementById("refresh").addEventListener("click", refresh);

document.querySelectorAll(".result-btn").forEach((btn) => {
  btn.addEventListener("click", async () => {
    try {
      await api.recordMatch(btn.dataset.result);
      const labels = { W: "Win", D: "Draw", L: "Loss" };
      toast(`${labels[btn.dataset.result]} recorded`);
      refresh();
    } catch (e) {
      toast(e.message, true);
    }
  });
});

document.getElementById("player-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const form = ev.target;
  const payload = {
    name: form.name.value.trim(),
    rating: Number(form.rating.value),
    nationality: form.nationality.value.trim(),
    homegrown: form.homegrown.checked,
  };
  if (!payload.name || !payload.nationality) {
    toast("Name and nationality are required", true);
    return;
  }
  try {
    await api.addPlayer(payload);
    form.reset();
    form.rating.value = 60;
    toast(`${payload.name} added to squad`);
    refresh();
  } catch (e) {
    toast(e.message, true);
  }
});

refresh();
