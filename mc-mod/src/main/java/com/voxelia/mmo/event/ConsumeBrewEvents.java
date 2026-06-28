package com.voxelia.mmo.event;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.progression.Progression;
import com.voxelia.mmo.progression.TalentLogic;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.ArrayList;

/**
 * Cooking (passive): trains from eating; "Well Fed" grants a short regeneration.
 * Alchemy (passive): trains from brewing; "Lingering" extends your beneficial
 * effect durations whenever you finish drinking a potion.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class ConsumeBrewEvents {
    private ConsumeBrewEvents() {}

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem();

        // Cooking — eating food
        if (stack.has(DataComponents.FOOD)) {
            Progression.grant(player, Skill.COOKING, 8);
            int cooking = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(Skill.COOKING);
            int wellFed = VoxeliaConfig.cookingWellFedLevel();
            if (wellFed > 0 && cooking >= wellFed) {
                int amp = Math.min(3, (cooking - wellFed) / 25);
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, amp, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, false, false, true));
            }
        }

        // Alchemy — drinking a potion
        if (stack.getItem() instanceof PotionItem) {
            Progression.grant(player, Skill.ALCHEMY, 6);
            int alchemy = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(Skill.ALCHEMY);
            double factor = 1.0 + alchemy * VoxeliaConfig.alchemyDurationPerLevel()
                * TalentLogic.masteryMultiplier(player, Skill.ALCHEMY);
            if (factor > 1.0) {
                for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
                    if (effect.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) continue;
                    if (effect.isInfiniteDuration()) continue;
                    int newDuration = (int) Math.min(Integer.MAX_VALUE, effect.getDuration() * factor);
                    player.addEffect(new MobEffectInstance(effect.getEffect(), newDuration, effect.getAmplifier(),
                        effect.isAmbient(), effect.isVisible(), effect.showIcon()));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBrew(PlayerBrewedPotionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Progression.grant(player, Skill.ALCHEMY, 12);
        }
    }
}
