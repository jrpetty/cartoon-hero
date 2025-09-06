from typing import List
from fastapi import FastAPI
from database import init_db, add_player, list_players, record_match, get_season
from schemas import PlayerCreate, Player, MatchCreate, Season

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
