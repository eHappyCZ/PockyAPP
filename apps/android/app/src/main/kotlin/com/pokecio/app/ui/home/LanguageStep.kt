package com.pokecio.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.preferences.AppLanguage
import com.pokecio.app.preferences.LanguageMode
import com.pokecio.app.ui.onboarding.ChoiceCard

fun languageLabel(mode: LanguageMode) = when (mode) {
    LanguageMode.SYSTEM -> R.string.theme_system
    LanguageMode.CZECH -> R.string.language_czech
    LanguageMode.ENGLISH -> R.string.language_english
}

@Composable
fun LanguageStep() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LanguageMode.entries.forEach { mode ->
            ChoiceCard(
                title = stringResource(languageLabel(mode)),
                body = if (mode == LanguageMode.SYSTEM) stringResource(R.string.language_system_body) else "",
                selected = AppLanguage.mode == mode,
                onClick = {
                    if (AppLanguage.mode != mode) {
                        AppLanguage.select(context, mode)
                        restart(context)
                    }
                },
            )
        }
    }
}

private fun restart(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    Runtime.getRuntime().exit(0)
}
