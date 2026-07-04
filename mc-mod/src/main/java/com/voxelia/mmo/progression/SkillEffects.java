package com.voxelia.mmo.progression;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;
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

        // A skill's signature talent scales its signature stat (the same stat shown in /voxelia stats).
        double xCombat = TalentLogic.signatureBonus(player, Skill.COMBAT);
        double xFarming = TalentLogic.signatureBonus(player, Skill.FARMING);
        double xMining = TalentLogic.signatureBonus(player, Skill.MINING);
        double xForaging = TalentLogic.signatureBonus(player, Skill.FORAGING);
        double xExcav = TalentLogic.signatureBonus(player, Skill.EXCAVATION);
        double xDefense = TalentLogic.signatureBonus(player, Skill.DEFENSE);

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
        // (Acrobatics, Fishing, Archery, Alchemy talents scale their event-based
        //  signature stats directly in their handlers — see AbilityEvents etc.)

        // Flat attribute talents (Vitality, Fleetfoot, Deepreach, Juggernaut, Sea Fortune, …).
        // Each is keyed by its own talent id so they stack per-skill and are idempotent.
        for (Talent t : Talent.values()) {
            int r = TalentLogic.rank(player, t);
            double v = t.flatAt(r);
            switch (t.category()) {
                case HEALTH -> set(player, Attributes.MAX_HEALTH, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                case SPEED -> set(player, Attributes.MOVEMENT_SPEED, id(t.id()), v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                case ATTACK_SPEED -> set(player, Attributes.ATTACK_SPEED, id(t.id()), v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                case REACH -> {
                    set(player, Attributes.BLOCK_INTERACTION_RANGE, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                    set(player, Attributes.ENTITY_INTERACTION_RANGE, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                }
                case LUCK -> set(player, Attributes.LUCK, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                case ARMOR -> set(player, Attributes.ARMOR, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                case KB_RESIST -> set(player, Attributes.KNOCKBACK_RESISTANCE, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                case STEP -> set(player, Attributes.STEP_HEIGHT, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                case SAFEFALL -> set(player, Attributes.SAFE_FALL_DISTANCE, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                case OXYGEN -> set(player, Attributes.OXYGEN_BONUS, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                case SUBMERGE -> set(player, Attributes.SUBMERGED_MINING_SPEED, id(t.id()), v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                case SWEEP -> set(player, Attributes.SWEEPING_DAMAGE_RATIO, id(t.id()), v, AttributeModifier.Operation.ADD_VALUE);
                default -> { /* percent multiplier talents are read live by their handlers */ }
            }
        }

        // Strip the retired universal-talent modifiers from returning players' saves.
        set(player, Attributes.MAX_HEALTH, VITALITY_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, SWIFTNESS_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ARMOR, TOUGHNESS_ID, 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.LUCK, FORTUNE_ID, 0, AttributeModifier.Operation.ADD_VALUE);

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
