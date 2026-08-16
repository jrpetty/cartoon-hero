package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * The drops that turn a round.
 *
 * <p>The last pillar of the genre the engine was missing. Everything else in a
 * run is earned at a steady rate - points tick up, rounds get harder on a curve,
 * the shop costs what it costs. Power-ups are the one thing that arrives without
 * warning and changes the next thirty seconds, and a survival mode without them
 * is a flat line with no shape to it.
 *
 * <p>Whether they drop at all is the map's decision: a ruleset sets the chance and
 * zero switches them off entirely, so an author who wants a grim, unassisted map
 * gets one.
 *
 * <p>Collection is by proximity rather than by picking the item up. A full
 * inventory must never be the reason a rescue was missed, and in this mode your
 * inventory is usually full of things you just bought.
 */
public final class EnginePowerUps {

    private static final String TAG = "aztecabyss_engine_powerup";
    /** How long a drop lies there before it rots. */
    private static final int LIFETIME = 600;
    /** How long the timed ones last. */
    private static final int DURATION = 600;
    private static final double PICKUP = 2.5;

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

    private static long doubleUntil = 0L;
    private static long instaUntil = 0L;

    private EnginePowerUps() {
    }

    public static void reset() {
        doubleUntil = 0L;
        instaUntil = 0L;
    }

    public static boolean doublePoints(ServerLevel level) {
        return level.getGameTime() < doubleUntil;
    }

    public static boolean instaKill(ServerLevel level) {
        return level.getGameTime() < instaUntil;
    }

    /** Rolls a drop where something died. */
    public static void maybeDrop(ServerLevel level, Mob mob, int chance, RandomSource rng) {
        if (chance <= 0 || rng.nextInt(chance) != 0) {
            return;
        }
        Kind kind = Kind.values()[rng.nextInt(Kind.values().length)];
        ItemStack stack = new ItemStack(kind.icon);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, kind.name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(kind.title));

        ItemEntity drop = new ItemEntity(level, mob.getX(), mob.getY() + 0.6, mob.getZ(), stack);
        // Never picked up by walking over it in the ordinary way - the sweep below
        // handles that - so a full inventory can never block a rescue.
        drop.setPickUpDelay(Short.MAX_VALUE);
        drop.setUnlimitedLifetime();
        drop.setGlowingTag(true);
        drop.getPersistentData().putLong(TAG + "_born", level.getGameTime());
        level.addFreshEntity(drop);
        level.playSound(null, mob.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.1F, 1.6F);
    }

    /** Sweeps for drops: hands out any a player reaches, rots the rest. */
    public static void tick(ServerLevel level, List<ServerPlayer> present,
                            net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        long now = level.getGameTime();
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                box.minX(), box.minY(), box.minZ(),
                box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
                e -> kindOf(e.getItem()) != null)) {
            if (now - item.getPersistentData().getLong(TAG + "_born") > LIFETIME) {
                level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY(), item.getZ(),
                        6, 0.2, 0.2, 0.2, 0.01);
                item.discard();
                continue;
            }
            level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.3, item.getZ(),
                    2, 0.2, 0.2, 0.2, 0.01);
            for (ServerPlayer p : present) {
                if (p.distanceToSqr(item) <= PICKUP * PICKUP) {
                    collect(level, kindOf(item.getItem()), present);
                    item.discard();
                    break;
                }
            }
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

    /** One player picks it up, everybody gets it. */
    private static void collect(ServerLevel level, Kind kind, List<ServerPlayer> present) {
        if (kind == null) {
            return;
        }
        // Which power-up landed, so a map can react to the thing itself rather
        // than to its side effects. The name is the subject, so
        // {"when":{"subject":"insta_kill"}} reads the way it sounds.
        EngineArena arena = EngineArena.active();
        if (arena != null) {
            Script.fire(arena, level, arena.rulesetId(), "powerup_taken",
                    present.isEmpty() ? null : present.get(0), null,
                    kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        long now = level.getGameTime();
        switch (kind) {
            case AEGIS -> {
                for (ServerPlayer p : present) {
                    p.heal(p.getMaxHealth());
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                            300, 1, false, true));
                }
            }
            case DOUBLE_POINTS -> doubleUntil = now + DURATION;
            case INSTA_KILL -> instaUntil = now + DURATION;
            case PURGE -> {
                if (arena != null) {
                    arena.purge();
                }
            }
        }
        for (ServerPlayer p : present) {
            p.displayClientMessage(Component.literal(kind.title + " §r§7— " + kind.blurb), false);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 1.0F, kind == Kind.AEGIS ? 0.8F : 1.4F);
        }
    }

    /** Clears any drop still lying about. Called when a run ends. */
    public static void clearDrops(ServerLevel level,
                                  net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                box.minX(), box.minY(), box.minZ(),
                box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0);
        // Drops are given unlimited lifetime, so one left when a run ends would
        // still be lying there for the next - a free Insta-Kill on round one.
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
                e -> kindOf(e.getItem()) != null)) {
            item.discard();
        }
    }
}
