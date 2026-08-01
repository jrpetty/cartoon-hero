package com.jrpetty.aztecabyss.engine;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Wiring for the engine layer.
 *
 * <p>Deliberately its own subscriber rather than more branches inside the existing
 * arena handler. The engine is meant to outlive the four hand-built maps, so it
 * does not get tangled into their code.
 *
 * <p>Dealer signs work in <em>every</em> dimension on purpose. Map authors build and
 * test in the overworld long before anything is stamped into the Abyss, and a shop
 * that only functions once the map is "properly" installed is a shop you cannot
 * iterate on.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class EngineEvents {

    private EngineEvents() {
    }

    @SubscribeEvent
    public static void onRightClickSign(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof SignBlockEntity sign)) {
            return;
        }
        if (!DealerSign.isDealer(sign)) {
            return;
        }
        // Sneaking is left alone so an author can still edit their own sign.
        if (player.isShiftKeyDown()) {
            return;
        }
        if (DealerSign.buy(level, player, sign)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> wallet = Commands.literal("wallet")
                .executes(ctx -> show(ctx.getSource()))
                .then(Commands.literal("give").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("currency", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .executes(ctx -> give(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "currency"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"))))));
        event.getDispatcher().register(wallet);
    }

    /** {@code /wallet} - what you are carrying, in every currency that exists. */
    private static int show(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6— Your wallet —"), false);
        for (Currency c : Currency.all()) {
            source.sendSuccess(() -> Component.literal(
                    "§7" + c.name() + ": " + c.format(c.balance(player))
                            + " §8(" + c.backing().name().toLowerCase(java.util.Locale.ROOT) + ")"), false);
        }
        return 1;
    }

    /** {@code /wallet give <currency> <amount>} - for testing a map's prices. */
    private static int give(CommandSourceStack source, String currencyId, int amount) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        Currency c = Currency.byId(currencyId);
        c.award(player, amount);
        source.sendSuccess(() -> Component.literal(
                "§a✔ " + c.format(amount) + " §a→ " + player.getGameProfile().getName()), true);
        return 1;
    }
}
