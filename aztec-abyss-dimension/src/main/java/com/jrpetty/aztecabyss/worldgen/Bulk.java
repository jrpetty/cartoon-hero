package com.jrpetty.aztecabyss.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * How this mod writes blocks when it is writing millions of them.
 *
 * <h2>What was wrong with {@code level.setBlock(pos, state, 2)}</h2>
 *
 * <p>Stamping the maze is 4.3 million block writes - 468 per cell across 9,216
 * cells - and every one of them went through {@code ServerLevel.setBlock} with
 * flag 2. That flag means "tell the clients", and it is the right flag for
 * placing a single block. For a build it is three separate kinds of waste, and
 * the third one is the expensive one:
 *
 * <ul>
 *   <li><b>A fresh {@link BlockPos} per write.</b> 4.3 million short-lived
 *       objects, all of them garbage a moment later.</li>
 *   <li><b>A chunk lookup per write.</b> {@code setBlock} resolves the chunk from
 *       the position every single time, even when the previous six hundred
 *       writes were in the same chunk.</li>
 *   <li><b>A neighbour-shape cascade per write.</b> This is the big one.
 *       {@code Level.markAndNotifyBlock} runs {@code updateNeighbourShapes}
 *       unless flag 16 is set, and flag 16 was never set anywhere in this mod.
 *       Every write therefore read its six neighbours and asked each of them
 *       whether it wanted to change shape - roughly twelve extra operations per
 *       block, or fifty million across a build, to reconcile stone bricks
 *       against stone bricks.</li>
 * </ul>
 *
 * <p>On top of that, {@code setBlock} sends a block-change notification to
 * clients for each write. During the initial stamp there is nobody in the
 * dimension to send it to: the maze refuses entry while it is building and
 * admits the queue afterwards, at which point the chunks go out whole anyway.
 *
 * <h2>What this does instead</h2>
 *
 * <p>A {@link Writer} is a cursor over one level. It keeps one mutable position
 * and one resolved chunk, so a run of writes down a wall costs one chunk lookup
 * and no allocation at all. Then it picks a path:
 *
 * <ul>
 *   <li><b>Nobody in the level</b> - writes go straight into the chunk. No
 *       client packet, no point-of-interest check, no shape cascade. This is the
 *       case during a build, which is where the millions of writes are.</li>
 *   <li><b>Somebody is in the level</b> - writes go through {@code setBlock}
 *       with {@code 2 | 16}: clients are told, because somebody can see it, but
 *       the shape cascade is still skipped. This is the case for the nightly
 *       reshape and for an admin rebuilding under people's feet.</li>
 * </ul>
 *
 * <p>Skipping shape updates is safe for what this writes and only for what this
 * writes: floors, walls, bedrock, barriers and air. None of them have connection
 * state to reconcile. Anything with a shape that depends on its neighbours - a
 * wall block, a fence, a pane, redstone - must keep going through
 * {@code level.setBlock} with the ordinary flags, and in this mod all of those
 * are one-off decorations placed outside these loops.
 *
 * <p>It also means a wall taken away by the reshape no longer knocks the ivy off
 * the wall next to it and drops it as an item. That is the correct outcome twice
 * over: the maze stops littering itself with vines every midnight, and it stops
 * paying for the drop.
 */
public final class Bulk {

    /**
     * Tell clients (2), skip the neighbour-shape cascade (16).
     *
     * <p>Written as literals rather than the {@code Block.UPDATE_*} constants to
     * match how the rest of the mod spells its flags, so a reader comparing this
     * against the four hundred ordinary {@code setBlock(..., 2)} calls elsewhere
     * can see what changed without a lookup.
     */
    public static final int FLAGS = 2 | 16;

    private Bulk() {
    }

    /** Opens a cursor for a burst of writes. Cheap; make one per tick of work. */
    public static Writer writer(ServerLevel level) {
        return new Writer(level);
    }

    /**
     * A write cursor over one level.
     *
     * <p>Not thread-safe and not meant to be: it holds a mutable position and a
     * cached chunk, and everything that builds in this mod builds on the server
     * thread. Do not keep one across ticks either - the empty check that decides
     * whether clients need telling is taken once, when the cursor is opened.
     */
    public static final class Writer {

        private final ServerLevel level;
        private final boolean unwatched;
        private final int minY;
        private final int maxY;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        private LevelChunk chunk;
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;

        private Writer(ServerLevel level) {
            this.level = level;
            this.unwatched = level.players().isEmpty();
            this.minY = level.getMinBuildHeight();
            this.maxY = level.getMaxBuildHeight();
        }

        /** Whether this cursor is taking the straight-into-the-chunk path. */
        public boolean unwatched() {
            return unwatched;
        }

        /**
         * Writes one structural block.
         *
         * <p>Only for blocks with no block entity and no neighbour-dependent
         * shape. Use {@link #place} for anything else.
         */
        public void set(int x, int y, int z, BlockState state) {
            // The height guard that setBlock does for us and a direct chunk
            // write does not. Without it a write above the world - which the sky
            // lid used to do - indexes past the end of the section array instead
            // of quietly doing nothing. Bounds are read once, in the constructor.
            if (y < minY || y >= maxY) {
                return;
            }
            cursor.set(x, y, z);
            if (!unwatched) {
                level.setBlock(cursor, state, FLAGS);
                return;
            }
            chunkAt(x, z).setBlockState(cursor, state, false);
        }

        /** As {@link #set(int, int, int, BlockState)}, from a position. */
        public void set(BlockPos pos, BlockState state) {
            set(pos.getX(), pos.getY(), pos.getZ(), state);
        }

        /** A vertical run in one column: one chunk lookup for the whole thing. */
        public void column(int x, int z, int fromY, int toY, BlockState state) {
            for (int y = fromY; y <= toY; y++) {
                set(x, y, z, state);
            }
        }

        /**
         * Writes a block that needs the ordinary notification path - a block
         * entity, a connective shape, anything a client has to be told about
         * individually.
         */
        public void place(BlockPos pos, BlockState state, int flags) {
            level.setBlock(pos, state, flags);
        }

        /** Reads through the cached chunk rather than resolving it again. */
        public BlockState state(int x, int y, int z) {
            if (y < minY || y >= maxY) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            cursor.set(x, y, z);
            return chunkAt(x, z).getBlockState(cursor);
        }

        private LevelChunk chunkAt(int x, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            if (chunk == null || cx != chunkX || cz != chunkZ) {
                chunk = level.getChunk(cx, cz);
                chunkX = cx;
                chunkZ = cz;
            }
            return chunk;
        }
    }
}
