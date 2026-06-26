package com.voxelia.mmo.progression;

import com.voxelia.mmo.VoxeliaMMO;
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
 * Translates skill levels into persistent attribute modifiers. Idempotent:
 * re-applying replaces the previous modifiers (keyed by stable ResourceLocation
 * ids), so it is safe to call on every level-up, login, and respawn.
 */
public final class SkillEffects {
    private SkillEffects() {}

    private static final ResourceLocation HEALTH_ID = id("combat_health");
    private static final ResourceLocation DAMAGE_ID = id("combat_damage");
    private static final ResourceLocation ARMOR_ID  = id("mining_armor");
    private static final ResourceLocation SPEED_ID  = id("foraging_speed");
    private static final ResourceLocation LUCK_ID   = id("farming_luck");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, path);
    }

    public static void apply(ServerPlayer player) {
        PlayerSkills s = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        int combat   = s.getLevel(Skill.COMBAT) - 1;
        int mining   = s.getLevel(Skill.MINING) - 1;
        int foraging = s.getLevel(Skill.FORAGING) - 1;
        int farming  = s.getLevel(Skill.FARMING) - 1;

        set(player, Attributes.MAX_HEALTH,    HEALTH_ID, combat * 0.4,    AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, combat * 0.25,   AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR,         ARMOR_ID,  mining * 0.2,    AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, foraging * 0.002, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.LUCK,          LUCK_ID,   farming * 0.05,  AttributeModifier.Operation.ADD_VALUE);

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
