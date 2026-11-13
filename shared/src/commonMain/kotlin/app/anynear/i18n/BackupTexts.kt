package app.anynear.i18n

/** Manual backup and restore, with password (ADR 0027). */
interface BackupTexts {
    val title: String
    val footer: String
    val create: String
    val createSubtitle: String
    val restore: String
    val restoreSubtitle: String
    val codeText: String
    val code: String
    val codeTypeText: String
    val codeWritten: String
    val codeShowAgain: String
    val codeMismatch: String
    val codeOrPassword: String
    val creating: String
    val createFailed: String
    val passwordsIncluded: String
    val passwordsReason: String
    val passwordsNeedLock: String
    val saveFailed: String
    fun savedIn(where: String): String
    val keepSafe: String
    val restoreText: String
    val chooseFile: String
    val chooseOtherFile: String
    val fileTooLarge: String
    val fileUnreadable: String
    val checking: String
    val wrongPassword: String
    val notABackup: String
    val tooNew: String

    /** Backup from the time before the fresh start (ADR 0034 §4). */
    val tooOld: String
    val confirmTitle: String
    fun confirmText(date: String): String
    val confirmOnlyHere: String
    val restoreAndRestart: String
    val restarting: String
    val restoreFromBackup: String
    fun fileName(date: String): String
    // Move to the new app with a different app ID (ADR 0025)
    val movedTitle: String
    val movedText: String
    val movedSteps: List<String>
    val loadNewApp: String
    val movedRow: String
    // After the move: delete the old app (ADR 0025)
    val oldAppTitle: String
    val oldAppText: String
    val oldAppHowTo: String
    val deleteOldApp: String
    val oldAppRow: String
    // New signing key: delete and reinstall (ADR 0037)
    val reinstallTitle: String
    val reinstallText: String
    val reinstallSteps: List<String>
    val loadNewVersion: String
    val deleteThisApp: String
    val reinstallRow: String
}

internal object BackupTextsDe : BackupTexts {
    override val title = "Sicherung"
    override val footer = "Die Sicherung ist eine Datei mit allem aus $APP: Kreise, Kontakte, Nachrichten und Bilder. Mit ihr holst du alles auf eine neue Installation oder ein neues Handy."
    override val create = "Sicherung erstellen"
    override val createSubtitle = "Als Datei, mit Code geschützt"
    override val restore = "Sicherung einspielen"
    override val restoreSubtitle = "Ersetzt alles auf diesem Handy"
    override val codeText = "Das ist der Code deiner Sicherung. Schreib ihn auf Papier ab. Ohne ihn lässt sich die Sicherung nicht öffnen, auch nicht von uns."
    override val code = "Code"
    override val codeTypeText = "Tipp den Code einmal von deinem Papier ab."
    override val codeWritten = "Aufgeschrieben"
    override val codeShowAgain = "Code noch mal zeigen"
    override val codeMismatch = "Der Code stimmt nicht. Prüf, was du aufgeschrieben hast."
    override val codeOrPassword = "Code (oder Passwort einer älteren Sicherung)"
    override val creating = "Sicherung wird erstellt …"
    override val createFailed = "Die Sicherung konnte nicht erstellt werden."
    override val passwordsIncluded = "Deine Passwörter kommen mit. Bestätige dafür gleich mit der Gerätesperre."
    override val passwordsReason = "Passwörter in die Sicherung"
    override val passwordsNeedLock = "Ohne Bestätigung mit der Gerätesperre entsteht keine Sicherung, weil deine Passwörter sonst fehlen würden."
    override val saveFailed = "Die Sicherung konnte nicht gespeichert werden."
    override fun savedIn(where: String) = "Gespeichert in „$where“."
    override val keepSafe = "Bewahre die Datei gut auf, zum Beispiel auf einem anderen Gerät. Wer sie hat und den Code kennt, kann alles lesen."
    override val restoreText = "Wähle die Sicherungsdatei und gib ihren Code ein."
    override val chooseFile = "Datei auswählen"
    override val chooseOtherFile = "Andere Datei auswählen"
    override val fileTooLarge = "Die Datei ist zu groß für eine Sicherung."
    override val fileUnreadable = "Die Datei konnte nicht gelesen werden."
    override val checking = "Wird geprüft …"
    override val wrongPassword = "Der Code passt nicht zu dieser Sicherung."
    override val notABackup = "Das ist keine Sicherung von $APP."
    override val tooNew = "Diese Sicherung ist von einer neueren Version. Aktualisiere zuerst die App."
    override val tooOld = "Diese Sicherung ist von vor dem Neuanfang und passt nicht mehr. Richte deinen Kreis bitte neu ein."
    override val confirmTitle = "Alles ersetzen?"
    override fun confirmText(date: String) = "Die Sicherung vom $date ersetzt alles, was jetzt auf diesem Handy in $APP ist. Danach startet die App neu."
    override val confirmOnlyHere = "Danach ist dieses Handy ein weiteres Gerät von dir. Gleich fragt die App, ob du dein altes Handy noch hast."
    override val restoreAndRestart = "Einspielen und neu starten"
    override val restarting = "Die App startet neu. Falls sie sich nur schließt, öffne sie wieder."
    override val restoreFromBackup = "Aus einer Sicherung wiederherstellen"
    override fun fileName(date: String) = "$APP-Sicherung-$date"
    override val movedTitle = "$APP gibt es jetzt als neue App"
    override val movedText = "Diese App bekommt keine Aktualisierungen mehr und funktioniert bald nicht mehr. So geht es weiter:"
    override val movedSteps = listOf(
        "Die neue App laden und installieren.",
        "In der neuen App neu beginnen: Kreise anlegen und Kontakte einladen.",
        "Danach diese App löschen.",
    )
    override val loadNewApp = "Neue App laden"
    override val movedRow = "Neue App verfügbar"
    override val oldAppTitle = "Die alte App ist noch da"
    override val oldAppText = "Die alte App PYRSA ist noch auf diesem Handy. Sie funktioniert nicht mehr. Lösch sie, damit du nicht die falsche öffnest."
    override val oldAppHowTo = "Tippe auf „Alte App löschen“, dann fragt Android nur nach der alten. Diese hier bleibt."
    override val deleteOldApp = "Alte App löschen"
    override val oldAppRow = "Alte App noch installiert"
    override val reinstallTitle = "$APP bitte neu installieren"
    override val reinstallText = "Die nächste Version lässt sich nicht über diese installieren. Nach dem Neuinstallieren fängt $APP leer an, du richtest deine Kreise neu ein."
    override val reinstallSteps = listOf(
        "„Neue Version laden“ tippen und die Datei laden.",
        "„$APP löschen“ tippen und bestätigen.",
        "Die geladene Datei öffnen und installieren.",
    )
    override val loadNewVersion = "Neue Version laden"
    override val deleteThisApp = "$APP löschen"
    override val reinstallRow = "Neu installieren nötig"
}

internal object BackupTextsEn : BackupTexts {
    override val title = "Backup"
    override val footer = "A backup is a file with everything in $APP: circles, contacts, messages and pictures. Use it to bring everything to a new installation or a new phone."
    override val create = "Create backup"
    override val createSubtitle = "As a file, protected by a code"
    override val restore = "Restore backup"
    override val restoreSubtitle = "Replaces everything on this phone"
    override val codeText = "This is the code of your backup. Write it down on paper. Without it the backup can't be opened, not even by us."
    override val code = "Code"
    override val codeTypeText = "Type the code once from your paper."
    override val codeWritten = "Written down"
    override val codeShowAgain = "Show the code again"
    override val codeMismatch = "The code doesn't match. Check what you wrote down."
    override val codeOrPassword = "Code (or password of an older backup)"
    override val creating = "Creating backup …"
    override val createFailed = "The backup couldn't be created."
    override val passwordsIncluded = "Your passwords are included. Confirm with your screen lock in a moment."
    override val passwordsReason = "Passwords into the backup"
    override val passwordsNeedLock = "Without confirming with your screen lock there is no backup, because your passwords would be missing."
    override val saveFailed = "The backup couldn't be saved."
    override fun savedIn(where: String) = "Saved in “$where”."
    override val keepSafe = "Keep the file somewhere safe, for example on another device. Anyone who has it and knows the code can read everything."
    override val restoreText = "Choose the backup file and enter its code."
    override val chooseFile = "Choose file"
    override val chooseOtherFile = "Choose another file"
    override val fileTooLarge = "This file is too large for a backup."
    override val fileUnreadable = "The file couldn't be read."
    override val checking = "Checking …"
    override val wrongPassword = "The code doesn't match this backup."
    override val notABackup = "This isn't a $APP backup."
    override val tooNew = "This backup is from a newer version. Please update the app first."
    override val tooOld = "This backup is from before the fresh start and no longer fits. Please set up your circle again."
    override val confirmTitle = "Replace everything?"
    override fun confirmText(date: String) = "The backup from $date replaces everything that's in $APP on this phone now. The app then restarts."
    override val confirmOnlyHere = "Afterwards this phone is another device of yours. The app will then ask whether you still have your old phone."
    override val restoreAndRestart = "Restore and restart"
    override val restarting = "The app is restarting. If it only closes, open it again."
    override val restoreFromBackup = "Restore from a backup"
    override fun fileName(date: String) = "$APP-backup-$date"
    override val movedTitle = "$APP is now a new app"
    override val movedText = "This app won't get updates anymore and will soon stop working. Here's what to do:"
    override val movedSteps = listOf(
        "Download and install the new app.",
        "Start fresh in the new app: create your circles and invite your contacts.",
        "Then delete this app.",
    )
    override val loadNewApp = "Download new app"
    override val movedRow = "New app available"
    override val oldAppTitle = "The old app is still here"
    override val oldAppText = "The old app PYRSA is still on this phone. It no longer works. Delete it so you don't open the wrong one."
    override val oldAppHowTo = "Tap “Delete old app” and Android only asks about the old one. This one stays."
    override val deleteOldApp = "Delete old app"
    override val oldAppRow = "Old app still installed"
    override val reinstallTitle = "Please reinstall $APP"
    override val reinstallText = "The next version can't be installed over this one. After reinstalling, $APP starts empty and you set up your circles again."
    override val reinstallSteps = listOf(
        "Tap “Download new version” and download the file.",
        "Tap “Delete $APP” and confirm.",
        "Open the downloaded file and install it.",
    )
    override val loadNewVersion = "Download new version"
    override val deleteThisApp = "Delete $APP"
    override val reinstallRow = "Reinstall needed"
}
