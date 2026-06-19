import os

# ensure a fresh database for tests
if os.path.exists("data.db"):
    os.remove("data.db")

from fastapi.testclient import TestClient

from app import app
from database import get_connection
import seed as seed_module

client = TestClient(app)


def reset_empty():
    """Clear all data so a test can assert on exact counts."""
    conn = get_connection()
    cur = conn.cursor()
    cur.execute("DELETE FROM players")
    cur.execute("DELETE FROM matches")
    cur.execute("UPDATE season SET games_played = 0, points = 0 WHERE id = 1")
    conn.commit()
    conn.close()


def test_create_player_and_record_match():
    reset_empty()
    resp = client.post(
        "/players",
        json={"name": "John", "rating": 55, "nationality": "ENG", "homegrown": True},
    )
    assert resp.status_code == 200
    player = resp.json()
    assert player["name"] == "John"
    assert player["homegrown"] is True

    resp = client.post("/matches", json={"result": "W"})
    assert resp.status_code == 200
    season = resp.json()
    assert season["games_played"] == 1
    assert season["points"] == 3


def test_dashboard_served():
    resp = client.get("/")
    assert resp.status_code == 200
    assert "Cartoon Hero" in resp.text

    css = client.get("/static/styles.css")
    assert css.status_code == 200

    js = client.get("/static/app.js")
    assert js.status_code == 200


def test_get_players_and_season():
    resp = client.get("/players")
    assert resp.status_code == 200
    players = resp.json()
    assert len(players) >= 1

    resp = client.get("/season")
    assert resp.status_code == 200
    season = resp.json()
    assert "games_played" in season
    assert "points" in season


def test_matches_endpoint_returns_recent_first():
    reset_empty()
    for result in ["W", "D", "L"]:
        client.post("/matches", json={"result": result})

    resp = client.get("/matches?limit=10")
    assert resp.status_code == 200
    matches = resp.json()
    assert len(matches) == 3
    # most recent first
    assert [m["result"] for m in matches] == ["L", "D", "W"]


def test_seed_populates_squad_and_results():
    seed_module.seed(reset=True)

    players = client.get("/players").json()
    assert len(players) == len(seed_module.SQUAD)

    season = client.get("/season").json()
    assert season["games_played"] == len(seed_module.RESULTS)
    assert season["points"] > 0
