package com.jrpetty.zombierealm;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ZombieRealm.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZombieRealm.MODID);

    /** The Rotten Heart — right-click to travel to and from the Rotten Realm. */
    public static final DeferredItem<RottenHeartItem> ROTTEN_HEART =
            ITEMS.register("rotten_heart",
                    () -> new RottenHeartItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_MODE_TABS.register("zombierealm", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.zombierealm"))
                    .icon(() -> new ItemStack(ROTTEN_HEART.get()))
                    .displayItems((params, output) -> output.accept(ROTTEN_HEART.get()))
                    .build());

    private ModItems() {
    }
}
