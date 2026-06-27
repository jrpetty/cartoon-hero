package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The progression disciplines. {@code color} is the HUD text color; {@code abilityName} is the signature active ability. */
public enum Skill implements StringRepresentable {
    // Original six: active (keybind) abilities. New ones: passive signatures.
    MINING("mining", 0xBDBDBD, "Miner's Focus", true),
    FORAGING("foraging", 0x81C784, "Overgrowth", true),
    COMBAT("combat", 0xEF5350, "Frenzy", true),
    FARMING("farming", 0xFFD54F, "Hearty Meal", true),
    ACROBATICS("acrobatics", 0x4FC3F7, "Leap", true),
    FISHING("fishing", 0x4DD0E1, "Reel", true),
    EXCAVATION("excavation", 0xC8A064, "Prospector", false),
    DEFENSE("defense", 0x90A4AE, "Last Stand", false),
    COOKING("cooking", 0xFF8A65, "Well Fed", false),
    ALCHEMY("alchemy", 0xBA68C8, "Lingering", false),
    ARCHERY("archery", 0x8D6E63, "Power Shot", false);

    public static final Codec<Skill> CODEC = StringRepresentable.fromEnum(Skill::values);

    private final String id;
    private final int color;
    private final String abilityName;
    private final boolean active;

    Skill(String id, int color, String abilityName, boolean active) {
        this.id = id;
        this.color = color;
        this.abilityName = abilityName;
        this.active = active;
    }

    public String id() { return id; }

    public int color() { return color; }

    /** Display name of the skill's signature ability (active) or passive perk. */
    public String abilityName() { return abilityName; }

    /** True if the ability is a keybind-activated ability; false for passive perks. */
    public boolean active() { return active; }

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
