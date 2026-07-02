package com.voxelia.mmo.client;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client-side cache of the local player's skills, fed by SkillsSyncPayload. */
public final class ClientSkillData {
    private ClientSkillData() {}

    private static volatile PlayerSkills current = null;

    public static void update(PlayerSkills skills) {
        current = skills;
    }

    public static int xp(Skill s) { return current == null ? 0 : current.getXp(s); }

    public static int level(Skill s) { return current == null ? 1 : current.getLevel(s); }

    public static boolean hasData() { return current != null; }

    /** Drop the cache on logout so a new world/character starts clean (no stale HUD flashes). */
    @EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            current = null;
        }
    }
}
