package com.jrpetty.aztecabyss.round;

import com.jrpetty.aztecabyss.worldgen.ArenaMap;
import com.jrpetty.aztecabyss.worldgen.OutpostBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Box: points in, a weapon out, and it will not stay where you found it.
 *
 * <p>The randomness is the advertised part and the least interesting one. What
 * the Box is actually for is <b>where it is</b>. After a handful of uses it
 * packs up and reappears somewhere else on the map - and because the Outpost's
 * rooms are sealed behind rubble, that relocation regularly puts it somewhere
 * nobody has dug out yet. It drags a squad into rooms they were avoiding, and
 * it turns "where is the Box" into the running conversation of a co-op run.
 *
 * <p>It is also the only source of variety in a shop that is otherwise a fixed
 * price list. The wall buys are what you can plan for; the Box is what you
 * gamble on when planning has not been enough.
 */
public final class MysteryBox {

    public static final int PRICE = 950;
    /** Uses before it moves on. Deliberately small - the moving is the point. */
    private static final int USES_BEFORE_MOVE = 5;

    /**
     * What can come out. Weighted by being listed more than once rather than by
     * a weight table, because the pool is small enough that a list reads better
     * than an algorithm.
     */
    private static final Item[] POOL = {
            Items.IRON_SWORD, Items.IRON_SWORD,
            Items.IRON_AXE,
            Items.DIAMOND_SWORD,
            Items.DIAMOND_AXE,
            Items.BOW, Items.BOW,
            Items.CROSSBOW,
            Items.SHIELD,
            Items.TRIDENT,
            Items.NETHERITE_SWORD,
            Items.IRON_CHESTPLATE,
            Items.DIAMOND_CHESTPLATE,
    };

    private static int site = 0;
    private static int usesHere = 0;

    private MysteryBox() {
    }

    public static BlockPos position() {
        BlockPos[] sites = OutpostBuilder.BOX_SITES;
        return sites[Math.floorMod(site, sites.length)];
    }

    /** Puts the Box back at its first site. Called when a run starts. */
    public static void reset(ServerLevel level) {
        clearBox(level, position());
        site = 0;
        usesHere = 0;
        buildBox(level, position());
    }

    public static boolean isBox(BlockPos pos) {
        BlockPos b = position();
        return Math.abs(pos.getX() - b.getX()) <= 1
                && Math.abs(pos.getZ() - b.getZ()) <= 1
                && Math.abs(pos.getY() - b.getY()) <= 1;
    }

    /**
     * Opens the Box for one player. Charges, hands out a weapon, and moves the
     * Box on once it has been used enough times.
     */
    public static void open(ServerLevel level, ServerPlayer player, RandomSource rng) {
        if (!OutpostEconomy.spend(player, PRICE, "The Box")) {
            return;
        }
        ItemStack prize = new ItemStack(POOL[rng.nextInt(POOL.length)]);
        prize.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(
                        java.util.List.of(Component.literal("§8Out of the Box. It stays here."))));
        if (!player.getInventory().add(prize)) {
            player.drop(prize, false);
        }

        BlockPos at = position();
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5,
                24, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.3F);
        player.displayClientMessage(Component.literal(
                "§d✦ " + prize.getItem().getDescription().getString()
                        + " §7— §e-" + PRICE), false);

        if (++usesHere >= USES_BEFORE_MOVE) {
            relocate(level, rng);
        }
    }

    /** Packs up and reappears elsewhere, and tells everyone where. */
    private static void relocate(ServerLevel level, RandomSource rng) {
        BlockPos old = position();
        clearBox(level, old);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, old.getX() + 0.5, old.getY() + 1.0, old.getZ() + 0.5,
                30, 0.5, 0.6, 0.5, 0.04);
        level.playSound(null, old, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.2F, 0.5F);

        int sites = OutpostBuilder.BOX_SITES.length;
        site = Math.floorMod(site + 1 + rng.nextInt(Math.max(1, sites - 1)), sites);
        usesHere = 0;
        buildBox(level, position());

        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§d§lTHE BOX HAS MOVED §r§7— it is in the §f"
                            + OutpostBuilder.boxSiteName(site) + "§7 now."), false);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 0.8F, 0.7F);
        }
    }

    /**
     * The Box: a crate chained to a plinth, lit purple from inside.
     *
     * <p>It has to be findable at a glance from the far side of a dark room,
     * because the entire point of the thing is that it keeps moving and people
     * keep having to look for it. Amethyst does that - it is the only purple
     * light in the building, so "the Box is in the cellar" is something you can
     * confirm from the stairs rather than by walking the whole floor.
     */
    public static void buildBox(ServerLevel level, BlockPos at) {
        // Plinth, banded in gold.
        level.setBlock(at.below(), Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            level.setBlock(at.below().relative(d), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
            // Chains lashing the crate down at each corner.
            level.setBlock(at.relative(d), Blocks.CHAIN.defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y), 2);
        }
        level.setBlock(at, Blocks.CHEST.defaultBlockState(), 2);
        // The glow: budding amethyst under a cluster, so it reads purple in the
        // dark and nothing else on the map does.
        level.setBlock(at.above(), Blocks.BUDDING_AMETHYST.defaultBlockState(), 2);
        level.setBlock(at.above(2), Blocks.AMETHYST_CLUSTER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, net.minecraft.core.Direction.DOWN), 2);
        level.setBlock(at.above(3), Blocks.CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y), 2);
    }

    /**
     * Strips the Box back off a site.
     *
     * <p>Deliberately leaves the plinth where it is. Clearing it too would punch
     * a hole in whatever floor the Box was standing on - and every site is on a
     * different material, so there is no single right block to put back. A spare
     * plinth reads as somewhere the Box has been, which is fine; a hole in the
     * cellar floor is not.
     */
    private static void clearBox(ServerLevel level, BlockPos at) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            level.setBlock(at.relative(d), Blocks.AIR.defaultBlockState(), 2);
        }
        for (int dy = 0; dy <= 3; dy++) {
            level.setBlock(at.above(dy), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** Only the Outpost has a Box. */
    public static boolean activeOn(ArenaMap map) {
        return map.hasEconomy();
    }
}
