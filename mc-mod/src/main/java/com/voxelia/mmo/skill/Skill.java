package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The progression disciplines. {@code color} is the HUD text color; {@code abilityName} is the signature active ability. */
public enum Skill implements StringRepresentable {
    MINING("mining", 0xBDBDBD, "Miner's Focus"),
    FORAGING("foraging", 0x81C784, "Overgrowth"),
    COMBAT("combat", 0xEF5350, "Frenzy"),
    FARMING("farming", 0xFFD54F, "Hearty Meal"),
    ACROBATICS("acrobatics", 0x4FC3F7, "Leap"),
    FISHING("fishing", 0x4DD0E1, "Reel");

    public static final Codec<Skill> CODEC = StringRepresentable.fromEnum(Skill::values);

    private final String id;
    private final int color;
    private final String abilityName;

    Skill(String id, int color, String abilityName) {
        this.id = id;
        this.color = color;
        this.abilityName = abilityName;
    }

    public String id() { return id; }

    public int color() { return color; }

    public String abilityName() { return abilityName; }

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
