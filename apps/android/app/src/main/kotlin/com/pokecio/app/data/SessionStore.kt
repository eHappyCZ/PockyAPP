package com.pokecio.app.data

import android.util.Base64
import java.io.File
import java.security.SecureRandom

class SessionStore(dir: File) {

    private val store = SecureStore(dir)

    fun loadSession(): String? = store.read(SESSION)?.decodeToString()

    fun saveSession(session: String) = store.write(SESSION, session.encodeToByteArray())

    fun databasePassphrase(): String {
        store.read(DB_KEY)?.let { return it.decodeToString() }

        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val passphrase = Base64.encodeToString(bytes, Base64.NO_WRAP)
        store.write(DB_KEY, passphrase.encodeToByteArray())
        return passphrase
    }

    fun clear() {
        store.delete(SESSION)
        store.delete(DB_KEY)
    }

    private companion object {
        const val SESSION = "session.bin"
        const val DB_KEY = "db_key.bin"
    }
}
