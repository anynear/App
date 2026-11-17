package app.anynear.i18n

/** Messages, conversations, attachments, message details. */
interface ChatTexts {
    // Message list
    val messages: String
    /** „1 neue Nachricht“, „3 neue Nachrichten“ */
    fun newMessages(n: Int): String
    val search: String
    val newMessage: String
    /** For screen readers after the name: „Familie, Kreis“. */
    val circle: String
    val noLongerInCircle: String
    val noMessagesYet: String
    val loadingMessages: String

    // New message
    val whoToMessage: String

    /** Shared from another app: header and question of the selection of who it goes to. */
    val shareTitle: String
    val shareQuestion: String
    val forwardTitle: String
    val forwardQuestion: String
    val searchName: String
    val circles: String
    val contacts: String
    val writeInCircle: String
    /** Contact without a shared circle: „direkter Kontakt“ (no matter who added whom). */
    val addedByYou: String
    val noContactsYet: String
    val addContact: String
    val startCircle: String

    // Conversation
    val directMessage: String

    /** Strip at the top of a direct conversation with a radio without the app (e.g. Meshtastic). */
    val radioOnlyConversation: String
    val viewContact: String
    val viewCircle: String
    val inviteToThisCircle: String
    val fileTooLarge: String
    val fileUnreadable: String
    val photoUnreadable: String
    fun maxAttachments(n: Int): String
    fun aboLongerText(plan: String, chars: Int): String
    fun aboBiggerAttachments(plan: String, mb: Int): String
    fun aboMoreAttachments(plan: String, n: Int): String
    fun aboLongerVoice(plan: String, minutes: Int): String
    val aboSeePlans: String
    val aboPlansTitle: String
    val preparing: String
    val attachPhotoOrFile: String
    val file: String
    val defaultFileName: String
    val photo: String
    val takePhoto: String
    val pasteImage: String
    val writeMessage: String
    val captionOptional: String
    val send: String
    val reinvite: String
    /** Message over radio, attachments follow over the internet. */
    val arrivesWhenOnline: String
    val details: String
    val showDetails: String
    val hideDetails: String

    // Empty circle
    val inviteSomeoneTitle: String
    val inviteSomeoneText: String
    val invite: String

    // Reactions
    /** The own person in „👍 von Anna, dir“. */
    val reactionYou: String
    /** „👍 von Anna, dir“ */
    fun reactedBy(emoji: String, names: List<String>): String
    val reactTheSame: String
    val removeReaction: String
    fun reactWith(emoji: String): String
    fun yourReaction(emoji: String): String
    /** Line in the notification, after the name: „hat mit 👍 auf „Hallo“ reagiert“ */
    fun reactedTo(emoji: String, text: String): String
    /** Like [reactedTo], when the text of the message is not known (iOS after waking). */
    fun reactedToYours(emoji: String): String

    // Attachments
    val loadsWhenOnline: String
    val noLongerAvailable: String
    val fileCantOpen: String
    /** Displayed name of a PDF without a name. */
    val defaultPdfName: String
    val saveFile: String
    val download: String
    val toFiles: String

    /** Forward a message inside anynear to another person or circle. */
    val forward: String

    /** Hand a message to the system's share menu, only via this explicit button. */
    val shareMessage: String

    /** Mark of a passed on message and of such a draft (ADR 0118). */
    val passedOn: String
    fun downloadTitle(count: Int): String
    fun downloadText(count: Int): String
    val savePhoto: String
    /** „2 von 5“ */
    fun pageOf(page: Int, count: Int): String
    val removeAttachment: String
    val attachmentGone: String
    /** „Gespeichert in „Bilder › anynear““ */
    fun savedIn(where: String): String
    val saveFailed: String
    /** Folder of the photos on Android, as the gallery calls it. */
    val picturesFolder: String

    // Voice messages (ADR 0030)
    val recordVoice: String
    val voiceMessage: String
    val discardRecording: String
    val finishRecording: String
    val play: String
    val pause: String
    val micDenied: String
    val recordingFailed: String
    /** Recording has reached the maximum and is saved as a draft. */
    fun voiceLimitReached(minutes: Int): String
    /** Recording ended because the app went to the background (screen off); saved as a draft, not sent. */
    val voiceStoppedInBackground: String
    val voiceCantPlay: String
    /** Above the automatic text preview of a voice message (ADR 0030). */
    val textPreview: String
    val textPreviewShortened: String
    val textPreviewInProgress: String
    /** No text preview, radio paired: own text also goes over radio. */
    val noTextPreview: String
    /** „2,4 MB“ from tenths of an MB. */
    fun megabytes(tenths: Long): String

    // Images of people and circles
    val circlePicture: String
    val personPicture: String
    val enlargeCirclePicture: String
    val enlargePersonPicture: String
    val photoTooLarge: String
    val photoCantRead: String

    // General building blocks
    val expand: String
    val collapse: String
    val clearSearch: String
    val nothingFound: String

    // Search in the conversation
    val searchInChat: String
    val closeSearch: String
    val searchInChatHint: String
    /** „1 Treffer“, „3 Treffer“ */
    fun searchMatches(n: Int): String

    // Photos and files of a conversation (detail page of circle and person)
    val photosAndFiles: String
    val noFilesYet: String
    /** „Alle 12 ansehen“ */
    fun showAllFiles(n: Int): String
    val photos: String
    val documents: String
    val voiceMessages: String

    // Character limit
    /** „3 Zeichen zu viel“ */
    fun charsTooMany(n: Int): String
    /** „Noch 8 Zeichen“ */
    fun charsLeft(n: Int): String
    /** Below the input field when the text is too long for radio; the rest is marked in the field. */
    val radioShortened: String

    // Connection to the radio
    val linkNoDevice: String
    val linkBluetoothUnavailable: String
    val linkSearching: String
    val linkConnecting: String
    val linkConnected: String
    val linkLost: String
    val linkInUseElsewhere: String
    val linkInUseElsewhereHint: String
    val linkSearchingHint: String
    fun linkBluetoothHint(appName: String): String
    fun linkConnectedTo(deviceName: String): String
    fun radioStatus(state: String): String
    val linkLostHint: String

    // State of a message
    val statusSending: String
    val statusHandedToRadio: String
    val statusSentAwaitingConfirmation: String
    val statusSent: String
    val statusDelivered: String
    val statusRead: String
    val statusSentNoConfirmation: String
    val statusNotSent: String

    // Message details
    /** „Gesendet über Internet“: word before the path. */
    val via: String
    fun sentAt(time: String): String
    fun receivedAt(time: String): String
    val relayedByOther: String
    val deliveredToAll: String
    fun deliveredToSome(n: Int, total: Int): String
    /** After a name: „Anna: angekommen“. */
    val deliveredLower: String
    /** After a name: „Ben: noch nicht“. */
    val notYet: String
    val readByAll: String
    val readByNone: String
    fun readBySome(n: Int, total: Int): String
    fun deliveredAt(time: String): String
    val notDeliveredYet: String
    fun readAt(time: String): String
    val notReadYet: String
    val viewProfile: String
    val chatActions: String
    fun alreadyInCircle(circle: String): String
    val showDetailsAndReactions: String
    val react: String
    val emptyChatList: String
    val allowNotificationsTitle: String
    val allowNotificationsText: String
    /** Card when a radio is on record but Bluetooth is not allowed (e.g. after reinstalling). */
    val allowBluetoothTitle: String
    val allowBluetoothText: String
    val allow: String
    val emptyNobodyYet: String
    val emptyInviteHint: String
    fun firstMessageTo(name: String): String
    fun circleReadsAlong(circle: String): String

    // Invitation in a message: card instead of link (sentence with the name from the bubble, without „sein/ihr“).
    /** „Alex lädt dich zum Kanal „Wetter“ ein.“ */
    fun cardChannel(sender: String, channel: String): String
    fun cardChannelOwn(channel: String): String
    /** Channel with official warnings. */
    fun cardOfficial(sender: String): String
    val cardOfficialOwn: String
    /** „Alex teilt einen Kontakt: Ben“ */
    fun cardContact(sender: String, contact: String): String
    fun cardContactOwn(contact: String): String
    /** Sender shares their own contact: „Alex teilt den eigenen Kontakt.“ */
    fun cardContactSelf(sender: String): String
    val cardContactSelfOwn: String
    /** „Alex lädt dich in den Kreis „Familie“ ein.“ */
    fun cardCircle(sender: String, circle: String): String
    fun cardCircleOwn(circle: String): String
    fun cardDevice(sender: String, device: String): String
    fun cardDeviceOwn(device: String): String
    val cardAddDevice: String
    val cardDeviceAdded: String
    val cardThisDevice: String
    /** Broken link or one from another network. */
    val cardBroken: String
    val cardSubscribe: String
    val cardSubscribed: String
    val cardAdd: String
    val cardInContacts: String
    val cardThisIsYou: String
    val cardJoin: String
    val cardInCircle: String
    val cardOpen: String
    val delete: String
    val deleteForEveryone: String
    val deleteForMe: String
    val deleteHint: String
    fun deletedBy(name: String): String
    val deletedByYou: String
    val deletedMessage: String
    val removePreview: String
    val reply: String
    fun replyTo(name: String): String
    val replyToYou: String
    val you: String
    val cancelReply: String
    val showAnsweredMessage: String
    val replyUnavailable: String
    val answeredMessageGone: String
    /** Text of an introduction for apps that do not know introductions yet (ADR 0116). */
    fun introStandIn(name: String): String
}

internal object ChatTextsDe : ChatTexts {
    override val messages = "Nachrichten"
    override fun newMessages(n: Int) = if (n == 1) "1 neue Nachricht" else "$n neue Nachrichten"
    override val search = "Suchen"
    override val newMessage = "Neue Nachricht"
    override val circle = "Kreis"
    override val noLongerInCircle = "Du bist nicht mehr in diesem Kreis"
    override val noMessagesYet = "Noch keine Nachrichten"
    override val loadingMessages = "Wird geladen …"

    override val whoToMessage = "Wem möchtest du schreiben?"
    override val shareTitle = "Teilen"
    override val shareQuestion = "An wen soll das gehen?"
    override val forwardTitle = "Weiterleiten"
    override val forwardQuestion = "An wen weiterleiten?"
    override val searchName = "Name suchen"
    override val circles = "Kreise"
    override val contacts = "Kontakte"
    override val writeInCircle = "Im Kreis schreiben"
    override val addedByYou = "direkter Kontakt"
    override val noContactsYet =
        "Noch keine Kontakte. Füge jemanden hinzu, dem du direkt schreiben möchtest. Wer in einem deiner Kreise ist, erscheint hier automatisch."
    override val addContact = "Kontakt hinzufügen"
    override val startCircle = "Kreis gründen"

    override val directMessage = "Direktnachricht"
    override val radioOnlyConversation = "Funkgerät ohne $APP: Nachrichten gehen nur über Funk, kurz und ohne Bilder oder Dateien."
    override val viewContact = "Kontakt ansehen"
    override val viewCircle = "Kreis ansehen"
    override val inviteToThisCircle = "In diesen Kreis einladen"
    override val fileTooLarge = "Die Datei ist zu groß (höchstens 10 MB)."
    override val fileUnreadable = "Eine Datei ließ sich nicht lesen."
    override val photoUnreadable = "Ein Foto ließ sich nicht lesen."
    override fun maxAttachments(n: Int) = "Höchstens $n Anhänge pro Nachricht."
    override fun aboLongerText(plan: String, chars: Int) = "Mit $plan bis zu ${grouped(chars, ' ')} Zeichen."
    override fun aboBiggerAttachments(plan: String, mb: Int) = "Mit $plan Anhänge bis je $mb MB."
    override fun aboMoreAttachments(plan: String, n: Int) = "Mit $plan bis zu $n Anhänge pro Nachricht."
    override fun aboLongerVoice(plan: String, minutes: Int) = "Mit $plan Sprachnachrichten bis zu $minutes Minuten."
    override val aboSeePlans = "Pakete ansehen"
    override val aboPlansTitle = "Pakete"
    override val preparing = "Wird vorbereitet …"
    override val attachPhotoOrFile = "Foto oder Datei anhängen"
    override val file = "Datei"
    override val defaultFileName = "Datei"
    override val photo = "Foto"
    override val takePhoto = "Foto aufnehmen"
    override val pasteImage = "Bild einfügen"
    override val writeMessage = "Nachricht schreiben"
    override val captionOptional = "Text dazu (optional)"
    override val send = "Senden"
    override val reinvite = "Wieder einladen"
    override val arrivesWhenOnline = "Kommt vollständig, sobald Internet da ist."
    override val details = "Einzelheiten"
    override val showDetails = "Einzelheiten zeigen"
    override val hideDetails = "Einzelheiten ausblenden"

    override val inviteSomeoneTitle = "Lade jemanden ein"
    override val inviteSomeoneText =
        "Noch bist nur du in diesem Kreis. Schick den Einladungslink, z. B. per WhatsApp. Wer darauf tippt, kann dann beitreten."
    override val invite = "Einladen"

    override val reactionYou = "dir"
    override fun reactedBy(emoji: String, names: List<String>) = "$emoji von ${names.joinToString(", ")}"
    override val reactTheSame = "Genauso reagieren"
    override val removeReaction = "Reaktion zurücknehmen"
    override fun reactWith(emoji: String) = "Mit $emoji reagieren"
    override fun yourReaction(emoji: String) = "$emoji, deine Reaktion"
    override fun reactedTo(emoji: String, text: String) = "hat mit $emoji auf „$text“ reagiert"
    override fun reactedToYours(emoji: String) = "hat mit $emoji auf deine Nachricht reagiert"

    override val loadsWhenOnline = "Wird geladen, sobald Internet da ist"
    override val noLongerAvailable = "Nicht mehr verfügbar"
    override val fileCantOpen = "Diese Datei lässt sich auf dem Handy gerade nicht öffnen."
    override val defaultPdfName = "Dokument.pdf"
    override val saveFile = "Datei auf dem Handy speichern"
    override val download = "Herunterladen"
    override val toFiles = "In Ablage speichern"
    override val forward = "Weiterleiten"
    override val shareMessage = "Teilen…"
    override val passedOn = "Weitergeleitet"
    override fun downloadTitle(count: Int) = if (count == 1) "Möchtest du die Datei herunterladen?" else "Möchtest du die $count Dateien herunterladen?"
    override fun downloadText(count: Int) = if (count == 1) "Sie wird auf deinem Handy gespeichert." else "Sie werden auf deinem Handy gespeichert."
    override val savePhoto = "Foto auf dem Handy speichern"
    override fun pageOf(page: Int, count: Int) = "$page von $count"
    override val removeAttachment = "Anhang entfernen"
    override val attachmentGone = "Dieser Anhang ist nicht mehr auf dem Handy."
    override fun savedIn(where: String) = "Gespeichert in „$where“"
    override val saveFailed = "Speichern hat nicht geklappt."
    override val picturesFolder = "Bilder"

    override val recordVoice = "Sprachnachricht aufnehmen"
    override val voiceMessage = "Sprachnachricht"
    override val discardRecording = "Verwerfen"
    override val finishRecording = "Fertig"
    override val play = "Abspielen"
    override val pause = "Anhalten"
    override val micDenied = "Für Sprachnachrichten braucht die App das Mikrofon. Du kannst es in den Einstellungen des Handys erlauben."
    override val recordingFailed = "Die Aufnahme hat nicht geklappt."
    override fun voiceLimitReached(minutes: Int) = "Höchstens $minutes Minuten. Die Aufnahme ist gespeichert."
    override val voiceStoppedInBackground = "Aufnahme beendet, weil die App geschlossen wurde. Du kannst sie jetzt senden."
    override val voiceCantPlay = "Die Sprachnachricht lässt sich gerade nicht abspielen."
    override val textPreview = "Textvorschau"
    override val textPreviewShortened = "Textvorschau · gekürzt"
    override val textPreviewInProgress = "Textvorschau wird erstellt …"
    override val noTextPreview = "Ohne Textvorschau. Schreib ein paar Worte dazu, wenn du magst – sie gehen auch über Funk."
    override fun megabytes(tenths: Long) = "${tenths / 10},${tenths % 10} MB"

    override val circlePicture = "Bild des Kreises"
    override val personPicture = "Bild der Person"
    override val enlargeCirclePicture = "Bild des Kreises vergrößern"
    override val enlargePersonPicture = "Bild der Person vergrößern"
    override val photoTooLarge = "Das Foto ist zu groß."
    override val photoCantRead = "Das Foto ließ sich nicht lesen."

    override val expand = "Aufklappen"
    override val collapse = "Zuklappen"
    override val clearSearch = "Suche leeren"
    override val nothingFound = "Nichts gefunden."

    override val searchInChat = "Im Chat suchen"
    override val closeSearch = "Suche schließen"
    override val searchInChatHint = "Gib ein Wort ein, das in einer Nachricht vorkommt. Tippe auf einen Treffer, um ihn im Chat zu sehen."
    override fun searchMatches(n: Int) = "$n Treffer"

    override val photosAndFiles = "Fotos und Dateien"
    override val noFilesYet = "Im Chat wurden noch keine Fotos oder Dateien verschickt."
    override fun showAllFiles(n: Int) = "Alle $n ansehen"
    override val photos = "Fotos"
    override val documents = "Dokumente"
    override val voiceMessages = "Sprachnachrichten"

    override fun charsTooMany(n: Int) = "$n Zeichen zu viel"
    override fun charsLeft(n: Int) = "Noch $n Zeichen"
    override val radioShortened = "Über Funk kommt nur der Anfang an, der markierte Teil erst übers Internet."

    override val linkNoDevice = "Kein Funkgerät eingerichtet"
    override val linkBluetoothUnavailable = "Bluetooth ist aus oder nicht erlaubt"
    override val linkSearching = "Funkgerät wird gesucht …"
    override val linkConnecting = "Verbindung wird aufgebaut …"
    override val linkConnected = "Verbunden"
    override val linkLost = "Verbindung verloren, wird wiederhergestellt …"
    override val linkInUseElsewhere = "Funkgerät wird woanders genutzt"
    override val linkInUseElsewhereHint = "Unter „Funkgerät“ kannst du es hier verbinden."
    override val linkSearchingHint = "Bitte Funkgerät einschalten, in die Nähe des Handys legen und Bluetooth am Handy einschalten."
    override fun linkBluetoothHint(appName: String) = "Bitte Bluetooth einschalten und $appName den Zugriff erlauben."
    override fun linkConnectedTo(deviceName: String) = "Verbunden mit „$deviceName“"
    override fun radioStatus(state: String) = "Funkgerät: $state"
    override val linkLostHint = "Bitte Funkgerät eingeschaltet in der Nähe lassen."

    override val statusSending = "Wird gesendet …"
    override val statusHandedToRadio = "An Funkgerät übergeben"
    override val statusSentAwaitingConfirmation = "Gesendet, warte auf Bestätigung"
    override val statusSent = "Gesendet"
    override val statusDelivered = "Angekommen"
    override val statusRead = "Gelesen"
    override val statusSentNoConfirmation = "Gesendet, keine Bestätigung"
    override val statusNotSent = "Nicht gesendet"

    override val via = "über"
    override fun sentAt(time: String) = "Gesendet: $time"
    override fun receivedAt(time: String) = "Empfangen: $time"
    override val relayedByOther = "Über Funk gesendet, von einem anderen $APP-Gerät weitergereicht"
    override val deliveredToAll = "Angekommen bei allen:"
    override fun deliveredToSome(n: Int, total: Int) = "Angekommen bei $n von $total:"
    override val deliveredLower = "angekommen"
    override val notYet = "noch nicht"
    override val readByAll = "Von allen gelesen"
    override val readByNone = "Noch von niemandem gelesen"
    override fun readBySome(n: Int, total: Int) = "Von $n von $total gelesen"
    override fun deliveredAt(time: String) = "Angekommen: $time"
    override val notDeliveredYet = "Noch nicht angekommen"
    override fun readAt(time: String) = "Gelesen: $time"
    override val notReadYet = "Noch nicht gelesen"
    override val viewProfile = "Profil ansehen"
    override val chatActions = "Mehr Aktionen"
    override fun alreadyInCircle(circle: String) = "Schon in „$circle“"
    override val showDetailsAndReactions = "Einzelheiten und Reaktionen zeigen"
    override val react = "Reagieren"
    override val emptyChatList = "Noch keine Nachrichten. Tippe auf „Neue Nachricht“, um jemandem zu schreiben."
    override val allowNotificationsTitle = "Benachrichtigungen erlauben?"
    override val allowNotificationsText = "Dann zeigt dir dein Handy neue Nachrichten, auch wenn $APP geschlossen ist."
    override val allowBluetoothTitle = "Bluetooth erlauben?"
    override val allowBluetoothText = "Dein Funkgerät ist hinterlegt. Damit $APP es erreicht, braucht die App Zugriff auf Bluetooth."
    override val allow = "Erlauben"
    override val emptyNobodyYet = "Hier erscheinen deine Gespräche. Füge zuerst einen Kontakt hinzu oder gründe einen Kreis."
    override val emptyInviteHint = "Du hast eine Einladung bekommen? Tippe einfach auf den Link."
    override fun firstMessageTo(name: String) = "Schreib $name die erste Nachricht."
    override fun circleReadsAlong(circle: String) = "Alle im Kreis „$circle“ lesen mit."

    override fun cardChannel(sender: String, channel: String) = "$sender lädt dich zum Kanal „$channel“ ein."
    override fun cardChannelOwn(channel: String) = "Du hast zum Kanal „$channel“ eingeladen."
    override fun cardOfficial(sender: String) = "$sender lädt dich zu den amtlichen Warnungen ein."
    override val cardOfficialOwn = "Du hast zu den amtlichen Warnungen eingeladen."
    override fun cardContact(sender: String, contact: String) = "$sender teilt einen Kontakt: $contact"
    override fun cardContactOwn(contact: String) = "Du hast einen Kontakt geteilt: $contact"
    override fun cardContactSelf(sender: String) = "$sender teilt den eigenen Kontakt."
    override val cardContactSelfOwn = "Du hast deinen Kontakt geteilt."
    override fun cardCircle(sender: String, circle: String) = "$sender lädt dich in den Kreis „$circle“ ein."
    override fun cardCircleOwn(circle: String) = "Du hast in den Kreis „$circle“ eingeladen."
    override fun cardDevice(sender: String, device: String) = if (device.isBlank()) "$sender teilt den Code eines neuen Geräts." else "$sender teilt den Code des neuen Geräts „$device“."
    override fun cardDeviceOwn(device: String) = if (device.isBlank()) "Du hast den Code eines neuen Geräts geteilt." else "Du hast den Code des neuen Geräts „$device“ geteilt."
    override val cardAddDevice = "Gerät hinzufügen"
    override val cardDeviceAdded = "Schon eines deiner Geräte"
    override val cardThisDevice = "Das ist dieses Gerät."
    override val cardBroken = "Diese Einladung lässt sich nicht öffnen."
    override val cardSubscribe = "Abonnieren"
    override val cardSubscribed = "Abonniert"
    override val cardAdd = "Hinzufügen"
    override val cardInContacts = "Schon in deinen Kontakten"
    override val cardThisIsYou = "Das bist du."
    override val cardJoin = "Beitreten"
    override val cardInCircle = "Du bist schon im Kreis."
    override val cardOpen = "Öffnen"
    override val delete = "Löschen"
    override val deleteForEveryone = "Für alle löschen"
    override val deleteForMe = "Nur für mich löschen"
    override val deleteHint = "Wer die Nachricht schon gesehen oder gespeichert hat, kann sie behalten."
    override fun deletedBy(name: String) = "Von $name gelöscht"
    override val deletedByYou = "Von dir gelöscht"
    override val deletedMessage = "Gelöschte Nachricht"
    override val removePreview = "Vorschau entfernen"
    override val reply = "Antworten"
    override fun replyTo(name: String) = "Antwort an $name"
    override val replyToYou = "Antwort an dich"
    override val you = "Du"
    override val cancelReply = "Antwort abbrechen"
    override val showAnsweredMessage = "Zur Nachricht springen"
    override val replyUnavailable = "Nachricht nicht verfügbar"
    override val answeredMessageGone = "Die Nachricht ist auf diesem Gerät nicht mehr da."
    override fun introStandIn(name: String) = "Kontakt: $name"
}

internal object ChatTextsEn : ChatTexts {
    override val messages = "Messages"
    override fun newMessages(n: Int) = if (n == 1) "1 new message" else "$n new messages"
    override val search = "Search"
    override val newMessage = "New message"
    override val circle = "circle"
    override val noLongerInCircle = "You're no longer in this circle"
    override val noMessagesYet = "No messages yet"
    override val loadingMessages = "Loading …"

    override val whoToMessage = "Who do you want to message?"
    override val shareTitle = "Share"
    override val shareQuestion = "Who should get this?"
    override val forwardTitle = "Forward"
    override val forwardQuestion = "Forward to whom?"
    override val searchName = "Search by name"
    override val circles = "Circles"
    override val contacts = "Contacts"
    override val writeInCircle = "Message the circle"
    override val addedByYou = "direct contact"
    override val noContactsYet =
        "No contacts yet. Add someone you want to message directly. Anyone in one of your circles shows up here automatically."
    override val addContact = "Add contact"
    override val startCircle = "Start a circle"

    override val directMessage = "Direct message"
    override val radioOnlyConversation = "Radio without $APP: messages go by radio only, short and without photos or files."
    override val viewContact = "View contact"
    override val viewCircle = "View circle"
    override val inviteToThisCircle = "Invite to this circle"
    override val fileTooLarge = "The file is too large (10 MB max)."
    override val fileUnreadable = "A file couldn't be read."
    override val photoUnreadable = "A photo couldn't be read."
    override fun maxAttachments(n: Int) = "Up to $n attachments per message."
    override fun aboLongerText(plan: String, chars: Int) = "With $plan up to ${grouped(chars, ',')} characters."
    override fun aboBiggerAttachments(plan: String, mb: Int) = "With $plan attachments up to $mb MB each."
    override fun aboMoreAttachments(plan: String, n: Int) = "With $plan up to $n attachments per message."
    override fun aboLongerVoice(plan: String, minutes: Int) = "With $plan voice messages up to $minutes minutes."
    override val aboSeePlans = "See plans"
    override val aboPlansTitle = "Plans"
    override val preparing = "Preparing …"
    override val attachPhotoOrFile = "Attach photo or file"
    override val file = "File"
    override val defaultFileName = "File"
    override val photo = "Photo"
    override val takePhoto = "Take photo"
    override val pasteImage = "Paste image"
    override val writeMessage = "Write a message"
    override val captionOptional = "Add text (optional)"
    override val send = "Send"
    override val reinvite = "Invite again"
    override val arrivesWhenOnline = "Arrives in full once there's internet."
    override val details = "Details"
    override val showDetails = "Show details"
    override val hideDetails = "Hide details"

    override val inviteSomeoneTitle = "Invite someone"
    override val inviteSomeoneText =
        "So far it's just you in this circle. Send the invite link, for example by WhatsApp. Anyone who taps it can then join."
    override val invite = "Invite"

    override val reactionYou = "you"
    override fun reactedBy(emoji: String, names: List<String>) = "$emoji from ${names.joinToString(", ")}"
    override val reactTheSame = "React the same"
    override val removeReaction = "Remove reaction"
    override fun reactWith(emoji: String) = "React with $emoji"
    override fun yourReaction(emoji: String) = "$emoji, your reaction"
    override fun reactedTo(emoji: String, text: String) = "reacted $emoji to “$text”"
    override fun reactedToYours(emoji: String) = "reacted $emoji to your message"

    override val loadsWhenOnline = "Loads once there's internet"
    override val noLongerAvailable = "No longer available"
    override val fileCantOpen = "This file can't be opened on your phone right now."
    override val defaultPdfName = "Document.pdf"
    override val saveFile = "Save file to phone"
    override val download = "Download"
    override val toFiles = "Save to files"
    override val forward = "Forward"
    override val shareMessage = "Share…"
    override val passedOn = "Forwarded"
    override fun downloadTitle(count: Int) = if (count == 1) "Do you want to download the file?" else "Do you want to download the $count files?"
    override fun downloadText(count: Int) = if (count == 1) "It will be saved on your phone." else "They will be saved on your phone."
    override val savePhoto = "Save photo to phone"
    override fun pageOf(page: Int, count: Int) = "$page of $count"
    override val removeAttachment = "Remove attachment"
    override val attachmentGone = "This attachment is no longer on your phone."
    override fun savedIn(where: String) = "Saved to “$where”"
    override val saveFailed = "Saving didn't work."
    override val picturesFolder = "Pictures"

    override val recordVoice = "Record voice message"
    override val voiceMessage = "Voice message"
    override val discardRecording = "Discard"
    override val finishRecording = "Done"
    override val play = "Play"
    override val pause = "Pause"
    override val micDenied = "Voice messages need the microphone. You can allow it in your phone's settings."
    override val recordingFailed = "The recording didn't work."
    override fun voiceLimitReached(minutes: Int) = "Up to $minutes minutes. The recording is saved."
    override val voiceStoppedInBackground = "Recording stopped because the app was closed. You can send it now."
    override val voiceCantPlay = "This voice message can't be played right now."
    override val textPreview = "Text preview"
    override val textPreviewShortened = "Text preview · shortened"
    override val textPreviewInProgress = "Creating text preview …"
    override val noTextPreview = "No text preview. Add a few words if you like – they also go over radio."
    override fun megabytes(tenths: Long) = "${tenths / 10}.${tenths % 10} MB"

    override val circlePicture = "Circle picture"
    override val personPicture = "Profile picture"
    override val enlargeCirclePicture = "Enlarge circle picture"
    override val enlargePersonPicture = "Enlarge profile picture"
    override val photoTooLarge = "The photo is too large."
    override val photoCantRead = "The photo couldn't be read."

    override val expand = "Expand"
    override val collapse = "Collapse"
    override val clearSearch = "Clear search"
    override val nothingFound = "Nothing found."

    override val searchInChat = "Search in chat"
    override val closeSearch = "Close search"
    override val searchInChatHint = "Type a word from a message. Tap a result to see it in the chat."
    override fun searchMatches(n: Int) = if (n == 1) "1 result" else "$n results"

    override val photosAndFiles = "Photos and files"
    override val noFilesYet = "No photos or files have been sent in this chat yet."
    override fun showAllFiles(n: Int) = "View all $n"
    override val photos = "Photos"
    override val documents = "Documents"
    override val voiceMessages = "Voice messages"

    override fun charsTooMany(n: Int) = if (n == 1) "1 character too many" else "$n characters too many"
    override fun charsLeft(n: Int) = if (n == 1) "1 character left" else "$n characters left"
    override val radioShortened = "Over radio only the beginning gets through, the highlighted part follows over the internet."

    override val linkNoDevice = "No radio set up"
    override val linkBluetoothUnavailable = "Bluetooth is off or not allowed"
    override val linkSearching = "Looking for your radio …"
    override val linkConnecting = "Connecting …"
    override val linkConnected = "Connected"
    override val linkLost = "Connection lost, reconnecting …"
    override val linkInUseElsewhere = "Radio is in use elsewhere"
    override val linkInUseElsewhereHint = "You can connect it here under “Radio”."
    override val linkSearchingHint = "Please turn on your radio, put it near your phone and turn on Bluetooth on your phone."
    override fun linkBluetoothHint(appName: String) = "Please turn on Bluetooth and allow $appName to use it."
    override fun linkConnectedTo(deviceName: String) = "Connected to “$deviceName”"
    override fun radioStatus(state: String) = "Radio: $state"
    override val linkLostHint = "Please keep your radio turned on and nearby."

    override val statusSending = "Sending …"
    override val statusHandedToRadio = "Handed to radio"
    override val statusSentAwaitingConfirmation = "Sent, waiting for confirmation"
    override val statusSent = "Sent"
    override val statusDelivered = "Delivered"
    override val statusRead = "Read"
    override val statusSentNoConfirmation = "Sent, no confirmation"
    override val statusNotSent = "Not sent"

    override val via = "via"
    override fun sentAt(time: String) = "Sent: $time"
    override fun receivedAt(time: String) = "Received: $time"
    override val relayedByOther = "Sent by radio, passed on by another $APP device"
    override val deliveredToAll = "Delivered to everyone:"
    override fun deliveredToSome(n: Int, total: Int) = "Delivered to $n of $total:"
    override val deliveredLower = "delivered"
    override val notYet = "not yet"
    override val readByAll = "Read by everyone"
    override val readByNone = "Not read by anyone yet"
    override fun readBySome(n: Int, total: Int) = "Read by $n of $total"
    override fun deliveredAt(time: String) = "Delivered: $time"
    override val notDeliveredYet = "Not delivered yet"
    override fun readAt(time: String) = "Read: $time"
    override val notReadYet = "Not read yet"
    override val viewProfile = "View profile"
    override val chatActions = "More actions"
    override fun alreadyInCircle(circle: String) = "Already in “$circle”"
    override val showDetailsAndReactions = "Show details and reactions"
    override val react = "React"
    override val emptyChatList = "No messages yet. Tap “New message” to write to someone."
    override val allowNotificationsTitle = "Allow notifications?"
    override val allowNotificationsText = "Then your phone shows you new messages, even when $APP is closed."
    override val allowBluetoothTitle = "Allow Bluetooth?"
    override val allowBluetoothText = "Your radio is set up. To reach it, $APP needs access to Bluetooth."
    override val allow = "Allow"
    override val emptyNobodyYet = "Your conversations will appear here. First add a contact or start a circle."
    override val emptyInviteHint = "Got an invite? Just tap the link."
    override fun firstMessageTo(name: String) = "Write the first message to $name."
    override fun circleReadsAlong(circle: String) = "Everyone in the circle “$circle” reads along."

    override fun cardChannel(sender: String, channel: String) = "$sender invites you to the channel “$channel”."
    override fun cardChannelOwn(channel: String) = "You invited others to the channel “$channel”."
    override fun cardOfficial(sender: String) = "$sender invites you to official warnings."
    override val cardOfficialOwn = "You invited others to official warnings."
    override fun cardContact(sender: String, contact: String) = "$sender shares a contact: $contact"
    override fun cardContactOwn(contact: String) = "You shared a contact: $contact"
    override fun cardContactSelf(sender: String) = "$sender shares their own contact."
    override val cardContactSelfOwn = "You shared your contact."
    override fun cardCircle(sender: String, circle: String) = "$sender invites you to the circle “$circle”."
    override fun cardCircleOwn(circle: String) = "You invited others to the circle “$circle”."
    override fun cardDevice(sender: String, device: String) = if (device.isBlank()) "$sender shares the code of a new device." else "$sender shares the code of the new device “$device”."
    override fun cardDeviceOwn(device: String) = if (device.isBlank()) "You shared the code of a new device." else "You shared the code of the new device “$device”."
    override val cardAddDevice = "Add device"
    override val cardDeviceAdded = "Already one of your devices"
    override val cardThisDevice = "This is this device."
    override val cardBroken = "This invitation can’t be opened."
    override val cardSubscribe = "Subscribe"
    override val cardSubscribed = "Subscribed"
    override val cardAdd = "Add"
    override val cardInContacts = "Already in your contacts"
    override val cardThisIsYou = "This is you."
    override val cardJoin = "Join"
    override val cardInCircle = "You’re already in this circle."
    override val cardOpen = "Open"
    override val delete = "Delete"
    override val deleteForEveryone = "Delete for everyone"
    override val deleteForMe = "Delete for me"
    override val deleteHint = "Anyone who has already seen or saved the message can keep it."
    override fun deletedBy(name: String) = "Deleted by $name"
    override val deletedByYou = "You deleted this message"
    override val deletedMessage = "Deleted message"
    override val removePreview = "Remove preview"
    override val reply = "Reply"
    override fun replyTo(name: String) = "Reply to $name"
    override val replyToYou = "Reply to yourself"
    override val you = "You"
    override val cancelReply = "Cancel reply"
    override val showAnsweredMessage = "Go to message"
    override val replyUnavailable = "Message not available"
    override val answeredMessageGone = "This message is no longer on this device."
    override fun introStandIn(name: String) = "Contact: $name"
}

/** 10000 → „10 000“ or „10,000“, as in the building blocks of the subscription preview. */
private fun grouped(n: Int, sep: Char): String = n.toString().reversed().chunked(3).joinToString(sep.toString()).reversed()
