package com.jrpetty.aztecabyss.maze;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
     * Where each unchosen player is being kept, by id.
     *
     * <p>Transient on purpose: a restart simply re-anchors on the next tick,
     * and a player who leaves the dimension is forgotten by the change hook.
     */
    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> HELD =
            new java.util.HashMap<>();

    /** How far a held player may drift before being stood back: a step and a half. */
    private static final double DRIFT_SQ = 1.5 * 1.5;

    /** Ticks between re-sends of the sheet while unchosen. Insurance, not nagging. */
    private static final int RESEND_EVERY = 60;

    /**
     * Somebody has just come up. Anchor them and put the decision in front of
     * them. Called from the dimension-change hook the moment they arrive, and
     * from {@link #hold} for anybody the hook never saw (a restart mid-choice).
     */
    public static void arrived(ServerLevel level, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()
                || MazeJobs.get(level).jobOf(p.getUUID()) != null) {
            return;
        }
        HELD.put(p.getUUID(), p.position());
        com.jrpetty.aztecabyss.network.ModNetworking.sendInduction(p);
    }

    /** They chose, or they left: nothing is holding them any more. */
    public static void forget(java.util.UUID id) {
        HELD.remove(id);
    }

    /**
     * Nobody moves until they choose - and nothing touches their camera.
     *
     * <p>This used to be Slowness at amplifier 250 and Jump Boost at 200,
     * refreshed every second. Vanilla scales the field of view with movement
     * speed, so a speed of nothing halved the FOV: the first thing the maze
     * did to every new player was zoom their camera in and hold it there for
     * as long as it took them to find a board they could not walk to. And no
     * screen ever opened - the decision was a chat line and a command.
     *
     * <p>Now the hold is the induction screen itself. A screen that cannot be
     * dismissed until a trade is chosen blocks every movement key by the
     * ordinary rules of having a screen open, with no effect on speed, jump
     * or view. The server's part is insurance: re-send the sheet every few
     * seconds in case the first copy was lost in the dimension change, and
     * stand anybody who has somehow drifted back on the spot they arrived -
     * silently, without an effect, so there is nothing to feel.
     */
    private static void hold(ServerLevel level, ServerPlayer p) {
        net.minecraft.world.phys.Vec3 anchor = HELD.get(p.getUUID());
        if (anchor == null) {
            arrived(level, p);
            return;
        }
        // Horizontal only. The arrival point may sit a block above the floor,
        // and a hold that measured height would catch the landing and stand a
        // falling player back in the air forever.
        double dx = p.getX() - anchor.x;
        double dz = p.getZ() - anchor.z;
        if (dx * dx + dz * dz > DRIFT_SQ) {
            p.teleportTo(level, anchor.x, p.getY(), anchor.z, java.util.Set.of(),
                    p.getYRot(), p.getXRot());
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
        if (level.getGameTime() % RESEND_EVERY == 0L) {
            com.jrpetty.aztecabyss.network.ModNetworking.sendInduction(p);
        }
        p.displayClientMessage(Component.literal(
                "§e§lCHOOSE A TRADE §8— §7the Box does not let go until you say"), true);
    }

    private static void release(ServerPlayer p) {
        HELD.remove(p.getUUID());
        // Anybody still carrying the old effect-based hold across an upgrade
        // is let go of properly; on a fresh world these are no-ops.
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
        // The Runner's chart cannot be given from kit(), which has no level to
        // create the map data in.
        if (MazeJobs.RUNNER.equals(job) && p.level() instanceof ServerLevel sl) {
            p.getInventory().placeItemBackInInventory(runnersChart(sl));
        }
    }

    /**
     * The Runner's chart: a real map, scaled so the whole maze fits on one
     * sheet. Vanilla does everything the job needs - it fills in only where
     * its holder has walked, draws walls and corridors in their own colours,
     * and carries the position arrow - which makes it fog-of-war that the
     * game engine maintains for free. Also sold on the catalogue, so losing
     * one is a cost rather than a dead end.
     */
    public static ItemStack runnersChart(ServerLevel level) {
        ItemStack chart = net.minecraft.world.item.MapItem.create(
                level, MazeData.SPAN / 2, MazeData.SPAN / 2, (byte) 3, true, true);
        chart.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("§bRunner's Chart"));
        return chart;
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
