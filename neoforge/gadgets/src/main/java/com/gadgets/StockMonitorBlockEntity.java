package com.gadgets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Watches the container it faces and shows the live stock of whatever it has
 * been told to track. When that total falls below the alert level it emits
 * redstone — wire it to an Item Sender or dropper for hands-off restocking.
 * Reads through the item-handler capability, so Create depots/vaults work too.
 *
 * <p>It tracks either an explicit list of items or a single item tag. Tag mode
 * is what makes "do I have enough logs of any kind" answerable without listing
 * every wood: right-click adds and removes individual items, sneak-click walks
 * through the tags the held item belongs to.
 */
public class StockMonitorBlockEntity extends BlockEntity {
    private static final int INTERVAL = 10;
    public static final int[] THRESHOLDS = {1, 8, 16, 32, 64, 128, 256};
    /** Enough for "any of these five ores"; beyond this a tag is the better tool. */
    public static final int MAX_TRACKED = 6;

    /** Explicit items to total up. Empty while a tag is set. */
    private final List<Item> tracked = new ArrayList<>();
    /** Item tag to total up, e.g. {@code minecraft:logs}. Empty in list mode. */
    private String tagId = "";

    private int threshold = 16;
    private long count = 0;
    private boolean low = false;
    /** Whether a container was actually found in front on the last sample. */
    private boolean containerPresent = false;
    /** How many different item types the watched container holds. */
    private int distinctTypes = 0;
    /** Player-set label shown on the face and on a Command Hub board. */
    private String customName = "";

    /** Parsed form of {@link #tagId}, rebuilt whenever the id changes. */
    private TagKey<Item> tagKey = null;

    /** The container this monitor faces. */
    private final CapCache<IItemHandler> source = new CapCache<>(Capabilities.ItemHandler.BLOCK, this);

    public StockMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.STOCK_MONITOR_BE.get(), pos, state);
    }

    public List<Item> getTracked() {
        return tracked;
    }

    public String getTagId() {
        return tagId;
    }

    public boolean isTagMode() {
        return !tagId.isEmpty();
    }

    /** True when nothing has been chosen to watch yet. */
    public boolean isUnset() {
        return tagId.isEmpty() && tracked.isEmpty();
    }

    public int getThreshold() {
        return threshold;
    }

    public long getCount() {
        return count;
    }

    public boolean isLow() {
        return low;
    }

    public int getDistinctTypes() {
        return distinctTypes;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        this.customName = name == null ? "" : name;
        sync();
    }

    public boolean hasContainer() {
        return containerPresent;
    }

    // --- what to track ---

    /**
     * Add {@code item} to the tracked list, or drop it if already there.
     * Leaves tag mode, since the two are alternatives.
     *
     * @return a short description of what happened, for the click feedback
     */
    public String toggleTracked(Item item) {
        tagId = "";
        tagKey = null;
        if (tracked.remove(item)) {
            sync();
            return "stopped tracking " + item.getDescription().getString();
        }
        if (tracked.size() >= MAX_TRACKED) {
            return "already tracking " + MAX_TRACKED + " items — sneak-click to use a tag instead";
        }
        tracked.add(item);
        sync();
        return "tracking " + item.getDescription().getString() + " (" + tracked.size() + " item"
                + (tracked.size() == 1 ? "" : "s") + ")";
    }

    /**
     * Walk to the next tag {@code stack} belongs to, replacing the tracked list.
     * Cycling past the last tag returns to an empty list, so repeated
     * sneak-clicks tour the options and come back out again.
     */
    public String cycleTag(ItemStack stack) {
        List<String> tags = tagsOf(stack);
        if (tags.isEmpty()) {
            return stack.getHoverName().getString() + " is in no item tags";
        }
        int next = tagId.isEmpty() ? 0 : tags.indexOf(tagId) + 1;
        tracked.clear();
        if (next < 0 || next >= tags.size()) {
            tagId = "";
            tagKey = null;
            sync();
            return "tracking nothing — right-click to pick items";
        }
        tagId = tags.get(next);
        tagKey = tagKeyOf(tagId);
        sync();
        return "tracking #" + shortTag(tagId) + " (" + (next + 1) + "/" + tags.size() + ")";
    }

    /** Every item tag the stack is in, ordered so cycling is deterministic. */
    private static List<String> tagsOf(ItemStack stack) {
        return stack.getTags()
                .map(t -> t.location().toString())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public void clearTracked() {
        tracked.clear();
        tagId = "";
        tagKey = null;
        sync();
    }

    public void untrack(Item item) {
        if (tracked.remove(item)) {
            sync();
        }
    }

    /** Does this stack count toward the total? */
    private boolean matches(ItemStack stack) {
        if (tagKey != null) {
            return stack.is(tagKey);
        }
        return tracked.contains(stack.getItem());
    }

    /** Set the alert level directly — only preset values are accepted. */
    public void setThreshold(int value) {
        for (int preset : THRESHOLDS) {
            if (preset == value) {
                threshold = value;
                sync();
                return;
            }
        }
    }

    /** Advance the low-stock alert level and return it. */
    public int cycleThreshold() {
        int idx = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (THRESHOLDS[i] == threshold) {
                idx = i;
                break;
            }
        }
        threshold = THRESHOLDS[(idx + 1) % THRESHOLDS.length];
        sync();
        return threshold;
    }

    // --- display ---

    /** Big line on the screen: the current stock, or a dash when nothing is tracked. */
    public String faceValue() {
        if (isUnset()) {
            return "—";
        }
        return containerPresent ? ItemCounterBlockEntity.compact(count) : "?";
    }

    /** Small line on the screen: the player's label, else what is being tracked. */
    public String faceLabel() {
        String name = customName.isEmpty() ? trackedSummary() : customName;
        return name.length() > 14 ? name.substring(0, 13) + "…" : name;
    }

    /** A human label for this monitor, used on a Command Hub board. */
    public String displayName() {
        return customName.isEmpty() ? trackedSummary() : customName;
    }

    /** "#logs" / "Iron Ingot" / "Iron Ingot +2" / "set item". */
    public String trackedSummary() {
        if (isTagMode()) {
            return "#" + shortTag(tagId);
        }
        if (tracked.isEmpty()) {
            return "set item";
        }
        String first = tracked.get(0).getDescription().getString();
        return tracked.size() == 1 ? first : first + " +" + (tracked.size() - 1);
    }

    /** Drops the common {@code minecraft:} prefix so tags read as "#logs". */
    public static String shortTag(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StockMonitorBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL)) {
            return;
        }
        long found = 0;
        int types = 0;
        Direction facing = state.getValue(StockMonitorBlock.FACING);
        IItemHandler handler = be.source.get(level, pos.relative(facing), facing.getOpposite());
        boolean present = handler != null;
        if (present) {
            Set<Item> seen = new HashSet<>();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                seen.add(stack.getItem());
                if (be.matches(stack)) {
                    found += stack.getCount();
                }
            }
            types = seen.size();
        }
        // Never alarm on a missing container — a broken chest is not "out of stock".
        boolean lowNow = present && !be.isUnset() && found < be.threshold;

        boolean changed = found != be.count || lowNow != be.low || present != be.containerPresent
                || types != be.distinctTypes;
        be.count = found;
        be.low = lowNow;
        be.containerPresent = present;
        be.distinctTypes = types;
        if (state.getValue(StockMonitorBlock.LOW) != lowNow) {
            level.setBlock(pos, state.setValue(StockMonitorBlock.LOW, lowNow), Block.UPDATE_ALL);
        }
        if (changed) {
            be.sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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
        ListTag list = new ListTag();
        for (Item item : tracked) {
            list.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
        }
        tag.put("TrackedList", list);
        tag.putString("TagId", tagId);
        tag.putInt("Threshold", threshold);
        tag.putLong("Count", count);
        tag.putBoolean("Low", low);
        tag.putBoolean("HasContainer", containerPresent);
        tag.putInt("DistinctTypes", distinctTypes);
        tag.putString("CustomName", customName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tracked.clear();
        if (tag.contains("TrackedList")) {
            ListTag list = tag.getList("TrackedList", Tag.TAG_STRING);
            for (int i = 0; i < Math.min(list.size(), MAX_TRACKED); i++) {
                Item item = itemOf(list.getString(i));
                if (item != Items.AIR) {
                    tracked.add(item);
                }
            }
        } else if (tag.contains("Tracked")) {
            // Monitors saved before multi-item tracking stored one item id.
            Item item = itemOf(tag.getString("Tracked"));
            if (item != Items.AIR) {
                tracked.add(item);
            }
        }
        tagId = tag.getString("TagId");
        tagKey = tagKeyOf(tagId);
        if (tagKey == null) {
            tagId = ""; // unparseable id on disk — fall back to list mode
        }
        if (tag.contains("Threshold")) {
            threshold = tag.getInt("Threshold");
        }
        count = tag.getLong("Count");
        low = tag.getBoolean("Low");
        containerPresent = tag.getBoolean("HasContainer");
        distinctTypes = tag.getInt("DistinctTypes");
        customName = tag.getString("CustomName");
    }

    private static Item itemOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
    }

    /** Null for an empty or malformed id, so a bad save can never throw on load. */
    @Nullable
    private static TagKey<Item> tagKeyOf(String id) {
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : TagKey.create(Registries.ITEM, key);
    }
}
