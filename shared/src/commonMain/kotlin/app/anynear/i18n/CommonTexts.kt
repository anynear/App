package app.anynear.i18n

import app.anynear.AppConfig

/** Words that occur in many areas. Area-specific texts belong in the catalogue of the area. */
interface CommonTexts {
    val ok: String
    val cancel: String
    val close: String
    val notNow: String
    val later: String
    val done: String
    val back: String
    val next: String
    val retry: String
    val remove: String
    val add: String
    val share: String
    val copy: String
    val leave: String
    val advanced: String
    val moreInfo: String
    val yes: String

    /** „du“ or „you“, e.g. in „Anna und du“. */
    val you: String

    /** Name of the own person in lists, e.g. „Ich“. */
    val me: String

    /** Joins the last two items of a list: „Anna, Ben und du“. */
    val and: String

    /** The paths of a message. */
    val internet: String
    val radio: String

    /** Times in overviews. */
    val yesterday: String

    /** „heute, 14:05“ */
    fun todayAt(time: String): String

    /** „gestern, 14:05“ */
    fun yesterdayAt(time: String): String

    /** Time tomorrow, e.g. „morgen, 14:05“ („gültig bis“). */
    fun tomorrowAt(time: String): String

    /** „Montag, 14:05“ */
    fun weekdayAt(weekday: String, time: String): String

    /** List with „und“ before the last item: „Anna, Ben und du“. */
    fun list(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items.single()
        else -> items.dropLast(1).joinToString(", ") + " $and " + items.last()
    }
}

/** Name of the app in texts (ADR 0025, 0061), always lower case: „anynear“. */
internal const val APP: String = AppConfig.APP_NAME

internal object CommonTextsDe : CommonTexts {
    override val ok = "OK"
    override val cancel = "Abbrechen"
    override val close = "Schließen"
    override val notNow = "Nicht jetzt"
    override val later = "Später"
    override val done = "Fertig"
    override val back = "Zurück"
    override val next = "Weiter"
    override val retry = "Noch einmal"
    override val remove = "Entfernen"
    override val add = "Hinzufügen"
    override val share = "Teilen"
    override val copy = "Kopieren"
    override val leave = "Verlassen"
    override val advanced = "Erweitert"
    override val moreInfo = "Mehr dazu"
    override val yes = "ja"
    override val you = "du"
    override val me = "Ich"
    override val and = "und"
    override val internet = "Internet"
    override val radio = "Funk"
    override val yesterday = "Gestern"
    override fun todayAt(time: String) = "heute, $time"
    override fun yesterdayAt(time: String) = "gestern, $time"
    override fun tomorrowAt(time: String) = "morgen, $time"
    override fun weekdayAt(weekday: String, time: String) = "$weekday, $time"
}

internal object CommonTextsEn : CommonTexts {
    override val ok = "OK"
    override val cancel = "Cancel"
    override val close = "Close"
    override val notNow = "Not now"
    override val later = "Later"
    override val done = "Done"
    override val back = "Back"
    override val next = "Continue"
    override val retry = "Try again"
    override val remove = "Remove"
    override val add = "Add"
    override val share = "Share"
    override val copy = "Copy"
    override val leave = "Leave"
    override val advanced = "Advanced"
    override val moreInfo = "Learn more"
    override val yes = "yes"
    override val you = "you"
    override val me = "Me"
    override val and = "and"
    override val internet = "Internet"
    override val radio = "radio"
    override val yesterday = "Yesterday"
    override fun todayAt(time: String) = "today, $time"
    override fun yesterdayAt(time: String) = "yesterday, $time"
    override fun tomorrowAt(time: String) = "tomorrow, $time"
    override fun weekdayAt(weekday: String, time: String) = "$weekday, $time"
}
