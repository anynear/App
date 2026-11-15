package app.anynear.i18n

/** New software for the own radio (ADR 0056). „Firmware“ only appears under „Erweitert“. */
interface RadioSoftwareTexts {
    val title: String
    val noticeText: String
    val updateNow: String
    val ready: String
    val loading: String
    val notConnected: String
    val radioBattery: String
    val phoneBattery: String
    val messagesWaiting: String
    val notNowTitle: String
    val keepOpen: String
    val preparing: String
    fun progress(percent: Int): String
    val checking: String
    val leaveTitle: String
    val leaveText: String
    val leaveWriting: String
    val stay: String
    val doneTitle: String
    val doneText: String
    val doneSetUpAgain: String
    val failedTitle: String
    val failedText: String
    val failedWrote: String
    val usbText: String
    fun advancedVersion(version: String): String
    val advancedUnknown: String
    val checkNow: String
    val checkRunning: String
    val checkFailed: String
    val upToDate: String
    fun newerAvailable(version: String): String
    fun newerNotYet(version: String): String

    // Rescue via USB cable
    val usbRescue: String
    val usbRescueHint: String
    val usbRescueIntro: String
    val usbStuckIntro: String
    val usbSearch: String
    val usbSearching: String
    val usbNotFound: String

    /** The phone sees nothing on the USB port at all. */
    val usbNoDevice: String

    /** What usually causes [usbNoDevice]: charging-only cable, OTG off. */
    val usbNoDeviceHint: String
    val usbTitle: String
    val usbChooseUse: String
    val usbSetupAlready: String
    val usbUseNotPossible: String
    val usbSwitchToBluetooth: String
    val usbSwitchToWifi: String
    val usbPickInstall: String

    /** Main button of the choice of radio (ADR 0095 §3). */
    val usbInstallMeshtastic: String

    /** Small link under the recommended radios to the full list. */
    val usbOtherRadio: String

    /** The app recognised the radio on the cable and preselected it. */
    val usbRecognisedHint: String
    val usbPickInstallHint: String

    /** Reason on a recommended radio that does not match the chip on the cable (greyed out). */
    val usbRadioUnfit: String

    /** iOS: no USB, the setup with the cable runs in the webapp on a computer (ADR 0095 §5). */
    val usbWebTitle: String
    val usbWebHint: String
    val usbWebOpen: String
    val usbInstallDownloading: String
    val usbInstalling: String
    val usbInstallKeepPlugged: String
    val usbInstallFailed: String
    val usbReinstallEsp: String
    val usbChangeMode: String
    val usbModeTitle: String
    val usbModeMobile: String
    val usbModeMobileHint: String
    val usbModeStationary: String
    val usbModeStationaryHint: String
    val usbModePowerNote: String
    val usbWifiText: String
    val usbWifiName: String
    val usbWifiPassword: String
    val usbWifiConnect: String
    val usbWifiFailed: String
    val usbWifiAgain: String
    val usbPairingWifi: String
    val usbPairingUsb: String
    val usbNotSeenWifi: String
    val usbNotSeenUsb: String
    fun usbPairedWifi(name: String): String

    /** Wi-Fi name and password are on the radio; it restarts and joins [ssid] (FUT 14.11.2026). */
    fun usbWifiJoining(ssid: String): String

    /** The radio took name and password but did not get into the Wi-Fi. */
    val usbWifiNotJoined: String

    /** Joined and connected: the confirmation the user test missed. */
    fun usbPairedWifiIn(ssid: String): String
    fun usbPairedUsb(name: String): String
    val usbChooseText: String
    val usbChooseSetup: String
    val usbChooseSetupAgain: String
    val usbChooseRescue: String
    val usbChooseRescueHint: String
    val usbPairing: String
    val usbPairingHint: String
    fun usbPaired(name: String): String
    fun usbPairedConnected(name: String): String
    val usbNotSeen: String
    val usbTryAgain: String
    val usbConnectRow: String
    val usbConnectRowHint: String
    val usbNoSoftware: String

    /** ESP32 whose loader did not answer: start it by hand with the BOOT button. */
    val usbHoldBoot: String

    /** Plugged in, but no Meshtastic answers yet (new or empty radio). */
    val usbNoMeshtasticYet: String

    /** nRF52/RP2040: no drive appeared, start the bootloader by hand. */
    val usbDoubleTapReset: String
    val usbOpenFlasher: String
    val usbBluetoothOff: String
    val usbBluetoothOn: String
    val usbEnableBluetooth: String
    val usbEnablingBluetooth: String
    val usbBluetoothDone: String
    val usbBluetoothFailed: String
    fun usbFound(name: String, version: String?): String
    val usbFactoryReset: String
    val usbFactoryResetTitle: String
    val usbFactoryResetText: String
    val usbResetting: String
    val usbResetDone: String
    fun usbResetDoneNamed(name: String): String
    val usbResetFailed: String
    val usbReinstall: String
    val usbEnteringBootloader: String
    val usbBootloaderFailed: String
    val usbStuck: String
    val usbPickDrive: String
    val usbChooseDrive: String
    val usbWriting: String
    val usbWriteDone: String
    val usbWriteFailed: String
    val usbDownloadFailed: String
    val usbUpdateBootloader: String
    val usbUpdateBootloaderHint: String
    val usbUpdatingBootloader: String
    val usbBootloaderUpdated: String
    val usbBootloaderUpdateFailed: String
}

internal object RadioSoftwareTextsDe : RadioSoftwareTexts {
    override val title = "Neue Software für dein Funkgerät"
    override val noticeText = "Sie macht dein Funkgerät besser und sicherer. Das dauert ein paar Minuten, dabei muss $APP offen bleiben."
    override val updateNow = "Jetzt aktualisieren"
    override val ready = "Tippe, um zu aktualisieren"
    override val loading = "Wird geladen …"
    override val notConnected = "Verbinde zuerst dein Funkgerät mit diesem Handy."
    override val radioBattery = "Lade dein Funkgerät auf oder steck es ans Ladekabel."
    override val phoneBattery = "Lade zuerst dein Handy auf."
    override val messagesWaiting = "Warte, bis deine Nachrichten per Funk verschickt sind."
    override val notNowTitle = "Gerade nicht"
    override val keepOpen = "Dein Funkgerät wird aktualisiert. Lass $APP offen."
    override val preparing = "Wird vorbereitet …"
    override fun progress(percent: Int) = "$percent %"
    override val checking = "Funkgerät startet neu …"
    override val leaveTitle = "Aktualisierung abbrechen?"
    override val leaveText = "Noch ist nichts verändert. Du kannst es später noch einmal versuchen."
    override val leaveWriting = "Dein Funkgerät wird gerade beschrieben. Lass $APP offen, bis es fertig ist."
    override val stay = "Weiter warten"
    override val doneTitle = "Fertig"
    override val doneText = "Dein Funkgerät hat die neue Software."
    override val doneSetUpAgain = "Dein Funkgerät hat die neue Software. Richte es jetzt einmal neu ein."
    override val failedTitle = "Hat nicht geklappt"
    override val failedText = "Dein Funkgerät ist unverändert. Versuch es später noch einmal."
    override val failedWrote = "Lass dein Funkgerät eingeschaltet und versuch es gleich noch einmal."
    override val usbText = "Schließ dein Funkgerät mit einem USB-Kabel an einen Computer an. Öffne dort flasher.meshtastic.org und folge den Schritten."
    override fun advancedVersion(version: String) = "Firmware $version"
    override val advancedUnknown = "Firmware unbekannt"
    override val checkNow = "Nach neuer Software sehen"
    override val checkRunning = "Sehe nach …"
    override val checkFailed = "Nachsehen ging nicht. Versuch es später noch einmal."
    override val upToDate = "Aktuell, es gibt nichts Neueres."
    override fun newerAvailable(version: String) = "Neue Software $version verfügbar."
    override fun newerNotYet(version: String) = "Neue Software $version verfügbar. $APP kann sie noch nicht selbst aufspielen."

    override val usbRescue = "Rettung per USB-Kabel"
    override val usbRescueHint = "Wenn sich dein Funkgerät nicht mehr verbinden lässt"
    override val usbRescueIntro = "Verbinde dein Funkgerät mit einem USB-Kabel mit diesem Handy, dann tippe auf „Suchen“."
    override val usbStuckIntro = "Leuchtet dein Funkgerät nur noch dauerhaft und reagiert nicht? Dann tippe auf „Funkgerät hängt“."
    override val usbSearch = "Suchen"
    override val usbSearching = "Suche das Funkgerät …"
    override val usbNotFound = "Kein Funkgerät am Kabel gefunden. Steck es noch einmal ein und erlaube den Zugriff."
    override val usbNoDevice = "Dein Handy erkennt am USB-Kabel kein Gerät. Meist liegt es am Kabel."
    override val usbNoDeviceHint =
        "Viele Kabel können nur laden. Nimm eins, mit dem du auch Fotos auf einen Computer überträgst. Leuchtet das Funkgerät nicht, bekommt es keinen Strom. Bei manchen Handys musst du in den Einstellungen außerdem „OTG“ einschalten. Steck es dann neu ein und tippe auf „Suchen“."
    override val usbTitle = "Funkgerät am USB-Kabel"
    override val usbChooseUse = "Funkgerät per USB-Kabel benutzen"
    override val usbSetupAlready = "Es ist schon für $APP eingerichtet. Du wählst nur neu, wie du es nutzt."
    override val usbUseNotPossible = "Geht erst, wenn es für $APP eingerichtet ist."
    override val usbSwitchToBluetooth = "Auf Bluetooth umstellen"
    override val usbSwitchToWifi = "Auf WLAN umstellen"
    override val usbPickInstall = "Welches Funkgerät ist das? Die App spielt dann die passende Meshtastic-Software auf."
    override val usbInstallMeshtastic = "Meshtastic aufspielen"
    override val usbOtherRadio = "Anderes Meshtastic-fähiges Funkgerät"
    override val usbRecognisedHint = "Am Kabel erkannt. Steht auf deinem Gerät etwas anderes, wähl es aus."
    override val usbRadioUnfit = "Passt nicht zum eingesteckten Gerät"
    override val usbWebTitle = "Per USB-Kabel am Computer einrichten"
    override val usbWebHint = "Am iPhone geht das nicht per Kabel. Öffne am Computer die Webapp in Chrome oder Edge und steck das Funkgerät dort an."
    override val usbWebOpen = "Webapp öffnen"
    override val usbPickInstallHint = "Das Modell steht meist auf dem Gerät oder der Verpackung. Ein falsch gewähltes lässt sich durch erneutes Aufspielen beheben."
    override val usbInstallDownloading = "Software wird geladen …"
    override val usbInstalling = "Meshtastic wird aufgespielt …"
    override val usbInstallKeepPlugged = "Zieh das Kabel nicht ab, bis es fertig ist. Das dauert etwa eine Minute."
    override val usbInstallFailed = "Das Aufspielen hat nicht geklappt. Steck das Funkgerät noch einmal ein und versuch es erneut. Kaputtgehen kann dabei nichts."
    override val usbReinstallEsp = "Meshtastic neu aufspielen"
    override val usbChangeMode = "Unterwegs oder stationär umstellen"
    override val usbModeTitle = "Wie nutzt du das Funkgerät?"
    override val usbModeMobile = "Unterwegs"
    override val usbModeMobileHint = "Es verbindet sich per Bluetooth mit deinem Handy. Gut, wenn du es unterwegs dabeihaben möchtest. Wichtig: Bluetooth reicht nur etwa 10 Meter, lass das Funkgerät in der Nähe deines Handys."
    override val usbModeStationary = "Stationär im WLAN"
    override val usbModeStationaryHint = "Es bleibt zu Hause am Strom und hängt im WLAN."
    override val usbModePowerNote = "Wichtig: Im WLAN hängt das Funkgerät am Router. Fällt der Strom aus, ist auch das WLAN weg und du kannst es nicht nutzen. Dann steck es per USB-Kabel ans Handy: Du kannst es direkt so benutzen oder unter „Verbindungen und Funkgerät“ mit einem Tipp auf Bluetooth umstellen."
    override val usbWifiText = "Mit welchem WLAN soll sich das Funkgerät verbinden? Dein Handy muss im selben WLAN sein. Das Passwort geht nur aufs Funkgerät, $APP speichert es nicht."
    override val usbWifiName = "WLAN-Name"
    override val usbWifiPassword = "Passwort"
    override val usbWifiConnect = "Verbinden"
    override val usbWifiFailed = "Das hat nicht geklappt. Versuch es noch einmal."
    override val usbWifiAgain = "WLAN neu eingeben"
    override val usbPairingWifi = "$APP verbindet sich mit deinem Funkgerät im WLAN …"
    override val usbPairingUsb = "Wird über das Kabel verbunden …"
    override val usbNotSeenWifi = "Das Funkgerät meldet sich nicht im WLAN. Stimmen Name und Passwort, und ist dein Handy im selben WLAN?"
    override val usbNotSeenUsb = "Über das Kabel klappt die Verbindung nicht. Steck es noch einmal ein und versuch es erneut."
    override fun usbPairedWifi(name: String) = "„$name“ ist über das WLAN verbunden. Du kannst das Kabel abziehen."
    override fun usbWifiJoining(ssid: String) = "Dein Funkgerät startet neu und verbindet sich mit „$ssid“. Das dauert bis zu einer Minute. Lass das Kabel bis dahin stecken."
    override val usbWifiNotJoined = "Dein Funkgerät kommt nicht ins WLAN. Prüf den WLAN-Namen und das Passwort (auf Groß- und Kleinschreibung achten) und ob das Funkgerät nah genug am Router steht."
    override fun usbPairedWifiIn(ssid: String) = "Dein Funkgerät ist jetzt im WLAN „$ssid“ und mit $APP verbunden. Du kannst das Kabel abziehen."
    override fun usbPairedUsb(name: String) = "„$name“ ist per USB-Kabel verbunden. Lass das Kabel stecken, solange du es so nutzt."
    override val usbChooseText = "Was möchtest du mit dem Funkgerät machen?"
    override val usbChooseSetup = "Neues Funkgerät einrichten"
    override val usbChooseSetupAgain = "Funkgerät erneut einrichten"
    override val usbChooseRescue = "Funkgerät retten"
    override val usbChooseRescueHint = "Retten hilft, wenn dein Funkgerät nicht mehr richtig läuft oder sich nicht mehr koppeln lässt."
    override val usbPairing = "Dein Funkgerät wird über Bluetooth gesucht und gekoppelt …"
    override val usbPairingHint = "Gleich wirst du nach einer Zahl gefragt. Sie steht auf dem Display des Funkgeräts, ohne Display ist es 123456."
    override fun usbPaired(name: String) = "„$name“ wird gekoppelt. Gib die Zahl vom Display ein, wenn du danach gefragt wirst. Danach kannst du das Kabel abziehen."
    override fun usbPairedConnected(name: String) = "„$name“ ist verbunden. Du kannst das Kabel abziehen."
    override val usbNotSeen =
        "Das Funkgerät taucht über Bluetooth nicht auf. Ist es gerade mit einem anderen Handy oder der Meshtastic-App verbunden? Trenne es dort und versuch es noch einmal."
    override val usbTryAgain = "Nochmal versuchen"
    override val usbConnectRow = "Per USB-Kabel verbinden"
    override val usbConnectRowHint = "Steck das Funkgerät ans Handy, die App sieht dann nach, was ihm fehlt."
    override val usbNoSoftware =
        "Auf dem Funkgerät fehlt noch die Meshtastic-Software. Spiel sie einmal am Computer im Browser Chrome auf und steck es dann wieder ein."
    override val usbHoldBoot =
        "Das Funkgerät reagiert noch nicht. Zieh das Kabel ab, halte die Taste BOOT gedrückt und steck es wieder ein. Lass die Taste dann los und tippe auf „Suchen“."
    override val usbNoMeshtasticYet = "Auf dem Funkgerät läuft noch keine Meshtastic-Software. Tippe auf „Neues Funkgerät einrichten“, $APP spielt sie auf."
    override val usbDoubleTapReset = "Siehst du kein Laufwerk? Drück die Reset-Taste am Funkgerät zweimal schnell hintereinander, dann erscheint es."
    override val usbOpenFlasher = "Anleitung öffnen"
    override val usbBluetoothOff = "Bluetooth ist auf dem Funkgerät aus, weil es WLAN nutzt. Deshalb findet die Suche es nicht."
    override val usbBluetoothOn = "Bluetooth ist an. Findet die Suche es trotzdem nicht, ist es wahrscheinlich gerade mit einem anderen Handy verbunden."
    override val usbEnableBluetooth = "Bluetooth einschalten"
    override val usbEnablingBluetooth = "Bluetooth wird eingeschaltet …"
    override val usbBluetoothDone = "Bluetooth ist an. Das Funkgerät startet neu und erscheint gleich unter „Neues Funkgerät koppeln“. Du kannst das Kabel abziehen."
    override val usbBluetoothFailed = "Bluetooth ließ sich nicht einschalten. Versuch es noch einmal oder setz das Funkgerät zurück."
    override fun usbFound(name: String, version: String?) = if (version != null) "Gefunden: $name, Software $version." else "Gefunden: $name."
    override val usbFactoryReset = "Auf Werkseinstellungen zurücksetzen"
    override val usbFactoryResetTitle = "Zurücksetzen?"
    override val usbFactoryResetText = "Das löscht alles auf dem Funkgerät, auch Kopplungen, Name und Funkeinstellungen. $APP richtet es danach neu ein."
    override val usbResetting = "Setze zurück …"
    override val usbResetDone = "Zurückgesetzt. Dein Funkgerät startet jetzt neu, das dauert einen Moment. Entferne es danach in den Bluetooth-Einstellungen und verbinde es neu."
    override fun usbResetDoneNamed(name: String) = "Zurückgesetzt. Dein Funkgerät startet jetzt neu, das dauert einen Moment. Es heißt danach „$name“. Entferne es in den Bluetooth-Einstellungen und verbinde es neu."
    override val usbResetFailed = "Zurücksetzen hat nicht geklappt. Steck das Kabel noch einmal ein und versuch es erneut."
    override val usbReinstall = "Software neu aufspielen"
    override val usbEnteringBootloader = "Funkgerät startet neu …"
    override val usbBootloaderFailed = "Das Funkgerät ließ sich nicht umschalten. Versuch es noch einmal."
    override val usbStuck = "Funkgerät hängt"
    override val usbPickDrive = "Fragt Android nach einer App für das USB-Gerät, wähle $APP. Tippe dann auf „Laufwerk wählen“, wähle das Laufwerk deines Funkgeräts und tippe auf „Diesen Ordner verwenden“."
    override val usbChooseDrive = "Laufwerk wählen"
    override val usbWriting = "Spiele die Software auf …"
    override val usbWriteDone = "Fertig. Das Funkgerät startet jetzt neu und lässt sich danach wieder verbinden."
    override val usbWriteFailed = "Das hat nicht geklappt. Wähle das Laufwerk deines Funkgeräts (mit der Datei INFO_UF2.TXT)."
    override val usbDownloadFailed = "Die Software ließ sich nicht laden. Prüfe die Internetverbindung."
    override val usbUpdateBootloader = "Startprogramm erneuern"
    override val usbUpdateBootloaderHint = "Hilft, wenn die Aktualisierung über Bluetooth nicht klappt. Ist es schon aktuell, spielt $APP die Software neu auf. Deine Einstellungen bleiben."
    override val usbUpdatingBootloader = "Erneuere das Startprogramm …"
    override val usbBootloaderUpdated = "Fertig. Das Funkgerät startet jetzt neu. Danach kannst du die Aktualisierung über Bluetooth noch einmal versuchen."
    override val usbBootloaderUpdateFailed = "Das Startprogramm ließ sich nicht erneuern. Tippe auf „Suchen“ und versuch es noch einmal."
}

internal object RadioSoftwareTextsEn : RadioSoftwareTexts {
    override val title = "New software for your radio"
    override val noticeText = "It makes your radio better and safer. This takes a few minutes, and $APP has to stay open."
    override val updateNow = "Update now"
    override val ready = "Tap to update"
    override val loading = "Downloading …"
    override val notConnected = "Connect your radio to this phone first."
    override val radioBattery = "Charge your radio or plug it in."
    override val phoneBattery = "Charge your phone first."
    override val messagesWaiting = "Wait until your messages have gone out by radio."
    override val notNowTitle = "Not right now"
    override val keepOpen = "Your radio is being updated. Keep $APP open."
    override val preparing = "Getting ready …"
    override fun progress(percent: Int) = "$percent%"
    override val checking = "Radio is restarting …"
    override val leaveTitle = "Cancel the update?"
    override val leaveText = "Nothing has changed yet. You can try again later."
    override val leaveWriting = "Your radio is being written right now. Keep $APP open until it is done."
    override val stay = "Keep waiting"
    override val doneTitle = "Done"
    override val doneText = "Your radio has the new software."
    override val doneSetUpAgain = "Your radio has the new software. Please set it up once more now."
    override val failedTitle = "That didn't work"
    override val failedText = "Your radio is unchanged. Try again later."
    override val failedWrote = "Keep your radio switched on and try again right away."
    override val usbText = "Connect your radio to a computer with a USB cable. Open flasher.meshtastic.org there and follow the steps."
    override fun advancedVersion(version: String) = "Firmware $version"
    override val advancedUnknown = "Firmware unknown"
    override val checkNow = "Check for new software"
    override val checkRunning = "Checking …"
    override val checkFailed = "Couldn't check. Try again later."
    override val upToDate = "Up to date, there is nothing newer."
    override fun newerAvailable(version: String) = "New software $version available."
    override fun newerNotYet(version: String) = "New software $version available. $APP can't install it by itself yet."

    override val usbRescue = "Rescue via USB cable"
    override val usbRescueHint = "If your radio won't connect anymore"
    override val usbRescueIntro = "Connect your radio to this phone with a USB cable, then tap “Search”."
    override val usbStuckIntro = "Is your radio's light on all the time and it doesn't respond? Then tap “Radio is stuck”."
    override val usbSearch = "Search"
    override val usbSearching = "Looking for the radio …"
    override val usbNotFound = "No radio found on the cable. Plug it in again and allow access."
    override val usbNoDevice = "Your phone doesn't see any device on the USB cable. Usually it's the cable."
    override val usbNoDeviceHint =
        "Many cables can only charge. Use one you'd also use to copy photos to a computer. If the radio doesn't light up, it gets no power. On some phones you also need to turn on “OTG” in the settings. Then plug it in again and tap “Search”."
    override val usbTitle = "Radio on the USB cable"
    override val usbChooseUse = "Use the radio with a USB cable"
    override val usbSetupAlready = "It's already set up for $APP. You only choose again how you use it."
    override val usbUseNotPossible = "Only possible once it's set up for $APP."
    override val usbSwitchToBluetooth = "Switch to Bluetooth"
    override val usbSwitchToWifi = "Switch to Wi-Fi"
    override val usbPickInstall = "Which radio is this? The app then installs the matching Meshtastic software."
    override val usbInstallMeshtastic = "Install Meshtastic"
    override val usbOtherRadio = "Other Meshtastic-compatible radio"
    override val usbRecognisedHint = "Recognized on the cable. If your device says something else, pick that one."
    override val usbRadioUnfit = "Doesn't match the plugged-in device"
    override val usbWebTitle = "Set up with a USB cable on a computer"
    override val usbWebHint = "An iPhone can't do this over a cable. On a computer, open the web app in Chrome or Edge and plug the radio in there."
    override val usbWebOpen = "Open web app"
    override val usbPickInstallHint = "The model is usually printed on the device or the box. If you pick the wrong one, just install again."
    override val usbInstallDownloading = "Loading the software …"
    override val usbInstalling = "Installing Meshtastic …"
    override val usbInstallKeepPlugged = "Don't unplug the cable until it's done. This takes about a minute."
    override val usbInstallFailed = "Installing didn't work. Plug the radio in again and try once more. Nothing can break."
    override val usbReinstallEsp = "Install Meshtastic again"
    override val usbChangeMode = "Switch between on the move and stationary"
    override val usbModeTitle = "How will you use the radio?"
    override val usbModeMobile = "On the move"
    override val usbModeMobileHint = "It connects to your phone over Bluetooth. Good if you want to take it with you. Important: Bluetooth only reaches about 10 meters (30 feet), so keep the radio near your phone."
    override val usbModeStationary = "Stationary on Wi-Fi"
    override val usbModeStationaryHint = "It stays at home, plugged in, on your Wi-Fi."
    override val usbModePowerNote = "Important: on Wi-Fi the radio depends on your router. If the power goes out, the Wi-Fi is gone too and you can't use it. Then plug it into your phone with a USB cable: you can use it that way right away, or switch it to Bluetooth with one tap under “Connections and radio”."
    override val usbWifiText = "Which Wi-Fi should the radio join? Your phone needs to be on the same Wi-Fi. The password only goes to the radio; $APP doesn't store it."
    override val usbWifiName = "Wi-Fi name"
    override val usbWifiPassword = "Password"
    override val usbWifiConnect = "Connect"
    override val usbWifiFailed = "That didn't work. Try again."
    override val usbWifiAgain = "Enter Wi-Fi again"
    override val usbPairingWifi = "$APP is connecting to your radio on your Wi-Fi …"
    override val usbPairingUsb = "Connecting over the cable …"
    override val usbNotSeenWifi = "The radio doesn't show up on your Wi-Fi. Are the name and password right, and is your phone on the same Wi-Fi?"
    override val usbNotSeenUsb = "Connecting over the cable didn't work. Plug it in again and try once more."
    override fun usbPairedWifi(name: String) = "“$name” is connected over Wi-Fi. You can unplug the cable."
    override fun usbWifiJoining(ssid: String) = "Your radio is restarting and joining “$ssid”. This takes up to a minute. Keep the cable plugged in until then."
    override val usbWifiNotJoined = "Your radio can't get onto the Wi-Fi. Check the Wi-Fi name and password (they're case-sensitive) and whether the radio is close enough to the router."
    override fun usbPairedWifiIn(ssid: String) = "Your radio is now on the Wi-Fi “$ssid” and connected to $APP. You can unplug the cable."
    override fun usbPairedUsb(name: String) = "“$name” is connected with the USB cable. Keep it plugged in while you use it this way."
    override val usbChooseText = "What would you like to do with the radio?"
    override val usbChooseSetup = "Set up a new radio"
    override val usbChooseSetupAgain = "Set up the radio again"
    override val usbChooseRescue = "Rescue the radio"
    override val usbChooseRescueHint = "Rescuing helps if your radio no longer works properly or won't pair anymore."
    override val usbPairing = "Looking for your radio over Bluetooth and pairing it …"
    override val usbPairingHint = "You'll be asked for a number in a moment. It's shown on the radio's display; without a display it's 123456."
    override fun usbPaired(name: String) = "Pairing “$name”. Enter the number from the display when you're asked for it. Then you can unplug the cable."
    override fun usbPairedConnected(name: String) = "“$name” is connected. You can unplug the cable."
    override val usbNotSeen =
        "The radio doesn't show up over Bluetooth. Is it connected to another phone or the Meshtastic app right now? Disconnect it there and try again."
    override val usbTryAgain = "Try again"
    override val usbConnectRow = "Connect with a USB cable"
    override val usbConnectRowHint = "Plug the radio into your phone and the app checks what it's missing."
    override val usbNoSoftware =
        "The radio doesn't have the Meshtastic software yet. Install it once on a computer in the Chrome browser, then plug it in again."
    override val usbHoldBoot =
        "The radio isn't responding yet. Unplug the cable, hold the BOOT button and plug it back in. Then let go of the button and tap “Search”."
    override val usbNoMeshtasticYet = "The radio isn't running the Meshtastic software yet. Tap “Set up a new radio” and $APP will install it."
    override val usbDoubleTapReset = "Don't see a drive? Press the reset button on the radio twice quickly, then it appears."
    override val usbOpenFlasher = "Open instructions"
    override val usbBluetoothOff = "Bluetooth is off on the radio because it uses Wi-Fi. That's why the search can't find it."
    override val usbBluetoothOn = "Bluetooth is on. If the search still can't find it, it's probably connected to another phone right now."
    override val usbEnableBluetooth = "Turn on Bluetooth"
    override val usbEnablingBluetooth = "Turning on Bluetooth …"
    override val usbBluetoothDone = "Bluetooth is on. The radio restarts and will show up under “Pair a new radio” in a moment. You can unplug the cable."
    override val usbBluetoothFailed = "Bluetooth couldn't be turned on. Try again or reset the radio."
    override fun usbFound(name: String, version: String?) = if (version != null) "Found: $name, software $version." else "Found: $name."
    override val usbFactoryReset = "Reset to factory settings"
    override val usbFactoryResetTitle = "Reset?"
    override val usbFactoryResetText = "This erases everything on the radio, including pairings, name and radio settings. $APP sets it up again afterwards."
    override val usbResetting = "Resetting …"
    override val usbResetDone = "Reset. Your radio restarts now, which takes a moment. Then remove it in the Bluetooth settings and connect it again."
    override fun usbResetDoneNamed(name: String) = "Reset. Your radio restarts now, which takes a moment. It is then called “$name”. Remove it in the Bluetooth settings and connect it again."
    override val usbResetFailed = "Reset didn't work. Plug the cable in again and try once more."
    override val usbReinstall = "Install software again"
    override val usbEnteringBootloader = "Radio is restarting …"
    override val usbBootloaderFailed = "The radio couldn't be switched. Try again."
    override val usbStuck = "Radio is stuck"
    override val usbPickDrive = "If Android asks which app to use for the USB device, choose $APP. Then tap “Choose drive”, pick your radio's drive and tap “Use this folder”."
    override val usbChooseDrive = "Choose drive"
    override val usbWriting = "Installing the software …"
    override val usbWriteDone = "Done. The radio restarts now and can be connected again afterwards."
    override val usbWriteFailed = "That didn't work. Choose your radio's drive (with the file INFO_UF2.TXT)."
    override val usbUpdateBootloader = "Renew start program"
    override val usbUpdateBootloaderHint = "Helps if updating over Bluetooth doesn't work. If it's already up to date, $APP installs the software again. Your settings stay."
    override val usbUpdatingBootloader = "Renewing the start program …"
    override val usbBootloaderUpdated = "Done. The radio restarts now. Then try the update over Bluetooth again."
    override val usbBootloaderUpdateFailed = "The start program couldn't be renewed. Tap “Search” and try again."
    override val usbDownloadFailed = "The software couldn't be downloaded. Check the internet connection."
}
