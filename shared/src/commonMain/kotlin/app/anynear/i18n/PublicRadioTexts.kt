package app.anynear.i18n

/** Public radio of the area: „Alle in der Nähe“ and „Mesh Hessen“ (ADR 0055). */
interface PublicRadioTexts {
    val section: String
    val nearbyTitle: String
    val nearbySubtitle: String
    val meshHessenTitle: String
    val meshHessenSubtitle: String
    val intro: String
    val meshHessenIntro: String
    val introOk: String
    val info: String
    val aboutChat: String
    val notifications: String
    val notificationsOn: String
    val notificationsOff: String
    fun radioId(id: String): String
    val heardDirectly: String
    fun relayedBy(count: Int): String
    val viaBridge: String
    fun signal(quality: String, values: String): String
    val signalGood: String
    val signalFair: String
    val signalWeak: String
    val decimalSeparator: Char
    val noMessagesYet: String
    val unknownRadio: String
    fun contactRadio(name: String): String
    val inputHint: String
    fun bytesLeft(left: Int): String
    val confirmTitle: String
    val confirmText: String
    val confirmSend: String
    val tooLong: String
    fun tooSoon(seconds: Int): String
    val notReady: String
    val statusSent: String
    val statusRelayed: String
    val statusSending: String
    val statusFailed: String
    val toggle: String
    val toggleOn: String
    val toggleOff: String
    fun offerTitle(name: String): String
    fun offerText(name: String, freeSlots: Int): String
    val offerYes: String
    val offerNo: String
    fun offerNoSlot(name: String): String
    fun community(name: String): String
    val communityOn: String
    val communityOff: String
}

internal object PublicRadioTextsDe : PublicRadioTexts {
    override val section = "Öffentlicher Funk"
    override val nearbyTitle = "Alle in der Nähe"
    override val nearbySubtitle = "Öffentlich · alle Funkgeräte in der Nähe"
    override val meshHessenTitle = "Mesh Hessen"
    override val meshHessenSubtitle = "Öffentlich · Funkgruppe Mesh Hessen"
    override val intro = "Hier liest du, was Funkgeräte in deiner Gegend an alle senden. Das sind Menschen außerhalb deiner Kreise, oft ohne $APP. Was du hier schreibst, kann jeder in Funkreichweite lesen."
    override val meshHessenIntro = "Mesh Hessen ist eine Gruppe von Funkfreunden in Hessen. Ihre Funkgeräte geben Nachrichten über weite Strecken weiter, zum Teil auch übers Internet."
    override val introOk = "Verstanden"
    override val info = "Was ist das?"
    override val aboutChat = "Über diesen Chat"
    override val notifications = "Benachrichtigungen"
    override val notificationsOn = "An: Bei neuen Nachrichten meldet sich $APP."
    override val notificationsOff = "Aus: Neue Nachrichten zeigt nur die Zahl in der Übersicht."
    override fun radioId(id: String) = "Kennung des Funkgeräts: $id"
    override val heardDirectly = "Direkt empfangen, ohne Umweg"
    override fun relayedBy(count: Int) = if (count == 1) "Über ein weiteres Funkgerät weitergereicht" else "Über $count weitere Funkgeräte weitergereicht"
    override val viaBridge = "Kam über eine Brücke aus dem Internet"
    override fun signal(quality: String, values: String) = "Empfang: $quality ($values)"
    override val signalGood = "gut"
    override val signalFair = "mittel"
    override val signalWeak = "schwach"
    override val decimalSeparator = ','
    override val noMessagesYet = "Noch nichts gehört. Nachrichten kommen nur an, solange dein Funkgerät verbunden ist."
    override val unknownRadio = "Unbekanntes Funkgerät"
    override fun contactRadio(name: String) = "Funkgerät von $name"
    override val inputHint = "Nachricht an alle in der Nähe"
    override fun bytesLeft(left: Int) = if (left >= 0) "Noch $left Zeichen" else "${-left} Zeichen zu viel"
    override val confirmTitle = "Für alle sichtbar"
    override val confirmText = "Das kann jeder in der Nähe lesen, auch Fremde. Trotzdem senden?"
    override val confirmSend = "Senden"
    override val tooLong = "Die Nachricht ist zu lang für den Funk."
    override fun tooSoon(seconds: Int) = "Bitte warte noch $seconds Sekunden. So bleibt der Funk für alle frei."
    override val notReady = "Dein Funkgerät ist gerade nicht verbunden."
    override val statusSent = "Gesendet"
    override val statusRelayed = "Weitergegeben"
    override val statusSending = "Wird gesendet"
    override val statusFailed = "Nicht gesendet"
    override val toggle = "Öffentlichen Funk zeigen"
    override val toggleOn = "Öffentliche Chats unter den Kreisen"
    override val toggleOff = "Aus. Öffentliche Nachrichten werden nicht gespeichert."
    override fun offerTitle(name: String) = "Funk-Community „$name“"
    override fun offerText(name: String, freeSlots: Int) =
        "Möchtest du auch in deiner lokalen Funk-Community „$name“ funken? Das belegt " +
            (if (freeSlots == 1) "den letzten freien Kanal" else "einen von $freeSlots freien Kanälen") + " auf deinem Funkgerät."
    override val offerYes = "Ja"
    override val offerNo = "Nein danke"
    override fun offerNoSlot(name: String) = "In deiner Gegend gibt es die Funk-Community „$name“. Auf deinem Funkgerät ist gerade kein Kanal frei."
    override fun community(name: String) = "In „$name“ mitfunken"
    override val communityOn = "An. Belegt einen Kanal auf deinem Funkgerät."
    override val communityOff = "Aus"
}

internal object PublicRadioTextsEn : PublicRadioTexts {
    override val section = "Public radio"
    override val nearbyTitle = "Everyone nearby"
    override val nearbySubtitle = "Public · all radios nearby"
    override val meshHessenTitle = "Mesh Hessen"
    override val meshHessenSubtitle = "Public · Mesh Hessen radio group"
    override val intro = "Here you read what radios in your area send to everyone. These are people outside your circles, often without $APP. Anyone within radio range can read what you write here."
    override val meshHessenIntro = "Mesh Hessen is a group of radio enthusiasts in Hesse. Their radios pass messages on over long distances, partly also over the internet."
    override val introOk = "Got it"
    override val info = "What is this?"
    override val aboutChat = "About this chat"
    override val notifications = "Notifications"
    override val notificationsOn = "On: $APP lets you know about new messages."
    override val notificationsOff = "Off: new messages only show as a number in the overview."
    override fun radioId(id: String) = "Radio ID: $id"
    override val heardDirectly = "Received directly, no detour"
    override fun relayedBy(count: Int) = if (count == 1) "Passed on by one other radio" else "Passed on by $count other radios"
    override val viaBridge = "Came over a bridge from the internet"
    override fun signal(quality: String, values: String) = "Reception: $quality ($values)"
    override val signalGood = "good"
    override val signalFair = "fair"
    override val signalWeak = "weak"
    override val decimalSeparator = '.'
    override val noMessagesYet = "Nothing heard yet. Messages only arrive while your radio is connected."
    override val unknownRadio = "Unknown radio"
    override fun contactRadio(name: String) = "$name's radio"
    override val inputHint = "Message to everyone nearby"
    override fun bytesLeft(left: Int) = if (left >= 0) "$left characters left" else "${-left} characters too many"
    override val confirmTitle = "Visible to everyone"
    override val confirmText = "Anyone nearby can read this, including strangers. Send anyway?"
    override val confirmSend = "Send"
    override val tooLong = "The message is too long for radio."
    override fun tooSoon(seconds: Int) = "Please wait $seconds more seconds. This keeps the radio free for everyone."
    override val notReady = "Your radio is not connected right now."
    override val statusSent = "Sent"
    override val statusRelayed = "Passed on"
    override val statusSending = "Sending"
    override val statusFailed = "Not sent"
    override val toggle = "Show public radio"
    override val toggleOn = "Public chats below your circles"
    override val toggleOff = "Off. Public messages are not stored."
    override fun offerTitle(name: String) = "Radio community “$name”"
    override fun offerText(name: String, freeSlots: Int) =
        "Would you also like to join your local radio community “$name”? This uses " +
            (if (freeSlots == 1) "the last free channel" else "one of $freeSlots free channels") + " on your radio."
    override val offerYes = "Yes"
    override val offerNo = "No thanks"
    override fun offerNoSlot(name: String) = "There is a radio community “$name” in your area. Your radio has no free channel right now."
    override fun community(name: String) = "Join “$name”"
    override val communityOn = "On. Uses one channel on your radio."
    override val communityOff = "Off"
}
