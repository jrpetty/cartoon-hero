package com.gadgets;

import net.minecraft.util.RandomSource;

/**
 * The eight-digit codes gadgets are paired by.
 *
 * <p>A code is minted by the thing being addressed — a receiver, a hub — and
 * typed into the thing addressing it. That way round matters: an address you
 * choose is an address somebody else has already chosen, and the whole point of
 * a code is that reading it off one block and typing it into another is enough.
 */
public final class LinkCode {
    public static final int LENGTH = 8;

    private LinkCode() {
    }

    /** True when the string is exactly {@link #LENGTH} digits. */
    public static boolean isCode(String text) {
        if (text == null || text.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /** "1234 5678" — grouped, because eight digits are read off a screen by eye. */
    public static String pretty(String code) {
        return isCode(code) ? code.substring(0, 4) + " " + code.substring(4) : code;
    }

    public static String mint(RandomSource random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append((char) ('0' + random.nextInt(10)));
        }
        return sb.toString();
    }
}
