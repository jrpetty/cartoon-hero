package com.succession;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Succession — abandoned, cleared land slowly heals itself.
 *
 * <p>Over time bare dirt greens over, grass sprouts flowers and ferns, and the
 * occasional sapling takes root and (via normal growth) becomes a tree. Leave a
 * scarred clearing alone long enough and the forest reclaims it.
 *
 * <p>All logic is server-side; see {@link SuccessionEngine}.
 */
public class SuccessionMod implements ModInitializer {
    public static final String MOD_ID = "succession";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SuccessionEngine engine = new SuccessionEngine();
        ServerTickEvents.END_WORLD_TICK.register(engine::onWorldTick);

        LOGGER.info("Succession initialized — the wild is patient.");
    }
}
