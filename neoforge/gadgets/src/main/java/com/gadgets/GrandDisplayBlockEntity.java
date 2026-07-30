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
 * A wall board showing several of a Command Hub's gadgets at once, rather than
 * one screen per gadget.
 *
 * <p>Link it to a hub with the Monitor Wand and it mirrors the top rows of that
 * hub's board, ordered however you choose. "Alerts first" is the default and the
 * reason the thing exists: a single panel that always shows what is currently
 * wrong, without paging.
 *
 * <p>Rows are stored as {@link CommandHubBlockEntity.Node}s so they share the
 * hub's snapshot format and NBT rather than re-deriving it.
 */
public class GrandDisplayBlockEntity extends BlockEntity {
    private static final int INTERVAL = 20;

    /** Rows shown per density: compact fits more, large is legible further off. */
    public static final int ROWS_COMPACT = 6;
    public static final int ROWS_LARGE = 3;
    /** Longest row of panels joined into one board. Lives here rather than on
     *  the renderer so common code never points at a client-only class. */
    public static final int MAX_RUN = 8;

    public static final int ORDER_ALERTS = 0;
    public static final int ORDER_LINK = 1;
    public static final int ORDER_NAME = 2;
    public static final String[] ORDER_LABELS = {"Alerts first", "Link order", "A–Z"};

    private String hubDim = "";
    private long hubPos = 0L;
    private boolean hubLinked = false;

    private int order = ORDER_ALERTS;
    /** True for the roomier three-row layout. */
    private boolean large = false;

    /** How many of the hub's gadgets it is drawing from, for the "+N more" tail. */
    private int totalRows = 0;
    private final List<CommandHubBlockEntity.Node> rows = new ArrayList<>();

    private String lastSync = "";

    public GrandDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.GRAND_DISPLAY_BE.get(), pos, state);
    }


    // --- state the screen and renderer read ---

    public boolean isHubLinked() {
        return hubLinked;
    }

    public List<CommandHubBlockEntity.Node> getRows() {
        return rows;
    }

    public int getOrder() {
        return order;
    }

    public boolean isLarge() {
        return large;
    }

    public int rowCapacity() {
        return large ? ROWS_LARGE : ROWS_COMPACT;
    }

    /** Gadgets on the hub that didn't fit on this screen. */
    public int hiddenRows() {
        return Math.max(0, totalRows - rows.size());
    }

    public BlockPos hubBlockPos() {
        return BlockPos.of(hubPos);
    }

    // --- configuration ---

    public void linkHub(String dim, BlockPos pos) {
        hubDim = dim;
        hubPos = pos.asLong();
        hubLinked = true;
        rows.clear();
        totalRows = 0;
        setChanged();
        sync();
    }

    public void setOrder(int value) {
        if (value >= 0 && value < ORDER_LABELS.length) {
            order = value;
            setChanged();
            sync();
        }
    }

    public void setLarge(boolean value) {
        large = value;
        setChanged();
        sync();
    }

    private void unlinkHub() {
        hubDim = "";
        hubPos = 0L;
        hubLinked = false;
        rows.clear();
        totalRows = 0;
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

    public static void tick(Level level, BlockPos pos, BlockState state, GrandDisplayBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L || !be.hubLinked) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        // A destroyed hub takes its boards down with it. As everywhere else in
        // this system, only a loaded chunk may prove the block is gone.
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
        be.rows.clear();
        be.totalRows = 0;
        if (hub != null) {
            List<CommandHubBlockEntity.Node> all = new ArrayList<>(hub.getNodes());
            be.totalRows = all.size();
            switch (be.order) {
                case ORDER_ALERTS -> all.sort((a, c) -> Boolean.compare(!a.alarmed(), !c.alarmed()));
                case ORDER_NAME -> all.sort((a, c) -> a.label.compareToIgnoreCase(c.label));
                default -> {
                    // link order — already in that order
                }
            }
            for (int i = 0; i < Math.min(all.size(), be.rowCapacity()); i++) {
                be.rows.add(all.get(i));
            }
        }

        String fingerprint = be.fingerprint();
        if (!fingerprint.equals(be.lastSync)) {
            be.lastSync = fingerprint;
            be.setChanged();
            be.sync();
        }
    }

    private String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(hubLinked).append(order).append(large).append(totalRows);
        for (CommandHubBlockEntity.Node n : rows) {
            sb.append('|').append(n.label).append(n.type).append(n.online)
                    .append(n.a).append(',').append(n.b).append(',').append(n.c).append(',').append(n.d);
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
        tag.putInt("Order", order);
        tag.putBoolean("Large", large);
        tag.putInt("TotalRows", totalRows);
        ListTag list = new ListTag();
        for (CommandHubBlockEntity.Node n : rows) {
            list.add(n.toNbt());
        }
        tag.put("Rows", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hubLinked = tag.getBoolean("HubLinked");
        hubDim = tag.getString("HubDim");
        hubPos = tag.getLong("HubPos");
        order = Math.floorMod(tag.getInt("Order"), ORDER_LABELS.length);
        large = tag.getBoolean("Large");
        totalRows = tag.getInt("TotalRows");
        rows.clear();
        ListTag list = tag.getList("Rows", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            rows.add(CommandHubBlockEntity.Node.fromNbt(list.getCompound(i)));
        }
    }
}
