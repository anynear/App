package app.anynear.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

internal actual fun systemLocaleTag(): String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "de-DE"
