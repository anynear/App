package app.anynear.app

import app.anynear.AndroidPlatform

/** Android (ADR 0071 item 9): the app's foreground service, otherwise without background. */
internal actual fun platformFinderKeepAlive(): FinderKeepAlive = AndroidPlatform.finderKeepAlive ?: NoFinderKeepAlive

/** Android (ADR 0081): WorkManager from :androidApp; without it (tests) no regular look. */
internal actual fun platformFinderSchedule(): FinderSchedule = AndroidPlatform.finderSchedule ?: NoFinderSchedule
