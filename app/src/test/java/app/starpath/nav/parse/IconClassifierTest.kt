package app.starpath.nav.parse

import app.starpath.nav.model.NavManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * [IconClassifier] is pure JVM-testable: icons are built as [IconPixels]
 * from string art (white arrow on Maps-teal background, like the real
 * `nav_notification_icon` bitmaps).
 */
class IconClassifierTest {

    companion object {
        private const val TEAL = 0xFF0E7C7B.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DARK = 0xFF222222.toInt()

        /** Renders string-art rows ('#' = arrow) into Maps-like pixels. */
        fun pixelsFromArt(
            rows: List<String>,
            cell: Int = 4,
            fg: Int = WHITE,
            bg: Int = TEAL,
        ): IconClassifier.IconPixels {
            val h = rows.size * cell
            val w = rows[0].length * cell
            val argb = IntArray(w * h) { bg }
            for (y in rows.indices) {
                for (x in rows[y].indices) {
                    if (rows[y][x] == '#') {
                        for (dy in 0 until cell) {
                            for (dx in 0 until cell) {
                                argb[(y * cell + dy) * w + (x * cell + dx)] = fg
                            }
                        }
                    }
                }
            }
            return IconClassifier.IconPixels(w, h, argb)
        }

        val LEFT_HOOK = listOf(
            "................",
            "................",
            "................",
            "..#.............",
            ".##.............",
            ".###########....",
            ".###.......#....",
            "..#........#....",
            "...........#....",
            "...........#....",
            "...........#....",
            "...........#....",
            "...........#....",
            "...........#....",
            "................",
            "................",
        )

        val RIGHT_HOOK = LEFT_HOOK.map { it.reversed() }

        val STRAIGHT = listOf(
            "................",
            ".......#........",
            "......###.......",
            ".....#####......",
            ".......#........",
            ".......#........",
            "................",
            ".......#........",
            ".......#........",
            ".......#........",
            "................",
            ".......#........",
            ".......#........",
            ".......#........",
            "................",
            "................",
        )

        /** Down chevron with shaft: Maps never points backwards -> UNKNOWN. */
        val DOWN_CHEVRON = listOf(
            "................",
            "................",
            "................",
            ".....#####......",
            "......###.......",
            ".......#........",
            ".......#........",
            ".......#........",
            ".......#........",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
        )

        /** Lone right chevron with no bar/shaft behind it -> UNKNOWN. */
        val HEAD_ONLY = listOf(
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "..........#.....",
            ".........##.....",
            "........###.....",
            ".........##.....",
            "..........#.....",
            "................",
            "................",
            "................",
            "................",
            "................",
        )

        /** Field mask from 2_maps_starpath_turn_left dump: thick hook tied L=R=0.95. */
        val THICK_LEFT_FIELD = listOf(
            "................",
            "....###.........",
            "...####.........",
            "..#####.........",
            ".############...",
            ".##############.",
            "..#############.",
            "...####.....###.",
            "...####......##.",
            ".....##......##.",
            ".............##.",
            ".............##.",
            ".............##.",
            ".............##.",
            "................",
            "................",
        )

        val THICK_RIGHT_FIELD = THICK_LEFT_FIELD.map { it.reversed() }

        /** Field mask from maps_starpath_destination_left dump: pin+road, wide bottom. */
        val DEST_PIN_LEFT = listOf(
            "...###..........",
            ".#######........",
            ".########.......",
            "###.#####.......",
            "######.##.......",
            "######.##.......",
            ".##...###.......",
            ".###..########..",
            ".###.#########..",
            "..#############.",
            "...############.",
            "...############.",
            "......##########",
            "......##########",
            "......##########",
            "................",
        )

        val DEST_PIN_RIGHT = DEST_PIN_LEFT.map { it.reversed() }

        /** Field mask from roundabout_right dump: loop wide mid-icon, narrow stem. */
        val ROUNDABOUT_RIGHT_FIELD = listOf(
            "................",
            "................",
            "..#####...###...",
            ".#######..####..",
            "########..#####.",
            "###...##########",
            "###...##########",
            "####.###...####.",
            ".#######..####..",
            "..#####...###...",
            "...###....##....",
            "...###..........",
            "...###..........",
            "...###..........",
            "................",
            "................",
        )

        /** Synthetic exit-left mirror of the field roundabout mask. */
        val ROUNDABOUT_LEFT_FIELD = ROUNDABOUT_RIGHT_FIELD.map { it.reversed() }
    }

    @Test
    fun `classifies left hook as turn left`() {
        val m = IconClassifier.classify(pixelsFromArt(LEFT_HOOK))
        assertEquals(NavManeuver.TURN_LEFT, m.maneuver)
        assertTrue("score=${m.score}", m.score > 0.5)
    }

    @Test
    fun `classifies right hook as turn right`() {
        val m = IconClassifier.classify(pixelsFromArt(RIGHT_HOOK))
        assertEquals(NavManeuver.TURN_RIGHT, m.maneuver)
        assertTrue("score=${m.score}", m.score > 0.5)
    }

    @Test
    fun `classifies up arrow as straight`() {
        val m = IconClassifier.classify(pixelsFromArt(STRAIGHT))
        assertEquals(NavManeuver.STRAIGHT, m.maneuver)
        assertTrue("score=${m.score}", m.score > 0.5)
    }

    @Test
    fun `tolerates shifted icons`() {
        // Arrow occupies the middle 16 of 24 cols (offset rendering).
        val padded = LEFT_HOOK.map { "....$it...." }
        val m = IconClassifier.classify(pixelsFromArt(padded))
        assertEquals(NavManeuver.TURN_LEFT, m.maneuver)
    }

    @Test
    fun `handles dark-on-light polarity`() {
        val m = IconClassifier.classify(pixelsFromArt(RIGHT_HOOK, fg = DARK, bg = WHITE))
        assertEquals(NavManeuver.TURN_RIGHT, m.maneuver)
    }

    @Test
    fun `tolerates dropout noise`() {
        val noisy = LEFT_HOOK.mapIndexed { y, row ->
            row.mapIndexed { x, c ->
                if ((x * 7 + y * 13) % 11 == 0) '.' else c
            }.joinToString("")
        }
        val m = IconClassifier.classify(pixelsFromArt(noisy))
        assertEquals(NavManeuver.TURN_LEFT, m.maneuver)
    }

    @Test
    fun `thin right hook is turn right never uturn`() {
        // Field regression: the old whole-icon matcher scored this art
        // < 0.30 ("?") or matched UTURN at 0.37 on a 40 m right turn.
        val m = IconClassifier.classify(pixelsFromArt(RIGHT_HOOK))
        assertEquals(NavManeuver.TURN_RIGHT, m.maneuver)
        assertTrue("score=${m.score}", m.score >= IconClassifier.HEAD_THRESHOLD)
    }

    @Test
    fun `thick field left hook is turn left not unknown`() {
        // Field regression 2_maps_starpath_turn_left: thick hook tied L=R=0.95.
        val m = IconClassifier.classify(pixelsFromArt(THICK_LEFT_FIELD))
        assertEquals(NavManeuver.TURN_LEFT, m.maneuver)
        assertTrue("score=${m.score}", m.score >= IconClassifier.HEAD_THRESHOLD)
        val mirrored = IconClassifier.classify(pixelsFromArt(THICK_RIGHT_FIELD))
        assertEquals(NavManeuver.TURN_RIGHT, mirrored.maneuver)
    }

    @Test
    fun `destination pin mirrors both collapse to one dest`() {
        // Field regression maps_starpath_destination_left/right: pin+road
        // largeIcon mimicked a large moment delta (L=0.90 / R=0.88).
        val left = IconClassifier.classify(pixelsFromArt(DEST_PIN_LEFT))
        assertEquals(NavManeuver.DESTINATION, left.maneuver)
        assertTrue("score=${left.score}", left.score >= IconClassifier.HEAD_THRESHOLD)
        val right = IconClassifier.classify(pixelsFromArt(DEST_PIN_RIGHT))
        assertEquals(NavManeuver.DESTINATION, right.maneuver)
        assertTrue("score=${right.score}", right.score >= IconClassifier.HEAD_THRESHOLD)
    }

    @Test
    fun `roundabout exit right is turn right never destination`() {
        // Field regression roundabout_right (3.5 km remaining): the loop is
        // wide mid-icon so the old anywhere-in-bottom-half gate returned
        // DESTINATION at 0.85. The width does not persist to the bottom
        // edge (3-wide stem), so it must not be DEST; the enclosed loop
        // re-enables the exit-side verdict via the diluted moments delta.
        val m = IconClassifier.classify(pixelsFromArt(ROUNDABOUT_RIGHT_FIELD))
        assertEquals(NavManeuver.TURN_RIGHT, m.maneuver)
        assertTrue("score=${m.score}", m.score >= IconClassifier.HEAD_THRESHOLD)
    }

    @Test
    fun `roundabout exit left mirror is turn left`() {
        val m = IconClassifier.classify(pixelsFromArt(ROUNDABOUT_LEFT_FIELD))
        assertEquals(NavManeuver.TURN_LEFT, m.maneuver)
        assertTrue("score=${m.score}", m.score >= IconClassifier.HEAD_THRESHOLD)
    }

    @Test
    fun `down chevron is unknown`() {
        val m = IconClassifier.classify(pixelsFromArt(DOWN_CHEVRON))
        assertEquals(NavManeuver.UNKNOWN, m.maneuver)
    }

    @Test
    fun `head without shaft is unknown`() {
        val m = IconClassifier.classify(pixelsFromArt(HEAD_ONLY))
        assertEquals(NavManeuver.UNKNOWN, m.maneuver)
    }

    @Test
    fun `detail exposes mask and per orientation scores`() {
        val d = IconClassifier.classifyDetailed(pixelsFromArt(RIGHT_HOOK))
        assertEquals(NavManeuver.TURN_RIGHT, d.maneuver)
        assertEquals(16, d.maskArt.lines().size)
        assertTrue(d.maskArt.contains('#'))
        assertEquals(
            setOf(
                NavManeuver.TURN_LEFT,
                NavManeuver.TURN_RIGHT,
                NavManeuver.STRAIGHT,
                NavManeuver.DESTINATION,
            ),
            d.scores.keys,
        )
    }

    @Test
    fun `blank icon is unknown`() {
        val blank = List(16) { ".".repeat(16) }
        val m = IconClassifier.classify(pixelsFromArt(blank))
        assertEquals(NavManeuver.UNKNOWN, m.maneuver)
    }

    @Test
    fun `empty pixels are unknown`() {
        val m = IconClassifier.classify(IconClassifier.IconPixels(0, 0, IntArray(0)))
        assertEquals(NavManeuver.UNKNOWN, m.maneuver)
    }
}
