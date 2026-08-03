package com.jrpetty.aztecabyss.maze;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Coming up in the Box: choosing what you are, before you are allowed to be
 * anything.
 *
 * <p>Arrival was the weakest moment in the whole loop. You appeared in the middle
 * of the Glade with an empty inventory and no instructions, and the game's
 * position was that you might like to read a sign at some point. Jobs existed,
 * were the spine of everything, and were <em>optional</em> - so the default
 * experience was a person standing in a field owning nothing, with four
 * unexplained buildings and a maze that kills you.
 *
 * <p>So the Box does not let go of you until you have said what you are. Nobody
 * moves until they choose. That is not a tutorial gate for its own sake: it is
 * the one moment when the whole group is standing in the same place with the same
 * question in front of them, which is exactly when a party should be deciding who
 * is the Med-jack.
 *
 * <h2>And then you are given something</h2>
 *
 * <p>Materials are genuinely hard to come by in here - no soil, no animals, an
 * unbreakable floor - so arriving empty is not a challenge, it is an hour of
 * nothing. The kits are deliberately on the generous side of sensible: enough to
 * be the thing you chose <em>today</em>, not enough to stop needing the Box, the
 * field or the caches tomorrow. A Track-hoe can start farming in the first
 * minute; they still cannot feed anybody without working the field.
 *
 * <h2>One kit per game</h2>
 *
 * <p>Changing trade later is free and gives you nothing, because a kit per switch
 * is a vending machine. The Box's role crates cover anybody who changes their
 * mind, one dawn later - which is the right amount of friction: a real cost,
 * measured in a day rather than in a wasted week.
 */
public final class MazeInduction {

    private MazeInduction() {
    }

    /**
     * Called once a second for everybody in the maze.
     *
     * <p>Handles both halves: holding anybody who has not chosen, and kitting
     * anybody who has and has not been kitted this game. Kitting on arrival
     * rather than at the moment of choosing means it works identically whether
     * you picked your trade standing in the Box or typed it from the overworld
     * an hour ago.
     */
    public static void tick(ServerLevel level, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            return; // an operator fixing the map is not a Greenie
        }
        MazeJobs jobs = MazeJobs.get(level);
        String job = jobs.jobOf(p.getUUID());
        if (job == null) {
            hold(level, p);
            return;
        }
        int session = MazeClock.get(level).session();
        if (jobs.kitted(p.getUUID(), session)) {
            return;
        }
        jobs.markKitted(p.getUUID(), session);
        release(p);
        outfit(p, job);
        announce(level, p, job);
    }

    /**
     * Nobody moves until they choose.
     *
     * <p>Slowness at maximum stops the walking and negative jump stops the
     * hopping over it, which between them is the whole of a player's ability to
     * leave. Refreshed every second at two seconds' duration so it lapses on its
     * own the instant they choose, with nothing to remember to undo.
     */
    private static void hold(ServerLevel level, ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 250, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 200, false, false));
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);

        p.displayClientMessage(Component.literal(
                "§e§lCHOOSE A TRADE §8— §b/maze job runner §8· §6builder §8· §amedjack §8· §2trackhoe"), true);
        // The full pitch every five seconds. Once would be missed by anybody
        // still loading in; every second would be unreadable.
        if (level.getGameTime() % 100L == 0L) {
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("§e§lWHAT ARE YOU?")));
            p.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("§7The Box does not let go until you say")));
            for (String job : MazeJobs.ALL) {
                p.displayClientMessage(Component.literal(
                        "  " + MazeJobs.display(job) + " §8/maze job " + job
                                + "  §7" + MazeJobs.blurb(job).substring(2)), false);
            }
            level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND,
                    SoundSource.BLOCKS, 0.7F, 0.8F);
        }
    }

    private static void release(ServerPlayer p) {
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(MobEffects.JUMP);
    }

    private static void announce(ServerLevel level, ServerPlayer p, String job) {
        p.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal(MazeJobs.display(job).toUpperCase(java.util.Locale.ROOT))));
        p.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("§7They gave you what you will need")));
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        for (ServerPlayer other : level.players()) {
            if (other != p) {
                other.displayClientMessage(Component.literal(
                        "§7" + p.getGameProfile().getName() + " came up as a "
                                + MazeJobs.display(job) + "§7."), false);
            }
        }
    }

    // ------------------------------------------------------------------
    // The kits
    // ------------------------------------------------------------------

    private static void outfit(ServerPlayer p, String job) {
        for (ItemStack stack : kit(job)) {
            p.getInventory().placeItemBackInInventory(stack);
        }
    }

    private static void add(List<ItemStack> out, Item item, int count) {
        out.add(new ItemStack(item, count));
    }

    /**
     * What you come up with.
     *
     * <p>Every kit carries the same three things - food, light and something to
     * swing - because those are survival rather than trade, and a Track-hoe who
     * cannot defend themselves at night is not a farmer, they are a casualty.
     * Everything after that is the job.
     */
    public static List<ItemStack> kit(String job) {
        List<ItemStack> out = new ArrayList<>();
        // The common three.
        add(out, Items.BREAD, 12);
        add(out, Items.TORCH, 16);

        switch (job) {
            case MazeJobs.RUNNER -> {
                // Light on purpose. A Runner in iron is a Runner who has stopped
                // being able to outrun the thing chasing them, so the armour is
                // leather and the compensation is a real weapon and real food.
                out.add(new ItemStack(Items.IRON_SWORD));
                out.add(new ItemStack(Items.LEATHER_HELMET));
                out.add(new ItemStack(Items.LEATHER_BOOTS));
                add(out, Items.COOKED_BEEF, 8);
                add(out, Items.OAK_SIGN, 8);
                add(out, Items.GOLDEN_APPLE, 1);
                add(out, Items.TORCH, 16); // twice the light; they go furthest
            }
            case MazeJobs.BUILDER -> {
                // Enough to actually build something on day one rather than
                // spend it collecting sticks. The axe is the weapon and the tool.
                out.add(new ItemStack(Items.IRON_AXE));
                out.add(new ItemStack(Items.LEATHER_CHESTPLATE));
                out.add(new ItemStack(Items.CRAFTING_TABLE));
                add(out, Items.OAK_PLANKS, 32);
                add(out, Items.STICK, 16);
                add(out, Items.COBBLESTONE, 32);
                add(out, Items.IRON_INGOT, 8);
                add(out, Items.COAL, 12);
                add(out, Items.WHITE_WOOL, 8);
                add(out, Items.WHITE_CARPET, 16);
            }
            case MazeJobs.MEDJACK -> {
                // Four bandages and the makings of a dozen more. The point is
                // that a Med-jack can be useful in the first five minutes, not
                // after a week of accumulating string.
                out.add(new ItemStack(Items.STONE_SWORD));
                out.add(new ItemStack(Items.LEATHER_CHESTPLATE));
                for (int i = 0; i < 4; i++) {
                    out.add(MazeBandage.create(0));
                }
                add(out, Items.STRING, 24);
                add(out, Items.PAPER, 12);
                add(out, Items.GOLDEN_APPLE, 2);
                add(out, Items.GLASS_BOTTLE, 4);
                add(out, Items.COOKED_BEEF, 4);
            }
            case MazeJobs.TRACKHOE -> {
                // A field's worth of seed and the two tools that make farmland
                // work. Everything else the Glade eats grows from this.
                out.add(new ItemStack(Items.IRON_HOE));
                out.add(new ItemStack(Items.WATER_BUCKET));
                out.add(new ItemStack(Items.STONE_SWORD));
                out.add(new ItemStack(Items.LEATHER_BOOTS));
                add(out, Items.WHEAT_SEEDS, 24);
                add(out, Items.CARROT, 12);
                add(out, Items.POTATO, 12);
                add(out, Items.BEETROOT_SEEDS, 12);
                add(out, Items.BONE_MEAL, 16);
                add(out, Items.OAK_SAPLING, 4);
            }
            default -> out.add(new ItemStack(Items.STONE_SWORD));
        }
        return out;
    }
}
