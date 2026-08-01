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

        LiteralArgumentBuilder<CommandSourceStack> arena = Commands.literal("arena")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("scan")
                        .executes(ctx -> scan(ctx.getSource(), 64))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 256))
                                .executes(ctx -> scan(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("validate")
                        .executes(ctx -> validate(ctx.getSource(), 64))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 256))
                                .executes(ctx -> validate(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))));
        event.getDispatcher().register(arena);
    }

    /** A box centred on the caller, tall enough to catch a whole build. */
    private static net.minecraft.world.level.levelgen.structure.BoundingBox around(
            ServerPlayer player, int radius) {
        net.minecraft.core.BlockPos at = player.blockPosition();
        return new net.minecraft.world.level.levelgen.structure.BoundingBox(
                at.getX() - radius, player.level().getMinBuildHeight(), at.getZ() - radius,
                at.getX() + radius, player.level().getMaxBuildHeight() - 1, at.getZ() + radius);
    }

    /** {@code /arena scan} - what the engine can see from where you are stood. */
    private static int scan(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        MapScan.Result result = MapScan.scan(level, around(player, radius));
        if (result.all().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§7Nothing found within " + radius + " blocks. Markers are signs whose "
                            + "first line is a kind in brackets, like §f[Spawn]§7."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                "§6— " + result.all().size() + " markers within " + radius + " blocks —"), false);
        result.byKind().forEach((kind, list) -> source.sendSuccess(() -> Component.literal(
                "§e" + list.size() + "§7 × §f[" + kind + "]"), false));
        return 1;
    }

    /** {@code /arena validate} - would this actually play? */
    private static int validate(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        MapScan.Result result = MapScan.scan(level, around(player, radius));
        java.util.List<String> problems = MapScan.validate(result);
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§a✔ Playable. §7" + result.all().size() + " markers, "
                            + result.count("horde") + " ways in."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("§6— " + problems.size() + " to fix —"), false);
        for (String p : problems) {
            source.sendSuccess(() -> Component.literal(" " + p), false);
        }
        return 1;
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
