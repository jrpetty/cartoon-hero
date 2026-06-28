package com.voxelia.mmo.event;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.MobMastery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Mob Mastery / Bestiary: killing a mob type accrues kills; reaching higher
 * tiers grants a small permanent damage + loot bonus against that type.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class MobMasteryEvents {
    private MobMasteryEvents() {}

    private static String typeKey(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return; // no PvP mastery
        MobMastery mastery = player.getData(VoxeliaAttachments.MOB_MASTERY.get());
        mastery.addKill(typeKey(victim));
        player.setData(VoxeliaAttachments.MOB_MASTERY.get(), mastery);
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;
        int tier = player.getData(VoxeliaAttachments.MOB_MASTERY.get()).tier(typeKey(victim));
        if (tier > 0) {
            double bonus = tier * VoxeliaConfig.masteryDamagePerTier();
            event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;
        int tier = player.getData(VoxeliaAttachments.MOB_MASTERY.get()).tier(typeKey(victim));
        if (tier <= 0) return;
        double chance = tier * VoxeliaConfig.masteryLootChancePerTier();
        for (ItemEntity entity : event.getDrops()) {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty() && player.getRandom().nextDouble() < chance) {
                stack.grow(stack.getCount());
            }
        }
    }
}
