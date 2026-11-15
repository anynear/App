package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.remember
import app.anynear.AppConfig
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.app.AppUpdateState
import app.anynear.app.DarkMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment

/**
 * „Neue Version“ hint when opening the app, once per version and session. After „Jetzt aktualisieren“
 * the same dialog shows the progress until Android takes over with its own dialog.
 */
@Composable
internal fun AppUpdateDialog(controller: AppController) {
    val state by controller.appUpdate.collectAsState()
    // Version last dismissed with „Später“; "started" keeps the dialog open while loading.
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    var started by rememberSaveable { mutableStateOf(false) }

    val current = state
    if (current is AppUpdateState.MovedToNewApp) {
        // New app ID: explain once per session, afterwards it is shown under „Über anynear“.
        if (dismissed != MOVED_KEY) MovedToNewAppDialog { dismissed = MOVED_KEY }
        return
    }
    if (current is AppUpdateState.ReinstallNeeded) {
        // New signing key (ADR 0037): explain once per session, afterwards it is shown under „Über anynear“.
        if (dismissed != REINSTALL_KEY) ReinstallDialog(controller) { dismissed = REINSTALL_KEY }
        return
    }
    val version = when (current) {
        is AppUpdateState.Available -> current.version
        is AppUpdateState.Downloading -> current.version
        is AppUpdateState.NeedsPermission -> current.version
        is AppUpdateState.Installing -> current.version
        is AppUpdateState.Failed -> current.version
        else -> null
    }
    val show = when (current) {
        is AppUpdateState.Available -> started || dismissed != current.version
        is AppUpdateState.Downloading, is AppUpdateState.NeedsPermission, is AppUpdateState.Failed, is AppUpdateState.Installing -> started
        else -> false
    }
    if (!show) {
        started = false
        return
    }
    val close = {
        dismissed = version
        started = false
    }
    // „Später“ only as long as one can really choose. Afterwards Android takes over with its own dialog,
    // a second button for cancelling is then only confusing.
    val canPostpone = current is AppUpdateState.Available || (current is AppUpdateState.Failed && current.version != null)

    AppDialog(
        onDismissRequest = { if (canPostpone || current is AppUpdateState.Failed) close() },
        title = { Text(texts.settings.newVersionTitle(version?.ifBlank { null })) },
        text = { UpdateProgress(current, intro = texts.settings.updateIntro) },
        confirmButton = {
            when (current) {
                is AppUpdateState.Available, is AppUpdateState.NeedsPermission ->
                    AppButton(texts.settings.updateNow, onClick = {
                        started = true
                        controller.installAppUpdate()
                    })
                is AppUpdateState.Failed ->
                    if (current.version != null) {
                        AppButton(texts.common.retry, controller::installAppUpdate)
                    } else {
                        AppButton(texts.common.ok, close)
                    }
                else -> Unit
            }
        },
        dismissButton = {
            if (canPostpone) AppButton(texts.common.later, close, kind = ButtonKind.Quiet)
        },
    )
}

/** „Über anynear“ section in the settings: version and update. */
@Composable
internal fun AppUpdateSection(controller: AppController) {
    val state by controller.appUpdate.collectAsState()
    val version = controller.appVersion?.let { texts.settings.version(it) }
    var showingLicense by rememberSaveable { mutableStateOf(false) }
    if (showingLicense) {
        AppDialog(
            onDismissRequest = { showingLicense = false },
            title = { Text(texts.settings.license) },
            text = { Text(texts.settings.licenseText, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { AppButton(texts.common.close, { showingLicense = false }, kind = ButtonKind.Quiet) },
        )
    }
    AppSection(texts.settings.aboutApp) {
        // Licence from LICENSE and TRADEMARKS.md: code free under GPL, name and logos excepted.
        AppRow(title = texts.settings.license, subtitle = texts.settings.licenseShort, icon = AppIcons.Dokument, chevron = true, onClick = { showingLicense = true })
        when (val current = state) {
            AppUpdateState.Unsupported -> AppRow(title = appName, subtitle = version, icon = AppIcons.Kreis)
            AppUpdateState.Idle, AppUpdateState.UpToDate -> AppRow(
                title = if (current == AppUpdateState.UpToDate) texts.settings.upToDate else appName,
                subtitle = version,
                icon = AppIcons.Kreis,
                trailing = { AppButton(texts.settings.check, controller::checkAppUpdate, kind = ButtonKind.Secondary) },
            )
            AppUpdateState.Checking -> AppRow(title = texts.settings.checking, subtitle = version, icon = AppIcons.Kreis)
            is AppUpdateState.MovedToNewApp -> {
                AppRow(title = texts.appMove.movedRow, subtitle = version, icon = AppIcons.Aktualisieren)
                SectionContent { MovedToNewAppContent() }
            }
            is AppUpdateState.ReinstallNeeded -> {
                AppRow(title = texts.appMove.reinstallRow, subtitle = version, icon = AppIcons.Aktualisieren)
                SectionContent { ReinstallContent(controller) }
            }
            is AppUpdateState.Available, is AppUpdateState.NeedsPermission -> {
                AppRow(title = texts.settings.newVersionAvailable(current.newVersion()), subtitle = version, icon = AppIcons.Aktualisieren)
                SectionContent {
                    if (current is AppUpdateState.NeedsPermission) UpdateProgress(current, intro = "")
                    AppButton(texts.settings.updateNow, controller::installAppUpdate, Modifier.fillMaxWidth())
                }
            }
            is AppUpdateState.Downloading, is AppUpdateState.Installing -> {
                AppRow(title = texts.settings.updating(current.newVersion()), subtitle = version, icon = AppIcons.Aktualisieren)
                SectionContent { UpdateProgress(current, intro = "") }
            }
            is AppUpdateState.Failed -> {
                AppRow(title = current.message, subtitle = version, icon = AppIcons.Aktualisieren, danger = true)
                SectionContent {
                    if (current.version != null) {
                        AppButton(texts.common.retry, controller::installAppUpdate, Modifier.fillMaxWidth())
                    } else {
                        AppButton(texts.settings.checkForUpdate, controller::checkAppUpdate, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                    }
                }
            }
        }
    }
}

/** Appearance light, dark or like the phone; selection in the dialog. */
@Composable
internal fun DarkModeRow(controller: AppController) {
    val mode by controller.darkMode.collectAsState()
    var choosing by rememberSaveable { mutableStateOf(false) }
    AppRow(title = texts.settings.lightOrDark, subtitle = mode.text(), icon = AppIcons.Bild, chevron = true, onClick = { choosing = true })
    if (choosing) {
        AppDialog(
            onDismissRequest = { choosing = false },
            title = { Text(texts.settings.appearance) },
            text = {
                Column {
                    DarkMode.entries.forEach { option ->
                        val pick = {
                            controller.setDarkMode(option)
                            choosing = false
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = pick).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == mode, onClick = pick)
                            Text(option.text(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { AppButton(texts.common.done, { choosing = false }, kind = ButtonKind.Quiet) },
        )
    }
}

@Composable
internal fun UpdateProgress(state: AppUpdateState, intro: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            is AppUpdateState.Available -> Text(intro)
            is AppUpdateState.NeedsPermission -> Text(texts.settings.needsInstallPermission)
            is AppUpdateState.Downloading -> {
                Text(texts.settings.downloading(state.version.ifBlank { null }))
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            is AppUpdateState.Installing -> Text(texts.settings.installing)
            is AppUpdateState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
    }
}

/** Name of the appearance in the app's language. */
private fun DarkMode.text(): String = when (this) {
    DarkMode.SYSTEM -> texts.settings.appearanceSystem
    DarkMode.LIGHT -> texts.settings.appearanceLight
    DarkMode.DARK -> texts.settings.appearanceDark
}

private const val MOVED_KEY = "moved"
private const val REINSTALL_KEY = "reinstall"

/** Explains the reinstall because of the new signing key (ADR 0037). */
@Composable
private fun ReinstallDialog(controller: AppController, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.appMove.reinstallTitle) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { ReinstallContent(controller) } },
        confirmButton = { AppButton(texts.common.later, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Briefly explained, three steps in this order: first download, then delete, then install. */
@Composable
private fun ReinstallContent(controller: AppController) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(texts.appMove.reinstallText, style = MaterialTheme.typography.bodyMedium)
        texts.appMove.reinstallSteps.forEachIndexed { i, step ->
            Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
        }
        AppButton(texts.appMove.loadNewVersion, { uriHandler.openUri(AppConfig.APP_PAGE_URL) }, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
        AppButton(texts.appMove.deleteThisApp, controller::uninstallThisApp, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

/**
 * Explains the move to the new app `app.anynear` (ADR 0061, step 2.1). Because of the hard cut the new app starts
 * empty.
 */
@Composable
private fun MovedToNewAppDialog(onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.appMove.movedTitle) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { MovedToNewAppContent() } },
        confirmButton = { AppButton(texts.common.later, onDismiss, kind = ButtonKind.Quiet) },
    )
}

/** Briefly explained, three steps, plus „Neue App laden“. */
@Composable
private fun MovedToNewAppContent() {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(texts.appMove.movedText, style = MaterialTheme.typography.bodyMedium)
        texts.appMove.movedSteps.forEachIndexed { i, step ->
            Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
        }
        AppButton(texts.appMove.loadNewApp, { uriHandler.openUri(AppConfig.APP_PAGE_URL) }, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
    }
}

/**
 * After the move to the new app ID (ADR 0025): once per session, as long as the old app is still installed.
 * The button opens Android's uninstall dialog for exactly the old app, so one cannot catch the wrong
 * one (both are called anynear).
 */
@Composable
internal fun OldAppDialog(controller: AppController) {
    val show by controller.oldAppToRemove.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (!show || dismissed) return
    AppDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(texts.appMove.oldAppTitle) },
        text = { OldAppContent(controller) },
        confirmButton = { AppButton(texts.common.later, { dismissed = true }, kind = ButtonKind.Quiet) },
    )
}

/** Entry under „Über anynear“ as long as the old app is still there. */
@Composable
internal fun OldAppSection(controller: AppController) {
    val show by controller.oldAppToRemove.collectAsState()
    if (!show) return
    AppSection(texts.appMove.oldAppRow) {
        SectionContent { OldAppContent(controller) }
    }
}

@Composable
private fun OldAppContent(controller: AppController) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(texts.appMove.oldAppText, style = MaterialTheme.typography.bodyMedium)
        Text(texts.appMove.oldAppHowTo, style = MaterialTheme.typography.bodyMedium)
        AppButton(texts.appMove.deleteOldApp, controller::uninstallOldApp, Modifier.fillMaxWidth(), kind = ButtonKind.Main)
    }
}

/** Version that is being updated to; `null` if there is none right now. */
private fun AppUpdateState.newVersion(): String? = when (this) {
    is AppUpdateState.Available -> version
    is AppUpdateState.Downloading -> version
    is AppUpdateState.NeedsPermission -> version
    is AppUpdateState.Installing -> version
    is AppUpdateState.Failed -> version
    else -> null
}?.ifBlank { null }
