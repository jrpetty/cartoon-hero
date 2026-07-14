package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Compact, shareable deck codes. A deck is a list of mob ids; each maps to its
 * ordinal (0-80) which fits in a single byte, so the whole deck packs into a
 * short URL-safe Base64 string a friend can import.
 */
public final class DeckCodes {

    private DeckCodes() {
    }

    public static String encode(List<String> ids) {
        byte[] bytes = new byte[ids.size()];
        int n = 0;
        for (String id : ids) {
            int ord = MobCards.ordinal(id);
            if (ord >= 0 && ord < 256) bytes[n++] = (byte) ord;
        }
        byte[] trimmed = n == bytes.length ? bytes : java.util.Arrays.copyOf(bytes, n);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed);
    }

    /** Decode a code back into mob ids, ignoring any that don't resolve. Null if malformed. */
    public static List<String> decode(String code) {
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(code.trim());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (byte b : bytes) {
            int ord = b & 0xFF;
            if (ord < MobCards.ALL.size()) {
                MobCard card = MobCards.ALL.get(ord);
                ids.add(card.id());
            }
        }
        return ids;
    }
}
