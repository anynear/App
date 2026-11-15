package app.anynear.i18n

/** Several devices of one person, emergency card and restoring (ADR 0042). */
interface DevicesTexts {
    val title: String
    val entrySubtitle: String
    val footer: String
    val thisDevice: String
    val unnamed: String
    fun added(date: String): String
    val add: String
    val addText: String
    val scanHint: String
    val scanHelp: String
    val compareTitle: String
    val compareText: String
    val compareYes: String
    val compareNo: String
    val compareMismatch: String
    val pairingCodeText: String
    val replaceTitle: String
    val replaceText: String
    val replaceNone: String
    val replaceRemoved: String
    val replaceWorking: String
    val replaceFailed: String
    val addConfirmTitle: String
    val addConfirmText: String
    val addReason: String
    val addNeedsLock: String
    val removeReason: String
    val lockOutReason: String
    val added: String
    val addRevoked: String
    val addFull: String
    val addInvalid: String
    val remove: String
    fun removeTitle(name: String): String
    val removeText: String
    val lockOut: String
    val lockOutText: String
    val lockOutTextNoCode: String
    val lockOutFailedNoCode: String
    val lockOutDone: String
    val lockOutFailed: String
    fun unknownTitle(name: String): String
    val unknownText: String
    val unknownYes: String
    val unknownNo: String
    fun passwordRequestTitle(name: String): String
    val passwordRequestText: String
    val passwordRequestYes: String
    val passwordRequestNo: String
    val passwordRequestReason: String
    val removedUnknownNotice: String
    val revokedTitle: String
    val revokedText: String
    val revokedDelete: String
    val removedHereNotice: String

    val cardTitle: String
    val cardFooter: String
    val cardCreate: String
    val cardRenew: String
    val cardDone: String
    val cardShowText: String
    val cardNext: String
    val cardTypeText: String
    val cardCode: String
    val cardWrong: String
    val cardFailed: String
    val cardOldCode: String
    val cardCopied: String
    val cardPrint: String
    val cardPrintTitle: String
    val cardPrintText: String
    fun cardPrintDate(date: String): String

    val returning: String
    val returningTitle: String
    val returningOtherDevice: String
    val returningOtherDeviceText: String
    val returningCard: String
    val returningCardText: String
    val pairingTitle: String
    val pairingText: String
    val pairingWaiting: String
    val pairingTimeout: String
    val recoverTitle: String
    val recoverText: String
    val recoverButton: String
    val recoverInvalid: String
    val recoverNotFound: String
    val recoverLater: String
    val newDeviceName: String
    val newDeviceNameHint: String
}

internal object DevicesTextsDe : DevicesTexts {
    override val title = "Meine Geräte"
    override val entrySubtitle = "Handy und Tablet"
    override val footer = "Auf allen deinen Geräten bist du dieselbe Person. Kreise, Kontakte und Nachrichten sind überall da."
    override val thisDevice = "Dieses Gerät"
    override val unnamed = "Gerät ohne Namen"
    override fun added(date: String) = "Dabei seit $date"
    override val add = "Gerät hinzufügen"
    override val addText = "Öffne $APP auf dem neuen Gerät und tippe auf „Ich habe $APP schon“. Scanne dann den Code dort mit der Kamera."
    override val scanHint = "Code auf dem neuen Gerät"
    override val scanHelp = "Halte die Kamera auf den QR-Code auf dem neuen Gerät."
    override val compareTitle = "Stimmt die Zahl überein?"
    override val compareText = "Steht auf dem neuen Gerät auch diese Zahl?"
    override val compareYes = "Ja, stimmt überein"
    override val compareNo = "Nein"
    override val compareMismatch = "Nicht hinzugefügt. Füge nur ein Gerät hinzu, das vor dir liegt und dieselbe Zahl zeigt."
    override val pairingCodeText = "Dein anderes Gerät zeigt gleich diese Zahl. Sie muss gleich sein."
    override val replaceTitle = "Ersetzt dieses Handy eins deiner Geräte?"
    override val replaceText = "Es übernimmt dann dessen Einstellungen, und das alte Gerät verschwindet aus „Meine Geräte“."
    override val replaceNone = "Nein, es ist zusätzlich"
    override val replaceRemoved = "nicht mehr in „Meine Geräte“"
    override val replaceWorking = "Einstellungen werden geholt …"
    override val replaceFailed = "Das hat nicht geklappt. Prüf die Internetverbindung und versuch es noch mal."
    override val addConfirmTitle = "Gerät hinzufügen?"
    override val addConfirmText = "Das neue Gerät bekommt deine Kreise, Kontakte und Nachrichten. Füge nur eigene Geräte hinzu."
    override val addReason = "Gerät hinzufügen"
    override val addNeedsLock = "Richte zuerst eine Gerätesperre ein (PIN, Muster oder Fingerabdruck)."
    override val removeReason = "Gerät entfernen"
    override val lockOutReason = "Gerät aussperren"
    override val added = "Gerät hinzugefügt. Es holt jetzt alles."
    override val addRevoked = "Dieses Gerät wurde entfernt und kann nicht wieder hinzugefügt werden."
    override val addFull = "Du hast schon 16 Geräte."
    override val addInvalid = "Dieser Code passt nicht. Zeig auf dem neuen Gerät den Code noch einmal."
    override val remove = "Entfernen"
    override fun removeTitle(name: String) = "„$name“ entfernen?"
    override val removeText = "Es bekommt danach keine Nachrichten mehr."
    override val lockOut = "Gestohlen? Für immer aussperren"
    override val lockOutText = "Gib den Code deiner Notfallkarte ein. Danach kann das Gerät nichts mehr lesen, auch wenn jemand es entsperrt."
    override val lockOutTextNoCode = "Danach kann das Gerät nichts mehr lesen, auch wenn jemand es entsperrt."
    override val lockOutFailedNoCode = "Das hat nicht geklappt. Prüfe die Verbindung."
    override val lockOutDone = "Gerät ausgesperrt."
    override val lockOutFailed = "Das hat nicht geklappt. Prüfe den Code und die Verbindung."
    override fun unknownTitle(name: String) = "Neues Gerät: „$name“"
    override val unknownText = "Warst du das?"
    override val unknownYes = "Ja"
    override val unknownNo = "Nein, entfernen"
    override fun passwordRequestTitle(name: String) = "Passwörter auf „$name“ öffnen?"
    override val passwordRequestText = "Bestätige nur, wenn du gerade selbst davor sitzt."
    override val passwordRequestYes = "Öffnen"
    override val passwordRequestNo = "Nein"
    override val passwordRequestReason = "Passwörter am Computer öffnen"
    override val removedUnknownNotice = "Gerät entfernt. Wenn jemand dein Handy entsperren kann: Unter „Meine Geräte“ mit der Notfallkarte für immer aussperren."
    override val revokedTitle = "$APP wurde auf diesem Gerät entfernt"
    override val revokedText = "Ein anderes Gerät von dir hat es entfernt. Alles hier wird gelöscht."
    override val revokedDelete = "Jetzt löschen"
    override val removedHereNotice = "Ein anderes Gerät von dir hat $APP hier entfernt. Alles hier wurde gelöscht."

    override val cardTitle = "Notfallkarte"
    override val cardFooter = "Damit holst du $APP auf ein neues Handy, auch wenn das alte weg ist."
    override val cardCreate = "Notfallkarte erstellen"
    override val cardRenew = "Neue Notfallkarte"
    override val cardDone = "Du hast eine Notfallkarte."
    override val cardShowText = "Schreib diesen Code auf, druck ihn aus oder mach ein Foto und leg es an einen sicheren Ort. Wer ihn hat, kann dein $APP übernehmen."
    override val cardNext = "Aufgeschrieben"
    override val cardTypeText = "Tipp den Code zur Kontrolle einmal ab."
    override val cardCode = "Code der Notfallkarte"
    override val cardWrong = "Der Code stimmt nicht. Schau noch einmal genau hin."
    override val cardFailed = "Gerade nicht möglich. Prüfe die Internetverbindung."
    override val cardOldCode = "Code der bisherigen Notfallkarte"
    override val cardCopied = "Kopiert"
    override val cardPrint = "Drucken"
    override val cardPrintTitle = "$APP Notfallkarte"
    override val cardPrintText = "Mit diesem Code holst du $APP auf ein neues Handy, auch wenn deine Geräte weg sind: Kreise, Kontakte, Nachrichten, Dateien und Passwörter. Bewahre die Karte sicher auf, etwa bei deinen Ausweisen. Wer den Code hat, kann dein $APP übernehmen."
    override fun cardPrintDate(date: String) = "Erstellt am $date"

    override val returning = "Ich habe $APP schon"
    override val returningTitle = "Wie holst du $APP hierher?"
    override val returningOtherDevice = "Als weiteres Gerät einrichten"
    override val returningOtherDeviceText = "Handy oder Tablet mit $APP in der Nähe"
    override val returningCard = "Mit der Notfallkarte"
    override val returningCardText = "Wenn kein Gerät mit deinen Daten mehr da ist"
    override val pairingTitle = "Dieses Gerät hinzufügen"
    override val pairingText = "Öffne $APP auf deinem anderen Gerät: „Mehr“ → „Meine Geräte“ → „Gerät hinzufügen“. Scanne dann diesen Code."
    override val pairingWaiting = "Warte auf dein anderes Gerät …"
    override val pairingTimeout = "Das hat zu lange gedauert. Versuch es noch einmal."
    override val recoverTitle = "Notfallkarte"
    override val recoverText = "Gib den Code deiner Notfallkarte ein."
    override val recoverButton = "$APP zurückholen"
    override val recoverInvalid = "Der Code stimmt nicht. Schau noch einmal genau hin."
    override val recoverNotFound = "Zu diesem Code gibt es nichts."
    override val recoverLater = "Gerade keine Verbindung. Versuch es gleich noch einmal."
    override val newDeviceName = "Name dieses Geräts"
    override val newDeviceNameHint = "z. B. Tablet"
}

internal object DevicesTextsEn : DevicesTexts {
    override val title = "My devices"
    override val entrySubtitle = "Phone and tablet"
    override val footer = "You are the same person on all your devices. Circles, contacts and messages are everywhere."
    override val thisDevice = "This device"
    override val unnamed = "Unnamed device"
    override fun added(date: String) = "Added $date"
    override val add = "Add a device"
    override val addText = "Open $APP on the new device and tap “I already use $APP”. Then scan the code there with the camera."
    override val scanHint = "Code on the new device"
    override val scanHelp = "Point the camera at the QR code on the new device."
    override val compareTitle = "Do the numbers match?"
    override val compareText = "Does the new device show this number too?"
    override val compareYes = "Yes, they match"
    override val compareNo = "No"
    override val compareMismatch = "Not added. Only add a device that is in front of you and shows the same number."
    override val pairingCodeText = "Your other device will show this number. It has to be the same."
    override val replaceTitle = "Does this phone replace one of your devices?"
    override val replaceText = "It then takes over that device's settings, and the old device leaves “My devices”."
    override val replaceNone = "No, it is an additional one"
    override val replaceRemoved = "no longer under “My devices”"
    override val replaceWorking = "Getting the settings …"
    override val replaceFailed = "That didn't work. Check the internet connection and try again."
    override val addConfirmTitle = "Add this device?"
    override val addConfirmText = "The new device gets your circles, contacts and messages. Only add your own devices."
    override val addReason = "Add a device"
    override val addNeedsLock = "Set up a screen lock first (PIN, pattern or fingerprint)."
    override val removeReason = "Remove a device"
    override val lockOutReason = "Lock out a device"
    override val added = "Device added. It is getting everything now."
    override val addRevoked = "This device was removed and cannot be added again."
    override val addFull = "You already have 16 devices."
    override val addInvalid = "This code does not fit. Show the code on the new device again."
    override val remove = "Remove"
    override fun removeTitle(name: String) = "Remove “$name”?"
    override val removeText = "It will not get messages anymore."
    override val lockOut = "Stolen? Lock it out for good"
    override val lockOutText = "Enter the code of your emergency card. Afterwards the device cannot read anything, even if someone unlocks it."
    override val lockOutTextNoCode = "Afterwards the device cannot read anything, even if someone unlocks it."
    override val lockOutFailedNoCode = "That did not work. Check the connection."
    override val lockOutDone = "Device locked out."
    override val lockOutFailed = "That did not work. Check the code and the connection."
    override fun unknownTitle(name: String) = "New device: “$name”"
    override val unknownText = "Was that you?"
    override val unknownYes = "Yes"
    override val unknownNo = "No, remove it"
    override fun passwordRequestTitle(name: String) = "Open passwords on “$name”?"
    override val passwordRequestText = "Only confirm if you are sitting at it right now."
    override val passwordRequestYes = "Open"
    override val passwordRequestNo = "No"
    override val passwordRequestReason = "Open passwords on the computer"
    override val removedUnknownNotice = "Device removed. If someone can unlock your phone: lock it out for good under “My devices” with your emergency card."
    override val revokedTitle = "$APP was removed from this device"
    override val revokedText = "Another device of yours removed it. Everything here will be deleted."
    override val revokedDelete = "Delete now"
    override val removedHereNotice = "Another device of yours removed $APP here. Everything here was deleted."

    override val cardTitle = "Emergency card"
    override val cardFooter = "Brings $APP to a new phone, even if the old one is gone."
    override val cardCreate = "Create emergency card"
    override val cardRenew = "New emergency card"
    override val cardDone = "You have an emergency card."
    override val cardShowText = "Write this code down, print it or take a photo and keep it somewhere safe. Whoever has it can take over your $APP."
    override val cardNext = "Written down"
    override val cardTypeText = "Type the code once to check it."
    override val cardCode = "Emergency card code"
    override val cardWrong = "The code does not match. Look closely once more."
    override val cardFailed = "Not possible right now. Check the internet connection."
    override val cardOldCode = "Code of the previous emergency card"
    override val cardCopied = "Copied"
    override val cardPrint = "Print"
    override val cardPrintTitle = "$APP emergency card"
    override val cardPrintText = "This code brings $APP to a new phone, even if your devices are gone: circles, contacts, messages, files and passwords. Keep the card somewhere safe, for example with your ID documents. Whoever has the code can take over your $APP."
    override fun cardPrintDate(date: String) = "Created on $date"

    override val returning = "I already use $APP"
    override val returningTitle = "How do you bring $APP here?"
    override val returningOtherDevice = "Set up as an additional device"
    override val returningOtherDeviceText = "Phone or tablet with $APP nearby"
    override val returningCard = "With the emergency card"
    override val returningCardText = "If no device with your data is left"
    override val pairingTitle = "Add this device"
    override val pairingText = "Open $APP on your other device: “More” → “My devices” → “Add a device”. Then scan this code."
    override val pairingWaiting = "Waiting for your other device …"
    override val pairingTimeout = "That took too long. Please try again."
    override val recoverTitle = "Emergency card"
    override val recoverText = "Enter the code of your emergency card."
    override val recoverButton = "Bring $APP back"
    override val recoverInvalid = "The code does not match. Look closely once more."
    override val recoverNotFound = "There is nothing for this code."
    override val recoverLater = "No connection right now. Try again in a moment."
    override val newDeviceName = "Name of this device"
    override val newDeviceNameHint = "e.g. Tablet"
}
