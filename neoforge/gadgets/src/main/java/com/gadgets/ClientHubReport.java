package com.gadgets;

import net.minecraft.nbt.CompoundTag;

/**
 * The last answer a Base Tablet got back, held for the screen to draw.
 *
 * <p>Deliberately free of any client-only type. The payload handler that fills
 * it is named from common code, and a class that mentioned {@code Minecraft} or
 * a screen would be dragged onto a dedicated server's classpath along with it.
 * Everything here is plain data; the screen does the drawing.
 */
public final class ClientHubReport {
    /** What state a request is in, for the screen to say something useful. */
    public static final int IDLE = 0;
    public static final int WAITING = 1;
    public static final int FOUND = 2;
    public static final int MISSING = 3;

    private static int state = IDLE;
    private static String code = "";
    private static String name = "";
    private static String dim = "";
    private static long ageTicks;
    private static CompoundTag board = new CompoundTag();

    private ClientHubReport() {
    }

    /** Note that a request has gone out, so the screen can say it is asking. */
    public static void asked(String forCode) {
        state = WAITING;
        code = forCode;
        name = "";
        dim = "";
        ageTicks = 0L;
        board = new CompoundTag();
    }

    public static void accept(HubReportPayload.Reply reply) {
        state = reply.found() ? FOUND : MISSING;
        name = reply.name();
        dim = reply.dim();
        ageTicks = reply.ageTicks();
        board = reply.board();
    }

    public static void forget() {
        state = IDLE;
        code = "";
        board = new CompoundTag();
    }

    public static int state() {
        return state;
    }

    public static String code() {
        return code;
    }

    public static String name() {
        return name;
    }

    public static String dim() {
        return dim;
    }

    public static long ageTicks() {
        return ageTicks;
    }

    public static CompoundTag board() {
        return board;
    }

    /** "just now" / "4m ago" / "2h ago" — how stale the reading is. */
    public static String age() {
        long seconds = ageTicks / 20L;
        if (seconds < 5) {
            return "just now";
        }
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m ago";
        }
        return (seconds / 3600) + "h ago";
    }

    /** True while the base is loaded and reporting live rather than remembered. */
    public static boolean live() {
        return state == FOUND && ageTicks < 100L;
    }
}
