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
        if (n < THRESHOLD) {
            int left = THRESHOLD - n;
            player.displayClientMessage(Component.literal(
                    "§c✖ Stung. §7" + n + "§8/§7" + THRESHOLD
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
            // The last ten seconds get a heartbeat, because a number on the action
            // bar is easy to stop looking at and a sound is not.
            if (now <= 10) {
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ANGRY,
                        SoundSource.PLAYERS, 0.7F, 0.5F);
            }
        }
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

    /** A short readout for the status bar. */
    public static String hudFragment(UUID id) {
        if (INFECTED.contains(id)) {
            Integer left = COUNTDOWN.get(id);
            return left == null ? " §8| §4§lTURNING" : " §8| §4CHANGING §c" + left + "s";
        }
        int n = stings(id);
        return n == 0 ? "" : " §8| §cStung " + n + "§8/§c" + THRESHOLD;
    }
}
