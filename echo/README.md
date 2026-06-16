# Echo — your second voice

Echo is a personal AI that interviews you, builds a deep model of how you
think and feel, and uses that single growing picture of you to log decisions,
stress-test your takes, remember your life, and — eventually — speak on your
behalf.

One brain. One memory. Four modes.

## What it does

- **🎤 The Interview** — ~20 serious questions about how you think, decide, feel,
  and talk. Echo synthesizes them into a first-person *operating manual for
  being you* (your "dossier").
- **💬 Talk / Lore-keeper** — chat with something that actually remembers you.
  Every exchange quietly extracts durable views, facts, feelings, people and
  jokes into the database.
- **⚖️ Decision Logger + Calibration Scorecard** — record a decision, your
  reasoning, your prediction and your confidence. Echo pressure-tests it now and
  pings you to **grade your own calibration** when the review date arrives. The
  Decisions tab then shows your **scorecard**: whether you run over- or
  under-confident, your typical miss, and a reliability curve ("when you said
  90%, you were actually right 60% of the time"). This is the payoff of the whole
  loop — your judgment, measured.
- **🥊 Argue against me** — state a take; Echo steelmans the opposite *using what
  it knows about you* so the counter-argument actually lands.
- **🪞 Speak as me** — ask a question and Echo answers **as you**, in your voice.
- **🧭 Decide for me** — hand Echo a choice and it makes the call *and* writes the
  reasoning you'd give. In the Decisions tab, **"Draft this for me"** pre-fills a
  decision's reasoning, prediction and confidence as you.
- **🔀 Contradiction-spotting** — when something you say clashes with what you said
  before, Echo flags it ("you used to think X, now you're saying Y").

Everything lands in one SQLite database (`echo.db`) plus an evolving markdown
dossier.

### One brain — how it all links together

The modes aren't silos; they feed a single evolving model of you:

- Every mode (chat, argue, decide, decision-advice) reasons from the **same
  shared context**: your dossier, your memories, your **decision track record**,
  and your **unresolved contradictions**. So "decide for me" knows how your past
  calls actually turned out, and "argue against me" knows your open tensions.
- **Grading a decision feeds back** as a calibration signal ("on career bets I
  tend to be overconfident") that the rest of Echo then reasons from.
- **Confirming a contradiction** ("I changed my mind") updates your dossier so
  the whole model stays consistent.
- The dossier is **living**: hit *Re-sync from everything I've learned* and Echo
  rewrites it from all the evidence accumulated since — not just the interview.

### Voice

Talk with your voice and have replies read aloud. By default this uses the
browser's built-in speech (Chrome/Edge). Set `ELEVENLABS_API_KEY` for natural
text-to-speech and `OPENAI_API_KEY` for Whisper speech-to-text — Echo upgrades
automatically and falls back to the browser if a key is missing.

### Email reminders

Decisions come back to you to grade. Set SMTP env vars (`SMTP_HOST`,
`ECHO_EMAIL_TO`, …) and a background thread emails you when a decision's review
date arrives — once per decision. Without SMTP, Echo just prompts you on-open.
Trigger a check manually with `POST /api/reminders/run`.

## Run it

```bash
cd echo
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...      # your key — the brain reads this
uvicorn app:app --reload
```

Open <http://127.0.0.1:8000>. Use Chrome/Edge for voice input.

> No API key yet? The app still runs and stores everything — the interview,
> decisions and memory all work. You just won't get AI replies or a synthesized
> dossier until a key is set.

## Models

Defaults to **Claude Sonnet 4.6** for chat and **Claude Opus 4.8** for the deep
dossier synthesis. Override with `ECHO_MODEL` / `ECHO_SYNTH_MODEL`.

## Your data

It's yours and it's local. `echo.db` is gitignored. Hit **export** in the Memory
tab (or `GET /api/export`) to dump the entire brain as JSON anytime.

## Tests

```bash
pytest
```

## What's next

The original roadmap — premium voices, real email reminders, contradiction
spotting, and "decide for me" — is all shipped. What's left is depth:

- **Calibration by domain** — tag decisions (career / money / relationships) so the
  scorecard can say *where* you're miscalibrated, not just by how much.
- **A "Today" home surface** — open straight into what's due to grade and your
  latest calibration read, so there's always a reason to come back.
- **Trends over time** — is your calibration improving? Chart bias month over month.
- **Multi-user accounts** — today it's a single-user, single-password brain.
