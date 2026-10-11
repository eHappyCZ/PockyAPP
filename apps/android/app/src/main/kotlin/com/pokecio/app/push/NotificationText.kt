package com.pokecio.app.push

import com.pokecio.app.core.NotificationText
import com.pokecio.app.core.PockyClient

suspend fun PockyClient.notificationText(): NotificationText =
    if (appLock().enabled) NotificationText.NEVER else preferences().notificationPreview
