package com.pokecio.app.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class UriInfo(val name: String, val size: Long?, val mime: String)

fun uriInfo(context: Context, uri: Uri): UriInfo {
    var name: String? = null
    var size: Long? = null
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    }
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    return UriInfo(name ?: uri.lastPathSegment ?: "soubor", size, mime)
}
