# cartoon-hero

This project provides a minimal FastAPI backend and simple interface for tracking a FIFA-style league season, players and match results.

It also includes **Mob Trumps** — a Top Trumps / Pokémon TCG Pocket style
battle system for collectable Minecraft creature cards.

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
