package com.voxelia.mmo.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaClientConfig;
import com.voxelia.mmo.config.VoxeliaClientConfig.Anchor;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;

/**
 * Client-side commands so HUD prefs and the reward reference work even in
 * single player / without op:
 *   /voxelia hud                     toggle the HUD
 *   /voxelia hudpos <corner>         move the HUD (top_left|top_right|bottom_left|bottom_right)
 *   /voxelia rewards                 print what each skill level grants
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, value = Dist.CLIENT)
public final class VoxeliaClientCommands {
    private VoxeliaClientCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("voxelia")
                .then(Commands.literal("menu").executes(VoxeliaClientCommands::openMenu))
                .then(Commands.literal("profile").executes(VoxeliaClientCommands::openProfile))
                .then(Commands.literal("leaderboards").executes(VoxeliaClientCommands::openLeaderboards))
                .then(Commands.literal("hud").executes(VoxeliaClientCommands::toggleHud))
                .then(Commands.literal("sidebar").executes(VoxeliaClientCommands::toggleSidebar))
                .then(Commands.literal("hudpos")
                    .then(Commands.argument("corner", StringArgumentType.word())
                        .executes(VoxeliaClientCommands::setCorner)))
                .then(Commands.literal("rewards").executes(VoxeliaClientCommands::rewards))
        );
    }

    private static int openMenu(CommandContext<CommandSourceStack> ctx) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new SkillsScreen()));
        return 1;
    }

    private static int openProfile(CommandContext<CommandSourceStack> ctx) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new ProfileScreen()));
        return 1;
    }

    private static int openLeaderboards(CommandContext<CommandSourceStack> ctx) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new LeaderboardScreen()));
        return 1;
    }

    private static int toggleHud(CommandContext<CommandSourceStack> ctx) {
        boolean now = !VoxeliaClientConfig.showHud();
        VoxeliaClientConfig.setShowHud(now);
        ctx.getSource().sendSuccess(() -> Component.literal("Skills HUD " + (now ? "shown" : "hidden"))
            .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int toggleSidebar(CommandContext<CommandSourceStack> ctx) {
        boolean now = !VoxeliaClientConfig.showSidebar();
        VoxeliaClientConfig.setShowSidebar(now);
        ctx.getSource().sendSuccess(() -> Component.literal("Skill sidebar " + (now ? "shown" : "hidden"))
            .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int setCorner(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "corner").toUpperCase(Locale.ROOT);
        Anchor anchor;
        try {
            anchor = Anchor.valueOf(raw);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(
                "Use one of: top_left, top_right, bottom_left, bottom_right"));
            return 0;
        }
        VoxeliaClientConfig.setAnchor(anchor);
        ctx.getSource().sendSuccess(() -> Component.literal("HUD moved to " + raw.toLowerCase(Locale.ROOT))
            .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int rewards(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("=== Voxelia level rewards (per level, max 100) ===")
            .withStyle(ChatFormatting.GOLD), false);
        line(src, "Combat",     "+" + VoxeliaConfig.combatDamagePerLevel() + " attack damage");
        line(src, "Farming",    "+" + VoxeliaConfig.farmingHealthPerLevel() + " max health");
        line(src, "Mining",     "+" + pct(VoxeliaConfig.miningSpeedPerLevel()) + " break speed, +" + pct(VoxeliaConfig.miningFortunePerLevel()) + " Fortune on ores (no Silk Touch)");
        line(src, "Foraging",   "+" + pct(VoxeliaConfig.foragingSpeedPerLevel()) + " break speed, +" + pct(VoxeliaConfig.foragingFortunePerLevel()) + " Fortune on wood (no Silk Touch)");
        line(src, "Acrobatics", "higher jumps + softer landings (trains from fall damage)");
        line(src, "Fishing",    "up to +" + VoxeliaConfig.fishingLuckMax() + " luck & bonus treasure catches at level " + SkillCurve.MAX_LEVEL);
        line(src, "Excavation", "+" + pct(VoxeliaConfig.excavationSpeedPerLevel()) + " dig speed, +" + pct(VoxeliaConfig.excavationFortunePerLevel()) + " Fortune on shovel blocks");
        line(src, "Defense",    "+" + VoxeliaConfig.defenseArmorPerLevel() + " armor & +" + VoxeliaConfig.defenseToughnessPerLevel() + " toughness per level; Last Stand when low HP");
        line(src, "Cooking",    "bonus saturation + Well Fed regen after eating (trains from eating)");
        line(src, "Alchemy",    "+" + pct(VoxeliaConfig.alchemyDurationPerLevel()) + " potion duration per level (trains from brewing)");
        line(src, "Archery",    "+" + VoxeliaConfig.archeryPowerShotPerLevel() + " Power Shot damage per level on fully-drawn arrows");
        return 1;
    }

    private static void line(CommandSourceStack src, String skill, String desc) {
        src.sendSuccess(() -> Component.literal(skill + ": ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(desc).withStyle(ChatFormatting.WHITE)), false);
    }

    private static String pct(double fraction) {
        return String.format(Locale.ROOT, "%.2f%%", fraction * 100.0);
    }
}
