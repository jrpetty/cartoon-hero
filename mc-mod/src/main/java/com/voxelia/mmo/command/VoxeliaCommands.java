package com.voxelia.mmo.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.network.VoxeliaNetwork;
import com.voxelia.mmo.progression.Progression;
import com.voxelia.mmo.progression.SkillEffects;
import com.voxelia.mmo.progression.SkillStats;
import com.voxelia.mmo.progression.TalentLogic;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import com.voxelia.mmo.skill.TalentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /voxelia skills  and  /voxelia grant <skill> <amount> (op). */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class VoxeliaCommands {
    private VoxeliaCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("voxelia")
                .then(Commands.literal("skills").executes(VoxeliaCommands::showSkills))
                .then(Commands.literal("stats").executes(VoxeliaCommands::stats))
                .then(Commands.literal("bestiary").executes(VoxeliaCommands::bestiary))
                .then(Commands.literal("talents").executes(VoxeliaCommands::showTalents))
                .then(Commands.literal("talent")
                    .then(Commands.literal("reset").executes(VoxeliaCommands::resetTalents))
                    .then(Commands.argument("skill", StringArgumentType.word())
                        .then(Commands.argument("talent", StringArgumentType.word())
                            .executes(VoxeliaCommands::spendTalent))))
                .then(Commands.literal("grant")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("skill", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(VoxeliaCommands::grant))))
                .then(Commands.literal("top")
                    .then(Commands.argument("skill", StringArgumentType.word())
                        .executes(VoxeliaCommands::top)))
        );
    }

    private static int showSkills(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        ctx.getSource().sendSuccess(() -> Component
            .literal("=== Voxelia Skills (Lv " + skills.characterLevel() + ") ===")
            .withStyle(ChatFormatting.GOLD), false);
        for (Skill s : Skill.values()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                s.display() + ": level " + skills.getLevel(s) + "  (" + skills.getXp(s) + " xp)"), false);
        }
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        CommandSourceStack src = ctx.getSource();
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());

        src.sendSuccess(() -> Component.literal(
            "=== Voxelia Stats — " + player.getGameProfile().getName()
                + " (Character Lv " + skills.characterLevel() + ", " + skills.totalLevels() + " total) ===")
            .withStyle(ChatFormatting.GOLD), false);

        for (Skill s : Skill.values()) {
            int xp = skills.getXp(s);
            int level = skills.getLevel(s);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);
            String progress = span > 0 ? into + "/" + span + " xp" : "MAX";
            String desc = SkillStats.describe(s, level);
            String tag = s.active() ? " [active: " + s.abilityName() + "]" : " [passive: " + s.abilityName() + "]";
            src.sendSuccess(() -> Component.literal("")
                .append(Component.literal(s.display() + " ").withStyle(style -> style.withColor(s.color())))
                .append(Component.literal("Lv " + level + "  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("(" + progress + ")").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(tag).withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("  " + desc).withStyle(ChatFormatting.GRAY)), false);
        }

        double loss = com.voxelia.mmo.config.VoxeliaConfig.deathXpLossPercent();
        if (loss > 0) {
            src.sendSuccess(() -> Component.literal(
                String.format(java.util.Locale.ROOT, "On death you lose %.0f%% of each skill's XP.", loss * 100))
                .withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    private static int bestiary(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        CommandSourceStack src = ctx.getSource();
        com.voxelia.mmo.skill.MobMastery mastery = player.getData(VoxeliaAttachments.MOB_MASTERY.get());

        var entries = new java.util.ArrayList<>(mastery.kills().entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        src.sendSuccess(() -> Component.literal("=== Bestiary ===").withStyle(ChatFormatting.GOLD), false);
        if (entries.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No kills recorded yet.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        double dmgPerTier = com.voxelia.mmo.config.VoxeliaConfig.masteryDamagePerTier();
        int shown = Math.min(15, entries.size());
        for (int i = 0; i < shown; i++) {
            var entry = entries.get(i);
            String key = entry.getKey();
            String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
            int kills = entry.getValue();
            int tier = mastery.tier(key);
            String line = String.format(java.util.Locale.ROOT, "%s — %d kills (tier %d, +%.0f%% dmg)",
                name, kills, tier, tier * dmgPerTier * 100);
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int showTalents(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        CommandSourceStack src = ctx.getSource();
        int max = com.voxelia.mmo.config.VoxeliaConfig.talentMaxRank();
        src.sendSuccess(() -> Component.literal("=== Talents (max rank " + max + ") ===")
            .withStyle(ChatFormatting.GOLD), false);
        for (Skill s : Skill.values()) {
            int available = TalentLogic.pointsAvailable(player, s);
            int prodigy = TalentLogic.rank(player, s, TalentType.PRODIGY);
            int mastery = TalentLogic.rank(player, s, TalentType.MASTERY);
            String line = String.format("%s — %d pts  |  Prodigy %d/%d, Mastery %d/%d",
                s.display(), available, prodigy, max, mastery, max);
            src.sendSuccess(() -> Component.literal(line)
                .withStyle(style -> style.withColor(s.color())), false);
        }
        src.sendSuccess(() -> Component.literal("Spend with /voxelia talent <skill> <prodigy|mastery>")
            .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int spendTalent(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        Skill skill = Skill.byId(StringArgumentType.getString(ctx, "skill"));
        TalentType type = TalentType.byId(StringArgumentType.getString(ctx, "talent"));
        if (skill == null || type == null) {
            ctx.getSource().sendFailure(Component.literal("Usage: /voxelia talent <skill> <prodigy|mastery>"));
            return 0;
        }
        if (!TalentLogic.spend(player, skill, type)) {
            ctx.getSource().sendFailure(Component.literal("Can't spend there (no points or max rank reached)."));
            return 0;
        }
        SkillEffects.apply(player);
        VoxeliaNetwork.syncTo(player);
        int newRank = TalentLogic.rank(player, skill, type);
        ctx.getSource().sendSuccess(() -> Component.literal(
            skill.display() + " " + type.display() + " is now rank " + newRank + ".")
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int resetTalents(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        TalentLogic.reset(player);
        SkillEffects.apply(player);
        VoxeliaNetwork.syncTo(player);
        ctx.getSource().sendSuccess(() -> Component.literal("All talent points refunded.")
            .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String skillId = StringArgumentType.getString(ctx, "skill");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        Skill skill = Skill.byId(skillId);
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal(
                "Unknown skill '" + skillId + "'. Use: mining, foraging, combat, farming."));
            return 0;
        }
        Progression.grant(player, skill, amount);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Granted " + amount + " " + skill.display() + " XP."), true);
        return 1;
    }

    private static int top(CommandContext<CommandSourceStack> ctx) {
        String skillId = StringArgumentType.getString(ctx, "skill");
        Skill skill = Skill.byId(skillId);
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal(
                "Unknown skill '" + skillId + "'. Use: mining, foraging, combat, farming, acrobatics, fishing."));
            return 0;
        }
        var players = new java.util.ArrayList<>(ctx.getSource().getServer().getPlayerList().getPlayers());
        players.sort((a, b) -> Integer.compare(
            b.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill),
            a.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill)));
        ctx.getSource().sendSuccess(() -> Component.literal(
            "=== Top " + skill.display() + " ===").withStyle(ChatFormatting.GOLD), false);
        int shown = Math.min(10, players.size());
        for (int i = 0; i < shown; i++) {
            ServerPlayer p = players.get(i);
            int lvl = p.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill);
            int rank = i + 1;
            ctx.getSource().sendSuccess(() -> Component.literal(
                rank + ". " + p.getGameProfile().getName() + " — level " + lvl), false);
        }
        return 1;
    }
}
