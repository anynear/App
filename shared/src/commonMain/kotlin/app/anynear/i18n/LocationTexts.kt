package app.anynear.i18n

/** Who sees where the person is: the question in the setup and after the update, and the settings (ADR 0121 §3, §10). */
interface LocationTexts {
    val questionTitle: String
    val questionText: String
    val questionNote: String
    fun appliesTo(circles: String): String
    val yes: String
    val no: String
    val setupTitle: String
    val setupText: String
    val setupNo: String
    val setupLaterNote: String
    val settingsTitle: String
    val whenNeeded: String
    val whenNeededOn: String
    val whenNeededOff: String
    val settingsFooter: String
    val settingsFooterMap: String
    val circleOn: String
    val circleOff: String
    val allowTitle: String
    val allowText: String
    fun metres(value: Int): String
    fun kilometres(value: String): String

    // The one sheet and the hint line for the permission of an action (ADR 0125).
    val accessTitle: String
    val accessAllow: String
    val accessSkip: String
    val accessOff: String
    val accessSosContact: String
    val accessLifeSign: String
    val accessShare: String
    val accessSendPlace: String
    val accessLevel: String
    val accessAlways: String
    val missingTitle: String
    val roughTitle: String
    val offTitle: String
    val lifeSignMissing: String
    val alwaysLostTitle: String
    val alwaysLostText: String
    val alwaysLostFix: String
    val alwaysLostLater: String
}

internal object LocationTextsDe : LocationTexts {
    override val questionTitle = "Dein Standort bei SOS"
    override val questionText = "Sollen deine SOS-Kontakte sehen, wo du bist, wenn du Hilfe rufst (SOS) oder auf ein Lebenszeichen nicht antwortest? Sonst sieht niemand deinen Standort."
    override val questionNote = "Du kannst das später in den Einstellungen ändern, auch für einzelne Kreise."
    override fun appliesTo(circles: String) = "Gilt für: $circles"
    override val yes = "Ja, bei SOS"
    override val no = "Nein"
    override val setupTitle = "Dein Standort"
    override val setupText = "anynear braucht ihn für Warnungen in deinem Landkreis und damit dein Funkgerät so funkt wie die Funkgeräte in deiner Gegend. Dafür genügt der ungefähre Standort, und er bleibt auf dem Handy."
    override val setupNo = "Nein, nur für Warnungen und Funk"
    override val setupLaterNote = "Ohne Standort wählst du das Gebiet für dein Funkgerät später von Hand."
    override val settingsTitle = "Standort"
    override val whenNeeded = "Standort bei SOS zeigen"
    override val whenNeededOn = "Rufst du Hilfe (SOS) oder antwortest auf ein Lebenszeichen nicht, sehen deine SOS-Kontakte, wo du bist."
    override val whenNeededOff = "Niemand sieht deinen Standort."
    override val settingsFooter = "Gilt für alle deine Kreise, außer du stellst es bei einem anders ein. Nur in diesem Fall verlässt dein Standort das Handy."
    override val settingsFooterMap = "Gilt für alle, außer du stellst es bei einem Kreis oder einer Person anders ein. Das geht unter „Karte“ bei „Wer sieht mich?“."
    override val circleOn = "Sieht deinen Standort bei SOS"
    override val circleOff = "Sieht deinen Standort nie"
    override val allowTitle = "Standort auf diesem Handy erlauben"
    override val allowText = "Ohne die Erlaubnis für den genauen Standort kann anynear ihn nicht oder nur ungefähr mitschicken."
    override fun metres(value: Int) = "$value m"
    override fun kilometres(value: String) = "$value km"
    override val accessTitle = "Standort erlauben"
    override val accessAllow = "Erlauben"
    override val accessSkip = "Ohne Standort weiter"
    override val accessOff = "Schalte dafür auch den Standort am Handy ein."
    override val accessSosContact = "Damit deine SOS-Kontakte dich im Notfall finden, braucht anynear deinen genauen Standort. Er geht nur bei SOS hinaus."
    override val accessLifeSign = "Damit deine SOS-Kontakte dich finden, wenn du nicht antwortest, ortet anynear, solange das Lebenszeichen läuft. Dein Standort geht nur dann hinaus."
    override val accessShare = "Damit die anderen sehen, wo du gerade bist, braucht anynear deinen genauen Standort, solange du teilst."
    override val accessSendPlace = "Damit anynear deinen Standort senden kann, braucht es den genauen Standort."
    override val accessLevel = "Damit dich die anderen im Notfall finden, braucht anynear deinen genauen Standort."
    override val accessAlways = "Damit dein Standort auch aktuell ist, wenn anynear geschlossen ist, wähle „Immer erlauben“. Sonst aktualisiert er sich nur, wenn du die App öffnest."
    override val missingTitle = "Standort fehlt"
    override val roughTitle = "Standort nur ungefähr"
    override val offTitle = "Standort am Handy aus"
    override val lifeSignMissing = "Antwortest du nicht, finden dich deine SOS-Kontakte nicht. Tippen zum Erlauben."
    override val alwaysLostTitle = "Standort nicht mehr aktuell"
    override val alwaysLostText = "Dein Handy erlaubt anynear nicht mehr, den Standort im Hintergrund zu bestimmen. Wer ihn sieht, sieht ihn nur noch aktuell, wenn du die App öffnest."
    override val alwaysLostFix = "Wieder erlauben"
    override val alwaysLostLater = "So lassen"
}

internal object LocationTextsEn : LocationTexts {
    override val questionTitle = "Your location with SOS"
    override val questionText = "Should your SOS contacts see where you are if you call for help (SOS) or don't answer a check-in? Otherwise nobody sees your location."
    override val questionNote = "You can change this later in Settings, also for single circles."
    override fun appliesTo(circles: String) = "Applies to: $circles"
    override val yes = "Yes, with SOS"
    override val no = "No"
    override val setupTitle = "Your location"
    override val setupText = "anynear needs it for warnings in your district, and so your radio uses the same settings as the radios around you. The approximate location is enough for that, and it stays on your phone."
    override val setupNo = "No, only for warnings and radio"
    override val setupLaterNote = "Without a location, you choose the area for your radio by hand later."
    override val settingsTitle = "Location"
    override val whenNeeded = "Show location with SOS"
    override val whenNeededOn = "If you call for help (SOS) or don't answer a check-in, your SOS contacts see where you are."
    override val whenNeededOff = "Nobody sees your location."
    override val settingsFooter = "Applies to all your circles unless you set one differently. Only in this case does your location leave your phone."
    override val settingsFooterMap = "Applies to everyone unless you set a circle or a person differently. You do that under “Map” in “Who sees me?”."
    override val circleOn = "Sees your location with SOS"
    override val circleOff = "Never sees your location"
    override val allowTitle = "Allow location on this phone"
    override val allowText = "Without the permission for the precise location, anynear can't include it, or only approximately."
    override fun metres(value: Int) = "$value m"
    override fun kilometres(value: String) = "$value km"
    override val accessTitle = "Allow location"
    override val accessAllow = "Allow"
    override val accessSkip = "Continue without location"
    override val accessOff = "Also turn on location on your phone."
    override val accessSosContact = "So your SOS contacts can find you in an emergency, anynear needs your precise location. It only goes out with SOS."
    override val accessLifeSign = "So your SOS contacts can find you if you don't answer, anynear locates you while the check-in runs. Your location only goes out then."
    override val accessShare = "So the others see where you are right now, anynear needs your precise location while you share."
    override val accessSendPlace = "To send your location, anynear needs your precise location."
    override val accessLevel = "So the others can find you in an emergency, anynear needs your precise location."
    override val accessAlways = "So your location stays current when anynear is closed, choose “Allow all the time”. Otherwise it only updates when you open the app."
    override val missingTitle = "Location missing"
    override val roughTitle = "Location only approximate"
    override val offTitle = "Location is off on your phone"
    override val lifeSignMissing = "If you don't answer, your SOS contacts can't find you. Tap to allow."
    override val alwaysLostTitle = "Location no longer current"
    override val alwaysLostText = "Your phone no longer lets anynear find your location in the background. Those who see it only see it current when you open the app."
    override val alwaysLostFix = "Allow again"
    override val alwaysLostLater = "Leave it"
}
