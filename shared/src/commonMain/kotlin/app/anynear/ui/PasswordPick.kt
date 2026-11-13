package app.anynear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anynear.i18n.texts
import app.anynear.passwords.Login
import app.anynear.passwords.LoginMatch

/**
 * Selection for autofill (Android, ADR 0039, item 6), only after the device lock: logins matching
 * the site [domain] at the top, the others below. Apps without a domain only get the list to choose from themselves.
 */
@Composable
fun PasswordPickScreen(logins: List<Login>, domain: String?, onPick: (Login) -> Unit, onCancel: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AppTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Own window above the other app: keep the system's insets clear ourselves, status bar night blue as in the app.
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                Box(Modifier.fillMaxWidth().background(AppTheme.colors.header).windowInsetsTopHeight(WindowInsets.statusBars))
                AppHeader(texts.passwords.chooseLogin, subtitle = domain, leading = { HeaderAction(AppIcons.Schliessen, texts.common.cancel, onCancel) })
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SearchField(query, { query = it }, texts.passwords.search)
                    val found = logins.filter { matchesSearch(query, it.website, it.username) }
                    val (matching, others) = found.partition { domain != null && LoginMatch.matches(it, domain) }
                    if (matching.isNotEmpty()) LoginSection(texts.passwords.matching, LoginMatch.sorted(matching, null), onPick)
                    if (others.isNotEmpty()) {
                        LoginSection(if (matching.isEmpty()) texts.passwords.logins else texts.passwords.otherLogins, LoginMatch.sorted(others, null), onPick)
                    }
                    if (found.isEmpty()) NoSearchResults()
                }
            }
        }
    }
}

@Composable
private fun LoginSection(title: String, logins: List<Login>, onPick: (Login) -> Unit) {
    AppSection(title) {
        logins.forEach { login ->
            AppRow(
                title = LoginMatch.displayName(login.website),
                subtitle = login.username.ifEmpty { null },
                icon = AppIcons.Schluessel,
                onClick = { onPick(login) },
            )
        }
    }
}
