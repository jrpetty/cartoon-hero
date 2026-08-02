package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.server.level.ServerBossEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * A round-survival game played on a map nobody wrote any code for.
 *
 * <p>This is the join between the two halves of the engine. Up to now a build
 * could be authored, validated, saved and shipped, and a ruleset could be written
 * and reloaded - but nothing ever put the two together and played them. Markers
 * were read and then ignored; rulesets changed what a command printed and nothing
 * else.
 *
 * <p>Everything here comes from data. Where players start is a {@code [Spawn]}
 * sign. Where the horde comes from is every {@code [Horde]} sign. How many arrive,
 * how much health they have, how hard they hit and how long you get between rounds
 * are all read off the ruleset. There is no map-specific logic anywhere in this
 * class, and that is the test of whether the engine is real: if this file ever
 * needs to know the name of a map, the design has failed.
 *
 * <p>Deliberately independent of the existing round system. The hand-built maps
 * keep working exactly as they do while this grows up alongside them, rather than
 * both being half-migrated at once.
 */
public final class EngineArena {

    private static final int SPAWN_INTERVAL_TICKS = 20;

    private static EngineArena current;

    private final ServerLevel level;
    private final String mapName;
    private final Ruleset rules;

    /** The ruleset this run is playing under, for machines that need to consult it. */
    public Ruleset rules() {
        return rules;
    }

    /**
     * How many times each limited dealer has been bought from this run.
     *
     * <p>Per run and per sign rather than per player: a limit of one on a
     * netherite sword should mean the squad gets one, which is a decision they
     * have to have together. Counted per position so two dealers selling the same
     * thing are two stocks.
     */
    private final java.util.Map<BlockPos, Integer> dealerSales = new java.util.HashMap<>();

    public boolean canBuyFrom(BlockPos dealer, int limit) {
        return dealerSales.getOrDefault(dealer.immutable(), 0) < limit;
    }

    public void recordBuy(BlockPos dealer) {
        dealerSales.merge(dealer.immutable(), 1, Integer::sum);
    }
    private final BlockPos spawn;
    private final List<Marker> hordes;
    private final BoundingBox bounds;

    private final List<UUID> participants = new ArrayList<>();
    private final List<Mob> alive = new ArrayList<>();
    private final ServerBossEvent bar;
    private final RandomSource rng = RandomSource.create();

    /** Elapsed run time in ticks, for free mode's clock and the board's tie-break. */
    private int elapsed = 0;
    /** What free mode shows on the bar. Set by script; the clock is appended. */
    private String barText = "";

    public void setBarText(String text) {
        this.barText = text == null ? "" : text;
    }

    public int elapsedSeconds() {
        return elapsed / 20;
    }

    private int round = 0;
    private int leftToSpawn = 0;
    private int breather = 0;
    private boolean running = true;

    /**
     * Which areas have been paid open.
     *
     * <p>"start" is open from the first round and everything else has to be bought
     * through a {@code [Door]}. That is the whole shape of a map like this: you
     * begin in one room you can hold, and every extra room you unlock is more
     * ground worth having and more directions the horde arrives from. Opening the
     * map is a decision, not a formality.
     */
    private final java.util.Set<String> openAreas = new java.util.HashSet<>(java.util.Set.of("start", ""));
    private final List<Marker> zones = new ArrayList<>();
    private final List<Marker> spawners = new ArrayList<>();
    private final List<Marker> bossPoints = new ArrayList<>();
    /** Optional out-of-sight spawn chambers, paired to the nearest way in. */
    private final List<Marker> pens = new ArrayList<>();
    private final List<Marker> teleports = new ArrayList<>();
    /** Named volumes that fire an event when somebody walks in or out. */
    private final List<Marker> regions = new ArrayList<>();
    /** Who is currently inside which region, so enter and leave fire once each. */
    private final java.util.Map<UUID, java.util.Set<String>> inRegion = new java.util.HashMap<>();
    /** What this run remembers. */
    private final Vars vars = new Vars();
    /** Sides, if the map declared any. */
    private Teams teams;
    /** Team spawn points, by team id. */
    private final java.util.Map<String, Marker> teamSpawns = new java.util.HashMap<>();

    public Teams teams() {
        if (teams == null) {
            teams = new Teams(level);
        }
        return teams;
    }

    /**
     * Where a given player starts.
     *
     * <p>A team spawn if their side has one, the map's single spawn otherwise. The
     * fallback matters: a map that declares teams but only marks one spawn should
     * still be playable, just symmetrical, rather than dropping half the players
     * into the void.
     */
    public BlockPos spawnFor(ServerPlayer player) {
        if (teams != null) {
            String side = teams.teamOf(player);
            if (side != null) {
                Marker m = teamSpawns.get(side);
                if (m != null) {
                    return m.pos();
                }
            }
        }
        return spawn;
    }
    private final List<Marker> traps = new ArrayList<>();
    /** Armed traps: position to the game time they stop burning. */
    private final java.util.Map<BlockPos, Long> trapsActive = new java.util.HashMap<>();
    /** Traps cooling down, so one purchase is not a permanent kill zone. */
    private final java.util.Map<BlockPos, Long> trapsCooling = new java.util.HashMap<>();
    /** Players who just teleported, so a pad does not bounce them straight back. */
    private final java.util.Map<UUID, Long> teleportCooldown = new java.util.HashMap<>();
    /** Loot caches already emptied this round. */
    private final java.util.Set<BlockPos> lootTaken = new java.util.HashSet<>();
    private final Director director;
    /** Where a run can be banked, or null if this map has no way out. */
    private Marker extract;
    /** Ticks each player has spent stood on the extraction point. */
    private final java.util.Map<UUID, Integer> extracting = new java.util.HashMap<>();

    /** How long you must stand still on the glyph. */
    private static final int EXTRACT_TICKS = 100;

    /**
     * Players who have died. They stay out for the rest of the run.
     *
     * <p>Without this, death was only ever temporary: the tick loop filtered on
     * {@code isDeadOrDying()}, which stops being true the moment somebody clicks
     * respawn. They would then quietly rejoin a run they had already lost, from
     * wherever the world put them, with the round continuing around them.
     */
    private final java.util.Set<UUID> fallen = new java.util.HashSet<>();

    /**
     * Which player object currently holds the bar, per participant.
     *
     * <p>A boss bar keeps references to {@code ServerPlayer} objects, and logging
     * out and back in produces a <em>new</em> object for the same person. So a
     * participant who relogged mid-run lost their round bar permanently and had
     * no way to get it back - they were still in the run, still being attacked,
     * with nothing on screen telling them what round it was or how many were
     * left. Tracking who holds it lets the stale reference be swapped for the
     * live one, and only when it actually changes, so this costs no packets in
     * the normal case.
     */
    private final java.util.Map<UUID, ServerPlayer> barred = new java.util.HashMap<>();

    /**
     * Every block this run changed, and what was there before it.
     *
     * <p>Playing a map used to wear it out. Buying a door carved a permanent hole
     * in the wall and deleted the sign that sold it, so the second run on the same
     * map started with that area already open and no way to gate it again - and
     * area-gating is the best mechanic the engine has. Three or four runs and a
     * carefully sealed map was one open room.
     *
     * <p>So the engine records what it overwrites and puts it back when the run
     * ends. A map is a fixture, not a consumable: the hundredth run on it is the
     * same game as the first.
     */
    private final java.util.LinkedHashMap<BlockPos, BlockState> restore = new java.util.LinkedHashMap<>();

    /**
     * Players on the floor: uuid to ticks of bleed-out left.
     *
     * <p>The difference between four people playing together and four people
     * playing solo beside each other. A death nobody can do anything about is
     * just an exit; a teammate face-down across the room with a clock on them is
     * a decision, and it is the only moment in the genre where the right move is
     * to walk towards the horde.
     */
    private final java.util.Map<UUID, Integer> downed = new java.util.HashMap<>();
    /** How far through a revive each downed player is. */
    private final java.util.Map<UUID, Integer> reviving = new java.util.HashMap<>();

    /** The variant round in force, or null on an ordinary one. */
    private Ruleset.SpecialRound special;

    /** What this map asks of you besides surviving, if anything. */
    private Objective objective;

    /** Game time of the last thing that counted as progress. */
    private long lastProgress = 0L;
    /** How long a round may make no progress before the stragglers are fetched. */
    private static final long STALL_TICKS = 300L;

    private EngineArena(ServerLevel level, String mapName, Ruleset rules,
                        BlockPos spawn, List<Marker> hordes, BoundingBox bounds) {
        this.level = level;
        this.mapName = mapName;
        this.rules = rules;
        this.spawn = spawn;
        this.hordes = hordes;
        this.bounds = bounds;
        this.bar = new ServerBossEvent(Component.literal(mapName),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        this.director = new Director(rules);
        this.startedTick = level.getGameTime();
    }

    public static EngineArena active() {
        return current;
    }

    public static boolean isRunning() {
        return current != null && current.running;
    }

    /**
     * Starts a run on whatever is around the caller.
     *
     * @return an error to show the player, or null if it started
     */
    public static String start(ServerLevel level, ServerPlayer player, int radius, String rulesetId) {
        return startIn(level, player, new BoundingBox(
                player.blockPosition().getX() - radius, level.getMinBuildHeight(),
                player.blockPosition().getZ() - radius,
                player.blockPosition().getX() + radius, level.getMaxBuildHeight() - 1,
                player.blockPosition().getZ() + radius), rulesetId);
    }

    /** Runs a game inside an explicit box - what a wand selection plays. */
    public static String startIn(ServerLevel level, ServerPlayer player,
                                 BoundingBox box, String rulesetId) {
        MapScan.Result scan = MapScan.scan(level, box);

        Marker spawnMarker = scan.first("spawn");
        if (spawnMarker == null) {
            return "This map has no [Spawn] marker. Run /arena validate to see what else is missing.";
        }
        Ruleset rules = RulesetLoader.byId(rulesetId);
        // A free-mode map is allowed to have nothing that attacks. That is the
        // entire point of it: a race, an escape room and a heist have no horde by
        // definition, and refusing them here made round-survival not one mode but
        // the only thing the engine could express.
        if (!rules.free && scan.of("horde").isEmpty()) {
            return "This map has no [Horde] markers, so nothing could ever attack. "
                    + "(A ruleset with \"mode\": \"free\" does not need any.)";
        }

        stop(false);
        current = new EngineArena(level, "Custom Arena", rules,
                spawnMarker.pos(), scan.of("horde"), box);
        current.zones.addAll(scan.of("zone"));
        current.spawners.addAll(scan.of("spawner"));
        current.bossPoints.addAll(scan.of("boss"));
        current.pens.addAll(scan.of("pen"));
        current.teleports.addAll(scan.of("teleport"));
        current.regions.addAll(scan.of("region"));
        // A [Spawn] carrying team= is that side's spawn rather than the map's.
        for (Marker sp : scan.of("spawn")) {
            String team = sp.arg("team", "").toLowerCase(java.util.Locale.ROOT);
            if (!team.isEmpty()) {
                current.teamSpawns.put(team, sp);
                current.teams().define(team, null);
            }
        }
        current.traps.addAll(scan.of("trap"));
        Marker objectiveMarker = scan.first("objective");
        if (objectiveMarker != null) {
            Objective built = new Objective(objectiveMarker);
            current.objective = built.valid() ? built : null;
        }
        current.extract = scan.first("extract");
        EnginePowerUps.reset();
        current.consumeMarkers(scan);
        // Everyone already stood in the map is in it. Making each player type a
        // command to be included in a fight happening around them is the kind of
        // friction that turns a co-op mode into a single-player one by accident.
        current.join(player);
        for (ServerPlayer other : level.players()) {
            if (other != player && box.isInside(other.blockPosition())) {
                current.join(other);
            }
        }
        if (rules.free) {
            // No round ever begins. run_start is the only thing that fires, and
            // from there the map is on its own.
            current.bar.setName(Component.literal("§6" + rules.id));
            Script.fire(current, level, rules.id, "run_start", player);
        } else {
            current.beginRound(1);
        }
        return null;
    }

    /**
     * Joins a run already in progress.
     *
     * <p>Late joiners start on the round the squad is on, not at round one - the
     * alternative is either a private difficulty curve for one player or a reset
     * that punishes everyone else for their arriving. They do get the starting
     * money, because turning up to round twelve with nothing is not a challenge,
     * it is a spectator seat.
     *
     * @return an error to show, or null on success
     */
    public static String joinRun(ServerPlayer player) {
        EngineArena a = current;
        if (a == null || !a.running) {
            return "No run in progress.";
        }
        if (a.participants.contains(player.getUUID())) {
            return "You are already in it.";
        }
        if (a.fallen.contains(player.getUUID())) {
            return "You died in this run. Wait for the next one.";
        }
        a.join(player);
        for (ServerPlayer p : a.players()) {
            p.displayClientMessage(Component.literal(
                    "§e" + player.getGameProfile().getName() + " joined on round §f"
                            + a.round + "§e."), false);
        }
        return null;
    }

    /**
     * Deletes the signs that were only ever instructions.
     *
     * <p>A {@code [Horde]} sign nailed to the wall of a map being played is set
     * dressing nobody asked for. The dealers stay, because those are the shop.
     */
    private void consumeMarkers(MapScan.Result scan) {
        // Deliberately does nothing now, and the method is kept so the intent is
        // recorded rather than silently dropped.
        //
        // Marker signs used to be deleted when a run started, on the reasoning
        // that a [Horde] sign nailed to a wall is scaffolding nobody wants to look
        // at. That was wrong in the worst way: the markers are how a map is
        // *defined*, so destroying them meant the second /arena play on the same
        // map found no [Spawn] and refused to start. Every map was single-use and
        // had to be rebuilt to be replayed.
        //
        // Tidiness is not worth that. The signs stay.
    }

    private void join(ServerPlayer player) {
        participants.add(player.getUUID());
        bar.addPlayer(player);
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), 0.0F);
        if (rules.economyEnabled) {
            Currency c = Currency.byId(rules.defaultCurrency);
            c.set(player, c.start());
        }
    }

    public static void stop(boolean announce) {
        if (current == null) {
            return;
        }
        if (announce) {
            for (ServerPlayer p : current.players()) {
                p.displayClientMessage(Component.literal("§7The run is over."), false);
            }
        }
        current.recordResult();
        current.vars.clear();
        current.inRegion.clear();
        if (current.teams != null) {
            current.teams.clear();
        }
        for (Mob m : current.alive) {
            if (m.isAlive()) {
                m.discard();
            }
        }
        EnginePowerUps.clearDrops(current.level, current.bounds);
        current.restoreWorld();
        current.bar.removeAllPlayers();
        current.running = false;
        current = null;
    }

    /**
     * Files the run on the board before it is torn down.
     *
     * <p>Round reached is the score, and how long it took breaks a tie - two
     * squads who both got to round twenty did not do the same thing if one of them
     * did it in half the time. Every participant is credited, including anyone who
     * went down and stayed down: they were in it.
     */
    private void recordResult() {
        if (level.getServer() == null || round <= 0 || participants.isEmpty()) {
            return;
        }
        String key = mapKey == null || mapKey.isBlank() ? "engine:" + rules.id : mapKey;
        int party = participants.size();
        int seconds = (int) ((level.getGameTime() - startedTick) / 20L);
        var board = com.jrpetty.aztecabyss.data.Leaderboards.get(level.getServer());
        for (UUID id : participants) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            board.submit(key, com.jrpetty.aztecabyss.data.Leaderboards.Mode.ROUNDS,
                    id, p.getGameProfile().getName(), round, seconds, party);
        }
    }

    /** Which board this run files under. Set when a published map starts one. */
    private String mapKey = "";
    /** When the run began, for the tie-break on the board. */
    private long startedTick = 0L;

    public void setMapKey(String key) {
        this.mapKey = key == null ? "" : key;
    }

    /** Everyone in the run, for anything outside this class that needs to address them. */
    public List<ServerPlayer> everyone() {
        return players();
    }

    private List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : participants) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // The loop
    // ------------------------------------------------------------------

    /**
     * Driven from the level tick, and fussy about which level.
     *
     * <p>Every loaded dimension ticks, so an unguarded call here would run the
     * round loop once per dimension per tick - the breather counting down three
     * times as fast and three mobs arriving where one was asked for, on a server
     * that happens to have the Nether loaded.
     */
    public static void tickActive(ServerLevel level) {
        if (current != null && current.running && current.level.dimension().equals(level.dimension())) {
            current.tick();
        }
    }

    private void tick() {
        List<ServerPlayer> present = players();
        if (present.isEmpty()) {
            stop(false);
            return;
        }
        // Anyone who has died is out, and stays out - respawning does not put you
        // back in a run you already lost.
        for (ServerPlayer p : present) {
            if (p.isDeadOrDying()) {
                fallen.add(p.getUUID());
                bar.removePlayer(p);
            }
        }
        present.removeIf(p -> fallen.contains(p.getUUID())
                || !p.level().dimension().equals(level.dimension()));
        if (present.isEmpty()) {
            for (ServerPlayer p : players()) {
                p.displayClientMessage(Component.literal(rules.free
                        ? "§c§lDOWN. §r§7You lasted §f" + MazeStyleTime.of(elapsed / 20) + "§7."
                        : "§c§lDOWN. §r§7You reached round §f" + round + "§7."), false);
            }
            stop(false);
            return;
        }
        elapsed++;

        refreshBars(present);
        tickDowned(present);
        tickObjective(present);
        tickPrompts(present);
        tickRegions(present);

        // Free mode stops here. No breather, no wave, no round ever begins - the
        // map is the script, and the script ends it. Everything above this line is
        // still live, because shops, doors, traps, regions and variables were
        // never really about rounds.
        if (rules.free) {
            tickFreeBar(present);
            return;
        }
        tickTraps();
        tickTeleports(present);
        if (rules.powerupChance > 0 && (special == null || !special.noPowerups())) {
            EnginePowerUps.tick(level, present, bounds);
        }
        alive.removeIf(m -> !m.isAlive());
        tickZones(present);
        if (level.getGameTime() % 20L == 0L) {
            director.sample(present);
        }

        if (breather > 0) {
            tickExtraction(present);
            breather--;
            // The bar is the only place a player reliably looks, so it is where
            // the way out gets advertised - an extraction nobody knows about is
            // the same as not having one.
            bar.setName(Component.literal("§7Next round in §f"
                    + Math.max(1, breather / 20) + "s"
                    + (extract != null ? " §8| §6extract point is open" : "")));
            bar.setProgress(1.0F - (breather / (float) Math.max(1, rules.breatherFor(round))));
            if (breather == 0) {
                beginRound(round + 1);
            }
            return;
        }

        // The Director works on pacing only: how often the next one arrives and how
        // many may be on the floor at once. It never touches what a mob is, because
        // a zombie that is quietly weaker teaches players their weapons are
        // unreliable, whereas a gap in the stream just reads as a lull.
        float pace = director.pace();
        int interval = Math.max(4, Math.round(SPAWN_INTERVAL_TICKS / pace));
        int cap = Math.max(4, Math.round(rules.concurrentCap * Math.min(1.5f, Math.max(0.5f, pace))));
        if (leftToSpawn > 0 && level.getGameTime() % interval == 0 && alive.size() < cap) {
            spawnOne();
        }
        if (leftToSpawn <= 0 && alive.isEmpty()) {
            endRound();
            return;
        }
        // A round ends when the last one dies, which means one that cannot be
        // reached ends nothing - and the run sits there forever with two zombies
        // wedged in a pen and no way to finish. This is the single most likely way
        // for a hand-built map to lock up, because it needs only one doorway an
        // author did not notice was too narrow.
        if (leftToSpawn <= 0 && !alive.isEmpty()
                && level.getGameTime() - lastProgress > STALL_TICKS) {
            fetchStragglers(present);
        }
        bar.setName(Component.literal("§c§lROUND " + round + " §r§7— §f"
                + (alive.size() + leftToSpawn) + "§7 left"
                + (objective == null ? "" : objective.hud())));
        int total = Math.max(1, rules.countFor(round));
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, (alive.size() + leftToSpawn) / (float) total)));
    }

    /**
     * Banking a run: stand on the extraction glyph between rounds.
     *
     * <p>Without this an engine map has no way to win. You play until you die, and
     * a game whose only ending is failure teaches players that survival was never
     * the point - so there is nothing to be careful about and no reason to stop on
     * a good round. Extraction makes leaving a decision, which is what gives
     * staying any weight.
     *
     * <p>Only between rounds, and only while stood still. Bailing out of a fight
     * you are losing would make it a panic button rather than a judgement call,
     * and the interesting version of the question is asked in the quiet: you have
     * what you have, and the next round is bigger.
     */
    private void tickExtraction(List<ServerPlayer> present) {
        if (extract == null) {
            return;
        }
        double range = Math.max(1, extract.intArg("radius", 2));
        for (ServerPlayer p : present) {
            boolean onIt = p.blockPosition().distSqr(extract.pos()) <= range * range;
            if (!onIt) {
                extracting.remove(p.getUUID());
                continue;
            }
            int held = extracting.merge(p.getUUID(), 1, Integer::sum);
            if (held < EXTRACT_TICKS) {
                if (held % 10 == 0) {
                    p.displayClientMessage(Component.literal(
                            "§6Extracting… §f" + (held * 100 / EXTRACT_TICKS) + "%"), true);
                }
                continue;
            }
            bankRun();
            return;
        }
    }

    /** Ends the run as a success, and tells the script layer it happened. */
    private void bankRun() {
        int reached = round;
        for (ServerPlayer p : players()) {
            p.displayClientMessage(Component.literal(
                    "§6§lOUT. §r§7Banked on round §f" + reached + "§7."), false);
        }
        Script.fire(this, level, rules.id, "extracted", null);
        stop(false);
    }

    /**
     * Drags whatever is left of a stalled round to the players.
     *
     * <p>Deliberately a teleport rather than killing them off. Quietly deleting
     * the last two zombies would end the round and hide the fault, and the author
     * would ship a map with a hole in it. Dropping them at somebody's feet ends
     * the stall and is unmistakably something going wrong, which is the correct
     * amount of noise for a bug in a map.
     */
    private void fetchStragglers(List<ServerPlayer> present) {
        if (present.isEmpty()) {
            return;
        }
        ServerPlayer target = present.get(rng.nextInt(present.size()));
        BlockPos at = target.blockPosition();
        int moved = 0;
        for (Mob m : alive) {
            if (!m.isAlive()) {
                continue;
            }
            m.teleportTo(at.getX() + rng.nextInt(5) - 2, at.getY(), at.getZ() + rng.nextInt(5) - 2);
            m.setTarget(target);
            moved++;
        }
        lastProgress = level.getGameTime();
        if (moved > 0) {
            for (ServerPlayer p : present) {
                p.displayClientMessage(Component.literal(
                        "§8The last of them could not find you. They have been brought in."), true);
            }
        }
    }

    private void beginRound(int n) {
        round = n;
        leftToSpawn = rules.countFor(n);
        breather = 0;
        lootTaken.clear();
        special = rules.specialFor(n);
        lastProgress = level.getGameTime();
        director.onRoundStart();
        runSpawners();
        maybeBoss();
        // run_start is an extra on top of round one, not a replacement for it -
        // a rule listening for round_start should hear about every round.
        if (n == 1) {
            Script.fire(this, level, rules.id, "run_start", null);
        }
        Script.fire(this, level, rules.id, "round_start", null);
        for (ServerPlayer p : players()) {
            p.displayClientMessage(Component.literal("§c§lROUND " + n), true);
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR,
                    SoundSource.HOSTILE, 0.5F, 1.4F);
            if (special != null && !special.title().isEmpty()) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal(special.title())));
                if (!special.subtitle().isEmpty()) {
                    p.connection.send(
                            new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                                    Component.literal(special.subtitle())));
                }
            }
        }
    }

    private void endRound() {
        if (!rules.endless && rules.finalRound > 0 && round >= rules.finalRound) {
            for (ServerPlayer p : players()) {
                p.displayClientMessage(Component.literal(
                        "§6§lCLEARED. §r§7Round §f" + round + "§7 was the last."), false);
            }
            stop(false);
            return;
        }
        Script.fire(this, level, rules.id, "round_end", null);
        breather = Math.max(1, rules.breatherFor(round));
    }

    /**
     * Puts one mob into the world at one of the map's horde markers.
     *
     * <p>Attributes are set from the ruleset and then scaled by the round curve,
     * in that order, so a mob's own numbers stay meaningful - a husk written as
     * three times a zombie's health is still three times a zombie's health at
     * round forty.
     */
    // ------------------------------------------------------------------
    // Areas, zones and caches
    // ------------------------------------------------------------------

    /**
     * Who a script's actions apply to: the living.
     *
     * <p>A script that heals "all" should not be healing the dead, and one that
     * spawns something at a player should not pick one who is no longer in the
     * run. Messages to everyone including the fallen are the exception, and the
     * round loop sends those directly.
     */
    public List<ServerPlayer> playersPublic() {
        List<ServerPlayer> living = livingPlayers();
        return living.isEmpty() ? players() : living;
    }

    /** Adopts a script-spawned mob so the round still counts it. */
    public void adopt(Mob mob) {
        alive.add(mob);
    }

    /**
     * Resolves an action's {@code at} to a position.
     *
     * <p>Scripts name places the way an author thinks about them - "boss", "spawn"
     * - rather than in coordinates, for the same reason markers do. A script that
     * hardcoded numbers would break the moment the map moved.
     */
    public BlockPos scriptAnchor(String name) {
        return switch (name == null ? "boss" : name.toLowerCase(java.util.Locale.ROOT)) {
            case "spawn" -> spawn;
            case "horde" -> hordes.isEmpty() ? spawn : hordes.get(rng.nextInt(hordes.size())).pos();
            case "player" -> {
                List<ServerPlayer> ps = players();
                yield ps.isEmpty() ? spawn : ps.get(0).blockPosition();
            }
            default -> bossPoints.isEmpty() ? spawn : bossPoints.get(0).pos();
        };
    }

    /** The ruleset this run is being played under, for script lookups. */
    public String rulesetId() {
        return rules.id;
    }

    /** Exposed for authors tuning a map, never shown to players in play. */
    public Director director() {
        return director;
    }

    /**
     * Everything the engine currently believes, in one readout.
     *
     * <p>Worth having because almost every "it does not work" in a system like
     * this is really "it is doing something I cannot see". A round that will not
     * advance, a horde that never arrives, a door that changes nothing - each has
     * a different cause and they all look identical from inside the game.
     */
    public java.util.List<String> status() {
        java.util.List<String> out = new ArrayList<>();
        out.add("§6Round §f" + round + "§7, ruleset §f" + rules.id);
        out.add("§7Alive §f" + alive.size() + "§7, still to spawn §f" + leftToSpawn
                + "§7, breather §f" + (breather / 20) + "s");
        out.add("§7Players §f" + participants.size() + "§7, fallen §f" + fallen.size());
        out.add("§7Ways in §f" + liveHordes().size() + "§7 of §f" + hordes.size()
                + "§7 (areas open: §f" + String.join(", ", openAreas).trim() + "§7)");
        out.add("§7Extract " + (extract == null ? "§cnone on this map" : "§aset"));
        out.add(director.describe());
        long stalled = level.getGameTime() - lastProgress;
        if (leftToSpawn <= 0 && !alive.isEmpty() && stalled > 60) {
            out.add("§e⚠ No progress for " + (stalled / 20) + "s — "
                    + "something may be unable to reach you.");
        }
        return out;
    }

    public boolean isAreaOpen(String area) {
        return openAreas.contains(area.toLowerCase(java.util.Locale.ROOT));
    }

    public void openArea(String area) {
        openAreas.add(area.toLowerCase(java.util.Locale.ROOT));
    }

    /** True the first time a cache is claimed each round. */
    public boolean claimLoot(BlockPos pos) {
        return lootTaken.add(pos.immutable());
    }

    /**
     * Changes a block and remembers what was there.
     *
     * <p>Everything the engine writes during a run goes through here. The first
     * value recorded for a position wins, so a block changed twice still reverts
     * to what the author built rather than to an intermediate state.
     */
    public void setTracked(BlockPos pos, BlockState state) {
        BlockPos key = pos.immutable();
        if (!restore.containsKey(key)) {
            restore.put(key, level.getBlockState(key));
        }
        level.setBlock(key, state, 3);
    }

    /** Puts the map back exactly as the author left it. */
    private void restoreWorld() {
        for (java.util.Map.Entry<BlockPos, BlockState> e : restore.entrySet()) {
            level.setBlock(e.getKey(), e.getValue(), 3);
        }
        restore.clear();
    }

    /**
     * Arms a trap, if it is not already burning or cooling.
     *
     * @return a message for the buyer, or null if it fired
     */
    /** Why this trap cannot be armed right now, or null if it can. */
    public String trapUnavailable(BlockPos at, long now) {
        if (trapsActive.getOrDefault(at, 0L) > now) {
            return "§7That is already running.";
        }
        if (trapsCooling.getOrDefault(at, 0L) > now) {
            return "§7Still cooling — §f" + ((trapsCooling.get(at) - now) / 20) + "s§7.";
        }
        return null;
    }

    /** Arms a trap. Availability is checked separately, before the player pays. */
    public void armTrap(BlockPos at, int seconds, int cooldownSeconds) {
        long now = level.getGameTime();
        trapsActive.put(at.immutable(), now + seconds * 20L);
        trapsCooling.put(at.immutable(), now + (seconds + cooldownSeconds) * 20L);
    }

    /** Burns anything standing in an armed trap. */
    private void tickTraps() {
        if (trapsActive.isEmpty() || level.getGameTime() % 10L != 0L) {
            return;
        }
        long now = level.getGameTime();
        for (Marker t : traps) {
            Long until = trapsActive.get(t.pos());
            if (until == null || until <= now) {
                continue;
            }
            double radius = Math.max(1, t.intArg("radius", 4));
            float damage = Math.max(1, t.intArg("damage", 10));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    t.pos().getX() + 0.5, t.pos().getY() + 1.0, t.pos().getZ() + 0.5,
                    12, radius / 2.0, 0.5, radius / 2.0, 0.01);
            for (Mob m : alive) {
                if (m.isAlive() && m.blockPosition().distSqr(t.pos()) <= radius * radius) {
                    m.hurt(level.damageSources().magic(), damage);
                }
            }
        }
    }

    /**
     * Paired pads. Stepping on one puts you on the other.
     *
     * <p>The cooldown is the whole trick: without it the destination pad sends you
     * straight back, and a player stands there flickering between two rooms until
     * something kills them.
     */
    private void tickTeleports(List<ServerPlayer> present) {
        if (teleports.size() < 2) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer p : present) {
            if (teleportCooldown.getOrDefault(p.getUUID(), 0L) > now) {
                continue;
            }
            for (Marker pad : teleports) {
                if (p.blockPosition().distSqr(pad.pos()) > 2.25) {
                    continue;
                }
                Marker other = partnerOf(pad);
                if (other == null) {
                    continue;
                }
                p.teleportTo(level, other.pos().getX() + 0.5, other.pos().getY() + 1,
                        other.pos().getZ() + 0.5, java.util.Set.of(), p.getYRot(), 0.0F);
                teleportCooldown.put(p.getUUID(), now + 60L);
                level.playSound(null, other.pos(), SoundEvents.PORTAL_TRAVEL,
                        SoundSource.PLAYERS, 0.4F, 1.6F);
                break;
            }
        }
    }

    /** The other pad sharing this one's id. */
    private Marker partnerOf(Marker pad) {
        String id = pad.arg("id", pad.arg("value", ""));
        for (Marker other : teleports) {
            if (other != pad && other.arg("id", other.arg("value", "")).equals(id)) {
                return other;
            }
        }
        return null;
    }

    /**
     * What a mob's role does to it.
     *
     * <p>The field was being parsed and thrown away. Roles are worth having only
     * if they express something attributes cannot - an author can already set
     * health and speed directly - so each one is a behaviour package rather than a
     * number: a brute that shrugs off hits but lumbers, a runner that closes
     * distance, a leaper that comes over the thing you were hiding behind.
     */
    private void applyRole(Mob mob, String role) {
        switch (role == null ? "" : role) {
            case "runner" -> mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false));
            case "brute" -> {
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, false, false));
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 0, false, false));
            }
            case "leaper" -> mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.JUMP, Integer.MAX_VALUE, 3, false, false));
            case "armoured", "armored" -> mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2, false, false));
            default -> {
                // "grunt" and anything unrecognised: an ordinary one.
            }
        }
    }

    /**
     * Tells a player what the invisible thing they are looking at is selling.
     *
     * <p>Marker Blocks made a finished map look finished, and took the shop front
     * with them - a dealer you cannot see is a dealer nobody finds. Vanilla's
     * wall-buys answer this with a floating label; this is the same idea on the
     * action bar, which costs no renderer and no new packet.
     *
     * <p>Four times a second, only for players in the run, and only within five
     * blocks - close enough that you are clearly looking <em>at</em> something
     * rather than past it.
     */
    private void tickPrompts(List<ServerPlayer> present) {
        if (level.getGameTime() % 5L != 0L) {
            return;
        }
        for (ServerPlayer p : present) {
            var hit = p.pick(5.0, 0.0F, false);
            if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr)) {
                continue;
            }
            if (!(level.getBlockEntity(bhr.getBlockPos())
                    instanceof com.jrpetty.aztecabyss.block.MarkerBlockEntity be)) {
                continue;
            }
            String kind = be.kind().toLowerCase(java.util.Locale.ROOT);
            if (kind.equals("dealer")) {
                DealerSign.Offer offer = DealerSign.parse(be);
                if (offer != null) {
                    p.displayClientMessage(Component.literal(DealerSign.prompt(offer,
                            offer.currency().balance(p) >= offer.price())), true);
                }
                continue;
            }
            String label = promptFor(kind, be);
            if (!label.isEmpty()) {
                p.displayClientMessage(Component.literal(label), true);
            }
        }
    }

    /** What the other buyable machines say when you look at them. */
    private String promptFor(String kind, com.jrpetty.aztecabyss.block.MarkerBlockEntity be) {
        Marker m = Marker.parse(be, level.getBlockState(be.getBlockPos()));
        if (m == null) {
            return "";
        }
        Currency c = Currency.byId(m.arg("currency", null));
        return switch (kind) {
            case "box" -> "§6The Box §8— §f" + c.format(m.intArg("price", 950)) + " §8(right-click)";
            case "perk" -> "§dPerk §8— §f" + c.format(m.intArg("price", 2500)) + " §8(right-click)";
            case "upgrade" -> "§bUpgrade §8— §f" + c.format(m.intArg("price", 5000)) + " §8(right-click)";
            case "door" -> "§eOpen §f" + m.arg("area", m.arg("value", "the way on"))
                    + " §8— §f" + c.format(m.intArg("cost", 1500)) + " §8(right-click)";
            case "trap" -> "§cArm the trap §8— §f" + c.format(m.intArg("cost", 1000)) + " §8(right-click)";
            case "loot" -> "§aSupplies §8(right-click)";
            default -> "";
        };
    }

    /**
     * Free mode's bar: whatever the map says, and how long you have been at it.
     *
     * <p>A run with no rounds still needs one line that is always true, and a
     * clock is the only thing every non-arena game has in common. The map can
     * write the rest with {@code set_bar} - "3 of 5 idols", "reach the roof",
     * "42 seconds left" - and the clock is appended so a race has a time without
     * the author having to build one.
     */
    private void tickFreeBar(List<ServerPlayer> present) {
        if (elapsed % 20 != 0) {
            return;
        }
        // A free-mode map has no round to hang rules off, so this is the only
        // recurring event it gets. Once a second, which is as fine-grained as a
        // deadline or a "have they collected them all yet" check ever needs, and
        // cheap enough that a map full of rules cannot make it hurt.
        Script.fire(this, level, rules.id, "tick", present.isEmpty() ? null : present.get(0));

        String clock = MazeStyleTime.of(elapsed / 20);
        bar.setName(Component.literal(barText.isEmpty()
                ? "§6" + clock
                : barText + " §8| §7" + clock));
        bar.setProgress(1.0F);
    }

    /** m:ss, which is how anybody reads a run time. */
    static final class MazeStyleTime {
        static String of(int seconds) {
            int m = seconds / 60;
            int s = seconds % 60;
            return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
        }
    }

    public Vars vars() {
        return vars;
    }

    /**
     * Fires {@code region_enter} and {@code region_leave} as people move.
     *
     * <p>This is the other half of what a map needs to be a game rather than an
     * arena. Rounds let a map react to <em>when</em>; regions let it react to
     * <em>where</em>, and almost everything that is not round-survival is built on
     * where somebody is standing - a checkpoint, a capture point, a vault, a
     * finish line, the room you are not supposed to be in yet.
     *
     * <p>Edge-triggered on purpose. A rule that ran every tick you stood in a
     * region would make "give the player a diamond when they reach the vault" into
     * a diamond every tick, and an author would have to invent their own latch to
     * get the obvious behaviour. Entering fires once, leaving fires once, and
     * standing still fires nothing.
     */
    private void tickRegions(List<ServerPlayer> present) {
        if (regions.isEmpty() || level.getGameTime() % 5L != 0L) {
            return;
        }
        for (ServerPlayer p : present) {
            java.util.Set<String> was = inRegion.computeIfAbsent(
                    p.getUUID(), id -> new java.util.HashSet<>());
            java.util.Set<String> now = new java.util.HashSet<>();
            for (Marker r : regions) {
                String id = r.arg("id", r.arg("value", "")).toLowerCase(java.util.Locale.ROOT);
                if (id.isEmpty()) {
                    continue;
                }
                double radius = Math.max(1, r.intArg("radius", 4));
                int height = Math.max(1, r.intArg("height", 4));
                BlockPos at = p.blockPosition();
                boolean inside = at.distToLowCornerSqr(
                        r.pos().getX(), at.getY(), r.pos().getZ()) <= radius * radius
                        && Math.abs(at.getY() - r.pos().getY()) <= height;
                if (inside) {
                    now.add(id);
                }
            }
            for (String id : now) {
                if (was.add(id)) {
                    Script.fireRegion(this, level, rules.id, "region_enter", p, id);
                }
            }
            was.removeIf(id -> {
                if (now.contains(id)) {
                    return false;
                }
                Script.fireRegion(this, level, rules.id, "region_leave", p, id);
                return true;
            });
        }
    }

    /** Drives the map's objective, and reacts when it resolves. */
    private void tickObjective(List<ServerPlayer> present) {
        if (objective == null) {
            return;
        }
        if (!objective.tick(level, present)) {
            return;
        }
        if (objective.complete()) {
            for (ServerPlayer p : players()) {
                p.displayClientMessage(Component.literal("§a§lOBJECTIVE COMPLETE"), false);
            }
            Script.fire(this, level, rules.id, "objective_complete", null);
        } else if (objective.failed()) {
            for (ServerPlayer p : players()) {
                p.displayClientMessage(Component.literal("§4§lOBJECTIVE LOST"), false);
            }
            Script.fire(this, level, rules.id, "objective_failed", null);
            if (objective.failEndsRun()) {
                stop(false);
            }
        }
    }

    /** Right-clicking a collect objective hands in what you are carrying. */
    public boolean handInTo(ServerPlayer player, Marker marker) {
        if (objective == null || !objective.marker().pos().equals(marker.pos())) {
            return false;
        }
        if (!objective.handIn(player)) {
            player.displayClientMessage(Component.literal(
                    "§7Nothing to hand in."), true);
            return true;
        }
        for (ServerPlayer p : players()) {
            p.displayClientMessage(Component.literal("§6Delivered." + objective.hud()), true);
        }
        if (objective.complete()) {
            for (ServerPlayer p : players()) {
                p.displayClientMessage(Component.literal("§a§lOBJECTIVE COMPLETE"), false);
            }
            Script.fire(this, level, rules.id, "objective_complete", null);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Down and revive
    // ------------------------------------------------------------------

    public boolean isDowned(UUID id) {
        return downed.containsKey(id);
    }

    /**
     * Whether this player should go down rather than die.
     *
     * <p>Requires somebody able to come for them. A lone player going down is not
     * a rescue with a clock on it, it is a death with a wait attached - so unless
     * a ruleset asks for it, solo death stays immediate and final.
     */
    public boolean canGoDown(ServerPlayer player) {
        if (!rules.downedEnabled || downed.containsKey(player.getUUID())
                || fallen.contains(player.getUUID())) {
            return false;
        }
        if (rules.downedSolo) {
            return true;
        }
        for (ServerPlayer other : livingPlayers()) {
            if (other != player) {
                return true;
            }
        }
        return false;
    }

    /** Puts a player on the floor with a clock running. */
    public void goDown(ServerPlayer player) {
        downed.put(player.getUUID(), rules.bleedoutSeconds * 20);
        reviving.put(player.getUUID(), 0);
        player.setHealth(1.0F);
        // Immobilised and unmistakable: a downed teammate has to be findable
        // across a dark room or nobody can choose to go for them.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                rules.bleedoutSeconds * 20, 5, false, false));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WEAKNESS,
                rules.bleedoutSeconds * 20, 9, false, false));
        player.setGlowingTag(true);
        for (ServerPlayer p : players()) {
            p.displayClientMessage(Component.literal(
                    "§c" + player.getGameProfile().getName() + " is down."), false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ANGRY,
                SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /** Bleed-out, and teammates picking people up. */
    private void tickDowned(List<ServerPlayer> present) {
        if (downed.isEmpty()) {
            return;
        }
        java.util.List<UUID> lost = new ArrayList<>();
        // Killing has to happen after the map is cleared, not during. The damage
        // interception that keeps a downed player alive reads this same map, so a
        // killing blow struck while the victim is still in it gets cancelled by
        // the system that put them there - and they stand back up at one health,
        // marked dead, unable to be revived and unable to die.
        java.util.List<ServerPlayer> finish = new ArrayList<>();
        for (java.util.Map.Entry<UUID, Integer> e : downed.entrySet()) {
            ServerPlayer victim = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (victim == null) {
                lost.add(e.getKey());
                continue;
            }
            ServerPlayer helper = reviverFor(victim, present);
            if (helper != null) {
                int progress = reviving.merge(victim.getUUID(), 1, Integer::sum);
                int need = rules.reviveSeconds * 20;
                if (progress >= need) {
                    lift(victim, helper);
                    lost.add(e.getKey());
                    continue;
                }
                if (progress % 10 == 0) {
                    String pct = (progress * 100 / need) + "%";
                    helper.displayClientMessage(Component.literal(
                            "§eReviving " + victim.getGameProfile().getName() + " — §f" + pct), true);
                    victim.displayClientMessage(Component.literal(
                            "§aBeing picked up — §f" + pct), true);
                }
                continue;
            }
            // Nobody is helping, so the clock runs. Progress is kept rather than
            // reset, because a rescuer driven off for a second and coming back
            // should not have to start again.
            int left = e.getValue() - 1;
            e.setValue(left);
            if (left <= 0) {
                lost.add(e.getKey());
                bleedOut(victim);
                finish.add(victim);
            } else if (left % 20 == 0) {
                victim.displayClientMessage(Component.literal(
                        "§cBleeding out — §f" + (left / 20) + "s"), true);
            }
        }
        for (UUID id : lost) {
            downed.remove(id);
            reviving.remove(id);
        }
        for (ServerPlayer victim : finish) {
            victim.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    /** A living teammate close enough and still enough to be helping. */
    private ServerPlayer reviverFor(ServerPlayer victim, List<ServerPlayer> present) {
        double range = rules.reviveRange;
        for (ServerPlayer p : present) {
            if (p == victim || downed.containsKey(p.getUUID()) || fallen.contains(p.getUUID())) {
                continue;
            }
            if (p.distanceToSqr(victim) <= range * range) {
                return p;
            }
        }
        return null;
    }

    private void lift(ServerPlayer victim, ServerPlayer helper) {
        victim.setGlowingTag(false);
        victim.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        victim.removeEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        victim.setHealth(Math.max(1.0F, victim.getMaxHealth() * 0.5F));
        for (ServerPlayer p : players()) {
            p.displayClientMessage(Component.literal(
                    "§a" + helper.getGameProfile().getName() + " picked "
                            + victim.getGameProfile().getName() + " up."), false);
        }
        level.playSound(null, victim.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    /**
     * Announces a bleed-out and marks the victim down.
     *
     * <p>Deliberately does not deal the killing blow - see {@link #tickDowned}.
     */
    private void bleedOut(ServerPlayer victim) {
        victim.setGlowingTag(false);
        fallen.add(victim.getUUID());
        bar.removePlayer(victim);
        barred.remove(victim.getUUID());
        victim.displayClientMessage(Component.literal(
                "§4§lYou bled out."), false);
        for (ServerPlayer p : players()) {
            if (p != victim) {
                p.displayClientMessage(Component.literal(
                        "§4" + victim.getGameProfile().getName() + " did not make it."), false);
            }
        }
    }

    /** Kills everything currently in the wave. Used by the Purge drop. */
    public void purge() {
        for (Mob m : alive) {
            if (m.isAlive()) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                        m.getX(), m.getY() + 0.5, m.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
                m.hurt(level.damageSources().magic(), Float.MAX_VALUE);
            }
        }
        lastProgress = level.getGameTime();
    }

    /** The horde markers currently allowed to send anything. */
    private List<Marker> liveHordes() {
        List<Marker> live = new ArrayList<>();
        for (Marker h : hordes) {
            if (isAreaOpen(h.arg("area", ""))) {
                live.add(h);
            }
        }
        // A map whose every horde marker sits behind a door would otherwise stall
        // on round one with nothing able to spawn and nothing able to be killed.
        return live.isEmpty() ? hordes : live;
    }

    private void tickZones(List<ServerPlayer> present) {
        if (zones.isEmpty()) {
            return;
        }
        for (Marker zone : zones) {
            int radius = zone.intArg("radius", 8);
            int r2 = radius * radius;
            for (ServerPlayer p : present) {
                if (p.blockPosition().distSqr(zone.pos()) <= r2) {
                    Machines.applyZone(p, zone);
                }
            }
        }
    }

    /**
     * Hand-placed enemies, for the fights a weighted table cannot describe.
     *
     * <pre>
     *   [Spawner]
     *   minecraft:skeleton
     *   count=4 round=5 every=5
     * </pre>
     */
    private void runSpawners() {
        for (Marker s : spawners) {
            int from = s.intArg("round", 1);
            int every = Math.max(0, s.intArg("every", 0));
            boolean due = round == from || (every > 0 && round > from && (round - from) % every == 0);
            if (!due) {
                continue;
            }
            String id = s.arg("id", s.arg("value", "minecraft:zombie"));
            int count = Math.max(1, Math.min(32, s.intArg("count", 1)));
            for (int i = 0; i < count; i++) {
                spawnAt(id, s.pos(), s.intArg("health", 0), s.intArg("damage", 0));
            }
        }
    }

    /** Boss rounds, if the map marked somewhere for one to come in. */
    private void maybeBoss() {
        if (bossPoints.isEmpty()) {
            return;
        }
        for (Marker b : bossPoints) {
            int every = Math.max(0, b.intArg("every", 10));
            if (every == 0 || round % every != 0) {
                continue;
            }
            String id = b.arg("id", b.arg("value", "minecraft:warden"));
            spawnAt(id, b.pos(), b.intArg("health", 0), b.intArg("damage", 0));
            for (ServerPlayer p : players()) {
                p.displayClientMessage(Component.literal(
                        "§4§lSOMETHING ELSE IS COMING"), false);
            }
        }
    }

    /** Puts one named entity at a point, optionally overriding its numbers. */
    private void spawnAt(String entityId, BlockPos at, int health, int damage) {
        var type = EntityType.byString(entityId);
        if (type.isEmpty()) {
            return;
        }
        Entity entity = type.get().create(level);
        if (!(entity instanceof Mob mob)) {
            return;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);
        if (health > 0) {
            setAttr(mob, Attributes.MAX_HEALTH, Math.min(1024, health));
        }
        if (damage > 0) {
            setAttr(mob, Attributes.ATTACK_DAMAGE, Math.min(256, damage));
        }
        mob.setHealth(mob.getMaxHealth());
        mob.getPersistentData().putBoolean("aztecabyss_engine_mob", true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        ServerPlayer target = nearestPlayer(at);
        if (target != null) {
            mob.setTarget(target);
        }
        alive.add(mob);
    }

    /**
     * Finds somewhere a mob can actually stand near a horde marker.
     *
     * <p>This used to be "two blocks behind the sign", which was wrong in a way
     * that broke every wall-mounted marker on every map. A wall sign's facing
     * points <em>away</em> from the wall - that is the side you read it from - so
     * stepping backwards from it walks straight into the stone it is nailed to.
     * Mobs were being spawned inside solid blocks, where they suffocate or get
     * shoved out somewhere arbitrary.
     *
     * <p>So the position is searched for rather than calculated: the marker itself
     * first, then progressively further out along the way it faces, then a step to
     * either side. A candidate has to have room for a body and something
     * underneath to stand on.
     */
    private BlockPos spawnPointFor(Marker gate) {
        // A [Pen] near this way in means the author built somewhere for the horde
        // to arrive out of sight, so use it. That is the difference between mobs
        // blinking into existence in front of you and mobs walking out of a dark
        // room - the same fight, told better.
        Marker pen = nearestPen(gate.pos());
        if (pen != null) {
            for (int dy = 0; dy <= 2; dy++) {
                if (standable(pen.pos().above(dy))) {
                    return pen.pos().above(dy);
                }
            }
        }
        Direction out = gate.facing();
        Direction side = out.getClockWise();
        BlockPos base = gate.pos();
        for (int distance = 0; distance <= 4; distance++) {
            for (int lateral = 0; lateral <= 2; lateral++) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    BlockPos candidate = base.relative(out, distance)
                            .relative(side, lateral * sign);
                    if (standable(candidate)) {
                        return candidate;
                    }
                    if (lateral == 0) {
                        break; // no point testing the same block twice
                    }
                }
            }
        }
        return null;
    }

    /** The closest pen to a way in, if one is near enough to have been meant for it. */
    private Marker nearestPen(BlockPos gate) {
        Marker best = null;
        double bestDist = 16 * 16;
        for (Marker p : pens) {
            double d = p.pos().distSqr(gate);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Room for a mob, and a floor beneath it. */
    private boolean standable(BlockPos pos) {
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }
        return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private void spawnOne() {
        List<Marker> live = liveHordes();
        Marker gate = live.get(rng.nextInt(live.size()));
        Ruleset.MobEntry pick = pickMob();
        if (pick == null) {
            leftToSpawn = 0;
            return;
        }
        var maybeType = EntityType.byString(pick.entityId());
        if (maybeType.isEmpty()) {
            leftToSpawn--;
            return;
        }
        Entity entity = maybeType.get().create(level);
        if (!(entity instanceof Mob mob)) {
            leftToSpawn--;
            return;
        }
        BlockPos at = spawnPointFor(gate);
        if (at == null) {
            // Nowhere to put it. Better to skip one than to bury it in stone,
            // which would leave the round waiting on something that cannot move.
            leftToSpawn--;
            return;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);

        double healthMul = rules.healthMultiplier(round);
        double damageMul = rules.damageMultiplier(round);
        setAttr(mob, Attributes.MAX_HEALTH, pick.maxHealth() * healthMul);
        setAttr(mob, Attributes.MOVEMENT_SPEED, pick.speed());
        setAttr(mob, Attributes.ATTACK_DAMAGE, pick.attackDamage() * damageMul);
        mob.setHealth(mob.getMaxHealth());
        applyRole(mob, pick.role());
        equip(mob, pick);
        mob.getPersistentData().putBoolean("aztecabyss_engine_mob", true);
        mob.setPersistenceRequired();

        level.addFreshEntity(mob);
        ServerPlayer target = nearestPlayer(at);
        if (target != null) {
            mob.setTarget(target);
        }
        alive.add(mob);
        leftToSpawn--;
    }

    /** Weighted choice among everything unlocked at this round. */
    private Ruleset.MobEntry pickMob() {
        if (rules.mobs.isEmpty()) {
            // A ruleset with no mob table still has to produce a game.
            return new Ruleset.MobEntry("minecraft:zombie", 1, 1, "grunt",
                    20.0, 0.25, 3.0, "", "");
        }
        int total = 0;
        for (Ruleset.MobEntry m : rules.mobs) {
            if (eligible(m)) {
                total += m.weight();
            }
        }
        // A special round that filters the table down to nothing would otherwise
        // stall the round forever, so an impossible filter is simply ignored.
        if (total <= 0) {
            special = null;
            for (Ruleset.MobEntry m : rules.mobs) {
                if (m.fromRound() <= round) {
                    return m;
                }
            }
            return rules.mobs.get(0);
        }
        int roll = rng.nextInt(total);
        for (Ruleset.MobEntry m : rules.mobs) {
            if (!eligible(m)) {
                continue;
            }
            roll -= m.weight();
            if (roll < 0) {
                return m;
            }
        }
        return rules.mobs.get(0);
    }

    /** Unlocked at this round, and allowed by any special round in force. */
    private boolean eligible(Ruleset.MobEntry m) {
        return m.fromRound() <= round && (special == null || special.allows(m));
    }

    private void equip(Mob mob, Ruleset.MobEntry entry) {
        ItemStack hand = itemOf(entry.mainHand());
        if (!hand.isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hand);
        }
        ItemStack head = itemOf(entry.head());
        if (!head.isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
        }
    }

    private static ItemStack itemOf(String id) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
    }

    private static void setAttr(Mob mob,
                                net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    /**
     * Participants still actually in the fight.
     *
     * <p>{@link #players()} is everyone who ever joined, which includes the dead.
     * That is the right list for telling people what happened and the wrong one
     * for anything the run acts on - hence both, named for what they are.
     */
    /** Hands the round bar to whichever player object is currently live. */
    private void refreshBars(List<ServerPlayer> present) {
        for (ServerPlayer p : present) {
            ServerPlayer held = barred.get(p.getUUID());
            if (held == p) {
                continue;
            }
            if (held != null) {
                bar.removePlayer(held);
            }
            bar.addPlayer(p);
            barred.put(p.getUUID(), p);
        }
    }

    private List<ServerPlayer> livingPlayers() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : players()) {
            if (!fallen.contains(p.getUUID())
                    && !downed.containsKey(p.getUUID())
                    && p.level().dimension().equals(level.dimension())
                    && !p.isDeadOrDying()) {
                out.add(p);
            }
        }
        return out;
    }

    private ServerPlayer nearestPlayer(BlockPos from) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        // Living only. Targeting the whole participant list meant a mob would
        // lock onto somebody who had already died and respawned somewhere else -
        // so in co-op the horde would walk away from the people still fighting
        // and go stand near a corpse's respawn point.
        for (ServerPlayer p : livingPlayers()) {
            double d = p.blockPosition().distSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Pays for a kill, if this run has an economy. */
    public static void onKill(Mob mob, ServerPlayer killer) {
        EngineArena a = current;
        if (a == null || !a.running || killer == null
                || !mob.getPersistentData().getBoolean("aztecabyss_engine_mob")) {
            return;
        }
        if (a.rules.economyEnabled) {
            int paid = a.rules.pointsKill;
            if (EnginePowerUps.doublePoints(a.level)) {
                paid *= 2;
            }
            Currency.byId(a.rules.defaultCurrency).award(killer, paid);
        }
        EnginePowerUps.maybeDrop(a.level, mob, a.rules.powerupChance, a.rng);
        // A kill is progress, which resets the stall clock. Without this a long
        // hard round with a slow weapon would be mistaken for a stuck one.
        a.lastProgress = a.level.getGameTime();
        Script.fire(a, a.level, a.rules.id, "mob_killed", killer);
    }

    public int round() {
        return round;
    }

    public String mapName() {
        return mapName;
    }

    public BoundingBox bounds() {
        return bounds;
    }
}
