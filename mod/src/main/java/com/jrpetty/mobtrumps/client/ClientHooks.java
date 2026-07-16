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

    /** Right-clicking a card display: owners (or an empty display) get the
     *  picker; everyone else just admires the projected card full-screen. */
    public static void openDisplayInteract(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!(mc.level.getBlockEntity(pos) instanceof com.jrpetty.mobtrumps.CardDisplayBlockEntity be)) {
            return;
        }
        boolean canEdit = be.canEdit(mc.player.getUUID());
        if (canEdit) {
            mc.setScreen(new CardDisplayScreen(pos));
        } else if (be.hasCard()) {
            MobCard card = com.jrpetty.mobtrumps.game.MobCards.byId(be.getMobId());
            if (card != null) {
                mc.setScreen(new MobCardScreen(card, null, be.isFoil()));
            }
        }
    }

    public static void openPackReveal(java.util.List<com.jrpetty.mobtrumps.PackOpenedPayload.Pull> pulls) {
        if (!pulls.isEmpty()) {
            Minecraft.getInstance().setScreen(new PackRevealScreen(pulls));
        }
    }

    /** Open the dueling table's home menu with the server's seat snapshot. */
    public static void openTableMenu(com.jrpetty.mobtrumps.TableMenuPayload payload) {
        Minecraft.getInstance().setScreen(new TableMenuScreen(
                payload.pos(), payload.seatedName(), payload.seatedMode(), payload.selfSeated()));
    }

    /** Apply a battle state snapshot, opening or closing the battle screen. */
    public static void updateBattle(com.jrpetty.mobtrumps.BattleSyncPayload payload) {
        ClientBattle.set(payload.phase(), payload.playerCardId(), payload.cpuCardId(), payload.nums());
        Minecraft mc = Minecraft.getInstance();
        if (payload.phase() == com.jrpetty.mobtrumps.BattleSyncPayload.CLOSED) {
            if (mc.screen instanceof BattleScreen) {
                mc.setScreen(null);
            }
            return;
        }
        if (!(mc.screen instanceof BattleScreen)) {
            mc.setScreen(new BattleScreen());
        }
    }
}
