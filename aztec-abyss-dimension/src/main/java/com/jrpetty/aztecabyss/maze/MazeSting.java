package com.jrpetty.aztecabyss.maze;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Sting, and the Changing that follows it.
 *
 * <p>A Griever hit is not automatically a death sentence. It takes four before
 * the venom takes hold - which is the whole point of the number. One sting is a
 * mistake you can walk away from, two is a warning, three is a decision about
 * whether to keep going, and the fourth is the one that gets you. It turns every
 * encounter into a running tally rather than a coin flip, and it means a runner
 * who has been caught three times is playing a visibly different game from one
 * who has not been touched.
 *
 * <p>Once it takes, the venom kills. Not instantly - it gives you long enough to
 * run for the Glade or for a serum, and not long enough to do both.
 */
public final class MazeSting {

    /** Stings survived before the venom takes. */
    public static final int THRESHOLD = 4;
    /**
     * How long you have after the fourth sting before it starts killing you.
     *
     * <p>Ninety seconds is not how long you survive - it is how long you have to
     * do something about it. The venom does no damage at all during this window;
     * it takes your sight, your footing and your strength, and it counts down
     * where you can see it. Long enough to run for the Glade or for a serum, and
     * not long enough to do both.
     */
    private static final int CHANGING_SECONDS = 90;
    /** Damage a second once it turns lethal, and it does not stop. */
    private static final float DEATH_TICK_DAMAGE = 2.0F;

    private static final Map<UUID, Integer> STINGS = new HashMap<>();
    private static final Set<UUID> INFECTED = new HashSet<>();
    /** Seconds left before the Changing turns lethal. Absent once it has. */
    private static final Map<UUID, Integer> COUNTDOWN = new HashMap<>();

    private MazeSting() {
    }

    public static void clear(UUID id) {
        STINGS.remove(id);
        INFECTED.remove(id);
        COUNTDOWN.remove(id);
    }

    public static void clearAll() {
        STINGS.clear();
        INFECTED.clear();
        COUNTDOWN.clear();
    }

    public static int stings(UUID id) {
        return STINGS.getOrDefault(id, 0);
    }

    /** How many this particular body can take. */
    public static int thresholdFor(ServerLevel level, ServerPlayer player) {
        return THRESHOLD + MazeSkills.rankOf(level, player.getUUID(), "antivenom");
    }

    public static boolean isInfected(UUID id) {
        return INFECTED.contains(id);
    }

    /**
     * Records a hit from a Griever. Returns true if this was the one that turned.
     *
     * <p>Each sting before the fourth is announced, because the count only works
     * as a mechanic if the runner is keeping it in their head.
     */
    public static boolean onStung(ServerLevel level, ServerPlayer player) {
        if (INFECTED.contains(player.getUUID())) {
            return false; // already turning; further stings change nothing
        }
        int n = STINGS.merge(player.getUUID(), 1, Integer::sum);
        // Antivenom moves the number. It is the one skill in the game that
        // changes how much punishment a body takes, and it is deliberately on
        // the job whose entire subject is punishment to bodies.
        int threshold = thresholdFor(level, player);
        if (n < threshold) {
            int left = threshold - n;
            player.displayClientMessage(Component.literal(
                    "§c✖ Stung. §7" + n + "§8/§7" + threshold
                            + " §8— " + left + " more and it takes."), true);
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ANGRY,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
            return false;
        }
        infect(level, player);
        return true;
    }

    /**
     * The Changing begins - the clock, not the dying.
     *
     * <p>Deliberately no Wither here. Killing you across the same ninety seconds
     * you are being told to run somewhere makes the run pointless: you arrive at
     * the Glade dead, having done the right thing. The venom cripples first and
     * kills afterwards, so the ninety seconds are a real chance and the deadline
     * is a real deadline.
     */
    private static void infect(ServerLevel level, ServerPlayer player) {
        INFECTED.add(player.getUUID());
        COUNTDOWN.put(player.getUUID(), CHANGING_SECONDS);
        int ticks = CHANGING_SECONDS * 20;
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, true));

        player.displayClientMessage(Component.literal("§4§lTHE CHANGING"), false);
        player.displayClientMessage(Component.literal(
                "§cThe fourth one took. §7Serum, or the Glade — you have "
                        + CHANGING_SECONDS + " seconds before it starts killing you."), false);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR,
                SoundSource.PLAYERS, 1.4F, 0.4F);
    }

    /**
     * Runs the Changing. Called once a second from the maze runtime.
     *
     * <p>Two phases. While the countdown lasts nothing here hurts anyone - it
     * announces, and it gets louder as the number gets smaller. When it reaches
     * zero the venom turns and does not stop, which is what makes the ninety
     * seconds mean something rather than being a status effect with a timer on it.
     */
    public static void tick(ServerLevel level) {
        if (INFECTED.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            if (!INFECTED.contains(id)) {
                continue;
            }
            Integer left = COUNTDOWN.get(id);
            if (left == null) {
                // Past the deadline: it is killing them now.
                player.hurt(level.damageSources().wither(), DEATH_TICK_DAMAGE);
                player.displayClientMessage(Component.literal(
                        "§4§lYou are turning."), true);
                continue;
            }
            int now = left - 1;
            if (now <= 0) {
                COUNTDOWN.remove(id);
                player.addEffect(new MobEffectInstance(
                        MobEffects.WITHER, Integer.MAX_VALUE, 1, false, true));
                player.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, Integer.MAX_VALUE, 0, false, true));
                player.displayClientMessage(Component.literal("§4§lIT HAS TAKEN"), false);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR,
                        SoundSource.PLAYERS, 1.6F, 0.35F);
                continue;
            }
            COUNTDOWN.put(id, now);
            player.displayClientMessage(Component.literal(
                    "§4§lTHE CHANGING §8— §c" + now + "s"), true);
            // The last ten seconds are a ramp, not a reminder. The heartbeat
            // climbs in pitch second by second, something structural cracks on
            // the off-beats, and in the last five the victim's own screen
            // closes in - Darkness, pulsed, so the world is going out for them
            // specifically while everyone nearby hears exactly why.
            if (now <= 10) {
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.PLAYERS, 1.2F, 0.45F + (10 - now) * 0.05F);
                if (now % 2 == 0) {
                    level.playSound(null, player.blockPosition(), SoundEvents.TURTLE_EGG_CRACK,
                            SoundSource.PLAYERS, 0.9F, 0.5F);
                }
                if (now <= 5) {
                    player.addEffect(new MobEffectInstance(
                            MobEffects.DARKNESS, 35, 0, false, false));
                }
            }
        }
    }

    /**
     * Buys someone more time.
     *
     * <p>The Changing was a private catastrophe: the victim watched a number go
     * down and everybody else watched them. This is the seam a Med-jack works
     * through - it does not cure anything, it moves the deadline, which is the
     * difference between "you are dead" and "you are dead unless somebody gets
     * to you". It deliberately cannot save a runner who has already turned,
     * because a deadline you can extend after it has passed is not a deadline.
     *
     * @return true if there was a countdown left to extend
     */
    public static boolean extend(ServerLevel level, ServerPlayer player, int seconds) {
        UUID id = player.getUUID();
        Integer left = COUNTDOWN.get(id);
        if (!INFECTED.contains(id) || left == null) {
            return false;
        }
        int now = left + seconds;
        COUNTDOWN.put(id, now);
        int ticks = now * 20;
        // The crippling effects were pinned to the original window, so without
        // this a treated runner walks away cured of everything but the dying.
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, true));
        player.displayClientMessage(Component.literal(
                "§a✚ Treated. §7The Changing is held off — §f" + now + "s§7 left."), false);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.8F, 1.6F);
        return true;
    }

    /**
     * Seconds left on somebody's change, or -1 if they are not changing.
     *
     * <p>For the HUD packet. The countdown was already public in effect - it is
     * printed at the player every second - it just had no accessor a packet
     * could read.
     */
    public static int changingSeconds(UUID id) {
        if (!INFECTED.contains(id)) {
            return -1;
        }
        Integer left = COUNTDOWN.get(id);
        return left == null ? 0 : Math.max(0, left);
    }

    /** True if the venom has taken and the deadline has not yet passed. */
    public static boolean isChanging(UUID id) {
        return INFECTED.contains(id) && COUNTDOWN.containsKey(id);
    }

    /**
     * Grief Serum: stops the Changing and wipes the tally.
     *
     * @return true if there was something to cure
     */
    public static boolean cure(ServerLevel level, ServerPlayer player) {
        boolean was = INFECTED.remove(player.getUUID()) || STINGS.containsKey(player.getUUID());
        STINGS.remove(player.getUUID());
        COUNTDOWN.remove(player.getUUID());
        if (!was) {
            return false;
        }
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.CONFUSION);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.displayClientMessage(Component.literal(
                "§a✚ The serum takes hold. §7The tally is clear."), false);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.9F, 1.4F);
        return true;
    }

    /**
     * A short readout for the status bar.
     *
     * <p>Takes the player rather than the id so it can print the denominator
     * again. Making the threshold per-player for Antivenom quietly cost the bar
     * its "/4", which left a runner reading "Stung 3" with no idea whether that
     * was survivable - the exact number the whole mechanic is built on.
     */
    public static String hudFragment(ServerLevel level, ServerPlayer player) {
        UUID id = player.getUUID();
        if (INFECTED.contains(id)) {
            Integer left = COUNTDOWN.get(id);
            return left == null ? " §8| §4§lTURNING" : " §8| §4CHANGING §c" + left + "s";
        }
        int n = stings(id);
        return n == 0 ? "" : " §8| §cStung " + n + "§8/§c" + thresholdFor(level, player);
    }
}
