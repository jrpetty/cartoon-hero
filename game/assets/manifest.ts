// Prompts for Meshy AI asset generation. Each entry maps a game unit/building
// type id to a text prompt plus how its baked sprite should sit on the tile.
//
// Art direction (kept in every prompt for a coherent set): stylized low-poly
// fantasy, painterly hand-painted textures, neutral/grey base colors (the
// engine tints team colors), clean readable silhouette, single centered object,
// three-quarter top-down view, plain background.
//
// `scale`   — multiplies the sprite size relative to the unit/building radius.
// `anchorY` — vertical anchor (0 = top, 0.5 = center, 1 = feet) so the sprite
//             plants correctly on the ground.

export interface AssetPrompt {
  id: string;
  kind: "unit" | "building";
  prompt: string;
  scale?: number;
  anchorY?: number;
}

const STYLE =
  "stylized low-poly fantasy, hand-painted painterly textures, neutral grey and " +
  "leather colors, clean readable silhouette, single centered object, " +
  "three-quarter top-down view, plain background, game asset";

const u = (id: string, desc: string, scale = 1, anchorY = 0.85): AssetPrompt => ({
  id, kind: "unit", prompt: `${desc}, ${STYLE}`, scale, anchorY,
});
const b = (id: string, desc: string, scale = 1, anchorY = 0.8): AssetPrompt => ({
  id, kind: "building", prompt: `${desc}, ${STYLE}`, scale, anchorY,
});

export const ASSET_PROMPTS: AssetPrompt[] = [
  // --- Units ---
  u("villager", "a medieval peasant villager holding simple tools, brown tunic"),
  u("militia", "a medieval man-at-arms infantry soldier with sword and round shield"),
  u("spearman", "a medieval spearman holding a long spear, light armor"),
  u("archer", "a medieval archer drawing a longbow, hooded leather outfit"),
  u("skirmisher", "a light skirmisher throwing javelins, minimal armor"),
  u("crossbow", "a medieval crossbowman aiming a heavy crossbow"),
  u("knight", "a heavy armored knight on a warhorse with lance", 1.25, 0.82),
  u("catapult", "a wooden medieval catapult siege engine", 1.3, 0.8),
  u("trebuchet", "a tall wooden trebuchet siege engine", 1.4, 0.78),
  u("ram", "a wheeled medieval battering ram with timber roof", 1.3, 0.82),
  u("hero", "a heroic fantasy champion in ornate golden armor with greatsword", 1.2, 0.82),
  u("monk", "a robed fantasy monk healer holding a glowing staff"),

  // --- Buildings ---
  b("town_center", "a grand medieval town center hall with banners and a tower", 1.3, 0.78),
  b("house", "a small medieval thatched-roof timber house"),
  b("mill", "a medieval windmill with rotating sails"),
  b("lumber_camp", "a rustic medieval lumber camp with stacked logs and a saw"),
  b("mining_camp", "a medieval mining camp with ore carts and a pickaxe rack"),
  b("farm", "a tilled farm field plot with crops and a fence", 1.0, 0.6),
  b("barracks", "a medieval barracks with weapon racks and a banner"),
  b("archery_range", "a medieval archery range building with targets"),
  b("stable", "a medieval stable building with a horse"),
  b("blacksmith", "a medieval blacksmith forge with anvil and chimney smoke"),
  b("market", "a colorful medieval market stall with awnings and goods"),
  b("palisade", "a wooden palisade wall segment of sharpened logs", 1.0, 0.7),
  b("stone_wall", "a stone fortress wall segment with battlements", 1.0, 0.7),
  b("gate", "a fortified stone gatehouse with a wooden gate", 1.1, 0.72),
  b("watchfire", "a stone watchtower with a burning brazier on top", 1.1, 0.72),
  b("watch_tower", "a tall medieval stone watch tower with arrow slits", 1.2, 0.72),
  b("castle", "a massive medieval stone castle keep with multiple towers and banners", 1.5, 0.74),
  b("siege_workshop", "a medieval siege workshop with timber frames and gears", 1.2, 0.78),
];
