package com.pokecio.app.media

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DownloadProgress(val done: Int, val total: Int)

data class DownloadResult(val saved: Int, val failed: Int, val cancelled: Boolean)

class MediaDownloads(
    private val context: Context,
    private val media: MediaLoader,
    private val scope: CoroutineScope,
) {
    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    private val _results = MutableSharedFlow<DownloadResult>(extraBufferCapacity = 2)
    val results: SharedFlow<DownloadResult> = _results.asSharedFlow()

    private var job: Job? = null

    fun save(items: List<SavedMedia>) {
        if (items.isEmpty() || job?.isActive == true) return
        val queue = items.distinctBy { it.source }
        job = scope.launch {
            var saved = 0
            var failed = 0
            _progress.value = DownloadProgress(0, queue.size)
            try {
                for (item in queue) {
                    if (!isActive) break
                    val keep = queue.size == 1 || media.hasFile(item.source)
                    if (MediaSaver.save(context, media, item)) saved++ else failed++
                    if (!keep) media.forget(item.source)
                    _progress.value = DownloadProgress(saved + failed, queue.size)
                }
            } finally {
                _progress.value = null
                _results.tryEmit(DownloadResult(saved, failed, saved + failed < queue.size))
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
