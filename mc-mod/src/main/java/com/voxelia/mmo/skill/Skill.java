package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The four progression disciplines. {@code color} is the HUD text color. */
public enum Skill implements StringRepresentable {
    MINING("mining", 0xBDBDBD),
    FORAGING("foraging", 0x81C784),
    COMBAT("combat", 0xEF5350),
    FARMING("farming", 0xFFD54F);

    public static final Codec<Skill> CODEC = StringRepresentable.fromEnum(Skill::values);

    private final String id;
    private final int color;

    Skill(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() { return id; }

    public int color() { return color; }

    public String display() {
        return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
    }

    @Override
    public String getSerializedName() { return id; }

    public static Skill byId(String s) {
        for (Skill sk : values()) {
            if (sk.id.equalsIgnoreCase(s)) return sk;
        }
        return null;
    }
}
