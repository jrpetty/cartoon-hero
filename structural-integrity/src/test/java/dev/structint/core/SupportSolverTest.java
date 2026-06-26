package dev.structint.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit view of the structural core, run by {@code gradle test}. These are the same scenarios
 * exercised by {@link CoreSelfTest} (the dependency-free runner), kept here so the algorithm is
 * covered by the standard build without needing a Minecraft runtime.
 */
class SupportSolverTest {

    /** Minimal in-memory grid. */
    static final class Grid implements GridAccess {
        final Map<Long, int[]> cells = new HashMap<>();

        Grid anchor(int x, int y, int z) {
            cells.put(PackedPos.of(x, y, z), new int[]{CellRole.ANCHOR.ordinal(), 0});
            return this;
        }

        Grid block(int x, int y, int z, int span) {
            cells.put(PackedPos.of(x, y, z), new int[]{CellRole.STRUCTURAL.ordinal(), span});
            return this;
        }

        Grid beam(int x0, int x1, int y, int span) {
            for (int x = x0; x <= x1; x++) {
                block(x, y, 0, span);
            }
            return this;
        }

        Grid pillar(int x, int y0, int y1, int span) {
            for (int y = y0; y <= y1; y++) {
                block(x, y, 0, span);
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

    private static StructuralEngine engine(Grid g) {
        return new StructuralEngine(g, SupportSolver.DEFAULT_CAP, 100_000);
    }

    @Test
    void blockOnGroundIsStable() {
        Grid g = new Grid().anchor(0, 0, 0).block(0, 1, 0, Material.WOOD.maxSpan());
        assertTrue(engine(g).isSupported(PackedPos.of(0, 1, 0)));
    }

    @Test
    void floatingBlockFalls() {
        Grid g = new Grid().block(0, 10, 0, Material.STONE.maxSpan());
        assertFalse(engine(g).isSupported(PackedPos.of(0, 10, 0)));
    }

    @Test
    void woodCantileversExactlyFour() {
        Grid g = new Grid();
        for (int y = 0; y <= 5; y++) g.anchor(0, y, 0);
        g.beam(1, 6, 5, Material.WOOD.maxSpan());
        StructuralEngine e = engine(g);
        assertTrue(e.isSupported(PackedPos.of(4, 5, 0)), "x=4 within span 4");
        assertFalse(e.isSupported(PackedPos.of(5, 5, 0)), "x=5 beyond span 4");
    }

    @Test
    void stoneReachesFurtherThanWood() {
        Grid g = new Grid();
        for (int y = 0; y <= 5; y++) g.anchor(0, y, 0);
        g.beam(1, 9, 5, Material.STONE.maxSpan());
        StructuralEngine e = engine(g);
        assertTrue(e.isSupported(PackedPos.of(7, 5, 0)));
        assertFalse(e.isSupported(PackedPos.of(8, 5, 0)));
    }

    @Test
    void tallPillarStaysUp() {
        Grid g = new Grid().anchor(0, 0, 0).pillar(0, 1, 40, Material.STONE.maxSpan());
        assertTrue(engine(g).isSupported(PackedPos.of(0, 40, 0)));
    }

    @Test
    void roofIsRedundantAcrossTwoWalls() {
        Grid g = new Grid();
        g.anchor(0, 0, 0).anchor(14, 0, 0);
        g.pillar(0, 1, 5, Material.STONE.maxSpan());
        g.pillar(14, 1, 5, Material.STONE.maxSpan());
        g.beam(0, 14, 5, Material.STONE.maxSpan());
        StructuralEngine e = engine(g);
        for (int x = 0; x <= 14; x++) {
            assertTrue(e.isSupported(PackedPos.of(x, 5, 0)), "roof x=" + x);
        }
    }

    @Test
    void removingOneWallDropsOnlyTheFarOverhang() {
        Grid g = new Grid();
        g.anchor(0, 0, 0).pillar(0, 1, 5, Material.STONE.maxSpan());
        g.beam(0, 14, 5, Material.STONE.maxSpan()); // only the left wall now
        StructuralEngine e = engine(g);
        for (int x = 0; x <= 7; x++) {
            assertTrue(e.isSupported(PackedPos.of(x, 5, 0)), "near half x=" + x);
        }
        assertFalse(e.isSupported(PackedPos.of(8, 5, 0)), "far overhang x=8");
    }

    @Test
    void weakLinkLimitsBeam() {
        Grid g = new Grid();
        for (int y = 0; y <= 5; y++) g.anchor(0, y, 0);
        g.beam(1, 4, 5, Material.DIRT.maxSpan());
        StructuralEngine e = engine(g);
        assertTrue(e.isSupported(PackedPos.of(1, 5, 0)));
        assertFalse(e.isSupported(PackedPos.of(2, 5, 0)));
    }

    @Test
    void collapseSetIsLocalTail() {
        Grid g = new Grid();
        for (int y = 0; y <= 5; y++) g.anchor(0, y, 0);
        g.beam(1, 8, 5, Material.WOOD.maxSpan());
        Set<Long> doomed = engine(g).findUnsupported(PackedPos.of(8, 5, 0));
        assertFalse(doomed.contains(PackedPos.of(4, 5, 0)), "x=4 survives");
        assertTrue(doomed.contains(PackedPos.of(5, 5, 0)), "x=5 falls");
        assertTrue(doomed.contains(PackedPos.of(8, 5, 0)), "x=8 falls");
    }

    @Test
    void overBudgetStructureIsTreatedAsStable() {
        // A region cap of 3 forces the flood to bail; bail = treat as stable (fail-safe).
        Grid g = new Grid();
        for (int y = 0; y <= 5; y++) g.anchor(0, y, 0);
        g.beam(1, 20, 5, Material.WOOD.maxSpan());
        StructuralEngine tiny = new StructuralEngine(g, SupportSolver.DEFAULT_CAP, 3);
        assertTrue(tiny.findUnsupported(PackedPos.of(20, 5, 0)).isEmpty(),
                "over-budget flood collapses nothing");
    }
}
