package com.jrpetty.aztecabyss.round;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;

/**
 * The "Abyssal Nova" - a single-charge ability every player is handed the
 * moment they enter the Abyss. Right-clicking it unleashes a burst that deals
 * 60 hearts (armor-ignoring) to every enemy around the caster, then consumes
 * the charge. It only works inside the Abyss and is stripped from the inventory
 * the instant a player leaves, so it never escapes into the overworld.
 */
public final class AbyssAbility {

    private static final String TAG = "aztecabyss_nova";
    private static final double RADIUS = 8.0;
    private static final float DAMAGE = 120.0F; // 60 hearts

    private AbyssAbility() {
    }

    /** Builds a fresh Nova charge. */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.NETHER_STAR);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("§d§l✦ Abyssal Nova §r§7(right-click — 60❤ blast)"));
        return stack;
    }

    public static boolean is(ItemStack stack) {
        if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
            return false;
        }
        return stack.get(DataComponents.CUSTOM_DATA).copyTag().getBoolean(TAG);
    }

    /** Grants exactly one charge (no duplicates). */
    public static void give(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (is(inv.getItem(i))) {
                return;
            }
        }
        ItemStack nova = create();
        if (!inv.add(nova)) {
            player.drop(nova, false);
        }
    }

    /** Removes every Nova charge from a player's inventory (called when they leave the Abyss). */
    public static void strip(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (is(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** Detonates the nova: 60 hearts, armor-ignoring, to every enemy in range, credited to the caster. */
    public static void trigger(ServerLevel level, ServerPlayer player) {
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        AABB box = player.getBoundingBox().inflate(RADIUS);
        net.minecraft.world.damagesource.DamageSource src = level.damageSources().indirectMagic(player, player);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive())) {
            if (mob.distanceToSqr(px, py, pz) <= RADIUS * RADIUS) {
                mob.hurt(src, DAMAGE);
            }
        }

        // Spectacle.
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, px, py + 1.0, pz, 1, 0.0, 0.0, 0.0, 0.0);
        int points = 40;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0 * i / points;
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    px + Math.cos(a) * RADIUS * 0.5, py + 1.0, pz + Math.sin(a) * RADIUS * 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py + 1.0, pz, 60, RADIUS * 0.4, 0.6, RADIUS * 0.4, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3F, 0.7F);
    }
}
