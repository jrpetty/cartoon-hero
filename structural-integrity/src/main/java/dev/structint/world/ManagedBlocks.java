package dev.structint.world;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-chunk record of which block positions are <b>player-managed structural blocks</b> —
 * i.e. placed by a player and therefore subject to the integrity rules.
 *
 * <p>This is the single source of truth for the natural-vs-player distinction. Any solid full
 * block <em>not</em> in a chunk's managed set is treated as natural terrain (an anchor). The set
 * stores packed {@code BlockPos.asLong()} keys, which (by design of {@code PackedPos}) are the
 * same longs the core solver uses.
 *
 * <p>Stored as a chunk {@code AttachmentType} so it saves and loads with the chunk and is
 * automatically discarded when the chunk is — keeping the whole system chunk-local with no
 * global world data.
 */
public final class ManagedBlocks {

    /** Serialized as a flat list of packed longs. */
    public static final Codec<ManagedBlocks> CODEC = Codec.LONG.listOf().xmap(
            list -> {
                LongOpenHashSet set = new LongOpenHashSet(list.size());
                for (long l : list) {
                    set.add(l);
                }
                return new ManagedBlocks(set);
            },
            managed -> {
                List<Long> list = new ArrayList<>(managed.positions.size());
                for (LongIterator it = managed.positions.iterator(); it.hasNext(); ) {
                    list.add(it.nextLong());
                }
                return list;
            });

    private final LongOpenHashSet positions;

    public ManagedBlocks() {
        this.positions = new LongOpenHashSet();
    }

    private ManagedBlocks(LongOpenHashSet positions) {
        this.positions = positions;
    }

    public boolean contains(long packedPos) {
        return positions.contains(packedPos);
    }

    /** @return true if the set changed. */
    public boolean add(long packedPos) {
        return positions.add(packedPos);
    }

    /** @return true if the set changed. */
    public boolean remove(long packedPos) {
        return positions.remove(packedPos);
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public LongSet view() {
        return positions;
    }
}
