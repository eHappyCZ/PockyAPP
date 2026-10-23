package com.pokecio.app.preferences

import com.pokecio.app.R
import com.pokecio.app.core.ChatLook
import com.pokecio.app.core.DevicePreferences
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.NotificationText
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.media.toJpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PreferencesController(
    private val client: PockyClient,
    private val scope: CoroutineScope,
) {
    private val _preferences = MutableStateFlow<DevicePreferences?>(null)
    val preferences: StateFlow<DevicePreferences?> = _preferences.asStateFlow()

    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()

    private val _profileBusy = MutableStateFlow(false)
    val profileBusy: StateFlow<Boolean> = _profileBusy.asStateFlow()

    private val _problems = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val problems: SharedFlow<Int> = _problems.asSharedFlow()

    private val _blocked = MutableStateFlow<List<String>>(emptyList())
    val blocked: StateFlow<List<String>> = _blocked.asStateFlow()

    private val saving = Mutex()

    init {
        scope.launch {
            _preferences.value = try {
                client.preferences()
            } catch (_: PockyException) {
                DEFAULTS
            }
        }
        scope.launch {
            _avatarUrl.value = try {
                client.avatarUrl()
            } catch (_: PockyException) {
                null
            }
        }
        scope.launch {
            _displayName.value = try {
                client.displayName()
            } catch (_: PockyException) {
                client.userId()?.removePrefix("@")?.substringBefore(':')
            }
        }
    }

    fun rename(name: String) = profileChange {
        try {
            _displayName.value = client.setDisplayName(name)
        } catch (_: PockyException.InvalidName) {
            _problems.tryEmit(R.string.profile_name_invalid)
        }
    }

    fun changeAvatar(read: () -> ByteArray?) = profileChange {
        val bytes = withContext(Dispatchers.IO) { read() }
        if (bytes == null) {
            _problems.tryEmit(R.string.room_image_unreadable)
            return@profileChange
        }
        _avatarUrl.value = try {
            client.setAvatar(bytes)
        } catch (_: PockyException.Other) {
            val converted = withContext(Dispatchers.Default) { toJpeg(bytes) }
            if (converted == null) {
                _problems.tryEmit(R.string.room_image_unreadable)
                return@profileChange
            }
            client.setAvatar(converted)
        }
        _problems.tryEmit(R.string.profile_photo_saved)
    }

    fun removeAvatar() = profileChange {
        client.removeAvatar()
        _avatarUrl.value = null
    }

    private fun profileChange(action: suspend () -> Unit) {
        if (_profileBusy.value) return
        _profileBusy.value = true
        scope.launch {
            try {
                action()
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            } finally {
                _profileBusy.value = false
            }
        }
    }

    fun setChatStyle(style: ChatLook) = update { it.copy(chatStyle = style) }

    fun setNotificationPreview(preview: NotificationText) = update { it.copy(notificationPreview = preview) }

    fun setLocationPrecision(area: LocationArea) = update { it.copy(locationPrecision = area) }

    fun setSharePresence(share: Boolean) = update { it.copy(sharePresence = share) }

    fun setShareTyping(share: Boolean) = update { it.copy(shareTyping = share) }

    fun setSendReadReceipts(send: Boolean) = update { it.copy(sendReadReceipts = send) }

    fun setBlockScreenshots(block: Boolean) = update { it.copy(blockScreenshots = block) }

    fun loadBlocked() {
        scope.launch {
            try {
                _blocked.value = client.blockedUsers()
            } catch (_: PockyException) {
            }
        }
    }

    fun unblock(userId: String) {
        scope.launch {
            try {
                client.unblockUser(userId)
                _blocked.value = client.blockedUsers()
                _problems.tryEmit(R.string.blocked_unblocked)
            } catch (_: PockyException) {
                _problems.tryEmit(R.string.room_action_failed)
            }
        }
    }

    fun finishOnboarding() = update { it.copy(onboardingDone = true) }

    fun restartOnboarding() = update { it.copy(onboardingDone = false) }

    private fun update(transform: (DevicePreferences) -> DevicePreferences) {
        val current = _preferences.value ?: return
        val next = transform(current)
        if (next == current) return
        _preferences.value = next

        scope.launch {
            saving.withLock {
                try {
                    client.savePreferences(_preferences.value ?: next)
                } catch (_: PockyException) {
                }
            }
        }
    }

    private companion object {
        val DEFAULTS = DevicePreferences(
            onboardingDone = false,
            chatStyle = ChatLook.CLASSIC,
            notificationPreview = NotificationText.WHEN_UNLOCKED,
            locationPrecision = LocationArea.CITY,
            sharePresence = false,
            shareTyping = true,
            sendReadReceipts = true,
            blockScreenshots = true,
        )
    }
}
