package com.jrpetty.aztecabyss.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public final List<MobEntry> mobs;

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
        this.mobs = List.copyOf(b.mobs);
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
        List<MobEntry> mobs = new ArrayList<>();
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

        JsonObject rounds = obj(root, "rounds");
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
