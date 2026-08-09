package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.PackType;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A booster pack: right-click to rip it open and pull five distinct mob
 * cards. Pulls are weighted by spawn rarity, so legendaries are rare.
 */
public class CardPackItem extends Item {

    public static final int CARDS_PER_PACK = 5;

    private final PackType packType;

    public CardPackItem(Properties properties) {
        this(properties, PackType.STANDARD);
    }

    public CardPackItem(Properties properties, PackType packType) {
        super(properties);
        this.packType = packType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(pack, true);
        }

        if (!player.getAbilities().instabuild) {
            pack.shrink(1);
        }

        // the wrapper tearing — a real rip rather than a bamboo hit standing in
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.PACK_RIP.get(), SoundSource.PLAYERS, 0.9F, 1.0F);

        var random = ThreadLocalRandom.current();
        int count = Config.CARDS_PER_PACK.get();
        double foilChance = packType.foilChance * Config.FOIL_MULTIPLIER.get();
        List<MobCard> pulls = MobCards.openPack(packType.pool(), count, packType.bias, random);
        List<PackOpenedPayload.Pull> results = new ArrayList<>();
        boolean anyFoil = false;

        boolean anyLegendary = false;
        for (MobCard card : pulls) {
            boolean foil = random.nextFloat() < foilChance;
            anyFoil |= foil;
            anyLegendary |= card.tier() == Tier.LEGENDARY;
            ItemStack cardStack = MobCardItem.stackOf(card, foil);
            if (!player.getInventory().add(cardStack)) {
                player.drop(cardStack, false);
            }
            boolean newlyCollected = player instanceof ServerPlayer serverPlayer
                    && CollectionTracker.record(serverPlayer, card.id(), foil);
            results.add(new PackOpenedPayload.Pull(card.id(), foil, newlyCollected));
        }

        if (player instanceof ServerPlayer serverPlayer) {
            // drive the animated reveal screen on the opener's client
            PacketDistributor.sendToPlayer(serverPlayer, new PackOpenedPayload(results));
        }

        boolean special = anyFoil || anyLegendary;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                special ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.7F, special ? 1.0F : 1.4F);
        if (special && player instanceof ServerPlayer serverPlayer) {
            var sl = serverPlayer.serverLevel();
            double px = player.getX(), py = player.getY() + 1.1, pz = player.getZ();
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    px, py, pz, 60, 0.5, 0.6, 0.5, 0.15);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    px, py, pz, 25, 0.4, 0.5, 0.4, 0.05);
            level.playSound(null, px, py, pz, SoundEvents.FIREWORK_ROCKET_TWINKLE,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(pack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to pull " + CARDS_PER_PACK + " mob cards.")
                .withStyle(ChatFormatting.GRAY));
        if (packType != PackType.STANDARD) {
            tooltip.add(Component.literal(themeLabel() + " · no common filler")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private String themeLabel() {
        return switch (packType) {
            case NETHER -> "Nether mobs";
            case BOSS -> "Bosses & brutes";
            default -> "All mobs";
        };
    }
}
