package com.voxelia.mmo.progression;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.TalentType;
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
    private static final ResourceLocation VITALITY_ID      = id("talent_vitality");
    private static final ResourceLocation SWIFTNESS_ID     = id("talent_swiftness");
    private static final ResourceLocation TOUGHNESS_ID     = id("talent_toughness");
    private static final ResourceLocation FORTUNE_ID       = id("talent_fortune");

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

        // Mastery scales each skill's signature stat (the same stat shown in /voxelia stats).
        double xCombat = TalentLogic.masteryMultiplier(player, Skill.COMBAT);
        double xFarming = TalentLogic.masteryMultiplier(player, Skill.FARMING);
        double xMining = TalentLogic.masteryMultiplier(player, Skill.MINING);
        double xForaging = TalentLogic.masteryMultiplier(player, Skill.FORAGING);
        double xExcav = TalentLogic.masteryMultiplier(player, Skill.EXCAVATION);
        double xDefense = TalentLogic.masteryMultiplier(player, Skill.DEFENSE);

        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID,
            combat * VoxeliaConfig.combatDamagePerLevel() * xCombat, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MAX_HEALTH, HEALTH_ID,
            farming * VoxeliaConfig.farmingHealthPerLevel() * xFarming, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.BLOCK_BREAK_SPEED, MINING_SPEED_ID,
            mining * VoxeliaConfig.miningSpeedPerLevel() * xMining, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.BLOCK_BREAK_SPEED, FORAGING_SPEED_ID,
            foraging * VoxeliaConfig.foragingSpeedPerLevel() * xForaging, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.BLOCK_BREAK_SPEED, EXCAV_SPEED_ID,
            excav * VoxeliaConfig.excavationSpeedPerLevel() * xExcav, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ARMOR, DEF_ARMOR_ID,
            defense * VoxeliaConfig.defenseArmorPerLevel() * xDefense, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR_TOUGHNESS, DEF_TOUGH_ID,
            defense * VoxeliaConfig.defenseToughnessPerLevel() * xDefense, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.KNOCKBACK_RESISTANCE, DEF_KB_ID,
            defense * VoxeliaConfig.defenseKnockbackResistPerLevel() * xDefense, AttributeModifier.Operation.ADD_VALUE);
        // (Acrobatics, Fishing, Archery, Alchemy mastery scale their event-based
        //  signature stats directly in their handlers — see AbilityEvents etc.)

        // Universal talents are summed across every skill.
        double scale = VoxeliaConfig.talentMasteryScale();
        double vitality = 0, swiftness = 0, toughness = 0, fortune = 0;
        for (Skill sk : Skill.values()) {
            vitality  += TalentLogic.rank(player, sk, TalentType.VITALITY);
            swiftness += TalentLogic.rank(player, sk, TalentType.SWIFTNESS);
            toughness += TalentLogic.rank(player, sk, TalentType.TOUGHNESS);
            fortune   += TalentLogic.rank(player, sk, TalentType.FORTUNE);
        }
        set(player, Attributes.MAX_HEALTH, VITALITY_ID, vitality * 0.3 * scale, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, SWIFTNESS_ID, swiftness * 0.003 * scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ARMOR, TOUGHNESS_ID, toughness * 0.25 * scale, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.LUCK, FORTUNE_ID, fortune * 0.15 * scale, AttributeModifier.Operation.ADD_VALUE);

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
