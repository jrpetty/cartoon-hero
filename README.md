# cartoon-hero

This project provides a minimal FastAPI backend and simple interface for tracking a FIFA-style league season, players and match results.

## Automata Minecraft server

This repo also hosts **Automata**, a Minecraft (Fabric 1.21.1) server that runs
only a mod written from scratch by Claude. It reimagines the core loop: the
crafting table and furnace are replaced by self-running machines you automate
with hoppers. See [`minecraft/README.md`](minecraft/README.md) for the design,
build, and server setup instructions.

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
