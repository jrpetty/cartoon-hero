package dev.structint.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Dependency-free self-test for the structural core. Builds toy worlds in an in-memory
 * {@link GridAccess} and asserts the solver's verdicts match the design's intent.
 *
 * <p>Runnable with nothing but a JDK:
 * <pre>
 *   javac -d out $(find src/main/java/dev/structint/core -name '*.java') \
 *                src/test/java/dev/structint/core/CoreSelfTest.java
 *   java -cp out dev.structint.core.CoreSelfTest
 * </pre>
 * A JUnit wrapper ({@code SupportSolverTest}) drives the same scenarios under {@code gradle test}.
 */
public final class CoreSelfTest {

    /** Tiny mutable grid: a map of packed pos -> (role, span). Everything else is EMPTY. */
    static final class FakeGrid implements GridAccess {
        private final Map<Long, int[]> cells = new HashMap<>(); // [roleOrdinal, span]

        FakeGrid anchor(int x, int y, int z) {
            cells.put(PackedPos.of(x, y, z), new int[]{CellRole.ANCHOR.ordinal(), 0});
            return this;
        }

        FakeGrid structural(int x, int y, int z, int span) {
            cells.put(PackedPos.of(x, y, z), new int[]{CellRole.STRUCTURAL.ordinal(), span});
            return this;
        }

        /** Lay a horizontal run of structural blocks along +x at constant y,z. */
        FakeGrid beamX(int x0, int x1, int y, int z, int span) {
            for (int x = x0; x <= x1; x++) {
                structural(x, y, z, span);
            }
            return this;
        }

        /** Lay a vertical run of structural blocks along +y. */
        FakeGrid pillarY(int x, int y0, int y1, int z, int span) {
            for (int y = y0; y <= y1; y++) {
                structural(x, y, z, span);
            }
            return this;
        }

        @Override
        public CellRole roleAt(int x, int y, int z) {
            int[] c = cells.get(PackedPos.of(x, y, z));
            return c == null ? CellRole.EMPTY : CellRole.values()[c[0]];
        }

        @Override
        public int maxSpanAt(int x, int y, int z) {
            int[] c = cells.get(PackedPos.of(x, y, z));
            return c == null ? 0 : c[1];
        }
    }

    private static int failures = 0;

    private static void check(boolean condition, String message) {
        if (condition) {
            System.out.println("  ok   " + message);
        } else {
            failures++;
            System.out.println("  FAIL " + message);
        }
    }

    private static StructuralEngine engine(FakeGrid grid) {
        return new StructuralEngine(grid, SupportSolver.DEFAULT_CAP, 100_000);
    }

    public static void main(String[] args) {
        testPackingRoundTrip();
        testBlockOnGround();
        testFloatingBlock();
        testWoodCantileverExactSpan();
        testStoneReachesFurtherThanWood();
        testTallPillar();
        testRoofBetweenTwoWallsRedundancy();
        testRemovingMiddleOfRedundantRoofKeepsItUp();
        testWeakLinkLimitsBeam();
        testArchKeepsCrownUp();
        testOnlyUnsupportedTailCollapses();
        testStepUpDoesNotResetSpan();
        testEvaluateSplitsStableAndUnsupported();
        testEvaluateFlagsOverBudget();
        testSnowLoadPenalty();
        testSnowLoadShortensCantilever();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CORE SELF-TESTS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static void testPackingRoundTrip() {
        System.out.println("packing round-trips through long");
        int[][] samples = {{0, 0, 0}, {1, 2, 3}, {-5, 7, -9}, {30000, -64, -30000}, {-1, 319, 12345}};
        for (int[] s : samples) {
            long p = PackedPos.of(s[0], s[1], s[2]);
            check(PackedPos.x(p) == s[0] && PackedPos.y(p) == s[1] && PackedPos.z(p) == s[2],
                    "(" + s[0] + "," + s[1] + "," + s[2] + ") survives pack/unpack");
        }
    }

    private static void testBlockOnGround() {
        System.out.println("a block resting on natural ground is stable");
        FakeGrid g = new FakeGrid().anchor(0, 0, 0).structural(0, 1, 0, Material.WOOD.maxSpan());
        check(engine(g).isSupported(PackedPos.of(0, 1, 0)), "block sitting on ground anchor is supported");
    }

    private static void testFloatingBlock() {
        System.out.println("a block with no path to an anchor falls");
        FakeGrid g = new FakeGrid().structural(0, 10, 0, Material.STONE.maxSpan());
        check(!engine(g).isSupported(PackedPos.of(0, 10, 0)), "lone floating block is unsupported");
        check(engine(g).findUnsupported(PackedPos.of(0, 10, 0)).contains(PackedPos.of(0, 10, 0)),
                "floating block is reported for collapse");
    }

    private static void testWoodCantileverExactSpan() {
        System.out.println("wood (span 4) cantilevers exactly 4 blocks from a wall");
        // Anchor wall column at x=0; wood beam extends +x at y=5.
        FakeGrid g = new FakeGrid();
        for (int y = 0; y <= 5; y++) {
            g.anchor(0, y, 0);
        }
        g.beamX(1, 6, 5, 0, Material.WOOD.maxSpan());
        StructuralEngine e = engine(g);
        for (int x = 1; x <= 4; x++) {
            check(e.isSupported(PackedPos.of(x, 5, 0)), "x=" + x + " (within span 4) holds");
        }
        check(!e.isSupported(PackedPos.of(5, 5, 0)), "x=5 (beyond span 4) falls");
        check(!e.isSupported(PackedPos.of(6, 5, 0)), "x=6 falls");
    }

    private static void testStoneReachesFurtherThanWood() {
        System.out.println("stone (span 7) cantilevers further than wood");
        FakeGrid g = new FakeGrid();
        for (int y = 0; y <= 5; y++) {
            g.anchor(0, y, 0);
        }
        g.beamX(1, 9, 5, 0, Material.STONE.maxSpan());
        StructuralEngine e = engine(g);
        check(e.isSupported(PackedPos.of(7, 5, 0)), "stone reaches x=7");
        check(!e.isSupported(PackedPos.of(8, 5, 0)), "stone does not reach x=8");
    }

    private static void testTallPillar() {
        System.out.println("a pillar of any height stays up (vertical load transfer)");
        FakeGrid g = new FakeGrid().anchor(0, 0, 0).pillarY(0, 1, 40, 0, Material.STONE.maxSpan());
        StructuralEngine e = engine(g);
        check(e.isSupported(PackedPos.of(0, 40, 0)), "top of a 40-high pillar is supported");
    }

    private static FakeGrid roofWorld() {
        // Two stone pillars at x=0 and x=14 rising to y=5 from ground anchors, with a stone
        // roof spanning x=0..14 at y=5. 14 wide, each wall reaches 7 -> 7+7 covers it exactly.
        FakeGrid g = new FakeGrid();
        g.anchor(0, 0, 0).anchor(14, 0, 0);
        g.pillarY(0, 1, 5, 0, Material.STONE.maxSpan());
        g.pillarY(14, 1, 5, 0, Material.STONE.maxSpan());
        g.beamX(0, 14, 5, 0, Material.STONE.maxSpan());
        return g;
    }

    private static void testRoofBetweenTwoWallsRedundancy() {
        System.out.println("a roof spanning two walls is stable from both sides");
        StructuralEngine e = engine(roofWorld());
        for (int x = 0; x <= 14; x++) {
            check(e.isSupported(PackedPos.of(x, 5, 0)), "roof x=" + x + " is supported");
        }
    }

    private static void testRemovingMiddleOfRedundantRoofKeepsItUp() {
        System.out.println("removing one wall's support still leaves the near half standing");
        // Knock out the entire right pillar; left wall (span 7) should still hold x=0..7.
        FakeGrid g = roofWorld();
        for (int y = 1; y <= 5; y++) {
            g.structural(14, y, 0, 0); // overwrite, then delete by setting EMPTY
        }
        // Actually remove the right pillar entirely.
        FakeGrid g2 = new FakeGrid();
        g2.anchor(0, 0, 0).pillarY(0, 1, 5, 0, Material.STONE.maxSpan());
        g2.beamX(0, 14, 5, 0, Material.STONE.maxSpan());
        StructuralEngine e = engine(g2);
        for (int x = 0; x <= 7; x++) {
            check(e.isSupported(PackedPos.of(x, 5, 0)), "near half x=" + x + " still supported");
        }
        check(!e.isSupported(PackedPos.of(8, 5, 0)), "far overhang x=8 now falls");
    }

    private static void testWeakLinkLimitsBeam() {
        System.out.println("a weak block in the chain caps how far support travels");
        // Stone wall, but the beam is dirt (span 1): it should only reach 1 block out
        // regardless of the strong anchor behind it.
        FakeGrid g = new FakeGrid();
        for (int y = 0; y <= 5; y++) {
            g.anchor(0, y, 0);
        }
        g.beamX(1, 4, 5, 0, Material.DIRT.maxSpan());
        StructuralEngine e = engine(g);
        check(e.isSupported(PackedPos.of(1, 5, 0)), "dirt x=1 holds against the wall");
        check(!e.isSupported(PackedPos.of(2, 5, 0)), "dirt x=2 falls (span 1)");
    }

    private static void testArchKeepsCrownUp() {
        System.out.println("an arch carries its crown via two supported sides");
        // Two ground anchors at x=0 and x=6. Stone uprights to y=3, then a flat span across
        // the top x=0..6 at y=3. Width 6 <= 7+7, crown at x=3 supported from both legs.
        FakeGrid g = new FakeGrid();
        g.anchor(0, 0, 0).anchor(6, 0, 0);
        g.pillarY(0, 1, 3, 0, Material.STONE.maxSpan());
        g.pillarY(6, 1, 3, 0, Material.STONE.maxSpan());
        g.beamX(0, 6, 3, 0, Material.STONE.maxSpan());
        StructuralEngine e = engine(g);
        check(e.isSupported(PackedPos.of(3, 3, 0)), "arch crown is supported");
    }

    private static void testStepUpDoesNotResetSpan() {
        System.out.println("stepping up off a cantilever tip does NOT refresh the span (no staircase exploit)");
        // Ground pillar to y=5, wood beam (span 4) out to x=4 (the tip, reach 0).
        FakeGrid g = new FakeGrid().anchor(0, 0, 0).pillarY(0, 1, 5, 0, Material.WOOD.maxSpan());
        g.beamX(1, 4, 5, 0, Material.WOOD.maxSpan());
        // Attempt the exploit: step up at the tip and continue the cantilever at y=6.
        g.structural(4, 6, 0, Material.WOOD.maxSpan());
        g.beamX(5, 9, 6, 0, Material.WOOD.maxSpan());
        StructuralEngine e = engine(g);
        check(e.isSupported(PackedPos.of(4, 5, 0)), "the tip itself is still supported");
        check(e.isSupported(PackedPos.of(4, 6, 0)), "one block may rest on the tip");
        check(!e.isSupported(PackedPos.of(5, 6, 0)), "but the cantilever cannot continue (x=5,y=6 falls)");
        check(!e.isSupported(PackedPos.of(9, 6, 0)), "the old exploit tail (x=9,y=6) falls");
        // Sanity: a real ground pillar at the new spot WOULD support a fresh span.
        FakeGrid g2 = new FakeGrid().anchor(10, 0, 0).pillarY(10, 1, 6, 0, Material.WOOD.maxSpan());
        g2.beamX(11, 14, 6, 0, Material.WOOD.maxSpan());
        check(engine(g2).isSupported(PackedPos.of(14, 6, 0)), "a genuine ground pillar still grants full span");
    }

    private static void testEvaluateSplitsStableAndUnsupported() {
        System.out.println("evaluate() returns the complete stable AND unsupported split for cancellation");
        FakeGrid g = new FakeGrid();
        for (int y = 0; y <= 5; y++) {
            g.anchor(0, y, 0);
        }
        g.beamX(1, 8, 5, 0, Material.WOOD.maxSpan()); // span 4: x=1..4 stable, x=5..8 doomed
        StructuralEngine.Evaluation ev = engine(g).evaluate(PackedPos.of(8, 5, 0));
        check(!ev.overBudget, "not over budget");
        for (int x = 1; x <= 4; x++) {
            check(ev.stable.contains(PackedPos.of(x, 5, 0)), "x=" + x + " reported stable");
            check(!ev.unsupported.contains(PackedPos.of(x, 5, 0)), "x=" + x + " not in unsupported");
        }
        for (int x = 5; x <= 8; x++) {
            check(ev.unsupported.contains(PackedPos.of(x, 5, 0)), "x=" + x + " reported unsupported");
            check(!ev.stable.contains(PackedPos.of(x, 5, 0)), "x=" + x + " not in stable");
        }
    }

    private static void testEvaluateFlagsOverBudget() {
        System.out.println("evaluate() flags an over-budget cluster instead of silently no-opping");
        FakeGrid g = new FakeGrid();
        for (int y = 0; y <= 5; y++) {
            g.anchor(0, y, 0);
        }
        g.beamX(1, 20, 5, 0, Material.WOOD.maxSpan());
        StructuralEngine.Evaluation ev =
                new StructuralEngine(g, SupportSolver.DEFAULT_CAP, 3).evaluate(PackedPos.of(20, 5, 0));
        check(ev.overBudget, "overBudget flag is set");
        check(ev.unsupported.isEmpty(), "over-budget evaluation collapses nothing (fail-safe)");
    }

    private static void testOnlyUnsupportedTailCollapses() {
        System.out.println("collapse is local: only the over-span tail is reported");
        FakeGrid g = new FakeGrid();
        for (int y = 0; y <= 5; y++) {
            g.anchor(0, y, 0);
        }
        g.beamX(1, 8, 5, 0, Material.WOOD.maxSpan()); // wood span 4
        Set<Long> doomed = engine(g).findUnsupported(PackedPos.of(8, 5, 0));
        for (int x = 1; x <= 4; x++) {
            check(!doomed.contains(PackedPos.of(x, 5, 0)), "x=" + x + " survives (not in collapse set)");
        }
        for (int x = 5; x <= 8; x++) {
            check(doomed.contains(PackedPos.of(x, 5, 0)), "x=" + x + " is in the collapse set");
        }
    }

    private static void testSnowLoadPenalty() {
        System.out.println("snow load: depth costs span, capped, with strong materials immune");
        // Defaults: one span per 3 layers, at most 3, immune from span 12 up.
        check(SnowLoad.penalty(0, 3, 3) == 0, "bare block pays nothing");
        check(SnowLoad.penalty(2, 3, 3) == 0, "a dusting (2 layers) is not yet a full span");
        check(SnowLoad.penalty(3, 3, 3) == 1, "3 layers costs exactly 1 span");
        check(SnowLoad.penalty(SnowLoad.FULL_DEPTH, 3, 3) == 2, "8 layers costs 2 spans");
        check(SnowLoad.penalty(SnowLoad.FULL_DEPTH, 1, 3) == 3, "the cap holds at deep snow");
        check(SnowLoad.penalty(SnowLoad.FULL_DEPTH, 0, 3) == 0, "layersPerSpan=0 disables snow load");

        check(SnowLoad.loadedSpan(4, SnowLoad.FULL_DEPTH, 3, 3, 12) == 2, "wood 4 → 2 under full snow");
        check(SnowLoad.loadedSpan(7, SnowLoad.FULL_DEPTH, 3, 3, 12) == 5, "stone 7 → 5 under full snow");
        check(SnowLoad.loadedSpan(20, SnowLoad.FULL_DEPTH, 3, 3, 12) == 20, "metal 20 shrugs snow off");
        check(SnowLoad.loadedSpan(4, 0, 3, 3, 12) == 4, "no snow leaves the span untouched");
        check(SnowLoad.loadedSpan(1, SnowLoad.FULL_DEPTH, 1, 3, 12) == 0, "span never goes below 0");
    }

    private static void testSnowLoadShortensCantilever() {
        System.out.println("snow load: a laden wood roof sheds the tail it could carry when bare");
        int bare = Material.WOOD.maxSpan(); // 4
        int laden = SnowLoad.loadedSpan(bare, SnowLoad.FULL_DEPTH, 3, 3, 12); // 2
        check(laden == 2, "full snow takes wood from " + bare + " to 2");

        FakeGrid g = new FakeGrid();
        for (int y = 0; y <= 5; y++) {
            g.anchor(0, y, 0);
        }
        g.beamX(1, 4, 5, 0, laden); // the same beam that stands bare, now snowed on
        Set<Long> doomed = engine(g).findUnsupported(PackedPos.of(4, 5, 0));
        for (int x = 1; x <= 2; x++) {
            check(!doomed.contains(PackedPos.of(x, 5, 0)), "x=" + x + " still holds under snow");
        }
        for (int x = 3; x <= 4; x++) {
            check(doomed.contains(PackedPos.of(x, 5, 0)), "x=" + x + " sheds under snow");
        }
    }
}
