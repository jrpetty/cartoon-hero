package com.gadgets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

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
    public static final int TYPE_FLUID = 2;
    public static final int TYPE_ENERGY = 3;

    /** Short word for a node's kind, used on every board and screen. */
    public static String kindOf(int type) {
        return switch (type) {
            case TYPE_MONITOR -> "stock";
            case TYPE_FLUID -> "fluid";
            case TYPE_ENERGY -> "energy";
            default -> "counter";
        };
    }

    /** One linked gadget and its latest snapshot (also the sync format). */
    public static class Node {
        public int type;
        public String dim = "";
        public long pos = 0L;
        public boolean online = false;
        public String label = "";
        public long a = 0; // counter: rate/min   · monitor: stock  · gauge: stored
        public long b = 0; // counter: rate/hour  · monitor: alert  · gauge: capacity
        public long c = 0; // counter: total      · low flag (1/0) for everything else
        public long d = 0; // counter: stalled    · monitor: types  · gauge: percent
        /** Alarm state at the last check, so the log only records the edges. */
        public boolean wasAlarmed = false;

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
            n.putBoolean("W", wasAlarmed);
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
            node.wasAlarmed = n.getBoolean("W");
            return node;
        }
    }

    /** Longest history kept; older entries fall off the end. */
    public static final int MAX_EVENTS = 40;

    /**
     * One recorded change of state. The live board can only answer "what is
     * wrong now" — this is what answers "what broke while I was away".
     */
    public record Event(long dayTime, int type, boolean raised, String label) {
        CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putLong("T", dayTime);
            t.putInt("Y", type);
            t.putBoolean("R", raised);
            t.putString("L", label);
            return t;
        }

        static Event fromNbt(CompoundTag t) {
            return new Event(t.getLong("T"), t.getInt("Y"), t.getBoolean("R"), t.getString("L"));
        }

        /** "stalled" / "recovered" / "ran low" / "refilled". */
        public String what() {
            if (type == TYPE_COUNTER) {
                return raised ? "stalled" : "recovered";
            }
            if (type == TYPE_MONITOR) {
                return raised ? "ran low" : "restocked";
            }
            return raised ? "ran low" : "refilled";
        }

        /** "Day 42 · 14:32" from a level's total day time. */
        public String when() {
            long day = dayTime / 24000L;
            long inDay = dayTime % 24000L;
            // Minecraft's day starts at 06:00, so t=0 is six in the morning.
            long hour = (inDay / 1000L + 6L) % 24L;
            long minute = (inDay % 1000L) * 60L / 1000L;
            return String.format(Locale.ROOT, "Day %d · %02d:%02d", day, hour, minute);
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    /** Newest first, so the interesting end is the one you read. */
    private final List<Event> events = new ArrayList<>();
    /** Digest of the last board pushed to clients; see {@link #fingerprint()}. */
    private long lastSync = Long.MIN_VALUE;

    public CommandHubBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.COMMAND_HUB_BE.get(), pos, state);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void clearEvents() {
        events.clear();
        setChanged();
        sync();
    }

    /**
     * Record an alarm edge. Only called for nodes that are online, so a chunk
     * unloading never reads as "recovered" and reloading never reads as a fresh
     * failure.
     */
    private void logEdge(Level level, Node n, boolean raised) {
        String name = n.label.isBlank() ? "a " + kindOf(n.type) + " gadget" : n.label;
        events.add(0, new Event(level.getDayTime(), n.type, raised, name));
        while (events.size() > MAX_EVENTS) {
            events.remove(events.size() - 1);
        }
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
        if (!TickPhase.due(level, pos, INTERVAL)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        if (!be.nodes.isEmpty()) {
            // One pass: read each gadget, and drop it if that read proves it's
            // gone. Pruning and refreshing separately meant looking every linked
            // block up twice a second for the same answer.
            Iterator<Node> it = be.nodes.iterator();
            while (it.hasNext()) {
                Node n = it.next();
                if (be.refresh(server, n) == Link.GONE) {
                    // A broken counter or monitor drops off the board rather
                    // than sitting there as a permanent "offline" ghost.
                    it.remove();
                    continue;
                }
                // An offline node keeps its last known alarm state: an unloaded
                // chunk is not evidence that anything changed.
                if (n.online) {
                    boolean now = n.alarmed();
                    if (now != n.wasAlarmed) {
                        be.logEdge(level, n, now);
                        n.wasAlarmed = now;
                    }
                }
            }
        }

        // Reconciled even with an empty board: unlinking the last alarmed gadget
        // has to release the redstone output, not leave it latched on forever.
        boolean alarmed = be.alarmCount() > 0;
        if (state.getValue(CommandHubBlock.ALARM) != alarmed) {
            level.setBlock(pos, state.setValue(CommandHubBlock.ALARM, alarmed), Block.UPDATE_ALL);
            // A cue only on the transition into alarm — polling every interval
            // would mean a constant chime for as long as anything stays flagged.
            if (alarmed) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.5F, 0.8F);
            }
        }

        // Push to clients only when the board actually changed. The newest event
        // is part of that: a log entry with no matching board change still needs
        // to reach the screen.
        long fingerprint = be.fingerprint();
        if (fingerprint != be.lastSync) {
            be.lastSync = fingerprint;
            be.setChanged();
            be.sync();
        }
    }

    /**
     * A cheap digest of everything the board shows.
     *
     * <p>This used to build the whole sync payload and compare its text against
     * last second's — a tag per node plus a string of the lot, allocated once a
     * second for the life of every hub, purely to answer "did anything move?".
     * Reading the same fields into a running hash allocates nothing at all.
     */
    private long fingerprint() {
        long h = 1125899906842597L;
        for (Node n : nodes) {
            h = h * 31 + n.type;
            h = h * 31 + n.pos;
            h = h * 31 + n.dim.hashCode();
            h = h * 31 + (n.online ? 1 : 0);
            h = h * 31 + n.label.hashCode();
            h = h * 31 + n.a;
            h = h * 31 + n.b;
            h = h * 31 + n.c;
            h = h * 31 + n.d;
        }
        h = h * 31 + events.size();
        if (!events.isEmpty()) {
            h = h * 31 + events.get(0).dayTime();
        }
        return h;
    }

    /** What a look at a linked gadget found. */
    private enum Link {
        /** Read successfully; the node now holds fresh numbers. */
        LIVE,
        /** Out of reach — unloaded chunk or missing dimension. Keep the link. */
        OFFLINE,
        /** Reachable, and there is no gadget there any more. Drop the link. */
        GONE
    }

    private Link refresh(MinecraftServer server, Node n) {
        n.online = false;
        ServerLevel w = levelOf(server, n);
        if (w == null) {
            return Link.OFFLINE; // dimension missing — it may come back
        }
        BlockPos p = BlockPos.of(n.pos);
        if (!w.isLoaded(p)) {
            return Link.OFFLINE; // never force a chunk, and never read absence into one
        }
        // One lookup, then decide what it is — the chunk and block-entity map
        // don't need walking three times to answer the same question.
        BlockEntity linked = w.getBlockEntity(p);
        if (linked instanceof ItemCounterBlockEntity counter) {
            n.online = true;
            n.label = counter.displayName();
            n.a = counter.getRateMin();
            n.b = counter.getRateHour();
            n.c = counter.getTotal();
            n.d = counter.isStalled() ? 1 : 0;
        } else if (linked instanceof StockMonitorBlockEntity monitor) {
            n.online = true;
            n.label = monitor.displayName();
            n.a = monitor.getCount();
            n.b = monitor.getThreshold();
            n.c = monitor.isLow() ? 1 : 0;
            n.d = monitor.getDistinctTypes();
        } else if (linked instanceof HubGauge gauge) {
            n.online = true;
            n.type = gauge.gaugeType();
            n.label = gauge.displayName();
            n.a = gauge.gaugeStored();
            n.b = gauge.gaugeCapacity();
            n.c = gauge.isLow() ? 1 : 0;
            n.d = gauge.percent();
        }
        return n.online ? Link.LIVE : Link.GONE;
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
        ListTag log = new ListTag();
        for (Event e : events) {
            log.add(e.toNbt());
        }
        tag.put("Events", log);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nodes.clear();
        ListTag list = tag.getList("Nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_NODES); i++) {
            nodes.add(Node.fromNbt(list.getCompound(i)));
        }
        events.clear();
        ListTag log = tag.getList("Events", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(log.size(), MAX_EVENTS); i++) {
            events.add(Event.fromNbt(log.getCompound(i)));
        }
    }
}
