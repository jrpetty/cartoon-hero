package com.jrpetty.aztecabyss.round;

import com.jrpetty.aztecabyss.worldgen.ArenaMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * The drops - the thing that makes a round turn.
 *
 * <p>Everything else in the Outpost is earned steadily: points tick up, boards
 * go back on, the Crucible costs what it costs. Power-ups are the opposite, and
 * that is their whole job. They are the moment a losing round becomes a winnable
 * one, and they arrive without warning.
 *
 * <p><b>Carpenter</b> is the one that matters most here and the reason the set
 * exists at all. Every other drop in the genre is a damage or economy button;
 * Carpenter re-boards every window on the map at once, which is a rescue that
 * only means anything because this map is built on boards. On a run where three
 * windows are open and the fourth is going, it is worth more than any weapon.
 *
 * <p>They fall rarely, they glow, and they rot after half a minute - so seeing
 * one is a decision about whether you can afford to cross the room for it.
 */
public final class OutpostPowerUps {

    /** Roughly one kill in this many leaves something behind. */
    private static final int DROP_CHANCE = 60;
    /** How long a drop lies on the floor before it rots. */
    private static final int LIFETIME_TICKS = 600;
    /** How long the timed drops run for. */
    private static final int DURATION_TICKS = 600;
    /** How close you have to be to take one. */
    private static final double PICKUP_RANGE = 2.0;

    private static final String TAG = "aztecabyss_powerup";

    public enum Kind {
        AEGIS("§6§lAEGIS", "§eEveryone back on their feet", Items.SHIELD),
        DOUBLE_POINTS("§b§lDOUBLE POINTS", "§bTwice the take", Items.GOLD_INGOT),
        INSTA_KILL("§c§lINSTA-KILL", "§cOne hit is enough", Items.BLAZE_POWDER),
        PURGE("§d§lPURGE", "§dThe room empties", Items.TNT);

        final String title;
        final String blurb;
        final net.minecraft.world.item.Item icon;

        Kind(String title, String blurb, net.minecraft.world.item.Item icon) {
            this.title = title;
            this.blurb = blurb;
            this.icon = icon;
        }
    }

    /** Game-time ticks the timed drops run until. */
    private static long doublePointsUntil = 0L;
    private static long instaKillUntil = 0L;

    private OutpostPowerUps() {
    }

    public static void reset() {
        doublePointsUntil = 0L;
        instaKillUntil = 0L;
    }

    public static boolean doublePoints(ServerLevel level) {
        return level.getGameTime() < doublePointsUntil;
    }

    public static boolean instaKill(ServerLevel level) {
        return level.getGameTime() < instaKillUntil;
    }

    /** A short readout for the round bar, so an active drop is never a mystery. */
    public static String hudFragment(ServerLevel level) {
        StringBuilder sb = new StringBuilder();
        if (doublePoints(level)) {
            sb.append(" §8| §b2x");
        }
        if (instaKill(level)) {
            sb.append(" §8| §cINSTA");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Dropping
    // ------------------------------------------------------------------

    /** Rolls a drop from a dead wave mob. */
    public static void maybeDrop(ServerLevel level, Mob mob, RandomSource rng) {
        if (!RoundManager.game().getMap().hasEconomy() || rng.nextInt(DROP_CHANCE) != 0) {
            return;
        }
        Kind kind = Kind.values()[rng.nextInt(Kind.values().length)];
        ItemStack stack = new ItemStack(kind.icon);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, kind.name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(kind.title));

        ItemEntity drop = new ItemEntity(level, mob.getX(), mob.getY() + 0.6, mob.getZ(), stack);
        // Never picked up by walking over it - taking one is handled by the sweep,
        // so a full inventory can never stop you claiming a rescue.
        drop.setPickUpDelay(Short.MAX_VALUE);
        drop.setUnlimitedLifetime();
        drop.setGlowingTag(true);
        level.addFreshEntity(drop);
        drop.getPersistentData().putLong("aztecabyss_powerup_born", level.getGameTime());

        level.playSound(null, mob.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.2F, 1.6F);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal("§7Something dropped. " + kind.title), true);
        }
    }

    // ------------------------------------------------------------------
    // Collecting
    // ------------------------------------------------------------------

    /**
     * Sweeps for lying drops: hands out any a player is stood on, and rots the
     * ones nobody reached. Rides the round loop's existing cadence.
     */
    public static void tick(ServerLevel level, List<ServerPlayer> present) {
        ArenaMap map = RoundManager.game().getMap();
        if (!map.hasEconomy()) {
            return;
        }
        long now = level.getGameTime();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, map.bounds(),
                e -> kindOf(e.getItem()) != null)) {
            if (now - item.getPersistentData().getLong("aztecabyss_powerup_born") > LIFETIME_TICKS) {
                level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY(), item.getZ(),
                        6, 0.2, 0.2, 0.2, 0.01);
                item.discard();
                continue;
            }
            level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.3, item.getZ(),
                    2, 0.2, 0.2, 0.2, 0.01);
            for (ServerPlayer p : present) {
                if (p.distanceToSqr(item) <= PICKUP_RANGE * PICKUP_RANGE) {
                    collect(level, p, kindOf(item.getItem()), present);
                    item.discard();
                    break;
                }
            }
        }
    }

    /**
     * Sweeps every uncollected drop off the floor.
     *
     * <p>Drops are given an unlimited lifetime so the rot timer above is the only
     * thing that removes them - which means one lying unclaimed when a run ends
     * would still be lying there when the next one starts, and the first player
     * past it gets an Insta-Kill on round one for nothing. Called when the session
     * tears down.
     */
    public static void clearDrops(ServerLevel level, ArenaMap map) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, map.bounds(),
                e -> kindOf(e.getItem()) != null)) {
            item.discard();
        }
    }

    private static Kind kindOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String name = data.copyTag().getString(TAG);
        if (name.isEmpty()) {
            return null;
        }
        try {
            return Kind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Drops are shared: one player picks it up, everybody gets it. */
    private static void collect(ServerLevel level, ServerPlayer taker, Kind kind, List<ServerPlayer> present) {
        if (kind == null) {
            return;
        }
        long now = level.getGameTime();
        switch (kind) {
            case AEGIS -> aegis(level, present);
            case DOUBLE_POINTS -> doublePointsUntil = now + DURATION_TICKS;
            case INSTA_KILL -> instaKillUntil = now + DURATION_TICKS;
            case PURGE -> purge(level, present);
        }
        for (ServerPlayer p : present) {
            p.displayClientMessage(Component.literal(kind.title + " §r§7— " + kind.blurb), false);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 1.0F, kind == Kind.AEGIS ? 0.8F : 1.4F);
        }
    }

    /**
     * Aegis: everyone goes back to full and takes half damage for a while.
     *
     * <p>This is the slot Carpenter used to hold. Carpenter re-boarded every
     * window at once, which was the best drop in the set right up until the
     * windows became open breaches and there was nothing left to nail shut.
     *
     * <p>The slot still needs to do the same <em>job</em>, though: be the drop
     * that turns a round you are losing rather than one you are already winning.
     * On an open map the way you lose is being worn down in the middle of the
     * floor with no wall to put your back to, so the rescue is health - given to
     * the whole squad at once, wherever they are stood.
     */
    private static void aegis(ServerLevel level, List<ServerPlayer> present) {
        for (ServerPlayer p : present) {
            p.heal(p.getMaxHealth());
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 300, 1, false, true));
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(),
                    30, 0.5, 0.8, 0.5, 0.03);
        }
        BlockPos any = present.isEmpty() ? BlockPos.ZERO : present.get(0).blockPosition();
        level.playSound(null, any, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.6F, 0.7F);
    }

    /** Purge: the room empties, and it pays as though you had earned it. */
    private static void purge(ServerLevel level, List<ServerPlayer> present) {
        ArenaMap map = RoundManager.game().getMap();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, map.bounds(),
                m -> m.getPersistentData().getBoolean("aztecabyss_wave_mob")
                        && !m.getPersistentData().getBoolean("aztecabyss_boss"));
        for (Mob m : mobs) {
            level.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.5, m.getZ(),
                    6, 0.3, 0.5, 0.3, 0.02);
            m.hurt(level.damageSources().magic(), Float.MAX_VALUE);
        }
        for (ServerPlayer p : present) {
            OutpostEconomy.award(p, mobs.size() * 10);
        }
    }
}
