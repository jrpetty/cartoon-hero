package com.gadgets;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A Command Hub's board, in your pocket.
 *
 * <p>Read a hub's code off its screen, type it into the tablet, and from then
 * on it answers anywhere — down a mineshaft, in the Nether, a thousand blocks
 * out. What it shows is the last board that hub published, with how long ago it
 * was heard from, because a base whose chunks are unloaded is genuinely not
 * doing anything and pretending otherwise would be a lie rather than a feature.
 */
public class BaseTabletItem extends Item {
    private static final String CODE_KEY = "HubCode";

    public BaseTabletItem(Properties properties) {
        super(properties);
    }

    public static String codeOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(CODE_KEY);
    }

    public static void setCode(ItemStack stack, String code) {
        CompoundTag tag = new CompoundTag();
        tag.putString(CODE_KEY, code == null ? "" : code);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            ScreenOpener.TABLET.run();
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String code = codeOf(stack);
        lines.add(Component.literal(code.isEmpty()
                        ? "Not linked — right-click and enter a hub's code"
                        : "Hub " + LinkCode.pretty(code))
                .withStyle(code.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        lines.add(Component.literal("Reads your base from anywhere")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
