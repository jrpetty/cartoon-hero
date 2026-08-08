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

    /** Open the big inspection view for the card held in {@code hand}. */
    public static void openCardInspect(net.minecraft.world.InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new CardInspectScreen(hand));
        }
    }

    /** Open the campaign route. */
    public static void openCampaign() {
        Minecraft.getInstance().setScreen(new CampaignScreen());
    }

    /** Open the server's Hall of Fame. */
    public static void openHall() {
        Minecraft.getInstance().setScreen(new HallScreen());
    }

    /** Open the Guess Who board. */
    public static void openGuessWho() {
        Minecraft.getInstance().setScreen(new GuessWhoScreen());
    }

    /** Open the Twenty-One table. */
    public static void openBlackjack() {
        Minecraft.getInstance().setScreen(new BlackjackScreen());
    }

    /** Raise a duel challenge on screen, or clear one that has been answered. */
    public static void duelInvite(com.jrpetty.mobtrumps.DuelInvitePayload payload) {
        ClientDuelInvite.set(payload);
        Minecraft mc = Minecraft.getInstance();
        if (!ClientDuelInvite.pending()) {
            if (mc.screen instanceof DuelInviteScreen) {
                mc.setScreen(null);
            }
            return;
        }
        // never steal a screen out from under someone mid-game: if they are
        // already busy the invite waits, and the chat line still tells them
        if (mc.screen == null) {
            mc.setScreen(new DuelInviteScreen());
        }
    }

    /** Open the Bluff table. */
    public static void openBluff() {
        Minecraft.getInstance().setScreen(new BluffScreen());
    }

    /** Open a recycler machine: 0 = the shredder, 1 = the press. */
    public static void openRecycler(int mode) {
        Minecraft.getInstance().setScreen(new RecyclerScreen(mode));
    }

    public static void openCollectionBook() {
        Minecraft.getInstance().setScreen(new CollectionBookScreen());
    }

    /** Right-clicking a projector: owners (or an empty one) get the picker;
     *  everyone else just admires the projected card full-screen. */
    public static void openProjectorInteract(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!(mc.level.getBlockEntity(pos) instanceof com.jrpetty.mobtrumps.HoloProjectorBlockEntity be)) {
            return;
        }
        boolean canEdit = be.canEdit(mc.player.getUUID());
        if (canEdit) {
            mc.setScreen(new ProjectorCardScreen(pos));
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

    /** Show an emote bubble on the battle screen, if one is open. */
    public static void showBattleEmote(int side, String text) {
        ClientBattle.setEmote(side, text);
    }

    /** Apply a battle state snapshot, opening or closing the battle screen. */
    public static void updateBattle(com.jrpetty.mobtrumps.BattleSyncPayload payload) {
        boolean wasCampaign = ClientBattle.campaignMission() > 0;
        ClientBattle.set(payload.phase(), payload.playerCardId(), payload.cpuCardId(),
                payload.nums(), payload.label());
        Minecraft mc = Minecraft.getInstance();
        int phase = payload.phase();
        if (phase == com.jrpetty.mobtrumps.BattleSyncPayload.CLOSED) {
            if (mc.screen instanceof BattleScreen) {
                // a mission ending hands you back to the route, not to the world
                mc.setScreen(wasCampaign ? new CampaignScreen() : null);
            }
            return;
        }
        // a final result should only land on a screen that's still open — don't
        // pop a DEFEAT screen onto someone who already walked away / forfeited
        if (phase == com.jrpetty.mobtrumps.BattleSyncPayload.FINISHED
                && !(mc.screen instanceof BattleScreen)) {
            return;
        }
        if (!(mc.screen instanceof BattleScreen)) {
            mc.setScreen(new BattleScreen());
        }
    }
}
