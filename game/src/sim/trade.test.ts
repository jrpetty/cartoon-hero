import { describe, expect, it } from "vitest";
import { World } from "./world";
import { OrderKind, Team } from "./types";
import { generateMap } from "../maps/generator";
import { SIM_HZ, TILE } from "../content/balance";

/** A world with a Market at each end of a long line. */
function trading(gapTiles = 40) {
  const map = generateMap("open_plains", 9, 2);
  const w = new World(9);
  w.init(map, [{}, {}], [1, 1], [0, 1]);
  const p = w.player(Team.Player);
  p.age = 1;
  p.resources = { food: 5000, wood: 5000, gold: 5000 };
  const cy = w.worldH / 2;
  const home = w.spawnBuilding(Team.Player, "market", 400, cy, true);
  const far = w.spawnBuilding(Team.Player, "market", 400 + gapTiles * TILE, cy, true);
  return { w, p, home, far };
}

const run = (w: World, secs: number) => {
  for (let i = 0; i < SIM_HZ * secs; i++) { w.tick(); w.drainEvents(); }
};

describe("Trade carts", () => {
  it("puts itself on a route between two Markets", () => {
    const { w, home } = trading();
    const cart = w.spawnUnit(Team.Player, "trade_cart", home.x + 30, home.y);
    expect(w.assignTradeRoute([cart.id])).toBe(true);
    expect(cart.order.kind).toBe(OrderKind.Trade);
    expect(cart.tradeHomeId).toBe(home.id);
  });

  it("picks the furthest Market, because the pay is the distance", () => {
    // A cart that walks to the nearest Market earns almost nothing, so the
    // route choice is not a matter of taste.
    const { w, home } = trading();
    const cy = w.worldH / 2;
    w.spawnBuilding(Team.Player, "market", home.x + 6 * TILE, cy, true); // a near decoy
    const far = w.spawnBuilding(Team.Player, "market", home.x + 60 * TILE, cy, true);
    const cart = w.spawnUnit(Team.Player, "trade_cart", home.x + 30, cy);
    w.assignTradeRoute([cart.id]);
    expect(cart.order.target).toBe(far.id);
  });

  it("refuses a route when there is only one Market", () => {
    const map = generateMap("open_plains", 9, 2);
    const w = new World(9);
    w.init(map, [{}, {}], [1, 1], [0, 1]);
    const only = w.spawnBuilding(Team.Player, "market", 400, w.worldH / 2, true);
    const cart = w.spawnUnit(Team.Player, "trade_cart", only.x + 30, only.y);
    expect(w.assignTradeRoute([cart.id])).toBe(false);
    expect(cart.order.kind).not.toBe(OrderKind.Trade);
  });

  it("earns gold on arrival and turns around", () => {
    const { w, p, home } = trading(24);
    const cart = w.spawnUnit(Team.Player, "trade_cart", home.x + 30, home.y);
    w.assignTradeRoute([cart.id]);
    const before = p.resources.gold;
    run(w, 90);
    expect(p.resources.gold, "the cart never paid out").toBeGreaterThan(before);
    // And it is heading back rather than parked at the far end.
    expect(cart.order.kind).toBe(OrderKind.Trade);
  }, 60000);

  it("pays more on a long route than a short one", () => {
    // The whole shape of the decision: trade is about ground you can hold, not
    // a building you press once.
    const earned = (gap: number) => {
      const { w, p, home } = trading(gap);
      const cart = w.spawnUnit(Team.Player, "trade_cart", home.x + 30, home.y);
      w.assignTradeRoute([cart.id]);
      const before = p.resources.gold;
      run(w, 240);
      return p.resources.gold - before;
    };
    const short = earned(8);
    const long = earned(50);
    expect(long, `short ${short}, long ${long}`).toBeGreaterThan(short);
  }, 120000);

  it("counts trade gold as gathered, so the economy report reflects it", () => {
    const { w, p, home } = trading(24);
    const cart = w.spawnUnit(Team.Player, "trade_cart", home.x + 30, home.y);
    w.assignTradeRoute([cart.id]);
    run(w, 90);
    expect(p.stats.gatheredBy.gold).toBeGreaterThan(0);
  }, 60000);

  it("goes idle rather than walking to a ruin when its Market falls", () => {
    const { w, home, far } = trading(24);
    const cart = w.spawnUnit(Team.Player, "trade_cart", home.x + 30, home.y);
    w.assignTradeRoute([cart.id]);
    run(w, 5);
    (w as unknown as { kill(e: unknown, t: Team): void }).kill(far, Team.Enemy);
    run(w, 5);
    expect(cart.order.kind, "kept trading with a razed Market").not.toBe(OrderKind.Trade);
  }, 60000);

  it("is unarmed — it is money walking across the map", () => {
    const { w, home } = trading();
    const cart = w.spawnUnit(Team.Player, "trade_cart", home.x + 30, home.y);
    expect(cart.attack).toBe(0);
  });
});

describe("A Market with prices that move", () => {
  it("pays less for each successive sale", () => {
    // Flat rates made the Market a converter you pressed whenever you were
    // short, with no reason to think about when.
    const { w, p } = trading();
    const first = w.marketQuote(Team.Player, "wood").sell;
    for (let i = 0; i < 6; i++) w.marketTrade(Team.Player, "sell_wood");
    const later = w.marketQuote(Team.Player, "wood").sell;
    expect(later, `first ${first}, after six sales ${later}`).toBeLessThan(first);
    void p;
  });

  it("charges more for each successive purchase", () => {
    const { w } = trading();
    const first = w.marketQuote(Team.Player, "food").buy;
    for (let i = 0; i < 6; i++) w.marketTrade(Team.Player, "buy_food");
    expect(w.marketQuote(Team.Player, "food").buy).toBeGreaterThan(first);
  });

  it("moves wood and food independently", () => {
    const { w } = trading();
    for (let i = 0; i < 6; i++) w.marketTrade(Team.Player, "sell_wood");
    expect(w.marketPrice(Team.Player, "wood")).toBeLessThan(1);
    expect(w.marketPrice(Team.Player, "food")).toBe(1);
  });

  it("keeps the swing inside a band, so the Market can't be broken by spamming", () => {
    const { w } = trading();
    for (let i = 0; i < 200; i++) w.marketTrade(Team.Player, "sell_wood");
    const price = w.marketPrice(Team.Player, "wood");
    expect(price).toBeGreaterThanOrEqual(0.6);
    expect(w.marketQuote(Team.Player, "wood").sell).toBeGreaterThan(0);
  });

  it("drifts back toward par over a few minutes", () => {
    const { w } = trading();
    for (let i = 0; i < 8; i++) w.marketTrade(Team.Player, "sell_wood");
    const depressed = w.marketPrice(Team.Player, "wood");
    expect(depressed).toBeLessThan(1);
    run(w, 180);
    const recovered = w.marketPrice(Team.Player, "wood");
    expect(recovered, `${depressed} → ${recovered}`).toBeGreaterThan(depressed);
    expect(recovered).toBeLessThanOrEqual(1);
  }, 60000);

  it("still refuses a trade you cannot afford", () => {
    const { w, p } = trading();
    p.resources = { food: 0, wood: 0, gold: 0 };
    expect(w.marketTrade(Team.Player, "sell_wood")).toBe(false);
    expect(w.marketTrade(Team.Player, "buy_food")).toBe(false);
  });

  it("still refuses to trade without a Market at all", () => {
    const map = generateMap("open_plains", 9, 2);
    const w = new World(9);
    w.init(map, [{}, {}], [1, 1], [0, 1]);
    expect(w.marketTrade(Team.Player, "sell_wood")).toBe(false);
  });

  it("never lets a round trip make money", () => {
    // Sell a hundred wood, buy a hundred back: you must come out behind, at any
    // point on the price curve, or the Market is an infinite gold press.
    const { w, p } = trading();
    for (const preload of [0, 4, 10]) {
      p.marketPressure = { wood: 0, food: 0 };
      for (let i = 0; i < preload; i++) w.marketTrade(Team.Player, "sell_wood");
      const q = w.marketQuote(Team.Player, "wood");
      expect(q.buy, `at pressure step ${preload}: sell ${q.sell}, buy ${q.buy}`)
        .toBeGreaterThan(q.sell);
    }
  });
});
