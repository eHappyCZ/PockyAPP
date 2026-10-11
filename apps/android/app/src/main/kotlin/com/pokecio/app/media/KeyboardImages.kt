package com.pokecio.app.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object KeyboardImages {

    private const val MAX_BYTES = 20L * 1024 * 1024

    fun copy(context: Context, uri: Uri): Uri? = try {
        val resolver = context.contentResolver
        val extension = resolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "img"
        val dir = File(context.cacheDir, "keyboard").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}.$extension")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val copied = input.copyTo(output)
                check(copied <= MAX_BYTES)
            }
        } ?: error("no stream")
        FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    } catch (_: Exception) {
        null
    }
}
