package app.anynear.i18n

/** Calling the helpers (ADR 0083 §2–§4, ADR 0121 §13). Never „Notruf“ for this: it warns the helpers (ADR 0083 §9). */
interface SosTexts {
    val title: String
    val button: String
    val buttonLabel: String
    val hold: String
    fun goesTo(names: String): String
    val whatGoes: String
    val not112: String
    val dial112: String
    val noHelpersTitle: String
    val noHelpersText: String
    val chooseHelpers: String
    val save: String
    val nobodyYet: String
    fun countdown(seconds: Int): String
    val cancel: String
    val sending: String
    val sendingGoesOn: String
    val sentConfirm: String
    val failedBanner: String
    val failed: String
    val runningTitle: String
    fun runningSince(time: String): String
    val nobodyCares: String
    fun agreedOf(done: Int, all: Int): String
    val nobodyAgreed: String
    fun askTitle(name: String): String
    fun askText(name: String): String
    val askNotice: String
    val askYes: String
    val askNo: String
    val askLater: String
    val contactFor: String
    val openAsks: String
    val withdraw: String
    fun arrived(done: Int, all: Int): String
    val notArrived: String
    fun cares(names: String): String
    fun caresMany(names: String): String
    val endHold: String
    fun alarmTitle(name: String): String
    val alarmText: String
    fun endedTitle(name: String): String
    val endedText: String
    fun needsHelp(name: String): String
    fun since(time: String): String
    fun battery(percent: Int): String
    val noPlace: String
    fun placeFrom(time: String): String
    val iCare: String
    val youCare: String
    val showOnMap: String
    val ended: String
    val close: String
    val circles: String
    val persons: String
    fun activeBanner(name: String): String
    val ownBanner: String
    val buttonSetUp: String

    // A check-in nobody confirmed is a call for the helpers (ADR 0083 §5.4).
    fun missedTitle(name: String): String
    val missedText: String
    fun unreachableTitle(name: String): String
    val unreachableText: String
    fun missedNote(label: String): String
    fun unreachableNote(time: String): String
    fun expectedSince(time: String): String
    val ownMissedTitle: String
    fun ownMissedText(circle: String): String
    val ownMissedNoPlace: String
    val ownMissedBanner: String
}

internal object SosTextsDe : SosTexts {
    override val title = "Hilfe rufen"
    override val button = "SOS"
    override val buttonLabel = "Hilfe rufen. Fünfmal schnell tippen löst mit Rückzähler aus."
    override val hold = "Halten, um deine SOS-Kontakte zu warnen"
    override fun goesTo(names: String) = "Geht an: $names"
    override val whatGoes = "Deine SOS-Kontakte bekommen deinen Standort, den Akkustand und wo du zuletzt warst."
    override val not112 = "anynear warnt deine SOS-Kontakte. Es ersetzt nicht den Notruf 112."
    override val dial112 = "112 anrufen"
    override val noHelpersTitle = "Wer soll dir helfen?"
    override val noHelpersText = "Wähle Kreise oder einzelne Personen. Sie werden laut geweckt, wenn du Hilfe rufst."
    override val chooseHelpers = "SOS-Kontakte ändern"
    override val save = "Speichern"
    override val nobodyYet = "Du hast noch keinen Kreis und keinen Kontakt. Lade zuerst jemanden ein."
    override fun countdown(seconds: Int) = "Deine SOS-Kontakte werden in $seconds Sekunden gewarnt"
    override val cancel = "Abbrechen"
    override val sending = "Deine SOS-Kontakte werden gewarnt …"
    override val sendingGoesOn = "Das läuft weiter, auch wenn du diese Seite schließt."
    override val sentConfirm = "Der Ruf ist raus und läuft weiter, auch wenn du diese Seite oder anynear schließt."
    override val failedBanner = "Dein Hilferuf ging nicht raus. Antippen"
    override val failed = "Das ging nicht raus. Prüf deine Verbindung und versuch es noch einmal, oder ruf die 112."
    override val runningTitle = "Deine SOS-Kontakte sind gewarnt"
    override fun runningSince(time: String) = "Seit $time. Dein Standort geht weiter mit, auch wenn anynear nicht offen ist."
    override val nobodyCares = "Noch hat niemand geantwortet."
    override fun agreedOf(done: Int, all: Int) = "$done von $all haben zugesagt"
    override val nobodyAgreed = "Noch hat niemand zugesagt. Erst nach der Zusage klingelt ein Hilferuf dort laut; bis dahin ist er nur eine Mitteilung."
    override fun askTitle(name: String) = "$name möchte dich als SOS-Kontakt"
    override fun askText(name: String) = "Ruft $name Hilfe oder bleibt ein Lebenszeichen aus, klingelt dein Handy laut, auch nachts. Du kannst das jederzeit zurücknehmen."
    override val askNotice = "Öffne anynear, um zu antworten."
    override val askYes = "Ja, einverstanden"
    override val askNo = "Nein"
    override val askLater = "Später"
    override val contactFor = "Du bist SOS-Kontakt für"
    override val openAsks = "Offene Anfragen"
    override val withdraw = "Antippen, um es zurückzunehmen"
    override fun arrived(done: Int, all: Int) = "Angekommen bei $done von $all"
    override val notArrived = "Noch bei niemandem angekommen. Wenn es eilt, ruf die 112 an."
    override fun cares(names: String) = "$names kümmert sich"
    override fun caresMany(names: String) = "$names kümmern sich"
    override val endHold = "Alles gut: halten, um zu entwarnen"
    override fun alarmTitle(name: String) = "$name braucht Hilfe"
    override val alarmText = "Tippen, um zu sehen, wo, und um zu antworten."
    override fun endedTitle(name: String) = "Entwarnung von $name"
    override val endedText = "Alles gut."
    override fun needsHelp(name: String) = "$name braucht Hilfe"
    override fun since(time: String) = "Gerufen um $time"
    override fun battery(percent: Int) = "Akku $percent %"
    override val noPlace = "Kein Standort dabei."
    override fun placeFrom(time: String) = "Standort von $time"
    override val iCare = "Ich kümmere mich"
    override val youCare = "Du kümmerst dich"
    override val showOnMap = "Auf Karte zeigen"
    override val ended = "Entwarnung. Alles gut."
    override val close = "Schließen"
    override val circles = "Kreise"
    override val persons = "Personen"
    override fun activeBanner(name: String) = "$name braucht Hilfe. Antippen"
    override val ownBanner = "Deine SOS-Kontakte sind gewarnt. Antippen"
    override val buttonSetUp = "Hilfe rufen ist noch nicht eingerichtet. Antippen, um SOS-Kontakte zu wählen."

    override fun missedTitle(name: String) = "$name hat das Lebenszeichen nicht bestätigt"
    override val missedText = "Antippen, um zu sehen, wo, und zu antworten."
    override fun unreachableTitle(name: String) = "$name ist nicht erreichbar"
    override val unreachableText = "Das Lebenszeichen blieb aus. Antippen, um zu sehen, wo, und zu antworten."
    override fun missedNote(label: String) = "Lebenszeichen „$label“: Das Handy hat gefragt, niemand hat „Alles gut“ getippt."
    override fun unreachableNote(time: String) = "Seit $time kam kein Lebenszeichen mehr. Das Handy ist aus oder hat kein Netz."
    override fun expectedSince(time: String) = "Fällig seit $time"
    override val ownMissedTitle = "Du hast nicht geantwortet"
    override fun ownMissedText(circle: String) = "Dein Lebenszeichen blieb aus. Deshalb ist „$circle“ gewarnt."
    override val ownMissedNoPlace = "Dein Standort geht nicht mit, weil du ihn mit diesem Kreis nicht teilst."
    override val ownMissedBanner = "Lebenszeichen blieb aus: Deine SOS-Kontakte sind gewarnt. Antippen"
}

internal object SosTextsEn : SosTexts {
    override val title = "Call for help"
    override val button = "SOS"
    override val buttonLabel = "Call for help. Five quick taps start it with a countdown."
    override val hold = "Hold to warn your SOS contacts"
    override fun goesTo(names: String) = "Goes to: $names"
    override val whatGoes = "Your SOS contacts get your location, your battery level and where you were last."
    override val not112 = "anynear warns your SOS contacts. It does not replace the emergency number 112."
    override val dial112 = "Call 112"
    override val noHelpersTitle = "Who should help you?"
    override val noHelpersText = "Choose circles or single people. They are woken loudly when you call for help."
    override val chooseHelpers = "Change SOS contacts"
    override val save = "Save"
    override val nobodyYet = "You have no circle and no contact yet. Invite someone first."
    override fun countdown(seconds: Int) = "Your SOS contacts will be warned in $seconds seconds"
    override val cancel = "Cancel"
    override val sending = "Warning your SOS contacts …"
    override val sendingGoesOn = "This goes on, also when you close this page."
    override val sentConfirm = "The call is out and goes on, also when you close this page or anynear."
    override val failedBanner = "Your call for help didn't go out. Tap"
    override val failed = "That didn't go out. Check your connection and try again, or call 112."
    override val runningTitle = "Your SOS contacts have been warned"
    override fun runningSince(time: String) = "Since $time. Your location keeps going along, also when anynear isn't open."
    override val nobodyCares = "Nobody has answered yet."
    override fun agreedOf(done: Int, all: Int) = "$done of $all have agreed"
    override val nobodyAgreed = "Nobody has agreed yet. A call for help only rings loudly there after they agree; until then it is just a notification."
    override fun askTitle(name: String) = "$name wants you as an SOS contact"
    override fun askText(name: String) = "If $name calls for help or misses a check-in, your phone rings loudly, also at night. You can take this back at any time."
    override val askNotice = "Open anynear to answer."
    override val askYes = "Yes, I agree"
    override val askNo = "No"
    override val askLater = "Later"
    override val contactFor = "You are an SOS contact for"
    override val openAsks = "Open requests"
    override val withdraw = "Tap to take it back"
    override fun arrived(done: Int, all: Int) = "Arrived with $done of $all"
    override val notArrived = "Not arrived with anyone yet. If it is urgent, call 112."
    override fun cares(names: String) = "$names is looking after it"
    override fun caresMany(names: String) = "$names are looking after it"
    override val endHold = "All good: hold to give the all-clear"
    override fun alarmTitle(name: String) = "$name needs help"
    override val alarmText = "Tap to see where and to answer."
    override fun endedTitle(name: String) = "All-clear from $name"
    override val endedText = "All good."
    override fun needsHelp(name: String) = "$name needs help"
    override fun since(time: String) = "Called at $time"
    override fun battery(percent: Int) = "Battery $percent %"
    override val noPlace = "No location included."
    override fun placeFrom(time: String) = "Location from $time"
    override val iCare = "I'll look after it"
    override val youCare = "You are looking after it"
    override val showOnMap = "Show on map"
    override val ended = "All-clear. All good."
    override val close = "Close"
    override val circles = "Circles"
    override val persons = "People"
    override fun activeBanner(name: String) = "$name needs help. Tap"
    override val ownBanner = "Your SOS contacts have been warned. Tap"
    override val buttonSetUp = "Calling for help isn't set up yet. Tap to choose SOS contacts."

    override fun missedTitle(name: String) = "$name didn't confirm the check-in"
    override val missedText = "Tap to see where and to answer."
    override fun unreachableTitle(name: String) = "$name can't be reached"
    override val unreachableText = "The check-in stopped. Tap to see where and to answer."
    override fun missedNote(label: String) = "Check-in “$label”: the phone asked, nobody tapped “All good”."
    override fun unreachableNote(time: String) = "No check-in has come since $time. The phone is off or has no network."
    override fun expectedSince(time: String) = "Due since $time"
    override val ownMissedTitle = "You didn't answer"
    override fun ownMissedText(circle: String) = "Your check-in stopped. That's why “$circle” has been warned."
    override val ownMissedNoPlace = "Your location doesn't go along, because you don't share it with this circle."
    override val ownMissedBanner = "Check-in stopped: your SOS contacts have been warned. Tap"
}
