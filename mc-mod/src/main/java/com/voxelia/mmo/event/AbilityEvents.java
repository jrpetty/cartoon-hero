package com.voxelia.mmo.event;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.progression.Progression;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.PickaxeItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The "active" skills + perks, handled live on the server game bus:
 *   Acrobatics — trains from fall damage; dodge chance + softer landings.
 *   Fishing    — trains from catches; bonus luck, faster bites, treasure.
 *   Mining     — Haste while holding a pickaxe past a milestone.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class AbilityEvents {
    private AbilityEvents() {}

    private static final ResourceLocation FISHING_LUCK_ID =
        ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "fishing_luck");

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var source = event.getSource();
        int acro = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(Skill.ACROBATICS);

        // Fall damage trains Acrobatics (always, on the full amount) and is softened, not dodged.
        if (source.is(DamageTypes.FALL)) {
            Progression.grant(player, Skill.ACROBATICS, Math.max(2, (int) Math.ceil(event.getAmount() * 2.0)));
            double reduction = Math.min(0.95, acro * VoxeliaConfig.acrobaticsFallReductionPerLevel());
            if (reduction > 0) event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
            return;
        }
        // Never dodge unavoidable damage (void, /kill, etc.).
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        double dodge = Math.min(0.95, acro * VoxeliaConfig.acrobaticsDodgePerLevel());
        if (dodge > 0 && player.getRandom().nextDouble() < dodge) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("Dodged!").withStyle(ChatFormatting.AQUA), true);
            player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.6f);
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
            }
        }
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Progression.grant(player, Skill.FISHING, 10);

        int level = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(Skill.FISHING);
        double chance = Math.min(VoxeliaConfig.fishingTreasureChanceMax(), level / 200.0);
        if (chance > 0 && player.getRandom().nextDouble() < chance) {
            Progression.grant(player, Skill.FISHING, 15); // treasure: bonus XP
            player.giveExperiencePoints(8);
            player.displayClientMessage(
                Component.literal("Treasure catch!").withStyle(ChatFormatting.GOLD), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());

        // --- Fishing perks while a line is out ---
        int fishing = skills.getLevel(Skill.FISHING);
        boolean isFishing = player.fishing != null;
        double t = (SkillCurve.MAX_LEVEL > 1) ? (fishing - 1) / (double) (SkillCurve.MAX_LEVEL - 1) : 0.0;

        AttributeInstance luck = player.getAttribute(Attributes.LUCK);
        if (luck != null) {
            if (isFishing && fishing > 1) {
                double value = VoxeliaConfig.fishingLuckMax() * t;
                AttributeModifier existing = luck.getModifier(FISHING_LUCK_ID);
                if (existing == null || existing.amount() != value) {
                    luck.removeModifier(FISHING_LUCK_ID);
                    luck.addTransientModifier(new AttributeModifier(
                        FISHING_LUCK_ID, value, AttributeModifier.Operation.ADD_VALUE));
                }
            } else if (luck.getModifier(FISHING_LUCK_ID) != null) {
                luck.removeModifier(FISHING_LUCK_ID);
            }
        }

        FishingHook hook = player.fishing;
        if (hook != null && fishing > 1 && hook.timeUntilLured > 0) {
            if (player.getRandom().nextDouble() < (VoxeliaConfig.fishingSpeedMax() - 1.0) * t) {
                hook.timeUntilLured = Math.max(0, hook.timeUntilLured - 1);
            }
        }

        // --- Mining Haste while holding a pickaxe past the milestone ---
        int hasteLevel = VoxeliaConfig.miningHasteLevel();
        if (hasteLevel > 0 && player.getMainHandItem().getItem() instanceof PickaxeItem) {
            int mining = skills.getLevel(Skill.MINING);
            if (mining >= hasteLevel) {
                int amplifier = Math.min(2, (mining - hasteLevel) / 25);
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60, amplifier, true, false, false));
            }
        }
    }
}
