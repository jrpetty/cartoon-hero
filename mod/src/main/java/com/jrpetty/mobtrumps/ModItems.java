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

    /** Data component marking a card as a rare holographic foil variant. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FOIL =
            DATA_COMPONENTS.register("foil", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    public static final DeferredItem<MobCardItem> MOB_CARD =
            ITEMS.register("mob_card", () -> new MobCardItem(new Item.Properties()));
    public static final DeferredItem<CardPackItem> CARD_PACK =
            ITEMS.register("card_pack", () -> new CardPackItem(new Item.Properties()));
    public static final DeferredItem<CardPackItem> NETHER_PACK =
            ITEMS.register("nether_pack", () -> new CardPackItem(new Item.Properties(),
                    com.jrpetty.mobtrumps.game.PackType.NETHER));
    public static final DeferredItem<CardPackItem> BOSS_PACK =
            ITEMS.register("boss_pack", () -> new CardPackItem(new Item.Properties(),
                    com.jrpetty.mobtrumps.game.PackType.BOSS));
    public static final DeferredItem<CollectionBookItem> COLLECTION_BOOK =
            ITEMS.register("collection_book", () -> new CollectionBookItem(new Item.Properties()));
    public static final DeferredItem<CardScannerItem> CARD_SCANNER =
            ITEMS.register("card_scanner", () -> new CardScannerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_MODE_TABS.register("mobtrumps", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mobtrumps"))
                    .icon(() -> new ItemStack(COLLECTION_BOOK.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(COLLECTION_BOOK.get());
                        output.accept(CARD_SCANNER.get());
                        output.accept(ModBlocks.DUELING_TABLE.get());
                        output.accept(ModBlocks.CARD_DISPLAY.get());
                        output.accept(ModBlocks.HOLO_PROJECTOR.get());
                        // cards are earned by hunting mobs; every card is shown here for reference
                        for (MobCard card : MobCards.ALL) {
                            output.accept(MobCardItem.stackOf(card));
                            output.accept(MobCardItem.stackOf(card, true));
                        }
                    })
                    .build());

    private ModItems() {
    }
}
