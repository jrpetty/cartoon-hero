package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Wires the maze dimension into the mod: builds it on first load, drives its
 * daily clock, and provides the way in and out.
 *
 * <p>Entry is a command rather than a portal for now. The Abyss portal's picker
 * chooses between <em>arenas</em> - maps that share the round system, the
 * rewards and the scoring - and the maze shares none of that. Hanging it off the
 * same picker would imply a sameness that is not there.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class MazeEvents {

    private MazeEvents() {
    }

    private static boolean isMaze(Level level) {
        return level instanceof ServerLevel sl && sl.dimension().equals(AztecAbyssConstants.MAZE_LEVEL_KEY);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && isMaze(level)) {
            MazeRuntime.reset();
            MazeBuilder.beginIfNeeded(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        // While the map is going down, run the stamper every tick so it finishes
        // in seconds; after that the clock only needs looking at once a second.
        if (MazeBuilder.isBuilding()) {
            MazeBuilder.tick(level);
            return;
        }
        if (level.getGameTime() % 20L == 0L) {
            MazeRuntime.tick(level);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("maze")
                .then(Commands.literal("enter").executes(ctx -> enter(ctx.getSource())))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("section").executes(ctx -> section(ctx.getSource())));
        event.getDispatcher().register(root);
    }

    private static int enter(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || player.getServer() == null) {
            return 0;
        }
        ServerLevel maze = player.getServer().getLevel(AztecAbyssConstants.MAZE_LEVEL_KEY);
        if (maze == null) {
            src.sendFailure(Component.literal("The maze dimension is not loaded."));
            return 0;
        }
        MazeBuilder.beginIfNeeded(maze);
        if (MazeBuilder.isBuilding()) {
            src.sendSuccess(() -> Component.literal(
                    "§7The maze is still being raised — §e" + MazeBuilder.progressPercent()
                            + "%§7. Try again in a moment."), false);
            return 0;
        }
        player.changeDimension(new DimensionTransition(maze,
                new Vec3(MazeData.SPAWN_X + 0.5, MazeData.SPAWN_Y, MazeData.SPAWN_Z + 0.5),
                Vec3.ZERO, 0.0F, 0.0F, DimensionTransition.DO_NOTHING));
        player.displayClientMessage(Component.literal(
                "§2§lTHE GLADE§r §7— " + MazeRuntime.status(maze)), false);
        return 1;
    }

    private static int leave(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || player.getServer() == null) {
            return 0;
        }
        ServerLevel home = player.getServer().overworld();
        player.changeDimension(new DimensionTransition(home,
                Vec3.atBottomCenterOf(home.getSharedSpawnPos()),
                Vec3.ZERO, 0.0F, 0.0F, DimensionTransition.DO_NOTHING));
        return 1;
    }

    private static int status(CommandSourceStack src) {
        ServerLevel maze = src.getServer().getLevel(AztecAbyssConstants.MAZE_LEVEL_KEY);
        if (maze == null) {
            src.sendFailure(Component.literal("The maze dimension is not loaded."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("§6" + MazeRuntime.status(maze)), false);
        return 1;
    }

    private static int section(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "§6Section: §f" + MazeRuntime.section(player.blockPosition())), false);
        return 1;
    }
}
