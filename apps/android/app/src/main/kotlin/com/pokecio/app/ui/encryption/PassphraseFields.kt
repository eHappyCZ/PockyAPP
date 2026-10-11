package com.pokecio.app.ui.encryption

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.PassphraseStrength
import com.pokecio.app.core.minPassphraseChars
import com.pokecio.app.core.passphraseStrength
import com.pokecio.app.core.recommendedPassphraseChars
import com.pokecio.app.ui.theme.PockyColors

fun passphraseReady(passphrase: String, repeat: String): Boolean {
    val strength = passphraseStrength(passphrase)
    return passphrase == repeat && (strength == PassphraseStrength.FAIR || strength == PassphraseStrength.STRONG)
}

@Composable
fun PassphraseFields(
    passphrase: String,
    onPassphrase: (String) -> Unit,
    repeat: String,
    onRepeat: (String) -> Unit,
    enabled: Boolean,
) {
    val strength = remember(passphrase) { passphraseStrength(passphrase) }
    val minimum = remember { minPassphraseChars().toInt() }
    val recommended = remember { recommendedPassphraseChars().toInt() }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphrase,
            label = { Text(stringResource(R.string.passphrase_label)) },
            supportingText = { Text(stringResource(R.string.passphrase_hint, minimum, recommended)) },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (passphrase.isNotEmpty()) {
            StrengthMeter(strength, missing = minimum - passphrase.trim().length)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = repeat,
            onValueChange = onRepeat,
            label = { Text(stringResource(R.string.passphrase_repeat)) },
            isError = repeat.isNotEmpty() && repeat != passphrase,
            supportingText = if (repeat.isNotEmpty() && repeat != passphrase) {
                { Text(stringResource(R.string.passphrase_mismatch)) }
            } else {
                null
            },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.passphrase_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StrengthMeter(strength: PassphraseStrength, missing: Int) {
    val (fraction, target, label) = when (strength) {
        PassphraseStrength.TOO_SHORT -> Triple(0.15f, MaterialTheme.colorScheme.error, pluralStringResource(R.plurals.passphrase_too_short, missing, missing))
        PassphraseStrength.NO_SYMBOL -> Triple(0.3f, MaterialTheme.colorScheme.error, stringResource(R.string.passphrase_no_symbol))
        PassphraseStrength.WEAK -> Triple(0.4f, MaterialTheme.colorScheme.error, stringResource(R.string.passphrase_weak))
        PassphraseStrength.FAIR -> Triple(0.7f, PockyColors.Gold, stringResource(R.string.passphrase_fair))
        PassphraseStrength.STRONG -> Triple(1f, PockyColors.Success, stringResource(R.string.passphrase_strong))
    }
    val width by animateFloatAsState(fraction, label = "strength")
    val color by animateColorAsState(target, label = "strength-color")

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
