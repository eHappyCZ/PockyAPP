package com.pokecio.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.EncryptionInfo
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

@Composable
fun UnlockStep(
    encryption: EncryptionInfo?,
    onUseKey: () -> Unit,
    onUseDevice: () -> Unit,
) {
    var explainNoKey by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            encryption == null -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), color = PockyColors.AccentSoft, strokeWidth = 2.dp)
            }

            encryption.deviceVerified -> NoticeCard(
                icon = PockyIcons.CheckCircle,
                title = stringResource(R.string.onboarding_unlock_done),
                body = stringResource(R.string.onboarding_unlock_done_body),
                tint = PockyColors.Success,
            )

            !encryption.hasIdentity -> NoticeCard(
                icon = PockyIcons.Info,
                title = stringResource(R.string.onboarding_unlock_nothing),
                body = stringResource(R.string.onboarding_unlock_nothing_body),
            )

            else -> {
                if (encryption.canRecover) {
                    ActionCard(
                        icon = PockyIcons.Lock,
                        title = stringResource(R.string.onboarding_unlock_key),
                        body = stringResource(R.string.onboarding_unlock_key_body),
                        onClick = onUseKey,
                    )
                }
                if (encryption.canVerifyWithDevice) {
                    ActionCard(
                        icon = PockyIcons.Device,
                        title = stringResource(R.string.onboarding_unlock_device),
                        body = stringResource(R.string.onboarding_unlock_device_body),
                        onClick = onUseDevice,
                    )
                }
                if (!encryption.canRecover && !encryption.canVerifyWithDevice) {
                    NoticeCard(
                        icon = PockyIcons.Info,
                        title = stringResource(R.string.onboarding_unlock_unavailable),
                        body = stringResource(R.string.onboarding_unlock_unavailable_body),
                    )
                } else if (explainNoKey) {
                    NoticeCard(
                        icon = PockyIcons.Info,
                        title = stringResource(R.string.onboarding_unlock_no_key),
                        body = stringResource(R.string.onboarding_unlock_no_key_body),
                        tint = PockyColors.Gold,
                    )
                } else {
                    TextButton(
                        onClick = { explainNoKey = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.onboarding_unlock_no_key), color = PockyColors.AccentSoft)
                    }
                }
            }
        }
    }
}
