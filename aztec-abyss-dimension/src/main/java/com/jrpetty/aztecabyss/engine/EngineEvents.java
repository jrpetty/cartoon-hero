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
        if (event.isCanceled()) {
            return; // something upstream already claimed this click
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof SignBlockEntity sign)) {
            return;
        }
        // Sneaking is left alone so an author can still edit their own sign.
        if (player.isShiftKeyDown()) {
            return;
        }
        // The wand owns the click while it is held. Both this and the wand handler
        // listen for the same event, so without this an author marking out a
        // corner near a shop would set the corner *and* buy whatever the shop was
        // selling - and the wand is in your hand for the whole of map-building.
        if (BuildTools.isWand(player.getMainHandItem())) {
            return;
        }
        if (DealerSign.isDealer(sign)) {
            if (DealerSign.buy(level, player, sign)) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
            return;
        }
        // Everything else the player can walk up to and pay: doors, the box, perk
        // machines, the upgrade bench, supply caches.
        Marker marker = Marker.parse(sign, level.getBlockState(event.getPos()));
        if (marker != null && Machines.use(level, player, marker)) {
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
                .then(Commands.literal("workshop")
                        .executes(ctx -> workshop(ctx.getSource())))
                .then(Commands.literal("wand")
                        .executes(ctx -> giveWand(ctx.getSource())))
                .then(Commands.literal("marker")
                        .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, sb) -> {
                                    for (String k : BuildTools.KINDS) {
                                        sb.suggest(k);
                                    }
                                    return sb.buildFuture();
                                })
                                .executes(ctx -> giveMarker(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind")))))
                .then(Commands.literal("line")
                        .then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8))
                                .then(Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> markerLine(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n"),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> create(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("test")
                        .executes(ctx -> test(ctx.getSource(), "built-in"))
                        .then(Commands.argument("ruleset", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> test(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ruleset")))))
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
                            ServerPlayer p = ctx.getSource().getPlayer();
                            // Testing drops you into survival; ending a test in the
                            // Workshop should hand the build tools straight back
                            // rather than leaving you punching stone.
                            if (p != null && p.level().dimension()
                                    .equals(AztecAbyssConstants.WORKSHOP_LEVEL_KEY)) {
                                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("§7Run stopped."), true);
                            return 1;
                        }))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> setOn(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "key"),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("look")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            if (p == null || !(ctx.getSource().getLevel() instanceof ServerLevel l)) {
                                return 0;
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal(MarkerEdit.describe(l, p)), false);
                            return 1;
                        }))
                .then(Commands.literal("prefab")
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> prefabSave(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("place")
                                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> prefabPlace(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"), 0, ""))
                                        .then(Commands.argument("rotate", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 359))
                                                .executes(ctx -> prefabPlace(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rotate"), ""))
                                                .then(Commands.argument("mirror", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .executes(ctx -> prefabPlace(ctx.getSource(),
                                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rotate"),
                                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mirror"))))))))
                .then(Commands.literal("copy")
                        .executes(ctx -> copy(ctx.getSource())))
                .then(Commands.literal("paste")
                        .executes(ctx -> paste(ctx.getSource(), 0, ""))
                        .then(Commands.argument("rotate", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 359))
                                .executes(ctx -> paste(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rotate"), ""))
                                .then(Commands.argument("mirror", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, sb) -> {
                                            sb.suggest("none");
                                            sb.suggest("x");
                                            sb.suggest("z");
                                            return sb.buildFuture();
                                        })
                                        .executes(ctx -> paste(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rotate"),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mirror"))))))
                .then(Commands.literal("undo")
                        .executes(ctx -> undo(ctx.getSource())))
                .then(Commands.literal("maps")
                        .executes(ctx -> maps(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> info(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("meta")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("field", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, sb) -> {
                                            for (String f : new String[]{"title", "author", "blurb", "difficulty", "ruleset"}) {
                                                sb.suggest(f);
                                            }
                                            return sb.buildFuture();
                                        })
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> meta(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "field"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "value")))))))
                .then(Commands.literal("mobs")
                        .executes(ctx -> mobs(ctx.getSource(), ""))
                        .then(Commands.argument("filter", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> mobs(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "filter")))))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            EngineArena a = EngineArena.active();
                            if (a == null) {
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§7No run in progress. §f/arena play§7 to start one."), false);
                                return 1;
                            }
                            for (String line : a.status()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("director")
                        .executes(ctx -> {
                            EngineArena a = EngineArena.active();
                            if (a == null) {
                                ctx.getSource().sendFailure(Component.literal("No run in progress."));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal(a.director().describe()), false);
                            return 1;
                        }))
                .then(Commands.literal("rules")
                        .executes(ctx -> rules(ctx.getSource(), null))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> rules(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id")))));
        event.getDispatcher().register(arena);

        // Its own root, deliberately. Everything under /arena is authoring and is
        // gated behind op; joining a game is the one thing an ordinary player
        // needs to be able to do, and a co-op command only ops can run is not one.
        event.getDispatcher().register(Commands.literal("arenajoin")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) {
                        return 0;
                    }
                    String error = EngineArena.joinRun(p);
                    if (error != null) {
                        ctx.getSource().sendFailure(Component.literal(error));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("§a✔ You are in."), false);
                    return 1;
                }));

        // Its own root and deliberately not op-gated, for the same reason as
        // /arenajoin: the whole point of a password is that it lets in somebody
        // who is not an operator. A command only ops can run cannot do that.
        event.getDispatcher().register(Commands.literal("creator")
                .executes(ctx -> enterCreator(ctx.getSource()))
                .then(Commands.literal("lock")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("who", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer who = net.minecraft.commands.arguments.EntityArgument
                                            .getPlayer(ctx, "who");
                                    MapCreator.lock(who);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "§6" + who.getGameProfile().getName()
                                                    + " §7will need the password again."), true);
                                    return 1;
                                })))
                .then(Commands.argument("password",
                                com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            if (p == null) {
                                return 0;
                            }
                            String given = com.mojang.brigadier.arguments.StringArgumentType
                                    .getString(ctx, "password");
                            if (!MapCreator.unlock(p, given)) {
                                ctx.getSource().sendFailure(Component.literal("That is not the word."));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§a✔ Map Creator is open to you. §7You will not need to type it again."), false);
                            return enterCreator(ctx.getSource());
                        })));
    }

    /** {@code /creator} - into the Workshop, if this player is allowed. */
    private static int enterCreator(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        String error = MapCreator.enter(player, true);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
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
                            + ", " + r.mobs.size() + " mob types, "
                            + Script.ruleCount(key) + " script rules"), false));
            return 1;
        }
        Ruleset r = RulesetLoader.byId(id);
        source.sendSuccess(() -> Component.literal("§6— " + r.id + " —"), false);
        for (String unknown : r.warnings) {
            source.sendSuccess(() -> Component.literal(
                    "§e⚠ unrecognised key §f" + unknown
                            + " §7— check the spelling; it is being ignored"), false);
        }
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

    /** {@code /arena set <key> <value>} - retunes the marker you are looking at. */
    private static int setOn(CommandSourceStack source, String key, String value) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        String result = MarkerEdit.set(level, player, key, value);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    /**
     * {@code /arena prefab save <name>} - the selection, kept as a reusable part.
     *
     * <p>The clipboard solves symmetry within one sitting; this solves reuse
     * across all of them. A shop kiosk, a gate assembly, a stair block worth
     * copying - built once, named, and stamped into every map afterwards. It is
     * the difference between a tool and a library.
     */
    private static int prefabSave(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        var box = BuildTools.selectionOf(player);
        if (box == null) {
            source.sendFailure(Component.literal("Pick both corners with the Map Wand first."));
            return 0;
        }
        if (BuildTools.volumeOf(box) > Clipboard.MAX_VOLUME) {
            source.sendFailure(Component.literal(
                    "That is too big for a part — prefabs are meant to be pieces, not maps."));
            return 0;
        }
        if (!MapStore.savePrefab(level, name, box)) {
            source.sendFailure(Component.literal("Could not write that prefab."));
            return 0;
        }
        MapScan.Result scan = MapScan.scan(level, box);
        source.sendSuccess(() -> Component.literal(
                "§a✔ Prefab §f" + name + "§a — " + BuildTools.spanText(box)
                        + (scan.all().isEmpty() ? "" : ", " + scan.all().size() + " markers included")), true);
        return 1;
    }

    /** {@code /arena prefab place <name> [rotate] [mirror]}. */
    private static int prefabPlace(CommandSourceStack source, String name, int degrees, String mirror) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        int placed = MapStore.placePrefab(level, name, player.blockPosition(),
                Clipboard.rotationOf(degrees), Clipboard.mirrorOf(mirror));
        if (placed < 0) {
            source.sendFailure(Component.literal("No prefab called '" + name + "'."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§a✔ Placed §f" + name + (degrees != 0 ? "§a rotated " + degrees + "°" : "")), false);
        return 1;
    }

    /** {@code /arena copy} - the wand selection into your clipboard. */
    private static int copy(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        var box = BuildTools.selectionOf(player);
        if (box == null) {
            source.sendFailure(Component.literal("Pick both corners with the Map Wand first."));
            return 0;
        }
        if (!Clipboard.copy(level, player, box)) {
            source.sendFailure(Component.literal(
                    "That selection is too large to copy — keep it under "
                            + Clipboard.MAX_VOLUME + " blocks."));
            return 0;
        }
        var size = Clipboard.sizeOf(player);
        source.sendSuccess(() -> Component.literal(
                "§a✔ Copied §f" + size.getX() + " × " + size.getY() + " × " + size.getZ()), false);
        return 1;
    }

    /**
     * {@code /arena paste [rotate] [mirror]} - places it at your feet.
     *
     * <p>Rotation and mirroring are vanilla's own structure settings, so a pasted
     * corner behaves exactly like a loaded map: stairs turn the right way and
     * signs keep their text, with no second code path to disagree.
     */
    private static int paste(CommandSourceStack source, int degrees, String mirror) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        int placed = Clipboard.paste(level, player, player.blockPosition(),
                Clipboard.rotationOf(degrees), Clipboard.mirrorOf(mirror));
        if (placed < 0) {
            source.sendFailure(Component.literal("Nothing copied yet — /arena copy first."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§a✔ Pasted" + (degrees != 0 ? " rotated " + degrees + "°" : "")
                        + (mirror.isEmpty() || mirror.equals("none") ? "" : " mirrored " + mirror)
                        + " §8— /arena undo to take it back"), false);
        return 1;
    }

    private static int undo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        if (!Clipboard.undo(level, player)) {
            source.sendFailure(Component.literal("Nothing to undo."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a✔ Put back."), false);
        return 1;
    }

    /**
     * {@code /arena mobs [filter]} - every entity a spawner or mob table can use.
     *
     * <p>Spawners accept any entity id, which is generous and completely
     * undiscoverable: there was no way to find out what to type, and a wrong guess
     * failed silently. Listing them turns "what can I put in this" from guesswork
     * into a question with an answer, and includes anything another mod has added
     * because it reads the live registry rather than a list I wrote down.
     */
    private static int mobs(CommandSourceStack source, String filter) {
        String needle = filter.toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> found = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.EntityType<?> type
                : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            // Living things only. Listing arrows and boats would bury the answer.
            if (type.getCategory() == net.minecraft.world.entity.MobCategory.MISC) {
                continue;
            }
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(type).toString();
            if (needle.isEmpty() || id.contains(needle)) {
                found.add(id);
            }
        }
        java.util.Collections.sort(found);
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§7Nothing matches §f" + filter + "§7."), false);
            return 1;
        }
        int shown = Math.min(found.size(), 40);
        source.sendSuccess(() -> Component.literal("§6— " + found.size()
                + (needle.isEmpty() ? " spawnable entities" : " matching '" + filter + "'")
                + (found.size() > shown ? ", first " + shown : "") + " —"), false);
        for (int i = 0; i < shown; i++) {
            String id = found.get(i);
            source.sendSuccess(() -> Component.literal("§7" + id), false);
        }
        if (found.size() > shown) {
            source.sendSuccess(() -> Component.literal(
                    "§8Narrow it with §f/arena mobs <part of a name>"), false);
        }
        return 1;
    }

    /** {@code /arena maps} - every map this world knows about. */
    private static int maps(CommandSourceStack source) {
        if (source.getServer() == null) {
            return 0;
        }
        var local = MapManifest.listAll(source.getServer());
        var packs = ArenaLoader.all();
        if (local.isEmpty() && packs.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§7No maps yet. Build one, then §f/arena create <name>§7."), false);
            return 1;
        }
        if (!local.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§6— made here —"), false);
            for (MapManifest m : local) {
                source.sendSuccess(() -> Component.literal(m.line()), false);
            }
        }
        // Datapack maps are listed apart from local ones on purpose: one set you
        // can edit and re-save, the other arrived from somebody else and will be
        // replaced wholesale next time they update it.
        if (!packs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§6— from datapacks —"), false);
            packs.forEach((key, m) -> source.sendSuccess(
                    () -> Component.literal(m.line() + " §8[" + key + "]"), false));
        }
        return 1;
    }

    private static int info(CommandSourceStack source, String name) {
        if (source.getServer() == null) {
            return 0;
        }
        MapManifest m = MapManifest.load(source.getServer(), name);
        if (m == null) {
            m = ArenaLoader.manifestFor(name);
        }
        if (m == null) {
            source.sendFailure(Component.literal("No map called '" + name + "'."));
            return 0;
        }
        final MapManifest found = m;
        source.sendSuccess(() -> Component.literal("§6" + found.title() + " §8v" + found.version()), false);
        source.sendSuccess(() -> Component.literal("§7by §f" + found.author()), false);
        source.sendSuccess(() -> Component.literal("§7" + found.blurb()), false);
        source.sendSuccess(() -> Component.literal(
                "§7Difficulty §f" + found.difficulty() + "§7, ruleset §f" + found.ruleset()), false);
        return 1;
    }

    /**
     * {@code /arena meta <name> <field> <value>} - edits a map's details.
     *
     * <p>Bumps the version on every change, because a map that has been shared and
     * then revised needs to be able to say so; two files with the same name and
     * different contents is the oldest problem in map distribution.
     */
    private static int meta(CommandSourceStack source, String name, String field, String value) {
        if (source.getServer() == null) {
            return 0;
        }
        MapManifest m = MapManifest.load(source.getServer(), name);
        if (m == null) {
            source.sendFailure(Component.literal("No map called '" + name + "'."));
            return 0;
        }
        MapManifest updated = m.with(field, value).bumped();
        MapManifest.save(source.getServer(), updated);
        source.sendSuccess(() -> Component.literal(
                "§a✔ " + field + " → §f" + value + " §8(now v" + updated.version() + ")"), true);
        return 1;
    }

    /** {@code /arena workshop} - into the empty lit void, in creative. */
    private static int workshop(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        // Shares one implementation with the Map Creator mode on the picker, so
        // the command and the menu can never drift into arriving somewhere
        // different or on a pad only one of them builds.
        String error = MapCreator.enter(player, false);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int giveWand(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        if (!player.getInventory().add(BuildTools.wand())) {
            player.drop(BuildTools.wand(), false);
        }
        source.sendSuccess(() -> Component.literal(
                "§6Map Wand. §7Left-click one corner, right-click the other."), false);
        return 1;
    }

    private static int giveMarker(CommandSourceStack source, String kind) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        String clean = kind.toLowerCase(java.util.Locale.ROOT);
        // Dealers stay signs. A dealer is the one marker a player is meant to walk
        // up to and read, so making it invisible would hide the shop rather than
        // tidy the map. Everything else is an instruction to the engine and has no
        // business being visible at all.
        net.minecraft.world.item.ItemStack stack = clean.equals("dealer")
                ? BuildTools.markerSign(clean, BuildTools.hintFor(clean))
                : BuildTools.markerBlock(clean, BuildTools.hintFor(clean));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal(
                "§6[" + clean + "] §7marker — place it, that is the whole step."), false);
        if (!clean.equals("dealer")) {
            source.sendSuccess(() -> Component.literal(
                    "§8Invisible in survival. §f/arena line <n> <text>§8 to write past a sign's limits."), false);
        }
        return 1;
    }

    /**
     * {@code /arena line <n> <text>} - writes one line of the marker you are facing.
     *
     * <p>This is where the character limit actually goes away. The sign's cap was
     * never in the format, it was in the editing screen; a command argument has no
     * such cap, so a spawner's whole configuration goes on one line and stays
     * readable.
     */
    private static int markerLine(CommandSourceStack source, int index, String text) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        com.jrpetty.aztecabyss.block.MarkerBlockEntity be = lookedAtMarker(player, level);
        if (be == null) {
            source.sendFailure(Component.literal(
                    "Look at a Marker Block. (Signs are edited by hand, as always.)"));
            return 0;
        }
        be.setLine(index - 1, text);
        source.sendSuccess(() -> Component.literal(
                "§6Line " + index + " §7= §f" + text), false);
        return 1;
    }

    /** The Marker Block a player is pointing at, within reach, or null. */
    static com.jrpetty.aztecabyss.block.MarkerBlockEntity lookedAtMarker(ServerPlayer player, ServerLevel level) {
        net.minecraft.world.phys.HitResult hit = player.pick(6.0, 0.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr)) {
            return null;
        }
        return level.getBlockEntity(bhr.getBlockPos())
                instanceof com.jrpetty.aztecabyss.block.MarkerBlockEntity be ? be : null;
    }

    /**
     * {@code /arena create <name>} - turns the wand selection into a map file.
     *
     * <p>Validates before it writes. Saving a map with no spawn point is a file
     * that will disappoint someone later, and the moment to say so is now.
     */
    private static int create(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        var box = BuildTools.selectionOf(player);
        if (box == null) {
            source.sendFailure(Component.literal(
                    "Pick both corners with the Map Wand first — /arena wand."));
            return 0;
        }
        long volume = BuildTools.volumeOf(box);
        if (volume > 8_000_000L) {
            source.sendFailure(Component.literal(
                    "That selection is " + volume + " blocks. Keep it under 8,000,000."));
            return 0;
        }
        MapScan.Result scan = MapScan.scan(level, box);
        java.util.List<String> problems = MapScan.validate(scan);

        if (!MapStore.saveRegion(level, name, box)) {
            source.sendFailure(Component.literal("Could not write that map to disk."));
            return 0;
        }
        // Re-saving an existing map keeps its details and bumps the version; a
        // first save mints one. Overwriting a title someone wrote because they
        // pressed save again would be the rudest possible behaviour here.
        MapManifest existing = MapManifest.load(source.getServer(), name);
        MapManifest manifest = existing != null
                ? existing.bumped()
                : MapManifest.fresh(name, player.getGameProfile().getName());
        MapManifest.save(source.getServer(), manifest);

        source.sendSuccess(() -> Component.literal(
                "§a✔ Created §f" + manifest.title() + "§a v" + manifest.version()
                        + " — " + BuildTools.spanText(box)
                        + ", " + scan.all().size() + " markers."), true);
        source.sendSuccess(() -> Component.literal(
                "§7Written to §fgenerated/" + MapStore.LOCAL + "/structures/" + name + ".nbt"), false);
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§a✔ It validates. §7/arena test to play it."), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "§e" + problems.size() + " thing(s) to fix before it plays well:"), false);
            for (String p : problems) {
                source.sendSuccess(() -> Component.literal(" " + p), false);
            }
        }
        return 1;
    }

    /** {@code /arena test} - play the selection exactly as the mod would run it. */
    private static int test(CommandSourceStack source, String rulesetId) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        var box = BuildTools.selectionOf(player);
        if (box == null) {
            source.sendFailure(Component.literal(
                    "No selection. Mark the map out with the Map Wand first."));
            return 0;
        }
        String error = EngineArena.startIn(level, player, box, rulesetId);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        source.sendSuccess(() -> Component.literal(
                "§a✔ Testing on §f" + rulesetId + "§a. §7/arena stop to end and go back to building."), true);
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

    /** Left-click with the wand: first corner. Cancelled so it does not break blocks. */
    @SubscribeEvent
    public static void onWandLeft(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && BuildTools.isWand(player.getMainHandItem())) {
            BuildTools.setCorner(player, event.getPos(), true);
            event.setCanceled(true);
        }
    }

    /** Right-click with the wand: second corner. */
    @SubscribeEvent
    public static void onWandRight(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && BuildTools.isWand(player.getMainHandItem())) {
            BuildTools.setCorner(player, event.getPos(), false);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /** Drives the engine's own round loop. */
    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EngineArena.tickActive(level);
        }
    }

    /**
     * Insta-Kill: anything in the wave dies to one hit while it is up.
     *
     * <p>Uses the same event the rest of the mod already hooks for damage, rather
     * than a second one that might behave differently. Raising the amount past the
     * target's maximum health is deliberate - overkill rather than a flag - so it
     * works on anything a map cares to spawn without the engine needing to know
     * what that is.
     */
    @SubscribeEvent
    public static void onEngineDamage(
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!mob.getPersistentData().getBoolean("aztecabyss_engine_mob")) {
            return;
        }
        if (EnginePowerUps.instaKill(level)
                && event.getSource().getEntity() instanceof ServerPlayer) {
            event.setAmount(Math.max(event.getAmount(), mob.getMaxHealth() * 2.0F));
        }
    }

    /**
     * Turns a killing blow on a participant into going down.
     *
     * <p>Intercepted before the damage lands rather than on death, because once a
     * player is dead their inventory has dropped, their position has moved and
     * putting them back is a reconstruction. Cancelling the blow means nothing has
     * happened yet and there is nothing to undo.
     *
     * <p>A player already on the floor takes no further damage at all. Bleeding out
     * is a clock, not a health bar, and a stray hit shortening it would make going
     * near the horde to help pointless.
     */
    @SubscribeEvent
    public static void onPlayerDown(
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EngineArena arena = EngineArena.active();
        if (arena == null || !EngineArena.isRunning()) {
            return;
        }
        if (arena.isDowned(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (event.getAmount() < player.getHealth()) {
            return;
        }
        if (arena.canGoDown(player)) {
            event.setCanceled(true);
            arena.goDown(player);
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
