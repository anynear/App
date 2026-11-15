package app.anynear.i18n

/** Moving to a new app or a new installation: new app ID (ADR 0025), new signing key (ADR 0037). */
interface AppMoveTexts {
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

internal object AppMoveTextsDe : AppMoveTexts {
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
    override val oldAppText = "Die alte App ist noch auf diesem Handy. Sie funktioniert nicht mehr. Lösch sie, damit du nicht die falsche öffnest."
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

internal object AppMoveTextsEn : AppMoveTexts {
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
    override val oldAppText = "The old app is still on this phone. It no longer works. Delete it so you don't open the wrong one."
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
