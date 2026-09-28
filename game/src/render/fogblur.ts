// Softening the fog frontier.
//
// Fog is one value per nav cell and only ever takes three of them — unseen,
// explored, visible. Upscaling that straight to the screen ramps between cell
// *centres*, so every diagonal frontier (which is most of them) arrives as a
// staircase. Blurring the mask first turns that edge into the soft falloff a
// scouted horizon should have.
//
// Its own function, and separable, for two reasons: a box blur done as two
// one-dimensional passes is O(n·r) instead of O(n·r²), and a frontier is far
// easier to make assertions about here than anywhere downstream of the
// compositor, where terrain noise swamps it.

/**
 * Separable box blur over a `cols`×`rows` grid, in place on `a`.
 *
 * `scratch` must be the same length as `a`; it is the intermediate buffer for
 * the horizontal pass and holds nothing meaningful afterwards. Both are passed
 * in rather than allocated so a caller on the render path allocates nothing
 * per frame.
 */
export function blurMask(
  a: Float32Array,
  scratch: Float32Array,
  cols: number,
  rows: number,
  radius: number,
): void {
  if (radius <= 0) return;
  const inv = 1 / (radius * 2 + 1);
  for (let y = 0; y < rows; y++) {
    const row = y * cols;
    for (let x = 0; x < cols; x++) {
      let sum = 0;
      for (let k = -radius; k <= radius; k++) {
        sum += a[row + Math.max(0, Math.min(cols - 1, x + k))];
      }
      scratch[row + x] = sum * inv;
    }
  }
  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      let sum = 0;
      for (let k = -radius; k <= radius; k++) {
        sum += scratch[Math.max(0, Math.min(rows - 1, y + k)) * cols + x];
      }
      a[y * cols + x] = sum * inv;
    }
  }
}

/** How far the fog edge is smeared, in cells either side of the true frontier. */
export const FOG_BLUR_RADIUS = 2;
