package com.jrpetty.aztecabyss.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Triggers and actions: the layer that turns tuning into authoring.
 *
 * <p>Everything before this let a map say how <em>hard</em> it was. This lets a map
 * say what <em>happens</em>. The distinction matters because it is the difference
 * between twenty maps that differ by a health multiplier and twenty maps that are
 * actually different games.
 *
 * <p>The model is borrowed from Hammer's entity I/O rather than from a scripting
 * language, and that is a deliberate limitation. There is no loop, no variable and
 * no arithmetic - a rule is "when this happens, if these things are true, do these
 * things", and nothing else. A map author cannot write an infinite loop into a
 * server, cannot leak memory, and cannot produce a stack trace nobody can read. The
 * ceiling is lower than a real language and the floor is enormously higher.
 *
 * <pre>
 * "script": [
 *   { "on": "round_start",
 *     "when": { "round": { "every": 10 } },
 *     "do": [ { "title": { "main": "§4THEY SENT SOMETHING" } },
 *             { "spawn": { "id": "minecraft:warden", "at": "boss", "health": 600 } } ] }
 * ]
 * </pre>
 */
public final class Script {

    /** Actions a single rule may run, so a typo cannot flood a tick. */
    private static final int ACTION_BUDGET = 32;

    private Script() {
    }

    public record Rule(String event, JsonObject when, JsonArray actions) {
    }

    /** Rules per ruleset id, rebuilt whenever datapacks reload. */
    private static final Map<String, List<Rule>> BY_RULESET = new HashMap<>();

    /**
     * Problems found while reading a script, per ruleset.
     *
     * <p>The switch that runs actions ends in a default that does nothing, and the
     * condition reader ignores keys it has no meaning for. Both are the right
     * runtime behaviour - a map written for a later engine must still run on an
     * earlier one - and both mean a typo produces a rule that loads perfectly and
     * never does anything, with no symptom to chase.
     *
     * <p>So unknown names are collected here and reported by {@code /arena rules}.
     * Not errors, because forward compatibility is worth more; never silent,
     * because a rule that quietly does nothing is the worst thing a script can do.
     */
    private static final Map<String, List<String>> WARNINGS = new HashMap<>();

    /** Every event the engine actually fires. */
    private static final java.util.Set<String> EVENTS = java.util.Set.of(
            "run_start", "round_start", "round_end", "mob_killed", "extracted",
            "objective_complete", "objective_failed", "region_enter", "region_leave", "tick",
            "use_block", "break_block", "run_won", "phase_start",
            "player_died", "player_joined");

    /** Every action the runner understands. */
    private static final java.util.Set<String> ACTIONS = java.util.Set.of(
            "message", "actionbar", "title", "sound", "effect", "give", "spawn", "award",
            "open_area", "set_block", "end_run", "set_var", "add_var", "set_my_var",
            "add_my_var", "win", "lose", "set_bar", "join_team", "balance_teams",
            "team_message", "add_team_var", "set_team_var", "teleport_to_spawn",
            "delay", "every", "take", "set_phase");

    /** Every condition the matcher understands. */
    private static final java.util.Set<String> CONDITIONS = java.util.Set.of(
            "round", "area_open", "region", "var", "my_var", "team", "team_var", "seconds", "block",
            "has_item", "killed", "chance", "phase", "subject", "players");

    public static List<String> warnings(String rulesetId) {
        return WARNINGS.getOrDefault(rulesetId, List.of());
    }

    public static void clear() {
        BY_RULESET.clear();
        WARNINGS.clear();
    }

    /** Pulls the {@code script} array out of a ruleset file. */
    public static void load(String rulesetId, JsonObject root) {
        if (!root.has("script") || !root.get("script").isJsonArray()) {
            return;
        }
        List<Rule> rules = new ArrayList<>();
        List<String> warn = new ArrayList<>();
        int index = 0;
        for (JsonElement el : root.getAsJsonArray("script")) {
            index++;
            if (!el.isJsonObject()) {
                warn.add("rule " + index + " is not an object");
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String on = str(o, "on", "").toLowerCase(Locale.ROOT);
            if (on.isEmpty() || !o.has("do") || !o.get("do").isJsonArray()) {
                warn.add("rule " + index + " has no \"on\" or no \"do\" — it will never run");
                continue;
            }
            if (!EVENTS.contains(on)) {
                warn.add("rule " + index + ": no event called \"" + on + "\" — it will never fire");
            }
            if (o.has("when") && o.get("when").isJsonObject()) {
                for (String c : o.getAsJsonObject("when").keySet()) {
                    if (!CONDITIONS.contains(c.toLowerCase(Locale.ROOT))) {
                        warn.add("rule " + index + ": unknown condition \"" + c + "\" — ignored");
                    }
                }
            }
            for (JsonElement act : o.getAsJsonArray("do")) {
                if (!act.isJsonObject()) {
                    continue;
                }
                for (String a : act.getAsJsonObject().keySet()) {
                    if (!ACTIONS.contains(a.toLowerCase(Locale.ROOT))) {
                        warn.add("rule " + index + ": unknown action \"" + a + "\" — does nothing");
                    }
                }
            }
            rules.add(new Rule(on,
                    o.has("when") && o.get("when").isJsonObject() ? o.getAsJsonObject("when") : null,
                    o.getAsJsonArray("do")));
        }
        if (!rules.isEmpty()) {
            BY_RULESET.put(rulesetId, rules);
        }
        if (!warn.isEmpty()) {
            WARNINGS.put(rulesetId, List.copyOf(warn));
        }
    }

    public static int ruleCount(String rulesetId) {
        return BY_RULESET.getOrDefault(rulesetId, List.of()).size();
    }

    // ------------------------------------------------------------------
    // Firing
    // ------------------------------------------------------------------

    /**
     * Runs every rule listening for an event.
     *
     * <p>Never throws. A map that misspells an action name loses that action and
     * keeps its run; the alternative is a scripting mistake in someone else's
     * downloaded map taking down a server mid-round.
     */
    /**
     * A block a player touched, and where.
     *
     * <p>Rounds answer <em>when</em>, regions answer <em>where somebody is
     * standing</em>, and neither answers <em>what did they just pull</em>. A lever,
     * a button, a pressure plate and a block being mined are the foundation of
     * every puzzle, switch and machine, and none of them could be reacted to at
     * all - so a map could ask you to reach a place but never to operate anything.
     *
     * <p>The region is resolved from the <em>block's</em> position rather than the
     * player's, which is the difference between "the lever in the vault" and "a
     * lever, pulled by somebody who happens to be standing in the vault".
     */
    public static void fireBlock(EngineArena arena, ServerLevel level, String rulesetId,
                                 String event, ServerPlayer who, BlockPos at, String blockId) {
        List<Rule> rules = BY_RULESET.get(rulesetId);
        if (rules == null || rules.isEmpty()) {
            return;
        }
        String region = arena.regionAt(at);
        for (Rule rule : rules) {
            if (!rule.event().equals(event)) {
                continue;
            }
            if (rule.when() != null && rule.when().has("block")) {
                String want = str(rule.when(), "block", "").toLowerCase(Locale.ROOT);
                if (!want.isEmpty() && !blockId.equalsIgnoreCase(want)
                        && !blockId.equalsIgnoreCase("minecraft:" + want)) {
                    continue;
                }
            }
            if (!matches(rule.when(), arena, who, region)) {
                trace(arena, "§8skip §7" + event + " §8(" + blockId + ") — conditions not met");
                continue;
            }
            trace(arena, "§afire §f" + event + " §7(" + blockId + ")"
                    + (region == null ? "" : " §8in " + region));
            int budget = ACTION_BUDGET;
            for (JsonElement el : rule.actions()) {
                if (budget-- <= 0) {
                    break;
                }
                if (el.isJsonObject()) {
                    run(arena, level, el.getAsJsonObject(), who);
                }
            }
        }
    }

    /** A region event, which carries the region's id so rules can filter on it. */
    public static void fireRegion(EngineArena arena, ServerLevel level, String rulesetId,
                                  String event, ServerPlayer who, String regionId) {
        fire(arena, level, rulesetId, event, who, regionId);
    }

    public static void fire(EngineArena arena, ServerLevel level, String rulesetId,
                            String event, ServerPlayer who) {
        fire(arena, level, rulesetId, event, who, null);
    }

    /** Authors watching their own script run. */
    private static final java.util.Set<java.util.UUID> TRACING = new java.util.HashSet<>();

    public static boolean toggleTrace(ServerPlayer player) {
        if (!TRACING.remove(player.getUUID())) {
            TRACING.add(player.getUUID());
            return true;
        }
        return false;
    }

    /**
     * Tells anyone tracing what just happened.
     *
     * <p>A map with forty rules is unbuildable without this. Everything the script
     * layer does is invisible when it works and identical to nothing at all when
     * it does not, so the only debugging tool was moving things about and playing
     * again. This says which rules fired and which were skipped, as it happens.
     */
    private static void trace(EngineArena arena, String text) {
        if (TRACING.isEmpty()) {
            return;
        }
        for (ServerPlayer p : arena.everyone()) {
            if (TRACING.contains(p.getUUID())) {
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§8[script] §7" + text), false);
            }
        }
    }

    public static void fire(EngineArena arena, ServerLevel level, String rulesetId,
                            String event, ServerPlayer who, String regionId) {
        fire(arena, level, rulesetId, event, who, regionId, null);
    }

    /**
     * Fires an event, carrying what it was <em>about</em>.
     *
     * <p>Every event until now told a rule that something happened and refused to
     * say what. {@code mob_killed} fired identically for a zombie and for the
     * boss, so "when the boss dies, open the vault" - the single most obvious
     * sentence in a boss map - could not be written at all. The subject is
     * whatever the event is a fact about: the entity id for a kill, and room for
     * the same treatment on any future event that has a noun in it.
     */
    public static void fire(EngineArena arena, ServerLevel level, String rulesetId,
                            String event, ServerPlayer who, String regionId, String subject) {
        List<Rule> rules = BY_RULESET.get(rulesetId);
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (Rule rule : rules) {
            if (!rule.event().equals(event)) {
                continue;
            }
            if (!matches(rule.when(), arena, who, regionId, subject)) {
                trace(arena, "§8skip §7" + event + (regionId == null ? "" : " (" + regionId + ")")
                        + " §8— conditions not met");
                continue;
            }
            trace(arena, "§afire §f" + event
                    + (regionId == null ? "" : " §7(" + regionId + ")")
                    + (who == null ? "" : " §8for " + who.getGameProfile().getName()));
            int budget = ACTION_BUDGET;
            for (JsonElement el : rule.actions()) {
                if (budget-- <= 0) {
                    break;
                }
                if (el.isJsonObject()) {
                    try {
                        run(arena, level, el.getAsJsonObject(), who);
                    } catch (RuntimeException ignored) {
                        // One bad action, not one bad run.
                    }
                }
            }
        }
    }

    /** Conditions. Absent means always. */
    private static boolean matches(JsonObject when, EngineArena arena,
                                   ServerPlayer who, String regionId) {
        return matches(when, arena, who, regionId, null);
    }

    private static boolean matches(JsonObject when, EngineArena arena,
                                   ServerPlayer who, String regionId, String subject) {
        if (when == null) {
            return true;
        }
        // What died, for the events that killed something. Matched loosely so
        // "zombie" works as well as "minecraft:zombie" - an author writing a
        // ruleset by hand should not have to remember which ids carry a
        // namespace and which do not.
        // The general form of "killed". Every event now carries a subject - what
        // it was a fact about - and this is how a rule reads it. "killed" is kept
        // as the alias because {"killed": "zombie"} reads like English and
        // {"subject": "zombie"} does not; they are the same test.
        if (when.has("subject") || when.has("killed")) {
            String want = str(when, when.has("killed") ? "killed" : "subject", "")
                    .toLowerCase(Locale.ROOT);
            if (subject == null || want.isEmpty()) {
                return false;
            }
            String got = subject.toLowerCase(Locale.ROOT);
            if (!got.equals(want) && !got.equals("minecraft:" + want)
                    && !got.endsWith(":" + want)) {
                return false;
            }
        }
        // How many people are in the run. A lobby that opens a gate at four, a
        // rule that only applies when the squad is down to two, a solo-only
        // secret - none of which could be asked.
        if (when.has("players") && when.get("players").isJsonObject()) {
            JsonObject n = when.getAsJsonObject("players");
            int now = arena.everyone().size();
            if (n.has("equals") && now != intOf(n, "equals", -1)) {
                return false;
            }
            if (n.has("at_least") && now < intOf(n, "at_least", 0)) {
                return false;
            }
            if (n.has("at_most") && now > intOf(n, "at_most", Integer.MAX_VALUE)) {
                return false;
            }
        }
        // A percentage roll. The script layer was entirely deterministic, which
        // meant an author could describe a game but never a game that surprises
        // you twice - no random events, no rare drops, no "one time in ten this
        // door is already open".
        if (when.has("chance")) {
            int percent = intOf(when, "chance", 100);
            if (percent < 100 && arena.rng().nextInt(100) >= percent) {
                return false;
            }
        }
        // What somebody is carrying. This is the one that unlocks keys, fetch
        // quests, deliveries, tolls and trades - the engine could read the run,
        // the round, the clock, the regions, the teams and its own variables,
        // and could not read the player.
        // Which part of the game this is. The condition every phased format
        // needs and the reason phases are a name rather than an enum: a rule
        // that only applies during your own "overtime" is written the same way
        // as one that only applies during the engine's own countdown.
        if (when.has("phase")) {
            if (!arena.phase().equalsIgnoreCase(str(when, "phase", ""))) {
                return false;
            }
        }
        if (when.has("has_item")) {
            if (who == null || !hasItem(who, when.get("has_item"))) {
                return false;
            }
        }
        // Which region fired this. Lets one rule per region rather than one
        // ruleset per region, which is what a map with nine checkpoints needs.
        if (when.has("region")) {
            String want = str(when, "region", "").toLowerCase(Locale.ROOT);
            if (regionId == null || !regionId.equalsIgnoreCase(want)) {
                return false;
            }
        }
        // Variables, run-scoped and per-player. This is what makes a win
        // condition expressible: "when flags is at_least 3, end the run".
        if (when.has("var") && when.get("var").isJsonObject()) {
            if (!varMatches(when.getAsJsonObject("var"), arena, who, false)) {
                return false;
            }
        }
        if (when.has("my_var") && when.get("my_var").isJsonObject()) {
            if (who == null || !varMatches(when.getAsJsonObject("my_var"), arena, who, true)) {
                return false;
            }
        }
        if (when.has("round") && when.get("round").isJsonObject()) {
            JsonObject r = when.getAsJsonObject("round");
            int round = arena.round();
            if (r.has("equals") && round != intOf(r, "equals", -1)) {
                return false;
            }
            if (r.has("at_least") && round < intOf(r, "at_least", 0)) {
                return false;
            }
            if (r.has("at_most") && round > intOf(r, "at_most", Integer.MAX_VALUE)) {
                return false;
            }
            int every = intOf(r, "every", 0);
            if (every > 0 && (round <= 0 || round % every != 0)) {
                return false;
            }
        }
        // Elapsed run time, so a free-mode map can have a deadline without
        // building its own clock out of variables.
        if (when.has("seconds") && when.get("seconds").isJsonObject()) {
            JsonObject t = when.getAsJsonObject("seconds");
            int now = arena.elapsedSeconds();
            if (t.has("at_least") && now < intOf(t, "at_least", 0)) {
                return false;
            }
            if (t.has("at_most") && now > intOf(t, "at_most", Integer.MAX_VALUE)) {
                return false;
            }
        }
        // Which side triggered this. "team": "red" on a region_enter is the whole
        // of "when a blue player reaches the red flag".
        if (when.has("team")) {
            if (who == null || !arena.teams().isOn(who, str(when, "team", ""))) {
                return false;
            }
        }
        if (when.has("team_var") && when.get("team_var").isJsonObject()) {
            JsonObject tv = when.getAsJsonObject("team_var");
            String team = str(tv, "team", "");
            if (team.isEmpty() && who != null) {
                team = arena.teams().teamOf(who);
            }
            if (team == null || team.isEmpty()) {
                return false;
            }
            JsonObject shim = new JsonObject();
            shim.addProperty("name", "team:" + team.toLowerCase(Locale.ROOT)
                    + ":" + str(tv, "name", ""));
            for (String c : new String[]{"equals", "at_least", "at_most"}) {
                if (tv.has(c)) {
                    shim.addProperty(c, intOf(tv, c, 0));
                }
            }
            if (!varMatches(shim, arena, who, false)) {
                return false;
            }
        }
        if (when.has("area_open")) {
            return arena.isAreaOpen(str(when, "area_open", ""));
        }
        return true;
    }

    /**
     * One variable comparison.
     *
     * <p>{@code { "name": "flags", "at_least": 3 }}. Same four comparators the
     * round condition uses, so an author who has written one has written both.
     */
    private static boolean varMatches(JsonObject v, EngineArena arena,
                                      ServerPlayer who, boolean mine) {
        String name = str(v, "name", "");
        if (name.isEmpty()) {
            return false;
        }
        int value = mine ? arena.vars().get(who, name)
                : v.has("total") ? arena.vars().total(name)
                : arena.vars().get(name);
        if (v.has("equals") && value != intOf(v, "equals", Integer.MIN_VALUE)) {
            return false;
        }
        if (v.has("at_least") && value < intOf(v, "at_least", 0)) {
            return false;
        }
        if (v.has("at_most") && value > intOf(v, "at_most", Integer.MAX_VALUE)) {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /**
     * {@code delay} and {@code every} — the same queue, one repeating.
     *
     * <pre>
     * { "delay": { "seconds": 30, "do": [ ... ] } }
     * { "every": { "seconds": 10, "times": 5, "do": [ ... ] } }
     * </pre>
     *
     * <p>{@code times} left off means "until the run ends", which is what a
     * heartbeat wants and is safe because a run ending clears the queue with it.
     */
    private static void later(EngineArena arena, JsonElement body, ServerPlayer who, boolean repeating) {
        if (!body.isJsonObject()) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        if (!o.has("do") || !o.get("do").isJsonArray()) {
            return;
        }
        int seconds = Math.max(1, Math.min(3600, intOf(o, "seconds", 1)));
        int ticks = seconds * 20;
        arena.schedule(o.getAsJsonArray("do"), who, ticks,
                repeating ? ticks : 0, repeating ? intOf(o, "times", 0) : 0);
    }

    /** {@code { "team_message": { "team": "red", "text": "The flag is out" } }} */
    private static void teamMessage(EngineArena arena, JsonElement body) {
        if (!body.isJsonObject()) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        String team = str(o, "team", "");
        String text = str(o, "text", "");
        if (team.isEmpty() || text.isEmpty()) {
            return;
        }
        for (ServerPlayer p : arena.teams().membersOf(team, arena.everyone())) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    render(arena, p, text)), false);
        }
    }

    /**
     * A variable scoped to a side, stored as a normal variable under a prefixed
     * name.
     *
     * <p>Deliberately not a third storage scope. A team score is a run variable
     * whose name happens to mention a team, and adding a parallel map for it would
     * mean every condition, action and readout learning about a distinction that
     * does not exist underneath. {@code team:red:score} is a name, and everything
     * that already reads names keeps working.
     */
    private static void teamVar(EngineArena arena, ServerPlayer who,
                                JsonElement body, boolean absolute) {
        if (!body.isJsonObject()) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        String name = str(o, "name", "");
        if (name.isEmpty()) {
            return;
        }
        // "team" names a side outright; leaving it off means whoever triggered it.
        String team = str(o, "team", "");
        if (team.isEmpty() && who != null) {
            team = arena.teams().teamOf(who);
        }
        if (team == null || team.isEmpty()) {
            return;
        }
        String key = "team:" + team.toLowerCase(Locale.ROOT) + ":" + name;
        int amount = intOf(o, absolute ? "to" : "by", absolute ? 0 : 1);
        if (absolute) {
            arena.vars().set(key, amount);
        } else {
            arena.vars().add(key, amount);
        }
    }

    /** {@code { "add_var": { "name": "flags", "by": 1 } }} */
    private static void varAction(EngineArena arena, ServerPlayer who,
                                  JsonElement body, boolean mine, boolean absolute) {
        if (!body.isJsonObject()) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        String name = str(o, "name", "");
        if (name.isEmpty()) {
            return;
        }
        int amount = intOf(o, absolute ? "to" : "by", absolute ? 0 : 1);
        if (mine) {
            if (who == null) {
                return;
            }
            if (absolute) {
                arena.vars().set(who, name, amount);
            } else {
                arena.vars().add(who, name, amount);
            }
            return;
        }
        if (absolute) {
            arena.vars().set(name, amount);
        } else {
            arena.vars().add(name, amount);
        }
    }

    /**
     * Ends the run with an outcome rather than just ending it.
     *
     * <p>{@code end_run} existed and said nothing about whether that was a good
     * thing. A game needs to be winnable on its own terms - reach the vault,
     * hold the point, get all four out - and "the run stopped" is not the same
     * message as "you did it".
     */
    private static void finish(EngineArena arena, ServerLevel level, JsonElement body, boolean won) {
        String text = body != null && body.isJsonObject()
                ? str(body.getAsJsonObject(), "title", won ? "§6§lYOU MADE IT" : "§4§lFAILED")
                : (won ? "§6§lYOU MADE IT" : "§4§lFAILED");
        for (ServerPlayer p : arena.everyone()) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    net.minecraft.network.chat.Component.literal(text)));
            level.playSound(null, p.blockPosition(),
                    won ? net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE
                            : net.minecraft.sounds.SoundEvents.WARDEN_DEATH,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, won ? 1.3F : 0.6F);
        }
        EngineArena.stop(false);
    }

    /**
     * Runs a list of actions.
     *
     * <p>Public because delayed work comes back through here rather than through a
     * second copy of the runner. One implementation means a scheduled action and
     * an immediate one cannot drift into behaving differently, which they would.
     */
    public static void runActions(EngineArena arena, ServerLevel level,
                                  JsonArray actions, ServerPlayer who) {
        int budget = ACTION_BUDGET;
        for (JsonElement el : actions) {
            if (budget-- <= 0) {
                break;
            }
            if (el.isJsonObject()) {
                run(arena, level, el.getAsJsonObject(), who);
            }
        }
    }

    /** A line for anyone tracing, from outside this class. */
    public static void note(EngineArena arena, String text) {
        trace(arena, text);
    }

    private static void run(EngineArena arena, ServerLevel level, JsonObject action, ServerPlayer who) {
        for (String key : action.keySet()) {
            JsonElement body = action.get(key);
            switch (key.toLowerCase(Locale.ROOT)) {
                case "message" -> forEach(arena, who, p ->
                        p.displayClientMessage(Component.literal(
                                render(arena, p, asText(body))), false));
                case "actionbar" -> forEach(arena, who, p ->
                        p.displayClientMessage(Component.literal(
                                render(arena, p, asText(body))), true));
                case "title" -> title(arena, who, body);
                case "sound" -> sound(level, arena, who, body);
                case "effect" -> effect(arena, who, body);
                case "give" -> give(arena, who, body);
                case "spawn" -> spawn(arena, level, body);
                case "award" -> award(arena, who, body);
                case "open_area" -> arena.openArea(asText(body));
                case "set_block" -> setBlock(level, body);
                case "end_run" -> EngineArena.stop(true);
                case "set_var" -> varAction(arena, who, body, false, true);
                case "add_var" -> varAction(arena, who, body, false, false);
                case "set_my_var" -> varAction(arena, who, body, true, true);
                case "add_my_var" -> varAction(arena, who, body, true, false);
                // No viewer: the bar is one bar for everybody, so per-player
                // placeholders resolve to blank rather than to the first player's.
                case "set_bar" -> arena.setBarText(render(arena, null, asText(body)));
                case "take" -> take(arena, who, body);
                case "set_phase" -> arena.setPhase(asText(body));
                case "delay" -> later(arena, body, who, false);
                case "every" -> later(arena, body, who, true);
                case "join_team" -> {
                    if (who != null) {
                        arena.teams().join(who, asText(body));
                    }
                }
                case "balance_teams" -> arena.teams().balance(arena.everyone());
                case "team_message" -> teamMessage(arena, body);
                case "add_team_var" -> teamVar(arena, who, body, false);
                case "set_team_var" -> teamVar(arena, who, body, true);
                case "teleport_to_spawn" -> {
                    if (who != null) {
                        BlockPos at = arena.spawnFor(who);
                        who.teleportTo(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5,
                                java.util.Set.of(), who.getYRot(), 0.0F);
                    }
                }
                case "win" -> finish(arena, level, body, true);
                case "lose" -> finish(arena, level, body, false);
                default -> {
                    // Unknown action names are ignored on purpose: a map written
                    // for a later engine should still run on an earlier one.
                }
            }
        }
    }

    private static void forEach(EngineArena arena, ServerPlayer who, java.util.function.Consumer<ServerPlayer> fn) {
        List<ServerPlayer> targets = arena != null ? arena.playersPublic() : List.of();
        if (targets.isEmpty() && who != null) {
            fn.accept(who);
            return;
        }
        for (ServerPlayer p : targets) {
            fn.accept(p);
        }
    }

    private static void title(EngineArena arena, ServerPlayer who, JsonElement body) {
        String main = body.isJsonObject() ? str(body.getAsJsonObject(), "main", "") : asText(body);
        String sub = body.isJsonObject() ? str(body.getAsJsonObject(), "sub", "") : "";
        forEach(arena, who, p -> {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal(render(arena, p, main))));
            if (!sub.isEmpty()) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                        Component.literal(render(arena, p, sub))));
            }
        });
    }

    /**
     * Plays any sound by id.
     *
     * <p>Looked up through the registry rather than named from the SoundEvents
     * constants, which sidesteps the fact that some of those are plain values and
     * some are holders - and means a map can use a sound this code has never
     * heard of, including one added by another mod.
     */
    private static void sound(ServerLevel level, EngineArena arena, ServerPlayer who, JsonElement body) {
        String id = body.isJsonObject() ? str(body.getAsJsonObject(), "id", "") : asText(body);
        ResourceLocation rl = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (rl == null) {
            return;
        }
        var holder = BuiltInRegistries.SOUND_EVENT.getHolder(
                ResourceKey.create(Registries.SOUND_EVENT, rl));
        if (holder.isEmpty()) {
            return;
        }
        float volume = body.isJsonObject() ? (float) dbl(body.getAsJsonObject(), "volume", 1.0) : 1.0F;
        float pitch = body.isJsonObject() ? (float) dbl(body.getAsJsonObject(), "pitch", 1.0) : 1.0F;
        forEach(arena, who, p -> level.playSound(null, p.blockPosition(),
                holder.get().value(), SoundSource.MASTER, volume, pitch));
    }

    private static void effect(EngineArena arena, ServerPlayer who, JsonElement body) {
        if (!body.isJsonObject()) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        ResourceLocation rl = ResourceLocation.tryParse(str(o, "id", "").toLowerCase(Locale.ROOT));
        if (rl == null) {
            return;
        }
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(
                ResourceKey.create(Registries.MOB_EFFECT, rl));
        if (holder.isEmpty()) {
            return;
        }
        int ticks = Math.max(1, Math.min(20 * 60 * 60, intOf(o, "seconds", 10) * 20));
        int amp = Math.max(0, Math.min(9, intOf(o, "amp", 0)));
        forEach(arena, who, p -> p.addEffect(new MobEffectInstance(holder.get(), ticks, amp, false, true)));
    }

    /**
     * How much of something a player is carrying.
     *
     * <p>Searched across the whole inventory by default rather than the held
     * slot, because "do you have the key" is almost never "are you holding the
     * key right now" - a player who put it in their bag to fight something has
     * not stopped having it. {@code slot} narrows it for the cases that do care.
     */
    private static int countItem(ServerPlayer p, ResourceLocation rl, String slot) {
        Item item = BuiltInRegistries.ITEM.get(rl);
        int found = 0;
        switch (slot) {
            case "mainhand" -> found = p.getMainHandItem().is(item) ? p.getMainHandItem().getCount() : 0;
            case "offhand" -> found = p.getOffhandItem().is(item) ? p.getOffhandItem().getCount() : 0;
            case "armor" -> {
                for (ItemStack stack : p.getInventory().armor) {
                    if (stack.is(item)) {
                        found += stack.getCount();
                    }
                }
            }
            default -> {
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    ItemStack stack = p.getInventory().getItem(i);
                    if (stack.is(item)) {
                        found += stack.getCount();
                    }
                }
            }
        }
        return found;
    }

    /** Reads a {@code has_item} clause, in either its short or long form. */
    private static boolean hasItem(ServerPlayer who, JsonElement body) {
        JsonObject o = body.isJsonObject() ? body.getAsJsonObject() : null;
        String id = o != null ? str(o, "id", "") : asText(body);
        ResourceLocation rl = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return false;
        }
        int want = o != null ? Math.max(1, intOf(o, "count", 1)) : 1;
        String slot = o != null ? str(o, "slot", "any").toLowerCase(Locale.ROOT) : "any";
        return countItem(who, rl, slot) >= want;
    }

    /**
     * Takes something away.
     *
     * <p>The counterpart {@code give} never had. Without it an item could be
     * required but never <em>spent</em>, so every key was a key that opened every
     * door forever and every delivery could be made twice. A toll you pay once is
     * a different mechanic from a toll you pass a check for, and only one of them
     * was expressible.
     */
    private static void take(EngineArena arena, ServerPlayer who, JsonElement body) {
        JsonObject o = body.isJsonObject() ? body.getAsJsonObject() : null;
        String id = o != null ? str(o, "id", "") : asText(body);
        ResourceLocation rl = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        int count = o != null ? Math.max(1, intOf(o, "count", 1)) : 1;
        forEach(arena, who, p -> {
            int left = count;
            for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
                ItemStack stack = p.getInventory().getItem(i);
                if (!stack.is(item)) {
                    continue;
                }
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        });
    }

    private static void give(EngineArena arena, ServerPlayer who, JsonElement body) {
        JsonObject o = body.isJsonObject() ? body.getAsJsonObject() : null;
        String id = o != null ? str(o, "id", "") : asText(body);
        ResourceLocation rl = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return;
        }
        int count = o != null ? Math.max(1, Math.min(64, intOf(o, "count", 1))) : 1;
        forEach(arena, who, p -> {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl), count);
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
        });
    }

    private static void award(EngineArena arena, ServerPlayer who, JsonElement body) {
        JsonObject o = body.isJsonObject() ? body.getAsJsonObject() : null;
        int amount = o != null ? intOf(o, "amount", 0) : asInt(body);
        Currency c = Currency.byId(o != null ? str(o, "currency", null) : null);
        forEach(arena, who, p -> c.award(p, amount));
    }

    private static void spawn(EngineArena arena, ServerLevel level, JsonElement body) {
        if (!body.isJsonObject() || arena == null) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        var type = EntityType.byString(str(o, "id", ""));
        if (type.isEmpty()) {
            return;
        }
        BlockPos at = arena.scriptAnchor(str(o, "at", "boss"));
        if (at == null) {
            return;
        }
        int count = Math.max(1, Math.min(24, intOf(o, "count", 1)));
        for (int i = 0; i < count; i++) {
            Entity e = type.get().create(level);
            if (!(e instanceof Mob mob)) {
                return;
            }
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
            int health = intOf(o, "health", 0);
            if (health > 0) {
                var inst = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (inst != null) {
                    inst.setBaseValue(Math.min(1024, health));
                }
                mob.setHealth(mob.getMaxHealth());
            }
            mob.getPersistentData().putBoolean("aztecabyss_engine_mob", true);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            arena.adopt(mob);
        }
    }

    private static void setBlock(ServerLevel level, JsonElement body) {
        if (!body.isJsonObject()) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        if (!o.has("x") || !o.has("y") || !o.has("z")) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(str(o, "id", "minecraft:air").toLowerCase(Locale.ROOT));
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            return;
        }
        BlockState state = BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
        BlockPos at = new BlockPos(intOf(o, "x", 0), intOf(o, "y", 0), intOf(o, "z", 0));
        // Tracked, so a script that reshapes the map during a run does not leave
        // the map reshaped for the next one.
        EngineArena arena = EngineArena.active();
        if (arena != null) {
            arena.setTracked(at, state);
        } else {
            level.setBlock(at, state, 2);
        }
    }

    // ------------------------------------------------------------------

    /**
     * The placeholders a message may contain.
     *
     * <p>Deliberately narrow: lower-case names and one optional argument. There
     * is no arithmetic, no nesting and no conditionals, for the same reason
     * {@link Vars} holds integers and nothing else - a map downloaded off the
     * internet should not be able to run a program on your server, and every
     * expression language is one bad idea away from being able to.
     */
    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("\\{([a-z_]+)(?::([^}]*))?}");

    /**
     * Fills the placeholders in a line of text.
     *
     * <p>Every text the script layer could produce was a literal. An author could
     * count keys, captures, lives and flags - the whole point of variables - and
     * had no way whatsoever to <em>show</em> a number to anybody. The state was
     * tracked perfectly and communicated not at all, which is the difference
     * between an engine that knows the score and a game that tells you it.
     *
     * <p>Rendered per recipient rather than once, because {@code {my_var}} and
     * {@code {player}} mean different things to different people and a message
     * that resolved them once would show the whole squad the first player's
     * numbers.
     */
    static String render(EngineArena arena, ServerPlayer viewer, String raw) {
        if (raw == null || raw.isEmpty() || raw.indexOf('{') < 0) {
            return raw == null ? "" : raw;
        }
        java.util.regex.Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = placeholder(arena, viewer, m.group(1), m.group(2));
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** One placeholder. Anything unrecognised is left exactly as it was written. */
    private static String placeholder(EngineArena arena, ServerPlayer viewer, String name, String arg) {
        String a = arg == null ? "" : arg;
        switch (name) {
            case "var":
                return String.valueOf(arena.vars().get(a));
            case "my_var":
                return viewer == null ? "0" : String.valueOf(arena.vars().get(viewer, a));
            case "total_var":
                return String.valueOf(arena.vars().total(a));
            case "team_var": {
                // {team_var:red:score}, or {team_var:score} for the viewer's side.
                int split = a.indexOf(':');
                String team = split < 0 ? (viewer == null ? "" : arena.teams().teamOf(viewer)) : a.substring(0, split);
                String key = split < 0 ? a : a.substring(split + 1);
                if (team == null || team.isEmpty()) {
                    return "0";
                }
                return String.valueOf(arena.vars().get(
                        "team:" + team.toLowerCase(Locale.ROOT) + ":" + key));
            }
            case "round":
                return String.valueOf(arena.round());
            case "seconds":
                return String.valueOf(arena.elapsedSeconds());
            case "time": {
                int t = arena.elapsedSeconds();
                return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60);
            }
            case "phase":
                return arena.phase();
            case "players":
                return String.valueOf(arena.everyone().size());
            case "player":
                return viewer == null ? "" : viewer.getGameProfile().getName();
            case "team": {
                String t = viewer == null ? null : arena.teams().teamOf(viewer);
                return t == null ? "" : t;
            }
            default:
                // Not ours. A map that writes {"foo"} in a message meant to.
                return arg == null ? "{" + name + "}" : "{" + name + ":" + arg + "}";
        }
    }

    private static String asText(JsonElement el) {
        try {
            return el.isJsonPrimitive() ? el.getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static int asInt(JsonElement el) {
        try {
            return el.isJsonPrimitive() ? el.getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int intOf(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double dbl(JsonObject o, String key, double fallback) {
        try {
            return o.has(key) ? o.get(key).getAsDouble() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
