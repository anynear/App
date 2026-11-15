package app.anynear.i18n

/** Passwords (ADR 0039): logins with the device lock, autofill, back with the emergency card. */
interface PasswordTexts {
    val title: String
    val intro: String
    val search: String
    val logins: String
    val noLogins: String
    val entrySubtitle: String
    val addLogin: String
    val editLogin: String
    val website: String
    val username: String
    val password: String
    val show: String
    val hide: String
    val hiddenPassword: String
    val copy: String
    val copied: String
    val edit: String
    val delete: String
    val save: String
    val websiteHint: String
    val usernameHint: String
    val addIntro: String
    val needWebsiteAndPassword: String
    fun deleteQuestion(website: String): String
    val deleteText: String
    val unlockReason: String
    val unlock: String
    val lockedText: String
    val setUpTitle: String
    val setUpText: String
    val setUpBackup: String
    val setUp: String
    val setUpFailed: String
    val noDeviceLockTitle: String
    val noDeviceLockText: String
    val openDeviceLockSettings: String
    val unreadableTitle: String
    val unreadableText: String
    val startOver: String
    val startOverQuestion: String
    val startOverText: String
    val waitingTitle: String
    val waitingText: String
    val autofill: String
    val autofillOn: String
    val autofillOff: String
    val autofillText: String
    val setUpAutofill: String
    val changeAutofill: String
    val howItWorks: String
    val moreUnlock: String
    val moreOffline: String
    val moreOnlyYou: String
    // Autofill (Android)
    val fillWithApp: String
    val chooseLogin: String
    val matching: String
    val otherLogins: String
    // One-time codes and passkeys (ADR 0052)
    val otp: String
    val otpAdd: String
    val otpScanHelp: String
    val otpTypeKey: String
    val otpKey: String
    val otpKeyHint: String
    val otpNotCode: String
    val otpInvalidKey: String
    val otpAdded: String
    val otpCopied: String
    fun otpSecondsLeft(seconds: Int): String
    fun otpDescription(code: String, seconds: Int): String
    val otpRemove: String
    val otpRemoveQuestion: String
    val otpRemoveText: String
    val otpReadyToPaste: String
    val fillCode: String
    val passkey: String
    fun passkeyCreated(date: String): String
    val passkeyDeleteQuestion: String
    val passkeyDeleteText: String
    val passkeysNotHere: String
    val passkeySave: String
    val passkeySignIn: String
    val needWebsite: String
    val morePasskeys: String
}

internal object PasswordTextsDe : PasswordTexts {
    override val title = "Passwörter"
    override val intro = "Speichere deine Zugänge und fülle sie beim Anmelden aus."
    override val search = "Zugänge durchsuchen"
    override val logins = "Zugänge"
    override val noLogins = "Noch keine Zugänge. Tippe auf „Zugang hinzufügen“."
    override val entrySubtitle = "Zugänge für Websites und Apps"
    override val addLogin = "Zugang hinzufügen"
    override val editLogin = "Zugang bearbeiten"
    override val website = "Website"
    override val username = "Nutzername oder E-Mail"
    override val password = "Passwort"
    override val show = "Anzeigen"
    override val hide = "Verbergen"
    override val hiddenPassword = "Passwort verdeckt"
    override val copy = "Passwort kopieren"
    override val copied = "Passwort kopiert."
    override val edit = "Bearbeiten"
    override val delete = "Löschen"
    override val save = "Speichern"
    override val websiteHint = "z. B. meinebank.de"
    override val usernameHint = "z. B. name@beispiel.de"
    override val addIntro = "Mehr braucht ein Zugang nicht: Website, Nutzername oder E-Mail und Passwort."
    override val needWebsiteAndPassword = "Website und Passwort brauchen wir mindestens."
    override fun deleteQuestion(website: String) = "Zugang zu $website löschen?"
    override val deleteText = "Er ist danach auf allen deinen Geräten weg."
    override val unlockReason = "Passwörter öffnen"
    override val unlock = "Entsperren"
    override val lockedText = "Deine Passwörter sind gesperrt. Öffne sie mit der Gerätesperre deines Handys."
    override val setUpTitle = "Passwörter einrichten"
    override val setUpText = "Geöffnet wird nur mit der Gerätesperre deines Handys: Fingerabdruck, Gesicht oder die PIN des Handys. Ein zusätzliches Passwort gibt es nicht."
    override val setUpBackup = "Deine Zugänge liegen verschlüsselt auf allen deinen Geräten und online. Nur du kannst sie öffnen. Mit deiner Notfallkarte kommen sie auch auf ein neues Handy."
    override val setUp = "Einrichten"
    override val setUpFailed = "Das hat nicht geklappt. Ist eine Gerätesperre eingerichtet?"
    override val noDeviceLockTitle = "Erst eine Gerätesperre einrichten"
    override val noDeviceLockText = "Passwörter öffnen sich nur mit der Sperre deines Handys. Richte dafür eine PIN, ein Muster oder einen Gerätecode ein."
    override val openDeviceLockSettings = "Gerätesperre einrichten"
    override val unreadableTitle = "Passwörter nicht mehr lesbar"
    override val unreadableText = "Dieses Handy hat den Schlüssel zu deinen Passwörtern verloren, etwa weil die Gerätesperre ausgeschaltet war. Öffne sie einmal auf einem anderen deiner Geräte, dann kommen sie hierher zurück. Sonst holt deine Notfallkarte sie zurück."
    override val waitingTitle = "Passwörter kommen von deinem anderen Gerät"
    override val waitingText = "Öffne die Passwörter einmal auf deinem anderen Gerät. Danach kannst du sie auch hier öffnen."
    override val startOver = "Neu anfangen"
    override val startOverQuestion = "Passwörter neu anfangen?"
    override val startOverText = "Die Zugänge, die hier nicht lesbar sind, werden von diesem Handy gelöscht. Auf deinen anderen Geräten bleiben sie und kommen beim nächsten Öffnen dort wieder dazu."
    override val autofill = "Automatisch ausfüllen"
    override val autofillOn = "$APP füllt Anmeldungen aus"
    override val autofillOff = "Noch nicht eingerichtet"
    override val autofillText = "Beim Anmelden tippst du auf das Anmeldefeld, wählst $APP und bestätigst mit der Gerätesperre."
    override val setUpAutofill = "Automatisch ausfüllen einrichten"
    override val changeAutofill = "In den Einstellungen ändern"
    override val howItWorks = "So funktionieren Passwörter"
    override val moreUnlock = "Entsperren: nur mit der Gerätesperre deines Handys. $APP fragt den Gerätecode nie selbst ab."
    override val moreOffline = "Offline: Deine Zugänge sind auch ohne Internet lesbar. Änderungen kommen auf deinen anderen Geräten an, sobald du wieder online bist."
    override val moreOnlyYou = "Zugriff: Zugänge gehören nur dir. Online liegen sie doppelt verschlüsselt, öffnen können sie nur deine Geräte. In Kreise oder Ablagen kommen sie nie."
    override val fillWithApp = "Mit $APP ausfüllen"
    override val chooseLogin = "Zugang wählen"
    override val matching = "Passend zu dieser Seite"
    override val otherLogins = "Weitere Zugänge"
    override val otp = "Einmalcode"
    override val otpAdd = "Einmalcode hinzufügen"
    override val otpScanHelp = "Halte die Kamera auf den QR-Code der Website."
    override val otpTypeKey = "Schlüssel eintippen"
    override val otpKey = "Schlüssel"
    override val otpKeyHint = "Der Text, den die Website statt des QR-Codes zeigt"
    override val otpNotCode = "Das ist kein QR-Code für Einmalcodes."
    override val otpInvalidKey = "Dieser Schlüssel stimmt nicht. Prüfe, ob alles abgetippt ist."
    override val otpAdded = "Einmalcode gespeichert"
    override val otpCopied = "Code kopiert"
    override fun otpSecondsLeft(seconds: Int) = "noch $seconds s"
    override fun otpDescription(code: String, seconds: Int) = "Einmalcode ${code.toList().joinToString(" ")}, noch $seconds Sekunden gültig. Antippen zum Kopieren."
    override val otpRemove = "Einmalcode entfernen"
    override val otpRemoveQuestion = "Einmalcode entfernen?"
    override val otpRemoveText = "$APP zeigt für diesen Zugang dann keinen Code mehr. Die Website fragt trotzdem weiter danach, bis du es dort ausschaltest."
    override val otpReadyToPaste = "Einmalcode liegt zum Einfügen bereit"
    override val fillCode = "Code mit $APP einfügen"
    override val passkey = "Passkey"
    override fun passkeyCreated(date: String) = "angelegt am $date"
    override val passkeyDeleteQuestion = "Passkey löschen?"
    override val passkeyDeleteText = "Mit diesem Passkey meldest du dich dann nicht mehr an. Lösche ihn auch in den Einstellungen der Website."
    override val passkeysNotHere = "Passkeys aus $APP gehen erst ab Android 14 bzw. iOS 17. Auf deinen anderen Geräten kannst du sie nutzen."
    override val passkeySave = "Passkey speichern"
    override val passkeySignIn = "Mit Passkey anmelden"
    override val needWebsite = "Bitte gib die Website ein."
    override val morePasskeys = "Passkeys und Einmalcodes: Bietet eine Website einen Passkey an, wähle $APP. Einen Einmalcode fügst du im Zugang mit der Kamera hinzu."
}

internal object PasswordTextsEn : PasswordTexts {
    override val title = "Passwords"
    override val intro = "Save your logins and fill them in when you sign in."
    override val search = "Search logins"
    override val logins = "Logins"
    override val noLogins = "No logins yet. Tap “Add login”."
    override val entrySubtitle = "Logins for websites and apps"
    override val addLogin = "Add login"
    override val editLogin = "Edit login"
    override val website = "Website"
    override val username = "Username or email"
    override val password = "Password"
    override val show = "Show"
    override val hide = "Hide"
    override val hiddenPassword = "Password hidden"
    override val copy = "Copy password"
    override val copied = "Password copied."
    override val edit = "Edit"
    override val delete = "Delete"
    override val save = "Save"
    override val websiteHint = "e.g. mybank.com"
    override val usernameHint = "e.g. name@example.com"
    override val addIntro = "A login needs nothing more: website, username or email, and password."
    override val needWebsiteAndPassword = "We need at least the website and the password."
    override fun deleteQuestion(website: String) = "Delete the login for $website?"
    override val deleteText = "It is then gone from all your devices."
    override val unlockReason = "Open passwords"
    override val unlock = "Unlock"
    override val lockedText = "Your passwords are locked. Open them with your phone's screen lock."
    override val setUpTitle = "Set up passwords"
    override val setUpText = "They only open with your phone's screen lock: fingerprint, face, or your phone's PIN. There is no extra password."
    override val setUpBackup = "Your logins are stored encrypted on all your devices and online. Only you can open them. With your emergency card, they also come to a new phone."
    override val setUp = "Set up"
    override val setUpFailed = "That did not work. Is a screen lock set up?"
    override val noDeviceLockTitle = "Set up a screen lock first"
    override val noDeviceLockText = "Passwords only open with your phone's lock. Set up a PIN, pattern or passcode for it."
    override val openDeviceLockSettings = "Set up screen lock"
    override val unreadableTitle = "Passwords can no longer be read"
    override val unreadableText = "This phone lost the key to your passwords, for example because the screen lock was turned off. Open them once on another of your devices, and they come back here. Otherwise, your emergency card brings them back."
    override val waitingTitle = "Your passwords are coming from your other device"
    override val waitingText = "Open the passwords once on your other device. After that, you can open them here too."
    override val startOver = "Start over"
    override val startOverQuestion = "Start passwords over?"
    override val startOverText = "The logins that cannot be read here will be deleted from this phone. They stay on your other devices and are added back the next time you open them there."
    override val autofill = "Autofill"
    override val autofillOn = "$APP fills in sign-ins"
    override val autofillOff = "Not set up yet"
    override val autofillText = "When you sign in, tap the login field, choose $APP and confirm with your screen lock."
    override val setUpAutofill = "Set up autofill"
    override val changeAutofill = "Change in settings"
    override val howItWorks = "How passwords work"
    override val moreUnlock = "Unlocking: only with your phone's screen lock. $APP never asks for the phone's code itself."
    override val moreOffline = "Offline: your logins can be read without internet. Changes reach your other devices as soon as you are online again."
    override val moreOnlyYou = "Access: logins belong only to you. Online they are encrypted twice, and only your devices can open them. They never go into circles or files."
    override val fillWithApp = "Fill in with $APP"
    override val chooseLogin = "Choose login"
    override val matching = "Matching this page"
    override val otherLogins = "Other logins"
    override val otp = "One-time code"
    override val otpAdd = "Add one-time code"
    override val otpScanHelp = "Point the camera at the website's QR code."
    override val otpTypeKey = "Type in key"
    override val otpKey = "Key"
    override val otpKeyHint = "The text the website shows instead of the QR code"
    override val otpNotCode = "This is not a QR code for one-time codes."
    override val otpInvalidKey = "This key is not right. Check that you typed all of it."
    override val otpAdded = "One-time code saved"
    override val otpCopied = "Code copied"
    override fun otpSecondsLeft(seconds: Int) = "$seconds s left"
    override fun otpDescription(code: String, seconds: Int) = "One-time code ${code.toList().joinToString(" ")}, valid for $seconds more seconds. Tap to copy."
    override val otpRemove = "Remove one-time code"
    override val otpRemoveQuestion = "Remove one-time code?"
    override val otpRemoveText = "$APP will no longer show a code for this login. The website keeps asking for it until you turn it off there."
    override val otpReadyToPaste = "One-time code ready to paste"
    override val fillCode = "Insert code with $APP"
    override val passkey = "Passkey"
    override fun passkeyCreated(date: String) = "created on $date"
    override val passkeyDeleteQuestion = "Delete passkey?"
    override val passkeyDeleteText = "You can then no longer sign in with this passkey. Delete it in the website's settings as well."
    override val passkeysNotHere = "Passkeys from $APP need Android 14 or iOS 17. You can use them on your other devices."
    override val passkeySave = "Save passkey"
    override val passkeySignIn = "Sign in with passkey"
    override val needWebsite = "Please enter the website."
    override val morePasskeys = "Passkeys and one-time codes: when a website offers a passkey, choose $APP. You add a one-time code in the login with the camera."
}
