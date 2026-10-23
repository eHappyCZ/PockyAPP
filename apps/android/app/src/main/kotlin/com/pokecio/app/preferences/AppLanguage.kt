package com.pokecio.app.preferences

import android.content.Context
import java.util.Locale

enum class LanguageMode { SYSTEM, CZECH, ENGLISH }

object AppLanguage {
    private const val STORE = "appearance"
    private const val KEY = "language"

    var mode: LanguageMode = LanguageMode.SYSTEM
        private set

    var locale: Locale = Locale.forLanguageTag("cs-CZ")
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null)
        mode = LanguageMode.entries.firstOrNull { it.name == saved } ?: LanguageMode.SYSTEM
        locale = when (mode) {
            LanguageMode.CZECH -> CZECH
            LanguageMode.ENGLISH -> ENGLISH
            LanguageMode.SYSTEM -> {
                val system = context.resources.configuration.locales[0]?.language
                if (system == "cs" || system == "sk") CZECH else ENGLISH
            }
        }
    }

    fun select(context: Context, selected: LanguageMode) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(KEY, selected.name).apply()
    }

    private val CZECH: Locale = Locale.forLanguageTag("cs-CZ")
    private val ENGLISH: Locale = Locale.forLanguageTag("en-GB")
}
