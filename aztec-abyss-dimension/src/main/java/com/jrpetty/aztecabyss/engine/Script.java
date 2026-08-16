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
 * language, and that is still the shape of it: a rule is "when this happens, if
 * these things are true, do these things". What it now also has is the three
 * things whose absence made real maps unwritable rather than merely limited -
 * branching, reuse, and sums.
 *
 * <pre>
 * "functions": {
 *   "pay_out": [ { "award": { "amount": "50 + {var:streak} * 10" } } ]
 * },
 * "script": [
 *   { "on": "round_start",
 *     "when": { "round": { "every": 10 } },
 *     "do": [ { "title": { "main": "§4THEY SENT SOMETHING" } },
 *             { "spawn": { "id": "minecraft:warden", "at": "boss", "health": 600 } } ] },
 *
 *   { "on": "mob_killed",
 *     "do": [ { "if": { "when": { "var": { "name": "streak", "at_least": 10 } },
 *                       "do":   [ { "call": "pay_out" }, { "set_var": { "name": "streak", "to": 0 } } ],
 *                       "else": [ { "add_var": { "name": "streak", "by": 1 } } ] } },
 *             { "one_of": [ { "weight": 9, "do": [] },
 *                           { "weight": 1, "do": [ { "give": { "id": "minecraft:golden_apple",
 *                                                             "target": "@nearest" } } ] } ] } ] }
 * ]
 * </pre>
 *
 * <h2>Still not a language</h2>
 *
 * <p>The original promise holds: a map downloaded off the internet cannot run a
 * program on your server. {@code if} and {@code one_of} branch but never loop.
 * {@code call} nests eight deep and stops. The action budget is spent across the
 * whole tree rather than per level, so a runaway exhausts it once instead of
 * once per frame. {@link Expr} does arithmetic on integers and has no
 * assignment, no function and no way back into the script. There is still no
 * construct here that can fail to terminate.
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
     * Named action lists, per ruleset: the {@code functions} block.
     *
     * <p>Every trigger used to carry its own copy of what it did. A map with one
     * reward sequence and six ways to earn it wrote that sequence six times, and
     * the seventh edit missed one of them - which is not a hypothetical, it is
     * what always happens. A function is the same list of actions with a name,
     * and {@code call} runs it.
     *
     * <pre>
     * "functions": {
     *   "pay_out": [ { "award": { "amount": 250 } },
     *                { "sound": "minecraft:entity.player.levelup" } ]
     * },
     * "script": [ { "on": "mob_killed", "do": [ { "call": "pay_out" } ] } ]
     * </pre>
     */
    private static final Map<String, Map<String, JsonArray>> FUNCTIONS = new HashMap<>();

    /**
     * How deep {@code call} may nest.
     *
     * <p>A function may call a function, because that is the whole point of
     * having them, and therefore a function may call itself. This is the wall
     * that turns infinite recursion into a rule that stops - together with the
     * shared action budget, which a runaway would exhaust first.
     */
    private static final int MAX_DEPTH = 8;

    /**
     * What a running action knows about the event that started it.
     *
     * <p>Actions used to receive the triggering player and nothing else, which
     * was survivable while conditions could only be asked once, at the top of a
     * rule. {@code if} asks them again, in the middle, so the answers - which
     * region fired, what the event was about - have to travel with the run.
     *
     * <p>It also carries the two things that must be shared rather than copied
     * per nesting level: the action budget, so a deep tree of calls cannot spend
     * thirty-two actions per level, and the cancel flag, so an {@code if} three
     * branches down can still veto the event that started it.
     */
    static final class Ctx {
        String region;
        String subject;
        /** How much the event was about - damage, price, hit points. */
        int amount;
        int budget = ACTION_BUDGET;
        int depth;
        boolean cancelled;

        Ctx(String region, String subject) {
            this(region, subject, 0);
        }

        Ctx(String region, String subject, int amount) {
            this.region = region;
            this.subject = subject;
            this.amount = amount;
        }

        /** Spends one action. False once the rule has had its allowance. */
        boolean spend() {
            return budget-- > 0;
        }
    }

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
            "player_died", "player_joined",
            // Stage B: the half of the game the script could not hear.
            "player_hurt", "player_down", "player_revived",
            "purchase", "powerup_taken", "objective_damaged",
            "block_placed", "item_dropped", "interact_entity", "timer_end",
            // Boards coming off and going back on.
            "barricade_broken", "barricade_repaired", "boss_phase");

    /** Every action the runner understands. */
    private static final java.util.Set<String> ACTIONS = java.util.Set.of(
            "message", "actionbar", "title", "sound", "effect", "give", "spawn", "award",
            "open_area", "set_block", "end_run", "set_var", "add_var", "set_my_var",
            "add_my_var", "win", "lose", "set_bar", "join_team", "balance_teams",
            "team_message", "add_team_var", "set_team_var", "teleport_to_spawn",
            "delay", "every", "take", "set_phase", "teleport", "heal", "clear_effects",
            "set_saved_var", "add_saved_var", "set_my_saved_var", "add_my_saved_var",
            // The language, rather than the verbs: branching, reuse and chance.
            "if", "call", "one_of", "tag", "untag",
            // Vetoing an event, and clocks with names.
            "cancel", "timer",
            // The sky, rules that live in a place, and the ruleset itself.
            "set_time", "weather", "zone", "set_rule",
            // Geometry as a verb, and the lights.
            "fill", "move", "power");

    /** Every condition the matcher understands. */
    private static final java.util.Set<String> CONDITIONS = java.util.Set.of(
            "round", "area_open", "region", "var", "my_var", "team", "team_var", "seconds", "block",
            "has_item", "killed", "chance", "phase", "subject", "players",
            "saved_var", "my_saved_var", "tag", "not", "timer", "amount");

    public static List<String> warnings(String rulesetId) {
        return WARNINGS.getOrDefault(rulesetId, List.of());
    }

    public static void clear() {
        BY_RULESET.clear();
        WARNINGS.clear();
        FUNCTIONS.clear();
    }

    /** Pulls the {@code script} array out of a ruleset file. */
    public static void load(String rulesetId, JsonObject root) {
        List<String> warn = new ArrayList<>();
        // Functions first, so a rule that calls one can be checked against the
        // list rather than only failing at the moment somebody plays the map.
        if (root.has("functions") && root.get("functions").isJsonObject()) {
            Map<String, JsonArray> fns = new HashMap<>();
            JsonObject block = root.getAsJsonObject("functions");
            for (String name : block.keySet()) {
                if (block.get(name).isJsonArray()) {
                    fns.put(name.toLowerCase(Locale.ROOT), block.getAsJsonArray(name));
                } else {
                    warn.add("function \"" + name + "\" is not a list of actions — ignored");
                }
            }
            if (!fns.isEmpty()) {
                FUNCTIONS.put(rulesetId, fns);
            }
        }
        if (!root.has("script") || !root.get("script").isJsonArray()) {
            if (!warn.isEmpty()) {
                WARNINGS.put(rulesetId, List.copyOf(warn));
            }
            return;
        }
        List<Rule> rules = new ArrayList<>();
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
            checkActions(rulesetId, o.getAsJsonArray("do"), "rule " + index, warn, 0);
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

    /** How many named functions a ruleset declared, for {@code /arena rules}. */
    public static int functionCount(String rulesetId) {
        return FUNCTIONS.getOrDefault(rulesetId, Map.of()).size();
    }

    /**
     * Walks an action list looking for names nothing will answer to.
     *
     * <p>Recursive, because {@code if}, {@code one_of}, {@code delay} and
     * {@code every} all carry action lists of their own, and a typo two levels
     * down is exactly as silent as a typo at the top - more so, because it only
     * runs when a branch is taken.
     */
    private static void checkActions(String rulesetId, JsonArray actions,
                                     String where, List<String> warn, int depth) {
        if (actions == null || depth > MAX_DEPTH) {
            return;
        }
        for (JsonElement act : actions) {
            if (!act.isJsonObject()) {
                continue;
            }
            JsonObject o = act.getAsJsonObject();
            for (String a : o.keySet()) {
                String key = a.toLowerCase(Locale.ROOT);
                if (!ACTIONS.contains(key)) {
                    warn.add(where + ": unknown action \"" + a + "\" — does nothing");
                    continue;
                }
                JsonElement body = o.get(a);
                switch (key) {
                    case "if" -> {
                        if (body.isJsonObject()) {
                            JsonObject b = body.getAsJsonObject();
                            checkWhen(b.has("when") && b.get("when").isJsonObject()
                                    ? b.getAsJsonObject("when") : null, where, warn);
                            if (b.has("do") && b.get("do").isJsonArray()) {
                                checkActions(rulesetId, b.getAsJsonArray("do"), where, warn, depth + 1);
                            }
                            if (b.has("else") && b.get("else").isJsonArray()) {
                                checkActions(rulesetId, b.getAsJsonArray("else"), where, warn, depth + 1);
                            }
                        }
                    }
                    case "one_of" -> {
                        if (body.isJsonArray()) {
                            for (JsonElement branch : body.getAsJsonArray()) {
                                if (branch.isJsonObject() && branch.getAsJsonObject().has("do")
                                        && branch.getAsJsonObject().get("do").isJsonArray()) {
                                    checkActions(rulesetId,
                                            branch.getAsJsonObject().getAsJsonArray("do"),
                                            where, warn, depth + 1);
                                }
                            }
                        }
                    }
                    case "delay", "every" -> {
                        if (body.isJsonObject() && body.getAsJsonObject().has("do")
                                && body.getAsJsonObject().get("do").isJsonArray()) {
                            checkActions(rulesetId, body.getAsJsonObject().getAsJsonArray("do"),
                                    where, warn, depth + 1);
                        }
                    }
                    case "call" -> {
                        String name = asText(body).toLowerCase(Locale.ROOT);
                        Map<String, JsonArray> fns = FUNCTIONS.get(rulesetId);
                        if (fns == null || !fns.containsKey(name)) {
                            warn.add(where + ": calls \"" + name + "\", which is not in \"functions\"");
                        }
                    }
                    default -> {
                        // A plain verb. Nothing nested to look inside.
                    }
                }
            }
        }
    }

    /** The same unknown-name check for a nested {@code when}. */
    private static void checkWhen(JsonObject when, String where, List<String> warn) {
        if (when == null) {
            return;
        }
        for (String c : when.keySet()) {
            if (!CONDITIONS.contains(c.toLowerCase(Locale.ROOT))) {
                warn.add(where + ": unknown condition \"" + c + "\" — ignored");
            }
        }
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
            Ctx ctx = new Ctx(region, blockId);
            body(arena, level, rule.actions(), who, ctx);
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
            Ctx ctx = new Ctx(regionId, subject);
            body(arena, level, rule.actions(), who, ctx);
            if (ctx.cancelled) {
                cancelled = true;
            }
        }
    }

    /**
     * Fires an event that can be called off, and says whether it was.
     *
     * <p>Everything before this reported the past: the block <em>was</em> broken,
     * the player <em>was</em> hurt. A map could dress that up but never prevent
     * it, so "this wall cannot be mined", "no fall damage in the pit", "the
     * carrier cannot open doors" were unsayable — every one of them a rule about
     * what must <em>not</em> happen.
     *
     * @return true if a rule ran {@code cancel}, and the caller should stop
     */
    public static boolean fireCancellable(EngineArena arena, ServerLevel level, String rulesetId,
                                          String event, ServerPlayer who, String regionId,
                                          String subject, int amount) {
        List<Rule> rules = BY_RULESET.get(rulesetId);
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        boolean veto = false;
        for (Rule rule : rules) {
            if (!rule.event().equals(event)) {
                continue;
            }
            if (!matches(rule.when(), arena, who, regionId, subject, amount)) {
                continue;
            }
            trace(arena, "§afire §f" + event + (subject == null ? "" : " §7(" + subject + ")"));
            Ctx ctx = new Ctx(regionId, subject, amount);
            body(arena, level, rule.actions(), who, ctx);
            if (ctx.cancelled) {
                veto = true;
            }
        }
        return veto;
    }

    /** The same, for events that carry no number. */
    public static boolean fireCancellable(EngineArena arena, ServerLevel level, String rulesetId,
                                          String event, ServerPlayer who, String subject) {
        return fireCancellable(arena, level, rulesetId, event, who, null, subject, 0);
    }

    /** Fires an ordinary event that carries a quantity. */
    public static void fireAmount(EngineArena arena, ServerLevel level, String rulesetId,
                                  String event, ServerPlayer who, String subject, int amount) {
        fireCancellable(arena, level, rulesetId, event, who, null, subject, amount);
    }

    /**
     * Whether the last event fired was vetoed by a {@code cancel}.
     *
     * <p>Read immediately after firing, by the handlers that have something to
     * cancel. A field rather than a return value because {@code fire} is called
     * from seventeen places that do not care, and making all of them handle a
     * boolean to serve the three that do is the wrong trade.
     */
    private static boolean cancelled;

    /** True if the event just fired asked to be called off. Clears on read. */
    public static boolean wasCancelled() {
        boolean was = cancelled;
        cancelled = false;
        return was;
    }

    /**
     * Runs a list of actions against one context.
     *
     * <p>The single path every action list goes through - a rule's own
     * {@code do}, a branch of an {@code if}, the body of a {@code call}, a
     * scheduled block coming back later. One implementation, so a branch and a
     * top-level action cannot drift into behaving differently.
     */
    private static void body(EngineArena arena, ServerLevel level,
                             JsonArray actions, ServerPlayer who, Ctx ctx) {
        if (actions == null) {
            return;
        }
        for (JsonElement el : actions) {
            if (!ctx.spend()) {
                trace(arena, "§cbudget spent §7— remaining actions skipped");
                return;
            }
            if (el.isJsonObject()) {
                try {
                    run(arena, level, el.getAsJsonObject(), who, ctx);
                } catch (RuntimeException ignored) {
                    // One bad action, not one bad run.
                }
            }
        }
    }

    /** Conditions. Absent means always. */
    private static boolean matches(JsonObject when, EngineArena arena,
                                   ServerPlayer who, String regionId) {
        return matches(when, arena, who, regionId, null, 0);
    }

    private static boolean matches(JsonObject when, EngineArena arena,
                                   ServerPlayer who, String regionId, String subject) {
        return matches(when, arena, who, regionId, subject, 0);
    }

    private static boolean matches(JsonObject when, EngineArena arena, ServerPlayer who,
                                   String regionId, String subject, int amount) {
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
        // What the world still knows from previous runs. Same four comparators
        // as every other number in here, so an author who has written one
        // condition has written all of them.
        if (when.has("saved_var") && when.get("saved_var").isJsonObject()) {
            if (!savedMatches(when.getAsJsonObject("saved_var"), arena, null)) {
                return false;
            }
        }
        if (when.has("my_saved_var") && when.get("my_saved_var").isJsonObject()) {
            if (who == null || !savedMatches(when.getAsJsonObject("my_saved_var"), arena, who)) {
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
        // How long is left on a named clock. Zero when it is not running, so
        // {"timer":{"id":"bomb","at_most":10}} is also how a map asks "is the
        // bomb nearly out" without a second variable shadowing the first.
        if (when.has("timer") && when.get("timer").isJsonObject()) {
            JsonObject t = when.getAsJsonObject("timer");
            int left = arena.timerSeconds(str(t, "id", "").toLowerCase(Locale.ROOT));
            if (t.has("equals") && left != intOf(t, "equals", -1)) {
                return false;
            }
            if (t.has("at_least") && left < intOf(t, "at_least", 0)) {
                return false;
            }
            if (t.has("at_most") && left > intOf(t, "at_most", Integer.MAX_VALUE)) {
                return false;
            }
            if (t.has("running") && (left > 0) != t.get("running").getAsBoolean()) {
                return false;
            }
        }
        // How much of something an event was about - damage dealt, points
        // spent, hit points taken off an objective. Carried on the events that
        // have a number in them and compared the same way as everything else.
        if (when.has("amount") && when.get("amount").isJsonObject()) {
            JsonObject a = when.getAsJsonObject("amount");
            int got = amount;
            if (a.has("equals") && got != intOf(a, "equals", Integer.MIN_VALUE)) {
                return false;
            }
            if (a.has("at_least") && got < intOf(a, "at_least", 0)) {
                return false;
            }
            if (a.has("at_most") && got > intOf(a, "at_most", Integer.MAX_VALUE)) {
                return false;
            }
        }
        // A tag somebody is carrying, put there by the "tag" action. The
        // engine's own memory of a player that is not a number: who has the
        // flag, who has been marked, who already opened this door once.
        if (when.has("tag")) {
            if (who == null || !who.getTags().contains(
                    str(when, "tag", "").toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        // The inverse of any condition, including itself. Without this every
        // negative test needed a variable flipped by a second rule, because
        // there was no way to say "and this is not true" about anything.
        if (when.has("not") && when.get("not").isJsonObject()) {
            if (matches(when.getAsJsonObject("not"), arena, who, regionId, subject, amount)) {
                return false;
            }
        }
        if (when.has("area_open")) {
            return arena.isAreaOpen(str(when, "area_open", ""));
        }
        return true;
    }

    /** One comparison against something the world remembered. */
    private static boolean savedMatches(JsonObject v, EngineArena arena, ServerPlayer who) {
        if (arena.level().getServer() == null) {
            return false;
        }
        SavedVars saved = SavedVars.get(arena.level().getServer());
        boolean global = v.has("global") && v.get("global").getAsBoolean();
        String name = str(v, "name", "");
        int value = who == null
                ? saved.get(arena.rulesetId(), name, global)
                : saved.get(who.getUUID(), arena.rulesetId(), name, global);
        if (v.has("equals") && value != intOf(v, "equals", 0)) {
            return false;
        }
        if (v.has("at_least") && value < intOf(v, "at_least", 0)) {
            return false;
        }
        return !v.has("at_most") || value <= intOf(v, "at_most", Integer.MAX_VALUE);
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
        int seconds = Math.max(1, Math.min(3600, num(o, "seconds", 1, arena, who)));
        int ticks = seconds * 20;
        arena.schedule(o.getAsJsonArray("do"), who, ticks,
                repeating ? ticks : 0, repeating ? num(o, "times", 0, arena, who) : 0);
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
        int amount = num(o, absolute ? "to" : "by", absolute ? 0 : 1, arena, who);
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
        int amount = num(o, absolute ? "to" : "by", absolute ? 0 : 1, arena, who);
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
        body(arena, level, actions, who, new Ctx(null, null));
    }

    /** A line for anyone tracing, from outside this class. */
    public static void note(EngineArena arena, String text) {
        trace(arena, text);
    }

    private static void run(EngineArena arena, ServerLevel level,
                            JsonObject action, ServerPlayer who, Ctx ctx) {
        for (String key : action.keySet()) {
            JsonElement body = action.get(key);
            switch (key.toLowerCase(Locale.ROOT)) {
                // ---- the language ---------------------------------------
                case "if" -> branch(arena, level, body, who, ctx);
                case "call" -> call(arena, level, asText(body), who, ctx);
                case "one_of" -> oneOf(arena, level, body, who, ctx);
                case "cancel" -> {
                    // Only meaningful on an event that has something left to
                    // stop. On any other it is a no-op rather than an error,
                    // because which events are cancellable is the engine's
                    // business and not something a map should have to track.
                    ctx.cancelled = true;
                    trace(arena, "§ccancel §7— event vetoed");
                }
                case "timer" -> timer(arena, body, who);
                case "set_time" -> setTime(arena, level, body, who);
                case "weather" -> weather(arena, level, body, who);
                case "zone" -> zone(arena, body, who);
                case "set_rule" -> setRule(arena, body, who);
                case "fill" -> fill(arena, level, body, who);
                case "move" -> move(arena, level, body, who);
                case "power" -> power(arena, body);
                case "tag" -> forEach(arena, who, body, p -> p.addTag(tagName(body)));
                case "untag" -> forEach(arena, who, body, p -> p.removeTag(tagName(body)));
                case "message" -> forEach(arena, who, body, p ->
                        p.displayClientMessage(Component.literal(
                                render(arena, p, asText(body))), false));
                case "actionbar" -> forEach(arena, who, body, p ->
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
                case "teleport" -> teleport(arena, level, who, body);
                case "heal" -> forEach(arena, who, body, p -> {
                    JsonObject h = body.isJsonObject() ? body.getAsJsonObject() : null;
                    float amount = h != null ? num(h, "hearts", 0, arena, p) * 2.0F : 0.0F;
                    // No amount means all of it, because "heal" with no argument
                    // obviously means heal, and making an author write the number
                    // for the common case is a tax on the common case.
                    p.setHealth(amount <= 0.0F ? p.getMaxHealth()
                            : Math.min(p.getMaxHealth(), p.getHealth() + amount));
                });
                case "clear_effects" -> forEach(arena, who, body, p -> p.removeAllEffects());
                case "set_saved_var" -> savedVar(arena, level, who, body, false, true);
                case "add_saved_var" -> savedVar(arena, level, who, body, false, false);
                case "set_my_saved_var" -> savedVar(arena, level, who, body, true, true);
                case "add_my_saved_var" -> savedVar(arena, level, who, body, true, false);
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

    /**
     * {@code if} — the condition, asked in the middle of a rule.
     *
     * <pre>
     * { "if": { "when": { "var": { "name": "keys", "at_least": 3 } },
     *           "do":   [ { "open_area": "vault" } ],
     *           "else": [ { "message": "Still locked." } ] } }
     * </pre>
     *
     * <p>Conditions existed only at the top of a rule, which meant every branch
     * of every decision was a whole separate rule listening to the same event
     * with the opposite test written out by hand. Two branches doubled the rule
     * count, three tripled it, and none of them could share a step. This is the
     * same {@code when} block, in the same grammar, asked where the decision is.
     */
    private static void branch(EngineArena arena, ServerLevel level,
                               JsonElement body, ServerPlayer who, Ctx ctx) {
        if (!body.isJsonObject() || ctx.depth >= MAX_DEPTH) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        JsonObject when = o.has("when") && o.get("when").isJsonObject()
                ? o.getAsJsonObject("when") : null;
        boolean pass = matches(when, arena, who, ctx.region, ctx.subject, ctx.amount);
        JsonArray take = pass
                ? (o.has("do") && o.get("do").isJsonArray() ? o.getAsJsonArray("do") : null)
                : (o.has("else") && o.get("else").isJsonArray() ? o.getAsJsonArray("else") : null);
        trace(arena, (pass ? "§aif §7— taken" : "§8if §7— else"));
        if (take == null) {
            return;
        }
        ctx.depth++;
        body(arena, level, take, who, ctx);
        ctx.depth--;
    }

    /** {@code call} — runs a named block out of the ruleset's {@code functions}. */
    private static void call(EngineArena arena, ServerLevel level,
                             String name, ServerPlayer who, Ctx ctx) {
        if (name == null || name.isEmpty() || ctx.depth >= MAX_DEPTH) {
            if (ctx.depth >= MAX_DEPTH) {
                trace(arena, "§ccall §7— too deep, stopped at " + MAX_DEPTH);
            }
            return;
        }
        Map<String, JsonArray> fns = FUNCTIONS.get(arena.rulesetId());
        JsonArray fn = fns == null ? null : fns.get(name.toLowerCase(Locale.ROOT));
        if (fn == null) {
            trace(arena, "§ccall §7— no function called §f" + name);
            return;
        }
        trace(arena, "§7call §f" + name);
        ctx.depth++;
        body(arena, level, fn, who, ctx);
        ctx.depth--;
    }

    /**
     * {@code one_of} — one branch, chosen by weight.
     *
     * <pre>
     * { "one_of": [ { "weight": 3, "do": [ { "message": "Nothing here." } ] },
     *               { "weight": 1, "do": [ { "give": "minecraft:diamond" } ] } ] }
     * </pre>
     *
     * <p>{@code chance} could already make one thing happen sometimes, but a
     * choice between three outcomes had to be written as three rules with
     * hand-computed percentages that stopped adding up the moment a fourth was
     * added. Weights are relative and need no arithmetic from the author.
     */
    private static void oneOf(EngineArena arena, ServerLevel level,
                              JsonElement body, ServerPlayer who, Ctx ctx) {
        if (!body.isJsonArray() || ctx.depth >= MAX_DEPTH) {
            return;
        }
        JsonArray branches = body.getAsJsonArray();
        int total = 0;
        for (JsonElement el : branches) {
            if (el.isJsonObject()) {
                total += Math.max(0, intOf(el.getAsJsonObject(), "weight", 1));
            }
        }
        if (total <= 0) {
            return;
        }
        int roll = arena.rng().nextInt(total);
        for (JsonElement el : branches) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            roll -= Math.max(0, intOf(o, "weight", 1));
            if (roll >= 0) {
                continue;
            }
            if (o.has("do") && o.get("do").isJsonArray()) {
                ctx.depth++;
                body(arena, level, o.getAsJsonArray("do"), who, ctx);
                ctx.depth--;
            }
            return;
        }
    }

    /**
     * {@code timer} — a clock with a name.
     *
     * <pre>
     * { "timer": { "id": "bomb", "seconds": 90, "bar": "§cDetonation",
     *              "on_end": [ { "lose": {} } ] } }
     * { "timer": { "id": "bomb", "stop": true } }
     * { "timer": { "id": "bomb", "add": 30 } }
     * </pre>
     *
     * <p>Every countdown in every map so far was hand-built out of {@code every}
     * plus a variable plus a rule to notice it reaching zero - four moving parts
     * to express one, rewritten slightly differently each time and wrong in a
     * different way each time. A timer counts itself down, shows itself if asked,
     * fires its own actions at zero, and can be read by a condition and printed
     * by a placeholder while it runs.
     */
    private static void timer(EngineArena arena, JsonElement body, ServerPlayer who) {
        if (!body.isJsonObject() || arena == null) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        String id = str(o, "id", "").toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            trace(arena, "§ctimer §7— no id given");
            return;
        }
        if (o.has("stop") && o.get("stop").getAsBoolean()) {
            arena.stopTimer(id);
            trace(arena, "§7timer §f" + id + " §7stopped");
            return;
        }
        if (o.has("add")) {
            arena.addTimer(id, num(o, "add", 0, arena, who));
            return;
        }
        int seconds = Math.max(1, Math.min(36000, num(o, "seconds", 60, arena, who)));
        String bar = o.has("bar") ? str(o, "bar", "") : null;
        JsonArray onEnd = o.has("on_end") && o.get("on_end").isJsonArray()
                ? o.getAsJsonArray("on_end") : null;
        arena.startTimer(id, seconds, bar, onEnd, who);
        trace(arena, "§7timer §f" + id + " §7— " + seconds + "s");
    }

    /**
     * {@code power} — the lights in a region, off or on.
     *
     * <pre>
     * { "power": { "region": "east_wing", "on": false } }
     * </pre>
     *
     * <p>Darkness was something a map could describe and not do. It is the
     * cheapest way there is to change a room somebody has already been shown,
     * and it was the one thing ENGINE.md designed a marker for and never built.
     *
     * <p>Restoring puts the exact blocks back - wall torches facing the way they
     * faced - because the taking-out goes through the same tracked-block system
     * that cleans a run up afterwards, rather than remembering it twice.
     */
    private static void power(EngineArena arena, JsonElement body) {
        if (arena == null) {
            return;
        }
        JsonObject o = body.isJsonObject() ? body.getAsJsonObject() : null;
        String region = o != null ? str(o, "region", str(o, "circuit", "")) : asText(body);
        boolean on = o == null || !o.has("on") || o.get("on").getAsBoolean();
        int changed = arena.power(region, on);
        trace(arena, "§7power §f" + region + " §7" + (on ? "on" : "off")
                + " §8— " + changed + " blocks");
    }

    /** Reads a three-number corner, each of which may be a sum. */
    private static BlockPos corner(JsonObject o, String key, EngineArena arena, ServerPlayer who) {
        if (o.has(key) && o.get(key).isJsonArray()) {
            com.google.gson.JsonArray a = o.getAsJsonArray(key);
            if (a.size() >= 3) {
                JsonObject shim = new JsonObject();
                shim.add("x", a.get(0));
                shim.add("y", a.get(1));
                shim.add("z", a.get(2));
                return new BlockPos(num(shim, "x", 0, arena, who),
                        num(shim, "y", 0, arena, who),
                        num(shim, "z", 0, arena, who));
            }
        }
        // A region name is a corner too - the only named places an author has.
        String region = str(o, key, "");
        if (!region.isEmpty() && arena != null) {
            return arena.regionPos(region);
        }
        return null;
    }

    /** How many blocks one action may write, so a typo cannot stamp a world. */
    private static final int FILL_LIMIT = 32768;

    /**
     * {@code fill} — a box of blocks, in one action.
     *
     * <pre>
     * { "fill": { "from": [0, 64, 0], "to": [16, 64, 16], "id": "minecraft:water" } }
     * { "fill": { "from": [0, "{var:tide}", 0], "to": [64, "{var:tide}", 64],
     *             "id": "minecraft:water" } }
     * </pre>
     *
     * <p>Promised in ENGINE.md and never built, and its absence is why a map
     * could not have a tide, a closing wall, a flooding cellar or a bridge that
     * builds itself: {@code set_block} does one block, and a hundred of them is
     * not a rule, it is a transcription.
     *
     * <p>Coordinates go through the same expression reader as every other
     * number, which is what turns this from "fill a box" into "fill the box the
     * water has reached" — the whole of a scheduled hazard, composed out of
     * pieces that already exist, rather than a bespoke verb for tides.
     *
     * <p>Every write is tracked, so a run that reshapes the map leaves the map
     * unreshaped for the next one.
     */
    private static void fill(EngineArena arena, ServerLevel level,
                             JsonElement body, ServerPlayer who) {
        if (!body.isJsonObject() || arena == null) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        BlockPos a = corner(o, "from", arena, who);
        BlockPos b = corner(o, "to", arena, who);
        if (a == null || b == null) {
            trace(arena, "§cfill §7— needs \"from\" and \"to\"");
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(
                str(o, "id", "minecraft:air").toLowerCase(Locale.ROOT));
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            return;
        }
        BlockState state = BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
        int x0 = Math.min(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX());
        int y1 = Math.max(a.getY(), b.getY());
        int z1 = Math.max(a.getZ(), b.getZ());
        long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (volume > FILL_LIMIT) {
            trace(arena, "§cfill §7— " + volume + " blocks is past the limit of " + FILL_LIMIT);
            return;
        }
        boolean keepAir = o.has("replace_air") && !o.get("replace_air").getAsBoolean();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (!arena.contains(at)) {
                        continue; // a map may only reshape itself
                    }
                    if (keepAir && level.getBlockState(at).isAir()) {
                        continue;
                    }
                    arena.setTracked(at, state);
                }
            }
        }
        trace(arena, "§7fill §f" + volume + " §7blocks");
    }

    /**
     * {@code move} — takes a box of blocks and puts it somewhere else.
     *
     * <pre>
     * { "move": { "from": [0, 64, 0], "to": [8, 64, 8], "by": [0, 1, 0] } }
     * </pre>
     *
     * <p>One translation per call, deliberately. Animation is
     * {@code every} wrapped round this, which is both simpler than a bespoke
     * track and more useful: the same verb makes a lift, a closing wall, a
     * rotating bridge and a platform that only moves while somebody stands on a
     * plate, because the schedule is the author's rather than the engine's.
     *
     * <p>Read first, then written, so a move that overlaps its own source does
     * not eat its own tail - which is what a one-block lift always does.
     */
    private static void move(EngineArena arena, ServerLevel level,
                             JsonElement body, ServerPlayer who) {
        if (!body.isJsonObject() || arena == null) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        BlockPos a = corner(o, "from", arena, who);
        BlockPos b = corner(o, "to", arena, who);
        BlockPos by = corner(o, "by", arena, who);
        if (a == null || b == null || by == null) {
            trace(arena, "§cmove §7— needs \"from\", \"to\" and \"by\"");
            return;
        }
        int x0 = Math.min(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX());
        int y1 = Math.max(a.getY(), b.getY());
        int z1 = Math.max(a.getZ(), b.getZ());
        long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (volume > FILL_LIMIT) {
            trace(arena, "§cmove §7— " + volume + " blocks is past the limit");
            return;
        }
        // Read the whole shape before writing any of it.
        java.util.List<BlockPos> from = new ArrayList<>();
        java.util.List<BlockState> what = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos at = new BlockPos(x, y, z);
                    from.add(at);
                    what.add(level.getBlockState(at));
                }
            }
        }
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (BlockPos at : from) {
            if (arena.contains(at)) {
                arena.setTracked(at, air);
            }
        }
        for (int i = 0; i < from.size(); i++) {
            BlockPos to = from.get(i).offset(by.getX(), by.getY(), by.getZ());
            if (arena.contains(to)) {
                arena.setTracked(to, what.get(i));
            }
        }
        trace(arena, "§7move §f" + volume + " §7blocks by "
                + by.getX() + "," + by.getY() + "," + by.getZ());
    }

    /**
     * {@code set_rule} — the run changes its mind about a number.
     *
     * <pre>
     * { "set_rule": { "path": "rounds.per_round", "to": 8 } }
     * { "set_rule": { "path": "rounds.per_round", "by": "{var:rage}" } }
     * { "set_rule": { "path": "rounds.per_round", "reset": true } }
     * </pre>
     *
     * <p>The ruleset file stays immutable and read-once, because two runs of the
     * same map should be the same game. What changes is a layer the <em>run</em>
     * carries over it, which dies with the run - so nothing a script does can
     * leak into the next game or back to the file on disk.
     *
     * <p>Overrides are clamped to the same bounds the loader uses. A script is
     * data from a stranger exactly as a ruleset is, and does not get to ask for
     * four hundred simultaneous zombies by a route the file was not allowed to
     * take.
     *
     * <p>Paths, all optional to know: {@code rounds.base_count},
     * {@code rounds.per_round}, {@code rounds.concurrent_cap},
     * {@code rounds.breather_start}, {@code rounds.breather_min},
     * {@code economy.powerup_chance}, {@code downed.bleedout_seconds},
     * {@code downed.revive_seconds}.
     */
    private static void setRule(EngineArena arena, JsonElement body, ServerPlayer who) {
        if (!body.isJsonObject() || arena == null) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        String path = str(o, "path", "").toLowerCase(Locale.ROOT);
        if (path.isEmpty()) {
            trace(arena, "§cset_rule §7— no path given");
            return;
        }
        if (o.has("reset") && o.get("reset").getAsBoolean()) {
            arena.clearRule(path);
            trace(arena, "§7set_rule §f" + path + " §7— back to the file");
            return;
        }
        // The value in play, which is the file's until somebody overrides it -
        // otherwise "add two" to an untouched rule quietly means "set to two".
        double now = arena.ruleNow(path);
        double want = o.has("by") ? now + num(o, "by", 0, arena, who)
                : num(o, "to", (int) Math.round(now), arena, who);
        arena.setRule(path, want);
        trace(arena, "§7set_rule §f" + path + " §7→ " + arena.rule(path, want));
    }

    /**
     * {@code set_time} — moves the sky.
     *
     * <pre>
     * { "set_time": { "to": 18000 } }        midnight
     * { "set_time": { "add": 1000 } }
     * { "set_time": { "to": "night", "frozen": true } }
     * </pre>
     *
     * <p>Sent as a packet per player rather than written to the level, because
     * {@code setDayTime} on a non-overworld level is a no-op - the level data is
     * a read-only view of the overworld's. This is the same technique the maze
     * uses for its own clock, which is the proof it works: hold the daylight
     * cycle off and the client keeps exactly what it is given.
     */
    private static void setTime(EngineArena arena, ServerLevel level,
                                JsonElement body, ServerPlayer who) {
        if (!body.isJsonObject()) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        long now = level.getDayTime() % 24000L;
        long want;
        if (o.has("add")) {
            want = now + num(o, "add", 0, arena, who);
        } else if (o.has("to") && o.get("to").isJsonPrimitive()
                && o.get("to").getAsJsonPrimitive().isString()
                && !Expr.looksLikeExpression(str(o, "to", ""))) {
            // Words, because "dawn" is what an author means and 23000 is not.
            want = switch (str(o, "to", "").toLowerCase(Locale.ROOT)) {
                case "dawn", "sunrise" -> 23000L;
                case "day", "noon" -> 6000L;
                case "dusk", "sunset" -> 12000L;
                case "night", "midnight" -> 18000L;
                default -> now;
            };
        } else {
            want = num(o, "to", (int) now, arena, who);
        }
        final long at = ((want % 24000L) + 24000L) % 24000L;
        boolean cycle = !(o.has("frozen") && o.get("frozen").getAsBoolean());
        forEach(arena, who, body, p -> p.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
                        level.getGameTime(), at, cycle)));
    }

    /**
     * {@code weather} — rain, thunder or clear, for the people in the run.
     *
     * <p>Also per player, and for the same reason: a custom dimension reads the
     * overworld's weather and cannot be told its own. The game-event packet is
     * what the client actually listens to, so sending it directly gets the
     * result without pretending the level owns anything.
     */
    private static void weather(EngineArena arena, ServerLevel level,
                                JsonElement body, ServerPlayer who) {
        String kind = body.isJsonObject()
                ? str(body.getAsJsonObject(), "type", "clear") : asText(body);
        float rain = switch (kind.toLowerCase(Locale.ROOT)) {
            case "rain", "raining" -> 1.0F;
            case "storm", "thunder" -> 1.0F;
            default -> 0.0F;
        };
        boolean storm = kind.equalsIgnoreCase("storm") || kind.equalsIgnoreCase("thunder");
        forEach(arena, who, body, p -> {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,
                    storm ? 1.0F : 0.0F));
        });
    }

    /**
     * {@code zone} — turns a region's rules on or off.
     *
     * <pre>
     * { "zone": { "id": "flooded", "effect": "minecraft:slowness", "amp": 1 } }
     * { "zone": { "id": "vault",   "no_build": true, "no_pvp": true } }
     * { "zone": { "id": "flooded", "clear": true } }
     * </pre>
     *
     * <p>A region could be entered and left and that was the whole of it - the
     * script was told about the crossing and the place itself had no properties.
     * Every rule about somewhere therefore had to be written as a pair of rules
     * about going in and coming out, and kept in step by hand forever. A zone
     * holds its own rules, so the place is the rule.
     */
    private static void zone(EngineArena arena, JsonElement body, ServerPlayer who) {
        if (!body.isJsonObject() || arena == null) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        String id = str(o, "id", "").toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            trace(arena, "§czone §7— no id given");
            return;
        }
        if (o.has("clear") && o.get("clear").getAsBoolean()) {
            arena.clearZoneRules(id);
            return;
        }
        arena.setZoneRules(id, o.deepCopy());
    }

    /** A scoreboard tag, read from either the short or the long form. */
    private static String tagName(JsonElement body) {
        String raw = body.isJsonObject() ? str(body.getAsJsonObject(), "name", "") : asText(body);
        return raw.toLowerCase(Locale.ROOT);
    }

    /**
     * Who an action happens to.
     *
     * <p>Actions hit the whole run or the one player who tripped the trigger,
     * and nothing in between - so "the carrier drops the flag", "the nearest
     * player hears the whisper", "only red gets the key" could not be said at
     * all. A {@code target} on the action's body picks a narrower set:
     *
     * <ul>
     *   <li>{@code all} — everybody in the run. The default, unchanged.</li>
     *   <li>{@code @self} — whoever triggered the event, and nobody else.</li>
     *   <li>{@code @nearest} — the player closest to the one who triggered it.</li>
     *   <li>{@code @random} — one of them, picked fresh each time.</li>
     *   <li>{@code @team:red} — one side.</li>
     *   <li>{@code @tagged:carrier} — everyone carrying a tag, set by {@code tag}.</li>
     *   <li>{@code @others} — everybody except the trigger.</li>
     * </ul>
     */
    private static List<ServerPlayer> targets(EngineArena arena, ServerPlayer who, JsonElement src) {
        List<ServerPlayer> all = arena != null ? arena.playersPublic() : List.of();
        if (all.isEmpty() && who != null) {
            all = List.of(who);
        }
        String spec = src != null && src.isJsonObject()
                ? str(src.getAsJsonObject(), "target", "").toLowerCase(Locale.ROOT) : "";
        if (spec.isEmpty() || spec.equals("all")) {
            return all;
        }
        if (spec.equals("@self") || spec.equals("self")) {
            return who == null ? List.of() : List.of(who);
        }
        if (spec.equals("@others")) {
            if (who == null) {
                return all;
            }
            List<ServerPlayer> out = new ArrayList<>(all);
            out.remove(who);
            return out;
        }
        if (spec.equals("@random")) {
            return all.isEmpty() ? all : List.of(all.get(arena.rng().nextInt(all.size())));
        }
        if (spec.equals("@nearest")) {
            if (who == null) {
                return all;
            }
            ServerPlayer best = null;
            double bestDist = Double.MAX_VALUE;
            for (ServerPlayer p : all) {
                if (p == who) {
                    continue;
                }
                double d = p.distanceToSqr(who);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
            return best == null ? List.of() : List.of(best);
        }
        if (spec.startsWith("@team:")) {
            return arena.teams().membersOf(spec.substring(6), all);
        }
        if (spec.startsWith("@tagged:")) {
            String tag = spec.substring(8);
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : all) {
                if (p.getTags().contains(tag)) {
                    out.add(p);
                }
            }
            return out;
        }
        // An unrecognised selector means everybody, the same as leaving it off:
        // a map written for a later engine keeps running on this one.
        return all;
    }

    private static void forEach(EngineArena arena, ServerPlayer who, JsonElement src,
                                java.util.function.Consumer<ServerPlayer> fn) {
        for (ServerPlayer p : targets(arena, who, src)) {
            fn.accept(p);
        }
    }

    /**
     * A number, which may be a sum.
     *
     * <p>A JSON number is taken as written. A JSON <em>string</em> goes to
     * {@link Expr}, so {@code "to": "{var:kills} * 10"} means what it looks like
     * it means. Every numeric field in every action reads through here, so there
     * is no list of which ones accept arithmetic - they all do.
     */
    private static int num(JsonObject o, String key, int fallback,
                           EngineArena arena, ServerPlayer who) {
        if (o == null || !o.has(key)) {
            return fallback;
        }
        JsonElement el = o.get(key);
        if (!el.isJsonPrimitive()) {
            return fallback;
        }
        var prim = el.getAsJsonPrimitive();
        if (prim.isNumber()) {
            return prim.getAsInt();
        }
        if (prim.isString()) {
            return Expr.eval(prim.getAsString(), name -> lookup(arena, who, name), fallback);
        }
        return fallback;
    }

    /** Resolves a placeholder inside an expression to an integer. */
    private static int lookup(EngineArena arena, ServerPlayer who, String name) {
        if (arena == null) {
            return 0;
        }
        String n = name.toLowerCase(Locale.ROOT);
        int colon = n.indexOf(':');
        String head = colon < 0 ? n : n.substring(0, colon);
        String arg = colon < 0 ? "" : n.substring(colon + 1);
        try {
            return Integer.parseInt(placeholder(arena, who, head, arg.isEmpty() ? null : arg));
        } catch (NumberFormatException e) {
            // {player} and {team} are words, not numbers. Nought is the least
            // surprising thing for arithmetic to do with them.
            return 0;
        }
    }

    private static void title(EngineArena arena, ServerPlayer who, JsonElement body) {
        String main = body.isJsonObject() ? str(body.getAsJsonObject(), "main", "") : asText(body);
        String sub = body.isJsonObject() ? str(body.getAsJsonObject(), "sub", "") : "";
        forEach(arena, who, body, p -> {
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
        forEach(arena, who, body, p -> level.playSound(null, p.blockPosition(),
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
        int ticks = Math.max(1, Math.min(20 * 60 * 60, num(o, "seconds", 10, arena, who) * 20));
        int amp = Math.max(0, Math.min(9, num(o, "amp", 0, arena, who)));
        forEach(arena, who, body, p -> p.addEffect(new MobEffectInstance(holder.get(), ticks, amp, false, true)));
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
        int count = o != null ? Math.max(1, num(o, "count", 1, arena, who)) : 1;
        forEach(arena, who, body, p -> {
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

    /**
     * Moves people to a named place.
     *
     * <p>{@code teleport_to_spawn} was the only way the script could move
     * anybody, which meant the one destination a map could ever name was the one
     * the engine had already named. No checkpoints, no jail, no second stage, no
     * start line - and the last of those is a hole I put there myself an hour
     * ago, because a lobby that cannot move people to the start when the
     * countdown ends is half a lobby.
     *
     * <p>Regions are the destination because they are the only named places an
     * author has. Raw coordinates are accepted too, for the map that wants a spot
     * it never needed to name.
     */
    /**
     * Writes something the world will still know tomorrow.
     *
     * <p>Per-player saved variables are written for <em>everyone the action
     * applies to</em>, the same as every other per-player action - a rule that
     * says "you have escaped once more" on a squad win should say it about the
     * squad, not about whoever happened to trip the trigger.
     */
    private static void savedVar(EngineArena arena, ServerLevel level, ServerPlayer who,
                                 JsonElement body, boolean perPlayer, boolean absolute) {
        if (!body.isJsonObject() || level.getServer() == null) {
            return;
        }
        JsonObject o = body.getAsJsonObject();
        String name = str(o, "name", "");
        if (name.isEmpty()) {
            trace(arena, "§csaved var §7— no name given");
            return;
        }
        int value = num(o, "value", absolute ? 0 : 1, arena, who);
        boolean global = o.has("global") && o.get("global").getAsBoolean();
        SavedVars saved = SavedVars.get(level.getServer());
        String rules = arena.rulesetId();
        if (!perPlayer) {
            if (absolute) {
                saved.set(rules, name, value, global);
            } else {
                saved.add(rules, name, value, global);
            }
            return;
        }
        forEach(arena, who, body, p -> {
            if (absolute) {
                saved.set(p.getUUID(), rules, name, value, global);
            } else {
                saved.add(p.getUUID(), rules, name, value, global);
            }
        });
    }

    private static void teleport(EngineArena arena, ServerLevel level, ServerPlayer who, JsonElement body) {
        JsonObject o = body.isJsonObject() ? body.getAsJsonObject() : null;
        String region = o != null ? str(o, "region", "") : asText(body);
        net.minecraft.core.BlockPos to = null;
        if (!region.isEmpty()) {
            to = arena.regionPos(region);
            if (to == null) {
                // A destination that does not exist is the exact shape of bug
                // this engine keeps producing: it would silently do nothing
                // forever. Say so, to anybody tracing.
                trace(arena, "§cteleport §7— no region called §f" + region);
                return;
            }
        } else if (o != null && o.has("x") && o.has("y") && o.has("z")) {
            to = new net.minecraft.core.BlockPos(
                    intOf(o, "x", 0), intOf(o, "y", 0), intOf(o, "z", 0));
        }
        if (to == null) {
            return;
        }
        final net.minecraft.core.BlockPos at = to;
        forEach(arena, who, body, p -> p.teleportTo(level,
                at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5,
                java.util.Set.of(), p.getYRot(), 0.0F));
    }

    private static void give(EngineArena arena, ServerPlayer who, JsonElement body) {
        JsonObject o = body.isJsonObject() ? body.getAsJsonObject() : null;
        String id = o != null ? str(o, "id", "") : asText(body);
        ResourceLocation rl = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return;
        }
        int count = o != null ? Math.max(1, Math.min(64, num(o, "count", 1, arena, who))) : 1;
        forEach(arena, who, body, p -> {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl), count);
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
        });
    }

    private static void award(EngineArena arena, ServerPlayer who, JsonElement body) {
        JsonObject o = body.isJsonObject() ? body.getAsJsonObject() : null;
        int amount = o != null ? num(o, "amount", 0, arena, who) : asInt(body);
        Currency c = Currency.byId(o != null ? str(o, "currency", null) : null);
        forEach(arena, who, body, p -> c.award(p, amount));
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
        int count = Math.max(1, Math.min(24, num(o, "count", 1, arena, null)));
        for (int i = 0; i < count; i++) {
            Entity e = type.get().create(level);
            if (!(e instanceof Mob mob)) {
                return;
            }
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
            int health = num(o, "health", 0, arena, null);
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
            case "saved_var":
                return arena.level().getServer() == null ? "0" : String.valueOf(
                        SavedVars.get(arena.level().getServer()).get(arena.rulesetId(), a, false));
            case "my_saved_var":
                return viewer == null || arena.level().getServer() == null ? "0" : String.valueOf(
                        SavedVars.get(arena.level().getServer())
                                .get(viewer.getUUID(), arena.rulesetId(), a, false));
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
            case "timer":
                return String.valueOf(arena.timerSeconds(a.toLowerCase(Locale.ROOT)));
            case "timer_clock": {
                int t = arena.timerSeconds(a.toLowerCase(Locale.ROOT));
                return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60);
            }
            case "amount":
                return "0";
            case "rule":
                return String.valueOf((int) arena.ruleNow(a.toLowerCase(Locale.ROOT)));
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
