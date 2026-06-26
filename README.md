# cartoon-hero

This repository contains three independent projects:

| Directory | What it is |
| --- | --- |
| (root) | A minimal **FastAPI** backend for tracking a FIFA-style league season (below). |
| [`mc-mod/`](./mc-mod) | **Voxelia MMO** — a **NeoForge Minecraft mod** (MC 1.21.1) that adds an MMO progression layer (skills, XP/levels, quests, combat scaling, HUD). Builds to a `.jar`. |
| [`mmo/`](./mmo) | **Voxelia** — a browser-based Minecraft-style MMO prototype (Three.js client + authoritative Node WebSocket server) used to design and validate the same systems. |

---

## League tracker (FastAPI)

This project provides a minimal FastAPI backend and simple interface for tracking a FIFA-style league season, players and match results.

## Development

Create a virtual environment and install dependencies:

```bash
pip install -r requirements.txt
```

Run the API locally:

```bash
uvicorn app:app --reload
```

Interactive docs are available at <http://127.0.0.1:8000/docs>.

## Testing

```bash
pytest
```
