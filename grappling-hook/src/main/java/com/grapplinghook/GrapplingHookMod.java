package com.grapplinghook;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grappling Hook — right-click a block in range and get yanked to it, then
 * upgrade the thing at an Enhancement Bench.
 */
public class GrapplingHookMod implements ModInitializer {
    public static final String MOD_ID = "grapplinghook";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item GRAPPLING_HOOK = register(
            "grappling_hook",
            new GrapplingHookItem(new Item.Settings().maxCount(1)));

    public static final Block ENHANCEMENT_TABLE = Registry.register(Registries.BLOCK,
            Identifier.of(MOD_ID, "enhancement_table"),
            new EnhancementTableBlock(AbstractBlock.Settings.create()
                    .strength(2.5F).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque()));
    public static final Item ENHANCEMENT_TABLE_ITEM = register("enhancement_table",
            new BlockItem(ENHANCEMENT_TABLE, new Item.Settings()));

    public static final ScreenHandlerType<EnhancementScreenHandler> ENHANCEMENT_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "enhancement_table"),
                    new ScreenHandlerType<>(EnhancementScreenHandler::new,
                            net.minecraft.resource.featuretoggle.FeatureSet.empty()));

    public static final RegistryKey<ItemGroup> GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MOD_ID, "grapplinghook"));
    public static final ItemGroup GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(GRAPPLING_HOOK))
            .displayName(Text.translatable("itemgroup.grapplinghook.grapplinghook"))
            .build();

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), item);
    }

    @Override
    public void onInitialize() {
        GrappleConfig.load();

        Registry.register(Registries.ITEM_GROUP, GROUP_KEY, GROUP);
        ItemGroupEvents.modifyEntriesEvent(GROUP_KEY).register(entries -> {
            entries.add(GRAPPLING_HOOK);
            entries.add(ENHANCEMENT_TABLE_ITEM);
        });
        // Still discoverable in the vanilla tab people already look in.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(GRAPPLING_HOOK));

        LOGGER.info("Grappling Hook ready — charge up and aim high.");
    }
}
