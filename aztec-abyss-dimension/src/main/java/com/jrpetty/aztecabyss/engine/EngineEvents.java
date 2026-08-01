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
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("save")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> save(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"), 48))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 128))
                                        .executes(ctx -> save(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("load")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> load(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("play")
                        .executes(ctx -> play(ctx.getSource(), 64, "built-in"))
                        .then(Commands.argument("ruleset", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> play(ctx.getSource(), 64,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ruleset")))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 256))
                                        .executes(ctx -> play(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ruleset"))))))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            EngineArena.stop(true);
                            ctx.getSource().sendSuccess(() -> Component.literal("§7Run stopped."), true);
                            return 1;
                        }))
                .then(Commands.literal("rules")
                        .executes(ctx -> rules(ctx.getSource(), null))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> rules(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id")))));
        event.getDispatcher().register(arena);
    }

    /**
     * {@code /arena save <name> [radius]} - writes the build around you to disk.
     *
     * <p>Lands in {@code <world>/generated/abyss_local/structures/<name>.nbt},
     * which is already the right shape and the right place to be copied straight
     * into a datapack.
     */
    private static int save(CommandSourceStack source, String name, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        net.minecraft.core.BlockPos origin = MapStore.originFor(level, player.blockPosition(), radius);
        boolean ok = MapStore.save(level, name, player.blockPosition(), radius);
        if (!ok) {
            source.sendFailure(Component.literal("Could not write that structure to disk."));
            return 0;
        }
        MapScan.Result scan = MapScan.scan(level, around(player, radius));
        source.sendSuccess(() -> Component.literal(
                "§a✔ Saved §f" + name + "§a — " + (radius * 2 + 1) + " blocks across, "
                        + scan.all().size() + " markers, from §7"
                        + origin.getX() + ", " + origin.getY() + ", " + origin.getZ()), true);
        source.sendSuccess(() -> Component.literal(
                "§7Find it in §fgenerated/" + MapStore.LOCAL + "/structures/§7 in your world folder."),
                false);
        return 1;
    }

    /** {@code /arena load <name>} - places a saved build, then checks it over. */
    private static int load(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        net.minecraft.core.BlockPos origin = player.blockPosition();
        int placed = MapStore.load(level, name, origin);
        if (placed < 0) {
            source.sendFailure(Component.literal(
                    "No structure called '" + name + "'. Try the full id, like "
                            + "mypack:my_map/piece_0."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§a✔ Placed §f" + name + "§a at your feet."), true);

        var box = MapStore.boundsOf(level, name, origin);
        if (box != null) {
            java.util.List<String> problems = MapScan.validate(MapScan.scan(level, box));
            if (problems.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§a✔ It validates — this will play."), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "§e" + problems.size() + " thing(s) to fix — run §f/arena validate"), false);
            }
        }
        return 1;
    }

    /**
     * {@code /arena rules [id]} - what is loaded, and what a given round works out to.
     *
     * <p>The per-round readout is the useful half. A scaling curve written as four
     * numbers in a file is impossible to picture; the same curve printed as the
     * health and damage multipliers at rounds 1, 10, 25 and 50 tells you instantly
     * whether you have built a fair fight or a wall.
     */
    private static int rules(CommandSourceStack source, String id) {
        if (id == null) {
            var all = RulesetLoader.all();
            if (all.isEmpty()) {
                source.sendSuccess(() -> Component.literal(
                        "§7No rulesets loaded. Put one at §fdata/<you>/abyss_ruleset/<name>.json§7 "
                                + "in a datapack and run §f/reload§7."), false);
                return 1;
            }
            source.sendSuccess(() -> Component.literal("§6— " + all.size() + " rulesets —"), false);
            all.forEach((key, r) -> source.sendSuccess(() -> Component.literal(
                    "§f" + key + " §8— " + (r.endless ? "endless" : "to round " + r.finalRound)
                            + ", " + r.mobs.size() + " mob types"), false));
            return 1;
        }
        Ruleset r = RulesetLoader.byId(id);
        source.sendSuccess(() -> Component.literal("§6— " + r.id + " —"), false);
        for (int round : new int[]{1, 10, 25, 50}) {
            int n = r.countFor(round);
            String line = String.format(java.util.Locale.ROOT,
                    "§7Round §f%d§7: §f%d§7 mobs, health §f×%.2f§7, damage §f×%.2f§7, breather §f%.1fs",
                    round, n, r.healthMultiplier(round), r.damageMultiplier(round),
                    r.breatherFor(round) / 20.0);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /** {@code /arena play [ruleset] [radius]} - runs a game on the build around you. */
    private static int play(CommandSourceStack source, int radius, String rulesetId) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        String error = EngineArena.start(level, player, radius, rulesetId);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§a✔ Running on §f" + rulesetId + "§a. §7/arena stop to end it."), true);
        return 1;
    }

    /** Drives the engine's own round loop. */
    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EngineArena.tickActive(level);
        }
    }

    /** Pays out for kills made during an engine run. */
    @SubscribeEvent
    public static void onMobKilled(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob mob
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            EngineArena.onKill(mob, killer);
        }
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
