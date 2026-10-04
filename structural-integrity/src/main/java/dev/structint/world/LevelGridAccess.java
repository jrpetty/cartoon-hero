package dev.structint.world;

import dev.structint.Config;
import dev.structint.core.CellRole;
import dev.structint.core.GridAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Adapts a live server level to the solver's {@link GridAccess} view.
 *
 * <p>Role resolution per position:
 * <ul>
 *   <li>not loaded / air / fluid / non-full / exempt → {@link CellRole#EMPTY} (with one
 *       deliberate exception below for unloaded chunks);</li>
 *   <li>foundation block → {@link CellRole#ANCHOR};</li>
 *   <li>full solid block that is <b>not</b> player-managed → {@link CellRole#ANCHOR}
 *       (natural terrain, always stable);</li>
 *   <li>full solid block that <b>is</b> player-managed → {@link CellRole#STRUCTURAL}.</li>
 * </ul>
 *
 * <p><b>Unloaded-chunk fail-safe:</b> a position in an unloaded chunk is reported as
 * {@link CellRole#ANCHOR}. This means a structure running off the edge of loaded terrain is
 * considered supported there and is never collapsed because of something we cannot see — the
 * system stays strictly chunk-local and never chases work into unloaded chunks.
 *
 * <p>A short-lived per-instance role cache makes a single flood+solve pass cheap: each position
 * is classified at most once even though flood and solve both visit it repeatedly. Create a
 * fresh instance per logical edit (they are cheap) so the cache never goes stale.
 */
public final class LevelGridAccess implements GridAccess {

    private final ServerLevel level;
    private final boolean onlyPlayerPlaced = Config.ONLY_PLAYER_PLACED.get();
    private final boolean snowLoad = BlockClassifier.snowLoadEnabled();
    private final Long2ObjectOpenHashMap<CellRole> roleCache = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<BlockState> stateCache = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    // One-entry chunk cache. A flood walks spatially coherent cells, so nearly every lookup
    // lands in the chunk the previous one did; this turns three chunk-map lookups per cell
    // (loaded? / block state / managed set) into roughly one per chunk.
    private long cachedChunkKey;
    private LevelChunk cachedChunk;
    private boolean hasCachedChunk;

    public LevelGridAccess(ServerLevel level) {
        this.level = level;
    }

    @Override
    public CellRole roleAt(int x, int y, int z) {
        return roleAt(BlockPos.asLong(x, y, z));
    }

    /** Packed keys share {@code BlockPos.asLong}'s layout, so the solver's longs index directly. */
    @Override
    public CellRole roleAt(long packed) {
        CellRole cached = roleCache.get(packed);
        if (cached != null) {
            return cached;
        }
        CellRole role = compute(packed);
        roleCache.put(packed, role);
        return role;
    }

    private CellRole compute(long packed) {
        int y = BlockPos.getY(packed);
        if (level.isOutsideBuildHeight(y)) {
            // Below the world floor reads as solid ground; above the ceiling is open air.
            return y < level.getMinBuildHeight() ? CellRole.ANCHOR : CellRole.EMPTY;
        }
        LevelChunk chunk = chunk(packed);
        if (chunk == null) {
            return CellRole.ANCHOR; // fail-safe: never collapse into the unloaded unknown
        }

        BlockState state = state(packed, chunk);
        if (BlockClassifier.isFoundation(state)) {
            return CellRole.ANCHOR;
        }
        cursor.set(packed);
        if (!BlockClassifier.isLoadBearing(state, level, cursor)) {
            return CellRole.EMPTY;
        }
        if (!onlyPlayerPlaced || StructuralData.managed(chunk).contains(packed)) {
            return CellRole.STRUCTURAL; // player-placed (or all-blocks mode): subject to the rules
        }
        // Natural terrain: an anchor only if it is genuinely solid ground, so natural grass,
        // flowers, vines, snow, torches, etc. don't become free infinite-reach anchors.
        return BlockClassifier.isSturdySupport(state, level, cursor) ? CellRole.ANCHOR : CellRole.EMPTY;
    }

    @Override
    public int maxSpanAt(int x, int y, int z) {
        return maxSpanAt(BlockPos.asLong(x, y, z));
    }

    @Override
    public int maxSpanAt(long packed) {
        BlockState state = state(packed);
        if (!snowLoad) {
            return BlockClassifier.spanOf(state);
        }
        // Snow resting on the block eats into how far it can carry. Reading one cell up is cheap
        // and cached, and the penalty is a pure function of depth — clear the snow and the full
        // span is back on the next check.
        return BlockClassifier.spanUnderSnow(state, state(BlockPos.offset(packed, 0, 1, 0)));
    }

    private BlockState state(long packed) {
        BlockState cached = stateCache.get(packed);
        if (cached != null) {
            return cached;
        }
        LevelChunk chunk = level.isOutsideBuildHeight(BlockPos.getY(packed)) ? null : chunk(packed);
        // Outside the world or in an unloaded chunk reads as void air, as Level#getBlockState
        // reports — but without Level's habit of loading the chunk to answer.
        BlockState s = chunk != null
                ? chunk.getBlockState(cursor.set(packed))
                : Blocks.VOID_AIR.defaultBlockState();
        stateCache.put(packed, s);
        return s;
    }

    private BlockState state(long packed, LevelChunk chunk) {
        BlockState cached = stateCache.get(packed);
        if (cached != null) {
            return cached;
        }
        BlockState s = chunk.getBlockState(cursor.set(packed));
        stateCache.put(packed, s);
        return s;
    }

    /** The loaded chunk holding {@code packed}, or {@code null}. Never loads or generates one. */
    private LevelChunk chunk(long packed) {
        int cx = BlockPos.getX(packed) >> 4;
        int cz = BlockPos.getZ(packed) >> 4;
        long key = ChunkPos.asLong(cx, cz);
        if (!hasCachedChunk || key != cachedChunkKey) {
            cachedChunk = StructuralData.loadedChunk(level, cx, cz);
            cachedChunkKey = key;
            hasCachedChunk = true;
        }
        return cachedChunk;
    }
}
