# cartoon-hero

## ⚔ Banner & Blade (game/)

A browser-based, stylized-fantasy RTS with skirmish AI and a chest-unlock
collection meta. See [game/README.md](game/README.md) for how to run and play.

```bash
cd game && npm install && npm run dev
```

## League tracker (legacy)

This project also contains a minimal FastAPI backend and simple interface for tracking a FIFA-style league season, players and match results.

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
