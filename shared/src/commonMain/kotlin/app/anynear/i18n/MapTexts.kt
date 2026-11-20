package app.anynear.i18n

/** The tab „Karte“: who sees whom, the people on the map and what the map can do (ADR 0121). */
interface MapTexts {
    val title: String
    val visibleNobody: String
    val visibleWhen: String
    fun visibleTo(names: String): String
    val visiblePaused: String
    val whoSeesMe: String
    val whoSeesMeText: String
    val pauseAll: String
    val pauseAllOn: String
    val pauseAllOff: String
    val defaultRow: String
    val ownSettings: String
    val ownSettingsNone: String
    val circles: String
    val persons: String
    val followsCircles: String
    val levelOff: String
    val levelWhen: String
    val levelArea: String
    val levelExact: String
    val levelOffText: String
    val levelWhenText: String
    val levelAreaText: String
    val levelExactText: String
    fun chooseLevel(name: String): String
    val forHowLong: String
    val untilChanged: String
    val oneHour: String
    val untilTonight: String
    fun until(time: String): String
    val openOnly: String
    val sourceTitle: String
    val sourceThis: String
    val sourceOther: String
    val sourceUseThis: String
    val people: String
    val noPeople: String
    val allCircles: String
    val sharesOff: String
    val sharesWhen: String
    val sharesNothingYet: String
    fun inArea(place: String): String
    val areaUnknown: String
    fun atPlace(place: String, time: String): String
    fun exactNoPlace(time: String): String
    val waitingForPlace: String
    val you: String
    fun yourPlace(place: String): String
    val yourPlaceUnknown: String
    val locateMe: String
    val showAll: String
    val zoomIn: String
    val zoomOut: String
    val mapNote: String
    val mapMissing: String
    val askPlace: String
    fun askSent(name: String): String
    val askFailed: String
    fun askedTitle(name: String): String
    val askedText: String
    val showOnce: String
    val showOneHour: String
    fun newcomerTitle(name: String): String
    fun newcomerText(name: String): String
    val newcomerYes: String
    val newcomerNo: String
    fun guideTo(name: String): String
    fun guideText(distance: String, direction: String): String
    val guideArea: String
    val guideNoOwn: String
    val north: String
    val northEast: String
    val east: String
    val southEast: String
    val south: String
    val southWest: String
    val west: String
    val northWest: String
    val writeMessage: String
    fun whatSees(name: String): String
    val warnings: String
    fun warningIn(area: String): String
    fun warningNear(name: String, title: String): String
    fun escortRunning(circle: String, time: String): String
    fun minutes(n: Int): String
    fun hours(n: Int): String
    val justNow: String
    fun minutesAgo(n: Int): String
    fun hoursAgo(n: Int): String
    fun daysAgo(n: Int): String
    val filterAll: String
    val filterCircles: String
    val filterPeople: String
    val newCircle: String
    val newMessage: String
    val spotMeet: String
    fun spotMeetText(label: String): String
    val spotHereText: String
    val spotAsk: String
    fun spotHere(name: String): String
    val spotHereOwn: String
    val spotShowOnMap: String
    val spotAnswer: String
    val spotAnswerHint: String
    val spotNeedsPlace: String
    val spotMeetHere: String
    val spotMeetName: String
    val spotMeetSend: String
    val spotMeetExplain: String
    fun spotFrom(time: String): String
    fun guideToPlace(name: String): String
    val closeFocus: String
    val shareMine: String
    val shareMineText: String
    val shareMineWarn: String
    val shareMineWarnText: String
    val shareMineStart: String
    val shareMineStop: String
    val rowTitle: String
    val spotLocationOff: String
    val spotNoFix: String
    val runningHere: String
    fun peopleCount(n: Int): String
    val sheetOpen: String
    val sheetClose: String
    fun sharingNotice(names: String): String
    val alwaysTitle: String
    val alwaysText: String
    val destination: String
    val destinationNone: String
    val destinationPick: String
    val destinationOnMap: String
    val destinationText: String
    val shareMineWarnArrive: String
    fun shareMineEndsAt(place: String): String
    val arrivedMessage: String
    val pickHint: String
    val pickTake: String
    val spotMeetOnMap: String
    fun answered(done: Int, all: Int): String
    fun answeredOpen(names: String): String
    val shareAll: String
    val shareAllText: String
    val shareAllStart: String
    fun shareAllMessage(time: String): String
    fun shareAllUntil(time: String): String
    val shareAllOver: String
    fun shareAllTaking(done: Int, all: Int): String
    val shareAllHint: String
    val shareAllJoin: String
    val shareAllLeave: String
    val mapLoading: String
    val placeFor: String
    val placeOnlyMe: String
    val placeForCircleText: String
    fun placeOfCircle(circle: String): String
    fun placeCircleText(circle: String): String
    val myPlaces: String
    val myPlacesText: String
    val placeAdd: String
    val placeHome: String
    val placeWork: String
    val placeSchool: String
    val placeName: String
    val placeHere: String
    val placeOnMap: String
    val placeWithArea: String
    val placeWithAreaText: String
    val placeRemove: String
    val placeHereNow: String
    val proposeHome: String
    val proposeWork: String
    val proposeText: String
    val proposeYes: String
    val proposeNo: String
    fun atSaved(place: String, time: String): String
    fun atSavedArea(place: String): String
}

internal object MapTextsDe : MapTexts {
    override val title = "Karte"
    override val visibleNobody = "Niemand sieht deinen Standort"
    override val visibleWhen = "Dein Standort ist nur sichtbar, wenn etwas ist"
    override fun visibleTo(names: String) = "Sichtbar für $names"
    override val visiblePaused = "Alles pausiert. Niemand sieht deinen Standort."
    override val whoSeesMe = "Wer sieht mich?"
    override val whoSeesMeText = "Nur du gibst deinen Standort frei, immer an einen Kreis oder eine Person. Hier siehst du alles auf einen Blick."
    override val pauseAll = "Alles pausieren"
    override val pauseAllOn = "Pausiert. Niemand sieht deinen Standort, auch nicht, wenn etwas ist."
    override val pauseAllOff = "Deine Freigaben gelten wie eingestellt."
    override val defaultRow = "Für alle, wenn nichts anderes eingestellt ist"
    override val ownSettings = "Anders eingestellt"
    override val ownSettingsNone = "Für alle gilt, was oben steht. Stellst du einen Kreis oder eine Person anders ein, steht das hier."
    override val circles = "Kreise"
    override val persons = "Personen"
    override val followsCircles = "Wie in den Kreisen"
    override val levelOff = "Aus"
    override val levelWhen = "Wenn etwas ist"
    override val levelArea = "Ungefähr immer, genau wenn etwas ist"
    override val levelExact = "Genau immer"
    override val levelOffText = "Sieht deinen Standort nie."
    override val levelWhenText = "Sieht den genauen Standort nur, wenn du Hilfe rufst (SOS) oder auf ein Lebenszeichen nicht antwortest."
    override val levelAreaText = "Sieht laufend, in welcher Gegend du bist (etwa 2 km), nie den Punkt. Wenn etwas ist, den genauen Standort."
    override val levelExactText = "Sieht laufend, wo genau du bist."
    override fun chooseLevel(name: String) = "Was sieht $name?"
    override val forHowLong = "Wie lange?"
    override val untilChanged = "Bis ich es ändere"
    override val oneHour = "1 Stunde"
    override val untilTonight = "Bis heute Abend"
    override fun until(time: String) = "bis $time"
    override val openOnly = "Dein Handy ortet dafür auch, wenn anynear nicht offen ist. Eine Mitteilung zeigt das, solange es läuft."
    override val sourceTitle = "Mein Standort kommt von"
    override val sourceThis = "diesem Gerät"
    override val sourceOther = "einem anderen deiner Geräte"
    override val sourceUseThis = "Dieses Gerät nehmen"
    override val people = "Deine Leute"
    override val noPeople = "Hier stehen die Menschen aus deinen Kreisen und Kontakten, sobald du welche hast."
    override val allCircles = "Alle"
    override val sharesOff = "Teilt keinen Standort"
    override val sharesWhen = "Teilt nur, wenn etwas ist"
    override val sharesNothingYet = "Hat noch nichts geteilt"
    override fun inArea(place: String) = "In der Gegend von $place"
    override val areaUnknown = "Ungefährer Standort"
    override fun atPlace(place: String, time: String) = "$place · $time"
    override fun exactNoPlace(time: String) = "Genauer Standort · $time"
    override val waitingForPlace = "Teilt, aber noch kein Standort da"
    override val you = "Du"
    override fun yourPlace(place: String) = "Du bist in $place"
    override val yourPlaceUnknown = "Dein Standort ist noch nicht bekannt"
    override val locateMe = "Meinen Standort zeigen"
    override val showAll = "Alle zeigen"
    override val zoomIn = "Näher"
    override val zoomOut = "Weiter weg"
    override val mapNote = "Die Karte geht ohne Internet. Straßen zeigt sie dort, wo die Kartenstücke schon auf dem Handy liegen."
    override val mapMissing = "Die Karte konnte nicht geladen werden."
    override val askPlace = "Standort anfragen"
    override fun askSent(name: String) = "$name wurde gefragt und entscheidet selbst."
    override val askFailed = "Das ging gerade nicht. Versuch es später noch einmal."
    override fun askedTitle(name: String) = "$name möchte sehen, wo du bist"
    override val askedText = "Du entscheidest. Ohne deine Antwort sieht niemand etwas."
    override val showOnce = "Einmal zeigen"
    override val showOneHour = "1 Stunde zeigen"
    override fun newcomerTitle(name: String) = "$name ist neu in deinem Kreis"
    override fun newcomerText(name: String) = "Du teilst deinen Standort mit diesem Kreis. Soll $name auch sehen, wo du bist?"
    override val newcomerYes = "Ja, auch zeigen"
    override val newcomerNo = "Nein, nur wenn etwas ist"
    override fun guideTo(name: String) = "Zu $name führen"
    override fun guideText(distance: String, direction: String) = "$distance Richtung $direction. Norden ist oben."
    override val guideArea = "Der Standort ist nur ungefähr. Der Pfeil zeigt zur Mitte der Gegend."
    override val guideNoOwn = "Dafür braucht anynear deinen eigenen Standort."
    override val north = "Norden"
    override val northEast = "Nordosten"
    override val east = "Osten"
    override val southEast = "Südosten"
    override val south = "Süden"
    override val southWest = "Südwesten"
    override val west = "Westen"
    override val northWest = "Nordwesten"
    override val writeMessage = "Nachricht schreiben"
    override fun whatSees(name: String) = "Was $name von dir sieht"
    override val warnings = "Amtliche Warnungen"
    override fun warningIn(area: String) = "Gilt für Teile von $area"
    override fun warningNear(name: String, title: String) = "$name ist in einem Warngebiet: $title"
    override fun escortRunning(circle: String, time: String) = "„$circle“ sieht deinen Standort bis $time"
    override fun minutes(n: Int) = "$n Minuten"
    override fun hours(n: Int) = "$n Stunden"
    override val justNow = "gerade eben"
    override fun minutesAgo(n: Int) = "vor $n Min."
    override fun hoursAgo(n: Int) = "vor $n Std."
    override fun daysAgo(n: Int) = "vor $n Tagen"
    override val filterAll = "Alle"
    override val filterCircles = "Kreise"
    override val filterPeople = "Personen"
    override val newCircle = "Neuer Kreis"
    override val newMessage = "Neue Nachricht"
    override val spotMeet = "Treffpunkt"
    override fun spotMeetText(label: String) = "Treffpunkt: $label"
    override val spotHereText = "Ich bin hier:"
    override val spotAsk = "Wo bist du?"
    override fun spotHere(name: String) = "Standort von $name"
    override val spotHereOwn = "Dein Standort"
    override val spotShowOnMap = "Auf Karte zeigen"
    override val spotAnswer = "Meinen Standort zeigen"
    override val spotAnswerHint = "Schickt einmal, wo du gerade bist. Niemand sieht dich danach weiter."
    override val spotNeedsPlace = "Dafür braucht anynear deinen genauen Standort. Erlaube ihn und versuch es noch einmal."
    override val spotMeetHere = "Treffpunkt hier"
    override val spotMeetName = "Name, z. B. Haupteingang (freiwillig)"
    override val spotMeetSend = "Treffpunkt senden"
    override val spotMeetExplain = "Alle im Gespräch sehen den Ort, an dem du gerade stehst, und können sich hinführen lassen."
    override fun spotFrom(time: String) = "Stand $time"
    override fun guideToPlace(name: String) = "Zu „$name“ führen"
    override val closeFocus = "Ziel schließen"
    override val shareMine = "Meinen Standort teilen"
    override val shareMineText = "Alle in diesem Gespräch sehen für die gewählte Zeit, wo genau du bist. Danach endet es von selbst."
    override val shareMineWarn = "Warnen, wenn ich mich nicht melde"
    override val shareMineWarnText = "anynear fragt zwischendurch „Alles gut?“ und warnt den Kreis, wenn du nicht antwortest."
    override val shareMineStart = "Teilen"
    override val shareMineStop = "Teilen beenden"
    override val rowTitle = "Mein Standort"
    override val spotLocationOff = "Der Standort ist auf diesem Gerät ausgeschaltet. Schalte ihn ein und versuch es noch einmal."
    override val spotNoFix = "Dein Standort lässt sich gerade nicht bestimmen. Geh ans Fenster oder ins Freie und versuch es noch einmal."
    override val runningHere = "Hier wird gerade ein Standort geteilt. Auf der Karte zeigen"
    override fun peopleCount(n: Int) = "Deine Leute ($n)"
    override val sheetOpen = "Liste öffnen"
    override val sheetClose = "Liste schließen"
    override fun sharingNotice(names: String) = "Du teilst deinen Standort mit $names"
    override val alwaysTitle = "Auch nach einem Neustart des Handys teilen"
    override val alwaysText = "Dafür braucht anynear die Erlaubnis „Immer“. Sonst geht das Teilen weiter, sobald du anynear wieder öffnest."
    override val destination = "Ziel"
    override val destinationNone = "Kein Ziel"
    override val destinationPick = "Auf der Karte wählen"
    override val destinationOnMap = "Ziel auf der Karte"
    override val destinationText = "In der Nähe des Ziels endet das Teilen von selbst, und das Gespräch liest „angekommen“."
    override val shareMineWarnArrive = "Warnen, wenn ich nicht ankomme"
    override fun shareMineEndsAt(place: String) = "Endet von selbst bei: $place"
    override val arrivedMessage = "Ich bin angekommen."
    override val pickHint = "Schieb die Karte, bis der Ort unter dem Kreis liegt."
    override val pickTake = "Diesen Ort nehmen"
    override val spotMeetOnMap = "Auf der Karte wählen"
    override fun answered(done: Int, all: Int) = "$done von $all haben geantwortet"
    override fun answeredOpen(names: String) = "Offen: $names"
    override val shareAll = "Alle teilen"
    override val shareAllText = "Lädt alle im Kreis ein, für eine Zeit zu zeigen, wo sie sind, zum Beispiel auf einem Festival. Jeder entscheidet selbst mit „Mitmachen“. Du machst gleich mit."
    override val shareAllStart = "Einladen und mitmachen"
    override fun shareAllMessage(time: String) = "Alle teilen ihren Standort bis $time. Mach mit in anynear."
    override fun shareAllUntil(time: String) = "Alle teilen bis $time"
    override val shareAllOver = "„Alle teilen“ ist vorbei"
    override fun shareAllTaking(done: Int, all: Int) = "$done von $all machen mit"
    override val shareAllHint = "Mit „Mitmachen“ sieht der Kreis bis dahin, wo du bist. Aussteigen geht jederzeit."
    override val shareAllJoin = "Mitmachen"
    override val shareAllLeave = "Aussteigen"
    override val mapLoading = "Karte wird geladen"
    override val placeFor = "Für wen?"
    override val placeOnlyMe = "Nur ich"
    override val placeForCircleText = "Alle im Kreis kennen den Ort und sehen den Namen, wenn jemand dort ist, dessen genauen Standort sie sehen."
    override fun placeOfCircle(circle: String) = "Ort im Kreis $circle"
    override fun placeCircleText(circle: String) = "Diesen Ort kennen alle im Kreis $circle. Jedes Mitglied kann ihn ändern."
    override val myPlaces = "Meine Orte"
    override val myPlacesText = "Wer deinen genauen Standort sieht, liest dort den Namen, zum Beispiel „Zu Hause“. Niemand wird benachrichtigt, wenn du ankommst."
    override val placeAdd = "Ort hinzufügen"
    override val placeHome = "Zu Hause"
    override val placeWork = "Arbeit"
    override val placeSchool = "Schule"
    override val placeName = "Name des Orts"
    override val placeHere = "Hier, wo ich gerade bin"
    override val placeOnMap = "Auf der Karte wählen"
    override val placeWithArea = "Auch zeigen, wenn jemand nur die Gegend sieht"
    override val placeWithAreaText = "Der Name sagt mehr als die Gegend allein."
    override val placeRemove = "Ort entfernen"
    override val placeHereNow = "Du bist gerade hier"
    override val proposeHome = "Bist du hier zu Hause?"
    override val proposeWork = "Arbeitest du hier?"
    override val proposeText = "Dein Handy war hier schon öfter. Als Ort gespeichert steht bei denen, die deinen genauen Standort sehen, der Name."
    override val proposeYes = "Ja, speichern"
    override val proposeNo = "Nein"
    override fun atSaved(place: String, time: String) = "$place · seit $time"
    override fun atSavedArea(place: String) = "$place"
}

internal object MapTextsEn : MapTexts {
    override val title = "Map"
    override val visibleNobody = "Nobody sees your location"
    override val visibleWhen = "Your location is only visible when something is wrong"
    override fun visibleTo(names: String) = "Visible to $names"
    override val visiblePaused = "Everything is paused. Nobody sees your location."
    override val whoSeesMe = "Who sees me?"
    override val whoSeesMeText = "Only you share your location, always with a circle or a person. Here you see everything at a glance."
    override val pauseAll = "Pause everything"
    override val pauseAllOn = "Paused. Nobody sees your location, not even when something is wrong."
    override val pauseAllOff = "Your sharing applies as set."
    override val defaultRow = "For everyone, unless set differently"
    override val ownSettings = "Set differently"
    override val ownSettingsNone = "What is set above applies to everyone. If you set a circle or a person differently, it shows here."
    override val circles = "Circles"
    override val persons = "People"
    override val followsCircles = "As in the circles"
    override val levelOff = "Off"
    override val levelWhen = "When something is wrong"
    override val levelArea = "Approximate always, precise when something is wrong"
    override val levelExact = "Precise always"
    override val levelOffText = "Never sees your location."
    override val levelWhenText = "Sees the precise location only if you call for help (SOS) or don't answer a check-in."
    override val levelAreaText = "Always sees which area you are in (about 1 mile), never the point. When something is wrong, the precise location."
    override val levelExactText = "Always sees exactly where you are."
    override fun chooseLevel(name: String) = "What does $name see?"
    override val forHowLong = "For how long?"
    override val untilChanged = "Until I change it"
    override val oneHour = "1 hour"
    override val untilTonight = "Until tonight"
    override fun until(time: String) = "until $time"
    override val openOnly = "Your phone also locates for this when anynear isn't open. A notification shows it for as long as it runs."
    override val sourceTitle = "My location comes from"
    override val sourceThis = "this device"
    override val sourceOther = "another one of your devices"
    override val sourceUseThis = "Use this device"
    override val people = "Your people"
    override val noPeople = "The people from your circles and contacts appear here once you have some."
    override val allCircles = "All"
    override val sharesOff = "Shares no location"
    override val sharesWhen = "Shares only when something is wrong"
    override val sharesNothingYet = "Hasn't shared anything yet"
    override fun inArea(place: String) = "Around $place"
    override val areaUnknown = "Approximate location"
    override fun atPlace(place: String, time: String) = "$place · $time"
    override fun exactNoPlace(time: String) = "Precise location · $time"
    override val waitingForPlace = "Shares, but no location yet"
    override val you = "You"
    override fun yourPlace(place: String) = "You are in $place"
    override val yourPlaceUnknown = "Your location isn't known yet"
    override val locateMe = "Show my location"
    override val showAll = "Show everyone"
    override val zoomIn = "Zoom in"
    override val zoomOut = "Zoom out"
    override val mapNote = "The map works without internet. It shows streets where the map pieces are already on the phone."
    override val mapMissing = "The map couldn't be loaded."
    override val askPlace = "Ask for location"
    override fun askSent(name: String) = "$name was asked and decides."
    override val askFailed = "That didn't work right now. Try again later."
    override fun askedTitle(name: String) = "$name would like to see where you are"
    override val askedText = "You decide. Without your answer nobody sees anything."
    override val showOnce = "Show once"
    override val showOneHour = "Show for 1 hour"
    override fun newcomerTitle(name: String) = "$name is new in your circle"
    override fun newcomerText(name: String) = "You share your location with this circle. Should $name see where you are, too?"
    override val newcomerYes = "Yes, show too"
    override val newcomerNo = "No, only when something is wrong"
    override fun guideTo(name: String) = "Guide me to $name"
    override fun guideText(distance: String, direction: String) = "$distance to the $direction. North is up."
    override val guideArea = "The location is only approximate. The arrow points to the middle of the area."
    override val guideNoOwn = "anynear needs your own location for this."
    override val north = "north"
    override val northEast = "northeast"
    override val east = "east"
    override val southEast = "southeast"
    override val south = "south"
    override val southWest = "southwest"
    override val west = "west"
    override val northWest = "northwest"
    override val writeMessage = "Write a message"
    override fun whatSees(name: String) = "What $name sees of you"
    override val warnings = "Official warnings"
    override fun warningIn(area: String) = "Applies to parts of $area"
    override fun warningNear(name: String, title: String) = "$name is in a warning area: $title"
    override fun escortRunning(circle: String, time: String) = "“$circle” sees your location until $time"
    override fun minutes(n: Int) = "$n minutes"
    override fun hours(n: Int) = "$n hours"
    override val justNow = "just now"
    override fun minutesAgo(n: Int) = "$n min ago"
    override fun hoursAgo(n: Int) = "$n h ago"
    override fun daysAgo(n: Int) = "$n days ago"
    override val filterAll = "All"
    override val filterCircles = "Circles"
    override val filterPeople = "People"
    override val newCircle = "New circle"
    override val newMessage = "New message"
    override val spotMeet = "Meeting point"
    override fun spotMeetText(label: String) = "Meeting point: $label"
    override val spotHereText = "I am here:"
    override val spotAsk = "Where are you?"
    override fun spotHere(name: String) = "Location of $name"
    override val spotHereOwn = "Your location"
    override val spotShowOnMap = "Show on map"
    override val spotAnswer = "Show my location"
    override val spotAnswerHint = "Sends once where you are right now. Nobody keeps seeing you afterwards."
    override val spotNeedsPlace = "anynear needs your precise location for this. Allow it and try again."
    override val spotMeetHere = "Meeting point here"
    override val spotMeetName = "Name, e.g. main entrance (optional)"
    override val spotMeetSend = "Send meeting point"
    override val spotMeetExplain = "Everyone in the conversation sees the place where you are standing right now and can be guided there."
    override fun spotFrom(time: String) = "As of $time"
    override fun guideToPlace(name: String) = "Guide me to “$name”"
    override val closeFocus = "Close destination"
    override val shareMine = "Share my location"
    override val shareMineText = "Everyone in this conversation sees exactly where you are for the chosen time. Then it ends by itself."
    override val shareMineWarn = "Warn if I don't check in"
    override val shareMineWarnText = "anynear asks “All good?” in between and warns the circle if you don't answer."
    override val shareMineStart = "Share"
    override val shareMineStop = "Stop sharing"
    override val rowTitle = "My location"
    override val spotLocationOff = "Location is switched off on this device. Switch it on and try again."
    override val spotNoFix = "Your location can't be determined right now. Go to a window or outside and try again."
    override val runningHere = "A location is being shared here right now. Show on the map"
    override fun peopleCount(n: Int) = "Your people ($n)"
    override val sheetOpen = "Open the list"
    override val sheetClose = "Close the list"
    override fun sharingNotice(names: String) = "You are sharing your location with $names"
    override val alwaysTitle = "Keep sharing after the phone restarts"
    override val alwaysText = "anynear needs the permission “Always” for that. Otherwise sharing continues as soon as you open anynear again."
    override val destination = "Destination"
    override val destinationNone = "No destination"
    override val destinationPick = "Choose on the map"
    override val destinationOnMap = "Destination on the map"
    override val destinationText = "Near the destination sharing ends by itself, and the conversation reads “arrived”."
    override val shareMineWarnArrive = "Warn if I don't arrive"
    override fun shareMineEndsAt(place: String) = "Ends by itself at: $place"
    override val arrivedMessage = "I have arrived."
    override val pickHint = "Move the map until the place is under the circle."
    override val pickTake = "Take this place"
    override val spotMeetOnMap = "Choose on the map"
    override fun answered(done: Int, all: Int) = "$done of $all have answered"
    override fun answeredOpen(names: String) = "Open: $names"
    override val shareAll = "Everyone shares"
    override val shareAllText = "Invites everyone in the circle to show where they are for a time, for example at a festival. Everyone decides with “Join”. You join right away."
    override val shareAllStart = "Invite and join"
    override fun shareAllMessage(time: String) = "Everyone shares their location until $time. Join in anynear."
    override fun shareAllUntil(time: String) = "Everyone shares until $time"
    override val shareAllOver = "“Everyone shares” is over"
    override fun shareAllTaking(done: Int, all: Int) = "$done of $all have joined"
    override val shareAllHint = "With “Join” the circle sees where you are until then. You can leave any time."
    override val shareAllJoin = "Join"
    override val shareAllLeave = "Leave"
    override val mapLoading = "Loading the map"
    override val placeFor = "For whom?"
    override val placeOnlyMe = "Only me"
    override val placeForCircleText = "Everyone in the circle knows the place and sees its name when someone is there whose precise location they see."
    override fun placeOfCircle(circle: String) = "Place in the circle $circle"
    override fun placeCircleText(circle: String) = "Everyone in the circle $circle knows this place. Every member can change it."
    override val myPlaces = "My places"
    override val myPlacesText = "Whoever sees your precise location reads the name there, for example “Home”. Nobody is notified when you arrive."
    override val placeAdd = "Add a place"
    override val placeHome = "Home"
    override val placeWork = "Work"
    override val placeSchool = "School"
    override val placeName = "Name of the place"
    override val placeHere = "Here, where I am now"
    override val placeOnMap = "Choose on the map"
    override val placeWithArea = "Also show to those who only see the area"
    override val placeWithAreaText = "The name says more than the area alone."
    override val placeRemove = "Remove the place"
    override val placeHereNow = "You are here right now"
    override val proposeHome = "Is this your home?"
    override val proposeWork = "Do you work here?"
    override val proposeText = "Your phone has been here several times. Saved as a place, those who see your precise location read the name."
    override val proposeYes = "Yes, save"
    override val proposeNo = "No"
    override fun atSaved(place: String, time: String) = "$place · since $time"
    override fun atSavedArea(place: String) = "$place"
}
