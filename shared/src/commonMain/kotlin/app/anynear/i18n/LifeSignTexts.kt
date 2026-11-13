package app.anynear.i18n

/** Check-ins (ADR 0083 §5): templates, question „Alles gut?“, the helpers' view. */
interface LifeSignTexts {
    val section: String
    val intro: String
    val start: String
    val startTitle: String
    val startText: String
    val goesTo: String
    fun circleWithCount(name: String, count: Int): String
    val noCircle: String
    val createCircle: String
    fun circleNobodyYet(name: String): String
    val startNow: String
    val notEmergency: String

    // Templates
    val templateWork: String
    val templateWorkText: String
    val templateOutside: String
    val templateOutsideText: String
    val templateHome: String
    val templateHomeText: String
    val templateDaily: String
    val templateDailyText: String
    val templateCustom: String
    fun customText(hours: Int, minutes: Int): String
    val customHours: String
    val customMinutes: String

    // Own check-in
    fun runningSince(time: String): String
    fun nextQuestion(time: String): String
    fun heardBy(count: Int, total: Int): String
    val heardByNobody: String
    val notArriving: String
    val askNow: String
    val allGood: String
    val done: String
    val endConfirm: String
    val bigButton: String
    val close: String

    // Question on the own phone
    val askTitle: String
    fun askText(label: String): String

    // View and notifications of the helpers
    fun watchTitle(name: String, label: String): String
    fun lastSign(time: String): String
    fun started(name: String): String
    fun startedText(label: String, until: String): String
    fun hintTitle(name: String): String
    val hintText: String
    fun missedTitle(name: String): String
    val missedText: String
    fun unreachableTitle(name: String): String
    fun unreachableText(time: String): String
    fun ended(name: String): String
    val endedText: String
    fun timedOut(name: String): String
    fun confirmedAgain(name: String): String
    fun battery(name: String, percent: Int): String
    val looking: String
    fun lookingBy(names: String): String
    val away: String
    val back: String
    val awayActive: String
    fun awayBy(names: String): String
    val loudness: String
    val loud: String
    val normal: String
    val off: String
    val exactAlarmHint: String
    val exactAlarmAllow: String
}

internal object LifeSignTextsDe : LifeSignTexts {
    override val section = "Lebenszeichen"
    override val intro = "Bist du eine Zeit lang allein? Dein Handy fragt ab und zu „Alles gut?“. Antwortest du nicht, " +
        "werden die Menschen in deinem Kreis gewarnt."
    override val start = "Lebenszeichen starten"
    override val startTitle = "Lebenszeichen starten"
    override val startText = "Wofür brauchst du es?"
    override val goesTo = "Geht an"
    override fun circleWithCount(name: String, count: Int) = if (count == 1) "$name (1 Person)" else "$name ($count Personen)"
    override val noCircle = "Für ein Lebenszeichen brauchst du einen Kreis mit Menschen, die auf dich achten. Leg einen an und lade sie ein."
    override val createCircle = "Kreis anlegen"
    override fun circleNobodyYet(name: String) = "$name (noch niemand dabei)"
    override val startNow = "Starten"
    override val notEmergency = "anynear warnt deine Helfer. Es ersetzt nicht den Notruf 112."

    override val templateWork = "Allein arbeiten"
    override val templateWorkText = "Garten, Werkstatt, Dachboden · bis du fertig bist · Frage alle 20 Minuten"
    override val templateOutside = "Unterwegs"
    override val templateOutsideText = "Spaziergang, Wandern, Joggen · 2 Stunden · Frage alle 30 Minuten"
    override val templateHome = "Allein zu Hause"
    override val templateHomeText = "Heute Nacht · Frage einmal am Morgen um 7 Uhr"
    override val templateDaily = "Täglich"
    override val templateDailyText = "Jeden Tag um 10 Uhr eine Frage · 30 Tage"
    override val templateCustom = "Eigenes"
    override fun customText(hours: Int, minutes: Int) = "$hours Stunden · Frage alle $minutes Minuten"
    override val customHours = "Stunden"
    override val customMinutes = "Frage alle … Minuten"

    override fun runningSince(time: String) = "Läuft seit $time"
    override fun nextQuestion(time: String) = "Nächste Frage: $time"
    override fun heardBy(count: Int, total: Int) = if (count >= total) "Alle wissen Bescheid" else "$count von $total wissen Bescheid"
    override val heardByNobody = "Noch hat es niemand bekommen"
    override val notArriving = "Deine Helfer bekommen deine Lebenszeichen gerade nicht."
    override val askNow = "Alles gut?"
    override val allGood = "Alles gut"
    override val done = "Bin fertig"
    override val endConfirm = "Lebenszeichen beenden? Deine Helfer erfahren, dass alles gut ist."
    override val bigButton = "Großer Knopf"
    override val close = "Schließen"

    override val askTitle = "Alles gut?"
    override fun askText(label: String) = "Lebenszeichen „$label“: Tippe auf „Alles gut“."

    override fun watchTitle(name: String, label: String) = "$name · $label"
    override fun lastSign(time: String) = "Zuletzt bestätigt: $time"
    override fun started(name: String) = "$name hat ein Lebenszeichen gestartet"
    override fun startedText(label: String, until: String) = "„$label“, längstens bis $until. Du wirst gewarnt, wenn es ausbleibt."
    override fun hintTitle(name: String) = "$name hat noch nicht bestätigt"
    override val hintText = "Vielleicht nur verpasst. Ruf kurz an oder schau nach."
    override fun missedTitle(name: String) = "$name hat nicht bestätigt"
    override val missedText = "Das Handy hat gefragt, aber niemand hat geantwortet."
    override fun unreachableTitle(name: String) = "$name ist nicht erreichbar"
    override fun unreachableText(time: String) = "Seit $time kommt nichts mehr: Funkloch, Akku leer oder etwas Ernstes."
    override fun ended(name: String) = "$name ist fertig"
    override val endedText = "Alles gut, das Lebenszeichen ist beendet."
    override fun timedOut(name: String) = "Lebenszeichen von $name ohne Abschluss beendet"
    override fun confirmedAgain(name: String) = "$name hat bestätigt: alles gut"
    override fun battery(name: String, percent: Int) = "Der Akku von $name ist fast leer ($percent %)"
    override val looking = "Ich schaue nach"
    override fun lookingBy(names: String) = "$names schaut nach"
    override val away = "Ich kann gerade nicht"
    override val back = "Ich bin wieder da"
    override val awayActive = "Du hast dich abgemeldet und wirst nicht gewarnt."
    override fun awayBy(names: String) = "$names kann gerade nicht"
    override val loudness = "Warnung aus diesem Kreis"
    override val loud = "Laut"
    override val normal = "Normal"
    override val off = "Aus"
    override val exactAlarmHint = "Damit Fragen und Warnungen pünktlich kommen, braucht anynear die Erlaubnis für Wecker."
    override val exactAlarmAllow = "Erlauben"
}

internal object LifeSignTextsEn : LifeSignTexts {
    override val section = "Check-ins"
    override val intro = "Alone for a while? Your phone asks “All good?” now and then. If you don't answer, the people in " +
        "your circle are warned."
    override val start = "Start check-in"
    override val startTitle = "Start check-in"
    override val startText = "What do you need it for?"
    override val goesTo = "Goes to"
    override fun circleWithCount(name: String, count: Int) = if (count == 1) "$name (1 person)" else "$name ($count people)"
    override val noCircle = "For a check-in you need a circle with people who look out for you. Create one and invite them."
    override val createCircle = "Create circle"
    override fun circleNobodyYet(name: String) = "$name (nobody there yet)"
    override val startNow = "Start"
    override val notEmergency = "anynear warns your helpers. It does not replace emergency number 112."

    override val templateWork = "Working alone"
    override val templateWorkText = "Garden, workshop, attic · until you're done · asks every 20 minutes"
    override val templateOutside = "Out and about"
    override val templateOutsideText = "Walk, hike, run · 2 hours · asks every 30 minutes"
    override val templateHome = "Alone at home"
    override val templateHomeText = "Tonight · asks once in the morning at 7"
    override val templateDaily = "Daily"
    override val templateDailyText = "Asks every day at 10 · 30 days"
    override val templateCustom = "Custom"
    override fun customText(hours: Int, minutes: Int) = "$hours hours · asks every $minutes minutes"
    override val customHours = "Hours"
    override val customMinutes = "Ask every … minutes"

    override fun runningSince(time: String) = "Running since $time"
    override fun nextQuestion(time: String) = "Next question: $time"
    override fun heardBy(count: Int, total: Int) = if (count >= total) "Everyone knows" else "$count of $total know"
    override val heardByNobody = "Nobody has received it yet"
    override val notArriving = "Your helpers are not receiving your check-ins right now."
    override val askNow = "All good?"
    override val allGood = "All good"
    override val done = "I'm done"
    override val endConfirm = "End check-in? Your helpers will learn that all is well."
    override val bigButton = "Big button"
    override val close = "Close"

    override val askTitle = "All good?"
    override fun askText(label: String) = "Check-in “$label”: tap “All good”."

    override fun watchTitle(name: String, label: String) = "$name · $label"
    override fun lastSign(time: String) = "Last confirmed: $time"
    override fun started(name: String) = "$name started a check-in"
    override fun startedText(label: String, until: String) = "“$label”, at the latest until $until. You'll be warned if it stops."
    override fun hintTitle(name: String) = "$name hasn't confirmed yet"
    override val hintText = "Maybe just missed. Give a quick call or check."
    override fun missedTitle(name: String) = "$name did not confirm"
    override val missedText = "The phone asked, but nobody answered."
    override fun unreachableTitle(name: String) = "$name can't be reached"
    override fun unreachableText(time: String) = "Nothing since $time: no signal, empty battery or something serious."
    override fun ended(name: String) = "$name is done"
    override val endedText = "All good, the check-in has ended."
    override fun timedOut(name: String) = "Check-in of $name ended without being closed"
    override fun confirmedAgain(name: String) = "$name confirmed: all good"
    override fun battery(name: String, percent: Int) = "$name's battery is nearly empty ($percent %)"
    override val looking = "I'll check"
    override fun lookingBy(names: String) = "$names is checking"
    override val away = "I can't right now"
    override val back = "I'm back"
    override val awayActive = "You signed off and won't be warned."
    override fun awayBy(names: String) = "$names can't right now"
    override val loudness = "Warnings from this circle"
    override val loud = "Loud"
    override val normal = "Normal"
    override val off = "Off"
    override val exactAlarmHint = "So questions and warnings come on time, anynear needs permission for alarms."
    override val exactAlarmAllow = "Allow"
}
