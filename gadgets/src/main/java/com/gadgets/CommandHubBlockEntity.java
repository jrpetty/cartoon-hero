package com.gadgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Command Hub: a base-wide monitoring console. Link Item Counters and
 * Stock Monitors to it with the Monitor Wand and it aggregates their live
 * numbers every second — throughput per counter, stock per monitor, offline
 * markers for unloaded chunks — across dimensions. The screen shows a
 * summary; the full board opens on right-click. This is v1 of the base
 * command center: read-only telemetry today, control later.
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

        public NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putInt("T", type);
            n.putString("D", dim);
            n.putLong("P", pos);
            n.putBoolean("O", online);
            n.putString("L", label);
            n.putLong("A", a);
            n.putLong("B", b);
            n.putLong("C", c);
            return n;
        }

        public static Node fromNbt(NbtCompound n) {
            Node node = new Node();
            node.type = n.getInt("T");
            node.dim = n.getString("D");
            node.pos = n.getLong("P");
            node.online = n.getBoolean("O");
            node.label = n.getString("L");
            node.a = n.getLong("A");
            node.b = n.getLong("B");
            node.c = n.getLong("C");
            return node;
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private String lastSync = "";

    public CommandHubBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.COMMAND_HUB_BE, pos, state);
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
        markDirty();
        return true;
    }

    /** Drop one linked gadget from the board; false when it wasn't on it. */
    public boolean removeNode(String dim, BlockPos nodePos) {
        long packed = nodePos.asLong();
        boolean removed = nodes.removeIf(n -> n.dim.equals(dim) && n.pos == packed);
        if (removed) {
            markDirty();
            sync();
        }
        return removed;
    }

    public void clearNodes() {
        nodes.clear();
        markDirty();
        sync();
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

    public int lowCount() {
        int low = 0;
        for (Node n : nodes) {
            if (n.type == TYPE_MONITOR && n.online && n.c != 0) {
                low++;
            }
        }
        return low;
    }

    public static void tick(World world, BlockPos pos, BlockState state, CommandHubBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L || be.nodes.isEmpty()) {
            return;
        }
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }
        for (Node n : be.nodes) {
            be.refresh(server, n);
        }
        // Push to clients only when the board actually changed.
        String fingerprint = be.buildList().toString();
        if (!fingerprint.equals(be.lastSync)) {
            be.lastSync = fingerprint;
            be.markDirty();
            be.sync();
        }
    }

    private void refresh(MinecraftServer server, Node n) {
        n.online = false;
        Identifier dimId = Identifier.tryParse(n.dim);
        if (dimId == null) {
            return;
        }
        ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
        if (w == null) {
            return;
        }
        BlockPos p = BlockPos.fromLong(n.pos);
        if (!w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) {
            return; // offline: chunk not loaded — never force it
        }
        if (w.getBlockEntity(p) instanceof ItemCounterBlockEntity counter) {
            n.online = true;
            n.label = "Counter";
            n.a = counter.getRateMin();
            n.b = counter.getRateHour();
            n.c = counter.getTotal();
        } else if (w.getBlockEntity(p) instanceof StockMonitorBlockEntity monitor) {
            n.online = true;
            n.label = ItemCounterBlockEntity.displayName(
                    net.minecraft.registry.Registries.ITEM.getId(monitor.getTracked()).toString());
            n.a = monitor.getCount();
            n.b = monitor.getThreshold();
            n.c = monitor.isLow() ? 1 : 0;
        }
    }

    private void sync() {
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    private NbtList buildList() {
        NbtList list = new NbtList();
        for (Node n : nodes) {
            list.add(n.toNbt());
        }
        return list;
    }

    // --- sync + persistence ---

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.put("Nodes", buildList());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        nodes.clear();
        NbtList list = nbt.getList("Nodes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(list.size(), MAX_NODES); i++) {
            nodes.add(Node.fromNbt(list.getCompound(i)));
        }
    }
}
