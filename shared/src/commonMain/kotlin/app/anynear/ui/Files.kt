package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.data.PersonalFile
import app.anynear.data.PersonalFiles
import app.anynear.i18n.texts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow

// Storage: „Nur für mich“ is „Meine Ablage“ (ADR 0040, 0041), next to it the storage of every circle (ADR 0049). Both
// show the same pages (PersonalFilesUi.kt).

/** Long file names in lists: shorten at the front, the extension stays visible; the full name is in the detail. */
internal fun shortFileName(name: String, max: Int = 26): String {
    if (name.length <= max) return name
    val ext = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }?.let { ".$it" }.orEmpty()
    return name.dropLast(ext.length).take(max - ext.length - 1).trimEnd() + "…" + ext
}

/** „Noch leer“ or the number of files in [files]. */
@Composable
private fun filesSummary(files: PersonalFiles?): String {
    val list by (files?.list ?: NoFiles).collectAsState()
    return if (list.isEmpty()) texts.files.noFilesYet else texts.files.fileCount(list.size)
}

/** In short, what is in the storage of the circle [circleId]. */
@Composable
internal fun circleFilesSummary(controller: AppController, circleId: String): String {
    val byCircle by (controller.circleFiles?.byCircle ?: NoCircleFiles).collectAsState()
    return filesSummary(byCircle[circleId])
}

/** Tab „Ablage“: personal storage and the storages of your own circles, above them the search across all (ADR 0070 §7). */
@Composable
internal fun FilesScreen(
    controller: AppController,
    onOpenPersonal: () -> Unit,
    onOpenCircle: (circleId: String) -> Unit,
    onOpenPasswords: () -> Unit,
    onOpenFile: (fileId: String, circleId: String?) -> Unit = { _, _ -> },
) {
    val circles by controller.circles.collectAsState()
    val support by controller.supportPeer.collectAsState()
    var query by remember { mutableStateOf("") }
    // „Mehr Speicher“: subscription plans and registering interest (ADR 0076).
    var plans by remember { mutableStateOf(false) }
    if (plans) {
        AboPreviewScreen { plans = false }
        return
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.setup.tabFiles)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // One online storage space for all storages together (ADR 0079), at the very top.
            StorageMeter(controller) { plans = true }
            SearchField(query, { query = it }, texts.files.searchAll)
            if (query.isNotBlank()) {
                SearchAllResults(controller, query, onOpenFile)
                return@Column
            }
            AppSection(texts.files.personal) {
                AppRow(
                    title = texts.files.justForMe,
                    subtitle = filesSummary(controller.personalFiles),
                    icon = AppIcons.Schloss,
                    chevron = true,
                    onClick = onOpenPersonal,
                )
            }
            // Only entry point to the password area, between personal storage and circles.
            AppSection {
                AppRow(
                    title = texts.passwords.title,
                    subtitle = texts.passwords.entrySubtitle,
                    icon = AppIcons.Schluessel,
                    chevron = true,
                    onClick = onOpenPasswords,
                )
            }
            // Support requests (ADR 0065) have no storage for users; the support still sees them.
            val active = remember(circles, support) { circles.filter { !it.removed && !controller.isSupportCircle(it.id) } }
            // Without a circle only the sentence, no empty card.
            if (active.isEmpty()) {
                AppSection(texts.files.withYourCircles) { SectionContent { Text(texts.files.noCirclesYet, style = MaterialTheme.typography.bodyMedium) } }
            } else AppSection(texts.files.withYourCircles) {
                active.forEach { circle ->
                    AppRow(
                        title = circle.name,
                        subtitle = circleFilesSummary(controller, circle.id),
                        leading = { CircleAvatar(controller, circle.id, circle.image, 40.dp) },
                        chevron = true,
                        onClick = { onOpenCircle(circle.id) },
                    )
                }
            }
        }
    }
}

/** A storage for the search across all: „Meine Ablage“ ([circleId] `null`) or that of a circle. */
private class SearchPlace(val title: String, val circleId: String?, val store: PersonalFiles)

/**
 * Hits of the search across all storages, grouped by place: first „Nur für mich“, then per circle. Tapping opens the
 * file in its place; there it says who sees it and what deleting means. Deliberately no mixed list.
 */
@Composable
private fun SearchAllResults(controller: AppController, query: String, onOpenFile: (String, String?) -> Unit) {
    val circles by controller.circles.collectAsState()
    val byCircle by (controller.circleFiles?.byCircle ?: NoCircleFiles).collectAsState()
    val personal by (controller.personalFiles?.list ?: NoFiles).collectAsState()
    val readVersion by (controller.docReading?.version ?: NoVersion).collectAsState()
    val places = remember(circles, byCircle, controller.personalFiles) {
        listOfNotNull(controller.personalFiles?.let { SearchPlace(texts.files.justForMe, null, it) }) +
            circles.filter { !it.removed && !controller.isSupportCircle(it.id) }
                .mapNotNull { c -> byCircle[c.id]?.let { SearchPlace(c.name, c.id, it) } }
    }
    val results by produceState<List<Pair<SearchPlace, List<PersonalFile>>>?>(null, query, places, personal, readVersion) {
        value = withContext(Dispatchers.Default) {
            places.map { place ->
                val files = place.store.list.value
                val byId = files.associateBy { it.id }
                place to docIndexOf(files, controller.docReading).search(query, "").mapNotNull { byId[it] }
            }.filter { it.second.isNotEmpty() }
        }
    }
    val found = results ?: return
    if (found.isEmpty()) {
        NoSearchResults()
        return
    }
    found.forEach { (place, files) ->
        AppSection(place.title) {
            files.forEach { file -> key(place.circleId, file.id) { PersonalFileRow(place.store, file) { onOpenFile(file.id, place.circleId) } } }
        }
    }
}

private val NoVersion = MutableStateFlow(0)

private val NoFiles = MutableStateFlow(emptyList<PersonalFile>())
private val NoCircleFiles: StateFlow<Map<String, PersonalFiles>> = MutableStateFlow(emptyMap())
