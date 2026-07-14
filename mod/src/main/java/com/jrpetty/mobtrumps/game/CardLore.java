package com.jrpetty.mobtrumps.game;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Flavour text and a real Minecraft fact for each mob card, shown on the
 * full card view. Any card without an entry falls back to a generic line.
 */
public final class CardLore {

    /** A card's flavour line and a fun fact about the real mob. */
    public record Lore(String flavor, String fact) {
    }

    private static final Map<String, Lore> LORE = new HashMap<>();

    private static void e(String id, String flavor, String fact) {
        LORE.put(id, new Lore(flavor, fact));
    }

    public static Lore of(String id) {
        if (id == null) return GENERIC;
        return LORE.getOrDefault(id.toLowerCase(Locale.ROOT), GENERIC);
    }

    private static final Lore GENERIC =
            new Lore("A creature of the blocky world.", "Every mob has its own quirks — go meet it.");

    static {
        e("allay", "A tiny blue helper with a song in its heart.", "Allays duplicate when given an Amethyst Shard while dancing to a jukebox.");
        e("armadillo", "Rolls into a ball at the first sign of trouble.", "Brushing an armadillo yields scutes used to craft wolf armor.");
        e("axolotl", "Cute, deadly, and plays dead to survive.", "Axolotls give you Regeneration when you help them win a fight.");
        e("bat", "Flaps in the dark, bothering no one.", "Bats are purely ambient — they drop nothing and harm nothing.");
        e("bee", "Busy, fuzzy, and best not angered.", "Bees pollinate crops and only sting once before dying.");
        e("blaze", "A whirl of rods and fire in the Nether.", "Blaze rods are the only source of blaze powder for brewing and Eyes of Ender.");
        e("bogged", "A mossy skeleton dripping with venom.", "The bogged fires poison-tipped arrows that poison you on hit.");
        e("breeze", "Bounces about, hurling gusts of wind.", "Breezes drop wind charges and live in trial chambers.");
        e("camel", "Tall enough to seat two and dash past danger.", "Players riding a camel are too high for most melee mobs to reach.");
        e("cat", "Aloof, but keeps creepers and phantoms away.", "Cats scare off creepers and can gift you a present when you wake up.");
        e("cave_spider", "Small, fast, and venomous in the mines.", "Cave spiders are smaller than spiders and can fit through 1-block gaps.");
        e("chicken", "The humble bird behind every breakfast.", "Thrown eggs have a small chance to hatch into a chick.");
        e("cod", "A plain fish going about its day.", "Cod swim in schools and are a staple food source in oceans.");
        e("cow", "Dependable source of beef and leather.", "Cows can be milked endlessly with a bucket.");
        e("creaking", "A groaning guardian of the pale garden.", "The creaking only moves when you aren't looking at it.");
        e("creeper", "Approaches quietly... then everything explodes.", "Creepers are the only mob that drops music discs — when killed by a skeleton.");
        e("dolphin", "Playful, quick, and eager to lead the way.", "Dolphins give you Dolphin's Grace and guide you to buried treasure.");
        e("donkey", "A sturdy pack animal with saddlebags.", "Donkeys can wear a chest, carrying 15 slots of storage.");
        e("drowned", "A zombie that never left the water.", "Drowned can spawn holding tridents — a rare and powerful drop.");
        e("elder_guardian", "The ancient warden of the ocean monument.", "Elder guardians inflict Mining Fatigue to stop you looting their temple.");
        e("ender_dragon", "The End's apex predator and final boss.", "The ender dragon is healed by End Crystals you must destroy first.");
        e("enderman", "Don't look it in the eyes.", "Endermen teleport, carry blocks, and hate being stared at.");
        e("endermite", "A tiny purple pest from thrown pearls.", "Endermites can spawn when you throw an ender pearl.");
        e("evoker", "Summons fangs and vexes from thin air.", "Evokers drop the Totem of Undying, which cheats death once.");
        e("fox", "Sly, sleepy, and fond of sweet berries.", "Foxes remember and trust whoever helped raise them, and hold items in their mouths.");
        e("frog", "Ribbits by the swamp and eats what fits.", "Each frog variant turns slime (or magma cubes) into a different froglight.");
        e("ghast", "A floating jellyfish of the Nether, wailing fire.", "Ghast tears are needed to brew Potions of Regeneration.");
        e("glow_squid", "Drifts and glows in the deep dark waters.", "Glow squid drop glow ink used to make signs and item frames light up.");
        e("goat", "Headbutts first, asks questions never.", "Goats can ram you off ledges and occasionally drop a goat horn.");
        e("guardian", "A spiky sentry with a laser stare.", "Guardians fire a beam that charges up before it hits.");
        e("hoglin", "A brutish Nether boar that hates you.", "Hoglins flee from warped fungus and nether portals.");
        e("horse", "Fast, faithful, and made for the open road.", "A horse's speed and jump are inherited — breed for the best foal.");
        e("husk", "A sunburnt zombie of the desert.", "Husks give you Hunger on hit and don't burn in daylight.");
        e("iron_golem", "The village's towering iron protector.", "Iron golems offer poppies to villagers, especially children.");
        e("llama", "Spits at anything that annoys it.", "Angry llamas spit at attackers — and can be linked into caravans.");
        e("magma_cube", "A bouncing cube of molten hostility.", "Large magma cubes split into smaller ones when killed.");
        e("mooshroom", "A mushroom cow you can milk for stew.", "Shear a mooshroom for mushrooms — turning it into a plain cow.");
        e("mule", "The tireless offspring of horse and donkey.", "Mules can't breed but can carry a chest like donkeys.");
        e("ocelot", "A jungle cat that trusts only the patient.", "Ocelots can't be tamed — but feeding fish earns their trust.");
        e("panda", "Rolls, sneezes, and lazes in the bamboo.", "Pandas have hidden personalities like lazy, playful, or weak.");
        e("parrot", "A colourful mimic that dances to music.", "Parrots imitate the sounds of nearby hostile mobs.");
        e("phantom", "Swoops from the night sky when you don't sleep.", "Phantoms only spawn if you've been awake for three in-game days.");
        e("pig", "The classic snorting source of porkchops.", "Saddle a pig and steer it with a carrot on a stick.");
        e("piglin", "A gold-obsessed brute of the Nether.", "Piglins turn hostile if you open chests or mine gold near them.");
        e("piglin_brute", "An elite piglin that never bargains.", "Piglin brutes guard bastion treasure and ignore your gold armor.");
        e("pillager", "A crossbow-toting raider of villages.", "Killing a pillager captain gives you the dreaded Bad Omen.");
        e("polar_bear", "A gentle giant — unless cubs are near.", "Adult polar bears turn hostile to protect their cubs.");
        e("pufferfish", "Puffs up and poisons the careless.", "Touching an inflated pufferfish inflicts Poison and Nausea.");
        e("rabbit", "Hops harmlessly... mostly.", "The Killer Bunny is a legendary hostile rabbit variant.");
        e("ravager", "A hulking beast that smashes through raids.", "Ravagers can trample crops and knock you back with a roar.");
        e("salmon", "Swims upstream in cold rivers.", "Salmon come in three sizes: small, medium, and large.");
        e("sheep", "Woolly, mild, and endlessly shearable.", "Feed a sheep dyed grass — no, wait, dye it directly for colored wool.");
        e("shulker", "A box that bites back with floating bullets.", "Shulker bullets give Levitation, and shells craft shulker boxes.");
        e("silverfish", "Hides in stone and swarms when disturbed.", "Silverfish burrow into blocks and can call others to swarm.");
        e("skeleton", "A bony marksman with deadly aim.", "Skeletons burn in daylight unless they're wearing a helmet.");
        e("skeleton_horse", "A spectral steed born of lightning.", "Skeleton horse traps spawn when lightning strikes during a thunderstorm.");
        e("slime", "A jiggling cube that splits when struck.", "Slimes spawn in swamps and in rare underground slime chunks.");
        e("sniffer", "An ancient gentle giant that digs up seeds.", "Sniffers are hatched from eggs found by brushing suspicious blocks.");
        e("snow_golem", "A frosty friend that pelts foes with snow.", "Snow golems leave snow trails but melt in warm biomes.");
        e("spider", "A skittering climber of walls and ceilings.", "Spiders become neutral in daylight and can climb any surface.");
        e("squid", "An inky drifter of the open ocean.", "Squid squirt ink to escape and drop it for black dye.");
        e("stray", "A frostbitten skeleton of the icy wastes.", "Stray arrows inflict Slowness to freeze you in place.");
        e("strider", "Rides the lava seas of the Nether.", "Saddle a strider and steer it with a warped fungus on a stick.");
        e("sulfur_cube", "A volatile cube crackling with brimstone.", "A rare elemental oddity of the deep places.");
        e("tadpole", "A wriggling baby frog of the swamp.", "The biome a tadpole grows up in decides its frog color.");
        e("trader_llama", "The wandering trader's loyal packmate.", "Trader llamas despawn with their trader unless you lead them away.");
        e("tropical_fish", "A dazzling splash of reef color.", "There are over 2,700 tropical fish patterns and color combinations.");
        e("turtle", "Plods the beach and buries its eggs in sand.", "Turtle scutes from grown babies craft the water-breathing Turtle Shell.");
        e("vex", "A ghostly blade summoned by evokers.", "Vexes phase through walls and slowly take damage until they vanish.");
        e("villager", "Trades emeralds for just about anything.", "A villager's profession is set by the workstation block nearest it.");
        e("vindicator", "Charges in swinging a wicked axe.", "A vindicator named 'Johnny' attacks nearly every other mob on sight.");
        e("wandering_trader", "A roaming merchant with two llamas.", "Wandering traders drink a potion of invisibility when threatened at night.");
        e("warden", "Blind, deaf to reason, guided by sound.", "The warden is blind and hunts by vibration and smell — sneak or die.");
        e("witch", "Lobs potions and heals herself mid-fight.", "Witches are immune to most potion effects they throw.");
        e("wither", "A three-headed storm of destruction.", "The wither is built with soul sand and three wither skeleton skulls.");
        e("wither_skeleton", "A blackened skeleton of the fortress.", "Their skulls are needed to summon the wither — a rare drop.");
        e("wolf", "A loyal companion once you earn its trust.", "Tamed wolves come in nine coat variants across different biomes.");
        e("zombie", "The shambling classic of every first night.", "Zombies bang on doors and call reinforcements when hurt.");
        e("zombie_villager", "A villager lost to the horde — but curable.", "Cure one with a Weakness potion and a golden apple for cheaper trades.");
        e("zombified_piglin", "A neutral undead of the Nether.", "Anger one and the entire mob swarms you together.");
        e("zoglin", "A hoglin that crossed into the Overworld.", "Hoglins zombify into aggressive zoglins after time in the Overworld.");
    }

    private CardLore() {
    }
}
