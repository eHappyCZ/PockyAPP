package com.pokecio.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.pokecio.app.data.ClientHolder
import com.pokecio.app.push.Notifier
import com.pokecio.app.tls.TlsVerifier
import com.pokecio.app.update.UpdateCheckWorker
import com.pokecio.app.update.UpdateController

class PockyApp : Application() {

    val clients by lazy { ClientHolder(this) }
    val notifier by lazy { Notifier(this) }

    @Volatile
    var visibleRoomId: String? = null

    @Volatile
    var inForeground = false

    override fun onCreate() {
        super.onCreate()
        TlsVerifier.init(this)
        notifier.createChannels()
        if (UpdateController.isAutomatic(UpdateController.prefs(this))) UpdateCheckWorker.schedule(this)
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = notifier.revealConcealed()
            },
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
