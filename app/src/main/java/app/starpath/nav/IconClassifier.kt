package app.starpath.nav

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
 * Thickness, dash style, and shift cancel out — which is exactly what
 * defeated both the old whole-icon templates and the triangle-head matcher
 * (thick corners matched `<` and `>` equally at 0.95).
 * Emits only TURN_LEFT / TURN_RIGHT / STRAIGHT / UNKNOWN — never UTURN or
 * ROUNDABOUT from pixels (UTURN stays reachable via text keywords).
 */
object IconClassifier {

    const val GRID = 16
    /** Minimum confidence for a direction verdict to be trusted. */
    const val HEAD_THRESHOLD = 0.55
    /** |topMeanX − bottomMeanX| at or above this means a turn. */
    internal const val TURN_DELTA = 1.5
    /** Bounding-box height below this carries no direction (chevrons, heads). */
    internal const val MIN_HEIGHT = 8

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
        val topMean = topSum.toDouble() / topN
        val botMean = botSum.toDouble() / botN
        val delta = topMean - botMean
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
