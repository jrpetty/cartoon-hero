package com.jrpetty.aztecabyss.gametest;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.engine.MapScan;
import com.jrpetty.aztecabyss.engine.Ruleset;
import com.jrpetty.aztecabyss.engine.RulesetLoader;
import com.jrpetty.aztecabyss.engine.Script;
import com.jrpetty.aztecabyss.maze.GrieverHoles;
import com.jrpetty.aztecabyss.maze.MazeBuilder;
import com.jrpetty.aztecabyss.maze.MazeData;
import com.jrpetty.aztecabyss.network.LeaderboardPayload;
import com.jrpetty.aztecabyss.network.MazeHubPayload;
import com.jrpetty.aztecabyss.network.MazeInductionPayload;
import com.jrpetty.aztecabyss.network.OpenMapPickerPayload;
import com.jrpetty.aztecabyss.network.TradeBoardPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The smoke tests: a real dedicated server boots this mod and asks it questions.
 *
 * <p>Until these existed nothing in this project had ever <em>run</em> before it
 * reached the live server. CI compiled the jar and a human read the bytecode;
 * the first process to actually load the mod, register its payloads, parse its
 * dimensions and read its rulesets was the production server, in front of the
 * players. Every category of error that reached them - a ruleset that silently
 * ran as the wrong mode, a payload whose two ends disagreed, a hold that zoomed
 * the camera - is a category a running server could have caught.
 *
 * <p>So the build now boots one. NeoForge's game-test server starts a headless
 * dedicated server with the mod installed, runs every method here, and exits
 * non-zero if any fails or if the boot itself throws. The jar is only published
 * if it comes back clean. The tests are deliberately about <em>facts</em> rather
 * than gameplay: does the data load, do the codecs round-trip, does the
 * geometry hold. Gameplay is what players are for; this is for never again
 * handing them something that could not have worked.
 *
 * <p>Compiled into the ordinary jar. NeoForge only discovers game tests when a
 * game-test server is running, so on a production server this class is inert.
 */
@GameTestHolder(AztecAbyssConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmokeTests {

    /** A one-block structure of air: the tests need a template and want no world. */
    private static final String EMPTY = AztecAbyssConstants.MOD_ID + ":empty";

    private SmokeTests() {
    }

    /** Both custom dimensions parsed and were created. */
    @GameTest(template = EMPTY)
    public static void dimensionsExist(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        if (server.getLevel(AztecAbyssConstants.ABYSS_LEVEL_KEY) == null) {
            helper.fail("The Abyss dimension did not load - check data/aztecabyss/dimension");
            return;
        }
        if (server.getLevel(AztecAbyssConstants.MAZE_LEVEL_KEY) == null) {
            helper.fail("The maze dimension did not load - check data/aztecabyss/dimension");
            return;
        }
        helper.succeed();
    }

    /**
     * Every shipped ruleset loads, with no unrecognised keys and no script
     * problems, and plays the mode it was written for.
     *
     * <p>This is the test that would have caught the Key Hunt running as a
     * default round-survival for weeks because its {@code mode} sat one level
     * too high in the file, and the linter that flagged seven valid keys as
     * unknown. A warning nobody reads is a warning that needs a machine.
     */
    @GameTest(template = EMPTY)
    public static void rulesetsLoadClean(GameTestHelper helper) {
        Map<String, Ruleset> all = RulesetLoader.all();
        List<String> problems = new ArrayList<>();
        if (all.size() < 8) {
            problems.add("expected the eight shipped rulesets, found " + all.size() + ": " + all.keySet());
        }
        for (Map.Entry<String, Ruleset> e : all.entrySet()) {
            for (String w : e.getValue().warnings) {
                problems.add(e.getKey() + ": unrecognised key " + w);
            }
            for (String w : Script.warnings(e.getKey())) {
                problems.add(e.getKey() + ": script: " + w);
            }
            if (e.getValue().title.isEmpty() || e.getValue().blurb.isEmpty()) {
                problems.add(e.getKey() + ": no title or blurb - the picker shows a file id");
            }
        }
        for (String freeMode : new String[]{"heist", "vault", "capture", "hunger", "keyhunt"}) {
            Ruleset r = all.get(AztecAbyssConstants.MOD_ID + ":" + freeMode);
            if (r != null && !r.free) {
                problems.add(freeMode + " is written as a free-mode game but did not parse as one");
            }
        }
        finish(helper, problems);
    }

    /** The maze's fixed geometry agrees with itself. */
    @GameTest(template = EMPTY)
    public static void mazeGeometryHolds(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < GrieverHoles.COUNT; i++) {
            BlockPos h = GrieverHoles.hole(i);
            int cx = h.getX() / MazeData.CELL;
            int cz = h.getZ() / MazeData.CELL;
            if (MazeData.inGlade(cx, cz)) {
                problems.add("Griever hole " + i + " is inside the Glade at cell " + cx + "," + cz);
            }
            if (h.getX() < 0 || h.getZ() < 0 || h.getX() >= MazeData.SPAN || h.getZ() >= MazeData.SPAN) {
                problems.add("Griever hole " + i + " is off the map at " + h);
            }
        }
        for (int d = 0; d < MazeBuilder.DOOR_CELLS.length; d++) {
            int[] door = MazeBuilder.DOOR_CELLS[d];
            boolean onRim = door[0] == MazeData.GLADE_MIN_CELL - 1 || door[0] == MazeData.GLADE_MAX_CELL + 1
                    || door[1] == MazeData.GLADE_MIN_CELL - 1 || door[1] == MazeData.GLADE_MAX_CELL + 1;
            if (!onRim) {
                problems.add("door " + d + " at cell " + door[0] + "," + door[1] + " is not on the Glade rim");
            }
        }
        for (MazeData.Layout layout : MazeData.layouts()) {
            if (MazeData.exit(layout.exit()) == null) {
                problems.add("layout " + layout + " names exit \"" + layout.exit() + "\" which does not exist");
            }
        }
        finish(helper, problems);
    }

    /**
     * Every hand-written payload codec decodes what it encodes.
     *
     * <p>A payload's two ends are written by the same person on the same day
     * and can still disagree - a field added to the record and not the codec,
     * a list where a byte array was meant. The client finds out with a
     * disconnect. This finds out first.
     */
    @GameTest(template = EMPTY)
    public static void payloadsRoundTrip(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var access = helper.getLevel().registryAccess();

        MazeHubPayload hub = new MazeHubPayload(new byte[]{1, 2, 3}, new byte[]{4}, new byte[0],
                List.of("IV|§bRunner|2|41"), List.of(123456789L, -5L));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        MazeHubPayload.STREAM_CODEC.encode(buf, hub);
        MazeHubPayload hub2 = MazeHubPayload.STREAM_CODEC.decode(buf);
        if (!hub2.roster().equals(hub.roster()) || !hub2.waypoints().equals(hub.waypoints())
                || hub2.glade().length != 3 || hub2.mine().length != 1 || hub2.marks().length != 0) {
            problems.add("MazeHubPayload did not round-trip");
        }

        MazeInductionPayload ind = new MazeInductionPayload(List.of("runner|§bRunner|b|d|t|p", "x|y|z|w|v|u"));
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        MazeInductionPayload.STREAM_CODEC.encode(buf, ind);
        if (!MazeInductionPayload.STREAM_CODEC.decode(buf).cards().equals(ind.cards())) {
            problems.add("MazeInductionPayload did not round-trip");
        }

        OpenMapPickerPayload pick = new OpenMapPickerPayload(1, List.of(3, 0, 7),
                List.of("m|Map|HARD|blurb|author|The Heist|three idols"));
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        OpenMapPickerPayload.STREAM_CODEC.encode(buf, pick);
        OpenMapPickerPayload pick2 = OpenMapPickerPayload.STREAM_CODEC.decode(buf);
        if (pick2.currentChoice() != 1 || !pick2.bestRounds().equals(pick.bestRounds())
                || !pick2.customMaps().equals(pick.customMaps())
                || !OpenMapPickerPayload.field(pick2.customMaps().get(0), 5).equals("The Heist")) {
            problems.add("OpenMapPickerPayload did not round-trip");
        }

        LeaderboardPayload lb = new LeaderboardPayload(List.of("r1", "r2"), List.of("l"), List.of("run|x"));
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        LeaderboardPayload.STREAM_CODEC.encode(buf, lb);
        LeaderboardPayload lb2 = LeaderboardPayload.STREAM_CODEC.decode(buf);
        if (!lb2.rows().equals(lb.rows()) || !lb2.labels().equals(lb.labels()) || !lb2.runs().equals(lb.runs())) {
            problems.add("LeaderboardPayload did not round-trip");
        }

        TradeBoardPayload tb = new TradeBoardPayload("runner", "§bRunner", "body\n\nmore", "IV", "");
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        TradeBoardPayload.STREAM_CODEC.encode(buf, tb);
        if (!TradeBoardPayload.STREAM_CODEC.decode(buf).equals(tb)) {
            problems.add("TradeBoardPayload did not round-trip");
        }
        finish(helper, problems);
    }

    /** The map validator runs against real chunks and notices an empty map. */
    @GameTest(template = EMPTY)
    public static void mapScanNoticesMissingSpawn(GameTestHelper helper) {
        BoundingBox box = new BoundingBox(helper.absolutePos(BlockPos.ZERO)).inflatedBy(3);
        List<String> problems = MapScan.validate(MapScan.scan(helper.getLevel(), box));
        boolean saidSo = problems.stream().anyMatch(p -> p.contains("[Spawn]"));
        if (!saidSo) {
            helper.fail("MapScan.validate on an empty box did not report the missing [Spawn]: " + problems);
            return;
        }
        helper.succeed();
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty()) {
            helper.succeed();
        } else {
            helper.fail(String.join("\n", problems));
        }
    }
}
