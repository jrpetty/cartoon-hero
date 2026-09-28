export const PROTOCOL: number;
export function startServer(
  port?: number,
  host?: string,
  opts?: {
    /** Where the community map pool is kept (default: beside the server, or MAPS_FILE). */
    mapsFile?: string;
    /** Where player ratings are kept (default: beside the server, or PLAYERS_FILE). */
    playersFile?: string;
    /** The game page to serve at `/`; false for relay only. Default: GAME_HTML, server/public/index.html, or dist/. */
    gameHtml?: string | false;
    /** Test knobs. */
    quickCountdownMs?: number;
    pingMs?: number;
    idleMs?: number;
  },
): Promise<{ server: unknown; port: number; close: () => Promise<void> }>;
