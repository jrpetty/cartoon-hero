package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The Box, doing the job it is named for.
 *
 * <p>It is the centrepiece of the Glade and the thing every Glader arrived in,
 * and it was a decorative cage. Meanwhile the maze gave you nothing you could
 * build with - no seeds, no saplings, no string, no iron, no way to replace a
 * broken tool - so the Glade could never become anything. Everybody's inventory
 * only ever went down.
 *
 * <p>Every dawn the lift comes up loaded. That is what makes the Glade a
 * settlement rather than a shelter: a day in it is a day something arrived, and
 * a week of them compounds into a place with tools, crops and armour in it.
 *
 * <h2>What goes in it, and why</h2>
 *
 * <p>The staples crate carries what the maze structurally cannot: seeds and
 * saplings, because there is no soil out there; string, leather and feathers,
 * because those come off animals and there are none; iron and coal, because the
 * floor does not break. Generous on the things you cannot otherwise have and
 * deliberately thin on the things you can - a Glade that is handed everything is
 * a Glade with nothing to do, which is the problem this is fixing.
 *
 * <h2>Role crates</h2>
 *
 * <p>One per job that somebody is actually doing. A Glade with no Med-jack gets
 * no medical crate, which sounds harsh and is the point: the jobs are supposed to
 * be a decision the group makes together, and a decision with no consequence is
 * not one. It also means the crates read as a delivery <em>to people</em> rather
 * than as loot - somebody down there knows who is up here.
 */
public final class TheBox {

    private TheBox() {
    }

    /** Where the crates land inside the cage. */
    private static BlockPos slot(int index) {
        int cx = MazeData.SPAWN_X;
        int cz = MazeData.SPAWN_Z;
        return switch (index) {
            case 0 -> new BlockPos(cx, MazeData.FLOOR_Y + 1, cz - 2);
            case 1 -> new BlockPos(cx - 2, MazeData.FLOOR_Y + 1, cz - 1);
            case 2 -> new BlockPos(cx + 2, MazeData.FLOOR_Y + 1, cz - 1);
            case 3 -> new BlockPos(cx - 2, MazeData.FLOOR_Y + 1, cz + 1);
            case 4 -> new BlockPos(cx + 2, MazeData.FLOOR_Y + 1, cz + 1);
            default -> new BlockPos(cx, MazeData.FLOOR_Y + 1, cz + 2);
        };
    }

    /**
     * The morning delivery.
     *
     * <p>Seeded off the day so a server restart on the same morning does not
     * hand out a second one, and so a day's delivery is the same delivery for
     * everybody who was there to see it.
     */
    public static void deliver(ServerLevel level, int day) {
        if (level.getServer() == null) {
            return;
        }
        int heads = Math.max(1, level.players().size());
        RandomSource rng = RandomSource.create(0xB0E5 + day * 7919L);
        MazeJobs jobs = MazeJobs.get(level);

        // Struck every morning, including the two before requisitions start.
        // Otherwise the pool is zero all through day one - while people are
        // being told at dusk to file an order against it.
        MazeOrders.get(level).setHeads(level.players().size());

        // From the second morning the Box stops giving and starts filling
        // orders. Two days of handouts is enough to learn the place; after that
        // the supply line is a decision somebody has to make every evening.
        if (day >= MazeOrders.REQUISITION_FROM_DAY) {
            fillOrders(level, day);
            return;
        }
        // Use-it-or-lose-it starts on day zero, not day two: without this,
        // work credits from the handout days piled up and quietly inflated
        // the first real pool, contradicting the one rule the pool has.
        MazeDayWork.get(level).clearAll();

        List<ItemStack> staples = staples(rng, heads, day);
        if (day == 0) {
            // Two cures in the first crate. Day one is the only day nobody has
            // had a chance to earn one, and a Glade whose first Changing is
            // unanswerable teaches the wrong lesson about the whole system.
            staples.add(MazeSerum.create());
            staples.add(MazeSerum.create());
        }
        stock(level, slot(0), "STAPLES", Blocks.YELLOW_CONCRETE.defaultBlockState(), staples);

        int next = 1;
        StringBuilder manifest = new StringBuilder();
        for (String job : MazeJobs.ALL) {
            int takers = takers(level, jobs, job);
            if (takers == 0) {
                continue; // nobody does this job; nothing comes up for it
            }
            stock(level, slot(next++), crateName(job), crateColour(job),
                    roleCrate(job, rng, takers, day));
            manifest.append(manifest.isEmpty() ? "" : "§8, ").append(crateName(job));
        }
        String crates = manifest.toString();

        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§6▲ The Box came up. §e§lSTAPLES§7 on yellow"
                            + (crates.isEmpty() ? "§8, and nothing else — nobody here has a trade."
                                    : "§7, and " + crates)), false);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS, 1.0F, 0.7F);
        }
        level.playSound(null, new BlockPos(MazeData.SPAWN_X, MazeData.FLOOR_Y + 2, MazeData.SPAWN_Z),
                SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.4F, 0.6F);
    }

    /**
     * Filling the slates.
     *
     * <p>One crate per person, on their own coloured pad, holding exactly what
     * they asked for. A player who ordered nothing gets a ration of bread rather
     * than an empty box - forgetting to order should be a bad day, not a fatal
     * one, and an empty crate teaches nothing except that the system is broken.
     */
    private static void fillOrders(ServerLevel level, int day) {
        MazeOrders orders = MazeOrders.get(level);
        MazeSkills skills = MazeSkills.get(level);
        // Everyone here this morning, and then everyone with a slate who is
        // not. An order paid for out of the shared pot is owed whether its
        // owner is standing at the cage, asleep in the real world, or waiting
        // in the overworld - it used to be quietly destroyed for all three,
        // because the loop only ever looked at who was in the dimension.
        java.util.LinkedHashSet<java.util.UUID> everyone = new java.util.LinkedHashSet<>();
        for (ServerPlayer p : level.players()) {
            everyone.add(p.getUUID());
        }
        everyone.addAll(orders.everyone());
        int slot = 0;
        for (java.util.UUID who : everyone) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
            List<ItemStack> goods = new ArrayList<>();
            int spent = 0;
            for (var line : orders.slate(who).entrySet()) {
                MazeOrders.Entry e = MazeOrders.entry(line.getKey());
                if (e == null) {
                    continue;
                }
                goods.addAll(MazeOrders.build(level, e, line.getValue(),
                        skills.rank(who, "dressing")));
                spent += e.cost() * line.getValue();
            }
            boolean ordered = !goods.isEmpty();
            String name = p != null ? p.getGameProfile().getName()
                    : level.getServer().getProfileCache() == null ? "away"
                    : level.getServer().getProfileCache().get(who)
                            .map(com.mojang.authlib.GameProfile::getName).orElse("away");
            // Nothing ordered, nothing delivered. The crate still arrives and is
            // still yours; it is simply empty, which says what happened far more
            // plainly than no crate at all would. A consolation ration would make
            // the deadline advisory, and an advisory deadline is not one.
            stock(level, orderSlot(slot++), name,
                    ordered ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
                            : Blocks.RED_CONCRETE.defaultBlockState(), goods);
            if (p != null) {
                final int paid = spent;
                p.displayClientMessage(Component.literal(ordered
                        ? "§6▲ The Box came up. §7Your order is in the cage — §f" + paid + "§7 points spent."
                        : "§c▲ The Box came up empty for you. §7You filed no order last night."), false);
                level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 1.0F, 0.7F);
            }
        }
        // Yesterday closes: the head allowance and the day's work expire, an
        // unspent bounty rolls. Order matters - settle reads yesterday's work,
        // so the work is only cleared afterwards.
        orders.settle(level);
        MazeDayWork.get(level).clearAll();
        // Today's pool is struck now, for the people who are actually here, and
        // held at that size all day. A pool that tracked the live headcount
        // would shrink when somebody logged off at dusk, underneath orders
        // already filed against it.
        orders.setHeads(level.players().size());

        int pool = MazeOrders.pool(level);
        int rolled = orders.totalBounty();
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§6▣ THE GLADE HAS §f" + pool + "§6 CREDITS §8— "
                            + orders.heads() + " of you"
                            + (rolled > 0 ? ", §6" + rolled + "§8 carried from bounties" : "")), false);
            p.displayClientMessage(Component.literal(
                    "§7Earn more by working your trade today. §8/maze work  /maze order"), false);
        }
        level.playSound(null, new BlockPos(MazeData.SPAWN_X, MazeData.FLOOR_Y + 2, MazeData.SPAWN_Z),
                SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.4F, 0.6F);
    }

    /**
     * Where one person's crate lands.
     *
     * <p>Ringed around the outside of the cage rather than inside it, because
     * eight crates will not fit in a five by five box and a queue to reach your
     * own supplies is a bad first minute of the day.
     */
    private static BlockPos orderSlot(int index) {
        int cx = MazeData.SPAWN_X;
        int cz = MazeData.SPAWN_Z;
        int ring = 5;
        int side = index / 4;
        int along = index % 4;
        int offset = -3 + along * 2;
        return switch (side) {
            case 0 -> new BlockPos(cx + offset, MazeData.FLOOR_Y + 1, cz - ring);
            case 1 -> new BlockPos(cx + ring, MazeData.FLOOR_Y + 1, cz + offset);
            case 2 -> new BlockPos(cx + offset, MazeData.FLOOR_Y + 1, cz + ring);
            default -> new BlockPos(cx - ring, MazeData.FLOOR_Y + 1, cz + offset);
        };
    }

    /** How many people are currently doing a given job. */
    private static int takers(ServerLevel level, MazeJobs jobs, String job) {
        int n = 0;
        for (ServerPlayer p : level.players()) {
            if (jobs.is(p.getUUID(), job)) {
                n++;
            }
        }
        return n;
    }

    private static String crateName(String job) {
        return switch (job) {
            case MazeJobs.RUNNER -> "§b§lRUNNERS§7 on blue";
            case MazeJobs.BUILDER -> "§6§lBUILDERS§7 on orange";
            case MazeJobs.MEDJACK -> "§a§lMED-JACKS§7 on white";
            case MazeJobs.TRACKHOE -> "§2§lTRACK-HOES§7 on green";
            default -> "§7SUPPLIES";
        };
    }

    private static net.minecraft.world.level.block.state.BlockState crateColour(String job) {
        return switch (job) {
            case MazeJobs.RUNNER -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case MazeJobs.BUILDER -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            case MazeJobs.MEDJACK -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case MazeJobs.TRACKHOE -> Blocks.GREEN_CONCRETE.defaultBlockState();
            default -> Blocks.YELLOW_CONCRETE.defaultBlockState();
        };
    }

    // ------------------------------------------------------------------
    // What comes up
    // ------------------------------------------------------------------

    /**
     * Scaled by heads, gently.
     *
     * <p>Half again per extra person rather than double. A crate that scales
     * one-for-one makes a big Glade richer per head than a small one, because
     * the work does not scale the same way - four people do not eat four times
     * as much wheat, they clear the field four times as fast.
     */
    private static int per(RandomSource rng, int base, int spread, int heads) {
        int scaled = base + rng.nextInt(Math.max(1, spread));
        return Math.max(1, (int) Math.round(scaled * (1.0 + (heads - 1) * 0.5)));
    }

    private static void add(List<ItemStack> out, Item item, int count) {
        if (count > 0) {
            out.add(new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize())));
        }
    }

    private static List<ItemStack> staples(RandomSource rng, int heads, int day) {
        List<ItemStack> out = new ArrayList<>();

        // Soil. There is none out there, so every crop the Glade will ever grow
        // comes up in this crate.
        add(out, Items.WHEAT_SEEDS, per(rng, 6, 5, heads));
        add(out, Items.CARROT, per(rng, 3, 3, heads));
        add(out, Items.POTATO, per(rng, 3, 3, heads));
        add(out, Items.BEETROOT_SEEDS, per(rng, 2, 3, heads));
        if (rng.nextInt(2) == 0) {
            add(out, Items.MELON_SEEDS, per(rng, 2, 2, heads));
        }
        if (rng.nextInt(2) == 0) {
            add(out, Items.PUMPKIN_SEEDS, per(rng, 2, 2, heads));
        }
        add(out, Items.SUGAR_CANE, per(rng, 2, 2, heads));
        add(out, Items.BONE_MEAL, per(rng, 4, 5, heads));

        // Timber. The Glade's wood is finite and the maze has none.
        add(out, Items.OAK_SAPLING, per(rng, 2, 3, heads));
        if (rng.nextInt(3) == 0) {
            add(out, Items.DARK_OAK_SAPLING, per(rng, 1, 2, heads));
        }

        // Metal and stone. The floor does not break, so there is no mining here.
        add(out, Items.IRON_INGOT, per(rng, 3, 4, heads));
        add(out, Items.COAL, per(rng, 6, 6, heads));
        add(out, Items.COBBLESTONE, per(rng, 12, 12, heads));
        if (day >= 2 && rng.nextInt(3) == 0) {
            add(out, Items.DIAMOND, per(rng, 1, 2, heads));
        }
        if (rng.nextInt(4) == 0) {
            add(out, Items.GOLD_INGOT, per(rng, 2, 3, heads));
        }

        // Off animals, of which there are none.
        add(out, Items.STRING, per(rng, 4, 5, heads));
        add(out, Items.LEATHER, per(rng, 2, 3, heads));
        add(out, Items.FEATHER, per(rng, 3, 4, heads));
        add(out, Items.ARROW, per(rng, 8, 9, heads));
        if (rng.nextInt(3) == 0) {
            add(out, Items.WHITE_WOOL, per(rng, 3, 4, heads));
        }

        // The odd tool, because a Glade with no bucket cannot move water and a
        // Glade with no shears cannot take wool off anything.
        if (day == 0 || rng.nextInt(5) == 0) {
            add(out, Items.BUCKET, 1);
        }
        if (day == 0 || rng.nextInt(6) == 0) {
            add(out, Items.SHEARS, 1);
        }
        if (day == 0 || rng.nextInt(6) == 0) {
            add(out, Items.FLINT_AND_STEEL, 1);
        }
        add(out, Items.BREAD, per(rng, 3, 4, heads));
        return out;
    }

    private static List<ItemStack> roleCrate(String job, RandomSource rng, int takers, int day) {
        List<ItemStack> out = new ArrayList<>();
        switch (job) {
            case MazeJobs.RUNNER -> {
                // Everything a Runner burns through: food, light, and something
                // to write with. Leather rather than iron, because a Runner who
                // armours up stops being able to outrun anything.
                add(out, Items.COOKED_BEEF, per(rng, 4, 4, takers));
                add(out, Items.TORCH, per(rng, 8, 8, takers));
                add(out, Items.OAK_SIGN, per(rng, 3, 4, takers));
                add(out, Items.SUGAR, per(rng, 2, 3, takers));
                if (day == 0 || rng.nextInt(4) == 0) {
                    out.add(new ItemStack(Items.LEATHER_BOOTS));
                }
                if (rng.nextInt(5) == 0) {
                    out.add(new ItemStack(Items.GOLDEN_APPLE));
                }
            }
            case MazeJobs.BUILDER -> {
                add(out, Items.IRON_INGOT, per(rng, 4, 4, takers));
                add(out, Items.COAL, per(rng, 6, 6, takers));
                add(out, Items.OAK_PLANKS, per(rng, 12, 12, takers));
                add(out, Items.STICK, per(rng, 8, 8, takers));
                add(out, Items.COBBLESTONE, per(rng, 16, 16, takers));
                // Colour, which is the Builder's other job entirely.
                add(out, Items.WHITE_WOOL, per(rng, 4, 4, takers));
                add(out, Items.RED_DYE, per(rng, 1, 3, takers));
                add(out, Items.BLUE_DYE, per(rng, 1, 3, takers));
                add(out, Items.YELLOW_DYE, per(rng, 1, 3, takers));
                if (day >= 3 && rng.nextInt(3) == 0) {
                    add(out, Items.DIAMOND, per(rng, 1, 2, takers));
                }
            }
            case MazeJobs.MEDJACK -> {
                add(out, Items.GOLDEN_APPLE, per(rng, 1, 2, takers));
                add(out, Items.GLASS_BOTTLE, per(rng, 2, 3, takers));
                add(out, Items.SUGAR, per(rng, 3, 3, takers));
                add(out, Items.STRING, per(rng, 4, 4, takers));
                add(out, Items.PAPER, per(rng, 4, 4, takers));
                add(out, Items.COOKED_BEEF, per(rng, 3, 3, takers));
                // The one crate that can carry a cure, and rarely.
                if (day > 0 && rng.nextInt(3) == 0) {
                    out.add(MazeSerum.create());
                }
            }
            case MazeJobs.TRACKHOE -> {
                add(out, Items.WHEAT_SEEDS, per(rng, 8, 8, takers));
                add(out, Items.CARROT, per(rng, 4, 4, takers));
                add(out, Items.POTATO, per(rng, 4, 4, takers));
                add(out, Items.BEETROOT_SEEDS, per(rng, 4, 4, takers));
                add(out, Items.BONE_MEAL, per(rng, 8, 8, takers));
                add(out, Items.OAK_SAPLING, per(rng, 3, 3, takers));
                if (day == 0) {
                    out.add(new ItemStack(Items.IRON_HOE));
                    out.add(new ItemStack(Items.WATER_BUCKET));
                }
            }
            default -> {
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Putting it in
    // ------------------------------------------------------------------

    /**
     * Places a crate and fills it.
     *
     * <p>Tops up rather than replacing. Somebody's spare iron left in yesterday's
     * crate is theirs, and a delivery system that quietly eats what you stored in
     * it is a delivery system nobody stores anything in. If a crate is genuinely
     * full the surplus is simply not delivered - which is a signal, not a bug.
     */
    private static void stock(ServerLevel level, BlockPos at, String name,
                              net.minecraft.world.level.block.state.BlockState pad,
                              List<ItemStack> goods) {
        if (!level.getBlockState(at).is(Blocks.CHEST)) {
            level.setBlock(at, Blocks.CHEST.defaultBlockState(), 2);
        }
        // A coloured slab under each crate rather than a name on it. Naming a
        // container from code is not something 1.21.1 exposes, and a floor you
        // can read at a glance is better than a tooltip anyway - you know whose
        // crate you are standing over before you open it.
        level.setBlock(at.below(), pad, 2);
        BlockEntity be = level.getBlockEntity(at);
        if (!(be instanceof Container chest)) {
            return;
        }
        for (ItemStack stack : goods) {
            if (stack.isEmpty()) {
                continue;
            }
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).isEmpty()) {
                    chest.setItem(i, stack);
                    break;
                }
            }
        }
        chest.setChanged();
    }
}
