package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.HallSyncPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side copy of the Hall of Fame, fetched on demand. */
public final class ClientHall {

    private static volatile Map<String, String> firstPrints = Map.of();
    private static volatile Map<String, String> categories = Map.of();
    private static volatile List<String> completions = List.of();
    private static volatile boolean loaded;

    private ClientHall() {
    }

    public static void set(HallSyncPayload payload) {
        firstPrints = split(payload.firstPrints());
        categories = split(payload.categories());
        completions = List.copyOf(payload.completions());
        loaded = true;
    }

    private static Map<String, String> split(List<String> pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : pairs) {
            int bar = pair.indexOf('|');
            if (bar > 0) {
                out.put(pair.substring(0, bar), pair.substring(bar + 1));
            }
        }
        return Map.copyOf(out);
    }

    public static boolean loaded() { return loaded; }
    public static Map<String, String> firstPrints() { return firstPrints; }
    public static Map<String, String> categories() { return categories; }
    public static List<String> completions() { return completions; }

    /** Who holds the most 000001s, best first. */
    public static List<Map.Entry<String, Integer>> finders() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (String who : firstPrints.values()) {
            tally.merge(who, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> out = new java.util.ArrayList<>(tally.entrySet());
        out.sort((a, b) -> b.getValue() - a.getValue());
        return out;
    }
}
