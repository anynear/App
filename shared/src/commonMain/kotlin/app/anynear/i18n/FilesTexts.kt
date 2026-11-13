package app.anynear.i18n

/** Storage: „Meine Ablage“ (ADR 0040, 0041) and the storages of the circles (ADR 0049). */
interface FilesTexts {
    val personal: String
    val justForMe: String
    val withYourCircles: String
    val noCirclesYet: String
    val files: String
    val addFile: String
    val circleFiles: String
    // Storage of a circle (ADR 0049)
    fun circleIntro(circle: String): String
    val circleOnlyHere: String
    val circleOnlineSavedText: String
    val circleNotAcceptedOnlineText: String
    fun circleLastChecked(time: String): String
    val circleDeleteText: String
    // „Meine Ablage“ (ADR 0040)
    fun fileCount(n: Int): String
    val noFilesYet: String
    val myFilesIntro: String
    val onlyHere: String
    val netStatus: String
    val netOnDevice: String
    val netInBin: String
    val onlineSaved: String
    val onlineSavedText: String
    val notYetOnline: String
    val notYetOnlineText: String
    /** Already at one relay, but not yet at two providers (ADR 0028 §7). */
    val securingText: String
    val netIncomplete: String
    val netIncompleteText: String
    val keepOnDevice: String
    val keepOnDeviceOn: String
    val keepOnDeviceOff: String
    val keepOnDeviceWait: String
    val keepOnDeviceLoading: String
    val keepOnDeviceFooter: String
    val onlineOnly: String
    val onlineOnlyNoInternet: String
    val tooLargeOnlineText: String
    val notAcceptedOnlineText: String

    /** Usage of the quota in the network (ADR 0029, 0079). */
    val storageTitle: String
    fun storageUsed(used: String, quota: String): String
    fun gigabytes(tenths: Long): String
    val storageNearlyFull: String
    val storageFull: String
    val storageAllStores: String
    val storageFullOnlineText: String
    val moreStorage: String

    /** Subscription preview (ADR 0076): package names, prices and building blocks come from the website. */
    fun aboIntro(trialMonths: Int): String
    /** Saving of the yearly price in percent. */
    fun aboSaving(percent: Int): String
    /** Yearly price converted to the month, instead of the monthly price. */
    fun aboYearlyPerMonth(perMonth: String, monthly: String): String
    /** What distinguishes the quota from simple online storage (above the packages). */
    val aboNetwork: String
    val aboPreviewNote: String
    val aboYearly: String
    val aboMonthly: String
    fun aboPerYear(price: String): String
    fun aboPerMonth(price: String): String
    fun aboOrMonthly(price: String): String
    fun aboOrYearly(price: String): String
    val aboFreeNow: String
    /** Column header of the free plan in the comparison. */
    val aboYourPlan: String
    /** Short form of „Empfohlen“ for the narrow column head. */
    val aboRecommendedShort: String
    /** Below the price in the comparison: per month / per year. */
    val aboMonthShort: String
    val aboYearShort: String
    /** Hint above the comparison: tap a plan to choose it. */
    val aboChooseHint: String
    val aboRecommended: String
    fun aboInterest(trialMonths: Int): String
    val aboInterestNote: String
    val aboOffline: String
    val aboLoading: String
    fun euro(cents: Long): String
    fun onlineWhere(date: String, mailboxes: Int): String
    val holdsThisFile: String
    fun lastChecked(time: String): String
    val addPhoto: String
    fun photoName(date: String): String
    fun addedOn(date: String): String
    val open: String
    val rename: String
    val renameTitle: String

    /** Title (ADR 0091): change it, empty gives it back to anynear. */
    val changeTitle: String
    val titleDialog: String
    val titleHint: String
    val save: String

    /** File name, changed separately from the title. */
    val renameFile: String

    /** Order of the list (ADR 0091 §7). */
    val sort: String
    val sortNewest: String
    val sortTitle: String
    val sortName: String
    val rotate: String
    val rotateHint: String
    val rotateFailed: String
    val sendToChat: String
    val saveToPhone: String
    val openWithOtherApp: String
    val cantShowHere: String
    val delete: String
    fun deleteQuestion(name: String): String
    val deleteText: String
    val moveToBin: String
    fun binQuestion(name: String): String
    fun binText(days: Int): String
    fun circleBinText(days: Int): String
    val bin: String
    fun binRow(count: Int): String
    fun binIntro(days: Int): String
    fun circleBinIntro(days: Int): String
    fun binGoneOn(date: String): String
    val restore: String
    val deleteForGood: String
    val emptyBin: String
    val emptyBinQuestion: String
    val emptyBinText: String
    val circleEmptyBinText: String
    val cantOpen: String
    val toMyFiles: String
    val addedToMyFiles: String
    fun chooseStore(count: Int): String
    fun addedTo(store: String): String
    val fileTooLarge: String
    val tooLargeForChat: String
    val sendHint: String
    // Recognise and search (ADR 0067)
    val search: String
    val allKinds: String
    fun kindName(kind: String): String
    val docReading: String
    val docChoose: String
    /** Recognised text on the page of a file (ADR 0078 §1). */
    val readText: String
    val readTextNone: String
    fun readTextWords(n: Int): String
    val readTextHint: String
    val docRecognizedHint: String
    val docChosenHint: String
    fun docUntil(date: String): String
    fun amount(cents: Long, currency: String): String
    // Gallery (ADR 0070)
    val documentsView: String
    val photosView: String
    val searchPhotos: String
    fun topicName(topic: String): String
    // Search across all storages (ADR 0070 §7)
    val searchAll: String
}

/** Amount with two decimal places and [decimal] as separator. */
private fun money(cents: Long, decimal: Char): String = "${cents / 100}$decimal${(cents % 100).toString().padStart(2, '0')}"

private fun currencySign(currency: String) = if (currency == "EUR") "€" else currency

internal object FilesTextsDe : FilesTexts {
    override val personal = "Persönlich"
    override val justForMe = "Nur für mich"
    override val withYourCircles = "Mit deinen Kreisen"
    override val noCirclesYet = "Sobald du in einem Kreis bist, erscheint hier seine Ablage."
    override val files = "Dateien"
    override val addFile = "Datei hinzufügen"
    override val circleFiles = "Ablage dieses Kreises"
    override fun circleIntro(circle: String) = "Die gemeinsame Ablage von „$circle“. Alle im Kreis sehen dieselben Dateien und können sie umbenennen und löschen."
    override val circleOnlyHere = "Die Dateien liegen verschlüsselt auf den Handys im Kreis und im $APP-Netz. Nur wer im Kreis ist, kann sie öffnen. Wer den Kreis verlässt, hat sie danach nicht mehr in der Ablage."
    override val circleOnlineSavedText = "Verschlüsselt bei mehreren Rechenzentren in Europa, ohne Ablaufdatum. Alle im Kreis können sie öffnen."
    override val circleNotAcceptedOnlineText = "Das $APP-Netz hat die Datei gerade nicht angenommen, vielleicht ist das Kontingent im Netz aufgebraucht. $APP versucht es von selbst wieder."
    override fun circleLastChecked(time: String) = "Zuletzt nachgesehen: $time. Alle im Kreis können die Datei öffnen."
    override val circleDeleteText = "Die Datei verschwindet für alle im Kreis aus der Ablage. Wer sie vorher gespeichert oder weitergeschickt hat, behält diese Kopie."
    override fun fileCount(n: Int) = if (n == 1) "1 Datei" else "$n Dateien"
    override val noFilesYet = "Noch leer"
    override val myFilesIntro = "Wichtige Fotos und Dateien, nur für dich. Sie sind auch ohne Internet da."
    override val onlyHere = "Deine Dateien liegen verschlüsselt auf diesem Handy und im $APP-Netz. Nur du kannst sie öffnen. Auf ein neues Handy kommen sie mit deiner Sicherung."
    override val netStatus = "Status im Netz"
    override val netOnDevice = "Nur auf diesem Handy"
    override val netInBin = "Im Papierkorb"
    override val onlineSaved = "Im Netz verteilt"
    override val onlineSavedText = "Verschlüsselt bei mehreren Rechenzentren in Europa, ohne Ablaufdatum. Nur du kannst sie öffnen."
    override val notYetOnline = "Wird verteilt"
    override val notYetOnlineText = "Das passiert von selbst, sobald Internet da ist."
    override val securingText = "Die Datei ist im Netz angekommen. Die Kopien bei weiteren Rechenzentren entstehen von selbst."
    override val netIncomplete = "Teilweise verteilt"
    override val netIncompleteText = "Gerade liegt die Datei nur bei einem Rechenzentrum. Das Netz ergänzt die Kopien von selbst."
    override val keepOnDevice = "Auf diesem Handy behalten"
    override val keepOnDeviceOn = "Die Datei ist auch ohne Internet da."
    override val keepOnDeviceOff = "Nur im Netz. Auf dem Handy liegt nur ein Vorschaubild, zum Öffnen braucht es Internet."
    override val keepOnDeviceWait = "Geht, sobald die Datei im Netz verteilt ist."
    override val keepOnDeviceLoading = "Wird aufs Handy geladen …"
    override val keepOnDeviceFooter = "Ausschalten schafft Platz auf dem Handy. Fallen alle Speicherorte im Netz aus, lässt sich die Datei dann nicht wiederherstellen."
    override val onlineOnly = "Nur im Netz"
    override val onlineOnlyNoInternet = "Diese Datei liegt nur im Netz. Zum Öffnen braucht es Internet."
    override val tooLargeOnlineText = "Die Datei ist größer, als das $APP-Netz gerade annimmt. Bis dahin liegt sie nur auf diesem Handy. $APP versucht es von selbst wieder."
    override val notAcceptedOnlineText = "Das $APP-Netz hat die Datei gerade nicht angenommen, vielleicht ist dein Kontingent im Netz aufgebraucht. $APP versucht es von selbst wieder."
    override val storageTitle = "Belegung im Netz"
    override fun storageUsed(used: String, quota: String) = "$used von $quota belegt"
    override fun gigabytes(tenths: Long) = if (tenths % 10 == 0L) "${tenths / 10} GB" else "${tenths / 10},${tenths % 10} GB"
    override val storageNearlyFull = "Dein Kontingent im Netz ist fast aufgebraucht."
    override val storageFull = "Dein Kontingent im Netz ist aufgebraucht. Neue Dateien kannst du erst wieder ablegen, wenn Platz ist. Öffnen geht weiter."
    override val storageAllStores = "Ein Kontingent für alle deine Ablagen. Was andere in einen Kreis legen, zählt bei ihnen."
    override val storageFullOnlineText = "Dein Kontingent im Netz ist aufgebraucht. Die Datei bleibt auf diesem Handy, bis wieder Platz ist."
    override val moreStorage = "Mehr Kontingent"
    override fun aboIntro(trialMonths: Int) =
        "Nachrichten, Funk und Warnungen bleiben kostenlos. Bald gibt es ein Abo für mehr. Bekunde jetzt Interesse und sichere dir $trialMonths Monate kostenlos."
    override val aboNetwork = "Nicht in einer Cloud, sondern im $APP-Netz: Jede Datei liegt verschlüsselt bei drei verschiedenen Rechenzentren in Europa. Fällt eins aus, ergänzt das Netz die Kopie von selbst."
    override val aboPreviewNote = "Voraussichtliche Preise inklusive Mehrwertsteuer. Noch nicht zu kaufen."
    override val aboYearly = "Jährlich"
    override fun aboSaving(percent: Int) = "Jährlich $percent % Ersparnis"
    override fun aboYearlyPerMonth(perMonth: String, monthly: String) = "entspricht $perMonth im Monat statt $monthly"
    override val aboMonthly = "Monatlich"
    override fun aboPerYear(price: String) = "$price im Jahr"
    override fun aboPerMonth(price: String) = "$price im Monat"
    override fun aboOrMonthly(price: String) = "oder $price im Monat"
    override fun aboOrYearly(price: String) = "oder $price im Jahr"
    override val aboFreeNow = "Hast du jetzt"
    override val aboYourPlan = "Dein Paket"
    override val aboRecommendedShort = "Empfohlen"
    override val aboMonthShort = "im Monat"
    override val aboYearShort = "im Jahr"
    override val aboChooseHint = "Tippe auf ein Paket, um es mit deinem zu vergleichen."
    override val aboRecommended = "Empfohlen"
    override fun aboInterest(trialMonths: Int) = "$trialMonths Monate kostenlos sichern"
    override val aboInterestNote = "Das Formular öffnet sich auf anynear.app. Wir schreiben dir, sobald das Abo startet."
    override val aboOffline = "Die Pakete lassen sich gerade nicht laden."
    override val aboLoading = "Pakete werden geladen …"
    override fun euro(cents: Long) = if (cents % 100 == 0L) "${cents / 100} €" else "${cents / 100},${(cents % 100).toString().padStart(2, '0')} €"
    override fun onlineWhere(date: String, mailboxes: Int) =
        "Seit $date verschlüsselt bei ${if (mailboxes == 1) "einem Rechenzentrum" else "$mailboxes Rechenzentren"} verschiedener Anbieter in Europa, ohne Ablaufdatum."
    override val holdsThisFile = "Hat diese Datei"
    override fun lastChecked(time: String) = "Zuletzt nachgesehen: $time. Nur du kannst die Datei öffnen."
    override val addPhoto = "Foto hinzufügen"
    override fun photoName(date: String) = "Foto vom $date"
    override fun addedOn(date: String) = "Hinzugefügt am $date"
    override val open = "Öffnen"
    override val rename = "Umbenennen"
    override val renameTitle = "Neuer Name"
    override val changeTitle = "Titel ändern"
    override val titleDialog = "Titel"
    override val titleHint = "Leer lassen, dann wählt anynear den Titel."
    override val save = "Speichern"
    override val renameFile = "Dateinamen ändern"
    override val sort = "Sortieren"
    override val sortNewest = "Neueste zuerst"
    override val sortTitle = "Titel A–Z"
    override val sortName = "Dateiname A–Z"
    override val rotate = "Drehen"
    override val rotateHint = "Eine Vierteldrehung nach rechts"
    override val rotateFailed = "Das Foto ließ sich nicht drehen."
    override val sendToChat = "In einen Chat schicken"
    override val saveToPhone = "Auf dem Handy speichern"
    override val openWithOtherApp = "Mit anderer App öffnen"
    override val cantShowHere = "Diese Art Datei lässt sich hier nicht anzeigen. Du kannst sie mit einer anderen App öffnen oder auf dem Handy speichern."
    override val delete = "Löschen"
    override fun deleteQuestion(name: String) = "„$name“ löschen?"
    override val deleteText = "Die Datei ist danach auf diesem Handy und im Netz weg. Sicherungen, die du vorher gemacht hast, enthalten sie noch."
    override val moveToBin = "In den Papierkorb"
    override fun binQuestion(name: String) = "„$name“ in den Papierkorb legen?"
    override fun binText(days: Int) = "Die Datei bleibt $days Tage im Papierkorb und zählt so lange zu deinem Kontingent im Netz. Danach ist sie endgültig weg."
    override fun circleBinText(days: Int) = "Die Datei liegt dann $days Tage im Papierkorb des Kreises. Alle im Kreis können sie in dieser Zeit zurückholen."
    override val bin = "Papierkorb"
    override fun binRow(count: Int) = if (count == 1) "Papierkorb: 1 Datei" else "Papierkorb: $count Dateien"
    override fun binIntro(days: Int) = "Gelöschte Dateien bleiben $days Tage hier und zählen so lange zu deinem Kontingent im Netz. Danach sind sie endgültig weg."
    override fun circleBinIntro(days: Int) = "Gelöschte Dateien des Kreises bleiben $days Tage hier. Alle im Kreis können sie zurückholen."
    override fun binGoneOn(date: String) = "Endgültig weg am $date"
    override val restore = "Wiederherstellen"
    override val deleteForGood = "Endgültig löschen"
    override val emptyBin = "Papierkorb leeren"
    override val emptyBinQuestion = "Papierkorb leeren?"
    override val emptyBinText = "Alle Dateien im Papierkorb sind danach auf diesem Handy und im Netz weg."
    override val circleEmptyBinText = "Alle Dateien im Papierkorb sind danach für alle im Kreis weg."
    override val cantOpen = "Die Datei lässt sich nicht öffnen."
    override val toMyFiles = "In meine Ablage"
    override val addedToMyFiles = "In deiner Ablage gespeichert."
    override fun chooseStore(count: Int) = if (count == 1) "Möchtest du die Datei in der Ablage speichern? In welcher?" else "Möchtest du die $count Dateien in der Ablage speichern? In welcher?"
    override fun addedTo(store: String) = "In der Ablage von „$store“ gespeichert."
    override val fileTooLarge = "Die Datei ist zu groß (höchstens 50 MB)."
    override val tooLargeForChat = "Für einen Chat ist die Datei zu groß (höchstens 10 MB)."
    override val sendHint = "Wer die Datei bekommt, behält eine Kopie. Die lässt sich nicht zurückholen."
    override val search = "Suchen, z. B. Impfpass"
    override val allKinds = "Alle"
    override fun kindName(kind: String) = when (kind) {
        "invoice" -> "Rechnung"
        "contract" -> "Vertrag"
        "id" -> "Ausweis"
        "official" -> "Bescheid oder Brief vom Amt"
        "insurance" -> "Versicherung"
        "bank" -> "Bank"
        "payslip" -> "Lohn und Gehalt"
        "receipt" -> "Kassenbon oder Garantie"
        "health" -> "Gesundheit"
        "certificate" -> "Urkunde oder Zeugnis"
        "tax" -> "Steuer"
        "provision" -> "Vorsorge und Vollmacht"
        "photo" -> "Foto"
        else -> "Sonstiges"
    }
    override val docReading = "Wird noch gelesen"
    override val docChoose = "Was ist das?"
    override val readText = "Gelesener Text"
    override val readTextNone = "Kein Text gefunden"
    override fun readTextWords(n: Int) = if (n == 1) "1 Wort, antippen zum Ansehen" else "$n Wörter, antippen zum Ansehen"
    override val readTextHint = "So hat dein Handy die Datei gelesen. Danach richten sich Suche und Erkennung. Der Text bleibt auf dem Handy."
    override val docRecognizedHint = "$APP erkennt das selbst, nur auf deinem Handy. Stimmt es nicht, tippe darauf."
    override val docChosenHint = "Von dir gewählt. Tippe darauf, um es zu ändern."
    override fun docUntil(date: String) = "gültig bis $date"
    override fun amount(cents: Long, currency: String) = "${money(cents, ',')} ${currencySign(currency)}"
    override val documentsView = "Dokumente"
    override val photosView = "Fotos"
    override val searchPhotos = "Suchen, z. B. Hund"
    override val searchAll = "In allen Ablagen suchen"
    override fun topicName(topic: String) = when (topic) {
        "people" -> "Menschen"
        "animals" -> "Tiere"
        "nature" -> "Natur"
        "food" -> "Essen"
        "buildings" -> "Gebäude"
        "vehicles" -> "Fahrzeuge"
        "home" -> "Zuhause"
        "sport" -> "Sport"
        "party" -> "Feiern"
        else -> topic
    }
}

internal object FilesTextsEn : FilesTexts {
    override val personal = "Personal"
    override val justForMe = "Just for me"
    override val withYourCircles = "With your circles"
    override val noCirclesYet = "Once you are in a circle, its files appear here."
    override val files = "Files"
    override val addFile = "Add file"
    override val circleFiles = "This circle's files"
    override fun circleIntro(circle: String) = "The shared files of “$circle”. Everyone in the circle sees the same files and can rename and delete them."
    override val circleOnlyHere = "The files are stored encrypted on the phones in the circle and in the $APP network. Only people in the circle can open them. Whoever leaves the circle no longer has them in their files."
    override val circleOnlineSavedText = "Encrypted at several data centres in Europe, with no expiry date. Everyone in the circle can open it."
    override val circleNotAcceptedOnlineText = "The $APP network did not accept the file right now, maybe the quota in the network is used up. $APP tries again by itself."
    override fun circleLastChecked(time: String) = "Last checked: $time. Everyone in the circle can open the file."
    override val circleDeleteText = "The file disappears from the files of everyone in the circle. Anyone who saved or forwarded it before keeps that copy."
    override fun fileCount(n: Int) = if (n == 1) "1 file" else "$n files"
    override val noFilesYet = "Empty so far"
    override val myFilesIntro = "Important photos and files, just for you. They are there without internet too."
    override val onlyHere = "Your files are stored encrypted on this phone and in the $APP network. Only you can open them. They come to a new phone with your backup."
    override val netStatus = "Status in the network"
    override val netOnDevice = "Only on this phone"
    override val netInBin = "In the bin"
    override val onlineSaved = "Distributed in the network"
    override val onlineSavedText = "Encrypted at several data centres in Europe, with no expiry date. Only you can open it."
    override val notYetOnline = "Being distributed"
    override val notYetOnlineText = "This happens by itself as soon as there is internet."
    override val securingText = "The file has reached the network. The copies at further data centres are made by themselves."
    override val netIncomplete = "Partly distributed"
    override val netIncompleteText = "Right now the file is at only one data centre. The network restores the copies by itself."
    override val keepOnDevice = "Keep on this phone"
    override val keepOnDeviceOn = "The file is there even without internet."
    override val keepOnDeviceOff = "Only in the network. Only a preview stays on the phone; opening it needs internet."
    override val keepOnDeviceWait = "Possible as soon as the file is distributed in the network."
    override val keepOnDeviceLoading = "Loading onto the phone …"
    override val keepOnDeviceFooter = "Turning it off frees up space on the phone. If every location in the network fails, the file cannot be restored."
    override val onlineOnly = "Only in the network"
    override val onlineOnlyNoInternet = "This file is only in the network. Opening it needs internet."
    override val tooLargeOnlineText = "The file is larger than the $APP network accepts right now. Until then it is only on this phone. $APP tries again by itself."
    override val notAcceptedOnlineText = "The $APP network did not accept the file right now, maybe your quota in the network is used up. $APP tries again by itself."
    override val storageTitle = "Usage in the network"
    override fun storageUsed(used: String, quota: String) = "$used of $quota used"
    override fun gigabytes(tenths: Long) = if (tenths % 10 == 0L) "${tenths / 10} GB" else "${tenths / 10}.${tenths % 10} GB"
    override val storageNearlyFull = "Your quota in the network is almost used up."
    override val storageFull = "Your quota in the network is used up. You can add new files again once there is room. Opening still works."
    override val storageAllStores = "One quota for all your files. What others put into a circle counts for them."
    override val storageFullOnlineText = "Your quota in the network is used up. The file stays on this phone until there is room."
    override val moreStorage = "More quota"
    override fun aboIntro(trialMonths: Int) =
        "Messages, radio and alerts stay free. Soon there will be a subscription for more. Register your interest now and get $trialMonths months free."
    override val aboNetwork = "Not in one cloud, but in the $APP network: every file is stored encrypted at three different data centres in Europe. If one fails, the network restores the copy by itself."
    override val aboPreviewNote = "Expected prices including VAT. Not on sale yet."
    override val aboYearly = "Yearly"
    override fun aboSaving(percent: Int) = "Yearly: save $percent%"
    override fun aboYearlyPerMonth(perMonth: String, monthly: String) = "works out at $perMonth a month instead of $monthly"
    override val aboMonthly = "Monthly"
    override fun aboPerYear(price: String) = "$price a year"
    override fun aboPerMonth(price: String) = "$price a month"
    override fun aboOrMonthly(price: String) = "or $price a month"
    override fun aboOrYearly(price: String) = "or $price a year"
    override val aboFreeNow = "What you have now"
    override val aboYourPlan = "Your plan"
    override val aboRecommendedShort = "Top pick"
    override val aboMonthShort = "a month"
    override val aboYearShort = "a year"
    override val aboChooseHint = "Tap a plan to compare it with yours."
    override val aboRecommended = "Recommended"
    override fun aboInterest(trialMonths: Int) = "Get $trialMonths months free"
    override val aboInterestNote = "The form opens on anynear.app. We will write to you as soon as the subscription starts."
    override val aboOffline = "The plans cannot be loaded right now."
    override val aboLoading = "Loading plans …"
    override fun euro(cents: Long) = if (cents % 100 == 0L) "€${cents / 100}" else "€${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    override fun onlineWhere(date: String, mailboxes: Int) =
        "Since $date encrypted at ${if (mailboxes == 1) "one data centre" else "$mailboxes data centres"} of different operators in Europe, with no expiry date."
    override val holdsThisFile = "Has this file"
    override fun lastChecked(time: String) = "Last checked: $time. Only you can open the file."
    override val addPhoto = "Add photo"
    override fun photoName(date: String) = "Photo from $date"
    override fun addedOn(date: String) = "Added on $date"
    override val open = "Open"
    override val rename = "Rename"
    override val renameTitle = "New name"
    override val changeTitle = "Change title"
    override val titleDialog = "Title"
    override val titleHint = "Leave it empty and anynear chooses the title."
    override val save = "Save"
    override val renameFile = "Change file name"
    override val sort = "Sort"
    override val sortNewest = "Newest first"
    override val sortTitle = "Title A–Z"
    override val sortName = "File name A–Z"
    override val rotate = "Rotate"
    override val rotateHint = "A quarter turn to the right"
    override val rotateFailed = "The photo couldn't be rotated."
    override val sendToChat = "Send to a chat"
    override val saveToPhone = "Save on phone"
    override val openWithOtherApp = "Open with another app"
    override val cantShowHere = "This kind of file can't be shown here. You can open it with another app or save it on your phone."
    override val delete = "Delete"
    override fun deleteQuestion(name: String) = "Delete “$name”?"
    override val deleteText = "The file is then gone from this phone and from the network. Backups you made earlier still contain it."
    override val moveToBin = "Move to bin"
    override fun binQuestion(name: String) = "Move “$name” to the bin?"
    override fun binText(days: Int) = "The file stays in the bin for $days days and counts towards your quota in the network until then. After that it is gone for good."
    override fun circleBinText(days: Int) = "The file then stays in the circle's bin for $days days. Everyone in the circle can bring it back until then."
    override val bin = "Bin"
    override fun binRow(count: Int) = if (count == 1) "Bin: 1 file" else "Bin: $count files"
    override fun binIntro(days: Int) = "Deleted files stay here for $days days and count towards your quota in the network until then. After that they are gone for good."
    override fun circleBinIntro(days: Int) = "Deleted files of the circle stay here for $days days. Everyone in the circle can bring them back."
    override fun binGoneOn(date: String) = "Gone for good on $date"
    override val restore = "Restore"
    override val deleteForGood = "Delete for good"
    override val emptyBin = "Empty bin"
    override val emptyBinQuestion = "Empty the bin?"
    override val emptyBinText = "All files in the bin are then gone from this phone and from the network."
    override val circleEmptyBinText = "All files in the bin are then gone for everyone in the circle."
    override val cantOpen = "The file cannot be opened."
    override val toMyFiles = "Save to my files"
    override val addedToMyFiles = "Saved to your files."
    override fun chooseStore(count: Int) = if (count == 1) "Do you want to save the file to your files? Where?" else "Do you want to save the $count files to your files? Where?"
    override fun addedTo(store: String) = "Saved to the files of “$store”."
    override val fileTooLarge = "The file is too large (50 MB max)."
    override val tooLargeForChat = "The file is too large for a chat (10 MB max)."
    override val sendHint = "Whoever receives the file keeps a copy. It cannot be taken back."
    override val search = "Search, e.g. vaccination"
    override val allKinds = "All"
    override fun kindName(kind: String) = when (kind) {
        "invoice" -> "Invoice"
        "contract" -> "Contract"
        "id" -> "ID document"
        "official" -> "Letter from an authority"
        "insurance" -> "Insurance"
        "bank" -> "Bank"
        "payslip" -> "Payslip"
        "receipt" -> "Receipt or warranty"
        "health" -> "Health"
        "certificate" -> "Certificate"
        "tax" -> "Tax"
        "provision" -> "Power of attorney and will"
        "photo" -> "Photo"
        else -> "Other"
    }
    override val docReading = "Still being read"
    override val docChoose = "What is this?"
    override val readText = "Text read"
    override val readTextNone = "No text found"
    override fun readTextWords(n: Int) = if (n == 1) "1 word, tap to see it" else "$n words, tap to see them"
    override val readTextHint = "This is how your phone read the file. Search and recognition go by it. The text stays on your phone."
    override val docRecognizedHint = "$APP recognizes this by itself, only on your phone. If it is wrong, tap it."
    override val docChosenHint = "Chosen by you. Tap to change it."
    override fun docUntil(date: String) = "valid until $date"
    override fun amount(cents: Long, currency: String) = "${currencySign(currency)}${money(cents, '.')}"
    override val documentsView = "Documents"
    override val photosView = "Photos"
    override val searchPhotos = "Search, e.g. dog"
    override val searchAll = "Search all files"
    override fun topicName(topic: String) = when (topic) {
        "people" -> "People"
        "animals" -> "Animals"
        "nature" -> "Nature"
        "food" -> "Food"
        "buildings" -> "Buildings"
        "vehicles" -> "Vehicles"
        "home" -> "Home"
        "sport" -> "Sport"
        "party" -> "Celebrations"
        else -> topic
    }
}
