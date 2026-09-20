package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import org.junit.Assert.assertEquals
import org.junit.Test

class GadgetbridgeSenderTest {

    private fun card(
        title: String = "▶▶",
        text: String = "Nguyen Hue",
        sub: String = "DEST 450 m · 6 min",
    ) = NavFormatter.Card(title, text, sub, NavState.ENROUTE, NavManeuver.TURN_RIGHT)

    @Test
    fun `body joins text and sub with newline`() {
        assertEquals("Nguyen Hue\nDEST 450 m · 6 min", GadgetbridgeSender.bodyOf(card()))
    }

    @Test
    fun `blank sub yields text-only body`() {
        assertEquals("Nguyen Hue", GadgetbridgeSender.bodyOf(card(sub = "")))
    }

    @Test
    fun `payload is a single-element title-body array`() {
        assertEquals(
            """[{"title":"▶▶","body":"Nguyen Hue\nDEST 450 m · 6 min"}]""",
            GadgetbridgeSender.notificationDataJson("▶▶", "Nguyen Hue\nDEST 450 m · 6 min"),
        )
    }

    @Test
    fun `quotes backslashes and controls are escaped`() {
        assertEquals(
            """[{"title":"say \"hi\" \\ bye","body":"a\nb\tc"}]""",
            GadgetbridgeSender.notificationDataJson("say \"hi\" \\ bye", "a\nb\tc"),
        )
    }

    @Test
    fun `diacritics and glyphs pass through verbatim`() {
        val payload = GadgetbridgeSender.notificationDataJson("◀◀", "Tòa Nhà CT5")
        assertEquals("""[{"title":"◀◀","body":"Tòa Nhà CT5"}]""", payload)
    }
}
