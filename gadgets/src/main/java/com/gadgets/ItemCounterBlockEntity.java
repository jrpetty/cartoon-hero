package com.gadgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Counts items passing the face it points at and pulses redstone every N items.
 *
 * <p>Beyond the pulse counter it keeps live statistics: rolling items-per-minute
 * and items-per-hour rates, a lifetime total with uptime, and a per-item-type
 * breakdown. Container mode diffs the watched inventory per item type, so it
 * knows exactly which items left; flow mode reads the passing item entities.
 * The chosen readout (/min, /hour, total, pulse) is synced to clients and drawn
 * on the display face by {@link ItemCounterRenderer}.
 */
public class ItemCounterBlockEntity extends BlockEntity {
    private static final int INTERVAL = 2;
    private static final int PULSE_TICKS = 4;
    private static final int[] THRESHOLDS = {1, 4, 8, 16, 32, 64};
    private static final int MAX_TRACKED_ITEMS = 64;
    private static final String OTHER_KEY = "other";

    /** Face readout modes, cycled with sneak + empty hand. */
    public static final String[] MODE_LABELS = {"/min", "/hour", "total", "pulse"};

    private int threshold = 8;
    private int count = 0;
    private long poweredUntil = 0L;
    private int displayMode = 0;

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
        super(Gadgets.ITEM_COUNTER_BE, pos, state);
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
        return MODE_LABELS[displayMode];
    }

    public static void tick(World world, BlockPos pos, BlockState state, ItemCounterBlockEntity be) {
        be.uptimeTicks++;
        be.advanceClocks(world.getTime());

        if (world.getTime() % INTERVAL == 0L) {
            Direction facing = state.get(ItemCounterBlock.FACING);
            BlockPos target = pos.offset(facing);

            int passed;
            Inventory container = HopperBlockEntity.getInventoryAt(world, target);
            be.watchingContainer = container != null;
            if (container != null) {
                passed = be.sampleContainer(container);
                be.seenEntities.clear(); // not in flow mode; forget any stragglers
            } else {
                passed = be.sampleFlow(world, target);
                be.lastContents = null; // container gone; reseed if it returns
            }

            if (passed > 0) {
                be.count += passed;
                be.markDirty();
            }
            if (be.count >= be.threshold) {
                be.count %= be.threshold; // a bulk drop fires one pulse, never a runaway
                be.poweredUntil = world.getTime() + PULSE_TICKS;
                be.markDirty();
            }
        }

        boolean powered = world.getTime() < be.poweredUntil;
        if (state.get(ItemCounterBlock.POWERED) != powered) {
            world.setBlockState(pos, state.with(ItemCounterBlock.POWERED, powered), Block.NOTIFY_ALL);
        }

        // Refresh the face readout once a second; push to clients only on change.
        if (world.getTime() % 20L == 0L) {
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

    /** Diff the container per item type; count every item whose stock went down. */
    private int sampleContainer(Inventory inv) {
        Map<String, Integer> now = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
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
    private int sampleFlow(World world, BlockPos target) {
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, new Box(target), e -> true);
        int passed = 0;
        Set<Integer> present = new HashSet<>();
        for (ItemEntity item : items) {
            int id = item.getId();
            present.add(id);
            if (seenEntities.add(id)) {
                int n = item.getStack().getCount();
                record(idOf(item.getStack()), n);
                passed += n;
            }
        }
        seenEntities.retainAll(present); // forget items that have moved on
        return passed;
    }

    private static String idOf(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static int sum(int[] buckets) {
        int total = 0;
        for (int b : buckets) {
            total += b;
        }
        return total;
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
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
        Identifier ident = Identifier.tryParse(id);
        if (ident != null && Registries.ITEM.containsId(ident)) {
            return Registries.ITEM.get(ident).getName().getString();
        }
        return id;
    }

    // --- sync + persistence ---

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        // Lean tag for clients: just what the face renderer needs.
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Threshold", threshold);
        nbt.putInt("Count", count);
        nbt.putInt("DisplayMode", displayMode);
        nbt.putLong("Total", total);
        nbt.putInt("RateMin", rateMin);
        nbt.putInt("RateHour", rateHour);
        return nbt;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Threshold", threshold);
        nbt.putInt("Count", count);
        nbt.putLong("PoweredUntil", poweredUntil);
        nbt.putInt("DisplayMode", displayMode);
        nbt.putLong("Total", total);
        nbt.putLong("Uptime", uptimeTicks);
        nbt.putInt("RateMin", rateMin);
        nbt.putInt("RateHour", rateHour);
        NbtCompound items = new NbtCompound();
        perItem.forEach(items::putLong);
        nbt.put("PerItem", items);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Threshold")) {
            threshold = nbt.getInt("Threshold");
        }
        count = nbt.getInt("Count");
        poweredUntil = nbt.getLong("PoweredUntil");
        displayMode = nbt.getInt("DisplayMode") % MODE_LABELS.length;
        total = nbt.getLong("Total");
        uptimeTicks = nbt.getLong("Uptime");
        rateMin = nbt.getInt("RateMin");
        rateHour = nbt.getInt("RateHour");
        if (nbt.contains("PerItem")) {
            perItem.clear();
            NbtCompound items = nbt.getCompound("PerItem");
            for (String key : items.getKeys()) {
                perItem.put(key, items.getLong(key));
            }
        }
    }
}
