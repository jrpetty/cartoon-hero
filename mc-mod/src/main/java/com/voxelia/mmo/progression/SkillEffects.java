package com.voxelia.mmo.progression;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Translates the always-on skills into persistent attribute modifiers:
 *   Combat   -> attack damage
 *   Farming  -> max health
 *   Mining   -> block break speed
 *   Foraging -> block break speed
 * (Acrobatics' dodge and Fishing's perks are handled live in AbilityEvents.)
 *
 * Idempotent: modifiers are keyed by stable ids, so re-applying replaces them.
 * Called on level-up, login, and respawn.
 */
public final class SkillEffects {
    private SkillEffects() {}

    private static final ResourceLocation DAMAGE_ID        = id("combat_damage");
    private static final ResourceLocation HEALTH_ID        = id("farming_health");
    private static final ResourceLocation MINING_SPEED_ID  = id("mining_speed");
    private static final ResourceLocation FORAGING_SPEED_ID = id("foraging_speed");
    private static final ResourceLocation EXCAV_SPEED_ID   = id("excavation_speed");
    private static final ResourceLocation DEF_ARMOR_ID     = id("defense_armor");
    private static final ResourceLocation DEF_TOUGH_ID     = id("defense_toughness");
    private static final ResourceLocation DEF_KB_ID        = id("defense_knockback");
    private static final ResourceLocation ACRO_MASTERY_ID  = id("acrobatics_mastery_speed");
    private static final ResourceLocation FISH_MASTERY_ID  = id("fishing_mastery_luck");
    private static final ResourceLocation ARCH_MASTERY_ID  = id("archery_mastery_damage");
    private static final ResourceLocation COOK_MASTERY_ID  = id("cooking_mastery_health");
    private static final ResourceLocation ALCH_MASTERY_ID  = id("alchemy_mastery_luck");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, path);
    }

    public static void apply(ServerPlayer player) {
        PlayerSkills s = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        int combat   = s.getLevel(Skill.COMBAT) - 1;
        int farming  = s.getLevel(Skill.FARMING) - 1;
        int mining   = s.getLevel(Skill.MINING) - 1;
        int foraging = s.getLevel(Skill.FORAGING) - 1;
        int excav    = s.getLevel(Skill.EXCAVATION) - 1;
        int defense  = s.getLevel(Skill.DEFENSE) - 1;

        // Mastery talent ranks (× global scale) add on top of the per-level bonus.
        double mCombat = TalentLogic.mastery(player, Skill.COMBAT);
        double mFarming = TalentLogic.mastery(player, Skill.FARMING);
        double mMining = TalentLogic.mastery(player, Skill.MINING);
        double mForaging = TalentLogic.mastery(player, Skill.FORAGING);
        double mExcav = TalentLogic.mastery(player, Skill.EXCAVATION);
        double mDefense = TalentLogic.mastery(player, Skill.DEFENSE);

        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID,
            combat * VoxeliaConfig.combatDamagePerLevel() + mCombat * 0.3, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MAX_HEALTH, HEALTH_ID,
            farming * VoxeliaConfig.farmingHealthPerLevel() + mFarming * 0.5, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.BLOCK_BREAK_SPEED, MINING_SPEED_ID,
            mining * VoxeliaConfig.miningSpeedPerLevel() + mMining * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.BLOCK_BREAK_SPEED, FORAGING_SPEED_ID,
            foraging * VoxeliaConfig.foragingSpeedPerLevel() + mForaging * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.BLOCK_BREAK_SPEED, EXCAV_SPEED_ID,
            excav * VoxeliaConfig.excavationSpeedPerLevel() + mExcav * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ARMOR, DEF_ARMOR_ID,
            defense * VoxeliaConfig.defenseArmorPerLevel() + mDefense * 0.4, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR_TOUGHNESS, DEF_TOUGH_ID,
            defense * VoxeliaConfig.defenseToughnessPerLevel(), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.KNOCKBACK_RESISTANCE, DEF_KB_ID,
            defense * VoxeliaConfig.defenseKnockbackResistPerLevel(), AttributeModifier.Operation.ADD_VALUE);

        // Mastery for the passive skills maps onto a fitting attribute.
        set(player, Attributes.MOVEMENT_SPEED, ACRO_MASTERY_ID,
            TalentLogic.mastery(player, Skill.ACROBATICS) * 0.005, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.LUCK, FISH_MASTERY_ID,
            TalentLogic.mastery(player, Skill.FISHING) * 0.2, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_DAMAGE, ARCH_MASTERY_ID,
            TalentLogic.mastery(player, Skill.ARCHERY) * 0.2, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MAX_HEALTH, COOK_MASTERY_ID,
            TalentLogic.mastery(player, Skill.COOKING) * 0.4, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.LUCK, ALCH_MASTERY_ID,
            TalentLogic.mastery(player, Skill.ALCHEMY) * 0.2, AttributeModifier.Operation.ADD_VALUE);

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void set(ServerPlayer player, Holder<Attribute> attr, ResourceLocation id,
                            double value, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (value != 0.0) {
            inst.addPermanentModifier(new AttributeModifier(id, value, op));
        }
    }
}
