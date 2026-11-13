package app.anynear

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Name and icon of the app in notifications and on the home screen. Package and app ID stay (ADR 0025). */
object AppBrand {
    const val name: String = AppConfig.APP_NAME

    val notificationIcon: Int get() = R.drawable.ic_stat_app

    /**
     * The brand tests (until October 2026) could disable the launcher icon `Launcher` and enable a different
     * one. The others no longer exist; without this step the app would then have no icon.
     */
    fun restoreLauncher(context: Context) {
        val component = ComponentName(context, "${MainActivity::class.java.`package`!!.name}.Launcher")
        val pm = context.packageManager
        if (pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
    }
}
