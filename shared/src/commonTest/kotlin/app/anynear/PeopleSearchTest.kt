package app.anynear

import app.anynear.people.Person
import app.anynear.ui.PEOPLE_SEARCH_FROM
import app.anynear.ui.PeopleSearch
import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeopleSearchTest {
    private fun person(peer: String, name: String, profileName: String = name) =
        Person(peer, name, "id-$peer", null, emptyList(), null, null, profileName = profileName)

    @Test
    fun supportIsNotListed() {
        val people = listOf(person("a", "Anna"), person("s", "anynear Support"))
        assertEquals(listOf("a"), PeopleSearch.listed(people, "s").map { it.peer })
        assertEquals(2, PeopleSearch.listed(people, null).size)
    }

    @Test
    fun searchFindsOwnAndProfileNameIgnoringCase() {
        val anna = person("a", "Mama", profileName = "Anna Berg")
        assertTrue(PeopleSearch.matches(anna, "mam"))
        assertTrue(PeopleSearch.matches(anna, "BERG"))
        assertTrue(PeopleSearch.matches(anna, "  "))
        assertFalse(PeopleSearch.matches(anna, "Otto"))
    }

    @Test
    fun searchFieldOnlyFromManyPeople() {
        val few = (1 until PEOPLE_SEARCH_FROM).map { person("$it", "P$it") }
        assertFalse(PeopleSearch(few, mutableStateOf("")).searchable)
        assertTrue(PeopleSearch(few + person("x", "X"), mutableStateOf("")).searchable)
    }

    @Test
    fun foundFollowsTheQuery() {
        val search = PeopleSearch(listOf(person("a", "Anna"), person("b", "Bernd")), mutableStateOf(""))
        assertEquals(2, search.found.size)
        search.query = "ber"
        assertEquals(listOf("b"), search.found.map { it.peer })
    }
}
