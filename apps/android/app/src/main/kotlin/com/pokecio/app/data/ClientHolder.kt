package com.pokecio.app.data

import android.content.Context
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.createClient
import com.pokecio.app.core.restoreClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ClientHolder(context: Context) {

    private val sessions = SessionStore(File(context.filesDir, "secure"))
    private val matrixDir = File(context.filesDir, "matrix").absolutePath
    private val lock = Mutex()
    private var client: PockyClient? = null

    suspend fun current(): PockyClient? = lock.withLock {
        client ?: restoreLocked()
    }

    suspend fun hasSession(): Boolean = io { sessions.loadSession() != null }

    suspend fun login(username: String, password: String, deviceName: String, server: String): PockyClient = lock.withLock {
        val passphrase = io { sessions.databasePassphrase() }
        val fresh = createClient(matrixDir, passphrase, server)
        replace(fresh)
        try {
            val session = fresh.login(username, password, deviceName)
            io { sessions.saveSession(session) }
        } catch (e: PockyException) {
            replace(null)
            throw e
        }
        fresh
    }

    suspend fun logout() = lock.withLock {
        try {
            client?.logout()
        } catch (_: PockyException) {
        }
        replace(null)
        io { sessions.clear() }
    }

    suspend fun forget() = lock.withLock {
        replace(null)
        io { sessions.clear() }
    }

    private suspend fun restoreLocked(): PockyClient? {
        val session = io { sessions.loadSession() } ?: return null
        val passphrase = io { sessions.databasePassphrase() }
        val restored = restoreClient(session, matrixDir, passphrase)
        client = restored
        return restored
    }

    private fun replace(new: PockyClient?) {
        client?.close()
        client = new
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
