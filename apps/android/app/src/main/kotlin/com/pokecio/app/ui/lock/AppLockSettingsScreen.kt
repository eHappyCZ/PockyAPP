package com.pokecio.app.ui.lock

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.AppLock
import com.pokecio.app.core.appLockPinAllowed
import com.pokecio.app.lock.AppLockController
import com.pokecio.app.lock.Biometrics
import com.pokecio.app.lock.PinState
import com.pokecio.app.ui.home.LinkRow
import com.pokecio.app.ui.home.RowDivider
import com.pokecio.app.ui.home.Section
import com.pokecio.app.ui.home.ToggleRow
import com.pokecio.app.ui.people.PeopleHeader
import com.pokecio.app.ui.room.clock
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import kotlinx.coroutines.launch

private enum class Flow { OVERVIEW, SETUP, CHANGE, DISABLE }

private val TIMEOUTS = listOf(
    0 to R.string.app_lock_timeout_now,
    60 to R.string.app_lock_timeout_1,
    300 to R.string.app_lock_timeout_5,
    900 to R.string.app_lock_timeout_15,
)

@Composable
fun AppLockSettingsScreen(
    settings: AppLock?,
    controller: AppLockController,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val canUseBiometrics = remember { Biometrics.available(context) }
    var flow by remember { mutableStateOf(Flow.OVERVIEW) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        PeopleHeader(
            title = stringResource(R.string.settings_app_lock),
            subtitle = null,
            busy = false,
            onBack = { if (flow == Flow.OVERVIEW) onBack() else flow = Flow.OVERVIEW },
        )

        when (flow) {
            Flow.OVERVIEW -> Overview(
                settings = settings,
                canUseBiometrics = canUseBiometrics,
                onEnable = { flow = Flow.SETUP },
                onChange = { flow = Flow.CHANGE },
                onDisable = { flow = Flow.DISABLE },
                onUpdate = controller::update,
            )
            Flow.SETUP -> PinFlow(
                needsCurrent = false,
                verify = { PinState.Idle },
                onDone = { pin ->
                    scope.launch {
                        controller.enable(pin, biometrics = canUseBiometrics, timeoutSeconds = 60)
                        flow = Flow.OVERVIEW
                    }
                },
            )
            Flow.CHANGE -> PinFlow(
                needsCurrent = true,
                verify = controller::verify,
                onDone = { pin ->
                    scope.launch {
                        controller.enable(
                            pin,
                            biometrics = settings?.biometrics == true,
                            timeoutSeconds = settings?.timeoutSeconds?.toInt() ?: 60,
                        )
                        flow = Flow.OVERVIEW
                    }
                },
            )
            Flow.DISABLE -> PinFlow(
                needsCurrent = true,
                verify = controller::disable,
                onDone = { flow = Flow.OVERVIEW },
                onlyCurrent = true,
            )
        }
    }
}

@Composable
private fun Overview(
    settings: AppLock?,
    canUseBiometrics: Boolean,
    onEnable: () -> Unit,
    onChange: () -> Unit,
    onDisable: () -> Unit,
    onUpdate: (Boolean, Int) -> Unit,
) {
    val enabled = settings?.enabled == true
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_lock_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.InkMuted,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        if (!enabled || settings == null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onEnable,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.app_lock_enable))
            }
            return@Column
        }

        Section(stringResource(R.string.settings_app_lock)) {
            if (canUseBiometrics) {
                ToggleRow(
                    icon = PockyIcons.Fingerprint,
                    title = stringResource(R.string.app_lock_biometrics),
                    checked = settings.biometrics,
                    onChange = { onUpdate(it, settings.timeoutSeconds.toInt()) },
                )
                RowDivider()
            }
            LinkRow(icon = PockyIcons.Lock, title = stringResource(R.string.app_lock_change_pin), onClick = onChange)
            RowDivider()
            LinkRow(
                icon = PockyIcons.Lock,
                title = stringResource(R.string.app_lock_disable),
                tint = MaterialTheme.colorScheme.error,
                onClick = onDisable,
            )
        }

        Section(stringResource(R.string.app_lock_timeout)) {
            TIMEOUTS.forEach { (seconds, label) ->
                TimeoutRow(
                    label = label,
                    selected = settings.timeoutSeconds.toInt() == seconds,
                    onClick = { onUpdate(settings.biometrics, seconds) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TimeoutRow(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = PockyColors.Accent),
            modifier = Modifier.padding(10.dp),
        )
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = PockyColors.Ink)
    }
}

@Composable
private fun PinFlow(
    needsCurrent: Boolean,
    verify: suspend (String) -> PinState,
    onDone: (String) -> Unit,
    onlyCurrent: Boolean = false,
) {
    var step by remember { mutableStateOf(if (needsCurrent) 0 else 1) }
    var entered by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mismatch = stringResource(R.string.app_lock_mismatch)
    val simple = stringResource(R.string.app_lock_simple)
    val resources = LocalContext.current.resources

    val title = when (step) {
        0 -> R.string.app_lock_current_pin
        1 -> R.string.app_lock_new_pin
        else -> R.string.app_lock_confirm_pin
    }

    val submit: (String) -> Unit = { pin ->
        when (step) {
            0 -> {
                busy = true
                scope.launch {
                    val result = verify(pin)
                    busy = false
                    entered = ""
                    when (result) {
                        PinState.Idle -> if (onlyCurrent) onDone(pin) else step = 1
                        is PinState.Wrong -> problem = resources.getQuantityString(
                            R.plurals.lock_wrong,
                            result.attemptsLeft,
                            result.attemptsLeft,
                        )
                        is PinState.Blocked -> problem = resources.getString(
                            R.string.lock_blocked,
                            clock(result.untilMs - System.currentTimeMillis()),
                        )
                        PinState.Checking -> Unit
                    }
                }
            }
            1 -> {
                entered = ""
                if (!appLockPinAllowed(pin)) {
                    problem = simple
                } else {
                    first = pin
                    problem = null
                    step = 2
                }
            }
            else -> {
                entered = ""
                if (pin == first) {
                    onDone(pin)
                } else {
                    problem = mismatch
                    first = ""
                    step = 1
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            color = PockyColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PinDots(filled = entered.length, error = problem != null && entered.isEmpty())
        Text(
            text = problem.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .padding(top = 14.dp),
        )
        Spacer(Modifier.height(12.dp))
        PinPad(
            enabled = !busy,
            onDigit = { digit ->
                if (entered.length < PIN_LENGTH) {
                    entered += digit
                    if (entered.length == PIN_LENGTH) submit(entered)
                }
            },
            onDelete = { entered = entered.dropLast(1) },
        )
    }
}
