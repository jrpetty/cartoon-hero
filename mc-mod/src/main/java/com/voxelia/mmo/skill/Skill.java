package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The progression disciplines. {@code color} is the HUD text color; {@code abilityName} is the signature active ability. */
public enum Skill implements StringRepresentable {
    // Every skill has an active (keybind) signature ability; passive perks (Fortune,
    // Last Stand, Well Fed, etc.) still run in the background on top of them.
    MINING("mining", 0xBDBDBD, "Miner's Focus", true, "Miner"),
    FORAGING("foraging", 0x81C784, "Overgrowth", true, "Forager"),
    COMBAT("combat", 0xEF5350, "Frenzy", true, "Warrior"),
    FARMING("farming", 0xFFD54F, "Hearty Meal", true, "Farmer"),
    ACROBATICS("acrobatics", 0x4FC3F7, "Leap", true, "Acrobat"),
    FISHING("fishing", 0x4DD0E1, "Maelstrom", true, "Angler"),
    EXCAVATION("excavation", 0xC8A064, "Excavate", true, "Excavator"),
    DEFENSE("defense", 0x90A4AE, "Bulwark", true, "Guardian"),
    COOKING("cooking", 0xFF8A65, "Feast", true, "Chef"),
    ALCHEMY("alchemy", 0xBA68C8, "Panacea", true, "Alchemist"),
    ARCHERY("archery", 0x8D6E63, "Volley", true, "Marksman");

    public static final Codec<Skill> CODEC = StringRepresentable.fromEnum(Skill::values);

    private final String id;
    private final int color;
    private final String abilityName;
    private final boolean active;
    private final String noun;

    Skill(String id, int color, String abilityName, boolean active, String noun) {
        this.id = id;
        this.color = color;
        this.abilityName = abilityName;
        this.active = active;
        this.noun = noun;
    }

    public String id() { return id; }

    public int color() { return color; }

    /** Display name of the skill's signature ability (active) or passive perk. */
    public String abilityName() { return abilityName; }

    /** True if the ability is a keybind-activated ability; false for passive perks. */
    public boolean active() { return active; }

    /** The title noun for this discipline (e.g. "Miner", "Warrior"). */
    public String noun() { return noun; }

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
