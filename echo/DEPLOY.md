# Put Echo on your phone (always-on cloud)

Goal: a permanent HTTPS link you open on your phone anywhere, with a password
lock and storage that survives redeploys. Recommended host: **Fly.io** (free
tier covers a small always-on app with a persistent disk; HTTPS is automatic,
which the mic/voice needs).

## Fastest: one command

After installing the Fly CLI and logging into your account, `deploy.sh` does
everything else — app, persistent disk, secrets, deploy:

```bash
curl -L https://fly.io/install.sh | sh   # install flyctl (once)
fly auth login                           # log into YOUR Fly account (once)
cd echo && ./deploy.sh                   # it asks 4 things, then ships it
```

`fly auth login` is the only step that has to be you — it's the browser sign-in
to your own account. The script handles the rest and prints your live link.

The manual walkthrough below is the same steps, spelled out, if you'd rather run
them yourself or something needs adjusting.

## 1. Install the Fly CLI & sign up
```bash
# macOS
brew install flyctl
# or: curl -L https://fly.io/install.sh | sh

fly auth signup   # or: fly auth login
```

## 2. Launch from this folder
```bash
cd echo
fly launch
```
- When asked, **use the existing `fly.toml`**.
- Pick a **unique app name** (e.g. `echo-yourname`) and a region near you.
- Say **no** to deploying immediately (we'll set secrets first).
- If it asks about a Postgres/Redis database, say **no** — Echo uses SQLite on
  the volume.

## 3. Create the persistent disk + set your secrets
```bash
fly volumes create echo_data --size 1 --region <your-region>

fly secrets set ANTHROPIC_API_KEY=sk-ant-your-key-here
fly secrets set ECHO_PASSWORD=pick-a-strong-password
```
`ECHO_PASSWORD` is what you'll type to unlock Echo on your phone. **Without it,
the app refuses to stay private — always set it before deploying publicly.**

Optional premium extras (skip unless you want them):
```bash
fly secrets set ELEVENLABS_API_KEY=...      # natural voice
fly secrets set OPENAI_API_KEY=...          # Whisper speech-to-text
# email reminders:
fly secrets set SMTP_HOST=smtp.gmail.com SMTP_PORT=587 \
  SMTP_USER=you@gmail.com SMTP_PASS=app-password \
  ECHO_EMAIL_TO=you@gmail.com
```

## 4. Deploy
```bash
fly deploy
```
When it finishes, your link is `https://<your-app-name>.fly.dev`.

## 5. Install it on your phone
1. Open the link in **Safari (iPhone)** or **Chrome (Android)**.
2. Enter your password.
3. **Add to Home Screen** — iPhone: Share → *Add to Home Screen*; Android:
   ⋮ menu → *Install app* / *Add to Home screen*.
4. It now opens full-screen with its own icon, like a native app. 🎉

## Updating later
Push changes to the branch, then `fly deploy` again. Your data (on the volume)
is untouched.

## Notes
- **Voice** works because Fly serves HTTPS. Without premium keys it uses the
  browser's built-in voice; iOS Safari supports speech synthesis and dictation.
- **Costs**: a 256MB shared-CPU machine that auto-stops when idle plus a 1GB
  volume sits within Fly's small-app free allowance for typical personal use.
  Check current Fly pricing for specifics.
- **Backups**: hit *export* in the Memory tab (or `GET /api/export`) any time to
  download your whole brain as JSON.
- Prefer a dashboard over a CLI? `render.yaml` in the repo root deploys to
  Render instead — but its persistent disk needs a paid instance (~$7/mo).
