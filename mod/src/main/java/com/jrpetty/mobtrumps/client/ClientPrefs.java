package com.jrpetty.mobtrumps.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client-side UI preferences, edited from the collection book's Settings page
 * and saved to {@code config/mobtrumps-client.properties} so they survive a
 * restart. Everything here is presentation only — nothing affects the game
 * rules, so these are never sent to the server.
 */
public final class ClientPrefs {

    /** How big battle cards render: AUTO fits them to the window; the rest force a size. */
    public enum CardSize {
        AUTO("Auto"), SMALL("Small"), MEDIUM("Medium"), LARGE("Large");

        public final String label;

        CardSize(String label) {
            this.label = label;
        }

        public CardSize next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** How brightly the battle arena's felt is lit. */
    public enum Brightness {
        DIM("Dim", 0.78f), NORMAL("Normal", 1.0f), BRIGHT("Bright", 1.22f), VIVID("Vivid", 1.45f);

        public final String label;
        public final float factor;

        Brightness(String label, float factor) {
            this.label = label;
            this.factor = factor;
        }

        public Brightness next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final String FILE = "mobtrumps-client.properties";

    private static volatile CardSize cardSize = CardSize.AUTO;
    private static volatile Brightness brightness = Brightness.BRIGHT;
    private static volatile boolean killCounter = true;
    private static volatile boolean foilSheen = true;
    private static volatile boolean livePortraits = true;
    private static volatile boolean reducedMotion = false;
    private static volatile boolean battleHints = true;
    private static volatile boolean confirmLeave = true;
    private static boolean loaded;

    private ClientPrefs() {
    }

    // --- accessors ----------------------------------------------------------

    public static CardSize cardSize() {
        return cardSize;
    }

    public static Brightness brightness() {
        return brightness;
    }

    /** Show the "x12" hunted-count badge on book cards. */
    public static boolean killCounter() {
        return killCounter;
    }

    /** Animate the holographic rainbow sheen (off is calmer and cheaper). */
    public static boolean foilSheen() {
        return foilSheen;
    }

    /** Render live 3D mobs in card portraits. */
    public static boolean livePortraits() {
        return livePortraits;
    }

    /** Suppress card flights, pulses and shimmer. */
    public static boolean reducedMotion() {
        return reducedMotion;
    }

    /** Show the "click a stat / waiting for..." line in the battle dock. */
    public static boolean battleHints() {
        return battleHints;
    }

    /** Require a second click on Leave while a game is live. */
    public static boolean confirmLeave() {
        return confirmLeave;
    }

    // --- mutators (all persist immediately) ---------------------------------

    public static void cycleCardSize() {
        cardSize = cardSize.next();
        save();
    }

    public static void cycleBrightness() {
        brightness = brightness.next();
        save();
    }

    public static void toggle(String key) {
        switch (key) {
            case "kill_counter" -> killCounter = !killCounter;
            case "foil_sheen" -> foilSheen = !foilSheen;
            case "live_portraits" -> livePortraits = !livePortraits;
            case "reduced_motion" -> reducedMotion = !reducedMotion;
            case "battle_hints" -> battleHints = !battleHints;
            case "confirm_leave" -> confirmLeave = !confirmLeave;
            default -> {
                return;
            }
        }
        save();
    }

    public static boolean get(String key) {
        return switch (key) {
            case "kill_counter" -> killCounter;
            case "foil_sheen" -> foilSheen;
            case "live_portraits" -> livePortraits;
            case "reduced_motion" -> reducedMotion;
            case "battle_hints" -> battleHints;
            case "confirm_leave" -> confirmLeave;
            default -> false;
        };
    }

    /**
     * The card scale to use given the fit-to-window scale. AUTO returns the fit;
     * fixed sizes clamp to the fit so a card can never overflow the window.
     */
    public static float resolveScale(float fitScale) {
        return switch (cardSize) {
            case AUTO -> fitScale;
            case SMALL -> Math.min(fitScale, 0.50f);
            case MEDIUM -> Math.min(fitScale, 0.68f);
            case LARGE -> Math.min(fitScale, 0.92f);
        };
    }

    /** Brighten (or dim) a packed ARGB colour by the arena brightness setting. */
    public static int lit(int argb) {
        return scale(argb, brightness.factor);
    }

    /** Multiply the RGB channels of a packed ARGB colour, keeping alpha. */
    public static int scale(int argb, float factor) {
        int a = argb >>> 24;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // --- persistence --------------------------------------------------------

    private static Path file() {
        Minecraft mc = Minecraft.getInstance();
        Path dir = (mc == null ? Path.of(".") : mc.gameDirectory.toPath()).resolve("config");
        return dir.resolve(FILE);
    }

    /** Read saved preferences once; safe to call from anywhere, never throws. */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Properties props = new Properties();
        Path path = file();
        if (!Files.isReadable(path)) {
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException | RuntimeException ignored) {
            return; // a corrupt prefs file just means defaults
        }
        cardSize = enumOf(CardSize.values(), props.getProperty("cardSize"), cardSize);
        brightness = enumOf(Brightness.values(), props.getProperty("brightness"), brightness);
        killCounter = bool(props, "killCounter", killCounter);
        foilSheen = bool(props, "foilSheen", foilSheen);
        livePortraits = bool(props, "livePortraits", livePortraits);
        reducedMotion = bool(props, "reducedMotion", reducedMotion);
        battleHints = bool(props, "battleHints", battleHints);
        confirmLeave = bool(props, "confirmLeave", confirmLeave);
    }

    private static synchronized void save() {
        Properties props = new Properties();
        props.setProperty("cardSize", cardSize.name());
        props.setProperty("brightness", brightness.name());
        props.setProperty("killCounter", String.valueOf(killCounter));
        props.setProperty("foilSheen", String.valueOf(foilSheen));
        props.setProperty("livePortraits", String.valueOf(livePortraits));
        props.setProperty("reducedMotion", String.valueOf(reducedMotion));
        props.setProperty("battleHints", String.valueOf(battleHints));
        props.setProperty("confirmLeave", String.valueOf(confirmLeave));
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "Mob Trumps client preferences");
            }
        } catch (IOException | RuntimeException ignored) {
            // failing to save a UI preference must never interrupt play
        }
    }

    private static <E extends Enum<E>> E enumOf(E[] values, String name, E fallback) {
        if (name == null) {
            return fallback;
        }
        for (E v : values) {
            if (v.name().equals(name)) {
                return v;
            }
        }
        return fallback;
    }

    private static boolean bool(Properties props, String key, boolean fallback) {
        String v = props.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }
}
