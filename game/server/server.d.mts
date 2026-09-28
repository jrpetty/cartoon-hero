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
    /** Where the match history is appended (default: beside the server, or MATCHES_FILE). */
    matchesFile?: string;
    /** Where the per-minute activity samples are appended (default: beside the match history, or SAMPLES_FILE). */
    samplesFile?: string;
    /** Admin dashboard token (default ADMIN_TOKEN); empty disables the dashboard. */
    adminToken?: string;
    /** Test knobs. */
    sampleMs?: number;
    quickCountdownMs?: number;
    pingMs?: number;
    idleMs?: number;
  },
): Promise<{ server: unknown; port: number; close: () => Promise<void> }>;
