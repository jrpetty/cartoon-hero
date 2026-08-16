package com.jrpetty.aztecabyss.engine;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/**
 * Something to do other than not die.
 *
 * <p>Every map the engine could build until now had the same verb. You survive,
 * the rounds get harder, eventually you stop - and two maps with different walls
 * are still the same game. An objective is where genuinely different maps come
 * from, because it changes what the horde is <em>interrupting</em>.
 *
 * <p>Three kinds, chosen because they cover the three shapes a survival objective
 * can take and because each one asks a different question of the map:
 *
 * <ul>
 *   <li><b>defend</b> - something has health and the horde attacks it. Asks
 *       whether you can be in two places, and makes a map's geometry a defence
 *       problem rather than an escape one.
 *   <li><b>hold</b> - stand in a zone for a stretch of time. Asks you to stay
 *       still, which is the exact opposite of what survival teaches, so it turns
 *       a good kiting spot into a trap you have chosen.
 *   <li><b>collect</b> - bring N of something to the marker. Asks you to cross
 *       the map repeatedly while it gets worse.
 * </ul>
 *
 * <p>Written on a sign like everything else:
 * <pre>
 *   [Objective]
 *   defend hp=600
 *   fail=end
 * </pre>
 */
public final class Objective {

    public enum Kind {
        DEFEND, HOLD, COLLECT, NONE
    }

    private final Kind kind;
    private final Marker marker;
    private final int target;
    private final boolean failEndsRun;

    private int progress;
    private float health;
    private boolean complete;
    private boolean failed;
    /** Counts ticks between bites, so damage is a rate per second rather than per tick. */
    private int gnaw;

    public Objective(Marker marker) {
        this.marker = marker;
        String word = marker.arg("type", marker.arg("value", "")).toLowerCase(Locale.ROOT);
        this.kind = switch (word) {
            case "defend" -> Kind.DEFEND;
            case "hold" -> Kind.HOLD;
            case "collect" -> Kind.COLLECT;
            default -> Kind.NONE;
        };
        this.target = switch (kind) {
            case HOLD -> Math.max(1, marker.intArg("seconds", 60)) * 20;
            case COLLECT -> Math.max(1, marker.intArg("count", 8));
            default -> 0;
        };
        this.health = Math.max(1, marker.intArg("hp", 600));
        // "fail=end" is the interesting setting and not the default. An objective
        // that ends the run when it breaks is a hard rule; one that merely stops
        // paying out lets a map have a side task without turning every map into a
        // single point of failure.
        this.failEndsRun = marker.arg("fail", "").equalsIgnoreCase("end");
    }

    public boolean valid() {
        return kind != Kind.NONE;
    }

    public Kind kind() {
        return kind;
    }

    public boolean complete() {
        return complete;
    }

    public boolean failed() {
        return failed;
    }

    public boolean failEndsRun() {
        return failEndsRun;
    }

    public Marker marker() {
        return marker;
    }

    /**
     * One tick of progress.
     *
     * @return true if the objective changed state this tick
     */
    public boolean tick(ServerLevel level, List<ServerPlayer> present) {
        if (complete || failed || present.isEmpty()) {
            return false;
        }
        return switch (kind) {
            case DEFEND -> tickDefend(level);
            case HOLD -> tickHold(present);
            case COLLECT -> false; // driven by handIn, not by time
            default -> false;
        };
    }

    /**
     * The horde chews on it whenever something is stood next to it.
     *
     * <p>Once a second, not once a tick. Per-tick damage reads fine in isolation
     * and is catastrophic in practice: ten mobs at a quarter each is fifty health
     * a second, so the 600 the author asked for lasts twelve seconds and every
     * defend objective in every map is unwinnable. The rate an author is thinking
     * in when they write {@code hp=600} is per second, so that is the rate.
     */
    private boolean tickDefend(ServerLevel level) {
        if (++gnaw < 20) {
            return false;
        }
        gnaw = 0;
        double radius = Math.max(2, marker.intArg("radius", 4));
        var box = new net.minecraft.world.phys.AABB(marker.pos()).inflate(radius);
        int biting = 0;
        for (net.minecraft.world.entity.Mob m
                : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                        e -> e.getPersistentData().getBoolean("aztecabyss_engine_mob") && e.isAlive())) {
            biting++;
        }
        if (biting == 0) {
            return false;
        }
        int bite = Math.max(1, Math.round(biting * 0.25f));
        health -= biting * 0.25f;
        // The thing the whole map is about was losing hit points in silence.
        // Nothing could sound an alarm, darken the sky at a quarter left, or
        // pay the players who drove the biters off - the drama of a defend map
        // was entirely in a number nobody was told about.
        EngineArena arena = EngineArena.active();
        if (arena != null) {
            Script.fireAmount(arena, level, arena.rulesetId(), "objective_damaged",
                    null, String.valueOf(percent()), bite);
        }
        if (health <= 0) {
            failed = true;
            return true;
        }
        return false;
    }

    /** How much of the objective is left, nought to a hundred. */
    public int percent() {
        int max = Math.max(1, marker.intArg("hp", 600));
        return Math.max(0, Math.min(100, Math.round(health * 100.0f / max)));
    }

    /** Someone has to be stood in it, and stay stood in it. */
    private boolean tickHold(List<ServerPlayer> present) {
        double radius = Math.max(1, marker.intArg("radius", 4));
        boolean anyone = false;
        for (ServerPlayer p : present) {
            if (p.blockPosition().distSqr(marker.pos()) <= radius * radius) {
                anyone = true;
                break;
            }
        }
        if (!anyone) {
            // Progress is kept rather than reset. Being driven off a point by the
            // thing that is trying to drive you off it should cost you time, not
            // the whole attempt.
            return false;
        }
        progress++;
        if (progress >= target) {
            complete = true;
            return true;
        }
        return false;
    }

    /** Hands in carried items at the marker. */
    public boolean handIn(ServerPlayer player) {
        if (kind != Kind.COLLECT || complete || failed) {
            return false;
        }
        String id = marker.arg("item", "minecraft:gold_ingot");
        var rl = net.minecraft.resources.ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
            return false;
        }
        var wanted = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        int taken = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && progress + taken < target; i++) {
            var stack = player.getInventory().getItem(i);
            if (!stack.is(wanted)) {
                continue;
            }
            int can = Math.min(stack.getCount(), target - progress - taken);
            stack.shrink(can);
            taken += can;
        }
        if (taken == 0) {
            return false;
        }
        progress += taken;
        if (progress >= target) {
            complete = true;
        }
        return true;
    }

    /** A line for the round bar, or empty if there is nothing to say. */
    public String hud() {
        return switch (kind) {
            case DEFEND -> failed ? "§4objective lost"
                    : " §8| §6◈ §f" + (int) health;
            case HOLD -> complete ? "§aheld"
                    : " §8| §6◈ §f" + (progress * 100 / Math.max(1, target)) + "%";
            case COLLECT -> complete ? "§adelivered"
                    : " §8| §6◈ §f" + progress + "/" + target;
            default -> "";
        };
    }

    public Component describe() {
        return Component.literal(switch (kind) {
            case DEFEND -> "§6Defend it. §7" + (int) health + " left.";
            case HOLD -> "§6Hold the ground. §7" + (target / 20) + " seconds.";
            case COLLECT -> "§6Bring " + target + " to the marker.";
            default -> "§7No objective.";
        });
    }
}
