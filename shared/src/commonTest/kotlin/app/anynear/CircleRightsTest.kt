package app.anynear

import app.anynear.images.AvatarPattern
import app.anynear.images.ImageRef
import app.anynear.core.b64
import app.anynear.people.Member
import app.anynear.people.MemberDirectory
import app.anynear.people.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pictures of circles and people (ADR 0012). The ANMP core checks key changes (KeyChangeTest in the ANMP repo). */
class CircleRightsTest {
    @Test
    fun generatedPicturesAreStableAndDistinct() {
        val a = AvatarPattern.circle("kreis-1", 6)
        assertEquals(a, AvatarPattern.circle("kreis-1", 6))
        assertNotEquals(a, AvatarPattern.circle("kreis-2", 6))
        val p = AvatarPattern.person("d:geraet", 6)
        assertEquals(p, AvatarPattern.person("d:geraet", 6))
        assertEquals(25, p.cells.size)
        // Mirrored left and right, never empty.
        for (row in 0 until 5) {
            assertEquals(p.cells[row * 5], p.cells[row * 5 + 4])
            assertEquals(p.cells[row * 5 + 1], p.cells[row * 5 + 3])
        }
        assertTrue(p.cells.count { it } >= 6)
        assertTrue(a.rings.all { it.countOneBits() >= 3 })
        // Many IDs, hardly any identical patterns.
        val patterns = (0 until 500).map { AvatarPattern.person("d:$it", 6) }.toSet()
        assertTrue(patterns.size > 495, "${patterns.size} verschiedene")
    }

    /** The person shows the newest picture from their entries (circles and contacts). */
    @Test
    fun personShowsNewestImage() {
        val old = ImageRef("alt", "k", "space", "h1")
        val new = ImageRef("neu", "k", "space", "h2")
        val key = b64.encode(ByteArray(32) { 8 })
        val members = listOf(
            Member("k1", "ben", "Ben", key, image = old, imageTs = 10),
            Member("k2", "ben", "Ben", key, image = new, imageTs = 30),
        )
        assertEquals(new, Person.merge(members, emptyList(), listOf("k1", "k2")).single().image)
        assertNull(Person.merge(listOf(Member("k1", "ben", "Ben", key)), emptyList(), listOf("k1")).single().image)
    }
}
