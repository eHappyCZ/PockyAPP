package com.pokecio.app.rooms

import com.pokecio.app.R
import com.pokecio.app.core.Contact
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class PeopleController(
    private val client: PockyClient,
    private val scope: CoroutineScope,
) {
    private val query = MutableStateFlow("")

    private val _results = MutableStateFlow<List<Contact>>(emptyList())
    val results: StateFlow<List<Contact>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    private val search: Job

    init {
        search = scope.launch {
            query.map { it.trim() }
                .distinctUntilChanged()
                .debounce(SEARCH_DELAY_MS)
                .collectLatest { term ->
                    if (term.length < 2) {
                        _results.value = emptyList()
                        _searching.value = false
                        return@collectLatest
                    }
                    _searching.value = true
                    _results.value = try {
                        client.searchPeople(term)
                    } catch (_: PockyException) {
                        emptyList()
                    }
                    _searching.value = false
                }
        }
    }

    fun search(term: String) {
        query.value = term
    }

    fun openDirect(userId: String, onOpened: (String) -> Unit) = act {
        onOpened(client.openDirectChat(userId))
    }

    fun createGroup(name: String, members: List<String>, onCreated: (String) -> Unit) = act {
        onCreated(client.createGroup(name, members))
    }

    fun accept(roomId: String, onJoined: (String) -> Unit) = act {
        client.acceptInvite(roomId)
        onJoined(roomId)
    }

    fun decline(roomId: String) = act { client.declineInvite(roomId) }

    fun close() {
        search.cancel()
    }

    private fun act(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                action()
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val SEARCH_DELAY_MS = 300L
    }
}
