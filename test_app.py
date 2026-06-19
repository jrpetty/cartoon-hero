import os
import json
from fastapi.testclient import TestClient

# ensure a fresh database for tests
if os.path.exists("data.db"):
    os.remove("data.db")

from app import app

client = TestClient(app)


def test_create_player_and_record_match():
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
