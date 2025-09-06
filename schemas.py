from pydantic import BaseModel
from typing import List


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
