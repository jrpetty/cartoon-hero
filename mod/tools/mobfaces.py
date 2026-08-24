#!/usr/bin/env python3
"""
Pixel portraits for the mob cards, shared by every tool that draws a card.

Minecraft mob faces ARE 8x8 textures, so each entry here is an 8x8 grid in that
same idiom. '.' is transparent and lets whatever is behind show through.

These are hand-made renditions of the vanilla faces, NOT the game's own
textures, and they are only ever used by the offline preview and poster tools.
In game the portrait well holds a live 3D mob; nothing here ships in the jar.

One library rather than a copy per tool: the preview and the poster art must
show the same creeper, or a poster stops being a picture of the game.
"""


def _fish(body, stripe, eye):
    """Side-on fish, facing right; used by the fish that have no 'face'."""
    pal = {"o": body, "w": stripe, "k": eye}
    rows = ["........",
            "o..ooo..",
            "ooowwoo.",
            "ooowwoko",
            "ooowwoo.",
            "o..ooo..",
            "........",
            "........"]
    return pal, rows


FACES = {
    "pig": ({"p": (229, 153, 150), "d": (198, 110, 110), "n": (86, 40, 42),
             "w": (240, 240, 240), "k": (30, 30, 30)},
            ["pppppppp", "pppppppp", "wkppppkw", "pppppppp",
             "pddddddp", "pdnddndp", "pddddddp", "pppppppp"]),
    "enderman": ({"k": (18, 16, 20), "m": (208, 78, 250), "w": (236, 190, 255)},
                 ["kkkkkkkk", "kkkkkkkk", "kkkkkkkk", "mwmkkmwm",
                  "kkkkkkkk", "kkkkkkkk", "kkkkkkkk", "kkkkkkkk"]),
    "warden": ({"t": (16, 62, 66), "c": (92, 225, 210), "k": (8, 30, 32)},
               ["tttttttt", "tttttttt", "tcttttct", "tcttttct",
                "tttttttt", "ttkkkktt", "tttttttt", "tttttttt"]),
    "panda": ({"w": (235, 235, 235), "k": (44, 44, 44)},
              ["kwwwwwwk", "wwwwwwww", "wkkwwkkw", "wkkwwkkw",
               "wwwwwwww", "wwwkkwww", "wwwwwwww", "wwwwwwww"]),
    "mule": ({"b": (94, 66, 46), "k": (40, 28, 20), "w": (235, 235, 235),
              "m": (168, 132, 96), "n": (60, 42, 30)},
             ["bkbbbbkb", "bbbbbbbb", "bwkbbkwb", "bbbbbbbb",
              "bmmmmmmb", "bmnmmnmb", "bmmmmmmb", "bbbbbbbb"]),
    "tropical_fish": _fish((235, 122, 42), (245, 245, 245), (20, 20, 20)),
    "cod": _fish((150, 122, 92), (196, 176, 148), (20, 20, 20)),
    "salmon": _fish((176, 96, 92), (226, 176, 150), (20, 20, 20)),
    "fox": ({"o": (219, 122, 44), "k": (35, 30, 26), "w": (242, 238, 230)},
            ["ko....ok", "oooooooo", "okooooko", "oooooooo",
             "owwwwwwo", "wwwkkwww", "wwwwwwww", "........"]),
    "goat": ({"c": (206, 199, 184), "h": (148, 138, 118), "k": (36, 34, 30),
              "p": (226, 220, 206)},
             ["hhcccchh", "cccccccc", "ckcccckc", "cccccccc",
              "cccccccc", "ccppppcc", "ccpkkpcc", "cccccccc"]),
    "vex": ({"v": (134, 152, 184), "d": (92, 106, 132), "k": (28, 32, 44)},
            ["vvvvvvvv", "vvvvvvvv", "vkvvvvkv", "vvvvvvvv",
             "vvddddvv", "vvvvvvvv", "vvvvvvvv", "vvvvvvvv"]),
    "bogged": ({"g": (108, 140, 74), "s": (170, 180, 142), "k": (38, 44, 32)},
               ["gggggggg", "ssssssss", "skssssks", "ssssssss",
                "ssssssss", "sksksksk", "ssssssss", "ssssssss"]),
    "strider": ({"r": (180, 58, 58), "d": (122, 32, 34), "k": (30, 20, 20),
                 "e": (222, 218, 214)},
                ["rrrrrrrr", "ekrrrrke", "rrrrrrrr", "dddddddd",
                 "rrrrrrrr", "dddddddd", "rrrrrrrr", "rrrrrrrr"]),

    # --- added for the poster art ---
    "creeper": ({"g": (94, 166, 79), "d": (78, 140, 66), "k": (18, 38, 18)},
                ["gdggggdg", "gkkggkkg", "gkkggkkg", "gggkkggg",
                 "ggkkkkgg", "ggkkkkgg", "ggkggkgg", "gdggggdg"]),
    "bee": ({"y": (238, 196, 60), "k": (58, 42, 26), "w": (245, 245, 240)},
            ["yyyyyyyy", "ykwyywky", "yyyyyyyy", "kkkkkkkk",
             "yyyyyyyy", "kkkkkkkk", "yyyyyyyy", "kkkkkkkk"]),
    "ravager": ({"d": (98, 94, 88), "n": (74, 70, 66), "k": (26, 22, 20),
                 "w": (222, 214, 198)},
                ["dddddddd", "dkddddkd", "dddddddd", "dnnnnnnd",
                 "wnnnnnnw", "dnnnnnnd", "dddddddd", "dddddddd"]),
    "ender_dragon": ({"k": (28, 20, 34), "d": (52, 38, 62), "m": (226, 60, 200)},
                     ["kkkkkkkk", "kdkkkkdk", "kmkkkkmk", "kkkkkkkk",
                      "kdddddDk".replace("D", "d"), "kkkkkkkk", "kdkkkkdk", "kkkkkkkk"]),
    "allay": ({"b": (86, 146, 224), "l": (140, 200, 250), "w": (235, 248, 255)},
              ["..bbbb..", ".bllllb.", "blwllwlb", "bllllllb",
               "bllllllb", ".bllllb.", "..bbbb..", "........"]),
    "axolotl": ({"p": (242, 164, 202), "g": (250, 122, 186), "k": (66, 34, 56)},
                [".pppppp.", "pppppppp", "pkppppkp", "pppppppp",
                 "gppppppg", "gpppppgg".replace("gg", "pg"), ".pppppp.", "..pppp.."]),
    "blaze": ({"y": (246, 212, 84), "o": (232, 152, 44), "k": (78, 48, 12)},
              [".oyyyyo.", "yyyyyyyy", "ykyyyyky", "yyyyyyyy",
               "yoyoyoyo", ".oyoyoy.", "..oyyo..", "........"]),
    "wolf": ({"w": (228, 224, 216), "g": (176, 172, 166), "k": (38, 36, 34)},
             ["g......g", "gg....gg", "wwwwwwww", "wkwwwwkw",
              "wwwwwwww", "wwwkkwww", ".wwkkww.", "..wwww.."]),
    "iron_golem": ({"i": (216, 212, 202), "n": (166, 160, 150), "k": (58, 56, 52),
                    "v": (108, 148, 88)},
                   ["iiiiiiii", "iiiiiiii", "ikiiiiki", "iiiiiiii",
                    "innnnnni", "iinnnnii", "iiiiiiii", "ivviiiii"]),
    "skeleton": ({"b": (226, 226, 220), "n": (188, 188, 180), "k": (28, 28, 28)},
                 [".bbbbbb.", "bbbbbbbb", "bkkbbkkb", "bkkbbkkb",
                  "bbbbbbbb", "bbkkkkbb", "bkbkkbkb", ".nnnnnn."]),
    "zombie": ({"z": (92, 142, 82), "d": (62, 104, 56), "k": (24, 42, 24)},
               ["zzzzzzzz", "zzzzzzzz", "zkkzzkkz", "zzzzzzzz",
                "zzzzzzzz", "zzddddzz", "zzzzzzzz", "zdzzzzdz"]),
    "witch": ({"h": (92, 62, 124), "g": (112, 152, 102), "k": (30, 30, 30),
               "n": (152, 182, 132)},
              ["..hhhh..", ".hhhhhh.", "hhhhhhhh", "gggggggg",
               "gkggggkg", "ggnnnngg", "gggggggg", ".gggggg."]),
    "wither": ({"d": (62, 62, 62), "n": (42, 42, 42), "k": (16, 16, 16)},
               ["dddddddd", "dkkddkkd", "dkkddkkd", "dddddddd",
                "ddkkkkdd", "dkddddkd", "nnnnnnnn", "........"]),
    "elder_guardian": ({"t": (132, 156, 146), "s": (104, 128, 120),
                        "w": (242, 242, 232), "k": (214, 62, 62)},
                       ["tttttttt", "tsssssst", "tswwwwst", "tswkkwst",
                        "tswwwwst", "tsssssst", "tttttttt", "tstststs"]),
}
