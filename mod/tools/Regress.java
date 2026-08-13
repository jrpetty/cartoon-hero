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
        boolean sized = true, dupes = false, det = true, anchored = true;
        for (CampaignMission m : CampaignDecks.ALL) {
            List<MobCard> d = CampaignDecks.cpuDeck(m);
            if (d.size() != 16) sized = false;
            if (new HashSet<>(d).size() != d.size()) dupes = true;
            if (!d.equals(CampaignDecks.cpuDeck(m))) det = false;
            for (String id : MobCategories.members(m.anchor()))
                if (MobCards.byId(id) != null && MobCategories.size(m.anchor()) <= 16
                        && d.stream().noneMatch(c -> c.id().equals(id))) anchored = false;
        }
        check(CampaignDecks.count() == 20, "20 missions");
        check(sized, "every opponent deck is exactly 16");
        check(!dupes, "no duplicate cards in a deck");
        check(det, "decks are deterministic");
        check(anchored, "every anchor member present");

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

                System.out.println(fails == 0 ? "\nALL REGRESSION CHECKS PASS" : "\n*** " + fails + " FAILURES ***");
        if (fails > 0) System.exit(1);
    }
}
