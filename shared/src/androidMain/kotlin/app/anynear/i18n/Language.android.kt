package app.anynear.i18n

import java.util.Locale

internal actual fun systemLocaleTag(): String = Locale.getDefault().toLanguageTag()
