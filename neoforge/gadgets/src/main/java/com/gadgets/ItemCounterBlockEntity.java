package com.gadgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Counts items passing the face it points at and pulses redstone every N items.
 *
 * <p>Beyond the pulse counter it keeps live statistics: rolling items-per-minute
 * and items-per-hour rates, a lifetime total with uptime, and a per-item-type
 * breakdown. Container mode diffs the watched item handler per item type, so it
 * knows exactly which items left; flow mode reads the passing item entities.
 * The chosen readout (/min, /hour, total, pulse) is synced to clients and drawn
 * on the display face by {@link ItemCounterRenderer}.
 */
public class ItemCounterBlockEntity extends BlockEntity {
    private static final int INTERVAL = 2;
    /** How often the face readout is recomputed and, if it changed, synced. */
    private static final int FACE_INTERVAL = 20;
    private static final int PULSE_TICKS = 4;
    public static final int[] THRESHOLDS = {1, 4, 8, 16, 32, 64};
    private static final int MAX_TRACKED_ITEMS = 64;
    private static final String OTHER_KEY = "other";
    /** No items for this long after having seen at least one: flagged as stalled. */
    private static final long STALL_TICKS = 3600L; // 3 minutes

    /** Face readout modes, cycled with sneak + empty hand. */
    public static final String[] MODE_LABELS = {"/min", "/hour", "total", "pulse"};

    /** Which way through the watched container items are counted. */
    public static final int COUNT_IN = 0;
    public static final int COUNT_OUT = 1;
    public static final int COUNT_BOTH = 2;
    public static final String[] COUNT_LABELS = {"arriving", "leaving", "both ways"};

    /** Beyond a handful, an unfiltered counter plus the breakdown is clearer. */
    public static final int MAX_FILTER = 6;

    private int threshold = 8;
    private int count = 0;
    private long poweredUntil = 0L;
    private int displayMode = 0;
    /**
     * Default: items arriving. A counter is usually pointed at the chest a farm
     * fills, and counting only departures made that read zero forever.
     */
    private int countMode = COUNT_IN;
    /** Only these items are counted; empty counts everything. */
    private final List<Item> filter = new ArrayList<>();
    /** Player-set label shown on the face and on a Command Hub board. */
    private String customName = "";

    // --- lifetime stats (persisted) ---
    private long total = 0L;
    private long uptimeTicks = 0L;
    private final Map<String, Long> perItem = new HashMap<>();
    /** Ticks since the last item was recorded; large until the first one ever is. */
    private long ticksSinceItem = Long.MAX_VALUE / 2;

    // --- rolling rate clocks (transient; rebuilt after reload) ---
    private final int[] secBuckets = new int[60];
    private final int[] minBuckets = new int[60];
    private long lastSec = -1L;
    private long lastMin = -1L;

    // --- transient sampling state ---
    /** Per-item contents of the watched container on the previous sample; null = reseed. */
    private Map<String, Integer> lastContents = null;
    /** Ids of item entities already counted while they sit in the detection cell. */
    private final Set<Integer> seenEntities = new HashSet<>();
    /** Which mode the last sample ran in — purely informational, not persisted. */
    private boolean watchingContainer = false;

    // --- face readout, synced to clients once a second when it changes ---
    private int rateMin = 0;
    private int rateHour = 0;
    private String lastFace = "";

    /** The container this counter faces, if it is one. */
    private final CapCache<IItemHandler> source = new CapCache<>(Capabilities.ItemHandler.BLOCK, this);

    public ItemCounterBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_COUNTER_BE.get(), pos, state);
    }

    public int getThreshold() {
        return threshold;
    }

    public int getCount() {
        return count;
    }

    public long getTotal() {
        return total;
    }

    public long getUptimeTicks() {
        return uptimeTicks;
    }

    public int getRateMin() {
        return rateMin;
    }

    public int getRateHour() {
        return rateHour;
    }

    public boolean isWatchingContainer() {
        return watchingContainer;
    }

    public int getCountMode() {
        return countMode;
    }

    public String getCountLabel() {
        return COUNT_LABELS[countMode];
    }

    public void setCountMode(int mode) {
        if (mode >= 0 && mode < COUNT_LABELS.length) {
            countMode = mode;
            lastContents = null; // reseed: old contents belong to the old rule
            sync();
        }
    }

    public List<Item> getFilter() {
        return filter;
    }

    /** Add or remove an item from the count filter; returns click feedback. */
    public String toggleFilter(Item item) {
        if (filter.remove(item)) {
            sync();
            return filter.isEmpty()
                    ? "counting everything again"
                    : "stopped counting " + item.getDescription().getString();
        }
        if (filter.size() >= MAX_FILTER) {
            return "already filtering " + MAX_FILTER + " items";
        }
        filter.add(item);
        sync();
        return "counting only " + describeFilter();
    }

    public void removeFilter(Item item) {
        if (filter.remove(item)) {
            sync();
        }
    }

    public void clearFilter() {
        filter.clear();
        sync();
    }

    /** "Iron Ingot" / "Iron Ingot +2" / "everything". */
    public String describeFilter() {
        if (filter.isEmpty()) {
            return "everything";
        }
        String first = filter.get(0).getDescription().getString();
        return filter.size() == 1 ? first : first + " +" + (filter.size() - 1);
    }

    /** True once a counter that has actually seen items goes quiet for a while —
     *  the signal a hub board uses to flag "this farm stopped producing". */
    public boolean isStalled() {
        return total > 0L && ticksSinceItem > STALL_TICKS;
    }

    /** The top {@code n} item types counted so far, best first. */
    public List<Map.Entry<String, Long>> topItems(int n) {
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(perItem.entrySet());
        sorted.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        return sorted.subList(0, Math.min(n, sorted.size()));
    }

    /** Advance to the next pulse size and restart the running pulse count. */
    public int cycleThreshold() {
        int idx = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (THRESHOLDS[i] == threshold) {
                idx = i;
                break;
            }
        }
        threshold = THRESHOLDS[(idx + 1) % THRESHOLDS.length];
        count = 0;
        sync();
        return threshold;
    }

    /** Advance the face readout to the next mode and return its label. */
    public String cycleDisplayMode() {
        displayMode = (displayMode + 1) % MODE_LABELS.length;
        sync();
        return faceLabel();
    }


    public String getCustomName() {
        return customName;
    }

    /** A human label for this counter, or a sensible default when unnamed. */
    public String displayName() {
        return customName.isEmpty() ? "Counter" : customName;
    }

    public void setCustomName(String name) {
        this.customName = name == null ? "" : name;
        sync();
    }

    public int getDisplayMode() {
        return displayMode;
    }

    /** Set the face readout mode directly (screen buttons). */
    public void setDisplayMode(int mode) {
        if (mode >= 0 && mode < MODE_LABELS.length) {
            displayMode = mode;
            sync();
        }
    }

    /** Set the pulse size directly — only preset values are accepted. */
    public void setThreshold(int value) {
        for (int preset : THRESHOLDS) {
            if (preset == value) {
                threshold = value;
                count = 0;
                sync();
                return;
            }
        }
    }

    /** Wipe every statistic and start the counter fresh (screen button). */
    public void resetStats() {
        total = 0L;
        uptimeTicks = 0L;
        count = 0;
        perItem.clear();
        java.util.Arrays.fill(secBuckets, 0);
        java.util.Arrays.fill(minBuckets, 0);
        rateMin = 0;
        rateHour = 0;
        ticksSinceItem = Long.MAX_VALUE / 2;
        sync();
    }

    /** The value string shown on the display face. */
    public String faceValue() {
        return switch (displayMode) {
            case 1 -> compact(rateHour);
            case 2 -> compact(total);
            case 3 -> count + "/" + threshold;
            default -> compact(rateMin);
        };
    }

    /** The unit label shown under the face value. */
    public String faceLabel() {
        return customName.isEmpty() ? MODE_LABELS[displayMode] : customName;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemCounterBlockEntity be) {
        be.uptimeTicks++;
        if (be.ticksSinceItem < Long.MAX_VALUE / 2) {
            be.ticksSinceItem++;
        }
        be.advanceClocks(level.getGameTime());

        if (TickPhase.due(level, pos, INTERVAL)) {
            Direction facing = state.getValue(ItemCounterBlock.FACING);
            BlockPos target = pos.relative(facing);

            int passed;
            IItemHandler handler = be.source.get(level, target, facing.getOpposite());
            be.watchingContainer = handler != null;
            if (handler != null) {
                passed = be.sampleContainer(handler);
                be.seenEntities.clear(); // not in flow mode; forget any stragglers
            } else {
                passed = be.sampleFlow(level, target);
                be.lastContents = null; // container gone; reseed if it returns
            }

            if (passed > 0) {
                be.count += passed;
                be.setChanged();
            }
            if (be.count >= be.threshold) {
                be.count %= be.threshold; // a bulk drop fires one pulse, never a runaway
                be.poweredUntil = level.getGameTime() + PULSE_TICKS;
                be.setChanged();
            }
        }

        boolean powered = level.getGameTime() < be.poweredUntil;
        if (state.getValue(ItemCounterBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(ItemCounterBlock.POWERED, powered), Block.UPDATE_ALL);
        }

        // Refresh the face readout once a second; push to clients only on change.
        if (TickPhase.due(level, pos, FACE_INTERVAL)) {
            be.rateMin = sum(be.secBuckets);
            be.rateHour = sum(be.minBuckets);
            String face = be.faceValue() + "|" + be.faceLabel();
            if (!face.equals(be.lastFace)) {
                be.lastFace = face;
                be.sync();
            }
        }
    }

    /** Zero out rate buckets the clock has moved past since the last tick. */
    private void advanceClocks(long time) {
        long sec = time / 20L;
        if (lastSec < 0) {
            lastSec = sec;
        }
        for (long s = lastSec + 1; s <= Math.min(sec, lastSec + 60); s++) {
            secBuckets[(int) (s % 60L)] = 0;
        }
        lastSec = sec;

        long min = time / 1200L;
        if (lastMin < 0) {
            lastMin = min;
        }
        for (long m = lastMin + 1; m <= Math.min(min, lastMin + 60); m++) {
            minBuckets[(int) (m % 60L)] = 0;
        }
        lastMin = min;
    }

    /** Credit {@code n} passed items of the given type to every statistic. */
    private void record(String id, int n) {
        total += n;
        ticksSinceItem = 0;
        secBuckets[(int) (lastSec % 60L)] += n;
        minBuckets[(int) (lastMin % 60L)] += n;
        String key = (!perItem.containsKey(id) && perItem.size() >= MAX_TRACKED_ITEMS) ? OTHER_KEY : id;
        perItem.merge(key, (long) n, Long::sum);
    }

    /**
     * Diff the handler per item type and credit whichever direction of movement
     * this counter is set to watch. Types present in either sample are visited,
     * so a stack arriving into an empty container counts as well as one leaving.
     */
    private int sampleContainer(IItemHandler handler) {
        Map<String, Integer> now = new HashMap<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                now.merge(idOf(stack), stack.getCount(), Integer::sum);
            }
        }
        int passed = 0;
        if (lastContents != null) {
            Set<String> types = new HashSet<>(lastContents.keySet());
            types.addAll(now.keySet());
            for (String id : types) {
                if (!counts(id)) {
                    continue;
                }
                int delta = now.getOrDefault(id, 0) - lastContents.getOrDefault(id, 0);
                int moved = switch (countMode) {
                    case COUNT_OUT -> delta < 0 ? -delta : 0;
                    case COUNT_BOTH -> Math.abs(delta);
                    default -> delta > 0 ? delta : 0;
                };
                if (moved > 0) {
                    record(id, moved);
                    passed += moved;
                }
            }
        }
        lastContents = now;
        return passed;
    }

    /** Is this item one the counter has been told to watch? */
    private boolean counts(String id) {
        if (filter.isEmpty()) {
            return true;
        }
        for (Item item : filter) {
            if (BuiltInRegistries.ITEM.getKey(item).toString().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Count dropped item entities entering the detection cell — each stack once. */
    private int sampleFlow(Level level, BlockPos target) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(target));
        int passed = 0;
        Set<Integer> present = new HashSet<>();
        for (ItemEntity item : items) {
            int id = item.getId();
            present.add(id);
            if (seenEntities.add(id)) {
                String itemId = idOf(item.getItem());
                if (!counts(itemId)) {
                    continue;
                }
                int n = item.getItem().getCount();
                record(itemId, n);
                passed += n;
            }
        }
        seenEntities.retainAll(present); // forget items that have moved on
        return passed;
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static int sum(int[] buckets) {
        int total = 0;
        for (int b : buckets) {
            total += b;
        }
        return total;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // --- formatting helpers shared with the block's dashboard ---

    /** "12,345" — full number with thousands separators. */
    public static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** "9,876" / "54.3k" / "1.2M" — short enough for the display face. */
    public static String compact(long n) {
        if (n < 10_000L) {
            return fmt(n);
        }
        if (n < 1_000_000L) {
            double k = n / 1000.0;
            return k < 100 ? String.format(Locale.ROOT, "%.1fk", k) : (n / 1000L) + "k";
        }
        double m = n / 1_000_000.0;
        return m < 100 ? String.format(Locale.ROOT, "%.1fM", m) : (n / 1_000_000L) + "M";
    }

    /** "3d 4h" / "2h 14m" / "5m 12s" — duration of {@code ticks}. */
    public static String duration(long ticks) {
        long s = ticks / 20L;
        if (s < 3600) {
            return (s / 60) + "m " + (s % 60) + "s";
        }
        if (s < 86400) {
            return (s / 3600) + "h " + (s % 3600 / 60) + "m";
        }
        return (s / 86400) + "d " + (s % 86400 / 3600) + "h";
    }

    /** Human name for a counted item id (or the overflow bucket). */
    public static String displayName(String id) {
        if (OTHER_KEY.equals(id)) {
            return "other items";
        }
        ResourceLocation ident = ResourceLocation.tryParse(id);
        if (ident != null && BuiltInRegistries.ITEM.containsKey(ident)) {
            return BuiltInRegistries.ITEM.get(ident).getDescription().getString();
        }
        return id;
    }

    // --- sync + persistence ---

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Full tag: the stats screen shows uptime and the per-item breakdown too.
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Threshold", threshold);
        tag.putInt("Count", count);
        tag.putLong("PoweredUntil", poweredUntil);
        tag.putInt("DisplayMode", displayMode);
        tag.putString("CustomName", customName);
        tag.putLong("Total", total);
        tag.putLong("Uptime", uptimeTicks);
        tag.putLong("TicksSinceItem", ticksSinceItem);
        tag.putInt("CountMode", countMode);
        ListTag filterTag = new ListTag();
        for (Item item : filter) {
            filterTag.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
        }
        tag.put("Filter", filterTag);
        tag.putInt("RateMin", rateMin);
        tag.putInt("RateHour", rateHour);
        CompoundTag items = new CompoundTag();
        perItem.forEach(items::putLong);
        tag.put("PerItem", items);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Threshold")) {
            threshold = tag.getInt("Threshold");
        }
        count = tag.getInt("Count");
        poweredUntil = tag.getLong("PoweredUntil");
        displayMode = tag.getInt("DisplayMode") % MODE_LABELS.length;
        // Saved but never read until now: the client threw the name away on
        // every sync, so it showed on a hub (read server-side) but never on the
        // counter's own screen or face, and it was lost on reload.
        customName = tag.getString("CustomName");
        total = tag.getLong("Total");
        uptimeTicks = tag.getLong("Uptime");
        ticksSinceItem = tag.contains("TicksSinceItem") ? tag.getLong("TicksSinceItem") : Long.MAX_VALUE / 2;
        countMode = Math.floorMod(tag.getInt("CountMode"), COUNT_LABELS.length);
        filter.clear();
        ListTag filterTag = tag.getList("Filter", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(filterTag.size(), MAX_FILTER); i++) {
            ResourceLocation key = ResourceLocation.tryParse(filterTag.getString(i));
            if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                filter.add(BuiltInRegistries.ITEM.get(key));
            }
        }
        rateMin = tag.getInt("RateMin");
        rateHour = tag.getInt("RateHour");
        if (tag.contains("PerItem")) {
            perItem.clear();
            CompoundTag items = tag.getCompound("PerItem");
            for (String key : items.getAllKeys()) {
                perItem.put(key, items.getLong(key));
            }
        }
    }
}
