package com.grapplinghook;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(GrapplingHookMod.MODID)
public class GrapplingHookMod {
    public static final String MODID = "grapplinghook";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<Item> GRAPPLING_HOOK = ITEMS.register("grappling_hook",
            () -> new GrapplingHookItem(new Item.Properties().stacksTo(1)));

    public static final DeferredBlock<Block> ENHANCEMENT_TABLE = BLOCKS.register("enhancement_table",
            () -> new EnhancementTableBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<?> ENHANCEMENT_TABLE_ITEM = ITEMS.register("enhancement_table",
            () -> new BlockItem(ENHANCEMENT_TABLE.get(), new Item.Properties()));

    public static final Supplier<MenuType<EnhancementMenu>> ENHANCEMENT_MENU =
            MENUS.register("enhancement_table",
                    () -> IMenuTypeExtension.create((id, inventory, buf) -> new EnhancementMenu(id, inventory)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("grapplinghook",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemgroup.grapplinghook.grapplinghook"))
                    .icon(() -> new ItemStack(GRAPPLING_HOOK.get()))
                    .displayItems((params, output) -> {
                        output.accept(GRAPPLING_HOOK.get());
                        output.accept(ENHANCEMENT_TABLE_ITEM.get());
                    })
                    .build());

    public GrapplingHookMod(IEventBus modBus) {
        GrappleConfig.load();
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
        modBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Still discoverable in the vanilla tab people already look in.
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(GRAPPLING_HOOK);
        }
    }
}
