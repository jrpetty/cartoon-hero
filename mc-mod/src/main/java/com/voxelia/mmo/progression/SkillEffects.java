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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, path);
    }

    public static void apply(ServerPlayer player) {
        PlayerSkills s = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        int combat   = s.getLevel(Skill.COMBAT) - 1;
        int farming  = s.getLevel(Skill.FARMING) - 1;
        int mining   = s.getLevel(Skill.MINING) - 1;
        int foraging = s.getLevel(Skill.FORAGING) - 1;

        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID,
            combat * VoxeliaConfig.combatDamagePerLevel(), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MAX_HEALTH, HEALTH_ID,
            farming * VoxeliaConfig.farmingHealthPerLevel(), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.BLOCK_BREAK_SPEED, MINING_SPEED_ID,
            mining * VoxeliaConfig.miningSpeedPerLevel(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.BLOCK_BREAK_SPEED, FORAGING_SPEED_ID,
            foraging * VoxeliaConfig.foragingSpeedPerLevel(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

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
