package app.anynear.i18n

/** Setup of the app, the circle and the radio. */
interface SetupTexts {
    // First start: name, invitation, circle
    /** „Schritt 1 von 3“ */
    fun step(number: Int, total: Int): String
    val welcome: String
    val nameIntro: String
    val yourName: String
    val invitedTitle: String
    /** „Kreis „Familie““ */
    fun circleNamed(name: String): String
    val afterJoining: String
    val joinCircle: String
    val haveInvite: String
    val circleExplained: String
    val haveLinkTitle: String
    val haveLinkText: String
    val startOwnTitle: String
    val startOwnText: String
    val openInvite: String
    val inviteLink: String
    val scanQrCode: String
    /** Suggestion for the name of the first circle. */
    val startCircle: String

    // Search for radio
    val radioIntro: String
    val yourRadio: String
    val whichRadio: String
    val nameOnDisplay: String
    val searchBluetooth: String
    val searchBluetoothWhy: String
    val allowBluetooth: String
    val searchingRadio: String
    val stillSearching: String
    val noRadioFound: String
    val searchAgain: String
    val foundNearbyBluetooth: String
    val foundOnWifi: String
    val connect: String
    val connectByAddressAdvanced: String
    val connectByAddress: String
    val connectByAddressHelp: String
    val addressPlaceholder: String
    val invalidAddress: String
    val addressNotReachable: String
    val checkingConnection: String

    // Set up radio
    val setUpRadio: String
    val updateRadio: String
    val setUpRadioForApp: String
    val setupOnlyName: String
    val setupUpdate: String
    val setupNew: String
    val setupOther: String
    val routerWarning: String
    val radioName: String
    val radioNameInvalid: String
    val radioNameTaken: String
    val showLess: String
    val setUpAnyway: String
    val update: String
    val setUp: String
    val detailsUpdate: String
    val detailsOther: String
    val detailsSettings: String
    val detailsRouter: String
    val detailsUnchanged: String
    val detailsNameKept: String
    val detailsNamePublic: String

    // Tabs
    val tabChats: String
    val tabContacts: String
    val tabCircles: String
    val tabSettings: String
    /** Read-aloud text on the tab „Nachrichten“: „3 neue Nachrichten“ */
    fun newMessages(n: Int): String

    // Hints and dialogs of the main view
    val removedFromCircle: String
    val join: String
    val backgroundTitle: String
    val backgroundText: String
    /** Battery optimisation: „Tippe gleich auf „Zulassen“.“ */
    val backgroundTapAllow: String
    /** Background restricted: „Tippe gleich auf „Akku“ …“ */
    val backgroundTapBattery: String
    /** Manufacturer has ended the app in the background: replaces [backgroundText]. */
    val backgroundStopped: String
    /** Manufacturer lock: „Tippe gleich auf „Akku“ … „Autostart“ …“ */
    val backgroundTapStopped: String
    val openSetting: String
    val radioFound: String
    fun suggestedRadio(deviceName: String): String
    fun suggestedKnownRadio(deviceName: String): String
    val invite: String
    fun invitedTo(circle: String): String

    // No connection
    val noConnection: String
    val appUnreachable: String
    val noInternet: String
    /** „Kein Internet. Nachrichten warten …“ */
    fun messagesWait(cause: String): String
    /** „Kein Internet, kein Funkgerät verbunden. Nachrichten warten …“ */
    fun messagesWaitWithLink(cause: String, link: String): String
    val allowAccess: String
    fun joinCircleConfirmTitle(circle: String): String
    val joinCircleConfirmText: String
    val dontJoin: String
    fun addContactConfirmTitle(name: String): String
    fun addContactConfirmText(name: String): String
    val addAndWrite: String
    val dontAdd: String
    fun inviteToCircle(circle: String): String
    val inviteStartText: String
    fun contactFrom(name: String): String
    fun contactStartText(name: String): String
    fun joinPointName(ownName: String): String
    val joinPointChat: String
    val joinPointLeave: String
    fun contactPointName(name: String, ownName: String): String
    val contactPointDirect: String
    val contactPointConfirm: String
    val welcomeTitle: String
    val welcomeText: String
    val letsGo: String
    /** Brand tagline; `null` as long as there is no approved wording in this language. */
    val tagline: String?

    /** Note on the usage statistics at the welcome (ADR 0045). */
    val usageStatsNote: String
    val howOthersSeeYou: String
    val photoOptional: String
    val howToStart: String
    val lookAround: String
    val choiceShareText: String
    val choiceLookAroundText: String
    val onlyAdd: String
    val tabFiles: String
    val tabMore: String

    // Guided setup (ADR 0073): per step a heading, one sentence, main button.
    val notificationsTitle: String
    val notificationsText: String
    val notificationsAllow: String
    val cardTitle: String
    val cardText: String
    val cardNote: String
    val cardCreate: String
    val cardReminderTitle: String
    val cardReminderText: String
    val alertsTitle: String
    val alertsText: String
    val alertsLocationNote: String
    val alertsTurnOn: String
    val alertsFailed: String
    val radioTitle: String
    val radioText: String
    val radioYes: String
    val radioNotYet: String
    val radioSearchTitle: String
    val radioBuyTitle: String
    val radioBuyText: String
    val radioBuyLink: String
    val radioContinueWithout: String
    val radioConnecting: String
    val publicTitle: String
    val publicText: String
    val publicNote: String
    val publicYes: String
    val publicNo: String
    /** Radios page on anynear.app with buying recommendations, in this language. */
    val radiosPageUrl: String
}

internal object SetupTextsDe : SetupTexts {
    override fun step(number: Int, total: Int) = "Schritt $number von $total"
    override val welcome = "Willkommen! Wie heißt du?"
    override val nameIntro = "So sehen dich die Menschen in deinen Kreisen. Du kannst ihn später in den Einstellungen ändern."
    override val yourName = "Dein Name"
    override val invitedTitle = "Du wurdest eingeladen"
    override fun circleNamed(name: String) = "Kreis „$name“"
    override val afterJoining = "Nach dem Beitreten kannst du sofort schreiben."
    override val joinCircle = "Kreis beitreten"
    override val haveInvite = "Hast du eine Einladung?"
    override val circleExplained = "Ein Kreis ist eine Gruppe, z. B. deine Familie oder Freunde. Jemand gründet ihn und lädt die anderen mit einem Link ein."
    override val haveLinkTitle = "Ja, ich habe einen Link"
    override val haveLinkText = "Jemand hat dir einen Einladungslink geschickt, z. B. per WhatsApp."
    override val startOwnTitle = "Nein, ich fange an"
    override val startOwnText = "Du gründest einen neuen Kreis und lädst danach die anderen ein."
    override val openInvite = "Einladung öffnen"
    override val inviteLink = "Einladungslink"
    override val scanQrCode = "QR-Code scannen"
    override val startCircle = "Kreis gründen"

    override val radioIntro =
        "Optional: Mit einem Funkgerät erreicht dich dein Kreis auch ohne Internet. " +
            "Schalte es ein und leg es neben das Handy. Es erscheint dann hier."
    override val yourRadio = "Dein Funkgerät"
    override val whichRadio = "Welches ist dein Funkgerät?"
    override val nameOnDisplay = "Den Namen zeigt auch das Display deines Funkgeräts an."
    override val searchBluetooth = "Auch per Bluetooth suchen"
    override val searchBluetoothWhy = "So findet $APP dein Funkgerät auch ohne WLAN, zum Beispiel bei Stromausfall."
    override val allowBluetooth = "Bluetooth erlauben"
    override val searchingRadio = "Suche nach deinem Funkgerät …"
    override val stillSearching = "Suche läuft weiter …"
    override val noRadioFound = "Noch kein Funkgerät gefunden. Ist es eingeschaltet und liegt in der Nähe?"
    override val searchAgain = "Nochmal suchen"
    override val foundNearbyBluetooth = "in der Nähe · Bluetooth"
    override val foundOnWifi = "im WLAN"
    override val connect = "Verbinden"
    override val connectByAddressAdvanced = "Erweitert: Gerät über Adresse verbinden"
    override val connectByAddress = "Gerät über Adresse verbinden"
    override val connectByAddressHelp =
        "Nur nötig, wenn das Funkgerät WLAN eingeschaltet hat und oben nicht erscheint. " +
            "Die Adresse steht z. B. auf dem Display des Geräts oder im Router."
    override val addressPlaceholder = "z. B. 192.168.1.50"
    override val invalidAddress = "Das sieht nicht nach einer Adresse aus. Beispiel: 192.168.1.50"
    override val addressNotReachable = "Unter dieser Adresse antwortet kein Funkgerät. Ist es eingeschaltet und im selben WLAN?"
    override val checkingConnection = "Prüfe Verbindung …"

    override val setUpRadio = "Funkgerät einrichten"
    override val updateRadio = "Funkgerät aktualisieren"
    override val setUpRadioForApp = "Funkgerät für $APP einrichten?"
    override val setupOnlyName = "Dein Funkgerät bekommt einen Namen, an dem $APP es erkennt."
    override val setupUpdate = "Wir haben die Einstellungen für $APP-Funkgeräte verbessert und möchten dein Funkgerät " +
        "aktualisieren. Es kann dabei mehrmals neu starten."
    override val setupNew = "Dein Funkgerät ist neu und wird jetzt für $APP vorbereitet. Es kann dabei mehrmals neu starten."
    override val setupOther = "Dein Funkgerät wird jetzt für $APP eingerichtet. Es kann dabei mehrmals neu starten."
    override val routerWarning = "Achtung: Es ist danach kein Verteiler im Netz mehr."
    override val radioName = "Name des Funkgeräts"
    override val radioNameInvalid = "Bitte mit mindestens einem Buchstaben oder einer Zahl."
    override val radioNameTaken = "Diesen Namen gibt es in deiner Nähe schon."
    override val showLess = "Weniger anzeigen"
    override val setUpAnyway = "Trotzdem einrichten"
    override val update = "Aktualisieren"
    override val setUp = "Einrichten"
    override val detailsUpdate =
        "Dein Funkgerät ist schon für $APP eingerichtet. Seitdem haben sich die $APP-Einstellungen geändert, " +
            "oder am Funkgerät wurde etwas verstellt. Mit der Aktualisierung erreicht es die anderen $APP-Funkgeräte zuverlässig."
    override val detailsOther = "Dein Funkgerät ist anders eingestellt, als $APP es braucht. So erreicht es die anderen $APP-Funkgeräte womöglich nicht."
    override val detailsSettings = "$APP stellt es so ein wie alle $APP-Funkgeräte: gleiche Funkeinstellungen, Gerätestatus einmal pro Stunde, Standort aus."
    override val detailsRouter = "Dein Funkgerät leitet bisher als Verteiler Nachrichten für andere weiter. Danach ist es ein normales Funkgerät."
    override val detailsUnchanged = "WLAN, Bluetooth und Anzeige bleiben, wie sie sind. Das Funkgerät startet dabei neu, manchmal auch mehrmals. Das ist normal."
    override val detailsNameKept = "Der Name deines Funkgeräts bleibt, wie er ist."
    override val detailsNamePublic = "Den Namen sehen alle in Funkreichweite. Am besten kein voller Name einer Person."

    override val tabChats = "Nachrichten"
    override val tabContacts = "Kontakte"
    override val tabCircles = "Kreise"
    override val tabSettings = "Einstellungen"
    override fun newMessages(n: Int) = if (n == 1) "1 neue Nachricht" else "$n neue Nachrichten"

    override val removedFromCircle = "Du wurdest aus diesem Kreis entfernt. Du kannst ihn unter „Kreise“ aus der Liste nehmen."
    override val join = "Beitreten"
    override val backgroundTitle = "Nachrichten auch bei geschlossener App"
    override val backgroundText = "Damit neue Nachrichten immer ankommen, muss $APP im Hintergrund laufen dürfen."
    override val backgroundTapAllow = "Tippe gleich auf „Zulassen“."
    override val backgroundTapBattery = "Tippe gleich auf „Akku“ und erlaube die Nutzung im Hintergrund."
    override val backgroundStopped = "Dein Handy hat $APP im Hintergrund beendet. Dann kommen keine Nachrichten an."
    override val backgroundTapStopped = "Tippe gleich auf „Akku“ und wähle „Nicht eingeschränkt“. Gibt es „Autostart“, schalte es ein."
    override val openSetting = "Einstellung öffnen"
    override val radioFound = "Funkgerät gefunden"
    override fun suggestedRadio(deviceName: String) =
        "In deinem WLAN ist das Funkgerät „$deviceName“. Möchtest du es mit $APP verbinden? " +
            "Dann erreichen dich deine Kreise auch, wenn Internet und Mobilfunk ausfallen."
    override fun suggestedKnownRadio(deviceName: String) =
        "Dein Funkgerät „$deviceName“ ist in der Nähe. Möchtest du es wieder mit $APP verbinden? " +
            "Dann erreichen dich deine Kreise auch, wenn Internet und Mobilfunk ausfallen."
    override val invite = "Einladung"
    override fun invitedTo(circle: String) = "Du wurdest in den Kreis „$circle“ eingeladen."

    override val noConnection = "Gerade keine Verbindung"
    override val appUnreachable = "$APP ist gerade nicht erreichbar"
    override val noInternet = "Kein Internet"
    override fun messagesWait(cause: String) = "$cause. Nachrichten warten und gehen automatisch raus."
    override fun messagesWaitWithLink(cause: String, link: String) = "$cause, $link. Nachrichten warten und gehen automatisch raus."
    override val allowAccess = "Zugriff erlauben"
    override fun joinCircleConfirmTitle(circle: String) = "Dem Kreis „$circle“ beitreten?"
    override val joinCircleConfirmText = "Alle im Kreis sehen deinen Namen und dein Bild, und du schreibst im Kreis-Chat mit. Verlassen kannst du den Kreis jederzeit."
    override val dontJoin = "Nicht beitreten"
    override fun addContactConfirmTitle(name: String) = "$name zu deinen Kontakten hinzufügen?"
    override fun addContactConfirmText(name: String) = "$name sieht deinen Namen und dein Bild, und ihr könnt euch direkt schreiben. Der Kontakt wird erst nach deiner Bestätigung hinzugefügt."
    override val addAndWrite = "Hinzufügen und schreiben"
    override val dontAdd = "Nicht hinzufügen"
    override fun inviteToCircle(circle: String) = "Einladung in den Kreis „$circle“"
    override val inviteStartText = "Im Kreis schreibt ihr zusammen. Erst stellst du dich kurz vor, dann entscheidest du, ob du beitrittst."
    override fun contactFrom(name: String) = "Kontakt von $name"
    override fun contactStartText(name: String) = "Mit diesem Link kannst du $name zu deinen Kontakten hinzufügen. Erst stellst du dich kurz vor, dann entscheidest du."
    override fun joinPointName(ownName: String) = "Alle im Kreis sehen deinen Namen „$ownName“ und dein Bild."
    override val joinPointChat = "Du liest und schreibst im Kreis-Chat mit."
    override val joinPointLeave = "Verlassen kannst du den Kreis jederzeit."
    override fun contactPointName(name: String, ownName: String) = "$name sieht deinen Namen „$ownName“ und dein Bild."
    override val contactPointDirect = "Ihr könnt euch dann direkt schreiben, auch ohne gemeinsamen Kreis."
    override val contactPointConfirm = "Der Kontakt wird erst nach deiner Bestätigung hinzugefügt."
    override val welcomeTitle = "Willkommen bei $APP"
    override val welcomeText = "Schreibe mit deinen Menschen. Über Internet und mit passendem Funkgerät auch über Funk."
    override val letsGo = "Los geht's"
    override val tagline: String? = "Verbunden bleiben. Wenn es zählt."
    override val usageStatsNote = "$APP schickt einmal am Tag ein paar Zahlen, ohne Namen und ohne Inhalte. Du kannst das in den Einstellungen abschalten."
    override val howOthersSeeYou = "Wie sollen dich andere sehen?"
    override val photoOptional = "Das Bild ist freiwillig. Beides kannst du später in den Einstellungen ändern."
    override val howToStart = "Womit möchtest du anfangen?"
    override val lookAround = "Umschauen"
    override val choiceShareText = "Schick deinen Kontakt an Familie oder Freunde. Wer ihn öffnet, kann dir gleich schreiben."
    override val choiceLookAroundText = "Erst einmal die App kennenlernen. Kontakte und Kreise kannst du jederzeit hinzufügen."
    override val onlyAdd = "Nur hinzufügen"
    override val tabFiles = "Ablage"
    override val tabMore = "Mehr"
    override val notificationsTitle = "Benachrichtigungen"
    override val notificationsText = "Damit du merkst, wenn dir jemand schreibt oder eine Warnung kommt."
    override val notificationsAllow = "Benachrichtigungen erlauben"
    override val cardTitle = "Deine Notfallkarte"
    override val cardText = "Damit du alles zurückbekommst, wenn dein Handy weg ist, und ein gestohlenes Handy aussperren kannst."
    override val cardNote = "Du bekommst einen Code. Schreib ihn auf Papier ab und bewahr ihn gut auf."
    override val cardCreate = "Notfallkarte erstellen"
    override val cardReminderTitle = "Notfallkarte fehlt noch"
    override val cardReminderText = "Ohne sie bekommst du nichts zurück, wenn dein Handy weg ist."
    override val alertsTitle = "Warnungen für deine Gegend"
    override val alertsText = "Unwetter, Hochwasser und andere amtliche Warnungen für deinen Landkreis."
    override val alertsLocationNote = "Dafür fragt $APP nach deinem ungefähren Standort. Er bleibt auf dem Handy."
    override val alertsTurnOn = "Warnungen einschalten"
    override val alertsFailed = "Das hat gerade nicht geklappt. Du kannst die Warnungen später unter Meldungen einschalten."
    override val radioTitle = "Hast du ein Funkgerät?"
    override val radioText = "Mit einem Funkgerät kommen Nachrichten auch ohne Internet an, etwa wenn das Handynetz ausfällt."
    override val radioYes = "Ja, jetzt verbinden"
    override val radioNotYet = "Noch keins"
    override val radioSearchTitle = "Funkgerät verbinden"
    override val radioBuyTitle = "So bekommst du ein Funkgerät"
    override val radioBuyText = "Auf unserer Website findest du Funkgeräte, die mit $APP getestet sind. Du kannst es später jederzeit in den Einstellungen verbinden."
    override val radioBuyLink = "Funkgeräte ansehen"
    override val radioContinueWithout = "Weiter ohne Funkgerät"
    override val radioConnecting = "Dein Funkgerät wird verbunden …"
    override val publicTitle = "Öffentliche Chats über Funk"
    override val publicText = "Über dein Funkgerät kannst du auch lesen, was andere Funkgeräte in deiner Nähe an alle senden. Möchtest du diese Chats sehen?"
    override val publicNote = "Das sind oft Fremde. Deine Kreise bleiben davon getrennt. Du kannst das später auf der Seite Funkgerät ändern."
    override val publicYes = "Ja, zeigen"
    override val publicNo = "Nein"
    override val radiosPageUrl = "https://anynear.app/de/funkgeraete#geraete"
}

internal object SetupTextsEn : SetupTexts {
    override fun step(number: Int, total: Int) = "Step $number of $total"
    override val welcome = "Welcome! What's your name?"
    override val nameIntro = "This is how people in your circles see you. You can change it later in Settings."
    override val yourName = "Your name"
    override val invitedTitle = "You've been invited"
    override fun circleNamed(name: String) = "Circle “$name”"
    override val afterJoining = "Once you join, you can start writing right away."
    override val joinCircle = "Join circle"
    override val haveInvite = "Do you have an invite?"
    override val circleExplained = "A circle is a group, like your family or friends. Someone starts it and invites the others with a link."
    override val haveLinkTitle = "Yes, I have a link"
    override val haveLinkText = "Someone sent you an invite link, for example on WhatsApp."
    override val startOwnTitle = "No, I'll start one"
    override val startOwnText = "You start a new circle and then invite the others."
    override val openInvite = "Open invite"
    override val inviteLink = "Invite link"
    override val scanQrCode = "Scan QR code"
    override val startCircle = "Start a circle"

    override val radioIntro =
        "Optional: With a radio, your circle can reach you even without internet. " +
            "Turn it on and put it next to your phone. It will then show up here."
    override val yourRadio = "Your radio"
    override val whichRadio = "Which one is your radio?"
    override val nameOnDisplay = "Your radio's display shows the name too."
    override val searchBluetooth = "Also search via Bluetooth"
    override val searchBluetoothWhy = "This lets $APP find your radio even without Wi-Fi, for example during a power outage."
    override val allowBluetooth = "Allow Bluetooth"
    override val searchingRadio = "Looking for your radio …"
    override val stillSearching = "Still looking …"
    override val noRadioFound = "No radio found yet. Is it turned on and nearby?"
    override val searchAgain = "Search again"
    override val foundNearbyBluetooth = "nearby · Bluetooth"
    override val foundOnWifi = "on Wi-Fi"
    override val connect = "Connect"
    override val connectByAddressAdvanced = "Advanced: Connect device by address"
    override val connectByAddress = "Connect device by address"
    override val connectByAddressHelp =
        "Only needed if the radio has Wi-Fi turned on and doesn't show up above. " +
            "You can find the address on the device's display or in your router, for example."
    override val addressPlaceholder = "e.g. 192.168.1.50"
    override val invalidAddress = "That doesn't look like an address. Example: 192.168.1.50"
    override val addressNotReachable = "No radio answers at this address. Is it turned on and on the same Wi-Fi?"
    override val checkingConnection = "Checking connection …"

    override val setUpRadio = "Set up radio"
    override val updateRadio = "Update radio"
    override val setUpRadioForApp = "Set up radio for $APP?"
    override val setupOnlyName = "Your radio gets a name so $APP can recognize it."
    override val setupUpdate = "We've improved the settings for $APP radios and would like to update your radio. " +
        "It may restart several times."
    override val setupNew = "Your radio is new and will now be prepared for $APP. It may restart several times."
    override val setupOther = "Your radio will now be set up for $APP. It may restart several times."
    override val routerWarning = "Note: Afterwards it will no longer pass on messages for others."
    override val radioName = "Radio name"
    override val radioNameInvalid = "Please use at least one letter or number."
    override val radioNameTaken = "This name is already used nearby."
    override val showLess = "Show less"
    override val setUpAnyway = "Set up anyway"
    override val update = "Update"
    override val setUp = "Set up"
    override val detailsUpdate =
        "Your radio is already set up for $APP. Since then, the $APP settings have changed, " +
            "or something was changed on the radio. With the update, it reliably reaches the other $APP radios."
    override val detailsOther = "Your radio is set up differently from what $APP needs. It may not reach the other $APP radios."
    override val detailsSettings = "$APP sets it up like all $APP radios: same radio settings, device status once an hour, location off."
    override val detailsRouter = "Your radio has been passing on messages for others so far. Afterwards it will be a normal radio."
    override val detailsUnchanged = "Wi-Fi, Bluetooth and display stay as they are. The radio restarts during this, sometimes several times. That's normal."
    override val detailsNameKept = "Your radio's name stays as it is."
    override val detailsNamePublic = "Everyone within radio range can see the name. Best not to use a person's full name."

    override val tabChats = "Messages"
    override val tabContacts = "Contacts"
    override val tabCircles = "Circles"
    override val tabSettings = "Settings"
    override fun newMessages(n: Int) = if (n == 1) "1 new message" else "$n new messages"

    override val removedFromCircle = "You were removed from this circle. You can remove it from the list under “Circles”."
    override val join = "Join"
    override val backgroundTitle = "Messages even when the app is closed"
    override val backgroundText = "So new messages always arrive, $APP needs to be allowed to run in the background."
    override val backgroundTapAllow = "Tap “Allow” next."
    override val backgroundTapBattery = "Tap “Battery” next and allow background use."
    override val backgroundStopped = "Your phone closed $APP in the background. Then no messages arrive."
    override val backgroundTapStopped = "Tap “Battery” next and choose “Unrestricted”. If there is “Autostart”, turn it on."
    override val openSetting = "Open setting"
    override val radioFound = "Radio found"
    override fun suggestedRadio(deviceName: String) =
        "The radio “$deviceName” is on your Wi-Fi. Do you want to connect it to $APP? " +
            "Then your circles can reach you even when internet and mobile service are down."
    override fun suggestedKnownRadio(deviceName: String) =
        "Your radio “$deviceName” is nearby. Do you want to connect it to $APP again? " +
            "Then your circles can reach you even when internet and mobile service are down."
    override val invite = "Invite"
    override fun invitedTo(circle: String) = "You've been invited to the circle “$circle”."

    override val noConnection = "No connection right now"
    override val appUnreachable = "$APP can't be reached right now"
    override val noInternet = "No internet"
    override fun messagesWait(cause: String) = "$cause. Messages will wait and go out automatically."
    override fun messagesWaitWithLink(cause: String, link: String) = "$cause, $link. Messages will wait and go out automatically."
    override val allowAccess = "Allow access"
    override fun joinCircleConfirmTitle(circle: String) = "Join the circle “$circle”?"
    override val joinCircleConfirmText = "Everyone in the circle sees your name and picture, and you read and write in the circle chat. You can leave the circle at any time."
    override val dontJoin = "Don't join"
    override fun addContactConfirmTitle(name: String) = "Add $name to your contacts?"
    override fun addContactConfirmText(name: String) = "$name will see your name and picture, and you can message each other directly. The contact is only added once you confirm."
    override val addAndWrite = "Add and message"
    override val dontAdd = "Don't add"
    override fun inviteToCircle(circle: String) = "Invitation to the circle “$circle”"
    override val inviteStartText = "In a circle you write together. First you introduce yourself, then you decide whether to join."
    override fun contactFrom(name: String) = "Contact from $name"
    override fun contactStartText(name: String) = "With this link you can add $name to your contacts. First you introduce yourself, then you decide."
    override fun joinPointName(ownName: String) = "Everyone in the circle sees your name “$ownName” and your picture."
    override val joinPointChat = "You read and write in the circle chat."
    override val joinPointLeave = "You can leave the circle at any time."
    override fun contactPointName(name: String, ownName: String) = "$name sees your name “$ownName” and your picture."
    override val contactPointDirect = "You can then message each other directly, even without a shared circle."
    override val contactPointConfirm = "The contact is only added once you confirm."
    override val welcomeTitle = "Welcome to $APP"
    override val welcomeText = "Write with your people. Over the internet, and with a suitable radio also over radio."
    override val letsGo = "Let's go"
    // No approved English brand wording yet, therefore hidden.
    override val tagline: String? = "Stay connected. When it matters."
    override val usageStatsNote = "$APP sends a few numbers once a day, without names or content. You can switch this off in the settings."
    override val howOthersSeeYou = "How should others see you?"
    override val photoOptional = "The picture is optional. You can change both later in Settings."
    override val howToStart = "How would you like to start?"
    override val lookAround = "Look around"
    override val choiceShareText = "Send your contact to family or friends. Whoever opens it can write to you right away."
    override val choiceLookAroundText = "Get to know the app first. You can add contacts and circles any time."
    override val onlyAdd = "Just add"
    override val tabFiles = "Files"
    override val tabMore = "More"
    override val notificationsTitle = "Notifications"
    override val notificationsText = "So you notice when someone writes to you or a warning comes in."
    override val notificationsAllow = "Allow notifications"
    override val cardTitle = "Your emergency card"
    override val cardText = "So that you get everything back if your phone is gone, and can lock out a stolen phone."
    override val cardNote = "You get a code. Write it down on paper and keep it safe."
    override val cardCreate = "Create emergency card"
    override val cardReminderTitle = "Emergency card still missing"
    override val cardReminderText = "Without it you get nothing back if your phone is gone."
    override val alertsTitle = "Warnings for your area"
    override val alertsText = "Storms, floods and other official warnings for your district."
    override val alertsLocationNote = "For this, $APP asks for your approximate location. It stays on your phone."
    override val alertsTurnOn = "Turn on warnings"
    override val alertsFailed = "That didn't work just now. You can turn on warnings later under Alerts."
    override val radioTitle = "Do you have a radio?"
    override val radioText = "With a radio, messages get through even without internet, for example when the mobile network fails."
    override val radioYes = "Yes, connect now"
    override val radioNotYet = "Not yet"
    override val radioSearchTitle = "Connect radio"
    override val radioBuyTitle = "How to get a radio"
    override val radioBuyText = "On our website you'll find radios tested with $APP. You can connect one any time later in Settings."
    override val radioBuyLink = "See radios"
    override val radioContinueWithout = "Continue without a radio"
    override val radioConnecting = "Connecting your radio …"
    override val publicTitle = "Public chats over radio"
    override val publicText = "With your radio you can also read what other radios nearby send to everyone. Would you like to see these chats?"
    override val publicNote = "These are often strangers. Your circles stay separate. You can change this later on the Radio page."
    override val publicYes = "Yes, show them"
    override val publicNo = "No"
    override val radiosPageUrl = "https://anynear.app/en/radios#geraete"
}

