// Multiplayer lobby — a small DOM overlay for the serverless WebRTC handshake.
// Canvas immediate-mode UI can't host editable text, so the copy/paste code
// exchange lives in real DOM elements layered over the game. Nothing here runs
// until the player opens it, so headless boot is unaffected.

import { PeerLink } from "../net/webrtc";

export interface NetStart { link: PeerLink; isHost: boolean; seed: number; }

export class NetLobby {
  private el: HTMLDivElement | null = null;
  private link: PeerLink | null = null;
  private onStart?: (s: NetStart) => void;
  open(onStart: (s: NetStart) => void) {
    this.onStart = onStart;
    this.build();
    this.showHome();
  }
  close() {
    this.el?.remove();
    this.el = null;
  }

  // --- DOM helpers ---------------------------------------------------------
  private build() {
    const el = document.createElement("div");
    el.style.cssText = [
      "position:fixed", "inset:0", "display:flex", "align-items:center", "justify-content:center",
      "background:rgba(8,6,3,0.82)", "z-index:50", "font-family:system-ui,sans-serif", "color:#e8ddc4",
    ].join(";");
    const panel = document.createElement("div");
    panel.id = "mp-panel";
    panel.style.cssText = [
      "width:560px", "max-width:92vw", "background:#221b12", "border:2px solid #c9a24a",
      "border-radius:12px", "padding:24px", "box-shadow:0 12px 40px rgba(0,0,0,0.6)",
    ].join(";");
    el.appendChild(panel);
    document.body.appendChild(el);
    this.el = el;
  }
  private panel(): HTMLDivElement { return this.el!.querySelector("#mp-panel") as HTMLDivElement; }
  private h(html: string) { this.panel().innerHTML = html; }
  private btn(label: string, primary = true): HTMLButtonElement {
    const b = document.createElement("button");
    b.textContent = label;
    b.style.cssText = [
      "padding:10px 16px", "margin:6px 8px 6px 0", "border-radius:8px", "cursor:pointer",
      "border:1px solid #c9a24a", "font-size:15px", "font-weight:600",
      primary ? "background:#c9a24a;color:#221b12" : "background:#2e2618;color:#e8ddc4",
    ].join(";");
    return b;
  }
  private area(value: string, readonly: boolean): HTMLTextAreaElement {
    const t = document.createElement("textarea");
    t.value = value;
    t.readOnly = readonly;
    t.spellcheck = false;
    t.style.cssText = [
      "width:100%", "height:84px", "margin:8px 0", "box-sizing:border-box", "resize:none",
      "background:#15110a", "color:#cabfa4", "border:1px solid #5a4a2a", "border-radius:6px",
      "padding:8px", "font-family:monospace", "font-size:11px",
    ].join(";");
    return t;
  }
  private title(t: string) {
    return `<div style="font-size:22px;font-weight:700;color:#ffe9b0;margin-bottom:4px">${t}</div>`;
  }

  // --- Screens -------------------------------------------------------------
  private showHome() {
    this.h(this.title("Multiplayer — 1v1") +
      `<div style="color:#bdb49a;font-size:13px;margin-bottom:14px">No server needed: one of you hosts and shares a code, the other joins and sends a code back. Use any chat to swap the codes. Best on the same network.</div>`);
    const p = this.panel();
    const host = this.btn("Host a game");
    const join = this.btn("Join a game", false);
    const cancel = this.btn("Cancel", false);
    host.onclick = () => this.startHost();
    join.onclick = () => this.startJoin();
    cancel.onclick = () => this.close();
    p.append(host, join, cancel);
  }

  private async startHost() {
    this.h(this.title("Hosting…") + `<div style="color:#bdb49a;font-size:13px">Generating your invite code…</div>`);
    const link = new PeerLink();
    this.link = link;
    const seed = (Math.random() * 1e9) | 0;
    const code = await link.host();
    link.onOpen = () => {
      // Tell the joiner which match to build, then start ours.
      link.send({ k: "start", seed });
      this.finish({ link, isHost: true, seed });
    };
    this.renderHost(code, seed);
  }

  private renderHost(code: string, seed: number) {
    this.h(this.title("Hosting — 1v1") +
      `<div style="color:#bdb49a;font-size:13px">1) Send this invite code to your opponent.</div>`);
    const p = this.panel();
    const out = this.area(code, true);
    const copy = this.btn("Copy invite code");
    copy.onclick = () => { out.select(); navigator.clipboard?.writeText(code); copy.textContent = "Copied!"; };
    const lbl = document.createElement("div");
    lbl.style.cssText = "color:#bdb49a;font-size:13px;margin-top:10px";
    lbl.textContent = "2) Paste their reply code here and connect.";
    const ans = this.area("", false);
    const connect = this.btn("Connect");
    const status = document.createElement("div");
    status.style.cssText = "color:#9fd08a;font-size:13px;margin-top:8px";
    connect.onclick = async () => {
      try { status.textContent = "Connecting…"; await this.link!.accept(ans.value); }
      catch { status.textContent = "That reply code didn't work — check it and try again."; }
    };
    const cancel = this.btn("Cancel", false);
    cancel.onclick = () => this.cancel();
    p.append(out, copy, lbl, ans, connect, cancel, status);
  }

  private async startJoin() {
    this.h(this.title("Join — 1v1") + `<div style="color:#bdb49a;font-size:13px">Paste the host's invite code, then send them your reply.</div>`);
    const p = this.panel();
    const inp = this.area("", false);
    const gen = this.btn("Generate reply code");
    const status = document.createElement("div");
    status.style.cssText = "color:#9fd08a;font-size:13px;margin-top:8px";
    const cancel = this.btn("Cancel", false);
    cancel.onclick = () => this.cancel();
    gen.onclick = async () => {
      const link = new PeerLink();
      this.link = link;
      // Wait for the host's start message to learn the seed, then begin.
      link.onData = (m) => {
        const msg = m as { k?: string; seed?: number };
        if (msg.k === "start" && typeof msg.seed === "number") this.finish({ link, isHost: false, seed: msg.seed });
      };
      try {
        const reply = await link.join(inp.value);
        this.renderJoinReply(reply);
      } catch {
        status.textContent = "That invite code didn't work — check it and try again.";
      }
    };
    p.append(inp, gen, cancel, status);
  }

  private renderJoinReply(reply: string) {
    this.h(this.title("Join — 1v1") + `<div style="color:#bdb49a;font-size:13px">Send this reply code back to the host, then wait — the match starts automatically once they connect.</div>`);
    const p = this.panel();
    const out = this.area(reply, true);
    const copy = this.btn("Copy reply code");
    copy.onclick = () => { out.select(); navigator.clipboard?.writeText(reply); copy.textContent = "Copied!"; };
    const status = document.createElement("div");
    status.style.cssText = "color:#9fd08a;font-size:13px;margin-top:8px";
    status.textContent = "Waiting for the host to connect…";
    const cancel = this.btn("Cancel", false);
    cancel.onclick = () => this.cancel();
    p.append(out, copy, status, cancel);
  }

  private cancel() {
    try { this.link?.close(); } catch { /* */ }
    this.link = null;
    this.close();
  }

  private finish(s: NetStart) {
    const cb = this.onStart;
    this.close();
    cb?.(s);
  }
}
