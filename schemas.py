from pydantic import BaseModel, Field
from typing import List, Optional


class PlayerCreate(BaseModel):
    name: str
    rating: int
    nationality: str
    homegrown: bool = False


class Player(PlayerCreate):
    id: int
    matches_played: int = 0
    injury_games_remaining: int = 0

    class Config:
        orm_mode = True


class MatchCreate(BaseModel):
    result: str  # 'W', 'D', 'L'


class Season(BaseModel):
    games_played: int
    points: int
    rating_cap: int
    budget: int


class PlayerList(BaseModel):
    players: List[Player]


# --- Minecraft mob card battle system ---

class Card(BaseModel):
    name: str
    health: int
    attack: int
    size: int
    speed: int
    farmable: int
    rarity: int
    tier: str


class BattleCreate(BaseModel):
    deck_size: int = Field(default=30, ge=4, le=81)


class BattleMove(BaseModel):
    stat: Optional[str] = None  # required on the player's turn


class RoundResult(BaseModel):
    round: int
    chooser: str
    stat: str
    player_card: Card
    cpu_card: Card
    result: str  # 'player', 'cpu' or 'tie'


class BattleState(BaseModel):
    id: str
    round: int
    turn: str
    finished: bool
    winner: Optional[str] = None
    player_deck_count: int
    cpu_deck_count: int
    pot_count: int
    player_card: Optional[Card] = None
    last_round: Optional[RoundResult] = None
