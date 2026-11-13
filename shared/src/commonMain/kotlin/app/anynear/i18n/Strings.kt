package app.anynear.i18n

/**
 * All texts that people see in the app, in German and English (ADR 0026). Per area one
 * catalogue with both languages side by side; if a translation is missing, the app does not build.
 *
 * Access via [texts], e.g. `texts.chat.send`. New texts go into the matching area, never as
 * a fixed string into a screen.
 */
class Strings private constructor(
    val language: Language,
    /** Words that occur everywhere: OK, Abbrechen, Gestern … */
    val common: CommonTexts,
    /** Messages, conversations, attachments, message details. */
    val chat: ChatTexts,
    /** Circles, contacts, people, invitations, QR code. */
    val people: PeopleTexts,
    /** Setup of the app, the circle and the radio. */
    val setup: SetupTexts,
    /** Settings, advanced, log, update. */
    val settings: SettingsTexts,
    /** States and messages from the app logic (AppController and below). */
    val status: StatusTexts,
    /** Notifications and texts of the platform (Android services, iOS extension). */
    val system: SystemTexts,
    /** Storage, for now only as a preview. */
    val files: FilesTexts,
    /** Passwords, for now only as a preview. */
    val passwords: PasswordTexts,
    /** Manual backup and restore. */
    val backup: BackupTexts,
    /** Several devices, emergency card (ADR 0042). */
    val devices: DevicesTexts,
    /** Public radio of the area (ADR 0055). */
    val publicRadio: PublicRadioTexts,
    /** New software for the radio (ADR 0056). */
    val radioSoftware: RadioSoftwareTexts,
    /** Alerts and channels (ADR 0062). */
    val alerts: AlertTexts,
    /** Troubleshooting and support as a chat (ADR 0065). */
    val support: SupportTexts,
    /** Search the phone for important documents (ADR 0071). */
    val find: FindTexts,
    /** Check-ins (ADR 0083). */
    val lifeSigns: LifeSignTexts,
) {
    companion object {
        val german: Strings by lazy {
            Strings(Language.GERMAN, CommonTextsDe, ChatTextsDe, PeopleTextsDe, SetupTextsDe, SettingsTextsDe, StatusTextsDe, SystemTextsDe, FilesTextsDe, PasswordTextsDe, BackupTextsDe, DevicesTextsDe, PublicRadioTextsDe, RadioSoftwareTextsDe, AlertTextsDe, SupportTextsDe, FindTextsDe, LifeSignTextsDe)
        }
        val english: Strings by lazy {
            Strings(Language.ENGLISH, CommonTextsEn, ChatTextsEn, PeopleTextsEn, SetupTextsEn, SettingsTextsEn, StatusTextsEn, SystemTextsEn, FilesTextsEn, PasswordTextsEn, BackupTextsEn, DevicesTextsEn, PublicRadioTextsEn, RadioSoftwareTextsEn, AlertTextsEn, SupportTextsEn, FindTextsEn, LifeSignTextsEn)
        }

        fun of(language: Language): Strings = when (language) {
            Language.GERMAN -> german
            Language.ENGLISH -> english
        }
    }
}

/** Texts in the language of the phone. If the language changes, Android rebuilds the screen. */
val texts: Strings get() = Strings.of(currentLanguage())
