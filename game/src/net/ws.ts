// Client transport for the hosted relay server. Uses the browser's native
// WebSocket (no dependency). The lobby owns onData during matchmaking, then the
// NetSession takes it over for turn/checksum traffic once the match starts.

import { Transport } from "./transport";

export class WSLink implements Transport {
  private ws: WebSocket;
  onData?: (msg: unknown) => void;
  onOpen?: () => void;
  onClose?: () => void;

  constructor(url: string) {
    this.ws = new WebSocket(url);
    this.ws.onopen = () => this.onOpen?.();
    this.ws.onclose = () => this.onClose?.();
    this.ws.onerror = () => this.onClose?.();
    this.ws.onmessage = (e) => {
      try { this.onData?.(JSON.parse(typeof e.data === "string" ? e.data : "")); } catch { /* ignore */ }
    };
  }

  send(msg: unknown) {
    if (this.ws.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(msg));
  }
  get connected(): boolean { return this.ws.readyState === WebSocket.OPEN; }
  close() { try { this.ws.close(); } catch { /* */ } }
}
