package com.pokecio.app.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileOpener {

    suspend fun prepare(context: Context, media: MediaLoader, source: String, name: String): File? {
        val cached = media.file(source) ?: return null
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "open").apply { mkdirs() }
            val target = File(dir, safeName(name))
            if (!target.exists() || target.length() != cached.length()) cached.copyTo(target, overwrite = true)
            target
        }
    }

    fun clear(context: Context) {
        File(context.cacheDir, "open").deleteRecursively()
    }

    private fun safeName(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(120)
        return if (clean.isEmpty() || clean.all { it == '.' }) "file" else clean
    }

    fun open(context: Context, file: File, mime: String?): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime ?: context.contentResolver.getType(uri) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
