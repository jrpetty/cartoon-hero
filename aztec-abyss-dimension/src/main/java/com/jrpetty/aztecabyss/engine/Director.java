package com.jrpetty.aztecabyss.engine;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Difficulty that watches the players rather than the clock.
 *
 * <p>Every scaling knob in this engine so far answers the same question - what
 * round is it? - and that produces a curve that is right for exactly one standard
 * of play. A squad that is better than the curve coasts; a squad that is worse hits
 * a wall at the same round every time and never sees the rest of the map. The round
 * number is a proxy for difficulty, and not a very good one.
 *
 * <p>Borrowed, therefore, from Left 4 Dead: measure how the run is <em>actually
 * going</em> and steer toward a target level of pressure. When the squad is
 * comfortable the horde arrives faster and in greater numbers; when they are being
 * taken apart it eases off and lets them recover. The map stops being a fixed test
 * and starts being a fight that stays interesting for whoever is playing it.
 *
 * <p>Two things it deliberately does not do. It never touches mob health or damage
 * - only <em>pacing</em> - because silently making a zombie weaker teaches players
 * that their weapons are unreliable, whereas sending fewer of them for a moment
 * reads as a lull. And it is invisible in play: there is no bar for it, because a
 * difficulty system you can watch is one you can game.
 */
public final class Director {

    /** How fast intensity follows reality. Low, so one bad second is not a crisis. */
    private static final float SMOOTHING = 0.15f;
    /** How hard a gap between actual and target pressure pushes the pace. */
    private static final float GAIN = 1.5f;

    private final boolean enabled;
    private final float target;
    private final float minPace;
    private final float maxPace;

    /** 0 = untouched, 1 = about to die. Smoothed over time. */
    private float intensity = 0.0f;
    private int downsThisRound = 0;

    public Director(Ruleset rules) {
        this.enabled = rules.directorEnabled;
        this.target = clamp(rules.directorTarget, 0.05f, 0.95f);
        this.minPace = clamp(rules.directorMinPace, 0.2f, 1.0f);
        this.maxPace = clamp(rules.directorMaxPace, 1.0f, 4.0f);
    }

    public boolean enabled() {
        return enabled;
    }

    public float intensity() {
        return intensity;
    }

    /**
     * Re-reads the squad's condition. Called about once a second.
     *
     * <p>Health fraction is the signal, because it is the one thing that reflects
     * both how hard the last minute was and how much room there is for the next
     * one. The lowest player counts double: a squad is in trouble when any one of
     * them is, not when the average is.
     */
    public void sample(List<ServerPlayer> players) {
        if (!enabled || players.isEmpty()) {
            return;
        }
        float total = 0.0f;
        float worst = 1.0f;
        for (ServerPlayer p : players) {
            float max = Math.max(1.0f, p.getMaxHealth());
            float frac = Math.max(0.0f, Math.min(1.0f, p.getHealth() / max));
            total += frac;
            worst = Math.min(worst, frac);
        }
        float average = total / players.size();
        float health = (average + worst) / 2.0f;
        float raw = 1.0f - health;
        // Being downed this round counts as pressure even after healing back up,
        // so a near-death that got patched does not read as a comfortable round.
        raw = Math.min(1.0f, raw + downsThisRound * 0.15f);
        intensity += (raw - intensity) * SMOOTHING;
    }

    public void onDown() {
        downsThisRound++;
    }

    public void onRoundStart() {
        downsThisRound = 0;
    }

    /**
     * How much faster or slower the horde should arrive right now.
     *
     * <p>1.0 means the squad is exactly as pressured as the map wants them. Above
     * 1 they are coasting and it leans in; below 1 they are struggling and it
     * backs off. Clamped both ways so the Director can flavour a fight but never
     * replace the ruleset's judgement of how hard the map is.
     */
    public float pace() {
        if (!enabled) {
            return 1.0f;
        }
        return clamp(1.0f + (target - intensity) * GAIN, minPace, maxPace);
    }

    /** A one-line readout, for authors tuning a map rather than for players. */
    public String describe() {
        if (!enabled) {
            return "§7Director: §8off";
        }
        return String.format(java.util.Locale.ROOT,
                "§7Director: pressure §f%.0f%%§7 (target §f%.0f%%§7), pace §f×%.2f",
                intensity * 100.0f, target * 100.0f, pace());
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
