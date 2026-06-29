package com.grapplinghook;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple properties-file config for the Grappling Hook, written to
 * {@code config/grapplinghook.properties}. Loaded once at startup; edit the
 * file and restart to change values.
 */
public final class GrappleConfig {
    private static final Logger LOG = LoggerFactory.getLogger("grapplinghook");

    /** Ticks of charge to reach full power (20 ticks = 1 second; 40 = 2s). */
    public static int chargeTicks = 40;
    /** Reach of a quick tap, in blocks. */
    public static double minRange = 8.0;
    /** Reach of a full 2-second charge, in blocks. */
    public static double maxRange = 40.0;
    /** How hard pull speed scales with distance. */
    public static double pullSpeedFactor = 0.30;
    /** Hard cap on launch speed. */
    public static double maxPullSpeed = 3.0;
    /** Extra upward boost added to every pull. */
    public static double upwardBoost = 0.30;
    /** Cooldown after a grapple, in ticks. */
    public static int cooldownTicks = 20;

    private GrappleConfig() {}

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("grapplinghook.properties");
        Properties p = new Properties();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                p.load(r);
            } catch (IOException e) {
                LOG.warn("[grapplinghook] Could not read config; using defaults", e);
            }
        }
        chargeTicks     = parseInt(p, "chargeTicks", chargeTicks);
        cooldownTicks   = parseInt(p, "cooldownTicks", cooldownTicks);
        minRange        = parseDouble(p, "minRange", minRange);
        maxRange        = parseDouble(p, "maxRange", maxRange);
        pullSpeedFactor = parseDouble(p, "pullSpeedFactor", pullSpeedFactor);
        maxPullSpeed    = parseDouble(p, "maxPullSpeed", maxPullSpeed);
        upwardBoost     = parseDouble(p, "upwardBoost", upwardBoost);
        save(path);
    }

    private static void save(Path path) {
        Properties p = new Properties();
        p.setProperty("chargeTicks", Integer.toString(chargeTicks));
        p.setProperty("cooldownTicks", Integer.toString(cooldownTicks));
        p.setProperty("minRange", Double.toString(minRange));
        p.setProperty("maxRange", Double.toString(maxRange));
        p.setProperty("pullSpeedFactor", Double.toString(pullSpeedFactor));
        p.setProperty("maxPullSpeed", Double.toString(maxPullSpeed));
        p.setProperty("upwardBoost", Double.toString(upwardBoost));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer w = Files.newBufferedWriter(path)) {
                p.store(w, "Grappling Hook config — chargeTicks: ticks to reach full power (20=1s); "
                        + "minRange: tap distance; maxRange: full-charge distance.");
            }
        } catch (IOException e) {
            LOG.warn("[grapplinghook] Could not write config", e);
        }
    }

    private static int parseInt(Properties p, String key, int def) {
        try {
            return p.containsKey(key) ? Integer.parseInt(p.getProperty(key).trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(Properties p, String key, double def) {
        try {
            return p.containsKey(key) ? Double.parseDouble(p.getProperty(key).trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
