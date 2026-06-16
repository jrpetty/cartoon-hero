// Serverless P2P transport for 1v1 multiplayer. Two browsers exchange a single
// connection "code" each (base64 SDP + gathered ICE candidates) out-of-band —
// paste into a chat, no signaling server required, which keeps the game a single
// self-contained file. A public STUN server is used so it works beyond the LAN
// where the network allows it (no TURN, so symmetric-NAT pairs may fail).
//
// Browser-only: nothing here touches WebRTC at import time, so headless boot is
// unaffected — a PeerLink is constructed only when the player hosts/joins.

const ICE = { iceServers: [{ urls: "stun:stun.l.google.com:19302" }] };

function encode(desc: RTCSessionDescription | null): string {
  if (!desc) return "";
  return btoa(JSON.stringify({ type: desc.type, sdp: desc.sdp }));
}
function decode(code: string): RTCSessionDescriptionInit {
  return JSON.parse(atob(code.trim()));
}

export class PeerLink {
  pc: RTCPeerConnection;
  private channel: RTCDataChannel | null = null;
  onOpen?: () => void;
  onData?: (msg: unknown) => void;
  onClose?: () => void;

  constructor() {
    this.pc = new RTCPeerConnection(ICE);
    this.pc.ondatachannel = (e) => this.bind(e.channel); // joiner receives the channel
    this.pc.onconnectionstatechange = () => {
      const s = this.pc.connectionState;
      if (s === "disconnected" || s === "failed" || s === "closed") this.onClose?.();
    };
  }

  private bind(ch: RTCDataChannel) {
    this.channel = ch;
    ch.onopen = () => this.onOpen?.();
    ch.onclose = () => this.onClose?.();
    ch.onmessage = (e) => {
      try { this.onData?.(JSON.parse(e.data)); } catch { /* ignore malformed */ }
    };
  }

  /** Wait until all ICE candidates are gathered so the code is self-contained. */
  private iceComplete(): Promise<void> {
    return new Promise((res) => {
      if (this.pc.iceGatheringState === "complete") return res();
      const check = () => {
        if (this.pc.iceGatheringState === "complete") {
          this.pc.removeEventListener("icegatheringstatechange", check);
          res();
        }
      };
      this.pc.addEventListener("icegatheringstatechange", check);
      // Safety: some browsers never fire "complete"; cap the wait.
      setTimeout(res, 2500);
    });
  }

  /** HOST: create the data channel + offer. Returns the code to share. */
  async host(): Promise<string> {
    this.bind(this.pc.createDataChannel("game", { ordered: true }));
    const offer = await this.pc.createOffer();
    await this.pc.setLocalDescription(offer);
    await this.iceComplete();
    return encode(this.pc.localDescription);
  }

  /** JOINER: consume the host's code, return your answer code to send back. */
  async join(hostCode: string): Promise<string> {
    await this.pc.setRemoteDescription(decode(hostCode));
    const answer = await this.pc.createAnswer();
    await this.pc.setLocalDescription(answer);
    await this.iceComplete();
    return encode(this.pc.localDescription);
  }

  /** HOST: consume the joiner's answer code to finish the handshake. */
  async accept(answerCode: string): Promise<void> {
    await this.pc.setRemoteDescription(decode(answerCode));
  }

  send(msg: unknown) {
    if (this.channel && this.channel.readyState === "open") this.channel.send(JSON.stringify(msg));
  }

  get connected(): boolean {
    return this.channel?.readyState === "open";
  }

  close() {
    try { this.channel?.close(); } catch { /* */ }
    try { this.pc.close(); } catch { /* */ }
  }
}
