package app.anynear.i18n

/** Settings, advanced, log, update. */
interface SettingsTexts {
    // Settings
    val title: String
    val radio: String
    val connectRadio: String
    val yourName: String

    /** „So sehen dich deine Kreise und Kontakte“ */
    val profileSubtitle: String
    val editProfile: String
    val editProfileTitle: String
    val nameAppliesEverywhere: String
    val preparingPhoto: String
    val choosePhoto: String
    val defaultPhoto: String

    // Connection
    val connection: String
    val offlineFooter: String
    val connected: String
    val appUnreachable: String
    val noInternet: String

    // Internet page: relays in the network and whether the app reaches them
    val mailboxes: String
    val mailbox: String
    val mailboxesFooter: String
    fun mailboxesReached(reached: Int, total: Int): String
    val relayReached: String
    val relayUnreachable: String
    val relayUnused: String
    val connectRadioHint: String
    val radioUpdateAvailable: String
    val radioNotSetUp: String

    // Radio page
    val battery: String

    /** „Am Strom“ */
    val charging: String
    fun batteryLevel(percent: Int): String
    val radioUpdates: String
    val radioUpdatesLater: String
    val needsHelp: String

    /** Radio on Wi-Fi used by another phone or app; the app has stepped back and asks. */
    val radioInUseTitle: String
    val radioInUseText: String
    val radioInUseNotification: String
    val radioInUseConnectHere: String
    val radioInUseLeave: String
    val pairingFails: String
    val pairingFailsText: String
    val openBluetoothSettings: String
    val pairingWaiting: String
    val pairingWaitingText: String
    val pairingWaitingNoWindow: String
    val pairingStillFails: String
    val pairingStillFailsText: String
    val updateRadio: String
    val setUpRadio: String
    val updateRadioHint: String
    val setUpRadioHint: String
    val sharedRadioFooter: String
    val disconnectRadio: String
    val reconnectRadio: String
    val radioDisconnected: String
    val radioDisconnectedHint: String
    val myRadios: String
    val radios: String
    val radioNotConnected: String
    val wayTitle: String
    val wayBluetooth: String
    val wayWifi: String
    val wayUsb: String
    val wayPowerNote: String
    val wayWifiWarning: String
    val wayBluetoothTitle: String
    val wayBluetoothText: String
    val wayWorkingWifi: String
    val wayWorkingBluetooth: String
    val wayNotSeenWifi: String
    val wayNotSeenBluetooth: String
    val wayFailed: String
    fun wayDoneWifi(name: String): String
    fun wayDoneBluetooth(name: String): String
    val radioViaWifi: String
    val radioViaUsb: String
    val pairNewRadio: String
    val pairNewRadioFooter: String
    val pairRadio: String
    val searchRadios: String
    val myRadiosFooter: String
    val radioNearby: String
    val radioLooking: String
    val unnamedRadio: String
    val forgetRadio: String
    fun forgetRadioTitle(name: String): String
    val forgetRadioText: String
    val confirmForget: String

    // Advanced for the radio
    val editOnlyWhenConnected: String
    val radioName: String
    val radioNameInvalid: String
    val radioNameApplying: String
    val radioApplying: String
    val radioApplyingHint: String
    fun circlesInternetOnly(count: Int): String
    fun circlesInternetOnlyHint(slots: Int): String
    val radioNameTaken: String
    val saveName: String
    val suggestion: String

    // Privacy: usage statistics (ADR 0045)
    val privacy: String
    val usageStats: String
    val usageStatsOn: String
    val usageStatsOff: String
    val usageStatsTestMode: String
    val usageStatsSent: String
    val usageStatsNone: String
    val usageStatsOffTitle: String
    val usageStatsOffText: String
    val usageStatsOffConfirm: String
    val usageStatsKeep: String
    fun usageStatsReport(devices: Int, radios: Int, circles: Int, channels: Int, nina: Boolean, platform: String, version: String): String
    val relayForOthers: String
    val servedByPatron: String
    val relayForOthersOn: String
    val relayForOthersOff: String
    /** Switch on the radio page under „Erweitert“ (ADR 0085). */
    val chargeReminder: String
    val chargeReminderOn: String
    val chargeReminderOff: String

    /** Notification: radio reports low battery. */
    val chargeLowTitle: String
    fun chargeLowText(percent: Int): String

    /** Notification: radio was not connected for [days] days; [percent] last known level or `null`. */
    val chargeAwayTitle: String
    fun chargeAwayText(days: Int, percent: Int?): String
    val reapplySettings: String
    val reapplyHint: String
    val reapplyTitle: String
    val reapplyText: String
    val reapply: String

    // Log
    /** First line of the log: „anynear-Protokoll, Version 1.2“ */
    fun logHeader(version: String?): String
    val debugLog: String
    val logEmpty: String
    fun logEntries(n: Int): String
    val log: String
    val logInfo: String
    val clearLog: String

    // App and update
    fun newVersionTitle(version: String?): String
    val updateIntro: String
    val updateNow: String
    fun version(version: String): String
    val appSection: String
    val upToDate: String
    val check: String
    val checking: String
    fun newVersionAvailable(version: String?): String
    fun updating(version: String?): String
    val checkForUpdate: String
    val needsInstallPermission: String
    fun downloading(version: String?): String
    val installing: String

    // Appearance
    val appearance: String
    val appearanceSystem: String
    val appearanceLight: String
    val appearanceDark: String
    val aboutApp: String
    val lightOrDark: String
    val internetAndRadio: String
    val security: String
    val notifications: String
    val notificationsNotAllowed: String
    val notificationsInPhoneSettings: String
    val backgroundRestricted: String
    val backgroundStopped: String
    val license: String
    val licenseShort: String
    val licenseText: String
    val notificationsOnOff: String
    val myProfile: String
    val myProfileHint: String
    val shareContactHint: String
    val connectionsAndRadio: String
    val connections: String
    val internetConnected: String
    val noRadio: String
    val settingsHint: String
    val help: String
    val language: String
    val languageCurrent: String
    val languageFooter: String
    val helpInShort: String
    val helpMoreNotAvailable: String
    val helpWhatIsCircle: String
    val helpWhatIsContactCode: String
    val helpNeedRadio: String

    // Start afresh (ADR 0046)
    val startOver: String
    val resetDevice: String
    val resetDeviceHint: String
    val resetTitle: String
    val resetText: String
    val resetLosesPerson: String
    val resetMore: String
    val resetConfirm: String
    val eraseAll: String
    val eraseAllHint: String
    val eraseTitle: String
    val eraseText: String
    val eraseMore: String
    val eraseConfirm: String
    val eraseReason: String
    val erasing: String
    val eraseOffline: String
}

internal object SettingsTextsDe : SettingsTexts {
    override val title = "Einstellungen"
    override val radio = "Funkgerät"
    override val connectRadio = "Funkgerät verbinden"
    override val yourName = "Dein Name"
    override val profileSubtitle = "So sehen dich deine Kreise und Kontakte"
    override val editProfile = "Name und Bild ändern"
    override val editProfileTitle = "Dein Name und Bild"
    override val nameAppliesEverywhere = "Gilt in allen deinen Kreisen."
    override val preparingPhoto = "Wird vorbereitet …"
    override val choosePhoto = "Foto wählen"
    override val defaultPhoto = "Standardbild"

    override val connection = "Verbindung"
    override val offlineFooter = "Nachrichten warten und gehen automatisch raus."
    override val connected = "Verbunden"
    override val appUnreachable = "$APP ist gerade nicht erreichbar, obwohl dein Handy Internet hat"
    override val noInternet = "Kein Internet"
    override val mailboxes = "Standorte"
    override val mailbox = "Standort"
    override val mailboxesFooter = "Deine Nachrichten liegen verschlüsselt verteilt über viele Standorte in Europa, die sich gegenseitig ersetzen. Die Schlüssel hast nur du."
    override fun mailboxesReached(reached: Int, total: Int) = "$reached von $total Standorten erreicht"
    override val relayReached = "Erreicht"
    override val relayUnreachable = "Nicht erreichbar"
    override val relayUnused = "Gerade nicht abgefragt"
    override val connectRadioHint = "Optional: Deine Kreise erreichen dich auch ohne Internet und Mobilfunk"
    override val radioUpdateAvailable = "Aktualisierung für $APP verfügbar"
    override val radioNotSetUp = "Noch nicht für $APP eingerichtet"

    override val battery = "Akku"
    override val charging = "Am Strom"
    override fun batteryLevel(percent: Int) = "$percent %"
    override val radioUpdates = "Aktualisierungen"
    override val radioUpdatesLater = "Kommen automatisch in einer späteren Version"
    override val needsHelp = "Braucht deine Hilfe"
    override val radioInUseTitle = "Funkgerät wird woanders genutzt"
    override val radioInUseText = "Ein anderes Handy oder eine andere App ist gerade mit deinem Funkgerät verbunden, zum Beispiel die Meshtastic-App. Es kann immer nur eins verbunden sein. Damit ihr euch nicht ständig gegenseitig trennt, hat $APP Platz gemacht. Wenn du hier verbindest, wird das andere getrennt."
    override val radioInUseNotification = "$APP hat Platz gemacht, damit ihr euch nicht gegenseitig trennt. Tippe, um zu entscheiden."
    override val radioInUseConnectHere = "Hier verbinden"
    override val radioInUseLeave = "Dort lassen"
    override val pairingFails = "Koppeln klappt nicht"
    override val pairingFailsText = "Entferne dein Funkgerät in den Bluetooth-Einstellungen und verbinde es neu."
    override val openBluetoothSettings = "Bluetooth-Einstellungen öffnen"
    override val pairingWaiting = "Zahl eingeben"
    override val pairingWaitingText = "Dein Handy fragt nach der Zahl vom Display des Funkgeräts."
    override val pairingWaitingNoWindow =
        "Kein Fenster zu sehen? Schau in den Benachrichtigungen nach oder öffne die Bluetooth-Einstellungen und tippe dort auf das Funkgerät."
    override val pairingStillFails = "Verbinden klappt weiter nicht"
    override val pairingStillFailsText = "Setze dein Funkgerät per USB-Kabel zurück, dann klappt das Koppeln wieder."
    override val updateRadio = "Funkgerät aktualisieren"
    override val setUpRadio = "Funkgerät einrichten"
    override val updateRadioHint = "Verbesserte Einstellungen für $APP-Funkgeräte"
    override val setUpRadioHint = "Dein Funkgerät ist noch nicht für $APP eingerichtet"
    override val sharedRadioFooter =
        "Teilt ihr euch das Funkgerät, kommen Nachrichten über Funk nur bei dem an, dessen Handy gerade verbunden ist. Übers Internet bekommen sie alle."
    override val disconnectRadio = "Trennen"
    override val reconnectRadio = "Verbinden"
    override val radioDisconnected = "Getrennt"
    override val radioDisconnectedHint = "Nachrichten laufen jetzt übers Internet. Mit „Verbinden“ nutzt die App das Funkgerät wieder."
    override val myRadios = "Meine Funkgeräte"
    override val radios = "Funkgeräte"
    override val radioNotConnected = "Nicht verbunden"
    override val wayTitle = "So nutzt du es"
    override val wayBluetooth = "Unterwegs, per Bluetooth"
    override val wayWifi = "Stationär im WLAN"
    override val wayUsb = "Per USB-Kabel"
    override val wayPowerNote = "Im WLAN hängt das Funkgerät am Router. Fällt der Strom aus, ist auch das WLAN weg. Dann hilft das USB-Kabel: direkt so benutzen oder zurück auf Bluetooth stellen."
    override val wayWifiWarning = "Stimmt das Passwort nicht, erreichst du das Funkgerät danach nur noch mit dem USB-Kabel. Darüber lässt es sich wieder umstellen."
    override val wayBluetoothTitle = "Auf Bluetooth umstellen?"
    override val wayBluetoothText = "Das Funkgerät startet neu und verbindet sich dann per Bluetooth. Dabei fragt dein Handy nach der Zahl vom Display."
    override val wayWorkingWifi = "Dein Funkgerät startet neu und meldet sich gleich im WLAN …"
    override val wayWorkingBluetooth = "Dein Funkgerät startet neu und wird per Bluetooth gesucht …"
    override val wayNotSeenWifi = "Das Funkgerät meldet sich nicht im WLAN. Stimmt das Passwort nicht, steck es per USB-Kabel ans Handy und stell es dort wieder um."
    override val wayNotSeenBluetooth = "Das Funkgerät taucht über Bluetooth nicht auf. Ist es mit einem anderen Handy verbunden? Sonst hilft das USB-Kabel."
    override val wayFailed = "Das hat nicht geklappt. Am Funkgerät hat sich nichts geändert."
    override fun wayDoneWifi(name: String) = "„$name“ ist jetzt im WLAN verbunden."
    override fun wayDoneBluetooth(name: String) = "„$name“ verbindet sich jetzt per Bluetooth. Gib die Zahl vom Display ein, wenn dein Handy danach fragt."
    override val radioViaWifi = "im WLAN"
    override val radioViaUsb = "per USB-Kabel"
    override val pairNewRadio = "Neues Funkgerät koppeln"
    override val pairNewRadioFooter =
        "Beim ersten Koppeln fragt dein Handy nach einer Zahl. Sie steht auf dem Display des Funkgeräts, ohne Display ist es 123456."
    override val pairRadio = "Koppeln"
    override val searchRadios = "Suchen"
    override val myRadiosFooter = "Verbinden geht, wenn das Funkgerät eingeschaltet und in der Nähe ist."
    override val radioNearby = "In der Nähe"
    override val radioLooking = "Wird gesucht …"
    override val unnamedRadio = "Funkgerät"
    override val forgetRadio = "Funkgerät entfernen"
    override fun forgetRadioTitle(name: String) = "„$name“ entfernen?"
    override val forgetRadioText = "Die App vergisst dieses Funkgerät. Du kannst es später wieder neu verbinden."
    override val confirmForget = "Entfernen"

    override val editOnlyWhenConnected = "Ändern geht nur, solange das Funkgerät verbunden ist."
    override val radioName = "Name des Funkgeräts"
    override val radioNameInvalid = "Bitte mit mindestens einem Buchstaben oder einer Zahl."
    override val radioNameApplying = "Wird übernommen. Das Funkgerät startet dabei neu, das dauert etwa eine Minute."
    override val radioApplying = "Übernimmt Änderungen …"
    override val radioApplyingHint = "Das Funkgerät startet dabei neu. Bitte nicht ausschalten."
    override fun circlesInternetOnly(count: Int) =
        if (count == 1) "1 Kreis läuft gerade nur übers Internet" else "$count Kreise laufen gerade nur übers Internet"
    override fun circlesInternetOnlyHint(slots: Int) =
        "Auf dein Funkgerät passen $slots Kreise. $APP gibt die Plätze von selbst den Kreisen, in denen gerade am meisten los ist."
    override val radioNameTaken = "Diesen Namen gibt es in deiner Nähe schon."
    override val saveName = "Namen speichern"
    override val suggestion = "Vorschlag"

    override val privacy = "Datenschutz"
    override val usageStats = "Nutzungsstatistik teilen"
    override val usageStatsOn = "Einmal am Tag ein paar Zahlen, ohne Namen und ohne Inhalte."
    override val usageStatsOff = "Aus. Es wird nichts geschickt."
    override val usageStatsTestMode = "Testmodus: Diese Version schickt nichts."
    override val usageStatsSent = "Was zuletzt geschickt wurde"
    override val usageStatsNone = "Noch nichts geschickt."
    override val usageStatsOffTitle = "Nutzungsstatistik abschalten?"
    override val usageStatsOffText = "$APP schickt einmal am Tag nur ein paar grobe Zahlen: zum Beispiel die Version der App, ob Android oder iOS und wie viele Geräte und Kreise du hast. Namen, Inhalte, Standorte und IP-Adressen werden nicht gespeichert. Die Zahlen helfen uns, $APP möglichst gut weiterzuentwickeln."
    override val usageStatsOffConfirm = "Abschalten"
    override val usageStatsKeep = "Eingeschaltet lassen"
    override fun usageStatsReport(devices: Int, radios: Int, circles: Int, channels: Int, nina: Boolean, platform: String, version: String) =
        listOfNotNull(
            if (devices == 1) "1 Gerät" else "$devices Geräte",
            if (radios == 1) "1 Funkgerät an diesem Gerät" else "$radios Funkgeräte an diesem Gerät",
            if (circles == 1) "1 Kreis" else "$circles Kreise",
            if (channels == 1) "1 Kanal" else "$channels Kanäle",
            "NINA abonniert".takeIf { nina },
            "$platform $version",
        ).joinToString(", ")
    override val relayForOthers = "Für andere weiterreichen"
    override val servedByPatron = "Nachrichten kommen über Funk zu dir."
    override val relayForOthersOn = "Dein Handy hilft, dass Nachrichten anderer über Funk ankommen. Lesen kann es sie nicht."
    override val relayForOthersOff = "Aus: Dein Handy reicht nichts für andere weiter."
    override val chargeReminder = "Ans Laden erinnern"
    override val chargeReminderOn = "Bei wenig Akku und wenn das Funkgerät lange nicht verbunden war."
    override val chargeReminderOff = "Aus: Du bekommst keine Erinnerung zum Laden."
    override val chargeLowTitle = "Funkgerät fast leer"
    override fun chargeLowText(percent: Int) = "Noch $percent %. Lade es bald, damit du im Notfall funken kannst."
    override val chargeAwayTitle = "Funkgerät lange nicht gesehen"
    override fun chargeAwayText(days: Int, percent: Int?) =
        "Dein Funkgerät war $days Tage nicht verbunden" + (percent?.let { ", zuletzt mit $it %" } ?: "") +
            ". Lade es und schalte es ein, damit es im Notfall bereit ist."
    override val reapplySettings = "$APP-Einstellungen neu aufspielen"
    override val reapplyHint = "Falls am Funkgerät etwas verstellt wurde"
    override val reapplyTitle = "$APP-Einstellungen neu aufspielen?"
    override val reapplyText = "Alle $APP-Einstellungen werden erneut aufs Funkgerät geschrieben. Es startet dabei neu."
    override val reapply = "Aufspielen"

    override fun logHeader(version: String?) = "$APP-Protokoll" + (version?.let { ", Version $it" } ?: "")
    override val debugLog = "Protokoll für die Fehlersuche"
    override val logEmpty = "Noch leer"
    override fun logEntries(n: Int) = if (n == 1) "1 Eintrag" else "$n Einträge"
    override val log = "Protokoll"
    override val logInfo = "Hilft bei der Fehlersuche. Ohne Nachrichtentexte; Geräte stehen als Kennung drin. Nur im Speicher, nach einem Neustart leer."
    override val clearLog = "Leeren"

    override fun newVersionTitle(version: String?) = if (version == null) "Neue Version von $APP" else "$APP $version ist da"
    override val updateIntro = "Eine neue Version ist da. Das Aktualisieren dauert etwa eine Minute, deine Kreise und Nachrichten bleiben."
    override val updateNow = "Jetzt aktualisieren"
    override fun version(version: String) = "Version $version"
    override val appSection = "App"
    override val upToDate = "Du hast die neueste Version"
    override val check = "Prüfen"
    override val checking = "Sehe nach …"
    override fun newVersionAvailable(version: String?) = if (version == null) "Neue Version ist da" else "Neue Version $version ist da"
    override fun updating(version: String?) = if (version == null) "Wird aktualisiert" else "Wird auf Version $version aktualisiert"
    override val checkForUpdate = "Nach neuer Version sehen"
    override val needsInstallPermission =
        "Android fragt einmalig, ob $APP Aktualisierungen installieren darf. Erlaube es und tippe dann auf „Zurück“, danach geht es von selbst weiter."
    override fun downloading(version: String?) = if (version == null) "Neue Version wird geladen …" else "Version $version wird geladen …"
    override val installing = "Gleich fragt Android, ob $APP aktualisiert werden soll. Tippe auf „Aktualisieren“, danach startet $APP neu."

    override val appearance = "Darstellung"
    override val appearanceSystem = "Wie das Handy"
    override val appearanceLight = "Hell"
    override val appearanceDark = "Dunkel"
    override val aboutApp = "Über $APP"
    override val lightOrDark = "Hell oder dunkel"
    override val internetAndRadio = "Internet und Funkgerät"
    override val security = "Sicherheit"
    override val notifications = "Benachrichtigungen"
    override val notificationsNotAllowed = "Noch nicht erlaubt. Tippen, um zu erlauben."
    override val notificationsInPhoneSettings = "In den Einstellungen des Handys ein- oder ausschalten"
    override val backgroundRestricted = "Im Hintergrund eingeschränkt. Tippen, um es zu erlauben."
    override val backgroundStopped = "Wurde im Hintergrund beendet. Tippen, um es zu erlauben."
    override val license = "Lizenz"
    override val licenseShort = "Freie Software, GPL-3.0-or-later"
    override val licenseText = "$APP ist freie Software. Der Quellcode steht unter der GNU General Public License, Version 3 oder neuer (GPL-3.0-or-later). Du darfst ihn nach deren Bedingungen nutzen, ändern und weitergeben. Der Name $APP und die Logos sind davon ausgenommen.\n\nAmtliche Warnungen: Kreise und Gemeindemittelpunkte aus dem Gemeindeverzeichnis (GV-ISys), Gebietsstand 31.12.2025, © Statistisches Bundesamt (Destatis), 2026; Koordinaten: Bundesamt für Kartographie und Geodäsie (BKG)."
    override val notificationsOnOff = "An oder aus"
    override val myProfile = "Mein Profil"
    override val myProfileHint = "Mein Profil · Name, Bild, Kontakt teilen"
    override val shareContactHint = "Als QR-Code, als Link oder zum Kopieren."
    override val connectionsAndRadio = "Verbindungen und Funkgerät"
    override val connections = "Verbindungen"
    override val internetConnected = "Internet verbunden"
    override val noRadio = "Kein Funkgerät"
    override val settingsHint = "Darstellung, Sprache, Benachrichtigungen"
    override val help = "Hilfe"
    override val language = "Sprache"
    override val languageCurrent = "Wie das Handy · Deutsch"
    override val languageFooter = "$APP folgt der Sprache des Handys: Deutsch oder Englisch."
    override val helpInShort = "Kurz erklärt"
    override val helpMoreNotAvailable = "Weitere Hilfe ist noch nicht verfügbar."
    override val helpWhatIsCircle = "Was ist ein Kreis?"
    override val helpWhatIsContactCode = "Wofür ist mein Kontaktcode?"
    override val helpNeedRadio = "Brauche ich ein Funkgerät?"

    override val startOver = "Neu anfangen"
    override val resetDevice = "Gerät zurücksetzen"
    override val resetDeviceHint = "Alles auf diesem Gerät löschen, wie frisch installiert"
    override val resetTitle = "Gerät zurücksetzen?"
    override val resetText = "$APP löscht alles auf diesem Gerät und startet neu, wie frisch installiert. Deine Kreise und Kontakte bleiben im Netz."
    override val resetLosesPerson = "Du hast keine Notfallkarte und kein anderes Gerät mit $APP. Danach kommst du nicht mehr an deine Kreise und Kontakte."
    override val resetMore = "Gelöscht werden Nachrichten, Bilder, Meine Ablage, Passwörter, Einstellungen und die Schlüssel dieses Geräts. Zurück geht es bei der Einrichtung mit „Ich hatte $APP schon“: mit einem anderen Gerät, der Notfallkarte oder einer Sicherung. Unter „Meine Geräte“ steht dieses Gerät danach noch; entferne es dort von einem anderen Gerät aus. Falls sich $APP danach nur schließt, öffne es wieder."
    override val resetConfirm = "Zurücksetzen"
    override val eraseAll = "Alle Daten löschen"
    override val eraseAllHint = "Auf diesem Gerät und im Netz"
    override val eraseTitle = "Alle Daten löschen?"
    override val eraseText = "$APP löscht dich hier und im Netz: Du verlässt alle Kreise, Kontakte, Meine Ablage und Passwörter sind weg, deine anderen Geräte werden abgemeldet. Das lässt sich nicht rückgängig machen."
    override val eraseMore = "Nachrichten, die andere schon bekommen haben, bleiben bei ihnen. Deine Notfallkarte führt danach zu nichts mehr. Sicherungsdateien, die du selbst gespeichert hast, löscht $APP nicht. Dafür braucht es Internet. Falls sich $APP danach nur schließt, öffne es wieder."
    override val eraseConfirm = "Alles löschen"
    override val eraseReason = "Alles löschen"
    override val erasing = "Wird gelöscht …"
    override val eraseOffline = "Gerade ist kein Internet da. Ohne Internet kann $APP nichts im Netz löschen; es wurde nichts verändert."
}

internal object SettingsTextsEn : SettingsTexts {
    override val title = "Settings"
    override val radio = "Radio"
    override val connectRadio = "Connect a radio"
    override val yourName = "Your name"
    override val profileSubtitle = "This is how your circles and contacts see you"
    override val editProfile = "Change name and picture"
    override val editProfileTitle = "Your name and picture"
    override val nameAppliesEverywhere = "Applies in all your circles."
    override val preparingPhoto = "Getting ready …"
    override val choosePhoto = "Choose photo"
    override val defaultPhoto = "Default picture"

    override val connection = "Connection"
    override val offlineFooter = "Messages wait and go out automatically."
    override val connected = "Connected"
    override val appUnreachable = "$APP can't be reached right now, even though your phone has internet"
    override val noInternet = "No internet"
    override val mailboxes = "Locations"
    override val mailbox = "Location"
    override val mailboxesFooter = "Your messages are encrypted and spread across many locations in Europe that stand in for each other. Only you hold the keys."
    override fun mailboxesReached(reached: Int, total: Int) = "$reached of $total locations reachable"
    override val relayReached = "Reachable"
    override val relayUnreachable = "Not reachable"
    override val relayUnused = "Not in use right now"
    override val connectRadioHint = "Optional: Your circles can reach you even without internet or cell service"
    override val radioUpdateAvailable = "Update for $APP available"
    override val radioNotSetUp = "Not set up for $APP yet"

    override val battery = "Battery"
    override val charging = "Plugged in"
    override fun batteryLevel(percent: Int) = "$percent%"
    override val radioUpdates = "Updates"
    override val radioUpdatesLater = "Coming automatically in a later version"
    override val needsHelp = "Needs your help"
    override val radioInUseTitle = "Radio is in use elsewhere"
    override val radioInUseText = "Another phone or app is connected to your radio right now, for example the Meshtastic app. Only one can be connected at a time. So that you don't keep disconnecting each other, $APP has stepped back. If you connect here, the other one is disconnected."
    override val radioInUseNotification = "$APP has stepped back so that you don't keep disconnecting each other. Tap to decide."
    override val radioInUseConnectHere = "Connect here"
    override val radioInUseLeave = "Leave it there"
    override val pairingFails = "Pairing doesn't work"
    override val pairingFailsText = "Remove your radio in the Bluetooth settings and connect it again."
    override val openBluetoothSettings = "Open Bluetooth settings"
    override val pairingWaiting = "Enter the number"
    override val pairingWaitingText = "Your phone asks for the number shown on the radio's display."
    override val pairingWaitingNoWindow =
        "No window? Check your notifications or open the Bluetooth settings and tap the radio there."
    override val pairingStillFails = "Connecting still doesn't work"
    override val pairingStillFailsText = "Reset your radio via USB cable, then pairing works again."
    override val updateRadio = "Update radio"
    override val setUpRadio = "Set up radio"
    override val updateRadioHint = "Improved settings for $APP radios"
    override val setUpRadioHint = "Your radio isn't set up for $APP yet"
    override val sharedRadioFooter =
        "If you share the radio, messages by radio only reach whoever's phone is connected right now. Everyone gets them over the internet."
    override val disconnectRadio = "Disconnect"
    override val reconnectRadio = "Connect"
    override val radioDisconnected = "Disconnected"
    override val radioDisconnectedHint = "Messages now go over the internet. Tap “Connect” to use the radio again."
    override val myRadios = "My radios"
    override val radios = "Radios"
    override val radioNotConnected = "Not connected"
    override val wayTitle = "How you use it"
    override val wayBluetooth = "On the move, over Bluetooth"
    override val wayWifi = "Stationary on Wi-Fi"
    override val wayUsb = "With a USB cable"
    override val wayPowerNote = "On Wi-Fi the radio depends on your router. If the power goes out, the Wi-Fi is gone too. Then the USB cable helps: use it that way or switch back to Bluetooth."
    override val wayWifiWarning = "If the password is wrong, you can only reach the radio with the USB cable afterwards. You can switch it back from there."
    override val wayBluetoothTitle = "Switch to Bluetooth?"
    override val wayBluetoothText = "The radio restarts and then connects over Bluetooth. Your phone will ask for the number shown on the display."
    override val wayWorkingWifi = "Your radio is restarting and will show up on your Wi-Fi in a moment …"
    override val wayWorkingBluetooth = "Your radio is restarting; looking for it over Bluetooth …"
    override val wayNotSeenWifi = "The radio doesn't show up on your Wi-Fi. If the password was wrong, plug it into your phone with a USB cable and switch it back there."
    override val wayNotSeenBluetooth = "The radio doesn't show up over Bluetooth. Is it connected to another phone? Otherwise the USB cable helps."
    override val wayFailed = "That didn't work. Nothing changed on the radio."
    override fun wayDoneWifi(name: String) = "“$name” is now connected over Wi-Fi."
    override fun wayDoneBluetooth(name: String) = "“$name” now connects over Bluetooth. Enter the number from the display when your phone asks for it."
    override val radioViaWifi = "on Wi-Fi"
    override val radioViaUsb = "with USB cable"
    override val pairNewRadio = "Pair a new radio"
    override val pairNewRadioFooter =
        "The first time you pair, your phone asks for a number. It's shown on the radio's display; without a display it's 123456."
    override val pairRadio = "Pair"
    override val searchRadios = "Search"
    override val myRadiosFooter = "You can connect when the radio is switched on and nearby."
    override val radioNearby = "Nearby"
    override val radioLooking = "Looking …"
    override val unnamedRadio = "Radio"
    override val forgetRadio = "Remove radio"
    override fun forgetRadioTitle(name: String) = "Remove “$name”?"
    override val forgetRadioText = "The app forgets this radio. You can connect it again later."
    override val confirmForget = "Remove"

    override val editOnlyWhenConnected = "You can only make changes while the radio is connected."
    override val radioName = "Radio name"
    override val radioNameInvalid = "Please use at least one letter or number."
    override val radioNameApplying = "Being applied. Your radio restarts for this, which takes about a minute."
    override val radioApplying = "Applying changes…"
    override val radioApplyingHint = "Your radio restarts while doing this. Please don't switch it off."
    override fun circlesInternetOnly(count: Int) =
        if (count == 1) "1 circle is running over the internet only" else "$count circles are running over the internet only"
    override fun circlesInternetOnlyHint(slots: Int) =
        "Your radio has room for $slots circles. $APP gives the places to the circles with the most going on, all by itself."
    override val radioNameTaken = "There's already a radio with this name nearby."
    override val saveName = "Save name"
    override val suggestion = "Suggestion"

    override val privacy = "Privacy"
    override val usageStats = "Share usage statistics"
    override val usageStatsOn = "A few numbers once a day, without names or content."
    override val usageStatsOff = "Off. Nothing is sent."
    override val usageStatsTestMode = "Test mode: this build sends nothing."
    override val usageStatsSent = "What was sent last"
    override val usageStatsNone = "Nothing sent yet."
    override val usageStatsOffTitle = "Switch off usage statistics?"
    override val usageStatsOffText = "$APP sends only a few rough numbers once a day: for example the app version, whether Android or iOS, and how many devices and circles you have. Names, content, locations and IP addresses are not stored. The numbers help us keep improving $APP."
    override val usageStatsOffConfirm = "Switch off"
    override val usageStatsKeep = "Keep on"
    override fun usageStatsReport(devices: Int, radios: Int, circles: Int, channels: Int, nina: Boolean, platform: String, version: String) =
        listOfNotNull(
            if (devices == 1) "1 device" else "$devices devices",
            if (radios == 1) "1 radio on this device" else "$radios radios on this device",
            if (circles == 1) "1 circle" else "$circles circles",
            if (channels == 1) "1 channel" else "$channels channels",
            "NINA subscribed".takeIf { nina },
            "$platform $version",
        ).joinToString(", ")
    override val relayForOthers = "Pass on for others"
    override val servedByPatron = "Messages reach you by radio."
    override val relayForOthersOn = "Your phone helps messages from others arrive by radio. It cannot read them."
    override val relayForOthersOff = "Off: your phone passes on nothing for others."
    override val chargeReminder = "Remind me to charge"
    override val chargeReminderOn = "When the battery is low and when the radio has not been connected for a long time."
    override val chargeReminderOff = "Off: you get no reminder to charge."
    override val chargeLowTitle = "Radio almost empty"
    override fun chargeLowText(percent: Int) = "$percent% left. Charge it soon so you can use radio in an emergency."
    override val chargeAwayTitle = "Radio not seen for a while"
    override fun chargeAwayText(days: Int, percent: Int?) =
        "Your radio has not been connected for $days days" + (percent?.let { ", last at $it%" } ?: "") +
            ". Charge it and switch it on so it is ready in an emergency."
    override val reapplySettings = "Reapply $APP settings"
    override val reapplyHint = "In case something was changed on the radio"
    override val reapplyTitle = "Reapply $APP settings?"
    override val reapplyText = "All $APP settings are written to the radio again. It restarts while doing this."
    override val reapply = "Apply"

    override fun logHeader(version: String?) = "$APP log" + (version?.let { ", version $it" } ?: "")
    override val debugLog = "Log for troubleshooting"
    override val logEmpty = "Empty so far"
    override fun logEntries(n: Int) = if (n == 1) "1 entry" else "$n entries"
    override val log = "Log"
    override val logInfo = "Helps with troubleshooting. No message texts; devices appear as IDs. Only kept in memory, empty after a restart."
    override val clearLog = "Clear"

    override fun newVersionTitle(version: String?) = if (version == null) "New version of $APP" else "$APP $version is here"
    override val updateIntro = "A new version is here. Updating takes about a minute. Your circles and messages stay."
    override val updateNow = "Update now"
    override fun version(version: String) = "Version $version"
    override val appSection = "App"
    override val upToDate = "You have the latest version"
    override val check = "Check"
    override val checking = "Checking …"
    override fun newVersionAvailable(version: String?) = if (version == null) "New version available" else "New version $version available"
    override fun updating(version: String?) = if (version == null) "Updating" else "Updating to version $version"
    override val checkForUpdate = "Check for new version"
    override val needsInstallPermission =
        "Android asks once whether $APP may install updates. Allow it, then tap “Back”. It continues on its own after that."
    override fun downloading(version: String?) = if (version == null) "Downloading new version …" else "Downloading version $version …"
    override val installing = "Android will ask in a moment whether to update $APP. Tap “Update”, then $APP restarts."

    override val appearance = "Appearance"
    override val appearanceSystem = "Same as phone"
    override val appearanceLight = "Light"
    override val appearanceDark = "Dark"
    override val aboutApp = "About $APP"
    override val lightOrDark = "Light or dark"
    override val internetAndRadio = "Internet and radio"
    override val security = "Security"
    override val notifications = "Notifications"
    override val notificationsNotAllowed = "Not allowed yet. Tap to allow."
    override val notificationsInPhoneSettings = "Turn on or off in your phone's settings"
    override val backgroundRestricted = "Restricted in the background. Tap to allow it."
    override val backgroundStopped = "Was closed in the background. Tap to allow it."
    override val license = "License"
    override val licenseShort = "Free software, GPL-3.0-or-later"
    override val licenseText = "$APP is free software. The source code is licensed under the GNU General Public License, version 3 or later (GPL-3.0-or-later). You may use, change and share it under its terms. The name $APP and the logos are excluded.\n\nOfficial warnings: districts and municipality centre points from the municipality directory GV-ISys, as of 31 Dec 2025, © Federal Statistical Office of Germany (Destatis), 2026; coordinates: Federal Agency for Cartography and Geodesy (BKG)."
    override val notificationsOnOff = "On or off"
    override val myProfile = "My profile"
    override val myProfileHint = "My profile · name, picture, share contact"
    override val shareContactHint = "As a QR code, as a link or to copy."
    override val connectionsAndRadio = "Connections and radio"
    override val connections = "Connections"
    override val internetConnected = "Internet connected"
    override val noRadio = "No radio"
    override val settingsHint = "Appearance, language, notifications"
    override val help = "Help"
    override val language = "Language"
    override val languageCurrent = "Same as phone · English"
    override val languageFooter = "$APP follows your phone's language: German or English."
    override val helpInShort = "In short"
    override val helpMoreNotAvailable = "More help is not available yet."
    override val helpWhatIsCircle = "What is a circle?"
    override val helpWhatIsContactCode = "What is my contact code for?"
    override val helpNeedRadio = "Do I need a radio?"

    override val startOver = "Start over"
    override val resetDevice = "Reset this device"
    override val resetDeviceHint = "Delete everything on this device, as if newly installed"
    override val resetTitle = "Reset this device?"
    override val resetText = "$APP deletes everything on this device and restarts as if newly installed. Your circles and contacts stay in the network."
    override val resetLosesPerson = "You have no emergency card and no other device with $APP. Afterwards you can no longer get to your circles and contacts."
    override val resetMore = "This deletes messages, pictures, My files, passwords, settings and this device's keys. To come back, choose “I used $APP before” during setup: with another device, the emergency card or a backup. This device still shows under “My devices”; remove it there from another device. If $APP just closes afterwards, open it again."
    override val resetConfirm = "Reset"
    override val eraseAll = "Delete all data"
    override val eraseAllHint = "On this device and in the network"
    override val eraseTitle = "Delete all data?"
    override val eraseText = "$APP deletes you here and in the network: you leave all circles, contacts, My files and passwords are gone, your other devices are signed out. This cannot be undone."
    override val eraseMore = "Messages others already received stay with them. Your emergency card leads to nothing afterwards. Backup files you saved yourself are not deleted by $APP. This needs the internet. If $APP just closes afterwards, open it again."
    override val eraseConfirm = "Delete everything"
    override val eraseReason = "Delete everything"
    override val erasing = "Deleting …"
    override val eraseOffline = "There is no internet right now. Without it $APP cannot delete anything in the network; nothing was changed."
}
