package com.gadgets;

import java.util.List;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Shared tooltip styling: first line gold (what it is), rest gray (controls). */
public final class Tips {
    private Tips() {
    }

    public static void append(List<Text> tooltip, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            tooltip.add(Text.translatable(keys[i]).formatted(i == 0 ? Formatting.GOLD : Formatting.GRAY));
        }
    }
}
