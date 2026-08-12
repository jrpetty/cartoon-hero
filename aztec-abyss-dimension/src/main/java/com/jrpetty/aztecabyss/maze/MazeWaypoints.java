package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapDecorations;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Waypoint torches: breadcrumbs that survive the doors moving.
 *
 * <p>A Runner deep in a maze that rearranges itself nightly needs marks that
 * outlive the walls. A soul torch planted in a corridor becomes a waypoint:
 * a light-blue marker on every Runner's Chart, at the exact spot, for as long
 * as the torch stands. The corridors themselves may close around it - the mark
 * on the sheet stays honest about where the torch is, not whether you can
 * still get there, which is exactly the kind of half-truth this maze deals in.
 *
 * <p>The torch is the marker. Knock it down (or let the reshape wall it over)
 * and the mark quietly leaves the charts on the next sweep - no ghost dots.
 *
 * <p>Chartwrights at rank three chart the cells around a torch as they plant
 * it, so placing a waypoint is also a small act of surveying.
 */
public final class MazeWaypoints extends SavedData {

    public static final String NAME = "aztecabyss_maze_waypoints";

    /** The chart only has so much face. More dots than this is noise anyway. */
    private static final int CAP = 24;

    private final Set<Long> posts = new LinkedHashSet<>();

    /** A soul torch has gone up in a corridor. */
    public void planted(ServerLevel level, ServerPlayer who, BlockPos at) {
        if (posts.size() >= CAP) {
            who.displayClientMessage(Component.literal(
                    "§7The charts hold " + CAP + " waypoints. §8This one will burn unmarked."), true);
            return;
        }
        if (!posts.add(at.asLong())) {
            return;
        }
        setDirty();
        who.displayClientMessage(Component.literal(
                "§b✦ Waypoint set. §7It will show on every Runner's Chart."), true);
        if (MazeSkills.rankOf(level, who.getUUID(), "chartwright") >= 3) {
            MazeCharts charts = MazeCharts.get(level);
            int cx = at.getX() / MazeData.CELL;
            int cz = at.getZ() / MazeData.CELL;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    charts.chart(who.getUUID(), cx + dx, cz + dz);
                }
            }
        }
        refresh(level);
    }

    /**
     * Sweeps the ledger against the world and repaints every chart in reach.
     *
     * <p>Validation is lazy on purpose: the reshape does not know it walled a
     * torch over, and the torch does not know it was broken. Checking the block
     * that is actually standing, at repaint time, is the one test that cannot
     * drift out of date.
     */
    public void refresh(ServerLevel level) {
        boolean changed = posts.removeIf(packed -> {
            BlockPos at = BlockPos.of(packed);
            var state = level.getBlockState(at);
            return !state.is(Blocks.SOUL_TORCH) && !state.is(Blocks.SOUL_WALL_TORCH);
        });
        if (changed) {
            setDirty();
        }
        Map<String, MapDecorations.Entry> marks = new HashMap<>();
        int i = 0;
        for (long packed : posts) {
            BlockPos at = BlockPos.of(packed);
            marks.put("wp" + i++, new MapDecorations.Entry(
                    MapDecorationTypes.BLUE_MARKER,
                    at.getX() + 0.5, at.getZ() + 0.5, 180.0F));
        }
        MapDecorations decorations = new MapDecorations(marks);
        for (ServerPlayer p : level.players()) {
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                ItemStack stack = p.getInventory().getItem(slot);
                if (isChart(stack)) {
                    stack.set(net.minecraft.core.component.DataComponents.MAP_DECORATIONS,
                            decorations);
                }
            }
        }
    }

    /** A Runner's Chart (or a pinned copy of one), by its name. */
    private static boolean isChart(ItemStack stack) {
        if (!stack.is(Items.FILLED_MAP)) {
            return false;
        }
        Component name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name != null && name.getString().contains("Chart");
    }

    public void clearAll() {
        posts.clear();
        setDirty();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] out = new long[posts.size()];
        int i = 0;
        for (long p : posts) {
            out[i++] = p;
        }
        tag.putLongArray("Posts", out);
        return tag;
    }

    public static MazeWaypoints load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeWaypoints out = new MazeWaypoints();
        for (long p : tag.getLongArray("Posts")) {
            out.posts.add(p);
        }
        return out;
    }

    public static SavedData.Factory<MazeWaypoints> factory() {
        return new SavedData.Factory<>(MazeWaypoints::new, MazeWaypoints::load, null);
    }

    public static MazeWaypoints get(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }
}
