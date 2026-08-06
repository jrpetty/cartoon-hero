// Symmetry: every cell a stroke should also touch.
//
// This lives beside the map data rather than in the editor UI because two
// different things need it — the editor's brush, and the map script, which
// mirrors whole placements rather than single cells. Neither should have to
// import a screen to find out what "rot180" means.


export type SymmetryId = "none" | "mirrorX" | "mirrorY" | "rot180" | "quad" | "radial3" | "radial6" | "radial8";
export const SYMMETRIES: { id: SymmetryId; label: string; desc: string }[] = [
  { id: "none", label: "Free", desc: "Paint exactly where you click." },
  { id: "mirrorX", label: "Mirror ↔", desc: "Every stroke is mirrored left-to-right." },
  { id: "mirrorY", label: "Mirror ↕", desc: "Every stroke is mirrored top-to-bottom." },
  { id: "rot180", label: "Rotate 180°", desc: "The classic two-player mirror: both halves are identical ground." },
  { id: "quad", label: "Quarters", desc: "Four-way — for a four-player map with equal corners." },
  // Rotating about the centre of a square map sends the far corners outside it,
  // so a radial stroke near a corner simply has fewer copies. That is geometry,
  // not a bug — say so, rather than let an author wonder why.
  { id: "radial3", label: "Radial ×3", desc: "Three equal slices around the centre. Stay inside the circle that fits the map — corners rotate off the edge." },
  { id: "radial6", label: "Radial ×6", desc: "Six equal slices. Stay inside the circle that fits the map — corners rotate off the edge." },
  { id: "radial8", label: "Radial ×8", desc: "Eight equal slices, one per seat on a full lobby. Corners rotate off the edge." },
];

/**
 * Every cell a stroke at (cx,cy) should also touch. Radial symmetry rotates
 * about the map's centre, which is why it needs the dimensions.
 */
export function symmetryCells(sym: SymmetryId, cx: number, cy: number, cols: number, rows: number): [number, number][] {
  const out: [number, number][] = [[cx, cy]];
  const push = (x: number, y: number) => {
    const rx = Math.round(x), ry = Math.round(y);
    if (rx < 0 || ry < 0 || rx >= cols || ry >= rows) return;
    if (out.some(([a, b]) => a === rx && b === ry)) return;
    out.push([rx, ry]);
  };
  const mx = cols - 1 - cx;
  const my = rows - 1 - cy;
  switch (sym) {
    case "mirrorX": push(mx, cy); break;
    case "mirrorY": push(cx, my); break;
    case "rot180": push(mx, my); break;
    case "quad": push(mx, cy); push(cx, my); push(mx, my); break;
    case "radial3": case "radial6": case "radial8": {
      const n = sym === "radial3" ? 3 : sym === "radial6" ? 6 : 8;
      const ox = (cols - 1) / 2, oy = (rows - 1) / 2;
      const dx = cx - ox, dy = cy - oy;
      for (let k = 1; k < n; k++) {
        const a = (k / n) * Math.PI * 2;
        push(ox + dx * Math.cos(a) - dy * Math.sin(a), oy + dx * Math.sin(a) + dy * Math.cos(a));
      }
      break;
    }
    default: break;
  }
  return out;
}

