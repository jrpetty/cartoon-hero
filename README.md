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

- **Mob Card Pack** item — craft with 3 paper + 1 emerald, right-click to pull
  5 rarity-weighted mob cards (all 81 collectable, legendaries have a foil glint)
- Card tooltips show all six stats; names are coloured by collector tier
- Right-click a card to open a full Top Trumps style card view — kraft
  border, the live 3D mob as the portrait, stat table and total rating
- **Mob Collection Book** (craft: book + emerald) — a 9x9 grid tracking
  all 81 cards you've ever collected, with progress bar; collected cards
  show in tier colours, missing ones as "?" slots, click one to view it.
  Collection is saved with your player and survives death
- `/mobtrumps battle [deck_size]` — start a Top Trumps battle against the CPU,
  played through clickable chat buttons; winning awards a free card pack
- `/mobtrumps play <stat>`, `/mobtrumps next`, `/mobtrumps forfeit`
- Creative tab "Mob Trumps" contains every card

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
