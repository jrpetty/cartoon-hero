package com.voxelia.mmo.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.network.VoxeliaNetwork;
import com.voxelia.mmo.progression.LeaderboardStore;
import com.voxelia.mmo.progression.PrestigeLogic;
import com.voxelia.mmo.progression.Progression;
import com.voxelia.mmo.progression.SkillEffects;
import com.voxelia.mmo.progression.SkillStats;
import com.voxelia.mmo.progression.TalentLogic;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
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
                    .then(Commands.argument("talent", StringArgumentType.word())
                        .executes(VoxeliaCommands::spendTalent)))
                .then(Commands.literal("prestige")
                    .then(Commands.argument("skill", StringArgumentType.word())
                        .executes(VoxeliaCommands::prestige)))
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
            String desc = SkillStats.describe(player, s, level);
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
            StringBuilder sb = new StringBuilder(String.format("%s — %d pts  |  ", s.display(), available));
            var talents = com.voxelia.mmo.skill.Talent.forSkill(s);
            for (int i = 0; i < talents.size(); i++) {
                var t = talents.get(i);
                sb.append(t.display()).append(' ').append(TalentLogic.rank(player, t)).append('/').append(max);
                if (i < talents.size() - 1) sb.append(", ");
            }
            String line = sb.toString();
            src.sendSuccess(() -> Component.literal(line)
                .withStyle(style -> style.withColor(s.color())), false);
        }
        src.sendSuccess(() -> Component.literal("Spend with /voxelia talent <talentId> (e.g. mining_prospector)")
            .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int spendTalent(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        com.voxelia.mmo.skill.Talent talent =
            com.voxelia.mmo.skill.Talent.byId(StringArgumentType.getString(ctx, "talent"));
        if (talent == null) {
            ctx.getSource().sendFailure(Component.literal(
                "Unknown talent. Use /voxelia talents to list them (e.g. mining_prospector)."));
            return 0;
        }
        if (!TalentLogic.spend(player, talent)) {
            ctx.getSource().sendFailure(Component.literal("Can't spend there (no points or max rank reached)."));
            return 0;
        }
        SkillEffects.apply(player);
        VoxeliaNetwork.syncTo(player);
        VoxeliaNetwork.syncTalents(player);
        int newRank = TalentLogic.rank(player, talent);
        ctx.getSource().sendSuccess(() -> Component.literal(
            talent.skill().display() + " " + talent.display() + " is now rank " + newRank + ".")
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int resetTalents(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        TalentLogic.reset(player);
        SkillEffects.apply(player);
        VoxeliaNetwork.syncTo(player);
        VoxeliaNetwork.syncTalents(player);
        ctx.getSource().sendSuccess(() -> Component.literal("All talent points refunded.")
            .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int prestige(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        Skill skill = Skill.byId(StringArgumentType.getString(ctx, "skill"));
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill. Use its id, e.g. combat, mining, fishing."));
            return 0;
        }
        if (!com.voxelia.mmo.config.VoxeliaConfig.prestigeEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Prestige is disabled on this server."));
            return 0;
        }
        int level = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill);
        if (level < SkillCurve.MAX_LEVEL) {
            ctx.getSource().sendFailure(Component.literal(skill.display() + " must reach level "
                + SkillCurve.MAX_LEVEL + " to prestige (currently " + level + ")."));
            return 0;
        }
        if (PrestigeLogic.count(player, skill) >= com.voxelia.mmo.config.VoxeliaConfig.prestigeMax()) {
            ctx.getSource().sendFailure(Component.literal(skill.display() + " is already at max prestige ("
                + com.voxelia.mmo.config.VoxeliaConfig.prestigeMax() + ")."));
            return 0;
        }
        VoxeliaNetwork.handlePrestige(player, skill.ordinal()); // performs the prestige + message + sync
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
        Skill skill = "character".equalsIgnoreCase(skillId) ? null : Skill.byId(skillId);
        if (skill == null && !"character".equalsIgnoreCase(skillId)) {
            ctx.getSource().sendFailure(Component.literal(
                "Unknown skill '" + skillId + "'. Use a skill name, or 'character' for the overall board."));
            return 0;
        }
        ServerPlayer self = ctx.getSource().getPlayer();
        java.util.UUID id = self == null ? null : self.getUUID();
        String heading = skill == null ? "Character" : skill.display();

        // Ranks everyone the server has ever seen, not just who's logged in now.
        var rows = LeaderboardStore.top(skill, 10, id);
        if (rows.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Nobody is ranked yet.")
                .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("=== Top " + heading + " ===")
            .withStyle(ChatFormatting.GOLD), false);
        for (LeaderboardStore.Row row : rows) {
            String stars = row.prestige() > 0 ? " " + "✦".repeat(Math.min(row.prestige(), 3)) : "";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "#" + row.rank() + " " + row.name() + " — level " + row.level() + stars)
                .withStyle(row.self() ? ChatFormatting.AQUA : ChatFormatting.WHITE), false);
        }
        if (id != null) {
            int rank = LeaderboardStore.rankOf(id, skill);
            if (rank > 10) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "You: #" + rank + " — level " + LeaderboardStore.levelFor(id, skill))
                    .withStyle(ChatFormatting.AQUA), false);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Full boards: skills menu ▸ Menu ▸ Leaderboards")
            .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

}
