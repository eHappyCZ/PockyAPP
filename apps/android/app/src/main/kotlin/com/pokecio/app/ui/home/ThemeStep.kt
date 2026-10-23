package com.pokecio.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.ui.onboarding.ChoiceCard
import com.pokecio.app.ui.theme.Appearance
import com.pokecio.app.ui.theme.ThemeMode

fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
fun ThemeStep() {
    val context = LocalContext.current
    val selected = Appearance.theme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier) {
        ThemeMode.entries.forEach { mode ->
            ChoiceCard(
                title = stringResource(themeLabel(mode)),
                body = stringResource(
                    when (mode) {
                        ThemeMode.SYSTEM -> R.string.theme_system_body
                        ThemeMode.LIGHT -> R.string.theme_light_body
                        ThemeMode.DARK -> R.string.theme_dark_body
                    },
                ),
                selected = selected == mode,
                onClick = { Appearance.select(context, mode) },
            )
        }
    }
}
