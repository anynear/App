package app.anynear.app

import app.anynear.data.TextFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A crash (ADR 0082): what happened, without content, scrubbed according to [CrashReports.scrub]. */
@Serializable
data class CrashReport(
    val kind: Kind,
    /** First crash of this kind. */
    val atMillis: Long,
    /** Full identifier of the build, e.g. `0.3.0-1a2b3c4`. */
    val build: String? = null,
    val platform: String,
    val os: String? = null,
    val model: String? = null,
    /** First line: exception with text, or reason for native crashes. */
    val error: String,
    /** Call stack as the platform delivers it. */
    val trace: String = "",
    /** Last lines of the log before the crash, oldest first. */
    val log: List<String> = emptyList(),
    /** How often exactly this crash occurred ([CrashReports.signature]). */
    val count: Int = 1,
    val lastMillis: Long = atMillis,
) {
    @Serializable
    enum class Kind {
        /** Uncaught exception in Kotlin code. */
        Crash,

        /** Crash outside of Kotlin (Android native, iOS signal from MetricKit). */
        Native,

        /** Android terminated the app because it stopped responding (ANR). */
        NotResponding,
    }
}

/** Rules for crash reports (ADR 0082): scrub, merge, limit, as text. Without platform, tested. */
object CrashReports {
    const val MAX_REPORTS = 5
    const val MAX_BYTES = 64 * 1024
    const val MAX_AGE_MILLIS = 14L * 24 * 60 * 60 * 1000
    const val LOG_LINES = 200
    private const val TOP_FRAMES = 5

    private val fragment = Regex("""(https?://\S*?)#\S*""")
    // Without `/` so that paths like `app/assets/index-B1x2.js` stay readable; keys in anynear are base64url.
    private val longToken = Regex("""[A-Za-z0-9+_=-]{20,}""")
    private val hexToken = Regex("""\b[0-9a-fA-F]{20,}\b""")
    private val uuid = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")

    /**
     * Takes out what does not belong in a report: everything after `#` in addresses (invitation and contact links carry
     * keys there), long hex and Base64 sequences and paths under the own data folder [dataDirs].
     * Lines of a call stack (`at …`, `0 Shared 0x… kfun:…`) keep their class and function names because these
     * are not digit sequences of 20 characters without a dot.
     */
    fun scrub(text: String, dataDirs: List<String> = emptyList()): String {
        var t = text
        dataDirs.filter { it.length > 1 }.forEach { t = t.replace(it, "…") }
        t = fragment.replace(t) { "${it.groupValues[1]}#…" }
        t = hexToken.replace(t, "…")
        // Identifiers of the binary (UUID, e.g. `binaryUUID` from MetricKit) stay: without them no symbols.
        t = longToken.replace(t) { m -> if (!uuid.matches(m.value) && m.value.any(Char::isDigit) && m.value.any(Char::isLetter)) "…" else m.value }
        return t
    }

    /** Same crashes: same kind, same exception (without text) and same topmost calls. */
    fun signature(r: CrashReport): String {
        val errorType = r.error.substringBefore(':').trim()
        val frames = r.trace.lines().map(String::trim).filter { it.startsWith("at ") || it.contains("kfun:") }.take(TOP_FRAMES)
        return listOf(r.kind.name, errorType, *frames.toTypedArray()).joinToString("|")
    }

    /**
     * Add [new] to [reports]: ones older than 14 days drop out, same ones are merged (counter, newest
     * call stack and newest log), at most [MAX_REPORTS], newest last.
     */
    fun add(reports: List<CrashReport>, new: CrashReport, now: Long): List<CrashReport> {
        val fresh = reports.filter { now - it.lastMillis <= MAX_AGE_MILLIS }
        val sig = signature(new)
        val same = fresh.firstOrNull { signature(it) == sig }
        val merged = if (same == null) clip(new) else clip(
            same.copy(count = same.count + new.count, lastMillis = maxOf(same.lastMillis, new.lastMillis), trace = new.trace, log = new.log, build = new.build ?: same.build),
        )
        return (fresh.filterNot { it === same } + merged).sortedBy { it.lastMillis }.takeLast(MAX_REPORTS)
    }

    /** Shortens log and call stack until the report as text is at most [MAX_BYTES] in size. */
    fun clip(r: CrashReport): CrashReport {
        var c = r.copy(log = r.log.takeLast(LOG_LINES))
        while (text(c).encodeToByteArray().size > MAX_BYTES && c.log.isNotEmpty()) c = c.copy(log = c.log.drop(maxOf(1, c.log.size / 4)))
        if (text(c).encodeToByteArray().size > MAX_BYTES) c = c.copy(trace = c.trace.take(MAX_BYTES / 2) + "\n…")
        return c
    }

    /** Report as text for `absturz.txt` and “Was geschickt wird”. Technical, without translation, like the log. */
    fun text(r: CrashReport): String = buildString {
        appendLine("kind: ${r.kind.name}")
        appendLine("first: ${isoUtc(r.atMillis)}")
        if (r.count > 1) appendLine("last: ${isoUtc(r.lastMillis)} (${r.count}x)")
        appendLine("build: ${r.build ?: "?"}")
        appendLine("platform: ${r.platform}${r.os?.let { " $it" }.orEmpty()}${r.model?.let { " · $it" }.orEmpty()}")
        appendLine("error: ${r.error}")
        if (r.trace.isNotBlank()) {
            appendLine()
            appendLine(r.trace.trimEnd())
        }
        if (r.log.isNotEmpty()) {
            appendLine()
            appendLine("log (seconds before the crash):")
            r.log.forEach(::appendLine)
        }
    }

    /**
     * From the traces of an ANR (all threads, often hundreds of KB) only the main thread: the block from `"main"` up to
     * the next blank line. Without a main thread the first 200 lines.
     */
    fun anrMainThread(traces: String): String {
        val lines = traces.lines()
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        if (start < 0) return lines.take(200).joinToString("\n")
        val end = (start + 1 until lines.size).firstOrNull { lines[it].isBlank() } ?: lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    /** All reports in one file, separated by a line. */
    fun text(reports: List<CrashReport>): String = reports.joinToString("\n----------------------------------------\n\n") { text(it) }

    /** `2026-11-08T14:03:12Z`, without a time zone library. */
    internal fun isoUtc(millis: Long): String {
        val seconds = millis.floorDiv(1000)
        val days = seconds.floorDiv(86_400)
        val rest = seconds - days * 86_400
        // Days since 1970 into a date (Howard Hinnant, civil_from_days).
        val z = days + 719_468
        val era = z.floorDiv(146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        fun two(v: Long) = v.toString().padStart(2, '0')
        return "$y-${two(m)}-${two(d)}T${two(rest / 3600)}:${two(rest / 60 % 60)}:${two(rest % 60)}Z"
    }
}

/**
 * Records crash reports on the device (ADR 0082). Is created before everything else so that crashes at startup
 * get in too; [record] writes synchronously and never throws because it runs during the crash. The file lies in the
 * private data folder, unencrypted: at the crash the local key may not be there yet, and the content is
 * scrubbed. Nothing is sent; the UI does that only after “Senden” via support (ADR 0065).
 */
class CrashRecorder(
    private val file: TextFile,
    val platform: String,
    private val build: () -> String?,
    private val os: String?,
    private val model: String?,
    private val dataDirs: List<String> = emptyList(),
    private val now: () -> Long,
) {
    /** Last lines of the log; set by [createAppController] as soon as the log is there. */
    var logLines: () -> List<String> = { emptyList() }

    private val _reports = MutableStateFlow(load())
    val reports: StateFlow<List<CrashReport>> = _reports.asStateFlow()

    /** Uncaught exception in Kotlin code. */
    fun record(error: Throwable) {
        runCatching {
            record(CrashReport.Kind.Crash, error.toString(), error.stackTraceToString().lines().drop(1).joinToString("\n"))
        }
    }

    /** Crash with reason and call stack, e.g. from `ApplicationExitInfo` or MetricKit ([at] = point in time, if known). */
    fun record(kind: CrashReport.Kind, error: String, trace: String, at: Long? = null) {
        runCatching {
            val time = at ?: now()
            val report = CrashReport(
                kind = kind,
                atMillis = time,
                build = runCatching(build).getOrNull(),
                platform = platform,
                os = os,
                model = model,
                error = CrashReports.scrub(error, dataDirs),
                trace = CrashReports.scrub(trace, dataDirs),
                log = runCatching(logLines).getOrDefault(emptyList()).takeLast(CrashReports.LOG_LINES).map { CrashReports.scrub(it, dataDirs) },
            )
            save(CrashReports.add(_reports.value, report, now()))
        }
    }

    /** Was there already a Kotlin crash of this build between [from] and [to]? Against duplicates from MetricKit. */
    fun hasCrashBetween(from: Long, to: Long): Boolean {
        val b = runCatching(build).getOrNull()
        return _reports.value.any { it.kind == CrashReport.Kind.Crash && it.build == b && it.lastMillis in from..to }
    }

    /** Delete all reports: after “Senden” or “Nicht senden”. */
    fun clear() = save(emptyList())

    private fun save(reports: List<CrashReport>) {
        _reports.value = reports
        runCatching { file.write(json.encodeToString(serializer, reports)) }
    }

    private fun load(): List<CrashReport> {
        val raw = runCatching { file.read() }.getOrNull() ?: return emptyList()
        val list = runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
        val cutoff = now() - CrashReports.MAX_AGE_MILLIS
        return list.filter { it.lastMillis >= cutoff }
    }

    companion object {
        const val FILE_NAME = "crashes.json"
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(CrashReport.serializer())
    }
}
