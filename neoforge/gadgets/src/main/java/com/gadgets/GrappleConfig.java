package com.gadgets;

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
 * Properties-file config for the Grappling Hook, written to
 * {@code config/gadgets-grapple.properties}. Loaded once at startup.
 */
public final class GrappleConfig {
    private static final Logger LOG = LoggerFactory.getLogger("gadgets");

    // --- charge / reach ---
    public static int chargeTicks = 40;
    public static double minRange = 8.0;
    public static double maxRange = 40.0;
    public static int cooldownTicks = 20;

    // --- block grapple: directional launch ---
    public static double minLaunchSpeed = 0.8;
    public static double maxLaunchSpeed = 2.0;
    public static double upwardBoost = 0.35;

    // --- entity grapple ---
    public static boolean grappleEntities = true;
    public static double entityPullFactor = 0.30;
    public static double entityMaxPullSpeed = 2.5;

    private GrappleConfig() {}

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("gadgets-grapple.properties");
        Properties p = new Properties();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                p.load(r);
            } catch (IOException e) {
                LOG.warn("[gadgets] Could not read config; using defaults", e);
            }
        }
        chargeTicks        = parseInt(p, "chargeTicks", chargeTicks);
        minRange           = parseDouble(p, "minRange", minRange);
        maxRange           = parseDouble(p, "maxRange", maxRange);
        cooldownTicks      = parseInt(p, "cooldownTicks", cooldownTicks);
        minLaunchSpeed     = parseDouble(p, "minLaunchSpeed", minLaunchSpeed);
        maxLaunchSpeed     = parseDouble(p, "maxLaunchSpeed", maxLaunchSpeed);
        upwardBoost        = parseDouble(p, "upwardBoost", upwardBoost);
        grappleEntities    = parseBoolean(p, "grappleEntities", grappleEntities);
        entityPullFactor   = parseDouble(p, "entityPullFactor", entityPullFactor);
        entityMaxPullSpeed = parseDouble(p, "entityMaxPullSpeed", entityMaxPullSpeed);
        save(path);
    }

    private static void save(Path path) {
        Properties p = new Properties();
        p.setProperty("chargeTicks", Integer.toString(chargeTicks));
        p.setProperty("minRange", Double.toString(minRange));
        p.setProperty("maxRange", Double.toString(maxRange));
        p.setProperty("cooldownTicks", Integer.toString(cooldownTicks));
        p.setProperty("minLaunchSpeed", Double.toString(minLaunchSpeed));
        p.setProperty("maxLaunchSpeed", Double.toString(maxLaunchSpeed));
        p.setProperty("upwardBoost", Double.toString(upwardBoost));
        p.setProperty("grappleEntities", Boolean.toString(grappleEntities));
        p.setProperty("entityPullFactor", Double.toString(entityPullFactor));
        p.setProperty("entityMaxPullSpeed", Double.toString(entityMaxPullSpeed));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer w = Files.newBufferedWriter(path)) {
                p.store(w, "Grappling Hook config. Block grapple = a single charge-scaled launch toward the "
                        + "aimed point (no reeling); aiming at a mob (grappleEntities) yanks it toward you.");
            }
        } catch (IOException e) {
            LOG.warn("[gadgets] Could not write config", e);
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

    private static boolean parseBoolean(Properties p, String key, boolean def) {
        return p.containsKey(key) ? Boolean.parseBoolean(p.getProperty(key).trim()) : def;
    }
}
