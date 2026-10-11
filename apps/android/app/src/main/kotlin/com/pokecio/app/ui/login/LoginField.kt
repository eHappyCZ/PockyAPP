package com.pokecio.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pokecio.app.ui.theme.PockyColors

private val FieldShape = RoundedCornerShape(14.dp)

@Composable
internal fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = MaterialTheme.typography.bodyLarge

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = PockyColors.Ink),
        cursorBrush = SolidColor(PockyColors.Accent),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { input ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .background(PockyColors.Surface, FieldShape)
                    .border(
                        width = 1.dp,
                        color = if (focused) PockyColors.Accent else PockyColors.InkMuted.copy(alpha = 0.15f),
                        shape = FieldShape,
                    )
                    .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
            ) {
                if (prefix != null) {
                    Text(prefix, style = textStyle, color = PockyColors.InkMuted)
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle, color = PockyColors.InkMuted.copy(alpha = 0.5f))
                    }
                    input()
                }
                if (suffix != null) {
                    Text(suffix, style = textStyle, color = PockyColors.InkMuted)
                }
                trailing?.invoke()
            }
        },
    )
}
