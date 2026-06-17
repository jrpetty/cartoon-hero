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

## Capacity & topology

One connection per client (star topology), so 8v8 is 16 sockets, not a 240-link
mesh. The relay is stateless beyond room membership; restart it any time between
matches. Multiple independent matches can run at once using different room names.
