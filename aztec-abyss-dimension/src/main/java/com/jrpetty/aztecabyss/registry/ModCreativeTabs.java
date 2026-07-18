package com.jrpetty.aztecabyss.registry;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AztecAbyssConstants.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AZTEC_ABYSS_TAB = TABS.register(
            "aztecabyss_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aztecabyss"))
                    .icon(() -> new ItemStack(ModItems.ABYSSAL_OBSIDIAN.get()))
                    .displayItems((params, output) -> output.accept(ModItems.ABYSSAL_OBSIDIAN.get()))
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
