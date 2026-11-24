package it.vfsfitvnm.vimusic.service

import it.vfsfitvnm.vimusic.DatabaseInitializer
import it.vfsfitvnm.vimusic.models.QueuedMediaItem
import java.util.concurrent.atomic.AtomicBoolean

/** Prevent replacing the on-disk queue until it has been read and restored. */
internal class PersistentQueueStore(private val db: DatabaseInitializer) {
    private val restored = AtomicBoolean(false)

    fun read(): List<QueuedMediaItem> = db.database.queue()

    fun markRestored() {
        restored.set(true)
    }

    /** When enabling the setting mid-session, never replace a pre-existing queue. */
    fun allowSavingIfEmpty(): Boolean {
        if (restored.get()) return true
        if (read().isNotEmpty()) return false
        restored.set(true)
        return true
    }

    fun replaceIfRestored(items: List<QueuedMediaItem>): Boolean {
        if (!restored.get()) return false
        db.runInTransaction {
            db.database.clearQueue()
            db.database.insert(items)
        }
        return true
    }
}
