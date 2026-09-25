export function startServer(
  port?: number,
  host?: string,
): Promise<{ server: unknown; port: number; close: () => Promise<void> }>;
