package app.anynear.app

import app.anynear.AppConfig
import app.anynear.i18n.Language
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * „Was ist neu“ of the version the app is offering, from `release-notes/X.Y.Z.md` of the App repo as the website
 * delivers it at [AppConfig.APP_NOTES_URL] (text of the GitHub release of exactly this build). Only text, never markup.
 */
data class ReleaseNotes(val version: String, val intro: List<String>, val sections: List<Section>) {
    /** [title] is empty for entries without a heading. */
    data class Section(val title: String, val items: List<String>)

    companion object {
        /** The [language] part of the website's answer; `null` for an unusable answer or a language without entries. */
        fun parse(text: String, language: Language): ReleaseNotes? = runCatching {
            val o = Json.parseToJsonElement(text) as? JsonObject ?: return null
            val version = (o["version"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            if (!VERSION.matches(version)) return null
            val part = o[if (language == Language.GERMAN) "de" else "en"] as? JsonObject ?: return null
            val intro = strings(part["intro"]).take(MAX_PARAGRAPHS)
            val sections = (part["sections"] as? JsonArray).orEmpty().mapNotNull { s ->
                val so = s as? JsonObject ?: return@mapNotNull null
                val title = (so["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.take(MAX_TEXT).orEmpty()
                val items = strings(so["items"]).take(MAX_ITEMS)
                if (items.isEmpty()) null else Section(title, items)
            }.take(MAX_SECTIONS)
            if (sections.isEmpty()) null else ReleaseNotes(version, intro, sections)
        }.getOrNull()

        private fun strings(e: Any?): List<String> = (e as? JsonArray).orEmpty().mapNotNull { item ->
            (item as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.take(MAX_TEXT)?.takeIf { it.isNotEmpty() }
        }

        private val VERSION = Regex("""\d+\.\d+\.\d+(-pre\.\d+)?""")
        private const val MAX_TEXT = 600
        private const val MAX_ITEMS = 40
        private const val MAX_SECTIONS = 12
        private const val MAX_PARAGRAPHS = 4
    }
}

/** Loads [ReleaseNotes] from the website, like [SupportLinks]. */
object ReleaseNotesLoader {
    private val client by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
            }
        }
    }

    /**
     * By [version] (own updater, App Store) or, where the store names none, by [build] (Google Play); with [since], the
     * installed version, only what is new since then. `null` without internet, for an unknown version, when nothing is
     * new or for an unusable answer: the dialog then simply shows no notes.
     */
    suspend fun load(version: String?, build: Long?, since: String?, language: Language, url: String = AppConfig.APP_NOTES_URL): ReleaseNotes? = runCatching {
        if (version.isNullOrBlank() && build == null) return null
        val response = client.get(url) {
            if (!version.isNullOrBlank()) parameter("version", version) else parameter("build", build)
            if (!since.isNullOrBlank()) parameter("since", since)
        }
        if (!response.status.isSuccess()) null else ReleaseNotes.parse(response.bodyAsText(), language)
    }.getOrNull()
}
