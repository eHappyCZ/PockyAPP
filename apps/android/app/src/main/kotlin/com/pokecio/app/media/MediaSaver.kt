package com.pokecio.app.media

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedMedia(val source: String, val name: String, val mime: String?)

object MediaSaver {

    private const val FOLDER = "PockyAPP"

    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    suspend fun save(context: Context, media: MediaLoader, item: SavedMedia): Boolean {
        val cached = media.file(item.source) ?: return false
        val name = fileName(item)
        val mime = item.mime ?: mimeOf(name)
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    insert(context, cached, name, mime)
                } else {
                    copyLegacy(context, cached, name, mime)
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun share(context: Context, media: MediaLoader, items: List<SavedMedia>): Boolean {
        val files = items.mapNotNull { item -> FileOpener.prepare(context, media, item.source, fileName(item)) }
        if (files.isEmpty()) return false
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        val mimes = items.map { it.mime ?: mimeOf(fileName(it)) }.distinct()
        val type = mimes.singleOrNull() ?: mimes.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun insert(context: Context, file: File, name: String, mime: String): Boolean {
        val (collection, directory) = when {
            mime.startsWith("image/") ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_PICTURES
            mime.startsWith("video/") ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
            mime.startsWith("audio/") ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return false
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("no output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun copyLegacy(context: Context, file: File, name: String, mime: String): Boolean {
        if (needsPermission(context)) return false
        val directory = when {
            mime.startsWith("image/") -> Environment.DIRECTORY_PICTURES
            mime.startsWith("video/") -> Environment.DIRECTORY_MOVIES
            mime.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        val folder = File(Environment.getExternalStoragePublicDirectory(directory), FOLDER).apply { mkdirs() }
        val target = unique(folder, name)
        file.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
        return true
    }

    private fun unique(folder: File, name: String): File {
        var target = File(folder, name)
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        var index = 1
        while (target.exists()) {
            target = File(folder, if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension")
            index++
        }
        return target
    }

    fun fileName(item: SavedMedia): String {
        val clean = item.name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(120)
        val base = clean.ifEmpty { "pockyapp" }
        if (base.contains('.')) return base
        val extension = item.mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: return base
        return "$base.$extension"
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
}
