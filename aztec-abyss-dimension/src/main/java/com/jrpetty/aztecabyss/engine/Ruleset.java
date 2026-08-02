package com.jrpetty.aztecabyss.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every number a round-survival game has, in one object, loaded from JSON.
 *
 * <p>These values currently live as {@code private static final} fields spread
 * across the round system. That is the actual problem with the mod as it stands:
 * changing how hard a map is means changing Java, which means a build, which means
 * me. Moving them here is what makes the difference between a mod with maps in it
 * and an engine you can tune.
 *
 * <p>Two principles run through the parsing:
 *
 * <p><b>Everything has a default.</b> A ruleset that declares nothing but a name is
 * valid and plays exactly like the stock game. An author who only wants tougher
 * zombies writes four lines, not four hundred - and a field added to the engine
 * later cannot break a ruleset written before it existed.
 *
 * <p><b>Everything is clamped.</b> Data is allowed to be wrong. A typo that asks
 * for a million-health zombie or two thousand simultaneous mobs should produce a
 * hard map, not a dead server, so limits are applied here rather than trusted to
 * the author.
 */
public final class Ruleset {

    /** Hard ceilings. Content can ask for anything; it gets these. */
    private static final double MAX_HEALTH = 1024.0;
    private static final double MAX_DAMAGE = 256.0;
    private static final double MAX_SPEED = 1.0;
    private static final int MAX_CONCURRENT = 400;

    public final String id;
    public final boolean endless;
    public final int finalRound;
    public final int baseCount;
    public final int perRound;
    public final int concurrentCap;

    public final double healthPerRound;
    public final int softenAfter;
    public final double healthExponent;
    public final double damagePerRound;
    public final double damageCap;

    public final int breatherStart;
    public final int breatherMin;
    public final int breatherTightenBy;

    public final boolean economyEnabled;
    public final String defaultCurrency;
    public final int pointsHit;
    public final int pointsKill;
    public final int pointsHeadshot;
    public final boolean stripInventory;

    /** One kill in this many leaves a drop. Zero switches power-ups off. */
    public final int powerupChance;

    /**
     * Going down instead of dying, and being picked back up.
     *
     * <p>Off by default because it changes what a run <em>is</em>, and a ruleset
     * that never mentions it should play the way it always did.
     */
    public final boolean downedEnabled;
    public final int bleedoutSeconds;
    public final int reviveSeconds;
    public final double reviveRange;
    /** Whether a lone player can go down. Off means solo death stays final. */
    public final boolean downedSolo;

    public final boolean directorEnabled;
    public final float directorTarget;
    public final float directorMinPace;
    public final float directorMaxPace;

    public final List<MobEntry> mobs;

    /**
     * Rounds that break the pattern.
     *
     * <p>Scaling alone makes round thirty into round twenty with bigger numbers.
     * A round that is <em>all runners</em>, or all brutes, or has no drops in it,
     * is remembered - and it costs nothing but a filter over the table the author
     * already wrote.
     */
    public final List<SpecialRound> specials;

    /**
     * Named item pools, for every machine that hands something out.
     *
     * <p>One block in the file feeds the Box, supply caches and the starting
     * loadout, because they were three hard-coded arrays solving the same problem.
     * A pool an author has not defined falls back to the built-in one, so a
     * ruleset that says nothing about pools plays exactly as it did.
     */
    public final Map<String, ItemPool> pools;

    /** One recurring variant round. */
    public record SpecialRound(int every, String role, String mobId,
                               boolean noPowerups, String title, String subtitle) {

        public boolean appliesTo(int round) {
            return every > 0 && round > 0 && round % every == 0;
        }

        /** Whether a mob entry is allowed to turn up during this round. */
        public boolean allows(MobEntry entry) {
            if (!role.isEmpty() && !role.equalsIgnoreCase(entry.role())) {
                return false;
            }
            return mobId.isEmpty() || mobId.equalsIgnoreCase(entry.entityId());
        }
    }

    /**
     * Keys the parser did not recognise.
     *
     * <p>Lenient parsing - every field defaulting rather than failing - is what
     * lets a four-line ruleset work and stops a new engine field breaking an old
     * file. It also silently swallows typos: write {@code basecount} instead of
     * {@code base_count} and the file looks perfect and plays as though you had
     * written nothing. That is the worst failure mode a config format has, because
     * there is no symptom to chase.
     *
     * <p>So unrecognised keys are collected rather than ignored, and reported by
     * {@code /arena rules}. Still not an error - a map written for a later engine
     * must keep working - but never invisible.
     */
    public final List<String> warnings;

    /** One kind of thing that can turn up in a wave. */
    public record MobEntry(String entityId, int weight, int fromRound, String role,
                           double maxHealth, double speed, double attackDamage,
                           String mainHand, String head) {
    }

    private Ruleset(Builder b) {
        this.id = b.id;
        this.endless = b.endless;
        this.finalRound = b.finalRound;
        this.baseCount = b.baseCount;
        this.perRound = b.perRound;
        this.concurrentCap = b.concurrentCap;
        this.healthPerRound = b.healthPerRound;
        this.softenAfter = b.softenAfter;
        this.healthExponent = b.healthExponent;
        this.damagePerRound = b.damagePerRound;
        this.damageCap = b.damageCap;
        this.breatherStart = b.breatherStart;
        this.breatherMin = b.breatherMin;
        this.breatherTightenBy = b.breatherTightenBy;
        this.economyEnabled = b.economyEnabled;
        this.defaultCurrency = b.defaultCurrency;
        this.pointsHit = b.pointsHit;
        this.pointsKill = b.pointsKill;
        this.pointsHeadshot = b.pointsHeadshot;
        this.stripInventory = b.stripInventory;
        this.powerupChance = b.powerupChance;
        this.downedEnabled = b.downedEnabled;
        this.bleedoutSeconds = b.bleedoutSeconds;
        this.reviveSeconds = b.reviveSeconds;
        this.reviveRange = b.reviveRange;
        this.downedSolo = b.downedSolo;
        this.specials = List.copyOf(b.specials);
        this.pools = Map.copyOf(b.pools);
        this.directorEnabled = b.directorEnabled;
        this.directorTarget = b.directorTarget;
        this.directorMinPace = b.directorMinPace;
        this.directorMaxPace = b.directorMaxPace;
        this.mobs = List.copyOf(b.mobs);
        this.warnings = List.copyOf(b.warnings);
    }

    /** Flags any key in an object that the parser has no meaning for. */
    private static void checkKeys(JsonObject o, String section, List<String> out, String... known) {
        if (o == null) {
            return;
        }
        java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList(known));
        for (String key : o.keySet()) {
            // A leading underscore is the conventional way to write a note in a
            // JSON file that has nowhere else to put one, so those stay silent.
            if (!allowed.contains(key) && !key.startsWith("_")) {
                out.add(section + "." + key);
            }
        }
    }

    private static final class Builder {
        String id = "default";
        boolean endless = false;
        int finalRound = 20;
        int baseCount = 6;
        int perRound = 4;
        int concurrentCap = 120;
        double healthPerRound = 0.18;
        int softenAfter = 20;
        double healthExponent = 1.045;
        double damagePerRound = 0.14;
        double damageCap = 8.0;
        int breatherStart = 200;
        int breatherMin = 60;
        int breatherTightenBy = 40;
        boolean economyEnabled = false;
        String defaultCurrency = "points";
        int pointsHit = 10;
        int pointsKill = 50;
        int pointsHeadshot = 25;
        boolean stripInventory = false;
        int powerupChance = 0;
        boolean downedEnabled = false;
        int bleedoutSeconds = 30;
        int reviveSeconds = 5;
        double reviveRange = 3.0;
        boolean downedSolo = false;
        List<SpecialRound> specials = new ArrayList<>();
        Map<String, ItemPool> pools = new LinkedHashMap<>();
        boolean directorEnabled = false;
        float directorTarget = 0.55f;
        float directorMinPace = 0.5f;
        float directorMaxPace = 2.0f;
        List<MobEntry> mobs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
    }

    /** The stock game, for a map that names no ruleset at all. */
    public static Ruleset defaults(String id) {
        Builder b = new Builder();
        b.id = id;
        return new Ruleset(b);
    }

    /**
     * Reads a ruleset. Never throws: anything unreadable falls back to the stock
     * value for that field alone, so one bad line costs one setting rather than
     * the whole file.
     */
    public static Ruleset parse(String id, JsonObject root) {
        Builder b = new Builder();
        b.id = id;

        checkKeys(root, "", b.warnings,
                "rounds", "economy", "mobs", "currencies", "director", "script", "powerups",
                "downed", "special_rounds", "pools");
        checkKeys(obj(root, "downed"), "downed", b.warnings,
                "enabled", "bleedout_seconds", "revive_seconds", "revive_range", "solo");
        checkKeys(obj(root, "powerups"), "powerups", b.warnings, "one_in");

        JsonObject rounds = obj(root, "rounds");
        checkKeys(rounds, "rounds", b.warnings,
                "mode", "final_round", "base_count", "per_round", "concurrent_cap",
                "health", "damage", "breather");
        checkKeys(obj(rounds == null ? root : rounds, "health"), "rounds.health", b.warnings,
                "per_round", "soften_after", "exponent");
        checkKeys(obj(rounds == null ? root : rounds, "damage"), "rounds.damage", b.warnings,
                "per_round", "cap");
        checkKeys(obj(rounds == null ? root : rounds, "breather"), "rounds.breather", b.warnings,
                "start_ticks", "min_ticks", "tighten_by_round");
        checkKeys(obj(root, "economy"), "economy", b.warnings,
                "enabled", "currency", "hit", "kill", "headshot", "strip_inventory_on_entry");
        checkKeys(obj(root, "director"), "director", b.warnings,
                "enabled", "target_pressure", "min_pace", "max_pace");

        if (rounds != null) {
            b.endless = "endless".equalsIgnoreCase(str(rounds, "mode", "finite"));
            b.finalRound = clampInt(intOf(rounds, "final_round", b.finalRound), 0, 10000);
            b.baseCount = clampInt(intOf(rounds, "base_count", b.baseCount), 1, 200);
            b.perRound = clampInt(intOf(rounds, "per_round", b.perRound), 0, 200);
            b.concurrentCap = clampInt(intOf(rounds, "concurrent_cap", b.concurrentCap), 1, MAX_CONCURRENT);

            JsonObject health = obj(rounds, "health");
            if (health != null) {
                b.healthPerRound = clamp(dbl(health, "per_round", b.healthPerRound), 0.0, 5.0);
                b.softenAfter = clampInt(intOf(health, "soften_after", b.softenAfter), 1, 1000);
                b.healthExponent = clamp(dbl(health, "exponent", b.healthExponent), 1.0, 1.5);
            }
            JsonObject damage = obj(rounds, "damage");
            if (damage != null) {
                b.damagePerRound = clamp(dbl(damage, "per_round", b.damagePerRound), 0.0, 5.0);
                b.damageCap = clamp(dbl(damage, "cap", b.damageCap), 1.0, 64.0);
            }
            JsonObject breather = obj(rounds, "breather");
            if (breather != null) {
                b.breatherStart = clampInt(intOf(breather, "start_ticks", b.breatherStart), 0, 2400);
                b.breatherMin = clampInt(intOf(breather, "min_ticks", b.breatherMin), 0, 2400);
                b.breatherTightenBy = clampInt(intOf(breather, "tighten_by_round", b.breatherTightenBy), 1, 500);
            }
        }

        JsonObject economy = obj(root, "economy");
        if (economy != null) {
            b.economyEnabled = bool(economy, "enabled", b.economyEnabled);
            b.defaultCurrency = str(economy, "currency", b.defaultCurrency);
            b.pointsHit = clampInt(intOf(economy, "hit", b.pointsHit), 0, 100000);
            b.pointsKill = clampInt(intOf(economy, "kill", b.pointsKill), 0, 100000);
            b.pointsHeadshot = clampInt(intOf(economy, "headshot", b.pointsHeadshot), 0, 100000);
            b.stripInventory = bool(economy, "strip_inventory_on_entry", b.stripInventory);
        }

        JsonObject downed = obj(root, "downed");
        if (downed != null) {
            b.downedEnabled = bool(downed, "enabled", false);
            b.bleedoutSeconds = clampInt(intOf(downed, "bleedout_seconds", b.bleedoutSeconds), 3, 300);
            b.reviveSeconds = clampInt(intOf(downed, "revive_seconds", b.reviveSeconds), 1, 60);
            b.reviveRange = clamp(dbl(downed, "revive_range", b.reviveRange), 1.0, 8.0);
            b.downedSolo = bool(downed, "solo", false);
        }

        // Pools: one named weighted list per machine that hands something out.
        if (root.has("pools") && root.get("pools").isJsonObject()) {
            JsonObject poolsObj = root.getAsJsonObject("pools");
            for (String key : poolsObj.keySet()) {
                if (!poolsObj.get(key).isJsonArray()) {
                    b.warnings.add("pools." + key + " is not a list");
                    continue;
                }
                ItemPool pool = ItemPool.fromJson(key.toLowerCase(Locale.ROOT),
                        poolsObj.getAsJsonArray(key));
                if (pool.isEmpty()) {
                    // An empty pool is nearly always a misspelled item id, and it
                    // would otherwise show up as a Box that hands out stone swords.
                    b.warnings.add("pools." + key + " has no usable items in it");
                }
                b.pools.put(key.toLowerCase(Locale.ROOT), pool);
            }
        }

        if (root.has("special_rounds") && root.get("special_rounds").isJsonArray()) {
            for (var el : root.getAsJsonArray("special_rounds")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject sr = el.getAsJsonObject();
                int every = clampInt(intOf(sr, "every", 0), 0, 1000);
                if (every <= 0) {
                    continue;
                }
                b.specials.add(new SpecialRound(every,
                        str(sr, "role", ""),
                        str(sr, "mob", ""),
                        bool(sr, "no_powerups", false),
                        str(sr, "title", ""),
                        str(sr, "subtitle", "")));
            }
        }

        JsonObject powerups = obj(root, "powerups");
        if (powerups != null) {
            b.powerupChance = clampInt(intOf(powerups, "one_in", 0), 0, 100000);
        }

        JsonObject director = obj(root, "director");
        if (director != null) {
            b.directorEnabled = bool(director, "enabled", false);
            b.directorTarget = (float) clamp(dbl(director, "target_pressure", b.directorTarget), 0.05, 0.95);
            b.directorMinPace = (float) clamp(dbl(director, "min_pace", b.directorMinPace), 0.2, 1.0);
            b.directorMaxPace = (float) clamp(dbl(director, "max_pace", b.directorMaxPace), 1.0, 4.0);
        }

        JsonArray mobs = root.has("mobs") && root.get("mobs").isJsonArray()
                ? root.getAsJsonArray("mobs") : null;
        if (mobs != null) {
            for (var el : mobs) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject m = el.getAsJsonObject();
                String entity = str(m, "id", "");
                if (entity.isEmpty()) {
                    continue;
                }
                // Same trap as a mistyped spawner: an id nothing matches produces a
                // mob table entry that can never turn up, and the round just feels
                // thin for reasons nobody can point at.
                if (net.minecraft.world.entity.EntityType.byString(entity).isEmpty()) {
                    b.warnings.add("mobs: no entity called '" + entity + "'");
                    continue;
                }
                JsonObject attrs = obj(m, "attributes");
                JsonObject gear = obj(m, "equipment");
                b.mobs.add(new MobEntry(
                        entity,
                        clampInt(intOf(m, "weight", 10), 0, 10000),
                        clampInt(intOf(m, "from_round", 1), 1, 10000),
                        str(m, "role", "grunt").toLowerCase(Locale.ROOT),
                        attrs == null ? 20.0 : clamp(dbl(attrs, "max_health", 20.0), 1.0, MAX_HEALTH),
                        attrs == null ? 0.25 : clamp(dbl(attrs, "movement_speed", 0.25), 0.01, MAX_SPEED),
                        attrs == null ? 3.0 : clamp(dbl(attrs, "attack_damage", 3.0), 0.0, MAX_DAMAGE),
                        gear == null ? "" : str(gear, "mainhand", ""),
                        gear == null ? "" : str(gear, "head", "")));
            }
        }
        return new Ruleset(b);
    }

    /** How many mobs round {@code round} sends. */
    public int countFor(int round) {
        return Math.min(concurrentCap * 4, baseCount + perRound * Math.max(0, round - 1));
    }

    /**
     * The health multiplier at a given round.
     *
     * <p>Linear while a run is still going somewhere, exponential past the soften
     * point - which is what stops an endless mode becoming a stalemate where
     * nothing can kill you and you cannot kill anything either.
     */
    public double healthMultiplier(int round) {
        double linear = 1.0 + Math.min(round, softenAfter) * healthPerRound;
        return round <= softenAfter ? linear : linear * Math.pow(healthExponent, round - softenAfter);
    }

    public double damageMultiplier(int round) {
        return Math.min(1.0 + round * damagePerRound, damageCap);
    }

    /**
     * The special round in force at this round, or null.
     *
     * <p>Where several apply at once the rarest wins, and that rule is the whole
     * point of this method. Taking the first match instead looks harmless and is
     * not: every multiple of ten is also a multiple of five, so a file listing
     * {@code every: 5} above {@code every: 10} - which is the obvious order to
     * write them in, and the order the documentation used - gives the ten-round
     * special no round it can ever fire on. It would simply never happen, with
     * nothing in the file to suggest why.
     *
     * <p>Rarest-wins also matches what an author means. A round that comes up
     * every ten is being written as the bigger event than one that comes up
     * every five, so when they collide the bigger one is the one to keep.
     */
    public SpecialRound specialFor(int round) {
        SpecialRound best = null;
        for (SpecialRound sr : specials) {
            if (sr.appliesTo(round) && (best == null || sr.every() > best.every())) {
                best = sr;
            }
        }
        return best;
    }

    /** The gap between rounds, shrinking as a run goes on. */
    public int breatherFor(int round) {
        if (breatherStart <= breatherMin) {
            return breatherMin;
        }
        double t = Math.min(1.0, Math.max(0, round - 1) / (double) breatherTightenBy);
        return (int) Math.round(breatherStart - (breatherStart - breatherMin) * t);
    }

    // ------------------------------------------------------------------
    // Lenient JSON helpers
    // ------------------------------------------------------------------

    private static JsonObject obj(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
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

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        try {
            return o.has(key) ? o.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
