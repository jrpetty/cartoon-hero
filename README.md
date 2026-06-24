# cartoon-hero

This project provides a minimal FastAPI backend and simple interface for tracking a FIFA-style league season, players and match results.

## ClaudeCraft Minecraft server

This repo also hosts **ClaudeCraft**, a Minecraft (Fabric 1.21.1) server that
runs only mods written from scratch by Claude. See
[`minecraft/README.md`](minecraft/README.md) for the mod, build, and server
setup instructions.

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
