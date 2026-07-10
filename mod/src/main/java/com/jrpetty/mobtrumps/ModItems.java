package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MobTrumps.MODID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MobTrumps.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MobTrumps.MODID);

    /** Data component storing which mob a card item represents. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> MOB_ID =
            DATA_COMPONENTS.register("mob_id", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    public static final DeferredItem<MobCardItem> MOB_CARD =
            ITEMS.register("mob_card", () -> new MobCardItem(new Item.Properties()));
    public static final DeferredItem<CardPackItem> CARD_PACK =
            ITEMS.register("card_pack", () -> new CardPackItem(new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_MODE_TABS.register("mobtrumps", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mobtrumps"))
                    .icon(() -> new ItemStack(CARD_PACK.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(CARD_PACK.get());
                        for (MobCard card : MobCards.ALL) {
                            output.accept(MobCardItem.stackOf(card));
                        }
                    })
                    .build());

    private ModItems() {
    }
}
