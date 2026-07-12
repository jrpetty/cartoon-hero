package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** A free Mob Card Pack claimable once per cooldown via /mobtrumps daily. */
public final class DailyReward {

    private DailyReward() {
    }

    private static long cooldownMs() {
        return Config.DAILY_COOLDOWN_HOURS.get() * 3600_000L;
    }

    public static boolean isReady(ServerPlayer player) {
        long last = player.getData(ModAttachments.LAST_DAILY.get());
        return System.currentTimeMillis() - last >= cooldownMs();
    }

    /** Nudge the player on login if a daily pack is waiting. */
    public static void notifyOnLogin(ServerPlayer player) {
        if (isReady(player)) {
            player.sendSystemMessage(Component.literal("Your daily Mob Card Pack is ready! ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(claimButton()));
        }
    }

    public static int claim(ServerPlayer player) {
        if (!isReady(player)) {
            long left = cooldownMs() - (System.currentTimeMillis()
                    - player.getData(ModAttachments.LAST_DAILY.get()));
            long hrs = left / 3600_000L;
            long mins = (left % 3600_000L) / 60_000L;
            player.sendSystemMessage(Component.literal("Your next daily pack is in "
                            + (hrs > 0 ? hrs + "h " : "") + mins + "m.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        player.setData(ModAttachments.LAST_DAILY.get(), System.currentTimeMillis());
        ItemStack pack = new ItemStack(ModItems.CARD_PACK.get());
        CardActions.give(player, pack);
        player.sendSystemMessage(Component.literal("Claimed your daily Mob Card Pack — right-click to open it!")
                .withStyle(ChatFormatting.GREEN));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.5F);
        return 1;
    }

    private static Component claimButton() {
        return Component.literal("[Claim]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mobtrumps daily"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Claim your free daily pack"))));
    }
}
