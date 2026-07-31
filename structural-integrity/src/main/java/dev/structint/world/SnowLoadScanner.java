package dev.structint.world;

import dev.structint.Config;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Keeps snow load honest.
 *
 * <p>Snow settling and melting is weather, not a player edit: it fires no break or place event,
 * so nothing would otherwise tell the structural system that a roof's span just shrank (or came
 * back). This sweeps the managed blocks near players on a slow timer, remembers how deep the snow
 * on each was, and schedules an ordinary stability check for any block whose cover has
 * <em>changed</em> since it was last seen.
 *
 * <p>Three properties keep it cheap. It only looks at blocks the mod already tracks; it only
 * looks near players, because that is the only place vanilla accumulates snow; and it re-checks
 * on change alone, so a snowed-in base costs one sweep, not one sweep per tick. A per-sweep
 * budget spreads a very large base over several sweeps rather than spiking a single tick.
 *
 * <p>The remembered depths are an in-memory cache, not saved state: after a restart the first
 * sweep simply re-checks anything currently under snow, which is the correct thing to do anyway.
 */
public final class SnowLoadScanner {

    private static final Map<ServerLevel, SnowLoadScanner> SCANNERS = new IdentityHashMap<>();

    /**
     * Ceiling on remembered positions. A player roaming a snowy world leaves entries behind for
     * places long since unloaded; past this many we simply drop the lot. The cache is an
     * optimisation, not state — forgetting it only costs one extra re-check per laden block.
     */
    private static final int MAX_REMEMBERED = 100_000;

    /** Last seen snow depth per managed block. Positions with no snow are absent, not stored. */
    private final Long2ByteOpenHashMap depths = new Long2ByteOpenHashMap();
    private int ticksSinceSweep;
    /** Rotating start index into the candidate chunk list, so budgeted sweeps cover everything. */
    private int chunkCursor;

    private SnowLoadScanner() {
        depths.defaultReturnValue((byte) 0);
    }

    static void tick(ServerLevel level) {
        if (!BlockClassifier.snowLoadEnabled()) {
            SCANNERS.remove(level); // switched off at runtime: drop the cache with it
            return;
        }
        SCANNERS.computeIfAbsent(level, l -> new SnowLoadScanner()).sweep(level);
    }

    static void forget(ServerLevel level) {
        SCANNERS.remove(level);
    }

    private void sweep(ServerLevel level) {
        if (++ticksSinceSweep < Config.SNOW_LOAD_SCAN_INTERVAL_TICKS.get()) {
            return;
        }
        ticksSinceSweep = 0;
        if (depths.size() > MAX_REMEMBERED) {
            depths.clear();
        }

        LongArrayList chunks = candidateChunks(level);
        if (chunks.isEmpty()) {
            if (!depths.isEmpty()) {
                depths.clear(); // nobody around: let the cache go rather than hold it forever
            }
            return;
        }

        int budget = Config.SNOW_LOAD_SCAN_BUDGET.get();
        int size = chunks.size();
        int start = Math.floorMod(chunkCursor, size);
        for (int i = 0; i < size && budget > 0; i++) {
            int index = (start + i) % size;
            budget -= scanChunk(level, chunks.getLong(index), budget);
            chunkCursor = index + 1;
        }
    }

    /** The loaded chunks within the configured radius of any player, each listed once. */
    private static LongArrayList candidateChunks(ServerLevel level) {
        int radius = Config.SNOW_LOAD_SCAN_RADIUS_CHUNKS.get();
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList out = new LongArrayList();
        for (ServerPlayer player : level.players()) {
            ChunkPos centre = player.chunkPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long key = ChunkPos.asLong(centre.x + dx, centre.z + dz);
                    if (seen.add(key)) {
                        out.add(key);
                    }
                }
            }
        }
        return out;
    }

    /** @return how much of the budget this chunk consumed (one unit per block examined). */
    private int scanChunk(ServerLevel level, long chunkKey, int budget) {
        LevelChunk chunk =
                StructuralData.loadedChunk(level, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        if (chunk == null) {
            return 0;
        }
        ManagedBlocks managed = StructuralData.managed(chunk);
        if (managed.isEmpty()) {
            return 0;
        }

        int examined = 0;
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (LongIterator it = managed.view().iterator(); it.hasNext() && examined < budget; ) {
            long packed = it.nextLong();
            examined++;

            above.set(BlockPos.getX(packed), BlockPos.getY(packed) + 1, BlockPos.getZ(packed));
            if (level.isOutsideBuildHeight(above.getY())) {
                continue;
            }
            int depth = BlockClassifier.snowDepthOf(level.getBlockState(above));
            if (depth == depths.get(packed)) {
                continue; // unchanged since the last sweep — the structure already reflects it
            }
            if (depth == 0) {
                depths.remove(packed);
            } else {
                depths.put(packed, (byte) depth);
            }
            StructuralManager.enqueueChange(level, packed);
        }
        return examined;
    }
}
