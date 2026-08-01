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

    public static void clear() {
        BY_RULESET.clear();
    }

    /** Pulls the {@code script} array out of a ruleset file. */
    public static void load(String rulesetId, JsonObject root) {
        if (!root.has("script") || !root.get("script").isJsonArray()) {
            return;
        }
        List<Rule> rules = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("script")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String on = str(o, "on", "").toLowerCase(Locale.ROOT);
            if (on.isEmpty() || !o.has("do") || !o.get("do").isJsonArray()) {
                continue;
            }
            rules.add(new Rule(on,
                    o.has("when") && o.get("when").isJsonObject() ? o.getAsJsonObject("when") : null,
                    o.getAsJsonArray("do")));
        }
        if (!rules.isEmpty()) {
            BY_RULESET.put(rulesetId, rules);
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
    public static void fire(EngineArena arena, ServerLevel level, String rulesetId,
                            String event, ServerPlayer who) {
        List<Rule> rules = BY_RULESET.get(rulesetId);
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (Rule rule : rules) {
            if (!rule.event().equals(event) || !matches(rule.when(), arena)) {
                continue;
            }
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
    private static boolean matches(JsonObject when, EngineArena arena) {
        if (when == null) {
            return true;
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
        if (when.has("area_open")) {
            return arena.isAreaOpen(str(when, "area_open", ""));
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private static void run(EngineArena arena, ServerLevel level, JsonObject action, ServerPlayer who) {
        for (String key : action.keySet()) {
            JsonElement body = action.get(key);
            switch (key.toLowerCase(Locale.ROOT)) {
                case "message" -> forEach(arena, who, p ->
                        p.displayClientMessage(Component.literal(asText(body)), false));
                case "actionbar" -> forEach(arena, who, p ->
                        p.displayClientMessage(Component.literal(asText(body)), true));
                case "title" -> title(arena, who, body);
                case "sound" -> sound(level, arena, who, body);
                case "effect" -> effect(arena, who, body);
                case "give" -> give(arena, who, body);
                case "spawn" -> spawn(arena, level, body);
                case "award" -> award(arena, who, body);
                case "open_area" -> arena.openArea(asText(body));
                case "set_block" -> setBlock(level, body);
                case "end_run" -> EngineArena.stop(true);
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
                    Component.literal(main)));
            if (!sub.isEmpty()) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                        Component.literal(sub)));
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
