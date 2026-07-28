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
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
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
    private static final int PULSE_TICKS = 4;
    public static final int[] THRESHOLDS = {1, 4, 8, 16, 32, 64};
    private static final int MAX_TRACKED_ITEMS = 64;
    private static final String OTHER_KEY = "other";

    /** Face readout modes, cycled with sneak + empty hand. */
    public static final String[] MODE_LABELS = {"/min", "/hour", "total", "pulse"};

    private int threshold = 8;
    private int count = 0;
    private long poweredUntil = 0L;
    private int displayMode = 0;
    /** Player-set label shown on the face and on a Command Hub board. */
    private String customName = "";

    // --- lifetime stats (persisted) ---
    private long total = 0L;
    private long uptimeTicks = 0L;
    private final Map<String, Long> perItem = new HashMap<>();

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
        be.advanceClocks(level.getGameTime());

        if (level.getGameTime() % INTERVAL == 0L) {
            Direction facing = state.getValue(ItemCounterBlock.FACING);
            BlockPos target = pos.relative(facing);

            int passed;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target, facing.getOpposite());
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
        if (level.getGameTime() % 20L == 0L) {
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
        secBuckets[(int) (lastSec % 60L)] += n;
        minBuckets[(int) (lastMin % 60L)] += n;
        String key = (!perItem.containsKey(id) && perItem.size() >= MAX_TRACKED_ITEMS) ? OTHER_KEY : id;
        perItem.merge(key, (long) n, Long::sum);
    }

    /** Diff the handler per item type; count every item whose stock went down. */
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
            for (Map.Entry<String, Integer> e : lastContents.entrySet()) {
                int left = e.getValue() - now.getOrDefault(e.getKey(), 0);
                if (left > 0) {
                    record(e.getKey(), left);
                    passed += left;
                }
            }
        }
        lastContents = now;
        return passed;
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
                int n = item.getItem().getCount();
                record(idOf(item.getItem()), n);
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
        total = tag.getLong("Total");
        uptimeTicks = tag.getLong("Uptime");
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
