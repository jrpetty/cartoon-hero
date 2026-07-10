import random

import pytest
from fastapi.testclient import TestClient

import battle
import cards
from app import app

client = TestClient(app)


# --- card collection ---

def test_collection_has_every_mob():
    all_cards = cards.get_all_cards()
    assert len(all_cards) == 81
    names = {c["name"] for c in all_cards}
    assert len(names) == 81  # no duplicates
    for expected in ("Warden", "Ender Dragon", "Chicken", "Sulfur Cube"):
        assert expected in names


def test_stats_are_on_the_0_10_scale():
    for card in cards.get_all_cards():
        for stat in cards.STATS:
            assert 0 <= card[stat] <= 10, f"{card['name']}.{stat}"


def test_known_card_values():
    warden = cards.get_card("warden")
    assert warden["health"] == 10 and warden["attack"] == 10
    assert warden["rarity"] == 1 and warden["tier"] == "legendary"

    chicken = cards.get_card("Chicken")
    assert chicken["farmable"] == 10 and chicken["rarity"] == 10
    assert chicken["tier"] == "common"

    assert cards.get_card("Herobrine") is None


def test_open_pack_distinct_and_sized():
    rng = random.Random(42)
    pack = cards.open_pack(5, rng)
    assert len(pack) == 5
    assert len({c["name"] for c in pack}) == 5
    with pytest.raises(ValueError):
        cards.open_pack(0)


# --- battle engine ---

def test_new_game_deals_evenly():
    game = battle.new_game(30, random.Random(1))
    assert len(game["player_deck"]) == 15
    assert len(game["cpu_deck"]) == 15
    assert game["turn"] == "player"
    assert not game["finished"]


def test_round_winner_takes_both_cards():
    game = battle.new_game(30, random.Random(1))
    p, c = game["player_deck"][0], game["cpu_deck"][0]
    # find a stat where there is no tie, so someone must win
    stat = next(s for s in cards.STATS if p[s] != c[s])
    expected = "player" if p[stat] > c[stat] else "cpu"
    result = battle.play_round(game, stat)
    assert result["result"] == expected
    winner_deck = game[f"{expected}_deck"]
    assert len(winner_deck) == 16
    assert game["turn"] == expected


def test_tie_sends_cards_to_pot():
    game = battle.new_game(8, random.Random(1))
    # force a tie by planting identical stat values
    game["player_deck"][0] = dict(game["player_deck"][0], speed=5)
    game["cpu_deck"][0] = dict(game["cpu_deck"][0], speed=5)
    result = battle.play_round(game, "speed")
    assert result["result"] == "tie"
    assert len(game["pot"]) == 2
    assert game["turn"] == "player"  # chooser goes again


def test_player_turn_requires_valid_stat():
    game = battle.new_game(10, random.Random(2))
    with pytest.raises(battle.BattleError):
        battle.play_round(game, None)
    with pytest.raises(battle.BattleError):
        battle.play_round(game, "cuteness")


def test_game_plays_to_completion():
    rng = random.Random(7)
    game = battle.new_game(12, rng)
    while not game["finished"]:
        stat = rng.choice(cards.STATS) if game["turn"] == "player" else None
        battle.play_round(game, stat)
    assert game["winner"] in ("player", "cpu", "draw")
    with pytest.raises(battle.BattleError):
        battle.play_round(game, "health")


# --- API ---

def test_cards_endpoints():
    resp = client.get("/cards")
    assert resp.status_code == 200
    assert len(resp.json()) == 81

    resp = client.get("/cards/Creeper")
    assert resp.status_code == 200
    assert resp.json()["attack"] == 8

    assert client.get("/cards/Herobrine").status_code == 404


def test_pack_endpoint():
    resp = client.post("/packs/open?size=5")
    assert resp.status_code == 200
    pulls = resp.json()
    assert len(pulls) == 5
    assert len({c["name"] for c in pulls}) == 5


def test_battle_over_api():
    resp = client.post("/battles", json={"deck_size": 12})
    assert resp.status_code == 200
    state = resp.json()
    game_id = state["id"]
    assert state["player_deck_count"] == 6
    assert state["cpu_deck_count"] == 6
    assert state["player_card"] is not None

    # playing without a stat on our turn is rejected
    resp = client.post(f"/battles/{game_id}/play", json={})
    assert resp.status_code == 400

    # drive the game to the end through the API
    rounds = 0
    while not state["finished"]:
        body = {"stat": "attack"} if state["turn"] == "player" else {}
        resp = client.post(f"/battles/{game_id}/play", json=body)
        assert resp.status_code == 200
        state = resp.json()
        last = state["last_round"]
        assert last["result"] in ("player", "cpu", "tie")
        rounds += 1
        assert rounds <= battle.MAX_ROUNDS + 1
    assert state["winner"] in ("player", "cpu", "draw")

    assert client.get(f"/battles/{game_id}").status_code == 200
    assert client.get("/battles/nope").status_code == 404


def test_game_ui_served():
    resp = client.get("/")
    assert resp.status_code == 200
    assert "Mob Trumps" in resp.text
