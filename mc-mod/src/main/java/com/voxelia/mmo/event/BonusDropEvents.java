package com.voxelia.mmo.event;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Fortune-style bonus drops from the Mining (ores) and Foraging (logs/leaves)
 * skills. Scales the dropped stack sizes up by a per-level factor. Never applies
 * when the tool has Silk Touch (which would otherwise let you dupe blocks).
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class BonusDropEvents {
    private BonusDropEvents() {}

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();

        Skill skill;
        double perLevel;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            skill = Skill.FORAGING;
            perLevel = VoxeliaConfig.foragingFortunePerLevel();
        } else if (state.is(Tags.Blocks.ORES)) {
            skill = Skill.MINING;
            perLevel = VoxeliaConfig.miningFortunePerLevel();
        } else {
            return;
        }

        if (hasSilkTouch(event.getLevel(), event.getTool())) return;

        int level = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill);
        double factor = perLevel * level; // e.g. 0.01 * level -> +100% (2x) at level 100
        if (factor <= 0) return;

        for (ItemEntity entity : event.getDrops()) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;
            double bonus = stack.getCount() * factor;
            int extra = (int) Math.floor(bonus);
            if (player.getRandom().nextDouble() < (bonus - extra)) extra++;
            if (extra > 0) stack.grow(extra);
        }
    }

    private static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        if (tool.isEmpty()) return false;
        Holder<Enchantment> silk = level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0;
    }
}
