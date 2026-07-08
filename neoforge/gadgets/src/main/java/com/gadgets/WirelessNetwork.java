package com.gadgets;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A tiny server-side registry that carries redstone signals over named channels.
 *
 * <p>Transmitters {@link #publish} their input power under a channel name, keyed
 * by a unique source id (dimension + position). Receivers {@link #strength} read
 * the strongest live signal on their channel. Entries expire after a short window
 * so a transmitter that is broken, unpowered, or unloaded stops driving the
 * channel on its own — the state is fully self-healing and never has to be saved
 * to disk (each block remembers its own channel in NBT; the live power does not).
 */
public final class WirelessNetwork {
    /** How many ticks a published entry survives without a refresh. */
    private static final long STALE_TICKS = 30L;

    /** channel -> (sourceId -> [power, lastUpdateTick]) */
    private static final Map<String, Map<String, long[]>> CHANNELS = new HashMap<>();

    private WirelessNetwork() {
    }

    public static synchronized void publish(String channel, String source, int power, long now) {
        if (channel == null || channel.isEmpty()) {
            return;
        }
        if (power <= 0) {
            Map<String, long[]> m = CHANNELS.get(channel);
            if (m != null) {
                m.remove(source);
                if (m.isEmpty()) {
                    CHANNELS.remove(channel);
                }
            }
            return;
        }
        CHANNELS.computeIfAbsent(channel, k -> new HashMap<>()).put(source, new long[]{power, now});
    }

    /** Drop all live signals — call when a server stops so nothing leaks into the next world. */
    public static synchronized void clear() {
        CHANNELS.clear();
    }

    public static synchronized int strength(String channel, long now) {
        if (channel == null || channel.isEmpty()) {
            return 0;
        }
        Map<String, long[]> m = CHANNELS.get(channel);
        if (m == null) {
            return 0;
        }
        int best = 0;
        Iterator<Map.Entry<String, long[]>> it = m.entrySet().iterator();
        while (it.hasNext()) {
            long[] entry = it.next().getValue();
            if (now < entry[1] || now - entry[1] > STALE_TICKS) {
                it.remove();
                continue;
            }
            if (entry[0] > best) {
                best = (int) entry[0];
            }
        }
        if (m.isEmpty()) {
            CHANNELS.remove(channel);
        }
        return best;
    }
}
