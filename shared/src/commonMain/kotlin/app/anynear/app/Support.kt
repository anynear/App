package app.anynear.app

import app.anynear.AppConfig
import app.anynear.alerts.Alert
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Kind of a request to support: error (with log) or question. */
enum class SupportKind { Problem, Question }

/**
 * Support as a chat: every request becomes its own small circle with the support identity
 * ([AppConfig.SUPPORT_CONTACT_LINK]). Here only what can be checked without the core: name of the circle and texts.
 */
object Support {
    /** Length of the circle name; truncated as in [AppController.createCircle]. */
    const val MAX_CIRCLE_NAME = 40

    /** Subject at most this long so that "Support: …" still fits into the circle name. */
    const val MAX_SUBJECT = 80

    /** `Support: <Betreff>`, truncated to [MAX_CIRCLE_NAME], at the word boundary with "…". */
    fun circleName(prefix: String, subject: String): String {
        val full = "$prefix ${subject.trim().replace(Regex("\\s+"), " ")}"
        if (full.length <= MAX_CIRCLE_NAME) return full
        val cut = full.take(MAX_CIRCLE_NAME - 1)
        val space = cut.lastIndexOf(' ')
        val head = if (space > prefix.length) cut.take(space) else cut
        return head.trimEnd() + "…"
    }

    /** First message in the circle: kind and subject, description, below that version and platform. */
    fun firstMessage(kindLabel: String, subject: String, description: String, version: String?, platform: String): String =
        buildString {
            appendLine("$kindLabel: ${subject.trim()}")
            appendLine()
            appendLine(description.trim())
            appendLine()
            append("${AppConfig.APP_NAME} ${version ?: "?"} · $platform")
        }

    /**
     * Subject of a new request if [text] is the direct message to support („Neue Anfrage: <Betreff>“ or
     * "New request: …", below that an invitation link); otherwise `null`. [prefixes] are the beginnings in all languages.
     */
    fun requestSubject(text: String, prefixes: List<String>): String? {
        val lines = text.trim().lines()
        val first = lines.firstOrNull() ?: return null
        val prefix = prefixes.firstOrNull { first.startsWith(it) } ?: return null
        if (lines.drop(1).none { it.contains("/k#") }) return null
        return first.removePrefix(prefix).trim().ifEmpty { null }
    }

    /** Invitation link in the direct message to support (the line with `/k#`), otherwise `null`. */
    fun inviteLink(text: String): String? = text.lines().map { it.trim() }.firstOrNull { it.contains("/k#") }

    /**
     * Is the alert for a request already in the channel? The support identity can run on several devices (app and
     * web app); whoever alerts first wins, the others do not alert again. Like `alreadyAlerted` in the web app.
     */
    fun alreadyAlerted(alerts: List<Alert>, channelId: String, text: String, requestMillis: Long): Boolean =
        alerts.any { it.channelId == channelId && it.text.trim() == text.trim() && it.timestampMillis >= requestMillis - ALERT_MARGIN_MILLIS }

    /**
     * Contact link from `/v1/support.json` (ADR 0084 §7), `null` for an unusable answer. Network and name are then
     * checked by the core. Like `parseSupportJson` in the web app.
     */
    fun parseSupportJson(text: String): String? = runCatching {
        val o = Json.parseToJsonElement(text) as? JsonObject ?: return null
        val version = (o["version"] as? JsonPrimitive)?.content
        val contact = (o["contact"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        contact?.takeIf { version == "1" && SUPPORT_LINK.matches(it) }
    }.getOrNull()

    private val SUPPORT_LINK = Regex("""^https://anynear\.app/c#[A-Za-z0-9_-]+$""")

    /** Leeway for differing clocks of the devices. */
    private const val ALERT_MARGIN_MILLIS = 60_000L
}

/** Loads the support link from the website (ADR 0084 §7), like [AboPreviews]. */
object SupportLinks {
    private val client by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
            }
        }
    }

    /** `null` without internet or for an unusable answer. */
    suspend fun load(url: String = AppConfig.SUPPORT_LINK_URL): String? = runCatching {
        val response = client.get(url)
        if (!response.status.isSuccess()) null else Support.parseSupportJson(response.bodyAsText())
    }.getOrNull()
}
