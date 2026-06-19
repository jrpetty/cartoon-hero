# cartoon-hero

This project provides a minimal FastAPI backend and simple interface for tracking a FIFA-style league season, players and match results.

## Development

Create a virtual environment and install dependencies:

```bash
pip install -r requirements.txt
```

Run everything with one command (installs deps if needed, then starts the server):

```bash
./run.sh
```

Or run the API directly:

```bash
uvicorn app:app --reload
```

On first launch the database is automatically populated with a demo squad and a
season of results, so the dashboard has something to explore immediately. Reseed
at any time with:

```bash
python seed.py
```

Then open the dashboard at <http://127.0.0.1:8000/> to view season stats, manage
your squad and record match results. Interactive API docs are available at
<http://127.0.0.1:8000/docs>.

## Dashboard

The dashboard is a static frontend (`static/index.html`, `styles.css`, `app.js`)
served by the FastAPI app. It shows live season totals (games, points,
points-per-game, rating cap, budget), a sortable squad table, and controls to log
Win/Draw/Loss results and add new players. It talks to the existing
`/players`, `/matches` and `/season` endpoints — no build step required.

## Testing

```bash
pytest
```
