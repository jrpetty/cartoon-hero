package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Field notes: what a Runner has surveyed and not yet brought home.
 *
 * <p>Charting used to land on the Glade's map the instant a Runner's boot
 * touched a new cell. That made the single most dangerous job in the mode
 * completely risk-free at what it was actually for: you could walk out, chart
 * forty cells, die at the far end of the map, and the Glade kept every one of
 * them. Nothing a Runner carried could ever be lost, so nothing a Runner did was
 * ever a gamble.
 *
 * <p>Now the survey is <em>carried</em>. It goes onto the Glade's chart when you
 * walk back through the door and not before, exactly as job experience does. Die
 * out there and it falls on the floor where you fell - a real item, in a real
 * place, that somebody else can go and get.
 *
 * <h2>Why it is worth going after</h2>
 *
 * <p>Notes are not personal. Whoever carries them into the Glade banks them, and
 * the day's work they represent is paid to <em>that</em> person. A recovery run
 * to the spot where somebody died is genuinely worth making, which is the whole
 * point: it turns a death into a place on the map that matters, instead of a
 * line in chat.
 *
 * <h2>Why the Glade chart does not flicker</h2>
 *
 * <p>The Chart Floor only ever shows what came home. A Runner three hundred
 * blocks out watching their own progress appear on a map they are nowhere near
 * was always the wrong picture - the Glade knows what it has been told.
 */
public final class MazeNotes {

    /** NBT key on the item. Cell indices, packed as {@code z * GRID + x}. */
    private static final String CELLS = "aztecabyss_notes";
    /** Which layout the survey was made on, so it charts onto the right one. */
    private static final String LAYOUT = "aztecabyss_notes_layout";
    private static final String AUTHOR = "aztecabyss_notes_author";

    /**
     * Uncommitted survey, per player. Transient on purpose: it belongs to a trip,
     * and a trip does not survive a restart any more than the runner does.
     */
    private static final Map<UUID, Set<Integer>> PENDING = new HashMap<>();

    private MazeNotes() {
    }

    private static int index(int cellX, int cellZ) {
        return cellZ * MazeData.GRID + cellX;
    }

    public static int carrying(UUID who) {
        Set<Integer> mine = PENDING.get(who);
        return mine == null ? 0 : mine.size();
    }

    public static void clearAll() {
        PENDING.clear();
        lastMilestone = -1;
    }

    /**
     * Notes a cell down. Nothing reaches the Glade's chart from here.
     *
     * @return true if this is ground the Glade has not already got
     */
    public static boolean record(ServerLevel level, ServerPlayer who, int cellX, int cellZ) {
        if (cellX < 0 || cellZ < 0 || cellX >= MazeData.GRID || cellZ >= MazeData.GRID) {
            return false;
        }
        if (level.getServer() == null || MazeCharts.get(level.getServer()).charted(cellX, cellZ)) {
            return false;
        }
        return PENDING.computeIfAbsent(who.getUUID(), k -> new LinkedHashSet<>())
                .add(index(cellX, cellZ));
    }

    /**
     * Everything carried goes onto the Glade's chart, and pays the carrier.
     *
     * <p>Called the moment somebody steps back inside, alongside the job
     * experience that banks in the same place and for the same reason.
     */
    public static void bank(ServerLevel level, ServerPlayer who) {
        Set<Integer> mine = PENDING.remove(who.getUUID());
        if (mine == null || mine.isEmpty() || level.getServer() == null) {
            return;
        }
        // Union chart only. The maze is one layout with doors that move, so a
        // chart per night would be a new chart every single day, each obsolete
        // by morning - and MazeCharts would grow one BitSet per day forever.
        int fresh = commit(level, who, mine, null);
        if (fresh <= 0) {
            return;
        }
        who.displayClientMessage(Component.literal(
                "§b✎ Survey filed. §f" + fresh + "§7 new cells on the Glade's chart."), false);
        MazeDayWork.get(level).add(level, who, MazeJobs.RUNNER, fresh);
        milestone(level);
    }

    /**
     * "The Glade has charted N%", said when it becomes true.
     *
     * <p>Lives here rather than on the walking hook because that is the moment
     * the number actually moves. Announcing it as a Runner stepped on a cell
     * meant reading a percentage that had not changed yet.
     */
    private static void milestone(ServerLevel level) {
        MazeCharts charts = MazeCharts.get(level.getServer());
        int pct = charts.gladePercent();
        if (pct <= 0 || pct % 10 != 0 || pct == lastMilestone) {
            return;
        }
        lastMilestone = pct;
        for (ServerPlayer other : level.players()) {
            other.displayClientMessage(Component.literal(
                    "§6✎ The Glade has charted §f" + pct + "%§6 of the maze."), true);
        }
    }

    /** So a milestone is announced once rather than on every filing at that mark. */
    private static int lastMilestone = -1;

    /** Writes a set of cell indices onto the chart. Returns how many were new. */
    private static int commit(ServerLevel level, ServerPlayer who, Set<Integer> cells, String layout) {
        MazeCharts charts = MazeCharts.get(level.getServer());
        int fresh = 0;
        for (int packed : cells) {
            int cellX = packed % MazeData.GRID;
            int cellZ = packed / MazeData.GRID;
            if (charts.chart(who.getUUID(), cellX, cellZ, layout)) {
                fresh++;
            }
        }
        return fresh;
    }

    /**
     * Drops what they were carrying, where they fell.
     *
     * <p>Deliberately an item on the ground rather than a message. The whole
     * value of this is that it has a <em>location</em> - somewhere on the map
     * that is now worth going to, and that gets more dangerous the longer it is
     * left.
     */
    public static void drop(ServerLevel level, ServerPlayer who) {
        Set<Integer> mine = PENDING.remove(who.getUUID());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        ItemStack notes = create(mine, "", who.getGameProfile().getName());
        Block.popResource(level, who.blockPosition(), notes);
        who.displayClientMessage(Component.literal(
                "§4✖ Your survey fell with you. §7" + mine.size()
                        + " cells, where you died."), false);
        for (ServerPlayer p : level.players()) {
            if (p != who) {
                p.displayClientMessage(Component.literal(
                        "§7✎ §f" + who.getGameProfile().getName() + "§7's notes are out there — §f"
                                + mine.size() + "§7 cells, unfiled."), false);
            }
        }
    }

    public static ItemStack create(Set<Integer> cells, String layout, String author) {
        ItemStack stack = new ItemStack(Items.MAP);
        int[] packed = new int[cells.size()];
        int i = 0;
        for (int c : cells) {
            packed[i++] = c;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putIntArray(CELLS, packed);
            tag.putString(LAYOUT, layout);
            tag.putString(AUTHOR, author);
        });
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§b" + author + "'s Field Notes"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§7" + packed.length + " cells surveyed"),
                Component.literal("§8Carry it into the Glade to file it"))));
        return stack;
    }

    public static boolean isNotes(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.MAP)
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().contains(CELLS);
    }

    /**
     * Files any notes the player is carrying, theirs or anybody else's.
     *
     * <p>Whoever brings them in gets the credit. A recovery run is real work and
     * is paid as such - and the alternative, crediting the corpse, would mean the
     * person who walked out to fetch them got nothing at all.
     */
    public static void redeemCarried(ServerLevel level, ServerPlayer who) {
        if (level.getServer() == null) {
            return;
        }
        List<ItemStack> found = new ArrayList<>();
        for (int slot = 0; slot < who.getInventory().getContainerSize(); slot++) {
            ItemStack stack = who.getInventory().getItem(slot);
            if (isNotes(stack)) {
                found.add(stack);
            }
        }
        // Collected first, emptied after: shrinking a stack while walking the
        // inventory it lives in is the bug shape this codebase keeps producing.
        for (ItemStack stack : found) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            int[] packed = tag.getIntArray(CELLS);
            String layout = tag.getString(LAYOUT);
            String author = tag.getString(AUTHOR);
            Set<Integer> cells = new LinkedHashSet<>();
            for (int c : packed) {
                cells.add(c);
            }
            int fresh = commit(level, who, cells, layout.isEmpty() ? null : layout);
            stack.setCount(0);
            for (ServerPlayer p : level.players()) {
                p.displayClientMessage(Component.literal(
                        "§b§l✎ NOTES RECOVERED §r§7— §f" + who.getGameProfile().getName()
                                + "§7 filed " + author + "'s survey. §f" + fresh + "§7 new cells."), false);
            }
            level.playSound(null, who.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS, 1.0F, 1.5F);
            if (fresh > 0) {
                MazeDayWork.get(level).add(level, who, MazeJobs.RUNNER, fresh);
                milestone(level);
            }
        }
    }
}
