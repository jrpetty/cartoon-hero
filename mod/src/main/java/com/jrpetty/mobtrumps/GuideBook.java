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

            Kill any mob for a §ochance§r at its card. Rarer mobs give it up far more readily, because you meet them far less often:

            §7Common§r 1 in 20
            §aUncommon§r 1 in 10
            §bRare§r 1 in 5
            §5Epic§r 1 in 2
            §6Legendary§r §lalways§r

            Every boss is Legendary, so a boss always pays.""",

            """
            §l§6Cold streaks§r

            The dice can be cruel, so they're not the only rule.

            Go long enough hunting one mob with nothing to show — about §othree times§r the usual wait — and its next card is §lguaranteed§r.

            That counter resets every time a card drops, so it only ever rescues a genuinely unlucky run.""",

            """
            §l§6Unlocking holos§r

            Kills always count, even when no card drops — a dry spell is never wasted effort.

            Kill lots of one mob to unlock its §dholographic§r card, which is stat-boosted.

            Kills needed: common 100, uncommon 75, rare 25, epic 10, legendary 5.""",

            """
            §l§6Holographics§r

            A holo boosts what that mob is §oknown for§r — every card gets:

            §c+2§r on its speciality stat
            §a+1§r on its next three defining stats
            (+5 total, capped at 10 each)

            A Creeper's holo hits harder; a Chicken's farms better. Everyone's holo of a card is identical.

            Press 4 duplicates into a foil with §e/mobtrumps foil§r.""",

            """
            §l§6Battling the CPU§r

            §e/mobtrumps battle§r deals a deck to you and the CPU.

            On your turn, pick a stat; higher value wins both cards. Win them all!

            Difficulty: §e/mobtrumps battle easy|normal|hard§r. Hard plays the odds and bluffs.""",

            """
            §l§6The Rarity rule§r

            Five stats work how you'd expect: the §obigger§r number takes the round.

            §cRarity is the exception.§r It is how §ocommon§r the mob is — 10 is a Chicken, 1 is the Warden — so the §lLOWER§r§c number wins§r.

            Look for the small §7▼§r beside it on the card.""",

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

            Tabs on the top edge open §6Awards§r and §bSettings§r.""",

            """
            §l§6Awards§r

            The back of the book lists every award: collecting, the arena, duels and hunting.

            Nothing is paid out for you — an earned award shows §aCollect§r, and the reward drops into your inventory.

            Finish a whole §oset§r and you also pick §oone§r spawn egg from it. One choice, forever.""",

            """
            §l§6Settings§r

            The last leaf is yours: hide the hunt counter, resize battle cards, brighten the arena, calm the motion.

            They only change how the mod §olooks§r, never how it plays, and they're saved on your computer.

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
