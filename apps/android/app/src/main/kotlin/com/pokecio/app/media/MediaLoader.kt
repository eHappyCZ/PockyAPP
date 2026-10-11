package com.pokecio.app.media

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

val LocalMediaLoader = staticCompositionLocalOf<MediaLoader?> { null }

class MediaLoader(
    private val client: PockyClient,
    private val scope: CoroutineScope,
    private val filesDir: File,
) {
    private val cache = object : LruCache<String, ImageBitmap>(cacheSize()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val pending = HashMap<String, Deferred<ImageBitmap?>>()
    private val lock = Mutex()

    fun cached(source: String, widthPx: Int, heightPx: Int): ImageBitmap? =
        cache.get(key(source, widthPx, heightPx))

    suspend fun load(source: String, widthPx: Int, heightPx: Int): ImageBitmap? {
        val key = key(source, widthPx, heightPx)
        cache.get(key)?.let { return it }

        val job = lock.withLock {
            pending.getOrPut(key) {
                scope.async { fetch(source, widthPx, heightPx)?.also { cache.put(key, it) } }
            }
        }

        return try {
            job.await()
        } finally {
            lock.withLock { pending.remove(key) }
        }
    }

    suspend fun file(source: String): File? = withContext(Dispatchers.IO) {
        val target = File(filesDir, sha256(source))
        if (target.exists()) return@withContext target
        val bytes = try {
            client.loadMedia(source, 0u, 0u)
        } catch (_: PockyException) {
            return@withContext null
        }
        filesDir.mkdirs()
        val partial = File(filesDir, target.name + ".part")
        partial.writeBytes(bytes)
        partial.renameTo(target)
        target
    }

    fun hasFile(source: String): Boolean = File(filesDir, sha256(source)).exists()

    fun forget(source: String) {
        File(filesDir, sha256(source)).delete()
    }

    fun clear() {
        cache.evictAll()
        filesDir.deleteRecursively()
    }

    private suspend fun fetch(source: String, widthPx: Int, heightPx: Int): ImageBitmap? {
        val bytes = try {
            client.loadMedia(source, widthPx.toUInt(), heightPx.toUInt())
        } catch (_: PockyException) {
            return null
        }

        return withContext(Dispatchers.Default) { decode(bytes, widthPx, heightPx) }
    }

    private fun decode(bytes: ByteArray, widthPx: Int, heightPx: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx && bounds.outHeight / (sample * 2) >= heightPx) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    private fun key(source: String, widthPx: Int, heightPx: Int) = "$widthPx x $heightPx $source"

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        fun cacheSize(): Int = (Runtime.getRuntime().maxMemory() / 8).toInt()
    }
}
