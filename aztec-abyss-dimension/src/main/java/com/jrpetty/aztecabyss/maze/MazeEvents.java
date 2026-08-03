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

    /** How high anything may be built inside the Glade. */
    private static final int BUILD_CEILING = MazeData.FLOOR_Y + 12;

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
        // The Glade is yours. Breaking inside it was already allowed and placing
        // was not, which meant you could pull your own hut apart and never put
        // it back - a rule that only ever destroys is not a rule, it is a hole.
        //
        // With a ceiling, though. The Glade is the one place left open to the
        // sky, so an uncapped build height is a pillar up to the wall tops and a
        // walk over the whole maze - the exploit that banning placement outright
        // used to close by accident. Twelve blocks is a tower, a lookout or a
        // second storey; it is six short of the top of the wall.
        if (insideGlade(event.getPos())) {
            if (event.getPos().getY() <= BUILD_CEILING) {
                return;
            }
            if (event.getEntity() instanceof ServerPlayer p) {
                p.displayClientMessage(Component.literal(
                        "§7Not that high. §8The wall is not a road."), true);
            }
            event.setCanceled(true);
            return;
        }
        ServerPlayer player = event.getEntity() instanceof ServerPlayer sp ? sp : null;
        var block = event.getPlacedBlock().getBlock();
        if (isRunnersMark(block)) {
            return;
        }
        if (player != null && isBuildersMark(level, player, block)) {
            markCell(level, player, event.getPos());
            return;
        }
        if (player != null) {
            player.displayClientMessage(Component.literal(
                    "§7Signs and torches only. §8The walls are the puzzle."), true);
        }
        event.setCanceled(true);
    }

    private static boolean insideGlade(net.minecraft.core.BlockPos at) {
        int min = MazeData.gladeMinBlock();
        int max = MazeData.gladeMaxBlock();
        return at.getX() >= min && at.getX() <= max && at.getZ() >= min && at.getZ() <= max;
    }

    /**
     * What a Builder may leave that a Runner may not.
     *
     * <p>Everything here is chosen on one test: can you stand on it, and does it
     * change where a wall is. Carpet is a sixteenth of a block and needs support
     * underneath, wool replaces nothing, and lanterns and banners hang. None of
     * them shortens a corridor, so the maze stays the maze while the person
     * whose job is marking it can actually mark it in more than one colour.
     */
    private static boolean isBuildersMark(ServerLevel level, ServerPlayer player,
                                          net.minecraft.world.level.block.Block block) {
        MazeJobs jobs = MazeJobs.get(level);
        if (!jobs.is(player.getUUID(), MazeJobs.BUILDER)) {
            return false;
        }
        int rank = jobs.level(player.getUUID());
        var state = block.defaultBlockState();
        if (state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)) {
            return true;
        }
        if (rank >= 2 && state.is(net.minecraft.tags.BlockTags.WOOL)) {
            return true;
        }
        return rank >= 3 && (state.is(net.minecraft.tags.BlockTags.BANNERS)
                || block == net.minecraft.world.level.block.Blocks.LANTERN
                || block == net.minecraft.world.level.block.Blocks.SOUL_LANTERN);
    }

    /**
     * Records that somebody said something about this cell, and pays them for it.
     *
     * <p>A level 4 Builder's mark also charts the cell for the whole Glade. That
     * is the one perk in the game that hands other people something rather than
     * the person who earned it, which is the right shape for the job: a Builder
     * at the top of their trade is useful to the Glade, not to themselves.
     */
    private static void markCell(ServerLevel level, ServerPlayer player, net.minecraft.core.BlockPos at) {
        if (level.getServer() == null) {
            return;
        }
        MazeCharts charts = MazeCharts.get(level.getServer());
        int cellX = at.getX() / MazeData.CELL;
        int cellZ = at.getZ() / MazeData.CELL;
        MazeJobs jobs = MazeJobs.get(level);
        if (charts.mark(cellX, cellZ)) {
            jobs.award(player, MazeJobs.BUILDER, 3);
            if (jobs.level(player.getUUID()) >= MazeJobs.MAX_LEVEL) {
                charts.chart(player.getUUID(), cellX, cellZ);
            }
        }
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
        if (!insideGlade(at)) {
            // A mark is not the maze. Signs, torches and a Builder's colours were
            // placeable and then permanent, so one torch on the wrong wall stayed
            // there for the life of the world and a chart could only ever be added
            // to. Taking your own marks back down is half of mapping.
            if (isMark(event.getState())) {
                if (p != null && level.getServer() != null) {
                    clearMarkIfLast(level, at);
                }
                return;
            }
            if (p != null) {
                p.displayClientMessage(Component.literal(
                        "§7The maze does not come apart. §8Only the Glade is yours."), true);
            }
            event.setCanceled(true);
            return;
        }
        if (p != null) {
            trackHoe(level, p, at, event.getState());
            salvage(level, p, at, event.getState());
        }
    }

    /** Anything a player was ever allowed to leave in a corridor. */
    private static boolean isMark(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.ALL_SIGNS)
                || state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)
                || state.is(net.minecraft.tags.BlockTags.WOOL)
                || state.is(net.minecraft.tags.BlockTags.BANNERS)
                || isRunnersMark(state.getBlock())
                || state.getBlock() == net.minecraft.world.level.block.Blocks.LANTERN
                || state.getBlock() == net.minecraft.world.level.block.Blocks.SOUL_LANTERN;
    }

    /**
     * Drops the map flag when the last mark in a cell comes down.
     *
     * <p>Scans the cell rather than counting, because a count would have to be
     * kept in step with every way a block can vanish and this cannot drift. Six
     * by six by the wall height is a small enough box to walk once on a break.
     */
    private static void clearMarkIfLast(ServerLevel level, net.minecraft.core.BlockPos broken) {
        int cellX = broken.getX() / MazeData.CELL;
        int cellZ = broken.getZ() / MazeData.CELL;
        MazeCharts charts = MazeCharts.get(level.getServer());
        if (!charts.marked(cellX, cellZ)) {
            return;
        }
        net.minecraft.core.BlockPos.MutableBlockPos scan = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = 0; x < MazeData.CELL; x++) {
            for (int z = 0; z < MazeData.CELL; z++) {
                for (int y = MazeData.FLOOR_Y + 1; y <= MazeData.WALL_TOP_Y; y++) {
                    scan.set(cellX * MazeData.CELL + x, y, cellZ * MazeData.CELL + z);
                    if (scan.equals(broken)) {
                        continue; // this one is on its way out
                    }
                    if (isMark(level.getBlockState(scan))) {
                        return;
                    }
                }
            }
        }
        charts.unmark(cellX, cellZ);
    }

    /**
     * The field, finally worth walking into.
     *
     * <p>It was drawn with farmland, a water channel, a fence and three crops
     * and then left as a backdrop: harvesting it did nothing, and the wheat it
     * gave you was worth less than the time it took. Now a Track-hoe's harvest
     * goes into the Glade's larder, which is a number other people spend - and
     * comes out of the ground at twice the rate it does for anybody else.
     */
    private static void trackHoe(ServerLevel level, ServerPlayer p, net.minecraft.core.BlockPos at,
                                 net.minecraft.world.level.block.state.BlockState state) {
        if (!isFarmable(state)) {
            return;
        }
        MazeJobs jobs = MazeJobs.get(level);
        if (!jobs.is(p.getUUID(), MazeJobs.TRACKHOE)) {
            return;
        }
        doubleHarvest(level, p, at, state);
        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop
                && crop.isMaxAge(state)) {
            int rank = jobs.level(p.getUUID());
            jobs.store(rank);
            jobs.award(p, MazeJobs.TRACKHOE, 1);
        }
    }

    /**
     * Everything a Track-hoe's hands are better at.
     *
     * <p>Matched on the block classes rather than a list of ids, so it covers
     * wheat, carrots, potatoes, beetroot, nether wart, berries, cocoa, cane and
     * both gourds without naming any of them - and covers a modded crop that
     * extends the same class for free. "All the farmable blocks" is a category,
     * so it is written as one.
     */
    private static boolean isFarmable(net.minecraft.world.level.block.state.BlockState state) {
        var block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.CropBlock
                || block instanceof net.minecraft.world.level.block.StemGrownBlock
                || block instanceof net.minecraft.world.level.block.SugarCaneBlock
                || block instanceof net.minecraft.world.level.block.NetherWartBlock
                || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                || block instanceof net.minecraft.world.level.block.CocoaBlock;
    }

    /**
     * A second harvest, drawn from the block's own loot table.
     *
     * <p>Rolling the real loot table rather than guessing the product means a
     * Track-hoe's bonus wheat comes with a bonus seed at the same odds the game
     * would have given, and a crop this code has never heard of still doubles
     * correctly. Anything else would be a hand-written table that drifts out of
     * date the first time a crop changes.
     *
     * <p>An unripe crop drops seeds, so this deliberately doubles that too - a
     * Track-hoe who mis-times a harvest is still better at it than you are.
     */
    private static void doubleHarvest(ServerLevel level, ServerPlayer p, net.minecraft.core.BlockPos at,
                                      net.minecraft.world.level.block.state.BlockState state) {
        java.util.List<net.minecraft.world.item.ItemStack> extra =
                net.minecraft.world.level.block.Block.getDrops(state, level, at,
                        level.getBlockEntity(at), p, p.getMainHandItem());
        for (net.minecraft.world.item.ItemStack stack : extra) {
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, at, stack.copy());
            }
        }
        // Bountiful: sometimes a third, on top of the double.
        int bountiful = MazeSkills.rankOf(level, p.getUUID(), "bountiful");
        if (bountiful > 0 && level.getRandom().nextInt(Math.max(2, 5 - bountiful)) == 0) {
            for (net.minecraft.world.item.ItemStack stack : extra) {
                if (!stack.isEmpty()) {
                    net.minecraft.world.level.block.Block.popResource(level, at, stack.copy());
                }
            }
        }
    }

    /**
     * Salvage: a Builder gets some of it back.
     *
     * <p>Only in the Glade, and only for a Builder. The Glade is the one place
     * anything comes apart, and a settlement that loses a block every time it
     * changes its mind about where a hut goes is a settlement nobody rearranges.
     */
    private static void salvage(ServerLevel level, ServerPlayer p,
                                net.minecraft.core.BlockPos at,
                                net.minecraft.world.level.block.state.BlockState state) {
        MazeJobs jobs = MazeJobs.get(level);
        if (!jobs.is(p.getUUID(), MazeJobs.BUILDER)) {
            return;
        }
        int rank = MazeSkills.rankOf(level, p.getUUID(), "salvage");
        if (rank <= 0 || level.getRandom().nextInt(Math.max(2, 7 - rank * 2)) != 0) {
            return;
        }
        for (net.minecraft.world.item.ItemStack stack
                : net.minecraft.world.level.block.Block.getDrops(state, level, at,
                        level.getBlockEntity(at), p, p.getMainHandItem())) {
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, at, stack.copy());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        // While the map is going down, run the stamper every tick so it finishes
        // in seconds.
        if (MazeBuilder.isBuilding()) {
            MazeBuilder.tick(level);
            if (!MazeBuilder.isBuilding()) {
                admitWaiting(level);
            }
            return;
        }
        // Every tick, not every twentieth. The maze owns its clock now, and a
        // clock only read once a second is wrong by up to a second - which is
        // the difference between getting through the door and not.
        MazeRuntime.tick(level);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("maze")
                .then(Commands.literal("enter").executes(ctx -> enter(ctx.getSource())))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("section").executes(ctx -> section(ctx.getSource())))
                .then(Commands.literal("map").executes(ctx -> chart(ctx.getSource())))
                .then(Commands.literal("job")
                        .executes(ctx -> jobInfo(ctx.getSource()))
                        .then(Commands.literal(MazeJobs.RUNNER)
                                .executes(ctx -> takeJob(ctx.getSource(), MazeJobs.RUNNER)))
                        .then(Commands.literal(MazeJobs.BUILDER)
                                .executes(ctx -> takeJob(ctx.getSource(), MazeJobs.BUILDER)))
                        .then(Commands.literal(MazeJobs.MEDJACK)
                                .executes(ctx -> takeJob(ctx.getSource(), MazeJobs.MEDJACK)))
                        .then(Commands.literal(MazeJobs.TRACKHOE)
                                .executes(ctx -> takeJob(ctx.getSource(), MazeJobs.TRACKHOE))))
                .then(Commands.literal("jobs").executes(ctx -> roster(ctx.getSource())))
                .then(Commands.literal("skills").executes(ctx -> skills(ctx.getSource())))
                .then(Commands.literal("learn")
                        .then(Commands.argument("skill", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> learn(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "skill")))))
                .then(Commands.literal("forget").executes(ctx -> forget(ctx.getSource())))
                .then(Commands.literal("bandage").executes(ctx -> bandage(ctx.getSource())))
                .then(Commands.literal("treat").executes(ctx -> treat(ctx.getSource())))
                .then(Commands.literal("rations").executes(ctx -> rations(ctx.getSource())))
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
                "§8█ yours   ▓ brought back by others   §e✚§8 marked   · unknown   ▒ the Glade"), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // Jobs
    // ------------------------------------------------------------------

    /** What you are, what it gives you, and what the next level gives you. */
    private static int jobInfo(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        String job = jobs.jobOf(player.getUUID());
        if (job == null) {
            src.sendSuccess(() -> Component.literal("§6— THE JOB BOARD —"), false);
            for (String j : MazeJobs.ALL) {
                src.sendSuccess(() -> Component.literal(
                        "  " + MazeJobs.display(j) + " §8/maze job " + j), false);
                src.sendSuccess(() -> Component.literal("    " + MazeJobs.blurb(j)), false);
            }
            src.sendSuccess(() -> Component.literal(
                    "§8Changing your mind is free — every job keeps its own experience."), false);
            return 1;
        }
        int rank = jobs.levelOf(player.getUUID(), job);
        int next = jobs.toNext(player.getUUID(), job);
        src.sendSuccess(() -> Component.literal(
                MazeJobs.display(job) + " §8lv" + rank + " §7— " + MazeJobs.perkLine(job, rank)), false);
        src.sendSuccess(() -> Component.literal(next < 0
                ? "§8Top of the trade."
                : "§7" + next + " more and you make level " + (rank + 1) + "§7: §f"
                        + MazeJobs.perkLine(job, rank + 1)), false);
        src.sendSuccess(() -> Component.literal(
                "§7The larder holds §f" + jobs.larder() + "§7. §8/maze rations"), false);
        return 1;
    }

    private static int takeJob(CommandSourceStack src, String job) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        if (!jobs.setJob(player.getUUID(), job)) {
            src.sendSuccess(() -> Component.literal("§7You already are one."), false);
            return 0;
        }
        int rank = jobs.levelOf(player.getUUID(), job);
        src.sendSuccess(() -> Component.literal(
                "§7You are a " + MazeJobs.display(job) + " §8lv" + rank), false);
        src.sendSuccess(() -> Component.literal("  " + MazeJobs.perkLine(job, rank)), false);
        for (ServerPlayer other : src.getServer().getPlayerList().getPlayers()) {
            if (other != player) {
                other.displayClientMessage(Component.literal(
                        "§7" + player.getGameProfile().getName() + " is a "
                                + MazeJobs.display(job) + "§7 now."), false);
            }
        }
        return 1;
    }

    private static int roster(CommandSourceStack src) {
        if (src.getServer() == null) {
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        src.sendSuccess(() -> Component.literal("§6— THE GLADE —"), false);
        for (String row : jobs.roster(src.getServer())) {
            src.sendSuccess(() -> Component.literal("  " + row), false);
        }
        src.sendSuccess(() -> Component.literal(
                "§7Larder §f" + jobs.larder()), false);
        return 1;
    }

    /**
     * A Med-jack working on somebody.
     *
     * <p>Range is deliberately short. Treating from across the Glade would make
     * the job a command rather than a thing you do, and the point of it is that
     * somebody has to physically get to a runner who is dying on the grass.
     */
    private static int treat(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        ServerLevel level = src.getLevel();
        if (player == null || src.getServer() == null || !isMaze(level)) {
            src.sendFailure(Component.literal("Only inside the maze."));
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        if (!jobs.is(player.getUUID(), MazeJobs.MEDJACK)) {
            src.sendFailure(Component.literal("You are not a Med-jack. §8/maze job medjack"));
            return 0;
        }
        int steady = MazeSkills.rankOf(level, player.getUUID(), "steady");
        double reach = 5.0 + steady * 2.0;
        ServerPlayer patient = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer other : level.players()) {
            if (!MazeSting.isChanging(other.getUUID())) {
                continue;
            }
            double d = other.distanceToSqr(player);
            if (d <= reach * reach && d < best) {
                best = d;
                patient = other;
            }
        }
        if (patient == null) {
            src.sendFailure(Component.literal("Nobody within reach is Changing."));
            return 0;
        }
        int rank = jobs.level(player.getUUID());
        long day = MazeRuntime.dayNumber(level);
        final ServerPlayer subject = patient;
        if (rank >= 3 && MazeJobs.cureReady(player.getUUID(), day, rank)) {
            MazeJobs.spendCure(player.getUUID(), day);
            MazeSting.cure(level, subject);
            jobs.award(player, MazeJobs.MEDJACK, 20);
            src.sendSuccess(() -> Component.literal(
                    "§a✚ You pulled " + subject.getGameProfile().getName() + " back."), false);
            return 1;
        }
        int seconds = (rank >= 2 ? 45 : 30) + steady * 10;
        if (!MazeSting.extend(level, subject, seconds)) {
            src.sendFailure(Component.literal("Too late. It has already taken."));
            return 0;
        }
        jobs.award(player, MazeJobs.MEDJACK, 8);
        src.sendSuccess(() -> Component.literal(
                "§a✚ You bought " + subject.getGameProfile().getName()
                        + " §f" + seconds + "s§a."), false);
        return 1;
    }

    /**
     * The skill sheet.
     *
     * <p>Shows only your own trade's skills. A list of twelve things you cannot
     * take is a list nobody reads, and the point of the sheet is the decision
     * between three of them.
     */
    private static int skills(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        MazeSkills skills = MazeSkills.get(src.getServer());
        String job = jobs.jobOf(player.getUUID());
        if (job == null) {
            src.sendFailure(Component.literal("Take a job first. §8/maze job"));
            return 0;
        }
        int spare = skills.available(jobs, player.getUUID(), job);
        int need = MazeSkills.costPerPoint(job);
        int have = jobs.xpOf(player.getUUID(), job);
        src.sendSuccess(() -> Component.literal(
                MazeJobs.display(job) + " §8— §f" + spare + "§7 point" + (spare == 1 ? "" : "s")
                        + " spare §8(" + (have % need) + "/" + need + " toward the next)"), false);
        for (MazeSkills.Skill s : MazeSkills.forJob(job)) {
            int rank = skills.rank(player.getUUID(), s.id());
            String bar = "§a" + "●".repeat(rank) + "§8" + "○".repeat(MazeSkills.MAX_RANK - rank);
            src.sendSuccess(() -> Component.literal(
                    "  " + bar + " §f" + s.display() + " §8/maze learn " + s.id()), false);
            src.sendSuccess(() -> Component.literal("      §7" + s.ranks()[
                    Math.min(rank, MazeSkills.MAX_RANK - 1)]), false);
        }
        src.sendSuccess(() -> Component.literal(
                "§8/maze forget puts every point in this trade back, free."), false);
        return 1;
    }

    private static int learn(CommandSourceStack src, String id) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        MazeSkills.Skill skill = MazeSkills.byId(id);
        if (skill == null) {
            src.sendFailure(Component.literal("No such skill. §8/maze skills"));
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        MazeSkills skills = MazeSkills.get(src.getServer());
        String why = skills.learn(jobs, player.getUUID(), skill);
        if (why != null) {
            src.sendFailure(Component.literal(why));
            return 0;
        }
        int rank = skills.rank(player.getUUID(), skill.id());
        src.sendSuccess(() -> Component.literal(
                "§a✦ " + skill.display() + " §f" + rank + "§7 — " + skill.ranks()[rank - 1]), false);
        return 1;
    }

    private static int forget(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        String job = jobs.jobOf(player.getUUID());
        if (job == null) {
            src.sendFailure(Component.literal("You have no trade to forget."));
            return 0;
        }
        int back = MazeSkills.get(src.getServer()).forget(player.getUUID(), job);
        src.sendSuccess(() -> Component.literal(
                "§7Unlearned. §f" + back + "§7 point" + (back == 1 ? "" : "s") + " back."), false);
        return 1;
    }

    /**
     * Rolling bandages.
     *
     * <p>Made at a command rather than a bench because a bandage that needs a
     * registered item needs a model and a texture this mod does not have, and the
     * serum already established that a potion is the honest way to ship a
     * consumable here. It also means the Med-jack's advantage can live in the
     * making rather than in a second recipe nobody would find.
     */
    private static int bandage(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null || !isMaze(player.level())) {
            src.sendFailure(Component.literal("Only in the maze."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        if (MazeBandage.count(player, net.minecraft.world.item.Items.STRING) < MazeBandage.STRING_COST
                || MazeBandage.count(player, net.minecraft.world.item.Items.PAPER) < MazeBandage.PAPER_COST) {
            src.sendFailure(Component.literal(
                    "Not enough to work with — §f" + MazeBandage.STRING_COST + " string §7and §f"
                            + MazeBandage.PAPER_COST + " paper§7."));
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        boolean medjack = jobs.is(player.getUUID(), MazeJobs.MEDJACK);
        MazeBandage.take(player, net.minecraft.world.item.Items.STRING, MazeBandage.STRING_COST);
        MazeBandage.take(player, net.minecraft.world.item.Items.PAPER, MazeBandage.PAPER_COST);

        int made = medjack ? MazeBandage.MEDJACK_YIELD : MazeBandage.YIELD;
        int dressing = MazeSkills.rankOf(level, player.getUUID(), "dressing");
        for (int i = 0; i < made; i++) {
            player.getInventory().placeItemBackInInventory(MazeBandage.create(dressing));
        }
        if (medjack) {
            jobs.award(player, MazeJobs.MEDJACK, 2);
        }
        src.sendSuccess(() -> Component.literal(
                "§f✚ " + made + " bandages." + (medjack ? " §8Half again, because you know how." : "")), false);
        return 1;
    }

    /**
     * Drawing on the Glade's stores.
     *
     * <p>Bread rather than the raw crop, because the Track-hoe's work should be
     * worth more coming out of the larder than it was going in - otherwise the
     * shared store is a worse inventory and nobody uses it.
     */
    private static int rations(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        MazeJobs jobs = MazeJobs.get(src.getServer());
        int provisions = MazeSkills.rankOf(src.getLevel(), player.getUUID(), "provisions");
        int cost = provisions >= 3 ? 3 : 4;
        if (!jobs.draw(cost)) {
            src.sendFailure(Component.literal(
                    "The larder is near empty — §f" + jobs.larder()
                            + "§7. Somebody has to work the field."));
            return 0;
        }
        player.getInventory().placeItemBackInInventory(
                new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.BREAD, 3 + provisions));
        src.sendSuccess(() -> Component.literal(
                "§7Rations drawn. §8The larder holds " + jobs.larder() + "."), false);
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
