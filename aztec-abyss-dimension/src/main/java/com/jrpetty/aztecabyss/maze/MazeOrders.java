package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The requisition slate: what you are asking the Box to send up tomorrow.
 *
 * <p>The Box delivered a fixed crate every dawn, which made it weather rather
 * than a system. Nobody ever decided anything about it: the same seeds and the
 * same iron arrived whether the Glade was starving, out of arrows, or sitting on
 * a fortnight of surplus. A supply line nobody chooses is scenery with items in
 * it.
 *
 * <p>So from the second morning the Box sends what you asked for and nothing
 * else. A hundred points a day, a catalogue with a price on everything, and one
 * decision every evening that the whole next day runs on.
 *
 * <h2>Points come from ground covered</h2>
 *
 * <p>Your own charting is worth double the Glade's, but the Glade's counts -
 * which is the shape the whole mode wants. A Runner who has walked half the maze
 * is genuinely richer than one who has not, and a Track-hoe who has never left
 * the clearing still gets more than they did last week because the settlement
 * around them is doing well. Nobody is punished for the job they took, and
 * everybody has a reason to want the maze charted.
 *
 * <h2>No gear on the catalogue, ever</h2>
 *
 * <p>You cannot order a sword, a pickaxe or a breastplate. You order iron and
 * wood and you <em>make</em> them, which does three things at once: it keeps the
 * crafting bench relevant, it makes the Builder's forge bonus worth having
 * because their versions are simply better, and it stops the Box being a shop
 * that sells the answer to every problem.
 *
 * <h2>Use it or lose it</h2>
 *
 * <p>Points do not roll over. Banking would turn the daily decision into an
 * occasional one and let a cautious Glade skip four days to buy something
 * enormous on the fifth, which is a different and much worse game than choosing
 * what you need tomorrow morning.
 */
public final class MazeOrders extends SavedData {

    public static final String NAME = "aztecabyss_maze_orders";

    /** What everybody gets, every day, before anything they have earned. */
    public static final int BASE_POINTS = 100;
    /** However well it goes, a day's slate is bounded. */
    public static final int MAX_POINTS = 400;
    /** The first morning the Box stops giving and starts filling orders. */
    public static final int REQUISITION_FROM_DAY = 2;

    /**
     * One line of the catalogue.
     *
     * <p>Priced as a bundle rather than per unit, because "one cobblestone for a
     * point" is not a decision anybody wants to make thirty-two times. The
     * bundle is the unit of thought.
     */
    public record Entry(String id, String display, Item item, int count, int cost) {
    }

    private static final List<Entry> CATALOGUE = List.of(
            // --- metal and stone: the floor does not break, so none of this exists here
            new Entry("diamond", "Diamond", Items.DIAMOND, 1, 7),
            new Entry("iron", "Iron ingot", Items.IRON_INGOT, 1, 4),
            new Entry("gold", "Gold ingot", Items.GOLD_INGOT, 1, 3),
            new Entry("copper", "Copper ingot", Items.COPPER_INGOT, 2, 2),
            new Entry("redstone", "Redstone", Items.REDSTONE, 8, 2),
            new Entry("coal", "Coal", Items.COAL, 8, 2),
            new Entry("cobble", "Cobblestone", Items.COBBLESTONE, 32, 2),
            new Entry("stone", "Stone", Items.STONE, 16, 2),
            new Entry("glass", "Glass", Items.GLASS, 16, 2),
            new Entry("clay", "Bricks", Items.BRICK, 8, 2),

            // --- timber
            new Entry("logs", "Oak logs", Items.OAK_LOG, 8, 3),
            new Entry("planks", "Oak planks", Items.OAK_PLANKS, 32, 2),
            new Entry("sticks", "Sticks", Items.STICK, 16, 1),
            new Entry("sapling", "Oak saplings", Items.OAK_SAPLING, 4, 2),
            new Entry("darkoak", "Dark oak saplings", Items.DARK_OAK_SAPLING, 2, 2),

            // --- things that come off animals, of which there are none
            new Entry("string", "String", Items.STRING, 8, 3),
            new Entry("leather", "Leather", Items.LEATHER, 4, 3),
            new Entry("feather", "Feathers", Items.FEATHER, 8, 2),
            new Entry("arrows", "Arrows", Items.ARROW, 16, 3),
            new Entry("wool", "White wool", Items.WHITE_WOOL, 8, 2),

            // --- soil
            new Entry("seeds", "Wheat seeds", Items.WHEAT_SEEDS, 16, 1),
            new Entry("carrots", "Carrots", Items.CARROT, 8, 1),
            new Entry("potatoes", "Potatoes", Items.POTATO, 8, 1),
            new Entry("beetroot", "Beetroot seeds", Items.BEETROOT_SEEDS, 8, 1),
            new Entry("melon", "Melon seeds", Items.MELON_SEEDS, 4, 1),
            new Entry("pumpkin", "Pumpkin seeds", Items.PUMPKIN_SEEDS, 4, 1),
            new Entry("cane", "Sugar cane", Items.SUGAR_CANE, 8, 2),
            new Entry("bonemeal", "Bone meal", Items.BONE_MEAL, 16, 2),

            // --- food
            new Entry("bread", "Bread", Items.BREAD, 8, 2),
            new Entry("steak", "Cooked beef", Items.COOKED_BEEF, 8, 3),
            new Entry("apple", "Golden apple", Items.GOLDEN_APPLE, 1, 8),

            // --- light and sundries
            new Entry("torches", "Torches", Items.TORCH, 32, 1),
            new Entry("lanterns", "Lanterns", Items.LANTERN, 4, 2),
            new Entry("signs", "Oak signs", Items.OAK_SIGN, 8, 1),
            new Entry("paper", "Paper", Items.PAPER, 16, 2),
            new Entry("bottles", "Glass bottles", Items.GLASS_BOTTLE, 8, 2),
            new Entry("carpet", "White carpet", Items.WHITE_CARPET, 16, 1),

            // --- the two that change how a night ends, priced accordingly
            new Entry("bandages", "Bandages", Items.PAPER, 0, 6),
            new Entry("serum", "Griever Serum", Items.POTION, 0, 25)
    );

    public static List<Entry> catalogue() {
        return CATALOGUE;
    }

    public static Entry entry(String id) {
        String want = id.toLowerCase(Locale.ROOT);
        for (Entry e : CATALOGUE) {
            if (e.id().equals(want)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Two entries are built rather than stacked, because they are not items in
     * the ordinary sense - the serum and the bandage are potions carrying custom
     * effects, so their {@code count} is zero and this makes them instead.
     */
    public static List<ItemStack> build(Entry e, int qty, int dressingRank) {
        List<ItemStack> out = new ArrayList<>();
        if ("serum".equals(e.id())) {
            for (int i = 0; i < qty; i++) {
                out.add(MazeSerum.create());
            }
            return out;
        }
        if ("bandages".equals(e.id())) {
            for (int i = 0; i < qty * 4; i++) {
                out.add(MazeBandage.create(dressingRank));
            }
            return out;
        }
        int total = e.count() * qty;
        int max = e.item().getDefaultMaxStackSize();
        while (total > 0) {
            int take = Math.min(total, max);
            out.add(new ItemStack(e.item(), take));
            total -= take;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Budget
    // ------------------------------------------------------------------

    /**
     * What one player has to spend today.
     *
     * <p>Your own ground counts double the Glade's. Both count, so a Track-hoe
     * who never leaves the clearing still gets richer as the settlement learns
     * the maze - nobody is punished for the trade they took, and everybody has a
     * reason to want the Runners out there.
     */
    public static int budget(ServerLevel level, UUID who) {
        if (level.getServer() == null) {
            return BASE_POINTS;
        }
        MazeCharts charts = MazeCharts.get(level.getServer());
        return Math.min(MAX_POINTS, BASE_POINTS + charts.myPercent(who) * 2 + charts.gladePercent());
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Per player, catalogue id to quantity ordered for the next delivery. */
    private final Map<UUID, Map<String, Integer>> slates = new LinkedHashMap<>();

    public Map<String, Integer> slate(UUID who) {
        return slates.getOrDefault(who, Map.of());
    }

    public int committed(UUID who) {
        int total = 0;
        for (Map.Entry<String, Integer> line : slate(who).entrySet()) {
            Entry e = entry(line.getKey());
            if (e != null) {
                total += e.cost() * line.getValue();
            }
        }
        return total;
    }

    public int remaining(ServerLevel level, UUID who) {
        return budget(level, who) - committed(who);
    }

    /**
     * Adds to a slate.
     *
     * @return null on success, or why not
     */
    public String add(ServerLevel level, UUID who, Entry e, int qty) {
        if (qty < 1 || qty > 64) {
            return "Between one and sixty-four.";
        }
        int cost = e.cost() * qty;
        int left = remaining(level, who);
        if (cost > left) {
            return "That is " + cost + " and you have " + left + ".";
        }
        slates.computeIfAbsent(who, k -> new LinkedHashMap<>()).merge(e.id(), qty, Integer::sum);
        setDirty();
        return null;
    }

    /** Takes a line back off, refunding it. Returns how many were removed. */
    public int cancel(UUID who, String id) {
        Map<String, Integer> mine = slates.get(who);
        if (mine == null) {
            return 0;
        }
        Integer had = mine.remove(id.toLowerCase(Locale.ROOT));
        setDirty();
        return had == null ? 0 : had;
    }

    public void clear(UUID who) {
        slates.remove(who);
        setDirty();
    }

    /** Wipes every slate. Called once the Box has filled them. */
    public void clearAll() {
        slates.clear();
        setDirty();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        slates.forEach((id, mine) -> {
            CompoundTag one = new CompoundTag();
            mine.forEach(one::putInt);
            all.put(id.toString(), one);
        });
        tag.put("Slates", all);
        return tag;
    }

    public static MazeOrders load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeOrders out = new MazeOrders();
        CompoundTag all = tag.getCompound("Slates");
        for (String id : all.getAllKeys()) {
            try {
                UUID who = UUID.fromString(id);
                CompoundTag one = all.getCompound(id);
                Map<String, Integer> mine = new LinkedHashMap<>();
                for (String k : one.getAllKeys()) {
                    mine.put(k, one.getInt(k));
                }
                out.slates.put(who, mine);
            } catch (IllegalArgumentException ignored) {
                // Not a uuid. Not worth losing everybody else's order over.
            }
        }
        return out;
    }

    public static SavedData.Factory<MazeOrders> factory() {
        return new SavedData.Factory<>(MazeOrders::new, MazeOrders::load, null);
    }

    public static MazeOrders get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static MazeOrders get(ServerLevel level) {
        return get(level.getServer());
    }
}
