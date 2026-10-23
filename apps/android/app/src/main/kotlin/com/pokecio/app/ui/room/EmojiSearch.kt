package com.pokecio.app.ui.room

import android.content.Context
import android.icu.text.UnicodeSet
import com.pokecio.app.R

class EmojiEntry(val emoji: String, val name: String)

object EmojiSearch {

    private val index: List<EmojiEntry> by lazy {
        UnicodeSet("[\p{Emoji_Presentation}]")
            .filter { it.codePointCount(0, it.length) == 1 }
            .mapNotNull { emoji ->
                Character.getName(emoji.codePointAt(0))?.lowercase()?.let { EmojiEntry(emoji, it) }
            }
    }

    private var synonyms: List<Pair<String, String>>? = null

    fun find(context: Context, query: String, limit: Int = 120): List<EmojiEntry> {
        val words = query.lowercase().split(' ', '-').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val table = synonyms ?: context.resources.getStringArray(R.array.emoji_synonyms)
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .also { synonyms = it }

        val wanted = words.map { word ->
            val english = table.filter { (czech, _) -> czech.startsWith(word) }.map { it.second }
            listOf(word) + english
        }
        return index
            .filter { entry -> wanted.all { options -> options.any { it in entry.name } } }
            .take(limit)
    }
}
