#!/usr/bin/env python3
"""Draft the mob trait table, check it, then emit Java."""

T = {}   # trait -> set of mob ids


def t(name, ids):
    T[name] = set(ids.split())


t("FLIES", """
allay bat bee blaze breeze ender_dragon ghast parrot phantom vex wither
""")

t("HOSTILE", """
blaze bogged breeze cave_spider creaking creeper drowned elder_guardian
ender_dragon endermite evoker ghast guardian hoglin husk magma_cube phantom
piglin_brute pillager ravager shulker silverfish skeleton slime spider stray
sulfur_cube vex vindicator warden witch wither wither_skeleton zoglin zombie
zombie_villager zombified_piglin enderman piglin
""")

t("RANGED", """
blaze bogged breeze ender_dragon ghast guardian elder_guardian llama
trader_llama pillager shulker skeleton snow_golem stray witch wither
""")

t("TAMEABLE", """
cat donkey horse llama mule parrot wolf
""")

t("RIDEABLE", """
camel donkey horse mule pig skeleton_horse strider
""")

t("SWIMS", """
axolotl cod dolphin drowned elder_guardian frog glow_squid guardian pufferfish
salmon squid tadpole tropical_fish turtle
""")

t("SHEARABLE", """
sheep mooshroom snow_golem
""")

t("GLOWS", """
allay blaze glow_squid magma_cube sulfur_cube warden
""")

t("COLD", """
stray polar_bear snow_golem goat
""")

t("BABY", """
armadillo camel cat chicken cow donkey drowned fox goat hoglin horse husk llama
mooshroom mule ocelot panda pig piglin polar_bear rabbit sheep sniffer strider
trader_llama turtle villager wolf zoglin zombie zombie_villager zombified_piglin
""")

t("ARTHROPOD", """
bee cave_spider endermite silverfish spider
""")

t("HUMANOID", """
allay bogged drowned evoker husk iron_golem piglin piglin_brute pillager
skeleton snow_golem stray vex villager vindicator wandering_trader warden witch
wither_skeleton zombie zombie_villager zombified_piglin
""")

t("TRADES", """
villager wandering_trader piglin
""")

t("NOCTURNAL", """
bogged drowned phantom skeleton stray zombie zombie_villager
""")

LABEL = {
    "FLIES": "Does it fly?",
    "HOSTILE": "Is it hostile?",
    "RANGED": "Does it attack from range?",
    "TAMEABLE": "Can it be tamed?",
    "RIDEABLE": "Can you ride it?",
    "SWIMS": "Does it live in water?",
    "SHEARABLE": "Can you shear it?",
    "GLOWS": "Does it give off light?",
    "COLD": "Does it live somewhere cold?",
    "BABY": "Does it have a baby form?",
    "ARTHROPOD": "Is it a bug?",
    "HUMANOID": "Does it stand on two legs?",
    "TRADES": "Can you trade with it?",
    "NOCTURNAL": "Does it burn in daylight?",
}

if __name__ == "__main__":
    import re
    import sys
    src = open("/home/user/cartoon-hero/mod/src/main/java/com/jrpetty/mobtrumps/"
               "game/MobCards.java").read()
    ids = re.findall(r'card\("([a-z_]+)"', src)
    known = set(ids)

    bad = False
    for name, members in T.items():
        stray = members - known
        if stray:
            print("ERROR %s names unknown mobs: %s" % (name, sorted(stray)))
            bad = True
    if bad:
        sys.exit(1)

    print("%-11s %5s  %s" % ("trait", "count", "split quality (81 total)"))
    for name in T:
        n = len(T[name])
        bar = "#" * round(n / 81 * 40)
        flag = "" if 8 <= n <= 73 else "   <- lopsided"
        print("  %-11s %3d  %-41s%s" % (name, n, bar, flag))

    # the three pairs that stats and categories cannot tell apart
    pairs = [("skeleton", "zombie"), ("bogged", "stray"), ("cow", "sheep")]
    print("\nseparating the identical pairs:")
    for a, b in pairs:
        sep = [n for n in T if (a in T[n]) != (b in T[n])]
        print("  %-10s vs %-10s -> %s" % (a, b, ", ".join(sep) if sep else "STILL IDENTICAL"))
