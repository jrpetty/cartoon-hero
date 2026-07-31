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

    /** The Box itself: a chest lashed under a lit frame you can spot in the dark. */
    public static void buildBox(ServerLevel level, BlockPos at) {
        level.setBlock(at.below(), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(at, Blocks.CHEST.defaultBlockState(), 2);
        level.setBlock(at.above(), Blocks.CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y), 2);
        level.setBlock(at.above(2), Blocks.SOUL_LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
    }

    private static void clearBox(ServerLevel level, BlockPos at) {
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(at.above(2), Blocks.AIR.defaultBlockState(), 2);
    }

    /** Only the Outpost has a Box. */
    public static boolean activeOn(ArenaMap map) {
        return map.hasEconomy();
    }
}
