package app.anynear.i18n

/** Search the phone for important documents (ADR 0071). */
interface FindTexts {
    val cardTitle: String
    val cardText: String
    val search: String
    val notNow: String
    val moreAbout: String
    val moreText: String
    val menuEntry: String
    fun menuRunning(checked: Int, total: Int): String
    val keepsRunning: String
    val show: String
    val title: String
    val inPhotos: String
    val alsoFolder: String
    val pickFiles: String
    val searchDevice: String
    val deviceTitle: String
    val deviceText: String
    val deviceTapAllow: String
    val pickFilesInstead: String
    /** Android 11 and up (ADR 0071, change 08.11.2026): first the access, then whole phone or one folder. */
    val allowAccess: String
    val chooseFolder: String
    val chooseFolderTitle: String
    val searchThisFolder: String
    val noSubfolders: String
    fun alreadyChecked(n: Int): String
    val checkAgain: String
    fun skipped(n: Int): String
    val pickPhotos: String
    val denied: String
    fun progress(checked: Int, total: Int): String
    val preparing: String
    val pausedBackground: String
    val pausedBattery: String
    val noWifiQuestion: String
    val searchAnyway: String
    val cancel: String
    val done: String
    /** Run ended by restart or update, access is missing (ADR 0071 item 10). */
    val interrupted: String
    val nothingFound: String
    fun found(n: Int): String
    val moreWithText: String
    fun takeOver(n: Int): String
    val close: String
    fun added(n: Int): String
    val someFailed: String
    // In the background (ADR 0071 item 9)
    val backgroundChannel: String
    val backgroundTitle: String
    val stop: String
    fun foundTitle(n: Int): String
    // Find new documents automatically (ADR 0081)
    fun newFoundTitle(n: Int): String
    val watchQuestion: String
    val watchText: String
    val watchYes: String
    val watchNo: String
    val watchSwitch: String
    val watchSwitchOn: String
    val watchSwitchOff: String
    val foundText: String
}

internal object FindTextsDe : FindTexts {
    override val cardTitle = "Wichtige Dokumente auf diesem Handy finden?"
    override val cardText = "anynear sucht in deinen Fotos nach Ausweisen, Urkunden, Impfpässen und Arztbriefen. Es bleibt alles auf dem Handy, bis du etwas übernimmst."
    override val search = "Suchen"
    override val notNow = "Nicht jetzt"
    override val moreAbout = "Mehr dazu"
    override val moreText = "Die Suche läuft nur auf diesem Handy. Sie macht weiter, wenn du anynear verlässt, und hält bei wenig Akku an. " +
        "Gefundene Dokumente kommen erst in deine Ablage, wenn du sie übernimmst; die Dateien bleiben, wo sie sind. " +
        "Den Zugriff kannst du in den Einstellungen des Handys jederzeit wieder entziehen. " +
        "Auch Ordner aus Dropbox, Synology Drive oder ähnlichen Apps lassen sich durchsuchen."
    override val menuEntry = "Handy nach Dokumenten durchsuchen"
    override fun menuRunning(checked: Int, total: Int) = "Suche läuft: $checked von $total"
    override val show = "Ansehen"
    override val keepsRunning = "Du kannst anynear weiter benutzen. Die Suche läuft weiter, auch wenn du zurückgehst."
    override val title = "Dokumente finden"
    override val inPhotos = "In meinen Fotos suchen"
    override val alsoFolder = "Auch einen Ordner durchsuchen"
    override val pickFiles = "Dateien auswählen, z. B. aus Download"
    override val searchDevice = "Ganzes Handy durchsuchen"
    override val deviceTitle = "Zugriff auf deine Dateien"
    override val deviceText = "Um das ganze Handy zu durchsuchen, braucht anynear Zugriff auf deine Dateien. Es bleibt alles auf dem Handy, und du kannst den Zugriff jederzeit wieder entziehen."
    override val deviceTapAllow = "Schalte gleich den Zugriff ein und komm dann zurück."
    override val pickFilesInstead = "Lieber Dateien selbst auswählen"
    override val allowAccess = "Zugriff erlauben"
    override val chooseFolder = "Einen Ordner durchsuchen"
    override val chooseFolderTitle = "Welcher Ordner?"
    override val searchThisFolder = "Diesen Ordner durchsuchen"
    override val noSubfolders = "Keine weiteren Ordner darin."
    override fun alreadyChecked(n: Int) = if (n == 1) "1 Datei hat dieses Handy schon geprüft. Sie wird übersprungen." else "$n Dateien hat dieses Handy schon geprüft. Sie werden übersprungen."
    override val checkAgain = "Alles noch einmal prüfen"
    override fun skipped(n: Int) = if (n == 1) "1 Datei schon früher geprüft, übersprungen" else "$n Dateien schon früher geprüft, übersprungen"
    override val pickPhotos = "Fotos selbst auswählen"
    override val denied = "Ohne Zugriff auf deine Fotos kannst du sie selbst auswählen. anynear sucht dann darin."
    override fun progress(checked: Int, total: Int) = "$checked von $total angesehen"
    override val preparing = "Wird vorbereitet …"
    override val pausedBackground = "Pausiert, solange anynear nicht offen ist."
    override val pausedBattery = "Pausiert: wenig Akku. Mit Ladekabel geht es weiter."
    override val noWifiQuestion = "Ohne WLAN kann das viele Daten kosten. Trotzdem suchen?"
    override val searchAnyway = "Trotzdem suchen"
    override val cancel = "Abbrechen"
    override val done = "Fertig angesehen."
    override val interrupted = "Die Suche wurde unterbrochen. Wähle unten, wo sie weitersuchen soll. Schon angesehene Fotos überspringt sie."
    override val nothingFound = "Keine Dokumente gefunden."
    override fun found(n: Int) = if (n == 1) "1 Dokument gefunden" else "$n Dokumente gefunden"
    override val moreWithText = "Weitere Fotos mit Text"
    override fun takeOver(n: Int) = if (n == 1) "1 Dokument übernehmen" else "$n Dokumente übernehmen"
    override val close = "Schließen"
    override fun added(n: Int) = if (n == 1) "1 Dokument in deine Ablage gelegt." else "$n Dokumente in deine Ablage gelegt."
    override val someFailed = "Einige ließen sich nicht übernehmen."
    override val backgroundChannel = "Dokumente finden"
    override val backgroundTitle = "anynear sucht nach Dokumenten"
    override val stop = "Anhalten"
    override fun foundTitle(n: Int) = if (n == 1) "1 wichtiges Dokument gefunden" else "$n wichtige Dokumente gefunden"
    override fun newFoundTitle(n: Int) = if (n == 1) "1 neues Dokument gefunden" else "$n neue Dokumente gefunden"
    override val watchQuestion = "Neue Dokumente von selbst finden?"
    override val watchText = "anynear schaut beim Laden nach neuen Fotos und Dateien, nur auf diesem Handy. Abgelegt wird erst, wenn du es bestätigst."
    override val watchYes = "Ja, regelmäßig schauen"
    override val watchNo = "Nein, danke"
    override val watchSwitch = "Neue Dokumente von selbst finden"
    override val watchSwitchOn = "anynear schaut beim Laden nach neuen Fotos und Dateien und meldet sich, wenn es etwas Wichtiges findet."
    override val watchSwitchOff = "Aus. anynear sucht nur, wenn du es selbst startest."
    override val foundText = "Tippe, um auszuwählen, was in deine Ablage soll."
}

internal object FindTextsEn : FindTexts {
    override val cardTitle = "Find important documents on this phone?"
    override val cardText = "anynear looks through your photos for IDs, certificates, vaccination records and medical letters. Everything stays on your phone until you add something."
    override val search = "Search"
    override val notNow = "Not now"
    override val moreAbout = "More about this"
    override val moreText = "The search runs only on this phone. It keeps going when you leave anynear, and pauses when the battery is low. " +
        "Found documents only go into your files when you add them; the originals stay where they are. " +
        "You can take back access at any time in your phone's settings. " +
        "Folders from Dropbox, Synology Drive or similar apps can be searched too."
    override val menuEntry = "Search phone for documents"
    override fun menuRunning(checked: Int, total: Int) = "Search running: $checked of $total"
    override val show = "Show"
    override val keepsRunning = "You can keep using anynear. The search keeps going when you go back."
    override val title = "Find documents"
    override val inPhotos = "Search my photos"
    override val alsoFolder = "Also search a folder"
    override val pickFiles = "Choose files, e.g. from Downloads"
    override val searchDevice = "Search whole phone"
    override val deviceTitle = "Access to your files"
    override val deviceText = "To search your whole phone, anynear needs access to your files. Everything stays on your phone, and you can take back access at any time."
    override val deviceTapAllow = "Turn on access next, then come back."
    override val pickFilesInstead = "Choose files myself instead"
    override val allowAccess = "Allow access"
    override val chooseFolder = "Search one folder"
    override val chooseFolderTitle = "Which folder?"
    override val searchThisFolder = "Search this folder"
    override val noSubfolders = "No more folders in here."
    override fun alreadyChecked(n: Int) = if (n == 1) "This phone has already checked 1 file. It will be skipped." else "This phone has already checked $n files. They will be skipped."
    override val checkAgain = "Check everything again"
    override fun skipped(n: Int) = if (n == 1) "1 file checked before, skipped" else "$n files checked before, skipped"
    override val pickPhotos = "Choose photos myself"
    override val denied = "Without access to your photos, you can choose them yourself. anynear then searches those."
    override fun progress(checked: Int, total: Int) = "$checked of $total checked"
    override val preparing = "Getting ready …"
    override val pausedBackground = "Paused while anynear isn't open."
    override val pausedBattery = "Paused: battery low. It continues when charging."
    override val noWifiQuestion = "Without Wi-Fi this can use a lot of data. Search anyway?"
    override val searchAnyway = "Search anyway"
    override val cancel = "Cancel"
    override val done = "All checked."
    override val interrupted = "The search was interrupted. Choose below where to keep searching. It skips photos it has already looked at."
    override val nothingFound = "No documents found."
    override fun found(n: Int) = if (n == 1) "1 document found" else "$n documents found"
    override val moreWithText = "More photos with text"
    override fun takeOver(n: Int) = if (n == 1) "Add 1 document" else "Add $n documents"
    override val close = "Close"
    override fun added(n: Int) = if (n == 1) "Added 1 document to your files." else "Added $n documents to your files."
    override val someFailed = "Some couldn't be added."
    override val backgroundChannel = "Finding documents"
    override val backgroundTitle = "anynear is looking for documents"
    override val stop = "Stop"
    override fun foundTitle(n: Int) = if (n == 1) "1 important document found" else "$n important documents found"
    override fun newFoundTitle(n: Int) = if (n == 1) "1 new document found" else "$n new documents found"
    override val watchQuestion = "Find new documents on their own?"
    override val watchText = "anynear looks for new photos and files while charging, only on this phone. Nothing is added until you confirm."
    override val watchYes = "Yes, check regularly"
    override val watchNo = "No, thanks"
    override val watchSwitch = "Find new documents on their own"
    override val watchSwitchOn = "anynear looks for new photos and files while charging and lets you know when it finds something important."
    override val watchSwitchOff = "Off. anynear only searches when you start it."
    override val foundText = "Tap to choose what goes into your files."
}
