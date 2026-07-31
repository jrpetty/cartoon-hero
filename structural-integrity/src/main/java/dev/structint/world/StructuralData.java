package dev.structint.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Thin helpers for reading and mutating the per-chunk {@link ManagedBlocks} attachment without
 * forcing chunk loads. All reads are guarded against unloaded chunks so the structural system
 * never touches terrain that is not in memory.
 */
public final class StructuralData {

    /** @return the loaded chunk for {@code pos}, or {@code null} if it is not in memory. */
    private static LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        // getChunk with load=false returns null rather than generating/loading the chunk.
        return (LevelChunk) level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
    }

    public static boolean isManaged(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) {
            return false;
        }
        return chunk.getData(ModAttachments.MANAGED_BLOCKS.get()).contains(pos.asLong());
    }

    public static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return loadedChunk(level, pos) != null;
    }

    /** @return the loaded chunk at these chunk coordinates, or {@code null} if it is not in memory. */
    public static LevelChunk loadedChunk(ServerLevel level, int chunkX, int chunkZ) {
        return (LevelChunk) level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    /** The managed-block record of an already-loaded chunk. */
    public static ManagedBlocks managed(LevelChunk chunk) {
        return chunk.getData(ModAttachments.MANAGED_BLOCKS.get());
    }

    public static void setManaged(ServerLevel level, BlockPos pos, boolean managed) {
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) {
            return;
        }
        ManagedBlocks data = chunk.getData(ModAttachments.MANAGED_BLOCKS.get());
        boolean changed = managed ? data.add(pos.asLong()) : data.remove(pos.asLong());
        if (changed) {
            chunk.setUnsaved(true);
        }
    }

    private StructuralData() {
    }
}
