package com.pokecio.app.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.pokecio.app.core.VideoUpload
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

object VideoPrep {

    fun prepare(context: Context, uri: Uri): VideoUpload? {
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val original = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rotation = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            val width = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: 0
            val height = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: 0
            val duration = retriever.extract(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: 0
            val thumbnail = retriever.getFrameAtTime(0)?.let(::jpeg)
            val upright = rotation == 90 || rotation == 270

            val cleaned = if (mime == "video/mp4") withoutMetadata(context, uri, rotation) else null
            VideoUpload(
                bytes = cleaned ?: original,
                mime = mime,
                width = (if (upright) height else width).toUInt(),
                height = (if (upright) width else height).toUInt(),
                durationMs = duration.toULong(),
                thumbnail = thumbnail,
            )
        } catch (_: Exception) {
            VideoUpload(original, mime, 0u, 0u, 0u, null)
        } finally {
            retriever.release()
        }
    }

    private fun withoutMetadata(context: Context, uri: Uri, rotation: Int): ByteArray? {
        val target = File(context.cacheDir, "video-${System.currentTimeMillis()}.mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val created = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = created
            val tracks = (0 until extractor.trackCount).associateWith { index ->
                extractor.selectTrack(index)
                created.addTrack(extractor.getTrackFormat(index))
            }
            created.setOrientationHint(rotation)
            created.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            while (true) {
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.offset = 0
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                created.writeSampleData(tracks.getValue(extractor.sampleTrackIndex), buffer, info)
                extractor.advance()
            }
            created.stop()
            target.readBytes()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
            target.delete()
        }
    }

    private fun MediaMetadataRetriever.extract(key: Int): Int? = extractMetadata(key)?.toIntOrNull()

    private fun jpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }

    private const val BUFFER_SIZE = 4 * 1024 * 1024
}
