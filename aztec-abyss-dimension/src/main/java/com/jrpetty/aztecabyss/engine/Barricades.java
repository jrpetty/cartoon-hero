package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Boards a horde tears off and players nail back on.
 *
 * <h2>Why this is not the Bridge's barricades</h2>
 *
 * <p>There are already barricades in this mod, on the Bridge, and they cannot be
 * used here. They are addressed by gate index against a hardcoded
 * {@code ArenaMap}, with the board layout, the gate width and the pen geometry
 * all written as constants for four gates that exist in one map. Everything true
 * about them is true about that map, which is exactly the property an engine
 * cannot have.
 *
 * <p>So this is the same idea rebuilt on the only thing an authored map has: a
 * marker you stood next to and named. Its size, its board count, how fast it
 * comes apart and what it is made of are all read off the marker, so a map can
 * have one barricade or nine, three boards or twelve, planks or iron bars.
 *
 * <pre>
 *   [Barricade]
 *   id=east_door
 *   boards=5 width=3 height=4 seconds=3
 * </pre>
 *
 * <h2>What a barricade is for</h2>
 *
 * <p>Not a wall, and it must never become one. A wall a horde cannot pass is a
 * map that plays itself; a wall it passes instantly is scenery. A barricade is a
 * <em>throttle</em> - it decides how fast a room fills, and it asks a question
 * every round: which of these can you afford to keep standing, given that
 * mending one costs you the seconds you would have spent shooting.
 *
 * <p>Boards deliberately do not mend themselves between rounds. The breather is
 * the repair window, which is what gives the breather something to be for.
 */
public final class Barricades {

    /** Boards a marker gets if it does not say. Five, as the genre settled on. */
    private static final int DEFAULT_BOARDS = 5;

    /** Seconds of a mob standing at a barricade before a board comes off. */
    private static final int DEFAULT_SECONDS = 3;

    /** How close a player must be to nail one back on. */
    private static final double REPAIR_RANGE = 4.0;

    private final EngineArena arena;
    private final ServerLevel level;
    private final List<Marker> markers = new ArrayList<>();

    /** Boards still standing, per marker, by index. */
    private final int[] boards;

    /** Ticks a mob has spent gnawing, per marker. */
    private final int[] gnaw;

    public Barricades(EngineArena arena, ServerLevel level, List<Marker> found) {
        this.arena = arena;
        this.level = level;
        this.markers.addAll(found);
        this.boards = new int[markers.size()];
        this.gnaw = new int[markers.size()];
    }

    public boolean isEmpty() {
        return markers.isEmpty();
    }

    /** Puts every board back and stamps them into the world. Called on run start. */
    public void resetAll() {
        for (int i = 0; i < markers.size(); i++) {
            boards[i] = max(i);
            gnaw[i] = 0;
            paint(i);
        }
    }

    private int max(int i) {
        return Math.max(1, Math.min(16, markers.get(i).intArg("boards", DEFAULT_BOARDS)));
    }

    /**
     * Where board {@code n} sits.
     *
     * <p>Laid bottom-upward across the opening, and stripped from the top down,
     * so the last plank standing is the one across the floor. That ordering is
     * the whole readability of the thing: a glance at a barricade tells you how
     * long it has left without a number anywhere.
     */
    private List<BlockPos> cellsFor(int i, int board) {
        Marker m = markers.get(i);
        int width = Math.max(1, Math.min(9, m.intArg("width", 3)));
        boolean alongX = m.arg("axis", "x").equalsIgnoreCase("x");
        List<BlockPos> out = new ArrayList<>();
        int half = width / 2;
        for (int off = -half; off <= half; off++) {
            out.add(m.pos().offset(alongX ? off : 0, board, alongX ? 0 : off));
        }
        return out;
    }

    private BlockState plank(int i) {
        String id = markers.get(i).arg("block", "minecraft:oak_planks");
        var rl = net.minecraft.resources.ResourceLocation.tryParse(
                id.toLowerCase(java.util.Locale.ROOT));
        if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(rl)) {
            return Blocks.OAK_PLANKS.defaultBlockState();
        }
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
    }

    /** Writes the world to match the board count. Only the row that changed. */
    private void paint(int i) {
        int have = boards[i];
        int all = max(i);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState wood = plank(i);
        for (int b = 0; b < all; b++) {
            for (BlockPos at : cellsFor(i, b)) {
                arena.setTracked(at, b < have ? wood : air);
            }
        }
    }

    /**
     * A second of the horde working on the boards.
     *
     * <p>Counted per barricade rather than per mob: ten zombies on one gate is
     * still one gate coming apart, at the speed the map said. Otherwise a big
     * round strips every barricade in the time it takes to walk to one, and the
     * throttle stops throttling exactly when it is needed.
     */
    public void tick() {
        for (int i = 0; i < markers.size(); i++) {
            if (boards[i] <= 0) {
                continue;
            }
            Marker m = markers.get(i);
            double radius = Math.max(1, m.intArg("radius", 3));
            AABB box = new AABB(m.pos()).inflate(radius);
            boolean biting = !level.getEntitiesOfClass(Mob.class, box,
                    e -> e.getPersistentData().getBoolean("aztecabyss_engine_mob") && e.isAlive())
                    .isEmpty();
            if (!biting) {
                gnaw[i] = 0;
                continue;
            }
            int need = Math.max(1, m.intArg("seconds", DEFAULT_SECONDS)) * 20;
            if (++gnaw[i] < need) {
                continue;
            }
            gnaw[i] = 0;
            boards[i]--;
            paint(i);
            level.playSound(null, m.pos(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2F, 0.8F);
            level.sendParticles(ParticleTypes.CRIT,
                    m.pos().getX() + 0.5, m.pos().getY() + 1.5, m.pos().getZ() + 0.5,
                    8, 0.4, 0.4, 0.4, 0.02);
            Script.fireAmount(arena, level, arena.rulesetId(), "barricade_broken",
                    null, id(i), boards[i]);
        }
    }

    /**
     * Nails a board back on, if somebody is stood at one that needs it.
     *
     * @return true if this counted as a repair, so the caller can consume the hit
     */
    public boolean repair(ServerPlayer player) {
        for (int i = 0; i < markers.size(); i++) {
            if (boards[i] >= max(i)) {
                continue;
            }
            if (player.blockPosition().distSqr(markers.get(i).pos())
                    > REPAIR_RANGE * REPAIR_RANGE) {
                continue;
            }
            boards[i]++;
            paint(i);
            level.playSound(null, markers.get(i).pos(), SoundEvents.WOOD_PLACE,
                    SoundSource.BLOCKS, 1.0F, 1.1F);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§eBoard nailed on §8— " + boards[i] + "/" + max(i)), true);
            Script.fireAmount(arena, level, arena.rulesetId(), "barricade_repaired",
                    player, id(i), boards[i]);
            return true;
        }
        return false;
    }

    private String id(int i) {
        return markers.get(i).arg("id", markers.get(i).arg("value", "barricade" + i));
    }

    /** How many boards are missing across the map, for the HUD. */
    public int missing() {
        int n = 0;
        for (int i = 0; i < markers.size(); i++) {
            n += max(i) - boards[i];
        }
        return n;
    }

    /** How many barricades have been stripped to nothing. */
    public int open() {
        int n = 0;
        for (int b : boards) {
            if (b <= 0) {
                n++;
            }
        }
        return n;
    }
}
