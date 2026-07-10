"""Top Trumps style battle engine for the Minecraft mob cards.

Rules
-----
* A shuffled deck is dealt evenly between the player and the CPU.
* Whoever holds the turn picks one of the six stats on their top card.
* Both top cards are revealed; the higher value on that stat wins.
* The winner puts both cards (plus anything in the pot) on the bottom
  of their deck and picks the stat next round.
* On a tie both cards go into the pot and the same side picks again.
* You win by taking every card. A long stalemate is called on card count.
"""
from __future__ import annotations

import random
import uuid
from typing import Dict, List, Optional

from cards import CARDS, STATS

MAX_ROUNDS = 500

# in-memory store of running games, keyed by game id
GAMES: Dict[str, Dict] = {}


class BattleError(ValueError):
    """Raised for illegal battle moves (bad stat, wrong turn, finished game)."""


def new_game(deck_size: int = 30, rng: Optional[random.Random] = None) -> Dict:
    """Create a game: shuffle the collection, deal `deck_size` cards evenly."""
    if not 4 <= deck_size <= len(CARDS):
        raise BattleError(f"deck size must be between 4 and {len(CARDS)}")
    if deck_size % 2:
        deck_size -= 1  # both sides need equal hands

    rng = rng or random
    deck = [dict(c) for c in rng.sample(CARDS, deck_size)]
    game = {
        "id": uuid.uuid4().hex[:12],
        "player_deck": deck[: deck_size // 2],
        "cpu_deck": deck[deck_size // 2:],
        "pot": [],
        "turn": "player",
        "round": 0,
        "finished": False,
        "winner": None,
        "last_round": None,
    }
    GAMES[game["id"]] = game
    return game


def get_game(game_id: str) -> Optional[Dict]:
    return GAMES.get(game_id)


def cpu_pick_stat(card: Dict, rng: Optional[random.Random] = None) -> str:
    """CPU strategy: lead with the card's strongest stat."""
    best = max(STATS, key=lambda s: card[s])
    return best


def play_round(game: Dict, stat: Optional[str] = None,
               rng: Optional[random.Random] = None) -> Dict:
    """Play one round. `stat` is required on the player's turn and
    ignored on the CPU's turn (the CPU chooses for itself)."""
    if game["finished"]:
        raise BattleError("game is already finished")

    chooser = game["turn"]
    if chooser == "player":
        if stat is None:
            raise BattleError("it is your turn: pick a stat")
        stat = stat.strip().lower()
        if stat not in STATS:
            raise BattleError(f"unknown stat {stat!r}, choose one of {STATS}")
    else:
        stat = cpu_pick_stat(game["cpu_deck"][0], rng)

    player_card = game["player_deck"].pop(0)
    cpu_card = game["cpu_deck"].pop(0)
    game["round"] += 1

    if player_card[stat] > cpu_card[stat]:
        result = "player"
        game["player_deck"] += [player_card, cpu_card] + game["pot"]
        game["pot"] = []
        game["turn"] = "player"
    elif cpu_card[stat] > player_card[stat]:
        result = "cpu"
        game["cpu_deck"] += [cpu_card, player_card] + game["pot"]
        game["pot"] = []
        game["turn"] = "cpu"
    else:
        result = "tie"
        game["pot"] += [player_card, cpu_card]
        # turn stays with the current chooser

    game["last_round"] = {
        "round": game["round"],
        "chooser": chooser,
        "stat": stat,
        "player_card": player_card,
        "cpu_card": cpu_card,
        "result": result,
    }

    _check_end(game)
    return game["last_round"]


def _check_end(game: Dict) -> None:
    player_out = not game["player_deck"]
    cpu_out = not game["cpu_deck"]
    if player_out and cpu_out:
        game["finished"], game["winner"] = True, "draw"
    elif player_out:
        game["finished"], game["winner"] = True, "cpu"
    elif cpu_out:
        game["finished"], game["winner"] = True, "player"
    elif game["round"] >= MAX_ROUNDS:
        # stalemate: call it on cards held
        p, c = len(game["player_deck"]), len(game["cpu_deck"])
        game["finished"] = True
        game["winner"] = "player" if p > c else "cpu" if c > p else "draw"


def public_state(game: Dict) -> Dict:
    """Game state as seen by the player: CPU cards stay face down."""
    state = {
        "id": game["id"],
        "round": game["round"],
        "turn": game["turn"],
        "finished": game["finished"],
        "winner": game["winner"],
        "player_deck_count": len(game["player_deck"]),
        "cpu_deck_count": len(game["cpu_deck"]),
        "pot_count": len(game["pot"]),
        "player_card": game["player_deck"][0] if game["player_deck"] else None,
        "last_round": game["last_round"],
    }
    return state
