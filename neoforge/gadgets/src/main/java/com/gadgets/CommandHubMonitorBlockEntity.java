package com.gadgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A remote readout for one line of a Command Hub's board.
 *
 * <p>Link it to a hub with the Monitor Wand, then right-click to pick which of
 * the hub's counters or monitors it should show. It then displays that gadget's
 * numbers exactly as the gadget's own face does, with its name above them, so a
 * wall of these reads like a control room.
 *
 * <p>The chosen source is stored as its own dimension and position rather than a
 * row number, so unlinking or pruning something else on the board can never
 * silently repoint this screen at a different machine.
 */
public class CommandHubMonitorBlockEntity extends BlockEntity {
    private static final int INTERVAL = 20;

    /** One pickable line, mirrored to the client so the chooser can list names. */
    public record Choice(String dim, long pos, int type, String label, boolean alarmed) {
        CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putString("D", dim);
            t.putLong("P", pos);
            t.putInt("T", type);
            t.putString("L", label);
            t.putBoolean("Al", alarmed);
            return t;
        }

        static Choice fromNbt(CompoundTag t) {
            return new Choice(t.getString("D"), t.getLong("P"), t.getInt("T"), t.getString("L"), t.getBoolean("Al"));
        }
    }

    // The hub this screen belongs to.
    private String hubDim = "";
    private long hubPos = 0L;
    private boolean hubLinked = false;

    // The gadget being displayed.
    private String srcDim = "";
    private long srcPos = 0L;
    private boolean srcChosen = false;

    // Latest snapshot of that gadget, and the hub's pickable lines.
    private int type = CommandHubBlockEntity.TYPE_COUNTER;
    private boolean online = false;
    private String label = "";
    private long a, b, c, d;
    private final List<Choice> choices = new ArrayList<>();

    private String lastSync = "";

    public CommandHubMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.COMMAND_HUB_MONITOR_BE.get(), pos, state);
    }

    // --- state the screen and renderer read ---

    public boolean isHubLinked() {
        return hubLinked;
    }

    public boolean hasSource() {
        return srcChosen;
    }

    public boolean isOnline() {
        return online;
    }

    public int sourceType() {
        return type;
    }

    public String sourceLabel() {
        return label;
    }

    public long a() {
        return a;
    }

    public long b() {
        return b;
    }

    public long c() {
        return c;
    }

    public long d() {
        return d;
    }

    public List<Choice> choices() {
        return choices;
    }

    public BlockPos hubBlockPos() {
        return BlockPos.of(hubPos);
    }

    /** True when {@code choice} is the line currently on screen. */
    public boolean isShowing(Choice choice) {
        return srcChosen && choice.pos() == srcPos && choice.dim().equals(srcDim);
    }

    // --- linking ---

    /** Point this screen at a hub, clearing whatever it was showing. */
    public void linkHub(String dim, BlockPos pos) {
        hubDim = dim;
        hubPos = pos.asLong();
        hubLinked = true;
        clearSource();
        setChanged();
        sync();
    }

    /** Choose a line, but only one the linked hub actually carries. */
    public boolean chooseSource(MinecraftServer server, String dim, long pos) {
        CommandHubBlockEntity hub = resolveHub(server);
        if (hub == null) {
            return false;
        }
        boolean onBoard = hub.getNodes().stream().anyMatch(n -> n.pos == pos && n.dim.equals(dim));
        if (!onBoard) {
            return false;
        }
        srcDim = dim;
        srcPos = pos;
        srcChosen = true;
        setChanged();
        sync();
        return true;
    }

    /** Blank the screen but keep it linked — the chooser's Clear button. */
    public void clearChosenSource() {
        clearSource();
        setChanged();
        sync();
    }

    private void clearSource() {
        srcDim = "";
        srcPos = 0L;
        srcChosen = false;
        online = false;
        label = "";
        a = b = c = d = 0;
    }

    private void unlinkHub() {
        hubDim = "";
        hubPos = 0L;
        hubLinked = false;
        clearSource();
        choices.clear();
    }

    @Nullable
    private CommandHubBlockEntity resolveHub(MinecraftServer server) {
        if (!hubLinked) {
            return null;
        }
        ServerLevel w = levelOf(server, hubDim);
        BlockPos p = BlockPos.of(hubPos);
        if (w == null || !w.isLoaded(p)) {
            return null;
        }
        return w.getBlockEntity(p) instanceof CommandHubBlockEntity hub ? hub : null;
    }

    @Nullable
    private static ServerLevel levelOf(MinecraftServer server, String dim) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        return id == null ? null
                : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    // --- tick ---

    public static void tick(Level level, BlockPos pos, BlockState state, CommandHubMonitorBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L || !be.hubLinked) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        // A destroyed hub takes its screens down with it. As on the hub itself,
        // only a loaded chunk is allowed to prove the block is gone.
        ServerLevel hubLevel = levelOf(server, be.hubDim);
        BlockPos hp = BlockPos.of(be.hubPos);
        if (hubLevel != null && hubLevel.isLoaded(hp)
                && !(hubLevel.getBlockEntity(hp) instanceof CommandHubBlockEntity)) {
            be.unlinkHub();
            be.setChanged();
            be.sync();
            return;
        }

        CommandHubBlockEntity hub = be.resolveHub(server);
        be.choices.clear();
        if (hub != null) {
            for (CommandHubBlockEntity.Node n : hub.getNodes()) {
                be.choices.add(new Choice(n.dim, n.pos, n.type, n.label, n.alarmed()));
            }
            // The board drops links whose gadget is gone; follow it, so a broken
            // counter clears this screen instead of freezing its last numbers.
            if (be.srcChosen && be.choices.stream().noneMatch(c -> c.pos() == be.srcPos && c.dim().equals(be.srcDim))) {
                be.clearSource();
            }
        }
        be.readSource(server);

        String fingerprint = be.fingerprint();
        if (!fingerprint.equals(be.lastSync)) {
            be.lastSync = fingerprint;
            be.setChanged();
            be.sync();
        }
    }


    /** Copy the chosen gadget's live numbers, or mark it offline. */
    private void readSource(MinecraftServer server) {
        if (!srcChosen) {
            return;
        }
        online = false;
        ServerLevel w = levelOf(server, srcDim);
        BlockPos p = BlockPos.of(srcPos);
        if (w == null || !w.isLoaded(p)) {
            return;
        }
        if (w.getBlockEntity(p) instanceof ItemCounterBlockEntity counter) {
            type = CommandHubBlockEntity.TYPE_COUNTER;
            online = true;
            label = counter.displayName();
            a = counter.getRateMin();
            b = counter.getRateHour();
            c = counter.getTotal();
            d = counter.isStalled() ? 1 : 0;
        } else if (w.getBlockEntity(p) instanceof StockMonitorBlockEntity monitor) {
            type = CommandHubBlockEntity.TYPE_MONITOR;
            online = true;
            label = monitor.displayName();
            a = monitor.getCount();
            b = monitor.getThreshold();
            c = monitor.isLow() ? 1 : 0;
            d = monitor.getDistinctTypes();
        }
    }

    private String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(hubLinked).append(srcChosen).append(srcDim).append(srcPos)
                .append(type).append(online).append(label)
                .append(a).append(',').append(b).append(',').append(c).append(',').append(d);
        for (Choice ch : choices) {
            sb.append('|').append(ch.dim()).append(ch.pos()).append(ch.type()).append(ch.label()).append(ch.alarmed());
        }
        return sb.toString();
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
        tag.putBoolean("HubLinked", hubLinked);
        tag.putString("HubDim", hubDim);
        tag.putLong("HubPos", hubPos);
        tag.putBoolean("SrcChosen", srcChosen);
        tag.putString("SrcDim", srcDim);
        tag.putLong("SrcPos", srcPos);
        tag.putInt("Type", type);
        tag.putBoolean("Online", online);
        tag.putString("Label", label);
        tag.putLong("A", a);
        tag.putLong("B", b);
        tag.putLong("C", c);
        tag.putLong("D", d);
        ListTag list = new ListTag();
        for (Choice ch : choices) {
            list.add(ch.toNbt());
        }
        tag.put("Choices", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hubLinked = tag.getBoolean("HubLinked");
        hubDim = tag.getString("HubDim");
        hubPos = tag.getLong("HubPos");
        srcChosen = tag.getBoolean("SrcChosen");
        srcDim = tag.getString("SrcDim");
        srcPos = tag.getLong("SrcPos");
        type = tag.getInt("Type");
        online = tag.getBoolean("Online");
        label = tag.getString("Label");
        a = tag.getLong("A");
        b = tag.getLong("B");
        c = tag.getLong("C");
        d = tag.getLong("D");
        choices.clear();
        ListTag list = tag.getList("Choices", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            choices.add(Choice.fromNbt(list.getCompound(i)));
        }
    }
}
