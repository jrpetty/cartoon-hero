package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.client.ClientAbyssState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the custom payload and provides the server-side send helper.
 * The client handler defers to {@link ClientAbyssState} (loaded only on the
 * client) to avoid touching client classes on a dedicated server.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetworking {

    private ModNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                AbyssStatePayload.TYPE,
                AbyssStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.accept(payload)));
        registrar.playToClient(
                RunRecapPayload.TYPE,
                RunRecapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.openRecap(payload)));
        registrar.playToClient(
                AbyssCooldownPayload.TYPE,
                AbyssCooldownPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.acceptCooldown(payload)));
        registrar.playToClient(
                SquadPayload.TYPE,
                SquadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.acceptSquad(payload)));
        registrar.playToClient(
                OpenMapPickerPayload.TYPE,
                OpenMapPickerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.openMapPicker(payload)));
        registrar.playToServer(
                MapSelectPayload.TYPE,
                MapSelectPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        // The maze is a different game, not another arena - it gets
                        // routed straight there rather than recorded as a choice.
                        if (payload.mapId() == MapSelectPayload.MAZE) {
                            com.jrpetty.aztecabyss.maze.MazeEvents.sendToMaze(sp);
                            return;
                        }
                        if (payload.mapId() >= MapSelectPayload.CUSTOM_BASE) {
                            com.jrpetty.aztecabyss.engine.PublishedMaps.playFromPicker(
                                    sp, payload.mapId() - MapSelectPayload.CUSTOM_BASE);
                            return;
                        }
                        // Creator is a mode, not a hunt - nothing is recorded as a
                        // choice, because there is no portal trip to record it for.
                        if (payload.mapId() == MapSelectPayload.CREATOR) {
                            String error = com.jrpetty.aztecabyss.engine.MapCreator.enter(sp, true);
                            if (error != null) {
                                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                        "§c" + error), false);
                            }
                            return;
                        }
                        int id = Math.max(0, Math.min(payload.mapId(),
                                com.jrpetty.aztecabyss.worldgen.ArenaMap.values().length - 1));
                        // Checked on the raw index, because byId deliberately
                        // snaps shelved maps to the Temple - a client that sends
                        // one anyway gets told no, not silently rerouted.
                        if (com.jrpetty.aztecabyss.worldgen.ArenaMap.values()[id].comingSoon()) {
                            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                    "§7That battlefield is being rethought. §8Coming soon."), false);
                            return;
                        }
                        sp.getPersistentData().putInt("aztecabyss_chosen_map", id);
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "§6✦ Hunt set: §e" + com.jrpetty.aztecabyss.worldgen.ArenaMap.byId(id).title()
                                        + " §7— step through the portal when you're ready."), false);
                    }
                }));
        registrar.playToClient(
                LeaderboardPayload.TYPE,
                LeaderboardPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientAbyssState.openLeaderboards(payload)));
        registrar.playToServer(
                RequestLeaderboardPayload.TYPE,
                RequestLeaderboardPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        sendLeaderboards(sp);
                    }
                }));
        registrar.playToClient(
                SkillTreePayload.TYPE,
                SkillTreePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientAbyssState.openSkills(payload)));
        registrar.playToServer(
                SkillLearnPayload.TYPE,
                SkillLearnPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        com.jrpetty.aztecabyss.maze.MazeEvents.onSkillClick(sp, payload.skillId());
                    }
                }));
        registrar.playToClient(
                TradeBoardPayload.TYPE,
                TradeBoardPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientAbyssState.openTradeBoard(payload)));
        registrar.playToServer(
                TradeChoicePayload.TYPE,
                TradeChoicePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        com.jrpetty.aztecabyss.maze.MazeEvents.chooseTrade(sp, payload.job());
                    }
                }));
        registrar.playToClient(
                RequisitionPayload.TYPE,
                RequisitionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientAbyssState.openRequisition(payload)));
        registrar.playToServer(
                RequisitionOrderPayload.TYPE,
                RequisitionOrderPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        com.jrpetty.aztecabyss.maze.MazeEvents.onOrderClick(
                                sp, payload.id(), payload.delta());
                    }
                }));
        registrar.playToServer(
                PingPayload.TYPE,
                PingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        com.jrpetty.aztecabyss.round.RoundManager.onPing(
                                sp, new net.minecraft.core.BlockPos(payload.x(), payload.y(), payload.z()));
                    }
                }));
    }

    /**
     * Sends a player their whole trade sheet.
     *
     * <p>One packet for the lot, so opening the screen never waits on a second
     * round trip and re-sending after a point is spent is a single message. The
     * screen is rebuilt from this rather than patched, which is why spending a
     * point can never leave the client showing a rank the server does not agree
     * exists.
     */
    /** The trade board: one post, described in full, for the sign-up screen. */
    public static void sendTradeBoard(ServerPlayer player, String job) {
        if (player.getServer() == null) {
            return;
        }
        var jobs = com.jrpetty.aztecabyss.maze.MazeJobs.get(player.getServer());
        StringBuilder takers = new StringBuilder();
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            if (jobs.is(p.getUUID(), job)) {
                if (!takers.isEmpty()) {
                    takers.append("§8, ");
                }
                takers.append("§f").append(p.getGameProfile().getName())
                        .append(" §8lv").append(jobs.levelOf(p.getUUID(), job));
            }
        }
        String current = jobs.jobOf(player.getUUID());
        PacketDistributor.sendToPlayer(player, new TradeBoardPayload(
                job,
                com.jrpetty.aztecabyss.maze.MazeJobs.display(job),
                com.jrpetty.aztecabyss.maze.MazeJobs.description(job),
                takers.toString(),
                current == null ? "" : com.jrpetty.aztecabyss.maze.MazeJobs.display(current)));
    }

    public static void sendSkills(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        var jobs = com.jrpetty.aztecabyss.maze.MazeJobs.get(player.getServer());
        var skills = com.jrpetty.aztecabyss.maze.MazeSkills.get(player.getServer());
        String job = jobs.jobOf(player.getUUID());
        if (job == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7Take a trade first. §8/maze job"), false);
            return;
        }
        java.util.List<String> rows = new java.util.ArrayList<>();
        for (var s : com.jrpetty.aztecabyss.maze.MazeSkills.forJob(job)) {
            StringBuilder packed = new StringBuilder();
            packed.append(s.id()).append('|').append(s.display()).append('|')
                    .append(skills.rank(player.getUUID(), s.id())).append('|')
                    .append(com.jrpetty.aztecabyss.maze.MazeSkills.MAX_RANK);
            for (String rank : s.ranks()) {
                packed.append('|').append(rank);
            }
            rows.add(packed.toString());
        }
        PacketDistributor.sendToPlayer(player, new SkillTreePayload(
                job,
                com.jrpetty.aztecabyss.maze.MazeJobs.display(job),
                skills.available(jobs, player.getUUID(), job),
                jobs.xpOf(player.getUUID(), job),
                com.jrpetty.aztecabyss.maze.MazeSkills.costPerPoint(job),
                rows));
    }

    /**
     * Sends a player the whole catalogue and their whole slate.
     *
     * <p>One packet for the lot, so opening the screen never waits and every
     * click is a single message each way. The screen is rebuilt from this rather
     * than patched, which is why adding a line can never leave the client
     * showing a balance the server does not agree with.
     */
    public static void sendOrders(ServerPlayer player) {
        if (player.getServer() == null
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        var orders = com.jrpetty.aztecabyss.maze.MazeOrders.get(level);
        var work = com.jrpetty.aztecabyss.maze.MazeDayWork.get(level);
        var mine = orders.slate(player.getUUID());

        // What the whole Glade has on order, line by line. With one shared pot
        // this is the number that decides anything - your own is a footnote.
        java.util.Map<String, Integer> glade = new java.util.HashMap<>();
        for (java.util.UUID who : orders.everyone()) {
            orders.slate(who).forEach((id, n) -> glade.merge(id, n, Integer::sum));
        }

        java.util.List<String> rows = new java.util.ArrayList<>();
        for (com.jrpetty.aztecabyss.maze.MazeOrders.Entry e
                : com.jrpetty.aztecabyss.maze.MazeOrders.catalogue()) {
            rows.add(e.group() + "|" + e.id() + "|" + e.display() + "|"
                    + e.count() + "|" + e.cost() + "|"
                    + mine.getOrDefault(e.id(), 0) + "|"
                    + glade.getOrDefault(e.id(), 0) + "|"
                    // The item's registry name, so the screen can draw the
                    // actual thing being bought rather than a line of text.
                    + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.item()));
        }

        String job = com.jrpetty.aztecabyss.maze.MazeJobs.get(level).jobOf(player.getUUID());
        String summary = orders.heads()
                + "|" + com.jrpetty.aztecabyss.maze.MazeOrders.fromHeads(level)
                + "|" + com.jrpetty.aztecabyss.maze.MazeDayWork.totalCredits(level)
                + "|" + orders.totalBounty()
                + "|" + work.unitsOf(player.getUUID())
                + "|" + com.jrpetty.aztecabyss.maze.MazeDayWork.quotaFor(job)
                + "|" + work.creditsOf(level, player.getUUID())
                + "|" + com.jrpetty.aztecabyss.maze.MazeDayWork.maxCredits()
                + "|" + (job == null ? "No trade"
                        : com.jrpetty.aztecabyss.maze.MazeJobs.display(job))
                + "|" + com.jrpetty.aztecabyss.maze.MazeDayWork.unitName(job);

        PacketDistributor.sendToPlayer(player, new RequisitionPayload(
                com.jrpetty.aztecabyss.maze.MazeClock.get(player.getServer()).day(),
                com.jrpetty.aztecabyss.maze.MazeOrders.pool(level),
                orders.committedTotal(),
                summary,
                rows));
    }

    public static void sendState(ServerPlayer player, boolean inRun, int round) {
        sendState(player, inRun, round, false);
    }

    public static void sendState(ServerPlayer player, boolean inRun, int round, boolean fogRound) {
        // No gate figures in this lightweight form - mark them absent rather than
        // letting a zeroed int read as "every gate is wide open".
        PacketDistributor.sendToPlayer(player, new AbyssStatePayload(
                inRun, round, fogRound, 0, AbyssStatePayload.pack(0, 0, 0), 0));
    }

    /** Full in-run state including the live HUD figures. */
    public static void sendHud(ServerPlayer player, int round, boolean fogRound,
                               int enemiesRemaining, int playersUp, int playersTotal, int myKills,
                               int barricadeSummary) {
        PacketDistributor.sendToPlayer(player, new AbyssStatePayload(
                true, round, fogRound, enemiesRemaining,
                AbyssStatePayload.pack(playersUp, playersTotal, barricadeSummary), myKills));
    }

    /** Pushes the player's re-entry cooldown deadline so their screen can count it down. */
    public static void sendCooldown(ServerPlayer player, long cooldownUntil) {
        PacketDistributor.sendToPlayer(player, new AbyssCooldownPayload(cooldownUntil));
    }

    /** Opens the arena picker, pre-selecting their last choice and showing per-map bests. */
    public static void sendOpenMapPicker(ServerPlayer player) {
        java.util.List<Integer> bests = new java.util.ArrayList<>();
        if (player.getServer() != null) {
            com.jrpetty.aztecabyss.data.AbyssStats stats =
                    com.jrpetty.aztecabyss.data.AbyssStats.get(player.getServer());
            for (int i = 0; i < com.jrpetty.aztecabyss.worldgen.ArenaMap.values().length; i++) {
                bests.add(stats.bestRoundOnMap(player.getUUID(), i));
            }
        }
        java.util.List<String> custom = new java.util.ArrayList<>();
        if (player.getServer() != null) {
            for (com.jrpetty.aztecabyss.engine.PublishedMaps.Entry e
                    : com.jrpetty.aztecabyss.engine.PublishedMaps.ordered(player.getServer())) {
                custom.add(e.name() + "|" + e.title() + "|" + e.difficulty() + "|" + e.blurb());
            }
        }
        PacketDistributor.sendToPlayer(player, new OpenMapPickerPayload(
                player.getPersistentData().getInt("aztecabyss_chosen_map"), bests, custom));
    }

    /**
     * Packs every board on the server into one payload.
     *
     * <p>Flat strings, because the client has no way to look any of this up: a
     * published map is a file in the world folder that no client has heard of.
     * One list for every map and both boards, so opening the screen is one packet
     * however many maps there are.
     */
    public static void sendLeaderboards(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        var board = com.jrpetty.aztecabyss.data.Leaderboards.get(player.getServer());
        java.util.List<String> rows = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();

        java.util.LinkedHashMap<String, String> names = new java.util.LinkedHashMap<>();
        for (com.jrpetty.aztecabyss.worldgen.ArenaMap m : com.jrpetty.aztecabyss.worldgen.ArenaMap.values()) {
            names.put(m.name().toLowerCase(java.util.Locale.ROOT), m.title());
        }
        names.put("maze", "The Maze");
        for (com.jrpetty.aztecabyss.engine.PublishedMaps.Entry e
                : com.jrpetty.aztecabyss.engine.PublishedMaps.ordered(player.getServer())) {
            names.put("custom:" + e.name().toLowerCase(java.util.Locale.ROOT), e.title());
        }
        // Anything with a record but no name left - a map since unpublished -
        // still shows, under its key. Losing somebody's record because the map was
        // taken off the portal would be worse than an ugly label.
        for (String key : board.mapsWithRecords()) {
            names.putIfAbsent(key, key);
        }

        for (var entry : names.entrySet()) {
            String key = entry.getKey();
            labels.add(key + "|" + entry.getValue());
            boolean time = board.modeOf(key)
                    == com.jrpetty.aztecabyss.data.Leaderboards.Mode.TIME;
            for (boolean group : new boolean[]{false, true}) {
                var top = board.top(key, group, 10);
                for (int i = 0; i < top.size(); i++) {
                    var r = top.get(i);
                    String score = time
                            ? (r.seconds() / 60) + "m " + (r.seconds() % 60) + "s"
                            : "Round " + r.score();
                    rows.add(key + (group ? "#group" : "#solo") + "|" + (i + 1) + "|"
                            + r.name() + "|" + score + "|" + r.seconds() + "|" + r.party());
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new LeaderboardPayload(rows, labels));
    }

    /** Pushes a player's squadmate list for the co-op teammate HUD. */
    public static void sendSquad(ServerPlayer player, java.util.List<TeammateInfo> teammates) {
        PacketDistributor.sendToPlayer(player, new SquadPayload(teammates));
    }

    public static void sendRecap(ServerPlayer player, int round, int kills, int revives, int survivalSeconds,
                                 int previousBest, boolean victory, boolean multiplayer, boolean extracted,
                                 int headshots, int deaths, boolean ritual) {
        PacketDistributor.sendToPlayer(player, new RunRecapPayload(
                round, kills, headshots, survivalSeconds,
                RunRecapPayload.pack(previousBest, deaths, revives),
                RunRecapPayload.packFlags(victory, multiplayer, extracted, ritual)));
    }
}
