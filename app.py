from typing import List, Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

import battle
import cards
from database import init_db, add_player, list_players, record_match, get_season
from schemas import (
    PlayerCreate, Player, MatchCreate, Season,
    Card, BattleCreate, BattleMove, BattleState,
)

# initialize database on startup
init_db()

app = FastAPI(title="Cartoon Hero Tracker")


@app.post("/players", response_model=Player)
def create_player(player: PlayerCreate):
    player_id = add_player(
        player.name, player.rating, player.nationality, player.homegrown
    )
    return Player(id=player_id, **player.dict())


@app.get("/players", response_model=List[Player])
def get_players():
    return [Player(**p) for p in list_players()]


@app.post("/matches", response_model=Season)
def add_match(match: MatchCreate):
    record_match(match.result)
    return Season(**get_season())


@app.get("/season", response_model=Season)
def season_status():
    return Season(**get_season())


# --- Minecraft mob card battle system ---

@app.get("/cards", response_model=List[Card])
def list_cards():
    """The full collection: every Minecraft mob card."""
    return cards.get_all_cards()


@app.get("/cards/{name}", response_model=Card)
def get_card(name: str):
    card = cards.get_card(name)
    if card is None:
        raise HTTPException(status_code=404, detail=f"no card named {name!r}")
    return card


@app.post("/packs/open", response_model=List[Card])
def open_pack(size: int = Query(default=5, ge=1, le=10)):
    """Open a booster pack — pulls are weighted by spawn rarity."""
    return cards.open_pack(size)


@app.post("/battles", response_model=BattleState)
def create_battle(config: Optional[BattleCreate] = None):
    config = config or BattleCreate()
    game = battle.new_game(config.deck_size)
    return battle.public_state(game)


@app.get("/battles/{game_id}", response_model=BattleState)
def get_battle(game_id: str):
    game = battle.get_game(game_id)
    if game is None:
        raise HTTPException(status_code=404, detail="battle not found")
    return battle.public_state(game)


@app.post("/battles/{game_id}/play", response_model=BattleState)
def play_battle_round(game_id: str, move: Optional[BattleMove] = None):
    """Play one round. Send {"stat": ...} on your turn; on the CPU's
    turn the body can be empty — the CPU picks its own stat."""
    game = battle.get_game(game_id)
    if game is None:
        raise HTTPException(status_code=404, detail="battle not found")
    stat = move.stat if move else None
    try:
        battle.play_round(game, stat)
    except battle.BattleError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return battle.public_state(game)


# playable web UI
app.mount("/static", StaticFiles(directory="static"), name="static")


@app.get("/", include_in_schema=False)
def game_ui():
    return FileResponse("static/index.html")
