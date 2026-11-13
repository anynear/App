package app.anynear.i18n

/** Troubleshooting, log and support as a chat (ADR 0065). */
interface SupportTexts {
    val troubleshooting: String
    val reportSection: String
    val askSection: String
    val reportFooter: String
    val askFooter: String
    val subject: String
    val description: String
    val attachLog: String
    val attachLogHint: String
    val send: String
    val sending: String
    val notAvailable: String
    val sendFailed: String
    val sent: String
    val kindProblem: String
    val kindQuestion: String
    val circlePrefix: String
    val logFileName: String
    val logSection: String
    fun newRequest(subject: String, invite: String): String
    /** Start of [newRequest], for recognition by support. */
    val newRequestPrefix: String
    val requestAlertTitle: String
    fun requestAlertText(subject: String): String
    val directBlocked: String

    /** Below the first own message in the circle of a request. */
    val replySoon: String
    val openForm: String

    // Crash report (ADR 0082)
    val crashCardTitle: String
    fun crashCardText(count: Int): String
    val crashReview: String
    val crashDontSend: String
    val crashDialogTitle: String
    val crashDialogInfo: String
    val crashNote: String
    val crashWhatIsSent: String
    val crashRow: String
    fun crashRowCount(count: Int): String
    fun crashSubject(version: String?): String
    fun crashDescription(count: Int): String
    val crashFileName: String
    val crashDelete: String
}

internal object SupportTextsDe : SupportTexts {
    override val troubleshooting = "Fehlerbehebung"
    override val reportSection = "Fehler melden"
    override val askSection = "Frage an uns"
    override val reportFooter = "Deine Meldung wird ein eigener Chat mit uns. Dort kommt auch die Antwort."
    override val askFooter = "Deine Frage wird ein eigener Chat mit uns. Dort kommt auch die Antwort."
    override val subject = "Worum geht es?"
    override val description = "Beschreibung"
    override val attachLog = "Protokoll mitschicken"
    override val attachLogHint = "Das Protokoll zeigt, was $APP zuletzt gemacht hat. Es hilft uns, den Fehler zu finden."
    override val send = "Senden"
    override val sending = "Wird gesendet …"
    override val notAvailable = "Der Support ist bald erreichbar."
    override val sendFailed = "Senden hat nicht geklappt. Versuch es gleich noch einmal."
    override val sent = "Gesendet. Die Antwort kommt hier in diesem Chat."
    override val kindProblem = "Fehler"
    override val kindQuestion = "Frage"
    override val circlePrefix = "Support:"
    override val logFileName = "protokoll.txt"
    override val logSection = "Protokoll"
    override val newRequestPrefix = "Neue Anfrage:"
    override fun newRequest(subject: String, invite: String) = "$newRequestPrefix $subject\n$invite"
    override val requestAlertTitle = "Neue Support-Anfrage"
    override fun requestAlertText(subject: String) = "„$subject“. Der Kreis dazu steht schon in deinen Chats."
    override val replySoon = "Danke für deine Nachricht. Wir melden uns zeitnah hier im Chat bei dir."
    override val directBlocked = "Schreib uns bitte über das Formular. So bekommt jede Frage ihren eigenen Chat."
    override val openForm = "Zum Formular"
    override val crashCardTitle = "$APP wurde unerwartet beendet"
    override fun crashCardText(count: Int) =
        (if (count > 1) "Das ist in den letzten Tagen $count-mal passiert. " else "") +
            "Schickst du uns einen Bericht? Er hilft uns, den Fehler zu finden."
    override val crashReview = "Ansehen und senden"
    override val crashDontSend = "Nicht senden"
    override val crashDialogTitle = "Absturzbericht"
    override val crashDialogInfo = "Ohne Nachrichten, Namen und Dateien. Der Bericht wird ein eigener Chat mit uns, das Protokoll kommt mit."
    override val crashNote = "Was hast du gerade gemacht? (freiwillig)"
    override val crashWhatIsSent = "Was geschickt wird"
    override val crashRow = "Absturzberichte"
    override fun crashRowCount(count: Int) = if (count == 1) "1 Bericht" else "$count Berichte"
    override fun crashSubject(version: String?) = "Absturz ${version.orEmpty()}".trim()
    override fun crashDescription(count: Int) = if (count > 1) "$count Abstürze, Bericht im Anhang." else "Absturz, Bericht im Anhang."
    override val crashFileName = "absturz.txt"
    override val crashDelete = "Löschen"
}

internal object SupportTextsEn : SupportTexts {
    override val troubleshooting = "Troubleshooting"
    override val reportSection = "Report a problem"
    override val askSection = "Ask us"
    override val reportFooter = "Your report becomes a chat with us. The answer arrives there too."
    override val askFooter = "Your question becomes a chat with us. The answer arrives there too."
    override val subject = "What is it about?"
    override val description = "Description"
    override val attachLog = "Send the log"
    override val attachLogHint = "The log shows what $APP did recently. It helps us find the problem."
    override val send = "Send"
    override val sending = "Sending …"
    override val notAvailable = "Support will be available soon."
    override val sendFailed = "Sending did not work. Please try again in a moment."
    override val sent = "Sent. The answer will arrive here in this chat."
    override val kindProblem = "Problem"
    override val kindQuestion = "Question"
    override val circlePrefix = "Support:"
    override val logFileName = "log.txt"
    override val logSection = "Log"
    override val newRequestPrefix = "New request:"
    override fun newRequest(subject: String, invite: String) = "$newRequestPrefix $subject\n$invite"
    override val requestAlertTitle = "New support request"
    override fun requestAlertText(subject: String) = "“$subject”. Its circle is already in your chats."
    override val replySoon = "Thanks for your message. We'll get back to you soon, right here in this chat."
    override val directBlocked = "Please write to us using the form. That way every question gets its own chat."
    override val openForm = "Open the form"
    override val crashCardTitle = "$APP closed unexpectedly"
    override fun crashCardText(count: Int) =
        (if (count > 1) "This happened $count times in the last few days. " else "") +
            "Would you send us a report? It helps us find the problem."
    override val crashReview = "Review and send"
    override val crashDontSend = "Don't send"
    override val crashDialogTitle = "Crash report"
    override val crashDialogInfo = "No messages, names or files. The report becomes a chat with us, the log comes along."
    override val crashNote = "What were you doing? (optional)"
    override val crashWhatIsSent = "What is sent"
    override val crashRow = "Crash reports"
    override fun crashRowCount(count: Int) = if (count == 1) "1 report" else "$count reports"
    override fun crashSubject(version: String?) = "Crash ${version.orEmpty()}".trim()
    override fun crashDescription(count: Int) = if (count > 1) "$count crashes, report attached." else "Crash, report attached."
    override val crashFileName = "crash.txt"
    override val crashDelete = "Delete"
}
