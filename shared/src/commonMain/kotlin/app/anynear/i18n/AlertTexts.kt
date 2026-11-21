package app.anynear.i18n

/**
 * Alerts and channels (ADR 0062). "Warning" only for official sources, own and shared channels speak of
 * alerts and alarms (§7).
 */
interface AlertTexts {
    val title: String
    val activeSection: String
    val ack: String
    val unack: String
    fun ackedHidden(count: Int): String
    fun regionHint(region: String): String
    val channelsSection: String
    val emptyTitle: String
    val emptyText: String
    val createChannel: String
    val noAlertYet: String
    fun newAlerts(count: Int): String
    fun validUntil(time: String): String
    fun fromSource(source: String): String
    val kindOwn: String
    val kindSubscribed: String
    val kindOfficial: String

    // Priorities and state
    val prioInfo: String
    val prioImportant: String
    val prioWarning: String
    val prioAlarm: String
    val allClear: String
    val statusActive: String
    val statusEnded: String
    val statusExpired: String

    // Official warnings
    val officialSection: String
    val officialTitle: String
    val officialSubtitle: String
    val officialTurnOn: String
    val officialFooter: String
    val regionNeedsLocation: String
    val allowLocation: String
    val regionLocating: String
    val regionFailed: String
    val regionAbroad: String
    val regionInfo: String

    // Channel
    val share: String
    val send: String
    val history: String
    val viewChannel: String
    val noHistory: String
    val openContext: String
    val imageUnavailable: String
    val onThisDevice: String
    val notifications: String
    val notificationsOn: String
    val notificationsOff: String
    val loudFrom: String
    val loudOnlyAlarm: String
    val loudFromImportant: String
    val loudFromWarning: String
    val loudAlways: String
    val loudNever: String
    val loudFooter: String
    val sound: String
    val soundAlarm: String
    val soundOfficial: String
    val soundHint: String
    val soundWarning: String
    val soundSos: String
    val soundQuestion: String
    val rename: String
    val leave: String
    val remove: String
    fun leaveTitle(name: String): String
    val leaveText: String
    val removeText: String

    // Test alarm
    val testAlarm: String
    val testAlarmSubtitle: String
    val testTitle: String
    fun testText(channel: String): String
    val testAllGood: String
    val testMissing: String
    val problemNotifications: String
    val problemDoNotDisturb: String
    val problemBattery: String
    val problemFullScreen: String
    val problemTimeSensitive: String
    val problemSilentSwitch: String

    // Create channel, send
    val createTitle: String
    val createText: String
    val channelName: String
    val create: String
    val composeTitle: String
    val priority: String
    val composeTitleField: String
    val composeText: String
    val composeUrl: String
    val composeInvalid: String
    val resetLink: String
    val resetLinkText: String
    val resetLinkDone: String

    // Subscribers (owner only, ADR 0062 §11)
    val subscribersSection: String
    val noSubscribers: String
    val subscribersFooter: String
    fun subscriberSince(date: String): String
    fun removeSubscriberTitle(name: String): String
    fun removeSubscriberText(name: String): String
    val removeSubscriber: String
    fun subscriberRemoved(name: String): String
    val someone: String
    val sent: String

    // Give the all-clear (owner only, active alert)
    val endAlert: String
    fun endAlertTitle(title: String): String
    val endAlertText: String
    val endAlertDefaultText: String
    val endAlertSent: String

    // Addresses for devices
    val addressesSection: String
    val addressesFooter: String
    val noAddress: String
    val addAddress: String
    val addressLabel: String
    val addressLabelDefault: String
    fun addressCreated(date: String): String
    val exampleHomeAssistant: String
    val exampleCommandLine: String
    fun exampleHomeAssistantCode(url: String): String
    fun exampleCommandLineCode(url: String): String
    val revoke: String
    fun revokeTitle(label: String): String
    val revokeText: String
    val relayNote: String

    // Invitation link
    /** Channel link for sharing in other apps, like the invitation to a circle; [name] is the own name. */
    fun shareChannel(name: String, channel: String, url: String): String
    fun joinTitle(name: String): String
    val joinText: String
    val joinSeenText: String
    val joinOfficialText: String
    val join: String
    val dontJoin: String
    fun joined(name: String): String
    fun alreadyJoined(name: String): String
    val linkBroken: String
    val linkForeignNetwork: String
    val failed: String

    // Hint „Nicht stören“
    val accessTitle: String
    val accessText: String

    // Android: notification channels and full screen
    val channelNormal: String
    fun channelAlarm(sound: String): String
    val channelAlarmDescription: String
    val silence: String
    val open: String

    // Warnings on the map (ADR 0121 §7): a tap on a coloured district
    fun mapWarnings(count: Int): String
    val kindWeather: String
    val kindFlood: String
    val kindPolice: String
    val kindCivilProtection: String
    fun validSince(time: String): String
    fun area(names: String): String
    fun moreAreas(count: Int): String
    val openInAlerts: String

    /** States of Germany by the first two digits of the regional key. */
    val states: Map<String, String>
}

internal object AlertTextsDe : AlertTexts {
    override val title = "Meldungen"
    override val activeSection = "Gerade aktiv"
    override val ack = "Gesehen"
    override val unack = "Wieder einblenden"
    override fun ackedHidden(count: Int) = if (count == 1) "1 gesehene ausgeblendet" else "$count gesehene ausgeblendet"
    override fun regionHint(region: String) = "Für $region, aus deinem ungefähren Standort."
    override val channelsSection = "Kanäle"
    override val emptyTitle = "Noch keine Kanäle"
    override val emptyText = "Über einen Kanal bekommst du Meldungen, zum Beispiel vom Rauchmelder oder der Kamera im Garten. Teilen kannst du ihn mit deiner Familie."
    override val createChannel = "Kanal anlegen"
    override val noAlertYet = "Noch keine Meldung"
    override fun newAlerts(count: Int) = if (count == 1) "1 neue Meldung" else "$count neue Meldungen"
    override fun validUntil(time: String) = "gültig bis $time"
    override fun fromSource(source: String) = "Quelle: $source"
    override val kindOwn = "Eigener Kanal"
    override val kindSubscribed = "Abonniert"
    override val kindOfficial = "Amtlich"

    override val prioInfo = "Info"
    override val prioImportant = "Wichtig"
    override val prioWarning = "Warnung"
    override val prioAlarm = "Alarm"
    override val allClear = "Entwarnung"
    override val statusActive = "aktiv"
    override val statusEnded = "entwarnt"
    override val statusExpired = "abgelaufen"

    override val officialSection = "Amtliche Warnungen"
    override val officialTitle = "Amtliche Warnungen (NINA)"
    override val officialSubtitle = "Warnungen der Behörden für deinen Ort"
    override val officialTurnOn = "Einschalten"
    override val officialFooter = "$APP ersetzt weder NINA noch die Warnungen deines Telefons. Lass diese eingeschaltet."
    override val regionNeedsLocation = "Für Warnungen in deiner Nähe braucht $APP deinen ungefähren Standort."
    override val allowLocation = "Standort erlauben"
    override val regionLocating = "Dein Kreis wird gesucht …"
    override val regionFailed = "Dein Standort ist gerade nicht zu bekommen. Ist die Ortung am Handy eingeschaltet?"
    override val regionAbroad = "Amtliche Warnungen gibt es nur für Orte in Deutschland."
    override val regionInfo = "$APP bestimmt deinen ungefähren Standort nur, wenn die App offen ist, und sucht deinen Kreis in einer Liste auf deinem Handy. Dein Standort verlässt das Handy nie, gespeichert wird nur der Kreis. Bist du woanders, passt sich der Kreis beim nächsten Öffnen an."

    override val share = "Teilen"
    override val send = "Meldung senden"
    override val history = "Verlauf"
    override val viewChannel = "Kanal ansehen"
    override val noHistory = "Hier erscheinen die Meldungen dieses Kanals."
    override val openContext = "Öffnen"
    override val imageUnavailable = "Bild gerade nicht erreichbar"
    override val onThisDevice = "Auf diesem Gerät"
    override val notifications = "Benachrichtigungen"
    override val notificationsOn = "Bei jeder Meldung"
    override val notificationsOff = "Aus, Meldungen nur im Verlauf"
    override val loudFrom = "Laut, auch bei Stummschaltung"
    override val loudOnlyAlarm = "Nur Alarm"
    override val loudFromImportant = "Ab „Wichtig“"
    override val loudFromWarning = "Ab „Warnung“"
    override val loudAlways = "Immer"
    override val loudNever = "Nie"
    override val loudFooter = "Laut heißt: Alarmton in Weckerlautstärke, auch wenn das Handy stumm ist. Sonst kommt die Meldung mit dem normalen Ton."
    override val sound = "Ton"
    override val soundAlarm = "Alarm"
    override val soundOfficial = "Amtlich"
    override val soundHint = "Hinweis"
    override val soundWarning = "Warnton"
    override val soundSos = "Hilfe rufen"
    override val soundQuestion = "Lebenszeichen"
    override val rename = "Umbenennen"
    override val leave = "Kanal verlassen"
    override val remove = "Kanal entfernen"
    override fun leaveTitle(name: String) = "„$name“ verlassen?"
    override val leaveText = "Du bekommst dann keine Meldungen mehr aus diesem Kanal."
    override val removeText = "Der Kanal verschwindet von diesem Gerät. Wer ihn abonniert hat, bekommt keine neuen Meldungen mehr von dir."

    override val testAlarm = "Probealarm"
    override val testAlarmSubtitle = "Prüfen, ob ein Alarm hier wirklich klingelt"
    override val testTitle = "Probealarm"
    override fun testText(channel: String) = "So klingt ein Alarm aus „$channel“."
    override val testAllGood = "Alles bereit. Ein Alarm klingelt auf diesem Gerät."
    override val testMissing = "Das fehlt noch:"
    override val problemNotifications = "Benachrichtigungen sind aus."
    override val problemDoNotDisturb = "„Nicht stören“ ist nicht freigegeben."
    override val problemBattery = "Akku-Optimierung ist an."
    override val problemFullScreen = "Vollbild auf dem Sperrbildschirm ist nicht erlaubt."
    override val problemTimeSensitive = "Dringende Mitteilungen sind aus."
    override val problemSilentSwitch = "Bei stummem iPhone bleibt der Alarm leise. Das erlaubt Apple erst nach einer Freigabe, die $APP beantragt hat."

    override val createTitle = "Kanal anlegen"
    override val createText = "Gib ihm einen Namen. Danach kannst du ihn teilen und Geräte anschließen."
    override val channelName = "Name, z. B. Haus"
    override val create = "Anlegen"
    override val composeTitle = "Meldung senden"
    override val priority = "Priorität"
    override val composeTitleField = "Titel (optional)"
    override val composeText = "Text"
    override val composeUrl = "Link zum Öffnen (optional)"
    override val composeInvalid = "So geht die Meldung nicht raus. Prüfe die Länge und ob der Link mit https:// beginnt."
    override val resetLink = "Link zurücksetzen"
    override val resetLinkText = "Alle, die den Kanal abonniert haben, bekommen danach keine Meldungen mehr. Teile den neuen Link mit denen, die dabei bleiben sollen."
    override val resetLinkDone = "Neuer Link erstellt. Teile ihn jetzt."
    override val subscribersSection = "Abonnenten"
    override val noSubscribers = "Noch niemand hat den Kanal abonniert."
    override val subscribersFooter = "Nur du siehst, wer dabei ist. Wer eine ältere Version von $APP hat, fehlt hier."
    override fun subscriberSince(date: String) = "dabei seit $date"
    override fun removeSubscriberTitle(name: String) = "$name entfernen?"
    override fun removeSubscriberText(name: String) = "$name bekommt keine neuen Meldungen mehr. Alle anderen bleiben dabei. Wer den Kanal noch abonnieren soll, braucht danach den neuen Link."
    override val removeSubscriber = "Entfernen"
    override fun subscriberRemoved(name: String) = "$name ist nicht mehr dabei."
    override val someone = "Ohne Namen"
    override val sent = "Meldung gesendet"

    override val endAlert = "Entwarnen"
    override fun endAlertTitle(title: String) = "Entwarnung für „$title“ senden?"
    override val endAlertText = "Alle im Kanal sehen dann, dass es vorbei ist."
    override val endAlertDefaultText = "Aufgehoben."
    override val endAlertSent = "Entwarnung gesendet"

    override val addressesSection = "Adressen für Geräte"
    override val addressesFooter = "Über eine Adresse schicken Geräte wie Home Assistant, eine Kamera oder ein Skript Meldungen in diesen Kanal. Wer die Adresse kennt, kann senden, aber nichts lesen."
    override val noAddress = "Noch keine Adresse"
    override val addAddress = "Adresse anlegen"
    override val addressLabel = "Name, z. B. Home Assistant"
    override val addressLabelDefault = "Gerät"
    override fun addressCreated(date: String) = "angelegt $date"
    override val exampleHomeAssistant = "Beispiel für Home Assistant"
    override val exampleCommandLine = "Beispiel für die Kommandozeile"
    override fun exampleHomeAssistantCode(url: String) =
        "rest_command:\n  anynear:\n    url: \"$url\"\n    method: post\n    content_type: application/json\n    payload: >\n      {{ {\"title\": title, \"message\": message, \"priority\": priority | default(\"warning\")} | to_json }}"
    override fun exampleCommandLineCode(url: String) = "curl -d title=Garten -d message=\"Bewegung im Garten\" -d priority=alarm $url"
    override val revoke = "Widerrufen"
    override fun revokeTitle(label: String) = "„$label“ widerrufen?"
    override val revokeText = "Die Adresse nimmt danach nichts mehr an. Geräte, die sie nutzen, brauchen eine neue."
    override val relayNote = "Während eine Meldung über die Adresse hereinkommt, sieht der Server sie kurz im Klartext, so wie bei jedem Benachrichtigungsdienst. Danach liegt sie nur verschlüsselt."

    override fun shareChannel(name: String, channel: String, url: String) = "$name lädt dich zum Kanal „$channel“ bei $APP ein: $url"
    override fun joinTitle(name: String) = "„$name“ abonnieren?"
    override val joinText = "Du bekommst die Meldungen dieses Kanals auf diesem Gerät. Niemand sieht, dass du ihn abonniert hast."
    override val joinSeenText = "Du bekommst die Meldungen dieses Kanals auf diesem Gerät. Der Besitzer sieht, dass du dabei bist."
    override val joinOfficialText = "Du bekommst amtliche Warnungen für deinen Kreis. Dafür nutzt $APP deinen ungefähren Standort, nur auf deinem Handy. Niemand erfährt, wo du bist."
    override val join = "Abonnieren"
    override val dontJoin = "Nicht abonnieren"
    override fun joined(name: String) = "„$name“ abonniert"
    override fun alreadyJoined(name: String) = "„$name“ hast du schon abonniert."
    override val linkBroken = "Dieser Kanallink ist unvollständig. Lass ihn dir noch einmal schicken."
    override val linkForeignNetwork = "Dieser Kanal gehört zu einem anderen Netz."
    override val failed = "Das hat gerade nicht geklappt. Versuch es gleich noch einmal."

    override val accessTitle = "Alarme auch bei „Nicht stören“"
    override val accessText = "Damit ein Alarm auch bei „Nicht stören“ klingelt, braucht $APP dort eine Freigabe. Tippe auf $APP und erlaube den Zugriff."

    override val channelNormal = "Meldungen"
    override fun channelAlarm(sound: String) = "Alarme (Ton: $sound)"
    override val channelAlarmDescription = "Laut, auch bei stummem Handy und bei „Nicht stören“."
    override val silence = "Ton aus"
    override val open = "Öffnen"

    override fun mapWarnings(count: Int) = if (count == 1) "Amtliche Warnung hier" else "$count amtliche Warnungen hier"
    override val kindWeather = "Unwetter"
    override val kindFlood = "Hochwasser"
    override val kindPolice = "Polizei"
    override val kindCivilProtection = "Bevölkerungsschutz"
    override fun validSince(time: String) = "gilt seit $time"
    override fun area(names: String) = "Gebiet: $names"
    override fun moreAreas(count: Int) = "und $count weitere"
    override val openInAlerts = "In „Meldungen“ ansehen"
    override val states = mapOf(
        "01" to "Schleswig-Holstein", "02" to "Hamburg", "03" to "Niedersachsen", "04" to "Bremen",
        "05" to "Nordrhein-Westfalen", "06" to "Hessen", "07" to "Rheinland-Pfalz", "08" to "Baden-Württemberg",
        "09" to "Bayern", "10" to "Saarland", "11" to "Berlin", "12" to "Brandenburg",
        "13" to "Mecklenburg-Vorpommern", "14" to "Sachsen", "15" to "Sachsen-Anhalt", "16" to "Thüringen",
    )
}

internal object AlertTextsEn : AlertTexts {
    override val title = "Alerts"
    override val activeSection = "Active now"
    override val ack = "Got it"
    override val unack = "Show again"
    override fun ackedHidden(count: Int) = "$count seen, hidden"
    override fun regionHint(region: String) = "For $region, from your approximate location."
    override val channelsSection = "Channels"
    override val emptyTitle = "No channels yet"
    override val emptyText = "A channel brings you alerts, for example from the smoke detector or the camera in the garden. You can share it with your family."
    override val createChannel = "Create channel"
    override val noAlertYet = "No alert yet"
    override fun newAlerts(count: Int) = if (count == 1) "1 new alert" else "$count new alerts"
    override fun validUntil(time: String) = "valid until $time"
    override fun fromSource(source: String) = "Source: $source"
    override val kindOwn = "Your channel"
    override val kindSubscribed = "Subscribed"
    override val kindOfficial = "Official"

    override val prioInfo = "Info"
    override val prioImportant = "Important"
    override val prioWarning = "Warning"
    override val prioAlarm = "Alarm"
    override val allClear = "All clear"
    override val statusActive = "active"
    override val statusEnded = "all clear"
    override val statusExpired = "expired"

    override val officialSection = "Official warnings"
    override val officialTitle = "Official warnings (NINA)"
    override val officialSubtitle = "Warnings from the authorities for where you are"
    override val officialTurnOn = "Turn on"
    override val officialFooter = "$APP replaces neither NINA nor the emergency alerts of your phone. Keep those turned on."
    override val regionNeedsLocation = "For warnings near you, $APP needs your approximate location."
    override val allowLocation = "Allow location"
    override val regionLocating = "Looking for your district …"
    override val regionFailed = "Your location isn't available right now. Is location turned on on your phone?"
    override val regionAbroad = "Official warnings are only available for places in Germany."
    override val regionInfo = "$APP finds your approximate location only while the app is open and looks up your district in a list on your phone. Your location never leaves the phone, only the district is saved. When you are somewhere else, the district updates the next time you open the app."

    override val share = "Share"
    override val send = "Send alert"
    override val history = "History"
    override val viewChannel = "View channel"
    override val noHistory = "Alerts of this channel will show up here."
    override val openContext = "Open"
    override val imageUnavailable = "Picture not available right now"
    override val onThisDevice = "On this device"
    override val notifications = "Notifications"
    override val notificationsOn = "For every alert"
    override val notificationsOff = "Off, alerts only in the history"
    override val loudFrom = "Loud, even on silent"
    override val loudOnlyAlarm = "Only alarms"
    override val loudFromImportant = "From “Important”"
    override val loudFromWarning = "From “Warning”"
    override val loudAlways = "Always"
    override val loudNever = "Never"
    override val loudFooter = "Loud means: alarm sound at alarm clock volume, even when your phone is on silent. Otherwise the alert comes with the normal sound."
    override val sound = "Sound"
    override val soundAlarm = "Alarm"
    override val soundOfficial = "Official"
    override val soundHint = "Chime"
    override val soundWarning = "Warning"
    override val soundSos = "Call for help"
    override val soundQuestion = "Check-in"
    override val rename = "Rename"
    override val leave = "Leave channel"
    override val remove = "Remove channel"
    override fun leaveTitle(name: String) = "Leave “$name”?"
    override val leaveText = "You won't get alerts from this channel anymore."
    override val removeText = "The channel disappears from this device. Those who subscribed won't get new alerts from you anymore."

    override val testAlarm = "Test alarm"
    override val testAlarmSubtitle = "Check that an alarm really rings here"
    override val testTitle = "Test alarm"
    override fun testText(channel: String) = "This is how an alarm from “$channel” sounds."
    override val testAllGood = "All set. An alarm rings on this device."
    override val testMissing = "Still missing:"
    override val problemNotifications = "Notifications are off."
    override val problemDoNotDisturb = "“Do Not Disturb” access is not allowed."
    override val problemBattery = "Battery optimization is on."
    override val problemFullScreen = "Full screen on the lock screen is not allowed."
    override val problemTimeSensitive = "Time Sensitive notifications are off."
    override val problemSilentSwitch = "With the iPhone on silent, the alarm stays quiet. Apple only allows this after an approval $APP has applied for."

    override val createTitle = "Create channel"
    override val createText = "Give it a name. Then you can share it and connect devices."
    override val channelName = "Name, e.g. Home"
    override val create = "Create"
    override val composeTitle = "Send alert"
    override val priority = "Priority"
    override val composeTitleField = "Title (optional)"
    override val composeText = "Text"
    override val composeUrl = "Link to open (optional)"
    override val composeInvalid = "This alert can't be sent like this. Check the length and that the link starts with https://."
    override val resetLink = "Reset link"
    override val resetLinkText = "Everyone who subscribed will stop getting alerts. Share the new link with those who should stay."
    override val resetLinkDone = "New link created. Share it now."
    override val subscribersSection = "Subscribers"
    override val noSubscribers = "Nobody has subscribed yet."
    override val subscribersFooter = "Only you see who is in. People with an older version of $APP are missing here."
    override fun subscriberSince(date: String) = "since $date"
    override fun removeSubscriberTitle(name: String) = "Remove $name?"
    override fun removeSubscriberText(name: String) = "$name won't get new alerts anymore. Everyone else stays. Anyone who should still subscribe needs the new link afterwards."
    override val removeSubscriber = "Remove"
    override fun subscriberRemoved(name: String) = "$name is no longer in."
    override val someone = "No name"
    override val sent = "Alert sent"

    override val endAlert = "Give all clear"
    override fun endAlertTitle(title: String) = "Send all clear for “$title”?"
    override val endAlertText = "Everyone in the channel will see that it's over."
    override val endAlertDefaultText = "Canceled."
    override val endAlertSent = "All clear sent"

    override val addressesSection = "Addresses for devices"
    override val addressesFooter = "Through an address, devices like Home Assistant, a camera or a script send alerts into this channel. Whoever knows the address can send, but can't read anything."
    override val noAddress = "No address yet"
    override val addAddress = "Create address"
    override val addressLabel = "Name, e.g. Home Assistant"
    override val addressLabelDefault = "Device"
    override fun addressCreated(date: String) = "created $date"
    override val exampleHomeAssistant = "Example for Home Assistant"
    override val exampleCommandLine = "Example for the command line"
    override fun exampleHomeAssistantCode(url: String) =
        "rest_command:\n  anynear:\n    url: \"$url\"\n    method: post\n    content_type: application/json\n    payload: >\n      {{ {\"title\": title, \"message\": message, \"priority\": priority | default(\"warning\")} | to_json }}"
    override fun exampleCommandLineCode(url: String) = "curl -d title=Garden -d message=\"Motion in the garden\" -d priority=alarm $url"
    override val revoke = "Revoke"
    override fun revokeTitle(label: String) = "Revoke “$label”?"
    override val revokeText = "The address won't accept anything after that. Devices using it need a new one."
    override val relayNote = "While an alert comes in through the address, the server briefly sees it in plain text, just like any notification service. After that it is only stored encrypted."

    override fun shareChannel(name: String, channel: String, url: String) = "$name invites you to the channel “$channel” on $APP: $url"
    override fun joinTitle(name: String) = "Subscribe to “$name”?"
    override val joinText = "You get the alerts of this channel on this device. Nobody sees that you subscribed."
    override val joinSeenText = "You get the alerts of this channel on this device. The owner sees that you are in."
    override val joinOfficialText = "You get official warnings for your district. For this, $APP uses your approximate location, only on your phone. Nobody learns where you are."
    override val join = "Subscribe"
    override val dontJoin = "Don't subscribe"
    override fun joined(name: String) = "Subscribed to “$name”"
    override fun alreadyJoined(name: String) = "You already subscribed to “$name”."
    override val linkBroken = "This channel link is incomplete. Ask for it to be sent again."
    override val linkForeignNetwork = "This channel belongs to another network."
    override val failed = "That didn't work just now. Try again in a moment."

    override val accessTitle = "Alarms during “Do Not Disturb”"
    override val accessText = "For an alarm to ring during “Do Not Disturb”, $APP needs access there. Tap $APP and allow access."

    override val channelNormal = "Alerts"
    override fun channelAlarm(sound: String) = "Alarms (sound: $sound)"
    override val channelAlarmDescription = "Loud, even when your phone is on silent or in “Do Not Disturb”."
    override val silence = "Silence"
    override val open = "Open"

    override fun mapWarnings(count: Int) = if (count == 1) "Official warning here" else "$count official warnings here"
    override val kindWeather = "Severe weather"
    override val kindFlood = "Flood"
    override val kindPolice = "Police"
    override val kindCivilProtection = "Civil protection"
    override fun validSince(time: String) = "in effect since $time"
    override fun area(names: String) = "Area: $names"
    override fun moreAreas(count: Int) = "and $count more"
    override val openInAlerts = "View in “Alerts”"
    override val states = mapOf(
        "01" to "Schleswig-Holstein", "02" to "Hamburg", "03" to "Lower Saxony", "04" to "Bremen",
        "05" to "North Rhine-Westphalia", "06" to "Hesse", "07" to "Rhineland-Palatinate", "08" to "Baden-Württemberg",
        "09" to "Bavaria", "10" to "Saarland", "11" to "Berlin", "12" to "Brandenburg",
        "13" to "Mecklenburg-Western Pomerania", "14" to "Saxony", "15" to "Saxony-Anhalt", "16" to "Thuringia",
    )
}
