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
        openCardScreen(card, false);
    }

    public static void openCardScreen(MobCard card, boolean foil) {
        Minecraft.getInstance().setScreen(new MobCardScreen(card, null, foil));
    }

    public static void openCollectionBook() {
        Minecraft.getInstance().setScreen(new CollectionBookScreen());
    }

    public static void openPackReveal(java.util.List<com.jrpetty.mobtrumps.PackOpenedPayload.Pull> pulls) {
        if (!pulls.isEmpty()) {
            Minecraft.getInstance().setScreen(new PackRevealScreen(pulls));
        }
    }
}
