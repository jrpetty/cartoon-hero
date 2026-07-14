package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/** Hands the player a written "How to Play" book explaining every mechanic. */
public final class GuideBook {

    private GuideBook() {
    }

    private static final String[] PAGES = {
            """
            §l§2MOB TRUMPS§r

            A Top Trumps card game for all 81 Minecraft mobs.

            Collect cards, build a deck, and battle the CPU or your friends.

            Turn the page to learn how →""",

            """
            §l§6Collecting cards§r

            Kill any mob and it drops §oits own card§r, 100% of the time.

            Kill lots of one mob to unlock its §dholographic§r card, which is stat-boosted.

            Kills needed: common 100, uncommon 75, rare 25, epic 10, legendary 5.""",

            """
            §l§6Holographics§r

            A holo card gets a fixed boost, the same for everyone:

            §a+1 Health§r
            §c+2 Attack§r
            §b+1 Speed§r
            (each capped at 10)

            Press 4 duplicate cards into a foil with §e/mobtrumps foil§r.""",

            """
            §l§6Battling the CPU§r

            §e/mobtrumps battle§r deals a deck to you and the CPU.

            On your turn, pick a stat; higher value wins both cards. Win them all!

            Difficulty: §e/mobtrumps battle easy|normal|hard§r. Hard plays the odds and bluffs.""",

            """
            §l§6Duels§r

            §e/mobtrumps duel <player>§r challenges a friend.

            Add §ewager§r (hold a card) or §ebet <emeralds>§r to play for stakes.

            §e/mobtrumps queue§r auto-matches you. §e/mobtrumps rematch§r replays.""",

            """
            §l§6Spectating§r

            §e/mobtrumps watch <player>§r follows a live duel.

            While watching, §e/mobtrumps sidebet <player> <emeralds>§r backs a duelist — winners split the pool.

            Cheer with §e/mobtrumps emote gg§r.""",

            """
            §l§6Decks§r

            Open your §9Collection Book§r → §aDeck§r to pick your cards, then §e/mobtrumps battle deck§r.

            Share a deck: §e/mobtrumps export§r gives a code; §e/mobtrumps import <code>§r loads one.

            Owned holos play boosted in your deck.""",

            """
            §l§6Collection Book§r

            Craft it: §obook + emerald§r.

            It tracks all 81 cards and acts as a binder — hit §aStore§r to file loose cards away and tidy your inventory.

            §e/mobtrumps top§r shows the ranked ladder. Good luck!"""
    };

    public static int give(ServerPlayer player) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = new ArrayList<>();
        for (String page : PAGES) {
            pages.add(Filterable.passThrough(Component.literal(page)));
        }
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("Mob Trumps Guide"), "Mob Trumps", 0, pages, false);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        CardActions.give(player, book);
        player.sendSystemMessage(Component.literal("Here's your Mob Trumps guide — right-click to read it.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
