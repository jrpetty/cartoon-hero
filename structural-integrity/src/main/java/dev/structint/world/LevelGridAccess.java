package dev.structint.world;

import dev.structint.Config;
import dev.structint.core.CellRole;
import dev.structint.core.GridAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

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
    private final Long2ObjectOpenHashMap<CellRole> roleCache = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<BlockState> stateCache = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelGridAccess(ServerLevel level) {
        this.level = level;
    }

    @Override
    public CellRole roleAt(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        CellRole cached = roleCache.get(key);
        if (cached != null) {
            return cached;
        }
        CellRole role = compute(x, y, z);
        roleCache.put(key, role);
        return role;
    }

    private CellRole compute(int x, int y, int z) {
        cursor.set(x, y, z);

        if (level.isOutsideBuildHeight(cursor.getY())) {
            // Below the world floor reads as solid ground; above the ceiling is open air.
            return cursor.getY() < level.getMinBuildHeight()
                    ? CellRole.ANCHOR : CellRole.EMPTY;
        }
        if (!StructuralData.isChunkLoaded(level, cursor)) {
            return CellRole.ANCHOR; // fail-safe: never collapse into the unloaded unknown
        }

        BlockState state = state(x, y, z);
        if (BlockClassifier.isFoundation(state)) {
            return CellRole.ANCHOR;
        }
        if (!BlockClassifier.isLoadBearing(state, level, cursor)) {
            return CellRole.EMPTY;
        }
        boolean managed = !Config.ONLY_PLAYER_PLACED.get() || StructuralData.isManaged(level, cursor);
        if (managed) {
            return CellRole.STRUCTURAL; // player-placed (or all-blocks mode): subject to the rules
        }
        // Natural terrain: an anchor only if it is genuinely solid ground, so natural grass,
        // flowers, vines, snow, torches, etc. don't become free infinite-reach anchors.
        return BlockClassifier.isSturdySupport(state, level, cursor) ? CellRole.ANCHOR : CellRole.EMPTY;
    }

    @Override
    public int maxSpanAt(int x, int y, int z) {
        return BlockClassifier.spanOf(state(x, y, z));
    }

    private BlockState state(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        BlockState cached = stateCache.get(key);
        if (cached != null) {
            return cached;
        }
        BlockState s = level.getBlockState(cursor.set(x, y, z));
        stateCache.put(key, s);
        return s;
    }
}
