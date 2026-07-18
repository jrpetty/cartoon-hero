package com.jrpetty.zombierealm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Set;

/**
 * Right-click to travel between the Overworld and the Rotten Realm. The
 * destination is chosen from where you currently are, dropping you onto the
 * surface at the same x/z. Glows like an enchanted item.
 */
public class RottenHeartItem extends Item {

    public RottenHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var server = serverPlayer.serverLevel().getServer();
            boolean inRealm = serverPlayer.level().dimension().equals(ZombieRealm.ROTTEN_REALM);
            ServerLevel target = server.getLevel(inRealm ? Level.OVERWORLD : ZombieRealm.ROTTEN_REALM);
            if (target != null) {
                int x = Mth.floor(serverPlayer.getX());
                int z = Mth.floor(serverPlayer.getZ());
                target.getChunk(x >> 4, z >> 4); // force-load the destination chunk
                int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(),
                        serverPlayer.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.7F, 0.8F);
                serverPlayer.teleportTo(target, x + 0.5, y, z + 0.5, Set.of(),
                        serverPlayer.getYRot(), serverPlayer.getXRot());
                target.playSound(null, x + 0.5, y, z + 0.5, SoundEvents.PORTAL_TRAVEL,
                        SoundSource.PLAYERS, 0.6F, 1.0F);
                serverPlayer.getCooldowns().addCooldown(this, 60);
                serverPlayer.displayClientMessage(Component.literal(
                        inRealm ? "You escape the Rotten Realm." : "You slip into the Rotten Realm...")
                        .withStyle(inRealm ? ChatFormatting.GREEN : ChatFormatting.DARK_GREEN), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to cross into — or out of —")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("the Rotten Realm.").withStyle(ChatFormatting.GRAY));
    }
}
