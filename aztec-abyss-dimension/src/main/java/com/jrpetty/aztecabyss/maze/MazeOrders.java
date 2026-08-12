package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.config.AbyssConfig;
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

    /** The first morning the Box stops giving and starts filling orders. */
    public static final int REQUISITION_FROM_DAY = 2;

    /**
     * One line of the catalogue.
     *
     * <p>Priced as a bundle rather than per unit, because "one cobblestone for a
     * point" is not a decision anybody wants to make thirty-two times. The
     * bundle is the unit of thought.
     */
    public record Entry(String group, String id, String display, Item item, int count, int cost) {
    }

    private static final List<Entry> CATALOGUE = List.of(
            // --- metal: the floor does not break, so none of this is mineable here
            new Entry("Metal", "diamond", "Diamond", Items.DIAMOND, 1, 7),
            new Entry("Metal", "iron", "Iron ingot", Items.IRON_INGOT, 1, 4),
            new Entry("Metal", "gold", "Gold ingot", Items.GOLD_INGOT, 1, 3),
            new Entry("Metal", "copper", "Copper ingot", Items.COPPER_INGOT, 2, 2),
            new Entry("Metal", "redstone", "Redstone", Items.REDSTONE, 8, 2),
            new Entry("Metal", "coal", "Coal", Items.COAL, 8, 2),

            // --- stone
            new Entry("Stone", "cobble", "Cobblestone", Items.COBBLESTONE, 32, 2),
            new Entry("Stone", "stone", "Stone", Items.STONE, 16, 2),
            new Entry("Stone", "glass", "Glass", Items.GLASS, 16, 2),
            new Entry("Stone", "clay", "Bricks", Items.BRICK, 8, 2),
            // Flint, because gravel does not exist in here and the floor never
            // breaks. Without this line the fletching table is missing a leg and
            // a bow is an ornament.
            new Entry("Stone", "flint", "Flint", Items.FLINT, 8, 3),

            // --- timber
            new Entry("Timber", "logs", "Oak logs", Items.OAK_LOG, 8, 3),
            new Entry("Timber", "planks", "Oak planks", Items.OAK_PLANKS, 32, 2),
            new Entry("Timber", "sticks", "Sticks", Items.STICK, 16, 1),
            new Entry("Timber", "sapling", "Oak saplings", Items.OAK_SAPLING, 4, 2),
            new Entry("Timber", "darkoak", "Dark oak saplings", Items.DARK_OAK_SAPLING, 2, 2),

            // --- things that come off animals, of which there are none
            new Entry("Cloth", "string", "String", Items.STRING, 8, 3),
            new Entry("Cloth", "leather", "Leather", Items.LEATHER, 4, 3),
            new Entry("Cloth", "feather", "Feathers", Items.FEATHER, 8, 2),
            new Entry("Cloth", "wool", "White wool", Items.WHITE_WOOL, 8, 2),

            // --- soil
            new Entry("Soil", "seeds", "Wheat seeds", Items.WHEAT_SEEDS, 16, 1),
            new Entry("Soil", "carrots", "Carrots", Items.CARROT, 8, 1),
            new Entry("Soil", "potatoes", "Potatoes", Items.POTATO, 8, 1),
            new Entry("Soil", "beetroot", "Beetroot seeds", Items.BEETROOT_SEEDS, 8, 1),
            new Entry("Soil", "melon", "Melon seeds", Items.MELON_SEEDS, 4, 1),
            new Entry("Soil", "pumpkin", "Pumpkin seeds", Items.PUMPKIN_SEEDS, 4, 1),
            new Entry("Soil", "cane", "Sugar cane", Items.SUGAR_CANE, 8, 2),
            new Entry("Soil", "bonemeal", "Bone meal", Items.BONE_MEAL, 16, 2),

            // --- food
            //
            // No golden apple. It was the one line on the sheet that sold the
            // answer rather than the stock: eight points and the best heal in
            // the game arrived in a crate, which is exactly what the rest of the
            // catalogue is built to avoid. Gold is on the sheet at three a bar
            // and apples come off the leaves in the wood, so a golden apple is
            // now eight bars and an afternoon in the trees - twenty-four points
            // and some actual work, instead of eight and a click.
            new Entry("Food", "bread", "Bread", Items.BREAD, 8, 2),
            new Entry("Food", "steak", "Cooked beef", Items.COOKED_BEEF, 8, 3),

            // --- light and sundries
            new Entry("Camp", "torches", "Torches", Items.TORCH, 32, 1),
            new Entry("Camp", "lanterns", "Lanterns", Items.LANTERN, 4, 2),
            new Entry("Camp", "signs", "Oak signs", Items.OAK_SIGN, 8, 1),
            new Entry("Camp", "paper", "Paper", Items.PAPER, 16, 2),
            new Entry("Camp", "bottles", "Glass bottles", Items.GLASS_BOTTLE, 8, 2),
            new Entry("Camp", "carpet", "White carpet", Items.WHITE_CARPET, 16, 1),

            // --- the two that change how a night ends, priced accordingly
            new Entry("Medical", "bandages", "Bandages", Items.PAPER, 0, 6),
            new Entry("Medical", "serum", "Griever Serum", Items.POTION, 0, 25)
    );

    public static List<Entry> catalogue() {
        return CATALOGUE;
    }

    /**
     * The catalogue's group names, in catalogue order.
     *
     * <p>Derived from the entries rather than listed separately, so a new line
     * cannot end up in a group the tab rail has never heard of - the commonest
     * way a menu like this rots.
     */
    public static List<String> groups() {
        List<String> out = new ArrayList<>();
        for (Entry e : CATALOGUE) {
            if (!out.contains(e.group())) {
                out.add(e.group());
            }
        }
        return out;
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
    public static int pool(ServerLevel level) {
        MazeOrders orders = get(level);
        return orders.heads * AbyssConfig.MAZE_POOL_PER_PLAYER.get()
                + orders.totalBounty()
                + MazeDayWork.totalCredits(level);
    }

    /** The flat part: so many a head, for however many heads dawn found. */
    public static int fromHeads(ServerLevel level) {
        return get(level).heads * AbyssConfig.MAZE_POOL_PER_PLAYER.get();
    }

    /**
     * How many people the pool was sized for.
     *
     * <p>Snapshotted rather than counted live, because a pool that tracks the
     * current headcount shrinks when somebody logs off at dusk - underneath
     * orders that were already filed against it. The Glade was fed for six this
     * morning; it is still fed for six at midnight.
     */
    public int heads() {
        return heads;
    }

    /** Called at dawn, once, when the day's size is decided. */
    public void setHeads(int count) {
        heads = Math.max(0, count);
        setDirty();
    }

    /** Everything killed for, across everybody. */
    public int totalBounty() {
        int total = 0;
        for (int n : bounty.values()) {
            total += n;
        }
        return total;
    }

    /** What one person has put into the pot by killing Grievers. */
    public int bonus(UUID who) {
        return bounty.getOrDefault(who, 0);
    }

    public void addBonus(UUID who, int points) {
        if (points <= 0) {
            return;
        }
        bounty.merge(who, points, Integer::sum);
        setDirty();
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Per player, catalogue id to quantity ordered for the next delivery. */
    private final Map<UUID, Map<String, Integer>> slates = new LinkedHashMap<>();
    /**
     * Points earned today by doing something worth paying for, on top of the
     * charting allowance.
     *
     * <p>Charting was the only thing that moved the budget, which quietly said
     * the only work worth rewarding was walking. Killing a Griever is the single
     * hardest thing anybody does in here - well over a hundred health, knockback
     * resistant, and it takes a squad - and it paid a serum and a scoreboard
     * line. Now it pays in the currency that decides what tomorrow looks like.
     *
     * <p>Saved, so a restart in the middle of a day does not wipe what a squad
     * bled for, and cleared with the slates each dawn.
     */
    private final Map<UUID, Integer> bounty = new LinkedHashMap<>();
    /**
     * How many people were in the maze when the day's pool was struck.
     *
     * <p>Saved, because a restart mid-day must not silently resize the Glade's
     * budget underneath orders already committed against it.
     */
    private int heads = 0;

    /** Everybody with anything on the slate today. */
    public java.util.Set<UUID> everyone() {
        return java.util.Set.copyOf(slates.keySet());
    }

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

    /** Everything the whole Glade has committed, across every slate. */
    public int committedTotal() {
        int total = 0;
        for (UUID who : slates.keySet()) {
            total += committed(who);
        }
        return total;
    }

    /** What is left in the pot for anybody to spend. */
    public static int remaining(ServerLevel level) {
        return pool(level) - get(level).committedTotal();
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
        int left = remaining(level);
        if (cost > left) {
            return "That is " + cost + " and the Glade has " + left + ".";
        }
        slates.computeIfAbsent(who, k -> new LinkedHashMap<>()).merge(e.id(), qty, Integer::sum);
        setDirty();
        return null;
    }

    /**
     * Takes some of a line back off, refunding it. Returns how many went.
     *
     * <p>Separate from {@link #cancel} because the screen's minus button removes
     * one bundle and the command removes the whole line, and folding those into
     * one call would mean a mis-click on a slate of twelve iron costs you all
     * twelve.
     */
    public int take(UUID who, String id, int qty) {
        Map<String, Integer> mine = slates.get(who);
        if (mine == null) {
            return 0;
        }
        String key = id.toLowerCase(Locale.ROOT);
        Integer had = mine.get(key);
        if (had == null) {
            return 0;
        }
        int taken = Math.min(had, Math.max(1, qty));
        if (taken >= had) {
            mine.remove(key);
        } else {
            mine.put(key, had - taken);
        }
        if (mine.isEmpty()) {
            slates.remove(who);
        }
        setDirty();
        return taken;
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

    /**
     * A new game starts from an empty ledger.
     *
     * <p>Without this, the previous game's unfilled slates survived the reset
     * and were delivered on the next game's second morning - a crate of iron
     * from a settlement that no longer exists - and bounty credit earned in
     * one game spent in the next. Both halves of the economy are per-game.
     */
    public void resetAll() {
        slates.clear();
        bounty.clear();
        heads = 0;
        setDirty();
    }

    /**
     * Closes the day out once the Box has filled the slates.
     *
     * <h2>What expires and what does not</h2>
     *
     * <p>The head allowance and the day's work both expire. That is what makes
     * the evening a decision: a Glade that underspends has lost the difference,
     * so somebody has to actually sit down and choose.
     *
     * <p>A Griever bounty does not. The broadcast promises twenty credits on
     * tomorrow's slate, and for a kill at noon that was true - you had all
     * afternoon to spend it. For a kill at half past midnight it was a lie: the
     * credits landed, dawn came a minute later, the slate was already filed and
     * the lot was wiped unspent. The hardest thing in the game paid nothing
     * precisely when it was hardest.
     *
     * <p>So the pot is spent cheapest-first: whatever the Glade committed comes
     * out of the perishable half before it touches a bounty, and only the excess
     * eats into what somebody bled for. What is left of that rolls.
     */
    public void settle(ServerLevel level) {
        int perishable = fromHeads(level) + MazeDayWork.totalCredits(level);
        int spentFromBounty = Math.max(0, committedTotal() - perishable);

        // Taken in the order the kills happened, so the oldest bounty is the
        // one that gets spent. Built into a new map first: editing the map being
        // read from is the way this file would break.
        Map<UUID, Integer> carried = new LinkedHashMap<>();
        int owing = spentFromBounty;
        for (Map.Entry<UUID, Integer> paid : bounty.entrySet()) {
            int had = paid.getValue();
            int taken = Math.min(had, owing);
            owing -= taken;
            int left = had - taken;
            if (left > 0) {
                carried.put(paid.getKey(), left);
            }
        }
        slates.clear();
        bounty.clear();
        bounty.putAll(carried);
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
        CompoundTag paid = new CompoundTag();
        bounty.forEach((id, n) -> paid.putInt(id.toString(), n));
        tag.put("Bonus", paid);
        // The doc on the field promised this for a long time before the code
        // kept it: without Heads on disk, a restart mid-day reloaded heads as
        // zero, the pool collapsed to bounty-plus-work, and every order the
        // Glade had already filed was suddenly refused as over budget.
        tag.putInt("Heads", heads);
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
        CompoundTag paid = tag.getCompound("Bonus");
        for (String id : paid.getAllKeys()) {
            try {
                out.bounty.put(UUID.fromString(id), paid.getInt(id));
            } catch (IllegalArgumentException ignored) {
                // Same.
            }
        }
        out.heads = Math.max(0, tag.getInt("Heads"));
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
