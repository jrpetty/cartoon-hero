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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
                .then(Commands.literal("section").executes(ctx -> section(ctx.getSource())))
                .then(Commands.literal("leaderboard").executes(ctx -> leaderboard(ctx.getSource())))
                .then(Commands.literal("top").executes(ctx -> leaderboard(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("griever").requires(src -> src.hasPermission(2))
                        .executes(ctx -> spawnGriever(ctx.getSource())))
                .then(Commands.literal("race").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("start").executes(ctx -> raceStart(ctx.getSource())))
                        .then(Commands.literal("stop").executes(ctx -> raceStop(ctx.getSource()))));
        event.getDispatcher().register(root);
    }

    /**
     * Shared way in, used by both {@code /maze enter} and the portal picker.
     * Refuses while the map is still going down rather than dropping someone
     * into half a maze.
     */
    public static boolean sendToMaze(ServerPlayer player) {
        if (player.getServer() == null) {
            return false;
        }
        ServerLevel maze = player.getServer().getLevel(AztecAbyssConstants.MAZE_LEVEL_KEY);
        if (maze == null) {
            return false;
        }
        int lock = MazeRuns.lockoutRemaining(player.getUUID());
        if (lock > 0) {
            player.displayClientMessage(Component.literal(
                    "§cThe maze is not done with you yet — §7" + lock + "s."), true);
            return false;
        }
        MazeBuilder.beginIfNeeded(maze);
        if (MazeBuilder.isBuilding()) {
            player.displayClientMessage(Component.literal(
                    "§7The maze is still being raised — §e" + MazeBuilder.progressPercent()
                            + "%§7. Try again in a moment."), true);
            return false;
        }
        // Remember the teleporter they stepped through, so the maze can put them
        // back on it rather than at whatever the world calls spawn. Uses the same
        // home fields the arena side already keeps, so a player who does both
        // always returns to wherever they last went in from.
        if (!player.level().dimension().equals(AztecAbyssConstants.MAZE_LEVEL_KEY)) {
            com.jrpetty.aztecabyss.round.RunState rs =
                    player.getData(com.jrpetty.aztecabyss.registry.ModAttachments.RUN_STATE);
            rs.setHome(player.blockPosition(), player.level().dimension());
            player.setData(com.jrpetty.aztecabyss.registry.ModAttachments.RUN_STATE, rs);
        }
        player.changeDimension(new DimensionTransition(maze,
                new Vec3(MazeData.SPAWN_X + 0.5, MazeData.SPAWN_Y, MazeData.SPAWN_Z + 0.5),
                Vec3.ZERO, 0.0F, 0.0F, DimensionTransition.DO_NOTHING));
        player.displayClientMessage(Component.literal(
                "§2§lTHE GLADE§r §7— " + MazeRuntime.status(maze)), false);
        return true;
    }

    private static int enter(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || player.getServer() == null) {
            return 0;
        }
        return sendToMaze(player) ? 1 : 0;
    }

    private static int leave(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || player.getServer() == null) {
            return 0;
        }
        returnToTeleporter(player);
        return 1;
    }

    /**
     * Puts a player back on the teleporter they came in through.
     *
     * <p>Falls back to world spawn only if the stored home is missing or points
     * at a dimension that no longer loads - and never at the maze itself, which
     * would leave someone ejected from the maze standing in it.
     */
    private static void returnToTeleporter(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        com.jrpetty.aztecabyss.round.RunState rs =
                player.getData(com.jrpetty.aztecabyss.registry.ModAttachments.RUN_STATE);
        ServerLevel home = player.getServer().overworld();
        if (rs.getHomeDimension() != null) {
            ServerLevel stored = player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, rs.getHomeDimension()));
            if (stored != null && !stored.dimension().equals(AztecAbyssConstants.MAZE_LEVEL_KEY)) {
                home = stored;
            }
        }
        net.minecraft.core.BlockPos at = rs.getHomePortalPos();
        Vec3 to = at != null
                ? new Vec3(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5)
                : Vec3.atBottomCenterOf(home.getSharedSpawnPos());
        player.changeDimension(new DimensionTransition(home, to,
                Vec3.ZERO, player.getYRot(), 0.0F, DimensionTransition.DO_NOTHING));
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

    /**
     * Dying in the maze costs time, not the run. The runner is put back in the
     * Glade with their clock still going and a penalty on it.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // Grievers are handled by onGrieverKilled
        }
        if (!(player.level() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        MazeRuns.onDeath(player);
        MazeRace.dropOut(level, player.getUUID());
        DIED_IN_MAZE.add(player.getUUID());
    }

    /** Who died in the maze and is owed a trip out of it on respawn. */
    private static final java.util.Set<java.util.UUID> DIED_IN_MAZE = new java.util.HashSet<>();

    /** A dead Griever leaves the colour team, and sometimes a serum. */
    @SubscribeEvent
    public static void onGrieverKilled(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)
                || !(mob.level() instanceof ServerLevel level) || !isMaze(level)
                || !Griever.isGriever(mob)) {
            return;
        }
        Griever.onDeath(level, mob);
        boolean dropped = MazeSerum.maybeDrop(level, mob, net.minecraft.util.RandomSource.create());
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            MazeAdvancements.grant(killer, MazeAdvancements.GRIEVER_SLAYER);
            if (dropped) {
                MazeAdvancements.grant(killer, MazeAdvancements.SERUM);
            }
        }
    }

    private static int raceStart(CommandSourceStack src) {
        ServerLevel maze = src.getServer().getLevel(AztecAbyssConstants.MAZE_LEVEL_KEY);
        if (maze == null || !MazeRace.start(maze)) {
            src.sendFailure(Component.literal("Cannot start a race — nobody is in the maze, or one is already running."));
            return 0;
        }
        return 1;
    }

    private static int raceStop(CommandSourceStack src) {
        ServerLevel maze = src.getServer().getLevel(AztecAbyssConstants.MAZE_LEVEL_KEY);
        if (maze != null) {
            MazeRace.stop(maze, "cancelled");
        }
        return 1;
    }

    /**
     * Dying in the maze puts you out of it.
     *
     * <p>Handled on respawn rather than on death because a dead player cannot be
     * moved anywhere useful. Vanilla may already have respawned them outside -
     * with no bed they land at world spawn - but it may not, so this is explicit
     * either way rather than relying on where the respawn happened to put them.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !DIED_IN_MAZE.remove(player.getUUID())) {
            return;
        }
        if (player.getServer() == null) {
            return;
        }
        returnToTeleporter(player);
        int lock = MazeRuns.lockoutRemaining(player.getUUID());
        player.displayClientMessage(Component.literal(lock > 0
                ? "§7The walls spat you back out at the teleporter. §8Back in in " + lock + "s."
                : "§7The walls spat you back out at the teleporter."), false);
    }

    /** Leaving the dimension abandons whatever run was going. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom().equals(AztecAbyssConstants.MAZE_LEVEL_KEY)) {
            MazeRuns.abandon(event.getEntity().getUUID());
            MazeRuntime.onPlayerLeft(event.getEntity().getUUID());
        }
        if (event.getTo().equals(AztecAbyssConstants.MAZE_LEVEL_KEY)
                && event.getEntity() instanceof ServerPlayer p) {
            MazeAdvancements.grant(p, MazeAdvancements.ROOT);
        }
    }

    private static int leaderboard(CommandSourceStack src) {
        java.util.List<MazeRuns.Record> top = MazeRuns.get(src.getServer()).top();
        if (top.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7Nobody has escaped the maze yet."), false);
            return 1;
        }
        MazeRace race = MazeRace.get(src.getServer());
        if (race.recordSeconds() > 0) {
            src.sendSuccess(() -> Component.literal("§6🏁 Race record: §f" + race.recordName()
                    + " §b" + MazeRuns.format(race.recordSeconds())), false);
        }
        src.sendSuccess(() -> Component.literal("§6§l✦ FASTEST ESCAPES ✦"), false);
        for (int i = 0; i < top.size(); i++) {
            MazeRuns.Record r = top.get(i);
            int rank = i + 1;
            src.sendSuccess(() -> Component.literal("§e#" + rank + " §f" + r.name()
                    + " §b" + MazeRuns.format(r.seconds())
                    + " §7(day " + r.day() + ", " + r.layout()
                    + (r.deaths() > 0 ? ", " + r.deaths() + " deaths" : "") + ")"), false);
        }
        return 1;
    }

    private static int stop(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            return 0;
        }
        MazeRuns.abandon(player.getUUID());
        src.sendSuccess(() -> Component.literal("§7Run abandoned."), false);
        return 1;
    }

    private static int spawnGriever(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level) || !isMaze(level)) {
            src.sendFailure(Component.literal("Only inside the maze."));
            return 0;
        }
        Griever.spawnNear(level, player, net.minecraft.util.RandomSource.create());
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
