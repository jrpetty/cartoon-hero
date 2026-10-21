// The comparison sheets for the Godot port render text in the same font files
// on both sides. Trebuchet MS and Georgia (what the game asks for) are not on a
// stock Linux box; the Godot port falls back to Liberation Sans / Liberation
// Serif (Arial / Times metrics), so here those files are registered under the
// game's family names. Without this, Skia falls back to a sans for "Georgia".
import { GlobalFonts } from "@napi-rs/canvas";
import { existsSync } from "node:fs";

const DIR = "/usr/share/fonts/truetype/liberation";
export function aliasFonts() {
  const pairs = [
    ["LiberationSans-Regular.ttf", "Trebuchet MS"],
    ["LiberationSans-Bold.ttf", "Trebuchet MS"],
    ["LiberationSans-Italic.ttf", "Trebuchet MS"],
    ["LiberationSans-BoldItalic.ttf", "Trebuchet MS"],
    ["LiberationSerif-Regular.ttf", "Georgia"],
    ["LiberationSerif-Bold.ttf", "Georgia"],
    ["LiberationSerif-Italic.ttf", "Georgia"],
    ["LiberationSerif-BoldItalic.ttf", "Georgia"],
  ];
  for (const [file, alias] of pairs) {
    const p = `${DIR}/${file}`;
    if (existsSync(p)) GlobalFonts.registerFromPath(p, alias);
  }
}
