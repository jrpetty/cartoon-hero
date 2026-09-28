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

```bash
cd game
fly launch --no-deploy --copy-config     # claim an app name once
fly volumes create bb_data --size 1      # ratings + community maps survive deploys
fly deploy
```

Open `https://<app>.fly.dev` and play. Fly terminates TLS, so the page is
`https://` and online play uses `wss://` to the same address automatically.
Point your own domain at it with `fly certs add play.yourdomain.com`.

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

## Operating it

| | |
|---|---|
| `GET /` | the game |
| `GET /healthz` | `{ ok, rooms, games, players, online, protocol, game, uptime }` — point your host's health check here |
| `GET /rooms` · `/maps` · `/leaderboard` | JSON |
| `PORT`, `HOST` | where to listen (managed hosts set `PORT`) |
| `GAME_HTML` | the game page (default: `server/public/index.html`, then `dist/`) |
| `MAPS_FILE`, `PLAYERS_FILE` | where community maps and ratings are kept (`/data/…` in the container) |

- **One instance.** Rooms and matches live in memory; scale the machine up,
  not out. The relay does no simulation, so a small VM carries a lot.
- **Deploys end running matches.** Deploy when it's quiet. `SIGTERM` closes
  cleanly. A page from before a protocol change is asked to reload.
- **Abuse limits**: messages are rate-limited per connection (clients that
  flood are dropped), chat is capped at five lines per five seconds and 240
  characters, names and room names are cleaned and length-limited, frames
  over 1 MB close the connection, and anything that isn't a map code is
  refused by the pool.
- **Dead connections** (a closed laptop, dropped Wi-Fi) are detected by
  heartbeat within ~45 s and their seat freed; in a match, their units stop.
- **Passwords** on rooms are a gate for friends, not security.

## Private play: LAN, VPN, tunnel

The same server works without a website. Run `node server/server.mjs` on a
machine everyone can reach (LAN, Tailscale/WireGuard/ZeroTier, or a tunnel
such as ngrok or Cloudflare Tunnel) and, in the game, use **Multiplayer →
Play Online** (it asks for the address when the game was opened from a
file) or **Join a Server by address** to go straight into a named room. Use
`ws://<address>:8787` on a LAN or VPN; `wss://` through anything with TLS.
There is also **Quick 1v1 (no server)**: two players swap codes over any chat.
