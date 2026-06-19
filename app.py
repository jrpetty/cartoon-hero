from pathlib import Path
from typing import List
from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from database import init_db, add_player, list_players, record_match, get_season
from schemas import PlayerCreate, Player, MatchCreate, Season

# initialize database on startup
init_db()

app = FastAPI(title="Cartoon Hero Tracker")

STATIC_DIR = Path(__file__).with_name("static")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


@app.get("/", include_in_schema=False)
def dashboard():
    """Serve the season dashboard."""
    return FileResponse(STATIC_DIR / "index.html")


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
