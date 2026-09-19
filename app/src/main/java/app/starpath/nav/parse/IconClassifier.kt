package app.starpath.nav.parse

import app.starpath.nav.model.NavManeuver
/**
 * Classifies the Google Maps navigation arrow bitmap into a [NavManeuver].
 *
 * Why this exists: on the Amazfit Active 2 (Zepp App Alerts) only notification
 * **text** reaches the watch — images such as `largeIcon` do not. And Maps
 * often posts icon-only instructions ("40 m" + street name, no turn verb —
 * see screenshots in docs/IMPLEMENTATION.md §5), so keyword parsing yields UNKNOWN.
 * The arrow pixels are then the only direction signal. This classifier runs
 * on-phone, converts the icon to a maneuver, and the maneuver is rendered as
 * a **text glyph** (`◀◀ 40 m`) which is what the watch actually displays.
 *
 * Pure Kotlin (no `android.*` imports) so it stays JVM-unit-testable:
 * callers convert `Bitmap` to [IconPixels] via `getPixels` first.
 *
 * Method: binarize by brightness polarity (arrow is the minority-polarity
 * region, so both white-on-teal and dark-on-light styles work), downscale to
 * a 16×16 mask, recenter to the foreground bounding box, then compare where
 * the top-half mass sits versus the bottom-half mass (first-order moments).
 * Left hooks (`—┐` rotated) carry mass on the top-left with the shaft foot at
 * bottom-right, so topMeanX − bottomMeanX << 0; right hooks mirror it;
 * straight arrows stay centered (delta ≈ 0) with a narrow apex on top.
 * A roundabout loop dilutes the delta below the turn threshold, so an
 * enclosed background hole (donut) re-enables a lower threshold whose sign
 * gives the exit side; symmetric loops stay UNKNOWN.
 * Thickness, dash style, and shift cancel out — which is exactly what
 * defeated both the old whole-icon templates and the triangle-head matcher
 * (thick corners matched `<` and `>` equally at 0.95).
 * Emits TURN_LEFT / TURN_RIGHT / STRAIGHT / DESTINATION / UNKNOWN — never
 * UTURN from pixels (UTURN stays reachable via text keywords).
 */
object IconClassifier {

    const val GRID = 16
    /** Minimum confidence for a direction verdict to be trusted. */
    const val HEAD_THRESHOLD = 0.55
    /** |topMeanX − bottomMeanX| at or above this means a turn. Safety: gentle bends stay TURN — a missed fork costs more than a false alert. */
    internal const val TURN_DELTA = 1.5
    /** Bounding-box height below this carries no direction (chevrons, heads). */
    internal const val MIN_HEIGHT = 8
    /**
     * Widest foreground run allowed at the icon's bottom edge for a turn verdict.
     * Real hooks end in a ≤6-cell shaft (thick field hooks peak at 6); the
     * Maps destination pin (pin+road) stays 10+ cells wide down to the bottom
     * edge and otherwise mimics a large top-vs-bottom moment delta. Both
     * mirrors (mass left or right) collapse to a single DESTINATION verdict
     * (renders as `DEST`). The width must persist to the lowest foreground
     * rows: a roundabout loop is wide mid-icon but tapers to a narrow stem
     * (field: roundabout exit-right at 3.5 km remaining falsely hit DEST),
     * so "wide anywhere in the bottom half" is not enough.
     */
    internal const val MAX_BOTTOM_WIDTH = 8
    /** Confidence for the destination-pin verdict (fixed, shape-gated). */
    internal const val PIN_SCORE = 0.85
    /**
     * Smallest enclosed background hole that counts as a roundabout loop.
     * The exit-right loop encloses 7 cells; hooks and straight arrows enclose
     * none. Destination pins do enclose small gaps, but the DEST gate above
     * returns first, so they never reach the loop branch.
     */
    internal const val LOOP_MIN_HOLE = 4
    /**
     * |topMeanX − bottomMeanX| at or above this means a roundabout exit side
     * when a loop is present. Below the plain-turn [TURN_DELTA]: the loop's
     * symmetric mass dilutes the exit arrowhead (field: ±1.38), so the loop
     * gate — not the delta alone — carries the verdict. A symmetric loop
     * (delta ≈ 0) still falls through to UNKNOWN.
     */
    internal const val LOOP_DELTA = 1.0

    /** Raw pixels in row-major `0xAARRGGBB`, as returned by `Bitmap.getPixels`. */
    data class IconPixels(
        val width: Int,
        val height: Int,
        val argb: IntArray,
    )

    data class Match(
        val maneuver: NavManeuver,
        /** Confidence of the moments direction verdict, 0..1. */
        val score: Double,
    )

    /**
     * Full classification detail for field-harvest logging ([maskArt] is 16
     * `#`/`.` rows; [scores] holds the confidence per direction).
     */
    data class Detail(
        val maneuver: NavManeuver,
        val score: Double,
        val scores: Map<NavManeuver, Double>,
        val maskArt: String,
    )

    fun classify(p: IconPixels): Match {
        val d = classifyDetailed(p)
        return Match(d.maneuver, d.score)
    }

    fun classifyDetailed(p: IconPixels): Detail {
        if (p.width <= 0 || p.height <= 0 || p.argb.size < p.width * p.height) {
            return Detail(NavManeuver.UNKNOWN, 0.0, emptyMap(), "")
        }
        val grid = toMask(p) ?: return Detail(NavManeuver.UNKNOWN, 0.0, emptyMap(), "")
        val norm = recenter(grid)
        val art = renderArt(norm)
        val (maneuver, score) = directionFromMask(norm)
        val scores = linkedMapOf(
            NavManeuver.TURN_LEFT to if (maneuver == NavManeuver.TURN_LEFT) score else 0.0,
            NavManeuver.TURN_RIGHT to if (maneuver == NavManeuver.TURN_RIGHT) score else 0.0,
            NavManeuver.STRAIGHT to if (maneuver == NavManeuver.STRAIGHT) score else 0.0,
            NavManeuver.DESTINATION to if (maneuver == NavManeuver.DESTINATION) score else 0.0,
        )
        return if (maneuver != NavManeuver.UNKNOWN && score >= HEAD_THRESHOLD) {
            Detail(maneuver, score, scores, art)
        } else {
            Detail(NavManeuver.UNKNOWN, score, scores, art)
        }
    }

    /**
     * First-order moments direction: top-half mean-x minus bottom-half mean-x.
     * Returns the maneuver with a 0..1 confidence.
     */
    internal fun directionFromMask(norm: BooleanArray): Pair<NavManeuver, Double> {
        var minY = GRID
        var maxY = -1
        var topSum = 0
        var topN = 0
        var botSum = 0
        var botN = 0
        var allSum = 0
        var allN = 0
        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                if (!norm[y * GRID + x]) continue
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                allSum += x
                allN++
                if (y < GRID / 2) {
                    topSum += x
                    topN++
                } else {
                    botSum += x
                    botN++
                }
            }
        }
        if (allN == 0 || maxY < 0) return NavManeuver.UNKNOWN to 0.0
        if (maxY - minY + 1 < MIN_HEIGHT) return NavManeuver.UNKNOWN to 0.0
        if (topN == 0 || botN == 0) return NavManeuver.UNKNOWN to 0.0
        // Destination pin: wide road block flush to the icon's bottom edge is
        // not a shaft. Sign-agnostic: left and right mirrors both land here
        // as one DEST. The width must hold at the lowest foreground rows —
        // a roundabout loop is wide mid-icon but narrows to a stem, so it
        // falls through to the loop branch below.
        var edgeWidth = 0
        for (dy in 0..1) {
            val y = maxY - dy
            if (y < GRID / 2) break
            var c = 0
            for (x in 0 until GRID) if (norm[y * GRID + x]) c++
            if (c > edgeWidth) edgeWidth = c
        }
        if (edgeWidth > MAX_BOTTOM_WIDTH) return NavManeuver.DESTINATION to PIN_SCORE
        val topMean = topSum.toDouble() / topN
        val botMean = botSum.toDouble() / botN
        val delta = topMean - botMean
        // Roundabout exit: the loop (donut hole) dilutes the moments delta
        // below TURN_DELTA, but the exit arrowhead still offsets the top-half
        // mass to its side. Loop-gated, so plain hooks (no hole) are
        // unaffected; symmetric loops fall through to UNKNOWN.
        if (maxEnclosedHole(norm) >= LOOP_MIN_HOLE) {
            if (delta <= -LOOP_DELTA) {
                return NavManeuver.TURN_LEFT to confidence(delta)
            }
            if (delta >= LOOP_DELTA) {
                return NavManeuver.TURN_RIGHT to confidence(delta)
            }
        }
        if (delta <= -TURN_DELTA) {
            return NavManeuver.TURN_LEFT to confidence(delta)
        }
        if (delta >= TURN_DELTA) {
            return NavManeuver.TURN_RIGHT to confidence(delta)
        }
        // Possible straight: centered overall with a narrow apex on top.
        val overallMean = allSum.toDouble() / allN
        if (kotlin.math.abs(overallMean - (GRID - 1) / 2.0) <= 1.75 && apexWidth(norm, minY) <= 2) {
            return NavManeuver.STRAIGHT to 0.80
        }
        return NavManeuver.UNKNOWN to 0.0
    }

    private fun confidence(delta: Double): Double =
        (0.60 + kotlin.math.abs(delta) * 0.07).coerceAtMost(0.95)

    /** Foreground cell count in the topmost row (arrow apex width). */
    private fun apexWidth(norm: BooleanArray, minY: Int): Int {
        var c = 0
        for (x in 0 until GRID) if (norm[minY * GRID + x]) c++
        return c
    }

    /**
     * Largest enclosed background region (4-connected) in cells. Flood-fills
     * from the mask border; background never reached is a hole (donut loop).
     * Zero when the background is fully connected (hooks, straight arrows).
     */
    internal fun maxEnclosedHole(norm: BooleanArray): Int {
        val reached = BooleanArray(GRID * GRID)
        val queue = ArrayDeque<Int>()
        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                if (y != 0 && y != GRID - 1 && x != 0 && x != GRID - 1) continue
                val i = y * GRID + x
                if (!norm[i] && !reached[i]) {
                    reached[i] = true
                    queue.addLast(i)
                }
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % GRID
            val y = i / GRID
            if (x > 0) queue.tryReach(norm, reached, i - 1)
            if (x < GRID - 1) queue.tryReach(norm, reached, i + 1)
            if (y > 0) queue.tryReach(norm, reached, i - GRID)
            if (y < GRID - 1) queue.tryReach(norm, reached, i + GRID)
        }
        val seen = BooleanArray(GRID * GRID)
        var best = 0
        for (i in norm.indices) {
            if (norm[i] || reached[i] || seen[i]) continue
            var size = 0
            val stack = ArrayDeque<Int>()
            stack.addLast(i)
            seen[i] = true
            while (stack.isNotEmpty()) {
                val j = stack.removeLast()
                size++
                val x = j % GRID
                val y = j / GRID
                if (x > 0) stack.tryVisit(norm, reached, seen, j - 1)
                if (x < GRID - 1) stack.tryVisit(norm, reached, seen, j + 1)
                if (y > 0) stack.tryVisit(norm, reached, seen, j - GRID)
                if (y < GRID - 1) stack.tryVisit(norm, reached, seen, j + GRID)
            }
            if (size > best) best = size
        }
        return best
    }

    private fun ArrayDeque<Int>.tryReach(
        norm: BooleanArray,
        reached: BooleanArray,
        i: Int,
    ) {
        if (!norm[i] && !reached[i]) {
            reached[i] = true
            addLast(i)
        }
    }

    private fun ArrayDeque<Int>.tryVisit(
        norm: BooleanArray,
        reached: BooleanArray,
        seen: BooleanArray,
        i: Int,
    ) {
        if (!norm[i] && !reached[i] && !seen[i]) {
            seen[i] = true
            addLast(i)
        }
    }

    /**
     * Binarize + downscale to a GRID×GRID foreground mask.
     * Returns null when there is no usable foreground (blank icon).
     */
    internal fun toMask(p: IconPixels): BooleanArray? {
        var bright = 0
        var dark = 0
        val lum = FloatArray(p.width * p.height)
        for (i in lum.indices) {
            val px = p.argb[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val l = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            lum[i] = l
            // Ignore transparent pixels entirely.
            val a = (px ushr 24) and 0xFF
            if (a < 32) {
                lum[i] = -1f
            } else if (l > 0.70f) {
                bright++
            } else if (l < 0.35f) {
                dark++
            }
        }
        if (bright == 0 && dark == 0) return null
        // The arrow is the minority polarity: white arrow on filled badge,
        // or dark arrow on light background.
        val fgIsBright = bright <= dark || dark == 0
        if (fgIsBright && bright < 8) return null
        if (!fgIsBright && dark < 8) return null

        val out = BooleanArray(GRID * GRID)
        val cellW = p.width / GRID.toFloat()
        val cellH = p.height / GRID.toFloat()
        var fgCells = 0
        for (gy in 0 until GRID) {
            for (gx in 0 until GRID) {
                val x0 = (gx * cellW).toInt().coerceIn(0, p.width - 1)
                val x1 = (((gx + 1) * cellW).toInt() - 1).coerceIn(0, p.width - 1)
                val y0 = (gy * cellH).toInt().coerceIn(0, p.height - 1)
                val y1 = (((gy + 1) * cellH).toInt() - 1).coerceIn(0, p.height - 1)
                var fg = 0
                var total = 0
                for (y in y0..y1) {
                    for (x in x0..x1) {
                        val l = lum[y * p.width + x]
                        if (l < 0f) continue
                        total++
                        val isFg = if (fgIsBright) l > 0.70f else l < 0.35f
                        if (isFg) fg++
                    }
                }
                if (total > 0 && fg * 4 >= total) {
                    out[gy * GRID + gx] = true
                    fgCells++
                }
            }
        }
        // Degenerate: nearly empty or nearly full mask carries no shape info.
        if (fgCells < 6 || fgCells > GRID * GRID - 6) return null
        return out
    }

    /**
     * Translate the mask so the foreground bounding box is centered.
     * Removes translation variance (replaces the old ±2-cell shift search);
     * scale variance is covered by matching heads in two sizes.
     */
    internal fun recenter(mask: BooleanArray): BooleanArray {
        var minX = GRID
        var maxX = -1
        var minY = GRID
        var maxY = -1
        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                if (!mask[y * GRID + x]) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return mask
        val dx = (GRID - (maxX - minX + 1)) / 2 - minX
        val dy = (GRID - (maxY - minY + 1)) / 2 - minY
        if (dx == 0 && dy == 0) return mask
        val out = BooleanArray(GRID * GRID)
        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                val sx = x - dx
                val sy = y - dy
                out[y * GRID + x] =
                    sx in 0 until GRID && sy in 0 until GRID && mask[sy * GRID + sx]
            }
        }
        return out
    }

    /** Renders a mask as 16 `#`/`.` rows for debug dumps and test fixtures. */
    fun renderArt(mask: BooleanArray): String =
        (0 until GRID).joinToString("\n") { y ->
            (0 until GRID).joinToString("") { x -> if (mask[y * GRID + x]) "#" else "." }
        }
}
