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

    /** Players who asked to go in while the maze was still being raised. */
    private static final java.util.Set<java.util.UUID> WAITING = new java.util.HashSet<>();

    private static boolean isMaze(Level level) {
        return level instanceof ServerLevel sl && sl.dimension().equals(AztecAbyssConstants.MAZE_LEVEL_KEY);
    }

    /** Sends in everyone who was queued while the build was running. */
    private static void admitWaiting(ServerLevel level) {
        if (WAITING.isEmpty() || level.getServer() == null) {
            return;
        }
        java.util.List<java.util.UUID> queued = new java.util.ArrayList<>(WAITING);
        WAITING.clear();
        for (java.util.UUID id : queued) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                sendToMaze(p);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && isMaze(level)) {
            MazeRuntime.reset();
            MazeBuilder.beginIfNeeded(level);
        }
    }

    /**
     * Signs and torches, and nothing else.
     *
     * <p>A maze you can bridge over, pillar up out of or wall yourself into is not
     * a maze - every one of those turns the walls from a problem into scenery. So
     * building stays banned.
     *
     * <p>But banning <em>everything</em> banned the one thing a Runner should
     * obviously be able to do. Charting the maze is the job; a Runner who cannot
     * leave a mark has to hold the whole map in their head, and the section colours
     * in the corridor walls exist precisely to be written down. A sign at a
     * junction saying which way you already went, and a torch on the wall you have
     * already followed, are the tools of the trade rather than a way out of it -
     * neither is something you can stand on, and neither changes where a wall is.
     *
     * <p>Creative is exempt, because that is the only way to fix a map by hand.
     */
    @SubscribeEvent
    public static void onPlaceBlock(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer p && p.isCreative()) {
            return;
        }
        if (isRunnersMark(event.getPlacedBlock().getBlock())) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer p) {
            p.displayClientMessage(Component.literal(
                    "§7Signs and torches only. §8The walls are the puzzle."), true);
        }
        event.setCanceled(true);
    }

    /**
     * What a Runner is allowed to leave behind: something to read, and something
     * to see it by.
     *
     * <p>Matched by block tag rather than by listing every wood type, so a sign
     * from a mod's tree works exactly like an oak one and nothing has to be added
     * here when a new wood exists.
     */
    private static boolean isRunnersMark(net.minecraft.world.level.block.Block block) {
        var state = block.defaultBlockState();
        return state.is(net.minecraft.tags.BlockTags.ALL_SIGNS)
                || block == net.minecraft.world.level.block.Blocks.TORCH
                || block == net.minecraft.world.level.block.Blocks.WALL_TORCH
                || block == net.minecraft.world.level.block.Blocks.SOUL_TORCH
                || block == net.minecraft.world.level.block.Blocks.SOUL_WALL_TORCH;
    }

    /**
     * The maze is not a quarry.
     *
     * <p>Three rules, in the order they matter. The floor never breaks, anywhere,
     * including inside the Glade - a hole in the floor is a way under a wall, and
     * one shovel would undo the entire map. Outside the Glade nothing breaks at
     * all, which covers the corridor walls, the Glade's own wall, and the exit
     * frames. Inside the Glade you may do as you like above the floor, because
     * the clearing is the one place that is yours.
     *
     * <p>The Glade wall stands one block outside the clearing on every side, so it
     * falls outside the protected interior automatically rather than needing a
     * case of its own.
     */
    @SubscribeEvent
    public static void onBreakBlock(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        ServerPlayer p = event.getPlayer() instanceof ServerPlayer sp ? sp : null;
        if (p != null && p.isCreative()) {
            return;
        }
        net.minecraft.core.BlockPos at = event.getPos();
        if (at.getY() <= MazeData.FLOOR_Y) {
            if (p != null) {
                p.displayClientMessage(Component.literal(
                        "§7The ground does not give."), true);
            }
            event.setCanceled(true);
            return;
        }
        int min = MazeData.gladeMinBlock();
        int max = MazeData.gladeMaxBlock();
        boolean insideGlade = at.getX() >= min && at.getX() <= max
                && at.getZ() >= min && at.getZ() <= max;
        if (!insideGlade) {
            if (p != null) {
                p.displayClientMessage(Component.literal(
                        "§7The maze does not come apart. §8Only the Glade is yours."), true);
            }
            event.setCanceled(true);
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
            if (!MazeBuilder.isBuilding()) {
                admitWaiting(level);
            }
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
                .then(Commands.literal("map").executes(ctx -> chart(ctx.getSource())))
                .then(Commands.literal("rebuild").requires(src -> src.hasPermission(2))
                        .executes(ctx -> rebuild(ctx.getSource())))
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
            // Never fail mutely here. This is reached from a button that closes
            // itself, so a silent false looks exactly like the game ignoring you.
            player.displayClientMessage(Component.literal(
                    "§cThe maze dimension is not loaded on this world."), false);
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
            // Queue them instead of turning them away. The build is a one-off and
            // takes seconds, and "try again in a moment" is indistinguishable from
            // the game being broken if you happen to hit it twice.
            WAITING.add(player.getUUID());
            player.displayClientMessage(Component.literal(
                    "§7The maze is being raised — §e" + MazeBuilder.progressPercent()
                            + "%§7. You will be sent in the moment it is ready."), false);
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
        // Nobody inside means the last attempt ended - everyone died or everyone
        // left. Re-roll the way out before this runner arrives, so a wipe costs
        // you the route you had learned. The maze itself is untouched: whatever
        // was built or broken in there is exactly where it was left.
        if (maze.players().isEmpty()) {
            MazeRuntime.rerollAfterWipe(maze);
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
    public static void returnToTeleporter(ServerPlayer player) {
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

    /**
     * {@code /maze rebuild} - restamps the whole map from scratch.
     *
     * <p>An existing world keeps whatever maze it was first given, so a change to
     * the shape of the map is invisible on it. This is the way to take one.
     */
    private static int rebuild(CommandSourceStack src) {
        ServerLevel maze = src.getServer().getLevel(AztecAbyssConstants.MAZE_LEVEL_KEY);
        if (maze == null) {
            src.sendFailure(Component.literal("The maze dimension is not loaded."));
            return 0;
        }
        MazeBuilder.forceRebuild(maze);
        MazeRuntime.reset();
        src.sendSuccess(() -> Component.literal(
                "§5The maze is being torn down and raised again. §7Give it a moment."), true);
        return 1;
    }

    /**
     * {@code /maze map} - what the Glade knows.
     *
     * <p>Drawn in chat rather than on an item, because a real filled map would
     * show terrain the maze does not have - every cell is the same stone - and
     * would need a renderer to say the one thing that matters, which is whether
     * anybody has been down there. Blocks in chat say it directly.
     */
    private static int chart(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        MazeCharts charts = MazeCharts.get(src.getServer());
        int mine = charts.myPercent(player.getUUID());
        int all = charts.gladePercent();

        src.sendSuccess(() -> Component.literal(
                "§6— THE MAP ROOM —  §7you §f" + mine + "%§7, the Glade §f" + all + "%"), false);
        for (String row : charts.render(player, 21)) {
            src.sendSuccess(() -> Component.literal(row), false);
        }
        int[] sec = charts.sectionPercent();
        src.sendSuccess(() -> Component.literal(
                "§7NW §f" + sec[0] + "%  §7NE §f" + sec[1]
                        + "%  §7SW §f" + sec[2] + "%  §7SE §f" + sec[3] + "%"), false);
        src.sendSuccess(() -> Component.literal(
                "§8█ yours   ▓ brought back by others   · unknown   ▒ the Glade"), false);
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
        MazeSting.clear(player.getUUID());
        DIED_IN_MAZE.add(player.getUUID());
    }

    /** Who died in the maze and is owed a trip out of it on respawn. */
    private static final java.util.Set<java.util.UUID> DIED_IN_MAZE = new java.util.HashSet<>();

    /**
     * A Griever landing a hit adds to the tally. Four is what turns it - so a
     * hit is a cost, not a verdict, until the fourth one.
     */
    @SubscribeEvent
    public static void onStung(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.Mob mob
                && Griever.isGriever(mob)) {
            MazeSting.onStung(level, player);
        }
    }

    /** Drinking Grief Serum stops the Changing and wipes the tally. */
    @SubscribeEvent
    public static void onDrink(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        if (event.getItem().is(net.minecraft.world.item.Items.POTION)
                && event.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            MazeSting.cure(level, player);
        }
    }

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
            MazeSting.clear(event.getEntity().getUUID());
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
