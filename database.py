"""Simple SQLite backend for tracking squad and season data."""
import sqlite3
from pathlib import Path
from typing import List, Dict, Any

DB_PATH = Path(__file__).with_name("data.db")


def get_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    """Initialise all tables and ensure a season row exists."""
    conn = get_connection()
    cur = conn.cursor()
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS players (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            rating INTEGER NOT NULL,
            nationality TEXT NOT NULL,
            homegrown INTEGER NOT NULL DEFAULT 0,
            matches_played INTEGER NOT NULL DEFAULT 0,
            injury_games_remaining INTEGER NOT NULL DEFAULT 0
        );
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS season (
            id INTEGER PRIMARY KEY CHECK(id = 1),
            games_played INTEGER NOT NULL DEFAULT 0,
            points INTEGER NOT NULL DEFAULT 0,
            rating_cap INTEGER NOT NULL DEFAULT 60,
            budget INTEGER NOT NULL DEFAULT 0
        );
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS matches (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            result TEXT NOT NULL CHECK(result IN ('W','D','L')),
            season_id INTEGER NOT NULL,
            FOREIGN KEY(season_id) REFERENCES season(id)
        );
        """
    )
    # ensure a season row exists
    cur.execute("INSERT OR IGNORE INTO season(id) VALUES (1)")
    conn.commit()
    conn.close()


def add_player(name: str, rating: int, nationality: str, homegrown: bool) -> int:
    conn = get_connection()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO players(name, rating, nationality, homegrown) VALUES (?,?,?,?)",
        (name, rating, nationality, int(homegrown)),
    )
    conn.commit()
    player_id = cur.lastrowid
    conn.close()
    return player_id


def list_players() -> List[Dict[str, Any]]:
    conn = get_connection()
    cur = conn.cursor()
    cur.execute("SELECT * FROM players")
    rows = cur.fetchall()
    conn.close()
    return [dict(row) for row in rows]


def record_match(result: str) -> None:
    points_map = {"W": 3, "D": 1, "L": 0}
    if result not in points_map:
        raise ValueError("result must be one of 'W', 'D' or 'L'")
    conn = get_connection()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO matches(result, season_id) VALUES (?, 1)", (result,)
    )
    cur.execute(
        "UPDATE season SET games_played = games_played + 1, points = points + ? WHERE id = 1",
        (points_map[result],),
    )
    conn.commit()
    conn.close()


def get_season() -> Dict[str, Any]:
    conn = get_connection()
    cur = conn.cursor()
    cur.execute("SELECT games_played, points, rating_cap, budget FROM season WHERE id=1")
    row = cur.fetchone()
    conn.close()
    return dict(row)


if __name__ == "__main__":
    init_db()
