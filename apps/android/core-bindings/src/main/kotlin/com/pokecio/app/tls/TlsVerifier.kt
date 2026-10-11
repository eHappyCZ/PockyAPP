package com.pokecio.app.tls

import android.content.Context

object TlsVerifier {

    init {
        System.loadLibrary("pocky_ffi")
    }

    @JvmStatic
    external fun init(context: Context)
}
