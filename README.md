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
- **Mob Card Pack** item — craft with 3 paper + 1 emerald, right-click to open
  a **scrollable unboxing**: your 5 pulls fan out face-down and flip up as you
  scroll through them (wheel / ← → / on-screen arrows), with a rarity chime,
  sparkle bursts and a glow on legendaries/foils, then a summary. Scroll back
  and forth to admire the whole pack. ~9% of pulls are rare **holographic
  foils** with an animated rainbow sheen, tracked separately in the collection
- Card tooltips show all six stats; names are coloured by collector tier
- Right-click a card to open a full Top Trumps style card view — kraft
  border, the live 3D mob as the portrait, stat table and total rating
- **Mob Collection Book** (craft: book + emerald) — an open two-page
  binder with a 3x3 grid of full Top Trumps cards on each page (live 3D
  portraits); unfound mobs lie face down. Search by name, filter (all /
  owned / missing / foil), sort (number / name / tier / rating), and a
  stats page (collection %, foils, duel wins, per-tier). A ✦ tab marks
  mobs you own in more than one variant — click one to open the variant
  picker and choose which (normal or holographic foil) sits on top of the
  pile. Saved with your player and survives death
- `/mobtrumps battle [deck_size]` — start a Top Trumps battle against the CPU,
  played through clickable chat buttons; winning awards a free card pack
- **Deck builder** — open the Collection Book → **Deck** to pick up to 16 cards
  you own into a custom deck, then `/mobtrumps battle deck` to fight the CPU
  with your own hand
- `/mobtrumps duel <player> [wager]` — challenge a player to a PvP duel; add
  `wager` while holding a card to bet it (both stake a card, winner takes both)
- `/mobtrumps trade <player>` — offer your held card; they hold one and accept
  to swap 1-for-1
- `/mobtrumps foil` — press 4 duplicate copies of a card into 1 holographic foil
- The **Wandering Trader** now sells Mob Card Packs for emeralds
- **Special packs** — a **Nether Pack** (craft: 3 paper + blaze powder +
  emerald; Nether-mob pool, odds tilted toward rarer cards) and a **Boss Pack**
  (3 paper + diamond + emerald; tough-mob pool, ~½ chance at a legendary), both
  with higher foil odds. Nothing is guaranteed — the premium packs just roll
  from a better-stacked bag
- **Ranked ladder** — duels update an Elo rating stored server-wide (persisted,
  offline players included); `/mobtrumps top` shows the standings and your rank
- `/mobtrumps play <stat>`, `/mobtrumps next`, `/mobtrumps forfeit`
- `/mobtrumps` on its own opens a clickable hub menu
- Juice: legendary/foil pulls burst particles in-world and sparkles on the
  reveal screen; cards ease in with a rarity halo and a shine sweep; battle
  rounds play win/lose sound cues; the reveal ends on a summary panel
- Creative tab "Mob Trumps" contains every card

- **Advancements** — a Mob Trumps tab tracks milestones: first card, 10 /
  40 / all 81 collected, your first foil (and the hidden all-foils goal),
  and duel wins.

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
