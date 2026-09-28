// Local steering: separation from nearby units + arrival damping. Keeps units
// from stacking and makes group movement look organic. Operates on top of the
// path/flow-field desired direction.

import { Entity, Kind } from "../sim/types";
import { SpatialGrid } from "../engine/spatialgrid";

export function applySeparation(
  e: Entity,
  neighbors: Entity[],
  desiredX: number,
  desiredY: number,
  strength = 60,
): [number, number] {
  let sepX = 0;
  let sepY = 0;
  for (const n of neighbors) {
    if (n === e || !n.alive) continue;
    if (n.kind !== Kind.Unit) continue;
    const dx = e.x - n.x;
    const dy = e.y - n.y;
    const d2 = dx * dx + dy * dy;
    const minDist = e.radius + n.radius + 2;
    if (d2 < minDist * minDist && d2 > 0.0001) {
      const d = Math.sqrt(d2);
      const push = (minDist - d) / minDist;
      sepX += (dx / d) * push;
      sepY += (dy / d) * push;
    }
  }
  return [desiredX + sepX * strength, desiredY + sepY * strength];
}

/** Hard de-overlap pass so idle units never permanently stack. */
export function resolveOverlaps(units: Entity[], spatial: SpatialGrid, dt: number) {
  for (const e of units) {
    if (!e.alive || e.kind !== Kind.Unit) continue;
    const near = spatial.query(e.x, e.y, e.radius + 16) as Entity[];
    for (const n of near) {
      if (n === e || !n.alive || n.kind !== Kind.Unit) continue;
      const dx = e.x - n.x;
      const dy = e.y - n.y;
      const d2 = dx * dx + dy * dy;
      const minDist = e.radius + n.radius;
      if (d2 < minDist * minDist) {
        const d = Math.sqrt(d2) || 0.01;
        const overlap = (minDist - d) * 0.5;
        const px = (dx / d) * overlap * Math.min(1, dt * 12);
        const py = (dy / d) * overlap * Math.min(1, dt * 12);
        e.x += px;
        e.y += py;
        n.x -= px;
        n.y -= py;
      }
    }
  }
}
