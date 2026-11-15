package app.anynear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import app.anynear.app.CardCodeInput
import app.anynear.i18n.texts

/**
 * Field for the code of the emergency card, the same everywhere it is typed (create, renew, recover, lock out): capital
 * letters and dashes come by themselves ([CardCodeInput]), monospace like the code on the card. [onChange] gets the
 * formatted text; `RecoveryCode.parse` reads it as it is.
 */
@Composable
internal fun CardCodeField(onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = texts.devices.cardCode) {
    // Not in the saved state of the window: codes and passwords stay out of it.
    var value by remember { mutableStateOf(TextFieldValue("")) }
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val f = CardCodeInput.format(value.text, new.text, new.selection.end)
            value = TextFieldValue(f.text, TextRange(f.cursor), new.composition?.takeIf { f.text == new.text })
            onChange(f.text)
        },
        singleLine = true,
        label = { Text(label) },
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
