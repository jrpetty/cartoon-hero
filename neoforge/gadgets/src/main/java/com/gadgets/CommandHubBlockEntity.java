package com.gadgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Command Hub: a base-wide monitoring console. Link Item Counters and
 * Stock Monitors to it with the Monitor Wand and it aggregates their live
 * numbers every second — throughput per counter, stock per monitor, offline
 * markers for unloaded chunks — across dimensions. The screen shows a
 * summary; the full board opens on right-click.
 *
 * <p>It also drives one output: redstone through the block itself, on
 * whenever any linked monitor is low or any linked counter has stalled, so a
 * single hub can trigger a base-wide alarm instead of wiring every gadget's
 * own signal separately.
 */
public class CommandHubBlockEntity extends BlockEntity {
    public static final int MAX_NODES = 32;
    private static final int INTERVAL = 20;

    public static final int TYPE_COUNTER = 0;
    public static final int TYPE_MONITOR = 1;

    /** One linked gadget and its latest snapshot (also the sync format). */
    public static class Node {
        public int type;
        public String dim = "";
        public long pos = 0L;
        public boolean online = false;
        public String label = "";
        public long a = 0; // counter: rate/min   · monitor: stock count
        public long b = 0; // counter: rate/hour  · monitor: alert threshold
        public long c = 0; // counter: total      · monitor: low flag (1/0)
        public long d = 0; // counter: stalled (1/0) · monitor: distinct item types

        /** True when this node is in a state the hub's alarm output should count. */
        public boolean alarmed() {
            return online && (type == TYPE_COUNTER ? d != 0 : c != 0);
        }

        public CompoundTag toNbt() {
            CompoundTag n = new CompoundTag();
            n.putInt("T", type);
            n.putString("D", dim);
            n.putLong("P", pos);
            n.putBoolean("O", online);
            n.putString("L", label);
            n.putLong("A", a);
            n.putLong("B", b);
            n.putLong("C", c);
            n.putLong("D2", d);
            return n;
        }

        public static Node fromNbt(CompoundTag n) {
            Node node = new Node();
            node.type = n.getInt("T");
            node.dim = n.getString("D");
            node.pos = n.getLong("P");
            node.online = n.getBoolean("O");
            node.label = n.getString("L");
            node.a = n.getLong("A");
            node.b = n.getLong("B");
            node.c = n.getLong("C");
            node.d = n.getLong("D2");
            return node;
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private String lastSync = "";

    public CommandHubBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.COMMAND_HUB_BE.get(), pos, state);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    /** Link a gadget; returns false when the board is full or already linked. */
    public boolean addNode(int type, String dim, BlockPos nodePos) {
        long packed = nodePos.asLong();
        for (Node n : nodes) {
            if (n.dim.equals(dim) && n.pos == packed) {
                return false;
            }
        }
        if (nodes.size() >= MAX_NODES) {
            return false;
        }
        Node n = new Node();
        n.type = type;
        n.dim = dim;
        n.pos = packed;
        nodes.add(n);
        setChanged();
        return true;
    }

    /** Drop one linked gadget from the board; false when it wasn't on it. */
    public boolean removeNode(String dim, BlockPos nodePos) {
        long packed = nodePos.asLong();
        boolean removed = nodes.removeIf(n -> n.dim.equals(dim) && n.pos == packed);
        if (removed) {
            setChanged();
            sync();
        }
        return removed;
    }

    public void clearNodes() {
        nodes.clear();
        setChanged();
        sync();
    }

    /** Drop the link at {@code index} — what the board's per-row ✕ button calls. */
    public boolean removeNodeAt(int index) {
        if (index < 0 || index >= nodes.size()) {
            return false;
        }
        nodes.remove(index);
        setChanged();
        sync();
        return true;
    }

    /**
     * Forget links whose gadget is gone for good.
     *
     * <p>Only a loaded chunk can prove absence: an unloaded one reads as empty
     * and would drop every link in an unvisited base. So a node is dropped only
     * when its chunk is loaded and the block there is no longer a counter or a
     * monitor.
     */
    private boolean pruneDead(MinecraftServer server) {
        return nodes.removeIf(n -> {
            ServerLevel w = levelOf(server, n);
            if (w == null) {
                return false; // dimension missing — keep, it may come back
            }
            BlockPos p = BlockPos.of(n.pos);
            if (!w.isLoaded(p)) {
                return false; // unloaded is "offline", never "deleted"
            }
            BlockEntity there = w.getBlockEntity(p);
            return !(there instanceof ItemCounterBlockEntity || there instanceof StockMonitorBlockEntity);
        });
    }

    @Nullable
    private static ServerLevel levelOf(MinecraftServer server, Node n) {
        ResourceLocation dimId = ResourceLocation.tryParse(n.dim);
        return dimId == null ? null
                : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId));
    }

    // --- screen summary ---

    public int nodeCount() {
        return nodes.size();
    }

    public long totalRateMin() {
        long sum = 0;
        for (Node n : nodes) {
            if (n.type == TYPE_COUNTER && n.online) {
                sum += n.a;
            }
        }
        return sum;
    }

    /** Nodes currently flagged: a monitor gone low, or a counter gone stalled. */
    public int alarmCount() {
        int n = 0;
        for (Node node : nodes) {
            if (node.alarmed()) {
                n++;
            }
        }
        return n;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CommandHubBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L || be.nodes.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        // A broken counter or monitor drops off the board rather than sitting
        // there as a permanent "offline" ghost.
        be.pruneDead(server);
        for (Node n : be.nodes) {
            be.refresh(server, n);
        }

        boolean alarmed = be.alarmCount() > 0;
        if (state.getValue(CommandHubBlock.ALARM) != alarmed) {
            level.setBlock(pos, state.setValue(CommandHubBlock.ALARM, alarmed), Block.UPDATE_ALL);
            // A cue only on the transition into alarm — polling every interval
            // would mean a constant chime for as long as anything stays flagged.
            if (alarmed) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.5F, 0.8F);
            }
        }

        // Push to clients only when the board actually changed.
        String fingerprint = be.buildList().toString();
        if (!fingerprint.equals(be.lastSync)) {
            be.lastSync = fingerprint;
            be.setChanged();
            be.sync();
        }
    }

    private void refresh(MinecraftServer server, Node n) {
        n.online = false;
        ServerLevel w = levelOf(server, n);
        if (w == null) {
            return;
        }
        BlockPos p = BlockPos.of(n.pos);
        if (!w.isLoaded(p)) {
            return; // offline: chunk not loaded — never force it
        }
        if (w.getBlockEntity(p) instanceof ItemCounterBlockEntity counter) {
            n.online = true;
            n.label = counter.displayName();
            n.a = counter.getRateMin();
            n.b = counter.getRateHour();
            n.c = counter.getTotal();
            n.d = counter.isStalled() ? 1 : 0;
        } else if (w.getBlockEntity(p) instanceof StockMonitorBlockEntity monitor) {
            n.online = true;
            n.label = monitor.displayName();
            n.a = monitor.getCount();
            n.b = monitor.getThreshold();
            n.c = monitor.isLow() ? 1 : 0;
            n.d = monitor.getDistinctTypes();
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private ListTag buildList() {
        ListTag list = new ListTag();
        for (Node n : nodes) {
            list.add(n.toNbt());
        }
        return list;
    }

    // --- sync + persistence ---

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Nodes", buildList());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nodes.clear();
        ListTag list = tag.getList("Nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_NODES); i++) {
            nodes.add(Node.fromNbt(list.getCompound(i)));
        }
    }
}
