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

import java.util.ArrayList;
import java.util.List;

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
        var skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());

        // --- Fortune-style bonus drops on ores (Mining) and wood (Foraging) ---
        Skill skill = null;
        double perLevel = 0;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            skill = Skill.FORAGING;
            perLevel = VoxeliaConfig.foragingFortunePerLevel();
        } else if (state.is(Tags.Blocks.ORES)) {
            skill = Skill.MINING;
            perLevel = VoxeliaConfig.miningFortunePerLevel();
        } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            skill = Skill.EXCAVATION;
            perLevel = VoxeliaConfig.excavationFortunePerLevel();
        }
        if (skill != null && perLevel > 0 && !hasSilkTouch(event.getLevel(), event.getTool())) {
            double factor = perLevel * skills.getLevel(skill)
                * com.voxelia.mmo.progression.TalentLogic.fortuneBonus(player, skill);
            for (ItemEntity entity : event.getDrops()) {
                ItemStack stack = entity.getItem();
                if (stack.isEmpty()) continue;
                double bonus = stack.getCount() * factor;
                int extra = (int) Math.floor(bonus);
                if (player.getRandom().nextDouble() < (bonus - extra)) extra++;
                if (extra > 0) stack.grow(extra);
            }
        }

        // --- Telekinesis: high Mining sends every mined drop to your inventory ---
        int teleLevel = VoxeliaConfig.telekinesisLevel();
        if (teleLevel > 0 && skills.getLevel(Skill.MINING) >= teleLevel) {
            // These entities haven't spawned yet — the event spawns whatever is left in
            // the list afterwards. So an absorbed drop must be REMOVED from the list;
            // discarding it instead leaves the level trying to spawn a removed entity,
            // which is the "Tried to add entity minecraft:item but it was marked as
            // removed already" warning that floods the server log while mining.
            List<ItemEntity> absorbed = new ArrayList<>();
            for (ItemEntity entity : event.getDrops()) {
                ItemStack stack = entity.getItem();
                if (stack.isEmpty()) {
                    absorbed.add(entity);
                    continue;
                }
                player.getInventory().add(stack); // mutates stack down to the leftover
                if (stack.isEmpty()) absorbed.add(entity);
                else entity.setItem(stack);       // partial: the rest still hits the floor
            }
            if (!absorbed.isEmpty()) {
                try {
                    event.getDrops().removeAll(absorbed);
                } catch (UnsupportedOperationException readOnly) {
                    // Belt and braces: if the list can't be edited, hand back empty
                    // stacks instead — an empty ItemEntity discards itself on its first
                    // tick, which is still silent.
                    for (ItemEntity entity : absorbed) entity.setItem(ItemStack.EMPTY);
                }
            }
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
