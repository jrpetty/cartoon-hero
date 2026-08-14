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
        // On a raid night the breach in the Glade wall takes blocks: plugging
        // it is the intended play, and the wall ring is otherwise outside the
        // buildable ground. For a Builder it is also the trade: each block in
        // the hole counts toward the day's work (the quota clamps it) and pays
        // the ladder - a raid night is the Builder's biggest night.
        if (MazeRaid.placeable(event.getPos())) {
            if (event.getEntity() instanceof ServerPlayer builder) {
                MazeJobs jobs = MazeJobs.get(level);
                if (jobs.is(builder.getUUID(), MazeJobs.BUILDER)) {
                    jobs.award(builder, MazeJobs.BUILDER, 4);
                    MazeDayWork.get(level).add(level, builder, MazeJobs.BUILDER, 1);
                }
            }
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
            // A soul torch in a corridor is a waypoint: it goes on every
            // Runner's Chart for as long as it stands.
            if (player != null && (block == net.minecraft.world.level.block.Blocks.SOUL_TORCH
                    || block == net.minecraft.world.level.block.Blocks.SOUL_WALL_TORCH)) {
                MazeWaypoints.get(level).planted(level, player, event.getPos());
            }
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
                // Union chart only: the maze is one layout with moving doors
                // now, and a chart named after the night would be a new chart
                // every day, each stale by morning.
                charts.chart(player.getUUID(), cellX, cellZ, null);
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
        countFood(level, p, at, state);
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
                || block == net.minecraft.world.level.block.Blocks.MELON
                || block == net.minecraft.world.level.block.Blocks.PUMPKIN
                || block instanceof net.minecraft.world.level.block.SugarCaneBlock
                || block instanceof net.minecraft.world.level.block.NetherWartBlock
                || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                || block instanceof net.minecraft.world.level.block.CocoaBlock;
    }

    /**
     * A day's farming, counted where it actually happens.
     *
     * <p>Read off the block's real loot table rather than guessed, for the same
     * reason the double harvest is: a crop this code has never heard of still
     * counts correctly, and a hand-written table would drift the first time a
     * crop changed. The Track-hoe's own doubling is counted too - that yield is
     * real food in the larder, and paying for it is the point of the perk.
     */
    private static void countFood(ServerLevel level, ServerPlayer p, net.minecraft.core.BlockPos at,
                                  net.minecraft.world.level.block.state.BlockState state) {
        int food = 0;
        for (net.minecraft.world.item.ItemStack stack
                : net.minecraft.world.level.block.Block.getDrops(state, level, at,
                        level.getBlockEntity(at), p, p.getMainHandItem())) {
            if (!stack.isEmpty() && MazeDayWork.isFood(stack.getItem())) {
                // Doubled, because the Track-hoe's harvest bonus lands too.
                food += stack.getCount() * 2;
            }
        }
        if (food > 0) {
            MazeDayWork.get(level).add(level, p, MazeJobs.TRACKHOE, food);
        }
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

    /**
     * The dial on the Chart Floor.
     *
     * <p>A block you use rather than a command, because the floor is a place five
     * people stand round and the person turning the page should be visibly the
     * person turning the page.
     */
    @SubscribeEvent
    public static void onUseBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        // The event fires once per hand, and cancelling the main-hand pass does
        // not stop the off-hand one. Every interactive block in here was being
        // clicked twice per click: the chart dial turned two pages, the lens
        // skipped a zoom level, and the desks opened their screen twice in the
        // same frame. One guard fixes all of them at once.
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        // The Dead Glade's charts. The one thing out there worth walking to
        // that is not the way out.
        if (event.getPos().equals(DeadGlade.lectern())) {
            DeadGlade.readCharts(level, p);
            event.setCanceled(true);
            return;
        }
        // The trade board: right-click a post to read the trade and sign on.
        // No command, no chat - you click RUNNER and the screen asks if you
        // are sure, the way choosing who you are for a week deserves.
        String post = tradePostAt(event.getPos());
        if (post != null) {
            com.jrpetty.aztecabyss.network.ModNetworking.sendTradeBoard(p, post);
            level.playSound(null, event.getPos(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 1.1F);
            event.setCanceled(true);
            return;
        }
        // The trade desk and the order desk, so neither screen needs a command.
        if (MazeStations.onUse(level, p, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * Which trade's post this position belongs to, or null.
     *
     * <p>The whole post answers - the log column, the trade sign and the
     * roster sign under it - because "click the sign" should not mean "click
     * the exact sign, not the wood it hangs on".
     */
    private static String tradePostAt(net.minecraft.core.BlockPos at) {
        int oz = MazeData.SPAWN_Z + 5;
        if (at.getZ() != oz && at.getZ() != oz + 1) {
            return null;
        }
        int y = at.getY() - MazeData.FLOOR_Y;
        if (y < 0 || y > 4) {
            return null;
        }
        int ox = MazeData.SPAWN_X - 8;
        for (int i = 0; i < MazeJobs.ALL.size(); i++) {
            if (at.getX() == ox + i * 3) {
                return MazeJobs.ALL.get(i);
            }
        }
        return null;
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
        if (level.getGameTime() % 20L == 0L) {
            tickDeathKicks(level);
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
                .then(Commands.literal("skills")
                        .executes(ctx -> skills(ctx.getSource()))
                        .then(Commands.literal("text").executes(ctx -> skillsText(ctx.getSource()))))
                .then(Commands.literal("learn")
                        .then(Commands.argument("skill", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> learn(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "skill")))))
                .then(Commands.literal("forget").executes(ctx -> forget(ctx.getSource())))
                .then(Commands.literal("bandage").executes(ctx -> bandage(ctx.getSource())))
                .then(Commands.literal("order")
                        .executes(ctx -> orderScreen(ctx.getSource()))
                        .then(Commands.literal("text").executes(ctx -> orderSheet(ctx.getSource())))
                        .then(Commands.literal("clear").executes(ctx -> orderClear(ctx.getSource())))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> orderCancel(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item")))))
                        .then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> orderAdd(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item"), 1))
                                .then(Commands.argument("qty", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> orderAdd(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "qty"))))))
                .then(Commands.literal("work").executes(ctx -> workSheet(ctx.getSource())))
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
        src.sendSuccess(() -> Component.literal(
                "§8The Chart Floor in the Glade shows the same thing, walkable, live."), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // The requisition slate
    // ------------------------------------------------------------------

    /**
     * The catalogue and what you have already committed.
     *
     * <p>Prices and budget on the same screen on purpose. A shop that makes you
     * remember what you can afford while you read what things cost is a shop
     * nobody uses properly.
     */
    /**
     * Opens the slate.
     *
     * <p>The chat sheet is still there under {@code /maze order text}, for the
     * same reason the trade sheet kept its own: a printed list is the only
     * version you can read back after the screen is closed, and it is the only
     * version that works if somebody is looking over your shoulder.
     */
    private static int orderScreen(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || !isMaze(src.getLevel())) {
            src.sendFailure(Component.literal("Only in the maze."));
            return 0;
        }
        com.jrpetty.aztecabyss.network.ModNetworking.sendOrders(player);
        return 1;
    }

    /**
     * One click on the requisition screen, re-checked from scratch.
     *
     * <p>Everything the command path checks is checked again here - that they
     * are in the maze, that the id is real, that the budget covers it - because
     * a packet is not a button press. The screen is then re-sent whatever
     * happened, so a refused click leaves the client showing the truth rather
     * than its own guess.
     */
    public static void onOrderClick(ServerPlayer player, String id, int delta) {
        if (!(player.level() instanceof ServerLevel level) || !isMaze(level)) {
            return;
        }
        MazeOrders orders = MazeOrders.get(level);
        if (com.jrpetty.aztecabyss.network.RequisitionOrderPayload.CLEAR.equals(id)) {
            orders.clear(player.getUUID());
            com.jrpetty.aztecabyss.network.ModNetworking.sendOrders(player);
            return;
        }
        MazeOrders.Entry e = MazeOrders.entry(id);
        if (e == null) {
            // Not a catalogue line. Say so rather than silently redrawing, which
            // is how a renamed entry would sit broken for a month.
            player.displayClientMessage(Component.literal(
                    "§cThe Box has no line called '" + id + "'."), false);
            com.jrpetty.aztecabyss.network.ModNetworking.sendOrders(player);
            return;
        }
        if (delta > 0) {
            int qty = Math.min(delta, 64);
            String why = orders.add(level, player.getUUID(), e, qty);
            if (why == null) {
                announceOrder(level, player, e, qty);
            } else {
                // The refusal used to be computed, returned, and thrown away -
                // an over-budget click just silently redrew the screen, which
                // reads as the button being broken rather than the pot empty.
                player.displayClientMessage(Component.literal("§c" + why), true);
            }
        } else if (delta < 0) {
            orders.take(player.getUUID(), e.id(), -delta);
        }
        com.jrpetty.aztecabyss.network.ModNetworking.sendOrders(player);
    }

    private static int orderSheet(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || !isMaze(src.getLevel())) {
            src.sendFailure(Component.literal("Only in the maze."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        MazeOrders orders = MazeOrders.get(level);
        int pool = MazeOrders.pool(level);
        int left = MazeOrders.remaining(level);

        src.sendSuccess(() -> Component.literal(
                "§6— THE GLADE'S SLATE — §f" + left + "§7 of §f" + pool + "§7 credits left"), false);
        src.sendSuccess(() -> Component.literal(
                "§8" + MazeOrders.fromHeads(level) + " for " + orders.heads() + " heads §8+ "
                        + MazeDayWork.totalCredits(level) + " day's work §8+ "
                        + orders.totalBounty() + " bounties"), false);

        var slate = orders.slate(player.getUUID());
        if (!slate.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7On order:"), false);
            for (var line : slate.entrySet()) {
                MazeOrders.Entry e = MazeOrders.entry(line.getKey());
                if (e != null) {
                    src.sendSuccess(() -> Component.literal("  §f" + line.getValue() + "× §7"
                            + e.display() + " §8(" + (e.cost() * line.getValue()) + ")"), false);
                }
            }
        }
        src.sendSuccess(() -> Component.literal(
                "§7Catalogue §8— /maze order <id> [qty]"), false);
        StringBuilder row = new StringBuilder();
        int n = 0;
        for (MazeOrders.Entry e : MazeOrders.catalogue()) {
            row.append("§f").append(e.id()).append(" §8").append(e.cost()).append("§7  ");
            if (++n % 5 == 0) {
                String text = row.toString();
                src.sendSuccess(() -> Component.literal("  " + text), false);
                row.setLength(0);
            }
        }
        if (row.length() > 0) {
            String text = row.toString();
            src.sendSuccess(() -> Component.literal("  " + text), false);
        }
        src.sendSuccess(() -> Component.literal(
                "§8No weapons, tools or armour — order the materials and make them."), false);
        return 1;
    }

    private static int orderAdd(CommandSourceStack src, String id, int qty) {
        ServerPlayer player = src.getPlayer();
        if (player == null || !isMaze(src.getLevel())) {
            src.sendFailure(Component.literal("Only in the maze."));
            return 0;
        }
        MazeOrders.Entry e = MazeOrders.entry(id);
        if (e == null) {
            src.sendFailure(Component.literal("Nothing called that. §8/maze order"));
            return 0;
        }
        ServerLevel level = src.getLevel();
        MazeOrders orders = MazeOrders.get(level);
        String why = orders.add(level, player.getUUID(), e, qty);
        if (why != null) {
            src.sendFailure(Component.literal(why));
            return 0;
        }
        announceOrder(level, player, e, qty);
        int left = MazeOrders.remaining(level);
        src.sendSuccess(() -> Component.literal(
                "§a+ §f" + qty + "× " + e.display() + " §8(" + (e.cost() * qty) + ") §7— "
                        + left + " left in the pot"), false);
        return 1;
    }

    /**
     * Telling the Glade what somebody just spent their shared money on.
     *
     * <p>The pot belongs to everybody, so anybody can commit it - and the only
     * thing standing between that and one person emptying it on diamonds before
     * the others wake up is that everyone can see it happen. Visibility is the
     * arbitration, which is how it would work down there anyway. A cap would be
     * safer and would also stop the group deliberately pooling everything into
     * one serum, which is the main reason to have a shared pot at all.
     */
    private static void announceOrder(ServerLevel level, ServerPlayer who,
                                      MazeOrders.Entry e, int qty) {
        int cost = e.cost() * qty;
        int left = MazeOrders.remaining(level);
        for (ServerPlayer p : level.players()) {
            if (p == who) {
                continue;
            }
            p.displayClientMessage(Component.literal(
                    "§7▸ §f" + who.getGameProfile().getName() + "§7 put §f" + cost
                            + "§7 on " + qty + "× " + e.display()
                            + " §8(" + left + " left)"), false);
        }
    }

    /**
     * How your day is going, in the only terms the pool cares about.
     *
     * <p>A quota you cannot see is a quota nobody works toward. This is the
     * screen for "am I nearly there", and it is deliberately blunt about the
     * fact that the credits are the Glade's rather than yours.
     */
    private static int workSheet(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || !isMaze(src.getLevel())) {
            src.sendFailure(Component.literal("Only in the maze."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        String job = MazeJobs.get(level).jobOf(player.getUUID());
        if (job == null) {
            src.sendFailure(Component.literal("Take a trade first. §8/maze job"));
            return 0;
        }
        MazeDayWork work = MazeDayWork.get(level);
        int done = work.unitsOf(player.getUUID());
        int quota = MazeDayWork.quotaFor(job);
        int credits = work.creditsOf(level, player.getUUID());
        int max = MazeDayWork.maxCredits();

        src.sendSuccess(() -> Component.literal(
                "§6— YOUR DAY — §7" + MazeJobs.display(job)), false);
        src.sendSuccess(() -> Component.literal(
                "§f" + done + "§7/§f" + quota + " §8" + MazeDayWork.unitName(job)), false);
        // A bar, because a fraction is a number and a bar is a feeling.
        int filled = quota <= 0 ? 0 : Math.min(20, done * 20 / quota);
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < 20; i++) {
            if (i == filled) {
                bar.append("§8");
            }
            bar.append('|');
        }
        src.sendSuccess(() -> Component.literal(bar.toString()), false);
        src.sendSuccess(() -> Component.literal(
                "§7Worth §a+" + credits + "§7 of a possible §f" + max
                        + "§7 to the Glade's pot."), false);
        src.sendSuccess(() -> Component.literal(
                "§8The pot stands at " + MazeOrders.remaining(level) + " of "
                        + MazeOrders.pool(level) + ". §8/maze order"), false);
        int carrying = MazeNotes.carrying(player.getUUID());
        if (carrying > 0) {
            // The number a Runner actually weighs "push on or turn back" against.
            src.sendSuccess(() -> Component.literal(
                    "§b✎ Carrying §f" + carrying + "§b cells unfiled §8— they bank at the door,"
                            + " and fall where you fall."), false);
        }
        return 1;
    }

    private static int orderCancel(CommandSourceStack src, String id) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        int removed = MazeOrders.get(src.getLevel()).cancel(player.getUUID(), id);
        if (removed == 0) {
            src.sendFailure(Component.literal("That is not on your slate."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("§7Taken off — §f" + removed + "§7 refunded."), false);
        return 1;
    }

    private static int orderClear(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            return 0;
        }
        MazeOrders.get(src.getLevel()).clear(player.getUUID());
        src.sendSuccess(() -> Component.literal("§7Slate wiped."), false);
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
        if (player == null) {
            return 0;
        }
        chooseTrade(player, job);
        return 1;
    }

    /**
     * Signs somebody on to a trade - the confirm button on the board screen,
     * and the old command, both land here.
     *
     * <p>The job id is re-checked against the real list because it arrived
     * from a client; a made-up trade is refused rather than stored.
     */
    public static void chooseTrade(ServerPlayer player, String job) {
        if (player.getServer() == null || !MazeJobs.ALL.contains(job)) {
            return;
        }
        MazeJobs jobs = MazeJobs.get(player.getServer());
        if (!jobs.setJob(player.getUUID(), job)) {
            player.displayClientMessage(Component.literal("§7You already are one."), false);
            return;
        }
        int rank = jobs.levelOf(player.getUUID(), job);
        player.displayClientMessage(Component.literal(
                "§7You are a " + MazeJobs.display(job) + " §8lv" + rank), false);
        player.displayClientMessage(Component.literal(
                "  " + MazeJobs.perkLine(job, rank)), false);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (other != player) {
                other.displayClientMessage(Component.literal(
                        "§7" + player.getGameProfile().getName() + " is a "
                                + MazeJobs.display(job) + "§7 now."), false);
            }
        }
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
            // A person is worth more than a dressing. Three units either way -
            // buying somebody a minute is the same work as curing them, and
            // often the braver of the two.
            MazeDayWork.get(level).add(level, player, MazeJobs.MEDJACK, 3);
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
        MazeDayWork.get(level).add(level, player, MazeJobs.MEDJACK, 3);
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
        // The sheet is a screen now. Twelve lines of chat is not a place anybody
        // compares three options and picks one.
        com.jrpetty.aztecabyss.network.ModNetworking.sendSkills(player);
        return 1;
    }

    /**
     * A click on the trade sheet.
     *
     * <p>The screen names a skill and nothing else; every check the command made
     * is made again here, because a screen is a nicer way to ask a question and
     * not a reason to trust the answer. The sheet is re-sent afterwards whatever
     * happens, so the client is always looking at what the server actually
     * thinks - including when the answer was no.
     */
    public static void onSkillClick(ServerPlayer player, String skillId) {
        if (player.getServer() == null) {
            return;
        }
        MazeJobs jobs = MazeJobs.get(player.getServer());
        MazeSkills skills = MazeSkills.get(player.getServer());
        String job = jobs.jobOf(player.getUUID());
        if (job == null) {
            return;
        }
        if (skillId == null || skillId.isEmpty()) {
            int back = skills.forget(player.getUUID(), job);
            player.displayClientMessage(Component.literal(
                    "§7Unlearned. §f" + back + "§7 point" + (back == 1 ? "" : "s") + " back."), false);
            com.jrpetty.aztecabyss.network.ModNetworking.sendSkills(player);
            return;
        }
        MazeSkills.Skill skill = MazeSkills.byId(skillId);
        if (skill == null) {
            com.jrpetty.aztecabyss.network.ModNetworking.sendSkills(player);
            return;
        }
        String why = skills.learn(jobs, player.getUUID(), skill);
        if (why != null) {
            player.displayClientMessage(Component.literal("§c" + why), true);
        } else {
            int rank = skills.rank(player.getUUID(), skill.id());
            player.displayClientMessage(Component.literal(
                    "§a✦ " + skill.display() + " §f" + rank + "§7 — " + skill.ranks()[rank - 1]), false);
            player.level().playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.6F);
        }
        com.jrpetty.aztecabyss.network.ModNetworking.sendSkills(player);
    }

    /** The old chat sheet, kept behind {@code /maze skills text} for servers with no client mod. */
    private static int skillsText(CommandSourceStack src) {
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
            // One unit per dressing, so a Med-jack who spends the morning at the
            // bench is paid for the morning rather than for the batch.
            MazeDayWork.get(level).add(level, player, MazeJobs.MEDJACK, made);
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
        // The reward is for surviving. Whatever this trip earned dies here -
        // except the survey, which does not die, it falls. Somebody can go and
        // get it, and that is the difference between a death and a place on the
        // map that now matters.
        if (player.level() instanceof ServerLevel sl) {
            MazeJobs.get(sl).forfeit(player);
            MazeNotes.drop(sl, player);
        }
        MazeRuns.onDeath(player);
        MazeRace.dropOut(level, player.getUUID());
        MazeSting.clear(player.getUUID());
        DIED_IN_MAZE.add(player.getUUID());
        // The walls put you out - all the way out. On a dedicated server the
        // death ends at the server door: four seconds to read the red screen,
        // then disconnected, with your record as the parting words. Dying in
        // a group game with permanent death should not leave you standing in
        // a lobby watching other people's game; it should put you OUT, and
        // the disconnect screen is the one place the game can say what your
        // run amounted to with your full attention on it.
        if (level.getServer().isDedicatedServer()) {
            int days = MazeClock.get(level.getServer()).day() + 1;
            int pct = MazeCharts.get(level.getServer()).myPercent(player.getUUID());
            KICK_AT.put(player.getUUID(), level.getGameTime() + 80);
            KICK_WORDS.put(player.getUUID(), Component.literal(
                    "§4§lTHE MAZE TOOK YOU\n\n"
                            + "§7You lasted §f" + days + (days == 1 ? " day" : " days")
                            + "§7 in the corridors"
                            + (pct > 0 ? ", and charted §f" + pct + "%§7 of the maze" : "")
                            + ".\n"
                            + "§7Your charts fell where you died. §8Someone can still"
                            + " recover them.\n\n"
                            + "§8Die and the walls put you out. There is no second try"
                            + " at a run."));
        }
    }

    /** Who died in the maze and is owed a trip out of it on respawn. */
    private static final java.util.Set<java.util.UUID> DIED_IN_MAZE = new java.util.HashSet<>();

    /** Grievers killed per player this game, for the epitaphs and the hall. */
    private static final java.util.Map<java.util.UUID, Integer> GRIEVER_KILLS
            = new java.util.HashMap<>();

    public static int grieverKills(java.util.UUID who) {
        return GRIEVER_KILLS.getOrDefault(who, 0);
    }

    /** A new game; the tallies are the old game's. */
    public static void clearKills() {
        GRIEVER_KILLS.clear();
    }

    /** Deaths owed a walk to the server door: when, and with what words. */
    private static final java.util.Map<java.util.UUID, Long> KICK_AT = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Component> KICK_WORDS = new java.util.HashMap<>();

    /** Runs the pending disconnects. Called once a second from the level tick. */
    private static void tickDeathKicks(ServerLevel level) {
        if (KICK_AT.isEmpty()) {
            return;
        }
        var it = KICK_AT.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (level.getGameTime() < e.getValue()) {
                continue;
            }
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            Component words = KICK_WORDS.remove(e.getKey());
            it.remove();
            if (p != null) {
                p.connection.disconnect(words != null ? words
                        : Component.literal("§4The maze took you."));
            }
        }
    }

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
        // The barb survives. Two of them and a haft make the Fang, so the
        // maze's own weapon is paid for in the hardest thing the maze has.
        mob.spawnAtLocation(new net.minecraft.world.item.ItemStack(
                com.jrpetty.aztecabyss.registry.ModItems.GRIEVER_STINGER.get()));
        boolean dropped = MazeSerum.maybeDrop(level, mob, net.minecraft.util.RandomSource.create());
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            GRIEVER_KILLS.merge(killer.getUUID(), 1, Integer::sum);
            MazeAdvancements.grant(killer, MazeAdvancements.GRIEVER_SLAYER);
            if (dropped) {
                MazeAdvancements.grant(killer, MazeAdvancements.SERUM);
            }
            // Killing one is the hardest thing anybody does in here and it paid a
            // serum and an advancement. Now it pays in the currency that decides
            // what tomorrow looks like - and it pays outside the charting cap, so
            // a well-mapped Glade is still rewarded for doing it.
            int bounty = com.jrpetty.aztecabyss.config.AbyssConfig.MAZE_GRIEVER_BOUNTY.get();
            if (bounty > 0) {
                MazeOrders.get(level).addBonus(killer.getUUID(), bounty);
                for (ServerPlayer p : level.players()) {
                    p.displayClientMessage(Component.literal(
                            "§6§l✦ " + killer.getGameProfile().getName()
                                    + " KILLED A GRIEVER §r§7— §f+" + bounty
                                    + "§7 points on tomorrow's slate"), false);
                    level.playSound(null, p.blockPosition(),
                            net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.8F);
                }
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
