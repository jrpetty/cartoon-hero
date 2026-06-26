package dev.structint.client;

import dev.structint.StructuralIntegrityMod;
import dev.structint.world.BlockClassifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Adds a structural-strength readout to the tooltip of any recognized building material, so a
 * player can tell at a glance how far a block will span before they build with it:
 *
 * <pre>
 *   Oak Planks
 *   Structural strength: Medium
 *   Max unsupported span: 4 blocks
 * </pre>
 *
 * Client-only; reads the same spans the server uses (synced config), so it always reflects the
 * actual rules in force.
 */
@EventBusSubscriber(modid = StructuralIntegrityMod.MODID, value = Dist.CLIENT)
public final class TooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();

        if (BlockClassifier.isFoundation(state)) {
            event.getToolTip().add(Component.translatable("tooltip.structint.foundation")
                    .withStyle(ChatFormatting.AQUA));
            return;
        }

        Integer span = BlockClassifier.taggedSpan(state);
        if (span == null) {
            return; // not a recognized structural material — keep its tooltip clean
        }

        Tier tier = Tier.forSpan(span);
        event.getToolTip().add(Component.translatable("tooltip.structint.strength",
                        Component.translatable(tier.key).withStyle(tier.color))
                .withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("tooltip.structint.span", span)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Coarse strength buckets, deliberately independent of exact config numbers. */
    private enum Tier {
        VERY_WEAK("tooltip.structint.tier.very_weak", ChatFormatting.RED),
        WEAK("tooltip.structint.tier.weak", ChatFormatting.GOLD),
        MEDIUM("tooltip.structint.tier.medium", ChatFormatting.YELLOW),
        STRONG("tooltip.structint.tier.strong", ChatFormatting.GREEN),
        VERY_STRONG("tooltip.structint.tier.very_strong", ChatFormatting.AQUA),
        EXTREME("tooltip.structint.tier.extreme", ChatFormatting.LIGHT_PURPLE);

        final String key;
        final ChatFormatting color;

        Tier(String key, ChatFormatting color) {
            this.key = key;
            this.color = color;
        }

        static Tier forSpan(int span) {
            if (span <= 1) return VERY_WEAK;
            if (span <= 3) return WEAK;
            if (span <= 6) return MEDIUM;
            if (span <= 11) return STRONG;
            if (span <= 19) return VERY_STRONG;
            return EXTREME;
        }
    }

    private TooltipHandler() {
    }
}
