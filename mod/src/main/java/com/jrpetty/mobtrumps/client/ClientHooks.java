package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.MobCard;
import net.minecraft.client.Minecraft;

/**
 * Client-only entry points. Only ever call these from code paths guarded by
 * {@code level.isClientSide} so this class never loads on a dedicated server.
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void openCardScreen(MobCard card) {
        Minecraft.getInstance().setScreen(new MobCardScreen(card));
    }
}
