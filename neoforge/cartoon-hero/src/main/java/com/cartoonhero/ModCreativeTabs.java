package com.cartoonhero;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CartoonHero.MODID);

    public static final Supplier<CreativeModeTab> CARTOON_HERO_TAB = TABS.register("cartoon_hero",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemgroup.cartoonhero.cartoon_hero"))
                    .icon(() -> new ItemStack(ModItems.HERO_EMBLEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.HERO_EMBLEM.get());
                        output.accept(ModBlocks.CARTOON_BRICKS.get());
                    })
                    .build());
}
