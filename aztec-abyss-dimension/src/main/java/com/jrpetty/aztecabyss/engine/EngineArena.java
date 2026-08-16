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

    /** The run's own randomness, so the script layer rolls the same dice the arena does. */
    public RandomSource rng() {
        return rng;
    }

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

    /**
     * This round's exact roster, if the ruleset named one.
     *
     * <p>Drained as mobs go out. Empty means the weighted table decides, which
     * is every round a map did not write a line for - so an author writes the
     * three rounds that matter and leaves the rest to roll.
     */
    private final List<Ruleset.WaveEntry> waveQueue = new ArrayList<>();
    private int breather = 0;
    private boolean running = true;

    /**
     * What part of the game this is.
     *
     * <p>A run used to begin the instant somebody walked in. There was no
     * before - no lobby, no start line, no countdown, no warmup, no overtime and
     * no intermission - so every game in this engine started mid-sentence. That
     * ruled out every format with a beginning: a reaping, a ready-up, a race
     * start, a draft, a shop phase between rounds, sudden death.
     *
     * <p>Deliberately a free-form string rather than an enum. The engine ships
     * {@code lobby}, {@code countdown} and {@code active} because those are the
     * three every game needs, and an author can invent {@code overtime} or
     * {@code intermission} without asking anybody - a phase is only ever a name
     * that rules can be gated on and the script can set.
     */
    private String phase = PHASE_ACTIVE;
    /** Ticks spent in the current phase, so a phase can have a clock of its own. */
    private int phaseTicks = 0;

    public static final String PHASE_LOBBY = "lobby";
    public static final String PHASE_COUNTDOWN = "countdown";
    public static final String PHASE_ACTIVE = "active";

    public String phase() {
        return phase;
    }

    public int phaseSeconds() {
        return phaseTicks / 20;
    }

    /**
     * Moves the game to a named phase.
     *
     * <p>Fires {@code phase_start} carrying the new name, so a ruleset can hang
     * anything it likes off the transition rather than needing the engine to know
     * what an overtime is.
     */
    public void setPhase(String next) {
        if (next == null || next.isEmpty() || next.equals(phase)) {
            return;
        }
        phase = next;
        phaseTicks = 0;
        Script.fire(this, level, rules.id, "phase_start", players().isEmpty()
                ? null : players().get(0), null, next);
    }

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
    /** Patrol legs, in the order an author numbered them. */
    private final List<Marker> waypoints = new ArrayList<>();
    /** Optional out-of-sight spawn chambers, paired to the nearest way in. */
    private final List<Marker> pens = new ArrayList<>();
    private final List<Marker> teleports = new ArrayList<>();
    /** Named volumes that fire an event when somebody walks in or out. */
    private final List<Marker> regions = new ArrayList<>();
    /** The map's boarded openings, if it marked any. */
    private Barricades barricades;
    /** Who is currently inside which region, so enter and leave fire once each. */
    private final java.util.Map<UUID, java.util.Set<String>> inRegion = new java.util.HashMap<>();
    /** What this run remembers. */
    private final Vars vars = new Vars();
    /** Sides, if the map declared any. */
    private Teams teams;
    /** Team spawn points, by team id. */
    private final java.util.Map<String, Marker> teamSpawns = new java.util.HashMap<>();
    /** Every [Spawn] on the map, for scattered starts. */
    private final List<Marker> allSpawns = new ArrayList<>();
    /** Which pedestal each player was given, so a respawn returns them to it. */
    private final java.util.Map<UUID, BlockPos> mySpawn = new java.util.HashMap<>();

    /**
     * Puts a player back in rather than out.
     *
     * <p>Called instead of marking them fallen when the ruleset allows respawns.
     * Deliberately no death screen: they are healed, sent to their spawn and given
     * a few seconds of standing still to think about it. A competitive mode that
     * makes you click through a death screen every thirty seconds is a worse game
     * than one that does not, and the screen carries no information a respawning
     * player wants.
     */
    private void respawn(ServerPlayer player) {
        BlockPos at = spawnFor(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.clearFire();
        player.teleportTo(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), 0.0F);
        int ticks = Math.max(1, rules.respawnSeconds) * 20;
        // Briefly untouchable and slow, so a spawn camp is not a strategy and the
        // player has a beat to work out where they are.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, ticks, 4, false, false));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, ticks, 2, false, false));
        giveKit(player);
        player.displayClientMessage(Component.literal(
                "§7Back in. §8" + rules.respawnSeconds + "s to get your bearings."), true);
        level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.5F);
    }

    /**
     * Whether this run hands players their lives back.
     *
     * <p>Read by the damage interception, which has to decide between downing
     * somebody, respawning them and letting them die - three different games.
     */
    public boolean respawns() {
        return rules.respawnEnabled;
    }

    public void respawnNow(ServerPlayer player) {
        respawn(player);
    }

    /**
     * What a player is handed on every spawn.
     *
     * <p>Named from the pools block, so the same weighted-list syntax that fills
     * the Box fills a loadout. A map that starts you with a bow and eight arrows
     * is a different game from one that starts you with a stone sword, and it was
     * the one thing about a map an author could not touch at all.
     */
    public void giveKit(ServerPlayer player) {
        if (rules.kitPool.isEmpty()) {
            return;
        }
        ItemPool pool = rules.pools.get(rules.kitPool.toLowerCase(java.util.Locale.ROOT));
        if (pool == null || pool.isEmpty()) {
            return;
        }
        // Every entry once, not a random draw - a loadout is a list of what you
        // get, and rolling it would make two players on the same team start with
        // different equipment for no reason anybody asked for.
        for (int i = 0; i < pool.size(); i++) {
            ItemStack stack = pool.rollAt(i, level);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

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
        // A scattered map hands out one pedestal each and remembers who got which,
        // so a respawn puts you back where you started rather than shuffling you
        // into somebody else's corner mid-match.
        if (rules.scatterSpawns && !allSpawns.isEmpty()) {
            BlockPos mine = mySpawn.get(player.getUUID());
            if (mine != null) {
                return mine;
            }
            java.util.Set<BlockPos> taken = new java.util.HashSet<>(mySpawn.values());
            for (Marker sp : allSpawns) {
                if (!taken.contains(sp.pos())) {
                    mySpawn.put(player.getUUID(), sp.pos());
                    return sp.pos();
                }
            }
            // More players than pedestals: wrap round rather than refuse. A map
            // that is one short should be crowded, not unplayable.
            Marker fallback = allSpawns.get(mySpawn.size() % allSpawns.size());
            mySpawn.put(player.getUUID(), fallback.pos());
            return fallback.pos();
        }
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
        current.waypoints.addAll(scan.of("waypoint"));
        current.pens.addAll(scan.of("pen"));
        current.teleports.addAll(scan.of("teleport"));
        current.regions.addAll(scan.of("region"));
        current.barricades = new Barricades(current, current.level, scan.of("barricade"));
        // A [Spawn] carrying team= is that side's spawn rather than the map's.
        current.allSpawns.addAll(scan.of("spawn"));
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
        // With a lobby, creating the arena and beginning play stop being the
        // same act. Nothing starts until the countdown runs out - which is the
        // entire point of having a before.
        if (rules.lobbyMinPlayers > 1 || rules.lobbyCountdownSeconds > 0) {
            current.phase = PHASE_LOBBY;
            current.phaseTicks = 0;
            current.bar.setName(Component.literal("§eWaiting for players"));
        } else if (rules.free) {
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
        // spawnFor rather than the map's single spawn, so a team map puts people
        // on their own side from the first second rather than only after a death.
        BlockPos at = spawnFor(player);
        player.teleportTo(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), 0.0F);
        if (rules.economyEnabled) {
            Currency c = Currency.byId(rules.defaultCurrency);
            c.set(player, c.start());
        }
        giveKit(player);
        // Somebody arriving is a thing a map may want to react to - a lobby
        // greeting, a team assignment, a headcount that opens the gate. The
        // engine knew it had happened and told nobody.
        Script.fire(this, level, rules.id, "player_joined", player, null,
                String.valueOf(participants.size()));
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
        // Anything queued dies with the run. A delayed action firing into a run
        // that has ended would act on an arena nobody is in.
        current.scheduled.clear();
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
            // A lobby that empties is a lobby, not a loss. Everywhere else an
            // empty arena means everybody died; here it means nobody turned up.
            stop(false);
            return;
        }
        // Anyone who has died is out, and stays out - respawning does not put you
        // back in a run you already lost.
        for (ServerPlayer p : present) {
            if (!p.isDeadOrDying()) {
                continue;
            }
            // The engine had no event for a player dying, which is a strange
            // hole in a game engine: "drop your flag when you die", "count the
            // deaths", "respawn with a penalty" and every elimination format
            // that is not the one built in were all unwritable.
            Script.fire(this, level, rules.id, "player_died", p, null,
                    rules.respawnEnabled ? "respawn" : "final");
            if (rules.respawnEnabled) {
                // Back in rather than out. Anything competitive needs this, and a
                // survival arena needs the opposite, so the ruleset decides.
                respawn(p);
                continue;
            }
            fallen.add(p.getUUID());
            bar.removePlayer(p);
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
        // Anything that is not the game itself runs here and then stops. The
        // shops, the doors, the regions and the script all stay live, because a
        // lobby you cannot gear up in or ready up in is a loading screen.
        if (!PHASE_ACTIVE.equals(phase)) {
            phaseTicks++;
            tickScheduled();
            tickTimers();
            tickRegions(present);
            if (phaseTicks % 20 == 0) {
                Script.fire(this, level, rules.id, "tick", present.get(0));
            }
            tickWarmup(present);
            return;
        }
        elapsed++;
        phaseTicks++;

        refreshBars(present);
        tickDowned(present);
        tickObjective(present);
        tickPrompts(present);
        tickRegions(present);
        // Before the mode split, so delayed work fires in a free-mode map and a
        // round-mode one alike. A countdown is not a free-mode idea.
        tickScheduled();
        tickTimers();
        if (barricades != null && !barricades.isEmpty()) {
            barricades.tick();
        }
        MobBrains.tick(level, alive, this);
        tickBossPhases();
        if (level.getGameTime() % 20L == 0L) {
            tickZoneRules(present);
        }
        tickBorder();
        tickLastStanding(present);
        // Once a second, in both modes. A deadline, a countdown and a "have they
        // got them all yet" check are not free-mode ideas, and round mode having
        // no recurring event of its own was an accident of build order.
        if (elapsed % 20 == 0) {
            Script.fire(this, level, rules.id, "tick", present.get(0));
        }

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
        if (powerupChance() > 0 && (special == null || !special.noPowerups())) {
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
            bar.setProgress(1.0F - (breather / (float) Math.max(1, breatherFor(round))));
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
        int cap = Math.max(4, Math.round(concurrentCap() * Math.min(1.5f, Math.max(0.5f, pace))));
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
        // A map that wrote its own bar keeps it. Round mode had no way to say
        // anything of its own here, which made set_bar a free-mode privilege for
        // no reason other than the order the two were built in.
        bar.setName(Component.literal(barText.isEmpty()
                ? "§c§lROUND " + round + " §r§7— §f"
                        + (alive.size() + leftToSpawn) + "§7 left"
                        + (objective == null ? "" : objective.hud())
                : barText + " §8| §7R" + round));
        int total = Math.max(1, countFor(round));
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
    /**
     * The lobby and the countdown, which are the two phases the engine owns.
     *
     * <p>The lobby waits for people and the countdown warns them. Both are on a
     * timeout, because a lobby that waits forever for a fourth player is a lobby
     * that never starts on a quiet evening - the wait is a preference, not a
     * requirement, and the engine should always eventually play the game.
     */
    private void tickWarmup(List<ServerPlayer> present) {
        if (PHASE_LOBBY.equals(phase)) {
            int need = Math.max(1, rules.lobbyMinPlayers);
            int waited = phaseTicks / 20;
            boolean enough = present.size() >= need;
            boolean waitedLongEnough = rules.lobbyWaitSeconds > 0 && waited >= rules.lobbyWaitSeconds;
            bar.setName(Component.literal(enough
                    ? "§aReady §8— §7starting"
                    : "§eWaiting for players §8— §f" + present.size() + "§7/§f" + need
                            + (rules.lobbyWaitSeconds > 0
                                    ? " §8| starts anyway in " + Math.max(0, rules.lobbyWaitSeconds - waited) + "s"
                                    : "")));
            bar.setProgress(Math.min(1.0F, present.size() / (float) need));
            if (enough || waitedLongEnough) {
                setPhase(PHASE_COUNTDOWN);
            }
            return;
        }
        if (!PHASE_COUNTDOWN.equals(phase)) {
            return; // an author's own phase; it is theirs to leave
        }
        int total = Math.max(1, rules.lobbyCountdownSeconds);
        int left = total - phaseTicks / 20;
        bar.setName(Component.literal("§6Starting in §f" + Math.max(0, left) + "s"));
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, left / (float) total)));
        // A tick per second for the last five, because a countdown you cannot
        // hear is a countdown people miss while looking at a chest.
        if (phaseTicks % 20 == 0 && left <= 5 && left > 0) {
            for (ServerPlayer p : present) {
                p.displayClientMessage(Component.literal("§6§l" + left), true);
                level.playSound(null, p.blockPosition(),
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, left == 1 ? 1.6F : 1.0F);
            }
        }
        if (left <= 0) {
            beginPlay(present);
        }
    }

    /**
     * The moment the game actually starts.
     *
     * <p>Separated from {@code start} because those were the same thing and
     * should never have been: creating the arena is a server concern and
     * beginning play is a game concern, and a lobby is exactly the gap between
     * them.
     */
    private void beginPlay(List<ServerPlayer> present) {
        setPhase(PHASE_ACTIVE);
        // Boards go up when the run does, not when the map was stamped: a
        // barricade mended during a lobby is a barricade nobody paid for.
        if (barricades != null && !barricades.isEmpty()) {
            barricades.resetAll();
        }
        for (ServerPlayer p : present) {
            p.displayClientMessage(Component.literal("§a§lGO"), true);
            level.playSound(null, p.blockPosition(),
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        // Exactly what start() used to do inline, now that there is a moment to
        // hang it on. beginRound(1) fires run_start itself, so round mode must
        // not fire it twice.
        if (rules.free) {
            bar.setName(Component.literal("§6" + rules.id));
            Script.fire(this, level, rules.id, "run_start", present.isEmpty() ? null : present.get(0));
        } else {
            beginRound(1);
        }
    }

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
        leftToSpawn = countFor(n);
        // An exact roster overrides both the roll and the count: a round written
        // as twelve husks and one breaker is thirteen mobs, not thirteen rolls
        // that average out to it.
        waveQueue.clear();
        List<Ruleset.WaveEntry> exact = rules.waves.get(n);
        if (exact != null && !exact.isEmpty()) {
            int total = 0;
            for (Ruleset.WaveEntry w : exact) {
                for (int i = 0; i < w.count(); i++) {
                    waveQueue.add(w);
                }
                total += w.count();
            }
            java.util.Collections.shuffle(waveQueue, new java.util.Random(rng.nextLong()));
            leftToSpawn = total;
        }
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
        breather = Math.max(1, breatherFor(round));
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
    /** The world this run is happening in. Conditions need it to read saved state. */
    public ServerLevel level() {
        return level;
    }

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
        if (scheduledCount() > 0) {
            out.add("§7Queued §f" + scheduledCount() + "§7 delayed action(s)");
        }
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

    /**
     * Which region contains a given block, or null.
     *
     * <p>Used by block events so a rule can say "the lever in the vault" rather
     * than "a lever, pulled by somebody standing in the vault" - which are
     * different sentences and only one of them is what an author means.
     */
    /**
     * Where a named region is.
     *
     * <p>The engine could answer "which region is this position in" and not
     * "where is the region called start_line", which is the wrong half. Regions
     * are the only named places an author has - they are how a map says
     * <em>the vault</em>, <em>the start line</em>, <em>jail</em> - and until now
     * nothing could send anybody to one.
     */
    public BlockPos regionPos(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String want = id.toLowerCase(java.util.Locale.ROOT);
        for (Marker r : regions) {
            if (r.arg("id", r.arg("value", "")).toLowerCase(java.util.Locale.ROOT).equals(want)) {
                return r.pos();
            }
        }
        return null;
    }

    public String regionAt(BlockPos at) {
        for (Marker r : regions) {
            String id = r.arg("id", r.arg("value", "")).toLowerCase(java.util.Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            double radius = Math.max(1, r.intArg("radius", 4));
            int height = Math.max(1, r.intArg("height", 4));
            double dx = at.getX() - r.pos().getX();
            double dz = at.getZ() - r.pos().getZ();
            if (dx * dx + dz * dz <= radius * radius
                    && Math.abs(at.getY() - r.pos().getY()) <= height) {
                return id;
            }
        }
        return null;
    }

    /** Whether a position is inside the map at all, for the block-event guard. */
    /**
     * The legs of a named route, in order.
     *
     * <p>Sorted by the {@code order} an author wrote rather than by the order the
     * scan happened to find them in, because a structure block's position inside
     * an NBT file is not a thing anybody can see or control.
     */
    public List<Marker> route(String id) {
        if (id == null || id.isEmpty() || waypoints.isEmpty()) {
            return List.of();
        }
        String want = id.toLowerCase(java.util.Locale.ROOT);
        List<Marker> legs = new ArrayList<>();
        for (Marker w : waypoints) {
            if (w.arg("route", w.arg("value", "")).toLowerCase(java.util.Locale.ROOT).equals(want)) {
                legs.add(w);
            }
        }
        legs.sort(java.util.Comparator.comparingInt(m -> m.intArg("order", 0)));
        return legs;
    }

    /** The map's barricades, or null if it marked none. */
    public Barricades barricades() {
        return barricades;
    }

    // ------------------------------------------------------------------
    // Power
    // ------------------------------------------------------------------

    /**
     * Cuts or restores the light in a named region.
     *
     * <p>ENGINE.md designed an {@code abyss:light} marker - "a light the engine
     * may turn off" - and it was never built, so darkness was something a map
     * could describe and not do. Which matters more than it sounds: the lights
     * going out is the single cheapest way to change a room you have already
     * shown somebody, and every horror map ever written turns on it.
     *
     * <p>Every emitter in the region is taken out through the tracked-block
     * system, so restoring is the same machinery that cleans a run up afterwards
     * rather than a second remembering of the same thing. Turning the power back
     * on puts the exact blocks back, wall torches facing the way they faced.
     *
     * @return how many blocks were changed
     */
    public int power(String regionId, boolean on) {
        Marker region = null;
        String want = regionId == null ? "" : regionId.toLowerCase(java.util.Locale.ROOT);
        for (Marker r : regions) {
            if (r.arg("id", r.arg("value", "")).toLowerCase(java.util.Locale.ROOT).equals(want)) {
                region = r;
                break;
            }
        }
        if (region == null) {
            return 0;
        }
        int radius = Math.max(1, region.intArg("radius", 8));
        int height = Math.max(1, region.intArg("height", 6));
        BlockPos centre = region.pos();
        int changed = 0;
        if (on) {
            // Put back everything inside this region that we took out. Walked as
            // a copy: restoring writes to the same map it is reading.
            for (java.util.Map.Entry<BlockPos, BlockState> e
                    : new ArrayList<>(restore.entrySet())) {
                BlockPos at = e.getKey();
                double dx = at.getX() - centre.getX();
                double dz = at.getZ() - centre.getZ();
                if (dx * dx + dz * dz > (double) radius * radius
                        || Math.abs(at.getY() - centre.getY()) > height) {
                    continue;
                }
                if (e.getValue().getLightEmission() <= 0) {
                    continue; // not ours; some other action changed this block
                }
                level.setBlock(at, e.getValue(), 3);
                restore.remove(at);
                changed++;
            }
            return changed;
        }
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++) {
            for (int y = centre.getY() - height; y <= centre.getY() + height; y++) {
                for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++) {
                    BlockPos at = new BlockPos(x, y, z);
                    double dx = x - centre.getX();
                    double dz = z - centre.getZ();
                    if (dx * dx + dz * dz > (double) radius * radius || !contains(at)) {
                        continue;
                    }
                    if (level.getBlockState(at).getLightEmission() <= 0) {
                        continue;
                    }
                    setTracked(at, air);
                    changed++;
                }
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // Rule overrides
    // ------------------------------------------------------------------

    /**
     * Numbers a running script has changed its mind about.
     *
     * <p>Everything in a {@link Ruleset} is final, and deliberately: a ruleset is
     * a file, read once, and two runs of the same map should be the same game.
     * But that also meant a map's difficulty was decided before anybody arrived
     * and could never answer to what happened - "from round thirty the horde is
     * twice the size", "killing the warden halves every price for the rest of
     * the night", "on the last life the breather doubles" were all unsayable.
     *
     * <p>So the file stays immutable and the <em>run</em> carries a layer over
     * it. Overrides live and die with the run, which is the property that makes
     * this safe: nothing a script does can leak into the next game or back into
     * the file on disk.
     *
     * <p>Clamped on the way in, to the same bounds the loader uses. A script is
     * data from a stranger exactly as a ruleset is, and it does not get to ask
     * for a thousand simultaneous zombies by a route the file could not.
     */
    private final java.util.Map<String, Double> ruleOverrides = new java.util.HashMap<>();

    /** The bounds an override is held to, matching the loader's own. */
    private static double clampRule(String path, double v) {
        return switch (path) {
            case "rounds.base_count", "rounds.per_round" -> Math.max(0, Math.min(400, v));
            case "rounds.concurrent_cap" -> Math.max(1, Math.min(400, v));
            case "rounds.breather_start", "rounds.breather_min" -> Math.max(0, Math.min(6000, v));
            case "economy.powerup_chance" -> Math.max(0, Math.min(100, v));
            case "downed.bleedout_seconds", "downed.revive_seconds" -> Math.max(1, Math.min(600, v));
            case "rounds.health_per_round", "rounds.damage_per_round" -> Math.max(0.0, Math.min(4.0, v));
            default -> v;
        };
    }

    public void setRule(String path, double value) {
        if (ruleOverrides.size() < 64 || ruleOverrides.containsKey(path)) {
            ruleOverrides.put(path, clampRule(path, value));
        }
    }

    public void clearRule(String path) {
        ruleOverrides.remove(path);
    }

    /**
     * Health bands each boss has already dropped through.
     *
     * <p>A boss was a mob with a lot of health and a bar over it, and the only
     * thing that could happen to it was dying. Every boss in this mod is written
     * in Java for that reason - there was no way to say "at half health it calls
     * for help and the lights go out" in data.
     *
     * <p>So rather than a phase system with its own vocabulary of attacks,
     * immunities and enrages, a boss crossing a threshold <em>fires an event</em>.
     * Everything a phase might want to do - spawn adds, cut the power, rewrite a
     * rule, retitle the bar, hasten itself - is a verb the script already has, so
     * a phase is an ordinary rule and needed no new grammar at all.
     */
    private final java.util.Map<java.util.UUID, Integer> bossPhase = new java.util.HashMap<>();

    /**
     * Watches every boss's health and fires {@code boss_phase} as it falls.
     *
     * <p>The subject is the entity id and the amount is the band just entered, so
     * {@code {"when": {"amount": {"at_most": 50}}}} reads as "at half or worse".
     * The first sighting is not a phase change - otherwise every boss would fire
     * one the instant it spawned, at full health, which is the kind of off-by-one
     * that makes an author distrust the whole feature.
     */
    private void tickBossPhases() {
        if (alive.isEmpty() || level.getGameTime() % 10L != 0L) {
            return;
        }
        for (Mob mob : alive) {
            if (!mob.isAlive() || !mob.getPersistentData().getBoolean("aztecabyss_boss")) {
                continue;
            }
            int pct = (int) Math.floor(mob.getHealth() * 100.0F / Math.max(1.0F, mob.getMaxHealth()));
            int band = pct >= 75 ? 100 : pct >= 50 ? 75 : pct >= 25 ? 50 : 25;
            Integer was = bossPhase.get(mob.getUUID());
            if (was != null && band >= was) {
                continue;
            }
            bossPhase.put(mob.getUUID(), band);
            if (was == null) {
                continue;
            }
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(mob.getType()).toString();
            Script.fireAmount(this, level, rules.id, "boss_phase", null, id, band);
        }
    }

    /** How many numbers this run has had rewritten, for {@code /arena status}. */
    public int ruleOverrideCount() {
        return ruleOverrides.size();
    }

    /**
     * What a path is worth right now, override or file.
     *
     * <p>Needed because {@code set_rule} with {@code by} has to add to the
     * number in play, and the number in play is usually the file's - reading the
     * override map alone would treat "add two" on an untouched rule as "set to
     * two", which is a quiet, wrong answer of exactly the kind this engine keeps
     * producing.
     */
    public double ruleNow(String path) {
        Double d = ruleOverrides.get(path);
        if (d != null) {
            return d;
        }
        return switch (path) {
            case "rounds.base_count" -> rules.baseCount;
            case "rounds.per_round" -> rules.perRound;
            case "rounds.concurrent_cap" -> rules.concurrentCap;
            case "rounds.breather_start" -> rules.breatherStart;
            case "rounds.breather_min" -> rules.breatherMin;
            case "economy.powerup_chance" -> rules.powerupChance;
            case "downed.bleedout_seconds" -> rules.bleedoutSeconds;
            case "downed.revive_seconds" -> rules.reviveSeconds;
            case "rounds.health_per_round" -> rules.healthPerRound;
            case "rounds.damage_per_round" -> rules.damagePerRound;
            default -> 0.0;
        };
    }

    public double rule(String path, double fallback) {
        Double d = ruleOverrides.get(path);
        return d == null ? fallback : d;
    }

    public int ruleInt(String path, int fallback) {
        return (int) Math.round(rule(path, fallback));
    }

    /**
     * The wave size for a round, override-aware.
     *
     * <p>Shadows {@link Ruleset#countFor} rather than replacing it: the ruleset
     * still answers for anyone asking what the <em>file</em> says, and the arena
     * answers for what this run is actually doing.
     */
    public int countFor(int round) {
        int base = ruleInt("rounds.base_count", rules.baseCount);
        int per = ruleInt("rounds.per_round", rules.perRound);
        int cap = ruleInt("rounds.concurrent_cap", rules.concurrentCap);
        return Math.min(cap * 4, base + per * Math.max(0, round - 1));
    }

    /** The gap between rounds, override-aware. */
    public int breatherFor(int round) {
        int start = ruleInt("rounds.breather_start", rules.breatherStart);
        int min = ruleInt("rounds.breather_min", rules.breatherMin);
        if (start <= min) {
            return min;
        }
        double t = Math.min(1.0, Math.max(0, round - 1)
                / (double) Math.max(1, rules.breatherTightenBy));
        return (int) Math.round(start - (start - min) * t);
    }

    public int concurrentCap() {
        return ruleInt("rounds.concurrent_cap", rules.concurrentCap);
    }

    public int powerupChance() {
        return ruleInt("economy.powerup_chance", rules.powerupChance);
    }

    public int bleedoutSeconds() {
        return ruleInt("downed.bleedout_seconds", rules.bleedoutSeconds);
    }

    public int reviveSeconds() {
        return ruleInt("downed.revive_seconds", rules.reviveSeconds);
    }

    // ------------------------------------------------------------------
    // Zone rules
    // ------------------------------------------------------------------

    /**
     * The properties a named region carries, if any.
     *
     * <p>A region used to be a shape that fired two events and nothing else, so
     * everything true "in the vault" had to be written as a rule for going in
     * and a matching rule for coming out - two halves kept in step by hand, and
     * wrong the first time anybody died inside and respawned elsewhere. A zone
     * holds its rules itself and they apply to whoever is standing in it, which
     * is what a place is.
     */
    private final java.util.Map<String, com.google.gson.JsonObject> zoneRules =
            new java.util.HashMap<>();

    public void setZoneRules(String id, com.google.gson.JsonObject rules) {
        if (zoneRules.size() < 64 || zoneRules.containsKey(id)) {
            zoneRules.put(id, rules);
        }
    }

    public void clearZoneRules(String id) {
        zoneRules.remove(id);
    }

    /** The rules of whichever zone a position sits in, or null. */
    public com.google.gson.JsonObject zoneRulesAt(BlockPos at) {
        String id = regionAt(at);
        return id == null ? null : zoneRules.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    /** Whether a zone forbids something, by flag name. */
    public boolean zoneForbids(BlockPos at, String flag) {
        com.google.gson.JsonObject r = zoneRulesAt(at);
        return r != null && r.has(flag) && r.get(flag).getAsBoolean();
    }

    /**
     * Applies whatever the zones say, once a second.
     *
     * <p>Effects are reapplied rather than tracked: a three-second effect topped
     * up every second is self-cleaning, because walking out simply stops the
     * top-up and it lapses on its own. Tracking who is in what and removing on
     * exit is the version of this that leaves someone permanently slowed after
     * a disconnect.
     */
    private void tickZoneRules(List<ServerPlayer> present) {
        if (zoneRules.isEmpty()) {
            return;
        }
        for (ServerPlayer p : present) {
            com.google.gson.JsonObject r = zoneRulesAt(p.blockPosition());
            if (r == null) {
                continue;
            }
            if (r.has("effect")) {
                var rl = net.minecraft.resources.ResourceLocation.tryParse(
                        r.get("effect").getAsString().toLowerCase(java.util.Locale.ROOT));
                if (rl != null) {
                    var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                            .getHolder(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.MOB_EFFECT, rl));
                    if (holder.isPresent()) {
                        int amp = r.has("amp") ? Math.max(0, Math.min(9, r.get("amp").getAsInt())) : 0;
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                holder.get(), 60, amp, false, false));
                    }
                }
            }
            if (r.has("damage")) {
                float dmg = r.get("damage").getAsFloat();
                if (dmg > 0.0F) {
                    p.hurt(level.damageSources().magic(), dmg);
                }
            }
            if (r.has("heal")) {
                p.heal(r.get("heal").getAsFloat());
            }
            if (r.has("no_fall") && r.get("no_fall").getAsBoolean()) {
                p.fallDistance = 0.0F;
            }
        }
    }

    /** Whether somebody is in this run, for the handlers that fire per player. */
    public boolean isParticipant(ServerPlayer player) {
        return participants.contains(player.getUUID());
    }

    public boolean contains(BlockPos at) {
        return bounds.isInside(at);
    }

    /**
     * Work a rule asked for later.
     *
     * <p>The script layer could say "when this happens, do that" and had no way to
     * say "in thirty seconds, do that". Every countdown, delayed gate, staged
     * reveal and timed penalty had to be faked by polling {@code tick} against a
     * variable the map incremented itself - which is the shape of a missing
     * feature rather than a technique, and it put a counter in every map that
     * merely wanted a pause.
     */
    private record Scheduled(long fireAt, com.google.gson.JsonArray actions,
                             java.util.UUID who, int repeatTicks, int remaining) {
    }

    private final List<Scheduled> scheduled = new ArrayList<>();

    /**
     * Queues actions to run later.
     *
     * @param repeatTicks 0 for one-shot, otherwise the gap between repeats
     * @param times       how many times a repeater fires; 0 means until the run ends
     */
    public void schedule(com.google.gson.JsonArray actions, ServerPlayer who,
                         int delayTicks, int repeatTicks, int times) {
        // A cap, because a map from a stranger should not be able to queue a
        // million pieces of work.
        if (scheduled.size() >= 256) {
            return;
        }
        scheduled.add(new Scheduled(level.getGameTime() + Math.max(1, delayTicks), actions,
                who == null ? null : who.getUUID(),
                Math.max(0, repeatTicks), times <= 0 ? Integer.MAX_VALUE : times));
    }

    /**
     * Fires anything due.
     *
     * <p>Collected and run after the sweep rather than during it, for the same
     * reason as every other list in this class: an action may schedule more work,
     * and mutating the list being walked is how three separate bugs in this
     * project started.
     */
    private void tickScheduled() {
        if (scheduled.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Scheduled> due = new ArrayList<>();
        for (Scheduled s : scheduled) {
            if (s.fireAt() <= now) {
                due.add(s);
            }
        }
        if (due.isEmpty()) {
            return;
        }
        scheduled.removeAll(due);
        for (Scheduled s : due) {
            ServerPlayer who = s.who() == null ? null
                    : level.getServer().getPlayerList().getPlayer(s.who());
            Script.runActions(this, level, s.actions(), who);
            if (s.repeatTicks() > 0 && s.remaining() > 1) {
                scheduled.add(new Scheduled(now + s.repeatTicks(), s.actions(), s.who(),
                        s.repeatTicks(), s.remaining() - 1));
            }
        }
    }

    /** How many pieces of delayed work are queued, for {@code /arena status}. */
    public int scheduledCount() {
        return scheduled.size();
    }

    // ------------------------------------------------------------------
    // Named timers
    // ------------------------------------------------------------------

    /**
     * A clock with a name, counting down.
     *
     * <p>Every countdown a map wanted was previously assembled out of
     * {@code every} plus a variable plus a rule watching for zero - four moving
     * parts for one idea, rebuilt slightly differently in every map and wrong in
     * a different way in each. One of these counts itself down, can be read by a
     * condition while it runs, prints itself through {@code {timer:id}}, and
     * fires its own actions when it reaches nothing.
     */
    private record Timer(int ticksLeft, String bar, com.google.gson.JsonArray onEnd, UUID who) {
    }

    private final java.util.Map<String, Timer> timers = new java.util.LinkedHashMap<>();

    /** Starts or replaces a timer. Restarting one is how a map extends a siege. */
    public void startTimer(String id, int seconds, String bar,
                           com.google.gson.JsonArray onEnd, ServerPlayer who) {
        if (timers.size() >= 32 && !timers.containsKey(id)) {
            return; // a cap, for the same reason the scheduler has one
        }
        timers.put(id, new Timer(Math.max(1, seconds) * 20, bar, onEnd,
                who == null ? null : who.getUUID()));
    }

    public void stopTimer(String id) {
        timers.remove(id);
    }

    /** Adds (or, negative, takes off) seconds without disturbing the rest. */
    public void addTimer(String id, int seconds) {
        Timer t = timers.get(id);
        if (t == null) {
            return;
        }
        int left = Math.max(1, t.ticksLeft() + seconds * 20);
        timers.put(id, new Timer(left, t.bar(), t.onEnd(), t.who()));
    }

    /** Seconds left, rounded up. Zero when no such timer is running. */
    public int timerSeconds(String id) {
        Timer t = timers.get(id);
        return t == null ? 0 : (t.ticksLeft() + 19) / 20;
    }

    /** The label of the first timer asking to be shown, or null. */
    public String timerBar() {
        for (Timer t : timers.values()) {
            if (t.bar() != null && !t.bar().isEmpty()) {
                int left = (t.ticksLeft() + 19) / 20;
                return t.bar() + " §f" + (left / 60) + ":"
                        + (left % 60 < 10 ? "0" : "") + (left % 60);
            }
        }
        return null;
    }

    /**
     * Counts every timer down and fires the ones that land.
     *
     * <p>Collected then run, like everything else here: the actions at zero may
     * start another timer, and mutating the map being walked is how three
     * separate bugs in this class started.
     */
    private void tickTimers() {
        if (timers.isEmpty()) {
            return;
        }
        List<String> done = new ArrayList<>();
        for (java.util.Map.Entry<String, Timer> e : timers.entrySet()) {
            Timer t = e.getValue();
            int left = t.ticksLeft() - 1;
            if (left <= 0) {
                done.add(e.getKey());
            } else {
                e.setValue(new Timer(left, t.bar(), t.onEnd(), t.who()));
            }
        }
        for (String id : done) {
            Timer t = timers.remove(id);
            if (t == null) {
                continue;
            }
            ServerPlayer who = t.who() == null || level.getServer() == null ? null
                    : level.getServer().getPlayerList().getPlayer(t.who());
            Script.fire(this, level, rules.id, "timer_end", who, null, id);
            if (t.onEnd() != null) {
                Script.runActions(this, level, t.onEnd(), who);
            }
        }
    }

    /** Whether the border has already been told to close. */
    private boolean borderStarted;

    /**
     * Closes the world border in, once.
     *
     * <p>What stops a last-one-standing mode being two people hiding in opposite
     * corners until somebody restarts the server. Driven through vanilla's own
     * border rather than a custom ring of blocks, so players get the red warning
     * wall, the sound, and damage outside it for free - and so the shrink is
     * smooth rather than a series of jumps.
     *
     * <p>Set once and left alone. Vanilla interpolates it over the duration by
     * itself; re-issuing it every tick would restart the interpolation and the
     * border would never actually arrive.
     */
    private void tickBorder() {
        if (borderStarted || rules.borderTo <= 0) {
            return;
        }
        if (elapsed < rules.borderWaitSeconds * 20) {
            return;
        }
        borderStarted = true;
        var border = level.getWorldBorder();
        double from = rules.borderFrom > 0 ? rules.borderFrom : border.getSize();
        border.setCenter(spawn.getX() + 0.5, spawn.getZ() + 0.5);
        border.setSize(from);
        border.lerpSizeBetween(from, rules.borderTo, rules.borderSeconds * 1000L);
        for (ServerPlayer p : players()) {
            p.displayClientMessage(Component.literal(
                    "§c§lTHE BORDER IS CLOSING"), false);
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR,
                    SoundSource.AMBIENT, 1.0F, 0.5F);
        }
    }

    /**
     * Ends a no-respawn run when one player is left.
     *
     * <p>Only for maps that both scatter their spawns and never hand lives back -
     * which is exactly the shape of a battle royale and nothing else. A co-op
     * arena has its own ending, and applying this to one would end a two-player
     * run the moment somebody died.
     */
    private void tickLastStanding(List<ServerPlayer> present) {
        if (!rules.scatterSpawns || rules.respawnEnabled
                || participants.size() < 2 || present.size() != 1) {
            return;
        }
        ServerPlayer winner = present.get(0);
        for (ServerPlayer p : players()) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal("§6§l" + winner.getGameProfile().getName().toUpperCase(
                            java.util.Locale.ROOT) + " WINS")));
        }
        Script.fire(this, level, rules.id, "run_won", winner);
        stop(false);
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
        downed.put(player.getUUID(), bleedoutSeconds() * 20);
        reviving.put(player.getUUID(), 0);
        player.setHealth(1.0F);
        // Immobilised and unmistakable: a downed teammate has to be findable
        // across a dark room or nobody can choose to go for them.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                bleedoutSeconds() * 20, 5, false, false));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WEAKNESS,
                bleedoutSeconds() * 20, 9, false, false));
        player.setGlowingTag(true);
        for (ServerPlayer p : players()) {
            p.displayClientMessage(Component.literal(
                    "§c" + player.getGameProfile().getName() + " is down."), false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ANGRY,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        // The whole downed-and-revive system was invisible to scripts, so a map
        // could not react to the moment that decides most co-op runs.
        Script.fire(this, level, rules.id, "player_down", player);
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
                int need = reviveSeconds() * 20;
                if (progress >= need) {
                    lift(victim, helper);
                    lost.add(e.getKey());
                    // Fired for the one who was picked up; the helper is the
                    // subject, so a rule can pay whoever did the picking.
                    Script.fire(this, level, rules.id, "player_revived", victim, null,
                            helper.getGameProfile().getName());
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
        // Bleeding out is dying. It reaches this method rather than the one in
        // tick(), so without its own fire the co-op modes would be the only ones
        // where player_died never happened.
        Script.fire(this, level, rules.id, "player_died", victim, null, "final");
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
            // A gate may hold itself shut until a given round, which is how a map
            // gets somewhere to go rather than everything being open at once.
            if (round < h.intArg("from_round", 0)) {
                continue;
            }
            if (h.intArg("until_round", Integer.MAX_VALUE) < round) {
                continue;
            }
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
                Mob made = spawnAt(id, s.pos(), s.intArg("health", 0), s.intArg("damage", 0));
                if (made == null) {
                    continue;
                }
                // A hand-placed enemy is the one an author is most likely to want
                // walking a beat, so behaviour and route come off the marker that
                // put it there. Without the route a patroller has nowhere to go
                // and stands still, which reads as a broken mob rather than a
                // missing argument.
                MobBrains.mark(made, s.arg("behaviour", s.arg("behavior", "")));
                String route = s.arg("route", "");
                if (!route.isEmpty()) {
                    made.getPersistentData().putString("aztecabyss_route",
                            route.toLowerCase(java.util.Locale.ROOT));
                    MobBrains.mark(made, "patrol");
                }
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
    private Mob spawnAt(String entityId, BlockPos at, int health, int damage) {
        var type = EntityType.byString(entityId);
        if (type.isEmpty()) {
            return null;
        }
        Entity entity = type.get().create(level);
        if (!(entity instanceof Mob mob)) {
            return null;
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
        return mob;
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

    /**
     * Picks a gate, respecting per-gate weight.
     *
     * <p>Every gate was equally likely, which made a map's four ways in
     * interchangeable no matter how different the rooms behind them were. A
     * weight lets an author say "most of it comes through the front" without
     * needing four rulesets or any code.
     */
    /**
     * Which way in the next mob uses.
     *
     * <p>Weighted, and now steered. The Director has always measured how a run
     * is going and had only one lever - how <em>fast</em> the horde arrives -
     * because every gate was interchangeable to it. A gate that says what kind
     * of way in it is gives the Director somewhere to send them:
     *
     * <pre>
     *   [Horde]
     *   hint=flank
     * </pre>
     *
     * <ul>
     *   <li>{@code pressure} — the obvious way in. Favoured while the squad is
     *       comfortable, because that is the fight they are set up to win and
     *       the one that should feel like the map working as intended.</li>
     *   <li>{@code flank} — behind the line. Favoured as intensity climbs, which
     *       is what turns "more of them" into "you are holding the wrong
     *       corner".</li>
     *   <li>{@code ambush} — the nasty one. Held back until the squad is well on
     *       top, so it reads as the map answering them rather than as noise.</li>
     * </ul>
     *
     * <p>Hints bias weights rather than replacing them. A map that names none
     * behaves exactly as it did, which is the only acceptable way to add a
     * steering wheel to something that already drove.
     */
    private Marker pickGate(List<Marker> live) {
        float heat = director == null ? 0.5f : director.intensity();
        int total = 0;
        for (Marker m : live) {
            total += gateWeight(m, heat);
        }
        int roll = rng.nextInt(Math.max(1, total));
        for (Marker m : live) {
            roll -= gateWeight(m, heat);
            if (roll < 0) {
                return m;
            }
        }
        return live.get(0);
    }

    /** A gate's weight, bent by what kind of way in it says it is. */
    private int gateWeight(Marker gate, float heat) {
        int base = Math.max(1, gate.intArg("weight", 1));
        String hint = gate.arg("hint", "").toLowerCase(java.util.Locale.ROOT);
        if (hint.isEmpty()) {
            return base;
        }
        // heat is 0 (untroubled) to 1 (about to die).
        double scale = switch (hint) {
            case "pressure" -> 1.6 - heat;                 // 1.6 calm, 0.6 desperate
            case "flank" -> 0.6 + heat;                    // 0.6 calm, 1.6 desperate
            case "ambush" -> heat < 0.35 ? 1.5 : 0.15;     // only while they are winning
            default -> 1.0;
        };
        return Math.max(1, (int) Math.round(base * scale));
    }

    private void spawnOne() {
        List<Marker> live = liveHordes();
        Marker gate = pickGate(live);
        // A gate may name its own mobs, which is what turns four identical ways
        // in into a map where the cellar sends something different from the roof.
        Ruleset.MobEntry pick = nextFromWave();
        if (pick == null) {
            pick = pickMobFor(gate);
        }
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
        // burst= sends several at once out of this gate. A pack arriving together
        // is a different problem from the same number trickling in, and trickle
        // was the only thing the engine could do.
        int burst = Math.max(1, Math.min(8, gate.intArg("burst", 1)));
        BlockPos at = spawnPointFor(gate);
        if (at == null) {
            // Nowhere to put it. Better to skip one than to bury it in stone,
            // which would leave the round waiting on something that cannot move.
            leftToSpawn--;
            return;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);
        mob.getPersistentData().putString("aztecabyss_gate",
                gate.arg("id", gate.arg("area", "")));

        // Per-gate multipliers, on top of the round curve rather than instead of
        // it. A gate saying health=200 means "twice as tough as whatever this
        // round is", which stays meaningful at round 5 and at round 40 - an
        // absolute number would stop meaning anything by round 10.
        double gateHealth = Math.max(1, gate.intArg("health", 100)) / 100.0;
        double gateDamage = Math.max(1, gate.intArg("damage", 100)) / 100.0;
        double healthMul = rules.healthMultiplier(round) * gateHealth;
        double damageMul = rules.damageMultiplier(round) * gateDamage;
        setAttr(mob, Attributes.MAX_HEALTH, pick.maxHealth() * healthMul);
        setAttr(mob, Attributes.MOVEMENT_SPEED, pick.speed());
        setAttr(mob, Attributes.ATTACK_DAMAGE, pick.attackDamage() * damageMul);
        mob.setHealth(mob.getMaxHealth());
        applyRole(mob, pick.role());
        MobBrains.mark(mob, pick.behaviour());
        equip(mob, pick);
        mob.getPersistentData().putBoolean("aztecabyss_engine_mob", true);
        mob.setPersistenceRequired();

        level.addFreshEntity(mob);
        // The rest of the burst, if this gate asked for one.
        for (int extra = 1; extra < burst && leftToSpawn > 1; extra++) {
            spawnCompanion(gate, pick, at, healthMul, damageMul);
        }
        ServerPlayer target = nearestPlayer(at);
        if (target != null) {
            mob.setTarget(target);
        }
        alive.add(mob);
        leftToSpawn--;
    }

    /**
     * The next mob off an exact roster, or null when there is not one.
     *
     * <p>Its numbers come from the ruleset's own entry for that entity where one
     * exists, so a wave table says <em>what</em> comes and the mob table still
     * says what it is worth - writing a boss round should not mean restating
     * every husk's health.
     */
    private Ruleset.MobEntry nextFromWave() {
        if (waveQueue.isEmpty()) {
            return null;
        }
        Ruleset.WaveEntry w = waveQueue.remove(waveQueue.size() - 1);
        for (Ruleset.MobEntry m : rules.mobs) {
            if (m.entityId().equalsIgnoreCase(w.entityId())) {
                // The wave may still override the behaviour for this round only.
                return w.behaviour().isEmpty() ? m
                        : new Ruleset.MobEntry(m.entityId(), m.weight(), m.fromRound(), m.role(),
                                m.maxHealth(), m.speed(), m.attackDamage(),
                                m.mainHand(), m.head(), w.behaviour());
            }
        }
        return new Ruleset.MobEntry(w.entityId(), 1, 1, "grunt",
                20.0, 0.25, 3.0, "", "", w.behaviour());
    }

    /** Weighted choice among everything unlocked at this round. */
    private Ruleset.MobEntry pickMob() {
        if (rules.mobs.isEmpty()) {
            // A ruleset with no mob table still has to produce a game.
            return new Ruleset.MobEntry("minecraft:zombie", 1, 1, "grunt",
                    20.0, 0.25, 3.0, "", "", "");
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

    /**
     * A gate's own mob table, if it named one.
     *
     * <p>{@code mobs=husk,drowned} on a horde marker restricts what comes out of
     * it to those entities, drawn from the ruleset's table so their scaling,
     * roles and equipment still apply. A gate naming something the table does not
     * contain falls back to the table rather than sending nothing, because a
     * silent gate is indistinguishable from a broken one.
     */
    private Ruleset.MobEntry pickMobFor(Marker gate) {
        String only = gate.arg("mobs", "");
        if (only.isBlank()) {
            return pickMob();
        }
        java.util.List<Ruleset.MobEntry> allowed = new ArrayList<>();
        for (String raw : only.split(",")) {
            String want = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (want.isEmpty()) {
                continue;
            }
            for (Ruleset.MobEntry m : rules.mobs) {
                String id = m.entityId().toLowerCase(java.util.Locale.ROOT);
                if ((id.equals(want) || id.equals("minecraft:" + want)) && eligible(m)) {
                    allowed.add(m);
                }
            }
        }
        if (allowed.isEmpty()) {
            return pickMob();
        }
        return allowed.get(rng.nextInt(allowed.size()));
    }

    /** One more of the same, beside the first. Used only by {@code burst=}. */
    private void spawnCompanion(Marker gate, Ruleset.MobEntry pick, BlockPos near,
                                double healthMul, double damageMul) {
        var maybeType = EntityType.byString(pick.entityId());
        if (maybeType.isEmpty() || !(maybeType.get().create(level) instanceof Mob mob)) {
            return;
        }
        BlockPos at = spawnPointFor(gate);
        if (at == null) {
            at = near;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);
        setAttr(mob, Attributes.MAX_HEALTH, pick.maxHealth() * healthMul);
        setAttr(mob, Attributes.MOVEMENT_SPEED, pick.speed());
        setAttr(mob, Attributes.ATTACK_DAMAGE, pick.attackDamage() * damageMul);
        mob.setHealth(mob.getMaxHealth());
        applyRole(mob, pick.role());
        MobBrains.mark(mob, pick.behaviour());
        equip(mob, pick);
        mob.getPersistentData().putBoolean("aztecabyss_engine_mob", true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        alive.add(mob);
        leftToSpawn--;
        ServerPlayer t = nearestPlayer(at);
        if (t != null) {
            mob.setTarget(t);
        }
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
        EnginePowerUps.maybeDrop(a.level, mob, a.powerupChance(), a.rng);
        // A kill is progress, which resets the stall clock. Without this a long
        // hard round with a slow weapon would be mistaken for a stuck one.
        a.lastProgress = a.level.getGameTime();
        Script.fire(a, a.level, a.rules.id, "mob_killed", killer, null,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(mob.getType()).toString());
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
