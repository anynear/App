package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.mesh.DeviceName
import app.anynear.mesh.DeviceSetupCheck
import app.anynear.mesh.ProfileDeviation

/**
 * „Funkgerät einrichten“ (ADR 0006): new device → notice, differently configured device → question,
 * router device → question with a warning. The name always starts with „anynear“, only the rest is free.
 * Already set-up device with a changed profile → „Funkgerät aktualisieren“, without a name field.
 */
@Composable
internal fun DeviceSetupDialog(controller: AppController) {
    val prompt by controller.deviceSetupPrompt.collectAsState()
    val current = prompt ?: return
    val check = current.check
    // Suggest again only for a new state, not for every newly heard device ID.
    var suffix by remember(check) { mutableStateOf(current.suggestedNameSuffix) }
    val taken = DeviceName.isTaken(suffix, current.takenNames)
    val valid = DeviceName.isValidSuffix(suffix)
    val isNew = check.kind == DeviceSetupCheck.Kind.NEW
    // Already set up: the name is fixed, it is only about improved settings.
    val isUpdate = check.kind == DeviceSetupCheck.Kind.UPDATE
    var showDetails by remember { mutableStateOf(false) }

    AppDialog(
        // A new device does not send at all without setup, hence no „Nicht jetzt“ there.
        onDismissRequest = { if (!isNew) controller.postponeDeviceSetup() },
        title = {
            Text(
                when {
                    isNew -> texts.setup.setUpRadio
                    isUpdate -> texts.setup.updateRadio
                    else -> texts.setup.setUpRadioForApp
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val onlyName = check.deviations == setOf(ProfileDeviation.NAME)
                Text(
                    when {
                        onlyName -> texts.setup.setupOnlyName
                        isUpdate -> texts.setup.setupUpdate
                        isNew -> texts.setup.setupNew
                        else -> texts.setup.setupOther
                    },
                )
                if (check.kind == DeviceSetupCheck.Kind.ROUTER) {
                    Text(texts.setup.routerWarning, color = MaterialTheme.colorScheme.error)
                }
                if (!isUpdate) OutlinedTextField(
                    value = suffix,
                    onValueChange = { suffix = DeviceNameLimit.accept(suffix, it) },
                    label = { Text(texts.setup.radioName) },
                    prefix = { Text(DeviceName.PREFIX) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = when {
                        !valid -> ({ Text(texts.setup.radioNameInvalid) })
                        taken -> ({ Text(texts.setup.radioNameTaken) })
                        else -> DeviceNameLimit.hint(suffix)?.let { { TextLimitHint(it) } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(if (showDetails) texts.setup.showLess else texts.common.moreInfo, { showDetails = !showDetails }, kind = ButtonKind.Quiet)
                if (showDetails) {
                    setupDetails(check).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            AppButton(
                when (check.kind) {
                    DeviceSetupCheck.Kind.ROUTER -> texts.setup.setUpAnyway
                    DeviceSetupCheck.Kind.UPDATE -> texts.setup.update
                    else -> texts.setup.setUp
                },
                { controller.applyDeviceSetup(suffix) },
                enabled = valid,
            )
        },
        dismissButton = if (isNew) null else ({ AppButton(texts.common.notNow, controller::postponeDeviceSetup, kind = ButtonKind.Quiet) }),
    )
}

/** Expandable details, so that the dialog itself stays short. */
private fun setupDetails(check: DeviceSetupCheck): List<String> = buildList {
    when (check.kind) {
        DeviceSetupCheck.Kind.NEW -> Unit
        DeviceSetupCheck.Kind.UPDATE -> add(texts.setup.detailsUpdate)
        else -> add(texts.setup.detailsOther)
    }
    add(texts.setup.detailsSettings)
    if (check.kind == DeviceSetupCheck.Kind.ROUTER) {
        add(texts.setup.detailsRouter)
    }
    add(texts.setup.detailsUnchanged)
    if (check.kind == DeviceSetupCheck.Kind.UPDATE) {
        add(texts.setup.detailsNameKept)
    } else {
        add(texts.setup.detailsNamePublic)
    }
}
