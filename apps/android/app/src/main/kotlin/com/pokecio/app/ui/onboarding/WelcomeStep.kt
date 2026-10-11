package com.pokecio.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.ui.theme.PockyIcons

@Composable
fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier) {
        PromiseRow(
            icon = PockyIcons.Lock,
            title = stringResource(R.string.onboarding_promise_private),
            body = stringResource(R.string.onboarding_promise_private_body),
        )
        PromiseRow(
            icon = PockyIcons.Smile,
            title = stringResource(R.string.onboarding_promise_no_ads),
            body = stringResource(R.string.onboarding_promise_no_ads_body),
        )
        PromiseRow(
            icon = PockyIcons.List,
            title = stringResource(R.string.onboarding_promise_no_algorithm),
            body = stringResource(R.string.onboarding_promise_no_algorithm_body),
        )
    }
}
