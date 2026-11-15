package app.anynear.i18n

/** States and messages from the app logic. */
interface StatusTexts {
    // --- Set up and update radio ---
    /** „Dein Funkgerät bekommt seinen Namen.“ */
    val deviceNaming: String
    val deviceUpdating: String
    val deviceSettingUp: String
    val deviceUpToDate: String
    val deviceReady: String
    val deviceNotConnected: String
    fun updateFailed(reason: String): String
    fun setupFailed(reason: String): String
    fun somethingFailed(reason: String): String
    val deviceNameSaving: String
    val deviceNameSaved: String
    val deviceNameNotTaken: String
    val profileApplying: String
    val profileApplied: String

    /** Reasons why the radio did not accept something. */
    val radioSetupNotConfirmed: String
    val deviceNameInvalid: String
    val radioSettingsIncomplete: String

    /** Name of a found radio if it does not state one itself. */
    val bluetoothRadioFallbackName: String

    /** „Funkgerät im WLAN (192.168.0.5)“ */
    fun wifiRadioName(host: String): String

    // --- Circle on the radio ---
    fun circleRadioFailed(circle: String, reason: String): String

    /** The radio channel `anynear` could not be set up (ADR 0051). */
    fun radioChannelFailed(reason: String): String

    /** All channel slots of the radio are taken; anynear does not overwrite foreign channels. */
    val radioChannelNoSlot: String

    // --- Sending and attachments ---
    val messageEmpty: String
    fun tooManyAttachments(max: Int): String
    val messageTooLong: String
    val circleClosedForWriting: String
    val attachmentsNeedApp: String
    val personUnreachable: String
    val fileTooLarge: String

    /** Announcement of attachments in the radio text: „📎 Bild“, „📎 2 Bilder, 1 PDF“. */
    val announceImage: String
    val announcePdf: String
    fun announceImages(n: Int): String
    fun announcePdfs(n: Int): String
    val announceFile: String
    fun announceFiles(n: Int): String
    /** Radio text of a voice message without text: „Sprachnachricht 0:42“ (ADR 0030). */
    fun announceVoice(duration: String?): String

    // --- Circles and contacts ---
    val enterYourName: String

    /** „Bitte gib dem Kreis einen Namen, z. B. „Familie“.“ */
    val nameYourCircleExample: String
    val nameYourCircle: String
    fun alreadyInCircle(circle: String): String
    fun joinedCircle(circle: String): String
    fun contactAdded(name: String): String
    fun contactKeyMismatch(name: String): String
    val ownContact: String
    val contactLinkBroken: String
    fun memberRemovedNotice(name: String): String
    val invitesResetNotice: String
    fun removedFromCircleNotice(circle: String): String
    fun reinviteSent(name: String): String
    val circleReadOnly: String
    val appStarting: String
    val photoUnreadable: String

    /** Name of a circle from the time with only one circle. */
    val myCircle: String

    // --- Share texts ---
    fun shareInvite(circle: String, url: String): String
    fun reinviteMessage(circle: String, url: String): String
    /** Own contact link for sharing in other apps; [name] is the own name. */
    fun shareOwnContact(name: String, url: String): String

    // --- Lines in the circle chat ---
    fun lineYouRemoved(name: String): String
    val lineYouResetInvites: String
    val lineBackInCircle: String
    fun lineRemovedYou(who: String): String
    fun lineRemoved(who: String, name: String): String
    fun lineResetInvites(who: String): String
    fun lineLeft(name: String): String
    fun lineUnreachable(name: String): String
    fun lineRenamed(who: String, circle: String): String
    fun lineImageRemoved(who: String): String
    fun lineImageChanged(who: String): String
    fun lineYouRenamed(circle: String): String
    val lineYouRemovedImage: String
    val lineYouChangedImage: String

    /** Fallback names. */
    val someone: String
    val unknown: String

    /** Own last message in the overview: „Du: …“ */
    val youSender: String

    // --- History ---
    val historyWaiting: String
    val historyLoading: String

    /** Over the list of chats while a new device or one back from the emergency card still loads (ADR 0107 §8). */
    val messagesLoading: String

    // --- Invitation and contact links ---
    val notAnInviteLink: String
    val inviteLinkBroken: String

    /** Link from another network (ADR 0035 §6). */
    val linkForeignNetwork: String

    /** Once after the fresh start on ANMP and ANDP (ADR 0034 §4). */
    val freshStart: String

    // --- Appearance ---
    val darkModeSystem: String
    val darkModeLight: String
    val darkModeDark: String
}

internal object StatusTextsDe : StatusTexts {
    override val deviceNaming = "Dein Funkgerät bekommt seinen Namen."
    override val deviceUpdating = "Dein Funkgerät wird aktualisiert. Es kann dabei mehrmals neu starten."
    override val deviceSettingUp = "Dein Funkgerät wird eingerichtet. Es kann dabei mehrmals neu starten."
    override val deviceUpToDate = "Fertig: Dein Funkgerät ist auf dem neuesten Stand."
    override val deviceReady = "Fertig: Dein Funkgerät ist für $APP eingerichtet."
    override val deviceNotConnected = "Das Funkgerät ist gerade nicht verbunden. Bitte versuch es gleich noch einmal."
    override fun updateFailed(reason: String) = "Aktualisieren hat nicht geklappt: $reason"
    override fun setupFailed(reason: String) = "Einrichten hat nicht geklappt: $reason"
    override fun somethingFailed(reason: String) = "Das hat nicht geklappt: $reason"
    override val deviceNameSaving = "Dein Funkgerät übernimmt den neuen Namen und startet dabei neu."
    override val deviceNameSaved = "Neuer Name übernommen."
    override val deviceNameNotTaken = "Dein Funkgerät hat den neuen Namen noch nicht übernommen. Versuch es gleich noch einmal."
    override val profileApplying = "Die $APP-Einstellungen werden aufgespielt. Das Funkgerät kann dabei mehrmals neu starten."
    override val profileApplied = "$APP-Einstellungen aufgespielt."
    override val radioSetupNotConfirmed = "Das Funkgerät hat die Einrichtung nicht bestätigt."
    override val deviceNameInvalid = "Der Name passt nicht."
    override val radioSettingsIncomplete = "Das Funkgerät hat nicht alles übernommen."
    override val bluetoothRadioFallbackName = "Meshtastic-Funkgerät"
    override fun wifiRadioName(host: String) = "Funkgerät im WLAN ($host)"

    override fun circleRadioFailed(circle: String, reason: String) = "Der Kreis „$circle“ konnte nicht aufs Funkgerät: $reason"
    override fun radioChannelFailed(reason: String) = "$APP konnte das Funkgerät nicht einrichten: $reason"
    override val radioChannelNoSlot = "Auf deinem Funkgerät ist kein Platz frei. Entferne dort einen Kanal einer anderen App, dann richtet $APP es ein."

    override val messageEmpty = "Die Nachricht ist leer."
    override fun tooManyAttachments(max: Int) = "Höchstens $max Anhänge pro Nachricht."
    override val messageTooLong = "Die Nachricht ist zu lang. Bitte kürzer fassen."
    override val circleClosedForWriting = "In diesem Kreis kannst du nicht mehr schreiben."
    override val attachmentsNeedApp = "Bilder und PDFs kannst du dieser Person erst schicken, wenn sie $APP nutzt."
    override val personUnreachable = "Diese Person ist gerade auf keinem Weg erreichbar."
    override val fileTooLarge = "Die Datei ist zu groß (höchstens 10 MB)."
    override val announceImage = "Bild"
    override val announcePdf = "PDF"
    override fun announceImages(n: Int) = if (n == 1) "1 Bild" else "$n Bilder"
    override fun announcePdfs(n: Int) = if (n == 1) "1 PDF" else "$n PDFs"
    override val announceFile = "Datei"
    override fun announceFiles(n: Int) = if (n == 1) "1 Datei" else "$n Dateien"
    override fun announceVoice(duration: String?) = if (duration == null) "Sprachnachricht" else "Sprachnachricht $duration"

    override val enterYourName = "Bitte gib deinen Namen ein."
    override val nameYourCircleExample = "Bitte gib dem Kreis einen Namen, z. B. „Familie“."
    override val nameYourCircle = "Bitte gib dem Kreis einen Namen."
    override fun alreadyInCircle(circle: String) = "Du bist schon im Kreis „$circle“."
    override fun joinedCircle(circle: String) = "Du bist jetzt im Kreis „$circle“."
    override fun contactAdded(name: String) = "$name ist jetzt in deinen Kontakten."
    override fun contactKeyMismatch(name: String) =
        "Achtung: Der Kontakt $name passt nicht zum gespeicherten. Bitte direkt nachfragen."
    override val ownContact = "Das ist dein eigener Kontakt."
    override val contactLinkBroken = "Der Kontaktlink ist beschädigt oder unvollständig."
    override fun memberRemovedNotice(name: String) = "$name ist nicht mehr im Kreis."
    override val invitesResetNotice = "Alte Einladungslinks gelten nicht mehr."
    override fun removedFromCircleNotice(circle: String) = "Du wurdest aus dem Kreis „$circle“ entfernt."
    override fun reinviteSent(name: String) = "Einladung an $name geschickt."
    override val circleReadOnly = "In diesem Kreis kannst du nichts mehr ändern."
    override val appStarting = "Einen Moment bitte, die App startet noch."
    override val photoUnreadable = "Das Foto ließ sich nicht lesen."
    override val myCircle = "Mein Kreis"

    override fun shareInvite(circle: String, url: String) = "Komm in meinen Kreis „$circle“ bei $APP: $url"
    override fun reinviteMessage(circle: String, url: String) = "Komm wieder in den Kreis „$circle“ bei $APP: $url"
    override fun shareOwnContact(name: String, url: String) = "$name bei $APP. Tippe auf den Link, um mich als Kontakt hinzuzufügen: $url"

    override fun lineYouRemoved(name: String) = "Du hast $name entfernt."
    override val lineYouResetInvites = "Du hast die alten Einladungslinks ungültig gemacht."
    override val lineBackInCircle = "Du bist wieder im Kreis."
    override fun lineRemovedYou(who: String) = "$who hat dich aus dem Kreis entfernt."
    override fun lineRemoved(who: String, name: String) = "$who hat $name entfernt."
    override fun lineResetInvites(who: String) = "$who hat die alten Einladungslinks ungültig gemacht."
    override fun lineLeft(name: String) = "$name hat den Kreis verlassen."
    override fun lineUnreachable(name: String) = "$name ist über anynear nicht mehr erreichbar."
    override fun lineRenamed(who: String, circle: String) = "$who hat den Kreis in „$circle“ umbenannt."
    override fun lineImageRemoved(who: String) = "$who hat das Kreisbild entfernt."
    override fun lineImageChanged(who: String) = "$who hat das Kreisbild geändert."
    override fun lineYouRenamed(circle: String) = "Du hast den Kreis in „$circle“ umbenannt."
    override val lineYouRemovedImage = "Du hast das Kreisbild entfernt."
    override val lineYouChangedImage = "Du hast das Kreisbild geändert."
    override val someone = "Jemand"
    override val unknown = "Unbekannt"
    override val youSender = "Du"

    override val historyWaiting = "Frühere Nachrichten kommen, sobald jemand aus dem Kreis online ist."
    override val historyLoading = "Frühere Nachrichten werden geladen …"
    override val messagesLoading = "Deine Nachrichten werden geladen …"

    override val notAnInviteLink = "Das ist kein $APP-Einladungslink."
    override val inviteLinkBroken = "Der Einladungslink ist beschädigt oder unvollständig."
    override val linkForeignNetwork = "Dieser Link gehört nicht zu $APP."
    override val freshStart = "$APP wurde neu aufgesetzt. Bitte richte deinen Kreis neu ein."

    override val darkModeSystem = "Wie das Handy"
    override val darkModeLight = "Hell"
    override val darkModeDark = "Dunkel"
}

internal object StatusTextsEn : StatusTexts {
    override val deviceNaming = "Your radio is getting its name."
    override val deviceUpdating = "Your radio is being updated. It may restart a few times."
    override val deviceSettingUp = "Your radio is being set up. It may restart a few times."
    override val deviceUpToDate = "Done: Your radio is up to date."
    override val deviceReady = "Done: Your radio is set up for $APP."
    override val deviceNotConnected = "Your radio isn't connected right now. Please try again in a moment."
    override fun updateFailed(reason: String) = "The update didn't work: $reason"
    override fun setupFailed(reason: String) = "The setup didn't work: $reason"
    override fun somethingFailed(reason: String) = "That didn't work: $reason"
    override val deviceNameSaving = "Your radio is taking the new name and restarts to do so."
    override val deviceNameSaved = "New name is set."
    override val deviceNameNotTaken = "Your radio hasn't taken the new name yet. Please try again in a moment."
    override val profileApplying = "Applying the $APP settings. Your radio may restart a few times."
    override val profileApplied = "$APP settings applied."
    override val radioSetupNotConfirmed = "The radio didn't confirm the setup."
    override val deviceNameInvalid = "That name doesn't work."
    override val radioSettingsIncomplete = "The radio didn't take all the settings."
    override val bluetoothRadioFallbackName = "Meshtastic radio"
    override fun wifiRadioName(host: String) = "Radio on Wi-Fi ($host)"

    override fun circleRadioFailed(circle: String, reason: String) = "Couldn't put the circle “$circle” on your radio: $reason"
    override fun radioChannelFailed(reason: String) = "$APP couldn't set up your radio: $reason"
    override val radioChannelNoSlot = "There’s no free slot on your radio. Remove a channel of another app there, then $APP sets it up."

    override val messageEmpty = "The message is empty."
    override fun tooManyAttachments(max: Int) = "At most $max attachments per message."
    override val messageTooLong = "The message is too long. Please make it shorter."
    override val circleClosedForWriting = "You can't write in this circle anymore."
    override val attachmentsNeedApp = "You can send photos and PDFs to this person once they use $APP."
    override val personUnreachable = "This person can't be reached right now."
    override val fileTooLarge = "The file is too large (10 MB max)."
    override val announceImage = "Photo"
    override val announcePdf = "PDF"
    override fun announceImages(n: Int) = if (n == 1) "1 photo" else "$n photos"
    override fun announcePdfs(n: Int) = if (n == 1) "1 PDF" else "$n PDFs"
    override val announceFile = "File"
    override fun announceFiles(n: Int) = if (n == 1) "1 file" else "$n files"
    override fun announceVoice(duration: String?) = if (duration == null) "Voice message" else "Voice message $duration"

    override val enterYourName = "Please enter your name."
    override val nameYourCircleExample = "Please give the circle a name, e.g. “Family”."
    override val nameYourCircle = "Please give the circle a name."
    override fun alreadyInCircle(circle: String) = "You're already in the circle “$circle”."
    override fun joinedCircle(circle: String) = "You're now in the circle “$circle”."
    override fun contactAdded(name: String) = "$name is now in your contacts."
    override fun contactKeyMismatch(name: String) =
        "Careful: The contact $name doesn't match the saved one. Please check with them directly."
    override val ownContact = "That's your own contact."
    override val contactLinkBroken = "The contact link is damaged or incomplete."
    override fun memberRemovedNotice(name: String) = "$name is no longer in the circle."
    override val invitesResetNotice = "Old invite links no longer work."
    override fun removedFromCircleNotice(circle: String) = "You were removed from the circle “$circle”."
    override fun reinviteSent(name: String) = "Invite sent to $name."
    override val circleReadOnly = "You can't change anything in this circle anymore."
    override val appStarting = "One moment please, the app is still starting."
    override val photoUnreadable = "Couldn't read the photo."
    override val myCircle = "My circle"

    override fun shareInvite(circle: String, url: String) = "Join my circle “$circle” on $APP: $url"
    override fun reinviteMessage(circle: String, url: String) = "Come back to the circle “$circle” on $APP: $url"
    override fun shareOwnContact(name: String, url: String) = "$name on $APP. Tap the link to add me as a contact: $url"

    override fun lineYouRemoved(name: String) = "You removed $name."
    override val lineYouResetInvites = "You turned off the old invite links."
    override val lineBackInCircle = "You're back in the circle."
    override fun lineRemovedYou(who: String) = "$who removed you from the circle."
    override fun lineRemoved(who: String, name: String) = "$who removed $name."
    override fun lineResetInvites(who: String) = "$who turned off the old invite links."
    override fun lineLeft(name: String) = "$name left the circle."
    override fun lineUnreachable(name: String) = "$name can no longer be reached on anynear."
    override fun lineRenamed(who: String, circle: String) = "$who renamed the circle to “$circle”."
    override fun lineImageRemoved(who: String) = "$who removed the circle picture."
    override fun lineImageChanged(who: String) = "$who changed the circle picture."
    override fun lineYouRenamed(circle: String) = "You renamed the circle to “$circle”."
    override val lineYouRemovedImage = "You removed the circle picture."
    override val lineYouChangedImage = "You changed the circle picture."
    override val someone = "Someone"
    override val unknown = "Unknown"
    override val youSender = "You"

    override val historyWaiting = "Earlier messages will arrive as soon as someone from the circle is online."
    override val historyLoading = "Loading earlier messages …"
    override val messagesLoading = "Loading your messages …"

    override val notAnInviteLink = "That's not a $APP invite link."
    override val inviteLinkBroken = "The invite link is damaged or incomplete."
    override val linkForeignNetwork = "This link isn't from $APP."
    override val freshStart = "$APP has been set up again. Please set up your circle again."

    override val darkModeSystem = "Same as phone"
    override val darkModeLight = "Light"
    override val darkModeDark = "Dark"
}
