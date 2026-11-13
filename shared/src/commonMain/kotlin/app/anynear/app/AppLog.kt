package app.anynear.app

/** Level of a log line. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** Log of the app, independent of the radio SDK (ADR 0057). */
fun interface AppLog {
    fun log(level: LogLevel, tag: String, message: String, cause: Throwable?)

    companion object {
        val Silent = AppLog { _, _, _, _ -> }
    }
}
