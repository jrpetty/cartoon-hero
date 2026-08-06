# Banner & Blade — multiplayer relay server

A tiny, **zero-dependency** WebSocket relay for networked matches (2–16 players,
up to **8 vs 8**). It only forwards each player's lockstep turns to the others —
it never simulates the game — so it's lightweight and easy to host anywhere your
players can reach, including a box on your **VPN**.

## Run it

Requires Node 18+ (uses only built-in modules — nothing to install).

```bash
node server/server.mjs            # listens on 0.0.0.0:8787
node server/server.mjs 9000       # custom port
```

You'll see:

```
Banner & Blade relay listening on ws://0.0.0.0:8787  (up to 16 players/room)
```

Sanity-check it's up from a browser: open `http://<host>:8787/` — it returns a
short status line (room/player counts).

## Hosting over a VPN

1. Join your VPN on the machine that will run the server (Tailscale, WireGuard,
   ZeroTier, Hamachi, etc.).
2. Find that machine's **VPN IP** (e.g. Tailscale `100.x.y.z`, WireGuard
   `10.0.0.5`).
3. Start the server: `node server/server.mjs`.
4. Make sure every player is on the same VPN, then in the game choose
   **Multiplayer → Join a Server** and enter:

   ```
   ws://<VPN-IP>:8787
   ```

   Everyone uses the **same room name** (default `main`). One player is the host
   (marked ⭐) — they pick sides and press **Start Match**.

Notes:
- Use `ws://` (not `wss://`). The VPN provides the encryption/trust boundary;
  the relay speaks plain WebSocket. If you expose it on the public internet
  instead, put it behind a TLS-terminating reverse proxy and use `wss://`.
- If a player can't connect, check the VPN IP, that the port isn't firewalled,
  and that the server is actually listening (the `http://…` status check above).
- If someone disconnects mid-match, the rest keep playing — that team's units
  simply go idle.

## Room passwords

Rooms can be locked. The **first** player to enter a room sets its password (the
"Password" field on the Join-a-Server screen); everyone else must enter the same
one or they're rejected with "Wrong room password." Leave it blank for an open
room. The password lives only in memory and resets once the room empties, so it's
a lightweight gate for friends — not account security.

## Hosting on the public internet

The server already binds `0.0.0.0`, so to let friends connect over the internet
(no VPN) you just need to make your machine reachable. Easiest → most robust:

1. **A tunnel (no router setup, works behind CGNAT).** Run a tool like
   **playit.gg**, **ngrok** (`ngrok tcp 8787`) or **Cloudflare Tunnel**; it gives
   you a public address that forwards to your local `:8787`. Friends use
   `ws://<that-address>`.
2. **Port forwarding.** Give your PC a static LAN IP, forward external TCP **8787**
   → that IP:8787 on your router, allow Node through the firewall, and share
   `ws://<your-public-ip>:8787`. Verify from outside with
   `http://<public-ip>:8787/`. (Won't work if your ISP uses CGNAT — use a tunnel.)
3. **A cheap VPS.** Run the server on a $4–6/mo box for an always-on public IP.

Notes:
- Open the **game from the local HTML file** so the browser allows plain `ws://`.
  A page served over `https://` can only use `wss://` (put a TLS reverse proxy /
  Caddy in front, or use a tunnel that provides `wss://`).
- Always set a room password when exposing the server publicly.

## Deploying it somewhere permanent

Everything the code can do is done: the server reads `PORT` and `HOST` from the
environment, answers `/healthz` with JSON, and shuts down cleanly on `SIGTERM`
so a deploy doesn't hang waiting to be killed. What is left is choosing a host,
which is a decision about money and location rather than about code.

There is one rule that decides everything else:

> **A page served over `https://` may only open `wss://`.** Browsers block a
> plain `ws://` connection from a secure page, and there is no flag or code
> change that gets around it.

So if the game is opened from `https://` — anything hosted, including GitHub
Pages — the relay needs TLS. You do not want to be managing certificates for
this, so pick a host that terminates TLS for you.

| | good for | TLS | cost |
|---|---|---|---|
| **Fly.io** (`fly.toml` here) | the default | yes, free | free tier; sleeps to zero when empty |
| **Render** (`render.yaml` here) | click-to-deploy from the repo | yes, free | free tier; cold starts |
| **Any VPS + Caddy** | you already have a box | yes, two lines of Caddyfile | ~$4/mo |
| **A tunnel** (Cloudflare, ngrok) | trying it out this evening | yes, given by the tunnel | free |
| **Your own machine, `ws://`** | a LAN or a VPN | not needed | free |

```bash
# Fly — one command after `fly launch --no-deploy`
cd server && fly deploy

# Docker, anywhere
docker build -t bb-relay server && docker run -p 8787:8787 bb-relay

# Nothing at all
npm run serve
```

Then in the game: **Multiplayer → Join a Server**, and enter `wss://<host>` (or
`ws://<host>:8787` on a LAN or VPN). Set a room password if it is reachable from
the public internet.

If you are putting it behind your own reverse proxy, the only thing that needs
care is that WebSocket upgrades pass through untouched. Caddy does it by
default:

```
relay.example.com {
    reverse_proxy localhost:8787
}
```

## Capacity & topology

One connection per client (star topology), so 8v8 is 16 sockets, not a 240-link
mesh. The relay is stateless beyond room membership; restart it any time between
matches. Multiple independent matches can run at once using different room names.
