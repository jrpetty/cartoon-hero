# Banner & Blade — the website and its multiplayer server

`server.mjs` is the whole online side of the game in one **zero-dependency**
Node file (Node 18+, nothing to install). It:

- **serves the game** at `/` — the single-file build — so players just open
  the site;
- runs the **online hub** — a live room list, quick match, custom rooms, a
  lobby per room, a ranked ladder;
- **relays matches**. Games run in deterministic lockstep, so the server never
  simulates anything; it forwards each player's orders and checksums. That's
  why one small machine holds many matches, up to 8 v 8 each.

## Put it on the internet

The repository builds into one container: the game plus the server, on one
port (`Dockerfile` in `game/`).

### Fly.io (recommended)

One-time setup, from the `game/` folder:

```bash
fly launch --no-deploy --copy-config          # pick an app name; keep the existing fly.toml
fly volumes create bb_data --size 1           # stats, ratings, match history, community maps
fly secrets set ADMIN_TOKEN=$(openssl rand -hex 24)   # turns on /admin — keep this value
fly deploy
```

Then:

- the game: `https://<app>.fly.dev`
- your dashboard: `https://<app>.fly.dev/admin` (paste the ADMIN_TOKEN)
- health: `https://<app>.fly.dev/healthz`

Every later update is just `fly deploy`. Your own domain:
`fly certs add play.yourdomain.com` and point the DNS record it gives you.
Fly terminates TLS, so the page is `https://` and online play is `wss://` to
the same address with nothing to configure.

Useful: `fly logs` (live server log), `fly status`, `fly ssh console` (a
shell on the machine; data is in `/data`), `fly volumes snapshots list
bb_data` (Fly snapshots the volume daily).

### Render

`render.yaml` at the repository root is a Blueprint: **New → Blueprint →**
this repo. It uses the Starter plan with a 1 GB disk — the free plan sleeps,
and a sleeping site drops live matches.

### Any server with Docker

```bash
cd game
docker build -t banner-and-blade .
docker run -d --restart unless-stopped -p 8787:8787 -v bb-data:/data banner-and-blade
```

Put HTTPS in front with Caddy (it gets the certificate itself and passes
WebSockets through untouched):

```
play.yourdomain.com {
    reverse_proxy localhost:8787
}
```

### Without Docker

```bash
cd game
npm ci && npm run build:single      # builds dist/banner-and-blade.html
node server/server.mjs              # serves it on :8787
```

> A page served over `https://` may only open `wss://` — which is why the site
> and the server are the same address: the game connects back to whatever
> served it, and TLS in front covers both.

## What players see

1. **Open the site.** First visit: claim a commander, then choose a free
   faction (the Factions book explains each). The rest cost 1500 renown.
2. **Multiplayer → Play Online.** No address to type — the game connects to
   the site it came from. Pick a name (kept by the browser).
3. **The hub** shows how many people are online, your rating, the open rooms
   (🔒 for passworded ones, "In battle" for running ones), quick match, a
   create-room form, and the top of the ladder.
4. **Quick match** (1 v 1, 2 v 2, 4-player free-for-all): the server waits for
   enough players, makes a room, seats them in teams, picks a built-in
   battlefield, counts down four seconds and starts. These games are
   **ranked**.
5. **Custom rooms**: the creator is the host ⭐ and sets the layout
   (free-for-all, or 2/3/4 teams in join order — eight players and two teams
   is players 1–4 against 5–8), moves or removes players, and picks the
   battlefield: built-in, one of their own published maps, or one from this
   server's community pool. Players choose a team and a faction (only ones
   they own), chat, ready up. Up to 16 players; others can join to watch.
6. **After the match** the game returns to the hub.
7. **Casting**: "Watch" on an open room joins it as an observer. In the lobby
   the caster picks a broadcast delay (live, 30 s, 1, 2 or 5 minutes) and
   gets the full caster view once the match starts — player bar, army and
   economy comparisons, production, vision per player, an auto-director
   camera, an event feed, graphs and a clean feed for streaming. The delay
   runs on the caster's machine; the server just relays turns as usual.
   Every match is also kept as a replay on each player's device.

## Ranked results and the ladder

Every client simulates the same match, so each knows who won. When a ranked
match ends, each client reports the winner; once everyone still connected
has, the server applies Elo (K = 32, team average) if the reports agree —
and rates nothing if they don't. A player who quits counts as having lost:
the ones who stayed report the result. Ratings are at `/leaderboard`.

A player's identity is a random id kept in their browser, plus a name. There
are no passwords and no accounts: clearing browser data starts afresh. Renown,
unlocked factions and War Chests are kept in the browser too — the server does
not verify them. Online matches are all-Common, without commanders or boons,
so nothing bought or unboxed decides a ranked game; factions are a choice of
play style, not power.

## Community maps

Players publish maps they've made from the lobby's battlefield picker. They
are saved in `MAPS_FILE`, deduplicated by content, capped at 500, and listed
at `/maps`. The host's chosen map travels with the match start, so nobody
else needs a copy.

## The admin dashboard — `/admin`

Set `ADMIN_TOKEN` (a long random string) and open `/admin`. Without it the
dashboard is off. Wrong guesses are limited to ten per address per ten
minutes. Everything refreshes by itself: "Right now" every 3 seconds, the
rest every 15. Pick 24 hours, 7 days, 30 days or all time at the top.

**Right now**: players online (and the peak), in matches, in lobbies, just
browsing, waiting in each quick-match queue; live games; connections since
start; uptime; event-loop lag and memory (lag creeping above ~50 ms means the
machine is too small).

**For the chosen period**:
- matches, how many finished / were abandoned / disputed, ranked vs custom;
- average, median and longest game; players per match;
- active players (today, 7 and 30 days), new and returning players;
- quit rate (matches someone left early) and desync rate (should be 0);
- a chart of players online and games running (one sample a minute, kept on
  disk, so it survives deploys), matches per day with unique players, matches
  per hour for the last two days, and which hours (UTC) people play;
- **factions**: picks and share, how often chosen vs taken at Random, games,
  wins, win rate, **1 v 1 win rate**, average game length and average length of
  its wins — plus a **match-up grid**: each faction's 1 v 1 win rate against
  each other faction, with game counts;
- **maps**: plays, share, finished games, average length, average players;
  custom maps with their author;
- **game length**: a histogram, and averages by kind (quick 1v1, 2v2, FFA,
  custom) and by player count; formats played (1 v 1, 2 v 2, 3 v 3…);
- **players**: total, rated, average rating, the rating spread, the
  leaderboard, the most active players;
- **rooms right now**, with a **Close** button;
- **recent matches**: kind, map, format, every side with its factions and
  names (winners in bold), result, length, quitters;
- **server health**: messages and traffic per minute, turns relayed, chat
  lines, kicks, maps published, rate-limited messages, dead connections
  dropped, errors (with the last one), storage;
- **announce**: send a message to everyone online — it shows in the hub and
  as an alert inside running matches ("restarting in 5 minutes");
- **Export CSV**: every match ever recorded, for a spreadsheet.

How the numbers are gathered: each match is recorded when it starts (room,
kind, map, sides, each player's faction). Clients report the factions
actually in play (so "Random" resolves to the real one), the winner and the
game-clock length when it ends; the server keeps the winner only when the
reports agree. A match everyone leaves before a winner is "abandoned"; a
server restart records running matches as abandoned too. Player ids are
hashed before they are written to the match history and never shown.

## Operating it

| | |
|---|---|
| `GET /` | the game (gzip, ETag — an unchanged page is a 304) |
| `GET /admin` | the dashboard (needs `ADMIN_TOKEN`) |
| `GET /healthz` | `{ ok, rooms, games, players, online, protocol, game, uptime }` — Fly's health check |
| `GET /rooms` · `/maps` · `/leaderboard` | public JSON |
| `/admin/api/live` · `stats?range=24h\|7d\|30d\|all` · `matches?n=` · `matches.csv` · `POST announce` · `POST close-room` | admin JSON, `Authorization: Bearer <ADMIN_TOKEN>` |
| `PORT`, `HOST` | where to listen (managed hosts set `PORT`) |
| `ADMIN_TOKEN` | turns on the dashboard |
| `GAME_HTML` | the game page (default: `server/public/index.html`, then `dist/`) |
| `MAPS_FILE`, `PLAYERS_FILE`, `MATCHES_FILE`, `SAMPLES_FILE` | data files (all in `/data` in the container) |
| `RUN_AS=uid:gid` | start as root, take ownership of the data files, then drop to this user (the container uses the `node` user) |

- **Measured**: 400 simulated players in 150 simultaneous matches, each
  sending 20 turns a second, on one process: event-loop lag peaked at 10 ms,
  84 MB of memory, no errors, every match recorded. The 512 MB machine in
  `fly.toml` has plenty of room; scale the machine up, not out — rooms and
  matches live in memory on one instance.
- **Data is safe across deploys and crashes**: files are written to a
  temporary name and renamed into place; the match history and activity
  samples are append-only; on `SIGTERM` everything is flushed first.
- **Deploys end running matches.** Announce first, deploy when it's quiet.
  A page from before a protocol change is asked to reload.
- **Abuse limits**: per-connection message rate limit (floods are
  disconnected), chat capped at five lines per five seconds and 240
  characters, names and room names cleaned and length-limited, frames over
  1 MB close the connection, the map pool refuses anything that isn't a map.
- **Dead connections** (a closed laptop, dropped Wi-Fi) are found by
  heartbeat within ~45 s and their seat freed.
- **Room passwords** are a gate for friends, not security.

## Private play: LAN, VPN, tunnel

The same server works without a website. Run `node server/server.mjs` on a
machine everyone can reach (LAN, Tailscale/WireGuard/ZeroTier, or a tunnel
such as ngrok or Cloudflare Tunnel) and, in the game, use **Multiplayer →
Play Online** (it asks for the address when the game was opened from a
file) or **Join a Server by address** to go straight into a named room. Use
`ws://<address>:8787` on a LAN or VPN; `wss://` through anything with TLS.
There is also **Quick 1v1 (no server)**: two players swap codes over any chat.
