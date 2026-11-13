package app.anynear.app

import app.anynear.i18n.texts

/** Light or dark: like the phone, or fixed choice. */
enum class DarkMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    val label: String
        get() = when (this) {
            SYSTEM -> texts.status.darkModeSystem
            LIGHT -> texts.status.darkModeLight
            DARK -> texts.status.darkModeDark
        }
}
