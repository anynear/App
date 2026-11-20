package app.anynear.i18n

/** Login with anynear at a service (ADR 0122). */
interface LoginTexts {
    fun title(service: String): String
    val loading: String
    val nameLabel: String
    val nameHint: String
    val pickNumber: String
    val footnote: String
    fun reason(service: String): String
    val confirming: String
    fun done(service: String): String
    val needsLock: String
    val wrongNumber: String
    val expired: String
    val rejected: String
    val tooMany: String
    val offline: String
    val noChain: String
    val badName: String
    val invalid: String
    val notSetUp: String
}

internal object LoginTextsDe : LoginTexts {
    override fun title(service: String) = "Bei $service anmelden?"
    override val loading = "Anmeldung wird geladen …"
    override val nameLabel = "Dein Name dort"
    override val nameHint = "So sehen dich andere dort. 1 bis 40 Zeichen."
    override val pickNumber = "Tippe auf die Zahl, die auf der Seite steht."
    override val footnote = "Ohne Passwort und ohne E-Mail-Adresse. Der Dienst erfährt nur diesen Namen."
    override fun reason(service: String) = "Bei $service anmelden"
    override val confirming = "Wird bestätigt …"
    override fun done(service: String) = "Du bist bei $service angemeldet. Die Seite geht von selbst weiter."
    override val needsLock = "Richte zuerst eine Gerätesperre ein (PIN, Muster oder Fingerabdruck)."
    override val wrongNumber = "Das war nicht die Zahl von der Seite. Starte die Anmeldung dort neu."
    override val expired = "Die Anmeldung ist abgelaufen. Starte sie auf der Seite neu."
    override val rejected = "Die Anmeldung hat nicht geklappt. Prüfe, ob Datum und Uhrzeit auf deinem Handy stimmen."
    override val tooMany = "Zu viele Versuche. Warte eine Minute und versuch es dann noch einmal."
    override val offline = "Gerade keine Verbindung. Versuch es gleich noch einmal."
    override val noChain = "Mit diesem Gerät geht die Anmeldung gerade nicht. Starte anynear neu und versuch es noch einmal."
    override val badName = "Dein Name dort braucht 1 bis 40 Zeichen."
    override val invalid = "Dieser Anmelde-Link funktioniert nicht."
    override val notSetUp = "Richte anynear zuerst ein. Danach kannst du dich anmelden."
}

internal object LoginTextsEn : LoginTexts {
    override fun title(service: String) = "Sign in to $service?"
    override val loading = "Loading sign-in …"
    override val nameLabel = "Your name there"
    override val nameHint = "This is how others see you there. 1 to 40 characters."
    override val pickNumber = "Tap the number shown on the page."
    override val footnote = "No password and no email address. The service only learns this name."
    override fun reason(service: String) = "Sign in to $service"
    override val confirming = "Confirming …"
    override fun done(service: String) = "You are signed in to $service. The page continues on its own."
    override val needsLock = "Set up a screen lock first (PIN, pattern or fingerprint)."
    override val wrongNumber = "That was not the number on the page. Start signing in there again."
    override val expired = "The sign-in has expired. Start it again on the page."
    override val rejected = "Signing in did not work. Check that the date and time on your phone are right."
    override val tooMany = "Too many tries. Wait a minute and try again."
    override val offline = "No connection right now. Try again in a moment."
    override val noChain = "Signing in does not work on this device right now. Restart anynear and try again."
    override val badName = "Your name there needs 1 to 40 characters."
    override val invalid = "This sign-in link does not work."
    override val notSetUp = "Set up anynear first. Then you can sign in."
}
