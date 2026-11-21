package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.people.Person

/** From this many people on, a list of people gets a search field. */
internal const val PEOPLE_SEARCH_FROM = 6

/**
 * A list of people to choose from, the same everywhere: the support is not among them (it is no contact of one's own,
 * ADR 0065), and from [PEOPLE_SEARCH_FROM] people on [PeopleSearchField] searches by name. [all] are all listed people,
 * [found] those the search matches.
 */
@Stable
internal class PeopleSearch(val all: List<Person>, private val queryState: MutableState<String>) {
    var query: String
        get() = queryState.value
        set(value) { queryState.value = value }

    /** Few people need no search. */
    val searchable: Boolean get() = all.size >= PEOPLE_SEARCH_FROM

    val found: List<Person> get() = all.filter { matches(it, query) }

    companion object {
        /** Own name for the person or the name from the profile, upper and lower case do not count. */
        fun matches(person: Person, query: String): Boolean = matchesSearch(query, person.name, person.profileName)

        /** [people] without the support. */
        fun listed(people: List<Person>, supportPeer: String?): List<Person> = people.filter { it.peer != supportPeer }
    }
}

/** [people] (already narrowed by the caller, e.g. to those with the app) as a [PeopleSearch]. */
@Composable
internal fun rememberPeopleSearch(controller: AppController, people: List<Person>): PeopleSearch {
    val supportPeer by controller.supportPeer.collectAsState()
    val query = rememberSaveable { mutableStateOf("") }
    val all = remember(people, supportPeer) { PeopleSearch.listed(people, supportPeer) }
    return remember(all, query) { PeopleSearch(all, query) }
}

/** The search field from [PEOPLE_SEARCH_FROM] people on, and a calm sentence if nobody matches. */
@Composable
internal fun PeopleSearchField(search: PeopleSearch, modifier: Modifier = Modifier) {
    if (!search.searchable) return
    SearchField(search.query, { search.query = it }, texts.chat.searchName, modifier)
    if (search.found.isEmpty()) NoSearchResults()
}
