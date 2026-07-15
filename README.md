# cartoon-hero

This project provides a minimal FastAPI backend and simple interface for tracking a FIFA-style league season, players and match results.

It also includes **Mob Trumps** — a Top Trumps / Pokémon TCG Pocket style
battle system for collectable Minecraft creature cards, in two forms:

1. a **NeoForge Minecraft mod** (Minecraft 1.21.1) in [`mod/`](mod/) — the main deliverable
2. a FastAPI + browser prototype (below) used to design the card stats and rules

## NeoForge mod (`mod/`)

Build the jar (requires Java 21; Gradle is fetched by the wrapper):

```bash
cd mod
./gradlew build     # jar lands in mod/build/libs/mobtrumps-1.0.0.jar
```

Drop the jar into your `mods/` folder on a NeoForge 21.1.x (Minecraft 1.21.1)
client or server.

In game:

- **Mob Card** item — every one of the 81 mobs has its own inventory icon
  (a mini trading card with the mob's portrait), so a stack of cards reads
  at a glance without opening them; legendaries have a foil glint
- **Cards drop from mobs** — kill any mob and it drops **its own card** 100% of
  the time, right alongside its normal loot. The whole overworld (and Nether,
  and End) is your card hunt — no packs, no crafting, just play
- **Holographic cards, earned by hunting** — kill enough of one mob to unlock
  its **holographic** version, tracked with an action-bar progress counter.
  Thresholds scale with how common the mob is: **100** kills for a common mob,
  **75** uncommon, **25** rare, **10** epic, **5** legendary. A holo's boost is
  shaped by what that mob is known for: **+2 on its speciality stat** (Attack for
  a Creeper, Speed for a swift mob, Farmable for livestock) and **+1 on its next
  three defining stats** — +5 total, never more, stats capped at 10, rarity never
  touched. The boost is derived purely from the card itself, so everyone who
  earns a holo gets exactly the same upgraded card and no one gets a card no
  one else can
- Card tooltips show all six stats; names are coloured by collector tier
- Right-click a card to open a full Top Trumps style card view — kraft
  border, the live 3D mob as the portrait, stat table and total rating,
  plus a flavour line and a real Minecraft **fun fact** for every mob
- **Mob Collection Book** (craft: book + emerald) — an open two-page
  binder with a 3x3 grid of full Top Trumps cards on each page (live 3D
  portraits); unfound mobs lie face down. Search by name, filter (all /
  owned / missing / foil), sort (number / name / tier / rating), and a
  stats page (collection %, foils, filed-in-book, duel wins, per-tier). A ✦
  tab marks mobs you own in more than one variant — click one to open the
  variant picker and choose which (normal or holographic foil) sits on top
  of the pile. Saved with your player and survives death
- **Physical card storage** — the book is also a binder: hit **Store** (or
  `/mobtrumps store`) to file one of each loose card away, freeing up
  inventory slots while keeping spare duplicates for trading and foil-pressing.
  Filed cards get a green corner tab; shift-click one to take a copy back out,
  or `/mobtrumps withdraw` to empty the book. The binder is saved with your
  player and survives death
- `/mobtrumps battle [deck_size]` — start a Top Trumps battle against the CPU,
  played through clickable chat buttons; winning awards emeralds
- **Deck builder** — open the Collection Book → **Deck** to pick up to 16 cards
  you own into a custom deck, then `/mobtrumps battle deck` to fight the CPU
  with your own hand. Any card whose holo you've unlocked is played **boosted**
- **CPU difficulty** — `/mobtrumps battle [deck] easy|normal|hard`. Easy plays
  randomly; Normal leads with its strongest stat; Hard picks the stat with the
  best odds against the whole card pool and bluffs so you can't read it
- **Deck codes** — `/mobtrumps export` gives a short shareable code for your
  deck (click to copy); `/mobtrumps import <code>` loads a friend's
- `/mobtrumps duel <player>` — challenge a player to a PvP duel. Add
  `wager` while holding a card to bet it (both stake a card, winner takes both),
  or `bet <emeralds>` to gamble money — both players escrow the same number of
  emeralds and the winner takes the whole pot (stakes are refunded on a draw,
  decline, timeout or logout). Add `bo3` or `bo5` for a **best-of series** —
  the deck is re-dealt each game and the first to win the majority takes the match
- **Dueling Table** (craft: 3 crafting tables + 2 paper + emerald) — a placeable
  block so duels are discoverable without commands: right-click to take a seat,
  and when a second player right-clicks it a best-of-3 duel begins between you
- **Card Scanner** (craft: spyglass + emerald + amethyst shard) — hold
  right-click to raise it to your eye like a spyglass and zoom in; any mob you
  look at gets its Mob Trumps card **projected in the air above it**, plus a
  scanner readout of its full stat block at the bottom of the screen. Great for
  scouting a mob's card before you commit to the hunt
- **Card Display** (craft: item frame + emerald) — a sleek dark wall panel that
  **projects** a card straight from your collection: right-click to open a modern
  picker and choose any card you own (shift-click for its holographic version).
  The card **never leaves your collection**, so there is nothing to steal — the
  projected card floats in the frame but only its owner can swap it (right-click)
  or clear it (sneak-right-click). Anyone else who right-clicks just admires it
  full-screen. Show off your rarest holo with zero risk
- **Quick match & spectating** — `/mobtrumps queue` auto-pairs you with any
  other waiting player; `/mobtrumps watch <player>` lets you follow a live duel
  in chat and place an emerald **side bet** on either duelist (pari-mutuel —
  backers of the winner split the pool). `/mobtrumps rematch` re-challenges your
  last opponent, `/mobtrumps emote <gg|nice|close|oops|gl|wow>` taunts mid-duel,
  and a 45-second **turn timer** auto-plays anyone who stalls
- `/mobtrumps trade <player>` — offer your held card; they hold one and accept
  to swap 1-for-1
- `/mobtrumps foil` — press 4 duplicate copies of a card into 1 holographic foil
- **Ranked ladder & seasons** — duels update an Elo rating stored server-wide
  (persisted, offline players included). Ratings map to **tiers with divisions**
  — Bronze III up to Master — so you rank up every couple of wins, with a
  promotion fanfare (and demotions to claw back). Play runs in **seasons**
  (length set in config, default 7 days): when one ends, ratings soft-reset
  toward the mean, every ranked player keeps a **permanent tier badge** and is
  paid emeralds for their final tier (queued for anyone offline). `/mobtrumps
  top` shows the season standings with tiers, `/mobtrumps season` shows your
  tier, peak, badges and time left, and both are surfaced on `/mobtrumps stats`
  and your profile card. Ops can force a rollover with `/mobtrumps season end`
- `/mobtrumps play <stat>`, `/mobtrumps next`, `/mobtrumps forfeit`
- `/mobtrumps` on its own opens a clickable hub menu
- `/mobtrumps guide` — get a written **How to Play** book covering collecting,
  holographics, battling, duels, spectating and decks
- Juice: unlocking a holo bursts particles in-world; cards ease in with a
  rarity halo and a shine sweep; battle rounds play win/lose sound cues
- Creative tab "Mob Trumps" contains every card (normal and holographic)

- **Advancements** — a Mob Trumps tab tracks milestones: first card, 10 /
  40 / all 81 collected, your first foil (and the hidden all-foils goal),
  and duel wins.
- **Config file** (`config/mobtrumps-common.toml`) — admins can tune max deck
  size (and other knobs) without editing code.

Dev runs: `./gradlew runClient` / `./gradlew runServer`.

## Mob Trumps

Every Minecraft mob (81 cards) has six stats on a 0-10 scale:

| Stat | Meaning |
| --- | --- |
| Health | How tough the mob is |
| Attack | How hard it hits (0 = passive) |
| Size | Physical size |
| Speed | How fast it moves |
| Farmable | How good / helpful it is to farm |
| Rarity | How likely it is to spawn (10 = everywhere, 1 = legendary) |

Cards also get a collector tier derived from spawn rarity:
common → uncommon → rare → epic → legendary.

**Battle rules (Top Trumps):** the deck is dealt evenly between you and the
CPU. Whoever holds the turn picks a stat on their top card; both cards are
revealed and the higher value wins both cards (plus any pot). Ties send both
cards to the pot. Collect every card to win.

Play in the browser at <http://127.0.0.1:8000/> once the API is running.

### Card game API

| Endpoint | Description |
| --- | --- |
| `GET /cards` | Full card collection |
| `GET /cards/{name}` | Single card by mob name |
| `POST /packs/open?size=5` | Open a booster pack (pulls weighted by rarity) |
| `POST /battles` | Start a battle (`{"deck_size": 30}`) |
| `GET /battles/{id}` | Current battle state (CPU cards stay hidden) |
| `POST /battles/{id}/play` | Play a round: `{"stat": "attack"}` on your turn, `{}` on the CPU's |

## Development

Create a virtual environment and install dependencies:

```bash
pip install -r requirements.txt
```

Run the API locally:

```bash
uvicorn app:app --reload
```

Interactive docs are available at <http://127.0.0.1:8000/docs>.

## Testing

```bash
pytest
```
