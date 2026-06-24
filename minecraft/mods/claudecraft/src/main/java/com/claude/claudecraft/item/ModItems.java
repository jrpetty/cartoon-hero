package com.claude.claudecraft.item;

import com.claude.claudecraft.ClaudeCraft;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Items added by ClaudeCraft.
 *
 * <p>All items are registered up-front in {@link #registerModItems()}, which is
 * called once from the mod initializer.
 */
public final class ModItems {
	private ModItems() {
	}

	// A plain crafting material.
	public static final Item CLAUDE_CORE = register("claude_core", new Item(new Item.Settings()));

	// An enchanted-apple-style food that grants a short burst of regeneration and
	// resistance. Always edible so it can be used even at full hunger.
	public static final Item ANTHROPIC_APPLE = register("anthropic_apple", new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(6)
					.saturationModifier(1.2f)
					.alwaysEdible()
					.statusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1), 1.0f)
					.statusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 0), 1.0f)
					.build())));

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(ClaudeCraft.MOD_ID, name), item);
	}

	public static void registerModItems() {
		ClaudeCraft.LOGGER.info("[ClaudeCraft] Registering items.");
	}
}
