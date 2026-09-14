package app.starpath.nav

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.util.EnumMap

/**
 * Renders high-contrast, crisp 192x192 arrow graphics for Zepp OS watch notifications.
 * Zepp displays `android.largeIcon` prominently on the watch display.
 * Bitmaps are cached by maneuver to minimize allocations during active navigation.
 */
object ArrowBitmapGenerator {

    private const val SIZE = 192
    private val bitmapCache = EnumMap<NavManeuver, Bitmap>(NavManeuver::class.java)

    @Synchronized
    fun createArrowBitmap(maneuver: NavManeuver): Bitmap {
        return bitmapCache.getOrPut(maneuver) {
            renderArrowBitmap(maneuver)
        }
    }

    private fun renderArrowBitmap(maneuver: NavManeuver): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark circular background for contrast against any watch theme
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 28, 36)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 230, 118) // High-visibility bright green
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 24f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Draw outer circular badge
        val radius = SIZE / 2f - 4f
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, radius, bgPaint)
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, radius, strokePaint)

        val center = SIZE / 2f
        val path = Path()

        when (maneuver) {
            NavManeuver.TURN_LEFT -> {
                // Shaft horizontal & vertical
                path.moveTo(center + 36f, center + 48f)
                path.lineTo(center + 36f, center)
                path.lineTo(center - 10f, center)
                canvas.drawPath(path, linePaint)
                // Arrowhead pointing left
                val head = Path().apply {
                    moveTo(center - 48f, center)
                    lineTo(center - 4f, center - 36f)
                    lineTo(center - 4f, center + 36f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.TURN_RIGHT -> {
                // Shaft horizontal & vertical
                path.moveTo(center - 36f, center + 48f)
                path.lineTo(center - 36f, center)
                path.lineTo(center + 10f, center)
                canvas.drawPath(path, linePaint)
                // Arrowhead pointing right
                val head = Path().apply {
                    moveTo(center + 48f, center)
                    lineTo(center + 4f, center - 36f)
                    lineTo(center + 4f, center + 36f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.SLIGHT_LEFT, NavManeuver.KEEP_LEFT -> {
                path.moveTo(center + 24f, center + 50f)
                path.lineTo(center - 12f, center - 12f)
                canvas.drawPath(path, linePaint)
                val head = Path().apply {
                    moveTo(center - 46f, center - 46f)
                    lineTo(center - 8f, center - 52f)
                    lineTo(center - 52f, center - 8f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.SLIGHT_RIGHT, NavManeuver.KEEP_RIGHT -> {
                path.moveTo(center - 24f, center + 50f)
                path.lineTo(center + 12f, center - 12f)
                canvas.drawPath(path, linePaint)
                val head = Path().apply {
                    moveTo(center + 46f, center - 46f)
                    lineTo(center + 8f, center - 52f)
                    lineTo(center + 52f, center - 8f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.SHARP_LEFT -> {
                path.moveTo(center + 20f, center + 50f)
                path.lineTo(center + 20f, center - 10f)
                path.lineTo(center - 20f, center + 15f)
                canvas.drawPath(path, linePaint)
                val head = Path().apply {
                    moveTo(center - 44f, center + 32f)
                    lineTo(center - 8f, center + 24f)
                    lineTo(center - 32f, center - 8f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.SHARP_RIGHT -> {
                path.moveTo(center - 20f, center + 50f)
                path.lineTo(center - 20f, center - 10f)
                path.lineTo(center + 20f, center + 15f)
                canvas.drawPath(path, linePaint)
                val head = Path().apply {
                    moveTo(center + 44f, center + 32f)
                    lineTo(center + 8f, center + 24f)
                    lineTo(center + 32f, center - 8f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.UTURN -> {
                path.moveTo(center + 28f, center + 44f)
                path.lineTo(center + 28f, center - 16f)
                path.quadTo(center + 28f, center - 48f, center, center - 48f)
                path.quadTo(center - 28f, center - 48f, center - 28f, center - 16f)
                path.lineTo(center - 28f, center + 12f)
                canvas.drawPath(path, linePaint)
                val head = Path().apply {
                    moveTo(center - 28f, center + 46f)
                    lineTo(center - 50f, center + 12f)
                    lineTo(center - 6f, center + 12f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.ROUNDABOUT -> {
                val circlePaint = Paint(linePaint).apply {
                    strokeWidth = 18f
                }
                canvas.drawCircle(center, center, 32f, circlePaint)
                // Arrow on roundabout
                val head = Path().apply {
                    moveTo(center + 32f, center - 20f)
                    lineTo(center + 52f, center + 6f)
                    lineTo(center + 16f, center + 6f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.DESTINATION -> {
                // Chequered finish / flag symbol
                val flagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(255, 215, 0)
                    style = Paint.Style.FILL
                }
                val polePaint = Paint(linePaint).apply {
                    color = Color.WHITE
                    strokeWidth = 10f
                }
                canvas.drawLine(center - 26f, center - 44f, center - 26f, center + 44f, polePaint)
                val flag = Path().apply {
                    moveTo(center - 24f, center - 44f)
                    lineTo(center + 36f, center - 20f)
                    lineTo(center - 24f, center + 4f)
                    close()
                }
                canvas.drawPath(flag, flagPaint)
            }
            NavManeuver.EXIT -> {
                path.moveTo(center - 36f, center + 44f)
                path.lineTo(center - 36f, center - 24f)
                path.quadTo(center - 36f, center - 40f, center - 10f, center - 40f)
                path.lineTo(center + 18f, center - 40f)
                canvas.drawPath(path, linePaint)
                val head = Path().apply {
                    moveTo(center + 46f, center - 40f)
                    lineTo(center + 14f, center - 62f)
                    lineTo(center + 14f, center - 18f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
            NavManeuver.STRAIGHT, NavManeuver.UNKNOWN -> {
                // Straight ahead arrow pointing up
                path.moveTo(center, center + 44f)
                path.lineTo(center, center - 12f)
                canvas.drawPath(path, linePaint)
                val head = Path().apply {
                    moveTo(center, center - 48f)
                    lineTo(center - 34f, center - 6f)
                    lineTo(center + 34f, center - 6f)
                    close()
                }
                canvas.drawPath(head, fillPaint)
            }
        }

        return bitmap
    }
}
