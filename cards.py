"""Minecraft mob card collection for the Top Trumps style battle system.

Every card carries six stats on a 0-10 scale:

* health   - how tough the mob is
* attack   - how hard it hits (0 = fully passive)
* size     - physical size of the mob
* speed    - how fast it moves
* farmable - how good / helpful the mob is to farm
* rarity   - how likely the mob is to spawn (10 = everywhere, 1 = legendary)
"""
from __future__ import annotations

import random
from typing import Dict, List, Optional

STATS = ["health", "attack", "size", "speed", "farmable", "rarity"]

# name, health, attack, size, speed, farmable, rarity
_MOB_DATA = [
    ("Allay", 2, 0, 1, 4, 5, 3),
    ("Armadillo", 2, 0, 2, 3, 4, 4),
    ("Axolotl", 2, 2, 2, 4, 6, 4),
    ("Bat", 1, 0, 1, 4, 1, 8),
    ("Bee", 1, 1, 1, 4, 8, 7),
    ("Blaze", 4, 4, 3, 5, 8, 5),
    ("Bogged", 3, 3, 3, 3, 7, 6),
    ("Breeze", 3, 4, 3, 6, 3, 4),
    ("Camel", 6, 0, 5, 3, 5, 3),
    ("Cat", 2, 0, 2, 4, 4, 6),
    ("Cave Spider", 2, 3, 2, 6, 6, 6),
    ("Chicken", 1, 0, 1, 3, 10, 10),
    ("Cod", 1, 0, 1, 3, 8, 9),
    ("Cow", 2, 0, 3, 2, 10, 9),
    ("Creaking", 5, 4, 4, 3, 2, 4),
    ("Creeper", 3, 8, 3, 4, 9, 9),
    ("Dolphin", 2, 2, 3, 8, 2, 7),
    ("Donkey", 4, 0, 4, 3, 6, 5),
    ("Drowned", 3, 3, 3, 3, 8, 8),
    ("Elder Guardian", 8, 6, 7, 3, 2, 1),
    ("Ender Dragon", 10, 9, 10, 5, 1, 1),
    ("Enderman", 6, 5, 7, 4, 6, 6),
    ("Endermite", 1, 2, 1, 4, 3, 3),
    ("Evoker", 3, 5, 3, 3, 5, 2),
    ("Fox", 2, 0, 2, 5, 4, 5),
    ("Frog", 2, 0, 1, 4, 4, 6),
    ("Ghast", 2, 8, 8, 4, 7, 6),
    ("Glow Squid", 2, 0, 2, 3, 4, 7),
    ("Goat", 2, 2, 3, 4, 4, 5),
    ("Guardian", 3, 4, 3, 4, 6, 4),
    ("Hoglin", 8, 6, 6, 5, 9, 7),
    ("Horse", 5, 0, 4, 6, 7, 6),
    ("Husk", 3, 3, 3, 3, 7, 8),
    ("Iron Golem", 8, 7, 8, 2, 10, 5),
    ("Llama", 3, 1, 4, 3, 5, 5),
    ("Magma Cube", 3, 3, 4, 4, 7, 6),
    ("Mooshroom", 2, 0, 3, 2, 9, 2),
    ("Mule", 4, 0, 4, 3, 5, 3),
    ("Ocelot", 2, 0, 2, 5, 3, 4),
    ("Panda", 4, 2, 4, 2, 4, 3),
    ("Parrot", 1, 0, 1, 5, 4, 3),
    ("Phantom", 2, 3, 2, 8, 3, 6),
    ("Pig", 2, 0, 3, 3, 10, 9),
    ("Piglin", 3, 4, 3, 5, 8, 8),
    ("Piglin Brute", 5, 6, 3, 4, 4, 3),
    ("Pillager", 3, 4, 3, 4, 6, 6),
    ("Polar Bear", 4, 3, 5, 3, 2, 3),
    ("Pufferfish", 1, 1, 1, 2, 5, 7),
    ("Rabbit", 1, 0, 1, 6, 7, 8),
    ("Ravager", 7, 7, 7, 4, 5, 3),
    ("Salmon", 1, 0, 1, 4, 8, 9),
    ("Sheep", 2, 0, 3, 2, 10, 9),
    ("Shulker", 3, 3, 2, 1, 4, 2),
    ("Silverfish", 1, 2, 1, 6, 4, 7),
    ("Skeleton", 3, 3, 3, 3, 9, 10),
    ("Skeleton Horse", 4, 0, 4, 5, 5, 2),
    ("Slime", 3, 2, 4, 4, 9, 8),
    ("Sniffer", 2, 0, 6, 2, 3, 2),
    ("Snow Golem", 2, 1, 3, 4, 7, 5),
    ("Spider", 3, 2, 4, 6, 8, 10),
    ("Squid", 2, 0, 2, 3, 7, 9),
    ("Stray", 3, 3, 3, 3, 7, 6),
    ("Strider", 3, 0, 3, 3, 6, 7),
    ("Sulfur Cube", 4, 5, 4, 5, 6, 3),
    ("Tadpole", 1, 0, 1, 3, 4, 7),
    ("Trader Llama", 3, 1, 4, 3, 4, 4),
    ("Tropical Fish", 1, 0, 1, 4, 6, 8),
    ("Turtle", 3, 0, 2, 2, 6, 5),
    ("Vex", 2, 4, 1, 9, 1, 3),
    ("Villager", 3, 0, 3, 3, 10, 7),
    ("Vindicator", 4, 5, 3, 4, 5, 4),
    ("Wandering Trader", 3, 0, 3, 3, 5, 3),
    ("Warden", 10, 10, 10, 4, 1, 1),
    ("Witch", 3, 4, 3, 3, 7, 6),
    ("Wither", 9, 8, 8, 5, 5, 1),
    ("Wither Skeleton", 4, 5, 4, 4, 8, 6),
    ("Wolf", 3, 3, 2, 4, 7, 7),
    ("Zombie", 3, 3, 3, 3, 9, 10),
    ("Zombie Villager", 3, 3, 3, 3, 9, 7),
    ("Zombified Piglin", 3, 4, 3, 4, 9, 9),
    ("Zoglin", 6, 6, 4, 5, 6, 4),
]

# rarity stat -> collector tier (rarity is spawn likelihood, so LOW = rarer)
_TIERS = [
    (2, "legendary"),
    (4, "epic"),
    (6, "rare"),
    (8, "uncommon"),
    (10, "common"),
]


def _tier(rarity: int) -> str:
    for ceiling, name in _TIERS:
        if rarity <= ceiling:
            return name
    return "common"


def _build_card(row) -> Dict:
    name, health, attack, size, speed, farmable, rarity = row
    return {
        "name": name,
        "health": health,
        "attack": attack,
        "size": size,
        "speed": speed,
        "farmable": farmable,
        "rarity": rarity,
        "tier": _tier(rarity),
    }


CARDS: List[Dict] = [_build_card(row) for row in _MOB_DATA]
_CARDS_BY_NAME: Dict[str, Dict] = {c["name"].lower(): c for c in CARDS}


def get_all_cards() -> List[Dict]:
    """Return every card in the collection."""
    return [dict(c) for c in CARDS]


def get_card(name: str) -> Optional[Dict]:
    """Look up a single card by (case-insensitive) mob name."""
    card = _CARDS_BY_NAME.get(name.strip().lower())
    return dict(card) if card else None


def open_pack(size: int = 5, rng: Optional[random.Random] = None) -> List[Dict]:
    """Open a booster pack of distinct cards, weighted by spawn rarity.

    A mob with rarity 10 (spawns everywhere) is ten times more likely to
    turn up in a pack than a rarity 1 legendary.
    """
    if not 1 <= size <= len(CARDS):
        raise ValueError(f"pack size must be between 1 and {len(CARDS)}")
    rng = rng or random
    pool = list(CARDS)
    pulls: List[Dict] = []
    for _ in range(size):
        weights = [c["rarity"] for c in pool]
        pick = rng.choices(pool, weights=weights, k=1)[0]
        pool.remove(pick)
        pulls.append(dict(pick))
    return pulls
