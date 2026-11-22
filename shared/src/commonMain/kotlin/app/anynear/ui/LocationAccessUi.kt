package app.anynear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.location.LocationAccess
import app.anynear.location.LocationAccessState
import app.anynear.location.LocationNeed
import app.anynear.location.LocationPurpose
import kotlinx.coroutines.launch

/** The one sheet for the location permission of an action (ADR 0125 §2): one sentence why, „Erlauben“ or go on. */
@Composable
internal fun LocationAccessSheet(controller: AppController) {
    val access = controller.locationAccess ?: return
    val ask by access.asking.collectAsState()
    val current = ask ?: return
    val lt = texts.location
    val why = when (current.purpose) {
        LocationPurpose.SosContact -> lt.accessSosContact
        LocationPurpose.LifeSign -> lt.accessLifeSign
        LocationPurpose.Share -> lt.accessShare
        LocationPurpose.SendPlace -> lt.accessSendPlace
        LocationPurpose.Level -> if (current.need == LocationNeed.Always) lt.accessAlways else lt.accessLevel
    }
    AppDialog(
        onDismissRequest = { access.answer(false) },
        title = { Text(lt.accessTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(why)
                if (access.state(current.need) == LocationAccessState.Off) {
                    Text(lt.accessOff, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { AppButton(lt.accessAllow, { access.answer(true) }) },
        dismissButton = { AppButton(lt.accessSkip, { access.answer(false) }, kind = ButtonKind.Quiet) },
    )
}

/**
 * The hint line where an action needs the location and the phone does not allow it (ADR 0125 §4, §5): nothing while
 * all is well; a tap goes the steps through the system without the sheet.
 */
@Composable
internal fun LocationAccessRow(access: LocationAccess, need: LocationNeed, purpose: LocationPurpose, text: String) {
    val changes by access.changes.collectAsState(null)
    val minute = rememberMinute()
    val state = remember(changes, minute) { access.state(need) }
    if (state == LocationAccessState.Ok) return
    val lt = texts.location
    val title = when (state) {
        LocationAccessState.Off -> lt.offTitle
        LocationAccessState.Limited -> lt.roughTitle
        else -> lt.missingTitle
    }
    val scope = rememberCoroutineScope()
    AppRow(title = title, subtitle = text, icon = AppIcons.Achtung, chevron = true, onClick = { scope.launch { access.fixAndWait(need, purpose) } })
}

/** „Immer“ was taken away while a level needs it (ADR 0125 §5): once per loss. */
@Composable
internal fun AlwaysLostDialog(access: LocationAccess) {
    val lt = texts.location
    AppDialog(
        onDismissRequest = access::acknowledgeAlwaysLost,
        title = { Text(lt.alwaysLostTitle) },
        text = { Text(lt.alwaysLostText) },
        confirmButton = {
            AppButton(lt.alwaysLostFix, {
                access.acknowledgeAlwaysLost()
                access.fix(LocationNeed.Always, LocationPurpose.Level)
            })
        },
        dismissButton = { AppButton(lt.alwaysLostLater, access::acknowledgeAlwaysLost, kind = ButtonKind.Quiet) },
    )
}
