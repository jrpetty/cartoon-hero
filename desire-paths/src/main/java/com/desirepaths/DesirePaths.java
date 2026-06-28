package com.desirepaths;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desire Paths — the ground remembers where you walk.
 *
 * <p>Walk the same grass repeatedly and it compacts on its own:
 * grass → coarse dirt → packed path. Stop using a route and, over a few
 * in-game days, nature reclaims it. No items, no UI — purely emergent.
 *
 * <p>All of the work happens server-side; see {@link PathWear}.
 */
public class DesirePaths implements ModInitializer {
    public static final String MOD_ID = "desirepaths";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PathWear tracker = new PathWear();

        // Count footsteps as players move across the world.
        ServerTickEvents.END_WORLD_TICK.register(tracker::onWorldTick);

        LOGGER.info("Desire Paths initialized — the world is watching your feet.");
    }
}
