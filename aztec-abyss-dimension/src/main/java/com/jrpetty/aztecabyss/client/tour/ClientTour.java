package com.jrpetty.aztecabyss.client.tour;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The tour: the real game, driven start to finish, photographed as it goes.
 *
 * <p>Everything else that verifies this mod runs on a server with no player on
 * it. That catches a great deal - a boot that throws, a ruleset that parses
 * wrong, a codec whose two ends disagree - and it cannot catch anything that
 * needs somebody to be <em>there</em>: a screen that never opens, a HUD drawn in
 * the wrong place, a round that never starts, a run that cannot be finished.
 * Those are the mistakes players actually meet.
 *
 * <p>So this is a player. With {@code -Daztecabyss.tour=true} the client waits
 * for the title screen, creates a fresh singleplayer world, and then plays: it
 * enters the Temple through the same portal code a player's portal calls,
 * fights a round, extracts, runs the Bridge, goes into the maze, picks a trade,
 * opens the hub, watches the doors close, goes out at night, escapes, and
 * visits the Creator - taking a screenshot at every stage and writing down
 * whether each stage actually happened. CI runs it under a virtual display and
 * publishes the pictures.
 *
 * <h2>How it drives the game</h2>
 *
 * <p>Through the mod's own entry points, never around them. Screens are opened
 * by having the server send the packet it always sends, so a screenshot of a
 * screen is also proof the packet arrived and the screen built. Choices are
 * made by pressing the screen's own buttons and keys. Where the tour has to
 * cheat - fast-forwarding the maze clock, killing a wave instantly, keeping
 * itself alive - it says so in the step name, and it cheats only on time and
 * on its own survival, never on whether a feature works.
 *
 * <p>Inert unless the system property is set. A production client never
 * subscribes to anything here doing work.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientTour {

    public static final boolean ENABLED = Boolean.getBoolean("aztecabyss.tour");

    private static final Logger LOG = LogUtils.getLogger();

    /** Thirty-five minutes, then stop whatever is happening and report. */
    private static final long HARD_LIMIT_MS = 35L * 60L * 1000L;

    private ClientTour() {
    }

    // ------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------

    /** One stage of the tour: do something, wait for it to be true, photograph it. */
    static final class Step {
        final String name;
        Consumer<Minecraft> act;
        Consumer<Minecraft> every;
        int everyTicks = 20;
        BooleanSupplier until;
        int minTicks = 20;
        int timeout = 600;
        String shot;

        Step(String name) {
            this.name = name;
        }

        Step act(Consumer<Minecraft> a) {
            this.act = a;
            return this;
        }

        /** Runs repeatedly while the step waits - for things that must keep happening. */
        Step every(int ticks, Consumer<Minecraft> a) {
            this.everyTicks = ticks;
            this.every = a;
            return this;
        }

        Step until(BooleanSupplier u, int timeoutTicks) {
            this.until = u;
            this.timeout = timeoutTicks;
            return this;
        }

        Step hold(int ticks) {
            this.minTicks = ticks;
            return this;
        }

        Step shot(String file) {
            this.shot = file;
            return this;
        }
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1;
    private static int titleTicks;
    private static boolean entered;
    private static int ticksInStep;
    private static int framesInStep;
    /** Frame and tick counts when this step's condition first held, or -1. */
    private static int framesAtDone = -1;
    private static int ticksAtDone = -1;
    private static long startedAt;
    private static boolean finished;
    private static long tickCount;
    /** Whether to keep the tour's player alive and fed. Off for the deliberate death. */
    private static volatile boolean guard = true;

    private static final List<String> REPORT = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger PASSED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();

    /** Facts the server reports back for the client to wait on. */
    private static volatile boolean roundCleared;
    private static volatile boolean flag;

    private static Step step(String name) {
        Step s = new Step(name);
        STEPS.add(s);
        return s;
    }

    // ------------------------------------------------------------------
    // The tour itself
    // ------------------------------------------------------------------

    private static void build() {
        STEPS.clear();

        step("create-world").act(mc -> createWorld(mc))
                .until(() -> inWorld() && Minecraft.getInstance().screen == null, 9000)
                .hold(60);
        step("settle").act(mc -> server("settle", sp -> {
                    sp.serverLevel().setDayTime(6000L);
                    sp.setGameMode(GameType.SURVIVAL);
                }))
                .hold(120);

        // ---- The portal picker ------------------------------------------------
        step("picker").act(mc -> server("picker",
                        com.jrpetty.aztecabyss.network.ModNetworking::sendOpenMapPicker))
                .until(() -> screenIs("MapSelectScreen"), 200).hold(40).shot("01_picker");
        step("picker-close").act(mc -> mc.setScreen(null)).hold(10);

        // ---- The Temple ---------------------------------------------------------
        step("temple-choose").act(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.jrpetty.aztecabyss.network.MapSelectPayload(0))).hold(20);
        step("temple-portal-confirm").act(mc -> server("portal-confirm",
                com.jrpetty.aztecabyss.event.PortalEvents::enterFromPortal)).hold(30);
        step("temple-enter").act(mc -> server("portal-enter",
                        com.jrpetty.aztecabyss.event.PortalEvents::enterFromPortal))
                .until(() -> inDimension(AztecAbyssConstants.ABYSS_LEVEL_KEY)
                        && Minecraft.getInstance().screen == null, 2400)
                .hold(160).shot("10_temple_arrival");
        step("temple-round").until(() -> com.jrpetty.aztecabyss.client.ClientAbyssState.getRound() >= 1
                        && com.jrpetty.aztecabyss.client.ClientAbyssState.getEnemiesRemaining() > 0, 3600)
                .hold(220).shot("11_temple_round");
        step("temple-clear [tour kills the wave]").act(mc -> roundCleared = false)
                .every(20, mc -> killWave())
                .until(() -> roundCleared, 4800).hold(40);
        step("temple-glyph").act(mc -> server("glyph", sp -> {
                    // Standing on the floor six blocks south of the glyph, not
                    // in it: the glyph is the floor block itself, and the first
                    // tour put its camera inside the stone.
                    var g = com.jrpetty.aztecabyss.round.RoundManager.game().getMap().extraction();
                    look(sp, g.getX() + 0.5, g.getY() + 1, g.getZ() + 6.5, 180.0F, 28.0F);
                }))
                .hold(80).shot("12_temple_between_rounds");
        step("temple-extract").act(mc -> server("extract", sp -> {
                    var g = com.jrpetty.aztecabyss.round.RoundManager.game().getMap().extraction();
                    look(sp, g.getX() + 0.5, g.getY() + 0.1, g.getZ() + 0.5, sp.getYRot(), 20.0F);
                }))
                .until(() -> screenIs("RunRecapScreen"), 1600).hold(40).shot("13_temple_recap");
        step("recap-close").act(mc -> mc.setScreen(null)).hold(40);

        // ---- The Bridge ---------------------------------------------------------
        step("bridge-choose").act(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.jrpetty.aztecabyss.network.MapSelectPayload(1))).hold(20);
        step("bridge-portal-confirm").act(mc -> server("portal-confirm",
                com.jrpetty.aztecabyss.event.PortalEvents::enterFromPortal)).hold(30);
        step("bridge-enter").act(mc -> server("portal-enter",
                        com.jrpetty.aztecabyss.event.PortalEvents::enterFromPortal))
                .until(() -> inDimension(AztecAbyssConstants.ABYSS_LEVEL_KEY)
                        && Minecraft.getInstance().screen == null, 2400)
                .hold(160).shot("20_bridge_arrival");
        step("bridge-round").until(() -> com.jrpetty.aztecabyss.client.ClientAbyssState.getRound() >= 1
                        && com.jrpetty.aztecabyss.client.ClientAbyssState.getEnemiesRemaining() > 0, 3600)
                .hold(300).shot("21_bridge_round");
        step("bridge-death [tour dies on purpose]").act(mc -> {
                    guard = false;
                    server("die", sp -> {
                        sp.removeAllEffects();
                        sp.hurt(sp.damageSources().genericKill(), Float.MAX_VALUE);
                    });
                })
                .until(() -> screenIs("RunRecapScreen"), 1600).hold(40).shot("22_bridge_death_recap");
        step("recap-close-2").act(mc -> {
                    mc.setScreen(null);
                    if (mc.player != null && !mc.player.isAlive()) {
                        mc.player.respawn();
                    }
                })
                .until(() -> Minecraft.getInstance().player != null
                        && Minecraft.getInstance().player.isAlive(), 400)
                .hold(60);
        step("clear-lockout [tour lifts its own cooldown]").act(mc -> {
                    guard = true;
                    server("lockout", sp -> {
                        var rs = sp.getData(com.jrpetty.aztecabyss.registry.ModAttachments.RUN_STATE);
                        rs.setCooldownUntil(0L);
                        sp.setData(com.jrpetty.aztecabyss.registry.ModAttachments.RUN_STATE, rs);
                        com.jrpetty.aztecabyss.network.ModNetworking.sendCooldown(sp, 0L);
                    });
                }).hold(20);

        // ---- Records --------------------------------------------------------------
        step("records").act(mc -> server("records",
                        com.jrpetty.aztecabyss.network.ModNetworking::sendLeaderboards))
                .until(() -> screenIs("LeaderboardScreen"), 200).hold(40).shot("23_records");
        step("records-runs").act(mc -> press("runs"))
                .hold(30).shot("24_records_your_runs");
        step("records-close").act(mc -> mc.setScreen(null)).hold(10);

        // ---- The Maze -------------------------------------------------------------
        step("maze-enter").act(mc -> server("maze-enter",
                        com.jrpetty.aztecabyss.maze.MazeEvents::sendToMaze))
                .until(() -> inDimension(AztecAbyssConstants.MAZE_LEVEL_KEY)
                        && screenIs("MazeInductionScreen"), 9000)
                .hold(100).shot("30_maze_induction");
        step("maze-pick-runner").act(mc -> key(org.lwjgl.glfw.GLFW.GLFW_KEY_1))
                .hold(30).shot("31_maze_induction_runner");
        step("maze-come-up").act(mc -> key(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER))
                .until(() -> Minecraft.getInstance().screen == null, 200)
                .hold(140).shot("32_glade_day");
        step("hub").act(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.jrpetty.aztecabyss.network.RequestMazeHubPayload()))
                .until(() -> screenIs("MazeHubScreen"), 200).hold(40).shot("33_hub_status");
        step("hub-chart").act(mc -> press("Chart")).hold(30).shot("34_hub_chart");
        step("hub-close").act(mc -> mc.setScreen(null)).hold(10);
        step("trade-board").act(mc -> server("trade-board",
                        sp -> com.jrpetty.aztecabyss.network.ModNetworking.sendTradeBoard(sp, "builder")))
                .until(() -> screenIs("TradeBoardScreen"), 200).hold(30).shot("35_trade_board");
        step("skills").act(mc -> server("skills",
                        com.jrpetty.aztecabyss.network.ModNetworking::sendSkills))
                .until(() -> screenIs("SkillTreeScreen"), 200).hold(30).shot("36_skills");
        step("orders").act(mc -> server("orders",
                        com.jrpetty.aztecabyss.network.ModNetworking::sendOrders))
                .until(() -> screenIs("RequisitionScreen"), 200).hold(30).shot("37_orders");
        step("orders-close").act(mc -> mc.setScreen(null)).hold(10);

        // ---- What the maze looks like, from above and from inside ---------------
        step("maze-aerial [tour flies]").act(mc -> {
                    mc.options.hideGui = true;
                    mc.options.renderDistance().set(12);
                    server("aerial", sp -> {
                        sp.setGameMode(GameType.SPECTATOR);
                        double c = com.jrpetty.aztecabyss.maze.MazeData.SPAWN_X + 0.5;
                        double z = com.jrpetty.aztecabyss.maze.MazeData.SPAWN_Z + 70.5;
                        look(sp, c, com.jrpetty.aztecabyss.maze.MazeData.FLOOR_Y + 58, z, 180.0F, 38.0F);
                    });
                }).hold(420).shot("42_maze_aerial");
        step("maze-corridor").act(mc -> server("corridor", sp -> {
                    ServerLevel maze = sp.serverLevel();
                    int[] door = com.jrpetty.aztecabyss.maze.MazeBuilder.DOOR_CELLS[0];
                    BlockPos out = openFloor(maze, door[0], door[1] - 1);
                    if (out == null) {
                        throw new IllegalStateException("no open corridor outside the north door");
                    }
                    look(sp, out.getX() + 0.5, out.getY() + 0.6, out.getZ() + 0.5, 180.0F, -4.0F);
                })).hold(120).shot("43_maze_corridor");
        step("maze-ground").act(mc -> {
                    mc.options.hideGui = false;
                    mc.options.renderDistance().set(6);
                    server("ground", sp -> {
                        sp.setGameMode(GameType.SURVIVAL);
                        int[] door = com.jrpetty.aztecabyss.maze.MazeBuilder.DOOR_CELLS[0];
                        double x = door[0] * com.jrpetty.aztecabyss.maze.MazeData.CELL + 3.0;
                        double z = (door[1] + 1) * com.jrpetty.aztecabyss.maze.MazeData.CELL + 16.0;
                        look(sp, x, com.jrpetty.aztecabyss.maze.MazeData.FLOOR_Y + 1, z, 180.0F, 0.0F);
                    });
                }).hold(60);

        step("door-pose [tour fast-forwards to dusk]").act(mc -> server("dusk", sp -> {
                    ServerLevel maze = sp.serverLevel();
                    // Inside the Glade, sixteen blocks south of the north door, looking up at it.
                    int[] door = com.jrpetty.aztecabyss.maze.MazeBuilder.DOOR_CELLS[0];
                    double x = door[0] * com.jrpetty.aztecabyss.maze.MazeData.CELL + 3.0;
                    double z = (door[1] + 1) * com.jrpetty.aztecabyss.maze.MazeData.CELL + 16.0;
                    look(sp, x, com.jrpetty.aztecabyss.maze.MazeData.FLOOR_Y + 1, z, 180.0F, -16.0F);
                    var clock = com.jrpetty.aztecabyss.maze.MazeClock.get(maze);
                    int to = com.jrpetty.aztecabyss.maze.MazeClock.dayTicks() - clock.phase() - 40;
                    if (to > 0) {
                        clock.advance(to);
                    }
                })).hold(20);
        step("doors-closing").hold(95).shot("38_doors_closing");
        step("doors-sealed").hold(110).shot("39_doors_sealed");
        step("night-out").act(mc -> server("night", sp -> {
                    ServerLevel maze = sp.serverLevel();
                    int[] door = com.jrpetty.aztecabyss.maze.MazeBuilder.DOOR_CELLS[0];
                    BlockPos out = openFloor(maze, door[0], door[1] - 1);
                    if (out == null) {
                        throw new IllegalStateException("no open corridor outside the north door");
                    }
                    sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0, false, false, false));
                    look(sp, out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 180.0F, 0.0F);
                    BlockPos ahead = openFloor(maze, door[0], door[1] - 2);
                    if (ahead != null) {
                        com.jrpetty.aztecabyss.maze.Griever.raiderAt(maze, ahead);
                    }
                })).hold(120).shot("40_night_griever");
        step("griever-closeup [tour sees in the dark]").act(mc -> server("closeup", sp -> {
                    ServerLevel maze = sp.serverLevel();
                    sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
                    BlockPos at = sp.blockPosition();
                    // Four blocks ahead, held still and facing the camera, so
                    // the picture is of the thing and not of a blur.
                    var g = com.jrpetty.aztecabyss.maze.Griever.raiderAt(maze, at.north(5));
                    if (g == null) {
                        throw new IllegalStateException("no Griever to photograph");
                    }
                    g.setNoAi(true);
                    g.setInvulnerable(true);
                    g.setYRot(0.0F);
                    g.setYHeadRot(0.0F);
                    g.setYBodyRot(0.0F);
                    sp.teleportTo(maze, sp.getX(), sp.getY(), sp.getZ(), java.util.Set.of(), 180.0F, 18.0F);
                })).hold(80).shot("44_griever_closeup");
        step("griever-clear").act(mc -> server("clear-grievers", sp -> {
                    sp.removeEffect(MobEffects.NIGHT_VISION);
                    for (var m : com.jrpetty.aztecabyss.maze.Griever.loaded(sp.serverLevel())) {
                        m.discard();
                    }
                })).hold(20);
        step("maze-escape").act(mc -> server("escape", sp -> {
                    ServerLevel maze = sp.serverLevel();
                    var layout = com.jrpetty.aztecabyss.maze.MazeRuntime.todaysLayout(maze);
                    var exit = layout == null ? null : com.jrpetty.aztecabyss.maze.MazeData.exit(layout.exit());
                    if (exit == null) {
                        throw new IllegalStateException("no exit for today's layout");
                    }
                    BlockPos p = com.jrpetty.aztecabyss.maze.PortalAnnex.portalPos(exit);
                    sp.teleportTo(maze, p.getX() + 0.5, p.getY(), p.getZ() + 0.5,
                            java.util.Set.of(), sp.getYRot(), 0.0F);
                }))
                .until(() -> screenIs("MazeVictoryScreen"), 800).hold(60).shot("41_maze_victory");
        step("victory-close").act(mc -> mc.setScreen(null)).hold(60);

        // ---- Creator: build, check, test, save, publish, play, come home --------------
        step("creator").act(mc -> server("creator", sp -> {
                    String err = com.jrpetty.aztecabyss.engine.MapCreator.enter(sp, true);
                    if (err != null) {
                        throw new IllegalStateException(err);
                    }
                }))
                .until(() -> inDimension(AztecAbyssConstants.WORKSHOP_LEVEL_KEY), 600)
                .hold(160).shot("50_creator");
        step("creator-gate").act(mc -> mc.setScreen(new com.jrpetty.aztecabyss.client.CreatorPasswordScreen(false)))
                .until(() -> screenIs("CreatorPasswordScreen"), 100).hold(30).shot("53_creator_gate");
        step("creator-build [tour builds a small arena]").act(mc -> {
                    mc.setScreen(null);
                    server("build", ClientTour::buildTourArena);
                }).hold(60);
        step("creator-console").act(mc -> server("console", com.jrpetty.aztecabyss.engine.CreatorConsole::open))
                .until(() -> screenIs("CreatorConsoleScreen"), 200).hold(30).shot("54_creator_console");
        step("creator-check").act(mc -> press("Check the map")).hold(40).shot("55_creator_check");
        step("creator-playtest").act(mc -> press("Play-test"))
                .until(() -> com.jrpetty.aztecabyss.engine.EngineArena.isRunning()
                        && Minecraft.getInstance().screen == null, 600)
                .hold(140).shot("56_creator_playtest");
        step("creator-console-again").act(mc -> server("console", com.jrpetty.aztecabyss.engine.CreatorConsole::open))
                .until(() -> screenIs("CreatorConsoleScreen"), 200).hold(20);
        step("creator-stop-test").act(mc -> press("Stop the test"))
                .until(() -> !com.jrpetty.aztecabyss.engine.EngineArena.isRunning(), 400).hold(20);
        step("creator-publish-page").act(mc -> press("Publish")).hold(20);
        step("creator-save").act(mc -> {
                    type(0, "tour_arena");
                    press("Save");
                })
                .until(() -> textBoxes() >= 3, 400).hold(20);
        step("creator-details").act(mc -> {
                    type(1, "The Tour Yard");
                    type(2, "A small walled yard, built and published by the tour to prove the whole path.");
                    press("Save title");
                }).hold(40);
        step("creator-publish").act(mc -> press("Publish"))
                .until(() -> Minecraft.getInstance().getSingleplayerServer() != null
                        && com.jrpetty.aztecabyss.engine.PublishedMaps.byName(
                                Minecraft.getInstance().getSingleplayerServer(), "tour_arena") != null, 600)
                .hold(40).shot("57_creator_publish");
        step("creator-leave").act(mc -> press("Leave the Workshop"))
                .until(() -> inDimension(Level.OVERWORLD), 600).hold(40);

        step("picker-after").act(mc -> server("picker-after",
                        com.jrpetty.aztecabyss.network.ModNetworking::sendOpenMapPicker))
                .until(() -> screenIs("MapSelectScreen"), 200).hold(40).shot("51_picker_after_runs");
        step("player-maps").act(mc -> press("Player maps"))
                .until(() -> screenIs("PlayerMapsScreen"), 100).hold(30).shot("52_player_maps");
        step("play-published").act(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.jrpetty.aztecabyss.network.MapSelectPayload(
                                com.jrpetty.aztecabyss.network.MapSelectPayload.CUSTOM_BASE)))
                .until(() -> inDimension(AztecAbyssConstants.ABYSS_LEVEL_KEY)
                        && com.jrpetty.aztecabyss.engine.EngineArena.isRunning(), 1200)
                .hold(160).shot("58_published_run");
        step("published-home [tour ends the run]").act(mc -> server("end-run",
                        sp -> com.jrpetty.aztecabyss.engine.EngineArena.stop(true)))
                .until(() -> inDimension(Level.OVERWORLD), 600).hold(40);
        step("end").act(mc -> mc.setScreen(null)).hold(20);
    }

    /**
     * A small arena for the Creator steps: a paved yard, a low wall, and the
     * three markers a map needs to play - somewhere to arrive, somewhere the
     * horde comes from, and a way out - then the wand's two corners round it.
     */
    private static void buildTourArena(ServerPlayer sp) {
        ServerLevel shop = sp.serverLevel();
        int x0 = 8;
        int z0 = 8;
        int x1 = 24;
        int z1 = 24;
        int y = 4;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                shop.setBlock(new BlockPos(x, y, z), (edge
                        ? net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS
                        : net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE).defaultBlockState(), 3);
                if (edge) {
                    shop.setBlock(new BlockPos(x, y + 1, z),
                            net.minecraft.world.level.block.Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                }
            }
        }
        for (int[] c : new int[][]{{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
            shop.setBlock(new BlockPos(c[0], y + 2, c[1]),
                    net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState(), 3);
        }
        markerSign(shop, new BlockPos(16, y + 1, 20), "[spawn]", "");
        markerSign(shop, new BlockPos(16, y + 1, 11), "[horde]", "area=start");
        markerSign(shop, new BlockPos(21, y + 1, 21), "[extract]", "");
        com.jrpetty.aztecabyss.engine.BuildTools.setCorner(sp, new BlockPos(x0, y, z0), true);
        com.jrpetty.aztecabyss.engine.BuildTools.setCorner(sp, new BlockPos(x1, y + 4, z1), false);
        look(sp, 16.5, y + 1, z1 + 9.5, 180.0F, 24.0F);
    }

    private static void markerSign(ServerLevel level, BlockPos pos, String head, String second) {
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
            be.updateText(t -> t.setMessage(0, net.minecraft.network.chat.Component.literal(head))
                    .setMessage(1, net.minecraft.network.chat.Component.literal(second)), true);
        }
    }

    // ------------------------------------------------------------------
    // The driver
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (ENABLED) {
            framesInStep++;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        tickCount++;
        if (index < 0) {
            if (mc.screen instanceof TitleScreen && ++titleTicks > 60) {
                build();
                index = 0;
                entered = false;
                startedAt = System.currentTimeMillis();
                LOG.info("[TOUR] starting: {} steps", STEPS.size());
            }
            return;
        }
        if (guard && tickCount % 100L == 0L && mc.player != null && mc.getSingleplayerServer() != null) {
            server("guard", ClientTour::keepAlive);
        }
        if (index >= STEPS.size()) {
            finish(mc);
            return;
        }
        if (System.currentTimeMillis() - startedAt > HARD_LIMIT_MS) {
            fail(STEPS.get(index).name, "hard time limit reached");
            finish(mc);
            return;
        }
        Step s = STEPS.get(index);
        if (!entered) {
            // Set before acting: some actions (creating a world) block and run
            // client ticks of their own, which must not re-enter this step.
            entered = true;
            ticksInStep = 0;
            framesInStep = 0;
            framesAtDone = -1;
            ticksAtDone = -1;
            LOG.info("[TOUR] -> {}", s.name);
            if (s.act != null) {
                try {
                    s.act.accept(mc);
                } catch (Throwable t) {
                    fail(s.name, "action threw " + t);
                    LOG.error("[TOUR] action failed", t);
                }
            }
            return;
        }
        ticksInStep++;
        if (s.every != null && ticksInStep % Math.max(1, s.everyTicks) == 0) {
            try {
                s.every.accept(mc);
            } catch (Throwable t) {
                LOG.error("[TOUR] repeating action failed", t);
            }
        }
        boolean done;
        try {
            done = s.until == null || s.until.getAsBoolean();
        } catch (Throwable t) {
            done = false;
        }
        if (done && framesAtDone < 0) {
            framesAtDone = framesInStep;
            ticksAtDone = ticksInStep;
        }
        // A picture taken the tick a screen opens is a picture of the frame
        // before it - which is how the first tour photographed two loading
        // screens. The condition has to have held for several drawn frames.
        boolean settled = done && framesInStep - framesAtDone >= 10 && ticksInStep - ticksAtDone >= 5;
        if (settled && ticksInStep >= s.minTicks) {
            if (s.shot != null) {
                shoot(mc, s.shot);
            }
            pass(s.name, ticksInStep);
            next();
        } else if (s.until != null && ticksInStep > s.timeout) {
            fail(s.name, "timed out after " + ticksInStep + " ticks");
            shoot(mc, "FAIL_" + s.name.replaceAll("[^a-z0-9-]", "_"));
            next();
        }
    }

    private static void next() {
        index++;
        entered = false;
    }

    private static void finish(Minecraft mc) {
        finished = true;
        String result = "[TOUR] RESULT: " + PASSED.get() + " passed, " + FAILED.get() + " failed";
        REPORT.add(result);
        LOG.info(result);
        try {
            Files.writeString(new File(mc.gameDirectory, "tour-report.txt").toPath(),
                    String.join("\n", REPORT) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[TOUR] could not write the report", e);
        }
        mc.stop();
    }

    private static void pass(String name, int ticks) {
        PASSED.incrementAndGet();
        String line = "PASS  " + name + "  (" + ticks + " ticks)";
        REPORT.add(line);
        LOG.info("[TOUR] {}", line);
    }

    private static void fail(String name, String why) {
        FAILED.incrementAndGet();
        String line = "FAIL  " + name + "  - " + why;
        REPORT.add(line);
        LOG.error("[TOUR] {}", line);
    }

    private static void shoot(Minecraft mc, String name) {
        try {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {
            });
            REPORT.add("SHOT  " + name);
        } catch (Throwable t) {
            LOG.error("[TOUR] screenshot failed", t);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("AztecTour", GameType.SURVIVAL, false,
                Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(20261004L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("AztecTour", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    private static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.getSingleplayerServer() != null;
    }

    private static boolean inDimension(ResourceKey<Level> key) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.level.dimension().equals(key);
    }

    /** By simple name, so a screen class that moves or is renamed fails loudly here. */
    private static boolean screenIs(String simpleName) {
        Screen s = Minecraft.getInstance().screen;
        return s != null && s.getClass().getSimpleName().equals(simpleName);
    }

    /** Presses the first button on the open screen whose label contains the text. */
    private static void press(String label) {
        Screen s = Minecraft.getInstance().screen;
        if (s == null) {
            throw new IllegalStateException("no screen open to press \"" + label + "\" on");
        }
        for (GuiEventListener child : s.children()) {
            if (child instanceof AbstractButton b && b.active
                    && b.getMessage().getString().toLowerCase().contains(label.toLowerCase())) {
                b.onPress();
                return;
            }
        }
        throw new IllegalStateException("no button containing \"" + label + "\" on "
                + s.getClass().getSimpleName());
    }

    /** Types into the n-th text box on the open screen. */
    private static void type(int index, String value) {
        Screen s = Minecraft.getInstance().screen;
        if (s == null) {
            throw new IllegalStateException("no screen open to type into");
        }
        int i = 0;
        for (GuiEventListener child : s.children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox box && i++ == index) {
                box.setValue(value);
                return;
            }
        }
        throw new IllegalStateException("no text box " + index + " on " + s.getClass().getSimpleName());
    }

    /** How many text boxes the open screen has. */
    private static int textBoxes() {
        Screen s = Minecraft.getInstance().screen;
        int n = 0;
        if (s != null) {
            for (GuiEventListener child : s.children()) {
                if (child instanceof net.minecraft.client.gui.components.EditBox) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void key(int glfwKey) {
        Screen s = Minecraft.getInstance().screen;
        if (s == null) {
            throw new IllegalStateException("no screen open for a key press");
        }
        s.keyPressed(glfwKey, 0, 0);
    }

    /** Runs something on the integrated server as the tour's player. */
    private static void server(String what, Consumer<ServerPlayer> body) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) {
            fail(what, "no integrated server or no player");
            return;
        }
        UUID id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) {
                fail(what, "server player missing");
                return;
            }
            try {
                body.accept(sp);
            } catch (Throwable t) {
                fail(what, "server action threw " + t);
                LOG.error("[TOUR] server action failed", t);
            }
        });
    }

    private static void keepAlive(ServerPlayer sp) {
        if (sp.isCreative() || sp.isSpectator() || !sp.isAlive()) {
            return;
        }
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 220, 4, true, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.SATURATION, 220, 0, true, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 220, 0, true, false, false));
        sp.setHealth(sp.getMaxHealth());
    }

    /** Kills every live wave mob with the tour's player as the killer, then reports the phase. */
    private static void killWave() {
        server("kill-wave", sp -> {
            var game = com.jrpetty.aztecabyss.round.RoundManager.game();
            ServerLevel level = sp.serverLevel();
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, game.getMap().bounds(),
                    m -> m.isAlive() && m.getPersistentData().getBoolean("aztecabyss_wave_mob"))) {
                m.hurt(sp.damageSources().playerAttack(sp), Float.MAX_VALUE);
            }
            roundCleared = game.getRound() >= 1
                    && game.getPhase() == com.jrpetty.aztecabyss.round.AbyssGame.Phase.BETWEEN_ROUNDS;
        });
    }

    private static void look(ServerPlayer sp, double x, double y, double z, float yaw, float pitch) {
        sp.teleportTo(sp.serverLevel(), x, y, z, java.util.Set.of(), yaw, pitch);
    }

    /** A standable spot in a maze cell: two blocks of air over a solid floor, or null. */
    private static BlockPos openFloor(ServerLevel level, int cellX, int cellZ) {
        int cell = com.jrpetty.aztecabyss.maze.MazeData.CELL;
        int y = com.jrpetty.aztecabyss.maze.MazeData.FLOOR_Y + 1;
        for (int dx = 1; dx <= 4; dx++) {
            for (int dz = 1; dz <= 4; dz++) {
                BlockPos at = new BlockPos(cellX * cell + dx, y, cellZ * cell + dz);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                        && !level.getBlockState(at.below()).isAir()) {
                    return at;
                }
            }
        }
        return null;
    }
}
