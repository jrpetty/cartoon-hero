package com.gadgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tracks where the live Item Receivers are, keyed by channel — the item-transport
 * sibling of {@link WirelessNetwork}. Receivers publish their location (dimension
 * + packed position) every few ticks; senders look up fresh receivers on their
 * channel and push items to them. Entries expire so unloaded/broken receivers
 * drop off on their own, and the map is cleared on server stop.
 */
public final class ItemNetwork {
    private static final long STALE_TICKS = 30L;

    /** channel -> (sourceKey "dim|packedPos" -> lastUpdateTick) */
    private static final Map<String, Map<String, Long>> CHANNELS = new HashMap<>();

    private ItemNetwork() {
    }

    public static synchronized void publish(String channel, String sourceKey, long now) {
        if (channel == null || channel.isEmpty()) {
            return;
        }
        CHANNELS.computeIfAbsent(channel, k -> new HashMap<>()).put(sourceKey, now);
    }

    public static synchronized void remove(String channel, String sourceKey) {
        if (channel == null || channel.isEmpty()) {
            return;
        }
        Map<String, Long> m = CHANNELS.get(channel);
        if (m != null) {
            m.remove(sourceKey);
            if (m.isEmpty()) {
                CHANNELS.remove(channel);
            }
        }
    }

    /** Fresh receiver source keys ("dim|packedPos") on the channel. */
    public static synchronized List<String> receivers(String channel, long now) {
        List<String> out = new ArrayList<>();
        if (channel == null || channel.isEmpty()) {
            return out;
        }
        Map<String, Long> m = CHANNELS.get(channel);
        if (m == null) {
            return out;
        }
        Iterator<Map.Entry<String, Long>> it = m.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            long t = e.getValue();
            if (now < t || now - t > STALE_TICKS) {
                it.remove();
                continue;
            }
            out.add(e.getKey());
        }
        if (m.isEmpty()) {
            CHANNELS.remove(channel);
        }
        return out;
    }

    public static synchronized void clear() {
        CHANNELS.clear();
    }
}
