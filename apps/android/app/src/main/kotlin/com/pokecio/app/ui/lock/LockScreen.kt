package com.pokecio.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.lock.PinState
import com.pokecio.app.ui.room.clock
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    state: PinState,
    biometrics: Boolean,
    onPin: (String) -> Unit,
    onBiometrics: () -> Unit,
    onLogout: () -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var forgot by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val blockedUntil = (state as? PinState.Blocked)?.untilMs
    val blocked = blockedUntil != null && blockedUntil > now

    LaunchedEffect(Unit) { if (biometrics) onBiometrics() }
    LaunchedEffect(state) { if (state is PinState.Wrong || state is PinState.Blocked) entered = "" }
    LaunchedEffect(blockedUntil) {
        while (blockedUntil != null && System.currentTimeMillis() < blockedUntil) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(PockyColors.Bg)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Icon(PockyIcons.Lock, contentDescription = null, tint = PockyColors.AccentSoft, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PockyColors.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.lock_enter_pin),
            style = MaterialTheme.typography.bodyLarge,
            color = PockyColors.InkMuted,
        )
        Spacer(Modifier.height(28.dp))
        PinDots(filled = entered.length, error = state is PinState.Wrong && entered.isEmpty())
        Text(
            text = when {
                blocked -> stringResource(R.string.lock_blocked, clock(blockedUntil - now))
                state is PinState.Wrong -> pluralStringResource(R.plurals.lock_wrong, state.attemptsLeft, state.attemptsLeft)
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .padding(top = 14.dp),
        )
        Spacer(Modifier.height(12.dp))
        PinPad(
            enabled = !blocked && state != PinState.Checking,
            onDigit = { digit ->
                if (entered.length < PIN_LENGTH) {
                    entered += digit
                    if (entered.length == PIN_LENGTH) onPin(entered)
                }
            },
            onDelete = { entered = entered.dropLast(1) },
            extra = if (biometrics) PockyIcons.Fingerprint else null,
            extraLabel = stringResource(R.string.lock_biometric_use),
            onExtra = onBiometrics,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { forgot = true }) {
            Text(stringResource(R.string.lock_forgot), color = PockyColors.AccentSoft)
        }
    }

    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            containerColor = PockyColors.Surface,
            title = { Text(stringResource(R.string.lock_forgot_title)) },
            text = { Text(stringResource(R.string.lock_forgot_text)) },
            confirmButton = {
                TextButton(onClick = {
                    forgot = false
                    onLogout()
                }) {
                    Text(stringResource(R.string.lock_forgot_logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { forgot = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
