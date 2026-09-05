package com.voxelia.mmo.event;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

/** Prefixes chat with the player's level + title, e.g. [Lv 42 • Master Miner] Steve: hi */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class ChatTitleEvents {
    private ChatTitleEvents() {}

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (!VoxeliaConfig.showChatTitle()) return;
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        int level = skills.characterLevel();
        String title = rank(level) + " " + skills.highest().noun();

        Component formatted = Component.literal("")
            .append(Component.literal("[Lv " + level + " • " + title + "] ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(event.getMessage());

        event.setCanceled(true);
        server.getPlayerList().broadcastSystemMessage(formatted, false);
    }

    private static String rank(int level) {
        // Bands scale with the cap so the ladder still spans the whole 1–500 climb.
        int max = SkillCurve.MAX_LEVEL;
        if (level >= max) return "Grandmaster";
        if (level >= max * 3 / 4) return "Master";
        if (level >= max / 2) return "Expert";
        if (level >= max / 4) return "Adept";
        if (level >= max / 10) return "Apprentice";
        return "Novice";
    }
}
