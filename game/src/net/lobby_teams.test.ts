import { describe, expect, it, afterAll, beforeAll } from "vitest";
// Node built-ins; the project has no @types/node.
// @ts-ignore
import { mkdtempSync, existsSync } from "fs";
// @ts-ignore
import { tmpdir } from "os";
// @ts-ignore
import { join } from "path";
import { startServer } from "../../server/server.mjs";
import { newCustomMap, serialiseMap } from "../maps/custom";

/**
 * Online team setup: the host lays seats out in organised blocks (1–4 against
 * 5–8, or four pairs, or everyone alone), anyone can be moved to any team, and
 * the match carries the host's battlefield and everyone's faction. Players can
 * publish maps to the server's community pool for everyone there to use.
 */

const flush = (ms = 30) => new Promise((r) => setTimeout(r, ms));
type Msg = Record<string, unknown> & { t: string };

function client(url: string, name: string, room: string, extra: Record<string, unknown> = {}) {
  const ws = new WebSocket(url);
  const got: Msg[] = [];
  ws.onmessage = (e) => got.push(JSON.parse(e.data as string));
  ws.onopen = () => ws.send(JSON.stringify({ t: "hello", name, room, ...extra }));
  return {
    ws, got,
    send: (m: Msg) => ws.send(JSON.stringify(m)),
    last: (t: string) => [...got].reverse().find((m) => m.t === t) as Msg | undefined,
  };
}

let srv: { port: number; close: () => Promise<void> };
let mapsFile: string;
const url = () => `ws://127.0.0.1:${srv.port}`;
beforeAll(async () => {
  mapsFile = join(mkdtempSync(join(tmpdir(), "bb-maps-")), "maps.json");
  srv = await startServer(0, "127.0.0.1", { mapsFile });
});
afterAll(async () => { await srv.close(); });

async function room(name: string, n: number) {
  const cs = [];
  for (let i = 0; i < n; i++) { cs.push(client(url(), `P${i + 1}`, name)); await flush(15); }
  await flush();
  return cs;
}
const close = (cs: { ws: WebSocket }[]) => cs.forEach((c) => c.ws.close());

/** Group start-message seats into their alliances, in seat (join) order. */
function blocks(start: Msg): number[][] {
  const st = start.slotTeams as { slot: number; team: number }[];
  const al = start.alliances as number[];
  const by = new Map<number, number[]>();
  for (const { slot, team } of [...st].sort((a, b) => a.slot - b.slot)) {
    const g = by.get(al[team]) ?? [];
    g.push(slot + 1);
    by.set(al[team], g);
  }
  return [...by.values()];
}

describe("Organised teams online", () => {
  it("puts players 1-4 on team 1 and 5-8 on team 2", async () => {
    const cs = await room("blocks2", 8);
    cs[0].send({ t: "layout", teams: 2 });
    await flush();
    cs[0].send({ t: "start" });
    await flush();
    expect(blocks(cs[3].last("start")!)).toEqual([[1, 2, 3, 4], [5, 6, 7, 8]]);
    close(cs);
  });

  it("makes four pairs, or everyone for themselves", async () => {
    const cs = await room("blocks4", 8);
    cs[0].send({ t: "layout", teams: 4 });
    await flush();
    const lobby = cs[5].last("lobby")!;
    expect((lobby.players as { team: number }[]).map((p) => p.team)).toEqual([1, 1, 2, 2, 3, 3, 4, 4]);
    cs[0].send({ t: "layout", teams: 0 });
    await flush();
    cs[0].send({ t: "start" });
    await flush();
    expect(blocks(cs[0].last("start")!)).toHaveLength(8);
    close(cs);
  });

  it("lets a player pick a team and the host move anyone", async () => {
    const cs = await room("moves", 4);
    cs[0].send({ t: "layout", teams: 2 }); // 1,1,2,2
    await flush();
    cs[3].send({ t: "team", team: 3 }); // P4 starts a third team
    cs[0].send({ t: "team", slot: 1, team: 3 }); // host moves P2 to it
    cs[2].send({ t: "team", slot: 0, team: 2 }); // not the host — ignored
    await flush();
    const teams = (cs[1].last("lobby")!.players as { team: number }[]).map((p) => p.team);
    expect(teams).toEqual([1, 3, 2, 3]);
    close(cs);
  });

  it("won't start with everyone on one side", async () => {
    const cs = await room("oneside", 3);
    for (let i = 0; i < 3; i++) cs[i].send({ t: "team", team: 1 });
    await flush();
    cs[0].send({ t: "start" });
    await flush();
    expect(cs[1].last("start")).toBeUndefined();
    close(cs);
  });

  it("sends the host's battlefield and every faction with the start", async () => {
    const cs = [client(url(), "Host", "carry", { faction: "legion" })];
    await flush(15);
    cs.push(client(url(), "Guest", "carry"));
    await flush();
    cs[1].send({ t: "faction", faction: "khanate" });
    const code = serialiseMap(newCustomMap("Ford", 64));
    cs[0].send({ t: "map", map: { id: "custom_x", name: "Ford", author: "Host", code } });
    cs[1].send({ t: "map", map: { id: "highlands", name: "Highlands" } }); // only the host chooses
    await flush();
    expect((cs[1].last("lobby")!.map as { name: string }).name).toBe("Ford");
    cs[0].send({ t: "start" });
    await flush();
    const start = cs[1].last("start")!;
    expect((start.map as { code: string }).code).toBe(code);
    expect(start.factions).toEqual(["legion", "khanate"]);
    close(cs);
  });
});

describe("The community map pool", () => {
  it("takes a published map, lists it, hands its code back, and keeps it", async () => {
    const a = client(url(), "Maker", "pool");
    await flush();
    const code = serialiseMap(newCustomMap("Twin Rivers", 96));
    a.send({ t: "publish", map: { code, name: "Twin Rivers", author: "Maker", minPlayers: 2, maxPlayers: 4, cols: 96 } });
    await flush();
    const listed = (a.last("maps")!.maps as { id: string; name: string; author: string }[]);
    const entry = listed.find((m) => m.name === "Twin Rivers")!;
    expect(entry.author).toBe("Maker");
    expect(JSON.stringify(listed)).not.toContain(code); // the list is light; codes on request

    const b = client(url(), "Player", "elsewhere");
    await flush();
    b.send({ t: "getmap", id: entry.id });
    await flush();
    expect(b.last("mapcode")!.code).toBe(code);

    // Publishing the same map twice doesn't duplicate it.
    a.send({ t: "publish", map: { code, name: "Twin Rivers", author: "Maker" } });
    await flush();
    expect((a.last("maps")!.maps as unknown[]).length).toBe(listed.length);
    a.ws.close(); b.ws.close();

    // It survives a restart.
    expect(existsSync(mapsFile)).toBe(true);
    const again = await startServer(0, "127.0.0.1", { mapsFile });
    const res = await fetch(`http://127.0.0.1:${again.port}/maps`);
    const maps = (await res.json()) as { name: string }[];
    expect(maps.map((m) => m.name)).toContain("Twin Rivers");
    await again.close();
  });

  it("refuses things that aren't maps", async () => {
    const a = client(url(), "Troll", "junk");
    await flush();
    a.send({ t: "publish", map: { code: "<script>", name: "x" } });
    await flush();
    expect(a.last("error")).toBeTruthy();
    a.ws.close();
  });
});
