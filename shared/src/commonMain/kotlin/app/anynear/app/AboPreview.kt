package app.anynear.app

import app.anynear.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Subscription preview (ADR 0076): plans with expected prices and quantities, as the website delivers them at
 * [AppConfig.ABO_PREVIEW_URL]. Prices are never in the app so that they can change without a new version.
 */
@Serializable
data class AboPreview(
    val preview: Boolean = true,
    val currency: String = "EUR",
    /** Free months for everyone who registers interest now. */
    val trialMonths: Int = 0,
    /** Page “Interesse bekunden” per language (`de`, `en`). */
    val interest: Map<String, String> = emptyMap(),
    val plans: List<AboPlan> = emptyList(),
    /** The same building blocks as a comparison table per language (`de`, `en`); empty for older previews. */
    val compare: Map<String, AboCompare> = emptyMap(),
) {
    val free: AboPlan? get() = plans.firstOrNull { it.monthlyCents == 0L }

    /** Comparison table in the language, otherwise English; `null` if the website sends none (then features apply). */
    fun compare(german: Boolean): AboCompare? =
        (compare[if (german) "de" else "en"] ?: compare["en"])?.takeIf { it.rows.isNotEmpty() }

    /**
     * The cheapest plan that allows more for [limit] than applies today ([current], default: free), with its
     * value; `null` if none allows more or the preview names no limits (ADR 0076 §1a).
     */
    fun upgradeFor(limit: AboLimit, current: Int = free?.limits?.let(limit.of) ?: 0): Pair<AboPlan, Int>? =
        paid.sortedBy { it.monthlyCents }
            .firstNotNullOfOrNull { p -> p.limits?.let(limit.of)?.takeIf { it > current }?.let { p to it } }
    val paid: List<AboPlan> get() = plans.filter { it.monthlyCents > 0 }

    /** Page “Interesse bekunden” with preselected plan and billing period; `null` if the website names none. */
    fun interestUrl(plan: AboPlan, yearly: Boolean, german: Boolean): String? {
        val base = interest[if (german) "de" else "en"] ?: interest["en"] ?: return null
        if (!base.startsWith("https://")) return null
        return "$base?paket=${plan.id}&takt=${if (yearly) "jahr" else "monat"}"
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** `null` if the text is not a usable plan directory. */
        fun parse(text: String): AboPreview? =
            runCatching { json.decodeFromString<AboPreview>(text) }.getOrNull()?.takeIf { it.plans.isNotEmpty() }
    }
}

/**
 * A plan. The app only knows name, prices and whether it is recommended; what is included is given as finished text in
 * [features] per language so that the building blocks can change without a new app version.
 */
@Serializable
data class AboPlan(
    val id: String,
    /** Name per language (`de`, `en`). */
    val name: Map<String, String> = emptyMap(),
    val monthlyCents: Long = 0,
    val yearlyCents: Long = 0,
    /** Included building blocks as lines per language. */
    val features: Map<String, List<String>> = emptyMap(),
    val recommended: Boolean = false,
    /** What a message may carry in the plan (ADR 0076 §1a); `null` for older previews. */
    val limits: AboLimits? = null,
) {
    /** Name in the language, otherwise English, otherwise the ID. */
    fun name(german: Boolean): String = name[lang(german)] ?: name["en"] ?: id.replaceFirstChar { it.uppercase() }

    /** Saving of the yearly price compared with twelve monthly fees in percent, rounded; 0 without benefit. Like website and web app. */
    fun yearlySaving(): Int =
        if (monthlyCents <= 0) 0 else maxOf(0, ((monthlyCents * 12 - yearlyCents) * 100.0 / (monthlyCents * 12)).roundToInt())

    /** Yearly price converted to the month, rounded to cents. */
    fun yearlyPerMonth(): Long = (yearlyCents / 12.0).roundToLong()

    /** Building blocks in the language, otherwise English. */
    fun features(german: Boolean): List<String> = features[lang(german)] ?: features["en"].orEmpty()

    private fun lang(german: Boolean) = if (german) "de" else "en"
}

/** Comparison table: rows with a label and a short value per plan ID, plus what all plans include. */
@Serializable
data class AboCompare(val rows: List<AboCompareRow> = emptyList(), val note: String? = null)

/** One row of the comparison; a plan without a value shows a dash. */
@Serializable
data class AboCompareRow(val label: String = "", val values: Map<String, String> = emptyMap()) {
    fun value(plan: AboPlan): String = values[plan.id] ?: "–"
}

/**
 * Limits of a message in a plan: text in characters, attachment in MB, attachments per message, longest voice message
 * in minutes (ADR 0099; 0 = not named, older previews).
 */
@Serializable
data class AboLimits(val attachmentMb: Int = 0, val attachments: Int = 0, val voiceMinutes: Int = 0)

/** Which limit has been reached. */
enum class AboLimit(val of: (AboLimits) -> Int) {
    ATTACHMENT_SIZE({ it.attachmentMb }),
    ATTACHMENT_COUNT({ it.attachments }),
    VOICE_LENGTH({ it.voiceMinutes }),
}

/** Loads the subscription preview from the website. */
object AboPreviews {
    private var cached: AboPreview? = null

    /** Like [load], but successfully loaded only once per app start (for hints in the chat). */
    suspend fun loadOnce(): AboPreview? = cached ?: load()?.also { cached = it }

    private val client by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
            }
        }
    }

    /** `null` without internet or on an unusable response. */
    suspend fun load(url: String = AppConfig.ABO_PREVIEW_URL): AboPreview? = runCatching {
        val response = client.get(url)
        if (!response.status.isSuccess()) null else AboPreview.parse(response.bodyAsText())
    }.getOrNull()
}

