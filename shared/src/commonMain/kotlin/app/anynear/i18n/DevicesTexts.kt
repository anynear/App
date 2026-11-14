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
    val addPaste: String
    val addPasteHint: String
    val compareTitle: String
    val compareText: String
    val compareYes: String
    val compareNo: String
    val compareMismatch: String
    val pairingCodeText: String
    val previousTitle: String
    fun previousText(name: String): String
    val previousKept: String
    val previousLost: String
    val previousStolen: String
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
    fun lineNewDevice(person: String, device: String): String
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

    val returning: String
    val returningTitle: String
    val returningOtherDevice: String
    val returningOtherDeviceText: String
    val returningCard: String
    val returningCardText: String
    val returningBackup: String
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
}

internal object DevicesTextsDe : DevicesTexts {
    override val title = "Meine Geräte"
    override val entrySubtitle = "Handy und Tablet"
    override val footer = "Auf allen deinen Geräten bist du dieselbe Person. Kreise, Kontakte und Nachrichten sind überall da."
    override val thisDevice = "Dieses Gerät"
    override val unnamed = "Gerät ohne Namen"
    override fun added(date: String) = "Dabei seit $date"
    override val add = "Gerät hinzufügen"
    override val addText = "Öffne $APP auf dem neuen Gerät und tippe auf „Ich hatte $APP schon“. Scanne dann den Code dort mit der Kamera, oder füge den Link hier ein."
    override val addPaste = "Link vom neuen Gerät"
    override val addPasteHint = "Nur, wenn das neue Gerät vor dir liegt. Schickt dir jemand einen solchen Link, füge ihn nicht ein."
    override val compareTitle = "Gleicher Code?"
    override val compareText = "Steht auf dem neuen Gerät auch diese Zahl?"
    override val compareYes = "Ja, gleich"
    override val compareNo = "Nein"
    override val compareMismatch = "Nicht hinzugefügt. Füge nur ein Gerät hinzu, das vor dir liegt und dieselbe Zahl zeigt."
    override val pairingCodeText = "Dein anderes Gerät zeigt gleich diese Zahl. Sie muss gleich sein."
    override val previousTitle = "Hast du dein altes Handy noch?"
    override fun previousText(name: String) = "Die Sicherung kam von „$name“. Es steht noch in „Meine Geräte“."
    override val previousKept = "Ja, habe ich noch"
    override val previousLost = "Nein, verloren oder kaputt"
    override val previousStolen = "Nein, gestohlen"
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
    override fun lineNewDevice(person: String, device: String) = if (device.isBlank()) "$person nutzt $APP jetzt auch auf einem weiteren Gerät" else "$person nutzt $APP jetzt auch auf „$device“"
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
    override val cardShowText = "Schreib diesen Code auf oder mach ein Foto und leg es an einen sicheren Ort. Wer ihn hat, kann dein $APP übernehmen."
    override val cardNext = "Aufgeschrieben"
    override val cardTypeText = "Tipp den Code zur Kontrolle einmal ab."
    override val cardCode = "Code der Notfallkarte"
    override val cardWrong = "Der Code stimmt nicht. Schau noch einmal genau hin."
    override val cardFailed = "Gerade nicht möglich. Prüfe die Internetverbindung."
    override val cardOldCode = "Code der bisherigen Notfallkarte"

    override val returning = "Ich hatte $APP schon"
    override val returningTitle = "Wie holst du $APP hierher?"
    override val returningOtherDevice = "Mein anderes Gerät ist da"
    override val returningOtherDeviceText = "Handy oder Tablet mit $APP in der Nähe"
    override val returningCard = "Mit der Notfallkarte"
    override val returningCardText = "Wenn das alte Handy weg ist"
    override val returningBackup = "Aus einer Sicherungsdatei"
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
    override val newDeviceName = "Name dieses Geräts, z. B. Tablet"
}

internal object DevicesTextsEn : DevicesTexts {
    override val title = "My devices"
    override val entrySubtitle = "Phone and tablet"
    override val footer = "You are the same person on all your devices. Circles, contacts and messages are everywhere."
    override val thisDevice = "This device"
    override val unnamed = "Unnamed device"
    override fun added(date: String) = "Added $date"
    override val add = "Add a device"
    override val addText = "Open $APP on the new device and tap “I used $APP before”. Then scan the code there with the camera, or paste the link here."
    override val addPaste = "Link from the new device"
    override val addPasteHint = "Only if the new device is in front of you. If someone sends you such a link, do not paste it."
    override val compareTitle = "Same code?"
    override val compareText = "Does the new device show this number too?"
    override val compareYes = "Yes, the same"
    override val compareNo = "No"
    override val compareMismatch = "Not added. Only add a device that is in front of you and shows the same number."
    override val pairingCodeText = "Your other device will show this number. It has to be the same."
    override val previousTitle = "Do you still have your old phone?"
    override fun previousText(name: String) = "The backup came from “$name”. It is still in “My devices”."
    override val previousKept = "Yes, I still have it"
    override val previousLost = "No, lost or broken"
    override val previousStolen = "No, stolen"
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
    override fun lineNewDevice(person: String, device: String) = if (device.isBlank()) "$person now also uses $APP on another device" else "$person now also uses $APP on “$device”"
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
    override val cardShowText = "Write this code down or take a photo and keep it somewhere safe. Whoever has it can take over your $APP."
    override val cardNext = "Written down"
    override val cardTypeText = "Type the code once to check it."
    override val cardCode = "Emergency card code"
    override val cardWrong = "The code does not match. Look closely once more."
    override val cardFailed = "Not possible right now. Check the internet connection."
    override val cardOldCode = "Code of the previous emergency card"

    override val returning = "I used $APP before"
    override val returningTitle = "How do you bring $APP here?"
    override val returningOtherDevice = "My other device is here"
    override val returningOtherDeviceText = "Phone or tablet with $APP nearby"
    override val returningCard = "With the emergency card"
    override val returningCardText = "If the old phone is gone"
    override val returningBackup = "From a backup file"
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
    override val newDeviceName = "Name of this device, e.g. Tablet"
}
