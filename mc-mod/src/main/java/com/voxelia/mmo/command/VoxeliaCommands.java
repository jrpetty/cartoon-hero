package com.voxelia.mmo.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.progression.Progression;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /voxelia skills  and  /voxelia grant <skill> <amount> (op). */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class VoxeliaCommands {
    private VoxeliaCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("voxelia")
                .then(Commands.literal("skills").executes(VoxeliaCommands::showSkills))
                .then(Commands.literal("grant")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("skill", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(VoxeliaCommands::grant))))
        );
    }

    private static int showSkills(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        ctx.getSource().sendSuccess(() -> Component
            .literal("=== Voxelia Skills (Lv " + skills.characterLevel() + ") ===")
            .withStyle(ChatFormatting.GOLD), false);
        for (Skill s : Skill.values()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                s.display() + ": level " + skills.getLevel(s) + "  (" + skills.getXp(s) + " xp)"), false);
        }
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String skillId = StringArgumentType.getString(ctx, "skill");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        Skill skill = Skill.byId(skillId);
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal(
                "Unknown skill '" + skillId + "'. Use: mining, foraging, combat, farming."));
            return 0;
        }
        Progression.grant(player, skill, amount);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Granted " + amount + " " + skill.display() + " XP."), true);
        return 1;
    }
}
