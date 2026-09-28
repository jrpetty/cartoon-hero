export function startServer(
  port?: number,
  host?: string,
  /** mapsFile: where the community map pool is kept (default: beside the server, or MAPS_FILE). */
  opts?: { mapsFile?: string },
): Promise<{ server: unknown; port: number; close: () => Promise<void> }>;
