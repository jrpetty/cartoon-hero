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
- **⚖️ Decision Logger** — record a decision, your reasoning, your prediction and
  your confidence. Echo pressure-tests it now and pings you to **grade your own
  calibration** when the review date arrives.
- **🥊 Argue against me** — state a take; Echo steelmans the opposite *using what
  it knows about you* so the counter-argument actually lands.
- **🪞 Speak as me** — ask a question and Echo answers **as you**, in your voice.

Everything lands in one SQLite database (`echo.db`) plus an evolving markdown
dossier. Talk with your voice (browser speech-to-text) and have replies read
back aloud.

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

## Roadmap

- Premium cloud voices (ElevenLabs / Whisper) for a more natural sound
- Real scheduled reminders (email via your inbox) instead of on-open prompts
- Per-topic memory recall and contradiction-spotting ("you used to think…")
- A "decide for me" mode that drafts a choice + the reasoning you'd give
