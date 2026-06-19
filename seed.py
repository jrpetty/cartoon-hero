"""Populate the database with a demo squad and season so the dashboard
has something to show on first launch.

Run directly to (re)seed an empty database::

    python seed.py

The FastAPI app also calls :func:`seed_if_empty` on startup, so a freshly
cloned checkout shows a full squad and match history with no manual setup.
"""
from database import (
    init_db,
    add_player,
    record_match,
    count_players,
    get_connection,
)

# A cartoon-hero themed squad: (name, rating, nationality, homegrown)
SQUAD = [
    ("Bruce Wayne", 91, "USA", False),
    ("Diana Prince", 90, "GRC", False),
    ("Clark Kent", 93, "USA", False),
    ("Peter Parker", 84, "USA", True),
    ("T'Challa", 88, "WAK", False),
    ("Natasha Romanoff", 86, "RUS", False),
    ("Steve Rogers", 89, "USA", True),
    ("Tony Stark", 90, "USA", True),
    ("Hal Jordan", 82, "USA", True),
    ("Barry Allen", 87, "USA", True),
    ("Arthur Curry", 83, "ATL", False),
    ("Wanda Maximoff", 85, "SOK", False),
    ("Scott Lang", 76, "USA", True),
    ("Carol Danvers", 92, "USA", True),
    ("Bruce Banner", 80, "USA", True),
    ("Kara Danvers", 88, "USA", False),
    ("Stephen Strange", 87, "USA", True),
    ("Wally West", 74, "USA", True),
]

# A run of results for the season tally and form guide (oldest first).
RESULTS = list("WWDLWWWDLWWDWLW")


def seed(reset: bool = False) -> None:
    """Insert the demo squad and season results.

    If ``reset`` is True, existing players, matches and season totals are
    cleared first so the data is deterministic.
    """
    init_db()
    if reset:
        conn = get_connection()
        cur = conn.cursor()
        cur.execute("DELETE FROM players")
        cur.execute("DELETE FROM matches")
        cur.execute(
            "UPDATE season SET games_played = 0, points = 0,"
            " rating_cap = 88, budget = 25000000 WHERE id = 1"
        )
        conn.commit()
        conn.close()

    for name, rating, nationality, homegrown in SQUAD:
        add_player(name, rating, nationality, homegrown)

    for result in RESULTS:
        record_match(result)


def seed_if_empty() -> None:
    """Seed only when no players exist, so real data is never overwritten."""
    init_db()
    if count_players() == 0:
        seed(reset=True)


if __name__ == "__main__":
    seed(reset=True)
    print(f"Seeded {len(SQUAD)} players and {len(RESULTS)} match results.")
