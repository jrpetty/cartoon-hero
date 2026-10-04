import com.jrpetty.mobtrumps.game.*;
import java.util.*;

public class Regress {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok    " : "  FAIL  ") + what);
        if (!ok) fails++;
    }
    public static void main(String[] a) {
        System.out.println("card set");
        check(MobCards.ALL.size() == 81, "81 cards");
        check(MobCards.orderIntact(), "card order matches ORDER_FINGERPRINT");
        Set<Integer> ords = new HashSet<>();
        for (MobCard c : MobCards.ALL) ords.add(MobCards.ordinal(c.id()));
        check(ords.size() == 81 && Collections.min(ords) == 0 && Collections.max(ords) == 80,
                "ordinals are 0..80, unique");

        System.out.println("campaign");
        boolean sized = true, dupes = false, det = true, anchored = true, trophyFirst = true, bossesKept = true;
        for (CampaignMission m : CampaignDecks.ALL) {
            List<MobCard> d = CampaignDecks.cpuDeck(m);
            if (d.size() != Battle.HAND_SIZE) sized = false;
            if (new HashSet<>(d).size() != d.size()) dupes = true;
            if (!d.equals(CampaignDecks.cpuDeck(m))) det = false;
            long fromAnchor = d.stream().filter(c -> c.category() == m.anchor()).count();
            if (fromAnchor != m.anchorCount()) anchored = false;
            if (d.isEmpty() || !d.get(0).id().equals(m.trophyMob())) trophyFirst = false;
            if (m.anchor() != Category.BOSS && d.stream().anyMatch(c -> c.category() == Category.BOSS))
                bossesKept = false;
        }
        // the rule itself, not just today's twenty decks: missions built to
        // need legendary padding from outside any boss-adjacent set, where a
        // boss is the likeliest pick there is, still never get one
        boolean probesClean = true;
        for (int i = 0; i < 12; i++) {
            CampaignMission probe = new CampaignMission(90 + i, "probe_" + i, "Probe", "Probe",
                    Category.VILLAGE, 1, Tier.LEGENDARY, Tier.LEGENDARY, Difficulty.HARD, true, 0, "villager");
            if (CampaignDecks.cpuDeck(probe).stream().anyMatch(c -> c.category() == Category.BOSS))
                probesClean = false;
        }
        check(probesClean, "padding never draws a boss, even where a boss is the likeliest card");
        check(Battle.HAND_SIZE == 6, "every Top Trumps game is six cards a side");
        check(CampaignDecks.count() == 20, "20 missions");
        check(sized, "every opponent deck is exactly a hand (" + Battle.HAND_SIZE + ")");
        check(!dupes, "no duplicate cards in a deck");
        check(det, "decks are deterministic");
        check(anchored, "every deck holds exactly its anchor share");
        check(trophyFirst, "every deck leads with its trophy mob");
        check(bossesKept, "no boss is ever padding");
        // the taglines that count cards are promises the deck has to keep
        check(CampaignDecks.byIndex(12).anchorCount() == 6, "Raid Bells is six illagers");
        check(CampaignDecks.byIndex(19).anchorCount() == 2, "Reckoning holds two of the three bosses");
        check(CampaignDecks.byIndex(20).anchorCount() == 3, "The Last Trump holds all three bosses");
        check(CampaignDecks.byIndex(8).anchorCount() == 4, "The Trading Post holds four villagers");

        System.out.println("hands");
        {
        boolean dealt = true, conserved = true, ordered = true, logged = true, story = true;
        for (int seed = 0; seed < 400; seed++) {
            Random r = new Random(seed);
            Battle b = new Battle(Battle.HAND_SIZE * 2, r);
            if (b.playerCardCount() != Battle.HAND_SIZE || b.cpuCardCount() != Battle.HAND_SIZE) dealt = false;
            List<Battle.RoundResult> seen = new ArrayList<>();
            while (!b.isFinished()) {
                if (!b.playerHand().isEmpty() && !b.playerHand().get(0).equals(b.playerTopCard())) ordered = false;
                if (b.playerHand().size() != b.playerCardCount()) ordered = false;
                Stat st = b.getTurn() == Battle.Side.CPU ? b.cpuChoice() : Stat.values()[r.nextInt(6)];
                seen.add(b.playRound(st));
                if (b.playerCardCount() + b.cpuCardCount() + b.potCount() != Battle.HAND_SIZE * 2) conserved = false;
            }
            if (!b.history().equals(seen) || b.history().size() != b.getRound()) logged = false;
            for (Battle.Side side : new Battle.Side[]{Battle.Side.PLAYER, Battle.Side.CPU}) {
                BattleSummary sum = BattleSummary.of(b.history(), side);
                if (sum.rounds() != b.getRound()) story = false;
                if (sum.bestStreak() > sum.won() || sum.mvpWins() > sum.won()) story = false;
                if ((sum.won() == 0) != (sum.mvp() == null)) story = false;
                Battle.Side other = side == Battle.Side.PLAYER ? Battle.Side.CPU : Battle.Side.PLAYER;
                if (sum.lost() != BattleSummary.of(b.history(), other).won()) story = false;
            }
        }
        check(dealt, "a game deals " + Battle.HAND_SIZE + " cards to each side");
        check(conserved, "a six-card game never creates or loses a card");
        check(ordered, "the hand view is the play order, top card first");
        check(logged, "the history holds every round, in order");
        check(story, "summaries add up from both seats; no wins means no MVP");
        // runs of wins: W W T W L W W — a draw ends a run and so does a loss,
        // so the best is 2, where either rule missing would make it 3
        MobCard x = MobCards.byId("creeper"), y = MobCards.byId("zombie"), z = MobCards.byId("cow");
        Battle.Side P = Battle.Side.PLAYER, C = Battle.Side.CPU, N = Battle.Side.NONE;
        Battle.Side[] runs = {P, P, N, P, C, P, P};
        List<Battle.RoundResult> h = new ArrayList<>();
        for (int i = 0; i < runs.length; i++)
            h.add(new Battle.RoundResult(i + 1, P, Stat.ATTACK, x, z, runs[i]));
        BattleSummary hs = BattleSummary.of(h, P);
        check(hs.won() == 5 && hs.lost() == 1 && hs.tied() == 1, "a summary counts won 5, lost 1, tied 1");
        check(hs.bestStreak() == 2, "a draw ends a run of wins, and so does a loss (best run 2)");
        check(BattleSummary.of(h, C).won() == 1 && BattleSummary.of(h, C).bestStreak() == 1
                        && BattleSummary.of(h, C).mvp() == z,
                "the other seat reads the same rounds the other way round, its own card the MVP");
        // the MVP is whoever SET the mark: x, y, y, x leaves y on 2 and x
        // only drawing level with it, so y keeps it
        List<Battle.RoundResult> mvp = List.of(
                new Battle.RoundResult(1, P, Stat.ATTACK, x, z, P),
                new Battle.RoundResult(2, P, Stat.ATTACK, y, z, P),
                new Battle.RoundResult(3, P, Stat.ATTACK, y, z, P),
                new Battle.RoundResult(4, P, Stat.ATTACK, x, z, P));
        hs = BattleSummary.of(mvp, P);
        check(hs.mvp() == y && hs.mvpWins() == 2, "the card that set the mark keeps MVP when another draws level");
        check(BattleSummary.of(mvp, C).mvp() == null, "a seat that won nothing has no MVP");
        }

        System.out.println("experience");
        // the numbers the config comment promises, pinned as numbers
        check(GamePay.xp(25, 15_000L) == 25, "a 15-second game pays the full 25 (five zombies)");
        check(GamePay.xp(25, 10 * 60_000L) == 25, "a long game pays no more than the full amount");
        check(GamePay.xp(25, 7_500L) == 13, "a 7.5-second game pays half (rounded)");
        check(GamePay.xp(25, 14_000L) < 25, "a game under 15 seconds pays less than the full amount");
        check(GamePay.xp(25, 0) == 1 && GamePay.xp(25, -5) == 1, "a finished game always pays something");
        check(GamePay.xp(0, 60_000L) == 0, "a rate of 0 switches it off");
        boolean monotone = true;
        for (long ms = 0; ms <= GamePay.FULL_PAY_MS + 1000; ms += 250)
            if (GamePay.xp(25, ms) < GamePay.xp(25, Math.max(0, ms - 250))) monotone = false;
        check(monotone, "a longer game never pays less");

        System.out.println("recycler");
        boolean flat = true, ordered = true;
        int prevMax = 0;
        for (Tier t : Tier.values()) {
            for (int s = Recycler.MIN_STAKE; s <= Recycler.maxStake(t); s++)
                if (Math.abs(Recycler.expectedCost(t, s) - Recycler.maxStake(t)) > 0.001) flat = false;
            if (Recycler.maxStake(t) <= prevMax) ordered = false;
            prevMax = Recycler.maxStake(t);
            if (Recycler.yield(t, 100) != Recycler.baseYield(t)) flat = false;
            if (Recycler.yield(t, 0) < 1) flat = false;
            if (Recycler.yield(t, 0) > Recycler.yield(t, 100)) flat = false;
        }
        check(flat, "expected cost flat at every stake; yield mint>=ruined>=1");
        check(ordered, "max stake rises with tier");
        // the property, not a frozen number: the max always guarantees and half
        // of it is always a coin flip, whatever the price is set to
        int cap = Recycler.maxStake(Tier.COMMON);
        check(Recycler.percent(Tier.COMMON, cap) == 100 && Recycler.percent(Tier.COMMON, cap / 2) == 50,
                "common: " + cap + " guarantees, " + (cap / 2) + " is a coin flip");

        System.out.println("stats");
        check(Stat.RARITY.lowerWins, "rarity is lower-wins");
        check(Stat.RARITY.score(1) > Stat.RARITY.score(10), "rarity 1 beats rarity 10");
        check(Stat.HEALTH.score(10) > Stat.HEALTH.score(1), "health 10 beats health 1");

        System.out.println("memory");
        List<String> pool = new ArrayList<>();
        for (MobCard c : MobCards.ALL) pool.add(c.id());
        boolean dealt = true, twice = true;
        for (Memory.BoardSize s : Memory.BoardSize.values()) {
            List<String> faces = Memory.deal(pool, s.pairs(), new Random(11));
            if (faces.size() != s.tiles()) dealt = false;
            Map<String,Integer> n = new HashMap<>();
            for (String f : faces) n.merge(f, 1, Integer::sum);
            if (n.size() != s.pairs() || !n.values().stream().allMatch(v -> v == 2)) twice = false;
        }
        check(dealt, "every board deals cols*rows tiles");
        check(twice, "every mob on a board appears exactly twice");
        check(Memory.BoardSize.HARD.pairs() == 18, "hard is 18 pairs");

        // The security property, stated as a test because it is the whole game:
        // a face-down tile must not name its mob. Everything that builds a
        // packet reads faces through faceAt, so this is what stops the board
        // being read out of the traffic.
        Memory.Board board = new Memory.Board(Memory.deal(pool, 18, new Random(5)));
        boolean leaks = false;
        for (int i = 0; i < board.size(); i++) if (!board.faceAt(i).isEmpty()) leaks = true;
        check(!leaks, "a fresh board names none of its 36 mobs");
        board.flip(0);
        int named = 0;
        for (int i = 0; i < board.size(); i++) if (!board.faceAt(i).isEmpty()) named++;
        check(named == 1, "after one flip exactly one tile is readable");
        check(board.faceAt(999).isEmpty() && board.faceAt(-1).isEmpty(),
                "out-of-range tiles are empty, not a crash");

        Memory.Board small = new Memory.Board(List.of("a", "a", "b", "b"));
        check(small.flip(0) == Memory.Flip.FIRST, "first flip is FIRST");
        check(small.flip(0) == Memory.Flip.REJECTED, "the same tile twice is refused");
        check(small.flip(2) == Memory.Flip.MISS, "a mismatch is a MISS");
        check(small.peeking(), "a miss leaves the board peeking");
        check(small.flip(1) == Memory.Flip.REJECTED, "flips during the peek are refused");
        small.resolvePeek();
        check(small.faceAt(0).isEmpty(), "the peek hides the faces again");
        small.flip(0);
        check(small.flip(1) == Memory.Flip.MATCH, "a pair is a MATCH");
        check(small.stateAt(0) == Memory.MATCHED, "matched tiles stay on the table");
        small.flip(2); small.flip(3);
        check(small.complete() && small.moves() == 3,
                "a cleared board counts moves per pair, not per flip");
        check(small.flip(0) == Memory.Flip.REJECTED, "a finished board refuses flips");

                System.out.println("memory: single player");
        List<String> mpool = new ArrayList<>();
        for (MobCard c : MobCards.ALL) mpool.add(c.id());
        UUID solo = new UUID(1, 1);
        {
            MemoryMatch m = new MemoryMatch(solo, null, Memory.BoardSize.MEDIUM,
                    Memory.deal(mpool, 12, new Random(3)));
            check(m.solo() && m.isTurn(solo), "solo: it is always your turn");
            long t = 1000;
            // a miss freezes the board until the peek elapses, solo included
            int i = 0, j = 1;
            while (!m.board().faceAt(i).equals("") || i == j) i++;
            m.flip(solo, 0, t);
            MemoryMatch.Outcome second = m.flip(solo, 1, t);
            if (second == MemoryMatch.Outcome.MISS) {
                check(m.flip(solo, 2, t) == MemoryMatch.Outcome.REJECTED,
                        "solo: no flipping while the pair is being peeked at");
                check(!m.tick(t + MemoryMatch.PEEK_MS - 1), "solo: the peek does not end early");
                check(m.tick(t + MemoryMatch.PEEK_MS), "solo: the peek ends on time");
                check(m.board().faceAt(0).isEmpty(), "solo: the peeked pair goes back down");
            }
            // play it out
            MemoryMatch g = new MemoryMatch(solo, null, Memory.BoardSize.MEDIUM,
                    Memory.deal(mpool, 12, new Random(3)));
            long now = 0;
            int guard = 0;
            // Pick at RANDOM, not "the two lowest hidden tiles": a deterministic
            // chooser turns the same mismatched pair over forever and never
            // finishes, which says nothing about the game and everything about
            // the chooser. That bug was in this file twice before it was in the
            // rules never.
            Random pick = new Random(77);
            while (!g.done() && guard++ < 20000) {
                now += 50;
                if (g.tick(now)) continue;
                if (g.peeking()) { now += MemoryMatch.PEEK_MS; continue; }
                List<Integer> hidden = new ArrayList<>();
                for (int k = 0; k < g.board().size(); k++)
                    if (g.board().stateAt(k) == Memory.HIDDEN) hidden.add(k);
                if (hidden.size() < 2) break;
                g.flip(solo, hidden.remove(pick.nextInt(hidden.size())), now);
                g.flip(solo, hidden.remove(pick.nextInt(hidden.size())), now);
            }
            check(g.done() && g.board().complete(), "solo: a board can be cleared");
            check(g.scoreOf(solo) == 12, "solo: score is the 12 pairs taken");
            check(g.winner() == null, "solo: there is no opponent to beat");
        }

        System.out.println("memory: two player");
        UUID pa = new UUID(2, 2), pb = new UUID(3, 3);
        {
            MemoryMatch m = new MemoryMatch(pa, pb, Memory.BoardSize.EASY,
                    List.of("a","a","b","b","c","c","d","d","e","e","f","f","g","g","h","h"));
            check(m.isTurn(pa) && !m.isTurn(pb), "2p: the challenger goes first");
            check(m.flip(pb, 0, 100) == MemoryMatch.Outcome.REJECTED,
                    "2p: you cannot flip out of turn");
            check(m.board().faceAt(0).isEmpty(),
                    "2p: an out-of-turn flip does not even reveal the card");
            check(m.flip(pa, 0, 100) == MemoryMatch.Outcome.FIRST, "2p: first flip");
            check(m.flip(pa, 1, 100) == MemoryMatch.Outcome.MATCH, "2p: 'a' pairs with 'a'");
            check(m.isTurn(pa), "2p: a match KEEPS your turn");
            check(m.scoreOf(pa) == 1 && m.scoreOf(pb) == 0, "2p: the pair is scored to you");
            m.flip(pa, 2, 200);
            check(m.flip(pa, 4, 200) == MemoryMatch.Outcome.MISS, "2p: 'b' does not pair with 'c'");
            check(m.isTurn(pa), "2p: the turn does NOT pass until the peek ends");
            check(m.flip(pb, 6, 200) == MemoryMatch.Outcome.REJECTED,
                    "2p: nor can they jump in during the peek");
            check(!m.tick(200 + MemoryMatch.PEEK_MS - 1), "2p: the peek runs its full 1.4s");
            check(m.tick(200 + MemoryMatch.PEEK_MS), "2p: then it resolves");
            check(m.isTurn(pb) && !m.isTurn(pa), "2p: and the turn passes");
            check(m.board().faceAt(2).isEmpty() && m.board().faceAt(4).isEmpty(),
                    "2p: both cards go back face down");
        }
        {   // forfeit
            MemoryMatch m = new MemoryMatch(pa, pb, Memory.BoardSize.EASY,
                    Memory.deal(mpool, 8, new Random(9)));
            m.forfeit(pa);
            check(m.done() && pb.equals(m.winner()), "2p: walking out hands them the win");
            check(m.flip(pb, 0, 500) == MemoryMatch.Outcome.REJECTED,
                    "2p: a forfeited board is closed");
        }
        {   // full random playouts of both modes
            int games = 0, drawn = 0, badTurn = 0, badScore = 0, unfinished = 0;
            for (int seed = 0; seed < 400; seed++) {
                Random rng = new Random(seed);
                Memory.BoardSize size = Memory.BoardSize.byOrdinal(seed % 3);
                MemoryMatch m = new MemoryMatch(pa, pb, size, Memory.deal(mpool, size.pairs(), rng));
                long now = 0;
                int guard = 0;
                while (!m.done() && guard++ < 40000) {
                    now += 60;
                    if (m.tick(now)) continue;
                    if (m.peeking()) { now += MemoryMatch.PEEK_MS; continue; }
                    UUID who = m.turn();
                    List<Integer> hidden = new ArrayList<>();
                    for (int k = 0; k < m.board().size(); k++)
                        if (m.board().stateAt(k) == Memory.HIDDEN) hidden.add(k);
                    if (hidden.size() < 2) break;
                    int x = hidden.remove(rng.nextInt(hidden.size()));
                    int y = hidden.remove(rng.nextInt(hidden.size()));
                    m.flip(who, x, now);
                    MemoryMatch.Outcome o = m.flip(who, y, now);
                    // the rule that makes it a game: a match keeps the turn
                    if (o == MemoryMatch.Outcome.MATCH && !m.done() && !m.isTurn(who)) badTurn++;
                    // and the loser of a pair never scores it
                    if (o == MemoryMatch.Outcome.MISS && m.scoreOf(who) != m.scoreOf(who)) badScore++;
                }
                if (!m.done()) { unfinished++; continue; }
                games++;
                if (m.scoreOf(pa) + m.scoreOf(pb) != size.pairs()) badScore++;
                UUID w = m.winner();
                if (w == null) drawn++;
                else if (m.scoreOf(w) <= m.scoreOf(m.other(w))) badScore++;
            }
            check(unfinished == 0, "2p: all 400 random games reached an end");
            check(badTurn == 0, "2p: a match never passed the turn (400 games)");
            check(badScore == 0, "2p: every game's scores summed to its pairs, "
                    + "and the winner always had more (" + drawn + " draws)");
            check(games == 400, "2p: " + games + " complete games played this build");
        }

        System.out.println("memory layout");
        // This sweeps the REAL solve, not a copy of it. An earlier version of
        // this check was a Python transcription of the same arithmetic, and it
        // could not see a single change to the algorithm: flooring turned back
        // into rounding, and the height budget dropped from the scale, both
        // passed. That is what a check that re-implements its subject is worth.
        int swept = 0;
        String worstFit = null;
        float tightest = 9f;
        String tightestWhere = "";
        for (int w = 320; w <= 900; w += 7) {
            for (int h = 240; h <= 700; h += 5) {
                for (Memory.BoardSize size : Memory.BoardSize.values()) {
                    swept++;
                    MemoryLayout.Grid gr = MemoryLayout.solve(w, h, size.cols, size.rows, 170, 236);
                    if (!MemoryLayout.fits(gr, w, h) && worstFit == null) {
                        worstFit = size.label + " " + size.cols + "x" + size.rows
                                + " at " + w + "x" + h + ": grid y " + gr.gridY()
                                + ".." + (gr.gridY() + gr.gridH()) + ", x " + gr.gridX()
                                + ".." + (gr.gridX() + gr.gridW());
                    }
                    if (gr.scale() < tightest) {
                        tightest = gr.scale();
                        tightestWhere = size.label + " at " + w + "x" + h + " -> "
                                + gr.cardW() + "x" + gr.cardH() + "px";
                    }
                }
            }
        }
        for (int[] big : new int[][]{{1280,720},{1920,1080},{2560,1440},{3840,2160}}) {
            for (Memory.BoardSize size : Memory.BoardSize.values()) {
                swept++;
                MemoryLayout.Grid gr = MemoryLayout.solve(big[0], big[1], size.cols, size.rows, 170, 236);
                if (!MemoryLayout.fits(gr, big[0], big[1]) && worstFit == null) {
                    worstFit = size.label + " at " + big[0] + "x" + big[1];
                }
                if (gr.scale() > MemoryLayout.SCALE_CAP) worstFit = "scale cap exceeded";
            }
        }
        check(worstFit == null, swept + " board/window combinations all fit"
                + (worstFit == null ? " (tightest " + tightestWhere + ")" : ": " + worstFit));
        // the specific case the feature was specified against
        MemoryLayout.Grid hard = MemoryLayout.solve(320, 240, 6, 6, 170, 236);
        check(MemoryLayout.fits(hard, 320, 240),
                "6x6 fits a 240px-tall GUI (" + hard.cardW() + "x" + hard.cardH() + "px a card)");
        // tiles must not overlap, or two cards share a click
        MemoryLayout.Grid g6 = MemoryLayout.solve(640, 480, 6, 6, 170, 236);
        boolean overlap = false;
        for (int i = 0; i < 36; i++)
            for (int j = i + 1; j < 36; j++) {
                int ax = g6.tileX(i, 6), ay = g6.tileY(i, 6);
                int bx = g6.tileX(j, 6), by = g6.tileY(j, 6);
                if (ax < bx + g6.cardW() && bx < ax + g6.cardW()
                        && ay < by + g6.cardH() && by < ay + g6.cardH()) overlap = true;
            }
        check(!overlap, "no two tiles overlap, so no click is ambiguous");

        System.out.println("battle layout");
        {
        // Every window the battle screen can be opened in, both kinds of game
        // and every card-size setting: nothing on the table may overlap or
        // leave the felt, and the board must have room for its text.
        String bad = null;
        int laid = 0;
        float smallest = 9;
        String smallestAt = "";
        float[] caps = {1.05f, 0.50f, 0.68f, 0.92f};
        for (int w = 320; w <= 1280 && bad == null; w += 4) {
            for (int h = 240; h <= 720 && bad == null; h += 4) {
                for (boolean pvp : new boolean[]{false, true}) {
                    for (float sizeCap : caps) {
                        laid++;
                        BattleLayout.Layout L = BattleLayout.solve(w, h, sizeCap, pvp);
                        String at = " at " + w + "x" + h + (pvp ? " pvp" : " cpu") + " cap " + sizeCap;
                        BattleLayout.Rect window = new BattleLayout.Rect(0, 0, w, h);
                        java.util.List<BattleLayout.Rect> table = new ArrayList<>();
                        table.add(L.myCard()); table.add(L.board()); table.add(L.oppCard());
                        if (L.hands()) { table.add(L.myHand()); table.add(L.oppHand()); }
                        table.add(L.myPlate()); table.add(L.oppPlate()); table.add(L.status());
                        for (int i = 0; i < table.size() && bad == null; i++) {
                            if (!table.get(i).inside(L.felt())) bad = "table piece " + i + " leaves the felt" + at;
                            for (int j = i + 1; j < table.size() && bad == null; j++)
                                if (table.get(i).overlaps(table.get(j))) bad = "pieces " + i + "/" + j + " overlap" + at;
                        }
                        if (bad == null && (L.felt().overlaps(L.header()) || L.felt().overlaps(L.dock())))
                            bad = "the felt runs under a band" + at;
                        if (bad == null && !L.rows().inside(L.board())) bad = "stat rows leave the board" + at;
                        if (bad == null && L.rowH() < BattleLayout.ROW_MIN_H) bad = "stat rows too short" + at;
                        if (bad == null && (!L.footer().inside(L.board()) || L.footer().h() < 12))
                            bad = "no room for the board's footer" + at;
                        if (bad == null && !L.panel().inside(L.felt())) bad = "result panel leaves the felt" + at;
                        if (bad == null && L.panel().h() < 120) bad = "result panel too short" + at;
                        if (bad == null && L.scale() < BattleLayout.MIN_SCALE) bad = "cards below the minimum" + at;
                        // the dock: every button inside it, none touching another
                        java.util.List<BattleLayout.Rect> dock = new ArrayList<>();
                        dock.add(L.sizeButton()); dock.add(L.primary()); dock.add(L.leaveButton());
                        if (L.emoteButton() != null) dock.add(L.emoteButton());
                        if (L.autoButton() != null) dock.add(L.autoButton());
                        for (int i = 0; i < dock.size() && bad == null; i++) {
                            if (!dock.get(i).inside(L.dock())) bad = "dock button " + i + " leaves the dock" + at;
                            for (int j = i + 1; j < dock.size() && bad == null; j++)
                                if (dock.get(i).overlaps(dock.get(j))) bad = "dock buttons " + i + "/" + j + " touch" + at;
                        }
                        if (bad == null && L.primary().w() < 80) bad = "no room for the primary button" + at;
                        if (bad == null && (L.emoteButton() != null) != pvp) bad = "emote button in the wrong game" + at;
                        if (bad == null && (L.autoButton() != null) == pvp) bad = "auto-continue in the wrong game" + at;
                        // a hand can hold every card in the game; the column must show them all
                        if (bad == null && L.hands()) {
                            for (int n = 1; n <= Battle.HAND_SIZE * 2 && bad == null; n++) {
                                BattleLayout.Rect last = BattleLayout.mini(L.myHand(), L.miniScale(), n - 1, n);
                                if (last.bottom() > L.myHand().bottom() + 1) bad = n + " cards overflow the hand column" + at;
                            }
                        }
                        if (sizeCap == caps[0] && L.scale() < smallest) { smallest = L.scale(); smallestAt = w + "x" + h; }
                    }
                }
            }
        }
        check(bad == null, laid + " window/game/size combinations lay the table out cleanly"
                + (bad == null ? " (smallest cards " + String.format("%.2f", smallest) + " at " + smallestAt + ")" : ": " + bad));
        // the windows people actually play in keep their hands on the table
        for (int[] wh : new int[][]{{427, 240}, {480, 270}, {455, 256}, {640, 360}}) {
            BattleLayout.Layout L = BattleLayout.solve(wh[0], wh[1], 1.05f, false);
            check(L.hands() && L.scale() >= 0.55f, wh[0] + "x" + wh[1] + ": both hands shown, cards at "
                    + String.format("%.2f", L.scale()) + " (" + L.myCard().w() + "x" + L.myCard().h() + ")");
        }
        }

        System.out.println("draft layout");
        {
        String bad = null;
        int laid = 0;
        int picks = Battle.HAND_SIZE;
        int poolMax = picks * 2 + 4;
        for (int w = 320; w <= 1280 && bad == null; w += 4) {
            for (int h = 240; h <= 720 && bad == null; h += 4) {
                for (int n = 1; n <= poolMax && bad == null; n++) {
                    laid++;
                    DraftLayout.Layout L = DraftLayout.solve(w, h, n, picks);
                    String at = " at " + w + "x" + h + " with " + n + " in the pool";
                    DraftLayout.Rect window = new DraftLayout.Rect(0, 0, w, h);
                    for (int i = 0; i < n && bad == null; i++) {
                        DraftLayout.Rect c = L.poolCard(i, n);
                        if (!c.inside(L.pool())) bad = "pool card " + i + " leaves the pool" + at;
                        for (int j = i + 1; j < n && bad == null; j++)
                            if (c.overlaps(L.poolCard(j, n))) bad = "pool cards " + i + "/" + j + " overlap" + at;
                    }
                    for (DraftLayout.Rect row : new DraftLayout.Rect[]{L.mine(), L.theirs()}) {
                        if (bad != null) break;
                        if (!row.inside(window) || row.overlaps(L.pool()) || row.overlaps(L.footer()))
                            bad = "a row of picks collides" + at;
                        DraftLayout.Rect last = L.slot(row, picks - 1);
                        if (bad == null && (last.right() > row.right() || last.bottom() > row.bottom()))
                            bad = "the last pick slot leaves its row" + at;
                    }
                    if (bad == null && L.mine().overlaps(L.theirs())) bad = "the two rows of picks overlap" + at;
                    if (bad == null && (!L.leave().inside(L.footer()))) bad = "Leave leaves the footer" + at;
                    if (bad == null && L.pool().overlaps(L.header())) bad = "the pool runs under the header" + at;
                }
            }
        }
        check(bad == null, laid + " window/pool combinations lay the draft out cleanly" + (bad == null ? "" : ": " + bad));
        DraftLayout.Layout typical = DraftLayout.solve(427, 240, poolMax, picks);
        check(typical.poolScale() >= 0.2f, "427x240: the full pool of " + poolMax + " at "
                + String.format("%.2f", typical.poolScale()) + " (" + typical.poolCard(0, poolMax).w() + "x"
                + typical.poolCard(0, poolMax).h() + ")");
        }

                System.out.println(fails == 0 ? "\nALL REGRESSION CHECKS PASS" : "\n*** " + fails + " FAILURES ***");
        if (fails > 0) System.exit(1);
    }
}
