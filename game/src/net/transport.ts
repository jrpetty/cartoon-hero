// A minimal duplex message channel. Both the serverless WebRTC PeerLink and the
// server-relayed WSLink implement it, so the lockstep session is agnostic to how
// turns actually travel between players.
export interface Transport {
  send(msg: unknown): void;
  onData?: (msg: unknown) => void;
  onOpen?: () => void;
  onClose?: () => void;
  close(): void;
}
