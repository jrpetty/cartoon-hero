package dev.structint.registry;

import dev.structint.StructuralIntegrityMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Block items and the creative tab that collects the mod's blocks. */
public final class ModContent {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(StructuralIntegrityMod.MODID);

    public static final DeferredItem<BlockItem> FOUNDATION_ANCHOR_ITEM =
            ITEMS.registerSimpleBlockItem("foundation_anchor", ModBlocks.FOUNDATION_ANCHOR);
    public static final DeferredItem<BlockItem> REINFORCED_BEAM_ITEM =
            ITEMS.registerSimpleBlockItem("reinforced_beam", ModBlocks.REINFORCED_BEAM);
    public static final DeferredItem<BlockItem> HEAVY_GIRDER_ITEM =
            ITEMS.registerSimpleBlockItem("heavy_girder", ModBlocks.HEAVY_GIRDER);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StructuralIntegrityMod.MODID);

    public static final Supplier<CreativeModeTab> TAB = register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.structint"))
                    .icon(() -> new ItemStack(ModBlocks.FOUNDATION_ANCHOR.get()))
                    .displayItems((params, output) -> {
                        output.accept(FOUNDATION_ANCHOR_ITEM.get());
                        output.accept(REINFORCED_BEAM_ITEM.get());
                        output.accept(HEAVY_GIRDER_ITEM.get());
                    })
                    .build());

    private static DeferredHolder<CreativeModeTab, CreativeModeTab> register(
            String name, Supplier<CreativeModeTab> tab) {
        return CREATIVE_TABS.register(name, tab);
    }

    private ModContent() {
    }
}
