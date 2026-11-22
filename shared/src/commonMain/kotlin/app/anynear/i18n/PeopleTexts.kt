package app.anynear.i18n

/** Circles, contacts, people, invitations, QR code. */
interface PeopleTexts {
    // Shared across several screens
    val scanQrCode: String
    val notAContactYet: String

    /** Contact details of a radio without the app (e.g. Meshtastic). */
    val radioOnlyContact: String
    val shareLink: String

    // Circles
    fun circleCount(n: Int): String
    val startOrJoinCircle: String

    /** „Noch kein Kreis. Gründe einen oder tritt mit einer Einladung bei.“ */
    val noCircleYet: String
    val searchCircles: String
    val yourCircles: String
    val startNewCircle: String
    val joinWithInviteLink: String

    /** „Einladung von einem anderen Handy“ */
    val inviteFromOtherPhone: String
    val joinCircle: String
    val inviteLink: String
    val startCircle: String
    val circleName: String

    /** Placeholder for the name: „z. B. Freunde“ */
    val circleNamePlaceholder: String

    // Detail page of a circle
    val invite: String
    val edit: String
    val removedFromThisCircle: String
    val members: String

    /** Footer below „Mitglieder“ as long as nobody else is in the circle. */
    val noMembersYet: String

    /** Own line in „Mitglieder“: „Anna (du)“, without a name „Du (du)“. */
    fun ownMember(name: String): String

    /** Below the own line in „Mitglieder“ when one's own further devices are in the circle too (ADR 0042). */
    fun ownDevicesInCircle(count: Int): String

    /** „Alte Einladungslinks ungültig machen“ */
    val invalidateOldInviteLinks: String
    val removeFromList: String
    val leaveCircle: String
    fun removeMemberTitle(name: String): String
    fun removeMemberText(name: String, circle: String): String
    val invalidateInviteLinksTitle: String
    val invalidateInviteLinksText: String

    /** Button „Ungültig machen“ */
    val invalidate: String

    /** Briefly, who is in the circle: „Du wurdest entfernt“, „Noch niemand eingeladen“, „Du und 3 weitere“. */
    val summaryRemoved: String
    val internetOnlyShort: String
    val internetOnlyExplained: String
    val summaryNobodyInvited: String
    fun summaryYouAndOthers(n: Int): String

    val removeCircleTitle: String
    fun leaveCircleTitle(name: String): String
    val removeCircleText: String
    val leaveCircleText: String
    /** Only own devices left in the circle: leaving also deletes the circle storage (ADR 0049 §5). */
    val leaveCircleLastNote: String
    val editCircle: String
    val everyoneSeesChange: String

    // Contacts
    val addContact: String

    /** „Noch niemand da. Wer einem deiner Kreise beitritt, erscheint hier automatisch.“ */
    val noContactsYet: String

    /** „Ohne Kreis · direkter Kontakt“ */
    val noCircleSelfAdded: String
    val pasteLink: String
    val pasteLinkHint: String
    val scanContactHint: String
    val myContact: String
    val myContactHint: String
    val addContactText: String

    // Invite from the phone's contacts (ADR 0003: no phone numbers)
    val inviteFromPhone: String
    val inviteFromPhoneHint: String
    /** Why there is no phone number search, said as an advantage. */
    val inviteFromPhoneWhy: String
    val inviteFromPhoneHow: String
    val pickPhoneContact: String

    /** Scanned or pasted code does not match the stored key of [name]. */
    fun keyMismatch(name: String): String
    val ownContact: String
    val preparingContact: String

    /** Only for the support identifier (ADR 0084 §6). */
    val publicContact: String
    val publicContactText: String
    val publicContactRevoke: String
    val ownContactText: String
    val copied: String

    // Detail page of a person
    val selfAdded: String
    val reachableBy: String

    /** „über die anynear-App“ */
    val viaApp: String

    /** „Funkgerät „Anna““ */
    fun radioNamed(name: String): String
    val notKnownYet: String
    val sharedCircles: String

    /** „Zuletzt erreichbar heute, 14:05“ */
    fun lastSeen(at: String): String

    // Inviting
    fun inviteTo(circle: String): String
    val qrInviteHelp: String
    val inviteWarning: String
    val shareLinkHint: String
    val showQrCode: String
    val showQrCodeHint: String
    val qrCode: String

    // Scan QR code
    val notAppCode: String
    val scanHelp: String
    val cameraNeededAndroid: String
    val cameraNeededIos: String
    val viewPerson: String
    val leaveCircleContactsNote: String
    val editNameAndPicture: String

    // Own name for a person, only for oneself (person page)
    val changeName: String
    val contactNameLabel: String

    /** Below the field: who sees the name. */
    val contactNameHint: String

    /** Back to the name from the person's profile. */
    val resetName: String

    /** Small below an own name: „Im Profil: Hans“. */
    fun profileNameLine(name: String): String
    val removeMember: String
    val whoToRemove: String
    val circleExplained: String
    val acceptInvite: String
    val pasteInviteLinkHint: String
    val shareMyContact: String
    val openConversation: String
    val radioOnly: String
    val lastReached: String
    val lastReachedFooter: String
    fun nothingReceivedFrom(name: String): String
    val viaInternetAndRadio: String
    val viaRadio: String
    val viaInternet: String
    val removeContact: String
    val removeRadioContactText: String
    val inviteToCircle: String
    fun inviteWhichCircle(name: String): String
    fun staysWhileSharedCircle(name: String): String
    fun removeDirectContactText(name: String): String
    /** Every relay refuses our messages to [name] (ADR 0046): hint in the chat. */
    fun unreachableText(name: String): String
    val unreachableRemove: String
    val unreachableKeep: String
    /** Below the name in lists. */
    val unreachableShort: String
    fun removeDirectContactInCircleText(name: String, circles: String): String
    /** Button in the remove dialog: only the contact, the conversation stays. */
    val removeKeepHistory: String
    /** Button in the remove dialog: contact and conversation on this device. */
    val removeWithHistory: String
    /** Line below „Erweitert“, also for people who are no longer a contact. */
    val deleteHistory: String
    fun deleteHistoryTitle(name: String): String
    fun deleteHistoryText(name: String): String
    // Radio check (ADR 0053)
    /** Heading „Über Funk“ */
    val overRadio: String
    /** Button „Über Funk prüfen“ */
    val checkOverRadio: String
    /** Button „Wer antwortet über Funk?“ (circle, radio page) */
    val whoAnswersOverRadio: String
    /** The radio as it appears in the sentence: „Das Funkgerät von Paul“, „… von Paul und Anna“; without a name the device name. */
    fun radioOf(users: List<String>, radioName: String): String
    fun radioAnswersDirect(radio: String): String
    fun radioAnswersVia(radio: String, others: Int): String
    fun radioAnswers(radio: String): String
    fun radioNoAnswer(radio: String): String
    fun radioChecking(radio: String): String
    fun noRadioKnown(name: String): String
    val connectRadioFirst: String

    /** Radio connected, but the radio channel `anynear` is still missing (ADR 0051). */
    val radioNotSetUp: String
    fun checkAgainFrom(time: String): String
    /** Below „Mehr dazu“ when there is no answer, honest. */
    val noAnswerExplained: String
    /** Footer: why „über X andere“ is only a number. */
    val radioCheckFooter: String
    fun answersCount(answered: Int, total: Int): String
    val groupCheckRunning: String
    val noRadiosKnownHere: String
    fun signalLine(level: app.anynear.app.SignalLevel, snr: Float, rssi: Int?): String
    fun forwardsLine(hops: Int): String
    fun answerAfter(seconds: Long): String
    fun checkedAt(time: String): String
    fun radioNumber(id: String): String
    // Contact requests and introductions (ADR 0116)
    fun requestTitle(name: String): String
    fun introducedBy(name: String): String
    val viaYourContactLink: String
    val requestAgree: String
    val requestDecline: String
    fun requestAcceptedLine(name: String): String
    fun requestAnsweredLine(name: String): String
    fun requestsTitle(count: Int): String
    val requestsPage: String
    fun requestsRow(count: Int): String
    val requestsToYou: String
    val requestsFromYou: String
    val requestsNone: String
    val requestAboutContact: String
    fun requestAboutLocation(exact: Boolean): String
    val requestAboutSos: String
    val requestAboutSosOwn: String
    val sharingsTitle: String
    val sharingsFooter: String
    val askAsSos: String
    val requestsShow: String
    val requestsHide: String
    fun requestSent(name: String): String
    val requestPending: String
    val shareContact: String
    fun shareContactWho(name: String): String
    fun shareContactHint(name: String): String
    val noOtherContacts: String
    fun introSent(name: String, to: String): String
    val introFailed: String
    val introExpired: String
    fun introAcceptedLine(to: String, name: String): String
    fun introCardIncoming(by: String, name: String): String
    fun introCardOwn(name: String): String
    val introAdd: String
    fun introConfirmText(name: String): String
    val alreadyContact: String
}

internal object PeopleTextsDe : PeopleTexts {
    override val scanQrCode = "QR-Code scannen"
    override val notAContactYet = "Noch kein Kontakt"
    override val radioOnlyContact = "Nutzt kein $APP. Nur über Funk erreichbar, ohne Bilder und Dateien."
    override val shareLink = "Link teilen"

    override fun circleCount(n: Int) = if (n == 1) "1 Kreis" else "$n Kreise"
    override val startOrJoinCircle = "Kreis gründen oder beitreten"
    override val noCircleYet = "Noch kein Kreis. Gründe einen oder tritt mit einer Einladung bei."
    override val searchCircles = "Kreise durchsuchen"
    override val yourCircles = "Deine Kreise"
    override val startNewCircle = "Neuen Kreis gründen"
    override val joinWithInviteLink = "Mit Einladungslink beitreten"
    override val inviteFromOtherPhone = "Einladung von einem anderen Handy"
    override val joinCircle = "Kreis beitreten"
    override val inviteLink = "Einladungslink"
    override val startCircle = "Kreis gründen"
    override val circleName = "Name des Kreises"
    override val circleNamePlaceholder = "z. B. Freunde"

    override val invite = "Einladen"
    override val edit = "Bearbeiten"
    override val removedFromThisCircle = "Du wurdest aus diesem Kreis entfernt."
    override val members = "Mitglieder"
    override val noMembersYet = "Noch niemand sonst. Wer beitritt, erscheint hier, sobald seine App online war."
    override fun ownMember(name: String) = "${name.ifBlank { "Du" }} (du)"
    override fun ownDevicesInCircle(count: Int) = "Auf $count Geräten"
    override val invalidateOldInviteLinks = "Alte Einladungslinks ungültig machen"
    override val removeFromList = "Aus der Liste nehmen"
    override val leaveCircle = "Kreis verlassen"
    override fun removeMemberTitle(name: String) = "$name entfernen?"
    override fun removeMemberText(name: String, circle: String) =
        "$name bekommt danach keine Nachrichten mehr aus „$circle“. " +
            "Alle im Kreis sehen, dass du $name entfernt hast, und können wieder einladen. " +
            "Bisherige Einladungslinks gelten dann nicht mehr."
    override val invalidateInviteLinksTitle = "Einladungslinks ungültig machen?"
    override val invalidateInviteLinksText =
        "Sinnvoll, wenn ein Link an die falsche Person weitergeleitet wurde. Alle, die schon im Kreis sind, " +
            "bleiben drin. Wer einen alten Link noch nicht geöffnet hat, braucht einen neuen."
    override val invalidate = "Ungültig machen"
    override val summaryRemoved = "Du wurdest entfernt"
    override val internetOnlyShort = "nur Internet"
    override val internetOnlyExplained =
        "Auf deinem Funkgerät ist gerade kein Platz für diesen Kreis. Er läuft übers Internet und bekommt von selbst einen Platz, sobald hier mehr los ist."
    override val summaryNobodyInvited = "Noch niemand eingeladen"
    override fun summaryYouAndOthers(n: Int) = "Du und $n weitere"
    override val removeCircleTitle = "Kreis entfernen?"
    override fun leaveCircleTitle(name: String) = "Kreis „$name“ verlassen?"
    override val removeCircleText = "Der Kreis verschwindet aus deiner Liste."
    override val leaveCircleText =
        "Du bekommst keine Nachrichten mehr aus diesem Kreis, und sein Chat wird von deinem Handy gelöscht. " +
            "Deine Direktnachrichten bleiben erhalten. Zurück geht es nur mit einer neuen Einladung."
    override val leaveCircleLastNote =
        "Außer dir ist niemand mehr im Kreis. Deshalb wird auch die Ablage des Kreises gelöscht, für immer."
    override val editCircle = "Kreis bearbeiten"
    override val everyoneSeesChange = "Alle im Kreis sehen die Änderung."

    override val addContact = "Kontakt hinzufügen"
    override val noContactsYet = "Noch niemand da. Wer einem deiner Kreise beitritt, erscheint hier automatisch."
    override val noCircleSelfAdded = "Ohne Kreis · direkter Kontakt"
    override val pasteLink = "Link einfügen"
    override val pasteLinkHint = "Den Kontaktlink, den du bekommen hast"
    override val scanContactHint = "Kontakt oder Einladung einer anderen Person scannen"
    override val myContact = "Mein Kontakt"
    override val myContactHint = "Gib deinen Kontakt an andere weiter, als Link oder QR-Code"
    override val inviteFromPhone = "Aus Telefonkontakten einladen"
    override val inviteFromPhoneHint = "Per SMS mit deinem Kontaktlink"
    override val inviteFromPhoneWhy =
        "$APP kennt deine Telefonnummer nicht. So kann dich niemand darüber finden, und du lädst selbst ein, wen du willst."
    override val inviteFromPhoneHow =
        "Wähle eine Person aus deinen Kontakten. Sie bekommt eine SMS mit deinem Kontaktlink. Ihre Nummer bleibt auf deinem Handy."
    override val pickPhoneContact = "Person auswählen"
    override val addContactText =
        "Für Menschen außerhalb deiner Kreise: Tippe auf den Kontaktlink, den du bekommen hast, scanne den QR-Code oder füge den Link hier ein. " +
            "Menschen aus deinen Kreisen erscheinen automatisch."
    override fun keyMismatch(name: String) =
        "Achtung: Dieser Code passt nicht zum gespeicherten Kontakt $name. " +
            "Bitte bei der Person direkt nachfragen, bevor du ihn verwendest."
    override val ownContact = "Das ist dein eigener Kontakt."
    override val preparingContact = "Einen Moment, dein Kontakt wird vorbereitet …"
    override val publicContact = "Link zum Veröffentlichen"
    override val publicContactText = "Für App, Webapp und Website. Gilt ein Jahr. Zurückziehen, wenn Unerwünschtes kommt."
    override val publicContactRevoke = "Zurückziehen"
    override val ownContactText =
        "Mit diesem Code oder Link kann dich jemand außerhalb deiner Kreise als Kontakt hinzufügen und dir " +
            "Ende-zu-Ende verschlüsselt schreiben. Er enthält keine Passwörter."
    override val copied = "Kopiert"

    override val selfAdded = "Selbst hinzugefügt"
    override val reachableBy = "Erreichbar über"
    override val viaApp = "über die $APP-App"
    override fun radioNamed(name: String) = "Funkgerät „$name“"
    override val notKnownYet = "Noch unbekannt"
    override val sharedCircles = "Gemeinsame Kreise"
    override fun lastSeen(at: String) = "Zuletzt erreichbar $at"

    override fun inviteTo(circle: String) = "In „$circle“ einladen"
    override val qrInviteHelp =
        "Die Person öffnet die Kamera ihres Handys und richtet sie auf den Code. " +
            "Ist $APP installiert, öffnet sich direkt die Einladung."
    override val inviteWarning = "Wer den Link oder Code bekommt, kann dem Kreis beitreten. Gib ihn nur an Menschen, die dazugehören sollen."
    override val shareLinkHint = "Per WhatsApp, Signal, SMS … wenn die Person nicht da ist."
    override val showQrCode = "QR-Code zeigen"
    override val showQrCodeHint = "Wenn die Person neben dir ist."
    override val qrCode = "QR-Code"

    override val notAppCode = "Das ist kein Code von $APP."
    override val scanHelp = "Halte die Kamera auf den QR-Code einer Einladung oder eines Kontakts."
    override val cameraNeededAndroid = "Zum Scannen braucht $APP die Kamera. Du kannst sie in den Einstellungen des Handys für $APP erlauben."
    override val cameraNeededIos = "Zum Scannen braucht $APP die Kamera. Du kannst sie in den Einstellungen des iPhones für $APP erlauben."
    override val viewPerson = "Person ansehen"
    override val leaveCircleContactsNote = "Personen bleiben in deinen Kontakten, wenn sie direkt gespeichert sind, ihr euch bereits direkt geschrieben habt oder ihr einen weiteren gemeinsamen Kreis habt."
    override val editNameAndPicture = "Name und Bild ändern"
    override val changeName = "Namen ändern"
    override val contactNameLabel = "Name"
    override val contactNameHint = "Nur du siehst diesen Namen, auf allen deinen Geräten. Die Person merkt davon nichts."
    override val resetName = "Zurücksetzen"
    override fun profileNameLine(name: String) = "Im Profil: $name"
    override val removeMember = "Mitglied entfernen"
    override val whoToRemove = "Wen entfernen?"
    override val circleExplained = "Ein Kreis ist eine feste Gruppe, zum Beispiel deine Familie oder deine Nachbarn. Alle im Kreis lesen mit."
    override val acceptInvite = "Einladung annehmen"
    override val pasteInviteLinkHint = "Den Einladungslink, den du bekommen hast"
    override val shareMyContact = "Meinen Kontakt teilen"
    override val openConversation = "Gespräch öffnen"
    override val radioOnly = "nur über Funk"
    override val lastReached = "Zuletzt kontaktiert"
    override val lastReachedFooter = "Wann diese Person zuletzt eine Nachricht empfangen hat."
    override fun nothingReceivedFrom(name: String) = "Noch nichts von $name erhalten"
    override val viaInternetAndRadio = "über Internet und Funk"
    override val viaRadio = "über Funk"
    override val viaInternet = "über Internet"
    override val removeContact = "Kontakt entfernen"
    override val removeRadioContactText = "Das Funkgerät steht danach nicht mehr in deinen Kontakten. Euer Gespräch kannst du behalten oder mit löschen."
    override val inviteToCircle = "In einen Kreis einladen"
    override fun inviteWhichCircle(name: String) = "$name in welchen Kreis einladen?"
    override fun staysWhileSharedCircle(name: String) = "$name bleibt in deinen Kontakten, solange ihr einen gemeinsamen Kreis habt."
    override fun removeDirectContactText(name: String) = "$name steht danach nicht mehr in deinen Kontakten und kann dir nicht mehr direkt schreiben. Wieder verbinden könnt ihr euch über den Kontaktlink. Euer Gespräch kannst du behalten oder mit löschen."
    override fun unreachableText(name: String) = "$name ist über anynear nicht mehr erreichbar. Vielleicht wurde das Konto gelöscht."
    override val unreachableRemove = "Kontakt entfernen"
    override val unreachableKeep = "Behalten"
    override val unreachableShort = "nicht erreichbar"
    override fun removeDirectContactInCircleText(name: String, circles: String) = "$name steht danach nicht mehr als direkter Kontakt in deinen Kontakten. Weil ihr gemeinsam in einem Kreis seid ($circles), bleibt $name dort als Mitglied sichtbar, und ihr könnt euch weiter schreiben. Schreibt ihr euch wieder direkt, gilt $name wieder als dein Kontakt. Euer Gespräch kannst du behalten oder mit löschen."
    override val removeKeepHistory = "Nur Kontakt entfernen"
    override val removeWithHistory = "Kontakt und Gespräch löschen"
    override val deleteHistory = "Gespräch löschen"
    override fun deleteHistoryTitle(name: String) = "Gespräch mit $name löschen?"
    override fun deleteHistoryText(name: String) = "Alle Nachrichten, Fotos und Dateien aus eurem Gespräch verschwinden von diesem Handy. Bei $name bleibt das Gespräch."
    override val overRadio = "Über Funk"
    override val checkOverRadio = "Über Funk prüfen"
    override val whoAnswersOverRadio = "Wer antwortet über Funk?"
    override fun radioOf(users: List<String>, radioName: String) = when (users.size) {
        0 -> "Funkgerät „$radioName“"
        1 -> "Das Funkgerät von ${users.single()}"
        else -> "Das Funkgerät von ${users.dropLast(1).joinToString(", ")} und ${users.last()}"
    }
    override fun radioAnswersDirect(radio: String) = "$radio antwortet. Direkt."
    override fun radioAnswersVia(radio: String, others: Int) =
        if (others == 1) "$radio antwortet. Über ein anderes Funkgerät." else "$radio antwortet. Über $others andere Funkgeräte."
    override fun radioAnswers(radio: String) = "$radio antwortet."
    override fun radioNoAnswer(radio: String) = "$radio hat nicht geantwortet."
    override fun radioChecking(radio: String) = "$radio wird geprüft …"
    override fun noRadioKnown(name: String) = "Von $name kennt $APP noch kein Funkgerät."
    override val connectRadioFirst = "Verbinde zuerst dein Funkgerät."
    override val radioNotSetUp = "Dein Funkgerät ist noch nicht für $APP eingerichtet."
    override fun checkAgainFrom(time: String) = "Noch einmal möglich ab $time."
    override val noAnswerExplained = "Das heißt nicht, dass das Funkgerät nicht erreichbar ist. Das Paket kann unterwegs verloren gegangen oder das Gerät gerade aus sein. Versuch es später noch einmal."
    override val radioCheckFooter = "Geprüft wird derselbe Weg, den deine Nachrichten nehmen, auch über andere Funkgeräte. Die Person merkt davon nichts."
    override fun answersCount(answered: Int, total: Int) = "$answered von $total antworten"
    override val groupCheckRunning = "Wird geprüft. Das dauert etwas, eins nach dem anderen."
    override val noRadiosKnownHere = "Von niemandem hier kennt $APP ein Funkgerät."
    override fun signalLine(level: app.anynear.app.SignalLevel, snr: Float, rssi: Int?): String {
        val word = when (level) {
            app.anynear.app.SignalLevel.GOOD -> "gut"
            app.anynear.app.SignalLevel.MEDIUM -> "mittel"
            app.anynear.app.SignalLevel.WEAK -> "schwach"
        }
        val snrText = snr.toString().replace('.', ',')
        return "Signal beim letzten Schritt: $word ($snrText dB${rssi?.let { ", $it dBm" }.orEmpty()})"
    }
    override fun forwardsLine(hops: Int) = when (hops) {
        0 -> "Direkt, ohne Weiterleitung"
        1 -> "Über 1 anderes Funkgerät (Rückweg)"
        else -> "Über $hops andere Funkgeräte (Rückweg)"
    }
    override fun answerAfter(seconds: Long) = "Antwort nach $seconds s"
    override fun checkedAt(time: String) = "Geprüft: $time"
    override fun radioNumber(id: String) = "Funkgerät $id"
    override fun requestTitle(name: String) = "$name möchte dein Kontakt sein"
    override fun introducedBy(name: String) = "Vorgestellt von $name"
    override val viaYourContactLink = "Über deinen Kontaktlink"
    override val requestAgree = "Zustimmen"
    override val requestDecline = "Ablehnen"
    override fun requestAcceptedLine(name: String) = "Du hast $name hinzugefügt. Ihr könnt euch jetzt schreiben."
    override fun requestAnsweredLine(name: String) = "$name hat zugestimmt. Ihr könnt euch jetzt schreiben."
    override fun requestsTitle(count: Int) = "$count Kontaktanfragen"
    override val requestsPage = "Anfragen"
    override fun requestsRow(count: Int) = if (count == 1) "1 Anfrage wartet auf dich" else "$count Anfragen warten auf dich"
    override val requestsToYou = "An dich"
    override val requestsFromYou = "Von dir"
    override val requestsNone = "Gerade ist nichts offen."
    override val requestAboutContact = "möchte Kontakt werden"
    override fun requestAboutLocation(exact: Boolean) = if (exact) "Standort freigeben: genau" else "Standort freigeben: ungefähr"
    override val requestAboutSos = "fragt, ob du SOS-Kontakt bist"
    override val requestAboutSosOwn = "als SOS-Kontakt gefragt"
    override val sharingsTitle = "Freigaben"
    override val sharingsFooter = "Was ihr miteinander vereinbart habt. Beenden gilt für euch beide."
    override val askAsSos = "Als SOS-Kontakt fragen"
    override val requestsShow = "Anzeigen"
    override val requestsHide = "Ausblenden"
    override fun requestSent(name: String) = "Anfrage an $name gesendet. Ihr könnt euch schreiben, sobald $name zustimmt."
    override val requestPending = "Anfrage gesendet"
    override val shareContact = "Kontakt teilen"
    override fun shareContactWho(name: String) = "Wem möchtest du $name vorstellen?"
    override fun shareContactHint(name: String) = "$name wird gefragt, bevor ihr euch schreiben könnt."
    override val noOtherContacts = "Du hast noch keine anderen Kontakte."
    override fun introSent(name: String, to: String) = "Du hast $name an $to weitergegeben."
    override val introFailed = "Das hat nicht geklappt. Versuch es später noch einmal."
    override val introExpired = "Diese Vorstellung gilt nicht mehr."
    override fun introAcceptedLine(to: String, name: String) = "$to möchte $name hinzufügen. $name wird gefragt."
    override fun introCardIncoming(by: String, name: String) = "$by stellt dir $name vor"
    override fun introCardOwn(name: String) = "Du hast $name vorgestellt"
    override val introAdd = "Hinzufügen"
    override fun introConfirmText(name: String) = "$name sieht deinen Namen und dein Bild. Ihr könnt euch schreiben, sobald $name zustimmt."
    override val alreadyContact = "Schon in deinen Kontakten"
}

internal object PeopleTextsEn : PeopleTexts {
    override val scanQrCode = "Scan QR code"
    override val notAContactYet = "Not a contact yet"
    override val radioOnlyContact = "Doesn't use $APP. Reachable by radio only, without photos or files."
    override val shareLink = "Share link"

    override fun circleCount(n: Int) = if (n == 1) "1 circle" else "$n circles"
    override val startOrJoinCircle = "Start or join a circle"
    override val noCircleYet = "No circle yet. Start one or join with an invite."
    override val searchCircles = "Search circles"
    override val yourCircles = "Your circles"
    override val startNewCircle = "Start a new circle"
    override val joinWithInviteLink = "Join with an invite link"
    override val inviteFromOtherPhone = "Invite from another phone"
    override val joinCircle = "Join a circle"
    override val inviteLink = "Invite link"
    override val startCircle = "Start a circle"
    override val circleName = "Circle name"
    override val circleNamePlaceholder = "e.g. Friends"

    override val invite = "Invite"
    override val edit = "Edit"
    override val removedFromThisCircle = "You were removed from this circle."
    override val members = "Members"
    override val noMembersYet = "Nobody else yet. People who join show up here once their app has been online."
    override fun ownMember(name: String) = "${name.ifBlank { "You" }} (you)"
    override fun ownDevicesInCircle(count: Int) = "On $count devices"
    override val invalidateOldInviteLinks = "Turn off old invite links"
    override val removeFromList = "Remove from list"
    override val leaveCircle = "Leave circle"
    override fun removeMemberTitle(name: String) = "Remove $name?"
    override fun removeMemberText(name: String, circle: String) =
        "$name won't get any more messages from “$circle”. " +
            "Everyone in the circle sees that you removed $name and can invite people again. " +
            "Earlier invite links will stop working."
    override val invalidateInviteLinksTitle = "Turn off invite links?"
    override val invalidateInviteLinksText =
        "Useful if a link was forwarded to the wrong person. Everyone already in the circle " +
            "stays in. Anyone who hasn't opened an old link yet needs a new one."
    override val invalidate = "Turn off"
    override val summaryRemoved = "You were removed"
    override val internetOnlyShort = "internet only"
    override val internetOnlyExplained =
        "There's no room for this circle on your radio right now. It runs over the internet and gets a place by itself as soon as there's more going on here."
    override val summaryNobodyInvited = "Nobody invited yet"
    override fun summaryYouAndOthers(n: Int) = if (n == 1) "You and 1 other" else "You and $n others"
    override val removeCircleTitle = "Remove circle?"
    override fun leaveCircleTitle(name: String) = "Leave “$name”?"
    override val removeCircleText = "The circle disappears from your list."
    override val leaveCircleText =
        "You won't get any more messages from this circle, and its chat will be deleted from your phone. " +
            "Your direct messages are kept. You can only come back with a new invite."
    override val leaveCircleLastNote =
        "Nobody else is left in this circle, so its files are deleted too, for good."
    override val editCircle = "Edit circle"
    override val everyoneSeesChange = "Everyone in the circle sees the change."

    override val addContact = "Add contact"
    override val noContactsYet = "Nobody here yet. People who join one of your circles show up here automatically."
    override val noCircleSelfAdded = "No circle · direct contact"
    override val pasteLink = "Paste link"
    override val pasteLinkHint = "The contact link you got"
    override val scanContactHint = "Scan another person's contact or invite"
    override val myContact = "My contact"
    override val myContactHint = "Share your contact with others, as a link or QR code"
    override val inviteFromPhone = "Invite from phone contacts"
    override val inviteFromPhoneHint = "By text message with your contact link"
    override val inviteFromPhoneWhy =
        "$APP doesn't know your phone number. So no one can find you by it, and you decide who to invite."
    override val inviteFromPhoneHow =
        "Choose someone from your contacts. They get a text message with your contact link. Their number stays on your phone."
    override val pickPhoneContact = "Choose person"
    override val addContactText =
        "For people outside your circles: tap the contact link you got, scan the QR code or paste the link here. " +
            "People from your circles show up automatically."
    override fun keyMismatch(name: String) =
        "Careful: this code doesn't match your saved contact $name. " +
            "Please check with the person directly before you use it."
    override val ownContact = "That's your own contact."
    override val preparingContact = "One moment, getting your contact ready …"
    override val publicContact = "Link to publish"
    override val publicContactText = "For the app, the web app and the website. Valid for a year. Withdraw it if unwanted messages arrive."
    override val publicContactRevoke = "Withdraw"
    override val ownContactText =
        "With this code or link, someone outside your circles can add you as a contact and send you " +
            "end-to-end encrypted messages. It contains no passwords."
    override val copied = "Copied"

    override val selfAdded = "Added by you"
    override val reachableBy = "Reachable by"
    override val viaApp = "through the $APP app"
    override fun radioNamed(name: String) = "Radio “$name”"
    override val notKnownYet = "Not known yet"
    override val sharedCircles = "Circles in common"
    override fun lastSeen(at: String) = "Last reachable $at"

    override fun inviteTo(circle: String) = "Invite to “$circle”"
    override val qrInviteHelp =
        "The person opens their phone's camera and points it at the code. " +
            "If $APP is installed, the invite opens right away."
    override val inviteWarning = "Anyone who gets the link or code can join the circle. Only give it to people who should be part of it."
    override val shareLinkHint = "By WhatsApp, Signal, SMS … if the person isn't with you."
    override val showQrCode = "Show QR code"
    override val showQrCodeHint = "If the person is right next to you."
    override val qrCode = "QR code"

    override val notAppCode = "That's not a $APP code."
    override val scanHelp = "Point the camera at the QR code of an invite or contact."
    override val cameraNeededAndroid = "$APP needs the camera to scan. You can allow it for $APP in your phone's settings."
    override val cameraNeededIos = "$APP needs the camera to scan. You can allow it for $APP in your iPhone's settings."
    override val viewPerson = "View person"
    override val leaveCircleContactsNote = "People stay in your contacts if they are saved directly, you have already messaged each other directly, or you share another circle."
    override val editNameAndPicture = "Change name and picture"
    override val changeName = "Change name"
    override val contactNameLabel = "Name"
    override val contactNameHint = "Only you see this name, on all your devices. The person won't notice."
    override val resetName = "Reset"
    override fun profileNameLine(name: String) = "In profile: $name"
    override val removeMember = "Remove member"
    override val whoToRemove = "Remove whom?"
    override val circleExplained = "A circle is a fixed group, for example your family or your neighbors. Everyone in the circle can read along."
    override val acceptInvite = "Accept invite"
    override val pasteInviteLinkHint = "The invite link you received"
    override val shareMyContact = "Share my contact"
    override val openConversation = "Open conversation"
    override val radioOnly = "radio only"
    override val lastReached = "Last contacted"
    override val lastReachedFooter = "When this person last received a message."
    override fun nothingReceivedFrom(name: String) = "Nothing received from $name yet"
    override val viaInternetAndRadio = "over the internet and radio"
    override val viaRadio = "over radio"
    override val viaInternet = "over the internet"
    override val removeContact = "Remove contact"
    override val removeRadioContactText = "The radio will no longer be in your contacts. You can keep your conversation or delete it too."
    override val inviteToCircle = "Invite to a circle"
    override fun inviteWhichCircle(name: String) = "Invite $name to which circle?"
    override fun staysWhileSharedCircle(name: String) = "$name stays in your contacts as long as you share a circle."
    override fun removeDirectContactText(name: String) = "$name will no longer be in your contacts and can no longer write to you directly. You can connect again via the contact link. You can keep your conversation or delete it too."
    override fun unreachableText(name: String) = "$name can no longer be reached on anynear. The account may have been deleted."
    override val unreachableRemove = "Remove contact"
    override val unreachableKeep = "Keep"
    override val unreachableShort = "unreachable"
    override fun removeDirectContactInCircleText(name: String, circles: String) = "$name will no longer be saved as a direct contact. Because you share a circle ($circles), $name stays visible there as a member and you can keep writing to each other. If you message each other directly again, $name counts as your contact again. You can keep your conversation or delete it too."
    override val removeKeepHistory = "Remove contact only"
    override val removeWithHistory = "Delete contact and conversation"
    override val deleteHistory = "Delete conversation"
    override fun deleteHistoryTitle(name: String) = "Delete conversation with $name?"
    override fun deleteHistoryText(name: String) = "All messages, photos and files from your conversation disappear from this phone. $name keeps the conversation."

    override val overRadio = "Over radio"
    override val checkOverRadio = "Check over radio"
    override val whoAnswersOverRadio = "Who answers over radio?"
    override fun radioOf(users: List<String>, radioName: String) = when (users.size) {
        0 -> "Radio “$radioName”"
        1 -> "${users.single()}’s radio"
        else -> "${users.dropLast(1).joinToString(", ")} and ${users.last()}’s radio"
    }
    override fun radioAnswersDirect(radio: String) = "$radio answers. Directly."
    override fun radioAnswersVia(radio: String, others: Int) =
        if (others == 1) "$radio answers. Via one other radio." else "$radio answers. Via $others other radios."
    override fun radioAnswers(radio: String) = "$radio answers."
    override fun radioNoAnswer(radio: String) = "$radio did not answer."
    override fun radioChecking(radio: String) = "Checking $radio …"
    override fun noRadioKnown(name: String) = "$APP doesn’t know a radio of $name yet."
    override val connectRadioFirst = "Connect your radio first."
    override val radioNotSetUp = "Your radio isn’t set up for $APP yet."
    override fun checkAgainFrom(time: String) = "You can check again from $time."
    override val noAnswerExplained = "This doesn’t mean the radio can’t be reached. The packet may have got lost on the way, or the radio may be off right now. Try again later."
    override val radioCheckFooter = "This checks the same path your messages take, including other radios on the way. The other person won’t notice."
    override fun answersCount(answered: Int, total: Int) = "$answered of $total answer"
    override val groupCheckRunning = "Checking. This takes a while, one after the other."
    override val noRadiosKnownHere = "$APP doesn’t know a radio of anyone here."
    override fun signalLine(level: app.anynear.app.SignalLevel, snr: Float, rssi: Int?): String {
        val word = when (level) {
            app.anynear.app.SignalLevel.GOOD -> "good"
            app.anynear.app.SignalLevel.MEDIUM -> "medium"
            app.anynear.app.SignalLevel.WEAK -> "weak"
        }
        return "Signal on the last step: $word ($snr dB${rssi?.let { ", $it dBm" }.orEmpty()})"
    }
    override fun forwardsLine(hops: Int) = when (hops) {
        0 -> "Directly, not forwarded"
        1 -> "Via 1 other radio (way back)"
        else -> "Via $hops other radios (way back)"
    }
    override fun answerAfter(seconds: Long) = "Answer after $seconds s"
    override fun checkedAt(time: String) = "Checked: $time"
    override fun radioNumber(id: String) = "Radio $id"
    override fun requestTitle(name: String) = "$name would like to be your contact"
    override fun introducedBy(name: String) = "Introduced by $name"
    override val viaYourContactLink = "Through your contact link"
    override val requestAgree = "Accept"
    override val requestDecline = "Decline"
    override fun requestAcceptedLine(name: String) = "You added $name. You can now message each other."
    override fun requestAnsweredLine(name: String) = "$name accepted. You can now message each other."
    override fun requestsTitle(count: Int) = "$count contact requests"
    override val requestsPage = "Requests"
    override fun requestsRow(count: Int) = if (count == 1) "1 request is waiting for you" else "$count requests are waiting for you"
    override val requestsToYou = "To you"
    override val requestsFromYou = "From you"
    override val requestsNone = "Nothing is open right now."
    override val requestAboutContact = "would like to be a contact"
    override fun requestAboutLocation(exact: Boolean) = if (exact) "Share location: precisely" else "Share location: approximately"
    override val requestAboutSos = "asks whether you'll be their SOS contact"
    override val requestAboutSosOwn = "asked to be your SOS contact"
    override val sharingsTitle = "Sharing"
    override val sharingsFooter = "What you agreed on with each other. Stopping it holds for both of you."
    override val askAsSos = "Ask to be SOS contact"
    override val requestsShow = "Show"
    override val requestsHide = "Hide"
    override fun requestSent(name: String) = "Request sent to $name. You can message each other once $name accepts."
    override val requestPending = "Request sent"
    override val shareContact = "Share contact"
    override fun shareContactWho(name: String) = "Who would you like to introduce $name to?"
    override fun shareContactHint(name: String) = "$name will be asked before you can message each other."
    override val noOtherContacts = "You have no other contacts yet."
    override fun introSent(name: String, to: String) = "You passed $name on to $to."
    override val introFailed = "That didn't work. Please try again later."
    override val introExpired = "This introduction is no longer valid."
    override fun introAcceptedLine(to: String, name: String) = "$to would like to add $name. $name will be asked."
    override fun introCardIncoming(by: String, name: String) = "$by is introducing $name to you"
    override fun introCardOwn(name: String) = "You introduced $name"
    override val introAdd = "Add"
    override fun introConfirmText(name: String) = "$name will see your name and picture. You can message each other once $name accepts."
    override val alreadyContact = "Already in your contacts"
}
