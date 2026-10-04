package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.GamePay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pays every finished game its experience, whichever game it was.
 *
 * <p>The amount is the server's {@link Config#GAME_XP} — by default 25, about
 * five zombies' worth — shaped by {@link GamePay} so a very short game pays
 * its share rather than the lot. Leaving or forfeiting pays nothing: callers
 * only come here for a game that actually reached its end.
 */
public final class GameRewards {

    /** What the default config pays, used before the config has loaded. */
    private static final int FALLBACK_XP = 25;

    private GameRewards() {
    }

    public static int fullXp() {
        try {
            return Config.GAME_XP.get();
        } catch (IllegalStateException notLoaded) {
            return FALLBACK_XP;
        }
    }

    /**
     * Pay a finished game's experience and return how much was paid.
     *
     * <p>With {@code toast} the client floats a small "+25 XP" over whatever
     * screen is open. The battle screen passes false, because its result panel
     * already shows the figure and two of them would read as double pay.
     */
    public static int payGame(ServerPlayer player, long startedMs, boolean toast) {
        if (player == null || player.hasDisconnected()) {
            return 0;
        }
        int xp = GamePay.xp(fullXp(), System.currentTimeMillis() - startedMs);
        if (xp <= 0) {
            return 0;
        }
        player.giveExperiencePoints(xp);
        // the orb chime, kept at the quiet end so a run of games never nags
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.25F, 0.9F + player.getRandom().nextFloat() * 0.3F);
        if (toast) {
            PacketDistributor.sendToPlayer(player, new XpGainPayload(xp));
        }
        return xp;
    }
}
