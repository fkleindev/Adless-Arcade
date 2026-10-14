package com.adlessarcade.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Führt Schreibvorgänge strikt in Aufrufreihenfolge in einem App-weiten Scope aus, damit sie auch beim
 * Schließen eines Screens (ViewModel zerstört) fertig werden. Speichern ist "best effort": IO-Fehler werden geschluckt.
 */
class WriteQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED).also { channel ->
        scope.launch { for (write in channel) write() }
    }

    /** Reiht [write] ein; der Job ist fertig, sobald der Schreibvorgang (erfolgreich oder nicht) abgeschlossen ist. */
    fun enqueue(write: suspend () -> Unit): Job {
        val done = Job()
        writes.trySend {
            try {
                write()
            } catch (e: IOException) {
                // Ein Speicherfehler darf das Spiel nicht abstürzen lassen.
            } finally {
                done.complete()
            }
        }
        return done
    }

    /** Wartet, bis alle bisher eingereihten Schreibvorgänge erledigt sind (vor dem Lesen aufrufen). */
    suspend fun flush() {
        enqueue {}.join()
    }
}
