package com.voxelia.mmo.event;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.progression.Progression;
import com.voxelia.mmo.progression.TalentLogic;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Archery (passive): trains from landing ranged hits. "Power Shot" — fully-drawn
 * (critical) arrows deal bonus damage scaling with the Archery level.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class ArcheryEvents {
    private ArcheryEvents() {}

    @SubscribeEvent
    public static void onArrowHit(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) return;
        if (!(source.getEntity() instanceof ServerPlayer shooter)) return;

        int level = shooter.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(Skill.ARCHERY);

        // Power Shot: extra damage on a fully-drawn shot.
        if (arrow.isCritArrow()) {
            double bonus = level * VoxeliaConfig.archeryPowerShotPerLevel()
                * TalentLogic.signatureBonus(shooter, Skill.ARCHERY);
            if (bonus > 0) event.setAmount((float) (event.getAmount() + bonus));
        }

        // Train from landing the hit.
        Progression.grant(shooter, Skill.ARCHERY, Math.min(20, Math.max(2, (int) Math.ceil(event.getAmount() / 2.0))));
    }
}
