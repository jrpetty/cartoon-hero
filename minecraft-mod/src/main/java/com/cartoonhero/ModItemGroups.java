package com.cartoonhero;

import com.cartoonhero.block.ModBlocks;
import com.cartoonhero.item.ModItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Defines the "Cartoon Hero" creative-inventory tab and fills it with this
 * mod's content.
 */
public class ModItemGroups {

    public static final RegistryKey<ItemGroup> CARTOON_HERO_GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(CartoonHero.MOD_ID, "cartoon_hero"));

    public static final ItemGroup CARTOON_HERO_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(ModItems.HERO_EMBLEM))
            .displayName(Text.translatable("itemgroup.cartoonhero.cartoon_hero"))
            .build();

    public static void registerItemGroups() {
        Registry.register(Registries.ITEM_GROUP, CARTOON_HERO_GROUP_KEY, CARTOON_HERO_GROUP);

        ItemGroupEvents.modifyEntriesEvent(CARTOON_HERO_GROUP_KEY).register(entries -> {
            entries.add(ModItems.HERO_EMBLEM);
            entries.add(ModBlocks.CARTOON_BRICKS);
        });

        CartoonHero.LOGGER.info("Registering item groups for {}", CartoonHero.MOD_ID);
    }
}
