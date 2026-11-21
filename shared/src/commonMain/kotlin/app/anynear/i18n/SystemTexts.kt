package app.anynear.i18n

/** Notifications and texts of the platform. */
interface SystemTexts {
    // Notifications about messages
    fun newMessages(n: Int): String

    /** Locked screen on iOS: „Neue Nachricht“ */
    val newMessage: String
    /** Locked screen when only a reaction is new: „Neue Reaktion“ */
    val newReaction: String
    val messagesChannel: String
    val messagesChannelDescription: String
    /** Action on a message notification, also on watches: „Antworten“ */
    val reply: String
    /** Action on a message notification, also on watches and in Android Auto: „Gelesen“ */
    val markRead: String
    /** Placeholder of the reply field in the notification */
    val replyHint: String
    /** Ready-made short replies offered on watches and in the notification, shortest first. */
    val quickReplies: List<String>
    /** Shown in the notification when a reply from it could not be sent: „Nicht gesendet: …“ */
    fun replyNotSent(reason: String): String
    /** Android channel for reminders, e.g. to charge the radio (ADR 0085). */
    val remindersChannel: String
    val remindersChannelDescription: String

    // Foreground service (Android)
    val receiving: String
    val backgroundChannel: String
    val backgroundChannelDescription: String

    // Update of the app (Android)
    val updatesChannel: String
    val updatesChannelDescription: String
    val updateNotificationText: String
    val movedNotificationText: String
    val reinstallNotificationText: String
    val updateCheckFailed: String
    val downloadFailed: String
    val notEnoughSpace: String
    val versionMismatch: String

    /** The relays no longer fully serve this version (ADR 0061, decision 13). */
    val updateNeeded: String
    val updateFailed: String
    val allowInstall: String

    // Update requested by the network (ADR 0096)
    /** Band: „Es gibt eine neue Version von anynear.“ */
    /** Band with the day from which the old version no longer works, e.g. „20. November“. */
    fun updateSoon(date: String): String
    /** Block screen: title, one sentence why. */
    val updateBlockTitle: String
    val updateBlockText: String

    // Sharing
    /** Title of the chooser when sharing: „Einladen über …“ */
    val shareVia: String

    // Menu bar (Mac)
    /** Menu „Gehe zu“ with the areas */
    val menuGoTo: String
    /** „Einstellungen …“ in the app menu */
    val menuSettings: String
    /** „anynear-Hilfe“ in the menu „Hilfe“ */
    val menuHelp: String
}

internal object SystemTextsDe : SystemTexts {
    override fun newMessages(n: Int) = if (n == 1) "1 neue Nachricht" else "$n neue Nachrichten"
    override val newMessage = "Neue Nachricht"
    override val newReaction = "Neue Reaktion"
    override val messagesChannel = "Nachrichten"
    override val messagesChannelDescription = "Neue Nachrichten aus deinen Kreisen und von deinen Kontakten"
    override val reply = "Antworten"
    override val markRead = "Gelesen"
    override val replyHint = "Nachricht"
    override val quickReplies = listOf("Ja", "Nein", "Okay", "Bin unterwegs", "Rufe gleich an")
    override fun replyNotSent(reason: String) = "Nicht gesendet: $reason"
    override val remindersChannel = "Erinnerungen"
    override val remindersChannelDescription = "Zum Beispiel, dein Funkgerät zu laden"

    override val receiving = "Empfängt deine Nachrichten"
    override val backgroundChannel = "$APP läuft im Hintergrund"
    override val backgroundChannelDescription = "Nötig, damit neue Nachrichten ankommen. Kann ausgeblendet werden."

    override val updatesChannel = "Neue Versionen"
    override val updatesChannelDescription = "Hinweis, wenn eine neue Version von $APP da ist"
    override val updateNotificationText = "Tippe hier, um $APP zu aktualisieren."
    override val movedNotificationText = "Tippe hier, um die neue App zu laden."
    override val reinstallNotificationText = "Tippe hier: Die nächste Version muss neu installiert werden."
    override val updateCheckFailed = "Konnte gerade nicht nachsehen. Hat dein Handy Internet?"
    override val downloadFailed = "Laden hat nicht geklappt. Prüf dein Internet und versuch es noch einmal."
    override val notEnoughSpace = "Auf deinem Handy ist zu wenig Platz frei."
    override val updateNeeded = "Bitte aktualisiere $APP, damit deine Nachrichten weiter ankommen."
    override val versionMismatch = "Diese Version passt nicht zur installierten App. Installiere $APP bitte einmal neu, siehe anynear.app/app-laden."
    override val updateFailed = "Die Aktualisierung hat nicht geklappt. Versuch es bitte später noch einmal."
    override val allowInstall = "Bitte erlaube $APP in den Einstellungen deines Handys, Apps zu installieren, und versuch es dann noch einmal."
    override fun updateSoon(date: String) = "Ab $date geht $APP nur noch mit der neuen Version."
    override val updateBlockTitle = "Bitte aktualisiere $APP"
    override val updateBlockText = "Damit deine Nachrichten weiter ankommen, brauchst du die neue Version."

    override val shareVia = "Einladen über …"

    override val menuGoTo = "Gehe zu"
    override val menuSettings = "Einstellungen …"
    override val menuHelp = "$APP-Hilfe"
}

internal object SystemTextsEn : SystemTexts {
    override fun newMessages(n: Int) = if (n == 1) "1 new message" else "$n new messages"
    override val newMessage = "New message"
    override val newReaction = "New reaction"
    override val messagesChannel = "Messages"
    override val messagesChannelDescription = "New messages from your circles and contacts"
    override val reply = "Reply"
    override val markRead = "Mark as read"
    override val replyHint = "Message"
    override val quickReplies = listOf("Yes", "No", "Okay", "On my way", "Will call you soon")
    override fun replyNotSent(reason: String) = "Not sent: $reason"
    override val remindersChannel = "Reminders"
    override val remindersChannelDescription = "For example, to charge your radio"

    override val receiving = "Receiving your messages"
    override val backgroundChannel = "$APP running in the background"
    override val backgroundChannelDescription = "Needed so new messages arrive. You can hide it."

    override val updatesChannel = "New versions"
    override val updatesChannelDescription = "Lets you know when a new version of $APP is here"
    override val updateNotificationText = "Tap here to update $APP."
    override val movedNotificationText = "Tap here to download the new app."
    override val reinstallNotificationText = "Tap here: the next version needs a fresh install."
    override val updateCheckFailed = "Couldn't check right now. Does your phone have internet?"
    override val downloadFailed = "Download didn't work. Check your internet and try again."
    override val notEnoughSpace = "There isn't enough free space on your phone."
    override val updateNeeded = "Please update $APP so your messages keep arriving."
    override val versionMismatch = "This version doesn't match the installed app. Please install $APP again, see anynear.app/app-laden."
    override val updateFailed = "The update didn't work. Please try again later."
    override val allowInstall = "Please allow $APP to install apps in your phone's settings, then try again."
    override fun updateSoon(date: String) = "From $date on, $APP only works with the new version."
    override val updateBlockTitle = "Please update $APP"
    override val updateBlockText = "You need the new version so your messages keep arriving."

    override val shareVia = "Invite via …"

    override val menuGoTo = "Go"
    override val menuSettings = "Settings…"
    override val menuHelp = "$APP Help"
}
