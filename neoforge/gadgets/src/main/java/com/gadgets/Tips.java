package com.gadgets;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Shared tooltip styling: first line gold (what it is), rest gray (controls). */
public final class Tips {
    private Tips() {
    }

    public static void append(List<Component> tooltip, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            tooltip.add(Component.translatable(keys[i]).withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }
}
